#!/usr/bin/env python3
"""Draws the hotbar in Elysium's palette.

Four sprites that replace vanilla's grey ones — the hotbar, the selection
frame, and the two offhand slots — drawn from scratch (not recoloured from
Mojang's art) to vanilla's exact geometry, because the game positions items and
the frame by fixed offsets and a sprite that disagrees with them misaligns
every slot.

Geometry, read from vanilla 1.21.1's own sprites:
  hotbar.png            182 x 22   a slot every 20 px; items drawn 3 px in
  hotbar_selection.png   24 x 23   placed 1 px up-left of a slot; 16x16 window
  hotbar_offhand_*.png   29 x 24   a 22 x 22 box, left-aligned or right-aligned

Colours are read from ElysiumPalette.java, the palette every Elysium screen
uses, so the hotbar cannot drift from the GUI. This is the only source of these
files; regen.sh runs it.
"""
import os
import re

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
LIB = os.path.dirname(HERE)
PALETTE = os.path.join(LIB, "src/main/java/com/elysium/lib/client/ElysiumPalette.java")
OUT = os.path.join(LIB, "src/main/resources/assets/minecraft/textures/gui/sprites/hud")


def palette():
    text = open(PALETTE, encoding="utf-8").read()
    found = dict(re.findall(r"public static final int (\w+) = 0x([0-9A-Fa-f]{8});", text))
    need = ["SURFACE", "SURFACE_RAISED", "LINE", "LINE_SOFT", "SLOT", "SLOT_EDGE",
            "ACCENT", "ACCENT_DIM", "ACCENT_BRIGHT"]
    missing = [n for n in need if n not in found]
    if missing:
        raise SystemExit(f"ElysiumPalette.java no longer defines {missing}")
    def rgba(hexargb, alpha=None):
        v = int(hexargb, 16)
        a = (v >> 24) & 0xFF if alpha is None else alpha
        return ((v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF, a)
    # The hotbar is drawn over the world, so its body is opaque: a
    # translucent bar would show terrain through the gaps between wells.
    return {n: rgba(found[n], 255) for n in need}


def rect(img, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), colour)


def frame(img, x0, y0, x1, y1, colour):
    for x in range(x0, x1 + 1):
        img.putpixel((x, y0), colour)
        img.putpixel((x, y1), colour)
    for y in range(y0, y1 + 1):
        img.putpixel((x0, y), colour)
        img.putpixel((x1, y), colour)


def cut_corners(img, x0, y0, x1, y1):
    for x, y in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        img.putpixel((x, y), (0, 0, 0, 0))


def slot_well(img, x, y, c):
    """An 18x18 well whose 16x16 inside is exactly where the item draws."""
    rect(img, x, y, x + 17, y + 17, c["SLOT"])
    frame(img, x, y, x + 17, y + 17, c["SLOT_EDGE"])


def hotbar(c):
    img = Image.new("RGBA", (182, 22), (0, 0, 0, 0))
    rect(img, 0, 0, 181, 21, c["SURFACE"])
    frame(img, 0, 0, 181, 21, c["LINE"])
    # A raised lip along the top, as on the panels.
    for x in range(1, 181):
        img.putpixel((x, 1), c["SURFACE_RAISED"])
    cut_corners(img, 0, 0, 181, 21)
    for i in range(9):
        slot_well(img, 2 + 20 * i, 2, c)
    return img


def selection(c):
    img = Image.new("RGBA", (24, 23), (0, 0, 0, 0))
    frame(img, 0, 0, 23, 22, c["ACCENT_DIM"])
    frame(img, 1, 1, 22, 21, c["ACCENT_BRIGHT"])
    frame(img, 2, 2, 21, 20, c["ACCENT"])
    cut_corners(img, 0, 0, 23, 22)
    return img


def offhand(c, left):
    img = Image.new("RGBA", (29, 24), (0, 0, 0, 0))
    x0 = 0 if left else 7
    rect(img, x0, 1, x0 + 21, 22, c["SURFACE"])
    frame(img, x0, 1, x0 + 21, 22, c["LINE"])
    for x in range(x0 + 1, x0 + 21):
        img.putpixel((x, 2), c["SURFACE_RAISED"])
    cut_corners(img, x0, 1, x0 + 21, 22)
    slot_well(img, x0 + 2, 3, c)
    return img


def main():
    c = palette()
    os.makedirs(OUT, exist_ok=True)
    sprites = {
        "hotbar.png": hotbar(c),
        "hotbar_selection.png": selection(c),
        "hotbar_offhand_left.png": offhand(c, True),
        "hotbar_offhand_right.png": offhand(c, False),
    }
    for name, img in sprites.items():
        img.save(os.path.join(OUT, name))
    print(f"hud sprites        : {len(sprites)} drawn in the Elysium palette")


if __name__ == "__main__":
    main()
