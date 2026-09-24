#!/usr/bin/env python3
"""
An Elysium style per world, built by re-theming a MineColonies one.

<h2>What a style is</h2>

MineColonies calls it a style; Structurize calls it a structure pack. It is a
directory of blueprints — gzipped NBT — and a pack.json, and every loaded mod
is scanned for one at blueprints/<modid>/<packname>/. A colony's style decides
what its buildings look like and, because a blueprint names the blocks it is
made of, what materials they cost.

<h2>Why re-theme rather than draw</h2>

Because a style is four hundred buildings and nobody is drawing four hundred
buildings. What makes a style Cinderveil's is not its floor plans — a bakery is
a bakery — it is that it is built out of cinder stone and emberwood instead of
cobblestone and oak. That is a palette substitution, and a blueprint's palette
is a list of block states indexed by a separate array of geometry. Rewriting
the palette changes every block in the building and moves nothing.

<p>It also means the anchor data MineColonies needs — the tile entities that
make a hut a hut, the primary offset, the tags — survives untouched, because
none of it is in the palette.

<h2>Which source for which world</h2>

Chosen so the substitution has the least work to do: a nordic style is already
made of stone and spruce, so Frostfall's version of it reads as the same
architecture in local rock. A style whose source was all sandstone would fight
the substitution and come out patchy.
"""
import gzip
import io
import json
import os
import re
import shutil
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumcolonies"
OUT = os.path.join(MOD, "src/main/resources/blueprints", NS)

SOURCE_JAR = "/tmp/mods/minecolonies-1.1.1368-1.21.1-snapshot.jar"

