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
OVERWORLD_RULES = os.path.join(
    MOD, "src/main/java/com/elysium/wilds/rules/OverworldRules.java")
DATA_IN = os.path.join(HERE, "data")
VANILLA_LIST = os.path.join(DATA_IN, "overworld-vanilla.tsv")
VANILLA_FEATURES = os.path.join(DATA_IN, "biome-features.tsv")
VANILLA_PRESETS = os.path.join(DATA_IN, "world_preset")
MINECRAFT = os.path.join(OUT, "src/main/resources/data/minecraft")
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


def write_tight(path, obj):
    """
    A file the game reads and nobody ever opens.

    The three world presets carry 8,433 climate entries apiece. Written the
    way every other file here is written — two spaces of indent, one number to
    a line — that is six megabytes each and eighteen in the jar, for a file
    whose entire readership is a codec. Written tight it is a third of that.
    """
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(obj, handle, separators=(",", ":"))
        handle.write("\n")


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


# ---------------------------------------------------------------------------
# The Vale
# ---------------------------------------------------------------------------
#
# Fifteen biomes, a dimension and a dimension type, written out of ValeRules
# rather than by hand. Fifteen biome files written by hand have two identical
# ones in them and nobody ever finds out which two; fifteen written from a
# crossing cannot.

VALE_RULES = os.path.join(
    MOD, "src/main/java/com/elysium/wilds/rules/ValeRules.java")


def vale():
    """The bands, the names and what each biome is, read out of ValeRules."""
    source = open(VALE_RULES, encoding="utf-8").read()
    warmths = re.search(r"WARMTHS = (\d+);", source)
    wets = re.search(r"WETS = (\d+);", source)
    # Not "NAMES", which WARMTH_NAMES and WET_NAMES both end with: a regex
    # defeated by a prefix is a regex that reads the wrong list and reports a
    # smaller world that is perfectly valid.
    listed = re.search(r"(?<![A-Z_])NAMES = List\.of\((.*?)\);", source, re.S)
    if not (warmths and wets and listed):
        print("the Vale's rules no longer say how it is cut up")
        sys.exit(1)
    warmths = int(warmths.group(1))
    wets = int(wets.group(1))
    names = re.findall(r'"([a-z_]+)"', listed.group(1))
    if len(names) != warmths * wets:
        print(f"the Vale crosses {warmths} x {wets} bands and this file could read "
              f"{len(names)} name(s); a biome would be unnamed and the one that "
              f"is unnamed is the one that never generates")
        sys.exit(1)
    found = []
    at = 0
    for warmth in range(warmths):
        for wet in range(wets):
            found.append((names[at], warmth, wet))
            at += 1
    return found, warmths, wets


def band_edge(at, bands):
    """The same edge ValeRules works out, so the data and the proof agree."""
    return -1.0 + 2.0 * at / bands


def mix(low, high, at, bands):
    """
    The Vale's ramps, mixed the way ValeRules mixes them.

    One line, and it used to be a different line: Python's floor division
    against Java's truncation, which differ by one whenever the ramp runs
    downhill. The Vale's files were written with one and proved with the
    other for as long as the Vale has existed, and nothing found it because
    nothing compared them. It is one call now so that there is one of it.
    """
    return blend(low, high, at, bands)


def vale_colours(warmth, wet, warmths, wets):
    sky = ((mix(0x88, 0xE8, warmth, warmths) << 16)
           | (mix(0xB8, 0x9C, warmth, warmths) << 8)
           | mix(0xF0, 0x60, warmth, warmths))
    fog = ((mix(0xB0, 0xD8, warmth, warmths) << 16)
           | (mix(0xC8, 0xB0, warmth, warmths) << 8)
           | mix(0xE8, 0x88, warmth, warmths))
    water = ((mix(0x3A, 0x2E, warmth, warmths) << 16)
             | (mix(0x6E, 0x7A, warmth, warmths) << 8)
             | mix(0xB4, 0x5E, warmth, warmths))
    grass = ((mix(0xB8, 0x4E, wet, wets) << 16)
             | (mix(0x9C, 0x9E, wet, wets) << 8)
             | mix(0x74, 0x3A, wet, wets))
    foliage = ((mix(0xA4, 0x36, wet, wets) << 16)
               | (mix(0x92, 0x8A, wet, wets) << 8)
               | mix(0x6A, 0x30, wet, wets))
    return sky, fog, water, grass, foliage


