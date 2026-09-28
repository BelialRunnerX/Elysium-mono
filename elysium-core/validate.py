#!/usr/bin/env python3
"""
Cross-check the registry declared in Java against the shipped resources.

Run from the repo root:   python3 validate.py

Fails if a registered item has no model, a model points at a texture that does
not exist, a recipe or loot table references an unregistered item, a shaped
pattern disagrees with its declared keys, a placed feature is unreachable from
any biome modifier, or a data pack folder is still using its pre-1.21 name.
""" 
import json
import os
import pathlib
import re
import sys

WORK = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(WORK, "src/main/resources")
JAVA = os.path.join(WORK, "src/main/java/com/elysium/core/Elysium.java")

problems = []
notes = []


def fail(msg):
    problems.append(msg)


def exists(rel):
    return os.path.exists(os.path.join(RES, rel))


# ---------------------------------------------------------------------------
# Is this actually the packaged tree?
#
# Checked first, and fatal, because the alternative is what happened the first
# time this ran in CI: the pre-port sources were pushed with this script laid
# on top, and it correctly emitted 106 complaints about a project nobody was
# trying to build. A hundred accurate errors about the wrong tree read exactly
# like one broken build, and cost more to interpret than they are worth.
#
# Every marker below is something the port removed. If any survives, the
# checkout is a mixture of old and new files and nothing downstream means
# anything.
# ---------------------------------------------------------------------------
LEGACY_MARKERS = [
    ("src/main/resources/data/elysium/recipes", "pre-1.21 recipe folder (now 'recipe')"),
    ("src/main/resources/data/elysium/loot_tables", "pre-1.21 loot table folder (now 'loot_table')"),
    ("src/main/resources/data/minecraft/tags/blocks", "pre-1.21 tag folder (now 'tags/block')"),
    ("libs", "the Parchment zip the port deleted"),
]

stale = [(path, why) for path, why in LEGACY_MARKERS
         if os.path.exists(os.path.join(WORK, path))]

if stale:
    print("This is not the packaged Elysium tree.\n")
    print("Found files the port removed:\n")
    for path, why in stale:
        print(f"  {path}\n      {why}")
    print("""
The most likely cause is extracting the release zip into a folder that still
held the original project, so old and new files are mixed together. Git keeps
whatever was already tracked, and the result compiles as neither version.

To fix it, replace the contents rather than merging into them:

    mkdir elysium-clean && cd elysium-clean
    unzip /path/to/elysium-mod.zip
    git init && git add . && git commit -m "Elysium"
    git branch -M main
    git remote add origin <your repo url>
    git push -u origin main --force

Nothing else is checked until this is resolved - every later result would be
about the wrong files.""")
    sys.exit(1)

source = open(JAVA, encoding="utf-8").read()

blocks = re.findall(r'BLOCKS\.register\("([a-z_]+)"', source)

# Items are registered through a family of small helpers rather than one call,
# so match the name literal wherever it is handed to one of them.
# The leading (?<!\.) keeps qualified calls out - CREATIVE_TABS.register and
# BLOCKS.register are not item registrations.
ITEM_HELPERS = (r'(?<!\.)\b(?:register|item|simpleItem|armour|neutroniumArmour|weapon|tool'
                r'|ration|sigil)')
items = re.findall(ITEM_HELPERS + r'\(\s*"([a-z_]+)"', source)

# blockItem() derives the item name from the block's registry name — but not
# every block gets one.
#
# This used to assume they all did, and simply unioned the block names into the
# item names. That is true of seven of the eight blocks here and false of the
# inspection beacon, which is Imperial equipment: it is planted by the Empire,
# has no recipe, drops nothing, and a player holding a stack of them would have
# a supply of a block whose only function is to tax them. Assuming an item
# produced two complaints about a block that is working exactly as designed.
#
# So the block items are read from the blockItem() calls that actually create
# them, via the holder constant each block is assigned to. A block with no
# blockItem() line is then genuinely itemless — and because forgetting that line
# is a real mistake with the same signature as making the choice deliberately,
# the itemless ones must be named below.
block_constants = dict(re.findall(
    r'DeferredHolder<Block, Block>\s+(\w+)\s*=\s*\n?\s*BLOCKS\.register\("([a-z_]+)"',
    source))
# Matches both blockItem(BLOCK) and the described form, which takes tooltip
# keys after the block and wraps across lines:
#
#     blockItem(REGISTRY_BEACON,
#             "elysium.tooltip.registry_beacon", ...)
#
# The narrower pattern this replaces silently stopped matching the moment two
# blocks grew tooltips, and reported both of them as unobtainable — loudly,
# which is the behaviour that made it worth fixing rather than working around.
with_items = {block_constants[const]
              for const in re.findall(r'blockItem\(\s*(\w+)', source)
              if const in block_constants}

# Blocks that deliberately have no item. Each needs a reason, and the reason
# has to be that a player is never meant to hold one.
ITEMLESS_BLOCKS = {
    "inspection_beacon": "Imperial equipment: planted by the Empire, drops "
                         "nothing, and a player who could place one could put "
                         "an Imperial sensor in somebody else's base",
}

if len(block_constants) != len(blocks):
    fail(f"read a holder constant for {len(block_constants)} of {len(blocks)} "
         f"blocks — the declaration shape has changed and the block-item "
         f"checks below are no longer checking anything")

for block in blocks:
    if block in with_items or block in ITEMLESS_BLOCKS:
        continue
    fail(f"{block} has no blockItem() and is not listed as deliberately "
         f"itemless — it would be a block nobody can obtain or place. Add the "
         f"blockItem line, or add it to ITEMLESS_BLOCKS with a reason.")

for block in ITEMLESS_BLOCKS:
    if block in with_items:
        fail(f"{block} is listed as deliberately itemless but has a "
             f"blockItem() — one of the two is wrong")

items = set(items) | with_items

# Runes are library objects registered by id, and the item that carries one is
# registered separately. Read the definitions rather than the items, so a rune
# that was defined and never given an item is caught here rather than by a
# player finding a hole in the creative tab.
RUNES = os.path.join(WORK, "src/main/java/com/elysium/core/item/ElysiumRunes.java")
rune_source = open(RUNES, encoding="utf-8").read()
rune_ids = re.findall(r'ElysiumRune\.builder\(id\("([a-z_]+)"\)\)', rune_source)
if not rune_ids:
    fail("could not read any rune ids out of ElysiumRunes - check the builder form")
items |= {rune_id + "_rune" for rune_id in rune_ids}

# Every defined rune must also have an item registered for it, by name.
for rune_id in rune_ids:
    if rune_id + "_rune" not in items:
        fail(f"rune {rune_id} is defined but no {rune_id}_rune item is registered")

# ---------------------------------------------------------------------------
# Material gear
#
# Eight items per material, registered in a loop rather than declared by name,
# so the regex above cannot see them. Read the material table instead and take
# the product — which makes this a real cross-check rather than a workaround:
# a material added to the Java table with no texture, or a texture with no
# material, now fails here instead of showing up as a purple-and-black cube.
# ---------------------------------------------------------------------------
MATERIALS_JAVA = os.path.join(
    WORK, "src/main/java/com/elysium/core/item/ElysiumMaterials.java")
material_source = open(MATERIALS_JAVA, encoding="utf-8").read()

# The shapes, read out of the enum the game registers from rather than listed
# here.
#
# This was a literal list, and the moment five shapes were added to the Java it
# was a stale second copy of the truth: the validator went on checking four
# shapes per material and reported every new recipe as referencing an item that
# does not exist — which is exactly backwards, since the items existed and the
# validator did not know about them. A list that has to be kept in step with
# code is a list that will not be.
GEAR_JAVA = os.path.join(
    WORK, "src/main/java/com/elysium/core/item/ElysiumMaterialGear.java")
_gear_source = open(GEAR_JAVA, encoding="utf-8").read()
_enum = re.search(r"public enum Shape \{(.*?)\}", _gear_source, re.S)
SHAPES = re.findall(r'\w+\("(\w+)"\)', _enum.group(1)) if _enum else []
if not SHAPES:
    fail("cannot read the Shape enum out of ElysiumMaterialGear.java, so every "
         "material gear check below would pass by checking nothing")

ARMOUR_PIECES = ["helmet", "chestplate", "leggings", "boots"]

gear_materials = []          # (name, has_armour, ingredient tag, is_vanilla)
for _match in re.finditer(
        r'ElysiumGearMaterial\.builder\(id\("(\w+)"\)\)(.*?)\.register\(\)',
        material_source, re.S):
    _name, _body = _match.group(1), _match.group(2)
    _tag = re.search(r'\.ingredient\(common\("([\w/]+)"\)\)', _body)
    gear_materials.append((_name, ".armour(" in _body,
                           "c:" + (_tag.group(1) if _tag else "ingots/" + _name),
                           ".vanilla()" in _body))
for _match in re.finditer(
        r'vanillaMaterial\(\s*"(\w+)",\s*(?:common\("([\w/]+)"\),\s*)?',
        material_source):
    _name, _tag = _match.group(1), _match.group(2)
    gear_materials.append((_name, True, "c:" + (_tag or "ingots/" + _name), True))
for _match in re.finditer(r'\{"(\w+)",\s*ElysiumElements\.\w+,', material_source):
    gear_materials.append((_match.group(1), True, "c:ingots/" + _match.group(1), False))

_names = [entry[0] for entry in gear_materials]
if len(set(_names)) != len(_names):
    _dupes = sorted({n for n in _names if _names.count(n) > 1})
    fail(f"material declared more than once in ElysiumMaterials.java: {_dupes} - "
         f"the second registration would throw at load")

MATERIAL_ITEMS = set()
# Gear whose ingredient only exists when some other mod is installed. It is
# registered in every world on purpose (see ElysiumGearMaterial), so "no
# survival source" is the wrong complaint to make about it - it is reachable
# exactly when the mod supplying the metal is present, which this checkout
# cannot know.
CONDITIONAL_ITEMS = set()
for _name, _has_armour, _tag, _is_vanilla in gear_materials:
    for _piece in SHAPES + (ARMOUR_PIECES if _has_armour else []):
        _item = f"{_name}_{_piece}"
        MATERIAL_ITEMS.add(_item)
        if not _is_vanilla:
            CONDITIONAL_ITEMS.add(_item)

# A generated name that collides with a hand-written one is a duplicate
# registration, which throws at load with a message that names the item but not
# which of the two declarations to delete. Catching it here says both.
_collisions = sorted(MATERIAL_ITEMS & set(items))
if _collisions:
    fail(f"these items are registered twice - once by name in Elysium.java and "
         f"once by ElysiumMaterialGear: {_collisions}. Give the material no "
         f"ArmourProfile, or delete the hand-written declaration.")

items = set(items) | MATERIAL_ITEMS

items = sorted(items)

# The folders can be right while the sources are not — an Elysium.java from
# before the port registers a fraction of what the resources reference, which
# again produces a wall of accurate but useless errors.
EXPECTED_MIN_ITEMS = 40
if len(items) < EXPECTED_MIN_ITEMS:
    print(f"This is not the packaged Elysium tree.\n")
    print(f"  src/main/java/com/elysium/core/Elysium.java registers {len(items)} items;")
    print(f"  the packaged version registers over {EXPECTED_MIN_ITEMS}.\n")
    print("The resources and the sources are from different versions. Extract the")
    print("release zip into an empty folder and push that, rather than merging it")
    print("into an existing checkout.")
    sys.exit(1)

print(f"blocks registered : {len(blocks)}")
print(f"items registered  : {len(items)}")

def body_of(src, signature):
    """The text between the braces of the method whose declaration starts with
    `signature`. A rule scoped to one method cannot pass because its token
    survives in an import, a comment or a different branch."""
    start = src.find(signature)
    if start < 0:
        return ""
    open_at = src.find("{", start)
    if open_at < 0:
        return ""
    depth = 0
    for i in range(open_at, len(src)):
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return src[open_at + 1:i]
    return src[open_at + 1:]

lang = json.load(open(os.path.join(RES, "assets/elysium/lang/en_us.json"), encoding="utf-8"))

# --- models, textures, lang -------------------------------------------------
for block in blocks:
    if not exists(f"assets/elysium/blockstates/{block}.json"):
        fail(f"missing blockstate: {block}")
    if not exists(f"assets/elysium/models/block/{block}.json"):
        fail(f"missing block model: {block}")
    if not exists(f"assets/elysium/textures/block/{block}.png"):
        fail(f"missing block texture: {block}")
    if f"block.elysium.{block}" not in lang:
        fail(f"missing lang key: block.elysium.{block}")
    if not exists(f"data/elysium/loot_table/blocks/{block}.json"):
        fail(f"missing loot table: {block}")

for item in items:
    if not exists(f"assets/elysium/models/item/{item}.json"):
        fail(f"missing item model: {item}")
    key = f"block.elysium.{item}" if item in blocks else f"item.elysium.{item}"
    if key not in lang:
        fail(f"missing lang key: {key}")

# --- every key the code names has something to say --------------------------
#
# A Component.translatable with no entry behind it shows the player a raw key.
# The library has checked this for a long time; this module did not, and a
# deliberate test walked straight through the gap — a sigil whose entire
# interface is one line of description read as "elysium.sigil.unknown" and
# every check here passed.
_literal_keys = set()
for _dirpath, _, _filenames in os.walk(os.path.join(WORK, "src/main/java")):
    for _filename in _filenames:
        if not _filename.endswith(".java"):
            continue
        _text = open(os.path.join(_dirpath, _filename), encoding="utf-8").read()
        _literal_keys |= set(re.findall(r'"(elysium\.[a-z_.]+)"', _text))
