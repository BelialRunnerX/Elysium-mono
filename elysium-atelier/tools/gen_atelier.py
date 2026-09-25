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
    # Two loops now, one per helper, and each is found by the helper it calls
    # rather than by being first in the file. Reading "the first List.of" was
    # fine while there was one loop and silently dropped twenty materials the
    # day there were two.
    loops = re.findall(
        r'for \(String name : List\.of\(([^)]*)\)\) \{\s*found\.add\((\w+)\(',
        source, re.S)
    looped = 0
    for listed, helper in loops:
        for name in listed.replace("\n", "").replace('"', "").split(","):
            name = name.strip()
            if not name:
                continue
            looped += 1
            if helper == "wood":
                materials.append((name, f"minecraft:{name}_planks",
                                  f"minecraft:block/{name}_planks", True))
            elif helper == "stone":
                materials.append((name, f"minecraft:{name}",
                                  f"minecraft:block/{name}", False))
            else:
                print(f"the rules build materials with a helper called {helper} "
                      f"and this file does not know what that one does")
                sys.exit(1)
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
                + len(re.findall(r"found\.add\(stone\(\"", source))
                + looped)
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
    "counter": [(0, 0, 0, 16, 14, 16)],
    "sink": [(0, 0, 0, 16, 14, 16), (0, 14, 0, 16, 16, 2),
             (0, 14, 14, 16, 16, 16), (0, 14, 0, 2, 16, 16),
             (14, 14, 0, 16, 16, 16)],
    "stove": [(0, 0, 0, 16, 16, 16)],
    "wardrobe": [(1, 0, 3, 15, 16, 15)],
    "drawers": [(0, 0, 2, 16, 14, 16), (1, 14, 1, 15, 15, 16)],
    "nightstand": [(2, 0, 2, 14, 12, 14)],
    "desk": [(0, 12, 0, 16, 14, 16), (1, 0, 1, 4, 12, 15),
             (12, 0, 1, 15, 12, 15)],
    "planter": [(2, 0, 2, 14, 10, 14)],
    "trough": [(0, 0, 3, 16, 9, 13)],
    "rack": [(0, 12, 12, 16, 16, 16), (0, 2, 14, 2, 12, 16),
             (14, 2, 14, 16, 12, 16)],
    "screen": [(0, 0, 6, 16, 16, 10)],
    "post": [(6, 0, 6, 10, 16, 10)],
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
    "counter": ["MMM", "M  "],
    "sink": ["M M"],
    "stove": ["M ", " M"],
    "wardrobe": ["MMM", "MM ", "M  "],
    "drawers": ["MMM", "MM "],
    "nightstand": ["MM", "M "],
    "desk": ["MMM", "M M"],
    "planter": ["MM ", "  M"],
    "trough": ["MM ", "M M"],
    "rack": ["M M", "M  "],
    "screen": ["M M", " M "],
    "post": ["MM ", " MM"],
}


def trimmed_grid(grid):
    """A grid with its empty rows and columns taken off.

    The game slides a shaped recipe around the crafting grid, so ["MMM", "   "]
    and ["MMM"] are one recipe. Comparing the written strings says they are
    two, and two blocks on one recipe is one block nobody can make.
    """
    rows = list(grid)
    while rows and not rows[0].strip():
        rows.pop(0)
    while rows and not rows[-1].strip():
        rows.pop()
    if not rows:
        return []
    wide = max(len(row) for row in rows)
    rows = [row.ljust(wide) for row in rows]
    # Only the outer columns. An empty column in the *middle* is part of the
    # shape -- a lattice is four blocks with a hole in it and a crate is four
    # blocks with none, and taking the hole out makes them the same recipe
    # when the game says they are not.
    first, last = 0, wide - 1
    while first <= last and all(row[first] == " " for row in rows):
        first += 1
    while last >= first and all(row[last] == " " for row in rows):
        last -= 1
    return [row[first:last + 1] for row in rows]


def grid_key(grid):
    """One name for a grid and its mirror image, ignoring where it sits."""
    trimmed = trimmed_grid(grid)
    return min("|".join(trimmed),
               "|".join(row[::-1] for row in trimmed))


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
    "counter": "Counter", "sink": "Sink", "stove": "Stove",
    "wardrobe": "Wardrobe", "drawers": "Drawers", "nightstand": "Nightstand",
    "desk": "Desk", "planter": "Planter", "trough": "Trough", "rack": "Rack",
    "screen": "Screen", "post": "Post",
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
    "andesite": "Andesite", "diorite": "Diorite", "granite": "Granite",
    "calcite": "Calcite", "dripstone_block": "Dripstone", "basalt": "Basalt",
    "smooth_basalt": "Smooth Basalt", "sandstone": "Sandstone",
    "red_sandstone": "Red Sandstone", "prismarine": "Prismarine",
    "dark_prismarine": "Dark Prismarine", "purpur_block": "Purpur",
    "bricks": "Brick", "mud_bricks": "Mud Brick", "nether_bricks": "Nether Brick",
    "end_stone_bricks": "End Stone Brick", "amethyst_block": "Amethyst",
}


def language(shapes, materials, pieces_read=(), courses_read=(), already=None,
             fittings_read=(), soft_family="", coloured=((), (), ()),
             diagonals_read=()):
    entries = {"itemGroup.elysiumatelier": "Elysium Atelier"}
    arch_language(entries, pieces_read, materials)
    course_language(entries, courses_read, already or {}, materials)
    trim_language(entries, fittings_read, soft_family, materials)
    cloth_language(entries, coloured[0], coloured[1], coloured[2], materials)
    diagonal_language(entries, diagonals_read, materials)
    for name, _, _, _soft in materials:
        for shape, _, _, _ in shapes:
            entries[f"block.{NS}.{name}_{shape}"] = \
                f"{MATERIAL_NAMES[name]} {SHAPE_NAMES[shape]}"
    for name, kind, _face, _soft in openings(materials):
        entries[f"block.{NS}.{name}_{kind}"] = \
            f"{MATERIAL_NAMES[name]} {KIND_NAMES[kind]}"
    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(entries.items())))
    return len(entries)



# ---------------------------------------------------------------------------
# The architectural pieces
# ---------------------------------------------------------------------------
#
# Roofs, paths, lights, bridges and trim: twenty-one pieces in every material.
# Read out of ArchRules the same way the shapes are read out of AtelierRules,
# and the boxes mirrored from ArchRules.boxesFor -- which the harness prints
# and validate.py compares, so a model and a collision box cannot drift apart.

ARCH_RULES = os.path.join(MOD, "src/main/java/com/elysium/atelier/rules/ArchRules.java")


def pieces():
    source = open(ARCH_RULES, encoding="utf-8").read()
    found = []
    # Any of the three numbers may be a ternary on the pitch, so they are read
    # as text and resolved per pitch rather than matched slot by slot. The
    # slot-by-slot version read nineteen of twenty-one the day a second one
    # became a ternary, and the count below is what caught it.
    roofs = re.findall(
        r'found\.add\(roof\(pitch,\s*(\w+),\s*([^,]+),\s*([^,]+),\s*([^)]+)\)\);',
        source)

    def per_pitch(text, steep):
        text = text.strip()
        numbers = re.findall(r"\d+", text)
        if "steep ?" in text:
            return int(numbers[0] if steep else numbers[1])
        return int(numbers[0])
    listed = re.findall(r'PITCHES =\s*List\.of\(([^)]*)\)', source)
    pitches = re.findall(r'"([a-z]+)"', listed[0]) if listed else []
    roles = {"SLOPE": "slope", "OUTER": "outer", "INNER": "inner",
             "RIDGE": "ridge"}
    for pitch in pitches:
        steep = pitch == "steep"
        for role, units, made, of in roofs:
            found.append((pitch + "_roof_" + roles[role], "roof", roles[role],
                          pitch, per_pitch(units, steep), per_pitch(made, steep),
                          per_pitch(of, steep), 8, 0, False))
    # \s* between every field rather than a literal space: five of the
    # thirteen are wrapped onto a second line, and a regex that assumes one
    # line reads eight of them and generates a smaller, perfectly valid
    # catalogue. The count below is what caught that.
    for name, family, role, pitch, units, made, of, tall, light, joins in re.findall(
            r'new Piece\(\s*"([a-z_]+)",\s*(\w+),\s*"([a-z_]+)",\s*"([a-z]*)",'
            r'\s*(\d+),\s*(\d+),\s*(\d+),\s*(\w+),\s*(\w+),\s*(true|false)\s*\)',
            source, re.S):
        found.append((name, family.lower(), role, pitch, int(units), int(made),
                      int(of), 8 if tall == "WHOLE" else int(tall),
                      15 if light == "BRIGHTEST" else int(light), joins == "true"))
    declared = (len(re.findall(r"found\.add\(roof\(", source)) * max(1, len(pitches))
                + len(re.findall(r"found\.add\(new Piece\(", source)))
    if len(found) != declared:
        print("the rules declare " + str(declared) + " pieces and this file could "
              "read " + str(len(found)) + "; something was wrapped onto a second "
              "line and the catalogue would have come out short")
        sys.exit(1)
    return found