#: What lives in each band of the Vale, as (what, most, fewest, weight). Cold
#: and dry is empty on purpose: a world where every band has the same animals
#: in it is one band.
#:
#: The order of those two numbers is not obvious and getting it round the
#: wrong way is not a crash: the game logs a line and carries on with the
#: spawner dropped, so the biome is simply empty and nothing in the world says
#: why. It shipped that way once, for three biomes, and the load test caught
#: it.
VALE_CREATURES = [
    [],
    [("minecraft:rabbit", 3, 2, 6), ("minecraft:fox", 2, 2, 4)],
    [("minecraft:sheep", 4, 4, 10), ("minecraft:cow", 4, 4, 8),
     ("minecraft:chicken", 4, 4, 8)],
    [("minecraft:llama", 4, 2, 5), ("minecraft:horse", 6, 2, 4)],
    [("minecraft:strider", 2, 1, 3)],
]


def vale_biome(name, warmth, wet, warmths, wets):
    sky, fog, water, grass, foliage = vale_colours(warmth, wet, warmths, wets)
    creatures = [{"type": kind, "maxCount": most, "minCount": least,
                  "weight": weight}
                 for kind, most, least, weight in VALE_CREATURES[warmth]]
    return {
        "has_precipitation": wet > 0,
        "temperature": round(-0.5 + warmth * (2.5 / (warmths - 1)), 3),
        "downfall": round(wet * (1.0 / (wets - 1)), 3),
        "effects": {
            "sky_color": sky, "fog_color": fog, "water_color": water,
            "water_fog_color": water, "grass_color": grass,
            "foliage_color": foliage,
            "mood_sound": {"block_search_extent": 8, "offset": 2.0,
                           "sound": "minecraft:ambient.cave",
                           "tick_delay": 6000},
        },
        "spawners": {"monster": [], "creature": creatures, "ambient": [],
                     "water_creature": [], "water_ambient": [],
                     "underground_water_creature": [], "axolotls": [],
                     "misc": []},
        "spawn_costs": {},
        "carvers": {"air": ["minecraft:cave", "minecraft:cave_extra_underground",
                            "minecraft:canyon"]},
        "features": [[], [], [], [], [], [], [], [], [], [], []],
    }


def vale_files(found, warmths, wets):
    """Every biome of the Vale, the dimension and the type it is."""
    data = os.path.join(MOD, "src/main/resources/data", NS)
    written = 0
    claims = []
    for name, warmth, wet in found:
        id = "vale_" + name
        write(os.path.join(data, "worldgen/biome", id + ".json"),
              vale_biome(name, warmth, wet, warmths, wets))
        written += 1
        claims.append({
            "biome": f"{NS}:{id}",
            "parameters": {
                "temperature": [round(band_edge(warmth, warmths), 4),
                                round(band_edge(warmth + 1, warmths), 4)],
                "humidity": [round(band_edge(wet, wets), 4),
                             round(band_edge(wet + 1, wets), 4)],
                "continentalness": [-1.0, 1.0],
                "erosion": [-1.0, 1.0],
                "weirdness": [-1.0, 1.0],
                "depth": 0.0,
                "offset": 0.0,
            },
        })
    write(os.path.join(data, "dimension/vale.json"), {
        "type": f"{NS}:vale",
        "generator": {
            "type": "minecraft:noise",
            "settings": "minecraft:overworld",
            "biome_source": {"type": "minecraft:multi_noise", "biomes": claims},
        },
    })
    write(os.path.join(data, "dimension_type/vale.json"), {
        "ambient_light": 0.0, "bed_works": True, "coordinate_scale": 1.0,
        "effects": "minecraft:overworld", "has_ceiling": False,
        "has_raids": False, "has_skylight": True, "height": 384,
        "infiniburn": "#minecraft:infiniburn_overworld", "logical_height": 384,
        "min_y": -64, "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": {"type": "minecraft:uniform",
                                      "max_inclusive": 7, "min_inclusive": 0},
        "natural": True, "piglin_safe": False, "respawn_anchor_works": False,
        "ultrawarm": False,
    })
    return written + 2


