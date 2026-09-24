#!/usr/bin/env python3
"""
Validate Elysium Spawners against its sources.

Run from anywhere:   python3 elysium-spawners/validate.py

The things that matter most for this module, checked in this order:

  1. INDEPENDENCE. It is Elysium's own answer to Apothic Spawners. Nothing in
     it may reach for that mod — no import, no mod id, no dependency, not even
     an optional one — and it depends on elysium-lib and nothing else from
     Elysium.
  2. PURITY. `rules/` is compiled against the JDK alone and its harness is
     run, so the spawner's arithmetic is executed rather than only read.
  3. THE SPAWNER. Vanilla's own spawning, this module's numbers: the tuning
     has to be pushed into BaseSpawner rather than reimplemented beside it,
     and an untouched spawner has to be vanilla's spawner exactly.
  4. THE RUNES. Eleven items, eleven data files, eleven pairs of language
     entries, and every one of them checked against the others — a rune with
     no modifier file is an item that does nothing and says nothing.
  5. PICKING ONE UP. The drop carries both what is in the spawner and what was
     done to it, or moving one loses a player their work.
"""
import importlib.util
import json
import os
import re
import subprocess
import sys
import tempfile

WORK = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(WORK, "src/main/resources")
SRC = os.path.join(WORK, "src/main/java/com/elysium/spawners")
PURE = os.path.join(SRC, "rules")
HARNESS = os.path.join(WORK, "tools/SpawnerHarness.java")
HARNESS_MAIN = "com.elysium.spawners.rules.SpawnerHarness"
GENERATOR = os.path.join(WORK, "tools/gen_spawners.py")
NS = "elysiumspawners"

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


def items_named(body):
    """Every item id a criterion names, however deeply it is nested."""
    found = []

    def walk(node):
        if isinstance(node, dict):
            for key, value in node.items():
                if key == "items" and isinstance(value, str):
                    found.append(value)
                elif key == "items" and isinstance(value, list):
                    found.extend(v for v in value if isinstance(v, str))
                else:
                    walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(body)
    return [item for item in found if not item.startswith("#")]


def method_body(text, signature):
    """The body of one method, by brace matching, so a rule cannot pass on a
    token that survived somewhere else in the file."""
    start = text.find(signature)
    if start < 0:
        return ""
    brace = text.find("{", start)
    if brace < 0:
        return ""
    depth = 0
    for index in range(brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[brace:index + 1]
    return text[brace:]


if not os.path.isdir(SRC):
    print("This is not the elysium-spawners tree.")
    sys.exit(1)

java_files = []
for dirpath, _, filenames in os.walk(SRC):
    for name in filenames:
        if name.endswith(".java"):
            java_files.append(os.path.join(dirpath, name))
sources = {path: open(path, encoding="utf-8").read() for path in java_files}

# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------
FOREIGN = re.compile(r"(apotheosis|apothic|shadowsoffire|placebo|silentgear|silent_gear|"
                     r"silentchaos|silentlib|zenith)", re.I)
for path, raw in sources.items():
    rel = os.path.relpath(path, WORK)
    hit = FOREIGN.search(strip_comments(raw, keep_strings=True))
    if hit:
        fail(f"{rel} refers to {hit.group(0)!r} in code. This module replaces that "
             f"mod; it may not depend on it, even softly")
    for imp in re.findall(r"\bimport\s+(?:static\s+)?([\w.]+)\s*;", raw):
        if imp.startswith("com.elysium.") and not (
                imp.startswith("com.elysium.lib.") or imp.startswith("com.elysium.spawners.")):
            fail(f"{rel} imports {imp}. This module depends on elysium-lib and "
                 f"nothing else from Elysium")
toml = read(RES, "META-INF/neoforge.mods.toml")
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
      f"Apothic Spawners, depends on the library only")

# ---------------------------------------------------------------------------
# 2. Purity: the rules compile on the JDK alone, and the harness runs
# ---------------------------------------------------------------------------
pure_files = []
for dirpath, _, filenames in os.walk(PURE):
    for name in filenames:
        if name.endswith(".java"):
            pure_files.append(os.path.join(dirpath, name))
