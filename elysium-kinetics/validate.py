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


def squashed(text):
    """One method's source with every run of whitespace made one space.

    A rule that names a whole statement has to survive the statement being
    re-wrapped by a formatter, or it is a rule that fails the first time
    somebody tidies the file and then gets deleted rather than fixed.
    """
    return re.sub(r"\s+", " ", text)


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

# A blockstate names the variants the block actually has. A sail is a plain
# Block with no properties, so its one state is ""; the machines carry AXIS
# from KineticBlock. The sail shipped with axis=x/y/z variants and the game
# logged "missing model for variant: 'elysiumkinetics:sail#'" at resource
# reload and drew the missing model — a WARN line nothing else repeats.
_flat_registry = re.sub(r"\s+", " ", registry)
for name in registered:
    # Not "plain": that name is the comment-stripped rules source that
    # constant() reads, and shadowing it here made the dynamo rule below
    # search a bool.
    plain_block = re.search(rf'BLOCKS\.register\( ?"{name}" ?, ?\(\) ?-> ?new Block\(',
                            _flat_registry) is not None
    state = json.loads(read(RES, f"assets/{NS}/blockstates/{name}.json") or "{}")
    keys = set(state.get("variants", {}))
    if plain_block and keys != {""}:
        fail(f"{name} is a plain Block with one state, but its blockstate has variants "
             f"{sorted(keys)} rather than the empty one, so the game finds no model for it")
    if not plain_block and keys and not all(key.startswith("axis=") for key in keys):
        fail(f"{name} carries AXIS, but its blockstate has variants {sorted(keys)}")

items = re.sub(r"\s+", " ", strip_comments(read(SRC, "registry/KineticItems.java"),
                                          keep_strings=True))
# The one list the tab shows, read out of its own declaration rather than by
# looking at the whole file — a name mentioned anywhere in the registry would
# otherwise satisfy a check about being *in the tab*.
# The engines and the fuels are registered from a loop over EngineRules, so
# they are invisible to a reading that walks literal register("...") calls —
# the same blind spot that once let five hundred blocks go unchecked in
# elysium-logistics. Parsed out of the rules and added by hand.
ENGINE_RULES = strip_comments(read(SRC, "rules/EngineRules.java"),
                              keep_strings=True)
engine_ids = re.findall(r'new Engine\("([a-z_]+)"', ENGINE_RULES)
fuel_ids = [one.split(":", 1)[1] for one
            in re.findall(r'new Fuel\("([a-z:_]+)"', ENGINE_RULES)
            if one.startswith(NS + ":")]
_refinery = re.search(r'REFINERY = "([a-z_]+)"', ENGINE_RULES)
refinery = _refinery.group(1) if _refinery else ""
if not engine_ids or not fuel_ids or not refinery:
    fail(f"EngineRules no longer says what the engines are; this file read "
         f"{len(engine_ids)} engine(s) and {len(fuel_ids)} fuel(s), and every "
         f"check below it is then checking nothing")
# The dynamo is registered under a name held in the block rather than typed
# into the registry, so a reading that walks literal register("...") calls
# cannot see it either. Read it out of the block.
DYNAMO_BLOCK = strip_comments(read(SRC, "block/DynamoBlock.java"),
                              keep_strings=True)
_dynamo = re.search(r'String NAME = "([a-z_]+)";', DYNAMO_BLOCK)
dynamo = _dynamo.group(1) if _dynamo else ""
if not dynamo:
    fail("DynamoBlock no longer says what it is called, so the seam is a block "
         "nothing below it can find")
elif f"BLOCKS.register(\n            com.elysium.kinetics.block.DynamoBlock.NAME" \
        not in read(SRC, "registry/KineticBlocks.java") \
        and "DynamoBlock.NAME" not in registry:
    fail("the dynamo is not registered from its own name, so the name in the "
         "block and the name in the world can drift apart")

# The anchors are registered from a loop over their own rules too.
ANCHOR_RULES = strip_comments(read(SRC, "rules/AnchorRules.java"),
                              keep_strings=True)
