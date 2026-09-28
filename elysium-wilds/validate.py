#!/usr/bin/env python3
"""
Validate Elysium Wilds against its sources.

Run from anywhere:   python3 elysium-wilds/validate.py

A biome can be declared, registered, translated, listed in every compass in the
pack, and never generate anywhere — because the game picks a biome by which of
their claims on a six-dimensional climate space is *nearest*, and a claim that
sits inside another claim declared before it ties everywhere it could win and
loses every tie. This pack shipped one. Nothing reported it, because nothing
was wrong: the file was valid and the id resolved.

So this module is the game's own nearest-box rule against the JDK alone, and
`check_worldgen.py` at the root runs it over every dimension file in the
repository.

  1. INDEPENDENCE. The library, the game, and nothing else.
  2. PURITY, and the harness run rather than read.
  3. THE METRIC, which has to be the game's and not nearly the game's.
  4. THE THEOREM, and the two numbers that are design decisions.
  5. THE SEAM, and the check at the root that uses all of it.
"""
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
SRC = os.path.join(WORK, "src/main/java/com/elysium/wilds")
PURE = os.path.join(SRC, "rules")
HARNESS = os.path.join(WORK, "tools/WildHarness.java")
VALE_HARNESS = os.path.join(WORK, "tools/ValeHarness.java")
VALE_MAIN = "com.elysium.wilds.rules.ValeHarness"
GRAMMAR_HARNESS = os.path.join(WORK, "tools/GrammarHarness.java")
GRAMMAR_MAIN = "com.elysium.wilds.rules.GrammarHarness"
OVERWORLD_HARNESS = os.path.join(WORK, "tools/OverworldHarness.java")
OVERWORLD_MAIN = "com.elysium.wilds.rules.OverworldHarness"
ROSTER = os.path.join(WORK, "tools/OverworldRoster.java")
ROSTER_MAIN = "com.elysium.wilds.rules.OverworldRoster"
VALE_ROSTER = os.path.join(WORK, "tools/ValeRoster.java")
VALE_ROSTER_MAIN = "com.elysium.wilds.rules.ValeRoster"
DATA_IN = os.path.join(WORK, "tools/data")
VANILLA_LIST = os.path.join(DATA_IN, "overworld-vanilla.tsv")
VANILLA_FEATURES = os.path.join(DATA_IN, "biome-features.tsv")
VANILLA_PRESETS = os.path.join(DATA_IN, "world_preset")
HARNESS_MAIN = "com.elysium.wilds.rules.WildHarness"
CHECKER = os.path.join(ROOT, "check_worldgen.py")

#: Two million checks over a million climates run in a few seconds.
HARNESS_SECONDS = 180
NS = "elysiumwilds"

problems = []

#: What tools/OverworldRoster.java printed, one line per biome. Empty when the
#: rules did not compile, and then every check that reads it says so rather
#: than throwing a NameError out of the validator.
roster_lines = []

#: And the Vale's, likewise.
vale_roster_lines = []


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
    print("This is not the elysium-wilds tree.")
    sys.exit(1)

print("=" * 70)
print("elysium-wilds — the game's own biome placement, so a biome that never "
      "generates is a build failure rather than an afternoon")
print("=" * 70)

# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------

FOREIGN = re.compile(r"(?:wwoo|ctov|choicetheorem|naturescompass|terralith|biomesoplenty)",
                     re.I)

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
    for used in set(re.findall(r"com\.elysium\.(\w+)", src)):
        if used not in ("wilds", "lib"):
            fail(f"{where} names com.elysium.{used}; what this module knows is the "
                 f"shape of the game's own biome placement, which every pack needs "
                 f"whether or not it has the rest of Elysium in it")

toml = read(RES, "META-INF/neoforge.mods.toml")
declared = set(re.findall(r'modId="([^"]+)"', toml))
allowed = {"${mod_id}", "neoforge", "minecraft", "elysiumlib"}
for mod in declared - allowed:
    fail(f"the module declares a dependency on {mod}; it needs the game and the library")
for needed in ("neoforge", "minecraft", "elysiumlib"):
    if needed not in declared:
        fail(f"the module does not declare {needed}")

print(f"independence       : {len(java_files)} source file(s), nothing it replaces "
      f"named, and not one dependency beyond the game and the library")

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
                 f"so every dimension file in the repository can be measured against "
                 f"them without starting a game")
    for used in re.findall(r"import\s+(com\.elysium\.[\w.]+);", without_package):
        fail(f"rules/{name} imports {used}; the rules are the JDK alone")

if not os.path.isfile(GRAMMAR_HARNESS):
    fail("there is no grammar harness, so whether every room of every building "
         "this pack generates can be walked to is an opinion")
if not os.path.isfile(VALE_HARNESS):
    fail("there is no Vale harness, so whether fifteen biomes partition the "
         "climate plane or merely nearly do is an opinion")
if not os.path.isfile(HARNESS):
    fail("there is no harness, so whether a claim inside another claim can generate "
         "is an opinion")
else:
    with tempfile.TemporaryDirectory() as tmp:
        staged = os.path.join(tmp, "src")
        os.makedirs(staged)
        sources = [os.path.join(PURE, n) for n in os.listdir(PURE) if n.endswith(".java")]
        for source in sources + [HARNESS, VALE_HARNESS, GRAMMAR_HARNESS,
                                 OVERWORLD_HARNESS, ROSTER, VALE_ROSTER]:
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
                     f"something in the rules can loop forever")
            if ran is None:
                pass
            elif ran.returncode != 0:
                fail("the wilds harness failed:\n" + harness_trouble(ran.stdout))
            else:
                print("the arithmetic     : " + ran.stdout.strip().splitlines()[-1])

            try:
                ran_vale = subprocess.run(
                    ["java", "-cp", os.path.join(tmp, "out"), VALE_MAIN],
                    capture_output=True, text=True, timeout=HARNESS_SECONDS)
            except subprocess.TimeoutExpired:
                ran_vale = None
                fail(f"the Vale harness did not finish within {HARNESS_SECONDS} "
                     f"seconds")
            if ran_vale is None:
                pass
            elif ran_vale.returncode != 0:
                fail("the Vale harness failed:\n" + harness_trouble(ran_vale.stdout))
            else:
                print("the Vale           : "
                      + ran_vale.stdout.strip().splitlines()[-1])

            try:
                ran_grammar = subprocess.run(
                    ["java", "-cp", os.path.join(tmp, "out"), GRAMMAR_MAIN],
                    capture_output=True, text=True, timeout=HARNESS_SECONDS)
            except subprocess.TimeoutExpired:
                ran_grammar = None
                fail(f"the grammar harness did not finish within "
                     f"{HARNESS_SECONDS} seconds, which for a file whose whole "
                     f"claim is that laying a building out terminates is the "
                     f"answer")
            if ran_grammar is None:
                pass
            elif ran_grammar.returncode != 0:
                fail("the grammar harness failed:\n"
                     + harness_trouble(ran_grammar.stdout))
            else:
                print("the buildings      : "
                      + ran_grammar.stdout.strip().splitlines()[-1])

            try:
                ran_over = subprocess.run(
                    ["java", "-cp", os.path.join(tmp, "out"), OVERWORLD_MAIN],
                    capture_output=True, text=True, timeout=HARNESS_SECONDS)
            except subprocess.TimeoutExpired:
                ran_over = None
                fail(f"the overworld harness did not finish within "
                     f"{HARNESS_SECONDS} seconds")
            if ran_over is None:
                pass
            elif ran_over.returncode != 0:
                fail("the overworld harness failed:\n"
                     + harness_trouble(ran_over.stdout))
            else:
                print("the overworld      : "
                      + ran_over.stdout.strip().splitlines()[-1])

            # And the roster itself, printed, so the files written in Python
            # can be checked against the arithmetic written in Java. Two
            # implementations of one piece of arithmetic drift, and the drift
            # is a biome painted one colour in the proof and another in the
            # file the game reads.
            try:
                ran_roster = subprocess.run(
                    ["java", "-cp", os.path.join(tmp, "out"), ROSTER_MAIN],
                    capture_output=True, text=True, timeout=HARNESS_SECONDS)
                roster_lines = [line for line in ran_roster.stdout.splitlines()
                                if line.strip() and "\t" in line]
                ran_vale_roster = subprocess.run(
                    ["java", "-cp", os.path.join(tmp, "out"), VALE_ROSTER_MAIN],
                    capture_output=True, text=True, timeout=HARNESS_SECONDS)
                vale_roster_lines = [line for line
                                     in ran_vale_roster.stdout.splitlines()
                                     if line.strip() and "\t" in line]
            except subprocess.TimeoutExpired:
                roster_lines = []
                fail("a roster printer did not finish")

