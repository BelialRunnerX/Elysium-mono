#!/usr/bin/env python3
"""
Every sprite, model and data file elysium-comforts ships.

    python3 tools/gen_comforts.py

One block, facing four ways. Nobody crafts it; it appears when you need it and
disappears when you have taken your things back.
"""
import json
import re
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumcomforts"

OUT = os.environ.get("ELYSIUM_COMFORTS_OUT", MOD)
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
# Weathered stone, and one line of the pack's own accent cut into the face, so
# that a grave reads as ours across a field without reading as a decoration.

STONE = [(58, 58, 62), (86, 86, 92), (114, 114, 122), (146, 146, 156)]
MOSS = [(48, 66, 40), (70, 96, 56), (96, 128, 76)]
CUT = (127, 112, 196)
CLEAR = (0, 0, 0, 0)


#: A crate: four shades of plain board.
WOOD = [(58, 42, 28), (104, 78, 50), (140, 108, 70), (176, 142, 96)]


def rgba(colour):
    return (colour[0], colour[1], colour[2], 255)


def weathered(seed):
    """Stone with a fixed scatter and a little moss at the foot."""
    image = Image.new("RGBA", (16, 16), rgba(STONE[1]))
    draw = ImageDraw.Draw(image)
    draw.line([(0, 0), (15, 0)], fill=rgba(STONE[3]))
    draw.line([(0, 0), (0, 15)], fill=rgba(STONE[2]))
    draw.line([(0, 15), (15, 15)], fill=rgba(STONE[0]))
    draw.line([(15, 0), (15, 15)], fill=rgba(STONE[0]))
    for y in range(1, 15):
        for x in range(1, 15):
            mix = (x * 41 + y * 73 + seed * 31) % 256
            if mix < 22:
                draw.point((x, y), fill=rgba(STONE[0]))
            elif mix < 44:
                draw.point((x, y), fill=rgba(STONE[2]))
            if y >= 12 and mix % 5 == 0:
                draw.point((x, y), fill=rgba(MOSS[mix % 3]))
    return image


def grave_front():
    """A face with a mark cut into it."""
    image = weathered(3)
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 2, 12, 12], outline=rgba(STONE[0]))
    draw.line([(8, 4), (8, 10)], fill=rgba(CUT))
    draw.line([(6, 6), (10, 6)], fill=rgba(CUT))
    draw.point((8, 8), fill=rgba(STONE[3]))
    return image


def grave_side():
    return weathered(7)


def grave_top():
    image = weathered(11)
    draw = ImageDraw.Draw(image)
    draw.rectangle([4, 5, 11, 10], outline=rgba(STONE[0]))
    return image


SPRITES = {
    "grave_front": grave_front,
    "grave_side": grave_side,
    "grave_top": grave_top,
}

BLOCKS = ["grave"]

# Somewhere to sleep that is not a bed, read out of RestRules. Which colours
# exist and which half of the day each kind works in are rules; what shade of
# cloth each colour is, is art, and lives here.
def rules(named):
    """A rules file, or a sentence rather than a stack trace.

    The validator runs this generator as a subprocess, and a traceback there
    is a page of noise printed over the findings the validator had already
    collected. A missing rules file is a state this project describes rather
    than dies of.
    """
    where = os.path.join(MOD, "src/main/java/com/elysium/comforts/rules", named)
    if not os.path.isfile(where):
        print("there is no %s, so this generator cannot know what to write" % named)
        sys.exit(2)
    return open(where, encoding="utf-8").read()


REST_SOURCE = rules("RestRules.java")
_squashed = re.sub(r"\s+", " ", REST_SOURCE)
_kinds = re.search(r"List<String> KINDS = List\.of\((.*?)\);", _squashed)
KIND_NAMES = dict(re.findall(
    r'public static final String (BAG|HAMMOCK|ROPE) = "(\w+)";', REST_SOURCE))
KINDS = [KIND_NAMES[one] for one in re.findall(r"[A-Z]+", _kinds.group(1))
         if one in KIND_NAMES] if _kinds else []
_colours = re.search(r"List<String> COLOURS = List\.of\((.*?)\);", _squashed)
COLOURS = re.findall(r'"([a-z_]+)"', _colours.group(1)) if _colours else []
ROPE = KIND_NAMES.get("ROPE", "rope_and_nail")
REFUSALS = dict(re.findall(
    r'public static final String (OCCUPIED|TOO_FAR|NO_ROOM|NO_ROPE|NOT_NOW'
    r'|UNSAFE) = "(\w+)";', REST_SOURCE))
if len(KINDS) != 2 or len(COLOURS) != 16 or len(REFUSALS) != 6:
    print("RestRules reads as %d kind(s), %d colour(s) and %d refusal(s)"
          % (len(KINDS), len(COLOURS), len(REFUSALS)))
    sys.exit(2)
RESTS = [colour + "_" + kind for kind in KINDS for colour in COLOURS]
BLOCKS = BLOCKS + RESTS + [ROPE]

#: What each of the game's sixteen dyes looks like as cloth. Art, not a rule.
CLOTH = {
    "white": (233, 236, 236), "orange": (240, 118, 19),
    "magenta": (189, 68, 179), "light_blue": (58, 175, 217),
    "yellow": (248, 198, 39), "lime": (112, 185, 25),
    "pink": (237, 141, 172), "gray": (62, 68, 71),
    "light_gray": (142, 142, 134), "cyan": (21, 137, 145),
    "purple": (121, 42, 172), "blue": (53, 57, 157),
    "brown": (114, 71, 40), "green": (84, 109, 27),
    "red": (161, 39, 34), "black": (20, 21, 25),
}
if sorted(CLOTH) != sorted(COLOURS):
    print("there is cloth for %s and the rules name %s"
          % (sorted(CLOTH), sorted(COLOURS)))
    sys.exit(2)
