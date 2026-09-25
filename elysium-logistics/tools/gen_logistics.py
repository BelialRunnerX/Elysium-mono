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
import re
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


def language(materials=(), depths=(), tiers=0, bodies=(), parts=(), found=()):
    entries = dict(LINES)
    pack_language(entries, bodies, parts)
    device_language(entries, found)
    for name, shown in NAMES.items():
        entries[f"block.{NS}.{name}"] = shown
    compress_language(entries, materials, depths, tiers)
    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(entries.items())))
    return len(entries)


# ---------------------------------------------------------------------------
# Compression
# ---------------------------------------------------------------------------
#
# Five hundred and thirty-one blocks, and not one new sprite: a compression
# wears the face of the thing it is made of, read out of the game's own model
# for that block, in a frame of the metal that says which tier it is.
#
# The materials, the tiers and the frames are read out of CompressRules rather
# than repeated here, and the count is checked afterwards, because a regex
# that reads fifty of fifty-nine materials writes a smaller catalogue that is
# perfectly valid and nine piles nobody can compress.

COMPRESS_RULES = os.path.join(
    MOD, "src/main/java/com/elysium/logistics/rules/CompressRules.java")
MC_JAR = os.environ.get("MC_JAR", "/tmp/mc/mapped-at.jar")

#: How thick the frame around a compression is, in sixteenths. One: any more
#: and the face of the material it is made of stops being what you see.
FRAME = 1


def compressions():
    """The materials, the tier names and the frame each tier wears."""
    source = open(COMPRESS_RULES, encoding="utf-8").read()

    def listed(name):
        found = re.search(name + r" = List\.of\((.*?)\);", source, re.S)
        if not found:
            print("the compression rules no longer say what " + name + " is")
            sys.exit(1)
        return re.findall(r'"([a-z_:]+)"', found.group(1))

    materials = listed("MATERIALS")
    depths = listed("DEPTHS")
    markers = listed("MARKERS")
    tiers = re.search(r"TIERS = (\d+);", source)
    if not tiers:
        print("the compression rules no longer say how deep compression goes")
        sys.exit(1)
    tiers = int(tiers.group(1))
    if len(depths) != tiers or len(markers) != tiers:
        print(f"the rules go {tiers} tiers deep and this file could read "
              f"{len(depths)} name(s) and {len(markers)} frame(s); a tier would "
              f"be unnamed or two tiers would look the same")
        sys.exit(1)
    if len(materials) < 20:
        print(f"this file could read {len(materials)} materials out of the rules, "
              f"and a catalogue built from a short list is a catalogue with holes")
        sys.exit(1)
    return materials, depths, markers, tiers


def vanilla_face(jar, block):
    """The texture the game itself draws this block with.

    Read rather than typed: quartz wears quartz_block_side, hay wears
    hay_block_side, and a hand-written table of fifty-nine of those has two
    mistakes in it that look like a rendering bug.
    """
    import json as _json
    try:
        model = _json.loads(jar.read(f"assets/minecraft/models/block/{block}.json"))
    except KeyError:
        return None
    textures = model.get("textures", {})
    for key in ("all", "side", "texture", "end", "particle"):
        if key in textures and not textures[key].startswith("#"):
            return textures[key]
    return None


def compress_model(face, frame):
    """A cube of the material, inside a frame of the metal for its tier."""
    inner = FRAME
    outer = 16 - FRAME
    def box(x1, y1, z1, x2, y2, z2, texture):
        return {"from": [x1, y1, z1], "to": [x2, y2, z2],
                "faces": {side: {"texture": texture} for side in
                          ("down", "up", "north", "south", "west", "east")}}
    elements = [box(inner, inner, inner, outer, outer, outer, "#material")]
    # The twelve edges of the block, which is what reads as a frame.
    for y1, y2 in ((0, inner), (outer, 16)):
        elements.append(box(0, y1, 0, 16, y2, inner, "#frame"))
        elements.append(box(0, y1, outer, 16, y2, 16, "#frame"))
        elements.append(box(0, y1, inner, inner, y2, outer, "#frame"))
        elements.append(box(outer, y1, inner, 16, y2, outer, "#frame"))
    for x1, x2 in ((0, inner), (outer, 16)):
        for z1, z2 in ((0, inner), (outer, 16)):
            elements.append(box(x1, inner, z1, x2, outer, z2, "#frame"))
    return {"parent": "block/block",
            "textures": {"material": face, "frame": frame, "particle": face},
            "elements": elements}


