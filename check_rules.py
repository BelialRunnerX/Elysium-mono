#!/usr/bin/env python3
"""
Validator rules that name a call where they should name a statement.

    python3 check_rules.py

<h2>The mistake this exists because of</h2>

A per-module validator proves things about the source by looking for text in
it. A rule that looks for a <em>call</em> — {@code "ComfortRules.seedFor("} —
goes on passing the day somebody writes:

    long seed = 0L;
    if (false) seed = ComfortRules.seedFor(...);

The call is still there. The rule still matches. The behaviour it was written
to guarantee is gone, and the rule has never once failed, which is the same as
not having written it.

This project has been bitten by that **fourteen times**, every single one found
by a negative test rather than by reading. The fix each time was the same:
name the *whole statement*, not the token inside it. So that fix is now a
check.

<h2>What counts as suspect</h2>

A string compared against a **body** — a variable produced by `body_of` or
`squashed`, which is a method's source rather than a whole file — that ends in
`(` or `,`. Those are truncated statements: whatever follows them can be
anything at all.

A rule that asks whether a *declaration* exists ("is there a method called
this?") is a different kind of rule and is compared against a whole file, so it
is left alone. That distinction is the whole reason this check is worth having
rather than being noise.
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

#: Variables that hold one method's source rather than a whole file. A rule
#: against one of these is a rule about what a method *does*, which is exactly
#: where naming half a statement stops meaning anything.
BODIES = re.compile(r"^\s*(\w+)\s*=\s*(?:squashed\(\s*)?body_of\(", re.M)

#: if "..." not in <body>:   /   if "..." in <body>:
AGAINST = re.compile(r'"((?:[^"\\]|\\.)*)"\s+(?:not\s+)?in\s+(\w+)\b')

#: The rules that already name a call and have not been rewritten yet.
#:
#: A lint introduced into a tree this size either blocks everything on the day
#: it lands or is a ratchet. This is a ratchet: what is already here is
#: recorded, a new one fails the build, and the recorded ones come out as the
#: modules they are in are touched. Regenerate with --bless, which is a
#: deliberate act and shows up in a diff.
BASELINE = os.path.join(HERE, "docs", "RULES_BASELINE.txt")

problems = []
stale = []


def suspect(literal):
    """Whether a search string is a truncated statement."""
    trimmed = literal.rstrip()
    return trimmed.endswith("(") or trimmed.endswith(",")


modules = sorted(name for name in os.listdir(HERE)
                 if name.startswith("elysium-")
                 and os.path.isfile(os.path.join(HERE, name, "validate.py")))

known = set()
if os.path.isfile(BASELINE):
    for line in open(BASELINE, encoding="utf-8"):
        line = line.rstrip("\n")
        if line and not line.startswith("#"):
            known.add(line)

found = set()
checked = 0
rules = 0
for module in modules:
    path = os.path.join(HERE, module, "validate.py")
    source = open(path, encoding="utf-8").read()
    bodies = set(BODIES.findall(source))
    if not bodies:
        continue
    checked += 1
    for number, line in enumerate(source.splitlines(), start=1):
        if line.lstrip().startswith("#"):
            continue
        for literal, against in AGAINST.findall(line):
            if against not in bodies:
                continue
            rules += 1
            if not suspect(literal):
                continue
            entry = f"{module}\t{literal}"
            found.add(entry)
            if entry not in known:
                problems.append(
                    f"{module}/validate.py:{number} looks for {literal!r} inside a "
                    f"method body. That is half a statement: an edit can leave it "
                    f"exactly where it is and do something else with the result. "
                    f"Name the whole statement.")

if "--bless" in sys.argv:
    os.makedirs(os.path.dirname(BASELINE), exist_ok=True)
    with open(BASELINE, "w", encoding="utf-8") as handle:
        handle.write("# Validator rules that name a call where they should name a "
                     "statement.\n#\n# Written by check_rules.py --bless. Every line is "
                     "a rule that can be\n# defeated by an edit that leaves the call "
                     "exactly where it is — an\n# `if (false)` in front of it, an "
                     "assignment nobody reads — and goes on\n# passing. This file is a "
                     "ratchet: a new one fails the build, and these\n# come out as the "
                     "modules they are in are next touched.\n#\n# Fewer lines here is "
                     "the only direction this file may move.\n")
        for entry in sorted(found):
            handle.write(entry + "\n")
    print(f"blessed {len(found)} existing rule(s) into {os.path.relpath(BASELINE, HERE)}")
    sys.exit(0)

stale = sorted(known - found)

print("=" * 70)
print("rules — no validator names half a statement and calls it a check")
print("=" * 70)

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    print("  (these are new; docs/RULES_BASELINE.txt records the ones already here)")
    sys.exit(1)

if stale:
    print(f"validator rules    : {len(stale)} baselined rule(s) have been rewritten — "
          f"run check_rules.py --bless to take them off the list")

print(f"validator rules    : {rules} rule(s) across {checked} module validator(s); "
      f"{len(found)} still name a call rather than a statement and are on the ratchet, "
      f"and no new one has been added")
