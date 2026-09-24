#!/usr/bin/env python3
"""
Validate Elysium Ores against its sources.

Run from anywhere:   python3 elysium-ores/validate.py

This module is unusual in this repository: it is the only one whose whole
subject is other mods, and it is the only one that must never name one. So the
checks are ordered by how much damage a lapse would do.

  1. INDEPENDENCE, which here means something stronger than usual. Every other
     mod is a c: tag and an optional tag entry. No import, no mod id in Java,
     no required dependency, no item id belonging to anyone but vanilla. A
     jar that loads next to vanilla alone and generates nothing is the
     supported configuration, and a tag entry that is not optional breaks it.
  2. THE GUARD. A unifier that converts one material into a different material
     costs a player something they cannot get back. The ambiguity guard is the
     one check standing between this module and that, and it is checked in the
     rules, in the map, and in the loot modifier.
  3. ADDITIVE ONLY. Nothing here may edit, reorder or remove another mod's
     recipe. The injector copies and appends; a validator that let that slip
     would let this module break a pack it was installed to tidy.
  4. PURITY. rules/ is compiled against the JDK alone and its harness is run,
     so the arithmetic is executed rather than read.
  5. THE MATRIX. A hundred and fifty-six ores, each of which needs a block, a
     model, a texture, a loot table, a feature, a placement, a gate, four
     tags and a name. One missing file is one invisible or nameless ore, and
     there are too many to notice by eye.
"""
import json
import os
import re
import subprocess
import sys
import tempfile

WORK = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(WORK, "src/main/resources")
SRC = os.path.join(WORK, "src/main/java/com/elysium/ores")
PURE = os.path.join(SRC, "rules")
DATA = os.path.join(RES, "data/elysiumores")
ASSETS = os.path.join(RES, "assets/elysiumores")
C = os.path.join(RES, "data/c")
HARNESS = os.path.join(WORK, "tools/OreHarness.java")
HARNESS_MAIN = "com.elysium.ores.rules.OreHarness"
NS = "elysiumores"

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
    if not os.path.isfile(path):
        return None
    return json.load(open(path, encoding="utf-8"))


def body_of(src, signature):
    """The text between the braces of one method, so a rule scoped to it
    cannot pass because its token survives in an import or another branch."""
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
#
# The module exists to tidy up after other mods. The one way it could make
# things worse is by depending on one of them: a pack that removes Mekanism
# would then be a pack that cannot load the thing that was managing its
# duplicate osmium.
#
# So the rule is absolute, and it is checked against the whole Java tree rather
# than against a list of known mods - a name nobody thought of is exactly the
# one that would slip through a list.

VANILLA_NAMESPACES = {"minecraft", "c", "neoforge", "forge", NS, "elysium",
                      "elysiumlib", "elysiumspace"}

toml = read(RES, "META-INF/neoforge.mods.toml")
if not toml:
    fail("neoforge.mods.toml is missing, so the module declares nothing at all")
else:
    declared = re.findall(r'modId\s*=\s*"([a-z0-9_]+)"', toml)
    types = re.findall(r'modId\s*=\s*"([a-z0-9_]+)"[\s\S]{0,80}?type\s*=\s*"(\w+)"', toml)
    for mod, kind in types:
        if mod in ("neoforge", "minecraft", "elysiumlib"):
            continue
        if kind == "required":
            fail(f"{mod} is a required dependency; this module must load beside "
                 f"vanilla alone, and a required host makes the tidying depend on "
                 f"the mess")
    if "elysiumlib" not in declared:
        fail("elysiumlib is not declared, but the ore yield reads the world tier")
    for forbidden in ("apotheosis", "silentgear", "mekanism", "immersiveengineering",
                      "create", "minecolonies", "apothic_spawners"):
        if forbidden in declared:
            fail(f"{forbidden} is declared as a dependency; every other mod here is a "
                 f"tag, not a dependency")

