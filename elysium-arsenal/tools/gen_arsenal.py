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
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
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


def weapon_tiers():
    """Every rung a weapon or a bow comes in, read out of WeaponRules: the
    game's six by name, then the Cordon's in the order the rules place them
    (by Valoria's damage, ties keeping their declaration order). Returns
    (names, {name: (material tag, colour)} for the Cordon's)."""
    source = open(WEAPON_RULES, encoding="utf-8").read()
    game = re.findall(r'game\("([a-z]+)", [\d.]+F, [A-Z_\d.]+F?, (?:true|false)\)', source)
    cordon = re.findall(r'new Cordon\("([a-z_]+)", (\d+), (\d+), ([\d.]+)F, ([\d.]+)F, (\d+), '
                        r'"(c:[a-z_/]+)", "(#[0-9a-f]{6})"\)', source)
    if len(game) != 6 or not cordon:
        print("the rungs could not be read out of WeaponRules.java; something was wrapped")
        sys.exit(1)
    cordon.sort(key=lambda row: float(row[4]))
    names = game + [row[0] for row in cordon]
    details = {row[0]: (row[6], row[7]) for row in cordon}
    return names, details


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


def shades_of(colour):
    """Four shades from one hue, for a rung the game has no palette for."""
    base = tuple(int(colour[i:i + 2], 16) for i in (1, 3, 5))

    def step(factor):
        return tuple(max(0, min(255, round(channel * factor))) for channel in base)
    return [step(0.5), step(0.75), step(1.0), step(1.3)]


# The Cordon's rungs join the six palettes, the materials and the names, read
# off the rules so a rung added there is a rung here.
WEAPON_TIERS, CORDON_DETAILS = weapon_tiers()
for _name, (_material, _colour) in CORDON_DETAILS.items():
    HEADS[_name] = shades_of(_colour)


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
for _name, (_material, _colour) in CORDON_DETAILS.items():
    WEAPON_MATERIAL[_name] = {"tag": _material}

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
for _name in CORDON_DETAILS:
    TIER_NAMES[_name] = _name.replace("_", " ").title()


def language(broads, tiers, kinds, weapon_tiers=None):
    entries = {"itemGroup.elysiumarsenal": "Elysium Arsenal"}
    for tier in tiers:
        for shape, _ in broads:
            entries[f"item.{NS}.{tier}_{shape}"] = \
                f"{TIER_NAMES[tier]} {SHAPE_NAMES[shape]}"
    for tier in (weapon_tiers or tiers):
        for row in kinds:
            entries[f"item.{NS}.{tier}_{row[0]}"] = \
                f"{TIER_NAMES[tier]} {WEAPON_NAMES[row[0]]}"
    entries["death.attack.elysiumarsenal.piercing"] = "%1$s was run through by %2$s"
    return entries


# ---------------------------------------------------------------------------
# The bows, the arrows, and the twenty-four that have names
# ---------------------------------------------------------------------------
#
# Everything below reads its catalogue out of the Java, the same way the tools
# and the weapons above do. Nothing here decides what exists.

RANGED_RULES = os.path.join(MOD,
                            "src/main/java/com/elysium/arsenal/rules/RangedRules.java")
UNIQUE_RULES = os.path.join(MOD,
                            "src/main/java/com/elysium/arsenal/rules/UniqueRules.java")


def bows():
    """The six kinds, read rather than typed."""
    source = open(RANGED_RULES, encoding="utf-8").read()
    found = re.findall(
        r'new Bow\("([a-z_]+)", ([\d.]+)F, ([\d.]+)F, ([\d.]+)F, (\d+), '
        r'([\d.]+)F, ([\d.]+)F\)', source)
    declared = len(re.findall(r'new Bow\("', source))
    if len(found) != declared:
        print(f"the rules declare {declared} bows and this file could read "
              f"{len(found)}; something was wrapped onto a second line")
        sys.exit(1)
    return [name for name, *_ in found]


def shots():
    """The twelve kinds of ammunition, and which of them are bolts."""
    source = open(RANGED_RULES, encoding="utf-8").read()
    found = re.findall(
        r'new Shot\("([a-z_]+)", ([\d.]+)F, "([a-z]*)", (true|false)\)', source)
    declared = len(re.findall(r'new Shot\("', source))
    if len(found) != declared:
        print(f"the rules declare {declared} shots and this file could read "
              f"{len(found)}")
        sys.exit(1)
    return [(name, effect, bolt == "true") for name, _, effect, bolt in found]


def uniques():
    """The twenty-four, with the shape and tier each is built on."""
    source = open(UNIQUE_RULES, encoding="utf-8").read()
    found = re.findall(
        r'unique\("([a-z_]+)", "([a-z_]+)", "([a-z]+)", (\d+), List\.of\(([^)]*)\)\)',
        source)
    declared = len(re.findall(r'^\s+unique\("', source, re.M))
    if len(found) != declared:
        print(f"the rules declare {declared} uniques and this file could read "
              f"{len(found)}")
        sys.exit(1)
    return [(id, shape, tier, int(rarity),
             re.findall(r'"([a-z_]+)"', abilities)) for id, shape, tier, rarity,
            abilities in found]


# ---------------------------------------------------------------------------
# Drawing a bow
# ---------------------------------------------------------------------------
#
# Four frames apiece: at rest, and three through the draw. The frames are not
# decoration. The game asks the model how far through a draw it is and picks a
# frame; an item that animates a draw and has one frame holds the unpulled
# picture for the whole draw and then fires, which reads as a bow that has
# stopped working rather than as a missing texture, so nobody reports it.

STRING = [(196, 192, 180), (232, 228, 216)]


def limb(draw, ramp, kind):
    """The wood or metal of the bow, which is the part that says which it is."""
    if kind == "sling":
        draw.arc([4, 3, 12, 13], start=250, end=110, fill=rgba(ramp[1]))
        draw.rectangle([5, 6, 8, 9], outline=rgba(ramp[2]))
    elif kind == "repeater":
        draw.arc([2, 1, 12, 15], start=270, end=90, fill=rgba(ramp[1]))
        draw.line([(4, 5), (11, 5)], fill=rgba(ramp[2]))
        draw.line([(4, 8), (11, 8)], fill=rgba(ramp[2]))
        draw.line([(4, 11), (11, 11)], fill=rgba(ramp[2]))
    elif kind == "recurve":
        draw.arc([2, 1, 12, 15], start=270, end=90, fill=rgba(ramp[1]))
        draw.arc([6, 0, 14, 5], start=90, end=250, fill=rgba(ramp[2]))
        draw.arc([6, 11, 14, 16], start=110, end=270, fill=rgba(ramp[2]))
    elif kind == "longbow":
        draw.arc([1, 0, 13, 16], start=268, end=92, fill=rgba(ramp[1]))
        draw.arc([2, 0, 14, 16], start=268, end=92, fill=rgba(ramp[0]))
    elif kind == "crossbow":
        draw.line([(3, 8), (14, 8)], fill=rgba(ramp[1]))
        draw.arc([2, 2, 8, 14], start=270, end=90, fill=rgba(ramp[2]))
        draw.line([(10, 7), (13, 7)], fill=rgba(ramp[0]))
    else:                                    # shortbow
        draw.arc([3, 2, 12, 14], start=270, end=90, fill=rgba(ramp[1]))
        draw.arc([4, 2, 13, 14], start=270, end=90, fill=rgba(ramp[0]))


#: Where the string sits at rest, and at each third of a draw. A crossbow's
#: string travels less because it is already most of the way back.
DRAWN_TO = {
    "sling": (11, 6),
    "shortbow": (12, 6),
    "repeater": (12, 6),
    "recurve": (12, 7),
    "longbow": (13, 8),
    "crossbow": (12, 3),
}


def bow_sprite(kind, tier, frame):
    """One bow, at one of four points through its draw."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    ramp = HEADS[tier]
    limb(draw, ramp, kind)
    rest, travel = DRAWN_TO[kind]
    back = rest - int(round(travel * frame / 3.0))
    top, bottom = (2, 13) if kind != "crossbow" else (4, 12)
    draw.line([(rest, top), (back, 8)], fill=rgba(STRING[0]))
    draw.line([(back, 8), (rest, bottom)], fill=rgba(STRING[0]))
    if frame > 0:
        # The nocked arrow, which is what makes a drawn bow read as drawn.
        draw.line([(back, 8), (min(15, back + 8), 8)], fill=rgba(HANDLE[1]))
        draw.point((min(15, back + 8), 8), fill=rgba(STRING[1]))
    return image


def arrow_sprite(name, effect, bolt):
    """One shot: a shaft, a head coloured by what it does, and fletching."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    tip = EFFECT_COLOURS.get(effect, EFFECT_COLOURS[""])
    draw.line([(3, 12), (12, 3)], fill=rgba(HANDLE[1]))
    draw.line([(4, 12), (12, 4)], fill=rgba(HANDLE[0]))
    draw.polygon([(11, 4), (14, 1), (12, 5)], fill=rgba(tip), outline=rgba(ramp_dark(tip)))
    if bolt:
        # A bolt is stubbier and has no fletching, which is the whole visible
        # difference between the two and the reason a player can tell at a
        # glance which bow it goes in.
        draw.rectangle([2, 11, 4, 13], fill=rgba(tip))
    else:
        draw.line([(2, 13), (5, 10)], fill=rgba(STRING[1]))
        draw.line([(2, 11), (4, 13)], fill=rgba(STRING[0]))
    return image


#: What each effect looks like. A player who has seen one rime arrow knows the
#: next blue-tipped thing is cold without reading anything.
EFFECT_COLOURS = {
    "": (168, 168, 176),
    "bleed": (168, 34, 40),
    "burning": (232, 126, 32),
    "slowing": (120, 186, 232),
    "sundering": (196, 108, 52),
    "marking": (238, 232, 132),
    "chaining": (146, 206, 240),
    "piercing": (142, 96, 200),
}