rules = read(PURE, "WildRules.java")
plain = strip_comments(rules)

# ---------------------------------------------------------------------------
# 3. The metric
# ---------------------------------------------------------------------------
#
# This is the one part of the module that is not allowed to be a good idea of
# ours. It has to be what the game does, because its whole purpose is to
# predict the game.

gap = squashed(body_of(plain, "public double distance(double value)"))
if "double past = value - high;" not in gap or "double before = low - value;" not in gap:
    fail("how far a climate is from a span is no longer measured to both edges, so "
         "every biome on one side of its own box is at the wrong distance")
if "return past > 0.0D ? past : Math.max(before, 0.0D);" not in gap:
    fail("a climate inside a span no longer costs nothing; that single fact is why a "
         "wide claim beats a narrow one outright rather than merely often, and without "
         "it this file predicts a different game")

far = squashed(body_of(plain, "public double distance(double[] climate)"))
if "total += apart * apart;" not in far:
    fail("distances across the axes are no longer squared before they are added, so "
         "this is a different metric from the game's and the winner it names is a "
         "guess")
if "return total + offset * offset;" not in far:
    fail("offset is no longer the seventh axis; a biome carrying one would win ties it "
         "is meant to lose")

picking = squashed(body_of(plain, "public static Region nearest("))
if "if (apart < closest) {" not in picking:
    fail("ties no longer go to whichever biome was declared first, which is the game's "
         "own behaviour and the entire reason a swallowed claim never generates")

inside = squashed(body_of(plain, "public boolean inside(Region other)"))
if "if (mine.low() < theirs.low() || mine.high() > theirs.high()) {" not in inside:
    fail("whether one claim sits inside another is no longer checked on every axis, so "
         "the one test here that needs no sampling — and that found the bug this "
         "module exists for — is blind")

axes = re.search(r"public static final int AXES = (\d+);", plain)
if not axes or axes.group(1) != "6":
    fail("the climate space has stopped being six-dimensional, which the game's is")
listed = re.search(r'PARAMETERS = List\.of\(([^;]+)\);',
                   strip_comments(rules, keep_strings=True), re.S)
named = re.findall(r'"(\w+)"', listed.group(1)) if listed else []
if named != ["temperature", "humidity", "continentalness", "erosion", "depth",
             "weirdness"]:
    fail(f"the axes are {named}; they have to be the game's, in the game's order, or "
         f"a dimension file read into them lands on the wrong ones")

print("the metric         : the game's own nearest-box rule — six axes in the game's "
      "order, inside costing nothing, offset a seventh, ties to whoever was first")

# ---------------------------------------------------------------------------
# 4. The theorem, and the numbers that are decisions
# ---------------------------------------------------------------------------

swallowing = squashed(body_of(plain, "public static List<String> swallowed("))
if "for (int earlier = 0; earlier < later; earlier++) {" not in swallowing:
    fail("a claim is no longer compared only against claims declared before it; "
         "comparing it against later ones would report the arrangement that works")
if "if (!inner.biome().equals(outer.biome()) && inner.inside(outer)) {" not in swallowing:
    fail("a biome's own second claim is reported as swallowing it, or a claim inside "
         "an earlier one is not; splitting a claim into two bands either side of a "
         "third is the fix, and it must not read as the bug")

ceiling = squashed(body_of(plain, "public static List<String> lopsided("))
if "if (mostOfIt > NOTHING_IS_A_WORLD_ABOVE) {" not in ceiling:
    fail("a world may now claim any ceiling it likes, so a world that is one biome "
         "passes by saying so")
if "if (share > mostOfIt) {" not in ceiling:
    fail("a world no longer has to be the world it claimed to be, which is the only "
         "thing making the claim worth writing down")
if re.search(r"\bVANISHING_FRACTION\b", plain):
    fail("a floor on a biome's share is back. It was removed because a river failed "
         "it for being a river: how thin is too thin is a question about how far a "
         "player walks, which tooFar asks in blocks")

for name, kind in (("NOTHING_IS_A_WORLD_ABOVE", "double"),
                   ("FURTHEST_WORTH_WALKING", "double"),
                   ("WORLD_GRAIN", "double"),
                   ("EXPLORED_EARLY", "double"),
                   ("COMMON_ENOUGH_TO_IGNORE", "double")):
    if not re.search(r"public static final " + kind + r"\s+" + name + r"\s*=", plain):
        fail(f"{name} is gone; it is one of the handful of numbers in this module that "
             f"is a decision rather than the game's, and deleting it deletes the check "
             f"built on it")

walking = squashed(body_of(plain, "public static double walkTo(double share)"))
if "return WORLD_GRAIN / Math.sqrt(share);" not in walking:
    fail("how far a biome is no longer falls with the square root of its share; area "
         "is what scales with how much of the space you hold, not distance")
if "if (share <= 0.0D) {" not in walking:
    fail("a biome holding none of the world is given a finite distance, so a dead "
         "biome reads as a long walk")

spacing = squashed(body_of(plain, "public Spacing {"))
if "if (separation < 0 || separation >= spacing) {" not in spacing:
    fail("a separation as large as its spacing is accepted; the game rejects that "
         "outright and the structure never places at all")

print("the theorem        : a claim inside an earlier claim reported without sampling, "
      "and every world made to be the world it said it was")

