//! Headless UI tests for Raven apps, over Raven's UI driver.
//!
//! ```no_run
//! #[test]
//! fn adds_a_note() {
//!     raven::testing::run(NOTES_XML, |ui| {
//!         ui.type_text("role=text-field", "buy milk");
//!         ui.click(r#"role=button name="Add""#);
//!         ui.expect_text("role=list-item [0]", "buy milk");
//!     });
//! }
//! # const NOTES_XML: &str = "";
//! ```
//!
//! Selectors are the driver's (raven_driver.h): `role=button name="Save" [1]`.
//! A test means the same as in C (`raven/raven_test.h`) and Python
//! (`raven_testing`): finding, acting and waiting are Raven's C.
//!
//! The harness:
//! - headless: the null backend with no window system; with
//!   [`Options::capture`], headless Vulkan, and without it the test prints that
//!   it is skipped and returns (cargo has no skipped outcome);
//! - deterministic: animations advance by a fixed step each frame;
//! - Raven's errors (warnings too) fail the test, at the step that follows
//!   them; [`Ui::expect_errors`] collects the ones a test means to cause;
//! - idle around every step: `Rwait_idle` before every find and after every
//!   action, and a step fails when the app stays busy;
//! - leaks checked: the test fails when Raven still holds memory after `Rshutdown`;
//! - with capture, a failure saves a PNG of each window into the folder
//!   `RAVEN_TEST_CAPTURES` names.
//!
//! A failure panics with the driver's message, as `cargo test` reports it.
//! Raven runs once per process and cargo runs tests on several threads:
//! [`run`] takes them one at a time, and shuts Raven down even when a test panics.

use super::*;
use std::ffi::CString;
use std::os::raw::{c_char, c_int};
use std::panic::{self, AssertUnwindSafe};
use std::sync::Mutex;

/// One test at a time: Raven is one per process.
static RUNNING: Mutex<()> = Mutex::new(());

/// Raven's errors, from its callback (which can't capture), and whether a test expects them.
struct Errors {
    pending: Vec<String>,
    expected: Option<Vec<String>>,
}

static ERRORS: Mutex<Errors> = Mutex::new(Errors { pending: Vec::new(), expected: None });

fn errors() -> std::sync::MutexGuard<'static, Errors> {
    ERRORS.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

unsafe extern "C" fn on_error(code: RError, severity: RSeverity, detail: *const c_char) {
    if severity == R_SEVERITY_WEAK_WARNING {
        return;
    }
    let message = format!("{} ({}): {}", text(Rerror_to_string(code)), text(Rseverity_to_string(severity)), text(detail));
    let mut errors = errors();
    match errors.expected.as_mut() {
        Some(expected) => expected.push(message),
        None => errors.pending.push(message),
    }
}

/// How [`run_with`] starts Raven. `Options::default()` is the null backend, no window.
#[derive(Clone, Debug)]
pub struct Options {
    /// A window built before the test, from XML text. Empty: none (the app's own code builds it).
    pub xml: String,
    /// Headless Vulkan, so windows can be captured (and are, on a failure). Without Vulkan the test is skipped.
    pub capture: bool,
    /// Shown in messages and in the names of the captures.
    pub name: String,
    /// Seconds the animations advance each frame.
    pub time_step: f64,
    /// Frames a step waits for the app to be idle.
    pub idle_frames: i32,
    /// Don't check that Raven freed everything.
    pub allow_leaks: bool,
}

impl Default for Options {
    fn default() -> Self {
        Options {
            xml: String::new(),
            capture: false,
            name: "test".to_string(),
            time_step: 1.0 / 60.0,
            idle_frames: 600,
            allow_leaks: false,
        }
    }
}

/// An accessible as the driver describes it, copied.
#[derive(Clone, Debug, PartialEq)]
pub struct Accessible {
    /// Its role as a selector says it: "button", "list-item".
    pub role: String,
    pub name: String,
    pub description: String,
    /// A text field's text, the selected entry of a list, a slider's number as text; or "".
    pub value: String,
    pub number: f64,
    pub minimum: f64,
    pub maximum: f64,
    /// `RAccessibleState` values, or-ed.
    pub states: u32,
    /// In its window, logical units.
    pub x: f32,
    pub y: f32,
    pub width: f32,
    pub height: f32,
    /// Its index among its widget's items, or -1.
    pub item: i32,
}