for path in java_files():
    src = read(path)
    rel = os.path.relpath(path, WORK)
    for module in ("com.mekanism", "mekanism.", "blusunrize.", "com.simibubi",
                   "net.silentchaos", "dev.shadowsoffire", "com.minecolonies"):
        if module in src:
            fail(f"{rel} imports {module}; this module names no other mod in Java")
    stripped_but_strings = strip_comments(src, keep_strings=True)
    for literal in re.findall(r'"([a-z0-9_]+):[a-z0-9_/]+"', stripped_but_strings):
        if literal not in VANILLA_NAMESPACES:
            fail(f"{rel} contains the id \"{literal}:...\"; a material is a tag here, "
                 f"never another mod's item id")

catalogue_src = read(SRC, "data/OreCatalogue.java")
for literal in re.findall(r'"([a-z0-9_]+):[a-z0-9_/]+"', catalogue_src):
    if literal != "minecraft":
        fail(f"OreCatalogue names {literal}:...; the only ids it may fall back on are "
             f"vanilla's, because vanilla is the only mod that is always there")
print("independence       : no mod named in Java, no required host, vanilla-only fallbacks")

# ---------------------------------------------------------------------------
# 2. The guard
# ---------------------------------------------------------------------------
#
# oritech:biosteel_ingot sits in c:ingots/steel as well as c:ingots/biosteel.
# A unifier that trusted the tag would offer to turn biosteel into steel and
# the player would lose it. Three places have to agree that this never happens.

unify_rules = strip_comments(read(PURE, "UnifyRules.java"))
if "public static boolean ambiguous(" not in unify_rules:
    fail("UnifyRules has no ambiguity guard, so an item tagged into two families "
         "would be converted and a material destroyed")
conversions = body_of(unify_rules, "public static List<String> conversions")
if "ambiguousIds.contains(" not in conversions:
    fail("conversions() does not consult the ambiguity guard, so the guard exists "
         "and protects nothing")
if ".equals(canonical)" not in conversions:
    fail("conversions() offers the canonical a conversion into itself")

unification = strip_comments(read(SRC, "unify/OreUnification.java"))
if "UnifyRules.ambiguous(" not in unification:
    fail("the unification map never asks the guard, so the rules' protection never "
         "reaches the items it protects")
recompute = body_of(unification, "public static void recompute")
if "twoFamilies" not in recompute:
    fail("recompute() does not collect the items that answer to two families")
if "UnifyRules.worthUnifying(" not in recompute:
    fail("every family is unified, including the ones with a single supplier, which "
         "is a conversion recipe for every material in the game")

modifier = strip_comments(read(SRC, "loot/UnifyingModifier.java"))
if "OreUnification.unify(" not in modifier:
    fail("the loot modifier rewrites stacks by some route other than the unification "
         "map, so the guard does not apply to loot")

unify_body = body_of(unification, "public static ItemStack unify")
if "getCount()" not in unify_body:
    fail("unify() drops the stack's count, so unifying a stack of lead would cost "
         "the player all but one")
if "copyComponents" in unify_body or "applyComponents" in unify_body:
    fail("unify() carries the variant's components onto a different item, which "
         "produces something nobody's recipe expects")
print("the guard          : an item in two families is never converted, in all three places")

# ---------------------------------------------------------------------------
# 3. Additive only
# ---------------------------------------------------------------------------

injector = strip_comments(read(SRC, "unify/UnifyRecipes.java"))
inject = body_of(injector, "public static void inject")
if "recipes.getRecipes()" not in inject:
    fail("the injector does not read the existing recipes, so replaceRecipes would "
         "install ours and delete every other mod's")
if "all.add(existing)" not in inject:
    fail("the injector does not copy the existing recipes across, so a pack would "
         "lose every recipe it had")
if "replaceRecipes(all)" not in inject:
    fail("the injector does not install the combined list")
if "getPath().startsWith(PREFIX)" not in inject:
    fail("the injector does not recognise its own recipes from a previous run, so "
         "reloading twice accumulates two copies of every conversion")
for banned in ("remove(", "clear(", "byName", "removeIf"):
    if banned in inject:
        fail(f"the injector calls {banned}, which is how an additive unifier stops "
             f"being additive")
handler = strip_comments(read(SRC, "event/OreUnifyHandler.java"))
if "ItemCraftedEvent" in handler or "ItemSmeltedEvent" in handler:
    fail("the module swaps a crafted or smelted result, which means emptying a "
         "player's output and posting them something else - a trick that eats "
         "materials the moment it races an automated crafter")