#: What each piece is made of, in the grid. The *number* of material blocks
#: each pattern uses has to equal the piece's `of` in the rules, which is where
#: its price comes from -- checked below rather than trusted.
#: The one extra thing each piece is made with.
#:
#: Two purposes, and the second is why it exists. A lamp of stone alone is an
#: odd lamp: a light wants something that gives light in it. And six pieces
#: cost one block each, while there is exactly *one* three-by-three grid
#: holding one block -- so without a second ingredient six of them would be
#: one recipe, and five of those six would be blocks nobody could ever make.
ARCH_EXTRA = {
    "awning": "minecraft:clay_ball",
    "bridge_deck": "minecraft:stick",
    "bridge_rail": "minecraft:chain",
    "bridge_support": "minecraft:iron_nugget",
    "chandelier": "minecraft:chain",
    "cornice": "minecraft:brick",
    "lamp": "minecraft:glowstone_dust",
    "path_border": "minecraft:gravel",
    "path_step": "minecraft:flint",
    "post_light": "minecraft:lantern",
    "sconce": "minecraft:torch",
    "shallow_roof_inner": "minecraft:clay_ball",
    "shallow_roof_outer": "minecraft:clay_ball",
    "shallow_roof_ridge": "minecraft:clay_ball",
    "shallow_roof_slope": "minecraft:clay_ball",
    "sill": "minecraft:clay_ball",
    "steep_roof_inner": "minecraft:clay_ball",
    "steep_roof_outer": "minecraft:clay_ball",
    "steep_roof_ridge": "minecraft:clay_ball",
    "steep_roof_slope": "minecraft:clay_ball",
}

ARCH_PATTERNS = {
    "awning":                ["MM", "MM"],
    "bridge_deck":           ["MM"],
    "bridge_rail":           ["MM"],
    "bridge_support":        ["MMM"],
    "chandelier":            ["M"],
    "cornice":               ["MMM"],
    "lamp":                  ["MM"],
    "path":                  ["M"],
    "path_border":           ["M"],
    "path_step":             ["M"],
    "post_light":            ["M"],
    "sconce":                ["M"],
    "shallow_roof_inner":    ["MMM", "M  "],
    "shallow_roof_outer":    ["MM"],
    "shallow_roof_ridge":    ["MMM"],
    "shallow_roof_slope":    ["MM", "M "],
    "sill":                  ["M ", "MM"],
    "steep_roof_inner":      ["MMM", " M "],
    "steep_roof_outer":      ["M M"],
    "steep_roof_ridge":      ["MM ", "M M"],
    "steep_roof_slope":      ["MM ", "  M"],
}

PIECE_NAMES = {
    "shallow_roof_slope": "Roof", "shallow_roof_outer": "Roof Corner",
    "shallow_roof_inner": "Roof Valley", "shallow_roof_ridge": "Roof Ridge",
    "steep_roof_slope": "Steep Roof", "steep_roof_outer": "Steep Roof Corner",
    "steep_roof_inner": "Steep Roof Valley", "steep_roof_ridge": "Steep Roof Ridge",
    "path": "Path", "path_border": "Path Border", "path_step": "Path Step",
    "sconce": "Sconce", "post_light": "Post Light", "lamp": "Lamp",
    "chandelier": "Chandelier", "bridge_deck": "Bridge Deck",
    "bridge_rail": "Bridge Rail", "bridge_support": "Bridge Support",
    "sill": "Sill", "awning": "Awning", "cornice": "Cornice",
}


def arch_boxes(piece):
    """The boxes a piece is made of, mirroring ArchRules.boxesFor.

    Written twice on purpose and checked against what the harness prints out
    of the Java: a model and a collision box that disagree is a block you fall
    through or cannot step onto, and it looks correct from every angle.
    """
    name, family, role, pitch, units, made, of, tall, light, joins = piece
    deep = tall * 2
    if family == "roof":
        steep = pitch == "steep"
        rise = 12 if steep else 8
        if role == "slope":
            return [(0, 0, 0, 16, 8, 16), (0, 8, 0, 16, 16, rise)]
        if role == "outer":
            return [(0, 0, 0, 16, 8, 16), (0, 8, 0, rise, 16, rise)]
        if role == "inner":
            return [(0, 0, 0, 16, 8, 16), (0, 8, 0, 16, 16, rise),
                    (0, 8, rise, rise, 16, 16)]
        return [(0, 0, 2, 16, 8, 14),
                (0, 8, 4 if steep else 5, 16, 14, 12 if steep else 11)]
    if family == "path":
        return [(0, 0, 0, 16, deep, 16)]
    if role in ("sill", "awning", "cornice", "wall"):
        return [(0, 16 - deep, 0, 16, 16, deep)]
    if role == "deck":
        return [(0, 0, 0, 16, deep, 16)]
    if role in ("post", "standing", "support"):
        return [(4, 0, 4, 12, 16, 12)]
    if role == "hanging":
        return [(3, 4, 3, 13, 16, 13)]
    if role == "rail":
        return [(7, 0, 0, 9, 16, 16)]
    return [(0, 0, 0, 16, 16, 16)]


def arch_model(piece, texture):
    return {
        "parent": "block/block",
        "textures": {"all": texture, "particle": texture},
        "elements": [{"from": list(box[:3]), "to": list(box[3:]),
                      "faces": faces(texture)} for box in arch_boxes(piece)],
    }


def pane_model(part, texture):
    return {"parent": "minecraft:block/template_glass_pane_" + part,
            "textures": {"pane": texture, "edge": texture, "particle": texture}}


def pane_state(id):
    """The game's own pane blockstate: five models and a multipart."""
    return {"multipart": [
        {"apply": {"model": NS + ":block/" + id + "_post"}},
        {"when": {"north": "true"},
         "apply": {"model": NS + ":block/" + id + "_side"}},
        {"when": {"east": "true"},
         "apply": {"model": NS + ":block/" + id + "_side", "y": 90}},
        {"when": {"south": "true"},
         "apply": {"model": NS + ":block/" + id + "_side_alt"}},
        {"when": {"west": "true"},
         "apply": {"model": NS + ":block/" + id + "_side_alt", "y": 90}},
        {"when": {"north": "false"},
         "apply": {"model": NS + ":block/" + id + "_noside"}},
        {"when": {"east": "false"},
         "apply": {"model": NS + ":block/" + id + "_noside_alt"}},
        {"when": {"south": "false"},
         "apply": {"model": NS + ":block/" + id + "_noside_alt", "y": 90}},
        {"when": {"west": "false"},
         "apply": {"model": NS + ":block/" + id + "_noside", "y": 270}},
    ]}