# ---------------------------------------------------------------------------
# 5. The compass, which is where all of this becomes a server problem
# ---------------------------------------------------------------------------
#
# Finding the nearest instance of a biome is a synchronous scan on the server
# thread whose cost grows with the square of how far it looks. A compass with
# no bound on that is a compass that, held down, is a server that stutters --
# and what gets reported is "the server lags", so nobody connects it to the
# compass. The mod this replaces has exactly that shape.

search = squashed(body_of(plain, "public static double stepFor(double radius)"))
if "return Math.max(FINEST_STEP, across / Math.sqrt(SAMPLES_PER_SEARCH));" not in search:
    fail("how coarsely a search samples is no longer worked out from how far it "
         "reaches, so the two stop multiplying out to a constant and a search for "
         "something rare is a search that costs whatever it costs")

radius = squashed(body_of(plain, "public static double searchRadius(double share)"))
if "return Math.min(MOST_SEARCH_BLOCKS, walkTo(share) * SEARCH_MARGIN);" not in radius:
    fail("how far a search reaches is no longer capped, so one biome nobody thought "
         "about is a scan of the whole world on the server thread")

cost = squashed(body_of(plain, "public static List<String> tooExpensive()"))
if "if (worstPerSecond() > MOST_PER_SECOND) {" not in cost:
    fail("nothing checks what one player holding the compass down costs the server a "
         "second, which is the only number that decides whether this is safe")
if "if (mightMiss(coarsest) >= WORLD_GRAIN) {" not in cost:
    fail("nothing checks that the blurriest search still cannot step over a patch of "
         "biome; a compass that says 'no such place' about somewhere a player is "
         "standing in is worse than a slow one")

network = strip_comments(read(SRC, "network/WildNetwork.java"))
looking = squashed(body_of(network, "private static void look(ServerPlayer player,"))
if "int radius = (int) WildRules.MOST_SEARCH_BLOCKS;" not in looking:
    fail("how far the compass looks is not the stated ceiling, so whatever it is comes "
         "from somewhere this file cannot see")
if "int step = (int) Math.round(WildRules.stepFor(WildRules.MOST_SEARCH_BLOCKS));" \
        not in looking:
    fail("the grid the compass samples on is not worked out from the rules, so the "
         "cost the harness proved is not the cost the server pays")
if re.search(r"payload\.(radius|step|blocks)\(", squashed(network)):
    fail("the client tells the server how far to search; that is letting a client ask "
         "for arbitrary work and is the unbounded search with an extra step in it")

cooling = squashed(strip_comments(read(SRC, "item/BiomeCompassItem.java")))
if "player.getCooldowns().addCooldown(this, WildRules.COOLDOWN_TICKS);" not in cooling:
    fail("the compass has no cooldown, so the per-second bound the rules state is a "
         "bound on nothing")

found = squashed(strip_comments(read(SRC, "network/WildPayloads.java")))
if "public record Found(String place, boolean found, BlockPos where," not in found:
    fail("whether anything was found is no longer said separately from where; a "
         "compass that answers a failed search with a position sends somebody walking "
         "to nothing")

hooks = squashed(strip_comments(read(SRC, "client/WildsClientHooks.java")))
if "if (!answer.found()) {" not in hooks:
    fail("a failed search is not told to the player as a failure")

needle = strip_comments(read(SRC, "client/WildsClient.java"))
turning = squashed(body_of(needle, "public static void onSetup("))
if "if (bearing == null || level == null" not in turning:
    fail("the needle points somewhere even with nothing found, or across worlds; a "
         "needle that lies is worse than one that says nothing")
if "return wobble(holder, seed);" not in turning:
    fail("a compass with nothing to point at is not shown as having nothing to point "
         "at, so a player follows it")

screen = squashed(strip_comments(read(SRC, "client/CompassScreen.java")))
if "protected boolean filterable() { return true; }" not in screen:
    fail("the biome list cannot be typed into. A modded world knows well over a "
         "hundred biomes and the chooser pages seven at a time, so without a filter "
         "this is twenty pages and a player closes it and opens a wiki instead")
if "protected Component nothing()" not in screen:
    fail("a filter that matches nothing shows an empty panel and says nothing about "
         "why, which reads as the screen being broken rather than the word being wrong")

print("the compass        : one search of the stated size for anything, a cooldown "
      "under it, a list that can be typed into, and a needle that spins rather than "
      "lies")

# ---------------------------------------------------------------------------
# 6. The words, and the tree rebuilt
# ---------------------------------------------------------------------------

GENERATOR = os.path.join(WORK, "tools/gen_wilds.py")
lang_path = os.path.join(RES, "assets", NS, "lang/en_us.json")
import json
lang = json.loads(read(lang_path)) if os.path.isfile(lang_path) else {}
for key in ("itemGroup.elysiumwilds", "item.elysiumwilds.biome_compass",
            "elysiumwilds.compass.title", "elysiumwilds.compass.found",
            "elysiumwilds.compass.nowhere", "elysiumwilds.compass.idle"):
    if key not in lang:
        fail(f"{key} has no words, so it reads as its own translation key in the world")

if os.path.isfile(GENERATOR):
    with tempfile.TemporaryDirectory() as tmp:
        env = dict(os.environ, ELYSIUM_WILDS_OUT=tmp)
        built = subprocess.run([sys.executable, GENERATOR], capture_output=True,
                               text=True, env=env, cwd=WORK)
        if built.returncode != 0:
            # Its own complaints go to stdout, and a failure reported without
            # the reason for it is a failure somebody has to reproduce before
            # they can read it.
            fail("the generator does not run:\n"
                 + (built.stdout + built.stderr).strip()[:1200])
        else:
            drifted = []
            for kind in ("assets", "data"):
                here = os.path.join(RES, kind, NS)
                there = os.path.join(tmp, "src/main/resources", kind, NS)
                for base, _, names in os.walk(there):
                    for name in names:
                        fresh = os.path.join(base, name)
                        shipped = os.path.join(here, os.path.relpath(fresh, there))
                        if not os.path.isfile(shipped):
                            drifted.append(os.path.relpath(fresh, there) + " (missing)")
                        elif not samefile.same(fresh, shipped):
                            drifted.append(os.path.relpath(fresh, there))
            for name in drifted[:12]:
                fail(f"{name} is not what the generator produces, so it was edited by "
                     f"hand and the next person to run regen.sh will lose the edit")
            if len(drifted) > 12:
                fail(f"...and {len(drifted) - 12} more file(s) edited by hand")
            if not drifted:
                frames = len([n for n in os.listdir(
                    os.path.join(RES, "assets", NS, "models/item"))
                    if re.match(r"biome_compass_\d+\.json", n)])
                print(f"the words          : {frames} needle frame(s), a model, a recipe "
                      f"and the words, rebuilt and identical")
else:
    fail("there is no generator, so every sprite and model in this module is "
         "hand-written and nothing checks them")

# ---------------------------------------------------------------------------
# 7. The seam
# ---------------------------------------------------------------------------

if not os.path.isfile(CHECKER):
    fail("check_worldgen.py is gone, so these rules are a library nothing points at "
         "any of the repository's own worlds")
