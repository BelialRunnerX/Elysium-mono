#!/usr/bin/env python3
"""
Validate Elysium Flora against its sources.

Run from anywhere:   python3 elysium-flora/validate.py

The point of this module is narrow and the checks follow it: string and
charcoal become things a player can farm, on a road that is deliberately
slower than the ones they already have. Every rule below is about keeping that
true.

  1. INDEPENDENCE. Vanilla and elysium-lib; no content module, no other mod.
  2. PURITY, and the harness run rather than read.
  3. THE SLOWER ROAD. A crop that outpaid an ore, a fibre that was a string on
     its own, or a fuel worth two coal would each quietly replace the thing it
     was meant to sit beside.
  4. THE PLANTS THEMSELVES. Eight stages, a model per stage, a wild form that
     gives seeds and no fibre, and a drop resolved in Java because it depends
     on who broke it.
  5. THE WOOD. Seventeen blocks, each of which needs a model, a blockstate, a
     loot table, a recipe, a name and a place in the vanilla tags that make a
     plank behave like a plank.
"""
import json
import os
import re
import subprocess
import sys
import tempfile

WORK = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(WORK, "src/main/resources")
SRC = os.path.join(WORK, "src/main/java/com/elysium/flora")
PURE = os.path.join(SRC, "rules")
DATA = os.path.join(RES, "data/elysiumflora")
ASSETS = os.path.join(RES, "assets/elysiumflora")
HARNESS = os.path.join(WORK, "tools/FloraHarness.java")
HARNESS_MAIN = "com.elysium.flora.rules.FloraHarness"
NS = "elysiumflora"

problems = []


def fail(msg):
    problems.append(msg)


def strip_comments(text, keep_strings=False):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    if keep_strings:
        return text
    return re.sub(r'"(?:\\.|[^"\\])*"', '""', text)


def read(*parts):
    path = os.path.join(*parts)
    return open(path, encoding="utf-8").read() if os.path.isfile(path) else ""


def load(*parts):
    path = os.path.join(*parts)
    return json.load(open(path, encoding="utf-8")) if os.path.isfile(path) else None


def body_of(src, signature):
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


def java_files():
    for root, _, names in os.walk(SRC):
        for name in names:
            if name.endswith(".java"):
                yield os.path.join(root, name)


# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------

toml = read(RES, "META-INF/neoforge.mods.toml")
if not toml:
    fail("neoforge.mods.toml is missing")
else:
    for mod, kind in re.findall(
            r'modId\s*=\s*"([a-z0-9_]+)"[\s\S]{0,80}?type\s*=\s*"(\w+)"', toml):
        if mod in ("neoforge", "minecraft", "elysiumlib"):
            continue
        if kind == "required":
            fail(f"{mod} is required; this module is two crops and a tree and needs "
                 f"nothing but the engine")
    if "elysiumlib" not in toml:
        fail("elysiumlib is not declared, but a field's yield reads the world tier")

for path in java_files():
    src = strip_comments(read(path), keep_strings=True)
    rel = os.path.relpath(path, WORK)
    for sibling in ("com.elysium.core", "com.elysium.armory", "com.elysium.space",
                    "com.elysium.mobs", "com.elysium.ores", "com.elysium.dungeons"):
        if sibling in src:
            fail(f"{rel} reaches for {sibling}; this module talks to the library and "
                 f"to vanilla, and nothing else")
    for literal in re.findall(r'"([a-z0-9_]+):[a-z0-9_/]+"', src):
        if literal not in ("minecraft", "c", "neoforge", NS, "elysium", "elysiumlib"):
            fail(f"{rel} names {literal}:...; nothing here belongs to another mod")
print("independence       : the library and vanilla, and nothing else")

# ---------------------------------------------------------------------------
# 2. Purity, and the harness
# ---------------------------------------------------------------------------

for name in sorted(os.listdir(PURE)):
    if not name.endswith(".java"):
        continue
    src = strip_comments(read(PURE, name))
    without_package = re.sub(r"^\s*package [^;]+;", "", src, count=1, flags=re.M)
    for forbidden in ("net.minecraft", "net.neoforged", "com.mojang", "com.elysium"):
        if forbidden in without_package:
            fail(f"rules/{name} reaches for {forbidden}; the rules are the JDK and "
                 f"nothing else, so they can be executed by a harness")