# The library's entries count too: both language files ship in the same jar and
# the game merges them, so a key this module names and the library translates
# is a key the player can read.
_lib_lang_path = os.path.join(WORK, "..",
                              "elysium-lib/src/main/resources/assets/elysiumlib/lang/en_us.json")
_shipped_keys = set(lang)
if os.path.isfile(_lib_lang_path):
    _shipped_keys |= set(json.load(open(_lib_lang_path, encoding="utf-8")))
for _key in sorted(_literal_keys):
    if _key not in _shipped_keys and not _key.endswith("."):
        fail(f"missing lang key: {_key} (referenced literally in the sources)")

# --- every model's texture reference resolves -------------------------------
model_root = os.path.join(RES, "assets/elysium/models")
for dirpath, _, filenames in os.walk(model_root):
    for filename in filenames:
        path = os.path.join(dirpath, filename)
        model = json.load(open(path, encoding="utf-8"))
        for slot, ref in (model.get("textures") or {}).items():
            if not ref.startswith("elysium:"):
                continue
            rel = "assets/elysium/textures/" + ref.split(":", 1)[1] + ".png"
            if not exists(rel):
                fail(f"{os.path.relpath(path, RES)}: texture not found -> {ref}")
        parent = model.get("parent", "")
        if parent.startswith("elysium:"):
            rel = "assets/elysium/models/" + parent.split(":", 1)[1] + ".json"
            if not exists(rel):
                fail(f"{os.path.relpath(path, RES)}: parent model not found -> {parent}")

# --- armour layer textures --------------------------------------------------
for material in re.findall(r'ResourceLocation\.fromNamespaceAndPath\(Elysium\.MODID, "(\w+)"\)\)\)',
                           open(os.path.join(WORK, "src/main/java/com/elysium/core/item/ElysiumArmorMaterials.java"),
                                encoding="utf-8").read()):
    for layer in (1, 2):
        rel = f"assets/elysium/textures/models/armor/{material}_layer_{layer}.png"
        if not exists(rel):
            fail(f"missing armour layer texture: {rel}")

# --- the materials config must be readable during registration --------------
#
# ElysiumMaterialConfig decides which items exist, so it is read inside the mod
# constructor, before any item is registered. Only ModConfig.Type.STARTUP is
# loaded that early; a COMMON config is not loaded until just before
# FMLCommonSetupEvent, which is after every constructor has run.
#
# Registered as COMMON, this threw "Cannot get config value before config is
# loaded" and took the whole mod down on the very first launch. Nothing about
# that is visible to the compiler — it is a lifecycle rule, not a signature —
# so it is asserted here instead.
main_class = JAVA
if os.path.exists(main_class):
    main_source = open(main_class, encoding="utf-8").read()
    registrations = re.findall(r"registerConfig\(\s*ModConfig\.Type\.(\w+)", main_source)
    if not registrations:
        fail("no registerConfig call found in Elysium.java; the materials config "
             "must be registered or extra_materials silently does nothing")
    for kind in registrations:
        if kind != "STARTUP":
            fail(f"config registered as ModConfig.Type.{kind}, but its values are read "
                 f"during mod construction to decide which items exist. Only STARTUP is "
                 f"loaded that early — COMMON throws 'Cannot get config value before "
                 f"config is loaded' and kills the mod on launch.")
    print(f"config lifecycle   : {len(registrations)} config(s), all STARTUP")

# --- no GUI textures, and that is the point ---------------------------------
#
# This used to require textures/gui/reforge_table.png. The screen no longer
# blits anything: it is drawn from ElysiumUI, so a GUI texture would be an
# unreferenced file that this check kept alive. The assertion is inverted for
# the same reason it existed — to notice when the screen and its resources
# disagree — but now the correct state is that there are none.
gui_dir = os.path.join(RES, "assets/elysium/textures/gui")
if os.path.isdir(gui_dir) and os.listdir(gui_dir):
    fail("GUI textures present in %s: the Elysium screens are drawn, not "
         "blitted, so nothing loads these" % gui_dir)

# --- recipes / loot tables only reference things that exist -----------------
known_items = {f"elysium:{name}" for name in items}
VANILLA_OK = re.compile(r"^(minecraft|#minecraft|c|#c):")


def check_id(value, where):
    if not isinstance(value, str):
        return
    if VANILLA_OK.match(value):
        return
    if value.startswith("elysium:") and value not in known_items:
        fail(f"{where}: references unknown item -> {value}")


def ingredient_ids(value):
    """
    The id strings inside an Ingredient, whatever legal shape it is in.

    An Ingredient is {"item": id}, {"tag": id}, or an array of those. Reading
    it by hand in three places is how the checks below came to read only the
    string form and then quietly stop checking anything when the strings became
    objects - a validator that inspects the wrong shape reports nothing and
    passes, which is worse than not existing.
    """
    if isinstance(value, str):
        return [value]
    if isinstance(value, list):
        return [each for item in value for each in ingredient_ids(item)]
    if isinstance(value, dict):
        found = []
        if isinstance(value.get("item"), str):
            found.append(value["item"])
        if isinstance(value.get("tag"), str):
            found.append("#" + value["tag"].lstrip("#"))
        return found
    return []


def check_ingredient(value, where, what):
    """
    Ingredients must be objects, and every id in them must exist.

    The shape half is the important half. A bare id string was a legal
    Ingredient through 1.20 and is not in 1.21.1, and when the generators
    carried the old form forward every one of the mod's 229 recipes failed to
    load with

        Map entry '#' : Failed to parse either.
        First: Not a json array: "elysium:neutronium_ingot";
        Second: Not a JSON object: "elysium:neutronium_ingot"

    Nothing crashed. The recipes simply were not there, the log scrolled past
    it during startup, and the mod shipped with no crafting at all. That is why
    this is checked here rather than trusted to a launch.
    """
    if isinstance(value, str):
        fail(f"{where}: {what} is the bare id {value!r}. In 1.21 an Ingredient is an "
             f"object - {{\"item\": \"{value}\"}} or {{\"tag\": ...}} - and a string "
             f"fails to parse, which drops the whole recipe silently")
        return
    if isinstance(value, list):
        for each in value:
            check_ingredient(each, where, what)
        return
    if not isinstance(value, dict):
        fail(f"{where}: {what} is neither an object nor an array of objects")
        return
    if not ({"item", "tag"} & set(value)):
        fail(f"{where}: {what} has neither an \"item\" nor a \"tag\" key")
    for identifier in ingredient_ids(value):
        check_id(identifier.lstrip("#"), where)


for dirpath, _, filenames in os.walk(os.path.join(RES, "data/elysium/recipe")):
    for filename in filenames:
        path = os.path.join(dirpath, filename)
        recipe = json.load(open(path, encoding="utf-8"))
        where = os.path.relpath(path, RES)
        check_id(recipe.get("result", {}).get("id"), where)
        for symbol, value in (recipe.get("key") or {}).items():
            check_ingredient(value, where, f"key {symbol!r}")
        for index, value in enumerate(recipe.get("ingredients") or []):
            check_ingredient(value, where, f"ingredient {index}")
        for name in ("ingredient", "base", "addition", "template"):
            if name in recipe:
                check_ingredient(recipe[name], where, name)
        if recipe["type"] == "minecraft:crafting_shaped":
            widths = {len(row) for row in recipe["pattern"]}
            if len(widths) != 1:
                fail(f"{where}: pattern rows have differing widths {widths}")
            used = {c for row in recipe["pattern"] for c in row if c != " "}
            declared = set(recipe["key"])
            if used - declared:
                fail(f"{where}: pattern uses undeclared key(s) {used - declared}")
            if declared - used:
                fail(f"{where}: declares unused key(s) {declared - used}")


def walk_json(node, where):
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "name" and isinstance(value, str):
                check_id(value, where)
            else:
                walk_json(value, where)
    elif isinstance(node, list):
        for value in node:
            walk_json(value, where)


for dirpath, _, filenames in os.walk(os.path.join(RES, "data/elysium/loot_table")):
    for filename in filenames:
        path = os.path.join(dirpath, filename)
        walk_json(json.load(open(path, encoding="utf-8")), os.path.relpath(path, RES))

# --- worldgen wiring --------------------------------------------------------
placed_dir = os.path.join(RES, "data/elysium/worldgen/placed_feature")
for filename in os.listdir(placed_dir):
    path = os.path.join(placed_dir, filename)
    placed = json.load(open(path, encoding="utf-8"))
    if "feature" not in placed:
        fail(f"placed_feature/{filename}: missing 'feature' field")
    else:
        target = placed["feature"].split(":", 1)[1]
        if not exists(f"data/elysium/worldgen/configured_feature/{target}.json"):
            fail(f"placed_feature/{filename}: points at missing configured feature {placed['feature']}")

bm_dir = os.path.join(RES, "data/elysium/neoforge/biome_modifier")
referenced = set()
for filename in os.listdir(bm_dir):
    modifier = json.load(open(os.path.join(bm_dir, filename), encoding="utf-8"))
    referenced.add(modifier["features"])
for filename in os.listdir(placed_dir):
    feature = "elysium:" + filename[:-5]
    if feature not in referenced:
        fail(f"placed feature {feature} is never added to a biome by any biome modifier")

# --- obtainability: every item has a path back to the world ------------------
#
# The check that matters most and is easiest to lose. An item can have a model,
# a texture, a lang key and a working class and still be unreachable in
# survival, which is exactly what happened to five armour pieces: they were
# registered, rendered, socketable and reforgeable, and there was no way to
# get one without creative mode.
#
# So: start from what the world itself gives you, then close over the recipe
# graph until nothing new appears. Anything still unreached is either a bug or
# an item that must be declared creative-only on purpose.

# Deliberately creative-only. Spawn eggs are a creative tool by definition.
CREATIVE_ONLY = {"imperial_enforcer_spawn_egg", "unsworn_raider_spawn_egg"}

# Ore blocks are reachable because they generate. Everything else has to be
# earned through the graph below.
worldgen_blocks = {name[:-5] for name in os.listdir(placed_dir)}

obtainable = set()
sources = {}


def source(name, why):
    if name not in obtainable:
        obtainable.add(name)
        sources[name] = why


# 1. Blocks that generate, and whatever their loot tables drop.
for block in sorted(worldgen_blocks):
    source(block, "worldgen")
    table = f"data/elysium/loot_table/blocks/{block}.json"
    if not exists(os.path.join(RES, table)) and not exists(table):
        fail(f"{block} generates in the world but has no loot table - it would drop nothing")
        continue
    text = open(os.path.join(RES, table), encoding="utf-8").read()
    for dropped in re.findall(r'"elysium:([a-z_]+)"', text):
        source(dropped, f"mined from {block}")

# 2. Whatever the mobs drop, by data.
entity_dir = os.path.join(RES, "data/elysium/loot_table/entities")
for filename in os.listdir(entity_dir):
    text = open(os.path.join(entity_dir, filename), encoding="utf-8").read()
    for dropped in re.findall(r'"elysium:([a-z_]+)"', text):
        source(dropped, f"dropped by {filename[:-5]}")

# 3. Whatever the mod hands out in code, from any handler that does so.
#    Listed by name rather than discovered, so adding a new giver is a
#    deliberate act and a typo in a path fails loudly instead of quietly
#    marking an item unobtainable.
# The library owns the loot and join *handlers*; this mod owns what they hand
# out. So the audit reads this mod's registrations into the library rather than
# the handlers themselves — ElysiumContent is where the reward shelves and the
# codex are declared, and the crown has its own listener.
CODE_GIVERS = [
    ("src/main/java/com/elysium/core/ElysiumContent.java", "standing loot"),
    ("src/main/java/com/elysium/core/event/ElysiumCrownDrops.java", "standing loot"),
]
handler = ""
for _path, _why in CODE_GIVERS:
    _full = os.path.join(WORK, _path)
    if not os.path.exists(_full):
        fail(f"obtainability audit points at a missing file: {_path}")
        continue
    handler += open(_full, encoding="utf-8").read()
holders = dict(re.findall(
    r'public static final DeferredHolder<Item, Item> ([A-Z0-9_]+)\s*=\s*\n?\s*'
    r'(?:register|item|simpleItem|armour|neutroniumArmour|weapon|tool|ration)\(\s*"([a-z_]+)"',
    source_java := open(JAVA, encoding="utf-8").read()))
for holder in re.findall(r'Elysium\.([A-Z0-9_]+)\.get\(\)', handler):
    if holder in holders:
        source(holders[holder], "standing loot")
if not any(why == "standing loot" for why in sources.values()):
    fail("could not read any rewards out of ElysiumContent - check the Elysium.X.get() form")

# 4. Close over the recipe graph: a recipe is reachable once every one of its
#    Elysium ingredients is. Repeat until nothing new appears, which is also
#    what catches a circular recipe - it simply never becomes reachable.
recipes = []
dynamic_recipes = {}
for filename in os.listdir(os.path.join(RES, "data/elysium/recipe")):
    recipe = json.load(open(os.path.join(RES, "data/elysium/recipe", filename), encoding="utf-8"))
    if "result" not in recipe:
        # A recipe whose output is computed from its inputs — the convergence
        # smithing recipe. It has no fixed result to reach, so it plays no part
        # in the reachability walk below and is checked in its own section.
        dynamic_recipes[filename] = recipe
        continue
    result = recipe["result"]["id"].split(":", 1)[1]
    ingredients = set()
    for value in list((recipe.get("key") or {}).values()) + list(recipe.get("ingredients") or []):
        for identifier in ingredient_ids(value):
            if identifier.startswith("elysium:"):
                ingredients.add(identifier.split(":", 1)[1])
    recipes.append((filename, result, ingredients))

