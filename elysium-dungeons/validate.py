#!/usr/bin/env python3
"""
Cross-check Elysium Dungeons' resources against its sources.

Run from the repo root:   python3 validate.py

Smaller than core's audit because this mod ships three items and no gear tree.
What it does check is the handful of things that would leave a player stuck in
a void dimension with no way home — every one of which is a missing or
misspelled file rather than a compile error, and so invisible until someone
walks through a portal.
"""
import json
import os
import re
import sys

WORK = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(WORK, "src/main/resources")
SRC = os.path.join(WORK, "src/main/java/com/elysium/dungeons")

problems = []


def fail(msg):
    problems.append(msg)


def exists(rel):
    return os.path.exists(os.path.join(RES, rel))


def read(rel):
    return open(os.path.join(SRC, rel), encoding="utf-8").read()


if not os.path.isdir(SRC):
    print("This is not the elysium-dungeons tree: "
          "src/main/java/com/elysium/dungeons is missing.")
    sys.exit(1)

# ---------------------------------------------------------------------------
# 1. The dimension exists, and the Java points at the same id
# ---------------------------------------------------------------------------
#
# The single worst failure this mod has. A player steps into a portal, the
# dimension is not there, and without the id matching on both sides they are
# either dropped nowhere or the rift silently does nothing. Both files are
# checked and so is the id the Java asks for.

for path in ("data/elysiumdungeons/dimension/dungeon.json",
             "data/elysiumdungeons/dimension_type/dungeon.json"):
    if not exists(path):
        fail(f"missing {path} - the dungeon dimension would not exist and no rift could "
             f"lead anywhere")

if exists("data/elysiumdungeons/dimension/dungeon.json"):
    dimension = json.load(open(os.path.join(
        RES, "data/elysiumdungeons/dimension/dungeon.json"), encoding="utf-8"))
    declared_type = dimension.get("type")
    if declared_type != "elysiumdungeons:dungeon":
        fail(f"the dimension declares type {declared_type!r}, which does not match the "
             f"dimension_type this mod ships")
    generator = (dimension.get("generator") or {}).get("type")
    if generator != "minecraft:flat":
        fail(f"the dungeon generator is {generator!r}. It must be a flat void: dungeons are "
             f"written into empty chunks, and terrain there would embed every room in stone")
    settings = (dimension.get("generator") or {}).get("settings") or {}
    if settings.get("layers"):
        fail("the dungeon dimension generates layers. It must be empty void, or every "
             "dungeon is built inside a floor that already exists")

main_source = read("ElysiumDungeons.java")
level_id = re.search(r'ResourceLocation\.fromNamespaceAndPath\(MODID, "(\w+)"\)', main_source)
if not level_id:
    fail("could not read the dungeon dimension id out of ElysiumDungeons.java")
elif level_id.group(1) != "dungeon":
    fail(f"the Java looks for dimension {level_id.group(1)!r} but the data file is "
         f"dungeon.json - a rift would lead nowhere")
else:
    print("dimension           : declared, void, and the id matches the Java")

# ---------------------------------------------------------------------------
# 2. Every registered block and item has a model, a texture and a name
# ---------------------------------------------------------------------------
blocks = re.findall(r'BLOCKS\.register\("(\w+)"', main_source)
items = re.findall(r'(?<!\.)\b(?:register|ITEMS\.register)\(\s*"(\w+)"', main_source)
items = sorted(set(items))

lang_path = os.path.join(RES, "assets/elysiumdungeons/lang/en_us.json")
lang = json.load(open(lang_path, encoding="utf-8"))

for block in blocks:
    if not exists(f"assets/elysiumdungeons/blockstates/{block}.json"):
        fail(f"missing blockstate: {block}")
    if f"block.elysiumdungeons.{block}" not in lang:
        fail(f"missing lang key: block.elysiumdungeons.{block}")

for item in items:
    if not exists(f"assets/elysiumdungeons/models/item/{item}.json"):
        fail(f"missing item model: {item}")
    key = f"item.elysiumdungeons.{item}"
    if key not in lang:
        fail(f"missing lang key: {key}")