ROPE_SHADE = [(74, 58, 36), (122, 98, 60), (158, 132, 86)]

# The chimes, read out of ChimeRules for the same reason: which materials
# exist and what note each rings are rules, and what colour a copper tube is
# is art.
CHIME_SOURCE = rules("ChimeRules.java")
STATION_SOURCE = rules("StationRules.java")
STATIONS = re.findall(
    r'new Station\("([a-z_]+)", (\w+), (\d+), (\d+), ([\d_]+), (true|false)\)',
    re.sub(r"\s+", " ", STATION_SOURCE))
STATION_IDS = [one[0] for one in STATIONS]
if len(STATION_IDS) != 8:
    print("StationRules reads as %d station(s) rather than eight" % len(STATION_IDS))
    sys.exit(2)
_materials = re.search(r"List<String> MATERIALS = List\.of\((.*?)\);",
                       re.sub(r"\s+", " ", CHIME_SOURCE))
MATERIALS = re.findall(r'"([a-z_]+)"', _materials.group(1)) if _materials else []
MADE_OF = dict(re.findall(r'made\.put\("([a-z_]+)", "([a-z_:]+)"\);',
                          CHIME_SOURCE))
if len(MATERIALS) != 6 or sorted(MADE_OF) != sorted(MATERIALS):
    print("ChimeRules reads as %d material(s) and %d recipe(s)"
          % (len(MATERIALS), len(MADE_OF)))
    sys.exit(2)
CHIMES = [colour + "_" + material + "_chimes"
          for material in MATERIALS for colour in COLOURS]
BLOCKS = BLOCKS + CHIMES + STATION_IDS

# The post, read out of PostRules for the same reason again: which woods take a
# pigeonhole and what each piece of mail costs are rules; what a paper box looks
# like is art.
POST_SOURCE = rules("PostRules.java")
_flat = re.sub(r"\s+", " ", POST_SOURCE)
_woods = re.search(r"List<String> WOODS = List\.of\((.*?)\);", _flat)
WOODS = re.findall(r'"([a-z_]+)"', _woods.group(1)) if _woods else []
PIGEONHOLES = [wood + "_pigeonhole" for wood in WOODS]
MAIL = [(one[0], int(one[1]), int(one[2]), one[3] == "true", one[4] == "true",
         one[5] == "true")
        for one in re.findall(
            r'new Mail\((\w+), (\d+), (\d+), (true|false), (true|false), '
            r'(true|false)\)', _flat)]
MAIL_NAMES = dict(re.findall(
    r'public static final String ([A-Z_]+) = "([a-z_]+)";', POST_SOURCE))
MAIL_IDS = [MAIL_NAMES[one[0]] for one in MAIL if one[0] in MAIL_NAMES]
# Each bird's name and how many colourings it comes in. The count matters here
# rather than only in the rules, because it is how many skins get drawn, and a
# bird whose renderer asks for a fourth skin that was never drawn is a bird one
# player in four meets as a missing texture.
BIRD_ROWS = [(one[0], int(one[4])) for one in re.findall(
    r'new Bird\("([a-z_]+)", (\w+), ([^,]+), (\d+), (\d+)\)', _flat)]
BIRDS = [one[0] for one in BIRD_ROWS]
_seal_materials = re.search(r"List<String> SEAL_MATERIALS = List\.of\((.*?)\);",
                            _flat)
SEAL_MATERIALS = (re.findall(r'"([a-z_]+)"', _seal_materials.group(1))
                  if _seal_materials else [])
if len(WOODS) != 11 or len(MAIL_IDS) != 8 or len(BIRDS) != 2 \
        or len(SEAL_MATERIALS) != 3:
    print("PostRules reads as %d wood(s), %d kind(s) of mail, %d bird(s) and "
          "%d seal material(s)"
          % (len(WOODS), len(MAIL_IDS), len(BIRDS), len(SEAL_MATERIALS)))
    sys.exit(2)
BLOCKS = BLOCKS + PIGEONHOLES + ["mailbox"]

# How dirty a pigeonhole gets, read rather than typed: the blockstate has to
# list every level the block can be in, and a blockstate one short is a block
# that renders as nothing at exactly the moment it is worth scooping.
_foulest = re.search(r"int FOULEST = (\d+);", POST_SOURCE)
if not _foulest:
    print("PostRules does not say how dirty a pigeonhole gets")
    sys.exit(2)
FOULEST = int(_foulest.group(1))

#: What each wood looks like, to look at. Art, not a rule.
PLANK = {
    "oak": (162, 130, 78), "spruce": (114, 84, 48), "birch": (196, 179, 123),
    "jungle": (160, 115, 81), "acacia": (168, 90, 50),
    "dark_oak": (66, 43, 20), "mangrove": (117, 54, 48),
    "cherry": (226, 177, 172), "bamboo": (193, 158, 78),
    "crimson": (101, 48, 71), "warped": (43, 104, 99),
}
if sorted(PLANK) != sorted(WOODS):
    print("there are planks for %s and the rules name %s"
          % (sorted(PLANK), sorted(WOODS)))
    sys.exit(2)