for path in pure_files:
    for imp in re.findall(r"\bimport\s+(?:static\s+)?([\w.]+)\s*;",
                          open(path, encoding="utf-8").read()):
        if not imp.startswith("java."):
            fail(f"{os.path.relpath(path, WORK)} imports {imp}; the spawner's arithmetic "
                 f"imports nothing but the JDK, so a harness can run it without a game")
if not pure_files:
    fail(f"the rules directory {os.path.relpath(PURE, WORK)} is empty")
elif not os.path.isfile(HARNESS):
    fail(f"{os.path.relpath(HARNESS, WORK)} is missing; the arithmetic is only read, "
         f"never run")
else:
    with tempfile.TemporaryDirectory() as out:
        compiled = subprocess.run(["javac", "-nowarn", "-d", out] + pure_files + [HARNESS],
                                  capture_output=True, text=True)
        text = "\n".join(l for l in (compiled.stdout + compiled.stderr).splitlines()
                         if "JAVA_TOOL_OPTIONS" not in l)
        if compiled.returncode != 0:
            fail("the rules do not compile on the JDK alone:\n      "
                 + text.replace("\n", "\n      "))
        else:
            try:
                ran = subprocess.run(["java", "-cp", out, HARNESS_MAIN],
                                     capture_output=True, text=True, timeout=300)
            except subprocess.TimeoutExpired:
                ran = None
                fail("the SpawnerHarness did not finish in five minutes")
            if ran is not None:
                lines = [l for l in (ran.stdout + ran.stderr).splitlines()
                         if "JAVA_TOOL_OPTIONS" not in l]
                if ran.returncode != 0:
                    fail("the SpawnerHarness failed:\n      " + "\n      ".join(lines))
                else:
                    print("spawner rules      : " + (lines[-1] if lines else "ran"))

# ---------------------------------------------------------------------------
# 3. All JSON parses
# ---------------------------------------------------------------------------
json_files = {}
for dirpath, _, filenames in os.walk(RES):
    for filename in filenames:
        if filename.endswith(".json"):
            path = os.path.join(dirpath, filename)
            try:
                json_files[path] = json.load(open(path, encoding="utf-8"))
            except Exception as exc:
                fail(f"invalid JSON {os.path.relpath(path, WORK)}: {exc}")
lang = json_files.get(os.path.join(RES, f"assets/{NS}/lang/en_us.json"), {})
if not lang:
    fail(f"assets/{NS}/lang/en_us.json is missing or empty; every string this module "
         f"shows would be a raw key")
print(f"json files parsed  : {len(json_files)}")

# ---------------------------------------------------------------------------
# 4. The numbers
# ---------------------------------------------------------------------------
rules = read(PURE, "SpawnerRules.java")
rules_code = strip_comments(rules)


def constant(name, text=None):
    match = re.search(r"\b" + name + r"\s*=\s*(-?[\d.]+)[FfDd]?\s*;", text or rules_code)
    return float(match.group(1)) if match else None


for name in ("BASE_MIN_DELAY", "BASE_MAX_DELAY", "BASE_SPAWN_COUNT", "BASE_MAX_NEARBY",
             "BASE_PLAYER_RANGE", "BASE_SPAWN_RANGE"):
    if constant(name) is None:
        fail(f"SpawnerRules no longer states {name}; an untouched spawner has to be "
             f"vanilla's spawner exactly, and that is only checkable against a number")
if constant("BASE_MIN_DELAY") != 200 or constant("BASE_MAX_DELAY") != 800 \
        or constant("BASE_SPAWN_COUNT") != 4 or constant("BASE_MAX_NEARBY") != 6 \
        or constant("BASE_PLAYER_RANGE") != 16 or constant("BASE_SPAWN_RANGE") != 4:
    fail("an untouched spawner is not vanilla's spawner: one of the six base numbers "
         "has been changed, so every spawner already in a player's world quietly "
         "behaves differently than it did")
share = method_body(rules_code, "public static double share(")
if "at / (at + DELAY_PACE)" not in share.replace("  ", " "):
    fail("the waiting is not cut by a share through x/(x+b), so enough tuning takes it "
         "to zero — which is every mob the spawner will ever send arriving on one tick")
