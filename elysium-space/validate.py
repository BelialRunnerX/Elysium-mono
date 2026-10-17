#!/usr/bin/env python3
"""
Validate Elysium Space against its sources.

Run from the repo root:   python3 elysium-space/validate.py

  1. INDEPENDENCE. Ported from Stellaris, but it must need neither Stellaris
     nor Stellaris's libraries (Architectury, Potentials, Sky Aesthetics): no
     reference in code (string literals included), no mods.toml dependency but
     the library, and no import of another Elysium content module.
  2. CREDIT. Stellaris is CC BY-NC-SA 4.0. CREDITS.md names Exodus Studio and
     the license, and the mod's declared license says the same.
  3. PURITY. The world table compiles on the JDK alone and WorldHarness runs.
  4. REFERENCES. Every elysiumspace: id in the data resolves to a file or a
     registered block, and no stellaris: id survived the port.
  5. COMPLETENESS. Every block has a blockstate, models whose textures exist, an
     item model, lang and a loot table. Every world in the table has a
     dimension, and every dimension is in the table. Every block is in a
     mineable tag, or is exempt for a stated reason and needs no tool. Each
     world's stone breaks as vanilla's does (Silk Touch keeps it, any other
     pickaxe gives its cobblestone, a furnace turns that back), and the stones
     and ores are pickaxe-mineable.
  6. THE EMERALD BASTION. No monsters in its biomes, deepslate for stone,
     no stone or cobblestone anywhere in its generation, emerald the most
     common ore, and no oceans.
"""
import json
import os
import re
import subprocess
import sys
import tempfile

WORK = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(WORK, "src/main/resources")
DATA = os.path.join(RES, "data/elysiumspace")
ASSETS = os.path.join(RES, "assets/elysiumspace")
SRC = os.path.join(WORK, "src/main/java/com/elysium/space")
PURE = os.path.join(SRC, "world/SpaceWorld.java")
ROUTE = os.path.join(SRC, "world/SpaceRoute.java")
SUITAIR = os.path.join(SRC, "world/SuitAir.java")
MACHINES = os.path.join(SRC, "world/MachineRules.java")
JETS = os.path.join(SRC, "world/JetRules.java")
MOBRULES = os.path.join(SRC, "world/MobRules.java")
STATIONS = os.path.join(SRC, "world/StationRules.java")
GATES = os.path.join(SRC, "world/GateRules.java")
FREIGHT = os.path.join(SRC, "world/FreightRules.java")
HARNESS = os.path.join(WORK, "tools/WorldHarness.java")
NS = "elysiumspace"

problems = []


def squashed(text):
    """One method's source with every run of whitespace made one space.

    A rule that names a whole statement has to survive the statement being
    re-wrapped, or it fails the first time somebody tidies the file and then
    gets deleted rather than fixed.
    """
    return re.sub(r"\s+", " ", text)


def body_of(src, signature):
    """The text between the braces of one method. A rule scoped with `find`
    alone reaches past the method into everything after it, which is how a
    check ends up passing because the thing it was looking for survives in a
    later declaration."""
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


def fail(msg):
    problems.append(msg)


def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", " ", text)


if not os.path.isdir(SRC):
    print("This is not the elysium-space tree.")
    sys.exit(1)

# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------
FOREIGN = re.compile(r"(stellaris|st0x0ef|architectury|dev\.architectury|potentials|"
                     r"sky_aesthetics|skyaesthetics|fr\.tathan)", re.I)
java_files = [os.path.join(d, f) for d, _, fs in os.walk(SRC) for f in fs if f.endswith(".java")]
ALLOWED_IMPORTS = ("java.", "javax.", "net.minecraft.", "net.neoforged.", "com.mojang.", "io.netty.", "com.google.gson.", "org.joml.",
                   "org.slf4j.", "com.elysium.lib.", "com.elysium.space.")
for path in java_files:
    raw = open(path, encoding="utf-8").read()
    code = strip_comments(raw)
    rel = os.path.relpath(path, WORK)
    hit = FOREIGN.search(code)
    if hit:
        fail(f"{rel} refers to {hit.group(0)!r} in code. The port replaced Stellaris and "
             f"its libraries; nothing may reach for them")
    for imp in re.findall(r"\bimport\s+(?:static\s+)?([\w.]+)\s*;", raw):
        if not imp.startswith(ALLOWED_IMPORTS):
            fail(f"{rel} imports {imp}. This module depends on the library, the game and "
                 f"NeoForge only")
toml_path = os.path.join(RES, "META-INF/neoforge.mods.toml")
toml = open(toml_path, encoding="utf-8").read()
deps = set(re.findall(r'modId="([^"$]+)"', toml))
extra = deps - {"neoforge", "minecraft", "elysiumlib"}
if extra:
    fail(f"mods.toml declares a dependency on {sorted(extra)}; the library is the only one allowed")
if "elysiumlib" not in deps:
    fail("mods.toml does not require elysiumlib")
print(f"independence       : {len(java_files)} source file(s), no Stellaris or its libraries, "
      f"depends on the library only")

# ---------------------------------------------------------------------------
# 2. Credit
# ---------------------------------------------------------------------------
credits = os.path.join(WORK, "CREDITS.md")
text = open(credits, encoding="utf-8").read() if os.path.isfile(credits) else ""
for need in ("Stellaris", "Exodus Studio", "CC BY-NC-SA 4.0"):
    if need not in text:
        fail(f"CREDITS.md does not say {need!r}; the license requires the attribution")
props = open(os.path.join(WORK, "gradle.properties"), encoding="utf-8").read()
if not re.search(r"^mod_license=.*CC BY-NC-SA 4\.0", props, re.M):
    fail("gradle.properties does not declare CC BY-NC-SA 4.0 as the mod's license")
shipped = os.path.join(RES, "CREDITS-elysiumspace.md")
if not os.path.isfile(shipped) or open(shipped, encoding="utf-8").read() != text:
    fail("src/main/resources/CREDITS-elysiumspace.md is missing or differs from CREDITS.md; "
         "the attribution has to travel inside the jar, where the license line points")
print("credit             : Stellaris, Exodus Studio, CC BY-NC-SA 4.0, shipped in the jar")

# ---------------------------------------------------------------------------
# 3. Purity: the world table runs without a game
# ---------------------------------------------------------------------------
for _pure in (PURE, ROUTE, SUITAIR, MACHINES, JETS, MOBRULES, STATIONS, GATES,
              FREIGHT):
    for imp in re.findall(r"\bimport\s+(?:static\s+)?([\w.]+)\s*;", open(_pure, encoding="utf-8").read()):
        if not imp.startswith("java."):
            fail(f"{os.path.relpath(_pure, WORK)} imports {imp}; the tables must load without a game")
with tempfile.TemporaryDirectory() as out:
    compiled = subprocess.run(["javac", "-nowarn", "-d", out, PURE, ROUTE, SUITAIR, MACHINES, JETS, MOBRULES, STATIONS, GATES, FREIGHT, HARNESS],
                              capture_output=True, text=True)
    lines = [l for l in (compiled.stdout + compiled.stderr).splitlines() if "JAVA_TOOL" not in l]
    if compiled.returncode != 0:
        fail("the world table does not compile on the JDK alone:\n      " + "\n      ".join(lines))
    else:
        ran = subprocess.run(["java", "-cp", out, "com.elysium.space.world.WorldHarness"],
                             capture_output=True, text=True)
        lines = [l for l in (ran.stdout + ran.stderr).splitlines() if "JAVA_TOOL" not in l]
        if ran.returncode != 0:
            fail("the WorldHarness failed:\n      " + "\n      ".join(lines))
        else:
            print("world table        : " + (lines[0] if lines else "ran"))

# ---------------------------------------------------------------------------
# 4. References
# ---------------------------------------------------------------------------
blocks_src = strip_comments(open(os.path.join(SRC, "registry/SpaceBlocks.java"), encoding="utf-8").read())
BLOCKS = re.findall(r'(?:BLOCKS\.register|\bore|\bmetal|\bdeco|\bstairs|\bslab|\bpillar)\("([a-z_]+)"', blocks_src)
items_src = strip_comments(open(os.path.join(SRC, "registry/SpaceItems.java"), encoding="utf-8").read())
ITEMS = re.findall(r'ITEMS\.register(?:Item|SimpleItem)\("([a-z_]+)"', items_src)
fluids_src = strip_comments(open(os.path.join(SRC, "registry/SpaceFluids.java"), encoding="utf-8").read())
FLUIDS = re.findall(r'new Kind\("([a-z_]+)",.*?,\s*(true|false),\s*0x', fluids_src, re.S)
LIQUIDS = [n for n, liquid in FLUIDS if liquid == "true"]
mobrules_src = strip_comments(open(os.path.join(SRC, "world/MobRules.java"), encoding="utf-8").read())
MOB_CONST = dict(re.findall(r'Mob ([A-Z_]+) = new Mob\("([a-z_]+)"', mobrules_src))
ent_src = strip_comments(open(os.path.join(SRC, "registry/SpaceEntities.java"), encoding="utf-8").read())
MOBS = [MOB_CONST.get(c, c) for c in re.findall(r'mob\(MobRules\.([A-Z_]+),', ent_src)]
ENTITIES = re.findall(r'TYPES\.register\("([a-z_]+)"', ent_src) + MOBS
EGGS = re.findall(r'\begg\("([a-z_]+)",', items_src)
ITEMS += [m + "_spawn_egg" for m in EGGS]
stations_src = strip_comments(open(os.path.join(SRC, "world/StationRules.java"), encoding="utf-8").read())
STATION_SIZES = [(i, int(st), int(lu), int(x), int(y), int(z)) for i, st, lu, x, y, z in re.findall(
    r'[A-Z]+\("([a-z]+)", (\d+), (\d+), (\d+), (\d+), (\d+)\)', stations_src)]
if "blueprint(com.elysium.space.world.StationRules.Size." in items_src:
    ITEMS += [f"{i}_station_blueprint" for i, *_ in STATION_SIZES]
PROCESSORS = re.findall(r'TYPES\.register\("([a-z_]+)"',
                        strip_comments(open(os.path.join(SRC, "worldgen/SpaceProcessors.java"), encoding="utf-8").read()))
COMPONENTS = re.findall(r'COMPONENTS\.register\("([a-z_]+)"',
                        strip_comments(open(os.path.join(SRC, "registry/SpaceData.java"), encoding="utf-8").read()))
ITEMS += [n + "_bucket" for n in LIQUIDS]
class _NormalisedPaths(dict):
    """A path-keyed dict that reads the same file whichever separator built the key.

    os.walk hands back native separators and os.path.join keeps the slashes it
    was given, so on Windows the same file had two spellings and every lookup
    below missed. Every key is normalised on the way in and on the way out.
    """

    def __setitem__(self, key, value):
        super().__setitem__(os.path.normpath(key), value)

    def __getitem__(self, key):
        return super().__getitem__(os.path.normpath(key))

    def __contains__(self, key):
        return super().__contains__(os.path.normpath(key))

    def get(self, key, default=None):
        return super().get(os.path.normpath(key), default)


json_files = _NormalisedPaths()
for d, _, fs in os.walk(RES):
    for f in fs:
        if f.endswith(".json"):
            p = os.path.join(d, f)
            try:
                json_files[p] = json.load(open(p, encoding="utf-8"))
            except Exception as exc:
                fail(f"invalid JSON {os.path.relpath(p, WORK)}: {exc}")


def strings(o, key=None):
    """Every string in a JSON value. A value under a "tag" key (recipe
    ingredients, ore targets) is a tag even without its '#', and comes out with
    one so it is checked as a tag."""
    if isinstance(o, dict):
        for k, v in o.items():
            yield k
            yield from strings(v, k)
    elif isinstance(o, list):
        for v in o:
            yield from strings(v, key)
    elif isinstance(o, str):
        yield "#" + o if key == "tag" and not o.startswith("#") else o


DATA_KINDS = ["dimension", "dimension_type", "worldgen/noise_settings", "worldgen/biome",
              "worldgen/configured_feature", "worldgen/placed_feature",
              "worldgen/density_function", "damage_type", "neoforge/biome_modifier", "worldgen/structure",
              "worldgen/structure_set", "worldgen/template_pool", "worldgen/processor_list", "loot_table"]
