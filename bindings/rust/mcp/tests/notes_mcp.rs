//! The notes example's tools, called by an MCP client as an agent would: notes.add, notes.list and
//! notes.delete over the notes window, in a headless app through raven::testing, and the window
//! checked after. As examples/notes/test_notes_mcp.py in Python.
//!
//!   RAVEN_LIB_DIR=... RAVEN_MCP_LIB_DIR=... cargo test --test notes_mcp   (in bindings/rust/mcp)
//!
//! Run by `./gradlew :raven-mcp:rustTest` (part of compose's `check`).

#[path = "../../../../examples/notes/notes_app.rs"]
mod notes_app;
#[path = "../../../../examples/notes/notes_tools.rs"]
mod notes_tools;
#[path = "common/mcp_client.rs"]
mod mcp_client;

use mcp_client::{on_client, text_of, Client};
use notes_app::{notes_xml, open_notes};
use notes_tools::notes_tools;
use raven_mcp as mcp;
use serde_json::json;

const PORT: u16 = 18778;

#[test]
fn an_agent_adds_lists_and_deletes_notes() {
    raven::testing::run("", |ui| {
        let window = open_notes(&notes_xml());
        let tools = notes_tools(window);
        assert!(mcp::start(&mcp::Config {
            name: Some("notes-test".into()),
            port: PORT,
            token: Some(String::new()),
            ..mcp::Config::default()
        }));

        let (names, results) = on_client(|| {
            let mut client = Client::connect(PORT);
            let listed = client.request("tools/list", json!({}));
            let mut names: Vec<String> = listed["result"]["tools"]
                .as_array()
                .unwrap()
                .iter()
                .map(|tool| tool["name"].as_str().unwrap().to_string())
                .collect();
            names.sort();
            let results = vec![
                client.call("notes.list", json!({})),
                client.call("notes.add", json!({"text": "  buy milk "})),
                client.call("notes.add", json!({"text": "   "})),
                client.call("notes.add", json!({"text": "call Oscar"})),
                client.call("notes.delete", json!({"index": 0})),
                client.call("notes.delete", json!({"index": 5})),
                client.call("notes.list", json!({})),
            ];
            (names, results)
        });
        assert_eq!(names, vec!["notes.add", "notes.delete", "notes.list"]);
        assert_eq!(text_of(&results[0]), "[]");
        assert_eq!(text_of(&results[1]), r#"added "buy milk", note 0"#);
        assert_eq!(results[2]["isError"], true);
        assert_eq!(text_of(&results[2]), "a note can't be blank");
        assert_eq!(text_of(&results[3]), r#"added "call Oscar", note 1"#);
        assert_eq!(text_of(&results[4]), r#"deleted "buy milk""#);
        assert_eq!(results[5]["isError"], true);
        assert_eq!(text_of(&results[5]), "there is no note 5: the list has 1");
        assert_eq!(text_of(&results[6]), r#"["call Oscar"]"#);

        // the window shows what the agent did
        ui.expect_count("role=list-item", 1);
        ui.expect_text("role=list-item [0]", "call Oscar");
        ui.expect_text("widget=count", "1 note");
        ui.expect_enabled(r#"role=button name="Delete""#, false);

        drop(tools);
        mcp::stop();
    });
}
