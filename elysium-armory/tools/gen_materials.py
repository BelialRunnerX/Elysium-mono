#!/usr/bin/env python3
"""Generate elysium-armory's material files from the Silent Gear inventory.

Source: docs/reference/silentgear_inventory.md, appendices M1 (what each
material is), M2 (its main-part properties) and M3 (its other part types).
Those tables were read off the mod's own data files; this turns them into
Elysium's material format, which the game loads from
data/elysiumarmory/elysium_materials/**.json (SPEC_CLONE P2.2-P2.4).

Elysium's rulings applied here: a plain number in the tables is an AVERAGE
modifier, "+x" an ADD, "xb" a MULTIPLY_BASE and "xt" a MULTIPLY_TOTAL, which
are exactly ArmoryOperation's; nothing is clamped, because ArmoryCompute keeps
only floors. Re-runnable: the output is a pure function of the inventory.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOC = os.path.join(HERE, "..", "docs", "reference", "silentgear_inventory.md")
OUT = os.path.join(HERE, "src/main/resources/data/elysiumarmory/elysium_materials")
NS = "elysiumarmory"

# The M2 columns, in order, as property keys. "" is a column the loader does not read.
M2_COLUMNS = ["id", "durability", "armor_durability", "harvest_tier", "harvest_speed", "attack_damage",
              "attack_speed", "magic_damage", "ranged_damage", "draw_speed", "projectile_speed",
              "projectile_accuracy", "armor", "armor/helmet", "armor/chestplate", "armor/leggings",
              "armor/boots", "armor_toughness", "knockback_resistance", "magic_armor",
              "enchantment_value", "charging_value", "rarity", "repair_value", "other", "traits"]


def rows(title):
    """Every row of one appendix table, as a list of cells."""
    text = open(DOC, encoding="utf-8").read()
    start = text.index("## " + title)
    body = text[start:]
    end = body.find("\n## ", 3)
    body = body[:end] if end > 0 else body
    out = []
    for line in body.splitlines():
        line = line.strip()
        if not line.startswith("|") or set(line) <= set("|-: "):
            continue
        cells = [c.strip() for c in line.strip("|").split("|")]
        if cells and cells[0] in ("#", "id", "material", "part id"):
            continue
        out.append(cells)
    return out


def ingredient(text):
    """The M1 ingredient column as an item id or tag, or None when the mod has none yet."""
    text = text.strip()
    if not text or text in ("—", "-"):
        return None
    if text.startswith("sg:"):
        return None            # a Silent Gear item (alloy ingot, custom ingot): P8 supplies ours
    if text.startswith("#"):
        return text
    return text if ":" in text else "minecraft:" + text


NUMBER = r"-?\d+(?:\.\d+)?"


def modifier(text):
    """One cell of M2 or one term of M3 as a modifier object."""
    text = text.strip()
    if text.startswith("+"):
        return {"op": "add", "value": float(text[1:])}
    if text.startswith(("xb", "×b")):
        return {"op": "multiply_base", "value": float(text[2:])}
    if text.startswith(("xt", "×t")):
        return {"op": "multiply_total", "value": float(text[2:])}
    return float(text)         # a plain number is an average


def traits(text):
    """"malleable 3, soft 2 [ratio>=0.5]" -> the trait list."""
    out = []
    for piece in re.findall(r"[a-z_]+\s+\d+(?:\s*\[[^\]]*\])?", text.replace("≥", ">=")):
        name, level = re.match(r"([a-z_]+)\s+(\d+)", piece).groups()
        conditions = re.findall(r"\[([^\]]*)\]", piece)
        entry = {"trait": name, "level": int(level)}
        if conditions:
            entry["conditions"] = [c.strip().replace("NOT ", "not:").replace("on ", "on:")
                                   for c in conditions[0].split(" and ")]
        out.append(entry)
    return out


def put(block, key, value):
    if key in block:
        existing = block[key] if isinstance(block[key], list) else [block[key]]
        existing.append(value)
        block[key] = existing
    else:
        block[key] = value


def main_block(row):
    """One M2 row as the material's "main" part properties."""
    block = {}
    for column, cell in zip(M2_COLUMNS[1:], row[1:]):
        cell = cell.strip()
        if not cell or column == "id":
            continue
        if column == "harvest_tier":
            block["harvest_tier"] = re.match(r"([a-z_]+)", cell).group(1)
        elif column == "traits":
            if traits(cell):
                block["traits"] = traits(cell)
        elif column == "other":
            for term in cell.split(","):
                term = term.strip()
                if not term:
                    continue
                key, value = term.split("=")
                put(block, key.strip(), modifier(value))
        else:
            put(block, column, modifier(cell))
    return block


def other_block(cells):
    """One M3 row: 'armor_durability xb0.05, durability xt0.3 & +2, harvest_tier netherite(4)'."""
    block = {}
    text = cells[2].replace("×", "x").replace("≥", ">=")
    for term in text.split(","):
        term = term.strip()
        if not term:
            continue
        if term.startswith("harvest_tier"):
            block["harvest_tier"] = re.search(r"harvest_tier\s+([a-z_]+)", term).group(1)
            continue
        m = re.match(r"([a-z_]+(?:/[a-z_]+)?)\s+(.*)$", term)
        if not m:
            raise SystemExit("unreadable M3 term: " + term)
        key, rest = m.groups()
        for piece in rest.split("&"):
            put(block, key, modifier(piece.strip()))
    if len(cells) > 3 and traits(cells[3]):
        block["traits"] = traits(cells[3])
    return block


def main(out=OUT):
    m1 = rows("Appendix M1: all 132 material files")
    m2 = {r[0]: r for r in rows("Appendix M2: main-part properties per material "
                                "(72 materials with a `silentgear:main` block)")}
    m3 = {}
    for cells in rows("Appendix M3: non-main part-type properties per material "
                      "(rod, tip, binding, grip, cord, fletching, lining, coating, setting)"):
        m3.setdefault(cells[0], {})[cells[1]] = other_block(cells)

    written = 0
    for cells in m1:
        _, mid, name, kind, parent, categories, ingr, rod, salvage = cells[:9]
        material = {"type": kind}
        if parent and parent != "empty":
            material["parent"] = f"{NS}:{parent}"
        crafting = {}
        if ingredient(ingr):
            crafting["ingredient"] = ingredient(ingr)
        if categories.strip():
            crafting["categories"] = [c.strip() for c in categories.split(",") if c.strip()]
        if salvage.strip() == "N":
            crafting["can_salvage"] = False
        if rod.strip():
            crafting["part_substitutes"] = {"rod": rod.strip() if rod.startswith("#") or ":" in rod
                                            else "minecraft:" + rod.strip()}
        if crafting:
            material["crafting"] = crafting
        display = {}
        if not name.startswith("block."):
            display["name"] = name
        if display:
            material["display"] = display
        properties = {}
        if mid in m2:
            properties["main"] = main_block(m2[mid])
        for part, block in sorted(m3.get(mid, {}).items()):
            properties[part] = block
        if properties:
            material["properties"] = properties
        path = os.path.join(out, mid + ".json")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(material, f, indent=2)
            f.write("\n")
        written += 1
    print(f"wrote {written} material(s): {len(m2)} with a main part, "
          f"{sum(len(v) for v in m3.values())} other part blocks")


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else OUT))
