#!/usr/bin/env python3
"""
A kitchen: twenty-nine meals, twenty-four ingredients, thirteen cuts and the
blocks they are made on.

    python3 tools/gen_kitchen.py

Everything is read out of KitchenRules.java and KitchenBlocks.java. The
cooking recipes go out as data as well, in this module's own format, so a pack
can add a meal without a compiler — and validate.py checks the recipes on disk
and the meals in the rules still say the same thing.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumagronomy"

OUT = os.environ.get("ELYSIUM_KITCHEN_OUT", MOD)
ASSETS = os.path.join(OUT, "src/main/resources/assets", NS)
DATA = os.path.join(OUT, "src/main/resources/data", NS)

RULES = os.path.join(MOD, "src/main/java/com/elysium/agronomy/rules/KitchenRules.java")
BLOCKS = os.path.join(MOD,
                      "src/main/java/com/elysium/agronomy/registry/KitchenBlocks.java")

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


SOURCE = read(RULES)
BLOCK_SOURCE = read(BLOCKS)

INGREDIENTS = [{"id": i, "nutrition": int(n), "saturation": float(s)}
               for i, n, s in re.findall(
                   r'new Ingredient\("([a-z_]+)", (\d+), ([\d.]+)F\)', SOURCE)]

MEALS = []
for mid, parts, nutr, sat, ticks, serv, station, effect in re.findall(
        r'new Meal\("([a-z_]+)", List\.of\(([^)]*)\),\s*'
        r'(\d+), ([\d.]+)F, (\d+), (\d+), ([A-Z]+), "([a-z_]*)"\)', SOURCE):
    MEALS.append({"id": mid, "of": re.findall(r'"([a-z_]+)"', parts),
                  "nutrition": int(nutr), "saturation": float(sat),
                  "ticks": int(ticks), "servings": int(serv),
                  "station": station.lower(), "effect": effect})

CUTS = []
for inp, tool, yields in re.findall(
        r'new Cut\("([a-z_]+)", "([a-z_]+)", Map\.of\(([^)]*)\)\)', SOURCE):
    pairs = re.findall(r'"([a-z_]+)", (\d+)', yields)
    CUTS.append({"input": inp, "tool": tool,
                 "yields": {name: int(count) for name, count in pairs}})


def listing(name, source):
    found = re.search(r"public static final List<String> " + name
                      + r" = List\.of\(([^;]*?)\);", source, re.S)
    return re.findall(r'"([a-z_]+)"', found.group(1)) if found else []


WOODS = listing("WOODS", BLOCK_SOURCE)
CRATED = listing("CRATED", BLOCK_SOURCE)
STATION_BLOCK = dict(re.findall(r'KitchenRules\.([A-Z]+), "([a-z_]+)"', BLOCK_SOURCE))
STATIONS = {key.lower(): value for key, value in STATION_BLOCK.items()}
KNIVES = re.findall(r'String\[\] kinds = \{([^}]*)\}',
                    read(os.path.join(
                        MOD,
                        "src/main/java/com/elysium/agronomy/registry/KitchenItems.java")))
KNIVES = re.findall(r'"([a-z_]+)"', KNIVES[0]) if KNIVES else []

if not (INGREDIENTS and MEALS and CUTS and WOODS and CRATED and STATIONS and KNIVES):
    print("the kitchen's sources no longer have the shape this reads")
    sys.exit(2)


def java_hash(text):
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


def darker(colour, by=40):
    return tuple(max(0, channel - by) for channel in colour)


def lighter(colour, by=34):
    return tuple(min(255, channel + by) for channel in colour)


EFFECT_TINT = {
    "comfort": 0xE0A46A, "nourishment": 0xD8C070, "regeneration": 0xE07890,
    "absorption": 0xC8A0D8, "resistance": 0x8898B8, "strength": 0xC05848,
    "haste": 0xD8B040, "speed": 0x70B8D0, "fire_resistance": 0xE07840,
    "water_breathing": 0x60C0C0, "night_vision": 0x3860A0, "": 0xB89A70,
}


def bowl_sprite(meal):
    core = shade(meal["id"], EFFECT_TINT.get(meal["effect"], 0xB89A70), 26)
    ware = (222, 216, 206)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    if meal["station"] == "board":
        draw.rectangle([2, 6, 13, 10], fill=(212, 178, 124, 255))
        draw.rectangle([3, 7, 12, 9], fill=core + (255,))
        draw.line([(2, 6), (13, 6)], fill=(234, 206, 158, 255))
    elif meal["station"] == "oven":
        draw.polygon([(3, 12), (12, 12), (11, 6), (4, 6)], fill=(214, 178, 118, 255))
        draw.polygon([(5, 9), (10, 9), (10, 6), (5, 6)], fill=core + (255,))
        draw.line([(3, 12), (12, 12)], fill=(168, 134, 86, 255))
    elif meal["station"] == "skillet":
        draw.ellipse([2, 6, 12, 13], fill=(72, 72, 78, 255))
        draw.ellipse([4, 7, 10, 12], fill=core + (255,))
        draw.line([(12, 9), (15, 8)], fill=(56, 56, 62, 255))
    else:
        draw.ellipse([2, 6, 13, 14], fill=ware + (255,))
        draw.ellipse([3, 7, 12, 11], fill=core + (255,))
        draw.line([(2, 10), (13, 10)], fill=darker(ware, 34) + (255,))
    return image


def part_sprite(one):
    core = shade(one["id"], 0x8CB05C, 46)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.ellipse([4, 4, 11, 12], fill=core + (255,), outline=darker(core) + (255,))
    draw.line([(7, 2), (7, 4)], fill=darker(core, 60) + (255,))
    draw.point((6, 6), fill=lighter(core, 50) + (255,))
    return image


def knife_sprite(kind):
    blade = shade(kind, 0xC8CCD4, 30)
    grip = (94, 70, 46)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.polygon([(3, 11), (10, 4), (12, 6), (5, 13)], fill=blade + (255,))
    draw.line([(2, 12), (4, 14)], fill=grip + (255,), width=2)
    return image


def block_sprite(name, tone):
    core = shade(name, tone, 26)
    image = Image.new("RGBA", (16, 16), core + (255,))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 0], fill=lighter(core, 24) + (255,))
    draw.rectangle([0, 15, 15, 15], fill=darker(core, 30) + (255,))
    draw.rectangle([3, 4, 12, 11], outline=darker(core, 38) + (255,))
    draw.line([(7, 4), (7, 11)], fill=darker(core, 38) + (255,))
    return image


def save(image, *parts):
    path = os.path.join(*parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)


def flat(texture):
    return {"parent": "minecraft:item/generated",
            "textures": {"layer0": f"{NS}:item/{texture}"}}


def draw_and_model():
    for meal in MEALS:
        save(bowl_sprite(meal), ASSETS, "textures/item", meal["id"] + ".png")
        write(os.path.join(ASSETS, "models/item", meal["id"] + ".json"),
              flat(meal["id"]))
    for one in INGREDIENTS:
        save(part_sprite(one), ASSETS, "textures/item", one["id"] + ".png")
        write(os.path.join(ASSETS, "models/item", one["id"] + ".json"),
              flat(one["id"]))
    for kind in KNIVES:
        save(knife_sprite(kind), ASSETS, "textures/item", kind + "_knife.png")
        write(os.path.join(ASSETS, "models/item", kind + "_knife.json"),
              {"parent": "minecraft:item/handheld",
               "textures": {"layer0": f"{NS}:item/{kind}_knife"}})

    blocks = [(name, 0x9A8258) for name in STATIONS.values()]
    blocks += [(wood + "_cabinet", 0xA6845A) for wood in WOODS]
    blocks += [(crop + "_crate", 0xB08A5E) for crop in CRATED]
    for name, tone in blocks:
        save(block_sprite(name, tone), ASSETS, "textures/block", name + ".png")
        write(os.path.join(ASSETS, "models/block", name + ".json"),
              {"parent": "minecraft:block/cube_all",
               "textures": {"all": f"{NS}:block/{name}"}})
        write(os.path.join(ASSETS, "blockstates", name + ".json"),
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}"})
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
    return blocks


OURS = {one["id"] for one in INGREDIENTS} | {meal["id"] for meal in MEALS}


def named(item):
    return f"{NS}:{item}" if item in OURS else f"minecraft:{item}"


def cooking():
    """The meals as data, in this module's own format."""
    for meal in MEALS:
        write(os.path.join(DATA, "cooking", meal["id"] + ".json"), {
            "station": meal["station"],
            "ingredients": [named(part) for part in meal["of"]],
            "ticks": meal["ticks"],
            "servings": meal["servings"],
            "effect": meal["effect"],
            "result": f"{NS}:{meal['id']}",
        })
    for cut in CUTS:
        write(os.path.join(DATA, "cutting", cut["input"] + ".json"), {
            "input": named(cut["input"]),
            "tool": cut["tool"],
            "yields": [{"item": named(name), "count": count}
                       for name, count in sorted(cut["yields"].items())],
        })