# A recipe whose result is computed rather than written down still produces
# something, and the walk above cannot see what. Naming them here is not a
# second copy of the truth: each entry says which class computes the result and
# what expression in it names the item, and both are checked — so an entry that
# stops being true fails rather than quietly keeping an item "reachable".
DYNAMIC_PRODUCES = {
    "gem_grinding.json": ("gem_dust",
                          "src/main/java/com/elysium/core/recipe/GemGrindingRecipe.java",
                          "Elysium.GEM_DUST.get()"),
}
for _file, (_makes, _class, _expression) in DYNAMIC_PRODUCES.items():
    if _file not in dynamic_recipes:
        fail(f"{_file} is listed as a recipe with a computed result and is not one; "
             f"whatever it makes is being counted as reachable for no reason")
        continue
    _path = os.path.join(WORK, _class)
    if not os.path.isfile(_path):
        fail(f"{_class} is gone, so nothing computes {_file}'s result")
    elif _expression not in open(_path, encoding="utf-8").read():
        fail(f"{_class} no longer names {_expression}, so {_makes} is counted as "
             f"reachable by a recipe that does not make it")
    else:
        source(_makes, f"computed ({_file[:-5]})")

changed = True
while changed:
    changed = False
    for filename, result, ingredients in recipes:
        if result not in obtainable and ingredients <= obtainable:
            source(result, f"crafted ({filename[:-5]})")
            changed = True

unreachable = sorted(set(items) - obtainable - CREATIVE_ONLY - CONDITIONAL_ITEMS)
for name in unreachable:
    made_by = [f for f, result, _ in recipes if result == name]
    if made_by:
        blocking = set()
        for filename, result, ingredients in recipes:
            if result == name:
                blocking |= ingredients - obtainable
        fail(f"{name} is craftable but unreachable - needs {sorted(blocking)}, "
             f"which nothing in the world produces")
    else:
        fail(f"{name} has no survival source: no recipe, no loot table, no code drop")

_countable = set(items) - CREATIVE_ONLY - CONDITIONAL_ITEMS
print(f"obtainable in survival : {len(obtainable & _countable)}/{len(_countable)}"
      f"  (+{len(CREATIVE_ONLY)} creative-only by design,"
      f" +{len(CONDITIONAL_ITEMS)} gated on a modded ingredient tag)")

# --- character balance invariants -------------------------------------------
#
# These are properties the code documents about itself and cannot check at
# runtime. Both were violated in the first draft and both looked fine while
# they were: race bases ranged 41 to 47 while claiming to be equal, and every
# class gave three growth points while claiming to give two — which quietly
# made class growth equal to race growth, the opposite of the stated design.

CHARACTER = os.path.join(WORK, "src/main/java/com/elysium/core/character")


def stat_blocks(path):
    """Every ElysiumStatBlock.of(...) in a file, as a list of totals."""
    text = open(path, encoding="utf-8").read()
    blocks = []
    for match in re.finditer(r"ElysiumStatBlock\.of\((.*?)\)", text, re.S):
        numbers = re.findall(r",\s*(\d+)", match.group(1))
        blocks.append(sum(int(value) for value in numbers))
    return blocks


race_blocks = stat_blocks(os.path.join(CHARACTER, "ElysiumRaces.java"))
if len(race_blocks) != 12:
    fail(f"expected 12 stat blocks in ElysiumRaces (6 races x base+growth), "
         f"found {len(race_blocks)} - the balance check cannot read the file")
else:
    bases = race_blocks[0::2]
    growths = race_blocks[1::2]
    if len(set(bases)) != 1:
        fail(f"race starting blocks are not equal: {bases} - every race must "
             f"begin with the same number of points")
    if set(growths) != {3}:
        fail(f"race growth must be 3 points per level, found {growths}")
    print(f"race balance       : {len(bases)} races at {bases[0]} base, "
          f"{growths[0]}/level growth")

class_blocks = stat_blocks(os.path.join(CHARACTER, "ElysiumClasses.java"))
if not class_blocks:
    fail("could not read any stat blocks out of ElysiumClasses")
elif set(class_blocks) != {2}:
    fail(f"class growth must be 2 points per level - less than a race's 3 - "
         f"found {class_blocks}")
else:
    print(f"class balance      : {len(class_blocks)} classes at "
          f"{class_blocks[0]}/level growth")

# --- the split is wired the way it says it is -------------------------------
#
# Two failures here compile perfectly and break at runtime, which is what makes
# them worth a check rather than a comment.

manifest = open(os.path.join(RES, "META-INF/neoforge.mods.toml"), encoding="utf-8").read()
if 'modId="elysiumlib"' not in manifest:
    fail("neoforge.mods.toml does not declare a dependency on elysiumlib - "
         "a missing library would be a NoClassDefFoundError in a crash report "
         "instead of a clear line on the mods screen")

# The library's DeferredRegisters are declared in the elysiumlib namespace and
# registered on the library's own event bus. Registering one from here gives it
# an id whose namespace does not match the bus it went on, AND leaves the
# library broken when installed alone - which is exactly what happened to the
# standing attachments while the tree was being split.
LIBRARY_OWNED = ["ElysiumCharacter.ATTACHMENTS", "ElysiumStanding.ATTACHMENTS",
                 "ElysiumComponents.COMPONENTS"]
for dirpath, _, filenames in os.walk(os.path.join(WORK, "src/main/java")):
    for filename in filenames:
        if not filename.endswith(".java"):
            continue
        text = open(os.path.join(dirpath, filename), encoding="utf-8").read()
        for owned in LIBRARY_OWNED:
            if owned + ".register(" in text:
                fail(f"{filename} calls {owned}.register(...) - that register is "
                     f"declared in the elysiumlib namespace and belongs on the "
                     f"library's own event bus, not this mod's")

print("library wiring     : declared as required, no library registers re-run here")

# --- every material item has a recipe ---------------------------------------
#
# The obtainability audit above deliberately excuses gear gated on a modded
# ingredient tag, because this checkout cannot know which mods a pack has. That
# excuse would also hide a material item with no recipe at all, which is
# unobtainable everywhere - so check the file exists regardless.
_missing_recipes = sorted(
    item for item in MATERIAL_ITEMS
    if not exists(f"data/elysium/recipe/{item}.json"))
if _missing_recipes:
    fail(f"{len(_missing_recipes)} material item(s) have no recipe: "
         f"{_missing_recipes[:6]}{'...' if len(_missing_recipes) > 6 else ''} - "
         f"run: python3 tools/gen_material_gear.py")
else:
    print(f"material gear      : {len(MATERIAL_ITEMS)} items across "
          f"{len(gear_materials)} materials, all with recipes")

# --- data pack folder names must be the 1.21 singular form ------------------
for legacy in ["data/elysium/recipes", "data/elysium/loot_tables",
               "data/minecraft/tags/blocks", "data/minecraft/tags/items"]:
    if exists(legacy):
        fail(f"pre-1.21 data pack folder still present: {legacy}")

# --- every block is mineable ------------------------------------------------
pickaxe = json.load(open(os.path.join(RES, "data/minecraft/tags/block/mineable/pickaxe.json"),
                         encoding="utf-8"))["values"]
for block in blocks:
    if f"elysium:{block}" not in pickaxe:
        fail(f"{block} requires a correct tool but is in no mineable tag - it would drop nothing")

# --- loot functions that 1.21 renamed ---------------------------------------
#
# A loot table with an unknown function does not fall back to the rest of the
# table: the whole table fails to parse and the mob drops nothing. Both Elysium
# mobs were doing exactly that, and the only trace was one ERROR line during a
# resource reload. The replacements also changed shape - enchanted_count_increase
# will not guess which enchantment it scales with - so the rename alone is not
# enough and the required key is checked too.
RENAMED_LOOT_FUNCTIONS = {
    "minecraft:looting_enchant": "minecraft:enchanted_count_increase",
}


def check_loot_functions(node, where):
    if isinstance(node, dict):
        name = node.get("function")
        if name in RENAMED_LOOT_FUNCTIONS:
            fail(f"{where}: loot function {name} was renamed in 1.21 - use "
                 f"{RENAMED_LOOT_FUNCTIONS[name]}. An unknown function fails the "
                 f"entire table, so the mob drops nothing at all")
        if name == "minecraft:enchanted_count_increase" and "enchantment" not in node:
            fail(f"{where}: minecraft:enchanted_count_increase needs an explicit "
                 f"\"enchantment\" - unlike the 1.20 function it replaced, it does "
                 f"not assume Looting")
        for value in node.values():
            check_loot_functions(value, where)
    elif isinstance(node, list):
        for value in node:
            check_loot_functions(value, where)


_loot_root = os.path.join(RES, "data/elysium/loot_table")
_loot_tables = 0
_before = len(problems)
for dirpath, _, filenames in os.walk(_loot_root):
    for filename in filenames:
        if not filename.endswith(".json"):
            continue
        path = os.path.join(dirpath, filename)
        _loot_tables += 1
        check_loot_functions(json.load(open(path, encoding="utf-8")),
                             os.path.relpath(path, RES))
if len(problems) == _before:
    print(f"loot functions     : {_loot_tables} table(s), no functions 1.21 removed")

# --- Silent Gear material definitions ---------------------------------------
#
# Silent Gear reads these through the vanilla Ingredient codec and throws
# MaterialJsonException out of its reload listener when one does not parse -
# and that listener runs when the player clicks Create New World, so a bad
# ingredient here is not a missing material, it is a crash on the way into a
# world. Checked separately from recipes because nothing else looks in this
# folder.
_material_dir = os.path.join(RES, "data/elysium/silentgear_materials")
_materials = 0
_before = len(problems)
if os.path.isdir(_material_dir):
    for filename in sorted(os.listdir(_material_dir)):
        if not filename.endswith(".json"):
            continue
        path = os.path.join(_material_dir, filename)
        where = os.path.relpath(path, RES)
        material = json.load(open(path, encoding="utf-8"))
        _materials += 1
        crafting = material.get("crafting")
        if not isinstance(crafting, dict):
            fail(f"{where}: no crafting block")
            continue
        # Every key Silent Gear's codec requires, taken from its own 132
        # material files rather than from the wiki - the wiki example shows
        # three of these five, and the two it omits have no codec default, so a
        # file written from the documentation alone parses on no version of the
        # mod. gear_type_blacklist and part_substitutes are required *even
        # empty*: [] and {} are the "no restriction" values, and leaving the key
        # out is a hard failure that crashes the client on Create New World
        # rather than merely dropping the material.
        for required in ("can_salvage", "categories", "gear_type_blacklist",
                         "ingredient", "part_substitutes"):
            if required not in crafting:
                fail(f"{where}: crafting has no \"{required}\". Silent Gear requires it "
                     f"with no default, and a material missing it throws "
                     f"MaterialJsonException out of the resource reload - which is a "
                     f"crash on Create New World, not a missing material")
        if "ingredient" in crafting:
            check_ingredient(crafting["ingredient"], where, "crafting.ingredient")
if len(problems) == _before:
    print(f"silentgear         : {_materials} material(s), crafting blocks complete")

# --- all JSON parses --------------------------------------------------------
count = 0
for dirpath, _, filenames in os.walk(RES):
    for filename in filenames:
        if filename.endswith(".json"):
            count += 1
            try:
                json.load(open(os.path.join(dirpath, filename), encoding="utf-8"))
            except Exception as exc:
                fail(f"invalid JSON {os.path.join(dirpath, filename)}: {exc}")

print(f"json files parsed : {count}")

# ---------------------------------------------------------------------------
# The Registry Beacon answers to §14.3's failure row
# ---------------------------------------------------------------------------
#
# "Failure | beacon destroyed | claim is struck; structures remain and world
# changes persist" - and "destroyed" there means destroyed, not mined. The
# whole point of the row is that an Empire siege can break your beacon, and
# hooking the player-destroy path would make exactly that case the one case
# that did nothing, while a player quietly picking up their own beacon worked
# fine. That is an easy edit to make by accident and impossible to notice
# without playing a siege to its end.
beacon_path = os.path.join(
    WORK, "src/main/java/com/elysium/core/block/RegistryBeaconBlock.java")
if not os.path.exists(beacon_path):
    fail("RegistryBeaconBlock.java is missing")
