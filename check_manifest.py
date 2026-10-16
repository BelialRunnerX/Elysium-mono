#!/usr/bin/env python3
"""
Is the conversion manifest still the truth?

    python3 check_manifest.py              the check
    python3 check_manifest.py --summary    print the two summary tables the rows give

Three questions, and all of them exist because the programme in
docs/CONVERSION.md runs for longer than anybody's memory of it:

  1. **Has every mod in the pack been decided about?** A new jar in the pack
     with no rule in tools/manifest.py is a mod nobody converts, and the way
     that failure presents itself is silence.
  2. **Does docs/MANIFEST.md still match what the generator produces?** A
     hand-edited manifest that has drifted from its source is worse than none:
     it is a list somebody will trust.
  3. **Does the summary at its head say what its rows say?** The status column
     is edited by hand, and the count table and "work remaining" table above
     it were edited by hand to follow — or not. On 16 October the summary said
     PLANNED 2 and listed elysium-foundry with one mod to absorb, while the
     rows held one PLANNED row and the foundry's was DONE. The summary is
     what a session with no memory reads first, so it is built from the rows
     (tools/manifest.py's summary(), the same lines the generator prints) and
     every cell that disagrees is named. When a merge touches both, take
     either side's rows and paste what `--summary` prints.

The manifest's *status* column is meant to be edited by hand as work lands, so
that one column is compared loosely — a row may say DONE where the generator
still says PLANNED, and that is the whole point of it. Everything else has to
match.
"""
import importlib.util
import os
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent
LISTING = ROOT / "tools" / "pack-jars.txt"
MANIFEST = ROOT / "docs" / "MANIFEST.md"
GENERATOR = ROOT / "tools" / "manifest.py"

#: The statuses a row may have been moved to by hand, from the one the
#: generator gives it. Only forwards: nothing goes back to PLANNED.
PROGRESS = {"PLANNED": {"PLANNED", "ACTIVE", "DONE"},
            "ACTIVE": {"ACTIVE", "DONE"},
            "DONE": {"DONE"},
            "KEEP": {"KEEP", "DONE"},
            "DROP": {"DROP"}}

HINT = "python3 check_manifest.py --summary prints the tables the rows give"


def rows(text):
    """Every {jar: (status, module)} in a manifest table."""
    out = {}
    for line in text.splitlines():
        found = re.match(r"\|\s*`([^`]+)`\s*\|\s*(\w+)\s*\|\s*([^|]+?)\s*\|", line)
        if found:
            out[found.group(1)] = (found.group(2), found.group(3))
    return out


def generator():
    """tools/manifest.py as a module, for its summary() and STATUSES."""
    spec = importlib.util.spec_from_file_location("manifest_generator", GENERATOR)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def expected(have, made):
    """The summary lines the manifest's own rows give, in file order."""
    return made.summary([(jar, status, module, "") for jar, (status, module) in have.items()])


def summary_block(text):
    """The manifest's summary as it stands: from the count table's header to
    the line before "## Every jar", trailing blank lines dropped. None when
    either end is missing."""
    lines = text.splitlines()
    try:
        start = lines.index("| count | status | means |")
        end = lines.index("## Every jar", start)
    except ValueError:
        return None
    block = [line.rstrip() for line in lines[start:end]]
    while block and not block[-1]:
        block.pop()
    return block


