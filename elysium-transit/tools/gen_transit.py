#!/usr/bin/env python3
"""
Every sprite, model and data file elysium-transit ships.

    python3 tools/gen_transit.py

Three blocks and no more: the stone that holds a charge, and the two surfaces
a player lays on purpose. The other two surfaces in the rules are whatever the
world already is — a pack does not need to ship "grass".
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumtransit"

OUT = os.environ.get("ELYSIUM_TRANSIT_OUT", MOD)
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
# A waystone is dark stone with one lit mark on it, and the mark is the pack's
# own accent rather than a colour of this module's — a player should read
# "Elysium put this here" before they read anything else about it.

DEEP = [(26, 26, 34), (44, 44, 58), (64, 64, 82), (92, 92, 114)]
PAVE = [(96, 94, 88), (126, 124, 116), (154, 152, 143), (182, 180, 170)]
GRIT = [(84, 80, 74), (110, 105, 98), (134, 129, 120), (158, 153, 143)]
RUNE = (127, 112, 196)
GLOW = (169, 155, 232)
CLEAR = (0, 0, 0, 0)


def rgba(colour):
    return (colour[0], colour[1], colour[2], 255)


def speckled(ramp, seed, density=52):
    """A stone face: a base, a border and a scatter that is the same twice."""
    image = Image.new("RGBA", (16, 16), rgba(ramp[1]))
    draw = ImageDraw.Draw(image)
    draw.line([(0, 0), (15, 0)], fill=rgba(ramp[3]))
    draw.line([(0, 0), (0, 15)], fill=rgba(ramp[2]))
    draw.line([(0, 15), (15, 15)], fill=rgba(ramp[0]))
    draw.line([(15, 0), (15, 15)], fill=rgba(ramp[0]))
    # A fixed hash rather than random: the sprite has to come out the same on
    # every machine, or the generated-file check fails for everybody but the
    # person who last ran it.
    for y in range(1, 15):
        for x in range(1, 15):
            mix = (x * 37 + y * 101 + seed * 17) % 256
            if mix < density // 2:
                draw.point((x, y), fill=rgba(ramp[0]))
            elif mix < density:
                draw.point((x, y), fill=rgba(ramp[2]))
    return image


def waystone_side():
    """Carved stone with a mark burning in it."""
    image = speckled(DEEP, 3, 40)
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 2, 12, 13], outline=rgba(DEEP[2]))
    draw.line([(8, 4), (8, 11)], fill=rgba(RUNE))
    draw.line([(5, 7), (11, 7)], fill=rgba(RUNE))
    draw.line([(6, 5), (10, 5)], fill=rgba(GLOW))
    draw.point((8, 9), fill=rgba(GLOW))
    return image


def waystone_top():
    image = speckled(DEEP, 7, 40)
    draw = ImageDraw.Draw(image)
    draw.rectangle([4, 4, 11, 11], outline=rgba(DEEP[3]))
    draw.line([(5, 8), (10, 8)], fill=rgba(RUNE))
    draw.line([(8, 5), (8, 10)], fill=rgba(RUNE))
    draw.point((8, 8), fill=rgba(GLOW))
    return image


def paving():
    """Laid on purpose: courses that line up."""
    image = speckled(PAVE, 11, 30)
    draw = ImageDraw.Draw(image)
    for y in (5, 10):
        draw.line([(0, y), (15, y)], fill=rgba(PAVE[0]))
    for x, y0, y1 in ((8, 0, 4), (4, 6, 9), (12, 6, 9), (8, 11, 15)):
        draw.line([(x, y0), (x, y1)], fill=rgba(PAVE[0]))
    return image


def gravel_path():
    """Trodden, not laid."""
    image = speckled(GRIT, 5, 96)
    draw = ImageDraw.Draw(image)
    for x, y in ((3, 4), (9, 3), (12, 7), (5, 11), (11, 12), (7, 8)):
        draw.point((x, y), fill=rgba(GRIT[3]))
    return image


#: The three rungs, and the colour each is painted. Read straight off the
#: vehicles in the rules rather than typed again, so a fourth rung is a line
#: there and a colour here.
RUNGS = ["cart", "truck", "hauler"]
PAINT = {
    "cart": [(72, 48, 30), (106, 72, 44), (140, 100, 62), (176, 134, 88)],
    "truck": [(30, 52, 68), (44, 78, 102), (62, 108, 138), (92, 146, 180)],
    "hauler": [(62, 34, 30), (96, 52, 44), (134, 74, 60), (172, 106, 86)],
}
TYRE = [(20, 20, 24), (34, 34, 40), (52, 52, 60), (74, 74, 84)]


def vehicle_skin(ramp):
    """A 64x64 entity sheet laid out to match VehicleModel's texOffs.

    Four patches and nothing clever: the bed, the cab, the side rail and one
    wheel. Written against the model's own offsets, so a box moved there and
    not here is a box wearing somebody else's paint — which is why the numbers
    are in a comment above each patch rather than in anybody's head.
    """
    image = Image.new("RGBA", (64, 64), CLEAR)
    draw = ImageDraw.Draw(image)

    def plate(x0, y0, x1, y1, colours, ribs=0):
        draw.rectangle([x0, y0, x1, y1], fill=rgba(colours[1]),
                       outline=rgba(colours[0]))
        draw.line([(x0 + 1, y0 + 1), (x1 - 1, y0 + 1)], fill=rgba(colours[3]))
        for rib in range(ribs):
            x = x0 + 2 + rib * 4
            if x < x1 - 1:
                draw.line([(x, y0 + 2), (x, y1 - 1)], fill=rgba(colours[2]))

    plate(0, 0, 61, 27, ramp, ribs=14)          # bed: texOffs(0, 0), 16x3x24
    plate(0, 28, 45, 43, ramp, ribs=8)          # cab: texOffs(0, 28), 14x7x9
    plate(0, 45, 33, 52, ramp, ribs=6)          # rail: texOffs(0, 45), 16x3x1
    plate(48, 28, 63, 43, TYRE)                 # wheel: texOffs(48, 28), 2x6x6
    draw.line([(52, 32), (59, 39)], fill=rgba(TYRE[3]))
    draw.line([(52, 39), (59, 32)], fill=rgba(TYRE[3]))
    return image


def vehicle_item(ramp):
    """A 16x16 side-on silhouette for the item."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 7, 14, 10], fill=rgba(ramp[1]), outline=rgba(ramp[0]))
    draw.rectangle([8, 3, 13, 7], fill=rgba(ramp[2]), outline=rgba(ramp[0]))
    draw.line([(2, 8), (13, 8)], fill=rgba(ramp[3]))
    for x in (4, 11):
        draw.rectangle([x - 1, 11, x + 1, 13], fill=rgba(TYRE[1]),
                       outline=rgba(TYRE[0]))
        draw.point((x, 12), fill=rgba(TYRE[3]))
    return image