else:
    beacon = re.sub(r"//[^\n]*|/\*.*?\*/", " ",
                    open(beacon_path, encoding="utf-8").read(), flags=re.S)
    if "onRemove" not in beacon:
        fail("RegistryBeaconBlock does not override onRemove, so a beacon "
             "destroyed by anything other than a player never strikes its "
             "claim - which is the one case §14.3's failure row is about")
    if "playerWillDestroy" in beacon:
        fail("RegistryBeaconBlock hooks playerWillDestroy; §14.3's failure "
             "row does not care who broke the beacon")
    if "strike(" not in beacon:
        fail("RegistryBeaconBlock never strikes a claim")

    # C6: planting or growing a claim absorbs the unregistered works it
    # covers. Checked in each method's own body — a file-wide search would
    # pass with the call removed from one path so long as the other kept it.
    def _beacon_body(signature):
        start = beacon.find(signature)
        if start < 0:
            return None
        depth = 0
        for index in range(beacon.find("{", start), len(beacon)):
            if beacon[index] == "{":
                depth += 1
            elif beacon[index] == "}":
                depth -= 1
                if depth == 0:
                    return beacon[start:index + 1]
        return None

    for _signature, _what in (("private void stake(", "planting"),
                              ("private InteractionResult ascend(", "ascending")):
        _body = _beacon_body(_signature)
        if _body is None:
            fail(f"RegistryBeaconBlock has no {_signature.strip('(')}; the "
                 f"works check cannot find the {_what} path")
        elif "absorbWorks(" not in _body:
            fail(f"{_what} a Registry Beacon no longer absorbs the "
                 f"unregistered works it covers. The survey keeps watching a "
                 f"base its owner has put on the record, and registering buys "
                 f"nothing but the floor")
    _absorb = _beacon_body("private static void absorbWorks(")
    if _absorb is not None and ".registered(" not in _absorb:
        fail("absorbWorks no longer asks ElysiumWorks to register anything")
    # §E.1: "beacon destruction releases claim but does not erase constructed
    # world". Nothing in this block may remove or replace blocks.
    for forbidden in ("destroyBlock", "removeBlock", "setBlockAndUpdate"):
        if forbidden in beacon:
            fail(f"RegistryBeaconBlock calls {forbidden} - §E.1 requires that "
                 f"striking a claim leaves the constructed world alone")
    if "state.is(newState.getBlock())" not in beacon:
        fail("RegistryBeaconBlock.onRemove does not guard on the block "
             "actually changing, so lighting up would strike its own claim")
    # Claiming must not be pure cost. 14.2 levies a permanent Suspicion floor
    # and 14.3 makes the beacon a siege target; F.1's glossary names what comes
    # back - "ownership state with respawn/persistence/Empire consequences".
    # Without the respawn half, no rational player plants a beacon and the whole
    # of 14.2 and 14.3 is content nobody opts into, which is the worst way for a
    # system to fail: it works perfectly and is never seen.
    if "setRespawnPosition" not in beacon:
        fail("RegistryBeaconBlock does not anchor respawn, so a claim is all "
             "cost and no benefit - see F.1, and note that nobody would plant "
             "one")
    # Ascending the base is the third road to a world tier, alongside levelling
    # and the journey. The beacon is where a player pays for it, so the beacon
    # is where the tier is recorded - and it has to be recorded, not computed,
    # because computing it means walking every dimension's claim map on every
    # loot roll.
    _ascend = body_of(beacon, "private InteractionResult ascend")
    if "noticed(player, grown)" not in _ascend:
        fail("ascending a base no longer raises the owner's world tier, so the "
             "most expensive deliberate act in the mod buys ground and "
             "attention and nothing the worlds answer to")
    _noticed = body_of(beacon, "private void noticed")
    if "ElysiumTiers.lift(player," not in _noticed:
        fail("the base's ascension is written to the world floor by some route "
             "other than lift(), which is the one place that refuses to let a "
             "floor fall")
    if "ElysiumWorldTier.tierForAscension(" not in _noticed:
        fail("the beacon converts ascensions to tiers with arithmetic of its "
             "own instead of the library's, so the two would drift")
    if "noticed(player, claim)" not in body_of(beacon, "private void report"):
        fail("only a fresh ascension raises the tier, so a world already "
             "ascended before this existed can never catch up")
    if "elysium.claim.world_tier" not in lang:
        fail("elysium.claim.world_tier has no translation; the moment a base "
             "raises the world tier would announce itself as a raw key")
    print("registry beacon   : strikes on destruction by any hand, erases "
          "nothing, anchors respawn, and its ascension is a world tier")

# ---------------------------------------------------------------------------
# §14.5's inspection beacon: planted automatically, into somebody's world
# ---------------------------------------------------------------------------
#
# This is the only thing in the mod that puts a block into the world without a
# player asking it to, on a timer, near where they are standing. That makes one
# property non-negotiable: it must never replace anything. A placement that
# overwrote a block would be the mod damaging a build, and the player would
# have no way to know what happened or what was there.
#
# The rest is the §27.1 row it implements — "cooperate" and "resist" — and both
# halves have to work or the row is decorative.
inspection_path = os.path.join(
    WORK, "src/main/java/com/elysium/core/block/InspectionBeaconBlock.java")
if not os.path.exists(inspection_path):
    fail("InspectionBeaconBlock.java is missing — §14.5's inspection beacon is "
         "the Empire's half of the structure pair a claim is the player's half "
         "of")
else:
    inspection = re.sub(r"//[^\n]*|/\*.*?\*/", " ",
                        open(inspection_path, encoding="utf-8").read(), flags=re.S)

    if "isAir()" not in inspection:
        fail("InspectionBeaconBlock.plant does not check that its target is "
             "air — an automatic placement that overwrites a block is the mod "
             "damaging somebody's build on a timer")
    if "setBlockAndUpdate" in inspection and "getBlockState(surface).isAir()" not in inspection:
        fail("InspectionBeaconBlock.plant writes a block without first "
             "confirming the position is empty")

    # "resist": breaking it must stop the reporting AND be filed.
    if "onRemove" not in inspection or "remove(pos)" not in inspection:
        fail("breaking an inspection beacon does not remove its record, so it "
             "would keep doubling extraction from beyond the grave")
    if "CAUSE_RESISTED" not in inspection:
        fail("destroying Imperial equipment is free — §27.1 lists resistance "
             "as a response, and a response with no cost is not a decision")
    # The charge alone is not the price, and priced as Suspicion alone the
    # decision collapses: simulated, a flat charge is beaten by cooperating
    # after about four ore blocks and stays beaten forever, because the doubled
    # rate accrues per block and a one-off does not. Resistance has to cost a
    # currency the doubled rate cannot be compared against.
    if "ElysiumSquads.send" not in inspection:
        fail("resisting an inspection costs only Suspicion. Two options where "
             "one always wins by arithmetic is not a decision — the band the "
             "Empire sends is what makes it one")
    if "elysium.inspection.resisted_quiet" not in inspection:
        fail("resisting reports a band unconditionally, but ElysiumSquads.send "
             "is allowed to refuse (crowd cap, suppressors) — the message "
             "would promise enforcement that never arrives")
    # Only a player resists. A creeper has not committed a crime.
    if "playerWillDestroy" not in inspection:
        fail("the resistance charge is not on the player-destroy path, so a "
             "creeper flattening a post would bill the nearest person")

    # It must stay unobtainable, both ways round.
    if "inspection_beacon" not in ITEMLESS_BLOCKS:
        fail("the inspection beacon has become obtainable — a player holding a "
             "stack of these has a supply of a block whose only function is to "
             "tax them")
    if os.path.exists(os.path.join(RES, "data/elysium/recipe/inspection_beacon.json")):
        fail("the inspection beacon has a recipe; it is Imperial equipment, "
             "not player equipment")

    for key in ("block.elysium.inspection_beacon", "elysium.inspection.reporting",
                "elysium.inspection.lapsed", "elysium.inspection.resisted"):
        if key not in lang:
            fail(f"no lang entry for {key} — the raw key shows in game")

    print("inspection beacon : never overwrites, resisting is filed, "
          "unobtainable by design")

# ---------------------------------------------------------------------------
# The gear art
# ---------------------------------------------------------------------------
#
# Everything below is about sprites, and every failure it looks for is silent:
# nothing crashes, nothing logs, and the item is simply in the player's hand
# looking wrong. Three of these four checks exist because the thing they check
# for had already happened and nobody had noticed.

def _art_package():
    here = os.path.dirname(os.path.abspath(__file__))
    for base in (here, os.path.dirname(here)):
        if os.path.isfile(os.path.join(base, "art", "style.py")):
            return os.path.join(base, "art")
    return None


art_path = _art_package()
pixelart = material_art = None
art_error = None

if art_path is None:
    fail("cannot find the art package, so none of the gear art is checked")
else:
    sys.path.insert(0, art_path)
    try:
        # Importing this validates every shade map: sixteen rows of sixteen
        # known characters, with an element accent landing on a pixel the
        # sprite actually fills. A map one character short does not raise on
        # its own — it shifts every row below it by one and looks like bad
        # drawing rather than a typo.
        import pixelart
        import sprites
        import materials as material_art
    except Exception as exc:
        art_error = exc
        fail(f"the art package will not import ({exc}), so no sprite in this "
             f"mod can be regenerated")

if pixelart is not None and art_error is None:
    print(f"shade maps         : {len(pixelart.MAPS)} sprites at "
          f"{pixelart.SIZE}x{pixelart.SIZE}, each accent on the sprite")

    # -- are the materials actually distinguishable? -----------------------
    #
    # This is the check that found brass and gold rendering byte-identical.
    # The ramp kept nothing from its base but hue, those two bases share one,
    # and so every brass item in the mod was the same file as its gold
    # counterpart: twenty-six materials wearing twenty-five faces. Not visible
    # in the palette code, not visible in a screenshot of any one item, and
    # perfectly visible the moment somebody asks.
    #
    # The threshold is in CIELAB, where 2.3 is the textbook just-noticeable
    # difference between two large flat patches. Eight is the working number
    # for a tint spread over a sixteen-pixel sprite seen among others in an
    # inventory row.
    MIN_SEPARATION = 8.0

    def _to_lab(value):
        value = value.lstrip("#")
        channels = [int(value[i:i + 2], 16) / 255.0 for i in (0, 2, 4)]
        red, green, blue = [c / 12.92 if c <= 0.04045
                            else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
        x = red * 0.4124 + green * 0.3576 + blue * 0.1805
        y = red * 0.2126 + green * 0.7152 + blue * 0.0722
        z = red * 0.0193 + green * 0.1192 + blue * 0.9505

        def f(t):
            return t ** (1 / 3.0) if t > 0.008856 else 7.787 * t + 16 / 116.0

        fx, fy, fz = f(x / 0.95047), f(y), f(z / 1.08883)
        return (116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))

    midtones = {name: _to_lab(material_art.ramp(base)[3])
                for name, base in material_art.BASES.items()}
    closest = None
    for first in sorted(midtones):
        for second in sorted(midtones):
            if first >= second:
                continue
            distance = sum((a - b) ** 2 for a, b in
                           zip(midtones[first], midtones[second])) ** 0.5
            if closest is None or distance < closest[0]:
                closest = (distance, first, second)
            if distance < MIN_SEPARATION:
                fail(f"{first} and {second} sit {distance:.1f} apart in CIELAB. "
                     f"Below {MIN_SEPARATION} two materials tell the player "
                     f"nothing apart — move one of their base colours in "
                     f"art/materials.py")
    if closest:
        print(f"material palette   : {len(midtones)} materials, closest pair "
              f"{closest[1]}/{closest[2]} at {closest[0]:.1f} CIELAB "
              f"(floor {MIN_SEPARATION})")

    # -- the sprites actually on disk --------------------------------------
    try:
        from PIL import Image
    except ImportError:
        Image = None
        notes.append("Pillow is not installed, so the shipped sprites were not "
                     "opened — only the palette they are drawn from was checked")

    # Every shape the Java registers has to have something to draw it with.
    #
    # Adding a shape to the Shape enum and forgetting the art does not fail at
    # build time — it fails inside the generator with an AttributeError about a
    # missing attribute on a module, which says nothing about what to do. This
    # names the shape and the two places it needs to exist.
    missing_art = [shape for shape in SHAPES
                   if shape not in pixelart.MAPS and not hasattr(sprites, shape)]
    for shape in missing_art:
        fail(f"ElysiumMaterialGear registers a {shape!r} for every material and "
             f"nothing can draw one: there is no shade map for it in "
             f"art/pixelart.py and no {shape}() in art/sprites.py")
    if not missing_art:
        print(f"tool shapes        : {len(SHAPES)} in the Shape enum, "
              f"every one of them drawable")

    if Image is not None:
        ITEM_TEXTURES = os.path.join(RES, "assets/elysium/textures/item")
        GEAR_SHAPES = tuple(SHAPES)
        ARMOUR_SHAPES = ("helmet", "chestplate", "leggings", "boots")

        # Two materials must not produce the same file. The palette check above
        # asks this of the numbers; this asks it of the artefact, which is what
        # a player sees, and would still catch it if the sprites stopped being
        # derived from the palette at all.
        seen = {}
        for material in sorted(material_art.BASES):
            path = os.path.join(ITEM_TEXTURES, f"{material}_helmet.png")
            if not os.path.exists(path):
                fail(f"{material} has no helmet sprite, so the material is "
                     f"registered with nothing to show for it")
                continue
            with open(path, "rb") as handle:
                payload = handle.read()
            if payload in seen:
                fail(f"{material} and {seen[payload]} ship the identical helmet "
                     f"sprite — two materials the player cannot tell apart")
            seen[payload] = material

        # The same question turned ninety degrees: within one material, no two
        # *shapes* may look the same either.
        #
        # The check above compares materials for a fixed shape and would pass
        # cheerfully while every shape was the same picture. That is not
        # hypothetical — the first drafts of the plain axe, shovel and hoe were
        # three variations on a blob on a stick, and telling them apart in a
        # hotbar was guesswork. Iron stands for all of them: it is always
        # present, and the maps do not vary by material.
        # Not byte-identity: near-identity. Two shapes drawn from the same map
        # still differ in their element accent, which is two or three pixels —
        # so an exact comparison passes while the two sprites are the same
        # picture with a dot moved. Sixteen pixels out of 256 is the floor for
        # "these are different drawings".
        #
        # What this cannot do is judge whether two genuinely different drawings
        # *look* alike. That needs eyes, and it needed them here: the first
        # drafts of the axe, shovel and hoe were three variations on a blob and
        # every one of them cleared this check comfortably.
        DISTINCT_PIXELS = 16
        drawn = {}
        for shape in SHAPES:
            path = os.path.join(ITEM_TEXTURES, f"iron_{shape}.png")
            if not os.path.exists(path):
                continue
            pixels = list(Image.open(path).convert("RGBA").getdata())
            for other, other_pixels in drawn.items():
                differing = sum(1 for a, b in zip(pixels, other_pixels) if a != b)
                if differing < DISTINCT_PIXELS:
                    fail(f"the {shape} and the {other} differ by only "
                         f"{differing} pixel(s). They are two different tools "
                         f"and a player has to tell them apart in a hotbar")
            drawn[shape] = pixels

        # A sprite with no value structure reads as a silhouette however well
        # it is drawn, which this art package has already learned once: see the
        # note on _STEPS in art/materials.py. Vanilla's own metal items span
        # roughly 200 units of luminance.
        MIN_RANGE = 120

        def luminance(pixel):
            return 0.2126 * pixel[0] + 0.7152 * pixel[1] + 0.0722 * pixel[2]

        # The metal is measured on its own, with the handle and the element
        # accent taken out first.
        #
        # The first version of this check measured every opaque pixel, and a
        # deliberate test proved it useless: flattening the material ramp to
        # five nearly identical tones — exactly the regression the check exists
        # for — still passed, because the wooden handle runs dark-to-light and
        # the accent is near-white, and between them they carried the range on
        # their own. A check that cannot fail on the thing it was written for
        # is worse than no check, because it reads as coverage.
        import style as art_style
        wood_tones = {material_art._hex_to_rgb(tone) for tone in pixelart.WOOD}
        glow_tones = {material_art._hex_to_rgb(tone)
                      for ramp in art_style.GLOW.values() for tone in ramp}
        not_metal = wood_tones | glow_tones

        checked = wooden = 0
        flattest = None
        for name in sorted(os.listdir(ITEM_TEXTURES)):
            if not name.endswith(".png"):
                continue
            shape = name[:-4].rsplit("_", 1)[-1]
            if shape not in GEAR_SHAPES + ARMOUR_SHAPES:
                continue
            image = Image.open(os.path.join(ITEM_TEXTURES, name)).convert("RGBA")
            if image.size != (16, 16):
                fail(f"{name} is {image.size[0]}x{image.size[1]}; every item "
                     f"sprite here is a 16x16")
                continue
            opaque = [p for p in image.getdata() if p[3] > 0]
            if not opaque:
                fail(f"{name} is entirely transparent")
                continue

            metal = [luminance(p) for p in opaque if p[:3] not in not_metal]
            if not metal:
                fail(f"{name} has no metal pixels at all — it is drawn entirely "
                     f"in handle and accent colours")
                continue
            spread = max(metal) - min(metal)
            if flattest is None or spread < flattest[0]:
                flattest = (spread, name)
            if spread < MIN_RANGE:
                fail(f"{name}'s metal spans only {spread:.0f} units of "
                     f"luminance. Below {MIN_RANGE} there is no light and shade "
                     f"left in it and it reads as a coloured silhouette")

            if shape in GEAR_SHAPES:
                # The wooden haft is what makes a tool read as a tool rather
                # than as a casting of one, and it is also the constant the
                # recolour is judged against — with nothing fixed to compare
                # to, copper and iron are just two greys. Losing it would look
                # like a styling choice rather than a regression.
                if any(p[:3] in wood_tones for p in opaque):
                    wooden += 1
                else:
                    fail(f"{name} has no handle pixels left — a tool drawn end "
                         f"to end in one material gives the eye nothing to read "
                         f"the material against")
            checked += 1

        if checked:
            print(f"gear sprites       : {checked} checked, all 16x16, flattest "
                  f"is {flattest[1]} at {flattest[0]:.0f} luminance "
                  f"(floor {MIN_RANGE})")
            print(f"tool handles       : {wooden} tools keep a wooden haft")
        else:
            fail("no generated gear sprites were found at all — the naming "
                 "scheme must have changed, and this entire section just "
                 "passed by checking nothing")

