#!/usr/bin/env python3
"""
A texture for every ore, built rather than drawn.

A hundred and fifty-six ores is more than anyone is going to draw by hand, and
a hundred and fifty-six flat recolours would look like exactly what they are.
So each texture is assembled from two things that are already right:

  * the host stone's own texture, unaltered, so an ore in deepslate reads as
    deepslate and an ore in cinder stone reads as Cinderveil; and
  * a speck shape lifted out of a real vanilla ore - the pixels where
    iron_ore.png differs from stone.png are exactly the pixels vanilla's
    artist decided were ore - recoloured to the material.

The recolour keeps the shape's own luminance, so the highlight and shadow the
original artist put on each speck survive into every material. A flat fill
would lose them and the result would look like a sticker.

Six different vanilla ores supply six speck shapes, chosen per material by a
stable hash of its name, so neighbouring materials do not share a silhouette.
"""
import hashlib
import json
import os
import re

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
BASE = os.path.join(HERE, "base")
OUT = os.path.join(MOD, "src/main/resources/assets/elysiumores/textures/block")
JAVA = os.path.join(MOD, "src/main/java/com/elysium/ores")

# ---------------------------------------------------------------------------
# The stones, and which vanilla texture each one is.
# ---------------------------------------------------------------------------
HOST_TEXTURE = {
    "stone": "stone.png",
    "deepslate": "deepslate.png",
    "netherrack": "netherrack.png",
    "end_stone": "end_stone.png",
    "cinder": "cinder_stone.png",
    "frost": "frost_stone.png",
    "pale": "pale_stone.png",
}

# Speck shapes, each as (ore texture, the stone it was drawn on). The
# difference between the two is the shape.
SHAPES = [
    ("iron_ore.png", "stone.png"),
    ("gold_ore.png", "stone.png"),
    ("copper_ore.png", "stone.png"),
    ("coal_ore.png", "stone.png"),
    ("diamond_ore.png", "stone.png"),
    ("lapis_ore.png", "stone.png"),
    ("redstone_ore.png", "stone.png"),
    ("emerald_ore.png", "stone.png"),
]

# Deepslate ores have their own, slightly larger, specks. Used for the
# deepslate host so it keeps looking like deepslate ore.
DEEP_SHAPES = [
    ("deepslate_iron_ore.png", "deepslate.png"),
    ("deepslate_gold_ore.png", "deepslate.png"),
    ("deepslate_copper_ore.png", "deepslate.png"),
    ("deepslate_coal_ore.png", "deepslate.png"),
    ("deepslate_diamond_ore.png", "deepslate.png"),
    ("deepslate_lapis_ore.png", "deepslate.png"),
    ("deepslate_redstone_ore.png", "deepslate.png"),
    ("deepslate_emerald_ore.png", "deepslate.png"),
]

# ---------------------------------------------------------------------------
# What each material looks like.
#
# Chosen to match the item the family resolves to wherever a player has seen
# one - copper is copper, uranium is the green every mod agrees on - and to
# stay distinguishable from its neighbours where nobody has an opinion.
# ---------------------------------------------------------------------------
COLOURS = {
    "coal":          (0x20, 0x20, 0x20),
    "copper":        (0xC8, 0x6A, 0x3C),
    "iron":          (0xD8, 0xAF, 0x93),
    "redstone":      (0xD0, 0x1C, 0x14),
    "lapis":         (0x1C, 0x4C, 0xA8),
    "quartz":        (0xEC, 0xE4, 0xDC),
    "tin":           (0xC8, 0xD4, 0xD8),
    "zinc":          (0xB8, 0xBC, 0x9C),
    "lead":          (0x6C, 0x70, 0x8C),
    "nickel":        (0xC4, 0xC0, 0x90),
    "aluminum":      (0xD4, 0xDC, 0xE0),
    "lithium":       (0xE0, 0xD0, 0xE8),
    "silver":        (0xE0, 0xE8, 0xEC),
    "steel":         (0x8C, 0x94, 0x9C),
    "gold":          (0xF0, 0xC8, 0x3C),
    "diamond":       (0x5C, 0xE8, 0xE0),
    "emerald":       (0x2C, 0xD0, 0x5C),
    "platinum":      (0xBC, 0xDC, 0xE4),
    "fluorite":      (0x9C, 0xE0, 0xD4),
    "certus_quartz": (0xB4, 0xC8, 0xE8),
    "osmium":        (0xA8, 0xC0, 0xD8),
    "uranium":       (0x5C, 0xE0, 0x38),
    "uraninite":     (0x74, 0xD8, 0x44),
    "crimson_iron":  (0xC0, 0x3C, 0x38),
    "azure_silver":  (0x7C, 0xB4, 0xE8),
    "bort":          (0x8C, 0xA0, 0xA8),
    "cindrium":      (0xE8, 0x74, 0x28),
    "cryonite":      (0x6C, 0xC8, 0xF0),
    "frostite":      (0xB4, 0xE4, 0xF0),
    "lunite":        (0xD8, 0xCC, 0xE8),
    "rime_shard":    (0xC8, 0xF0, 0xF4),
}