print("additive only      : recipes copied and appended, never edited or removed")

# ---------------------------------------------------------------------------
# 4. Purity, and the harness
# ---------------------------------------------------------------------------

for name in sorted(os.listdir(PURE)):
    if not name.endswith(".java"):
        continue
    src = strip_comments(read(PURE, name))
    # The package line is this module's own and says nothing about what the
    # rules reach for; everything after it does.
    without_package = re.sub(r"^\s*package [^;]+;", "", src, count=1, flags=re.M)
    for forbidden in ("net.minecraft", "net.neoforged", "com.mojang", "com.elysium"):
        if forbidden in without_package:
            fail(f"rules/{name} reaches for {forbidden}; the rules are the JDK and "
                 f"nothing else, so they can be executed by a harness")

with tempfile.TemporaryDirectory() as tmp:
    sources = [os.path.join(PURE, n) for n in os.listdir(PURE) if n.endswith(".java")]
    staged = os.path.join(tmp, "src")
    os.makedirs(staged)
    for source in sources + [HARNESS]:
        target = os.path.join(staged, os.path.basename(source))
        open(target, "w", encoding="utf-8").write(read(source))
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
            fail("the ore harness failed:\n" + harness_trouble(ran.stdout))
        else:
            print("the arithmetic     : " + ran.stdout.strip().splitlines()[-1])

# Vanilla first is not a preference; it is what stops a pack deciding that the
# iron ingot is somebody else's iron ingot.
priority = body_of(unify_rules, "public static final List<String> PRIORITY")
if not re.search(r'List\.of\(\s*"minecraft"', read(PURE, "UnifyRules.java")):
    fail("minecraft is not first in the priority list, so a pack could settle on an "
         "iron ingot that is not the iron ingot")

# ---------------------------------------------------------------------------
# 5. The matrix
# ---------------------------------------------------------------------------

rows = re.findall(r'new Entry\("([a-z0-9_]+)", "([a-z0-9_]+)", "([a-z0-9_]+)", (\d+), (null|"[^"]*")\)',
                  catalogue_src)
if not rows:
    fail("OreCatalogue has no rows, so this module adds no ores at all")

families = sorted({family for _, family, _, _, _ in rows})
hosts = sorted({host for _, _, host, _, _ in rows})
print(f"the matrix         : {len(rows)} ore(s), {len(families)} material(s), "
      f"{len(hosts)} stone(s)")

lang = load(ASSETS, "lang/en_us.json") or {}
mineable = (load(RES, "data/minecraft/tags/block/mineable/pickaxe.json") or {}).get("values", [])
tool_tags = {}
for tier in ("needs_stone_tool", "needs_iron_tool", "needs_diamond_tool"):
    tool_tags[tier] = set((load(RES, f"data/minecraft/tags/block/{tier}.json")
                           or {}).get("values", []))

