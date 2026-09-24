#!/usr/bin/env python3
"""
Validate Elysium Arcana against its sources.

Run from anywhere:   python3 elysium-arcana/validate.py

A transmutation system has one way of being catastrophically wrong and it is
invisible to whoever wrote it: somewhere in a table of ten thousand derived
values there is an arrangement that **makes value out of nothing**. Craft A
into B, dissolve B, condense A, come out ahead. It is not a rare bug — it is
the default outcome of deriving ten thousand numbers from ten thousand recipes
and having nobody read the result.

Two rules make it impossible, and both are executed over the whole table on
every build rather than argued about here.

  1. INDEPENDENCE. The library, and not one thing it prices.
  2. PURITY, and the harness run rather than read.
  3. THE TABLE. Cheapest wins, nothing is worth nothing, nothing is profitable.
  4. THE TWO DIRECTIONS, and the difference between them.
  5. WHO DECIDES. The server, every time.
  6. ARCANA IN THE WORLD.
  7. THE SEAM.
"""
import json
import os
import re
import subprocess
import sys
import tempfile

WORK = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(WORK)
RES = os.path.join(WORK, "src/main/resources")
SRC = os.path.join(WORK, "src/main/java/com/elysium/arcana")
PURE = os.path.join(SRC, "rules")
BELOW = []
HARNESS = os.path.join(WORK, "tools/ArcanaHarness.java")
HARNESS_MAIN = "com.elysium.arcana.rules.ArcanaHarness"
GENERATOR = os.path.join(WORK, "tools/gen_arcana.py")

#: How long the arithmetic is allowed to take. Two million checks run in a few
#: seconds; anything past this is a loop.
HARNESS_SECONDS = 120
NS = "elysiumarcana"

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


def squashed(text):
    """Every run of whitespace as one space, so a rule can name a whole statement.

    Naming the whole statement is the point: a rule that looks for a token
    which survives the very edit it was meant to catch is a rule that has never
    failed, and this project has been bitten by that a dozen times.
    """
    return " ".join(text.split())


