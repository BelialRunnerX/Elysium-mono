#!/usr/bin/env python3
"""
Every file Age I needs that is not Java.

    python3 tools/gen_kinetics.py

Art, models, blockstates, loot, recipes, milling recipes and language, all
written from one place. Generated rather than hand-kept for the usual reason:
seven blocks is twenty-eight JSON files that must agree with each other about
names, and the twenty-eighth is always the one that is wrong.

The sprites are drawn here from shapes rather than recoloured from anything.
Nobody else's pixels are in this repository, and a cog is a circle with teeth
on it, which is a thing a program can draw.
"""
import json
import re
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumkinetics"
ASSETS = os.path.join(MOD, "src/main/resources/assets", NS)
DATA = os.path.join(MOD, "src/main/resources/data", NS)

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
# Four ramps, each four steps. Small on purpose: Age I is timber, brass and
# stone, and a texture set with thirty colours in it looks like six mods.

TIMBER = [(58, 40, 26), (86, 60, 38), (120, 86, 54), (152, 114, 74)]
BRASS = [(74, 56, 22), (122, 94, 38), (176, 138, 58), (214, 181, 96)]
STONE = [(52, 52, 56), (84, 84, 90), (118, 118, 126), (150, 150, 158)]
IRON = [(48, 48, 52), (92, 92, 96), (134, 134, 140), (176, 176, 182)]
CLEAR = (0, 0, 0, 0)


def rgba(colour, alpha=255):
    return (colour[0], colour[1], colour[2], alpha)


