#!/usr/bin/env python3
"""
Check the library's own invariants.

Run from the repo root:   python3 validate.py

The library ships no models, no recipes and no loot tables, so none of core's
resource cross-checks apply. What it does have is one property worth more than
all of them:

    the library must not know about any mod built on it.

That is the whole reason this repo is separate. A single import of
com.elysium.core would compile fine here — core is not on the classpath, so it
would not even compile — but the equivalent mistakes that *do* compile are easy
to make: naming a content mod in the manifest, hard-coding an item id, checking
for a specific mod being loaded. Each of those is checked below.

The rest is arithmetic the code documents about itself and cannot verify at
runtime: the element ring is closed, the stats are the twelve they claim to be,
and every translation key the engine emits has an entry to emit.
"""
import json
import os
import pathlib
import re
import sys

WORK = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(WORK, "src/main/java/com/elysium/lib")
RES = os.path.join(WORK, "src/main/resources")

problems = []


def fail(msg):
    problems.append(msg)


def read(rel):
    return open(os.path.join(SRC, rel), encoding="utf-8").read()


# Comments and string-literal *documentation* are not code. A javadoc example
# showing an add-on how a race id is stored ("elysium:imperial") is the library
# doing its job, not the library depending on a content mod — so the checks
# below read code with the comments stripped.
COMMENT = re.compile(r"//[^\n]*|/\*.*?\*/", re.S)


def sources(strip_comments=True):
    for dirpath, _, filenames in os.walk(SRC):
        for filename in sorted(filenames):
            if filename.endswith(".java"):
                path = os.path.join(dirpath, filename)
                text = open(path, encoding="utf-8").read()
                if strip_comments:
                    text = COMMENT.sub(" ", text)
                yield os.path.relpath(path, SRC), text


if not os.path.isdir(SRC):
    print("This is not the elysium-lib tree: src/main/java/com/elysium/lib is missing.")
    sys.exit(1)

# ---------------------------------------------------------------------------
# 1. The library knows about no mod built on it
# ---------------------------------------------------------------------------
#
# Checked first because everything else is worthless if this is false. A
# library that reaches into a content mod is not a library; it is one mod in
# two jars, and the second Elysium mod will discover that the hard way.

for path, text in sources():
    for hit in re.findall(r"com\.elysium\.(?!lib\b)\w+", text):
        fail(f"{path}: library source refers to {hit} - the library must not "
             f"know about any mod built on it")

    # An id in the elysium namespace is a content mod's id. The two exceptions
    # are the translation-key prefixes, which are deliberately shared so that a
    # player sees "Vitality" and not "elysiumlib.stat.vitality" — those are
    # string prefixes, not resource ids.
    for hit in re.findall(r'"elysium:([a-z_/]+)"', text):
        fail(f"{path}: hard-coded content id \"elysium:{hit}\" - the library "
             f"ships no items and must not name any")

    for hit in re.findall(r'isLoaded\("(\w+)"\)', text):
        fail(f"{path}: library checks for mod \"{hit}\" being loaded - "
             f"integrations belong to the mod that wants them")

print(f"library sources    : {sum(1 for _ in sources())} files, no content references")

# ---------------------------------------------------------------------------
# 2. The manifest depends on nothing but the platform
# ---------------------------------------------------------------------------
manifest = open(os.path.join(RES, "META-INF/neoforge.mods.toml"), encoding="utf-8").read()
declared = re.findall(r'modId="([\w$\{\}]+)"', manifest)
allowed = {"${mod_id}", "neoforge", "minecraft"}
for mod in declared:
    if mod not in allowed:
        fail(f"manifest declares a dependency on \"{mod}\" - the library "
             f"depends on the platform and nothing else")

# ---------------------------------------------------------------------------
# 3. The element ring is closed
# ---------------------------------------------------------------------------
#
# Five elements, each beating exactly two and beaten by exactly two. This was
# ordinal arithmetic on an enum and is now a declared graph, which is the whole
# point — and also exactly the change that could silently produce a lopsided
# ring nobody notices until a fight feels wrong.

