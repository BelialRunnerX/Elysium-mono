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
    with open(path, "w", encoding="utf-8") as handle:
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

    # The base is always the committed file, never whatever happens to be in
    # the output tree. validate.py regenerates into a temporary directory and
    # compares byte for byte; reading the base from there would make the two
    # runs differ for no reason but where they were pointed.
    committed = os.path.join(MOD, "src/main/resources/assets", NS, "lang/en_us.json")
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
                       f"item.{NS}.bee_jar", f"item.{NS}.bee_treat")}
    for key in mine:
        existing.pop(key, None)
    existing.update(words)
    write(path, dict(sorted(existing.items())))


def main():
    draw_all()
    models_and_states()
    loot()
    recipes()
    lang()
    print(f"bees: {len(BEES)} species, {len(HOMES)} nest(s), "
          f"{len(BEES) * 3 + len(HOMES) * 3 + 4} texture(s), "
          f"{len(BEES) * 2} recipe(s)")


if __name__ == "__main__":
    main()