def ramp_dark(colour):
    return tuple(max(0, channel - 60) for channel in colour[:3])


#: The accent each unique carries, by the domain of its first ability. Read
#: from the ability rather than chosen per unique, so two uniques that do the
#: same sort of thing look like they do.
ACCENTS = {
    "swiftfoot": (120, 220, 160), "riposte": (232, 232, 200),
    "static_arc": (146, 206, 240), "keen_edge": (240, 216, 140),
    "ember_brand": (232, 126, 32), "cleave": (208, 90, 60),
    "sunder": (196, 108, 52), "shockwave": (200, 176, 240),
    "sure_grip": (160, 200, 160), "chill_touch": (120, 186, 232),
    "hunters_mark": (238, 232, 132), "steadfast": (170, 170, 190),
    "bulwark": (150, 160, 200), "night_eyes": (110, 96, 180),
    "bloodscent": (168, 34, 40), "soul_harvest": (140, 230, 220),
    "gleaner": (200, 220, 140), "mender": (140, 230, 170),
    "gravewake": (110, 110, 140),
}


def unique_sprite(shape, tier, abilities, ranged, frame=0):
    """A named weapon: its shape, at its tier, with its own colour on it."""
    if ranged:
        image = bow_sprite(shape, tier, frame)
    else:
        image = Image.new("RGBA", (16, 16), CLEAR)
        draw = ImageDraw.Draw(image)
        handle(draw)
        blade(shape, HEADS[tier], draw)
    accent = ACCENTS.get(abilities[0] if abilities else "", (255, 255, 255))
    draw = ImageDraw.Draw(image)
    # Along the edge rather than over it: an accent that covers the blade
    # makes every unique of a shape look like the same item in six colours.
    pixels = image.load()
    for y in range(16):
        for x in range(16):
            if pixels[x, y][3] == 0:
                continue
            if (x + y) % 5 == 0:
                draw.point((x, y), fill=rgba(accent))
    return image


# ---------------------------------------------------------------------------
# What they are made of
# ---------------------------------------------------------------------------
#
# Each grid is its own; two bows laid out the same way are one recipe and one
# bow nobody can make. The check at the bottom of this file is what keeps that
# true, and it has already caught one.

BOW_GRIDS = {
    "sling":    ["S S", " M ", " M "],
    "shortbow": [" MS", "M S", " MS"],
    "repeater": ["MSM", "MMM", " S "],
    "recurve":  ["SMS", "M M", "SMS"],
    "longbow":  ["MS ", "MS ", "MS "],
    "crossbow": ["MSM", "SMS", " M "],
}

BOW_NAMES = {"sling": "Sling", "shortbow": "Shortbow", "repeater": "Repeater",
             "recurve": "Recurve Bow", "longbow": "Longbow",
             "crossbow": "Crossbow"}

#: The tip each shot is made of. Distinct per shot within its own pattern,
#: because two shots made of the same thing in the same grid are one recipe.
SHOT_TIPS = {
    "flint_arrow": {"item": "minecraft:flint"},
    "iron_arrow": {"item": "minecraft:iron_nugget"},
    "barbed_arrow": {"item": "minecraft:iron_ingot"},
    "ember_arrow": {"item": "minecraft:blaze_powder"},
    "rime_arrow": {"item": "minecraft:packed_ice"},
    "sundering_arrow": {"item": "minecraft:amethyst_shard"},
    "hollow_arrow": {"item": "minecraft:glass"},
    "thunder_arrow": {"item": "minecraft:copper_ingot"},
    "void_arrow": {"item": "minecraft:ender_pearl"},
    "iron_bolt": {"item": "minecraft:iron_ingot"},
    "piercing_bolt": {"item": "minecraft:quartz"},
    "shattering_bolt": {"item": "minecraft:prismarine_shard"},
}

SHOT_NAMES = {
    "flint_arrow": "Flint Arrow", "iron_arrow": "Iron Arrow",
    "barbed_arrow": "Barbed Arrow", "ember_arrow": "Ember Arrow",
    "rime_arrow": "Rime Arrow", "sundering_arrow": "Sundering Arrow",
    "hollow_arrow": "Hollow Arrow", "thunder_arrow": "Thunder Arrow",
    "void_arrow": "Void Arrow", "iron_bolt": "Iron Bolt",
    "piercing_bolt": "Piercing Bolt", "shattering_bolt": "Shattering Bolt",
}

#: How many one crafting comes to. Four, the way the game's own arrow does.
SHOT_COUNT = 4

#: What a unique costs beyond its own shape, by how rare it is. A rarity that
#: cost nothing extra would be a word in a tooltip.
UNIQUE_CATALYSTS = {
    1: "minecraft:amethyst_shard",
    2: "minecraft:echo_shard",
    3: "minecraft:heart_of_the_sea",
    4: "minecraft:nether_star",
}

UNIQUE_NAMES = {
    "whisperfang": "Whisperfang", "duelists_grace": "Duelist's Grace",
    "stormfang": "Stormfang", "wardens_edge": "Warden's Edge",
    "emberline": "Emberline", "kingsmourn": "Kingsmourn",
    "grovecleaver": "Grovecleaver", "sunderer": "Sunderer",
    "worldsplitter": "Worldsplitter", "riverreach": "Riverreach",
    "frostpike": "Frostpike", "skypiercer": "Skypiercer",
    "stoneshaper": "Stoneshaper", "faultline": "Faultline",
    "mountainfall": "Mountainfall", "needlepoint": "Needlepoint",
    "bloodletter": "Bloodletter", "soulpiercer": "Soulpiercer",
    "pebblethrower": "Pebblethrower", "swiftstring": "Swiftstring",
    "hailstorm": "Hailstorm", "hornbow_of_the_wild": "Hornbow of the Wild",
    "sunspine": "Sunspine", "gravebolt": "Gravebolt",
}

ABILITY_NAMES = {
    "swiftfoot": "Swiftfoot", "riposte": "Riposte", "static_arc": "Static Arc",
    "keen_edge": "Keen Edge", "ember_brand": "Ember Brand", "cleave": "Cleave",
    "sunder": "Sunder", "shockwave": "Shockwave", "sure_grip": "Sure Grip",
    "chill_touch": "Chill Touch", "hunters_mark": "Hunter's Mark",
    "steadfast": "Steadfast", "bulwark": "Bulwark", "night_eyes": "Night Eyes",
    "bloodscent": "Bloodscent", "soul_harvest": "Soul Harvest",
    "gleaner": "Gleaner", "mender": "Mender", "gravewake": "Gravewake",
}


def bow_model(id):
    """A bow's model, and the three frames the game swaps to as it is drawn.

    The override list is ordered from least pulled to most because the game
    takes the *last* override whose predicates all hold, so a list in the
    other order would show the fully-drawn frame the instant a draw started.
    """
    return {
        "parent": "item/generated",
        "textures": {"layer0": f"{NS}:item/{id}"},
        "display": {
            "thirdperson_righthand": {"rotation": [-80, 260, -40],
                                      "translation": [-1, -2, 2.5],
                                      "scale": [0.9, 0.9, 0.9]},
            "thirdperson_lefthand": {"rotation": [-80, -280, 40],
                                     "translation": [-1, -2, 2.5],
                                     "scale": [0.9, 0.9, 0.9]},
            "firstperson_righthand": {"rotation": [0, -90, 25],
                                      "translation": [1.13, 3.2, 1.13],
                                      "scale": [0.68, 0.68, 0.68]},
            "firstperson_lefthand": {"rotation": [0, 90, -25],
                                     "translation": [1.13, 3.2, 1.13],
                                     "scale": [0.68, 0.68, 0.68]},
        },
        "overrides": [
            {"predicate": {"pulling": 1}, "model": f"{NS}:item/{id}_pulling_0"},
            {"predicate": {"pulling": 1, "pull": 0.65},
             "model": f"{NS}:item/{id}_pulling_1"},
            {"predicate": {"pulling": 1, "pull": 0.9},
             "model": f"{NS}:item/{id}_pulling_2"},
        ],
    }


def ranged_files(kinds, tiers, ammunition):
    sprites = made = data = 0
    folder = os.path.join(ASSETS, "textures/item")
    os.makedirs(folder, exist_ok=True)
    for tier in tiers:
        for kind in kinds:
            id = f"{tier}_{kind}"
            bow_sprite(kind, tier, 0).save(os.path.join(folder, id + ".png"))
            sprites += 1
            write(os.path.join(ASSETS, "models/item", id + ".json"), bow_model(id))
            made += 1
            for frame in range(3):
                drawn = f"{id}_pulling_{frame}"
                bow_sprite(kind, tier, frame + 1).save(
                    os.path.join(folder, drawn + ".png"))
                write(os.path.join(ASSETS, "models/item", drawn + ".json"),
                      {"parent": f"{NS}:item/{id}",
                       "textures": {"layer0": f"{NS}:item/{drawn}"}})
                sprites += 1
                made += 1
            write(os.path.join(DATA, "recipe", id + ".json"), {
                "type": "minecraft:crafting_shaped",
                "category": "equipment",
                "pattern": BOW_GRIDS[kind],
                "key": {"M": WEAPON_MATERIAL[tier],
                        "S": {"item": "minecraft:string"}},
                "result": {"id": f"{NS}:{id}", "count": 1},
            })
            data += 1
    for name, effect, bolt in ammunition:
        arrow_sprite(name, effect, bolt).save(os.path.join(folder, name + ".png"))
        sprites += 1
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": "item/generated",
               "textures": {"layer0": f"{NS}:item/{name}"}})
        made += 1
        pattern = [" M ", " S ", " S "] if bolt else [" M ", " S ", " F "]
        key = {"M": SHOT_TIPS[name], "S": {"item": "minecraft:stick"}}
        if not bolt:
            key["F"] = {"item": "minecraft:feather"}
        write(os.path.join(DATA, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "equipment",
            "pattern": pattern, "key": key,
            "result": {"id": f"{NS}:{name}", "count": SHOT_COUNT},
        })
        data += 1
    # Our arrows have to be in the game's arrow tag or no bow in the game,
    # ours included, will accept one of them.
    write(os.path.join(OUT, "src/main/resources/data/minecraft/tags/item",
                       "arrows.json"),
          {"replace": False,
           "values": [f"{NS}:{name}" for name, _, bolt in ammunition if not bolt]})
    data += 1
    return sprites, made, data