if constant("DELAY_FLOOR") is None or constant("DELAY_FLOOR") < 1:
    fail("there is no floor under how fast a spawner may be told to go")
capture = method_body(rules_code, "public static float captureChance(")
if "CAPTURE_CEILING" not in capture or "(at + CAPTURE_PACE)" not in capture:
    fail("capturing is not a share of a ceiling, so some number of levels makes a spawn "
         "egg certain and the first Capturing sword ends the mob-farm question forever")
if (constant("CAPTURE_CEILING") or 1.0) >= 1.0:
    fail("capturing's ceiling is certainty")

# ---------------------------------------------------------------------------
# 5. The spawner itself
# ---------------------------------------------------------------------------
entity = read(SRC, "block/ElysiumSpawnerBlockEntity.java")
entity_code = strip_comments(entity, keep_strings=True)
block = read(SRC, "block/ElysiumSpawnerBlock.java")
block_code = strip_comments(block, keep_strings=True)
if "extends BaseSpawner" not in entity_code:
    fail("the spawner does not use the game's own BaseSpawner. Every behaviour a player "
         "understands — the checks, the particles, the creature turning in the cage — "
         "would then be this module's to reimplement and get subtly wrong")
applied = method_body(entity_code, "private void apply(")
for key, call in (("MinSpawnDelay", "SpawnerRules.minDelay("),
                  ("MaxSpawnDelay", "SpawnerRules.maxDelay("),
                  ("SpawnCount", "SpawnerRules.spawnCount("),
                  ("MaxNearbyEntities", "SpawnerRules.maxNearby("),
                  ("SpawnRange", "SpawnerRules.spawnRange(")):
    if key not in applied or call not in applied:
        fail(f"the tuning is not pushed into the spawner's {key}, so a rune raises a "
             f"number nothing reads")
if "spawner.load(" not in applied:
    fail("the tuning is written into a tag and never handed to the spawner")
if "SpawnerRules.ALWAYS_AWAKE" not in entity_code:
    fail("a spawner told to ignore players is not given a range that always contains "
         "one, so the rune does nothing")
ticked = method_body(entity_code, "public static void serverTick(")
if "hasNeighborSignal" not in ticked:
    fail("a spawner told to wait for a signal never checks for one")
if "tuning.redstone()" not in ticked:
    fail("every spawner waits for a redstone signal rather than only the ones told to")
loaded = method_body(entity_code, "protected void loadAdditional(")
saved = method_body(entity_code, "protected void saveAdditional(")
if "spawner.load(" not in loaded or "spawner.save(" not in saved:
    fail("the spawner's own contents are not both written and read, so what is in a "
         "spawner is lost on the next restart")
if '"tuning"' not in loaded or '"tuning"' not in saved:
    fail("the tuning is not both written and read, so every rune a player spent is "
         "lost on the next restart")
if "protected void collectImplicitComponents(" not in entity_code \
        or "protected void applyImplicitComponents(" not in entity_code:
    fail("the spawner does not offer its tuning as a component, so a spawner picked up "
         "and put down again forgets what was done to it")
used = method_body(block_code, "protected ItemInteractionResult useItemOn(")
if "SpawnerModifiers.get().forItem(" not in used:
    fail("what a held item does to a spawner is decided in Java rather than read from "
         "the data, so a pack cannot add a rune or change one")
if "SpawnerRules.stepCost(steps)" not in used:
    fail("a step on an axis costs a flat number rather than one that climbs with the "
         "steps already taken, so it is a number this module made up on the spot")
if "held.shrink(" not in method_body(block_code, "private static boolean spend("):
    fail("tuning a spawner does not consume the runes, so one rune tunes a spawner "
         "forever")
if "instabuild" not in method_body(block_code, "private static boolean spend("):
    fail("creative mode is charged for a step")
if "modifier.maxSteps()" not in used:
    fail("an axis with a limit on it is not limited, so a player keeps spending runes "
         "on a number that has stopped moving")
