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
PORTAL_HARNESS = os.path.join(WORK, "tools/PortalHarness.java")
PORTAL_MAIN = "com.elysium.wilds.rules.PortalHarness"
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
if not os.path.isfile(PORTAL_HARNESS):
    fail("there is no way-in harness, so whether a ring of Vale Frames lights from "
         "every frame of it, and refuses one with a hole in it, is an opinion")
if not os.path.isfile(HARNESS):
    fail("there is no harness, so whether a claim inside another claim can generate "
         "is an opinion")
else:
    with tempfile.TemporaryDirectory() as tmp:
        staged = os.path.join(tmp, "src")
        os.makedirs(staged)
        sources = [os.path.join(PURE, n) for n in os.listdir(PURE) if n.endswith(".java")]
        for source in sources + [HARNESS, VALE_HARNESS, GRAMMAR_HARNESS,
                                 OVERWORLD_HARNESS, ROSTER, VALE_ROSTER,
                                 PORTAL_HARNESS]:
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

            # The way into the Vale: every ring from one cell to twenty-three,
            # whole and broken, on a plane of open sky. A flood that does not
            # stop on open sky is a server thread that does not stop either,
            # so running out of time is the failure, not an inconvenience.
            try:
                ran_portal = subprocess.run(
                    ["java", "-cp", os.path.join(tmp, "out"), PORTAL_MAIN],
                    capture_output=True, text=True, timeout=HARNESS_SECONDS)
            except subprocess.TimeoutExpired:
                ran_portal = None
                fail(f"the way-in harness did not finish within {HARNESS_SECONDS} "
                     f"seconds, so a flood for a ring can run for ever, and in the "
                     f"game it runs on the server thread")
            if ran_portal is None:
                pass
            elif ran_portal.returncode != 0:
                fail("the way-in harness failed:\n"
                     + harness_trouble(ran_portal.stdout))
            else:
                print("the way in         : "
                      + ran_portal.stdout.strip().splitlines()[-1])

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
            # And the tool list the Vale Frame is on, which is the game's
            # namespace rather than ours and so outside the two folders above.
            tags_there = os.path.join(tmp, "src/main/resources/data/minecraft/tags")
            tags_here = os.path.join(RES, "data/minecraft/tags")
            for base, _, names in os.walk(tags_there):
                for name in names:
                    fresh = os.path.join(base, name)
                    shipped = os.path.join(tags_here, os.path.relpath(fresh, tags_there))
                    if not os.path.isfile(shipped):
                        drifted.append("minecraft tags/" + os.path.relpath(fresh, tags_there)
                                       + " (missing)")
                    elif not samefile.same(fresh, shipped):
                        drifted.append("minecraft tags/" + os.path.relpath(fresh, tags_there))
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

for where, field in (("WildComponents", "TYPES"), ("WildBlocks", "BLOCKS"),
                     ("WildItems", "ITEMS"), ("WildItems", "TABS")):
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
# The way in
# ---------------------------------------------------------------------------
#
# The Vale had no survival way in: an operator's command and nothing else, so
# its twenty-eight creatures and two almanac quests were content nobody could
# have (the obtainability survey, 29 September 2026). A ring of Vale Frames lit
# with the compass is the way in. PortalRules is the shape and PortalHarness
# proves it; what is left for here is that the game's side does what the rules
# say, and that the data is there to build it from.

portal_rules = strip_comments(read(PURE, "PortalRules.java"), True)
compass_src = strip_comments(read(SRC, "item/BiomeCompassItem.java"), True)
gate_src = strip_comments(read(SRC, "block/ValePortal.java"), True)
frame_src = strip_comments(read(SRC, "block/ValeFrameBlock.java"), True)
sheet_src = strip_comments(read(SRC, "block/ValePortalBlock.java"), True)
travel_src = strip_comments(read(SRC, "level/ValeTravel.java"), True)
links_src = strip_comments(read(SRC, "level/ValeLinks.java"), True)
blocks_src = squashed(read(SRC, "registry/WildBlocks.java"))
items_src = strip_comments(read(SRC, "registry/WildItems.java"))

# The compass: lights a ring, passes on anything else, and is not used up.
lighting = squashed(body_of(compass_src, "public InteractionResult useOn(UseOnContext context)"))
for statement, why in (
        ("if (!level.getBlockState(pos).is(WildBlocks.VALE_FRAME.get())) { return InteractionResult.PASS; }",
         "the compass no longer passes on a block that is not a Vale Frame, so used on the "
         "ground it neither opens its search nor lights anything"),
        ("ValePortal.Frame frame = ValePortal.findFrame(level, pos);",
         "the compass does not find the ring it is used on, so what lights is not the shape "
         "the harness proved"),
        ("ValePortal.light(level, frame);",
         "the compass finds a ring and never lights it, so the Vale has no way in but a "
         "command again"),
        ('player.displayClientMessage(Component.translatable("elysiumwilds.gate.no_frame", '
         'PortalRules.LEAST_ACROSS, PortalRules.LEAST_UP) .withStyle(ChatFormatting.RED), true);',
         "a ring one frame short is refused in silence, and it looks exactly like a ring "
         "that works")):
    if statement not in lighting:
        fail(why)
if re.search(r"\.shrink\s*\(|hurtAndBreak|\.consume\s*\(", lighting):
    fail("lighting a ring uses the compass up; it is the wilds' way-finding tool, and a "
         "ring is already three crafts of frames")

# Finding a ring in the world is the rules' flood, on both axes, with the
# rules' idea of open.
finding = squashed(body_of(gate_src, "public static Frame findFrame(LevelAccessor level, BlockPos used)"))
if ("PortalRules.Ring ring = PortalRules.around( (a, u) -> cell(level.getBlockState("
        "used.relative(along, a).above(u))), 0, 0);") not in finding:
    fail("a ring in the world is not found by PortalRules.around, so the rule the harness "
         "proved from every frame, corners and all, is not the rule that lights")
if "for (Direction.Axis axis : new Direction.Axis[] {first, second}) {" not in finding \
        or "Direction.Axis second = first == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;" \
        not in finding:
    fail("a ring is looked for on one axis only, so a ring built the other way round never "
         "lights and says there is no frame")