VALE_WORDS = {
    "rime_barrens": "Rime Barrens", "glacier_vale": "Glacier Vale",
    "frostmere": "Frostmere", "ashen_steppe": "Ashen Steppe",
    "pinewold": "Pinewold", "coldmarsh": "Coldmarsh",
    "chalk_downs": "Chalk Downs", "verdant_vale": "Verdant Vale",
    "bloomfen": "Bloomfen", "amber_scrub": "Amber Scrub",
    "goldwood": "Goldwood", "reedbasin": "Reedbasin",
    "cinder_flats": "Cinder Flats", "emberwood": "Emberwood",
    "sulphur_springs": "Sulphur Springs",
}


def vale_language(entries, found):
    entries["dimension.%s.vale" % NS] = "The Vale"
    for name, _warmth, _wet in found:
        shown = VALE_WORDS.get(name)
        if shown is None:
            print(f"{name} has no name here, so a compass would list it by its id")
            sys.exit(1)
        entries["biome.%s.vale_%s" % (NS, name)] = shown
    return entries


# ---------------------------------------------------------------------------
# The structures
# ---------------------------------------------------------------------------
#
# One structure file and one placement per grammar, written out of
# GrammarRules. The spacing comes from WildRules' own record of what a player
# actually meets: a structure every few hundred chunks is one nobody ever sees.

GRAMMAR_RULES = os.path.join(
    MOD, "src/main/java/com/elysium/wilds/rules/GrammarRules.java")

#: How far apart each kind of building is, in chunks, and how much of that is
#: guaranteed. Rarer things are further apart; nothing is so far apart that a
#: player walking for an afternoon would not meet one.
GRAMMAR_SPACING = {
    "hamlet": (32, 12), "watchtower": (24, 10), "camp": (20, 8),
    "ruin": (28, 11), "lodge": (26, 10), "mine": (30, 12),
    "shrine": (40, 16), "keep": (48, 20),
}

#: Where each kind of building belongs. A keep in a swamp and a keep on a
#: mountain are the same keep, and a world where every structure is in every
#: biome is a world with one structure in it.
GRAMMAR_BIOMES = {
    "hamlet": "#minecraft:is_overworld",
    "watchtower": "#minecraft:is_hill",
    "camp": "#minecraft:is_forest",
    "ruin": "#minecraft:is_overworld",
    "lodge": "#minecraft:is_taiga",
    "mine": "#minecraft:is_mountain",
    "shrine": "#minecraft:is_jungle",
    "keep": "#minecraft:is_overworld",
}


def grammars():
    """Every kind of building, read out of GrammarRules."""
    source = open(GRAMMAR_RULES, encoding="utf-8").read()
    found = re.findall(r'new Grammar\("([a-z_]+)",\s*"([a-z_]+)"', source)
    if len(found) != len(re.findall(r"new Grammar\(", source)):
        print("the grammar rules declare more buildings than this file could "
              "read; one was wrapped onto a second line and it would never "
              "generate")
        sys.exit(1)
    if not found:
        print("this file read no buildings out of the grammar rules")
        sys.exit(1)
    return [name for name, _start in found]


def structure_files(names):
    data = os.path.join(MOD, "src/main/resources/data", NS)
    written = 0
    for name in names:
        biomes = GRAMMAR_BIOMES.get(name)
        spacing = GRAMMAR_SPACING.get(name)
        if biomes is None or spacing is None:
            print(f"{name} has no biomes or no spacing here, so it is a building "
                  f"the world has nowhere to put")
            sys.exit(1)
        apart, guaranteed = spacing
        if guaranteed >= apart:
            print(f"{name} is placed every {apart} chunk(s) with {guaranteed} "
                  f"guaranteed between them, and the guarantee has to be smaller "
                  f"than the spacing or the game cannot place it at all")
            sys.exit(1)
        write(os.path.join(data, "worldgen/structure", name + ".json"), {
            "type": f"{NS}:grammar",
            "grammar": name,
            "biomes": biomes,
            "step": "surface_structures",
            "terrain_adaptation": "beard_thin",
            "spawn_overrides": {},
        })
        write(os.path.join(data, "worldgen/structure_set", name + ".json"), {
            "structures": [{"structure": f"{NS}:{name}", "weight": 1}],
            "placement": {
                "type": "minecraft:random_spread",
                "spacing": apart,
                "separation": guaranteed,
                "salt": 900000 + sum(ord(letter) for letter in name),
            },
        })
        written += 2
    return written



# ======================================================================
# The overworld: a hundred and forty biomes in somebody else's list
# ======================================================================
#
# Everything below reads its inputs out of OverworldRules.java and works the
# rest out again here. That is two implementations of one piece of arithmetic
# and they drift, silently, so validate.py runs tools/OverworldRoster.java and
# compares what the Java thinks against what this actually wrote. Either they
# agree or the build stops.