def compress_files(materials, depths, markers, tiers):
    """Every compression: model, state, item, drop and the two recipes."""
    import zipfile
    if not os.path.isfile(MC_JAR):
        print("the game's jar is not at " + MC_JAR + ", so the face each "
              "compression wears cannot be read out of it")
        sys.exit(1)
    blocks = data = 0
    ids = []
    with zipfile.ZipFile(MC_JAR) as jar:
        faces = {}
        for material in materials:
            face = vanilla_face(jar, material)
            if not face:
                print(f"the game draws {material} with a texture this file cannot "
                      f"read, so a compression of it would be drawn as nothing")
                sys.exit(1)
            faces[material] = face
        frames = {}
        for marker in markers:
            frame = vanilla_face(jar, marker.split(":", 1)[1])
            if not frame:
                print(f"the game draws {marker} with a texture this file cannot "
                      f"read, so a tier would have no frame")
                sys.exit(1)
            frames[marker] = frame
    for material in materials:
        for tier in range(1, tiers + 1):
            id = depths[tier - 1] + "_" + material
            ids.append(id)
            write(os.path.join(ASSETS, "models/block", id + ".json"),
                  compress_model(faces[material], frames[markers[tier - 1]]))
            write(os.path.join(ASSETS, "blockstates", id + ".json"),
                  {"variants": {"": {"model": f"{NS}:block/{id}"}}})
            write(os.path.join(ASSETS, "models/item", id + ".json"),
                  {"parent": f"{NS}:block/{id}"})
            write(os.path.join(DATA, "loot_table/blocks", id + ".json"),
                  drop_itself(id))
            under = ("minecraft:" + material if tier == 1
                     else f"{NS}:" + depths[tier - 2] + "_" + material)
            # Nine into one, and one back into nine. The pair is the round
            # trip the harness proves, written as data the game will run.
            write(os.path.join(DATA, "recipe", id + ".json"), {
                "type": "minecraft:crafting_shaped", "category": "building",
                "pattern": ["MMM", "MMM", "MMM"], "key": {"M": {"item": under}},
                "result": {"id": f"{NS}:{id}", "count": 1},
            })
            write(os.path.join(DATA, "recipe", id + "_undone.json"), {
                "type": "minecraft:crafting_shapeless", "category": "building",
                "ingredients": [{"item": f"{NS}:{id}"}],
                "result": {"id": under, "count": 9},
            })
            blocks += 1
            data += 3
    tags = os.path.join(OUT, "src/main/resources/data/minecraft/tags/block")
    where = os.path.join(tags, "mineable/pickaxe.json")
    existing = []
    if os.path.isfile(where):
        with open(where, encoding="utf-8") as handle:
            existing = json.load(handle).get("values", [])
    write(where, {"replace": False,
                  "values": sorted(set(existing) | {f"{NS}:{id}" for id in ids})})
    data += 1
    return blocks, data, ids


#: What each material is called on a label. Read from the game where it can
#: be, so that "dried_kelp_block" is not "Dried Kelp Block Compressed".
COMPRESS_WORDS = {
    "dripstone_block": "Dripstone", "bone_block": "Bone", "moss_block": "Moss",
    "snow_block": "Snow", "magma_block": "Magma", "slime_block": "Slime",
    "hay_block": "Hay", "coal_block": "Coal", "iron_block": "Iron",
    "gold_block": "Gold", "redstone_block": "Redstone", "lapis_block": "Lapis",
    "diamond_block": "Diamond", "emerald_block": "Emerald",
    "quartz_block": "Quartz", "amethyst_block": "Amethyst",
    "copper_block": "Copper", "netherite_block": "Netherite",
    "purpur_block": "Purpur", "honeycomb_block": "Honeycomb",
    "dried_kelp_block": "Dried Kelp", "bamboo_block": "Bamboo",
    "sea_lantern": "Sea Lantern", "end_stone": "End Stone",
}


def compress_language(entries, materials, depths, tiers):
    for material in materials:
        shown = COMPRESS_WORDS.get(
            material, " ".join(word.capitalize() for word in material.split("_")))
        for tier in range(1, tiers + 1):
            deep = " ".join(word.capitalize()
                            for word in depths[tier - 1].split("_"))
            entries[f"block.{NS}.{depths[tier - 1]}_{material}"] = \
                f"{deep} {shown}"
    return entries