tag_dirs = [os.path.join(DATA, "tags", k) for k in ("block", "item", "worldgen/biome")]
refs = 0
for path, data in json_files.items():
    rel = os.path.relpath(path, WORK)
    for s in strings(data):
        if "stellaris:" in s:
            fail(f"{rel} still names {s!r}; the port renames every Stellaris id")
        m = re.fullmatch(r"(#?)elysiumspace:([a-z0-9_/]+)", s)
        if not m or "/models/" in path.replace(os.sep, "/") or "/blockstates/" in path.replace(os.sep, "/"):
            continue
        refs += 1
        tag, name = m.group(1), m.group(2)
        if tag:
            if not any(os.path.isfile(os.path.join(t, name + ".json")) for t in tag_dirs):
                fail(f"{rel} names tag #{NS}:{name}, which does not exist")
            continue
        if os.path.isfile(os.path.join(DATA, "structure", name + ".nbt")):
            continue
        if os.path.isfile(os.path.join(ASSETS, "neoforge/animations/entity", name + ".json")):
            continue
        if name in BLOCKS or name in ITEMS or name in LIQUIDS or name in ENTITIES or name in COMPONENTS or name in PROCESSORS or any(os.path.isfile(os.path.join(DATA, k, name + ".json")) for k in DATA_KINDS):
            continue
        fail(f"{rel} names {NS}:{name}, which is neither a block nor a data file here")
import gzip as _gz
_nbt_dir = os.path.join(DATA, "structure")
_nbt_files = [os.path.join(d, f) for d, _, fs in os.walk(_nbt_dir) for f in fs if f.endswith(".nbt")]
for _p in _nbt_files:
    _raw = _gz.decompress(open(_p, "rb").read())
    _rel = os.path.relpath(_p, WORK)
    if b"stellaris:" in _raw:
        fail(f"{_rel} still contains a stellaris: id; the template would place missing blocks")
    for _m in set(re.findall(rb"elysiumspace:([a-z0-9_/]+)", _raw)):
        _n = _m.decode()
        if "/" in _n:
            if not (os.path.isfile(os.path.join(DATA, "loot_table", _n + ".json"))
                    or os.path.isfile(os.path.join(DATA, "worldgen/template_pool", _n + ".json"))):
                fail(f"{_rel} names {NS}:{_n}, which is neither a loot table nor a template pool")
        elif _n not in BLOCKS and _n not in LIQUIDS and _n not in ENTITIES:
            fail(f"{_rel} places {NS}:{_n}, which is neither a registered block nor a mob")
print(f"references         : {refs} {NS}: ids resolve; no stellaris: id left; {len(_nbt_files)} structure templates clean")

# ---------------------------------------------------------------------------
# 5. Completeness
# ---------------------------------------------------------------------------
lang = json.load(open(os.path.join(ASSETS, "lang/en_us.json"), encoding="utf-8"))
for b in BLOCKS:
    bs = os.path.join(ASSETS, "blockstates", b + ".json")
    if not os.path.isfile(bs):
        fail(f"block {b} has no blockstate")
        continue
    for model in set(re.findall(r'"model":\s*"elysiumspace:([a-z0-9_/]+)"', open(bs).read())):
        mp = os.path.join(ASSETS, "models", model + ".json")
        if not os.path.isfile(mp):
            fail(f"{b}'s blockstate uses model {model}, which is missing")
            continue
        for tex in re.findall(r'"elysiumspace:((?:block|item)/[a-z0-9_]+)"', open(mp).read()):
            if not os.path.isfile(os.path.join(ASSETS, "textures", tex + ".png")):
                fail(f"model {model} uses texture {tex}, which is missing")
    if f"block.{NS}.{b}" not in lang:
        fail(f"block {b} has no lang entry")
    if not os.path.isfile(os.path.join(DATA, "loot_table/blocks", b + ".json")):
        fail(f"block {b} has no loot table; breaking it would drop nothing")
    if b != "pale_vines_plant" and not os.path.isfile(os.path.join(ASSETS, "models/item", b + ".json")):
        fail(f"block {b} has no item model")
for it in ITEMS:
    if f"item.{NS}.{it}" not in lang:
        fail(f"item {it} has no lang entry")
    ip = os.path.join(ASSETS, "models/item", it + ".json")
    if not os.path.isfile(ip):
        fail(f"item {it} has no item model")
    else:
        for tex in re.findall(r'"elysiumspace:((?:block|item)/[a-z0-9_]+)"', open(ip).read()):
            if not os.path.isfile(os.path.join(ASSETS, "textures", tex + ".png")):
                fail(f"item {it} uses texture {tex}, which is missing")
for ent in ENTITIES:
    if f"entity.{NS}.{ent}" not in lang:
        fail(f"entity {ent} has no lang entry")
    if not os.path.isfile(os.path.join(ASSETS, "textures/entity", ent + ".png")):
        fail(f"entity {ent} has no texture")
# Every fixed translation key the code asks for is in the lang file (or is the game's own).
VANILLA_KEYS = {"container.inventory"}
for path in java_files:
    for key in re.findall(r'Component\.translatable\("([a-z0-9_.]+)"', strip_comments(open(path, encoding="utf-8").read())):
        if key not in lang and not key.endswith(".") and key not in VANILLA_KEYS:
            fail(f"{os.path.relpath(path, WORK)} asks for {key}, which the lang file does not have")
if sorted(n for n, _ in FLUIDS) != ["diesel", "fuel", "hydrogen", "oil", "oxygen"]:
    fail(f"expected Stellaris's five fluids (oil, fuel, diesel, oxygen, hydrogen), found {[n for n, _ in FLUIDS]}")
tank_loot = os.path.join(DATA, "loot_table/blocks/fluid_tank.json")
if not os.path.isfile(tank_loot) or f"{NS}:tank_fluid" not in open(tank_loot).read():
    fail("the Fluid Tank's loot table does not copy its contents; breaking a full tank would destroy the fluid")
for name, liquid in FLUIDS:
    if f"fluid_type.{NS}.{name}" not in lang:
        fail(f"fluid {name} has no fluid_type lang entry; tanks and pipes would show the raw key")
    if liquid == "true":
        if not os.path.isfile(os.path.join(ASSETS, "blockstates", name + ".json")):
            fail(f"liquid {name} has no blockstate")
        if f"block.{NS}.{name}" not in lang:
            fail(f"liquid {name} has no block name")
world_src = open(PURE, encoding="utf-8").read()
WORLDS = re.findall(r'new SpaceWorld\("([a-z_]+)"', world_src)
DIMS = sorted(f[:-5] for f in os.listdir(os.path.join(DATA, "dimension")))
if sorted(WORLDS) != DIMS:
    fail(f"the world table has {sorted(WORLDS)} but the dimensions are {DIMS}; a world without "
         f"a dimension cannot be reached, and a dimension without a row has no hazards")
for w in WORLDS:
    if f"dimension.{NS}.{w}" not in lang:
        fail(f"world {w} has no display name")
if f"elysium.hazard.{NS}.vacuum" not in lang:
    fail("the vacuum hazard has no name")
# Art borrowed from vanilla mangrove must be ours after the rename. A bare
# "block/mangrove_x" path means minecraft:, and Emgrove would render red.
# The prefix is normalised as json_files' keys are: with the forward slashes of
# "src/main/resources" left in, it matched no path on Windows and this check
# never ran there.
_assets = os.path.normpath(os.path.join(RES, "assets")) + os.sep
for path in json_files:
    if path.startswith(_assets):
        for s in strings(json_files[path]):
            if "mangrove" in s:
                fail(f"{os.path.relpath(path, WORK)} still points at {s!r}; Emgrove's "
                     f"art is the green copy under elysiumspace:")
print(f"completeness       : {len(BLOCKS)} blocks drawn, named and dropping; "
      f"{len(WORLDS)} worlds each with a dimension")

# ---------------------------------------------------------------------------
# 5a. Breaking blocks: every block is mined by some tool, and each world's
#     stone breaks into its cobblestone
# ---------------------------------------------------------------------------
# Every block is in a mineable tag, or is on MINE_EXEMPT with its reason. With
# no tag every tool mines a block at a fist's pace, and a block that asks for
# the right tool drops nothing: the stones, ores, metal blocks and machines did
# that until 29 September, and the Freight Terminal with them. An exemption is
# read where the block is registered, not taken on trust: an exempt block must
# not ask for the right tool, and any vanilla block it copies (and so inherits
# that from) must be one that breaks at a touch or is a liquid.
MINEABLE = {}
for _tool in ("pickaxe", "axe", "shovel", "hoe"):
    _tag = json_files.get(os.path.join(RES, f"data/minecraft/tags/block/mineable/{_tool}.json"))
    for _id in _tag.get("values", []) if isinstance(_tag, dict) else []:
        MINEABLE.setdefault(_id, set()).add(_tool)
PICKAXE = {_id for _id, _tools in MINEABLE.items() if "pickaxe" in _tools}
MINE_EXEMPT = {
    "pale_vines": "it breaks at a touch, as cave vines do",
    "pale_vines_plant": "it breaks at a touch, as cave vines do",
    "emgrove_propagule": "it breaks at a touch, as a mangrove propagule does",
}
MINE_EXEMPT.update({_n: "a liquid: a bucket takes it up and nothing mines it" for _n in LIQUIDS})
# The vanilla blocks an exempt block may copy. None of them asks for a tool. A
# block that copies none has every property written where it is registered.
BY_HAND = {"CAVE_VINES", "CAVE_VINES_PLANT", "MANGROVE_PROPAGULE", "WATER"}


def _statement(src, start):
    """The Java statement from `start` to the semicolon that ends it."""
    depth = 0
    for i in range(start, len(src)):
        if src[i] in "([{":
            depth += 1
        elif src[i] in ")]}":
            depth -= 1
        elif src[i] == ";" and depth == 0:
            return src[start:i]
    return src[start:]


def registration(name):
    """Where a block is registered, with the body of any helper it goes through:
    everything its properties are made of. Empty if it cannot be found."""
    if name in LIQUIDS:
        at = fluids_src.find("BLOCKS.register(name,")
        return _statement(fluids_src, at) if at >= 0 else ""
    m = re.search(r'(?:BLOCKS\.register|\b(ore|metal|deco|stairs|slab|pillar))\("' + re.escape(name) + '"',
                  blocks_src)
    if not m:
        return ""
    helper = body_of(blocks_src, f"DeferredBlock<Block> {m.group(1)}(") if m.group(1) else ""
    return _statement(blocks_src, m.start()) + helper


_mined = _touch = 0
for b in BLOCKS + LIQUIDS:
    if b not in MINE_EXEMPT:
        if f"{NS}:{b}" in MINEABLE:
            _mined += 1
        else:
            fail(f"{b} is in no mineable tag, so it takes a fist and drops nothing quickly")
        continue
    if b not in LIQUIDS:
        _touch += 1
    why, made = MINE_EXEMPT[b], registration(b)
    copied = set(re.findall(r"\bBlocks\.([A-Z_]+)", made))
    if f"{NS}:{b}" in MINEABLE:
        fail(f"{b} is exempt from the mineable tags but is in "
             f"{', '.join(sorted(MINEABLE[NS + ':' + b]))}; take it off MINE_EXEMPT")
    elif not made:
        fail(f"{b} is exempt from the mineable tags, but nothing here finds where it is "
             f"registered, so nothing shows it needs no tool")
    elif "requiresCorrectToolForDrops" in made:
        fail(f"{b} is exempt from the mineable tags ({why}), but it asks for the right tool "
             f"to drop; with no tag no tool is right, so it would drop nothing")
    elif copied - BY_HAND:
        _odd = ", ".join("Blocks." + c for c in sorted(copied - BY_HAND))
        fail(f"{b} is exempt from the mineable tags ({why}), but it copies {_odd}, which is "
             f"not one that breaks at a touch")
for b in sorted(set(MINE_EXEMPT) - set(BLOCKS) - set(LIQUIDS)):
    fail(f"{b} is exempt from the mineable tags but is not registered; take it off MINE_EXEMPT")
print(f"mineable           : {_mined} block(s) in a mineable tag; {len(LIQUIDS)} liquid(s) and "
      f"{_touch} that break at a touch exempt, each read where it is registered")

# Until 29 September the three stones dropped themselves: nothing made Frost
# Cobblestone, and the pale and cinder kinds were salvage from two ruins. A
# world's stone now breaks as vanilla stone does: Silk Touch keeps it, any other
# pickaxe gives the cobblestone, and a furnace turns that back. None of it
# happens unless minecraft:mineable/pickaxe names the stone. It copies vanilla
# stone, which drops nothing to a tool that is not right for it, and an ore asks
# for the right tool too. The generator had written that tag three times over,
# and the last write kept only the 49 building blocks.


