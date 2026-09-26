#!/usr/bin/env python3
"""
Where towns found themselves: the structure that stakes a site, and the stake.

Writes, under src/main/resources:
  data/elysiumcolonies/worldgen/structure/settlement.json        a jigsaw of one piece
  data/elysiumcolonies/worldgen/structure_set/settlements.json   how far apart, from SettleRules
  data/elysiumcolonies/worldgen/template_pool/settlement/seed.json
  data/elysiumcolonies/structure/settlement_seed.nbt             one block: the survey stake
  data/elysiumcolonies/tags/worldgen/biome/settles_in.json       every biome of every world that settles
  assets: the stake's blockstate, model and texture, and its language keys

Spacing, separation and salt are read out of rules/SettleRules.java rather than
typed here, so the structure set and the rule the harness proves are one number.
The biome tag is read out of the charters (which worlds settle) and out of the
biome files those worlds ship, so a biome added to a world is a biome towns can
stand in without anybody remembering this file.

ELYSIUM_SETTLE_OUT points what is written at another tree; validate.py uses it
to rebuild into a scratch folder and compare with what is shipped.
"""
import json
import os
import re
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
ROOT = os.path.dirname(MOD)
NS = "elysiumcolonies"
OUT = os.environ.get("ELYSIUM_SETTLE_OUT", MOD)
RES = os.path.join(OUT, "src/main/resources")
DATA = os.path.join(RES, "data", NS)
ASSETS = os.path.join(RES, "assets", NS)
SHIPPED = os.path.join(MOD, "src/main/resources")

sys.path.insert(0, HERE)
import nbt  # noqa: E402

DATA_VERSION = 3955  # 1.21.1


def write(path, body):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w", encoding="utf-8", newline="\n").write(json.dumps(body, indent=2) + "\n")


def rule(name):
    source = open(os.path.join(MOD, "src/main/java/com/elysium/colonies/rules/SettleRules.java"),
                  encoding="utf-8").read()
    match = re.search(r"public static final int " + name + r" = ([0-9_]+);", source)
    if not match:
        sys.exit(f"SettleRules has no {name}")
    return int(match.group(1).replace("_", ""))


# --- which worlds settle, and what grows in them ------------------------------
def settling_worlds():
    """The dimension ids whose charter says towns found themselves there."""
    worlds = []
    folder = os.path.join(SHIPPED, "data", NS, "charter")
    for name in sorted(os.listdir(folder)):
        charter = json.load(open(os.path.join(folder, name), encoding="utf-8"))
        if charter.get("settles"):
            worlds.append(charter["dimension"])
    return worlds


# Vanilla's surface biomes with ground to build on: its own grouping tags, and the
# plains-like ones that have no tag of their own. No oceans, rivers or caves.
VANILLA_LAND = [
    "#minecraft:is_forest", "#minecraft:is_taiga", "#minecraft:is_savanna",
    "#minecraft:is_jungle", "#minecraft:is_badlands", "#minecraft:is_hill",
    "#minecraft:is_mountain", "minecraft:plains", "minecraft:sunflower_plains",
    "minecraft:snowy_plains", "minecraft:meadow", "minecraft:desert", "minecraft:swamp",
    "minecraft:mangrove_swamp", "minecraft:cherry_grove", "minecraft:stony_shore",
    "minecraft:beach", "minecraft:snowy_beach",
]

# Biomes of ours that are underground or open sky: no ground to stand a town on.
NOT_GROUND = re.compile(r"cavern|grotto|well$|vault$|hollow$|_caves$|space$|orbit")


def biomes_of(world):
    """Every surface biome a world ships, read from the module that owns the world."""
    namespace, path = world.split(":", 1)
    if namespace == "minecraft":
        if path != "overworld":
            return []
        wilds = os.path.join(ROOT, "elysium-wilds/src/main/resources/data/elysiumwilds/worldgen/biome")
        ours = []
        if os.path.isdir(wilds):
            for name in sorted(os.listdir(wilds)):
                if name.endswith(".json") and not name.startswith("vale_") \
                        and not NOT_GROUND.search(name[:-5]):
                    ours.append("elysiumwilds:" + name[:-5])
        return VANILLA_LAND + ours
    if namespace == "elysiumspace":
        space = os.path.join(ROOT, "elysium-space/src/main/resources/data/elysiumspace/worldgen/biome")
        found = []
        if os.path.isdir(space):
            for name in sorted(os.listdir(space)):
                if name.endswith(".json") and name.startswith(path + "_") \
                        and not NOT_GROUND.search(name[:-5]):
                    found.append("elysiumspace:" + name[:-5])
        return found
    return []