def arch_files(pieces_read, materials):
    """Every architectural block: model, state, item, drop and recipe."""
    blocks = data = 0
    for name, block, face, _soft in materials:
        for piece in pieces_read:
            id = name + "_" + piece[0]
            if piece[9]:
                write(os.path.join(ASSETS, "blockstates", id + ".json"),
                      pane_state(id))
                for part in ("post", "side", "side_alt", "noside", "noside_alt"):
                    write(os.path.join(ASSETS, "models/block",
                                       id + "_" + part + ".json"),
                          pane_model(part, face))
                write(os.path.join(ASSETS, "models/item", id + ".json"),
                      {"parent": "item/generated", "textures": {"layer0": face}})
            else:
                write(os.path.join(ASSETS, "models/block", id + ".json"),
                      arch_model(piece, face))
                write(os.path.join(ASSETS, "blockstates", id + ".json"),
                      blockstate(id, piece[0]))
                write(os.path.join(ASSETS, "models/item", id + ".json"),
                      {"parent": NS + ":block/" + id})
            write(os.path.join(DATA, "loot_table/blocks", id + ".json"),
                  drop_itself(id))
            write(os.path.join(DATA, "recipe", id + ".json"),
                  arch_shaped(piece[0], block, id, piece[5]))
            blocks += 1
            data += 2
    return blocks, data


def arch_shaped(piece, material, id, count):
    """A piece's recipe: its material, and the one thing that says what it is."""
    grid = ARCH_PATTERNS[piece]
    key = {"M": {"item": material}}
    extra = ARCH_EXTRA.get(piece)
    if extra:
        # Along the bottom, in its own row, so the shape above stays the shape
        # the piece looks like.
        wide = max(len(row) for row in grid)
        grid = [row.ljust(wide) for row in grid] + ["X".ljust(wide)]
        key["X"] = {"item": extra}
    return {"type": "minecraft:crafting_shaped", "category": "building",
            "pattern": grid, "key": key,
            "result": {"id": NS + ":" + id, "count": count}}


def arch_language(entries, pieces_read, materials):
    for name, _block, _face, _soft in materials:
        for piece in pieces_read:
            entries["block." + NS + "." + name + "_" + piece[0]] = \
                MATERIAL_NAMES[name] + " " + PIECE_NAMES[piece[0]]
    return entries


# ---------------------------------------------------------------------------
# The courses: the cuts of a block, and the ways up one
# ---------------------------------------------------------------------------
#
# Read out of CourseRules.java the same way the pieces are read out of
# ArchRules.java, and counted afterwards, because a regex that reads eleven of
# thirteen kinds writes a smaller, perfectly valid catalogue and the two
# missing blocks are simply not in the game.
#
# The four cuts the game already knows are drawn with the game's own
# blockstates, transcribed out of the jar rather than derived: a stair has
# forty variants and one of them wrong is a corner you fall through from
# exactly one direction.

COURSE_RULES = os.path.join(MOD,
                            "src/main/java/com/elysium/atelier/rules/CourseRules.java")


def courses():
    """Every kind, and every material each one is missing from the game in."""
    source = open(COURSE_RULES, encoding="utf-8").read()
    named = dict(re.findall(
        r'public static final String (\w+)\s*=\s*"([a-z_]+)";', source))
    kinds = []
    for name, family, role, made, of, joins in re.findall(
            r'new Kind\(\s*(\w+),\s*(\w+),\s*("[a-z]*"|\w+),\s*(\d+),\s*(\d+),'
            r'\s*(true|false)\s*\)', source, re.S):
        kinds.append((named[name], named[family],
                      role.strip('"') if role.startswith('"') else named[role],
                      int(made), int(of), joins == "true"))
    declared = len(re.findall(r"new Kind\(", source))
    if len(kinds) != declared:
        print("the course rules declare " + str(declared) + " kinds and this file "
              "could read " + str(len(kinds)) + "; one of them was wrapped onto a "
              "second line and those blocks would simply not exist")
        sys.exit(1)

    global STONECUT
    listed = re.search(r"STONECUT =\s*List\.of\(([^)]*)\);", source, re.S)
    if not listed:
        print("the course rules no longer say which kinds a stonecutter makes, so "
              "every one of them would want a grid and there are not enough grids")
        sys.exit(1)
    STONECUT = tuple(named[name] for name in re.findall(r"\b([A-Z_]+)\b",
                                                        listed.group(1)))

    block = re.search(r"VANILLA_ALREADY = Map\.of\((.*?)\);", source, re.S)
    if not block:
        print("the course rules no longer say what the game already has, so this "
              "file would add a second oak stair to a game that has one")
        sys.exit(1)
    already = {}
    for name, listed in re.findall(r'(\w+),\s*Set\.of\(([^)]*)\)', block.group(1)):
        already[named[name]] = set(re.findall(r'"([a-z_]+)"', listed))
    for kind, _family, _role, _made, _of, _joins in kinds:
        already.setdefault(kind, set())
    return kinds, already


#: The boxes each course is drawn and collided as, mirroring
#: CourseRules.boxesFor. Written twice on purpose and diffed by validate.py
#: against what the harness prints: a model and a collision box that disagree
#: is a stair you cannot step onto and it looks right from every angle.
COURSE_BOXES = {
    "stair":          [(0, 0, 0, 16, 8, 16), (0, 8, 0, 16, 16, 8)],
    "slab":           [(0, 0, 0, 16, 8, 16)],
    "vertical_slab":  [(0, 0, 0, 8, 16, 16)],
    "wall":           [(4, 0, 4, 12, 16, 12), (5, 0, 0, 11, 14, 16)],
    "compact_stairs": [(0, 0, 0, 8, 8, 16), (0, 8, 0, 8, 16, 8)],
    "loft_stairs":    [(0, 6, 0, 16, 8, 16), (0, 8, 0, 16, 16, 8)],
    "terrace_stairs": [(0, 0, 0, 16, 4, 16), (0, 4, 0, 16, 8, 11),
                       (0, 8, 0, 16, 12, 6), (0, 12, 0, 16, 16, 2)],
    "skyline_stairs": [(0, 0, 0, 16, 8, 16), (0, 8, 0, 16, 16, 8),
                       (0, 8, 14, 3, 16, 16)],
    "bulk_stairs":    [(0, 0, 0, 16, 8, 16), (0, 8, 0, 16, 16, 12)],
    "balcony":        [(0, 0, 0, 16, 3, 16), (0, 3, 13, 16, 14, 16)],
    "railing":        [(6, 0, 6, 10, 16, 10), (0, 10, 7, 16, 14, 9),
                       (0, 1, 7, 16, 4, 9)],
    "platform":       [(0, 4, 0, 16, 8, 16)],
    "ladder":         [(0, 0, 14, 2, 16, 16), (14, 0, 14, 16, 16, 16),
                       (2, 2, 14, 14, 4, 16), (2, 7, 14, 14, 9, 16),
                       (2, 12, 14, 14, 14, 16)],
}

#: The grid each course is made on. The number of M's has to equal what the
#: rules charge for it, which is checked below rather than trusted.
COURSE_PATTERNS = {
    "compact_stairs": ["MM", "M "],
    "loft_stairs":    ["M  ", " M ", "  M"],
    "terrace_stairs": ["M ", "MM", "M "],
    "skyline_stairs": ["M M", "MM "],
    "bulk_stairs":    ["MM", "MM"],
    "balcony":        ["MMM"],
    "railing":        ["MM"],
    "platform":       ["MM"],
}

#: The kinds a stonecutter makes instead, one block at a time, read out of
#: CourseRules below. They have no grid at all, which is the point: this
#: module already has forty-five recipes on arrangements of three blocks, and
#: two blocks on one arrangement is one recipe and one block nobody can make.
STONECUT = ()

#: The one other thing each of these is made with, where its grid alone would
#: be a grid something else already uses. Two blocks on one grid are one
#: recipe and the other is a block nobody can make.
COURSE_EXTRA = {
    "balcony": "minecraft:chain",
    "bulk_stairs": "minecraft:flint",
    "compact_stairs": "minecraft:stick",
    "platform": "minecraft:iron_nugget",
    "railing": "minecraft:iron_ingot",
}

COURSE_NAMES = {
    "stair": "Stairs", "slab": "Slab", "vertical_slab": "Vertical Slab",
    "wall": "Wall", "compact_stairs": "Compact Stairs",
    "loft_stairs": "Loft Stairs", "terrace_stairs": "Terrace Stairs",
    "skyline_stairs": "Skyline Stairs", "bulk_stairs": "Bulk Stairs",
    "balcony": "Balcony", "railing": "Railing", "platform": "Platform",
    "ladder": "Ladder",
}

