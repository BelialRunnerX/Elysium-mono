#!/usr/bin/env python3
"""
Twenty-four tools: a sprite, a model, a recipe and a name for each.

    python3 tools/gen_arsenal.py

The sprites are drawn rather than borrowed, because a hammer is not a shape the
game already has — but they are drawn out of the same six palettes the vanilla
tiers use, so an iron hammer reads as iron from across a room.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumarsenal"

OUT = os.environ.get("ELYSIUM_ARSENAL_OUT", MOD)
ASSETS = os.path.join(OUT, "src/main/resources/assets", NS)
DATA = os.path.join(OUT, "src/main/resources/data", NS)
RULES = os.path.join(MOD, "src/main/java/com/elysium/arsenal/rules/ArsenalRules.java")

try:
    from PIL import Image, ImageDraw
except ImportError:
    print("Pillow is needed to draw the sprites: pip install pillow")
    sys.exit(1)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(obj, handle, indent=2)
        handle.write("\n")


WEAPON_RULES = os.path.join(MOD,
                            "src/main/java/com/elysium/arsenal/rules/WeaponRules.java")


def catalogue():
    """The shapes and tiers, read out of the Java rather than typed again."""
    source = open(RULES, encoding="utf-8").read()
    broads = [(name, wide == "true") for name, wide in
              re.findall(r'new Broad\("([a-z_]+)", (true|false),', source)]
    tiers = re.findall(r'"([a-z]+)"',
                       re.search(r"TIERS =\s*List\.of\(([^)]*)\)", source).group(1))
    return broads, tiers


def weapons():
    """The six kinds, likewise. A seventh here is a seventh everywhere."""
    source = open(WEAPON_RULES, encoding="utf-8").read()
    found = re.findall(
        r'new Weapon\("([a-z_]+)", ([\d.]+)F, ([\d.]+)F, ([\d.]+)F, (\d+), '
        r'([\d.]+)F\)', source)
    kinds = [(name, float(d), float(sp), float(r), int(sw), float(pi))
             for name, d, sp, r, sw, pi in found]
    # The vanilla sword in outclassesTheGame() is built the same way and is
    # not one of ours, so it is counted out by name rather than by hoping the
    # regex does not reach it.
    kinds = [k for k in kinds if k[0] != "sword"]
    declared = len(re.findall(r'new Weapon\("', source))
    if len(kinds) != declared - 1:
        print(f"the rules declare {declared - 1} weapons and this file could read "
              f"{len(kinds)}; something was wrapped onto a second line")
        sys.exit(1)
    return kinds


# ---------------------------------------------------------------------------
# The palettes
# ---------------------------------------------------------------------------
#
# Four shades each, roughly the vanilla tool colours, so a diamond hammer reads
# as diamond before anybody looks at the shape.

HEADS = {
    "wooden": [(84, 62, 36), (118, 88, 52), (152, 116, 72), (186, 150, 100)],
    "stone": [(62, 62, 62), (92, 92, 92), (122, 122, 122), (154, 154, 154)],
    "iron": [(118, 118, 118), (156, 156, 156), (196, 196, 196), (226, 226, 226)],
    "golden": [(130, 96, 22), (186, 142, 36), (232, 190, 58), (252, 226, 130)],
    "diamond": [(38, 128, 122), (58, 176, 166), (90, 216, 206), (150, 240, 232)],
    "netherite": [(38, 32, 34), (58, 50, 52), (82, 72, 74), (110, 98, 100)],
}
HANDLE = [(62, 44, 26), (96, 70, 42), (126, 94, 58), (156, 124, 82)]
CLEAR = (0, 0, 0, 0)


def rgba(colour):
    return (colour[0], colour[1], colour[2], 255)


def handle(draw):
    """The stick, corner to corner, on every one of them."""
    for step in range(9):
        x = 3 + step
        y = 12 - step
        draw.point((x, y), fill=rgba(HANDLE[1]))
        draw.point((x, y + 1), fill=rgba(HANDLE[0]))
        if step % 3 == 0:
            draw.point((x, y), fill=rgba(HANDLE[2]))


def head(shape, ramp, draw):
    if shape == "hammer":
        draw.rectangle([9, 2, 14, 7], fill=rgba(ramp[1]), outline=rgba(ramp[0]))
        draw.line([(10, 3), (13, 3)], fill=rgba(ramp[3]))
        draw.line([(10, 6), (13, 6)], fill=rgba(ramp[0]))
    elif shape == "excavator":
        draw.polygon([(8, 5), (14, 1), (15, 7), (10, 8)], fill=rgba(ramp[1]),
                     outline=rgba(ramp[0]))
        draw.line([(11, 3), (14, 5)], fill=rgba(ramp[3]))
    elif shape == "scythe":
        draw.arc([6, 1, 15, 9], start=200, end=350, fill=rgba(ramp[2]))
        draw.arc([6, 0, 15, 8], start=200, end=350, fill=rgba(ramp[3]))
        draw.arc([6, 2, 15, 10], start=200, end=350, fill=rgba(ramp[0]))
    else:                                    # paxel: a bit of all three
        draw.polygon([(8, 6), (11, 1), (14, 2), (12, 7)], fill=rgba(ramp[1]),
                     outline=rgba(ramp[0]))
        draw.rectangle([12, 2, 15, 5], fill=rgba(ramp[2]), outline=rgba(ramp[0]))
        draw.point((13, 3), fill=rgba(ramp[3]))


def sprite(shape, tier):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    handle(draw)
    head(shape, HEADS[tier], draw)
    return image


def blade(kind, ramp, draw):
    """One weapon's head, drawn rather than borrowed."""
    if kind == "sabre":
        draw.line([(6, 10), (13, 3)], fill=rgba(ramp[2]))
        draw.line([(7, 10), (14, 3)], fill=rgba(ramp[3]))
        draw.line([(6, 11), (13, 4)], fill=rgba(ramp[0]))
        draw.point((14, 2), fill=rgba(ramp[3]))
    elif kind == "longsword":
        draw.line([(5, 11), (13, 3)], fill=rgba(ramp[2]))
        draw.line([(6, 11), (14, 3)], fill=rgba(ramp[3]))
        draw.line([(5, 12), (13, 4)], fill=rgba(ramp[0]))
        draw.line([(3, 12), (6, 9)], fill=rgba(ramp[1]))
    elif kind == "greatsword":
        draw.polygon([(4, 12), (6, 14), (14, 4), (12, 2)], fill=rgba(ramp[1]),
                     outline=rgba(ramp[0]))
        draw.line([(5, 12), (13, 4)], fill=rgba(ramp[3]))
        draw.line([(2, 13), (6, 9)], fill=rgba(ramp[2]))
    elif kind == "spear":
        draw.polygon([(11, 5), (14, 1), (15, 5), (12, 7)], fill=rgba(ramp[2]),
                     outline=rgba(ramp[0]))
        draw.point((14, 2), fill=rgba(ramp[3]))
    elif kind == "warhammer":
        draw.rectangle([9, 1, 15, 7], fill=rgba(ramp[1]), outline=rgba(ramp[0]))
        draw.line([(10, 2), (14, 2)], fill=rgba(ramp[3]))
        draw.line([(10, 6), (14, 6)], fill=rgba(ramp[0]))
        draw.line([(11, 4), (13, 4)], fill=rgba(ramp[2]))
    else:                                    # estoc: a needle, and a guard
        draw.line([(5, 11), (14, 2)], fill=rgba(ramp[3]))
        draw.line([(5, 12), (13, 4)], fill=rgba(ramp[1]))
        draw.line([(4, 10), (6, 12)], fill=rgba(ramp[2]))
        draw.point((15, 1), fill=rgba(ramp[3]))