def unique_files(named):
    sprites = made = data = 0
    folder = os.path.join(ASSETS, "textures/item")
    os.makedirs(folder, exist_ok=True)
    bow_kinds = set(BOW_GRIDS)
    for id, shape, tier, rarity, abilities in named:
        ranged = shape in bow_kinds
        unique_sprite(shape, tier, abilities, ranged).save(
            os.path.join(folder, id + ".png"))
        sprites += 1
        if ranged:
            write(os.path.join(ASSETS, "models/item", id + ".json"), bow_model(id))
            made += 1
            for frame in range(3):
                drawn = f"{id}_pulling_{frame}"
                unique_sprite(shape, tier, abilities, True, frame + 1).save(
                    os.path.join(folder, drawn + ".png"))
                write(os.path.join(ASSETS, "models/item", drawn + ".json"),
                      {"parent": f"{NS}:item/{id}",
                       "textures": {"layer0": f"{NS}:item/{drawn}"}})
                sprites += 1
                made += 1
        else:
            write(os.path.join(ASSETS, "models/item", id + ".json"),
                  {"parent": "item/handheld",
                   "textures": {"layer0": f"{NS}:item/{id}"}})
            made += 1
        # A smithing transform rather than a grid, and on the shape itself:
        # a unique that could be crafted from raw material would let a player
        # skip the weapon it is a version of, which is the whole ladder.
        write(os.path.join(DATA, "recipe", id + ".json"), {
            "type": "minecraft:smithing_transform",
            "template": {"item": f"{NS}:legend_sigil"},
            "base": {"item": f"{NS}:{tier}_{shape}"},
            "addition": {"item": UNIQUE_CATALYSTS[rarity]},
            "result": {"id": f"{NS}:{id}", "count": 1},
        })
        data += 1
    # The template itself, which is the only thing in the module made out of
    # nothing but the game.
    write(os.path.join(ASSETS, "models/item", "legend_sigil.json"),
          {"parent": "item/generated",
           "textures": {"layer0": f"{NS}:item/legend_sigil"}})
    sigil().save(os.path.join(folder, "legend_sigil.png"))
    sprites += 1
    made += 1
    write(os.path.join(DATA, "recipe", "legend_sigil.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": ["GLG", "LDL", "GLG"],
        "key": {"G": {"item": "minecraft:gold_ingot"},
                "L": {"item": "minecraft:lapis_lazuli"},
                "D": {"item": "minecraft:diamond"}},
        "result": {"id": f"{NS}:legend_sigil", "count": 1},
    })
    data += 1
    return sprites, made, data


def sigil():
    """The template: a plate with a mark on it."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    gold = HEADS["golden"]
    draw.rectangle([3, 2, 12, 13], fill=rgba(gold[1]), outline=rgba(gold[0]))
    draw.line([(5, 4), (10, 4)], fill=rgba(gold[3]))
    draw.line([(7, 4), (7, 11)], fill=rgba(gold[3]))
    draw.line([(5, 11), (10, 11)], fill=rgba(gold[2]))
    draw.point((8, 7), fill=rgba((90, 120, 200)))
    draw.point((6, 8), fill=rgba((90, 120, 200)))
    return image


def ranged_language(entries, kinds, tiers, ammunition, named):
    for tier in tiers:
        for kind in kinds:
            entries[f"item.{NS}.{tier}_{kind}"] = \
                f"{TIER_NAMES[tier]} {BOW_NAMES[kind]}"
    for name, _, _ in ammunition:
        entries[f"item.{NS}.{name}"] = SHOT_NAMES[name]
    entries[f"item.{NS}.legend_sigil"] = "Sigil of Legends"
    for id, shape, tier, _, _ in named:
        entries[f"item.{NS}.{id}"] = UNIQUE_NAMES[id]
    for shape, name in dict(list(SHAPE_NAMES.items()) + list(WEAPON_NAMES.items())
                            + list(BOW_NAMES.items())).items():
        entries[f"{NS}.shape.{shape}"] = name
    for tier, name in TIER_NAMES.items():
        entries[f"{NS}.tier.{tier}"] = name
    for ability, name in ABILITY_NAMES.items():
        entries[f"{NS}.ability.{ability}"] = name
    entries[f"{NS}.unique.shape"] = "%1$s \u00b7 %2$s"
    entries[f"{NS}.unique.always"] = "  always"
    entries[f"{NS}.unique.every"] = "  every %1$s seconds"
    return entries


# ---------------------------------------------------------------------------
# The hammers, and the names of the grades they roll
# ---------------------------------------------------------------------------

FORGE_RULES = os.path.join(MOD,
                           "src/main/java/com/elysium/arsenal/rules/ForgeRules.java")


def hammers():
    """The hammers and the kinds each rolls, read out of the Java."""
    source = open(FORGE_RULES, encoding="utf-8").read()
    table = re.search(r"HAMMERS = Map\.of\((.*?)\);", source, re.S).group(1)
    found = re.findall(r'"([a-z_]+)", List\.of\(([^)]*)\)', table)
    return [(name, re.findall(r'"([a-z]+)"', kinds)) for name, kinds in found]


def grades():
    """Every grade, with the kind it lands on."""
    source = open(FORGE_RULES, encoding="utf-8").read()
    return re.findall(r'grade\("([a-z_]+)", "([a-z]+)", \d+', source)


def gear_kinds():
    source = open(FORGE_RULES, encoding="utf-8").read()
    listed = re.search(r"KINDS =\s*List\.of\(([^)]*)\)", source).group(1)
    return re.findall(r'"([a-z]+)"', listed)


#: What each hammer is faced with, which is the only visible difference
#: between the three and the only difference in their recipes.
HAMMER_FACE = {
    "weaponsmiths_hammer": ("iron", {"item": "minecraft:iron_ingot"}),
    "toolsmiths_hammer": ("stone", {"item": "minecraft:copper_ingot"}),
    "armourers_hammer": ("golden", {"item": "minecraft:gold_ingot"}),
}

HAMMER_NAMES = {
    "weaponsmiths_hammer": "Weaponsmith's Hammer",
    "toolsmiths_hammer": "Toolsmith's Hammer",
    "armourers_hammer": "Armourer's Hammer",
}

GEAR_NAMES = {"melee": "melee weapons", "tool": "tools", "armour": "armour",
              "bow": "bows", "shield": "shields"}

#: Every grade's name. Written out rather than made from the id, because
#: "wardens_own" is not "Wardens Own" and "plain_blade" is just "Plain".
GRADE_NAMES = {
    "dull": "Dull", "unwieldy": "Unwieldy", "plain_blade": "Plain",
    "sharp": "Sharp", "swift": "Swift", "keen": "Keen", "heavy": "Heavy",
    "savage": "Savage", "fabled": "Fabled",
    "blunted": "Blunted", "brittle": "Brittle", "plain_tool": "Plain",
    "honed": "Honed", "hasteful": "Hasteful", "tempered": "Tempered",
    "far_reaching": "Far-Reaching", "tireless": "Tireless",
    "masterwrought": "Masterwrought",
    "dented": "Dented", "cumbersome": "Cumbersome", "plain_plate": "Plain",
    "reinforced": "Reinforced", "unchained": "Unchained",
    "resilient": "Resilient", "heavy_plate": "Heavy", "fortified": "Fortified",
    "wardens_own": "Warden's Own",
    "warped": "Warped", "stiff": "Stiff", "plain_bow": "Plain",
    "strung": "Strung", "elastic": "Elastic", "truesighted": "Truesighted",
    "longstrung": "Longstrung", "windcut": "Windcut",
    "stormstrung": "Stormstrung",
    "split": "Split", "weighted": "Weighted", "plain_shield": "Plain",
    "banded": "Banded", "light_shield": "Light", "braced": "Braced",
    "towering": "Towering", "immovable": "Immovable",
    "bulwark_of_ages": "Bulwark of Ages",
}


def hammer_sprite(face):
    """A smith's hammer: a heavy head on a short haft, faced with its metal."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    dark = HEADS["netherite"]
    ramp = HEADS[face]
    for step in range(8):
        x, y = 3 + step, 13 - step
        draw.point((x, y), fill=rgba(HANDLE[1]))
        draw.point((x, y + 1), fill=rgba(HANDLE[0]))
    draw.rectangle([8, 1, 14, 7], fill=rgba(dark[2]), outline=rgba(dark[0]))
    draw.rectangle([8, 2, 9, 6], fill=rgba(ramp[1]))
    draw.line([(11, 2), (13, 2)], fill=rgba(dark[3]))
    draw.line([(11, 6), (13, 6)], fill=rgba(dark[0]))
    return image


def forge_files(smiths):
    sprites = made = data = 0
    folder = os.path.join(ASSETS, "textures/item")
    os.makedirs(folder, exist_ok=True)
    for name, _ in smiths:
        face, material = HAMMER_FACE[name]
        hammer_sprite(face).save(os.path.join(folder, name + ".png"))
        sprites += 1
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": "item/handheld",
               "textures": {"layer0": f"{NS}:item/{name}"}})
        made += 1
        # The three differ in one square, which is the square that says what
        # the hammer is for. Any two of them sharing a grid would be one
        # recipe and two hammers nobody could make separately.
        write(os.path.join(DATA, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "equipment",
            "pattern": ["NFN", "NSN", " S "],
            "key": {"N": {"item": "minecraft:iron_nugget"}, "F": material,
                    "S": {"item": "minecraft:stick"}},
            "result": {"id": f"{NS}:{name}", "count": 1},
        })
        data += 1
    return sprites, made, data


