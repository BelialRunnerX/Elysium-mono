#!/usr/bin/env python3
"""
Validate Elysium Armory against its sources.

Run from the repo root:   python3 elysium-armory/validate.py

Two things matter most for this module, and they are checked first:

  1. INDEPENDENCE. It is Elysium's own replacement for Silent Gear. Nothing in
     it may reach for that mod — no import, no mod id, no dependency, not even
     an optional one — and it depends on elysium-lib and nothing else from
     Elysium. The owner's instruction: minimise dependence on those mods.
  2. PURITY. The engine (the property engine) is compiled against the JDK alone, with no
     Minecraft on the classpath, and its harness is run. If the engine ever
     imports a game class, this fails at the compile step rather than
     quietly dropping the harness.
"""
import json
import os
import re
import subprocess
import sys
import tempfile

WORK = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(WORK, "src/main/resources")
SRC = os.path.join(WORK, "src/main/java/com/elysium/armory")
PURE = os.path.join(SRC, "property")
HARNESS = os.path.join(WORK, "tools/PropertyHarness.java")
HARNESS_MAIN = "com.elysium.armory.property.PropertyHarness"

problems = []


def fail(msg):
    problems.append(msg)


def strip_comments(text, keep_strings=False):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    if keep_strings:
        return text
    return re.sub(r'"(?:\\.|[^"\\])*"', '""', text)


if not os.path.isdir(SRC):
    print("This is not the elysium-armory tree.")
    sys.exit(1)

# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------
FOREIGN = re.compile(r"(apotheosis|apothic|shadowsoffire|placebo|silentgear|silent_gear|"
                     r"silentchaos|silentlib)", re.I)
java_files = []
for dirpath, _, filenames in os.walk(SRC):
    for name in filenames:
        if name.endswith(".java"):
            java_files.append(os.path.join(dirpath, name))
for path in java_files:
    raw = open(path, encoding="utf-8").read()
    code = strip_comments(raw)
    rel = os.path.relpath(path, WORK)
    # Searched with string literals kept: a soft dependency is written as
    # ModList.get().isLoaded("silentgear"), and that name is only ever in a string.
    hit = FOREIGN.search(strip_comments(raw, keep_strings=True))
    if hit:
        fail(f"{rel} refers to {hit.group(0)!r} in code. This module replaces that "
             f"mod; it may not depend on it, even softly")
    for imp in re.findall(r"\bimport\s+(?:static\s+)?([\w.]+)\s*;", raw):
        if imp.startswith("com.elysium.") and not (
                imp.startswith("com.elysium.lib.") or imp.startswith("com.elysium.armory.")):
            fail(f"{rel} imports {imp}. This module depends on elysium-lib and "
                 f"nothing else from Elysium")
toml_path = os.path.join(RES, "META-INF/neoforge.mods.toml")
toml = open(toml_path, encoding="utf-8").read() if os.path.isfile(toml_path) else ""
toml_code = "\n".join(l for l in toml.splitlines() if not l.strip().startswith("#"))
deps = re.findall(r'modId\s*=\s*"([^"]+)"', toml_code)
extra = [d for d in deps if d not in ("${mod_id}", "neoforge", "minecraft", "elysiumlib")]
if not toml:
    fail("META-INF/neoforge.mods.toml is missing")
elif extra:
    fail(f"mods.toml declares a dependency on {extra}. The module needs the "
         f"library and nothing else")
elif "elysiumlib" not in deps:
    fail("mods.toml does not require elysiumlib, which this module is built on")
print(f"independence       : {len(java_files)} source file(s), no reference to "
      f"Silent Gear, depends on the library only")

# ---------------------------------------------------------------------------
# 2. Purity: the engine compiles on the JDK alone, and its harness runs
# ---------------------------------------------------------------------------
pure_files = []
for dirpath, _, filenames in os.walk(PURE):
    for name in filenames:
        if name.endswith(".java"):
            pure_files.append(os.path.join(dirpath, name))
# One pure file may lean on another pure file in the library, and exactly one
# does: the gear-type tree lives in ElysiumGearTree so that traits, affixes,
# gems and runes all read the same answer to "what is a sword". That file is
# itself JDK-only and is compiled with the engine below, so the rule the purity
# check is really protecting — no registry, no game, runnable by a harness —
# still holds. Anything else from outside the JDK is still refused.
LIB_PURE = {"com.elysium.lib.item.ElysiumGearTree":
            os.path.join(WORK, "../elysium-lib/src/main/java/com/elysium/lib/item/ElysiumGearTree.java")}
borrowed = []
for path in pure_files:
    # Every import, wherever it sits on the line: a scan anchored to the start
    # of a line misses a second import sharing one, which is exactly how a
    # non-JDK dependency would slip in unnoticed.
    for imp in re.findall(r"\bimport\s+(?:static\s+)?([\w.]+)\s*;",
                          open(path, encoding="utf-8").read()):
        if imp in LIB_PURE:
            borrowed.append(LIB_PURE[imp])
            continue
        if not imp.startswith("java."):
            fail(f"{os.path.relpath(path, WORK)} imports {imp}; the engine imports "
                 f"nothing but the JDK, so a harness can run it without a game")
for name, source in LIB_PURE.items():
    if not os.path.isfile(source):
        fail(f"{name} is gone from the library; the armory reads its gear-type tree from it")
        continue
    for imp in re.findall(r"\bimport\s+(?:static\s+)?([\w.]+)\s*;", open(source, encoding="utf-8").read()):
        if not imp.startswith("java."):
            fail(f"{name} imports {imp}; the armory's engine borrows it, so it has to stay "
                 f"as pure as the engine is")
borrowed = sorted(set(borrowed))
if not pure_files:
    fail(f"the engine directory {os.path.relpath(PURE, WORK)} is empty")
elif not os.path.isfile(HARNESS):
    fail(f"{os.path.relpath(HARNESS, WORK)} is missing; the engine is only read, never run")
else:
    with tempfile.TemporaryDirectory() as out:
        compiled = subprocess.run(["javac", "-nowarn", "-d", out] + pure_files + borrowed + [HARNESS],
                                  capture_output=True, text=True)
        text = "\n".join(l for l in (compiled.stdout + compiled.stderr).splitlines()
                         if "JAVA_TOOL_OPTIONS" not in l)
        if compiled.returncode != 0:
            fail("the engine does not compile on the JDK alone:\n      "
                 + text.replace("\n", "\n      "))
        else:
            ran = subprocess.run(["java", "-cp", out, HARNESS_MAIN],
                                 capture_output=True, text=True)
            lines = [l for l in (ran.stdout + ran.stderr).splitlines()
                     if "JAVA_TOOL_OPTIONS" not in l]
            if ran.returncode != 0:
                fail("the PropertyHarness failed:\n      " + "\n      ".join(lines))
            else:
                print(f"property engine    : " + (lines[0] if lines else "ran"))

# ---------------------------------------------------------------------------
# 3. Materials and parts (SPEC_CLONE P2)
# ---------------------------------------------------------------------------
# The data is the system here: 132 materials and 48 part definitions, each a
# file. MaterialHarness loads every one of them with the mod's own loader,
# folds the parent chains in and builds whole pieces of gear from them. What it
# cannot see is whether the files still say what the inventory they were read
# from says — so the generators are re-run into a scratch directory and the
# result compared byte for byte with what ships.
MATERIALS = os.path.join(RES, "data/elysiumarmory/elysium_materials")
TRAITS = os.path.join(RES, "data/elysiumarmory/elysium_traits")
PARTS = os.path.join(RES, "data/elysiumarmory/elysium_parts")
material_files = [os.path.join(d, f) for d, _, fs in os.walk(MATERIALS) for f in fs if f.endswith(".json")]
part_files = [os.path.join(d, f) for d, _, fs in os.walk(PARTS) for f in fs if f.endswith(".json")]
if len(material_files) < 132:
    fail(f"only {len(material_files)} material file(s); the inventory lists 132")
if len(part_files) < 48:
    fail(f"only {len(part_files)} part file(s); the inventory lists 48")

import filecmp
import shutil
with tempfile.TemporaryDirectory() as scratch:
    for name, script, shipped in (("materials", "tools/gen_materials.py", MATERIALS),
                                  ("parts", "tools/gen_parts.py", PARTS),
                                  ("traits", "tools/gen_traits.py", TRAITS)):
        out = os.path.join(scratch, name)
        os.makedirs(out)
        run = subprocess.run([sys.executable, os.path.join(WORK, script), out],
                             capture_output=True, text=True, cwd=WORK)
        if run.returncode != 0:
            fail(f"{script} no longer runs: {run.stderr.strip()[-300:]}")
            continue
        diff = filecmp.dircmp(out, shipped)
        drift = []
        def walk(cmp_result, prefix=""):
            drift.extend(prefix + n for n in cmp_result.left_only + cmp_result.right_only + cmp_result.diff_files)
            for sub, child in cmp_result.subdirs.items():
                walk(child, prefix + sub + "/")
        walk(diff)
        if drift:
            fail(f"the shipped {name} are not what {script} generates from the inventory: {sorted(drift)[:5]}")
trait_files = [f for f in os.listdir(TRAITS) if f.endswith(".json")] if os.path.isdir(TRAITS) else []
if len(trait_files) < 75:
    fail(f"only {len(trait_files)} trait file(s); the inventory lists 75")
# Every trait a material grants has a name and a description a player can read.
for trait_file in trait_files:
    tid = os.path.splitext(trait_file)[0]
    for suffix in ("", ".desc"):
        key = f"elysiumarmory.trait.{tid}{suffix}"
        if key not in json.load(open(os.path.join(RES, "assets/elysiumarmory/lang/en_us.json"),
                                     encoding="utf-8")):
            fail(f"trait {tid} has no {key}")
            break