#: Which kinds are the game's own class, drawn with the game's own state.
VANILLA_DRAWN = ("stair", "slab", "wall", "ladder")


def course_tags(kind, soft):
    """The second copy of CourseRules.tagsFor. Diffed against the first."""
    tags = {"minecraft:mineable/axe" if soft else "minecraft:mineable/pickaxe"}
    if kind == "stair":
        tags.add("minecraft:stairs")
        if soft:
            tags.add("minecraft:wooden_stairs")
    elif kind == "slab":
        tags.add("minecraft:slabs")
        if soft:
            tags.add("minecraft:wooden_slabs")
    elif kind == "wall":
        tags.add("minecraft:walls")
    elif kind == "ladder":
        tags.add("minecraft:climbable")
    return tags


def course_model(kind, texture):
    """A model out of the course's own boxes, all of them wearing one face."""
    return {
        "parent": "block/block",
        "textures": {"all": texture, "particle": texture},
        "elements": [{"from": list(box[:3]), "to": list(box[3:]),
                      "faces": faces(texture)} for box in COURSE_BOXES[kind]],
    }


def course_state(id, kind, block):
    """The game's own arrangement for a cut it already knows, wearing our names."""
    if kind not in TEMPLATES:
        print(f"no transcribed blockstate for a {kind}")
        sys.exit(1)
    text = json.dumps(TEMPLATES[kind])
    text = text.replace("{ID}", f"{NS}:block/{id}")
    # A double slab is the material's own block, and the material's own block
    # is the game's, not ours.
    text = text.replace("{FULL}", "minecraft:block/" + block.split(":", 1)[1])
    return json.loads(text)


def course_templates(id, kind, face):
    """The models under a cut the game already knows, on the game's templates."""
    made = {}
    if kind == "stair":
        cut = {"bottom": face, "top": face, "side": face}
        made[id] = {"parent": "minecraft:block/stairs", "textures": cut}
        made[id + "_inner"] = {"parent": "minecraft:block/inner_stairs",
                               "textures": cut}
        made[id + "_outer"] = {"parent": "minecraft:block/outer_stairs",
                               "textures": cut}
    elif kind == "slab":
        cut = {"bottom": face, "top": face, "side": face}
        made[id] = {"parent": "minecraft:block/slab", "textures": cut}
        made[id + "_top"] = {"parent": "minecraft:block/slab_top", "textures": cut}
    elif kind == "wall":
        made[id + "_post"] = {"parent": "minecraft:block/template_wall_post",
                              "textures": {"wall": face}}
        made[id + "_side"] = {"parent": "minecraft:block/template_wall_side",
                              "textures": {"wall": face}}
        made[id + "_side_tall"] = {
            "parent": "minecraft:block/template_wall_side_tall",
            "textures": {"wall": face}}
        made[id + "_inventory"] = {"parent": "minecraft:block/wall_inventory",
                                   "textures": {"wall": face}}
    return made


def course_recipe(kind, material, id, count):
    """A course's recipe: a stonecutter for a cut, a grid for everything else."""
    if kind in STONECUT:
        return {"type": "minecraft:stonecutting",
                "ingredient": {"item": material},
                "result": {"id": NS + ":" + id, "count": count}}
    return course_shaped(kind, material, id, count)


def course_shaped(kind, material, id, count):
    """A course's grid: its material, and what says which course it is."""
    grid = COURSE_PATTERNS[kind]
    key = {"M": {"item": material}}
    extra = COURSE_EXTRA.get(kind)
    if extra:
        wide = max(len(row) for row in grid)
        grid = [row.ljust(wide) for row in grid] + ["X".ljust(wide)]
        key["X"] = {"item": extra}
    return {"type": "minecraft:crafting_shaped", "category": "building",
            "pattern": grid, "key": key,
            "result": {"id": NS + ":" + id, "count": count}}


def course_files(kinds, already, materials):
    """Every course: model, state, item, drop, recipe and tag."""
    blocks = data = 0
    tags = {}
    for name, block, face, soft in materials:
        for kind, family, role, made, of, joins in kinds:
            if name in already.get(kind, set()):
                continue
            id = name + "_" + kind
            if kind in VANILLA_DRAWN:
                write(os.path.join(ASSETS, "blockstates", id + ".json"),
                      course_state(id, kind, block))
                for part, body in course_templates(id, kind, face).items():
                    write(os.path.join(ASSETS, "models/block", part + ".json"),
                          body)
                if kind == "ladder":
                    write(os.path.join(ASSETS, "models/block", id + ".json"),
                          course_model(kind, face))
                    write(os.path.join(ASSETS, "models/item", id + ".json"),
                          {"parent": "item/generated",
                           "textures": {"layer0": face}})
                elif kind == "wall":
                    write(os.path.join(ASSETS, "models/item", id + ".json"),
                          {"parent": NS + ":block/" + id + "_inventory"})
                else:
                    write(os.path.join(ASSETS, "models/item", id + ".json"),
                          {"parent": NS + ":block/" + id})
            elif joins:
                write(os.path.join(ASSETS, "blockstates", id + ".json"),
                      pane_state(id))
                for part in ("post", "side", "side_alt", "noside", "noside_alt"):
                    write(os.path.join(ASSETS, "models/block",
                                       id + "_" + part + ".json"),
                          pane_model(part, face))
                write(os.path.join(ASSETS, "models/item", id + ".json"),
                      {"parent": "item/generated", "textures": {"layer0": face}})
            else:
                write(os.path.join(ASSETS, "models/block", id + ".json"),
                      course_model(kind, face))
                write(os.path.join(ASSETS, "blockstates", id + ".json"),
                      blockstate(id, kind))
                write(os.path.join(ASSETS, "models/item", id + ".json"),
                      {"parent": NS + ":block/" + id})
            write(os.path.join(DATA, "loot_table/blocks", id + ".json"),
                  drop_itself(id))
            write(os.path.join(DATA, "recipe", id + ".json"),
                  course_recipe(kind, block, id, made))
            for tag in course_tags(kind, soft):
                tags.setdefault(tag, []).append(f"{NS}:{id}")
            blocks += 1
            data += 2
    for tag, values in sorted(tags.items()):
        namespace, path = tag.split(":", 1)
        where = os.path.join(OUT, "src/main/resources/data", namespace,
                             "tags/block", path + ".json")
        existing = []
        if os.path.isfile(where):
            with open(where, encoding="utf-8") as handle:
                existing = json.load(handle).get("values", [])
        merged = sorted(set(existing) | set(values))
        write(where, {"replace": False, "values": merged})
        data += 1
    return blocks, data


def course_language(entries, kinds, already, materials):
    for name, _block, _face, _soft in materials:
        for kind, _family, _role, _made, _of, _joins in kinds:
            if name in already.get(kind, set()):
                continue
            entries["block." + NS + "." + name + "_" + kind] = \
                MATERIAL_NAMES[name] + " " + COURSE_NAMES[kind]
    return entries


# ---------------------------------------------------------------------------
# The fittings: what goes on a wall rather than what a wall is made of
# ---------------------------------------------------------------------------
#
# Twelve shapes, read out of TrimRules.java and counted afterwards. Three of
# them are only made out of something a hand works, which is the one place
# this crossing is not simply every kind times every material -- and it is
# read out of the rules rather than repeated here, because a cushion of
# deepslate is a joke the generator makes on its own.

TRIM_RULES = os.path.join(MOD, "src/main/java/com/elysium/atelier/rules/TrimRules.java")


