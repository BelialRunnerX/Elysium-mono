#!/usr/bin/env python3
"""
Does every translated message get the arguments its string asks for?

    python3 check_lang.py

`Component.translatable(key, a, b, c)` substitutes positionally and checks
nothing. Too few arguments and the player reads a raw `%s`; too many and the
extras vanish; reordered placeholders put every value in the wrong sentence.
None of it is visible to a compiler, and none of it is visible to the per-module
validators either — they check that a key <em>exists</em>, which is a different
question and the one that happens to be easier.

This is at the root rather than in each module's validate.py because it is one
rule applied identically six times, and six copies of it would drift.

See tools/langargs.py for what the scan can and cannot see. The short version:
it checks the count, not the order, because nothing in the source says what a
placeholder is for.
"""
import os
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "tools"))

import langargs

# The namespace each module's language file lives under. Written out rather
# than derived from the directory name, because they do not match: elysium-core
# ships under "elysium" and elysium-npcs under "elysiumnpcs".
MODULES = [
    ("elysium-lib", "elysiumlib"),
    ("elysium-core", "elysium"),
    ("elysium-dungeons", "elysiumdungeons"),
    ("elysium-mobs", "elysiummobs"),
    ("elysium-npcs", "elysiumnpcs"),
    ("elysium-trinkets", "elysiumtrinkets"),
]


def main():
    problems = []
    total_checked = 0
    total_skipped = 0

    for module, namespace in MODULES:
        source = HERE / module / "src/main/java"
        lang = HERE / module / f"src/main/resources/assets/{namespace}/lang/en_us.json"
        if not source.is_dir():
            problems.append(f"{module}: no src/main/java")
            continue
        if not lang.is_file():
            problems.append(f"{module}: no language file at "
                            f"assets/{namespace}/lang/en_us.json")
            continue

        checked, skipped = langargs.check(str(source), str(lang), problems.append)
        total_checked += checked
        total_skipped += skipped

    if total_checked == 0:
        problems.append("no Component.translatable calls were found at all — "
                        "the scan pattern no longer matches the source, so this "
                        "check is passing by finding nothing")

    if problems:
        print(f"{len(problems)} PROBLEM(S):")
        for problem in problems:
            print("  - " + problem)
        return 1

    note = f", {total_skipped} unparseable and skipped" if total_skipped else ""
    print(f"translated messages: {total_checked} calls across {len(MODULES)} "
          f"modules, every argument count matches{note}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