print(f"data               : {len(material_files)} materials, {len(part_files)} parts and "
      f"{len(trait_files)} traits, exactly what the inventory states")

# ---------------------------------------------------------------------------
# 3b. The Cordon's materials (docs/SPLITS.md "Valoria: armour")
# ---------------------------------------------------------------------------
#
# Valoria's armour sets stand on elysium-ores' roster, and the materials here
# are generated from it. The drift check above proves the files are what the
# generator writes; this proves the generator read the roster that is there,
# so a set added to the roster is a material here and a number changed there
# is a number changed here.
_roster = os.path.join(WORK, "..", "elysium-ores", "src", "main", "java", "com", "elysium", "ores",
                       "rules", "CordonMaterials.java")
_roster_src = open(_roster, encoding="utf-8").read() if os.path.isfile(_roster) else ""
_roster_src = re.sub(r"//[^\n]*", " ", re.sub(r"/\*.*?\*/", " ", _roster_src, flags=re.S))
if not _roster_src:
    fail("elysium-ores' CordonMaterials.java is missing, so the Cordon's armour stands on nothing")
_cordon = re.findall(r'\b(?:dug|alloy|found)\("([a-z_]+)",\s*(?:Kind\.(\w+),\s*)?(?:OreRules\.\w+,\s*)?'
                     r'Element\.\w+,\s*"#[0-9a-f]{6}",\s*(null|new Tier\((\d+), (\d+), [\d.]+F, [\d.]+F, \d+\)),'
                     r'\s*(null|new Plate\((\d+), (\d+), (\d+), ([\d.]+)F, ([\d.]+)F\))', _roster_src)
_armour_sets = dict(re.findall(r'new Set\("([a-z_]+)", "([a-z_]+)", (?:true|false), true\)', _roster_src))
_cordon_files = 0
for family, kind, tool, level, uses, plate, protection, durability, enchant, knockback, null_defence in _cordon:
    if tool == "null" and plate == "null":
        continue
    path = os.path.join(MATERIALS, family + ".json")
    if not os.path.isfile(path):
        fail(f"{family} is in the roster and has no material here, so its gear cannot be made")
        continue
    _cordon_files += 1
    body = json.load(open(path, encoding="utf-8"))
    main = body.get("properties", {}).get("main", {})
    if plate != "null":
        if main.get("armor") != float(protection):
            fail(f"{family}'s armour is {main.get('armor')} here and {protection} in the roster")
        if main.get("armor_durability") != float(durability):
            fail(f"{family}'s armour durability is {main.get('armor_durability')} here and {durability} in the roster")
        if main.get("knockback_resistance") != float(knockback):
            fail(f"{family}'s knockback resistance disagrees with the roster")
    if tool != "null" and main.get("durability") != float(uses):
        fail(f"{family}'s durability is {main.get('durability')} here and {uses} in the roster")
    if "cordon" not in body.get("crafting", {}).get("categories", []):
        fail(f"{family} is not in the cordon category, so nothing can ask for the Cordon's materials as a group")
    if not main.get("traits"):
        fail(f"{family} grants no trait, so its set is a stat block and not a reason to wear it")
    else:
        for trait in main["traits"]:
            if not os.path.isfile(os.path.join(TRAITS, trait["trait"] + ".json")):
                fail(f"{family} grants {trait['trait']}, a trait that does not exist")
for valoria_set, family in _armour_sets.items():
    if not os.path.isfile(os.path.join(MATERIALS, family + ".json")):
        fail(f"Valoria's {valoria_set} set had armour and its family {family} has no material here")
print(f"the cordon         : {_cordon_files} of Valoria's materials, each armoured as Valoria numbered it, "
      f"in the cordon category, granting a trait that exists")

mc_libs = os.environ.get("ELYSIUM_MC_LIBS", "/tmp/mc")
classpath_file = os.path.join(mc_libs, "cp.txt")
_out_root = os.environ.get("ELYSIUM_OUT", "/tmp/elysium-classes")
classes = os.path.join(_out_root, "elysium-armory")
# The armory reads its gear-type tree and its effect engine from the library,
# so the harness needs the library's classes as well as its own.
lib_classes = os.path.join(_out_root, "elysium-lib")
MATERIAL_HARNESS = os.path.join(WORK, "tools/MaterialHarness.java")
if not os.path.isfile(MATERIAL_HARNESS):
    fail("tools/MaterialHarness.java is missing; the material files are only read, never loaded")
elif not (os.path.isfile(classpath_file) and os.path.isdir(classes)):
    print(f"material harness   : not run, no compiled classes or classpath ({classpath_file})")
else:
    with tempfile.TemporaryDirectory() as out:
        # Joined with the platform's separator, so the harness runs wherever
        # the classpath file was written for.
        cp = os.pathsep.join([open(classpath_file, encoding="utf-8").read().strip(),
                              classes, lib_classes])
        compiled = subprocess.run(["javac", "-nowarn", "-cp", cp, "-d", out, MATERIAL_HARNESS],
                                  capture_output=True, text=True)
        if compiled.returncode != 0:
            fail("MaterialHarness does not compile:\n      "
                 + "\n      ".join(l for l in (compiled.stdout + compiled.stderr).splitlines()
                                   if "JAVA_TOOL_OPTIONS" not in l)[:800])
        else:
            ran = subprocess.run(["java", "-cp", cp + os.pathsep + out,
                                  "com.elysium.armory.material.MaterialHarness", MATERIALS],
                                 capture_output=True, text=True)
            lines = [l for l in (ran.stdout + ran.stderr).splitlines()
                     if "JAVA_TOOL_OPTIONS" not in l and not l.startswith("SLF4J")]
            if ran.returncode != 0:
                fail("the MaterialHarness failed:\n      " + "\n      ".join(lines))
            else:
                print("material harness   : " + (lines[0] if lines else "ran"))

# ---------------------------------------------------------------------------
# 4. The items (SPEC_CLONE P3)
# ---------------------------------------------------------------------------
# Items are registered in code at startup; the parts that describe them are
# data loaded later. The two lists have to agree, or a player crafts a part
# that no item exists for — which is a crash at the bench, not at load. So they
# are compared here, along with the art and names every registered item needs.
items_src = strip_comments(open(os.path.join(SRC, "registry/ArmoryItems.java"), encoding="utf-8").read(),
                           keep_strings=True)
def listed(name):
    block = re.search(r"List<String> " + name + r" = List\.of\((.*?)\);", items_src, re.S)
    return re.findall(r'"([a-z_]+)"', block.group(1)) if block else []
GEAR_ITEMS = listed("GEAR")
PART_ITEMS = listed("PARTS")
part_ids = sorted(os.path.splitext(f)[0] for f in os.listdir(PARTS) if f.endswith(".json"))
if sorted(PART_ITEMS) != part_ids:
    fail(f"the part items and the part files disagree: "
         f"{sorted(set(PART_ITEMS) ^ set(part_ids))}")
main_gear = sorted({json.load(open(os.path.join(PARTS, f), encoding="utf-8"))["gear_type"]
                    for f in os.listdir(PARTS) if f.endswith(".json")
                    and json.load(open(os.path.join(PARTS, f), encoding="utf-8"))["part_type"] == "main"})
if sorted(GEAR_ITEMS) != main_gear:
    fail(f"the gear items and the gear types with a main part disagree: "
         f"{sorted(set(GEAR_ITEMS) ^ set(main_gear))}")

lang_path = os.path.join(RES, "assets/elysiumarmory/lang/en_us.json")
lang = json.load(open(lang_path, encoding="utf-8")) if os.path.isfile(lang_path) else {}
EXTRA_ITEMS = ["blueprint_paper", "blueprint", "template", "mod_kit",
               "affix_essence", "sheet_metal"]
BENCHES = ["gear_smithing_table", "salvager", "reforging_table", "metal_press", "stone_anvil",
           "alloy_forge", "material_grader", "starlight_charger"]
for item in GEAR_ITEMS + PART_ITEMS + EXTRA_ITEMS + BENCHES:
    # A bench is a block: its name is on the block, not the item.
    named = (f"block.elysiumarmory.{item}" if item in BENCHES
             else f"item.elysiumarmory.{item}")
    if named not in lang:
        fail(f"item {item} has no name; it would show a raw key in every inventory")
    model_path = os.path.join(RES, "assets/elysiumarmory/models/item", item + ".json")
    if not os.path.isfile(model_path):
        fail(f"item {item} has no model")
        continue
    for layer in json.load(open(model_path, encoding="utf-8")).get("textures", {}).values():
        texture = os.path.join(RES, "assets/elysiumarmory/textures",
                               layer.split(":", 1)[1] + ".png") if ":" in layer else None
        if texture and not os.path.isfile(texture):
            fail(f"item {item} draws {layer}, which is missing")
# ---------------------------------------------------------------------------
# The benches (SPEC_CLONE P6)
# ---------------------------------------------------------------------------
# Loot becomes materials and materials become better gear. Each bench is a
# block, a model, a loot table and a recipe, and a bench missing any of them is
# either invisible, unbreakable or uncraftable — none of which the compiler has
# an opinion about.
for bench in BENCHES:
    for path, what in (
            (f"assets/elysiumarmory/blockstates/{bench}.json", "blockstate"),
            (f"assets/elysiumarmory/models/block/{bench}.json", "block model"),
            (f"assets/elysiumarmory/models/item/{bench}.json", "item model"),
            (f"data/elysiumarmory/loot_table/blocks/{bench}.json", "loot table"),
            (f"data/elysiumarmory/recipe/{bench}.json", "recipe")):
        if not os.path.isfile(os.path.join(RES, path)):
            fail(f"the {bench} has no {what}; it would be "
                 + {"blockstate": "invisible", "block model": "invisible",
                    "item model": "invisible in the hand", "loot table": "unbreakable",
                    "recipe": "uncraftable"}[what])
    for side in ("top", "side"):
        if not os.path.isfile(os.path.join(RES,
                f"assets/elysiumarmory/textures/block/{bench}_{side}.png")):
            fail(f"the {bench} has no {side} texture")
