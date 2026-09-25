#!/usr/bin/env python3
"""
Every texture this module needs, built from vanilla's.

The same method the ore module uses: a vanilla texture is the shape, and the
recolour keeps that shape's own shading, so the result reads as something a
person drew rather than something a script filled in.

  * the crops are wheat's eight stages, hue-shifted per plant, so the
    silhouette of a half-grown field is one a player already knows how to read;
  * emberwood is crimson stem and planks pushed towards ash and ember;
  * the items are wheat, wheat seeds and charcoal, recoloured to match the
    blocks they come from.
"""
import colorsys
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
BASE = os.path.join(HERE, "base")
OUT = os.path.join(MOD, "src/main/resources/assets/elysiumflora/textures")


def load(name):
    return Image.open(os.path.join(BASE, name)).convert("RGBA")


def save(image, folder, name, source=None):
    """
    Write the texture, and its animation metadata when the source has any.

    Crimson stem is five frames stacked in one 16x80 image, and the game only
    knows that from the .mcmeta beside it. A recolour that copied the pixels
    and not the metadata shipped a 16x80 "still" that the atlas squashed onto
    every log face — so whatever the source says about its frames, the copy
    says too.
    """
    os.makedirs(os.path.join(OUT, folder), exist_ok=True)
    image.save(os.path.join(OUT, folder, name + ".png"))
    target = os.path.join(OUT, folder, name + ".png.mcmeta")
    meta = os.path.join(BASE, source + ".mcmeta") if source else None
    if meta and os.path.isfile(meta):
        with open(meta, encoding="utf-8") as src, open(target, "w", encoding="utf-8", newline="\n") as dst:
            dst.write(src.read())
    elif os.path.isfile(target):
        os.remove(target)


def recolour(image, hue, saturation=1.0, value=1.0, floor=0.0):
    """
    Move every pixel to a new hue, keeping its own brightness.

    Hue is set rather than rotated: a source with several hues in it (crimson
    stem has three) would otherwise come out in several hues of the target,
    which reads as a texture somebody got wrong rather than a new wood.
    """
    out = image.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            _, lightness, existing = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            wanted = max(floor, min(1.0, existing * saturation))
            red, green, blue = colorsys.hls_to_rgb(
                hue, min(1.0, lightness * value), wanted)
            pixels[x, y] = (int(red * 255), int(green * 255), int(blue * 255), a)
    return out


#: Where each plant sits on the wheel. Loomflax is the blue-green of retted
#: flax; downbloom is the warm off-white of raw cotton.
LOOMFLAX_HUE = 0.46
DOWNBLOOM_HUE = 0.11

#: Emberwood: a bark that has been in a fire and leaves that have not gone out.
EMBER_BARK_HUE = 0.045
EMBER_LEAF_HUE = 0.075


def crops():
    written = 0
    for name, hue, saturation, value in (
            ("loomflax", LOOMFLAX_HUE, 0.75, 1.05),
            ("downbloom", DOWNBLOOM_HUE, 0.45, 1.15)):
        for stage in range(8):
            source = load(f"wheat_stage{stage}.png")
            save(recolour(source, hue, saturation, value), "block",
                 f"{name}_stage{stage}")
            written += 1
        # The wild form is the last stage, paler and shorter-looking - it is
        # the same plant that nobody has been looking after.
        wild = recolour(load("wheat_stage5.png"), hue, saturation * 0.7, value * 0.95)
        save(wild, "block", f"wild_{name}")
        written += 1
    return written


def wood():
    pairs = [
        ("crimson_stem.png", "emberwood_log", EMBER_BARK_HUE, 0.85, 0.95),
        ("crimson_stem_top.png", "emberwood_log_top", EMBER_BARK_HUE, 0.85, 0.95),
        ("stripped_crimson_stem.png", "stripped_emberwood_log",
         EMBER_BARK_HUE, 0.55, 1.15),
        ("stripped_crimson_stem_top.png", "stripped_emberwood_log_top",
         EMBER_BARK_HUE, 0.55, 1.15),
        ("crimson_planks.png", "emberwood_planks", EMBER_BARK_HUE, 0.55, 1.10),
        # A floor, because vanilla's oak leaves are grey and coloured by the
        # biome; emberwood's are not tinted and need their own colour.
        ("oak_leaves.png", "emberwood_leaves", EMBER_LEAF_HUE, 1.05, 0.95, 0.62),
        ("crimson_fungus.png", "emberwood_sapling", EMBER_LEAF_HUE, 1.05, 1.05),
        ("crimson_door_top.png", "emberwood_door_top", EMBER_BARK_HUE, 0.55, 1.10),
        ("crimson_door_bottom.png", "emberwood_door_bottom", EMBER_BARK_HUE, 0.55, 1.10),
        ("crimson_trapdoor.png", "emberwood_trapdoor", EMBER_BARK_HUE, 0.55, 1.10),
        ("hay_block_side.png", "downbloom_bale_side", DOWNBLOOM_HUE, 0.35, 1.20),
        ("hay_block_top.png", "downbloom_bale_top", DOWNBLOOM_HUE, 0.35, 1.20),
        ("coal_block.png", "emberwood_charcoal_block", EMBER_BARK_HUE, 0.35, 0.85),
    ]
    for row in pairs:
        source, name, hue, saturation, value = row[:5]
        floor = row[5] if len(row) > 5 else 0.0
        save(recolour(load(source), hue, saturation, value, floor), "block", name, source)
    return len(pairs)


def items():
    pairs = [
        ("item_wheat.png", "loomflax_fibre", LOOMFLAX_HUE, 0.75, 1.05),
        ("item_wheat_seeds.png", "loomflax_seeds", LOOMFLAX_HUE, 0.70, 1.00),
        ("item_string.png", "downbloom_tuft", DOWNBLOOM_HUE, 0.35, 1.15),
        ("item_wheat_seeds.png", "downbloom_seeds", DOWNBLOOM_HUE, 0.50, 1.05),
        ("item_charcoal.png", "emberwood_charcoal", EMBER_BARK_HUE, 0.40, 0.95),
    ]
    for source, name, hue, saturation, value in pairs:
        save(recolour(load(source), hue, saturation, value), "item", name)
    return len(pairs)


def main():
    for folder in ("block", "item"):
        path = os.path.join(OUT, folder)
        if os.path.isdir(path):
            for old in os.listdir(path):
                if old.endswith(".png"):
                    os.remove(os.path.join(path, old))
    print(f"crop textures     : {crops()}")
    print(f"wood textures     : {wood()}")
    print(f"item textures     : {items()}")


if __name__ == "__main__":
    main()
