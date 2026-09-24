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
           "elysium-npcs", "elysium-trinkets", "elysium-solarpunk",
           "elysium-armory", "elysium-ascendancy", "elysium-enchanting",
           "elysium-spawners",
           "elysium-space", "elysium-ores", "elysium-flora", "elysium-kinetics", "elysium-industry", "elysium-atomics", "elysium-logistics", "elysium-agronomy", "elysium-foundry",
           "elysium-almanac",
           "elysium-transit",
           "elysium-arcana",
           "elysium-comforts",
           "elysium-atelier",
           "elysium-arsenal",
           "elysium-bestiary",
           "elysium-wilds",
           "elysium-colonies"]

# The cross-cutting checks at the root are built the same way and can fail the
# same way. They were not covered here at first, which is the same oversight in
# miniature: the file that exists to catch unreportable checks had its own
# blind spot.
ROOT_CHECKS = ["check_gear.py", "check_ui.py", "check_buses.py",
               "check_recursion.py", "check_datapack.py",
               "check_nullable.py", "check_loadtest.py", "check_rules.py",
               "check_worldgen.py",
               "check_construct.py"]

# The report block, matched loosely enough to survive rewording and to find one
# written inside a main() — a check whose report is indented is still a report,
# and a pattern anchored to column zero silently skipped the file that had one.
REPORT = re.compile(r"^[ \t]*if problems:", re.M)


def sources():
    for module in MODULES:
        yield module, HERE / module / "validate.py"
    for check in ROOT_CHECKS:
        yield check, HERE / check


def main():
    problems = []
    checked = 0

    for module, path in sources():
        if not path.is_file():
            problems.append(f"{module} has no validate.py"
                            if path.name == "validate.py" else f"{module} is missing")
            continue

        text = path.read_text(encoding="utf-8")
        reports = list(REPORT.finditer(text))
        if not reports:
            problems.append(f"{module} has no 'if problems:' report "
                            f"— it can never fail")
            continue
        if len(reports) > 1:
            problems.append(f"{module} reports {len(reports)} times; "
                            f"the first exits, so everything after it is dead")
            continue

        after = text[reports[0].start():]
        # fail() calls after the report are recorded into an already-printed
        # list. Comments and docstrings mentioning fail() are not calls.
        stripped = re.sub(r"#[^\n]*", "", after)
        dead = re.findall(r"\bfail\s*\(|\bproblems\.append\s*\(", stripped)
        if dead:
            line = text[:reports[0].start()].count("\n") + 1
            problems.append(
                f"{module} calls fail() {len(dead)} time(s) after "
                f"its report at line {line}. Those checks run, record nothing "
                f"anybody sees, and the file still prints success — move the "
                f"report to the end of the file")
        checked += 1

    if problems:
        print(f"{len(problems)} PROBLEM(S):")
        for problem in problems:
            print("  - " + problem)
        return 1

    print(f"validators          : {checked} validators — {len(MODULES)} module and "
          f"{len(ROOT_CHECKS)} cross-cutting — each reports once and reports last")
    return 0


if __name__ == "__main__":
    sys.exit(main())