#: What the mail is made of, to look at. Art, not a rule.
PAPER = (232, 228, 214)
WAX = {"red_wax": (167, 58, 51), "gold": (255, 179, 71), "lapis": (58, 92, 173)}
if sorted(WAX) != sorted(SEAL_MATERIALS):
    print("there is wax for %s and the rules name %s"
          % (sorted(WAX), sorted(SEAL_MATERIALS)))
    sys.exit(2)

#: What each chime's tubes are made of, to look at. Art, not a rule.
TUBE = {
    "oak": (150, 118, 72), "bamboo": (176, 186, 96),
    "copper": (190, 110, 72), "iron": (198, 198, 202),
    "glass": (206, 230, 236), "amethyst": (162, 116, 204),
}
if sorted(TUBE) != sorted(MATERIALS):
    print("there are tubes for %s and materials for %s"
          % (sorted(TUBE), sorted(MATERIALS)))
    sys.exit(2)


def crate_empty():
    """A slatted crate with the lid off: somewhere to put somebody."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 3, 14, 15], fill=rgba(WOOD[1]), outline=rgba(WOOD[0]))
    for x in range(3, 14, 3):
        draw.line([(x, 4), (x, 14)], fill=rgba(WOOD[0]))
    draw.line([(1, 3), (14, 3)], fill=rgba(WOOD[3]))
    draw.line([(1, 8), (14, 8)], fill=rgba(WOOD[2]))
    # The lid, leaning against the side, so an empty crate reads as open.
    draw.polygon([(2, 2), (13, 2), (12, 0), (3, 0)], fill=rgba(WOOD[2]),
                 outline=rgba(WOOD[0]))
    return image


def crate_full():
    """The same crate, shut, with somebody's hat showing over the rim."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 5, 14, 15], fill=rgba(WOOD[1]), outline=rgba(WOOD[0]))
    for x in range(3, 14, 3):
        draw.line([(x, 6), (x, 14)], fill=rgba(WOOD[0]))
    draw.line([(1, 5), (14, 5)], fill=rgba(WOOD[3]))
    draw.line([(1, 10), (14, 10)], fill=rgba(WOOD[2]))
    draw.rectangle([5, 1, 10, 4], fill=(122, 96, 72, 255), outline=(74, 58, 46, 255))
    draw.line([(4, 4), (11, 4)], fill=(74, 58, 46, 255))
    return image


#: The two item sprites. A crate with somebody in it has to be different from
#: an empty one at a glance, or a player with a row of them in a chest is a
#: player opening every one.
ITEM_SPRITES = {"trader_crate": crate_empty, "trader_crate_full": crate_full}

def cloth(colour, seed):
    """A square of dyed cloth, scattered with a fixed hash so the sheet is
    the same on every machine and the generated-file check can hold."""
    base = CLOTH[colour]
    image = Image.new("RGBA", (16, 16), rgba(base))
    draw = ImageDraw.Draw(image)
    for y in range(16):
        for x in range(16):
            mix = (x * 41 + y * 97 + seed * 23) % 256
            if mix < 40:
                draw.point((x, y), fill=rgba(tuple(max(0, c - 22) for c in base)))
            elif mix < 84:
                draw.point((x, y), fill=rgba(tuple(min(255, c + 18) for c in base)))
    draw.line([(0, 0), (15, 0)], fill=rgba(tuple(min(255, c + 34) for c in base)))
    draw.line([(0, 15), (15, 15)], fill=rgba(tuple(max(0, c - 34) for c in base)))
    return image