impl Accessible {
    /// What it shows: a text field's value, else its name (the driver's `text=`).
    pub fn text(&self) -> &str {
        match self.role.as_str() {
            "text-field" | "document" | "combo-box" | "spin-button" => &self.value,
            _ => &self.name,
        }
    }

    /// Whether a state (`R_ACCESSIBLE_CHECKED`...) is set.
    pub fn has(&self, state: RAccessibleState) -> bool {
        self.states & (state as u32) != 0
    }
}

/// What [`Ui::act`] does, without input.
#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub enum Action {
    Expand,
    Collapse,
    Select,
    Focus,
}

/// "Enter", "Ctrl+S", "Shift+Tab", "a": a key and its modifiers.
pub fn parse_key(key: &str) -> Option<(RKey, u32)> {
    const NAMES: &[(&str, RKey)] = &[
        ("space", R_KEY_SPACE), ("escape", R_KEY_ESCAPE), ("esc", R_KEY_ESCAPE), ("enter", R_KEY_ENTER),
        ("return", R_KEY_ENTER), ("tab", R_KEY_TAB), ("backspace", R_KEY_BACKSPACE), ("insert", R_KEY_INSERT),
        ("delete", R_KEY_DELETE), ("del", R_KEY_DELETE), ("right", R_KEY_RIGHT), ("left", R_KEY_LEFT),
        ("down", R_KEY_DOWN), ("up", R_KEY_UP), ("pageup", R_KEY_PAGE_UP), ("pagedown", R_KEY_PAGE_DOWN),
        ("home", R_KEY_HOME), ("end", R_KEY_END), ("f1", R_KEY_F1), ("f2", R_KEY_F2), ("f3", R_KEY_F3),
        ("f4", R_KEY_F4), ("f5", R_KEY_F5), ("f6", R_KEY_F6), ("f7", R_KEY_F7), ("f8", R_KEY_F8),
        ("f9", R_KEY_F9), ("f10", R_KEY_F10), ("f11", R_KEY_F11), ("f12", R_KEY_F12),
    ];
    let mut parts: Vec<&str> = key.split('+').collect();
    // "Ctrl++": the last part is the plus key itself
    if key.ends_with("++") {
        parts.truncate(parts.len() - 2);
        parts.push("+");
    }
    let (mut code, mut modifiers) = (None, 0u32);
    for part in parts {
        let lower = part.to_ascii_lowercase();
        let modifier = match lower.as_str() {
            "ctrl" | "control" => Some(R_MOD_CTRL),
            "shift" => Some(R_MOD_SHIFT),
            "alt" => Some(R_MOD_ALT),
            "super" | "cmd" | "win" => Some(R_MOD_SUPER),
            _ => None,
        };
        if let Some(modifier) = modifier {
            modifiers |= modifier as u32;
            continue;
        }
        if code.is_some() {
            return None;
        }
        code = NAMES.iter().find(|(name, _)| *name == lower).map(|(_, key)| *key);
        if code.is_none() && part.len() == 1 {
            let c = part.as_bytes()[0];
            if c > 32 && c < 127 {
                code = Some(c.to_ascii_uppercase() as RKey);
            }
        }
        code?;
    }
    code.map(|code| (code, modifiers))
}

/// The app under test, given to the closure of [`run`]: find, act, check.
pub struct Ui {
    options: Options,
    failures: u32,
    /// The window built from `Options::xml`, or the last one `load_xml` built.
    pub window: Rid,
}