if "SpawnerRules.UNSTABLE_POWER" not in block_code:
    fail("an unstable spawner does not go off when it is broken")

# ---------------------------------------------------------------------------
# 6. What the runes are
# ---------------------------------------------------------------------------
spec = importlib.util.spec_from_file_location("gen_spawners", GENERATOR)
generator = importlib.util.module_from_spec(spec)
try:
    spec.loader.exec_module(generator)
except Exception as exc:                                    # pragma: no cover
    generator = None
    fail(f"tools/gen_spawners.py could not be read: {exc}")

items_java = read(SRC, "registry/SpawnerItems.java")
tuning_java = read(SRC, "data/SpawnerTuning.java")
registered = re.findall(r'rune\("(\w+)"\)', strip_comments(items_java, True))
stats = re.findall(r'"(\w+)"', strip_comments(tuning_java, True).split("STATS =")[-1]
                   .split(";")[0]) if "STATS =" in tuning_java else []
flags = re.findall(r'"(\w+)"', strip_comments(tuning_java, True).split("FLAGS =")[-1]
                   .split(";")[0]) if "FLAGS =" in tuning_java else []
if generator is not None:
    table = {row[0]: row for row in generator.RUNES}
    if len(table) != len(generator.RUNES):
        fail("two runes in gen_spawners.py have the same name; one file is the file the "
             "other overwrites")
    for name in registered:
        if name not in table:
            fail(f"{name} is registered in Java and the generator has never heard of it, "
                 f"so it has no sprite, no recipe and — worse — no modifier file, and "
                 f"clicking a spawner with it does nothing at all")
    for name in table:
        if name not in registered:
            fail(f"{name} has files generated for it and nothing registers it, so the "
                 f"files are dead weight in the jar")
    answered = {"stat": set(), "flag": set()}
    for name, title, spec_text, max_steps, sigil, glow, ingredient, why in generator.RUNES:
        what, which = generator.kind(spec_text)
        answered[what].add(which)
        for label, path in (("model", f"assets/{NS}/models/item/{name}.json"),
                            ("sprite", f"assets/{NS}/textures/item/{name}.png"),
                            ("recipe", f"data/{NS}/recipe/{name}.json"),
                            ("modifier file",
                             f"data/{NS}/elysium_spawner_modifiers/{name}.json")):
            if not os.path.isfile(os.path.join(RES, path)):
                fail(f"{name} has no {label} ({path})")
        entry = json_files.get(os.path.join(
            RES, f"data/{NS}/elysium_spawner_modifiers/{name}.json"), {})
        if entry.get("item") != f"{NS}:{name}":
            fail(f"{name}'s modifier file names {entry.get('item')!r}, so the rune and "
                 f"the file that gives it meaning are about different items")
        if entry.get(what) != which:
            fail(f"{name}'s modifier file says {what} {entry.get(what)!r} and the table "
                 f"says {which!r}; a shipped file was edited by hand")
        if ("stat" in entry) == ("flag" in entry):
            fail(f"{name}'s modifier file names both a stat and a flag, or neither; it "
                 f"has to name exactly one")
        if entry.get("max_steps", 0) != max_steps:
            fail(f"{name}'s limit shipped as {entry.get('max_steps', 0)} and the table "
                 f"says {max_steps}")
        if f"item.{NS}.{name}" not in lang:
            fail(f"{name} has no name, so a player sees item.{NS}.{name}")
        if f"{NS}.tooltip.{name}" not in lang:
            fail(f"{name} says nothing about itself, and what a rune does is a data file "
                 f"a player cannot read")
        if sigil not in getattr(generator.sprites, "SPAWNER_SIGILS", {}):
            fail(f"{name} is drawn with the mark {sigil!r}, which the art package does "
                 f"not have")
    for which in stats:
        if which not in answered["stat"]:
            fail(f"a spawner has an axis called {which!r} and no rune raises it, so it "
                 f"is a number nobody can move")
    for which in flags:
        if which not in answered["flag"]:
            fail(f"a spawner has a behaviour called {which!r} and no rune switches it "
                 f"on, so it is a behaviour nobody can ask for")
    for which in answered["stat"]:
        if which not in stats:
            fail(f"a rune raises {which!r}, which is not one of a spawner's axes, so "
                 f"clicking with it does nothing")
    for which in answered["flag"]:
        if which not in flags:
            fail(f"a rune switches on {which!r}, which is not one of a spawner's "
                 f"behaviours")
    print(f"the runes          : {len(table)}, every axis and behaviour answered by one")