# Every model's texture reference resolves.
model_root = os.path.join(RES, "assets/elysiumdungeons/models")
for dirpath, _, filenames in os.walk(model_root):
    for filename in filenames:
        path = os.path.join(dirpath, filename)
        model = json.load(open(path, encoding="utf-8"))
        for _slot, ref in (model.get("textures") or {}).items():
            if not ref.startswith("elysiumdungeons:"):
                continue
            rel = "assets/elysiumdungeons/textures/" + ref.split(":", 1)[1] + ".png"
            if not exists(rel):
                fail(f"{os.path.relpath(path, RES)}: texture not found -> {ref}")
        parent = model.get("parent", "")
        if parent.startswith("elysiumdungeons:"):
            rel = "assets/elysiumdungeons/models/" + parent.split(":", 1)[1] + ".json"
            if not exists(rel):
                fail(f"{os.path.relpath(path, RES)}: parent model not found -> {parent}")

print(f"blocks registered   : {len(blocks)}")
print(f"items registered    : {len(items)}")

# ---------------------------------------------------------------------------
# 3. The blockstate covers every value of every property
# ---------------------------------------------------------------------------
#
# A missing variant is a missing model, which vanilla renders as the purple and
# black cube - and for the portal that means an invisible-ish wall the player
# walks into with no idea what it is.
if exists("assets/elysiumdungeons/blockstates/rift_portal.json"):
    variants = json.load(open(os.path.join(
        RES, "assets/elysiumdungeons/blockstates/rift_portal.json"),
        encoding="utf-8")).get("variants", {})
    for axis in ("axis=x", "axis=z"):
        if axis not in variants:
            fail(f"rift_portal blockstate has no {axis} variant")

# ---------------------------------------------------------------------------
# 4. Recipes and loot tables reference things that exist
# ---------------------------------------------------------------------------
known = {f"elysiumdungeons:{name}" for name in set(items) | set(blocks)}
VANILLA_OK = re.compile(r"^(minecraft|#minecraft|c|#c):")

for folder in ("data/elysiumdungeons/recipe", "data/elysiumdungeons/loot_table/blocks"):
    full = os.path.join(RES, folder)
    if not os.path.isdir(full):
        continue
    for filename in os.listdir(full):
        text = open(os.path.join(full, filename), encoding="utf-8").read()
        for ref in re.findall(r'"(elysiumdungeons:[a-z_/]+)"', text):
            if ref not in known:
                fail(f"{folder}/{filename}: references unknown item -> {ref}")
        for ref in re.findall(r'"((?:minecraft|c):[a-z_/]+)"', text):
            if not VANILLA_OK.match(ref):
                fail(f"{folder}/{filename}: suspicious reference -> {ref}")

# --- ingredients are objects, not bare ids ----------------------------------
#
# An Ingredient in 1.21 is {"item": id} or {"tag": id}, or an array of those.
# A bare id string was legal through 1.20, parses as neither, and takes the
# whole recipe down without a crash - both of this mod's recipes were doing it,
# so the rift key could not be crafted and the only trace was one line in the
# client log. Cheap to check, invisible otherwise.
def check_ingredient(value, where, what):
    if isinstance(value, list):
        for each in value:
            check_ingredient(each, where, what)
        return
    if isinstance(value, str):
        fail(f"{where}: {what} is the bare id {value!r} - in 1.21 an Ingredient is "
             f"an object like {{\"item\": \"{value}\"}}, and a string silently drops "
             f"the recipe")
        return
    if not isinstance(value, dict) or not ({"item", "tag"} & set(value)):
        fail(f"{where}: {what} is not an Ingredient object with an \"item\" or "
             f"\"tag\" key")


