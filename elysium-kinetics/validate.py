#!/usr/bin/env python3
"""
Validate Elysium Kinetics against its sources.

Run from anywhere:   python3 elysium-kinetics/validate.py

Age I is the spine: Ages II, III and IV all connect to this module's rotation,
so a number that drifts here drifts in four places. The checks follow that.

  1. INDEPENDENCE. Vanilla and elysium-lib. No content module, and in
     particular no knowledge of the ages built on top of this one.
  2. PURITY, and the harness run rather than read.
  3. THE MODEL. The design decisions that make this not-Create, read back out
     of the Java: a source is a curve and not a number, a line settles rather
     than switching off, gearing costs power, and a shaft has a rating.
  4. THE LADDER. Each rung of Age I is a thing the constants make possible and
     the rung before it did not. A constant that quietly flattens the ladder
     passes the harness — the harness checks the model is consistent — and
     changes the game.
  5. THE SEAM. What the module declares, and that the spec agrees with it.
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
SRC = os.path.join(WORK, "src/main/java/com/elysium/kinetics")
PURE = os.path.join(SRC, "rules")
HARNESS = os.path.join(WORK, "tools/KineticHarness.java")
HARNESS_MAIN = "com.elysium.kinetics.rules.KineticHarness"

#: How long the arithmetic is allowed to take. The honest sweeps in
#: the harness run in a few seconds; anything past this is a loop.
HARNESS_SECONDS = 60
SPEC = os.path.join(ROOT, "docs/SPEC_TECH.md")
NS = "elysiumkinetics"

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


def body_of(text, signature):
    """One method's or record's source, brace-matched from its declaration."""
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
    print("This is not the elysium-kinetics tree.")
    sys.exit(1)

print("=" * 70)
print("elysium-kinetics — the first age, and the spine the other three hang off")
print("=" * 70)

# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------
#
# The ages are a one-way dependency: II, III and IV know about I. If this file
# ever learns the word "mekanism", or the name of a module above it, the spine
# has become a circle and neither end can be built without the other.

# Matched as package paths, resource namespaces or quoted mod ids — never as
# bare words. "create" is a word Java uses about forty times in a registry
# file (DeferredRegister.create, CreativeModeTab), and a rule that flags those
# is a rule somebody deletes rather than reads.
FOREIGN = re.compile(
    r"(?:com\.simibubi\.create|com\.simibubi|mekanism\.|blusunrize\.|"
    r"owmii\.powah|gregtech\.|cofh\.thermal|appeng\.)"
    r"|\b(?:create|mekanism|immersiveengineering|powah|gregtech|thermal|ae2)\s*:"
    r"|\"(?:create|mekanism|immersiveengineering|powah|gregtech|ae2)\"", re.I)
UPWARD = re.compile(r"com\.elysium\.(industry|atomics|foundry|logistics|transit)")

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
        fail(f"{where} reaches up into a later age; the ages depend downwards only, "
             f"or neither end can be built on its own")

toml = read(RES, "META-INF/neoforge.mods.toml")
declared = set(re.findall(r'modId="([^"]+)"', toml))
allowed = {"${mod_id}", "neoforge", "minecraft", "elysiumlib"}
for mod in declared - allowed:
    fail(f"the module declares a dependency on {mod}; Age I needs the game and "
         f"the library and nothing else")
for needed in ("neoforge", "minecraft", "elysiumlib"):
    if needed not in declared:
        fail(f"the module does not declare {needed}")

print(f"independence       : {len(java_files)} source file(s), no mod it replaces, "
      f"no age above it")

# ---------------------------------------------------------------------------
# 2. Purity, and the harness executed
# ---------------------------------------------------------------------------

if not os.path.isdir(PURE):
    fail("there is no rules package, so there is nothing a harness could execute")
else:
    for name in sorted(os.listdir(PURE)):
        if not name.endswith(".java"):
            continue
        src = read(PURE, name)
        without_package = re.sub(r"^\s*package [^;]+;", "", src, count=1, flags=re.M)
        for forbidden in ("net.minecraft", "net.neoforged", "com.mojang", "com.elysium"):
            if forbidden in without_package:
                fail(f"rules/{name} reaches for {forbidden}; the rules are the JDK and "
                     f"nothing else, so they can be executed by a harness")

if not os.path.isfile(HARNESS):
    fail("there is no harness, so every number below is checked by reading")
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
                fail("the kinetic harness failed:\n" + harness_trouble(ran.stdout))
            else:
                print("the arithmetic     : " + ran.stdout.strip().splitlines()[-1])

rules = read(PURE, "KineticRules.java")
plain = strip_comments(rules)


def constant(name, kind=r"float"):
    m = re.search(r"public static final " + kind + r"\s+" + name + r"\s*=\s*([^;]+);", plain)
    return m.group(1).strip() if m else None


def source_of(name):
    m = re.search(r"public static final Source\s+" + name
                  + r"\s*=\s*new Source\(([\d.]+)F,\s*([\d.]+)F\)", plain)
    return (float(m.group(1)), float(m.group(2))) if m else None


def load_of(name):
    m = re.search(r"public static final Load\s+" + name
                  + r"\s*=\s*new Load\(([\d.]+)F,\s*([\d.]+)F\)", plain)
    return (float(m.group(1)), float(m.group(2))) if m else None


# ---------------------------------------------------------------------------
# 3. The model
# ---------------------------------------------------------------------------
#
# These are the decisions, not the arithmetic. The harness proves the numbers
# agree with each other; this proves they are still the numbers the design
# chose, and that the shapes that make the model what it is are still there.

torque_at = body_of(plain, "public float torqueAt(float rpm)")
if "1.0F - Math.abs(rpm) / freeSpeed" not in torque_at:
    fail("a source no longer falls off along a line between its stall torque and its "
         "free speed, so it is a fixed capacity again and the whole model is Create's")
if "Math.max(0.0F," not in torque_at:
    fail("a source driven past its free speed pushes backwards instead of freewheeling, "
         "so an overdriven line drives itself")

settle = body_of(plain, "public static Reading settle(")
if "(stall - demand) / slope" not in settle:
    fail("a line no longer settles where supply meets demand, which is the one line of "
         "algebra this module is built on")
if "if (demand >= stall) {" not in settle:
    fail("stalling is not decided by the loads outweighing every source at a standstill, "
         "so a line can stall while a source still has something left")
if "shears(shaft, rpm)" not in settle:
    fail("settling does not ask whether the line survives the speed it settled at, so a "
         "shaft rating is decoration")

reading_at = plain.find("public record Reading(")
reading_head = plain[reading_at:plain.find("{", reading_at)] if reading_at >= 0 else ""
reading = reading_head + body_of(plain, "public record Reading(")
if "supply" in reading:
    fail("Reading carries a supply again; at the settled speed supply equals demand by "
         "definition, so the field reads as information and carries none, and a gauge "
         "drawn from it sits at full on an idle line")
if "demand / capacity" not in reading:
    fail("effort is not measured against what the sources could make at a standstill, so "
         "the gauge does not climb and the model is a light switch after all")

for record, expected in (("public record Source(", "float size = Math.abs(ratio);"),
                         ("public record Load(", "float size = Math.abs(ratio);")):
    if expected not in body_of(plain, record):
        fail(f"{record.split()[2].rstrip('(')} reflects through a signed ratio, so gearing "
             f"in the direction a pair of cogs actually turns produces negative torque")

kept = body_of(plain, "public static float kept(int stages)")
if "Math.pow" not in kept or "GEAR_LOSS" not in kept:
    fail("gear stages no longer cost power, so a gear train is free and there is no "
         "reason to put a machine near its source")
if "MOST_STAGES" not in kept:
    fail("the loss curve is not floored, so an absurd train underflows and something "
         "downstream divides by zero")

waiting = body_of(plain, "public static float waitingFor(")
if "!load.working(rpm)" not in waiting or "load.leastRpm() > wanted" not in waiting:
    fail("a line cannot say what speed its stopped machines are waiting for, which is "
         "the half of the diagnosis the effort gauge cannot give")

if constant("GEAR_LOSS") != "0.03F":
    fail(f"a gear stage costs {constant('GEAR_LOSS')} rather than the 3% the spec states")
if constant("MAX_RPM") != "1024.0F":
    fail("the ceiling every age shares has moved")

line = strip_comments(read(PURE, "LineRules.java"))
if not line:
    fail("there is no LineRules, so a line with a pair of cogs in it has no arithmetic")

conflict = body_of(line, "public static boolean conflict(")
if "Math.abs(a - b) / size" not in conflict:
    fail("two ratios are compared without scaling by their size, so either a long gear "
         "train jams on float drift or a short one never jams at all")

assemble = body_of(line, "public static Assembly assemble(")
if ".through(driven.at().ratio(), driven.at().stages())" not in assemble:
    fail("sources are not reflected onto the root through their own gearing, so a line "
         "with cogs in it settles as though the cogs were not there")
if ".through(load.at().ratio(), load.at().stages())" not in assemble:
    fail("loads are not reflected onto the root, so a machine behind a step-up is free")
if "atRoot.rpm() * fastest" not in assemble:
    fail("shearing is decided at the root rather than at the fastest shaft on the line, "
         "so a line whose far end is spun to pieces reports itself intact")
if "KineticRules.Shaft.ASCENDANT" not in assemble:
    fail("the root settle is asked about shearing as well, so the line is judged twice "
         "against two different speeds and the first answer wins")
if "if (jammed)" not in assemble:
    fail("a jammed line still settles, so a gear train that fights itself turns anyway")

waiting_line = body_of(line, "public static float waitingFor(List<Drawn> loads,")
if "assembly.speedOf(drawn.at())" not in waiting_line:
    fail("a geared line judges its machines against the root's speed rather than their "
         "own, so a saw behind a step-up is reported as too slow while it is cutting")
if "drawn.load().leastRpm() > wanted" not in waiting_line:
    fail("the speed a geared line says it wants is not the number written on the "
         "machine, so the player is told to reach a speed that would not help")

print("the model          : a curve not a number, a line that settles, teeth that cost "
      "power, a shaft that can shear, and a tree of speeds that jams rather than "
      "insisting a shaft turns at two of them")

# ---------------------------------------------------------------------------
# 4. The ladder
# ---------------------------------------------------------------------------

crank = source_of("HAND_CRANK")
wheel = source_of("WATER_WHEEL")
sail = source_of("SAIL")
steam = source_of("STEAM_ENGINE")
millstone = load_of("MILLSTONE")
press = load_of("PRESS")
saw = load_of("SAW")
mixer = load_of("MIXER")
dynamo = load_of("DYNAMO")

if None in (crank, wheel, sail, steam, millstone, press, saw, mixer, dynamo):
    fail("one of Age I's sources or machines is no longer declared where the validator "
         "and the generators can read it")
else:
    if crank[1] > millstone[0] * 2:
        fail("a hand crank turns two millstones, so nobody builds a water wheel. "
             "(The stall rule is demand >= stall torque, so equality already stalls; "
             "this comparison must be written the same way the engine is.)")
    if crank[1] < millstone[0]:
        fail("a hand crank cannot turn one millstone, so the age has no first step")
    if wheel[0] >= saw[1]:
        fail("a water wheel is fast enough to run a saw on its own, so gearing is "
             "optional and the second half of Age I never has to be learned")
    if wheel[1] <= steam[1]:
        fail("steam is at least as strong as water, so the water wheel is obsolete the "
             "moment you can burn something")
    if steam[0] <= wheel[0]:
        fail("water is at least as fast as steam, so steam buys nothing")
    if mixer[0] <= press[0] or mixer[0] <= saw[0]:
        fail("the mixer is not the expensive machine, so nothing in Age I creates the "
             "pressure that makes a player want steam")
    if dynamo[1] < mixer[1]:
        fail("the dynamo asks less speed than the mixer, so Age I electricity is the "
             "easy option rather than the curiosity it is meant to be")

timber = re.search(r"TIMBER\((\d+(?:\.\d+)?)F\)", plain)
if not timber:
    fail("timber has no rating, so the first shaft anybody builds cannot shear")
elif steam and float(timber.group(1)) >= steam[0]:
    fail("a steam engine runs happily on timber shafts, so brass is cosmetic and the "
         "shaft ladder means nothing")

if timber and constant("FAST_AT") != f"{float(timber.group(1))}F":
    fail(f"a timber shaft shears at {timber.group(1)} and the recipe book's FAST band "
         f"begins at {constant('FAST_AT')}; they must be the same number or the book "
         f"and the shaft disagree about what fast means")

print(f"the ladder         : crank -> wheel -> gearing -> steam; 4 source(s) and "
      f"5 machine(s), each one a rung the one before it could not reach")

# ---------------------------------------------------------------------------
# 5. The machine hall
# ---------------------------------------------------------------------------
#
# The block layer, where the model meets the world. None of this is arithmetic
# a harness can run — it is about whether the world is asked the right
# questions and told the answers.

line_java = strip_comments(read(SRC, "block/KineticLine.java"))
block_java = strip_comments(read(SRC, "block/KineticBlock.java"), keep_strings=True)
entity_java = strip_comments(read(SRC, "block/KineticBlockEntity.java"))

across = body_of(line_java, "public static Float across(")
if "here.axis() != there.axis()" not in across:
    fail("two blocks turning about different axes are allowed to connect, so a shaft "
         "running north joins one running up")
if "face.getAxis() == here.axis()" not in across:
    fail("blocks in a row along their own axis are not treated as the same shaft")
if "KineticRules.ratio(here.teeth(), there.teeth())" not in across:
    fail("a mesh between two cogs does not use the gear ratio, so the teeth are "
         "decoration")
if "here.teeth() > 0 && there.teeth() > 0" not in across:
    fail("something without teeth meshes sideways with something else, so a shaft "
         "laid beside a shaft is a gearbox")

corner = body_of(line_java, "private static Float corner(")
if not corner:
    fail("there is no third connection rule, so power cannot turn a corner and a "
         "machine hall is a corridor")
# The whole guard, not the tail of it: "here.axis() != face.getAxis()" is a
# substring of "there.axis() != face.getAxis()", so checking for the first one
# alone passes on a file that only has the second. That is the fifth time a
# rule in this project has been satisfied by a longer name containing a
# shorter one.
for who in ("here", "there"):
    if f"!{who}.couplesAnyAxis() && {who}.axis() != face.getAxis()" not in corner:
        fail(f"a gearbox joins a shaft that is merely lying across its face rather "
             f"than running into it ({who} is not checked), so one box couples half "
             f"a hall by accident")
if "return -1.0F;" not in corner:
    fail("a gearbox does not reverse, so 'every shaft on it turns the other way' is "
         "no longer the sentence a player was told")

couplers = [os.path.relpath(path, SRC) for path in java_files
            if "couplesAnyAxis" in strip_comments(read(path))
            and "default boolean couplesAnyAxis" not in strip_comments(read(path))
            and "block/KineticLine.java" not in path.replace(os.sep, "/")]
if len(couplers) != 1 or "Gearbox" not in couplers[0]:
    fail(f"{len(couplers)} block(s) couple shafts of any axis ({couplers}); exactly one "
         f"should, or a machine hall stops being readable at a glance")

bearing = strip_comments(read(SRC, "block/WindmillBearingBlock.java"), keep_strings=True)
sails = body_of(bearing, "public static int sailsAround(")
if "queue" not in sails or "seen.add" not in sails:
    fail("a windmill counts sails in a box rather than following them out from the "
         "bearing, so loose blocks scattered in a field are a windmill")
if "face.getAxis() == axis" not in sails:
    fail("a sail is counted along the bearing's own axis, so a windmill is a drum")
if "Math.min(KineticRules.MOST_SAILS" not in sails:
    fail("a windmill has no limit, so a big enough one is a steam engine made of wool")
bearing_source = body_of(bearing, "public KineticRules.Source source()")
if "one.stallTorque() * sails" not in bearing_source:
    fail("sails do not add force, so building a bigger windmill buys nothing")
if "one.freeSpeed()" not in bearing_source:
    fail("sails add speed as well as force, so a big enough windmill outruns steam")

walk = body_of(line_java, "public static Found walk(")
if "LineRules.conflict(already.ratio(), reached)" not in walk:
    fail("a second path to the same block is not checked against the first, so a gear "
         "loop that insists one shaft turns at two speeds turns anyway")
if "LineRules.tooBig(seen.size())" not in walk:
    fail("the walk has no bound, so a large enough build is a server tick that never "
         "ends")
if "ratio == 1.0F ? 0 : 1" not in walk:
    fail("a plain length of shaft is counted as a gear stage, so running a line across "
         "a room costs power that no teeth ever took")

solve = body_of(line_java, "public static LineRules.Assembly solve(")
if "node.driven(assembly.speedOf(member.at()), assembly.running())" not in solve:
    fail("the members of a line are not told what it settled at, or are told the "
         "root's speed rather than their own")
if "node.material().rating() < weakest.rating()" not in solve:
    fail("the line is not judged by its weakest shaft, so one timber block in a brass "
         "line is safe")

for hook in ("onPlace", "onRemove", "neighborChanged"):
    if f"protected void {hook}(" not in block_java or \
            block_java.count("KineticLine.disturb") < 3:
        fail(f"{hook} does not disturb the line, so a change to the world is not "
             f"noticed until something else happens to change it too")

if "this.unsettled = true;" not in body_of(entity_java, "protected void loadAdditional("):
    fail("a block entity trusts the speed it was saved with, which is a speed that can "
         "be wrong the moment a chunk loads with half its line still unloaded")
tick = body_of(entity_java, "public static void tick(")
if "level.isClientSide" not in tick or "KineticLine.solve" not in tick:
    fail("the tick does not separate the sides; a client that solves the line for "
         "itself is a client working from half the chunks and overwriting the speed "
         "the server sent it")
if tick.find("isClientSide") > tick.find("KineticLine.solve"):
    fail("the client solves the line before it is told not to")
if "Block.UPDATE_CLIENTS" not in body_of(entity_java, "public void driven("):
    fail("a speed change is sent with flags that notify neighbours, and a neighbour "
         "update disturbs the line, which re-solves, which changes a speed: a machine "
         "hall re-walking itself every tick because it said it had sped up")
if "level.sendBlockUpdated" not in body_of(entity_java, "public void driven("):
    fail("a change in speed is not sent to the client, so the shaft a player is "
         "looking at is turning at whatever it was doing when the chunk arrived")
if "KineticLine.disturb" not in body_of(entity_java, "public void setRemoved()"):
    fail("breaking a block does not disturb its line from the block entity's side")

readout = body_of(block_java, "public static Component reading(")
for state, key in (("jammed", "line.jammed"), ("sheared", "line.sheared"),
                   ("stalled", "line.stalled"), ("waiting", "line.waiting"),
                   ("running", "line.reading")):
    if key not in readout:
        fail(f"the readout cannot say a line is {state}, which is one of the five "
             f"things a player needs to be able to tell apart")
if readout.find("line.jammed") > readout.find("line.stalled"):
    fail("a jammed line is reported as stalled, which sends a player looking for a "
         "source they already have")

shortfall = body_of(block_java, "private static float shortfall(")
if "assembly.speedOf(member.at())" not in shortfall:
    fail("machines are judged against the root's speed rather than their own, so a saw "
         "behind a step-up is reported too slow while it is cutting")

print("the machine hall   : three ways to connect and no more - along a shaft, through "
      "a mesh, and round a gearbox - one walk per change, every member told its own "
      "speed, and five things a player can tell apart")

# ---------------------------------------------------------------------------
# 6. What is actually in the jar
# ---------------------------------------------------------------------------

registry = strip_comments(read(SRC, "registry/KineticBlocks.java"), keep_strings=True)
registered = re.findall(r'BLOCKS\.register\(\s*"([a-z_]+)"', re.sub(r"\s+", " ", registry))
if not registered:
    fail("no blocks are registered, so Age I is a rules class")

items = re.sub(r"\s+", " ", strip_comments(read(SRC, "registry/KineticItems.java"),
                                          keep_strings=True))
# The one list the tab shows, read out of its own declaration rather than by
# looking at the whole file — a name mentioned anywhere in the registry would
# otherwise satisfy a check about being *in the tab*.
_order = re.search(r"ORDER = java\.util\.List\.of\(([^;]*)\);", items)
tab_order = _order.group(1) if _order else ""
if not tab_order:
    fail("the creative tab has no declared order, so what it shows is whatever the "
         "registry happened to list")

lang_path = os.path.join(RES, "assets", NS, "lang/en_us.json")
lang = read(lang_path)
lang_keys = set(json.loads(lang).keys()) if lang else set()

for name in registered:
    for what, where in (("blockstate", f"assets/{NS}/blockstates/{name}.json"),
                        ("model", f"assets/{NS}/models/block/{name}.json"),
                        ("item model", f"assets/{NS}/models/item/{name}.json"),
                        ("drop", f"data/{NS}/loot_table/blocks/{name}.json"),
                        ("recipe", f"data/{NS}/recipe/{name}.json")):
        if not os.path.isfile(os.path.join(RES, where)):
            fail(f"{name} has no {what}")
    if f"block.{NS}.{name}" not in lang_keys:
        fail(f"{name} has no name in any language")
    if f"registerSimpleBlockItem(KineticBlocks.{name.upper()})" not in items:
        fail(f"{name} has no item, so it can only be placed by a command")
    if name.upper() not in tab_order:
        fail(f"{name} is not in the creative tab, so nobody can find it")

# Every texture a model names has to exist, or the block is a black and purple
# cube and nothing says so until somebody places one.
textures = 0
for name in registered:
    model = read(RES, f"assets/{NS}/models/block/{name}.json")
    for reference in re.findall(rf'"{NS}:block/([a-z_]+)"', model):
        textures += 1
        if not os.path.isfile(os.path.join(RES, f"assets/{NS}/textures/block/{reference}.png")):
            fail(f"{name}'s model names the texture {reference}, which is not there")

# Every machine reads its own recipes, and every machine must have some: a
# block that draws torque and does no work is a block that was registered and
# then forgotten.
KINDS = ("milling", "pressing", "sawing", "mixing")
recipes = 0
paces = set()
for kind in KINDS:
    folder = os.path.join(RES, f"data/{NS}/recipe/{kind}")
    names = sorted(os.listdir(folder)) if os.path.isdir(folder) else []
    if not names:
        fail(f"nothing can be {kind[:-3]}ed, so the machine that does it draws torque "
             f"and does no work")
    for name in names:
        recipes += 1
        recipe = json.loads(read(folder, name))
        if recipe.get("type") != f"{NS}:{kind}":
            fail(f"{kind}/{name} declares {recipe.get('type')}, so it sits in one "
                 f"machine's folder and runs in another's — or in neither")
        paces.add(recipe.get("pace"))
        for field in ("input", "result"):
            blob = json.dumps(recipe.get(field, {}))
            for namespace in re.findall(r'"([a-z_]+):', blob):
                if namespace not in ("minecraft", NS):
                    fail(f"{kind}/{name} names {namespace}, so the recipe needs a mod "
                         f"Age I does not depend on")
if len(paces) < 2:
    fail("every machine recipe asks for the same pace, so the speed of the line never "
         "decides what can be made and the pace field does nothing")

# One shape, four types. Four copies of a recipe class is how the fifth machine
# ends up with a serializer that quietly reads the fourth one's fields.
shared = strip_comments(read(SRC, "recipe/KineticRecipe.java"), keep_strings=True)
if not shared:
    fail("there is no shared recipe class, so each machine has its own copy to drift")
kinds_declared = re.findall(r'^\s*([A-Z]+)\("([a-z]+)"\)', shared, re.M)
if {kind for _, kind in kinds_declared} != set(KINDS):
    fail(f"the recipe kinds in Java are {[k for _, k in kinds_declared]} and the folders "
         f"are {list(KINDS)}; a kind with no folder is a machine nobody can give work to")
if "new KineticRecipe(input, result, ticks, pace, kind)" not in shared:
    fail("a recipe's kind is read from the file rather than from the type it was "
         "registered under, so a file can claim to be a sawing recipe while sitting in "
         "the pressing type and work in neither")

machine_java = strip_comments(read(SRC, "block/MachineBlock.java"), keep_strings=True)
if not machine_java:
    fail("there is no shared machine block, so four machines have four copies of the "
         "same two slots and the same grind loop")
for needed, why in (
        ("public abstract KineticRules.Load load()", "how much torque it draws"),
        ("public abstract KineticRecipe.Kind kind()", "which recipes it reads"),
        ("public abstract String saying()", "what it says when it is not working")):
    if needed not in machine_java:
        fail(f"a machine does not have to declare {why}, so adding one means reading "
             f"the base class to find out what it forgot")
tick = body_of(machine_java, "protected void serverTick(")
if "recipe.possibleAt(speed)" not in tick:
    fail("a machine works at any speed, so the pace on a recipe is decoration")
if "insertItem(OUT, result, true)" not in tick:
    fail("a machine with a full output destroys what it makes next")

machines = [name for name in sorted(os.listdir(os.path.join(SRC, "block")))
            if name.endswith("Block.java")
            and "extends MachineBlock" in read(SRC, "block", name)]
if len(machines) != len(KINDS):
    fail(f"{len(machines)} machine block(s) for {len(KINDS)} recipe kind(s): "
         f"{machines}")

print(f"the hall's contents: {len(registered)} block(s), each modelled, named, drawn, "
      f"dropped and craftable; {textures} texture reference(s) all present; "
      f"{recipes} machine recipe(s) across {len(KINDS)} machine(s) and "
      f"{len(paces)} pace(s)")

# ---------------------------------------------------------------------------
# 7. The seam
# ---------------------------------------------------------------------------

spec = read(SPEC)
if not spec:
    fail("docs/SPEC_TECH.md is gone; it is the contract Ages II to IV are built against")
else:
    for needed in ("free speed", "stall torque", "rpm = (Σs − D) / Σ(s/f)", "MAX_RPM = 1024"):
        if needed not in spec:
            fail(f"the spec no longer states {needed!r}, so a later age has nothing to "
                 f"build against")
    for material in ("Timber", "Brass", "Steel", "Ascendant"):
        if material not in spec:
            fail(f"the spec does not mention the {material} shaft")
    if "stress" in spec.lower() and "citizens" not in spec.lower():
        fail("the spec uses the word stress without saying why this module does not; "
             "elysium-lib already has a stress package about citizens")

main_class = read(SRC, "ElysiumKinetics.java")
if f'MODID = "{NS}"' not in main_class:
    fail("the module id is not what everything else in the tree spells it")

print("what it declares   : the game, the library, and a spec the later ages can read")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all kinetics checks passed")