_aflat = re.sub(r"\s+", " ", ANCHOR_RULES)
_widest = re.search(r"int WIDEST = (\d+);", ANCHOR_RULES)
WIDEST = int(_widest.group(1)) if _widest else -1
anchors = [(one[0], WIDEST if one[1] == "WIDEST" else int(one[1]), one[2])
           for one in re.findall(
               r'new Anchor\("([a-z_]+)", (WIDEST|\d+), '
               r"KineticRules\.Shaft\.([A-Z]+)\)", _aflat)]
anchor_ids = [one[0] for one in anchors]
if not anchors or WIDEST < 1:
    fail("AnchorRules no longer says what the anchors are, so every check "
         "below about them is checking nothing")

registered = sorted(set(registered) | set(engine_ids) | {refinery}
                    | ({dynamo} if dynamo else set()) | set(anchor_ids))

# What the tab shows, read out of the one method that builds it.
#
# It used to be a literal list and is now a list plus two rosters walked from
# the rules, because an engine added to the ladder should appear in the tab
# the same day rather than the day somebody remembers. So the covered set is
# the names written down plus, when the method reaches for a roster, every
# name in that roster.
_order = re.search(r"List<DeferredItem<BlockItem>> order\(\) \{(.*?)\}", items)
if not _order:
    _order = re.search(r"ORDER = java\.util\.List\.of\(([^;]*)\);", items)
tab_order = _order.group(1) if _order else ""
if "ENGINES.values()" in tab_order:
    tab_order += " " + " ".join(name.upper() for name in engine_ids)
if "REFINERY" in tab_order:
    tab_order += " " + refinery.upper()
if "ANCHORS.values()" in tab_order:
    tab_order += " " + " ".join(name.upper() for name in anchor_ids)
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
    if f"registerSimpleBlockItem(KineticBlocks.{name.upper()})" not in items \
            and name not in engine_ids and name not in anchor_ids:
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
PROCESS_RULES = strip_comments(read(SRC, "rules/ProcessRules.java"),
                               keep_strings=True)
_pflat = re.sub(r"\s+", " ", PROCESS_RULES)
_farm = re.search(r'String FARM = "([a-z]+)"', PROCESS_RULES)
FARM = _farm.group(1) if _farm else ""
_seeded = re.search(r"List<String> SEEDED = List\.of\(([^;]*?)\);", _pflat)
SEEDED = re.findall(r'"([a-z_]+)"', _seeded.group(1)) if _seeded else []
if not FARM or not SEEDED:
    fail("ProcessRules no longer says which module the machines join onto, so "
         "every check below about the join is checking nothing")

MACHINE_OF = {"mill": "milling", "press": "pressing",
              "saw": "sawing", "stir": "mixing"}


def _rid(text):
    text = text.strip()
    if text.startswith("FARM"):
        return FARM + ":" + text.split('"')[-2].lstrip(":")
    return text.strip('"')


steps = {}
for _one in re.finditer(
        r'(mill|press|saw|stir)\("([a-z_]+)", '
        r'(FARM \+ ":[a-z_]+"|"[^"]*"), (FARM \+ ":[a-z_]+"|"[^"]*"), '
        r"(\d+), (\d+), (SLOW|STEADY|FAST)\)", _pflat):
    steps[(MACHINE_OF[_one.group(1)], _one.group(2))] = {
        "input": _rid(_one.group(3)), "output": _rid(_one.group(4)),
        "count": int(_one.group(5)), "ticks": int(_one.group(6)),
        "pace": _one.group(7).lower()}
for _crop in SEEDED:
    steps[("milling", _crop + "_seeds")] = {
        "input": f"{FARM}:{_crop}", "output": f"{FARM}:{_crop}_seeds",
        "count": 1, "ticks": 140, "pace": "slow"}
if len(steps) < 20:
    fail(f"only {len(steps)} recipe(s) could be read out of ProcessRules, which "
         f"is not the roster and leaves every check below it checking nothing")