def _condition(c, silk):
    """Whether one loot condition passes when a pickaxe breaks the block, with
    Silk Touch or without. None if this check cannot tell."""
    kind = str(c.get("condition", "")).replace("minecraft:", "") if isinstance(c, dict) else ""
    if kind == "survives_explosion":
        return True                      # a pickaxe is not an explosion
    if kind == "match_tool":
        wants = c.get("predicate")
        tests = wants.get("predicates") if isinstance(wants, dict) and set(wants) == {"predicates"} else None
        enchants = (tests.get("minecraft:enchantments")
                    if isinstance(tests, dict) and set(tests) == {"minecraft:enchantments"} else None)
        if isinstance(enchants, list) and enchants and all(
                isinstance(e, dict) and e.get("enchantments") == "minecraft:silk_touch" for e in enchants):
            return silk
        return None
    if kind == "inverted":
        inner = _condition(c.get("term"), silk)
        return None if inner is None else not inner
    if kind in ("any_of", "all_of"):
        parts = [_condition(term, silk) for term in c.get("terms", [])]
        if None in parts:
            return None
        return any(parts) if kind == "any_of" else all(parts)
    return None


def _runs(node, silk):
    if not isinstance(node, dict) or not isinstance(node.get("conditions", []), list):
        return None
    parts = [_condition(c, silk) for c in node.get("conditions", [])]
    return None if None in parts else all(parts)


def _entry(e, silk):
    """(whether the entry expanded, the items it offers the pool), or None."""
    ran = _runs(e, silk)
    if ran is None:
        return None
    if not ran:
        return False, []
    kind = str(e.get("type", "")).replace("minecraft:", "")
    if kind == "item":
        return True, [e.get("name")]
    if kind == "empty":
        return True, []
    if kind == "alternatives":           # the first child that expands, and only it
        for child in e.get("children", []):
            got = _entry(child, silk)
            if got is None or got[0]:
                return got
        return False, []
    if kind in ("group", "sequence"):
        offered = []
        for child in e.get("children", []):
            got = _entry(child, silk)
            if got is None:
                return None
            offered += got[1]
            if kind == "sequence" and not got[0]:
                return False, offered
        return True, offered
    return None


def pickaxe_drops(table, silk):
    """What a block's loot table can give when a pickaxe breaks the block, with
    Silk Touch or without: the sorted item ids, or None where the table asks
    something this check cannot answer."""
    found = set()
    for pool in table.get("pools", []):
        if not isinstance(pool, dict):
            return None
        rolls = pool.get("rolls", 1)
        if isinstance(rolls, (int, float)) and rolls <= 0:
            continue
        ran = _runs(pool, silk)
        if ran is None:
            return None
        for entry in pool.get("entries", []) if ran else []:
            got = _entry(entry, silk)
            if got is None:
                return None
            found.update(got[1])
    return sorted(found)


def _said(drops):
    return "something this check cannot read" if drops is None else ", ".join(drops) or "nothing"


def _ingredient_items(ingredient):
    both = ingredient if isinstance(ingredient, list) else [ingredient]
    return {one.get("item") for one in both if isinstance(one, dict)}


_recipes = os.path.normpath(os.path.join(DATA, "recipe")) + os.sep
SMELTED = {(next(iter(_ingredient_items(r.get("ingredient"))), None), r.get("result", {}).get("id"))
           for p, r in json_files.items()
           if p.startswith(_recipes) and isinstance(r, dict) and r.get("type") == "minecraft:smelting"
           and len(_ingredient_items(r.get("ingredient"))) == 1 and isinstance(r.get("result"), dict)}
_stones = 0
for cob in sorted(b for b in BLOCKS if b.endswith("_cobblestone")):
    base = cob[:-len("_cobblestone")] + "_stone"
    if base not in BLOCKS:
        fail(f"{cob} is registered but {base} is not, so the cobblestone has no stone to break out of")
        continue
    _stones += 1
    table = json_files.get(os.path.join(DATA, "loot_table/blocks", base + ".json"))
    if isinstance(table, dict):          # a missing table is reported above
        plain, silky = pickaxe_drops(table, False), pickaxe_drops(table, True)
        if plain != [f"{NS}:{cob}"]:
            fail(f"{base} gives {_said(plain)} to a pickaxe without Silk Touch, not {cob}. Vanilla "
                 f"stone gives cobblestone, and nothing else makes {cob} renewable")
        if silky != [f"{NS}:{base}"]:
            fail(f"{base} gives {_said(silky)} to Silk Touch; Silk Touch keeps the stone itself, "
                 f"as it keeps vanilla stone")
    if (f"{NS}:{cob}", f"{NS}:{base}") not in SMELTED:
        fail(f"no furnace recipe turns {cob} back into {base}, so the stone a pickaxe breaks "
             f"is gone for good")
    if f"{NS}:{base}" not in PICKAXE:
        fail(f"{base} is in no mineable tag, so no pickaxe is right for it and breaking it drops "
             f"nothing: it copies vanilla stone, which needs one")
if not _stones:
    fail("no world's stone has a cobblestone; the three stones should each break into one")
print(f"stone              : {_stones} worlds' stone keeps itself to Silk Touch, gives its "
      f"cobblestone to any other pickaxe and smelts back; stones and ores are pickaxe-mineable")

# ---------------------------------------------------------------------------
# 5b. The rocket: the server decides, and client code stays on the client
# ---------------------------------------------------------------------------
rocket = strip_comments(open(os.path.join(SRC, "entity/RocketEntity.java"), encoding="utf-8").read())
travel = re.search(r"public void travelTo\(ServerPlayer pilot, String destination\) \{(.*?)\n    \}", rocket, re.S)
if not travel:
    fail("RocketEntity.travelTo is gone; nothing checks a destination the client sent")
else:
    body = travel.group(1)
    for need, why in (("pilot.getVehicle() != this", "that the sender is riding this rocket"),
                      ("phase() != Phase.AWAITING", "that the rocket is waiting above the world"),
                      ("SpaceRoute.canTravel(from, destination, fuel()", "the destination against the fuel the server holds")):
        if need not in body:
            fail(f"RocketEntity.travelTo no longer checks {why}; a modified client could fly anywhere")
    if "SpaceRoute.canTravel" in body and "changeDimension" in body \
            and body.index("SpaceRoute.canTravel") > body.index("changeDimension"):
        fail("RocketEntity.travelTo moves the pilot before checking the route")
m = re.search(r"COUNTDOWN_TICKS\s*=\s*(\d+)", rocket)
if not m or int(m.group(1)) != 200:
    fail("the countdown is no longer Stellaris's 200 ticks (ten seconds)")
net = strip_comments(open(os.path.join(SRC, "network/SpaceNetwork.java"), encoding="utf-8").read())
if net.count("player.getVehicle() instanceof RocketEntity") < 2:
    fail("SpaceNetwork finds the rocket some way other than the sender's own vehicle")
for path in java_files:
    rel = os.path.relpath(path, SRC)
    if rel.startswith("client" + os.sep):
        continue
    src = open(path, encoding="utf-8").read()
    for imp in re.findall(r"^import\s+([\w.]+);", src, re.M):
        if imp.startswith("com.elysium.space.client.") and not rel.startswith("network" + os.sep):
            fail(f"{rel} imports {imp}; client classes load only on the client, and a server would crash")
        if imp.startswith("net.minecraft.client.") or imp.startswith("com.mojang.blaze3d."):
            fail(f"{rel} imports {imp}, a client-only class, outside the client package")
for fn in ("travelTo", "tryLaunch", "reachedSpace"):
    m = re.search(r"(?:public|private) void " + fn + r"\(.*?\n    \}", rocket, re.S)
    if m and "SpaceRoute.Fuel.FUEL" in m.group(0):
        fail(f"RocketEntity.{fn} assumes plain fuel; a hydrogen-motor rocket would be costed wrong")
# One fuel per motor: fuelIn switches on the motor, and every fuel SpaceRoute
# prices has a motor item that fits it (plain fuel is the engine itself) and a
# case that takes its own fuel and nothing else's.
fi = re.search(r"private int fuelIn\(ItemStack held\) \{(.*?)\n    \}", rocket, re.S)
_route_src = open(os.path.join(SRC, "world/SpaceRoute.java"), encoding="utf-8").read()
_enum_body = _route_src[_route_src.find("enum Fuel {"):]
_enum_body = _enum_body[:_enum_body.find(";")]
_fuels = re.findall(r"([A-Z]+)\([\d.]+D\)", _enum_body)
if len(_fuels) < 2:
    fail("SpaceRoute.Fuel reads as fewer than two fuels, so the motor rules below check nothing")
if not fi or "switch (motor())" not in fi.group(1):
    fail("RocketEntity.fuelIn no longer switches on the motor; two fuels would share one tank")
else:
    for _fuel in _fuels:
        if f"case {_fuel} ->" not in fi.group(1):
            fail(f"RocketEntity.fuelIn has no case for the {_fuel} motor, so a rocket with it can never be fuelled")
    # ...and each fuel item is named in its own motor's case and nowhere else.
    # The switch alone does not say so: a hydrogen canister tested before it,
    # or beside the fuel canister in the FUEL case, fills a rocket of any motor
    # with it. The hydrogen rule this replaced went with the switch (cf24eb16)
    # and nothing took its place until negtest_s6b.sh asked (17 October 2026).
    def _brace_body(text, opening):
        depth = 0
        for _at in range(opening, len(text)):
            depth += {"{": 1, "}": -1}.get(text[_at], 0)
            if depth == 0:
                return text[opening + 1:_at]
        return text[opening + 1:]
    _fuel_item = r'SpaceItems\.[A-Z_]+|SpaceFluids\.[A-Z_]+\.bucket|"[a-z0-9_]+:[a-z0-9_/.]+"'
    _named = re.findall(_fuel_item, fi.group(1))
    _cased = {}
    for _case in re.finditer(r"\bcase ([A-Z_]+) -> \{", fi.group(1)):
        for _item in re.findall(_fuel_item, _brace_body(fi.group(1), _case.end() - 1)):
            _cased.setdefault(_item, []).append(_case.group(1))
    for _item in sorted(set(_named)):
        _in = _cased.get(_item, [])
        if _named.count(_item) > len(_in):
            fail(f"RocketEntity.fuelIn does not tie {_item} to one motor: it is named outside the motor's "
                 f"own case, so a rocket with any motor takes it and two fuels would mix in one tank")
        elif len(_in) > 1:
            fail(f"RocketEntity.fuelIn does not tie {_item} to one motor: the {' and '.join(_in)} cases "
                 f"both take it, so two fuels would mix in one tank")
    # ...and the case it is named in is its own motor's. Being in one case is
    # not enough: the hydrogen canister moved from HYDROGEN into FUEL, or the
    # two canisters swapped, each sit in exactly one case and passed (review,
    # 17 October 2026). A fuel item is named for what it is, so each motor
    # has the words its fuels are named by, and the case that takes an item
    # must be the motor one of its words names; a motor this table does not
    # know fails, so a sixth fuel is not passed by default.
    _own_words = {"FUEL": ("fuel",), "HYDROGEN": ("hydrogen",), "URANIUM": ("uranium",),
                  "PLUTONIUM": ("plutonium",), "RADIOACTIVE": ("heavy_metal",)}
    for _fuel in _fuels:
        if _fuel not in _own_words:
            fail(f"SpaceRoute.Fuel has a {_fuel} motor whose fuel this validator cannot name; add "
                 f"its words to _own_words so fuelIn's {_fuel} case is held to its own fuel")
    def _is_own(item, motor):
        return any(word in item.lower() for word in _own_words.get(motor, ()))
    for _item, _in in sorted(_cased.items()):
        for _case_name in _in:
            if _case_name in _own_words and not _is_own(_item, _case_name):
                fail(f"RocketEntity.fuelIn does not tie {_item} to its own motor: the {_case_name} case "
                     f"takes it, so a {_case_name.lower()} rocket would burn a fuel that is not its own")
    for _fuel in _fuels:
        if _fuel in _own_words and not any(_fuel in _in and _is_own(_item, _fuel) for _item, _in in _cased.items()):
            fail(f"RocketEntity.fuelIn's {_fuel} case takes none of {_fuel.lower()}'s own fuel, so a "
                 f"rocket with that motor can never be fuelled")
_motor_of = re.search(r"public static SpaceRoute\.Fuel motorOf\(ItemStack held\) \{(.*?)\n    \}", rocket, re.S)
if not _motor_of:
    fail("RocketEntity.motorOf is gone; no upgrade fits any motor")