with tempfile.TemporaryDirectory() as tmp:
    staged = os.path.join(tmp, "src")
    os.makedirs(staged)
    sources = [os.path.join(PURE, n) for n in os.listdir(PURE) if n.endswith(".java")]
    for source in sources + [HARNESS]:
        open(os.path.join(staged, os.path.basename(source)), "w",
             encoding="utf-8").write(read(source))
    compiled = subprocess.run(
        ["javac", "-nowarn", "-d", os.path.join(tmp, "out")]
        + [os.path.join(staged, n) for n in os.listdir(staged)],
        capture_output=True, text=True)
    if compiled.returncode != 0:
        fail("the rules do not compile against the JDK alone:\n"
             + compiled.stderr.strip()[:1200])
    else:
        ran = subprocess.run(["java", "-cp", os.path.join(tmp, "out"), HARNESS_MAIN],
                             capture_output=True, text=True)
        if ran.returncode != 0:
            fail("the flora harness failed:\n" + harness_trouble(ran.stdout))
        else:
            print("the arithmetic     : " + ran.stdout.strip().splitlines()[-1])

# ---------------------------------------------------------------------------
# 3. The slower road
# ---------------------------------------------------------------------------
#
# These are checked in the harness too. They are checked again here because the
# harness proves the numbers are consistent with each other and this proves
# they are still the numbers the design chose - a generator that read a
# different constant would pass the harness and ship a different game.

rules = read(PURE, "FloraRules.java")


def constant(name, kind="int"):
    m = re.search(r"public static final " + kind + r" " + name + r" = ([^;]+);", rules)
    return m.group(1).strip() if m else None


if constant("FIBRE_PER_STRING") == "1":
    fail("one fibre is a string, so a single plant replaces a spider and the crop "
         "stops being the slower road it was meant to be")
if constant("WILD_SEEDS") in (None, "0", "1"):
    fail("a wild plant gives at most one seed, so finding one specimen is not enough "
         "to start a field and a discovery becomes an errand")
harvest = body_of(strip_comments(rules), "public static int harvest")
if "bonusChance(" not in harvest:
    fail("the harvest bonus is not rolled against the chance curve, so it either "
         "always or never lands")
if "/ (at + BONUS_PACE)" not in rules:
    fail("the harvest chance does not go through x/(x+b), so it can reach certainty")

# ---------------------------------------------------------------------------
# 4. The plants
# ---------------------------------------------------------------------------

crop_src = strip_comments(read(SRC, "block/FibreCropBlock.java"))
drops = body_of(crop_src, "public List<ItemStack> getDrops")
if "FloraRules.ripe(" not in drops:
    fail("the crop's drops do not check whether it was ripe, so trampling a seedling "
         "pays the same as harvesting a field")
if "FloraRules.harvest(" not in drops:
    fail("the crop's yield is decided somewhere other than the rules, so the world "
         "tier buys nothing in a field")
if "FloraRules.fortuned(amount," not in drops:
    fail("fortune does nothing to the fibre a field gives, whatever it does to the "
         "seeds")
if "ElysiumTiers.of(" not in crop_src:
    fail("the crop never asks what tier the harvester has reached")
if "getMaxAge" not in crop_src or "FloraRules.MATURE" not in crop_src:
    fail("the crop's last stage is not the one the rules name, so the model for the "
         "ripe stage and the stage that is ripe can disagree")

wild_src = strip_comments(read(SRC, "block/WildPlantBlock.java"))
wild_drops = body_of(wild_src, "public List<ItemStack> getDrops")
if "FloraRules.WILD_SEEDS" not in wild_drops:
    fail("a wild plant gives a number of seeds nobody decided")
if "fibre" in wild_drops.lower():
    fail("a wild plant gives fibre, so the wild form is a harvest and nobody plants "
         "anything")

blocks_src = strip_comments(read(SRC, "registry/FloraBlocks.java"))
blocks_literal = strip_comments(read(SRC, "registry/FloraBlocks.java"),
                                keep_strings=True)