def chime_sprite(colour, material):
    """A cord in the dye's colour, and four tubes in the material's."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    cord = CLOTH[colour]
    tube = TUBE[material]
    draw.rectangle([5, 0, 10, 2], fill=rgba(cord))
    for at, x in enumerate((4, 6, 9, 11)):
        bottom = 8 + ((at * 3 + len(material)) % 5)
        draw.line([(x, 3), (x, bottom)], fill=rgba(tube))
        draw.point((x, 3), fill=rgba(tuple(min(255, c + 40) for c in tube)))
        draw.point((x, bottom), fill=rgba(tuple(max(0, c - 40) for c in tube)))
    return image


#: Plain boards with one face doing the job, so eight stations read as eight
#: things at a glance rather than as eight labels on one box.
#: What sits in the middle of each station's recipe. Eight blocks made of the
#: same nine things in the same shape would be eight recipes the game cannot
#: tell apart, and only one of them would ever fire.
STATION_HEART = {
    "trading_station": "minecraft:emerald",
    "auto_trader": "minecraft:hopper",
    "breeder": "minecraft:hay_block",
    "converter": "minecraft:golden_apple",
    "incubator": "minecraft:bread",
    "villager_farm": "minecraft:iron_hoe",
    "iron_farm": "minecraft:iron_block",
    "inventory_viewer": "minecraft:glass",
}

if sorted(STATION_HEART) != sorted(STATION_IDS):
    print("there are hearts for %s and stations for %s"
          % (sorted(STATION_HEART), sorted(STATION_IDS)))
    sys.exit(2)

STATION_FACE = {
    "trading_station": (196, 160, 72), "auto_trader": (150, 178, 196),
    "breeder": (176, 132, 168), "converter": (120, 176, 120),
    "incubator": (216, 196, 140), "villager_farm": (150, 186, 96),
    "iron_farm": (196, 196, 200), "inventory_viewer": (168, 148, 120),
}


def station_sprite(name):
    image = Image.new("RGBA", (16, 16), rgba(WOOD[1]))
    draw = ImageDraw.Draw(image)
    for y in range(16):
        for x in range(16):
            mix = (x * 41 + y * 97 + sum(ord(c) for c in name) * 23) % 256
            if mix < 44:
                draw.point((x, y), fill=rgba(WOOD[0]))
            elif mix < 92:
                draw.point((x, y), fill=rgba(WOOD[2]))
    draw.rectangle([3, 3, 12, 12], fill=rgba(STATION_FACE[name]))
    draw.rectangle([3, 3, 12, 12], outline=rgba(WOOD[0]))
    return image


def pigeonhole_sprite(wood):
    """A wall of holes in a plank face, one shade per wood."""
    shade = PLANK[wood]
    dark = tuple(max(0, one - 45) for one in shade)
    light = tuple(min(255, one + 30) for one in shade)
    image = Image.new("RGBA", (16, 16), rgba(shade))
    draw = ImageDraw.Draw(image)
    for y in range(0, 16, 5):
        draw.line([(0, y), (15, y)], fill=rgba(dark))
    for across in (2, 9):
        for down in (2, 9):
            draw.rectangle([across, down, across + 4, down + 4], fill=rgba(dark))
            draw.rectangle([across + 1, down + 1, across + 3, down + 3],
                           fill=rgba(tuple(max(0, one - 70) for one in shade)))
    draw.line([(0, 0), (15, 0)], fill=rgba(light))
    return image


def mailbox_sprite():
    image = Image.new("RGBA", (16, 16), rgba(PLANK["spruce"]))
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 4, 13, 12], fill=rgba((176, 62, 56)))
    draw.rectangle([2, 4, 13, 12], outline=rgba((92, 30, 27)))
    draw.line([(4, 8), (11, 8)], fill=rgba(PAPER))
    return image


def letter_sprite(name, slots, sealed, wax):
    """A sheet or a box, with wax on it when it is sealed."""
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    if slots > 0:
        draw.rectangle([2, 4, 13, 14],
                       fill=rgba(tuple(max(0, one - 30) for one in PAPER)))
        draw.rectangle([2, 4, 13, 14], outline=rgba((150, 140, 116)))
        draw.line([(2, 8), (13, 8)], fill=rgba((150, 140, 116)))
        draw.line([(7, 4), (7, 14)], fill=rgba((150, 140, 116)))
    else:
        draw.rectangle([1, 4, 14, 12], fill=rgba(PAPER))
        draw.rectangle([1, 4, 14, 12], outline=rgba((150, 140, 116)))
        draw.line([(1, 4), (7, 9)], fill=rgba((150, 140, 116)))
        draw.line([(14, 4), (8, 9)], fill=rgba((150, 140, 116)))
    if sealed:
        draw.ellipse([6, 7, 10, 11], fill=rgba(wax))
        draw.ellipse([7, 8, 9, 10],
                     fill=rgba(tuple(max(0, one - 50) for one in wax)))
    return image


def tag_sprite():
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.polygon([(3, 5), (12, 5), (14, 8), (12, 11), (3, 11)], fill=rgba(PAPER))
    draw.polygon([(3, 5), (12, 5), (14, 8), (12, 11), (3, 11)],
                 outline=rgba((150, 140, 116)))
    draw.point((5, 8), fill=rgba((90, 84, 70)))
    return image


def stamp_sprite():
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([6, 2, 9, 9], fill=rgba(PLANK["oak"]))
    draw.rectangle([4, 9, 11, 13], fill=rgba((198, 198, 202)))
    draw.rectangle([4, 9, 11, 13], outline=rgba((120, 120, 126)))
    return image


def egg_sprite(name):
    base = (154, 154, 154) if name == "pigeon" else (72, 60, 58)
    spot = (63, 63, 63) if name == "pigeon" else (196, 88, 44)
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.ellipse([4, 2, 11, 13], fill=rgba(base))
    for x, y in ((6, 5), (9, 7), (6, 10), (8, 11)):
        draw.rectangle([x, y, x + 1, y + 1], fill=rgba(spot))
    return image


#: What each bird's colourings look like. Art, not a rule -- but the number of
#: them per bird is a rule, and draw_all walks that rather than this.
FEATHERS = {
    "pigeon": [(154, 154, 160), (110, 122, 140), (196, 196, 200),
               (128, 108, 96)],
    "charred_pigeon": [(64, 54, 52)],
}


def bird_skin(name, variant):
    """The bird itself, on the model's own 32x16 sheet."""
    shades = FEATHERS[name]
    base = shades[variant % len(shades)]
    dark = tuple(max(0, one - 40) for one in base)
    image = Image.new("RGBA", (32, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 31, 15], fill=rgba(base))
    draw.rectangle([0, 12, 15, 15], fill=rgba(dark))
    draw.rectangle([20, 0, 31, 5], fill=rgba(dark))
    if name == "charred_pigeon":
        for y in range(0, 16, 3):
            draw.line([(0, y), (31, y)], fill=rgba((214, 96, 40)))
    return image


