#!/usr/bin/env python3
"""
Writes the bindings of Raven for other languages from its C headers.

    python3 tools/bindgen/bindgen.py            writes bindings/, skills/raven/languages/ and .claude-plugin/
    python3 tools/bindgen/bindgen.py --check    fails when they are not what would be written

The result is part of the repository (bindings/), so that using Raven from
another language does not need Python; run this again after changing a
public header.
"""

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import cparse  # noqa: E402


def main():
    root = os.path.dirname(os.path.dirname(HERE))
    out_dir = os.path.join(root, "bindings")
    check = "--check" in sys.argv
    only = [a for a in sys.argv[1:] if not a.startswith("--")]
    api = cparse.parse(os.path.join(root, "include"))

    files = {}
    for language in ("python", "cpp", "rust", "csharp", "zig", "go", "luajit", "java", "odin", "odin_wrapper", "bun", "c3", "napi"):
        if only and language not in only:
            continue
        try:
            module = __import__("gen_" + language)
        except ImportError:
            continue
        files.update(module.generate(api))
    # the raven agent skill: its language files read the bindings just made
    if not only or "skill" in only:
        import gen_skill
        files.update(gen_skill.generate(api, files))

    stale = []
    for path, text in sorted(files.items()):
        # (the skill and the plugin's manifests live at the root of the repository)
        full = os.path.join(root if path.startswith(("skills/", ".claude-plugin/")) else out_dir, path)
        if not text.endswith("\n"):
            text += "\n"
        current = open(full, encoding="utf-8").read() if os.path.exists(full) else None
        if current == text:
            continue
        stale.append(path)
        if not check:
            os.makedirs(os.path.dirname(full), exist_ok=True)
            with open(full, "w", encoding="utf-8", newline="\n") as handle:
                handle.write(text)
    if check and stale:
        print("bindings/ is out of date (run tools/bindgen/bindgen.py): " + ", ".join(stale))
        return 1
    print("%d functions, %d enums, %d structures, %d callbacks -> %d files%s" % (
        len(api.functions), len(api.enums), len(api.structs), len(api.callbacks), len(files),
        "" if check else " (%d written)" % len(stale)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
