#!/usr/bin/env python3
"""
Every file storage needs that is not Java.

    python3 tools/gen_logistics.py

A drawer's blockstate carries which way it faces and how full it is, which is
four times five variants per tier before anybody has written a line by hand.
That is exactly the shape of file nobody keeps straight for long, so it is
written from here and validate.py regenerates the whole set into a temporary
tree and compares it byte for byte.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumlogistics"
IND = "elysiumindustry"

OUT = os.environ.get("ELYSIUM_LOGISTICS_OUT", MOD)
ASSETS = os.path.join(OUT, "src/main/resources/assets", NS)
DATA = os.path.join(OUT, "src/main/resources/data", NS)

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


# ---------------------------------------------------------------------------
# The palette
# ---------------------------------------------------------------------------
#
# The three drawer tiers are the three materials the ages are already built
# out of — timber, brass, steel — so that a player reads a wall of them
# without being told anything.

TIMBER = [(58, 40, 26), (86, 60, 38), (120, 86, 54), (152, 114, 74)]
BRASS = [(74, 56, 22), (122, 94, 38), (176, 138, 58), (214, 181, 96)]
STEEL = [(38, 41, 46), (72, 78, 86), (112, 120, 130), (156, 164, 174)]
GLASS = (168, 196, 208)
FILL = [(62, 96, 58), (92, 148, 84), (128, 196, 112), (176, 232, 150)]
CLEAR = (0, 0, 0, 0)

#: How many steps of fullness a drawer front shows. Five, counting empty,
#: because that is as many as anybody can tell apart across a room and it is
#: what the blockstate carries.
STEPS = 5


def rgba(colour, alpha=255):
    return (colour[0], colour[1], colour[2], alpha)


def carcass(ramp):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], fill=rgba(ramp[1]))
    draw.line([(0, 0), (15, 0)], fill=rgba(ramp[3]))
    draw.line([(0, 0), (0, 15)], fill=rgba(ramp[2]))
    draw.line([(0, 15), (15, 15)], fill=rgba(ramp[0]))
    draw.line([(15, 0), (15, 15)], fill=rgba(ramp[0]))
    return image


def front(ramp, step):
    """A drawer face: a frame, a glass window, and how full it is behind it."""
    image = carcass(ramp)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 2, 13, 13], fill=rgba(GLASS), outline=rgba(ramp[0]))
    if step > 0:
        height = round(10 * step / (STEPS - 1))
        top = 13 - height
        shade = FILL[min(step, len(FILL) - 1)]
        draw.rectangle([3, top, 12, 12], fill=rgba(shade))
        draw.line([(3, top), (12, top)], fill=rgba(FILL[3]))
    draw.rectangle([6, 7, 9, 8], fill=rgba(ramp[3]), outline=rgba(ramp[0]))
    return image


def bus_side():
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([5, 0, 10, 15], fill=rgba(STEEL[1]), outline=rgba(STEEL[0]))
    for y in range(1, 16, 3):
        draw.line([(6, y), (9, y)], fill=rgba(STEEL[3]))
    return image


def bus_end():
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([5, 5, 10, 10], fill=rgba(STEEL[0]), outline=rgba(STEEL[2]))
    draw.point((7, 7), fill=rgba(FILL[2]))
    draw.point((8, 8), fill=rgba(FILL[2]))
    return image


def controller_side():
    image = carcass(STEEL)
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 3, 12, 12], fill=rgba(STEEL[0]), outline=rgba(STEEL[2]))
    for y in (5, 8, 11):
        for x in range(4, 12, 2):
            draw.point((x, y), fill=rgba(FILL[2] if (x + y) % 4 else FILL[3]))
    return image


def controller_top():
    image = carcass(STEEL)
    draw = ImageDraw.Draw(image)
    draw.rectangle([4, 4, 11, 11], fill=rgba(STEEL[0]), outline=rgba(STEEL[2]))
    draw.rectangle([6, 6, 9, 9], fill=rgba(FILL[2]))
    return image


#: The colour of a machine that is doing something. Used nowhere else, so a
#: player scanning a room can tell at a glance which assemblers are working
#: and which are waiting on ingredients — which is the whole diagnostic.
LIT = [(120, 60, 18), (196, 116, 34), (244, 176, 62), (255, 226, 150)]


def core_side():
    """A core: a case with a lattice on it, which is what it is."""
    image = carcass(STEEL)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 2, 13, 13], fill=rgba(STEEL[0]), outline=rgba(STEEL[2]))
    for step in range(4, 12, 3):
        draw.line([(3, step), (12, step)], fill=rgba(BRASS[2]))
        draw.line([(step, 3), (step, 12)], fill=rgba(BRASS[2]))
    draw.rectangle([7, 7, 8, 8], fill=rgba(LIT[2]))
    return image


def core_top():
    image = carcass(STEEL)
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 3, 12, 12], fill=rgba(STEEL[0]), outline=rgba(STEEL[2]))
    draw.rectangle([5, 5, 10, 10], fill=rgba(BRASS[1]), outline=rgba(BRASS[2]))
    draw.rectangle([7, 7, 8, 8], fill=rgba(LIT[3]))
    return image


def assembler_side():
    image = carcass(STEEL)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 4, 13, 11], fill=rgba(STEEL[0]), outline=rgba(STEEL[2]))
    for x in range(4, 12, 2):
        draw.line([(x, 5), (x, 10)], fill=rgba(STEEL[1]))
    return image


def assembler_front(busy):
    """A grid of nine, lit when there is a job in it."""
    image = carcass(STEEL)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 2, 13, 13], fill=rgba(STEEL[0]), outline=rgba(STEEL[2]))
    ramp = LIT if busy else STEEL
    for row in range(3):
        for column in range(3):
            left = 3 + column * 4
            top = 3 + row * 4
            draw.rectangle([left, top, left + 2, top + 2],
                           fill=rgba(ramp[2 if busy else 1]),
                           outline=rgba(ramp[3 if busy else 0]))
    return image


def terminal_side():
    image = carcass(STEEL)
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 3, 12, 12], fill=rgba(STEEL[0]), outline=rgba(STEEL[2]))
    return image


def terminal_face():
    """A screen with a line of text on it and a cursor."""
    image = carcass(STEEL)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 2, 13, 12], fill=rgba((18, 26, 30)), outline=rgba(STEEL[2]))
    for y in (5, 7):
        draw.line([(4, y), (10, y)], fill=rgba(FILL[2]))
    draw.line([(4, 9), (6, 9)], fill=rgba(FILL[3]))
    draw.point((8, 9), fill=rgba(LIT[2]))
    return image


TIERS = {
    "timber_drawer": TIMBER,
    "brass_drawer": BRASS,
    "steel_drawer": STEEL,
}

BLOCK_SPRITES = {}
for tier, ramp in TIERS.items():
    BLOCK_SPRITES[tier + "_side"] = (lambda r=ramp: carcass(r))
    for step in range(STEPS):
        BLOCK_SPRITES[f"{tier}_front_{step}"] = (
            lambda r=ramp, s=step: front(r, s))
BLOCK_SPRITES["bus"] = bus_side
BLOCK_SPRITES["bus_end"] = bus_end
BLOCK_SPRITES["controller"] = controller_side
BLOCK_SPRITES["controller_top"] = controller_top
BLOCK_SPRITES["assembly_core"] = core_side
BLOCK_SPRITES["assembly_core_top"] = core_top
BLOCK_SPRITES["assembler"] = assembler_side
BLOCK_SPRITES["assembler_front"] = (lambda: assembler_front(False))
BLOCK_SPRITES["assembler_front_busy"] = (lambda: assembler_front(True))
BLOCK_SPRITES["terminal"] = terminal_side
BLOCK_SPRITES["terminal_face"] = terminal_face


def draw_all():
    blocks = os.path.join(ASSETS, "textures/block")
    os.makedirs(blocks, exist_ok=True)
    for name, make in BLOCK_SPRITES.items():
        make().save(os.path.join(blocks, name + ".png"))
    return len(BLOCK_SPRITES)


# ---------------------------------------------------------------------------
# Models and blockstates
# ---------------------------------------------------------------------------

#: Which way to rotate a model so its front faces each direction.
FACING_ROTATION = {"north": 0, "east": 90, "south": 180, "west": 270}


def drawer_model(tier, step):
    return {
        "parent": "block/block",
        "textures": {"front": f"{NS}:block/{tier}_front_{step}",
                     "side": f"{NS}:block/{tier}_side",
                     "particle": f"{NS}:block/{tier}_side"},
        "elements": [{
            "from": [0, 0, 0],
            "to": [16, 16, 16],
            "faces": {
                "down": {"texture": "#side", "cullface": "down"},
                "up": {"texture": "#side", "cullface": "up"},
                "north": {"texture": "#front", "cullface": "north"},
                "south": {"texture": "#side", "cullface": "south"},
                "west": {"texture": "#side", "cullface": "west"},
                "east": {"texture": "#side", "cullface": "east"},
            },
        }],
    }


def drawer_blockstate(tier):
    """Four ways round times five steps of fullness, written once."""
    variants = {}
    for facing, rotation in FACING_ROTATION.items():
        for step in range(STEPS):
            entry = {"model": f"{NS}:block/{tier}_{step}"}
            if rotation:
                entry["y"] = rotation
            variants[f"facing={facing},full={step}"] = entry
    return {"variants": variants}


def pillar(width, side, end):
    low = (16 - width) / 2
    high = 16 - low
    return {
        "parent": "block/block",
        "textures": {"side": f"{NS}:block/{side}", "end": f"{NS}:block/{end}",
                     "particle": f"{NS}:block/{side}"},
        "elements": [{
            "from": [low, low, low],
            "to": [high, high, high],
            "faces": {
                "down": {"texture": "#end"},
                "up": {"texture": "#end"},
                "north": {"texture": "#side"},
                "south": {"texture": "#side"},
                "west": {"texture": "#side"},
                "east": {"texture": "#side"},
            },
        }],
    }


def cube(top, side):
    return {
        "parent": "block/block",
        "textures": {"top": f"{NS}:block/{top}", "side": f"{NS}:block/{side}",
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


#: The bus's shape, which has to match BusBlock's collision box exactly.
BUS_BOX = (5, 11)

BLOCKS = list(TIERS) + ["bus", "controller", "assembly_core", "assembler",
                        "terminal"]


def faced(front, top, side):
    """A cube with one face of its own, for the machines that have a front."""
    return {
        "parent": "block/block",
        "textures": {"front": f"{NS}:block/{front}", "top": f"{NS}:block/{top}",
                     "side": f"{NS}:block/{side}", "particle": f"{NS}:block/{side}"},
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
    for tier in TIERS:
        for step in range(STEPS):
            write(os.path.join(ASSETS, "models/block", f"{tier}_{step}.json"),
                  drawer_model(tier, step))
        write(os.path.join(ASSETS, "blockstates", tier + ".json"),
              drawer_blockstate(tier))
        write(os.path.join(ASSETS, "models/item", tier + ".json"),
              {"parent": f"{NS}:block/{tier}_0"})
        written += 1

    low, high = BUS_BOX
    bus = pillar(high - low, "bus", "bus_end")
    write(os.path.join(ASSETS, "models/block", "bus.json"), bus)
    write(os.path.join(ASSETS, "blockstates", "bus.json"),
          {"variants": {"": {"model": f"{NS}:block/bus"}}})
    write(os.path.join(ASSETS, "models/item", "bus.json"), {"parent": f"{NS}:block/bus"})
    written += 1

    write(os.path.join(ASSETS, "models/block", "controller.json"),
          cube("controller_top", "controller"))
    write(os.path.join(ASSETS, "blockstates", "controller.json"),
          {"variants": {"": {"model": f"{NS}:block/controller"}}})
    write(os.path.join(ASSETS, "models/item", "controller.json"),
          {"parent": f"{NS}:block/controller"})
    written += 1

    write(os.path.join(ASSETS, "models/block", "assembly_core.json"),
          cube("assembly_core_top", "assembly_core"))
    write(os.path.join(ASSETS, "blockstates", "assembly_core.json"),
          {"variants": {"": {"model": f"{NS}:block/assembly_core"}}})
    write(os.path.join(ASSETS, "models/item", "assembly_core.json"),
          {"parent": f"{NS}:block/assembly_core"})
    written += 1

    # Two models and a blockstate that picks between them, because "busy" is
    # the one thing about an assembler worth being able to see from a distance.
    for busy in (False, True):
        write(os.path.join(ASSETS, "models/block",
                           "assembler_busy.json" if busy else "assembler.json"),
              faced("assembler_front_busy" if busy else "assembler_front",
                    "assembler", "assembler"))
    write(os.path.join(ASSETS, "blockstates", "assembler.json"), {"variants": {
        "busy=false": {"model": f"{NS}:block/assembler"},
        "busy=true": {"model": f"{NS}:block/assembler_busy"},
    }})
    write(os.path.join(ASSETS, "models/item", "assembler.json"),
          {"parent": f"{NS}:block/assembler"})
    written += 1

    write(os.path.join(ASSETS, "models/block", "terminal.json"),
          faced("terminal_face", "terminal", "terminal"))
    write(os.path.join(ASSETS, "blockstates", "terminal.json"),
          {"variants": {"": {"model": f"{NS}:block/terminal"}}})
    write(os.path.join(ASSETS, "models/item", "terminal.json"),
          {"parent": f"{NS}:block/terminal"})
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

#: The first drawer is planks and a pane, on purpose: it is the rung a player
#: reaches before they have a furnace's worth of anything, and a storage mod
#: whose first rung needs a factory has no first rung.
RECIPES = {
    "timber_drawer": shaped(["PPP", "PGP", "PPP"], {
        "P": TAG("minecraft:planks"), "G": ITEM("minecraft:glass_pane")},
        "timber_drawer", 2),
    "brass_drawer": shaped([" C ", "CDC", " N "], {
        "C": ITEM("minecraft:copper_ingot"), "D": ITEM(f"{NS}:timber_drawer"),
        "N": ITEM("minecraft:gold_nugget")}, "brass_drawer"),
    "steel_drawer": shaped([" S ", "SDS", " S "], {
        "S": TAG("c:ingots/steel"), "D": ITEM(f"{NS}:brass_drawer")}, "steel_drawer"),
    "bus": shaped(["PWP"], {
        "P": TAG("c:plates/iron"), "W": ITEM(f"{IND}:copper_wire")}, "bus", 4),
    "controller": shaped(["PPP", "PRP", "PWP"], {
        "P": TAG("c:plates/steel"), "R": ITEM("minecraft:redstone_block"),
        "W": ITEM(f"{IND}:gold_wire")}, "controller"),
    # The core is a controller with a brain bolted on, and says so in its
    # recipe: you cannot plan a warehouse you have not indexed.
    "assembly_core": shaped(["PWP", "RCR", "PWP"], {
        "P": TAG("c:plates/steel"), "W": ITEM(f"{IND}:gold_wire"),
        "R": ITEM("minecraft:redstone_block"), "C": ITEM(f"{NS}:controller")},
        "assembly_core"),
    # And an assembler is a crafting table that somebody wired up, which is
    # exactly what it does.
    "assembler": shaped(["PPP", "WTW", "PPP"], {
        "P": TAG("c:plates/steel"), "W": ITEM(f"{IND}:copper_wire"),
        "T": ITEM("minecraft:crafting_table")}, "assembler"),
    "terminal": shaped(["PGP", "PRP", "PPP"], {
        "P": TAG("c:plates/iron"), "G": ITEM("minecraft:glass_pane"),
        "R": ITEM("minecraft:redstone")}, "terminal"),
}


def data_files():
    written = 0
    for name in BLOCKS:
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"), drop_itself(name))
        written += 1
    for name, recipe in RECIPES.items():
        write(os.path.join(DATA, "recipe", name + ".json"), recipe)
        written += 1

    tags = os.path.join(OUT, "src/main/resources/data/minecraft/tags/block")
    write(os.path.join(tags, "mineable/axe.json"),
          {"replace": False, "values": [f"{NS}:timber_drawer"]})
    write(os.path.join(tags, "mineable/pickaxe.json"),
          {"replace": False, "values": [f"{NS}:{name}" for name in BLOCKS
                                        if name != "timber_drawer"]})
    written += 2
    return written


# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

NAMES = {
    "timber_drawer": "Timber Drawer",
    "brass_drawer": "Brass Drawer",
    "steel_drawer": "Steel Drawer",
    "bus": "Bus",
    "controller": "Controller",
    "assembly_core": "Assembly Core",
    "assembler": "Assembler",
    "terminal": "Terminal",
}

LINES = {
    "itemGroup.elysiumlogistics": "Elysium Logistics",
    "elysiumlogistics.drawer.empty": "Empty. It will hold %s of each thing.",
    "elysiumlogistics.drawer.holding": "%s. It will hold %s of each.",
    "elysiumlogistics.controller.alone": "No drawers on its bus.",
    "elysiumlogistics.controller.overloaded": "%s drawers on one controller, and it "
                                              "will index %s. Build another.",
    "elysiumlogistics.controller.dark": "%s drawers, and no power reaching it.",
    "elysiumlogistics.controller.running": "%s drawers, drawing %s, moving %s a tick.",
    "elysiumlogistics.assembler.idle": "Nothing to make.",
    "elysiumlogistics.assembler.dark": "Making %s, and no power reaching it.",
    "elysiumlogistics.assembler.working": "Making %s. %s to go, about %s seconds.",
    "elysiumlogistics.core.handless": "No assemblers on its bus. It can plan and it "
                                      "cannot build.",
    "elysiumlogistics.core.waiting": "%s assembler(s), and nothing to make.",
    "elysiumlogistics.core.busy": "Still making %s: %s step(s) to go.",
    "elysiumlogistics.core.nothing": "Hold up what you want made.",
    "elysiumlogistics.core.nothing_to_make": "Nothing here knows how to make %s.",
    "elysiumlogistics.core.refused": "No: %s",
    "elysiumlogistics.core.enormous": "That comes to %s machine-runs, which is more "
                                      "than %s assembler(s) will get through in an "
                                      "hour. Ask for fewer, or build more.",
    "elysiumlogistics.core.started": "Making %s x %s: %s step(s), %s machine-runs.",
    "elysiumlogistics.core.dark": "Making %s, and no power reaching it.",
    "elysiumlogistics.core.running": "Making %s: %s step(s) left, %s machine-runs.",
    "elysiumlogistics.terminal.coreless": "No assembly core on its bus.",
}


def language():
    entries = dict(LINES)
    for name, shown in NAMES.items():
        entries[f"block.{NS}.{name}"] = shown
    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(entries.items())))
    return len(entries)


if __name__ == "__main__":
    sprites = draw_all()
    models = models_and_states()
    data = data_files()
    keys = language()
    print(f"logistics: {sprites} sprite(s), {models} block(s) modelled and stated, "
          f"{data} data file(s), {keys} language key(s)")