elements_src = read("element/ElysiumElements.java")
# Each element is registered against a shared *_ID constant, so read the
# constants first and then the ring in terms of them. Reading the constants
# rather than assuming the names match is what catches the copy-paste where
# KINETIC is registered against DIMENSIONAL_ID.
ids = dict(re.findall(
    r"ResourceLocation (\w+)_ID = id\(\"(\w+)\"\)", elements_src))

beats = {}
for constant, targets in re.findall(
        r"ElysiumElement\.register\(\s*(\w+)_ID,.*?Set\.of\((.*?)\)\s*,",
        elements_src, re.S):
    name = ids.get(constant, constant.lower())
    beats[name] = {ids.get(t, t.lower())
                   for t in re.findall(r"(\w+)_ID", targets)}

if len(beats) != 5:
    fail(f"expected 5 canonical elements, read {len(beats)}: {sorted(beats)} - "
         f"the ring check cannot read ElysiumElements")
else:
    for name, targets in beats.items():
        if len(targets) != 2:
            fail(f"{name} beats {len(targets)} elements, not 2: {sorted(targets)}")
        if name in targets:
            fail(f"{name} beats itself")
        for target in targets:
            if target not in beats:
                fail(f"{name} beats \"{target}\", which is not a canonical element")
            elif name in beats.get(target, ()):
                fail(f"{name} and {target} both beat each other - "
                     f"the advantage would cancel and neither would matter")
    beaten = {name: sum(1 for t in beats.values() if name in t) for name in beats}
    lopsided = {n: c for n, c in beaten.items() if c != 2}
    if lopsided:
        fail(f"elements are not beaten by exactly two others: {lopsided} - "
             f"the ring is lopsided and some elements are strictly better")
    if not problems:
        print(f"element ring       : {len(beats)} elements, each beats 2 and is beaten by 2")

# ---------------------------------------------------------------------------
# 4. The stats are the twelve they claim to be
# ---------------------------------------------------------------------------
stats_src = read("stats/ElysiumStats.java")
stat_ids = re.findall(r"ElysiumStat\.(?:flat|curve)\(id\(\"(\w+)\"\)", stats_src)
if len(stat_ids) != 12:
    fail(f"expected 12 canonical stats, read {len(stat_ids)}: {sorted(stat_ids)}")
if len(set(stat_ids)) != len(stat_ids):
    duplicates = sorted({s for s in stat_ids if stat_ids.count(s) > 1})
    fail(f"duplicate stat ids: {duplicates}")

# A curved stat's halfway point must be positive, or the curve divides by zero
# at the origin and every value comes out as 1.0 — full effect for free.
for name, halfway in re.findall(
        r"ElysiumStat\.curve\(id\(\"(\w+)\"\),\s*[\w.]+,\s*([\d.]+)F", stats_src):
    if float(halfway) <= 0.0:
        fail(f"stat {name} has a halfway point of {halfway} - a curve needs a "
             f"positive one or it returns full effect at zero points")

if len(stat_ids) == 12 and len(set(stat_ids)) == 12:
    print(f"stats registered   : {len(stat_ids)}")

# ---------------------------------------------------------------------------
# 5. Every key the engine emits has something to emit
# ---------------------------------------------------------------------------
lang_path = os.path.join(RES, "assets/elysiumlib/lang/en_us.json")
lang = json.load(open(lang_path, encoding="utf-8"))

for name in stat_ids:
    for suffix in ("", ".desc"):
        key = f"elysium.stat.{name}{suffix}"
        if key not in lang:
            fail(f"missing lang key: {key}")

for name in beats:
    key = f"elysium.element.{name}"
    if key not in lang:
        fail(f"missing lang key: {key}")

# Literal keys, for everything not built by concatenation.
literal = set()
for _path, text in sources():
    literal |= set(re.findall(r'"(elysium\.[a-z_.]+)"', text))
for key in sorted(literal):
    if key not in lang and not key.endswith("."):
        fail(f"missing lang key: {key} (referenced literally in the sources)")