KINDS = ("milling", "pressing", "sawing", "mixing", "refining")
recipes = 0
paces = set()
written = set()
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
        # A recipe may name another module's items — that is what a join is —
        # but only if it carries a condition naming that module, so Age I is
        # still a jar that installs on its own. Without the condition the
        # recipe would be there naming an item that is not, which is an error
        # in the log and a machine that silently cannot make the thing.
        joined = set()
        for field in ("input", "result"):
            blob = json.dumps(recipe.get(field, {}))
            for namespace in re.findall(r'"([a-z_]+):', blob):
                if namespace not in ("minecraft", NS):
                    joined.add(namespace)
        named = {one.get("modid") for one in recipe.get("neoforge:conditions", [])
                 if one.get("type") == "neoforge:mod_loaded"}
        for namespace in sorted(joined - named):
            fail(f"{kind}/{name} names {namespace} and is not conditional on it, "
                 f"so Age I on its own has a recipe naming an item that is not "
                 f"there")
        for namespace in sorted(named - joined):
            fail(f"{kind}/{name} is conditional on {namespace} and does not name "
                 f"it, so the condition switches off a recipe for no reason")
        if kind != "refining":
            written.add((kind, name[:-5]))
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


# ---------------------------------------------------------------------------
# The engines, and the sum they may not get wrong
# ---------------------------------------------------------------------------

if "public static List<String> faultsInCeiling(" not in ENGINE_RULES:
    fail("nothing asks whether the ceiling an engine is measured against is a "
         "ceiling at all, and at one or more every check on an engine passes "
         "by being asked nothing, so the whole section becomes a comment")
if "public static List<String> makingEnergy(" not in ENGINE_RULES:
    fail("nothing asks whether an engine gets more out of a fuel than the fuel "
         "contains, which is a machine hall that makes energy — the same bug "
         "as a loop that gains torque, arrived at from the other end")
if "return left > 0 ? rules().source() : null;" not in strip_comments(
        read(SRC, "block/EngineBlock.java"), keep_strings=True):
    fail("an engine pushes on an empty tank, so it is free — and a free engine "
         "is every other source in the age with an extra recipe in front of it")
if "left -= Math.max(1, rules.burning() / TICKS_PER_SECOND);" not in \
        strip_comments(read(SRC, "block/EngineBlock.java"), keep_strings=True):
    fail("an engine spends fuel at a rate of its own rather than at the thirst "
         "the rules give it, so the sum the harness proves is about an engine "
         "nobody runs")

fuels_declared = re.findall(r'new Fuel\("([a-z:_]+)", ([\d_]+), "([a-z ]*)"\)',
                            ENGINE_RULES)
if len(fuels_declared) < 2:
    fail("there is no ladder of fuels, so there is nothing to refine and the "
         "refinery is a block that draws torque and does nothing")
burns = [int(one[1].replace("_", "")) for one in fuels_declared]
if burns != sorted(burns):
    fail(f"the fuel ladder runs backwards somewhere: {burns}")

for name in engine_ids:
    if not os.path.isfile(os.path.join(RES, f"data/{NS}/recipe/{name}.json")):
        fail(f"{name} has no recipe, so a rung of the engine ladder is a block "
             f"only the creative menu has")
    if f"block.{NS}.{name}" not in lang_keys:
        fail(f"{name} has no name a player reads")

for name in fuel_ids:
    if not os.path.isfile(os.path.join(
            RES, f"assets/{NS}/models/item/{name}.json")):
        fail(f"{name} has no model, so a fuel of ours is a purple cube")
    if f"item.{NS}.{name}" not in lang_keys:
        fail(f"{name} has no name a player reads")

# Every fuel this module makes has a way up to it, or it is an item nobody can
# ever hold.
refining = os.path.join(RES, f"data/{NS}/recipe/refining")
made_by_refining = set()
if not os.path.isdir(refining):
    fail("there is no refining folder, so the refinery reads no recipes at all")
else:
    for name in sorted(os.listdir(refining)):
        body = json.loads(read(refining, name))
        if body.get("type") != f"{NS}:refining":
            fail(f"refining/{name} is not a refining recipe")
        made_by_refining.add(str(body.get("result", {}).get("id", "")))
        if not body.get("input"):
            fail(f"refining/{name} is made out of nothing")
for name in fuel_ids:
    if f"{NS}:{name}" not in made_by_refining:
        fail(f"{name} is a fuel this module makes and nothing makes it, so it "
             f"is an item nobody can ever hold")