impl Ui {
    /// Fails the test: the windows are captured (with capture), then it panics with the message.
    pub fn fail(&mut self, message: &str) -> ! {
        self.failures += 1;
        let mut message = message.to_string();
        if self.options.capture {
            if let Ok(folder) = std::env::var("RAVEN_TEST_CAPTURES") {
                if !folder.is_empty() {
                    for index in 0..unsafe { Rwindow_get_count() } {
                        let path = format!("{}/{}-{}-window{}.png", folder, self.options.name, self.failures, index);
                        let c_path = cstr(&path);
                        if unsafe { Rwindow_save_preview(Rwindow_get(index), c_path.as_ptr(), 1.0) } != 0 {
                            message.push_str(&format!("\n  captured {}", path));
                        }
                    }
                }
            }
        }
        panic!("{}", message);
    }

    fn check_errors(&mut self) {
        let pending = std::mem::take(&mut errors().pending);
        if !pending.is_empty() {
            self.fail(&format!("Raven reported: {}", pending.join("; ")));
        }
    }

    /// Draws frames until the app has nothing pending; fails when it stays busy.
    pub fn wait_idle(&mut self) {
        self.idle("wait_idle");
    }

    fn idle(&mut self, step: &str) {
        let busy = unsafe { Rwait_idle(self.options.idle_frames) } == 0;
        self.check_errors();
        if busy {
            self.fail(&format!("{}: the app is still busy after {} frames", step, self.options.idle_frames));
        }
    }

    /// Raven's errors inside `body` are returned instead of failing the test.
    pub fn expect_errors<F: FnOnce(&mut Ui)>(&mut self, body: F) -> Vec<String> {
        let outer = errors().expected.replace(Vec::new());
        let result = panic::catch_unwind(AssertUnwindSafe(|| body(self)));
        let collected = {
            let mut errors = errors();
            let collected = errors.expected.take().unwrap_or_default();
            errors.expected = outer;
            collected
        };
        if let Err(payload) = result {
            panic::resume_unwind(payload);
        }
        collected
    }

    /// Builds a window from XML text; returns it (also as `ui.window`).
    pub fn load_xml(&mut self, xml: &str) -> Rid {
        let window = window_from_xml(xml);
        self.check_errors();
        if window == R_INVALID_ID {
            self.fail("load_xml: no window was built from the XML");
        }
        self.window = window;
        window
    }

    /// Builds a window from an XML file; returns it (also as `ui.window`).
    pub fn load_xml_file(&mut self, path: &str) -> Rid {
        let c_path = cstr(path);
        let window = unsafe { Rwindow_create_from_xml_file(c_path.as_ptr()) };
        self.check_errors();
        if window == R_INVALID_ID {
            self.fail(&format!("load_xml_file: no window was built from {}", path));
        }
        self.window = window;
        window
    }

    fn last_error() -> String {
        unsafe { text(Raccessible_get_last_error()) }
    }

    fn find_one(&mut self, step: &str, selector: &str) -> u64 {
        self.idle(step);
        let c_selector = cstr(selector);
        let found = unsafe { Raccessible_find(0, c_selector.as_ptr()) };
        self.check_errors();
        if found == 0 {
            self.fail(&format!("{}({}): {}", step, selector, Self::last_error()));
        }
        found
    }

    fn describe(&mut self, step: &str, selector: &str) -> Accessible {
        let found = self.find_one(step, selector);
        let mut info: RAccessibleInfo = unsafe { std::mem::zeroed() };
        if unsafe { Raccessible_describe(found, &mut info) } == 0 {
            self.fail(&format!("{}({}): it is gone", step, selector));
        }
        unsafe {
            Accessible {
                role: text(Raccessible_get_role_name(info.role)),
                name: text(info.name),
                description: text(info.description),
                value: text(info.value),
                number: info.number,
                minimum: info.minimum,
                maximum: info.maximum,
                states: info.states,
                x: info.x,
                y: info.y,
                width: info.width,
                height: info.height,
                item: info.item as i32,
            }
        }
    }

    /// The one accessible a selector names, described; fails unless exactly one matches.
    pub fn find(&mut self, selector: &str) -> Accessible {
        self.describe("find", selector)
    }

    /// How many accessibles match.
    pub fn count(&mut self, selector: &str) -> i32 {
        self.idle("count");
        let c_selector = cstr(selector);
        let found = unsafe { Raccessible_count(0, c_selector.as_ptr()) };
        self.check_errors();
        found as i32
    }

