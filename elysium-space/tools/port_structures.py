#!/usr/bin/env python3
"""Port Stellaris 1.4.25's structures for our worlds into elysium-space.

Moon structures go to the Pale Moon, Mars to Frostfall, Venus to Cinderveil,
Earth and the meteor to the Overworld. Everything they reference is followed:
template pools, processor lists, structure templates (.nbt) and chest loot
tables. Every stellaris: id is renamed; blocks we have no equal for are mapped
to the nearest one we do (vertical slabs to the full block, globes and flags to
air). Stellaris's mobs, in templates, spawners and spawn overrides, become ours
(mobnames.NAMES). Re-runnable.

Credit: Stellaris, (c) Exodus Studio, CC BY-NC-SA 4.0. See CREDITS.md.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt  # noqa: E402

STELLARIS = os.environ.get("STELLARIS", "/tmp/ref/stellaris")
SRC = os.path.join(STELLARIS, "data/stellaris")
HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(HERE, "src/main/resources/data/elysiumspace")
NS = "elysiumspace"

PREFIX = [("small_moon_", "pale_moon_small_"), ("moon_", "pale_moon_"), ("mars_", "frostfall_"),
          ("venus_", "cinderveil_"), ("earth_", "overworld_")]

BLOCKS = {
    # world stone
    "moon_stone": "pale_stone", "moon_sand": "pale_regolith", "moon_cobblestone": "pale_cobblestone",
    "moon_stone_bricks": "pale_stone_bricks", "polished_moon_stone": "polished_pale_stone",
    "moon_stone_stairs": "pale_stone_stairs", "moon_stone_slab": "pale_stone_slab",
    "moon_polished_stone_brick_stairs": "pale_stone_brick_stairs",
    "moon_polished_stone_brick_slab": "pale_stone_brick_slab", "moon_stone_pillar": "pale_stone_pillar",
    "moon_deepslate": "pale_deepslate", "moon_stone_dust": "pale_regolith", "sky_stone": "polished_pale_stone",
    "mars_stone": "frost_stone", "mars_sand": "frost_regolith", "mars_stone_bricks": "frost_stone_bricks",
    "polished_mars_stone": "polished_frost_stone", "mars_stone_stairs": "frost_stone_stairs",
    "mars_stone_slab": "frost_stone_slab", "mars_polished_stone_stairs": "polished_frost_stone_stairs",
    "mars_polished_stone_slab": "polished_frost_stone_slab", "mars_stone_brick_stairs": "frost_stone_brick_stairs",
    "chiseled_mars_stone_bricks": "chiseled_frost_stone_bricks", "mars_pillar": "frost_stone_pillar",
    "vertical_mars_slab": "frost_stone_bricks",
    "venus_stone": "cinder_stone", "venus_sand": "cinder_sand", "venus_stone_bricks": "cinder_stone_bricks",
    "polished_venus_stone": "polished_cinder_stone", "venus_stone_stairs": "cinder_stone_stairs",
    "venus_stone_slab": "cinder_stone_slab", "venus_polished_stone_stairs": "polished_cinder_stone_stairs",
    "venus_polished_stone_slab": "polished_cinder_stone_slab", "chiseled_venus_stone": "chiseled_cinder_stone_bricks",
    "venus_smooth_sandstone": "smooth_cinder_sandstone", "venus_smooth_sandstone_stairs": "smooth_cinder_sandstone_stairs",
    "venus_smooth_sandstone_slab": "smooth_cinder_sandstone_slab", "vertical_venus_slab": "cinder_stone_bricks",
    # metals
    "iron_plating_block": "iron_plating", "iron_plating_stairs": "iron_plating_stairs",
    "iron_plating_slab": "iron_plating_slab", "iron_pillar": "iron_pillar", "vertical_iron_plating_slab": "iron_plating",
    "steel_block": "steel_block", "steel_stairs": "steel_plating_stairs", "steel_slab": "steel_plating_slab",
    "steel_plating_stairs": "steel_plating_stairs", "steel_plating_slab": "steel_plating_slab",
    "steel_pillar": "steel_pillar", "heavy_metal_casing": "steel_plating", "heavy_metal_stairs": "steel_plating_stairs",
    "heavy_metal_slab": "steel_plating_slab", "vertical_heavy_metal_plate_slab": "steel_plating",
    "desh_plating_block": "lunite_plating", "desh_plating_stairs": "lunite_plating_stairs",
    "venus_smooth_sandstone_brick": "smooth_cinder_sandstone", "desh_plating_slab": "lunite_plating_slab", "desh_pillar": "lunite_pillar",
    "vertical_desh_plating_slab": "lunite_plating", "raw_desh_block": "raw_lunite_block",
    # ores and machines
    "moon_desh_ore": "lunite_ore", "mars_ostrum_ore": "frostite_ore", "venus_gold_ore": "cinder_gold_ore",
    "steel_ore": "steel_ore", "cable": "cable", "coal_generator": "coal_generator", "oil": "oil",
    "rocket_launch_pad": "launch_pad",
}
VANILLA = {"globe": "minecraft:air", "moon_globe": "minecraft:air", "earth_globe": "minecraft:air", "mars_globe": "minecraft:air",
           "venus_globe": "minecraft:air", "flag": "minecraft:air", "rocket_station": "minecraft:crafting_table"}
ITEMS = {
    "space_suit_helmet": f"{NS}:space_suit_helmet", "space_suit_leggings": f"{NS}:space_suit_leggings",
    "space_suit_boots": f"{NS}:space_suit_boots", "jet_suit_chestplate": f"{NS}:jet_suit",
    "jet_suit_helmet": f"{NS}:space_suit_helmet", "jet_suit_leggings": f"{NS}:space_suit_leggings",
    "jet_suit_boots": f"{NS}:space_suit_boots", "steel_ingot": f"{NS}:steel_ingot",
    "heavy_metal_ingot": f"{NS}:steel_ingot", "steel_block": f"{NS}:steel_block",
    "hydrogen_motor": f"{NS}:hydrogen_motor", "big_oxygen_tank": f"{NS}:oxygen_canister",
    "engine_fan": f"{NS}:rocket_fin", "steel_sword": "minecraft:iron_sword", "steel_pickaxe": "minecraft:iron_pickaxe",
    "steel_axe": "minecraft:iron_axe", "steel_shovel": "minecraft:iron_shovel", "steel_hoe": "minecraft:iron_hoe",
    "moon_stone": f"{NS}:pale_stone", "moon_sand": f"{NS}:pale_regolith", "moon_desh_ore": f"{NS}:lunite_ore",
    "moon_iron_ore": f"{NS}:pale_iron_ore", "mars_stone": f"{NS}:frost_stone", "mars_sand": f"{NS}:frost_regolith",
    "venus_coal_ore": f"{NS}:cinder_coal_ore",
}
from mobnames import NAMES as MOBS  # noqa: E402  Stellaris's mob -> ours

KINDS = ["worldgen/structure", "worldgen/structure_set", "worldgen/template_pool", "worldgen/processor_list"]
unmapped = set()


SEGMENTS = {"moon": "pale_moon", "mars": "frostfall", "venus": "cinderveil", "earth": "overworld"}


def rename_path(p):
    """Rename every path segment: moon/ -> pale_moon/, moon_x -> pale_moon_x, has_structure/venus_biomes ->
    has_structure/cinderveil_biomes."""
    out = []
    for seg in p.split("/"):
        if seg in SEGMENTS:
            out.append(SEGMENTS[seg])
            continue
        for a, b in PREFIX:
            if seg.startswith(a):
                seg = b + seg[len(a):]
                break
        out.append(seg)
    return "/".join(out)


def map_id(value, kind):
    """A stellaris: id in context: a block (templates), an item (loot), or a data file."""
    body = value.split(":", 1)[1]
    if kind == "block":
        if body in BLOCKS:
            return f"{NS}:{BLOCKS[body]}"
        if body in VANILLA:
            return VANILLA[body]
        if body in MOBS:
            return f"{NS}:{MOBS[body]}"
        unmapped.add(value)
        return "minecraft:stone"
    if kind == "item":
        if body in ITEMS:
            return ITEMS[body]
        return None
    return f"{NS}:{rename_path(body)}"


def port_json(obj, todo):
    if isinstance(obj, dict):
        if isinstance(obj.get("elements"), list):    # pool entries whose template Stellaris never shipped
            def exists(e):
                loc = e.get("element", {}).get("location", "")
                return not (isinstance(loc, str) and loc.startswith("stellaris:")) or os.path.isfile(
                    os.path.join(SRC, "structure", loc.split(":", 1)[1] + ".nbt"))
            obj = dict(obj, elements=[e for e in obj["elements"] if exists(e)])
        return {k: port_json(v, todo) for k, v in obj.items()}
    if isinstance(obj, list):
        return [port_json(v, todo) for v in obj]
    if isinstance(obj, str):
        tag = obj.startswith("#")
        body = obj[1:] if tag else obj
        if body.startswith("stellaris:"):
            name = body.split(":", 1)[1]
            for kind in KINDS:
                if os.path.isfile(os.path.join(SRC, kind, name + ".json")):
                    todo.append((kind, name))
            if os.path.isfile(os.path.join(SRC, "structure", name + ".nbt")):
                todo.append(("structure", name))
            if name in BLOCKS or name in VANILLA or name in MOBS:
                return map_id(body, "block")
            return ("#" if tag else "") + map_id(body, "data")
    return obj


def port_loot(obj):
    if isinstance(obj, dict):
        if obj.get("type") == "minecraft:item" and str(obj.get("name", "")).startswith("stellaris:"):
            mapped = map_id(obj["name"], "item")
            if mapped is None:
                return None
            obj = dict(obj, name=mapped)
        out = {}
        for k, v in obj.items():
            v2 = port_loot(v)
            if isinstance(v, list):
                v2 = [e for e in v2 if e is not None]
            out[k] = v2
        return out
    if isinstance(obj, list):
        return [port_loot(v) for v in obj]
    return obj


def port_template(name, loots, todo):
    _, root = nbt.load(os.path.join(SRC, "structure", name + ".nbt"))
    comp = root.value
    for s in nbt.strings(root):
        v = s.value
        if not v.startswith("stellaris:"):
            continue
        body = v.split(":", 1)[1]
        if "/" in body:                      # a loot table or a jigsaw pool
            is_pool = os.path.isfile(os.path.join(SRC, "worldgen/template_pool", body + ".json"))
            if not is_pool and not os.path.isfile(os.path.join(SRC, "loot_table", body + ".json")):
                s.value = "minecraft:chests/simple_dungeon"     # Stellaris names a table it never shipped
                continue
            if is_pool:
                todo.append(("worldgen/template_pool", body))
            else:
                loots.add(body)
            s.value = f"{NS}:{rename_path(body)}"
        else:
            s.value = map_id(v, "block")
    out = os.path.join(OUT, "structure", rename_path(name) + ".nbt")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    nbt.save(out, "", root)


def main():
    keep = ("moon", "mars", "venus", "earth", "meteor", "small_moon")
    todo = [(k, f[:-5]) for k in ("worldgen/structure", "worldgen/structure_set")
            for f in os.listdir(os.path.join(SRC, k)) if f.startswith(keep)]
    # Space stations are placed by a blueprint, not by worldgen: their templates are ported on their own.
    todo += [("structure", f"space_station_{size}") for size in ("small", "medium", "big")]
    done, loots = set(), set()
    while todo:
        kind, name = todo.pop()
        if (kind, name) in done:
            continue
        done.add((kind, name))
        if kind == "structure":
            port_template(name, loots, todo)
            continue
        data = port_json(json.load(open(os.path.join(SRC, kind, name + ".json"))), todo)
        out = os.path.join(OUT, kind, rename_path(name) + ".json")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        json.dump(data, open(out, "w"), indent=2)
    for body in sorted(loots):
        src = os.path.join(SRC, "loot_table", body + ".json")
        if not os.path.isfile(src):
            continue
        out = os.path.join(OUT, "loot_table", rename_path(body) + ".json")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        json.dump(port_json(port_loot(json.load(open(src))), []), open(out, "w"), indent=2)
    # the biome tags the structures ask for, in our biomes
    tags = {
        "pale_moon_biomes": [f"{NS}:pale_moon_desert", f"{NS}:pale_moon_caves"],
        "frostfall_biomes": [f"{NS}:frostfall_desert", f"{NS}:frostfall_ice_spikes", f"{NS}:frostfall_rocky_plains"],
        "has_structure/cinderveil_biomes": [f"{NS}:cinderveil_desert"],
        "has_structure/overworld_biomes": json.load(open(os.path.join(SRC, "tags/worldgen/biome/has_structure/earth_biomes.json")))["values"],
        "has_structure/meteor_biomes": json.load(open(os.path.join(SRC, "tags/worldgen/biome/has_structure/earth_biomes.json")))["values"]
        + [f"#{NS}:pale_moon_biomes"],
    }
    for name, values in tags.items():
        out = os.path.join(OUT, "tags/worldgen/biome", name + ".json")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        json.dump({"replace": False, "values": values}, open(out, "w"), indent=2)
    print(f"ported {len(done)} structure files and {len(loots)} loot tables")
    if unmapped:
        print("UNMAPPED (placed as stone):", sorted(unmapped))
        sys.exit(1)


if __name__ == "__main__":
    main()