# The generator has to be able to regenerate.
#
# It spent its whole life reporting "sprites written: 0", because it skipped
# every file that already existed and after the first run that is all of them.
# Editing a shape changed nothing, and the only way to see an edit was to
# delete 196 files first — which nobody would guess and nothing said.
gear_tool = os.path.join(WORK, "tools/gen_material_gear.py")
if os.path.isfile(gear_tool):
    gear_source = open(gear_tool, encoding="utf-8").read()
    # Scoped to write_sprites, because the rule is not general: write_models and
    # write_recipes skip files that exist on purpose, so that a hand-written
    # recipe wins over a generated one. It is only the *sprites* where "already
    # there" means "drawn by an older version of the code".
    body = re.search(r"\ndef write_sprites\(.*?\n(?=\ndef |\n# ---)",
                     gear_source, re.S)
    if not body:
        fail("cannot find write_sprites in tools/gen_material_gear.py, so "
             "whether the gear art can still be regenerated is unchecked")
    elif re.search(r"if os\.path\.exists\(", body.group(0)):
        fail("write_sprites in tools/gen_material_gear.py is skipping sprites "
             "that already exist again. After one run every generated sprite "
             "exists, so that turns the generator into a no-op and pins the art "
             "at whatever happened to be drawn first")
    elif "hand_drawn()" not in body.group(0):
        fail("write_sprites no longer consults hand_drawn(), so it will "
             "overwrite the sprites art/build.py draws by name")
    else:
        print("gear generator     : rewrites what it owns, reserves what "
              "art/build.py draws by name")

print()


# ---------------------------------------------------------------------------
# Gear convergence: the crossing to Silent Gear and Apotheosis
# ---------------------------------------------------------------------------
#
# The whole feature rests on one property: Elysium must build and run with
# neither of those mods installed. That property is invisible in this
# environment — both jars are on the compile classpath and every file type-
# checks — so it has to be checked structurally, here.
#
# This is not hypothetical. An earlier version of this repository imported
# shadows.apotheosis.* onto classes that were loaded unconditionally; the mod
# could then neither build without Apotheosis nor run without it, and FIXES.md
# still carries the entry. The rule that prevents it is simple and mechanical:
# foreign imports live only in the bridge files, and the bridges are only ever
# named inside a method that has asked ModList first.

JAVA_DIR = os.path.join(WORK, "src/main/java/com/elysium/core")


def strip_java_comments(text):
    """Java source with comments removed.

    A check of mine once passed on deliberately broken code because the
    phrase it searched for appeared in the javadoc explaining the rule.
    Prose about a guard is not a guard.
    """
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", " ", text)


COMPAT = os.path.join(JAVA_DIR, "compat")
FOREIGN = ("net.silentchaos512", "dev.shadowsoffire")

# The only two files allowed to import either mod.
BRIDGES = {"SilentGearBridge.java", "ApotheosisBridge.java"}

if not os.path.isdir(COMPAT):
    fail("com/elysium/core/compat/ is missing — the gear conversion is gone")
else:
    compat_files = sorted(f for f in os.listdir(COMPAT) if f.endswith(".java"))

    # 1. Foreign imports, only in the bridges.
    for filename in compat_files:
        text = open(os.path.join(COMPAT, filename), encoding="utf-8").read()
        imports = [line for line in text.split("\n")
                   if line.startswith("import ") and any(f in line for f in FOREIGN)]
        if imports and filename not in BRIDGES:
            fail(f"compat/{filename} imports {imports[0].strip()}. Only "
                 f"{sorted(BRIDGES)} may name another mod's classes — anything "
                 f"else is loaded unconditionally and takes the whole mod down "
                 f"with it when that mod is absent")

    # ...and nowhere else in the module at all.
    for root, _, files in os.walk(JAVA_DIR):
        if os.path.basename(root) == "compat":
            continue
        for filename in files:
            if not filename.endswith(".java"):
                continue
            text = open(os.path.join(root, filename), encoding="utf-8").read()
            for line in text.split("\n"):
                if line.startswith("import ") and any(f in line for f in FOREIGN):
                    where = os.path.relpath(os.path.join(root, filename), JAVA_DIR)
                    fail(f"{where} imports {line.strip()} outside compat/. That "
                         f"class loads whether or not the mod is installed")

    # 2. The dispatcher must be clean, because it is the class that is always
    #    loaded. If GearConvergence itself named a foreign type in a field, a
    #    parameter or a supertype, the guard inside its methods would never run.
    dispatcher = os.path.join(COMPAT, "GearConvergence.java")
    if not os.path.isfile(dispatcher):
        fail("compat/GearConvergence.java is missing — nothing dispatches")
    else:
        text = open(dispatcher, encoding="utf-8").read()
        stripped = strip_java_comments(text)
        for foreign in FOREIGN:
            if foreign in stripped:
                fail(f"GearConvergence names {foreign} in its code. It is the "
                     f"class that is always loaded, so a foreign type anywhere "
                     f"in its signature defeats every ModList check inside it")

        # 3. Every mention of a bridge must sit downstream of a ModList check.
        #    Checked per method, which is the scope the JVM actually resolves
        #    at: a bridge named in a method that never asks is a class load on
        #    a game that does not have the mod.
        for method in re.split(r"\n    (?=[a-zA-Z@].*\()", stripped):
            named = [b for b in ("SilentGearBridge", "ApotheosisBridge")
                     if b in method]
            if not named:
                continue
            if "ModList" not in method and "available()" not in method:
                fail(f"a method in GearConvergence names {named[0]} without "
                     f"asking ModList first:\n      "
                     + method.strip().split("\n")[0])

    # 3b. The same rule, checked in the bytecode rather than the source.
    #
    # The import scan above reads what a person wrote. This reads what javac
    # emitted, which is the thing the JVM will actually resolve — and the two
    # can differ. A foreign type can reach a class file through an inferred
    # `var`, a generic signature, a lambda's synthetic method or a compiler
    # bridge method, none of which appear as an import line. Every one of those
    # would put a NoClassDefFoundError in front of a player who does not have
    # the mod, and none of them is visible in the source.
    #
    # Runs only where the module has been compiled, which is the same condition
    # the machine harness uses. Skipping is reported, not silent.
    classes_dir = os.environ.get("ELYSIUM_OUT", "/tmp/elysium-classes")
    compiled = os.path.join(classes_dir, "elysium-core")
    if not os.path.isdir(compiled):
        print(f"gear convergence  : bytecode not scanned, no compiled classes "
              f"at {compiled} (run ./compile.sh first)")
    else:
        # Read the class bytes directly rather than shelling out to javap.
        # A class file stores every type it references as a UTF8 constant in
        # the internal form ("net/silentchaos512/..."), so a byte search finds
        # exactly what the constant pool holds — and finds it about a thousand
        # times faster than 85 javap processes, which mattered the moment the
        # negative tests started running this eleven times in a row.
        bridge_classes = {os.path.splitext(b)[0] for b in BRIDGES}
        foreign_bytes = (b"net/silentchaos512", b"dev/shadowsoffire")
        leaked = []
        scanned = 0
        for root, _, files in os.walk(os.path.join(compiled, "com/elysium/core")):
            for filename in files:
                if not filename.endswith(".class"):
                    continue
                scanned += 1
                # The bridges are allowed to, and must, reference them. So are
                # their nested and synthetic classes, which carry the outer
                # name before a $.
                stem = filename[:-len(".class")].split("$")[0]
                if stem in bridge_classes:
                    continue
                blob = open(os.path.join(root, filename), "rb").read()
                for foreign in foreign_bytes:
                    if foreign in blob:
                        name = os.path.relpath(os.path.join(root, filename),
                                               compiled)[:-len(".class")]
                        leaked.append((name.replace(os.sep, "."),
                                       foreign.decode()))
                        break
        for name, foreign in leaked:
            fail(f"{name} carries a reference to {foreign} in its bytecode. "
                 f"That class is loaded whether or not the mod is installed, "
                 f"so every player without it gets a NoClassDefFoundError — "
                 f"and no import line in the source says so")
        if not leaked:
            print(f"convergence guard : {scanned} compiled classes scanned, "
                  f"foreign references confined to the {len(bridge_classes)} "
                  f"bridge class(es) in the bytecode as well as the source")

    # 4. Both mods must be declared optional in the manifest, or NeoForge will
    #    not know to load Elysium after them — and a bridge that runs before
    #    the mod it bridges to sees an empty registry.
    #
    # Parsed per block, not searched for in a window. The first version read
    # 400 characters either side of the mod id and found the word "optional" in
    # the *comment* above the section and in the neighbouring dependency — so a
    # dependency deliberately switched to required still passed. A check that
    # can be satisfied by prose nearby is not checking the setting.
    toml = open(os.path.join(RES, "META-INF/neoforge.mods.toml"),
                encoding="utf-8").read()
    declared = {}
    current = None
    for raw in toml.split("\n"):
        line = raw.split("#", 1)[0].strip()
        if line.startswith("[["):
            current = {}
        elif current is not None and "=" in line:
            key, _, value = line.partition("=")
            current[key.strip()] = value.strip().strip('"')
            if key.strip() == "modId":
                declared[current["modId"]] = current
    for mod_id in ("silentgear", "apotheosis"):
        entry = declared.get(mod_id)
        if entry is None:
            fail(f"neoforge.mods.toml declares no dependency on {mod_id}, so "
                 f"nothing tells NeoForge to load Elysium after it — and a "
                 f"bridge that runs first sees an empty registry")
        elif entry.get("type") != "optional":
            fail(f"the {mod_id} dependency in neoforge.mods.toml is "
                 f"type={entry.get('type')!r}, not optional — Elysium would "
                 f"refuse to start without a mod it is supposed to run fine "
                 f"without")
        elif entry.get("ordering") != "AFTER":
            fail(f"the {mod_id} dependency does not order Elysium AFTER it, so "
                 f"the bridge may build gear before that mod has registered "
                 f"anything to build it from")

    # 5. The smithing recipe has to be readable by the serializer that ships.
    convergence = os.path.join(RES, "data/elysium/recipe/convergence.json")
    if not os.path.isfile(convergence):
        fail("no convergence recipe ships, so the smithing table does nothing")
    else:
        recipe = json.load(open(convergence, encoding="utf-8"))
        registered = re.findall(r'SERIALIZERS\.register\("([a-z_]+)"',
                                open(os.path.join(COMPAT,
                                     "ConvergenceSmithingRecipe.java"),
                                     encoding="utf-8").read())
        wanted = recipe["type"].split(":", 1)[1]
        if wanted not in registered:
            fail(f"convergence.json is a {recipe['type']} and the mod registers "
                 f"{registered} — the recipe fails to load and the table does "
                 f"nothing, silently")

    # 6. Both templates have to be real items a player can get.
    for template in ("silent_convergence_template", "affix_convergence_template"):
        if template not in items:
            fail(f"{template} is not registered, so the conversion has no "
                 f"template and cannot be performed at all")
        if not os.path.isfile(os.path.join(
                RES, f"assets/elysium/textures/item/{template}.png")):
            fail(f"{template} has no texture")
        if not os.path.isfile(os.path.join(
                RES, f"data/elysium/recipe/{template}.json")):
            fail(f"{template} has no recipe, so it is uncraftable and the "
                 f"conversion is unreachable in survival")

    # 7. Every gear shape must have somewhere to go. A shape added to the enum
    #    later and forgotten here is a piece a player cannot convert, with
    #    nothing saying why.
    bridge = open(os.path.join(COMPAT, "SilentGearBridge.java"),
                  encoding="utf-8").read()
    for shape in SHAPES:
        if f"Shape.{shape.upper()}" not in bridge:
            fail(f"Shape.{shape.upper()} has no Silent Gear counterpart in "
                 f"SilentGearBridge. A player holding one gets an empty output "
                 f"slot and no reason")
    for slot in ("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"):
        if f"ArmorItem.Type.{slot}" not in bridge:
            fail(f"{slot} has no Silent Gear counterpart in SilentGearBridge")

    # Report what was found, not what was hoped for. The first version of this
    # line ended "both mods optional" and printed it unchanged in a run that had
    # just failed on a required dependency — a summary that states the
    # conclusion rather than the measurement will eventually state a false one.
    _types = ", ".join(f"{m}={declared.get(m, {}).get('type', 'absent')}"
                       for m in ("silentgear", "apotheosis"))
    print(f"gear convergence  : {len(compat_files)} compat file(s), foreign "
          f"imports in {len(BRIDGES)} of them, {len(SHAPES)} shapes and 4 slots "
          f"mapped, {_types}")