def block_recipes(blocks):
    for wood in WOODS:
        write(os.path.join(DATA, "recipe", wood + "_cabinet.json"), {
            "type": "minecraft:crafting_shaped",
            "category": "building",
            "pattern": ["PPP", "P P", "PPP"],
            "key": {"P": {"item": f"minecraft:{wood}_planks"}},
            "result": {"id": f"{NS}:{wood}_cabinet", "count": 1},
        })
    for crop in CRATED:
        write(os.path.join(DATA, "recipe", crop + "_crate.json"), {
            "type": "minecraft:crafting_shaped",
            "category": "building",
            "pattern": ["CCC", "CCC", "CCC"],
            "key": {"C": {"item": named(crop)}},
            "result": {"id": f"{NS}:{crop}_crate", "count": 1},
        })
    station_parts = {
        "cooking_pot": ["minecraft:iron_ingot", "minecraft:bricks"],
        "skillet": ["minecraft:iron_ingot", "minecraft:stick"],
        "cutting_board": ["minecraft:oak_planks", "minecraft:iron_ingot"],
        "stone_oven": ["minecraft:bricks", "minecraft:iron_ingot"],
    }
    for name in STATIONS.values():
        write(os.path.join(DATA, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shapeless",
            "category": "building",
            "ingredients": [{"item": item}
                            for item in station_parts.get(
                                name, ["minecraft:iron_ingot"])],
            "result": {"id": f"{NS}:{name}", "count": 1},
        })
    for index, kind in enumerate(KNIVES):
        metal = {"flint": "minecraft:flint", "iron": "minecraft:iron_ingot",
                 "golden": "minecraft:gold_ingot", "diamond": "minecraft:diamond",
                 "netherite": "minecraft:netherite_ingot"}[kind]
        write(os.path.join(DATA, "recipe", kind + "_knife.json"), {
            "type": "minecraft:crafting_shaped",
            "category": "equipment",
            "pattern": [" M", "S "],
            "key": {"M": {"item": metal}, "S": {"item": "minecraft:stick"}},
            "result": {"id": f"{NS}:{kind}_knife", "count": 1},
        })


