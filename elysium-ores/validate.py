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
import sys as _sys
_sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "tools"))
import samefile  # noqa: E402
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


def squashed(text):
    """Every run of whitespace as one space, comments gone first, so a rule can
    name a whole statement without guessing how it was wrapped, and cannot be
    passed by a // in front of the line it names."""
    return " ".join(strip_comments(text, keep_strings=True).split())


def harness_trouble(output):
    """The lines of a harness run that say what went wrong.

    The validator used to call this without defining it, so the first harness
    failure this module ever had crashed the validator with a NameError instead
    of being reported — found by a negative test, which is what they are for.
    """
    lines = [line for line in output.splitlines()
             if line.strip().startswith("FAIL:") or "failure(s)" in line]
    return "\n".join(lines or output.strip().splitlines()[-12:])[:2000]


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

# The foundry and atomics outrank every other mod, and the agronomy, which is
# not listed (29 Sep 2026). Unlisted, the foundry lost the tie for every mined
# metal's c:raw_materials family to the agronomy's chunk on the alphabet: the
# ores of twelve metals and of uranium dropped a chunk, and the foundry's raw
# metals and atomics' raw uranium could never be had (check_obtainable.py).
_listed = re.search(r"List<String> PRIORITY = List\.of\((.*?)\);",
                    strip_comments(read(PURE, "UnifyRules.java"), keep_strings=True), re.S)
_ranked = re.findall(r'"([a-z0-9_]+)"', _listed.group(1)) if _listed else []
for _metal in ("elysiumfoundry", "elysiumatomics"):
    if _metal not in _ranked:
        fail(f"{_metal} is not in UnifyRules.PRIORITY, so the agronomy's chunk wins its metals' "
             f"families on the alphabet: the ore drops the chunk and the raw metal is a thing nobody holds")
    elif any(ns != "minecraft" and not ns.startswith("elysium") for ns in _ranked[:_ranked.index(_metal)]):
        fail(f"{_metal} is ranked below another mod in UnifyRules.PRIORITY, so a pack that has that mod "
             f"drops its metal from this module's ores")

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
# No stone is left to a mod the pack no longer ships (29 Sep 2026)
# ---------------------------------------------------------------------------
#
# The generator leaves a stone alone where tools/pack_scan.json says a mod
# filled it, and the scan is the pack this was written against: most of its
# mods are retired, each with a row in docs/LICENSING.md. Asked of the whole
# scan, the Overworld had no tin, zinc, nickel, silver, lead, aluminium,
# lithium, osmium, uranium or uraninite, and bronze, the foundry's first alloy,
# waited on a trip to the Nether. So every stone a retired mod once filled with
# a material this module has ores for is filled here.

_retired = set()
for _line in read(os.path.dirname(WORK), "docs", "LICENSING.md").splitlines():
    _row = re.match(r"^\| [^|]* \| `[^`]+\.jar` \| ([^|]*) \|", _line)
    if _row:
        _retired.update(part.strip() for part in _row.group(1).split(",") if part.strip())
if not _retired:
    fail("docs/LICENSING.md names no retired mod, so nothing here can tell a stone a retired mod "
         "once filled from one a shipped mod still fills")
# How a scanned block's name says its stone; anything else is plain stone.
_STONE_OF = (("deepslate_", "deepslate"), ("nether_", "netherrack"), ("netherrack", "netherrack"),
             ("blackstone_", "netherrack"), ("end_", "end_stone"), ("endstone_", "end_stone"),
             ("cinder_", "cinder"), ("frost_", "frost"), ("pale_", "pale"))
# The game fills its own eight in stone and deepslate, and quartz in netherrack.
_GAME_FILLS = {(f, h) for f in ("coal", "copper", "iron", "gold", "redstone", "lapis", "emerald",
                                "diamond") for h in ("stone", "deepslate")} | {("quartz", "netherrack")}
_ours = {(family, host) for _, family, host, _, _ in rows}
_left = []
for _family, _blocks in sorted((load(WORK, "tools/pack_scan.json") or {}).get("ores", {}).items()):
    if _family not in families:
        continue
    for _block in _blocks:
        _mod, _path = _block.split(":", 1)
        if _mod not in _retired:
            continue
        _host = next((h for prefix, h in _STONE_OF if _path.startswith(prefix)),
                     "deepslate" if "deepslate" in _path else "stone")
        if (_family, _host) not in _ours and (_family, _host) not in _GAME_FILLS:
            _left.append(f"{_family} in {_host} ({_block})")
