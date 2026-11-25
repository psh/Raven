//! raven::testing, the harness for Rust UI tests: its checks pass and fail as they say, its
//! messages say why, Raven's errors fail a test, a failing test leaves Raven ready for the next,
//! and a failure with capture leaves a PNG of each window.

use raven::testing::{self, parse_key, Action, Options};
use raven::*;
use std::panic;

const UI: &str = r#"
<Window Title="T" Width="300" Height="240">
    <VBox>
        <TextInput Name="field" AccessibleName="Field"/>
        <Button Name="go" Text="Go"/>
        <Button Name="off" Text="Off" Enabled="false"/>
        <CheckBox Name="box" Text="Box" Checked="true"/>
        <Slider Name="slider" AccessibleName="Level" Min="0" Max="10" Value="4"/>
        <Label Text="Hello"/>
    </VBox>
</Window>"#;

/// The panic message of a test that fails, which must hold every part.
fn fails_with<F: FnOnce()>(body: F, parts: &[&str]) {
    let hook = panic::take_hook();
    panic::set_hook(Box::new(|_| {}));
    let result = panic::catch_unwind(panic::AssertUnwindSafe(body));
    panic::set_hook(hook);
    let payload = result.expect_err("expected the test to fail");
    let message = payload.downcast_ref::<String>().cloned().or_else(|| payload.downcast_ref::<&str>().map(|s| s.to_string())).unwrap_or_default();
    for part in parts {
        assert!(message.contains(part), "the message {:?} doesn't hold {:?}", message, part);
    }
}

#[test]
fn checks_that_hold() {
    testing::run(UI, |ui| {
        ui.expect_exists(r#"role=button name="Go""#);
        ui.expect_absent(r#"role=button name="Stop""#);
        ui.expect_count("role=button", 2);
        ui.expect_text("role=text", "Hello");
        ui.type_text("role=text-field", "typed");
        ui.expect_text("role=text-field", "typed");
        ui.expect_focused("role=text-field", true);
        ui.key("role=text-field", "Backspace");
        ui.expect_text("role=text-field", "type");
        ui.expect_enabled(r#"role=button name="Go""#, true);
        ui.expect_enabled(r#"role=button name="Off""#, false);
        ui.expect_checked("role=checkbox", true);
        ui.click("role=checkbox");
        ui.expect_checked("role=checkbox", false);
        ui.expect_value("role=slider", 4.0);
        ui.act(r#"role=button name="Go""#, Action::Focus);
        ui.expect_focused(r#"role=button name="Go""#, true);
        let found = ui.find("role=slider");
        assert_eq!((found.role.as_str(), found.name.as_str(), found.maximum), ("slider", "Level", 10.0));
        assert_eq!(ui.count("role=button"), 2);
        // the errors a test means to cause are collected, not failures
        let errors = ui.expect_errors(|_| unsafe { Rwidget_set_enabled(0x7FFF1234, 1) });
        assert_eq!(errors.len(), 1);
        assert!(errors[0].contains("R_ERROR_INVALID_ID"));
    });
}

#[test]
fn keys() {
    assert_eq!(parse_key("Enter"), Some((R_KEY_ENTER, 0)));
    assert_eq!(parse_key("Ctrl+Shift+s"), Some((R_KEY_S, (R_MOD_CTRL | R_MOD_SHIFT) as u32)));
    assert_eq!(parse_key("Ctrl++"), Some(('+' as RKey, R_MOD_CTRL as u32)));
    assert_eq!(parse_key("F5"), Some((R_KEY_F5, 0)));
    assert_eq!(parse_key("Hyper+Q"), None);
}

#[test]
fn failure_messages() {
    let cases: Vec<(Box<dyn Fn(&mut testing::Ui)>, &[&str])> = vec![
        (Box::new(|ui| ui.expect_text("role=text", "Goodbye")), &[r#"expect_text(role=text): "Hello", expected "Goodbye""#]),
        (Box::new(|ui| ui.expect_absent(r#"role=button name="Go""#)), &[r#"expect_absent(role=button name="Go"): 1 match, expected 0"#]),
        (Box::new(|ui| ui.expect_exists("role=button")), &["2 matches, expected exactly 1"]),
        (Box::new(|ui| ui.expect_count("role=checkbox", 3)), &["1 match, expected 3"]),
        (Box::new(|ui| ui.expect_enabled(r#"role=button name="Off""#, true)), &[r#"expect_enabled(role=button name="Off"): disabled, expected enabled"#]),
        (Box::new(|ui| ui.expect_checked("role=checkbox", false)), &["expect_checked(role=checkbox): checked, expected not checked"]),
        (Box::new(|ui| ui.expect_focused(r#"role=button name="Go""#, true)), &["not focused, expected focused"]),
        (Box::new(|ui| ui.expect_value("role=slider", 5.0)), &["expect_value(role=slider): 4, expected 5"]),
        (Box::new(|ui| ui.expect_value(r#"role=button name="Go""#, 1.0)), &["it has no number (a button)"]),
        (Box::new(|ui| ui.click(r#"role=button name="Stop""#)), &[r#"click(role=button name="Stop"): "#, "Stop"]),
        (Box::new(|ui| ui.click("role=button")), &["2 accessibles"]),
        (Box::new(|ui| ui.click(r#"role=button name="Off""#)), &["disabled"]),
        (Box::new(|ui| ui.key("role=text-field", "Hyper+Q")), &["unknown key"]),
        (Box::new(|_| unsafe { Rwidget_set_enabled(0x7FFF1234, 1) }), &["Raven reported: R_ERROR_INVALID_ID"]),
    ];
    for (body, parts) in cases {
        // each its own run: a failing test leaves Raven shut down, ready for the next
        fails_with(move || testing::run(UI, |ui| body(ui)), parts);
    }
}

#[test]
fn xml_warnings_fail() {
    fails_with(|| testing::run(r#"<Window><Slider Minimum="0"/></Window>"#, |_| {}), &["Minimum"]);
}

#[test]
fn a_failure_leaves_a_png() {
    let folder = std::env::temp_dir().join(format!("raven_testing_{}", std::process::id()));
    std::fs::create_dir_all(&folder).unwrap();
    std::env::set_var("RAVEN_TEST_CAPTURES", &folder);
    let path = folder.join("captured-1-window0.png");
    let _ = std::fs::remove_file(&path);
    let options = Options { xml: UI.to_string(), capture: true, name: "captured".to_string(), ..Options::default() };
    let mut ran = false;
    let shown = path.display().to_string();
    fails_with(
        || {
            testing::run_with(options, |ui| {
                ran = true;
                ui.expect_text("role=text", "Goodbye");
            });
            // without Vulkan it is skipped: fail on purpose so the check below is left out
            panic!("skipped");
        },
        &[],
    );
    if ran {
        let bytes = std::fs::read(&path).expect("the capture was written");
        assert_eq!(&bytes[..8], b"\x89PNG\r\n\x1a\n", "{}", shown);
    } else {
        println!("no usable Vulkan driver: the captures are not checked");
    }
    let _ = std::fs::remove_dir_all(&folder);
}
