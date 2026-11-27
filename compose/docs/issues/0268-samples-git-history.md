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
  repository: a message in the window instead of the panes. No `git` is needed on the machine.
- **Left pane**: the commits of `HEAD`, newest first (a revision walk): the short hash, the subject, the
  author and the date, one row each, in a `ListView` (or a `LazyColumn` of rows). The first commit
  is selected at the start.
- **Right pane**: the selected commit's changes as a patch (its parent's tree to its own), in
  `DiffView(DiffContent.Patch(...))`, every file under its names. Above it, the commit's full
  message, its author and date. Previous and Next change, and the unified or split mode, as the
  `diff-view` sample has them.
- **Git in-process, over libgit2**: a cinterop of the sample's own over libgit2's C API (no
  maintained Kotlin/Native binding exists: `kgit2/kgit2` is unpublished, unlicensed and on Kotlin
  1.7), as `samples/viewport-vulkan` has a cinterop of its own; compose-raven's library gains no
  dependency.
  - **Built from source** by a Gradle task of the sample, with CMake, before the cinterop runs, as
    `compose-raven-native` builds Raven: libgit2 at a pinned release tag, static, local-only
    (`BUILD_SHARED_LIBS=OFF`, `BUILD_TESTS=OFF`, `BUILD_CLI=OFF`, `USE_HTTPS=OFF`, `USE_SSH=OFF`), its
    bundled zlib, regex and HTTP parser: nothing to install. Check the option names and the latest
    release when building.
  - **A `libgit2.def`** over `git2.h`, with `compilerOpts`/`linkerOpts` per OS (`.osx`, `.linux`),
    the static library linked into the sample.
  - **The calls**, a dozen: `git_libgit2_init`/`_shutdown`, `git_repository_open_ext`, `git_revwalk_*`
    (push `HEAD`, sort by time), `git_commit_lookup` and its id, summary, message, author, time and
    parents, `git_commit_tree`, `git_diff_tree_to_tree`, `git_diff_to_buf(GIT_DIFF_FORMAT_PATCH)`,
    and the frees.
  - **Threading**: `git_libgit2_init` once; one background coroutine owns the repository, reads the
    log and the patches, and hands their Kotlin values to the UI thread.
  - **Licence**: GPLv2 with the linking exception, which allows linking it into compose-raven's
    sample; the sample's README says so and links libgit2's `COPYING`.
- **Large histories**: the list reads the first few hundred commits and more as it is scrolled to the
  end; a commit's patch is read when it is selected, and cancelled when another is selected first.

## Not in the first version

The commit graph and its branch lines, other branches and tags, a file tree per commit, searching
the history, comparing two chosen commits, and blame. Each can come as its own ticket once this one
is built.

## Deliverables

- [ ] `samples/git-history` on macOS and Linux: the splitter, the commit list, the selected commit's message and diff.
- [ ] libgit2 built from source by the sample's Gradle (pinned tag, static, local-only) and its cinterop, on macOS and Linux.
- [ ] The repository read off the UI thread by one coroutine owning it: a large patch not freezing the window, a new selection dropping the previous read.
- [ ] The message for a folder that isn't a git repository.
- [ ] A headless test over a small repository the test creates in a temporary folder through libgit2 too (`git_repository_init`, the index, `git_commit_create`; no `git` command), a few commits, one touching two files: the list's rows, selecting a commit, the diff shown, more commits read at the end of the list.
- [ ] The roadmap's sample table, and the sample's README with how to run it on a repository and libgit2's licence.
