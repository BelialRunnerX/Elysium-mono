#!/usr/bin/env python3
"""
Every sprite, model and data file elysium-arcana ships.

    python3 tools/gen_arcana.py

One block. The whole of this module is arithmetic; there is nothing to model.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumarcana"

OUT = os.environ.get("ELYSIUM_ARCANA_OUT", MOD)
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

STONE = [(24, 18, 34), (40, 30, 56), (58, 44, 80), (80, 62, 110)]
LIT = [(96, 52, 168), (139, 82, 214), (176, 128, 240), (214, 186, 252)]
CLEAR = (0, 0, 0, 0)


def rgba(colour):
    return (colour[0], colour[1], colour[2], 255)


def base(seed):
    """A dark stone face with a fixed scatter — the same on every machine."""
    image = Image.new("RGBA", (16, 16), rgba(STONE[1]))
    draw = ImageDraw.Draw(image)
    draw.line([(0, 0), (15, 0)], fill=rgba(STONE[3]))
    draw.line([(0, 0), (0, 15)], fill=rgba(STONE[2]))
    draw.line([(0, 15), (15, 15)], fill=rgba(STONE[0]))
    draw.line([(15, 0), (15, 15)], fill=rgba(STONE[0]))
    for y in range(1, 15):
        for x in range(1, 15):
            mix = (x * 53 + y * 89 + seed * 29) % 256
            if mix < 18:
                draw.point((x, y), fill=rgba(STONE[0]))
            elif mix < 34:
                draw.point((x, y), fill=rgba(STONE[2]))
    return image


def transmuter_side():
    """A seam of lit veins running up the face."""
    image = base(2)
    draw = ImageDraw.Draw(image)
    draw.line([(8, 2), (8, 13)], fill=rgba(LIT[1]))
    for y, reach in ((5, 3), (8, 4), (11, 3)):
        draw.line([(8 - reach, y), (8 + reach, y)], fill=rgba(LIT[0]))
    draw.point((8, 8), fill=rgba(LIT[3]))
    draw.point((8, 5), fill=rgba(LIT[2]))
    draw.point((8, 11), fill=rgba(LIT[2]))
    return image


def transmuter_top():
    """A ring, because that is what you put a thing into."""
    image = base(6)
    draw = ImageDraw.Draw(image)
    draw.ellipse([4, 4, 11, 11], outline=rgba(LIT[1]))
    draw.ellipse([6, 6, 9, 9], fill=rgba(LIT[0]), outline=rgba(LIT[2]))
    draw.point((7, 7), fill=rgba(LIT[3]))
    return image


SPRITES = {
    "transmuter": transmuter_side,
    "transmuter_top": transmuter_top,
}

BLOCKS = ["transmuter"]
FACES = {"transmuter": ("transmuter_top", "transmuter")}


def draw_all():
    folder = os.path.join(ASSETS, "textures/block")
    os.makedirs(folder, exist_ok=True)
    for name, make in SPRITES.items():
        make().save(os.path.join(folder, name + ".png"))
    return len(SPRITES)


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


def models_and_states():
    for name in BLOCKS:
        top, side = FACES[name]
        write(os.path.join(ASSETS, "models/block", name + ".json"), cube(top, side))
        write(os.path.join(ASSETS, "blockstates", name + ".json"),
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})
    return len(BLOCKS)


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
    return {"type": "minecraft:crafting_shaped", "category": "misc",
            "pattern": pattern, "key": key,
            "result": {"id": f"{NS}:{result}", "count": count}}


ITEM = lambda name: {"item": name}

#: Expensive on purpose, and out of the base game on purpose. A transmuter is
#: the last thing a player should build by accident and the first thing they
#: should be able to build without another mod installed.
RECIPES = {
    "transmuter": shaped(["ODO", "DED", "ODO"], {
        "O": ITEM("minecraft:obsidian"),
        "D": ITEM("minecraft:diamond"),
        "E": ITEM("minecraft:nether_star")}, "transmuter"),
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
          {"replace": False, "values": [f"{NS}:{name}" for name in BLOCKS]})
    write(os.path.join(tags, "needs_diamond_tool.json"),
          {"replace": False, "values": [f"{NS}:{name}" for name in BLOCKS]})
    written += 2
    return written


# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

NAMES = {"transmuter": "Transmuter"}

LINES = {
    "itemGroup.elysiumarcana": "Elysium Arcana",
    "elysiumarcana.transmuter.title": "What you know",
    "elysiumarcana.transmuter.motes": "%s motes.",
    "elysiumarcana.transmuter.alone": "You have dissolved nothing yet. Hold something "
                                      "up to it.",
    "elysiumarcana.transmuter.empty": "Nothing dissolved yet. Hold something up to it — "
                                      "it knows the worth of things made from %s "
                                      "recipes.",
    "elysiumarcana.row": "%s motes",
    "elysiumarcana.row_short": "%s motes · %s short",
    "elysiumarcana.dissolve.worthless": "Nothing here knows what a %s is worth.",
    "elysiumarcana.dissolve.altered": "A worn or enchanted %s is not the same thing as a "
                                      "fresh one, and this only prices fresh ones.",
    "elysiumarcana.dissolve.done": "%s x %s → %s motes. You have %s.",
    "elysiumarcana.dissolve.learned": "%s x %s → %s motes, and you can make them "
                                      "now. You have %s.",
    "elysiumarcana.condense.unknown": "You have never dissolved one of those.",
    "elysiumarcana.condense.gone": "Nothing installed makes that any more.",
    "elysiumarcana.condense.poor": "That costs %s and you have %s.",
    "elysiumarcana.condense.done": "%s x %s for %s motes. You have %s.",
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
    print(f"arcana: {sprites} sprite(s), {models} block(s) modelled and stated, "
          f"{data} data file(s), {keys} language key(s)")