SPRITES = {
    "waystone": waystone_side,
    "waystone_top": waystone_top,
    "paving": paving,
    "gravel_path": gravel_path,
}

BLOCKS = ["waystone", "paving", "gravel_path"]


def draw_all():
    folder = os.path.join(ASSETS, "textures/block")
    os.makedirs(folder, exist_ok=True)
    for name, make in SPRITES.items():
        make().save(os.path.join(folder, name + ".png"))
    entities = os.path.join(ASSETS, "textures/entity")
    items = os.path.join(ASSETS, "textures/item")
    os.makedirs(entities, exist_ok=True)
    os.makedirs(items, exist_ok=True)
    for rung in RUNGS:
        vehicle_skin(PAINT[rung]).save(os.path.join(entities, rung + ".png"))
        vehicle_item(PAINT[rung]).save(os.path.join(items, rung + ".png"))
    return len(SPRITES) + 2 * len(RUNGS)


# ---------------------------------------------------------------------------
# Models and blockstates
# ---------------------------------------------------------------------------

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


FACES = {
    "waystone": ("waystone_top", "waystone"),
    "paving": ("paving", "paving"),
    "gravel_path": ("gravel_path", "gravel_path"),
}


def models_and_states():
    for name in BLOCKS:
        top, side = FACES[name]
        write(os.path.join(ASSETS, "models/block", name + ".json"), cube(top, side))
        write(os.path.join(ASSETS, "blockstates", name + ".json"),
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})
    for rung in RUNGS:
        write(os.path.join(ASSETS, "models/item", rung + ".json"),
              {"parent": "item/generated",
               "textures": {"layer0": f"{NS}:item/{rung}"}})
    return len(BLOCKS) + len(RUNGS)


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

IND = "elysiumindustry"