    fn acted(&mut self, step: &str, selector: &str, done: c_int) {
        if done == 0 {
            self.fail(&format!("{}({}): {}", step, selector, Self::last_error()));
        }
        self.idle(step);
    }

    /// Clicks it with the mouse, as a user would.
    pub fn click(&mut self, selector: &str) {
        let found = self.find_one("click", selector);
        let done = unsafe { Raccessible_click(found) };
        self.acted("click", selector, done);
    }

    /// Focuses it, then types the text as the keyboard would.
    pub fn type_text(&mut self, selector: &str, typed: &str) {
        let found = self.find_one("type_text", selector);
        let c_text = cstr(typed);
        let done = unsafe { Raccessible_type(found, c_text.as_ptr()) };
        self.acted("type_text", selector, done);
    }

    /// Focuses it, then presses a key: "Enter", "Ctrl+S", "Shift+Tab", "a".
    pub fn key(&mut self, selector: &str, key: &str) {
        let (code, modifiers) = match parse_key(key) {
            Some(parsed) => parsed,
            None => self.fail(&format!("key({}): unknown key {:?} (Enter, Ctrl+S, Shift+Tab, a)", selector, key)),
        };
        let found = self.find_one("key", selector);
        let done = unsafe { Raccessible_press_key(found, code, modifiers) };
        self.acted("key", selector, done);
    }

    /// Expands, collapses, selects or focuses it, without input.
    pub fn act(&mut self, selector: &str, action: Action) {
        let value = match action {
            Action::Expand => R_ACCESSIBLE_EXPAND,
            Action::Collapse => R_ACCESSIBLE_COLLAPSE,
            Action::Select => R_ACCESSIBLE_SELECT,
            Action::Focus => R_ACCESSIBLE_FOCUS,
        };
        let found = self.find_one("act", selector);
        let done = unsafe { Raccessible_act(found, value) };
        self.acted("act", selector, done);
    }

    fn expect_matches(&mut self, step: &str, selector: &str, expected: i32, exactly: &str) {
        let found = self.count(selector);
        if found != expected {
            self.fail(&format!("{}({}): {} match{}, expected {}{}", step, selector, found, if found == 1 { "" } else { "es" }, exactly, expected));
        }
    }

    /// Exactly one accessible matches.
    pub fn expect_exists(&mut self, selector: &str) {
        self.expect_matches("expect_exists", selector, 1, "exactly ");
    }

    /// None matches.
    pub fn expect_absent(&mut self, selector: &str) {
        self.expect_matches("expect_absent", selector, 0, "");
    }

    pub fn expect_count(&mut self, selector: &str, count: i32) {
        self.expect_matches("expect_count", selector, count, "");
    }

    /// The text it shows: a text field's value, else its name (the driver's `text=`).
    pub fn expect_text(&mut self, selector: &str, expected: &str) {
        let found = self.describe("expect_text", selector);
        if found.text() != expected {
            self.fail(&format!("expect_text({}): {:?}, expected {:?}", selector, found.text(), expected));
        }
    }

    /// The number of a slider, a spin box, a progress bar.
    pub fn expect_value(&mut self, selector: &str, expected: f64) {
        let found = self.describe("expect_value", selector);
        if !found.has(R_ACCESSIBLE_HAS_RANGE) {
            self.fail(&format!("expect_value({}): it has no number (a {})", selector, found.role));
        }
        if (found.number - expected).abs() > 1e-6 * expected.abs().max(1.0) {
            self.fail(&format!("expect_value({}): {}, expected {}", selector, found.number, expected));
        }
    }

    fn expect_state(&mut self, step: &str, selector: &str, state: RAccessibleState, inverted: bool, yes: &str, no: &str, expected: bool) {
        let found = self.describe(step, selector);
        let actual = found.has(state) != inverted;
        if actual != expected {
            self.fail(&format!("{}({}): {}, expected {}", step, selector, if actual { yes } else { no }, if expected { yes } else { no }));
        }
    }