def grain(image, ramp, x0, y0, x1, y1, vertical=True):
    """A plank-ish fill: bands of the ramp with a little jitter."""
    draw = ImageDraw.Draw(image)
    span = (x1 - x0) if vertical else (y1 - y0)
    for step in range(span):
        shade = ramp[(step * 7 + (step // 3)) % len(ramp)]
        if vertical:
            draw.line([(x0 + step, y0), (x0 + step, y1 - 1)], fill=rgba(shade))
        else:
            draw.line([(x0, y0 + step), (x1 - 1, y0 + step)], fill=rgba(shade))


def shaft_sprite(ramp):
    """A length of shaft seen from the side: a bar with an edge either side."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    grain(image, ramp, 5, 0, 11, 16)
    draw.line([(5, 0), (5, 15)], fill=rgba(ramp[0]))
    draw.line([(10, 0), (10, 15)], fill=rgba(ramp[3]))
    for y in range(2, 16, 5):
        draw.line([(6, y), (9, y)], fill=rgba(ramp[1]))
    return image


def shaft_end(ramp):
    """The cut end of a shaft: a square of endgrain."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([5, 5, 10, 10], fill=rgba(ramp[2]), outline=rgba(ramp[0]))
    draw.point((7, 7), fill=rgba(ramp[3]))
    draw.point((8, 8), fill=rgba(ramp[1]))
    return image


def cog_sprite(ramp, teeth, radius):
    """A cog face: a disc, a hub, and teeth around the rim."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    centre = 7.5
    draw.ellipse([centre - radius, centre - radius, centre + radius, centre + radius],
                 fill=rgba(ramp[2]), outline=rgba(ramp[0]))
    for index in range(teeth):
        angle = 2 * math.pi * index / teeth
        x = centre + math.cos(angle) * (radius + 0.9)
        y = centre + math.sin(angle) * (radius + 0.9)
        draw.rectangle([round(x) - 0, round(y) - 0, round(x) + 0, round(y) + 0],
                       fill=rgba(ramp[1]))
    draw.ellipse([centre - 2, centre - 2, centre + 2, centre + 2],
                 fill=rgba(ramp[1]), outline=rgba(ramp[0]))
    draw.rectangle([6, 6, 9, 9], fill=rgba(ramp[3]))
    draw.rectangle([7, 7, 8, 8], fill=rgba(ramp[0]))
    return image


def crank_sprite():
    """A handle on a boss: the only block you operate with your hands."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 3, 12, 12], fill=rgba(TIMBER[1]), outline=rgba(TIMBER[0]))
    draw.rectangle([5, 5, 10, 10], fill=rgba(TIMBER[2]))
    draw.rectangle([7, 1, 8, 6], fill=rgba(IRON[2]), outline=rgba(IRON[0]))
    draw.rectangle([9, 1, 12, 2], fill=rgba(TIMBER[3]), outline=rgba(TIMBER[0]))
    return image


def wheel_sprite():
    """A water wheel face: rim, spokes and paddles."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.ellipse([0, 0, 15, 15], outline=rgba(TIMBER[0]), fill=rgba(TIMBER[1]))
    draw.ellipse([1, 1, 14, 14], outline=rgba(TIMBER[2]))
    for index in range(8):
        angle = 2 * math.pi * index / 8
        draw.line([(7.5, 7.5),
                   (7.5 + math.cos(angle) * 7, 7.5 + math.sin(angle) * 7)],
                  fill=rgba(TIMBER[3] if index % 2 else TIMBER[2]))
    draw.ellipse([5, 5, 10, 10], fill=rgba(IRON[1]), outline=rgba(IRON[0]))
    draw.point((7, 7), fill=rgba(IRON[3]))
    return image


def wheel_side():
    """The wheel's edge: the paddles, end on."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    grain(image, TIMBER, 0, 0, 16, 16, vertical=False)
    for x in range(0, 16, 4):
        draw.rectangle([x, 0, x + 1, 15], fill=rgba(TIMBER[0]))
        draw.line([(x + 2, 0), (x + 2, 15)], fill=rgba(TIMBER[3]))
    return image


def gearbox_sprite():
    """A gearbox: a case with a bevel pair showing through the window."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], fill=rgba(IRON[1]), outline=rgba(IRON[0]))
    draw.rectangle([2, 2, 13, 13], fill=rgba(IRON[0]))
    for index in range(6):
        angle = 2 * math.pi * index / 6
        draw.line([(7.5, 7.5), (7.5 + math.cos(angle) * 5, 7.5 + math.sin(angle) * 5)],
                  fill=rgba(BRASS[2]))
    draw.ellipse([5, 5, 10, 10], fill=rgba(BRASS[1]), outline=rgba(BRASS[0]))
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        draw.point((x, y), fill=rgba(IRON[3]))
    return image


def bearing_sprite():
    """The windmill bearing: a brass hub with bolt holes for the sail arms."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], fill=rgba(BRASS[1]), outline=rgba(BRASS[0]))
    draw.ellipse([2, 2, 13, 13], fill=rgba(BRASS[2]), outline=rgba(BRASS[0]))
    for index in range(4):
        angle = 2 * math.pi * index / 4 + math.pi / 4
        draw.ellipse([7.5 + math.cos(angle) * 4 - 1, 7.5 + math.sin(angle) * 4 - 1,
                      7.5 + math.cos(angle) * 4 + 1, 7.5 + math.sin(angle) * 4 + 1],
                     fill=rgba(BRASS[0]))
    draw.ellipse([6, 6, 9, 9], fill=rgba(IRON[2]), outline=rgba(IRON[0]))
    return image


def sail_sprite():
    """Sailcloth on a lattice: what a windmill is mostly made of."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], fill=(214, 208, 190, 255))
    for x in range(0, 16, 5):
        draw.line([(x, 0), (x, 15)], fill=rgba(TIMBER[1]))
    for y in range(0, 16, 5):
        draw.line([(0, y), (15, y)], fill=rgba(TIMBER[1]))
    for x in range(2, 16, 5):
        for y in range(2, 16, 5):
            draw.point((x, y), fill=(188, 180, 160, 255))
    return image


def millstone_top():
    """The grinding face: a stone disc with a feed hole."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], fill=rgba(STONE[1]))
    draw.ellipse([1, 1, 14, 14], fill=rgba(STONE[2]), outline=rgba(STONE[0]))
    for index in range(8):
        angle = 2 * math.pi * index / 8 + 0.3
        draw.line([(7.5 + math.cos(angle) * 3, 7.5 + math.sin(angle) * 3),
                   (7.5 + math.cos(angle) * 6.5, 7.5 + math.sin(angle) * 6.5)],
                  fill=rgba(STONE[0]))
    draw.ellipse([6, 6, 9, 9], fill=rgba(STONE[0]))
    return image


def millstone_side():
    """The stone's rim: courses of rough block."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], fill=rgba(STONE[1]))
    for y in range(0, 16, 5):
        draw.line([(0, y), (15, y)], fill=rgba(STONE[0]))
        for x in range((y // 5 % 2) * 4, 16, 8):
            draw.line([(x, y), (x, min(15, y + 4))], fill=rgba(STONE[0]))
    for x in range(0, 16, 3):
        draw.point((x, (x * 5) % 16), fill=rgba(STONE[3]))
    draw.rectangle([0, 12, 15, 15], fill=rgba(IRON[1]))
    draw.line([(0, 12), (15, 12)], fill=rgba(IRON[0]))
    return image


def press_sprite():
    """A press head: a brass ram in a frame, seen from the side."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], fill=rgba(IRON[1]), outline=rgba(IRON[0]))
    draw.rectangle([2, 1, 13, 4], fill=rgba(BRASS[2]), outline=rgba(BRASS[0]))
    draw.rectangle([6, 5, 9, 10], fill=rgba(IRON[2]), outline=rgba(IRON[0]))
    draw.rectangle([1, 11, 14, 14], fill=rgba(IRON[0]))
    for x in range(2, 14, 3):
        draw.point((x, 12), fill=rgba(IRON[3]))
    return image


def press_top():
    """Looking down on the press: the ram and its four guides."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], fill=rgba(IRON[1]), outline=rgba(IRON[0]))
    draw.rectangle([4, 4, 11, 11], fill=rgba(BRASS[2]), outline=rgba(BRASS[0]))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        draw.ellipse([x - 1, y - 1, x + 1, y + 1], fill=rgba(IRON[0]))
    return image


def saw_sprite():
    """A saw: a toothed blade standing in a timber table."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    grain(image, TIMBER, 0, 0, 16, 16, vertical=False)
    draw.rectangle([0, 10, 15, 15], fill=rgba(TIMBER[0]))
    draw.rectangle([7, 2, 8, 11], fill=rgba(IRON[3]), outline=rgba(IRON[1]))
    for y in range(2, 11, 2):
        draw.point((6, y), fill=rgba(IRON[2]))
        draw.point((9, y + 1), fill=rgba(IRON[2]))
    return image


def saw_top():
    """The blade coming up through the table."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    grain(image, TIMBER, 0, 0, 16, 16, vertical=True)
    draw.rectangle([7, 1, 8, 14], fill=rgba(IRON[0]))
    draw.line([(7, 1), (7, 14)], fill=rgba(IRON[3]))
    return image


def mixer_sprite():
    """A mixer: a brass vat with a stirring shaft down the middle."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], fill=rgba(BRASS[1]), outline=rgba(BRASS[0]))
    draw.rectangle([1, 3, 14, 14], fill=rgba(BRASS[2]))
    for y in (3, 8, 13):
        draw.line([(1, y), (14, y)], fill=rgba(BRASS[0]))
    draw.rectangle([7, 0, 8, 6], fill=rgba(IRON[2]), outline=rgba(IRON[0]))
    return image


def mixer_top():
    """Looking into the vat."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], fill=rgba(BRASS[1]), outline=rgba(BRASS[0]))
    draw.ellipse([2, 2, 13, 13], fill=(58, 70, 88, 255), outline=rgba(BRASS[0]))
    for index in range(3):
        angle = 2 * math.pi * index / 3
        draw.line([(7.5, 7.5), (7.5 + math.cos(angle) * 4, 7.5 + math.sin(angle) * 4)],
                  fill=rgba(IRON[2]))
    draw.ellipse([6, 6, 9, 9], fill=rgba(IRON[1]), outline=rgba(IRON[0]))
    return image


SPRITES = {
    "timber_shaft": lambda: shaft_sprite(TIMBER),
    "timber_shaft_end": lambda: shaft_end(TIMBER),
    "brass_shaft": lambda: shaft_sprite(BRASS),
    "brass_shaft_end": lambda: shaft_end(BRASS),
    "small_cogwheel": lambda: cog_sprite(TIMBER, 8, 4.5),
    "large_cogwheel": lambda: cog_sprite(TIMBER, 12, 6.5),
    "cogwheel_side": lambda: shaft_sprite(TIMBER),
    "hand_crank": crank_sprite,
    "water_wheel": wheel_sprite,
    "water_wheel_side": wheel_side,
    "gearbox": gearbox_sprite,
    "windmill_bearing": bearing_sprite,
    "sail": sail_sprite,
    "press": press_sprite,
    "press_top": press_top,
    "saw": saw_sprite,
    "saw_top": saw_top,
    "mixer": mixer_sprite,
    "mixer_top": mixer_top,
    "millstone_top": millstone_top,
    "millstone_side": millstone_side,
}


def draw_all():
    out = os.path.join(ASSETS, "textures/block")
    items = os.path.join(ASSETS, "textures/item")
    os.makedirs(out, exist_ok=True)
    os.makedirs(items, exist_ok=True)
    for name, make in SPRITES.items():
        make().save(os.path.join(out, name + ".png"))
    drawn = len(SPRITES)
    for engine in ENGINES:
        engine_face(engine, False).save(
            os.path.join(out, engine["id"] + ".png"))
        engine_face(engine, True).save(
            os.path.join(out, engine["id"] + "_top.png"))
        drawn += 2
    refinery_face(False).save(os.path.join(out, REFINERY + ".png"))
    refinery_face(True).save(os.path.join(out, REFINERY + "_top.png"))
    drawn += 2
    dynamo_face(False).save(os.path.join(out, DYNAMO + ".png"))
    dynamo_face(True).save(os.path.join(out, DYNAMO + "_top.png"))
    drawn += 2
    for anchor in ANCHORS:
        anchor_face(anchor, False).save(
            os.path.join(out, anchor["id"] + ".png"))
        anchor_face(anchor, True).save(
            os.path.join(out, anchor["id"] + "_top.png"))
        drawn += 2
    for name in OUR_FUELS:
        fuel_sprite(name).save(os.path.join(items, name + ".png"))
        drawn += 1
    return drawn


# ---------------------------------------------------------------------------
# Models and blockstates
# ---------------------------------------------------------------------------
#
# Every block turns about an axis, so every blockstate is the same three
# entries with the same two rotations. Written once here rather than three
# times each by hand.

AXIS_ROTATION = {"y": {}, "x": {"x": 90, "y": 90}, "z": {"x": 90}}


def axis_blockstate(model):
    variants = {}
    for axis, rotation in AXIS_ROTATION.items():
        entry = {"model": f"{NS}:block/{model}"}
        entry.update(rotation)
        variants[f"axis={axis}"] = entry
    return {"variants": variants}


def pillar(width, texture, end):
    """A square-section beam running up the Y axis."""
    low = (16 - width) / 2
    high = 16 - low
    return {
        "parent": "block/block",
        "textures": {"side": f"{NS}:block/{texture}", "end": f"{NS}:block/{end}",
                     "particle": f"{NS}:block/{texture}"},
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


def disc(thickness, face, rim):
    """A flat wheel on the Y axis, seen face-on from above and below."""
    low = (16 - thickness) / 2
    high = 16 - low
    return {
        "parent": "block/block",
        "textures": {"face": f"{NS}:block/{face}", "rim": f"{NS}:block/{rim}",
                     "particle": f"{NS}:block/{face}"},
        "elements": [{
            "from": [0, low, 0],
            "to": [16, high, 16],
            "faces": {
                "down": {"texture": "#face"},
                "up": {"texture": "#face"},
                "north": {"texture": "#rim"},
                "south": {"texture": "#rim"},
                "west": {"texture": "#rim"},
                "east": {"texture": "#rim"},
            },
        }],
    }


def slab(height, top, side, bottom):
    return {
        "parent": "block/block",
        "textures": {"top": f"{NS}:block/{top}", "side": f"{NS}:block/{side}",
                     "bottom": f"{NS}:block/{bottom}", "particle": f"{NS}:block/{side}"},
        "elements": [{
            "from": [0, 0, 0],
            "to": [16, height, 16],
            "faces": {
                "down": {"texture": "#bottom", "cullface": "down"},
                "up": {"texture": "#top"},
                "north": {"texture": "#side"},
                "south": {"texture": "#side"},
                "west": {"texture": "#side"},
                "east": {"texture": "#side"},
            },
        }],
    }


# ---------------------------------------------------------------------------
# The engines, read out of their own rules
# ---------------------------------------------------------------------------

ENGINE_SOURCE = open(os.path.join(
    MOD, "src/main/java/com/elysium/kinetics/rules/EngineRules.java"),
    encoding="utf-8").read()

ENGINES = [{"id": one[0], "free": float(one[1]), "stall": float(one[2]),
            "takes": int(one[3]), "burning": int(one[4])}
           for one in re.findall(
               r'new Engine\("([a-z_]+)",\s*new KineticRules\.Source\('
               r'([\d.]+)F, ([\d.]+)F\), (\d+), (\d+)\)', ENGINE_SOURCE)]
FUELS = [{"id": one[0], "burn": int(one[1].replace("_", "")), "made": one[2]}
         for one in re.findall(
             r'new Fuel\("([a-z:_]+)", ([\d_]+), "([a-z ]*)"\)', ENGINE_SOURCE)]
REFINERY = re.search(r'REFINERY = "([a-z_]+)"', ENGINE_SOURCE)
REFINING_TICKS = re.search(r'REFINING_TICKS = (\d+);', ENGINE_SOURCE)
if not ENGINES or not FUELS or not REFINERY or not REFINING_TICKS:
    print(f"EngineRules no longer has the shape this reads: {len(ENGINES)} "
          f"engine(s), {len(FUELS)} fuel(s)")
    sys.exit(2)
REFINERY = REFINERY.group(1)
REFINING_TICKS = int(REFINING_TICKS.group(1))
OUR_FUELS = [one["id"].split(":", 1)[1] for one in FUELS
             if one["id"].startswith(NS + ":")]

# The seam: the one block in this age that makes electricity. Its name is read
# out of the block rather than typed here, for the same reason every other name
# in this file is.
DYNAMO = re.search(r'String NAME = "([a-z_]+)";', open(os.path.join(
    MOD, "src/main/java/com/elysium/kinetics/block/DynamoBlock.java"),
    encoding="utf-8").read())
if not DYNAMO:
    print("DynamoBlock no longer says what it is called")
    sys.exit(2)
DYNAMO = DYNAMO.group(1)

# The anchors: the machines whose product is that the world keeps running.
ANCHOR_SOURCE = open(os.path.join(
    MOD, "src/main/java/com/elysium/kinetics/rules/AnchorRules.java"),
    encoding="utf-8").read()
_WIDEST = re.search(r"int WIDEST = (\d+);", ANCHOR_SOURCE)
if not _WIDEST:
    print("AnchorRules no longer says how wide an anchor may be")
    sys.exit(2)
WIDEST = int(_WIDEST.group(1))
ANCHORS = [{"id": one[0],
            "widest": WIDEST if one[1] == "WIDEST" else int(one[1]),
            "made": one[2].lower()}
           for one in re.findall(
               r'new Anchor\("([a-z_]+)", (WIDEST|\d+), '
               r"KineticRules\.Shaft\.([A-Z]+)\)",
               re.sub(r"\s+", " ", ANCHOR_SOURCE))]
if not ANCHORS:
    print("AnchorRules no longer has the shape this reads")
    sys.exit(2)


def engine_face(engine, top):
    """
    An engine, drawn from how hard it pushes.

    <p>Hotter the stronger it is, and with one more pipe on the side for each
    rung, so three engines on a wall are three engines rather than one block
    in three colours.
    """
    rung = ENGINES.index(engine)
    body = (98 + rung * 34, 84 + rung * 12, 72)
    dark = tuple(max(0, part - 44) for part in body)
    hot = (232, 150 - rung * 30, 60)
    image = Image.new("RGBA", (16, 16), body + (255,))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], outline=dark + (255,))
    if top:
        draw.ellipse([4, 4, 11, 11], fill=dark + (255,))
        draw.ellipse([6, 6, 9, 9], fill=hot + (255,))
        return image
    for at in range(rung + 1):
        draw.rectangle([2, 3 + at * 4, 13, 4 + at * 4], fill=dark + (255,))
    draw.rectangle([5, 11, 10, 14], fill=hot + (255,), outline=dark + (255,))
    return image


def refinery_face(top):
    """A still: a vessel with a pipe off the top of it."""
    body = (120, 116, 108)
    dark = tuple(max(0, part - 44) for part in body)
    glass = (168, 196, 188)
    image = Image.new("RGBA", (16, 16), body + (255,))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], outline=dark + (255,))
    if top:
        draw.ellipse([3, 3, 12, 12], fill=glass + (255,), outline=dark + (255,))
        draw.ellipse([7, 7, 8, 8], fill=dark + (255,))
        return image
    draw.rectangle([4, 5, 11, 13], fill=glass + (255,), outline=dark + (255,))
    draw.rectangle([10, 1, 12, 5], fill=dark + (255,))
    return image


def dynamo_face(top):
    """
    A dynamo: a coil of copper round a core, with a terminal on the side.

    The one block in this age that is about electricity, so it is the one
    block drawn in copper rather than in brass or timber — a player scanning
    a machine hall should be able to find it without reading anything.
    """
    body = (104, 100, 96)
    dark = tuple(max(0, part - 44) for part in body)
    copper = (192, 118, 78)
    spark = (238, 214, 120)
    image = Image.new("RGBA", (16, 16), body + (255,))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], outline=dark + (255,))
    if top:
        draw.ellipse([2, 2, 13, 13], fill=copper + (255,), outline=dark + (255,))
        draw.ellipse([6, 6, 9, 9], fill=dark + (255,))
        return image
    for at in range(3):
        draw.rectangle([2, 2 + at * 4, 13, 4 + at * 4], fill=copper + (255,),
                       outline=dark + (255,))
    draw.rectangle([11, 13, 13, 14], fill=spark + (255,))
    return image


def anchor_face(anchor, top):
    """
    An anchor: a ring with a weight hanging in it, wider the more it holds.

    Drawn from what it is made of, because the second one is made of a later
    age's metal and a player should be able to see that before they try to
    run it on a brass shaft.
    """
    metal = {"brass": (162, 130, 72), "steel": (150, 154, 160),
             "timber": (124, 96, 62), "ascendant": (198, 176, 222)}
    body = metal.get(anchor["made"], (140, 140, 140))
    dark = tuple(max(0, part - 48) for part in body)
    glow = (118, 196, 214)
    image = Image.new("RGBA", (16, 16), body + (255,))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], outline=dark + (255,))
    inset = 4 - anchor["widest"]
    if top:
        draw.ellipse([inset, inset, 15 - inset, 15 - inset],
                     outline=glow + (255,), width=2)
        draw.ellipse([7, 7, 8, 8], fill=glow + (255,))
        return image
    draw.rectangle([inset, 3, 15 - inset, 12], outline=dark + (255,))
    for at in range(anchor["widest"] + 1):
        draw.rectangle([6, 5 + at * 3, 9, 6 + at * 3], fill=glow + (255,))
    return image


def fuel_sprite(name):
    """A fuel, darker and oilier the further up the ladder it is."""
    rung = OUR_FUELS.index(name)
    body = (68 - rung * 20, 54 - rung * 18, 44 - rung * 14)
    shine = tuple(min(255, part + 90 + rung * 30) for part in body)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.ellipse([3, 4, 12, 13], fill=body + (255,),
                 outline=tuple(max(0, c - 20) for c in body) + (255,))
    draw.ellipse([5, 6, 8, 8], fill=shine + (255,))
    return image


BLOCKS = {
    "timber_shaft": pillar(6, "timber_shaft", "timber_shaft_end"),
    "brass_shaft": pillar(6, "brass_shaft", "brass_shaft_end"),
    "small_cogwheel": disc(8, "small_cogwheel", "cogwheel_side"),
    "large_cogwheel": disc(8, "large_cogwheel", "cogwheel_side"),
    "hand_crank": pillar(10, "hand_crank", "timber_shaft_end"),
    "water_wheel": disc(12, "water_wheel", "water_wheel_side"),
    "millstone": slab(12, "millstone_top", "millstone_side", "millstone_side"),
    "gearbox": pillar(16, "gearbox", "gearbox"),
    "windmill_bearing": pillar(12, "windmill_bearing", "brass_shaft_end"),
    "sail": {
        "parent": "block/cube_all",
        "textures": {"all": f"{NS}:block/sail"},
    },
    "press": slab(14, "press_top", "press", "press_top"),
    "saw": slab(14, "saw_top", "saw", "saw_top"),
    "mixer": slab(16, "mixer_top", "mixer", "mixer_top"),
}
for _engine in ENGINES:
    BLOCKS[_engine["id"]] = slab(16, _engine["id"] + "_top",
                                 _engine["id"], _engine["id"] + "_top")
BLOCKS[REFINERY] = slab(16, REFINERY + "_top", REFINERY, REFINERY + "_top")
BLOCKS[DYNAMO] = slab(14, DYNAMO + "_top", DYNAMO, DYNAMO + "_top")
for _anchor in ANCHORS:
    BLOCKS[_anchor["id"]] = pillar(16, _anchor["id"], _anchor["id"] + "_top")

#: Which blocks a placement can lay on any axis. A millstone cannot.
#:
#: The dynamo is not among them: it takes the axis it is placed against, like
#: a cogwheel, because a dynamo is bolted onto the end of whatever shaft
#: happens to be there and a fixed one would need a gearbox to reach.
FIXED = {"millstone", "sail", "press", "mixer", REFINERY}


ANCHOR_IDS = {one["id"]: one for one in ANCHORS}


def anchor_blockstate(name):
    """Every axis crossed with every width the block will hold.

    One model for all of them: a block that is drawn differently at every
    width is three models to draw and three to keep in step, and the width is
    already the thing the block says out loud when you prod it.
    """
    widest = ANCHOR_IDS[name]["widest"]
    variants = {}
    for axis in ("x", "y", "z"):
        for radius in range(widest + 1):
            variants[f"axis={axis},radius={radius}"] = \
                axis_blockstate(name)["variants"][f"axis={axis}"]
    return {"variants": variants}


def models_and_states():
    for name, model in BLOCKS.items():
        write(os.path.join(ASSETS, "models/block", name + ".json"), model)
        if name in ANCHOR_IDS:
            write(os.path.join(ASSETS, "blockstates", name + ".json"),
                  anchor_blockstate(name))
        elif name in FIXED:
            write(os.path.join(ASSETS, "blockstates", name + ".json"),
                  {"variants": {"axis=x": {"model": f"{NS}:block/{name}"},
                                "axis=y": {"model": f"{NS}:block/{name}"},
                                "axis=z": {"model": f"{NS}:block/{name}"}}})
        else:
            write(os.path.join(ASSETS, "blockstates", name + ".json"), axis_blockstate(name))
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})
    return len(BLOCKS)


# ---------------------------------------------------------------------------
# Loot and recipes
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


RECIPES = {
    "timber_shaft": {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["SSS"],
        "key": {"S": {"item": "minecraft:stick"}},
        "result": {"id": f"{NS}:timber_shaft", "count": 4},
    },
    "brass_shaft": {
        "type": "minecraft:crafting_shapeless",
        "category": "building",
        "ingredients": [{"item": f"{NS}:timber_shaft"},
                        {"item": "minecraft:copper_ingot"},
                        {"item": "minecraft:gold_nugget"}],
        "result": {"id": f"{NS}:brass_shaft", "count": 2},
    },
    "small_cogwheel": {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": [" P ", "PSP", " P "],
        "key": {"P": {"tag": "minecraft:planks"}, "S": {"item": f"{NS}:timber_shaft"}},
        "result": {"id": f"{NS}:small_cogwheel", "count": 1},
    },
    "large_cogwheel": {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["PPP", "PCP", "PPP"],
        "key": {"P": {"tag": "minecraft:planks"}, "C": {"item": f"{NS}:small_cogwheel"}},
        "result": {"id": f"{NS}:large_cogwheel", "count": 1},
    },
    "hand_crank": {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["TI", "PS"],
        "key": {"T": {"item": "minecraft:stick"}, "I": {"item": "minecraft:iron_nugget"},
                "P": {"tag": "minecraft:planks"}, "S": {"item": f"{NS}:timber_shaft"}},
        "result": {"id": f"{NS}:hand_crank", "count": 1},
    },
    "water_wheel": {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["PPP", "PSP", "PPP"],
        "key": {"P": {"tag": "minecraft:planks"}, "S": {"item": f"{NS}:brass_shaft"}},
        "result": {"id": f"{NS}:water_wheel", "count": 1},
    },
    "gearbox": {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": [" C ", "CIC", " C "],
        "key": {"C": {"item": f"{NS}:small_cogwheel"},
                "I": {"item": "minecraft:iron_ingot"}},
        "result": {"id": f"{NS}:gearbox", "count": 1},
    },
    "windmill_bearing": {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["BBB", "BSB", "BBB"],
        "key": {"B": {"item": "minecraft:copper_ingot"},
                "S": {"item": f"{NS}:brass_shaft"}},
        "result": {"id": f"{NS}:windmill_bearing", "count": 1},
    },
    "sail": {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["SWS", "WWW", "SWS"],
        "key": {"S": {"item": "minecraft:stick"}, "W": {"tag": "minecraft:wool"}},
        "result": {"id": f"{NS}:sail", "count": 4},
    },
    "press": {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["III", "BSB", "III"],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "B": {"item": f"{NS}:brass_shaft"},
                "S": {"item": f"{NS}:gearbox"}},
        "result": {"id": f"{NS}:press", "count": 1},
    },
    "saw": {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": [" I ", "PSP", "PPP"],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "P": {"tag": "minecraft:planks"},
                "S": {"item": f"{NS}:timber_shaft"}},
        "result": {"id": f"{NS}:saw", "count": 1},
    },
    "mixer": {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["CSC", "CBC", "III"],
        "key": {"C": {"item": "minecraft:copper_ingot"}, "S": {"item": f"{NS}:brass_shaft"},
                "B": {"item": "minecraft:cauldron"}, "I": {"item": "minecraft:iron_ingot"}},
        "result": {"id": f"{NS}:mixer", "count": 1},
    },
    "millstone": {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["CCC", "CSC", "III"],
        "key": {"C": {"item": "minecraft:cobblestone"}, "S": {"item": f"{NS}:timber_shaft"},
                "I": {"item": "minecraft:iron_nugget"}},
        "result": {"id": f"{NS}:millstone", "count": 1},
    },
}

# ---------------------------------------------------------------------------
# What each machine does, read out of the rules
# ---------------------------------------------------------------------------
#
# This was a set of Python dictionaries, which is to say a set of numbers no
# harness had ever executed. It is now ProcessRules, because a recipe roster
# spanning two modules is a graph and a graph is wrong in ways nobody sees by
# reading a row of it — so the rules hold it, the harness proves no loop in it
# multiplies, and this file writes out what they say.

PROCESS_SOURCE = open(os.path.join(
    MOD, "src/main/java/com/elysium/kinetics/rules/ProcessRules.java"),
    encoding="utf-8").read()
_flat = re.sub(r"\s+", " ", PROCESS_SOURCE)

_FARM = re.search(r'String FARM = "([a-z]+)"', PROCESS_SOURCE)
_SEEDED = re.search(r"List<String> SEEDED = List\.of\(([^;]*?)\);", _flat)
if not _FARM or not _SEEDED:
    print("ProcessRules no longer says what it joins onto")
    sys.exit(2)
FARM = _FARM.group(1)
SEEDED = re.findall(r'"([a-z_]+)"', _SEEDED.group(1))

MACHINE_OF = {"mill": "milling", "press": "pressing",
              "saw": "sawing", "stir": "mixing"}


def _id(text):
    """One of the rules' two ways of writing an id, as the id."""
    text = text.strip()
    if text.startswith("FARM"):
        return FARM + ":" + text.split('"')[-2].lstrip(":")
    return text.strip('"')


STEPS = []
for _one in re.finditer(
        r'(mill|press|saw|stir)\("([a-z_]+)", '
        r'(FARM \+ ":[a-z_]+"|"[^"]*"), (FARM \+ ":[a-z_]+"|"[^"]*"), '
        r"(\d+), (\d+), (SLOW|STEADY|FAST)\)", _flat):
    STEPS.append({"machine": MACHINE_OF[_one.group(1)], "name": _one.group(2),
                  "input": _id(_one.group(3)), "output": _id(_one.group(4)),
                  "count": int(_one.group(5)), "ticks": int(_one.group(6)),
                  "pace": _one.group(7).lower()})
# The seed supply is built by a loop over SEEDED rather than written out, for
# the same reason every roster in this project is walked rather than listed.
for _crop in SEEDED:
    STEPS.append({"machine": "milling", "name": _crop + "_seeds",
                  "input": f"{FARM}:{_crop}", "output": f"{FARM}:{_crop}_seeds",
                  "count": 1, "ticks": 140, "pace": "slow"})
if len(STEPS) < 20:
    print(f"only {len(STEPS)} recipe(s) could be read out of ProcessRules, "
          f"which is not the roster")
    sys.exit(2)


def data_files():
    for name in BLOCKS:
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"), drop_itself(name))
    for name, recipe in RECIPES.items():
        write(os.path.join(DATA, "recipe", name + ".json"), recipe)
    machines = 0
    for step in STEPS:
        body = {
            "type": f"{NS}:{step['machine']}",
            # A leading hash is a tag; anything else is one item.
            "input": {"tag": step["input"][1:]} if step["input"].startswith("#")
            else {"item": step["input"]},
            "result": {"id": step["output"], "count": step["count"]},
            "ticks": step["ticks"],
            "pace": step["pace"],
        }
        # A recipe that joins onto another module carries a condition naming
        # it, so Age I is still a jar you can install on its own: without the
        # farm these recipes are simply not there, rather than being there and
        # naming an item that does not exist.
        joins = sorted({part.split(":", 1)[0].lstrip("#")
                        for part in (step["input"], step["output"])
                        if not part.lstrip("#").startswith(("minecraft:", NS + ":"))})
        if joins:
            body["neoforge:conditions"] = [
                {"type": "neoforge:mod_loaded", "modid": one} for one in joins]
        write(os.path.join(DATA, "recipe", step["machine"],
                           step["name"] + ".json"), body)
        machines += 1
    # The fuels, which are items rather than blocks and so want a model of
    # the flat kind rather than a blockstate.
    for name in OUR_FUELS:
        write(os.path.join(ASSETS, "models/item", name + ".json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"{NS}:item/{name}"},
        })
        machines += 1

    # The engines, the refinery and the fuels.
    for at, engine in enumerate(ENGINES):
        metal = ["minecraft:iron_ingot", "minecraft:iron_block",
                 "minecraft:netherite_scrap"][min(2, at)]
        write(os.path.join(DATA, "recipe", engine["id"] + ".json"), {
            "type": "minecraft:crafting_shaped",
            "category": "building",
            "pattern": ["MMM", "MFM", "MSM"],
            "key": {"M": {"item": metal},
                    "F": {"item": "minecraft:furnace"},
                    "S": {"item": f"{NS}:brass_shaft"}},
            "result": {"id": f"{NS}:{engine['id']}", "count": 1},
        })
        machines += 1
    write(os.path.join(DATA, "recipe", REFINERY + ".json"), {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["IGI", "ICI", "ISI"],
        "key": {"I": {"item": "minecraft:iron_ingot"},
                "G": {"item": "minecraft:glass"},
                "C": {"item": "minecraft:cauldron"},
                "S": {"item": f"{NS}:brass_shaft"}},
        "result": {"id": f"{NS}:{REFINERY}", "count": 1},
    })
    machines += 1

    # The dynamo. Copper and a cogwheel, because what it is is a coil and a
    # thing that turns, and both of those are Age I items a player already has
    # by the time they want one.
    write(os.path.join(DATA, "recipe", DYNAMO + ".json"), {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["CCC", "CGC", "IBI"],
        "key": {"C": {"item": "minecraft:copper_ingot"},
                "G": {"item": f"{NS}:small_cogwheel"},
                "I": {"item": "minecraft:iron_ingot"},
                "B": {"item": f"{NS}:brass_shaft"}},
        "result": {"id": f"{NS}:{DYNAMO}", "count": 1},
    })
    machines += 1

    # The anchors. The first is brass and an ender pearl — the pearl is the
    # game's own way of saying "somewhere else is still there" — and the
    # second is the first one built up in steel, which is the metal it has to
    # be made of to survive the speed it needs.
    for at, anchor in enumerate(ANCHORS):
        if at == 0:
            body = {
                "type": "minecraft:crafting_shaped",
                "category": "building",
                "pattern": ["BEB", "ESE", "BEB"],
                "key": {"B": {"item": "minecraft:copper_ingot"},
                        "E": {"item": "minecraft:ender_pearl"},
                        "S": {"item": f"{NS}:brass_shaft"}},
                "result": {"id": f"{NS}:{anchor['id']}", "count": 1},
            }
        else:
            body = {
                "type": "minecraft:crafting_shaped",
                "category": "building",
                "pattern": ["IEI", "IAI", "IEI"],
                "key": {"I": {"item": "minecraft:iron_block"},
                        "E": {"item": "minecraft:ender_eye"},
                        "A": {"item": f"{NS}:{ANCHORS[at - 1]['id']}"}},
                "result": {"id": f"{NS}:{anchor['id']}", "count": 1},
            }
        write(os.path.join(DATA, "recipe", anchor["id"] + ".json"), body)
        machines += 1

    # One refining recipe per step up the ladder that this module makes.
    #
    # Walked from the ladder rather than written out, so a fuel added to it is
    # a fuel that can be made the same day — and a fuel of ours with no way up
    # to it is an item nobody can ever hold.
    for at, fuel in enumerate(FUELS):
        if not fuel["id"].startswith(NS + ":") or at == 0:
            continue
        under = FUELS[at - 1]
        name = fuel["id"].split(":", 1)[1]
        write(os.path.join(DATA, "recipe", "refining", name + ".json"), {
            "type": f"{NS}:refining",
            "input": {"item": under["id"]},
            "result": {"id": fuel["id"], "count": 1},
            "ticks": REFINING_TICKS,
            "pace": "STEADY",
        })
        machines += 1

    # The tags that make a shaft choppable and a millstone mineable.
    tags = os.path.join(MOD, "src/main/resources/data/minecraft/tags/block")
    axe = [f"{NS}:{n}" for n in
           ("timber_shaft", "small_cogwheel", "large_cogwheel", "hand_crank",
            "water_wheel", "sail", "saw")]
    pick = [f"{NS}:{n}" for n in ("brass_shaft", "millstone", "gearbox",
                                  "windmill_bearing", "press", "mixer")]
    pick += [f"{NS}:{one['id']}" for one in ENGINES]
    pick.append(f"{NS}:{REFINERY}")
    pick.append(f"{NS}:{DYNAMO}")
    pick += [f"{NS}:{one['id']}" for one in ANCHORS]
    write(os.path.join(tags, "mineable/axe.json"), {"replace": False, "values": axe})
    write(os.path.join(tags, "mineable/pickaxe.json"), {"replace": False, "values": pick})
    return len(BLOCKS) + len(RECIPES) + machines + 2


# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

NAMES = {
    "timber_shaft": "Timber Shaft",
    "brass_shaft": "Brass Shaft",
    "small_cogwheel": "Small Cogwheel",
    "large_cogwheel": "Large Cogwheel",
    "hand_crank": "Hand Crank",
    "water_wheel": "Water Wheel",
    "millstone": "Millstone",
    "gearbox": "Gearbox",
    "windmill_bearing": "Windmill Bearing",
    "sail": "Sail",
    "press": "Press",
    "saw": "Saw",
    "mixer": "Mixer",
}

LINES = {
    "itemGroup.elysiumkinetics": "Elysium Kinetics",
    "elysiumkinetics.line.reading": "%s rpm, %s of what the line can push.",
    "elysiumkinetics.line.stalled": "Stalled: the machines outweigh everything driving them.",
    "elysiumkinetics.line.jammed": "Jammed: two gear paths disagree about how fast this turns.",
    "elysiumkinetics.line.sheared": "Too fast for the shaft: %s rpm against a rating of %s.",
    "elysiumkinetics.line.waiting": "Turning, but %s rpm short of what the slowest machine needs.",
    "elysiumkinetics.line.still": "Nothing on this line is driving it.",
    "elysiumkinetics.windmill.bare": "No sails on it. Build the sail out from the "
                                     "bearing, in the plane it turns in.",
    "elysiumkinetics.windmill.sails": "%s sail(s), pulling %s.",
}


#: What each machine says, in its own words. Four lines each, because "it is
#: turning too slowly" and "nothing is turning it" have to read differently or
#: the second looks like the first.
SAYINGS = {
    "millstone": ("Nothing is turning it.", "Turning at %s rpm; it needs %s to grind.",
                  "Turning at %s rpm, with nothing on it.", "Grinding, at %s rpm."),
    "press": ("Nothing is driving it.", "Turning at %s rpm; it needs %s to press.",
              "Turning at %s rpm, with nothing under it.", "Pressing, at %s rpm."),
    "saw": ("The blade is still.", "Turning at %s rpm; it needs %s to cut.",
            "Turning at %s rpm, with nothing on the table.", "Cutting, at %s rpm."),
    "mixer": ("Nothing is stirring it.", "Turning at %s rpm; it needs %s to mix.",
              "Turning at %s rpm, with an empty vat.", "Mixing, at %s rpm."),
}


def language():
    entries = dict(LINES)
    for machine, (still, slow, empty, working) in SAYINGS.items():
        entries[f"{NS}.{machine}.still"] = still
        entries[f"{NS}.{machine}.slow"] = slow
        entries[f"{NS}.{machine}.empty"] = empty
        entries[f"{NS}.{machine}.working"] = working
    for name, shown in NAMES.items():
        entries[f"block.{NS}.{name}"] = shown
    for engine in ENGINES:
        entries[f"block.{NS}.{engine['id']}"] = " ".join(
            word.capitalize() for word in engine["id"].split("_"))
    entries[f"block.{NS}.{REFINERY}"] = REFINERY.capitalize()
    for name in OUR_FUELS:
        entries[f"item.{NS}.{name}"] = name.capitalize()
    entries[f"{NS}.engine.empty"] = "Nothing in the hopper."
    entries[f"{NS}.engine.wrongfuel"] = \
        "This engine will not burn that; it wants something further up the ladder."
    entries[f"{NS}.engine.burning"] = "Burning: %s second(s) left, at %s rpm."
    entries[f"{NS}.refinery.still"] = "Nothing is turning it."
    entries[f"{NS}.refinery.slow"] = "Turning at %s rpm; it needs %s to distil."
    entries[f"{NS}.refinery.empty"] = "Turning at %s rpm, with an empty vessel."
    entries[f"{NS}.refinery.working"] = "Distilling, at %s rpm."
    entries[f"block.{NS}.{DYNAMO}"] = DYNAMO.capitalize()
    entries[f"{NS}.{DYNAMO}.still"] = "Nothing is turning it."
    entries[f"{NS}.{DYNAMO}.slow"] = \
        "Turning at %s rpm; it needs %s before it makes anything. Gear it up."
    entries[f"{NS}.{DYNAMO}.making"] = "%s FE/t. Holding %s of %s."
    for anchor in ANCHORS:
        entries[f"block.{NS}.{anchor['id']}"] = " ".join(
            word.capitalize() for word in anchor["id"].split("_"))
    entries[f"{NS}.anchor.holding"] = "Holding a %s square: %s chunk(s) awake."
    entries[f"{NS}.anchor.waiting"] = \
        "Set to a %s square, which needs %s rpm. This is turning at %s. " \
        "Crouch and prod to change the width."
    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(entries.items())))
    return len(entries)


if __name__ == "__main__":
    sprites = draw_all()
    models = models_and_states()
    data = data_files()
    keys = language()
    print(f"kinetics: {sprites} sprite(s), {models} block(s) modelled and stated, "
          f"{data} data file(s), {keys} language key(s)")