def overworld_spans(source, name):
    """A list of Spans out of the Java, as (low, high) pairs."""
    found = re.search(name + r"\s*=\s*List\.of\((.*?)\);", source, re.S)
    if not found:
        print(f"OverworldRules no longer says what {name} is, and every claim in "
              f"the overworld is cut on it")
        sys.exit(1)
    pairs = re.findall(r"Span\(\s*(-?[\d.]+)D,\s*(-?[\d.]+)D\s*\)", found.group(1))
    return [(float(low), float(high)) for low, high in pairs]


def overworld_strings(source, name):
    """A List.of of plain strings out of the Java."""
    found = re.search(r"(?<![A-Z_])" + name + r"\s*=\s*List\.of\((.*?)\);",
                      source, re.S)
    if not found:
        print(f"OverworldRules no longer says what {name} is")
        sys.exit(1)
    return re.findall(r'"([a-z_0-9]+)"', found.group(1))


def overworld_table(source, name, rows):
    """A List.of of List.ofs out of the Java: one row per band of warmth."""
    found = re.search(name + r"\s*=\s*List\.of\((.*?)\);\n", source, re.S)
    if not found:
        print(f"OverworldRules no longer says what {name} is")
        sys.exit(1)
    table = [re.findall(r'"([a-z_]+)"', row)
             for row in re.findall(r"List\.of\((.*?)\)", found.group(1), re.S)]
    if len(table) != rows:
        print(f"{name} has {len(table)} row(s) and there are {rows} bands of "
              f"warmth; a biome would decorate like nothing")
        sys.exit(1)
    return table


def overworld():
    """The whole roster, worked out the way OverworldRules works it out."""
    source = read(OVERWORLD_RULES)
    warmths = overworld_spans(source, "WARMTHS")
    wets = overworld_spans(source, "WETS")
    reliefs = overworld_spans(source, "RELIEFS")
    strange = overworld_spans(source, "STRANGENESS")
    inland = re.search(r"INLAND = new WildRules\.Span\((-?[\d.]+)D, (-?[\d.]+)D\)",
                       source)
    caves_wets = re.search(r"CAVE_WETS = (\d+);", source)
    if not (inland and caves_wets):
        print("OverworldRules no longer says how far inland we go, or how many "
              "bands of damp a cave is cut into")
        sys.exit(1)
    inland = (float(inland.group(1)), float(inland.group(2)))
    caves_wets = int(caves_wets.group(1))

    climates = overworld_strings(source, "CLIMATE_NAMES")
    cave_names = overworld_strings(source, "CAVE_NAMES")
    relief_names = overworld_strings(source, "RELIEF_NAMES")
    models = overworld_table(source, "MODELS", len(warmths))
    crags = overworld_strings(source, "CRAG_MODELS")
    cave_models = overworld_strings(source, "CAVE_MODELS")
    ours = overworld_strings(source, "OURS")

    if len(climates) != len(warmths) * len(wets):
        print(f"there are {len(climates)} climate name(s) for "
              f"{len(warmths) * len(wets)} crossing(s), so a biome is unnamed and "
              f"the one that is unnamed is the one that never generates")
        sys.exit(1)
    if len(cave_names) != len(warmths) * caves_wets:
        print(f"there are {len(cave_names)} cave name(s) for "
              f"{len(warmths) * caves_wets} crossing(s)")
        sys.exit(1)
    if len(relief_names) != len(reliefs) or len(crags) != len(warmths):
        print("the erosion bands and their names, or the crags and the warmths, "
              "are different lengths")
        sys.exit(1)

    lands = []
    for warmth in range(len(warmths)):
        for wet in range(len(wets)):
            for relief in range(len(reliefs)):
                lands.append({
                    "name": climates[warmth * len(wets) + wet] + "_"
                            + relief_names[relief],
                    "warmth": warmth, "wet": wet, "relief": relief,
                    "model": crags[warmth] if relief == 0
                             else models[warmth][wet],
                })
    caves = []
    for warmth in range(len(warmths)):
        for wet in range(caves_wets):
            caves.append({
                "name": cave_names[warmth * caves_wets + wet],
                "warmth": warmth, "wet": wet,
                "model": cave_models[wet],
            })
    return {
        "warmths": warmths, "wets": wets, "reliefs": reliefs,
        "strange": strange, "inland": inland, "cave_wets": caves_wets,
        "lands": lands, "caves": caves, "ours": ours,
    }


