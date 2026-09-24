#!/usr/bin/env python3
"""
Build the Elysium Tech Reskin from the mods installed in an instance.

    python3 tools/retexture <instance>/mods -o <instance>/resourcepacks/ElysiumTech

    --only create,mekanism     just these namespaces
    --overrides FILE           hand corrections, {"overrides":[{"match":..,"class":..}]}
    --limit N                  stop after N textures, for a look before committing
    --report FILE              write the coverage report as JSON

What it does
------------
Reads every mod jar in the folder, decides what kind of surface each texture
is, and paints it onto the Elysium palette: same size, same alpha, same order
of brightnesses, different colours. Anything that was saturated and bright is
painted on the mod's energy ramp instead, so a machine still tells you it is
running.

What it does not do
-------------------
Copy, store, upload or redistribute anybody's art. It runs where the mods are
already installed and writes a resource pack beside them. Most of these mods'
assets are All Rights Reserved; what this repository ships is the program.
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import build


def main():
    parser = argparse.ArgumentParser(prog="retexture", description=__doc__)
    parser.add_argument("mods", help="the instance's mods folder")
    parser.add_argument("-o", "--out", required=True, help="where to write the pack")
    parser.add_argument("--only", default="",
                        help="comma-separated namespaces, instead of the built-in list")
    parser.add_argument("--overrides", default="", help="hand corrections, as JSON")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--report", default="")
    options = parser.parse_args()

    if not os.path.isdir(options.mods):
        print(f"no such mods folder: {options.mods}")
        return 2
    only = [piece.strip() for piece in options.only.split(",") if piece.strip()]
    report, written = build.build(options.mods, options.out, only or None,
                                  options.overrides or None, options.limit)
    print()
    for mod in sorted(report.mods):
        seen = report.mods[mod]
        classes = ", ".join(f"{kind} {count}" for kind, count
                            in sorted(seen["classes"].items(),
                                      key=lambda pair: -pair[1])[:4])
        print(f"  {mod:28s} {seen['textures']:5d}  {classes}")
    print()
    print(f"{written} texture(s) written to {options.out}")
    named = sum(seen['how'].get('name', 0) + seen['how'].get('override', 0)
                for seen in report.mods.values())
    if written:
        print(f"{named * 100 // written}% classified by name or by hand; the rest by "
              f"their own colours")
    if options.report:
        with open(options.report, "w", encoding="utf-8") as out:
            json.dump(report.as_json(), out, indent=2, sort_keys=True)
            out.write("\n")
        print(f"coverage written to {options.report}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
