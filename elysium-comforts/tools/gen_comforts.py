#!/usr/bin/env python3
"""
Every sprite, model and data file elysium-comforts ships.

    python3 tools/gen_comforts.py

One block, facing four ways. Nobody crafts it; it appears when you need it and
disappears when you have taken your things back.
"""
import json
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

def draw_all():
    folder = os.path.join(ASSETS, "textures/block")
    os.makedirs(folder, exist_ok=True)
    for name, make in SPRITES.items():
        make().save(os.path.join(folder, name + ".png"))
    items = os.path.join(ASSETS, "textures/item")
    os.makedirs(items, exist_ok=True)
    for name, make in ITEM_SPRITES.items():
        make().save(os.path.join(items, name + ".png"))
    return len(SPRITES) + len(ITEM_SPRITES)


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


def data_files():
    written = 0
    for name in BLOCKS:
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"),
              drops_nothing(name))
        written += 1
    tags = os.path.join(OUT, "src/main/resources/data/minecraft/tags/block")
    write(os.path.join(tags, "mineable/pickaxe.json"),
          {"replace": False, "values": [f"{NS}:{name}" for name in BLOCKS]})
    written += 1
    # Wood, a lead and a bed: what you would actually need to move somebody.
    write(os.path.join(DATA, "recipe", "trader_crate.json"), {
        "type": "minecraft:crafting_shaped",
        "pattern": ["PLP", "P P", "PPP"],
        "key": {"P": {"tag": "minecraft:planks"}, "L": {"item": "minecraft:lead"}},
        "result": {"id": f"{NS}:trader_crate", "count": 1},
    })
    written += 1
    return written


# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

NAMES = {"grave": "Grave"}

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
}


def language():
    entries = dict(LINES)
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