else:
    for _fuel in _fuels:
        if _fuel != "FUEL" and f"return SpaceRoute.Fuel.{_fuel};" not in _motor_of.group(1):
            fail(f"no upgrade item fits the {_fuel} motor (RocketEntity.motorOf), so SpaceRoute prices a fuel nothing burns")
hm = re.search(r"SpaceRoute\.Fuel fitted = motorOf\(held\);\s*if \(fitted != null\) \{(.*?)return InteractionResult\.sidedSuccess", rocket, re.S)
if not hm or not re.search(r"if \(phase\(\) != Phase\.IDLE[^{]*\|\| fuel\(\) > 0\) \{", hm.group(1)):
    fail("fitting a motor does not require an empty tank; the fuel in it would become the new motor's")
for _fuel in _fuels:
    if f'"elysiumspace.fuel.{_fuel.lower()}"' not in open(os.path.join(WORK, "src/main/resources/assets/elysiumspace/lang/en_us.json"), encoding="utf-8").read() and _fuel != "FUEL":
        fail(f"elysiumspace.fuel.{_fuel.lower()} is not in the language file, so fitting that motor prints a raw key")
launch = re.search(r"public void tryLaunch\(ServerPlayer asker\) \{(.*?)\n    \}", rocket, re.S)
if not launch or "getFirstPassenger() != asker" not in launch.group(1) or "phase() != Phase.IDLE" not in launch.group(1):
    fail("RocketEntity.tryLaunch does not check that the asker is its pilot and it is idle; "
         "anyone could launch anyone's rocket")
# The suit: it seals vacuum only because its element is vacuum's, and its air
# reads exposure through the library (so a filled room is no vacuum here too).
suit = strip_comments(open(os.path.join(SRC, "item/SpaceSuitItem.java"), encoding="utf-8").read())
hz = strip_comments(open(os.path.join(SRC, "hazard/SpaceHazards.java"), encoding="utf-8").read())
sm = re.search(r"getElement\(\) \{\s*return ElysiumElements\.(\w+);", suit)
hm = re.search(r"ElysiumElements\.(\w+),\s*[\d.]+F\)", hz)
if not sm or not hm or sm.group(1) != hm.group(1):
    fail(f"the space suit's element ({sm and sm.group(1)}) is not vacuum's ({hm and hm.group(1)}); "
         f"a full suit would not seal vacuum")
ev = strip_comments(open(os.path.join(SRC, "event/SuitEvents.java"), encoding="utf-8").read())
if "ElysiumHazardField.intensity(" not in ev or "SpaceHazards.VACUUM" not in ev:
    fail("SuitEvents decides exposure itself instead of asking the hazard pipeline; the suit "
         "and the hazard would disagree about where vacuum is")
if "SuitAir.step(" not in ev or "SuitAir.suffocating(" not in ev:
    fail("SuitEvents does not use SuitAir's tested rule for draining and suffocation")
for layer in ("space_suit_layer_1", "space_suit_layer_2"):
    if not os.path.isfile(os.path.join(ASSETS, "textures/models/armor", layer + ".png")):
        fail(f"the worn suit texture {layer}.png is missing; the suit would render as the missing texture")
for f in os.listdir(os.path.join(DATA, "damage_type")):
    msg = json.load(open(os.path.join(DATA, "damage_type", f)))["message_id"]
    for k in (f"death.attack.{msg}", f"death.attack.{msg}.player"):
        if k not in lang:
            fail(f"damage type {f[:-5]} dies with the raw key {k}")
# The cable and the pipe are runs: arms, plates, dye and pick as every run in
# the pack, checked by the shared art/runs.py so six modules agree.
import importlib.util as _ilu
_runs_spec = _ilu.spec_from_file_location("art_runs", os.path.join(os.path.dirname(WORK), "art", "runs.py"))
_art_runs = _ilu.module_from_spec(_runs_spec)
_runs_spec.loader.exec_module(_art_runs)
_conduit_src = open(os.path.join(SRC, "machine/ConduitBlock.java"), encoding="utf-8").read()
for _run in ("cable", "pipe"):
    for _problem in _art_runs.check("elysiumspace", _run, RES, _conduit_src):
        fail(_problem)
if "com.elysium.lib.run.Runs.declare(SpaceBlocks.CABLE," not in open(os.path.join(SRC, "ElysiumSpace.java"), encoding="utf-8").read() \
        or "com.elysium.lib.run.Runs.declare(SpaceBlocks.PIPE," not in open(os.path.join(SRC, "ElysiumSpace.java"), encoding="utf-8").read():
    fail("the cable or the pipe is not declared a run, so its plates have no colour and no tint handler")
if "implements RunDyed" not in open(os.path.join(SRC, "machine/ConduitBlockEntity.java"), encoding="utf-8").read():
    fail("a conduit forgets its dye: the block entity does not carry it")
print("runs               : the cable and the pipe draw arms and plates, take a dye, and keep it")

# Machines run on the tested rules, and pipes respect inputs and outputs.
def _src(rel):
    return strip_comments(open(os.path.join(SRC, rel), encoding="utf-8").read())
for rel, need in (("machine/FuelRefineryBlockEntity.java", "MachineRules.refineryBatches("),
                  ("machine/PumpjackBlockEntity.java", "MachineRules.pumped("),
                  ("machine/PumpjackBlockEntity.java", "MachineRules.oilDeposit("),
                  ("machine/WaterSeparatorBlockEntity.java", "MachineRules.separatorBatches("),
                  ("block/OxygenDistributorBlockEntity.java", "MachineRules.distributorOxygen("),
                  ("block/OxygenDistributorBlockEntity.java", "MachineRules.distributorEnergy("),
                  ("block/OxygenDistributorBlockEntity.java", "ElysiumRoomScan.interior("),
                  ("machine/SolarPanelBlockEntity.java", "MachineRules.solarPerTick(")):
    if need not in _src(rel):
        fail(f"{rel} no longer calls {need}; the machine runs on numbers nobody tested")
tanks = _src("machine/MachineTanks.java")
fill = re.search(r"public int fill\(.*?\n    \}", tanks, re.S)
drains = re.findall(r"public FluidStack drain\(.*?\n    \}", tanks, re.S)
if not fill or "for (FluidTank tank : inputs)" not in fill.group(0):
    fail("MachineTanks.fill reaches past the input tanks; a pipe could pour fuel into a refinery's output")
if len(drains) != 2 or any("for (FluidTank tank : outputs)" not in d for d in drains):
    fail("MachineTanks.drain reaches past the output tanks; a pipe could suck a refinery's oil back out")
caps = _src("registry/SpaceBlockEntities.java")
# The machines' own stores are handed over in register(), once per machine
# type, and onCapabilities runs it over them. Asking only whether the file
# names the capability passed with the machines' energy registration deleted,
# because the cable's own registration names Capabilities.EnergyStorage.BLOCK
# too (negtest_machines.sh, 17 October 2026), so the rule names the statements.
_cap_register = re.search(r"private static <T extends MachineBlockEntity> void register\("
                          r"RegisterCapabilitiesEvent event, BlockEntityType<T> type\) \{(.*?)\n    \}", caps, re.S)
_cap_event = re.search(r"public static void onCapabilities\(RegisterCapabilitiesEvent event\) \{(.*?)\n    \}",
                       caps, re.S)
for cap, store in (("Capabilities.EnergyStorage.BLOCK", "be.energy()"),
                   ("Capabilities.FluidHandler.BLOCK", "be.fluids()")):
    _statement = f"event.registerBlockEntity({cap}, type, (be, side) -> {store});"
    if not _cap_register or _statement not in " ".join(_cap_register.group(1).split()):
        fail(f"SpaceBlockEntities does not register {cap} for its machines ({_statement} is not in "
             f"register); other mods' cables and pipes could not connect")
if not _cap_event or "register(event, type.get());" not in " ".join(_cap_event.group(1).split()):
    fail("SpaceBlockEntities.onCapabilities no longer calls register(event, type.get()) for the machine "
         "types, so no machine offers its Capabilities.EnergyStorage.BLOCK or its tanks to anything")
cond = _src("machine/ConduitBlockEntity.java")
if ".produce(" in cond or re.search(r"\.fill\(\s*new FluidStack", cond):
    fail("a conduit creates energy or fluid; cables and pipes may only move what they are given, "
         "or a loop of them duplicates it")
for cap, holder in (("Capabilities.EnergyStorage.BLOCK, CABLE.get()", "cable"),
                    ("Capabilities.FluidHandler.BLOCK, PIPE.get()", "pipe")):
    if cap not in caps:
        fail(f"the {holder} does not expose its capability; nothing could feed it")
keys = _src("client/SpaceKeys.java")
if "JetRules.vertical(" not in keys:
    fail("the jet suit's client movement does not use JetRules.vertical; flight is untested arithmetic")
jev = _src("event/JetEvents.java")
if "JetRules.burn(" not in jev or "JetRules.flying(" not in jev:
    fail("JetEvents does not burn fuel and cancel falls through JetRules")
if "JetSuitItem.worn(player).isEmpty()" not in net:
    fail("the jet thrust packet is accepted without checking a jet suit is worn")
bm_dir = os.path.join(DATA, "neoforge/biome_modifier")
placed = set()
for f in os.listdir(bm_dir):
    feats = json.load(open(os.path.join(bm_dir, f))).get("features", [])
    placed.update([feats] if isinstance(feats, str) else feats)
for b in BLOCKS:
    if b.endswith("_ore") and f"{NS}:{b}" not in placed:
        fail(f"ore {b} is registered but no biome modifier places it; it would never generate")
    if b.endswith("_ore") and f"{NS}:{b}" not in PICKAXE:
        fail(f"ore {b} is in no mineable tag, so no pickaxe is right for it and mining it "
             f"drops nothing")
    if b.endswith("_ore"):
        loot = os.path.join(DATA, "loot_table/blocks", b + ".json")
        if os.path.isfile(loot) and "minecraft:fortune" not in open(loot).read():
            fail(f"ore {b} ignores Fortune")
for m in ("lunite", "frostite", "cryonite", "cindrium", "steel"):
    for need in (f"recipe/{m}_ingot_from_smelting.json", f"tags/item/ingots/{m}.json"):
        path = os.path.join(DATA if need.startswith("recipe") else os.path.join(RES, "data/c"),
                            need if need.startswith("recipe") else need.replace("tags/", "tags/", 1))
        if not os.path.isfile(path):
            fail(f"metal {m} is missing {need}; other mods' recipes or furnaces would not take it")
print("rocket             : server re-checks pilot, phase and route before moving anyone; client code isolated")

# ---------------------------------------------------------------------------
# 6. The Emerald Bastion
# ---------------------------------------------------------------------------
BIOME_DIR = os.path.join(DATA, "worldgen/biome")
bastion_biomes = [f for f in os.listdir(BIOME_DIR) if f.startswith("emerald_bastion")]
dim = json_files.get(os.path.join(DATA, "dimension/emerald_bastion.json"), {})
used = {b["biome"].split(":", 1)[1] + ".json" for b in
        dim.get("generator", {}).get("biome_source", {}).get("biomes", [])}
if used != set(bastion_biomes):
    fail(f"the Bastion's dimension uses {sorted(used)} but its biomes are {sorted(bastion_biomes)}")
for f in bastion_biomes:
    b = json_files[os.path.join(BIOME_DIR, f)]
    if b.get("spawners", {}).get("monster"):
        fail(f"{f} spawns monsters; the Emerald Bastion is a paradise world")
ns = json_files.get(os.path.join(DATA, "worldgen/noise_settings/emerald_bastion.json"), {})
if ns.get("default_block", {}).get("Name") != "minecraft:deepslate":
    fail("the Bastion's ground is not deepslate")
if ns.get("ore_veins_enabled"):
    fail("the Bastion has ore veins on; their filler is granite and tuff, not deepslate")
bastion_files = [p for p in json_files if "emerald_bastion" in os.path.basename(p)
                 or "/emerald_bastion/" in p.replace(os.sep, "/")]
for p in bastion_files:
    for s in strings(json_files[p]):
        if s in ("minecraft:stone", "minecraft:cobblestone", "minecraft:grass_block", "minecraft:dirt"):
            fail(f"{os.path.relpath(p, WORK)} places {s}; the Bastion has deepslate, moss and mud")
counts = {}
for p in bastion_files:
    m = re.search(r"emerald_bastion_ore_(\w+)\.json$", p)
    if m and "/placed_feature/" in p.replace(os.sep, "/"):
        for step in json_files[p]["placement"]:
            if step.get("type") == "minecraft:count":
                counts[m.group(1)] = step["count"]
if not counts or max(counts, key=counts.get) != "emerald":
    fail(f"emerald is not the Bastion's most common ore: {counts}")