if _left:
    fail(f"{len(_left)} stone(s) are left to a mod the pack no longer ships (docs/LICENSING.md), so "
         f"that material is in that stone nowhere at all: " + ", ".join(_left[:6]))
print(f"the stones         : {len(_retired)} retired mod(s) in the ledger, and every stone one of them "
      f"once filled is filled here")

# ---------------------------------------------------------------------------
# The drop, and what decides it
# ---------------------------------------------------------------------------

drops = strip_comments(read(SRC, "ore/OreDrops.java"), keep_strings=True)
drop_rules = strip_comments(read(PURE, "UnifyRules.java"), keep_strings=True)
order = re.search(r'DROP_ORDER\s*=\s*List\.of\(([^)]*)\)', drop_rules)
if not order:
    fail("UnifyRules has no drop order, so what an ore drops depends on which tag "
         "happened to be looked at first")
else:
    kinds = [k.strip().strip('"') for k in order.group(1).split(",")]
    if kinds[0] != "raw_materials":
        fail("the drop order looks for a processed form before a raw one, so breaking an "
             "ore would hand a player a finished ingot")
    if "ingots" in kinds and kinds.index("ingots") != len(kinds) - 1:
        fail("the drop order prefers an ingot to a gem or a dust")
    # The game's own item before any dust or ingot (29 Sep 2026): last, it lost
    # to c:dusts/coal, and the Nether's and the End's coal ores dropped the
    # foundry's coal dust.
    if "vanilla" not in kinds or any(kind in kinds and kinds.index(kind) < kinds.index("vanilla")
                                     for kind in ("dusts", "ingots")):
        fail("the drop order looks at a dust or an ingot before the game's own item, so the "
             "Nether's coal ore drops the foundry's coal dust rather than coal")
if "canonical(found)" not in squashed(body_of(drop_rules, "public static String drop(")):
    fail("the drop takes a member of the family by some rule other than the one everything "
         "else was unified onto, so the ore would produce a seventh lead")
_search = squashed(body_of(drops, "private static Item search("))
if "UnifyRules.drop(members, fallback == null ? null : vanilla);" not in _search \
        or "for (String kind : UnifyRules.DROP_ORDER) {" not in _search:
    fail("OreDrops decides the drop by something other than UnifyRules' drop order, so the harness "
         "executes one rule and the game plays another")
_of = squashed(body_of(drops, "public static Item of("))
if 'String key = family + "|" + (vanilla == null ? "" : vanilla);' not in _of \
        or "Item found = RESOLVED.computeIfAbsent(key, ignored -> search(family, vanilla));" not in _of:
    fail("OreDrops remembers one answer per family, so the placement gate, which asks without the "
         "game's own item, answers for the ore, which asks with it: the Nether's coal ore drops "
         "whatever worldgen asked first, which is coal dust")

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
# Plural. NeoForge's LootModifierManager reads `loot_modifiers` and nothing
# else; this file sat in a singular directory for a while, listed, registered
# and never loaded, and nothing said so.
if not os.path.isfile(os.path.join(DATA, "loot_modifiers/unifying.json")):
    fail("the loot modifier is listed and its file is not at loot_modifiers/unifying.json, "
         "which is the one directory NeoForge reads")
if os.path.isdir(os.path.join(DATA, "loot_modifier")):
    fail("data/elysiumores/loot_modifier (singular) exists; NeoForge never reads it, so whatever "
         "is in there is a modifier that does not run")
registry = strip_comments(read(SRC, "registry/OreLoot.java"))
if "UnifyingModifier.CODEC" not in registry:
    fail("the loot modifier names a serializer nobody registered")

# ---------------------------------------------------------------------------
# 6. The Cordon's materials (docs/SPLITS.md "Valoria: materials")
# ---------------------------------------------------------------------------
#
# OreHarness walks the roster: seventeen sets, each on a material that is dug,
# made or found. What it cannot see is whether the roster reached the disk -
# an item with no model is a purple square, a family with no tag is an ore
# that drops its own block, a recipe naming a mod that is not here is a
# datapack error - and whether the Java registers what the roster lists rather
# than a list of its own.