def forge_language(entries, smiths, graded, kinds):
    for name, _ in smiths:
        entries[f"item.{NS}.{name}"] = HAMMER_NAMES[name]
    for id, _ in graded:
        entries[f"{NS}.grade.{id}"] = GRADE_NAMES[id]
    for kind in kinds:
        entries[f"{NS}.gear.{kind}"] = GEAR_NAMES[kind]
    entries[f"{NS}.forge.rolls"] = "Reforges %1$s"
    entries[f"{NS}.forge.how"] = "Hold the gear in your other hand"
    entries[f"{NS}.forge.wrong_hammer"] = \
        "This hammer does not reforge what you are holding"
    entries[f"{NS}.forge.too_poor"] = "Reforging this costs %1$s levels"
    return entries


# ---------------------------------------------------------------------------
# Digging: sites, fossils, totems, artefacts, and what a dig turns up
# ---------------------------------------------------------------------------

DIG_RULES = os.path.join(MOD, "src/main/java/com/elysium/arsenal/rules/DigRules.java")
FOSSIL_RULES = os.path.join(MOD,
                            "src/main/java/com/elysium/arsenal/rules/FossilRules.java")
ARTIFACT_RULES = os.path.join(
    MOD, "src/main/java/com/elysium/arsenal/rules/ArtifactRules.java")
TOTEM_RULES = os.path.join(MOD,
                           "src/main/java/com/elysium/arsenal/rules/TotemRules.java")
DIG_BLOCKS = os.path.join(MOD,
                          "src/main/java/com/elysium/arsenal/registry/DigBlocks.java")


def set_shapes():
    """The shapes the two decorative sets come in, read out of DigBlocks: the
    rotten set's wood shapes and the cracked mud bricks' stone ones, each
    without its full block (planks, bricks), which is a cube."""
    source = open(DIG_BLOCKS, encoding="utf-8").read()
    wood = re.findall(r'"(_[a-z_]+)"', re.search(
        r"WOOD_SHAPES = List\.of\(([^)]*)\)", source).group(1))
    stone = re.findall(r'"(_[a-z_]+)"', re.search(
        r"STONE_SHAPES = List\.of\(([^)]*)\)", source).group(1))
    return [shape for shape in wood if shape != "_planks"], stone


def brushes():
    source = open(DIG_RULES, encoding="utf-8").read()
    return re.findall(r'new Brush\("([a-z_]+)", (\d+), ([\d.]+)F\)', source)


def sites():
    source = open(DIG_RULES, encoding="utf-8").read()
    return re.findall(r'new Site\("([a-z_]+)", "([a-z:_]+)", "([a-z_]+)"\)', source)


def patches():
    """The surface patches, read out of DigRules like everything else here."""
    source = open(DIG_RULES, encoding="utf-8").read()
    found = re.findall(
        r'new Patch\("([a-z_]+)", (\d+), (\d+), (\d+),\s*List\.of\(([^)]*)\),\s*'
        r'List\.of\(([^)]*)\)\)', source)
    declared = len(re.findall(r'new Patch\("', source))
    if len(found) != declared:
        print(f"the rules declare {declared} patches and this file could read "
              f"{len(found)}; something was written in a shape it does not know")
        sys.exit(1)
    return [(site, int(one_in), int(least), int(most),
             re.findall(r'"([^"]+)"', replaces), re.findall(r'"([^"]+)"', biomes))
            for site, one_in, least, most, replaces, biomes in found]


def seams():
    """The buried seams, likewise."""
    source = open(DIG_RULES, encoding="utf-8").read()
    found = re.findall(
        r'new Seam\("([a-z_]+)", (\d+), (\d+), (-?\d+), (-?\d+), "([a-z0-9_:/]+)",\s*'
        r'List\.of\(([^)]*)\)\)', source)
    declared = len(re.findall(r'new Seam\("', source))
    if len(found) != declared:
        print(f"the rules declare {declared} seams and this file could read "
              f"{len(found)}; something was written in a shape it does not know")
        sys.exit(1)
    return [(site, int(tries), int(size), int(lowest), int(highest), within,
             re.findall(r'"([^"]+)"', biomes))
            for site, tries, size, lowest, highest, within, biomes in found]


def fossils():
    source = open(FOSSIL_RULES, encoding="utf-8").read()
    return re.findall(
        r'new Fossil\("([a-z_]+)", "([a-z_]+)", "([a-z_]+)", ([\d.]+)F, (\d+)\)',
        source)


def artifacts():
    source = open(ARTIFACT_RULES, encoding="utf-8").read()
    return re.findall(
        r'new Artifact\("([a-z_]+)", "([a-z_]+)", ([\d.]+)F, (\d+), "([a-z]+)"\)',
        source)


def totems():
    source = open(TOTEM_RULES, encoding="utf-8").read()
    return re.findall(
        r'new Totem\("([a-z_]+)", (PLACED|HELD), ([\d.]+)F, (\d+), (\d+)\)', source)


#: How many stages a site shows, read out of the rules rather than assumed:
#: a fifth stage added there without a fifth model is a blockstate that points
#: at a model that is not there, which is a purple-and-black cube.
def stages():
    source = open(DIG_RULES, encoding="utf-8").read()
    return int(re.search(r"STAGES = (\d+);", source).group(1))


BLOCK_PALETTES = {
    "suspicious_dirt": [(86, 66, 46), (108, 84, 58), (128, 102, 72), (150, 122, 90)],
    "suspicious_red_sand": [(150, 82, 44), (176, 100, 54), (196, 120, 70),
                            (214, 146, 96)],
    "fossiliferous_dirt": [(78, 70, 58), (100, 92, 76), (122, 114, 96),
                           (146, 138, 118)],
    "bone": [(196, 192, 168), (218, 214, 190), (236, 232, 210), (250, 248, 232)],
    "rotten": [(66, 54, 40), (88, 72, 52), (110, 92, 66), (132, 114, 84)],
    "mud": [(96, 84, 74), (118, 104, 90), (138, 124, 108), (158, 144, 126)],
    "totem": [(122, 92, 28), (168, 130, 42), (212, 172, 62), (244, 214, 128)],
    "table": [(72, 58, 42), (98, 80, 58), (124, 104, 76), (150, 130, 98)],
}


def noisy(palette, seed, speckles=None):
    """A 16-by-16 block face: a ramp, stirred, with optional flecks on top.

    The stirring is a hash of the two coordinates rather than a random number,
    so the generator writes the same texture every time it runs -- otherwise
    every run would report every texture as having drifted, and the check that
    nothing generated has been edited by hand would be useless.
    """
    image = Image.new("RGBA", (16, 16), CLEAR)
    pixels = image.load()
    for y in range(16):
        for x in range(16):
            mixed = (x * 374_761_393 + y * 668_265_263 + seed * 2_246_822_519) & 0xFFFFFFFF
            mixed ^= mixed >> 13
            mixed = (mixed * 1_274_126_177) & 0xFFFFFFFF
            pixels[x, y] = rgba(palette[(mixed >> 7) % len(palette)])
    if speckles:
        colour, many = speckles
        for step in range(many):
            mixed = (step * 2_654_435_761 + seed * 40_503) & 0xFFFFFFFF
            x, y = (mixed >> 3) % 16, (mixed >> 11) % 16
            ImageDraw.Draw(image).point((x, y), fill=rgba(colour))
    return image


def steady(text):
    """A hash of a string that is the same in every process.

    Python salts hash() for strings unless the environment says otherwise, so
    a generator that used it drew a different texture every run and the check
    that nothing generated has been edited by hand reported all thirty-three
    of them as drifted -- which is how this was found.
    """
    mixed = 2_166_136_261
    for letter in text.encode("utf-8"):
        mixed = ((mixed ^ letter) * 16_777_619) & 0xFFFFFFFF
    return mixed


