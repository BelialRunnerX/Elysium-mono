#!/usr/bin/env python3
"""Contact sheets and a player-model mock, so the set can be eyeballed."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image, ImageDraw

ROOT = "/tmp/elysium_work/work/src/main/resources/assets/elysium/textures"
BG = (22, 20, 30, 255)
SCALE = 11


def load(rel):
    return Image.open(os.path.join(ROOT, rel)).convert("RGBA")


def sheet(names, cols, path, scale=SCALE):
    rows = (len(names) + cols - 1) // cols
    img = Image.new("RGBA", (cols * 16, rows * 16), BG)
    for i, name in enumerate(names):
        img.alpha_composite(load(name), ((i % cols) * 16, (i // cols) * 16))
    img = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
    img.save(path)
    return path


# Front-facing faces of each box in the 64x32 armour layout.
FRONT = {
    "head": (8, 8, 8, 8),     # x, y, w, h
    "body": (20, 20, 8, 12),
    "arm":  (44, 20, 4, 12),
    "leg":  (4, 20, 4, 12),
}

# Where those faces sit on a front view of the player.
PLACE = {
    "head":  (4, 0),
    "body":  (4, 8),
    "arm_l": (0, 8),
    "arm_r": (12, 8),
    "leg_l": (4, 20),
    "leg_r": (8, 20),
}


def crop(sheet_img, key):
    x, y, w, h = FRONT[key]
    return sheet_img.crop((x, y, x + w, y + h))


def model_mock(layer1, layer2, path, scale=12):
    """
    Composite the front faces onto a player-shaped figure. Not a render — but
    it does prove the art lands on the right part of the body, which is the
    thing that was actually wrong before.
    """
    one = load(layer1)
    two = load(layer2)

    skin = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(skin)
    flesh = (150, 122, 100, 255)
    draw.rectangle([4, 0, 11, 7], fill=flesh)
    draw.rectangle([4, 8, 11, 19], fill=(90, 120, 170, 255))
    draw.rectangle([0, 8, 3, 19], fill=flesh)
    draw.rectangle([12, 8, 15, 19], fill=flesh)
    draw.rectangle([4, 20, 7, 31], fill=(70, 70, 110, 255))
    draw.rectangle([8, 20, 11, 31], fill=(70, 70, 110, 255))

    # Leggings sit under the chestplate and boots.
    skin.alpha_composite(crop(two, "body"), PLACE["body"])
    skin.alpha_composite(crop(two, "leg"), PLACE["leg_l"])
    skin.alpha_composite(crop(two, "leg"), PLACE["leg_r"])

    skin.alpha_composite(crop(one, "head"), PLACE["head"])
    skin.alpha_composite(crop(one, "body"), PLACE["body"])
    skin.alpha_composite(crop(one, "arm"), PLACE["arm_l"])
    skin.alpha_composite(crop(one, "arm"), PLACE["arm_r"])
    skin.alpha_composite(crop(one, "leg"), PLACE["leg_l"])
    skin.alpha_composite(crop(one, "leg"), PLACE["leg_r"])

    out = Image.new("RGBA", (20, 34), BG)
    out.alpha_composite(skin, (2, 1))
    out = out.resize((out.width * scale, out.height * scale), Image.NEAREST)
    out.save(path)
    return path


def side_by_side(paths, path, pad=16):
    images = [Image.open(p).convert("RGBA") for p in paths]
    width = sum(i.width for i in images) + pad * (len(images) + 1)
    height = max(i.height for i in images) + pad * 2
    out = Image.new("RGBA", (width, height), BG)
    x = pad
    for image in images:
        out.alpha_composite(image, (x, pad))
        x += image.width + pad
    out.save(path)
    return path


if __name__ == "__main__":
    sheet([
        "item/neutronium_ingot.png", "item/aetherium_ingot.png",
        "item/voidglass_ingot.png", "item/elysium_reforge.png",
        "item/emperor_crown.png",
        "item/voidward_rune.png", "item/plasmaforge_rune.png",
        "item/neuralspike_rune.png", "item/dimensionalshift_rune.png",
        "item/kineticsurge_rune.png",
    ], 5, "/tmp/prev_items.png")

    sheet([
        "item/voidglass_hammer.png", "item/aetherium_hammer.png",
        "item/neutronium_hammer.png",
        "item/voidglass_broadaxe.png", "item/aetherium_broadaxe.png",
        "item/neutronium_broadaxe.png",
        "item/voidglass_scythe.png", "item/aetherium_scythe.png",
        "item/neutronium_scythe.png",
        "item/voidglass_spear.png", "item/aetherium_spear.png",
        "item/neutronium_spear.png",
    ], 3, "/tmp/prev_tools.png")

    sheet([
        "item/elysium_helmet.png", "item/plasma_chestplate.png",
        "item/neural_leggings.png", "item/dimensional_boots.png",
        "item/neutronium_helmet.png", "item/neutronium_chestplate.png",
        "item/neutronium_leggings.png", "item/neutronium_boots.png",
    ], 4, "/tmp/prev_armour.png")

    sheet([
        "block/neutronium_ore.png", "block/aetherium_ore.png",
        "block/voidglass_ore.png", "block/neutronium_block.png",
        "block/reforge_table.png", "block/rune_socket_table.png",
        "block/ascension_forge.png",
    ], 4, "/tmp/prev_blocks.png")

    model_mock("models/armor/elysium_layer_1.png",
               "models/armor/elysium_layer_2.png", "/tmp/mock_elysium.png")
    model_mock("models/armor/neutronium_layer_1.png",
               "models/armor/neutronium_layer_2.png", "/tmp/mock_neutronium.png")
    side_by_side(["/tmp/mock_elysium.png", "/tmp/mock_neutronium.png"], "/tmp/prev_worn.png")

    print("previews written")