roster_src = strip_comments(read(PURE, "CordonMaterials.java"), keep_strings=True)
roster_rows = re.findall(r'\b(dug|alloy|found)\("([a-z_]+)",\s*(?:Kind\.(\w+),\s*)?', roster_src)
roster = [(family, kind or "ALLOY") for _, family, kind in roster_rows]
roster_drops = re.findall(r'new Drop\("([a-z_]+)"', roster_src)
roster_parts = re.findall(r'new Part\("([a-z_]+)",\s*"([a-z_]+)"', roster_src)
sets = re.findall(r'new Set\("([a-z_]+)",\s*"([a-z_]+)"', roster_src)
if len(sets) != 17:
    fail(f"the roster carries {len(sets)} Valoria sets, not the seventeen its language file names")
for valoria_set, family in sets:
    if family not in dict(roster):
        fail(f"Valoria's {valoria_set} stands on {family}, which the roster does not have")
item_models = os.path.join(ASSETS, "models/item")
item_tex = os.path.join(ASSETS, "textures/item")
block_tex = os.path.join(ASSETS, "textures/block")
recipes = os.path.join(DATA, "recipe")
shipped_items = []
shipped_blocks = []
for family, kind in roster:
    if kind == "EXTERNAL":
        for stray in os.listdir(item_models) if os.path.isdir(item_models) else []:
            if stray.startswith(family + "_") or stray == family + ".json":
                fail(f"{stray} is shipped for {family}, which is another module's family; "
                     f"two mods would make two {family}s")
        continue
    names = {"METAL": [f"raw_{family}", f"{family}_ingot", f"{family}_nugget"],
             "ALLOY": [f"{family}_ingot", f"{family}_nugget"]}.get(kind, [family])
    blocks = [f"{family}_block"] + ([f"raw_{family}_block"] if kind == "METAL" else [])
    shipped_items += names
    shipped_blocks += blocks
    if kind in ("METAL", "GEM"):
        if family not in families:
            fail(f"{family} is dug and OreCatalogue has no ore for it, so it is a material with no source")
    if kind in ("METAL", "ALLOY"):
        for tag in ("ingots", "nuggets"):
            body = load(C, f"tags/item/{tag}/{family}.json") or {}
            if f"{NS}:{family}_{tag[:-1]}" not in body.get("values", []):
                fail(f"c:{tag}/{family} does not list this module's {family} {tag[:-1]}, so no recipe "
                     f"against the tag takes it")
    if kind == "METAL":
        body = load(C, f"tags/item/raw_materials/{family}.json") or {}
        if f"{NS}:raw_{family}" not in body.get("values", []):
            fail(f"c:raw_materials/{family} does not list raw {family}, so the ore resolves to nothing "
                 f"and drops its own block")
        for cook in ("smelting", "blasting"):
            if not os.path.isfile(os.path.join(recipes, f"{family}_ingot_from_{cook}.json")):
                fail(f"raw {family} has no {cook} recipe, so it is a stone nobody can smelt")
    if kind in ("GEM", "SHARD"):
        body = load(C, f"tags/item/gems/{family}.json") or {}
        if f"{NS}:{family}" not in body.get("values", []):
            fail(f"c:gems/{family} does not list {family}, so the ore or the drop answers to no family")
    if kind == "ALLOY":
        recipe = load(recipes, f"{family}_ingot.json")
        if recipe is None:
            fail(f"{family} is an alloy with no recipe, so it is a material nobody can make")
        else:
            for ingredient in recipe.get("ingredients", []):
                item = ingredient.get("item", "")
                if not item.startswith(("minecraft:", f"{NS}:")):
                    fail(f"{family} is made of {item}, which is another mod's item; a missing mod would "
                         f"make the recipe fail to load")
            if recipe.get("result", {}).get("count", 0) < 1:
                fail(f"{family}'s recipe makes nothing")
    if kind == "CLOTH" and not os.path.isfile(os.path.join(recipes, f"{family}.json")):
        fail(f"{family} is a cloth nobody can weave")
    body = load(C, f"tags/item/storage_blocks/{family}.json") or {}
    if f"{NS}:{family}_block" not in body.get("values", []):
        fail(f"c:storage_blocks/{family} does not list the block")
    for big in blocks:
        small = big[:-len("_block")]
        if kind in ("METAL", "ALLOY") and not small.startswith("raw_"):
            small += "_ingot"
        if not os.path.isfile(os.path.join(recipes, f"{big}_from_{small}.json")) \
                or not os.path.isfile(os.path.join(recipes, f"{small}_from_{big}.json")):
            fail(f"{big} cannot be made from {small} and back, so a stack of {small} has nowhere to go")