def worldgen():
    spacing, separation, salt = rule("SPACING"), rule("SEPARATION"), rule("SALT")
    worlds = settling_worlds()
    biomes = []
    for world in worlds:
        for biome in biomes_of(world):
            if biome not in biomes:
                biomes.append(biome)
    if not biomes:
        sys.exit("no world settles, so there is nowhere for a town to found itself")
    write(os.path.join(DATA, "tags/worldgen/biome/settles_in.json"),
          {"replace": False, "values": biomes})
    write(os.path.join(DATA, "worldgen/structure/settlement.json"), {
        "type": "minecraft:jigsaw",
        "biomes": f"#{NS}:settles_in",
        "step": "surface_structures",
        "spawn_overrides": {},
        "terrain_adaptation": "none",
        "start_pool": f"{NS}:settlement/seed",
        "size": 1,
        "start_height": {"absolute": 0},
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "max_distance_from_center": 16,
        "use_expansion_hack": False,
    })
    write(os.path.join(DATA, "worldgen/structure_set/settlements.json"), {
        "structures": [{"structure": f"{NS}:settlement", "weight": 1}],
        "placement": {
            "type": "minecraft:random_spread",
            "spacing": spacing,
            "separation": separation,
            "salt": salt,
            # Never on top of a village: the two would fight over the same
            # ground, and the village was there first.
            "exclusion_zone": {"other_set": "minecraft:villages", "chunk_count": 6},
        },
    })
    write(os.path.join(DATA, "worldgen/template_pool/settlement/seed.json"), {
        "name": f"{NS}:settlement/seed",
        "fallback": "minecraft:empty",
        "elements": [{"weight": 1, "element": {
            "element_type": "minecraft:single_pool_element",
            "location": f"{NS}:settlement_seed",
            "processors": "minecraft:empty",
            "projection": "rigid",
        }}],
    })
    return worlds, biomes, spacing, separation


def compound(pairs):
    return nbt.Tag(nbt.COMPOUND, dict(pairs))


def stake_template():
    """A one-block structure: the survey stake, on the surface."""
    palette = [compound([("Name", nbt.Tag(nbt.STRING, f"{NS}:settlement_seed"))])]
    blocks = [compound([("pos", nbt.Tag(nbt.LIST, [nbt.Tag(nbt.INT, 0)] * 3, nbt.INT)),
                        ("state", nbt.Tag(nbt.INT, 0))])]
    root = compound([
        ("size", nbt.Tag(nbt.LIST, [nbt.Tag(nbt.INT, 1)] * 3, nbt.INT)),
        ("palette", nbt.Tag(nbt.LIST, palette, nbt.COMPOUND)),
        ("blocks", nbt.Tag(nbt.LIST, blocks, nbt.COMPOUND)),
        ("entities", nbt.Tag(nbt.LIST, [], nbt.END)),
        ("DataVersion", nbt.Tag(nbt.INT, DATA_VERSION)),
    ])
    path = os.path.join(DATA, "structure/settlement_seed.nbt")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    nbt.save(path, "", root)


# --- the stake itself ------------------------------------------------------------
def stake_sprite():
    """A wooden post with a green pennant: a surveyor's mark, not a fence post."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    wood, dark, light = (121, 88, 50, 255), (84, 58, 30, 255), (150, 112, 68, 255)
    for y in range(2, 16):
        draw.point((7, y), fill=wood)
        draw.point((8, y), fill=light if y % 3 else wood)
    for y in range(13, 16):
        draw.point((7, y), fill=dark)
    draw.point((7, 2), fill=dark)
    draw.point((8, 2), fill=dark)
    green, deep = (58, 156, 84, 255), (36, 110, 58, 255)
    for dx in range(1, 6):
        for dy in range(0, 4 - (dx - 1) // 2):
            draw.point((8 + dx, 3 + dy), fill=green if (dx + dy) % 4 else deep)
    return image


def assets():
    os.makedirs(os.path.join(ASSETS, "textures/block"), exist_ok=True)
    stake_sprite().save(os.path.join(ASSETS, "textures/block/settlement_seed.png"))
    write(os.path.join(ASSETS, "blockstates/settlement_seed.json"),
          {"variants": {"": {"model": f"{NS}:block/settlement_seed"}}})
    # Two crossed planes, like a sapling: the model reads transparent pixels,
    # so it names its render layer, or the solid layer paints them black.
    write(os.path.join(ASSETS, "models/block/settlement_seed.json"), {
        "parent": "minecraft:block/cross",
        "render_type": "minecraft:cutout",
        "textures": {"cross": f"{NS}:block/settlement_seed"},
    })


WORDS = {
    f"block.{NS}.settlement_seed": "Survey Stake",
    "elysium.colony.claimed": "%s is yours now; its steward steps aside.",
    "elysium.colony.claim.none": "You are not standing in a settlement that founded itself.",
    "elysium.colony.natural.header": "%s settlement(s) founded themselves in this world:",
    "elysium.colony.natural.entry": "  %s at %s, %s (%s blocks away, %s citizens, %s order(s) in hand, day %s)",
    "elysium.colony.natural.none": "No settlement has founded itself in this world yet.",
}


def lang():
    """The settlements' half of the language file, merged into what is already there."""
    path = os.path.join(ASSETS, "lang/en_us.json")
    base = os.path.join(SHIPPED, "assets", NS, "lang/en_us.json")
    words = json.load(open(base, encoding="utf-8")) if os.path.isfile(base) else {}
    words.update(WORDS)
    write(path, dict(sorted(words.items())))


def main():
    worlds, biomes, spacing, separation = worldgen()
    stake_template()
    assets()
    lang()
    print(f"settlements: {len(worlds)} world(s) settle, {len(biomes)} biome(s) or biome tag(s), "
          f"one site per {spacing} chunks, never within {separation}; the stake and "
          f"{len(WORDS)} language key(s)")


if __name__ == "__main__":
    main()
