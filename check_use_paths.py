#!/usr/bin/env python3
"""
A block with two ways of being used lets the empty hand reach the second.

    python3 check_use_paths.py

The game asks a block two questions when a player right-clicks it: useItemOn,
with whatever is held, and — only if that answers
PASS_TO_DEFAULT_BLOCK_INTERACTION — useWithoutItem. A block that overrides
both and answers the empty hand with anything else has a useWithoutItem
nothing can reach: the code compiles, the held-item path works, and the
empty-hand feature (a chooser, a readout) never opens for anyone. Found by
hand at the transmuter, 28 Sep 2026, whose chooser had never once opened;
the logistics terminal had the same shape.

So: every class in every module that overrides both is held to passing
somewhere in the file, or to handing useItemOn to a library helper that
passes (Runs.dye, which the conduits use). A module validator cannot see
this, because the rule is about a pair of methods and a game contract, not
about any one module's feature.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
RUNS = os.path.join(ROOT, "elysium-lib/src/main/java/com/elysium/lib/run/Runs.java")
COMMENT = re.compile(r"//[^\n]*|/\*.*?\*/", re.S)
PASS = "PASS_TO_DEFAULT_BLOCK_INTERACTION"


def read(path):
    return COMMENT.sub(" ", open(path, encoding="utf-8").read())


def main():
    problems = []
    helpers_that_pass = set()
    if os.path.isfile(RUNS):
        runs = read(RUNS)
        for name, body in re.findall(r"public static ItemInteractionResult (\w+)\([^)]*\)\s*\{(.*?)\n    \}", runs, re.S):
            if PASS in body:
                helpers_that_pass.add(name)
    paired = 0
    for module in sorted(os.listdir(ROOT)):
        src = os.path.join(ROOT, module, "src/main/java")
        if not module.startswith("elysium-") or not os.path.isdir(src):
            continue
        for dirpath, _, filenames in os.walk(src):
            for filename in filenames:
                if not filename.endswith(".java"):
                    continue
                path = os.path.join(dirpath, filename)
                text = read(path)
                if "useItemOn(" not in text or "useWithoutItem(" not in text:
                    continue
                if not re.search(r"ItemInteractionResult useItemOn\(", text) \
                        or not re.search(r"InteractionResult useWithoutItem\(", text):
                    continue
                paired += 1
                rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
                if PASS in text:
                    continue
                # The hand-off has to be in useItemOn itself: a helper that
                # passes, called from some other method, passes nothing here.
                use_item = re.search(r"ItemInteractionResult useItemOn\([^)]*\)\s*\{(.*?)\n    \}", text, re.S)
                delegated = re.findall(r"return Runs\.(\w+)\(", use_item.group(1)) if use_item else []
                if any(name in helpers_that_pass for name in delegated):
                    continue
                problems.append(f"{rel}: overrides useItemOn and useWithoutItem and never answers "
                                f"{PASS}, so the game never asks useWithoutItem and the empty hand does nothing here")
    if problems:
        print(f"{len(problems)} PROBLEM(S):")
        for problem in problems:
            print("  - " + problem)
        return 1
    print(f"use paths          : {paired} block(s) with both use paths, every one letting the empty hand "
          f"through to useWithoutItem ({len(helpers_that_pass)} library helper(s) that pass)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