shipped_items += roster_drops + [name for name, _ in roster_parts]
for name, family in roster_parts:
    if not os.path.isfile(os.path.join(recipes, f"{name}.json")):
        fail(f"{name} has no recipe, so a riveted part is a found part")
    if family not in dict(roster) or dict(roster)[family] == "EXTERNAL":
        fail(f"{name} is riveted from {family}, which this module does not ship")
for name in shipped_items:
    for what, path in (("model", os.path.join(item_models, name + ".json")),
                       ("sprite", os.path.join(item_tex, name + ".png"))):
        if not os.path.isfile(path):
            fail(f"{name} has no {what}, so it is a purple square in the player's hand")
    if f"item.{NS}.{name}" not in lang:
        fail(f"{name} has no name, so a player reads a raw key")
for name in shipped_blocks:
    for what, path in (("blockstate", os.path.join(ASSETS, f"blockstates/{name}.json")),
                       ("block model", os.path.join(ASSETS, f"models/block/{name}.json")),
                       ("item model", os.path.join(item_models, name + ".json")),
                       ("texture", os.path.join(block_tex, name + ".png")),
                       ("loot table", os.path.join(DATA, f"loot_table/blocks/{name}.json"))):
        if not os.path.isfile(path):
            fail(f"{name} has no {what}, so it is a block nobody can see or keep")
    if f"block.{NS}.{name}" not in lang:
        fail(f"{name} has no name")
for drop in roster_drops:
    eaten = False
    for name in os.listdir(recipes) if os.path.isdir(recipes) else []:
        if f'"{NS}:{drop}"' in read(recipes, name):
            eaten = True
    if not eaten:
        fail(f"{drop} is shipped and no recipe here takes it, so it is a thing with no use")
# The Java registers what the roster lists, not a list of its own.
material_items = strip_comments(read(SRC, "registry/MaterialItems.java"))
if "CordonMaterials.items()" not in material_items or "CordonMaterials.blocks()" not in material_items:
    fail("MaterialItems does not register from CordonMaterials.items() and blocks(); a second list "
         "is a list that drifts")
material_blocks = strip_comments(read(SRC, "registry/MaterialBlocks.java"))
if "CordonMaterials.MATERIALS" not in material_blocks or "material.blocks()" not in material_blocks:
    fail("MaterialBlocks does not loop over the roster's blocks()")
main_src = strip_comments(read(SRC, "ElysiumOres.java"))
for needed in ("MaterialBlocks.register(modEventBus);", "MaterialItems.register(modEventBus);"):
    if needed not in main_src:
        fail(f"ElysiumOres never calls {needed}; the roster's items would not exist in the game")
# Drawn fresh, and drawn the same every time.
art = os.path.join(WORK, "tools/gen_material_art.py")
if not os.path.isfile(art):
    fail("tools/gen_material_art.py is gone; the sprites could be replaced by hand and nothing would say so")
else:
    import filecmp
    with tempfile.TemporaryDirectory() as scratch:
        drawn = subprocess.run([sys.executable, art], capture_output=True, text=True, cwd=WORK,
                               env={**os.environ, "ELYSIUM_ORES_OUT": scratch, "PYTHONIOENCODING": "utf-8"})
        if drawn.returncode != 0:
            fail(f"tools/gen_material_art.py no longer runs: {drawn.stderr.strip()[-300:]}")
        else:
            drift = []
            for root, _, names in os.walk(scratch):
                for name in names:
                    made = os.path.join(root, name)
                    rel = os.path.relpath(made, scratch)
                    have = os.path.join(WORK, rel)
                    if not samefile.same(made, have):
                        drift.append(name)
            if drift:
                fail(f"the shipped material sprites are not what tools/gen_material_art.py draws: {drift[:5]}")
