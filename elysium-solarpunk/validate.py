#!/usr/bin/env python3
"""
Cross-check the solarpunk set against the resources it ships.

    python3 validate.py          (from inside elysium-solarpunk/)

<h2>What this is looking for</h2>

Almost everything this mod registers is a block whose entire job is to look
right, and every way one of them can be broken is silent.
A missing loot table is a block that vanishes when mined. A missing item model
is a purple cube in the hand and the correct block on the ground. A missing
language entry is `block.elysiumsolarpunk.mosaic_tile` in the creative tab. A
block with no block item is registered, placed by nothing, and reachable only
with `/setblock`. None of that fails a build, appears in a log, or shows up in
a screenshot of the one block somebody happened to test.

<h2>Two kinds of check, kept apart on purpose</h2>

<b>Structural</b> checks read only the shipped files — the Java that the game
registers from, the JSON the game loads, the PNGs it samples. They do not
import the generator. That matters: a validator that asks the generator what
should be there and then asks the generator whether it is there is grading its
own homework, and will pass cheerfully while the shipped tree is empty.

<b>Drift</b> checks do import the generator, and exist for the opposite
failure: a hand-edit to a generated file, which survives until the next regen
silently reverts it. Those are marked where they appear.

<h2>The white stones</h2>

Three of them are mined rather than crafted, which means three files have to
agree before they exist in a world at all — a configured feature, a placed
feature and a biome modifier, naming each other by id with nothing checking the
references. A broken link there is not an error a player sees; it is a stone
that is in the creative tab and nowhere in the ground. The chain is walked end
to end below, and only a block that survives the walk counts as obtainable.

Twenty-four more are a stone face with a metal pattern composed over it. That is
the right way to build twenty-four blocks and it has one failure mode worth
guarding: an overlay that runs, draws nothing visible, and leaves a block called
"Gold Filigree White Marble" that is a plain white square.

<h2>The machines</h2>

Eight blocks are shaped like machines. Two of them work; the rest are decoration
that is meant to stop being decoration, and what each still owes is recorded in
three places — `MACHINES.md`, the classes under `block/pending/`, and the
tooltip the player reads. The point of three copies is that they can disagree.

Being in *neither* state, or in both, is the failure that matters most and the
easiest to reach: half a graduation leaves a finished machine still claiming it
owes work, or delists one that was never written. So the section at the bottom
checks that every machine is exactly one of the two, that a graduated one has
shed every marker of being a prop and gained a block entity and an energy
capability, and that a pending one still carries all three of its markers
saying the same sentence.
"""
import json
import os
import pathlib
import re
import sys

from PIL import Image

WORK = pathlib.Path(__file__).resolve().parent
RES = WORK / "src/main/resources"
JAVA = WORK / "src/main/java/com/elysium/solarpunk"
NS = "elysiumsolarpunk"

problems = []


def fail(msg):
    problems.append(msg)


def load(rel):
    with open(RES / rel, encoding="utf-8") as handle:
        return json.load(handle)


def exists(rel):
    return (RES / rel).exists()


def read_java(rel):
    path = JAVA / rel
    if not path.is_file():
        fail(f"{rel} is missing")
        return ""
    return path.read_text(encoding="utf-8")


# ===========================================================================
# The table, read out of the Java the game actually registers from
# ===========================================================================

CONTENT = read_java("SolarpunkContent.java")
if not CONTENT:
    print("no SolarpunkContent.java — nothing to validate")
    sys.exit(1)

# BLOCKS.register("id", () -> new SomeBlock(
BLOCK_PATTERN = re.compile(
    r'BLOCKS\.register\(\s*"(\w+)",\s*\(\)\s*->\s*\n?\s*new\s+(\w+)\(')
# item("id", () -> new SomeItem(    ... up to the closing of the call
ITEM_PATTERN = re.compile(
    r'item\(\s*"(\w+)",\s*\(\)\s*->\s*new\s+(\w+)\((.*?)\)\);', re.S)

blocks = BLOCK_PATTERN.findall(CONTENT)          # [(id, java class), ...]
items = [(m.group(1), m.group(2), m.group(3)) for m in ITEM_PATTERN.finditer(CONTENT)]

block_ids = [block_id for block_id, _ in blocks]
block_class = dict(blocks)
item_ids = [item_id for item_id, _, _ in items]

if not blocks or not items:
    print("read no registrations out of SolarpunkContent.java — the shape of the "
          "generated file has changed and every check below would pass by "
          "finding nothing")
    sys.exit(1)

# A table that silently empties is the failure this file cannot otherwise see:
# every "for each block" check below passes trivially over an empty list.
if len(block_ids) < 25:
    fail(f"only {len(block_ids)} blocks are registered. The set is supposed to be "
         f"a building *set*; this is small enough that the table has probably "
         f"lost entries rather than been trimmed")

for name, ids in (("block", block_ids), ("item", item_ids)):
    duplicates = sorted({i for i in ids if ids.count(i) > 1})
    if duplicates:
        fail(f"{name} ids registered more than once: {duplicates}. The second "
             f"registration throws at startup")

print(f"registered         : {len(block_ids)} blocks, {len(item_ids)} items")


# The generator, imported once and used only where the file says so.
#
# The structural checks below deliberately do not touch it — a validator that
# asks the generator what should exist and then asks the generator whether it
# exists is grading its own homework. What it is for is the opposite question:
# which of two states a machine is meant to be in, and which items a machine is
# meant to make. Those are facts about the *design*, not about the shipped tree,
# and there is nowhere else to read them from.
sys.path.insert(0, str(WORK / "tools"))
try:
    import gen_solarpunk
except Exception as exc:                                # pragma: no cover
    gen_solarpunk = None
    fail(f"tools/gen_solarpunk.py will not import ({exc}), so nothing can be "
         f"regenerated and every generated file is now hand-maintained")


# Which blocks are which kind, from the class the registration names. Derived
# from the shipped Java rather than from the generator's table, so a block that
# was re-registered as the wrong type is visible here.
MACHINE_CLASS = re.compile(r"^[A-Z]\w*Block$")

# A third kind, between a plain block and a machine: a building block that
# brought its own behaviour. The power conduit is one — no facing, no block
# entity, no screen, and a use handler that prints the grid.
#
# It has to be named somewhere, because from the shipped Java alone
# `new PowerConduitBlock(...)` is indistinguishable from a machine, and a
# machine that is neither working nor pending is a real failure worth keeping.
# Named from the generator's table and then checked back against the Java, in
# both directions, so neither side can quietly acquire or lose one.
behaviour_blocks = ({entry["id"]: entry["block_class"].rsplit(".", 1)[1]
                     for entry in gen_solarpunk.BUILDING
                     if entry.get("block_class")}
                    if gen_solarpunk is not None else {})
_shipped_classes = dict(blocks)
for _block_id, _expected_class in behaviour_blocks.items():
    if _shipped_classes.get(_block_id) != _expected_class:
        fail(f"{_block_id} is listed as a building block with its own "
             f"behaviour ({_expected_class}) and the shipped Java registers "
             f"{_shipped_classes.get(_block_id)!r}")

machines = [block_id for block_id, cls in blocks
            if cls not in ("Block", "StairBlock", "SlabBlock", "WallBlock")
            and MACHINE_CLASS.match(cls)
            and block_id not in behaviour_blocks]
stairs = [block_id for block_id, cls in blocks if cls == "StairBlock"]
slabs = [block_id for block_id, cls in blocks if cls == "SlabBlock"]
walls = [block_id for block_id, cls in blocks if cls == "WallBlock"]

print(f"shapes             : {len(stairs)} stairs, {len(slabs)} slabs, "
      f"{len(walls)} walls, {len(machines)} machines, "
      f"{len(behaviour_blocks)} building block(s) with behaviour")


# ---------------------------------------------------------------------------
# Every block needs an item, or it is unobtainable
# ---------------------------------------------------------------------------

for block_id in block_ids:
    if block_id not in item_ids:
        fail(f"{block_id} is a registered block with no block item. It exists in "
             f"the registry, is in no creative tab, cannot be crafted into a "
             f"hand, and can only be placed with /setblock")

ingredients = [item_id for item_id in item_ids if item_id not in block_ids]
print(f"ingredients        : {len(ingredients)} — {', '.join(sorted(ingredients))}")


# ===========================================================================
# Resources
# ===========================================================================

for block_id in block_ids:
    for rel, what in (
            (f"assets/{NS}/blockstates/{block_id}.json", "blockstate"),
            (f"assets/{NS}/models/item/{block_id}.json", "item model"),
            (f"data/{NS}/loot_table/blocks/{block_id}.json", "loot table")):
        if not exists(rel):
            fail(f"{block_id} has no {what} ({rel})")

for item_id in ingredients:
    if not exists(f"assets/{NS}/models/item/{item_id}.json"):
        fail(f"{item_id} has no item model, so it renders as the missing-texture "
             f"cube in the hand while its recipe works perfectly")

print(f"blockstates + loot : checked for {len(block_ids)} blocks")


# ---------------------------------------------------------------------------
# Every model resolves, and every texture it names is on disk
# ---------------------------------------------------------------------------
#
# A model naming a texture that is not there is not a crash: the block renders
# with the missing-texture check pattern on exactly the faces that named it, so
# a typo in "top" leaves five correct faces and one wrong one.

model_dir = RES / f"assets/{NS}/models"
referenced_textures = set()
model_count = 0

for path in sorted(model_dir.rglob("*.json")):
    model_count += 1
    model = json.loads(path.read_text(encoding="utf-8"))
    for key, value in model.get("textures", {}).items():
        if not isinstance(value, str) or value.startswith("#"):
            continue
        referenced_textures.add(value)
    parent = model.get("parent", "")
    if parent.startswith(f"{NS}:"):
        target = RES / f"assets/{NS}/models/{parent.split(':', 1)[1]}.json"
        if not target.exists():
            fail(f"{path.name} inherits from {parent}, which does not exist — the "
                 f"model fails to load and the block renders as nothing")

missing_textures = []
for reference in sorted(referenced_textures):
    namespace, path = reference.split(":", 1)
    if namespace != NS:
        continue                        # vanilla textures are vanilla's problem
    if not exists(f"assets/{NS}/textures/{path}.png"):
        missing_textures.append(reference)

for reference in missing_textures:
    fail(f"{reference} is named by a model and is not in the texture tree")

print(f"models             : {model_count} models, "
      f"{len([r for r in referenced_textures if r.startswith(NS)])} own textures "
      f"referenced, {len(missing_textures)} missing")


