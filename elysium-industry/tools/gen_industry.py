#!/usr/bin/env python3
"""
Every file Age II needs that is not Java.

    python3 tools/gen_industry.py

Art, models, blockstates, loot, crafting recipes, machine recipes and
language, written from one place for the reason Age I's generator exists:
twelve blocks and five items is more than sixty JSON files that have to agree
with each other about names, and the last one is always the one that is wrong.

The sprites are drawn here from shapes. Nobody else's pixels are in this
repository, and a coil of wire is a rectangle with bands on it.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumindustry"
KIN = "elysiumkinetics"

#: Where the files land. The module itself, unless something asks for them
#: somewhere else — which is how validate.py regenerates the whole set into a
#: temporary tree and compares, so that a file edited by hand is caught on the
#: next build instead of on the day the generator is next run and silently
#: undoes the edit.
OUT = os.environ.get("ELYSIUM_INDUSTRY_OUT", MOD)
ASSETS = os.path.join(OUT, "src/main/resources/assets", NS)
DATA = os.path.join(OUT, "src/main/resources/data", NS)

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


# ---------------------------------------------------------------------------
# The palette
# ---------------------------------------------------------------------------
#
# Age I was timber, brass and stone. Age II is painted steel and bare metal,
# with one hot colour that only ever means "this is running". Five ramps, four
# steps each, because a texture set with thirty colours in it looks like six
# mods rather than one age.

STEEL = [(38, 41, 46), (72, 78, 86), (112, 120, 130), (156, 164, 174)]
COPPER = [(84, 46, 28), (140, 78, 44), (186, 110, 62), (216, 148, 96)]
GOLD = [(96, 74, 18), (156, 122, 32), (212, 170, 54), (244, 212, 110)]
PAINT = [(26, 46, 52), (38, 72, 80), (52, 98, 108), (72, 130, 142)]
HOT = [(90, 22, 10), (168, 58, 14), (226, 118, 24), (252, 194, 76)]
CLEAR = (0, 0, 0, 0)


def rgba(colour, alpha=255):
    return (colour[0], colour[1], colour[2], alpha)


def panel(ramp, rivets=True):
    """A painted plate: flat colour, a lit top edge, a dark bottom, rivets."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], fill=rgba(ramp[1]))
    draw.line([(0, 0), (15, 0)], fill=rgba(ramp[2]))
    draw.line([(0, 0), (0, 15)], fill=rgba(ramp[2]))
    draw.line([(0, 15), (15, 15)], fill=rgba(ramp[0]))
    draw.line([(15, 0), (15, 15)], fill=rgba(ramp[0]))
    if rivets:
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            draw.point((x, y), fill=rgba(ramp[3]))
    return image


def cable(ramp):
    """A run of wire seen from the side: a core with a twist banding it."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([6, 0, 9, 15], fill=rgba(ramp[1]))
    draw.line([(6, 0), (6, 15)], fill=rgba(ramp[0]))
    draw.line([(9, 0), (9, 15)], fill=rgba(ramp[2]))
    for y in range(1, 16, 3):
        draw.line([(6, y), (9, y)], fill=rgba(ramp[3]))
    return image


def cable_end(ramp):
    """The cut end of a run of wire: strands in a sheath."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([6, 6, 9, 9], fill=rgba(ramp[0]))
    draw.point((7, 7), fill=rgba(ramp[3]))
    draw.point((8, 8), fill=rgba(ramp[2]))
    draw.point((7, 8), fill=rgba(ramp[2]))
    draw.point((8, 7), fill=rgba(ramp[3]))
    return image


def transformer_side():
    """Fins, because that is what every transformer anyone has seen looks like."""
    image = panel(STEEL)
    draw = ImageDraw.Draw(image)
    for x in range(2, 15, 3):
        draw.line([(x, 3), (x, 12)], fill=rgba(STEEL[0]))
        draw.line([(x + 1, 3), (x + 1, 12)], fill=rgba(STEEL[3]))
    draw.rectangle([1, 1, 14, 2], fill=rgba(COPPER[1]))
    draw.rectangle([1, 13, 14, 14], fill=rgba(COPPER[1]))
    return image


def transformer_top():
    image = panel(STEEL)
    draw = ImageDraw.Draw(image)
    draw.ellipse([4, 4, 11, 11], fill=rgba(COPPER[2]), outline=rgba(COPPER[0]))
    draw.ellipse([6, 6, 9, 9], fill=rgba(STEEL[0]))
    return image