# ---------------------------------------------------------------------------
# Packs: eight bodies and ten parts
# ---------------------------------------------------------------------------
#
# Item sprites rather than block textures, and drawn here for the same reason
# everything else is: eighteen sprites hand-painted in a folder are eighteen
# things nobody keeps in step with the rules, and the rules are where the
# ladder lives.
#
# A bag gets bigger and its buckles get brighter up the ladder; a crate is a
# box with bands. Every part is a small plate with one glyph on it, and the
# glyph's colour says what kind of part it is — so a row of them in a bag's
# side slots reads at a glance.

PACK_RULES = os.path.join(
    MOD, "src/main/java/com/elysium/logistics/rules/PackRules.java")

#: The five bags, dullest first, and the three crates.
CANVAS = [(62, 48, 32), (98, 78, 52), (138, 112, 76), (176, 150, 108)]
HIDE = [(74, 44, 28), (116, 72, 44), (158, 104, 66), (198, 148, 104)]
WAX = [(92, 62, 30), (140, 100, 46), (188, 146, 70), (226, 194, 122)]
OILCLOTH = [(38, 54, 46), (62, 90, 74), (94, 132, 108), (140, 182, 152)]
STORM = [(34, 44, 62), (58, 76, 104), (92, 118, 156), (142, 172, 208)]

#: One colour per kind of part, so a row of fitted parts reads at a glance.
PART_COLOURS = {
    "space": (128, 196, 112),
    "gather": (142, 172, 208),
    "void": (46, 34, 54),
    "feed": (214, 138, 76),
    "cook": (244, 176, 62),
    "press": (156, 164, 174),
    "restock": (176, 232, 150),
    "sieve": (226, 194, 122),
}

PACK_RAMPS = {
    "satchel": CANVAS, "knapsack": HIDE, "haversack": WAX,
    "rucksack": OILCLOTH, "expedition_pack": STORM,
    "crate": TIMBER, "strongbox": BRASS, "vault": STEEL,
}


def packs():
    """The bodies and the parts, read out of PackRules."""
    source = open(PACK_RULES, encoding="utf-8").read()
    bodies = [(name, int(slots), int(fittings), carried == "true")
              for name, slots, fittings, carried in re.findall(
                  r'new Body\("([a-z_]+)",\s*(\d+),\s*(\d+),\s*(true|false)\)',
                  source)]
    kinds = dict(re.findall(
        r'public static final String (\w+)\s*=\s*"([a-z_]+)";', source))
    parts = []
    for name, kind, slots in re.findall(
            r'new Part\("([a-z_]+)",\s*(\w+),\s*(\d+)\)', source):
        if kind not in kinds:
            print("the pack rules name a kind this file cannot resolve: " + kind)
            sys.exit(1)
        parts.append((name, kinds[kind], int(slots)))
    if len(bodies) != len(re.findall(r"new Body\(", source)) \
            or len(parts) != len(re.findall(r"new Part\(", source)):
        print("the pack rules declare more bodies or parts than this file could "
              "read; one was wrapped onto a second line and those items would "
              "simply not exist")
        sys.exit(1)
    if not bodies or not parts:
        print("this file read no bodies or no parts out of the pack rules")
        sys.exit(1)
    return bodies, parts


def bag_sprite(ramp, straps):
    """A bag: a body, a flap, and a buckle for every part it takes."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 5, 12, 14], fill=rgba(ramp[1]), outline=rgba(ramp[0]))
    draw.rectangle([3, 3, 12, 7], fill=rgba(ramp[2]), outline=rgba(ramp[0]))
    draw.line([(5, 2), (5, 4)], fill=rgba(ramp[0]))
    draw.line([(10, 2), (10, 4)], fill=rgba(ramp[0]))
    for at in range(min(straps, 5)):
        y = 9 + (at % 3) * 2
        x = 4 + (at // 3) * 6
        draw.rectangle([x, y, x + 2, y + 1], fill=rgba(ramp[3]),
                       outline=rgba(ramp[0]))
    return image


def crate_sprite(ramp, bands):
    """A crate: a box with a band for every part it takes."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 3, 13, 14], fill=rgba(ramp[1]), outline=rgba(ramp[0]))
    draw.line([(2, 3), (13, 3)], fill=rgba(ramp[3]))
    for at in range(min(bands, 6)):
        y = 5 + at
        if y > 13:
            break
        draw.line([(3, y), (12, y)], fill=rgba(ramp[2]))
    draw.rectangle([6, 8, 9, 10], fill=rgba(ramp[0]), outline=rgba(ramp[3]))
    return image


