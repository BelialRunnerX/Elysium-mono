#!/usr/bin/env python3
"""
Seven generators, one per vein, read out of AgronomyRules.

    python3 tools/gen_veins.py

The mod this replaces ships seven block classes that differ in one field. The
table is in the rules, this draws what the table says, and validate.py checks
that the shares in it stay under what a mine is worth — because a generator
that hands you netherite is the most direct way there is to make three ages of
ore ladder pointless.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumagronomy"

OUT = os.environ.get("ELYSIUM_VEINS_OUT", MOD)
ASSETS = os.path.join(OUT, "src/main/resources/assets", NS)
DATA = os.path.join(OUT, "src/main/resources/data", NS)

RULES = os.path.join(MOD, "src/main/java/com/elysium/agronomy/rules/AgronomyRules.java")

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
VEINS = [{"material": m, "tier": int(t), "share": float(s)}
         for m, t, s in re.findall(
             r'new Vein\("([a-z_]+)", (\d+), ([\d.]+)F\)', SOURCE)]
if not VEINS:
    print("no veins were read out of AgronomyRules.java")
    sys.exit(2)

TONE = {
    "stone": (128, 128, 128), "copper": (196, 118, 78), "iron": (216, 216, 216),
    "gold": (244, 208, 92), "diamond": (110, 226, 216), "emerald": (66, 194, 106),
    "netherite_scrap": (112, 86, 76),
}


def darker(colour, by=40):
    return tuple(max(0, channel - by) for channel in colour)


def lighter(colour, by=34):
    return tuple(min(255, channel + by) for channel in colour)


def side(vein):
    core = TONE.get(vein["material"], (150, 150, 150))
    steel = (96, 100, 106)
    image = Image.new("RGBA", (16, 16), steel + (255,))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 1], fill=lighter(steel, 26) + (255,))
    draw.rectangle([0, 14, 15, 15], fill=darker(steel, 26) + (255,))
    draw.rectangle([3, 4, 12, 11], fill=darker(steel, 18) + (255,))
    draw.rectangle([5, 6, 10, 9], fill=core + (255,))
    draw.line([(5, 6), (10, 6)], fill=lighter(core, 30) + (255,))
    for rung in range(vein["tier"] + 1):
        draw.point((2, 13 - rung), fill=(240, 226, 150, 255))
    return image


def top(vein):
    core = TONE.get(vein["material"], (150, 150, 150))
    steel = (110, 114, 120)
    image = Image.new("RGBA", (16, 16), steel + (255,))
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 2, 13, 13], outline=darker(steel, 28) + (255,))
    draw.rectangle([6, 6, 9, 9], fill=core + (255,))
    return image


def save(image, *parts):
    path = os.path.join(*parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)


def a_name(text):
    return " ".join(word.capitalize() for word in text.split("_"))


def main():
    for vein in VEINS:
        name = vein["material"] + "_generator"
        save(side(vein), ASSETS, "textures/block", name + "_side.png")
        save(top(vein), ASSETS, "textures/block", name + "_top.png")
        write(os.path.join(ASSETS, "models/block", name + ".json"), {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {"top": f"{NS}:block/{name}_top",
                         "bottom": f"{NS}:block/{name}_top",
                         "side": f"{NS}:block/{name}_side"},
        })
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
        # Every generator is the rung below it plus a block of what it makes,
        # so the ladder is walked rather than jumped and the recipe says which
        # vein it is without anybody reading a tooltip.
        base = (f"{NS}:rock_generator" if vein["tier"] == 0
                else f"{NS}:driven_rock_generator" if vein["tier"] == 1
                else f"{NS}:industrial_rock_generator")
        marker = {"stone": "minecraft:stone", "copper": "minecraft:copper_block",
                  "iron": "minecraft:iron_block", "gold": "minecraft:gold_block",
                  "diamond": "minecraft:diamond_block",
                  "emerald": "minecraft:emerald_block",
                  "netherite_scrap": "minecraft:ancient_debris"}[vein["material"]]
        write(os.path.join(DATA, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": [{"item": base}, {"item": marker}],
            "result": {"id": f"{NS}:{name}", "count": 1},
        })

    words = {}
    for vein in VEINS:
        words[f"block.{NS}.{vein['material']}_generator"] = \
            a_name(vein["material"]) + " Generator"
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
    for key in {f"block.{NS}.{v['material']}_generator" for v in VEINS}:
        existing.pop(key, None)
    existing.update(words)
    write(path, dict(sorted(existing.items())))

    print(f"veins: {len(VEINS)} generator(s), {len(VEINS) * 2} texture(s)")


if __name__ == "__main__":
    main()