if refinery:
    if not os.path.isfile(os.path.join(RES, f"data/{NS}/recipe/{refinery}.json")):
        fail("the refinery has no recipe")
    if "extends MachineBlock" not in read(SRC, "block/RefineryBlock.java"):
        fail("the refinery is not one of the shared machines, so it has its own "
             "copy of the two slots and the grind loop to drift")

print(f"the engines        : {len(engine_ids)} engine(s) on a ladder of "
      f"{len(fuels_declared)} fuel(s), {len(fuel_ids)} of them ours and every "
      f"one with a way up to it, and a refinery to climb it with")

print(f"the hall's contents: {len(registered)} block(s), each modelled, named, drawn, "
      f"dropped and craftable; {textures} texture reference(s) all present; "
      f"{recipes} machine recipe(s) across {len(KINDS)} machine(s) and "
      f"{len(paces)} pace(s)")

spec_text = read(SPEC)


# ---------------------------------------------------------------------------
# The anchors, and the three ways a chunk loader leaks
# ---------------------------------------------------------------------------

ANCHOR_BLOCK = strip_comments(read(SRC, "block/AnchorBlock.java"),
                              keep_strings=True)
if not ANCHOR_BLOCK:
    fail("there is no anchor, so every machine in this age stops the moment "
         "nobody is looking at it and the farm a player built is a farm that "
         "only grows while they stand in it")

# A chunk loader leaks in three places and only three: the line stopped and it
# did not let go, the block was broken and it did not let go, and the world
# came back up holding tickets whose blocks are gone. Each is named as a whole
# statement, because "letGo(" survives the very edit that would break it.
_tick = squashed(body_of(ANCHOR_BLOCK, "protected void serverTick("))
if "} else if (!want && held >= 0) { letGo(server);" not in _tick:
    fail("an anchor does not let go when its line stops, so a chunk loader in "
         "this pack is a block you place and forget after all — which is the "
         "one thing it exists not to be")
_removed = squashed(body_of(ANCHOR_BLOCK, "public void setRemoved("))
if "letGo(server); } super.setRemoved();" not in _removed:
    fail("an anchor does not let go when it is broken, so the chunks it was "
         "holding stay awake with nothing left in the world asking for them")
_loaded = squashed(body_of(ANCHOR_BLOCK, "protected void loadAdditional("))
if "held = -1;" not in _loaded:
    fail("an anchor reads back what it was holding, so a ticket outlives the "
         "block that asked for it across a restart")
_entities = squashed(strip_comments(read(SRC, "registry/KineticBlockEntities.java"),
                                    keep_strings=True))
if "helper.removeAllTickets(pos));" not in _entities:
    fail("nothing drops the tickets of a world coming back up, so an anchor "
         "broken while the world was off holds its chunks for ever")
if "event.register(ANCHORS);" not in _entities:
    fail("the ticket controller is never registered, so an anchor is a load "
         "that draws torque and keeps nothing awake at all")

# Every width a block will hold has a blockstate, or the widths a player can
# set it to are widths the game draws as nothing.
for name, widest, _made in anchors:
    states = read(RES, f"assets/{NS}/blockstates/{name}.json")
    if not states:
        continue
    variants = json.loads(states).get("variants", {})
    for axis in ("x", "y", "z"):
        for radius in range(widest + 1):
            if f"axis={axis},radius={radius}" not in variants:
                fail(f"{name} has no model for a radius of {radius} on the "
                     f"{axis} axis, so a width a player can set it to is a "
                     f"width the game draws as a missing model")
    if len(variants) != 3 * (widest + 1):
        fail(f"{name} is drawn in {len(variants)} state(s) and has "
             f"{3 * (widest + 1)}, so one of them is a state nothing reaches "
             f"or a state that does not exist")

if "IntegerProperty.create(\"radius\", 0, AnchorRules.WIDEST)" not in ANCHOR_BLOCK:
    fail("an anchor's width is not read off the rules, so the widths the block "
         "offers and the widths the arithmetic prices can drift apart")
