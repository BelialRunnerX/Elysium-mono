#!/usr/bin/env python3
"""Port Stellaris 1.4.25's worldgen for four worlds into elysium-space.

Follows every stellaris: reference from the four dimensions, renames each id
into Elysium's names (see RENAME and rename()), and writes the files under
src/main/resources/data/elysiumspace/. Mob spawns name our
mobs (mobnames.NAMES). Re-runnable: the output is a pure function
of the Stellaris jar (unpacked at STELLARIS, default /tmp/ref/stellaris).

Credit: Stellaris, (c) Exodus Studio, CC BY-NC-SA 4.0. See CREDITS.md.
"""
import json
import os
import sys

STELLARIS = os.environ.get("STELLARIS", "/tmp/ref/stellaris")
SRC = os.path.join(STELLARIS, "data/stellaris")
HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(HERE, "src/main/resources/data/elysiumspace")

DIMENSIONS = {"earth_orbit": "high_orbit", "moon": "pale_moon",
              "venus": "cinderveil", "mars": "frostfall"}

# Whole-id renames: blocks, and dimension types whose names differ.
RENAME = {
    "moon_sand": "pale_regolith", "moon_stone": "pale_stone",
    "moon_vines": "pale_vines", "moon_vines_plant": "pale_vines_plant",
    "mars_sand": "frost_regolith", "mars_stone": "frost_stone",
    "venus_sand": "cinder_sand", "venus_stone": "cinder_stone",
    "venus_sandstone": "cinder_sandstone",
    "orbit": "high_orbit", "earth_orbit": "high_orbit",
    "moon": "pale_moon", "venus": "cinderveil", "mars": "frostfall",
    "space": "high_orbit_space",
    "infernal_venus_barrens": "cinderveil_infernal_barrens",
}
PREFIX = [("moon_", "pale_moon_"), ("venus_", "cinderveil_"), ("mars_", "frostfall_")]

KINDS = ["dimension", "dimension_type", "worldgen/noise_settings", "worldgen/biome",
         "worldgen/configured_feature", "worldgen/placed_feature",
         "worldgen/density_function", "worldgen/structure", "worldgen/structure_set",
         "worldgen/template_pool", "worldgen/processor_list"]

from mobnames import mob  # noqa: E402  Stellaris's mobs under our names


def rename(path):
    if path in RENAME:
        return RENAME[path]
    for old, new in PREFIX:
        if path.startswith(old):
            return new + path[len(old):]
    return path


def load(kind, name):
    p = os.path.join(SRC, kind, name + ".json")
    return json.load(open(p, encoding="utf-8")) if os.path.isfile(p) else None


def main():
    if not os.path.isdir(SRC):
        sys.exit(f"no Stellaris data at {SRC}")
    todo = [("dimension", d) for d in DIMENSIONS]
    done = {}
    seen = set()

    def ref(value):
        tag = value.startswith("#")
        body = value[1:] if tag else value
        if not body.startswith("stellaris:"):
            return value
        name = body.split(":", 1)[1]
        if not tag and body not in seen:
            seen.add(body)
            for kind in KINDS:
                if load(kind, name) is not None:
                    todo.append((kind, name))
        return ("#" if tag else "") + "elysiumspace:" + rename(name)

    def walk(o):
        if isinstance(o, dict):
            out = {}
            for k, v in o.items():
                if k == "spawners":
                    v = {cat: [dict(s, type=mob(s["type"]) or s["type"]) for s in lst] for cat, lst in v.items()}
                if k == "spawn_costs":
                    v = {mob(m) or m: c for m, c in v.items()}
                out[k] = walk(v)
            return out
        if isinstance(o, list):
            return [walk(v) for v in o]
        if isinstance(o, str):
            return ref(o)
        return o

    while todo:
        kind, name = todo.pop()
        if (kind, name) in done:
            continue
        data = load(kind, name)
        if kind == "dimension_type":
            # Stellaris drew its skies with Sky Aesthetics; ours are client/sky's effects, one per world.
            data["effects"] = f"elysiumspace:{DIMENSIONS.get(name, rename(name))}"
        done[(kind, name)] = walk(data)

    for (kind, name), data in sorted(done.items()):
        target = DIMENSIONS.get(name, rename(name)) if kind == "dimension" else rename(name)
        path = os.path.join(OUT, kind, target + ".json")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
    print(f"ported {len(done)} files for {len(DIMENSIONS)} worlds")


if __name__ == "__main__":
    main()
