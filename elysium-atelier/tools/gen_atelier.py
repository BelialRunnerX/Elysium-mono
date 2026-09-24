#!/usr/bin/env python3
"""
Two hundred blocks, modelled, stated, dropped, named and craftable.

    python3 tools/gen_atelier.py

Nothing here is drawn. Every shape wears the vanilla texture of the material it
is cut from, which is the reason this module ships no sprites, declares no
dependencies, and looks like part of the game rather than like a mod.

The geometry below has to agree with `ShapedBlock.boxFor` — the model a player
sees and the box they walk into are two descriptions of one block, and two
files that must agree are one file too many. validate.py reads both and
compares them, so they cannot drift.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumatelier"

OUT = os.environ.get("ELYSIUM_ATELIER_OUT", MOD)
ASSETS = os.path.join(OUT, "src/main/resources/assets", NS)
DATA = os.path.join(OUT, "src/main/resources/data", NS)
RULES = os.path.join(MOD, "src/main/java/com/elysium/atelier/rules/AtelierRules.java")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(obj, handle, indent=2)
        handle.write("\n")


# ---------------------------------------------------------------------------
# The catalogue, read out of the Java
# ---------------------------------------------------------------------------
#
# The shapes and materials are declared once, in the rules, where a harness can
# count them. Reading them back here means a thirteenth shape is one line
# there and nothing at all here.

def catalogue():
    source = open(RULES, encoding="utf-8").read()
    shapes = [(name, int(units), int(made), int(of)) for name, units, made, of
              in re.findall(r'new Shape\("([a-z_]+)", (\d+), (\d+), (\d+)\)', source)]
    # Four fields now, the fourth being whether a hand alone works the
    # material. It changes nothing about a chair and everything about a door,
    # so it is read here rather than guessed from the name.
    materials = []
    for name in re.findall(r'for \(String name : List\.of\(([^)]*)\)\)', source)[0] \
            .replace("\n", "").replace('"', "").split(","):
        name = name.strip()
        if name:
            materials.append((name, f"minecraft:{name}_planks",
                              f"minecraft:block/{name}_planks", True))
    for name, block, face, soft in re.findall(
            r'found\.add\(new Material\("([a-z_]+)",\s*"([^"]+)",\s*"([^"]+)",\s*'
            r'(true|false)\)\);', source, re.S):
        materials.append((name, block, face, soft == "true"))
    for name, texture in re.findall(r'found\.add\(stone\("([a-z_]+)",\s*"([a-z_]+)"\)\);',
                                    source):
        materials.append((name, f"minecraft:{name}", f"minecraft:block/{texture}", False))

    # And counted against the Java rather than trusted. A regex that stops
    # matching because somebody wrapped a line reads *fewer* materials and
    # generates a smaller, perfectly valid-looking catalogue — which is how
    # two of these went missing the first time this ran.
    declared = (len(re.findall(r"found\.add\(new Material\(", source))
                + len(re.findall(r"found\.add\(stone\(", source))
                + len(re.findall(r'"[a-z_]+"', re.findall(
                    r'for \(String name : List\.of\(([^)]*)\)\)', source)[0])))
    if len(materials) != declared:
        print(f"the rules declare {declared} materials and this file could read "
              f"{len(materials)}; something was wrapped onto a second line and the "
              f"catalogue would have come out short")
        sys.exit(1)
    return shapes, materials


# ---------------------------------------------------------------------------
# The geometry
# ---------------------------------------------------------------------------
#
# Boxes in the usual 0-16, in the order the model draws them. The bounding box
# of each list has to equal ShapedBlock.boxFor's box for that shape, and
# validate.py checks it.

GEOMETRY = {
    "stool": [(3, 7, 3, 13, 9, 13),
              (3, 0, 3, 5, 7, 5), (11, 0, 3, 13, 7, 5),
              (3, 0, 11, 5, 7, 13), (11, 0, 11, 13, 7, 13)],
    "chair": [(3, 7, 3, 13, 9, 13),
              (3, 0, 3, 5, 7, 5), (11, 0, 3, 13, 7, 5),
              (3, 0, 11, 5, 7, 13), (11, 0, 11, 13, 7, 13),
              (3, 9, 11, 13, 17, 13)],
    "bench": [(0, 7, 3, 16, 9, 13),
              (0, 0, 3, 2, 7, 5), (14, 0, 3, 16, 7, 5),
              (0, 0, 11, 2, 7, 13), (14, 0, 11, 16, 7, 13)],
    "table": [(0, 12, 0, 16, 16, 16),
              (1, 0, 1, 3, 12, 3), (13, 0, 1, 15, 12, 3),
              (1, 0, 13, 3, 12, 15), (13, 0, 13, 15, 12, 15)],
    "shelf": [(0, 10, 10, 16, 13, 16),
              (1, 4, 13, 3, 10, 16), (13, 4, 13, 15, 10, 16)],
    "cabinet": [(0, 0, 0, 16, 16, 16)],
    "crate": [(0, 0, 0, 16, 16, 16)],
    "pillar": [(0, 0, 0, 16, 16, 16)],
    "beam": [(5, 5, 0, 11, 11, 16)],
    "lattice": [(0, 0, 7, 16, 16, 9)],
    "shutter": [(0, 0, 13, 16, 16, 16)],
    "rug": [(0, 0, 0, 16, 1, 16)],
}

#: The one shape that stands along an axis rather than facing a way, which has
#: to match AtelierBlocks.UPRIGHT.
UPRIGHT = ["pillar"]


def faces(texture):
    return {side: {"texture": "#all"} for side in
            ("down", "up", "north", "south", "west", "east")}


def model(shape, texture):
    """A model out of boxes, all of them wearing one face."""
    return {
        "parent": "block/block",
        "textures": {"all": texture, "particle": texture},
        "elements": [{"from": list(box[:3]), "to": list(box[3:]),
                      "faces": faces(texture)} for box in GEOMETRY[shape]],
    }


def blockstate(name, shape):
    if shape in UPRIGHT:
        return {"variants": {
            "axis=y": {"model": f"{NS}:block/{name}"},
            "axis=x": {"model": f"{NS}:block/{name}", "x": 90, "y": 90},
            "axis=z": {"model": f"{NS}:block/{name}", "x": 90},
        }}
    variants = {}
    for water in ("false", "true"):
        for facing, turn in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            one = {"model": f"{NS}:block/{name}"}
            if turn:
                one["y"] = turn
            variants[f"facing={facing},waterlogged={water}"] = one
    return {"variants": variants}


def models_and_states(shapes, materials):
    written = 0
    for name, block, face, _soft in materials:
        for shape, _, _, _ in shapes:
            id = f"{name}_{shape}"
            write(os.path.join(ASSETS, "models/block", id + ".json"), model(shape, face))
            write(os.path.join(ASSETS, "blockstates", id + ".json"),
                  blockstate(id, shape))
            write(os.path.join(ASSETS, "models/item", id + ".json"),
                  {"parent": f"{NS}:block/{id}"})
            written += 1
    return written


# ---------------------------------------------------------------------------
# Loot and crafting
# ---------------------------------------------------------------------------

def drop_itself(id):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{id}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }


#: How a shape is laid out in a grid. The *number* of material blocks each
#: pattern uses has to equal the shape's `of` in the rules, which is where the
#: price comes from — so the patterns are checked against it rather than
#: trusted.
PATTERNS = {
    "stool": ["M", "M"],
    "bench": ["MM"],
    "chair": ["M ", "MM"],
    "crate": ["MM", "MM"],
    "lattice": ["M M", "M M"],
    "table": ["MMM", " M "],
    "shelf": ["MMM"],
    "pillar": ["M", "M", "M"],
    "cabinet": ["MM", "MM", "MM"],
    "beam": ["MMM", "MMM"],
    "shutter": ["M M", "MMM"],
    "rug": [" M ", "MMM", " M "],
}


def shaped(pattern, material, id, count):
    return {"type": "minecraft:crafting_shaped", "category": "building",
            "pattern": pattern, "key": {"M": {"item": material}},
            "result": {"id": f"{NS}:{id}", "count": count}}


def reclaim(id, material, count):
    """One shape back into the material it was cut from.

    Only for the shapes that cost a whole block each, and only ever at exactly
    what they cost — which is what stops the pair being a loop. A shape worth
    six eighths gives back nothing, because six eighths of a plank is not a
    plank and pretending otherwise is free wood.
    """
    return {"type": "minecraft:crafting_shapeless", "category": "building",
            "ingredients": [{"item": f"{NS}:{id}"}],
            "result": {"id": material, "count": count}}


def data_files(shapes, materials):
    written = 0
    for name, block, _, _soft in materials:
        for shape, units, made, of in shapes:
            id = f"{name}_{shape}"
            write(os.path.join(DATA, "loot_table/blocks", id + ".json"), drop_itself(id))
            write(os.path.join(DATA, "recipe", id + ".json"),
                  shaped(PATTERNS[shape], block, id, made))
            written += 2
            back = units // 8
            if back >= 1:
                write(os.path.join(DATA, "recipe", id + "_reclaim.json"),
                      reclaim(id, block, back))
                written += 1
    tags = os.path.join(OUT, "src/main/resources/data/minecraft/tags/block")
    # Which tool mines it comes from the material's own softness, not from
    # whether its block id happens to end in "_planks". Those two agreed right
    # up until they did not, and a block in neither tag mines at hand speed
    # with no tool at all — which reads as a bug in the pack rather than in one
    # line of a generator.
    wooden = [f"{NS}:{name}_{shape}" for name, _block, _face, soft in materials
              if soft for shape, _, _, _ in shapes]
    stony = [f"{NS}:{name}_{shape}" for name, _block, _face, soft in materials
             if not soft for shape, _, _, _ in shapes]
    write(os.path.join(tags, "mineable/axe.json"), {"replace": False, "values": wooden})
    write(os.path.join(tags, "mineable/pickaxe.json"),
          {"replace": False, "values": stony})
    written += 2
    return written


# ---------------------------------------------------------------------------
# The joins: the five kinds whose whole point is meeting their neighbours
# ---------------------------------------------------------------------------
#
# Only in the materials the game has none of, which is the stone half of the
# list plus panes in everything. The table below is the second copy of
# JoinRules.VANILLA_ALREADY and of JoinRules.tagsFor; validate.py runs the
# harness and diffs the tag files this writes against what the rules say,
# rather than against this, so the two copies are checked rather than trusted.

KINDS = ["door", "trapdoor", "gate", "fence", "pane"]

VANILLA_ALREADY = {
    "door": {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove",
             "cherry", "bamboo", "crimson", "warped"},
    "trapdoor": {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove",
                 "cherry", "bamboo", "crimson", "warped"},
    "gate": {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove",
             "cherry", "bamboo", "crimson", "warped"},
    "fence": {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove",
              "cherry", "bamboo", "crimson", "warped"},
    "pane": set(),
}

KIND_NAMES = {"door": "Door", "trapdoor": "Trapdoor", "gate": "Gate",
              "fence": "Fence", "pane": "Pane"}

#: What one crafting takes and makes, in the game's own shapes. The material
#: block stands in for planks throughout, because every one of these is made
#: of something the game has no wooden version of.
GRIDS = {
    "door":     (["MM", "MM", "MM"], 3),
    "trapdoor": (["MMM", "MMM"], 2),
    "gate":     (["MMM", "MMM"], 2),
    "fence":    (["MMM", "MMM"], 3),
    "pane":     (["MMM", "MMM"], 16),
}


def openings(materials):
    """Every joining block this module adds, as (material, kind, face, soft)."""
    made = []
    for name, _block, face, soft in materials:
        for kind in KINDS:
            if name in VANILLA_ALREADY[kind]:
                continue
            made.append((name, kind, face, soft))
    return made


def join_tags(name, kind, soft):
    """The second copy of JoinRules.tagsFor. Diffed against the first."""
    tags = set()
    if kind == "door":
        tags.add("minecraft:doors")
        if soft:
            tags.add("minecraft:wooden_doors")
    elif kind == "trapdoor":
        tags.add("minecraft:trapdoors")
        if soft:
            tags.add("minecraft:wooden_trapdoors")
    elif kind == "gate":
        tags.add("minecraft:fence_gates")
    elif kind == "fence":
        tags.add("minecraft:fences")
        if soft:
            tags.add("minecraft:wooden_fences")
    tags.add("minecraft:mineable/axe" if soft else "minecraft:mineable/pickaxe")
    return tags


def join_models_and_states(materials):
    """
    Models and blockstates for the five kinds.

    <p>Every one of them is a vanilla template wearing the material's own
    vanilla face, which is the same principle the twelve shapes follow: a
    stone door is a door-shaped piece of stone, and it should look like one.
    """
    written = 0
    for name, kind, face, _soft in openings(materials):
        id = f"{name}_{kind}"
        if kind == "door":
            for half in ("bottom", "top"):
                for hinge in ("left", "right"):
                    for state in ("", "_open"):
                        model = f"{id}_{half}_{hinge}{state}"
                        write(os.path.join(ASSETS, "models/block", model + ".json"), {
                            "parent": f"minecraft:block/door_{half}_{hinge}{state}",
                            "textures": {"bottom": face, "top": face},
                        })
                        written += 1
            write(os.path.join(ASSETS, "blockstates", id + ".json"),
                  vanilla_shaped(id, "door"))
            # A door's item is a sprite in vanilla. Pointing it at the closed
            # bottom-left model instead gives a small three-dimensional door,
            # which reads as a door and needs no artwork of ours.
            write(os.path.join(ASSETS, "models/item", id + ".json"),
                  {"parent": f"{NS}:block/{id}_bottom_left"})
            written += 2
        elif kind == "trapdoor":
            for part in ("bottom", "top", "open"):
                write(os.path.join(ASSETS, "models/block", f"{id}_{part}.json"), {
                    # The plain template, not the orientable one. The
                    # orientable variant turns its texture with the facing,
                    # which on two of four facings is a trapdoor with its
                    # grain running the wrong way — and the blockstate naming
                    # it is perfect either way.
                    "parent": f"minecraft:block/template_trapdoor_{part}",
                    "textures": {"texture": face},
                })
                written += 1
            write(os.path.join(ASSETS, "blockstates", id + ".json"),
                  vanilla_shaped(id, "trapdoor"))
            write(os.path.join(ASSETS, "models/item", id + ".json"),
                  {"parent": f"{NS}:block/{id}_bottom"})
            written += 2
        elif kind == "gate":
            for part in ("", "_open", "_wall", "_wall_open"):
                write(os.path.join(ASSETS, "models/block", f"{id}{part}.json"), {
                    "parent": f"minecraft:block/template_fence_gate{part}",
                    "textures": {"texture": face},
                })
                written += 1
            write(os.path.join(ASSETS, "blockstates", id + ".json"),
                  vanilla_shaped(id, "gate"))
            write(os.path.join(ASSETS, "models/item", id + ".json"),
                  {"parent": f"{NS}:block/{id}"})
            written += 2
        elif kind == "fence":
            write(os.path.join(ASSETS, "models/block", f"{id}_post.json"), {
                "parent": "minecraft:block/fence_post", "textures": {"texture": face}})
            write(os.path.join(ASSETS, "models/block", f"{id}_side.json"), {
                "parent": "minecraft:block/fence_side", "textures": {"texture": face}})
            write(os.path.join(ASSETS, "models/block", f"{id}_inventory.json"), {
                "parent": "minecraft:block/fence_inventory",
                "textures": {"texture": face}})
            write(os.path.join(ASSETS, "blockstates", id + ".json"),
                  vanilla_shaped(id, "fence"))
            write(os.path.join(ASSETS, "models/item", id + ".json"),
                  {"parent": f"{NS}:block/{id}_inventory"})
            written += 5
        elif kind == "pane":
            for part, template in (("post", "template_glass_pane_post"),
                                   ("side", "template_glass_pane_side"),
                                   ("side_alt", "template_glass_pane_side_alt"),
                                   ("noside", "template_glass_pane_noside"),
                                   ("noside_alt", "template_glass_pane_noside_alt")):
                write(os.path.join(ASSETS, "models/block", f"{id}_{part}.json"), {
                    "parent": f"minecraft:block/{template}",
                    "textures": {"pane": face, "edge": face},
                })
                written += 1
            write(os.path.join(ASSETS, "blockstates", id + ".json"),
                  vanilla_shaped(id, "pane"))
            write(os.path.join(ASSETS, "models/item", id + ".json"),
                  {"parent": "minecraft:item/generated", "textures": {"layer0": face}})
            written += 2
    return written


#: The game's own blockstates for these five, transcribed once out of the jar
#: with the model name replaced by a placeholder.
#:
#: They were derived by hand first. Three of the five came out right and two
#: did not: the trapdoor rotated its top half the wrong way, and the pane put
#: its four "noside" pieces on the wrong sides — which is a pane with gaps in
#: it that reads as a rendering bug. Deriving a rotation is guessing at
#: somebody else's table; copying it is not. validate.py reads the jar and
#: proves these are still what the game does.
TEMPLATE_FILE = os.path.join(HERE, "data", "vanilla_blockstates.json")
with open(TEMPLATE_FILE, encoding="utf-8") as _handle:
    TEMPLATES = json.load(_handle)


def vanilla_shaped(id, kind):
    """The game's own arrangement for this kind, wearing our names."""
    if kind not in TEMPLATES:
        print(f"no transcribed blockstate for a {kind}")
        sys.exit(1)
    text = json.dumps(TEMPLATES[kind])
    return json.loads(text.replace("{ID}", f"{NS}:block/{id}"))


