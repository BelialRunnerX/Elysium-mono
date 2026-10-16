#!/usr/bin/env python3
"""
Everything the five village professions need, from one table.

<h2>What a profession costs in files</h2>

Each of the five needs a bench (texture, blockstate, block model, item model,
loot table, recipe, name) and a person (a clothing overlay, a name). That is
nine files apiece and forty-five in total, every one of them derived from three
facts: a colour, an emblem, and what the bench is made of.

<h2>The clothing overlay, and the one thing here that is not verified</h2>

A villager's profession texture is a 64x64 layer drawn over the base villager.
The coordinates below are derived from the villager model's own box layout
rather than guessed — the body box sits at texture offset (16, 20) and is
8 wide, 12 tall and 6 deep, and a box puts its front face at
(x + depth, y + depth), which is (22, 26). That arithmetic is in `apron()` so it
can be checked rather than believed.

It has not been seen in game. Everything else this repository generates can be
verified from the files themselves; where a texture lands on a model cannot, and
claiming otherwise would be worse than saying so. The overlay is deliberately
small for that reason: an apron panel and a badge rather than a full recolour,
so that if the placement is off by a pixel it reads as clothing sitting slightly
wrong rather than as a villager painted the wrong colour.
"""
import json
import pathlib
import re

from PIL import Image

MODULE = pathlib.Path(__file__).resolve().parent.parent
RES = MODULE / "src/main/resources"
NS = "elysiumnpcs"

#: What mines a bench: rules/HarvestRules.java, read rather than retyped. The
#: pickaxe tag was a file typed by hand until 29 September 2026, and the sixth
#: bench was not in it; it is written from the rules for every bench now.
HARVEST = MODULE / "src/main/java/com/elysium/npcs/rules/HarvestRules.java"
TOOLS = ("pickaxe", "axe", "shovel", "hoe")

# ---------------------------------------------------------------------------
# The table
# ---------------------------------------------------------------------------
#
# "role" is the person and "bench" is the block, and they are different words on
# purpose — see the note in ElysiumStations. The Java is the source of truth for
# both; validate.py reads it back and fails if this table has drifted.

PROFESSIONS = [
    {
        "role": "signaller", "name": "Signaller",
        "bench": "signal_mast", "bench_name": "Signal Mast",
        "cloth": ["#1b3a52", "#27536f", "#35708f", "#4a90b0"],
        "accent": "#7fd4f0",
        "core": "minecraft:amethyst_shard",
        "blurb": "Reads the sky and sells what it hears.",
    },
    {
        "role": "splicer", "name": "Splicer",
        "bench": "splicing_vat", "bench_name": "Splicing Vat",
        "cloth": ["#1c3a1e", "#27512a", "#356d39", "#4a8f4e"],
        "accent": "#8ce88f",
        "core": "minecraft:glow_berries",
        "blurb": "Grows things that did not use to grow.",
    },
    {
        "role": "voidwright", "name": "Voidwright",
        "bench": "void_crucible", "bench_name": "Void Crucible",
        "cloth": ["#2a1c3e", "#3b2857", "#4f3676", "#67489b"],
        "accent": "#b98ce8",
        "core": "minecraft:obsidian",
        "blurb": "Melts what you bring and gives it back better.",
    },
    {
        "role": "hydroponicist", "name": "Hydroponicist",
        "bench": "hydroponics_rack", "bench_name": "Hydroponics Rack",
        "cloth": ["#274a2c", "#36683c", "#478a4f", "#5cae66"],
        "accent": "#c6f08a",
        "core": "minecraft:kelp",
        "blurb": "Food without soil, which is what a station eats.",
    },
    {
        # SPEC_CLONE P9.14. The one villager whose trade is the mod's own gear:
        # a bench where parts and blueprints change hands, so a player who has
        # not found a material yet can buy their way to the first of it.
        "role": "gear_smith", "name": "Gear Smith",
        "bench": "gear_bench", "bench_name": "Gear Bench",
        "cloth": ["#2c2c33", "#3f3f49", "#565663", "#70707f"],
        "accent": "#c8d2e0",
        "core": "minecraft:iron_ingot",
        "blurb": "Deals in parts, and in the plans for them.",
    },
    {
        "role": "salvager", "name": "Salvager",
        "bench": "salvage_rig", "bench_name": "Salvage Rig",
        "cloth": ["#4a2d16", "#663f1f", "#8a552a", "#ae6f39"],
        "accent": "#f0b87f",
        "core": "minecraft:iron_nugget",
        "blurb": "Takes the broken things nobody else wants.",
    },
]

