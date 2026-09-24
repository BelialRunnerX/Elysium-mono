#!/usr/bin/env python3
"""
Every sprite, model and data file elysium-wilds ships.

    python3 tools/gen_wilds.py

One item so far, and it has a needle, which means it is not one model but a
ring of them: the game picks a frame from the `angle` property, so a compass
with three frames reads as a compass that is broken rather than one that is
coarse. Sixteen is the fewest that reads as turning.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumwilds"

OUT = os.environ.get("ELYSIUM_WILDS_OUT", MOD)
ASSETS = os.path.join(OUT, "src/main/resources/assets", NS)
DATA = os.path.join(OUT, "src/main/resources/data", NS)

RULES = os.path.join(MOD, "src/main/java/com/elysium/wilds/rules/WildRules.java")
CLIENT = os.path.join(MOD, "src/main/java/com/elysium/wilds/client/WildsClient.java")

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


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


RULES_SOURCE = read(RULES)
CLIENT_SOURCE = read(CLIENT)

#: The property the models switch on, read off the Java so a rename there is a
#: failure here rather than a compass that never moves.
ANGLE = re.search(r'"angle"', CLIENT_SOURCE)
if not ANGLE:
    sys.exit("the client no longer registers an 'angle' property, so the models below "
             "would switch on something nothing sets and the needle would never move")

#: Sixteen frames. Eight reads as a compass with a loose needle and thirty-two
#: is thirty-two files nobody can tell apart.
FRAMES = 16

# ---------------------------------------------------------------------------
# The palette
# ---------------------------------------------------------------------------
#
# A brass ring, a dark face and one lit needle in the pack's own accent, so it
# reads as Elysium's before it reads as a compass.

RING = [(74, 58, 32), (122, 96, 52), (166, 134, 74), (206, 176, 106)]
FACE = [(20, 22, 30), (32, 36, 48), (48, 54, 70)]
NEEDLE = (169, 155, 232)
NORTH = (206, 108, 96)
CLEAR = (0, 0, 0, 0)


def rgba(colour):
    return (colour[0], colour[1], colour[2], 255)


def face():
    """The ring and the dial, which never move."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.ellipse([1, 1, 14, 14], fill=rgba(RING[1]), outline=rgba(RING[0]))
    draw.ellipse([2, 2, 13, 13], fill=rgba(RING[2]), outline=rgba(RING[3]))
    draw.ellipse([3, 3, 12, 12], fill=rgba(FACE[1]), outline=rgba(FACE[0]))
    # A speckle on the dial, fixed rather than random so the sprite is the same
    # on every machine and the generated-file check passes for everybody.
    for y in range(4, 12):
        for x in range(4, 12):
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 > 16:
                continue
            if (x * 29 + y * 71) % 256 < 40:
                draw.point((x, y), fill=rgba(FACE[2]))
    return image


def needle(frame):
    """One frame of the needle, drawn by hand rather than rotated."""
    import math
    image = face()
    draw = ImageDraw.Draw(image)
    turn = (frame / FRAMES) * math.tau
    centre = 7.5
    reach = 4.2
    tipx = centre + math.sin(turn) * reach
    tipy = centre - math.cos(turn) * reach
    tailx = centre - math.sin(turn) * (reach * 0.7)
    taily = centre + math.cos(turn) * (reach * 0.7)
    draw.line([(tailx, taily), (centre, centre)], fill=rgba(NEEDLE))
    draw.line([(centre, centre), (tipx, tipy)], fill=rgba(NORTH))
    draw.point((centre, centre), fill=rgba(RING[3]))
    return image


def main():
    sprites = os.path.join(ASSETS, "textures/item")
    os.makedirs(sprites, exist_ok=True)
    for frame in range(FRAMES):
        needle(frame).save(os.path.join(sprites, "biome_compass_%02d.png" % frame))

    # The model the game actually looks up, and the ring of overrides under it.
    # The thresholds are the midpoints between frames so that each frame owns
    # an equal slice; putting them at the frame values themselves biases every
    # reading half a step clockwise, which reads as a compass that is slightly
    # wrong and is the sort of thing nobody can quite name.
    overrides = []
    for frame in range(FRAMES):
        at = (frame - 0.5) / FRAMES
        if at < 0.0:
            at = 0.0
        overrides.append({
            "predicate": {"%s:angle" % NS: round(at, 5)},
            "model": "%s:item/biome_compass_%02d" % (NS, frame),
        })
    write(os.path.join(ASSETS, "models/item/biome_compass.json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "%s:item/biome_compass_00" % NS},
        "overrides": overrides,
    })
    for frame in range(FRAMES):
        write(os.path.join(ASSETS, "models/item/biome_compass_%02d.json" % frame), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "%s:item/biome_compass_%02d" % (NS, frame)},
        })

    # Made out of the game's own compass and a map, because both of those are
    # "where am I" and this is the next question.
    write(os.path.join(DATA, "recipe/biome_compass.json"), {
        "type": "minecraft:crafting_shaped",
        "pattern": [" g ", "gcg", " g "],
        "key": {
            "g": {"item": "minecraft:gold_ingot"},
            "c": {"item": "minecraft:compass"},
        },
        "result": {"id": "%s:biome_compass" % NS, "count": 1},
    })

    write(os.path.join(ASSETS, "lang/en_us.json"), {
        "itemGroup.elysiumwilds": "Elysium Wilds",
        "item.elysiumwilds.biome_compass": "Surveyor's Compass",
        "elysiumwilds.compass.title": "Look for somewhere",
        "elysiumwilds.compass.count": "%s place(s) this world knows about",
        "elysiumwilds.compass.idle": "Pointing at nothing yet",
        "elysiumwilds.compass.holding": "Pointing at %s, around %s, %s",
        "elysiumwilds.compass.found": "%s: around %s, %s — about %s blocks away",
        "elysiumwilds.compass.nowhere": "No %s anywhere the compass can see from here",
        "elysiumwilds.compass.nomatch": "Nothing here matches what you have typed",
    })

    print("elysium-wilds: 1 item, %d needle frame(s), 1 recipe" % FRAMES)


if __name__ == "__main__":
    main()
