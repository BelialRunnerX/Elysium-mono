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
    if bands < 2:
        return low
    return low + (high - low) * at // (bands - 1)


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
    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(entries.items())))

    print("elysium-wilds: 1 item, %d needle frame(s), 1 recipe, %d biome(s) of "
          "the Vale and %d kind(s) of building in %d file(s)"
          % (FRAMES, len(found), len(built), valefiles))


if __name__ == "__main__":
    main()