def fittings():
    """Every fitting, and which family decides what it may be made of."""
    source = open(TRIM_RULES, encoding="utf-8").read()
    named = dict(re.findall(
        r'public static final String (\w+)\s*=\s*"([a-z_]+)";', source))
    kinds = []
    for name, family, made, of, joins, climbs in re.findall(
            r'new Kind\(\s*(\w+),\s*(\w+),\s*(\d+),\s*(\d+),\s*(true|false),'
            r'\s*(true|false)\s*\)', source, re.S):
        kinds.append((named[name], named[family], int(made), int(of),
                      joins == "true", climbs == "true"))
    declared = len(re.findall(r"new Kind\(", source))
    if len(kinds) != declared:
        print("the trim rules declare " + str(declared) + " fittings and this file "
              "could read " + str(len(kinds)) + "; one was wrapped onto a second "
              "line and those blocks would simply not exist")
        sys.exit(1)
    soft_family = named.get("SOFT")
    if not soft_family:
        print("the trim rules no longer say which family is only made out of "
              "something soft, so this file would put a cushion in deepslate")
        sys.exit(1)
    return kinds, soft_family


#: The boxes each fitting is drawn and collided as, mirroring
#: TrimRules.boxesFor and diffed by validate.py against what the harness
#: prints out of the Java.
TRIM_BOXES = {
    "panel":    [(0, 0, 15, 16, 16, 16)],
    "blinds":   [(0, 0, 13, 16, 16, 16)],
    "board":    [(0, 0, 11, 16, 16, 16)],
    "frame":    [(0, 0, 6, 16, 2, 10), (0, 14, 6, 16, 16, 10),
                 (0, 2, 6, 2, 14, 10), (14, 2, 6, 16, 14, 10)],
    "trellis":  [(0, 0, 14, 2, 16, 16), (7, 0, 14, 9, 16, 16),
                 (14, 0, 14, 16, 16, 16), (0, 0, 14, 16, 2, 16),
                 (0, 7, 14, 16, 9, 16), (0, 14, 14, 16, 16, 16)],
    "quoin":    [(0, 0, 0, 16, 16, 3), (0, 0, 3, 3, 16, 16)],
    "banister": [(0, 2, 7, 4, 8, 9), (4, 4, 7, 8, 10, 9),
                 (8, 6, 7, 12, 12, 9), (12, 8, 7, 16, 14, 9)],
    "catwalk":  [(0, 4, 0, 16, 6, 16), (0, 6, 0, 16, 14, 2),
                 (0, 6, 14, 16, 14, 16)],
    "girder":   [(0, 0, 7, 16, 2, 9), (0, 14, 7, 16, 16, 9),
                 (7, 2, 7, 9, 14, 9)],
    "cushion":  [(1, 0, 1, 15, 5, 15)],
    "curtain":  [(0, 0, 14, 16, 16, 16)],
    "rope":     [(7, 0, 7, 9, 16, 9)],
}

#: The grid each fitting is made on. Every one takes two blocks, and there
#: are only four arrangements of two -- so every one of them also takes the
#: one other thing that says which fitting it is.
TRIM_PATTERNS = {
    "panel":    ["MM"],
    "blinds":   ["M M"],
    "board":    ["M", "M"],
    "frame":    ["M M"],
    "trellis":  ["M M"],
    "quoin":    ["MM"],
    "banister": ["M", "M"],
    "catwalk":  ["MM"],
    "girder":   ["M", "M"],
    "cushion":  ["MM"],
    "curtain":  ["MM"],
    "rope":     ["M", "M"],
}

TRIM_EXTRA = {
    "panel": "minecraft:paper",
    "blinds": "minecraft:string",
    "board": "minecraft:stick",
    "frame": "minecraft:stick",
    "trellis": "minecraft:bamboo",
    "quoin": "minecraft:flint",
    "banister": "minecraft:iron_nugget",
    "catwalk": "minecraft:iron_bars",
    "girder": "minecraft:iron_ingot",
    "cushion": "minecraft:white_wool",
    "curtain": "minecraft:string",
    "rope": "minecraft:string",
}

TRIM_NAMES = {
    "panel": "Panelling", "blinds": "Blinds", "board": "Board Wall",
    "frame": "Frame", "trellis": "Trellis", "quoin": "Quoin",
    "banister": "Banister", "catwalk": "Catwalk", "girder": "Girder",
    "cushion": "Cushion", "curtain": "Curtain", "rope": "Rope",
}


def trim_tags(kind, soft, climbs):
    """The second copy of TrimRules.tagsFor. Diffed against the first."""
    tags = {"minecraft:mineable/axe" if soft else "minecraft:mineable/pickaxe"}
    if climbs:
        tags.add("minecraft:climbable")
    return tags


def trim_model(kind, texture):
    return {
        "parent": "block/block",
        "textures": {"all": texture, "particle": texture},
        "elements": [{"from": list(box[:3]), "to": list(box[3:]),
                      "faces": faces(texture)} for box in TRIM_BOXES[kind]],
    }


def trim_shaped(kind, material, id, count):
    grid = TRIM_PATTERNS[kind]
    key = {"M": {"item": material}}
    extra = TRIM_EXTRA.get(kind)
    if extra:
        wide = max(len(row) for row in grid)
        grid = [row.ljust(wide) for row in grid] + ["X".ljust(wide)]
        key["X"] = {"item": extra}
    return {"type": "minecraft:crafting_shaped", "category": "building",
            "pattern": grid, "key": key,
            "result": {"id": NS + ":" + id, "count": count}}


def trim_files(kinds, soft_family, materials):
    """Every fitting: model, state, item, drop, recipe and tag."""
    blocks = data = 0
    tags = {}
    for name, block, face, soft in materials:
        for kind, family, made, of, joins, climbs in kinds:
            if family == soft_family and not soft:
                continue
            id = name + "_" + kind
            if joins:
                write(os.path.join(ASSETS, "blockstates", id + ".json"),
                      pane_state(id))
                for part in ("post", "side", "side_alt", "noside", "noside_alt"):
                    write(os.path.join(ASSETS, "models/block",
                                       id + "_" + part + ".json"),
                          pane_model(part, face))
                write(os.path.join(ASSETS, "models/item", id + ".json"),
                      {"parent": "item/generated", "textures": {"layer0": face}})
            else:
                write(os.path.join(ASSETS, "models/block", id + ".json"),
                      trim_model(kind, face))
                write(os.path.join(ASSETS, "blockstates", id + ".json"),
                      blockstate(id, kind))
                write(os.path.join(ASSETS, "models/item", id + ".json"),
                      {"parent": NS + ":block/" + id})
            write(os.path.join(DATA, "loot_table/blocks", id + ".json"),
                  drop_itself(id))
            write(os.path.join(DATA, "recipe", id + ".json"),
                  trim_shaped(kind, block, id, made))
            for tag in trim_tags(kind, soft, climbs):
                tags.setdefault(tag, []).append(f"{NS}:{id}")
            blocks += 1
            data += 2
    for tag, values in sorted(tags.items()):
        namespace, path = tag.split(":", 1)
        where = os.path.join(OUT, "src/main/resources/data", namespace,
                             "tags/block", path + ".json")
        existing = []
        if os.path.isfile(where):
            with open(where, encoding="utf-8") as handle:
                existing = json.load(handle).get("values", [])
        write(where, {"replace": False, "values": sorted(set(existing) | set(values))})
        data += 1
    return blocks, data


def trim_language(entries, kinds, soft_family, materials):
    for name, _block, _face, soft in materials:
        for kind, family, _made, _of, _joins, _climbs in kinds:
            if family == soft_family and not soft:
                continue
            entries["block." + NS + "." + name + "_" + kind] = \
                MATERIAL_NAMES[name] + " " + TRIM_NAMES[kind]
    return entries


# ---------------------------------------------------------------------------
# The colour axis: sixteen dyes, and not one new texture
# ---------------------------------------------------------------------------
#
# An upholstered seat is this module's own seat with some of its boxes drawn
# in somebody's wool. Which boxes is read out of ClothRules and checked
# against the geometry above, because a shape that gains a box would go on
# being upholstered on the boxes it used to have -- which is a chair with a
# padded leg, and it renders perfectly.

CLOTH_RULES = os.path.join(MOD, "src/main/java/com/elysium/atelier/rules/ClothRules.java")