# ---------------------------------------------------------------------------
# Textures: the right size, and the right kind of transparency
# ---------------------------------------------------------------------------
#
# The transparency check is the interesting one. A block model declares a
# render type and the texture has to match it:
#
#   * cutout rounds every pixel's alpha to fully on or fully off. A texture
#     drawn at partial alpha under cutout renders *solid* — the glazing simply
#     disappears and nobody can tell from the code that it did.
#   * a solid block whose texture has holes in it renders those holes as
#     opaque black-ish nothing, because the solid path never blends.
#
# Both are invisible until somebody places the block, and neither produces a
# log line.

render_of = {}
for path in sorted((model_dir / "block").glob("*.json")):
    model = json.loads(path.read_text(encoding="utf-8"))
    if "render_type" in model:
        render_of[path.stem] = model["render_type"].split(":")[-1]

texture_dir = RES / f"assets/{NS}/textures"
texture_count = 0
for path in sorted(texture_dir.rglob("*.png")):
    texture_count += 1
    image = Image.open(path).convert("RGBA")
    if image.size != (16, 16):
        fail(f"{path.name} is {image.size[0]}x{image.size[1]}; every texture here "
             f"is a 16x16 and the models assume it")
        continue
    # The alpha channel as raw bytes: one set() over 256 values, and it does
    # not go through the per-pixel accessor Pillow has deprecated.
    alphas = set(image.getchannel("A").tobytes())
    if path.parent.name != "block":
        continue

    render = render_of.get(path.stem)
    # Stairs, slabs and walls carry the base block's render type, and machine
    # faces are named <machine>_front and so on.
    if render is None:
        for base, value in render_of.items():
            if path.stem.startswith(base):
                render = value
                break

    partial = {a for a in alphas if 0 < a < 255}
    if render == "cutout":
        if 0 not in alphas:
            fail(f"{path.name} is drawn for a cutout block and has no fully "
                 f"transparent pixel, so there is nothing to cut out and the "
                 f"extra render pass buys nothing")
        if partial:
            fail(f"{path.name} is cutout and has partial alpha {sorted(partial)}, "
                 f"which cutout rounds to fully opaque — those pixels will not "
                 f"look the way they were drawn")
    elif render == "translucent":
        if not partial:
            fail(f"{path.name} declares translucent and has no partial alpha; "
                 f"either it wants cutout or the texture lost its glass")
    elif render is None and alphas != {255}:
        fail(f"{path.name} has transparent pixels and its block declares no "
             f"render type, so the solid renderer fills them in and the holes "
             f"come out opaque")

print(f"textures           : {texture_count} PNGs, all 16x16, "
      f"{len(render_of)} blocks with an explicit render type")


# ---------------------------------------------------------------------------
# Recipes: everything is craftable, and out of things that exist
# ---------------------------------------------------------------------------

recipe_dir = RES / f"data/{NS}/recipe"
recipe_results = {}
recipe_count = 0

# The mod's own recipe types, which are not shaped or shapeless and are checked
# in their own section below rather than squeezed into vanilla's shapes.
# Read from the generator rather than listed here. A third machine was added
# and this line still named two, so every farming recipe fell through to the
# vanilla-shape branch and crashed on a "result" key it does not have. A
# hard-coded copy of a table that already exists is a copy that will go stale.
def _process_types():
    """Which recipe types this mod's own machines read.

    From the generator when it can be imported, because that is the table the
    recipes are written from. From the Java registration when it cannot, so a
    generator that is temporarily unparseable produces a clear failure rather
    than a stack trace three hundred lines later — which is what happened the
    first time: the import failed, this set came back empty, every farming
    recipe fell through to the vanilla-shape branch, and the validator died on
    a missing "ingredients" key with nothing pointing at the real cause.
    """
    if gen_solarpunk is not None:
        return {f"{NS}:{name}" for name in gen_solarpunk.RECIPE_TYPES.values()}
    registry = JAVA / "recipe/SolarpunkRecipes.java"
    if registry.is_file():
        return {f"{NS}:{name}" for name in
                re.findall(r'process\("([a-z_]+)"\)',
                           registry.read_text(encoding="utf-8"))}
    return set()


PROCESS_TYPES = _process_types()
process_recipes = {}

for path in sorted(recipe_dir.glob("*.json")):
    recipe = json.loads(path.read_text(encoding="utf-8"))
    if recipe.get("type") in PROCESS_TYPES:
        process_recipes[path.stem] = recipe
        continue
    recipe_count += 1
    result = recipe.get("result", {}).get("id")
    recipe_results.setdefault(result, []).append(path.stem)

    used = []
    if recipe["type"].endswith("shaped"):
        used = [v["item"] for v in recipe["key"].values()]
        width = {len(row) for row in recipe["pattern"]}
        if len(width) != 1:
            fail(f"{path.name} has rows of different widths {sorted(width)}; the "
                 f"recipe will not load")
        keys_used = {c for row in recipe["pattern"] for c in row if c != " "}
        declared = set(recipe["key"])
        if keys_used - declared:
            fail(f"{path.name} uses {sorted(keys_used - declared)} in its pattern "
                 f"with no key for it")
        if declared - keys_used:
            fail(f"{path.name} declares keys {sorted(declared - keys_used)} that "
                 f"its pattern never uses")
    else:
        used = [v["item"] for v in recipe["ingredients"]]

    for ingredient in used + ([result] if result else []):
        if ingredient and ingredient.startswith(f"{NS}:"):
            name = ingredient.split(":", 1)[1]
            if name not in item_ids:
                fail(f"{path.name} names {ingredient}, which this mod does not "
                     f"register — the recipe silently fails to load")

craftable = {r.split(":", 1)[1] for r in recipe_results if r and r.startswith(f"{NS}:")}


# ---------------------------------------------------------------------------
# Worldgen: the other way a block gets into a player's hands
# ---------------------------------------------------------------------------
#
# Three files have to agree before a block appears in a world, and they name
# each other by id with nothing checking the references. A biome modifier
# pointing at a placed feature that does not exist logs a datapack error most
# players never read and then simply generates nothing; a configured feature
# naming an unregistered block does the same. Either way the stone is in the
# creative tab, has a recipe-shaped hole where its recipe would be, and cannot
# be found anywhere in the world.
#
# So the chain is walked end to end, and only a block that survives the whole
# walk counts as obtainable below.

def material_rgb(value):
    """A palette entry as an (r, g, b) tuple, for comparing against pixels."""
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def mineable_tags():
    """Every block this mod puts in a mineable tag."""
    values = set()
    for tool in ("pickaxe", "axe"):
        path = RES / f"data/minecraft/tags/block/mineable/{tool}.json"
        if path.is_file():
            values |= set(json.loads(path.read_text(encoding="utf-8"))
                          .get("values", []))
    return values


def walk_worldgen():
    """{block id: how it is placed}, for every block the chain really places."""
    placed = {}

    configured_dir = RES / f"data/{NS}/worldgen/configured_feature"
    placed_dir = RES / f"data/{NS}/worldgen/placed_feature"
    modifier_dir = RES / f"data/{NS}/neoforge/biome_modifier"

    # configured feature -> the blocks it places
    produces = {}
    for path in sorted(configured_dir.glob("*.json")) if configured_dir.is_dir() else []:
        config = json.loads(path.read_text(encoding="utf-8")).get("config", {})
        names = set()
        for target in config.get("targets", []):
            name = target.get("state", {}).get("Name")
            if name:
                names.add(name)
        if not names:
            fail(f"worldgen/configured_feature/{path.name} places no block at "
                 f"all — it will generate nothing, silently")
        for name in names:
            if name.startswith(f"{NS}:") and name.split(":", 1)[1] not in block_ids:
                fail(f"worldgen/configured_feature/{path.name} places {name}, "
                     f"which this mod does not register. The feature loads and "
                     f"generates nothing")
        produces[f"{NS}:{path.stem}"] = names

    # placed feature -> the configured feature it runs
    runs = {}
    for path in sorted(placed_dir.glob("*.json")) if placed_dir.is_dir() else []:
        data = json.loads(path.read_text(encoding="utf-8"))
        feature = data.get("feature")
        if feature not in produces:
            fail(f"worldgen/placed_feature/{path.name} runs {feature}, which is "
                 f"not a configured feature this mod ships")
            continue
        if not data.get("placement"):
            fail(f"worldgen/placed_feature/{path.name} has no placement rules, "
                 f"so it is never placed anywhere")
        runs[f"{NS}:{path.stem}"] = feature

    # biome modifier -> the placed feature it adds
    for path in sorted(modifier_dir.glob("*.json")) if modifier_dir.is_dir() else []:
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("type") != "neoforge:add_features":
            continue
        features = data.get("features")
        for feature in ([features] if isinstance(features, str) else features or []):
            if feature not in runs:
                fail(f"neoforge/biome_modifier/{path.name} adds {feature}, "
                     f"which is not a placed feature this mod ships — nothing "
                     f"is added to any biome")
                continue
            for name in produces[runs[feature]]:
                if name.startswith(f"{NS}:"):
                    placed[name.split(":", 1)[1]] = data.get("biomes", "?")
    return placed


generated = walk_worldgen()

# The third way in: a machine makes it.
#
# Read from the generator's table rather than inferred, and then checked
# against the Java — because "a machine makes it" is only an answer if the
# machine exists *and works*. An item sourced from a block that is still a prop
# is an item with no source at all, and it would be the easiest thing in the
# world to claim otherwise.
machine_made = {}
if gen_solarpunk is not None:
    for entry in gen_solarpunk.INGREDIENTS:
        if "made_by" in entry:
            machine_made[entry["id"]] = entry["made_by"]

for item_id, maker in machine_made.items():
    if maker not in [w["id"] for w in gen_solarpunk.WORKING]:
        fail(f"{item_id} says it is made by {maker}, which is not a machine "
             f"that works. Either it is a prop, in which case nothing makes "
             f"the item, or the name is wrong")

for item_id in item_ids:
    if (item_id not in craftable and item_id not in generated
            and item_id not in machine_made):
        fail(f"{item_id} has no recipe, does not generate in any world, and no "
             f"machine makes it. It is in the creative tab and nowhere else")

# The reverse: a block that generates and cannot be picked up. Stone that
# spawns in a cliff face and drops nothing when mined is worse than stone that
# was never there, because the player spends the trip.
for block_id in sorted(generated):
    if not exists(f"data/{NS}/loot_table/blocks/{block_id}.json"):
        fail(f"{block_id} generates in the world and has no loot table, so it "
             f"is mined into nothing")
    if f"{NS}:{block_id}" not in mineable_tags():
        fail(f"{block_id} generates in the world and is in no mineable tag")

