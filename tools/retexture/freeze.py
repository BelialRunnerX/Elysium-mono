#!/usr/bin/env python3
"""
Write the palette out so the retexture tool can travel without the art system.

    python3 tools/retexture/freeze.py

Run this after changing art/style.py or art/materials.py. check_retexture.py
fails until you do, which is the point: the frozen copy is allowed to be stale
for as long as it takes to run the validator, and no longer.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import palette


def main():
    built = palette.live()
    if built is None:
        print("art/ is not here, so there is nothing to freeze from")
        return 2
    with open(palette.FROZEN, "w", encoding="utf-8") as out:
        json.dump(built, out, indent=2, sort_keys=True)
        out.write("\n")
    print(f"froze {len(built['surfaces'])} surface ramp(s) and "
          f"{len(built['glows'])} energy ramp(s) into "
          f"{os.path.relpath(palette.FROZEN)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