if "AnchorRules.comparator(anchor.holding(), state.getValue(RADIUS))" \
        not in squashed(ANCHOR_BLOCK):
    fail("an anchor works out its own comparator reading rather than asking "
         "the rules, so the number a redstone line reads is a number no "
         "harness has ever executed")
if "AnchorRules.loadAt(getBlockState().getValue(RADIUS))" not in squashed(ANCHOR_BLOCK):
    fail("an anchor draws a load of its own rather than the one its width "
         "costs, so widening it could be free")

print(f"the anchors        : {len(anchors)} of them over {WIDEST + 1} width(s), "
      f"each a load rather than a block you place and forget, letting go when "
      f"the line stops, when it is broken and when a world comes back up "
      f"holding what it left behind")


# ---------------------------------------------------------------------------
# What the machines do, and the one thing none of it may add up to
# ---------------------------------------------------------------------------

# Every recipe the rules declare is written, and every recipe written is
# declared. A roster held in one place and written from another drifts the
# moment somebody edits the easier of the two.
missing = sorted(f"{a}/{b}" for a, b in set(steps) - written)
extra = sorted(f"{a}/{b}" for a, b in written - set(steps))
if missing:
    fail(f"{len(missing)} recipe(s) the rules declare are not written: "
         + ", ".join(missing[:6]))
if extra:
    fail(f"{len(extra)} recipe(s) are written that the rules do not declare, so "
         f"they are numbers no harness has ever executed: " + ", ".join(extra[:6]))
for (kind, name), want in sorted(steps.items()):
    path = os.path.join(RES, f"data/{NS}/recipe/{kind}/{name}.json")
    if not os.path.isfile(path):
        continue
    got = json.loads(read(path))
    where = got.get("input", {})
    wrote = ("#" + where["tag"]) if "tag" in where else where.get("item", "")
    if wrote != want["input"]:
        fail(f"{kind}/{name} is written taking {wrote} and the rules say "
             f"{want['input']}")
    if got.get("result", {}).get("id") != want["output"] \
            or got.get("result", {}).get("count") != want["count"]:
        fail(f"{kind}/{name} is written making {got.get('result')} and the rules "
             f"say {want['count']} of {want['output']}")
    if got.get("ticks") != want["ticks"] or got.get("pace") != want["pace"]:
        fail(f"{kind}/{name} is written at {got.get('ticks')} ticks {got.get('pace')} "
             f"and the rules say {want['ticks']} {want['pace']}")

# Every id in the other module's namespace is an item that module really has.
# A join is a recipe naming somebody else's items, and a typo in one of them
# is a recipe that loads, sits in the book and can never be made.
farm_lang = os.path.join(ROOT, "elysium-agronomy", "src/main/resources/assets",
                         FARM, "lang/en_us.json")
if not os.path.isfile(farm_lang):
    fail(f"the module these join onto has no language file where this looks, so "
         f"every id they name is unchecked")
else:
    THE_FARM_HAS = {key.split(".", 2)[2] for key
                    in json.loads(read(farm_lang))
                    if key.startswith((f"item.{FARM}.", f"block.{FARM}."))}
    if len(THE_FARM_HAS) < 100:
        fail(f"only {len(THE_FARM_HAS)} name(s) could be read out of the farm's "
             f"language file, so the check below it is checking nothing")
    else:
        strangers = sorted({one for step in steps.values()
                            for one in (step["input"], step["output"])
                            if one.startswith(FARM + ":")
                            and one.split(":", 1)[1] not in THE_FARM_HAS})
        if strangers:
            fail(f"{len(strangers)} recipe(s) name something the farm does not "
                 f"have: " + ", ".join(strangers[:6]))

# And no mechanical route may yield more than the hand route it automates.
# The saw doing the kitchen's knife-work is the point of three of the jars
# this module takes in; a saw that gave more than the knife would be a reason
# never to hold the knife again.
cutting = os.path.join(ROOT, "elysium-agronomy", "src/main/resources/data",
                       FARM, "cutting")
if not os.path.isdir(cutting):
    fail("the kitchen's own cutting recipes are not where this looks, so no "
         "mechanical route was compared against the hand one it automates")
