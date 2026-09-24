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
HARNESS_MAIN = "com.elysium.wilds.rules.WildHarness"
CHECKER = os.path.join(ROOT, "check_worldgen.py")

#: Two million checks over a million climates run in a few seconds.
HARNESS_SECONDS = 180
NS = "elysiumwilds"

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
    """Every run of whitespace as one space, so a rule can name a whole statement."""
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

if not os.path.isfile(HARNESS):
    fail("there is no harness, so whether a claim inside another claim can generate "
         "is an opinion")
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
                     f"something in the rules can loop forever")
            if ran is None:
                pass
            elif ran.returncode != 0:
                fail("the wilds harness failed:\n" + ran.stdout.strip()[:1600])
            else:
                print("the arithmetic     : " + ran.stdout.strip().splitlines()[-1])

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
            fail("the generator does not run:\n" + built.stderr.strip()[:1200])
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
                        elif open(fresh, "rb").read() != open(shipped, "rb").read():
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

print("the seam           : the rules pointed at every dimension file in the "
      "repository, a world with no row an error rather than a pass, and every "
      "register on the bus")

# ---------------------------------------------------------------------------

print("-" * 70)
if problems:
    print(f"{len(problems)} problem(s):")
    for problem in problems:
        print(f"  - {problem}")
    sys.exit(1)
print("all wilds checks passed")
