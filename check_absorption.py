#!/usr/bin/env python3
"""
absorption: every shield, bulwark and barrier goes through the one helper.

Since 1.20.5 the game clamps a living thing's absorption to its max_absorption
attribute, which is 0 on everything nobody gave one. Five places in the pack
set absorption straight - the Praetor's field, the bulwark drones, overheal,
the stat shield, the barrier rune - and every one of them set it to 0, until
the mechanics survey of 27 September 2026 read a bulwark drone's 0.0 back.
com.elysium.lib.combat.ElysiumAbsorption raises the ceiling first; this names
any other call, in any module, so the silent zero cannot come back.
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
HELPER = os.path.join("elysium-lib", "src", "main", "java", "com", "elysium", "lib", "combat", "ElysiumAbsorption.java")
CALL = re.compile(r"(?<![A-Za-z0-9_])setAbsorptionAmount\(")   # a bare call inside the entity counts too


def main():
    problems = []
    seen = 0
    for module in sorted(os.listdir(HERE)):
        src = os.path.join(HERE, module, "src", "main", "java")
        if not module.startswith("elysium-") or not os.path.isdir(src):
            continue
        for root, _dirs, files in os.walk(src):
            for name in files:
                if not name.endswith(".java"):
                    continue
                path = os.path.join(root, name)
                rel = os.path.relpath(path, HERE)
                if rel == HELPER:
                    continue
                text = open(path, encoding="utf-8").read()
                for match in CALL.finditer(text):
                    seen += 1
                    line = text.count("\n", 0, match.start()) + 1
                    problems.append(f"{rel.replace(os.sep, '/')}:{line}: sets absorption straight, which the game clamps to a ceiling of 0")
    for problem in problems:
        print("FAIL " + problem)
    if problems:
        print(f"absorption         : {len(problems)} call(s) set absorption without raising its ceiling; use ElysiumAbsorption.set")
        return 1
    print("absorption         : every shield, bulwark and barrier goes through ElysiumAbsorption, which raises the ceiling first")
    return 0


if __name__ == "__main__":
    sys.exit(main())