def weapon_sprite(kind, tier):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    handle(draw)
    blade(kind, HEADS[tier], draw)
    return image


def draw_weapons(kinds, tiers):
    folder = os.path.join(ASSETS, "textures/item")
    os.makedirs(folder, exist_ok=True)
    made = 0
    for tier in tiers:
        for row in kinds:
            weapon_sprite(row[0], tier).save(
                os.path.join(folder, f"{tier}_{row[0]}.png"))
            write(os.path.join(ASSETS, "models/item", f"{tier}_{row[0]}.json"),
                  {"parent": "item/handheld",
                   "textures": {"layer0": f"{NS}:item/{tier}_{row[0]}"}})
            made += 1
    return made


#: What one of each is made of, in the grid. A weapon is its material and
#: sticks, the way the game's own are, so nobody skips a tier to get one.
WEAPON_GRIDS = {
    "sabre":      ["  M", " M ", "S  "],
    "longsword":  ["  M", " M ", " S "],
    "greatsword": [" MM", "MM ", "S  "],
    "spear":      ["  M", " S ", "S  "],
    "warhammer":  ["MMM", " S ", " S "],
    # Straight up the middle rather than diagonal: a thrusting blade, and
    # more to the point a grid no other weapon here shares. Its first draft
    # was the sabre's grid exactly, which the check below caught before it
    # could ship as a weapon nobody could craft.
    "estoc":      [" M ", " M ", " S "],
}