# ---------------------------------------------------------------------------
# 7. Picking one up
# ---------------------------------------------------------------------------
pickup = read(SRC, "event/SpawnerPickup.java")
pickup_code = strip_comments(pickup, keep_strings=True)
dropped = method_body(pickup_code, "public static void onDrops(")
if not dropped:
    fail("nothing picks a spawner up, which is the whole of what this module is for")
else:
    if "hasSilkTouch(" not in dropped:
        fail("a spawner comes up with any tool at all, so the silk-touch pickaxe every "
             "player saves for is worth nothing")
    if "DataComponents.BLOCK_ENTITY_DATA" not in dropped:
        fail("the dropped spawner does not carry what is in it, so a skeleton spawner "
             "moved across a world arrives empty")
    if "SpawnerComponents.TUNING.get()" not in dropped:
        fail("the dropped spawner does not carry what was done to it, so moving one "
             "loses a player every rune they spent")
    if "isVanillaSpawner(" not in dropped:
        fail("vanilla's own spawner is not picked up, so every spawner already in every "
             "world stays what it was and this module only applies to itself")
    if "carriable(" not in dropped:
        fail("an unstable spawner can be pocketed, which is the one thing it is not for")
    if "smashed(" not in dropped:
        fail("a spawner broken without silk touch gives nothing, so smashing one is "
             "strictly worse than leaving it and every player smashes it anyway")
smashed = method_body(pickup_code, "private static void smashed(")
if "UNSTABLE" not in smashed or "getRandomItems(" not in smashed:
    fail("what a smashed spawner is worth is decided in Java rather than by a loot "
         "table, so a pack cannot change it")
if not os.path.isfile(os.path.join(RES, f"data/{NS}/loot_table/unstable_spawner.json")):
    fail("the table a smashed spawner rolls does not ship")
contents = method_body(pickup_code, "private static CompoundTag contentsOf(")
if 'putString("id"' not in contents:
    fail("the carried spawner's tag does not name this module's block entity, so "
         "placing it puts nothing back — vanilla only applies block entity data when "
         "the tag names the block entity that is there")

# ---------------------------------------------------------------------------
# 8. What the spawner sends
# ---------------------------------------------------------------------------
behaviour = read(SRC, "event/SpawnerBehaviour.java")
behaviour_code = strip_comments(behaviour, keep_strings=True)
position = method_body(behaviour_code, "public static void onPosition(")
finalise = method_body(behaviour_code, "public static void onFinalize(")
if "ElysiumSpawnerBlockEntity.Cage" not in position:
    fail("the light rule is applied without asking whether the spawner is one of this "
         "module's, so every spawner in the game ignores the light")
if "ignoresLight()" not in position:
    fail("a spawner ignores the light whether or not it was told to")
for flag, call in (("no_ai", "setNoAi(true)"), ("silent", "setSilent(true)"),
                   ("persistent", "setPersistenceRequired()")):
    if call not in finalise:
        fail(f"the {flag} behaviour is a rune that does nothing: {call} is never called")
if "tuningOf(event)" not in finalise:
    fail("what a creature is like is decided without asking which spawner sent it")

# ---------------------------------------------------------------------------
# 9. Capturing
# ---------------------------------------------------------------------------
capturing = read(SRC, "loot/CapturingModifier.java")
capturing_code = strip_comments(capturing, keep_strings=True)
if "SpawnerRules.captureChance(" not in capturing_code:
    fail("the capturing modifier has a chance of its own rather than the rule the "
         "harness checks")
if "SpawnEggItem.byId(" not in capturing_code:
    fail("capturing does not produce the creature's own egg")