else:
    by_hand = {}
    for name in sorted(os.listdir(cutting)):
        body = json.loads(read(cutting, name))
        for yield_ in body.get("yields", []):
            by_hand[(body.get("input"), yield_.get("item"))] = yield_.get("count", 0)
    if not by_hand:
        fail("no hand recipes could be read out of the kitchen's cutting folder")
    automated = 0
    for (kind, name), step in sorted(steps.items()):
        hand = by_hand.get((step["input"], step["output"]))
        if hand is None:
            continue
        automated += 1
        if step["count"] > hand:
            fail(f"{kind}/{name} yields {step['count']} and the knife it "
                 f"automates yields {hand}, so a machine that needs no hand on "
                 f"it is also the better way of doing it")
    if automated < 5:
        fail(f"only {automated} mechanical route(s) were matched against a hand "
             f"one, and the join is meant to be the kitchen's whole knife")

# The game's own half of the graph, against the game's own jar.
#
# A loop rule that only looked at these recipes would be a rule that cannot
# see the loop that matters, because the way back is almost always a crafting
# recipe the game already ships. So every recipe in the game's jar whose
# result and one of whose ingredients are both nodes in this graph has to be
# declared — not the ones somebody thought of — and each declaration is
# checked against the recipe file, counting a shaped recipe's pattern rather
# than believing the number written down.

GAME_JAR = os.path.join(os.environ.get("ELYSIUM_MC_LIBS", "/tmp/mc"), "vanilla.jar")
routes = {(one[0], one[1]): (int(one[2]), one[3], int(one[4])) for one in
          re.findall(r'new Route\("([a-z_:]+)", "(#?[a-z_:]+)", (\d+), '
                     r'"([a-z_:]+)", (\d+)\)', _pflat)}
if not routes:
    fail("ProcessRules declares none of the game's own recipes, so the loop "
         "rule is looking at half a graph and the half it cannot see is the "
         "half the way back is always in")

if not os.path.isfile(GAME_JAR):
    print("the game's routes  : the game's jar is not here, so the loops that "
          "close through its own recipes were not swept for")
