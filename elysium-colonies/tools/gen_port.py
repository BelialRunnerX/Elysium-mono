#!/usr/bin/env python3
"""
Everything the spaceport ships that is a file rather than a decision.

Three blocks, their models, their textures, their drops, their recipes and
their names. The textures are built rather than drawn, the same way the ore
module's and the waygate's are: a vanilla block is the surface and the port's
own colour is laid into the parts of it that are already dark, so the result
reads as worked metal with something running through it rather than as a flat
recolour.

    python3 elysium-colonies/tools/gen_port.py

Idempotent: run it as often as you like.
"""
import json
import os
import re

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumcolonies"
RES = os.path.join(MOD, "src/main/resources")
ASSETS = os.path.join(RES, "assets", NS)
DATA = os.path.join(RES, "data", NS)
BASE = os.path.join(HERE, "base")

#: What mines each block: rules/HarvestRules.java, read rather than retyped.
#: A block in no tool's tag breaks at a fist's pace, and one that asks for the
#: right tool — every part of the elevator does — drops nothing at all.
HARVEST = os.path.join(MOD, "src/main/java/com/elysium/colonies/rules/HarvestRules.java")
TOOLS = ("pickaxe", "axe", "shovel", "hoe")

#: The colour of a working port. Amber rather than Elysium's usual cold blue,
#: because everything else this repository lights up is arcane and a freight
#: terminal is industry: sodium light, loading bays, a thing that runs at
#: night because cargo does not keep office hours.
AMBER = (0xF0, 0xB0, 0x48)

#: The same light, hotter, for the parts that are lit rather than merely warm.
HOT = (0xFF, 0xE0, 0x9C)


def write(path, body):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if isinstance(body, (dict, list)):
        body = json.dumps(body, indent=2) + "\n"
    open(path, "w", encoding="utf-8", newline="\n").write(body)


def load(name):
    return Image.open(os.path.join(BASE, name)).convert("RGBA")


def luminance(pixel):
    return (pixel[0] * 299 + pixel[1] * 587 + pixel[2] * 114) / 255000.0


def veined(source, colour, share, glow):
    """
    A surface with something lit running through it.

    `share` is what fraction of the texture becomes vein rather than an
    absolute brightness, because a fixed threshold either catches the whole of
    a dark block or none of a bright one. Taking a percentile of the source's
    own luminance means the same number works on iron and on deepslate.
    """
    out = source.copy()
    pixels = out.load()
    levels = sorted(luminance(pixels[x, y])
                    for y in range(out.height) for x in range(out.width)
                    if pixels[x, y][3] > 0)
    if not levels:
        return out
    threshold = levels[max(0, min(len(levels) - 1, int(len(levels) * share)))]
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            level = luminance((r, g, b))
            if level > threshold:
                continue
            strength = glow * (1.0 - level / max(1e-6, threshold))
            pixels[x, y] = (
                min(255, int(r + (colour[0] - r) * strength)),
                min(255, int(g + (colour[1] - g) * strength)),
                min(255, int(b + (colour[2] - b) * strength)),
                a,
            )
    return out


def shaded(source, shade):
    """The same surface, darker, so something laid on it reads against it."""
    out = source.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            pixels[x, y] = (int(r * shade), int(g * shade), int(b * shade), a)
    return out


def cabled(source):
    """
    Iron with a cable run down the middle of it and a rail either side.

    Drawn rather than borrowed: vanilla's chain is a hanging ornament with its
    art in one half of the texture, and tiled eighty times up a column it reads
    as a stripe down one edge. What a tether wants is something that lines up
    with itself vertically however many of them are stacked, which means the
    pattern has to repeat on a factor of sixteen. Rungs every four pixels do.
    """
    out = shaded(source, 0.72)
    pixels = out.load()
    dark = (0x2E, 0x31, 0x38)
    steel = (0x8A, 0x92, 0x9E)
    for y in range(out.height):
        # The two guide rails the car runs against.
        for x in (2, 13):
            r, g, b, a = pixels[x, y]
            pixels[x, y] = (steel[0], steel[1], steel[2], a)
        # The cable itself: four pixels wide, with a rung every fourth row.
        rung = y % 4 == 0
        for x in range(6, 10):
            r, g, b, a = pixels[x, y]
            if rung:
                pixels[x, y] = (AMBER[0], AMBER[1], AMBER[2], a)
            elif x in (6, 9):
                pixels[x, y] = (dark[0], dark[1], dark[2], a)
            else:
                pixels[x, y] = (steel[0], steel[1], steel[2], a)
    return out