blocks_src = strip_comments(open(os.path.join(SRC, "registry/ArmoryBlocks.java"),
                                 encoding="utf-8").read(), keep_strings=True)
for bench in BENCHES:
    # The block's own registration, not any mention of the name: the block item
    # is registered under the same string a line later, and a bare search for
    # it passed on a block that was never registered at all.
    if f'BLOCKS.register("{bench}"' not in blocks_src:
        fail(f"the {bench} is drawn and named and never registered")

# ---------------------------------------------------------------------------
# What mines a bench (ArmoryHarvestRules; the harvest audit of 29 Sep 2026)
# ---------------------------------------------------------------------------
# A block breaks at the speed of the tool the game lists it under, and one
# that asks for the right tool drops nothing unless some tool is listed as
# right. The audit found every bench in no tool's tag. So every registered
# block is in exactly one of this module's mineable tags, or is named BY_HAND
# in the rules with its reason here; an exemption is read where the block is
# registered, not taken on trust; the tags are what the rules say, written by
# tools/gen_harvest.py; and a tag names only blocks this module registers,
# because one unknown id makes the game refuse the whole tag.
HARVEST_RULES = os.path.join(PURE, "ArmoryHarvestRules.java")
_harvest_src = (strip_comments(open(HARVEST_RULES, encoding="utf-8").read(), keep_strings=True)
                if os.path.isfile(HARVEST_RULES) else "")
if not _harvest_src:
    fail("property/ArmoryHarvestRules.java is missing, so nothing says what mines a bench "
         "and the tags are written from nothing")


def _harvest_list(name):
    found = re.search(r"List<String> " + name + r" = List\.of\(([^;]*?)\);", _harvest_src, re.S)
    return re.findall(r'"([a-z_]+)"', found.group(1)) if found else []


HARVEST_TOOLS = ("pickaxe", "axe", "shovel", "hoe")
MINEABLE = {}
for _tool in HARVEST_TOOLS:
    _tag = os.path.join(RES, "data/minecraft/tags/block/mineable", _tool + ".json")
    if not os.path.isfile(_tag):
        continue
    _doc = json.load(open(_tag, encoding="utf-8"))
    if _doc.get("replace"):
        fail(f"mineable/{_tool} sets replace: true, which throws away every other mod's "
             f"entries in the game's own tag")
    for _one in _doc.get("values", []):
        _named = _one.get("id") if isinstance(_one, dict) else _one
        if not isinstance(_named, str):
            continue
        MINEABLE.setdefault(_named, set()).add(_tool)
        if _named.startswith("elysiumarmory:") and not os.path.isfile(os.path.join(
                RES, "assets/elysiumarmory/blockstates", _named.split(":", 1)[1] + ".json")):
            fail(f"mineable/{_tool} names {_named}, which is not a block this module "
                 f"registers, so the game refuses the whole tag and every block in it "
                 f"is back to dropping nothing")

#: The blocks no tool is for, and why. ArmoryHarvestRules.BY_HAND says which;
#: a block it names with no reason here is refused, and so is a reason for a
#: block it no longer names. Nothing, today: every bench is metal or stone.
MINE_EXEMPT = {}

#: What the game's blocks this module copies ask of a tool, read out of the
#: game's own jar. No bench copies one; a bench that starts to is looked up
#: here or refused.
ASKS_FOR_A_TOOL = {}


def asks_for_a_tool(made, what):
    """Whether a block built like this asks for the right tool, and how."""
    if not made:
        fail(f"nothing here finds how {what} is built, so nothing shows whether it "
             f"asks for the right tool")
        return None, ""
    if ".requiresCorrectToolForDrops()" in made:
        return True, "it says so where it is registered"
    for copied in re.findall(r"\bBlocks\.([A-Z_]+)", made):
        if copied not in ASKS_FOR_A_TOOL:
            fail(f"{what} copies Blocks.{copied}, and nothing here knows whether that "
                 f"asks for the right tool: look it up in the game's jar and add it")
            return None, ""
        if ASKS_FOR_A_TOOL[copied]:
            return True, f"it copies Blocks.{copied}, which does"
    return False, ""


_registered_blocks = re.findall(r'BLOCKS\.register\("([a-z_]+)"', blocks_src)
_blockstate_dir = os.path.join(RES, "assets/elysiumarmory/blockstates")
for _name in sorted(os.path.splitext(f)[0] for f in os.listdir(_blockstate_dir)
                    if f.endswith(".json")):
    if _name not in _registered_blocks:
        fail(f"{_name} has a blockstate and no registration in ArmoryBlocks, so nothing "
             f"below can say what mines it")
_by_hand = _harvest_list("BY_HAND")
for _name in _by_hand:
    if _name not in MINE_EXEMPT:
        fail(f"ArmoryHarvestRules breaks {_name} by hand and this gives no reason, so it "
             f"is left out of every tool's tag on nobody's say-so")
for _name in MINE_EXEMPT:
    if _name not in _by_hand:
        fail(f"a reason is given for breaking {_name} by hand and ArmoryHarvestRules no "
             f"longer does, so the exemption covers a block the rules put in a tool's tag")
_rule_tools = {}
for _tool in HARVEST_TOOLS:
    for _name in _harvest_list(_tool.upper()):
        _rule_tools.setdefault(_name, []).append(_tool)
for _name in sorted(set(_rule_tools) | set(_by_hand)):
    if _name not in _registered_blocks:
        fail(f"ArmoryHarvestRules says what mines {_name}, and no such block is "
             f"registered, so the tag it is written into is refused whole")
_flat_blocks = re.sub(r"\s+", " ", blocks_src)
_tool_blocks = []
_asking_untagged = []
_untagged = []
for _name in _registered_blocks:
    _start = _flat_blocks.find(f'BLOCKS.register("{_name}"')
    _made = _flat_blocks[_start:_flat_blocks.find(";", _start)] if _start >= 0 else ""
    _asks, _how = asks_for_a_tool(_made, f"the {_name}")
    _tags = MINEABLE.get(f"elysiumarmory:{_name}", set())
    if _asks:
        _tool_blocks.append(_name)
    if len(_tags) > 1:
        fail(f"{_name} is in {len(_tags)} mineable tags ({', '.join(sorted(_tags))}), so "
             f"two tools are right and the rules can only have named one")
    if _name in MINE_EXEMPT:
        if _asks:
            fail(f"{_name} is exempt from the mineable tags ({MINE_EXEMPT[_name]}), but it "
                 f"asks for the right tool: {_how}. With no tag no tool is right, so it "
                 f"would drop nothing")
        if _tags:
            fail(f"{_name} is exempt from the mineable tags and in one all the same; take "
                 f"it off MINE_EXEMPT or out of the tag")
        continue
    if not _tags:
        (_asking_untagged if _asks else _untagged).append(_name)
    if sorted(_rule_tools.get(_name, [])) != sorted(_tags):
        fail(f"the rules put {_name} under {' and '.join(_rule_tools.get(_name)) or 'no tool'} "
             f"and the tags under {' and '.join(sorted(_tags)) or 'none'}; the tags are "
             f"written from the rules by tools/gen_harvest.py, so one of them was edited "
             f"by hand")
if _asking_untagged:
    fail(f"{len(_asking_untagged)} block(s) ask for the right tool and are in no tool's "
         f"tag, so no tool is ever right and they drop nothing however they are mined: "
         + ", ".join(_asking_untagged))
if _untagged:
    fail(f"{len(_untagged)} block(s) are in no mineable tag and not exempt, so every tool "
         f"breaks them at a fist's pace: " + ", ".join(_untagged))
with tempfile.TemporaryDirectory() as _scratch:
    _ran = subprocess.run([sys.executable, os.path.join(WORK, "tools/gen_harvest.py"), _scratch],
                          capture_output=True, text=True, cwd=WORK)
    if _ran.returncode != 0:
        fail(f"tools/gen_harvest.py no longer runs: {_ran.stderr.strip()[-300:]}")
    else:
        _sub = "data/minecraft/tags/block/mineable"
        _made, _shipped = os.path.join(_scratch, _sub), os.path.join(RES, _sub)
        _names = (set(os.listdir(_made)) if os.path.isdir(_made) else set()) | \
            (set(os.listdir(_shipped)) if os.path.isdir(_shipped) else set())
        for _name in sorted(_names):
            _one, _two = os.path.join(_made, _name), os.path.join(_shipped, _name)
            if not (os.path.isfile(_one) and os.path.isfile(_two)
                    and filecmp.cmp(_one, _two, shallow=False)):
                fail(f"{_sub}/{_name} is not what tools/gen_harvest.py writes from "
                     f"ArmoryHarvestRules")
print(f"what mines them    : {len(_registered_blocks)} block(s), {len(_tool_blocks)} asking "
      f"for the right tool, every one in exactly one tool's tag or broken by hand with a "
      f"reason: " + ", ".join(f"{sum(1 for t in _rule_tools.values() if t == [tool])} by {tool}"
                              for tool in HARVEST_TOOLS
                              if any(t == [tool] for t in _rule_tools.values()))
      + f", {len(MINE_EXEMPT)} by hand")

# Essence and sheet metal are salvaged and pressed. A crafting recipe for
# either would be a way around the two benches that exist to produce them.
for made in ("affix_essence", "sheet_metal"):
    if os.path.isfile(os.path.join(RES, f"data/elysiumarmory/recipe/{made}.json")):
        fail(f"{made} has a crafting recipe, which is a way around the bench that makes it")