print(f"recipes            : {recipe_count} files, "
      f"{len(craftable)} of {len(item_ids)} items craftable")
print(f"worldgen           : {len(generated)} block(s) generate, every feature "
      f"resolved from biome modifier to block")


# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

lang = load(f"assets/{NS}/lang/en_us.json")

for block_id in block_ids:
    if f"block.{NS}.{block_id}" not in lang:
        fail(f"{block_id} has no name, so it shows as block.{NS}.{block_id}")
for item_id in ingredients:
    if f"item.{NS}.{item_id}" not in lang:
        fail(f"{item_id} has no name")
if f"itemGroup.{NS}" not in lang:
    fail("the creative tab has no name")

# Every tooltip key the Java asks for. These are the strings passed to
# DescribedBlockItem and DescribedItem, and a missing one renders as the key.
tooltip_keys = set(re.findall(r'"(' + NS + r'\.tooltip\.\w+)"', CONTENT))
for key in sorted(tooltip_keys):
    if key not in lang:
        fail(f"{key} is asked for in SolarpunkContent.java and is not in the "
             f"language file, so the tooltip reads as the key")

unused = {k for k in lang if k.startswith(f"{NS}.tooltip.")} - tooltip_keys
if unused:
    fail(f"language file carries tooltip keys nothing asks for: {sorted(unused)}. "
         f"Usually the block was renamed and the old string stayed")

print(f"language           : {len(lang)} entries, {len(tooltip_keys)} tooltip keys "
      f"all present")


# ---------------------------------------------------------------------------
# Tags
# ---------------------------------------------------------------------------

def tag_values(rel):
    if not exists(rel):
        fail(f"{rel} is missing")
        return set()
    data = load(rel)
    if data.get("replace"):
        fail(f'{rel} sets "replace": true, which throws away every other mod\'s '
             f'entries in that tag')
    return {v for v in data.get("values", [])}


mineable = set()
for tool in ("pickaxe", "axe"):
    mineable |= tag_values(f"data/minecraft/tags/block/mineable/{tool}.json")

# Bloom Glass is the deliberate exception: glass is hand-breakable in vanilla
# and a lamp you need a pickaxe for is a lamp you place once.
TOOLLESS = {"bloom_glass"}
for block_id in block_ids:
    listed = f"{NS}:{block_id}" in mineable
    if block_id in TOOLLESS:
        if listed:
            fail(f"{block_id} is in a mineable tag but is meant to be breakable "
                 f"by hand")
    elif not listed:
        fail(f"{block_id} is in no mineable tag, so it takes the default speed "
             f"and drops nothing to the right tool")

wall_tag = tag_values("data/minecraft/tags/block/walls.json")
for block_id in walls:
    if f"{NS}:{block_id}" not in wall_tag:
        fail(f"{block_id} is a WallBlock outside #minecraft:walls. WallBlock reads "
             f"that tag to decide what to connect to, so it will stand alone "
             f"beside its own kind")
if len(wall_tag) != len(walls):
    fail(f"#minecraft:walls lists {len(wall_tag)} of this mod's blocks and "
         f"{len(walls)} are WallBlocks")

for label, ids, rel in (("stairs", stairs, "stairs"), ("slabs", slabs, "slabs")):
    values = tag_values(f"data/minecraft/tags/block/{rel}.json")
    if len(values) != len(ids):
        fail(f"#minecraft:{rel} lists {len(values)} blocks and {len(ids)} are "
             f"registered as that shape")

print(f"tags               : {len(mineable)} mineable, {len(wall_tag)} walls, "
      f"{len(TOOLLESS)} deliberate exception(s)")


# ---------------------------------------------------------------------------
# Independence
# ---------------------------------------------------------------------------
#
# The whole argument for this being its own mod, and for neoforge.mods.toml
# carrying no elysiumlib dependency, is that the set needs nothing else in
# Elysium. One import is all it would take for that to stop being true while
# the toml still says it is — and the failure lands on a player who installed
# this mod on its own and got a crash about a missing class.

foreign = []
for path in sorted(JAVA.rglob("*.java")):
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.startswith("import "):
            continue
        target = line[len("import "):].rstrip(";").replace("static ", "")
        if target.startswith("com.elysium.") and not target.startswith(
                "com.elysium.solarpunk."):
            foreign.append((path.name, target))

for filename, target in foreign:
    fail(f"{filename} imports {target}. This mod declares no dependency on any "
         f"other Elysium mod, so installed on its own that is a crash at class "
         f"load. Either drop the import or add the dependency to "
         f"neoforge.mods.toml and say why")

toml = (WORK / "src/main/resources/META-INF/neoforge.mods.toml").read_text(encoding="utf-8")
declared = set(re.findall(r'modId="(\w+)"', toml)) - {NS}
if declared != {"neoforge", "minecraft"}:
    fail(f"neoforge.mods.toml declares dependencies {sorted(declared)}; this mod "
         f"is meant to depend on neoforge and minecraft and nothing else")

print(f"independence       : 0 imports from other Elysium mods, "
      f"{len(declared)} declared dependencies")


# ===========================================================================
# The machines, and the three places their unfinished state is written down
# ===========================================================================

PENDING_DIR = JAVA / "block/pending"
manifest_path = WORK / "MACHINES.md"

# --- 1. the classes --------------------------------------------------------

pending_classes = {}
if not PENDING_DIR.is_dir():
    fail("block/pending/ does not exist. That directory *is* the note that these "
         "blocks are unfinished; without it the marker is one interface nobody "
         "will look for")
else:
    for path in sorted(PENDING_DIR.glob("*.java")):
        if path.stem == "PendingMachineBlock":
            continue
        source = path.read_text(encoding="utf-8")
        if "extends PendingMachineBlock" not in source:
            fail(f"{path.name} is in block/pending/ and does not extend "
                 f"PendingMachineBlock, so it carries no marker and appears on "
                 f"no list")
            continue
        owed = re.search(r'super\(properties,\s*"([^"]+)"\)', source)
        if not owed:
            fail(f"{path.name} does not pass a sentence to super(), so "
                 f"PendingMachine.owes() returns nothing and the tooltip is blank")
            continue
        pending_classes[path.stem] = owed.group(1)

# --- 2. the manifest -------------------------------------------------------

manifest = {}
if not manifest_path.is_file():
    fail("MACHINES.md is missing. It is the copy of this list that somebody "
         "reading the repository rather than the code will find")
else:
    text = manifest_path.read_text(encoding="utf-8")
    # Only the rows after the OUTSTANDING marker.
    #
    # The parser used to read every table row in the file. On the day the last
    # machine graduated, the manifest gained a "what the machines do" table
    # listing all eight working ones — and every one of them was read back as a
    # pending machine, so the validator reported the finished set as eight
    # unfinished ones. A document is allowed to contain more than one table; a
    # parser that assumes otherwise is reading a layout rather than a fact.
    _, marker, outstanding = text.partition("<!-- OUTSTANDING:")
    if marker:
        for row in re.finditer(
                r"^\|\s*`(\w+)`\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|$",
                outstanding, re.M):
            manifest[row.group(1)] = row.group(3)

# --- 3. the registrations and the tooltip ----------------------------------


def strip_comments(source):
    """Java source with every comment removed.

    Written because a check of mine once passed on deliberately broken code: the
    phrase it searched for appeared in the javadoc explaining the rule. A
    validator that reads prose is a validator that can be satisfied by writing
    about the thing instead of doing it.
    """
    source = re.sub(r"/\*.*?\*/", " ", source, flags=re.S)
    return re.sub(r"//[^\n]*", " ", source)


def method_body(source, signature):
    """The body of one method, or None when it is not declared.

    Comment-stripped source in, so a phrase in a javadoc cannot satisfy a
    caller. Ends at the first line that closes at method indentation.
    """
    at = source.find(signature)
    if at < 0:
        return None
    end = source.find("\n    }", at)
    return source[at:end if end >= 0 else len(source)]


def calls(source, signature, callee):
    """Whether one method actually calls another.

    This exists because the same mistake has now been made three times in this
    file: a check written as `if "thing(" not in source` finds the *declaration*
    of `thing` and passes when every call to it has been deleted. A guard that a
    method exists is not a guard that anything uses it, and the two look
    identical at a glance.

    Returns False when the method is not declared at all, which is also a
    failure worth reporting — the caller says which.
    """
    body = method_body(source, signature)
    return body is not None and callee in body


def camel(block_id):
    return "".join(part.capitalize() for part in block_id.split("_"))


# What a machine says it is offering, and what the platform has to be told.
# Keyed on the accessor because that is the machine's own declaration: a private
# field is internal, a public getter is a promise that something outside can
# reach it.
# The pattern matches a no-argument public *declaration*, not a mention. The
# first version of this searched for the bare name and reported the bioreactor
# for exposing a tank it does not have — it calls getTanks() on somebody else's
# handler. A check that fires on a method call is a check that will be silenced
# by renaming a local variable.
MACHINE_STORES = [
    ("getEnergy", "EnergyStorage", "power buffer"),
    ("getOutput", "ItemHandler", "output slot"),
    ("getInventory", "ItemHandler", "slots"),
    ("getTank", "FluidHandler", "tank"),
]


def declares(source, method):
    """Whether this source declares `public <something> method()`."""
    return re.search(r"public\s+[\w.$<>,?\[\]\s]+\s" + method + r"\s*\(\s*\)",
                     source) is not None


def with_inherited(class_name, seen=None):
    """One class's source plus every machine superclass above it.

    Written because the first version of this check read only the entity's own
    file, and every one of these machines inherits getEnergy() from
    SolarpunkMachineEntity — so deleting a capstan's energy registration passed
    silently. A check that cannot see an inherited member is a check that only
    guards the members a class happens to have written down twice.
    """
    seen = seen or set()
    if class_name in seen:
        return ""                       # a cycle would not compile, but do not hang
    seen.add(class_name)
    path = JAVA / f"machine/{class_name}.java"
    if not path.is_file():
        return ""
    source = strip_comments(path.read_text(encoding="utf-8"))
    parent = re.search(r"class\s+" + class_name + r"\s+extends\s+(\w+)", source)
    if parent:
        source += "\n" + with_inherited(parent.group(1), seen)
    return source


def class_for(block_id):
    return "".join(part.capitalize() for part in block_id.split("_")) + "Block"


# Which of the two states each machine-shaped block is meant to be in. Read
# from the design, then checked against the shipped tree in both directions.
working = [entry["id"] for entry in gen_solarpunk.WORKING] \
    if gen_solarpunk is not None else []
