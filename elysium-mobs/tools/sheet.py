#!/usr/bin/env python3
"""
One contact sheet: every family down the page, four phases of the stride across.

Eight separate GIFs are eight things to remember while looking at the ninth.
The whole question here is whether the families move *differently from each
other*, and that is a question about one image.
"""
import os
import sys

from PIL import Image

FAMILIES = ["Adept", "Choir", "Drone", "Lictor", "Praetor", "Reaver",
            "Scavenger", "Whisper"]
PHASES = [0, 4, 8, 12]
SIZE = 160


def build(out_dir, view):
    sheet = Image.new("RGBA", (SIZE * len(PHASES), SIZE * len(FAMILIES)),
                      (214, 214, 220, 255))
    for row, family in enumerate(FAMILIES):
        path = os.path.join(out_dir, "%sModel_%s_strip.png" % (family, view))
        if not os.path.exists(path):
            continue
        strip = Image.open(path)
        for col, phase in enumerate(PHASES):
            frame = strip.crop((phase * SIZE, 0, phase * SIZE + SIZE, SIZE))
            sheet.alpha_composite(frame, (col * SIZE, row * SIZE))
    target = os.path.join(out_dir, "sheet_%s.png" % view)
    sheet.save(target)
    return target


if __name__ == "__main__":
    directory = sys.argv[1] if len(sys.argv) > 1 else "mobs/tools/preview"
    for name in ("side", "three_quarter"):
        print("  ", build(directory, name))
