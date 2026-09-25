#!/usr/bin/env python3
"""
A hundred and sixty-five pots, and the soil and sprout tables behind them.

    python3 tools/gen_pots.py

The three lists are read out of PotRules.java, so a seventeenth colour or an
eighth stone appears here without anybody remembering to come and do it. The
soils and sprouts go out as data files as well as living in the rules, because
a pack that wants to add a crop should be able to drop a JSON in rather than
recompile — and validate.py checks the two agree.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumagronomy"

OUT = os.environ.get("ELYSIUM_POTS_OUT", MOD)
ASSETS = os.path.join(OUT, "src/main/resources/assets", NS)
DATA = os.path.join(OUT, "src/main/resources/data", NS)

RULES = os.path.join(MOD, "src/main/java/com/elysium/agronomy/rules/PotRules.java")

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


def listing(name):
    found = re.search(r"public static final List<String> " + name
                      + r" = List\.of\(([^;]*?)\);", SOURCE, re.S)
    if not found:
        print("could not read", name, "out of PotRules.java")
        sys.exit(2)
    return re.findall(r'"([a-z_]+)"', found.group(1))


COLOURS = listing("COLOURS")
CERAMICS = listing("CERAMICS")
STONES = listing("STONES")
FITTINGS = ["plain", "hopper", "waxed"]

SOILS = [{"id": sid, "tier": int(tier), "speed": float(speed),
          "grows": re.findall(r'"([a-z_]+)"', grows)}
         for sid, tier, speed, grows in re.findall(
             r'new Soil\("([a-z_]+)", (\d+), ([\d.]+)F, List\.of\(([^)]*)\)\)', SOURCE)]

SPROUTS = [{"id": sid, "needs": needs, "ticks": int(ticks), "drop": drop,
            "amount": int(amount)}
           for sid, needs, ticks, drop, amount in re.findall(
               r'new Sprout\("([a-z_]+)", "([a-z_]+)", (\d+), "([a-z_]+)", (\d+)\)',
               SOURCE)]

if not (COLOURS and CERAMICS and STONES and SOILS and SPROUTS):
    print("PotRules.java no longer has the shape this reads")
    sys.exit(2)

MATERIALS = [f"{colour}_{ceramic}" for ceramic in CERAMICS for colour in COLOURS]
MATERIALS += STONES


# A material whose block item is not called what the material is called.
#
# `minecraft:quartz` is the gem you dig up and the block is `quartz_block`, so
# a pot recipe written the obvious way asks for three gemstones and reads
# wrong on the recipe screen. `minecraft:amethyst` and `minecraft:dripstone`
# are worse than wrong: they do not exist at all, and a recipe naming one
# fails to load with "unknown registry key" at the moment a player opens a
# world. The load test caught both the day the two materials were added.
BLOCK_ITEM = {"quartz": "quartz_block", "amethyst": "amethyst_block",
              "dripstone": "dripstone_block"}


# The soils this module makes rather than the game. They are registered as
# blocks by PotBlocks, and validate.py checks the two lists agree.
OURS = ("clay_bed", "gravel_bed", "rich_loam", "ashen_bed", "void_silt")


def pot_id(material, fitting):
    return (material + "_botany_pot" if fitting == "plain"
            else f"{material}_{fitting}_botany_pot")


POTS = [(material, fitting) for material in MATERIALS for fitting in FITTINGS]


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


DYE = {
    "white": (233, 236, 236), "orange": (240, 118, 19), "magenta": (189, 68, 179),
    "light_blue": (58, 175, 217), "yellow": (248, 198, 39), "lime": (112, 185, 25),
    "pink": (237, 141, 172), "gray": (62, 68, 71), "light_gray": (142, 142, 134),
    "cyan": (21, 137, 145), "purple": (121, 42, 172), "blue": (53, 57, 157),
    "brown": (114, 71, 40), "green": (84, 109, 27), "red": (161, 39, 34),
    "black": (20, 21, 25),
}
STONE_TONE = {
    "stone": (125, 125, 125), "deepslate": (77, 77, 81), "tuff": (108, 109, 102),
    "blackstone": (42, 36, 41), "calcite": (223, 221, 214), "quartz": (231, 226, 218),
    "obsidian": (24, 18, 35),
}


def body(material):
    if material in STONE_TONE:
        return STONE_TONE[material]
    colour, ceramic = material.rsplit("_", 1)[0], material.rsplit("_", 1)[-1]
    for kind in CERAMICS:
        if material.endswith("_" + kind):
            colour = material[: -len(kind) - 1]
            ceramic = kind
            break
    base = DYE.get(colour, (150, 150, 150))
    if ceramic == "terracotta":
        return tuple(int(channel * 0.62 + 90 * 0.38) for channel in base)
    if ceramic == "glazed_terracotta":
        return tuple(min(255, int(channel * 1.08)) for channel in base)
    return base


def darker(colour, by=40):
    return tuple(max(0, channel - by) for channel in colour)


def lighter(colour, by=34):
    return tuple(min(255, channel + by) for channel in colour)


def pot_sprite(material, fitting):
    core = body(material)
    rim = lighter(core, 26)
    shade = darker(core, 46)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 4, 13, 15], fill=core + (255,))
    draw.rectangle([1, 2, 14, 4], fill=rim + (255,))
    draw.line([(2, 15), (13, 15)], fill=shade + (255,))
    draw.line([(2, 4), (2, 15)], fill=shade + (255,))
    draw.line([(13, 4), (13, 15)], fill=lighter(core, 12) + (255,))
    if fitting == "hopper":
        draw.polygon([(5, 13), (10, 13), (8, 15), (7, 15)], fill=(58, 58, 64, 255))
    elif fitting == "waxed":
        draw.line([(4, 6), (6, 6)], fill=(255, 245, 205, 190))
        draw.line([(4, 7), (5, 7)], fill=(255, 245, 205, 140))
    return image


def soil_top(material, step=0):
    """The soil seen from above, with however much has come up out of it."""
    core = darker(body(material), 20)
    image = Image.new("RGBA", (16, 16), core + (255,))
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 3, 12, 12], fill=(78, 58, 40, 255))
    for spot in ((5, 6), (9, 5), (7, 9), (10, 10), (4, 10)):
        draw.point(spot, fill=(58, 42, 28, 255))
    green = (74 + step * 18, 116 + step * 22, 52 + step * 12)
    for leaf in range(step + 1):
        column = 5 + leaf * 2
        if column > 11:
            break
        draw.line([(column, 11 - step), (column, 11)], fill=green + (255,))
        if step >= 2:
            draw.point((column - 1, 10 - step), fill=green + (255,))
            draw.point((column + 1, 10 - step), fill=green + (255,))
    return image


SOIL_TONE = {
    "clay_bed": (162, 168, 178), "gravel_bed": (128, 124, 120),
    "rich_loam": (86, 62, 40), "ashen_bed": (74, 66, 62),
    "void_silt": (58, 48, 74),
}


def soil_sprite(soil):
    core = SOIL_TONE.get(soil, (110, 90, 70))
    image = Image.new("RGBA", (16, 16), core + (255,))
    draw = ImageDraw.Draw(image)
    seed = java_hash(soil)
    for step in range(28):
        seed = (seed * 1103515245 + 12345) & 0x7FFFFFFF
        x = seed % 16
        seed = (seed * 1103515245 + 12345) & 0x7FFFFFFF
        y = seed % 16
        draw.point((x, y), fill=(darker(core, 24) if (x + y) % 2 else
                                 lighter(core, 18)) + (255,))
    return image


def save(image, *parts):
    path = os.path.join(*parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)


def draw_all():
    for material, fitting in POTS:
        save(pot_sprite(material, fitting), ASSETS, "textures/block",
             pot_id(material, fitting) + ".png")
    for material in MATERIALS:
        for step in range(AGES):
            save(soil_top(material, step), ASSETS, "textures/block",
                 f"{material}_botany_pot_top_{step}.png")
    for soil in OURS:
        save(soil_sprite(soil), ASSETS, "textures/block", soil + ".png")


#: The pot's box, which has to be the shape PotBlock's collision uses.
POT_BOX = (2, 0, 2, 14, 12, 14)

#: How many steps of growth the blockstate carries, from PotBlock's own
#: age property. Four variants are not optional: a blockstate with one
#: variant and no property against a block that has one is a file the game
#: refuses to load, and the symptom is a missing model for every pot.
AGES = 4


def box_model(name, material, step):
    low, floor, near, high, ceiling, far = POT_BOX
    return {
        "parent": "block/block",
        "textures": {
            "top": f"{NS}:block/{material}_botany_pot_top_{step}",
            "side": f"{NS}:block/{name}",
            "bottom": f"{NS}:block/{name}",
            "particle": f"{NS}:block/{name}",
        },
        "elements": [{
            "from": [low, floor, near],
            "to": [high, ceiling, far],
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


def models_and_states():
    for material, fitting in POTS:
        name = pot_id(material, fitting)
        for step in range(AGES):
            write(os.path.join(ASSETS, "models/block", f"{name}_{step}.json"),
                  box_model(name, material, step))
        write(os.path.join(ASSETS, "blockstates", name + ".json"),
              {"variants": {f"age={step}": {"model": f"{NS}:block/{name}_{step}"}
                            for step in range(AGES)}})
        write(os.path.join(ASSETS, "models/item", name + ".json"),
              {"parent": f"{NS}:block/{name}_0"})
    for soil in OURS:
        write(os.path.join(ASSETS, "models/block", soil + ".json"),
              {"parent": "minecraft:block/cube_all",
               "textures": {"all": f"{NS}:block/{soil}"}})
        write(os.path.join(ASSETS, "blockstates", soil + ".json"),
              {"variants": {"": {"model": f"{NS}:block/{soil}"}}})
        write(os.path.join(ASSETS, "models/item", soil + ".json"),
              {"parent": f"{NS}:block/{soil}"})


def loot():
    for material, fitting in POTS:
        name = pot_id(material, fitting)
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
    for soil in OURS:
        write(os.path.join(DATA, "loot_table/blocks", soil + ".json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{soil}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })


def recipes():
    for material, fitting in POTS:
        name = pot_id(material, fitting)
        plain = pot_id(material, "plain")
        if fitting == "plain":
            write(os.path.join(DATA, "recipe", name + ".json"), {
                "type": "minecraft:crafting_shaped",
                "category": "misc",
                "pattern": ["M M", " M "],
                "key": {"M": {"item": "minecraft:" + BLOCK_ITEM.get(material,
                                                                     material)}},
                "result": {"id": f"{NS}:{name}", "count": 1},
            })
        elif fitting == "hopper":
            write(os.path.join(DATA, "recipe", name + ".json"), {
                "type": "minecraft:crafting_shapeless",
                "category": "misc",
                "ingredients": [{"item": f"{NS}:{plain}"},
                                {"item": "minecraft:hopper"}],
                "result": {"id": f"{NS}:{name}", "count": 1},
            })
        else:
            write(os.path.join(DATA, "recipe", name + ".json"), {
                "type": "minecraft:crafting_shapeless",
                "category": "misc",
                "ingredients": [{"item": f"{NS}:{plain}"},
                                {"item": "minecraft:honeycomb"}],
                "result": {"id": f"{NS}:{name}", "count": 1},
            })


SOIL_RECIPE = {
    "clay_bed": ["minecraft:clay", "minecraft:dirt", "minecraft:water_bucket"],
    "gravel_bed": ["minecraft:gravel", "minecraft:dirt", "minecraft:sand"],
    "rich_loam": ["minecraft:dirt", "minecraft:bone_meal", "minecraft:clay_ball"],
    "ashen_bed": ["minecraft:soul_soil", "minecraft:gravel", "minecraft:blaze_powder"],
    "void_silt": ["minecraft:end_stone", "minecraft:gravel", "minecraft:ender_pearl"],
}


def soil_recipes():
    for soil in OURS:
        write(os.path.join(DATA, "recipe", soil + ".json"), {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": [{"item": item} for item in SOIL_RECIPE[soil]],
            "result": {"id": f"{NS}:{soil}", "count": 2},
        })


def tables():
    """The soils and sprouts as data, so a pack can add one without a compiler."""
    for soil in SOILS:
        write(os.path.join(DATA, "botany_soil", soil["id"] + ".json"), {
            "block": (f"{NS}:{soil['id']}" if soil["id"] in OURS
                      else "minecraft:" + soil["id"]),
            "tier": soil["tier"],
            "speed": soil["speed"],
            "grows": soil["grows"],
        })
    for sprout in SPROUTS:
        write(os.path.join(DATA, "botany_sprout", sprout["id"] + ".json"), {
            "needs": sprout["needs"],
            "ticks": sprout["ticks"],
            "drop": sprout["drop"],
            "amount": sprout["amount"],
        })


def a_name(text):
    return " ".join(word.capitalize() for word in text.split("_"))


def lang():
    words = {}
    for material, fitting in POTS:
        name = pot_id(material, fitting)
        shown = a_name(material) + " Botany Pot"
        if fitting == "hopper":
            shown = a_name(material) + " Hopper Botany Pot"
        elif fitting == "waxed":
            shown = "Waxed " + a_name(material) + " Botany Pot"
        words[f"block.{NS}.{name}"] = shown
    for soil in OURS:
        words[f"block.{NS}.{soil}"] = a_name(soil)

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
    existing = json.loads(read(committed)) if os.path.exists(committed) else {}
    ours = {f"block.{NS}.{pot_id(m, f)}" for m, f in POTS}
    ours |= {f"block.{NS}.{soil}" for soil in OURS}
    for key in ours:
        existing.pop(key, None)
    existing.update(words)
    write(path, dict(sorted(existing.items())))


def main():
    draw_all()
    models_and_states()
    loot()
    recipes()
    soil_recipes()
    tables()
    lang()
    print(f"pots: {len(POTS)} across {len(MATERIALS)} material(s), "
          f"{len(SOILS)} soil(s), {len(SPROUTS)} sprout(s), "
          f"{len(POTS) + len(MATERIALS) * AGES + len(OURS)} texture(s)")


if __name__ == "__main__":
    main()
