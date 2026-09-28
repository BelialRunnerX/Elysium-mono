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
import tempfile

WORK = os.path.dirname(os.path.abspath(__file__))


def strip_comments(text):
    """Java source with comments removed.

    Checks in this repository have twice passed on deliberately broken code
    because the phrase they searched for appeared in the javadoc explaining the
    rule. Prose about a guard is not a guard.
    """
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", " ", text)


def squashed(text):
    """One method's source with every run of whitespace made one space.

    A rule that names a whole statement has to survive the statement being
    re-wrapped, or it fails the first time somebody tidies the file and then
    gets deleted rather than fixed.
    """
    return re.sub(r"\s+", " ", text)


def body_of(text, signature):
    """One method's source, comments stripped, by its declaration.

    Brace-matched from the declaration, so a check can be scoped to the method
    it is about rather than to the file. A file-wide substring search finds the
    same words in the neighbouring method and passes on code that has none of
    them — which has happened here three times.
    """
    start = text.find(signature)
    if start < 0:
        return None
    depth = 0
    for index in range(text.find("{", start), len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[start:index + 1]
    return None


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
                yield os.path.relpath(path, SRC).replace(os.sep, "/"), text


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
# An undeclared player cannot be hurt
# ---------------------------------------------------------------------------
#
# The declaration screen is modal and refuses ESC, so from the first tick of a
# new world until Confirm the player can neither move nor fight. A beast found
# one at a night spawn under the screen and killed them (28 Sep 2026). The
# shield is a cancelled damage event until a race and a calling are recorded,
# and it ends the moment they are.
_shield_path = os.path.join(SRC, "event/ElysiumUndeclaredShield.java")
shield_src = COMMENT.sub(" ", read("event/ElysiumUndeclaredShield.java")) if os.path.isfile(_shield_path) else ""
if not shield_src:
    fail("event/ElysiumUndeclaredShield.java is gone, so a new player is helpless "
         "under the declaration screen and whatever finds them there has them")
else:
    if "@EventBusSubscriber(modid = ElysiumLib.MODID)" not in shield_src             or "LivingIncomingDamageEvent" not in shield_src:
        fail("the undeclared shield is not subscribed to incoming damage, so it "
             "shields nothing")
    if "!ElysiumCharacter.hasChosen(player)" not in shield_src:
        fail("the undeclared shield no longer asks whether the player has chosen, "
             "so it either shields everyone forever or nobody")
    if "event.setCanceled(true)" not in shield_src:
        fail("the undeclared shield sees the damage and lets it through")
    print("the undeclared     : a player under the declaration screen takes no damage "
          "until a race and a calling are recorded")

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

# ---------------------------------------------------------------------------
# Villager memory
# ---------------------------------------------------------------------------
#
# Four numbers decide how this system feels, and all four are in a table in
# ElysiumMemoryKinds. The failure mode is not a crash: it is a villager who
# stops trading with a player who hit them once by accident, or one who watches
# a murder and sells you bread the next morning. Both read as the mod being
# broken, and neither shows up anywhere except in play.
#
# So the relationships between the numbers are checked here, against the Java
# rather than against a transcription of it.

KINDS = os.path.join(SRC, "social/ElysiumMemoryKinds.java")
MEMORIES = os.path.join(SRC, "social/ElysiumMemories.java")

if not os.path.isfile(KINDS) or not os.path.isfile(MEMORIES):
    fail("the villager memory system is missing")
else:
    kind_source = open(KINDS, encoding="utf-8").read()
    memory_source = open(MEMORIES, encoding="utf-8").read()

    kinds = {}
    for block in re.finditer(
            r'builder\(id\("(\w+)"\)\)\s*(.*?)\.register\(\)', kind_source, re.S):
        name, body = block.group(1), block.group(2)
        weight = re.search(r"\.weight\((-?\d+)\)", body)
        half = re.search(r"\.halfLife\((.*?)\)", body)
        if not weight or not half:
            fail(f"memory kind {name} has no weight or no half-life")
            continue
        kinds[name] = (int(weight.group(1)), eval(half.group(1), {"MINUTE": 20 * 60}))

    refusal = re.search(r"int REFUSAL = (-?\d+);", memory_source)
    depth = re.search(r"int DEPTH = (\d+);", memory_source)
    if not kinds or not refusal or not depth:
        fail("could not read the memory table out of the Java — every check "
             "below would pass by finding nothing")
    else:
        REFUSAL = int(refusal.group(1))
        DEPTH = int(depth.group(1))
        harms = {n: w for n, (w, _) in kinds.items() if w < 0}
        helps = {n: w for n, (w, _) in kinds.items() if w > 0}

        if not harms or not helps:
            fail("the memory table is all harm or all help — a relationship "
                 "that can only move one way is not a relationship")

        # 1. No single incident closes the shop. A villager who stops trading
        #    over one misclick is a bug as far as a player is concerned.
        for name, weight in harms.items():
            if weight <= REFUSAL:
                fail(f"one {name} ({weight}) is on its own enough to reach the "
                     f"refusal threshold ({REFUSAL}). A single incident must "
                     f"never close the shop — a misclick would end trading with "
                     f"that villager forever")

        # 2. But the worst one, twice, does. Otherwise refusal is unreachable in
        #    practice and the threshold is decoration.
        worst = min(harms.values())
        if worst * 2 > REFUSAL:
            fail(f"two of the worst incident ({worst} each) still does not "
                 f"reach refusal ({REFUSAL}), so nothing a player can plausibly "
                 f"do ever makes a villager refuse them")

        # 3. Harm outweighs help, or apologising is free.
        if abs(worst) <= max(helps.values()):
            fail(f"the best good memory ({max(helps.values())}) is worth as "
                 f"much as the worst bad one ({worst}). One trade would undo a "
                 f"killing")

        # 4. Everything fades, and harm fades slower than help.
        slowest_help = max(half for name, (w, half) in kinds.items() if w > 0)
        slowest_harm = max(half for name, (w, half) in kinds.items() if w < 0)
        if slowest_harm <= slowest_help:
            fail(f"harm fades at least as fast as help ({slowest_harm} vs "
                 f"{slowest_help} ticks). Being forgiven should take longer "
                 f"than being thanked wears off")

        # 5. The ledger has to be able to hold the worst case. If refusal takes
        #    more incidents than a villager can remember, the oldest drops off
        #    as the newest arrives and the threshold can never be crossed.
        # ceil(|refusal| / |worst|), written out because the negation
        # trick got it wrong twice: the first version computed -1.
        needed = -(-abs(REFUSAL) // abs(worst)) if worst else 0
        if needed > DEPTH:
            fail(f"reaching refusal needs {needed} incidents and a villager "
                 f"only remembers {DEPTH}. The oldest would fall off the end "
                 f"before the threshold was crossed")

        # What it takes to come back, reported rather than asserted.
        best_help = max(helps.values())
        trades = -(-abs(worst) // best_help)
        halvings = 0
        remaining = abs(worst)
        while remaining > abs(worst) / 4:
            remaining /= 2
            halvings += 1
        print(f"villager memory    : {len(kinds)} kinds, refusal at {REFUSAL}, "
              f"ledger {DEPTH} deep")
        print(f"                     worst incident {worst}; "
              f"{trades} best-case good turns to undo one, "
              f"or {halvings} half-lives "
              f"({slowest_harm // (20 * 60)} min each) to fade to a quarter")

# ---------------------------------------------------------------------------
# The gear gate
# ---------------------------------------------------------------------------
#
# The engine used to ask `stack.getItem() instanceof ElysiumSocketable` in
# thirteen places. That is a test only classes compiled against this library can
# pass, so a vanilla iron sword was not *weaker* than an Elysium one — it was
# outside the game's systems entirely, with no way in. ElysiumGear replaced it
# with a question any item can answer.
#
# One instanceof put back anywhere returns that call site to the old behaviour,
# and returns it silently: the code compiles, Elysium's own items still work,
# and only foreign gear quietly stops counting at that one point. So the rule is
# checked rather than remembered.

GATE = re.compile(r"instanceof\s+ElysiumSocketable")
RESOLVER = os.path.join("src", "main", "java", "com", "elysium", "lib",
                        "item", "ElysiumGear.java")

java_files = sorted(pathlib.Path(WORK).glob("src/main/java/**/*.java"))
gates = []
for path in java_files:
    relative = os.path.relpath(str(path), WORK)
    if relative == RESOLVER:
        continue                          # the one place the test belongs
    for number, line in enumerate(path.read_text(encoding="utf-8").split("\n"), 1):
        stripped = line.strip()
        if GATE.search(line) and not stripped.startswith(("*", "//")):
            gates.append(f"{relative}:{number}")

for where in gates:
    fail(f"{where} asks `instanceof ElysiumSocketable` directly. That is the "
         f"gate ElysiumGear exists to replace, and at that call site a vanilla "
         f"or modded weapon silently stops being gear again. Use "
         f"ElysiumGear.of, .is, .armour or .weapon")

resolver = os.path.join(WORK, RESOLVER)
if not os.path.isfile(resolver):
    fail("ElysiumGear is missing — nothing resolves foreign gear")
else:
    source = open(resolver, encoding="utf-8").read()

    # Line by line with comments skipped, not a substring search over the file.
    # The first version was a substring search, and a deliberate test walked
    # straight through it: commenting the call out left the text in place, so
    # the check still found a "caller" and passed while the cache was never
    # cleared again. A check that a comment can satisfy is not a check.
    def calls(path, expression):
        for line in path.read_text(encoding="utf-8").split("\n"):
            stripped = line.strip()
            if stripped.startswith(("*", "//", "/*")):
                continue
            if expression in line.split("//")[0]:
                return True
        return False

    callers = [p for p in java_files
               if os.path.relpath(str(p), WORK) != RESOLVER
               and calls(p, "ElysiumGear.invalidate()")]
    # The cache is only safe if something clears it. A material's ingredient is
    # a tag, so what an item is made of is a datapack question — a cache that
    # outlived a reload would answer with the old pack's opinion for the rest of
    # the session.
    if "invalidate" not in source:
        fail("ElysiumGear has no invalidate(), so its cache would survive a "
             "datapack reload that changed what an item is made of")
    elif not callers:
        fail("nothing calls ElysiumGear.invalidate(), so the resolution cache "
             "is never cleared and a /reload cannot change what an item is "
             "made of")
    elif not gates:
        print("gear gate          : no direct instanceof outside the resolver, "
              "cache cleared on reload")

# ---------------------------------------------------------------------------
# Loot categories (SPEC_CLONE P4.1)
# ---------------------------------------------------------------------------
#
# ElysiumGear answers "is this gear at all". That was enough while everything a
# piece of gear could receive applied to all gear equally, and it stopped being
# enough with affixes: "only on melee weapons" is a sentence the data has to be
# able to write, and a pool of bowstring affixes on a helmet is nonsense.
#
# ElysiumLootCategory is the finer question, asked the same way — not what class
# an item is, which only this mod's own items can answer, but what it says it
# is, what it descends from, and what it does. Three things are checked here,
# because none of them can be executed without a running game: every kind the
# resolver can return is a kind the tree knows, the cache is cleared when the
# gear cache is, and an item that names its own kind is asked first.

CATEGORY = os.path.join(WORK, "src/main/java/com/elysium/lib/item/ElysiumLootCategory.java")
TREE = os.path.join(WORK, "src/main/java/com/elysium/lib/item/ElysiumGearTree.java")
KINDED = os.path.join(WORK, "src/main/java/com/elysium/lib/item/ElysiumKinded.java")
if not os.path.isfile(TREE):
    fail("ElysiumGearTree is missing — four systems name kinds in it and the "
         "armory's property engine reads its rows from it")
elif not os.path.isfile(CATEGORY):
    fail("ElysiumLootCategory is missing — nothing can say what kind of thing a "
         "stack is, so an affix pool could only ever say 'any gear'")
elif not os.path.isfile(KINDED):
    fail("ElysiumKinded is missing — modular gear is a plain Item carrying "
         "parts, and without it a modular sword is not a sword to anything")
else:
    _tree_src = open(TREE, encoding="utf-8").read()
    _kinds = set(re.findall(r'\{"([a-z_]+)",\s*(?:"[a-z_]+"|null)\}', _tree_src))
    if len(_kinds) < 40:
        fail(f"only {len(_kinds)} kind(s) read out of the gear tree; it declares far more")
    _category_src = strip_comments(open(CATEGORY, encoding="utf-8").read())
    # Both shapes an answer takes: a plain return, and a switch arm, which is
    # how the armour slots are read.
    _returned = set(re.findall(r'return\s+"([a-z_]+)"\s*;', _category_src))
    _returned |= set(re.findall(r'->\s*"([a-z_]+)"\s*;', _category_src))
    _strays = sorted(k for k in _returned if k not in _kinds)
    if _strays:
        fail(f"ElysiumLootCategory answers with {_strays}, which the gear tree has "
             f"never heard of — a rule naming that kind could never be written, and "
             f"one naming its parent would miss the item entirely")
    _reached = sorted(_returned & _kinds)
    if "sword" not in _reached or "helmet" not in _reached:
        fail("the resolver answers for neither a plain sword nor a plain helmet; "
             "the vanilla shapes are the ones most of the game is made of")
    # A word boundary, not a substring: renaming the interface to
    # ElysiumKindedGone left the old name inside the new one, and a substring
    # check walked straight past a resolver that no longer asked anything.
    if not re.search(r"\bElysiumKinded\b", _category_src) or ".gearKind()" not in _category_src:
        fail("the resolver never asks an item what it is, so modular gear — where "
             "a sword and a pickaxe are the same class — could not be told apart")
    _kinded_first = _category_src.find("ElysiumKinded")
    _class_first = _category_src.find("instanceof SwordItem")
    if _kinded_first > _class_first >= 0:
        fail("the resolver reads an item's class before asking the item, so a piece "
             "of gear that names itself would be overruled by whatever class it had "
             "to extend to be equippable")
    _cat_callers = [p for p in java_files
                    if os.path.relpath(str(p), WORK) != os.path.relpath(CATEGORY, WORK)
                    and calls(p, "ElysiumLootCategory.invalidate()")]
    if "invalidate" not in _category_src:
        fail("ElysiumLootCategory has no invalidate(), so its cache would survive a "
             "reload that changed the answer")
    elif not _cat_callers:
        fail("nothing calls ElysiumLootCategory.invalidate(); the kind cache would "
             "outlive the tag reload that changed it, exactly as the gear cache would")
    else:
        print(f"loot categories    : {len(_kinds)} kinds in the tree, "
              f"{len(_reached)} resolved from vanilla shapes, items asked first, "
              f"cache cleared on reload")

# ---------------------------------------------------------------------------
# One item-effect engine, and the affixes on it (SPEC_CLONE P4.2)
# ---------------------------------------------------------------------------
#
# Silent Gear traits and Apotheosis affixes are the same idea wearing different
# words: a conditional effect attached to an item. Elysium's gems and runes are
# a third and fourth telling of it. Built four times, they would disagree about
# whether two sources of bleed stack, and a player could not reason about any
# of it.
#
# So there is one vocabulary and one set of handlers, and every attachment
# states its effects in those words. What is checked here is that it stayed
# that way: that the affixes are data rather than code, that every kind they
# use is one the engine reads, that no affix states a kind that can only be
# read while gear is built from parts, and that the shipped catalogue is what
# its generator produces.

AFFIX_DIR = os.path.join(WORK, "src/main/resources/data/elysiumlib/elysium_affixes")
EFFECT_DIR = os.path.join(WORK, "src/main/java/com/elysium/lib/effect")
KINDS_FILE = os.path.join(EFFECT_DIR, "ElysiumEffectKinds.java")
if not os.path.isdir(EFFECT_DIR) or not os.path.isfile(KINDS_FILE):
    fail("the effect engine is gone; traits and affixes would each need their own")
elif not os.path.isdir(AFFIX_DIR):
    fail("there are no affixes; the engine is built and only one thing feeds it")
else:
    _kinds_src = open(KINDS_FILE, encoding="utf-8").read()
    _vocabulary = dict((m[0], m[1] == "true")
                       for m in re.findall(r'm\.put\(\s*"?([a-z_]+)"?,\s*(true|false)\)', _kinds_src))
    # CODE is named by its constant rather than its string, so it is added by
    # hand: behaviour written in Java, never read off a finished item.
    if re.search(r'm\.put\(CODE,\s*false\)', _kinds_src):
        _vocabulary["code"] = False
    else:
        fail("the effect vocabulary no longer names 'code', so a trait whose behaviour "
             "is Java would read as a kind nothing answers to")
    if len(_vocabulary) < 19:
        fail(f"the effect vocabulary reads as {len(_vocabulary)} kind(s); it names far more")
    # Everything in the library that could read an effect — but NOT the
    # vocabulary file, which names every kind by definition. Including it made
    # the readership check below pass for any kind at all, which is a check
    # that cannot fail and therefore is not one.
    _engine = ""
    for _name in sorted(os.listdir(EFFECT_DIR)):
        if _name.endswith(".java") and _name != "ElysiumEffectKinds.java":
            _engine += strip_comments(open(os.path.join(EFFECT_DIR, _name), encoding="utf-8").read())
    for _package in ("affix", "item", "loot"):
        _dir = os.path.join(WORK, "src/main/java/com/elysium/lib", _package)
        for _name in sorted(os.listdir(_dir)):
            if _name.endswith(".java"):
                _engine += strip_comments(open(os.path.join(_dir, _name), encoding="utf-8").read())

    _affix_files = sorted(f for f in os.listdir(AFFIX_DIR) if f.endswith(".json"))
    if len(_affix_files) < 94:
        fail(f"only {len(_affix_files)} affix file(s); the catalogue lists 94")
    _lang = json.load(open(os.path.join(WORK, "src/main/resources/assets/elysiumlib/lang/en_us.json"),
                           encoding="utf-8"))
    _placements = {"prefix": 0, "suffix": 0}
    _used = set()
    _gates = set()
    for _file in _affix_files:
        _id = os.path.splitext(_file)[0]
        _affix = json.load(open(os.path.join(AFFIX_DIR, _file), encoding="utf-8"))
        _type = _affix.get("type")
        if _type not in _placements:
            fail(f"affix {_id} is a '{_type}', which is neither a prefix nor a suffix, "
                 f"so its name could go nowhere")
            continue
        _placements[_type] += 1
        if not _affix.get("on"):
            fail(f"affix {_id} names no kinds of gear, so it would roll on a potion")
        if _affix.get("weight", 0) <= 0:
            fail(f"affix {_id} weighs nothing and could never be drawn")
        if _affix.get("max_level", 0) < 1:
            fail(f"affix {_id} rolls at level 0, which is a name on an item that does nothing")
        if _affix.get("min_tier", 0) < 0:
            fail(f"affix {_id} asks for a negative world tier, which nothing can be below")
        _gates.add(_affix.get("min_tier", 0))
        if not _affix.get("effects"):
            fail(f"affix {_id} states no effects at all")
        for _effect in _affix.get("effects", []):
            _kind = _effect.get("type")
            _used.add(_kind)
            if _kind not in _vocabulary:
                fail(f"affix {_id} states a '{_kind}' effect, which is not a kind the "
                     f"engine has a word for")
            elif not _vocabulary[_kind]:
                fail(f"affix {_id} states '{_kind}', which is only read while a piece of "
                     f"gear is computed from its parts. An affix is rolled onto a finished "
                     f"item — the parts were added up long before — so it would do nothing")
        _key = f"elysium.affix.elysiumlib.{_id}"
        if _key not in _lang:
            fail(f"affix {_id} has no name; an item would be called by a raw key")
    for _key in ("elysium.affix.line", "elysium.affix.line.level"):
        if _key not in _lang:
            fail(f"{_key} has no translation, so the tooltip line would read as a raw key")
    if _placements["prefix"] == 0 or _placements["suffix"] == 0:
        fail("every affix sits on the same side of a name; an item could never have both")
    # A tier with nothing new in it is a tier a player reaches and does not feel.
    for _tier in range(0, 5):
        if _tier not in _gates:
            fail(f"no affix appears at world tier {_tier}, so reaching it would add nothing "
                 f"a player could see")
    if 0 not in _gates:
        fail("every affix is gated behind a tier; a Frontier player could find nothing at all")
    for _kind in sorted(_used):
        if f'"{_kind}"' not in _engine:
            fail(f"the affixes state a '{_kind}' effect and nothing in the library reads one. "
                 f"A kind only the armory reads is a kind an affix on a vanilla sword would "
                 f"state and nothing would act on")
    # And something has to fire the handlers for any item, not only modular
    # gear. This was the shape of the bug: every effect event opened with
    # `instanceof ArmoryGear`, so an affix on a vanilla iron sword registered
    # its effects, showed them in the tooltip, and did nothing.
    _events = os.path.join(EFFECT_DIR, "ElysiumEffectEvents.java")
    if not os.path.isfile(_events):
        fail("nothing in the library fires the effect handlers, so an affix on anything but "
             "modular gear would be a tooltip and no more")
    else:
        _events_src = strip_comments(open(_events, encoding="utf-8").read())
        if "ElysiumLootCategory.of(" not in _events_src:
            fail("the effect events ask something other than 'is this gear at all', so some "
                 "class of item is silently outside the engine again")
        if re.search(r"instanceof\s+Armory", _events_src):
            fail("the effect events test for a class from another module, which is the gate "
                 "this engine exists to replace")
        for _fired in ("extraDamage(", "onHit(", "negate(", "wielderEffects(", "selfRepair(",
                       "cancelEffects(", "magnet(", "miningSpeed(", "bonusDrops("):
            if _fired not in _events_src:
                fail(f"nothing fires ElysiumEffectHandlers.{_fired.rstrip('(')}, so that kind "
                     f"of effect is read from the data and never happens")

    # The catalogue is generated. Re-run it into a scratch directory and compare,
    # so an affix edited by hand is caught the way an edited trait is.
    import filecmp
    import shutil
    import subprocess as _sub
    _gen = os.path.join(WORK, "tools/gen_affixes.py")
    if not os.path.isfile(_gen):
        fail("tools/gen_affixes.py is gone; the affix files could drift from the table "
             "they were written from and nothing would say so")
    else:
        with tempfile.TemporaryDirectory() as _scratch:
            _run = _sub.run([sys.executable, _gen, _scratch], capture_output=True, text=True, cwd=WORK)
            if _run.returncode != 0:
                fail(f"tools/gen_affixes.py no longer runs: {_run.stderr.strip()[-300:]}")
            else:
                _diff = filecmp.dircmp(_scratch, AFFIX_DIR)
                _drift = sorted(_diff.left_only + _diff.right_only + _diff.diff_files)
                if _drift:
                    fail(f"the shipped affixes are not what tools/gen_affixes.py generates "
                         f"from its table: {_drift[:5]}")

    # A tier on an affix file that the pool never reads is a gate that is not a
    # gate: every affix would be available at the Frontier and the catalogue's
    # whole shape would be decoration.
    _cat_src = strip_comments(open(os.path.join(WORK,
        "src/main/java/com/elysium/lib/affix/ElysiumAffixCatalogue.java"), encoding="utf-8").read())
    if "allowedAt(" not in _cat_src:
        fail("the affix pool never asks whether an affix is allowed at this world tier, so "
             "every gate in the catalogue would let everything through")
    _roll_affix = strip_comments(open(os.path.join(WORK,
        "src/main/java/com/elysium/lib/affix/ElysiumAffixRoll.java"), encoding="utf-8").read())
    if "pool(kind, type, worldTier)" not in _roll_affix:
        fail("the roller does not pass the world tier it was given to the pool, so the gates "
             "in the catalogue would be read against something else — or against nothing")

    # The whole point: one set of handlers, fed by every attachment. If the
    # affixes register no source, they are a catalogue nothing ever reads.
    _sources = re.findall(r'ElysiumItemEffects\.register\("([^"]+)"', _engine)
    if "elysiumlib:affixes" not in _sources:
        fail("the affixes are never registered with the effect engine, so every effect "
             "they state is read by nothing")
    if "ElysiumItemEffects.of(" not in _engine:
        fail("nothing gathers effects through the engine; each attachment would be read "
             "separately, which is the four implementations this replaced")
    # A roller nothing can call is a page of code that compiles, validates and
    # does nothing in game. Three coded traits fell into exactly that hole this
    # phase and were caught by a rule like this one; the affix roller gets the
    # same rule rather than the same hole.
    _roll_src = ""
    _loot_dir = os.path.join(WORK, "src/main/java/com/elysium/lib/loot")
    if os.path.isdir(_loot_dir):
        for _name in sorted(os.listdir(_loot_dir)):
            if _name.endswith(".java"):
                _roll_src += strip_comments(open(os.path.join(_loot_dir, _name),
                                                 encoding="utf-8").read())
    if "ElysiumAffixRoll.roll(" not in _roll_src:
        fail("nothing reachable from data ever rolls an affix onto anything, so the "
             "catalogue, the pools and the roller are all written and all dead")
    if '"roll_affixes"' not in _roll_src or "ElysiumRollAffixesFunction.CODEC" not in _roll_src:
        fail("the affix loot function is not registered with its own codec, so a loot "
             "table naming it would fail to parse at load")
    _lib_main = strip_comments(open(os.path.join(WORK, "src/main/java/com/elysium/lib/ElysiumLib.java"),
                                    encoding="utf-8").read())
    if "ElysiumLoot.register(modEventBus)" not in _lib_main:
        fail("the library's loot types never reach the registry")

    # Injection (P5.5). A loot function only fires where a pack wrote it, so
    # without a global modifier the affixes would exist and nothing in an
    # unmodified world would ever carry one.
    if "IGlobalLootModifier" not in _roll_src:
        fail("nothing puts affixes on loot a pack did not ask about, so a vanilla chest at "
             "any world tier would hold exactly what it always held")
    _modifier = os.path.join(WORK, "src/main/resources/data/elysiumlib/loot_modifiers/affixes.json")
    _global = os.path.join(WORK, "src/main/resources/data/neoforge/loot_modifiers/global_loot_modifiers.json")
    if not os.path.isfile(_modifier):
        fail("the affix loot modifier has no data file, so its serializer is registered and "
             "nothing ever instantiates it")
    elif json.load(open(_modifier, encoding="utf-8")).get("type") != "elysiumlib:affixes":
        fail("the affix loot modifier names a serializer that is not the one registered")
    if not os.path.isfile(_global):
        fail("the loot modifier is never listed in global_loot_modifiers.json, which is the "
             "only thing that makes NeoForge load it at all")
    else:
        _listed = json.load(open(_global, encoding="utf-8"))
        if "elysiumlib:affixes" not in _listed.get("entries", []):
            fail("global_loot_modifiers.json does not list the affix modifier")
        if _listed.get("replace", False):
            fail("global_loot_modifiers.json replaces every other mod's modifiers instead of "
                 "adding to them")
    # The injector must add nothing: the loot economy stays whoever wrote the
    # table's to balance.
    _injector = os.path.join(WORK, "src/main/java/com/elysium/lib/loot/ElysiumAffixInjector.java")
    if os.path.isfile(_injector):
        _inj_src = strip_comments(open(_injector, encoding="utf-8").read())
        if re.search(r"generated\.add\(|generated\.addAll\(", _inj_src):
            fail("the affix injector adds items to a loot table. It exists to affix what is "
                 "already there; adding takes the pack author's loot economy away from them")
        if "ElysiumLootCategory.of(" not in _inj_src:
            fail("the injector does not check that what it is affixing is gear, so it would "
                 "roll affixes onto bread")
        # The guard, not the phrase: the same call appears again further down,
        # where the rarity component is set, and a bare search for it passed on
        # an injector whose guard had been deleted.
        if not re.search(r"\|\|\s*!ElysiumAffixEffects\.of\(stack\)\.isEmpty\(\)", _inj_src):
            fail("the injector overwrites affixes a table rolled itself, so a pack that said "
                 "what it wanted would be overruled")
# ---------------------------------------------------------------------------
# World tiers (SPEC_CLONE P5.1)
# ---------------------------------------------------------------------------
#
# The tier is derived from a player's own progression rather than tracked as a
# second one, and the library cannot know what dimensions exist — so worlds are
# declared by whoever adds them. Three things are checked, none of which can be
# executed without a running game: the library keeps no list of dimensions of
# its own, the floor a player has earned only ever rises and survives dying for
# it, and something actually records a visit.

TIER_DIR = os.path.join(WORK, "src/main/java/com/elysium/lib/tier")
if not os.path.isdir(TIER_DIR):
    fail("the world tier is gone; nothing gates what may drop by how far a player has got")
else:
    _tiers_src = strip_comments(open(os.path.join(TIER_DIR, "ElysiumTiers.java"),
                                     encoding="utf-8").read())
    _handler_src = strip_comments(open(os.path.join(TIER_DIR, "ElysiumTierHandler.java"),
                                       encoding="utf-8").read())
    if "declareWorld" not in _tiers_src:
        fail("nothing can declare a world's tier, so the library would need its own list "
             "of every dimension a pack might contain")
    if re.search(r'"(?:[a-z_]+:)?(?:overworld|the_nether|the_end)"', _tiers_src):
        fail("the library names a dimension of its own. Worlds are declared by whoever "
             "adds them; a list here is a list that goes stale the moment a pack changes")
    if "Math::max" not in _tiers_src and "Math.max" not in _tiers_src:
        fail("a world's declared tier can be lowered by declaring it again, so two modules "
             "sharing a world would race")
    if "copyOnDeath" not in _tiers_src:
        fail("the world floor does not survive death, so dying on Cinderveil would cost a "
             "player the standing they earned by getting there — a punishment nothing "
             "announced")
    _lift = body_of(_tiers_src, "public static boolean lift")
    if "<= floorOf(player)" not in _lift:
        fail("lift() never compares against the floor already earned, so walking home "
             "would lower it")
    if _tiers_src.count("setData(WORLD_FLOOR.get()") != 1:
        fail("the world floor is written in more than one place, so the two writers would "
             "disagree about whether a floor may ever go down")
    _visited = body_of(_tiers_src, "public static boolean visited")
    if "lift(player," not in _visited:
        fail("visited() no longer raises the floor through lift(), so arriving in a deeper "
             "world would write a floor nothing compared")
    for _event in ("PlayerChangedDimensionEvent", "PlayerLoggedInEvent"):
        if _event not in _handler_src:
            fail(f"nothing listens for {_event}, so a world a player is standing in would "
                 f"never raise their floor")
    if "ElysiumTiers.visited(" not in _handler_src:
        fail("the handler never records the visit it exists to record")
    for _key in ("elysium.world_tier.frontier", "elysium.world_tier.apotheosis",
                 "elysium.world_tier.beyond", "elysium.world_tier.raised"):
        if _key not in json.load(open(os.path.join(WORK, "src/main/resources/assets/elysiumlib/lang/en_us.json"),
                                      encoding="utf-8")):
            fail(f"{_key} has no translation; a tier would announce itself as a raw key")
    # The augments (P5.2). Two sides, applied two different ways, and each has
    # a way of going wrong that compiles: a player's augment added instead of
    # re-asserted doubles every time it is applied, and a mob's multiplier is
    # written into a base value, which cannot be undone — so it must happen
    # exactly once.
    _augment_src = strip_comments(open(os.path.join(TIER_DIR, "ElysiumTierAugmentHandler.java"),
                                       encoding="utf-8").read())
    _mobs_src = strip_comments(open(os.path.join(TIER_DIR, "ElysiumTierMobs.java"),
                                    encoding="utf-8").read())
    # Scoped to the statement that matters, not a presence check: the same file
    # removes a modifier elsewhere, for the case where a tier grants nothing,
    # and a bare search for the word passed on an add with no remove in front
    # of it.
    if not re.search(r"removeModifier\([^)]*\)\s*;\s*instance\.addTransientModifier", _augment_src):
        fail("a player's tier augment is added without removing the one already there, so "
             "every re-assertion would stack another copy and a long session would end "
             "with a player of arbitrary health")
    if "addTransientModifier" not in _augment_src:
        fail("a player's tier augment is applied permanently, so it would be saved into the "
             "player and survive the tier that granted it")
    if "PlayerTickEvent" not in _augment_src or "INTERVAL" not in _augment_src:
        fail("nothing re-asserts a player's augments, so a tier gained mid-session would "
             "buy nothing until the next login")
    if "setBaseValue" not in _mobs_src:
        fail("mobs are not hardened by tier at all, so the world half of a world tier does "
             "nothing")
    if "HARDENED" not in _mobs_src or "getData(HARDENED" not in _mobs_src:
        fail("nothing records that a mob has been hardened, and the multiplier is written "
             "into a base value — so a mob hardened twice keeps both multiplications and "
             "there is no way back")
    if "FinalizeSpawnEvent" not in _mobs_src:
        fail("mobs are hardened somewhere other than at spawn; the multiplier is not "
             "reversible, so it has one safe moment")
    if "setHealth(" not in _mobs_src:
        fail("a hardened mob's health is never refilled, so raising its maximum would leave "
             "it arriving at a fraction of its own bar")
    print("world tiers        : derived from level with a world floor that only rises, "
          "worlds declared rather than listed, kept through death; augments re-asserted "
          "for players and applied once to mobs")

    # Gear sets (SPEC_CLONE P9.1 and P9.2): a third attachment on the same
    # engine. What is checked is that it stays one — a set bonus written as its
    # own system would be the fourth implementation P4 exists to prevent — and
    # that a bonus cannot outlive the armour that earned it.
    _set_dir = os.path.join(WORK, "src/main/java/com/elysium/lib/set")
    if not os.path.isdir(_set_dir):
        fail("gear sets are gone; four pieces of a set would be four unrelated pieces")
    else:
        _set_src = ""
        for _name in sorted(os.listdir(_set_dir)):
            if _name.endswith(".java"):
                _set_src += strip_comments(open(os.path.join(_set_dir, _name),
                                                encoding="utf-8").read())
        if 'ElysiumItemEffects.register("elysiumlib:gear_sets"' not in _set_src:
            fail("gear sets do not register with the effect engine, so a set bonus would be "
                 "a fourth implementation of the thing P4 built once")
        if "ElysiumItemEffects.Active(set.id(), 1, effect)" not in _set_src:
            fail("a set hands its worn count on as an effect level, which multiplies every "
                 "number in the bonus by how many pieces are on — a four-piece set would "
                 "grant four times what its file says")
        if "addTransientModifier" not in _set_src or "removeModifier" not in _set_src:
            fail("a set's attributes are not re-asserted, so taking the armour off would "
                 "leave the bonus behind")
        if "getPath().startsWith(\"gear_set/\")" not in _set_src:
            fail("nothing takes a set modifier back off, so a bonus would survive the armour "
                 "that earned it")
        if "ThreadLocal" not in _set_src:
            fail("the wearer is tracked in a way two threads would share, and the client "
                 "renders tooltips on one while the server ticks on another")
        _sets_dir = os.path.join(WORK, "..", "elysium-core/src/main/resources/data/elysium/"
                                             "elysium_gear_sets")
        if os.path.isdir(_sets_dir):
            _set_files = sorted(f for f in os.listdir(_sets_dir) if f.endswith(".json"))
            for _file in _set_files:
                _set = json.load(open(os.path.join(_sets_dir, _file), encoding="utf-8"))
                if len(_set.get("items", [])) < 2:
                    fail(f"gear set {_file} names fewer than two items, so there is nothing "
                         f"to complete")
                if not _set.get("bonuses"):
                    fail(f"gear set {_file} grants nothing for completing it")
                for _bonus in _set.get("bonuses", []):
                    for _effect in _bonus.get("effects", []):
                        _kind = _effect.get("type")
                        if _kind not in _vocabulary:
                            fail(f"gear set {_file} states a '{_kind}' effect, which the "
                                 f"engine has no word for")
                        elif not _vocabulary[_kind]:
                            fail(f"gear set {_file} states '{_kind}', which is only read "
                                 f"while gear is built from parts")
            print(f"gear sets          : {len(_set_files)} sets, on the one engine, "
                  f"with bonuses that come off with the armour")

    # Elites and invaders (SPEC_CLONE P9.3 and P9.4). An augment multiplies
    # base attribute values, which cannot be undone, so the whole of what is
    # checked here is that it happens once, that it does something, and that
    # the creature it produces can be recognised.
    _aug_dir = os.path.join(WORK, "src/main/resources/data/elysiumlib/elysium_mob_augments")
    _elite_dir = os.path.join(WORK, "src/main/java/com/elysium/lib/elite")
    if not os.path.isdir(_aug_dir) or not os.path.isdir(_elite_dir):
        fail("elites are gone; every creature at every world tier would be the same creature")
    else:
        _elite_src = ""
        for _name in sorted(os.listdir(_elite_dir)):
            if _name.endswith(".java"):
                _elite_src += strip_comments(open(os.path.join(_elite_dir, _name),
                                                  encoding="utf-8").read())
        _aug_files = sorted(f for f in os.listdir(_aug_dir) if f.endswith(".json"))
        if len(_aug_files) < 16:
            fail(f"only {len(_aug_files)} augment(s); the catalogue lists 16")
        _aug_lang = json.load(open(os.path.join(
            WORK, "src/main/resources/assets/elysiumlib/lang/en_us.json"), encoding="utf-8"))
        _aug_gates = set()
        for _file in _aug_files:
            _id = os.path.splitext(_file)[0]
            _augment = json.load(open(os.path.join(_aug_dir, _file), encoding="utf-8"))
            _aug_gates.add(_augment.get("min_tier", 0))
            if not _augment.get("attributes") and not _augment.get("effects"):
                fail(f"augment {_id} does nothing at all, so it would be a word in a "
                     f"creature's name and no more")
            for _attribute, _factor in (_augment.get("attributes") or {}).items():
                if _factor == 1.0:
                    fail(f"augment {_id} multiplies {_attribute} by one, which is not a change")
                if _factor <= 0.0:
                    fail(f"augment {_id} multiplies {_attribute} by {_factor}, which would "
                         f"take the attribute away rather than augment it")
            if _augment.get("weight", 0) <= 0:
                fail(f"augment {_id} weighs nothing and could never be drawn")
            if f"elysium.augment.elysiumlib.{_id}" not in _aug_lang:
                fail(f"augment {_id} has no name, and its name is what a creature is called")
        for _key in ("elysium.rank.elite", "elysium.rank.invader"):
            if _key not in _aug_lang:
                fail(f"{_key} has no translation; an invader would announce itself as a raw key")
        if len(_aug_gates) < 3:
            fail("the augments are not spread across world tiers, so the deep tiers would "
                 "produce the same creatures as the shallow ones")
        if "ElysiumEliteRules.roll(" not in _elite_src:
            fail("nothing rolls a rank, so the rules that say how rare an elite is are read "
                 "by nothing")
        if "decided(mob)" not in _elite_src:
            fail("a creature can be promoted twice, and an augment multiplies base values — "
                 "there is no way back from that")
        if "setBaseValue(" not in _elite_src:
            fail("augments do not change what a creature is made of")
        if "setCustomName(" not in _elite_src:
            fail("an elite is not named after what is wrong with it, so a player has no way "
                 "to tell one from an ordinary creature until it kills them")
        if "mob.setHealth(mob.getMaxHealth())" not in _elite_src:
            fail("a promoted creature keeps the health it had before its maximum was raised, "
                 "so an elite would arrive at a fraction of its own bar")
        if "ElysiumAffixRules.pick(" not in _elite_src:
            fail("augments are drawn by a second implementation of the weighted pick, which "
                 "is a second thing that can disagree about what a weight of zero means")
        _tier_mobs = strip_comments(open(os.path.join(
            WORK, "src/main/java/com/elysium/lib/tier/ElysiumTierMobs.java"),
            encoding="utf-8").read())
        if "ElysiumElites.promote(" not in _tier_mobs:
            fail("nothing promotes a creature at the one moment it is safe to")
        # What a promotion is worth to whoever ends it (SG-93). More of what it
        # already dropped, not a second loot table — so a pack that changed
        # what zombies drop has changed what elite zombies drop.
        if "LivingDropsEvent" not in _elite_src or "lootMultiplier(" not in _elite_src:
            fail("an elite is harder than an ordinary creature and worth exactly the same, "
                 "so the extra weight is a tax on the player")
        if "event.getDrops().addAll(" not in _elite_src:
            fail("the extra drops are computed and never added")
        if "LivingExperienceDropEvent" not in _elite_src:
            fail("an elite gives ordinary experience")
        print(f"elites             : {len(_aug_files)} augments across {len(_aug_gates)} "
              f"world-tier gates, drawn by the affix pool, applied once at spawn")

    print(f"item effects       : {len(_vocabulary)} kinds "
          f"({sum(1 for v in _vocabulary.values() if v)} read off a finished item), "
          f"{len(_affix_files)} affixes in {_placements['prefix']} prefixes and "
          f"{_placements['suffix']} suffixes across {len(_gates)} world-tier gates, "
          f"all on the one engine")

# ---------------------------------------------------------------------------
# The sheet packet must not rebuild a screen that is already open
# ---------------------------------------------------------------------------
#
# CodexScreenHarness proves the screen keeps the player's page when it is
# refreshed. It cannot prove the packet handler *calls* refresh, because that
# path needs a running client — and the handler is where the reported bug
# actually was: spending a point pushed a sheet, and the client answered by
# constructing a new screen, which starts on the first page.
#
# So the half that can be executed is executed, and this checks the half that
# cannot. Structural, and narrow on purpose: the handler must consult the
# screen that is already up before it replaces it.

hooks = os.path.join(WORK, "src/main/java/com/elysium/lib/network/ElysiumClientHooks.java")
screen_file = os.path.join(WORK, "src/main/java/com/elysium/lib/screen/ElysiumCharacterScreen.java")

if not os.path.isfile(hooks):
    fail("ElysiumClientHooks.java is missing — nothing receives the sheet")
elif not os.path.isfile(screen_file):
    fail("ElysiumCharacterScreen.java is missing")
else:
    hook_source = strip_comments(open(hooks, encoding="utf-8").read())
    open_body = hook_source.split("openCharacterScreen", 1)[-1].split("\n    }", 1)[0]

    if "setScreen" not in open_body:
        fail("openCharacterScreen never calls setScreen, so the Codex opens "
             "nothing at all")
    if "instanceof ElysiumCharacterScreen" not in open_body:
        fail("openCharacterScreen calls setScreen without first asking whether "
             "an ElysiumCharacterScreen is already open. Every sheet the server "
             "pushes then rebuilds the screen from scratch — and a rebuilt "
             "screen starts on the first page, so spending a point throws the "
             "player off the stats tab. This is the reported bug")
    if "refresh(" not in open_body:
        fail("openCharacterScreen checks for an open screen and never calls "
             "refresh on it, so the screen it found keeps showing the numbers "
             "it was built with")

    screen_source = strip_comments(open(screen_file, encoding="utf-8").read())
    if "public void refresh(" not in screen_source:
        fail("ElysiumCharacterScreen has no refresh method, so the only way to "
             "show a newer sheet is to build a new screen and lose the page")

    # The fields refresh has to be able to write. Final ones would not compile,
    # but the failure would read as a compile error rather than as the design
    # rule it is.
    for field in ("private CharacterSheet.Parsed sheet;", "private int unspent;"):
        if field not in screen_source:
            fail(f"ElysiumCharacterScreen no longer declares `{field}` — if it "
                 f"went final again, the screen cannot take a newer sheet "
                 f"without being rebuilt")

    # And the thing that must survive: the tab is written by the tab strip and
    # by nothing else. A reset hidden in refresh is the bug wearing a new coat.
    if "this.tab = " in screen_source.split("public void refresh(", 1)[-1] \
            .split("\n    }", 1)[0]:
        fail("refresh() assigns the tab. That is the reported bug moved inside "
             "the fix: the screen is kept and the page is thrown away anyway")

    print("codex navigation   : the sheet packet refreshes an open screen "
          "rather than rebuilding it, and refresh leaves the page alone")

# ---------------------------------------------------------------------------
# Hazards: every one answerable, every one explained
# ---------------------------------------------------------------------------
#
# HazardHarness executes the arithmetic. What it cannot see is whether a hazard
# a player meets can be understood — the counters are translation keys, and a
# missing one renders as the key itself in the chat line that is supposed to
# tell somebody how to stop dying.

hazard_dir = os.path.join(WORK, "src/main/java/com/elysium/lib/hazard")
if not os.path.isdir(hazard_dir):
    fail("com/elysium/lib/hazard is missing — the hazard layer is gone")
else:
    hazards_src = strip_comments(
        open(os.path.join(hazard_dir, "ElysiumHazards.java"), encoding="utf-8").read())
    declared = re.findall(r'id\("([a-z_]+)"\)', hazards_src)
    if len(declared) != 5:
        fail(f"ElysiumHazards declares {len(declared)} hazards, not the five the "
             f"specification names: {declared}")

    lang_path = os.path.join(WORK, "src/main/resources/assets/elysiumlib/lang/en_us.json")
    lang = json.load(open(lang_path, encoding="utf-8"))
    for hazard in declared:
        for suffix, why in (("", "its name"),
                            (".cheap", "the free thing a player can do about it"),
                            (".engineered", "the thing they can build")):
            key = f"elysium.hazard.{hazard}{suffix}"
            if key not in lang:
                fail(f"{key} is not translated, so a player standing in {hazard} "
                     f"is shown a raw key where {why} should be")

    if "elysium.hazard.exposed" not in lang:
        fail("elysium.hazard.exposed is not translated, so the line that names "
             "the hazard doing the damage renders as a key")

    # Every hazard must be bound to a distinct element, because the element is
    # the whole answer. Two hazards on one element means one set of armour
    # covers two worlds and one of the five elements covers none.
    elements = re.findall(r"ElysiumElements\.([A-Z]+)", hazards_src)
    if len(set(elements)) != len(elements):
        duplicated = [e for e in set(elements) if elements.count(e) > 1]
        fail(f"more than one hazard is bound to {duplicated}, so that armour "
             f"answers two hazards and some other element answers none")

    # Shelter has to be consulted too. A claim that does not stop the weather is
    # the feature not existing, and the harness cannot see it — inShelteredClaim
    # needs a server level, so only this says the damage tick asks.
    handler_check = os.path.join(WORK,
        "src/main/java/com/elysium/lib/event/ElysiumHazardHandler.java")
    if os.path.isfile(handler_check):
        src = strip_comments(open(handler_check, encoding="utf-8").read())
        if "inShelteredClaim(" not in src:
            fail("the hazard damage tick never asks whether the player is in a "
                 "claim, so a filed base does not shelter anybody and the "
                 "permanent Suspicion floor it costs buys nothing")

    # A claim's ascension must survive a save. The field was added after claims
    # shipped, so every existing claim in every existing world has no such key;
    # a required field would make those saves fail to parse and drop the lot.
    claim_src = os.path.join(WORK,
        "src/main/java/com/elysium/lib/claim/ElysiumClaim.java")
    if os.path.isfile(claim_src):
        text = strip_comments(open(claim_src, encoding="utf-8").read())
        if 'optionalFieldOf("ascension"' not in text:
            fail("ElysiumClaim reads `ascension` as a required codec field. "
                 "Every claim written before beacons could be ascended has no "
                 "such key, so those saves fail to parse and the world loses "
                 "every claim in it")

    # The seal has to be consulted by the code that deals the damage. The
    # harness proves the rule is right; only this says the rule is asked.
    handler = os.path.join(WORK,
        "src/main/java/com/elysium/lib/event/ElysiumHazardHandler.java")
    if not os.path.isfile(handler):
        fail("ElysiumHazardHandler is missing, so no hazard ever reaches a player")
    else:
        handler_src = strip_comments(open(handler, encoding="utf-8").read())
        if "isSealed(" not in handler_src:
            fail("the hazard damage tick never asks whether the player is sealed, "
                 "so a full matching set protects by arithmetic alone and the "
                 "immunity a player was promised does not exist")
        if "hurt(" not in handler_src:
            fail("the hazard damage tick never hurts anybody, so every hazard is "
                 "a message with no consequence")

    # ----------------------------------------------------------------------
    # Heat: it has to need a reason
    # ----------------------------------------------------------------------
    #
    # HazardHarness executes the two gates. What it cannot see is whether the
    # damage tick is still reaching them — a thermal term that stopped calling
    # sunIntensity would be back to burning every desert, and the harness would
    # go on proving that the function it no longer calls is correct.

    field_path = os.path.join(hazard_dir, "ElysiumHazardField.java")
    if os.path.isfile(field_path):
        field_src = strip_comments(open(field_path, encoding="utf-8").read())
        thermal_body = body_of(field_src, "private static float thermal(")
        if thermal_body is None:
            fail("ElysiumHazardField.thermal is gone, so heat has no rule")
        else:
            for call, why in (
                    ("fromSun(", "the altitude gate"),
                    ("fromFire(", "the fire gate")):
                if call not in thermal_body:
                    fail(f"the thermal hazard never asks {call[:-1]}, so "
                         f"{why} the owner asked for is computed and never used")
        sun_body = body_of(field_src, "public static float sunIntensity(")
        if sun_body is not None and "THERMAL_ALTITUDE" not in sun_body:
            fail("sunIntensity ignores THERMAL_ALTITUDE, so heat is back to "
                 "firing at the height players actually walk around at")
        fire_body = body_of(field_src, "static float measureFire(")
        if fire_body is None:
            fail("nothing scans for fire, so the second gate does not exist")
        elif "isLoaded(" not in fire_body:
            fail("the fire scan reads blocks without asking whether the chunk "
                 "is loaded, so it generates terrain at the edge of every "
                 "player's view once a second forever")
        cold_body = body_of(field_src, "private static float cryogenic(")
        if cold_body is not None and "SCORCHING" not in cold_body:
            fail("the cold hazard's altitude term ignores the biome, so a "
                 "desert mesa at y=250 is both freezing and burning")

        heat_tag = os.path.join(RES, "data/elysiumlib/tags/block/heat_source.json")
        if not os.path.isfile(heat_tag):
            fail("the heat_source tag has no file, so nothing but lava is ever "
                 "a fire and a campfire in a sealed room means nothing")
        else:
            values = json.load(open(heat_tag, encoding="utf-8")).get("values", [])
            if not values:
                fail("the heat_source tag is empty")
            for value in values:
                entry = value.get("id") if isinstance(value, dict) else value
                namespace = str(entry).lstrip("#").split(":", 1)[0]
                if namespace not in ("minecraft", "elysiumlib", "c"):
                    fail(f"heat_source names {entry}, which is a content mod. "
                         f"The library must not know about any mod built on it")

        print("heat                : gated on altitude and on fire, with the "
              "scan bounded and the cold term kept out of scorching biomes")

    # ----------------------------------------------------------------------
    # Wards: the cheap counter has to be asked for, bound, and readable
    # ----------------------------------------------------------------------
    #
    # HazardHarness executes the ward's arithmetic — flat, unstackable, three
    # minutes. What it cannot execute is any of the wiring, because all of it
    # needs a registry: whether the damage tick consults a ward at all, whether
    # each of the five hazards actually has one, and whether a player who eats
    # a ration sees a name in the corner of their inventory or a raw key.

    wards_path = os.path.join(hazard_dir, "ElysiumWards.java")
    protection_path = os.path.join(hazard_dir, "ElysiumProtection.java")
    if not os.path.isfile(wards_path):
        fail("ElysiumWards is missing, so food answers no hazard and §10.2's "
             "cheap counter is a line of tooltip text with nothing behind it")
    else:
        wards_src = strip_comments(open(wards_path, encoding="utf-8").read())

        # One ward per hazard, bound at the point of registration. A ward that
        # is registered and never bound is a status icon that does nothing, and
        # nothing in the game would say so.
        pairs = re.findall(r'ward\("([a-z_]+)",\s*ElysiumHazards\.([A-Z]+)\)',
                           wards_src)
        registered = [name for name, _hazard in pairs]
        bound_hazards = [hazard for _name, hazard in pairs]
        if len(registered) != len(declared):
            fail(f"ElysiumWards registers {len(registered)} ward effect(s) for "
                 f"{len(declared)} hazards, so at least one hazard has no cheap "
                 f"counter and can only be answered by a project")

        if sorted(bound_hazards) != sorted(set(bound_hazards)):
            fail("two wards are bound to the same hazard in ElysiumWards, so "
                 "one hazard has two answers and another has none")

        if "bind(hazard, holder)" not in wards_src:
            fail("ElysiumWards.ward registers an effect without binding it to "
                 "its hazard, so every ration grants a status icon that the "
                 "damage tick will never look at")

        for name in registered:
            key = f"effect.elysiumlib.{name}"
            if key not in lang:
                fail(f"{key} is not translated, so a player who eats a ration "
                     f"reads a raw key in the one place the game tells them "
                     f"what they just gained")

        # The ward must stay a state. The harness proves wardedBy ignores its
        # amplifier; this is the guard against the amplifier being reintroduced
        # by a caller that computes its own answer instead of asking.
        state_body = body_of(wards_src, "public static boolean wardedBy(")
        if state_body is None:
            fail("ElysiumWards.wardedBy is gone — the ward rule is no longer "
                 "executable, so the harness is checking nothing")
        elif "amplifier" in state_body.split(")", 1)[1]:
            fail("ElysiumWards.wardedBy reads its amplifier, so a stacked ward "
                 "means something different from a ward, and a modded potion "
                 "decides whether a player is immune")

    if os.path.isfile(protection_path):
        protection_src = strip_comments(
            open(protection_path, encoding="utf-8").read())

        all_shares = body_of(protection_src, "public static float[] allShares(")
        if all_shares is None:
            fail("ElysiumProtection.allShares is gone, so nothing gathers the "
                 "partial sources and every source but armour is unreachable")
        else:
            if "ElysiumWards" in all_shares:
                fail("ElysiumProtection.allShares treats the ward as a share. "
                     "The owner made a ration immunity — a named state, asked "
                     "with the set and the claim — and a state that also "
                     "combines as a share is two implementations that have to "
                     "agree")
            if "wornShares(" not in all_shares:
                fail("ElysiumProtection.allShares no longer includes worn "
                     "armour, so standing somewhere replaces wearing something")

        # The seal stays the seal. A ward is its own named state, asked by the
        # tick beside it — folding it into isSealed would make "sealed" mean two
        # things and the reassurance message would name the wrong one.
        for method in ("public static boolean isSealed(",
                       "public static boolean sealedBy("):
            sealed_body = body_of(protection_src, method)
            if sealed_body is not None and "ElysiumWards" in sealed_body:
                fail(f"{method.split()[-1].rstrip('(')} consults ElysiumWards, "
                     f"so the armour seal and the meal are one state and a "
                     f"player is told their armour is what saved them when it "
                     f"was lunch")

    # And the damage tick has to ask all three named states, and use the
    # gathered list rather than armour alone for everything short of them.
    if os.path.isfile(handler_check):
        tick_src = strip_comments(open(handler_check, encoding="utf-8").read())
        if "ElysiumWards.isWarded(" not in tick_src:
            fail("the hazard damage tick never asks whether the player is warded, "
                 "so a ration is a status icon and the immunity the owner "
                 "decided on does not exist")
        if "elysium.hazard.warded" not in lang:
            fail("elysium.hazard.warded is not translated, so a warded player is "
                 "shown a raw key at the one moment the meal pays off")
        if "allShares(" not in tick_src:
            fail("the hazard damage tick still reduces damage by worn armour "
                 "alone, so every partial source that is not armour — the ward "
                 "food grants today, the engineered projects tomorrow — is "
                 "computed and thrown away")

    print(f"hazards            : {len(declared)} bound to {len(set(elements))} "
          f"distinct elements, each with a name, a cheap counter and an "
          f"engineered one")
    print(f"hazard wards       : {len(declared)} hazards, each with an effect "
          f"that is registered, bound, named, and asked by the damage tick as "
          f"the third named immunity")

# ---------------------------------------------------------------------------
# Discovery: every kind and every office has words
# ---------------------------------------------------------------------------
#
# The harness checks what filing pays. What it cannot see is whether a player
# can read the offer — the kinds and offices are translation keys, and a missing
# one renders as a raw key in the line that is supposed to teach the whole of
# §17.3 in one sentence.

disc_dir = os.path.join(WORK, "src/main/java/com/elysium/lib/discovery")
if not os.path.isdir(disc_dir):
    print("discovery          : not present")
else:
    lang_path = os.path.join(WORK,
        "src/main/resources/assets/elysiumlib/lang/en_us.json")
    lang = json.load(open(lang_path, encoding="utf-8"))

    kinds = re.findall(r'^\s{4}([A-Z_]+)\("([a-z_]+)"',
                       strip_comments(open(os.path.join(disc_dir,
                           "ElysiumDiscoveryKind.java"), encoding="utf-8").read()),
                       re.M)
    offices = re.findall(r'^\s{4}([A-Z_]+)\("([a-z_]+)"',
                         strip_comments(open(os.path.join(disc_dir,
                             "ElysiumFiling.java"), encoding="utf-8").read()),
                         re.M)
    if len(kinds) != 5:
        fail(f"ElysiumDiscoveryKind declares {len(kinds)} kinds, not the five "
             f"§17.1 names: {[k[1] for k in kinds]}")
    if len(offices) != 3:
        fail(f"ElysiumFiling declares {len(offices)} offices, not the three "
             f"§17.3 names: {[o[1] for o in offices]}")

    for _, kind in kinds:
        key = f"elysium.discovery.kind.{kind}"
        if key not in lang:
            fail(f"{key} is not translated, so a player cataloguing a {kind} is "
                 f"shown a raw key where its name should be")
    for _, office in offices:
        key = f"elysium.discovery.filing.{office}"
        if key not in lang:
            fail(f"{key} is not translated, so the line naming who a discovery "
                 f"was filed with renders as a key")

    # First-discovery-only is the rule that stops the scanner being a money
    # printer pointed at a wheat field.
    store = strip_comments(open(os.path.join(disc_dir,
        "ElysiumDiscoveries.java"), encoding="utf-8").read())
    # The *guard*, not merely a call to add.
    #
    # The first version accepted either "if (!catalogue.add(" or a bare
    # "catalogue.add(", joined by `and` — so deleting the early return left the
    # bare call behind and the check passed on a scanner that paid every time it
    # was pointed at the same rabbit. A set add that nobody reads the result of
    # is not a first-discovery rule.
    if "if (!catalogue.add(" not in store:
        fail("ElysiumDiscoveries adds to the catalogue without checking whether "
             "the find was already there, so scanning the same rabbit twice "
             "pays twice and the scanner is a money printer")
    elif "ALREADY_KNOWN" not in store:
        fail("ElysiumDiscoveries has no already-known result, so a repeat scan "
             "cannot be told apart from a new find by anything that calls it")
    if "copyOnDeath()" not in store:
        fail("the catalogue does not survive death, so dying resets it — and a "
             "reset catalogue pays out again, which makes dying an income")

    print(f"discovery          : {len(kinds)} kinds and {len(offices)} offices, "
          f"all named, first-find-only and kept across death")

# ---------------------------------------------------------------------------
# Points and scaling: uncapped by design, which only works if nothing caps them
# ---------------------------------------------------------------------------
#
# StatHarness executes the curves and the twelve effects. What it cannot see is
# whether something elsewhere quietly reintroduces a ceiling — a clamp on the
# spend, a fixed denominator on the character sheet's bars — because those are
# statements about other files, and a stat that is uncapped in the arithmetic
# and capped in the interface is capped.

character_src = strip_comments(read("character/ElysiumCharacter.java"))
grant = re.search(r"POINTS_PER_LEVEL\s*=\s*(\d+)", character_src)
if grant is None:
    fail("POINTS_PER_LEVEL is gone, so a level grants whatever the call site "
         "happens to multiply by")
else:
    granted = int(grant.group(1))
    if granted < 5:
        fail(f"a level grants {granted} point(s); the character sheet, the "
             f"level-up message and the whole progression were retuned around "
             f"ten and this is back to the old number")

spend_body = character_src.split("public static boolean spendPoint(", 1)
if len(spend_body) < 2:
    fail("spendPoint is gone — nothing assigns a point to a stat")
else:
    body = spend_body[1].split("public static", 1)[0]
    # A per-stat ceiling is the one thing that would make "no limit" false
    # without any of the arithmetic changing.
    if re.search(r"Math\.min\s*\(", body) and "getUnspentPoints" not in body:
        fail("spendPoint clamps something, so a stat has a ceiling the curves "
             "were written specifically so that nobody would need")

# The two-term scaling has to be the thing the curve is fed. It is easy to add
# an effective() that nothing calls, and the result is a game that plays exactly
# as it did with a new constant in it.
stat_src = strip_comments(read("stats/ElysiumStat.java"))
if "SCALING_DIVISOR" not in stat_src:
    fail("ElysiumStat has no scaling divisor, so a stat is linear again and a "
         "level in the late game is worth what a level in the first hour was")
else:
    proportion = stat_src.split("public float proportionOf(", 1)
    if len(proportion) < 2:
        fail("proportionOf is gone — nothing turns a point total into an effect")
    else:
        body = proportion[1].split("public float curveOf(", 1)[0]
        if "effective(" not in body:
            fail("proportionOf does not pass the point total through "
                 "effective(), so the linear-plus-square scaling exists and "
                 "nothing is scaled by it")

    effective_body = stat_src.split("public static float effective(", 1)
    if len(effective_body) < 2:
        fail("ElysiumStat.effective is gone, so the scaling function cannot be "
             "executed and StatHarness is checking nothing")
    elif "SCALING_DIVISOR" not in effective_body[1].split("public", 1)[0]:
        fail("effective() no longer divides by SCALING_DIVISOR, so the constant "
             "that is supposed to move the takeover point moves nothing")

# The flat effects have to be scaled by the same function as the curved ones,
# or every level widens a gap between them that nothing in the game explains.
flat_src = strip_comments(read("stats/ElysiumStats.java"))


def _declared_body(text, name):
    """The body of `static <type> name(int ...)`, brace-matched.

    Not `text.split(name + "(")` — that finds the *call* in the delegating
    Player-taking method first, whose body is one line long and never mentions
    effective(), so three correct rules were reported as broken. A check that
    reads the wrong method is a check that is right by accident when it passes.
    """
    match = re.search(r"static\s+\w+\s+" + re.escape(name) + r"\(int\b", text)
    if match is None:
        return None
    depth = 0
    for index in range(text.index("{", match.start()), len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[match.start():index + 1]
    return None


for rule, what in (("healthFor", "Vitality's health"),
                   ("damageFor", "Strength's damage"),
                   ("shieldFor", "Willpower's shield"),
                   ("regenFor", "Vitality's regeneration"),
                   ("armourFor", "Fortitude's armour")):
    body = _declared_body(flat_src, rule)
    if body is None:
        fail(f"{rule} is gone, so {what} has no rule that can be executed")
    elif "effective(" not in body:
        fail(f"{what} multiplies the raw point total instead of the scaled one, "
             f"so it falls further behind every proportional stat with every "
             f"level and nothing in the game says why")

screen_bars = strip_comments(read("screen/ElysiumCharacterScreen.java"))
bar = re.search(r"private static float barFor\(([^)]*)\)", screen_bars)
if bar is None:
    fail("the character sheet no longer draws stat bars")
elif "scale" not in bar.group(1):
    fail("the stat bars are drawn against a fixed number again. At ten points "
         "a level every bar is full before level seven, and a column of full "
         "bars is a display that has stopped distinguishing while still "
         "looking like information")
elif "barScale()" not in screen_bars:
    fail("barFor takes a scale that nothing computes")

# Fortitude has to keep paying past vanilla's armour ceiling, and the handover
# has to be applied by something. The harness proves the rule; only this says
# the rule is asked for.
stat_handler = strip_comments(read("event/ElysiumStatHandler.java"))
if "baseToughness(" not in stat_handler:
    fail("nothing applies Fortitude's toughness, so every point past "
         "vanilla's armour ceiling of 30 buys a number the game never reads")
if "ARMOR_TOUGHNESS" not in stat_handler:
    fail("the stat handler never touches ARMOR_TOUGHNESS, so baseToughness is "
         "computed and thrown away")

print("points and scaling : uncapped in the spend, uncapped in the curves, and "
      "the sheet's bars scale with the character")

# ---------------------------------------------------------------------------
# Rooms: detected, named, read by the tick, and made of tags that exist
# ---------------------------------------------------------------------------
#
# RoomHarness executes the rules. What it cannot see is the wiring and the data:
# whether the damage tick consults a room at all, whether every requirement tag
# has a file behind it, and whether a player standing in one is told which it
# is. A room that is detected and never mentioned is a number in a log.

room_dir = os.path.join(SRC, "room")
if not os.path.isdir(room_dir):
    print("rooms              : not present")
else:
    rooms_src = strip_comments(
        open(os.path.join(room_dir, "ElysiumRooms.java"), encoding="utf-8").read())

    declared_rooms = re.findall(r'ElysiumRoom\.register\(\s*\n?\s*id\("([a-z_]+)"',
                                rooms_src)
    if not declared_rooms:
        fail("ElysiumRooms registers no rooms, so §8.5 is a package with nothing "
             "in it")

    for room in declared_rooms:
        for suffix, why in (("", "its name"),
                            (".desc", "what it is made of")):
            key = f"elysium.room.{room}{suffix}"
            if key not in lang:
                fail(f"{key} is not translated, so a player standing in {room} "
                     f"is shown a raw key where {why} should be")
    if "elysium.room.entered" not in lang:
        fail("elysium.room.entered is not translated, so the line naming the "
             "room a player just walked into renders as a key")

    # Every requirement tag needs a file, or the room can never be built: a tag
    # with no definition matches nothing and reports nothing.
    tag_dir = os.path.join(RES, "data/elysiumlib/tags/block")
    for tag in sorted(set(re.findall(r'tag\("([a-z_/]+)"\)', rooms_src))):
        path = os.path.join(tag_dir, tag + ".json")
        if not os.path.isfile(path):
            fail(f"the {tag} tag has no file at data/elysiumlib/tags/block/"
                 f"{tag}.json, so it matches no block and the room asking for "
                 f"it can never be built")
            continue
        try:
            values = json.load(open(path, encoding="utf-8")).get("values", [])
        except Exception as problem:
            fail(f"{tag}.json does not parse: {problem}")
            continue
        if not values:
            fail(f"the {tag} tag is empty, so the room asking for it can never "
                 f"be built")
        for value in values:
            entry = value.get("id") if isinstance(value, dict) else value
            namespace = str(entry).lstrip("#").split(":", 1)[0]
            if namespace not in ("minecraft", "elysiumlib", "c"):
                fail(f"{tag}.json names {entry}, which is a content mod. The "
                     f"library must not know about any mod built on it")

    # The tick has to ask. The harness proves the rules; only this says they run.
    if os.path.isfile(handler_check):
        room_tick = strip_comments(open(handler_check, encoding="utf-8").read())
        if "ElysiumRoomCache.at(" not in room_tick:
            fail("the hazard damage tick never asks which room the player is in, "
                 "so §8.5's rooms are detected by nothing and protect nobody")
        if "announceRoom(" not in room_tick:
            fail("nothing names the room a player walks into, so a bunker and a "
                 "broom cupboard feel identical until one of them fails to save "
                 "somebody")

    # ...and the share has to reach the arithmetic.
    if os.path.isfile(protection_path):
        gathered = body_of(strip_comments(
            open(protection_path, encoding="utf-8").read()),
            "public static float[] allShares(")
        if gathered is not None and "roomShare(" not in gathered:
            fail("ElysiumProtection.allShares never includes the room, so "
                 "standing in a bunker is worth exactly as much as standing in "
                 "the rain")

    # The scan must be reached through the cache. Five hazards a second times a
    # five-hundred-block fill is the kind of cost that only appears on somebody
    # else's server.
    prot_src = strip_comments(open(protection_path, encoding="utf-8").read()) \
        if os.path.isfile(protection_path) else ""
    if "ElysiumRoomScan.survey(" in prot_src:
        fail("ElysiumProtection scans for a room directly instead of going "
             "through ElysiumRoomCache, so the hazard tick walks up to 500 "
             "blocks five times a second for every player")

    print(f"rooms              : {len(declared_rooms)} kinds, each named, "
          f"described, built from tags that exist, and read by the damage tick")

# ---------------------------------------------------------------------------
# The rest of the HUD, and the two screens a layer cannot reach
# ---------------------------------------------------------------------------
#
# Health was restyled first and the others followed, because a screen with one
# bar and two rows of icons on it does not read as a designed interface. Food
# and armour have layer ids and are cancelled like health; the attack indicator
# and the inventory do not and are re-skinned as textures by tools/gen_gui.py.
#
# Both halves fail silently. A layer that stops being cancelled draws two
# readouts on top of each other; a texture written to the wrong path is simply
# vanilla's, and nothing anywhere says so.

bars_path = os.path.join(SRC, "client/ElysiumStatusBars.java")
if not os.path.isfile(bars_path):
    fail("ElysiumStatusBars is missing, so food and armour are vanilla icons "
         "beside an Elysium health bar")
else:
    bars_src = strip_comments(open(bars_path, encoding="utf-8").read())
    for layer, what in (("FOOD_LEVEL", "food"), ("ARMOR_LEVEL", "armour"),
                        ("EXPERIENCE_BAR", "the experience bar"),
                        ("EXPERIENCE_LEVEL", "the experience level")):
        if f"VanillaGuiLayers.{layer}" not in bars_src:
            fail(f"nothing names vanilla's {layer} layer, so the {what} icons "
                 f"are still drawn")
    if bars_src.count("setCanceled(true)") < 3:
        fail("ElysiumStatusBars cancels fewer than all three of its layers, so "
             "at least one of food, armour and experience is drawn twice, one "
             "on top of the other")
    # The experience layer pair has its own gate, and it is not the one the
    # others use — a creative player can be hurt by nothing and still has no
    # experience bar.
    if "hasExperience()" not in bars_src:
        fail("the experience bar does not ask whether the player has "
             "experience, so a creative player is given a bar vanilla never "
             "drew them")
    # Once per cancelled layer, not once in the file. The loose form passed a
    # deliberate test that removed the guard from the food bar and left it on
    # the armour bar — a check that counts to one cannot see the second of two.
    if (bars_src.count("canHurtPlayer()") + bars_src.count("hasExperience()")
            < bars_src.count("setCanceled(true)")):
        fail("a status bar cancels vanilla's layer without asking whether the "
             "player can be hurt, so a creative player is shown a readout "
             "vanilla never gave them")
    # Armour is the one that had to change shape: vanilla's ten icons stop at
    # 20 points and Fortitude now fills 30 and spills into toughness.
    if "ARMOR_TOUGHNESS" not in bars_src:
        fail("the armour bar ignores toughness, so every point of Fortitude "
             "past vanilla's armour ceiling is invisible again")
    if "ElysiumMeter." not in bars_src:
        fail("the status bars do their own arithmetic instead of using "
             "ElysiumMeter, so a clamp has two places to be forgotten")

gui_tool = os.path.join(WORK, "tools", "gen_gui.py")
gui_root = os.path.join(RES, "assets/minecraft/textures/gui")
if not os.path.isfile(gui_tool):
    fail("tools/gen_gui.py is missing, so the inventory and the attack "
         "indicator are vanilla's again and nothing regenerates them")
else:
    expected = re.findall(r'^\s*"([a-z_/]+\.png)":', 
                          open(gui_tool, encoding="utf-8").read(), re.M)
    if not expected:
        fail("gen_gui.py declares no textures, so it writes nothing")
    for relative in expected:
        path = os.path.join(gui_root, relative)
        if not os.path.isfile(path):
            fail(f"gen_gui.py declares {relative} and it is not in the "
                 f"resources — run regen.sh")
            continue
        from PIL import Image as _Image
        with _Image.open(path) as _img:
            if _img.mode != "RGBA":
                fail(f"{relative} is {_img.mode}, not RGBA — the corner "
                     f"notches need transparency")
            if "inventory" in relative and _img.size != (256, 256):
                fail(f"{relative} is {_img.size}; the inventory sheet is "
                     f"256x256 and the game reads fixed offsets into it")

    # The one thing a picture cannot check: that the recesses are where the
    # *game* puts the slots.
    #
    # The coordinates below are written out rather than imported from the
    # generator, and that is the whole point of the check. The first version
    # read them from gen_gui.py and tested each recess at the coordinate the
    # generator had just drawn it at — so moving every slot a pixel moved both
    # sides of the comparison and forty-five wrong recesses passed. These are
    # vanilla's numbers, from InventoryMenu, and they do not move.
    VANILLA_SLOTS = (
        [(8, 8 + i * 18) for i in range(4)]                      # armour column
        + [(77, 62)]                                             # offhand
        + [(98 + c * 18, 18 + r * 18) for r in range(2) for c in range(2)]
        + [(8 + c * 18, 84 + r * 18) for r in range(3) for c in range(9)]
        + [(8 + c * 18, 142) for c in range(9)]
    )
    inventory_png = os.path.join(gui_root, "container/inventory.png")
    if not os.path.isfile(inventory_png):
        pass                      # already reported by the loop above
    else:
        from PIL import Image as _Image
        with _Image.open(inventory_png) as _inv:
            _pixels = _inv.convert("RGBA").load()
            _wrong = [(x, y) for x, y in VANILLA_SLOTS
                      if _pixels[x + 8, y + 8][3] == 0]
            if _wrong:
                fail(f"{len(_wrong)} inventory slot(s) have nothing drawn under "
                     f"them, starting at {_wrong[0]} — items will float beside "
                     f"their holes")
            # ...and the recess has to be darker than the panel around it, or
            # it is a patch of background rather than a hole.
            #
            # (170, 160) is the one corner of the panel no slot reaches: the
            # hotbar's recesses stop at x=169 and y=159. The first reference
            # pixel picked here was (88, 76), which is inside the offhand slot,
            # so every recess compared as "no darker than the panel" and the
            # check reported forty-five failures on a correct texture.
            _panel = _pixels[170, 160][:3]
            _corner_clear = sum(_panel) > sum(_pixels[16, 16][:3])
            _flat = [(x, y) for x, y in VANILLA_SLOTS
                     if sum(_pixels[x + 8, y + 8][:3]) >= sum(_panel)]
            if _flat:
                fail(f"{len(_flat)} inventory slot(s) are no darker than the "
                     f"panel behind them, starting at {_flat[0]} — a slot that "
                     f"is not a recess is not a slot")
            # ...and its border must start exactly one pixel up and left of the
            # slot, which is where the game draws it.
            #
            # This is the check that actually pins the position. The recesses
            # are 18 pixels on an 18-pixel pitch, so they tile seamlessly and a
            # test on the middle of one cannot tell a correct grid from a grid
            # shifted a pixel — a deliberate test moved every inventory slot by
            # (1, 1) and every centre was still inside a recess. The top-left
            # corner is the one place the shift shows.
            _adrift = [(x, y) for x, y in VANILLA_SLOTS
                       if _pixels[x - 1, y - 1][:3] == _panel]
            if _adrift:
                fail(f"{len(_adrift)} inventory slot(s) have panel where their "
                     f"border should be, starting at {_adrift[0]} — the recess "
                     f"grid is offset from the slots the game draws on it")

            if not _corner_clear:
                fail("the panel reference pixel at (170, 160) is as dark as a "
                     "slot, so the recess check is comparing a hole with a "
                     "hole and cannot fail")
            if not _wrong and not _flat and not _adrift and _corner_clear:
                print(f"gui textures       : {len(expected)} re-skinned, "
                      f"{len(VANILLA_SLOTS)} recesses under vanilla's own slot "
                      f"coordinates")

# ---------------------------------------------------------------------------
# The health bar: exactly one of them, drawn in the mod's own style
# ---------------------------------------------------------------------------
#
# HudHarness executes the geometry. What it cannot see is whether vanilla's
# hearts are still being drawn underneath — which is not a subtle failure, it is
# two health readouts on top of each other, and it is what happens the moment
# the cancel is lost in a refactor.

bar_path = os.path.join(SRC, "client/ElysiumHealthBar.java")
if not os.path.isfile(bar_path):
    fail("ElysiumHealthBar is missing, so health is back to a row of hearts "
         "that stops being readable at about 40 maximum health")
else:
    bar_src = strip_comments(open(bar_path, encoding="utf-8").read())

    if "VanillaGuiLayers.PLAYER_HEALTH" not in bar_src:
        fail("the health bar does not name vanilla's health layer, so it draws "
             "beside the hearts instead of instead of them")
    if "setCanceled(true)" not in bar_src:
        fail("the health bar never cancels vanilla's layer, so a player sees "
             "both readouts at once")
    if "canHurtPlayer()" not in bar_src:
        fail("the health bar does not ask whether the player can be hurt, so a "
             "creative player is shown a health readout vanilla never gave them")

    # The point of the exercise: the bar is a fraction of the player's own
    # maximum, not a count of fixed-size hearts.
    if "getMaxHealth()" not in bar_src:
        fail("the health bar never reads the player's maximum health, so it "
             "cannot account for the Vitality that made hearts unreadable in "
             "the first place")
    if "getAbsorptionAmount()" not in bar_src:
        fail("the health bar ignores absorption, so Willpower's shield — the "
             "other half of what stands between a player and death — is "
             "invisible")

    # Drawn with the same primitives as every other bar in the mod, which is
    # the actual request: it has to look like the rest of the interface.
    if "ElysiumUI." not in bar_src or "ElysiumPalette." not in bar_src:
        fail("the health bar draws with its own colours and primitives instead "
             "of ElysiumUI and ElysiumPalette, so it matches nothing else in "
             "the mod")

    print("health bar         : one readout, vanilla's cancelled, scaled to the "
          "player's own maximum and drawn in the mod's palette")

# The rule itself, executed rather than read. See tools/GearHarness.java.
#
# It runs only where a real Minecraft classpath is available — the same one
# compile.sh uses — because the rule is stated in terms of vanilla's own types
# and there is nothing honest to check it against without them.
# ---------------------------------------------------------------------------
# B2/B3: research fields, and the promise that they never buy power
# ---------------------------------------------------------------------------

RESEARCH = os.path.join(SRC, "research")
_field_src = open(os.path.join(RESEARCH, "ElysiumResearchField.java"),
                  encoding="utf-8").read()
_rank_src = open(os.path.join(RESEARCH, "ElysiumResearchRank.java"),
                 encoding="utf-8").read()
_deed_src = open(os.path.join(RESEARCH, "ElysiumDeed.java"),
                 encoding="utf-8").read()
_unlock_src = open(os.path.join(RESEARCH, "ElysiumUnlock.java"),
                   encoding="utf-8").read()

# 1. The four tables load without a game.
#
# The harness executes the entire progression — five thousand assertions over
# every point total from nothing to past the ceiling — and it can only do that
# because none of these four needs a registry to initialise. ElysiumResearch
# itself registers attachments, which is why CEILING lives on the rank: reading
# one constant off the wrong class pulls NeoForge's registries in and the whole
# progression stops being executed. That has now happened twice in this
# codebase, so it is a rule rather than a memory.

# 2. Every unlock is actually read somewhere.
#
# The whole B3 claim is "research unlocks capabilities". An unlock that is
# declared, translated, and announced to the player on promotion but never
# asked about is a promise the game makes out loud and does not keep — and it
# is invisible to everything except the player who goes looking for what they
# earned.
_all_java = []
for _module in sorted(os.listdir(os.path.join(WORK, ".."))):
    _dir = os.path.join(WORK, "..", _module, "src/main/java")
    if not os.path.isdir(_dir):
        continue
    for _root, _, _files in os.walk(_dir):
        for _file in _files:
            if _file.endswith(".java"):
                _all_java.append(os.path.join(_root, _file))
_everything = "\n".join(open(_path, encoding="utf-8").read()
                        for _path in _all_java)
_declared_unlocks = re.findall(r'^\s+([A-Z_]+)\("[a-z_]+", ElysiumResearchField',
                               _unlock_src, re.M)
if not _declared_unlocks:
    fail("no unlocks could be read out of ElysiumUnlock.java; the check that "
         "every unlock is wired is now checking nothing")
for _unlock in _declared_unlocks:
    if f"ElysiumUnlock.{_unlock}" not in _everything:
        fail(f"{_unlock} is declared, translated and announced to the player, "
             f"and nothing ever asks for it. An unlock that does nothing is a "
             f"promise the game makes aloud and does not keep")

# 3. No unlock buys power.
#
# The design decision B3 is named for. A research tree that grants power has to
# be pursued, so it stops describing what a player did and becomes a checklist
# they resent; a tree that grants information can be ignored and the game is the
# same game.
#
# The first version of this check scanned the descriptions for the names of
# combat stats, and it failed on the very first honest unlock: the scanner
# reports a creature's *health*, which is information about a mob and not a
# stat the player gains. Looking for the nouns tests the subject matter. What
# distinguishes an information unlock from a power one is the verb — whether
# the player can now *read* something, or now *has* more of something — so
# that is what this looks at, in both directions.
_granting = ("grants", "increases", "boosts", "raises your", "improves your",
             "bonus to", "more damage", "extra damage", "faster attack",
             "stronger", "tougher", "heals you", "reduces damage")
_reporting = ("shows", "reports", "names", "states", "says", "reads", "reveals",
              "tells", "displays", "lists", "knowing", "you arrive knowing")

_unlock_ids = dict(re.findall(
    r'^\s+([A-Z_]+)\("([a-z_]+)", ElysiumResearchField', _unlock_src, re.M))
_lang_path = os.path.join(RES, "assets/elysiumlib/lang/en_us.json")
_lang = json.load(open(_lang_path, encoding="utf-8"))

for _unlock, _uid in sorted(_unlock_ids.items()):
    _blurb = _lang.get(f"elysium.research.unlock.{_uid}.blurb", "").lower()
    if not _blurb:
        continue                      # reported by the language check below
    _granted = [word for word in _granting if word in _blurb]
    if _granted:
        fail(f"the unlock {_uid} is described as {_granted[0]!r}: {_blurb!r}. "
             f"Research buys information and access, never power — that is the "
             f"whole of B3 and it is why the tree can be ignored")
    elif not any(word in _blurb for word in _reporting):
        fail(f"the unlock {_uid} is described as {_blurb!r}, which says nothing "
             f"about what the player can now read or reach. Every unlock is "
             f"information or access; one that cannot be described that way is "
             f"probably not one")

# 4. Every field, rank and unlock has its words.
#
# A missing key renders as the raw string in a chat line the player is shown at
# exactly the moment they are being congratulated.
_wanted = set()
for _fid in re.findall(r'^\s+[A-Z_]+\("([a-z_]+)"\),?$', _field_src, re.M):
    _wanted.add(f"elysium.research.field.{_fid}")
    _wanted.add(f"elysium.research.field.{_fid}.blurb")
for _rid in re.findall(r'^\s+[A-Z]+\("([a-z]+)", \d+\)', _rank_src, re.M):
    _wanted.add(f"elysium.research.rank.{_rid}")
for _uid in _unlock_ids.values():
    _wanted.add(f"elysium.research.unlock.{_uid}")
    _wanted.add(f"elysium.research.unlock.{_uid}.blurb")
_wanted.update({"elysium.research.promoted", "elysium.research.unlocked"})
_absent = sorted(key for key in _wanted if key not in _lang)
if _absent:
    fail(f"the language file is missing {len(_absent)} research key(s), "
         f"starting with {_absent[0]}")

# 5. Novelty is enforced where it is claimed.
#
# A deed declared novel whose credit path does not consult the counted set pays
# every time, and the track stops describing what a player has done.
_research_src = open(os.path.join(RESEARCH, "ElysiumResearch.java"),
                     encoding="utf-8").read()
_credit = body_of(_research_src,
                  "public static Gain credit(Player player, ElysiumDeed deed, String subject)")
if _credit is None:
    fail("ElysiumResearch has no credit method; nothing awards research")
elif "deed.novel()" not in _credit or "COUNTED.get()" not in _credit:
    fail("ElysiumResearch.credit no longer consults the counted set, so every "
         "once-only deed pays every time and the tracks can be farmed by "
         "rescanning one cow")

# 6. The ceiling is enforced.
_award = body_of(_research_src,
                 "public static Gain award(Player player, ElysiumResearchField field, int points)")
if _award is None:
    fail("ElysiumResearch has no award method")
elif "Math.min(CEILING" not in _award:
    fail("research is no longer capped at the ceiling, so a field counts past "
         "the end of its own track — a number that grows forever and buys "
         "nothing, which is the grind this system was built to avoid")

# 7. Exposure is not written to disk, and is bounded.
_exposure = open(os.path.join(RESEARCH, "ElysiumExposure.java"),
                 encoding="utf-8").read()
if "AttachmentType" in _exposure or "serialize" in _exposure:
    fail("ElysiumExposure has become persistent. A part-finished minute is "
         "worth at most one credit and is not worth a field in every player's "
         "save, a codec to migrate and a migration to get wrong")
if "CAPACITY" not in _exposure:
    fail("ElysiumExposure is unbounded; the key is a player and a long-running "
         "server sees many of them")

# 8. The screen can see the research, and keeps up with it.
#
# Research lives in a server-side attachment, so a client that asked the local
# player would get six zeroes and draw a confident empty page for somebody who
# had filled three tracks. The numbers have to ride in the synced sheet.
#
# That they stay current is free, and worth understanding rather than assuming:
# the sync handler packs the whole sheet every five ticks and resends on any
# difference, so putting research inside the packed string is what makes a gain
# reach an open screen. A research field synced by a packet of its own would
# have needed its own trigger, and would have gone stale the first time
# somebody forgot to fire it.
_sheet = open(os.path.join(SRC, "network/CharacterSheet.java"),
              encoding="utf-8").read()
_pack = body_of(_sheet, "public static String pack(Player player)")
if _pack is None:
    fail("CharacterSheet has no pack method")
else:
    if "research(player)" not in _pack:
        fail("the synced sheet no longer carries research, so the character "
             "screen's research page draws six zeroes for every player — a "
             "client cannot read a server-side attachment")
    if "catalogued(player)" not in _pack:
        fail("the synced sheet no longer carries the catalogue count, so B5's "
             "readout is blank again")

_sync = open(os.path.join(SRC, "event/ElysiumSyncHandler.java"),
             encoding="utf-8").read()
if "CharacterSheet.pack(player)" not in _sync:
    fail("the sync handler no longer compares the packed sheet, so nothing "
         "notices a change and an open screen never updates")

# 9. The screen's tab count and its label strip agree.
#
# A tab whose case is missing from the switch falls through to the character
# page: the strip highlights "Research" and the body shows a character sheet,
# which reads as the click not having registered.
_screen = open(os.path.join(SRC, "screen/ElysiumCharacterScreen.java"),
               encoding="utf-8").read()
_labels = re.search(r"TAB_LABELS = \{(.*?)\};", _screen, re.S)
_tab_consts = re.findall(r"private static final int TAB_[A-Z]+ = (\d+);", _screen)
if _labels is None:
    fail("the character screen has no TAB_LABELS")
else:
    _label_count = _labels.group(1).count("Component.translatable")
    if _label_count != len(_tab_consts):
        fail(f"the character screen offers {_label_count} tab label(s) and "
             f"declares {len(_tab_consts)} tab constant(s); a tab with no case "
             f"falls through to the character page and reads as a dead click")
    _cases = len(re.findall(r"case TAB_[A-Z]+ ->", _screen))
    if _cases != _label_count - 1:
        fail(f"{_cases} tab(s) are handled in the switch and there are "
             f"{_label_count} label(s); one of them draws the default page "
             f"while the strip says otherwise")

print(f"research           : {len(_declared_unlocks)} unlock(s), every one "
      f"read somewhere and none of them power; "
      f"{len(_wanted)} language key(s) present")

# ---------------------------------------------------------------------------
# H2/H3: stress bands, and the explanation being the number
# ---------------------------------------------------------------------------

STRESS = os.path.join(SRC, "stress")
_stressor_src = open(os.path.join(STRESS, "ElysiumStressor.java"),
                     encoding="utf-8").read()
_band_src = open(os.path.join(STRESS, "ElysiumStressBand.java"),
                 encoding="utf-8").read()
_stress_src = open(os.path.join(STRESS, "ElysiumStress.java"),
                   encoding="utf-8").read()
_field_stress_src = open(os.path.join(STRESS, "ElysiumStressField.java"),
                         encoding="utf-8").read()

# 1. The model loads without a game.
#
# Four thousand combinations of reasons are executed by the harness, which is
# the only way to reach the villager who is homeless, injured, threatened and
# standing in lava at the same time. That stays possible only while these three
# need nothing to initialise.

# 2. Stress is derived, not accumulated.
#
# The whole of §8.3 rests on this. A saved total is a number whose reasons are
# gone, and any "why" shown beside it would be a second tally free to disagree
# with the first. Exactly one thing about stress is saved — the peak, because
# Recovery is a direction and a direction needs two points.
if "AttachmentType" in _field_stress_src or "serialize" in _field_stress_src:
    fail("ElysiumStressField has started saving something. Stress is derived "
         "from the world every time it is asked for; a stored total is a "
         "number that cannot say why it is what it is")
if "AttachmentType" in _stress_src:
    fail("ElysiumStress has started saving something; the arithmetic is meant "
         "to be pure and executable outside a world")
_record_src = open(os.path.join(STRESS, "ElysiumStressRecord.java"),
                   encoding="utf-8").read()
_saved = re.findall(r'ATTACHMENTS\.register\("([a-z_]+)"', _record_src)
if _saved != ["stress_peak"]:
    fail(f"the stress package saves {_saved}; the peak is the single "
         f"deliberate exception and anything else is an accumulated score "
         f"wearing a different name")

# 3. The total is floored at zero.
_total = body_of(_stress_src,
                 "public static int total(Collection<ElysiumStressor> reasons)")
if _total is None:
    fail("ElysiumStress has no total method")
elif "Math.max(0" not in _total:
    fail("stress is no longer floored at zero, so a contented village banks "
         "relief against a future disaster and absorbs one for free")

# 4. The readout prints every reason, with its weight.
#
# Printing the band alone is the version of this feature that looks finished
# and answers nothing: §8.3 asks for the reason stack, and a band is a summary
# of one.
_social = open(os.path.join(SRC, "event/ElysiumSocialHandler.java"),
               encoding="utf-8").read()
_say = body_of(_social, "private static void sayHowTheyAre(Villager villager, Player player)")
if _say is None:
    fail("nothing reports a villager's stress, so the bands exist and no "
         "player can ever see one")
else:
    if "reading.ordered()" not in _say:
        fail("the stress readout no longer walks the reasons, so a player is "
             "told a band and not why")
    if "reason.weight()" not in _say:
        fail("the stress readout no longer prints each reason's weight. The "
             "point is that the explanation adds up to the number; without the "
             "figures it is a description of one")

# 5. A villager who refuses says why.
if "stress.worst()" not in _social:
    fail("a villager in crisis refuses to trade without naming a reason, which "
         "is indistinguishable from a bug and will be reported as one")

# 6. Every reason and band has its words.
_stress_ids = set(re.findall(r'^\s+[A-Z_]+\("([a-z_]+)",\s*-?\d+\)[,;]',
                             _stressor_src, re.M))
_band_ids = set(re.findall(r'^\s+[A-Z]+\("([a-z]+)",\s*-?\d+\)[,;]',
                           _band_src, re.M))
if len(_stress_ids) < 6 or len(_band_ids) < 4:
    fail(f"only {len(_stress_ids)} stressor(s) and {len(_band_ids)} band(s) "
         f"could be read; the language check below is checking nothing")
_missing = sorted(
    [f"elysium.stress.reason.{one}" for one in _stress_ids
     if f"elysium.stress.reason.{one}" not in _lang]
    + [f"elysium.stress.band.{one}" for one in _band_ids
       if f"elysium.stress.band.{one}" not in _lang])
if _missing:
    fail(f"the language file is missing {len(_missing)} stress key(s), "
         f"starting with {_missing[0]} — it would render as the raw key in the "
         f"one line explaining why a villager will not trade")

print(f"stress bands       : {len(_stress_ids)} reasons and {len(_band_ids)} "
      f"bands, derived rather than stored, each reason printed with its weight")

# ---------------------------------------------------------------------------
# E7: nothing hits a player hard without warning them first
# ---------------------------------------------------------------------------

TELEGRAPH = os.path.join(SRC, "telegraph")
_tel_src = open(os.path.join(TELEGRAPH, "ElysiumTelegraph.java"),
                encoding="utf-8").read()
_warn_src = open(os.path.join(TELEGRAPH, "ElysiumWarnings.java"),
                 encoding="utf-8").read()
_goal_src = open(os.path.join(TELEGRAPH, "ElysiumTelegraphedAttack.java"),
                 encoding="utf-8").read()

# 1. The rule loads without a game.

# 2. The cap is applied in the damage pipeline, for every attacker.
#
# The whole value of "you will always see it coming" is that it is true of the
# mob you have never met. Enforced per-mob it would be a convention this mod
# follows and every other mod does not, which is not a promise at all.
_combat = open(os.path.join(SRC, "event/ElysiumCombatHandler.java"),
               encoding="utf-8").read()
_incoming = body_of(_combat,
                    "public static void onIncomingDamage(LivingIncomingDamageEvent event)")
if _incoming is None:
    fail("ElysiumCombatHandler has no incoming-damage handler")
else:
    if "ElysiumTelegraph.allowed(" not in _incoming:
        fail("the damage pipeline no longer caps unannounced blows, so the "
             "readability rule holds only for mobs that choose to keep it")
    if "ElysiumWarnings.spend(" not in _incoming:
        fail("a warning is never spent, so one wind-up licences every blow that "
             "follows it for as long as the mob keeps swinging")
    # Before armour and the matrix, or a player in good gear can legally be hit
    # unannounced for more than one in none.
    _cap_at = _incoming.find("ElysiumTelegraph.allowed(")
    _matrix_at = _incoming.find("ElysiumElement attacking")
    if _matrix_at >= 0 and _cap_at > _matrix_at:
        fail("the telegraph cap is applied after the elemental matrix. What is "
             "capped has to be the blow as it was swung, or armour makes an "
             "unfair hit legal rather than making a fair one survivable")

# 3. Nothing in the bestiary uses a bare melee goal.
#
# A single family written with vanilla's goal would be the one monster in the
# game a player cannot learn, and it would look exactly like all the others.
_mob_java = []
for _module in ("elysium-mobs", "elysium-core", "elysium-npcs", "elysium-dungeons"):
    _dir = os.path.join(WORK, "..", _module, "src/main/java")
    for _root, _, _files in os.walk(_dir) if os.path.isdir(_dir) else []:
        for _file in _files:
            if _file.endswith(".java"):
                _mob_java.append(os.path.join(_root, _file))
_bare = []
for _path in _mob_java:
    _text = strip_comments(open(_path, encoding="utf-8").read())
    # Matched with the package allowed in front of it. A check for the bare
    # name would pass on a fully-qualified construction — which is exactly what
    # somebody writes when they have not imported the class, and exactly what
    # the negative test found this rule missing.
    if re.search(r"new\s+(?:[\w.]+\.)?MeleeAttackGoal\s*\(", _text):
        _bare.append(os.path.basename(_path))
if _bare:
    fail(f"{', '.join(sorted(_bare))} still construct(s) vanilla's "
         f"MeleeAttackGoal. Every melee attack in this mod goes through "
         f"ElysiumTelegraphedAttack, or the readability rule has an exception "
         f"that looks exactly like every mob that keeps it")

# 4. The warning is something a player can actually perceive.
#
# A record with no sound and no particle would satisfy every other check here
# and none of the point: the enforcement would work perfectly and the player
# would be told nothing.
# Scoped to begin(), not to the file. Two of these rules have already been
# caught passing on a whole-file search, and a sound effect declared in a
# comment or an unused helper is exactly the shape that slips through one.
_begin = body_of(_warn_src, "public static int begin(LivingEntity attacker, float damage)")
if _begin is None:
    fail("ElysiumWarnings has no begin method, so nothing ever announces a blow")
elif "playSound" not in _begin or "sendParticles" not in _begin:
    fail("a telegraph no longer makes a sound or throws a particle. The record "
         "is only how the rule is enforced afterwards; the sound and the "
         "particle are the readability")
if "AttachmentType" in _warn_src or "serialize" in _warn_src:
    fail("warnings have become persistent. A wind-up lives for at most a "
         "second and is meaningless once the chunk reloads")
if "CAPACITY" not in _warn_src:
    fail("the warning map is unbounded; the key is a mob and a world has many")

# 5. The goal raises the warning once, from the mob's own damage.
_check_attack = body_of(_goal_src,
                        "protected void checkAndPerformAttack(LivingEntity target)")
if _check_attack is None:
    fail("ElysiumTelegraphedAttack no longer overrides the attack, so it is "
         "vanilla's goal with a longer name")
else:
    if "ElysiumWarnings.begin(" not in _check_attack:
        fail("the telegraphed attack never raises a warning")
    if "Attributes.ATTACK_DAMAGE" not in _check_attack:
        fail("the wind-up is no longer sized from the mob's own attack damage, "
             "so a mob scaled up by ElysiumScaling keeps a warning sized for "
             "what it used to hit for")
    if "getTicksUntilNextAttack() == windup" not in _check_attack:
        fail("the warning is no longer raised on exactly one tick. Announcing "
             "every tick of the approach turns it into a continuous noise, "
             "which a player stops hearing — the opposite of readable")

print(f"telegraph          : the cap is in the pipeline for every attacker, "
      f"{len(_mob_java)} mob source file(s) checked for a bare melee goal")

# ---------------------------------------------------------------------------
# H6: a maker's mark, and what quality is allowed to mean
# ---------------------------------------------------------------------------

CRAFT = os.path.join(SRC, "craft")
_quality_src = open(os.path.join(CRAFT, "ElysiumQuality.java"),
                    encoding="utf-8").read()
_mark_src = open(os.path.join(CRAFT, "ElysiumMakersMark.java"),
                 encoding="utf-8").read()
_log_src = open(os.path.join(CRAFT, "ElysiumCraftLog.java"),
                encoding="utf-8").read()
_craft_handler = open(os.path.join(SRC, "event/ElysiumCraftHandler.java"),
                      encoding="utf-8").read()

# 1. Quality does not come from research.
#
# The research tracks were built on a promise that they buy information and
# never power, and letting an Engineering rank raise the durability of every
# piece a player forges would keep the letter of that and break all of it: the
# tree would become something a player has to pursue. Practice is the honest
# source, and it has to stay the only one.
for _name, _src in (("ElysiumQuality", _quality_src),
                    ("ElysiumCraftLog", _log_src),
                    ("ElysiumCraftHandler", _craft_handler)):
    if "research" in _src.replace("Research", "research").lower() \
            and "ElysiumResearch" in _src:
        fail(f"{_name} now reads the research tracks. Quality comes from "
             f"practice at the bench; sourcing it from research would make the "
             f"tree something a player has to pursue, which is the one thing "
             f"B3 exists to prevent")

# 2. The grade is read before the count is raised.
#
# A maker's mark records the skill at the moment of making. Recording first
# puts the second grade on everybody's first attempt and the whole ladder
# starts one rung up — an off-by-one nobody notices until somebody writes down
# what the bottom grade is for.
_crafted = body_of(_craft_handler,
                   "public static void onCrafted(PlayerEvent.ItemCraftedEvent event)")
if _crafted is None:
    fail("nothing stamps a maker's mark, so crafted gear is anonymous")
else:
    _read_at = _crafted.find("gradeFor(")
    _record_at = _crafted.find("ElysiumCraftLog.record(")
    if _read_at < 0 or _record_at < 0:
        fail("the craft handler no longer both reads a grade and records the "
             "practice; one of the two halves of authorship is missing")
    elif _read_at > _record_at:
        fail("the practice count is raised before the grade is read, so every "
             "maker gets a free grade on their first attempt and the mark "
             "stops recording the skill at the time")

# 3. Only this mod's damageable gear is signed.
#
# Stamping another mod's sword is this mod rewriting somebody else's item, and
# a tooltip line nobody asked for is the politest version of that. A mark on an
# ingot is a grade that means nothing, since quality here *is* durability.
_signable = body_of(_craft_handler, "public static boolean signable(ItemStack stack)")
if _signable is None:
    fail("the craft handler no longer decides what it may sign, so it signs "
         "everything")
else:
    if "isDamageableItem" not in _signable:
        fail("the mark is being put on things with no durability. Quality here "
             "is durability, so a mark on an ingot is a grade that means "
             "nothing")
    if "NAMESPACE_PREFIX" not in _signable:
        fail("the craft handler signs items from other mods. That is this mod "
             "rewriting somebody else's item")

# 4. Quality is durability and nothing else.
if re.search(r"ATTACK_DAMAGE|Attributes\.", _quality_src):
    fail("ElysiumQuality touches an attribute. Craftsmanship changes how long "
         "a thing lasts, not what it does — the moment it does anything else "
         "it belongs in the damage pipeline and a player has to forge to keep up")

# 5. The mark travels with the item.
if "networkSynchronized" not in _mark_src or "persistent" not in _mark_src:
    fail("the maker's mark is no longer both saved and synced. A mark the "
         "client cannot see is a tooltip that never renders; one that is not "
         "saved is a signature that survives until the chunk unloads")

# 6. Every grade has its words.
_grade_ids = re.findall(r'^\s+[A-Z]+\("([a-z]+)",\s*\d+,', _quality_src, re.M)
if len(_grade_ids) < 3:
    fail(f"only {len(_grade_ids)} quality grade(s) could be read; the language "
         f"check is checking nothing")
_absent = [f"elysium.craft.quality.{one}" for one in _grade_ids
           if f"elysium.craft.quality.{one}" not in _lang]
if _absent:
    fail(f"the language file is missing {_absent}, which would render as the "
         f"raw key on the tooltip of every piece somebody forged")

print(f"maker's marks      : {len(_grade_ids)} grades from practice alone, "
      f"durability only, stamped before the count is raised")

# ---------------------------------------------------------------------------
# G1: issued work, a deadline, and a standing consequence
# ---------------------------------------------------------------------------

CONTRACT = os.path.join(SRC, "contract")
_terms_src = open(os.path.join(CONTRACT, "ElysiumTerms.java"),
                  encoding="utf-8").read()
_contracts_src = open(os.path.join(CONTRACT, "ElysiumContracts.java"),
                      encoding="utf-8").read()
_record_c_src = open(os.path.join(CONTRACT, "ElysiumContract.java"),
                     encoding="utf-8").read()

# 1. Expiry goes through exactly one door.
#
# Nothing ticks contracts: every read sweeps the list and applies the
# consequence for anything out of time. That only works while every read goes
# through held() — a second accessor reading the attachment directly would be
# a path on which a lapsed contract is still live, and the deadline would hold
# for some interactions and not others.
_direct = len(re.findall(r"getData\(HELD\.get\(\)\)", _contracts_src))
if _direct != 1:
    fail(f"the contract attachment is read directly {_direct} time(s). Expiry "
         f"is applied on read, so every path has to go through held() — any "
         f"other reader is one on which a lapsed contract is still live")
_held = body_of(_contracts_src, "public static List<ElysiumContract> held(Player player)")
if _held is None:
    fail("ElysiumContracts has no held method")
elif "expired(" not in _held or "punish(" not in _held:
    fail("held() no longer sweeps for expiry, so a deadline is a suggestion "
         "and nothing ever applies the consequence §19.2 asks for")

# 2. Failing costs standing.
_punish = body_of(_contracts_src, "private static void punish(Player player, ElysiumContract contract)")
if _punish is None:
    fail("nothing applies a penalty when a contract lapses")
elif "addFavor" not in _punish or "-cost" not in _punish:
    fail("letting a contract lapse no longer moves the issuer's meter. Issued "
         "work with a deadline and no consequence is not a contract")
if "addSuspicion" in _punish:
    fail("failing a contract raises Suspicion. Being unreliable is not the "
         "same as being seditious, and conflating them means a player who "
         "missed a delivery gets hunted")

# 3. An office never asks you to kill its own.
_offer = body_of(_contracts_src,
                 "public static ElysiumContract offer(Player player, boolean empire,")
if _offer is None:
    fail("ElysiumContracts no longer writes contracts")
elif "empire ? ElysiumFaction.UNSWORN : ElysiumFaction.EMPIRE" not in _offer:
    fail("a cull contract may name the issuing office's own faction. An office "
         "does not pay you to kill its people, and being asked to would be the "
         "mod contradicting its own standing system in front of the player")

# 4. A delivery asks for something a new player can get.
#
# Read off the stripped source. Both this rule and the countdown rule below
# fired on their own explanatory comments the first time they ran — a check
# that reads prose is a check that fails whenever somebody writes down why it
# exists, which is the opposite of what the comments in this file are for.
_contracts_code = strip_comments(_contracts_src)
_record_c_code = strip_comments(_record_c_src)
if "neutronium" in _contracts_code or "aetherium" in _contracts_code:
    fail("a delivery contract asks for one of this mod's own materials. The "
         "first contract anybody sees would be one they cannot take")

# 5. Progress is capped at what was asked for.
_advanced = body_of(_record_c_src, "public ElysiumContract advancedBy(int more)")
if _advanced is None:
    fail("a contract can no longer be advanced")
elif "Math.min(quantity" not in _advanced:
    fail("contract progress is uncapped, so the readout says \"34 of 32\" — "
         "which reads as the office not knowing what it asked for")

# 6. The deadline is a timestamp, not a countdown.
if re.search(r"\bticksLeft\b|\bcountdown\b", _record_c_code):
    fail("a contract carries a countdown. That has to be ticked for every "
         "contract of every player including offline ones; issued-plus-deadline "
         "is the same fact, read when somebody asks")

# 7. Every kind has its words.
_kind_src = open(os.path.join(CONTRACT, "ElysiumContractKind.java"),
                 encoding="utf-8").read()
_kind_ids = re.findall(r'^\s+[A-Z]+\("([a-z]+)",\s*[\d.]+F\)[,;]', _kind_src, re.M)
if len(_kind_ids) < 2:
    fail("fewer than two contract kinds could be read; the language check "
         "below is checking nothing")
_absent = [key for one in _kind_ids
           for key in (f"elysium.contract.kind.{one}",
                       f"elysium.contract.kind.{one}.line")
           if key not in _lang]
if _absent:
    fail(f"the language file is missing {_absent}, which would render as a raw "
         f"key on the one line describing what an office wants")

print(f"contracts          : {len(_kind_ids)} kinds, expiry applied on read "
      f"through one accessor, failing costs standing and never Suspicion")

# ---------------------------------------------------------------------------
# H4: villagers get better at what they repeatedly do
# ---------------------------------------------------------------------------

PRACTICE = os.path.join(SRC, "practice")
_practice_src = open(os.path.join(PRACTICE, "ElysiumPractice.java"),
                     encoding="utf-8").read()
_plog_src = open(os.path.join(PRACTICE, "ElysiumPracticeLog.java"),
                 encoding="utf-8").read()
_plog_code = strip_comments(_plog_src)
_social_src = open(os.path.join(SRC, "event/ElysiumSocialHandler.java"),
                   encoding="utf-8").read()

# 1. Practice is keyed by what is sold, not by the seller's profession.
#
# The whole distinction from vanilla's Novice-to-Master. Keyed by profession
# this would be a second copy of a bar the game already has; keyed by the
# result it is a fact about what a villager has actually spent their life on.
_key = body_of(_plog_src, "public static String keyFor(ItemStack result)")
if _key is None:
    fail("ElysiumPracticeLog no longer keys practice on anything")
elif "BuiltInRegistries.ITEM" not in _key:
    fail("practice is no longer keyed on the item a villager sells. Keyed on "
         "anything else it is vanilla's villager level with a new name")
if re.search(r"VillagerProfession|getVillagerData", _plog_code):
    fail("ElysiumPracticeLog reads the villager's profession. Practice is per "
         "trade — two bakers who have sold different things are differently "
         "good at different things, and that is the feature")

# 2. The special price is set, never added.
#
# Vanilla recomputes the special price on every open for Hero of the Village
# and for a villager's opinion. Adding would compound the discount a little
# further every time the screen was closed and reopened — a bug that only
# appears after somebody has stood at a trade screen for a while, and then
# appears as free goods.
_price = body_of(_plog_src, "public static void price(LivingEntity seller, MerchantOffers offers)")
if _price is None:
    fail("nothing applies a practice discount, so the bands do nothing")
else:
    if "setSpecialPriceDiff" not in _price:
        fail("the practice discount is no longer applied to the offers")
    if "addToSpecialPriceDiff" in _price:
        fail("the practice discount is *added* to the special price. Vanilla "
             "recomputes that value on every open, so the discount would "
             "compound each time the screen was reopened and end in free goods")

# 3. The discount is applied before the screen opens, not after the trade.
if "ElysiumPracticeLog.price(" not in _social_src:
    fail("nothing prices a villager's offers before the trade screen opens, so "
         "the discount is worked out after the player has already paid")
if "ElysiumPracticeLog.record(" not in _social_src:
    fail("nothing records a completed trade, so no villager ever becomes "
         "practised at anything")

# 4. The floor is real and the discount is a proportion.
if "FLOOR" not in _practice_src:
    fail("the practice discount has lost its floor. A trade that reaches zero "
         "is an infinite emerald machine that looks like it is working, and "
         "the player who finds it will not report it")
_price_for = body_of(_practice_src, "public int priceFor(int baseCost)")
if _price_for is None:
    fail("ElysiumPractice no longer prices anything")
elif "1.0F - discount" not in _price_for:
    fail("the practice discount is no longer a proportion of the price. A flat "
         "number off takes three quarters from a cheap trade and a twentieth "
         "from a dear one, which is exactly backwards")

print(f"practice           : keyed on what is sold, a proportional discount "
      f"with a floor, set rather than added")

# ---------------------------------------------------------------------------
# E9: destructible combat, and everything that stops it
# ---------------------------------------------------------------------------

SCAR = os.path.join(SRC, "scar")
_scar_src = open(os.path.join(SCAR, "ElysiumScarring.java"),
                 encoding="utf-8").read()
_cover_src = open(os.path.join(SCAR, "ElysiumCover.java"), encoding="utf-8").read()
_cover_code = strip_comments(_cover_src)

# 1. Every guard is present.
#
# Listed by name rather than counted, because the way this feature becomes a
# grief machine is one guard quietly going missing, and a count would be
# satisfied by a different guard being added.
_may_break = body_of(_cover_src, "public static boolean mayBreak(ServerLevel level, BlockPos at)")
if _may_break is None:
    fail("ElysiumCover no longer decides what may be broken")
else:
    for _guard, _why in (
            ("isLoaded", "a blow reaches into unloaded chunks"),
            ("is(FRAGILE)", "untagged blocks can be broken, so the first "
                            "content mod somebody installs loses a crop field"),
            ("hasBlockEntity", "chests and machines can be broken, and their "
                               "contents go with them"),
            ("BlockTags.PORTALS", "portals can be broken"),
            ("BlockTags.BEDS", "beds can be broken, so a fight can take away a "
                               "player's respawn"),
            ("ElysiumScarring.breakable", "the hardness rule is no longer "
                                          "consulted at all")):
        if _guard not in _may_break:
            fail(f"ElysiumCover.mayBreak has lost its {_guard} guard: {_why}")

# 2. Claims are never touched.
_scar_body = body_of(_cover_src, "public static void scar(Level level, Entity victim, float damage)")
if _scar_body is None:
    fail("ElysiumCover no longer scars anything")
elif "inShelteredClaim" not in _scar_body:
    fail("blocks can be broken inside a filed claim. A claim is the mod's "
         "existing answer to \"how do I stop this happening to my base\", and "
         "a second answer would mean the one a player finds first is wrong")

# 3. The blast is bounded, and the block is dropped rather than deleted.
if "MOST_PER_BLOW" not in _scar_body:
    fail("a blow no longer has a limit on how many blocks it takes")
if "destroyBlock(at, true)" not in _cover_code:
    fail("a broken block is deleted rather than dropped. A block that vanishes "
         "is a block a player has lost; one that falls at their feet is a "
         "fight that moved the scenery")

# 4. The set of blows that scar is the set that were announced.
if "ElysiumTelegraph.isDangerous" not in _scar_src:
    fail("the scarring threshold is no longer read off the telegraph's. The "
         "two sets have to be the same one, or there is a blow in the game "
         "that takes a block out of the world with no warning at all")

# 5. It is measured on the swing, not on what landed.
_incoming2 = body_of(_combat,
                     "public static void onIncomingDamage(LivingIncomingDamageEvent event)")
if _incoming2 is not None and "ElysiumCover.scar(" in _incoming2:
    if "event.getAmount()" not in _incoming2.split("ElysiumCover.scar(")[1][:120]:
        fail("scarring is measured on the damage that landed rather than the "
             "blow that was swung, so the better armoured a player is the more "
             "solid the world becomes around them")
elif _incoming2 is not None:
    fail("nothing in the damage pipeline scars the world, so E9 is a package "
         "nothing calls")

# 6. The tag ships, and holds nothing structural.
_fragile = os.path.join(RES, "data/elysiumlib/tags/block/fragile_cover.json")
if not os.path.isfile(_fragile):
    fail("the fragile_cover tag is missing, so nothing is breakable — the "
         "feature is off while looking like it is on")
else:
    _values = json.load(open(_fragile, encoding="utf-8")).get("values", [])
    _forbidden = ("planks", "stone", "chest", "door", "obsidian", "iron",
                  "log", "brick", "concrete", "barrel", "furnace", "bed")
    for _value in _values:
        _plain = str(_value).lstrip("#")
        for _word in _forbidden:
            if _word in _plain:
                fail(f"the fragile_cover tag names {_value!r}, which is "
                     f"something players build with. Cover that grew there "
                     f"comes apart; cover somebody built stays up")
    if not _values:
        fail("the fragile_cover tag is empty as shipped")

print(f"destructible cover : {len(_values)} tagged entries, "
      f"{6} guards on every break, claims untouched, one block per blow")

# ---------------------------------------------------------------------------
# The files the harnesses execute: nothing may drag a registry into them
# ---------------------------------------------------------------------------
#
# Every system in this library is split the same way: the rules and the numbers
# in files that need nothing to load, and the registration, attachments and
# world-reading in files beside them. The harnesses execute the first half —
# hundreds of thousands of assertions across the research curriculum, the
# stress model, the telegraph promise and the quality ladder — and can only do
# so while that half stays clean.
#
# This began as four copies of the same check, one per feature. It is a table
# now because the same mistake has been made three times: a constant that
# belongs on the registering class by subject cannot live there by mechanics,
# because reading it runs the static initialiser and pulls in NeoForge's
# registries. ElysiumLevelling was split out of ElysiumCharacter for that;
# ElysiumResearchRank.CEILING and ElysiumQuality.COUNT_CEILING are there for
# the same reason. A table means the fifth system is covered on the day it is
# written rather than the day somebody remembers.
# ---------------------------------------------------------------------------
# C6: unregistered works
# ---------------------------------------------------------------------------
#
# The numbers are executed by WorksHarness. What is checked here is the wiring
# the harness cannot see: that the handler charges only what is outside a
# claim, lets doctrine speak, and keeps the notice-threshold rule every other
# inspection path keeps.

_works_handler_path = os.path.join(SRC, "event/ElysiumWorksHandler.java")
if not os.path.isfile(_works_handler_path):
    fail("event/ElysiumWorksHandler.java is gone, so building outside a claim "
         "is free again — the incentive this feature turned the right way up")
else:
    _wh = strip_comments(open(_works_handler_path, encoding="utf-8").read())
    _place = body_of(_wh, "public static void onPlace(")
    if _place is None:
        fail("ElysiumWorksHandler has no onPlace; nothing feeds the works")
    else:
        # 1. A claimed placement is the claim's business.
        if not re.search(r"ElysiumClaims\.of\(\w+\)\.at\([^;]*\)\s*!=\s*null\s*\)"
                         r"\s*\{\s*return\s*;", _place):
            fail("ElysiumWorksHandler.onPlace no longer returns for a position "
                 "inside a claim. The claim already raises its floor for that "
                 "placement, so the one player who registered would be charged "
                 "twice for the same wall")
        # 2. Doctrine scales the weight, before the site sees it.
        _scaled = _place.find("ElysiumConspicuity.scaled(")
        _built = _place.find(".built(")
        if _scaled < 0 or "suspicionMultiplier(" not in _place:
            fail("ElysiumWorksHandler.onPlace does not scale by doctrine. A "
                 "Clandestine builder would be exactly as loud as a Sanctioned "
                 "one, and the tree's commitment would be a word")
        elif _built >= 0 and _scaled > _built:
            fail("ElysiumWorksHandler scales by doctrine after the site has "
                 "already recorded the unscaled weight")
        # 3. No post for a player below the notice threshold.
        if ".plant(" in _place and not re.search(
                r"getSuspicion\(\w+\)\s*>=\s*ElysiumStanding\.NOTICE", _place):
            fail("ElysiumWorksHandler plants an inspection post without the "
                 "notice-threshold gate. Below it the Empire watches nobody, "
                 "and this would be the one path where that failed")
        # 4. Creative building is not play against the Empire.
        if "isCreative()" not in _place:
            fail("ElysiumWorksHandler charges creative builders; the hazard "
                 "handler exempts them and so must this")
        # 5. Suspicion is filed under its own cause.
        if "CAUSE_WORKS" not in _place:
            fail("a found works is filed under some other cause, so the "
                 "archive cannot tell a file you opened from one the Empire "
                 "opened for you")

_works_path = os.path.join(SRC, "works/ElysiumWorks.java")
if os.path.isfile(_works_path):
    _built_body = body_of(strip_comments(open(_works_path, encoding="utf-8").read()),
                          "public ElysiumSite.Placed built(")
    if _built_body is None or ".placed(" not in _built_body:
        fail("ElysiumWorks.built no longer delegates to ElysiumSite.placed. "
             "The rule has moved somewhere the harness does not execute it")

# 6. The conspicuous tag is industry, not shelter.
#
# The feature is only fair if a house costs nothing. A plank, a stone brick or
# a pane of glass in this tag makes every player's first night a survey.
_consp = os.path.join(RES, "data/elysiumlib/tags/block/works/conspicuous.json")
_industry = os.path.join(RES, "data/elysiumlib/tags/block/works/industry.json")
for _path in (_consp, _industry):
    if not os.path.isfile(_path):
        fail(f"{os.path.relpath(_path, RES)} is missing; the handler reads "
             f"a tag nothing defines")
if os.path.isfile(_consp):
    try:
        _values = json.load(open(_consp, encoding="utf-8")).get("values", [])
    except ValueError as _err:
        fail(f"works/conspicuous.json does not parse: {_err}")
        _values = []
    _SHELTER = re.compile(
        r"(planks|_log$|_wood$|(?:^|_)stone|cobble|brick|dirt|grass|sand|gravel|glass"
        r"|wool|door|slab|stairs|fence|wall$|torch|lantern|bed$|leaves|clay"
        r"|terracotta|concrete|deepslate|tuff|andesite|diorite|granite|mud)")
    _houses = [v for v in _values if isinstance(v, str)
               and _SHELTER.search(v.split(":")[-1].lstrip("#"))]
    if _houses:
        fail(f"works/conspicuous.json lists {_houses[:3]} — building "
             f"material. The survey is for industry; a tag with shelter in it "
             f"taxes every player's first house")

# ---------------------------------------------------------------------------
# H8: wounds and the Med bay
# ---------------------------------------------------------------------------
#
# The numbers — caps, chances, heal times, rates — are executed by
# MedicalHarness. These are the decisions it cannot see.

_med_handler_path = os.path.join(SRC, "event/ElysiumMedicalHandler.java")
_med_path = os.path.join(SRC, "medical/ElysiumMedical.java")
if not os.path.isfile(_med_handler_path) or not os.path.isfile(_med_path):
    fail("the medical handler or ElysiumMedical is gone; wounds are neither "
         "taken nor mended")
else:
    _mh = strip_comments(open(_med_handler_path, encoding="utf-8").read())
    _mm = strip_comments(open(_med_path, encoding="utf-8").read())

    _damaged = body_of(_mh, "public static void onDamaged(")
    if _damaged is None:
        fail("ElysiumMedicalHandler has no onDamaged; nothing wounds anybody")
    else:
        # 1. On what landed. Armour that does not protect against lasting
        #    injury is armour doing half its job.
        if "getNewDamage()" not in _damaged or "getOriginalDamage()" in _damaged:
            fail("wounds are rolled on the blow as swung rather than on what "
                 "landed. Armour would then do nothing about lasting injury — "
                 "the one thing a player most wants it to do")
        if "isCreative()" not in _damaged:
            fail("creative players can be wounded")
        if ".wounded(" not in _damaged:
            fail("onDamaged no longer goes through ElysiumInjury.wounded, which "
                 "is where the cap lives")

    # 2. Most specific first: a fire arrow is a burn, not a cut.
    _kind = body_of(_mh, "public static ElysiumWoundKind kindOf(")
    if _kind is None:
        fail("ElysiumMedicalHandler.kindOf is gone")
    else:
        _fire = _kind.find("IS_FIRE")
        _proj = _kind.find("IS_PROJECTILE")
        if _fire < 0 or _proj < 0 or _fire > _proj:
            fail("kindOf tests for a projectile before fire, so a fire arrow "
                 "leaves a cut. Most specific first")
        if "return null" not in _kind:
            fail("kindOf wounds on every damage source; drowning and the void "
                 "are ways of dying, not wounds")

    # 3. The Med bay is what is checked on the recovery step.
    _tick = body_of(_mh, "public static void onTick(")
    if _tick is None or "ElysiumRooms.MED_BAY" not in _tick \
            or "isSleeping()" not in _tick:
        fail("the recovery step no longer asks whether the player is in a Med "
             "bay or asleep, so the room is decoration")

    _login = body_of(_mh, "public static void onLogin(")
    if _login is None or "ElysiumMedical.apply(" not in _login:
        fail("wound penalties are not re-applied at login. They are transient, "
             "so a wounded player who logs back in would walk at full speed for "
             "five seconds and then be slowed, which reads as a bug")

    # 4. Death is a clean slate. The one mercy the respawn screen is for.
    if "copyOnDeath" in _mm:
        fail("the injury attachment is copied on death. A wound that outlives "
             "its player is a death spiral: respawn weaker, die sooner")

    # 5. The bar never shows more health than it holds.
    _apply = body_of(_mm, "public static void apply(")
    if _apply is None or "setHealth(" not in _apply:
        fail("ElysiumMedical.apply no longer clamps health to the new maximum; "
             "a laceration would leave the bar showing hearts that are not there")

# 6. The Med bay is a shelter plus one.
_rooms_src = strip_comments(read("room/ElysiumRooms.java"))
_room_decl = re.compile(
    r"public static final ElysiumRoom (\w+) = ElysiumRoom\.register\(\s*"
    r"id\(\"(\w+)\"\),\s*(\d+),\s*(\d+),\s*Map\.of\(([^)]*)\)", re.S)
_rooms_found = {m.group(1): (int(m.group(3)), m.group(5))
                for m in _room_decl.finditer(_rooms_src)}
if "MED_BAY" not in _rooms_found or "SHELTER" not in _rooms_found:
    fail("the Med bay or the shelter is no longer registered")
else:
    _mb_rank, _mb_req = _rooms_found["MED_BAY"]
    _sh_rank, _sh_req = _rooms_found["SHELTER"]
    if _mb_rank <= _sh_rank:
        fail("the Med bay does not outrank the shelter, so a bedroom with a "
             "brewing stand in it is still read as a bedroom and nothing heals "
             "faster")
    for _needed in ("BlockTags.BEDS", "LIGHT", "MEDICAL"):
        if _needed not in _mb_req:
            fail(f"the Med bay no longer asks for {_needed}. It is meant to be "
                 f"the shelter a player already sleeps in, plus one block")
    for _other in ("GREENHOUSE", "BUNKER"):
        if _other in _rooms_found and _rooms_found[_other][0] <= _mb_rank:
            fail(f"the Med bay outranks the {_other.lower()}, so a bunker "
                 f"with a cauldron in it stops being a bunker")

_medical_tag = os.path.join(RES, "data/elysiumlib/tags/block/room/medical.json")
if not os.path.isfile(_medical_tag):
    fail("room/medical.json is missing; no room can ever be a Med bay")
else:
    try:
        _mvals = json.load(open(_medical_tag, encoding="utf-8")).get("values", [])
    except ValueError as _err:
        fail(f"room/medical.json does not parse: {_err}")
        _mvals = []
    if not _mvals:
        fail("room/medical.json is empty; no room can ever be a Med bay")
    _giveaway = [v for v in _mvals if isinstance(v, str)
                 and re.search(r"(_bed$|torch|lantern|glowstone|lamp|light)", v)]
    if _giveaway:
        fail(f"room/medical.json lists {_giveaway[:2]} — something every "
             f"shelter already has, which makes every bedroom a Med bay")

# ---------------------------------------------------------------------------
# Every effect has an icon
# ---------------------------------------------------------------------------
#
# The game finds a mob effect's icon at textures/mob_effect/<id>.png under the
# effect's own namespace, and says nothing when it is not there — the status
# bar shows the purple-and-black square and the log is silent. All five hazard
# wards shipped that way, because the art pipeline wrote into elysium-core's
# namespace and the effects are elysiumlib's.
#
# Read from the registrations, so a sixth ward is held to this without anyone
# remembering to add it here.
_wards_src = strip_comments(read("hazard/ElysiumWards.java"))
_effect_ids = set(re.findall(r'\bward\(\s*"([a-z0-9_]+)"', _wards_src)) | \
    set(re.findall(r'EFFECTS\.register\(\s*"([a-z0-9_]+)"', _wards_src))
if not _effect_ids:
    fail("no effects found in ElysiumWards; the icon check is checking nothing")
try:
    from PIL import Image as _Image
except ImportError:
    _Image = None
for _eid in sorted(_effect_ids):
    _icon = os.path.join(RES, f"assets/elysiumlib/textures/mob_effect/{_eid}.png")
    if not os.path.isfile(_icon):
        fail(f"elysiumlib:{_eid} has no icon at textures/mob_effect/{_eid}.png. "
             f"The status bar shows the missing-texture square, and nothing in "
             f"the log says why")
        continue
    if _Image is not None:
        _img = _Image.open(_icon).convert("RGBA")
        if _img.size != (18, 18):
            fail(f"the {_eid} icon is {_img.size[0]}x{_img.size[1]}; vanilla "
                 f"draws effects at 18x18 and anything else is rescaled soft")
        if not any(px[3] for px in _img.getdata()):
            fail(f"the {_eid} icon is fully transparent — present, and invisible")
print(f"effect icons       : {len(_effect_ids)} effect(s), each with an 18x18 icon "
      f"in its own namespace")

# ---------------------------------------------------------------------------
# Rune tiers reach every number a rune produces
# ---------------------------------------------------------------------------
#
# ElysiumRuneTiers holds the rule and RuneTierHarness proves it. What the
# harness cannot see is whether each place a rune's number is produced actually
# asks for the tier — and a place that forgets is a rune that is tier 5 on the
# tooltip and tier 1 in the fight, which nobody would ever notice.
_sockets_src = strip_comments(read("item/ElysiumSockets.java"))
_apply = body_of(_sockets_src, "public static ItemAttributeModifiers applyRunes(")
if _apply is None or "socketed(" not in _apply or ".scale()" not in _apply:
    fail("ElysiumSockets.applyRunes no longer scales a rune's attribute bonus by "
         "its tier. A tier-4 Plasmaforge would add what a tier-1 adds")

_rune_src = strip_comments(read("item/ElysiumRune.java"))
_sum = body_of(_rune_src, "private static double sumOn(")
if _sum is None or ".scale()" not in _sum:
    fail("ElysiumRune.sumOn no longer weights dodge and heat reduction by tier")
_tsum = body_of(_rune_src, "private static float tierSumOn(")
if _tsum is None or ".scale()" not in _tsum:
    fail("ElysiumRune.tierSum no longer counts by tier; a stacking rune's "
         "capacity ignores what its copies were merged to")

_tick_src = strip_comments(read("event/ElysiumRuneTickHandler.java"))
_run = body_of(_tick_src, "private static void run(")
if _run is None or "socketed.scale()" not in _run:
    fail("the rune tick no longer hands each effect its tier, so every "
         "effect runs at tier 1 whatever was merged")

_gd_src = strip_comments(read("item/ElysiumGearData.java"))
for _sig, _what in (("public ElysiumGearData withAscendedTier(", "ascending"),
                    ("public ElysiumGearData withReforgedStats(", "reforging")):
    _b = body_of(_gd_src, _sig)
    if _b is None or "runeTiers" not in _b:
        fail(f"{_what} a piece of gear drops its runes' tiers: the record is "
             f"rebuilt without them and every socketed rune falls to tier 1")
if 'optionalFieldOf("rune_tiers"' not in _gd_src:
    fail("rune_tiers is not optional in the gear data codec. Every piece "
         "socketed before tiers has no such field, and a required one would "
         "fail to decode and strip their runes")

# ---------------------------------------------------------------------------
# Rune stats are not capped
# ---------------------------------------------------------------------------
#
# The owner's instruction, and a reversal: dodge and heat reduction from runes
# used to stop at 60%. Now they are N x base all the way. What must still
# hold is the one thing that is not a cap but arithmetic — a blow reduced past
# 100% is a blow of nothing, never a negative blow, which would heal the
# victim and cancel the attacker's bonuses applied after it.
_combat_src = strip_comments(read("event/ElysiumCombatHandler.java"))
_incoming = body_of(_combat_src, "public static void onIncomingDamage(")
if _incoming is None:
    fail("ElysiumCombatHandler.onIncomingDamage is gone")
else:
    if "MAX_RUNE_REDUCTION" in _combat_src or re.search(
            r"nextFloat\(\)\s*<\s*Math\.min\(", _incoming):
        fail("rune dodge is capped again. Rune stats are not capped")
    if re.search(r"Math\.min\(\s*[\w.]+\s*,\s*reduction\s*\)", _incoming):
        fail("rune heat reduction is capped again. Rune stats are not capped")
    if "Math.max(0.0F, 1.0F - reduction)" not in _incoming:
        fail("heat reduction past 100% is no longer floored at zero damage: the "
             "factor goes negative and a hit heals its victim")

# ---------------------------------------------------------------------------
# Every percentage is stat / (stat + b), and nothing is capped
# ---------------------------------------------------------------------------
#
# The owner's ruling: "uncap everything, but give percentile effects
# stat/stat+b scaling". ElysiumShares is the rule and StatHarness executes it.
# These are the places a percentage is produced; each must go through the rule
# rather than add percentages or clamp them, and each used to do one or both.
def _uses(path, sig, needle, why):
    body = body_of(strip_comments(read(path)), sig)
    if body is None or needle not in body:
        fail(why)
    # Calling the rule is not enough: a clamp wrapped around the call still
    # contains it. That exact mutation — Math.min(sane(share), 0.95F) — passed
    # this check once, so a Math.min anywhere in these bodies now fails too.
    elif re.search(r"Math\.min\(", body):
        fail(why + " (it calls the rule, and then clamps what comes back)")
    return body

_uses("stats/ElysiumStats.java", "public static float combine(",
      "ElysiumShares.add(",
      "ElysiumStats.combine no longer adds shares as stats; it multiplied what "
      "each let through and clamped at 99.9%")
_uses("stats/ElysiumStats.java", "public static float clampShare(",
      "ElysiumShares.sane(",
      "ElysiumStats.clampShare is a ceiling again rather than a type check")
_uses("hazard/ElysiumResistance.java", "public static float combine(",
      "ElysiumShares.add(",
      "hazard protection no longer adds shares as stats")
_uses("hazard/ElysiumResistance.java", "public static float clampShare(",
      "ElysiumShares.sane(",
      "hazard protection clamps each source again (it was 95%)")
_uses("hazard/ElysiumProtection.java", "public static float shareOf(",
      "pieceShare(",
      "a piece of gear's hazard share is computed somewhere other than "
      "ElysiumResistance.pieceShare, which the harness executes")
_uses("stats/ElysiumStat.java", "public float curveOf(",
      "ElysiumShares.share(",
      "curve stats compute their own share; through ElysiumShares.share a "
      "finite stat can never round to 100%")
_uses("item/ElysiumSockets.java", "public static ItemAttributeModifiers applyRunes(",
      "isShare()",
      "rune affixes on 0-to-1 attributes (knockback resistance) scale their "
      "share linearly again instead of their stat")
_doc_body = _uses("doctrine/ElysiumDoctrines.java", "public static float hazardShare(",
                  "return best;",
                  "the doctrine hazard share is capped again (it was 50%)")

_stats_code = strip_comments(read("stats/ElysiumStats.java"))
_capped = re.findall(r'ElysiumStat\.curve\(id\("(\w+)"\)[^;]*?,\s*([\d.]+F)\)', _stats_code)
if _capped:
    fail(f"Elysium's {_capped[0][0]} stat has a ceiling of {_capped[0][1]}. The "
         f"owner ruled no caps: pass ElysiumStats.UNCAPPED")

_combat_code = strip_comments(read("event/ElysiumCombatHandler.java"))
_inc = body_of(_combat_code, "public static void onIncomingDamage(")
if _inc is not None:
    if "ElysiumShares.add(" not in _inc:
        fail("dodge no longer adds runes, Reflexes and passives as stats")
    if re.search(r"Math\.min\(\s*0\.\d+F\s*,\s*blunt\s*\)", _inc) \
            or not re.search(r"ElysiumShares\.fromOdds\(\s*blunt", _inc):
        fail("elemental blunting is a clamped percentage again (it was 90%); "
             "it is a stat, and removes stat / (stat + 1)")

# ---------------------------------------------------------------------------
# Vanilla's armour, Protection and attribute ceilings are lifted, for players
# ---------------------------------------------------------------------------
_ah = strip_comments(read("event/ElysiumArmourHandler.java"))
for _needed, _why in (
        ("Reduction.ARMOR", "the armour reduction is not replaced, so vanilla's 80% ceiling stands"),
        ("Reduction.ENCHANTMENTS", "the Protection reduction is not replaced, so vanilla's 80% ceiling stands"),
        ("instanceof Player", "the uncapped armour rules reach mobs too; they are for the player"),
        ("BYPASSES_ARMOR", "damage that bypasses armour is reduced by armour"),
        ("BYPASSES_ENCHANTMENTS", "damage that bypasses enchantments is reduced by Protection"),
        ("modifyArmorEffectiveness(", "Breach and its kin no longer pierce armour"),
        ("ElysiumAttributeLimits.uncapped(", "armour is read through the clamped attribute value, so it stops at 30")):
    if _needed not in _ah:
        fail(_why)
_rules = strip_comments(read("stats/ElysiumArmourRules.java"))
if re.search(r"Math\.min\(", _rules):
    fail("ElysiumArmourRules clamps something; vanilla's ceilings are what it replaces")
_lim = strip_comments(read("stats/ElysiumAttributeLimits.java"))
_raise = body_of(_lim, "public static int raise(")
if _raise is None or not re.search(r"catch\s*\(\s*ReflectiveOperationException\s*\|\s*RuntimeException", _raise):
    fail("lifting attribute ceilings is not guarded; a failure there would stop the game starting")
_at = os.path.join(RES, "META-INF/accesstransformer.cfg")
_toml = open(os.path.join(RES, "META-INF/neoforge.mods.toml"), encoding="utf-8").read()
if not os.path.isfile(_at) or "RangedAttribute maxValue" not in open(_at, encoding="utf-8").read():
    fail("the access transformer that makes attribute ceilings writable is missing")
if 'file="META-INF/accesstransformer.cfg"' not in _toml:
    fail("mods.toml does not declare the access transformer, so NeoForge never applies it")

# ---------------------------------------------------------------------------
# The hotbar, in Elysium's palette and vanilla's geometry
# ---------------------------------------------------------------------------
#
# The game draws items and the selection frame at fixed offsets, so these
# sprites must match vanilla's sizes and slot layout to the pixel, or every
# item in the hotbar sits off-centre.
_HUD = os.path.join(RES, "assets/minecraft/textures/gui/sprites/hud")
_HUD_SIZES = {"hotbar.png": (182, 22), "hotbar_selection.png": (24, 23),
              "hotbar_offhand_left.png": (29, 24), "hotbar_offhand_right.png": (29, 24)}
try:
    from PIL import Image as _HImg
except ImportError:
    _HImg = None
_pal_src = read("client/ElysiumPalette.java")
_pal = {int(v, 16) & 0xFFFFFF for v in re.findall(r"= 0x([0-9A-Fa-f]{8});", _pal_src)}
for _name, _size in _HUD_SIZES.items():
    _path = os.path.join(_HUD, _name)
    if not os.path.isfile(_path):
        fail(f"hud/{_name} is missing; run elysium-lib/tools/gen_hud.py")
        continue
    if _HImg is None:
        continue
    _img = _HImg.open(_path).convert("RGBA")
    if _img.size != _size:
        fail(f"hud/{_name} is {_img.size}, vanilla's is {_size}; every item "
             f"in the hotbar would sit off its slot")
    _off = {(r << 16) | (g << 8) | b for (r, g, b, a) in _img.getdata() if a}
    if _off - _pal:
        fail(f"hud/{_name} uses {len(_off - _pal)} colour(s) that are not in "
             f"ElysiumPalette, so the hotbar has drifted from the GUI")
if _HImg is not None and os.path.isfile(os.path.join(_HUD, "hotbar.png")):
    _bar = _HImg.open(os.path.join(_HUD, "hotbar.png")).convert("RGBA")
    _sel = _HImg.open(os.path.join(_HUD, "hotbar_selection.png")).convert("RGBA")
    _slot = int(re.search(r"SLOT = 0x([0-9A-Fa-f]{8});", _pal_src).group(1), 16) & 0xFFFFFF
    # Each slot's 16x16 item area is the well's inside.
    for _i in range(9):
        for (_x, _y) in ((3 + 20 * _i, 3), (18 + 20 * _i, 18)):
            _r, _g, _b, _a = _bar.getpixel((_x, _y))
            if ((_r << 16) | (_g << 8) | _b) != _slot:
                fail(f"slot {_i + 1}'s item area in hotbar.png is not a well; "
                     f"the item is drawn at x={3 + 20 * _i}")
                break
    # The selection frame's window, where the item shows through, is clear.
    if any(_sel.getpixel((_x, _y))[3] for _x in range(4, 20) for _y in range(4, 20)):
        fail("hotbar_selection.png paints over the item it is meant to frame")
print(f"hotbar             : {len(_HUD_SIZES)} sprites at vanilla's geometry, in the Elysium palette")

# ---------------------------------------------------------------------------
# The combat attributes (SPEC_CLONE P0.1)
# ---------------------------------------------------------------------------
# The table is executed by AttributeHarness. What a harness cannot see is the
# wiring: that the registry is on the bus, that every row has a name a player
# can read, that the ceiling is the shared one, and that the chance display is
# the tested one rather than a second copy of the arithmetic.
_ATTR = os.path.join(SRC, "attribute")
_spec_src = open(os.path.join(_ATTR, "ElysiumAttributeSpec.java"), encoding="utf-8").read()
_rows = re.findall(r'(?:flat|mult|chance)\("([a-z_]+)"', _spec_src) + \
        re.findall(r'new ElysiumAttributeSpec\("([a-z_]+)"', _spec_src)
_lang = json.load(open(os.path.join(RES, "assets/elysiumlib/lang/en_us.json"), encoding="utf-8"))
for _row in _rows:
    if f"attribute.elysiumlib.{_row}" not in _lang:
        fail(f"attribute {_row} has no lang entry attribute.elysiumlib.{_row}; the "
             f"stats screen and every tooltip would show the raw key")
_reg_src = open(os.path.join(_ATTR, "ElysiumAttributes.java"), encoding="utf-8").read()
_ctors = re.findall(r"new (?:ElysiumChance|Ranged)Attribute\(([^;]*?)\)\s*[:;]", strip_comments(_reg_src), re.S)
if len(_ctors) < 2 or any(not c.strip().endswith("ElysiumAttributeSpec.MAX") for c in _ctors):
    fail("ElysiumAttributes does not take its ceiling from ElysiumAttributeSpec.MAX; "
         "a typed-in maximum is a cap the harness never sees")
if "setSyncable(true)" not in _reg_src:
    fail("ElysiumAttributes does not sync its attributes; the client would show every "
         "one at its default whatever the gear says")
_main_src = open(os.path.join(SRC, "ElysiumLib.java"), encoding="utf-8").read()
if "ElysiumAttributes.register(modEventBus)" not in strip_comments(_main_src):
    fail("ElysiumLib never registers ElysiumAttributes; every attribute holder "
         "would throw the first time gear asked for it")
_chance_src = open(os.path.join(_ATTR, "ElysiumChanceAttribute.java"), encoding="utf-8").read()
if "ElysiumAttributeSpec.percentText(" not in _chance_src or "fromOdds" in _chance_src \
        or "100.0" in _chance_src:
    fail("ElysiumChanceAttribute computes its own percentage instead of forwarding to "
         "ElysiumAttributeSpec.percentText, which is the one the harness checks")
print(f"attributes         : {len(_rows)} registered, named and synced; chances display through the tested rule")

# ---------------------------------------------------------------------------
# The combat model (SPEC_CLONE P1)
# ---------------------------------------------------------------------------
# CombatHarness executes the arithmetic: pierce, shred, negative armour, the
# repeating crit, the auxiliary hits, overheal, draw speed and cooldowns. What
# it cannot see is whether anything in the game reads them, which is what this
# section is: every attribute the table registers has to be read by some rule,
# every effect and potion has to have a name, and every brewing mix has to name
# something that exists.
_COMBAT = os.path.join(SRC, "combat")
_EVENT = os.path.join(SRC, "event")
_game_src = ""
for _root, _dirs, _files in os.walk(SRC):
    for _f in _files:
        if _f.endswith(".java"):
            _game_src += strip_comments(open(os.path.join(_root, _f), encoding="utf-8").read())
# mining_speed and ghost_health are MAPPED_ELSEWHERE by name in the spec; every
# other row has to be read by a rule, or it is a number nothing acts on.
_mapped = re.findall(r'MAPPED_ELSEWHERE = List\.of\(([^)]*)\)', _spec_src)
_mapped = re.findall(r'"([a-z_]+)"', _mapped[0]) if _mapped else []
for _row in _rows:
    if _row in _mapped:
        continue
    if f'"{_row}"' not in _game_src.replace('ElysiumAttributeSpec', ''):
        fail(f"attribute {_row} is registered but no rule reads it; it would be a "
             f"tooltip line that changes nothing")
_rules_src = strip_comments(open(os.path.join(_COMBAT, "ElysiumCombatRules.java"), encoding="utf-8").read())
for _imp in re.findall(r"\bimport\s+(?:static\s+)?([\w.]+)\s*;", _rules_src):
    if not _imp.startswith("java.") and not _imp.startswith("com.elysium.lib.stats"):
        fail(f"ElysiumCombatRules imports {_imp}; it is executed without a game")
_armour_src = strip_comments(open(os.path.join(_EVENT, "ElysiumArmourHandler.java"), encoding="utf-8").read())
_armour_flat = re.sub(r"\s+", " ", _armour_src)
if not re.search(r'piercedArmour\(armour, shred\(attacker, "armor_shred"\), pierce\(attacker, "armor_pierce"\), toughness\)',
                 _armour_flat):
    fail("armour is not reduced by the attacker's armor_shred then armor_pierce, blunted by toughness")
if not re.search(r'piercedProtection\(points, shred\(attacker, "prot_shred"\), pierce\(attacker, "prot_pierce"\)\)',
                 _armour_flat):
    fail("Protection points are not reduced by the attacker's prot_shred then prot_pierce")
if "negativeArmourFactor" not in _armour_src:
    fail("armour driven below zero no longer makes the blow bigger (AA-2's Negative Armor Factor)")
if "vanillaArmourShare" not in _armour_src:
    fail("the armour handler gives every mob the player's uncapped curve; mobs stay as vanilla made them")
_combat_handler = strip_comments(open(os.path.join(_EVENT, "ElysiumCombatHandler.java"), encoding="utf-8").read())
if "ElysiumCombatRules.critMultiplier" not in _combat_handler:
    fail("crits no longer go through the tested rule; a second copy of the arithmetic would drift")
if "ElysiumShares.odds(ElysiumStats.critChance" not in _combat_handler.replace("com.elysium.lib.stats.", ""):
    fail("Accuracy's crit chance is rolled separately from the crit attribute; one hit would roll twice")
if "overhealAbsorption" not in _combat_handler:
    fail("overheal never turns a hit into absorption")
# Effects and potions: registered, named, and on the bus.
_effects_src = strip_comments(open(os.path.join(_COMBAT, "ElysiumEffects.java"), encoding="utf-8").read())
_EFFECT_NAMES = re.findall(r'EFFECTS\.register\("([a-z_]+)"', _effects_src)
if sorted(_EFFECT_NAMES) != sorted(["sundering", "bleeding", "grievous", "vitality", "knowledge",
                                    "detonation", "flying",
                                    "salve", "stunned", "tipsy", "exhaustion", "renewal",
                                    "foresight", "overload"]):
    fail(f"the effects are not Apothic Attributes' seven and Valoria's seven: {_EFFECT_NAMES}")
for _name in _EFFECT_NAMES:
    if f"effect.elysiumlib.{_name}" not in _lang:
        fail(f"effect {_name} has no name; its icon would show a raw key")
for _hook, _where in (("SUNDERING", "ElysiumAttributeCombat.java"), ("GRIEVOUS", "ElysiumAttributeCombat.java")):
    if f"ElysiumEffects.{_hook}" not in strip_comments(
            open(os.path.join(_EVENT, _where), encoding="utf-8").read()):
        fail(f"{_hook.lower()} is registered but nothing reads it")
_potions_src = strip_comments(open(os.path.join(_COMBAT, "ElysiumPotions.java"), encoding="utf-8").read())
_POTIONS = re.findall(r'potion\("([a-z_]+)"', _potions_src)
for _name in _POTIONS:
    for _kind in ("potion", "splash_potion", "lingering_potion", "tipped_arrow"):
        if f"item.minecraft.{_kind}.effect.{_name}" not in _lang:
            fail(f"potion {_name} has no {_kind} name")
_MIXES = re.findall(r'"([a-z_]+)"', re.search(r"MIXES = List\.of\((.*?)\);", _potions_src, re.S).group(1))
_mix_dir = os.path.join(RES, "data/elysiumlib/brewing_mixes")
_on_disk = sorted(f[:-5] for f in os.listdir(_mix_dir) if f.endswith(".json"))
if sorted(_MIXES) != _on_disk:
    fail(f"the brewing mixes listed in code and the files on disk disagree: "
         f"{sorted(set(_MIXES) ^ set(_on_disk))}")
for _name in _on_disk:
    _mix = json.load(open(os.path.join(_mix_dir, _name + ".json"), encoding="utf-8"))
    for _key in ("from", "to"):
        _id = _mix[_key]
        if _id.startswith("elysiumlib:") and _id.split(":", 1)[1] not in _POTIONS:
            fail(f"brewing mix {_name} names {_id}, which this mod does not register")
        if not _id.startswith(("elysiumlib:", "minecraft:")):
            fail(f"brewing mix {_name} names {_id}, from a mod this one does not depend on")
    if not _mix["ingredient"].startswith("minecraft:"):
        fail(f"brewing mix {_name} is brewed with {_mix['ingredient']}, which is not a vanilla item")
if "ElysiumEffects.register(modEventBus)" not in strip_comments(_main_src) \
        or "ElysiumPotions.register(modEventBus)" not in strip_comments(_main_src):
    fail("the effects or potions registry is not on the mod bus; brewing would do nothing")
print(f"combat model       : {len(_rows) - len(_mapped)} attributes read by a rule, "
      f"{len(_EFFECT_NAMES)} effects, {len(_POTIONS)} potions, {len(_MIXES)} brewing mixes")

# The damage types those attributes deal (P0.2). Each key in the class has its
# JSON and both death messages; each tag entry names a type that exists; and
# every tag is additive, because the merged jar unions tags and a replace:true
# here would wipe vanilla's bypasses_armor list for everyone.
_dt_src = strip_comments(open(os.path.join(_ATTR, "ElysiumDamageTypes.java"), encoding="utf-8").read())
_dt_keys = re.findall(r'key\("([a-z_]+)"\)', _dt_src)
_dt_dir = os.path.join(RES, "data/elysiumlib/damage_type")
_dt_json = sorted(f[:-5] for f in os.listdir(_dt_dir) if f.endswith(".json")) if os.path.isdir(_dt_dir) else []
if sorted(_dt_keys) != _dt_json:
    fail(f"ElysiumDamageTypes names {sorted(_dt_keys)} but damage_type/ holds {_dt_json}; "
         f"a key without its JSON throws when the first source is built")
for _n in _dt_json:
    _d = json.load(open(os.path.join(_dt_dir, _n + ".json"), encoding="utf-8"))
    _msg = _d.get("message_id", "")
    for _k in (f"death.attack.{_msg}", f"death.attack.{_msg}.player"):
        if _k not in _lang:
            fail(f"damage type {_n} dies with the raw key {_k}")
_dt_tags = 0
for _ns, _tag in (("minecraft", "bypasses_armor"), ("neoforge", "is_magic"),
                  ("neoforge", "is_physical"), ("elysiumlib", "is_non_physical")):
    _tp = os.path.join(RES, f"data/{_ns}/tags/damage_type/{_tag}.json")
    if not os.path.isfile(_tp):
        fail(f"damage type tag {_ns}:{_tag} is missing")
        continue
    _td = json.load(open(_tp, encoding="utf-8"))
    if _td.get("replace", False):
        fail(f"{_ns}:{_tag} replaces the tag instead of adding to it; every other mod's "
             f"entries in it would be dropped")
    for _v in _td.get("values", []):
        _dt_tags += 1
        if _v.startswith("elysiumlib:") and _v.split(":", 1)[1] not in _dt_json:
            fail(f"{_ns}:{_tag} lists {_v}, which is not a damage type here")
print(f"damage types       : {len(_dt_json)} types with death messages, {_dt_tags} additive tag entries")

# The stack-modifier API (P0.3). The harness runs the table; what it cannot see
# is that the event handler goes through the owning check and survives a
# contributor that throws.
_sm = strip_comments(open(os.path.join(_ATTR, "ElysiumStackModifiers.java"), encoding="utf-8").read())
if "ElysiumContributorTable.owns(" not in _sm:
    fail("ElysiumStackModifiers adds modifiers without checking the contributor owns "
         "their namespace; one module could then overwrite another's by id")
if not re.search(r"catch \(RuntimeException", _sm):
    fail("ElysiumStackModifiers does not catch a failing contributor; one bad affix "
         "would break every item's attributes and tooltip")
if "@EventBusSubscriber" not in _sm:
    fail("ElysiumStackModifiers is not subscribed; contributors would never run")
print("stack modifiers    : contributors namespaced, isolated and subscribed")

# The hazard-source hook (the space module's atmospheres come through it). The
# harness runs unit(); what it cannot see is that intensity() consults the
# sources at all, takes the worst rather than the sum, and survives one that
# throws.
_hf = strip_comments(open(os.path.join(SRC, "hazard/ElysiumHazardField.java"), encoding="utf-8").read())
_body = re.search(r"public static float intensity\(Level level, BlockPos pos, ElysiumHazard hazard\) \{(.*?)\n    \}", _hf, re.S)
if not _body or "fromSources(" not in _body.group(1) or "Math.max(" not in _body.group(1):
    fail("ElysiumHazardField.intensity does not take the worst of the built-in rules and the "
         "registered sources; a planet's atmosphere would never reach the hazard tick")
_fs = re.search(r"private static float fromSources\(.*?\n    \}", _hf, re.S)
if not _fs or "catch (RuntimeException" not in _fs.group(0) or "unit(" not in _fs.group(0):
    fail("ElysiumHazardField.fromSources does not isolate a failing source and read it through "
         "unit(); one broken planet would stop every hazard, or push intensity past 1")
elif re.search(r"worst\s*\+=|\+\s*unit\(", _fs.group(0)):
    fail("ElysiumHazardField.fromSources adds sources together; two reasons a place is hot "
         "do not make it twice as hot")
print("hazard sources     : consulted, worst-of, clamped and isolated")

EXECUTED_PURE = {
    "character": ["ElysiumLevelling.java"],
    "research": ["ElysiumResearchField.java", "ElysiumResearchRank.java",
                 "ElysiumDeed.java", "ElysiumUnlock.java"],
    "stress": ["ElysiumStressor.java", "ElysiumStressBand.java",
               "ElysiumStress.java"],
    "telegraph": ["ElysiumTelegraph.java"],
    "craft": ["ElysiumQuality.java"],
    # The gear-type tree: the one vocabulary traits, affixes, gems and runes
    # all name kinds in. It is pure so that the armory's property engine, which
    # is pure itself, can read its rows from here rather than keeping a second
    # copy of the tree that could drift.
    "item": ["ElysiumGearTree.java", "ElysiumPurity.java"],
    # The affix arithmetic: weights, rolls and levels. Pure so a harness can
    # draw sixteen hundred times from a pool and count what came up, which is
    # the only way to see a weighting that is quietly wrong.
    "affix": ["ElysiumAffixRules.java"],
    # The vocabulary every attachment states its effects in.
    "effect": ["ElysiumEffectKinds.java"],
    # The weather schedule: when a storm happens and how hard. Pure because it
    # is arithmetic on the day rather than a stored roll, which is also what
    # lets a client draw a storm nobody told it about.
    "weather": ["ElysiumWeatherRules.java"],
    # The world tier: where a tier begins, what a world floor does, and the
    # share curve everything percentile it buys goes through. Derived from a
    # player's own progression rather than tracked as a second one.
    "tier": ["ElysiumWorldTier.java", "ElysiumLootRarity.java",
             "ElysiumTierAugments.java"],
    # Who the world promotes, and how rare that is.
    "elite": ["ElysiumEliteRules.java"],
    "contract": ["ElysiumContractKind.java", "ElysiumTerms.java"],
    "practice": ["ElysiumPractice.java"],
    # ElysiumScarring is the exception in this table: it imports one Elysium
    # class, ElysiumTelegraph, on purpose. The set of blows that scar has to be
    # exactly the set that were announced, and reading it off the same constant
    # is what keeps that true. Both files are in this table, so neither can
    # acquire a registry — which is what the rule is actually protecting.
    "scar": ["ElysiumScarring.java"],
    # The works rule. ElysiumSite is not in here: it carries a Codec, which is
    # DataFixerUpper and loads standalone, but it is a record the level data
    # stores and not a table of numbers. The numbers are all in this file.
    "works": ["ElysiumConspicuity.java"],
    # ElysiumWound and ElysiumInjury carry Codecs and are executed too, but
    # the numbers are here.
    "medical": ["ElysiumWoundKind.java", "ElysiumMedicine.java"],
    "item": ["ElysiumRuneTiers.java"],
    # The rule every percentage goes through, and the hazard combination
    # built on it. Both executed without a game.
    "stats": ["ElysiumShares.java", "ElysiumArmourRules.java"],
    "hazard": ["ElysiumResistance.java"],
    # The combat attribute table: kinds, defaults, and chances stored as odds.
    "attribute": ["ElysiumAttributeSpec.java", "ElysiumContributorTable.java"],
    # The combat attributes' arithmetic: pierce and shred, repeating crits,
    # the auxiliary hits, overheal, draw speed and cooldowns.
    "combat": ["ElysiumCombatRules.java"],
}

_pure_count = 0
for _package, _files in sorted(EXECUTED_PURE.items()):
    for _file in _files:
        _path = os.path.join(SRC, _package, _file)
        if not os.path.isfile(_path):
            fail(f"{_package}/{_file} is gone. It held the numbers a harness "
                 f"executes; whatever replaced it is not being executed")
            continue
        _pure_count += 1
        _text = open(_path, encoding="utf-8").read()
        _foreign = [line.strip() for line in _text.split("\n")
                    if line.startswith("import ")
                    and not line.startswith("import java.")
                    # One pure file may lean on another: that is how the
                    # scarring rule reads its threshold off the telegraph's.
                    # Everything in this table is checked, so a chain between
                    # two of them cannot smuggle a registry in.
                    and not line.startswith("import com.elysium.lib.")]
        if _foreign:
            fail(f"{_package}/{_file} imports {_foreign[0]!r}. This is one of "
                 f"the files a harness executes outside a world, and one import "
                 f"of a registry type takes the whole system out of the tests "
                 f"with nothing else complaining")
        # The same mistake from the other direction: a file in this list that
        # has started registering something is no longer loadable on its own,
        # whatever it imports.
        if "DeferredRegister" in _text or "AttachmentType" in _text:
            fail(f"{_package}/{_file} has started registering something. The "
                 f"static initialiser would then need a running game, and "
                 f"reading one constant off it from a harness fails")

# ---------------------------------------------------------------------------
# An ability that happens
# ---------------------------------------------------------------------------
#
# The state this exists to make impossible: thirty abilities priced against
# each other, thirty relics carrying them, every check in this file passing,
# and pressing the button doing nothing at all. A system that is complete and
# inert is worse than one that is missing, because nothing reports it.

ABILITY = os.path.join(SRC, "ability")
SHAPES = read(os.path.join(ABILITY, "ElysiumAbilityShapes.java"))
USE = read(os.path.join(ABILITY, "act/ElysiumAbilityUse.java"))
HOLDER = read(os.path.join(ABILITY, "act/ElysiumAbilityEntities.java"))

if not SHAPES:
    fail("nothing says what any ability does, only what each is worth, so the "
         "whole roster can be priced, carried, registered and inert")
if not USE:
    fail("nothing performs an ability, so every relic in the pack is a "
         "tooltip")

_shapes = re.findall(r'String ([A-Z]+) = "([a-z]+)";', SHAPES)
shape_names = [name for _, name in _shapes]
if len(shape_names) < 4:
    fail(f"only {len(shape_names)} shape(s) could be read out of "
         f"ElysiumAbilityShapes, so every check below it checks nothing")
else:
    # Every shape has a branch, and there is no branch for a shape that is not
    # one. A seventh branch is how this becomes one bespoke case per ability.
    _perform = squashed(body_of(strip_comments(USE),
                                "public static boolean perform("))
    for name in shape_names:
        constant = [c for c, value in _shapes if value == name][0]
        if f"case ElysiumAbilityShapes.{constant} ->" not in _perform:
            fail(f"nothing performs a {name} ability, so every ability of that "
                 f"shape is priced, carried and silent")
    _branches = re.findall(r"case ElysiumAbilityShapes\.([A-Z]+) ->", _perform)
    if len(_branches) != len(shape_names):
        fail(f"there are {len(_branches)} branch(es) for {len(shape_names)} "
             f"shape(s); a branch with no shape is unreachable and a shape "
             f"with no branch does nothing")

    # The thing that keeps this a set rather than thirty special cases: the
    # one place that performs an ability may not know any ability by name.
    _names = re.findall(r'"([a-z_]{4,})"', USE)
    _roster = set(re.findall(r'made\("([a-z_]+)"',
                             read(os.path.join(ABILITY,
                                               "ElysiumAbilityRules.java"))))
    _knows = sorted(set(_names) & _roster)
    if _knows:
        fail(f"the one place that performs an ability names "
             f"{len(_knows)} of them — {', '.join(_knows[:4])} — so it has "
             f"started growing a branch per ability, which is how an ability "
             f"system becomes thirty special cases nobody can say anything "
             f"about as a set")

    # And the magnitudes are derived rather than typed beside the ability.
    for needed, why in (
            ("public static float reachOf(",
             "nothing says how far an ability reaches, so a reach would be "
             "typed at each of the places that perform one"),
            ("public static int lastsOf(",
             "nothing says how long what an ability does lasts"),
            ("public static int countOf(",
             "nothing says how many things an ability puts in the world, so "
             "the line between an ability and a performance problem is "
             "nowhere written down")):
        if needed not in SHAPES:
            fail(why)

    # The shapes that cannot happen without something existing in the world
    # say so, and refuse to be silent about it.
    if not HOLDER:
        fail("the shapes that throw or summon have nothing to throw or "
             "summon and no way of being given one, so two of the six are "
             "branches that quietly do nothing")
    elif "public static java.util.List<String> missing()" not in HOLDER:
        fail("nothing can say which ability shapes still have nothing behind "
             "them, so a module that failed to fill one in would ship a relic "
             "that does nothing and says nothing")

print("an ability happens : 6 shape(s), one branch each in the one place that "
      "performs them, not one ability named there, and every magnitude "
      "derived from what the ability is already worth")


print(f"executed rules     : {_pure_count} file(s) across "
      f"{len(EXECUTED_PURE)} system(s) load without a game")

mc_libs = os.environ.get("ELYSIUM_MC_LIBS", "/tmp/mc")
classpath_file = os.path.join(mc_libs, "cp.txt")
# Each entry is (source file, main class, label). Both are executed the same
# way and both state a rule the source alone cannot prove.
HARNESSES = [
    ("AbilityHarness.java", "com.elysium.lib.ability.AbilityHarness", "abilities"),
    ("WeatherHarness.java", "com.elysium.lib.weather.WeatherHarness", "weather"),
    ("GearHarness.java", "com.elysium.lib.item.GearHarness", "gear rule"),
    ("CodexScreenHarness.java", "com.elysium.lib.screen.CodexScreenHarness",
     "codex screen"),
    ("HazardHarness.java", "com.elysium.lib.hazard.HazardHarness", "hazards"),
    ("ClaimHarness.java", "com.elysium.lib.claim.ClaimHarness", "claims"),
    ("DiscoveryHarness.java", "com.elysium.lib.discovery.DiscoveryHarness",
     "discovery"),
    ("SpawnRuleHarness.java", "com.elysium.lib.event.SpawnRuleHarness",
     "spawn rule"),
    ("StatHarness.java", "com.elysium.lib.stats.StatHarness", "stat scaling"),
    ("HudHarness.java", "com.elysium.lib.client.HudHarness", "health bar"),
    ("RoomHarness.java", "com.elysium.lib.room.RoomHarness", "rooms"),
    ("ResearchHarness.java", "com.elysium.lib.research.ResearchHarness",
     "research"),
    ("StressHarness.java", "com.elysium.lib.stress.StressHarness", "stress"),
    ("TelegraphHarness.java", "com.elysium.lib.telegraph.TelegraphHarness",
     "telegraph"),
    ("CraftHarness.java", "com.elysium.lib.craft.CraftHarness", "craft"),
    ("ContractHarness.java", "com.elysium.lib.contract.ContractHarness",
     "contracts"),
    ("PracticeHarness.java", "com.elysium.lib.practice.PracticeHarness",
     "practice"),
    ("ScarHarness.java", "com.elysium.lib.scar.ScarHarness", "scarring"),
    ("WorksHarness.java", "com.elysium.lib.works.WorksHarness", "works"),
    ("MedicalHarness.java", "com.elysium.lib.medical.MedicalHarness", "medical"),
    ("RuneTierHarness.java", "com.elysium.lib.item.RuneTierHarness", "rune tiers"),
    ("AttributeHarness.java", "com.elysium.lib.attribute.AttributeHarness",
     "attributes"),
    ("CombatHarness.java", "com.elysium.lib.combat.CombatHarness", "combat model"),
    ("GearTreeHarness.java", "com.elysium.lib.item.GearTreeHarness", "gear tree"),
    ("AffixHarness.java", "com.elysium.lib.affix.AffixHarness", "affixes"),
    ("PurityHarness.java", "com.elysium.lib.item.PurityHarness", "purities"),
    ("EliteHarness.java", "com.elysium.lib.elite.EliteHarness", "elites"),
    ("WorldTierHarness.java", "com.elysium.lib.tier.WorldTierHarness", "world tiers"),
    ("LootRarityHarness.java", "com.elysium.lib.tier.LootRarityHarness", "loot rarity"),
    ("TierAugmentHarness.java", "com.elysium.lib.tier.TierAugmentHarness", "tier augments"),
    ("NullHarness.java", "com.elysium.lib.nihil.NullHarness", "the Null field"),
    ("RunHarness.java", "com.elysium.lib.run.RunHarness", "runs"),
    ("SoundHarness.java", "SoundHarness", "sound table"),
]

missing = [name for name, _, _ in HARNESSES
           if not os.path.isfile(os.path.join(WORK, "tools", name))]
if missing:
    for name in missing:
        fail(f"tools/{name} is missing — the rule it states is no longer "
             f"executed anywhere, only read")
elif not os.path.isfile(classpath_file):
    print(f"harnesses          : not run, no Minecraft classpath at "
          f"{classpath_file} (set ELYSIUM_MC_LIBS)")
else:
    import subprocess
    import tempfile

    classpath = open(classpath_file, encoding="utf-8").read().strip()
    annotations = os.environ.get(
        "ELYSIUM_ANNOTATIONS", "/opt/gradle-8.14.3/lib/annotations-24.0.1.jar")
    if os.path.isfile(annotations):
        classpath += os.pathsep + annotations

    with tempfile.TemporaryDirectory() as scratch:
        library = os.path.join(scratch, "lib")
        tools = os.path.join(scratch, "tools")

        def run(step, label):
            # A timeout, because a harness executes rules and a rule can be
            # broken into a loop that never ends — which is not a failing
            # check, it is a validator that never returns. Generous enough that
            # no honest harness has ever come close.
            try:
                result = subprocess.run(step, capture_output=True, text=True, timeout=300)
            except subprocess.TimeoutExpired:
                fail(f"the {label} harness did not finish in 300s; a rule it executes has "
                     f"been broken into something that does not terminate")
                return None
            text = "\n".join(
                line for line in (result.stdout + result.stderr).split("\n")
                if line and "JAVA_TOOL_OPTIONS" not in line
                and not line.startswith(("Note:", "SLF4J")))
            if result.returncode != 0:
                fail(f"the {label} harness failed:\n      "
                     + text.replace("\n", "\n      "))
                return None
            return text

        # The sources go through an argfile: the library names more files
        # than one command line holds everywhere, and javac reads @files on
        # every platform. Forward slashes, because a backslash in an argfile
        # is an escape.
        source_list = os.path.join(scratch, "sources.txt")
        with open(source_list, "w", encoding="utf-8") as handle:
            for p in java_files:
                handle.write('"' + str(p).replace("\\", "/") + '"\n')

        # The module once, then each harness against it.
        if run(["javac", "-nowarn", "-proc:none", "-cp", classpath,
                "-d", library, "@" + source_list],
               "library") is not None:
            for source, main, label in HARNESSES:
                path = os.path.join(WORK, "tools", source)
                if run(["javac", "-nowarn", "-proc:none",
                        "-cp", classpath + os.pathsep + library,
                        "-d", tools, path], label) is None:
                    continue
                summary = run(["java", "-cp",
                               os.pathsep.join([classpath, library, tools]), main],
                              label)
                if summary is not None:
                    print(f"{label:<19}: " + summary.split("\n")[0])

# ---------------------------------------------------------------------------
# The add-on API (SPEC_CLONE P12.20)
# ---------------------------------------------------------------------------
#
# Three events, and the rule that made it three: an event exists where an
# add-on would otherwise have to copy a whole system to change one answer.
# Everything else is data, and a file needs no event.
#
# What is checked is that each one is fired from the system it is about, that
# cancelling it does something, and that no system posts to the bus on its own
# — an event that a system fires for itself is an event that system can forget.

EVENTS = read("api/ElysiumEvents.java") if os.path.isfile(
    os.path.join(SRC, "api/ElysiumEvents.java")) else ""
if not EVENTS:
    fail("there is no add-on API: api/ElysiumEvents.java is missing, and every mod "
         "built on this library has to copy a system to change one answer")
else:
    events_code = COMMENT.sub(" ", EVENTS)
    for name in ("AffixRoll", "WorldTier", "Promote"):
        if f"class {name} extends Event" not in events_code:
            fail(f"the {name} event is gone. A published API that loses an event is a "
                 f"mod somebody else has to fix")
    for name in ("AffixRoll", "Promote"):
        if f"class {name} extends Event implements ICancellableEvent" not in events_code:
            fail(f"the {name} event can no longer be cancelled, so a listener can watch "
                 f"it happen and nothing else")
    for method in ("fireAffixRoll(", "fireWorldTier(", "firePromote("):
        if f"public static" not in events_code or method not in events_code:
            fail(f"{method[:-1]} is gone, so the system that used it is either not "
                 f"firing or is posting to the bus itself")
    # One file names the bus. A system that posted its own event would be a
    # system that can be changed without anybody noticing the event went away.
    posters = [path for path, text in sources() if "NeoForge.EVENT_BUS.post(" in text]
    if posters != ["api/ElysiumEvents.java"]:
        fail(f"{posters} post to the event bus. Exactly one file does: an event a system "
             f"fires for itself is an event that system can forget to fire")

    fired = {"affix/ElysiumAffixRoll.java": "ElysiumEvents.fireAffixRoll(",
             "tier/ElysiumTiers.java": "ElysiumEvents.fireWorldTier(",
             "elite/ElysiumElites.java": "ElysiumEvents.firePromote("}
    for where, call in fired.items():
        text = COMMENT.sub(" ", read(where))
        if call not in text:
            fail(f"{where} never fires its event, so the API says a listener will be "
                 f"asked and nothing asks")
    roll = COMMENT.sub(" ", read("affix/ElysiumAffixRoll.java"))
    if "asked.isCanceled()" not in roll:
        fail("cancelling an affix roll does nothing: the roller asks and then rolls "
             "anyway")
    if "count = asked.getCount();" not in roll or "worldTier = asked.getWorldTier();" not in roll:
        fail("a listener may change the count or the tier of an affix roll and the "
             "roller ignores both")
    elites = COMMENT.sub(" ", read("elite/ElysiumElites.java"))
    if "firePromote(mob, worldTier" not in elites \
            or "rank = ElysiumEliteRules.Rank.ORDINARY;" not in elites:
        fail("cancelling a promotion does not leave an ordinary creature, so a listener "
             "that says no gets an elite with no augments")
    print("add-on api         : 3 events, each fired by the system it is about, and one "
          "file that posts")

# ---------------------------------------------------------------------------
# The attribute tooltip, and the key that steps out of its way
# (SPEC_CLONE P12.1 and P12.13)
# ---------------------------------------------------------------------------
#
# A diamond sword's vanilla tooltip says "+3 Attack Damage" and the sword does
# seven. This replaces the block with one line per attribute per slot, and the
# detail key puts vanilla's own lines back for anybody comparing two affixes.

TOOLTIPS = "client/ElysiumAttributeTooltips.java"
KEYS = "client/ElysiumKeys.java"
if not os.path.isfile(os.path.join(SRC, TOOLTIPS)):
    fail("nothing reworks the attribute tooltip: a player reads four blue lines and "
         "none of the numbers is the number")
else:
    tooltips = COMMENT.sub(" ", read(TOOLTIPS))
    if "ItemTooltipEvent" not in tooltips:
        fail("the attribute tooltip is rewritten nowhere a tooltip is built")
    if "Dist.CLIENT" not in tooltips:
        fail("the tooltip rework is not client-only, so a server loads a class that "
             "draws things")
    if "removeVanillaLines(lines)" not in tooltips:
        fail("the summary is added and vanilla's lines are left, so every gear tooltip "
             "in the game says everything twice")
    if "show_in_tooltip" in tooltips or "stack.set(" in tooltips \
            or "stack.remove(" in tooltips:
        fail("vanilla's lines are hidden by changing the item rather than the tooltip, "
             "which travels with the item into other mods' screens and other people's "
             "worlds")
    if "instanceof TranslatableContents" not in tooltips:
        fail("vanilla's lines are found by position rather than by what they say, so "
             "another mod's line in the middle of the block is swallowed")
    if "ElysiumKeys.detailWanted()" not in tooltips:
        fail("there is no way to see the individual modifiers again, which is what a "
             "player comparing two affixes actually wants")
    if "ADD_MULTIPLIED_BASE" not in tooltips or "ADD_MULTIPLIED_TOTAL" not in tooltips:
        fail("the summary drops one of the three operations, so a percentage modifier "
             "silently disappears from the tooltip")
if not os.path.isfile(os.path.join(SRC, KEYS)):
    fail("there is no detail key")
else:
    keys = COMMENT.sub(" ", read(KEYS))
    if "event.register(DETAIL)" not in keys:
        fail("the detail key is never registered, so it cannot be rebound and does "
             "nothing")
    if "consumeClick" in keys:
        fail("the detail key consumes its own press. A tooltip is drawn outside the "
             "key-handling tick, and a consumed press is one the rest of the game never "
             "sees")
    for key in ("key.elysiumlib.detail", "key.categories.elysiumlib"):
        if key not in lang:
            fail(f"the controls screen shows {key} and nothing translates it")
    print("attribute tooltips : one line per attribute, vanilla's put back on a key")

# ---------------------------------------------------------------------------
# Quality of life (SPEC_CLONE P12.4)
# ---------------------------------------------------------------------------
#
# Two things: where you died, and that your pickaxe is about to break. Both are
# information the game already has and has never told anybody.

QOL = "event/ElysiumQualityOfLife.java"
if not os.path.isfile(os.path.join(SRC, QOL)):
    fail("neither quality-of-life feature ships")
else:
    qol = COMMENT.sub(" ", read(QOL))
    if "public static void onDeath(LivingDeathEvent" not in qol \
            or "@SubscribeEvent" not in qol:
        fail("nothing tells a player where they died, which is the one thing everybody "
             "wants at exactly the moment the game stops showing it")
    if "dimension().location()" not in qol:
        fail("the death message gives a coordinate and not a dimension, which has sent "
             "people to the wrong world")
    if "< QUIET_FOR" not in qol:
        fail("the durability warning repeats, and a warning that repeats is a warning "
             "that gets muted")
    if "player.tickCount % INTERVAL" not in qol:
        fail("a player's gear is checked every tick rather than on an interval")
    if "displayClientMessage(" not in qol:
        fail("the durability warning is printed to chat rather than the action bar, so "
             "it scrolls away under whatever else is happening")
    if "isDamageableItem()" not in qol:
        fail("the durability check does not ask whether an item can be damaged at all")
    for key in ("elysium.qol.died", "elysium.qol.wearing_out"):
        if key not in lang:
            fail(f"quality of life prints {key} and nothing translates it")
    print("quality of life    : where you died, and that it is about to break")

print()

# ---------------------------------------------------------------------------
# Accessory slots: the seam, and the engine reading through it
# ---------------------------------------------------------------------------
#
# The library must not know which accessory mod is installed, and the effect
# engine must not care. Both halves are load-bearing: the first keeps the
# library buildable on its own, and the second is what makes a worn ring a
# thing the engine asks questions of rather than a special case somebody has
# to remember to add to fifteen handlers.

_wearables = os.path.join(SRC, "trinket/ElysiumWearables.java")
if not os.path.isfile(_wearables):
    fail("there is no register of what wants wearing, so the module that builds "
         "jewellery and the module that owns the slots have no way to meet")
_seam = open(os.path.join(SRC, "trinket/ElysiumTrinkets.java"), encoding="utf-8").read()
# The brace, because "interface StackProvider" is a prefix of every longer
# name and a rule that matched one of those would pass on a rename.
if "interface StackProvider {" not in _seam:
    fail("the seam answers what trinkets are worn and not what stacks are worn, so "
         "anything in an accessory slot that is not one of this mod's own trinkets "
         "is invisible to the engine")
if "wornStacks(" not in _seam:
    fail("nothing can ask what is in an accessory slot")
_effects = open(os.path.join(SRC, "effect/ElysiumEffectEvents.java"), encoding="utf-8").read()
if _effects.count("accessories(") < 3:
    fail("the effect engine looks at accessory slots in fewer places than it looks "
         "at armour, so a worn ring works for some effects and not others")
if "ElysiumTrinkets.wornStacks(" not in _effects:
    fail("the effect engine reaches for accessories some other way than the seam")
for _path, _, _names in os.walk(SRC):
    for _name in _names:
        if not _name.endswith(".java"):
            continue
        _body = open(os.path.join(_path, _name), encoding="utf-8").read()
        if "top.theillusivec4" in _body or "curios.api" in _body:
            fail(f"{_name} names Curios; the library builds without any accessory "
                 f"mod and that is the whole point of the seam")

print("accessory seam     : two questions, no accessory mod named, and the effect "
      "engine reads worn accessories as it reads armour")

# ---------------------------------------------------------------------------
# Weather: a hazard that comes and goes
# ---------------------------------------------------------------------------
#
# The whole design rests on weather being a hazard *source* rather than a
# system of its own. Everything a player already knows about hazards — that
# armour resists them, that a ward answers one, that a sealed room stops them —
# then applies to a storm without a line being written, and "storm shutters"
# turns out to be a wall. A weather system that ticked its own damage would be
# a second pipeline that had to agree with the first, and would not.

_weather = open(os.path.join(SRC, "weather/ElysiumWeather.java"), encoding="utf-8").read()
_weather_rules = open(os.path.join(SRC, "weather/ElysiumWeatherRules.java"),
                      encoding="utf-8").read()
_weather_handler = open(os.path.join(SRC, "event/ElysiumWeatherHandler.java"),
                        encoding="utf-8").read()

if "ElysiumHazardField.registerSource(" not in _weather:
    fail("weather is not a hazard source, so a storm needs its own damage tick, its "
         "own resistance and its own idea of what being indoors means - and the four "
         "of those will not agree with the hazard engine's")
_intensity = body_of(_weather, "private static float intensity(")
if "pos" in _intensity.replace("BlockPos pos", ""):
    fail("the weather's intensity depends on where you are standing, so a storm is "
         "made of chunks rather than being a sky")
if "getDayTime()" not in _weather:
    fail("the weather is not read off the world's own clock, so it has to be stored - "
         "and a stored sky can disagree with itself after a restart")
for _stored in ("SavedData", "static final Map<java.util.UUID", "setDirty("):
    if _stored in _weather:
        fail(f"the weather stores {_stored}; the schedule is arithmetic on the day and "
             f"storing it is how two players end up in different storms")
if "ElysiumWeatherRules.machineShare(" not in _weather:
    fail("what a machine gets out of a storm is decided outside the rules, where no "
         "harness runs it")
_announce = body_of(_weather_handler, "private static void announce(")
if "now.equals(was)" not in _announce:
    fail("the weather is announced every second it blows, which is a message a "
         "player turns off")
_draw = body_of(_weather_handler, "private static void draw(")
if "canSeeSky(" not in _draw:
    fail("a storm is drawn underground, where the hazard pipeline has already decided "
         "it does not reach - which tells the player exactly the wrong thing")
for _key in ("elysium.weather.begins", "elysium.weather.clears"):
    if _key not in lang:
        fail(f"{_key} has no translation, so the one line that explains why the "
             f"damage started reads as a raw key")
if "MOST_STORMY" not in _weather_rules:
    fail("there is no ceiling on how stormy a world may be, so a pack can declare a "
         "climate rather than weather")

print(f"weather            : a hazard source read off the day, announced when it "
      f"turns, and drawn only where the sky is")

# ---------------------------------------------------------------------------
# Valoria: effects (docs/SPLITS.md; docs/SPEC_VALORIA.md)
# ---------------------------------------------------------------------------
#
# Valoria's status effects, attributes and enchantments, and its Nihility as
# the Null field. NullHarness executes the gauge's arithmetic and the ward's;
# AttributeHarness executes the mapping; CombatHarness the healing scales and
# the elemental bonus. What none of them can see is the wiring: that the field
# is a hazard the existing protection answers rather than a second pipeline,
# that the gauge is moved from the tick and from the ward, that the effects
# are read where they matter, and that the data the generator writes is what
# is shipped.

_NIHIL = os.path.join(SRC, "nihil")
_null_rules = strip_comments(open(os.path.join(_NIHIL, "ElysiumNullRules.java"), encoding="utf-8").read())
for _imp in re.findall(r"\bimport\s+(?:static\s+)?([\w.]+)\s*;", _null_rules):
    if not _imp.startswith("java."):
        fail(f"ElysiumNullRules imports {_imp}; it is executed without a game")
_null = strip_comments(open(os.path.join(_NIHIL, "ElysiumNull.java"), encoding="utf-8").read())
if 'ElysiumHazard.register(id("null"), ElysiumElements.VOID, 0.0F)' not in _null:
    fail("the Null field is not a hazard on the Void element with no damage of its own, so "
         "either armour does not answer it or it hurts twice — once as a hazard, once as a gauge")
if "ElysiumWards.bind(NULL, WARD)" not in _null:
    fail("the Null ward is not bound to the Null field, so a warded player still accrues")
if "MobEffectCategory.BENEFICIAL" not in body_of(_null, "private NullWard()"):
    fail("the Null ward is not beneficial, so milk and every cure strip it")
if "ElysiumNull.register(modEventBus);" not in strip_comments(_main_src) \
        or "ElysiumNull.bootstrap();" not in strip_comments(_main_src):
    fail("ElysiumLib never registers the Null field's attachment and ward; the gauge would "
         "throw on first read and the ward would not exist")
_null_handler = strip_comments(open(os.path.join(_NIHIL, "ElysiumNullHandler.java"), encoding="utf-8").read())
_tick = body_of(_null_handler, "public static void onPlayerTick(")
for _need, _why in (
        ("ElysiumHazardField.intensity(level, pos, ElysiumNull.NULL)",
         "the gauge does not read the field's intensity, so it accrues everywhere or nowhere"),
        ("!ElysiumWards.isWarded(player, ElysiumNull.NULL)",
         "a warded player still accrues; the ward would be a shield and not a ward"),
        ("ElysiumNullRules.accrual(protection, difficulty) * intensity",
         "accrual is not the tested rule scaled by the field; a second copy would drift"),
        ("ElysiumNull.add(player, -ElysiumNullRules.decay(difficulty))",
         "the gauge never decays away from the field, so one visit is forever"),
        ("if (ElysiumNullRules.collapses(ratio))",
         "the top of the gauge does nothing; the field would hurt forever and never reclaim anybody"),
        ("player.isCreative() || player.isSpectator()",
         "creative players accrue, which is a builder dying in their own field")):
    if _need not in _tick:
        fail(_why)
_prot = body_of(_null_handler, "private static float protection(")
for _need, _why in (
        ("ElysiumProtection.isSealed(player, ElysiumNull.NULL)",
         "a sealed set does not stop the field; the Void armour a player built answers nothing"),
        ("ElysiumProtection.inShelteredClaim(level, pos)",
         "a filed claim does not shelter from the field, so a base in one is not a base"),
        ("return ElysiumResistance.combine(all);",
         "the field's protection is not combined by the library's arithmetic, so it would "
         "disagree with every other hazard's")):
    if _need not in _prot:
        fail(_why)
_ward_hit = body_of(_null_handler, "public static void onIncomingDamage(")
for _need, _why in (
        ("event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)",
         "the ward shields blows that bypass armour, which Valoria's did not"),
        ("ElysiumNullRules.wardBlock(ward.getAmplifier() + 1)",
         "the ward's shield is not the tested share of the level"),
        ("ElysiumNull.add(player, ElysiumNullRules.wardLeak(incoming, reduced));",
         "what the ward turns aside never leaks into the gauge, so a high ward is free")):
    if _need not in _ward_hit:
        fail(_why)
_status = strip_comments(open(os.path.join(_EVENT, "ElysiumStatusHandler.java"), encoding="utf-8").read())
_heal = body_of(_status, "public static void onHeal(")
if "amount *= ElysiumCombatRules.exhaustionScale(exhaustion.getAmplifier() + 1);" not in _heal \
        or "amount *= ElysiumCombatRules.renewalScale(renewal.getAmplifier() + 1);" not in _heal \
        or "event.setAmount(amount);" not in _heal:
    fail("Exhaustion and Renewal do not scale healing through the tested rules")
_thrown = body_of(_status, "public static void onBlowThrown(")
if '"miss_chance"' not in _thrown or "event.setCanceled(true);" not in _thrown:
    fail("miss_chance never cancels a blow; the attribute would be a tooltip line")
if "stunned(attacker)" not in _thrown:
    fail("a stunned attacker's blow still lands")
_landing = body_of(_status, "public static void onBlowLanding(")
if "amount += ElysiumCombatRules.elementalBonus(flat, resisted, all);" not in _landing:
    fail("elemental damage is not added through the tested rule")
if "ElysiumNull.add(player, landed);" not in _landing:
    fail("null_damage that lands never fills the target's gauge, so it is plain damage with a name")
if "attacker instanceof Player && victim instanceof Player" not in _landing:
    fail("elemental bonuses apply between players, which Valoria kept out of duels")
if "ElysiumSummons.damageMultiplier(owner)" not in _landing:
    fail("summon_damage never scales an owned creature's blow")
for _event in ("AttackEntityEvent", "LivingEntityUseItemEvent.Start", "PlayerInteractEvent.RightClickBlock",
               "PlayerInteractEvent.LeftClickBlock", "PlayerInteractEvent.EntityInteract"):
    if f"({_event} event)" not in _status:
        fail(f"Stunned does not cancel {_event}; a stunned player could still do that")
_salve = body_of(_effects_src, "public boolean applyEffectTick(LivingEntity entity, int amplifier) {\n            if (!entity.level().isClientSide() && entity.getHealth()")
if "entity.heal(SALVE_PER_TICK * (1 + amplifier));" not in _effects_src:
    fail("Salve does not heal its rate per level")
if "entity.getHealth() < entity.getMaxHealth()" not in _effects_src:
    fail("Salve heals at full health, which is a heal event every tick for nothing")
# The enchantments are data; the class only holds keys, and both have to agree.
_ench_src = strip_comments(open(os.path.join(SRC, "item/ElysiumEnchantments.java"), encoding="utf-8").read())
_ench_keys = sorted(re.findall(r'key\("([a-z_]+)"\)', _ench_src))
_ench_dir = os.path.join(RES, "data/elysiumlib/enchantment")
_ench_json = sorted(f[:-5] for f in os.listdir(_ench_dir) if f.endswith(".json")) if os.path.isdir(_ench_dir) else []
if _ench_keys != _ench_json or _ench_keys != ["accuracy", "bleeding", "explosive_charge"]:
    fail(f"ElysiumEnchantments names {_ench_keys} but enchantment/ holds {_ench_json}; Valoria's "
         f"three are bleeding, accuracy and explosive_charge")
for _n in _ench_json:
    _e = json.load(open(os.path.join(_ench_dir, _n + ".json"), encoding="utf-8"))
    for _k in (f"enchantment.elysiumlib.{_n}", f"enchantment.elysiumlib.{_n}.desc"):
        if _k not in _lang:
            fail(f"enchantment {_n} shows the raw key {_k}")
    _items = _e.get("supported_items", "")
    if _items.startswith("#elysiumlib:"):
        _tag = os.path.join(RES, "data/elysiumlib/tags/item", _items[len("#elysiumlib:"):] + ".json")
        if not os.path.isfile(_tag):
            fail(f"enchantment {_n} applies to {_items}, a tag that does not exist, so nothing can carry it")
    if _e.get("max_level", 0) < 1:
        fail(f"enchantment {_n} has no levels")
    for _table in ("in_enchanting_table", "non_treasure"):
        _t = json.load(open(os.path.join(RES, f"data/minecraft/tags/enchantment/{_table}.json"), encoding="utf-8"))
        if f"elysiumlib:{_n}" not in _t.get("values", []) or _t.get("replace", False):
            fail(f"enchantment {_n} is not added to minecraft:{_table}, so no table offers it")
_bleed = json.load(open(os.path.join(_ench_dir, "bleeding.json"), encoding="utf-8")) if "bleeding" in _ench_json else {}
_bleed_effects = _bleed.get("effects", {}).get("minecraft:post_attack", [])
if not any(_x.get("effect", {}).get("to_apply") == "elysiumlib:bleeding" for _x in _bleed_effects):
    fail("the bleeding enchantment does not apply the library's own Bleeding on hit")
# The Null field's damage and its words.
for _k in ("elysium.hazard.null", "elysium.hazard.null.cheap", "elysium.hazard.null.engineered",
           "elysium.null.rising", "elysium.null.speaks", "elysium.null.extracted", "elysium.combat.missed"):
    if _k not in _lang:
        fail(f"{_k} is not translated; the line that tells a player what the field is doing to them is a raw key")
for _n in ("null", "null_collapse"):
    if _n not in _dt_json:
        fail(f"damage type {_n} is missing; the field would throw the first time it hurt")
    for _ns, _tag in (("minecraft", "bypasses_armor"), ("elysiumlib", "is_non_physical")):
        _t = json.load(open(os.path.join(RES, f"data/{_ns}/tags/damage_type/{_tag}.json"), encoding="utf-8"))
        if f"elysiumlib:{_n}" not in _t.get("values", []):
            fail(f"elysiumlib:{_n} is not in {_ns}:{_tag}; armour would stop the field, or a physical resistance would")
# Generated data is what is shipped.
_gen = os.path.join(WORK, "tools/gen_valoria_effects.py")
if not os.path.isfile(_gen):
    fail("tools/gen_valoria_effects.py is gone; the enchantments, damage types and words could drift")
else:
    import filecmp as _fc
    import subprocess as _sp
    with tempfile.TemporaryDirectory() as _scratch:
        _run = _sp.run([sys.executable, _gen], capture_output=True, text=True, cwd=WORK,
                       env={**os.environ, "ELYSIUM_LIB_OUT": _scratch, "PYTHONIOENCODING": "utf-8"})
        if _run.returncode != 0:
            fail(f"tools/gen_valoria_effects.py no longer runs: {_run.stderr.strip()[-300:]}")
        else:
            _drift = []
            for _root, _dirs, _files in os.walk(_scratch):
                for _f in _files:
                    _made = os.path.join(_root, _f)
                    _rel = os.path.relpath(_made, _scratch)
                    _shipped = os.path.join(WORK, _rel)
                    if not os.path.isfile(_shipped) or not _fc.cmp(_made, _shipped, shallow=False):
                        _drift.append(_rel.replace(os.sep, "/"))
            if _drift:
                fail(f"the shipped files are not what tools/gen_valoria_effects.py generates: {_drift[:5]}")
print(f"valoria effects    : the Null field as a sixth hazard with a gauge and a ward, "
      f"{len(re.findall(r'\"([a-z_]+)\"', re.search(r'VALORIA_ROWS = List\.of\((.*?)\);', _spec_src, re.S).group(1)))} "
      f"attributes, seven effects and {len(_ench_json)} enchantments, all read by a rule")

# ---------------------------------------------------------------------------
# Sound (docs/POLISH.md P5)
# ---------------------------------------------------------------------------
#
# The pack shipped no sound at all until 28 Sep 2026: no sounds.json, no .ogg,
# every creature and machine silent. Now every clip is synthesised from
# sound/ElysiumSoundRules.java by tools/gen_sounds.py through art/sound.py, and
# ElysiumSounds registers an event per clip from the same table. What is held
# here: the table, the registry, sounds.json, the files and the subtitles are
# one set; every file is a Vorbis clip the game can play, mono at the rate the
# game's own sounds are, no longer than a call and no quieter than a whisper;
# and the shipped files are what the generator makes.
sys.path.insert(0, os.path.join(os.path.dirname(WORK), "tools"))
import samefile as _samefile  # noqa: E402
_sound_rules = COMMENT.sub(" ", read("sound/ElysiumSoundRules.java"))
_sound_reg = COMMENT.sub(" ", read("sound/ElysiumSounds.java"))
_voices_src = COMMENT.sub(" ", read("sound/ElysiumVoices.java"))
_sounds_json_path = os.path.join(RES, "assets", "elysiumlib", "sounds.json")
if not _sound_rules or not _sound_reg or not _voices_src:
    fail("the sound table, its registry or the voices helper is gone, so the pack is silent again")
else:
    _voice_ids = re.findall(r'voice\("(\w+)",', _sound_rules)
    _hum_ids = re.findall(r'new Hum\("(\w+)",', _sound_rules)
    _click_ids = re.findall(r'new Click\("(\w+)",', _sound_rules)
    _calls = re.findall(r'"(\w+)"', re.search(r'CALLS = List\.of\(([^)]*)\)', _sound_rules).group(1))
    _expected = ["voice.%s.%s" % (v, c) for v in _voice_ids for c in _calls] \
        + ["hum.%s" % h for h in _hum_ids] + ["click.%s" % c for c in _click_ids]
    if len(_voice_ids) < 20 or len(_hum_ids) < 6 or not _click_ids:
        fail(f"the sound table has {len(_voice_ids)} voice(s), {len(_hum_ids)} hum(s) and "
             f"{len(_click_ids)} click(s); the rosters and the engines need more than that")
    for needed in ('for (ElysiumSoundRules.Voice voice : ElysiumSoundRules.VOICES)',
                   'for (ElysiumSoundRules.Hum hum : ElysiumSoundRules.HUMS)',
                   'for (ElysiumSoundRules.Click click : ElysiumSoundRules.CLICKS)'):
        if needed not in _sound_reg:
            fail("ElysiumSounds no longer registers from the table, so a voice added to the rules "
                 "is a clip nothing can play")
    if "ElysiumSounds.register(modEventBus)" not in COMMENT.sub(" ", read("ElysiumLib.java")):
        fail("ElysiumSounds is never put on the bus, so not one sound event exists at runtime")
    _sounds_json = json.load(open(_sounds_json_path, encoding="utf-8")) if os.path.isfile(_sounds_json_path) else None
    if _sounds_json is None:
        fail("assets/elysiumlib/sounds.json is missing, so every registered event points at nothing")
    else:
        _listed = sorted(_sounds_json)
        if _listed != sorted(_expected):
            _off = sorted(set(_listed) ^ set(_expected))
            fail(f"sounds.json and the sound table disagree on {len(_off)} event(s) ({_off[:4]}); "
                 f"an event without a file is silent, a file without an event is dead weight")
        _lang_now = json.load(open(os.path.join(RES, "assets", "elysiumlib", "lang/en_us.json"), encoding="utf-8"))
        _sound_dir = os.path.join(RES, "assets", "elysiumlib", "sounds")
        try:
            import soundfile as _sf
        except ImportError:
            _sf = None
            fail("the soundfile module is missing, so no clip can be checked (pip install soundfile)")
        _checked = 0
        for _name, _entry in sorted(_sounds_json.items()):
            _sub = _entry.get("subtitle")
            if not _sub or _sub not in _lang_now:
                fail(f"{_name} has no translated subtitle, so a player reading the screen sees a raw key")
            for _one in _entry.get("sounds", []):
                _ref = _one["name"] if isinstance(_one, dict) else _one
                _rel = _ref.split(":", 1)[1] + ".ogg"
                _path = os.path.join(_sound_dir, _rel)
                if not os.path.isfile(_path):
                    fail(f"{_name} names {_ref}, and there is no such file under sounds/")
                    continue
                if _sf is not None:
                    try:
                        _info = _sf.info(_path)
                    except Exception as trouble:  # noqa: BLE001
                        fail(f"{_rel} is not a clip the game can decode ({trouble})")
                        continue
                    if _info.format != "OGG" or _info.subtype != "VORBIS":
                        fail(f"{_rel} is {_info.format}/{_info.subtype}; the game plays only Ogg Vorbis")
                    if _info.channels != 1 or _info.samplerate != 22050:
                        fail(f"{_rel} is {_info.channels} channel(s) at {_info.samplerate} Hz; every clip is mono at 22050")
                    if not (0.05 <= _info.duration <= 4.0):
                        fail(f"{_rel} runs {_info.duration:.2f} s; a call is between a twentieth of a second and four")
                    _data, _ = _sf.read(_path, dtype="float32")
                    _peak = float(abs(_data).max()) if _data.size else 0.0
                    if _peak < 0.3:
                        fail(f"{_rel} peaks at {_peak:.2f}; a clip that quiet is a clip nobody hears")
                    _checked += 1
        # The shipped clips are what the generator makes.
        _gen = os.path.join(WORK, "tools/gen_sounds.py")
        if not os.path.isfile(_gen):
            fail("tools/gen_sounds.py is gone; the clips could drift from the table")
        else:
            with tempfile.TemporaryDirectory() as _scratch:
                _run = subprocess.run([sys.executable, _gen], capture_output=True, text=True, cwd=WORK,
                                      env={**os.environ, "ELYSIUM_LIB_OUT": _scratch, "PYTHONIOENCODING": "utf-8"})
                if _run.returncode != 0:
                    fail(f"tools/gen_sounds.py no longer runs: {_run.stderr.strip()[-300:]}")
                else:
                    _drift = []
                    for _root, _dirs, _files in os.walk(_scratch):
                        for _f in _files:
                            _made = os.path.join(_root, _f)
                            _rel = os.path.relpath(_made, _scratch)
                            if not _samefile.same(_made, os.path.join(WORK, _rel)):
                                _drift.append(_rel.replace(os.sep, "/"))
                    if _drift:
                        fail(f"{len(_drift)} shipped sound file(s) are not what tools/gen_sounds.py generates "
                             f"({_drift[:3]}): edited by hand, or rendered from another table")
        for needed, why in (("level.playSound(null, pos, event, SoundSource.BLOCKS, ElysiumSoundRules.HUM_VOLUME, 1.0F)",
                             "hum"), ("% ElysiumSoundRules.HUM_EVERY", "hum on the table's cadence")):
            if needed not in _voices_src:
                fail(f"ElysiumVoices no longer plays a machine's {why}, so every engine is silent")
        print(f"sound              : {len(_voice_ids)} voices x {len(_calls)}, {len(_hum_ids)} hums, "
              f"{len(_click_ids)} clicks: {_checked} Vorbis clips on disk, each mono at 22050 Hz, listed, "
              f"subtitled and rebuilt identical")

# ---------------------------------------------------------------------------
# Registrations that ride on another class's DeferredRegister
# ---------------------------------------------------------------------------
# A static field that calls Owner.REGISTER.register("...") from a class other
# than Owner is registered only when that class is loaded. If nothing loads it
# before RegisterEvent, the first use — a mob spawning, say — runs the static
# initialiser then, and DeferredRegister refuses with "Cannot register new
# entries ... after RegisterEvent has been fired", which took the server tick
# down. ElysiumElites.RANK died exactly this way. So every such class is named
# in the library's constructor (a bootstrap() call is enough) or is a bus
# subscriber, which the scan loads.

_lib_ctor = strip_comments(_main_src)
_riders = 0
for _dir, _, _files in os.walk(SRC):
    for _file in sorted(_files):
        if not _file.endswith(".java"):
            continue
        _text = strip_comments(open(os.path.join(_dir, _file), encoding="utf-8").read())
        _here = _file[:-5]
        for _owner, _reg in re.findall(r'\b([A-Z]\w+)\.([A-Z_]+)\.register\(\s*"', _text):
            if _owner == _here:
                continue
            _riders += 1
            if (_here + ".") not in _lib_ctor and "@EventBusSubscriber" not in _text:
                fail(f"{_file} registers onto {_owner}.{_reg} from its static initialiser and "
                     f"nothing loads the class before RegisterEvent, so the entry is refused "
                     f"the first time something touches it; call its bootstrap() from "
                     f"ElysiumLib's constructor")
print(f"riding registrations: {_riders} entry(ies) registered from another class, each "
      f"loaded before the bus fires")

# Runs: one helper for every pipe, cable, wire, conduit, bus and channel.
_runs_src = read("run/Runs.java")
for _stmt, _why in (
        ("return RunRules.mayJoin(dyeAt(level, pos), dyeAt(level, next)) ? RunLink.RUN : RunLink.NONE;",
         "two runs join by something other than the dye rule"),
        ("return device.test(level, next, side.getOpposite()) ? RunLink.DEVICE : RunLink.NONE;",
         "a device is decided by something other than the block's own test"),
        ("if (!player.getAbilities().instabuild) {", "dyeing a run is free in survival"),
        ("? RunRules.plateTint(dye, ownColour(state.getBlock()))", "the plate is not tinted the run's own colour when undyed")):
    if _stmt not in _runs_src:
        fail(f"Runs.java: {_why}")
_dye_body = _runs_src.split("public static ItemInteractionResult dye(")[1].split("\n    }\n")[0]     if "public static ItemInteractionResult dye(" in _runs_src else ""
if "relinkAround(level, pos, device);" not in _dye_body:
    fail("Runs.java: a dyed run does not tell its neighbours, so a colour change leaves stale arms")
if 'registerComponentType("run_dye"' not in read("item/ElysiumComponents.java"):
    fail("a dyed run item has no component to carry the dye, so a dyed pipe in a chest is a plain pipe")
if "event.register(Runs::tint, block);" not in read("run/RunColors.java") \
        or "event.register(Runs::itemTint, block.asItem());" not in read("run/RunColors.java"):
    fail("the run tints are not registered for every declared run, so dye would show nowhere")
print("runs               : the join rule, the plate, the dye and the tints, executed and shared")

# The boxes. Every chest menu type the library declares opens under the kit's
# screen, and there is one per row count the game's generic menus have.
_chests = squashed(read("menu/ElysiumChests.java"))
_chest_screens = squashed(read("client/ElysiumChestScreens.java"))
if "public static final int MOST_ROWS = 6;" not in _chests \
        or "for (int rows = 1; rows <= MOST_ROWS; rows++) {" not in _chests:
    fail("the library does not declare a chest menu type for every row count a box can "
         "have, so some box falls back to the game's grey screen")
if ("for (DeferredHolder<MenuType<?>, MenuType<ChestMenu>> type : ElysiumChests.TYPES) { "
        "event.<ChestMenu, ElysiumChestScreen<ChestMenu>>register(type.get(), "
        "(menu, inventory, title) -> new ElysiumChestScreen<>(menu, inventory, title)); }") not in _chest_screens:
    fail("the kit's box screen is not registered for every chest menu type the library "
         "declares, so a box of some row count opens with no screen at all")
print("the boxes          : a chest menu type per row count, one to six, each under the kit's screen")
if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all library checks passed")