#: What each tier is made of, in the grid.
WEAPON_MATERIAL = {
    "wooden": {"tag": "minecraft:planks"},
    "stone": {"tag": "minecraft:stone_tool_materials"},
    "iron": {"item": "minecraft:iron_ingot"},
    "golden": {"item": "minecraft:gold_ingot"},
    "diamond": {"item": "minecraft:diamond"},
    "netherite": {"item": "minecraft:netherite_ingot"},
}

WEAPON_NAMES = {"sabre": "Sabre", "longsword": "Longsword",
                "greatsword": "Greatsword", "spear": "Spear",
                "warhammer": "Warhammer", "estoc": "Estoc"}


def weapon_data(kinds, tiers):
    written = 0
    for tier in tiers:
        for row in kinds:
            kind = row[0]
            write(os.path.join(DATA, "recipe", f"{tier}_{kind}.json"), {
                "type": "minecraft:crafting_shaped",
                "pattern": WEAPON_GRIDS[kind],
                "key": {"M": WEAPON_MATERIAL[tier],
                        "S": {"item": "minecraft:stick"}},
                "result": {"id": f"{NS}:{tier}_{kind}", "count": 1},
            })
            written += 1
    # The one damage type in the module: the half of an estoc's hit that
    # armour does not stop. It needs to bypass armour and to say something
    # when it kills somebody, or the death message is blank.
    write(os.path.join(DATA, "damage_type", "piercing.json"),
          {"message_id": "elysiumarsenal.piercing", "exhaustion": 0.1,
           "scaling": "when_caused_by_living_non_player"})
    write(os.path.join(OUT, "src/main/resources/data/minecraft/tags/damage_type",
                       "bypasses_armor.json"),
          {"replace": False, "values": [f"{NS}:piercing"]})
    written += 2
    return written


def draw_all(broads, tiers):
    folder = os.path.join(ASSETS, "textures/item")
    os.makedirs(folder, exist_ok=True)
    made = 0
    for tier in tiers:
        for shape, _ in broads:
            sprite(shape, tier).save(os.path.join(folder, f"{tier}_{shape}.png"))
            made += 1
    return made


def models(broads, tiers):
    for tier in tiers:
        for shape, _ in broads:
            id = f"{tier}_{shape}"
            write(os.path.join(ASSETS, "models/item", id + ".json"),
                  {"parent": "item/handheld",
                   "textures": {"layer0": f"{NS}:item/{id}"}})
    return len(broads) * len(tiers)


# ---------------------------------------------------------------------------
# Crafting
# ---------------------------------------------------------------------------
#
# Each one is the vanilla tools it replaces plus its material, so the recipe
# says what the tool is before anybody reads a tooltip — and so that nobody
# skips the tier it is built on.

MATERIAL = {
    "wooden": {"tag": "minecraft:planks"},
    "stone": {"tag": "minecraft:stone_tool_materials"},
    "iron": {"item": "minecraft:iron_ingot"},
    "golden": {"item": "minecraft:gold_ingot"},
    "diamond": {"item": "minecraft:diamond"},
    "netherite": {"item": "minecraft:netherite_ingot"},
}

BUILT_FROM = {
    "hammer": ["pickaxe"],
    "excavator": ["shovel"],
    "scythe": ["hoe"],
    "paxel": ["pickaxe", "axe", "shovel"],
}

#: Where the tool it is built on goes in the grid, and where the material goes.
PATTERNS = {
    "hammer": ["MMM", "MTM", " S "],
    "excavator": ["MTM", "MSM", " S "],
    "scythe": [" MM", "MT ", "S  "],
    "paxel": ["ABC", " S ", " S "],
}