cont = json_files.get(os.path.join(DATA, "worldgen/density_function/emerald_bastion/continents.json"), {})
try:
    add = cont["argument"]["argument1"]
    mul = cont["argument"]["argument2"]["argument1"]
    lowest = add - abs(mul)
    if lowest <= -0.19:
        fail(f"the Bastion's continentalness reaches {lowest:.2f}; below -0.19 is ocean")
except (KeyError, TypeError):
    fail("the Bastion's continents function is not add(a, mul(b, continents)); cannot prove no oceans")
if ns.get("noise_router", {}).get("continents") != f"{NS}:emerald_bastion/continents":
    fail("the Bastion's noise router still reads the overworld's continents, oceans included")

# Flat, with rare volcanic ranges. Erosion decides flat against mountainous, and
# the Bastion reads the overworld's through a spline. Its points all carry
# derivative 0, so between two points the value never leaves them: the points
# alone bound the whole curve. Plains are 0.05..0.45; above 0.45 vanilla
# shatters the ground and above 0.55 sinks it into swamp, so nothing may exceed
# 0.45. Peaks need -0.78. And the mountains must stay a minority of the input.
if ns.get("noise_router", {}).get("erosion") != f"{NS}:emerald_bastion/erosion":
    fail("the Bastion's noise router reads the overworld's erosion, so its biomes follow "
         "hills the terrain no longer has")
for _df in ("offset", "factor", "jaggedness"):
    _txt = json.dumps(json_files.get(os.path.join(DATA, f"worldgen/density_function/emerald_bastion/{_df}.json"), {}))
    if "minecraft:overworld/erosion" in _txt:
        fail(f"the Bastion's {_df} still reads the overworld's erosion, so its hills come back")
_ero = json_files.get(os.path.join(DATA, "worldgen/density_function/emerald_bastion/erosion.json"), {})
try:
    _spline = _ero["argument"]["spline"]
    assert _spline["coordinate"] == "minecraft:overworld/erosion"
    _pts = [(p["location"], p["value"], p["derivative"]) for p in _spline["points"]]
except (KeyError, TypeError, AssertionError):
    _pts = None
    fail("the Bastion's erosion is not a spline over the overworld's; cannot prove it is flat")
if _pts:
    if any(d != 0 for _, _, d in _pts):
        fail("the Bastion's erosion spline has a slope at a point, so it can overshoot its values")
    if any(b[1] < a[1] for a, b in zip(_pts, _pts[1:])):
        fail("the Bastion's erosion spline turns back on itself, so plains and peaks interleave")
    if max(v for _, v, _ in _pts) > 0.45:
        fail(f"the Bastion's erosion reaches {max(v for _, v, _ in _pts)}; above 0.45 vanilla "
             f"shatters and sinks the ground, which is not flat")
    if min(v for _, v, _ in _pts) > -0.78:
        fail("the Bastion's erosion never reaches peak values, so it has no mountains")
    _mountain = next((a for a, v, _ in reversed(_pts) if v <= -0.375), -1.0)
    if (_mountain + 1.0) / 2.0 > 0.3:
        fail(f"the Bastion's mountains take the overworld's erosion up to {_mountain}, over "
             f"30% of its range; the world is meant to be mostly flat")
_rule = json.dumps(ns.get("surface_rule", {}))
if f"{NS}:emerald_bastion_highlands" not in _rule or "minecraft:blackstone" not in _rule:
    fail("the Bastion's mountains are not volcanic: no blackstone rule for the highlands")
print(f"bastion terrain    : plains from the overworld's erosion above "
      f"{next(a for a, v, _ in _pts if v > -0.375) if _pts else '?'}, black volcanic ranges below it")
print(f"emerald bastion    : {len(bastion_biomes)} biomes with no monsters, deepslate, no stone, "
      f"emerald most common ({counts.get('emerald')} a chunk), no oceans")
print()

# ---------------------------------------------------------------------------
# 7. Mobs
# ---------------------------------------------------------------------------
_mob_ids = [m for m in MOB_CONST.values()]
if sorted(MOBS) != sorted(_mob_ids):
    fail(f"registered mobs {sorted(MOBS)} are not MobRules's {sorted(_mob_ids)}")
_mobs_src = strip_comments(open(os.path.join(SRC, "registry/SpaceMobs.java"), encoding="utf-8").read())
_rend_src = strip_comments(open(os.path.join(SRC, "client/mob/MobRenderers.java"), encoding="utf-8").read())
_const_of = {v: k for k, v in MOB_CONST.items()}
_class_of = dict((MOB_CONST.get(c, c), k) for c, k in re.findall(r'mob\(MobRules\.([A-Z_]+), (\w+)::new\)', ent_src))
_rigged = re.findall(r'"([a-z_]+)"', re.search(r'RIGGED = \{(.*?)\}', _rend_src, re.S).group(1))
_anim_dir = os.path.join(ASSETS, "neoforge/animations/entity")
for mob in MOBS:
    c = _const_of[mob]
    if mob not in EGGS:
        fail(f"mob {mob} has no spawn egg")
    if not os.path.isfile(os.path.join(DATA, "loot_table/entities", mob + ".json")):
        fail(f"mob {mob} has no loot table")
    if not re.search(rf"SpaceEntities\.{c}\.get\(\),[^;]*MobRules\.{c}\)", _mobs_src):
        fail(f"mob {mob} gets no attributes from MobRules; it would crash on spawn")
    if not re.search(rf"event\.register\(SpaceEntities\.{c}\.get\(\), SpawnPlacementTypes", _mobs_src):
        fail(f"mob {mob} has no spawn placement")
    if f"SpaceEntities.{c}.get()" not in _rend_src:
        fail(f"mob {mob} has no renderer; the client would crash when it appears")
    cls = open(os.path.join(SRC, "entity/mob", _class_of[mob] + ".java"), encoding="utf-8").read()
    # A native of a world that is not the Nether never zombifies, and it takes
    # three statements to say so: the constructor's, the load's (the piglin's
    # and the hoglin's own readAdditionalSaveData read the saved flag back over
    # it, false for anything summoned or put down by a structure), and an
    # isConverting() that answers no whatever the flag says. The cinderlings
    # had only the first, so one summoned off Cinderveil turned (17 Oct 2026).
    if re.search(r"extends (Hoglin|Piglin|PiglinBrute)\b", cls):
        _flat_cls = re.sub(r"\s+", " ", cls)
        _name = _class_of[mob]
        if not re.search(r"public " + _name + r"\(EntityType<[^)]*\) \{ super\(type, level\); "
                         r"setImmuneToZombification\(true\);", _flat_cls):
            fail(f"{_name} would zombify outside the Nether, on its own world: its constructor does not "
                 f"call setImmuneToZombification(true)")
        if ("public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) { "
                "super.readAdditionalSaveData(tag); setImmuneToZombification(true); }") not in _flat_cls:
            fail(f"{_name} reads its immunity back from the save, false for one summoned or put down by a "
                 f"structure, so it would zombify outside the Nether")
        if "public boolean isConverting() { return false; }" not in _flat_cls:
            fail(f"{_name} can still begin to convert: isConverting() does not answer false, so a lost "
                 f"immunity flag turns it")
geo_dir = os.path.join(ASSETS, "geometry")
for mob in _rigged + ["cinderling"]:
    gp = os.path.join(geo_dir, mob + ".json")
    if not os.path.isfile(gp):
        fail(f"mob {mob} has no geometry")
        continue
    geo = json.load(open(gp))
    paths = set(geo["parts"])
    names = {p.split("/")[-1] for p in paths}
    for path, part in geo["parts"].items():
        if part["parent"] and part["parent"] not in paths:
            fail(f"{mob} geometry: {path}'s parent {part['parent']} is not a part")
    rig = geo.get("rig", {})
    for bone in [rig.get("head")] + [s["bone"] for s in rig.get("swing", [])]:
        if bone and bone not in paths:
            fail(f"{mob} rig moves {bone}, which is not a part path")
    anims = list(rig.get("states", {}).items()) + ([("walk", rig["walk"]["animation"])] if "walk" in rig else [])
    for state, aid in anims:
        ap = os.path.join(_anim_dir, aid.split(":", 1)[1] + ".json")
        if not os.path.isfile(ap):
            fail(f"{mob} rig plays {aid}, which is not an animation file")
            continue
        for ch in json.load(open(ap))["animations"]:
            if ch["bone"] not in names:
                fail(f"{aid} animates bone {ch['bone']}, which {mob} does not have")
        if state != "walk":
            cls = open(os.path.join(SRC, "entity/mob", _class_of[mob] + ".java"), encoding="utf-8").read()
            if f'"{state}".equals(state)' not in cls or "implements Animated" not in cls and "Animated" not in cls:
                fail(f"{_class_of[mob]} never gives the rig its {state} animation state")
for bp in [p for p in json_files if "/worldgen/biome/" in p.replace(os.sep, "/")]:
    for cat, lst in json_files[bp].get("spawners", {}).items():
        for sp in lst:
            t = sp["type"]
            if t.startswith(NS + ":") and t.split(":", 1)[1] not in MOBS:
                fail(f"{os.path.relpath(bp, WORK)} spawns {t}, which is not a mob")
            if t.startswith("stellaris:"):
                fail(f"{os.path.relpath(bp, WORK)} still spawns {t}")
moon = open(os.path.join(SRC, "entity/mob/Moonfolk.java"), encoding="utf-8").read()
if "spawnGolemIfNeeded" not in moon or "MOONFOLK.get().create" not in moon:
    fail("moonfolk must not summon iron golems, and their children must be moonfolk")

# Skies (S7): every space dimension type names our effects, each has a sky file the client registers.
_sky_src = strip_comments(open(os.path.join(SRC, "client/sky/SpaceSkies.java"), encoding="utf-8").read())
_SKY_WORLDS = re.findall(r'"([a-z_]+)"', re.search(r"WORLDS = \{(.*?)\}", _sky_src, re.S).group(1))
for dp in sorted(p for p in json_files if "/dimension_type/" in p.replace(os.sep, "/")):
    eff = json_files[dp].get("effects", "")
    world = os.path.basename(dp)[:-5]
    if eff.startswith(NS + ":"):
        if eff.split(":", 1)[1] not in _SKY_WORLDS:
            fail(f"{os.path.relpath(dp, WORK)} names effects {eff}, which SpaceSkies does not register")
    elif world != "emerald_bastion":
        fail(f"{world} still uses {eff}'s sky; Stellaris gave it its own")
for w in _SKY_WORLDS:
    sp = os.path.join(ASSETS, "skies", w + ".json")
    if not os.path.isfile(sp):
        fail(f"SpaceSkies registers {w}, which has no sky file")
        continue
    sky = json.load(open(sp))
    bodies = [sky["sun"]] + ([sky["moon"]] if "moon" in sky else []) + sky["objects"]
    for b in bodies:
        ns, path = b["texture"].split(":", 1)
        if ns == NS and not os.path.isfile(os.path.join(ASSETS, path)):
            fail(f"the {w} sky draws {b['texture']}, which is missing")
        if not (0 < b["size"] and 0 < b["height"]):
            fail(f"the {w} sky has a body of size {b['size']} at {b['height']}")
    if not 0 < sky["stars"]["count"] <= 40000:
        fail(f"the {w} sky has {sky['stars']['count']} stars; the renderer builds them all into one buffer")
_cp = os.environ.get("ELYSIUM_MC_LIBS", "/tmp/mc") + "/cp.txt"
_classes = os.environ.get("ELYSIUM_OUT", "/tmp/elysium-classes") + "/elysium-space"
_baked = "not baked"
if not os.path.isfile(_cp):
    # Failures, not a status line: until 15 October either input missing
    # printed "geometry skipped" and the module passed with not one mob baked.
    fail(f"mob geometry cannot be baked: there is no classpath file at {_cp} (set "
         f"ELYSIUM_MC_LIBS to the folder holding cp.txt, or run python3 tools/setup_env.py)")
elif not os.path.isdir(_classes):
    fail(f"mob geometry cannot be baked: the space module's compiled classes are not at "
         f"{_classes} (run ./compile.sh first, or set ELYSIUM_OUT to where it wrote them)")