# Causes are the one place this project stores a string that becomes half of a
# translation key at runtime. The literal scan above cannot see them, because
# nothing anywhere writes "elysium.cause.ore" — the key is assembled from a
# prefix in ElysiumFile and a constant in ElysiumStanding. So a cause added
# without a lang entry would ship, and would render in the archive as the raw
# key, which is the exact failure mode this file exists to catch.
standing_src = COMMENT.sub(" ", read("standing/ElysiumStanding.java"))
causes = re.findall(r'String CAUSE_\w+ = "([a-z_]+)";', standing_src)
if not causes:
    fail("no CAUSE_ constants found in ElysiumStanding — either they were "
         "removed or they are no longer declared as String constants, and the "
         "archive's causes are unchecked either way")
for cause in causes:
    key = f"elysium.cause.{cause}"
    if key not in lang:
        fail(f"missing lang key: {key} (assembled from ElysiumStanding's "
             f"CAUSE_ constants, so the literal scan cannot see it)")

# And the other direction: a cause constant nothing passes is dead weight that
# reads as coverage.
used = set()
for module in ("elysium-lib", "elysium-core", "elysium-mobs", "elysium-npcs",
               "elysium-trinkets", "elysium-dungeons"):
    root = os.path.join(os.path.dirname(WORK), module, "src/main/java")
    for dirpath, _, filenames in os.walk(root):
        for filename in filenames:
            # ElysiumStanding.java is where they are declared. Counting it as a
            # user makes every constant self-justifying, which is how the first
            # version of this check managed to be unable to fail.
            if filename.endswith(".java") and filename != "ElysiumStanding.java":
                text = open(os.path.join(dirpath, filename), encoding="utf-8").read()
                used |= set(re.findall(r"CAUSE_(\w+)", COMMENT.sub(" ", text)))
declared = set(re.findall(r"String CAUSE_(\w+) =", standing_src))
orphans = sorted(name for name in declared - used if name != "UNKNOWN")
if orphans:
    fail(f"CAUSE_ constants nothing ever files: {orphans} — an archive that "
         f"can never show a cause is worse than one that does not list it")

# ---------------------------------------------------------------------------
# Everything on the character sheet explains itself on hover
# ---------------------------------------------------------------------------
#
# The twelve stat descriptions existed in the language file for a long time and
# were never shown anywhere — which is the failure this section is really for.
# A description nothing renders is indistinguishable from no description at all,
# and the only way to notice is to open the screen and hover, which nobody does
# while adding a stat.
screen_src = COMMENT.sub(" ", read("screen/ElysiumCharacterScreen.java"))
ui_src = COMMENT.sub(" ", read("client/ElysiumUI.java"))

if "ElysiumUI.tooltip(" not in screen_src:
    fail("the character screen never draws a tooltip, so the twelve stat "
         "descriptions in the language file are text nothing renders")
if "stat.getDescription()" not in screen_src:
    fail("the stats tab does not pass stat.getDescription() to anything — the "
         "hover would explain the stat's name back to the reader")

# The tooltip has to be built from this interface's own vocabulary. Vanilla's
# renderTooltip is a textured nine-slice with its own purple gradient border,
# and one of those popping up over an Elysium panel is the single most visible
# way to break the theming these screens exist for.
if "renderTooltip" in screen_src or "renderTooltip" in ui_src:
    fail("something calls vanilla's renderTooltip. It is a textured nine-slice "
         "with its own border and would be the one element on these screens "
         "that is not ElysiumPalette")
if "ElysiumPalette" not in ui_src.split("public static void tooltip(")[-1][:1200]:
    fail("ElysiumUI.tooltip does not use ElysiumPalette — the tooltip would not "
         "follow the generated theme the rest of the interface is checked "
         "against")

# One wrapping rule, not two. A tooltip has to size its panel before drawing
# the text, and the obvious way to do that is a second copy of the wrap loop —
# which drifts, and then the last line of long tooltips spills out of the box
# for whichever strings happen to be long enough.
if ui_src.count("font().width(candidate)") != 1:
    fail(f"the line-wrapping rule appears "
         f"{ui_src.count('font().width(candidate)')} times in ElysiumUI. A "
         f"tooltip that measures with one copy and draws with another sizes "
         f"its panel wrong as soon as they drift")