def join_data(materials):
    """Recipes, drops and every tag the five kinds need."""
    written = 0
    tags = {}
    for name, kind, _face, soft in openings(materials):
        id = f"{name}_{kind}"
        block = [b for n, b, _f, _s in materials if n == name][0]
        grid, count = GRIDS[kind]
        write(os.path.join(DATA, "recipe", id + ".json"), {
            "type": "minecraft:crafting_shaped",
            "pattern": grid,
            "key": {"M": {"item": block}},
            "result": {"id": f"{NS}:{id}", "count": count},
        })
        # A door drops itself once, from its lower half only, or breaking one
        # gives two doors.
        write(os.path.join(DATA, "loot_table/blocks", id + ".json"),
              door_drop(id) if kind == "door" else drop_itself(id))
        written += 2
        for tag in join_tags(name, kind, soft):
            tags.setdefault(tag, []).append(f"{NS}:{id}")
    for tag, values in sorted(tags.items()):
        namespace, path = tag.split(":", 1)
        where = os.path.join(OUT, "src/main/resources/data", namespace, "tags/block",
                             path + ".json")
        existing = []
        if os.path.isfile(where):
            with open(where, encoding="utf-8") as handle:
                existing = json.load(handle).get("values", [])
        merged = sorted(set(existing) | set(values))
        write(where, {"replace": False, "values": merged})
        written += 1
    return written


