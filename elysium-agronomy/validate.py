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
    """Every run of whitespace as one space.

    So that a rule can name a whole statement — "drawers.add(...); continue;" —
    without also having to guess how it was wrapped. Naming the whole statement
    is the point: this project has now been bitten ten times by a rule that
    looked for a token which survives the very edit it was meant to catch,
    because the token appears somewhere else in the same method.
    """
    return " ".join(text.split())


def squashed(text):
    """Every run of whitespace as one space, so a rule can name a whole statement.

    Naming the whole statement is the point: a rule that looks for a token
    which survives the very edit it was meant to catch is a rule that has never
    failed, and this project has now been bitten by that eleven times.
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
                fail("the logistics harness failed:\n" + ran.stdout.strip()[:1600])
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

GENERATOR = os.path.join(WORK, "tools/gen_agronomy.py")
if not os.path.isfile(GENERATOR):
    fail("there is no generator, so every asset in the module is kept by hand")
else:
    with tempfile.TemporaryDirectory() as tmp:
        environment = dict(os.environ, ELYSIUM_AGRONOMY_OUT=tmp)
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
                     f"writes, so they were edited by hand and the next run will undo "
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
if "AgronomyRules.combsPerHour(" not in body_of(hive, "public void work("):
    fail("a hive works out its own rate, so crowding is decided somewhere the harness "
         "cannot execute it")
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

seeds = dict(re.findall(r'SEEDS\.put\("([a-z_]+)", (\d+)\);',
                        strip_comments(items_src, True)))
generator = read(WORK, "tools/gen_agronomy.py")
written = dict(re.findall(r'"([a-z_]+)": \((\d+), "', generator))
if not seeds:
    fail("no seeds are declared, so the module grows nothing")
if seeds != written:
    fail("the tiers in the Java and the tiers in the generator disagree: "
         + ", ".join(f"{name} is {seeds.get(name)} and {written.get(name)}"
                     for name in sorted(set(seeds) | set(written))
                     if seeds.get(name) != written.get(name))
         + " — a seed that takes one length of time to grow and costs another to make")

essences = re.findall(r'"([a-z_]+_essence)"', strip_comments(items_src, True))
if len(essences) != int(constant("TIERS", kind="int") or 0):
    fail("there are not as many essences as there are tiers, so some rung of the ladder "
         "has nothing on it")

OURS = {f"{NS}:{name}" for name in registered}
OURS |= {f"{NS}:{name}" for name in re.findall(r'registerSimpleItem\("([a-z_]+)"\)',
                                               strip_comments(items_src, True))}
OURS |= {f"{NS}:{name}_seed" for name in seeds}
OURS |= {f"{NS}:{name}" for name in essences}
BELOW_TAGS = {f"c:plates/{m}" for m in ("iron", "copper", "gold", "steel")}
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

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all agronomy checks passed")
