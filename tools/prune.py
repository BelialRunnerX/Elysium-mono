#!/usr/bin/env python3
"""
Which jars the pack no longer needs, because Elysium has replaced them.

    python3 tools/prune.py                 # list them
    python3 tools/prune.py --why           # list them with what replaced each

Dylan's instruction: *"Make sure to remove old mods once we have converted them
so there isn't redundancy."* This is the list, derived from `docs/MANIFEST.md`
rather than from memory, so it cannot drift from what has actually been done.

<h2>What counts as replaced</h2>

Only the rows the manifest marks **DONE** — a module exists and covers the jar.
A jar marked PLANNED is one we intend to replace and have not, and pulling it
would take its content out of the pack with nothing in its place. A jar marked
DROP is a library or a performance mod that other jars still depend on; it goes
when the last thing needing it goes, not before.

<h2>What this does not do</h2>

Delete anything. It prints names. Moving them is done deliberately, into a
folder beside them, because a jar that turns out to have been holding something
up is a jar you want back in thirty seconds.
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST = os.path.join(HERE, "docs", "MANIFEST.md")

ROW = re.compile(r"^\|\s*`([^`]+)`\s*\|\s*(\w+)\s*\|\s*([^|]*)\|\s*([^|]*)\|")


def removable(jar):
    """Whether a DONE row names a jar that can actually be taken out.

    Two kinds of row are marked DONE and are not somebody else's jar, and
    printing them here is how a prune list becomes a list that tells you to
    delete your own pack:

      * a placeholder for something this project invented rather than
        replaced — "(new) automobility equivalent" is a design note, not a
        file;
      * our own jars, which are what is doing the replacing.
    """
    if jar.startswith("(new)"):
        return False
    return not jar.lower().startswith("elysium")

if not os.path.isfile(MANIFEST):
    print("docs/MANIFEST.md is not there; run tools/manifest.py first")
    sys.exit(1)

done = []
counts = {}
for line in open(MANIFEST, encoding="utf-8"):
    row = ROW.match(line.strip())
    if not row:
        continue
    jar, status, module, note = (part.strip() for part in row.groups())
    counts[status] = counts.get(status, 0) + 1
    if status == "DONE" and removable(jar):
        done.append((jar, module.strip("`"), note))

why = "--why" in sys.argv
for jar, module, note in sorted(done):
    print(f"{jar}\t{module}\t{note}" if why else jar)

print(f"# {len(done)} jar(s) replaced and removable; "
      + ", ".join(f"{count} {status.lower()}" for status, count in sorted(counts.items())),
      file=sys.stderr)