def boiler(lit):
    """An engine seen from the front: a hatch that is open on the fire or is not."""
    image = panel(PAINT)
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 5, 12, 13], fill=rgba(STEEL[0]), outline=rgba(STEEL[2]))
    if lit:
        draw.rectangle([4, 7, 11, 12], fill=rgba(HOT[1]))
        draw.rectangle([5, 9, 10, 12], fill=rgba(HOT[2]))
        draw.line([(6, 11), (9, 11)], fill=rgba(HOT[3]))
    else:
        draw.rectangle([4, 7, 11, 12], fill=rgba(STEEL[1]))
    draw.rectangle([2, 1, 13, 3], fill=rgba(STEEL[1]), outline=rgba(STEEL[0]))
    return image


def engine_side():
    image = panel(PAINT)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 4, 13, 11], fill=rgba(STEEL[1]), outline=rgba(STEEL[0]))
    for y in (5, 7, 9):
        draw.line([(3, y), (12, y)], fill=rgba(STEEL[2]))
    return image


def engine_top():
    image = panel(PAINT)
    draw = ImageDraw.Draw(image)
    draw.rectangle([5, 1, 10, 6], fill=rgba(STEEL[0]), outline=rgba(STEEL[2]))
    draw.ellipse([6, 9, 11, 14], fill=rgba(COPPER[1]), outline=rgba(COPPER[0]))
    return image


def spindle_side(ramp):
    """A machine with a shaft through it: a drum with a band round the middle."""
    image = panel(STEEL, rivets=False)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 4, 15, 11], fill=rgba(ramp[1]), outline=rgba(ramp[0]))
    for x in range(1, 15, 2):
        draw.line([(x, 5), (x, 10)], fill=rgba(ramp[2]))
    draw.line([(0, 3), (15, 3)], fill=rgba(STEEL[3]))
    draw.line([(0, 12), (15, 12)], fill=rgba(STEEL[0]))
    return image


def spindle_end(ramp):
    """The end a shaft goes into."""
    image = panel(STEEL, rivets=False)
    draw = ImageDraw.Draw(image)
    draw.ellipse([2, 2, 13, 13], fill=rgba(ramp[1]), outline=rgba(ramp[0]))
    draw.ellipse([5, 5, 10, 10], fill=rgba(STEEL[0]))
    draw.rectangle([6, 6, 9, 9], fill=rgba(STEEL[2]))
    return image


def machine_side(accent):
    image = panel(PAINT)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 9, 13, 13], fill=rgba(STEEL[1]), outline=rgba(STEEL[0]))
    draw.rectangle([3, 3, 12, 7], fill=rgba(accent[1]), outline=rgba(accent[0]))
    draw.line([(4, 5), (11, 5)], fill=rgba(accent[3]))
    return image


def crusher_top():
    image = panel(STEEL)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 2, 13, 13], fill=rgba(STEEL[0]))
    for x in range(3, 13, 3):
        draw.polygon([(x, 4), (x + 2, 4), (x + 1, 11)], fill=rgba(STEEL[2]))
    return image


def mill_top():
    image = panel(STEEL)
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 4, 14, 6], fill=rgba(STEEL[3]), outline=rgba(STEEL[0]))
    draw.rectangle([1, 9, 14, 11], fill=rgba(STEEL[3]), outline=rgba(STEEL[0]))
    return image


def arc_top():
    image = panel(STEEL)
    draw = ImageDraw.Draw(image)
    draw.ellipse([3, 3, 12, 12], fill=rgba(STEEL[0]), outline=rgba(STEEL[2]))
    draw.line([(6, 5), (8, 10)], fill=rgba(HOT[3]))
    draw.line([(9, 5), (7, 10)], fill=rgba(HOT[2]))
    return image


def separator_top():
    image = panel(STEEL)
    draw = ImageDraw.Draw(image)
    draw.ellipse([2, 2, 13, 13], fill=rgba(STEEL[1]), outline=rgba(STEEL[0]))
    draw.ellipse([5, 5, 10, 10], fill=rgba(GOLD[2]), outline=rgba(GOLD[0]))
    draw.point((7, 4), fill=rgba(STEEL[3]))
    draw.point((11, 8), fill=rgba(STEEL[3]))
    return image


def ingot(ramp):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.polygon([(3, 10), (5, 6), (11, 6), (13, 10), (13, 11), (3, 11)],
                 fill=rgba(ramp[1]), outline=rgba(ramp[0]))
    draw.line([(5, 7), (10, 7)], fill=rgba(ramp[3]))
    draw.line([(4, 10), (12, 10)], fill=rgba(ramp[0]))
    return image