print(f"the cordon         : {len(sets)} sets on {len([f for f, k in roster if k != 'EXTERNAL'])} materials, "
      f"{len(shipped_items)} items and {len(shipped_blocks)} blocks, each modelled, drawn, named, tagged and made")

# ---------------------------------------------------------------------------
# 6b. What mines them (rules/HarvestRules; the harvest audit of 29 Sep 2026)
# ---------------------------------------------------------------------------
#
# A block breaks at the speed of the tool the game lists it under, and one
# that asks for the right tool drops nothing unless some tool is listed as
# right. Every ore and every block of metal, gem, alloy or shard asks for a
# pickaxe; the cloth block is wool, asks for nothing, and was skipped by name
# in the generator, so it was in no tool's list at all. The rules decide by
# kind now. So: every registered block is in exactly one of this module's
# mineable tags, or is of a kind the rules break by hand with its reason here;
# an exemption is read where the block is registered (the one branch of
# MaterialBlocks.propertiesFor that builds a cloth block), not taken on trust;
# the tags are what the rules say; and a tag names only blocks this module
# registers, because one unknown id makes the game refuse the whole tag.
_harvest_src = strip_comments(read(PURE, "HarvestRules.java"), keep_strings=True)
if not _harvest_src:
    fail("rules/HarvestRules.java is missing, so nothing says what mines a block and the "
         "tags are written from nothing")


def _harvest_list(name):
    found = re.search(r"List<String> " + name + r" = List\.of\(([^;]*?)\);", _harvest_src, re.S)
    return re.findall(r'"([A-Za-z_]+)"', found.group(1)) if found else []


HARVEST_TOOLS = ("pickaxe", "axe", "shovel", "hoe")
_ore_tool = re.search(r'String ORE_TOOL = "([a-z]+)";', _harvest_src)
_ore_tool = _ore_tool.group(1) if _ore_tool else ""
if _ore_tool not in HARVEST_TOOLS:
    fail(f"HarvestRules.ORE_TOOL is {_ore_tool!r}, which is no tool the game keeps a list "
         f"for, so every ore is written into a tag nothing reads")
_pickaxe_kinds = _harvest_list("PICKAXE_KINDS")
_by_hand_kinds = _harvest_list("BY_HAND_KINDS")
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
        if _named.startswith(f"{NS}:") and not os.path.isfile(os.path.join(
                ASSETS, "blockstates", _named.split(":", 1)[1] + ".json")):
            fail(f"mineable/{_tool} names {_named}, which is not a block this module "
                 f"registers, so the game refuses the whole tag and every block in it "
                 f"is back to dropping nothing")

#: The kinds of material whose blocks no tool is for, and why.
#: HarvestRules.BY_HAND_KINDS says which; a kind it names with no reason here
#: is refused, and so is a reason for a kind it no longer names.
MINE_EXEMPT = {
    "CLOTH": "a cloth block is wool: it breaks at 0.8 by hand and asks for no tool, and "
             "the game's own wool is in no tool's tag",
}

#: What the game's blocks this module copies ask of a tool, read out of the
#: game's own jar. Nothing here copies one; a block that starts to is looked
#: up here or refused.
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


for _kind in _by_hand_kinds:
    if _kind not in MINE_EXEMPT:
        fail(f"HarvestRules breaks every {_kind} block by hand and this gives no reason, so "
             f"they are left out of every tool's tag on nobody's say-so")
for _kind in MINE_EXEMPT:
    if _kind not in _by_hand_kinds:
        fail(f"a reason is given for breaking {_kind} blocks by hand and HarvestRules no "
             f"longer does, so the exemption covers blocks the rules put in a tool's tag")
for _kind in _pickaxe_kinds:
    if _kind in _by_hand_kinds:
        fail(f"HarvestRules both mines {_kind} blocks with a pickaxe and breaks them by hand")
# How each kind of block is built: the cloth branch of propertiesFor, and the
# branch every other kind falls through to.
_material_blocks = squashed(strip_comments(read(SRC, "registry/MaterialBlocks.java"),
                                           keep_strings=True))