else:
    with tempfile.TemporaryDirectory() as out:
        cp = os.pathsep.join([open(_cp).read().strip(), _classes, os.path.join(WORK, "src/main/resources")])
        r = subprocess.run(["javac", "-nowarn", "-cp", cp, "-d", out, os.path.join(WORK, "tools/GeometryCheck.java")],
                           capture_output=True, text=True)
        if r.returncode != 0:
            fail("GeometryCheck does not compile: " + r.stderr[-500:])
        else:
            r = subprocess.run(["java", "-cp", cp + os.pathsep + out, "com.elysium.space.tools.GeometryCheck"]
                               + _rigged + ["piglin:cinderling"] + ["sky:" + w for w in _SKY_WORLDS],
                               capture_output=True, text=True)
            if r.returncode != 0:
                fail("mob geometry does not bake:\n      " + "\n      ".join(
                    l for l in r.stdout.splitlines() if l.startswith("FAIL")))
            else:
                _baked = f"{len(_rigged) + 1} baked and {len(_SKY_WORLDS)} skies read with Minecraft's classes"
print(f"mobs               : {len(MOBS)} with stats, attributes, eggs, loot, renderers; geometry {_baked}")
print()

# ---------------------------------------------------------------------------
# 8. Space stations
# ---------------------------------------------------------------------------
sys.path.insert(0, os.path.join(WORK, "tools"))
import nbt as _nbt  # noqa: E402
if [i for i, *_ in STATION_SIZES] != ["small", "medium", "big"]:
    fail(f"expected Stellaris's three stations, found {STATION_SIZES}")
_gen = open(os.path.join(WORK, "tools/gen_space.py"), encoding="utf-8").read()
_gen_costs = re.findall(r'\("([a-z]+)", (\d+), (\d+)\)', re.search(r"STATIONS = \((.*?)\)\s+#", _gen, re.S).group(1))
if [(i, int(a), int(b)) for i, a, b in _gen_costs] != [(i, st, lu) for i, st, lu, *_ in STATION_SIZES]:
    fail("gen_space.STATIONS and StationRules disagree on what stations cost")
for sid, steel, lunite, sx, sy, sz in STATION_SIZES:
    tp = os.path.join(DATA, "structure", f"space_station_{sid}.nbt")
    if not os.path.isfile(tp):
        fail(f"the {sid} station has no template")
        continue
    _, root = _nbt.load(tp)
    c = root.value
    size = [t.value for t in c["size"].value]
    if size != [sx, sy, sz]:
        fail(f"the {sid} station template is {size}, StationRules says {[sx, sy, sz]}")
    names = [p.value["Name"].value for p in c["palette"].value]
    blocks = [([t.value for t in b.value["pos"].value], names[b.value["state"].value]) for b in c["blocks"].value]
    pads = {(p[0], p[2]): p[1] for p, n in blocks if n == f"{NS}:launch_pad"}
    if not pads:
        fail(f"the {sid} station has no launch pad to land on")
    roof = [p for p, n in blocks if (p[0], p[2]) in pads and p[1] > pads[(p[0], p[2])]
            and n not in ("minecraft:air", "minecraft:structure_void")]
    if roof:
        fail(f"the {sid} station covers its launch pad at {roof[0]}; a rocket could not land")
    if f"item.{NS}.{sid}_station_blueprint" not in lang:
        fail(f"the {sid} station blueprint has no name")
    if not os.path.isfile(os.path.join(DATA, "recipe", f"{sid}_station_blueprint.json")):
        fail(f"the {sid} station blueprint has no recipe")
_st_src = strip_comments(open(os.path.join(SRC, "station/SpaceStations.java"), encoding="utf-8").read())
for res in re.search(r"enum Result \{(.*?)\}", _st_src).group(1).replace(" ", "").split(","):
    if f"{NS}.station.{res.lower()}" not in lang:
        fail(f"the station result {res} has no message")
if "SpaceStations.arrival(pilot, target)" not in rocket:
    fail("rockets to High Orbit do not land on the pilot's station")
if not re.search(r"MISSING_MATERIALS;[\s\S]*placeInWorld[\s\S]*take\(player, STEEL", _st_src):
    fail("a station must be paid for after the materials are checked and the template found, not before")
for t in ("ingots/steel", "ingots/lunite"):
    if not os.path.isfile(os.path.join(RES_ROOT := os.path.join(WORK, "src/main/resources"), "data/c/tags/item", t + ".json")):
        fail(f"stations are paid in #c:{t}, which has no tag file")
print(f"stations           : {len(STATION_SIZES)} sizes, templates match their rules, pads open to the sky; "
      "rockets land on the pilot's station")
print()

# Machine screens: the block writes the opening packet in the order the menu reads it.
_mblock = strip_comments(open(os.path.join(SRC, "machine/MachineBlock.java"), encoding="utf-8").read())
_mmenu = strip_comments(open(os.path.join(SRC, "machine/MachineMenu.java"), encoding="utf-8").read())
_writes = re.findall(r"buf\.write(\w+)\(", _mblock)
_reads = re.findall(r"buf\.read(\w+)\(", _mmenu)
if not _writes or _writes != _reads:
    fail(f"the machine screen's packet is written as {_writes} but read as {_reads}")
if "SpaceMenus.MACHINE.get(), MachineScreen::new" not in strip_comments(open(os.path.join(SRC, "client/SpaceClient.java")).read()):
    fail("the machine menu has no screen registered; opening a machine would disconnect the client")
if "SpaceMenus.TYPES.register(modEventBus)" not in strip_comments(open(os.path.join(SRC, "ElysiumSpace.java")).read()):
    fail("the machine menu type is never registered")

# ---------------------------------------------------------------------------
# 9. The reskin (S8): no Stellaris texture ships unchanged
# ---------------------------------------------------------------------------
import hashlib as _hl
_stl_tex = os.path.join(os.environ.get("STELLARIS", "/tmp/ref/stellaris"), "assets/stellaris/textures")
# Art the reskin keeps by design: Earth, seen from the Pale Moon and High Orbit, is the Overworld.
_KEEP = {"environment/overworld.png"}
if os.path.isdir(_stl_tex):
    _theirs = {}
    for d, _, fs in os.walk(_stl_tex):
        for f in fs:
            if f.endswith(".png"):
                _theirs[_hl.md5(open(os.path.join(d, f), "rb").read()).hexdigest()] = os.path.relpath(os.path.join(d, f), _stl_tex).replace(os.sep, "/")
    _ours_tex = os.path.join(ASSETS, "textures")
    _copies = 0
    for d, _, fs in os.walk(_ours_tex):
        for f in fs:
            p = os.path.join(d, f)
            # Forward slashes, as _KEEP is written: on Windows relpath gives
            # backslashes and the kept Earth would be reported as a copy.
            rel = os.path.relpath(p, _ours_tex).replace(os.sep, "/")
            if f.endswith(".png") and rel not in _KEEP:
                digest = _hl.md5(open(p, "rb").read()).hexdigest()
                if digest in _theirs:
                    _copies += 1
                    fail(f"textures/{rel} is Stellaris's {_theirs[digest]} unchanged; the reskin must redraw it")
    _reskin_note = f"{len(_theirs)} Stellaris textures compared, none shipped unchanged"
else:
    _reskin_note = "skipped (no Stellaris textures to compare against)"
_pm = open(os.path.join(WORK, "tools/port_mobs.py"), encoding="utf-8").read()
_homes = dict(re.findall(r'"([a-z_]+)": "([a-z_]+)"', re.search(r"HOMES = \{(.*?)\}", _pm, re.S).group(1)))
_rule_homes = dict(re.findall(r'new Mob\("([a-z_]+)", "[a-z_]+", "([a-z_]+)"', mobrules_src))
if _homes != _rule_homes:
    fail(f"port_mobs.HOMES {_homes} and MobRules's homes {_rule_homes} disagree; a mob would wear another world's palette")
print(f"reskin             : {_reskin_note}; mobs wear their home world's palette")
print(f"machine screens    : one menu for every machine, packet {'/'.join(_writes)} read in order")

# ---------------------------------------------------------------------------
# 9b. Generated files are what the generators write now
# ---------------------------------------------------------------------------
# On 25 September the generators wrote a lang file the game had outgrown: the
# weather names, destinations.burn/nowhere and freight.run the Java reads were
# missing, and four keys nothing read any more were still written. The shipped
# file had been fixed by hand, and nothing compared the two, so the next run of
# the generators would have put the stale file back. So: the four generators
# are run, in the order they are meant to be run (gen_waygate and gen_freight
# merge into the lang file and the tags gen_space wrote), into a scratch tree,
# and every file they write must be the shipped one. PNGs are compared as
# pixels and the waygate template as decoded NBT, since Pillow and zlib
# versions change the bytes without changing the content.
sys.path.insert(0, os.path.join(os.path.dirname(WORK), "tools"))
import samefile  # noqa: E402
import mcpath  # noqa: E402

SPACE_GENERATORS = ["tools/gen_space.py", "tools/gen_waygate.py",
                    "tools/gen_waygate_structure.py", "tools/gen_freight.py"]
_stellaris = os.environ.get("STELLARIS", "/tmp/ref/stellaris")
_mc_jar = os.environ.get("MC_JAR")
if not _mc_jar:
    for _candidate in ("/tmp/mc/mapped-at.jar", "/tmp/mc/libs/vanilla-assets.jar",
                       os.path.join(mcpath.libs(), "libs/vanilla-assets.jar")):
        if os.path.isfile(_candidate):
            _mc_jar = _candidate
            break


def _plain(tag):
    """An NBT tag as plain Python, so two templates compare by content."""
    if tag.type == _nbt.COMPOUND:
        return {key: _plain(value) for key, value in tag.value.items()}
    if tag.type == _nbt.LIST:
        return (tag.elem, [_plain(value) for value in tag.value])
    return (tag.type, tag.value)


def _same_generated(made, kept):
    if made.lower().endswith(".nbt"):
        try:
            a_name, a_root = _nbt.load(made)
            b_name, b_root = _nbt.load(kept)
        except Exception:
            return False
        return a_name == b_name and _plain(a_root) == _plain(b_root)
    return samefile.same(made, kept)


for _relative in SPACE_GENERATORS:
    if not os.path.isfile(os.path.join(WORK, _relative)):
        fail(f"there is no {_relative}, so the files it writes are kept by hand")
if not os.path.isdir(os.path.join(_stellaris, "assets/stellaris")):
    # Failures, not status lines: until 15 October either input missing printed
    # "generated : skipped" and the module passed with not one file rebuilt.
    _generated_note = "not rebuilt"
    fail(f"the generated files cannot be rebuilt to compare with the shipped ones: there is "
         f"no Stellaris data at {_stellaris} (set STELLARIS to the unpacked Stellaris "
         f"1.4.25 jar, or run python3 tools/setup_env.py and use the STELLARIS it exports)")
elif not _mc_jar or not os.path.isfile(_mc_jar):
    _generated_note = "not rebuilt"
    fail("the generated files cannot be rebuilt to compare with the shipped ones: no game "
         "jar here (MC_JAR is " + (f"{_mc_jar}, which is not a file" if _mc_jar else
                                   "not set, and none of the usual places has one")
         + "); set MC_JAR to vanilla-assets.jar or the 1.21.1 client jar, or run "
         "python3 tools/setup_env.py and use the MC_JAR it exports")
elif all(os.path.isfile(os.path.join(WORK, _relative)) for _relative in SPACE_GENERATORS):
    _generated_note = "not rebuilt"
    with tempfile.TemporaryDirectory() as _scratch:
        _ran_all = True
        for _relative in SPACE_GENERATORS:
            _made = subprocess.run([sys.executable, os.path.join(WORK, _relative)],
                                   capture_output=True, text=True, cwd=WORK,
                                   env=dict(os.environ, ELYSIUM_SPACE_OUT=_scratch,
                                            STELLARIS=_stellaris, MC_JAR=_mc_jar))
            if _made.returncode != 0:
                _ran_all = False
                fail(f"{_relative} will not run, so nothing it writes can be rebuilt:\n"
                     + (_made.stdout + _made.stderr).strip()[:800])
        if _ran_all:
            _fresh = os.path.join(_scratch, "src/main/resources")
            _drifted = []
            _made_count = 0
            for _dirpath, _, _filenames in os.walk(_fresh):
                for _name in _filenames:
                    _made_count += 1
                    _made_path = os.path.join(_dirpath, _name)
                    _rel = os.path.relpath(_made_path, _fresh).replace(os.sep, "/")
                    _kept = os.path.join(RES, _rel)
                    if not os.path.isfile(_kept):
                        _drifted.append(_rel + " (missing)")
                    elif not _same_generated(_made_path, _kept):
                        _drifted.append(_rel)
            if _drifted:
                fail(f"{len(_drifted)} generated file(s) differ from what the generators write "
                     f"now; run tools/gen_space.py, tools/gen_waygate.py, "
                     f"tools/gen_waygate_structure.py then tools/gen_freight.py rather than "
                     f"editing them: " + ", ".join(sorted(_drifted)[:6])
                     + (" ..." if len(_drifted) > 6 else ""))
            _generated_note = (f"{_made_count} file(s) rebuilt from the four generators, "
                               f"each the same as the one shipped")