cells = squashed(body_of(gate_src, "private static PortalRules.Cell cell(BlockState state)"))
if "if (state.is(WildBlocks.VALE_FRAME.get())) { return PortalRules.Cell.FRAME; }" not in cells:
    fail("a Vale Frame is not a frame to the flood, so no ring of them ever closes")
if "if (state.isAir() || state.is(WildBlocks.VALE_PORTAL.get())) { return PortalRules.Cell.OPEN; }" \
        not in cells:
    fail("something other than air or a lit gate counts as open, so a ring lights over "
         "whatever was left standing in it, or a lit ring cannot be found again")

# Building a Vale side sets every block through one guard, so nothing that
# holds things is replaced: a ring rebuilt where a player has since put a chest
# leaves the chest, and a gap in the ring, rather than the chest's contents gone.
building = squashed(body_of(gate_src, "public static void build(LevelAccessor level, BlockPos anchor, Direction.Axis axis)"))
placing = squashed(body_of(gate_src, "private static void place(LevelAccessor level, BlockPos pos, BlockState state, int flags)"))
if not building or re.search(r"level\.setBlock\(", building) \
        or "if (level.getBlockState(pos).hasBlockEntity()) { return; }" not in placing:
    fail("building a Vale side replaces blocks that hold things, so a ring rebuilt where a "
         "player has since put a chest takes the chest and everything in it")

# Breaking a frame closes the gate; standing in the gate is the trip.
removing = squashed(body_of(frame_src, "protected void onRemove("))
if "if (!state.is(newState.getBlock()) && !level.isClientSide()) { ValePortal.close(level, pos); }" \
        not in removing:
    fail("breaking a Vale Frame leaves its gate hanging in the air, still working, with "
         "nothing left to break to undo it")
inside = squashed(body_of(sheet_src, "protected void entityInside("))
if "if (level.isClientSide() || !(entity instanceof ServerPlayer player)) { return; }" not in inside:
    fail("the gate moves players on the client, or moves things that are not players; the "
         "first is a flicker of the wrong world and the second a trip nothing records")
if "ValeTravel.use(player, pos);" not in inside:
    fail("standing in a Vale Gate does nothing")

# The gate is a block and never an item, and drops nothing.
if re.search(r'"vale_portal"|VALE_PORTAL', items_src):
    fail("the Vale Gate has an item, so a player can carry a gate with no ring to be known "
         "by, and every trip through it builds another Vale side")
if not re.search(r'BLOCKS\.register\("vale_portal", \(\) -> new ValePortalBlock\('
                 r'BlockBehaviour\.Properties\.of\(\)[^;]*\.noCollission\(\)[^;]*\.noLootTable\(\)',
                 blocks_src):
    fail("the Vale Gate is not a block with no collision and no loot table: walked into it "
         "is a wall, and broken it asks for a drop it has none of")
for stray in ("assets/" + NS + "/models/item/vale_portal.json",
              "data/" + NS + "/loot_table/blocks/vale_portal.json"):
    if os.path.isfile(os.path.join(RES, stray)):
        fail(f"{stray} exists, and the gate is neither an item nor a drop")

# What a ring is built from: a recipe making what the rules say a craft makes,
# a drop, the right tool, both faces of the gate, drawn see-through.
per_craft = re.search(r"public static final int FRAMES_PER_CRAFT = (\d+);", portal_rules)
recipe = read(RES, "data", NS, "recipe/vale_frame.json")
if not recipe:
    fail("the Vale Frame has no recipe, and nothing else in the game makes one")
else:
    made = json.loads(recipe)
    if made.get("result", {}).get("id") != f"{NS}:vale_frame":
        fail(f"the Vale Frame's recipe makes {made.get('result', {}).get('id')}")
    if not per_craft or made.get("result", {}).get("count") != int(per_craft.group(1)):
        fail(f"a craft of Vale Frames makes {made.get('result', {}).get('count')} and "
             f"PortalRules says {per_craft.group(1) if per_craft else 'nothing'}, so the "
             f"price the harness proved is not the price a player pays")
    for symbol, value in (made.get("key") or {}).items():
        if not isinstance(value, dict) or not ({"item", "tag"} & set(value)):
            fail(f"the Vale Frame's recipe key {symbol!r} is {value!r}; an ingredient in "
                 f"1.21 is an object with an item or a tag, and anything else drops the "
                 f"recipe with one line in the log")
loot = read(RES, "data", NS, "loot_table/blocks/vale_frame.json")
if f'"name": "{NS}:vale_frame"' not in loot or '"minecraft:survives_explosion"' not in loot:
    fail("a mined Vale Frame does not drop itself, so a ring can be built and never taken "
         "down again")
pickaxe = read(RES, "data/minecraft/tags/block/mineable/pickaxe.json")
if f"{NS}:vale_frame" not in (json.loads(pickaxe).get("values", []) if pickaxe else []):
    fail("the Vale Frame wants the right tool for its drop and is not on the pickaxe's list, "
         "so mining one with anything at all drops nothing")
gate_state = read(RES, "assets", NS, "blockstates/vale_portal.json")
gate_variants = json.loads(gate_state).get("variants", {}) if gate_state else {}
for axis in ("axis=x", "axis=z"):
    if axis not in gate_variants:
        fail(f"the Vale Gate has no {axis} in its blockstate, so a ring built that way round "
             f"is filled with the missing-model cube")
for model in ("vale_portal_ns", "vale_portal_ew"):
    body = read(RES, "assets", NS, f"models/block/{model}.json")
    if not body or json.loads(body).get("render_type") != "minecraft:translucent":
        fail(f"{model} is not drawn on the translucent layer, and the gate's light is "
             f"see-through, so it renders as a black sheet")

# The trip: read the way off the world, remember where the player came in,
# search once a ring, build the far side if it is not standing, land beside it.
using = squashed(body_of(travel_src, "public static void use(ServerPlayer player, BlockPos gatePos)"))
if "if (onCooldown(player, server)) { return; }" not in using:
    fail("a gate is asked every tick a player stands in it, and nothing ignores somebody who "
         "has just come through, so arriving is being sent straight back")