else:
    checker = read(CHECKER)
    if "WildRules" not in checker:
        fail("check_worldgen.py no longer uses these rules, so whatever it is measuring "
             "the worlds against is a second copy of the game's arithmetic")
    # Anchored to the start of a line, because "NOT_WORLDS = {" contains
    # "WORLDS = {" and a rule defeated by a three-letter prefix is a rule that
    # has never once failed. This project has been caught by that before.
    if not re.search(r"^WORLDS = \{", checker, re.M):
        fail("check_worldgen.py has no table of what each world is meant to look like, "
             "so how much of a world one biome may be is being guessed")
    if "is a multi-noise world with no row in check_worldgen.py" not in checker:
        fail("a world with no row in check_worldgen.py no longer fails; a world nothing "
             "has looked at is exactly the state this check exists to make impossible")

root_validate = read(ROOT, "validate.py")
if "check_worldgen.py" not in root_validate:
    fail("check_worldgen.py is not run by the root validator, so nothing measures the "
         "repository's worlds unless somebody remembers to")

for where, field in (("WildComponents", "TYPES"), ("WildItems", "ITEMS"),
                     ("WildItems", "TABS")):
    wanted = f"com.elysium.wilds.registry.{where}.{field}.register(modEventBus);"
    if wanted not in squashed(strip_comments(read(SRC, "ElysiumWilds.java"))):
        fail(f"{where}.{field} is declared and never put on the bus, so everything in "
             f"it is written, compiled, and absent from the game")

# ---------------------------------------------------------------------------
# The Vale
# ---------------------------------------------------------------------------
#
# The harness proves the claim is a partition. What is left for here is that
# the data the game will actually read says the same thing: fifteen biome
# files, a dimension that names all fifteen, and every rectangle in it exactly
# the one the rules work out. A dimension whose claims have drifted from the
# rules is a world the proof is about and the player is not in.

vale = strip_comments(read(PURE, "ValeRules.java"), True)
vale_warmths = re.search(r"WARMTHS = (\d+);", vale)
vale_wets = re.search(r"WETS = (\d+);", vale)
vale_listed = re.search(r"(?<![A-Z_])NAMES = List\.of\((.*?)\);", vale, re.S)
vale_names = re.findall(r'"([a-z_]+)"', vale_listed.group(1)) if vale_listed else []
vale_warmths = int(vale_warmths.group(1)) if vale_warmths else 0
vale_wets = int(vale_wets.group(1)) if vale_wets else 0

if not vale_names or vale_warmths < 2 or vale_wets < 2:
    fail(f"this validator read {len(vale_names)} name(s) and a {vale_warmths} by "
         f"{vale_wets} grid out of the Vale's rules, and a check against a short "
         f"list passes whatever is wrong with the rest")
elif len(vale_names) != vale_warmths * vale_wets:
    fail(f"the Vale crosses {vale_warmths} x {vale_wets} bands and has "
         f"{len(vale_names)} name(s); one biome is unnamed and the unnamed one is "
         f"the one that never generates")
else:
    missing = []
    for name in vale_names:
        where = os.path.join(RES, "data", NS, "worldgen/biome", "vale_" + name + ".json")
        if not os.path.isfile(where):
            missing.append(name)
        if f"biome.{NS}.vale_{name}" not in lang:
            missing.append(name + " (no name)")
    if missing:
        fail(f"{len(missing)} biome(s) of the Vale have no file or no name: "
             f"{missing[:4]}")

    world = os.path.join(RES, "data", NS, "dimension/vale.json")
    if not os.path.isfile(world):
        fail("the Vale has fifteen biomes and no dimension to put them in")
    else:
        claims = json.loads(read(world))["generator"]["biome_source"]["biomes"]
        claimed = [one["biome"] for one in claims]
        if len(claimed) != len(vale_names):
            fail(f"the Vale's dimension names {len(claimed)} biome(s) and the "
                 f"rules have {len(vale_names)}; the difference is biomes with a "
                 f"file, a name and no territory")
        elif len(set(claimed)) != len(claimed):
            fail("the Vale's dimension names one biome twice, so another one is "
                 "not named at all and never generates")
        else:
            # Every rectangle exactly the one the bands work out. Off by a
            # thousandth and two bands overlap or leave a gap, and climate in
            # the gap goes to whichever box happens to be nearest.
            def edge(at, bands):
                return round(-1.0 + 2.0 * at / bands, 4)

            wrong = []
            at = 0
            for warmth in range(vale_warmths):
                for wet in range(vale_wets):
                    want = {
                        "temperature": [edge(warmth, vale_warmths),
                                        edge(warmth + 1, vale_warmths)],
                        "humidity": [edge(wet, vale_wets), edge(wet + 1, vale_wets)],
                    }
                    got = claims[at]["parameters"]
                    if got.get("temperature") != want["temperature"] \
                            or got.get("humidity") != want["humidity"]:
                        wrong.append(f"{claimed[at]} claims {got.get('temperature')} "
                                     f"x {got.get('humidity')} and its band is "
                                     f"{want['temperature']} x {want['humidity']}")
                    at += 1
            if wrong:
                fail(f"{len(wrong)} of the Vale's claims are not the bands the "
                     f"rules work out: {wrong[:2]}")

    # Nobody may want fewer than they will take. The game does not refuse a
    # spawner whose minimum is above its maximum -- it logs a line, drops the
    # spawner and carries on, so the biome is simply empty and nothing in the
    # world says why. This shipped that way for three biomes of the Vale.
    backwards = []
    for name in vale_names:
        where = os.path.join(RES, "data", NS, "worldgen/biome",
                             "vale_" + name + ".json")
        if not os.path.isfile(where):
            continue
        for kind, listed in json.loads(read(where)).get("spawners", {}).items():
            for one in listed:
                if one.get("minCount", 0) > one.get("maxCount", 0):
                    backwards.append(f"vale_{name} wants at least "
                                     f"{one.get('minCount')} {one.get('type')} and "
                                     f"at most {one.get('maxCount')}")
                if one.get("weight", 0) < 1:
                    backwards.append(f"vale_{name} spawns {one.get('type')} at "
                                     f"weight {one.get('weight')}, which is never")
    if backwards:
        fail(f"{len(backwards)} spawner(s) the game will log and drop, leaving the "
             f"biome empty and nothing saying why: {backwards[:3]}")

    # And no two biome files identical: two identical biomes are one biome
    # with two names and nobody standing in either can tell.
    seen = {}
    for name in vale_names:
        where = os.path.join(RES, "data", NS, "worldgen/biome",
                             "vale_" + name + ".json")
        if not os.path.isfile(where):
            continue
        body = json.dumps(json.loads(read(where)), sort_keys=True)
        if body in seen:
            fail(f"vale_{name} and vale_{seen[body]} are the same file to the "
                 f"byte, so they are one biome with two names")
            break
        seen[body] = name

    kind = os.path.join(RES, "data", NS, "dimension_type/vale.json")
    if not os.path.isfile(kind):
        fail("the Vale has a dimension and no type, so the game has nowhere to "
             "read its height or its light from")
    else:
        # A dimension type whose numbers disagree with each other is a world
        # that loads and then behaves like a different one: a logical height
        # above the real height is chunks the game will generate into and then
        # refuse to let anything stand in.
        kind = json.loads(read(kind))
        if kind.get("logical_height", 0) > kind.get("height", 0):
            fail(f"the Vale is {kind.get('height')} blocks tall and says its "
                 f"logical height is {kind.get('logical_height')}; the game will "
                 f"generate into the difference and let nothing stand there")
        if kind.get("min_y", 0) + kind.get("height", 0) > 2032:
            fail("the Vale's floor plus its height is past the top of a world")
        if kind.get("height", 0) % 16:
            fail(f"the Vale is {kind.get('height')} blocks tall and a world is "
                 f"made of chunks sixteen deep")

    print(f"the Vale           : {len(vale_names)} biome(s) on a {vale_warmths} by "
          f"{vale_wets} grid, every rectangle exactly the band the rules work out, "
          f"no two of the files alike, and a dimension and a type to put them in")