_recipe_dir = os.path.join(RES, "data/elysiumdungeons/recipe")
if os.path.isdir(_recipe_dir):
    for filename in sorted(os.listdir(_recipe_dir)):
        if not filename.endswith(".json"):
            continue
        recipe = json.load(open(os.path.join(_recipe_dir, filename), encoding="utf-8"))
        where = f"data/elysiumdungeons/recipe/{filename}"
        for symbol, value in (recipe.get("key") or {}).items():
            check_ingredient(value, where, f"key {symbol!r}")
        for index, value in enumerate(recipe.get("ingredients") or []):
            check_ingredient(value, where, f"ingredient {index}")

# Both craftable items need a recipe, or nothing in this mod is obtainable in
# survival - there is no worldgen and no mob drop to fall back on.
for item in items:
    if not exists(f"data/elysiumdungeons/recipe/{item}.json"):
        fail(f"{item} has no recipe, and this mod has no other survival source")

# The frame is mined, so it needs a loot table and a tool tag or it drops
# nothing and a portal can never be dismantled.
if "rift_frame" in blocks:
    if not exists("data/elysiumdungeons/loot_table/blocks/rift_frame.json"):
        fail("rift_frame has no loot table - mining it would destroy it")
    pickaxe = os.path.join(RES, "data/minecraft/tags/block/mineable/pickaxe.json")
    if not os.path.exists(pickaxe):
        fail("no pickaxe mineable tag - rift_frame requires a tool and would drop nothing")
    else:
        values = json.load(open(pickaxe, encoding="utf-8")).get("values", [])
        if "elysiumdungeons:rift_frame" not in values:
            fail("rift_frame is not in the pickaxe mineable tag")

# ---------------------------------------------------------------------------
# 5. The library dependency is declared
# ---------------------------------------------------------------------------
manifest = open(os.path.join(RES, "META-INF/neoforge.mods.toml"), encoding="utf-8").read()
if 'modId="elysiumlib"' not in manifest:
    fail("neoforge.mods.toml does not declare elysiumlib - loot rooms call into it, so a "
         "missing library is a NoClassDefFoundError rather than a clear line on the mods screen")
if 'modId="elysium"' in manifest and 'type="required"' in manifest.split('modId="elysium"')[1][:120]:
    fail("elysium-core is declared required. It supplies reward items and nothing this mod "
         "cannot run without; requiring it makes dungeons unavailable to anyone not playing core")

# ---------------------------------------------------------------------------
# 6. Room registrations line up with the layout's kinds
# ---------------------------------------------------------------------------
#
# A layout kind with no room registered generates empty boxes and one warning
# per room. It is legal, and it is never what anyone meant.
rooms_source = read("room/DungeonRooms.java")
registered_kinds = set(re.findall(r"\.kind\(DungeonLayout\.Kind\.(\w+)\)", rooms_source))
# Rooms with no explicit kind are fillers, by the builder's default.
if re.search(r"DungeonRoom\.builder\((?:.|\n)*?\.weight\(", rooms_source):
    registered_kinds.add("FILLER")

layout_source = read("room/DungeonLayout.java")
kinds = set(re.findall(r"^\s{8}(\w+),?$", layout_source, re.M))
kinds = {k for k in kinds if k.isupper()}
for kind in kinds:
    if kind not in registered_kinds:
        fail(f"no room is registered for layout kind {kind} - every {kind} room in every "
             f"dungeon would generate as an empty box")

filler_count = len(re.findall(r"DungeonRoom\.builder", rooms_source))
print(f"room types          : {filler_count} registered, kinds covered: "
      f"{', '.join(sorted(registered_kinds))}")

# ---------------------------------------------------------------------------
# 7. All JSON parses
# ---------------------------------------------------------------------------
count = 0
for dirpath, _, filenames in os.walk(RES):
    for filename in filenames:
        if filename.endswith(".json"):
            count += 1
            try:
                json.load(open(os.path.join(dirpath, filename), encoding="utf-8"))
            except Exception as exc:
                fail(f"invalid JSON {os.path.join(dirpath, filename)}: {exc}")
print(f"json files parsed   : {count}")
print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all dungeon checks passed")