def site_texture(block, stage, whole):
    """A dig site, part-way through. Each stage shows a little more of it."""
    base = noisy(BLOCK_PALETTES[block], 11 + steady(block) % 97)
    draw = ImageDraw.Draw(base)
    bone = BLOCK_PALETTES["bone"]
    # A quarter more of the buried thing showing at each stage.
    shown = (stage + 1) / float(whole)
    width = max(1, int(round(10 * shown)))
    draw.rectangle([3, 11, 3 + width, 12], fill=rgba(bone[2]),
                   outline=rgba(bone[0]))
    if shown > 0.5:
        draw.rectangle([5, 8, 5 + width // 2, 9], fill=rgba(bone[3]))
    return base


def fossil_texture(creature, part):
    """Bones in rock: the same ribs for every creature, a different skull."""
    base = noisy(BLOCK_PALETTES["fossiliferous_dirt"], 3 + steady(creature) % 89)
    draw = ImageDraw.Draw(base)
    bone = BLOCK_PALETTES["bone"]
    if part in ("head", "whole"):
        draw.ellipse([4, 2, 11, 8], fill=rgba(bone[2]), outline=rgba(bone[0]))
        draw.point((6, 5), fill=rgba(BLOCK_PALETTES["rotten"][0]))
        draw.point((9, 5), fill=rgba(BLOCK_PALETTES["rotten"][0]))
    if part in ("body", "whole"):
        top = 9 if part == "whole" else 3
        draw.line([(7, top), (7, top + 5)], fill=rgba(bone[3]))
        for rib in range(0, 6, 2):
            draw.line([(4, top + rib), (10, top + rib)], fill=rgba(bone[1]))
    return base


def plain_texture(kind, seed, speckles=None):
    return noisy(BLOCK_PALETTES[kind], seed, speckles)


def brush_texture(name):
    """A brush: a handle and a head of bristles, tiered like everything else."""
    tier = name.split("_")[0]
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    for step in range(7):
        x, y = 3 + step, 12 - step
        draw.point((x, y), fill=rgba(HANDLE[1]))
        draw.point((x, y + 1), fill=rgba(HANDLE[0]))
    ramp = HEADS.get(tier, HEADS["iron"])
    draw.rectangle([9, 3, 12, 6], fill=rgba(ramp[1]), outline=rgba(ramp[0]))
    for bristle in range(4):
        draw.line([(13, 3 + bristle), (15, 2 + bristle)],
                  fill=rgba((236, 232, 210)))
    return image


def shard_texture(pieces):
    """A shard, or several of them fitted together."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    clay = [(150, 106, 74), (182, 136, 96), (208, 166, 122), (232, 200, 160)]
    if pieces == 1:
        draw.polygon([(5, 10), (8, 3), (12, 9), (7, 13)], fill=rgba(clay[2]),
                     outline=rgba(clay[0]))
        draw.line([(7, 6), (9, 10)], fill=rgba(clay[3]))
    else:
        draw.ellipse([3, 3, 12, 12], fill=rgba(clay[1]), outline=rgba(clay[0]))
        draw.line([(3, 8), (12, 8)], fill=rgba(clay[0]))
        draw.line([(8, 3), (8, 12)], fill=rgba(clay[0]))
        draw.point((6, 6), fill=rgba(clay[3]))
    return image


#: What colour each artefact reads in, so three lumps are three things.
ARTIFACT_COLOURS = {
    "penetrating_strike": (196, 92, 92),
    "soaring_winds": (150, 208, 236),
    "tunneling": (166, 146, 110),
}


def artifact_texture(name):
    image = shard_texture(9)
    draw = ImageDraw.Draw(image)
    accent = ARTIFACT_COLOURS.get(name, (220, 220, 220))
    draw.ellipse([6, 6, 9, 9], fill=rgba(accent))
    draw.point((7, 5), fill=rgba(accent))
    draw.point((7, 10), fill=rgba(accent))
    return image


def totem_texture(name, held):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    ramp = BLOCK_PALETTES["totem"]
    if held:
        draw.polygon([(7, 1), (11, 5), (11, 13), (4, 13), (4, 5)],
                     fill=rgba(ramp[1]), outline=rgba(ramp[0]))
        draw.line([(6, 6), (9, 6)], fill=rgba(ramp[3]))
        draw.line([(6, 9), (9, 9)], fill=rgba(ramp[2]))
    else:
        draw.rectangle([2, 0, 13, 15], fill=rgba(ramp[1]), outline=rgba(ramp[0]))
        draw.rectangle([5, 3, 10, 12], outline=rgba(ramp[3]))
        draw.point((7, 7), fill=rgba((240, 240, 200)))
        draw.point((8, 8), fill=rgba((240, 240, 200)))
    return image


def cube(folder, id, texture, sides=None, render_type=None):
    """
    A block that is one texture on all six sides, with its item and its drop.

    render_type is named when the texture has transparent pixels: a modded
    model draws on the solid layer unless it says otherwise, and the solid
    layer paints transparency black.
    """
    texture.save(os.path.join(folder, id + ".png"))
    write(os.path.join(ASSETS, "blockstates", id + ".json"),
          {"variants": {"": {"model": f"{NS}:block/{id}"}}})
    model = {"parent": "block/cube_all", "textures": {"all": f"{NS}:block/{id}"}}
    if render_type:
        model["render_type"] = render_type
    write(os.path.join(ASSETS, "models/block", id + ".json"), model)
    write(os.path.join(ASSETS, "models/item", id + ".json"),
          {"parent": f"{NS}:block/{id}"})
    write(os.path.join(DATA, "loot_table/blocks", id + ".json"), drops(id))
    return 1, 2, 1


def drops(id):
    """The loot table a block that simply drops itself needs."""
    return {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:{id}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    }


# ---------------------------------------------------------------------------
# The two decorative sets, shaped
# ---------------------------------------------------------------------------
#
# Every shape is the game's own: the same parent models, the same states and
# the same turns as its oak or mud brick counterpart, cut from the set's full
# block. Until 29 September 2026 each was a cube with one model for all its
# states, so a slab stood a full block tall over half a block of collision
# and a pressure plate was a block nothing could stand on.

#: Each set's stem, the full block its shapes are cut from, and its palette.
SET_BLOCKS = {"rotten": ("rotten_planks", "rotten"),
              "cracked_mud_brick": ("cracked_mud_bricks", "mud")}


def stair_variants(id):
    """The game's own stair states, turned by facing and turned over for the
    top half. Turning a stair over mirrors it, so an upside-down corner turns
    the other way from one the right way up; a rule that turns every corner
    alike draws those wrong and every state is still there to count, so this
    is checked against the game's own oak stairs, one for one."""
    out = {}
    for facing, y in (("east", 0), ("south", 90), ("west", 180), ("north", 270)):
        for half in ("bottom", "top"):
            for shape, suffix in (("straight", ""), ("inner_left", "_inner"),
                                  ("inner_right", "_inner"), ("outer_left", "_outer"),
                                  ("outer_right", "_outer")):
                turn = y
                if half == "bottom" and shape.endswith("_left"):
                    turn = (y + 270) % 360
                if half == "top" and shape.endswith("_right"):
                    turn = (y + 90) % 360
                model = {"model": f"{NS}:block/{id}{suffix}"}
                if half == "top":
                    model["x"] = 180
                if turn:
                    model["y"] = turn
                if half == "top" or turn:
                    model["uvlock"] = True
                out[f"facing={facing},half={half},shape={shape}"] = model
    return out


def fence_multipart(id):
    """A post, and a side towards each neighbour it joins."""
    parts = [{"apply": {"model": f"{NS}:block/{id}_post"}}]
    for side, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        apply = {"model": f"{NS}:block/{id}_side", "uvlock": True}
        if y:
            apply["y"] = y
        parts.append({"when": {side: "true"}, "apply": apply})
    return parts


def gate_variants(id):
    out = {}
    for facing, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
        for in_wall in ("false", "true"):
            for open_state in ("false", "true"):
                suffix = ("_wall" if in_wall == "true" else "") \
                    + ("_open" if open_state == "true" else "")
                model = {"model": f"{NS}:block/{id}{suffix}", "uvlock": True}
                if y:
                    model["y"] = y
                out[f"facing={facing},in_wall={in_wall},open={open_state}"] = model
    return out


def door_variants(id):
    out = {}
    for facing, y in (("east", 0), ("south", 90), ("west", 180), ("north", 270)):
        for half in ("lower", "upper"):
            for hinge in ("left", "right"):
                for open_state in ("false", "true"):
                    part = "bottom" if half == "lower" else "top"
                    suffix = f"_{part}_{hinge}" + ("_open" if open_state == "true" else "")
                    turn = y
                    if open_state == "true":
                        turn = (y + (90 if hinge == "left" else 270)) % 360
                    model = {"model": f"{NS}:block/{id}{suffix}"}
                    if turn:
                        model["y"] = turn
                    out[f"facing={facing},half={half},hinge={hinge},"
                        f"open={open_state}"] = model
    return out


def trapdoor_variants(id):
    """The game's oak trapdoor: shut, it lies the same way whichever way it
    faces; open, it stands against the side it faces."""
    out = {}
    for facing, y in (("north", 0), ("south", 180), ("east", 90), ("west", 270)):
        for half in ("bottom", "top"):
            for open_state in ("false", "true"):
                if open_state == "true":
                    model = {"model": f"{NS}:block/{id}_open"}
                    if y:
                        model["y"] = y
                else:
                    model = {"model": f"{NS}:block/{id}_{half}"}
                out[f"facing={facing},half={half},open={open_state}"] = model
    return out


def slab_drops(id):
    """A slab's table, the game's own: two from a double slab."""
    return {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:{id}",
                                "functions": [
                                    {"function": "minecraft:set_count", "count": 2,
                                     "add": False,
                                     "conditions": [{
                                         "condition": "minecraft:block_state_property",
                                         "block": f"{NS}:{id}",
                                         "properties": {"type": "double"}}]},
                                    {"function": "minecraft:explosion_decay"}]}]}],
    }


def door_drops(id):
    """A door's table, the game's own: from the lower half only. Breaking
    either half takes the other with it, and that half's table runs too, so a
    table on both halves is two doors for every one broken."""
    return {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:{id}",
                                "conditions": [{
                                    "condition": "minecraft:block_state_property",
                                    "block": f"{NS}:{id}",
                                    "properties": {"half": "lower"}}]}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    }


def door_texture(palette, half):
    """One half of a door: planks up it inside a frame, the handle below."""
    ramp = BLOCK_PALETTES[palette]
    image = noisy(ramp, 31 if half == "bottom" else 43)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], outline=rgba(ramp[0]))
    for x in (5, 10):
        draw.line([(x, 1), (x, 14)], fill=rgba(ramp[0]))
    if half == "bottom":
        draw.line([(13, 1), (13, 3)], fill=rgba(BLOCK_PALETTES["bone"][1]))
    else:
        draw.line([(1, 12), (14, 12)], fill=rgba(ramp[0]))
    return image


def door_sprite(palette):
    """The door in the hand, flat, the way the game draws its own doors."""
    ramp = BLOCK_PALETTES[palette]
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([4, 0, 11, 15], fill=rgba(ramp[1]), outline=rgba(ramp[0]))
    draw.line([(7, 1), (7, 14)], fill=rgba(ramp[2]))
    draw.line([(5, 7), (10, 7)], fill=rgba(ramp[0]))
    draw.point((10, 9), fill=rgba(BLOCK_PALETTES["bone"][1]))
    return image


