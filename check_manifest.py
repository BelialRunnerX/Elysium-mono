#!/usr/bin/env python3
"""
Is the conversion manifest still the truth?

    python3 check_manifest.py

Two questions, and both of them exist because the programme in
docs/CONVERSION.md runs for longer than anybody's memory of it:

  1. **Has every mod in the pack been decided about?** A new jar in the pack
     with no rule in tools/manifest.py is a mod nobody converts, and the way
     that failure presents itself is silence.
  2. **Does docs/MANIFEST.md still match what the generator produces?** A
     hand-edited manifest that has drifted from its source is worse than none:
     it is a list somebody will trust.

The manifest's *status* column is meant to be edited by hand as work lands, so
that one column is compared loosely — a row may say DONE where the generator
still says PLANNED, and that is the whole point of it. Everything else has to
match.
"""
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent
LISTING = ROOT / "tools" / "pack-jars.txt"
MANIFEST = ROOT / "docs" / "MANIFEST.md"

#: The statuses a row may have been moved to by hand, from the one the
#: generator gives it. Only forwards: nothing goes back to PLANNED.
PROGRESS = {"PLANNED": {"PLANNED", "ACTIVE", "DONE"},
            "ACTIVE": {"ACTIVE", "DONE"},
            "DONE": {"DONE"},
            "KEEP": {"KEEP", "DONE"},
            "DROP": {"DROP"}}


def rows(text):
    """Every {jar: (status, module)} in a manifest table."""
    out = {}
    for line in text.splitlines():
        found = re.match(r"\|\s*`([^`]+)`\s*\|\s*(\w+)\s*\|\s*([^|]+?)\s*\|", line)
        if found:
            out[found.group(1)] = (found.group(2), found.group(3))
    return out


def main():
    if not LISTING.is_file():
        print("manifest          : no pack listing at tools/pack-jars.txt; "
              "nothing to check against")
        return 0
    if not MANIFEST.is_file():
        print("manifest          : docs/MANIFEST.md is missing, so the programme has "
              "no record of what is done")
        return 1

    made = subprocess.run([sys.executable, str(ROOT / "tools" / "manifest.py"),
                           str(LISTING)], capture_output=True, text=True)
    problems = []
    if "UNDECIDED" in made.stdout and "| 0 | UNDECIDED" not in made.stdout:
        for line in made.stdout.splitlines():
            if line.startswith("> **"):
                problems.append(line.lstrip("> ").replace("**", ""))

    wanted = rows(made.stdout)
    have = rows(MANIFEST.read_text(encoding="utf-8"))
    for jar, (status, module) in wanted.items():
        if jar not in have:
            problems.append(f"{jar} is in the pack and not in the manifest, so "
                            f"nobody has decided what becomes of it")
            continue
        theirs, their_module = have[jar]
        if theirs not in PROGRESS.get(status, {theirs}):
            problems.append(f"{jar} is {theirs} in the manifest and {status} in the "
                            f"generator; a status only ever moves forwards")
        if their_module != module:
            problems.append(f"{jar} goes to {their_module} in the manifest and "
                            f"{module} in the generator; the two have drifted")
    for jar in have:
        if jar not in wanted and not jar.startswith("(new)"):
            problems.append(f"{jar} is in the manifest and not in the pack listing; "
                            f"either it was removed or the listing is stale")

    done = sum(1 for status, _ in have.values() if status == "DONE")
    planned = sum(1 for status, _ in have.values() if status in ("PLANNED", "ACTIVE"))
    if problems:
        print(f"{len(problems)} PROBLEM(S):")
        for problem in problems:
            print("  - " + problem)
        return 1
    print(f"manifest          : {len(have)} row(s), {done} done, {planned} to go, "
          f"and every jar in the pack has a decision")
    return 0


if __name__ == "__main__":
    sys.exit(main())
