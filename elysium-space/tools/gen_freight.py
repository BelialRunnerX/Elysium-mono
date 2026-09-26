#!/usr/bin/env python3
"""
Everything the freight terminal ships that is a file rather than a decision.

One block, its model, its textures, its drop, its recipe and its words. The
textures are built rather than drawn, the same way the waygate's are: a vanilla
block is the surface and the terminal's own marking is laid over it, so a
terminal reads as a machine somebody bolted a hatch to rather than as a
recolour.
"""
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumspace"
# ELYSIUM_SPACE_OUT redirects what is written (see gen_space.py); the lang
# file and tags merged into are read from that same tree.
RES = os.path.join(os.environ.get("ELYSIUM_SPACE_OUT", MOD), "src/main/resources")
ASSETS = os.path.join(RES, "assets", NS)
DATA = os.path.join(RES, "data", NS)
BASE = os.path.join(HERE, "base")

#: Cargo amber: the colour this project uses for industry rather than for
#: magic, and the same one the colony spaceport's lights are.
AMBER = (0xE8, 0xB4, 0x5C)
DARK = (0x2A, 0x2D, 0x34)


def write(path, body):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if isinstance(body, (dict, list)):
        body = json.dumps(body, indent=2) + "\n"
    open(path, "w", encoding="utf-8", newline="\n").write(body)


def load(name):
    return Image.open(os.path.join(BASE, name)).convert("RGBA")


def shaded(source, factor):
    out = source.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            pixels[x, y] = (int(r * factor), int(g * factor), int(b * factor), a)
    return out


def hazard(source, rows=(13, 14)):
    """
    A hazard stripe along the bottom: the universal mark for "things move here".

    Diagonal, four pixels to a repeat, so a wall of terminals lines up. Drawn
    rather than sampled because no vanilla block has one and it is six lines.
    """
    out = source.copy()
    pixels = out.load()
    for y in rows:
        for x in range(out.width):
            lit = ((x + y) // 2) % 2 == 0
            colour = AMBER if lit else DARK
            r, g, b, a = pixels[x, y]
            pixels[x, y] = (colour[0], colour[1], colour[2], a)
    return out


def panelled(source):
    """A recessed panel with a lit strip: the face you read the terminal by."""
    out = source.copy()
    pixels = out.load()
    for y in range(3, 11):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (3, 10)
            r, g, b, a = pixels[x, y]
            if edge:
                pixels[x, y] = (DARK[0], DARK[1], DARK[2], a)
            else:
                pixels[x, y] = (int(r * 0.72), int(g * 0.74), int(b * 0.8), a)
    for x in range(5, 11):
        pixels[x, 6] = (AMBER[0], AMBER[1], AMBER[2], 255)
    return out


def hatch(source):
    """The top: a shaft the cargo goes up, lit from inside."""
    out = shaded(source, 0.9)
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            away = max(abs(x - 7.5), abs(y - 7.5))
            r, g, b, a = pixels[x, y]
            if away <= 2.5:
                pixels[x, y] = (AMBER[0], AMBER[1], AMBER[2], a)
            elif away <= 4.5:
                pixels[x, y] = (DARK[0], DARK[1], DARK[2], a)
    return out


def textures():
    iron = load("iron_block.png")
    made = {
        "freight_terminal": hazard(panelled(shaded(iron, 0.82))),
        "freight_terminal_top": hatch(iron),
    }
    for name, image in made.items():
        path = os.path.join(ASSETS, "textures/block", name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        image.save(path)
    return len(made)


def block():
    write(os.path.join(ASSETS, "blockstates/freight_terminal.json"),
          {"variants": {"": {"model": f"{NS}:block/freight_terminal"}}})
    write(os.path.join(ASSETS, "models/block/freight_terminal.json"),
          {"parent": "minecraft:block/cube_top",
           "textures": {"top": f"{NS}:block/freight_terminal_top",
                        "side": f"{NS}:block/freight_terminal"}})
    write(os.path.join(ASSETS, "models/item/freight_terminal.json"),
          {"parent": f"{NS}:block/freight_terminal"})
    write(os.path.join(DATA, "loot_table/blocks/freight_terminal.json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:freight_terminal"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
    write(os.path.join(DATA, "recipe/freight_terminal.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["SSS", "SCS", "SSS"],
        "key": {"S": {"item": f"{NS}:steel_ingot"},
                "C": {"item": "minecraft:chest"}},
        "result": {"id": f"{NS}:freight_terminal", "count": 1},
    })
    return 1


WORDS = {
    "block.elysiumspace.freight_terminal": "Freight Terminal",
    "elysiumspace.freight.title": "Freight",
    "elysiumspace.freight.run": "%s",
    "elysiumspace.freight.aboard":
        "%s stack(s) aboard, %s mB of fuel in the tank",
    "elysiumspace.freight.alone":
        "No other terminal yet. Build one somewhere else and click it once.",
    "elysiumspace.freight.status":
        "%s/%s stacks, %s/%s mB fuel, %s away, %s inbound",
    "elysiumspace.freight.placed":
        "Terminal registered. Crouch and use it to choose where its load goes.",
    "elysiumspace.freight.away": "Away to %s: %s seconds, %s mB burned.",
    "elysiumspace.freight.gone": "There is no terminal here any more.",
    "elysiumspace.freight.nowhere": "That terminal no longer exists.",
    "elysiumspace.freight.itself": "A terminal cannot ship to itself.",
    "elysiumspace.freight.nothing": "There is nothing loaded to send.",
    "elysiumspace.freight.busy": "This terminal already has %s rockets in the air.",
    "elysiumspace.freight.unknown": "Nothing in the route table goes there.",
    "elysiumspace.freight.fuel": "That run needs %s mB and the tank holds %s.",
}


def words():
    path = os.path.join(ASSETS, "lang/en_us.json")
    lang = json.load(open(path, encoding="utf-8")) if os.path.isfile(path) else {}
    lang.update(WORDS)
    write(path, dict(sorted(lang.items())))
    return len(WORDS)


def main():
    print(f"textures : {textures()}")
    print(f"block    : {block()} (blockstate, model, item model, drop, recipe)")
    print(f"words    : {words()}")


if __name__ == "__main__":
    main()