def rope_sprite():
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    for y in range(4, 16):
        shade = ROPE_SHADE[(y // 2) % len(ROPE_SHADE)]
        draw.line([(7, y), (8, y)], fill=rgba(shade))
    draw.rectangle([6, 2, 9, 4], fill=rgba(ROPE_SHADE[0]))
    return image


def draw_all():
    folder = os.path.join(ASSETS, "textures/block")
    os.makedirs(folder, exist_ok=True)
    for name, make in SPRITES.items():
        make().save(os.path.join(folder, name + ".png"))
    items = os.path.join(ASSETS, "textures/item")
    os.makedirs(items, exist_ok=True)
    for name, make in ITEM_SPRITES.items():
        make().save(os.path.join(items, name + ".png"))
    drawn = 0
    for kind in KINDS:
        for colour in COLOURS:
            seed = sum(ord(letter) for letter in colour + kind)
            cloth(colour, seed).save(
                os.path.join(folder, colour + "_" + kind + ".png"))
            drawn += 1
    rope_sprite().save(os.path.join(folder, ROPE + ".png"))
    drawn += 1
    for material in MATERIALS:
        for colour in COLOURS:
            chime_sprite(colour, material).save(os.path.join(
                folder, colour + "_" + material + "_chimes.png"))
            drawn += 1
    for name in STATION_IDS:
        station_sprite(name).save(os.path.join(folder, name + ".png"))
        drawn += 1
    for wood in WOODS:
        pigeonhole_sprite(wood).save(
            os.path.join(folder, wood + "_pigeonhole.png"))
        drawn += 1
    mailbox_sprite().save(os.path.join(folder, "mailbox.png"))
    drawn += 1
    # The post's own items: a sheet or a box each, and wax on the sealed ones.
    # Walked rather than listed, because a kind of mail with no sprite is an item
    # that renders as the missing-texture cube and is found by whoever crafts it
    # rather than by anybody here.
    for one in MAIL:
        name = MAIL_NAMES[one[0]]
        letter_sprite(name, one[1], one[3], WAX[SEAL_MATERIALS[0]]).save(
            os.path.join(items, name + ".png"))
        drawn += 1
    tag_sprite().save(os.path.join(items, "address_tag.png"))
    stamp_sprite().save(os.path.join(items, "seal_stamp.png"))
    drawn += 2
    for name in BIRDS:
        egg_sprite(name).save(os.path.join(items, name + "_spawn_egg.png"))
        drawn += 1
    skins = os.path.join(ASSETS, "textures/entity")
    os.makedirs(skins, exist_ok=True)
    wanted = set()
    for one in BIRD_ROWS:
        for variant in range(one[1]):
            name = "%s_%d.png" % (one[0], variant)
            bird_skin(one[0], variant).save(os.path.join(skins, name))
            wanted.add(name)
            drawn += 1
    # And take away the ones the rules no longer ask for. A generator that only
    # ever adds leaves orphans behind, and an orphan skin is a texture shipped
    # in the jar that nothing can draw -- harmless until the day a validator
    # counts skins and finds the right number of the wrong ones.
    for stale in sorted(os.listdir(skins)):
        if stale.endswith(".png") and stale not in wanted:
            os.remove(os.path.join(skins, stale))
    return len(SPRITES) + len(ITEM_SPRITES) + drawn


# ---------------------------------------------------------------------------
# Models and blockstates
# ---------------------------------------------------------------------------

#: The headstone's box, which has to match GraveBlock's collision shape exactly
#: or a grave is a thing you can see through and cannot touch.
BOX = {"from": [2, 0, 4], "to": [14, 14, 12]}


def headstone():
    return {
        "parent": "block/block",
        "textures": {"front": f"{NS}:block/grave_front",
                     "side": f"{NS}:block/grave_side",
                     "top": f"{NS}:block/grave_top",
                     "particle": f"{NS}:block/grave_side"},
        "elements": [{
            "from": BOX["from"],
            "to": BOX["to"],
            "faces": {
                "down": {"texture": "#top"},
                "up": {"texture": "#top"},
                "north": {"texture": "#front"},
                "south": {"texture": "#side"},
                "west": {"texture": "#side"},
                "east": {"texture": "#side"},
            },
        }],
    }


#: What a bag or a hammock is, as a box. The same four sixteenths RestBlock
#: uses for its collision shape, because a thing you can see and cannot touch
#: is the commonest bug in a decorative block and it is never filed as one.
LIES = 4


def lying(colour, kind):
    texture = f"{NS}:block/{colour}_{kind}"
    return {
        "parent": "block/block",
        "textures": {"all": texture, "particle": texture},
        "elements": [{
            "from": [0, 0, 0],
            "to": [16, LIES, 16],
            "faces": {side: {"texture": "#all"} for side in
                      ("down", "up", "north", "south", "west", "east")},
        }],
    }


def rope_model():
    texture = f"{NS}:block/{ROPE}"
    return {
        "parent": "block/block",
        "textures": {"all": texture, "particle": texture},
        "elements": [{
            "from": [6, 10, 6],
            "to": [10, 16, 10],
            "faces": {side: {"texture": "#all"} for side in
                      ("down", "up", "north", "south", "west", "east")},
        }],
    }


def hanging(name):
    texture = f"{NS}:block/{name}"
    return {
        "parent": "block/block",
        "textures": {"all": texture, "particle": texture},
        "elements": [{
            "from": [4, 4, 4],
            "to": [12, 16, 12],
            "faces": {side: {"texture": "#all"} for side in
                      ("down", "up", "north", "south", "west", "east")},
        }],
    }


def models_and_states():
    write(os.path.join(ASSETS, "models/block", "grave.json"), headstone())
    write(os.path.join(ASSETS, "blockstates", "grave.json"), {"variants": {
        "facing=north": {"model": f"{NS}:block/grave"},
        "facing=east": {"model": f"{NS}:block/grave", "y": 90},
        "facing=south": {"model": f"{NS}:block/grave", "y": 180},
        "facing=west": {"model": f"{NS}:block/grave", "y": 270},
    }})
    # The crate, and the one thing that must be visible from a chest: whether
    # anybody is in it. A predicate on a component the item already carries,
    # so there is nothing extra to keep in step.
    for name in ITEM_SPRITES:
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": "minecraft:item/generated",
               "textures": {"layer0": f"{NS}:item/{name}"}})
    write(os.path.join(ASSETS, "models/item", "trader_crate.json"),
          {"parent": "minecraft:item/generated",
           "textures": {"layer0": f"{NS}:item/trader_crate"},
           "overrides": [{"predicate": {f"{NS}:occupied": 1},
                          "model": f"{NS}:item/trader_crate_full"}]})
    write(os.path.join(ASSETS, "models/item", "grave.json"),
          {"parent": f"{NS}:block/grave"})

    # Thirty-two places to sleep and a rope, every one of them walked out of
    # the rules. A blockstate written by hand for thirty-one of thirty-two is
    # one block that renders as a missing-texture cube, and it is only ever
    # found by the player who dyes that colour.
    for kind in KINDS:
        for colour in COLOURS:
            name = colour + "_" + kind
            write(os.path.join(ASSETS, "models/block", name + ".json"),
                  lying(colour, kind))
            write(os.path.join(ASSETS, "blockstates", name + ".json"),
                  {"variants": {
                      f"facing={way},occupied={held}": {
                          "model": f"{NS}:block/{name}",
                          **({"y": turn} if turn else {}),
                      }
                      for way, turn in (("north", 0), ("east", 90),
                                        ("south", 180), ("west", 270))
                      for held in ("false", "true")
                  }})
            write(os.path.join(ASSETS, "models/item", name + ".json"),
                  {"parent": f"{NS}:block/{name}"})
    write(os.path.join(ASSETS, "models/block", ROPE + ".json"), rope_model())
    write(os.path.join(ASSETS, "blockstates", ROPE + ".json"),
          {"variants": {"": {"model": f"{NS}:block/{ROPE}"}}})
    write(os.path.join(ASSETS, "models/item", ROPE + ".json"),
          {"parent": f"{NS}:block/{ROPE}"})

    for name in STATION_IDS:
        texture = f"{NS}:block/{name}"
        write(os.path.join(ASSETS, "models/block", name + ".json"), {
            "parent": "block/block",
            "textures": {"all": texture, "particle": texture},
            "elements": [{
                "from": [0, 0, 0],
                "to": [16, 14, 16],
                "faces": {side: {"texture": "#all"} for side in
                          ("down", "up", "north", "south", "west", "east")},
            }],
        })
        write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {
            f"facing={way},occupied={held}": {
                "model": f"{NS}:block/{name}",
                **({"y": turn} if turn else {}),
            }
            for way, turn in (("north", 0), ("east", 90), ("south", 180),
                              ("west", 270))
            for held in ("false", "true")
        }})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})

    # Eleven pigeonholes and a mailbox. Both face a direction, so both need all
    # four turns written out; a blockstate missing one is a block that renders
    # as nothing from exactly one angle.
    for wood in WOODS:
        name = wood + "_pigeonhole"
        texture = f"{NS}:block/{name}"
        write(os.path.join(ASSETS, "models/block", name + ".json"), {
            "parent": "block/block",
            "textures": {"all": texture, "particle": texture},
            "elements": [{
                "from": [0, 0, 0],
                "to": [16, 16, 16],
                "faces": {side: {"texture": "#all"} for side in
                          ("down", "up", "north", "south", "west", "east")},
            }],
        })
        write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {
            f"facing={way},waste={level}": {
                "model": f"{NS}:block/{name}",
                **({"y": turn} if turn else {}),
            }
            for way, turn in (("north", 0), ("east", 90), ("south", 180),
                              ("west", 270))
            for level in range(FOULEST + 1)
        }})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})

    write(os.path.join(ASSETS, "models/block", "mailbox.json"), {
        "parent": "block/block",
        "textures": {"all": f"{NS}:block/mailbox", "particle": f"{NS}:block/mailbox"},
        "elements": [{
            "from": [3, 0, 3],
            "to": [13, 14, 13],
            "faces": {side: {"texture": "#all"} for side in
                      ("down", "up", "north", "south", "west", "east")},
        }],
    })
    write(os.path.join(ASSETS, "blockstates", "mailbox.json"), {"variants": {
        f"facing={way},full={held}": {
            "model": f"{NS}:block/mailbox",
            **({"y": turn} if turn else {}),
        }
        for way, turn in (("north", 0), ("east", 90), ("south", 180),
                          ("west", 270))
        for held in ("false", "true")
    }})
    write(os.path.join(ASSETS, "models/item", "mailbox.json"),
          {"parent": f"{NS}:block/mailbox"})

    # And the post's flat items.
    for one in MAIL:
        name = MAIL_NAMES[one[0]]
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": "minecraft:item/generated",
               "textures": {"layer0": f"{NS}:item/{name}"}})
    for name in ("address_tag", "seal_stamp"):
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": "minecraft:item/generated",
               "textures": {"layer0": f"{NS}:item/{name}"}})
    for name in BIRDS:
        write(os.path.join(ASSETS, "models/item", name + "_spawn_egg.json"),
              {"parent": "minecraft:item/generated",
               "textures": {"layer0": f"{NS}:item/{name}_spawn_egg"}})

    for name in CHIMES:
        write(os.path.join(ASSETS, "models/block", name + ".json"), hanging(name))
        write(os.path.join(ASSETS, "blockstates", name + ".json"),
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})
    return len(BLOCKS)