if "LootContextParams.ATTACKING_ENTITY" not in capturing_code:
    fail("capturing never asks who did the killing, so a creature that fell off a cliff "
         "leaves an egg")
enchantment = json_files.get(os.path.join(RES, f"data/{NS}/enchantment/capturing.json"))
if enchantment is None:
    fail("the capturing enchantment does not ship, so nothing can ever have it")
else:
    if enchantment.get("weight", 10) > 2:
        fail("capturing is offered at every table in the game rather than only to a room "
             "built for rarity")
    if enchantment.get("description", {}).get("translate") != f"enchantment.{NS}.capturing":
        fail("the capturing enchantment does not name its own description key")
    if f"enchantment.{NS}.capturing" not in lang:
        fail("capturing has no name")
modifier = json_files.get(os.path.join(RES, f"data/{NS}/loot_modifiers/capturing.json"))
listed = json_files.get(os.path.join(RES, "data/neoforge/loot_modifiers/"
                                          "global_loot_modifiers.json"), {})
if modifier is None or modifier.get("type") != f"{NS}:capturing":
    fail("the capturing loot modifier does not ship, or names a serializer nobody "
         "registered")
if f"{NS}:capturing" not in listed.get("entries", []):
    fail("the capturing loot modifier is never listed, so the game never loads it")
if listed.get("replace"):
    fail("the loot modifier list is shipped with replace set, which turns off every "
         "other mod's loot modifiers")
print("capturing          : an enchantment that is data and one modifier that reads it")

# ---------------------------------------------------------------------------
# 10. The block, and getting hold of one
# ---------------------------------------------------------------------------
blocks_java = read(SRC, "registry/SpawnerBlocks.java")
blocks_code = strip_comments(blocks_java, keep_strings=True)
block_names = re.findall(r'BLOCKS\.register\(\s*"(\w+)"', blocks_code)
if not block_names:
    fail("SpawnerBlocks registers no blocks at all")
for name in block_names:
    for what, path in (("blockstate", f"assets/{NS}/blockstates/{name}.json"),
                       ("block model", f"assets/{NS}/models/block/{name}.json"),
                       ("item model", f"assets/{NS}/models/item/{name}.json"),
                       ("texture", f"assets/{NS}/textures/block/{name}.png"),
                       ("loot table", f"data/{NS}/loot_table/blocks/{name}.json")):
        if not os.path.isfile(os.path.join(RES, path)):
            fail(f"{name} has no {what} ({path})")
    if f"block.{NS}.{name}" not in lang:
        fail(f"{name} has no name, so a player sees block.{NS}.{name}")
    recipe = os.path.join(RES, f"data/{NS}/recipe/{name}.json")
    excused = generator is not None and name in generator.NO_RECIPE
    if os.path.isfile(recipe) and excused:
        fail(f"{name} has a recipe and the generator says it should not have one "
             f"({generator.NO_RECIPE[name]})")
    if not os.path.isfile(recipe) and not excused:
        fail(f"{name} has no recipe and no reason given for not having one")
    tag = json_files.get(os.path.join(RES,
                                      "data/minecraft/tags/block/mineable/pickaxe.json"), {})
    if f"{NS}:{name}" not in tag.get("values", []):
        fail(f"{name} requires the correct tool and is in no tool's tag, so no tool is "
             f"ever correct and it drops nothing however it is mined")
if "CreativeModeTab" not in blocks_java:
    fail("nothing this module adds is in a creative tab")
if "itemGroup.elysiumspawners" not in lang:
    fail("the creative tab has no name")
if "SpawnerBlocks.inTab(item)" not in strip_comments(items_java):
    fail("the runes are not in the creative tab, so a creative player has to know "
         "eleven item ids")

entities_java = read(SRC, "registry/SpawnerBlockEntities.java")
for name in block_names:
    if f"SpawnerBlocks.{name.upper()}.get()" not in entities_java:
        fail(f"{name} is not one of the blocks its block entity type accepts, so placing "
             f"one and clicking it throws rather than working")

client = read(SRC, "client/SpawnersClient.java")
renderer = read(SRC, "client/SpawnerCageRenderer.java")
if "registerBlockEntityRenderer" not in client:
    fail("nothing draws what is in the spawner, so the cage is empty whatever is in it")