# ---------------------------------------------------------------------------
# Field rations: §15.5's food that answers a hazard
# ---------------------------------------------------------------------------
#
# HazardHarness checks what a ward is worth and elysium-lib's validator checks
# that the effects exist and that the damage tick reads them. Neither can see
# the half of the feature that lives here: whether the five items a player
# actually crafts are edible, whether each one grants the ward it claims, and
# whether all five hazards are covered rather than three of them twice.

ration_item = os.path.join(WORK,
    "src/main/java/com/elysium/core/item/ElysiumRationItem.java")
ration_names = re.findall(r'ration\(\s*"([a-z_]+)",\s*'
                          r'com\.elysium\.lib\.hazard\.ElysiumHazards\.([A-Z]+)\)',
                          open(JAVA, encoding="utf-8").read())
HAZARDS = ("THERMAL", "CRYOGENIC", "CORROSIVE", "RADIOLOGICAL", "PRESSURE")

if not ration_names:
    fail("no field rations are registered, so §10.2's cheap counter cannot be "
         "crafted and the only answers to a hazard are two projects")
else:
    covered = [hazard for _name, hazard in ration_names]
    for hazard in HAZARDS:
        if hazard not in covered:
            fail(f"no ration wards {hazard.lower()}, so that hazard has no "
                 f"cheap counter and a player who meets it early can only "
                 f"leave")
    for hazard in set(covered):
        if covered.count(hazard) > 1:
            fail(f"{covered.count(hazard)} rations ward {hazard.lower()}; one "
                 f"of the five hazards is being answered twice while another "
                 f"is answered not at all")

    lang_file = json.load(open(os.path.join(
        RES, "assets/elysium/lang/en_us.json"), encoding="utf-8"))
    for key in ("elysium.tooltip.ration", "elysium.tooltip.ration.whole"):
        if key not in lang_file:
            fail(f"{key} is not translated, so the line that tells a player how "
                 f"long the immunity lasts renders as a raw key")

    for name, _hazard in ration_names:
        if not os.path.isfile(os.path.join(
                RES, f"data/elysium/recipe/{name}.json")):
            fail(f"{name} has no recipe, so the cheap counter is uncraftable "
                 f"and is therefore not cheap, it is impossible")

if not os.path.isfile(ration_item):
    fail("ElysiumRationItem is missing, so the ration items have no behaviour")
else:
    ration_src = re.sub(r"/\*.*?\*/", " ",
                        open(ration_item, encoding="utf-8").read(), flags=re.S)
    ration_src = re.sub(r"//[^\n]*", " ", ration_src)
    if ".food(" not in ration_src:
        fail("ElysiumRationItem is not food, so a ration cannot be eaten and "
             "nothing it does can ever happen")
    if "addEffect(" not in ration_src:
        fail("ElysiumRationItem never applies an effect, so eating a ration "
             "feeds a player and wards them against nothing")
    if "ElysiumWards.wardFor(" not in ration_src:
        fail("ElysiumRationItem does not ask ElysiumWards which effect wards "
             "its hazard, so whatever it grants is not what the damage tick "
             "looks for")
    # Scoped to the method that applies it. The loose form — "is the constant
    # mentioned anywhere in the file" — passed a deliberate test that replaced
    # the applied duration with a literal, because the tooltip also names the
    # constant and the tooltip is not what a player gets. Same failure as the
    # three declaration-instead-of-call checks before it.
    applying = ration_src.split("finishUsingItem", 1)
    if len(applying) < 2:
        fail("ElysiumRationItem no longer applies anything when it is eaten")
    elif "ElysiumWards.DURATION" not in applying[1].split("appendHoverText")[0]:
        fail("ElysiumRationItem applies its ward for a duration of its own "
             "rather than the library's, so the number HazardHarness checks "
             "is not the number a player gets")

    print(f"field rations     : {len(ration_names)} edible, one per hazard, "
          f"each granting the library's own ward for its full duration")

# ---------------------------------------------------------------------------
# Unit codecs: what a datapack reads must be what the server can send
# ---------------------------------------------------------------------------
#
# A recipe carrying no data has both codecs written as units, and the obvious
# spelling of that disconnected every client that joined a server:
#
#   MapCodec.unit(ConvergenceSmithingRecipe::new)
#   StreamCodec.unit(new ConvergenceSmithingRecipe())
#
# The map codec's method reference builds a new object when the recipe file
# loads; StreamCodec.unit refuses to encode anything not equals() to the object
# it captured. Nothing about it is visible at compile time or in a single-player
# world that never reaches the recipe sync.
#
# The round trip itself cannot run here — touching the recipe class loads
# net.minecraft...Recipe, which needs a bootstrapped game, which needs FML. So
# RecipeCodecHarness pins what `unit` compares, and this enforces the shape.

for _java in pathlib.Path(os.path.join(WORK, "src/main/java")).rglob("*.java"):
    _text = _java.read_text(encoding="utf-8")
    _code = re.sub(r"//[^\n]*", " ", re.sub(r"/\*.*?\*/", " ", _text, flags=re.S))
    if "StreamCodec.unit(" not in _code:
        continue
    _where = os.path.relpath(str(_java), WORK)

    # 1. A fresh instance handed straight to the stream codec can never be the
    #    one anything else produces.
    if re.search(r"StreamCodec\.unit\(\s*new\s", _code):
        fail(f"{_where}: StreamCodec.unit is given a freshly constructed value. "
             f"Nothing else in the game will ever be equal to it, so the first "
             f"client to join is disconnected while the packet is written")

    # 2. A supplier on the map side and a value on the stream side is the same
    #    disagreement spelled two ways.
    if re.search(r"MapCodec\.unit\(\s*\w+::new", _code):
        fail(f"{_where}: MapCodec.unit is given a ::new supplier while a "
             f"StreamCodec.unit exists in the same file. Every load makes a new "
             f"object and the stream codec captured a different one")

    # 3. Both units must name the same thing.
    _map_args = re.findall(r"MapCodec\.unit\(\s*([A-Za-z_][\w.]*)\s*\)", _code)
    _stream_args = re.findall(r"StreamCodec\.unit\(\s*([A-Za-z_][\w.]*)\s*\)", _code)
    if _map_args and _stream_args and set(_map_args) != set(_stream_args):
        fail(f"{_where}: the map codec is a unit of {sorted(set(_map_args))} and "
             f"the stream codec a unit of {sorted(set(_stream_args))}. They have "
             f"to be the same value or the recipe sync refuses it")

    # 4. ...and the type answers equals, because that is what unit asks.
    if "public boolean equals(" not in _code:
        fail(f"{_where}: uses StreamCodec.unit but does not override equals, so "
             f"any second instance that ever exists cannot be encoded")

# Crafting categories are an enum with four values and no error a player sees:
# an unknown one fails the whole recipe file during loading, so the item ships
# with art, a name and no way to make it. "food" cost five rations exactly that.
CRAFTING_CATEGORIES = {"building", "redstone", "equipment", "misc"}
_bad_category = []
for _recipe in pathlib.Path(os.path.join(RES, "data")).rglob("recipe/*.json"):
    try:
        _data = json.load(open(_recipe, encoding="utf-8"))
    except Exception:
        continue                      # the JSON parse pass above reports these
    if not str(_data.get("type", "")).startswith("minecraft:crafting"):
        continue
    _category = _data.get("category")
    if _category is not None and _category not in CRAFTING_CATEGORIES:
        _bad_category.append((os.path.relpath(str(_recipe), RES), _category))
for _path, _category in _bad_category:
    fail(f"{_path}: crafting category {_category!r} is not one of "
         f"{sorted(CRAFTING_CATEGORIES)}. The recipe fails to parse and is "
         f"dropped, so the item exists and cannot be crafted")

if not _bad_category:
    print("recipe codecs     : unit codecs share one value that answers equals; "
          "every crafting category is one the game has")

# ---------------------------------------------------------------------------
# What a kill pays, and what a bench costs
# ---------------------------------------------------------------------------
#
# Balance numbers with no harness behind them, because the reward provider
# needs registered items and a bench recipe is data. Three owner decisions,
# each written as the bound it has to stay inside:
#
#   * weapons are rare drops — a dropped blade ends the decision to build one;
#   * material comes in handfuls — one ingot off a kill reads as a rounding
#     error;
#   * the benches are built from what a player has on their first day, not
#     from obsidian or forty-five ingots of iron.

_content = re.sub(r"//[^\n]*", " ", re.sub(r"/\*.*?\*/", " ", open(os.path.join(
    WORK, "src/main/java/com/elysium/core/ElysiumContent.java"),
    encoding="utf-8").read(), flags=re.S))

_weapon = re.search(r"WEAPON_CHANCE\s*=\s*([\d.]+)F", _content)
if _weapon is None:
    fail("ElysiumContent has no WEAPON_CHANCE, so the weapon drop rate is a "
         "literal buried in a switch again")
elif float(_weapon.group(1)) > 0.05:
    fail(f"a top-shelf reward is a weapon {float(_weapon.group(1)):.0%} of the "
         f"time. The owner asked for fewer: a dropped blade is the end of the "
         f"decision to forge one, and at that rate the forge loses")

_minimum = re.search(r"MATERIAL_MIN\s*=\s*(\d+)", _content)
if _minimum is None:
    fail("ElysiumContent has no MATERIAL_MIN, so material drops are single "
         "ingots again")
elif int(_minimum.group(1)) < 2:
    fail("a material drop can be a single ingot, which reads as a rounding "
         "error rather than a reason to have fought")

for _entity, _cap in (("ImperialEnforcer", 0.03), ("UnswornRaider", 0.03)):
    _src = open(os.path.join(WORK, "src/main/java/com/elysium/core/entity",
                             _entity + ".java"), encoding="utf-8").read()
    for _chance in re.findall(r"MAINHAND,[^;]*?([\d.]+)F", _src):
        if float(_chance) > _cap:
            fail(f"{_entity} drops its weapon {float(_chance):.1%} of the time, "
                 f"above the {_cap:.0%} the owner's decision allows")

_first_benches = ("reforge_table", "rune_socket_table", "ascension_forge")
_too_dear = {"minecraft:obsidian", "minecraft:crying_obsidian",
             "minecraft:iron_block", "minecraft:diamond", "minecraft:netherite_ingot"}
for _bench in _first_benches:
    _path = os.path.join(RES, f"data/elysium/recipe/{_bench}.json")
    if not os.path.isfile(_path):
        continue                       # reported by the obtainability audit
    _recipe = json.load(open(_path, encoding="utf-8"))
    _used = {v.get("item") for v in _recipe.get("key", {}).values()
             if isinstance(v, dict)}
    for _item in sorted(_used & _too_dear):
        fail(f"{_bench} is built from {_item}. The first benches are stone, "
             f"wood and iron — obsidian is a diamond pickaxe and a trip to "
             f"lava, and it put the bench a player needs first behind the "
             f"things they would need it for")