if ("if (player.level().dimension().equals(VALE)) { leave(player, server); } "
        "else { enter(player, server, gatePos); }") not in using:
    fail("which way a gate leads is not read off the world the player is in, the one fact "
         "that cannot be wrong")
# A ring's first trip says so in the log, once, in a fixed form, and says whether
# its Vale side was built new or was a ring already standing where the search
# ended: the live footing stage proves a first trip, and a fresh search's
# footing, by that line and by nothing else (16 Oct 2026: the site list the
# stages kept by hand, and the client's "Loaded N advancements" taken for a
# change of world, let three rounds of review find fourteen false passes).
# Whether the side was standing is the settling's own word (16 Oct 2026, round
# 4): a side found by a lit gate near where it went (standingBy) is standing,
# whatever its anchor cell holds. Asking the side's anchor instead called a
# ring with its anchor cell broken by hand built, since the search still finds
# and uses such a ring and the build lights it again (finding M1).
# And the line is kept in the world, where no log line can write it: the
# integrated server logs some text as it is, line breaks and all, so a named
# entity's death can write a copy of an old first-trip line as a line of its own
# (findings F1v, F1n). Kept in ValeLinks, the mod's own saved data, since round
# 5 (OP1): it was command storage elysiumwilds:trips, which /data modify writes,
# and the operator who can name a mob with line breaks could set it to agree.
FIRST_TRIP_FORM = ('private static final String FIRST_TRIP = '
                   '"Vale first trip: player %s ring %s %d %d %d side %d %d %d axis %s %s";')
FIRST_TRIP_SETTLED = ("Settled settled = settle(vale, column != null ? column : anchor, axis); "
                      "arrival = settled.arrival();")
FIRST_TRIP_STANDING = "boolean standing = settled.standing();"
FIRST_TRIP_SAID = ('String said = String.format(Locale.ROOT, FIRST_TRIP, player.getGameProfile().getName(), '
                   'here.dimension().location(), anchor.getX(), anchor.getY(), anchor.getZ(), '
                   'arrival.anchor().getX(), arrival.anchor().getY(), arrival.anchor().getZ(), '
                   'arrival.axis().getName(), standing ? "standing" : "built");')
FIRST_TRIP_LOGGED = 'ElysiumWilds.LOGGER.info("{}", said);'
FIRST_TRIP_KEPT = "links.firstTrip(said);"
entering = squashed(body_of(travel_src,
                            "private static void enter(ServerPlayer player, MinecraftServer server, BlockPos gatePos)"))
for statement, why in (
        ("ValePortal.Frame frame = ValePortal.findFrame(here, gatePos);",
         "the ring a trip came through is not found again from the gate, so its Vale side is "
         "filed under whichever block was touched and every trip builds another"),
        ("links.remember(player.getUUID(), new ValeLinks.Return(here.dimension(), "
         "back(player, anchor, axis, side), facing(axis, side)));",
         "where a player came in is not remembered, so the way back cannot put them there"),
        ("ValeLinks.Arrival arrival = links.arrival(here.dimension(), anchor);",
         "a ring's Vale side is not looked up before a trip, so every trip through one ring "
         "searches and builds again"),
        ("if (arrival == null) { BlockPos column = nearestVerdant(vale, anchor); "
         + FIRST_TRIP_SETTLED + " links.link(here.dimension(), anchor, arrival); "
         + FIRST_TRIP_STANDING + " " + FIRST_TRIP_SAID + " " + FIRST_TRIP_LOGGED + " "
         + FIRST_TRIP_KEPT + " }",
         "a ring's first trip no longer searches once and files what it found, saying so once in "
         "the log, so either every trip costs a compass search and a new ring, or none ever finds "
         "the verdant vale, or the line the live footing stage proves a first trip by is said on "
         "some other trip or not at all"),
        ("if (!ValePortal.stands(vale, arrival.anchor())) { "
         "ValePortal.build(vale, arrival.anchor(), arrival.axis()); }",
         "the ring on the Vale side is not built, or not rebuilt once broken, so a player "
         "arrives with no way back"),
        ("BlockPos landing = arrival.anchor().relative(ValePortal.out(arrival.axis()), "
         "onward * PortalRules.LANDING_OUT);",
         "a player is not put down beside the ring they arrive at, and put down in it they "
         "are sent straight back"),
        ("teleport(player, server, vale, Vec3.atBottomCenterOf(landing), facing(arrival.axis(), onward));",
         "stepping into a gate does not take a player to the Vale")):
    if statement not in entering:
        fail(why)
if FIRST_TRIP_FORM not in squashed(travel_src):
    fail("the line a ring's first trip logs is not in the fixed form the live stages read "
         "(Vale first trip: player, ring, side, axis, built or standing), so "
         "stage_vale_footing_client.ps1 never sees a first trip and the Vale stages cannot "
         "tell where a trip landed")
if FIRST_TRIP_SAID not in entering or FIRST_TRIP_LOGGED not in entering:
    fail("a ring's first trip is not said at INFO in the log with the player, the ring, its "
         "Vale side and whether that side was built new, so the live footing stage has no word "
         "of the game's that a trip was a first trip")
if FIRST_TRIP_STANDING not in entering:
    fail("whether a first trip's Vale side was a ring already standing is not the settling's "
         "own word (Settled.standing), so the first-trip line calls a reused ring built when "
         "its anchor cell is empty (asked of the anchor alone), or every side built or "
         "standing, and the footing stage cannot tell a fresh search's footing from a ring it "
         "reused")
if len(re.findall(r"\bFIRST_TRIP\b", travel_src)) != 2 or travel_src.count("Vale first trip") != 1:
    fail("the first-trip line is named somewhere other than its one declaration and the one "
         "place a ring's first trip says it, so an ordinary trip can log it too and a second "
         "trip through a ring reads as its first")