_properties = body_of(_material_blocks, "private static BlockBehaviour.Properties propertiesFor(")
_kind_made = {}
for _kind in _by_hand_kinds + _pickaxe_kinds:
    _branch = _properties.find(f"Kind.{_kind})")
    if _branch >= 0:
        _kind_made[_kind] = _properties[_branch:_properties.find(";", _branch)]
    else:
        _last = _properties.rfind("return ")
        _kind_made[_kind] = _properties[_last:_properties.find(";", _last)] if _last >= 0 else ""
_kind_asks = {kind: asks_for_a_tool(made, f"a {kind.lower()} block") for kind, made in _kind_made.items()}
# And an ore, built once for the whole catalogue from the stone it sits in.
_ore_blocks = squashed(strip_comments(read(SRC, "registry/OreBlocks.java"), keep_strings=True))
_ore_asks, _ore_how = asks_for_a_tool(
    body_of(_ore_blocks, "private static BlockBehaviour.Properties propertiesFor("), "an ore")
_kind_of_block = {}
for _family, _kind in roster:
    if _kind == "EXTERNAL":
        continue
    _kind_of_block[f"{_family}_block"] = _kind
    if _kind == "METAL":
        _kind_of_block[f"raw_{_family}_block"] = _kind
_ore_names = [name for name, _family, _host, _band, _vanilla in rows]
_registered = _ore_names + list(_kind_of_block)
for _name in sorted(os.path.splitext(f)[0] for f in os.listdir(os.path.join(ASSETS, "blockstates"))
                    if f.endswith(".json")):
    if _name not in _registered:
        fail(f"{_name} has a blockstate and is neither an ore of the catalogue nor a block of "
             f"the roster, so nothing here can say what mines it")
_tool_blocks = []
_asking_untagged = []
_untagged = []
_by_hand_blocks = []
for _name in _registered:
    _tags = MINEABLE.get(f"{NS}:{_name}", set())
    _kind = _kind_of_block.get(_name)
    if _kind is None:
        _asks, _how, _wanted = _ore_asks, _ore_how, [_ore_tool]
    else:
        _asks, _how = _kind_asks.get(_kind, (None, ""))
        _wanted = [] if _kind in MINE_EXEMPT else (["pickaxe"] if _kind in _pickaxe_kinds else None)
        if _wanted is None:
            fail(f"{_name} is a {_kind.lower()} block, a kind HarvestRules does not say what mines")
            continue
    if _asks:
        _tool_blocks.append(_name)
    if len(_tags) > 1:
        fail(f"{_name} is in {len(_tags)} mineable tags ({', '.join(sorted(_tags))}), so two "
             f"tools are right and the rules can only have named one")
    if _kind in MINE_EXEMPT:
        _by_hand_blocks.append(_name)
        if _asks:
            fail(f"{_name} is exempt from the mineable tags ({MINE_EXEMPT[_kind]}), but it "
                 f"asks for the right tool: {_how}. With no tag no tool is right, so it "
                 f"would drop nothing")
        if _tags:
            fail(f"{_name} is exempt from the mineable tags and in one all the same; take "
                 f"its kind off MINE_EXEMPT or the block out of the tag")
        continue
    if not _tags:
        (_asking_untagged if _asks else _untagged).append(_name)
    if sorted(_tags) != _wanted:
        fail(f"the rules put {_name} under {' and '.join(_wanted) or 'no tool'} and the tags "
             f"under {' and '.join(sorted(_tags)) or 'none'}; the tags are written from the "
             f"rules by tools/gen_ores.py, so one of them was edited by hand")
if _asking_untagged:
    fail(f"{len(_asking_untagged)} block(s) ask for the right tool and are in no tool's tag, "
         f"so no tool is ever right and they drop nothing however they are mined: "
         + ", ".join(_asking_untagged[:6]))
if _untagged:
    fail(f"{len(_untagged)} block(s) are in no mineable tag and not exempt, so every tool "
         f"breaks them at a fist's pace: " + ", ".join(_untagged[:6]))
print(f"what mines them    : {len(_registered)} block(s), {len(_tool_blocks)} asking for the "
      f"right tool, every one in exactly one tool's tag or of a kind broken by hand with a "
      f"reason: {len(_registered) - len(_by_hand_blocks)} by {_ore_tool}, "
      f"{len(_by_hand_blocks)} by hand")