seen_names = set()
for name, family, host, band, vanilla in rows:
    block = f"{NS}:{name}"
    if name in seen_names:
        fail(f"{name} appears twice in the catalogue, so two blocks want one id")
    seen_names.add(name)
    if name != f"{host}_{family}_ore":
        fail(f"{name} is not spelled <stone>_<material>_ore, so nothing can work out "
             f"what it is from its id")
    for what, path in (
            ("blockstate", f"blockstates/{name}.json"),
            ("block model", f"models/block/{name}.json"),
            ("item model", f"models/item/{name}.json"),
            ("texture", f"textures/block/{name}.png")):
        if not os.path.isfile(os.path.join(ASSETS, path)):
            fail(f"{name} has no {what}, so it is an ore nobody can see")
    for what, path in (
            ("loot table", f"loot_table/blocks/{name}.json"),
            ("configured feature", f"worldgen/configured_feature/{name}.json"),
            ("placed feature", f"worldgen/placed_feature/{name}.json")):
        if not os.path.isfile(os.path.join(DATA, path)):
            fail(f"{name} has no {what}, so it is an ore that never appears or never "
                 f"drops")
    if f"block.{NS}.{name}" not in lang:
        fail(f"{name} has no name, so a player reads a raw key")
    if block not in mineable:
        fail(f"{name} is not mineable with a pickaxe, so it takes a lifetime by hand")
    tiers = [tier for tier, values in tool_tags.items() if block in values]
    if len(tiers) != 1:
        fail(f"{name} is in {len(tiers)} tool tag(s); it needs exactly one, or the "
             f"game cannot decide what mines it")

    placed = load(DATA, f"worldgen/placed_feature/{name}.json") or {}
    steps = placed.get("placement", [])
    if not steps or steps[0].get("type") != f"{NS}:family_present":
        fail(f"{name} does not begin with the family gate, so a material this pack "
             f"does not have would still generate and drop nothing")
    elif steps[0].get("family") != family:
        fail(f"{name} gates on {steps[0].get('family')} rather than on {family}")
    if placed.get("feature") != f"{NS}:{name}":
        fail(f"{name}'s placement points at a different feature")

    configured = load(DATA, f"worldgen/configured_feature/{name}.json") or {}
    targets = configured.get("config", {}).get("targets", [])
    if not targets:
        fail(f"{name} replaces nothing, so it generates nowhere")
    else:
        state = targets[0].get("state", {}).get("Name")
        if state != block:
            fail(f"{name}'s feature places {state} rather than itself")

    table = load(DATA, f"loot_table/blocks/{name}.json") or {}
    named = json.dumps(table)
    if f'"{NS}:' not in named:
        fail(f"{name}'s loot table does not drop the block, which is what silk touch "
             f"and the Java resolver both need it to do")
    if re.search(r'"(?!minecraft:|' + NS + r':)[a-z0-9_]+:', named):
        fail(f"{name}'s loot table names another mod's item; what it drops is resolved "
             f"at runtime, not written down")

# ---------------------------------------------------------------------------
# Every ore reaches a world, exactly once.
# ---------------------------------------------------------------------------

placed_by_modifier = {}
modifier_dir = os.path.join(DATA, "neoforge/biome_modifier")
if not os.path.isdir(modifier_dir):
    fail("there are no biome modifiers, so every ore generated here is data nothing "
         "reads and no world has any of it")
else:
    for name in sorted(os.listdir(modifier_dir)):
        body = load(modifier_dir, name) or {}
        if body.get("step") != "underground_ores":
            fail(f"{name} adds its features at {body.get('step')}; ores belong in "
                 f"underground_ores or they fight the terrain that is still being built")
        for feature in body.get("features", []):
            placed_by_modifier.setdefault(feature, []).append(name)

for name, family, host, band, vanilla in rows:
    where = placed_by_modifier.get(f"{NS}:{name}", [])
    if not where:
        fail(f"{name} is in no world, so it exists only in the creative tab")
    elif len(where) > 1:
        fail(f"{name} is added by {len(where)} biome modifiers, so it generates at "
             f"several times the rate it was tuned for")
print(f"the worlds         : {len(placed_by_modifier)} placed feature(s) across "
      f"{len(os.listdir(modifier_dir)) if os.path.isdir(modifier_dir) else 0} world(s), each once")

# ---------------------------------------------------------------------------
# The tags that complete a family
# ---------------------------------------------------------------------------
#
# Every entry has to be optional. A tag file that names an item from a mod
# nobody installed is a datapack error, and this module's whole claim is that
# it works next to vanilla alone.

tag_files = 0
for root, _, names in os.walk(C):
    for name in names:
        if not name.endswith(".json"):
            continue
        tag_files += 1
        body = load(root, name) or {}
        rel = os.path.relpath(os.path.join(root, name), RES)
        if body.get("replace"):
            fail(f"{rel} replaces the tag instead of adding to it, so every other "
                 f"mod's entries in that family are thrown away")
        for value in body.get("values", []):
            if isinstance(value, str):
                if not value.startswith(f"{NS}:"):
                    fail(f"{rel} names {value} as a required entry; a family member "
                         f"from a mod that is not installed has to be optional or the "
                         f"datapack fails to load")
            elif value.get("required", True):
                fail(f"{rel} marks {value.get('id')} required")
if tag_files == 0:
    fail("no family tags are shipped, so the variants never answer to one another and "
         "a recipe written against c:ingots/steel still accepts one steel")
