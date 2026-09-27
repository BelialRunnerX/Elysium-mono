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
import sys as _sys
_sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "tools"))
import samefile  # noqa: E402
import re
import subprocess
import sys
import tempfile

WORK = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(WORK)
RES = os.path.join(WORK, "src/main/resources")
SRC = os.path.join(WORK, "src/main/java/com/elysium/arcana")
PURE = os.path.join(SRC, "rules")
# The library's ability roster, which the relics are built on. It is the only
# thing outside this module the rules reach for, it is a pure-JDK class like
# they are, and elysium-lib sits below everything — so it is staged with them
# and the harness compiles against it the way the game will.
BELOW = [os.path.join(os.path.dirname(WORK),
                      "elysium-lib/src/main/java/com/elysium/lib/ability")]
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
    
    Comments go first, and that is not cosmetic. Without it every rule that
    uses this could be defeated by putting // in front of the line it names:
    the statement is still there, character for character, and the rule goes
    on passing. A negative test found that in one validator by commenting out
    one line, and it was true of every validator that had this function.
    """
    return " ".join(strip_comments(text, True).split())


def harness_trouble(output):
    """The lines of a harness run that say what went wrong.

    A failing harness prints its sections as it goes and its failures among
    them, and this used to be handed the first sixteen hundred characters of
    that. Where the failing check was near the end — which it is whenever the
    newest feature is the broken one — the failures fell off the end entirely
    and the validator reported a wall of successful section headings. Two
    negative tests were written against phrases that were never going to be
    printed before anybody noticed.
    """
    lines = [line for line in output.splitlines()
             if line.strip().startswith("FAIL:") or "failure(s)" in line]
    return "\n".join(lines or output.strip().splitlines()[-12:])[:2000]


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

# The mods this module replaces, as their Java packages and as their
# namespaces. The bare word "relics" used to be in the first group and had to
# come out: this module now has relics of its own, and a case-insensitive
# match on it flagged RelicRules, RelicItems and the RELICS constant three
# times each. The mod's own package is what identifies the mod, and it is
# still here.
FOREIGN = re.compile(
    r"(?:moze_intel|projecte|projectexpansion|it\.hurts\.sskirillss)"
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
        # The library is below everything and its rules are pure JDK too, so a
        # table here may be built on one there. That is what stops the relics
        # and the arsenal's uniques growing two sets of cooldowns that drift
        # apart. Anything else is a table reaching sideways.
        if used.startswith("com.elysium.lib."):
            continue
        fail(f"rules/{name} imports {used}; a table here is the JDK and the "
             f"library, and nothing else")

if not os.path.isfile(HARNESS):
    fail("there is no harness, so the one property this module rests on is checked by "
         "reading a table of ten thousand numbers, which nobody has ever done")
else:
    with tempfile.TemporaryDirectory() as tmp:
        staged = os.path.join(tmp, "src")
        os.makedirs(staged)
        sources = [os.path.join(PURE, n) for n in os.listdir(PURE)
                   if n.endswith(".java")]
        # And whatever sits below and is pure JDK too. The relics are built on
        # the library's ability roster, and a harness that cannot see it is a
        # harness that cannot check the one thing the relics are made of.
        for folder in BELOW:
            if not os.path.isdir(folder):
                fail(f"{folder} is not where this expects it, so the rules below "
                     f"this module cannot be compiled against")
                continue
            sources += [os.path.join(folder, n) for n in os.listdir(folder)
                        if n.endswith(".java")]
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
                fail("the arcana harness failed:\n" + harness_trouble(ran.stdout))
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

# Both generators, in the order regen.sh runs them, into one tree. They share
# a language file: gen_arcana writes its own keys over the whole of it and
# gen_geodes merges two hundred and seventy names on top. Run separately they
# each look wrong, because each is missing what the other wrote.
GENERATORS = [("tools/gen_arcana.py", "ELYSIUM_ARCANA_OUT"),
              ("tools/gen_geodes.py", "ELYSIUM_GEODES_OUT"),
              ("tools/gen_relics.py", "ELYSIUM_RELICS_OUT")]
for relative, _ in GENERATORS:
    if not os.path.isfile(os.path.join(WORK, relative)):
        fail(f"there is no {relative}, so the assets it writes are kept by hand")

if all(os.path.isfile(os.path.join(WORK, relative)) for relative, _ in GENERATORS):
    with tempfile.TemporaryDirectory() as tmp:
        ran_all = True
        for relative, variable in GENERATORS:
            environment = dict(os.environ, **{variable: tmp})
            made = subprocess.run([sys.executable, os.path.join(WORK, relative)],
                                  capture_output=True, text=True, env=environment,
                                  cwd=WORK)
            if made.returncode != 0:
                ran_all = False
                fail(f"{relative} will not run, so nothing it writes can be "
                     f"rebuilt:\n" + (made.stdout + made.stderr).strip()[:800])
        if ran_all:
            fresh = os.path.join(tmp, "src/main/resources")
            drifted = []
            for dirpath, _, filenames in os.walk(fresh):
                for name in filenames:
                    made_path = os.path.join(dirpath, name)
                    kept = os.path.join(RES, os.path.relpath(made_path, fresh))
                    if not os.path.isfile(kept):
                        drifted.append(os.path.relpath(made_path, fresh) + " (missing)")
                    elif not samefile.same(made_path, kept):
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


# ---------------------------------------------------------------------------
# 7. Two hundred and ten geodes, and the twenty-nine materials that are not here
# ---------------------------------------------------------------------------
#
# The mod this replaces ships geodes for twenty-nine materials, and the list is
# the union of every mod it has ever supported. In this pack most of them are
# blocks with no way to obtain them. So the list here is checked against the
# foundry's own mined materials, exactly as the bees are.

def asset(*parts):
    return os.path.isfile(os.path.join(RES, "assets", NS, *parts))


def datum(*parts):
    return os.path.isfile(os.path.join(RES, "data", NS, *parts))


GEODE_RULES = read(SRC, "rules/GeodeRules.java")
GEODE_BLOCKS = read(SRC, "registry/GeodeBlocks.java")
GEODE_ITEMS = read(SRC, "registry/GeodeItems.java")
geode_source = squashed(GEODE_RULES)


def geode_listing(name):
    found = re.search(r"public static final List<String> " + name
                      + r" = List\.of\(([^;]*?)\);", geode_source)
    return re.findall(r'"([a-z_]+)"', found.group(1)) if found else []


geode_mined = geode_listing("MINED")
geode_vanilla = geode_listing("THE_GAMES_OWN")
geode_materials = geode_mined + geode_vanilla
geode_kinds = ["block", "budding", "small_bud", "medium_bud", "large_bud",
               "cluster", "tinted_glass"]


def geode_id(material, kind):
    return f"budding_{material}" if kind == "budding" else f"{material}_{kind}"


if not geode_materials:
    fail("no geode materials could be read out of GeodeRules.java, so nothing below "
         "this line checked anything at all")

foundry_path = os.path.join(ROOT, "elysium-foundry",
                            "src/main/java/com/elysium/foundry/rules/FoundryRules.java")
if not os.path.isfile(foundry_path):
    fail("the foundry's material list is not where this expects it, so the check "
         "that a geode names something this pack mines did not run")
else:
    foundry_src = open(foundry_path, encoding="utf-8").read()
    mined_here = set(re.findall(r'mined\("([a-z_]+)", \d+\)', foundry_src))
    made_here = set(re.findall(r'(?:alloy|refined|grown|pure)\("([a-z_]+)"',
                               foundry_src))
    unreachable = [m for m in geode_mined if m not in mined_here]
    if unreachable:
        fail(f"{len(unreachable)} geode material(s) the foundry does not mine, so "
             f"they are walls of blocks nobody can reach — the state the twenty-nine "
             f"in the jar are mostly in: " + ", ".join(sorted(unreachable)[:8]))
    alloys = [m for m in geode_materials if m in made_here]
    if alloys:
        fail("geode(s) for a material a machine makes rather than one the ground "
             "grows: " + ", ".join(sorted(alloys)[:6]))

for material in geode_vanilla:
    if material in geode_mined:
        fail(f"{material} is listed as both mined here and the game's own")

missing_geodes = []
for material in geode_materials:
    for kind in geode_kinds:
        name = geode_id(material, kind)
        if not asset("models/block", name + ".json"):
            missing_geodes.append(name + " model")
        if not asset("blockstates", name + ".json"):
            missing_geodes.append(name + " blockstate")
        if not asset("textures/block", name + ".png"):
            missing_geodes.append(name + " texture")
        if not asset("models/item", name + ".json"):
            missing_geodes.append(name + " item model")
        if not datum("loot_table/blocks", name + ".json"):
            missing_geodes.append(name + " loot")
        if f"block.{NS}.{name}" not in lang:
            missing_geodes.append(name + " name")
    for kind in ("shard", "spyglass"):
        if not asset("models/item", f"{material}_{kind}.json"):
            missing_geodes.append(f"{material}_{kind} model")
        if not asset("textures/item", f"{material}_{kind}.png"):
            missing_geodes.append(f"{material}_{kind} texture")
        if f"item.{NS}.{material}_{kind}" not in lang:
            missing_geodes.append(f"{material}_{kind} name")
if missing_geodes:
    fail(f"{len(missing_geodes)} thing(s) a geode needs are not there: "
         + ", ".join(missing_geodes[:8]) + (" ..." if len(missing_geodes) > 8 else ""))

# A directional crystal needs a variant for every way it can face, or the game
# draws nothing at all for the ones it has no entry for.
for material in geode_materials[:4]:
    for kind in ("small_bud", "medium_bud", "large_bud", "cluster"):
        path = os.path.join(RES, "assets", NS, "blockstates",
                            geode_id(material, kind) + ".json")
        if os.path.isfile(path):
            variants = json.load(open(path, encoding="utf-8")).get("variants", {})
            if len(variants) != 6:
                fail(f"{geode_id(material, kind)} has {len(variants)} facing "
                     f"variant(s) where six are needed, so it draws nothing when it "
                     f"grows the other ways")

# The budding block has to be handed its own chain, or it grows nothing.
if "new BuddingGeodeBlock(solid(), chain)" not in squashed(GEODE_BLOCKS):
    fail("the budding blocks are not built with a chain, so a random tick on one "
         "does nothing and every geode in the world stays a bare wall")
if "for (String material : GeodeRules.materials()) {" not in squashed(GEODE_BLOCKS):
    fail("GeodeBlocks no longer walks the material list, so the registry and the "
         "rules can disagree about which geodes exist")
for where, source in (("GeodeBlocks", GEODE_BLOCKS), ("GeodeItems", GEODE_ITEMS)):
    if "throw new IllegalStateException" not in squashed(source) \
            or "if (made.size() != GeodeRules." not in squashed(source):
        fail(f"{where} does not count what it registered against the catalogue")

print(f"geodes             : {len(geode_materials)} material(s) — "
      f"{len(geode_mined)} the foundry mines and {len(geode_vanilla)} the game's own "
      f"— in {len(geode_kinds)} shapes each, every chain whole and every block "
      f"modelled, dropped and named")


# ---------------------------------------------------------------------------
# 7b. How a budding block is made
# ---------------------------------------------------------------------------
#
# The section that lets a jar leave the mods folder. GeOre has been marked DONE
# and stayed in the pack because oritech ships thirty-two compat recipes that
# assemble a geore budding block out of geore shards — take the jar out and
# those recipes name items that do not exist, the game drops them at load, and
# a feature is simply gone with nothing saying so. This is that capability in
# this module's own items.
#
# What is checked here is the half no rule can see: that the recipe on disk is
# paid in the material's own blocks, in the number the rules price it at, and
# that it fits in a grid.

BUDDING_SRC = strip_comments(read(SRC, "rules/BuddingRules.java"), keep_strings=True)

if not BUDDING_SRC:
    fail("nothing says how a budding block is made, so the only way to get one "
         "is to find one — and the jar that used to provide the other way "
         "cannot leave the pack")
else:
    _budding = squashed(BUDDING_SRC)
    seedings = re.findall(r'new Budding\("(\w+)", "(\w+)", (\d+), (\w+)\)', _budding)
    catalysts = dict(re.findall(
        r'public static final String (\w+_CATALYST) = "([\w:]+)";', BUDDING_SRC))
    _gives = re.search(r"int CLUSTER_GIVES = (\d+);", _budding)
    to_a_block = int(_gives.group(1)) if _gives else 0
    _grid = re.search(r"int GRID = (\d+);", _budding)
    grid = int(_grid.group(1)) if _grid else 0

    if len(seedings) < 10 or not to_a_block or not grid:
        fail(f"only {len(seedings)} seeding(s), {to_a_block} shard(s) to a block "
             f"and a grid of {grid} could be read, so the checks below are "
             f"walking a short list and passing")

    # The number the payback arithmetic is entirely about has to be the number
    # the cluster loot tables actually hand out. One of the two is generated
    # and the other is reasoning, and they drift the moment nobody checks.
    for material, _from, _shards, _catalyst in seedings:
        at = os.path.join(RES, f"data/{NS}/loot_table/blocks",
                          f"{material}_cluster.json")
        if not os.path.isfile(at):
            fail(f"{material} has no cluster loot table, so what a grown geode "
                 f"gives back is nothing the payback arithmetic can be checked "
                 f"against")
            continue
        try:
            written = json.loads(read(os.path.dirname(at), os.path.basename(at)))
        except ValueError:
            fail(f"{material}'s cluster loot table is not readable as JSON")
            continue
        handed = None
        for pool in written.get("pools", []):
            for entry in pool.get("entries", []):
                for function in entry.get("functions", []):
                    if function.get("function") == "minecraft:set_count":
                        handed = function.get("count")
        if handed is not None and handed != to_a_block:
            fail(f"a {material} cluster hands out {handed} shard(s) and the "
                 f"seeding arithmetic is worked out against {to_a_block}, so "
                 f"every payback this module proves is about a number the game "
                 f"does not use")

    # And the recipe on disk: its own blocks, the right number of them, the
    # catalyst the rules name, and small enough to exist.
    for material, from_material, shards, catalyst in seedings:
        at = os.path.join(RES, f"data/{NS}/recipe", f"budding_{material}.json")
        if not os.path.isfile(at):
            fail(f"there is no recipe that makes a budding {material}, so that "
                 f"material is the one a player has to go and find")
            continue
        try:
            written = json.loads(read(os.path.dirname(at), os.path.basename(at)))
        except ValueError:
            fail(f"budding_{material}'s recipe is not readable as JSON, so the "
                 f"game refuses the whole data pack over one seeding")
            continue
        named = [one.get("item") for one in written.get("ingredients", [])]
        wanted = catalysts.get(catalyst, catalyst)
        blocks = [one for one in named if one == f"{NS}:{material}_block"]
        if to_a_block and len(blocks) != int(shards) // to_a_block:
            fail(f"budding_{material} is made of {len(blocks)} of its own "
                 f"block(s) and the rules price it at {shards} shard(s), which "
                 f"is {int(shards) // to_a_block} — so what the player pays and "
                 f"what this module proves about the price are different numbers")
        for one in named:
            if one == wanted or one == f"{NS}:{material}_block":
                continue
            fail(f"budding_{material} is made with {one}, which is neither its "
                 f"own block nor the catalyst its row names — so the recipe is "
                 f"a way of turning one material into another that the rules "
                 f"never saw")
        if grid and len(named) > grid:
            fail(f"budding_{material} is made of {len(named)} things in a grid "
                 f"of {grid}, so the game drops it at load and that material "
                 f"quietly cannot be seeded")
        if from_material != material:
            fail(f"budding_{material} is priced in {from_material} shards, so "
                 f"it is a source of something the player has not rather than a "
                 f"multiplier on something they have")

    # Every rule the file declares is added up, and faults() is a list.
    _bsum = squashed(body_of(BUDDING_SRC, "public static List<String> faults()"))
    _brules = set(re.findall(r"public static List<String> (faultsIn\w+)\(", BUDDING_SRC))
    _badded = set(re.findall(r"bad\.addAll\((faultsIn\w+)\([^;]*\)\);", _bsum))
    if len(_brules) < 6:
        fail(f"only {len(_brules)} seeding rule(s) are declared at all, so the "
             f"check below is checking nothing")
    if re.search(r"\bif\s*\(", _bsum):
        fail("the seedings' faults() has a condition in it, so some rule is "
             "added up only sometimes")
    for _brule in sorted(_brules - _badded):
        fail(f"{_brule} is written, compiled and never added up by the "
             f"seedings' faults(), so it answers the harness's controls and is "
             f"never once asked about what this module ships")

    print(f"the seeding        : {len(seedings)} material(s), every one of them "
          f"made of its own blocks in the number its row prices, with a catalyst "
          f"that is not another material's shard, small enough for a grid of "
          f"{grid} — so GeOre can leave the mods folder")



# ---------------------------------------------------------------------------
# 8. Thirty relics, and the ability system they are built on
# ---------------------------------------------------------------------------
#
# The hard part is not here: what a relic does is an ability, and the abilities
# live in the library with their own balance model and their own harness. What
# is checked here is that every relic's list of abilities is a list the library
# allows, that no ability exists only in a table, and that the tooltip is read
# off the roster rather than written into a string.

RELIC_RULES = read(SRC, "rules/RelicRules.java")
RELIC_ITEMS = read(SRC, "registry/RelicItems.java")
relic_source = squashed(RELIC_RULES)
relic_rows = re.findall(
    r'relic\("([a-z_]+)", "([a-z]+)", (\d+), List\.of\(([^)]*)\)\)', relic_source)
relic_ids = [row[0] for row in relic_rows]
relic_abilities = {name for row in relic_rows
                   for name in re.findall(r'"([a-z_]+)"', row[3])}

ability_path = os.path.join(
    ROOT, "elysium-lib/src/main/java/com/elysium/lib/ability/ElysiumAbilityRules.java")
if not os.path.isfile(ability_path):
    fail("the library's ability roster is not where this expects it, so every relic "
         "below could name an ability that does not exist")
    library_abilities = set()
else:
    library_abilities = set(re.findall(
        r'made\("([a-z_]+)", [A-Z]+,', open(ability_path, encoding="utf-8").read()))

if not relic_ids:
    fail("no relics could be read out of RelicRules.java, so nothing below this line "
         "checked anything at all")

unknown = sorted(relic_abilities - library_abilities)
if unknown:
    fail("relic(s) naming an ability the library does not have, so those relics do "
         "nothing at all: " + ", ".join(unknown[:6]))
unworn = sorted(library_abilities - relic_abilities)
if unworn:
    fail("abilit(ies) in the library that nothing in the world carries, so they "
         "exist only in a table: " + ", ".join(unworn[:6]))

# The registry has to refuse an illegal set as well as the harness, because a
# relic added later by hand never goes through the harness at all.
if "ElysiumAbilityRules.cannotShareAnItem(relic.abilities())" not in squashed(
        RELIC_ITEMS):
    fail("RelicItems does not check what it is about to register against the "
         "library's rule, so a relic written by hand can carry anything")
if "for (RelicRules.Relic relic : RelicRules.RELICS) {" not in squashed(RELIC_ITEMS):
    fail("RelicItems no longer walks the roster, so the registry and the rules can "
         "disagree about which relics exist")
if "if (made.size() != RelicRules.RELICS.size()) { throw new IllegalStateException(" \
        not in squashed(RELIC_ITEMS):
    fail("RelicItems does not count what it registered against the roster")

# The tooltip reads the roster. A cooldown written into a translation string is
# a tooltip that stops being true the first time the number is retuned.
if "ability.cooldown() / 20.0F" not in squashed(RELIC_ITEMS):
    fail("the relic tooltip no longer reads the cooldown off the ability, so it "
         "stops being true the first time a number is retuned")

missing_relics = []
for rid in relic_ids:
    if not asset("models/item", rid + ".json"):
        missing_relics.append(rid + " model")
    if not asset("textures/item", rid + ".png"):
        missing_relics.append(rid + " texture")
    if not datum("recipe", rid + ".json"):
        missing_relics.append(rid + " recipe")
    if f"item.{NS}.{rid}" not in lang:
        missing_relics.append(rid + " name")
for name in sorted(library_abilities):
    if f"{NS}.ability.{name}" not in lang:
        missing_relics.append(name + " tooltip line")
for line in (f"{NS}.relic.slot", f"{NS}.relic.always", f"{NS}.relic.every"):
    if line not in lang:
        missing_relics.append(line)
if missing_relics:
    fail(f"{len(missing_relics)} thing(s) a relic needs are not there: "
         + ", ".join(missing_relics[:8]) + (" ..." if len(missing_relics) > 8 else ""))

# A relic you press does something.
#
# For a while this was the whole gap: thirty relics, every ability priced, the
# harness passing, and nothing bound to the right mouse button. A system that
# is complete and inert passes every check there is, which is why this one had
# to be written down.

RELIC_ITEMS = strip_comments(read(SRC, "registry/RelicItems.java"))
_use = squashed(body_of(RELIC_ITEMS,
                        "public InteractionResultHolder<ItemStack> use("))
if not _use:
    fail("a relic does nothing when you press it, so every relic in this "
         "module is a tooltip")
else:
    if "ElysiumAbilityUse.perform(active, player)" not in _use:
        fail("a relic does its own thing on use rather than asking the library "
             "that knows what an ability is, so what a relic does and what it "
             "is priced at can come apart")
    if "player.getCooldowns().addCooldown(this, active.cooldown());" not in _use:
        fail("a relic's cooldown is not the ability's own, so the tooltip says "
             "one number and the item obeys another")
    if "player.getCooldowns().isOnCooldown(this)" not in _use:
        fail("a relic can be pressed again while it is on cooldown, so the "
             "cooldown is a line in a tooltip")
_active = squashed(body_of(RELIC_ITEMS,
                           "public ElysiumAbilityRules.Ability activeOf("))
if "ElysiumAbilityRules.ACTIVE.equals(ability.kind())" not in _active:
    fail("a relic fires whichever of its abilities it finds first rather than "
         "the one that is an active, so pressing it could set off something "
         "that is always on")

print(f"relics             : {len(relic_ids)} across "
      f"{len({row[1] for row in relic_rows})} slot(s), carrying every one of "
      f"{len(library_abilities)} abilities the library defines, each list legal by "
      f"the library's own rule")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all arcana checks passed")