# A gem's raw chunk goes on to the gem (29 Sep 2026). The drop order finds a
# raw form before a gem, and the agronomy tags a chunk into
# c:raw_materials/<gem> for every gem here, so a ruby ore drops a ruby chunk.
# With no furnace step from the chunk to the gem, the six Cordon gems, their
# blocks and the pyratite plate were things nothing in the pack made, and this
# module's diamond, emerald, lapis and quartz ores gave a chunk good for nothing
# (check_obtainable.py). The game's gems are named here, not read from the
# generator, so a gem the generator forgets is caught rather than agreed with.
GAME_GEMS = {"minecraft:diamond", "minecraft:emerald", "minecraft:lapis_lazuli", "minecraft:quartz"}
gem_results = {family: f"{NS}:{family}" for family, kind in roster if kind == "GEM"}
for _, family, _, _, vanilla in rows:
    if vanilla.strip('"') in GAME_GEMS:
        gem_results[family] = vanilla.strip('"')
for family, gem in sorted(gem_results.items()):
    for suffix, cook in (("", "smelting"), ("_blasting", "blasting")):
        body = load(recipes, f"{family}_from_raw{suffix}.json")
        if body is None:
            fail(f"{family} cannot be smelted from its raw chunk ({family}_from_raw{suffix}.json is missing), "
                 f"so the chunk its ore drops is a dead end and {gem} is a thing the ore never gives")
        elif body.get("type") != f"minecraft:{cook}" \
                or body.get("ingredient", {}).get("tag") != f"c:raw_materials/{family}" \
                or body.get("result", {}).get("id") != gem:
            fail(f"{family}_from_raw{suffix}.json is not a {cook} of #c:raw_materials/{family} into {gem}, "
                 f"so a chunk from the ground still does not become the gem")
print(f"the gem chunks     : {len(gem_results)} gem(s) with an ore here, each smelted and blasted from "
      f"its raw chunk")

# ---------------------------------------------------------------------------
# 7. The roots' sources (rules/CordonSources.java; docs/SPEC_FOUNDRY.md §4)
# ---------------------------------------------------------------------------
#
# "The ground does not give them, but a world does." Nine things - the six
# drops and the three shards - are the tier-0 roots of every Cordoned alloy,
# and for a while nothing in the game gave any of them: no table, no modifier,
# no recipe. The harness proves the table in the rules is whole; this proves
# the table reached the disk - one loot modifier per row, on the vanilla table
# the row names, at the row's chance, adding this module's table for the seed,
# and every one of them listed where NeoForge looks.

sources_src = strip_comments(read(PURE, "CordonSources.java"), keep_strings=True)
source_rows = re.findall(r'\bat\("([a-z_]+)",\s*"([a-z_:/]+)"\)', sources_src)
if not source_rows:
    fail("CordonSources has no rows, so no root is left anywhere and every Cordoned alloy is unmakeable")
chances = {}
for name in ("KILL_CHANCE", "CHEST_CHANCE"):
    found = re.search(r"public static final float " + name + r" = ([0-9.]+)F;", sources_src)
    chances[name] = float(found.group(1)) if found else None
    if found is None:
        fail(f"CordonSources has no {name}, so the generator cannot say how often a root is left")
seeds = list(roster_drops) + [family for family, kind in roster if kind == "SHARD"]
sourced = {seed: [table for s_seed, table in source_rows if s_seed == seed] for seed in seeds}
for seed, tables in sourced.items():
    if not tables:
        fail(f"{seed} is a root nothing in the world leaves: no row in CordonSources names it, so every "
             f"alloy made of it is unobtainable in survival")
seen_rows = set()
for seed, table in source_rows:
    if (seed, table) in seen_rows:
        fail(f"{seed} from {table} is written twice in CordonSources, so it rolls at twice the chance")
    seen_rows.add((seed, table))
    if seed not in seeds:
        fail(f"CordonSources leaves {seed}, which is neither a drop nor a shard the roster ships")
    if not table.startswith("minecraft:"):
        fail(f"{seed} is left by {table}, which is another mod's table; this module names only vanilla")