def load(name):
    return Image.open(os.path.join(BASE, name)).convert("RGBA")


def shape_of(ore_name, stone_name):
    """
    The pixels where an ore texture differs from the stone it was drawn on.

    Returns a list of (x, y, luminance) - the position of each speck pixel and
    how bright the original artist made it, which is what carries the shading
    through into every recolour.
    """
    ore = load(ore_name)
    stone = load(stone_name)
    width, height = ore.size
    specks = []
    for y in range(height):
        for x in range(width):
            a = ore.getpixel((x, y))
            b = stone.getpixel((x, y))
            if a[:3] == b[:3]:
                continue
            luminance = (a[0] * 299 + a[1] * 587 + a[2] * 114) / 255000.0
            specks.append((x, y, luminance))
    return specks


def normalise(specks):
    """
    Rescale the shape's luminance to fill [0.45, 1.15].

    Without this a shape lifted from coal (almost black) would recolour every
    material to almost black, and one lifted from diamond would blow every
    material out to white. The shape is a shape; its own brightness is not
    part of what is being reused.
    """
    if not specks:
        return specks
    lows = min(l for _, _, l in specks)
    highs = max(l for _, _, l in specks)
    span = max(1e-6, highs - lows)
    return [(x, y, 0.45 + 0.70 * (l - lows) / span) for x, y, l in specks]


def tint(colour, factor):
    return tuple(max(0, min(255, int(round(channel * factor)))) for channel in colour)


def luminance(colour):
    return (colour[0] * 299 + colour[1] * 587 + colour[2] * 114) / 255000.0


def mean_luminance(image):
    total = 0.0
    for y in range(image.height):
        for x in range(image.width):
            total += luminance(image.getpixel((x, y)))
    return total / (image.width * image.height)


#: How close in brightness a material may be to its host before it stops
#: reading as a separate thing. Below this the material is pushed away.
CONTRAST_FLOOR = 0.20

#: How far it is pushed. Enough to be seen across a cave; not so far that
#: silver stops looking like silver.
CONTRAST_PUSH = 0.30


def separated(colour, host):
    """
    The material's colour, moved away from the host's brightness if it is too
    close to it.

    Tin on pale stone is the case this exists for: both are almost white, and
    without this the ore is invisible until a player is standing on it. The
    hue is kept and only the brightness moves, so the material still reads as
    the metal it is - a darker tin is still tin, and an indistinguishable one
    is not anything.
    """
    gap = luminance(colour) - host
    if abs(gap) >= CONTRAST_FLOOR:
        return colour
    direction = -1.0 if host > 0.5 else 1.0
    factor = 1.0 + direction * CONTRAST_PUSH
    if direction < 0:
        return tint(colour, factor)
    lifted = tuple(int(round(channel + (255 - channel) * CONTRAST_PUSH))
                   for channel in colour)
    return lifted


def pick(family, shapes):
    """A stable shape per material, so a regeneration does not reshuffle them."""
    digest = hashlib.sha256(family.encode("utf-8")).digest()
    return shapes[digest[0] % len(shapes)]


def catalogue():
    """The rows, read back out of the generated Java so there is one list."""
    src = open(os.path.join(JAVA, "data/OreCatalogue.java"), encoding="utf-8").read()
    rows = re.findall(r'new Entry\("([a-z0-9_]+)", "([a-z0-9_]+)", "([a-z0-9_]+)", (\d+)',
                      src)
    return [(name, family, host, int(band)) for name, family, host, band in rows]


def main():
    os.makedirs(OUT, exist_ok=True)
    for old in os.listdir(OUT):
        if old.endswith(".png"):
            os.remove(os.path.join(OUT, old))

    shallow = {name: normalise(shape_of(*pair)) for name, pair in
               {f"{a}": (a, b) for a, b in SHAPES}.items()}
    deep = {name: normalise(shape_of(*pair)) for name, pair in
            {f"{a}": (a, b) for a, b in DEEP_SHAPES}.items()}
    shallow_names = sorted(shallow)
    deep_names = sorted(deep)

    hosts = {host: load(texture) for host, texture in HOST_TEXTURE.items()}
    host_brightness = {host: mean_luminance(image) for host, image in hosts.items()}

    missing = []
    written = 0
    for name, family, host, band in catalogue():
        colour = COLOURS.get(family)
        if colour is None:
            missing.append(family)
            continue
        source = deep if host == "deepslate" else shallow
        names = deep_names if host == "deepslate" else shallow_names
        specks = source[pick(family, names)]

        image = hosts[host].copy()
        shown = separated(colour, host_brightness[host])
        for x, y, shade in specks:
            if x >= image.width or y >= image.height:
                continue
            image.putpixel((x, y), tint(shown, shade) + (255,))
        image.save(os.path.join(OUT, name + ".png"))
        written += 1

    if missing:
        raise SystemExit("no colour for: " + ", ".join(sorted(set(missing))))
    print(f"ore textures      : {written}")
    print(f"speck shapes      : {len(shallow)} shallow, {len(deep)} deep")


if __name__ == "__main__":
    main()
