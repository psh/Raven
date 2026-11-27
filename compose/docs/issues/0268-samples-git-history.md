---
status: open
---

# samples/git-history

A sample of compose-raven's own, with no C example behind it: a small git history viewer, the first
step towards a Raven take on `gitk`. A `HorizontalSplitter` holds the commits of a repository as a
list on the left, and on the right the diff of the commit selected, in a `DiffView`. It shows the
binding's widgets working together on real data, where the ports each show one widget. Start small:
the history of one branch and one commit's diff, nothing more.

**Depends on:** [Documents: DiffView](0266-documents-diffview.md), for `DiffView` and
`DiffContent.Patch`.

## Scope of the first version

- **The repository**: the one given on the command line, else the current directory. Not a git
  repository, or no `git` on the path: a message in the window instead of the panes.
- **Left pane**: the commits of `HEAD`, newest first (`git log`): the short hash, the subject, the
  author and the date, one row each, in a `ListView` (or a `LazyColumn` of rows). The first commit
  is selected at the start.
- **Right pane**: the selected commit's changes as a patch (`git show --format= <hash>`), in
  `DiffView(DiffContent.Patch(...))`, every file under its names. Above it, the commit's full
  message, its author and date. Previous and Next change, and the unified or split mode, as the
  `diff-view` sample has them.
- **Running git**: inside the sample, over Kotlin/Native's `platform.posix` (`popen`, or
  `posix_spawn` with a pipe), off the UI thread, its output read as UTF-8. No process API in the
  library: if another sample or app needs one, that is its own decision.
- **Large histories**: the list reads the first few hundred commits and more as it is scrolled to the
  end; a commit's patch is read when it is selected, and cancelled when another is selected first.

## Not in the first version

The commit graph and its branch lines, other branches and tags, a file tree per commit, searching
the history, comparing two chosen commits, and blame. Each can come as its own ticket once this one
is built.

## Deliverables

- [ ] `samples/git-history` on macOS and Linux: the splitter, the commit list, the selected commit's message and diff.
- [ ] Git run off the UI thread, a slow `git show` not freezing the window, a new selection cancelling the previous read.
- [ ] The messages for no repository and no `git`.
- [ ] A headless test over a small repository the test creates in a temporary folder (a few commits, one touching two files): the list's rows, selecting a commit, the diff shown, more commits read at the end of the list.
- [ ] The roadmap's sample table, and the sample's README with how to run it on a repository.