# The world's own record of its trips (round 4, F1v and F1n; round 5, OP1 and
# RZ1): what the footing stage backs the first-trip line with, and the vale
# stage tells the way back from a way out to the spawn by. Kept in ValeLinks,
# which no command writes, by a first trip and only there, and by a way out to
# the spawn and only there; never in command storage, which any /data modify
# writes; and read back by a command that writes nothing.
if FIRST_TRIP_KEPT not in entering or squashed(travel_src).count(".firstTrip(") != 1:
    fail("a ring's first trip is not kept in the world's own record of first trips, once and "
         "only there, so the footing stage takes a copy of an old first-trip line written into "
         "the log by a named entity's death for a first trip")
if "getCommandStorage()" in squashed(travel_src) or "getCommandStorage()" in squashed(links_src):
    fail("the record of the world's Vale trips is kept in command storage, which any operator's "
         "/data modify writes, so the operator who can write a copy of an old first-trip line into "
         "the log can set the record to agree with it")
recording = squashed(body_of(links_src, "public void firstTrip(String said)"))
for statement, why in (
        ("firstTrips += 1;",
         "the record of first trips does not count one more for each first trip, so the footing "
         "stage cannot tell a first trip made now from none at all"),
        ("lastFirstTrip = said;",
         "the record of first trips does not keep the line the latest one logged, so the footing "
         "stage cannot tell whose first trip it counted"),
        ("setDirty();",
         "a first trip kept in the record is not marked to be saved, so the record the footing "
         "stage reads after a restart is not the world's")):
    if statement not in recording:
        fail(why)
spawning = squashed(body_of(links_src, "public void outToSpawn(UUID player)"))
for statement, why in (
        ("outToSpawn.merge(player, 1, Integer::sum);",
         "a way out of the Vale to the world's spawn is not counted one more for the player, so "
         "the vale stage takes a way out to a spawn by the ring for the return ring"),
        ("setDirty();",
         "a way out to the spawn counted in the record is not marked to be saved")):
    if statement not in spawning:
        fail(why)
for signature, statement in (("public int firstTrips()", "return firstTrips;"),
                             ("public String lastFirstTrip()", "return lastFirstTrip;"),
                             ("public int outToSpawnFor(UUID player)",
                              "return outToSpawn.getOrDefault(player, 0);")):
    if statement not in squashed(body_of(links_src, signature)):
        fail(f"{signature.split('(')[0].split()[-1]} does not read back what the record keeps, so "
             f"/elysium vale trips tells the live stages something other than the world's record")
saving = squashed(body_of(links_src, "public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries)"))
loading = squashed(body_of(links_src, "public static ValeLinks load(CompoundTag tag,"))
for statement, where in (('tag.putInt("FirstTrips", firstTrips);', saving),
                         ('tag.putString("LastFirstTrip", lastFirstTrip);', saving),
                         ('one.putInt("Count", entry.getValue());', saving),
                         ('tag.put("OutToSpawn", spawned);', saving),
                         ('data.firstTrips = tag.getInt("FirstTrips");', loading),
                         ('data.lastFirstTrip = tag.getString("LastFirstTrip");', loading),
                         ('ListTag spawned = tag.getList("OutToSpawn", Tag.TAG_COMPOUND);', loading),
                         ('data.outToSpawn.put(one.getUUID("Player"), one.getInt("Count"));', loading)):
    if statement not in where:
        fail("the world's record of its Vale trips is not saved and read back whole, so after a "
             "restart its first trips, its last first-trip line or its ways out to the spawn start "
             "again, and a stage run across one reads a record the world never kept")
        break
# Read back for the live stages: one fixed line, the player's own count, and
# nothing written.
command_src = strip_comments(read(SRC, "command/ValeCommand.java"), True)
if not command_src:
    fail("there is no /elysium vale trips (command/ValeCommand.java), so the live stages cannot "
         "read the world's record of its Vale trips and every run fails before the gate")
else:
    if "@EventBusSubscriber(modid = ElysiumWilds.MODID)" not in command_src \
            or "event.getDispatcher().register(root());" not in squashed(body_of(command_src, "public static void onRegister(RegisterCommandsEvent event)")):
        fail("/elysium vale trips is written and never registered, so the live stages' reads of "
             "the world's record answer that there is no such command")
    if ('return Commands.literal("elysium").then(Commands.literal("vale").then(Commands.literal("trips") '
            '.requires(source -> source.hasPermission(2)) .executes(context -> trips(context.getSource()))));') \
            not in squashed(body_of(command_src, "private static LiteralArgumentBuilder<CommandSourceStack> root()")):
        fail("the world's record of its Vale trips is not read by /elysium vale trips, for "
             "operators, so the live stages ask a command that is not there")
    if ('private static final String RECORD = "Vale trips: first %d, out to spawn %d, last %s";'
            not in squashed(command_src)):
        fail("the line /elysium vale trips answers is not in the fixed form the live stages read "
             "(Vale trips: first, out to spawn, last), so every read of the record fails")
    answering = squashed(body_of(command_src, "private static int trips(CommandSourceStack source)"))
    for statement in ("ServerPlayer player = source.getPlayerOrException();",
                      "ValeLinks links = ValeLinks.get(source.getServer());",
                      'String last = links.lastFirstTrip().isEmpty() ? "none" : links.lastFirstTrip();',
                      "String said = String.format(Locale.ROOT, RECORD, links.firstTrips(), "
                      "links.outToSpawnFor(player.getUUID()), last);",
                      "source.sendSuccess(() -> Component.literal(said), false);"):
        if statement not in answering:
            fail("/elysium vale trips does not answer the world's record as it stands, in one line: "
                 "its first trips, the asking player's ways out to the spawn, and the latest "
                 "first-trip line, so the live stages hold the log against something else")
            break
    if re.search(r"\.(?:firstTrip|outToSpawn|remember|link)\(|setDirty|getCommandStorage", command_src):
        fail("/elysium vale trips writes to what it reads, so the command the live stages trust "
             "to read the world's record is one an operator can use to set it")