def recipe(shape, tier):
    id = f"{tier}_{shape}"
    key = {"M": MATERIAL[tier], "S": {"item": "minecraft:stick"}}
    if shape == "paxel":
        key = {"A": {"item": f"minecraft:{tier}_pickaxe"},
               "B": {"item": f"minecraft:{tier}_axe"},
               "C": {"item": f"minecraft:{tier}_shovel"},
               "S": {"item": "minecraft:stick"}}
    else:
        key["T"] = {"item": f"minecraft:{tier}_{BUILT_FROM[shape][0]}"}
    return {"type": "minecraft:crafting_shaped", "category": "equipment",
            "pattern": PATTERNS[shape], "key": key,
            "result": {"id": f"{NS}:{id}", "count": 1}}


def data_files(broads, tiers):
    written = 0
    for tier in tiers:
        for shape, _ in broads:
            write(os.path.join(DATA, "recipe", f"{tier}_{shape}.json"),
                  recipe(shape, tier))
            written += 1
    tags = os.path.join(OUT, "src/main/resources/data/minecraft/tags/item")
    write(os.path.join(tags, "durability_sensitive.json"),
          {"replace": False,
           "values": [f"{NS}:{tier}_{shape}" for tier in tiers for shape, _ in broads]})
    written += 1
    return written


# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

SHAPE_NAMES = {"hammer": "Hammer", "excavator": "Excavator", "scythe": "Scythe",
               "paxel": "Paxel"}
TIER_NAMES = {"wooden": "Wooden", "stone": "Stone", "iron": "Iron",
              "golden": "Golden", "diamond": "Diamond", "netherite": "Netherite"}


def language(broads, tiers, kinds):
    entries = {"itemGroup.elysiumarsenal": "Elysium Arsenal"}
    for tier in tiers:
        for shape, _ in broads:
            entries[f"item.{NS}.{tier}_{shape}"] = \
                f"{TIER_NAMES[tier]} {SHAPE_NAMES[shape]}"
        for row in kinds:
            entries[f"item.{NS}.{tier}_{row[0]}"] = \
                f"{TIER_NAMES[tier]} {WEAPON_NAMES[row[0]]}"
    entries["death.attack.elysiumarsenal.piercing"] = "%1$s was run through by %2$s"
    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(entries.items())))
    return len(entries)


if __name__ == "__main__":
    broads, tiers = catalogue()
    unknown = [s for s, _ in broads if s not in SHAPE_NAMES or s not in PATTERNS] \
        + [t for t in tiers if t not in TIER_NAMES or t not in HEADS]
    if unknown:
        print(f"the rules and this file disagree: {unknown} have no sprite, pattern or "
              f"name here")
        sys.exit(1)
    for shape, wide in broads:
        used = sum(row.count("T") + row.count("A") + row.count("B") + row.count("C")
                   for row in PATTERNS[shape])
        if used != len(BUILT_FROM[shape]):
            print(f"{shape}'s grid holds {used} of the tools it is built on and it is "
                  f"built on {len(BUILT_FROM[shape])}")
            sys.exit(1)
    kinds = weapons()
    nameless = [k[0] for k in kinds
                if k[0] not in WEAPON_NAMES or k[0] not in WEAPON_GRIDS]
    if nameless:
        print(f"the weapon rules and this file disagree: {nameless} have no sprite, "
              f"grid or name here")
        sys.exit(1)
    # Two weapons on one grid is one recipe and whichever the game listed
    # first, and the loser is a weapon nobody can make.
    laid = {}
    for kind, grid in WEAPON_GRIDS.items():
        key = min("|".join(grid), "|".join(row[::-1] for row in grid))
        if key in laid:
            print(f"{kind} and {laid[key]} are laid out the same way, so they are one "
                  f"recipe and one of them cannot be made at all")
            sys.exit(1)
        laid[key] = kind
    sprites = draw_all(broads, tiers)
    made = models(broads, tiers)
    data = data_files(broads, tiers)
    sprites += draw_weapons(kinds, tiers)
    made += len(kinds) * len(tiers)
    data += weapon_data(kinds, tiers)
    keys = language(broads, tiers, kinds)
    print(f"arsenal: {sprites} sprite(s), {made} model(s), {data} data file(s), "
          f"{keys} language key(s)")