# ---------------------------------------------------------------------------
# Loot and tags
# ---------------------------------------------------------------------------

def drops_nothing(name):
    """A grave drops nothing, because everything in it is dropped separately.

    Breaking one spills its contents from the block entity. If the block also
    dropped itself, a player could stockpile empty graves — and an empty grave
    in a chest is a block that looks exactly like somebody's belongings.
    """
    return {"type": "minecraft:block", "pools": []}


def gives_itself_back(name):
    """A rest always comes back when you break it.

    This module's whole theme is that nothing a player owned is lost, and a
    bag that vanishes when a creeper clips the floor under it is the same
    failure as a grave with too few slots — smaller, and just as annoying.
    """
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }


#: A shape apiece, and no two the same. Two kinds of mail sharing a shape is two
#: recipes the game resolves to whichever it loaded first, so one of them
#: silently does not exist. The validator checks this pairwise; the shapes are
#: here because what a thing is made of is art and how much it costs is a rule.
POST_SHAPES = {
    "letter": [" p ", "p p", " p "],
    "sealed_letter": [" p ", "psp", " w "],
    "package": ["ppp", "p p", "ppp"],
    "sealed_package": ["ppp", "psp", "pwp"],
    "paper_box": [" p ", "php", " p "],
    "payback_package": ["php", "psp", "ppp"],
    "payback_box": ["p p", "hsh", "ppp"],
}