# ---------------------------------------------------------------------------
# The structures
# ---------------------------------------------------------------------------
#
# The harness proves the buildings. What is left for here is that the world
# has somewhere to put them: a structure file and a placement apiece, a
# spacing the game will accept, and a grammar name that is a grammar — a
# structure naming one that is not simply never generates, and nothing but
# this says so.

grammar = strip_comments(read(PURE, "GrammarRules.java"), True)
grammar_names = [name for name, _start in re.findall(
    r'new Grammar\("([a-z_]+)",\s*"([a-z_]+)"', grammar)]
if len(grammar_names) != len(re.findall(r"new Grammar\(", grammar)):
    fail(f"this validator read {len(grammar_names)} of "
         f"{len(re.findall(r'new Grammar', grammar))} buildings out of the rules")
if len(grammar_names) < 4:
    fail(f"this validator found {len(grammar_names)} kind(s) of building, and a "
         f"check against a handful passes whatever is wrong with the rest")

missing = []
for name in grammar_names:
    for where in (f"data/{NS}/worldgen/structure/{name}.json",
                  f"data/{NS}/worldgen/structure_set/{name}.json"):
        if not os.path.isfile(os.path.join(RES, where)):
            missing.append(f"{name} has no {where.rsplit('/', 2)[-2]}")
if missing:
    fail(f"{len(missing)} of {len(grammar_names) * 2} things a building needs are "
         f"missing: " + ", ".join(missing[:6]))

salts = {}
for name in grammar_names:
    body = read(RES, "data", NS, f"worldgen/structure/{name}.json")
    if body:
        one = json.loads(body)
        if one.get("grammar") != name:
            fail(f"the structure {name} is laid out by the grammar "
                 f"{one.get('grammar')}, and a structure naming a grammar that "
                 f"is not one never generates and nothing says so")
        if one.get("type") != f"{NS}:grammar":
            fail(f"the structure {name} is a {one.get('type')} rather than one of "
                 f"ours")
        if not str(one.get("biomes", "")).startswith("#") and not one.get("biomes"):
            fail(f"the structure {name} belongs in no biome, so it is a building "
                 f"the world has nowhere to put")
    placed = read(RES, "data", NS, f"worldgen/structure_set/{name}.json")
    if placed:
        where = json.loads(placed).get("placement", {})
        spacing = where.get("spacing", 0)
        separation = where.get("separation", 0)
        if separation >= spacing:
            fail(f"{name} is placed every {spacing} chunk(s) with {separation} "
                 f"guaranteed between them; the game refuses a separation that is "
                 f"not smaller than the spacing and the structure never appears")
        if spacing > 128:
            fail(f"{name} is placed every {spacing} chunks, which is {spacing * 16} "
                 f"blocks and further than anybody walks")
        salt = where.get("salt")
        if salt in salts:
            fail(f"{name} and {salts[salt]} are placed with the same salt, so "
                 f"every one of them generates in the same chunk as the other and "
                 f"one of the two is always the one that loses")
        salts[salt] = name

layout_src = squashed(strip_comments(read(PURE, "GrammarRules.java"), True))
if "for (int tries = 0; tries < TRIES && placed.size() < wanted; tries++) {" \
        not in layout_src:
    fail("laying a building out is not bounded by a count of tries, so a grammar "
         "that cannot place what it wants is a chunk that never finishes "
         "generating — which is a server that stops rather than a building that "
         "looks wrong")

structure_src = squashed(strip_comments(
    read(SRC, "structure/GrammarStructure.java"), True))
if "GrammarRules.layOut(grammar, seed)" not in structure_src:
    fail("the structure in the world is not laid out by the rules the harness "
         "proved, so every property above is about a building that does not ship")
if "if (GrammarRules.grammar(grammar) == null) { return Optional.empty(); }" \
        not in structure_src:
    fail("a structure naming a grammar that does not exist is not refused, so it "
         "throws out of world generation rather than simply not appearing")

piece_src = squashed(strip_comments(read(SRC, "structure/RoomPiece.java"), True))
if "if (!kind.opens(way)) { continue; }" not in piece_src:
    fail("the gaps a room is built with are not the doors the rules say it has, "
         "so the building a player walks through is not the building the harness "
         "walked through")

print(f"the structures     : {len(grammar_names)} kind(s) of building, every one "
      f"with a placement the game will accept, a salt of its own and a grammar "
      f"that exists")


# ---------------------------------------------------------------------------
# The Vale, likewise: the files against the rules that were proved
# ---------------------------------------------------------------------------

# A list that came back empty is a check that reads nothing and passes, which
# is the failure this whole file exists to make loud.
if len(vale_roster_lines) != 15:
    fail(f"the Vale's rules printed {len(vale_roster_lines)} biome(s) and the "
         f"Vale has fifteen, so the comparison below is comparing nothing")

vale_drift = []
for line in vale_roster_lines:
    parts = line.split("\t")
    path = os.path.join(RES, "data", NS, "worldgen/biome", parts[0] + ".json")
    if not os.path.isfile(path):
        vale_drift.append(f"{parts[0]} is in the rules and has no file")
        continue
    biome = json.loads(open(path, encoding="utf-8").read())
    effects = biome.get("effects", {})
    for key, want in (("sky_color", int(parts[1])), ("fog_color", int(parts[2])),
                      ("water_color", int(parts[3])),
                      ("water_fog_color", int(parts[3])),
                      ("grass_color", int(parts[4])),
                      ("foliage_color", int(parts[5]))):
        if effects.get(key) != want:
            vale_drift.append(f"{parts[0]}'s {key} is {effects.get(key)} in the "
                              f"file and {want} in the rules")
    if abs(biome.get("temperature", 0.0) - float(parts[6])) > 1e-6:
        vale_drift.append(f"{parts[0]} is {biome.get('temperature')} warm in the "
                          f"file and {parts[6]} in the rules")
    if biome.get("has_precipitation") is not (parts[8] == "true"):
        vale_drift.append(f"{parts[0]} rains in the file and not in the rules")
if vale_drift:
    fail(f"{len(vale_drift)} thing(s) in the Vale's files are not what "
         f"ValeRules works out, and ValeHarness proves its properties about the "
         f"rules rather than the files, first three:\n    "
         + "\n    ".join(vale_drift[:3]))

