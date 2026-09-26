#!/usr/bin/env python3
"""
Every file seventy-five bees need and none of them is Java.

    python3 tools/gen_bees.py

The roster is read out of BeeRules.java. Nothing here has its own list of
bees, so a bee added to the rules appears here, in the lang file, in the loot
tables and on a texture without anybody remembering to come and do it — and a
bee removed disappears the same way. validate.py regenerates the whole set
into a temporary tree and compares it byte for byte against what is committed.

The colours are the ones BeeItems.java computes for the spawn eggs, using the
same hash, so a comb and the egg that gives you the bee that makes it are the
same colour. validate.py checks the two implementations agree rather than
trusting this comment.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumagronomy"

OUT = os.environ.get("ELYSIUM_BEES_OUT", MOD)
ASSETS = os.path.join(OUT, "src/main/resources/assets", NS)
DATA = os.path.join(OUT, "src/main/resources/data", NS)

RULES = os.path.join(MOD, "src/main/java/com/elysium/agronomy/rules/BeeRules.java")

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


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


# ---------------------------------------------------------------------------
# What the Java says
# ---------------------------------------------------------------------------

SOURCE = read(RULES)

WILD = re.findall(r'wild\("([a-z_]+)", "([a-z_]+)", "([a-z_]+)"\)', SOURCE)
RESOURCE = re.findall(r'resource\("([a-z_]+)", "([a-z_]+)", "([a-z_]+)"\)', SOURCE)
PLACE = re.findall(
    r'place\("([a-z_]+)", "([a-z_]+)", "([a-z_]+)", "([a-z_]+)", "([a-z_]+)"\)', SOURCE)
PRODUCT = re.findall(
    r'product\("([a-z_]+)", "([a-z_]+)", "([a-z_]+)", "([a-z_]+)"\)', SOURCE)

BEES = []
for bid, produce, home in WILD:
    BEES.append({"id": bid, "kind": "wild", "produce": produce,
                 "parents": [], "home": home})
for bid, first, second in RESOURCE:
    BEES.append({"id": bid, "kind": "resource", "produce": bid,
                 "parents": [first, second], "home": ""})
for bid, home, produce, first, second in PLACE:
    BEES.append({"id": bid, "kind": "place", "produce": produce,
                 "parents": [first, second], "home": home})
for bid, produce, first, second in PRODUCT:
    BEES.append({"id": bid, "kind": "product", "produce": produce,
                 "parents": [first, second], "home": ""})

if not BEES:
    print("no bees were read out of BeeRules.java — the roster's shape has changed")
    sys.exit(2)

HOMES = sorted({bee["home"] for bee in BEES if bee["home"]})

# ---------------------------------------------------------------------------
# Where the wild bees are, read out of KeepingRules
# ---------------------------------------------------------------------------

KEEPING = read(os.path.join(
    MOD, "src/main/java/com/elysium/agronomy/rules/KeepingRules.java"))
BIOME_OF = dict(re.findall(r'biome\("([a-z_]+)", "([^"]+)"\);', KEEPING))


def keeping_number(name):
    found = re.search(r"public static final int " + name + r" = ([\d_]+);", KEEPING)
    if not found:
        print("KeepingRules no longer says", name)
        sys.exit(2)
    return int(found.group(1).replace("_", ""))


WILD_WEIGHT = keeping_number("WILD_SPAWN_WEIGHT")
WILD_LEAST = keeping_number("WILD_SPAWN_LEAST")
WILD_MOST = keeping_number("WILD_SPAWN_MOST")
NEST_BEES = keeping_number("NEST_BEES")
NEST_CHUNKS = keeping_number("NEST_CHUNKS")

#: KeepingRules.nested(): every home a wild bee lives in, once each, sorted.
NESTED = sorted({bee["home"] for bee in BEES if bee["kind"] == "wild"})
if not NESTED or any(home not in BIOME_OF for home in HOMES):
    print("KeepingRules does not give every home a biome: "
          f"{[home for home in HOMES if home not in BIOME_OF]}")
    sys.exit(2)


def wild_world():
    """
    Where the wild bees are found: a spawn and a nest in each one's biome.

    <p>An add-spawns modifier per wild bee, in its home's biome, and for each
    home a wild bee lives in, a nest generated on the surface there, full.
    Bred bees get neither: a bred bee found in the world is a breeding tree
    with a shortcut, which KeepingRules says and the harness checks.
    """
    written = 0
    for bee in BEES:
        if bee["kind"] != "wild":
            continue
        write(os.path.join(DATA, "neoforge/biome_modifier",
                           "bee_spawn_" + bee["id"] + ".json"), {
            "type": "neoforge:add_spawns",
            "biomes": BIOME_OF[bee["home"]],
            "spawners": [{"type": f"{NS}:{bee['id']}_bee", "weight": WILD_WEIGHT,
                          "minCount": WILD_LEAST, "maxCount": WILD_MOST}],
        })
        written += 1
    for home in NESTED:
        name = home + "_nest"
        write(os.path.join(DATA, "worldgen/configured_feature", name + ".json"), {
            "type": "minecraft:simple_block",
            "config": {"to_place": {
                "type": "minecraft:simple_state_provider",
                "state": {"Name": f"{NS}:{name}",
                          "Properties": {"bees": str(NEST_BEES)}}}},
        })
        write(os.path.join(DATA, "worldgen/placed_feature", name + ".json"), {
            "feature": f"{NS}:{name}",
            "placement": [
                {"type": "minecraft:rarity_filter", "chance": NEST_CHUNKS},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
                {"type": "minecraft:biome"},
                {"type": "minecraft:block_predicate_filter",
                 "predicate": {"type": "minecraft:all_of", "predicates": [
                     {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                     {"type": "minecraft:solid", "offset": [0, -1, 0]}]}},
            ],
        })
        write(os.path.join(DATA, "neoforge/biome_modifier", name + ".json"), {
            "type": "neoforge:add_features",
            "biomes": BIOME_OF[home],
            "features": f"{NS}:{name}",
            "step": "vegetal_decoration",
        })
        written += 3
    return written


#: What the jar, the hive, the scoop, the smoker and the nest say.
SAYINGS = {
    f"{NS}.jar.holds": "Holds: %s",
    f"{NS}.jar.empty": "Empty: use on a bee, a nest or a hive",
    f"{NS}.hive.no_species": "No bee here a jar can take",
    f"{NS}.hive.jarred": "Took a %s out",
    f"{NS}.hive.moved_in": "A %s moved in",
    f"{NS}.hive.paired": "%s and %s: a treat will breed them",
    f"{NS}.hive.refused": "The hive has no room for a %s",
    f"{NS}.hive.bred": "A %s was bred",
    f"{NS}.hive.no_pair": "Nothing here to breed: two of a bee, or a pair the "
                          "breeding tree names",
    f"{NS}.scoop.not_ready": "Not done yet: %s%% of a comb",
    f"{NS}.smoker.calmed": "%s bee(s) calmed for %s seconds",
    f"{NS}.nest.bred": "Nothing wild lives in this nest",
    f"{NS}.nest.empty": "The nest is empty; one grows back every %s seconds",
}


# ---------------------------------------------------------------------------
# The machinery: the housing ladder, the upgrades and the centrifuge
# ---------------------------------------------------------------------------

APIARY_RULES = read(os.path.join(
    MOD, "src/main/java/com/elysium/agronomy/rules/ApiaryRules.java"))

HOUSINGS = [{"id": one[0], "bees": int(one[1]) if one[1] else 0,
             "slots": int(one[2]), "from": one[3]}
            for one in re.findall(
                r'new Housing\("([a-z_]+)",\s*(?:AgronomyRules\.MOST_BEES|(\d+))'
                r',\s*(\d+),\s*"([a-z_]*)"\)', APIARY_RULES)]
# MOST_BEES is a name rather than a number in the first rung, and a regex that
# cannot read it reads a blank. The number itself lives in AgronomyRules.
MOST_BEES = re.search(r"MOST_BEES = (\d+);", read(os.path.join(
    MOD, "src/main/java/com/elysium/agronomy/rules/AgronomyRules.java")))
if not MOST_BEES:
    print("AgronomyRules no longer says how many bees a hive holds")
    sys.exit(2)
MOST_BEES = int(MOST_BEES.group(1))
for one in HOUSINGS:
    if not one["bees"]:
        one["bees"] = MOST_BEES

UPGRADES = [{"id": one[0], "bees": int(one[1]), "faster": float(one[2]),
             "attention": one[3]}
            for one in re.findall(
                r'new Upgrade\("([a-z_]+)",\s*(\d+),\s*([\d.]+)F,\s*"([a-z ]*)"\)',
                APIARY_RULES)]

CENTRIFUGE = re.search(r'CENTRIFUGE = "([a-z_]+)"', APIARY_RULES)
if not HOUSINGS or not UPGRADES or not CENTRIFUGE:
    print("ApiaryRules no longer says what the machinery is; read "
          f"{len(HOUSINGS)} housing(s), {len(UPGRADES)} upgrade(s)")
    sys.exit(2)
CENTRIFUGE = CENTRIFUGE.group(1)

# Every material this module hands over as a chunk: the seeds' own list plus
# every material a resource bee names. Read rather than listed, because a
# comb that spins into a chunk nobody registered is a centrifuge that eats
# combs and gives nothing back.
SEED_RULES = read(os.path.join(
    MOD, "src/main/java/com/elysium/agronomy/rules/SeedRules.java"))
# SeedRules.chunked() is the seeds whose result this module has to make
# itself, which it spots by the result being in our own namespace.
SEEDS_CHUNKED = [material for material, makes
                 in re.findall(r'mined\("([a-z_]+)",\s*\d+,\s*"([a-z:_]+)"\)',
                               SEED_RULES)
                 if makes.startswith(NS + ":")]
#: The chunks that are ours to draw and nobody else's.
#:
#: gen_agronomy.py already draws, models and tags the seeds' seventeen. Two
#: generators writing one file is a file whose contents depend on which ran
#: last, and the answer to "which ran last" is in a shell script somewhere.
#: So this one takes the materials the bees add and leaves the rest alone.
CHUNKED = [bee["produce"] for bee in BEES
           if bee["kind"] == "resource" and bee["produce"] not in SEEDS_CHUNKED]
CHUNKED = sorted(dict.fromkeys(CHUNKED))
if not CHUNKED:
    print("no material is handed over as a chunk by a bee, so either the "
          "roster has no resource bees left or the seeds already cover all of "
          "them and this generator has nothing to do")
    sys.exit(2)

KIND_HUE = {}
for kind, colour in re.findall(r'BeeRules\.([A-Z]+),\s*0x([0-9A-Fa-f]{6})', SOURCE):
    pass
# The hues live in BeeItems.java, which is the file that uses them.
ITEMS_JAVA = read(os.path.join(
    MOD, "src/main/java/com/elysium/agronomy/registry/BeeItems.java"))
for kind, colour in re.findall(
        r'BeeRules\.([A-Z]+),\s*0x([0-9A-Fa-f]{6})', ITEMS_JAVA):
    KIND_HUE[kind.lower()] = int(colour, 16)

if len(KIND_HUE) != 4:
    print("expected four kind hues in BeeItems.java, read", KIND_HUE)
    sys.exit(2)


# ---------------------------------------------------------------------------
# The colour, which has to be the one the Java computes
# ---------------------------------------------------------------------------

def java_hash(text):
    """String.hashCode, overflow and all."""
    value = 0
    for char in text:
        value = (value * 31 + ord(char)) & 0xFFFFFFFF
    if value >= 0x80000000:
        value -= 0x100000000
    return abs(value)


def shade(name, base, spread):
    value = java_hash(name)
    span = 2 * spread + 1
    red = min(255, max(0, ((base >> 16) & 0xFF) + (value % span) - spread))
    green = min(255, max(0, ((base >> 8) & 0xFF) + ((value // 7) % span) - spread))
    blue = min(255, max(0, (base & 0xFF) + ((value // 53) % span) - spread))
    return red, green, blue


def body_colour(bee):
    return shade(bee["id"], KIND_HUE[bee["kind"]], 26)


def comb_colour(bee):
    return shade(bee["produce"], 0xF2E4A8, 34)


def darker(colour, by=40):
    return tuple(max(0, channel - by) for channel in colour)


def lighter(colour, by=34):
    return tuple(min(255, channel + by) for channel in colour)


# ---------------------------------------------------------------------------
# Sprites
# ---------------------------------------------------------------------------

def comb_sprite(bee):
    """A hex comb in the colour of what it holds."""
    core = comb_colour(bee)
    edge = darker(core, 56)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for row in range(2, 14):
        inset = 2 + abs(8 - row) // 3
        draw.line([(inset, row), (15 - inset, row)], fill=core + (255,))
    draw.line([(4, 2), (11, 2)], fill=edge + (255,))
    draw.line([(4, 13), (11, 13)], fill=edge + (255,))
    for row in (5, 8, 11):
        draw.line([(5, row), (10, row)], fill=edge + (255,))
    return image


def comb_block_sprite(bee):
    """Four cells of it, which is what nine combs pressed together look like."""
    core = comb_colour(bee)
    edge = darker(core, 62)
    image = Image.new("RGBA", (16, 16), core + (255,))
    draw = ImageDraw.Draw(image)
    for offset in (0, 8):
        draw.rectangle([offset, 0, offset + 7, 7], outline=edge + (255,))
        draw.rectangle([offset, 8, offset + 7, 15], outline=edge + (255,))
    draw.line([(0, 0), (15, 0)], fill=lighter(core, 22) + (255,))
    return image


def bee_sprite(bee):
    """The creature, unwrapped the way BeeModel asks for it.

    A box needs 2*(width+depth) across and height+depth down, and the model
    puts the body at texOffs(0, 0) and the two wings at (0, 12) and (0, 18).
    Those rectangles are filled here and nowhere else; validate.py reads the
    offsets back out of BeeModel.java and checks they still say that, because
    a model and a texture that disagree render as garbage rather than as an
    error.
    """
    body = body_colour(bee)
    band = darker(body, 70)
    pale = lighter(body, 30)
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    # Body: width 6, height 5, depth 6, at (0, 0).
    w, h, d = 6, 5, 6
    draw.rectangle([d, 0, d + w - 1, d - 1], fill=pale + (255,))          # top
    draw.rectangle([d + w, 0, d + 2 * w - 1, d - 1], fill=band + (255,))  # bottom
    draw.rectangle([0, d, d - 1, d + h - 1], fill=body + (255,))          # right
    draw.rectangle([d, d, d + w - 1, d + h - 1], fill=body + (255,))      # front
    draw.rectangle([d + w, d, d + w + d - 1, d + h - 1], fill=body + (255,))
    draw.rectangle([d + w + d, d, d + w + d + w - 1, d + h - 1], fill=body + (255,))
    # Stripes across every side face, which is what makes it read as a bee.
    for row in (d + 1, d + 3):
        draw.line([(0, row), (d + w + d + w - 1, row)], fill=band + (255,))
    # Two eyes on the front face only.
    draw.point((d + 1, d + 1), fill=(20, 20, 24, 255))
    draw.point((d + w - 2, d + 1), fill=(20, 20, 24, 255))

    # Wings: width 5, height 1, depth 4, at (0, 12) and (0, 18).
    ww, wh, wd = 5, 1, 4
    film = (232, 238, 246)
    for top in (12, 18):
        draw.rectangle([wd, top, wd + ww - 1, top + wd - 1], fill=film + (210,))
        draw.rectangle([wd + ww, top, wd + 2 * ww - 1, top + wd - 1],
                       fill=film + (170,))
        draw.rectangle([0, top + wd, 2 * (ww + wd) - 1, top + wd + wh - 1],
                       fill=film + (190,))
        for column in range(wd, wd + ww, 2):
            draw.line([(column, top), (column, top + wd - 1)],
                      fill=darker(film, 40) + (210,))
    return image


def nest_sprite(home, face):
    """A nest, which is per place rather than per bee."""
    base = shade(home, 0x8A6A44, 30)
    grain = darker(base, 34)
    image = Image.new("RGBA", (16, 16), base + (255,))
    draw = ImageDraw.Draw(image)
    if face == "top":
        for row in range(0, 16, 3):
            draw.line([(0, row), (15, row)], fill=grain + (255,))
    else:
        for column in range(0, 16, 4):
            draw.line([(column, 0), (column, 15)], fill=grain + (255,))
        if face == "front":
            draw.rectangle([5, 9, 10, 13], fill=(30, 22, 16, 255))
            draw.rectangle([6, 10, 9, 12], fill=(12, 9, 7, 255))
    return image


def tool_sprite(name):
    palette = {
        "bee_scoop": ((198, 182, 150), (120, 96, 62)),
        "bee_smoker": ((150, 150, 158), (86, 86, 92)),
        "bee_jar": ((198, 216, 228), (120, 148, 170)),
        "bee_treat": ((228, 190, 106), (162, 120, 52)),
    }
    core, edge = palette[name]
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    if name == "bee_scoop":
        draw.rectangle([4, 3, 11, 8], fill=core + (255,), outline=edge + (255,))
        draw.line([(7, 9), (7, 14)], fill=edge + (255,))
        draw.line([(8, 9), (8, 14)], fill=edge + (255,))
    elif name == "bee_smoker":
        draw.rectangle([5, 5, 10, 14], fill=core + (255,), outline=edge + (255,))
        draw.rectangle([6, 2, 9, 4], fill=edge + (255,))
        draw.point((7, 1), fill=(200, 200, 200, 160))
    elif name == "bee_jar":
        draw.rectangle([4, 4, 11, 14], fill=core + (200,), outline=edge + (255,))
        draw.rectangle([6, 2, 9, 3], fill=edge + (255,))
    else:
        draw.ellipse([3, 4, 12, 13], fill=core + (255,), outline=edge + (255,))
        draw.line([(6, 7), (9, 10)], fill=edge + (255,))
        draw.line([(9, 7), (6, 10)], fill=edge + (255,))
    return image


def housing_sprite(housing, face):
    """
    A rung of the ladder, drawn from how much it holds.

    <p>The deeper the rung the more slots and the colder the material, so the
    three blocks read apart on a wall without anybody choosing three
    palettes: timber for the ones you make early, metal for the ones you make
    after a factory.
    """
    metal = housing["slots"] >= 4
    base = (118, 126, 134) if metal else (152, 116, 74)
    band = darker(base, 34)
    face_colour = lighter(base, 24)
    image = Image.new("RGBA", (16, 16), base + (255,))
    draw = ImageDraw.Draw(image)
    if face == "top":
        draw.rectangle([2, 2, 13, 13], fill=face_colour + (255,))
        for row in range(3, 13, 3):
            draw.line([(2, row), (13, row)], fill=band + (255,))
        return image
    for row in range(0, 16, 5):
        draw.line([(0, row), (15, row)], fill=band + (255,))
    if face == "front":
        # One opening per bee-slot pair, so a bigger rung looks bigger.
        holes = min(4, max(1, housing["slots"] // 2 + 1))
        for at in range(holes):
            left = 2 + at * (12 // holes)
            draw.rectangle([left, 9, left + 2, 12], fill=(28, 20, 12, 255))
        draw.rectangle([3, 3, 12, 7], fill=face_colour + (255,))
        draw.rectangle([4, 4, 11, 6], fill=band + (255,))
    return image


def centrifuge_sprite(face):
    """A drum that spins. Metal, with a rim, and a lid you can see is a lid."""
    base = (122, 128, 136)
    rim = darker(base, 40)
    shine = lighter(base, 40)
    image = Image.new("RGBA", (16, 16), base + (255,))
    draw = ImageDraw.Draw(image)
    if face == "top":
        draw.ellipse([2, 2, 13, 13], fill=shine + (255,), outline=rim + (255,))
        draw.ellipse([6, 6, 9, 9], fill=rim + (255,))
        return image
    draw.rectangle([0, 1, 15, 2], fill=rim + (255,))
    draw.rectangle([0, 13, 15, 14], fill=rim + (255,))
    draw.rectangle([3, 5, 12, 10], fill=shine + (255,), outline=rim + (255,))
    for column in range(4, 12, 3):
        draw.line([(column, 5), (column, 10)], fill=rim + (255,))
    return image


def upgrade_sprite(upgrade):
    """
    An upgrade, drawn from what it does rather than from a palette.

    <p>The two that earn are warm and the five that buy attention are cool,
    so a row of them in a chest says at a glance which kind each one is —
    which is the one thing about this system a player has to hold in their
    head.
    """
    earns = upgrade["bees"] > 0 or upgrade["faster"] > 1.0
    base = shade(upgrade["id"], 0xC08A3E if earns else 0x5E7E96, 26)
    edge = darker(base, 44)
    core = lighter(base, 40)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 2, 13, 13], fill=base + (255,), outline=edge + (255,))
    draw.rectangle([4, 4, 11, 11], fill=core + (255,), outline=edge + (255,))
    # A mark per upgrade, from its own name, so no two are the same card.
    mark = java_hash(upgrade["id"]) % 4
    if mark == 0:
        draw.line([(5, 8), (10, 8)], fill=edge + (255,))
        draw.line([(8, 5), (8, 10)], fill=edge + (255,))
    elif mark == 1:
        draw.line([(5, 5), (10, 10)], fill=edge + (255,))
        draw.line([(10, 5), (5, 10)], fill=edge + (255,))
    elif mark == 2:
        draw.ellipse([6, 6, 9, 9], outline=edge + (255,))
    else:
        draw.rectangle([6, 6, 9, 9], fill=edge + (255,))
    return image


def chunk_sprite(material):
    """A lump of a material, its colour from its own name."""
    base = shade(material, 0x9A8E7E, 60)
    edge = darker(base, 46)
    shine = lighter(base, 46)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.polygon([(4, 11), (3, 6), (7, 3), (12, 5), (13, 10), (9, 13)],
                 fill=base + (255,), outline=edge + (255,))
    draw.polygon([(6, 9), (5, 7), (8, 5), (10, 7), (9, 10)], fill=shine + (255,))
    return image


def save(image, *parts):
    path = os.path.join(*parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)


def draw_all():
    for bee in BEES:
        save(comb_sprite(bee), ASSETS, "textures/item", bee["id"] + "_comb.png")
        save(comb_block_sprite(bee), ASSETS, "textures/block",
             bee["id"] + "_comb_block.png")
        save(bee_sprite(bee), ASSETS, "textures/entity/bee", bee["id"] + ".png")
    for home in HOMES:
        for face in ("top", "side", "front"):
            save(nest_sprite(home, face), ASSETS, "textures/block",
                 f"{home}_nest_{face}.png")
    for tool in ("bee_scoop", "bee_smoker", "bee_jar", "bee_treat"):
        save(tool_sprite(tool), ASSETS, "textures/item", tool + ".png")
    for housing in HOUSINGS:
        if not housing["from"]:
            continue
        for face in ("top", "side", "front"):
            save(housing_sprite(housing, face), ASSETS, "textures/block",
                 f"{housing['id']}_{face}.png")
    for face in ("top", "side"):
        save(centrifuge_sprite(face), ASSETS, "textures/block",
             f"{CENTRIFUGE}_{face}.png")
    for upgrade in UPGRADES:
        save(upgrade_sprite(upgrade), ASSETS, "textures/item",
             upgrade["id"] + ".png")
    for material in CHUNKED:
        save(chunk_sprite(material), ASSETS, "textures/item",
             material + "_chunk.png")


# ---------------------------------------------------------------------------
# Models, blockstates, loot and recipes
# ---------------------------------------------------------------------------

def flat(texture):
    return {"parent": "minecraft:item/generated",
            "textures": {"layer0": f"{NS}:item/{texture}"}}


def models_and_states():
    for bee in BEES:
        write(os.path.join(ASSETS, "models/item", bee["id"] + "_comb.json"),
              flat(bee["id"] + "_comb"))
        write(os.path.join(ASSETS, "models/item",
                           bee["id"] + "_bee_spawn_egg.json"),
              {"parent": "minecraft:item/template_spawn_egg"})
        name = bee["id"] + "_comb_block"
        write(os.path.join(ASSETS, "models/block", name + ".json"),
              {"parent": "minecraft:block/cube_all",
               "textures": {"all": f"{NS}:block/{name}"}})
        write(os.path.join(ASSETS, "blockstates", name + ".json"),
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})

    for home in HOMES:
        name = home + "_nest"
        write(os.path.join(ASSETS, "models/block", name + ".json"),
              {"parent": "minecraft:block/orientable",
               "textures": {"top": f"{NS}:block/{home}_nest_top",
                            "front": f"{NS}:block/{home}_nest_front",
                            "side": f"{NS}:block/{home}_nest_side"}})
        write(os.path.join(ASSETS, "blockstates", name + ".json"),
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})

    for tool in ("bee_scoop", "bee_smoker", "bee_jar", "bee_treat"):
        write(os.path.join(ASSETS, "models/item", tool + ".json"), flat(tool))

    # The machinery: three rungs of housing, a centrifuge, seven upgrades and
    # a chunk for every material a comb comes apart into.
    for housing in HOUSINGS:
        if not housing["from"]:
            continue
        name = housing["id"]
        write(os.path.join(ASSETS, "models/block", name + ".json"),
              {"parent": "minecraft:block/orientable",
               "textures": {"top": f"{NS}:block/{name}_top",
                            "front": f"{NS}:block/{name}_front",
                            "side": f"{NS}:block/{name}_side"}})
        write(os.path.join(ASSETS, "blockstates", name + ".json"),
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})
    write(os.path.join(ASSETS, "models/block", CENTRIFUGE + ".json"),
          {"parent": "minecraft:block/cube_bottom_top",
           "textures": {"top": f"{NS}:block/{CENTRIFUGE}_top",
                        "bottom": f"{NS}:block/{CENTRIFUGE}_top",
                        "side": f"{NS}:block/{CENTRIFUGE}_side"}})
    write(os.path.join(ASSETS, "blockstates", CENTRIFUGE + ".json"),
          {"variants": {"": {"model": f"{NS}:block/{CENTRIFUGE}"}}})
    write(os.path.join(ASSETS, "models/item", CENTRIFUGE + ".json"),
          {"parent": f"{NS}:block/{CENTRIFUGE}"})
    for upgrade in UPGRADES:
        write(os.path.join(ASSETS, "models/item", upgrade["id"] + ".json"),
              flat(upgrade["id"]))
    for material in CHUNKED:
        write(os.path.join(ASSETS, "models/item", material + "_chunk.json"),
              flat(material + "_chunk"))


def drops(name):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }


def loot():
    for bee in BEES:
        name = bee["id"] + "_comb_block"
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"), drops(name))
    for home in HOMES:
        name = home + "_nest"
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"), drops(name))
    for housing in HOUSINGS:
        if housing["from"]:
            write(os.path.join(DATA, "loot_table/blocks",
                               housing["id"] + ".json"), drops(housing["id"]))
    write(os.path.join(DATA, "loot_table/blocks", CENTRIFUGE + ".json"),
          drops(CENTRIFUGE))


def recipes():
    for bee in BEES:
        comb = f"{NS}:{bee['id']}_comb"
        block = f"{NS}:{bee['id']}_comb_block"
        write(os.path.join(DATA, "recipe", bee["id"] + "_comb_block.json"), {
            "type": "minecraft:crafting_shaped",
            "category": "building",
            "pattern": ["CCC", "CCC", "CCC"],
            "key": {"C": {"item": comb}},
            "result": {"id": block, "count": 1},
        })
        write(os.path.join(DATA, "recipe", bee["id"] + "_comb_from_block.json"), {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": [{"item": block}],
            "result": {"id": comb, "count": 9},
        })


def machinery_recipes():
    """
    The ladder, the centrifuge, the upgrades and what a chunk smelts into.

    <p>Every rung is built out of the rung below it, which is what makes it a
    ladder rather than four separate blocks — and it is checked, because a
    first rung built out of a later one is a loop nobody can start.
    """
    made = 0
    for housing in HOUSINGS:
        if not housing["from"]:
            continue
        metal = "minecraft:iron_ingot" if housing["slots"] < 4 \
            else "minecraft:copper_block"
        write(os.path.join(DATA, "recipe", housing["id"] + ".json"), {
            "type": "minecraft:crafting_shaped",
            "category": "building",
            "pattern": ["III", "IHI", "III"],
            "key": {"I": {"item": metal},
                    "H": {"item": f"{NS}:{housing['from']}"}},
            "result": {"id": f"{NS}:{housing['id']}", "count": 1},
        })
        made += 1
    write(os.path.join(DATA, "recipe", CENTRIFUGE + ".json"), {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["III", "IRI", "III"],
        "key": {"I": {"item": "minecraft:iron_ingot"},
                "R": {"item": "minecraft:redstone_block"}},
        "result": {"id": f"{NS}:{CENTRIFUGE}", "count": 1},
    })
    made += 1
    for upgrade in UPGRADES:
        earns = upgrade["bees"] > 0 or upgrade["faster"] > 1.0
        heart = "minecraft:gold_ingot" if earns else "minecraft:copper_ingot"
        write(os.path.join(DATA, "recipe", upgrade["id"] + ".json"), {
            "type": "minecraft:crafting_shaped",
            "category": "equipment",
            "pattern": [" W ", "WHW", " W "],
            "key": {"W": {"item": f"{NS}:wax"}, "H": {"item": heart}},
            "result": {"id": f"{NS}:{upgrade['id']}", "count": 1},
        })
        made += 1

    # Every chunk a bee adds carries the tag the foundry already smelts, so a
    # gold comb and a gold ore end up in the same furnace rather than in two.
    for material in CHUNKED:
        write(os.path.join(DATA, "../c/tags/item",
                           f"raw_materials/{material}.json"),
              {"replace": False, "values": [f"{NS}:{material}_chunk"]})
        made += 1

    # Every comb a centrifuge will take, in one tag, walked from the roster.
    write(os.path.join(DATA, "tags/item/combs.json"), {
        "replace": False,
        "values": [f"{NS}:{bee['id']}_comb" for bee in BEES] + [f"{NS}:comb"],
    })
    made += 1
    return made


def a_name(text):
    return " ".join(word.capitalize() for word in text.split("_"))


def lang():
    words = {}
    for bee in BEES:
        pretty = a_name(bee["id"])
        words[f"entity.{NS}.{bee['id']}_bee"] = pretty + " Bee"
        words[f"item.{NS}.{bee['id']}_comb"] = pretty + " Comb"
        words[f"item.{NS}.{bee['id']}_bee_spawn_egg"] = pretty + " Bee Spawn Egg"
        words[f"block.{NS}.{bee['id']}_comb_block"] = "Block of " + pretty + " Comb"
    for home in HOMES:
        words[f"block.{NS}.{home}_nest"] = a_name(home) + " Nest"
    words[f"item.{NS}.bee_scoop"] = "Comb Scoop"
    words[f"item.{NS}.bee_smoker"] = "Hive Smoker"
    words[f"item.{NS}.bee_jar"] = "Bee Jar"
    words[f"item.{NS}.bee_treat"] = "Bee Treat"
    for housing in HOUSINGS:
        if housing["from"]:
            words[f"block.{NS}.{housing['id']}"] = a_name(housing["id"])
    words[f"block.{NS}.{CENTRIFUGE}"] = a_name(CENTRIFUGE)
    for upgrade in UPGRADES:
        words[f"item.{NS}.{upgrade['id']}"] = a_name(upgrade["id"])
    for material in CHUNKED:
        words[f"item.{NS}.{material}_chunk"] = a_name(material) + " Chunk"
    words[f"{NS}.centrifuge.empty"] = "Nothing in it to spin"
    words[f"{NS}.centrifuge.dark"] = "No power: the drum is still"
    words[f"{NS}.centrifuge.running"] = "Spinning %s comb(s), %s of full speed"
    words[f"{NS}.hive.upgraded"] = "%s bees, %s upgrade(s), %s combs an hour"
    words.update(SAYINGS)

    # The base is always the committed file, never whatever happens to be in
    # the output tree. validate.py regenerates into a temporary directory and
    # compares byte for byte; reading the base from there would make the two
    # runs differ for no reason but where they were pointed.
    # The base is the language file as it stands in the output tree if one is
    # there, and the committed one otherwise.
    #
    # Six generators write this file and each keeps the keys it does not own,
    # so they have to compose: the first starts from what is committed and
    # every one after it starts from what the one before wrote. Starting every
    # one of them from the committed file instead means the last to run puts
    # back whatever is in it — including a value somebody edited by hand,
    # which then matches itself and nothing reports it. That hid a hand-edited
    # line for as long as this file has had six generators writing it.
    committed = os.path.join(MOD, "src/main/resources/assets", NS,
                             "lang/en_us.json")
    standing = os.path.join(ASSETS, "lang/en_us.json")
    if os.path.exists(standing):
        committed = standing
    path = os.path.join(ASSETS, "lang/en_us.json")
    existing = {}
    if os.path.exists(committed):
        existing = json.loads(read(committed))
    # Anything this generator used to write and no longer does goes, so a
    # removed bee does not leave its name behind in the language file.
    mine = {key for key in existing
            if re.match(rf"^(entity|item|block)\.{NS}\.[a-z_]+"
                        r"(_bee|_comb|_comb_block|_bee_spawn_egg|_nest)$", key)
            or key in (f"item.{NS}.bee_scoop", f"item.{NS}.bee_smoker",
                       f"item.{NS}.bee_jar", f"item.{NS}.bee_treat")
            or key in words}
    for key in mine:
        existing.pop(key, None)
    existing.update(words)
    write(path, dict(sorted(existing.items())))


def main():
    draw_all()
    models_and_states()
    loot()
    recipes()
    machined = machinery_recipes()
    wild_world()
    lang()
    print(f"bees: {len(BEES)} species, {len(HOMES)} nest(s), "
          f"{len(BEES) * 3 + len(HOMES) * 3 + 4 + (len(HOUSINGS) - 1) * 3 + 2 + len(UPGRADES) + len(CHUNKED)} texture(s), "
          f"{len(BEES) * 2 + machined} recipe(s); the machinery is "
          f"{len(HOUSINGS)} housing(s), {len(UPGRADES)} upgrade(s), a "
          f"centrifuge and {len(CHUNKED)} chunk(s)")


if __name__ == "__main__":
    main()