# ---------------------------------------------------------------------------
# The stone families. Each Elysium world has the same five shapes, so one
# table covers all of them.
# ---------------------------------------------------------------------------
def stone_of(prefix):
    """Every vanilla stone block that becomes this world's rock, by shape."""
    return {
        # full blocks
        "minecraft:stone": f"elysiumspace:{prefix}_stone",
        "minecraft:granite": f"elysiumspace:{prefix}_stone",
        "minecraft:diorite": f"elysiumspace:{prefix}_stone",
        "minecraft:andesite": f"elysiumspace:{prefix}_stone",
        "minecraft:polished_andesite": f"elysiumspace:{prefix}_stone",
        "minecraft:polished_granite": f"elysiumspace:{prefix}_stone",
        "minecraft:polished_diorite": f"elysiumspace:{prefix}_stone",
        "minecraft:deepslate": f"elysiumspace:{prefix}_stone",
        "minecraft:cobblestone": f"elysiumspace:{prefix}_cobblestone",
        "minecraft:mossy_cobblestone": f"elysiumspace:{prefix}_cobblestone",
        "minecraft:cobbled_deepslate": f"elysiumspace:{prefix}_cobblestone",
        "minecraft:stone_bricks": f"elysiumspace:{prefix}_stone_bricks",
        "minecraft:mossy_stone_bricks": f"elysiumspace:{prefix}_stone_bricks",
        "minecraft:cracked_stone_bricks": f"elysiumspace:{prefix}_stone_bricks",
        "minecraft:deepslate_bricks": f"elysiumspace:{prefix}_stone_bricks",
        "minecraft:deepslate_tiles": f"elysiumspace:{prefix}_stone_bricks",
        "minecraft:polished_deepslate": f"elysiumspace:{prefix}_stone_bricks",
        "minecraft:chiseled_stone_bricks": f"elysiumspace:{prefix}_stone_bricks",
        "minecraft:smooth_stone": f"elysiumspace:{prefix}_stone",
        # slabs
        "minecraft:stone_slab": f"elysiumspace:{prefix}_stone_slab",
        "minecraft:smooth_stone_slab": f"elysiumspace:{prefix}_stone_slab",
        "minecraft:cobblestone_slab": f"elysiumspace:{prefix}_stone_slab",
        "minecraft:mossy_cobblestone_slab": f"elysiumspace:{prefix}_stone_slab",
        "minecraft:andesite_slab": f"elysiumspace:{prefix}_stone_slab",
        "minecraft:granite_slab": f"elysiumspace:{prefix}_stone_slab",
        "minecraft:diorite_slab": f"elysiumspace:{prefix}_stone_slab",
        "minecraft:polished_andesite_slab": f"elysiumspace:{prefix}_stone_slab",
        "minecraft:cobbled_deepslate_slab": f"elysiumspace:{prefix}_stone_slab",
        "minecraft:stone_brick_slab": f"elysiumspace:{prefix}_stone_brick_slab",
        "minecraft:mossy_stone_brick_slab": f"elysiumspace:{prefix}_stone_brick_slab",
        "minecraft:deepslate_brick_slab": f"elysiumspace:{prefix}_stone_brick_slab",
        "minecraft:deepslate_tile_slab": f"elysiumspace:{prefix}_stone_brick_slab",
        "minecraft:polished_deepslate_slab": f"elysiumspace:{prefix}_stone_brick_slab",
        # stairs
        "minecraft:stone_stairs": f"elysiumspace:{prefix}_stone_stairs",
        "minecraft:cobblestone_stairs": f"elysiumspace:{prefix}_stone_stairs",
        "minecraft:mossy_cobblestone_stairs": f"elysiumspace:{prefix}_stone_stairs",
        "minecraft:andesite_stairs": f"elysiumspace:{prefix}_stone_stairs",
        "minecraft:granite_stairs": f"elysiumspace:{prefix}_stone_stairs",
        "minecraft:diorite_stairs": f"elysiumspace:{prefix}_stone_stairs",
        "minecraft:polished_andesite_stairs": f"elysiumspace:{prefix}_stone_stairs",
        "minecraft:cobbled_deepslate_stairs": f"elysiumspace:{prefix}_stone_stairs",
        "minecraft:stone_brick_stairs": f"elysiumspace:{prefix}_stone_brick_stairs",
        "minecraft:mossy_stone_brick_stairs": f"elysiumspace:{prefix}_stone_brick_stairs",
        "minecraft:deepslate_brick_stairs": f"elysiumspace:{prefix}_stone_brick_stairs",
        "minecraft:deepslate_tile_stairs": f"elysiumspace:{prefix}_stone_brick_stairs",
        "minecraft:polished_deepslate_stairs": f"elysiumspace:{prefix}_stone_brick_stairs",
        # pillars
        "minecraft:basalt": f"elysiumspace:{prefix}_stone_pillar",
        "minecraft:polished_basalt": f"elysiumspace:{prefix}_stone_pillar",
        "minecraft:quartz_pillar": f"elysiumspace:{prefix}_stone_pillar",
    }


#: Every wooden shape emberwood has an answer for. Cinderveil only: the other
#: worlds keep the source style's timber, because a moon base made of charred
#: wood is a stranger sight than one made of spruce.
EMBERWOOD = {}
for _wood in ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove",
              "cherry", "crimson", "warped"):
    EMBERWOOD.update({
        f"minecraft:{_wood}_planks": "elysiumflora:emberwood_planks",
        f"minecraft:{_wood}_slab": "elysiumflora:emberwood_slab",
        f"minecraft:{_wood}_stairs": "elysiumflora:emberwood_stairs",
        f"minecraft:{_wood}_fence": "elysiumflora:emberwood_fence",
        f"minecraft:{_wood}_fence_gate": "elysiumflora:emberwood_fence_gate",
        f"minecraft:{_wood}_door": "elysiumflora:emberwood_door",
        f"minecraft:{_wood}_trapdoor": "elysiumflora:emberwood_trapdoor",
        f"minecraft:{_wood}_button": "elysiumflora:emberwood_button",
        f"minecraft:{_wood}_pressure_plate": "elysiumflora:emberwood_pressure_plate",
        f"minecraft:{_wood}_leaves": "elysiumflora:emberwood_leaves",
        f"minecraft:{_wood}_log": "elysiumflora:emberwood_log",
        f"minecraft:{_wood}_wood": "elysiumflora:emberwood_wood",
        f"minecraft:stripped_{_wood}_log": "elysiumflora:stripped_emberwood_log",
        f"minecraft:stripped_{_wood}_wood": "elysiumflora:stripped_emberwood_wood",
        f"minecraft:{_wood}_stem": "elysiumflora:emberwood_log",
        f"minecraft:{_wood}_hyphae": "elysiumflora:emberwood_wood",
        f"minecraft:stripped_{_wood}_stem": "elysiumflora:stripped_emberwood_log",
        f"minecraft:stripped_{_wood}_hyphae": "elysiumflora:stripped_emberwood_wood",
    })