# ---------------------------------------------------------------------------
# The overworld: what was written against what the rules say
# ---------------------------------------------------------------------------
#
# Everything here is about the gap between two implementations. The proof is
# in Java and the files are written by Python, and neither one can see the
# other. A biome painted one colour in the proof and another in the file the
# game reads is a real bug that no amount of arithmetic finds, because the
# arithmetic is right.

over_src = squashed(strip_comments(read(PURE, "OverworldRules.java"), True))

if "for (int axis = 0; axis < WildRules.AXES; axis++) {" not in over_src:
    fail("the overworld's claims are not compared axis by axis against the "
         "number of axes the space has, so a claim with five could go out and "
         "the game would read the sixth as whatever was next in memory")

if 'if (low == high && !bothPoints) {' not in over_src:
    fail("two claims that meet at a face are not told apart from two that share "
         "a point axis; depth is a point on every surface entry the game ships, "
         "so reading that as a face touch makes every pair of surface biomes "
         "disjoint and the overlap check answers no forever")

# The dumps. Not ours, not editable, and the thing everything else rests on.
vanilla_rows = [line for line in read(VANILLA_LIST).splitlines()
                if line.strip() and not line.startswith("#")]
if len(vanilla_rows) < 7000:
    fail(f"the dump of vanilla's overworld has {len(vanilla_rows)} entries and "
         f"the game builds 7,593; the difference is vanilla biomes that would "
         f"stop generating and nothing would say which")
# The dump counts itself in its own header, and the header is written by the
# dumper. A row taken out by hand leaves the header saying what the game
# actually builds, and the two no longer agree — which is the only way to
# notice one missing entry among seven and a half thousand.
stated = re.search(r"# (\d+) entries across (\d+) biomes\.", read(VANILLA_LIST))
if not stated:
    fail("the dump of vanilla's overworld no longer says how much of it there "
         "should be, so a row taken out of it is a row nothing can miss")
else:
    if int(stated.group(1)) != len(vanilla_rows):
        fail(f"the dump of vanilla's overworld says it holds "
             f"{stated.group(1)} entries and it holds {len(vanilla_rows)}; a "
             f"row has been added or taken out by hand and vanilla's overworld "
             f"is not vanilla's overworld any more")
    if int(stated.group(2)) != len({row.split("\t")[0] for row in vanilla_rows}):
        fail(f"the dump says it covers {stated.group(2)} biomes and it covers "
             f"{len({row.split(chr(9))[0] for row in vanilla_rows})}")

bad_width = [row for row in vanilla_rows if len(row.split("\t")) != 14]
if bad_width:
    fail(f"{len(bad_width)} row(s) of the overworld dump are not 14 columns, so "
         f"an entry would be written with an axis missing")

feature_rows = [line for line in read(VANILLA_FEATURES).splitlines()
                if line.strip() and not line.startswith("#")]
vanilla_decoration = {}
for row in feature_rows:
    parts = row.split("\t")
    if len(parts) != 3:
        fail("a row of the decoration dump is not three columns")
        continue
    vanilla_decoration.setdefault(parts[0], {}).setdefault(int(parts[1]), []) \
        .append(parts[2])
stated_features = re.search(r"# (\d+) rows across (\d+) biomes\.",
                            read(VANILLA_FEATURES))
if not stated_features:
    fail("the dump of vanilla's decoration no longer says how much of it there "
         "should be")
elif int(stated_features.group(1)) != len(feature_rows):
    fail(f"the dump of vanilla's decoration says it holds "
         f"{stated_features.group(1)} rows and it holds {len(feature_rows)}")

if len(vanilla_decoration) < 50:
    fail(f"the dump of vanilla's decoration has {len(vanilla_decoration)} "
         f"biome(s) in it and the game ships sixty-two; a biome of ours would "
         f"decorate like nothing")

# What the Java says the roster is.
if len(roster_lines) != 140:
    fail(f"the overworld's rules printed {len(roster_lines)} biome(s) and the "
         f"roster is a hundred and forty, so every comparison below is "
         f"comparing nothing")

roster = {}
for line in roster_lines:
    parts = line.split("\t")
    if len(parts) < 11:
        fail("a line of the printed roster is too short to be a biome")
        continue
    roster[parts[0]] = parts
if roster_lines and len(roster) != len(roster_lines):
    fail("the printed roster names a biome twice")

over_dir = os.path.join(RES, "data", NS, "worldgen/biome")
written = {name[:-5] for name in os.listdir(over_dir) if name.endswith(".json")}
vale_written = {name for name in written if name.startswith("vale_")}
ours_written = written - vale_written

if roster and set(roster) != ours_written:
    missing = sorted(set(roster) - ours_written)
    extra = sorted(ours_written - set(roster))
    fail(f"the roster and the biome files disagree: {len(missing)} in the rules "
         f"with no file ({missing[:3]}) and {len(extra)} files the rules do not "
         f"name ({extra[:3]}) — a biome with no file is one the game reports as "
         f"unbound, and a file the rules do not name is one nothing proved")

AXES = ["temperature", "humidity", "continentalness", "erosion", "depth",
        "weirdness"]

drifted = []
unnamed = []
for name, parts in sorted(roster.items()):
    path = os.path.join(over_dir, name + ".json")
    if not os.path.isfile(path):
        continue
    biome = json.loads(open(path, encoding="utf-8").read())
    effects = biome.get("effects", {})
    want = {
        "sky_color": int(parts[2]), "fog_color": int(parts[3]),
        "water_color": int(parts[4]), "water_fog_color": int(parts[4]),
        "grass_color": int(parts[5]), "foliage_color": int(parts[6]),
    }
    for key, value in want.items():
        if effects.get(key) != value:
            drifted.append(f"{name}'s {key} is {effects.get(key)} in the file "
                           f"and {value} in the rules")
    if abs(biome.get("temperature", 0.0) - float(parts[7])) > 1e-6:
        drifted.append(f"{name} is {biome.get('temperature')} warm in the file "
                       f"and {parts[7]} in the rules")
    if abs(biome.get("downfall", 0.0) - float(parts[8])) > 1e-6:
        drifted.append(f"{name} gets {biome.get('downfall')} rain in the file "
                       f"and {parts[8]} in the rules")
    if biome.get("has_precipitation") is not (parts[9] == "true"):
        drifted.append(f"{name} rains in the file and not in the rules, or the "
                       f"other way about")

    # What it adds of its own, in the step the rules put it in.
    extras = [one for one in parts[10].split(",") if one]
    steps = biome.get("features", [])
    if len(steps) != 11:
        drifted.append(f"{name} has {len(steps)} generation step(s) and a biome "
                       f"file has eleven")
    else:
        for feature in extras:
            step = 2 if feature.startswith("boulder_") else 9
            if f"{NS}:{feature}" not in steps[step]:
                drifted.append(f"{name} is meant to add {feature} in step {step} "
                               f"and the file does not")
        listed = [one for step in steps for one in step
                  if one.startswith(NS + ":")]
        if listed != [f"{NS}:{one}" for one in extras]:
            drifted.append(f"{name} adds {listed} and the rules say {extras}")

    # And it decorates like the vanilla biome the rules name.
    model = "minecraft:" + parts[1]
    if model not in vanilla_decoration:
        drifted.append(f"{name} decorates like {model}, which the dump does not "
                       f"have")
    elif len(steps) == 11:
        for step, wanted in vanilla_decoration[model].items():
            got = [one for one in steps[step] if not one.startswith(NS + ":")]
            if got != wanted:
                drifted.append(f"{name} copies {model}'s step {step} as {got[:2]} "
                               f"and the game's own is {wanted[:2]}")
                break

    # Spawners: the two numbers the right way round, every time.
    for kind, rows in biome.get("spawners", {}).items():
        for row in rows:
            if row.get("minCount", 0) > row.get("maxCount", 0):
                drifted.append(f"{name} wants at least {row.get('minCount')} "
                               f"{row.get('type')} and at most "
                               f"{row.get('maxCount')}; the game drops the "
                               f"spawner, logs a line, and the biome is empty")

