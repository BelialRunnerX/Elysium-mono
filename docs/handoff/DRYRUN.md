# `tools/dryrun.py` — what is fixed, what is not, and the fix worth doing

## What it is for

A negative test finds the line it means to break by matching text — an
**anchor**. When that line is rewritten the anchor stops matching and the case
silently stops testing: it does not fail, it does not complain, it just is not
a test any more. `dryrun.py` writes a copy of a script with every mutation
neutered, so running the copy reports stale anchors and changes nothing.

## Six holes closed on 26–27 September

All are recorded at length in `tools/negtest/README.md`. In short:

1. **A dry script that would not parse printed nothing**, which reads exactly
   like a clean run. `negtest_gates.sh`, `negtest_icons.sh` and
   `negtest_uncap.sh` had been in that state for as long as they had existed,
   and one of them turns out to have a stale anchor. It now runs `bash -n`
   over what it wrote and refuses to hand over a script that is not a script.
2. **A command split over several lines with a trailing backslash** was
   half-neutered, leaving the rest dangling.
3. **A multi-line `restore()` with a brace group inside it** was matched to
   the wrong closing brace.
4. **A function whose last line was its cleanup** lost its closing brace when
   that line was replaced.
5. **A helper whose body is an inline `python3 -c` that writes** was not
   neutered at all, so a dry run wrote nine creature names into a language
   file and took a word out of a data file. Since `restore()` is neutered for
   a dry run, nothing put them back.
6. **A bare multi-line `python3 -c "..."` at the top level**, and **a
   mutation after a semicolon on a line that starts with something else**
   (`keep $S/x; mv $S/x /tmp/bak`).

## The one still open

**Closed on 2 October 2026, by construction rather than by pattern:** `tools/dryrun.py`
now runs every dry script inside a throwaway `git worktree` copy of the working tree
(HEAD plus the uncommitted diff and untracked files, every `/tmp/Elysium-mono` in the
script rewritten to the copy), proves the real tree byte-identical afterwards, and
removes the copy whatever happened; `--in-place` keeps the old behaviour. The pattern
neutering below stays for speed. Details and costs in `tools/negtest/README.md`; the
stale anchors the first thirteen runs found are in `STALE-ANCHORS.md`. What follows is
the state as it was on 27 September.

A helper that does something *before* its inline python:

    edit() { keep "$1"; python3 -c "
    ...
    " "$@"; }

The pattern for this was widened and it still leaks — a dry run of
`negtest_armour.sh` modified `ElysiumArmourRules.java`. Nine scripts are
written in this family and `STALE-ANCHORS.md` names them. **Do not dry-run
those nine** on a tree you are not willing to `git checkout --` afterwards.

## The fix worth doing instead

Every hole ever found in this tool has been the same thing: something that was
not at the start of a line. A continuation, a second command after a
semicolon, a helper that acted first, a heredoc with an unexpected tag, an
inline `python3 -c` two commands in. `dryrun.py` neuters by matching the start
of a line, and the scripts are shell, which does not care about lines.

**The approach is the bug.** Patching the next pattern buys a week. Two things
would end the family:

- **Run the dry copy somewhere disposable.** `git worktree add` a throwaway
  checkout, run the dry script against it, throw it away. Then a hole costs
  nothing: the worst case is that the dry run mutates a copy nobody keeps, and
  the stale anchors it reports are still correct. This is an afternoon's work
  and it makes every future hole harmless.
- **Or parse the script** rather than pattern-matching it, so a mutation is
  recognised wherever it sits.

The first is smaller, and it is the one I would do. It also fixes something
the patterns can never fix: a script whose helper is named something nobody
anticipated.

## The rule the whole episode is about

A tool whose output is silence has to prove it ran. `dryrun.py` reports
nothing when everything is fine, which means it also reports nothing when it
is broken, when it crashed, when the script would not parse, and when it
quietly did the opposite of its job. The `bash -n` check added today is the
smallest possible version of that proof. It is not the last one it needs.
