#!/usr/bin/env python3
"""
Validate Elysium Atomics against its sources.

Run from anywhere:   python3 elysium-atomics/validate.py

Age III is the third rung of the spine. It is the first age that can keep
energy rather than spending it the tick it was made, and the first that can
destroy a base on its own, so the checks are about four things: that it is
built on the ages below rather than beside them, that stored energy is
conserved, that heat settles and is fatal without coolant, and that the new
way of turning electricity into rotation cannot pay for itself.

  1. INDEPENDENCE. Vanilla, elysium-lib, elysium-kinetics, elysium-industry.
  2. PURITY, and the harness run rather than read.
  3. THE MODEL. A cell that conserves, a core that settles.
  4. THE LADDER, and the joints with the ages below.
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
SRC = os.path.join(WORK, "src/main/java/com/elysium/atomics")
PURE = os.path.join(SRC, "rules")
BELOW = [os.path.join(ROOT, "elysium-kinetics/src/main/java/com/elysium/kinetics/rules"),
         os.path.join(ROOT, "elysium-industry/src/main/java/com/elysium/industry/rules")]
HARNESS = os.path.join(WORK, "tools/AtomicHarness.java")
HARNESS_MAIN = "com.elysium.atomics.rules.AtomicHarness"

#: How long the arithmetic is allowed to take. The honest sweeps in
#: the harness run in a few seconds; anything past this is a loop.
HARNESS_SECONDS = 60
SPEC = os.path.join(ROOT, "docs/SPEC_TECH.md")
NS = "elysiumatomics"

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
    re-wrapped, or it fails the first time somebody tidies the file and then
    gets deleted rather than fixed.
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
    print("This is not the elysium-atomics tree.")
    sys.exit(1)

print("=" * 70)
print("elysium-atomics — the third age, and the first one that can keep what it makes")
print("=" * 70)

# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------

FOREIGN = re.compile(
    r"(?:mekanism\.|owmii\.powah|com\.simibubi|blusunrize\.|gregtech\.|appeng\.)"
    r"|\b(?:mekanism|powah|mekanismgenerators|nuclearcraft|bigreactors|oritech)\s*:"
    r"|\"(?:mekanism|powah|mekanismgenerators|nuclearcraft|bigreactors)\"", re.I)
UPWARD = re.compile(r"com\.elysium\.(foundry|logistics|transit|agronomy)")

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
allowed = {"${mod_id}", "neoforge", "minecraft", "elysiumlib", "elysiumkinetics",
           "elysiumindustry"}
for mod in declared - allowed:
    fail(f"the module declares a dependency on {mod}; Age III needs the game, the "
         f"library and the two ages below it")
for needed in ("neoforge", "minecraft", "elysiumlib", "elysiumkinetics", "elysiumindustry"):
    if needed not in declared:
        fail(f"the module does not declare {needed}")

print(f"independence       : {len(java_files)} source file(s), no mod it replaces, "
      f"no age above it, and both ages below declared")

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
                 f"ages below, so they can be executed by a harness")
    for used in re.findall(r"import\s+(com\.elysium\.[\w.]+);", without_package):
        if not (used.startswith("com.elysium.kinetics.rules.")
                or used.startswith("com.elysium.industry.rules.")):
            fail(f"rules/{name} imports {used}; a rules class may reach only into the "
                 f"rules of the ages below, which are pure like itself")

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
            fail("the rules do not compile against the JDK and the ages below alone:\n"
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
                fail("the atomics harness failed:\n" + harness_trouble(ran.stdout))
            else:
                print("the arithmetic     : " + ran.stdout.strip().splitlines()[-1])

rules = read(PURE, "AtomicRules.java")
plain = strip_comments(rules)


def constant(name, kind=r"float"):
    m = re.search(r"public static final " + kind + r"\s+" + name + r"\s*=\s*([^;]+);",
                  plain, re.S)
    return " ".join(m.group(1).split()) if m else None


# ---------------------------------------------------------------------------
# 3. The model
# ---------------------------------------------------------------------------
#
# Two things here would be invisible until they had ruined a save: energy that
# appears in a battery, and a reactor that runs away while nobody is watching.

if "IndustryRules.Voltage" not in plain or "IndustryRules.settle(" not in plain:
    fail("Age III declares its own electricity rather than settling on Age II's grid, so "
         "there are two models of a grid in one pack and they will disagree")
if re.search(r"\benum\s+Voltage\b", plain):
    fail("Age III declares a second voltage table; the tiers are Age II's, and EXTREME "
         "was left there for this age to move into")
if "IndustryRules.Reserve" not in plain:
    fail("Age III's cells do not describe themselves to the grid below as a reserve, so "
         "the age below cannot see them and this age is running a second grid")
for owned in ("burns(", "lossPerBlock", "Math.min(shortfall"):
    if re.search(r"(?<!IndustryRules\.)\b" + re.escape(owned), plain):
        fail(f"Age III works out {owned!r} for itself; every one of those is Age II's "
             f"and reaching for the answer rather than the age below is how two models "
             f"of a grid start")

after = body_of(plain, "public static int after(Cell cell, int was, Buffered grid")
if not after:
    fail("a cell's contents are not whole units of energy, so a bank gains and loses a "
         "unit or two per tick to rounding and there is no tolerance that makes "
         "conservation true")
else:
    if "Math.ceil" not in after or "Math.floor" not in after:
        fail("a cell rounds the same way charging and discharging, so rounding can fall "
             "on the side of the player having more — a free-energy bug with a long fuse")
    if "grid.fromCells()" not in after or "grid.toCells()" not in after:
        fail("a cell's new contents are not worked out from what the grid said it moved")

settle = body_of(plain, "public static Buffered settle(")
if "IndustryRules.settle(" not in settle:
    fail("Age III settles its own grid rather than handing its cells to Age II's as a "
         "reserve, so which tick a wire burns on is decided twice")
if "reserveOf(cells, stored)" not in settle:
    fail("the bank a grid is told about is not the bank that is there")

nxt = body_of(plain, "public static float nextTemperature(")
if "(temperature - AMBIENT)" not in nxt:
    fail("a core sheds a fixed amount of heat rather than a fraction of its excess, so "
         "its temperature does not settle — it runs away on its own while the player is "
         "somewhere else")
if "Math.max(AMBIENT," not in nxt:
    fail("a core can cool below the temperature of the room it is in")

settles = body_of(plain, "public static float settlesAt(float heatIn, float cooling)")
if "AMBIENT + heatIn" not in settles:
    fail("the temperature a readout predicts is not the fixed point of the temperature "
         "the core actually reaches, so the number shown before turning a reactor up is "
         "not the number that matters")
if "POSITIVE_INFINITY" not in settles:
    fail("a core with no cooling at all settles at a temperature, so a bare reactor is "
         "survivable and coolant is decoration")

wear = body_of(plain, "public static float wearAt(")
if "temperature <= SAFE" not in wear:
    fail("a core at exactly the safe temperature is already wearing out, so the number "
         "a player is shown is not the number the game uses")
if "through * through" not in wear:
    fail("wear climbs in a straight line, so overshooting slightly and ignoring the "
         "problem entirely cost the same")

print("the model          : energy kept in whole units and rounded against the player, "
      "one direction a tick, and heat that settles where the readout says")

# ---------------------------------------------------------------------------
# 4. The ladder and the joints below
# ---------------------------------------------------------------------------

if "IndustryRules.SEPARATOR_MULTIPLIER + 1" not in plain:
    fail("Age III's ore multiplier is a number of its own rather than the rung below "
         "plus one, so the ladder can be re-tuned into 1, 2, 4 in one file")

if "IndustryRules.roundTripBest(" not in plain:
    fail("Age III adds a motor and does not put it through Age II's free-energy proof; "
         "the alternator does not care which age is driving it")
if "servoRoundTrip" not in plain:
    fail("there is no name for what a servo driving alternators returns, so nothing can "
         "check it")

for forbidden in ("FE_PER_RPM", "dynamoOutput", "alternatorOutput"):
    if re.search(r"public static.*\b" + forbidden, plain):
        fail(f"Age III declares its own way of turning rotation into electricity "
             f"({forbidden}); Age II's proof covers Age II's alternator and says nothing "
             f"whatever about a better one")

least = constant("SHAFT_LEAST_RPM")
brass = re.search(r"BRASS\((\d+(?:\.\d+)?)F\)",
                  read(BELOW[0], "KineticRules.java"))
if least and brass:
    try:
        value = float(least.rstrip("F"))
    except ValueError:
        value = None
    if value is not None and value <= float(brass.group(1)):
        fail("a brass shaft can drive an Age III machine, so the age below has no reason "
             "to upgrade its shafts before reaching up — the same off-by-one the harness "
             "caught in Age II against timber")

if "IndustryRules.ON_A_SHAFT / 2.0F" not in plain:
    fail("Age III's shaft penalty is not written against Age II's, so the fallback route "
         "can stop getting worse as the spine climbs")

if "IndustryRules.SHAFT_FE_PER_TORQUE" not in plain:
    fail("Age III invents its own exchange rate between torque and electricity, so the "
         "two halves of the spine can drift apart")

if "KineticRules.MAX_RPM" not in plain:
    fail("a servo's free speed is a number of its own rather than the fastest speed in "
         "the pack, which is the whole reason to build one")

print("the ladder         : ore 1, 2, 3 by construction; a servo through Age II's proof; "
      "and a shaft that will do at " + str(constant("ON_A_SHAFT")) + " above "
      + str(constant("SHAFT_LEAST_RPM")) + " rpm")

# ---------------------------------------------------------------------------
# 5. The age in the world
# ---------------------------------------------------------------------------
#
# Everything above is arithmetic. This is the half that can be wrong while
# every number is right: a block with no model, a cell that re-walks the grid
# sixty times a second, a core that keeps making power after it has worn out,
# a servo that caps the line it is driving.

GENERATOR = os.path.join(WORK, "tools/gen_atomics.py")
if not os.path.isfile(GENERATOR):
    fail("there is no generator, so every asset in the age is kept by hand")
else:
    with tempfile.TemporaryDirectory() as tmp:
        environment = dict(os.environ, ELYSIUM_ATOMICS_OUT=tmp)
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

blocks_src = read(SRC, "registry/AtomicBlocks.java")
items_src = read(SRC, "registry/AtomicItems.java")
registered = re.findall(r'BLOCKS\.register\("([a-z_]+)"', strip_comments(blocks_src, True))
parts = re.findall(r'part\("([a-z_]+)"\)', strip_comments(items_src, True))
AGE_BLOCKS = len(registered)

# The reactor hall's three blocks are registered from a loop over HallRules
# rather than named one at a time, so a reading that walks literal
# register("...") calls cannot see them. Parsed out of the rules instead.
HALL_RULES = strip_comments(read(SRC, "rules/HallRules.java"), keep_strings=True)
hall = [found.group(1) for found in
        (re.search(r'String ' + key + r' = "([a-z_]+)"', HALL_RULES)
         for key in ("CASING", "ROD", "CONTROLLER")) if found]
if len(hall) != 3:
    fail(f"HallRules no longer says what a reactor hall is built of; this file "
         f"read {len(hall)} of its three blocks, and every check below it is "
         f"then checking nothing")
registered = registered + hall

if AGE_BLOCKS != 10:
    fail(f"Age III has {AGE_BLOCKS} block(s) beside the reactor hall; the spec "
         f"says ten — three cells, a core and its plumbing and a panel, a "
         f"servo and three machines")

block_items = set(re.findall(r'registerSimpleBlockItem\(AtomicBlocks\.([A-Z_]+)\)',
                             strip_comments(items_src, True)))
order = re.search(r"ORDER = List\.of\(([^;]+)\);", strip_comments(items_src, True))
in_tab = set(re.findall(r"[A-Z_]{3,}", order.group(1))) if order else set()
# What the tab actually shows, read out of the one lambda that builds it: a
# roster named anywhere in the registry would otherwise satisfy a rule about
# being *in the tab*.
_display = re.search(r"displayItems\(\(parameters, output\) -> \{(.*?)\}\)",
                     re.sub(r"\s+", " ", strip_comments(items_src, True)))
_shown = _display.group(1) if _display else ""
if "HALL" not in _shown:
    fail("the creative tab does not reach for the reactor hall's roster, so "
         "its three blocks are blocks nobody in a creative world can find")

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

for name in registered:
    # The reactor hall's three are registered from a roster rather than named,
    # so they have no literal item line and no literal name in the tab's
    # order. The count checks in the registry catch a missing one, and the
    # rule above checks the tab reaches for the roster at all.
    if name.upper() not in block_items and name not in hall:
        fail(f"{name} has no item, so it exists only to a command")
    if name.upper() not in in_tab and name not in hall:
        fail(f"{name} is in no creative tab, so nobody in a creative world can find it")
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

# The panel's model and its collision box are written in two files that cannot
# see each other. A block you cannot hit where you can see it is the result.
panel_box = re.search(r"SHAPE = box\(0, 0, 0, 16, (\d+), 16\)",
                      strip_comments(read(SRC, "block/SolarPanelBlock.java")))
panel_model = os.path.join(RES, "assets", NS, "models/block/solar_panel.json")
if panel_box and os.path.isfile(panel_model):
    element = json.load(open(panel_model, encoding="utf-8"))["elements"][0]
    if float(element["to"][1]) != float(panel_box.group(1)):
        fail("the solar panel's model and its collision box are different heights, so it "
             "can be hit where it cannot be seen or the other way round")

core_state = os.path.join(RES, "assets", NS, "blockstates/reactor_core.json")
if os.path.isfile(core_state):
    variants = json.load(open(core_state, encoding="utf-8")).get("variants", {})
    if set(variants) != {"lit=false", "lit=true"}:
        fail("the reactor's blockstate does not name both lit values, so either the game "
             "cannot render it or it looks the same running and cold")
    if variants.get("lit=false", {}).get("model") == variants.get("lit=true", {}).get("model"):
        fail("a reactor looks the same running and cold, so the one block in the pack "
             "that can take the roof off gives no sign that it is on")

# --- the blocks, and the ways they can be made to lie ---------------------

cell = strip_comments(read(SRC, "block/CellBlock.java"))
if "AtomicRules.reserveOf" not in body_of(cell, "public IndustryRules.Reserve reserve()"):
    fail("a cell tells the grid something other than what the rules say it can do")
flowed = body_of(cell, "public void flowed(")
if "AtomicRules.after(cell(), held, flow, bank)" not in flowed:
    fail("a cell works out its own new contents rather than asking the rules, so the "
         "rounding that keeps energy from appearing is decided twice")
if "if (moved)" not in flowed or "unsettle()" not in flowed:
    fail("a cell either never asks for the grid to be worked out again, or asks every "
         "tick — the first makes a full cell invisible, the second re-walks four "
         "thousand blocks sixty times a second")
if "couldGive() != toldGive" not in flowed:
    fail("a cell does not compare what it would now report against what it last "
         "reported, so it cannot tell whether anything changed")

reactor = strip_comments(read(SRC, "block/ReactorBlock.java"))
making = body_of(reactor, "public IndustryRules.Generator generator()")
if "throttle()" not in making:
    fail("a reactor's output is not what its throttle says")
throttle = body_of(reactor, "public float throttle()")
if "spent()" not in throttle or "fuelLeft <= 0" not in throttle:
    fail("a worn-out or unfuelled reactor still makes power, so neither wear nor fuel "
         "is a limit on anything")
ticking = body_of(reactor, "protected void serverTick(")
for needed in ("AtomicRules.nextTemperature", "AtomicRules.heatOf",
               "AtomicRules.coolingWith(channels())", "AtomicRules.wearAt(temperature)"):
    if needed not in ticking:
        fail(f"a reactor does not use {needed}, so its heat is worked out somewhere the "
             f"harness cannot execute it")
if "AtomicRules.meltingDown(temperature)" not in ticking:
    fail("a reactor never melts down, so every number about heat is decoration")
if "meltDown(level, pos)" not in ticking:
    fail("a melting reactor is not dealt with, so the failure the whole age is built "
         "around does nothing")
ending = body_of(reactor, "private void meltDown(")
for needed in ("level.removeBlock", "level.explode", "AtomicRules.MELTDOWN_RADIUS"):
    if needed not in ending:
        fail(f"a meltdown does not {needed}, so it is either a block that survives its "
             f"own destruction, a bang that never happens, or a radius decided in two "
             f"places")
counting = body_of(reactor, "public int channels()")
if "AtomicRules.MOST_CHANNELS" not in counting:
    fail("a reactor counts more coolant channels than the rules say it takes, so the "
         "arithmetic the harness proved does not describe the block")
if "CoolantChannelBlock" not in counting:
    fail("a reactor's cooling does not come from blocks a player has to place, so the "
         "one decision the age asks for is not a build")

servo = strip_comments(read(SRC, "block/ServoBlock.java"))
if "AtomicRules.MADE_OF" not in body_of(servo, "public KineticRules.Shaft material()"):
    fail("a servo does not say what it is made of, so it inherits Age II's steel and "
         "caps the line it is driving below the speed it drives it at")
for reading in ("share()", "supply()", "demand()"):
    if reading in body_of(servo, "public IndustryRules.Machine machine()"):
        fail(f"a servo's draw on the grid reads {reading}, so what the grid can afford "
             f"depends on what the servo asks for and the other way round — that "
             f"oscillates and never settles")
if "AtomicRules.servoAsSource" not in body_of(servo, "public KineticRules.Source source()"):
    fail("a servo drives the line by arithmetic of its own, so the free-energy proof "
         "covers a servo the game does not have")

works = strip_comments(read(SRC, "block/WorksBlock.java"))
if "AtomicRules.shaftShare(rpm)" not in body_of(works, "public float shaftShare("):
    fail("an Age III machine is driven by a shaft on Age II's terms, so the fallback "
         "route stops getting worse as the spine climbs")
if "AtomicRules.ON_A_SHAFT" not in body_of(works, "public float bestOnAShaft()"):
    fail("an Age III machine lets go of a shaft at Age II's share rather than its own")

panel = strip_comments(read(SRC, "block/SolarPanelBlock.java"))
if "canSeeSky" not in panel:
    fail("a solar panel does not ask whether it can see the sky, so one underground "
         "works as well as one on a roof")

MACHINE_KINDS = {
    "EnricherBlock": "ENRICHING",
    "ReactionChamberBlock": "REACTING",
    "CentrifugeBlock": "CENTRIFUGING",
}
claimed = {}
for machine, wanted in MACHINE_KINDS.items():
    body = body_of(strip_comments(read(SRC, f"block/{machine}.java")),
                   "public RecipeType<? extends PlantRecipe> recipes()")
    named = re.search(r"AtomicRecipe\.Kind\.(\w+)", body)
    if not named:
        fail(f"{machine} names no recipe kind, so it reads nothing")
        continue
    if named.group(1) != wanted:
        fail(f"{machine} reads {named.group(1)} recipes; it is the machine {wanted} "
             f"recipes are written for, and nothing else reads them")
    if named.group(1) in claimed:
        fail(f"{machine} and {claimed[named.group(1)]} both read {named.group(1)} "
             f"recipes, so one does the other's job and a whole catalogue is unreachable")
    claimed[named.group(1)] = machine
for kind in MACHINE_KINDS.values():
    if kind not in claimed:
        fail(f"no machine reads {kind} recipes, so that whole catalogue is unreachable")

if "AtomicRules.ENRICHER_MULTIPLIER" not in body_of(
        strip_comments(read(SRC, "block/EnricherBlock.java")), "public int multiplier()"):
    fail("the enricher's tripling is a number of its own rather than the spine's, so "
         "the material ladder is decided twice")
for other in ("ReactionChamberBlock", "CentrifugeBlock"):
    if "multiplier()" in strip_comments(read(SRC, f"block/{other}.java")):
        fail(f"{other} multiplies its output; the tripling is the enricher's alone and "
             f"is what the enricher is for")

# --- the recipes, and what this age owes the pack -------------------------

OURS = {f"{NS}:{name}" for name in registered} | {f"{NS}:{name}" for name in parts}
OUR_TAGS = {"c:raw_materials/uranium", "c:ingots/uranium"}
BELOW_TAGS = {"c:ingots/steel"} | {f"c:plates/{m}" for m in
                                   ("iron", "copper", "gold", "steel")}
below = set()
for module, namespace in (("elysium-industry", "elysiumindustry"),
                          ("elysium-kinetics", "elysiumkinetics")):
    folder = os.path.join(ROOT, module, "src/main/resources/data", namespace, "recipe")
    if os.path.isdir(folder):
        # Walked rather than listed. An age below keeps its machine recipes in
        # folders — recipe/smelting/steel_ingot.json — and a reading that only
        # looks at the top level decides the age below does not make steel,
        # which is exactly the thing it is best known for.
        for dirpath, _, filenames in os.walk(folder):
            for name in filenames:
                if name.endswith(".json"):
                    below.add(f"{namespace}:{name[:-5]}")

recipe_root = os.path.join(RES, "data", NS, "recipe")
crafted = {}
machine_recipes = {kind: 0 for kind in ("enriching", "reacting", "centrifuging")}
for dirpath, _, filenames in os.walk(recipe_root):
    for name in sorted(filenames):
        if not name.endswith(".json"):
            continue
        path = os.path.join(dirpath, name)
        recipe = json.load(open(path, encoding="utf-8"))
        where = os.path.relpath(path, recipe_root)
        folder = os.path.dirname(where)
        if folder:
            if recipe.get("type") != f"{NS}:{folder}":
                fail(f"{where} declares the type {recipe.get('type')!r} while sitting in "
                     f"{folder}/, so it will work in neither machine")
            machine_recipes[folder] = machine_recipes.get(folder, 0) + 1
            asked = recipe.get("input", {})
            item = asked.get("item") or ("#" + asked["tag"] if "tag" in asked else "")
            if item.startswith("#"):
                if item[1:] not in (OUR_TAGS | BELOW_TAGS) \
                        and not item.startswith("#minecraft:"):
                    fail(f"{where} asks for the tag {item}, which nothing in this install "
                         f"is guaranteed to fill")
            elif not (item.startswith("minecraft:") or item in OURS or item in below):
                fail(f"{where} asks for {item}, which the base game and the ages below "
                     f"do not have between them")
            if folder == "enriching" and recipe.get("result", {}).get("count", 1) != 1:
                fail(f"{where} states its own tripling; the multiplier is the machine's "
                     f"and lives in AtomicRules so the ladder is one number")
            continue
        crafted[where[:-5]] = recipe

for kind, count in machine_recipes.items():
    if count == 0:
        fail(f"nothing can be made by {kind}, so one of Age III's three machines does "
             f"nothing at all")


def ingredients(recipe):
    """Every item and tag a recipe names, however it is written."""
    named = list(recipe.get("key", {}).values())
    named.extend(recipe.get("ingredients", []))
    if "ingredient" in recipe:
        named.append(recipe["ingredient"])
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
            if asked[1:] not in (OUR_TAGS | BELOW_TAGS) \
                    and not asked.startswith("#minecraft:"):
                fail(f"recipe/{name} asks for the tag {asked}, which nothing in this "
                     f"install is guaranteed to fill")
        elif not (asked.startswith("minecraft:") or asked in OURS or asked in below):
            fail(f"recipe/{name} asks for {asked}; Age III is buildable out of the base "
                 f"game and the ages below it, and nothing else")

# The gate into the age. Everything here is built out of Age II's plate, which
# means a factory, rather than out of an ore somebody might trip over.
for name in registered:
    asked = ingredients(crafted.get(name, {}))
    if not any(a.startswith("#c:plates/") for a in asked):
        fail(f"the {name} can be built without a single plate, so the third age can be "
             f"reached without ever having had a factory")

# The uranium this age owes the pack, because elysium-ores generates the ore
# and every item that used to fill these tags belongs to a mod being replaced.
for tag in sorted(OUR_TAGS):
    namespace, path = tag.split(":", 1)
    where = os.path.join(RES, "data", namespace, "tags/item", path + ".json")
    if not os.path.isfile(where):
        fail(f"{tag} is asked for by a recipe and defined nowhere, so that recipe fails "
             f"to load — and the uranium ore the pack already generates drops nothing")
    elif not json.load(open(where, encoding="utf-8")).get("values"):
        fail(f"{tag} is empty, so every recipe that asks for it fails to load")

ores = os.path.join(ROOT, "elysium-ores/src/main/java/com/elysium/ores/data/"
                          "OreCatalogue.java")
if os.path.isfile(ores) and "uranium" in read(ores):
    filled = os.path.join(RES, "data/c/tags/item/raw_materials/uranium.json")
    if not os.path.isfile(filled):
        fail("elysium-ores generates uranium and nothing in this install supplies any, "
             "so every uranium ore in the world drops nothing when mined")

# ---------------------------------------------------------------------------
# The reactor hall: a reactor whose size is a decision
# ---------------------------------------------------------------------------

PORT_BLOCK = strip_comments(read(SRC, "block/ReactorPortBlock.java"),
                            keep_strings=True)
for needed, why in (
        ("public static ShapeRules.Shape boxOf(",
         "nothing builds the box of a given size, so seven shapes would have "
         "to be written out and the seventh is three hundred and forty-three "
         "characters of the same letter"),
        ("public static float safeThrottle(",
         "nothing solves the throttle a hall is safe at, so the one control a "
         "player has over a reactor is a slider they find by melting one"),
        ("public static List<String> faultsInLadder(",
         "nothing asks whether a bigger hall is a trade rather than an "
         "upgrade, which is the whole reason there is a second reactor"),
        ("public static List<String> faultsInPlumbing(",
         "nothing asks whether the cooling is a decision, so a reactor could "
         "be a generator you simply place")):
    if needed not in HALL_RULES:
        fail(why)

# The hall reuses Age II's shape arithmetic rather than having its own. Two
# multiblock checkers in one pack is two sets of bugs, and only one of them
# would be the one the harness sweeps.
if "import com.elysium.industry.rules.ShapeRules;" not in read(SRC, "rules/HallRules.java"):
    fail("the reactor hall has its own idea of what a shape is rather than "
         "the one Age II already proves, so there are two multiblock checkers "
         "in this pack and only one of them is swept")
if "ShapeRules.orientations(shape)" not in squashed(PORT_BLOCK):
    fail("the port does not try every orientation, so a hall built facing the "
         "wrong way is a hall that does not form and says nothing")
_look = squashed(body_of(PORT_BLOCK, "public void look("))
if "if (ShapeRules.sameAs(want, found)) {" not in _look:
    fail("the port decides for itself whether the blocks are a hall rather "
         "than asking the arithmetic that is swept at every size")
if "wrong = best;" not in _look:
    fail("the port throws away what it found wrong, so a player who has built "
         "a hall almost right is told nothing at all")
if "elysiumatomics.hall.unformed" not in PORT_BLOCK:
    fail("there is no sentence for a hall that has not formed, so the answer "
         "is the worst one there is: nothing happens")
_tick = squashed(body_of(PORT_BLOCK, "public static void tick("))
if "HallRules.coolingOf(side, port.channels)" not in _tick:
    fail("a hall sheds heat at a rate of its own rather than the one its size "
         "and plumbing give it, so the number a player watches is a number no "
         "harness has ever executed")

_sizes = re.search(r"int SMALLEST = (\d+);", HALL_RULES)
_largest = re.search(r"int LARGEST = (\d+);", HALL_RULES)
if not _sizes or not _largest:
    fail("HallRules no longer says how big a hall may be")
else:
    print(f"the reactor hall   : {int(_largest.group(1)) - int(_sizes.group(1)) + 1}"
          f" size(s) of reactor built out of three blocks, each one making "
          f"more altogether than the one below and less per rod - heat goes "
          f"as the cube of the side and cooling goes as the square, which is "
          f"what makes the size a decision rather than a ladder")


print(f"the age in the world: {len(registered)} block(s) and {len(parts)} part(s), each "
      f"modelled, dropped, named and craftable out of a factory; "
      f"{sum(machine_recipes.values())} machine recipe(s) across three machines")

# ---------------------------------------------------------------------------
# 6. The seam
# ---------------------------------------------------------------------------

spec = read(SPEC)
if not spec:
    fail("docs/SPEC_TECH.md is gone; it is the contract the ages are built against")
else:
    for needed in ("elysium-atomics", "Forge Energy"):
        if needed not in spec:
            fail(f"the spec no longer states {needed!r}, so a later age has nothing to "
                 f"build against")

main_class = read(SRC, "ElysiumAtomics.java")
if f'MODID = "{NS}"' not in main_class:
    fail("the module id is not what everything else in the tree spells it")

print("what it declares   : the game, the library, both ages below, and a spec the age "
      "above can read")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all atomics checks passed")