def summary_problems(text, want, made):
    """Every cell of the summary that the rows do not give, by name."""
    block = summary_block(text)
    if block is None:
        return [f"docs/MANIFEST.md has no summary between '| count | status | means |' and "
                f"'## Every jar', so nothing says how far the programme has got; {HINT}"]
    problems = []
    known = [status for status, _means in made.STATUSES]
    split = block.index("## Work remaining, by module") if "## Work remaining, by module" in block else len(block)

    def counts(lines, pattern):
        """{name: number} from the rows a pattern with groups `name` and `n` finds."""
        out = {}
        for line in lines:
            found = re.match(pattern, line)
            if found:
                out[found.group("name")] = int(found.group("n"))
        return out

    count_row = r"^\|\s*(?P<n>\d+)\s*\|\s*(?P<name>[A-Z]+)\s*\|"
    theirs = counts(block[:split], count_row)
    ours = counts(want, count_row)
    for status in known:
        if status not in theirs:
            problems.append(f"docs/MANIFEST.md's summary has no {status} cell; the rows "
                            f"give {ours.get(status, 0)}; {HINT}")
        elif theirs[status] != ours.get(status, 0):
            problems.append(f"docs/MANIFEST.md's summary says {status} {theirs[status]} and "
                            f"the rows give {ours.get(status, 0)}; {HINT}")
    for status in theirs:
        if status not in known:
            problems.append(f"docs/MANIFEST.md's summary counts {status}, which is not a "
                            f"status; {HINT}")

    work_row = r"^\|\s*`(?P<name>[^`]+)`\s*\|\s*(?P<n>\d+)\s*\|\s*$"
    if split == len(block):
        problems.append(f"docs/MANIFEST.md has no 'Work remaining, by module' table; {HINT}")
    else:
        theirs = counts(block[split:], work_row)
        ours = counts(want, work_row)
        for module in list(dict.fromkeys(list(theirs) + list(ours))):
            if theirs.get(module) == ours.get(module):
                continue
            if module not in ours:
                problems.append(f"docs/MANIFEST.md's work remaining gives {module} "
                                f"{theirs[module]} mod(s) to absorb, and no PLANNED or ACTIVE "
                                f"row goes to it; {HINT}")
            elif module not in theirs:
                problems.append(f"docs/MANIFEST.md's work remaining leaves out {module}, "
                                f"which the rows give {ours[module]} mod(s) to absorb; {HINT}")
            else:
                problems.append(f"docs/MANIFEST.md's work remaining gives {module} "
                                f"{theirs[module]} mod(s) to absorb and the rows give "
                                f"{ours[module]}; {HINT}")

    # every cell agrees, and still the text does not: the order, a "means"
    # column reworded, a line lost. Name the first line that differs.
    if not problems and block != want:
        for at in range(max(len(block), len(want))):
            mine = block[at] if at < len(block) else "(nothing)"
            right = want[at] if at < len(want) else "(nothing)"
            if mine != right:
                problems.append(f"docs/MANIFEST.md's summary, line {at + 1} of it, reads "
                                f"{mine!r} where the rows give {right!r}; {HINT}")
                break
    return problems


def main():
    if not MANIFEST.is_file():
        print("manifest          : docs/MANIFEST.md is missing, so the programme has "
              "no record of what is done")
        return 1
    made = generator()
    have = rows(MANIFEST.read_text(encoding="utf-8"))
    if "--summary" in sys.argv[1:]:
        for line in expected(have, made):
            print(line)
        return 0
    if not LISTING.is_file():
        # a check with nothing to check against has not passed
        print("manifest          : no pack listing at tools/pack-jars.txt, so not one "
              "jar's decision was checked")
        return 1

    # the generator's notes carry dashes: its output is UTF-8 whatever the console's code page
    result = subprocess.run([sys.executable, str(GENERATOR), str(LISTING)],
                            capture_output=True, text=True, encoding="utf-8",
                            env=dict(os.environ, PYTHONIOENCODING="utf-8"))
    problems = []
    if "UNDECIDED" in result.stdout and "| 0 | UNDECIDED" not in result.stdout:
        for line in result.stdout.splitlines():
            if line.startswith("> **"):
                problems.append(line.lstrip("> ").replace("**", ""))

    wanted = rows(result.stdout)
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
    known = {status for status, _means in made.STATUSES}
    for jar, (status, _module) in have.items():
        if status not in known:
            problems.append(f"{jar} is {status} in the manifest, a status the summary "
                            f"does not count")

    problems += summary_problems(MANIFEST.read_text(encoding="utf-8"), expected(have, made), made)

    done = sum(1 for status, _ in have.values() if status == "DONE")
    planned = sum(1 for status, _ in have.values() if status in ("PLANNED", "ACTIVE"))
    if problems:
        print(f"{len(problems)} PROBLEM(S):")
        for problem in problems:
            print("  - " + problem)
        return 1
    print(f"manifest          : {len(have)} row(s), {done} done, {planned} to go, "
          f"every jar in the pack has a decision, and the summary is counted from the rows")
    return 0


if __name__ == "__main__":
    sys.exit(main())
