#!/usr/bin/env python3
"""
Every file that grows something and is not Java.

    python3 tools/gen_agronomy.py

The essence ladder is five tiers, eight seeds and sixteen recipes that all have
to agree with each other about which rung a material sits on. That is exactly
the shape of thing nobody keeps straight by hand, so the table is here and
everything else is read off it — and validate.py regenerates the whole set into
a temporary tree and compares it byte for byte.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumagronomy"
IND = "elysiumindustry"

OUT = os.environ.get("ELYSIUM_AGRONOMY_OUT", MOD)
ASSETS = os.path.join(OUT, "src/main/resources/assets", NS)
DATA = os.path.join(OUT, "src/main/resources/data", NS)

try:
    from PIL import Image, ImageDraw
except ImportError:
    print("Pillow is needed to draw the sprites: pip install pillow")
    sys.exit(1)

# The other generators' block lists, for the mineable tags below: the same
# tables read once, rather than a second reading of every rules file here.
sys.path.insert(0, HERE)
from gen_bees import BEES, HOMES, HOUSINGS, CENTRIFUGE            # noqa: E402
from gen_fish import MOUNTS, TACKLE_BLOCKS                        # noqa: E402
from gen_kitchen import WOODS, CRATED, STATIONS, CROPS, COLONY    # noqa: E402
from gen_pots import POTS, pot_id, OURS as SOILS                  # noqa: E402
from gen_veins import VEINS                                       # noqa: E402


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(obj, handle, indent=2)
        handle.write("\n")


# ---------------------------------------------------------------------------
# The ladder, which everything else is read off
# ---------------------------------------------------------------------------
#
# Kept in step with AgronomyItems.SEEDS by the validator, because a seed whose
# tier differs between the Java and the recipes is a seed that takes one length
# of time to grow and costs another to make.

TIERS = 5
ESSENCE_PER_STEP = 4
ESSENCE_PER_INGOT = 8

ESSENCES = ["faint_essence", "bright_essence", "keen_essence",
            "radiant_essence", "ascendant_essence"]

#: material -> (tier, what one makes when you have enough essence)
#
# Read out of SeedRules.java rather than written here. It was written here, and
# in AgronomyItems.java as well, and three hand-written copies of thirty rows
# is three chances for a seed to take one length of time to grow and cost
# another to make.
SEED_RULES = os.path.join(
    MOD, "src/main/java/com/elysium/agronomy/rules/SeedRules.java")
with open(SEED_RULES, encoding="utf-8") as _handle:
    _seed_source = _handle.read()
SEEDS = {}
for _material, _tier, _makes in re.findall(
        r'mined\("([a-z_]+)", (\d+), "([^"]+)"\)', _seed_source):
    SEEDS[_material] = (int(_tier), _makes)
if not SEEDS:
    print("no seeds could be read out of SeedRules.java")
    sys.exit(2)

#: What a first-rung seed is paid in, where every rung above pays its own
#: essence (SeedRules.FIRST_RUNG_PRICE and paidInEssence). A pot grows a rung's
#: essence only out of a seed of that rung, so a first rung paid in faint
#: essence was a ladder nobody could step onto.
FIRST_RUNG_PRICE = re.search(r'FIRST_RUNG_PRICE = "([a-z_:]+)";', _seed_source)
if not FIRST_RUNG_PRICE:
    print("SeedRules no longer says what the first rung is paid in")
    sys.exit(2)
FIRST_RUNG_PRICE = FIRST_RUNG_PRICE.group(1)

#: Materials whose essence turns into a chunk this module makes itself, because
#: a recipe result has to be a concrete item and the foundry sits above us.
CHUNKED = sorted(material for material, (tier, makes) in SEEDS.items()
                 if makes.startswith("elysiumagronomy:"))

BLOCKS = ["pot", "accelerator", "hive",
          "rock_generator", "driven_rock_generator", "industrial_rock_generator"]

# ---------------------------------------------------------------------------
# The altar, read out of the rules rather than listed here
# ---------------------------------------------------------------------------

def _source(name):
    with open(os.path.join(MOD, "src/main/java/com/elysium/agronomy/rules", name),
              encoding="utf-8") as handle:
        return handle.read()


ALTAR_RULES = _source("AltarRules.java")


def altar_word(name):
    found = re.search(name + r'\s*=\s*"([a-z_]+)"', ALTAR_RULES)
    if not found:
        print(f"AltarRules no longer says what {name} is")
        sys.exit(2)
    return found.group(1)


CENTRE = altar_word("CENTRE")
PEDESTAL = altar_word("PEDESTAL")
RELAY = altar_word("RELAY")
INSANIUM = altar_word("INSANIUM")
TOP_ESSENCE = altar_word("TOP_ESSENCE")
ALTAR_BLOCKS = [CENTRE, PEDESTAL, RELAY]

PER_INSANIUM = re.search(r"PER_INSANIUM = AgronomyRules\.ESSENCE_PER_STEP",
                         ALTAR_RULES)
if not PER_INSANIUM:
    print("the sixth rung no longer costs what a rung of the ladder costs, and "
          "this generator writes the recipe that says it does")
    sys.exit(2)
PER_INSANIUM = int(re.search(r"ESSENCE_PER_STEP = (\d+);",
                             _source("AgronomyRules.java")).group(1))

#: How many steps of growth a pot's blockstate carries.
AGES = 4

# ---------------------------------------------------------------------------
# What mines them, read out of HarvestRules
# ---------------------------------------------------------------------------
#
# A block that asks for the right tool and is in no mineable tag drops
# nothing, and 459 of the module's 465 were in none (29 September 2026). The
# four tags are written here, once each, for every block of the module — the
# other generators' blocks included, which is why their lists are imported
# above. A tag file written twice keeps only the second write, which is how
# the space module once lost two thirds of its pickaxe tag.

HARVEST_RULES = _source("HarvestRules.java")
_harvest = " ".join(re.sub(r"//[^\n]*", " ",
                           re.sub(r"/\*.*?\*/", " ", HARVEST_RULES, flags=re.S)).split())
_harvest = re.sub(r'"\s*\+\s*"', "", _harvest)
HARVEST_WORDS = dict(re.findall(r'public static final String ([A-Z_]+) = "([a-z_]+)";',
                                _harvest))
_tools = re.search(r"TOOLS = List\.of\(([^;]*?)\);", _harvest)
HARVEST_TOOLS = ([HARVEST_WORDS.get(word, word) for word in re.findall(r"[A-Z_]+", _tools.group(1))]
                 if _tools else [])
BY_HAND = HARVEST_WORDS.get("BY_HAND", "")

#: material id -> the tool that mines it, or BY_HAND
MATERIAL_TOOL = {}
for _material, _built, _tool, _why in re.findall(
        r'new Material\(([A-Z_]+), List\.of\(([^)]*)\), ([A-Z_]+), "((?:[^"\\]|\\.)*)"\)',
        _harvest):
    MATERIAL_TOOL[HARVEST_WORDS.get(_material, _material)] = HARVEST_WORDS.get(_tool, _tool)

#: (kind name, material id), in the order HarvestRules.KINDS lists them
HARVEST_KINDS = [(name, HARVEST_WORDS.get(material, material)) for name, material
                 in re.findall(r'new Kind\("([a-z_*]+)", ([A-Z_]+)\)', _harvest)]

if not HARVEST_TOOLS or not BY_HAND or not MATERIAL_TOOL or not HARVEST_KINDS:
    print("HarvestRules.java no longer has the shape this reads, so no mineable tag "
          "can be written")
    sys.exit(2)


def harvest_tool(block):
    """HarvestRules.tool(): the kind by exact name first, then by suffix."""
    for name, material in HARVEST_KINDS:
        if name == block:
            return MATERIAL_TOOL.get(material)
    for name, material in HARVEST_KINDS:
        if name.startswith("*") and block.endswith(name[1:]) and len(block) > len(name) - 1:
            return MATERIAL_TOOL.get(material)
    return None


def every_block():
    """Every block the module registers, once each, from the tables that register them."""
    ids = list(BLOCKS) + ALTAR_BLOCKS
    ids += [housing["id"] for housing in HOUSINGS]
    ids += [CENTRIFUGE] + [vein["material"] + "_generator" for vein in VEINS]
    ids += TACKLE_BLOCKS + MOUNTS
    ids += [bee["id"] + "_comb_block" for bee in BEES] + [home + "_nest" for home in HOMES]
    ids += list(STATIONS.values())
    ids += [wood + "_cabinet" for wood in WOODS] + [crop + "_crate" for crop in CRATED]
    ids += [crop["id"] + "_crop" for crop in CROPS] + [COLONY]
    ids += list(SOILS) + [pot_id(material, fitting) for material, fitting in POTS]
    return list(dict.fromkeys(ids))


def harvest_tags():
    """The mineable tags, one file per tool, each written exactly once."""
    tags = os.path.join(OUT, "src/main/resources/data/minecraft/tags/block/mineable")
    listed = {tool: [] for tool in HARVEST_TOOLS}
    for block in every_block():
        tool = harvest_tool(block)
        if tool is None:
            print(f"HarvestRules has no kind for {block}, so nothing says what mines it")
            sys.exit(2)
        if tool in listed:
            listed[tool].append(f"{NS}:{block}")
    written = 0
    for tool, values in listed.items():
        if values:
            write(os.path.join(tags, tool + ".json"),
                  {"replace": False, "values": sorted(values)})
            written += 1
    return written


# ---------------------------------------------------------------------------
# The palette
# ---------------------------------------------------------------------------

CLAY = [(78, 52, 38), (116, 78, 56), (150, 104, 74), (184, 138, 104)]
STEEL = [(38, 41, 46), (72, 78, 86), (112, 120, 130), (156, 164, 174)]
TIMBER = [(58, 40, 26), (86, 60, 38), (120, 86, 54), (152, 114, 74)]
STONE = [(52, 52, 56), (84, 84, 90), (118, 118, 126), (150, 150, 158)]
LEAF = [(30, 62, 28), (48, 100, 42), (78, 146, 60), (128, 192, 96)]
HONEY = [(122, 78, 14), (176, 122, 26), (222, 168, 48), (248, 214, 114)]

#: One colour per rung, so an essence's tier is its colour and a player never
#: has to read a tooltip to know which pile is which.
RUNGS = [
    [(58, 60, 66), (96, 100, 108), (140, 146, 156), (186, 192, 202)],
    [(28, 72, 96), (42, 112, 148), (70, 162, 200), (140, 214, 236)],
    [(30, 86, 46), (46, 130, 68), (76, 182, 100), (146, 224, 162)],
    [(98, 62, 16), (150, 100, 28), (206, 148, 50), (244, 200, 108)],
    [(84, 30, 96), (128, 48, 146), (176, 84, 196), (222, 154, 238)],
    # The sixth. Not a rung of the seed ladder — there is no tier-six seed,
    # because a seed may only grow what the ground gives you — but the rung
    # an altar makes out of the fifth, and it wants to look like one.
    [(120, 18, 26), (176, 34, 44), (224, 76, 84), (255, 158, 162)],
]
CLEAR = (0, 0, 0, 0)


def rgba(colour, alpha=255):
    return (colour[0], colour[1], colour[2], alpha)


def slab(ramp):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], fill=rgba(ramp[1]))
    draw.line([(0, 0), (15, 0)], fill=rgba(ramp[2]))
    draw.line([(0, 15), (15, 15)], fill=rgba(ramp[0]))
    return image


def pot_side():
    image = slab(CLAY)
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 3, 14, 5], fill=rgba(CLAY[2]), outline=rgba(CLAY[0]))
    draw.rectangle([2, 7, 13, 14], fill=rgba(CLAY[1]), outline=rgba(CLAY[0]))
    return image


def pot_top(step):
    """Soil, and how far through the growth it is."""
    image = slab(CLAY)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 2, 13, 13], fill=rgba((58, 42, 30)), outline=rgba(CLAY[0]))
    if step > 0:
        reach = 1 + step
        for x in (5, 8, 11):
            draw.line([(x, 12), (x, 12 - reach * 2)], fill=rgba(LEAF[1 + step % 3]))
            draw.point((x, 12 - reach * 2), fill=rgba(LEAF[3]))
    return image


def machine_side(ramp, accent):
    image = slab(ramp)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 2, 13, 13], fill=rgba(ramp[0]), outline=rgba(ramp[2]))
    draw.rectangle([4, 5, 11, 10], fill=rgba(accent[1]), outline=rgba(accent[0]))
    draw.line([(5, 7), (10, 7)], fill=rgba(accent[3]))
    return image


def machine_top(ramp, accent):
    image = slab(ramp)
    draw = ImageDraw.Draw(image)
    draw.ellipse([4, 4, 11, 11], fill=rgba(accent[1]), outline=rgba(accent[0]))
    draw.ellipse([6, 6, 9, 9], fill=rgba(accent[3]))
    return image


def hive_side():
    image = slab(TIMBER)
    draw = ImageDraw.Draw(image)
    for y in (3, 7, 11):
        draw.rectangle([2, y, 13, y + 2], fill=rgba(HONEY[1]), outline=rgba(TIMBER[0]))
    return image


def hive_top():
    image = slab(TIMBER)
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 3, 12, 12], fill=rgba(HONEY[2]), outline=rgba(TIMBER[0]))
    for x, y in ((6, 6), (9, 6), (6, 9), (9, 9)):
        draw.point((x, y), fill=rgba(HONEY[0]))
    return image


def rock_side(rung):
    ramp = STONE if rung == 0 else STEEL
    image = slab(ramp)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 2, 13, 13], fill=rgba(ramp[0]), outline=rgba(ramp[2]))
    for index in range(rung + 1):
        draw.rectangle([4, 4 + index * 3, 11, 5 + index * 3], fill=rgba(STONE[2]))
    return image


def essence(rung):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    ramp = RUNGS[rung]
    draw.polygon([(8, 2), (13, 8), (8, 14), (3, 8)], fill=rgba(ramp[1]),
                 outline=rgba(ramp[0]))
    draw.polygon([(8, 4), (11, 8), (8, 12), (5, 8)], fill=rgba(ramp[2]))
    draw.point((8, 7), fill=rgba(ramp[3]))
    return image


def seed(rung):
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    ramp = RUNGS[rung]
    draw.polygon([(5, 11), (4, 7), (8, 4), (12, 7), (11, 11)], fill=rgba((104, 84, 44)),
                 outline=rgba((62, 48, 24)))
    draw.polygon([(7, 9), (8, 6), (9, 9)], fill=rgba(ramp[2]))
    draw.point((8, 8), fill=rgba(ramp[3]))
    return image


def bee():
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.ellipse([4, 6, 11, 11], fill=rgba(HONEY[2]), outline=rgba((40, 32, 12)))
    for x in (6, 9):
        draw.line([(x, 6), (x, 11)], fill=rgba((40, 32, 12)))
    draw.polygon([(4, 5), (7, 3), (7, 6)], fill=rgba((214, 230, 238)))
    draw.polygon([(11, 5), (8, 3), (8, 6)], fill=rgba((214, 230, 238)))
    return image


def comb():
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 4, 12, 12], fill=rgba(HONEY[1]), outline=rgba(HONEY[0]))
    for y in (6, 9):
        for x in (5, 8, 11):
            draw.point((x, y), fill=rgba(HONEY[3]))
    return image


def wax():
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([4, 5, 11, 11], fill=rgba(HONEY[3]), outline=rgba(HONEY[1]))
    draw.line([(5, 7), (10, 7)], fill=rgba(HONEY[2]))
    return image


BLOCK_SPRITES = {
    "pot_side": pot_side,
    "accelerator_side": lambda: machine_side(STEEL, RUNGS[1]),
    "accelerator_top": lambda: machine_top(STEEL, RUNGS[1]),
    "hive_side": hive_side,
    "hive_top": hive_top,
}
for step in range(AGES):
    BLOCK_SPRITES[f"pot_top_{step}"] = (lambda s=step: pot_top(s))
for rung in range(3):
    BLOCK_SPRITES[f"rock_side_{rung}"] = (lambda r=rung: rock_side(r))
BLOCK_SPRITES["rock_top"] = lambda: machine_top(STONE, STONE)


def altar_face(kind, top):
    """
    A block of the altar, drawn from what it is for.

    <p>Stone, with a groove that says how far out it stands: the centre has a
    full ring, a pedestal a short one, a relay a line. Three blocks a player
    lays out in a pattern want to be told apart across a room.
    """
    from PIL import Image, ImageDraw
    base = (126, 124, 118)
    cut = (86, 84, 80)
    lit = (196, 186, 150) if kind == CENTRE else (150, 146, 138)
    image = Image.new("RGBA", (16, 16), base + (255,))
    draw = ImageDraw.Draw(image)
    for row in (0, 15):
        draw.line([(0, row), (15, row)], fill=cut + (255,))
    for column in (0, 15):
        draw.line([(column, 0), (column, 15)], fill=cut + (255,))
    if not top:
        draw.rectangle([3, 5, 12, 10], fill=cut + (255,))
        return image
    if kind == CENTRE:
        draw.ellipse([2, 2, 13, 13], outline=lit + (255,))
        draw.ellipse([5, 5, 10, 10], fill=lit + (255,))
    elif kind == PEDESTAL:
        draw.rectangle([4, 4, 11, 11], outline=lit + (255,))
        draw.rectangle([6, 6, 9, 9], fill=lit + (255,))
    else:
        draw.line([(3, 8), (12, 8)], fill=lit + (255,))
        draw.line([(8, 3), (8, 12)], fill=lit + (255,))
    return image


for _kind in ALTAR_BLOCKS:
    BLOCK_SPRITES[f"{_kind}_top"] = (lambda k=_kind: altar_face(k, True))
    BLOCK_SPRITES[f"{_kind}_side"] = (lambda k=_kind: altar_face(k, False))

ITEM_SPRITES = {"bee": bee, "comb": comb, "wax": wax}
for rung, name in enumerate(ESSENCES):
    ITEM_SPRITES[name] = (lambda r=rung: essence(r))
for material, (tier, _) in SEEDS.items():
    ITEM_SPRITES[material + "_seed"] = (lambda t=tier: seed(t - 1))
# The sixth rung, drawn one step past the fifth so the ladder reads as a
# ladder, and an awakened seed for every ordinary one.
ITEM_SPRITES[INSANIUM] = lambda: essence(len(ESSENCES))
for material, (tier, _) in SEEDS.items():
    ITEM_SPRITES[f"awakened_{material}_seed"] = (lambda t=tier: seed(t - 1))


#: The seventeen chunks, drawn from the material's own name so two never look
#: the same and a new material needs no new artwork.
def chunk(material):
    value = 0
    for char in material:
        value = (value * 31 + ord(char)) & 0xFFFFFFFF
    red = 90 + (value % 130)
    green = 90 + ((value // 7) % 130)
    blue = 90 + ((value // 53) % 130)
    core = (red, green, blue)
    edge = tuple(max(0, channel - 52) for channel in core)
    shine = tuple(min(255, channel + 48) for channel in core)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.polygon([(4, 11), (3, 7), (6, 4), (11, 5), (12, 10), (8, 13)],
                 fill=core + (255,), outline=edge + (255,))
    draw.line([(6, 7), (9, 6)], fill=shine + (255,))
    draw.point((7, 9), fill=shine + (255,))
    return image


def food_book():
    """A bound book with a leaf in it."""
    cover = (118, 74, 54)
    spine = (86, 52, 38)
    page = (232, 226, 206)
    leaf = (108, 158, 72)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 2, 12, 13], fill=cover + (255,))
    draw.rectangle([3, 2, 4, 13], fill=spine + (255,))
    draw.rectangle([6, 4, 11, 11], fill=page + (255,))
    draw.line([(7, 6), (10, 6)], fill=(168, 160, 140, 255))
    draw.line([(7, 8), (10, 8)], fill=(168, 160, 140, 255))
    draw.polygon([(8, 9), (11, 10), (8, 12)], fill=leaf + (255,))
    return image


def draw_all():
    blocks = os.path.join(ASSETS, "textures/block")
    items = os.path.join(ASSETS, "textures/item")
    os.makedirs(blocks, exist_ok=True)
    os.makedirs(items, exist_ok=True)
    for name, make in BLOCK_SPRITES.items():
        make().save(os.path.join(blocks, name + ".png"))
    for name, make in ITEM_SPRITES.items():
        make().save(os.path.join(items, name + ".png"))
    for material in CHUNKED:
        chunk(material).save(os.path.join(items, material + "_chunk.png"))
    food_book().save(os.path.join(items, "food_book.png"))
    return len(BLOCK_SPRITES) + len(ITEM_SPRITES) + len(CHUNKED)


# ---------------------------------------------------------------------------
# Models and blockstates
# ---------------------------------------------------------------------------

#: The pot's shape, which has to match PotBlock's collision box exactly.
POT_BOX = (2, 0, 2, 14, 12, 14)


def box_model(box, top, side, bottom=None):
    return {
        "parent": "block/block",
        "textures": {"top": f"{NS}:block/{top}", "side": f"{NS}:block/{side}",
                     "bottom": f"{NS}:block/{bottom or side}",
                     "particle": f"{NS}:block/{side}"},
        "elements": [{
            "from": [box[0], box[1], box[2]],
            "to": [box[3], box[4], box[5]],
            "faces": {
                "down": {"texture": "#bottom"},
                "up": {"texture": "#top"},
                "north": {"texture": "#side"},
                "south": {"texture": "#side"},
                "west": {"texture": "#side"},
                "east": {"texture": "#side"},
            },
        }],
    }


FULL = (0, 0, 0, 16, 16, 16)


def models_and_states():
    written = 0
    for step in range(AGES):
        write(os.path.join(ASSETS, "models/block", f"pot_{step}.json"),
              box_model(POT_BOX, f"pot_top_{step}", "pot_side", "pot_side"))
    write(os.path.join(ASSETS, "blockstates", "pot.json"), {"variants": {
        f"age={step}": {"model": f"{NS}:block/pot_{step}"} for step in range(AGES)}})
    write(os.path.join(ASSETS, "models/item", "pot.json"), {"parent": f"{NS}:block/pot_0"})
    written += 1

    simple = {
        "accelerator": ("accelerator_top", "accelerator_side"),
        "hive": ("hive_top", "hive_side"),
        "rock_generator": ("rock_top", "rock_side_0"),
        "driven_rock_generator": ("rock_top", "rock_side_1"),
        "industrial_rock_generator": ("rock_top", "rock_side_2"),
    }
    for kind in ALTAR_BLOCKS:
        simple[kind] = (f"{kind}_top", f"{kind}_side")
    for name, (top, side) in simple.items():
        write(os.path.join(ASSETS, "models/block", name + ".json"),
              box_model(FULL, top, side))
        write(os.path.join(ASSETS, "blockstates", name + ".json"),
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})
        written += 1

    for name in ITEM_SPRITES:
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": "item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        written += 1
    for material in CHUNKED:
        write(os.path.join(ASSETS, "models/item", material + "_chunk.json"),
              {"parent": "item/generated",
               "textures": {"layer0": f"{NS}:item/{material}_chunk"}})
        written += 1
    write(os.path.join(ASSETS, "models/item", "food_book.json"),
          {"parent": "item/generated",
           "textures": {"layer0": f"{NS}:item/food_book"}})
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
            "result": {"id": result, "count": count}}


def shapeless(ingredients, result, count=1):
    return {"type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": ingredients, "result": {"id": result, "count": count}}


ITEM = lambda name: {"item": name}
TAG = lambda name: {"tag": name}

RECIPES = {
    # A book and something grown, which is the whole mechanic in one recipe.
    "food_book": {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [{"item": "minecraft:book"}, {"item": "minecraft:wheat"},
                        {"item": "minecraft:apple"}],
        "result": {"id": f"{NS}:food_book", "count": 1},
    },
    "pot": shaped(["C C", "CPC", "CCC"], {
        "C": ITEM("minecraft:clay_ball"), "P": TAG("minecraft:planks")},
        f"{NS}:pot", 2),
    "accelerator": shaped(["PRP", "RTR", "PRP"], {
        "P": TAG("c:plates/iron"), "R": ITEM("minecraft:redstone"),
        "T": ITEM(f"{NS}:pot")}, f"{NS}:accelerator"),
    "hive": shaped(["PPP", "PHP", "PPP"], {
        "P": TAG("minecraft:planks"), "H": ITEM("minecraft:honeycomb")},
        f"{NS}:hive"),
    "rock_generator": shaped(["CCC", "CPC", "CCC"], {
        "C": ITEM("minecraft:cobblestone"), "P": ITEM("minecraft:piston")},
        f"{NS}:rock_generator"),
    "driven_rock_generator": shaped(["PPP", "PGP", "PWP"], {
        "P": TAG("c:plates/iron"), "G": ITEM(f"{NS}:rock_generator"),
        "W": ITEM(f"{IND}:copper_wire")}, f"{NS}:driven_rock_generator"),
    "industrial_rock_generator": shaped(["PPP", "PGP", "PWP"], {
        "P": TAG("c:plates/steel"), "G": ITEM(f"{NS}:driven_rock_generator"),
        "W": ITEM(f"{IND}:gold_wire")}, f"{NS}:industrial_rock_generator"),
    CENTRE: shaped(["SES", "EOE", "SES"], {
        "S": ITEM("minecraft:stone_bricks"), "E": ITEM(f"{NS}:{ESSENCES[-1]}"),
        "O": ITEM("minecraft:obsidian")}, f"{NS}:{CENTRE}"),
    PEDESTAL: shaped(["S S", " E ", "SSS"], {
        "S": ITEM("minecraft:stone_bricks"),
        "E": ITEM(f"{NS}:{ESSENCES[-1]}")}, f"{NS}:{PEDESTAL}", 4),
    RELAY: shaped([" S ", "SES", " S "], {
        "S": ITEM("minecraft:stone_bricks"),
        "E": ITEM(f"{NS}:{ESSENCES[0]}")}, f"{NS}:{RELAY}", 4),
    "bee": shapeless([ITEM("minecraft:honeycomb"), TAG("minecraft:small_flowers"),
                      ITEM("minecraft:sugar")], f"{NS}:bee"),
    "wax": shapeless([ITEM(f"{NS}:comb"), ITEM(f"{NS}:comb")], f"{NS}:wax"),
}


def ladder_recipes():
    """Climbing a rung, planting a seed, and cashing essence in for metal."""
    made = {}
    for rung in range(1, TIERS):
        made[f"{ESSENCES[rung]}_from_{ESSENCES[rung - 1]}"] = shapeless(
            [ITEM(f"{NS}:{ESSENCES[rung - 1]}")] * ESSENCE_PER_STEP,
            f"{NS}:{ESSENCES[rung]}")
    for material, (tier, gives) in SEEDS.items():
        # The seed costs a sample of the thing it grows. For the seventeen
        # whose sample is a chunk this module itself makes, that would be
        # circular — the only way to get a chunk is to grow the seed — so the
        # seed asks for the ore instead, which elysium-ores tags and puts in
        # the ground. You mine it once and grow it after that.
        sample = (TAG(f"c:ores/{material}") if material in CHUNKED
                  else ITEM(gives))
        # The first rung is paid in the game's own seeds, every rung above in
        # its own essence: SeedRules.paidInEssence.
        price = (ITEM(FIRST_RUNG_PRICE) if tier == 1
                 else ITEM(f"{NS}:{ESSENCES[tier - 1]}"))
        made[f"{material}_seed"] = shapeless(
            [price] * ESSENCE_PER_STEP + [sample],
            f"{NS}:{material}_seed")
        made[f"{material}_from_essence"] = shapeless(
            [ITEM(f"{NS}:{ESSENCES[tier - 1]}")] * ESSENCE_PER_INGOT, gives)
    return made


def altar_files():
    """
    What the altar can do, written out as data.

    <p>Not recipes the game reads — the altar is a block that checks what is
    standing on it against AltarRules, and the game has no recipe type for a
    ring of pedestals. This is the same table in a form a player's recipe
    book, a wiki page and this repository's own validator can all read, and
    the validator checks it against the rules rather than against itself.
    """
    written = 0
    top = ESSENCES[-1]
    table = [{
        "id": INSANIUM,
        "takes": "",
        "each": PER_INSANIUM,
        "essence": top,
        "makes": INSANIUM,
    }]
    for material in SEEDS:
        table.append({
            "id": f"awakened_{material}_seed",
            "takes": f"{material}_seed",
            "each": PER_INSANIUM,
            "essence": INSANIUM,
            "makes": f"awakened_{material}_seed",
        })
    write(os.path.join(DATA, "awakening/table.json"), {
        "pedestals": 4,
        "relays": 4,
        "awakenings": table,
    })
    written += 1
    for name in ALTAR_BLOCKS:
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"),
              drop_itself(name))
        written += 1
    return written


def data_files():
    written = altar_files()
    for name in BLOCKS:
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"), drop_itself(name))
        written += 1
    for name, recipe in dict(RECIPES, **ladder_recipes()).items():
        write(os.path.join(DATA, "recipe", name + ".json"), recipe)
        written += 1

    # Every chunk carries the tag the foundry already smelts, so growing tin
    # and mining tin end up in the same furnace rather than in two.
    for material in CHUNKED:
        write(os.path.join(DATA, "../c/tags/item", f"raw_materials/{material}.json"),
              {"replace": False, "values": [f"{NS}:{material}_chunk"]})
        written += 1

    # What a hive will take. Vanilla has no bee item, so the tag is ours and
    # this is what fills it.
    write(os.path.join(DATA, "tags/item/bees.json"),
          {"replace": False, "values": [f"{NS}:bee"]})
    written += 1

    # What mines every block of the module, from HarvestRules: one write per
    # tag file, since a second write would replace the first.
    written += harvest_tags()
    return written


# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

NAMES = {
    "pot": "Pot",
    "accelerator": "Accelerator",
    "hive": "Hive",
    "rock_generator": "Rock Generator",
    "driven_rock_generator": "Driven Rock Generator",
    "industrial_rock_generator": "Industrial Rock Generator",
    CENTRE: "Awakening Altar",
    PEDESTAL: "Essence Pedestal",
    RELAY: "Essence Relay",
}

ITEM_NAMES = {
    "bee": "Bee",
    "comb": "Comb",
    "wax": "Wax",
    "faint_essence": "Faint Essence",
    "bright_essence": "Bright Essence",
    "keen_essence": "Keen Essence",
    "radiant_essence": "Radiant Essence",
    "ascendant_essence": "Ascendant Essence",
}
ITEM_NAMES[INSANIUM] = "Insanium Essence"
for material in SEEDS:
    ITEM_NAMES[material + "_seed"] = material.replace("_", " ").title() + " Seed"
    ITEM_NAMES[f"awakened_{material}_seed"] = \
        "Awakened " + material.replace("_", " ").title() + " Seed"

LINES = {
    "itemGroup.elysiumagronomy": "Elysium Agronomy",
    "elysiumagronomy.altar.bare": "Nothing on it",
    "elysiumagronomy.altar.unbuilt":
        "Not an altar yet: %s pedestals on the diagonals and %s relays on the "
        "axes, all flat and all around the middle",
    "elysiumagronomy.altar.notready":
        "Nothing here to awaken, or not enough essence to do it with",
    "elysiumagronomy.altar.awoke": "Awoken: %s",
    "elysiumagronomy.diet.read": "%s of %s different foods eaten: %s extra hearts.",
    "elysiumagronomy.diet.tip":
        "Eat %s different foods for %s extra hearts. Eating the same thing twice "
        "counts once.",
    "elysiumagronomy.pot.bare": "Nothing planted in it.",
    "elysiumagronomy.pot.dark": "Too dark: light %s, and it wants %s.",
    "elysiumagronomy.pot.growing": "%s, growing at %sx. About %ss to go.",
    "elysiumagronomy.accelerator.alone": "No pot beside it.",
    "elysiumagronomy.accelerator.dark": "%s pot(s) beside it, and no power reaching it.",
    "elysiumagronomy.accelerator.running": "%s pot(s) at %sx, on %s of what it wants.",
    "elysiumagronomy.hive.empty": "No bees in it.",
    "elysiumagronomy.hive.crowded": "%s bees and %s flowers, and they want %s. "
                                    "%s combs an hour.",
    "elysiumagronomy.hive.working": "%s bees, %s combs an hour.",
    "elysiumagronomy.rock.dark": "No power reaching it.",
    "elysiumagronomy.rock.running": "%s blocks an hour.",
}


def language():
    entries = dict(LINES)
    for name, shown in NAMES.items():
        entries[f"block.{NS}.{name}"] = shown
    for name, shown in ITEM_NAMES.items():
        entries[f"item.{NS}.{name}"] = shown
    for material in CHUNKED:
        entries[f"item.{NS}.{material}_chunk"] = " ".join(
            word.capitalize() for word in material.split("_")) + " Chunk"
    entries[f"item.{NS}.food_book"] = "Food Book"
    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(entries.items())))
    return len(entries)


if __name__ == "__main__":
    sprites = draw_all()
    models = models_and_states()
    data = data_files()
    keys = language()
    print(f"agronomy: {sprites} sprite(s), {models} thing(s) modelled and stated, "
          f"{data} data file(s), {keys} language key(s)")