def part_sprite(colour, rooms):
    """A part: a plate, a glyph in its kind's colour, and a pip per nine slots."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 2, 13, 13], fill=rgba(STEEL[1]), outline=rgba(STEEL[0]))
    draw.line([(2, 2), (13, 2)], fill=rgba(STEEL[3]))
    draw.rectangle([5, 5, 10, 10], fill=rgba(colour), outline=rgba(STEEL[0]))
    for at in range(min(rooms, 3)):
        draw.point((4 + at * 4, 12), fill=rgba(colour))
    return image


def pack_sprites(bodies, parts):
    where = os.path.join(ASSETS, "textures/item")
    os.makedirs(where, exist_ok=True)
    drawn = 0
    for name, slots, fittings, carried in bodies:
        ramp = PACK_RAMPS.get(name)
        if ramp is None:
            print(f"{name} is a body with no palette here, so it would be drawn "
                  f"as nothing at all")
            sys.exit(1)
        sprite = bag_sprite(ramp, fittings) if carried \
            else crate_sprite(ramp, fittings)
        sprite.save(os.path.join(where, name + ".png"))
        drawn += 1
    for name, kind, slots in parts:
        colour = PART_COLOURS.get(kind)
        if colour is None:
            print(f"{kind} is a kind of part with no colour here, so every part "
                  f"of that kind would be drawn the same as some other kind")
            sys.exit(1)
        part_sprite(colour, slots // 9).save(os.path.join(where, name + ".png"))
        drawn += 1
    return drawn


PACK_WORDS = {
    "satchel": "Satchel", "knapsack": "Knapsack", "haversack": "Haversack",
    "rucksack": "Rucksack", "expedition_pack": "Expedition Pack",
    "crate": "Crate", "strongbox": "Strongbox", "vault": "Vault",
    "small_pocket": "Small Pocket", "large_pocket": "Large Pocket",
    "deep_pocket": "Deep Pocket", "gathering_lens": "Gathering Lens",
    "void_seal": "Void Seal", "ration_tin": "Ration Tin",
    "ember_plate": "Ember Plate", "press_screw": "Press Screw",
    "restocker": "Restocker", "sieve_mesh": "Sieve Mesh",
}

#: What each body and part is made of. A bag is leather and cloth all the way
#: up; what changes is how much of it, which is what makes the ladder a ladder
#: rather than five unrelated recipes.
PACK_RECIPES = {
    "satchel": (["SLS", "L L", "LLL"],
                {"S": {"item": "minecraft:string"},
                 "L": {"item": "minecraft:leather"}}),
    "knapsack": (["SLS", "LBL", "LLL"],
                 {"S": {"item": "minecraft:string"},
                  "L": {"item": "minecraft:leather"},
                  "B": {"item": f"{NS}:satchel"}}),
    "haversack": (["CLC", "LBL", "LLL"],
                  {"C": {"item": "minecraft:copper_ingot"},
                   "L": {"item": "minecraft:leather"},
                   "B": {"item": f"{NS}:knapsack"}}),
    "rucksack": (["ILI", "LBL", "LLL"],
                 {"I": {"item": "minecraft:iron_ingot"},
                  "L": {"item": "minecraft:leather"},
                  "B": {"item": f"{NS}:haversack"}}),
    "expedition_pack": (["GLG", "LBL", "LLL"],
                        {"G": {"item": "minecraft:gold_ingot"},
                         "L": {"item": "minecraft:leather"},
                         "B": {"item": f"{NS}:rucksack"}}),
    "crate": (["PPP", "P P", "PPP"], {"P": {"tag": "minecraft:planks"}}),
    "strongbox": (["IPI", "PBP", "IPI"],
                  {"I": {"item": "minecraft:iron_ingot"},
                   "P": {"tag": "minecraft:planks"},
                   "B": {"item": f"{NS}:crate"}}),
    "vault": (["OIO", "IBI", "OIO"],
              {"O": {"item": "minecraft:obsidian"},
               "I": {"item": "minecraft:iron_ingot"},
               "B": {"item": f"{NS}:strongbox"}}),
    "small_pocket": (["LLL", "LSL", "LLL"],
                     {"L": {"item": "minecraft:leather"},
                      "S": {"item": "minecraft:string"}}),
    "large_pocket": (["LLL", "LPL", "LLL"],
                     {"L": {"item": "minecraft:leather"},
                      "P": {"item": f"{NS}:small_pocket"}}),
    "deep_pocket": (["LLL", "LPL", "LLL"],
                    {"L": {"item": "minecraft:rabbit_hide"},
                     "P": {"item": f"{NS}:large_pocket"}}),
    "gathering_lens": (["III", "IGI", "III"],
                       {"I": {"item": "minecraft:iron_nugget"},
                        "G": {"item": "minecraft:glass"}}),
    "void_seal": (["OOO", "OEO", "OOO"],
                  {"O": {"item": "minecraft:obsidian"},
                   "E": {"item": "minecraft:ender_pearl"}}),
    "ration_tin": (["III", "IBI", "III"],
                   {"I": {"item": "minecraft:iron_nugget"},
                    "B": {"item": "minecraft:bread"}}),
    "ember_plate": (["III", "IFI", "III"],
                    {"I": {"item": "minecraft:iron_nugget"},
                     "F": {"item": "minecraft:furnace"}}),
    "press_screw": (["III", "IPI", "III"],
                    {"I": {"item": "minecraft:iron_nugget"},
                     "P": {"item": "minecraft:piston"}}),
    "restocker": (["III", "IHI", "III"],
                  {"I": {"item": "minecraft:iron_nugget"},
                   "H": {"item": "minecraft:hopper"}}),
    "sieve_mesh": (["III", "ICI", "III"],
                   {"I": {"item": "minecraft:iron_nugget"},
                    "C": {"item": "minecraft:cobweb"}}),
}


def pack_files(bodies, parts):
    """Every pack and part: a model, a recipe and a name."""
    written = 0
    for name, *_rest in list(bodies) + [(p[0],) for p in parts]:
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": "item/generated",
               "textures": {"layer0": f"{NS}:item/{name}"}})
        grid = PACK_RECIPES.get(name)
        if grid is None:
            print(f"{name} has no recipe here, so it is an item only a command "
                  f"can give")
            sys.exit(1)
        pattern, key = grid
        write(os.path.join(DATA, "recipe", name + ".json"),
              {"type": "minecraft:crafting_shaped", "category": "equipment",
               "pattern": pattern, "key": key,
               "result": {"id": f"{NS}:{name}", "count": 1}})
        written += 2
    return written


def pack_language(entries, bodies, parts):
    for name, *_rest in list(bodies) + [(p[0],) for p in parts]:
        shown = PACK_WORDS.get(name)
        if shown is None:
            print(f"{name} has no name here, so it is called by its id")
            sys.exit(1)
        entries[f"item.{NS}.{name}"] = shown
    return entries


# ---------------------------------------------------------------------------
# The devices, and the logic layer's operators
# ---------------------------------------------------------------------------
#
# Seven blocks, each a steel case with one glyph on it in the colour of what
# it does — so a wall of them reads at a glance, which is the entire reason a
# storage network has faces at all.

NET_RULES = os.path.join(
    MOD, "src/main/java/com/elysium/logistics/rules/NetRules.java")

#: One colour per role. Pulling in and pushing out are the same green and the
#: same blue everywhere else in this pack uses for in and out.
ROLE_COLOURS = {
    "import": (128, 196, 112),
    "export": (142, 172, 208),
    "expose": (226, 194, 122),
    "watch": (244, 176, 62),
    "signal": (214, 88, 76),
    "serve": (176, 232, 150),
    "link": (168, 120, 214),
}


def devices():
    """The devices and their roles, read out of NetRules."""
    source = open(NET_RULES, encoding="utf-8").read()
    roles = dict(re.findall(
        r'public static final String (\w+)\s*=\s*"([a-z_]+)";', source))
    found = []
    for name, role, draws in re.findall(
            r'new Device\("([a-z_]+)",\s*(\w+),\s*(\d+)\)', source):
        if role not in roles:
            print("the network rules name a role this file cannot resolve: " + role)
            sys.exit(1)
        found.append((name, roles[role], int(draws)))
    if len(found) != len(re.findall(r"new Device\(", source)):
        print("the network rules declare more devices than this file could read; "
              "one was wrapped onto a second line and it would not exist")
        sys.exit(1)
    if not found:
        print("this file read no devices out of the network rules")
        sys.exit(1)
    return found


def device_face(colour, draws):
    """A steel case, a glyph in the colour of what it does, and a pip per rung."""
    image = carcass(STEEL)
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 3, 12, 12], fill=rgba(STEEL[0]), outline=rgba(STEEL[2]))
    draw.rectangle([5, 5, 10, 10], fill=rgba(colour), outline=rgba(STEEL[0]))
    draw.point((7, 7), fill=rgba(STEEL[3]))
    draw.point((8, 8), fill=rgba(STEEL[3]))
    for at in range(min(draws, 4)):
        draw.point((2 + at * 4, 14), fill=rgba(colour))
    return image


#: What each device is made of. Every one of them is the bus it hangs off plus
#: the thing it does, so a player who can make a bus can make any of them.
DEVICE_RECIPES = {
    "importer": ["minecraft:hopper", "minecraft:iron_ingot"],
    "exporter": ["minecraft:dropper", "minecraft:iron_ingot"],
    "storage_bus": ["minecraft:chest", "minecraft:iron_ingot"],
    "monitor": ["minecraft:glass_pane", "minecraft:iron_ingot"],
    "detector": ["minecraft:comparator", "minecraft:iron_ingot"],
    "interface": ["minecraft:crafting_table", "minecraft:iron_ingot"],
    "ender_link": ["minecraft:ender_pearl", "minecraft:gold_ingot"],
}

DEVICE_WORDS = {
    "importer": "Importer", "exporter": "Exporter",
    "storage_bus": "Storage Bus", "monitor": "Monitor",
    "detector": "Detector", "interface": "Interface",
    "ender_link": "Ender Link",
}


def device_files(found):
    """Every device: a sprite, a model, a state, an item, a drop and a recipe."""
    where = os.path.join(ASSETS, "textures/block")
    os.makedirs(where, exist_ok=True)
    drawn = written = 0
    for name, role, draws in found:
        colour = ROLE_COLOURS.get(role)
        if colour is None:
            print(f"{role} is a role with no colour here, so two devices would be "
                  f"drawn the same and a wall of them would say nothing")
            sys.exit(1)
        device_face(colour, draws).save(os.path.join(where, name + ".png"))
        drawn += 1
        write(os.path.join(ASSETS, "models/block", name + ".json"),
              {"parent": "minecraft:block/cube_all",
               "textures": {"all": f"{NS}:block/{name}"}})
        write(os.path.join(ASSETS, "blockstates", name + ".json"),
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"),
              drop_itself(name))
        parts = DEVICE_RECIPES.get(name)
        if parts is None:
            print(f"{name} has no recipe here, so it is a block only a command "
                  f"can place")
            sys.exit(1)
        write(os.path.join(DATA, "recipe", name + ".json"),
              {"type": "minecraft:crafting_shaped", "category": "misc",
               "pattern": ["MBM", "MCM", "MBM"],
               "key": {"M": {"item": parts[1]},
                       "B": {"item": f"{NS}:bus"},
                       "C": {"item": parts[0]}},
               "result": {"id": f"{NS}:{name}", "count": 1}})
        written += 2
    tags = os.path.join(OUT, "src/main/resources/data/minecraft/tags/block")
    pick = os.path.join(tags, "mineable/pickaxe.json")
    existing = []
    if os.path.isfile(pick):
        with open(pick, encoding="utf-8") as handle:
            existing = json.load(handle).get("values", [])
    write(pick, {"replace": False,
                 "values": sorted(set(existing)
                                  | {f"{NS}:{name}" for name, _r, _d in found})})
    written += 1
    return drawn, written


def device_language(entries, found):
    for name, _role, _draws in found:
        shown = DEVICE_WORDS.get(name)
        if shown is None:
            print(f"{name} has no name here, so it is called by its id")
            sys.exit(1)
        entries[f"block.{NS}.{name}"] = shown
    return entries


if __name__ == "__main__":
    sprites = draw_all()
    models = models_and_states()
    data = data_files()
    materials, depths, markers, tiers = compressions()
    # After data_files(), because that writes the mineable tag wholesale and
    # this merges into what it left.
    squashed, more, _ids = compress_files(materials, depths, markers, tiers)
    data += more
    found = devices()
    devicesprites, more = device_files(found)
    data += more
    bodies, parts = packs()
    packed = pack_sprites(bodies, parts)
    data += pack_files(bodies, parts)
    keys = language(materials, depths, tiers, bodies, parts, found)
    print(f"logistics: {sprites} sprite(s), {models} block(s) modelled and stated, "
          f"{squashed} compression(s), {devicesprites} device(s), {packed} pack "
          f"sprite(s), {data} data file(s), {keys} language key(s)")
