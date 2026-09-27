#!/usr/bin/env python3
"""
Every file Age III needs that is not Java.

    python3 tools/gen_atomics.py

Written from one place for the reason the ages below have generators: ten
blocks and three items is more than fifty JSON files that have to agree with
each other about names, and the last one is always the one that is wrong.
validate.py regenerates the whole set into a temporary tree and compares it
byte for byte, so a file edited by hand fails the build rather than being
silently undone the next time this runs.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumatomics"
IND = "elysiumindustry"

OUT = os.environ.get("ELYSIUM_ATOMICS_OUT", MOD)
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
# Age I was timber and brass, Age II painted steel. Age III is sealed white
# plant and one hot colour that only ever means "this is running", plus a cold
# blue for anything carrying coolant. Five ramps again: a texture set with
# thirty colours in it looks like six mods.

SHELL = [(150, 154, 158), (196, 200, 204), (226, 229, 232), (244, 246, 248)]
STEEL = [(38, 41, 46), (72, 78, 86), (112, 120, 130), (156, 164, 174)]
COLD = [(18, 62, 86), (28, 104, 138), (52, 152, 186), (120, 206, 226)]
HOT = [(90, 22, 10), (168, 58, 14), (226, 118, 24), (252, 194, 76)]
GLOW = [(30, 70, 36), (46, 126, 58), (86, 196, 92), (168, 240, 150)]
CLEAR = (0, 0, 0, 0)


def rgba(colour, alpha=255):
    return (colour[0], colour[1], colour[2], alpha)


def plate(ramp, rivets=True):
    """A sealed panel: flat, a lit top edge, a dark bottom, bolts at the corners."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], fill=rgba(ramp[1]))
    draw.line([(0, 0), (15, 0)], fill=rgba(ramp[2]))
    draw.line([(0, 0), (0, 15)], fill=rgba(ramp[2]))
    draw.line([(0, 15), (15, 15)], fill=rgba(ramp[0]))
    draw.line([(15, 0), (15, 15)], fill=rgba(ramp[0]))
    if rivets:
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            draw.point((x, y), fill=rgba(STEEL[1]))
    return image


def cell_face(ramp, bars):
    """A cell seen from the side: a gauge with that many bars lit."""
    image = plate(SHELL)
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 2, 12, 13], fill=rgba(STEEL[0]), outline=rgba(STEEL[2]))
    for index in range(bars):
        y = 12 - index * 2
        draw.line([(4, y), (11, y)], fill=rgba(ramp[2 if index % 2 else 3]))
    return image


def cell_top(ramp):
    image = plate(SHELL)
    draw = ImageDraw.Draw(image)
    draw.rectangle([4, 4, 11, 11], fill=rgba(STEEL[1]), outline=rgba(STEEL[0]))
    draw.rectangle([6, 6, 9, 9], fill=rgba(ramp[2]))
    return image


def reactor_face(lit):
    """A core seen from the side: a shielded window, dark or alight."""
    image = plate(SHELL)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 2, 13, 13], fill=rgba(STEEL[0]), outline=rgba(STEEL[2]))
    inner = HOT if lit else STEEL
    draw.rectangle([4, 4, 11, 11], fill=rgba(inner[1]), outline=rgba(inner[0]))
    if lit:
        draw.rectangle([5, 6, 10, 10], fill=rgba(HOT[2]))
        draw.line([(6, 8), (9, 8)], fill=rgba(HOT[3]))
        draw.point((7, 5), fill=rgba(HOT[3]))
    else:
        draw.line([(5, 6), (10, 6)], fill=rgba(STEEL[2]))
        draw.line([(5, 9), (10, 9)], fill=rgba(STEEL[2]))
    return image


def reactor_top():
    image = plate(SHELL)
    draw = ImageDraw.Draw(image)
    draw.ellipse([3, 3, 12, 12], fill=rgba(STEEL[0]), outline=rgba(STEEL[2]))
    for x, y in ((6, 6), (9, 6), (6, 9), (9, 9)):
        draw.point((x, y), fill=rgba(GLOW[2]))
    return image