modifiers_dir = os.path.join(DATA, "loot_modifiers")
glm_entries = glm.get("entries", [])
for seed, table in source_rows:
    kill = table.startswith("minecraft:entities/")
    name = f"{seed}_from_{table.rsplit('/', 1)[-1]}"
    body = load(modifiers_dir, name + ".json")
    if body is None:
        fail(f"{seed} has no loot modifier for {table} at loot_modifiers/{name}.json, so the row in "
             f"CordonSources is a source on paper only")
        continue
    if body.get("type") != "neoforge:add_table":
        fail(f"loot_modifiers/{name}.json is a {body.get('type')}, not neoforge:add_table")
    if body.get("table") != f"{NS}:cordon/{seed}":
        fail(f"loot_modifiers/{name}.json adds {body.get('table')} rather than {NS}:cordon/{seed}")
    conditions = body.get("conditions", [])
    ids = [c.get("loot_table_id") for c in conditions if c.get("condition") == "neoforge:loot_table_id"]
    if ids != [table]:
        fail(f"loot_modifiers/{name}.json listens for {ids} rather than {table}, so it fires on the "
             f"wrong table or on every table there is")
    rolled = [c.get("chance") for c in conditions if c.get("condition") == "minecraft:random_chance"]
    wanted = chances["KILL_CHANCE" if kill else "CHEST_CHANCE"]
    if len(rolled) != 1 or wanted is None or abs(rolled[0] - wanted) > 1e-6:
        fail(f"loot_modifiers/{name}.json rolls at {rolled} rather than the rules' {wanted}, so a root "
             f"is left at a rate the harness never saw")
    if kill and not any(c.get("condition") == "minecraft:killed_by_player" for c in conditions):
        fail(f"loot_modifiers/{name}.json pays for a kill nobody made, so a grinder is a root mine")
    if f"{NS}:{name}" not in glm_entries:
        fail(f"{NS}:{name} is not listed in global_loot_modifiers, so it is shipped and never runs")
for seed in seeds:
    table = load(DATA, f"loot_table/cordon/{seed}.json") or {}
    if f'"{NS}:{seed}"' not in json.dumps(table):
        fail(f"loot_table/cordon/{seed}.json does not drop {NS}:{seed}, so the modifier adds nothing")
for name in sorted(os.listdir(modifiers_dir)) if os.path.isdir(modifiers_dir) else []:
    if name == "unifying.json" or not name.endswith(".json"):
        continue
    stem = name[:-5]
    if stem not in {f"{seed}_from_{table.rsplit('/', 1)[-1]}" for seed, table in source_rows}:
        fail(f"loot_modifiers/{name} answers to no row of CordonSources, so it is a source the harness "
             f"never saw")
harness_src = strip_comments(read(HARNESS))
if "CordonSources.unsourced().isEmpty()" not in harness_src \
        or "CordonSources.duplicated().isEmpty()" not in harness_src:
    fail("OreHarness never asks CordonSources whether every seed is sourced and none twice, so the "
         "table is a table nothing executes")
# Written by the generator, and the same bytes every run.
gen = os.path.join(WORK, "tools/gen_ores.py")
with tempfile.TemporaryDirectory() as scratch:
    made = subprocess.run([sys.executable, gen], capture_output=True, text=True, cwd=WORK,
                          env={**os.environ, "ELYSIUM_ORES_OUT": scratch, "PYTHONIOENCODING": "utf-8"})
    if made.returncode != 0:
        fail(f"tools/gen_ores.py no longer runs: {made.stderr.strip()[-300:]}")
    else:
        drift = []
        for rel in ("data/elysiumores/loot_modifiers", "data/elysiumores/loot_table/cordon",
                    "data/neoforge/loot_modifiers"):
            made_dir = os.path.join(scratch, "src/main/resources", rel)
            have_dir = os.path.join(RES, rel)
            names = set(os.listdir(made_dir)) if os.path.isdir(made_dir) else set()
            names |= set(os.listdir(have_dir)) if os.path.isdir(have_dir) else set()
            for name in sorted(names):
                if not samefile.same(os.path.join(made_dir, name), os.path.join(have_dir, name)):
                    drift.append(f"{rel}/{name}")
        if drift:
            fail(f"the shipped loot modifiers are not what tools/gen_ores.py writes: {drift[:5]}")
print(f"the roots          : {len(seeds)} roots left by {len(source_rows)} vanilla tables, each a listed "
      f"loot modifier at the rules' chance, none twice")

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