def lit(source, colour, inset):
    """
    A face with a lit square in the middle of it.

    What the top of a tether block and the top of an anchor have in common: you
    are looking down the shaft, and the shaft is where the light is.
    """
    out = source.copy()
    pixels = out.load()
    for y in range(inset, out.height - inset):
        for x in range(inset, out.width - inset):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            edge = min(x - inset, y - inset,
                       out.width - 1 - inset - x, out.height - 1 - inset - y)
            strength = min(1.0, 0.45 + 0.2 * edge)
            pixels[x, y] = (
                min(255, int(r + (colour[0] - r) * strength)),
                min(255, int(g + (colour[1] - g) * strength)),
                min(255, int(b + (colour[2] - b) * strength)),
                a,
            )
    return out


def bordered(source, band):
    """
    A slab of something heavy, held together by a metal band.

    A flat colour rather than a sample of iron: the band has to read as a band
    at sixteen pixels against a very dark stone, and iron's own mid greys
    disappear into deepslate at that size.
    """
    out = source.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            edge = min(x, y, out.width - 1 - x, out.height - 1 - y)
            if edge > 1:
                continue
            r, g, b, a = pixels[x, y]
            weight = 1.0 if edge == 0 else 0.45
            pixels[x, y] = (
                min(255, int(r + (band[0] - r) * weight)),
                min(255, int(g + (band[1] - g) * weight)),
                min(255, int(b + (band[2] - b) * weight)),
                a,
            )
    return out


def ribbed(source, seam, every=8):
    """
    A cast pier: pale concrete with a shutter seam every few pixels.

    Vertical seams only, and spaced on a factor of sixteen, so a leg
    twenty-four blocks tall lines up with itself the whole way up. A pattern
    that does not tile vertically reads as scaffolding.
    """
    out = source.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            red, green, blue, alpha = pixels[x, y]
            if x % every == 0:
                weight = 0.55
            elif x % every == every - 1:
                weight = 0.2
            else:
                continue
            pixels[x, y] = (
                min(255, int(red + (seam[0] - red) * weight)),
                min(255, int(green + (seam[1] - green) * weight)),
                min(255, int(blue + (seam[2] - blue) * weight)),
                alpha,
            )
    return out


def studded(source, colour, every=4, inset=0):
    """A band with a light every few pixels: what a collar looks like at night."""
    out = source.copy()
    pixels = out.load()
    middle = out.height // 2
    for x in range(out.width):
        if x % every != 1:
            continue
        for y in (middle - 1, middle):
            red, green, blue, alpha = pixels[x, y]
            pixels[x, y] = (colour[0], colour[1], colour[2], alpha)
    return out