def door_drop(id):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{id}"}],
            "conditions": [{
                "condition": "minecraft:block_state_property",
                "block": f"{NS}:{id}",
                "properties": {"half": "lower"},
            }],
        }],
    }


# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

SHAPE_NAMES = {
    "stool": "Stool", "chair": "Chair", "bench": "Bench", "table": "Table",
    "shelf": "Shelf", "cabinet": "Cabinet", "crate": "Crate", "pillar": "Pillar",
    "beam": "Beam", "lattice": "Lattice", "shutter": "Shutter", "rug": "Rug",
}

MATERIAL_NAMES = {
    "oak": "Oak", "spruce": "Spruce", "birch": "Birch", "jungle": "Jungle",
    "acacia": "Acacia", "dark_oak": "Dark Oak", "mangrove": "Mangrove",
    "cherry": "Cherry", "crimson": "Crimson", "warped": "Warped",
    "bamboo": "Bamboo", "stone": "Stone", "deepslate": "Deepslate", "tuff": "Tuff",
    "blackstone": "Blackstone", "quartz": "Quartz",
}


def language(shapes, materials):
    entries = {"itemGroup.elysiumatelier": "Elysium Atelier"}
    for name, _, _, _soft in materials:
        for shape, _, _, _ in shapes:
            entries[f"block.{NS}.{name}_{shape}"] = \
                f"{MATERIAL_NAMES[name]} {SHAPE_NAMES[shape]}"
    for name, kind, _face, _soft in openings(materials):
        entries[f"block.{NS}.{name}_{kind}"] = \
            f"{MATERIAL_NAMES[name]} {KIND_NAMES[kind]}"
    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(entries.items())))
    return len(entries)


