#!/usr/bin/env python3
"""
Can each module's validator actually report what it finds?

    python3 check_validators.py

Run as part of the root validator.

<h2>The bug this exists for</h2>

Every module validator collects problems into a list and ends with:

    if problems:
        print(...); sys.exit(1)
    print("all ... checks passed")

That block has to be the LAST thing in the file. It was not, in one of them:
sections had been appended after it over time, so their fail() calls landed in
a list that had already been printed, and the file finished by announcing
success unconditionally. Those checks ran, found a genuine problem — a new
Empire family missing from the register action's clearance test — and exited 0.

That is the worst failure mode a validator has. Not a missing check: a check
that is present, prints a reassuring summary line, and is structurally
incapable of reporting anything. Nobody reviewing the file would notice,
because every individual check reads correctly.

So this checks the one structural property that makes the rest trustworthy.
"""
import os
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent

MODULES = ["elysium-lib", "elysium-core", "elysium-dungeons", "elysium-mobs",
           "elysium-npcs", "elysium-trinkets"]

# The report block, matched loosely enough to survive rewording.
REPORT = re.compile(r"^if problems:", re.M)


def main():
    problems = []
    checked = 0

    for module in MODULES:
        path = HERE / module / "validate.py"
        if not path.is_file():
            problems.append(f"{module} has no validate.py")
            continue

        text = path.read_text(encoding="utf-8")
        reports = list(REPORT.finditer(text))
        if not reports:
            problems.append(f"{module}/validate.py has no 'if problems:' report "
                            f"— it can never fail")
            continue
        if len(reports) > 1:
            problems.append(f"{module}/validate.py reports {len(reports)} times; "
                            f"the first exits, so everything after it is dead")
            continue

        after = text[reports[0].start():]
        # fail() calls after the report are recorded into an already-printed
        # list. Comments and docstrings mentioning fail() are not calls.
        stripped = re.sub(r"#[^\n]*", "", after)
        dead = re.findall(r"\bfail\s*\(", stripped)
        if dead:
            line = text[:reports[0].start()].count("\n") + 1
            problems.append(
                f"{module}/validate.py calls fail() {len(dead)} time(s) after "
                f"its report at line {line}. Those checks run, record nothing "
                f"anybody sees, and the file still prints success — move the "
                f"report to the end of the file")
        checked += 1

    if problems:
        print(f"{len(problems)} PROBLEM(S):")
        for problem in problems:
            print("  - " + problem)
        return 1

    print(f"validators          : {checked} module validators, each reports "
          f"once and reports last")
    return 0


if __name__ == "__main__":
    sys.exit(main())