def plate(ramp):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 5, 13, 11], fill=rgba(ramp[1]), outline=rgba(ramp[0]))
    draw.line([(3, 6), (12, 6)], fill=rgba(ramp[3]))
    draw.line([(3, 10), (12, 10)], fill=rgba(ramp[0]))
    for x in (4, 11):
        draw.point((x, 8), fill=rgba(ramp[2]))
    return image


# ---------------------------------------------------------------------------
# The bricks, read out of the shapes that are built from them
# ---------------------------------------------------------------------------

FURNACE_SOURCE = open(os.path.join(
    MOD, "src/main/java/com/elysium/industry/rules/FurnaceRules.java"),
    encoding="utf-8").read()
_flat_furnace = re.sub(r"\s+", " ", FURNACE_SOURCE)
BRICKS = re.findall(r'String (?:COKE_BRICK|BLAST_BRICK) = "([a-z_]+)"',
                    FURNACE_SOURCE)
SHAPE_IDS = re.findall(r'new ShapeRules\.Shape\(\s*"([a-z_]+)"', FURNACE_SOURCE)
COKE = re.search(r'String COKE = "([a-z_]+)"', FURNACE_SOURCE)
if len(BRICKS) != 2 or len(SHAPE_IDS) != 2 or not COKE:
    print(f"FurnaceRules no longer has the shape this reads: {len(BRICKS)} "
          f"brick(s), {len(SHAPE_IDS)} shape(s)")
    sys.exit(2)
COKE = COKE.group(1)
COKING = int(re.search(r"COKING_TICKS = ([\d_]+);", FURNACE_SOURCE)
             .group(1).replace("_", ""))
BLASTING = int(re.search(r"BLASTING_TICKS = ([\d_]+);", FURNACE_SOURCE)
               .group(1).replace("_", ""))
COKE_BURN = int(re.search(r"COKE_BURN = ([\d_]+);", FURNACE_SOURCE)
                .group(1).replace("_", ""))


def brick(base, mortar):
    """A course of brick, drawn rather than tinted from vanilla's."""
    image = Image.new("RGBA", (16, 16), mortar + (255,))
    draw = ImageDraw.Draw(image)
    for row in range(4):
        offset = 0 if row % 2 == 0 else 4
        for column in range(-1, 3):
            x0 = column * 8 + offset
            draw.rectangle([x0, row * 4, x0 + 6, row * 4 + 2],
                           fill=base + (255,))
    return image


def furnace_face(accent, lit):
    """A brick face with a mouth in it, so the controller is findable."""
    image = brick((132, 74, 58), (96, 86, 78))
    draw = ImageDraw.Draw(image)
    mouth = accent if lit else tuple(max(0, part - 70) for part in accent)
    draw.rectangle([4, 6, 11, 13], fill=(40, 34, 30, 255),
                   outline=(70, 62, 56, 255))
    draw.rectangle([6, 9, 9, 12], fill=mouth + (255,))
    return image