def cloths():
    """The colours, the seats and where the cushion goes."""
    source = open(CLOTH_RULES, encoding="utf-8").read()
    listed = re.search(r"COLOURS = List\.of\((.*?)\);", source, re.S)
    if not listed:
        print("the cloth rules no longer say what the colours are")
        sys.exit(1)
    colours = re.findall(r'"([a-z_]+)"', listed.group(1))
    seats = []
    for shape, boxes, padded in re.findall(
            r'new Seat\("([a-z_]+)",\s*(\d+),\s*List\.of\(([^)]*)\)\)', source):
        seats.append((shape, int(boxes),
                      [int(n) for n in re.findall(r"\d+", padded)]))
    if len(seats) != len(re.findall(r"new Seat\(", source)) - \
            len(re.findall(r'new Seat\("control"', source)):
        print("the cloth rules declare more seats than this file could read; one "
              "was wrapped onto a second line and those blocks would not exist")
        sys.exit(1)
    kinds = []
    for name, made, of in re.findall(r'new Cloth\((\w+),\s*(\d+),\s*(\d+)\)', source):
        named = re.search(r'public static final String ' + name
                          + r'\s*=\s*"([a-z_]+)";', source)
        if not named:
            print("the cloth rules name a kind this file cannot resolve: " + name)
            sys.exit(1)
        kinds.append((named.group(1), int(made), int(of)))
    if len(colours) != 16 or not seats or not kinds:
        print(f"the cloth rules read as {len(colours)} colour(s), {len(seats)} "
              f"seat(s) and {len(kinds)} kind(s) of cloth, and a crossing built "
              f"out of a short list is a crossing with holes in it")
        sys.exit(1)
    return colours, seats, kinds


#: The boxes each piece of cloth is drawn and collided as, mirroring
#: ClothRules.boxesFor.
CLOTH_BOXES = {
    "drape":  [(0, 0, 13, 16, 16, 15)],
    "canopy": [(0, 12, 0, 16, 14, 16)],
    "pillow": [(2, 0, 2, 14, 6, 14)],
    "runner": [(0, 0, 4, 16, 1, 12)],
}

CLOTH_PATTERNS = {
    "drape":  ["MM"],
    "canopy": ["MM"],
    "pillow": ["M M"],
    "runner": ["M", "M"],
}

CLOTH_EXTRA = {
    "drape": "minecraft:lead",
    "canopy": "minecraft:feather",
    "pillow": "minecraft:feather",
    "runner": "minecraft:lead",
}

CLOTH_NAMES = {
    "drape": "Drape", "canopy": "Canopy", "pillow": "Pillow", "runner": "Runner",
}

COLOUR_NAMES = {
    "white": "White", "orange": "Orange", "magenta": "Magenta",
    "light_blue": "Light Blue", "yellow": "Yellow", "lime": "Lime",
    "pink": "Pink", "gray": "Gray", "light_gray": "Light Gray", "cyan": "Cyan",
    "purple": "Purple", "blue": "Blue", "brown": "Brown", "green": "Green",
    "red": "Red", "black": "Black",
}


def upholstered_model(shape, padded, wood_face, cloth_face):
    """The seat's own boxes, with the padded ones wearing somebody's wool."""
    elements = []
    for index, box in enumerate(GEOMETRY[shape]):
        which = "#cloth" if index in padded else "#wood"
        elements.append({
            "from": list(box[:3]), "to": list(box[3:]),
            "faces": {side: {"texture": which} for side in
                      ("down", "up", "north", "south", "west", "east")},
        })
    return {"parent": "block/block",
            "textures": {"wood": wood_face, "cloth": cloth_face,
                         "particle": cloth_face},
            "elements": elements}


def cloth_model(kind, texture):
    return {
        "parent": "block/block",
        "textures": {"all": texture, "particle": texture},
        "elements": [{"from": list(box[:3]), "to": list(box[3:]),
                      "faces": faces(texture)} for box in CLOTH_BOXES[kind]],
    }


def cloth_files(colours, seats, kinds, materials):
    """Every upholstered seat and every piece of cloth."""
    blocks = data = 0
    tags = []
    woods = [(name, block, face) for name, block, face, soft in materials if soft]
    for colour in colours:
        wool = "minecraft:" + colour + "_wool"
        wool_face = "minecraft:block/" + colour + "_wool"
        for name, block, face in woods:
            for shape, boxes, padded in seats:
                if len(GEOMETRY[shape]) != boxes:
                    print(f"{shape} is drawn as {len(GEOMETRY[shape])} boxes here "
                          f"and upholstered as {boxes} in the rules; the cushion "
                          f"would be on whichever box that number lands on")
                    sys.exit(1)
                id = f"{colour}_{name}_{shape}"
                write(os.path.join(ASSETS, "models/block", id + ".json"),
                      upholstered_model(shape, padded, face, wool_face))
                write(os.path.join(ASSETS, "blockstates", id + ".json"),
                      blockstate(id, shape))
                write(os.path.join(ASSETS, "models/item", id + ".json"),
                      {"parent": NS + ":block/" + id})
                write(os.path.join(DATA, "loot_table/blocks", id + ".json"),
                      drop_itself(id))
                # Shapeless, and the only recipe in this module that is: a
                # seat and a block of wool, which is exactly what it costs.
                write(os.path.join(DATA, "recipe", id + ".json"), {
                    "type": "minecraft:crafting_shapeless",
                    "category": "building",
                    "ingredients": [{"item": f"{NS}:{name}_{shape}"},
                                    {"item": wool}],
                    "result": {"id": f"{NS}:{id}", "count": 1},
                })
                tags.append(f"{NS}:{id}")
                blocks += 1
                data += 2
        for kind, made, of in kinds:
            id = f"{colour}_{kind}"
            write(os.path.join(ASSETS, "models/block", id + ".json"),
                  cloth_model(kind, wool_face))
            write(os.path.join(ASSETS, "blockstates", id + ".json"),
                  blockstate(id, kind))
            write(os.path.join(ASSETS, "models/item", id + ".json"),
                  {"parent": NS + ":block/" + id})
            write(os.path.join(DATA, "loot_table/blocks", id + ".json"),
                  drop_itself(id))
            grid = CLOTH_PATTERNS[kind]
            key = {"M": {"item": wool}}
            extra = CLOTH_EXTRA.get(kind)
            if extra:
                wide = max(len(row) for row in grid)
                grid = [row.ljust(wide) for row in grid] + ["X".ljust(wide)]
                key["X"] = {"item": extra}
            write(os.path.join(DATA, "recipe", id + ".json"), {
                "type": "minecraft:crafting_shaped", "category": "building",
                "pattern": grid, "key": key,
                "result": {"id": f"{NS}:{id}", "count": made},
            })
            tags.append(f"{NS}:{id}")
            blocks += 1
            data += 2
    where = os.path.join(OUT, "src/main/resources/data/minecraft",
                         "tags/block/mineable/axe.json")
    existing = []
    if os.path.isfile(where):
        with open(where, encoding="utf-8") as handle:
            existing = json.load(handle).get("values", [])
    write(where, {"replace": False, "values": sorted(set(existing) | set(tags))})
    data += 1
    return blocks, data


def cloth_language(entries, colours, seats, kinds, materials):
    woods = [name for name, _b, _f, soft in materials if soft]
    for colour in colours:
        for name in woods:
            for shape, _boxes, _padded in seats:
                entries[f"block.{NS}.{colour}_{name}_{shape}"] = \
                    f"{COLOUR_NAMES[colour]} {MATERIAL_NAMES[name]} " \
                    f"{SHAPE_NAMES[shape]}"
        for kind, _made, _of in kinds:
            entries[f"block.{NS}.{colour}_{kind}"] = \
                f"{COLOUR_NAMES[colour]} {CLOTH_NAMES[kind]}"
    return entries


# ---------------------------------------------------------------------------
# The forty-five degrees
# ---------------------------------------------------------------------------
#
# The boxes are not written out here: a diagonal is a staircase of overlapping
# squares and writing sixteen of them by hand is sixteen chances to leave a
# gap. They are built the same way the rules build them, out of the one number
# that decides them -- how thick the run is -- and validate.py diffs the
# result against what the harness printed out of the Java.