def treaded(source, dark):
    """Deck plating: a chequer of raised squares, so a platform reads as one."""
    out = source.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            red, green, blue, alpha = pixels[x, y]
            if (x // 4 + y // 4) % 2:
                continue
            pixels[x, y] = (
                int(red + (dark[0] - red) * 0.35),
                int(green + (dark[1] - green) * 0.35),
                int(blue + (dark[2] - blue) * 0.35),
                alpha,
            )
    return out


def textures():
    iron = load("iron_block.png")
    pale = load("smooth_stone.png")
    quartz = load("quartz_block_side.png")
    lodestone = load("lodestone_side.png")
    lodestone_top = load("lodestone_top.png")
    deepslate = load("polished_deepslate.png")

    made = {
        # The anchor: lodestone's ribbing is already a machine surface, and
        # nearly half of it lights up, because the anchor is the part you are
        # meant to be able to find in the dark.
        "elevator_anchor": veined(lodestone, AMBER, 0.45, 0.9),
        "elevator_anchor_top": lit(veined(lodestone_top, AMBER, 0.35, 0.8), HOT, 5),
        # The tether: iron, darkened so the cable reads against it, with the
        # cable and its rails drawn down the middle.
        "elevator_tether": cabled(iron),
        "elevator_tether_top": lit(shaded(iron, 0.8), AMBER, 5),
        # The counterweight: the densest thing in the build, banded with steel
        # so it reads as held together rather than as a block of rock.
        "elevator_counterweight": bordered(veined(deepslate, AMBER, 0.18, 0.5),
                                           (0x9A, 0xA2, 0xAE)),
        # The legs: pale cast concrete, because the thing in the photograph
        # this was built from is pale cast concrete, and because a flare that
        # was as dark as the machinery would disappear against the ground.
        "elevator_pylon": ribbed(quartz, (0x8C, 0x8E, 0x99)),
        # The rings: darker, and lit, because they are the part you see from
        # the ground at night.
        "elevator_collar": studded(shaded(pale, 0.68), HOT),
        "elevator_deck": treaded(shaded(iron, 0.86), (0x3B, 0x3F, 0x48)),
    }
    for name, image in made.items():
        path = os.path.join(ASSETS, "textures/block", name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        image.save(path)
    return len(made)


def blocks():
    # The anchor: a lit head on a machine body.
    write(os.path.join(ASSETS, "blockstates/elevator_anchor.json"),
          {"variants": {"": {"model": f"{NS}:block/elevator_anchor"}}})
    write(os.path.join(ASSETS, "models/block/elevator_anchor.json"),
          {"parent": "minecraft:block/cube_top",
           "textures": {"top": f"{NS}:block/elevator_anchor_top",
                        "side": f"{NS}:block/elevator_anchor"}})

    # The tether: a column, so a run of it reads as one cable rather than as
    # eighty separate boxes, and so a length laid on its side is a gantry.
    write(os.path.join(ASSETS, "blockstates/elevator_tether.json"),
          {"variants": {
              "axis=y": {"model": f"{NS}:block/elevator_tether"},
              "axis=z": {"model": f"{NS}:block/elevator_tether", "x": 90},
              "axis=x": {"model": f"{NS}:block/elevator_tether", "x": 90, "y": 90}}})
    write(os.path.join(ASSETS, "models/block/elevator_tether.json"),
          {"parent": "minecraft:block/cube_column",
           "textures": {"end": f"{NS}:block/elevator_tether_top",
                        "side": f"{NS}:block/elevator_tether"}})

    for plain in ("elevator_counterweight", "elevator_pylon", "elevator_collar",
                  "elevator_deck"):
        write(os.path.join(ASSETS, f"blockstates/{plain}.json"),
              {"variants": {"": {"model": f"{NS}:block/{plain}"}}})
        write(os.path.join(ASSETS, f"models/block/{plain}.json"),
              {"parent": "minecraft:block/cube_all",
               "textures": {"all": f"{NS}:block/{plain}"}})

    for name in NAMES:
        write(os.path.join(ASSETS, f"models/item/{name}.json"),
              {"parent": f"{NS}:block/{name}"})
        write(os.path.join(DATA, f"loot_table/blocks/{name}.json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
    return len(NAMES)


NAMES = ("elevator_anchor", "elevator_tether", "elevator_counterweight",
         "elevator_pylon", "elevator_collar", "elevator_deck")


def harvest_list(name):
    """HarvestRules.<name>, a List.of of block ids, in order."""
    text = open(HARVEST, encoding="utf-8").read()
    text = re.sub(r"//[^\n]*", " ", re.sub(r"/\*.*?\*/", " ", text, flags=re.S))
    found = re.search(r"List<String> " + name + r" = List\.of\(([^;]*?)\);", text, re.S)
    if not found:
        raise SystemExit(f"HarvestRules has no {name} list to read")
    return re.findall(r'"([a-z_]+)"', found.group(1))


def tags():
    """
    The game's mineable tags, from HarvestRules.

    Every block of the module, the two stakes included: the tag is what sets
    the breaking speed, whether or not the block asks for the tool. One write a
    tag file, from the whole list at once, because a generator that wrote a tag
    in pieces kept only the last piece.
    """
    written = 0
    for tool in TOOLS:
        blocks = harvest_list(tool.upper())
        if not blocks:
            continue
        write(os.path.join(RES, "data/minecraft/tags/block/mineable", tool + ".json"),
              {"replace": False, "values": [f"{NS}:{block}" for block in blocks]})
        written += 1
    return written


def recipes():
    """
    What a spaceport costs.

    About three hundred and forty iron ingots for the shortest legal port, most
    of it in the column. That is the number this whole thing turns on: cheap
    enough that a settled colony with a mine can plan for one, expensive enough
    that nobody builds one by accident, and paid mostly in the boring block you
    have to place eighty times — which is the part that makes it feel like
    infrastructure rather than like a purchase.

    Vanilla ingredients only, deliberately. This module is an add-on to
    somebody else's mod and may be the only piece of Elysium a player has
    installed; a recipe that needed a metal from elysium-core would be a
    spaceport nobody could build.
    """
    write(os.path.join(DATA, "recipe/elevator_tether.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["ICI", "ICI", "ICI"],
        "key": {"I": {"item": "minecraft:iron_ingot"},
                "C": {"item": "minecraft:chain"}},
        "result": {"id": f"{NS}:elevator_tether", "count": 4},
    })
    write(os.path.join(DATA, "recipe/elevator_anchor.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["ICI", "IRI", "III"],
        "key": {"I": {"item": "minecraft:iron_block"},
                "C": {"item": "minecraft:chain"},
                "R": {"item": "minecraft:redstone_block"}},
        "result": {"id": f"{NS}:elevator_anchor", "count": 1},
    })
    write(os.path.join(DATA, "recipe/elevator_counterweight.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["III", "IAI", "III"],
        "key": {"I": {"item": "minecraft:iron_block"},
                "A": {"item": "minecraft:anvil"}},
        "result": {"id": f"{NS}:elevator_counterweight", "count": 1},
    })
    # The flare. Cheap per block and bought two thousand at a time, which is
    # the right shape for a monument: the cost is the building, not the
    # ingredient.
    write(os.path.join(DATA, "recipe/elevator_pylon.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["SIS", "SIS", "SIS"],
        "key": {"S": {"item": "minecraft:smooth_stone"},
                "I": {"item": "minecraft:iron_ingot"}},
        "result": {"id": f"{NS}:elevator_pylon", "count": 12},
    })
    write(os.path.join(DATA, "recipe/elevator_collar.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["IGI", "GTG", "IGI"],
        "key": {"I": {"item": "minecraft:iron_ingot"},
                "G": {"item": "minecraft:glowstone_dust"},
                "T": {"item": f"{NS}:elevator_pylon"}},
        "result": {"id": f"{NS}:elevator_collar", "count": 4},
    })
    write(os.path.join(DATA, "recipe/elevator_deck.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["III", "SSS"],
        "key": {"I": {"item": "minecraft:iron_ingot"},
                "S": {"item": "minecraft:smooth_stone"}},
        "result": {"id": f"{NS}:elevator_deck", "count": 6},
    })
    return 6


#: The port's half of the language file. Merged into whatever is already
#: there rather than written over it, because the rest of the module's
#: messages are not this generator's to own.
WORDS = {
    "block.elysiumcolonies.elevator_anchor": "Elevator Anchor",
    "block.elysiumcolonies.elevator_tether": "Elevator Tether",
    "block.elysiumcolonies.elevator_counterweight": "Elevator Counterweight",
    "block.elysiumcolonies.elevator_pylon": "Elevator Pylon",
    "block.elysiumcolonies.elevator_collar": "Elevator Collar",
    "block.elysiumcolonies.elevator_deck": "Elevator Deck",
    "elysium.colony.port.nowhere":
        "This elevator stands in nobody's colony, so it ships nothing.",
    "elysium.colony.port.short":
        "The tether is %s blocks; a spaceport needs %s.",
    "elysium.colony.port.broken":
        "The tether stops at height %s. A spaceport is one column, unbroken, "
        "with a counterweight on top.",
    "elysium.colony.port.roofed":
        "Something stands over the counterweight. A space elevator needs the sky.",
    "elysium.colony.port.notyours":
        "%s is not your settlement; its port takes cargo from its own people.",
    "elysium.colony.port.none":
        "%s has no working spaceport to sell through.",
    "elysium.colony.port.empty": "You are holding nothing to sell.",
    "elysium.colony.port.sold":
        "Shipped %s x %s for %s. The treasury holds %s.",
    "elysium.colony.port.legs":
        "%s of %s legs are standing; each one braced is another %s items a day.",
    "elysium.colony.port.ready":
        "%s's spaceport: %s blocks of tether, %s items a day, %s in the treasury.",
}


def words():
    path = os.path.join(ASSETS, "lang/en_us.json")
    lang = json.load(open(path, encoding="utf-8")) if os.path.isfile(path) else {}
    lang.update(WORDS)
    write(path, dict(sorted(lang.items())))
    return len(WORDS)


def main():
    print(f"textures  : {textures()}")
    print(f"blocks    : {blocks()} (blockstate, model, item model, drop)")
    print(f"tags      : {tags()} mineable tag(s), from HarvestRules")
    print(f"recipes   : {recipes()}")
    print(f"words     : {words()}")


if __name__ == "__main__":
    main()