if "SpawnerRenderer.renderEntityInSpawner(" not in renderer:
    fail("the creature in the cage is drawn by this module rather than by the game's own "
         "renderer, so it will look wrong in somebody's resource pack")

# ---------------------------------------------------------------------------
# What a player can ask (SPEC_CLONE P12.15)
# ---------------------------------------------------------------------------
command = read(SRC, "command/SpawnerCommand.java")
command_code = strip_comments(command, keep_strings=True)
if not command:
    fail("there is no way to ask what a spawner is: every number a player spent runes "
         "on is invisible to them")
else:
    if "RegisterCommandsEvent" not in command_code:
        fail("the spawner command is never registered")
    if "requires(" in command_code:
        fail("the spawner command is gated behind a permission. It only reads, and "
             "everything it prints is derivable from the block the player is already "
             "looking at — gating it stops only the people it was written for")
    if "player.pick(" not in command_code:
        fail("the command does not read the spawner the player is looking at, so it "
             "either takes coordinates nobody knows or reports somebody else's spawner")
    for axis in ("delay", "count", "nearby", "range", "spread"):
        if f'"{axis}"' not in command_code:
            fail(f"the command does not report the {axis} axis, so a player cannot tell "
                 f"whether another rune would do anything")
    if "SpawnerRules.stepCost(steps)" not in command_code:
        fail("the command reports what a spawner is and not what the next step costs, "
             "which is the question it exists to answer")
    if "tuning.spent()" not in command_code:
        fail("the command never says what has gone into the spawner already")
    print("the command        : five axes, their numbers, and what the next step costs")

OWNED = set(block_names) | set(registered)

# ---------------------------------------------------------------------------
# 11. Advancements: a tree a player can actually climb
# ---------------------------------------------------------------------------
#
# The triggers the game has. An advancement naming one it does not have fails
# to parse, which the game reports as a line in a log nobody reads and a player
# experiences as an advancement that never appears.
TRIGGERS = {
    "minecraft:allay_drop_item_on_block", "minecraft:any_block_use",
    "minecraft:avoid_vibration", "minecraft:bee_nest_destroyed", "minecraft:bred_animals",
    "minecraft:brewed_potion", "minecraft:changed_dimension", "minecraft:channeled_lightning",
    "minecraft:construct_beacon", "minecraft:consume_item",
    "minecraft:crafter_recipe_crafted", "minecraft:cured_zombie_villager",
    "minecraft:default_block_use", "minecraft:effects_changed", "minecraft:enchanted_item",
    "minecraft:enter_block", "minecraft:entity_hurt_player", "minecraft:entity_killed_player",
    "minecraft:fall_after_explosion", "minecraft:fall_from_height", "minecraft:filled_bucket",
    "minecraft:fishing_rod_hooked", "minecraft:hero_of_the_village", "minecraft:impossible",
    "minecraft:inventory_changed", "minecraft:item_durability_changed",
    "minecraft:item_used_on_block", "minecraft:kill_mob_near_sculk_catalyst",
    "minecraft:killed_by_crossbow", "minecraft:levitation", "minecraft:lightning_strike",
    "minecraft:location", "minecraft:nether_travel", "minecraft:placed_block",
    "minecraft:player_generates_container_loot", "minecraft:player_hurt_entity",
    "minecraft:player_interacted_with_entity", "minecraft:player_killed_entity",
    "minecraft:recipe_crafted", "minecraft:recipe_unlocked",
    "minecraft:ride_entity_in_lava", "minecraft:shot_crossbow", "minecraft:slept_in_bed",
    "minecraft:slide_down_block", "minecraft:started_riding", "minecraft:summoned_entity",
    "minecraft:tame_animal", "minecraft:target_hit",
    "minecraft:thrown_item_picked_up_by_entity", "minecraft:thrown_item_picked_up_by_player",
    "minecraft:tick", "minecraft:used_ender_eye", "minecraft:used_totem",
    "minecraft:using_item", "minecraft:villager_trade", "minecraft:voluntary_exile",
}