def blend(low, high, at, bands):
    """
    The same integer mix OverworldRules does, rounding the same way.

    Which is the whole reason this is five lines rather than one. Java's
    integer division truncates towards zero and Python's floors, and they
    differ by one on every negative numerator — which is every ramp here that
    runs from a bright colour down to a dark one, so most of them. The result
    was a hundred and thirty biomes painted one colour in the proof and
    another in the file the game reads, and no colour anybody would ever look
    at twice. The validator found it by comparing the two; nothing else could
    have.
    """
    if bands < 2:
        return low
    step = (high - low) * at
    carried = abs(step) // (bands - 1)
    return low + (carried if step >= 0 else -carried)


def rgb(red, green, blue):
    return (red << 16) | (green << 8) | blue


def land_colours(one, shape):
    warmths, wets, reliefs = (len(shape["warmths"]), len(shape["wets"]),
                              len(shape["reliefs"]))
    warmth, wet, relief = one["warmth"], one["wet"], one["relief"]
    return {
        "sky": rgb(blend(0x7A, 0xE0, warmth, warmths),
                   blend(0xB4, 0xA8, warmth, warmths),
                   blend(0xF4, 0x60, relief, reliefs)),
        "fog": rgb(blend(0xD8, 0x9C, wet, wets),
                   blend(0xD0, 0xB4, relief, reliefs),
                   blend(0xC0, 0xC8, wet, wets)),
        "water": rgb(blend(0x2C, 0x4E, warmth, warmths),
                     blend(0x5E, 0x8A, wet, wets),
                     blend(0xB8, 0x74, warmth, warmths)),
        "grass": rgb(blend(0xC4, 0x4A, wet, wets),
                     blend(0xA8, 0x9E, wet, wets),
                     blend(0x6C, 0x38, relief, reliefs)),
        "foliage": rgb(blend(0xAC, 0x3A, wet, wets),
                       blend(0x98, 0x8E, warmth, warmths),
                       blend(0x70, 0x2C, wet, wets)),
    }


def cave_colours(one, shape):
    warmths, wets = len(shape["warmths"]), shape["cave_wets"]
    warmth, wet = one["warmth"], one["wet"]
    return {
        "sky": rgb(blend(0x12, 0x3C, warmth, warmths),
                   blend(0x16, 0x2A, wet, wets),
                   blend(0x28, 0x1C, warmth, warmths)),
        "fog": rgb(blend(0x28, 0x6A, warmth, warmths),
                   blend(0x30, 0x54, wet, wets),
                   blend(0x44, 0x30, warmth, warmths)),
        "water": rgb(blend(0x1E, 0x46, warmth, warmths),
                     blend(0x48, 0x7E, wet, wets),
                     blend(0x8C, 0x5C, warmth, warmths)),
        "grass": rgb(blend(0x5C, 0x3E, wet, wets),
                     blend(0x78, 0x92, wet, wets),
                     blend(0x4C, 0x30, warmth, warmths)),
        "foliage": rgb(blend(0x4E, 0x34, wet, wets),
                       blend(0x6E, 0x86, warmth, warmths),
                       blend(0x40, 0x28, wet, wets)),
    }


def land_extras(one, ours):
    """What a surface biome adds of its own, in the one order OURS gives."""
    made = []
    if one["relief"] <= 1:
        made.append("boulder_basalt" if one["warmth"] == 4 else "boulder_stone")
    if one["wet"] >= 3 and one["relief"] >= 2:
        made.append("boulder_mossy")
    made.append("patch_scrub" if one["wet"] <= 1
                else "patch_bloom" if one["wet"] == 2 else "patch_thicket")
    return sorted(made, key=ours.index)


def cave_extras(one, ours, cave_wets):
    made = ["boulder_stone"]
    if one["wet"] == cave_wets - 1:
        made.append("boulder_mossy")
    return sorted(made, key=ours.index)


def vanilla_features():
    """Every vanilla biome's decoration, per step, out of the dump."""
    found = {}
    for line in read(VANILLA_FEATURES).splitlines():
        if line.startswith("#") or not line.strip():
            continue
        biome, step, feature = line.split("\t")
        found.setdefault(biome, {}).setdefault(int(step), []).append(feature)
    if not found:
        print("the dump of vanilla's decoration is empty, so every biome of ours "
              "would be bare ground")
        sys.exit(1)
    return found


