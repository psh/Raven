---
status: closed
wayfinder: grilling
parent: 0001
blocked_by: [0013]
assignee: Paul Hawke
---

# Kotlin highlighting in CodeEditor

## Question

Raven's TextEditor has no Kotlin highlighting, so the translator sample shows its output as Java. How is a Kotlin language added with `Rlanguage_register` (`RLanguageDesc`: keywords, comments, strings, flags), and where does it live? As a language compose-raven registers at start-up so that `CodeEditor(language = "Kotlin")` works for every app, in the sample only, or upstream in Raven next to the built-in languages?

## Resolution

- **Where**: Kotlin becomes a built-in Raven language, next to Java in `src/widgets/syntax.c`, in the `psh/Raven` fork, offered upstream. It is required Raven change 3. Every Raven user gets it (`Language="Kotlin"` in XML too), and compose-raven needs no code. Rejected: registering it from `RavenHost.start` (language data in Kotlin, only for compose-raven apps), or from the sample alone (not reusable).
- **Description**, informed by Prism.js's `prism-kotlin.js`:
  - `name = "Kotlin"`, `extensions = "kt;kts"`.
  - `keywords`, Prism's list: `abstract actual annotation as break by catch class companion const constructor continue crossinline data do dynamic else enum expect external final finally for fun get if import in infix init inline inner interface internal is lateinit noinline object open operator out override package private protected public reified return sealed set super suspend tailrec this throw to try typealias val var vararg when where while`. Like Prism, it leaves out `value`, `field` and `it`, which are everywhere in Compose code.
  - `constants = "true false null"`.
  - `types = "Int Long Short Byte Float Double Boolean Char String Unit Any Nothing Array List MutableList Map MutableMap Set MutableSet Sequence Pair Triple"`. Prism highlights no types, but Raven's other languages list them.
  - `line_comment = "//"`, `block_comment_start = "/*"`, `block_comment_end = "*/"`, `string_quotes = "\"'"`, `multiline_strings = "\"\"\""`, `annotation = "@"`.
  - `flags = R_LANGUAGE_NESTED_COMMENTS | R_LANGUAGE_CHAR_LITERALS`, because Kotlin block comments nest.
- **Documented limits** (Prism handles these, but Raven's language description can't express them):
  - `$x` / `${...}` string templates show as plain string text.
  - Labels (`loop@`, `return@forEach`) look like annotations.
  - Trailing-lambda calls (`Column {`, `.forEach {`) aren't marked as functions.
  - A keyword right after a `.` (`map.get(x)`, `a.to(b)`) may be highlighted.

  Supporting templates or labels in `RLanguageDesc` would be a Raven feature of its own. It gets no ticket unless a later sample needs it.
- **Follow-on**: the [XML to Compose translator sample](0013-xml-to-compose-translator.md) shows its output with `language = "Kotlin"`. `CodeEditor(language: String?)` keeps taking a plain name, with no constants.
