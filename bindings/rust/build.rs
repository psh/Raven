// Tells cargo where the Raven library is.
//
//   RAVEN_LIB_DIR   the folder that holds libraven.so / raven.lib / libraven.a
//   RAVEN_STATIC    set to 1 to link the static library (and what it needs)
//   RAVEN_MCP_LIB_DIR   with the feature `mcp`: the folder that holds libraven_mcp

use std::env;
use std::path::Path;

fn main() {
    println!("cargo:rerun-if-env-changed=RAVEN_LIB_DIR");
    println!("cargo:rerun-if-env-changed=RAVEN_STATIC");
    if let Ok(dir) = env::var("RAVEN_LIB_DIR") {
        println!("cargo:rustc-link-search=native={}", dir);
    }
    let is_static = env::var("RAVEN_STATIC").map(|value| value == "1").unwrap_or(false);
    println!("cargo:rustc-link-lib={}=raven", if is_static { "static" } else { "dylib" });
    // this package's examples and tests find the shared library where they were linked to it
    // (libraven's install name on macOS is @rpath/...); crates that use this one decide for themselves
    if !is_static {
        if let Ok(dir) = env::var("RAVEN_LIB_DIR") {
            let target = env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
            if target == "macos" || target == "linux" {
                println!("cargo:rustc-link-arg=-Wl,-rpath,{}", dir);
            }
        }
    }
    if env::var_os("CARGO_FEATURE_MCP").is_some() {
        // Raven MCP calls the app's own libraven, so it needs the shared one
        if is_static {
            panic!("the feature `mcp` needs the shared libraven: unset RAVEN_STATIC");
        }
        println!("cargo:rerun-if-env-changed=RAVEN_MCP_LIB_DIR");
        if let Ok(dir) = env::var("RAVEN_MCP_LIB_DIR") {
            println!("cargo:rustc-link-search=native={}", dir);
        }
    }
    if is_static {
        // the library is copied into the rlib of this crate: cargo must see when it changes
        if let Ok(dir) = env::var("RAVEN_LIB_DIR") {
            for name in &["libraven.a", "raven.lib"] {
                let path = Path::new(&dir).join(name);
                if path.exists() {
                    println!("cargo:rerun-if-changed={}", path.display());
                }
            }
        }
        let target = env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
        let (libs, frameworks): (&[&str], &[&str]) = match target.as_str() {
            "windows" => (&["user32", "gdi32", "shell32", "comdlg32", "ole32"], &[]),
            "linux" => (&["X11", "dl", "pthread", "m"], &[]),
            "macos" => (&["objc"], &["AppKit", "QuartzCore", "Metal", "Foundation", "CoreFoundation"]),
            _ => (&[], &[]),
        };
        for lib in libs {
            println!("cargo:rustc-link-lib={}", lib);
        }
        for framework in frameworks {
            println!("cargo:rustc-link-lib=framework={}", framework);
        }
    }
}
