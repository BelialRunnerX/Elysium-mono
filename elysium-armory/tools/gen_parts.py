#!/usr/bin/env python3
"""Generate elysium-armory's part files from the Silent Gear inventory.

Source: docs/reference/silentgear_inventory.md, appendix P1 (all 48 part
definitions). A part is the shape: what gear type it belongs to, which kind of
part it is, how many materials its recipe takes, and the property offsets the
shape itself carries — a sword blade is +3 attack damage and +1.6 attack speed
whatever it is made of (SPEC_CLONE P2.7).

Output: data/elysiumarmory/elysium_parts/<id>.json. Re-runnable.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(HERE, "tools"))
from gen_materials import rows, traits, modifier, put            # noqa: E402  the same tables, the same rules

OUT = os.path.join(HERE, "src/main/resources/data/elysiumarmory/elysium_parts")
NS = "elysiumarmory"

#: The extra ingredients P1 names, under the ids this pack has. The compound
#: part recipe charges them, one each (ArmoryPlanRules.extras). Silent Gear's
#: bort is a gem of its own and the base game has none, so a setting takes the
#: foundry's, the one the pack ships (it is all of c:gems/bort). By id, not by
#: that tag: extras are a list, and a tag inside a list is what check_datapack
#: refuses. This wrote "minecraft:" in front of every name once, and
#: "minecraft:bort" named nothing (found 29 September 2026).
EXTRAS = {
    "glass_bottle": "minecraft:glass_bottle",
    "heavy_core": "minecraft:heavy_core",
    "bort": "elysiumfoundry:bort",
}


def extra_id(name):
    """One P1 extra ingredient as the id or #tag the recipe asks for."""
    name = name.strip()
    if name not in EXTRAS:
        raise SystemExit(f"P1 names an extra ingredient '{name}' that EXTRAS has no id for; "
                         f"say which item or tag it is")
    return EXTRAS[name]


def offsets(text):
    """'attack_damage +2 & xbase -0.5; attack_speed +2.8' -> the part's own modifiers."""
    block = {}
    text = text.replace("×base", "xb").replace("×total", "xt").replace("×", "x")
    for term in text.split(";"):
        term = term.strip()
        if not term:
            continue
        m = re.match(r"([a-z_]+(?:/[a-z_]+)?)\s+(.*)$", term)
        if not m:
            raise SystemExit("unreadable P1 term: " + term)
        key, rest = m.groups()
        for piece in rest.split("&"):
            piece = piece.strip().replace("+-", "-")
            put(block, key, modifier(piece.replace("xb ", "xb").replace("xt ", "xt")))
    return block


def main(out=OUT):
    written = 0
    for cells in rows("Appendix P1: all 48 part definitions"):
        pid, kind, gear_type, part_type, count, extra, props, part_traits, applies = cells[:9]
        part = {"type": kind, "gear_type": gear_type.split(" (")[0].strip(), "part_type": part_type}
        blacklist = re.search(r"blacklist ([a-z_,\s]+)\)", gear_type)
        if blacklist:
            part["gear_type_blacklist"] = [b.strip() for b in blacklist.group(1).split(",")]
        crafting = {}
        number = re.match(r"(\d+)", count.strip())
        if number:
            crafting["materials"] = int(number.group(1))
        categories = re.search(r"cat ([a-z_,\s]+)\)", count)
        if categories:
            crafting["categories"] = [c.strip() for c in categories.group(1).split(",")]
        made = re.search(r"x(\d+)\s*$", count.strip())
        if made:
            crafting["count"] = int(made.group(1))
        if extra.strip():
            crafting["extra"] = [extra_id(e) for e in extra.split(",") if e.strip()]
        if crafting:
            part["crafting"] = crafting
        properties = offsets(props)
        if traits(part_traits):
            properties["traits"] = traits(part_traits)
        if properties:
            part["properties"] = properties
        if kind == "upgrade" and applies.strip():
            targets = applies.split("(")[0].strip()
            part["upgrade"] = {"gear_types": [t.strip() for t in targets.split(",") if t.strip()],
                               "match_children": "+children" in applies}
        os.makedirs(OUT, exist_ok=True)
        with open(os.path.join(out, pid + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(part, f, indent=2)
            f.write("\n")
        written += 1
    print(f"wrote {written} part definition(s)")


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else OUT))