    pub fn expect_checked(&mut self, selector: &str, checked: bool) {
        self.expect_state("expect_checked", selector, R_ACCESSIBLE_CHECKED, false, "checked", "not checked", checked);
    }

    pub fn expect_enabled(&mut self, selector: &str, enabled: bool) {
        self.expect_state("expect_enabled", selector, R_ACCESSIBLE_DISABLED, true, "enabled", "disabled", enabled);
    }

    pub fn expect_focused(&mut self, selector: &str, focused: bool) {
        self.expect_state("expect_focused", selector, R_ACCESSIBLE_FOCUSED, false, "focused", "not focused", focused);
    }
}

/// Runs a test on a headless Raven with a window built from `xml` ("" for none). See the module.
pub fn run<F: FnOnce(&mut Ui)>(xml: &str, test: F) {
    run_with(Options { xml: xml.to_string(), ..Options::default() }, test)
}

/// [`run`], with options: capture, a name, the time step, the idle frames, leaks allowed.
pub fn run_with<F: FnOnce(&mut Ui)>(options: Options, test: F) {
    let _one_at_a_time = RUNNING.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
    let app_name = CString::new(options.name.replace('\0', "")).unwrap_or_default();
    let mut desc: RInitDesc = unsafe { std::mem::zeroed() };
    desc.backend = if options.capture { R_BACKEND_VULKAN } else { R_BACKEND_NULL };
    desc.headless = 1;
    desc.app_name = app_name.as_ptr();
    {
        let mut errors = errors();
        errors.pending.clear();
        // (no Vulkan is a reason to skip, not a failure: its errors stay quiet)
        errors.expected = Some(Vec::new());
    }
    unsafe { Rerror_set_callback(Some(on_error)) };
    let started = unsafe { Rinit_ex(&desc) } != 0;
    let reasons = errors().expected.take().unwrap_or_default();
    if !started {
        if options.capture {
            println!("{}: skipped, no usable Vulkan driver to capture with", options.name);
            return;
        }
        panic!("{}: Raven didn't start headless: {}", options.name, if reasons.is_empty() { "Rinit_ex failed".to_string() } else { reasons.join("; ") });
    }
    if options.capture {
        let can_capture = unsafe {
            let probe = Rwindow_create(cstr("probe").as_ptr(), 16, 16, R_WINDOW_DEFAULT as Ruint);
            let (mut width, mut height) = (0, 0);
            let pixels = if probe != R_INVALID_ID { Rwindow_capture(probe, &mut width, &mut height) } else { std::ptr::null_mut() };
            Rpixels_free(pixels);
            if probe != R_INVALID_ID {
                Rwindow_destroy(probe);
            }
            !pixels.is_null()
        };
        if !can_capture {
            unsafe { Rshutdown() };
            errors().expected = None;
            println!("{}: skipped, no usable Vulkan driver to capture with", options.name);
            return;
        }
    }
    errors().expected = None;
    unsafe { Rset_animation_time_step(options.time_step) };

    let allow_leaks = options.allow_leaks;
    let xml = options.xml.clone();
    let mut ui = Ui { options, failures: 0, window: R_INVALID_ID };
    let result = panic::catch_unwind(AssertUnwindSafe(|| {
        if !xml.is_empty() {
            ui.load_xml(&xml);
        }
        test(&mut ui);
        // the errors of the last step, reported here
        ui.check_errors();
    }));
    unsafe {
        Rshutdown();
        Rerror_set_callback(None);
    }
    {
        let mut errors = errors();
        errors.pending.clear();
        errors.expected = None;
    }
    if let Err(payload) = result {
        panic::resume_unwind(payload);
    }
    if !allow_leaks {
        let mut stats: RMemoryStats = unsafe { std::mem::zeroed() };
        unsafe { Rmemory_get_stats(&mut stats) };
        assert!(stats.allocation_count == 0, "{} allocation(s) of Raven still alive after Rshutdown", stats.allocation_count);
        assert!(unsafe { Rmemory_check() } != 0, "Raven's heap is corrupted");
    }
}