def shape_files(blocks, items, stem, shape):
    """One shape of a set: the game's own blockstate, models, item model and
    drop for that shape, cut from the set's full block."""
    full, palette = SET_BLOCKS[stem]
    id = stem + shape
    cut = f"{NS}:block/{full}"
    sides = {"bottom": cut, "top": cut, "side": cut}
    item, loot, sprites = f"{NS}:block/{id}", drops(id), 0
    if shape == "_slab":
        models = {id: ("slab", sides), f"{id}_top": ("slab_top", sides)}
        state = {"variants": {"type=bottom": {"model": f"{NS}:block/{id}"},
                              "type=top": {"model": f"{NS}:block/{id}_top"},
                              "type=double": {"model": cut}}}
        loot = slab_drops(id)
    elif shape == "_stairs":
        models = {id: ("stairs", sides), f"{id}_inner": ("inner_stairs", sides),
                  f"{id}_outer": ("outer_stairs", sides)}
        state = {"variants": stair_variants(id)}
    elif shape == "_fence":
        models = {f"{id}{part}": (f"fence{part}", {"texture": cut})
                  for part in ("_post", "_side", "_inventory")}
        state = {"multipart": fence_multipart(id)}
        item = f"{NS}:block/{id}_inventory"
    elif shape == "_fence_gate":
        models = {f"{id}{part}": (f"template_fence_gate{part}", {"texture": cut})
                  for part in ("", "_open", "_wall", "_wall_open")}
        state = {"variants": gate_variants(id)}
    elif shape == "_door":
        halves = {"top": f"{NS}:block/{id}_top", "bottom": f"{NS}:block/{id}_bottom"}
        models = {f"{id}{part}": (f"door{part}", halves)
                  for part in ("_bottom_left", "_bottom_left_open", "_bottom_right",
                               "_bottom_right_open", "_top_left", "_top_left_open",
                               "_top_right", "_top_right_open")}
        state = {"variants": door_variants(id)}
        for half in ("top", "bottom"):
            door_texture(palette, half).save(os.path.join(blocks, f"{id}_{half}.png"))
        door_sprite(palette).save(os.path.join(items, id + ".png"))
        sprites += 3
        item = None
        loot = door_drops(id)
    elif shape == "_trapdoor":
        plain_texture(palette, 31).save(os.path.join(blocks, id + ".png"))
        sprites += 1
        models = {f"{id}{part}": (f"template_trapdoor{part}",
                                  {"texture": f"{NS}:block/{id}"})
                  for part in ("_bottom", "_top", "_open")}
        state = {"variants": trapdoor_variants(id)}
        item = f"{NS}:block/{id}_bottom"
    elif shape == "_pressure_plate":
        models = {id: ("pressure_plate_up", {"texture": cut}),
                  f"{id}_down": ("pressure_plate_down", {"texture": cut})}
        state = {"variants": {"powered=false": {"model": f"{NS}:block/{id}"},
                              "powered=true": {"model": f"{NS}:block/{id}_down"}}}
    else:
        print(f"{id} is a shape this generator has no models for, so it would be "
              f"drawn as nothing")
        sys.exit(1)
    for name, (parent, textures) in models.items():
        model = {"parent": f"minecraft:block/{parent}", "textures": textures}
        # A door and a trapdoor are cut out, as the game's are, so a texture
        # with a hole in it shows the hole rather than black.
        if shape in ("_door", "_trapdoor"):
            model["render_type"] = "minecraft:cutout"
        write(os.path.join(ASSETS, "models/block", name + ".json"), model)
    write(os.path.join(ASSETS, "blockstates", id + ".json"), state)
    if item:
        write(os.path.join(ASSETS, "models/item", id + ".json"), {"parent": item})
    else:
        # Held flat, the way the game holds its own doors.
        write(os.path.join(ASSETS, "models/item", id + ".json"),
              {"parent": "item/generated",
               "textures": {"layer0": f"{NS}:item/{id}"}})
    write(os.path.join(DATA, "loot_table/blocks", id + ".json"), loot)
    return sprites, len(models) + 1, 1


def dig_tags(sited, boned, stood, wood, stone):
    """Which tool mines each block the dig adds.

    The game's own lists decide both how fast a tool breaks a block and, for a
    block that needs the right tool, whether it gives anything up. Until 29
    September 2026 no block here was on any list, so a fossil, a chiselled
    bone block or a mud brick that a player set down and broke again gave
    nothing to any tool.
    """
    pickaxe = [f"{creature}_fossil{part}" for creature, _, _, _, _ in boned
               for part in ("_head", "_body", "")]
    pickaxe += ["chiseled_bone_block", "cracked_mud_bricks", "infested_mud_bricks"]
    pickaxe += [f"cracked_mud_brick{shape}" for shape in stone]
    pickaxe += [name for name, kind, _, _, _ in stood if kind == "PLACED"]
    axe = ["archeology_table", "rotten_log", "rotten_planks"]
    axe += [f"rotten{shape}" for shape in wood]
    shovel = [block for block, _, _ in sited]
    folder = os.path.join(OUT, "src/main/resources/data/minecraft/tags/block/mineable")
    for tool, blocks in (("pickaxe", pickaxe), ("axe", axe), ("shovel", shovel)):
        write(os.path.join(folder, tool + ".json"),
              {"replace": False, "values": [f"{NS}:{block}" for block in blocks]})
    return 3


def dig_files(brushed, sited, boned, found, stood, whole):
    sprites = made = data = 0
    items = os.path.join(ASSETS, "textures/item")
    blocks = os.path.join(ASSETS, "textures/block")
    os.makedirs(items, exist_ok=True)
    os.makedirs(blocks, exist_ok=True)

    # ---- the brushes -------------------------------------------------------
    for name, _, _ in brushed:
        brush_texture(name).save(os.path.join(items, name + ".png"))
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": "item/handheld",
               "textures": {"layer0": f"{NS}:item/{name}"}})
        tier = name.split("_")[0]
        write(os.path.join(DATA, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "equipment",
            "pattern": [" MF", " S ", "S  "],
            "key": {"M": BRUSH_MATERIAL[tier],
                    "F": {"item": "minecraft:feather"},
                    "S": {"item": "minecraft:stick"}},
            "result": {"id": f"{NS}:{name}", "count": 1},
        })
        sprites += 1
        made += 1
        data += 1

    # ---- the sites ---------------------------------------------------------
    for block, below, _ in sited:
        variants = {}
        for stage in range(whole):
            id = f"{block}_{stage}"
            site_texture(block, stage, whole).save(
                os.path.join(blocks, id + ".png"))
            write(os.path.join(ASSETS, "models/block", id + ".json"),
                  {"parent": "block/cube_all",
                   "textures": {"all": f"{NS}:block/{id}"}})
            variants[f"dusted={stage}"] = {"model": f"{NS}:block/{id}"}
            sprites += 1
            made += 1
        write(os.path.join(ASSETS, "blockstates", block + ".json"),
              {"variants": variants})
        write(os.path.join(ASSETS, "models/item", block + ".json"),
              {"parent": f"{NS}:block/{block}_0"})
        made += 1
        # Broken rather than brushed, it gives up the block underneath and
        # nothing else: a player who has found a site and smashes it has
        # destroyed what was in it, which is the point of brushing.
        write(os.path.join(DATA, "loot_table/blocks", block + ".json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "bonus_rolls": 0,
                       "entries": [{"type": "minecraft:item", "name": below}],
                       "conditions": [
                           {"condition": "minecraft:survives_explosion"}]}],
        })
        data += 1

    # ---- the fossils -------------------------------------------------------
    for creature, _, _, _, _ in boned:
        for part in ("head", "body", "whole"):
            id = (f"{creature}_fossil" if part == "whole"
                  else f"{creature}_fossil_{part}")
            one, two, three = cube(blocks, id, fossil_texture(creature, part))
            sprites += one
            made += two
            data += three

    # ---- what a dig turns up that is only decoration -----------------------
    one, two, three = cube(blocks, "chiseled_bone_block",
                           plain_texture("bone", 5, ((150, 146, 128), 14)))
    sprites += one
    made += two
    data += three
    one, two, three = cube(blocks, "archeology_table", plain_texture("table", 17))
    sprites += one
    made += two
    data += three
    write(os.path.join(DATA, "recipe", "archeology_table.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["BBB", "PPP", "P P"],
        "key": {"B": {"item": "minecraft:bone"},
                "P": {"tag": "minecraft:planks"}},
        "result": {"id": f"{NS}:archeology_table", "count": 1},
    })
    data += 1

    for id, kind, seed in (("rotten_log", "rotten", 23),
                           ("rotten_planks", "rotten", 29),
                           ("cracked_mud_bricks", "mud", 37),
                           ("infested_mud_bricks", "mud", 41)):
        one, two, three = cube(blocks, id, plain_texture(kind, seed))
        sprites += one
        made += two
        data += three
    wood, stone = set_shapes()
    for stem, shapes in (("rotten", wood), ("cracked_mud_brick", stone)):
        for shape in shapes:
            one, two, three = shape_files(blocks, items, stem, shape)
            sprites += one
            made += two
            data += three
    data += dig_tags(sited, boned, stood, wood, stone)

    # ---- the totems --------------------------------------------------------
    for name, kind, _, _, _ in stood:
        if kind == "PLACED":
            one, two, three = cube(blocks, name, totem_texture(name, False),
                                   render_type="minecraft:cutout")
            sprites += one
            made += two
            data += three
        else:
            totem_texture(name, True).save(os.path.join(items, name + ".png"))
            write(os.path.join(ASSETS, "models/item", name + ".json"),
                  {"parent": "item/generated",
                   "textures": {"layer0": f"{NS}:item/{name}"}})
            sprites += 1
            made += 1

    # ---- the artefacts -----------------------------------------------------
    shard_texture(1).save(os.path.join(items, "artifact_shard.png"))
    write(os.path.join(ASSETS, "models/item", "artifact_shard.json"),
          {"parent": "item/generated",
           "textures": {"layer0": f"{NS}:item/artifact_shard"}})
    shard_texture(9).save(os.path.join(items, "unidentified_artifact.png"))
    write(os.path.join(ASSETS, "models/item", "unidentified_artifact.json"),
          {"parent": "item/generated",
           "textures": {"layer0": f"{NS}:item/unidentified_artifact"}})
    sprites += 2
    made += 2
    shards = int(re.search(r"SHARDS_PER_ARTIFACT = (\d+);",
                           open(ARTIFACT_RULES, encoding="utf-8").read()).group(1))
    # The grid is read from the rules, so raising the price is one number
    # rather than a number and a recipe that has to be edited to match.
    across = int(shards ** 0.5)
    write(os.path.join(DATA, "recipe", "unidentified_artifact.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["S" * across] * across,
        "key": {"S": {"item": f"{NS}:artifact_shard"}},
        "result": {"id": f"{NS}:unidentified_artifact", "count": 1},
    })
    data += 1
    for name, _, _, _, _ in found:
        id = "identified_" + name
        artifact_texture(name).save(os.path.join(items, id + ".png"))
        write(os.path.join(ASSETS, "models/item", id + ".json"),
              {"parent": "item/generated", "textures": {"layer0": f"{NS}:item/{id}"}})
        sprites += 1
        made += 1
    # There was a disc here, retired on 29 September 2026: a plain item with
    # no song, which no jukebox would play and nothing handed out. The dirt
    # pool gives the game's own relic disc instead.
    return sprites, made, data