if __name__ == "__main__":
    shapes, materials = catalogue()
    missing = [s for s, _, _, _ in shapes if s not in GEOMETRY or s not in PATTERNS]
    spare = [s for s in GEOMETRY if s not in {n for n, _, _, _ in shapes}]
    unnamed = [s for s, _, _, _ in shapes if s not in SHAPE_NAMES] \
        + [m for m, _, _, _ in materials if m not in MATERIAL_NAMES]
    if missing or spare or unnamed:
        print(f"the rules and this file disagree: {missing} have no geometry or "
              f"pattern, {spare} are geometry for nothing, and {unnamed} have no name")
        sys.exit(1)
    laid = {}
    for shape in PATTERNS:
        # Under mirroring, because the game matches a shaped recipe both ways
        # round: two shapes with mirror-image patterns are one recipe, and
        # which of them you get is whichever the recipe manager listed first.
        key = min("|".join(PATTERNS[shape]),
                  "|".join(row[::-1] for row in PATTERNS[shape]))
        if key in laid:
            print(f"{shape} and {laid[key]} are laid out the same way, so they are one "
                  f"recipe and which you get is whichever was listed first")
            sys.exit(1)
        laid[key] = shape
    for shape, units, made, of in shapes:
        used = sum(row.count("M") for row in PATTERNS[shape])
        if used != of:
            print(f"{shape}'s grid uses {used} material blocks and the rules price it "
                  f"at {of}; the price and the recipe would disagree")
            sys.exit(1)
    made = models_and_states(shapes, materials)
    data = data_files(shapes, materials)
    # The joins go after the shapes, because data_files() writes the two
    # mineable tags wholesale and join_data() has to merge into what it left.
    joined = join_models_and_states(materials)
    data += join_data(materials)
    keys = language(shapes, materials)
    print(f"atelier: {len(shapes)} shape(s) x {len(materials)} material(s) = {made} "
          f"block(s) modelled and stated, {len(openings(materials))} joining block(s) "
          f"in {joined} more file(s), {data} data file(s), {keys} language key(s)")