def vanilla_entries():
    """Vanilla's 7,593 parameter entries, out of the dump, untouched."""
    made = []
    for line in read(VANILLA_LIST).splitlines():
        if line.startswith("#") or not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) != 14:
            print(f"a row of the overworld dump has {len(parts)} column(s) and "
                  f"an entry has 14")
            sys.exit(1)
        numbers = [int(part) / 10000.0 for part in parts[1:]]
        made.append({
            "biome": parts[0],
            "parameters": {
                "temperature": [numbers[0], numbers[1]],
                "humidity": [numbers[2], numbers[3]],
                "continentalness": [numbers[4], numbers[5]],
                "erosion": [numbers[6], numbers[7]],
                "depth": [numbers[8], numbers[9]],
                "weirdness": [numbers[10], numbers[11]],
                "offset": numbers[12],
            },
        })
    if len(made) < 7000:
        print(f"the dump of vanilla's overworld has {len(made)} entries and it "
              f"had 7,593; half of vanilla's biomes would stop generating")
        sys.exit(1)
    return made


def box(spans):
    """One entry's parameters, rounded the way OverworldRoster prints them."""
    axes = ["temperature", "humidity", "continentalness", "erosion", "depth",
            "weirdness"]
    made = {}
    for axis, (low, high) in zip(axes, spans):
        made[axis] = [round(low, 4), round(high, 4)]
    made["offset"] = 0.0
    return made


def overworld_claims(shape):
    """Every box we claim, surface first, in declaration order."""
    made = []
    for one in shape["lands"]:
        for strange in shape["strange"]:
            made.append(("%s:%s" % (NS, one["name"]), box([
                shape["warmths"][one["warmth"]], shape["wets"][one["wet"]],
                shape["inland"], shape["reliefs"][one["relief"]],
                (0.0, 0.0), strange])))
    for one in shape["caves"]:
        for strange in shape["strange"]:
            wet = one["wet"]
            if wet == 0:
                span = (shape["wets"][0][0], shape["wets"][1][1])
            elif wet == 1:
                span = shape["wets"][2]
            else:
                span = (shape["wets"][3][0], shape["wets"][4][1])
            made.append(("%s:%s" % (NS, one["name"]), box([
                shape["warmths"][one["warmth"]], span, shape["inland"],
                (-1.0, 1.0), (1.0, 1.0), strange])))
    return made


#: What spawns in a band of warmth, as (what, most, fewest, weight). Those two
#: numbers in that order: the other way round is not a crash, the game drops
#: the spawner and logs a line, and the biome is empty with nothing saying why.
OVERWORLD_CREATURES = [
    [("minecraft:polar_bear", 2, 1, 4), ("minecraft:rabbit", 3, 2, 8)],
    [("minecraft:fox", 4, 2, 8), ("minecraft:wolf", 4, 4, 5),
     ("minecraft:rabbit", 3, 2, 4)],
    [("minecraft:sheep", 4, 4, 12), ("minecraft:cow", 4, 4, 8),
     ("minecraft:chicken", 4, 4, 10), ("minecraft:pig", 4, 4, 10)],
    [("minecraft:horse", 6, 2, 5), ("minecraft:donkey", 3, 1, 1),
     ("minecraft:llama", 4, 4, 5)],
    [("minecraft:camel", 1, 1, 2), ("minecraft:rabbit", 3, 2, 4),
     ("minecraft:ocelot", 3, 2, 2)],
]

OVERWORLD_MONSTERS = [
    ("minecraft:spider", 4, 4, 100), ("minecraft:zombie", 4, 4, 95),
    ("minecraft:skeleton", 4, 4, 100), ("minecraft:creeper", 4, 4, 100),
    ("minecraft:slime", 4, 4, 100), ("minecraft:enderman", 4, 1, 10),
    ("minecraft:witch", 1, 1, 5),
]


def spawner_list(rows):
    return [{"type": kind, "maxCount": most, "minCount": fewest,
             "weight": weight} for kind, most, fewest, weight in rows]