#: A waystone wants a wire in it, because a waystone that never wanted power
#: would be a waystone nobody ever wires — and the whole argument of this
#: module is that wiring one is what makes it stop costing you a minute.
RECIPES = {
    "waystone": shaped(["SSS", "SES", "SWS"], {
        "S": ITEM("minecraft:deepslate_tiles"),
        "E": ITEM("minecraft:ender_pearl"),
        "W": ITEM(f"{IND}:copper_wire")}, "waystone"),
    "paving": shaped(["SGS", "GSG", "SGS"], {
        "S": ITEM("minecraft:stone"), "G": ITEM("minecraft:gravel")}, "paving", 8),
    "gravel_path": shaped(["GG", "DD"], {
        "G": ITEM("minecraft:gravel"), "D": ITEM("minecraft:dirt")},
        "gravel_path", 4),
    # The ladder in recipes as well as in numbers: each rung is the one below
    # it plus the age above it, so a hauler is visibly three vehicles deep.
    "cart": shaped(["I I", "ICI", "I I"], {
        "I": ITEM("minecraft:iron_ingot"), "C": ITEM("minecraft:chest")}, "cart"),
    "truck": shaped(["PMP", "PCP", "P P"], {
        "P": TAG("c:plates/steel"), "M": ITEM(f"{IND}:motor"),
        "C": ITEM(f"{NS}:cart")}, "truck"),
    "hauler": shaped(["PMP", "PTP", "PMP"], {
        "P": TAG("c:plates/steel"), "M": ITEM(f"{IND}:motor"),
        "T": ITEM(f"{NS}:truck")}, "hauler"),
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
    write(os.path.join(tags, "mineable/pickaxe.json"),
          {"replace": False, "values": [f"{NS}:waystone", f"{NS}:paving"]})
    write(os.path.join(tags, "mineable/shovel.json"),
          {"replace": False, "values": [f"{NS}:gravel_path"]})
    written += 2
    return written


# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

NAMES = {
    "waystone": "Waystone",
    "paving": "Paving",
    "gravel_path": "Gravel Path",
}

RIDES = {
    "cart": "Cart",
    "truck": "Truck",
    "hauler": "Hauler",
}

LINES = {
    "itemGroup.elysiumtransit": "Elysium Transit",
    "elysiumtransit.waystone.bound": "Bound: %s.",
    "elysiumtransit.waystone.named": "Named: %s.",
    "elysiumtransit.waystone.charge": "%s of %s. Reaches %s blocks, filling at %s a tick.",
    "elysiumtransit.waystone.title": "Where to?",
    "elysiumtransit.waystone.count": "%s place(s) bound.",
    "elysiumtransit.waystone.alone": "Nowhere else bound yet. Right-click another "
                                     "waystone to remember it.",
    "elysiumtransit.waystone.row": "%s · %s FE",
    "elysiumtransit.waystone.row_wait": "%s · %s FE · ready in %ss",
    "elysiumtransit.waystone.far": "Too far from the waystone.",
    "elysiumtransit.waystone.gone": "There is no waystone there.",
    "elysiumtransit.waystone.unknown": "You have not bound that place.",
    "elysiumtransit.waystone.noworld": "%s is not a world on this server.",
    "elysiumtransit.waystone.vanished": "That waystone has been mined. Forgotten.",
    "elysiumtransit.waystone.poor": "That costs %s and the stone holds %s. About %s "
                                    "seconds, or wire it to a grid.",
    "elysiumtransit.surface.road": "road",
    "elysiumtransit.surface.path": "path",
    "elysiumtransit.surface.ground": "open ground",
    "elysiumtransit.surface.rough": "rough going",
    "elysiumtransit.vehicle.status": "On %s. %s fuel, about %s blocks, %s blocks a "
                                     "second.",
    "elysiumtransit.vehicle.tooltip": "%s blocks/s on a road \u00b7 %s slots \u00b7 %s "
                                      "seat(s) \u00b7 %s blocks a tank",
}


def language():
    entries = dict(LINES)
    for name, shown in NAMES.items():
        entries[f"block.{NS}.{name}"] = shown
    for name, shown in RIDES.items():
        entries[f"item.{NS}.{name}"] = shown
        entries[f"entity.{NS}.{name}"] = shown
    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(entries.items())))
    return len(entries)


if __name__ == "__main__":
    sprites = draw_all()
    models = models_and_states()
    data = data_files()
    keys = language()
    print(f"transit: {sprites} sprite(s), {len(BLOCKS)} block(s) and {len(RUNGS)} "
          f"vehicle(s) modelled, {data} data file(s), {keys} language key(s)")