if drifted:
    fail(f"{len(drifted)} thing(s) written are not what the rules work out, "
         f"first five:\n    " + "\n    ".join(drifted[:5]))

# Every one of them has a name a player reads.
lang = json.loads(read(RES, "assets", NS, "lang/en_us.json")) \
    if os.path.isfile(os.path.join(RES, "assets", NS, "lang/en_us.json")) else {}
for name in sorted(roster):
    if f"biome.{NS}.{name}" not in lang:
        unnamed.append(name)
if unnamed:
    fail(f"{len(unnamed)} biome(s) of ours have no name a player reads, so the "
         f"compass lists them by their id: {unnamed[:5]}")

# Our own features, both halves of each.
our_features = sorted(set(re.findall(r'"([a-z_]+)"', re.search(
    r"(?<![A-Z_])OURS\s*=\s*List\.of\((.*?)\);", over_src, re.S).group(1)))) \
    if re.search(r"(?<![A-Z_])OURS\s*=\s*List\.of\((.*?)\);", over_src, re.S) \
    else []
if not our_features:
    fail("the rules no longer say which features are ours, so a biome of ours "
         "would be a vanilla biome under a name of ours")
for feature in our_features:
    for folder in ("configured_feature", "placed_feature"):
        path = os.path.join(RES, "data", NS, "worldgen", folder, feature + ".json")
        if not os.path.isfile(path):
            fail(f"{feature} has no {folder} file, so every biome naming it "
                 f"comes back unbound and none of them generate")
    placed = os.path.join(RES, "data", NS, "worldgen/placed_feature",
                          feature + ".json")
    if os.path.isfile(placed):
        body = json.loads(open(placed, encoding="utf-8").read())
        if body.get("feature") != f"{NS}:{feature}":
            fail(f"{feature}'s placed half points at {body.get('feature')}")
        if not body.get("placement"):
            fail(f"{feature} is placed with no placement at all, which is once "
                 f"at the corner of every chunk")

# ---------------------------------------------------------------------------
# The three presets, and what is left of vanilla
# ---------------------------------------------------------------------------

preset_dir = os.path.join(RES, "data/minecraft/worldgen/world_preset")
originals = sorted(name for name in os.listdir(VANILLA_PRESETS)
                   if name.endswith(".json"))
if not os.path.isdir(preset_dir):
    fail("no world preset is written at all, so not one of our biomes is in "
         "anybody's overworld")
    presets = []
else:
    presets = sorted(name for name in os.listdir(preset_dir)
                     if name.endswith(".json"))
if presets != originals:
    fail(f"we write {presets} and vanilla's three that use the overworld's list "
         f"are {originals}; the ones missing send a player to an overworld with "
         f"none of our biomes in it")

vanilla_claims = []
for row in vanilla_rows:
    parts = row.split("\t")
    if len(parts) != 14:
        continue
    numbers = [int(part) / 10000.0 for part in parts[1:]]
    vanilla_claims.append((parts[0], numbers))

for name in presets:
    preset = json.loads(read(preset_dir, name))
    original = json.loads(read(VANILLA_PRESETS, name))

    # Everything but the overworld goes through untouched.
    for dimension, body in original["dimensions"].items():
        if dimension == "minecraft:overworld":
            continue
        if preset["dimensions"].get(dimension) != body:
            fail(f"{name} rewrites {dimension} on the way past, and nobody asked "
                 f"us to touch it")
    ours_dim = preset["dimensions"].get("minecraft:overworld", {})
    theirs = original["dimensions"]["minecraft:overworld"]
    if ours_dim.get("type") != theirs.get("type"):
        fail(f"{name}'s overworld is not the overworld any more")
    if ours_dim.get("generator", {}).get("settings") \
            != theirs.get("generator", {}).get("settings"):
        fail(f"{name}'s overworld generates with different noise settings, which "
             f"is a different world shape entirely")

    source = ours_dim.get("generator", {}).get("biome_source", {})
    if source.get("type") != "minecraft:multi_noise":
        fail(f"{name}'s overworld does not use the multi-noise source, so the "
             f"climate this module is entirely about is not consulted")
        continue
    if "preset" in source:
        fail(f"{name} still names a preset, which takes the name of a list the "
             f"game already has and cannot be added to — not one of our biomes "
             f"would generate")
        continue
    entries = source.get("biomes", [])
    ours_count = len(roster) * len(re.findall(
        r"Span\(", re.search(r"STRANGENESS\s*=\s*List\.of\((.*?)\);",
                             over_src, re.S).group(1))) if roster else 0
    # A vanilla claim that lies wholly inside one of our crags' boxes is a tie
    # everywhere it is, and the game settles ties by the order its tree happens
    # to hold, not the order of the list: the crags lost every one and eight
    # of them never generated (the live world scan, 27 September 2026). The
    # generator drops those claims, so the count is the rules plus the dump
    # less the shadowed ones, and none of them may be in the file.
    def _within(inner, outer):
        for axis in ("temperature", "humidity", "continentalness", "erosion", "depth", "weirdness"):
            if inner[axis][0] < outer[axis][0] - 1e-9 or inner[axis][1] > outer[axis][1] + 1e-9:
                return False
        return inner["offset"] >= outer["offset"] - 1e-9
    def _boxed(numbers):
        return {"temperature": numbers[0:2], "humidity": numbers[2:4], "continentalness": numbers[4:6],
                "erosion": numbers[6:8], "depth": numbers[8:10], "weirdness": numbers[10:12], "offset": numbers[12]}
    # Every box of ours presses the claim, not the crags alone: the hoar hill's wide box
    # held the game's cold claims whole, lost every tie and never generated either.
    our_boxes = [one["parameters"] for one in entries[:ours_count]]
    shadowed = sum(1 for _claim_name, numbers in vanilla_claims if any(_within(_boxed(numbers), box) for box in our_boxes))
    if any(_within(one["parameters"], box) for one in entries[ours_count:] for box in our_boxes):
        fail(f"{name} still lists a vanilla claim wholly inside a crag's box or another of ours, which "
             f"the game may hand every tie to, so that biome never generates")
        continue
    if len(entries) != ours_count + len(vanilla_claims) - shadowed:
        fail(f"{name} lists {len(entries)} entries and the rules plus the dump less the "
             f"{shadowed} claim(s) our boxes shadow come to {ours_count + len(vanilla_claims) - shadowed}")
        continue

    # Ours first. That is not a style choice: ties go to whoever was declared
    # first, and declared second a box inside vanilla's wins nothing at all.
    head = entries[:ours_count]
    tail = entries[ours_count:]
    stray = [one for one in head if not one["biome"].startswith(NS + ":")]
    if stray:
        fail(f"{name} has {len(stray)} entry that is not ours in the half that "
             f"has to be ours")
    late = [one for one in tail if one["biome"].startswith(NS + ":")]
    if late:
        fail(f"{name} declares {len(late)} of our biomes after vanilla's, and a "
             f"box inside a box declared first wins nothing anywhere, ever")

    # Vanilla's half, entry for entry, in order, exactly as the game builds it.
    off = []
    carried = [(biome, numbers) for biome, numbers in vanilla_claims
               if not any(_within(_boxed(numbers), box) for box in our_boxes)]
    for at, (biome, numbers) in enumerate(carried):
        got = tail[at]
        if got["biome"] != biome:
            off.append(f"entry {at} is {got['biome']} and the game's own is "
                       f"{biome}")
            break
        parameters = got["parameters"]
        for axis_at, axis in enumerate(AXES):
            low, high = numbers[axis_at * 2], numbers[axis_at * 2 + 1]
            if [round(low, 4), round(high, 4)] != parameters.get(axis):
                off.append(f"{biome}'s {axis} is {parameters.get(axis)} and the "
                           f"game's own is {[low, high]}")
                break
        if off:
            break
    if off:
        fail(f"{name} does not carry vanilla's own list through untouched: "
             + off[0])