else:
    import zipfile

    def _named(blob, found):
        """Every item or tag an ingredient blob names, however it is nested."""
        if isinstance(blob, str):
            found.append(blob)
        elif isinstance(blob, list):
            for one in blob:
                _named(one, found)
        elif isinstance(blob, dict):
            if isinstance(blob.get("item"), str):
                found.append(blob["item"])
            elif isinstance(blob.get("tag"), str):
                found.append("#" + blob["tag"])
            else:
                for one in blob.values():
                    _named(one, found)

    def _takes(body, want):
        """How many of one ingredient a recipe uses."""
        kind = body.get("type", "")
        if kind.endswith(("smelting", "blasting", "smoking", "campfire_cooking")):
            return 1
        found = []
        if kind.endswith("crafting_shapeless"):
            _named(body.get("ingredients"), found)
            return sum(1 for one in found if one == want) or 1
        if kind.endswith("crafting_shaped"):
            symbols = []
            for symbol, blob in (body.get("key") or {}).items():
                here = []
                _named(blob, here)
                if want in here:
                    symbols.append(symbol)
            return sum(row.count(symbol) for row in body.get("pattern", [])
                       for symbol in symbols) or 1
        return 1

    nodes = {one for step in steps.values()
             for one in (step["input"], step["output"])}
    swept = 0
    closing = {}
    with zipfile.ZipFile(GAME_JAR) as jar:
        inside = [one for one in jar.namelist()
                  if one.startswith("data/minecraft/recipe/") and one.endswith(".json")]
        if len(inside) < 500:
            fail(f"only {len(inside)} recipe(s) are in the game's jar where this "
                 f"looks, so the sweep below is sweeping nothing")
        for one in inside:
            body = json.loads(jar.read(one))
            result = body.get("result")
            made = result.get("id") if isinstance(result, dict) else result
            if not isinstance(made, str) or made not in nodes:
                continue
            found = []
            for field in ("ingredients", "ingredient", "key", "base",
                          "addition", "template"):
                if field in body:
                    _named(body[field], found)
            count = result.get("count", 1) if isinstance(result, dict) else 1
            for taken in sorted(set(one for one in found if one in nodes)):
                swept += 1
                recipe = "minecraft:" + one.split("/")[-1][:-5]
                closing[(recipe, taken)] = (_takes(body, taken), made, count)
        # And the tag memberships, against the game's own tag files.
        for item, tag in re.findall(r'new Membership\("([a-z_:]+)", "#([a-z_:]+)"\)',
                                    _pflat):
            where = "data/{}/tags/item/{}.json".format(*tag.split(":", 1))
            try:
                values = json.loads(jar.read(where)).get("values", [])
            except KeyError:
                fail(f"{tag} is named as a tag and the game has no such tag, so "
                     f"the edge it stands for is an edge into nothing")
                continue
            if item not in [one if isinstance(one, str) else one.get("id")
                            for one in values]:
                fail(f"{item} is declared a member of #{tag} and the game's own "
                     f"tag does not list it, so a loop through that tag is a "
                     f"loop the rule cannot see")

    # And no machine here may beat the game's own recipe for the same thing
    # out of the same ingredient. A mixer that made blue ice out of one packed
    # ice, where the game asks for nine, is not a loop and is not a cheat —
    # it is simply the crafting recipe made pointless, which is the same
    # damage done politely.
    _most = re.search(r"MOST_A_MACHINE_ADDS = ([\d.]+)D;", PROCESS_RULES)
    if not _most:
        fail("nothing says how much a machine may beat the game's own recipe "
             "by, so a mixer could make blue ice out of one packed ice where "
             "the game asks for nine and nothing would say so")
    else:
        most = float(_most.group(1))
        if most < 1.0:
            fail(f"a machine may beat the game's own recipe by {most}, which "
                 f"is less than not beating it, so no machine here is worth "
                 f"the shaft behind it")
        beaten = []
        for (recipe, taken), (takes, made, count) in sorted(closing.items()):
            theirs = count / takes
            for (kind, name), step in sorted(steps.items()):
                if step["input"] != taken or step["output"] != made:
                    continue
                if step["count"] > theirs * most + 1.0E-6:
                    beaten.append(f"{kind}/{name} makes {step['count']} where "
                                  f"{recipe} makes {theirs:g}")
        if beaten:
            fail(f"{len(beaten)} machine recipe(s) beat the game's own recipe "
                 f"for the same thing out of the same ingredient by more than "
                 f"{most}x, which does not automate that recipe so much as "
                 f"make it pointless: " + ", ".join(beaten[:4]))

    undeclared = sorted(f"{a} ({b})" for a, b in set(closing) - set(routes))
    gone = sorted(f"{a} ({b})" for a, b in set(routes) - set(closing))
    if undeclared:
        fail(f"{len(undeclared)} recipe(s) the game ships close a loop with "
             f"these and are not declared, so the loop rule is looking at a "
             f"graph with holes in exactly the places loops live: "
             + ", ".join(undeclared[:6]))
    if gone:
        fail(f"{len(gone)} of the game's recipes are declared and the game does "
             f"not have them, or does not have them naming what this says: "
             + ", ".join(gone[:6]))
    for key, want in sorted(routes.items()):
        got = closing.get(key)
        if got and got != want:
            fail(f"{key[0]} is declared taking {want[0]} of {key[1]} and making "
                 f"{want[2]} of {want[1]}, and the game's own recipe takes "
                 f"{got[0]} and makes {got[2]} of {got[1]}")

print(f"the processing     : {len(steps)} recipe(s) across "
      f"{len(set(k for k, _ in steps))} machine(s), {automated} of them the "
      f"kitchen's knife done by machine and none yielding more than the hand "
      f"does, joined to {len(re.findall(r'new Route', PROCESS_RULES))} of the "
      f"game's own recipes - every one of them swept for rather than "
      f"thought of - with no loop anywhere that comes back with more than it "
      f"started with")


# ---------------------------------------------------------------------------
# The dynamo, and the one crossing this age makes
# ---------------------------------------------------------------------------

