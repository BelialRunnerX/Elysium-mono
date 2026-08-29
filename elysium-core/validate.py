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
ITEM_HELPERS = r'(?<!\.)\b(?:register|item|simpleItem|armour|neutroniumArmour|weapon|tool)'
items = re.findall(ITEM_HELPERS + r'\(\s*"([a-z_]+)"', source)

# blockItem() derives the item name from the block's registry name.
items = set(items) | set(blocks)

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

SHAPES = ["hammer", "broadaxe", "scythe", "spear"]
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
    r'(?:register|item|simpleItem|armour|neutroniumArmour|weapon|tool)\(\s*"([a-z_]+)"',
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
for filename in os.listdir(os.path.join(RES, "data/elysium/recipe")):
    recipe = json.load(open(os.path.join(RES, "data/elysium/recipe", filename), encoding="utf-8"))
    result = recipe["result"]["id"].split(":", 1)[1]
    ingredients = set()
    for value in list((recipe.get("key") or {}).values()) + list(recipe.get("ingredients") or []):
        for identifier in ingredient_ids(value):
            if identifier.startswith("elysium:"):
                ingredients.add(identifier.split(":", 1)[1])
    recipes.append((filename, result, ingredients))

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
print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)

print("all resource checks passed")