def a_name(text):
    return " ".join(word.capitalize() for word in text.split("_"))


def lang(blocks):
    words = {}
    for meal in MEALS:
        words[f"item.{NS}.{meal['id']}"] = a_name(meal["id"])
    for one in INGREDIENTS:
        words[f"item.{NS}.{one['id']}"] = a_name(one["id"])
    for kind in KNIVES:
        words[f"item.{NS}.{kind}_knife"] = a_name(kind) + " Knife"
    for name, tone in blocks:
        words[f"block.{NS}.{name}"] = a_name(name)

    committed = os.path.join(MOD, "src/main/resources/assets", NS, "lang/en_us.json")
    path = os.path.join(ASSETS, "lang/en_us.json")
    existing = json.loads(read(committed)) if os.path.exists(committed) else {}
    for key in set(words):
        existing.pop(key, None)
    existing.update(words)
    write(path, dict(sorted(existing.items())))


def main():
    blocks = draw_and_model()
    cooking()
    block_recipes(blocks)
    lang(blocks)
    print(f"kitchen: {len(MEALS)} meal(s), {len(INGREDIENTS)} ingredient(s), "
          f"{len(CUTS)} cut(s), {len(blocks)} block(s), {len(KNIVES)} knife/knives")


if __name__ == "__main__":
    main()