pending = [block_id for block_id in machines if block_id not in working]

# The one check that makes the other two trustworthy: every machine is exactly
# one of the two things. A block that is written *and* still listed as owing
# behaviour tells a reader the work is outstanding when it is done; one that is
# delisted with no implementation behind it tells them the opposite. Both are
# the manifest lying, in opposite directions, and both are easy to reach by
# doing half a graduation.
for block_id in working:
    if block_id not in machines:
        fail(f"{block_id} is listed as a working machine and is not registered "
             f"as one at all")
if gen_solarpunk is not None:
    for entry in gen_solarpunk.MACHINES:
        if entry["id"] in working:
            fail(f"{entry['id']} is in both the WORKING and the MACHINES "
                 f"table — it cannot be finished and outstanding at once")

for block_id in working:
    # A graduated machine must have shed every marker of being a prop.
    if class_for(block_id) in pending_classes:
        fail(f"{block_id} works and still has a stub in block/pending/. The "
             f"directory listing is supposed to be the to-do list; a finished "
             f"machine left in it makes the list wrong")
    if block_id in manifest:
        fail(f"{block_id} works and is still a row in MACHINES.md, so the "
             f"manifest claims work that is already done")
    entry = next((keys for item_id, _, keys in items if item_id == block_id), "")
    if f"{NS}.tooltip.pending" in entry:
        fail(f"{block_id} works and its block item still tells the player the "
             f"machinery is not built yet")

    # ...and must have gained the things that make it a machine. A block entity
    # type that is not registered is a machine that places, renders and never
    # ticks, which looks exactly like a machine that is simply switched off.
    machine_java = (JAVA / "machine/SolarpunkMachines.java")
    if not machine_java.is_file():
        fail("SolarpunkMachines.java is missing, so no machine has a block "
             "entity and none of them tick")
    else:
        registry = machine_java.read_text(encoding="utf-8")
        if f'BLOCK_ENTITIES.register("{block_id}"' not in registry:
            fail(f"{block_id} works and has no block entity type registered. "
                 f"It would place, render and never tick")
        if registry.count("registerBlockEntity(Capabilities.EnergyStorage") < 1:
            fail("no energy capability is registered, so the machines run in "
                 "isolation and connect to nothing — including each other")

        # Every store a machine exposes must actually be handed to the
        # capability system.
        #
        # This is the failure with no error message. A tank that is built,
        # filled, saved and loaded correctly but never registered behaves
        # perfectly from inside the machine and is invisible to every pipe,
        # hopper and tank in the game. Nothing throws, nothing logs, and the
        # only symptom is a player saying their pump does not see it. The
        # accessor is the machine's own statement that something is meant to be
        # reachable from outside, so hold it to that.
        entity_file = JAVA / f"machine/{camel(block_id)}Entity.java"
        if not entity_file.is_file():
            fail(f"{block_id} is a working machine with no "
                 f"{camel(block_id)}Entity.java behind it")
        else:
            source = with_inherited(f"{camel(block_id)}Entity")
            const = block_id.upper() + "_ENTITY"
            for accessor, capability, what in MACHINE_STORES:
                if not declares(source, accessor):
                    continue
                wanted = f"Capabilities.{capability}.BLOCK"
                registered = any(
                    wanted in block and const in block
                    for block in registry.split("event.registerBlockEntity"))
                if not registered:
                    fail(f"{block_id} exposes {accessor}() and {const} is never "
                         f"registered for {wanted}. Its {what} works perfectly "
                         f"from the inside and is invisible to every other "
                         f"block in the game — nothing throws and nothing logs")

for block_id in pending:
    expected = class_for(block_id)
    if block_class[block_id] != expected:
        fail(f"{block_id} registers {block_class[block_id]}; the generator names "
             f"machine classes {expected}")
    if expected not in pending_classes:
        fail(f"{block_id} is registered as a machine and has no class in "
             f"block/pending/ — it carries no marker at all")
    if block_id not in manifest:
        fail(f"{block_id} is a pending machine and has no row in MACHINES.md, so "
             f"the one list a reader will find does not mention it")

    # The item must say so. A machine that looks craftable, is craftable, and
    # does nothing without saying it is the interface lying to the player.
    entry = next((keys for item_id, _, keys in items if item_id == block_id), "")
    if f"{NS}.tooltip.pending" not in entry:
        fail(f"{block_id}'s block item does not carry the "
             f"{NS}.tooltip.pending line, so a player is told nothing")

for class_name in pending_classes:
    block_id = re.sub(r"(?<!^)(?=[A-Z])", "_", class_name[:-len("Block")]).lower()
    if block_id not in pending:
        fail(f"{class_name} sits in block/pending/ and nothing registers it. "
             f"Either it was finished and the class was left behind, or it was "
             f"never wired up")

for block_id in manifest:
    if block_id not in pending:
        fail(f"MACHINES.md lists {block_id}, which is not a registered pending "
             f"machine. A manifest naming blocks that do not exist is worse than "
             f"no manifest")

# The three sentences have to be one sentence.
for block_id in pending:
    class_name = class_for(block_id)
    from_class = pending_classes.get(class_name)
    from_manifest = manifest.get(block_id)
    from_lang = lang.get(f"{NS}.tooltip.{block_id}")
    distinct = {s for s in (from_class, from_manifest, from_lang) if s}
    if len(distinct) > 1:
        fail(f"{block_id} describes what it owes differently in different places: "
             f"class says {from_class!r}, MACHINES.md says {from_manifest!r}, the "
             f"tooltip says {from_lang!r}. Three copies that disagree are three "
             f"chances to believe the wrong one")

if pending and not manifest:
    fail("there are pending machines and MACHINES.md lists none of them")

print(f"working machines   : {len(working)} with block entities, energy and "
      f"no marker left on them")

# Where the real Minecraft data lives, when it is available. Several checks
# below are only honest against it; each says so when it has to skip.
_mc = os.environ.get("ELYSIUM_MC_LIBS", "/tmp/mc")
_classpath_file = os.path.join(_mc, "cp.txt")


# ---------------------------------------------------------------------------
# The harvesters must never destroy what they cannot store
# ---------------------------------------------------------------------------
#
# The one property of these machines that cannot be recovered from. A harvester
# that cut something and then found nowhere to put it would quietly delete a
# player's field or forest the first time their chest filled while they were
# away — a loss discovered days later, with nothing in the world saying what
# happened.
#
# The order is the guarantee: check there is room, then break the block. Checked
# structurally because the harness cannot see it — cutting needs a ServerLevel
# and a real crop.
#
# Written against the shared SolarpunkOutput helper rather than against each
# machine's own copy. The first version named HarvesterEntity's private methods
# and failed the moment those moved into the helper — correctly, since it could
# no longer see the property, but a check that has to be rewritten every time
# the code is tidied is a check people learn to edit rather than to heed.

_output_helper = JAVA / "machine/SolarpunkOutput.java"
if not _output_helper.is_file():
    fail("machine/SolarpunkOutput.java is missing — the rule that a harvester "
         "checks for room before it cuts lives there")
else:
    _out_src = strip_comments(_output_helper.read_text(encoding="utf-8"))
    if "Capabilities.ItemHandler.BLOCK" not in _out_src or "pos.below()" not in _out_src:
        fail("SolarpunkOutput does not post downward through the item handler "
             "capability, so the harvesters work with whatever class it names "
             "and with nothing else in the game")

# Each machine that cuts something out of the world, and the method that cuts.
for _machine, _cutter in (("HarvesterEntity", "private boolean harvest("),
                          ("TreeHarvesterEntity", "private boolean fell(")):
    _path = JAVA / f"machine/{_machine}.java"
    if not _path.is_file():
        continue
    _src = strip_comments(_path.read_text(encoding="utf-8"))
    _before = len(problems)

    _at = _src.find(_cutter)
    if _at < 0:
        fail(f"{_machine} has no {_cutter.split()[-1]} method, so nothing cuts "
             f"anything")
    else:
        # Scoped to the cutting method, not searched for anywhere in the file.
        # An earlier version asked whether the fit test appeared in the source at
        # all, and deleting the call left the method's own declaration behind —
        # so the check passed on a harvester that cut into a full tray. A guard
        # that a method exists is not a guard that anything calls it.
        _body = _src[_at:_src.index("\n    }", _at)]
        if "SolarpunkOutput.fits(" not in _body:
            fail(f"{_machine}'s cutting method never asks whether the harvest "
                 f"will fit before taking it, so a full tray means destroyed "
                 f"blocks rather than a paused machine")
        else:
            # Every way this codebase changes a block, not just the one the
            # first machine happened to use. The tree harvester clears logs with
            # setBlock and the crop harvester replaces with setBlockAndUpdate;
            # a guard that knew only the second reported the first as never
            # cutting anything.
            _mutations = [_body.find(call) for call in
                          ("setBlockAndUpdate(", "setBlock(", "destroyBlock(",
                           "removeBlock(")]
            _mutations = [i for i in _mutations if i >= 0]
            if not _mutations:
                fail(f"{_machine} never changes the block it harvested, so "
                     f"nothing is actually cut or replanted")
            elif _body.index("SolarpunkOutput.fits(") > min(_mutations):
                fail(f"{_machine} breaks the block before it checks the harvest "
                     f"will fit. A full tray then costs the player the block")

    if "insertItem" not in _src or "return stack;" not in _src:
        fail(f"{_machine}'s tray accepts insertion. A hopper feeding it would "
             f"fill the space the harvest needs and look like it was supplying "
             f"the replant")

    # Scoped to the method that replants, not searched for in the file.
    _replanters = (("private boolean harvest(", "paysForReplant("),
                   ("private void replant(", "payForSapling("))
    _paid = any(calls(_src, where, what) for where, what in _replanters)
    if not _paid:
        fail(f"{_machine} replants without consuming anything from the harvest, "
             f"so it prints what it is supposed to farm")

    if len(problems) == _before:
        print(f"{_machine[:-6].lower():<18} : checks the tray before it cuts, "
              f"pays for each replant out of the harvest")

# ---------------------------------------------------------------------------
# The tree harvester must not fell a building
# ---------------------------------------------------------------------------
#
# Three conditions have to hold at once before a log mass is treated as a tree,
# and a log wall fails all three. Each is one line in the source and removing
# any one of them turns a forestry machine into something that eats a player's
# cabin — which is not a bug anybody gets to fix afterwards.
#
# The harness checks the constants are sane. This checks the conditions are
# actually asked, because a generous constant and a missing test look identical
# from the outside.