print(f"family tags        : {tag_files} file(s), every foreign entry optional, none replacing")

# ---------------------------------------------------------------------------
# Ores answer to their own family
# ---------------------------------------------------------------------------

for family in families:
    body = load(C, f"tags/block/ores/{family}.json")
    if body is None:
        fail(f"c:ores/{family} is not shipped, so this module's {family} ore is not a "
             f"{family} ore as far as any other mod is concerned")
        continue
    mine = {v for v in body.get("values", []) if isinstance(v, str) and v.startswith(f"{NS}:")}
    expected = {f"{NS}:{name}" for name, fam, _, _, _ in rows if fam == family}
    if mine != expected:
        fail(f"c:ores/{family} lists {len(mine)} of this module's {len(expected)} "
             f"{family} ores")

# ---------------------------------------------------------------------------
# The drop, and what decides it
# ---------------------------------------------------------------------------

drops = strip_comments(read(SRC, "ore/OreDrops.java"), keep_strings=True)
order = re.search(r'KINDS\s*=\s*\n?\s*List\.of\(([^)]*)\)', drops)
if not order:
    fail("OreDrops has no search order, so what an ore drops depends on which tag "
         "happened to be looked at first")
else:
    kinds = [k.strip().strip('"') for k in order.group(1).split(",")]
    if kinds[0] != "raw_materials":
        fail("OreDrops looks for a processed form before a raw one, so breaking an "
             "ore would hand a player a finished ingot")
    if "ingots" in kinds and kinds.index("ingots") != len(kinds) - 1:
        fail("OreDrops prefers an ingot to a gem or a dust")
if "UnifyRules.canonical(" not in drops:
    fail("OreDrops chooses what to drop by some rule other than the one everything "
         "else was unified onto, so the ore would produce a seventh lead")

block_src = strip_comments(read(SRC, "ore/ElysiumOreBlock.java"))
get_drops = body_of(block_src, "public List<ItemStack> getDrops")
if "silked(" not in get_drops:
    fail("the ore ignores silk touch, so an ore whose drop is resolved at runtime "
         "can never be moved")
if "OreRules.yield(" not in get_drops:
    fail("the ore's yield is decided somewhere other than OreRules, so the world "
         "tier buys nothing at the rock face")
if "OreRules.fortuned(" not in get_drops:
    fail("fortune does nothing on this module's ores")
if "ElysiumTiers.of(" not in block_src:
    fail("the ore never asks what tier the breaker is, so a deep world pays exactly "
         "what the Overworld pays")

gate = strip_comments(read(SRC, "place/FamilyPresent.java"))
if "OreDrops.present(" not in body_of(gate, "protected boolean shouldPlace"):
    fail("the family gate does not ask whether the material exists, so it gates on "
         "nothing")
print("the drop           : silk touch, fortune, the world tier, and the pack's own item")

# ---------------------------------------------------------------------------
# The loot modifier is actually installed
# ---------------------------------------------------------------------------

glm = load(RES, "data/neoforge/loot_modifiers/global_loot_modifiers.json") or {}
if f"{NS}:unifying" not in glm.get("entries", []):
    fail("the unifying loot modifier is not listed in global_loot_modifiers, so it "
         "is registered, shipped and never runs")
if glm.get("replace"):
    fail("global_loot_modifiers replaces the list, so every other mod's loot "
         "modifiers are switched off by installing this one")
if not os.path.isfile(os.path.join(DATA, "loot_modifier/unifying.json")):
    fail("the loot modifier is listed and its file is missing")
registry = strip_comments(read(SRC, "registry/OreLoot.java"))
if "UnifyingModifier.CODEC" not in registry:
    fail("the loot modifier names a serializer nobody registered")

# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

for path in java_files():
    raw = strip_comments(read(path), keep_strings=True)
    for key in re.findall(r'"((?:commands\.' + NS + r'|itemGroup\.' + NS
                          + r'|' + NS + r')[\w.]*\.[\w.]+)"', raw):
        if key not in lang:
            fail(f"{os.path.relpath(path, WORK)} shows {key} and nothing translates it")
print(f"language           : {len(lang)} key(s), every literal one reachable")
print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all ore checks passed")