DIAGONAL_RULES = os.path.join(
    MOD, "src/main/java/com/elysium/atelier/rules/DiagonalRules.java")


def diagonals():
    """The diagonals, and how thick each run is."""
    source = open(DIAGONAL_RULES, encoding="utf-8").read()
    named = dict(re.findall(
        r'public static final String (\w+)\s*=\s*"([a-z_]+)";', source))
    kinds = []
    for name, wide, made, of in re.findall(
            r'new Kind\((\w+),\s*(\d+),\s*(\d+),\s*(\d+)\)', source):
        if name not in named:
            print("the diagonal rules name a kind this file cannot resolve: " + name)
            sys.exit(1)
        kinds.append((named[name], int(wide), int(made), int(of)))
    declared = len(re.findall(r"new Kind\(", source))
    if len(kinds) != declared:
        print("the diagonal rules declare " + str(declared) + " diagonals and this "
              "file could read " + str(len(kinds)) + "; one was wrapped onto a "
              "second line and those blocks would not exist")
        sys.exit(1)
    return kinds


def diagonal_boxes(wide):
    """A run of overlapping squares from one corner of the block to the other."""
    step = wide - 1
    boxes = []
    at = 0
    while at < 16:
        to = min(at + wide, 16)
        boxes.append((at, 0, at, to, 16, to))
        at += step
    return boxes


#: Two rows each, never three: every one of these takes a second ingredient,
#: which is written in a row of its own underneath, and the game's grid is
#: three by three. A four-row pattern is a recipe the game refuses to load and
#: the block behind it is one nobody can ever make.
DIAGONAL_PATTERNS = {
    "diagonal_pane":  ["M  ", " MM"],
    "diagonal_fence": ["MM ", "  M"],
    "diagonal_wall":  ["M M", "  M"],
}

DIAGONAL_EXTRA = {
    "diagonal_fence": "minecraft:stick",
    "diagonal_wall": "minecraft:flint",
    "diagonal_pane": "minecraft:glass",
}

DIAGONAL_NAMES = {
    "diagonal_pane": "Diagonal Pane", "diagonal_fence": "Diagonal Fence",
    "diagonal_wall": "Diagonal Wall",
}


def diagonal_tags(kind, soft):
    """The second copy of DiagonalRules.tagsFor. Diffed against the first."""
    tags = {"minecraft:mineable/axe" if soft else "minecraft:mineable/pickaxe"}
    if kind == "diagonal_fence":
        tags.add("minecraft:fences")
        if soft:
            tags.add("minecraft:wooden_fences")
    if kind == "diagonal_wall":
        tags.add("minecraft:walls")
    return tags


def diagonal_model(wide, texture):
    return {
        "parent": "block/block",
        "textures": {"all": texture, "particle": texture},
        "elements": [{"from": list(box[:3]), "to": list(box[3:]),
                      "faces": faces(texture)} for box in diagonal_boxes(wide)],
    }


def diagonal_shaped(kind, material, id, count):
    grid = DIAGONAL_PATTERNS[kind]
    key = {"M": {"item": material}}
    extra = DIAGONAL_EXTRA.get(kind)
    if extra:
        wide = max(len(row) for row in grid)
        grid = [row.ljust(wide) for row in grid] + ["X".ljust(wide)]
        key["X"] = {"item": extra}
    return {"type": "minecraft:crafting_shaped", "category": "building",
            "pattern": grid, "key": key,
            "result": {"id": NS + ":" + id, "count": count}}


def diagonal_files(kinds, materials):
    blocks = data = 0
    tags = {}
    for name, block, face, soft in materials:
        for kind, wide, made, of in kinds:
            id = name + "_" + kind
            write(os.path.join(ASSETS, "models/block", id + ".json"),
                  diagonal_model(wide, face))
            write(os.path.join(ASSETS, "blockstates", id + ".json"),
                  blockstate(id, kind))
            write(os.path.join(ASSETS, "models/item", id + ".json"),
                  {"parent": NS + ":block/" + id})
            write(os.path.join(DATA, "loot_table/blocks", id + ".json"),
                  drop_itself(id))
            write(os.path.join(DATA, "recipe", id + ".json"),
                  diagonal_shaped(kind, block, id, made))
            for tag in diagonal_tags(kind, soft):
                tags.setdefault(tag, []).append(f"{NS}:{id}")
            blocks += 1
            data += 2
    for tag, values in sorted(tags.items()):
        namespace, path = tag.split(":", 1)
        where = os.path.join(OUT, "src/main/resources/data", namespace,
                             "tags/block", path + ".json")
        existing = []
        if os.path.isfile(where):
            with open(where, encoding="utf-8") as handle:
                existing = json.load(handle).get("values", [])
        write(where, {"replace": False, "values": sorted(set(existing) | set(values))})
        data += 1
    return blocks, data


