#!/usr/bin/env python3
"""
The Cordon's materials, drawn.

    python3 tools/gen_material_art.py

Every item and block sprite for the materials in rules/CordonMaterials.java —
raw lumps, ingots, nuggets, gems, shards, cloth, the creatures' leavings, the
riveted plate, and a block of each — in the Voidforged style the rest of
Elysium is drawn in: one derived ramp per material (art/materials.py), the
element's glow as the only saturated thing on the sprite (art/style.py), one
outline, lit from the top-left. Nothing is copied from Valoria; nothing is
recoloured from vanilla.

Deterministic: the same roster draws the same bytes, so validate.py can draw
it again into a scratch directory and compare. Run after gen_textures.py,
which owns the ore faces in the same block directory and leaves these alone.
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
ROOT = os.path.dirname(MOD)
sys.path.insert(0, os.path.join(ROOT, "art"))

import materials as material_art  # noqa: E402
import pixelart  # noqa: E402
import sprites  # noqa: E402
import style  # noqa: E402
from canvas import Canvas, rect, union  # noqa: E402

NS = "elysiumores"
OUT = os.environ.get("ELYSIUM_ORES_OUT", MOD)
ITEM = os.path.join(OUT, "src/main/resources/assets", NS, "textures/item")
BLOCK = os.path.join(OUT, "src/main/resources/assets", NS, "textures/block")
ROSTER = os.path.join(MOD, "src/main/java/com/elysium/ores/rules/CordonMaterials.java")

# The element each roster element draws its glow with; NONE draws no light.
GLOW_OF = {"VOID": "void", "PLASMA": "plasma", "NEURAL": "neural",
           "DIMENSIONAL": "dimensional", "KINETIC": "kinetic", "NONE": None}


# ---------------------------------------------------------------------------
# The roster, read rather than retyped
# ---------------------------------------------------------------------------

def roster():
    """[(family, kind, element, colour)], [(drop, element, colour)], [(part, family, colour)]."""
    source = open(ROSTER, encoding="utf-8").read()
    body = re.sub(r"/\*.*?\*/", " ", source, flags=re.S)
    body = re.sub(r"//[^\n]*", " ", body)
    materials = []
    for match in re.finditer(r'\b(dug|alloy|found)\("([a-z_]+)",\s*(?:Kind\.(\w+),\s*)?'
                             r'(?:OreRules\.\w+,\s*)?Element\.(\w+),\s*"(#[0-9a-f]{6})"', body):
        call, family, kind, element, colour = match.groups()
        materials.append((family, kind or "ALLOY", element, colour))
    drops = re.findall(r'new Drop\("([a-z_]+)",\s*Element\.(\w+),\s*"(#[0-9a-f]{6})"', body)
    parts = re.findall(r'new Part\("([a-z_]+)",\s*"([a-z_]+)",\s*"(#[0-9a-f]{6})"', body)
    if not materials or not drops or not parts:
        raise SystemExit("CordonMaterials.java did not parse; the roster's shape changed")
    return materials, drops, parts


def glow_ramp(element):
    name = GLOW_OF.get(element)
    return style.GLOW[name] if name else None


# ---------------------------------------------------------------------------
# Shapes. Each is a set of (x, y) on a 16x16 canvas.
# ---------------------------------------------------------------------------

def lump():
    """A raw chunk: broken off, not cast."""
    return union(rect(5, 2, 10, 3), rect(3, 4, 12, 6), rect(2, 7, 12, 10),
                 rect(3, 11, 11, 12), rect(5, 13, 9, 13)) - {(2, 7), (12, 10), (3, 11)}


def nugget():
    return union(rect(5, 6, 10, 9), rect(6, 5, 9, 10))


def gem():
    """A cut stone: a lozenge with a flat table."""
    return {(x, y) for y in range(16) for x in range(16)
            if abs(x - 7.5) + abs(y - 7.5) <= 6.0 and y >= 3}


def plate():
    """A riveted rectangle."""
    return rect(2, 4, 13, 11)


def cloth():
    return rect(2, 2, 13, 13)


def shell():
    """A pressure shell: a dome on a flat lip."""
    dome = {(x, y) for y in range(16) for x in range(16)
            if (x - 7.5) ** 2 / 36.0 + (y - 9.0) ** 2 / 25.0 <= 1.0 and y <= 10}
    return dome | rect(2, 10, 13, 11)


def vial():
    return union(rect(6, 1, 9, 2), rect(5, 3, 10, 12), rect(6, 13, 9, 13))


def scales():
    """Chitin: three overlapping plates."""
    return union(rect(2, 3, 8, 8), rect(6, 6, 13, 11), rect(3, 9, 9, 13))


# ---------------------------------------------------------------------------
# Drawing
# ---------------------------------------------------------------------------

def plated(shape, ramp, glow, core=None, rivets=()):
    c = Canvas()
    c.plate(shape, ramp)
    for (x, y) in rivets:
        c.put(x, y, ramp[1])
        c.put(x, y - 1, ramp[5])
    if glow and core:
        c.glow(core, glow, within=shape)
    c.outline(ramp[0])
    return c


def woven(ramp, size=16, offset=0):
    c = Canvas(size, size)
    for y in range(size):
        for x in range(size):
            if offset and not (offset <= x < size - offset and offset <= y < size - offset):
                continue
            over = (x // 2 + y // 2) % 2 == 0
            c.put(x, y, ramp[4 if over else 3] if (x + y) % 3 else ramp[2])
    if offset:
        c.outline(ramp[0])
    return c


def raw_block(ramp):
    """Lumps of raw metal bedded in their own dust."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.put(x, y, ramp[2] if (x * 7 + y * 3) % 5 else ramp[1])
    for (ox, oy) in ((1, 1), (9, 2), (4, 8), (11, 9), (7, 12)):
        for (x, y) in union(rect(ox, oy + 1, ox + 4, oy + 3), rect(ox + 1, oy, ox + 3, oy + 4)):
            if c.inside(x, y):
                c.put(x, y, ramp[4] if y == oy or x == ox else ramp[3])
    return c