# The bench is built of copper and one thing that says what it is for. Copper
# because it is the one metal a village-era player has in quantity, and because
# every one of these is a piece of equipment rather than a piece of furniture.
BENCH_BASE = "minecraft:copper_ingot"


def write_json(rel, obj):
    path = RES / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(obj, handle, indent=2)
        handle.write("\n")


def rgba(value, alpha=255):
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


def noise(seed, x, y, salt=0):
    v = (x * 374761393 + y * 668265263 + seed * 2246822519 + salt * 2654435761)
    v &= 0xFFFFFFFF
    v ^= v >> 13
    v = (v * 1274126177) & 0xFFFFFFFF
    return v ^ (v >> 16)


def seed_of(name):
    value = 11
    for character in name:
        value = (value * 131 + ord(character)) & 0xFFFFFFFF
    return value


# ---------------------------------------------------------------------------
# The bench
# ---------------------------------------------------------------------------

def bench_tile(entry, face):
    """
    A panel of equipment: a copper frame with a working face.

    Three faces per bench — a top, a side and a front — rather than one texture
    on all six, because a bench that looks the same from above as from the front
    reads as a decorative block and a player will not think to stand at it.
    """
    seed = seed_of(entry["bench"] + face)
    cloth = entry["cloth"]
    accent = entry["accent"]
    image = Image.new("RGBA", (16, 16), rgba("#5c3a22"))
    pixels = image.load()

    for y in range(16):
        for x in range(16):
            step = 1 + (noise(seed, x, y) % 3)
            pixels[x, y] = rgba(["#6b4527", "#7d5230", "#8f6039"][step - 1])

    # A frame, so every face of the block is bounded and the block reads as
    # built rather than cast.
    for i in range(16):
        for edge in (0, 15):
            pixels[i, edge] = rgba("#4a2d1a")
            pixels[edge, i] = rgba("#4a2d1a")

    if face == "top":
        for y in range(3, 13):
            for x in range(3, 13):
                pixels[x, y] = rgba(cloth[1 + (noise(seed, x // 3, y // 3) % 2)])
        for x in range(5, 11):
            pixels[x, 5] = rgba(accent)
    elif face == "front":
        for y in range(4, 11):
            for x in range(3, 13):
                pixels[x, y] = rgba(cloth[1 + (noise(seed, x, y, 3) % 3)])
        for y in range(5, 10):
            for x in range(4, 8):
                pixels[x, y] = rgba(accent if (x + y) % 3 else cloth[3])
        for x in range(9, 12):
            pixels[x, 6] = rgba(accent)
            pixels[x, 8] = rgba(cloth[0])
    else:
        for y in range(5, 11):
            for x in range(2, 14):
                pixels[x, y] = rgba(cloth[1 + (noise(seed, x, y, 7) % 2)])
        for x in range(3, 13, 3):
            pixels[x, 7] = rgba(accent)
    return image


def write_bench(entry):
    bench = entry["bench"]
    textures = RES / f"assets/{NS}/textures/block"
    textures.mkdir(parents=True, exist_ok=True)
    for face in ("top", "side", "front"):
        bench_tile(entry, face).save(textures / f"{bench}_{face}.png")

    write_json(f"assets/{NS}/models/block/{bench}.json", {
        "parent": "minecraft:block/orientable",
        "textures": {
            "front": f"{NS}:block/{bench}_front",
            "side": f"{NS}:block/{bench}_side",
            "top": f"{NS}:block/{bench}_top",
        },
    })
    # No facing property on the block yet, so one variant. The model is
    # orientable anyway, because the day it gains one this is already right and
    # the alternative is a bench whose working face points at a wall.
    write_json(f"assets/{NS}/blockstates/{bench}.json",
               {"variants": {"": {"model": f"{NS}:block/{bench}"}}})
    write_json(f"assets/{NS}/models/item/{bench}.json",
               {"parent": f"{NS}:block/{bench}"})
    write_json(f"data/{NS}/loot_table/blocks/{bench}.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{bench}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
    write_json(f"data/{NS}/recipe/{bench}.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["ccc", "cxc", "ccc"],
        "key": {"c": {"item": BENCH_BASE}, "x": {"item": entry["core"]}},
        "result": {"id": f"{NS}:{bench}", "count": 1},
    })


# ---------------------------------------------------------------------------
# The person
# ---------------------------------------------------------------------------

# The villager body box: texture offset, size, depth. Everything in apron() is
# derived from these rather than typed as coordinates, so the arithmetic can be
# read and argued with.
BODY_OFFSET = (16, 20)
BODY_SIZE = (8, 12)
BODY_DEPTH = 6


def apron(entry):
    """
    An apron panel and a badge, on an otherwise empty 64x64 layer.

    A box lays its faces out as right, front, left, back along one row, each
    inset by the box's depth — so the front of the body is at
    (offset.x + depth, offset.y + depth). That is the only face a badge should
    be on: putting it at the offset itself would land it on the right shoulder.
    """
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    pixels = image.load()
    cloth = entry["cloth"]
    accent = entry["accent"]
    seed = seed_of(entry["role"])

    x0 = BODY_OFFSET[0] + BODY_DEPTH
    y0 = BODY_OFFSET[1] + BODY_DEPTH
    width, height = BODY_SIZE

    # The apron: the lower two thirds of the chest, so the villager's own collar
    # still shows above it.
    top = y0 + height // 3
    for y in range(top, y0 + height):
        for x in range(x0, x0 + width):
            pixels[x, y] = rgba(cloth[1 + (noise(seed, x, y) % 3)])
    for x in range(x0, x0 + width):
        pixels[x, top] = rgba(cloth[0])
        pixels[x, y0 + height - 1] = rgba(cloth[0])

    # The badge, centred on the apron.
    bx = x0 + width // 2 - 1
    by = top + 2
    for dx in range(2):
        for dy in range(2):
            pixels[bx + dx, by + dy] = rgba(accent)
    pixels[bx, by] = rgba("#ffffff")
    return image


def write_person(entry):
    directory = RES / f"assets/{NS}/textures/entity/villager/profession"
    directory.mkdir(parents=True, exist_ok=True)
    apron(entry).save(directory / f"{entry['role']}.png")


# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

def harvest_rules():
    """HarvestRules' two answers: the tool every bench is for, and the benches broken by hand."""
    text = HARVEST.read_text(encoding="utf-8")
    text = re.sub(r"//[^\n]*", " ", re.sub(r"/\*.*?\*/", " ", text, flags=re.S))
    tool = re.search(r'String BENCH_TOOL = "([a-z]+)";', text)
    by_hand = re.search(r"List<String> BY_HAND = List\.of\(([^;]*?)\);", text, re.S)
    if not tool or not by_hand:
        raise SystemExit("HarvestRules does not say what mines a bench")
    return tool.group(1), re.findall(r'"([a-z_]+)"', by_hand.group(1))


def mineable_tags():
    """{tool: [block ids]} for every bench, from HarvestRules and the table."""
    tool, by_hand = harvest_rules()
    tags = {}
    for one in PROFESSIONS:
        if one["bench"] not in by_hand:
            tags.setdefault(tool, []).append(f"{NS}:{one['bench']}")
    return tags


def write_tags():
    """The game's mineable tags, one write a file, from the whole list at once."""
    tags = mineable_tags()
    for tool, values in tags.items():
        write_json(f"data/minecraft/tags/block/mineable/{tool}.json",
                   {"replace": False, "values": values})
    return len(tags)


def lang_entries():
    entries = {}
    for one in PROFESSIONS:
        entries[f"block.{NS}.{one['bench']}"] = one["bench_name"]
        entries[f"{NS}.tooltip.{one['bench']}"] = one["blurb"]
        # Vanilla builds a villager's name as
        # "entity.minecraft.villager.<profession path>" and drops the namespace,
        # so a modded profession's name has to be declared under minecraft's
        # prefix. That looks wrong and is correct; see ElysiumStations.
        entries[f"entity.minecraft.villager.{one['role']}"] = one["name"]
    return entries


def main():
    for entry in PROFESSIONS:
        write_bench(entry)
        write_person(entry)
    tagged = write_tags()

    path = RES / f"assets/{NS}/lang/en_us.json"
    existing = json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {}
    existing.update(lang_entries())
    path.write_text(json.dumps(existing, indent=2, ensure_ascii=False,
                               sort_keys=True) + "\n", encoding="utf-8", newline=chr(10))

    print(f"village            : {len(PROFESSIONS)} professions, "
          f"{len(PROFESSIONS) * 3} bench textures, "
          f"{len(PROFESSIONS)} clothing overlays")
    print(f"                     {len(lang_entries())} language entries, "
          f"{tagged} mineable tag(s) from HarvestRules")


if __name__ == "__main__":
    main()
