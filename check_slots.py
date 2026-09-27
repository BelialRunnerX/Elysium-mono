#!/usr/bin/env python3
"""
slots: no machine asks its own refusing slot for room through insertItem.

A machine's output slot answers isItemValid with no, so a hopper cannot fill
the room a product needs. That same no is what the machine hears when it
asks the slot for room with insertItem(slot, result, true): the answer is
always "does not fit", and the machine stands with its input in hand for
ever. The pot had this bug and agronomy's validator caught it for the pot;
the crusher, the rolling mill, the arc furnace, the separator, every atomics
works, every kinetics machine, the alloy smelter, the refinery, the
centrifuge, the hives and the rock generators had the same one, found on
27 September 2026 by feeding them on a live server.

The cure is one place: com.elysium.lib.item.ElysiumSlots.roomFor reads the
slot. This check names every simulated insert into a slot whose name says it
is an output, in every module, so the bug cannot come back in a new machine.
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

# An insert, simulated or real, into a slot named for output: the slot refuses both.
ASKING = re.compile(r"\.insertItem\(\s*([A-Za-z_][A-Za-z0-9_]*(?:\s*\+\s*[A-Za-z_][A-Za-z0-9_]*)?)\s*,[^;]*?,\s*(?:true|false)\s*\)")
OUTPUT_SLOT = re.compile(r"^(OUT|OUTPUT|FIRST_OUT|RECOVERED|PRODUCT|RESULT|[A-Z_]*_OUT|OUT_[A-Z_]*)(\s*\+.*)?$")


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
                text = open(path, encoding="utf-8").read()
                for match in ASKING.finditer(text):
                    seen += 1
                    slot = match.group(1).replace(" ", "")
                    if OUTPUT_SLOT.match(slot):
                        line = text.count("\n", 0, match.start()) + 1
                        rel = os.path.relpath(path, HERE).replace(os.sep, "/")
                        problems.append(f"{rel}:{line}: goes through insertItem on its output slot {slot}, which refuses everyone")
    for problem in problems:
        print("FAIL " + problem)
    if problems:
        print(f"slots              : {len(problems)} machine(s) ask a refusing slot whether a product fits; "
              f"the answer is always no. Read the slot with ElysiumSlots.roomFor instead")
        return 1
    print(f"slots              : {seen} insert(s) across the modules, none into an output slot")
    return 0


if __name__ == "__main__":
    sys.exit(main())
