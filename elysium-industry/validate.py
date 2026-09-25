#!/usr/bin/env python3
"""
Validate Elysium Industry against its sources.

Run from anywhere:   python3 elysium-industry/validate.py

Age II is the second rung of the spine. Age III and IV are built on it, and it
is built on Age I, so the checks are about three things: that the model is what
the spec says, that the ladder inside the age is a ladder, and that the joint
with Age I runs one way and cannot be looped for free energy.

  1. INDEPENDENCE. Vanilla, elysium-lib, elysium-kinetics. Nothing above.
  2. PURITY, and the harness run rather than read.
  3. THE MODEL. A grid shares, a wire burns, distance costs.
  4. THE LADDER, and the joint with the age below.
  5. THE SEAM. What it declares, and that the spec agrees.
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
SRC = os.path.join(WORK, "src/main/java/com/elysium/industry")
PURE = os.path.join(SRC, "rules")
BELOW = os.path.join(ROOT, "elysium-kinetics/src/main/java/com/elysium/kinetics/rules")
HARNESS = os.path.join(WORK, "tools/IndustryHarness.java")
HARNESS_MAIN = "com.elysium.industry.rules.IndustryHarness"

#: How long the arithmetic is allowed to take. The honest sweeps in
#: the harness run in a few seconds; anything past this is a loop.
HARNESS_SECONDS = 60
SPEC = os.path.join(ROOT, "docs/SPEC_TECH.md")
NS = "elysiumindustry"

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


def squashed(text):
    """One method's source with every run of whitespace made one space.

    A rule that names a whole statement has to survive the statement being
    re-wrapped, or it is a rule that fails the first time somebody tidies the
    file and then gets deleted rather than fixed.
    """
    return re.sub(r"\s+", " ", text)


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
    print("This is not the elysium-industry tree.")
    sys.exit(1)

print("=" * 70)
print("elysium-industry — the second age, and the one that makes electricity worth it")
print("=" * 70)

# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------

FOREIGN = re.compile(
    r"(?:blusunrize\.|mekanism\.|com\.simibubi|owmii\.powah|gregtech\.|appeng\.)"
    r"|\b(?:immersiveengineering|tfmg|powergrid|oritech|mekanism|create)\s*:"
    r"|\"(?:immersiveengineering|tfmg|powergrid|oritech|mekanism)\"", re.I)
UPWARD = re.compile(r"com\.elysium\.(atomics|foundry|logistics|transit)")

java_files = []
for dirpath, _, filenames in os.walk(SRC):
    for name in filenames:
        if name.endswith(".java"):
            java_files.append(os.path.join(dirpath, name))

for path in java_files:
    src = strip_comments(read(path))
    where = os.path.relpath(path, SRC)
    for hit in FOREIGN.findall(src):
        fail(f"{where} names {hit}; this module is a reimplementation and must not "
             f"reach for what it replaces")
    if UPWARD.search(src):
        fail(f"{where} reaches up into a later age; the ages depend downwards only")

toml = read(RES, "META-INF/neoforge.mods.toml")
declared = set(re.findall(r'modId="([^"]+)"', toml))
allowed = {"${mod_id}", "neoforge", "minecraft", "elysiumlib", "elysiumkinetics"}
for mod in declared - allowed:
    fail(f"the module declares a dependency on {mod}; Age II needs the game, the "
         f"library and the age below it")
for needed in ("neoforge", "minecraft", "elysiumlib", "elysiumkinetics"):
    if needed not in declared:
        fail(f"the module does not declare {needed}")

print(f"independence       : {len(java_files)} source file(s), no mod it replaces, "
      f"no age above it, and the age below declared")

# ---------------------------------------------------------------------------
# 2. Purity, and the harness executed
# ---------------------------------------------------------------------------
#
# The one import a rules class may make outside the JDK is another module's
# rules class, because those are pure too and the harness compiles both. That
# is how Age II can speak about rotation without Age I knowing it exists.

for name in sorted(os.listdir(PURE)) if os.path.isdir(PURE) else []:
    if not name.endswith(".java"):
        continue
    src = read(PURE, name)
    without_package = re.sub(r"^\s*package [^;]+;", "", src, count=1, flags=re.M)
    for forbidden in ("net.minecraft", "net.neoforged", "com.mojang"):
        if forbidden in without_package:
            fail(f"rules/{name} reaches for {forbidden}; the rules are the JDK and "
                 f"another age's rules, so they can be executed by a harness")
    for used in re.findall(r"import\s+(com\.elysium\.[\w.]+);", without_package):
        if not used.startswith("com.elysium.kinetics.rules."):
            fail(f"rules/{name} imports {used}; a rules class may reach only into the "
                 f"rules of the age below, which is pure like itself")

if not os.path.isfile(HARNESS):
    fail("there is no harness, so every number below is checked by reading")
else:
    with tempfile.TemporaryDirectory() as tmp:
        staged = os.path.join(tmp, "src")
        os.makedirs(staged)
        sources = [os.path.join(PURE, n) for n in os.listdir(PURE) if n.endswith(".java")]
        sources += [os.path.join(BELOW, n) for n in os.listdir(BELOW) if n.endswith(".java")]
        for source in sources + [HARNESS]:
            open(os.path.join(staged, os.path.basename(source)), "w",
                 encoding="utf-8").write(read(source))
        compiled = subprocess.run(
            ["javac", "-nowarn", "-d", os.path.join(tmp, "out")]
            + [os.path.join(staged, n) for n in os.listdir(staged)],
            capture_output=True, text=True)
        if compiled.returncode != 0:
            fail("the rules do not compile against the JDK and the age below alone:\n"
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
                fail("the industry harness failed:\n" + harness_trouble(ran.stdout))
            else:
                print("the arithmetic     : " + ran.stdout.strip().splitlines()[-1])

rules = read(PURE, "IndustryRules.java")
plain = strip_comments(rules)


def constant(name, kind=r"float"):
    m = re.search(r"public static final " + kind + r"\s+" + name + r"\s*=\s*([^;]+);", plain)
    return m.group(1).strip() if m else None


# ---------------------------------------------------------------------------
# 3. The model
# ---------------------------------------------------------------------------

share = body_of(plain, "public float share()")
if "Math.min(1.0F, supply / demand)" not in share:
    fail("a short grid does not share what it has out proportionally, which is the one "
         "thing that makes Age II feel different from Age I rather than bigger")
if "burned" not in share:
    fail("a burned wire still feeds the machines behind it")

lending = body_of(plain, "public static Flow settle(")
if not lending:
    fail("the grid cannot be told that something on it lends or borrows power, so the "
         "age above has to run a second grid and the two will disagree")
else:
    if "else if (supply > demand)" not in lending:
        fail("lending and borrowing are not exclusive, so something on the grid can "
             "take at its input limit and give at its output limit in the same tick, "
             "which is a loop")
    if "Math.min(demand - supply, reserve.couldGive())" not in lending:
        fail("a grid borrows something other than what it is short by and what the "
             "reserve has, which is how a battery fills itself")
    if "Math.min(supply - demand, reserve.couldTake())" not in lending:
        fail("a grid stores something other than what it has spare and what the reserve "
             "has room for")
    if "burns(wire, carried)" not in lending:
        fail("a grid with a reserve on it never asks whether its wire survived what "
             "flowed, so a battery makes a wire safe")
    # Not "supply + lent": that phrase also appears in the line that works out
    # what the wire is carrying, so looking for it alone passes on a grid where
    # the loan is counted against the wire and never handed to a machine. The
    # whole expression, or nothing.
    if "new Reading(supply + lent, demand," not in lending:
        fail("what a reserve lends does not reach the machines, so storage is a hole "
             "rather than a battery")

for owned in ("Cell", "capacity()", "stored"):
    if re.search(r"\brecord\s+" + re.escape(owned) + r"\b", plain):
        fail(f"Age II declares a {owned}; storage is Age III's, and all this age knows "
             f"is that something on its grid can lend and borrow")

settle = body_of(plain, "public static Reading settle(")
if "Math.min(supply, demand)" not in settle:
    fail("a wire burns on what is asked rather than on what flows, so asking a small "
         "grid for a great deal destroys the wire that was coping")
if "burns(wire, carried)" not in settle:
    fail("a grid never asks whether its wire survived what went through it")

delivered = body_of(plain, "public static float delivered(")
if "lossPerBlock()" not in delivered:
    fail("distance costs nothing, so a generator may as well be a mile from the work")
if "Math.max(0.0F," not in delivered:
    fail("a long enough wire delivers negative power")

burns = body_of(plain, "public static boolean burns(")
if "carried > voltage.capacity()" not in burns:
    fail("a wire burns at exactly its rating, so the rating a player reads is not the "
         "rating the game uses")

destroys = body_of(plain, "public static boolean destroys(")
if "supplied.ordinal() > rated.ordinal()" not in destroys:
    fail("over-voltage does not destroy a machine, or under-voltage does, and either "
         "way a transformer has no purpose")

print("the model          : a grid that shares, a wire that burns on what flows, and "
      "distance that costs")

# ---------------------------------------------------------------------------
# 4. The ladder, and the joint below
# ---------------------------------------------------------------------------


def machine(name):
    m = re.search(r"public static final Machine\s+" + name
                  + r"\s*=\s*new Machine\(([\d.]+)F,\s*Voltage\.(\w+)\)", plain)
    return (float(m.group(1)), m.group(2)) if m else None


def generator(name):
    """A generator's output and tier, whether it is written flat or derived.

    The diesel engine's output is deliberately not a number: it is the
    turbine's times DIESEL_HASTE, so the one thing that makes it an honest rung
    — that it burns fuel at exactly the rate it makes power — cannot be decided
    twice. So this resolves the one shape of derivation the rules use, rather
    than insisting every generator be a literal and quietly passing the day
    somebody writes 480 back in by hand.
    """
    m = re.search(r"public static final Generator\s+" + name
                  + r"\s*=\s*\n?\s*new Generator\(([^,]+),\s*Voltage\.(\w+)\)", plain)
    if not m:
        return None
    return (value(m.group(1).strip()), m.group(2))


def value(expr):
    """A float literal, or a product of things already declared above it."""
    literal = re.fullmatch(r"([\d.]+)F", expr)
    if literal:
        return float(literal.group(1))
    product = re.fullmatch(r"(\w+)\.output\(\)\s*\*\s*(\w+)", expr)
    if product:
        base = generator(product.group(1))
        times = constant(product.group(2), kind="int")
        if base is not None and times is not None:
            return base[0] * float(times)
    return None


turbine = generator("STEAM_TURBINE")
diesel = generator("DIESEL_ENGINE")
crusher = machine("CRUSHER")
mill = machine("ROLLING_MILL")
arc = machine("ARC_FURNACE")
separator = machine("SEPARATOR")

if None in (turbine, diesel, crusher, mill, arc, separator) \
        or turbine[0] is None or diesel[0] is None:
    fail("one of Age II's generators or machines is no longer declared where the "
         "validator and the generators can read it")
else:
    if "STEAM_TURBINE.output() * DIESEL_HASTE" not in plain:
        fail("a diesel engine's output is a number of its own rather than a multiple of "
             "the turbine's, so the rate it eats fuel and the power it makes are two "
             "decisions and one of them will drift")
    if diesel[0] <= turbine[0]:
        fail("a diesel engine makes no more than a steam turbine")
    if arc[0] <= crusher[0] * 4:
        fail("the arc furnace is not the expensive machine, so nothing in Age II makes "
             "a player build a real grid")
    if turbine[1] != "LOW":
        fail("the first generator is not on the first wire, so a player's first build "
             "already needs a transformer")
    if diesel[1] == "LOW":
        fail("a diesel engine runs on a low wire, so its output has nowhere it must go "
             "and the voltage tiers never come up")

if constant("SEPARATOR_MULTIPLIER", kind="int") != "2":
    fail("Age II does not double ore; the spine says Age I gives raw metal and Age II "
         "doubles it, and that number lives here")

if constant("ON_A_SHAFT") is None:
    fail("an Age II machine cannot be driven by a shaft at all, which the spine says it "
         "must be, at a penalty")
else:
    penalty = float(constant("ON_A_SHAFT").rstrip("F"))
    if penalty <= 0.0 or penalty >= 1.0:
        fail(f"driving an Age II machine from a shaft is worth {penalty} of full speed; "
             f"it has to be a penalty and not a refusal, and not free either")

least = constant("SHAFT_LEAST_RPM")
timber = re.search(r"TIMBER\((\d+(?:\.\d+)?)F\)", read(BELOW, "KineticRules.java"))
if least and timber and float(least.rstrip("F")) <= float(timber.group(1)):
    fail("a timber shaft can drive an Age II machine, so the age below has no reason to "
         "upgrade its shafts before reaching up")

if constant("MOTOR_EFFICIENCY") is None or float(constant("MOTOR_EFFICIENCY").rstrip("F")) >= 1.0:
    fail("a motor loses nothing, so rotation into power and back is free and the two "
         "ages can be looped for energy out of nothing")

if "KineticRules.dynamoOutput" not in plain:
    fail("the alternator is not measured against Age I's dynamo, so the size of the "
         "step between the ages is decided in two places")

print(f"the ladder         : turbine -> diesel, crusher -> arc furnace; a wire that "
      f"forces the step up, ore doubled once, and a shaft that will do at "
      f"{constant('ON_A_SHAFT')} of the speed")

# ---------------------------------------------------------------------------
# 5. The age in the world
# ---------------------------------------------------------------------------
#
# Everything above is arithmetic. This is the half that can be wrong without
# the arithmetic noticing: a block with no blockstate, a machine that drags a
# shaft it is not using, an engine that burns fuel into an empty grid, a recipe
# that cannot be crafted because it wants an item nothing makes.

# Every asset and data file in this module is written by tools/gen_industry.py.
# A file edited by hand is a file the next run of the generator silently undoes,
# and the edit is usually the one carrying the fix. So the whole set is
# regenerated into a temporary tree and compared, byte for byte.

GENERATOR = os.path.join(WORK, "tools/gen_industry.py")
if not os.path.isfile(GENERATOR):
    fail("there is no generator, so every asset in the age is kept by hand")
else:
    with tempfile.TemporaryDirectory() as tmp:
        environment = dict(os.environ, ELYSIUM_INDUSTRY_OUT=tmp)
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
                    elif not samefile.same(made_path, kept):
                        drifted.append(os.path.relpath(made_path, fresh))
            if drifted:
                fail(f"{len(drifted)} generated file(s) differ from what the generator "
                     f"writes, so they were edited by hand and the next run will undo "
                     f"them: " + ", ".join(sorted(drifted)[:6])
                     + (" ..." if len(drifted) > 6 else ""))

blocks_src = read(SRC, "registry/IndustryBlocks.java")
items_src = read(SRC, "registry/IndustryItems.java")
registered = re.findall(r'BLOCKS\.register\("([a-z_]+)"', strip_comments(blocks_src, True))
parts = re.findall(r'part\("([a-z_]+)"\)', strip_comments(items_src, True))
GRID_BLOCKS = len(registered)

# The bricks and the two machines built out of them are registered from a loop
# over FurnaceRules rather than named one at a time, so a reading that walks
# literal register("...") calls cannot see them. That blind spot once let five
# hundred blocks go unchecked in another module; it is closed here by parsing
# the rules.
FURNACE_RULES = strip_comments(read(SRC, "rules/FurnaceRules.java"),
                               keep_strings=True)
_fflat = re.sub(r"\s+", " ", FURNACE_RULES)
bricks = re.findall(r'String (?:COKE_BRICK|BLAST_BRICK) = "([a-z_]+)"',
                    FURNACE_RULES)
shape_ids = re.findall(r'new ShapeRules\.Shape\( "([a-z_]+)"', _fflat)
coke = re.search(r'String COKE = "([a-z_]+)"', FURNACE_RULES)
if len(bricks) != 2 or len(shape_ids) != 2 or not coke:
    fail(f"FurnaceRules no longer says what the brick machines are; this file "
         f"read {len(bricks)} brick(s) and {len(shape_ids)} shape(s), and every "
         f"check below it is then checking nothing")
registered = registered + bricks + shape_ids
if coke:
    parts = parts + [coke.group(1)]

if GRID_BLOCKS != 12:
    fail(f"Age II has {GRID_BLOCKS} block(s) on the grid; the spec says twelve — "
         f"three wires and a transformer, two engines, two spindles and four "
         f"machines")
if not parts:
    fail("Age II makes nothing of its own, so the arc furnace has no purpose and the "
         "top tier of wire has nothing to be made of")

block_items = set(re.findall(r'registerSimpleBlockItem\(IndustryBlocks\.([A-Z_]+)\)',
                             strip_comments(items_src, True)))
order = re.search(r"ORDER = List\.of\(([^;]+)\);", strip_comments(items_src, True))
in_tab = set(re.findall(r"[A-Z_]{3,}", order.group(1))) if order else set()

lang = {}
lang_path = os.path.join(RES, "assets", NS, "lang/en_us.json")
if os.path.isfile(lang_path):
    lang = json.load(open(lang_path, encoding="utf-8"))
else:
    fail("there is no language file, so every block in the age is called by its id")

mineable = []
pick_path = os.path.join(RES, "data/minecraft/tags/block/mineable/pickaxe.json")
if os.path.isfile(pick_path):
    mineable = json.load(open(pick_path, encoding="utf-8")).get("values", [])

BRICKWORK = set(bricks) | set(shape_ids)

# What the creative tab actually shows, read out of the one lambda that builds
# it rather than out of the whole file. A roster named anywhere in the registry
# would otherwise satisfy a rule about being *in the tab*, which is the same
# mistake as naming a call where a statement was meant.
_display = re.search(r"displayItems\(\(parameters, output\) -> \{(.*?)\}\)",
                     squashed(strip_comments(items_src, True)))
_shown = _display.group(1) if _display else ""
if not _shown:
    fail("the creative tab shows whatever the registry happened to list, so "
         "nothing below can say a block is missing from it")

for name in registered:
    constant_name = name.upper()
    # The brickwork is registered from a loop, so it has no literal item line
    # and no literal name in the tab's order; the tab reaches for the whole
    # roster instead and the count check in the registry catches a missing one.
    if constant_name not in block_items and name not in BRICKWORK:
        fail(f"{name} has no item, so it exists only to a command")
    if constant_name not in in_tab and name not in BRICKWORK:
        fail(f"{name} is in no creative tab, so nobody in a creative world can find it")
    if name in BRICKWORK and "BRICKWORK" not in _shown:
        fail(f"{name} is registered from a roster and the tab does not reach for "
             f"that roster, so it is a block nobody in a creative world can find")
    for kind, where in (("blockstates", f"blockstates/{name}.json"),
                        ("a model", f"models/block/{name}.json"),
                        ("an item model", f"models/item/{name}.json")):
        if not os.path.isfile(os.path.join(RES, "assets", NS, where)):
            fail(f"{name} has no {kind}, so it is a purple cube")
    if not os.path.isfile(os.path.join(RES, "data", NS, f"loot_table/blocks/{name}.json")):
        fail(f"{name} has no drop, so mining one destroys it")
    if f"block.{NS}.{name}" not in lang:
        fail(f"{name} has no name in the language file")
    if f"{NS}:{name}" not in mineable:
        fail(f"{name} is in no mineable tag, so it takes a fist and drops nothing quickly")

for name in parts:
    if not os.path.isfile(os.path.join(RES, "assets", NS, f"models/item/{name}.json")):
        fail(f"{name} has no item model")
    if not os.path.isfile(os.path.join(RES, "assets", NS, f"textures/item/{name}.png")):
        fail(f"{name} has no texture")
    if f"item.{NS}.{name}" not in lang:
        fail(f"{name} has no name in the language file")

# The wire's model and its collision box are written in two files that cannot
# see each other. A block you cannot hit where you can see it is the result.
wire_box = re.search(r"SHAPE = box\((\d+), (\d+), (\d+), (\d+), (\d+), (\d+)\)",
                     strip_comments(read(SRC, "block/WireBlock.java")))
wire_model = os.path.join(RES, "assets", NS, "models/block/copper_wire.json")
if wire_box and os.path.isfile(wire_model):
    element = json.load(open(wire_model, encoding="utf-8"))["elements"][0]
    want = [float(wire_box.group(n)) for n in (1, 2, 3)]
    to = [float(wire_box.group(n)) for n in (4, 5, 6)]
    if [float(v) for v in element["from"]] != want or [float(v) for v in element["to"]] != to:
        fail("the wire's model and its collision box are different sizes, so it can be "
             "hit where it cannot be seen or the other way round")

for engine in ("steam_turbine", "diesel_engine"):
    state_path = os.path.join(RES, "assets", NS, f"blockstates/{engine}.json")
    if os.path.isfile(state_path):
        variants = json.load(open(state_path, encoding="utf-8")).get("variants", {})
        if set(variants) != {"lit=false", "lit=true"}:
            fail(f"{engine}'s blockstate does not name both lit values, so either the "
                 f"game cannot render it or it looks the same running and cold")
        if variants.get("lit=false", {}).get("model") == \
                variants.get("lit=true", {}).get("model"):
            fail(f"{engine} looks the same lit and cold, so its one piece of feedback "
                 f"is a message you have to ask for")

# --- the blocks that could be wrong without anything catching fire ---------

plant = strip_comments(read(SRC, "block/PlantBlock.java"))
released = body_of(plant, "public KineticRules.Load load()")
if "share() >= plant.bestOnAShaft()" not in released:
    fail("an Age II machine does not let go of a shaft when the grid is already doing "
         "as well — either it drags a line it is not using, or it takes the shaft's "
         "speed without paying the shaft's torque")
if "return null" not in released:
    fail("an Age II machine on a healthy grid is still a load on the line")

speed = body_of(plant, "public float speed()")
if "plant.shaftShare(rpm)" not in speed:
    fail("a machine works out for itself what a shaft is worth to it rather than asking "
         "its own block, so an age above cannot make the fallback route worse than this "
         "one without rewriting the machine")
if "Math.max(onGrid, onShaft)" not in speed:
    fail("a machine on both a grid and a shaft does not take whichever is better, so "
         "wiring a backup to something makes it worse")

progress = body_of(plant, "protected void serverTick(")
if "IndustryRules.workPerTick(speed)" not in progress:
    fail("a machine's progress is not the rules' work-per-tick, so the gauge and the "
         "machine are counting different things")
if "plant.multiplier()" not in progress:
    fail("a machine ignores its own multiplier, so the separator does not double")

separator_src = strip_comments(read(SRC, "block/SeparatorBlock.java"))
if "IndustryRules.SEPARATOR_MULTIPLIER" not in body_of(separator_src, "public int multiplier()"):
    fail("the separator's doubling is a number of its own rather than the spine's, so "
         "the material ladder is decided twice")
# One machine class, and each age puts its own catalogue behind it. A machine
# that named a recipe Kind rather than a recipe type would be a machine only
# Age II could ever use.
if "RecipeType<? extends PlantRecipe> recipes()" not in plant:
    fail("the machine is written against Age II's own recipe class rather than against a "
         "recipe type, so an age above has to copy the whole machine to have its own "
         "catalogue")
if "lookUp(plant.recipes(), asked, level)" not in plant:
    fail("a machine does not read the recipes its own block names")

# Four machines, four catalogues. Two machines pointing at one recipe type is
# not a crash and not a missing file: it is a machine that quietly does another
# machine's job and a set of recipes nothing can reach, which is the kind of
# thing only a player notices, months later, and cannot describe.
MACHINE_KINDS = {
    "CrusherBlock": "CRUSHING",
    "RollingMillBlock": "ROLLING",
    "ArcFurnaceBlock": "SMELTING",
    "SeparatorBlock": "SEPARATING",
}
claimed = {}
for machine, wanted in MACHINE_KINDS.items():
    body = body_of(strip_comments(read(SRC, f"block/{machine}.java")),
                   "public RecipeType<? extends PlantRecipe> recipes()")
    named = re.search(r"IndustryRecipe\.Kind\.(\w+)", body)
    if not named:
        fail(f"{machine} names no recipe kind, so it reads nothing")
        continue
    if named.group(1) != wanted:
        fail(f"{machine} reads {named.group(1)} recipes; it is the machine {wanted} "
             f"recipes are written for, and nothing else reads them")
    if named.group(1) in claimed:
        fail(f"{machine} and {claimed[named.group(1)]} both read {named.group(1)} "
             f"recipes, so one of them does the other's job and a whole catalogue is "
             f"unreachable")
    claimed[named.group(1)] = machine
for kind in ("CRUSHING", "ROLLING", "SMELTING", "SEPARATING"):
    if kind not in claimed:
        fail(f"no machine reads {kind} recipes, so that whole catalogue is unreachable")

for other in ("CrusherBlock", "RollingMillBlock", "ArcFurnaceBlock"):
    if "multiplier()" in strip_comments(read(SRC, f"block/{other}.java")):
        fail(f"{other} multiplies its output; the doubling is the separator's alone and "
             f"is what the separator is for")

motor = strip_comments(read(SRC, "block/MotorBlock.java"))
draws = body_of(motor, "public IndustryRules.Machine machine()")
for reading in ("share()", "supply()", "demand()"):
    if reading in draws:
        fail(f"a motor's draw on the grid reads {reading}, so what the grid can afford "
             f"depends on what the motor asks for and what the motor asks for depends "
             f"on what the grid can afford — that oscillates and never settles")
if "IndustryRules.motorAsSource" not in body_of(motor, "public KineticRules.Source source()"):
    fail("a motor drives the line by some arithmetic of its own rather than the rules', "
         "so the free-energy proof covers a motor the game does not have")

fueled = strip_comments(read(SRC, "block/FueledBlock.java"))
lighting = body_of(fueled, "protected void serverTick(")
if "wanted()" not in lighting:
    fail("an engine lights fuel without asking whether anything wants the power, so the "
         "only way to stop it burning is to break it")
cold = body_of(fueled, "public IndustryRules.Generator generator()")
if "burning > 0" not in cold or "null" not in cold:
    fail("a cold engine is still a generator, so the grid measures its wire loss from a "
         "firebox that is not alight")

steel = strip_comments(read(SRC, "block/SteelNode.java"))
if "KineticRules.Shaft.STEEL" not in body_of(steel, "KineticRules.Shaft material()"):
    fail("Age II is not made of steel, so an Age II machine on a brass line caps that "
         "line at the rating of timber")
# Read as an implements clause rather than as a piece of text: the interface
# can be named plainly, fully qualified, or with the import removed, and all
# three put a block on an Age I line. Looking for the string "implements
# KineticNode" catches only the first, which is exactly the version nobody
# writes by accident.
IMPLEMENTS = re.compile(r"\bimplements\s+([^{;]+)\{", re.S)
for path in java_files:
    where = os.path.relpath(path, SRC)
    if where == os.path.join("block", "SteelNode.java"):
        continue
    for clause in IMPLEMENTS.findall(strip_comments(read(path))):
        named = [part.strip().split("<")[0].strip().split(".")[-1]
                 for part in clause.split(",")]
        if "KineticNode" in named and "SteelNode" not in named:
            fail(f"{where} sits on an Age I line without saying what it is made of, so "
                 f"it counts as timber and shears the line under it")

# --- the recipes, and the ladder inside them ------------------------------

OURS = {f"{NS}:{name}" for name in registered} | {f"{NS}:{name}" for name in parts}
OUR_TAGS = {"c:ingots/steel"} | {f"c:plates/{m}" for m in ("iron", "copper", "gold", "steel")}
KINETICS = os.path.join(ROOT, "elysium-kinetics/src/main/resources/data/elysiumkinetics/recipe")
below = set()
if os.path.isdir(KINETICS):
    for name in os.listdir(KINETICS):
        if name.endswith(".json"):
            below.add("elysiumkinetics:" + name[:-5])

recipe_root = os.path.join(RES, "data", NS, "recipe")
crafted = {}
machine_recipes = {kind: 0 for kind in ("crushing", "rolling", "smelting", "separating")}
for dirpath, _, filenames in os.walk(recipe_root):
    for name in sorted(filenames):
        if not name.endswith(".json"):
            continue
        path = os.path.join(dirpath, name)
        recipe = json.load(open(path, encoding="utf-8"))
        where = os.path.relpath(path, recipe_root)
        folder = os.path.dirname(where)
        if folder:
            kind = recipe.get("type", "")
            if kind != f"{NS}:{folder}":
                fail(f"{where} declares the type {kind!r} while sitting in {folder}/, so "
                     f"it will work in neither machine")
            machine_recipes[folder] = machine_recipes.get(folder, 0) + 1
            asked = recipe.get("input", {})
            item = asked.get("item") or ("#" + asked["tag"] if "tag" in asked else "")
            if item.startswith("#"):
                if item[1:] not in OUR_TAGS and not item.startswith("#minecraft:"):
                    fail(f"{where} asks for the tag {item}, which nothing in this install "
                         f"is guaranteed to fill")
            elif not (item.startswith("minecraft:") or item in OURS):
                fail(f"{where} asks for {item}, which the base game and this module do "
                     f"not have between them")
            if folder == "separating" and recipe.get("result", {}).get("count", 1) != 1:
                fail(f"{where} states its own doubling; the multiplier is the machine's "
                     f"and lives in IndustryRules so the ladder is one number")
            continue
        crafted[where[:-5]] = recipe

for kind, count in machine_recipes.items():
    if count == 0:
        fail(f"nothing can be made by {kind}, so one of Age II's four machines does "
             f"nothing at all")


def ingredients(recipe):
    """Every item and tag a crafting recipe names, however it is written."""
    named = []
    for entry in recipe.get("key", {}).values():
        named.append(entry)
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

for name, recipe in crafted.items():
    for asked in ingredients(recipe):
        if asked.startswith("#"):
            if asked[1:] not in OUR_TAGS and not asked.startswith("#minecraft:"):
                fail(f"recipe/{name} asks for the tag {asked}, which nothing in this "
                     f"install is guaranteed to fill")
        elif not (asked.startswith("minecraft:") or asked in OURS or asked in below):
            fail(f"recipe/{name} asks for {asked}; Age II is buildable out of the base "
                 f"game and the age below it, and nothing else")

BOOTSTRAP = ("steam_turbine", "alternator")
GATED = ("steel_wire", "diesel_engine", "separator")
for name in BOOTSTRAP:
    asked = ingredients(crafted.get(name, {}))
    if any(a.startswith("#c:plates/") or a == "#c:ingots/steel" for a in asked):
        fail(f"the {name} needs something a factory makes, so the first grid cannot be "
             f"built without a grid")
for name in GATED:
    asked = ingredients(crafted.get(name, {}))
    if not any(a == "#c:ingots/steel" or a.startswith("#c:plates/steel") for a in asked):
        fail(f"the {name} can be built without steel, so the arc furnace is optional and "
             f"the middle of the age has no gate in it")

for tag in sorted(OUR_TAGS):
    namespace, path = tag.split(":", 1)
    where = os.path.join(RES, "data", namespace, "tags/item", path + ".json")
    if not os.path.isfile(where):
        fail(f"{tag} is asked for by a recipe and defined nowhere, so that recipe fails "
             f"to load")
    elif not json.load(open(where, encoding="utf-8")).get("values"):
        fail(f"{tag} is empty, so every recipe that asks for it fails to load")

# ---------------------------------------------------------------------------
# The books, and the loss that used to be invisible to them
# ---------------------------------------------------------------------------

GRID_LINE = strip_comments(read(SRC, "block/GridLine.java"), keep_strings=True)
for needed, why in (
        ("public record Books(",
         "nothing writes out what a grid did, so there is no statement "
         "anywhere that it hands out what it made — and an energy system that "
         "creates power under some arrangement of cables is found by a player "
         "in a day and cannot be fixed afterwards"),
        ("public record Placed<T>(",
         "nothing on a grid knows how far along the wire it is, so the loss "
         "cannot be worked out where the rest of the arithmetic is"),
        ("public static Books books(",
         "nothing adds a grid's books up, so the sweep that proves they "
         "balance has nothing to sweep"),
        ("public static Flow along(",
         "nothing settles a grid whose generators are down a wire, so the "
         "distance loss lives outside the rules again")):
    if needed not in plain:
        fail(why)

# The one that matters: the block that walks the grid must not do the
# discounting itself. It did, and that is exactly why the constant the whole
# voltage ladder exists for was a constant no harness had ever swept.
if "IndustryRules.delivered(" in GRID_LINE:
    fail("the block that walks the grid charges the distance loss itself, so "
         "the number the whole voltage ladder exists for is applied where no "
         "harness can sweep it — which is the state this module shipped in "
         "until the books were written")
_solve = squashed(body_of(GRID_LINE, "public static IndustryRules.Reading solve("))
if "IndustryRules.along(generators, machines, found.weakest(), bank);" not in _solve:
    fail("the grid is settled without its distances, so what a player sees and "
         "what the books prove are two different models")

print("the books          : a grid hands out what it made and borrowed, less "
      "what the wire took, on ten thousand grids built at random - and the "
      "loss is charged where the arithmetic can see it rather than by the "
      "block that walks the wire")


# ---------------------------------------------------------------------------
# The multiblocks, and the sentence a player gets when one has not formed
# ---------------------------------------------------------------------------

SHAPE_RULES = strip_comments(read(SRC, "rules/ShapeRules.java"), keep_strings=True)
FURNACE_BLOCK = strip_comments(read(SRC, "block/FurnaceBlock.java"),
                               keep_strings=True)
if not SHAPE_RULES:
    fail("there is no shape arithmetic, so a multiblock is a block that reads "
         "the world and decides for itself, which is where every unformable "
         "multiblock in every mod comes from")
for needed, why in (
        ("public static String wrongAt(",
         "nothing can say where a build stopped being the shape, and a "
         "multiblock that only says no is twenty minutes a player spends "
         "counting bricks"),
        ("public static List<char[][][]> orientations(",
         "nothing turns a shape, so a machine built facing the wrong way is a "
         "machine that does not work and does not say why"),
        ("public static List<String> faultsInSet(",
         "nothing asks whether two shapes are the same arrangement, so a "
         "machine could be built by accident while building a different one"),
        ("public static Map<String, Integer> billOf(",
         "nothing says what a shape is built out of, so breaking one could "
         "give back most of it")):
    if needed not in SHAPE_RULES:
        fail(why)

# The block asks the arithmetic. A block that walked the world itself would be
# a block the harness has never run, and the harness is the only thing that
# sweeps every mutation of every orientation.
if "ShapeRules.orientations(shape)" not in squashed(FURNACE_BLOCK):
    fail("the controller does not try every orientation, so a machine built "
         "facing the wrong way is a machine that does not work")
_look = squashed(body_of(FURNACE_BLOCK, "public void look("))
if "if (ShapeRules.sameAs(want, found)) {" not in _look:
    fail("the controller decides for itself whether the bricks are the shape "
         "rather than asking the arithmetic that is swept against every "
         "one-block mutation of it")
if "wrong = best == null" not in _look:
    fail("the controller throws away what it found wrong, so the one thing "
         "this block exists to do — say which brick — is not done")
if "elysiumindustry.furnace.unformed" not in FURNACE_BLOCK:
    fail("there is no sentence for a multiblock that has not formed, so the "
         "answer is the worst one there is: nothing happens")

# Every brick a shape names is a block this module registers, or the shape is
# a set of instructions nobody can follow.
for one in re.findall(r"Map\.of\(('[A-Z]', [A-Z_]+(?:, )?)+\)", _fflat):
    pass
for named in re.findall(r"Map\.of\([^)]*\)", _fflat):
    for symbol, value in re.findall(r"'(.)', ([A-Z_]+|\"[a-z_]+\")", named):
        want = value.strip('"')
        if want.isupper():
            continue
        if want not in registered:
            fail(f"a shape names {want} and nothing registers a block by that "
                 f"name, so it is a machine a player is told to build out of "
                 f"something that does not exist")

# And the jobs: everything they make is something that exists.
firings = re.findall(
    r'new Firing\("([a-z_]+)", "([#a-z_:]+)", (\d+), "([a-z_:]+)", (\d+), ',
    _fflat)
if len(firings) < 2:
    fail(f"the brick machines have {len(firings)} job(s) between them, which "
         f"is not two machines")
for shape, given, count, made, makes in firings:
    if shape not in shape_ids:
        fail(f"a job is fired in a {shape}, which is not one of the shapes")
    for one in (given, made):
        if one.startswith("#") or one.startswith("minecraft:"):
            continue
        if one not in OURS:
            fail(f"a brick machine names {one}, which this module does not make")

print(f"the brick machines : {len(shape_ids)} multiblock(s) out of "
      f"{len(bricks)} brick(s), every orientation accepted and every "
      f"one-block mutation of every one of them refused by name, and "
      f"{len(firings)} job(s) that want no wire at all")


print(f"the age in the world: {len(registered)} block(s) and {len(parts)} part(s), each "
      f"modelled, dropped, named and craftable; "
      f"{sum(machine_recipes.values())} machine recipe(s) across four machines")

# ---------------------------------------------------------------------------
# 6. The seam
# ---------------------------------------------------------------------------

spec = read(SPEC)
if not spec:
    fail("docs/SPEC_TECH.md is gone; it is the contract the ages are built against")
else:
    for needed in ("elysium-industry", "Forge Energy", "at a **penalty**"):
        if needed not in spec:
            fail(f"the spec no longer states {needed!r}, so a later age has nothing to "
                 f"build against")

main_class = read(SRC, "ElysiumIndustry.java")
if f'MODID = "{NS}"' not in main_class:
    fail("the module id is not what everything else in the tree spells it")

print("what it declares   : the game, the library, the age below, and a spec the ages "
      "above can read")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all industry checks passed")