print(f"lang keys          : {len(lang)}")
print(f"stat tooltips      : {len(stat_ids)} stats, each with a name and a "
      f"description the screen renders on hover")
print(f"standing causes    : {len(causes)} declared, all translated, all filed")

# ---------------------------------------------------------------------------
# 6. All JSON parses
# ---------------------------------------------------------------------------
count = 0
for dirpath, _, filenames in os.walk(RES):
    for filename in filenames:
        if filename.endswith(".json"):
            count += 1
            try:
                json.load(open(os.path.join(dirpath, filename), encoding="utf-8"))
            except Exception as exc:
                fail(f"invalid JSON {os.path.join(dirpath, filename)}: {exc}")
print(f"json files parsed  : {count}")

# ---------------------------------------------------------------------------
# 7. The generated palette is the palette that was designed against
# ---------------------------------------------------------------------------
#
# ElysiumPalette.java is generated by ui/palette.py, and the mockups that the
# interface was reviewed and approved from import that same file. If someone
# edits the Java by hand, the game and the previews part company silently —
# every screenshot that was signed off is then a picture of a build that no
# longer exists. So the file is regenerated in memory and compared.
palette_tool = os.path.join(WORK, "ui", "palette.py")
palette_java = os.path.join(
    WORK, "src/main/java/com/elysium/lib/client/ElysiumPalette.java")
if os.path.exists(palette_tool) and os.path.exists(palette_java):
    import importlib.util
    import tempfile

    spec = importlib.util.spec_from_file_location("elysium_palette", palette_tool)
    palette_module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(palette_module)

    for name, surface, ratio, floor in palette_module.check_contrast():
        fail(f"contrast: {name} on {surface} is {ratio}:1, below {floor}")

    with tempfile.TemporaryDirectory() as tmp:
        regenerated = pathlib.Path(tmp) / "ElysiumPalette.java"
        palette_module.write_java(regenerated)
        current = regenerated.read_text() == open(palette_java, encoding="utf-8").read()
    if not current:
        fail("ElysiumPalette.java does not match ui/palette.py — "
             "run `python3 ui/palette.py` (never edit the Java by hand)")
    else:
        print(f"palette            : {len(palette_module.PALETTE)} colours, "
              f"{len(palette_module.METRICS)} metrics, generated file is current")
else:
    fail("ui/palette.py or the generated ElysiumPalette.java is missing")

# ---------------------------------------------------------------------------
# 8. Roaming bands arrive consistently, and cannot lock themselves out
# ---------------------------------------------------------------------------
#
# ElysiumSquads is a tuning table that documents its own numbers in a javadoc
# table and then computes them somewhere else. That is exactly the shape of
# claim this file exists to check: the prose and the arithmetic drift apart
# silently, and nothing at runtime ever compares them.
#
# Two of these are invariants rather than tuning, and they are the reason the
# section is here at all:
#
#   * the crowd cap must be strictly larger than the largest band, or one full
#     band permanently blocks every later one and the feature switches itself
#     off for anybody who runs instead of fighting — the failure looks exactly
#     like "it just stopped working";
#   * the longest legal gap has to bound the reschedule sanity check in
#     ElysiumSquadHandler, which reads GAP_BASE + GAP_SPREAD to decide whether a
#     stored appointment is nonsense. If a gap could exceed that sum, a
#     legitimate appointment would be thrown away every time it was made.
squads = read("entity/ElysiumSquads.java")
squads_code = COMMENT.sub(" ", squads)


def constant(text, name, kind="int"):
    found = re.search(
        rf"public static final {kind} {name}\s*=\s*([^;]+);", text)
    if not found:
        fail(f"ElysiumSquads.{name} is missing")
        return None
    # Java's float and double suffixes are not Python's.
    literal = re.sub(r"(\d)[FfDdLl]\b", r"\1", found.group(1))
    return eval(literal)  # noqa: S307 - arithmetic only