SEAM_RULES = strip_comments(read(SRC, "rules/SeamRules.java"), keep_strings=True)
if not SEAM_RULES:
    fail("there is no SeamRules, so the exchange rate between rotation and FE "
         "is whatever each age that needs one decides for itself, and two ages "
         "that disagree about it is a loop that makes energy")

for needed, why in (
        ("WORTH_OF_A_TORQUE_RPM",
         "nothing says what a torque-rpm is worth in FE, so the crossing "
         "cannot be checked in either direction"),
        ("faultsInCrossing(",
         "nothing asks whether the crossing loses; a dynamo paying what a "
         "motor charges is a perpetual motion machine made of two blocks"),
        ("bestRatio(",
         "nothing solves the gearing a dynamo wants, so the one puzzle the "
         "block sets has no answer anybody has checked")):
    if needed not in SEAM_RULES:
        fail(why)

# The crossing has to lose, and the validator works it out from the constants
# rather than trusting the method: a method and a validator that agree because
# one calls the other agree about nothing.
_fe = constant("DYNAMO_FE_PER_RPM")
_worth = re.search(r"WORTH_OF_A_TORQUE_RPM\s*=\s*([\d.]+)F", SEAM_RULES)
_draw = load_of("DYNAMO")
if not _fe or not _worth or not _draw:
    fail("the seam's three numbers are no longer where this file reads them")
else:
    home = (float(_fe.rstrip("F")) / _draw[0]) / float(_worth.group(1))
    if home >= 1.0:
        fail(f"rotation into FE and back into rotation comes home with {home}"
             f" of what it left with, which is a perpetual motion machine "
             f"arrived at through the seam rather than through a gearbox")
    if home > 0.5:
        fail(f"the crossing comes home with {home}, and Age I's electricity is "
             f"meant to be a curiosity rather than a power plant with an extra "
             f"step")

# The block asks the rules what it made. A block that multiplies the constant
# itself is a block the harness has never run — and naming the whole statement
# rather than the call is the fourteen-times-bitten lesson: "SeamRules.output("
# is still there in `if (false) x = SeamRules.output(...)`.
_tick = squashed(body_of(DYNAMO_BLOCK, "protected void serverTick("))
if "making = running() ? SeamRules.output(rpm()) : 0.0F;" not in _tick:
    fail("the dynamo works out its own output rather than asking the rules for "
         "it, so the number a player sees is a number no harness has ever "
         "executed")
if "DYNAMO_FE_PER_RPM" in DYNAMO_BLOCK:
    fail("the dynamo reads the FE constant directly, which is the second place "
         "that arithmetic lives and the one that will drift")
_receive = squashed(body_of(DYNAMO_BLOCK, "public boolean canReceive("))
if "return false;" not in _receive:
    fail("a dynamo can be charged from outside, so two of them facing each "
         "other are a battery that fills itself and the seam's arithmetic says "
         "nothing about the pair")
if "Capabilities.EnergyStorage.BLOCK, DYNAMO.get()" not in re.sub(
        r"\s+", " ", strip_comments(read(SRC, "registry/KineticBlockEntities.java"),
                                    keep_strings=True)):
    fail("the dynamo offers no energy capability, so it makes FE that nothing "
         "in any age can take out of it")

# And the spec states the contract, because Age II builds its alternator and
# its motor against it. Mentioning the dynamo is not enough — what a later age
# has to be able to read is the exchange rate itself, by name.
if "WORTH_OF_A_TORQUE_RPM" not in spec_text:
    fail("the spec does not name the constant that fixes what a torque-rpm is "
         "worth in FE, so a later age has nothing to read and will choose its "
         "own rate; two ages that disagree about it is a loop that makes "
         "energy")
if "half its free speed" not in spec_text:
    fail("the spec does not say what the best gearing for a dynamo is, so the "
         "one puzzle this block sets has an answer only this module's harness "
         "knows")

print(f"the seam           : one dynamo, bad on purpose - a round trip out "
      f"through it and back through Age II's motor comes home with "
      f"{home:.0%} of what it left with, and the gearing that gets the most "
      f"out of it is solved rather than guessed")


# ---------------------------------------------------------------------------
# 7. The seam
# ---------------------------------------------------------------------------

spec = spec_text
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
