#!/usr/bin/env python3
"""
The pack's emblem, generated.

    python3 tools/gen_emblem.py

Writes `elysium-lib/src/main/resources/elysium_logo.png`, the `logoFile` every
module's manifest names, so the mod list shows one mark for the whole pack.
Generated like the rest of the art, from the interface palette
(`elysium-lib/ui/palette.py`), so it is the same violet the screens are; and
generated rather than drawn so a palette change redraws it (POLISH.md P2).

The mark: a hexagonal ring, the pack's initial cut into it as three bars, and
one bright point above — the anomaly the whole setting orbits. 256 square,
pixel-aligned, no anti-aliasing softer than the game's own GUI scale would
resample anyway.
"""
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "elysium-lib", "ui"))
import palette  # noqa: E402

OUT = os.path.join(ROOT, "elysium-lib", "src", "main", "resources", "elysium_logo.png")
SIZE = 256


def rgba(argb):
    return ((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >> 24) & 0xFF)


def hexagon(cx, cy, r):
    # A flat-topped hexagon, its points on integer pixels.
    import math
    return [(round(cx + r * math.cos(math.radians(60 * i))),
             round(cy + r * math.sin(math.radians(60 * i)))) for i in range(6)]


def draw():
    c = {name: rgba(value) for name, value in palette.PALETTE.items()}
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    g = ImageDraw.Draw(image)
    # The panel: the screens' surface with the screens' edge.
    g.rounded_rectangle((8, 8, SIZE - 9, SIZE - 9), radius=28, fill=c["SURFACE"], outline=c["LINE"], width=3)
    # The ring, dim outside, bright inside.
    g.polygon(hexagon(128, 132, 96), outline=c["ACCENT_DIM"], width=10)
    g.polygon(hexagon(128, 132, 78), outline=c["ACCENT"], width=4)
    # The initial: a spine and three bars, the middle one shorter.
    g.rectangle((88, 92, 104, 172), fill=c["TEXT"])
    g.rectangle((88, 92, 168, 106), fill=c["ACCENT_BRIGHT"])
    g.rectangle((88, 125, 150, 139), fill=c["ACCENT_BRIGHT"])
    g.rectangle((88, 158, 168, 172), fill=c["ACCENT_BRIGHT"])
    # The anomaly: one point of void light above the ring's top.
    g.ellipse((120, 18, 136, 34), fill=c["VOID"])
    g.ellipse((124, 22, 132, 30), fill=c["HIGHLIGHT"])
    return image


def main():
    image = draw()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    image.save(OUT, optimize=True)
    print(f"emblem             : {SIZE}x{SIZE}, {len(palette.PALETTE)} palette colours to draw from -> {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
