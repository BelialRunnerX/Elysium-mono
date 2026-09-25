#!/usr/bin/env python3
"""
Validate Elysium Agronomy against its sources.

Run from anywhere:   python3 elysium-agronomy/validate.py

One claim here matters more than everything else in the module: an acre of
seeds is worse than a mine. That is the property whose failure has ruined more
modpacks than any other single number, and it is invisible until a player has
already stopped mining.

  1. INDEPENDENCE. Vanilla, elysium-lib, elysium-kinetics, elysium-industry.
  2. PURITY, and the harness run rather than read.
  3. THE MARGIN. A farm that loses to a mine, and says by how much.
  4. THE MACHINES, and the grid they hang off.
  5. THE SEAM. What it declares.
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
SRC = os.path.join(WORK, "src/main/java/com/elysium/agronomy")
PURE = os.path.join(SRC, "rules")
BELOW = [os.path.join(ROOT, "elysium-kinetics/src/main/java/com/elysium/kinetics/rules"),
         os.path.join(ROOT, "elysium-industry/src/main/java/com/elysium/industry/rules")]
HARNESS = os.path.join(WORK, "tools/AgronomyHarness.java")
HARNESS_MAIN = "com.elysium.agronomy.rules.AgronomyHarness"

#: How long the arithmetic is allowed to take. The honest sweeps in
#: the harness run in a few seconds; anything past this is a loop.
HARNESS_SECONDS = 60
NS = "elysiumagronomy"
IND_NS = "elysiumindustry"

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
    """The code alone, every run of whitespace as one space.

    Naming the whole statement is the point: a rule that looks for a token
    which survives the very edit it was meant to catch is a rule that has never
    failed, and this project has now been bitten by that eleven times.

    <p>Comments go first, and that is not cosmetic. Without it every rule in
    this file could be defeated by putting {@code //} in front of the line it
    names — the statement is still there, character for character, and the
    rule goes on passing. A negative test found that by commenting out one
    line, and it was true of every rule here rather than of one.
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
    print("This is not the elysium-agronomy tree.")
    sys.exit(1)

print("=" * 70)
print("elysium-agronomy — things that grow, and the proof that a farm loses to a mine")
print("=" * 70)

# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------

FOREIGN = re.compile(
    r"(?:com\.blakebr0\.mysticalagriculture|vectorwing\.farmersdelight|cy\.jdkdigital"
    r"|com\.teamresourceful\.aquaculture)"
    r"|\b(?:mysticalagriculture|mysticalagradditions|farmersdelight|productivebees"
    r"|botanypots|aquaculture|solcarrot|cobblegengalore|tide)\s*:"
    r"|\"(?:mysticalagriculture|farmersdelight|productivebees|botanypots|aquaculture"
    r"|solcarrot)\"", re.I)
UPWARD = re.compile(r"com\.elysium\.(foundry|transit|logistics)")

java_files = []
for dirpath, _, filenames in os.walk(SRC):
    for name in filenames:
        if name.endswith(".java"):
            java_files.append(os.path.join(dirpath, name))

for path in java_files:
    src = strip_comments(read(path))
    where = os.path.relpath(path, SRC)
    for hit in FOREIGN.findall(src):
        fail(f"{where} names {hit}; this module is a reimplementation of seventeen jars "
             f"and must not reach for any of them")
    if UPWARD.search(src):
        fail(f"{where} reaches up into a later module; the dependencies run one way")

toml = read(RES, "META-INF/neoforge.mods.toml")
declared = set(re.findall(r'modId="([^"]+)"', toml))
allowed = {"${mod_id}", "neoforge", "minecraft", "elysiumlib", "elysiumkinetics",
           "elysiumindustry"}
for mod in declared - allowed:
    fail(f"the module declares a dependency on {mod}; it needs the game, the library, "
         f"and the grid its machines hang off")
for needed in ("neoforge", "minecraft", "elysiumlib", "elysiumindustry"):
    if needed not in declared:
        fail(f"the module does not declare {needed}")

print(f"independence       : {len(java_files)} source file(s), none of the ten named, "
      f"and the grid its machines hang off declared")

# ---------------------------------------------------------------------------
# 2. Purity, and the harness executed
# ---------------------------------------------------------------------------

for name in sorted(os.listdir(PURE)) if os.path.isdir(PURE) else []:
    if not name.endswith(".java"):
        continue
    src = read(PURE, name)
    without_package = re.sub(r"^\s*package [^;]+;", "", src, count=1, flags=re.M)
    for forbidden in ("net.minecraft", "net.neoforged", "com.mojang"):
        if forbidden in without_package:
            fail(f"rules/{name} reaches for {forbidden}; the rules are the JDK and the "
                 f"modules below, so they can be executed by a harness")
    for used in re.findall(r"import\s+(com\.elysium\.[\w.]+);", without_package):
        if not (used.startswith("com.elysium.kinetics.rules.")
                or used.startswith("com.elysium.industry.rules.")):
            fail(f"rules/{name} imports {used}; a rules class may reach only into other "
                 f"rules, which are pure like itself")

if not os.path.isfile(HARNESS):
    fail("there is no harness, so every number below is checked by reading")
else:
    with tempfile.TemporaryDirectory() as tmp:
        staged = os.path.join(tmp, "src")
        os.makedirs(staged)
        sources = [os.path.join(PURE, n) for n in os.listdir(PURE) if n.endswith(".java")]
        for folder in BELOW:
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
            fail("the rules do not compile against the JDK and the modules below alone:\n"
                 + compiled.stderr.strip()[:1200])
        else:
            # With a clock on it. A rules file is meant to be arithmetic, and
            # arithmetic finishes; a harness that does not come back means some
            # rule in there can loop forever on input a player could produce,
            # which is a server that stops responding rather than a wrong
            # number. Waiting for it is not a way of finding that out.
            try:
                ran = subprocess.run(
                    ["java", "-cp", os.path.join(tmp, "out"), HARNESS_MAIN],
                    capture_output=True, text=True, timeout=HARNESS_SECONDS)
            except subprocess.TimeoutExpired:
                ran = None
                fail(f"the harness did not finish within {HARNESS_SECONDS} seconds, so "
                     f"something in the rules can loop forever — which is a server that "
                     f"stops responding, not a wrong answer")
            if ran is None:
                pass
            elif ran.returncode != 0:
                fail("the agronomy harness failed:\n" + harness_trouble(ran.stdout))
            else:
                print("the arithmetic     : " + ran.stdout.strip().splitlines()[-1])

rules = read(PURE, "AgronomyRules.java")
plain = strip_comments(rules)


def constant(name, kind=r"(?:float|int)"):
    m = re.search(r"public static final " + kind + r"\s+" + name + r"\s*=\s*([^;]+);",
                  plain, re.S)
    return " ".join(m.group(1).split()) if m else None


# ---------------------------------------------------------------------------
# 3. The margin
# ---------------------------------------------------------------------------
#
# The whole module in one rule: a farm has to lose to a mine, by a stated
# amount, and both halves of the comparison have to be here where a harness can
# execute them rather than in somebody's head.

for needed in ("farmedPerHour", "minedPerHour", "FARM_AT_MOST", "plotsBefore"):
    if needed not in plain:
        fail(f"there is no {needed}, so the claim that a farm loses to a mine is not "
             f"stated anywhere and cannot be checked")

margin = constant("FARM_AT_MOST")
if margin is None:
    fail("there is no margin between a farm and a mine")
else:
    try:
        value = float(margin.rstrip("F"))
    except ValueError:
        value = None
    if value is None or value <= 0.0 or value >= 1.0:
        fail(f"a farm is allowed to be worth {margin} of a mine; at one or more it wins, "
             f"and three ages of ore ladder stop mattering")

if constant("BEST_MULTIPLIER", kind="int") != "3":
    fail("this module compares a farm against ore processing that is not the best the "
         "spine offers, so the comparison is against a mine that does not exist")

farmed = body_of(plain, "public static float farmedPerHour(")
if "ESSENCE_PER_INGOT" not in farmed:
    fail("a farm's yield is not divided by what it costs to turn essence into the "
         "material, so the comparison flatters the farm by the whole of that step")

ticks = body_of(plain, "public static int ticksAt(")
if "TIER_SLOWER" not in ticks:
    fail("the tiers do not get slower by a stated step, so each is a number of its own "
         "and the ladder can stop being a ladder one rung at a time")
if "Math.min(TIERS, tier)" not in ticks:
    fail("a tier past the top of the ladder keeps getting slower, so a data file can "
         "invent a sixth tier by accident")

hearts = body_of(plain, "public static float extraHearts(")
if "Math.sqrt" not in hearts:
    fail("the diet bonus climbs in a straight line, so the thirtieth new food is worth "
         "as much as the first and trying things is a checklist rather than a discovery")
if "Math.min(FOODS_FOR_FULL, foods)" not in hearts:
    fail("the diet bonus keeps climbing past its cap, so a pack that adds foods adds "
         "health without meaning to")

print("the margin          : a farm measured against a mine, at " + str(margin)
      + " of it at most, with the tiers a stated step apart")

# ---------------------------------------------------------------------------
# 4. The machines, and the grid they hang off
# ---------------------------------------------------------------------------

if "IndustryRules.Machine" not in plain:
    fail("nothing here is a machine on Age II's grid, so this module has invented a "
         "second idea of what power is")

speed = body_of(plain, "public static float potSpeed(")
if "light < LEAST_LIGHT" not in speed:
    fail("a pot grows things in the dark, so a farm needs no windows and the one "
         "condition a pot puts on a player does nothing")
if "Math.min(1.0F, share)" not in speed:
    fail("an accelerator on a grid with more than it needs runs faster than full, so "
         "over-building is a speed upgrade nobody documented")

combs = body_of(plain, "public static float combsPerHour(")
if "Math.min(MOST_BEES, bees)" not in combs:
    fail("a hive works more bees than it says it holds")
if "Math.min(1.0F," not in combs:
    fail("more flowers than the bees want makes a hive faster than full, or a crowded "
         "hive stops entirely — crowding is meant to be a fraction, not a switch")

rock = body_of(plain, "public static float rockPerHour(")
if "ROCK_PER_HOUR.length - 1" not in rock:
    fail("a rock generator tier past the top of the table keeps going, so a data file "
         "can invent a fourth one")
if "rung == 0 ? 1.0F" not in rock:
    fail("the first rock generator needs power, so the oldest trick in the game is "
         "behind the second age")

frontier = body_of(plain, "public boolean growsAnywhere()")
if "mostLight >= 15" not in frontier or "needsVacuum" not in frontier:
    fail("a frontier crop is not asked whether it would grow absolutely anywhere, which "
         "is the one thing that would make the frontier worlds scenery again")

work = body_of(plain, "public static int workPerTick(")
if "Math.max(1, Math.round(speed * WORK_FULL))" not in work:
    fail("a pot growing very slowly makes no progress at all, so a browned-out "
         "accelerator looks like a broken one")
if "speed <= 0.0F" not in work:
    fail("a pot in the dark makes progress")

print("the machines        : a pot that wants light, an accelerator that shares the "
      "grid, a hive that crowds, and rock that costs power above the first rung")

# ---------------------------------------------------------------------------
# 5. The farm in the world
# ---------------------------------------------------------------------------

# Both generators, in the order regen.sh runs them, into one tree.
#
# They share a language file: gen_agronomy writes its own keys over the whole
# of it and gen_bees merges seventy-five bees' worth on top. Run separately
# into separate directories they each look wrong, because each is missing what
# the other wrote. Run in order into one directory they are exactly what is
# committed, and that is the thing worth checking — this is how regen.sh runs
# them and how the tree was built.
GENERATORS = [("tools/gen_agronomy.py", "ELYSIUM_AGRONOMY_OUT"),
              ("tools/gen_bees.py", "ELYSIUM_BEES_OUT"),
              ("tools/gen_fish.py", "ELYSIUM_FISH_OUT"),
              ("tools/gen_pots.py", "ELYSIUM_POTS_OUT"),
              ("tools/gen_veins.py", "ELYSIUM_VEINS_OUT"),
              ("tools/gen_kitchen.py", "ELYSIUM_KITCHEN_OUT")]
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
                fail(f"{relative} will not run, so nothing it writes can be rebuilt:\n"
                     + (made.stdout + made.stderr).strip()[:800])
        if ran_all:
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
                fail(f"{len(drifted)} generated file(s) differ from what the generators "
                     f"write, so they were edited by hand and the next run will undo "
                     f"them: " + ", ".join(sorted(drifted)[:6])
                     + (" ..." if len(drifted) > 6 else ""))

blocks_src = read(SRC, "registry/AgronomyBlocks.java")
items_src = read(SRC, "registry/AgronomyItems.java")
registered = re.findall(r'BLOCKS\.register\("([a-z_]+)"', strip_comments(blocks_src, True))
block_items = set(re.findall(r'registerSimpleBlockItem\(AgronomyBlocks\.([A-Z_]+)\)',
                             strip_comments(items_src, True)))
order = re.search(r"ORDER = List\.of\(([^;]+)\);", strip_comments(items_src, True))
in_tab = set(re.findall(r"[A-Z_]{3,}", order.group(1))) if order else set()

lang = {}
lang_path = os.path.join(RES, "assets", NS, "lang/en_us.json")
if os.path.isfile(lang_path):
    lang = json.load(open(lang_path, encoding="utf-8"))
else:
    fail("there is no language file, so every block is called by its id")

mineable = []
for tool in ("axe", "pickaxe"):
    where = os.path.join(RES, f"data/minecraft/tags/block/mineable/{tool}.json")
    if os.path.isfile(where):
        mineable.extend(json.load(open(where, encoding="utf-8")).get("values", []))

for name in registered:
    if name.upper() not in block_items:
        fail(f"{name} has no item, so it exists only to a command")
    if name.upper() not in in_tab:
        fail(f"{name} is in no creative tab, so nobody in a creative world can find it")
    for kind, where in (("blockstates", f"blockstates/{name}.json"),
                        ("an item model", f"models/item/{name}.json")):
        if not os.path.isfile(os.path.join(RES, "assets", NS, where)):
            fail(f"{name} has no {kind}, so it is a purple cube")
    if not os.path.isfile(os.path.join(RES, "data", NS, f"loot_table/blocks/{name}.json")):
        fail(f"{name} has no drop, so mining one destroys it and what is growing in it")
    if f"block.{NS}.{name}" not in lang:
        fail(f"{name} has no name in the language file")
    if f"{NS}:{name}" not in mineable:
        fail(f"{name} is in no mineable tag, so it takes a fist and drops nothing quickly")

# The pot's blockstate has to carry every step of growth, or a pot halfway
# through has no model and the game refuses it at load.
pot_state = os.path.join(RES, "assets", NS, "blockstates/pot.json")
if os.path.isfile(pot_state):
    variants = json.load(open(pot_state, encoding="utf-8")).get("variants", {})
    if set(variants) != {f"age={step}" for step in range(4)}:
        fail("a pot's blockstate does not name every step of growth, so a pot halfway "
             "through has no model")
    if len({entry.get("model") for entry in variants.values()}) < 4:
        fail("a pot looks the same at every stage, so a room of them cannot be read from "
             "the door — which is the whole reason the growth is on the blockstate")

pot_box = re.search(r"SHAPE = box\((\d+), (\d+), (\d+), (\d+), (\d+), (\d+)\)",
                    strip_comments(read(SRC, "block/PotBlock.java")))
pot_model = os.path.join(RES, "assets", NS, "models/block/pot_0.json")
if pot_box and os.path.isfile(pot_model):
    element = json.load(open(pot_model, encoding="utf-8"))["elements"][0]
    want = [float(pot_box.group(n)) for n in (1, 2, 3)]
    to = [float(pot_box.group(n)) for n in (4, 5, 6)]
    if [float(v) for v in element["from"]] != want or [float(v) for v in element["to"]] != to:
        fail("the pot's model and its collision box are different sizes, so it can be "
             "hit where it cannot be seen or the other way round")

# --- the blocks ------------------------------------------------------------

pot = strip_comments(read(SRC, "block/PotBlock.java"))
speed_of = body_of(pot, "public float speed(")
if "AgronomyRules.potSpeed(" not in speed_of:
    fail("a pot works out its own growth speed rather than asking the rules, so the "
         "light requirement and the accelerator's share are decided twice")
if "getMaxLocalRawBrightness" not in speed_of:
    fail("a pot does not ask the world how light it is, so the one condition it puts on "
         "a player does nothing")
growing = body_of(pot, "public void grow(")
# The whole statement, not the call: assigning the rules' answer to a variable
# and then adding one is a pot that ignores it while still appearing to ask.
for needed in ("int done = AgronomyRules.workPerTick(speed(level, pos));",
               "grown += done;", "AgronomyRules.workFor(sown.ticks())"):
    if needed not in squashed(growing):
        fail("a pot counts its growth in something other than the rules' work units, so "
             "a speed that changes halfway through invents or throws away progress")
if "insertItem(OUT, harvest, true)" not in growing:
    fail("a pot harvests into a full slot, so the crop is destroyed")

accelerator = strip_comments(read(SRC, "block/AcceleratorBlock.java"))
drawing = body_of(accelerator, "public IndustryRules.Machine machine()")
for reading in ("share()", "supply()", "demand()"):
    if reading in drawing:
        fail(f"an accelerator's draw reads {reading}, so what the grid can afford "
             f"depends on what the accelerator asks for and the other way round")
if "pots <= 0 ? null" not in drawing:
    fail("an accelerator with no pot beside it still taxes the grid, so one somebody "
         "walled off is a permanent cost with no effect")

hive = strip_comments(read(SRC, "block/HiveBlock.java"))
if "AgronomyRules.combsPerHour(" not in body_of(hive, "public float rate()"):
    fail("a hive works out its own crowding, so how a full hive slows is decided "
         "somewhere the harness cannot execute it")
if "ApiaryRules.perHour(" not in body_of(hive, "public float rate()"):
    fail("a hive works out its own rate rather than asking the rules for it, so the "
         "one line a hive may not cross is enforced over arithmetic the block does "
         "not use")
if "rate()" not in body_of(hive, "public void work("):
    fail("a hive at work does not use the rate it works out, so the upgrades fitted "
         "to it change a number nobody reads")
if "BlockTags.FLOWERS" not in hive:
    fail("a hive does not count the flowers around it, so 'why is my hive slow' has no "
         "answer a player can walk outside and look at")

rock = strip_comments(read(SRC, "block/RockGeneratorBlock.java"))
if "draw.draw() <= 0.0F ? null" not in body_of(rock, "public IndustryRules.Machine machine()"):
    fail("the first rock generator asks the grid for something, so the rung that is "
         "supposed to need no power is behind the second age")
if "tier() == 0 ? 1.0F" not in body_of(rock, "private float fed()"):
    fail("the first rock generator stops without power, which it does not need")

# --- the ladder, in the Java and in the data ------------------------------

foundry = ""
foundry_path = os.path.join(os.path.dirname(WORK), "elysium-foundry",
                            "src/main/java/com/elysium/foundry/rules/FoundryRules.java")
if os.path.isfile(foundry_path):
    foundry = open(foundry_path, encoding="utf-8").read()
else:
    fail("the foundry's material list is not where this expects it, so the one check "
         "that stops a bee naming a metal this pack cannot make did not run")

# There is one seed table and it is in SeedRules. There were three — here, in
# AgronomyItems and in the generator — and three hand-written copies of thirty
# rows is three chances for a seed to take one length of time to grow and cost
# another to make. Both readers are checked for actually reading it.
SEED_RULES = read(SRC, "rules/SeedRules.java")
seed_rows = re.findall(r'mined\("([a-z_]+)", (\d+), "([^"]+)"\)',
                       strip_comments(SEED_RULES, True))
seeds = {material: tier for material, tier, makes in seed_rows}
generator = read(WORK, "tools/gen_agronomy.py")
if not seeds:
    fail("no seeds are declared, so the module grows nothing")
# The whole loop, not the name. `SeedRules.SEEDS` also appears in the guard
# below the loop, so a rule that names only that goes on passing when the loop
# itself has been replaced by a hand-written list — which is exactly what a
# negative test caught it doing.
SEED_LOOP = ("for (com.elysium.agronomy.rules.SeedRules.Seed seed "
             ": com.elysium.agronomy.rules.SeedRules.SEEDS) {")
if SEED_LOOP not in squashed(items_src):
    fail("AgronomyItems no longer walks the seed table in the rules, so the tiers it "
         "registers and the tiers the rules describe can disagree")
if "SeedRules.java" not in generator:
    fail("the generator keeps its own copy of the seed table again, so a seed can "
         "take one length of time to grow and cost another to make")

# A seed may grow what the ground gives you and nothing else. An ore seed
# competes with a pickaxe, which is priced; a steel seed competes with the
# whole foundry and wins, because a field needs no machines.
foundry_mined = set(re.findall(r'mined\("([a-z_]+)", \d+\)', foundry))
foundry_made = set(re.findall(
    r'(?:alloy|refined|grown|pure)\("([a-z_]+)"', foundry))
games_own = re.search(r'THE_GAMES_OWN = Set\.of\(([^;]*?)\);',
                      strip_comments(SEED_RULES, True), re.S)
seed_vanilla = set(re.findall(r'"([a-z_]+)"', games_own.group(1))) if games_own else set()
if not seed_vanilla:
    fail("SeedRules declares nothing as the game's own, so every vanilla seed below "
         "would read as a seed for something nothing makes")
grown_not_mined = [m for m in seeds
                   if m in foundry_made
                   or (m not in foundry_mined and m not in seed_vanilla)]
if grown_not_mined:
    fail("seed(s) that grow something a machine makes rather than something the "
         "ground gives you — a field would replace the whole foundry rather than a "
         "pickaxe: " + ", ".join(sorted(grown_not_mined)[:8]))

for rung in range(1, int(constant("TIERS", kind="int") or 0) + 1):
    if str(rung) not in set(seeds.values()):
        fail(f"nothing at all grows at tier {rung}, so that rung of the ladder is a "
             f"length of time nobody ever waits")

# A recipe result must be a concrete item; a tag simply fails to load.
bad_results = [f"{m} -> {makes}" for m, t, makes in seed_rows
               if not (makes.startswith("minecraft:") or makes.startswith(f"{NS}:"))]
if bad_results:
    fail("seed(s) whose essence turns into something that is not a concrete item, "
         "so the recipe does not load at all: " + ", ".join(bad_results[:6]))

essences = re.findall(r'"([a-z_]+_essence)"', strip_comments(items_src, True))
if len(essences) != int(constant("TIERS", kind="int") or 0):
    fail("there are not as many essences as there are tiers, so some rung of the ladder "
         "has nothing on it")

OURS = {f"{NS}:{name}" for name in registered}
BEE_RULES = read(SRC, "rules/BeeRules.java")
BEE_ITEMS = read(SRC, "registry/BeeItems.java")
BEE_BLOCKS = read(SRC, "registry/BeeBlocks.java")
BEE_ENTITIES = read(SRC, "registry/BeeEntities.java")
BEE_MODEL = read(SRC, "client/BeeModel.java")

bee_source = strip_comments(BEE_RULES, True)
wild_rows = re.findall(r'wild\("([a-z_]+)", "([a-z_]+)", "([a-z_]+)"\)', bee_source)
resource_rows = re.findall(r'resource\("([a-z_]+)", "([a-z_]+)", "([a-z_]+)"\)',
                           bee_source)
place_rows = re.findall(
    r'place\("([a-z_]+)", "([a-z_]+)", "([a-z_]+)", "([a-z_]+)", "([a-z_]+)"\)',
    bee_source)
product_rows = re.findall(
    r'product\("([a-z_]+)", "([a-z_]+)", "([a-z_]+)", "([a-z_]+)"\)', bee_source)

bee_ids = ([row[0] for row in wild_rows] + [row[0] for row in resource_rows]
           + [row[0] for row in place_rows] + [row[0] for row in product_rows])
bee_homes = sorted({row[2] for row in wild_rows} | {row[1] for row in place_rows})

OURS |= {f"{NS}:{bid}_comb" for bid in bee_ids}
OURS |= {f"{NS}:{bid}_comb_block" for bid in bee_ids}
OURS |= {f"{NS}:{bid}_bee_spawn_egg" for bid in bee_ids}
OURS |= {f"{NS}:{home}_nest" for home in bee_homes}
OURS |= {f"{NS}:{tool}" for tool in
         ("bee_scoop", "bee_smoker", "bee_jar", "bee_treat")}

FISH_RULES = read(SRC, "rules/FishRules.java")
FISH_ITEMS = read(SRC, "registry/FishItems.java")
FISH_ENTITIES = read(SRC, "registry/FishEntities.java")
FISH_MODEL = read(SRC, "client/FishModel.java")
fish_source = strip_comments(FISH_RULES, True)
fish_families = {name: const for name, const, _, _, _ in re.findall(
    r'public static final Family ([A-Z]+) = new Family\("([a-z_]+)", '
    r'([A-Z]+), (\d+), ([A-Z]+)\);', fish_source)}
fish_rows = re.findall(
    r'fish\("([a-z_]+)", ([A-Z]+), (\d+), ([\d.]+)F, (\d+)\)', fish_source)
fish_ids = [row[0] for row in fish_rows]
rod_names = re.findall(r'"([a-z_]+_rod)"', strip_comments(FISH_ITEMS, True))

OURS |= {f"{NS}:{fid}" for fid in fish_ids}
OURS |= {f"{NS}:{fid}_bucket" for fid in fish_ids}
OURS |= {f"{NS}:{fid}_spawn_egg" for fid in fish_ids}
OURS |= {f"{NS}:{name}" for name in rod_names}
OURS |= {f"{NS}:{family}_bait" for family in fish_families.values()}
OURS |= {f"{NS}:fillet_knife", f"{NS}:measuring_line"}

POT_RULES = read(SRC, "rules/PotRules.java")
POT_BLOCKS = read(SRC, "registry/PotBlocks.java")
pot_source = strip_comments(POT_RULES, True)


def pot_listing(name):
    found = re.search(r"public static final List<String> " + name
                      + r" = List\.of\(([^;]*?)\);", pot_source, re.S)
    return re.findall(r'"([a-z_]+)"', found.group(1)) if found else []


pot_colours = pot_listing("COLOURS")
pot_ceramics = pot_listing("CERAMICS")
pot_stones = pot_listing("STONES")
pot_materials = [f"{c}_{k}" for k in pot_ceramics for c in pot_colours] + pot_stones
pot_ids = []
for material in pot_materials:
    pot_ids.append(material + "_botany_pot")
    pot_ids.append(material + "_hopper_botany_pot")
    pot_ids.append(material + "_waxed_botany_pot")
our_soils = re.findall(r'"([a-z_]+)"', re.search(
    r"OUR_SOILS =\s*java\.util\.List\.of\(([^;]*?)\);",
    strip_comments(POT_BLOCKS, True)).group(1)) if "OUR_SOILS" in POT_BLOCKS else []
pot_soils = [row[0] for row in re.findall(
    r'new Soil\("([a-z_]+)", (\d+), ([\d.]+)F, List\.of\(([^)]*)\)\)', pot_source)]
pot_sprouts = [row[0] for row in re.findall(
    r'new Sprout\("([a-z_]+)", "([a-z_]+)", (\d+), "([a-z_]+)", (\d+)\)', pot_source)]

OURS |= {f"{NS}:{pid}" for pid in pot_ids}
OURS |= {f"{NS}:{soil}" for soil in our_soils}

vein_rows = re.findall(r'new Vein\("([a-z_]+)", (\d+), ([\d.]+)F\)',
                       strip_comments(read(SRC, "rules/AgronomyRules.java"), True))
OURS |= {f"{NS}:{row[0]}_generator" for row in vein_rows}

KITCHEN_RULES = read(SRC, "rules/KitchenRules.java")
KITCHEN_ITEMS = read(SRC, "registry/KitchenItems.java")
KITCHEN_BLOCKS = read(SRC, "registry/KitchenBlocks.java")
kitchen_source = strip_comments(KITCHEN_RULES, True)
kitchen_parts = [row[0] for row in re.findall(
    r'new Ingredient\("([a-z_]+)", (\d+), ([\d.]+)F\)', kitchen_source)]
kitchen_meals = re.findall(
    r'new Meal\("([a-z_]+)", List\.of\(([^)]*)\),\s*'
    r'(\d+), ([\d.]+)F, (\d+), (\d+), ([A-Z]+), "([a-z_]*)"\)', kitchen_source)
kitchen_cuts = [row[0] for row in re.findall(
    r'new Cut\("([a-z_]+)", "([a-z_]+)", Map\.of\(([^)]*)\)\)', kitchen_source)]


def kitchen_listing(name):
    found = re.search(r"public static final List<String> " + name
                      + r" = List\.of\(([^;]*?)\);",
                      strip_comments(KITCHEN_BLOCKS, True), re.S)
    return re.findall(r'"([a-z_]+)"', found.group(1)) if found else []


kitchen_woods = kitchen_listing("WOODS")
kitchen_crated = kitchen_listing("CRATED")
kitchen_stations = dict(re.findall(r'KitchenRules\.([A-Z]+), "([a-z_]+)"',
                                   strip_comments(KITCHEN_BLOCKS, True)))
kitchen_knives = re.findall(r'"([a-z_]+)"', (re.search(
    r'String\[\] kinds = \{([^}]*)\}',
    strip_comments(KITCHEN_ITEMS, True)) or re.match("", "")).group(1)) \
    if re.search(r'String\[\] kinds = \{', strip_comments(KITCHEN_ITEMS, True)) else []

kitchen_blocks = (list(kitchen_stations.values())
                  + [w + "_cabinet" for w in kitchen_woods]
                  + [c + "_crate" for c in kitchen_crated])

OURS |= {f"{NS}:{part}" for part in kitchen_parts}
OURS |= {f"{NS}:{row[0]}" for row in kitchen_meals}
OURS |= {f"{NS}:{name}" for name in kitchen_blocks}
OURS |= {f"{NS}:{knife}_knife" for knife in kitchen_knives}

chunked = [m for m, t, makes in seed_rows if makes.startswith(f"{NS}:")]
OURS |= {f"{NS}:{material}_chunk" for material in chunked}

OURS |= {f"{NS}:{name}" for name in re.findall(r'registerSimpleItem\("([a-z_]+)"\)',
                                               strip_comments(items_src, True))}
OURS |= {f"{NS}:{name}_seed" for name in seeds}
OURS |= {f"{NS}:{name}" for name in essences}
BELOW_TAGS = {f"c:plates/{m}" for m in ("iron", "copper", "gold", "steel")}
# Every ore tag elysium-ores actually declares, read out of its own data rather
# than listed here. A seed asks for the ore it grows, and an ore tag nothing
# fills is a recipe that cannot be made.
_ore_tags = os.path.join(ROOT, "elysium-ores",
                         "src/main/resources/data/c/tags/block/ores")
if os.path.isdir(_ore_tags):
    BELOW_TAGS |= {"c:ores/" + name[:-5] for name in os.listdir(_ore_tags)
                   if name.endswith(".json")}
else:
    fail("elysium-ores declares no ore tags where this expects them, so the seeds "
         "ask for ores nothing is known to fill")
below = set()
for module, namespace in (("elysium-industry", "elysiumindustry"),
                          ("elysium-kinetics", "elysiumkinetics")):
    folder = os.path.join(ROOT, module, "src/main/resources/data", namespace, "recipe")
    if os.path.isdir(folder):
        for name in os.listdir(folder):
            if name.endswith(".json"):
                below.add(f"{namespace}:{name[:-5]}")

recipe_root = os.path.join(RES, "data", NS, "recipe")
crafted = {}
if os.path.isdir(recipe_root):
    for name in sorted(os.listdir(recipe_root)):
        if name.endswith(".json"):
            crafted[name[:-5]] = json.load(
                open(os.path.join(recipe_root, name), encoding="utf-8"))


def ingredients(recipe):
    named = list(recipe.get("key", {}).values())
    named.extend(recipe.get("ingredients", []))
    out = []
    for entry in named:
        for one in (entry if isinstance(entry, list) else [entry]):
            if "item" in one:
                out.append(one["item"])
            elif "tag" in one:
                out.append("#" + one["tag"])
    return out


APIARY_RULES = read(SRC, "rules/ApiaryRules.java")
apiary_source = strip_comments(APIARY_RULES, True)

housings = [(one[0], one[1], int(one[2]), one[3]) for one in re.findall(
    r'new Housing\("([a-z_]+)",\s*(AgronomyRules\.MOST_BEES|\d+),\s*(\d+),'
    r'\s*"([a-z_]*)"\)', apiary_source)]
upgrades = [(one[0], int(one[1]), float(one[2]), one[3]) for one in re.findall(
    r'new Upgrade\("([a-z_]+)",\s*(\d+),\s*([\d.]+)F,\s*"([a-z ]*)"\)',
    apiary_source)]
centrifuge = re.search(r'CENTRIFUGE = "([a-z_]+)"', apiary_source)
centrifuge = centrifuge.group(1) if centrifuge else ""

if len(housings) < 2:
    fail(f"the housing ladder reads as {len(housings)} rung(s), which is not a "
         f"ladder — and every check below it is then checking nothing")
if not upgrades:
    fail("no upgrade can be read out of ApiaryRules, so the slots in every "
         "apiary are slots nothing goes in")
if not centrifuge:
    fail("ApiaryRules no longer says what the centrifuge is called, and a comb "
         "with nothing to put it in is a production chain that stops one block "
         "short of producing anything")

# Ours, so a recipe naming one of them is not a recipe naming somebody else's.
OURS |= {f"{NS}:{one[0]}" for one in housings}
OURS |= {f"{NS}:{one[0]}" for one in upgrades}
if centrifuge:
    OURS |= {f"{NS}:{centrifuge}"}
bee_materials = [bee for bee, in re.findall(
    r'resource\("([a-z_]+)",\s*"[a-z_]+",\s*"[a-z_]+"\)', BEE_RULES)] \
    if False else re.findall(
        r'resource\("([a-z_]+)", "[a-z_]+", "[a-z_]+"\)',
        strip_comments(BEE_RULES, True))
OURS |= {f"{NS}:{material}_chunk" for material in bee_materials}

ALTAR_RULES = read(SRC, "rules/AltarRules.java")
altar_source = strip_comments(ALTAR_RULES, True)


def altar_word(name):
    found = re.search(name + r'\s*=\s*"([a-z_0-9]+)"', altar_source)
    return found.group(1) if found else ""


CENTRE = altar_word("CENTRE")
PEDESTAL = altar_word("PEDESTAL")
RELAY = altar_word("RELAY")
INSANIUM = altar_word("INSANIUM")
TOP_ESSENCE = altar_word("TOP_ESSENCE")
ALTAR_BLOCKS = [one for one in (CENTRE, PEDESTAL, RELAY) if one]
if len(ALTAR_BLOCKS) != 3 or not INSANIUM or not TOP_ESSENCE:
    fail("AltarRules no longer says what an altar is made of or what the sixth "
         "rung is called, and every check below it is then checking nothing")

# The sixth rung and every awakened seed are ours, registered from a loop and
# so invisible to the reading that walks literal register("...") calls.
OURS |= {f"{NS}:{one}" for one in ALTAR_BLOCKS}
OURS |= {f"{NS}:{INSANIUM}"}
OURS |= {f"{NS}:awakened_{material}_seed" for material in seeds}

TACKLE_RULES = read(SRC, "rules/TackleRules.java")
tackle_source = strip_comments(TACKLE_RULES, True)
hooks = [(one[0], int(one[1]), int(one[2]), one[3]) for one in re.findall(
    r'new Hook\("([a-z_]+)", (\d+), (\d+), (true|false)\)', tackle_source)]
bobbers = [(one[0], int(one[1]), one[2], float(one[3])) for one in re.findall(
    r'new Bobber\("([a-z_]+)", (\d+), (true|false), ([\d.]+)F\)', tackle_source)]
worm_farm = re.search(r'WORM_FARM = "([a-z_]+)"', tackle_source)
tackle_box = re.search(r'TACKLE_BOX = "([a-z_]+)"', tackle_source)
worm_farm = worm_farm.group(1) if worm_farm else ""
tackle_box = tackle_box.group(1) if tackle_box else ""
if not hooks or not bobbers or not worm_farm or not tackle_box:
    fail(f"TackleRules no longer says what goes on a rod; this file read "
         f"{len(hooks)} hook(s) and {len(bobbers)} bobber(s), and every check "
         f"below is then checking nothing")

OURS |= {f"{NS}:{one[0]}" for one in hooks}
OURS |= {f"{NS}:{one[0]}" for one in bobbers}
OURS |= {f"{NS}:{worm_farm}", f"{NS}:{tackle_box}"}
OURS |= {f"{NS}:{fid}_mount" for fid in fish_ids}

CROP_RULES = read(SRC, "rules/CropRules.java")
crop_source = strip_comments(CROP_RULES, True)
crops = [{"id": one[0], "ground": one[1].lower(), "stages": int(one[2]),
          "ticks": int(one[3].replace("_", "")), "yield": int(one[4]),
          "nutrition": int(one[5]), "biomes": one[6]}
         for one in re.findall(
             r'new Crop\("([a-z_]+)", ([A-Z]+), (\d+), ([\d_]+), (\d+), '
             r'(\d+), "([a-z_]+)"\)', crop_source)]
crops = [one for one in crops if one["id"] != "wheat"]
larder = [one[0] for one in re.findall(
    r'new Larder\("([a-z_]+)", KitchenRules\.([A-Z]+)', crop_source)]
colony = re.search(r'COLONY = "([a-z_]+)"', crop_source)
colony = colony.group(1) if colony else ""
if len(crops) < 4 or not larder or not colony:
    fail(f"CropRules no longer has the shape this file reads: {len(crops)} "
         f"crop(s), {len(larder)} larder item(s); every check below it is then "
         f"checking nothing")

OURS |= {f"{NS}:{one['id']}_crop" for one in crops}
OURS |= {f"{NS}:{one['id']}_seeds" for one in crops}
OURS |= {f"{NS}:{colony}"}

for name in registered:
    if name not in crafted:
        fail(f"{name} has no crafting recipe, so it can only be given")
for material in seeds:
    if f"{material}_seed" not in crafted:
        fail(f"the {material} seed has no recipe, so that rung cannot be reached")
    if f"{material}_from_essence" not in crafted:
        fail(f"there is no way to turn {material} essence into {material}, so the whole "
             f"farm produces a pile of something with no use")

for name, recipe in crafted.items():
    for asked in ingredients(recipe):
        if asked.startswith("#"):
            if asked[1:] not in BELOW_TAGS and not asked.startswith("#minecraft:"):
                fail(f"recipe/{name} asks for the tag {asked}, which nothing in this "
                     f"install is guaranteed to fill")
        elif not (asked.startswith("minecraft:") or asked in OURS or asked in below):
            fail(f"recipe/{name} asks for {asked}; this module is buildable out of the "
                 f"base game and the modules below it, and nothing else")

# The first rung has to be reachable on day two, or a resource farm is an
# Age II feature and the pot has no early game to belong to.
first = ingredients(crafted.get("pot", {}))
if any(a.startswith("#c:plates/") or a.startswith(f"{IND_NS}:") for a in first):
    fail("a pot needs something only a factory makes, so farming arrives after "
         "electricity rather than long before it")

print(f"the farm in the world: {len(registered)} block(s), {len(seeds)} seed(s) across "
      f"{len(essences)} rung(s), each modelled, dropped, named and craftable; the pot "
      f"out of clay")

# ---------------------------------------------------------------------------
# 6. The seam
# ---------------------------------------------------------------------------

main_class = read(SRC, "ElysiumAgronomy.java")
if f'MODID = "{NS}"' not in main_class:
    fail("the module id is not what everything else in the tree spells it")

print("what it declares   : the game, the library, and the grid its machines hang off")


# ---------------------------------------------------------------------------
# 7. Seventy-five bees, and the four hundred and twenty-eight nobody can catch
# ---------------------------------------------------------------------------
#
# The jar this replaces registers 463 bee entities, of which 428 name a
# material no installed mod makes. They have spawn eggs, combs and
# translations, and no way on earth to obtain them. Nothing in that mod could
# notice, because the roster was typed out rather than derived.
#
# So the check that matters most in this file is the one that reads the
# foundry's own material list out of its source and refuses a bee that names
# something this pack cannot make.

if not bee_ids:
    fail("no bees could be read out of BeeRules.java, so nothing below this line "
         "checked anything at all")

# --- the headline check: every material a bee names, the pack can make -------

foundry_materials = set(re.findall(
    r'(?:mined|alloy|refined|grown|pure)\("([a-z_]+)"', foundry))
games_own = set(re.findall(r'THE_GAMES_OWN\s*=\s*Set\.of\(([^;]*)\);', bee_source))
own = set(re.findall(r'"([a-z_]+)"', games_own.pop())) if games_own else set()

if not own:
    fail("BeeRules declares no list of the game's own materials, so every vanilla "
         "resource bee below would read as a bee for a metal nothing makes")
if not foundry_materials:
    fail("no materials could be read out of FoundryRules, so the check that a bee "
         "names something real did not run")
else:
    uncatchable = [bid for bid, _, _ in resource_rows
                   if bid not in foundry_materials and bid not in own]
    if uncatchable:
        fail(f"{len(uncatchable)} resource bee(s) name a material neither the foundry "
             f"makes nor the game ships, so they can never be obtained — which is the "
             f"exact state the 428 dead bees in the jar this replaces are in: "
             + ", ".join(sorted(uncatchable)[:8]))
    unbeed = sorted(foundry_materials - {bid for bid, _, _ in resource_rows})
    if unbeed:
        fail(f"{len(unbeed)} material(s) the foundry makes have no bee, so the roster "
             f"is derived from nothing in particular: " + ", ".join(unbeed[:8]))

# --- the two shade() implementations have to agree --------------------------

def java_hash(text):
    value = 0
    for char in text:
        value = (value * 31 + ord(char)) & 0xFFFFFFFF
    if value >= 0x80000000:
        value -= 0x100000000
    return abs(value)


if "hash = hash * 31 + id.charAt(index);" not in squashed(body_of(BEE_ITEMS,
        "static int shade(String id, int base, int spread)")):
    fail("BeeItems.shade no longer multiplies by 31, so the colours it computes and "
         "the ones the generator draws have stopped being the same colours")

if "def java_hash(text):" not in read(WORK, "tools/gen_bees.py"):
    fail("the generator no longer computes the Java hash, so a comb and the egg that "
         "gives you the bee that makes it are no longer the same colour")

# --- the model and the texture have to agree about where things are ---------

model_body = re.search(r'texOffs\(0, 0\)', strip_comments(BEE_MODEL, True))
model_wings = re.findall(r'texOffs\(0, (\d+)\)', strip_comments(BEE_MODEL, True))
if model_body is None:
    fail("BeeModel no longer puts the body at texOffs(0, 0), and the generator draws "
         "it there — a model and a texture that disagree render as garbage rather "
         "than as an error")
if sorted(int(v) for v in model_wings) != [0, 12, 18]:
    fail(f"BeeModel's texture offsets are {sorted(int(v) for v in model_wings)} and the "
         f"generator draws into 0, 12 and 18")
if "LayerDefinition.create(mesh, 32, 32)" not in squashed(BEE_MODEL):
    fail("BeeModel no longer says its texture is 32 by 32, so the offsets above mean "
         "something different from what the generator drew")

# --- every bee has everything a bee needs -----------------------------------

def asset(*parts):
    return os.path.isfile(os.path.join(RES, "assets", NS, *parts))


def datum(*parts):
    return os.path.isfile(os.path.join(RES, "data", NS, *parts))


missing = []
for bid in bee_ids:
    if not asset("models/item", bid + "_comb.json"):
        missing.append(bid + " comb model")
    if not asset("textures/item", bid + "_comb.png"):
        missing.append(bid + " comb texture")
    if not asset("models/item", bid + "_bee_spawn_egg.json"):
        missing.append(bid + " spawn egg model")
    if not asset("models/block", bid + "_comb_block.json"):
        missing.append(bid + " comb block model")
    if not asset("blockstates", bid + "_comb_block.json"):
        missing.append(bid + " comb blockstate")
    if not asset("models/item", bid + "_comb_block.json"):
        missing.append(bid + " comb block item model")
    if not asset("textures/block", bid + "_comb_block.png"):
        missing.append(bid + " comb block texture")
    if not asset("textures/entity/bee", bid + ".png"):
        missing.append(bid + " entity texture")
    if not datum("loot_table/blocks", bid + "_comb_block.json"):
        missing.append(bid + " comb block loot")
    if not datum("recipe", bid + "_comb_block.json"):
        missing.append(bid + " comb block recipe")
for home in bee_homes:
    if not asset("models/block", home + "_nest.json"):
        missing.append(home + " nest model")
    if not asset("blockstates", home + "_nest.json"):
        missing.append(home + " nest blockstate")
    if not datum("loot_table/blocks", home + "_nest.json"):
        missing.append(home + " nest loot")
    for face in ("top", "side", "front"):
        if not asset("textures/block", f"{home}_nest_{face}.png"):
            missing.append(f"{home} nest {face} texture")
if missing:
    fail(f"{len(missing)} thing(s) a bee needs are not there — a bee without one is a "
         f"registry entry a player meets as a magenta cube or an unbreakable block: "
         + ", ".join(missing[:8]) + (" ..." if len(missing) > 8 else ""))

# --- named, every one of them -----------------------------------------------

unnamed = []
for bid in bee_ids:
    for key in (f"entity.{NS}.{bid}_bee", f"item.{NS}.{bid}_comb",
                f"item.{NS}.{bid}_bee_spawn_egg", f"block.{NS}.{bid}_comb_block"):
        if key not in lang:
            unnamed.append(key)
for home in bee_homes:
    if f"block.{NS}.{home}_nest" not in lang:
        unnamed.append(f"block.{NS}.{home}_nest")
if unnamed:
    fail(f"{len(unnamed)} bee thing(s) have no name and would show their id: "
         + ", ".join(unnamed[:6]) + (" ..." if len(unnamed) > 6 else ""))

# --- the registries count themselves ----------------------------------------

# The whole statement, not the throw inside it. A rule that names only
# `throw new IllegalStateException` goes on passing when somebody writes
# `if (false)` in front of it, which is the fifteenth time this project has
# been caught by that and the reason check_rules.py exists.
# The whole statement, condition and all. Naming only the throw is half a
# statement — `if (false)` in front of it leaves the throw exactly where it is
# and the rule goes on passing — and check_rules.py refuses that, which is how
# this rule came to be written properly on the second attempt.
COUNTED = re.compile(
    r"if \(made\.size\(\) != [A-Za-z_][A-Za-z0-9_.()]*\)"
    r" \{ throw new IllegalStateException\(")


def unguarded(source, where):
    """Every registerX() in a file that does not count what it built.

    Looking for the guard anywhere in the file is not enough, and was written
    that way first: a file with three registration methods goes on passing
    when one of them loses its guard. Each method is looked at on its own.
    """
    body = squashed(source)
    bad = []
    for name in re.findall(r"\b(register[A-Za-z]*)\(\) \{", body):
        if not COUNTED.search(body_of(body, name + "() {")):
            bad.append(f"{where}.{name}")
    return bad


ungated = []
for where, source in (("BeeEntities", BEE_ENTITIES), ("BeeItems", BEE_ITEMS),
                      ("BeeBlocks", BEE_BLOCKS)):
    ungated.extend(unguarded(source, where))
if ungated:
    fail("registration method(s) that do not count what they built against the "
         "roster, so seventy-four of seventy-five would ship and be found by "
         "whoever breeds the seventy-fifth: " + ", ".join(ungated))

# Inside register() specifically. BeeEntities walks the roster three times —
# to register, to give attributes, to place spawns — so a rule that just looks
# for the loop anywhere in the file goes on passing while the one loop that
# matters has been narrowed to the first three bees.
REGISTERING = body_of(squashed(BEE_ENTITIES), "register() {")
if not REGISTERING:
    fail("BeeEntities has no register() this can read, so nothing below checked "
         "what it registers")
elif "for (BeeRules.Bee bee : BeeRules.BEES) {" not in REGISTERING:
    fail("BeeEntities.register no longer walks the whole roster, so the entity types "
         "and the rules can disagree about which bees exist")

# --- the entity textures are the size the model says ------------------------

wrong_size = []
for bid in bee_ids[:200]:
    path = os.path.join(RES, "assets", NS, "textures/entity/bee", bid + ".png")
    if os.path.isfile(path):
        header = open(path, "rb").read(33)
        if len(header) >= 33:
            width = int.from_bytes(header[16:20], "big")
            height = int.from_bytes(header[20:24], "big")
            if (width, height) != (32, 32):
                wrong_size.append(f"{bid} is {width}x{height}")
if wrong_size:
    fail("entity texture(s) are not the 32 by 32 the model bakes against: "
         + ", ".join(wrong_size[:5]))

# --- and the generator has to be the only author ----------------------------

print(f"bees               : {len(bee_ids)} species in {len(bee_homes)} home(s), "
      f"every resource bee naming a material the foundry makes or the game ships, "
      f"and {len(foundry_materials)} of {len(foundry_materials)} foundry materials "
      f"with a bee of their own")


# ---------------------------------------------------------------------------
# 8. A hundred and five fish, and whether any of them can be caught
# ---------------------------------------------------------------------------

if not fish_ids:
    fail("no fish could be read out of FishRules.java, so nothing below this line "
         "checked anything at all")
if len(fish_families) != 11:
    fail(f"{len(fish_families)} fish famil(ies) were read where eleven were written, "
         f"so the roster's shape has changed and the generator is reading it wrong")

missing_fish = []
for fid in fish_ids:
    if not asset("models/item", fid + ".json"):
        missing_fish.append(fid + " item model")
    if not asset("textures/item", fid + ".png"):
        missing_fish.append(fid + " item texture")
    if not asset("models/item", fid + "_bucket.json"):
        missing_fish.append(fid + " bucket model")
    if not asset("textures/item", fid + "_bucket.png"):
        missing_fish.append(fid + " bucket texture")
    if not asset("models/item", fid + "_spawn_egg.json"):
        missing_fish.append(fid + " spawn egg model")
    if not asset("textures/entity/fish", fid + ".png"):
        missing_fish.append(fid + " entity texture")
if missing_fish:
    fail(f"{len(missing_fish)} thing(s) a fish needs are not there — a fish without "
         f"one is a magenta cube in the water or an item with no picture: "
         + ", ".join(missing_fish[:8]) + (" ..." if len(missing_fish) > 8 else ""))

unnamed_fish = []
for fid in fish_ids:
    for key in (f"entity.{NS}.{fid}", f"item.{NS}.{fid}",
                f"item.{NS}.{fid}_bucket", f"item.{NS}.{fid}_spawn_egg"):
        if key not in lang:
            unnamed_fish.append(key)
for name in rod_names:
    if f"item.{NS}.{name}" not in lang:
        unnamed_fish.append(f"item.{NS}.{name}")
for family in fish_families.values():
    if f"item.{NS}.{family}_bait" not in lang:
        unnamed_fish.append(f"item.{NS}.{family}_bait")
if unnamed_fish:
    fail(f"{len(unnamed_fish)} fish thing(s) have no name and would show their id: "
         + ", ".join(unnamed_fish[:6]) + (" ..." if len(unnamed_fish) > 6 else ""))

# A rod per rung, or a rung of the ladder the rules describe cannot be held.
rungs = re.search(r"public static final int RODS = (\d+);", fish_source)
if rungs is None:
    fail("FishRules no longer says how many rungs of rod there are")
elif len(rod_names) != int(rungs.group(1)):
    fail(f"the rules describe {rungs.group(1)} rungs of rod and {len(rod_names)} rod(s) "
         f"are registered, so a rung of the ladder cannot be held and its tilt does "
         f"nothing")

# A bait per family, or a habitat cannot be fished for on purpose.
baits = set(re.findall(r'family\.name\(\) \+ "_bait"', strip_comments(FISH_ITEMS, True)))
if not baits:
    fail("the baits are no longer one per family, so some habitat has no bait and "
         "cannot be fished for on purpose")

ungated_fish = []
for where, source in (("FishEntities", FISH_ENTITIES), ("FishItems", FISH_ITEMS)):
    ungated_fish.extend(unguarded(source, where))
if ungated_fish:
    fail("registration method(s) that do not count what they built against the "
         "roster, so a hundred and four of a hundred and five would ship: "
         + ", ".join(ungated_fish))

FISH_REGISTERING = body_of(squashed(FISH_ENTITIES), "register() {")
if not FISH_REGISTERING:
    fail("FishEntities has no register() this can read")
elif "for (FishRules.Fish one : FishRules.FISH) {" not in FISH_REGISTERING:
    fail("FishEntities.register no longer walks the whole roster, so the entity types "
         "and the rules can disagree about which fish exist")

fish_offsets = sorted(int(v) for v in re.findall(r'texOffs\(0, (\d+)\)',
                                                 strip_comments(FISH_MODEL, True)))
if fish_offsets != [0, 8]:
    fail(f"FishModel's texture offsets are {fish_offsets} and the generator draws into "
         f"0 and 8 — a model and a texture that disagree render as garbage rather than "
         f"as an error")
if "LayerDefinition.create(mesh, 32, 32)" not in squashed(FISH_MODEL):
    fail("FishModel no longer says its texture is 32 by 32, so the offsets above mean "
         "something different from what the generator drew")

# The hitbox and the drawn size both come from the length, or a fish looks
# nothing like the thing you are standing next to.
if "one.length() / 100.0F" not in squashed(FISH_ENTITIES):
    fail("FishEntities no longer sizes a fish from its own length, so the hitbox is a "
         "number somebody chose and will stop matching the roster")
if "fish.rules().length() / MODEL_CENTIMETRES" not in squashed(
        read(SRC, "client/FishRenderer.java")):
    fail("FishRenderer no longer scales a fish by its own length, so what you see and "
         "what you bump into are two different sizes")

wrong_fish_size = []
for fid in fish_ids[:200]:
    path = os.path.join(RES, "assets", NS, "textures/entity/fish", fid + ".png")
    if os.path.isfile(path):
        header = open(path, "rb").read(33)
        if len(header) >= 33:
            width = int.from_bytes(header[16:20], "big")
            height = int.from_bytes(header[20:24], "big")
            if (width, height) != (32, 32):
                wrong_fish_size.append(f"{fid} is {width}x{height}")
if wrong_fish_size:
    fail("fish entity texture(s) are not the 32 by 32 the model bakes against: "
         + ", ".join(wrong_fish_size[:5]))

print(f"fish               : {len(fish_ids)} species across {len(fish_families)} "
      f"famil(ies), {len(rod_names)} rod(s) and a bait apiece, every one of them "
      f"modelled, bucketed, named and catchable")


# ---------------------------------------------------------------------------
# 9. A hundred and sixty-five pots, and the soil that goes in them
# ---------------------------------------------------------------------------

if not pot_ids:
    fail("no pots could be read out of PotRules.java, so nothing below this line "
         "checked anything at all")
if len(pot_colours) != 16:
    fail(f"{len(pot_colours)} dyes were read where sixteen were written, so the "
         f"catalogue has lost a colour and only the player who wanted it finds out")

expected_pots = len(pot_materials) * 3
if len(pot_ids) != expected_pots:
    fail(f"{len(pot_ids)} pots were derived where {expected_pots} were expected — "
         f"every material has three fittings or half the catalogue is missing")

registered_pots = set(re.findall(r'BLOCKS\.register\(pot\.id\(\)',
                                 strip_comments(POT_BLOCKS, True)))
if not registered_pots:
    fail("PotBlocks no longer registers a block per pot id, so the catalogue and "
         "the registry can disagree about which pots exist")
if "for (PotRules.Pot pot : PotRules.catalogue()) {" not in squashed(POT_BLOCKS):
    fail("PotBlocks no longer walks the catalogue, so the pots are written out "
         "rather than derived")

ungated_pots = unguarded(POT_BLOCKS, "PotBlocks")
if ungated_pots:
    fail("pot registration method(s) that do not count what they built: "
         + ", ".join(ungated_pots))

# A pot that cannot grow anything is an ornament, and the difference is
# invisible until somebody plants in one. The catalogue blocks have to be
# PotBlocks, their block entity type has to name them, and their blockstates
# have to carry the age property that block declares — a blockstate with one
# variant against a block with a property is a file the game refuses to load.
if "new com.elysium.agronomy.block.PotBlock(" not in squashed(POT_BLOCKS):
    fail("the catalogue pots are plain blocks, so a hundred and sixty-five of them "
         "can be placed and filled and will grow nothing at all")
entities_src = read(SRC, "registry/AgronomyBlockEntities.java")
if "for (com.elysium.agronomy.rules.PotRules.Pot pot " \
        ": com.elysium.agronomy.rules.PotRules.catalogue()) {" \
        not in squashed(entities_src):
    fail("the pot block entity type no longer names the whole catalogue, so some "
         "pots attach no block entity and silently do nothing")

missing_pots = []
for pid in pot_ids:
    for step in range(4):
        if not asset("models/block", f"{pid}_{step}.json"):
            missing_pots.append(f"{pid} model at age {step}")
    states = os.path.join(RES, "assets", NS, "blockstates", pid + ".json")
    if os.path.isfile(states):
        variants = json.load(open(states, encoding="utf-8")).get("variants", {})
        if set(variants) != {f"age={step}" for step in range(4)}:
            missing_pots.append(pid + " blockstate has "
                                + ", ".join(sorted(variants)) + " rather than four ages")
    if not asset("blockstates", pid + ".json"):
        missing_pots.append(pid + " blockstate")
    if not asset("models/item", pid + ".json"):
        missing_pots.append(pid + " item model")
    if not asset("textures/block", pid + ".png"):
        missing_pots.append(pid + " texture")
    if not datum("loot_table/blocks", pid + ".json"):
        missing_pots.append(pid + " loot")
    if not datum("recipe", pid + ".json"):
        missing_pots.append(pid + " recipe")
for soil in our_soils:
    if not asset("textures/block", soil + ".png"):
        missing_pots.append(soil + " texture")
    if not asset("blockstates", soil + ".json"):
        missing_pots.append(soil + " blockstate")
    if not datum("recipe", soil + ".json"):
        missing_pots.append(soil + " recipe")
if missing_pots:
    fail(f"{len(missing_pots)} thing(s) a pot needs are not there: "
         + ", ".join(missing_pots[:8]) + (" ..." if len(missing_pots) > 8 else ""))

unnamed_pots = [pid for pid in pot_ids if f"block.{NS}.{pid}" not in lang]
unnamed_pots += [s for s in our_soils if f"block.{NS}.{s}" not in lang]
if unnamed_pots:
    fail(f"{len(unnamed_pots)} pot thing(s) have no name and would show their id: "
         + ", ".join(unnamed_pots[:6]) + (" ..." if len(unnamed_pots) > 6 else ""))

# Every soil in the table is a block somebody can actually get, whether the
# game's or ours. A rung of the ladder a player can read about and never build
# is the same hole as a bee for a metal nothing makes.
soil_files = os.path.join(RES, "data", NS, "botany_soil")
if not os.path.isdir(soil_files):
    fail("the soils are not written out as data, so a pack cannot add one without "
         "a compiler")
else:
    written = {name[:-5] for name in os.listdir(soil_files) if name.endswith(".json")}
    if written != set(pot_soils):
        fail(f"the soils in the rules and the soils on disk disagree: "
             f"{sorted(set(pot_soils) ^ written)[:6]}")
    homeless = []
    for name in sorted(written):
        block = json.load(open(os.path.join(soil_files, name + ".json"),
                               encoding="utf-8")).get("block", "")
        if block.startswith(f"{NS}:") and block.split(":", 1)[1] not in our_soils:
            homeless.append(name)
    if homeless:
        fail("soil(s) the table says are ours and nothing registers: "
             + ", ".join(homeless))

sprout_files = os.path.join(RES, "data", NS, "botany_sprout")
if not os.path.isdir(sprout_files):
    fail("the sprouts are not written out as data")
else:
    written = {name[:-5] for name in os.listdir(sprout_files) if name.endswith(".json")}
    if written != set(pot_sprouts):
        fail(f"the sprouts in the rules and the sprouts on disk disagree: "
             f"{sorted(set(pot_sprouts) ^ written)[:6]}")

print(f"pots               : {len(pot_ids)} across {len(pot_materials)} material(s) "
      f"with all three fittings, {len(pot_soils)} soil(s) of which {len(our_soils)} "
      f"are ours, and {len(pot_sprouts)} sprout(s), each written out as data")


# ---------------------------------------------------------------------------
# 10. Seven generators, and the reason a generator is not a mine
# ---------------------------------------------------------------------------

if not vein_rows:
    fail("no veins could be read out of AgronomyRules.java, so nothing below this "
         "line checked anything at all")

vein_at_most = re.search(r"VEIN_AT_MOST = ([\d.]+)F;",
                         strip_comments(read(SRC, "rules/AgronomyRules.java"), True))
if vein_at_most is None:
    fail("there is no line above which a generator out-earns a mine, so the seven "
         "generators are priced by taste")
else:
    ceiling = float(vein_at_most.group(1))
    over = [m for m, t, s in vein_rows if m != "stone" and float(s) > ceiling]
    if over:
        fail(f"generator(s) worth more than {ceiling} of a mine, which is the most "
             f"direct way there is to make three ages of ore ladder pointless: "
             + ", ".join(over))

shares = [float(s) for m, t, s in vein_rows]
if shares != sorted(shares, reverse=True):
    fail("the veins do not get slower as the material gets better, so the cheap "
         "ones are decoration")
tiers = [int(t) for m, t, s in vein_rows]
if tiers != sorted(tiers):
    fail("a dearer material needs a cheaper generator than a commoner one, so the "
         "ladder runs backwards")

missing_veins = []
for material, tier, share in vein_rows:
    name = material + "_generator"
    if not asset("models/block", name + ".json"):
        missing_veins.append(name + " model")
    if not asset("blockstates", name + ".json"):
        missing_veins.append(name + " blockstate")
    if not asset("models/item", name + ".json"):
        missing_veins.append(name + " item model")
    for face in ("side", "top"):
        if not asset("textures/block", f"{name}_{face}.png"):
            missing_veins.append(f"{name} {face} texture")
    if not datum("loot_table/blocks", name + ".json"):
        missing_veins.append(name + " loot")
    if not datum("recipe", name + ".json"):
        missing_veins.append(name + " recipe")
    if f"block.{NS}.{name}" not in lang:
        missing_veins.append(name + " name")
if missing_veins:
    fail(f"{len(missing_veins)} thing(s) a generator needs are not there: "
         + ", ".join(missing_veins[:8]) + (" ..." if len(missing_veins) > 8 else ""))

# The whole loop header, VEINS and all. Naming only its first half is half a
# statement: narrowing the list to `VEINS.subList(0, 3)` leaves the prefix
# exactly where it is and three of seven generators ship.
VEIN_LOOP = ("for (com.elysium.agronomy.rules.AgronomyRules.Vein vein "
             ": com.elysium.agronomy.rules.AgronomyRules.VEINS) {")
for where, source in (("AgronomyBlocks", blocks_src), ("AgronomyItems", items_src)):
    body = squashed(source)
    if VEIN_LOOP not in body:
        fail(f"{where} no longer walks the whole vein table, so the generators are "
             f"written out rather than derived, or some of them are missing")
    if not COUNTED.search(body_of(body, "registerVeins() {")):
        fail(f"{where}.registerVeins does not count what it built against the table, "
             f"so six of seven would ship")

# Every vein says what comes out of it. This was not so at first: all seven
# generators were built with a tier and nothing else, so the gold one made
# cobblestone and nothing would have said so until somebody built one and
# waited an hour.
rock_block = read(SRC, "block/RockGeneratorBlock.java")
from_vein = re.search(r"FROM_VEIN = Map\.of\(([^;]*?)\);",
                      strip_comments(rock_block, True), re.S)
if from_vein is None:
    fail("RockGeneratorBlock no longer says what comes out of each vein, so every "
         "generator in the table makes the same thing")
else:
    named = set(re.findall(r'"([a-z_]+)",\s*Items\.', from_vein.group(1)))
    unsaid = [m for m, t, sh in vein_rows if m not in named]
    if unsaid:
        fail("vein(s) with nothing said about what comes out of them, so they make "
             "cobblestone like everything else: " + ", ".join(unsaid))
    spare = sorted(named - {m for m, t, sh in vein_rows})
    if spare:
        fail("thing(s) named as coming out of a vein that is not in the table: "
             + ", ".join(spare))

if "new RockGeneratorBlock(steel(), vein)" not in squashed(blocks_src):
    fail("the vein generators are no longer built from their vein, so they are seven "
         "copies of the same block with different sides")

print(f"generators         : {len(vein_rows)} vein(s), each under {vein_at_most.group(1) if vein_at_most else '?'} "
      f"of a mine and each slower than the vein below it")


# ---------------------------------------------------------------------------
# 11. A kitchen, and whether more than three of its meals are worth making
# ---------------------------------------------------------------------------

if not kitchen_meals:
    fail("no meals could be read out of KitchenRules.java, so nothing below this "
         "line checked anything at all")
if not kitchen_knives:
    fail("no knives could be read out of KitchenItems.java, so the cutting board "
         "has nothing to cut with")

# Every effect a meal names is an effect the game has. A meal naming one that
# is not mapped would silently have no effect at all, which is the one thing
# that could make two meals identical after the harness has proved they are not.
named_effects = {row[7] for row in kitchen_meals if row[7]}
mapped = set(re.findall(r'Map\.entry\("([a-z_]+)", MobEffects\.',
                        strip_comments(KITCHEN_ITEMS, True)))
unmapped = sorted(named_effects - mapped)
if unmapped:
    fail("meal effect(s) with nothing in the game behind them, so those meals "
         "silently do nothing and two meals the harness proved different become "
         "the same: " + ", ".join(unmapped))
spare_effects = sorted(mapped - named_effects)
if spare_effects:
    fail("effect(s) mapped to the game that no meal ever names: "
         + ", ".join(spare_effects))

ungated_kitchen = unguarded(KITCHEN_ITEMS, "KitchenItems")
ungated_kitchen += unguarded(KITCHEN_BLOCKS, "KitchenBlocks")
if ungated_kitchen:
    fail("kitchen registration method(s) that do not count what they built: "
         + ", ".join(ungated_kitchen))

# A station in the rules has a block, or meals are cooked on nothing.
for station in ("POT", "SKILLET", "BOARD", "OVEN"):
    if station not in kitchen_stations:
        fail(f"meals are cooked on the {station.lower()} and no block is registered "
             f"for one")

missing_kitchen = []
for part in kitchen_parts:
    if not asset("models/item", part + ".json"):
        missing_kitchen.append(part + " model")
    if not asset("textures/item", part + ".png"):
        missing_kitchen.append(part + " texture")
for row in kitchen_meals:
    if not asset("models/item", row[0] + ".json"):
        missing_kitchen.append(row[0] + " model")
    if not asset("textures/item", row[0] + ".png"):
        missing_kitchen.append(row[0] + " texture")
    if not datum("cooking", row[0] + ".json"):
        missing_kitchen.append(row[0] + " cooking recipe")
for name in kitchen_blocks:
    if not asset("blockstates", name + ".json"):
        missing_kitchen.append(name + " blockstate")
    if not datum("loot_table/blocks", name + ".json"):
        missing_kitchen.append(name + " loot")
    if not datum("recipe", name + ".json"):
        missing_kitchen.append(name + " recipe")
for knife in kitchen_knives:
    if not asset("models/item", knife + "_knife.json"):
        missing_kitchen.append(knife + " knife model")
    if not datum("recipe", knife + "_knife.json"):
        missing_kitchen.append(knife + " knife recipe")
for cut in kitchen_cuts:
    if not datum("cutting", cut + ".json"):
        missing_kitchen.append(cut + " cutting recipe")
if missing_kitchen:
    fail(f"{len(missing_kitchen)} thing(s) the kitchen needs are not there: "
         + ", ".join(missing_kitchen[:8]) + (" ..." if len(missing_kitchen) > 8 else ""))

unnamed_kitchen = [p for p in kitchen_parts if f"item.{NS}.{p}" not in lang]
unnamed_kitchen += [r[0] for r in kitchen_meals if f"item.{NS}.{r[0]}" not in lang]
unnamed_kitchen += [n for n in kitchen_blocks if f"block.{NS}.{n}" not in lang]
if unnamed_kitchen:
    fail(f"{len(unnamed_kitchen)} kitchen thing(s) have no name: "
         + ", ".join(unnamed_kitchen[:6]) + (" ..." if len(unnamed_kitchen) > 6 else ""))

# The cooking recipes on disk say what the rules say. A pack can add a meal by
# dropping a file in, so the two have to be checked against each other rather
# than assumed to agree.
cooking_dir = os.path.join(RES, "data", NS, "cooking")
if not os.path.isdir(cooking_dir):
    fail("the meals are not written out as data, so a pack cannot add one without "
         "a compiler")
else:
    on_disk = {name[:-5] for name in os.listdir(cooking_dir) if name.endswith(".json")}
    in_rules = {row[0] for row in kitchen_meals}
    if on_disk != in_rules:
        fail("the meals in the rules and the meals on disk disagree: "
             + ", ".join(sorted(on_disk ^ in_rules)[:6]))
    else:
        drifted_meals = []
        for row in kitchen_meals:
            written = json.load(open(os.path.join(cooking_dir, row[0] + ".json"),
                                     encoding="utf-8"))
            if (written.get("ticks") != int(row[4])
                    or written.get("servings") != int(row[5])
                    or written.get("station") != row[6].lower()
                    or written.get("effect") != row[7]):
                drifted_meals.append(row[0])
        if drifted_meals:
            fail("meal(s) whose data file says something different from the rules: "
                 + ", ".join(drifted_meals[:6]))

print(f"kitchen            : {len(kitchen_meals)} meal(s) across "
      f"{len(kitchen_stations)} station(s) with {len(named_effects)} effect(s), "
      f"{len(kitchen_parts)} ingredient(s), {len(kitchen_cuts)} cut(s) and "
      f"{len(kitchen_blocks)} block(s), none of them pointless")


# ---------------------------------------------------------------------------
# 12. A diet, and the book that shows it
# ---------------------------------------------------------------------------
#
# The bonus is an attribute modifier with a stable id, replaced rather than
# added. A modifier added twice is a player who ate the same apple twice and
# got paid for it, and that is the single bug this mechanic is most likely to
# have.

DIET_EVENTS = read(SRC, "event/DietEvents.java")
DIET_ATTACH = read(SRC, "registry/DietAttachments.java")
diet = squashed(DIET_EVENTS)

if "health.removeModifier(VARIETY);" not in diet:
    fail("the diet bonus is added without the old one being taken off, so a player "
         "who eats two new things has two bonuses and one of them is for ever")
if "addPermanentModifier" not in diet:
    fail("the diet bonus is not permanent, so it is gone the next time the player "
         "logs in and the mechanic silently does nothing")
if "AttributeModifier.Operation.ADD_VALUE" not in diet:
    fail("the diet bonus is not a flat addition, so its size depends on what else "
         "the player is wearing")
if "AgronomyRules.extraHealth(" not in diet:
    fail("the diet bonus is a number of its own rather than the one the rules "
         "compute, so retuning the rule changes nothing anybody has")
if "player.getHealth() > player.getMaxHealth()" not in diet:
    fail("nothing clamps a player's health when the bonus shrinks, so retuning the "
         "rule downwards leaves players above their own maximum")

attach = squashed(DIET_ATTACH)
if "new LinkedHashSet<>(" not in attach:
    fail("what a player has eaten is not kept as a set, so eating thirty loaves of "
         "bread is worth as much as eating thirty different things — which is the "
         "exact behaviour the bonus exists to discourage")
if ".copyOnDeath()" not in attach:
    fail("what a player has eaten is lost on death, so the mechanic is a punishment "
         "for dying rather than a reward for exploring")

if not asset("models/item", "food_book.json"):
    fail("the food book has no model")
if not asset("textures/item", "food_book.png"):
    fail("the food book has no texture")
if not datum("recipe", "food_book.json"):
    fail("the food book cannot be made")
if f"item.{NS}.food_book" not in lang:
    fail("the food book has no name")
for line in ("elysiumagronomy.diet.read", "elysiumagronomy.diet.tip"):
    if line not in lang:
        fail(f"the book says {line}, which is not translated, so it shows the key")


# ---------------------------------------------------------------------------
# The apiary: the ladder, the upgrades, and the centrifuge at the end of it
# ---------------------------------------------------------------------------


# The ceiling, which is the whole reason this system is arithmetic.
if "public static int ticksForTheCeiling()" not in apiary_source:
    fail("the ticks a comb takes are no longer solved against the ceiling, so "
         "the top of the ladder is a number somebody chose and the rule it is "
         "meant to respect is a comment")
if "ApiaryRules.ticksForTheCeiling()" not in strip_comments(BEE_RULES, True):
    fail("BeeRules no longer takes its first resource comb from the ceiling, so "
         "retuning the mine leaves the best apiary over the line and nothing "
         "anywhere says so")
if "float ticks = BeeRules.combTicks(tier) / speedIn(fitted);" not in apiary_source:
    fail("a speed upgrade multiplies the bees rather than shortening the comb, "
         "which is the same number today and a different one the moment a comb "
         "yields more than one of anything")

# Every rung, every upgrade and the centrifuge: a block or an item, a model, a
# name and a recipe. A rung with no recipe is a rung nobody climbs.
lang_file = json.loads(read(RES, "assets", NS, "lang/en_us.json"))
for name, _, slots, built in housings:
    if not built:
        continue
    for folder, kind in (("models/block", "block model"),
                         ("models/item", "item model"),
                         ("blockstates", "blockstate")):
        if not os.path.isfile(os.path.join(RES, "assets", NS, folder,
                                           name + ".json")):
            fail(f"{name} has no {kind}, so a rung of the ladder is a purple "
                 f"cube")
    if not os.path.isfile(os.path.join(RES, "data", NS, "recipe",
                                       name + ".json")):
        fail(f"{name} has no recipe, so a rung of the ladder is a block only "
             f"the creative menu has")
    else:
        recipe = json.loads(read(RES, "data", NS, "recipe", name + ".json"))
        asked = json.dumps(recipe.get("key", {}))
        if f"{NS}:{built}" not in asked:
            fail(f"{name} is built out of {built} in the rules and its recipe "
                 f"does not ask for one, so the ladder in the rules and the "
                 f"ladder a player climbs are two different ladders")
    if not os.path.isfile(os.path.join(RES, "data", NS, "loot_table/blocks",
                                       name + ".json")):
        fail(f"{name} drops nothing when it is broken, so an apiary full of "
             f"bees is one misclick from gone")
    if f"block.{NS}.{name}" not in lang_file:
        fail(f"{name} has no name a player reads")

for name, bees, faster, attention in upgrades:
    if not os.path.isfile(os.path.join(RES, "assets", NS, "models/item",
                                       name + ".json")):
        fail(f"{name} has no model")
    if not os.path.isfile(os.path.join(RES, "data", NS, "recipe",
                                       name + ".json")):
        fail(f"{name} has no recipe, so an upgrade slot is a slot nothing goes "
             f"in")
    if f"item.{NS}.{name}" not in lang_file:
        fail(f"{name} has no name a player reads")
    if bees == 0 and faster == 1.0 and not attention:
        fail(f"{name} earns nothing and takes no chore away, so it is a recipe "
             f"that costs something and does nothing")

if centrifuge:
    for folder in ("models/block", "models/item", "blockstates"):
        if not os.path.isfile(os.path.join(RES, "assets", NS, folder,
                                           centrifuge + ".json")):
            fail(f"the centrifuge has no {folder.split('/')[-1]}")
    if not os.path.isfile(os.path.join(RES, "data", NS, "recipe",
                                       centrifuge + ".json")):
        fail("the centrifuge has no recipe")

# Every comb a centrifuge takes, in the tag it reads, walked from the roster.
combs_tag = os.path.join(RES, "data", NS, "tags/item/combs.json")
if not os.path.isfile(combs_tag):
    fail("there is no tag of combs, so the centrifuge accepts nothing at all "
         "and every comb in the pack stays a thing you look at")
else:
    listed = set(json.loads(read(combs_tag)).get("values", []))
    missing = [bid for bid in bee_ids if f"{NS}:{bid}_comb" not in listed]
    if missing:
        fail(f"{len(missing)} comb(s) are not in the tag the centrifuge reads, "
             f"so those bees produce something with nothing to put it in: "
             f"{missing[:5]}")

# Every material a comb spins into has a chunk, a sprite and a name.
for material in bee_materials:
    chunk = material + "_chunk"
    if not os.path.isfile(os.path.join(RES, "assets", NS, "models/item",
                                       chunk + ".json")):
        fail(f"{material} is what a bee makes and there is no {chunk} model, so "
             f"its comb spins into an item the game draws as a purple cube")
    if f"item.{NS}.{chunk}" not in lang_file:
        fail(f"{chunk} has no name a player reads")
    tag = os.path.join(RES, "data/c/tags/item/raw_materials", material + ".json")
    if not os.path.isfile(tag):
        fail(f"{chunk} carries no raw-material tag, so a comb of {material} "
             f"and an ore of {material} end up in two different furnaces")

# The block itself: it must ask the rules what comes out rather than decide.
spinner = strip_comments(read(SRC, "block/CentrifugeBlock.java"))
spinning = body_of(spinner, "protected void serverTick(")
if "ApiaryRules.spun(bee)" not in spinning:
    fail("the centrifuge decides for itself what a comb comes apart into, which "
         "is a second opinion about what a bee produces and one opinion too many")
if "ApiaryRules.spunCounts(bee)" not in spinning:
    fail("the centrifuge decides for itself how much a comb is worth, so the "
         "yield a validator checks and the yield a player gets are two numbers")
if "share() <= 0.0F || burned()" not in spinner:
    fail("a centrifuge spins with no power or with a burned grid, so the one "
         "cost of running it is optional")
if "items.getStackInSlot(IN).isEmpty() ? null" not in spinner:
    fail("a centrifuge with nothing in it still taxes the grid, so one somebody "
         "walled off is a permanent cost with no effect")
if "if (!room(bee)) {" not in spinner:
    fail("a centrifuge spins a comb without checking there is room for what "
         "comes out, so a full output slot is a comb destroyed")

print(f"the apiary         : {len(housings)} rung(s) of housing, "
      f"{len(upgrades)} upgrade(s) of which "
      f"{len([u for u in upgrades if u[1] or u[2] > 1.0])} earn and the rest buy "
      f"attention, a centrifuge, and {len(bee_materials)} material(s) a comb "
      f"comes apart into")


# ---------------------------------------------------------------------------
# The altar: the sixth rung, and the thing awakening is not
# ---------------------------------------------------------------------------

# The one name this file has to state twice. AltarRules cannot read the
# registry — the registry imports the game and the rules may not — so the top
# essence is written in both and checked here.
if TOP_ESSENCE and essences and TOP_ESSENCE != essences[-1]:
    fail(f"AltarRules says the top of the essence ladder is {TOP_ESSENCE} and "
         f"the registry's last essence is {essences[-1]}; the altar would ask "
         f"for an item that has never existed and nothing in the game would "
         f"say which of the two was wrong")

if "public static List<String> faultsInEconomy()" not in altar_source:
    fail("the altar no longer checks what awakening costs, and doubling a "
         "yield without halving the plots is a farm that beats a mine")
if "float allowed = AgronomyRules.minedPerHour() * AgronomyRules.FARM_AT_MOST;" \
        not in altar_source:
    fail("an awakened farm's allowance is not worked out from the same margin "
         "an ordinary farm's is, so the two can drift and only one of them is "
         "the rule")
if "Place turning = one;" not in altar_source:
    fail("the altar's shape is typed out rather than turned, and then the "
         "symmetry check is checking what somebody typed rather than what the "
         "shape is")

for name in ALTAR_BLOCKS:
    for folder in ("models/block", "models/item", "blockstates"):
        if not os.path.isfile(os.path.join(RES, "assets", NS, folder,
                                           name + ".json")):
            fail(f"{name} has no {folder.split('/')[-1]}, so a block of the "
                 f"altar is a purple cube")
    if not os.path.isfile(os.path.join(RES, "data", NS, "recipe",
                                       name + ".json")):
        fail(f"{name} has no recipe, so the altar is a structure only the "
             f"creative menu can build")
    if not os.path.isfile(os.path.join(RES, "data", NS, "loot_table/blocks",
                                       name + ".json")):
        fail(f"{name} drops nothing when it is broken")
    if f"block.{NS}.{name}" not in lang_file:
        fail(f"{name} has no name a player reads")

if INSANIUM:
    if not os.path.isfile(os.path.join(RES, "assets", NS, "models/item",
                                       INSANIUM + ".json")):
        fail(f"{INSANIUM} has no model, so the sixth rung is a purple cube")
    if f"item.{NS}.{INSANIUM}" not in lang_file:
        fail(f"{INSANIUM} has no name a player reads")
    if os.path.isfile(os.path.join(RES, "data", NS, "recipe",
                                   INSANIUM + ".json")):
        fail(f"{INSANIUM} has a crafting recipe, and the whole reason the sixth "
             f"rung is made at an altar is that a seed may only grow what the "
             f"ground gives you")

for material in seeds:
    awakened = f"awakened_{material}_seed"
    if not os.path.isfile(os.path.join(RES, "assets", NS, "models/item",
                                       awakened + ".json")):
        fail(f"{awakened} has no model")
    if f"item.{NS}.{awakened}" not in lang_file:
        fail(f"{awakened} has no name a player reads")

# The table, against the rules that produced it.
table_path = os.path.join(RES, "data", NS, "awakening/table.json")
if not os.path.isfile(table_path):
    fail("there is no table of what an altar can do, so the thirty-one things "
         "it makes are in Java and nowhere a player or a wiki can read them")
else:
    table = json.loads(read(table_path))
    listed = table.get("awakenings", [])
    if len(listed) != len(seeds) + 1:
        fail(f"the altar's table has {len(listed)} row(s) for {len(seeds)} "
             f"seed(s) and the sixth rung; a seed the altar has never heard of "
             f"is a seed nobody can awaken and nothing reports it")
    made = [row.get("makes") for row in listed]
    if INSANIUM not in made:
        fail("nothing in the altar's table makes the sixth rung, so it is a "
             "name with no way up to it")
    if len(set(made)) != len(made):
        fail("two rows of the altar's table make the same thing, so one of "
             "them is a recipe the altar will never reach")
    for row in listed:
        if row.get("each", 0) <= 0:
            fail(f"{row.get('id')} swallows no essence, so the altar is free")
        if row.get("makes") == row.get("takes"):
            fail(f"{row.get('id')} hands back what it took")
        if row.get("essence") not in (INSANIUM, TOP_ESSENCE):
            fail(f"{row.get('id')} is fed {row.get('essence')}, which is neither "
                 f"the top of the ladder nor the rung above it")
    if table.get("pedestals", 0) <= 0 or table.get("relays", 0) <= 0:
        fail("the altar's table says it has no pedestals or no relays")

# The blocks themselves: they read the rules rather than holding an opinion.
altar_block = strip_comments(read(SRC, "block/AwakeningAltarBlock.java"))
if "AltarRules.AWAKENINGS" not in altar_block:
    fail("the altar decides for itself what a seed awakens into, which is a "
         "second opinion about the ladder three ages are built on")
if "for (AltarRules.Place place : AltarRules.AROUND) {" \
        not in body_of(altar_block, "public boolean built("):
    fail("the altar's shape in the world is not walked from the rules, so the "
         "shape the harness proved symmetric and the shape a player builds are "
         "two different shapes")
if "if (!built(level, centre)) {" not in body_of(altar_block,
                                                "public Component awaken("):
    fail("an altar fires without being built, so its pedestals are decoration")
if "stand.spend(one.each());" not in altar_block:
    fail("an altar fires without spending the essence on its pedestals, which "
         "is a machine for making essence out of nothing")

stand_block = strip_comments(read(SRC, "block/AltarBlock.java"))
if "public boolean offer(ItemStack coming) {" not in stand_block:
    fail("nothing can be put on a pedestal, so an altar cannot be fed")

seed_item = strip_comments(read(SRC, "item/EssenceSeed.java"))
if "AltarRules.AWAKENED_YIELD" not in seed_item:
    fail("an awakened seed decides for itself how much it yields, so the "
         "arithmetic the harness proved and the arithmetic a plot uses are two "
         "different arithmetics")

print(f"the altar          : {len(ALTAR_BLOCKS)} block(s), "
      f"{len(seeds) + 1} awakening(s), a sixth rung made rather than grown, and "
      f"an awakened farm of half the plots earning what a whole one does")


# ---------------------------------------------------------------------------
# The tackle: what goes on a rod, and the blocks around the water
# ---------------------------------------------------------------------------

if "for (FishRules.Chance chance : FishRules.table(reached, luck, bait)) {" \
        not in tackle_source:
    fail("a fitted rod no longer reads the same table a bare one does, so the "
         "three things FishRules proves about every cast are proved about a "
         "cast nobody makes")
if "made.add(where);" not in body_of(tackle_source,
                                     "public static List<FishRules.Water> reaches("):
    fail("a bobber does not reach the water the player is standing in, which "
         "is the one water they meant to fish")
if "public static List<String> hurtBetween(" not in tackle_source:
    fail("the rule that a better hook never makes the rare fish harder to get "
         "cannot be handed two tables, so it can only ever be asked about a "
         "healthy roster and has never been shown able to say yes")

for name, luck, catches, holds in hooks:
    if not os.path.isfile(os.path.join(RES, "assets", NS, "models/item",
                                       name + ".json")):
        fail(f"{name} has no model")
    if not os.path.isfile(os.path.join(RES, "data", NS, "recipe",
                                       name + ".json")):
        fail(f"{name} has no recipe, so a hook is an item only the creative "
             f"menu has")
    if f"item.{NS}.{name}" not in lang_file:
        fail(f"{name} has no name a player reads")
    if luck == 0 and catches == 1 and holds == "false":
        fail(f"{name} is the bare end of a line with a name on it")

for name, deeper, anytime, sooner in bobbers:
    if not os.path.isfile(os.path.join(RES, "assets", NS, "models/item",
                                       name + ".json")):
        fail(f"{name} has no model")
    if not os.path.isfile(os.path.join(RES, "data", NS, "recipe",
                                       name + ".json")):
        fail(f"{name} has no recipe")
    if f"item.{NS}.{name}" not in lang_file:
        fail(f"{name} has no name a player reads")
    if deeper == 0 and anytime == "false" and sooner <= 0.0:
        fail(f"{name} does nothing at all")

missing_mounts = []
for fid in fish_ids:
    mount = fid + "_mount"
    for folder in ("models/block", "models/item", "blockstates"):
        if not os.path.isfile(os.path.join(RES, "assets", NS, folder,
                                           mount + ".json")):
            missing_mounts.append(mount)
            break
    if not os.path.isfile(os.path.join(RES, "data", NS, "recipe",
                                       mount + ".json")):
        missing_mounts.append(mount)
    if not os.path.isfile(os.path.join(RES, "data", NS, "loot_table/blocks",
                                       mount + ".json")):
        missing_mounts.append(mount)
    if f"block.{NS}.{mount}" not in lang_file:
        missing_mounts.append(mount)
if missing_mounts:
    fail(f"{len(set(missing_mounts))} fish have no mount to go on a wall, and "
         f"the one player who finds that hole is holding the fish: "
         f"{sorted(set(missing_mounts))[:5]}")

for name in (worm_farm, tackle_box):
    if not name:
        continue
    for folder in ("models/block", "blockstates"):
        if not os.path.isfile(os.path.join(RES, "assets", NS, folder,
                                           name + ".json")):
            fail(f"{name} has no {folder.split('/')[-1]}")
    if not os.path.isfile(os.path.join(RES, "data", NS, "recipe",
                                       name + ".json")):
        fail(f"{name} has no recipe")
    if f"block.{NS}.{name}" not in lang_file:
        fail(f"{name} has no name a player reads")

# A mount is a trophy: the fish goes in and does not come out.
for fid in fish_ids[:1] if fish_ids else []:
    path = os.path.join(RES, "data", NS, "recipe", fid + "_mount.json")
    if os.path.isfile(path):
        recipe = json.loads(read(path))
        if f"{NS}:{fid}" not in json.dumps(recipe.get("key", {})):
            fail(f"{fid}'s mount is not made out of the fish, so it is a plaque "
                 f"anybody can craft without ever catching one")

print(f"the tackle         : {len(hooks)} hook(s), {len(bobbers)} bobber(s), "
      f"{len(fish_ids)} mount(s) and two stations, none of them taking a fish "
      f"away and no hook making the rare one harder to get")


# ---------------------------------------------------------------------------
# The crops behind the kitchen
# ---------------------------------------------------------------------------

if "float ours = found == null ? 1.0F" not in \
        strip_comments(read(SRC, "block/KitchenCropBlock.java")):
    fail("a crop grows at the game's own speed rather than at the one the "
         "rules give it, so the frontier of time against yield that the "
         "harness proves is a table in a comment")
if "public static List<String> fromNowhere(" not in crop_source:
    fail("nothing asks, of every ingredient, where it comes from — which is "
         "the check that found six of them coming from nowhere at all")

for crop in crops:
    name = crop["id"]
    if not os.path.isfile(os.path.join(RES, "assets", NS, "blockstates",
                                       name + "_crop.json")):
        fail(f"{name} has no blockstate, so the crop is a purple cube")
    else:
        states = json.loads(read(RES, "assets", NS, "blockstates",
                                 name + "_crop.json")).get("variants", {})
        if len(states) != 8:
            fail(f"{name}'s blockstate has {len(states)} age(s) and the game's "
                 f"crop property has eight; the ages with no model are the "
                 f"ones a creative player bone-meals it to")
    for stage in range(8):
        if not os.path.isfile(os.path.join(
                RES, "assets", NS, "models/block",
                f"{name}_crop_stage{stage}.json")):
            fail(f"{name} has no model for age {stage}")
            break
    if not os.path.isfile(os.path.join(RES, "assets", NS, "models/item",
                                       name + "_seeds.json")):
        fail(f"{name} has no seed model")
    if f"item.{NS}.{name}_seeds" not in lang_file:
        fail(f"{name}'s seed has no name a player reads")
    if f"block.{NS}.{name}_crop" not in lang_file:
        fail(f"{name}'s crop has no name a player reads")

    loot = os.path.join(RES, "data", NS, "loot_table/blocks", name + "_crop.json")
    if not os.path.isfile(loot):
        fail(f"{name} drops nothing when it is broken, so a field of it is a "
             f"field that cannot be harvested")
    else:
        body = read(loot)
        if f"{NS}:{name}_seeds" not in body:
            fail(f"{name} does not give its seed back when it is broken, so a "
                 f"crop broken early is a crop nobody plants twice")
        if f"{NS}:{name}" not in body:
            fail(f"{name} does not drop itself when it is ripe")

    patch = "wild_" + name
    for folder in ("worldgen/configured_feature", "worldgen/placed_feature"):
        if not os.path.isfile(os.path.join(RES, "data", NS, folder,
                                           patch + ".json")):
            fail(f"{name} has no {folder.split('/')[-1]} for its wild patch, so "
                 f"the first seed of it comes from a village that may not exist")
    modifier = os.path.join(RES, "data", NS, "neoforge/biome_modifier",
                            patch + ".json")
    if not os.path.isfile(modifier):
        fail(f"{patch} is a feature nothing puts in a biome, so it is generated "
             f"nowhere at all")
    else:
        body = json.loads(read(modifier))
        if body.get("type") != "neoforge:add_features":
            fail(f"{patch}'s biome modifier is not an add-features modifier")
        if body.get("features") != f"{NS}:{patch}":
            fail(f"{patch}'s biome modifier adds {body.get('features')}")
        if not str(body.get("biomes", "")).startswith("#"):
            fail(f"{patch} is added to {body.get('biomes')} rather than to a "
                 f"tag, so a pack that adds a biome of that sort gets no patch")

# The wild patches are not all in one place, or where a player spawns decides
# most of what they can ever cook.
places = {crop["biomes"] for crop in crops}
if len(places) < 3:
    fail(f"every wild patch turns up in one of only {len(places)} sort(s) of "
         f"place, so where a player spawns decides most of what they can ever "
         f"cook")

for name in larder:
    path = os.path.join(RES, "data", NS, "larder", name + ".json")
    if not os.path.isfile(path):
        fail(f"{name} is in the kitchen's cupboard and there is no recipe that "
             f"puts it there")
    else:
        body = json.loads(read(path))
        if not body.get("of"):
            fail(f"{name} is made out of nothing at all")
        if body.get("result") != f"{NS}:{name}":
            fail(f"{name}'s recipe makes {body.get('result')}")

if colony:
    for folder in ("models/block", "models/item", "blockstates"):
        if not os.path.isfile(os.path.join(RES, "assets", NS, folder,
                                           colony + ".json")):
            fail(f"the mushroom colony has no {folder.split('/')[-1]}")
    if not os.path.isfile(os.path.join(RES, "data", NS, "recipe",
                                       colony + ".json")):
        fail("the mushroom colony has no recipe")
    if f"block.{NS}.{colony}" not in lang_file:
        fail("the mushroom colony has no name a player reads")

print(f"the crops          : {len(crops)} crop(s) on the frontier of time "
      f"against yield, a wild patch apiece in {len(places)} sort(s) of place, "
      f"{len(larder)} larder recipe(s), and every ingredient the kitchen asks "
      f"for coming from somewhere")

print(f"the diet             : {constant('FOODS_FOR_FULL', kind='int')} different "
      f"foods for {constant('MOST_EXTRA_HEARTS', kind='int')} hearts, kept as a set, "
      f"surviving death, and applied as one modifier that replaces itself")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all agronomy checks passed")