# A bench may lean on exactly one vanilla station — an anvil for the Reforge
# Table, a smithing table for the Ascension Forge — and never on two, and never
# on two of the same. An anvil is thirty-one iron; a second one is a second
# iron mine, which is the same mistake as the obsidian in a cheaper costume.
# The count is taken off the *pattern*, not the key: a key entry says an anvil
# is involved, the pattern says how many.
_stations = {"minecraft:anvil", "minecraft:chipped_anvil",
             "minecraft:damaged_anvil", "minecraft:smithing_table",
             "minecraft:grindstone"}
for _bench in _first_benches:
    _path = os.path.join(RES, f"data/elysium/recipe/{_bench}.json")
    if not os.path.isfile(_path):
        continue
    _recipe = json.load(open(_path, encoding="utf-8"))
    _symbols = {sym for sym, v in _recipe.get("key", {}).items()
                if isinstance(v, dict) and v.get("item") in _stations}
    _count = sum(row.count(_sym)
                 for row in _recipe.get("pattern", []) for _sym in _symbols)
    if _count > 1:
        _named = ", ".join(sorted(
            _recipe["key"][_sym]["item"] for _sym in _symbols))
        fail(f"{_bench} wants {_count} vanilla stations ({_named}). The owner "
             f"set the ceiling at one per bench: every extra anvil is another "
             f"thirty-one iron in front of the bench that teaches the mod")

# ---------------------------------------------------------------------------
# Rune tiers: every Elysium rune scales, and the forge merges honestly
# ---------------------------------------------------------------------------
_runes_path = os.path.join(WORK, "src/main/java/com/elysium/core/item/ElysiumRunes.java")
_runes_code = strip_java_comments(open(_runes_path, encoding="utf-8").read())
if re.search(r"\.effect\(\s*\(\s*\w+\s*,\s*\w+\s*,\s*\w+\s*\)\s*->", _runes_code):
    fail("an Elysium rune still has a three-argument effect, which never sees "
         "its tier: merged to V it does what it did at I")
if re.search(r"aligned\s*\?\s*1\s*:\s*0", _runes_code):
    fail("a rune picks its potion level with aligned ? 1 : 0, ignoring its "
         "tier. Use ElysiumRuneTiers.amplifier")
# Rune stats are uncapped, by the owner's instruction ("don't cap stats").
# Every potion level goes through the one uncapped rule, and nothing clamps it
# on the way.
_potion_levels = re.findall(r"MobEffects\.\w+,\s*DURATION,\s*([^,]+?\(.*?\)|[^,]+),",
                            _runes_code, re.S)
for _lvl in _potion_levels:
    if not re.search(r"ElysiumRuneTiers\.(amplifier|percentileAmplifier)\(", _lvl) \
            or "Math.min" in _lvl:
        fail(f"a rune's potion level is {_lvl.strip()[:60]!r} rather than one of "
             f"the tier rules in ElysiumRuneTiers. Rune stats are not capped")
        break
if "resistanceAmplifier" in _runes_code:
    fail("a rune still caps Resistance. Rune stats are not capped")
# Resistance is a percentage of damage, so it takes the stat / (stat + b) rule
# (the owner's ruling for percentile effects), not the linear one — which
# would reach level V, all damage, at tier 3 aligned.
_res = re.search(r"MobEffects\.DAMAGE_RESISTANCE,\s*DURATION,\s*(.*?)\)\s*,\s*false",
                 _runes_code, re.S)
if _res is None or "percentileAmplifier(" not in _res.group(1):
    fail("Resistance is granted by the linear tier rule; it is a percentage of "
         "damage and must follow stat / (stat + b) via percentileAmplifier")

_menu_path = os.path.join(WORK, "src/main/java/com/elysium/core/menu/ReforgeTableMenu.java")
_menu_code = strip_java_comments(open(_menu_path, encoding="utf-8").read())


def _menu_body(sig):
    start = _menu_code.find(sig)
    if start < 0:
        return None
    depth = 0
    for i in range(_menu_code.find("{", start), len(_menu_code)):
        if _menu_code[i] == "{":
            depth += 1
        elif _menu_code[i] == "}":
            depth -= 1
            if depth == 0:
                return _menu_code[start:i + 1]
    return None


_perform = _menu_body("public void performAction(")
_merge = _menu_body("private void mergeRunes(")
if _perform is None or _merge is None:
    fail("the forge menu has lost performAction or mergeRunes; runes cannot "
         "be merged")
else:
    _m = _perform.find("mergeRunes(")
    _g = _perform.find("ElysiumGear.of(armor)")
    if _m < 0 or "armor.isEmpty()" not in _perform[:_m] or (0 <= _g < _m):
        fail("rune merging is not reached with the gear slot empty, before any "
             "gear logic; either it never runs or a rune meant for socketing "
             "gets merged instead")
    if "tierOf(rune)" not in _perform:
        fail("socketing ignores the rune's tier: a merged rune goes into gear "
             "as tier 1 and the runes spent merging it are simply lost")
    if "canMerge(" not in _merge or "tierOf(material) == tier" not in _merge:
        fail("mergeRunes does not insist on two of the same tier; a tier-1 "
             "and a tier-4 would make a tier 5")
    if "player.drop(" not in _merge:
        fail("a merged rune that does not fit anywhere is not dropped, so it "
             "is destroyed")

# ---------------------------------------------------------------------------
# Tooltip code is never called from anywhere but a tooltip
# ---------------------------------------------------------------------------
#
# A dedicated server crashed here (crash-2026-09-21_13.17.10-server): the
# Reforge Table's teardown readout called Item.appendHoverText on the server,
# which runs every mod's hook into tooltips, and Relics' hook calls a method
# that only exists in client builds. NoSuchMethodError, server down. It passed
# every singleplayer test, because an integrated server runs inside a client.
#
# So, across all seven modules: the only call to appendHoverText is super's,
# inside an appendHoverText override, and nothing calls getTooltipLines. Server
# code that needs an item's description uses ElysiumSocketable.describe.
_mods_root = os.path.dirname(WORK)
_tooltip_calls = []
for _mod in sorted(os.listdir(_mods_root)):
    _src = os.path.join(_mods_root, _mod, "src/main/java")
    if not _mod.startswith("elysium") or not os.path.isdir(_src):
        continue
    for _dp, _dn, _fn in os.walk(_src):
        for _f in _fn:
            if not _f.endswith(".java"):
                continue
            _code = strip_java_comments(open(os.path.join(_dp, _f), encoding="utf-8").read())
            for _m in re.finditer(r"(\w+(?:\(\))?)\s*\.\s*(appendHoverText|getTooltipLines)\s*\(", _code):
                if _m.group(1) == "super" and _m.group(2) == "appendHoverText":
                    continue
                _tooltip_calls.append(f"{_mod}/{_f}: {_m.group(0).strip()}")
if _tooltip_calls:
    fail(f"tooltip code is called directly at {_tooltip_calls[0]}. On a dedicated "
         f"server that runs every other mod's tooltip hooks, and one of them "
         f"(Relics) crashed the server this way. Use ElysiumSocketable.describe")

_teardown = _menu_body("private void teardown(")
if _teardown is None or ".describe(" not in _teardown \
        or not re.search(r"catch\s*\(\s*RuntimeException\s*\|\s*LinkageError", _teardown):
    fail("the bench's teardown readout is not built with describe() inside a guard "
         "that catches LinkageError; a readout must never be able to take a "
         "server down")

# ---------------------------------------------------------------------------
# Every tag a recipe names resolves to something
# ---------------------------------------------------------------------------
#
# A recipe keyed on a tag nobody defines is not an error to Minecraft. It
# loads, it shows nothing, and it can never be crafted. Every voidglass,
# aetherium and neutronium tool shipped like that — c:ingots/voidglass was
# named by twenty-three recipes and defined by no one.
#
# A tag is accounted for when one of three things is true:
#   * we ship it;
#   * NeoForge ships it (transcribed from data/c/tags/item in the
#     21.1.248 jar — the list is short, and a copy here cannot agree with the
#     code under test by construction);
#   * it belongs to a modded material, which ElysiumMaterials registers
#     unconditionally and gates at runtime on another mod supplying the tag.
_NEOFORGE_ITEM_TAGS = {
    "c:ingots/copper", "c:ingots/gold", "c:ingots/iron", "c:ingots/netherite",
    "c:gems/amethyst", "c:gems/diamond", "c:gems/emerald", "c:gems/lapis",
    "c:gems/prismarine", "c:gems/quartz",
}
_mat_src = open(os.path.join(
    WORK, "src/main/java/com/elysium/core/item/ElysiumMaterials.java"),
    encoding="utf-8").read()
_modded_names = set(re.findall(r'\{"(\w+)",\s*ElysiumElements\.', _mat_src))
_shipped_tags = set()
_modules_root = os.path.dirname(WORK)
for _mod in os.listdir(_modules_root):
    _data = os.path.join(_modules_root, _mod, "src/main/resources/data")
    if not os.path.isdir(_data):
        continue
    for _ns in os.listdir(_data):
        _tagdir = os.path.join(_data, _ns, "tags/item")
        for _dp, _dn, _fn in os.walk(_tagdir):
            for _f in _fn:
                if _f.endswith(".json"):
                    _rel = os.path.relpath(os.path.join(_dp, _f), _tagdir)[:-5].replace(os.sep, "/")
                    _shipped_tags.add(f"{_ns}:{_rel}")
_unresolved = {}
_recipe_dir = os.path.join(RES, "data/elysium/recipe")
for _f in sorted(os.listdir(_recipe_dir)) if os.path.isdir(_recipe_dir) else []:
    if not _f.endswith(".json"):
        continue
    _text = open(os.path.join(_recipe_dir, _f), encoding="utf-8").read()
    for _tag in re.findall(r'"tag"\s*:\s*"([a-z0-9_.-]+:[a-z0-9_./-]+)"', _text):
        if _tag in _shipped_tags or _tag in _NEOFORGE_ITEM_TAGS:
            continue
        if _tag.startswith("minecraft:"):
            continue                    # vanilla's own item tags
        _leaf = _tag.rsplit("/", 1)[-1]
        if _tag.startswith("c:ingots/") and _leaf in _modded_names:
            continue                    # gated at runtime on another mod
        _unresolved.setdefault(_tag, []).append(_f)
if _unresolved:
    _worst = max(_unresolved, key=lambda t: len(_unresolved[t]))
    fail(f"{sum(len(v) for v in _unresolved.values())} recipe(s) name a tag "
         f"nothing defines, starting with {_worst} in "
         f"{len(_unresolved[_worst])} recipe(s) such as "
         f"{_unresolved[_worst][0]}. A recipe on an empty tag loads cleanly and "
         f"can never be crafted")
else:
    print(f"recipe tags        : every tag a recipe names resolves — "
          f"{len(_shipped_tags)} shipped, NeoForge's own, or a gated modded metal")

# ---------------------------------------------------------------------------
# The Extraction Ledger is retired: the Ledger Web is the pack's own
# ---------------------------------------------------------------------------
#
# Until 28 Sep 2026 the skill tree was a Pufferfish's Skills datapack of
# forty-five nodes, generated by tools/gen_skilltree.py — and Pufferfish's
# Skills was never one of the jars the pack ships, so in the pack as shipped
# the tree did not exist. It is replaced by the Ledger Web in the library
# (elysium-lib/validate.py, "The Ledger Web"): four hundred nodes, drawn on
# the character sheet, allocated by the pack's own state. What is held here is
# that nothing of the old tree is left behind to be a second, invisible one.
for _gone, _why in ((os.path.join(RES, "data/elysium/puffish_skills"),
                     "a Pufferfish's Skills datapack, which is a second tree nothing draws"),
                    (os.path.join(WORK, "tools/gen_skilltree.py"),
                     "the generator of the retired tree, which would write it back")):
    if os.path.exists(_gone):
        fail(f"{os.path.relpath(_gone, WORK)} is back: {_why}. The skill tree is the library's Ledger Web now")
_lang_keys_now = json.load(open(os.path.join(RES, "assets/elysium/lang/en_us.json"), encoding="utf-8"))
_ledger_keys = [k for k in _lang_keys_now if k.startswith("elysium.skilltree.")]
if _ledger_keys:
    fail(f"{len(_ledger_keys)} elysium.skilltree.* line(s) are still in the language file, words for a tree "
         f"that is gone (run tools/gen_data.py)")
_command_java = os.path.join(WORK, "src/main/java/com/elysium/core/command/ElysiumCommand.java")
if not os.path.isfile(_command_java)         or 'Commands.literal("tree")' not in open(_command_java, encoding="utf-8").read():
    fail("the /elysium tree command is gone, so the Ledger Web can be reached only by clicking")
else:
    print("ledger web         : the Pufferfish datapack and its generator retired, no words for it left, "
          "and /elysium tree in the command")

print(f"kills and benches : weapons at most 5% of a top-shelf drop, material "
      f"in handfuls, and the three benches built without obsidian, one "
      f"vanilla station apiece")