# The settling's word, from where it comes: only a ring found standing by a lit
# gate is standing; a side the settling goes on to build is not.
standing_by = squashed(body_of(travel_src, "private static Settled standingBy(ServerLevel vale, BlockPos ground)"))
if ("return there == null ? null : new Settled(new ValeLinks.Arrival(there.anchor(), there.axis()), true);"
        not in standing_by):
    fail("a ring the search finds standing by a lit gate is not said to be standing, so the "
         "first-trip line calls a reused ring built and the footing stage asserts a footing no "
         "fresh search chose")
searching =squashed(body_of(travel_src, "private static BlockPos nearestVerdant(ServerLevel vale, BlockPos from)"))
for statement, why in (
        ("int radius = (int) PortalRules.ARRIVAL_SEARCH_BLOCKS;",
         "a way in looks as far as something other than the rules say, so what one trip "
         "costs the server is whatever it is"),
        ("int step = (int) Math.round(PortalRules.arrivalStep());",
         "a way in samples on a grid the rules did not work out, so the cost the harness "
         "proved is not the cost the server pays"),
        ("int middle = vale.getMinBuildHeight() + vale.getHeight() / 2;",
         "a way in searches at a height that is not inside the Vale, and a search from outside "
         "the world's height finds nothing at all"),
        ("Pair<BlockPos, ?> hit = vale.findClosestBiome3d(holder -> holder.is(wanted), "
         "new BlockPos(from.getX(), middle, from.getZ()), radius, step, vale.getHeight());",
         "a way in does not look for the gentlest crossing at one height from the ring's own "
         "x and z")):
    if statement not in searching:
        fail(why)
# Where the Vale side goes: by a ring already standing, else on the nearest dry
# column within the rules' reach, else, when all of that is sea, by the shore
# the rules' eight compass lines meet, settled the same way, else the column as
# found. The ground is the heightmap that stops at water and the search finds a
# biome rather than a block, so the nearest verdant column, when it was a lake,
# a river or the sea, had the ring built on the water and the player landing in
# it (1 October 2026: the live stage arrived at sea level in a bay); and the
# Vale has no ocean biome, so a fresh ring's first trip landed in a sea where
# every column within sixty-four was water but its own pads (1 October, late).
# The spiral, the lines and the dryness test are the rules', proved in the
# way-in harness; held here is that the trip asks them in that order, with what
# the world says stands at each column's top, fluid and sturdiness both, and
# settles for the column as found only when neither stage finds dry ground.
settling = squashed(body_of(travel_src,
                            "private static Settled settle(ServerLevel vale, BlockPos column, Direction.Axis axis)"))
for statement, why in (
        ("Settled near = near(vale, column, axis); if (near != null) { return near; }",
         "the near stage, a ring standing by the column or the nearest dry column within the spiral's "
         "reach, is not tried first, so a verdant landing is walked off to a far shore or built over "
         "a standing ring"),
        ("PortalRules.Column far = PortalRules.nearestFarDry(footing(vale, column));",
         "when the spiral finds only water the Vale side is not walked out along the rules' compass "
         "lines to a shore, so a ring whose verdant column sits in a sea is built on the water and the "
         "player lands in it a hundred blocks from land"),
        ("PortalRules.Column shore = PortalRules.settled(footing(vale, column), far, axisOf(axis));",
         "the shore the far stage finds is not walked on along its line until the ring's sill and both "
         "landings are dry, so a ring found at a waterline is built astride it, its landing a block out "
         "over the water (2 October 2026: the player stood on a built pad with land a step east)"),
        ("if (far != null) { PortalRules.Column shore = PortalRules.settled(footing(vale, column), far, "
         "axisOf(axis)); near = near(vale, column.offset(shore.east(), 0, shore.south()), axis); "
         "if (near != null) { return near; } }",
         "the shore the far stage finds is not settled the way the column was, so a ring standing "
         "there is built over, or the side goes somewhere other than the shore found"),
        ("return new Settled(on(vale, column, axis), false);",
         "the Vale side does not settle for the column as found when everything within reach of both "
         "stages is water, so a ring whose verdant vale is all sea has no far side at all, or the "
         "side built there is said to be a ring already standing")):
    if statement not in settling:
        fail(why)
nearing = squashed(body_of(travel_src,
                           "private static Settled near(ServerLevel vale, BlockPos column, Direction.Axis axis)"))
for statement, why in (
        ("Settled standing = standingBy(vale, groundAt(vale, column)); "
         "if (standing != null) { return standing; }",
         "a ring already standing where a Vale side goes is not used first, so the side is built "
         "over it and breaks it"),
        ("PortalRules.Column dry = PortalRules.nearestDry(footing(vale, column));",
         "the Vale side is not moved to the dry column the rules find nearest, so when the verdant "
         "column the search found is a lake, a river or the sea the ring is built on the water and "
         "the player lands in it"),
        ("if (dry == null) { return null; }",
         "the near stage does not say when everything within its reach is water, so the far stage "
         "is never asked and the sea is landed in"),
        ("PortalRules.Column site = PortalRules.settled(footing(vale, column), dry, axisOf(axis));",
         "the dry column the spiral finds is not walked on along its line until the ring's sill and "
         "both landings are dry, so a ring found at a waterline is built astride it, its landing a "
         "block out over the water (2 October 2026: the player stood on a built pad with land a step "
         "east)"),
        ("BlockPos spot = column.offset(site.east(), 0, site.south());",
         "the Vale side goes somewhere other than the column settled on"),
        ("return standing != null ? standing : new Settled(on(vale, spot, axis), false);",
         "the Vale side is not built at the column settled on, or a ring standing by it is built "
         "over, or the side built there is said to be a ring already standing")):
    if statement not in nearing:
        fail(why)
axing = squashed(body_of(travel_src, "private static PortalRules.Axis axisOf(Direction.Axis axis)"))
if "return axis == Direction.Axis.X ? PortalRules.Axis.X : PortalRules.Axis.Z;" not in axing:
    fail("the ring's axis is not read into the rules' as the game names it, so the footprint the "
         "settling keeps dry runs the wrong way and it is the side posts' columns that are kept dry "
         "rather than the landings")
