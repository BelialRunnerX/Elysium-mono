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

# Neither of the two lists below is written here. Both were, and both drifted.
# The module list was a second copy of compile.sh's; the cross-cutting list
# said ten while validate.py had grown to sixteen, so six checkers — lang,
# lifecycle, manifest, merge, retexture, and this file itself — were never
# checked at all by the file whose whole job is checking checkers. A list
# copied by hand is a list that goes stale silently, which is the same failure
# this file exists to catch, one level up. So they are read out of the files
# that decide them.
COMPILE = (HERE / "compile.sh").read_text(encoding="utf-8")
VALIDATE = (HERE / "validate.py").read_text(encoding="utf-8")

_built = re.search(r'^MODULES="([^"]*)"', COMPILE, re.M)
if _built is None:
    sys.exit("check_validators: compile.sh has no MODULES= line to read")
MODULES = _built.group(1).split()

ROOT_CHECKS = sorted(set(re.findall(
    r'subprocess\.run\(\[sys\.executable, "(check_[a-z_]+\.py)"\]', VALIDATE)))
if not ROOT_CHECKS:
    sys.exit("check_validators: validate.py runs no check_*.py this can find")

# The report block, matched loosely enough to survive rewording and to find one
# written inside a main() — a check whose report is indented is still a report,
# and a pattern anchored to column zero silently skipped the file that had one.
REPORT = re.compile(r"^[ \t]*if problems:", re.M)


def code_only(text):
    """The file with its comments and docstrings blanked out.

    This file shows the report block inside its own docstring, as an example of
    the shape it is looking for, and read naively that makes it a file which
    reports twice. Every other validator is free to do the same. So comments
    and triple-quoted strings are blanked before anything is searched for —
    blanked rather than removed, so that the line numbers in a report still
    point at the real line.
    """
    out = list(text)
    for match in re.finditer(r'"""(?:.|\n)*?"""|\'\'\'(?:.|\n)*?\'\'\'|#[^\n]*', text):
        for index in range(match.start(), match.end()):
            if out[index] != "\n":
                out[index] = " "
    return "".join(out)


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

        text = code_only(path.read_text(encoding="utf-8"))
        reports = list(REPORT.finditer(text))
        if not reports:
            problems.append(f"{module} has no 'if problems:' report "
                            f"— it can never fail")
            continue
        if len(reports) > 1:
            problems.append(f"{module} reports {len(reports)} times; "
                            f"the first exits, so everything after it is dead")
            continue

        # fail() calls after the report are recorded into an already-printed
        # list. Comments and docstrings mentioning fail() are not calls, and
        # code_only has already taken both out of `text`.
        after = text[reports[0].start():]
        dead = re.findall(r"\bfail\s*\(|\bproblems\.append\s*\(", after)
        if dead:
            line = text[:reports[0].start()].count("\n") + 1
            problems.append(
                f"{module} calls fail() {len(dead)} time(s) after "
                f"its report at line {line}. Those checks run, record nothing "
                f"anybody sees, and the file still prints success — move the "
                f"report to the end of the file")
        checked += 1

    # A validator nobody runs is a validator that does not exist, and it is
    # worse than none at all because the count at the end of the run goes on
    # reassuring you. Two ways in: a check written at the root and never wired
    # into validate.py, and a module that compile.sh builds and validate.py
    # never asks about.
    asked_about = set(re.findall(r'"(elysium-[a-z]+)"', VALIDATE))
    for module in MODULES:
        if module not in asked_about:
            problems.append(f"{module} is built by compile.sh but validate.py "
                            f"never runs its validator — it is checked by nothing")

    for path in sorted(HERE.glob("check_*.py")):
        if path.name not in ROOT_CHECKS:
            problems.append(f"{path.name} sits at the root and validate.py "
                            f"never runs it — a check that passes by never "
                            f"being asked")

    # And the negative tests' own count. tools/negtest/README.md opens with a
    # number, and that number is what anybody asking "how well is this tested"
    # reads. It went one high for a whole feature because a script was edited
    # rather than added and the count was bumped anyway — which is the same
    # class of mistake as a roster with thirty-one of thirty-two blocks
    # registered, except that the thing miscounted is the checking itself.
    scripts = sorted((HERE / "tools" / "negtest").glob("negtest_*.sh"))
    readme = HERE / "tools" / "negtest" / "README.md"
    if not scripts:
        problems.append("tools/negtest holds no scripts at all, so every "
                        "validator in this tree is unproven")
    elif not readme.is_file():
        problems.append("tools/negtest has no README, so nothing states how "
                        "many scripts there are and the count cannot be wrong")
    else:
        said = re.search(r"^(\d+) scripts\.", readme.read_text(encoding="utf-8"),
                         flags=re.M)
        if not said:
            problems.append("tools/negtest/README.md does not open with a count "
                            "of the scripts, so nothing can check it")
        elif int(said.group(1)) != len(scripts):
            problems.append(f"tools/negtest/README.md says {said.group(1)} "
                            f"script(s) and there are {len(scripts)} — a number "
                            f"people read as how well this tree is tested")

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