def diagonal_language(entries, kinds, materials):
    for name, _block, _face, _soft in materials:
        for kind, _wide, _made, _of in kinds:
            entries["block." + NS + "." + name + "_" + kind] = \
                MATERIAL_NAMES[name] + " " + DIAGONAL_NAMES[kind]
    return entries


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
    # The pieces are read and checked the same way, and their grids go in the
    # same collision check as the shapes': two blocks on one grid are one
    # recipe whether or not they are in the same family.
    read = pieces()
    unknown = [p[0] for p in read
               if p[0] not in ARCH_PATTERNS or p[0] not in PIECE_NAMES]
    stray = [name for name in ARCH_PATTERNS if name not in {p[0] for p in read}]
    if unknown or stray:
        print(f"the arch rules and this file disagree: {unknown} have no grid or "
              f"name here, and {stray} are a grid for nothing")
        sys.exit(1)
    for piece in read:
        used = sum(row.count("M") for row in ARCH_PATTERNS[piece[0]])
        if used != piece[6]:
            print(f"{piece[0]}'s grid uses {used} material blocks and the rules "
                  f"price it at {piece[6]}; the price and the recipe would disagree")
            sys.exit(1)

    # And the courses, read and checked exactly the same way. A kind with no
    # geometry here is a block drawn as nothing; a geometry here for no kind is
    # a shape that used to be a block.
    cut, already = courses()
    names = {kind for kind, *_ in cut}
    nogeom = [kind for kind in names
              if kind not in COURSE_BOXES or kind not in COURSE_NAMES
              or (kind not in COURSE_PATTERNS and kind not in STONECUT)]
    orphan = [kind for kind in COURSE_BOXES if kind not in names] \
        + [kind for kind in COURSE_PATTERNS if kind not in names] \
        + [kind for kind in COURSE_EXTRA if kind not in names] \
        + [kind for kind in COURSE_PATTERNS if kind in STONECUT]
    if nogeom or orphan:
        print(f"the course rules and this file disagree: {nogeom} have no shape, "
              f"grid or name here, and {orphan} are a shape for nothing or a grid "
              f"for something the stonecutter makes")
        sys.exit(1)
    for kind, _family, _role, _made, of, _joins in cut:
        if kind in STONECUT:
            if of != 1:
                print(f"{kind} is cut on a stonecutter out of {of} blocks, and a "
                      f"stonecutter takes one: the game would load the recipe, "
                      f"ignore the rest and hand out the difference")
                sys.exit(1)
            continue
        used = sum(row.count("M") for row in COURSE_PATTERNS[kind])
        if used != of:
            print(f"{kind}'s grid uses {used} material blocks and the rules price "
                  f"it at {of}; the price and the recipe would disagree")
            sys.exit(1)
    for kind in names:
        if not already.get(kind) and kind in ("stair", "slab", "wall"):
            print(f"the game is said to have no {kind} of any material at all, "
                  f"which would add a second oak stair to a game that has one")
            sys.exit(1)

    # And the fittings, read and checked the same way again.
    fitted, soft_family = fittings()
    fitted_names = {kind for kind, *_ in fitted}
    nofit = [kind for kind in fitted_names
             if kind not in TRIM_BOXES or kind not in TRIM_PATTERNS
             or kind not in TRIM_NAMES]
    strayfit = [kind for kind in TRIM_BOXES if kind not in fitted_names] \
        + [kind for kind in TRIM_PATTERNS if kind not in fitted_names] \
        + [kind for kind in TRIM_EXTRA if kind not in fitted_names]
    if nofit or strayfit:
        print(f"the trim rules and this file disagree: {nofit} have no shape, grid "
              f"or name here, and {strayfit} are a shape for nothing")
        sys.exit(1)
    for kind, _family, _made, of, _joins, _climbs in fitted:
        used = sum(row.count("M") for row in TRIM_PATTERNS[kind])
        if used != of:
            print(f"{kind}'s grid uses {used} material blocks and the rules price "
                  f"it at {of}; the price and the recipe would disagree")
            sys.exit(1)
    if not any(family == soft_family for _k, family, *_ in fitted):
        print("no fitting is in the family that is only made out of something "
              "soft, so this file would put a cushion in deepslate and nothing "
              "would say so")
        sys.exit(1)

    # And the colours, whose crossing is checked against the geometry above.
    colours, seats, cloth_kinds = cloths()
    cloth_names = {kind for kind, _m, _o in cloth_kinds}
    nocloth = [kind for kind in cloth_names
               if kind not in CLOTH_BOXES or kind not in CLOTH_PATTERNS
               or kind not in CLOTH_NAMES]
    straycloth = [kind for kind in CLOTH_BOXES if kind not in cloth_names] \
        + [kind for kind in CLOTH_PATTERNS if kind not in cloth_names] \
        + [kind for kind in CLOTH_EXTRA if kind not in cloth_names]
    unpainted = [colour for colour in colours if colour not in COLOUR_NAMES]
    if nocloth or straycloth or unpainted:
        print(f"the cloth rules and this file disagree: {nocloth} have no shape, "
              f"grid or name here, {straycloth} are a shape for nothing, and "
              f"{unpainted} have no name")
        sys.exit(1)
    for kind, _made, of in cloth_kinds:
        used = sum(row.count("M") for row in CLOTH_PATTERNS[kind])
        if used != of:
            print(f"{kind}'s grid uses {used} blocks of wool and the rules price "
                  f"it at {of}; the price and the recipe would disagree")
            sys.exit(1)
    for shape, boxes, padded in seats:
        if shape not in GEOMETRY:
            print(f"{shape} is upholstered and this file has no geometry for it")
            sys.exit(1)
        if len(GEOMETRY[shape]) != boxes:
            print(f"{shape} is drawn as {len(GEOMETRY[shape])} boxes here and "
                  f"upholstered as {boxes} in the rules; the cushion would be on "
                  f"whichever box that number lands on")
            sys.exit(1)

    # And the diagonals, whose boxes are built rather than written out.
    slanted = diagonals()
    slanted_names = {kind for kind, *_ in slanted}
    noslant = [kind for kind in slanted_names
               if kind not in DIAGONAL_PATTERNS or kind not in DIAGONAL_NAMES]
    strayslant = [kind for kind in DIAGONAL_PATTERNS if kind not in slanted_names] \
        + [kind for kind in DIAGONAL_EXTRA if kind not in slanted_names]
    if noslant or strayslant:
        print(f"the diagonal rules and this file disagree: {noslant} have no grid "
              f"or name here, and {strayslant} are a grid for nothing")
        sys.exit(1)
    for kind, wide, _made, of in slanted:
        used = sum(row.count("M") for row in DIAGONAL_PATTERNS[kind])
        if used != of:
            print(f"{kind}'s grid uses {used} material blocks and the rules price "
                  f"it at {of}; the price and the recipe would disagree")
            sys.exit(1)
        run = diagonal_boxes(wide)
        for before, now in zip(run, run[1:]):
            if not (now[0] < before[3] and now[2] < before[5]):
                print(f"{kind} is drawn as squares that meet only at their "
                      f"corners, which the game does not count as meeting: a mob "
                      f"walks through the join and it looks like a rail")
                sys.exit(1)
        if run[0][0] != 0 or run[-1][3] != 16:
            print(f"{kind} does not run from one corner of its block to the other, "
                  f"so a line of them is a dotted line")
            sys.exit(1)

    # The trimmer, proved on two pairs before anything relies on it. One it
    # must call equal and one it must not, because it can fail in both
    # directions and each failure hides a different bug: a trimmer that has
    # stopped trimming makes every collision below pass by comparing padding,
    # and one that trims too hard calls a lattice and a crate the same recipe.
    if grid_key(["MMM", "   "]) != grid_key(["MMM"]):
        print("the grid trimmer no longer treats a padded grid as the grid it "
              "pads, so every recipe collision below would be missed")
        sys.exit(1)
    if grid_key(["M M", "M M"]) == grid_key(["MM", "MM"]):
        print("the grid trimmer takes out the middle column, so a lattice and a "
              "crate look like one recipe when the game says they are two")
        sys.exit(1)

    # The game's crafting grid is three by three. A pattern that fits in it on
    # its own and does not once a second ingredient is written underneath is a
    # recipe the game refuses to load, and the block behind it is one nobody
    # can ever make -- which nothing in this file noticed until the load test
    # read the data the way the game reads it.
    for shape, grid, extra in (
            [(n, g, ARCH_EXTRA.get(n)) for n, g in PATTERNS.items()]
            + [(n, g, ARCH_EXTRA.get(n)) for n, g in ARCH_PATTERNS.items()]
            + [(n, g, COURSE_EXTRA.get(n)) for n, g in COURSE_PATTERNS.items()]
            + [(n, g, TRIM_EXTRA.get(n)) for n, g in TRIM_PATTERNS.items()]
            + [(n, g, CLOTH_EXTRA.get(n)) for n, g in CLOTH_PATTERNS.items()]
            + [(n, g, DIAGONAL_EXTRA.get(n)) for n, g in DIAGONAL_PATTERNS.items()]):
        rows = len(grid) + (1 if extra else 0)
        columns = max(len(row) for row in grid)
        if rows > 3 or columns > 3:
            print(f"{shape} is laid out on a {rows} by {columns} grid and the "
                  f"game's grid is three by three; the game refuses to load the "
                  f"recipe and the block is one nobody can ever make")
            sys.exit(1)

    laid = {}
    for shape in (list(PATTERNS) + list(ARCH_PATTERNS)
                  + list(COURSE_PATTERNS) + list(TRIM_PATTERNS)
                  + list(CLOTH_PATTERNS) + list(DIAGONAL_PATTERNS)):
        # Under mirroring, because the game matches a shaped recipe both ways
        # round: two shapes with mirror-image patterns are one recipe, and
        # which of them you get is whichever the recipe manager listed first.
        grid = PATTERNS.get(shape) or ARCH_PATTERNS.get(shape) \
            or COURSE_PATTERNS.get(shape) or TRIM_PATTERNS.get(shape) \
            or CLOTH_PATTERNS.get(shape) or DIAGONAL_PATTERNS[shape]
        key = (grid_key(grid), ARCH_EXTRA.get(shape) or COURSE_EXTRA.get(shape)
               or TRIM_EXTRA.get(shape) or CLOTH_EXTRA.get(shape)
               or DIAGONAL_EXTRA.get(shape))
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
    built, more = arch_files(read, materials)
    data += more
    # The courses go last, because they merge into the tag files everything
    # above has already written into.
    laidup, more = course_files(cut, already, materials)
    data += more
    fittings_made, more = trim_files(fitted, soft_family, materials)
    data += more
    coloured, more = cloth_files(colours, seats, cloth_kinds, materials)
    data += more
    slants, more = diagonal_files(slanted, materials)
    data += more
    keys = language(shapes, materials, read, cut, already, fitted, soft_family,
                    (colours, seats, cloth_kinds), slanted)
    print(f"atelier: {len(shapes)} shape(s) x {len(materials)} material(s) = {made} "
          f"block(s) modelled and stated, {len(openings(materials))} joining block(s) "
          f"in {joined} more file(s), {built} architectural block(s), "
          f"{laidup} course(s), {fittings_made} fitting(s), {coloured} coloured "
          f"block(s), {slants} diagonal(s), {data} data file(s), "
          f"{keys} language key(s)")