def overworld_biome(name, model, colours, warmth, wet, rains, extras,
                    decoration, creatures, underground):
    """One biome file: a vanilla biome's decoration, our colours, our life."""
    steps = []
    for step in range(11):
        listed = list(decoration.get(step, []))
        listed += ["%s:%s" % (NS, feature) for feature in extras
                   if (2 if feature.startswith("boulder_") else 9) == step]
        steps.append(listed)
    return {
        "has_precipitation": rains,
        "temperature": round(warmth, 4),
        "downfall": round(wet, 4),
        "effects": {
            "sky_color": colours["sky"],
            "fog_color": colours["fog"],
            "water_color": colours["water"],
            "water_fog_color": colours["water"],
            "grass_color": colours["grass"],
            "foliage_color": colours["foliage"],
            "mood_sound": {"block_search_extent": 8, "offset": 2.0,
                           "sound": "minecraft:ambient.cave",
                           "tick_delay": 6000},
        },
        "spawners": {
            "monster": [] if underground else spawner_list(OVERWORLD_MONSTERS),
            "creature": spawner_list(creatures),
            "ambient": [], "water_creature": [], "water_ambient": [],
            "underground_water_creature": [], "axolotls": [], "misc": [],
        },
        "spawn_costs": {},
        "carvers": {"air": ["minecraft:cave", "minecraft:cave_extra_underground",
                            "minecraft:canyon"]},
        "features": steps,
    }


#: What each of our own features actually is. Blobs and patches out of blocks
#: the game already has: a configured feature with a type the game already
#: reads is one the load test can put through the real codec, and a new type
#: of our own is one nothing can check until somebody presses Create World.
OUR_FEATURES = {
    "boulder_stone": ("minecraft:forest_rock", {"state": {"Name": "minecraft:stone"}}),
    "boulder_mossy": ("minecraft:forest_rock",
                      {"state": {"Name": "minecraft:mossy_cobblestone"}}),
    "boulder_basalt": ("minecraft:forest_rock", {"state": {"Name": "minecraft:basalt",
                                                           "Properties": {"axis": "y"}}}),
    "patch_scrub": ("minecraft:random_patch", None),
    "patch_bloom": ("minecraft:random_patch", None),
    "patch_thicket": ("minecraft:random_patch", None),
}

#: What grows in each of the three patches, and how thickly.
OUR_PATCHES = {
    "patch_scrub": ("minecraft:dead_bush", 24, 2),
    "patch_bloom": ("minecraft:cornflower", 48, 4),
    "patch_thicket": ("minecraft:fern", 64, 6),
}


def our_feature_files(shape):
    """The configured and placed halves of every feature of ours."""
    written = 0
    for name in shape["ours"]:
        kind, extra = OUR_FEATURES[name]
        if kind == "minecraft:forest_rock":
            config = dict(extra)
        else:
            block, tries, spread = OUR_PATCHES[name]
            config = {
                "tries": tries,
                "xz_spread": spread,
                "y_spread": 1,
                "feature": {
                    "feature": {
                        "type": "minecraft:simple_block",
                        "config": {"to_place": {"type": "minecraft:simple_state_provider",
                                                "state": {"Name": block}}},
                    },
                    "placement": [{"type": "minecraft:block_predicate_filter",
                                   "predicate": {"type": "minecraft:matching_blocks",
                                                 "blocks": "minecraft:air"}}],
                },
            }
        write(os.path.join(DATA, "worldgen/configured_feature", name + ".json"),
              {"type": kind, "config": config})
        written += 1
        if kind == "minecraft:forest_rock":
            placement = [
                {"type": "minecraft:count", "count": 1},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:heightmap",
                 "heightmap": "MOTION_BLOCKING"},
                {"type": "minecraft:biome"},
            ]
        else:
            placement = [
                {"type": "minecraft:count", "count": 3},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:heightmap",
                 "heightmap": "WORLD_SURFACE_WG"},
                {"type": "minecraft:biome"},
            ]
        write(os.path.join(DATA, "worldgen/placed_feature", name + ".json"),
              {"feature": "%s:%s" % (NS, name), "placement": placement})
        written += 1
    return written