if blocks_src.count("noLootTable()") < 4:
    fail("a crop or a wild plant still has a loot table, so the game looks for a file "
         "that will never exist and the block's own answer is shadowed")

stages = None
for crop in ("loomflax", "downbloom"):
    states = load(ASSETS, f"blockstates/{crop}.json")
    if states is None:
        fail(f"{crop} has no blockstate")
        continue
    variants = states.get("variants", {})
    stages = len(variants)
    if stages != 8:
        fail(f"{crop} has {stages} growth stage(s) rather than the eight every other "
             f"crop in the game has")
    for age in range(stages):
        model = os.path.join(ASSETS, f"models/block/{crop}_stage{age}.json")
        texture = os.path.join(ASSETS, f"textures/block/{crop}_stage{age}.png")
        if not os.path.isfile(model):
            fail(f"{crop} stage {age} has no model")
        if not os.path.isfile(texture):
            fail(f"{crop} stage {age} has no texture, so a growing field is invisible")
    for part in (f"textures/block/wild_{crop}.png",
                 f"blockstates/wild_{crop}.json",
                 f"models/item/{crop}_seeds.json",
                 f"textures/item/{crop}_seeds.png"):
        if not os.path.isfile(os.path.join(ASSETS, part)):
            fail(f"wild {crop} is missing {os.path.basename(part)}")
print(f"the plants         : 2 crops, {stages} stages each, wild forms that give seeds "
      f"and no fibre")

# ---------------------------------------------------------------------------
# 5. The wood
# ---------------------------------------------------------------------------

registered = re.findall(r'BLOCKS\.register\("([a-z0-9_]+)"', blocks_literal)
if not registered:
    fail("no blocks are registered at all")

crops_and_wild = {"loomflax", "downbloom", "wild_loomflax", "wild_downbloom"}
wood = [name for name in registered if name not in crops_and_wild]

lang = load(ASSETS, "lang/en_us.json") or {}
for name in registered:
    if not os.path.isfile(os.path.join(ASSETS, f"blockstates/{name}.json")):
        fail(f"{name} has no blockstate, so it renders as the missing model")
    if f"block.{NS}.{name}" not in lang:
        fail(f"{name} has no name, so a player reads a raw key")

for name in wood:
    if not os.path.isfile(os.path.join(DATA, f"loot_table/blocks/{name}.json")):
        fail(f"{name} has no loot table, so breaking it destroys it")

axe = set((load(RES, "data/minecraft/tags/block/mineable/axe.json")
           or {}).get("values", []))
planks = set((load(RES, "data/minecraft/tags/block/planks.json")
              or {}).get("values", []))
if f"{NS}:emberwood_planks" not in planks:
    fail("emberwood planks are not planks, so every vanilla recipe written against "
         "the tag - boats, bowls, sticks, every workbench in the game - refuses them")
for wooden in (f"{NS}:emberwood_planks", f"{NS}:emberwood_stairs",
               f"{NS}:emberwood_fence", f"{NS}:emberwood_log"):
    if wooden not in axe:
        fail(f"{wooden} is not axe-mineable, so it is wood that behaves like stone")

logs = set((load(DATA, "tags/block/emberwood_logs.json") or {}).get("values", []))
if len(logs) < 4:
    fail("the emberwood log tag does not hold all four log forms, so stripping or "
         "smelting one of them silently does nothing")

burn = strip_comments(read(SRC, "registry/FloraItems.java"))
if "FloraRules.CHARCOAL_TICKS" not in burn:
    fail("the charcoal's burn time is a number nobody decided")
if "FloraRules.blockTicks()" not in burn:
    fail("a block of charcoal burns for a time unrelated to the nine that made it")
handler = strip_comments(read(SRC, "event/FloraHandler.java"))
if "FurnaceFuelBurnTimeEvent" not in handler or "setBurnTime" not in handler:
    fail("nothing tells the furnace how long emberwood burns, so the whole point of "
         "the tree is a log that behaves like oak")
print(f"the wood           : {len(wood)} block(s), each modelled, tagged, named and "
      f"droppable")

# ---------------------------------------------------------------------------
# Recipes, and the road they open
# ---------------------------------------------------------------------------

