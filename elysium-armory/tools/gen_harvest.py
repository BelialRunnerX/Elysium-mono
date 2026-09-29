#!/usr/bin/env python3
"""Write elysium-armory's mineable tags: which tool each bench is for.

A block breaks at the speed of the tool the game lists it under, and one that
asks for the right tool drops nothing unless some tool is listed as right. The
harvest audit of 29 September 2026 found every bench in no tool's tag. Which
tool is for which bench is ArmoryHarvestRules; this reads its lists and writes

  data/minecraft/tags/block/mineable/<tool>.json     one a tool with any bench on it

One write per tag file, from the whole list at once: a generator that wrote a
tag in pieces would keep only the last piece.

Usage: gen_harvest.py [root]. The root defaults to src/main/resources; the
validator passes a scratch directory and compares. Re-runnable.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RULES = os.path.join(HERE, "src/main/java/com/elysium/armory/property/ArmoryHarvestRules.java")
OUT = os.path.join(HERE, "src/main/resources")
NS = "elysiumarmory"
#: The four tools the game keeps a list for, as ArmoryHarvestRules.TOOLS names them.
TOOLS = ("pickaxe", "axe", "shovel", "hoe")


def rules():
    text = open(RULES, encoding="utf-8").read()
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", " ", text)


def listing(text, name):
    """ArmoryHarvestRules.<name>, a List.of of block ids, in order."""
    found = re.search(r"List<String> " + name + r" = List\.of\(([^;]*?)\);", text, re.S)
    if not found:
        raise SystemExit(f"ArmoryHarvestRules has no {name} list to read")
    return re.findall(r'"([a-z_]+)"', found.group(1))


def write(path, body):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(body, handle, indent=2)
        handle.write("\n")


def main(out=OUT):
    text = rules()
    tags = os.path.join(out, "data/minecraft/tags/block/mineable")
    written = []
    for tool in TOOLS:
        blocks = listing(text, tool.upper())
        if not blocks:
            continue
        write(os.path.join(tags, tool + ".json"),
              {"replace": False, "values": [f"{NS}:{block}" for block in blocks]})
        written.append(f"{len(blocks)} by {tool}")
    print("wrote the mineable tags: " + ", ".join(written))


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else OUT))