crossing = squashed(body_of(gate_src, "public static Direction across(Direction.Axis axis)"))
outing = squashed(body_of(gate_src, "public static Direction out(Direction.Axis axis)"))
if "return axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;" not in crossing \
        or "return axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;" not in outing:
    fail("the ring in the world does not run east and through south along X, and south and through "
         "east along Z, which is how the rules lay its footprint, so the columns the settling keeps "
         "dry are not the ones the ring is built on")
building = squashed(body_of(travel_src,
                            "private static ValeLinks.Arrival on(ServerLevel vale, BlockPos spot, Direction.Axis axis)"))
if "return new ValeLinks.Arrival(new BlockPos(spot.getX(), y, spot.getZ()), axis);" not in building:
    fail("the Vale side is not built at the spot settled on")
footing_src = squashed(body_of(travel_src,
                               "private static PortalRules.Surface footing(ServerLevel vale, BlockPos column)"))
if "return (east, south) -> top(vale, column.offset(east, 0, south));" not in footing_src:
    fail("the ground the rules read is not what stands at the top of each column so many blocks east "
         "and south of the one settled, so the dry search is asked about somewhere else")
topping = squashed(body_of(travel_src, "private static PortalRules.Footing top(ServerLevel vale, BlockPos column)"))
if ("BlockPos under = groundAt(vale, column).below(); "
        "return new PortalRules.Footing(wet(vale, under), firm(vale, under));") not in topping:
    fail("what stands at a column's top is not the block its heightmap stopped under, read for fluid "
         "and for sturdiness both, so the dry search is asked something other than what the harness "
         "proved")
wetting = squashed(body_of(travel_src, "private static boolean wet(ServerLevel vale, BlockPos under)"))
if "return !vale.getFluidState(under).isEmpty();" not in wetting:
    fail("whether a column is wet is not read off the fluid in the block its heightmap stopped "
         "under, so the dry search is asked something other than what the harness proved and a "
         "lake can count as ground again")
firming = squashed(body_of(travel_src, "private static boolean firm(ServerLevel vale, BlockPos under)"))
if "return vale.getBlockState(under).isFaceSturdy(vale, under, Direction.UP);" not in firming:
    fail("whether a column's top would hold a player up is not read off the sturdiness of its upper "
         "face, so a fence post, a bottom slab or a chest's lid that stops the heightmap and holds "
         "nobody up is called ground and a ring is built on it")
grounding = squashed(body_of(travel_src, "private static BlockPos groundAt(ServerLevel vale, BlockPos column)"))
if ("vale.getChunk(column.getX() >> 4, column.getZ() >> 4); "
        "return vale.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);") not in grounding:
    fail("the ground a Vale side is set on is not the heightmap's with the chunk generated first, "
         "so it is read off a chunk that is not there yet and the ring is built at the bottom of "
         "the world")
dryness = squashed(body_of(portal_rules, "public static boolean dry(Surface surface, Column at)"))
if "Footing top = surface.at(at.east(), at.south()); return !top.fluid() && top.sturdy();" not in dryness:
    fail("a column is not dry by both of the rules' tests, nothing flowing at its top and a top "
         "that holds a player up, so either a lake or a fence post is called ground")
drying = squashed(body_of(portal_rules, "public static Column nearestDry(Surface surface)"))
if "for (Column one : drySearch()) { if (dry(surface, one)) { return one; } }" not in drying:
    fail("the dry column is not the first dry one of the rules' own search, so what the harness "
         "proved nearest first is not what the trip lands on")
spiralling = squashed(body_of(portal_rules, "private static List<Column> spiral()"))
if not re.search(r"public static final int DRY_SEARCH_BLOCKS = \d+;", portal_rules) \
        or "for (int south = -DRY_SEARCH_BLOCKS; south <= DRY_SEARCH_BLOCKS; south++) {" not in spiralling \
        or "for (int east = -DRY_SEARCH_BLOCKS; east <= DRY_SEARCH_BLOCKS; east++) {" not in spiralling \
        or "columns.sort(NEAREST_FIRST);" not in spiralling:
    fail("the dry search does not read its reach from PortalRules.DRY_SEARCH_BLOCKS, or is not "
         "sorted nearest first, so how far a landing may be moved is not the number the harness "
         "proved")
# The far stage: its stride and reach are the rules' constants, every stride
# past the spiral's square along the eight lines, sorted nearest first, and the
# first dry sample is the shore.
faring = squashed(body_of(portal_rules, "public static Column nearestFarDry(Surface surface)"))
if "for (Column one : farDrySearch()) { if (dry(surface, one)) { return one; } }" not in faring:
    fail("the far shore is not the first dry sample of the rules' own compass lines, so what the "
         "harness proved nearest first is not where a trip past a sea lands")
lining = squashed(body_of(portal_rules, "private static List<Column> lines()"))
striding = squashed(body_of(portal_rules, "private static int stride()"))
if not re.search(r"public static final int FAR_DRY_STEP = \d+;", portal_rules) \
        or not re.search(r"public static final int FAR_DRY_SEARCH_BLOCKS = \d+;", portal_rules) \
        or "return Math.max(1, FAR_DRY_STEP);" not in striding \
        or "for (int out = stride(); out <= FAR_DRY_SEARCH_BLOCKS; out += stride()) {" not in lining \
        or "if (out <= DRY_SEARCH_BLOCKS) { continue; }" not in lining \
        or "for (int[] line : LINES) { columns.add(new Column(line[0] * out, line[1] * out)); }" not in lining \
        or "columns.sort(NEAREST_FIRST);" not in lining:
    fail("the far stage does not walk the eight compass lines by PortalRules.FAR_DRY_STEP out to "
         "PortalRules.FAR_DRY_SEARCH_BLOCKS past the spiral's square, sorted nearest first, so how "
         "far a sea may be crossed and what it costs the server is not what the harness proved")