recipe_dir = os.path.join(DATA, "recipe")
recipes = sorted(os.listdir(recipe_dir)) if os.path.isdir(recipe_dir) else []
if not recipes:
    fail("there are no recipes, so nothing this module grows can be turned into "
         "anything")

string_recipes = []
for name in recipes:
    body = load(recipe_dir, name) or {}
    if body.get("result", {}).get("id") == "minecraft:string":
        string_recipes.append(name)
if len(string_recipes) != 2:
    fail(f"{len(string_recipes)} recipe(s) turn this module's fibre into string; there "
         f"are two crops and each needs one, or one of them has no purpose")

for name in string_recipes:
    body = load(recipe_dir, name) or {}
    count = len(body.get("ingredients", []))
    if count < 2:
        fail(f"{name} turns {count} fibre into a string, so one plant is a string")

smelt = load(recipe_dir, "emberwood_charcoal.json") or {}
if smelt.get("type") != "minecraft:smelting":
    fail("emberwood charcoal is not smelted from the log, so the tree gives no fuel")

# ---------------------------------------------------------------------------
# Where they grow
# ---------------------------------------------------------------------------

modifier_dir = os.path.join(DATA, "neoforge/biome_modifier")
placed = set()
if not os.path.isdir(modifier_dir):
    fail("nothing is added to any biome, so none of this generates and the crops "
         "cannot be started at all")
else:
    for name in sorted(os.listdir(modifier_dir)):
        body = load(modifier_dir, name) or {}
        if body.get("step") != "vegetal_decoration":
            fail(f"{name} adds plants at {body.get('step')}; plants belong in "
                 f"vegetal_decoration or they are placed before the ground they need")
        placed.update(body.get("features", []))

for wanted in (f"{NS}:wild_loomflax", f"{NS}:wild_downbloom", f"{NS}:emberwood"):
    if wanted not in placed:
        fail(f"{wanted} is in no biome, so it exists only in the creative tab")
    if not os.path.isfile(os.path.join(
            DATA, f"worldgen/placed_feature/{wanted.split(':')[1]}.json")):
        fail(f"{wanted} is placed into a biome and has no placement file")

tree = load(DATA, "worldgen/configured_feature/emberwood.json") or {}
trunk = json.dumps(tree)
if f"{NS}:emberwood_log" not in trunk:
    fail("the emberwood tree is not made of emberwood")
if f"{NS}:emberwood_leaves" not in trunk:
    fail("the emberwood tree has no emberwood leaves, so it drops no saplings and "
         "cannot be replanted")
print(f"where they grow    : {len(placed)} feature(s) across "
      f"{len(os.listdir(modifier_dir)) if os.path.isdir(modifier_dir) else 0} modifier(s)")

# ---------------------------------------------------------------------------
# The shipped files are what the generators write
# ---------------------------------------------------------------------------
# The mirror shipped emberwood's biome modifier naming a tag, and gen_flora.py
# still wrote the older list-with-a-tag-in-it that the game refuses; nothing
# here compared the two, so the next regeneration put the broken form back and
# the world would not load. Every other generated module rebuilds into a
# scratch tree and diffs; now this one does too.

import sys as _sys
_sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                                 "tools"))
import samefile  # noqa: E402

FLORA_GENERATORS = ["tools/gen_textures.py", "tools/gen_flora.py"]
for relative in FLORA_GENERATORS:
    if not os.path.isfile(os.path.join(WORK, relative)):
        fail(f"there is no {relative}, so the files it writes are kept by hand")