#: Ground cover, per world. Grass on an airless moon is the single most
#: obviously wrong block a ported style can contain.
def ground_of(prefix, regolith):
    cover = f"elysiumspace:{prefix}_{regolith}" if regolith else None
    if cover is None:
        return {}
    return {
        "minecraft:grass_block": cover,
        "minecraft:dirt": cover,
        "minecraft:coarse_dirt": cover,
        "minecraft:rooted_dirt": cover,
        "minecraft:podzol": cover,
        "minecraft:mycelium": cover,
        "minecraft:sand": f"elysiumspace:{prefix}_sand"
        if prefix == "cinder" else cover,
        "minecraft:red_sand": f"elysiumspace:{prefix}_sand"
        if prefix == "cinder" else cover,
        "minecraft:gravel": cover,
        "minecraft:snow_block": cover if prefix != "frost" else "minecraft:snow_block",
    }


# ---------------------------------------------------------------------------
# The styles.
# ---------------------------------------------------------------------------
STYLES = [
    {
        "name": "Cinderhold",
        "source": "truedwarven",
        "desc": "Cinderveil's own: cinder stone cut deep, emberwood roofed, "
                "built by people who do not go outside more than they must.",
        "mods": ["structurize", "minecolonies", "elysiumspace", "elysiumflora"],
        "swap": {**stone_of("cinder"), **EMBERWOOD,
                 **ground_of("cinder", "sand")},
    },
    {
        "name": "Frostfall",
        "source": "nordic",
        "desc": "Frostfall's own: frost stone and spruce, low roofs, and every "
                "door facing away from the wind.",
        "mods": ["structurize", "minecolonies", "elysiumspace"],
        "swap": {**stone_of("frost"), **ground_of("frost", "regolith")},
    },
    {
        "name": "Pale Reach",
        "source": "spacewars",
        "desc": "The Pale Moon's own: pale stone and sealed frames, because "
                "there is nothing outside to let in.",
        "mods": ["structurize", "minecolonies", "elysiumspace"],
        "swap": {**stone_of("pale"), **ground_of("pale", "regolith")},
    },
    {
        "name": "High Orbit",
        "source": "lostcity",
        "desc": "For the station: nothing grows, nothing weathers, and every "
                "wall is the hull.",
        "mods": ["structurize", "minecolonies", "elysiumspace"],
        "swap": {**stone_of("pale"), **ground_of("pale", "regolith")},
    },
    {
        "name": "Bastion",
        "source": "shire",
        "desc": "Emerald Bastion's own: the one world that gives more than it "
                "takes, built like somebody meant to stay.",
        "mods": ["structurize", "minecolonies", "elysiumspace"],
        "swap": {**stone_of("pale")},
    },
]


def read_bytes(raw):
    """nbt.py reads from a path; a blueprint inside a jar is bytes."""
    data = gzip.decompress(raw) if raw[:2] == b"\x1f\x8b" else raw
    stream = io.BytesIO(data)
    kind = stream.read(1)[0]
    name = nbt._rs(stream)
    return name, nbt._read(stream, kind)