def data_files():
    written = 0
    for name in BLOCKS:
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"),
              gives_itself_back(name) if name != "grave" else drops_nothing(name))
        written += 1
    tags = os.path.join(OUT, "src/main/resources/data/minecraft/tags/block")
    write(os.path.join(tags, "mineable/pickaxe.json"),
          {"replace": False, "values": [f"{NS}:{name}" for name in BLOCKS]})
    written += 1
    # Wood, a lead and a bed: what you would actually need to move somebody.
    for kind in KINDS:
        for colour in COLOURS:
            name = colour + "_" + kind
            pattern = (["###", "///"] if kind == KIND_NAMES["HAMMOCK"]
                       else ["###", "..."])
            # Only the symbols the pattern actually uses. A key that defines
            # one it does not is not a harmless extra: the game refuses the
            # whole recipe with a message in the log and carries on, so all
            # sixteen of them quietly do not exist.
            everything = {
                "#": {"item": f"minecraft:{colour}_wool"},
                "/": {"item": "minecraft:string"},
                ".": {"item": "minecraft:leather"},
            }
            used = {symbol for row in pattern for symbol in row}
            write(os.path.join(DATA, "recipe", name + ".json"), {
                "type": "minecraft:crafting_shaped",
                "category": "misc",
                "pattern": pattern,
                "key": {symbol: what for symbol, what in everything.items()
                        if symbol in used},
                "result": {"id": f"{NS}:{name}", "count": 1},
            })
            written += 1
    for material in MATERIALS:
        for colour in COLOURS:
            name = colour + "_" + material + "_chimes"
            write(os.path.join(DATA, "recipe", name + ".json"), {
                "type": "minecraft:crafting_shaped",
                "category": "misc",
                "pattern": ["ccc", "mmm"],
                "key": {"c": {"item": f"minecraft:{colour}_wool"},
                        "m": {"item": MADE_OF[material]}},
                "result": {"id": f"{NS}:{name}", "count": 1},
            })
            written += 1
    for name in STATION_IDS:
        write(os.path.join(DATA, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "pattern": ["ppp", "pep", "ppp"],
            "key": {"p": {"item": "minecraft:oak_planks"},
                    "e": {"item": STATION_HEART[name]}},
            "result": {"id": f"{NS}:{name}", "count": 1},
        })
        written += 1
    write(os.path.join(DATA, "recipe", ROPE + ".json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["i", "s", "s"],
        "key": {"i": {"item": "minecraft:iron_nugget"},
                "s": {"item": "minecraft:string"}},
        "result": {"id": f"{NS}:{ROPE}", "count": 2},
    })
    written += 1
    write(os.path.join(DATA, "recipe", "trader_crate.json"), {
        "type": "minecraft:crafting_shaped",
        "pattern": ["PLP", "P P", "PPP"],
        "key": {"P": {"tag": "minecraft:planks"}, "L": {"item": "minecraft:lead"}},
        "result": {"id": f"{NS}:trader_crate", "count": 1},
    })
    written += 1

    # The post. One pigeonhole per wood, out of that wood's own planks, so a
    # player who has only found birch can still build one.
    for wood in WOODS:
        name = wood + "_pigeonhole"
        write(os.path.join(DATA, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "group": "pigeonhole",
            "pattern": ["ppp", "php", "ppp"],
            "key": {"p": {"item": f"minecraft:{wood}_planks"},
                    "h": {"item": "minecraft:hay_block"}},
            "result": {"id": f"{NS}:{name}", "count": 1},
        })
        written += 1
    write(os.path.join(DATA, "recipe", "mailbox.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["sss", "ptp", "ppp"],
        "key": {"p": {"tag": "minecraft:planks"},
                "s": {"tag": "minecraft:wooden_slabs"},
                "t": {"item": f"{NS}:address_tag"}},
        "result": {"id": f"{NS}:mailbox", "count": 1},
    })
    written += 1
    write(os.path.join(DATA, "recipe", "address_tag.json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [{"item": "minecraft:paper"}, {"tag": "minecraft:signs"}],
        "result": {"id": f"{NS}:address_tag", "count": 1},
    })
    written += 1
    write(os.path.join(DATA, "recipe", "seal_stamp.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "pattern": [" s ", " s ", " i "],
        "key": {"s": {"tag": "minecraft:wooden_slabs"},
                "i": {"item": "minecraft:iron_ingot"}},
        "result": {"id": f"{NS}:seal_stamp", "count": 1},
    })
    written += 1
    # Mail itself: postage is paid in paper, so paper is what it is made of.
    # Lost mail has no recipe -- it is what turns up when something did not
    # arrive, and a recipe for it would be a way to craft your own bad luck.
    for one in MAIL:
        name = MAIL_NAMES[one[0]]
        if name == "lost_mail":
            continue
        shape = POST_SHAPES[name]
        everything = {
            "p": {"item": "minecraft:paper"},
            "h": {"item": "minecraft:honeycomb"},
            "w": {"item": "minecraft:red_dye"},
            "s": {"item": f"{NS}:seal_stamp"},
        }
        used = {symbol for row in shape for symbol in row if symbol != " "}
        write(os.path.join(DATA, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "pattern": shape,
            "key": {symbol: what for symbol, what in everything.items()
                    if symbol in used},
            "result": {"id": f"{NS}:{name}", "count": 1},
        })
        written += 1
    return written


# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

def titled(name):
    return " ".join(word.capitalize() for word in name.split("_"))


NAMES = {"grave": "Grave"}
for _kind in KINDS:
    for _colour in COLOURS:
        NAMES[_colour + "_" + _kind] = titled(_colour + "_" + _kind)
NAMES[ROPE] = titled(ROPE)
for _name in CHIMES:
    NAMES[_name] = titled(_name)
for _name in STATION_IDS:
    NAMES[_name] = titled(_name)
for _name in PIGEONHOLES:
    NAMES[_name] = titled(_name)
NAMES["mailbox"] = "Mailbox"

#: One sentence per way a rest can decline, because a block that answers "you
#: cannot sleep here" to all six is a block a player has to guess at.
REFUSAL_WORDS = {
    "occupied": "Somebody is already in it.",
    "too_far_away": "You are not near enough to get into it.",
    "no_space": "There is no room to lie down here.",
    "missing_rope": "A hammock needs a rope and nail strung above it.",
    "not_now": "Not at this hour. A bag is for the night and a hammock for "
               "the day.",
    "not_safe": "Something is too close for sleeping.",
}

LINES = {
    "itemGroup.elysiumcomforts": "Elysium Comforts",
    "elysiumcomforts.grave.made": "Your things are at %s, %s, %s — %s stack(s).",
    "elysiumcomforts.grave.theirs": "%s's grave. Anybody may open it in about %s "
                                    "minute(s).",
    "elysiumcomforts.loot.yours": "Yours",
    "item.elysiumcomforts.trader_crate": "Trader's Crate",
    "item.elysiumcomforts.trader_crate_full": "Trader's Crate",
    "elysiumcomforts.trader.empty": "Empty. Use it on a villager.",
    "elysiumcomforts.trader.carrying": "Carrying a %s, level %s",
    "elysiumcomforts.trader.restocked": "Restocked %s time(s) today",
    "item.elysiumcomforts.address_tag": "Address Tag",
    "item.elysiumcomforts.seal_stamp": "Seal Stamp",
    "item.elysiumcomforts.to": "To: %s",
    "item.elysiumcomforts.blank": "Nothing written on it yet.",
    "item.elysiumcomforts.unknown": "Contents unknown.",
    "item.elysiumcomforts.sealed": "It is not addressed to you.",
    "block.elysiumcomforts.mailbox.empty": "Nothing has arrived.",
    "block.elysiumcomforts.mailbox.unnamed": "This box has no address yet.",
    "block.elysiumcomforts.mailbox.unaddressable": "Nothing the post will carry "
                                                   "is written on that tag.",
    "block.elysiumcomforts.mailbox.busy": "Something is already on its way here.",
    "block.elysiumcomforts.mailbox.levels": "Naming a box costs %s level(s).",
    "block.elysiumcomforts.mailbox.named": "This box is now %s.",
    "block.elysiumcomforts.mailbox.posted": "Posted. About %s second(s).",
}


#: One line per kind of mail and per bird. Walked out of the rules rather than
#: listed, because a kind with no name shows a player the raw registry id, which
#: is the one thing in a pack that always looks broken.
def post_lines():
    made = {}
    for one in MAIL:
        name = MAIL_NAMES[one[0]]
        made[f"item.{NS}.{name}"] = titled(name)
    for one in BIRDS:
        made[f"entity.{NS}.{one}"] = titled(one)
        made[f"item.{NS}.{one}_spawn_egg"] = titled(one) + " Spawn Egg"
    return made


def language():
    entries = dict(LINES)
    entries.update(post_lines())
    for refusal, words in REFUSAL_WORDS.items():
        entries[f"rest.{NS}.{refusal}"] = words
    for name, shown in NAMES.items():
        entries[f"block.{NS}.{name}"] = shown
    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(entries.items())))
    return len(entries)


if __name__ == "__main__":
    sprites = draw_all()
    models = models_and_states()
    data = data_files()
    keys = language()
    print(f"comforts: {sprites} sprite(s), {models} block(s) modelled and stated, "
          f"{data} data file(s), {keys} language key(s)")