def coke_sprite():
    """A lump: coal that has been through an oven, so paler and drier."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.polygon([(4, 11), (3, 6), (7, 3), (12, 5), (13, 10), (9, 13)],
                 fill=(64, 60, 58, 255), outline=(38, 36, 34, 255))
    draw.polygon([(6, 8), (8, 5), (10, 7), (8, 10)], fill=(104, 98, 94, 255))
    return image


BLOCK_SPRITES = {
    "copper_wire": lambda: cable(COPPER),
    "copper_wire_end": lambda: cable_end(COPPER),
    "gold_wire": lambda: cable(GOLD),
    "gold_wire_end": lambda: cable_end(GOLD),
    "steel_wire": lambda: cable(STEEL),
    "steel_wire_end": lambda: cable_end(STEEL),
    "transformer": transformer_side,
    "transformer_top": transformer_top,
    "steam_turbine": lambda: boiler(False),
    "steam_turbine_on": lambda: boiler(True),
    "engine_side": engine_side,
    "engine_top": engine_top,
    "diesel_engine": lambda: boiler(False),
    "diesel_engine_on": lambda: boiler(True),
    "alternator": lambda: spindle_side(COPPER),
    "alternator_end": lambda: spindle_end(COPPER),
    "motor": lambda: spindle_side(GOLD),
    "motor_end": lambda: spindle_end(GOLD),
    "machine_side": lambda: machine_side(STEEL),
    "crusher_top": crusher_top,
    "rolling_mill_top": mill_top,
    "arc_furnace_top": arc_top,
    "separator_top": separator_top,
    BRICKS[0]: lambda: brick((84, 74, 66), (58, 52, 48)),
    BRICKS[1]: lambda: brick((150, 96, 70), (96, 80, 70)),
    SHAPE_IDS[0]: lambda: furnace_face((232, 150, 60), False),
    SHAPE_IDS[0] + "_on": lambda: furnace_face((232, 150, 60), True),
    SHAPE_IDS[1]: lambda: furnace_face((120, 190, 232), False),
    SHAPE_IDS[1] + "_on": lambda: furnace_face((120, 190, 232), True),
}

ITEM_SPRITES = {
    "steel_ingot": lambda: ingot(STEEL),
    "iron_plate": lambda: plate([(70, 70, 74), (140, 140, 146), (180, 180, 186),
                                 (214, 214, 220)]),
    "copper_plate": lambda: plate(COPPER),
    "gold_plate": lambda: plate(GOLD),
    "steel_plate": lambda: plate(STEEL),
    COKE: coke_sprite,
}


def draw_all():
    blocks = os.path.join(ASSETS, "textures/block")
    items = os.path.join(ASSETS, "textures/item")
    os.makedirs(blocks, exist_ok=True)
    os.makedirs(items, exist_ok=True)
    for name, make in BLOCK_SPRITES.items():
        make().save(os.path.join(blocks, name + ".png"))
    for name, make in ITEM_SPRITES.items():
        make().save(os.path.join(items, name + ".png"))
    return len(BLOCK_SPRITES) + len(ITEM_SPRITES)


# ---------------------------------------------------------------------------
# Models and blockstates
# ---------------------------------------------------------------------------

AXIS_ROTATION = {"y": {}, "x": {"x": 90, "y": 90}, "z": {"x": 90}}


def axis_blockstate(model):
    variants = {}
    for axis, rotation in AXIS_ROTATION.items():
        entry = {"model": f"{NS}:block/{model}"}
        entry.update(rotation)
        variants[f"axis={axis}"] = entry
    return {"variants": variants}


def cube(top, side, bottom=None):
    return {
        "parent": "block/block",
        "textures": {"top": f"{NS}:block/{top}", "side": f"{NS}:block/{side}",
                     "bottom": f"{NS}:block/{bottom or top}",
                     "particle": f"{NS}:block/{side}"},
        "elements": [{
            "from": [0, 0, 0],
            "to": [16, 16, 16],
            "faces": {
                "down": {"texture": "#bottom", "cullface": "down"},
                "up": {"texture": "#top", "cullface": "up"},
                "north": {"texture": "#side", "cullface": "north"},
                "south": {"texture": "#side", "cullface": "south"},
                "west": {"texture": "#side", "cullface": "west"},
                "east": {"texture": "#side", "cullface": "east"},
            },
        }],
    }


def pillar(width, side, end):
    low = (16 - width) / 2
    high = 16 - low
    return {
        "parent": "block/block",
        "textures": {"side": f"{NS}:block/{side}", "end": f"{NS}:block/{end}",
                     "particle": f"{NS}:block/{side}"},
        "elements": [{
            "from": [low, 0, low],
            "to": [high, 16, high],
            "faces": {
                "down": {"texture": "#end", "cullface": "down"},
                "up": {"texture": "#end", "cullface": "up"},
                "north": {"texture": "#side"},
                "south": {"texture": "#side"},
                "west": {"texture": "#side"},
                "east": {"texture": "#side"},
            },
        }],
    }


#: Blocks whose model is a full cube, as (top, side) sprites.
CUBES = {
    BRICKS[0]: (BRICKS[0], BRICKS[0]),
    BRICKS[1]: (BRICKS[1], BRICKS[1]),
    "transformer": ("transformer_top", "transformer"),
    "crusher": ("crusher_top", "machine_side"),
    "rolling_mill": ("rolling_mill_top", "machine_side"),
    "arc_furnace": ("arc_furnace_top", "machine_side"),
    "separator": ("separator_top", "machine_side"),
}

#: Blocks with a shaft through them, as (side, end) sprites.
SPINDLES = {
    "alternator": ("alternator", "alternator_end"),
    "motor": ("motor", "motor_end"),
}

#: Blocks with a fire in them, as (front sprite stem, side, top).
ENGINES = {
    "steam_turbine": ("steam_turbine", "engine_side", "engine_top"),
    "diesel_engine": ("diesel_engine", "engine_side", "engine_top"),
}

WIRES = ("copper_wire", "gold_wire", "steel_wire")


def engine_model(front, side, top):
    return {
        "parent": "block/block",
        "textures": {"front": f"{NS}:block/{front}", "side": f"{NS}:block/{side}",
                     "top": f"{NS}:block/{top}", "particle": f"{NS}:block/{front}"},
        "elements": [{
            "from": [0, 0, 0],
            "to": [16, 16, 16],
            "faces": {
                "down": {"texture": "#top", "cullface": "down"},
                "up": {"texture": "#top", "cullface": "up"},
                "north": {"texture": "#front", "cullface": "north"},
                "south": {"texture": "#side", "cullface": "south"},
                "west": {"texture": "#side", "cullface": "west"},
                "east": {"texture": "#side", "cullface": "east"},
            },
        }],
    }


def models_and_states():
    written = 0
    # The wires are runs: core, arms, plates and dye recipes come from
    # art/runs.py, the same as every pipe, cable, conduit and bus in the pack.
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(HERE)), "art"))
    import runs as art_runs
    for name in WIRES:
        art_runs.write_all(NS, name, f"{NS}:block/{name}", ASSETS, DATA, write)
        written += 1
    for name, (top, side) in CUBES.items():
        write(os.path.join(ASSETS, "models/block", name + ".json"), cube(top, side))
        write(os.path.join(ASSETS, "blockstates", name + ".json"),
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})
        written += 1
    for name, (side, end) in SPINDLES.items():
        write(os.path.join(ASSETS, "models/block", name + ".json"), pillar(16, side, end))
        write(os.path.join(ASSETS, "blockstates", name + ".json"), axis_blockstate(name))
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})
        written += 1
    for name, (front, side, top) in ENGINES.items():
        write(os.path.join(ASSETS, "models/block", name + ".json"),
              engine_model(front, side, top))
        write(os.path.join(ASSETS, "models/block", name + "_on.json"),
              engine_model(front + "_on", side, top))
        # An engine that looked the same lit and cold would be an engine whose
        # one piece of feedback is a message you have to ask for.
        write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {
            "lit=false": {"model": f"{NS}:block/{name}"},
            "lit=true": {"model": f"{NS}:block/{name}_on"},
        }})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})
        written += 1
    # The two controllers, which are drawn lit when the bricks around them
    # are the machine. A multiblock that looks the same formed and unformed
    # is a multiblock whose one piece of feedback is a message you have to
    # ask for.
    for name in SHAPE_IDS:
        write(os.path.join(ASSETS, "models/block", name + ".json"),
              cube(BRICKS[SHAPE_IDS.index(name)], name))
        write(os.path.join(ASSETS, "models/block", name + "_on.json"),
              cube(BRICKS[SHAPE_IDS.index(name)], name + "_on"))
        write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {
            "formed=false": {"model": f"{NS}:block/{name}"},
            "formed=true": {"model": f"{NS}:block/{name}_on"},
        }})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})
        written += 1
    for name in ITEM_SPRITES:
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": "item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        written += 1
    return written


# ---------------------------------------------------------------------------
# Loot, tags and crafting
# ---------------------------------------------------------------------------

BLOCKS = list(WIRES) + list(CUBES) + list(SPINDLES) + list(ENGINES) + SHAPE_IDS


def drop_itself(name):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }


def shaped(pattern, key, result, count=1):
    return {"type": "minecraft:crafting_shaped", "category": "building",
            "pattern": pattern, "key": key,
            "result": {"id": f"{NS}:{result}", "count": count}}


ITEM = lambda name: {"item": name}
TAG = lambda name: {"tag": name}

STEEL_TAG = TAG("c:ingots/steel")

#: The bootstrap pair — a turbine and an alternator — are craftable out of
#: ingots. Everything above them wants plate, and plate wants a rolling mill,
#: so a factory is a thing you build with a factory; but the first grid is not,
#: because a first grid you cannot build is a wall rather than a ladder.
#: The bricks and the two machines built out of them.
#:
#: A coke brick is a brick and a lump of clay fired harder; a blast brick is
#: the same with iron in it. Both are cheap on purpose — a multiblock's cost
#: is time and space rather than materials, and thirty bricks that each want
#: an ingot would make these two blocks a mid-game project rather than the
#: first thing Age II builds.
BRICK_RECIPES = {
    BRICKS[0]: shaped(["BCB", "CBC", "BCB"], {
        "B": ITEM("minecraft:brick"), "C": ITEM("minecraft:clay_ball")},
        BRICKS[0], 4),
    BRICKS[1]: shaped(["BIB", "IBI", "BIB"], {
        "B": ITEM("minecraft:brick"), "I": ITEM("minecraft:iron_nugget")},
        BRICKS[1], 4),
    SHAPE_IDS[0]: shaped(["BBB", "BFB", "BBB"], {
        "B": ITEM(f"{NS}:{BRICKS[0]}"), "F": ITEM("minecraft:furnace")},
        SHAPE_IDS[0]),
    SHAPE_IDS[1]: shaped(["BBB", "BFB", "BBB"], {
        "B": ITEM(f"{NS}:{BRICKS[1]}"), "F": ITEM("minecraft:blast_furnace")},
        SHAPE_IDS[1]),
}

RECIPES = {
    "copper_wire": shaped(["CCC"], {"C": ITEM("minecraft:copper_ingot")}, "copper_wire", 8),
    "gold_wire": shaped(["GGG"], {"G": ITEM("minecraft:gold_ingot")}, "gold_wire", 8),
    "steel_wire": shaped(["SSS"], {"S": STEEL_TAG}, "steel_wire", 8),
    "transformer": shaped(["GPG", "GCG", "III"], {
        "G": ITEM("minecraft:gold_ingot"), "P": TAG("c:plates/copper"),
        "C": ITEM("minecraft:copper_block"), "I": ITEM("minecraft:iron_ingot")},
        "transformer"),
    "steam_turbine": shaped(["III", "CFC", "BBB"], {
        "I": ITEM("minecraft:iron_ingot"), "C": ITEM("minecraft:copper_ingot"),
        "F": ITEM("minecraft:blast_furnace"), "B": ITEM("minecraft:bricks")},
        "steam_turbine"),
    "diesel_engine": shaped(["SSS", "GFG", "PPP"], {
        "S": STEEL_TAG, "G": ITEM("minecraft:gold_ingot"),
        "F": ITEM("minecraft:blast_furnace"), "P": TAG("c:plates/steel")},
        "diesel_engine"),
    "alternator": shaped(["CIC", "CKC", "CIC"], {
        "C": ITEM("minecraft:copper_ingot"), "I": ITEM("minecraft:iron_ingot"),
        "K": ITEM(f"{KIN}:brass_shaft")}, "alternator"),
    "motor": shaped(["GIG", "GKG", "GIG"], {
        "G": ITEM("minecraft:gold_ingot"), "I": ITEM("minecraft:iron_ingot"),
        "K": ITEM(f"{KIN}:brass_shaft")}, "motor"),
    "crusher": shaped(["III", "IMI", "CCC"], {
        "I": ITEM("minecraft:iron_ingot"), "M": ITEM(f"{KIN}:millstone"),
        "C": ITEM("minecraft:cobblestone")}, "crusher"),
    "rolling_mill": shaped(["III", "IPI", "CCC"], {
        "I": ITEM("minecraft:iron_ingot"), "P": ITEM(f"{KIN}:press"),
        "C": ITEM("minecraft:cobblestone")}, "rolling_mill"),
    "arc_furnace": shaped(["III", "CFC", "III"], {
        "I": ITEM("minecraft:iron_block"), "C": ITEM("minecraft:copper_block"),
        "F": ITEM("minecraft:blast_furnace")}, "arc_furnace"),
    "separator": shaped(["PPP", "IRI", "PPP"], {
        "P": TAG("c:plates/steel"), "I": ITEM("minecraft:iron_block"),
        "R": ITEM("minecraft:redstone_block")}, "separator"),
}


# ---------------------------------------------------------------------------
# What the machines do
# ---------------------------------------------------------------------------
#
# Ticks are at a full share; a starved grid is proportionally slower. Kept
# short on purpose, as Age I's are: these are the jobs vanilla alone can
# justify, and the real catalogue arrives with the mods being converted into
# the ages above.

CRUSHING = {
    "gravel_from_cobblestone": ("minecraft:cobblestone", "minecraft:gravel", 1, 60),
    "sand_from_gravel": ("minecraft:gravel", "minecraft:sand", 1, 60),
    "cobbled_deepslate": ("minecraft:deepslate", "minecraft:cobbled_deepslate", 1, 80),
    "gravel_from_blackstone": ("minecraft:blackstone", "minecraft:gravel", 1, 80),
    "gravel_from_basalt": ("minecraft:basalt", "minecraft:gravel", 1, 80),
    "sand_from_glass": ("minecraft:glass", "minecraft:sand", 1, 40),
    "bone_meal_from_block": ("minecraft:bone_block", "minecraft:bone_meal", 9, 100),
    "cobblestone_from_stone": ("minecraft:stone", "minecraft:cobblestone", 1, 40),
}
#: Flint is deliberately not here. It is the one crushing job Age I's mixer
#: does with a chance attached, and a guaranteed flint on the grid would retire
#: the mixer rather than succeed it — the crusher is supposed to be bulk rock
#: work, not a better version of every earlier machine.

ROLLING = {
    "iron_plate": ("minecraft:iron_ingot", f"{NS}:iron_plate", 1, 100),
    "copper_plate": ("minecraft:copper_ingot", f"{NS}:copper_plate", 1, 80),
    "gold_plate": ("minecraft:gold_ingot", f"{NS}:gold_plate", 1, 100),
    "steel_plate": ("#c:ingots/steel", f"{NS}:steel_plate", 1, 140),
}

SMELTING = {
    "steel_ingot": ("minecraft:iron_ingot", f"{NS}:steel_ingot", 1, 200),
    "glass_from_sand": ("minecraft:sand", "minecraft:glass", 1, 60),
    "stone_from_cobblestone": ("minecraft:cobblestone", "minecraft:stone", 1, 60),
    "brick_from_clay": ("minecraft:clay_ball", "minecraft:brick", 1, 60),
    "iron_from_raw": ("minecraft:raw_iron", "minecraft:iron_ingot", 1, 100),
    "copper_from_raw": ("minecraft:raw_copper", "minecraft:copper_ingot", 1, 100),
    "gold_from_raw": ("minecraft:raw_gold", "minecraft:gold_ingot", 1, 100),
}

#: One in, one out, and the block doubles it. The count here is what a
#: separator recovers per pass; the doubling is IndustryRules.SEPARATOR_MULTIPLIER
#: and is applied by the machine, so the ladder is one number in one place.
SEPARATING = {
    "iron_from_raw": ("minecraft:raw_iron", "minecraft:iron_ingot", 1, 200),
    "copper_from_raw": ("minecraft:raw_copper", "minecraft:copper_ingot", 1, 200),
    "gold_from_raw": ("minecraft:raw_gold", "minecraft:gold_ingot", 1, 200),
    "scrap_from_debris": ("minecraft:ancient_debris", "minecraft:netherite_scrap", 1, 400),
}

KINDS = (("crushing", CRUSHING), ("rolling", ROLLING),
         ("smelting", SMELTING), ("separating", SEPARATING))


def data_files():
    written = 0
    for name in BLOCKS:
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"), drop_itself(name))
        written += 1
    for name, recipe in list(RECIPES.items()) + list(BRICK_RECIPES.items()):
        write(os.path.join(DATA, "recipe", name + ".json"), recipe)
        written += 1

    # The two brick machines' jobs are NOT written out as data.
    #
    # Every other machine in this module reads a datapack recipe, and that is
    # right for them: they have catalogues, and a catalogue is a thing a pack
    # maker changes. These two have three jobs between them and all three are
    # structural — coal into coke, wood into charcoal, iron into steel — so
    # the block reads FurnaceRules directly and there is no file in between to
    # drift from it. A serializer written for three rows nobody will add to is
    # a fourth place for the same numbers to live.

    for kind, table in KINDS:
        for name, (item, result, count, ticks) in table.items():
            write(os.path.join(DATA, "recipe", kind, name + ".json"), {
                "type": f"{NS}:{kind}",
                # A leading hash is a tag; anything else is one item.
                "input": {"tag": item[1:]} if item.startswith("#") else {"item": item},
                "result": {"id": result, "count": count},
                "ticks": ticks,
            })
            written += 1

    # Coke is a furnace fuel, for as long as FurnaceRules.COKE_BURN says, so a
    # vanilla furnace, the turbine and every engine that burns fuel burn it.
    # NeoForge reads fuel from the data map neoforge:furnace_fuels, and a data
    # map's file sits under the map's namespace, not the writer's: under this
    # module's own it would name a map that does not exist and burn nothing.
    # No "replace", so vanilla's fuels and every other mod's stay.
    fuels = os.path.join(OUT, "src/main/resources/data/neoforge/data_maps/item")
    write(os.path.join(fuels, "furnace_fuels.json"),
          {"values": {f"{NS}:{COKE}": {"burn_time": COKE_BURN}}})
    written += 1

    # Every block in the age is metal, so every block in the age is a pickaxe job.
    picks = os.path.join(OUT, "src/main/resources/data/minecraft/tags/block")
    write(os.path.join(picks, "mineable/pickaxe.json"),
          {"replace": False, "values": [f"{NS}:{name}" for name in BLOCKS]})
    written += 1

    # The common tags, so elysium-foundry can supply steel and plate by its own
    # routes later without either module naming the other.
    common = os.path.join(OUT, "src/main/resources/data/c/tags/item")
    write(os.path.join(common, "ingots/steel.json"),
          {"replace": False, "values": [f"{NS}:steel_ingot"]})
    for metal in ("iron", "copper", "gold", "steel"):
        write(os.path.join(common, f"plates/{metal}.json"),
              {"replace": False, "values": [f"{NS}:{metal}_plate"]})
    written += 5
    return written


# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

NAMES = {
    "copper_wire": "Copper Wire",
    "gold_wire": "Gold Wire",
    "steel_wire": "Steel Wire",
    "transformer": "Transformer",
    "steam_turbine": "Steam Turbine",
    "diesel_engine": "Diesel Engine",
    "alternator": "Alternator",
    "motor": "Motor",
    "crusher": "Crusher",
    "rolling_mill": "Rolling Mill",
    "arc_furnace": "Arc Furnace",
    "separator": "Separator",
    BRICKS[0]: "Coke Brick",
    BRICKS[1]: "Blast Brick",
    SHAPE_IDS[0]: "Coke Oven",
    SHAPE_IDS[1]: "Blast Furnace",
}

ITEM_NAMES = {
    "steel_ingot": "Steel Ingot",
    "iron_plate": "Iron Plate",
    "copper_plate": "Copper Plate",
    "gold_plate": "Gold Plate",
    "steel_plate": "Steel Plate",
    COKE: "Coke",
}

LINES = {
    "itemGroup.elysiumindustry": "Elysium Industry",
    "elysiumindustry.grid.mixed": "Two voltages on one grid. Put a transformer "
                                  "where they meet.",
    "elysiumindustry.grid.burned": "Burned out: %s through a wire rated for %s.",
    "elysiumindustry.grid.dark": "Nothing on this grid is generating.",
    "elysiumindustry.grid.short": "Running at %s: %s supplied against %s wanted.",
    "elysiumindustry.grid.fed": "Fed: %s supplied against %s wanted.",
    "elysiumindustry.alternator.still": "Nothing is turning it.",
    "elysiumindustry.alternator.slow": "Turning at %s rpm; it needs %s to make anything.",
    "elysiumindustry.alternator.making": "Making %s at %s rpm.",
    "elysiumindustry.motor.dark": "No power reaching it.",
    "elysiumindustry.motor.turning": "Fed %s, turning the line at %s rpm.",
    "elysiumindustry.furnace.unformed":
        "Not a %s yet. Relative to this block, %s.",
    "elysiumindustry.furnace.empty": "Built, and empty.",
    "elysiumindustry.furnace.working": "Firing: %s tick(s) in.",
    "elysiumindustry.furnace.unfuelled":
        "Waiting for %s: a job burns %s, and it holds %s.",
}

#: What each machine says, in its own words.
SAYINGS = {
    "crusher": ("The jaws are still.", "Running at %s, with nothing in the hopper.",
                "Crushing, at %s."),
    "rolling_mill": ("The rollers are still.", "Running at %s, with nothing on the bed.",
                     "Rolling, at %s."),
    "arc_furnace": ("The arc is out.", "Running at %s, with an empty crucible.",
                    "Smelting, at %s."),
    "separator": ("The drum is still.", "Running at %s, with nothing to sort.",
                  "Separating, at %s."),
}

#: What each engine says. Three lines, because "no fuel" and "fuel, but nothing
#: wants the power" are different problems with the same silence.
FIRED = {
    "steam_turbine": ("Burning: %s to the grid, %ss of fuel left.",
                      "The firebox is empty.",
                      "Banked: it has fuel, but nothing on the grid wants power."),
    "diesel_engine": ("Running: %s to the grid, %ss of fuel left.",
                      "The tank is empty.",
                      "Idle: it has fuel, but nothing on the grid wants power."),
}


def language():
    entries = dict(LINES)
    for machine, (dark, empty, working) in SAYINGS.items():
        entries[f"{NS}.{machine}.dark"] = dark
        entries[f"{NS}.{machine}.empty"] = empty
        entries[f"{NS}.{machine}.working"] = working
    for engine, (burning, empty, idle) in FIRED.items():
        entries[f"{NS}.{engine}.burning"] = burning
        entries[f"{NS}.{engine}.empty"] = empty
        entries[f"{NS}.{engine}.idle"] = idle
    for name, shown in NAMES.items():
        entries[f"block.{NS}.{name}"] = shown
    for name, shown in ITEM_NAMES.items():
        entries[f"item.{NS}.{name}"] = shown
    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(entries.items())))
    return len(entries)


if __name__ == "__main__":
    sprites = draw_all()
    models = models_and_states()
    data = data_files()
    keys = language()
    print(f"industry: {sprites} sprite(s), {models} thing(s) modelled and stated, "
          f"{data} data file(s), {keys} language key(s)")