# ---------------------------------------------------------------------------
# Where the sites lie
# ---------------------------------------------------------------------------
#
# Until 29 September 2026 nothing put a site in any world, so everything the
# dig hands out came from a command and from nowhere else. Each site now has a
# feature named after it, placed in a biome tag of its own by a biome modifier;
# how thick, how wide and where are DigRules' PATCHES and SEAMS.

#: The step the sites are laid at. The game lays its own disks of sand, clay
#: and gravel and its underground dirt here, after the ground is shaped and
#: before any tree or flower exists, so a patch never lies under a trunk.
DIG_STEP = "underground_ores"


def lies_in(site, biomes):
    """A site's biomes as a tag of its own, and the modifier that lays it there.

    A tag rather than the list itself, because a biome set is one tag or a
    list of ids and never a list with a tag in it (the game refuses the whole
    world: "Not a JSON object"). Named flat, has_dig_site_<site>, rather than
    in a folder: check_datapack.py finds a modifier's tag by a flat name, and
    reports a tag in a folder as missing.
    """
    tag = f"has_dig_site_{site}"
    write(os.path.join(DATA, "tags/worldgen/biome", tag + ".json"),
          {"replace": False, "values": biomes})
    write(os.path.join(DATA, "neoforge/biome_modifier", site + ".json"), {
        "type": "neoforge:add_features",
        "biomes": f"#{NS}:{tag}",
        "features": [f"{NS}:{site}"],
        "step": DIG_STEP})
    return 2


def dig_worldgen(laid, buried):
    written = 0
    for site, one_in, least, most, replaces, biomes in laid:
        # One block deep (half_height 0): the heightmap finds the first open
        # block above the ground and one down is the ground itself, so a patch
        # is the top layer and nothing under it.
        write(os.path.join(DATA, "worldgen/configured_feature", site + ".json"), {
            "type": "minecraft:disk",
            "config": {
                "state_provider": {
                    "fallback": {"type": "minecraft:simple_state_provider",
                                 "state": {"Name": f"{NS}:{site}"}},
                    "rules": []},
                "target": {"type": "minecraft:matching_blocks", "blocks": replaces},
                "radius": {"type": "minecraft:uniform",
                           "min_inclusive": least, "max_inclusive": most},
                "half_height": 0}})
        write(os.path.join(DATA, "worldgen/placed_feature", site + ".json"), {
            "feature": f"{NS}:{site}",
            "placement": [
                {"type": "minecraft:rarity_filter", "chance": one_in},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
                {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": -1},
                {"type": "minecraft:biome"}]})
        written += 2 + lies_in(site, biomes)
    for site, tries, size, lowest, highest, within, biomes in buried:
        # A vein that touches a cave keeps every block it laid, so a seam
        # shows in a cave wall, which is where a player looking for one looks.
        write(os.path.join(DATA, "worldgen/configured_feature", site + ".json"), {
            "type": "minecraft:ore",
            "config": {
                "size": size,
                "discard_chance_on_air_exposure": 0.0,
                "targets": [{"target": {"predicate_type": "minecraft:tag_match",
                                        "tag": within},
                             "state": {"Name": f"{NS}:{site}"}}]}})
        write(os.path.join(DATA, "worldgen/placed_feature", site + ".json"), {
            "feature": f"{NS}:{site}",
            "placement": [
                {"type": "minecraft:count", "count": tries},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:height_range",
                 "height": {"type": "minecraft:uniform",
                            "min_inclusive": {"absolute": lowest},
                            "max_inclusive": {"absolute": highest}}},
                {"type": "minecraft:biome"}]})
        written += 2 + lies_in(site, biomes)
    return written


# ---------------------------------------------------------------------------
# What the finds make
# ---------------------------------------------------------------------------

#: The rotten set: the game's own wood-set grids and counts, on rotten planks
#: ("#") and sticks ("/"). Planks come four to a log, as every log's do.
ROTTEN_GRIDS = {
    "rotten_slab": (["###"], 6, "building"),
    "rotten_stairs": (["#  ", "## ", "###"], 4, "building"),
    "rotten_fence": (["#/#", "#/#"], 3, "misc"),
    "rotten_fence_gate": (["/#/", "/#/"], 1, "redstone"),
    "rotten_door": (["##", "##", "##"], 3, "redstone"),
    "rotten_trapdoor": (["###", "###"], 2, "redstone"),
    "rotten_pressure_plate": (["##"], 1, "redstone"),
}

#: The cracked mud brick set, from cracked mud bricks: the game's own stone
#: grids, and beside each the count a stonecutter gives, as for its bricks.
MUD_GRIDS = {
    "cracked_mud_brick_slab": (["###"], 6, 2),
    "cracked_mud_brick_stairs": (["#  ", "## ", "###"], 4, 1),
}


def dig_recipes(boned):
    """Whole fossils from their halves, and the two sets from what a dig turns up."""
    written = 0
    # A whole fossil is its skull and its body put together. FossilEffects
    # already counts a whole as both halves, so this is the block a pair is for.
    for creature, _, _, _, _ in boned:
        write(os.path.join(DATA, "recipe", f"{creature}_fossil.json"), {
            "type": "minecraft:crafting_shapeless", "category": "building",
            "ingredients": [{"item": f"{NS}:{creature}_fossil_head"},
                            {"item": f"{NS}:{creature}_fossil_body"}],
            "result": {"id": f"{NS}:{creature}_fossil", "count": 1}})
        written += 1
    write(os.path.join(DATA, "recipe", "rotten_planks.json"), {
        "type": "minecraft:crafting_shapeless", "category": "building",
        "ingredients": [{"item": f"{NS}:rotten_log"}],
        "result": {"id": f"{NS}:rotten_planks", "count": 4}})
    written += 1
    for id, (pattern, count, category) in ROTTEN_GRIDS.items():
        key = {"#": {"item": f"{NS}:rotten_planks"}}
        if any("/" in row for row in pattern):
            key["/"] = {"item": "minecraft:stick"}
        write(os.path.join(DATA, "recipe", id + ".json"), {
            "type": "minecraft:crafting_shaped", "category": category,
            "pattern": pattern, "key": key,
            "result": {"id": f"{NS}:{id}", "count": count}})
        written += 1
    # Smelted from the game's own mud bricks, the way cracked stone bricks are
    # made: the badlands give the first few, and a furnace keeps the set going.
    write(os.path.join(DATA, "recipe", "cracked_mud_bricks.json"), {
        "type": "minecraft:smelting", "category": "blocks",
        "ingredient": {"item": "minecraft:mud_bricks"},
        "result": {"id": f"{NS}:cracked_mud_bricks"},
        "experience": 0.1, "cookingtime": 200})
    written += 1
    for id, (pattern, count, cut) in MUD_GRIDS.items():
        write(os.path.join(DATA, "recipe", id + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "building",
            "pattern": pattern, "key": {"#": {"item": f"{NS}:cracked_mud_bricks"}},
            "result": {"id": f"{NS}:{id}", "count": count}})
        write(os.path.join(DATA, "recipe", id + "_stonecutting.json"), {
            "type": "minecraft:stonecutting",
            "ingredient": {"item": f"{NS}:cracked_mud_bricks"},
            "result": {"id": f"{NS}:{id}", "count": cut}})
        written += 2
    return written


#: What each brush is faced with. Its tier's own metal, the way every other
#: ladder in this module is built.
BRUSH_MATERIAL = {
    "iron": {"item": "minecraft:iron_ingot"},
    "diamond": {"item": "minecraft:diamond"},
    "netherite": {"item": "minecraft:netherite_ingot"},
}

BRUSH_NAMES = {"iron_brush": "Iron Brush", "diamond_brush": "Diamond Brush",
               "netherite_brush": "Netherite Brush"}

SITE_NAMES = {"suspicious_dirt": "Suspicious Dirt",
              "suspicious_red_sand": "Suspicious Red Sand",
              "fossiliferous_dirt": "Fossiliferous Dirt"}