for key in ("elysiumarmory.salvager.button", "elysiumarmory.salvager.nothing",
            "elysiumarmory.reforge.cost", "elysiumarmory.reforge.target",
            "elysiumarmory.reforge.no_essence", "elysiumarmory.press.only_ingots",
            "elysiumarmory.forge.nothing", "elysiumarmory.grader.nothing",
            "elysiumarmory.grader.needs_gem", "elysiumarmory.grader.graded",
            "elysiumarmory.grader.crude", "elysiumarmory.charger.nothing"):
    if key not in lang:
        fail(f"{key} has no translation; a bench would speak in raw keys")

salvage_src = strip_comments(open(os.path.join(SRC, "craft/ArmorySalvage.java"),
                                  encoding="utf-8").read(), keep_strings=True)
reforge_src = strip_comments(open(os.path.join(SRC, "craft/ArmoryReforge.java"),
                                  encoding="utf-8").read(), keep_strings=True)
salvage_menu = strip_comments(open(os.path.join(SRC, "menu/ArmorySalvagerMenu.java"),
                                   encoding="utf-8").read(), keep_strings=True)
reforge_menu = strip_comments(open(os.path.join(SRC, "menu/ArmoryReforgingMenu.java"),
                                   encoding="utf-8").read(), keep_strings=True)
if "ArmorySalvageRules.chance(" not in salvage_src:
    fail("salvage does not roll against the rule that says what survives, so every part "
         "would come back and crafting would be an operation you could undo")
if "AFFIX_ESSENCE" not in salvage_src:
    fail("salvaging an affixed piece yields no essence, so a reroll could never be paid for")
if "input.setItem(0, ItemStack.EMPTY)" not in salvage_menu:
    fail("the salvager does not consume what it took apart; a single sword would salvage "
         "forever")
if "placeItemBackInInventory" not in salvage_menu:
    fail("results that do not fit on the bench are dropped on the floor of the code — that "
         "was the player's metal")
if "seed" not in reforge_menu or "ArmoryReforge.choices(" not in reforge_menu:
    fail("the reforging table does not roll its three from a seed it keeps, so reopening "
         "the screen would reroll them and the choice would be a free lever")
if "inputs.removeItem(1, cost)" not in reforge_menu:
    fail("taking a reforged piece does not spend the essence it cost")
press_src = strip_comments(open(os.path.join(SRC, "block/ArmoryMetalPressBlock.java"),
                                encoding="utf-8").read(), keep_strings=True)
if "INGOTS" not in press_src or "c" not in press_src:
    fail("the metal press takes anything, not ingots")
if "addCooldown" not in press_src:
    fail("the press has no cooldown, so a full inventory presses in a single tick")
logic_for_enchanting = strip_comments(open(os.path.join(SRC, "item/ArmoryGearLogic.java"),
                                           encoding="utf-8").read(), keep_strings=True)
if "enchantmentValue(" not in logic_for_enchanting or "isBroken(" not in logic_for_enchanting:
    fail("gear has no enchanting rules of its own, so a piece would enchant like the "
         "material class it had to extend rather than like what it is made of")
for gear_class in ("item/ArmoryGearItem.java", "item/ArmoryArmorItem.java"):
    source = strip_comments(open(os.path.join(SRC, gear_class), encoding="utf-8").read())
    if "isEnchantable" not in source:
        fail(f"{gear_class} never refuses an enchantment on a broken piece, so a table "
             f"would take a player's levels for something that does not work")
for recipe, serializer in (("repair", "REPAIR"), ("paint", "PAINT")):
    path = os.path.join(RES, "data/elysiumarmory/recipe", recipe + ".json")
    if not os.path.isfile(path) or json.load(open(path, encoding="utf-8"))["type"] != f"elysiumarmory:{recipe}":
        fail(f"the {recipe} recipe is missing or names another serializer")
    if serializer not in strip_comments(open(os.path.join(SRC, "registry/ArmoryRecipes.java"),
                                             encoding="utf-8").read()):
        fail(f"the {recipe} serializer is not registered")