_tree = JAVA / "machine/TreeHarvesterEntity.java"
if _tree.is_file():
    _tsrc = strip_comments(_tree.read_text(encoding="utf-8"))
    _tree_before = len(problems)

    if "isTrunkBase(" not in _tsrc or "BlockTags.DIRT" not in _tsrc:
        fail("TreeHarvesterEntity does not require a trunk to stand on soil, so "
             "a log wall on a stone floor reads as a tree")
    if "hasCanopy(" not in _tsrc or "BlockTags.LEAVES" not in _tsrc:
        fail("TreeHarvesterEntity does not require leaves, so a stack of logs "
             "reads as a tree and somebody's cabin is felled")
    if "MAX_TREE" not in _tsrc or "found.size() >= MAX_TREE" not in _tsrc:
        fail("TreeHarvesterEntity's flood fill is not bounded during the walk. "
             "A large wooden build would be walked in full before being "
             "refused, which is a server freeze rather than a refusal")

    # All three have to gate the felling, not merely exist.
    _fell_at = _tsrc.find("private boolean fell(")
    if _fell_at >= 0:
        _fell = _tsrc[_fell_at:_tsrc.index("\n    }", _fell_at)]
        for _call, _why in (("isTrunkBase(", "that the trunk stands on soil"),
                            ("hasCanopy(", "that the mass carries leaves")):
            if _call not in _fell:
                fail(f"TreeHarvesterEntity.fell() never checks {_why}, so the "
                     f"test exists and nothing consults it")

    # Refusing an oversized mass must refuse the whole thing. Half a felled
    # warehouse is worse than none.
    if "return Set.of();" not in _tsrc:
        fail("TreeHarvesterEntity takes part of a log mass that exceeded the "
             "cap instead of refusing it whole, so an oversized build is "
             "partly demolished")

    if len(problems) == _tree_before:
        print("tree harvester     : soil, canopy and a bounded walk, all three "
              "asked before anything is felled")

# ---------------------------------------------------------------------------
# The processing machines and their recipe books
# ---------------------------------------------------------------------------
#
# A recipe of a type nothing registers, or naming an item or tag that does not
# exist, does not crash. It fails to load, one line goes into a log nobody
# reads, and the machine simply refuses an input the player was told it takes.
# That is the worst failure mode in this file, because it looks exactly like a
# machine working correctly and the player being wrong.

def _vanilla_items():
    """Every vanilla item id, from the jar's item models. None without a jar."""
    import zipfile
    if not os.path.isfile(_classpath_file):
        return None
    for jar in open(_classpath_file, encoding="utf-8").read().strip().split(":"):
        if not jar.endswith(".jar") or not os.path.isfile(jar):
            continue
        try:
            archive = zipfile.ZipFile(jar)
        except Exception:
            continue
        with archive:
            found = {"minecraft:" + name.split("/")[-1][:-len(".json")]
                     for name in archive.namelist()
                     if name.startswith("assets/minecraft/models/item/")
                     and name.endswith(".json")}
            if found:
                return found
    return None


def _vanilla_item_tags():
    """Every vanilla *item* tag id. None without a jar."""
    import zipfile
    if not os.path.isfile(_classpath_file):
        return None
    for jar in open(_classpath_file, encoding="utf-8").read().strip().split(":"):
        if not jar.endswith(".jar") or not os.path.isfile(jar):
            continue
        try:
            archive = zipfile.ZipFile(jar)
        except Exception:
            continue
        with archive:
            prefix = "data/minecraft/tags/item/"
            found = {"minecraft:" + name[len(prefix):-len(".json")]
                     for name in archive.namelist()
                     if name.startswith(prefix) and name.endswith(".json")}
            if found:
                return found
    return None


# Which block reads which recipe type. Read from the Java rather than repeated,
# so a machine renamed in one place cannot be checked against a stale copy here.
_recipe_registry = (JAVA / "recipe/SolarpunkRecipes.java")
if not _recipe_registry.is_file():
    fail("recipe/SolarpunkRecipes.java is missing, so the processing machines "
         "have no recipe types and every one of their recipes fails to load")
    _registered_types = set()
else:
    _registered_types = {f"{NS}:{name}" for name in
                         re.findall(r'process\("([a-z_]+)"\)',
                                    strip_comments(_recipe_registry.read_text(
                                        encoding="utf-8")))}

if gen_solarpunk is None:
    print(f"processing         : {len(process_recipes)} recipes, generator not "
          f"importable so not cross-checked")
