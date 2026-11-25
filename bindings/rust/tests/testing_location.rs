//! raven::testing reports a failure at the test's own line (`#[track_caller]`), not inside the
//! harness. A file of its own: the panic hook it sets is the process's, and the other harness tests
//! set theirs.

use raven::testing;
use std::panic;

const UI: &str = r#"<Window Title="T" Width="300" Height="240"><VBox><Button Text="Go"/><Label Text="Hello"/></VBox></Window>"#;

#[test]
fn a_failure_is_reported_at_the_tests_line() {
    use std::sync::{Arc, Mutex};
    let at = Arc::new(Mutex::new(Vec::new()));
    let seen = at.clone();
    let hook = panic::take_hook();
    panic::set_hook(Box::new(move |info| {
        if let Some(location) = info.location() {
            seen.lock().unwrap().push((location.file().to_string(), location.line()));
        }
    }));
    let mut lines = Vec::new();
    let result = panic::catch_unwind(|| {
        testing::run(UI, |ui| {
            ui.expect_exists("role=button name=\"Go\""); // passes
            ui.expect_text("role=text", "Goodbye");
        })
    });
    lines.push(line!() - 3);
    let result_click = panic::catch_unwind(|| testing::run(UI, |ui| ui.click(r#"role=button name="Stop""#)));
    lines.push(line!() - 1);
    panic::set_hook(hook);
    assert!(result.is_err() && result_click.is_err());
    let at = at.lock().unwrap();
    assert_eq!(at.len(), 2, "{:?}", at);
    for ((file, line), expected) in at.iter().zip(lines) {
        assert!(file.ends_with("tests/testing_location.rs"), "reported at {}:{}, not at the test", file, line);
        assert_eq!(*line, expected);
    }
}