# The footing: the first dry column either stage meets is at the waterline,
# so a ring settled there straddles the shore with a landing over the water
# (2 October 2026). The footprint is the rules', the sill's row and both
# landing rows on the ring's own axes; a footing is dry when every column of
# it is, by the one test; and the settling walks the column found on along
# its line, within SETTLE_IN_BLOCKS, for the first dry footing, keeping the
# column found when there is none. Held here is that the trip asks exactly
# that, after both stages.
printing = squashed(body_of(portal_rules, "private static List<Column> footprintOf(Axis axis)"))
if ("for (int row : new int[] {-LANDING_OUT, 0, LANDING_OUT}) { for (int a = 0; a < LEAST_ACROSS; a++) { "
        "columns.add(new Column(a * along.east() + row * out.east(), a * along.south() + row * out.south())); "
        "} }") not in printing:
    fail("a ring's footprint is not the sill's row and both landing rows, LEAST_ACROSS wide along the "
         "across axis from the anchor and LANDING_OUT out either side along the through axis, so what "
         "the settling keeps dry is not where the ring stands and a player lands")
if "return axis == Axis.X ? new Column(1, 0) : new Column(0, 1);" \
        not in squashed(body_of(portal_rules, "public static Column across(Axis axis)")) \
        or "return axis == Axis.X ? new Column(0, 1) : new Column(1, 0);" \
        not in squashed(body_of(portal_rules, "public static Column through(Axis axis)")):
    fail("the rules' across and through do not run the way the ring in the world does, east and south "
         "along X and south and east along Z, so the footprint is turned a quarter and it is the side "
         "posts' columns that are kept dry rather than the landings")
footed = squashed(body_of(portal_rules, "public static boolean footingDry(Surface surface, Column column, Axis axis)"))
if ("for (Column one : footprint(axis)) { if (!dry(surface, new Column(column.east() + one.east(), "
        "column.south() + one.south()))) { return false; } } return true;") not in footed:
    fail("a footing is not dry only when every column of the footprint is dry by the rules' own test, "
         "so a ring is settled with a landing over the water, or never settled at all")
settled_src = squashed(body_of(portal_rules, "public static Column settled(Surface surface, Column found, Axis axis)"))
if not re.search(r"public static final int SETTLE_IN_BLOCKS = \d+;", portal_rules) \
        or ("Column way = new Column(Integer.signum(found.east()), Integer.signum(found.south()));") \
        not in settled_src \
        or ("for (int stride = 0; stride <= SETTLE_IN_BLOCKS; stride++) { Column at = new Column(found.east() "
            "+ way.east() * stride, found.south() + way.south() * stride); if (footingDry(surface, at, axis)) "
            "{ return at; } } return found;") not in settled_src \
        or ("for (Column one : drySearch()) { if (within(one, SETTLE_IN_BLOCKS) && footingDry(surface, one, "
            "axis)) { return one; } } return found;") not in settled_src:
    fail("the settling does not walk the dry column found on along the line it was found on, within "
         "PortalRules.SETTLE_IN_BLOCKS, for the first column whose footing is dry, or the spiral again "
         "within the same reach when there is no line, keeping the column found when there is none, so "
         "how far a ring may be walked inland is not the number the harness proved")
if ("return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath( "
        "ElysiumWilds.MODID, PortalRules.arrivalBiome()));") \
        not in squashed(body_of(travel_src, "gentlest() {")):
    fail("the crossing a way in looks for is not the one PortalRules names and the harness "
         "proved is nought rings out")
leaving = squashed(body_of(travel_src, "private static void leave(ServerPlayer player, MinecraftServer server)"))
if "teleport(player, server, home, back.where(), back.facing());" not in leaving:
    fail("the way back does not put a player where they came in")
# A way out to the spawn is counted in the world's record, there and only there
# (round 5, RZ1): the vale stage reads the count, since the log line saying so
# can be lost with a log that rolled over at midnight.
if ("ValeLinks links = ValeLinks.get(server); ValeLinks.Return back = links.returnFor(player.getUUID());"
        not in leaving
        or "if (back == null || home == null || home.dimension().equals(VALE)) { "
           "links.outToSpawn(player.getUUID());" not in leaving
        or squashed(travel_src).count(".outToSpawn(") != 1):
    fail("a way out of the Vale to the world's spawn is not counted in the world's record, once "
         "and only there, so the vale stage takes a way out to a spawn by the ring for the "
         "return ring whenever the log line saying so was lost")
if ("teleport(player, server, overworld, Vec3.atBottomCenterOf(overworld.getSharedSpawnPos()), "
        "player.getYRot());") not in leaving:
    fail("somebody in the Vale with no way in on record goes nowhere through a gate, and "
         "stands in it being asked every tick")
backing = squashed(body_of(travel_src, "private static Vec3 back("))
if ("double out = (axis == Direction.Axis.X ? anchor.getZ() : anchor.getX()) + 0.5D + side "
        "* PortalRules.LANDING_OUT;") not in backing:
    fail("the way back puts a player somewhere other than in front of the ring they came in "
         "by, on their side of it")
cooling_off = squashed(body_of(travel_src, "private static boolean onCooldown("))
if "if (left <= 0 || left > PortalRules.COOLDOWN_TICKS) { cooldowns.remove(player.getUUID()); return false; }" \
        not in cooling_off:
    fail("a gate cooldown left over from an earlier run of the server is still obeyed; in a "
         "single-player game the tick count starts again with every world, so a player could "
         "be shut out of every gate for as long as the last world ran")
moving = squashed(body_of(travel_src, "private static void teleport("))
if "cooldowns.put(player.getUUID(), server.getTickCount() + PortalRules.COOLDOWN_TICKS);" \
        not in moving:
    fail("a trip does not start the cooldown the rules state, so a player arriving by a "
         "gate is sent back through it")
keeping = squashed(links_src)
for statement in ('tag.put("Returns", backs);', 'tag.put("Arrivals", links);',
                  'ListTag backs = tag.getList("Returns", Tag.TAG_COMPOUND);',
                  'ListTag links = tag.getList("Arrivals", Tag.TAG_COMPOUND);'):
    if statement not in keeping:
        fail("where players came in and which Vale side each ring leads to are not both "
             "saved and read back, so after a restart the way back is the world's spawn and "
             "every ring builds its far side again")
        break