else:
    _by_machine = gen_solarpunk.processing_recipes()
    _vanilla_ids = _vanilla_items()
    _vanilla_tags = _vanilla_item_tags()
    _own_tags = {f"{NS}:{path.stem}" for path in
                 (RES / f"data/{NS}/tags/item").glob("*.json")}

    # Every machine the table names must be a machine that works, and must read
    # a type that is actually registered.
    for _machine, _rows in _by_machine.items():
        if _machine not in working:
            fail(f"the table gives {_machine} {len(_rows)} recipes and it is not "
                 f"a working machine, so nothing will ever read them")
        _entity = JAVA / f"machine/{camel(_machine)}Entity.java"
        if _entity.is_file():
            _source = strip_comments(_entity.read_text(encoding="utf-8"))
            if "ProcessingMachineEntity" not in _source:
                fail(f"{_machine} has recipes and its entity is not a "
                     f"ProcessingMachineEntity, so nothing reads them")

    # A machine with an empty recipe book is a block that does nothing and says
    # nothing about it — strictly worse than one still marked pending.
    for _id in working:
        _entity = JAVA / f"machine/{camel(_id)}Entity.java"
        if not _entity.is_file():
            continue
        if "ProcessingMachineEntity" not in strip_comments(
                _entity.read_text(encoding="utf-8")):
            continue
        if not _by_machine.get(_id):
            fail(f"{_id} reads a recipe book and the mod ships no recipes for "
                 f"it, so it is a working machine that does nothing")

    for _name, _recipe in sorted(process_recipes.items()):
        if _recipe["type"] not in _registered_types:
            fail(f"{_name}.json is a {_recipe['type']} and SolarpunkRecipes "
                 f"registers {sorted(_registered_types)} — the recipe will not "
                 f"load and the machine will refuse the input in silence")

        for _field in ("time", "energy"):
            if _recipe.get(_field, 0) <= 0:
                fail(f"{_name}.json has {_field} {_recipe.get(_field)}; a run "
                     f"that takes no time or costs nothing is a machine that "
                     f"turns one item into another for free")
        if _recipe.get("input_count", 1) < 1:
            fail(f"{_name}.json consumes {_recipe.get('input_count')} of its "
                 f"input, so it never runs out")
        if _recipe["result"].get("count", 1) < 1:
            fail(f"{_name}.json produces nothing")

        # Every id it names has to exist.
        _named = [_recipe["result"]["id"]]
        _input = _recipe["input"]
        _tag = _input.get("tag")
        if _tag:
            _tag_id = _tag if ":" in _tag else "minecraft:" + _tag
            if _tag_id.startswith(f"{NS}:"):
                if _tag_id not in _own_tags:
                    fail(f"{_name}.json takes #{_tag_id} and this mod ships no "
                         f"such item tag")
            elif _vanilla_tags is not None and _tag_id not in _vanilla_tags:
                fail(f"{_name}.json takes #{_tag_id}, which is not a vanilla "
                     f"*item* tag. A block tag used as an ingredient resolves "
                     f"to nothing and the recipe fails to load in silence")
        else:
            _named.append(_input["item"])

        for _id in _named:
            if _id.startswith(f"{NS}:"):
                if _id.split(":", 1)[1] not in item_ids:
                    fail(f"{_name}.json names {_id}, which this mod does not "
                         f"register")
            elif _vanilla_ids is not None and _id not in _vanilla_ids:
                fail(f"{_name}.json names {_id}, which is not a vanilla item")

    # The point of a machine: it has to beat doing the same thing by hand.
    for _row in gen_solarpunk.PROCESSING:
        _hand = recipe_results.get(_row["result"], [])
        if not _hand:
            continue
        _hand_recipe = json.loads(
            (recipe_dir / f"{_hand[0]}.json").read_text(encoding="utf-8"))
        _hand_yield = _hand_recipe["result"].get("count", 1)
        if _row["yield"] <= _hand_yield:
            fail(f"{_row['id']} makes {_row['yield']}x {_row['result']} and the "
                 f"hand recipe {_hand[0]} already makes {_hand_yield}x. A "
                 f"machine that matches the hand recipe is a machine with no "
                 f"reason to exist")

    # Every item whose only source is a machine must actually be produced by
    # one. "made_by" was a string nothing checked until there were two of them.
    for _entry in gen_solarpunk.INGREDIENTS:
        _maker = _entry.get("made_by")
        if not _maker:
            continue
        if _maker in _by_machine:
            _makes = any(_row["result"] == f"{NS}:{_entry['id']}"
                         for _row in _by_machine[_maker])
            if not _makes:
                fail(f"{_entry['id']} says it is made by {_maker} and no "
                     f"{_maker} recipe produces it, so it has no source at all")
        elif _maker not in working:
            fail(f"{_entry['id']} says it is made by {_maker}, which is not a "
                 f"working machine")

    # What the recipes actually cost, derived rather than read off the table.
    #
    # `energy` is a total and the machine spends a per-tick share of it; the two
    # numbers a designer writes are not the number a player feels. Compute the
    # draw, check a single generator can sustain it, and print the spread —
    # because the failure this guards against is not a crash, it is a table
    # where every row quietly says the same thing.
    def _per_tick(row):
        return max(1, -(-row["energy"] // row["time"]))

    _condenser = JAVA / "machine/SolarCondenserEntity.java"
    _peak = None
    if _condenser.is_file():
        _match = re.search(r"PEAK_OUTPUT\s*=\s*(\d+)",
                           strip_comments(_condenser.read_text(encoding="utf-8")))
        _peak = int(_match.group(1)) if _match else None

    for _row in gen_solarpunk.PROCESSING:
        _draw = _per_tick(_row)
        if _peak is not None and _draw > _peak:
            fail(f"{_row['id']} draws {_draw}/t and the best single generator "
                 f"makes {_peak}/t at its peak, so the recipe can never run on "
                 f"one generator and nothing says so")

    # The energy column has to carry information the time column does not. Every
    # row at the same rate means one of the two numbers is decoration, and the
    # machines are indistinguishable to anyone sizing a generator for them.
    _rates = {machine: {_per_tick(row) for row in rows}
              for machine, rows in _by_machine.items()}
    if len(set().union(*_rates.values())) < 2:
        fail(f"every processing recipe draws the same {_per_tick(gen_solarpunk.PROCESSING[0])}/t, "
             f"so `energy` is a restatement of `time` and the machines cost the "
             f"same to run")

    _lines = []
    for _machine, _rows in sorted(_by_machine.items()):
        _draws = sorted({_per_tick(r) for r in _rows})
        _cheapest = min(_rows, key=lambda r: r["energy"] / r["yield"])
        _dearest = max(_rows, key=lambda r: r["energy"] / r["yield"])
        _lines.append(
            f"{_machine} draws {'/'.join(str(d) for d in _draws)}/t; "
            f"cheapest output {_cheapest['id']} at "
            f"{_cheapest['energy'] // _cheapest['yield']} each, dearest "
            f"{_dearest['id']} at {_dearest['energy'] // _dearest['yield']}")

    _totals = ", ".join(f"{len(rows)} for {machine}"
                        for machine, rows in sorted(_by_machine.items()))
    print(f"processing         : {len(process_recipes)} recipes ({_totals}), "
          f"every id resolved, each beating the hand recipe it replaces")
    for _line in _lines:
        print(f"recipe economics   : {_line}")

# ---------------------------------------------------------------------------
# The kiln's input set, against vanilla's own recipes
# ---------------------------------------------------------------------------
#
# The generator ships an explicit list so it needs no Minecraft jar. That list
# is a copy of something Mojang owns, and a copy is a thing that rots. Here,
# where a jar is available, recompute it from the real recipe files and require
# the two to agree.
#
# The rule is: vanilla's `blocks` smelting category, minus KILN_DROPS, plus
# KILN_ADDS. Both exception lists are also checked for still being *needed* —
# an add that vanilla has since moved into `blocks`, or a drop for something
# vanilla no longer files there, is a line nobody will delete unless something
# says so, and it makes the next reader believe a decision was made that was
# not.
def _vanilla_smelting_by_category():
    """{category: {ingredient}} read from whichever jar carries vanilla data."""
    import zipfile
    if not os.path.isfile(_classpath_file):
        return None
    for jar in open(_classpath_file, encoding="utf-8").read().strip().split(":"):
        if not jar.endswith(".jar") or not os.path.isfile(jar):
            continue
        try:
            archive = zipfile.ZipFile(jar)
        except Exception:
            continue
        with archive:
            names = [n for n in archive.namelist()
                     if n.startswith("data/minecraft/recipe/") and n.endswith(".json")]
            if not names:
                continue
            found = {}
            for name in names:
                try:
                    recipe = json.loads(archive.read(name))
                except Exception:
                    continue
                if recipe.get("type") != "minecraft:smelting":
                    continue
                ingredient = recipe.get("ingredient")
                if isinstance(ingredient, dict) and "item" in ingredient:
                    value = ingredient["item"]
                elif isinstance(ingredient, dict) and "tag" in ingredient:
                    value = "#" + ingredient["tag"]
                elif isinstance(ingredient, str):
                    value = ingredient
                else:
                    continue        # a list of alternatives; only in `misc`
                found.setdefault(recipe.get("category", "misc"), set()).add(value)
            return found
    return None

_kiln_tag = WORK / f"src/main/resources/data/{NS}/tags/item/kiln_inputs.json"
if not _kiln_tag.is_file():
    fail("the kiln ships no kiln_inputs tag, so it will refuse everything and "
         "look like a machine that does not work")
elif gen_solarpunk is None:
    pass
else:
    _shipped = set(json.loads(_kiln_tag.read_text(encoding="utf-8"))["values"])
    _vanilla = _vanilla_smelting_by_category()
    if _vanilla is None:
        print(f"kiln inputs        : {len(_shipped)} entries, not cross-checked "
              f"(no Minecraft classpath at {_classpath_file})")
    else:
        _blocks = _vanilla.get("blocks", set())
        _expected = (_blocks - set(gen_solarpunk.KILN_DROPS)) \
            | set(gen_solarpunk.KILN_ADDS)

        for _missing in sorted(_expected - _shipped):
            fail(f"vanilla fires {_missing} and the kiln will not take it — the "
                 f"shipped tag has drifted from the rule that is supposed to "
                 f"produce it")
        for _extra in sorted(_shipped - _expected):
            fail(f"the kiln takes {_extra}, which is not in vanilla's `blocks` "
                 f"smelting category and is not a listed exception")

        # Every exception must still be doing something.
        for _added, _why in gen_solarpunk.KILN_ADDS.items():
            if _added in _blocks:
                fail(f"{_added} is listed as a kiln addition ({_why}) and "
                     f"vanilla already files it under `blocks`, so the "
                     f"exception is now a no-op that reads like a decision")
        for _dropped, _why in gen_solarpunk.KILN_DROPS.items():
            if _dropped not in _blocks:
                fail(f"{_dropped} is listed as a kiln exclusion ({_why}) and "
                     f"vanilla does not file it under `blocks` anyway, so the "
                     f"exception excludes nothing")

        # The point of the restriction: no ore, no food.
        _ore_and_food = _vanilla.get("misc", set()) | _vanilla.get("food", set())
        _leaked = sorted((_shipped & _ore_and_food) - set(gen_solarpunk.KILN_ADDS))
        if _leaked:
            fail(f"the kiln takes {', '.join(_leaked)}, which vanilla files as "
                 f"ore-processing or cooking. A kiln that does those is a "
                 f"better furnace, and then nobody builds a furnace")

        print(f"kiln inputs        : {len(_shipped)} entries derived from "
              f"vanilla's `blocks` category, {len(gen_solarpunk.KILN_ADDS)} "
              f"addition(s) and {len(gen_solarpunk.KILN_DROPS)} exclusion(s), "
              f"each still necessary")

# The arithmetic, executed. See tools/MachineHarness.java.
#
# Runs only where a real Minecraft classpath is available — the same one
# compile.sh uses — because the machines are written in terms of vanilla's own
# types and there is nothing honest to check them against without them.
_harness = WORK / "tools/MachineHarness.java"

if not _harness.is_file():
    fail("tools/MachineHarness.java is missing — the machines' numbers are no "
         "longer executed anywhere, only read")
elif not os.path.isfile(_classpath_file):
    print(f"machine numbers    : not run, no Minecraft classpath at "
          f"{_classpath_file} (set ELYSIUM_MC_LIBS)")
else:
    import subprocess
    import tempfile

    _cp = open(_classpath_file, encoding="utf-8").read().strip()
    _annotations = os.environ.get(
        "ELYSIUM_ANNOTATIONS", "/opt/gradle-8.14.3/lib/annotations-24.0.1.jar")
    if os.path.isfile(_annotations):
        _cp += os.pathsep + _annotations

    with tempfile.TemporaryDirectory() as scratch:
        _module = os.path.join(scratch, "module")
        _tools = os.path.join(scratch, "tools")
        _sources = [str(path) for path in
                    (WORK / "src/main/java").rglob("*.java")]
        _steps = [
            ["javac", "-nowarn", "-proc:none", "-cp", _cp, "-d", _module]
            + _sources,
            ["javac", "-nowarn", "-proc:none", "-cp", _cp + os.pathsep + _module,
             "-d", _tools, str(_harness)],
            ["java", "-cp", os.pathsep.join([_cp, _module, _tools]),
             "com.elysium.solarpunk.machine.MachineHarness"],
        ]
        _summary = ""
        for _step in _steps:
            _result = subprocess.run(_step, capture_output=True, text=True)
            _summary = "\n".join(
                line for line in (_result.stdout + _result.stderr).split("\n")
                if line and "JAVA_TOOL_OPTIONS" not in line
                and not line.startswith(("Note:", "SLF4J")))
            if _result.returncode != 0:
                fail("the machine harness failed:\n      "
                     + _summary.replace("\n", "\n      "))
                break
        else:
            for line in _summary.split("\n"):
                if line.strip():
                    print("machine numbers    : " + line.strip())
# ---------------------------------------------------------------------------
# The empty case: nothing pending, and nothing left behind
# ---------------------------------------------------------------------------
#
# The set has a finished state, and the first time it reached one the generator
# wrote "The 0 blocks below are registered, craftable, placeable, and do
# nothing" above an empty table. Every check above passed, because every one of
# them was written for a list with something in it.
#
# The scaffolding is deliberately kept rather than deleted — a machine added
# back to the table gets its stub and its row with nothing else to do — so what
# is checked here is that it is kept *clean*: no stub for a machine that no
# longer needs one, and no player-facing string for a state nothing is in.
if not pending:
    orphans = sorted(name for name in pending_classes
                     if name != "PendingMachineBlock")
    if orphans:
        fail(f"nothing is pending and block/pending/ still holds "
             f"{', '.join(orphans)}. The directory listing is the to-do list; "
             f"a stub left in it says work is outstanding when it is not")

    if f"{NS}.tooltip.pending" in lang:
        fail(f"nothing is pending and the lang file still ships "
             f"{NS}.tooltip.pending. No block item will ever show it, so it is "
             f"a line every translator renders and no player can reach")

    # The scaffolding that must NOT be tidied away, because the empty case is a
    # zero rather than an end.
    for kept, why in (
            ("block/PendingMachine.java", "the marker interface"),
            ("block/pending/PendingMachineBlock.java", "the shared body")):
        if not (JAVA / kept).is_file():
            fail(f"{kept} is gone — {why} for an unfinished machine. The list "
                 f"being empty today is not a reason to delete it; adding a "
                 f"machine back to the table would then need it rewritten by "
                 f"hand, and the generator does not write it")

    print(f"finished set       : nothing owed, no orphan stubs, no dead "
          f"tooltip string, scaffolding kept for the next one")

print(f"pending machines   : {len(pending)} registered, {len(pending_classes)} "
      f"classes in block/pending/, {len(manifest)} rows in MACHINES.md, all "
      f"describing the same work")


# ===========================================================================
# Drift: the shipped tree against the generator that wrote it
# ===========================================================================
#
# Everything above reads only what ships. This last section is the other
# direction: a hand-edit to a generated file works perfectly until the next
# `regen.sh`, which reverts it without saying so. Better to hear about it now.

if gen_solarpunk is not None:
    regenerated = gen_solarpunk.build_lang()
    if regenerated != lang:
        added = sorted(set(regenerated) - set(lang))
        removed = sorted(set(lang) - set(regenerated))
        changed = sorted(k for k in set(lang) & set(regenerated)
                         if lang[k] != regenerated[k])
        fail(f"the shipped language file is not what the generator produces "
             f"(missing {added}, extra {removed}, different {changed}). Either "
             f"re-run the generator or move the change into its table")

    table_ids = gen_solarpunk.all_block_ids()
    if table_ids != block_ids:
        fail(f"the generator's table lists {len(table_ids)} blocks and the "
             f"shipped Java registers {len(block_ids)}; the two have drifted "
             f"and a regen would rewrite the Java")

    table_machines = ([entry["id"] for entry in gen_solarpunk.WORKING]
                      + [entry["id"] for entry in gen_solarpunk.MACHINES])
    if table_machines != machines:
        fail(f"the generator's machine tables are {table_machines} and the "
             f"shipped Java registers {machines}")

    print(f"generator          : shipped tree matches the table "
          f"({len(table_ids)} blocks, {len(table_machines)} machines)")

    # ----------------------------------------------------------------------
    # The metal trim is actually there
    # ----------------------------------------------------------------------
    #
    # Twenty-four of these blocks are a stone face with a metal pattern drawn
    # over it, and every one of them is composed at generation time. That is the
    # right way to build them and it has one failure mode worth guarding: an
    # overlay that runs, writes nothing visible, and produces a block called
    # "Gold Filigree White Marble" that is a plain white square. It would not
    # crash, would not warn, and would look like plain marble in a screenshot of
    # plain marble.
    #
    # The table is used only to know which blocks to look at. What is checked is
    # the shipped PNG.
    if Image is not None:
        trims = [entry for entry in gen_solarpunk.BUILDING if "overlay" in entry]
        thin = []
        for entry in trims:
            path = RES / f"assets/{NS}/textures/block/{entry['id']}.png"
            base = RES / f"assets/{NS}/textures/block/{entry['base']}.png"
            if not path.is_file() or not base.is_file():
                fail(f"{entry['id']} is a trim block and either it or its base "
                     f"{entry['base']} has no texture")
                continue

            trimmed = Image.open(path).convert("RGBA")
            plain = Image.open(base).convert("RGBA")
            changed = sum(1 for a, b in zip(trimmed.getdata(), plain.getdata())
                          if a != b)
            if changed == 0:
                fail(f"{entry['id']} is pixel-identical to {entry['base']} — "
                     f"the overlay ran and drew nothing, so this is a trim "
                     f"block with no trim on it")
                continue

            # And the pixels it drew have to be the metal it is named after.
            # An overlay that drew in the *stone's* ramp would pass the test
            # above and still give four indistinguishable blocks per stone.
            metal = {material_rgb(tone)
                     for tone in gen_solarpunk.PALETTE[entry["overlay"][1]]}
            metal_pixels = sum(1 for p in trimmed.getdata() if p[:3] in metal)
            if metal_pixels == 0:
                fail(f"{entry['id']} differs from its base but contains none of "
                     f"the {entry['overlay'][1]} palette — whatever it was "
                     f"drawn in, it was not the metal in its name")
            elif metal_pixels < 12:
                thin.append((entry["id"], metal_pixels))

        for block_id, count in thin:
            fail(f"{block_id} shows only {count} pixel(s) of metal out of 256. "
                 f"At sixteen pixels that is not a pattern, it is a smudge")

        # Two trims of the same stone in different metals must not come out the
        # same file, which is the artefact-level version of the question the
        # gear palette check asks of the numbers.
        by_payload = {}
        for entry in trims:
            path = RES / f"assets/{NS}/textures/block/{entry['id']}.png"
            if not path.is_file():
                continue
            payload = path.read_bytes()
            if payload in by_payload:
                fail(f"{entry['id']} and {by_payload[payload]} ship the "
                     f"identical texture — two trims the player cannot tell "
                     f"apart")
            by_payload[payload] = entry["id"]

        print(f"metal trim         : {len(trims)} composed faces, each showing "
              f"its own metal, all distinct")


# ---------------------------------------------------------------------------
# All JSON parses
# ---------------------------------------------------------------------------

def _method_body(source, signature):
    """The text between the braces of the method whose signature contains this.

    Substring checks over a whole file are the commonest way a rule ends up
    unable to fail: PowerGrid names isLoaded in two walks, so deleting the guard
    from one of them leaves the word in the file and the check green. Scoping to
    one method is the difference between testing the code and testing the
    spelling.
    """
    at = source.find(signature)
    if at < 0:
        return None
    open_brace = source.find("{", at)
    if open_brace < 0:
        return None
    depth = 0
    for index in range(open_brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[open_brace + 1:index]
    return None


# ---------------------------------------------------------------------------
# C1: power priority, the grid, and brownouts
# ---------------------------------------------------------------------------
#
# Five checks, each guarding a failure that is silent in a running game.

_power = WORK / "src/main/java/com/elysium/solarpunk/power"
_pc_src = (_power / "PowerClass.java").read_text(encoding="utf-8")
_pb_src = (_power / "PowerBudget.java").read_text(encoding="utf-8")
_pg_src = (_power / "PowerGrid.java").read_text(encoding="utf-8")

# 1. The two arithmetic files import no Minecraft.
#
# Not a style rule. The harness executes the budget with 26,000 assertions and
# it can only do that because neither file needs a registry, a level or a
# server to load. One `import net.minecraft...` added for convenience would
# take the whole allocator out of the tests, and nothing else would complain.
_gs_src = (_power / "GridState.java").read_text(encoding="utf-8")
_cond_src = (_power / "PowerCondition.java").read_text(encoding="utf-8")
_rule_src = (_power / "PowerRule.java").read_text(encoding="utf-8")

for _name, _src in (("PowerClass", _pc_src), ("PowerBudget", _pb_src),
                    ("GridState", _gs_src), ("PowerCondition", _cond_src),
                    ("PowerRule", _rule_src)):
    _foreign = [line.strip() for line in _src.split("\n")
                if line.startswith("import ")
                and not line.startswith("import java.")]
    if _foreign:
        fail(f"{_name}.java imports {_foreign[0]!r}. The power arithmetic and "
             f"the whole rule vocabulary are executed by the harness outside a "
             f"world, and only stay executable while these files need nothing "
             f"to load")

# 2. The lang entries and the enum name the same five classes.
#
# A class the enum has and the language file does not renders in chat as a raw
# key; a class the language file has and the enum does not is a line every
# translator carries for a state that cannot happen. Checked in both directions
# because each is invisible from the other side.
_enum_ids = set(re.findall(r'^\s+[A-Z]+\("([a-z]+)"\)', _pc_src, re.M))
_lang = json.load(open(RES / f"assets/{NS}/lang/en_us.json", encoding="utf-8"))
_lang_ids = {key.rsplit(".", 1)[-1] for key in _lang
             if key.startswith(f"{NS}.power.class.")
             and not key.endswith(".blurb")}
if _enum_ids != _lang_ids:
    fail(f"PowerClass and the language file disagree about the power classes: "
         f"enum has {sorted(_enum_ids - _lang_ids)} extra, language file has "
         f"{sorted(_lang_ids - _enum_ids)} extra")
elif len(_enum_ids) != 5:
    fail(f"there are {len(_enum_ids)} power classes; the specification names "
         f"five, and the count is the ordering a player tunes a base against")

# 3. The enum's declaration order is the spec's priority order.
#
# PowerClass.rank() returns the ordinal, so moving a constant in that file
# re-ranks every base in every world that loads the mod — with no error, no
# migration and nothing in the changelog.
_declared = re.findall(r'^\s+([A-Z]+)\("[a-z]+"\)', _pc_src, re.M)
_expected = ["CRITICAL", "DEFENSE", "LOGISTICS", "INDUSTRY", "COMFORT"]
if _declared != _expected:
    fail(f"the power classes are declared {_declared}, not {_expected}. The "
         f"rank is the ordinal, so this order is the priority order")

# 4. The machines that must not be industry, are not.
#
# The overrides are one line each and a refactor that dropped one would leave a
# water reclaimer quietly ranked beside a loom — which is exactly the base that
# dies on a cloudy afternoon, and it would look like bad luck.
_expected_classes = {
    "WaterReclaimerEntity": "CRITICAL",
    "AquaponicsTankEntity": "CRITICAL",
    "HarvesterEntity": "LOGISTICS",
    "TreeHarvesterEntity": "LOGISTICS",
}
for _entity, _want in _expected_classes.items():
    _src = (WORK / f"src/main/java/com/elysium/solarpunk/machine/{_entity}.java"
            ).read_text(encoding="utf-8")
    _found = re.search(r"public PowerClass powerClass\(\)\s*\{\s*"
                       r"return PowerClass\.([A-Z]+);", _src)
    if _found is None:
        fail(f"{_entity} no longer declares a power class, so it falls back to "
             f"industry — it is meant to be {_want}")
    elif _found.group(1) != _want:
        fail(f"{_entity} is {_found.group(1)}, not {_want}")

# 5. The grid walk is bounded and stops at an unloaded chunk.
#
# An unbounded flood fill is a server hang that waits for a player to build a
# long enough line, and a walk that reads through an unloaded chunk turns every
# generator into an accidental chunk loader. Both are cheap to write and neither
# shows up until somebody's server is the one it shows up on.
if "MAX_CONDUITS" not in _pg_src or "MAX_RECEIVERS" not in _pg_src:
    fail("PowerGrid has lost one of its bounds; the walk is a flood fill and "
         "an unbounded one hangs the server thread")
# Both walks, separately. The word appears twice in the file, so a check for
# the word would stay green with the guard deleted from either one of them.
for _walk in ("public static Survey survey(", "int conduitCount("):
    _body = _method_body(_pg_src, _walk)
    if _body is None:
        fail(f"PowerGrid has no {_walk.strip('(')} method any more")
    elif "isLoaded(" not in _body:
        fail(f"PowerGrid's {_walk.strip('(')} walk no longer skips unloaded "
             f"chunks, so a generator loads the world around it by reading "
             f"through the edge of the loaded area")

# 6. The push goes through the budget rather than round the neighbours.
_push = (WORK / "src/main/java/com/elysium/solarpunk/machine/SolarpunkPower.java"
         ).read_text(encoding="utf-8")
if "PowerBudget.allocate" not in _push:
    fail("SolarpunkPower no longer allocates through PowerBudget, so the "
         "priority classes decide nothing and the block underneath a generator "
         "wins again")
if "receiveEnergy(grants[i], false)" not in _push \
        or "source.extractEnergy(taken, false)" not in _push:
    fail("SolarpunkPower has stopped offering before taking. A receiver that "
         "refuses what was already extracted destroys the difference, and "
         "nothing reports it")

# 7. The brownout count is in the buffer, not in seven ticks.
_base = (WORK / "src/main/java/com/elysium/solarpunk/machine/"
                "SolarpunkMachineEntity.java").read_text(encoding="utf-8")
# Scoped to the buffer's own extractEnergy, because the file also *defines*
# noteUnpaid — a check for the name alone passes with every call site deleted.
_extract = _method_body(_base, "public int extractEnergy(int toExtract, boolean simulate)")
if _extract is None:
    fail("SolarpunkMachineEntity's buffer no longer overrides extractEnergy, "
         "so nothing counts a starved tick")
elif "noteUnpaid()" not in _extract or "extracted < toExtract" not in _extract:
    fail("the brownout counter is no longer driven by the buffer's own failed "
         "extraction. Counting it in each machine's tick means a machine that "
         "forgets simply never reports being starved")
elif "notePaid()" not in _extract:
    fail("nothing clears the brownout counter on a paid tick, so every machine "
         "that is ever briefly short stays reported as browned out for good")

# 8. The pipe a player sees and the pipe a player bumps into are the same pipe.
#
# The shape is stated twice and has to be: the model is JSON the client draws,
# the VoxelShape is Java the server collides against. Nothing in Minecraft
# reconciles them, so a one-pixel edit to either is a conduit you can stand
# inside or one that stops you a pixel short of itself — and neither throws.
#
# Rather than copying the six arm boxes into this file, the north arm is taken
# from the generator and the other five are *derived* by rotating it, then
# compared against what the Java declares. That way the check cannot agree with
# the code by construction: it works out where the arms should be from one box
# and the rotations the blockstate claims to apply.

def _rotate_box(box, turn):
    """One of the five rotations the multipart blockstate applies to the arm."""
    x1, y1, z1, x2, y2, z2 = box
    if turn == "y90":
        points = [(16 - z1, y1, x1), (16 - z2, y2, x2)]
    elif turn == "y180":
        points = [(16 - x1, y1, 16 - z1), (16 - x2, y2, 16 - z2)]
    elif turn == "y270":
        points = [(z1, y1, 16 - x1), (z2, y2, 16 - x2)]
    elif turn == "x270":
        points = [(x1, 16 - z1, y1), (x2, 16 - z2, y2)]
    elif turn == "x90":
        points = [(x1, z1, 16 - y1), (x2, z2, 16 - y2)]
    else:
        raise ValueError(turn)
    lows = [min(a, b) for a, b in zip(*points)]
    highs = [max(a, b) for a, b in zip(*points)]
    return tuple(lows + highs)


_conduit_src = (_power / "PowerConduitBlock.java").read_text(encoding="utf-8")
_declared_boxes = {
    face: tuple(int(n) for n in nums.split(","))
    for face, nums in re.findall(
        r"ARM_SHAPES\.put\(Direction\.([A-Z]+), Block\.box\(([^)]*)\)\)",
        _conduit_src)
}
_core_box = re.search(r"VoxelShape CORE = Block\.box\(([^)]*)\)", _conduit_src)

if gen_solarpunk is None:
    pass
elif _core_box is None or len(_declared_boxes) != 6:
    fail(f"PowerConduitBlock no longer declares a core and six arm shapes "
         f"({len(_declared_boxes)} arm(s) found); the pipe would collide as "
         f"whatever is left")
else:
    _core = tuple(int(n) for n in _core_box.group(1).split(","))
    if _core != tuple(gen_solarpunk.PIPE_CORE):
        fail(f"the conduit's collision core is {_core} and its model's core is "
             f"{tuple(gen_solarpunk.PIPE_CORE)} — a player would stand inside "
             f"the pipe, or stop short of it")
    _north = tuple(gen_solarpunk.PIPE_ARM)
    _derived = {
        "NORTH": _north,
        "EAST": _rotate_box(_north, "y90"),
        "SOUTH": _rotate_box(_north, "y180"),
        "WEST": _rotate_box(_north, "y270"),
        "UP": _rotate_box(_north, "x270"),
        "DOWN": _rotate_box(_north, "x90"),
    }
    for _face, _want in _derived.items():
        if _declared_boxes.get(_face) != _want:
            fail(f"the conduit's {_face} arm collides at "
                 f"{_declared_boxes.get(_face)} and the model draws it at "
                 f"{_want}; the shape a player sees and the shape they walk "
                 f"into have drifted apart")

# 9. The multipart names exactly the properties the block has.
#
# A blockstate naming a property the block does not declare loads as a missing
# model — the purple cube — and the log line for it scrolls past during startup
# among the resource-pack chatter.
_conduit_states = json.load(
    open(RES / f"assets/{NS}/blockstates/power_conduit.json", encoding="utf-8"))
_when_keys = {key for case in _conduit_states.get("multipart", [])
              for key in case.get("when", {})}
_java_props = set(re.findall(
    r"BooleanProperty ([A-Z]+) = BlockStateProperties\.[A-Z]+", _conduit_src))
if _when_keys != {name.lower() for name in _java_props}:
    fail(f"the conduit's blockstate switches on {sorted(_when_keys)} and the "
         f"block declares {sorted(name.lower() for name in _java_props)}; a "
         f"case that names a property the block has not got renders as the "
         f"missing-model cube")
elif len(_conduit_states.get("multipart", [])) != 7:
    fail(f"the conduit's multipart has "
         f"{len(_conduit_states.get('multipart', []))} case(s); it wants a "
         f"core and one arm per face")

# 10. The arms' outward faces are left off, and the core's are not.
#
# An arm that draws its end face z-fights with whatever it is plugged into,
# which reads as a flickering seam along every pipe run in the base.
_arm_model = json.load(open(
    RES / f"assets/{NS}/models/block/power_conduit_arm.json", encoding="utf-8"))
if "north" in _arm_model["elements"][0]["faces"]:
    fail("the conduit's arm draws its outward face, which z-fights with the "
         "block it reaches into")
_core_model = json.load(open(
    RES / f"assets/{NS}/models/block/power_conduit_core.json", encoding="utf-8"))
if len(_core_model["elements"][0]["faces"]) != 6:
    fail("the conduit's core is missing a face, so a pipe with no arm on that "
         "side is see-through")

# ---------------------------------------------------------------------------
# C2: the automation vocabulary
# ---------------------------------------------------------------------------

_gov_src = (_power / "PowerGovernorBlock.java").read_text(encoding="utf-8")

# 11. The conditions the enum has and the ones the language file has are the
# same set. A condition with no words renders as a raw key in the sentence the
# governor says back, which is the only place a player ever reads their rule.
_cond_ids = set(re.findall(r'^\s+[A-Z_]+\("([a-z_]+)"\)[,;]$', _cond_src, re.M))
_cond_lang = {key.rsplit(".", 1)[-1] for key in _lang
              if key.startswith(f"{NS}.power.condition.")}
if _cond_ids != _cond_lang:
    fail(f"PowerCondition and the language file disagree: enum has "
         f"{sorted(_cond_ids - _cond_lang)} extra, language file has "
         f"{sorted(_cond_lang - _cond_ids)} extra")

# 12. The governor's properties span exactly the enums they index.
#
# A range one short and the last condition can never be selected — the cycle
# wraps a click early and the player simply never sees it. A range one long and
# two indices mean the same condition, so one click of the eight does nothing.
# Both are silent.
for _prop, _enum_src, _enum_name in (("CONDITION", _cond_src, "PowerCondition"),
                                     ("HOLD", _pc_src, "PowerClass")):
    _count = len(re.findall(r'^\s+[A-Z_]+\("[a-z_]+"\)[,;]', _enum_src, re.M))
    _found = re.search(_prop + r'\s*=\s*\s*IntegerProperty\.create\((.*?)\);',
                       _gov_src, re.S)
    if _found is None:
        fail(f"the governor no longer declares a {_prop} property")
    elif _enum_name + ".values().length - 1" not in _found.group(1):
        fail(f"the governor's {_prop} range is written as {_found.group(1)!r} "
             f"rather than off {_enum_name}.values().length. A literal here "
             f"goes stale the moment a condition is added, and the new one is "
             f"simply unreachable by clicking")
    elif _count == 0:
        fail(f"no constants could be read out of {_enum_name}")

# 13. The rules are actually applied where power is handed out.
if "PowerRule.held(" not in _push:
    fail("SolarpunkPower no longer consults the grid's rules, so every "
         "governor a player has placed is a decoration")
if "held.contains(classes[i])" not in _push:
    fail("SolarpunkPower reads the held set and does not use it to zero a "
         "want. A held class that still asks for power is a class that still "
         "gets it")

# 14. One walk, not two.
#
# The survey gathers receivers, conduits, governors and the charge together
# because they come from the same traversal. A second call to gather governors
# would double the cost of every generator tick, on the busiest path in the mod.
if _push.count("PowerGrid.survey(") != 1 or "PowerGrid.receivers(" in _push:
    fail("SolarpunkPower walks the grid more than once a tick. The survey "
         "returns receivers and rules together precisely so it does not")

# 15. A governor is read, not walked through.
#
# If the walk queued governors as it queues conduits, placing a rule would
# change where power flows — a player could not annotate their grid without
# rewiring it.
_survey_body = _method_body(_pg_src, "public static Survey survey(")
if _survey_body is None:
    fail("PowerGrid has no survey method")
elif "instanceof PowerGovernorBlock" not in _survey_body:
    # The test, not the name. The body also *calls* PowerGovernorBlock.ruleOf,
    # so looking for the class name alone stayed green with the check that
    # finds a governor replaced by one that never matches — the third time in
    # this codebase a substring search has been unable to fail.
    fail("the grid walk no longer tests for a governor, so rules are never "
         "found and every rule a player has placed does nothing")
elif re.search(r"PowerGovernorBlock\)\s*\{[^}]*queue\.add", _survey_body, re.S):
    fail("the grid walk queues governors as if they were conduit. A rule block "
         "that carried power would mean a player cannot annotate a grid "
         "without rewiring it")

print(f"automation rules   : {len(_cond_ids)} conditions, one action that only "
      f"subtracts, applied in the same walk that finds the receivers")

print(f"power priority     : 5 classes in the spec's order, "
      f"{len(_expected_classes)} machine(s) ranked off industry, a bounded "
      f"grid walk and a budget the harness executes\n"
      f"conduit shape      : a core and six arms, the collision boxes derived "
      f"from the model's one arm by the rotations the blockstate applies")

count = 0
for dirpath, _, filenames in os.walk(RES):
    for filename in filenames:
        if not filename.endswith(".json"):
            continue
        count += 1
        try:
            json.load(open(os.path.join(dirpath, filename), encoding="utf-8"))
        except Exception as exc:
            fail(f"invalid JSON {os.path.join(dirpath, filename)}: {exc}")

print(f"json files parsed  : {count}")
print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)

print("all solarpunk checks passed")