def overworld_files(shape):
    """Every biome of ours, our features, and the three presets they go in."""
    decoration = vanilla_features()
    written = our_feature_files(shape)

    for one in shape["lands"]:
        model = "minecraft:" + one["model"]
        if model not in decoration:
            print(f"{one['name']} decorates like {model} and the dump of "
                  f"vanilla's decoration has no such biome")
            sys.exit(1)
        write(os.path.join(DATA, "worldgen/biome", one["name"] + ".json"),
              overworld_biome(
                  one["name"], model, land_colours(one, shape),
                  -0.5 + one["warmth"] * (2.5 / (len(shape["warmths"]) - 1)),
                  one["wet"] / (len(shape["wets"]) - 1.0),
                  one["wet"] > 0, land_extras(one, shape["ours"]),
                  decoration[model], OVERWORLD_CREATURES[one["warmth"]], False))
        written += 1
    for one in shape["caves"]:
        model = "minecraft:" + one["model"]
        if model not in decoration:
            print(f"{one['name']} decorates like {model} and the dump of "
                  f"vanilla's decoration has no such biome")
            sys.exit(1)
        write(os.path.join(DATA, "worldgen/biome", one["name"] + ".json"),
              overworld_biome(
                  one["name"], model, cave_colours(one, shape),
                  -0.5 + one["warmth"] * (2.5 / (len(shape["warmths"]) - 1)),
                  one["wet"] / (shape["cave_wets"] - 1.0),
                  False,
                  cave_extras(one, shape["ours"], shape["cave_wets"]),
                  decoration[model], [], True))
        written += 1

    written += preset_files(shape)
    return written


def preset_files(shape):
    """
    The three world presets, with our list in front of vanilla's.

    Everything but the overworld's biome source goes through exactly as the
    game ships it. A preset that rewrites the nether on the way past is one
    that changes two dimensions nobody asked us to touch, and it would not
    look like a mistake in any file anybody reads.
    """
    entries = [{"biome": name, "parameters": parameters}
               for name, parameters in overworld_claims(shape)]
    entries += vanilla_entries()
    written = 0
    for name in sorted(os.listdir(VANILLA_PRESETS)):
        if not name.endswith(".json"):
            continue
        preset = json.loads(read(os.path.join(VANILLA_PRESETS, name)))
        overworld = preset["dimensions"]["minecraft:overworld"]
        source = overworld["generator"]["biome_source"]
        if source.get("preset") != "minecraft:overworld":
            print(f"{name} does not use vanilla's overworld list any more, so "
                  f"replacing it would replace something else")
            sys.exit(1)
        overworld["generator"]["biome_source"] = {
            "type": "minecraft:multi_noise",
            "biomes": entries,
        }
        write_tight(os.path.join(MINECRAFT, "worldgen/world_preset", name),
                    preset)
        written += 1
    if written != 3:
        print(f"{written} world preset(s) were written and vanilla has three "
              f"that use the overworld's list; one of them would send a player "
              f"to an overworld with none of our biomes in it")
        sys.exit(1)
    return written


def overworld_language(entries, shape):
    """A name a player reads, for every one of them."""
    for one in shape["lands"] + shape["caves"]:
        entries["biome.%s.%s" % (NS, one["name"])] = \
            " ".join(word.capitalize() for word in one["name"].split("_"))
    for name in shape["ours"]:
        entries["feature.%s.%s" % (NS, name)] = \
            " ".join(word.capitalize() for word in name.split("_"))


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

    found, warmths, wets = vale()
    valefiles = vale_files(found, warmths, wets)
    built = grammars()
    valefiles += structure_files(built)
    shape = overworld()
    overfiles = overworld_files(shape)

    entries = {
        "itemGroup.elysiumwilds": "Elysium Wilds",
        "item.elysiumwilds.biome_compass": "Surveyor's Compass",
        "elysiumwilds.compass.title": "Look for somewhere",
        "elysiumwilds.compass.count": "%s place(s) this world knows about",
        "elysiumwilds.compass.idle": "Pointing at nothing yet",
        "elysiumwilds.compass.holding": "Pointing at %s, around %s, %s",
        "elysiumwilds.compass.found": "%s: around %s, %s — about %s blocks away",
        "elysiumwilds.compass.nowhere": "No %s anywhere the compass can see from here",
        "elysiumwilds.compass.nomatch": "Nothing here matches what you have typed",
    }
    vale_language(entries, found)
    overworld_language(entries, shape)
    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(entries.items())))

    print("elysium-wilds: 1 item, %d needle frame(s), 1 recipe, %d biome(s) of "
          "the Vale and %d kind(s) of building in %d file(s); %d biome(s) of "
          "ours in the overworld and %d feature(s) to put in them, in %d file(s)"
          % (FRAMES, len(found), len(built), valefiles,
             len(shape["lands"]) + len(shape["caves"]), len(shape["ours"]),
             overfiles))


if __name__ == "__main__":
    main()