def coolant_side():
    image = plate(SHELL, rivets=False)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 5, 15, 10], fill=rgba(COLD[1]), outline=rgba(COLD[0]))
    for x in range(1, 15, 3):
        draw.line([(x, 6), (x, 9)], fill=rgba(COLD[3]))
    return image


def coolant_end():
    image = plate(SHELL, rivets=False)
    draw = ImageDraw.Draw(image)
    draw.ellipse([4, 4, 11, 11], fill=rgba(COLD[1]), outline=rgba(COLD[0]))
    draw.ellipse([6, 6, 9, 9], fill=rgba(COLD[3]))
    return image


def solar_top():
    """Cells in a frame, seen from above. The one texture anybody looks at."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], fill=rgba(STEEL[1]))
    for x in range(1, 15, 3):
        for y in range(1, 15, 3):
            draw.rectangle([x, y, x + 1, y + 1], fill=rgba((26, 38, 92)))
            draw.point((x, y), fill=rgba((56, 78, 160)))
    return image


def solar_side():
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 12, 15, 15], fill=rgba(SHELL[1]), outline=rgba(SHELL[0]))
    draw.line([(0, 12), (15, 12)], fill=rgba(SHELL[3]))
    return image


def servo_side():
    image = plate(SHELL, rivets=False)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 4, 15, 11], fill=rgba(STEEL[1]), outline=rgba(STEEL[0]))
    for x in range(1, 15, 2):
        draw.line([(x, 5), (x, 10)], fill=rgba(COLD[2]))
    return image


def servo_end():
    image = plate(SHELL, rivets=False)
    draw = ImageDraw.Draw(image)
    draw.ellipse([2, 2, 13, 13], fill=rgba(STEEL[1]), outline=rgba(STEEL[0]))
    draw.ellipse([5, 5, 10, 10], fill=rgba(COLD[1]))
    draw.rectangle([6, 6, 9, 9], fill=rgba(STEEL[2]))
    return image


def works_side(accent):
    image = plate(SHELL)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 8, 13, 13], fill=rgba(STEEL[1]), outline=rgba(STEEL[0]))
    draw.rectangle([3, 2, 12, 6], fill=rgba(accent[1]), outline=rgba(accent[0]))
    draw.line([(4, 4), (11, 4)], fill=rgba(accent[3]))
    return image


def enricher_top():
    image = plate(SHELL)
    draw = ImageDraw.Draw(image)
    draw.ellipse([3, 3, 12, 12], fill=rgba(GLOW[1]), outline=rgba(GLOW[0]))
    draw.ellipse([6, 6, 9, 9], fill=rgba(GLOW[3]))
    return image


def chamber_top():
    image = plate(SHELL)
    draw = ImageDraw.Draw(image)
    draw.rectangle([4, 2, 11, 13], fill=rgba(COLD[1]), outline=rgba(COLD[0]))
    for y in (4, 7, 10):
        draw.line([(5, y), (10, y)], fill=rgba(COLD[3]))
    return image


def centrifuge_top():
    image = plate(SHELL)
    draw = ImageDraw.Draw(image)
    draw.ellipse([2, 2, 13, 13], fill=rgba(STEEL[0]), outline=rgba(STEEL[2]))
    draw.arc([4, 4, 11, 11], 0, 180, fill=rgba(SHELL[3]))
    draw.arc([4, 4, 11, 11], 180, 360, fill=rgba(STEEL[2]))
    draw.point((7, 7), fill=rgba(GLOW[2]))
    return image


def ingot(ramp):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.polygon([(3, 10), (5, 6), (11, 6), (13, 10), (13, 11), (3, 11)],
                 fill=rgba(ramp[1]), outline=rgba(ramp[0]))
    draw.line([(5, 7), (10, 7)], fill=rgba(ramp[3]))
    draw.line([(4, 10), (12, 10)], fill=rgba(ramp[0]))
    return image


def lump(ramp):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.polygon([(4, 11), (3, 7), (6, 4), (11, 5), (12, 9), (10, 12)],
                 fill=rgba(ramp[1]), outline=rgba(ramp[0]))
    draw.point((6, 7), fill=rgba(ramp[3]))
    draw.point((9, 8), fill=rgba(ramp[2]))
    draw.point((7, 10), fill=rgba(ramp[0]))
    return image


def rod(ramp):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([6, 2, 9, 13], fill=rgba(ramp[1]), outline=rgba(ramp[0]))
    for y in range(3, 13, 3):
        draw.line([(7, y), (8, y)], fill=rgba(ramp[3]))
    draw.rectangle([5, 1, 10, 2], fill=rgba(STEEL[1]), outline=rgba(STEEL[0]))
    return image


URANIUM = [(40, 58, 34), (66, 96, 52), (96, 134, 70), (140, 184, 96)]

# ---------------------------------------------------------------------------
# The reactor hall, read out of its own rules
# ---------------------------------------------------------------------------

HALL_SOURCE = open(os.path.join(
    MOD, "src/main/java/com/elysium/atomics/rules/HallRules.java"),
    encoding="utf-8").read()
HALL = {key: re.search(r'String ' + key + r' = "([a-z_]+)"', HALL_SOURCE)
        for key in ("CASING", "ROD", "CONTROLLER")}
if not all(HALL.values()):
    print("HallRules no longer says what a reactor hall is built of")
    sys.exit(2)
HALL = {key: found.group(1) for key, found in HALL.items()}
LARGEST = int(re.search(r"int LARGEST = (\d+);", HALL_SOURCE).group(1))


def casing_face():
    """Plated wall: the outside of something you are not meant to open."""
    image = Image.new("RGBA", (16, 16), (108, 112, 116, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], outline=(72, 76, 80, 255))
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        draw.rectangle([x - 1, y - 1, x, y], fill=(150, 154, 158, 255))
    draw.line([1, 8, 14, 8], fill=(88, 92, 96, 255))
    return image


def rod_face():
    """A fuel rod, stood on end and glowing where it is doing its work."""
    image = Image.new("RGBA", (16, 16), (76, 80, 84, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], outline=(52, 56, 60, 255))
    for column in (4, 8, 12):
        draw.rectangle([column - 1, 2, column, 13], fill=(140, 146, 150, 255))
        draw.rectangle([column - 1, 5, column, 10], fill=(150, 226, 120, 255))
    return image


def port_face(formed):
    """The port: the one block of the wall with a window in it."""
    image = casing_face()
    draw = ImageDraw.Draw(image)
    glass = (150, 226, 120) if formed else (60, 74, 66)
    draw.rectangle([4, 5, 11, 12], fill=(40, 44, 48, 255),
                   outline=(150, 154, 158, 255))
    draw.rectangle([6, 7, 9, 10], fill=glass + (255,))
    return image


BLOCK_SPRITES = {
    HALL["CASING"]: casing_face,
    HALL["ROD"]: rod_face,
    HALL["CONTROLLER"]: lambda: port_face(False),
    HALL["CONTROLLER"] + "_on": lambda: port_face(True),
    "basic_cell": lambda: cell_face(GLOW, 2),
    "advanced_cell": lambda: cell_face(COLD, 4),
    "quantum_cell": lambda: cell_face(HOT, 6),
    "basic_cell_top": lambda: cell_top(GLOW),
    "advanced_cell_top": lambda: cell_top(COLD),
    "quantum_cell_top": lambda: cell_top(HOT),
    "reactor_core": lambda: reactor_face(False),
    "reactor_core_on": lambda: reactor_face(True),
    "reactor_core_top": reactor_top,
    "coolant_channel": coolant_side,
    "coolant_channel_end": coolant_end,
    "solar_panel_top": solar_top,
    "solar_panel_side": solar_side,
    "servo": servo_side,
    "servo_end": servo_end,
    "works_side": lambda: works_side(STEEL),
    "enricher_top": enricher_top,
    "reaction_chamber_top": chamber_top,
    "centrifuge_top": centrifuge_top,
}

ITEM_SPRITES = {
    "raw_uranium": lambda: lump(URANIUM),
    "uranium_ingot": lambda: ingot(URANIUM),
    "enriched_uranium": lambda: rod(GLOW),
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


#: The panel's shape, which has to match SolarPanelBlock's collision box
#: exactly. A model that disagreed with the box would be a block you can hit
#: where you cannot see it, and neither file can notice that on its own.
PANEL_HEIGHT = 4


def panel_model():
    return {
        "parent": "block/block",
        # The side face reads transparent rows of its sprite; on the solid
        # layer (a modded model's default) they were black.
        "render_type": "minecraft:cutout",
        "textures": {"top": f"{NS}:block/solar_panel_top",
                     "side": f"{NS}:block/solar_panel_side",
                     "particle": f"{NS}:block/solar_panel_top"},
        "elements": [{
            "from": [0, 0, 0],
            "to": [16, PANEL_HEIGHT, 16],
            "faces": {
                "down": {"texture": "#side", "cullface": "down"},
                "up": {"texture": "#top"},
                "north": {"texture": "#side"},
                "south": {"texture": "#side"},
                "west": {"texture": "#side"},
                "east": {"texture": "#side"},
            },
        }],
    }


CUBES = {
    HALL["CASING"]: (HALL["CASING"], HALL["CASING"]),
    HALL["ROD"]: (HALL["ROD"], HALL["ROD"]),
    "basic_cell": ("basic_cell_top", "basic_cell"),
    "advanced_cell": ("advanced_cell_top", "advanced_cell"),
    "quantum_cell": ("quantum_cell_top", "quantum_cell"),
    "enricher": ("enricher_top", "works_side"),
    "reaction_chamber": ("reaction_chamber_top", "works_side"),
    "centrifuge": ("centrifuge_top", "works_side"),
}

SPINDLES = {"servo": ("servo", "servo_end")}
PIPES = {"coolant_channel": ("coolant_channel", "coolant_channel_end")}
CORES = {"reactor_core": ("reactor_core", "reactor_core_top")}
PANELS = ("solar_panel",)

BLOCKS = (list(CUBES) + list(SPINDLES) + list(PIPES) + list(CORES)
          + list(PANELS) + [HALL["CONTROLLER"]])


def core_model(side, top):
    return {
        "parent": "block/block",
        "textures": {"side": f"{NS}:block/{side}", "top": f"{NS}:block/{top}",
                     "particle": f"{NS}:block/{side}"},
        "elements": [{
            "from": [0, 0, 0],
            "to": [16, 16, 16],
            "faces": {
                "down": {"texture": "#top", "cullface": "down"},
                "up": {"texture": "#top", "cullface": "up"},
                "north": {"texture": "#side", "cullface": "north"},
                "south": {"texture": "#side", "cullface": "south"},
                "west": {"texture": "#side", "cullface": "west"},
                "east": {"texture": "#side", "cullface": "east"},
            },
        }],
    }


def models_and_states():
    written = 0
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
    # The coolant channel is a run: core, arms, plates and dye recipes come
    # from art/runs.py, the same as every pipe, cable, wire and bus in the pack.
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(HERE)), "art"))
    import runs as art_runs
    for name in PIPES:
        art_runs.write_all(NS, name, f"{NS}:block/{name}", ASSETS, DATA, write)
        written += 1
    for name, (side, top) in CORES.items():
        write(os.path.join(ASSETS, "models/block", name + ".json"), core_model(side, top))
        write(os.path.join(ASSETS, "models/block", name + "_on.json"),
              core_model(side + "_on", top))
        # A core that looked the same running and cold would be a core whose
        # one piece of feedback is a message you have to ask for.
        write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {
            "lit=false": {"model": f"{NS}:block/{name}"},
            "lit=true": {"model": f"{NS}:block/{name}_on"},
        }})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})
        written += 1
    # The port: one model per size the hall can be, because the block says
    # out loud which size formed around it and a block that looked the same
    # at every size would be a block whose one piece of feedback is a message
    # you have to ask for.
    port = HALL["CONTROLLER"]
    write(os.path.join(ASSETS, "models/block", port + ".json"),
          cube(HALL["CASING"], port))
    write(os.path.join(ASSETS, "models/block", port + "_on.json"),
          cube(HALL["CASING"], port + "_on"))
    write(os.path.join(ASSETS, "blockstates", port + ".json"), {"variants": {
        **{f"side={size}": {"model": f"{NS}:block/{port}"} for size in (0,)},
        **{f"side={size}": {"model": f"{NS}:block/{port}_on"}
           for size in range(1, LARGEST + 1)},
    }})
    write(os.path.join(ASSETS, "models/item", port + ".json"),
          {"parent": f"{NS}:block/{port}"})
    written += 1
    for name in PANELS:
        write(os.path.join(ASSETS, "models/block", name + ".json"), panel_model())
        write(os.path.join(ASSETS, "blockstates", name + ".json"),
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
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

# Named with the suffix because the palette above already owns the bare word,
# and a ramp of colours quietly becoming a recipe ingredient is the sort of
# thing that produces a stack trace forty lines from where the mistake is.
STEEL_INGOT_TAG = TAG("c:ingots/steel")
STEEL_PLATE = TAG("c:plates/steel")
GOLD_PLATE = TAG("c:plates/gold")
COPPER_PLATE = TAG("c:plates/copper")

#: Everything here is built out of Age II's plate and Age II's machines. That
#: is the gate: you do not reach the third age by finding a new ore, you reach
#: it by having a factory already.
RECIPES = {
    HALL["CASING"]: shaped(["PSP", "S S", "PSP"], {
        "P": STEEL_PLATE, "S": ITEM(f"{IND}:steel_ingot")},
        HALL["CASING"], 4),
    HALL["ROD"]: shaped([" E ", "PSP", " E "], {
        "E": ITEM(f"{NS}:enriched_uranium"), "P": STEEL_PLATE,
        "S": ITEM(f"{IND}:steel_ingot")}, HALL["ROD"]),
    HALL["CONTROLLER"]: shaped(["CGC", "PRP", "CGC"], {
        "C": ITEM(f"{NS}:" + HALL["CASING"]), "G": ITEM("minecraft:glass"),
        "P": GOLD_PLATE, "R": ITEM("minecraft:redstone_block")},
        HALL["CONTROLLER"]),
    "basic_cell": shaped(["PRP", "PCP", "PRP"], {
        "P": STEEL_PLATE, "R": ITEM("minecraft:redstone_block"),
        "C": ITEM(f"{IND}:copper_wire")}, "basic_cell"),
    "advanced_cell": shaped(["PGP", "GBG", "PGP"], {
        "P": STEEL_PLATE, "G": GOLD_PLATE, "B": ITEM(f"{NS}:basic_cell")},
        "advanced_cell"),
    "quantum_cell": shaped(["PEP", "EAE", "PEP"], {
        "P": STEEL_PLATE, "E": ITEM("minecraft:ender_pearl"),
        "A": ITEM(f"{NS}:advanced_cell")}, "quantum_cell"),
    "reactor_core": shaped(["PPP", "PFP", "PPP"], {
        "P": STEEL_PLATE, "F": ITEM(f"{IND}:arc_furnace")}, "reactor_core"),
    "coolant_channel": shaped([" P ", "PIP", " P "], {
        "P": COPPER_PLATE, "I": ITEM("minecraft:packed_ice")}, "coolant_channel", 4),
    "solar_panel": shaped(["GGG", "QQQ", "PCP"], {
        "G": ITEM("minecraft:glass"), "Q": ITEM("minecraft:quartz"),
        "P": COPPER_PLATE, "C": ITEM(f"{IND}:copper_wire")}, "solar_panel"),
    "servo": shaped(["PGP", "PMP", "PGP"], {
        "P": STEEL_PLATE, "G": ITEM(f"{IND}:gold_wire"),
        "M": ITEM(f"{IND}:motor")}, "servo"),
    "enricher": shaped(["PPP", "PSP", "SSS"], {
        "P": STEEL_PLATE, "S": ITEM(f"{IND}:separator")}, "enricher"),
    "reaction_chamber": shaped(["PGP", "GCG", "PGP"], {
        "P": STEEL_PLATE, "G": ITEM("minecraft:glass"),
        "C": ITEM(f"{IND}:crusher")}, "reaction_chamber"),
    "centrifuge": shaped(["PPP", "PRP", "SSS"], {
        "P": STEEL_PLATE, "R": ITEM(f"{IND}:rolling_mill"), "S": STEEL_INGOT_TAG},
        "centrifuge"),
}

#: The one vanilla recipe this module ships, and the reason it does. The ore
#: is elysium-ores'; every item that used to fill c:raw_materials/uranium
#: belonged to a mod this conversion is replacing, so a smelting recipe has to
#: come from somewhere and the age that wants the metal is the age that owes it.
SMELTING = {
    "uranium_ingot_from_raw": {
        "type": "minecraft:smelting",
        "category": "misc",
        "ingredient": {"tag": "c:raw_materials/uranium"},
        "result": {"id": f"{NS}:uranium_ingot"},
        "experience": 0.7,
        "cookingtime": 200,
    },
    "uranium_ingot_from_raw_blasting": {
        "type": "minecraft:blasting",
        "category": "misc",
        "ingredient": {"tag": "c:raw_materials/uranium"},
        "result": {"id": f"{NS}:uranium_ingot"},
        "experience": 0.7,
        "cookingtime": 100,
    },
}


# ---------------------------------------------------------------------------
# What the machines do
# ---------------------------------------------------------------------------
#
# Ticks are at a full share; a starved grid is proportionally slower.

#: One in, one out, and the block triples it. The count here is what one pass
#: recovers; the tripling is AtomicRules.ENRICHER_MULTIPLIER and is applied by
#: the machine, so the spine's material ladder is one number in one place.
ENRICHING = {
    "iron_from_raw": ("minecraft:raw_iron", "minecraft:iron_ingot", 1, 300),
    "copper_from_raw": ("minecraft:raw_copper", "minecraft:copper_ingot", 1, 300),
    "gold_from_raw": ("minecraft:raw_gold", "minecraft:gold_ingot", 1, 300),
    "scrap_from_debris": ("minecraft:ancient_debris", "minecraft:netherite_scrap", 1, 600),
    "uranium_from_raw": ("#c:raw_materials/uranium", f"{NS}:uranium_ingot", 1, 400),
}

REACTING = {
    "leather_from_flesh": ("minecraft:rotten_flesh", "minecraft:leather", 1, 200),
    "slime_from_clay": ("minecraft:clay_ball", "minecraft:slime_ball", 1, 240),
    "coal_from_charcoal": ("minecraft:charcoal", "minecraft:coal", 1, 300),
    "glowstone_from_redstone": ("minecraft:redstone", "minecraft:glowstone_dust", 1, 260),
}

CENTRIFUGING = {
    "enriched_uranium": (f"{NS}:uranium_ingot", f"{NS}:enriched_uranium", 1, 900),
    "gunpowder_from_sand": ("minecraft:soul_sand", "minecraft:gunpowder", 2, 240),
    "quartz_from_netherrack": ("minecraft:netherrack", "minecraft:quartz", 1, 300),
}

KINDS = (("enriching", ENRICHING), ("reacting", REACTING),
         ("centrifuging", CENTRIFUGING))


def data_files():
    written = 0
    for name in BLOCKS:
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"), drop_itself(name))
        written += 1
    for name, recipe in RECIPES.items():
        write(os.path.join(DATA, "recipe", name + ".json"), recipe)
        written += 1
    for name, recipe in SMELTING.items():
        write(os.path.join(DATA, "recipe", name + ".json"), recipe)
        written += 1
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

    picks = os.path.join(OUT, "src/main/resources/data/minecraft/tags/block")
    write(os.path.join(picks, "mineable/pickaxe.json"),
          {"replace": False, "values": [f"{NS}:{name}" for name in BLOCKS]})
    written += 1

    # The uranium this age owes the pack. elysium-ores generates the ore and
    # drops whatever fills these tags; everything that used to fill them
    # belongs to a mod being replaced, so an ore that drops nothing is what is
    # left behind unless the age that wants the metal supplies it.
    common = os.path.join(OUT, "src/main/resources/data/c/tags/item")
    write(os.path.join(common, "raw_materials/uranium.json"),
          {"replace": False, "values": [f"{NS}:raw_uranium"]})
    write(os.path.join(common, "ingots/uranium.json"),
          {"replace": False, "values": [f"{NS}:uranium_ingot"]})
    written += 2
    return written


# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

NAMES = {
    HALL["CASING"]: "Reactor Casing",
    HALL["ROD"]: "Fuel Rod",
    HALL["CONTROLLER"]: "Reactor Port",
    "basic_cell": "Basic Cell",
    "advanced_cell": "Advanced Cell",
    "quantum_cell": "Quantum Cell",
    "reactor_core": "Reactor Core",
    "coolant_channel": "Coolant Channel",
    "solar_panel": "Solar Panel",
    "servo": "Servo",
    "enricher": "Enricher",
    "reaction_chamber": "Reaction Chamber",
    "centrifuge": "Centrifuge",
}

ITEM_NAMES = {
    "raw_uranium": "Raw Uranium",
    "uranium_ingot": "Uranium Ingot",
    "enriched_uranium": "Enriched Uranium",
}

LINES = {
    "itemGroup.elysiumatomics": "Elysium Atomics",
    "elysiumatomics.hall.unformed": "No reactor here. Nearest miss: %s.",
    "elysiumatomics.hall.running":
        "A %s hall: %s rod(s), %s channel(s), making %s FE/t at %s degrees. "
        "Safe up to %s%% throttle.",
    "elysiumatomics.cell.holding": "Holding %s of %s (%s).",
    "elysiumatomics.cell.charging": "Charging: %s of %s (%s).",
    "elysiumatomics.cell.draining": "Draining: %s of %s (%s).",
    "elysiumatomics.solar_panel.dark": "No sky above it, or no sun in it.",
    "elysiumatomics.solar_panel.lit": "In the sun: %s to the grid.",
    "elysiumatomics.servo.dark": "No power reaching it.",
    "elysiumatomics.servo.turning": "Fed %s, turning the line at %s rpm.",
    "elysiumatomics.reactor.unfuelled": "Dial at %s, and nothing in the hopper.",
    "elysiumatomics.reactor.running": "Dial at %s: %s to the grid, %sC and settling at "
                                      "%sC on %s channel(s).",
    "elysiumatomics.reactor.hot": "Hot: %sC, settling at %sC. Worn %s. Plumb it or "
                                  "turn it down.",
    "elysiumatomics.reactor.critical": "CRITICAL: %sC, settling at %sC. Worn %s. Turn "
                                       "it down now.",
    "elysiumatomics.reactor.spent": "Worn out. It needs steel plate before it will run "
                                    "again.",
}

#: What each machine says. Three lines each, because "nothing is reaching it"
#: and "it is running and empty" are different problems with the same silence.
SAYINGS = {
    "enricher": ("The vats are cold.", "Running at %s, with nothing to dissolve.",
                 "Dissolving, at %s."),
    "reaction_chamber": ("The chamber is cold.", "Running at %s, with nothing in it.",
                         "Reacting, at %s."),
    "centrifuge": ("The drum is still.", "Running at %s, with nothing to spin.",
                   "Spinning, at %s."),
}


def language():
    entries = dict(LINES)
    for machine, (dark, empty, working) in SAYINGS.items():
        entries[f"{NS}.{machine}.dark"] = dark
        entries[f"{NS}.{machine}.empty"] = empty
        entries[f"{NS}.{machine}.working"] = working
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
    print(f"atomics: {sprites} sprite(s), {models} thing(s) modelled and stated, "
          f"{data} data file(s), {keys} language key(s)")
