---
status: open
wayfinder: grilling
blocked_by: []
assignee: null
---

# samples/sqlite-browser

## Question

What should `samples/sqlite-browser` be: a new sample, not a port, that browses a SQLite database
through SQLDelight's `SqlDriver` (its Kotlin/Native driver, `NativeSqliteDriver`), as a proof of
phase 5's lists at scale against real data? Decide the scope, the shape, and what it proves.

**The basic sample** is a `HorizontalSplitter`:

- **Left**: a `TreeView` of the database's tables, each expanding to a list of its columns (name,
  type, primary key, not null).
- **Right**: a `Table` of the selected table's rows, read through the driver.

**Extensions** beyond the basic sample, each to be kept, split into its own ticket, or dropped:

- **SQL entry**: a `CodeEditor` with SQL highlighting, running what is typed and showing the
  result in the `Table`, with errors shown.
- **A table diagram**: a `NodeGraph` with a node for each table (its columns as pins) and an edge
  for each foreign key. This waits on
  [Specialist widgets: NodeGraph, the composable](0281-specialist-widgets-nodegraph-the-composable.md).

Some questions to grill:

- **The schema**: is it read with `sqlite_master` and `PRAGMA table_info` /
  `PRAGMA foreign_key_list` through `executeQuery`? Do views, indexes and triggers belong in the
  tree?
- **Arbitrary SQL**: `SqlCursor` reads columns by index only and gives no column count or names.
  How does a result of typed SQL get its headers? A prepared `SELECT * FROM (...) LIMIT 0`
  doesn't name the columns either. Options include a small cinterop to `sqlite3_column_count` and
  `sqlite3_column_name`, or reading `PRAGMA`s for plain table views only.
- **Size**: the `Table` is virtual, so how are rows fetched for a big table? Options are all at
  once, pages of `LIMIT`/`OFFSET` as Raven asks for cells, or a row count first. How does sorting
  map onto `ORDER BY`?
- **Values**: how are NULLs, BLOBs and long texts shown in a text cell? How are types told apart
  (`getLong`, `getDouble`, `getString`, `getBytes` by the column's declared type)?
- **Editing**: is the sample read-only? Raven's Table has editable cells, which compose-raven
  doesn't bind yet.
- **Opening a file**: through `FileDialog`/`openFileDialog`, a recent-files list, and a demo
  database the sample creates when it starts (which tables, with foreign keys for the diagram)?
- **The dependency**: SQLDelight's runtime and native driver (Apache 2.0), which link the
  system's `libsqlite3`. Does it build on each target (macOS now; Linux and Windows later)?
  Which version matches the Kotlin version, and how is it recorded in `THIRD_PARTY.md`?
- **Tests**: a headless test over a database made in a temporary file, checking the tree, the
  rows, the selection, and (with the extension) the SQL entry and its errors.
- **What it proves**: which port gaps it might find, such as a Table's headers changing with each
  query, or a tree of tables and columns loaded lazily.
