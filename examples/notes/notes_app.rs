//! The notes example's logic in Rust: what notes.xml's actions do, as notes_app.c does it in C.
//! bindings/rust/tests/notes.rs tests it headless (`cargo test --test notes`). notes_tools.rs
//! offers the same to agents, through raven_mcp.

use raven::*;

/// The notes.xml every language's example builds: beside this file, from any crate that includes it.
pub fn notes_xml() -> String {
    let here = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join(file!());
    here.with_file_name("notes.xml").to_string_lossy().into_owned()
}

/// The notes window's widgets, and what the actions and the tools do to them.
#[derive(Copy, Clone)]
pub struct Notes {
    pub list: Rid,
    count: Rid,
    delete: Rid,
}

impl Notes {
    /// The notes window's widgets, in a window `open_notes` built.
    pub fn of(window: Rid) -> Notes {
        Notes { list: widget_find(window, "notes"), count: widget_find(window, "count"), delete: widget_find(window, "delete") }
    }

    fn show_count(self) {
        let n = unsafe { Rlist_view_get_item_count(self.list) };
        let shown = if n == 0 { "No notes".to_string() } else { format!("{} note{}", n, if n == 1 { "" } else { "s" }) };
        unsafe { Rlabel_set_text(self.count, cstr(&shown).as_ptr()) };
    }

    /// Adds a note, without the spaces around it; a blank one is not added. Returns the note.
    pub fn add(self, typed: &str) -> Option<String> {
        let typed = typed.trim_matches(' ');
        if typed.is_empty() {
            return None;
        }
        unsafe { Rlist_view_add_item(self.list, cstr(typed).as_ptr()) };
        self.show_count();
        Some(typed.to_string())
    }

    /// Deletes the note at `index`; the selection goes, and Delete with it.
    pub fn delete(self, index: i32) {
        unsafe {
            Rlist_view_remove_item(self.list, index);
            Rlist_view_set_selected(self.list, -1);
            Rwidget_set_enabled(self.delete, 0);
        }
        self.show_count();
    }

    /// The notes, in order.
    #[allow(dead_code)] // notes_tools.rs's only
    pub fn all(self) -> Vec<String> {
        let n = unsafe { Rlist_view_get_item_count(self.list) };
        (0..n).map(|i| unsafe { text(Rlist_view_get_item_text(self.list, i)) }).collect()
    }
}

/// Builds the window from notes.xml and binds notes.add and notes.delete. Returns the window.
pub fn open_notes(xml_path: &str) -> Rid {
    let path = cstr(xml_path);
    let window = unsafe { Rwindow_create_from_xml_file(path.as_ptr()) };
    if window == R_INVALID_ID {
        return window;
    }
    let note = widget_find(window, "note");
    let notes = Notes::of(window);

    // the text typed becomes a note, and the field is emptied for the next one
    action_bind("notes.add", move |_, _| unsafe {
        if notes.add(&text(Rtext_input_get_text(note))).is_some() {
            Rtext_input_set_text(note, cstr("").as_ptr());
        }
    });
    // the selected note goes
    action_bind("notes.delete", move |_, _| {
        let selected = unsafe { Rlist_view_get_selected(notes.list) };
        if selected >= 0 {
            notes.delete(selected);
        }
    });
    // Delete is there for a selected note only
    unsafe extern "C" fn selection_changed(_list: Rid, index: std::os::raw::c_int, user_data: *mut std::os::raw::c_void) {
        Rwidget_set_enabled(user_data as usize as Rid, if index >= 0 { 1 } else { 0 });
    }
    unsafe { Rlist_view_set_selection_changed_callback(notes.list, Some(selection_changed), notes.delete as usize as *mut std::os::raw::c_void) };
    window
}