def item_sprite(name, kind, ramp, glow, family=None):
    """The one sprite for one item, by what the item is."""
    if name.startswith("raw_"):
        return plated(lump(), ramp, glow, core=[(7, 7), (8, 7)] if glow else None)
    if name.endswith("_ingot"):
        return pixelart.render("ingot", ramp, glow)
    if name.endswith("_nugget"):
        return plated(nugget(), ramp, None)
    if name.endswith("_shard") or kind == "SHARD" and name not in ("chitin",):
        return pixelart.render("shard", ramp, glow)
    if name.endswith("_plate"):
        return plated(plate(), ramp, glow, core=[(7, 7), (8, 7), (7, 8), (8, 8)] if glow else None,
                      rivets=[(3, 5), (12, 5), (3, 10), (12, 10)])
    if name == "chitin":
        return plated(scales(), ramp, glow, core=[(7, 8)] if glow else None)
    if kind == "CLOTH":
        return woven(ramp, offset=2)
    if name == "pressure_shell":
        return plated(shell(), ramp, glow, core=[(7, 6), (8, 6)] if glow else None,
                      rivets=[(5, 9), (10, 9)])
    if name == "bio_culture":
        c = Canvas()
        glass = material_art.ramp("#9ab0b8")
        c.plate(vial(), glass)
        fill = rect(6, 6, 9, 12)
        if glow:
            c.glow(fill, glow, within=fill, halo=False)
        c.outline(glass[0])
        return c
    if name == "blight_crystal" or name.endswith("_crystal"):
        return pixelart.render("shard", ramp, glow)
    if name == "ember_slag":
        return plated(lump(), ramp, glow, core=[(6, 8), (7, 8), (8, 9)] if glow else None)
    # a gem, or anything else finished and small
    return plated(gem(), ramp, glow, core=[(7, 7), (8, 7), (7, 8), (8, 8)] if glow else None)


def block_sprite(name, kind, ramp, glow, family):
    if name.startswith("raw_"):
        return raw_block(ramp)
    if kind == "CLOTH":
        return woven(ramp)
    style.METAL[family] = ramp
    return sprites.storage_block(family, GLOW_OF.get(glow_name_of[family]) or "inert")


glow_name_of = {}


def save(canvas, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    canvas.img.save(path)


def main():
    materials, drops, parts = roster()
    if "inert" not in style.GLOW:
        # A material with no element still needs a seam colour for its block.
        style.GLOW["inert"] = ["#1c1c22", "#2c2c34", "#3c3c46", "#4c4c58", "#6c6c7a"]
    items = blocks = 0
    for family, kind, element, colour in materials:
        if kind == "EXTERNAL":
            continue
        glow_name_of[family] = element
        ramp = material_art.ramp(colour)
        glow = glow_ramp(element)
        names = {"METAL": [f"raw_{family}", f"{family}_ingot", f"{family}_nugget"],
                 "ALLOY": [f"{family}_ingot", f"{family}_nugget"]}.get(kind, [family])
        for name in names:
            save(item_sprite(name, kind, ramp, glow, family), os.path.join(ITEM, name + ".png"))
            items += 1
        block_names = [f"{family}_block"] + ([f"raw_{family}_block"] if kind == "METAL" else [])
        for name in block_names:
            save(block_sprite(name, kind, ramp, element, family), os.path.join(BLOCK, name + ".png"))
            blocks += 1
    for name, element, colour in drops:
        save(item_sprite(name, "DROP", material_art.ramp(colour), glow_ramp(element)),
             os.path.join(ITEM, name + ".png"))
        items += 1
    for name, family, colour in parts:
        element = next(e for f, _k, e, _c in materials if f == family)
        save(item_sprite(name, "PART", material_art.ramp(colour), glow_ramp(element)),
             os.path.join(ITEM, name + ".png"))
        items += 1
    print(f"material sprites  : {items} items, {blocks} blocks")


if __name__ == "__main__":
    main()
