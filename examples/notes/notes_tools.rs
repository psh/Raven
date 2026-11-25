//! The notes example's tools, for an agent: notes.add, notes.list and notes.delete, over the window
//! notes_app.rs builds, through raven_mcp (the crate raven-gui-mcp). Included beside notes_app.rs
//! (`mod notes_app; mod notes_tools;`); bindings/rust/mcp/tests/notes_mcp.rs calls them through an
//! MCP client.

use crate::notes_app::Notes;
use raven::Rid;
use raven_mcp::schemars::JsonSchema;
use raven_mcp::serde::Deserialize;
use raven_mcp::{tool, Hint, NoArgs, ToolHandle};

#[derive(Deserialize, JsonSchema)]
#[serde(crate = "raven_mcp::serde")]
#[schemars(crate = "raven_mcp::schemars")]
struct AddNote {
    /// The note's text; the whitespace around it is dropped
    text: String,
}

#[derive(Deserialize, JsonSchema)]
#[serde(crate = "raven_mcp::serde")]
#[schemars(crate = "raven_mcp::schemars")]
struct DeleteNote {
    /// The note's position, from 0, as notes.list gives them
    index: usize,
}

/// Publishes notes.add, notes.list and notes.delete over the window `open_notes` built; they stay
/// while the handles are kept.
pub fn notes_tools(window: Rid) -> Vec<ToolHandle> {
    let notes = Notes::of(window);
    vec![
        tool("notes.add", "Add a note at the end of the list")
            .hints(Hint::NOT_DESTRUCTIVE | Hint::CLOSED_WORLD)
            .add(move |args: AddNote| match notes.add(&args.text) {
                Some(added) => Ok(format!("added {:?}, note {}", added, notes.all().len() - 1)),
                None => Err("a note can't be blank".to_string()),
            }),
        tool("notes.list", "The notes, in order")
            .hints(Hint::READ_ONLY | Hint::CLOSED_WORLD)
            .add(move |_: NoArgs| -> Result<raven_mcp::serde_json::Value, String> { Ok(notes.all().into()) }),
        tool("notes.delete", "Delete a note").hints(Hint::CLOSED_WORLD).add(move |args: DeleteNote| {
            let texts = notes.all();
            match texts.get(args.index) {
                Some(deleted) => {
                    notes.delete(args.index as i32);
                    Ok(format!("deleted {:?}", deleted))
                }
                None => Err(format!("there is no note {}: the list has {}", args.index, texts.len())),
            }
        }),
    ]
}
