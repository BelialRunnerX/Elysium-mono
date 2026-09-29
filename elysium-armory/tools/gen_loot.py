#!/usr/bin/env python3
"""Write elysium-armory's loot: the templates found in the base game's chests.

A template is a plan somebody drew and left behind, used up by the one part it
makes (SPEC_CLONE P3.3, P6.1). Nothing handed one out until 29 September 2026:
the Gear Smithing Table draws blueprints only. Where a template turns up, and
how often, is ArmoryPlanRules.TEMPLATES; this reads that table and writes

  data/elysiumarmory/loot_table/found/template.json         one template, found blank
  data/elysiumarmory/loot_modifiers/template_from_<chest>.json   one a row of the table
  data/neoforge/loot_modifiers/global_loot_modifiers.json     the list the game applies

The template is found blank and reads as a plan the first time a player
carries it (ArmoryBlueprintItem.inventoryTick), so the table names no part.

Usage: gen_loot.py [root]. The root defaults to src/main/resources; the
validator passes a scratch directory and compares. Re-runnable.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RULES = os.path.join(HERE, "src/main/java/com/elysium/armory/property/ArmoryPlanRules.java")
OUT = os.path.join(HERE, "src/main/resources")
NS = "elysiumarmory"
#: The table every modifier adds: one template.
TABLE = "found/template"


def rows():
    """ArmoryPlanRules.TEMPLATES as (chest table, chance) pairs, in order."""
    text = open(RULES, encoding="utf-8").read()
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    table = re.search(r"List<Found> TEMPLATES = List\.of\((.*?)\);", text, re.S)
    if not table:
        raise SystemExit("ArmoryPlanRules has no TEMPLATES table to read")
    found = re.findall(r'new Found\("([a-z0-9_:/.]+)", ([0-9.]+)D?\)', table.group(1))
    if not found:
        raise SystemExit("ArmoryPlanRules.TEMPLATES names no chest")
    return [(chest, float(chance)) for chest, chance in found]


def modifier(chest):
    """The loot modifier's name for one chest: template_from_<the table's last word>."""
    return "template_from_" + chest.rsplit("/", 1)[1]


def write(path, body):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(body, handle, indent=2)
        handle.write("\n")


def main(out=OUT):
    data = os.path.join(out, "data")
    write(os.path.join(data, NS, "loot_table", TABLE + ".json"), {
        "type": "minecraft:chest",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:template"}]}]})
    names = []
    for chest, chance in rows():
        name = modifier(chest)
        write(os.path.join(data, NS, "loot_modifiers", name + ".json"), {
            "type": "neoforge:add_table",
            "conditions": [
                {"condition": "neoforge:loot_table_id", "loot_table_id": chest},
                {"condition": "minecraft:random_chance", "chance": chance}],
            "table": f"{NS}:{TABLE}"})
        names.append(f"{NS}:{name}")
    write(os.path.join(data, "neoforge", "loot_modifiers", "global_loot_modifiers.json"),
          {"replace": False, "entries": names})
    print(f"wrote the found template and the {len(names)} chest(s) it turns up in")


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else OUT))