else:
    _generated_note = "not rebuilt (a generator is missing)"
print(f"generated          : {_generated_note}")

# ---------------------------------------------------------------------------
# What standing each world takes (SPEC_CLONE P5.1)
# ---------------------------------------------------------------------------
#
# The worlds are already ordered by how far they are from home, and that order
# is the difficulty order: the fuel to reach Frostfall is the reason nobody
# arrives there early. So a world's tier has to agree with its distance, or a
# player would be sent somewhere that drops loot for a journey they never made.
_tiers_path = os.path.join(SRC, "world/SpaceTiers.java")
_route_path = os.path.join(SRC, "world/SpaceRoute.java")
if not os.path.isfile(_tiers_path):
    fail("SpaceTiers is gone; these worlds would all count as the Frontier and a "
         "Cinderveil chest would pay like a village one")
else:
    _tiers_src = open(_tiers_path, encoding="utf-8").read()
    _route_src = open(_route_path, encoding="utf-8").read()
    _declared = [(m[0], int(m[1] != "" and 0) if False else m[1])
                 for m in re.findall(r'm\.put\("([a-z_]+)",\s*ElysiumWorldTier\.([A-Z]+)\)', _tiers_src)]
    _order = ["FRONTIER", "ASCENT", "SUMMIT", "PINNACLE", "APOTHEOSIS"]
    _distances = [(m[0], float(m[1].replace("_", "")))
                  for m in re.findall(r'd\.put\("([a-z_]+)",\s*([0-9_.]+)D\)', _route_src)]
    _worlds = [name for name, _ in _distances if name != "overworld"]
    _named = [name for name, _ in _declared]
    if sorted(_named) != sorted(_worlds):
        fail(f"the worlds with a tier and the worlds on the route disagree: "
             f"{sorted(set(_named) ^ set(_worlds))}")
    if "overworld" in _named:
        fail("the Overworld has a world tier; being at home is not an achievement")
    _by_distance = [name for name, _ in sorted(_distances, key=lambda row: row[1]) if name != "overworld"]
    _tier_of = dict(_declared)
    _last = -1
    for _name in _by_distance:
        _rank = _order.index(_tier_of[_name]) if _tier_of.get(_name) in _order else -1
        if _rank < 0:
            fail(f"{_name} stands at a tier that is not one of the named ones")
        elif _rank < _last:
            fail(f"{_name} is further from home than the world before it and stands at a "
                 f"lower tier, so the journey would buy a player less than the shorter one")
        else:
            _last = _rank
    if "SpaceTiers.declare()" not in open(os.path.join(SRC, "ElysiumSpace.java"), encoding="utf-8").read():
        fail("SpaceTiers.declare() is never called, so the library would never hear what "
             "these worlds are worth")
    print(f"world tiers        : {len(_named)} worlds, each at or above the one closer to home")

# ---------------------------------------------------------------------------
# The waygate
# ---------------------------------------------------------------------------
#
# Five worlds and a rocket is the right weight for arriving somewhere the first
# time and the wrong weight for the ninetieth. The gate is the answer, and
# almost everything worth checking about it is a thing it must NOT do: charge,
# wear out, or send somebody somewhere that is not there any more.

_gate_src = os.path.join(SRC, "gate")
if not os.path.isdir(_gate_src):
    fail("the waygate is gone; every trip between worlds is a rocket again")