advancement_dir = os.path.join(RES, f"data/{NS}/advancement")
shipped_advancements = sorted(name[:-5] for name in os.listdir(advancement_dir)
                              if name.endswith(".json")) \
    if os.path.isdir(advancement_dir) else []
if generator is not None:
    declared = {row[0]: row for row in generator.ADVANCEMENTS}
    if len(declared) != len(generator.ADVANCEMENTS):
        fail("two advancements have the same name; one file is the file the other "
             "overwrites")
    for name in shipped_advancements:
        if name not in declared:
            fail(f"data/{NS}/advancement/{name}.json is not in the table, so the next "
                 f"run deletes it")
    roots = [name for name, row in declared.items() if row[1] is None]
    if len(roots) != 1:
        fail(f"{len(roots)} advancement(s) have no parent. A tree has one root: two "
             f"make two tabs in the screen, and none makes a tab nobody can reach")
    for name, parent, icon, frame, title, description, criteria in generator.ADVANCEMENTS:
        entry = json_files.get(os.path.join(advancement_dir, f"{name}.json"))
        if entry is None:
            fail(f"the advancement {name} is declared and no file ships")
            continue
        if entry.get("parent") != (f"{NS}:{parent}" if parent is not None else None):
            fail(f"the advancement {name} ships hanging off {entry.get('parent')!r} and "
                 f"the table says {parent!r}; a shipped file was edited by hand")
        if parent is not None and parent not in declared:
            fail(f"{name} hangs off {parent!r}, which is not an advancement this module "
                 f"has, so it never appears")
        for key, what in ((f"advancements.{NS}.{name}.title", "title"),
                          (f"advancements.{NS}.{name}.description", "description")):
            if key not in lang:
                fail(f"the advancement {name} has no {what} ({key}), so a player sees a "
                     f"raw key in the one screen that is meant to explain the mod")
        namespace, _, path = icon.partition(":")
        if namespace == NS and path not in OWNED:
            fail(f"the advancement {name} is drawn with {icon}, which this module does "
                 f"not register, so its square is the missing-texture cube")
        if frame not in ("task", "goal", "challenge"):
            fail(f"the advancement {name} has the frame {frame!r}, which the game does "
                 f"not draw")
        for criterion, body in (entry.get("criteria") or {}).items():
            trigger = body.get("trigger")
            if trigger not in TRIGGERS:
                fail(f"the advancement {name} waits for {trigger!r}, which is not a "
                     f"trigger the game has: the file will not parse and the "
                     f"advancement will simply never appear")
            for item in items_named(body):
                if item.startswith(f"{NS}:") and item.split(":", 1)[1] not in OWNED:
                    fail(f"the advancement {name} waits for {item}, which this module "
                         f"does not register, so it can never be earned")
        wanted = sorted((entry.get("criteria") or {}).keys())
        listed = sorted(name for group in entry.get("requirements", []) for name in group)
        if wanted != listed:
            fail(f"the advancement {name} lists {listed} as its requirements and has "
                 f"{wanted} as its criteria; a challenge satisfied by one of six is not "
                 f"the challenge it says it is")
    print(f"advancements       : {len(declared)} in one tree, every trigger real and "
          f"every name written")

# ---------------------------------------------------------------------------
# 12. Every literal key this module shows has words behind it
# ---------------------------------------------------------------------------
shown = set()
for path, raw in sources.items():
    for key in re.findall(r'"((?:block|item|attribute|itemGroup|enchantment'
                          r'|elysiumspawners)[\w.]*\.[\w.]+)"', raw):
        if key.endswith(".") or "%s" in key:
            continue
        shown.add((os.path.relpath(path, WORK), key))
for rel, key in sorted(shown):
    if key.startswith("block.") or key.startswith(f"item.{NS}.") \
            or key.startswith("itemGroup.") or key.startswith(f"{NS}.") \
            or key.startswith(f"enchantment.{NS}."):
        if key not in lang:
            fail(f"{rel} shows {key} and nothing translates it, so a player reads a raw key")
print(f"language           : {len(lang)} key(s), every literal one reachable")
print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all spawner checks passed")