def body_of(text, signature):
    start = text.find(signature)
    if start < 0:
        return ""
    opened = text.find("{", start)
    if opened < 0:
        return ""
    depth = 0
    for index in range(opened, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[opened:index + 1]
    return text[opened:]


if not os.path.isdir(SRC):
    print("This is not the elysium-arcana tree.")
    sys.exit(1)

print("=" * 70)
print("elysium-arcana — turning things into other things, and the proof it never "
      "makes something out of nothing")
print("=" * 70)

# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------

FOREIGN = re.compile(
    r"(?:moze_intel|projecte|projectexpansion|it\.hurts\.sskirillss|relics)"
    r"|\b(?:projecte|projectexpansion|relics|geore)\s*:", re.I)
UPWARD = re.compile(r"com\.elysium\.(arsenal|bestiary|atelier|wilds|comforts|colonies)")

java_files = []
for dirpath, _, filenames in os.walk(SRC):
    for name in filenames:
        if name.endswith(".java"):
            java_files.append(os.path.join(dirpath, name))

for path in java_files:
    src = strip_comments(read(path))
    where = os.path.relpath(path, SRC)
    for hit in FOREIGN.findall(src):
        fail(f"{where} names {hit}; this module is a reimplementation of the mods it "
             f"replaces and must not reach for any of them")
    if UPWARD.search(src):
        fail(f"{where} reaches up into a later module; the dependencies run one way")
    for used in set(re.findall(r"com\.elysium\.(\w+)", src)):
        if used not in ("arcana", "lib"):
            fail(f"{where} names com.elysium.{used}; this module prices every item in "
                 f"the game and must not depend on any of the modules that add them")

toml = read(RES, "META-INF/neoforge.mods.toml")
declared = set(re.findall(r'modId="([^"]+)"', toml))
allowed = {"${mod_id}", "neoforge", "minecraft", "elysiumlib"}
for mod in declared - allowed:
    fail(f"the module declares a dependency on {mod}; it prices whatever is installed "
         f"and must not require any of it")
for needed in ("neoforge", "minecraft", "elysiumlib"):
    if needed not in declared:
        fail(f"the module does not declare {needed}")

print(f"independence       : {len(java_files)} source file(s), nothing it replaces "
      f"named, and not one of the modules whose items it prices")

# ---------------------------------------------------------------------------
# 2. Purity, and the harness executed
# ---------------------------------------------------------------------------

for name in sorted(os.listdir(PURE)) if os.path.isdir(PURE) else []:
    if not name.endswith(".java"):
        continue
    body = read(PURE, name)
    without_package = re.sub(r"^\s*package [^;]+;", "", body, count=1, flags=re.M)
    for forbidden in ("net.minecraft", "net.neoforged", "com.mojang"):
        if forbidden in without_package:
            fail(f"rules/{name} reaches for {forbidden}; the rules are the JDK alone, "
                 f"so a harness can price ten thousand recipes without a game")
    for used in re.findall(r"import\s+(com\.elysium\.[\w.]+);", without_package):
        fail(f"rules/{name} imports {used}; the table is the JDK alone")

if not os.path.isfile(HARNESS):
    fail("there is no harness, so the one property this module rests on is checked by "
         "reading a table of ten thousand numbers, which nobody has ever done")
else:
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
            try:
                ran = subprocess.run(
                    ["java", "-cp", os.path.join(tmp, "out"), HARNESS_MAIN],
                    capture_output=True, text=True, timeout=HARNESS_SECONDS)
            except subprocess.TimeoutExpired:
                ran = None
                fail(f"the harness did not finish within {HARNESS_SECONDS} seconds, so "
                     f"settling the table can loop forever — which is a server that "
                     f"stops responding, not a wrong number")
            if ran is None:
                pass
            elif ran.returncode != 0:
                fail("the arcana harness failed:\n" + ran.stdout.strip()[:1600])
            else:
                print("the arithmetic     : " + ran.stdout.strip().splitlines()[-1])

rules = read(PURE, "ArcanaRules.java")
plain = strip_comments(rules)
squished = squashed(plain)


def constant(name, kind=r"(?:float|int|double)"):
    m = re.search(r"public static final " + kind + r"\s+" + name + r"\s*=\s*([^;]+);",
                  plain, re.S)
    return " ".join(m.group(1).split()) if m else None




# ---------------------------------------------------------------------------
# 3. The table
# ---------------------------------------------------------------------------
#
# Three statements, and every one of them is the difference between a working
# transmutation system and an infinite supply of diamonds.

deriving = squashed(body_of(plain, "public static Map<String, Long> derive("))
if "if (priced != null && (cheapest == null || priced < cheapest)) { cheapest = priced; }" \
        not in deriving:
    fail("the table does not take the cheapest way of arriving at a thing, so anything "
         "with two recipes is priced above what it actually costs — and the gap between "
         "the two is exactly what a player rides")
if "if (seeds.containsKey(entry.getKey())) { continue; }" not in deriving:
    fail("a decided value can be derived over, so the seed table stops meaning anything "
         "the moment somebody adds a recipe for cobblestone")
if "return Map.copyOf(closed(recipes, worth, seeds));" not in deriving:
    fail("the table is returned without being closed against profit, so a recipe set "
         "that did not settle ships a mixture of values from different passes that need "
         "not agree with one another")

pricing = squashed(body_of(plain, "private static Long priceOf("))
if "if (each == null || each <= 0L) { return null; }" not in pricing:
    fail("an ingredient nobody can price is treated as free rather than as making the "
         "whole recipe unpriceable, which is how a mod ends up pricing a dragon egg at "
         "nothing")
if "if (each <= 0L) { return null; }" not in pricing:
    fail("something can be worth zero, and something worth zero can be dissolved for "
         "ever and makes every recipe that consumes it look free")
if "return total / step.makes();" in pricing:
    fail("the price of one output is not taken through the zero check, so the guard "
         "above can be bypassed by the very line it guards")
if "if (total < 0L) { return null; }" not in pricing:
    fail("a recipe whose ingredients overflow a long is priced at whatever the overflow "
         "came to, which is a negative number and therefore free")

closing = squashed(body_of(plain, "private static Map<String, Long> closed("))
if "List<String> rideable = profitable(recipes, table);" not in closing:
    fail("closing the table does not ask which recipes are still profitable, so it is "
         "closing against something else")
if "if (table.remove(step.output()) != null) { removed = true; }" not in closing:
    fail("a profitable recipe's output is not taken out of the table, so the closure "
         "never removes anything and the loop is decoration")
if "if (seeds.containsKey(step.output())) { continue; }" not in closing:
    fail("a decided value can be removed by the closure, so a bad recipe for cobblestone "
         "takes cobblestone out of the game's price list")

profit = squashed(body_of(plain, "public static List<String> profitable("))
if "if (priceable && made * step.makes() > spent) { caught.add(entry.getKey()); }" \
        not in profit:
    fail("what counts as profitable is not what a recipe makes against what it consumes, "
         "so the check that the whole module rests on is checking something else")
if "if (each == null) { priceable = false; break; }" not in profit:
    fail("a recipe with an unpriceable ingredient is judged as though that ingredient "
         "were free, so everything downstream of anything unknown reads as profitable")

seeds_named = re.findall(r'worth\.put\("([a-z_]+:[a-z_]+)", ([\d_]+)L\);',
                         strip_comments(rules, keep_strings=True))
if len(seeds_named) < 20:
    fail(f"there are only {len(seeds_named)} decided values, which leaves most of the "
         f"game unpriceable and the table mostly empty")
seeded = dict((name, int(value.replace("_", ""))) for name, value in seeds_named)
for name, value in seeded.items():
    if value <= 0:
        fail(f"{name} is decided at {value}; something worth nothing can be dissolved "
             f"for ever")
if seeded.get("minecraft:cobblestone") != 1:
    fail("the unit is not a cobblestone, so every ratio in the module is relative to "
         "something nobody can hold")
for cheap, dear in (("minecraft:iron_ingot", "minecraft:diamond"),
                    ("minecraft:coal", "minecraft:gold_ingot"),
                    ("minecraft:dirt", "minecraft:iron_ingot")):
    if seeded.get(cheap, 0) >= seeded.get(dear, 0):
        fail(f"{cheap} is decided at or above {dear}, so the ladder a player already "
             f"has in their head disagrees with the one the module uses")

print(f"the table          : {len(seeded)} decided values, cheapest wins, nothing worth "
      f"nothing, and the whole of it closed against profit")

# ---------------------------------------------------------------------------
# 4. The two directions
# ---------------------------------------------------------------------------

share = constant("BURN_SHARE")
try:
    share_value = float((share or "").rstrip("Ff"))
except ValueError:
    share_value = 1.0
    fail("what dissolving returns is not a number this file can read")
if share_value >= 1.0:
    fail("a round trip is lossless, so any single rounding error anywhere in a table of "
         "ten thousand derived numbers is an infinite supply of something")
if share_value <= 0.5:
    fail(f"dissolving returns {share_value}, which is not a transmutation system, it is "
         f"a bin")

dissolving = squashed(body_of(plain, "public static long dissolve("))
if "return (long) Math.floor((double) worth * count * BURN_SHARE);" not in dissolving:
    fail("dissolving does not round down, so a rounding error in a player's favour is "
         "the one direction the arithmetic leans")
if "if (worth <= 0L || count <= 0) { return 0L; }" not in dissolving:
    fail("dissolving nothing, or something worth nothing, gives motes")

condensing = squashed(body_of(plain, "public static long condensing("))
if "return worth * (long) count;" not in condensing:
    fail("condensing does not cost the full worth of what it makes, so the loss on a "
         "round trip is not the loss the rules state")

affording = squashed(body_of(plain, "public static int affords("))
if "if (worth <= 0L || motes <= 0L) { return 0; }" not in affording:
    fail("a balance of nothing, or a thing worth nothing, affords something")

estate = squashed(body_of(plain, "public static long estate("))
if "total += each * entry.getValue();" not in estate:
    fail("what a player is worth does not count what they are holding, so the one "
         "number the harness watches is not the one that matters")

print(f"the two directions : dissolving returns {share_value}, condensing costs all of "
      f"it, and both round the way that favours the game")

# ---------------------------------------------------------------------------
# 5. Who decides
# ---------------------------------------------------------------------------

transmuting = strip_comments(read(SRC, "transmute/Transmuting.java"))
condense_body = squashed(body_of(transmuting, "public static Component condense("))
for statement, why in (
        ("if (!ArcanaAttachments.knows(player, id)) {",
         "a packet can condense anything at all, whether or not the player has ever "
         "held one — which is the catalogue's whole purpose defeated by one message"),
        ("long worth = MoteTable.table(player.level()).getOrDefault(id, 0L);",
         "the price comes from somewhere other than this server's own table"),
        ("if (!ArcanaAttachments.spend(player, price)) {",
         "the motes are never taken, so everything is free")):
    if statement not in condense_body:
        fail("condensing: " + why)

dissolve_body = squashed(body_of(transmuting, "public static Component dissolve("))
if "if (held.isDamaged() || !held.getComponentsPatch().isEmpty()) {" not in dissolve_body:
    fail("a worn or enchanted item is priced the same as a fresh one, which turns the "
         "transmuter into a repair bench and an enchantment launderer")
if "held.shrink(taken);" not in dissolve_body:
    fail("dissolving does not consume what it dissolved, so one diamond is an infinite "
         "supply of motes")
if dissolve_body.find("held.shrink(taken);") > dissolve_body.find("ArcanaAttachments.gain"):
    fail("the motes are handed over before the item is taken, so anything that can fail "
         "in between is paid for twice")

payloads = squashed(strip_comments(read(SRC, "network/ArcanaPayloads.java")))
if "record ChooseTransmutation(String key, int count)" not in payloads:
    fail("what comes back from the catalogue is not just which row and how many; a "
         "packet carrying a price is a packet that can lie about one")
# From the record onwards, not from its last mention onwards: the name appears
# half a dozen times in its own body, so splitting on it and taking the tail
# lands past everything worth reading — which is how this rule spent its first
# afternoon passing whatever it was shown.
after_the_record = payloads.split("record ChooseTransmutation", 1)[-1]
for forbidden in ("long worth", "long price", "int price", "float price"):
    if forbidden in after_the_record:
        fail(f"the packet from the client carries {forbidden}, so the client is trusted "
             f"with the one number this whole module is about")

attachments = strip_comments(read(SRC, "registry/ArcanaAttachments.java"))
attach_squashed = squashed(attachments)
for statement, why in (
        (".copyOnDeath()",
         "a balance and a catalogue are lost on death, which is a punishment for dying "
         "aimed at the one system where it compounds"),
        (".sync(",
         "neither is sent to the client, so the catalogue is empty however much a player "
         "has dissolved")):
    if attach_squashed.count(statement) < 2:
        fail("the ledger: " + why)

spending = squashed(body_of(attachments, "public static boolean spend("))
if "if (now < 0L) { return false; }" not in spending:
    fail("a balance can go below nothing, which is a balance a player can spend twice")
learning = squashed(body_of(attachments, "public static boolean learn("))
if "player.setData(LEARNED.get(), List.copyOf(now));" not in learning:
    fail("learning mutates the list in place rather than replacing it, so the sync never "
         "hears about it and the client's catalogue silently stops growing")

table_src = strip_comments(read(SRC, "block/MoteTable.java"))
if "ArcanaRules.derive(recipes, ArcanaRules.SEEDS)" not in squashed(table_src):
    fail("the table in the world is not derived by the rules the harness proves, so "
         "three million checks are about a different table from the one that ships")
picking = squashed(body_of(table_src, "private static String dearest("))
if "TreeMap<String, String> sorted = new TreeMap<>();" not in picking:
    fail("which item a tag stands for is whichever the game listed first, so the whole "
         "price table is different on a different day")
building = squashed(body_of(table_src, "public static synchronized Map<String, Long> table("))
if "if (manager != builtAgainst) {" not in building:
    fail("the table is either rebuilt on every lookup, which is several thousand "
         "recipes per click, or never rebuilt at all, which is a datapack reload that "
         "does nothing")

print("who decides        : the catalogue, the price, the balance and what a worn item "
      "is worth, all re-decided on the server; the packet carries only which row")

# ---------------------------------------------------------------------------
# 6. Arcana in the world
# ---------------------------------------------------------------------------

if not os.path.isfile(GENERATOR):
    fail("there is no generator, so every asset in the module is kept by hand")
else:
    with tempfile.TemporaryDirectory() as tmp:
        environment = dict(os.environ, ELYSIUM_ARCANA_OUT=tmp)
        made = subprocess.run([sys.executable, GENERATOR], capture_output=True,
                              text=True, env=environment, cwd=WORK)
        if made.returncode != 0:
            fail("the generator will not run, so nothing here can be rebuilt:\n"
                 + (made.stdout + made.stderr).strip()[:800])
        else:
            fresh = os.path.join(tmp, "src/main/resources")
            drifted = []
            for dirpath, _, filenames in os.walk(fresh):
                for name in filenames:
                    made_path = os.path.join(dirpath, name)
                    kept = os.path.join(RES, os.path.relpath(made_path, fresh))
                    if not os.path.isfile(kept):
                        drifted.append(os.path.relpath(made_path, fresh) + " (missing)")
                    elif open(made_path, "rb").read() != open(kept, "rb").read():
                        drifted.append(os.path.relpath(made_path, fresh))
            if drifted:
                fail(f"{len(drifted)} generated file(s) differ from what the generator "
                     f"writes: " + ", ".join(sorted(drifted)[:6]))

lang = {}
lang_path = os.path.join(RES, "assets", NS, "lang/en_us.json")
if os.path.isfile(lang_path):
    lang = json.load(open(lang_path, encoding="utf-8"))
else:
    fail("there is no language file, so every block is called by its id")

blocks = sorted(re.findall(r'BLOCKS\.register\("([a-z_]+)"',
                           strip_comments(read(SRC, "registry/ArcanaBlocks.java"), True)))
items = strip_comments(read(SRC, "registry/ArcanaItems.java"), True)
for name in blocks:
    if f'registerSimpleBlockItem(ArcanaBlocks.{name.upper()})' not in squashed(items):
        fail(f"{name} has no item, so it exists only to a command")
    for kind, where in (("a model", f"assets/{NS}/models/block/{name}.json"),
                        ("an item model", f"assets/{NS}/models/item/{name}.json"),
                        ("a blockstate", f"assets/{NS}/blockstates/{name}.json"),
                        ("a texture", f"assets/{NS}/textures/block/{name}.png")):
        if not os.path.isfile(os.path.join(RES, where)):
            fail(f"{name} has no {kind}, so it is a purple cube")
    for kind, where in (("a drop", f"data/{NS}/loot_table/blocks/{name}.json"),
                        ("a recipe", f"data/{NS}/recipe/{name}.json")):
        if not os.path.isfile(os.path.join(RES, where)):
            fail(f"{name} has no {kind}")
    if f"block.{NS}.{name}" not in lang:
        fail(f"{name} has no name in the language file")

recipe = read(RES, "data", NS, "recipe/transmuter.json")
# The ingredients only: the *result* is of course this module's own, and a rule
# that looked at the whole file would fire on that and therefore always fire —
# which is a rule that has never once been about what it says it is about.
ingredients = json.dumps(json.loads(recipe).get("key", {})) if recipe else ""
if "elysium" in ingredients:
    fail("the transmuter is made out of another Elysium module, so a player with only "
         "this one installed cannot build the only block it adds")
if "nether_star" not in recipe and "diamond" not in recipe:
    fail("the transmuter is cheap, and the last thing a player should build by accident "
         "is the one that prices the whole game")

screen = strip_comments(read(SRC, "client/TransmuterScreen.java"))
if "extends ElysiumChooser" not in screen:
    fail("the catalogue does not use the library's chooser, so it looks like a "
         "different mod from every other list in the pack")
if "hasShiftDown()" not in screen:
    fail("there is no way to take more than one at a time, so filling a stack is "
         "sixty-four clicks")

print(f"arcana in the world: {len(blocks)} block(s), modelled, dropped, named and "
      f"craftable out of the base game alone")

# ---------------------------------------------------------------------------
# 7. The seam
# ---------------------------------------------------------------------------

main_class = read(SRC, "ElysiumArcana.java")
if f'MODID = "{NS}"' not in main_class:
    fail("the module id is not what everything else in the tree spells it")
for registered in ("ArcanaAttachments.ATTACHMENTS.register", "ArcanaBlocks.BLOCKS.register",
                   "ArcanaItems.ITEMS.register"):
    if registered not in main_class:
        fail(f"{registered} is never called, so part of the module is written and none "
             f"of it exists")

print("what it declares   : the game and the library, and not one thing it prices")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all arcana checks passed")