if all(os.path.isfile(os.path.join(WORK, relative)) for relative in FLORA_GENERATORS):
    with tempfile.TemporaryDirectory() as tmp:
        ran_all = True
        for relative in FLORA_GENERATORS:
            made = subprocess.run([sys.executable, os.path.join(WORK, relative)],
                                  capture_output=True, text=True,
                                  env=dict(os.environ, ELYSIUM_FLORA_OUT=tmp), cwd=WORK)
            if made.returncode != 0:
                ran_all = False
                fail(f"{relative} will not run, so nothing it writes can be rebuilt:\n"
                     + (made.stdout + made.stderr).strip()[:800])
        if ran_all:
            fresh = os.path.join(tmp, "src/main/resources")
            drifted = []
            made_count = 0
            for dirpath, _, filenames in os.walk(fresh):
                for name in filenames:
                    made_count += 1
                    made_path = os.path.join(dirpath, name)
                    kept = os.path.join(RES, os.path.relpath(made_path, fresh))
                    if not os.path.isfile(kept):
                        drifted.append(os.path.relpath(made_path, fresh) + " (missing)")
                    elif not samefile.same(made_path, kept):
                        drifted.append(os.path.relpath(made_path, fresh))
            if drifted:
                fail(f"{len(drifted)} generated file(s) differ from what the generators write "
                     f"now; run tools/gen_textures.py then tools/gen_flora.py rather than "
                     f"editing them: " + ", ".join(sorted(drifted)[:6])
                     + (" ..." if len(drifted) > 6 else ""))
            print(f"generated          : {made_count} file(s) rebuilt from the two generators, "
                  f"each the same as the one shipped")

# ---------------------------------------------------------------------------
# Textures the atlas can read
# ---------------------------------------------------------------------------
# A block or item texture is one square frame, or a column of square frames
# with a .png.mcmeta saying so. Crimson stem is five frames in a 16x80 image,
# and the recolour that made emberwood from it once copied the pixels and not
# the metadata: the atlas took the whole column as a single frame and squashed
# it onto every log face. Nothing in the game logs that, so the check is here.

atlas_textures = 0
for folder in ("textures/block", "textures/item"):
    root = os.path.join(ASSETS, folder)
    if not os.path.isdir(root):
        continue
    for name in sorted(os.listdir(root)):
        if not name.endswith(".png"):
            continue
        path = os.path.join(root, name)
        atlas_textures += 1
        try:
            from PIL import Image
            with Image.open(path) as image:
                width, height = image.size
        except ImportError:
            break
        except Exception as error:
            fail(f"{folder}/{name} is not a PNG the game can read: {error}")
            continue
        meta = os.path.isfile(path + ".mcmeta")
        if width == height and not meta:
            continue
        if not meta:
            fail(f"{folder}/{name} is {width}x{height} with no .png.mcmeta, so the atlas "
                 f"squashes the whole column onto one face")
        elif height % width != 0:
            fail(f"{folder}/{name} is {width}x{height}, not a whole number of "
                 f"{width}x{width} frames")
        else:
            animation = (load(ASSETS, f"{folder}/{name}.mcmeta") or {}).get("animation")
            if animation is None:
                fail(f"{folder}/{name}.mcmeta has no animation section, so its "
                     f"{height // width} frames are read as one")
print(f"atlas textures     : {atlas_textures} block and item texture(s), each square or "
      f"declared frames")

# Every texture a model names has to exist. The block side of this was always
# checked through the wood list; the item side was not, and the door item
# (minecraft:item/generated over item/emberwood_door) shipped without its
# texture — a purple-and-black door in every hand, and only a WARN line at
# resource reload to say so.
model_textures = 0
for folder in ("models/block", "models/item"):
    root = os.path.join(ASSETS, folder)
    for name in sorted(os.listdir(root)) if os.path.isdir(root) else []:
        model = load(ASSETS, f"{folder}/{name}") or {}
        for reference in model.get("textures", {}).values():
            if not reference.startswith(f"{NS}:"):
                continue
            model_textures += 1
            texture = os.path.join(ASSETS, "textures", reference.split(":", 1)[1] + ".png")
            if not os.path.isfile(texture):
                fail(f"{folder}/{name} names {reference} and no such texture is shipped")
print(f"model textures     : {model_textures} reference(s) from block and item models, each "
      f"to a shipped file")

# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

for path in java_files():
    raw = strip_comments(read(path), keep_strings=True)
    for key in re.findall(r'"((?:itemGroup\.' + NS + r'|' + NS + r')[\w.]*\.[\w.]+)"',
                          raw):
        if key not in lang:
            fail(f"{os.path.relpath(path, WORK)} shows {key} and nothing translates it")
print(f"language           : {len(lang)} key(s), every literal one reachable")
print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all flora checks passed")
