#!/usr/bin/env python3
"""
Two hundred and ten geode blocks and sixty items, from a thirty-row table.

    python3 tools/gen_geodes.py

The materials are read out of GeodeRules.java, the bud sizes out of
GeodeBlocks.java. Nothing here has its own list, so a thirty-first material is
a line in the rules and a colour, and a material dropped disappears from the
textures, the models, the loot and the language file at once.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumarcana"

OUT = os.environ.get("ELYSIUM_GEODES_OUT", MOD)
ASSETS = os.path.join(OUT, "src/main/resources/assets", NS)
DATA = os.path.join(OUT, "src/main/resources/data", NS)

RULES = os.path.join(MOD, "src/main/java/com/elysium/arcana/rules/GeodeRules.java")
BLOCKS = os.path.join(MOD,
                      "src/main/java/com/elysium/arcana/registry/GeodeBlocks.java")

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


SOURCE = read(RULES)
BLOCK_SOURCE = read(BLOCKS)


#: The rules name their kinds through constants, not string literals, so the
#: constants are resolved first. Reading the literals alone gave an empty
#: chain and the generator stopped with a message rather than writing half a
#: catalogue, which is the behaviour to want.
CONSTANTS = dict(re.findall(
    r'public static final String ([A-Z_]+) = "([a-z_]+)";', SOURCE))


def listing(name, source=SOURCE):
    found = re.search(r"public static final List<String> " + name
                      + r" =\s*List\.of\(([^;]*?)\);", source, re.S)
    if not found:
        return []
    body = found.group(1)
    out = []
    for token in re.findall(r'"([a-z_]+)"|([A-Z_]{2,})', body):
        literal, constant = token
        if literal:
            out.append(literal)
        elif constant in CONSTANTS:
            out.append(CONSTANTS[constant])
    return out


MINED = listing("MINED")
VANILLA = listing("THE_GAMES_OWN")
CHAIN = listing("CHAIN")
MATERIALS = MINED + VANILLA
KINDS = ["block", "budding", "small_bud", "medium_bud", "large_bud", "cluster",
         "tinted_glass"]
ITEMS = ["shard", "spyglass"]

heights = re.search(r"BUD_HEIGHT = \{([^}]*)\}", BLOCK_SOURCE)
BUD_HEIGHT = [int(v) for v in re.findall(r"\d+", heights.group(1))] if heights else []

if not (MATERIALS and CHAIN and BUD_HEIGHT):
    print("the geode sources no longer have the shape this reads")
    sys.exit(2)
if len(BUD_HEIGHT) != len(CHAIN):
    print("the chain has", len(CHAIN), "steps and", len(BUD_HEIGHT), "heights")
    sys.exit(2)


def block_id(material, kind):
    return f"budding_{material}" if kind == "budding" else f"{material}_{kind}"


# ---------------------------------------------------------------------------
# Colour, from the material's own name
# ---------------------------------------------------------------------------

def java_hash(text):
    value = 0
    for char in text:
        value = (value * 31 + ord(char)) & 0xFFFFFFFF
    if value >= 0x80000000:
        value -= 0x100000000
    return abs(value)


KNOWN = {
    "iron": (216, 216, 216), "copper": (196, 118, 78), "gold": (244, 208, 92),
    "tin": (206, 212, 220), "zinc": (198, 200, 186), "nickel": (206, 206, 176),
    "silver": (226, 230, 236), "lead": (110, 108, 134), "aluminum": (198, 204, 210),
    "osmium": (168, 190, 214), "platinum": (196, 226, 232), "lithium": (222, 214, 226),
    "crimson_iron": (186, 72, 60), "azure_silver": (150, 190, 226),
    "uranium": (108, 196, 88), "bort": (86, 104, 118), "certus_quartz": (176, 206, 226),
    "fluorite": (150, 226, 206), "rime_shard": (186, 230, 240),
    "uraninite": (86, 138, 78), "coal": (44, 44, 48), "redstone": (206, 58, 48),
    "lapis": (52, 86, 168), "quartz": (232, 226, 214), "amethyst": (166, 118, 220),
    "diamond": (110, 226, 216), "emerald": (66, 194, 106),
    "ancient_debris": (112, 86, 76), "glowstone": (244, 212, 120),
    "obsidian": (46, 34, 66),
}


def tone(material):
    if material in KNOWN:
        return KNOWN[material]
    value = java_hash(material)
    return (90 + value % 140, 90 + (value // 7) % 140, 90 + (value // 53) % 140)


def darker(colour, by=44):
    return tuple(max(0, channel - by) for channel in colour)


def lighter(colour, by=40):
    return tuple(min(255, channel + by) for channel in colour)


def solid_sprite(material):
    core = tone(material)
    image = Image.new("RGBA", (16, 16), core + (255,))
    draw = ImageDraw.Draw(image)
    seed = java_hash(material)
    for _ in range(22):
        seed = (seed * 1103515245 + 12345) & 0x7FFFFFFF
        x = seed % 16
        seed = (seed * 1103515245 + 12345) & 0x7FFFFFFF
        y = seed % 16
        draw.point((x, y), fill=(lighter(core, 30) if (x + y) % 2
                                 else darker(core, 26)) + (255,))
    draw.line([(0, 0), (15, 0)], fill=lighter(core, 22) + (255,))
    draw.line([(0, 15), (15, 15)], fill=darker(core, 30) + (255,))
    return image


def budding_sprite(material):
    image = solid_sprite(material)
    core = lighter(tone(material), 60)
    # The sparkle goes on a layer and is composited down: a soft pixel drawn
    # straight onto the block wrote its alpha into a solid sprite, and the
    # block — on the solid layer, as a full cube should be — showed those
    # five pixels black in the world.
    sparkle = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(sparkle)
    for spot in ((4, 4), (11, 5), (6, 11), (12, 12), (8, 7)):
        draw.point(spot, fill=core + (255,))
        draw.point((spot[0] + 1, spot[1]), fill=core + (200,))
    image.alpha_composite(sparkle)
    return image


def glass_sprite(material):
    core = tone(material)
    image = Image.new("RGBA", (16, 16), core + (96,))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], outline=lighter(core, 40) + (170,))
    draw.line([(2, 2), (6, 6)], fill=(255, 255, 255, 110))
    return image


def bud_sprite(material, step):
    """A crystal, taller with each step of the chain."""
    core = tone(material)
    bright = lighter(core, 54)
    edge = darker(core, 50)
    tall = 3 + step * 3
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    base = 15
    for shard in range(step + 1):
        left = 5 + shard * 2 - step
        if left < 1:
            left = 1
        top = base - tall - shard
        draw.polygon([(left, base), (left + 2, base), (left + 2, top + 1),
                      (left + 1, top), (left, top + 1)],
                     fill=core + (255,), outline=edge + (255,))
        draw.point((left + 1, top + 2), fill=bright + (255,))
    return image


def shard_sprite(material):
    core = tone(material)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.polygon([(7, 2), (11, 7), (9, 13), (5, 12), (4, 6)],
                 fill=core + (255,), outline=darker(core, 54) + (255,))
    draw.line([(7, 5), (8, 9)], fill=lighter(core, 60) + (255,))
    return image


def spyglass_sprite(material):
    core = tone(material)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.polygon([(3, 12), (6, 9), (12, 3), (14, 5), (8, 11), (5, 14)],
                 fill=core + (255,), outline=darker(core, 54) + (255,))
    draw.line([(11, 4), (13, 6)], fill=lighter(core, 70) + (255,))
    return image


def save(image, *parts):
    path = os.path.join(*parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)


def draw_all():
    count = 0
    for material in MATERIALS:
        save(solid_sprite(material), ASSETS, "textures/block",
             block_id(material, "block") + ".png")
        save(budding_sprite(material), ASSETS, "textures/block",
             block_id(material, "budding") + ".png")
        save(glass_sprite(material), ASSETS, "textures/block",
             block_id(material, "tinted_glass") + ".png")
        count += 3
        for step, kind in enumerate(CHAIN):
            save(bud_sprite(material, step), ASSETS, "textures/block",
                 block_id(material, kind) + ".png")
            count += 1
        save(shard_sprite(material), ASSETS, "textures/item",
             material + "_shard.png")
        save(spyglass_sprite(material), ASSETS, "textures/item",
             material + "_spyglass.png")
        count += 2
    return count


#: How a directional crystal is turned to face each way. The game's own
#: amethyst cluster, because a player already knows how that behaves.
FACING = {
    "facing=down": {"x": 180},
    "facing=up": {},
    "facing=north": {"x": 90},
    "facing=south": {"x": 90, "y": 180},
    "facing=west": {"x": 90, "y": 270},
    "facing=east": {"x": 90, "y": 90},
}


def models_and_states():
    count = 0
    for material in MATERIALS:
        for kind in ("block", "budding", "tinted_glass"):
            name = block_id(material, kind)
            model = {"parent": "minecraft:block/cube_all",
                     "textures": {"all": f"{NS}:block/{name}"}}
            if kind == "tinted_glass":
                # Every pixel of the pane is part-transparent, and a modded
                # model draws on the solid layer unless it says otherwise —
                # which made the glass an opaque block of its tint.
                model["render_type"] = "minecraft:translucent"
            write(os.path.join(ASSETS, "models/block", name + ".json"), model)
            write(os.path.join(ASSETS, "blockstates", name + ".json"),
                  {"variants": {"": {"model": f"{NS}:block/{name}"}}})
            write(os.path.join(ASSETS, "models/item", name + ".json"),
                  {"parent": f"{NS}:block/{name}"})
            count += 1
        for kind in CHAIN:
            name = block_id(material, kind)
            write(os.path.join(ASSETS, "models/block", name + ".json"),
                  {"parent": "minecraft:block/cross",
                   "render_type": "minecraft:cutout",
                   "textures": {"cross": f"{NS}:block/{name}"}})
            write(os.path.join(ASSETS, "blockstates", name + ".json"),
                  {"variants": {key: dict({"model": f"{NS}:block/{name}"}, **turn)
                                for key, turn in FACING.items()}})
            write(os.path.join(ASSETS, "models/item", name + ".json"),
                  {"parent": "minecraft:item/generated",
                   "textures": {"layer0": f"{NS}:block/{name}"}})
            count += 1
        for kind in ITEMS:
            write(os.path.join(ASSETS, "models/item", f"{material}_{kind}.json"),
                  {"parent": "minecraft:item/generated",
                   "textures": {"layer0": f"{NS}:item/{material}_{kind}"}})
            count += 1
    return count


def loot_and_recipes():
    count = 0
    for material in MATERIALS:
        for kind in ("block", "budding", "tinted_glass"):
            name = block_id(material, kind)
            write(os.path.join(DATA, "loot_table/blocks", name + ".json"),
                  drops(name))
            count += 1
        for kind in CHAIN[:-1]:
            name = block_id(material, kind)
            # A bud that is not finished growing drops nothing, the way the
            # game's own do: breaking it early is a mistake, not a shortcut.
            write(os.path.join(DATA, "loot_table/blocks", name + ".json"),
                  {"type": "minecraft:block", "pools": []})
            count += 1
        cluster = block_id(material, CHAIN[-1])
        write(os.path.join(DATA, "loot_table/blocks", cluster + ".json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item",
                             "name": f"{NS}:{material}_shard",
                             "functions": [{"function": "minecraft:set_count",
                                            "count": 4}]}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
        count += 1

        # Four shards make the block, the block makes four shards back, and
        # the glass is the block plus what the game makes tinted glass with.
        write(os.path.join(DATA, "recipe", block_id(material, "block") + ".json"), {
            "type": "minecraft:crafting_shaped",
            "category": "building",
            "pattern": ["SS", "SS"],
            "key": {"S": {"item": f"{NS}:{material}_shard"}},
            "result": {"id": f"{NS}:{block_id(material, 'block')}", "count": 1},
        })
        write(os.path.join(DATA, "recipe",
                           f"{material}_shard_from_block.json"), {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": [{"item": f"{NS}:{block_id(material, 'block')}"}],
            "result": {"id": f"{NS}:{material}_shard", "count": 4},
        })
        write(os.path.join(DATA, "recipe",
                           block_id(material, "tinted_glass") + ".json"), {
            "type": "minecraft:crafting_shaped",
            "category": "building",
            "pattern": [" S ", "SGS", " S "],
            "key": {"S": {"item": f"{NS}:{material}_shard"},
                    "G": {"item": "minecraft:glass"}},
            "result": {"id": f"{NS}:{block_id(material, 'tinted_glass')}",
                       "count": 2},
        })
        write(os.path.join(DATA, "recipe", f"{material}_spyglass.json"), {
            "type": "minecraft:crafting_shaped",
            "category": "equipment",
            "pattern": ["S", "C", "C"],
            "key": {"S": {"item": f"{NS}:{material}_shard"},
                    "C": {"item": "minecraft:copper_ingot"}},
            "result": {"id": f"{NS}:{material}_spyglass", "count": 1},
        })
        count += 4

        # And the one this module never had: a way of getting a budding block
        # other than finding one. Without it, thirty-two recipes in somebody
        # else's jar are the only way, which is why that jar is still in the
        # mods folder with DONE written next to it.
        priced = BY_MATERIAL.get(material)
        if priced is not None:
            write(os.path.join(DATA, "recipe", f"budding_{material}.json"),
                  seeding(priced))
            count += 1
    return count


# How a budding block is made, read out of BuddingRules: what each costs and
# what it is paid in are rules and are proved about.
BUDDING_SOURCE = read(os.path.join(
    MOD, "src/main/java/com/elysium/arcana/rules/BuddingRules.java"))
BUDDINGS = [
    {"material": one[0], "from": one[1], "shards": int(one[2]),
     "catalyst": one[3]}
    for one in re.findall(
        r'new Budding\("(\w+)", "(\w+)", (\d+), (\w+)\)',
        re.sub(r"\s+", " ", BUDDING_SOURCE))
]
_catalysts = dict(re.findall(
    r'public static final String (\w+_CATALYST) = "([\w:]+)";', BUDDING_SOURCE))
for _one in BUDDINGS:
    _one["catalyst"] = _catalysts.get(_one["catalyst"], _one["catalyst"])
_shards_to_a_block = re.search(r"int CLUSTER_GIVES = (\d+);", BUDDING_SOURCE)
SHARDS_TO_A_BLOCK = int(_shards_to_a_block.group(1)) if _shards_to_a_block else 0
if len(BUDDINGS) != 30 or not SHARDS_TO_A_BLOCK:
    print("BuddingRules reads as %d seeding(s) and %d shard(s) to a block"
          % (len(BUDDINGS), SHARDS_TO_A_BLOCK))
    sys.exit(2)
_unnamed = [one["material"] for one in BUDDINGS if ":" not in one["catalyst"]]
if _unnamed:
    print("no catalyst behind %s" % _unnamed)
    sys.exit(2)


BY_MATERIAL = {one["material"]: one for one in BUDDINGS}


def seeding(one):
    """The recipe that makes a budding block.

    Shapeless, and paid in blocks rather than shards because a crafting grid
    holds nine things and the dearest of these is thirty-two shards. The rules
    hold every price to a whole number of blocks and to eight of them plus the
    catalyst, so this function never has to decide what to do about a price it
    cannot express -- which is the decision that would otherwise be made by
    silently rounding.
    """
    blocks = one["shards"] // SHARDS_TO_A_BLOCK
    return {
        "type": "minecraft:crafting_shapeless",
        "category": "building",
        "group": "elysium_seeding",
        "ingredients": (
            [{"item": "%s:%s_block" % (NS, one["material"])}] * blocks
            + [{"item": one["catalyst"]}]),
        "result": {"id": "%s:budding_%s" % (NS, one["material"]), "count": 1},
    }


def drops(name):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }


def a_name(text):
    return " ".join(word.capitalize() for word in text.split("_"))


SHOWN = {
    "block": "Block", "budding": "Budding", "small_bud": "Small Bud",
    "medium_bud": "Medium Bud", "large_bud": "Large Bud", "cluster": "Cluster",
    "tinted_glass": "Tinted Glass",
}


def lang():
    words = {}
    for material in MATERIALS:
        pretty = a_name(material)
        for kind in KINDS:
            name = block_id(material, kind)
            words[f"block.{NS}.{name}"] = (
                "Budding " + pretty if kind == "budding"
                else pretty + " " + SHOWN[kind])
        words[f"item.{NS}.{material}_shard"] = pretty + " Shard"
        words[f"item.{NS}.{material}_spyglass"] = pretty + " Spyglass"

    committed = os.path.join(MOD, "src/main/resources/assets", NS, "lang/en_us.json")
    path = os.path.join(ASSETS, "lang/en_us.json")
    existing = json.loads(read(committed)) if os.path.exists(committed) else {}
    for key in set(words):
        existing.pop(key, None)
    existing.update(words)
    write(path, dict(sorted(existing.items())))
    return len(words)


def main():
    sprites = draw_all()
    modelled = models_and_states()
    data = loot_and_recipes()
    keys = lang()
    print(f"geodes: {len(MATERIALS)} material(s), {len(MATERIALS) * len(KINDS)} block(s), "
          f"{len(MATERIALS) * len(ITEMS)} item(s), {sprites} sprite(s), "
          f"{modelled} modelled, {data} data file(s), {keys} name(s)")


if __name__ == "__main__":
    main()