GAP_BASE = constant(squads_code, "GAP_BASE")
GAP_SPREAD = constant(squads_code, "GAP_SPREAD")
MIN_SIZE = constant(squads_code, "MIN_SIZE")
NEARBY_CAP = constant(squads_code, "NEARBY_CAP")
MIN_RANGE = constant(squads_code, "MIN_RANGE", kind="double")
MAX_RANGE = constant(squads_code, "MAX_RANGE", kind="double")

if None not in (GAP_BASE, GAP_SPREAD, MIN_SIZE, NEARBY_CAP, MIN_RANGE, MAX_RANGE):
    # The table in gapFor's javadoc, in ticks, as prose claims it.
    claimed = {
        0: (20 * 60 * 2, 20 * 60 * 4),
        1: (20 * 90, 20 * 60 * 3),
        2: (20 * 60, 20 * 60 * 2),
        3: (20 * 30, 20 * 60),
    }
    for band, (low, high) in claimed.items():
        # gapFor: (GAP_BASE + rand(GAP_SPREAD)) * (4 - band) / 4
        actual_low = (GAP_BASE * (4 - band)) // 4
        actual_high = ((GAP_BASE + GAP_SPREAD - 1) * (4 - band)) // 4
        if actual_low != low or abs(actual_high - high) > 20:
            fail(f"squads: band {band} gap is {actual_low}-{actual_high} ticks, "
                 f"but gapFor's table says {low}-{high}")

    # Monotonic: more standing is never quieter.
    gaps = [(GAP_BASE * (4 - b)) // 4 for b in range(4)]
    if gaps != sorted(gaps, reverse=True):
        fail(f"squads: gaps do not shorten with band: {gaps}")
    if gaps[3] <= 0:
        fail("squads: band 3 has a zero-length gap — bands would arrive every check")

    # sizeFor: MIN_SIZE .. MIN_SIZE + (band + 1) // 2
    sizes = {b: (MIN_SIZE, MIN_SIZE + (b + 1) // 2) for b in range(4)}
    largest = max(high for _, high in sizes.values())
    if MIN_SIZE < 2:
        fail(f"squads: MIN_SIZE is {MIN_SIZE} — a band of one is a wanderer")
    if largest > 4:
        fail(f"squads: largest band is {largest}, which is no longer 'small'")
    if NEARBY_CAP <= largest:
        fail(f"squads: NEARBY_CAP {NEARBY_CAP} is not above the largest band "
             f"{largest} — one band would block every later one forever")
    if MIN_RANGE >= MAX_RANGE:
        fail(f"squads: MIN_RANGE {MIN_RANGE} is not below MAX_RANGE {MAX_RANGE}")

    # The handler's sanity bound must be the longest gap this table can produce.
    handler = read("event/ElysiumSquadHandler.java")
    if "ElysiumSquads.GAP_BASE + ElysiumSquads.GAP_SPREAD" not in handler:
        fail("squads: ElysiumSquadHandler no longer bounds a stored appointment "
             "by GAP_BASE + GAP_SPREAD, so a clock that went backwards would "
             "switch the feature off for that player with nothing logged")

    # The tag is one literal, read by two systems in two modules. A second
    # spelling of it would break despawning or clearance and neither would say
    # anything.
    tags = re.findall(r'"elysium_squad"', squads)
    if len(tags) != 1:
        fail(f"squads: the tag literal appears {len(tags)} times in "
             f"ElysiumSquads.java — it must be written once, as TAG")

    # Ambient bands must not be made permanent. setPersistenceRequired here
    # would reintroduce exactly the leak the tag exists to prevent, and would
    # do it silently: the mobs would simply stop despawning.
    if "setPersistenceRequired" in squads_code:
        fail("squads: send() calls setPersistenceRequired — roaming bands must "
             "be allowed to despawn or a long session strews the world with them")

    print(f"roaming bands      : gaps {gaps[0]}-{gaps[3]} ticks by band, "
          f"{MIN_SIZE}-{largest} per band, cap {NEARBY_CAP}, one tag literal")

# ---------------------------------------------------------------------------
# 9. The claim floor is §A.2's formula
# ---------------------------------------------------------------------------
#
#   Claim floor   min(70, 8 + 2 x sqrt(totalStructureVoxels/100))
#
# Transcribed from the specification, so it is worth re-deriving here rather
# than trusting that the Java says what the javadoc quoting it says. A formula
# that is subtly wrong in the fourth decimal place is invisible; one that is
# wrong in the constant is a base that never becomes politically expensive.
import math

claim = COMMENT.sub(" ", read("claim/ElysiumClaim.java"))
BASE_FLOOR = constant(claim, "BASE_FLOOR")
MAX_FLOOR = constant(claim, "MAX_FLOOR")
RADIUS = constant(claim, "DEFAULT_RADIUS")

if None not in (BASE_FLOOR, MAX_FLOOR, RADIUS):
    if BASE_FLOOR != 8:
        fail(f"§A.2's claim floor starts at 8; BASE_FLOOR is {BASE_FLOOR}")
    if MAX_FLOOR != 70:
        fail(f"§A.2 caps the claim floor at 70; MAX_FLOOR is {MAX_FLOOR}")

    # The gap that carries the design argument: building can make you Marked
    # and must never make you Hunted, because a five-wave siege ending in a
    # Praetor should be something you provoked, not something you constructed.
    # 75 is the bottom of Hunted, from ElysiumStanding's own bands.
    if MAX_FLOOR >= 75:
        fail(f"MAX_FLOOR {MAX_FLOOR} reaches the Hunted band - building alone "
             f"would schedule five-wave register actions")

    # Tie the derivation below to the Java, or this section grades its own
    # homework: everything after this point tests a Python transcription of
    # §A.2, and would pass unchanged if ElysiumClaim.floorFor rounded the other
    # way, squared instead of rooted, or divided by ten.
    java_floor = re.search(r"int floorFor\(int structures\)\s*\{(.*?)\n    \}",
                           claim, re.S)
    if not java_floor:
        fail("ElysiumClaim.floorFor is missing")
    else:
        body = re.sub(r"\s+", "", java_floor.group(1))
        for fragment, why in (
            ("2.0D*Math.sqrt(structures/100.0D)",
             "§A.2's 2 x sqrt(structures/100)"),
            ("BASE_FLOOR+", "§A.2's leading 8"),
            ("Math.min(MAX_FLOOR", "§A.2's cap"),
            ("Math.floor(", "flooring rather than rounding, which is what "
                            "makes the table below reproducible"),
        ):
            if fragment.replace(" ", "") not in body:
                fail(f"ElysiumClaim.floorFor no longer contains {why}")

    def spec_floor(n):
        if n <= 0:
            return BASE_FLOOR
        return int(min(MAX_FLOOR, math.floor(BASE_FLOOR + 2.0 * math.sqrt(n / 100.0))))

    # The curve, at the sizes a player actually passes through.
    for structures, expected in ((0, 8), (100, 10), (2500, 18), (10000, 28),
                                 (96000, 69), (1000000, 70)):
        if spec_floor(structures) != expected:
            fail(f"claim floor at {structures} structures is "
                 f"{spec_floor(structures)}, expected {expected}")

    # Monotonic and bounded, which the square root guarantees and a typo does
    # not.
    previous = -1
    for structures in range(0, 200000, 997):
        value = spec_floor(structures)
        if value < previous:
            fail(f"claim floor falls at {structures} structures")
            break
        if value > MAX_FLOOR:
            fail(f"claim floor exceeds its cap at {structures} structures")
            break
        previous = value

    # A claim is a cylinder: territory is a footprint, and a sphere would put a
    # mine directly under its own beacon outside the claim.
    contains = re.search(r"boolean contains\([^)]*\)\s*\{(.*?)\n    \}", claim, re.S)
    if not contains:
        fail("ElysiumClaim.contains is missing")
    elif "getY()" in contains.group(1):
        fail("ElysiumClaim.contains measures vertically - a claim is a "
             "cylinder, or a mine below its own beacon is outside it")

    print(f"claims             : §A.2 floor {BASE_FLOOR}..{MAX_FLOOR} verified "
          f"at 6 sizes, monotonic, radius {RADIUS} cylinder")

print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all library checks passed")