CREATURE_NAMES = {"ocelot": "Ocelot", "wolf": "Wolf", "chicken": "Chicken",
                  "guardian": "Guardian", "creeper": "Creeper", "sheep": "Sheep",
                  "villager": "Villager"}

EFFECT_WORDS = {
    "creepers_keep_away": "Creepers keep their distance",
    "skeletons_keep_away": "Skeletons keep their distance",
    "lighter_landings": "You land more lightly",
    "breathe_under_water": "You breathe under water",
    "blasts_hurt_less": "Blasts hurt less",
    "wool_grows_back": "Wool grows back sooner",
    "traders_restock": "Traders restock sooner",
}

TOTEM_WORDS = {
    "growth_totem": "Set down: what grows near it grows sooner",
    "radiance_totem": "Set down: what lurks near it is seen, and burned",
    "torrent_totem": "Spent: thrown the way you are looking",
    "soul_totem": "Spent: takes from what is near and gives it to you",
}

ARTIFACT_WORDS = {
    "penetrating_strike": "Penetrating Strike",
    "soaring_winds": "Soaring Winds",
    "tunneling": "Tunnelling",
}

DECOR_NAMES = {
    "chiseled_bone_block": "Chiselled Bone Block",
    "archeology_table": "Archeology Table",
    "rotten_log": "Rotten Log", "rotten_planks": "Rotten Planks",
    "rotten_slab": "Rotten Slab", "rotten_stairs": "Rotten Stairs",
    "rotten_fence": "Rotten Fence", "rotten_fence_gate": "Rotten Fence Gate",
    "rotten_door": "Rotten Door", "rotten_trapdoor": "Rotten Trapdoor",
    "rotten_pressure_plate": "Rotten Pressure Plate",
    "cracked_mud_bricks": "Cracked Mud Bricks",
    "cracked_mud_brick_slab": "Cracked Mud Brick Slab",
    "cracked_mud_brick_stairs": "Cracked Mud Brick Stairs",
    "infested_mud_bricks": "Infested Mud Bricks",
}


def dig_language(entries, brushed, sited, boned, found, stood):
    for name, _, _ in brushed:
        entries[f"item.{NS}.{name}"] = BRUSH_NAMES[name]
    for block, _, _ in sited:
        entries[f"block.{NS}.{block}"] = SITE_NAMES[block]
    for creature, effect, _, _, _ in boned:
        who = CREATURE_NAMES[creature]
        entries[f"block.{NS}.{creature}_fossil"] = f"{who} Fossil"
        entries[f"block.{NS}.{creature}_fossil_head"] = f"{who} Fossil Skull"
        entries[f"block.{NS}.{creature}_fossil_body"] = f"{who} Fossil Body"
        entries[f"{NS}.fossil.{effect}"] = EFFECT_WORDS[effect]
    for name, kind, _, _, _ in stood:
        key = "block" if kind == "PLACED" else "item"
        entries[f"{key}.{NS}.{name}"] = " ".join(
            word.capitalize() for word in name.split("_"))
        entries[f"{NS}.totem.{name}"] = TOTEM_WORDS[name]
    entries[f"item.{NS}.artifact_shard"] = "Artefact Shard"
    entries[f"item.{NS}.unidentified_artifact"] = "Unidentified Artefact"
    for name, _, _, _, _ in found:
        entries[f"item.{NS}.identified_{name}"] = ARTIFACT_WORDS[name]
        entries[f"{NS}.artifact.{name}"] = ARTIFACT_WORDS[name]
    for id, words in DECOR_NAMES.items():
        entries[f"block.{NS}.{id}"] = words
    entries[f"{NS}.artifact.unknown"] = "One of %1$s things"
    entries[f"{NS}.artifact.set"] = "%1$s %2$s"
    entries[f"{NS}.artifact.fits"] = "Goes into %1$s, up to %2$s deep"
    entries[f"{NS}.artifact.wrong_gear"] = "This will not go into that"
    entries[f"{NS}.artifact.already"] = "Something else is already set into that"
    entries[f"{NS}.artifact.full"] = "That is already %1$s deep"
    entries[f"{NS}.gear.elytra"] = "elytra"
    return entries


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
    # Weapons and bows come in every rung, the Cordon's included; the broad
    # tools stay on the game's six, because each is built out of the vanilla
    # tool it replaces.
    sprites += draw_weapons(kinds, WEAPON_TIERS)
    made += len(kinds) * len(WEAPON_TIERS)
    data += weapon_data(kinds, WEAPON_TIERS)

    # The bows, their ammunition and the twenty-four that have names.
    kinds_ranged = bows()
    ammunition = shots()
    named = uniques()
    unknown = [k for k in kinds_ranged if k not in BOW_GRIDS or k not in BOW_NAMES] \
        + [n for n, _, _ in ammunition if n not in SHOT_TIPS or n not in SHOT_NAMES] \
        + [i for i, *_ in named if i not in UNIQUE_NAMES]
    if unknown:
        print(f"the ranged rules and this file disagree: {unknown} have no grid, "
              f"sprite or name here")
        sys.exit(1)
    # Two bows on one grid is one recipe and one bow nobody can make, which is
    # the bug this same check already caught among the melee weapons.
    laid = {}
    for kind, grid in BOW_GRIDS.items():
        key = min("|".join(grid), "|".join(row[::-1] for row in grid))
        if key in laid:
            print(f"{kind} and {laid[key]} are laid out the same way, so they are one "
                  f"recipe and one of them cannot be made at all")
            sys.exit(1)
        laid[key] = kind
    # And two shots made of the same thing in the same pattern, likewise.
    tipped = {}
    for name, _, bolt in ammunition:
        key = (bolt, json.dumps(SHOT_TIPS[name], sort_keys=True))
        if key in tipped:
            print(f"{name} and {tipped[key]} are made of the same thing in the same "
                  f"grid, so they are one recipe")
            sys.exit(1)
        tipped[key] = name
    # A unique is told apart by the weapon that goes into its smithing, so two
    # on one shape-and-tier would be one recipe and one unique unobtainable.
    seated = {}
    for id, shape, tier, rarity, _ in named:
        if rarity not in UNIQUE_CATALYSTS:
            print(f"{id} is rarity {rarity} and nothing says what that costs")
            sys.exit(1)
        if (shape, tier) in seated:
            print(f"{id} and {seated[(shape, tier)]} are both smithed from a "
                  f"{tier} {shape}, so one of them cannot be made")
            sys.exit(1)
        seated[(shape, tier)] = id
    more_sprites, more_models, more_data = ranged_files(kinds_ranged, WEAPON_TIERS,
                                                        ammunition)
    sprites += more_sprites
    made += more_models
    data += more_data
    more_sprites, more_models, more_data = unique_files(named)
    sprites += more_sprites
    made += more_models
    data += more_data

    # The hammers and the grades they roll.
    smiths = hammers()
    graded = grades()
    kinds_of_gear = gear_kinds()
    unknown = [h for h, _ in smiths
               if h not in HAMMER_FACE or h not in HAMMER_NAMES] \
        + [g for g, _ in graded if g not in GRADE_NAMES] \
        + [k for k in kinds_of_gear if k not in GEAR_NAMES]
    if unknown:
        print(f"the forge rules and this file disagree: {unknown} have no sprite, "
              f"face or name here")
        sys.exit(1)
    # Two hammers faced with the same metal are one recipe, and the loser is a
    # kind of gear nothing can reforge.
    faced = {}
    for name, _ in smiths:
        key = json.dumps(HAMMER_FACE[name][1], sort_keys=True)
        if key in faced:
            print(f"{name} and {faced[key]} are faced with the same thing, so they "
                  f"are one recipe and one of them cannot be made")
            sys.exit(1)
        faced[key] = name
    more_sprites, more_models, more_data = forge_files(smiths)
    sprites += more_sprites
    made += more_models
    data += more_data

    entries = language(broads, tiers, kinds, WEAPON_TIERS)
    entries = ranged_language(entries, kinds_ranged, WEAPON_TIERS, ammunition, named)
    entries = forge_language(entries, smiths, graded, kinds_of_gear)

    # The digging.
    brushed = brushes()
    sited = sites()
    boned = fossils()
    found = artifacts()
    stood = totems()
    whole = stages()
    unknown = [b for b, _, _ in brushed if b not in BRUSH_NAMES] \
        + [b for b, _, _ in sited if b not in SITE_NAMES] \
        + [c for c, _, _, _, _ in boned if c not in CREATURE_NAMES] \
        + [e for _, e, _, _, _ in boned if e not in EFFECT_WORDS] \
        + [a for a, _, _, _, _ in found if a not in ARTIFACT_WORDS] \
        + [t for t, _, _, _, _ in stood if t not in TOTEM_WORDS]
    if unknown:
        print(f"the digging rules and this file disagree: {unknown} have no sprite "
              f"or name here")
        sys.exit(1)
    if not brushed or not sited or not boned or not found or not stood:
        print("one of the digging rosters is empty, so this generator would write "
              "nothing for it and nothing would say so")
        sys.exit(1)
    more_sprites, more_models, more_data = dig_files(brushed, sited, boned, found,
                                                     stood, whole)
    sprites += more_sprites
    made += more_models
    data += more_data
    laid = patches()
    buried = seams()
    strangers = [row[0] for row in laid + buried
                 if row[0] not in {block for block, _, _ in sited}]
    if strangers:
        print(f"the digging rules lay {strangers} in the world, and they are not "
              f"sites")
        sys.exit(1)
    data += dig_worldgen(laid, buried)
    data += dig_recipes(boned)
    entries = dig_language(entries, brushed, sited, boned, found, stood)
    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(entries.items())))
    keys = len(entries)
    print(f"arsenal: {sprites} sprite(s), {made} model(s), {data} data file(s), "
          f"{keys} language key(s)")