# And the thing the whole design rests on: vanilla keeps every one of its
# biomes. Entry by entry, because "we only took half" is an argument and this
# is a fact.
if presets:
    ours_boxes = []
    for name, parts in roster.items():
        numbers = [float(one) for one in parts[11:]]
        for at in range(0, len(numbers), 12):
            ours_boxes.append(numbers[at:at + 12])

    def swallowed(numbers):
        """Whether one of vanilla's entries sits wholly inside one of ours."""
        for box in ours_boxes:
            inside = True
            for axis in range(6):
                if numbers[axis * 2] < box[axis * 2] \
                        or numbers[axis * 2 + 1] > box[axis * 2 + 1]:
                    inside = False
                    break
            if inside:
                return True
        return False

    kept = set()
    lost = {}
    for biome, numbers in vanilla_claims:
        if swallowed(numbers):
            lost[biome] = lost.get(biome, 0) + 1
        else:
            kept.add(biome)
    erased = sorted(set(lost) - kept)
    if erased:
        fail(f"{len(erased)} vanilla biome(s) have every entry inside a claim of "
             f"ours, so they stop generating anywhere in the world and nothing "
             f"reports it: {erased[:5]}")
    if not lost:
        fail("not one of vanilla's entries falls inside a claim of ours, which "
             "means our 140 biomes are taking their ground from somewhere this "
             "check cannot see — or from nowhere, and none of them generate")

    print(f"the overworld      : {len(roster)} biome(s) of ours in "
          f"{len(presets)} preset(s), declared ahead of vanilla's "
          f"{len(vanilla_claims)}, every one of vanilla's {len(kept)} biomes "
          f"still with ground of its own, and every colour, spawn and feature "
          f"in the files the arithmetic that was proved")

# The reference world every client stage starts from (tools/livetest/stages/
# reference/level.dat, POLISH.md P4) carries the overworld's biome list inline,
# the way the game saves a world made from a preset. When the preset moves on
# and the saved world does not, every fresh test world generates a world the
# pack no longer ships and nothing says so: that is how the hoar hill went
# unseen (27 Sep). So the list in the file is held to the list in the preset.
import nbtlite  # noqa: E402
_reference = os.path.join(ROOT, "tools/livetest/stages/reference/level.dat")
_normal = os.path.join(preset_dir, "normal.json")
if not os.path.isfile(_reference):
    fail("there is no tools/livetest/stages/reference/level.dat, so the client stages "
         "start from whatever world happens to be on the machine")
elif os.path.isfile(_normal):
    def _key(entry):
        biome, params = entry
        flat = []
        for axis in ("temperature", "humidity", "continentalness", "erosion", "depth", "weirdness", "offset"):
            # The game saves a span whose ends agree as one number; a preset
            # writes it as a pair. Both are the pair.
            value = params.get(axis, 0.0)
            values = value if isinstance(value, list) else [value, value]
            if len(values) == 1:
                values = [values[0], values[0]]
            flat.append(tuple(round(float(v), 4) for v in values))
        return (biome, tuple(flat))
    try:
        _saved = nbtlite.overworld_biomes(nbtlite.read(_reference))
    except Exception as trouble:  # noqa: BLE001
        _saved = None
        fail(f"the reference level.dat cannot be read ({trouble}), so nothing knows what "
             f"world the stages start from")
    if _saved is None:
        if os.path.isfile(_reference):
            fail("the reference world names a preset instead of carrying its biome list, so "
                 "it was not made from ours and has none of our biomes in it")
    else:
        _wanted = json.loads(read(preset_dir, "normal.json"))["dimensions"]["minecraft:overworld"]
        _wanted = _wanted["generator"]["biome_source"].get("biomes", [])
        _have = sorted(_key(e) for e in _saved)
        _want = sorted(_key((e["biome"], e.get("parameters", {}))) for e in _wanted)
        if _have != _want:
            _differ = len(set(_have) ^ set(_want))
            fail(f"the reference world was saved under an older preset: {_differ} biome "
                 f"entr{'y' if _differ == 1 else 'ies'} differ from normal.json, so every "
                 f"fresh test world generates a world the pack no longer ships; re-save it "
                 f"from the test server (tools/livetest/stages/README.md)")
        else:
            print(f"the reference      : the stages' reference world carries the preset's "
                  f"{len(_want)} biome entries exactly, so a fresh test world is the world "
                  f"the pack ships")

print("the seam           : the rules pointed at every dimension file in the "
      "repository, a world with no row an error rather than a pass, and every "
      "register on the bus")

# ---------------------------------------------------------------------------

print("-" * 70)

# A room piece places its blocks where its box is only when it has an
# orientation: with none, the game's placeBlock uses the piece's relative
# coordinates as world coordinates, and eight kinds of structure located fine
# and stood nowhere (the mechanics survey, 27 September 2026).
_room_src = read(SRC, "structure/RoomPiece.java")
if _room_src.count("setOrientation(net.minecraft.core.Direction.NORTH);") < 2:
    fail("RoomPiece is built without an orientation in one of its constructors, so its blocks land beside "
         "the world's origin instead of inside its box")

if problems:
    print(f"{len(problems)} problem(s):")
    for problem in problems:
        print(f"  - {problem}")
    sys.exit(1)
print("all wilds checks passed")