def remap(raw, swap):
    """
    One blueprint, re-themed.

    Only the palette's Name strings are touched. Geometry, tile entities,
    entities and the anchor data are the file's own and are written back
    byte-for-byte as they were read.
    """
    name, root = read_bytes(raw)
    body = root.value
    palette = body.get("palette")
    if palette is None:
        return None, 0
    changed = 0
    introduced = set()
    for entry in palette.value:
        tag = entry.value.get("Name")
        if tag is None or not isinstance(tag.value, str):
            continue
        wanted = swap.get(tag.value)
        if wanted and wanted != tag.value:
            tag.value = wanted
            changed += 1
            introduced.add(wanted.split(":", 1)[0])

    # Whatever the blueprint now needs, it has to say it needs.
    needed = body.get("required_mods")
    if needed is not None and introduced:
        have = {t.value for t in needed.value if isinstance(t.value, str)}
        for mod in sorted(introduced):
            if mod not in have:
                needed.value.append(nbt.Tag(nbt.STRING, mod))
        # An empty NBT list carries an element type of END, and appending to
        # one without correcting that writes a list header saying "N elements
        # of type END" — which the reader then consumes as nothing at all and
        # misparses everything after it. A blueprint of plain stone requires no
        # mods, so this is exactly the case that hits the bridges and the rails.
        if needed.elem in (None, nbt.END):
            needed.elem = nbt.STRING
    return (name, root), changed


def write_blueprint(path, name, root):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    buffer = io.BytesIO()
    buffer.write(bytes([root.type]))
    encoded = name.encode("utf-8")
    buffer.write(len(encoded).to_bytes(2, "big") + encoded)
    nbt._write(buffer, root)
    open(path, "wb").write(gzip.compress(buffer.getvalue(), mtime=0))


def icon_for(style, source_zip, source_pack):
    """The source pack's own icon, kept so a style has a picture in the menu."""
    for entry in source_zip.namelist():
        if entry.startswith(f"blueprints/minecolonies/{source_pack}/") \
                and entry.endswith(".png") and entry.count("/") == 4:
            return os.path.basename(entry), source_zip.read(entry)
    return None, None


def main():
    if not os.path.isfile(SOURCE_JAR):
        raise SystemExit("MineColonies is not at " + SOURCE_JAR
                         + "; the styles are re-themed from its own packs")
    shutil.rmtree(OUT, ignore_errors=True)
    archive = zipfile.ZipFile(SOURCE_JAR)
    total = 0
    for style in STYLES:
        prefix = f"blueprints/minecolonies/{style['source']}/"
        files = [n for n in archive.namelist()
                 if n.startswith(prefix) and n.endswith(".blueprint")]
        if not files:
            raise SystemExit("no blueprints in source pack " + style["source"])
        root = os.path.join(OUT, style["name"])
        swapped = 0
        for entry in files:
            read, changed = remap(archive.read(entry), style["swap"])
            if read is None:
                continue
            name, tree = read
            swapped += changed
            write_blueprint(os.path.join(root, entry[len(prefix):]), name, tree)

        icon_name, icon = icon_for(style, archive, style["source"])
        if icon:
            open(os.path.join(root, icon_name), "wb").write(icon)
        json.dump({
            "name": style["name"],
            "icon": icon_name or "",
            "authors": ["Elysium", "re-themed from MineColonies' " + style["source"]],
            "desc": style["desc"],
            "mods": style["mods"],
            "version": 1,
            "pack-format": 1,
        }, open(os.path.join(root, "pack.json"), "w", encoding="utf-8"), indent=2)
        size = sum(os.path.getsize(os.path.join(base, f))
                   for base, _, names in os.walk(root) for f in names)
        print(f"{style['name']:12s} {len(files):4d} blueprints from "
              f"{style['source']:12s} {swapped:6d} blocks swapped  "
              f"{size / 1048576:5.2f} MB")
        total += len(files)
    print(f"{len(STYLES)} styles, {total} blueprints")


if __name__ == "__main__":
    main()
