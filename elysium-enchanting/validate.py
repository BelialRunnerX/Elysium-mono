#!/usr/bin/env python3
"""
Validate Elysium Enchanting against its sources.

Run from anywhere:   python3 elysium-enchanting/validate.py

The things that matter most for this module, checked in this order:

  1. INDEPENDENCE. It is Elysium's own answer to Apothic Enchanting. Nothing
     in it may reach for that mod — no import, no mod id, no dependency, not
     even an optional one — and it depends on elysium-lib and nothing else
     from Elysium.
  2. PURITY. `rules/` is compiled against the JDK alone and its harness is
     run, so the table's arithmetic is executed rather than only read.
  3. THE ROOM. The four numbers have to come from blocks a player put there,
     the cap has to be the room's best rather than its sum, and a table in an
     empty room has to offer nothing.
  4. THE SHELVES. Every shipped stat file is checked back against the table in
     tools/gen_enchanting.py, every one of the four numbers has to be
     buildable, and something has to sit above what a player starts able to
     hold — otherwise the attribute is an attribute nobody can feel.
  5. THE TABLE ITSELF. Offers that do not reroll when the screen is reopened,
     uncertainty applied when the player buys rather than when the table
     offers, and a block that stands beside vanilla's rather than replacing it.
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
SRC = os.path.join(WORK, "src/main/java/com/elysium/enchanting")
PURE = os.path.join(SRC, "rules")
HARNESS = os.path.join(WORK, "tools/EnchantingHarness.java")
HARNESS_MAIN = "com.elysium.enchanting.rules.EnchantingHarness"
GENERATOR = os.path.join(WORK, "tools/gen_enchanting.py")
NS = "elysiumenchanting"

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
                    # inventory_changed's "items" is a list of item predicates,
                    # each a dict with "items" of its own, so the dicts are
                    # walked too: read as strings only, every criterion this
                    # module ships named nothing and an advancement waiting
                    # for an item nobody registers passed (16 October 2026).
                    for v in value:
                        if isinstance(v, str):
                            found.append(v)
                        else:
                            walk(v)
                else:
                    walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(body)
    return [item for item in found if not item.startswith("#")]


def method_body(text, signature):
    """
    The body of one method, by brace matching.

    A rule scoped to a file passes as long as the text it looks for survives
    anywhere in that file, which is how a check ends up unable to fail. Every
    rule below that is about what one method does is given that method.
    """
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
    print("This is not the elysium-enchanting tree.")
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
                     r"silentchaos|silentlib|enchantment_library|zenith)", re.I)
for path, raw in sources.items():
    rel = os.path.relpath(path, WORK)
    hit = FOREIGN.search(strip_comments(raw, keep_strings=True))
    if hit:
        fail(f"{rel} refers to {hit.group(0)!r} in code. This module replaces that "
             f"mod; it may not depend on it, even softly")
    for imp in re.findall(r"\bimport\s+(?:static\s+)?([\w.]+)\s*;", raw):
        if imp.startswith("com.elysium.") and not (
                imp.startswith("com.elysium.lib.") or imp.startswith("com.elysium.enchanting.")):
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
      f"Apothic Enchanting, depends on the library only")

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
            fail(f"{os.path.relpath(path, WORK)} imports {imp}; the table's arithmetic "
                 f"imports nothing but the JDK, so a harness can run it without a game")
if not pure_files:
    fail(f"the rules directory {os.path.relpath(PURE, WORK)} is empty")
elif not os.path.isfile(HARNESS):
    fail(f"{os.path.relpath(HARNESS, WORK)} is missing; the arithmetic is only read, never run")
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
                fail("the EnchantingHarness did not finish in five minutes")
            if ran is not None:
                lines = [l for l in (ran.stdout + ran.stderr).splitlines()
                         if "JAVA_TOOL_OPTIONS" not in l]
                if ran.returncode != 0:
                    fail("the EnchantingHarness failed:\n      " + "\n      ".join(lines))
                else:
                    print("enchanting rules   : " + (lines[-1] if lines else "ran"))

# ---------------------------------------------------------------------------
# 3. All JSON parses
# ---------------------------------------------------------------------------
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
# 4. The four numbers, and the rules that hold them
# ---------------------------------------------------------------------------
rules = read(PURE, "EnchantingRules.java")
rules_code = strip_comments(rules)


def constant(name, text=None):
    match = re.search(r"\b" + name + r"\s*=\s*(-?[\d.]+)[FfDd]?\s*;", text or rules_code)
    return float(match.group(1)) if match else None


base_max = constant("BASE_MAX_ETERNA")
if base_max is None:
    fail("EnchantingRules no longer states a BASE_MAX_ETERNA, which is what a player "
         "may hold before they have raised anything")
elif base_max <= 0:
    fail("BASE_MAX_ETERNA is not a positive number, so a new player's table can hold "
         "nothing whatever they build around it")

for name, field in (("eterna", "eterna"), ("quanta", "quanta"),
                    ("arcana", "arcana"), ("rectification", "rectification")):
    if not re.search(r"\b" + field + r"\b", rules_code):
        fail(f"EnchantingRules no longer has a {name}, which is one of the four "
             f"questions the table asks")

compact = method_body(rules_code, "public Stats {")
for field, expected in (("eterna", "Math.max(0.0F, eterna)"),
                        ("quanta", "Math.max(0.0F, quanta)"),
                        ("arcana", "Math.max(0.0F, arcana)")):
    if expected not in compact:
        fail(f"Stats no longer clamps {field} on the way in; a negative number in a "
             f"datapack would make a table run backwards rather than be read as none")
if "Math.min(1.0F, rectification)" not in compact:
    fail("rectification is no longer held to one, so a table could be paid more for its "
         "uncertainty than the uncertainty is worth")

ceiling = method_body(rules_code, "public static float ceiling(")
if not ceiling:
    fail("EnchantingRules has no ceiling(): the room's cap and the player's cap are two "
         "different questions and something has to answer both")
elif "return Math.min(Math.max(0.0F, roomCap), Math.max(0.0F, playerMax));" not in ceiling:
    fail("ceiling() does not take the smaller of the two caps, so either a room or a "
         "player alone decides what a table holds and the other is decoration")

rolled = method_body(rules_code, "public static int rolledLevel(")
if "stats.rectification()" not in rolled:
    fail("rolledLevel() no longer reads rectification, so uncertainty cuts both ways "
         "however much a player has paid to stop it")
elif "roll < 0.0D" not in rolled:
    fail("rolledLevel() no longer tells the downside from the upside, so rectification "
         "either removes both or neither")

share = method_body(rules_code, "public static double rarityShare(")
if "/ (at + ARCANA_PACE)" not in share.replace("  ", " "):
    fail("rarityShare() is not x/(x+b), so some amount of arcana makes a rare "
         "enchantment certain — every percentile thing in this mod is a share")

# ---------------------------------------------------------------------------
# 5. The room: what a table reads off the blocks around it
# ---------------------------------------------------------------------------
survey = read(SRC, "data/EnchantingSurvey.java")
survey_code = strip_comments(survey)
survey_body = method_body(survey_code, "public static EnchantingRules.Stats survey(")
if not survey_body:
    fail("EnchantingSurvey no longer has a survey(); nothing reads the room")
else:
    if "float roomCap = 0.0F;" not in survey_body:
        fail("the room's cap does not start at nothing, so a table in an empty room can "
             "hold power anyway and every block a player puts around it is decoration")
    if "Math.max(roomCap, shelf.maxEterna())" not in survey_body:
        fail("the room's cap is not the best single cap in it; added up, the answer to "
             "every question is one more bookshelf, which is the table this replaces")
    if "EnchantingRules.ceiling(roomCap, playerMax)" not in survey_body:
        fail("the survey works out its own ceiling rather than asking EnchantingRules, "
             "so the rule the harness checks and the rule the game uses are two rules")
    if "EnchantingAttributes.MAX_ETERNA" not in survey_body:
        fail("the survey never asks the player what they can hold, so the one attribute "
             "this module adds does nothing")
    if "total.plus(shelf.stats())" not in survey_body:
        fail("the survey does not add up what the shelves are worth")
if "visible(level, table, x, y, z)" not in survey_body:
    fail("the survey no longer checks that the table can see a shelf, so a wall of "
         "bookshelves buried in stone counts and the room stops being a build")
if "Math.abs(x) < 2 && Math.abs(z) < 2" not in survey_body:
    fail("the survey no longer skips the space around the table, so a player cannot "
         "stand at their own table")
radius = constant("RADIUS", strip_comments(survey))
height = constant("HEIGHT", strip_comments(survey))
if radius is None or radius < 2:
    fail("the survey's RADIUS is smaller than vanilla's, so a room every player already "
         "knows how to build stopped working")
if radius is not None and radius > 8:
    fail("the survey's RADIUS reaches further than a room, so a table reads blocks in "
         "the next building and a player cannot tell what it is counting")
if height is None or height < 1:
    fail("the survey's HEIGHT no longer looks above the table, so a two-high shelf wall "
         "counts as one")

blocks_java = read(SRC, "registry/EnchantingBlocks.java")
attributes_java = read(SRC, "registry/EnchantingAttributes.java")
if 'ATTRIBUTES.register("max_eterna"' not in strip_comments(attributes_java, True):
    fail("max_eterna is not registered, so nothing can raise what a player may hold")
if ".setSyncable(true)" not in attributes_java:
    fail("max_eterna is not synced to the client, so a player's screen and their server "
         "disagree about what their own table can do")
if 'Double.MAX_VALUE' not in attributes_java:
    fail("max_eterna has a ceiling of its own. Everything in this mod is uncapped "
         "upward; the scaling is in the shares, not in a limit")

# ---------------------------------------------------------------------------
# 6. The shelves, against the table that generated them
# ---------------------------------------------------------------------------
spec = importlib.util.spec_from_file_location("gen_enchanting", GENERATOR)
generator = importlib.util.module_from_spec(spec)
try:
    spec.loader.exec_module(generator)
except Exception as exc:                                    # pragma: no cover
    generator = None
    fail(f"tools/gen_enchanting.py could not be read: {exc}")

stats_dir = os.path.join(RES, f"data/{NS}/elysium_enchanting_stats")
shipped = {}
for name in sorted(os.listdir(stats_dir)) if os.path.isdir(stats_dir) else []:
    if name.endswith(".json"):
        shipped[name] = json_files.get(os.path.join(stats_dir, name), {})
if generator is not None:
    declared = generator.all_shelves()
    table = {row[0]: row for row in declared}
    if len(table) != len(declared):
        fail("two stat blocks in gen_enchanting.py name the same block; one of them is "
             "a file the other overwrites")
    if len(shipped) != len(declared):
        fail(f"{len(shipped)} stat file(s) shipped but the table declares "
             f"{len(declared)}; a hand-written one is a file the next run deletes")
    for block, eterna, quanta, arcana, rectification, max_eterna, why in declared:
        name = block.split(":", 1)[1] + ".json"
        entry = shipped.get(name)
        if entry is None:
            fail(f"the table declares {block} and no {name} ships")
            continue
        if entry.get("block") != block:
            fail(f"{name} names {entry.get('block')!r} and the table says {block!r}")
        for key, value in (("eterna", eterna), ("quanta", quanta), ("arcana", arcana),
                           ("rectification", rectification), ("max_eterna", max_eterna)):
            if float(entry.get(key, 0.0)) != float(value):
                fail(f"{name} says {key} is {entry.get(key, 0.0)} and the table says "
                     f"{value}; a shipped file was edited by hand")
        if not block.startswith("minecraft:") and not block.startswith(f"{NS}:"):
            fail(f"the table declares {block}, which is neither a vanilla block nor one "
                 f"this module ships. This module has to work on its own, and a shelf "
                 f"from a mod that is not installed is a shelf nobody can build")
        key = f"elysiumenchanting.shelf.{block.split(':', 1)[1]}"
        if key not in lang:
            fail(f"{block} ships without a sentence saying why it is worth what it is "
                 f"worth ({key})")
        if entry.get("_why") != why:
            fail(f"{name} carries a different reason than the table gives for it")
    answered = {"eterna": 0, "quanta": 0, "arcana": 0, "rectification": 0}
    for _block, eterna, quanta, arcana, rectification, _m, _w in declared:
        for key, value in (("eterna", eterna), ("quanta", quanta),
                           ("arcana", arcana), ("rectification", rectification)):
            if value > 0:
                answered[key] += 1
    for key, count in answered.items():
        if count == 0:
            fail(f"no block in the game supplies {key}, so one of the four numbers is a "
                 f"number nobody can build for")
        elif count < 3:
            fail(f"only {count} block(s) supply {key}, so a room built for it is not a "
                 f"build, it is that block repeated")
    if base_max is not None and not [row for row in declared if row[5] > base_max]:
        fail(f"no block raises a room's cap past {base_max:g}, which is what a player "
             f"starts able to hold — so the max_eterna attribute can never be the thing "
             f"holding them back and raising it does nothing")
    if not [row for row in declared if 0 < row[5] <= 15.0]:
        fail("nothing a player can build in their first hour raises a cap at all, so "
             "there is no first room")
    for block, eterna, quanta, arcana, rectification, max_eterna, _why in declared:
        if not (eterna or quanta or arcana or rectification or max_eterna):
            fail(f"{block} is worth nothing at all, so putting one next to a table would "
                 f"do nothing and a player would never find out why")
    made = len(generator.MADE_SHELVES)
    print(f"shelves            : {len(shipped)} stat block(s), {made} of them made, all "
          f"four numbers buildable, "
          f"{len([r for r in declared if r[5] > (base_max or 0)])} past what a player "
          f"starts with")

blocks_loaded = read(SRC, "data/EnchantingStatBlocks.java")
if '"elysium_enchanting_stats"' not in blocks_loaded:
    fail("EnchantingStatBlocks does not read the directory the stat files ship in")
if "extends SimpleJsonResourceReloadListener" not in blocks_loaded:
    fail("the stat blocks are not a reload listener, so a pack changing them takes a "
         "restart and /reload lies to whoever ran it")

# ---------------------------------------------------------------------------
# 7. The table itself: offers, clues, and what buying one costs
# ---------------------------------------------------------------------------
menu = read(SRC, "menu/ArcaneTableMenu.java")
menu_code = strip_comments(menu)
changed = method_body(menu_code, "public void slotsChanged(")
bought = method_body(menu_code, "public boolean clickMenuButton(")
if not changed or not bought:
    fail("ArcaneTableMenu no longer both reads the room and sells an offer")
else:
    if "long seed" not in changed or "seed + slot" not in changed:
        fail("the three offers are not seeded, so closing and reopening the screen "
             "rerolls them and the clues are worth nothing")
    if re.search(r"\blevel\.random\b", changed):
        fail("the offers are drawn from the world's random rather than from a seed, so "
             "they change every time the screen is opened")
    if "EnchantingRules.Stats stats = EnchantingSurvey.survey(level, pos, owner);" not in changed:
        fail("the menu no longer reads the room, so the blocks around the table do "
             "nothing")
    if "target.isEnchantable()" not in changed:
        fail("the table offers enchantments for things that cannot take one")
    if "int rolled = EnchantingRules.rolledLevel(stats, EnchantingRules.offerLevel(stats, slot), " \
            "level.random::nextDouble);" not in " ".join(bought.split()):
        fail("uncertainty is never applied, so quanta is a number on a screen and "
             "rectification is a thing to build for that does nothing")
    if "player.onEnchantmentPerformed(target, cost);" not in bought:
        fail("buying an offer costs no experience")
    if "inputs.removeItem(1, cost)" not in bought:
        fail("buying an offer costs no lapis")
    if "if (!player.getAbilities().instabuild) {" not in bought:
        fail("creative mode is charged for an enchantment, which no other table in the "
             "game does")
    if "target.enchant(instance.enchantment, instance.level);" not in bought:
        fail("buying an offer does not enchant anything")
clues = method_body(menu_code, "public List<EnchantmentInstance> clues(")
if "int shown = Math.min(all.size(), EnchantingRules.clues(current(), slot));" not in clues:
    fail("the screen decides for itself how much to tell a player, so the rule the "
         "harness checks is not the rule the game uses")
if "EnchantmentTags.IN_ENCHANTING_TABLE" not in menu_code:
    fail("the table draws from its own list of enchantments rather than the game's, so "
         "an enchantment another mod added can never appear at it")
if "EnchantingRules.available(" not in menu_code:
    fail("arcana never removes anything from an offer, so a room built for rarity is a "
         "room built for nothing")

block_java = read(SRC, "block/ArcaneTableBlock.java")
if "class ArcaneTableBlock extends Block" not in strip_comments(block_java):
    fail("the Arcane Table is no longer a block in its own right; whatever it became, "
         "the design is a second table standing beside vanilla's")
for path, raw in sources.items():
    code = strip_comments(raw, keep_strings=True)
    if "Blocks.ENCHANTING_TABLE" in code or "registerOverride" in code:
        fail(f"{os.path.relpath(path, WORK)} reaches for vanilla's enchanting table. "
             f"This module stands beside it: thousands of recipes, advancements and "
             f"structures name that block, and replacing it changes all of them at once "
             f"with no way back")

# ---------------------------------------------------------------------------
# 8. What a player can get hold of
# ---------------------------------------------------------------------------
registered = re.findall(r'BLOCKS\.register\(\s*"(\w+)"', strip_comments(blocks_java, True))
registered += re.findall(r'\bshelf\(\s*"(\w+)"', strip_comments(blocks_java, True))
if not registered:
    fail("EnchantingBlocks registers no blocks at all")
pickaxe = json_files.get(os.path.join(RES, "data/minecraft/tags/block/mineable/pickaxe.json"), {})
axe = json_files.get(os.path.join(RES, "data/minecraft/tags/block/mineable/axe.json"), {})
iron = json_files.get(os.path.join(RES, "data/minecraft/tags/block/needs_iron_tool.json"), {})
if generator is not None:
    benches = [name for name, *_ in generator.BLOCKS]
    made = [row[0] for row in generator.MADE_SHELVES]
    for name in registered:
        if name not in benches and name not in made:
            fail(f"{name} is registered in Java and the generator has never heard of it, "
                 f"so it ships with no model, no recipe and no name")
    for name in benches + made:
        if name not in registered:
            fail(f"{name} has files generated for it and nothing registers it, so the "
                 f"files are dead weight in the jar")
    for name in benches:
        if f"{NS}:{name}" not in pickaxe.get("values", []) \
                or f"{NS}:{name}" not in iron.get("values", []):
            fail(f"{name} requires the correct tool and is in no tool's tag, so no tool "
                 f"is ever correct and it drops nothing however it is mined")
    for name in made:
        if f"{NS}:{name}" not in axe.get("values", []):
            fail(f"{name} is a shelf and is in no tool's tag, so it takes as long to "
                 f"break as stone and gives an axe nothing to do")
for name in registered:
    for what, path in (
            ("blockstate", f"assets/{NS}/blockstates/{name}.json"),
            ("block model", f"assets/{NS}/models/block/{name}.json"),
            ("item model", f"assets/{NS}/models/item/{name}.json"),
            ("texture", f"assets/{NS}/textures/block/{name}.png"),
            ("loot table", f"data/{NS}/loot_table/blocks/{name}.json"),
            ("recipe", f"data/{NS}/recipe/{name}.json")):
        if not os.path.isfile(os.path.join(RES, path)):
            fail(f"{name} has no {what} ({path})")
    if f"block.{NS}.{name}" not in lang:
        fail(f"{name} has no name, so a player sees block.{NS}.{name}")
    item_call = strip_comments(blocks_java, True)
    if f'blockItem("{name}"' not in item_call \
            and f'ITEMS.registerItem("{name}"' not in item_call:
        fail(f"{name} has no item, so it cannot be held, crafted or put in a tab")
helper = method_body(strip_comments(blocks_java), "private static DeferredItem<Item> blockItem(")
if helper and "TAB_ORDER.add(item)" not in helper:
    fail("a block's item is registered without being added to the tab, so the one place "
         "that was meant to do both does one")
if "CreativeModeTab" not in blocks_java:
    fail("nothing this module adds is in a creative tab, so a creative player has to "
         "know the item id to get one")
if "itemGroup.elysiumenchanting" not in lang:
    fail("the creative tab has no name")
if "TABS.register(modBus)" not in strip_comments(blocks_java):
    fail("the creative tab is built and never registered")

# ---------------------------------------------------------------------------
# 9. The ladder: what there is to spend a room's power on
# ---------------------------------------------------------------------------
vanilla_dir = os.path.join(WORK, "tools/vanilla_enchantments")
shipped_ench = os.path.join(RES, "data/minecraft/enchantment")
UNTOUCHED = ("description", "effects", "slots", "supported_items", "primary_items",
             "weight", "anvil_cost", "exclusive_set")
if generator is not None:
    ladder = {name: (level, why) for name, level, why in generator.LADDER}
    if len(ladder) != len(generator.LADDER):
        fail("an enchantment is listed twice in the ladder; one row is the row the "
             "other overwrites")
    on_disk = sorted(name[:-5] for name in os.listdir(shipped_ench)
                     if name.endswith(".json")) if os.path.isdir(shipped_ench) else []
    for name in on_disk:
        if name not in ladder:
            fail(f"data/minecraft/enchantment/{name}.json overrides a vanilla enchantment "
                 f"that the ladder never asked to change; a file shipped under "
                 f"minecraft's own namespace replaces the game's and has to be asked for")
    for name, (level, _why) in sorted(ladder.items()):
        source = os.path.join(vanilla_dir, f"{name}.json")
        if not os.path.isfile(source):
            fail(f"the ladder re-paces {name}, which is not a vanilla enchantment this "
                 f"repository has a copy of; there is nothing to re-pace and nothing to "
                 f"check the result against")
            continue
        original = json.load(open(source, encoding="utf-8"))
        entry = json_files.get(os.path.join(shipped_ench, f"{name}.json"))
        if entry is None:
            fail(f"the ladder re-paces {name} and no file ships, so the enchantment is "
                 f"still vanilla's and the row does nothing")
            continue
        if original["max_level"] <= 1:
            fail(f"{name} is a statement rather than an amount in vanilla and the ladder "
                 f"gave it rungs; a second level of it would mean nothing to a player")
        if entry.get("max_level") != level:
            fail(f"{name} ships with max_level {entry.get('max_level')} and the ladder "
                 f"says {level}; a shipped file was edited by hand")
        elif level <= original["max_level"]:
            fail(f"{name} is re-paced to {level}, which is no further than vanilla's "
                 f"{original['max_level']}: the file is shipped, the game's own is "
                 f"replaced, and nothing about the enchantment changed")
        for key in UNTOUCHED:
            if original.get(key) != entry.get(key):
                fail(f"{name} ships with its {key} changed from vanilla's. The ladder "
                     f"changes how far an enchantment goes, not what it does — a mod "
                     f"that quietly rewrites Sharpness has made a change nobody was told "
                     f"about")
        low, high = entry.get("min_cost", {}), entry.get("max_cost", {})
        step = low.get("per_level_above_first", 0)
        top = low.get("base", 0) + step * (entry.get("max_level", 1) - 1)
        if base_max is not None and top > base_max:
            fail(f"{name}'s top rung needs {top} power and a player who has raised "
                 f"nothing can hold {base_max:g}; it is a level nobody has ever stood on")
        if low.get("base", 0) > 25:
            fail(f"{name}'s first rung needs {low.get('base')} power, which is not a "
                 f"first rung")
        if high.get("base", 0) - low.get("base", 0) < step:
            fail(f"{name}'s band between its cheapest and dearest power is narrower than "
                 f"the step between its levels, so some amounts of power reach no level "
                 f"of it at all and the table offers nothing for no visible reason")
        if high.get("per_level_above_first") != step:
            fail(f"{name}'s two cost curves climb at different rates, so the band widens "
                 f"or closes as the levels go up rather than moving with them")
    tag_path = os.path.join(RES, "data/minecraft/tags/enchantment/in_enchanting_table.json")
    tag = json_files.get(tag_path)
    if tag is None:
        fail("nothing is added to the enchanting table's own tag, so the treasure this "
             "module means a built room to reach is still trade-only")
    else:
        if tag.get("replace"):
            fail("the enchanting table's tag is shipped with replace set, which takes "
                 "every other mod's enchantments off every table in the game")
        listed = [value.split(":", 1)[-1] for value in tag.get("values", [])]
        for name in generator.FROM_THE_TABLE:
            if name not in listed:
                fail(f"{name} is meant to be reachable from a built room and is not in "
                     f"the tag")
            source = os.path.join(vanilla_dir, f"{name}.json")
            if os.path.isfile(source):
                weight = json.load(open(source, encoding="utf-8")).get("weight", 10)
                if weight > 2:
                    fail(f"{name} is put on the table with a weight of {weight}, which "
                         f"EnchantingRules.available lets through to any table at all; "
                         f"treasure has to be gated by arcana or it is not treasure")
    print(f"the ladder         : {len(ladder)} enchantment(s) re-paced, "
          f"{len(generator.FROM_THE_TABLE)} treasure(s) behind arcana, "
          f"everything else vanilla's")

# ---------------------------------------------------------------------------
# 10. The module's own enchantments
# ---------------------------------------------------------------------------
#
# The names the game actually has. An effect component or an effect type with
# a typo in it is not an error the game reports as a typo: the file fails to
# parse, the enchantment silently does not exist, and the first anybody hears
# of it is an enchanting table that never offers it.
COMPONENTS = {
    "minecraft:ammo_use", "minecraft:armor_effectiveness", "minecraft:attributes",
    "minecraft:block_experience", "minecraft:crossbow_charge_time",
    "minecraft:crossbow_charging_sounds", "minecraft:damage", "minecraft:damage_immunity",
    "minecraft:damage_protection", "minecraft:equipment_drops",
    "minecraft:fishing_luck_bonus", "minecraft:fishing_time_reduction",
    "minecraft:item_damage", "minecraft:location_changed", "minecraft:mob_experience",
    "minecraft:post_attack", "minecraft:prevent_armor_change",
    "minecraft:prevent_equipment_drop", "minecraft:projectile_count",
    "minecraft:projectile_piercing", "minecraft:projectile_spawned",
    "minecraft:projectile_spread", "minecraft:repair_with_xp",
    "minecraft:smash_damage_per_fallen_block", "minecraft:tick",
    "minecraft:trident_return_acceleration", "minecraft:trident_sound",
    "minecraft:trident_spin_attack_strength",
}
EFFECT_TYPES = {
    "minecraft:add", "minecraft:all_of", "minecraft:apply_mob_effect",
    "minecraft:attribute", "minecraft:change_item_damage", "minecraft:damage_entity",
    "minecraft:damage_item", "minecraft:explode", "minecraft:ignite",
    "minecraft:multiply", "minecraft:play_sound", "minecraft:remove_binomial",
    "minecraft:replace_block", "minecraft:replace_disk", "minecraft:run_function",
    "minecraft:set", "minecraft:set_block_properties", "minecraft:spawn_particles",
    "minecraft:summon_entity",
    # the value shapes an effect's numbers are written in
    "minecraft:clamped", "minecraft:enchantment_level", "minecraft:fraction",
    "minecraft:levels_squared", "minecraft:linear", "minecraft:lookup",
    "minecraft:simple_state_provider", "minecraft:matching_blocks",
    "minecraft:matching_block_tag", "minecraft:matching_fluids",
    "minecraft:unobstructed", "minecraft:uniform", "minecraft:in_bounding_box",
    "entity_position", "in_bounding_box",
}
ENCHANTABLE = {"armor", "bow", "chest_armor", "crossbow", "durability", "equippable",
               "fire_aspect", "fishing", "foot_armor", "head_armor", "leg_armor",
               "mace", "mining", "mining_loot", "sharp_weapon", "sword", "trident",
               "vanishing", "weapon"}

ours_dir = os.path.join(RES, f"data/{NS}/enchantment")
if generator is not None:
    mine = {entry["name"]: entry for entry in generator.NEW_ENCHANTMENTS}
    if len(mine) != len(generator.NEW_ENCHANTMENTS):
        fail("two of this module's enchantments have the same name; one file is the "
             "file the other overwrites")
    if len(mine) < 18:
        fail(f"{len(mine)} new enchantment(s) ship and the phase this module answers "
             f"has eighteen; the rest are missing rather than declined")
    shipped_ours = sorted(name[:-5] for name in os.listdir(ours_dir)
                          if name.endswith(".json")) if os.path.isdir(ours_dir) else []
    for name in shipped_ours:
        if name not in mine:
            fail(f"data/{NS}/enchantment/{name}.json is not in the table, so the next "
                 f"run deletes it")
    for name, entry in sorted(mine.items()):
        body = json_files.get(os.path.join(ours_dir, f"{name}.json"))
        if body is None:
            fail(f"{name} is declared and no file ships, so the enchantment does not exist")
            continue
        if body.get("max_level") != entry["max_level"] or body.get("weight") != entry["weight"]:
            fail(f"{name} ships with a different level or weight than the table gives it; "
                 f"a shipped file was edited by hand")
        step_wanted = generator.pace(entry["first"], entry["max_level"])
        if body.get("min_cost", {}) != {"base": entry["first"],
                                        "per_level_above_first": step_wanted}:
            fail(f"{name} ships re-paced by hand: its first rung and its step are not "
                 f"the ones the table works out")
        if body.get("effects") != entry["effects"]:
            fail(f"{name} ships with different effects than the table gives it; a "
                 f"shipped file was edited by hand")
        for component, entries in (body.get("effects") or {}).items():
            if component not in COMPONENTS:
                fail(f"{name} uses the effect component {component!r}, which the game "
                     f"does not have: the file will not parse and the enchantment will "
                     f"simply not exist")
        def types(node):
            found = []
            if isinstance(node, dict):
                if isinstance(node.get("type"), str):
                    found.append(node["type"])
                for value in node.values():
                    found += types(value)
            elif isinstance(node, list):
                for value in node:
                    found += types(value)
            return found
        # Only the effects themselves: a requirement is a loot condition and its
        # "type" is an entity predicate's, which is a different vocabulary.
        applied = []
        for entries in (body.get("effects") or {}).values():
            for one in entries if isinstance(entries, list) else []:
                applied.append(one.get("effect") if isinstance(one, dict) else one)
                if isinstance(one, dict) and "amount" in one:
                    applied.append(one.get("amount"))
        for kind in types(applied):
            if kind not in EFFECT_TYPES:
                fail(f"{name} uses the effect type {kind!r}, which the game does not "
                     f"have")
        supported = body.get("supported_items", "")
        if not supported.startswith("#minecraft:enchantable/") \
                or supported.split("/")[-1] not in ENCHANTABLE:
            fail(f"{name} may go on {supported!r}, which is not a tag the game has, so "
                 f"it may go on nothing")
        if body.get("description", {}).get("translate") != f"enchantment.{NS}.{name}":
            fail(f"{name} does not name its own description key")
        for key, what in ((f"enchantment.{NS}.{name}", "a name"),
                          (f"enchantment.{NS}.{name}.desc", "a line saying what it is for")):
            if key not in lang:
                fail(f"{name} ships without {what} ({key})")
        low, high = body.get("min_cost", {}), body.get("max_cost", {})
        step = low.get("per_level_above_first", 0)
        top = low.get("base", 0) + step * (body.get("max_level", 1) - 1)
        if base_max is not None and top > base_max:
            fail(f"{name}'s top rung needs {top} power and a player who has raised "
                 f"nothing can hold {base_max:g}; it is a level nobody has ever stood on")
        if low.get("base", 0) > 25:
            fail(f"{name}'s first rung needs {low.get('base')} power, which is not a "
                 f"first rung")
        if high.get("base", 0) - low.get("base", 0) < step:
            fail(f"{name}'s band is narrower than the step between its levels, so some "
                 f"amounts of power reach no level of it at all")
        if high.get("per_level_above_first") != step:
            fail(f"{name}'s two cost curves climb at different rates")
        if body.get("weight", 10) >= 10:
            fail(f"{name} has a weight of {body.get('weight')}, which puts it on every "
                 f"table in the game: EnchantingRules.available only gates what is "
                 f"rarer than ordinary, and a new enchantment a bookshelf room hands "
                 f"out is not something to build for")
        exclusive = body.get("exclusive_set")
        if exclusive and exclusive.startswith(f"#{NS}:"):
            tag_file = os.path.join(RES, f"data/{NS}/tags/enchantment",
                                    exclusive.split(":", 1)[1] + ".json")
            if not os.path.isfile(tag_file):
                fail(f"{name} names the exclusive set {exclusive}, and there is no such "
                     f"tag, so it is exclusive with nothing")
            elif f"{NS}:{name}" not in json_files.get(tag_file, {}).get("values", []):
                fail(f"{name} names an exclusive set it is not itself in, so it excludes "
                     f"others and not itself")
    for what, path in (("offered at a table", "in_enchanting_table"),
                       ("found in a chest", "on_random_loot"),
                       ("sold by a villager", "tradeable")):
        tag = json_files.get(os.path.join(RES,
                                          f"data/minecraft/tags/enchantment/{path}.json"))
        if tag is None:
            fail(f"nothing this module adds is {what}: data/minecraft/tags/enchantment/"
                 f"{path}.json does not ship")
            continue
        listed = set(tag.get("values", []))
        for name in mine:
            if f"{NS}:{name}" not in listed:
                fail(f"{name} can never be {what}, so the only way to one is a command")
    print(f"new enchantments   : {len(mine)}, every effect a component the game has, "
          f"every one offered, found and sold")

# ---------------------------------------------------------------------------
# 11. Infusion: the certain path, and what it costs
# ---------------------------------------------------------------------------
infusion_rules = read(PURE, "InfusionRules.java")
basin = read(SRC, "menu/InfusionMenu.java")
basin_code = strip_comments(basin)
if not infusion_rules:
    fail("there is no InfusionRules; the certain path is either missing or its "
         "arithmetic is somewhere no harness can run it")
else:
    for name in ("ETERNA_PER_LEVEL", "COST_PER_LEVEL", "DISCOUNT", "LAPIS_PER_LEVEL"):
        if constant(name, strip_comments(infusion_rules)) is None:
            fail(f"InfusionRules no longer states {name}, which is one of the numbers "
                 f"that decides what certainty costs")
    if "EnchantingRules.available(" not in infusion_rules:
        fail("infusion has a rarity gate of its own rather than the table's, so a book "
             "and a bench route straight past the one number that was meant to be hard "
             "to raise")
planned = method_body(basin_code, "private Plan plan(")
bought = method_body(basin_code, "public boolean clickMenuButton(")
changed = method_body(basin_code, "public void slotsChanged(")
if not planned or not bought:
    fail("InfusionMenu no longer both works out what it would do and does it")
else:
    if "EnchantingRules.Stats stats = EnchantingSurvey.survey(level, pos, owner);" not in changed:
        fail("the basin does not read the room, so the blocks around it do nothing and "
             "the two benches are not two rooms")
    if "enchantment.value().canEnchant(gear)" not in planned:
        fail("the basin never asks whether an enchantment may go on the item at all, so "
             "a book of Respiration lands on a shovel")
    if "if (!InfusionRules.allows(stats, enchantment.value().getWeight())) {" not in planned:
        fail("the basin never asks whether the room is strange enough, so every rare "
             "book in a chest is a rare enchantment for nothing")
    if "if (!worn.equals(enchantment) && !Enchantment.areCompatible(worn, enchantment)) {" \
            not in planned:
        fail("the basin never asks whether an enchantment argues with what is already "
             "on the gear, so Silk Touch and Fortune end up on one pickaxe")
    if "enchantment.value().getMaxLevel()" not in planned:
        fail("the basin does not hold a landing to the enchantment's own maximum, so a "
             "book edited by a command outranks the enchantment's own file")
    if "int wanted = InfusionRules.levelFor(entry.getIntValue(), stats.eterna(), " \
            "enchantment.value().getMaxLevel());" not in " ".join(planned.split()):
        fail("the basin decides for itself how high a book lands, so the rule the "
             "harness checks is not the rule the game uses")
    if "plan(stats)" not in bought:
        fail("buying works from something other than a freshly worked-out plan")
    if "readout.get(" in bought:
        fail("the purchase reads the numbers that were sent to the client. A client can "
             "be told anything; the server works the price out again")
    if "inputs.removeItem(1, 1)" not in bought:
        fail("the book survives being infused, so one book enchants every piece of gear "
             "a player owns and the only question left is how long they will stand there")
    if "player.onEnchantmentPerformed(gear, plan.cost());" not in bought:
        fail("infusing costs no experience")
    if "inputs.removeItem(2, plan.lapis())" not in bought:
        fail("infusing costs no lapis")
    if "player.getAbilities().instabuild" not in bought:
        fail("creative mode is charged for an infusion")
    if "plan.status() != READY" not in bought:
        fail("the button acts whatever the bench said it would do")
screen = read(SRC, "client/InfusionScreen.java")
for status in ("too_weak", "too_rare", "wrong_item", "conflict", "nothing"):
    if f"{NS}.infusion.{status}" not in screen:
        fail(f"the basin can refuse for a reason it never shows ({status}), so a player "
             f"is told no and not what to change")

# Every menu this module registers is a menu something can draw.
menus = read(SRC, "registry/EnchantingMenus.java")
client = read(SRC, "client/EnchantingClient.java")
for constant_name in re.findall(r"MenuType<\w+>>\s+(\w+)\s*=", strip_comments(menus)):
    if f"EnchantingMenus.{constant_name}.get()" not in client:
        fail(f"the {constant_name} menu has no screen registered for it, so opening that "
             f"block disconnects the player rather than showing them anything")
print("infusion           : the certain path, priced, gated and paid for")

# ---------------------------------------------------------------------------
# 12. The library: books in, one better book out
# ---------------------------------------------------------------------------
library_rules = read(PURE, "LibraryRules.java")
shelf_entity = read(SRC, "block/ScriptoriumBlockEntity.java")
shelf_entity_code = strip_comments(shelf_entity, keep_strings=True)
shelf_menu = read(SRC, "menu/ScriptoriumMenu.java")
shelf_menu_code = strip_comments(shelf_menu, keep_strings=True)
entities = read(SRC, "registry/EnchantingBlockEntities.java")
if not library_rules:
    fail("there is no LibraryRules; the library's arithmetic is either missing or "
         "somewhere no harness can run it")
else:
    tax = constant("COPY_TAX", strip_comments(library_rules))
    if tax is None or tax <= 1:
        fail("a copy costs no more than filing one, so the library duplicates books and "
             "every enchanted book in the world becomes every other one")
    if "1L << (held - 1)" not in library_rules:
        fail("a book's worth no longer doubles with its level, so 'four make the next "
             "one up' — the one sentence the whole economy is explained with — is no "
             "longer true")
if not shelf_entity or not shelf_menu:
    fail("the Scriptorium has no block entity or no menu, so it holds nothing or "
         "nothing can be put in it")
else:
    saved = method_body(shelf_entity_code, "protected void saveAdditional(")
    loaded = method_body(shelf_entity_code, "protected void loadAdditional(")
    if '"shelf"' not in saved or '"shelf"' not in loaded:
        fail("the Scriptorium does not both write and read its shelf, so everything "
             "filed in it is lost on the next restart")
    if "getUpdateTag" not in shelf_entity_code or "getUpdatePacket" not in shelf_entity_code:
        fail("the shelf is never sent to the client, so the screen lists nothing and a "
             "player cannot see what they have filed")
    if "scriptorium.size().capacity" not in shelf_entity_code:
        fail("both sizes of Scriptorium hold the same amount, so the great one is a "
             "recipe with nothing at the end of it")
    filing = method_body(shelf_menu_code, "private void file(")
    buying = method_body(shelf_menu_code, "private void buy(")
    if "if (id.isEmpty() || !LibraryRules.room(shelf.pagesOf(id), entry.getIntValue(), " \
            "shelf.capacity())) {" not in " ".join(filing.split()):
        fail("the Scriptorium files a book without asking whether it has room, so a "
             "full shelf eats the best book a player owns")
    if "books.removeItem(0, 1)" not in filing:
        fail("filing a book does not consume it, so one book fills a shelf")
    if "int best = LibraryRules.highest(shelf.pagesOf(chosen), maxLevel(level, chosen));" \
            not in buying:
        fail("the Scriptorium decides for itself what a shelf will buy, so the rule the "
             "harness checks is not the rule the game uses")
    if "maxLevel(level, chosen)" not in buying:
        fail("a copy is bought without asking what the enchantment's own file allows")
    if "player.giveExperienceLevels(-LibraryRules.experience(best));" not in buying:
        fail("a copy costs no experience, so a filled shelf is a vending machine")
    if "shelf.take(chosen, best)" not in buying:
        fail("a copy is handed over without the pages being spent")
    if "instabuild" not in buying:
        fail("creative mode is charged for a copy")
    if "Items.BOOK" not in buying:
        fail("a copy is made out of nothing rather than out of a book")
for name in re.findall(r'BLOCKS\.register\(\s*"(\w+)",\s*\n?\s*\(\)\s*->\s*new ScriptoriumBlock',
                       strip_comments(blocks_java, True)):
    constant_name = name.upper()
    if f"EnchantingBlocks.{constant_name}.get()" not in entities:
        fail(f"{name} is a Scriptorium and is not one of the blocks its block entity "
             f"type accepts, so placing one and opening it throws rather than opening")
print("the library        : books in, one better book out, and the shelf survives a restart")

# ---------------------------------------------------------------------------
# 13. The anvil, and the four tomes
# ---------------------------------------------------------------------------
anvil_rules = read(PURE, "AnvilRules.java")
anvil = read(SRC, "event/EnchantingAnvil.java")
anvil_code = strip_comments(anvil, keep_strings=True)
items_java = read(SRC, "registry/EnchantingItems.java")
if not anvil_rules or not anvil:
    fail("the anvil is untouched: either its arithmetic or the handler that uses it is "
         "missing, and the wall every long world hits is still there")
else:
    cap = constant("VANILLA_CAP", strip_comments(anvil_rules))
    if cap is None:
        fail("AnvilRules no longer states the cap it exists to answer")
    wall = method_body(anvil_code, "private static void wall(")
    if not wall:
        fail("nothing handles the operation vanilla refuses, so 'Too Expensive!' still "
             "ends the life of every piece of gear a player cares about")
    else:
        if "AnvilRules.refusedByVanilla(cost)" not in wall:
            fail("the handler computes every anvil operation rather than only the ones "
                 "vanilla refuses. An anvil that behaves differently in the ordinary "
                 "case is a bug report nobody can reproduce")
        if "return;" not in wall:
            fail("the handler never leaves an operation to vanilla")
        if "if (!worn.equals(enchantment) && !Enchantment.areCompatible(worn, enchantment)) {" \
                not in wall:
            fail("the lifted cap lets incompatible enchantments onto one item, which is "
                 "a thing no anvil in the game has ever done")
        if "canEnchant(output)" not in wall:
            fail("the lifted cap puts enchantments on items that cannot take them")
        if "enchantment.value().getMaxLevel()" not in wall:
            fail("the lifted cap ignores the enchantment's own maximum, so the cost is "
                 "the only thing that was ever holding a level back")
        if "output.set(DataComponents.REPAIR_COST, " \
                "AnvilRules.nextPriorWork(priorWork(left), priorWork(right)));" \
                not in " ".join(wall.split()):
            fail("an item worked on past the cap does not age, so the prior-work penalty "
                 "is gone rather than answered and the anvil is free forever after")
        if "definition().anvilCost()" not in wall:
            fail("the price ignores what the enchantment itself says it costs on an "
                 "anvil, so every enchantment is priced the same")
    for what, method in (("scrapping", "private static boolean scrap("),
                         ("transcription", "private static boolean transcribe("),
                         ("concentration", "private static boolean concentrate("),
                         ("patience", "private static boolean forgive(")):
        if not method_body(anvil_code, method):
            fail(f"the {what} tome has no behaviour behind it, so it is an item that "
                 f"does nothing on an anvil")
    scrapping = method_body(anvil_code, "private static boolean scrap(")
    if "ItemEnchantments.EMPTY" not in scrapping:
        fail("the scrapping tome leaves enchantments on the item, which is the one "
             "thing it is for")
    transcribing = method_body(anvil_code, "private static boolean transcribe(")
    if "DataComponents.STORED_ENCHANTMENTS" not in transcribing:
        fail("the transcription tome does not write the enchantments onto the book")
    narrowing = method_body(anvil_code, "private static boolean concentrate(")
    if "on.size() < 2" not in narrowing:
        fail("the concentration tome narrows a book that has nothing to narrow, so it "
             "is spent for nothing")
    forgiving = method_body(anvil_code, "private static boolean forgive(")
    if "AnvilRules.forgiven()" not in forgiving:
        fail("the patience tome sets an item's history to a number of its own rather "
             "than to what the rule says")
    if "history <= 0" not in forgiving:
        fail("the patience tome is spent on an item with no history to clear")
    finishing = method_body(anvil_code, "private static void finish(")
    if "event.setMaterialCost(1)" not in finishing:
        fail("a tome is not consumed by the anvil, so one of them does every job in "
             "the world forever")

registered_items = re.findall(r'ITEMS\.registerItem\(\s*(\w+)', strip_comments(items_java, True))
tomes = re.findall(r'tome\("(\w+)"\)', strip_comments(items_java, True))
if not tomes:
    fail("EnchantingItems registers no tomes")
for name in tomes:
    for what, path in (("item model", f"assets/{NS}/models/item/{name}.json"),
                       ("texture", f"assets/{NS}/textures/item/{name}.png"),
                       ("recipe", f"data/{NS}/recipe/{name}.json")):
        if not os.path.isfile(os.path.join(RES, path)):
            fail(f"{name} has no {what} ({path})")
    if f"item.{NS}.{name}" not in lang:
        fail(f"{name} has no name, so a player sees item.{NS}.{name}")
    if f"{NS}.tooltip.{name}" not in lang:
        fail(f"{name} says nothing about itself, and an anvil gives no hint until the "
             f"right two things are in it")
    if name.upper() not in anvil_code:
        fail(f"{name} is registered and the anvil has never heard of it")
if "EnchantingBlocks.inTab(item)" not in strip_comments(items_java):
    fail("the tomes are not in the creative tab, so a creative player has to know the "
         "item id to get one")
print(f"the anvil          : {len(tomes)} tome(s), and the wall lifted only where vanilla "
      f"refuses")

# ---------------------------------------------------------------------------
# 14. What the client says, and where this module joins the rest of Elysium
# ---------------------------------------------------------------------------
tooltips = read(SRC, "client/EnchantingTooltips.java")
tooltips_code = strip_comments(tooltips, keep_strings=True)
if not tooltips:
    fail("nothing this module adds says anything on a tooltip, so twenty enchantments "
         "nobody has met before are twenty names")
else:
    # Both lines ask, and the block one is the one that would otherwise put a
    # raw key under every block in the game rather than only under this mod's.
    if tooltips_code.count("I18n.exists(") < 2:
        fail("a tooltip line prints its key without asking whether anything translates "
             "it, so every block in the game gets a raw key under its name")
    if "shelfKey(" not in tooltips_code or "describes(" not in tooltips_code:
        fail("the tooltip no longer says both of the two things it is for: what a block "
             "is worth to a table, and what an enchantment does")
    if "hasShiftDown()" not in tooltips_code:
        fail("every enchantment's description is shown at all times, which is a tooltip "
             "a player cannot see past on a piece of gear with six things on it")
    if "@EventBusSubscriber" not in tooltips or "Dist.CLIENT" not in tooltips:
        fail("the tooltip handler is not client-only, so a server loads a class that "
             "reads the language file")
survey_tier = method_body(survey_code, "public static EnchantingRules.Stats survey(")
if "playerMax += EnchantingRules.tierBonus( com.elysium.lib.tier.ElysiumTiers.of(player));" \
        not in " ".join(survey_tier.split()):
    fail("a player's world tier adds nothing to what their table may hold, so the one "
         "measure every other system in this mod scales against stops mattering here")
if "ElysiumTiers.of(player)" not in survey_tier:
    fail("the survey works out a world tier of its own rather than asking the library")
if constant("ETERNA_PER_TIER") is None:
    fail("EnchantingRules no longer states what a world tier is worth at a table")
print("client and tiers   : two tooltips, and a ceiling that rises with the world tier")

OWNED = set(registered) | set(tomes)

# ---------------------------------------------------------------------------
# 15. Advancements: a tree a player can actually climb
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
# 16. Every literal key this module shows has words behind it
# ---------------------------------------------------------------------------
shown = set()
for path, raw in sources.items():
    for key in re.findall(r'"((?:block|item|attribute|itemGroup|enchantment'
                          r'|elysiumenchanting)[\w.]*\.[\w.]+)"', raw):
        if key.endswith(".") or "%s" in key:
            continue
        shown.add((os.path.relpath(path, WORK), key))
for rel, key in sorted(shown):
    if key.startswith("attribute.name.") or key.startswith("block.") \
            or key.startswith("itemGroup.") or key.startswith(f"item.{NS}.") \
            or key.startswith(f"{NS}."):
        if key not in lang:
            fail(f"{rel} shows {key} and nothing translates it, so a player reads a raw key")
# The screen builds two families of key from a name rather than writing them out.
for stat in ("eterna", "quanta", "arcana", "rectification"):
    if f"{NS}.stat.{stat}" not in lang:
        fail(f"the screen shows {NS}.stat.{stat} and nothing translates it")
for part in ("level", "and"):
    if f"{NS}.offer.{part}" not in lang:
        fail(f"the screen shows {NS}.offer.{part} and nothing translates it")

# ---------------------------------------------------------------------------
# The Tome Tower
# ---------------------------------------------------------------------------
#
# A surface structure is the one place a player meets this module before they
# have decided to look for it: they see a tower on a hill, climb it, and are
# standing in a seven-by-seven shelf room with a table in the corner. That only
# works if the tower is actually built - a template with no floors, no ladder
# or no way in is a ruin, and the player who climbs it learns the wrong thing.
#
# So the templates are read back out of their own NBT and checked as buildings.

sys.path.insert(0, os.path.join(WORK, "tools"))
import nbt as _nbt                                          # noqa: E402

TOWERS = {"tome_tower_small": 3, "tome_tower": 4, "tome_tower_great": 5}
TOWER_DIR = os.path.join(RES, f"data/{NS}/structure")

_tower_blocks = 0
for _name, _floors in TOWERS.items():
    _path = os.path.join(TOWER_DIR, _name + ".nbt")
    if not os.path.isfile(_path):
        fail(f"{_name}.nbt is missing, so the structure that names it places nothing")
        continue
    try:
        _, _root = _nbt.load(_path)
    except Exception as _error:
        fail(f"{_name}.nbt is not readable as a structure template: {_error}")
        continue
    _body = _root.value
    for _key in ("size", "palette", "blocks", "entities", "DataVersion"):
        if _key not in _body:
            fail(f"{_name}.nbt has no {_key}; the game will refuse it")
    if "size" not in _body or "blocks" not in _body or "palette" not in _body:
        continue
    _size = [t.value for t in _body["size"].value]
    _names = [p.value["Name"].value for p in _body["palette"].value]
    _grid = {}
    for _block in _body["blocks"].value:
        _x, _y, _z = [t.value for t in _block.value["pos"].value]
        _grid[(_x, _y, _z)] = _names[_block.value["state"].value]
    _tower_blocks += len(_grid)

    if _size[0] != _size[2]:
        fail(f"{_name} is not square, so its four walls cannot share one rhythm")
    _expected = _floors * 5 + 3
    if _size[1] != _expected:
        fail(f"{_name} is {_size[1]} blocks tall and {_floors} floors wants "
             f"{_expected}; one of the two was changed without the other")

    # A floor of boards every five blocks, and a ladder joining all of them.
    for _floor in range(_floors + 1):
        _y = _floor * 5
        _middle = _size[0] // 2
        if _grid.get((_middle, _y, _middle)) != "minecraft:spruce_planks":
            fail(f"{_name} has no floor at height {_y}, so a climber arrives in a shaft")
    _rungs = sum(1 for _y in range(1, _floors * 5 + 1)
                 if _grid.get((1, _y, 1)) == "minecraft:ladder")
    if _rungs != _floors * 5:
        fail(f"{_name}'s ladder has {_rungs} rungs and needs {_floors * 5}; a tower "
             f"nobody can climb is a wall with books in it")

    # A way in, and a whole one: a door is two blocks, and half a door is a
    # hole with a texture in it.
    _door = [_at for _at, _block in _grid.items() if _block == "minecraft:oak_door"]
    if len(_door) < 2:
        fail(f"{_name} has {len(_door)} block(s) of door where a door is two, so the "
             f"only way in is through a wall")
    elif not any(_x in (0, _size[0] - 1) or _z in (0, _size[2] - 1)
                 for _x, _y, _z in _door):
        fail(f"{_name}'s door is not in an outside wall, so it opens onto nothing")

    # The things the tower exists to show.
    _shelves = {_block for _block in _grid.values()
                if _block.startswith(f"{NS}:") and "tome" not in _block}
    if len([_s for _s in _shelves if "shelf" in _s or "column" in _s]) < 2:
        fail(f"{_name} contains fewer than two of this module's shelves, so climbing it "
             f"teaches a player nothing about the system it belongs to")
    if f"{NS}:arcane_table" not in _grid.values():
        fail(f"{_name} has no table, so the room it builds cannot be used in")
    if f"{NS}:scriptorium" not in _grid.values():
        fail(f"{_name} has no Scriptorium")
    if "minecraft:chest" not in _grid.values():
        fail(f"{_name} has no chest, so there is nothing in it to find")

    # And the chest has to be told what is in it.
    _told = False
    for _block in _body["blocks"].value:
        _data = _block.value.get("nbt")
        if _data is not None and "LootTable" in _data.value:
            if _data.value["LootTable"].value == f"{NS}:chests/tome_tower":
                _told = True
    if not _told:
        fail(f"{_name}'s chest names no loot table, so it generates empty")

_tower_data = {
    "structure": f"data/{NS}/worldgen/structure/tome_tower.json",
    "set": f"data/{NS}/worldgen/structure_set/tome_tower.json",
    "start": f"data/{NS}/worldgen/template_pool/tome_tower_start.json",
    "biomes": f"data/{NS}/tags/worldgen/biome/has_structure/tome_tower.json",
    "loot": f"data/{NS}/loot_table/chests/tome_tower.json",
}
_loaded = {}
for _what, _rel in _tower_data.items():
    _path = os.path.join(RES, _rel)
    if not os.path.isfile(_path):
        fail(f"the tower's {_what} file is missing ({_rel})")
    else:
        _loaded[_what] = json.load(open(_path, encoding="utf-8"))

if "structure" in _loaded:
    _structure = _loaded["structure"]
    if _structure.get("start_pool") != f"{NS}:tome_tower_start":
        fail("the tower structure starts from a pool other than its own")
    if _structure.get("step") != "surface_structures":
        fail(f"the tower is generated at {_structure.get('step')}; a surface structure "
             f"placed at another step is one buried in the hill it stands on")
    if _structure.get("biomes") != f"#{NS}:has_structure/tome_tower":
        fail("the tower's biome list is not the tag this module ships")

if "start" in _loaded:
    _elements = _loaded["start"].get("elements", [])
    _located = {_e["element"]["location"] for _e in _elements}
    if _located != {f"{NS}:{_n}" for _n in TOWERS}:
        fail(f"the start pool offers {len(_located)} of the three heights, so one of "
             f"the templates is built and never placed")
    if any(_e.get("weight", 0) <= 0 for _e in _elements):
        fail("a tower height is weighted at nothing, so it never appears")

if "set" in _loaded:
    _placement = _loaded["set"].get("placement", {})
    if _placement.get("separation", 0) >= _placement.get("spacing", 0):
        fail("the tower's separation is not smaller than its spacing, which the game "
             "refuses to load rather than quietly correcting")

if "biomes" in _loaded and not _loaded["biomes"].get("values"):
    fail("the tower's biome tag is empty, so it can generate nowhere")

if "loot" in _loaded:
    _named = json.dumps(_loaded["loot"])
    for _tome in ("transcription_tome", "patience_tome", "concentration_tome",
                  "scrapping_tome"):
        if _tome not in _named:
            fail(f"the tower's chest cannot give a {_tome}, so the Tome Tower is a "
                 f"tower without one of the tomes")
    if _loaded["loot"].get("pools", [{}])[0].get("rolls") != 1:
        fail("the tower's chest does not guarantee exactly one tome; the point of "
             "walking to it is that you know what is at the top")

print(f"the tome tower     : {len(TOWERS)} heights, {_tower_blocks} blocks, each "
      f"floored, laddered, shelved and stocked")

print(f"language           : {len(lang)} key(s), every literal one reachable")
print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all enchanting checks passed")