# ---------------------------------------------------------------------------
# The two humanoids: their own skins, on vanilla's model
# ---------------------------------------------------------------------------
#
# An Imperial Enforcer is a Zombie, which is what gives it its AI, its armour
# rendering and its immunity to the sunlight that would otherwise delete the
# Empire's soldiers at dawn. It also meant it rendered as a zombie: the mod's
# own soldier, in a green shirt, beside ten families of purpose-built mobs.
#
# Fixed by subclassing the renderer for one method. What that cannot survive is
# somebody deleting the texture, or the generator writing it somewhere the
# renderer does not look — both of which are a silent fall back to vanilla's
# zombie, because a missing texture renders as the missing-texture checkerboard
# only if the path is wrong in a way the game notices.

_humanoids = ("imperial_enforcer", "unsworn_raider")
_client = os.path.join(WORK, "src/main/java/com/elysium/core/client/ElysiumClient.java")
if not os.path.isfile(_client):
    fail("ElysiumClient is missing, so nothing registers a renderer and the "
         "first enforcer to spawn crashes the client")
else:
    _client_src = re.sub(r"//[^\n]*", " ",
                         re.sub(r"/\*.*?\*/", " ",
                                open(_client, encoding="utf-8").read(), flags=re.S))
    if "getTextureLocation" not in _client_src:
        fail("no renderer overrides getTextureLocation, so both combatants "
             "render in vanilla's zombie skin again")
    for _name in _humanoids:
        if f'"{_name}"' not in _client_src:
            fail(f"{_name} has no skin named in ElysiumClient, so it renders "
                 f"as a plain zombie")
        _skin = os.path.join(RES, f"assets/elysium/textures/entity/{_name}.png")
        if not os.path.isfile(_skin):
            fail(f"{_name}.png is not in the resources — run regen.sh")
            continue
        from PIL import Image as _Image
        with _Image.open(_skin) as _img:
            if _img.size != (64, 64):
                fail(f"{_name}.png is {_img.size}; a humanoid skin is 64x64 "
                     f"and the model reads fixed offsets into it")
            _opaque = [p for p in _img.convert("RGBA").getdata() if p[3] > 0]
            if not _opaque:
                fail(f"{_name}.png is entirely transparent")
            elif len(_opaque) < 1000:
                fail(f"{_name}.png has only {len(_opaque)} opaque pixels, so "
                     f"most of the model is see-through")

    print(f"humanoid skins    : {len(_humanoids)} at 64x64, each named by the "
          f"renderer that draws it")

# ---------------------------------------------------------------------------
# Gems, sockets and augmenting (SPEC_CLONE P7)
# ---------------------------------------------------------------------------
#
# Elysium's runes are its gems, so everything here is something that happens to
# a rune rather than a second family of stones. What is checked is that the
# loop stays closed: dust comes only from grinding, cutting costs what merging
# costs, a sigil is never spent on a piece it would not change, and a withdrawn
# rune comes back as an item rather than as nothing.

_P7_ITEMS = ["gem_dust", "gem_case", "sigil_of_socketing", "sigil_of_enhancement",
             "sigil_of_withdrawal", "sigil_of_rebirth"]
_P7_BLOCKS = ["gem_cutting_table", "augmenting_table"]
for _name in _P7_ITEMS:
    if _name not in items:
        fail(f"{_name} is drawn and never registered")
    if not os.path.isfile(os.path.join(RES, f"assets/elysium/textures/item/{_name}.png")):
        fail(f"{_name} has no sprite")
for _name in _P7_BLOCKS:
    if _name not in blocks:
        fail(f"the {_name} is drawn and never registered")
    if not os.path.isfile(os.path.join(RES, f"assets/elysium/textures/block/{_name}.png")):
        fail(f"the {_name} has no texture")

if os.path.isfile(os.path.join(RES, "data/elysium/recipe/gem_dust.json")):
    fail("gem dust has a crafting recipe, which is a way around grinding a rune for it")

_augments = os.path.join(WORK, "..",
                         "elysium-lib/src/main/java/com/elysium/lib/item/ElysiumAugments.java")
_cutting = os.path.join(WORK, "src/main/java/com/elysium/core/menu/GemCuttingMenu.java")
_augmenting = os.path.join(WORK, "src/main/java/com/elysium/core/menu/AugmentingMenu.java")
_drops = os.path.join(WORK, "src/main/java/com/elysium/core/event/RuneDropHandler.java")
for _path, _what in ((_augments, "what a sigil does"), (_cutting, "the cutting bench"),
                     (_augmenting, "the augmenting bench"), (_drops, "where runes come from")):
    if not os.path.isfile(_path):
        fail(f"{_what} is gone")
if all(os.path.isfile(_p) for _p in (_augments, _cutting, _augmenting, _drops)):
    _aug_src = open(_augments, encoding="utf-8").read()
    _cut_src = open(_cutting, encoding="utf-8").read()
    _augm_src = open(_augmenting, encoding="utf-8").read()
    _drop_src = open(_drops, encoding="utf-8").read()
    for _sigil in ("socketing", "enhancement", "withdrawal", "rebirth"):
        if f'"{_sigil}"' not in _aug_src:
            fail(f"the sigil of {_sigil} is an item and nothing knows what it does")
        if f"elysium.sigil.{_sigil}.desc" not in json.load(open(
                os.path.join(RES, "assets/elysium/lang/en_us.json"), encoding="utf-8")):
            fail(f"the sigil of {_sigil} has no description, which is the whole of its "
                 f"interface — a player deciding whether to spend it reads that line")
    if "ElysiumAugments.applies(" not in _augm_src:
        fail("the augmenting bench never asks whether the sigil would change the piece, so "
             "a player could spend one on a piece it does nothing to")
    if "result.changed()" not in _augm_src:
        fail("the bench offers a result whether or not anything happened")
    if "result.hasRune()" not in _augm_src:
        fail("a withdrawn rune is never handed back, so the Sigil of Withdrawal destroys "
             "exactly what it exists to save")
    if "ElysiumPurity.cutCost(" not in _cut_src:
        fail("cutting does not cost what the rules say it costs")
    if "ElysiumPurity.canCut(" not in _cut_src:
        fail("the cutting bench does not ask whether a rune can be cut at all, so a rune at "
             "the top of the arithmetic would overflow rather than refuse")
    if "ElysiumTiers.of(" not in _drop_src:
        fail("rune drops are not gated by world tier, so the Frontier would rain runes")
    if "ElysiumPurity.dropChance(" not in _drop_src or "ElysiumPurity.foundPurity(" not in _drop_src:
        fail("rune drops do not read the rules for how often and how good, so both would be "
             "decided somewhere nobody can execute")
    if "instanceof ElysiumRuneItem" not in _drop_src:
        fail("the drop pool is a list rather than the registry, so a rune another mod adds "
             "would never fall")
print(f"gems and sigils   : {len(_P7_ITEMS)} items and {len(_P7_BLOCKS)} benches; dust only from "
      f"grinding, cutting priced like merging, no sigil spent on nothing")

# ---------------------------------------------------------------------------
# What is waiting underground (SPEC_CLONE P9.5, P9.6)
# ---------------------------------------------------------------------------
#
# Two rogue spawners and a cage. Each one has a way of being wrong that the
# compiler has no opinion about: a spawner with no end to it is a chore rather
# than a fight, a spawner placed inside stone is one nobody ever meets, and a
# cage that can be opened from safety is not a decision.
_ROGUE = ["brutal_spawner", "swarm_spawner", "caged_invader"]
for _name in _ROGUE:
    if _name not in blocks:
        fail(f"the {_name} is drawn and never registered")
    if not exists(f"data/elysium/worldgen/placed_feature/{_name}.json"):
        fail(f"the {_name} is never placed in the world, so the only one a player would "
             f"ever see is one they placed themselves")
    else:
        _placed = json.load(open(os.path.join(
            RES, f"data/elysium/worldgen/placed_feature/{_name}.json"), encoding="utf-8"))
        _kinds = {_step.get("type") for _step in _placed.get("placement", [])}
        if "minecraft:rarity_filter" not in _kinds:
            fail(f"the {_name} is placed without a rarity filter, so there would be one in "
                 f"every chunk and meeting one would stop meaning anything")
        if "minecraft:block_predicate_filter" not in _kinds:
            fail(f"the {_name} is placed without asking what is there, so it would appear "
                 f"inside stone where nothing can reach it and nothing it sends can reach out")
    if not exists(f"data/elysium/neoforge/biome_modifier/add_{_name}.json"):
        fail(f"the {_name} has a placed feature and no biome modifier, which is the one "
             f"thing that puts a feature into a world")
_spawner_src = open(os.path.join(
    WORK, "src/main/java/com/elysium/core/block/RogueSpawnerBlockEntity.java"),
    encoding="utf-8").read()
_cage_src = open(os.path.join(
    WORK, "src/main/java/com/elysium/core/block/CagedInvaderBlock.java"), encoding="utf-8").read()
if "wavesLeft" not in _spawner_src or "level.destroyBlock(" not in _spawner_src:
    fail("a rogue spawner never runs out, so clearing one is a chore to suppress rather "
         "than a thing that finishes")
if "getNearestPlayer(" not in _spawner_src:
    fail("a rogue spawner sends creatures at nobody, filling a cave nobody is standing in")
if "ElysiumElites.promote(" not in _spawner_src:
    fail("the brutal spawner sends ordinary creatures, which is what the swarm one is for")
# The override itself, not the word: the method calls its own super, so a bare
# search for the name passes on a cage that no longer overrides anything.
if "public BlockState playerWillDestroy(" not in _cage_src:
    fail("the cage opens by some means other than breaking it, so it could be opened by "
         "accident or from safety — and the whole of what makes it interesting is that a "
         "player has to decide")
if "ElysiumElites.release(" not in _cage_src:
    fail("the cage lets out an ordinary creature, which is a disappointment rather than a "
         "decision")
print(f"underground       : {len(_ROGUE)} things waiting, each rare, each on a floor, and "
      f"the spawners run out")

# ---------------------------------------------------------------------------
# The starting kit (SPEC_CLONE P12.18)
# ---------------------------------------------------------------------------
#
# A loot table rather than a config, because the game already has a format for
# "a list of items, possibly with counts, possibly with conditions" and a pack
# already knows how to edit one.

KIT_SOURCE = os.path.join(JAVA_DIR, "event/ElysiumStartingKit.java")
KIT_TABLE = os.path.join(RES, "data/elysium/loot_table/gameplay/starting_kit.json")
if not os.path.isfile(KIT_SOURCE):
    fail("there is no starting kit: a new player arrives with nothing and no way to "
         "find out what this mod is")
else:
    kit = re.sub(r"//[^\n]*|/\*.*?\*/", " ",
                 open(KIT_SOURCE, encoding="utf-8").read(), flags=re.S)
    if not os.path.isfile(KIT_TABLE):
        fail("the starting kit's loot table does not ship, so the kit is a table id "
             "that resolves to nothing")
    else:
        table = json.load(open(KIT_TABLE, encoding="utf-8"))
        if table.get("type") != "minecraft:gift":
            fail("the starting kit's table is not a gift table, so the parameters the "
                 "kit passes and the ones the table expects are different sets")
        if not table.get("pools"):
            fail("the starting kit's table has no pools, so a new player is given "
                 "nothing and the code runs anyway")
        for pool in table.get("pools", []):
            for entry in pool.get("entries", []):
                name = entry.get("name", "")
                if name.startswith("elysium:") and name.split(":", 1)[1] not in items:
                    fail(f"the starting kit hands out {name}, which this mod does not "
                         f"register")
    if "PlayerLoggedInEvent" not in kit:
        fail("the starting kit is never given: nothing listens for a player arriving")
    if "player.getData(GIVEN.get())" not in kit or "player.setData(GIVEN.get(), true)" not in kit:
        fail("the starting kit is not remembered, so a player is handed one every time "
             "they log in")
    if "copyOnDeath()" not in kit:
        fail("the record of having been given a kit is lost on death, so dying is how "
             "you get another one")
    if "getLootTable(KIT)" not in kit:
        fail("the kit's contents are decided in Java rather than by the table, so a "
             "pack cannot change or empty it")
    if "player.drop(" not in kit:
        fail("a kit handed to a player with a full inventory is silently destroyed")
    print("starting kit       : one gift table, given once, remembered through death")

# ---------------------------------------------------------------------------
# /elysium tier (SPEC_CLONE P12.15)
# ---------------------------------------------------------------------------
#
# World tier is the one number every other system scales against — mobs, loot,
# enchanting ceilings — and until this it was the one number a player could not
# see anywhere.

COMMAND = os.path.join(JAVA_DIR, "command/ElysiumCommand.java")
if not os.path.isfile(COMMAND):
    fail("the /elysium command is missing")
else:
    command = re.sub(r"//[^\n]*|/\*.*?\*/", " ",
                     open(COMMAND, encoding="utf-8").read(), flags=re.S)
    if 'Commands.literal("tier")' not in command:
        fail("there is no way to see your own world tier, which is the number every "
             "other system in this mod is multiplied by")
    tier_block = command[command.find('Commands.literal("tier")'):]
    if "ElysiumTiers.of(player)" not in tier_block:
        fail("the tier command works the tier out itself rather than asking the library, "
             "so it can disagree with every system that uses it")
    if "floorOf(player)" not in tier_block:
        fail("the tier command reports a tier and not where it came from — a player "
             "whose tier is held up by a world floor cannot tell")
    if "ElysiumWorldTier.share(" not in tier_block:
        fail("the tier command does not say what the tier is worth, which is the only "
             "part a player can act on")
    for key in ("elysium.command.tier.header", "elysium.command.tier.from",
                "elysium.command.tier.share"):
        if key not in lang:
            fail(f"the tier command prints {key} and nothing translates it")
    print("the tier command   : the tier, where it came from, and what it is worth")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)

print("all resource checks passed")