# One height is the whole column only while every crossing claims the same
# depth: the search samples one, and a Vale whose biomes changed with depth
# would hide the verdant vale under a player's feet.
vale_world = read(RES, "data", NS, "dimension/vale.json")
if vale_world:
    vale_claims = json.loads(vale_world)["generator"]["biome_source"]["biomes"]
    if len({json.dumps(one["parameters"].get("depth")) for one in vale_claims}) != 1 \
            or len({json.dumps(one["parameters"].get("offset")) for one in vale_claims}) != 1:
        fail("the Vale's crossings claim different depths, so a column is not one biome top "
             "to bottom and a way in that searches one height can miss the verdant vale")

# One shape for both ways out of the world. The rift is the same flood in
# another module; while the two are two copies, their numbers are held equal
# here, so a player who has built one can build the other.
rift = strip_comments(read(ROOT, "elysium-dungeons/src/main/java/com/elysium/dungeons/block/RiftPortal.java"))
if rift:
    for theirs, ours in (("MIN_INNER_WIDTH", "LEAST_ACROSS"), ("MIN_INNER_HEIGHT", "LEAST_UP"),
                         ("MAX_INNER", "MOST")):
        rift_value = re.search(r"public static final int " + theirs + r" = (\d+);", rift)
        our_value = re.search(r"public static final int " + ours + r" = (\d+);", portal_rules)
        if not rift_value or not our_value or rift_value.group(1) != our_value.group(1):
            fail(f"a Vale ring's {ours} is {our_value.group(1) if our_value else 'unset'} and "
                 f"the rift's {theirs} is {rift_value.group(1) if rift_value else 'unset'}; "
                 f"both are the Nether portal's shape, so a player who has built one can "
                 f"build the other")

for key in ("block.elysiumwilds.vale_frame", "block.elysiumwilds.vale_portal",
            "elysiumwilds.compass.gate", "elysiumwilds.gate.no_frame",
            "elysiumwilds.gate.no_vale", "elysiumwilds.gate.arrived",
            "elysiumwilds.gate.arrived_far", "elysiumwilds.gate.home",
            "elysiumwilds.gate.no_way_back"):
    if key not in lang:
        fail(f"{key} has no words, so it reads as its own translation key in the world")

print("the way in         : a ring of Vale Frames found by the rules' flood on both axes "
      "and lit by the compass without using it up, the gate a block with no item and no "
      "drop, one search a ring for the gentlest crossing, a landing beside the far ring, "
      "and the way back to where the player came in")

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
    # The strangeness bands counted as whole elements, new WildRules.Span(a, b),
    # each between the commas of the list: a bare `Span(` counts one nested in
    # some other expression just the same.
    ours_count = len(roster) * len(re.findall(
        r"(?:^ ?|, )new WildRules\.Span\(-?\d+\.\d+D, -?\d+\.\d+D\)(?=,|$)",
        re.search(r"STRANGENESS\s*=\s*List\.of\((.*?)\);",
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

# ---------------------------------------------------------------------------
# The tags (29 Sep 2026): every biome of ours is, to every tag, the biome it
# models. The preset puts only ours in the overworld, and a biome is in no
# tag the game does not list it in, so before this none was in
# minecraft:is_overworld: no strongholds (no way to the End), no villages, no
# mineshafts, and nothing this pack adds by tag. The generator writes the
# tags out of the game's own files; this reads the same files and holds the
# shipped tags to them exactly, both ways.
# ---------------------------------------------------------------------------
import importlib.util as _ilu
import zipfile as _zipfile
_gen_spec = _ilu.spec_from_file_location("gen_wilds", os.path.join(WORK, "tools/gen_wilds.py"))
_gen = _ilu.module_from_spec(_gen_spec)
_gen_spec.loader.exec_module(_gen)
_shape = _gen.overworld()
_tags = _gen.biome_tag_members()
_expected = {}
for _one in _shape["lands"] + _shape["caves"]:
    _model = "minecraft:" + _one["model"]
    for _tag, _members in _tags.items():
        if _model in _members:
            _expected.setdefault(_tag, set()).add(f"{NS}:{_one['name']}")
_ours = {f"{NS}:{_one['name']}" for _one in _shape["lands"] + _shape["caves"]}
_tag_dir = os.path.join(RES, "data")
_shipped = {}
for _namespace in ("minecraft", "c"):
    _base = os.path.join(_tag_dir, _namespace, "tags/worldgen/biome")
    for _dirpath, _, _files in os.walk(_base) if os.path.isdir(_base) else []:
        for _file in _files:
            if _file.endswith(".json"):
                _rel = os.path.relpath(os.path.join(_dirpath, _file), _base).replace(os.sep, "/")[:-5]
                _doc = json.loads(read(_dirpath, _file) or "{}")
                _shipped[f"{_namespace}:{_rel}"] = {v if isinstance(v, str) else v.get("id", "")
                                                    for v in _doc.get("values", [])}
for _biome in sorted(_ours):
    if _biome not in _shipped.get("minecraft:is_overworld", set()):
        fail(f"{_biome} is in no minecraft:is_overworld, so it has no stronghold, no mineshaft and "
             f"none of what this pack adds to the overworld by tag")
for _tag, _members in sorted(_expected.items()):
    _missing = sorted(_members - _shipped.get(_tag, set()))
    if _missing:
        fail(f"{len(_missing)} biome(s) of ours model a member of {_tag} and are not in it "
             f"({_missing[0]} ...), so what is keyed to that tag skips them")
for _tag, _members in sorted(_shipped.items()):
    _extra = sorted(_members - _expected.get(_tag, set()))
    if _extra:
        fail(f"{_tag} lists {_extra[0]} and {len(_extra) - 1} more that model no member of it, "
             f"so a biome carries a tag its model does not")
print(f"the tags           : {len(_ours)} biome(s) of ours in {len(_expected)} tag(s), each the tags of "
      f"the biome it models, read from the game's own files both ways")

if problems:
    print(f"{len(problems)} problem(s):")
    for problem in problems:
        print(f"  - {problem}")
    sys.exit(1)
print("all wilds checks passed")