else:
    _ring = open(os.path.join(_gate_src, "WaygateRing.java"), encoding="utf-8").read()
    _anchor = open(os.path.join(_gate_src, "WaygateAnchorBlock.java"),
                   encoding="utf-8").read()
    _travel = open(os.path.join(_gate_src, "WaygateTravel.java"), encoding="utf-8").read()
    _network = open(os.path.join(_gate_src, "WaygateNetwork.java"),
                    encoding="utf-8").read()

    # The shape is the rules', not the block's.
    for _shape in ("GateRules.frame()", "GateRules.opening()"):
        if _shape not in _ring:
            fail(f"the ring is read without {_shape}, so its shape is written twice and "
                 f"the harness proves nothing about the one the game uses")
    if re.search(r"offset\(\s*-?[0-9]+\s*,\s*-?[0-9]+\s*,\s*-?[0-9]+\s*\)", _ring):
        fail("WaygateRing has a hard-coded offset; every position comes from GateRules "
             "or the two halves of the shape can disagree")

    # It costs nothing to use. This is the claim the whole feature rests on.
    for _cost in ("shrink(", "getEnergy", "extractItem", "consume",
                  "experienceLevel", "giveExperience"):
        if _cost in _anchor or _cost in _travel:
            fail(f"the waygate charges something ({_cost}); it is paid for once, in the "
                 f"building of it, and a shortcut a player has to think about is a "
                 f"shortcut they stop using")

    # Registering and forgetting.
    if "WaygateNetwork.of(server).record(" not in _anchor:
        fail("a finished gate never joins the network, so nothing can ever travel to it")
    if "forget(" not in _anchor:
        fail("a broken anchor stays on the network, so its address is a place a "
             "traveller arrives to find nothing")
    _remove = body_of(_anchor, "protected void onRemove")
    if "state.is(replacement.getBlock())" not in _remove:
        fail("onRemove does not guard on the block actually changing, so a state update "
             "takes a working gate off the network")

    # Travel re-reads everything, because a packet is a request and not a fact.
    _send = squashed(body_of(_travel, "public static Component send"))
    if "BlockPos standing = nearestAnchor(from, traveller.blockPosition());" not in _send:
        fail("travel does not check that the traveller is still at a gate, so a kept "
             "packet is a teleport from anywhere")
    # The anchor is clicked from the game's block reach (4.5 blocks, 5 in
    # creative), so the traveller may be that far away when they choose. The
    # first version searched two blocks and told a player who clicked from
    # three that they were no longer standing at a waygate.
    _near = body_of(_travel, "private static BlockPos nearestAnchor")
    _reach = re.search(r"public static final int REACH = (\d+);", _travel)
    if not _reach:
        fail("WaygateTravel has no REACH, so how far a traveller may stand is a number "
             "buried in a loop")
    else:
        if int(_reach.group(1)) < 5:
            fail(f"WaygateTravel.REACH is {_reach.group(1)}, less than the block reach the "
                 f"anchor is clicked from (4.5, 5 in creative), so a player who opens the "
                 f"screen from across the room is refused as no longer at the gate")
        if "REACH" not in _near or re.search(r"offset\(\s*-?\d", _near):
            fail("nearestAnchor searches a hard-coded distance rather than REACH, so the "
                 "stated reach and the checked one can disagree")
    # Both ends, by name: a count of the call is satisfied by two reads of the
    # same ring, or by one read whose answer nobody looks at.
    if 'if (!WaygateRing.read(from, standing).complete()) { ' \
            'return Component.translatable("elysiumspace.waygate.unbuilt"); }' not in _send:
        fail("travel does not check the ring the traveller is standing in, so a gate "
             "with its frame half broken still sends")
    if 'if (!WaygateRing.read(there, anchor).complete()) { ' \
            'network.forget(going.dimension(), anchor); ' \
            'return Component.translatable("elysiumspace.waygate.broken_there", ' \
            'going.shown()); }' not in _send:
        fail("travel checks one end of the journey and not the other, so a gate whose "
             "far ring was broken drops a player into the ground")
    if "addRegionTicket" not in _send:
        fail("travel does not hold the far chunk open, so arriving on a world nobody "
             "has visited for a month lands inside unloaded terrain")
    if "resetFallDistance" not in _send:
        fail("a traveller keeps their fall distance through a gate, so stepping through "
             "a doorway can kill them")
    if "network.forget(going.dimension(), anchor);" not in _send:
        fail("a gate found broken at the far end is left on the network to be found "
             "broken again tomorrow")

    # One network, on the overworld, or gates only work one way.
    if "Level.OVERWORLD" not in _network:
        fail("the network is per-dimension, so a gate on Cinderveil cannot see one in "
             "the overworld and every pair works in one direction only")
    if "extends SavedData" not in _network:
        fail("the network is not saved, so every gate is forgotten on restart")

    # The two blocks, complete.
    for _block in ("waygate_frame", "waygate_anchor"):
        for _what, _path in (
                ("blockstate", "blockstates/%s.json" % _block),
                ("model", "models/block/%s.json" % _block),
                ("item model", "models/item/%s.json" % _block),
                ("texture", "textures/block/%s.png" % _block)):
            if not os.path.isfile(os.path.join(ASSETS, _path)):
                fail(f"{_block} has no {_what}")
        if not os.path.isfile(os.path.join(DATA, "loot_table/blocks/%s.json" % _block)):
            fail(f"{_block} has no loot table, so breaking a gate destroys it")
        _recipe = os.path.join(DATA, "recipe/%s.json" % _block)
        if not os.path.isfile(_recipe):
            fail(f"{_block} cannot be made, so the gate is creative-only")
        else:
            _named = json.dumps(json.load(open(_recipe, encoding="utf-8")))
            for _foreign in re.findall(r'"([a-z0-9_]+):[a-z0-9_/]+"', _named):
                if _foreign not in ("minecraft", NS):
                    fail(f"{_block}'s recipe needs {_foreign}; this module is a world, "
                         f"not a dependant, and a gate must be buildable in a pack that "
                         f"took the content module out")
        if f"block.{NS}.{_block}" not in lang:
            fail(f"{_block} has no name")

    # Both halves of the packet, and the screen that uses them.
    _payloads = open(os.path.join(SRC, "network/SpacePayloads.java"),
                     encoding="utf-8").read()
    _netjava = open(os.path.join(SRC, "network/SpaceNetwork.java"),
                    encoding="utf-8").read()
    for _packet in ("OpenWaygates", "ChooseWaygate"):
        if _packet not in _payloads:
            fail(f"the {_packet} packet does not exist")
        if _packet not in _netjava:
            fail(f"the {_packet} packet is declared and never registered, so it is "
                 f"dropped on arrival")
    if not os.path.isfile(os.path.join(SRC, "client/WaygateScreen.java")):
        fail("there is no screen to choose a gate from")

    _size = re.search(r"SIZE = (\d+)", open(GATES, encoding="utf-8").read()).group(1)
    print(f"the waygate        : a ring of {_size}, one network on the overworld, "
          f"both ends re-read, and nothing to pay")

    # -----------------------------------------------------------------------
    # Freight: the rockets as a logistics system
    # -----------------------------------------------------------------------
    #
    # One rule matters more than the rest and everything here serves it:
    # cargo is never destroyed. A logistics system that eats a shipment when a
    # chest fills, a chunk unloads or a player mines the far end is a system
    # nobody will put anything they care about into, and then it is not a
    # system, it is a curiosity.

    def _src(*parts):
        """One of this module's source files, or "" when it is not there."""
        path = os.path.join(*parts)
        return open(path, encoding="utf-8").read() if os.path.isfile(path) else ""

    _freight_net = _src(SRC, "freight/FreightNetwork.java")
    _terminal = _src(SRC, "freight/FreightTerminalBlockEntity.java")
    _terminal_block = _src(SRC, "freight/FreightTerminalBlock.java")
    _dispatch = _src(SRC, "freight/FreightDispatch.java")
    for _file, _name in ((_freight_net, "FreightNetwork"),
                         (_terminal, "FreightTerminalBlockEntity"),
                         (_terminal_block, "FreightTerminalBlock"),
                         (_dispatch, "FreightDispatch")):
        if not _file:
            fail(f"{_name} is gone; the rockets have no logistics system behind them")

    if "extends SavedData" not in _freight_net:
        fail("freight in flight is not saved, so every shipment in the air is lost on "
             "restart")
    if "Level.OVERWORLD" not in body_of(_freight_net, "public static FreightNetwork of("):
        fail("the freight network is kept per world, so a terminal on Cinderveil "
             "cannot be sent to from the overworld, which is the only thing freight "
             "is for")
    for _half in ("private static FreightNetwork load(", "public CompoundTag save("):
        if "RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, lookup);" \
                not in body_of(_freight_net, _half):
            fail(f"cargo is read or written with plain NBT ops in {_half.split()[-1]}, "
                 f"so an enchanted tool in a shipment loses its components and lands "
                 f"as a stick")

    _land = squashed(body_of(_dispatch, "private static void land("))
    if not _land:
        fail("nothing lands a shipment, so everything sent is in the air forever")
    if "level.getChunk(at);" not in _land:
        fail("a landing needs somebody standing there, so a supply line only runs "
             "while you watch it")
    # Both of the ways a destination can fail to exist: a world that is no
    # longer on this server, and a block somebody mined. One branch sending
    # cargo home and the other quietly dropping it is the bug this counts.
    if _land.count("returned(now)") < 2:
        fail("a shipment addressed to a terminal that is gone is not sent home, so "
             "mining the far end of a supply line destroys what is in the air")
    if "if (!over.isEmpty())" not in _land \
            or "network.replace(shipment, new FreightNetwork.Shipment(shipment.from(), " \
               "shipment.to(), over, now - shipment.flight() + FreightRules.HOLD_TICKS, " \
               "shipment.flight()));" not in _land:
        fail("a full destination does not hold the rocket, so cargo is dropped or "
             "deleted when a terminal fills")
    _tick = body_of(_dispatch, "public static void onTick(")
    if "Level.OVERWORLD" not in _tick:
        fail("the landing sweep runs in every world, so a shipment lands once per "
             "loaded dimension")

    _launch = squashed(body_of(_dispatch, "public static Component launch("))
    for _needed, _why in (
            ("int need = FreightRules.fuelFor(route, terminal.stacksAboard());",
             "a run is not priced, so distance costs nothing"),
            ("int flight = FreightRules.flightTicks( SpaceRoute.gap(here.world(), "
             "there.world()));",
             "a run takes no time, so distance is free in the other direction too"),
            ("network.inFlightFrom(here) >= FreightRules.IN_FLIGHT",
             "a terminal can have any number of rockets in the air, so the queue "
             "that makes distance matter does not exist"),
            ("terminal.fuelTank().drain(need, IFluidHandler.FluidAction.EXECUTE);",
             "the fuel is checked and never spent"),
            ("terminal.unload()", "the cargo is never taken out of the terminal, so "
                                  "launching duplicates it")):
        if _needed not in _launch:
            fail(f"launching does not use {_needed}: {_why}")
    if "int route = SpaceRoute.fuelFor(here.world(), there.world(), " \
            "SpaceRoute.Fuel.FUEL);" not in _launch:
        fail("freight prices itself instead of using the route table the rockets "
             "already fly on, so the two disagree about how far away a world is")

    if "Containers.dropContents" not in _terminal_block:
        fail("breaking a terminal destroys what is inside it")
    if "getAnalogOutputSignal" not in _terminal_block:
        fail("a comparator cannot read a terminal, so a supply line cannot be built "
             "out of redstone")
    if "implements WorldlyContainer" not in _terminal:
        fail("a hopper cannot fill a terminal, so the logistics system cannot be "
             "automated, which is the only reason to have one")
    if "ElysiumChests.threeRows" not in _terminal:
        fail("a terminal opens something other than the library's chest screen, so a player "
             "has to learn a new window for a box, or gets the game's grey one")

    _caps = _src(SRC, "registry/SpaceBlockEntities.java")
    for _cap in ("Capabilities.ItemHandler.BLOCK, FREIGHT_TERMINAL.get()",
                 "Capabilities.FluidHandler.BLOCK, FREIGHT_TERMINAL.get()"):
        if _cap not in _caps:
            fail(f"the terminal does not expose {_cap.split(',')[0].split('.')[1]}, "
                 f"so other mods' pipes cannot reach it")

    for _packet in ("OpenFreight", "ChooseFreight"):
        if f"record {_packet}(" not in _payloads:
            fail(f"the {_packet} packet does not exist")
        # The exact type, because a packet name is a prefix of every longer
        # name and a rule that matched one of those would pass on a typo.
        if f"SpacePayloads.{_packet}.TYPE" not in _netjava:
            fail(f"the {_packet} packet is declared and never registered, so it is "
                 f"dropped on arrival")
    if "distSqr(payload.from())" not in _netjava:
        fail("the server acts on whichever terminal the client named without checking "
             "the player is near it, so a client can empty any terminal in the world")
    if not os.path.isfile(os.path.join(SRC, "client/FreightScreen.java")):
        fail("there is no screen to choose a destination from")

    for _what, _path in (
            ("blockstate", "blockstates/freight_terminal.json"),
            ("model", "models/block/freight_terminal.json"),
            ("item model", "models/item/freight_terminal.json"),
            ("texture", "textures/block/freight_terminal.png"),
            ("top texture", "textures/block/freight_terminal_top.png")):
        if not os.path.isfile(os.path.join(ASSETS, _path)):
            fail(f"the freight terminal has no {_what}")
    if not os.path.isfile(os.path.join(DATA, "loot_table/blocks/freight_terminal.json")):
        fail("breaking a freight terminal destroys it")
    if not os.path.isfile(os.path.join(DATA, "recipe/freight_terminal.json")):
        fail("a freight terminal cannot be made, so the logistics system is "
             "creative-only")
    if f"block.{NS}.freight_terminal" not in lang:
        fail("the freight terminal has no name")

    # -----------------------------------------------------------------------
    # Gates that are already standing
    # -----------------------------------------------------------------------
    #
    # The user asked for these in one word: *working*, not broken. So the
    # template is read back and measured against GateRules — every square the
    # game will check when somebody clicks the anchor has the block the game
    # expects, and every square of the opening is air. A generated gate that
    # needed repairing would be a ruined portal with extra steps.

    _gate_nbt = os.path.join(DATA, "structure/waygate.nbt")
    if not os.path.isfile(_gate_nbt):
        fail("no waygate stands in the world, so the only way to another world is to "
             "build one yourself")
    else:
        sys.path.insert(0, os.path.join(WORK, "tools"))
        import nbt as _nbt_mod
        _name, _root = _nbt_mod.load(_gate_nbt)
        _tree = _root.value
        _sx, _sy, _sz = [t.value for t in _tree["size"].value]
        _palette = [e.value["Name"].value for e in _tree["palette"].value]
        _placed = {}
        for _entry in _tree["blocks"].value:
            _pos = tuple(t.value for t in _entry.value["pos"].value)
            _placed[_pos] = _palette[_entry.value["state"].value]

        _gate_rules = open(GATES, encoding="utf-8").read()
        _gate_size = int(re.search(r"SIZE = (\d+)", _gate_rules).group(1))
        _half = _gate_size // 2
        _centre = _sx // 2
        _middle = _sz // 2

        def _ring_offsets(size):
            half = size // 2
            out = [(a, 0) for a in range(-half, half + 1)]
            out += [(half, u) for u in range(1, size - 1)]
            out += [(a, size - 1) for a in range(half, -half - 1, -1)]
            out += [(-half, u) for u in range(size - 2, 0, -1)]
            return out

        _missing = []
        for _across, _up in _ring_offsets(_gate_size):
            _at = (_centre + _across, 1 + _up, _middle)
            _wanted = f"{NS}:waygate_anchor" if (_across == 0 and _up == 0) \
                else f"{NS}:waygate_frame"
            if _placed.get(_at) != _wanted:
                _missing.append(f"{_at} is {_placed.get(_at)} and should be {_wanted}")
        if _missing:
            fail(f"the waygate that generates in the world is not a gate the game "
                 f"would accept: {'; '.join(_missing[:3])}")

        _blocked = []
        for _across in range(-_half + 1, _half):
            for _up in range(1, _gate_size - 1):
                _at = (_centre + _across, 1 + _up, _middle)
                if _placed.get(_at) != "minecraft:air":
                    _blocked.append(str(_at))
        if _blocked:
            fail(f"the generated gate has {len(_blocked)} square(s) of its opening "
                 f"filled in, so it generates broken and has to be repaired before it "
                 f"works")
        if len(_placed) < _sx * _sy * _sz:
            fail("the template does not write every square of its own box, so a gate "
                 "that generates inside a hill keeps the hill in its opening")

        for _block in set(_palette):
            if _block.startswith(NS + ":") and _block.split(":", 1)[1] not in BLOCKS:
                fail(f"the waygate template places {_block}, which is not a block "
                     f"this module has")

        for _what, _path in (
                ("structure", "worldgen/structure/waygate.json"),
                ("template pool", "worldgen/template_pool/waygate/waygate.json"),
                ("structure set", "worldgen/structure_set/waygate.json"),
                ("biome tag", "tags/worldgen/biome/has_structure/waygate_biomes.json")):
            if not os.path.isfile(os.path.join(DATA, _path)):
                fail(f"the waygate has no {_what}, so it never generates")
        _set = json.load(open(os.path.join(DATA, "worldgen/structure_set/waygate.json"),
                              encoding="utf-8"))
        _spacing = _set["placement"]["spacing"]
        _separation = _set["placement"]["separation"]
        if _spacing != 40 or _separation != 15:
            fail(f"the waygate generates at {_spacing}/{_separation} rather than the "
                 f"ruined portal's 40/15, which is the frequency that was asked for")
        _biomes = json.load(open(os.path.join(
            DATA, "tags/worldgen/biome/has_structure/waygate_biomes.json"),
            encoding="utf-8"))
        _named = json.dumps(_biomes)
        for _world in ("frostfall", "pale_moon", "cinderveil"):
            if _world not in _named:
                fail(f"no gate generates on {_world}, so a player who arrives there "
                     f"by rocket has no way back except the rocket")
        if "minecraft:plains" not in _named:
            fail("no gate generates in the Overworld, so nothing leads off it")
        print(f"gates in the world : a working {_gate_size}x{_gate_size} ring on a pad, "
              f"opening clear, at the ruined portal's own 40/15")

    # -----------------------------------------------------------------------
    # Weather: what each sky does when it turns
    # -----------------------------------------------------------------------
    _weather_src = _src(SRC, "world/SpaceWeather.java")
    if not _weather_src:
        fail("the frontier worlds have no weather, so the one hazard a player can see "
             "coming does not exist")
    else:
        _kinds = re.findall(r'new ElysiumWeather\.Kind\("([a-z_]+)", ElysiumHazards\.([A-Z]+)',
                            _weather_src)
        if len(_kinds) < 4:
            fail(f"only {len(_kinds)} kind(s) of weather across four worlds, so most "
                 f"skies do one thing")
        _hazards = _src(SRC, "hazard/SpaceHazards.java") + open(
            os.path.join(os.path.dirname(WORK), "elysium-lib",
                         "src/main/java/com/elysium/lib/hazard/ElysiumHazards.java"),
            encoding="utf-8").read()
        for _kind, _hazard in _kinds:
            if f"{_hazard} " not in _hazards and f"{_hazard}=" not in _hazards \
                    and f"{_hazard})" not in _hazards:
                fail(f"the {_kind} raises {_hazard}, which is not a hazard the engine "
                     f"has")
            if f'"elysium.weather.{_kind}"' not in json.dumps(lang):
                fail(f"the {_kind} has no name, so the message that explains the "
                     f"damage reads as a raw key")
        _declared = re.findall(r'ElysiumWeather\.declare\(world\("([a-z_]+)"\)',
                               _weather_src)
        for _world in _declared:
            if f'"{_world}"' not in world_src:
                fail(f"weather is declared for {_world}, which is not one of this "
                     f"module's worlds")
        # The two deliberate omissions, checked as decisions rather than gaps.
        for _calm, _why in (
                ("emerald_bastion", "the one world with no hazards at all; a paradise "
                                    "with storms is another frontier"),
                ("overworld", "the player's own world, which was here before this mod "
                              "was")):
            if f'world("{_calm}")' in _weather_src:
                fail(f"weather is declared for {_calm}: {_why}")
            if _calm not in _weather_src:
                fail(f"{_calm} has no weather and nothing says why; an omission that "
                     f"is a decision has to read as one")
        if "ElysiumWeather.machineShare(" not in _src(SRC,
                "machine/SolarPanelBlockEntity.java"):
            fail("a solar panel makes as much under an ash cloud as under a clear "
                 "sky, so the weather is scenery for anything that is not a player")
        print(f"weather            : {len(_kinds)} kind(s) across {len(set(_declared))} "
              f"world(s), each raising a hazard the engine has, and two skies left "
              f"calm on purpose")

    _flight = re.search(r"MOST_TICKS = 20 \* (\d+)", _src(SRC, "world/FreightRules.java"))
    print(f"freight            : one terminal, one clock on the overworld, cargo held "
          f"rather than lost, and up to "
          f"{_flight.group(1) if _flight else '?'} seconds to the far worlds")

print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for p in problems:
        print("  - " + p)
    sys.exit(1)
print("all space checks passed")