# Materials science (SPEC_CLONE P8). Three marks on a part — how well it was
# worked, what it was left out in, and what its materials earned by getting
# along — and three benches that set them. What is checked is that each one
# writes only its own mark, that a mark reaches the gear it is built into, and
# that the charger, which is the only thing here that waits, does not swallow
# what it is holding.
def method_body(text, signature):
    """One method's source, brace-matched from its declaration.

    A check scoped to a file is a check a constant satisfies by being declared
    at the top of it. These have to be reachable from the rule, not merely
    present in the file the rule lives in.
    """
    start = text.find(signature)
    if start < 0:
        return ""
    opened = text.find("{", start)
    depth = 0
    for index in range(opened, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[opened:index + 1]
    return text[opened:]


alloy_src = strip_comments(open(os.path.join(SRC, "craft/ArmoryAlloy.java"),
                                encoding="utf-8").read(), keep_strings=True)
grading_src = strip_comments(open(os.path.join(SRC, "craft/ArmoryGrading.java"),
                                  encoding="utf-8").read(), keep_strings=True)
charger_src = strip_comments(open(os.path.join(SRC, "block/ArmoryStarlightChargerBlockEntity.java"),
                                  encoding="utf-8").read(), keep_strings=True)
charger_block = strip_comments(open(os.path.join(SRC, "block/ArmoryStarlightChargerBlock.java"),
                                    encoding="utf-8").read(), keep_strings=True)
grader_src = strip_comments(open(os.path.join(SRC, "block/ArmoryMaterialGraderBlock.java"),
                                 encoding="utf-8").read(), keep_strings=True)
builder_marks = strip_comments(open(os.path.join(SRC, "item/ArmoryBuilder.java"),
                                    encoding="utf-8").read(), keep_strings=True)
if "ArmoryMaterialScience.synergy(" not in alloy_src:
    fail("the forge does not read the synergy rule, so what mixing is worth would be "
         "decided somewhere nobody can execute")
if "withSynergy(" not in alloy_src:
    fail("the forge computes a synergy and writes nothing, so forging a part does nothing")
if "marks(part).synergy() <= 1.0D" not in alloy_src:
    fail("a part can be forged twice, which is a bonus a player can apply as many times as "
         "they like")
if "ArmoryMaterialScience.graded(" not in grading_src:
    fail("grading does not roll against the rule, so the risk would be decided in the block")
if "marks.withGrade(" not in grading_src or "marks(part).charged()" not in grading_src:
    fail("a mark is written by something other than the one place that writes marks")
if "canCharge(" not in grading_src or "starcharged()" not in grading_src:
    fail("a part can take the charge more than once")
if "payment.shrink(1)" not in grader_src:
    fail("grading costs nothing when it fails, which makes it a free attempt with extra steps")
if "level.canSeeSky(" not in charger_src or "level.isDay()" not in charger_src:
    fail("the charger counts time it cannot see the stars in, so a charger under a roof "
         "would work exactly as well as one outside")
if "Block.popResource(" not in charger_block:
    fail("breaking the charger swallows what is on it")
_logic_marks = strip_comments(open(os.path.join(SRC, "item/ArmoryGearLogic.java"),
                                   encoding="utf-8").read(), keep_strings=True)
# The multiplication, not the mention: the file reads the component in a getter
# a few lines away, and a bare search for that passed on a recompute that had
# stopped applying it.
_recompute = method_body(_logic_marks, "public static void recompute(")
if "marks.factor()" not in _recompute or "entry.getValue() * factor" not in _recompute:
    fail("a piece's marks are never applied when its numbers are computed, so grading and "
         "charging a part would change nothing about the gear built from it")
if "combine(parts)" not in builder_marks:
    fail("the marks on the parts never reach the gear they are assembled into")
# The five named mixtures. They sat in five files that nothing read for a long
# time: every mixed part went on being called after whichever material was laid
# down first, so a blade of three metals was an "Iron Blade". A compound the
# rule can never name is that file again.
compounds_src = strip_comments(open(os.path.join(SRC, "material/ArmoryCompounds.java"),
                                    encoding="utf-8").read(), keep_strings=True)
_compound_files = sorted(os.path.splitext(f)[0] for f in os.listdir(MATERIALS)
                         if f.endswith(".json")
                         and json.load(open(os.path.join(MATERIALS, f), encoding="utf-8"))
                         .get("type") == "compound")


_classify = method_body(compounds_src, "public static String classify(")
if not _classify:
    fail("ArmoryCompounds has no classify method, so no mixture is named after what it is")
for _compound in _compound_files:
    if _compound.upper() not in _classify:
        fail(f"the {_compound} material is a name in a file that no mixture can ever be, "
             f"which is what this rule exists to end")
if "ArmoryCompounds.named(" not in builder_marks:
    fail("a mixed part is not named after what it is, so every alloy would be called after "
         "whichever material happened to be laid down first")
print(f"compounds          : {len(_compound_files)} named mixtures, each one a rule can reach")
print(f"benches            : {len(BENCHES)} benches, each drawn, named, breakable and craftable; "
      f"salvage rolls, reforge keeps its seed, the press takes ingots, "
      f"the charger only counts nights it can see")

if "block.elysiumarmory.gear_smithing_table" not in lang:
    fail("the Gear Smithing Table has no name")
for key in ("elysiumarmory.blueprint.blank", "elysiumarmory.blueprint.needs", "itemGroup.elysiumarmory",
            "elysiumarmory.mod_kit.nothing", "elysiumarmory.prospector.finds",
            "elysiumarmory.prospector.nothing"):
    if key not in lang:
        fail(f"{key} has no translation")

# The sprites are grey on purpose: the material's colour arrives at render time,
# and a sprite with a colour baked in would come out of the tint the wrong hue.
from PIL import Image


def coloured(path):
    image = Image.open(path).convert("RGBA")
    for x in range(image.width):
        for y in range(image.height):
            r, g, b, a = image.getpixel((x, y))
            if a and (max(r, g, b) - min(r, g, b)) > 24:
                return True
    return False


for item in GEAR_ITEMS + PART_ITEMS:
    path = os.path.join(RES, "assets/elysiumarmory/textures/item", item + ".png")
    if os.path.isfile(path) and coloured(path):
        fail(f"{item}.png carries a colour of its own; the sprites are tinted by material and must be grey")

# Every gear type is the vanilla class the game needs it to be, or it is not
# equippable, drawable or chargeable whatever its numbers say.
registry_src = strip_comments(open(os.path.join(SRC, "registry/ArmoryItems.java"), encoding="utf-8").read(),
                              keep_strings=True)
CLASS_FOR = {"helmet": "ArmoryArmorItem", "chestplate": "ArmoryArmorItem", "leggings": "ArmoryArmorItem",
             "boots": "ArmoryArmorItem", "elytra": "ArmoryElytraItem", "shield": "ArmoryShieldItem",
             "bow": "ArmoryRangedItems.Bow", "crossbow": "ArmoryRangedItems.Crossbow",
             "trident": "ArmoryRangedItems.Trident", "fishing_rod": "ArmoryRangedItems.Rod",
             "arrow": "ArmoryRangedItems.Arrow"}
for gear_type, wanted in CLASS_FOR.items():
    case = re.search(r'case ([^-]*"' + gear_type + r'"[^-]*)->\s*(?:new )?([\w.]+)', registry_src)
    if not case or wanted not in case.group(2):
        fail(f"{gear_type} is not built as a {wanted}; the game would not treat it as one")
for item in ("modular_layer_1", "modular_layer_2"):
    if not os.path.isfile(os.path.join(RES, "assets/elysiumarmory/textures/models/armor", item + ".png")):
        fail(f"the worn-armour texture {item} is missing; modular armour would render as missing")
builder_src = strip_comments(open(os.path.join(SRC, "item/ArmoryBuilder.java"), encoding="utf-8").read())
if "DYED_COLOR" not in builder_src:
    fail("armour is never dyed with its material's colour, so every piece would be worn grey")
logic_src = strip_comments(open(os.path.join(SRC, "item/ArmoryGearLogic.java"), encoding="utf-8").read(),
                           keep_strings=True)
for attribute in ("draw_speed", "arrow_damage"):
    if f'ElysiumAttributes.get("{attribute}")' not in logic_src:
        fail(f"ranged gear does not speak through the library's {attribute} attribute, "
             f"so P1's bow rules would not see it")
tools_src = strip_comments(open(os.path.join(SRC, "event/ArmoryToolEvents.java"), encoding="utf-8").read(),
                           keep_strings=True)
for habit in ('"hammer", "excavator"', '"saw"', '"machete", "sickle"'):
    if habit not in tools_src:
        fail(f"the wide tools no longer answer for {habit}")
if "BREAKING" not in tools_src:
    fail("the wide tools have no re-entry guard; one swing would break the world in a loop")

# Traits (SPEC_CLONE P4): every effect kind the data uses is acted on, the
# fifteen whose behaviour is code have that code, and nothing reads a trait
# without going through the one reader.
trait_effects_src = strip_comments(open(os.path.join(SRC, "trait/ArmoryTraitEffects.java"),
                                        encoding="utf-8").read(), keep_strings=True)
# The handlers moved to the library in P4.2: they are written once for every
# attachment an item can have — traits, affixes, gems, runes — so a trait's
# effect and an affix's are acted on by the same code. They are still what
# answers for the armory's trait kinds, so this is still where they are read.
LIB_EFFECT = os.path.join(WORK, "../elysium-lib/src/main/java/com/elysium/lib/effect")
if not os.path.isdir(LIB_EFFECT):
    fail("the library's effect engine is gone; every trait effect kind would be "
         "stated by the data and read by nothing")
handlers_src = ""
for _name in sorted(os.listdir(LIB_EFFECT)) if os.path.isdir(LIB_EFFECT) else []:
    if _name.endswith(".java"):
        handlers_src += strip_comments(open(os.path.join(LIB_EFFECT, _name),
                                            encoding="utf-8").read(), keep_strings=True)
properties_src = strip_comments(open(os.path.join(SRC, "trait/ArmoryTraitProperties.java"),
                                     encoding="utf-8").read(), keep_strings=True)
coded_src = strip_comments(open(os.path.join(SRC, "trait/ArmoryCodedTraits.java"),
                                encoding="utf-8").read(), keep_strings=True)
events_src = strip_comments(open(os.path.join(SRC, "event/ArmoryTraitEvents.java"),
                                 encoding="utf-8").read(), keep_strings=True)
acted_on = handlers_src + properties_src + coded_src + events_src +     strip_comments(open(os.path.join(SRC, "item/ArmoryGearLogic.java"), encoding="utf-8").read(),
                   keep_strings=True)
used_kinds = set()
for trait_file in trait_files:
    for effect in json.load(open(os.path.join(TRAITS, trait_file), encoding="utf-8"))["effects"]:
        used_kinds.add(effect["type"])
for kind in sorted(used_kinds):
    if kind == "code":
        continue
    if f'"{kind}"' not in acted_on:
        fail(f"traits state a '{kind}' effect, but nothing in the mod reads one")
# The gear-type tree is the library's, read from there rather than kept here. A
# second copy is a second opinion about what a sword is, and the moment the two
# disagree a player is holding a sword one system can see and another cannot.
_type_src = open(os.path.join(SRC, "property/ArmoryGearType.java"), encoding="utf-8").read()
if "ElysiumGearTree" not in _type_src:
    fail("ArmoryGearType no longer reads the library's gear tree; the armory would be "
         "keeping its own copy of the taxonomy that affixes, gems and runes also name")

# The words a trait uses have to be words the library's engine knows, or the
# trait states an effect in a vocabulary nothing else in the mod speaks — which
# is exactly the four-implementations problem P4 exists to end.
_kinds_file = os.path.join(LIB_EFFECT, "ElysiumEffectKinds.java")
if os.path.isfile(_kinds_file):
    _kinds_src = open(_kinds_file, encoding="utf-8").read()
    _vocabulary = set(re.findall(r'm\.put\(\s*"([a-z_]+)"', _kinds_src))
    _vocabulary.add("code")
    _outside = sorted(k for k in used_kinds if k not in _vocabulary)
    if _outside:
        fail(f"traits state {_outside}, which the library's effect vocabulary does not "
             f"name. Affixes, gems and runes read that vocabulary, so a kind outside it "
             f"is a kind only traits can ever use")

coded_traits = sorted(os.path.splitext(f)[0] for f in trait_files
                      if any(e["type"] == "code"
                             for e in json.load(open(os.path.join(TRAITS, f), encoding="utf-8"))["effects"]))
missing_code = [t for t in coded_traits if t not in coded_src and t not in events_src]
if missing_code:
    fail(f"these traits say their behaviour is code, and it is not there: {missing_code}")
# Written is not the same as run. Every method the coded traits expose has to be
# called from somewhere else in the mod, or the trait is a page of code the game
# never reaches — which reads as done and plays as nothing.
callers = ""
for dirpath, _, filenames in os.walk(SRC):
    for filename in filenames:
        if filename.endswith(".java") and filename != "ArmoryCodedTraits.java":
            callers += strip_comments(open(os.path.join(dirpath, filename), encoding="utf-8").read(),
                                      keep_strings=True)
uncalled = [m for m in re.findall(r"public static [\w.<>\[\] ]+? (\w+)\(", coded_src)
            if re.search(r"\b" + m + r"\s*\(", callers) is None]
if uncalled:
    fail(f"these coded traits are written and never called, so they do nothing in game: {sorted(uncalled)}")
if "ArmoryCodedTraits.widen(" not in tools_src:
    fail("a wide tool's radius no longer comes from the Widen trait, so the trait is decoration")
print(f"traits             : {len(trait_files)} traits, {len(used_kinds) - 1} effect kinds all acted on, "
      f"{len(coded_traits)} carried in code")

main_src_early = strip_comments(open(os.path.join(SRC, "ElysiumArmory.java"), encoding="utf-8").read(),
                                keep_strings=True)

# Loot (SPEC_CLONE P4.7). Two pieces a datapack cannot do without: asking what
# the tool in hand is, and handing out a piece of modular gear that is actually
# built. A registered type whose codec names another class, or a class whose
# getType names another registration, is a loot table that fails to parse at
# load — so the pair is checked rather than assumed.
loot_src = ""
for _name in ("ArmoryLoot.java", "ArmoryGearCondition.java", "ArmorySetPartsFunction.java"):
    _path = os.path.join(SRC, "loot", _name)
    if not os.path.isfile(_path):
        fail(f"loot/{_name} is missing; a pack could not ask what broke a block, "
             f"or hand out gear with parts in it")
    else:
        loot_src += strip_comments(open(_path, encoding="utf-8").read(), keep_strings=True)
if loot_src:
    for _registered, _codec in (('"gear"', "ArmoryGearCondition.CODEC"),
                                ('"set_parts"', "ArmorySetPartsFunction.CODEC")):
        if _registered not in loot_src or _codec not in loot_src:
            fail(f"the loot type {_registered} is not registered with {_codec}")
    if "ArmoryLoot.GEAR.get()" not in loot_src or "ArmoryLoot.SET_PARTS.get()" not in loot_src:
        fail("a loot piece does not name its own registration back, so a loot table "
             "using it would fail to parse at load")
    if "ArmoryLoot.register(modEventBus)" not in main_src_early:
        fail("ArmoryLoot is never put on the mod bus, so both types would be missing "
             "and every loot table naming one would fail to load")
    # The condition reads a trait through the shared engine, which is what lets a
    # pack name an affix there and have it work.
    if "ElysiumItemEffects.level(" not in loot_src:
        fail("the gear condition reads traits some other way than through the effect "
             "engine, so naming an affix in a loot table would silently never match")
    if "ElysiumLootCategory.of(" not in loot_src:
        fail("the gear condition does not resolve the tool's kind, so 'mined with a "
             "hammer' could not be written")
    print("loot               : a gear condition and a set_parts function, both "
          "registered and both reading the shared engine")

client_src = strip_comments(open(os.path.join(SRC, "client/ArmoryClient.java"), encoding="utf-8").read())
if "RegisterColorHandlersEvent" not in client_src or "ArmorySmithingScreen::new" not in client_src:
    fail("the client does not tint the sprites, or the bench has no screen registered")
main_src = strip_comments(open(os.path.join(SRC, "ElysiumArmory.java"), encoding="utf-8").read())
for registry in ("ArmoryComponents", "ArmoryItems", "ArmoryRecipes", "ArmoryBlocks", "ArmoryMenus"):
    if f"{registry}.register(modEventBus)" not in main_src:
        fail(f"{registry} is never put on the mod bus; everything it holds would be missing")
for recipe, serializer in (("compound_part", "COMPOUND_PART"), ("gear_assembly", "GEAR_ASSEMBLY"),
                           ("conversion", "CONVERSION")):
    path = os.path.join(RES, "data/elysiumarmory/recipe", recipe + ".json")
    if not os.path.isfile(path) or json.load(open(path, encoding="utf-8"))["type"] != f"elysiumarmory:{recipe}":
        fail(f"the {recipe} recipe is missing or names another serializer")
    if serializer not in strip_comments(open(os.path.join(SRC, "registry/ArmoryRecipes.java"),
                                             encoding="utf-8").read()):
        fail(f"the {recipe} serializer is not registered")
# Every upgrade part can be made, or it is an item nobody can ever hold.
for part_file in os.listdir(PARTS):
    part = json.load(open(os.path.join(PARTS, part_file), encoding="utf-8"))
    if part["type"] != "upgrade":
        continue
    name = os.path.splitext(part_file)[0]
    if not os.path.isfile(os.path.join(RES, "data/elysiumarmory/recipe", name + ".json")):
        fail(f"the upgrade {name} has no recipe; nothing could ever craft it")
print(f"items              : {len(GEAR_ITEMS)} gear and {len(PART_ITEMS)} part items, "
      f"matching the data, drawn grey and named")

# ---------------------------------------------------------------------------
# 5. All JSON parses
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
print(f"json files parsed  : {count}")
print()

# ---------------------------------------------------------------------------
# Sample gear in the creative tab (SPEC_CLONE P12.22)
# ---------------------------------------------------------------------------
#
# A modular sword registered as an item is an empty sword: no blade, no
# material, no damage and no name worth reading. A creative player who takes
# one out of the tab reasonably concludes the mod is broken.

SAMPLES = os.path.join(SRC, "item/ArmorySamples.java")
ITEMS_JAVA = os.path.join(SRC, "registry/ArmoryItems.java")
if not os.path.isfile(SAMPLES):
    fail("the creative tab offers only unbuilt gear, which has no damage, no durability "
         "and no name worth reading")
else:
    samples = strip_comments(open(SAMPLES, encoding="utf-8").read(), True)
    tab = strip_comments(open(ITEMS_JAVA, encoding="utf-8").read(), True)
    if "ArmorySamples.all()" not in tab:
        fail("the samples are built and never put in the tab")
    if "ArmoryBuilder.gear(" not in samples or "ArmoryBuilder.part(" not in samples:
        fail("a sample is assembled by hand rather than through the builder the "
             "assembly recipe uses, so the creative sword and the crafted one are two "
             "different swords")
    if "ArmoryMaterials.get().byId(MATERIAL) == null" not in samples:
        fail("the samples do not check that their material exists. Parts and materials "
             "are datapack data and there is none in the main menu, so this is a crash "
             "or a row of missing textures before a world is even loaded")
    if "partType().equals(\"main\")" not in samples:
        fail("a sample is built from whatever part comes first rather than from the one "
             "that decides what the piece is")
    if "return samples;" not in samples:
        fail("the sample list has no empty answer, so there is no graceful way for it "
             "to say the data is not loaded")
    print("sample gear        : one built piece per gear type, through the same builder "
          "the recipe uses")

# ---------------------------------------------------------------------------
# What the client is told (SPEC_CLONE P12.21)
# ---------------------------------------------------------------------------
#
# Parts, materials and traits are datapack data, which lives on the server. A
# screen or a tooltip that read them directly would be right in single player
# and wrong on every dedicated server — the failure that is hardest to find,
# because the developer's own world is the one configuration where it works.
#
# The answer this module uses is that the client is sent the *result* rather
# than the data: everything a built piece needs is a data component with a
# stream codec, written when the piece is assembled. So the rule is simply
# that nothing under client/ asks a reload listener anything.

DATA_REGISTRIES = ("ArmoryParts.get()", "ArmoryMaterials.get()", "ArmoryTraits.get()")
client_dir = os.path.join(SRC, "client")
for filename in sorted(os.listdir(client_dir)) if os.path.isdir(client_dir) else []:
    if not filename.endswith(".java"):
        continue
    text = strip_comments(open(os.path.join(client_dir, filename), encoding="utf-8").read(),
                          True)
    for registry in DATA_REGISTRIES:
        if registry in text:
            fail(f"client/{filename} reads {registry}, which is datapack data and lives "
                 f"on the server. It would be right in single player and empty on every "
                 f"dedicated server — the client is sent the result, as components on "
                 f"the stack, not the data")
components = strip_comments(
    open(os.path.join(SRC, "registry/ArmoryComponents.java"), encoding="utf-8").read(), True)
if components.count("networkSynchronized(") < 5:
    fail("fewer than five of the gear components are network-synchronised. Everything a "
         "built piece shows a player is a component, and one that is not synced is a "
         "tooltip that is blank on a server")
print("what the client sees: results as synced components, no datapack read from client/")

# ---------------------------------------------------------------------------
# Jewellery: worn in a slot this module is not allowed to know about
# ---------------------------------------------------------------------------
#
# A ring is built here and worn somewhere else. The whole of the arrangement is
# that the library holds a list in the middle, so this module never names an
# accessory mod and the accessory bridge never names a ring. Both halves of
# that are checked, because either one quietly going away leaves jewellery that
# can be made and not worn — which is exactly what this row of the spec was
# open on for months.

_logic = open(os.path.join(SRC, "item/ArmoryGearLogic.java"), encoding="utf-8").read()
_items_src = open(os.path.join(SRC, "registry/ArmoryItems.java"), encoding="utf-8").read()
_main_src = open(os.path.join(SRC, "ElysiumArmory.java"), encoding="utf-8").read()

_wearable = re.findall(r'"(ring|bracelet|necklace)"', _items_src)
# The method itself rather than the whole file: "ring" appears in the parts
# list, in the item list and in a comment, and a rule satisfied by any of those
# would pass on a switch that had lost its case.
_accessory_slot = _logic[_logic.find("public static String accessorySlot("):]
_accessory_slot = _accessory_slot[:_accessory_slot.find("\n    }")]
for _kind in ("ring", "bracelet", "necklace"):
    if _kind not in _wearable:
        fail(f"{_kind} is no longer a gear type, so a part exists for a shape with no "
             f"item")
    if f'"{_kind}"' not in _accessory_slot:
        fail(f"{_kind} is not named as an accessory slot, so a {_kind} is built with a "
             f"sword's attribute modifiers and grants attack damage from a finger")
    _tag = os.path.join(RES, f"data/curios/tags/item/{_kind}.json")
    if not os.path.isfile(_tag):
        fail(f"there is no curios tag for {_kind}, so the slot refuses it however "
             f"well it is registered")
    else:
        _body = json.load(open(_tag, encoding="utf-8"))
        _named = json.dumps(_body)
        if f"elysiumarmory:{_kind}" not in _named:
            fail(f"the curios {_kind} tag does not name our {_kind}")
        if '"required": false' not in _named and '"required":false' not in _named:
            fail(f"the curios {_kind} tag demands the item exists, so a pack with the "
                 f"tag and without this module logs an error every launch")

if "ElysiumWearables.register(" not in _items_src:
    fail("nothing declares this module's jewellery wearable, so a ring is an item "
         "that can be made and never worn")
if "declareWearables" not in _main_src:
    fail("the wearables are declared by nobody, so the list the accessory bridge "
         "reads is empty")
if "FMLCommonSetupEvent" not in _main_src:
    fail("the wearables are declared from the mod constructor, where the items do "
         "not exist yet - which is the crash this repository has had twice")
if "EquipmentSlotGroup.ANY" not in _logic:
    fail("jewellery's modifiers are written for a vanilla slot group, and an "
         "accessory slot is not one of those, so a worn ring grants nothing")
_jewel = re.search(r"private static void jewel\(", _logic)
if not _jewel:
    fail("jewellery has no attribute branch of its own")
for _forbidden in ("top.theillusivec4", "curios.api"):
    for _path, _, _names in os.walk(SRC):
        for _name in _names:
            if not _name.endswith(".java"):
                continue
            if _forbidden in open(os.path.join(_path, _name), encoding="utf-8").read():
                fail(f"{_name} names {_forbidden}; this module builds jewellery and "
                     f"is not allowed to know which mod holds the slot it is worn in")

print(f"jewellery          : {len(set(_wearable))} kind(s) declared wearable through "
      f"the library, tagged for the slot, and no accessory mod named here")

# ---------------------------------------------------------------------------
# Showing the working
# ---------------------------------------------------------------------------
#
# The one thing a player cannot otherwise find out is why a piece came out the
# way it did: the materials are server data, the computation is a pipeline, and
# the tooltip shows the answer rather than the working. The command is the
# working.

_command_file = os.path.join(SRC, "command/ArmoryCommand.java")
if not os.path.isfile(_command_file):
    fail("there is no way to ask a piece what it is made of, so the only account of "
         "why a sword hits for 9.4 is reading the datapack")
else:
    _command = open(_command_file, encoding="utf-8").read()
    for _needed, _why in (
            ("partsOf(", "the parts are not shown, so the one thing only this command "
                         "can say is the thing it does not say"),
            ("statsOf(", "the computed properties are not shown"),
            ("TRAITS", "the traits are not shown, so a piece whose material granted "
                       "one looks identical to one that did not"),
            # The call, not the declaration: a method named sorted that
            # nobody calls sorts nothing.
            ("sorted(stats.values())",
             "the properties come out in whatever order the map had, so two "
             "printouts of the same sword cannot be compared")):
        if _needed not in _command:
            fail(f"the properties command does not use {_needed}: {_why}")
    for _key in ("elysiumarmory.properties.header", "elysiumarmory.properties.part",
                 "elysiumarmory.properties.value", "elysiumarmory.properties.trait"):
        if _key not in lang:
            fail(f"{_key} has no translation, so the command prints a raw key")
    # The lock Silent Gear pairs with this is deliberately absent; a lock that
    # crept back in would be a second source of truth for every number.
    if "statLock" in _command or "stat_lock" in _command:
        fail("a stat lock has appeared: a locked number is a second source of truth "
             "for something the parts already decide, and the two disagree the moment "
             "a material is edited")

print("showing the working: parts, properties and traits, in a stable order, and no "
      "locked numbers")

# ---------------------------------------------------------------------------
# The plan book
# ---------------------------------------------------------------------------
#
# It stands in for a blueprint, and everything about it turns on it standing in
# for *one* blueprint: a book that answered for two plans at once would make
# whichever the search happened to find first, which is a recipe that produces
# different things on different days.

_book = strip_comments(open(os.path.join(SRC, "item/ArmoryPlanBookItem.java"),
                            encoding="utf-8").read(), keep_strings=True)
_book_data = strip_comments(open(os.path.join(SRC, "item/ArmoryPlanBook.java"),
                                 encoding="utf-8").read(), keep_strings=True)
_book_recipe = strip_comments(open(os.path.join(SRC, "recipe/ArmoryPlanBookRecipe.java"),
                                   encoding="utf-8").read(), keep_strings=True)
_part_recipe = strip_comments(open(os.path.join(SRC, "recipe/ArmoryCompoundPartRecipe.java"),
                                   encoding="utf-8").read(), keep_strings=True)

# Each of these is scoped to the body of the method that has to do the work.
# A token loose in a file is satisfied by a declaration, an import or a name
# that merely starts the same way, and every one of those has passed a rule of
# ours before.
_part_read = method_body(_part_recipe, "private static Found read(CraftingInput input)")
_only_known = method_body(_part_recipe, "private static ArmoryPart onlyKnown(")
_book_read = method_body(_book_recipe, "private static Found read(CraftingInput input)")
_remaining = method_body(_book_recipe,
                         "public NonNullList<ItemStack> getRemainingItems(CraftingInput input)")
_learn = method_body(_book_data, "public ArmoryPlanBook learn(String part)")

if "part = onlyKnown(book, bench);" not in _part_read:
    fail("a plan book is not resolved against the materials beside it, so a book "
         "that knows two plans makes whichever the search found first")
if not re.search(r"if \(only != null\) \{\s*return null;", _only_known):
    fail("a book that knows two plans which both fit the bench picks one of them "
         "instead of refusing, so the same grid makes different things")
if not re.search(r"ArmoryPlanBookItem\b.*?reusable = true;.*?book = stack;", _part_read, re.S):
    fail("the plan book is consumed when it is used, so knowing something is spent "
         "by using it")
if not re.search(r"if \(!plan\.reusable\(\)\) \{\s*return null;", _book_read):
    fail("a template can be learned, which turns a plan you were given once into a "
         "plan you know forever")
if "NonNullList.withSize(input.size(), ItemStack.EMPTY)" not in _remaining:
    fail("the book is both the result and returned as a remainder, so reading a "
         "blueprint makes a second book")
if "knows(part)" not in _learn:
    fail("a plan learned twice is learned twice, so a book grows a list of "
         "duplicates")
if "more.sort(" not in _learn:
    fail("the plans are kept in the order they were learned, so two books that know "
         "the same plans are different items")
if "fits(found.part(), found.materials())" not in _part_recipe:
    fail("the compound recipe and the book resolver test the materials by different "
         "rules, so a book can plan a part the bench cannot make")
for _needed in ("item.elysiumarmory.plan_book", "elysiumarmory.planbook.count"):
    if _needed not in lang:
        fail(f"{_needed} has no translation")
if not os.path.isfile(os.path.join(RES, "assets/elysiumarmory/textures/item/plan_book.png")):
    fail("the plan book has no sprite")
if not os.path.isfile(os.path.join(RES, "data/elysiumarmory/recipe/plan_book_item.json")):
    fail("the plan book cannot be made")
if not os.path.isfile(os.path.join(RES, "data/elysiumarmory/recipe/plan_book.json")):
    fail("nothing teaches the plan book, so it can never learn a plan")

print("the plan book      : learns plans, answers for one at a time, and a template "
      "is still a template")

# ---------------------------------------------------------------------------
# Survival: where essence and templates come from, and what a recipe charges
# ---------------------------------------------------------------------------
#
# The reachability check (check_obtainable.py, 29 September 2026) found two
# items here nobody could have, and the reading around them two recipes that
# did not do what they said. Affix essence came only from salvaging modular
# gear with affixes on it, and a modular piece carries affixes only after a
# reforge, which is paid in essence. The template was registered and made by
# nothing. The conversion recipe took a shape out of a map whose order changes
# every launch, so a pickaxe converted on some starts and not others. And three
# parts named an extra ingredient the grid never asked for, one of them an item
# that does not exist. Each is held here to the statement that fixed it.


def _squashed(text):
    """One method's source on one line, so a statement wrapped over two lines
    reads as the statement it is."""
    return re.sub(r"\s+", " ", text)


# Essence: any affixed piece goes on the salvager, and a reforged one pays back
# no more than the reroll cost.
if ("return !stack.isEmpty() && ArmorySalvageRules.salvageable(modular, affixLevels(stack));"
        not in method_body(salvage_src, "public static boolean canSalvage(ItemStack stack)")):
    fail("the salvager does not take any affixed piece, so essence can never be had: a modular piece "
         "carries affixes only after a reforge, and a reforge is paid in essence")
if ("int essence = ArmorySalvageRules.essence(affixLevels(gear), paidFor(gear));"
        not in method_body(salvage_src, "public static List<ItemStack> salvage(")):
    fail("salvage pays essence without asking what a reforge cost, so rerolling a wooden sword and "
         "taking it apart prints essence")
if ("return stack.getOrDefault(ArmoryComponents.REFORGED.get(), 0);"
        not in method_body(salvage_src, "public static int paidFor(ItemStack stack)")):
    fail("salvage reads what a reforge cost from somewhere other than the mark the reforge leaves")
if ("copy.set(ArmoryComponents.REFORGED.get(), cost(targetRarity));"
        not in method_body(reforge_src, "public static List<ItemStack> choices(")):
    fail("a reforged piece does not record what it cost, so salvage cannot cap what it pays back")
if 'TYPES.register("reforged"' not in components:
    fail("the mark a reforge leaves is never registered, so nothing can carry it")

# Templates: found in the chests the rules name, blank, and read on the server
# from the same list of plans the Gear Smithing Table offers.
_blueprint_src = strip_comments(open(os.path.join(SRC, "item/ArmoryBlueprintItem.java"),
                                     encoding="utf-8").read(), keep_strings=True)
_table_src = strip_comments(open(os.path.join(SRC, "block/ArmorySmithingTableBlock.java"),
                                 encoding="utf-8").read(), keep_strings=True)
if ("if (ArmoryPlanRules.plannable(part.isUpgrade(), part.crafting().materials())) {"
        not in method_body(_blueprint_src, "public static List<String> plannable()")):
    fail("the plans are not listed by the rule, so the table and a found template can disagree "
         "about what can be planned")
if ("List<String> parts = ArmoryBlueprintItem.plannable();"
        not in method_body(_table_src, "protected InteractionResult useWithoutItem(")):
    fail("the Gear Smithing Table keeps a list of plans of its own, so a found template can plan a "
         "part the table never draws, or the other way round")
_tick = _squashed(method_body(_blueprint_src, "public void inventoryTick("))
for _statement in ("if (reusable || level.isClientSide() || partOf(stack) != null) { return; }",
                   "List<String> plans = plannable();",
                   "int drawn = ArmoryPlanRules.drawn(plans.size(), level.getRandom().nextDouble());",
                   "plan(stack, plans.get(drawn));"):
    if _statement not in _tick:
        fail(f"a found template is never read into a plan, so it stays blank and makes nothing "
             f"(inventoryTick has lost \"{_statement}\")")
        break
_plan_rules = strip_comments(open(os.path.join(PURE, "ArmoryPlanRules.java"), encoding="utf-8").read(),
                             keep_strings=True)
_found_rows = re.findall(r'new Found\("([a-z0-9_:/.]+)", ([0-9.]+)D\)', _plan_rules)
if not _found_rows:
    fail("ArmoryPlanRules finds a template in no chest, so the template is an item nobody can hold")
_global_file = os.path.join(RES, "data/neoforge/loot_modifiers/global_loot_modifiers.json")
_listed = set(json.load(open(_global_file, encoding="utf-8")).get("entries", [])
              if os.path.isfile(_global_file) else [])
for _chest, _chance in _found_rows:
    _name = "template_from_" + _chest.rsplit("/", 1)[1]
    _file = os.path.join(RES, "data/elysiumarmory/loot_modifiers", _name + ".json")
    _doc = json.load(open(_file, encoding="utf-8")) if os.path.isfile(_file) else {}
    _when = {c.get("condition"): c for c in _doc.get("conditions", []) if isinstance(c, dict)}
    if (_doc.get("type") != "neoforge:add_table" or _doc.get("table") != "elysiumarmory:found/template"
            or len(_when) != 2
            or _when.get("neoforge:loot_table_id", {}).get("loot_table_id") != _chest
            or _when.get("minecraft:random_chance", {}).get("chance") != float(_chance)):
        fail(f"the rules find a template in {_chest}, one chest in {round(1 / float(_chance))}, and no "
             f"loot modifier says so")
    if f"elysiumarmory:{_name}" not in _listed:
        fail(f"elysiumarmory:{_name} is written and never listed in global_loot_modifiers.json, so the "
             f"game never applies it and that chest never holds a template")
_found_file = os.path.join(RES, "data/elysiumarmory/loot_table/found/template.json")
_found_doc = json.load(open(_found_file, encoding="utf-8")) if os.path.isfile(_found_file) else {}
_found_names = [entry.get("name") for pool in _found_doc.get("pools", []) for entry in pool.get("entries", [])]
if _found_names != ["elysiumarmory:template"]:
    fail(f"the found table holds {_found_names}, not one template, so a chest that should hold a plan "
         f"holds something else")
with tempfile.TemporaryDirectory() as _scratch:
    _ran = subprocess.run([sys.executable, os.path.join(WORK, "tools/gen_loot.py"), _scratch],
                          capture_output=True, text=True, cwd=WORK)
    if _ran.returncode != 0:
        fail(f"tools/gen_loot.py no longer runs: {_ran.stderr.strip()[-300:]}")
    else:
        for _sub in ("data/elysiumarmory/loot_table/found", "data/elysiumarmory/loot_modifiers",
                     "data/neoforge/loot_modifiers"):
            _made, _shipped = os.path.join(_scratch, _sub), os.path.join(RES, _sub)
            _names = (set(os.listdir(_made)) if os.path.isdir(_made) else set()) | \
                (set(os.listdir(_shipped)) if os.path.isdir(_shipped) else set())
            for _name in sorted(_names):
                _one, _two = os.path.join(_made, _name), os.path.join(_shipped, _name)
                if not (os.path.isfile(_one) and os.path.isfile(_two)
                        and filecmp.cmp(_one, _two, shallow=False)):
                    fail(f"{_sub}/{_name} is not what tools/gen_loot.py writes from ArmoryPlanRules")

# The conversion: the rule reads the name, and every material it names is one.
_conversion = strip_comments(open(os.path.join(SRC, "recipe/ArmoryConversionRecipe.java"),
                                  encoding="utf-8").read(), keep_strings=True)
if ("return ArmoryConversionRules.of(key.getPath());"
        not in method_body(_conversion, "static ArmoryConversionRules.Conversion of(ItemStack stack)")):
    fail("the conversion recipe does not ask ArmoryConversionRules what a piece becomes, so the rule "
         "the harness holds is not the one the game runs")
if "endsWith(" in _conversion or "Map.of" in _conversion:
    fail("the conversion recipe reads names with a table of its own again; a Map.ofEntries is ordered "
         "differently every launch, and so was what a pickaxe became")
_conv_rules = strip_comments(open(os.path.join(PURE, "ArmoryConversionRules.java"), encoding="utf-8").read(),
                             keep_strings=True)
_conv_words = re.search(r"Map<String, String> MATERIALS = Map\.of\((.*?)\);", _conv_rules, re.S)
_conv_made_of = re.findall(r'"([a-z_]+)"', _conv_words.group(1))[1::2] if _conv_words else []
_conv_shapes = re.findall(r'new Shape\("([a-z_]+)", "([a-z_]+)", (?:"([a-z_]+)"|null)\)', _conv_rules)
if not _conv_shapes or not _conv_made_of:
    fail("ArmoryConversionRules names no shape or no material, so no piece of the base game converts")
for _word, _gear, _material in _conv_shapes:
    if _gear not in GEAR_ITEMS:
        fail(f"a {_word} converts to {_gear}, which has no gear item")
    if _material:
        _conv_made_of.append(_material)
for _material in sorted(set(_conv_made_of)):
    if not os.path.isfile(os.path.join(MATERIALS, _material + ".json")):
        fail(f"the conversion makes gear of {_material}, which is no material here, so that piece "
             f"converts into nothing")

# The extras: the grid takes them out before it reads the materials, a book
# asks for them too, and a blueprint says so.
_materials_of = _squashed(method_body(_part_recipe, "private static List<String> materialsOf("))
for _statement in ("boolean[] extras = ArmoryPlanRules.extras(part.crafting().extra(), bench.size(), "
                   "(index, extra) -> answers(bench.get(index), extra));",
                   "if (extras == null) { return null; }",
                   "if (extras[i]) { continue; }"):
    if _statement not in _materials_of:
        fail(f"a part's extras are read from the data and never charged, so a coating needs no glass "
             f"bottle and a mace core no heavy core (materialsOf has lost \"{_statement}\")")
        break
if "List<String> materials = part == null ? null : materialsOf(part, bench);" not in _part_read:
    fail("the grid is read without taking the part's extras out, so the recipe never charges them")
if "List<String> materials = known == null ? null : materialsOf(known, bench);" not in _only_known:
    fail("a plan book picks its plan without the extras, so it can choose a coating with no bottle on "
         "the bench")
if ("return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));"
        not in method_body(_part_recipe, "private static boolean answers(")):
    fail("an extra named by #tag matches nothing, so a part that asks for one can never be made")
if "for (String extra : part.crafting().extra()) {" not in method_body(_blueprint_src,
                                                                     "public void appendHoverText("):
    fail("a blueprint does not say what else its part takes, so the grid charges a bottle nobody was told of")
for _key in ("elysiumarmory.blueprint.also", "elysiumarmory.template.unread"):
    if _key not in lang:
        fail(f"{_key} has no translation")

# Every item a material or a part names exists. The loader cannot tell: a
# material whose item is gone is one nobody can use, and a part whose extra is
# gone is one nobody can make ("minecraft:bort" sat in the setting, found 29
# September 2026). The base game's ids are read off its own jar, ours off the
# modules, and a #tag an extra names has to be one some module fills.
_mc_jar = next((p for p in (os.environ.get("MC_JAR", ""), "/tmp/mc/libs/vanilla-assets.jar",
                            "C:/tmp/mc/libs/vanilla-assets.jar") if p and os.path.isfile(p)), None)
_vanilla_items = None
if _mc_jar:
    import zipfile
    with zipfile.ZipFile(_mc_jar) as _jar:
        _prefix = "assets/minecraft/models/item/"
        _vanilla_items = {n[len(_prefix):-len(".json")] for n in _jar.namelist()
                          if n.startswith(_prefix) and n.endswith(".json")}
_MONO = os.path.dirname(WORK)
_module_res = [os.path.join(_MONO, d, "src/main/resources") for d in sorted(os.listdir(_MONO))
               if d.startswith("elysium-") and os.path.isdir(os.path.join(_MONO, d, "src/main/resources"))]


def _registered(namespace, path):
    """An item one of the modules ships: it has a model, which every item has."""
    return any(os.path.isfile(os.path.join(res, "assets", namespace, "models/item", path + ".json"))
               for res in _module_res)


def _filled(namespace, path):
    """An item tag some module writes, with at least one entry in it."""
    for res in _module_res:
        tag = os.path.join(res, "data", namespace, "tags/item", path + ".json")
        if os.path.isfile(tag) and json.load(open(tag, encoding="utf-8")).get("values"):
            return True
    return False


_named = []
for _path in material_files:
    _crafting = json.load(open(_path, encoding="utf-8")).get("crafting", {})
    _where = os.path.relpath(_path, RES).replace(os.sep, "/")
    if isinstance(_crafting.get("ingredient"), str):
        _named.append((_where, _crafting["ingredient"], False))
    for _substitute in (_crafting.get("part_substitutes") or {}).values():
        if isinstance(_substitute, str):
            _named.append((_where, _substitute, False))
for _path in part_files:
    _where = os.path.relpath(_path, RES).replace(os.sep, "/")
    for _extra in json.load(open(_path, encoding="utf-8")).get("crafting", {}).get("extra", []):
        _named.append((_where, _extra, True))
for _where, _id, _is_extra in _named:
    if _id.startswith("#"):
        _ns, _, _tag = _id[1:].partition(":")
        if _is_extra and _ns != "minecraft" and not _filled(_ns, _tag):
            fail(f"{_where} asks for {_id}, a tag no module fills, so that part can never be made")
        continue
    _ns, _, _item = _id.partition(":")
    if _ns == "minecraft":
        if _vanilla_items is not None and _item not in _vanilla_items:
            fail(f"{_where} names {_id}, which the game does not have, so "
                 + ("that part can never be made" if _is_extra else "that material can never be used"))
    elif not _registered(_ns, _item):
        fail(f"{_where} names {_id}, which no module of the pack registers")

print(f"survival           : any affixed piece salvages and a reforge pays back no more than it cost; "
      f"a template is found in {len(_found_rows)} chests; {len(_conv_shapes)} shapes convert by one rule; "
      f"extras are charged")
print(f"what the data names: {len(_named)} item(s) and tag(s) named by materials and parts, each one there"
      + ("" if _vanilla_items is not None else " (the base game's not checked: no vanilla jar, set MC_JAR)"))

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all armory checks passed")
