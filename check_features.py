#!/usr/bin/env python3
"""
Are docs/FEATURES.md and docs/features.json what tools/catalogue.py writes today?

    python3 check_features.py

Both files are generated: the catalogue reads every module's blocks, items,
creatures, effects, data folders and validator section lines off the tree, and
the live suite places, gives and summons from features.json. Nothing checked
that anybody had re-run it. On 16 October a review found two solarpunk
mechanics rows in both files that had been stale for days -- a catalogue that
lists a mechanic the validator no longer prints is a claim of a check, and a
live run built from a stale features.json tests yesterday's pack.

So this runs the catalogue into a temporary folder (`--out`) and compares the
two files it writes with the committed ones byte for byte, naming the first
line that differs. A catalogue that fails, or exits 0 having written nothing,
is a failure here too: a comparison against nothing has not passed.
"""
import json
import os
import pathlib
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent
CATALOGUE = ROOT / "tools" / "catalogue.py"
DOCS = ROOT / "docs"
FILES = ("FEATURES.md", "features.json")
FIX = "run python3 tools/catalogue.py"


def first_difference(committed, fresh):
    """(line number, committed line, fresh line) where two byte strings first
    differ line by line, a missing line as None; None when they are the same."""
    mine, theirs = committed.split(b"\n"), fresh.split(b"\n")
    for at in range(max(len(mine), len(theirs))):
        one = mine[at] if at < len(mine) else None
        two = theirs[at] if at < len(theirs) else None
        if one != two:
            return at + 1, one, two
    return None


def shown(line):
    """A line as a report shows it: cut short, its CRLF named rather than hidden."""
    if line is None:
        return "nothing (the file has ended)"
    text = line.decode("utf-8", "replace")
    ending = " (with a CRLF ending)" if text.endswith("\r") else ""
    text = text.rstrip("\r")
    if len(text) > 110:
        text = text[:110] + "..."
    return repr(text) + ending


def main():
    problems = []
    lines = {}
    if not CATALOGUE.is_file():
        problems.append("tools/catalogue.py is missing, so nothing can say what the "
                        "feature catalogue should hold")
    else:
        with tempfile.TemporaryDirectory() as fresh_dir:
            try:
                ran = subprocess.run([sys.executable, str(CATALOGUE), "--out", fresh_dir],
                                     cwd=ROOT, capture_output=True, text=True,
                                     encoding="utf-8", errors="replace", timeout=900,
                                     env=dict(os.environ, PYTHONIOENCODING="utf-8"))
                code, said = ran.returncode, (ran.stderr.strip() or ran.stdout.strip())
            except subprocess.TimeoutExpired:
                code, said = None, "still running after 900 s"
            if code != 0:
                last = said.splitlines()[-1] if said else "it printed nothing"
                problems.append(f"tools/catalogue.py failed (exit {code}): {last} -- so "
                                f"docs/FEATURES.md and docs/features.json were compared "
                                f"with nothing")
            else:
                for name in FILES:
                    fresh, committed = pathlib.Path(fresh_dir) / name, DOCS / name
                    if not fresh.is_file():
                        problems.append(f"tools/catalogue.py exited 0 and wrote no {name}, "
                                        f"so docs/{name} was compared with nothing")
                        continue
                    if not committed.is_file():
                        problems.append(f"docs/{name} is missing; {FIX}")
                        continue
                    made = fresh.read_bytes()
                    if name.endswith(".json"):
                        try:
                            held = json.loads(made.decode("utf-8"))
                        except ValueError as exc:
                            held = None
                            problems.append(f"tools/catalogue.py wrote a {name} that does "
                                            f"not parse ({exc})")
                        if held is not None and not held:
                            problems.append(f"tools/catalogue.py wrote a {name} with no "
                                            f"modules in it, so it compared nothing")
                    differs = first_difference(committed.read_bytes(), made)
                    if differs:
                        at, have, want = differs
                        problems.append(f"docs/{name} line {at} is {shown(have)} and the "
                                        f"catalogue writes {shown(want)}; {FIX}")
                    else:
                        lines[name] = made.count(b"\n") + 1

    if problems:
        print(f"{len(problems)} PROBLEM(S):")
        for problem in problems:
            print("  - " + problem)
        return 1
    print(f"features          : docs/FEATURES.md ({lines['FEATURES.md']} lines) and "
          f"docs/features.json ({lines['features.json']} lines) are what "
          f"tools/catalogue.py writes from the tree today")
    return 0


if __name__ == "__main__":
    sys.exit(main())
