#!/usr/bin/env python3
"""
Validate Elysium Bestiary against its sources.

Run from anywhere:   python3 elysium-bestiary/validate.py

Seven jars in this pack exist to put more things in the dark, and between them
they bring three problems nobody files as bugs, because all three look like the
game working: a mob that is harder and worth less, a mob that is far and away
the best thing to farm, and a bestiary the base game outfarms. All three are
ratios over the whole set, and nobody reads a list of mobs that way.

So the rewards are not written down here at all. They are derived from the
threat each beast's own stats come to, and this file proves that the derivation
is monotonic, that the generator's second copy of it agrees with the first, that
what the loot tables actually hand out is the number the bestiary says, and that
every beast is exactly as big on screen as the box you are swinging at.

  1. INDEPENDENCE. The library, the game, and nothing else.
  2. PURITY, and the harness run rather than read.
  3. THE TWO DERIVATIONS, compared line by line.
  4. THE SHEET, and the six corners nothing may spill out of.
  5. AS BIG AS IT LOOKS. The model measured against the hitbox.
  6. WHAT IT LEAVES, counted out of the written loot tables.
  7. WHAT YOU MEET, counted out of the written spawn rules.
  8. THE WORDS, and the generated tree rebuilt and compared.
  9. THE BEASTS IN THE WORLD, and the seam.
"""
import importlib.util
import json
import os
import re
import subprocess
import sys
import tempfile

WORK = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(WORK)
RES = os.path.join(WORK, "src/main/resources")
SRC = os.path.join(WORK, "src/main/java/com/elysium/bestiary")
PURE = os.path.join(SRC, "rules")
# The library's ability roster and shapes. The summons are the only thing in
# this module's rules that reaches outside it, both of those classes are pure
# JDK like the rules are, and elysium-lib sits below everything -- so they are
# staged with them and the harness compiles against them the way the game will.
# The same arrangement elysium-arcana uses, and for the same reason: a harness
# that cannot see what the summons are measured against cannot check the one
# property they exist for.
BELOW = [os.path.join(os.path.dirname(WORK),
                      "elysium-lib/src/main/java/com/elysium/lib/ability")]
HARNESS = os.path.join(WORK, "tools/BeastHarness.java")
HARNESS_MAIN = "com.elysium.bestiary.rules.BeastHarness"
GENERATOR = os.path.join(WORK, "tools/gen_bestiary.py")

#: How long the arithmetic is allowed to take. Three million checks, a hundred
#: thousand derived bestiaries and two million drawn spawns run in well under
#: this; anything past it is a loop.
HARNESS_SECONDS = 180
NS = "elysiumbestiary"

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
    """Every run of whitespace as one space, so a rule can name a whole statement.

    Naming the whole statement is the point: a rule that looks for a token
    which survives the very edit it was meant to catch is a rule that has never
    failed, and this project has been bitten by that fifteen times.
    
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
    print("This is not the elysium-bestiary tree.")
    sys.exit(1)

print("=" * 70)
print("elysium-bestiary — nine things that hunt you, and the proof that none of them "
      "is the only one worth killing")
print("=" * 70)

# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------

FOREIGN = re.compile(
    r"(?:mowziesmobs|alexsmobs|cataclysm|mutantmonsters|born_in_chaos|"
    r"creeperoverhaul|goblintraders)", re.I)
UPWARD = re.compile(r"com\.elysium\.(wilds|colonies)")

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
    if UPWARD.search(src):
        fail(f"{where} reaches up into a later module; the dependencies run one way")
    for used in set(re.findall(r"com\.elysium\.(\w+)", src)):
        if used not in ("bestiary", "lib"):
            fail(f"{where} names com.elysium.{used}; every beast here is measured "
                 f"against the game's own mobs and drops the game's own items, so the "
                 f"module works in a pack with nothing else installed")

toml = read(RES, "META-INF/neoforge.mods.toml")
declared = set(re.findall(r'modId="([^"]+)"', toml))
allowed = {"${mod_id}", "neoforge", "minecraft", "elysiumlib"}
for mod in declared - allowed:
    fail(f"the module declares a dependency on {mod}; nothing it adds needs anything "
         f"beyond the game and the library")
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
                 f"so a harness can derive a hundred thousand bestiaries without a game")
    for used in re.findall(r"import\s+(com\.elysium\.[\w.]+);", without_package):
        # The library is below everything and its ability classes are pure JDK
        # too, so a table here may be built on one there -- which is what stops
        # the summons and the abilities that call them up growing two sets of
        # cooldowns that drift apart. Anything else is a table reaching
        # sideways, and elysium-arcana draws the same line for the same reason.
        if used.startswith("com.elysium.lib."):
            continue
        fail(f"rules/{name} imports {used}; a table here is the JDK and the "
             f"library, and nothing else")

# ---------------------------------------------------------------------------
# What exists at all
# ---------------------------------------------------------------------------
#
# Two rosters name items by hand — what a beast leaves and what an animal
# leaves and eats — and a name with a letter too many in it produces a loot
# table that averages the right number of nothing. So both are looked up here,
# once, in the game's own language file (which names every item and block it
# ships) and in this pack's.

GAME_JAR = os.path.join(os.environ.get("ELYSIUM_MC_LIBS", "/tmp/mc"), "vanilla.jar")


def the_game_makes():
    if not os.path.isfile(GAME_JAR):
        return set()
    import zipfile
    with zipfile.ZipFile(GAME_JAR) as jar:
        named = json.loads(jar.read("assets/minecraft/lang/en_us.json"))
    return {"minecraft:" + key.split(".", 2)[2] for key in named
            if key.startswith(("item.minecraft.", "block.minecraft."))}


def the_pack_makes():
    found = set()
    for where, _, names in os.walk(ROOT):
        if not where.replace(os.sep, "/").endswith("/lang"):
            continue
        for name in names:
            if name != "en_us.json":
                continue
            for key in json.loads(read(where, name)):
                parts = key.split(".", 2)
                if len(parts) == 3 and parts[0] in ("item", "block"):
                    found.add(parts[1] + ":" + parts[2])
    return found


THE_GAME_MAKES = the_game_makes()
THE_PACK_HAS = the_pack_makes()

# Not having the game's jar is a state this file can describe; having it and
# reading nothing out of it is a bug, and the difference matters because the
# second one used to look exactly like the first. Both drop checks below sit
# behind this list, so an empty one turns both of them off at once and every
# misspelt drop in the module reads as fine.
if os.path.isfile(GAME_JAR) and len(THE_GAME_MAKES) < 2_000:
    fail(f"the game's jar is here and only {len(THE_GAME_MAKES)} item or block "
         f"name(s) could be read out of it, so every check in this file that "
         f"asks whether a thing exists is checking nothing at all")
if not THE_PACK_HAS:
    fail("not one item or block name could be read out of this pack's own "
         "language files, so a drop of something we make reads as invented")


harness_out = ""
if not os.path.isfile(HARNESS):
    fail("there is no harness, so whether a harder beast is worth more is an opinion")
else:
    with tempfile.TemporaryDirectory() as tmp:
        staged = os.path.join(tmp, "src")
        os.makedirs(staged)
        sources = [os.path.join(PURE, n) for n in os.listdir(PURE) if n.endswith(".java")]
        # And whatever sits below and is pure JDK too. Without it the summons'
        # own rules do not compile, and the one thing they prove -- that nothing
        # outlives the cooldown meant to limit it -- is measured against
        # cooldowns the harness cannot see.
        for folder in BELOW:
            if not os.path.isdir(folder):
                fail(f"{folder} is not where this expects it, so the rules below "
                     f"this module cannot be compiled against")
                continue
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
                     f"something in the rules can loop forever — which is a server that "
                     f"stops responding, not a wrong number")
            if ran is None:
                pass
            elif ran.returncode != 0:
                fail("the bestiary harness failed:\n" + harness_trouble(ran.stdout))
            else:
                harness_out = ran.stdout
                said = [line for line in ran.stdout.strip().splitlines()
                        if not line.startswith("EARNED")]
                print("the arithmetic     : " + said[-1])

rules = read(PURE, "BeastRules.java")
plain = strip_comments(rules)
squished = squashed(plain)

# The three properties have to be the three properties. A module whose whole
# reason for existing is a pair of ratios, with the ratios quietly deleted,
# passes every other check in this file.
for named in ("notWorthIt", "spread", "outfarmed", "offTheScale", "lopsided", "derive"):
    if f"public static " not in plain or named not in plain:
        fail(f"the rules no longer have {named}, which is one of the things this "
             f"module exists to prove")

derived = squashed(body_of(plain, "public static Map<String, Reward> derive("))
if "order.sort((first, second) -> Double.compare(first.threat(), second.threat()));" \
        not in derived:
    fail("the reward is not worked out in order of danger, so 'one more than the thing "
         "below' means one more than whichever beast happened to be written above it")
if "long worth = Math.max(Math.max(Math.round(beast.threat() * rate), below + 1L), " \
        "LEAST_WORTH);" not in derived:
    fail("a reward is no longer forced past the reward of the beast below it; two "
         "beasts of nearly equal danger will round onto the same number and one of "
         "them becomes the mob nobody fights twice")
if "int drops = (int) Math.max(LEAST_DROPS, Math.min(affordable, wanted));" not in derived:
    fail("the number of items is no longer capped at what the reward can pay for and "
         "still leave an orb, so a weak enough beast is handed a negative number of "
         "experience orbs and the whole table throws on class load")

worth_body = squashed(body_of(plain, "public double worth()"))
if "return experience + (double) drops * WORTH_OF_A_DROP;" not in worth_body:
    fail("what a kill is worth is no longer its orbs plus its items at the stated "
         "price, so the arithmetic that derived the table and the arithmetic that "
         "checks it are two different sums")

rate_body = squashed(body_of(plain, "public static double rate(Beast beast, Reward"))
if "return reward.worth() / beast.threat();" not in rate_body:
    fail("reward per unit of danger is not reward over danger, so the spread the "
         "harness measures is a spread of something else")

# The whole record, header and body: a reward written back into the table is
# the bug this module was rebuilt to remove, and it is just as much a bug when
# it arrives as an accessor returning a literal as when it arrives as a field.
start = plain.find("public record Beast(")
beast_body = body_of(plain, "public record Beast(")
declaration = ""
if start >= 0 and beast_body:
    declaration = squashed(plain[start:plain.find(beast_body, start)] + beast_body)
elif start >= 0:
    fail("the beast record has no body this file can read, so nothing checks what is "
         "stored in it")
for stored in ("experience", "drops"):
    if re.search(r"\b" + stored + r"\b", declaration):
        fail(f"{stored} is back in the beast record; a reward written beside the stats "
             f"it is supposed to be derived from is a reward that disagrees with them, "
             f"and this module has already shipped that bug once")

climbing = squashed(body_of(plain, "public static List<String> noReasonToClimb()"))
if "double bottom = averageRate(1);" not in climbing \
        or "double top = averageRate(highest);" not in climbing:
    fail("whether climbing a ladder pays is no longer the top of the ladders against "
         "the bottom, so a bestiary where getting stronger buys nothing passes")
if "if (top <= bottom) {" not in climbing:
    fail("the top of the ladders is allowed to pay no better than the bottom, so every "
         "player settles on the safest thing that drops anything and never looks up")

print("purity             : the rules are the JDK alone, and the reward is derived in "
      "order of danger rather than written down")

# ---------------------------------------------------------------------------
# 3. The two derivations
# ---------------------------------------------------------------------------
#
# The generator has its own copy of derive(), because it writes loot tables and
# cannot run a JVM to ask. Two copies of an arithmetic is a bug waiting for
# somebody to edit one of them, so they are compared here rather than trusted.

spec = importlib.util.spec_from_file_location("gen_bestiary", GENERATOR)
generator = None
if spec is None or not os.path.isfile(GENERATOR):
    fail("there is no generator, so every sprite and loot table in this module is "
         "hand-written and nothing checks them against the bestiary")
else:
    generator = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(generator)
    except SystemExit as stop:
        generator = None
        fail(f"the generator cannot read the Java any more: {stop}")
    except Exception as broke:  # noqa: BLE001 - a crash here is a finding
        # Not a bare traceback. The generator reads the rules at import, so a
        # rule file that has been renamed or deleted used to end this whole
        # validator in a stack trace with none of its findings printed —
        # including the one that said which file was missing. A checker that
        # dies instead of reporting is a checker that says nothing.
        generator = None
        fail(f"the generator stopped before it could be compared against the "
             f"rules: {type(broke).__name__}: {broke}")

said_earned = {}
for line in harness_out.splitlines():
    parts = line.split()
    if len(parts) == 4 and parts[0] == "EARNED":
        said_earned[parts[1]] = (int(parts[2]), int(parts[3]))

if generator is not None and harness_out:
    if not said_earned:
        fail("the harness no longer prints the table it derived, so the generator's "
             "copy of the arithmetic is believed rather than checked")
    mine = {name: tuple(value) for name, value in generator.REWARDS.items()}
    if mine != said_earned:
        for name in sorted(set(mine) | set(said_earned)):
            if mine.get(name) != said_earned.get(name):
                fail(f"{name} earns {said_earned.get(name)} in the rules and "
                     f"{mine.get(name)} in the generator, so the loot table it writes "
                     f"hands out a different amount than the bestiary is balanced on")
    else:
        print(f"the two derivations: {len(mine)} beast(s), and the generator's copy of "
              f"the arithmetic agrees with the rules on every one")

# ---------------------------------------------------------------------------
# 4. The sheet
# ---------------------------------------------------------------------------

model = read(SRC, "client/BeastModel.java")
model_plain = strip_comments(model)
# The boxes, the sheet and the measuring moved into CreatureModel when the
# fauna arrived, because a second copy of them with Animal written where Beast
# is would be the same mistake as two copies of a hitbox, one level up.
shared = read(SRC, "client/CreatureModel.java")
shared_plain = strip_comments(shared)
if not shared:
    fail("there is nothing shared between the two rosters' models any more, so "
         "the sheet arithmetic is written twice and the two copies will drift")

# Read here rather than borrowed off the generator. A generator that will not
# start is one of the states these checks exist to describe, and a check that
# falls silent exactly then is a check that only ever reports what does not
# matter.
FAMILY_NAMES = dict(re.findall(
    r'public static final String (PROWLER|BRUTE|WIGHT) = "(\w+)";',
    strip_comments(rules, keep_strings=True)))

table = re.search(r"public static final List<Beast> BEASTS = List\.of\((.*?)\);",
                  strip_comments(rules, keep_strings=True), re.S)
beasts = []
for row in re.finditer(
        r'new Beast\("(\w+)", (PROWLER|BRUTE|WIGHT), (\d+), ([\d.]+)D, ([\d.]+)D, '
        r'([\d.]+)D, ([\d.]+)D, (\d+), ([\d.]+)F, ([\d.]+)F\)',
        table.group(1) if table else ""):
    beasts.append({
        "id": row.group(1),
        "family": FAMILY_NAMES.get(row.group(2), row.group(2)),
        "tier": int(row.group(3)),
        "weight": int(row.group(8)),
        "width": float(row.group(9)),
        "height": float(row.group(10)),
    })
if not beasts:
    fail("this file can no longer read the bestiary out of the rules, so everything "
         "below is checking an empty list and passing")

PART_NAMES = dict(re.findall(
    r'public static final String (HEAD|BODY|ARM_LEFT|ARM_RIGHT|LEG_LEFT|LEG_RIGHT)'
    r' = "(\w+)";', strip_comments(shared, keep_strings=True)))

shapes = {}
for block in re.finditer(
        r"made\.put\(BeastRules\.(PROWLER|BRUTE|WIGHT), List\.of\((.*?)\)\);",
        model_plain, re.S):
    parts = []
    for row in re.finditer(
            r"new Part\((HEAD|BODY|ARM_LEFT|ARM_RIGHT|LEG_LEFT|LEG_RIGHT), "
            r"([-\d.]+)F, ([-\d.]+)F, ([-\d.]+)F, ([-\d.]+)F, ([-\d.]+)F, "
            r"([-\d.]+)F, (\d+), (\d+), (\d+)\)", block.group(2)):
        parts.append({
            "name": row.group(1),
            "pivot": (float(row.group(2)), float(row.group(3)), float(row.group(4))),
            "box": (float(row.group(5)), float(row.group(6)), float(row.group(7))),
            "size": (int(row.group(8)), int(row.group(9)), int(row.group(10))),
        })
    shapes[FAMILY_NAMES.get(block.group(1), block.group(1))] = parts

for family in sorted({beast["family"] for beast in beasts}):
    if family not in shapes:
        fail(f"nothing is shaped like a {family}, so every beast in that family is a "
             f"crash the first time one is drawn")
for family, parts in sorted(shapes.items()):
    if len(parts) != 6:
        fail(f"a {family} is made of {len(parts)} part(s) rather than six; the walk "
             f"cycle reaches for all six by name and will not find them")

slots = {}
for name, x, y, wide, tall in re.findall(
        r"(HEAD|BODY|ARM_LEFT|ARM_RIGHT|LEG_LEFT|LEG_RIGHT), new int\[\]\{"
        r"(\d+), (\d+), (\d+), (\d+)\}", shared_plain):
    slots[name] = (int(x), int(y), int(wide), int(tall))

sheet = re.search(r"public static final int SHEET = (\d+);", shared_plain)
sheet = int(sheet.group(1)) if sheet else 0

if len(slots) != 6:
    fail(f"the sheet has {len(slots)} corners on it rather than six, so at least one "
         f"part of every beast is drawn over another part")
for name, (x, y, wide, tall) in sorted(slots.items()):
    if x + wide > sheet or y + tall > sheet:
        fail(f"{name}'s corner runs off a {sheet}-pixel sheet, so that part of every "
             f"beast is transparent")
    for other, (ox, oy, owide, otall) in sorted(slots.items()):
        if other <= name:
            continue
        if x < ox + owide and ox < x + wide and y < oy + otall and oy < y + tall:
            fail(f"{name} and {other} overlap on the sheet, so painting one repaints "
                 f"the other and no beast can have both")

for family, parts in sorted(shapes.items()):
    for part in parts:
        wide, tall, deep = part["size"]
        slot = slots.get(part["name"])
        if slot is None:
            fail(f"a {family}'s {part['name']} has no corner of the sheet at all, so "
                 f"it is drawn out of whatever happens to be at the origin")
            continue
        if 2 * deep + 2 * wide > slot[2] or deep + tall > slot[3]:
            fail(f"a {family}'s {part['name']} unwraps to "
                 f"{2 * deep + 2 * wide}x{deep + tall} and its corner of the sheet "
                 f"is {slot[2]}x{slot[3]}, so one of its faces is drawn in another "
                 f"part's corner")
print(f"the sheet          : {len(slots)} corners on a {sheet}-pixel sheet, none "
      f"overlapping, and every box in every family fits the corner it is drawn in")

# ---------------------------------------------------------------------------
# 5. As big as it looks
# ---------------------------------------------------------------------------
#
# A modded mob whose model is not the size of its hitbox is the most common
# visual bug in a big pack and is never filed as one: it reads as "I keep
# missing it". The renderer squashes the shape onto the hitbox, so this checks
# the squashing exists, is worked out from the hitbox, and lands inside it.

renderer = strip_comments(read(SRC, "client/BeastRenderer.java"))
scaling = squashed(body_of(renderer, "protected void scale(BeastEntity beast,"))
if "float across = row.width() * PIXELS / Math.max(shape.wide(), shape.deep());" \
        not in scaling:
    fail("how wide a beast is drawn is no longer worked out from how wide it is, so a "
         "beast whose row is edited keeps the size of whatever it was before")
if "float up = row.height() * PIXELS / shape.tall();" not in scaling:
    fail("how tall a beast is drawn is no longer worked out from how tall it is, which "
         "is a head above its own hitbox and an arrow that goes through it")
if "pose.scale(across, up, across);" not in scaling:
    fail("the shape is not squashed onto the hitbox at all, so every beast is the size "
         "of the shape it shares with two others")

bounds_body = squashed(body_of(shared_plain,
                               "public static Bounds bounds(List<Part> parts)"))
if "return new Bounds(highX - lowX, highY - lowY, highZ - lowZ);" not in bounds_body:
    fail("a shape's size is no longer measured across its own boxes, so the renderer "
         "squashes onto a number somebody typed")
# And both rosters ask that one function rather than each keeping a copy.
for who, where in (("BeastModel", model_plain),
                   ("FaunaModel", strip_comments(read(SRC, "client/FaunaModel.java")))):
    if "return CreatureModel.bounds(parts);" not in squashed(where):
        fail(f"{who} measures its own shapes instead of asking the one function "
             f"that both rosters share, so the two copies can disagree about how "
             f"big something is and only one of them is the one the renderer uses")

if shapes and beasts:
    PIXELS = 16.0
    measured = {}
    for family, parts in sorted(shapes.items()):
        low = [float("inf")] * 3
        high = [float("-inf")] * 3
        for part in parts:
            for axis in range(3):
                start = part["pivot"][axis] + part["box"][axis]
                low[axis] = min(low[axis], start)
                high[axis] = max(high[axis], start + part["size"][axis])
        measured[family] = (high[0] - low[0], high[1] - low[1], high[2] - low[2])
        if min(measured[family]) <= 0.0:
            fail(f"a {family} has no extent in one direction, so it is drawn as a "
                 f"sheet of paper")
    for beast in beasts:
        if beast["family"] not in measured:
            # Already reported above. Carrying on rather than throwing is the
            # point: a validator that dies partway through prints none of the
            # problems it had already found, which is how one broken thing
            # hides five others.
            continue
        wide, tall, deep = measured[beast["family"]]
        across = beast["width"] * PIXELS / max(wide, deep)
        up = beast["height"] * PIXELS / tall
        drawn = (wide * across, tall * up, deep * across)
        box = (beast["width"] * PIXELS, beast["height"] * PIXELS,
               beast["width"] * PIXELS)
        for axis, side in enumerate(("wide", "tall", "deep")):
            if drawn[axis] > box[axis] + 1e-6:
                fail(f"{beast['id']} is drawn {drawn[axis] / PIXELS:.2f} blocks {side} "
                     f"inside a hitbox {box[axis] / PIXELS:.2f} blocks {side}, so part "
                     f"of it is outside the thing you are swinging at")
        if abs(drawn[1] - box[1]) > 1e-6:
            fail(f"{beast['id']} is drawn {drawn[1] / PIXELS:.2f} blocks tall in a "
                 f"hitbox {box[1] / PIXELS:.2f} blocks tall, so its head is either "
                 f"above the box or floating inside it")
        if abs(max(drawn[0], drawn[2]) - box[0]) > 1e-6:
            fail(f"{beast['id']} does not touch the sides of its own hitbox, so there "
                 f"is a band around it that can be hit and cannot be seen")
    print(f"as big as it looks : {len(beasts)} beasts, every one of them exactly as "
          f"tall and as wide on screen as the box you swing at")

# ---------------------------------------------------------------------------
# 6. What it leaves
# ---------------------------------------------------------------------------
#
# The loot tables are read back and their expected haul worked out from what
# they say, rather than from what the generator meant. A uniform roll from
# nothing to n averages n over two; looting is the player's and is left out of
# the sum on purpose.

if beasts and said_earned:
    for beast in beasts:
        path = os.path.join(RES, "data", NS, "loot_table/entities",
                            beast["id"] + ".json")
        if not os.path.isfile(path):
            fail(f"{beast['id']} has no loot table, so it drops nothing at all")
            continue
        table = json.loads(read(path))
        expected = 0.0
        for pool in table.get("pools", []):
            rolls = pool.get("rolls", 1)
            for entry in pool.get("entries", []):
                for function in entry.get("functions", []):
                    if function.get("function") != "minecraft:set_count":
                        continue
                    count = function.get("count", {})
                    if count.get("type") != "minecraft:uniform":
                        fail(f"{beast['id']}'s loot uses a {count.get('type')} count, "
                             f"which this file cannot work an expectation out of, so "
                             f"what it drops has stopped being checked")
                        continue
                    expected += rolls * (count.get("min", 0.0)
                                         + count.get("max", 0.0)) / 2.0
        wanted = said_earned.get(beast["id"], (0, 0))[1]
        if abs(expected - wanted) > 1e-9:
            fail(f"{beast['id']}'s loot table hands out {expected} items on average "
                 f"and the bestiary is balanced on it leaving {wanted}, so the reward "
                 f"the harness proved rises with danger is not the reward a player gets")
    # And what it hands out is a thing that exists.
    #
    # The sum above is blind to this: a loot table that drops six
    # minecraft:iron_nuggett averages six items just as convincingly as one
    # that drops six iron nuggets, and the second letter t is the whole bug.
    # The symptom is a mob that kills you and leaves nothing, with every
    # check in this file passing. So every name in every table is looked up
    # in the game's own language file, which names every item and block the
    # game ships, and in this pack's.
    dropped = set()
    for beast in beasts:
        table = read(RES, "data", NS, "loot_table/entities", beast["id"] + ".json")
        if not table:
            continue
        for poolset in json.loads(table).get("pools", []):
            for entry in poolset.get("entries", []):
                if entry.get("type") == "minecraft:item" and entry.get("name"):
                    dropped.add(entry["name"])
    if not dropped:
        fail("no loot table names a single item, so the nine of them hand out "
             "nothing and the arithmetic above is balancing zero against zero")
    elif not THE_GAME_MAKES:
        print("what it drops      : the game's jar is not here, so the drops "
              "were not looked up in it")
    else:
        missing = sorted(one for one in dropped
                         if one not in THE_GAME_MAKES
                         and one not in THE_PACK_HAS)
        if missing:
            fail(f"{len(missing)} thing(s) are dropped and neither the game "
                 f"nor this pack makes them, so the mob that drops one "
                 f"kills you and leaves nothing while every other check "
                 f"here passes: " + ", ".join(missing))
        print(f"what it drops      : {len(dropped)} distinct drop(s), every "
              f"one of them a thing the game or this pack really makes")

    print(f"what it leaves     : {len(beasts)} loot table(s) read back, and every one "
          f"hands out exactly what the bestiary says it does")

# ---------------------------------------------------------------------------
# 7. What you meet
# ---------------------------------------------------------------------------

if beasts:
    seen = {}
    tags_used = {}
    modifiers = os.path.join(RES, "data", NS, "neoforge/biome_modifier")
    for name in sorted(os.listdir(modifiers)) if os.path.isdir(modifiers) else []:
        rule = json.loads(read(modifiers, name))
        if rule.get("type") != "neoforge:add_spawns":
            continue
        # A beast may be somewhere in particular. That rule used to be that
        # every one of them had to be in #minecraft:is_overworld, which was
        # right while the whole roster was overworld-wide and is the wrong
        # shape now: a roster where every mob is everywhere is nine strangers
        # with no sense of place. What matters is the pair of claims below —
        # every tag is one the game really has, and one beast of each family
        # is somewhere a player certainly goes.
        tags_used.setdefault(str(rule.get("biomes")), []).append(name)
        for spawner in rule.get("spawners", []):
            kind = spawner.get("type", "")
            if kind in seen:
                fail(f"{kind} is spawned by {name} and by {seen[kind]}; it appears "
                     f"twice as often as its weight says and the shares the harness "
                     f"proved are wrong")
            seen[kind] = name
            if spawner.get("minCount", 0) < 1:
                fail(f"{kind} spawns in groups of {spawner.get('minCount')}, so the "
                     f"game rolls it and puts nothing there")
    for beast in beasts:
        kind = f"{NS}:{beast['id']}"
        if kind not in seen:
            fail(f"{beast['id']} is registered and never spawns, so the only way to "
                 f"meet one is a spawn egg")
            continue
        rule = json.loads(read(modifiers, seen[kind]))
        for spawner in rule.get("spawners", []):
            if spawner.get("type") == kind and spawner.get("weight") != beast["weight"]:
                fail(f"{beast['id']} spawns at weight {spawner.get('weight')} and its "
                     f"row says {beast['weight']}, so the share of the dark the harness "
                     f"proved is not the share a player meets")
    # Every tag a beast is put in is a tag the game really ships. A tag
    # nothing fills is a mob that never spawns, and from outside that looks
    # exactly like a mob that spawns rarely — which is the quietest bug a
    # pack can have and the one this whole section exists for.
    GAME_JAR = os.path.join(os.environ.get("ELYSIUM_MC_LIBS", "/tmp/mc"),
                            "vanilla.jar")
    if not os.path.isfile(GAME_JAR):
        print("what you meet      : the game's jar is not here, so the biome "
              "tags were not checked against it")
    else:
        import zipfile
        with zipfile.ZipFile(GAME_JAR) as jar:
            THE_GAME_HAS = {one.split("/")[-1][:-5] for one in jar.namelist()
                            if "/tags/worldgen/biome/" in one
                            and one.endswith(".json")}
        ours = set()
        our_tags = os.path.join(RES, "data")
        for dirpath, _, filenames in os.walk(our_tags):
            if "tags/worldgen/biome" not in dirpath.replace(os.sep, "/"):
                continue
            for one in filenames:
                if one.endswith(".json"):
                    ours.add(one[:-5])
        if len(THE_GAME_HAS) < 20:
            fail(f"only {len(THE_GAME_HAS)} biome tag(s) could be read out of "
                 f"the game's jar, so the check below is checking nothing")
        else:
            for tag, where in sorted(tags_used.items()):
                if not tag.startswith("#"):
                    fail(f"{where[0]} names the biome {tag} rather than a tag; "
                         f"a mob tied to one biome vanishes the day somebody "
                         f"adds a world preset")
                    continue
                plain = tag[1:].split(":", 1)[-1]
                if plain not in THE_GAME_HAS and plain not in ours:
                    fail(f"{where[0]} puts a beast in {tag} and neither the "
                         f"game nor this pack has such a tag, so it never "
                         f"spawns — which from outside looks exactly like a "
                         f"mob that spawns rarely")

    # And one of each family is somewhere a player certainly goes.
    EVERYWHERE = "#minecraft:is_overworld"
    for family in sorted({beast["family"] for beast in beasts}):
        anywhere = False
        for beast in beasts:
            if beast["family"] != family:
                continue
            where = seen.get(f"{NS}:{beast['id']}")
            if where and json.loads(read(modifiers, where)).get("biomes") \
                    == EVERYWHERE:
                anywhere = True
        if not anywhere:
            fail(f"nothing in the {family} family is in {EVERYWHERE}, so a "
                 f"player who stays in the plains never meets one at all and "
                 f"a third of the bestiary is a rumour")

    print(f"what you meet      : {len(seen)} beast(s) across "
          f"{len(os.listdir(modifiers)) if os.path.isdir(modifiers) else 0} spawn "
          f"rule(s), each named once, each at the weight its row declares")

# ---------------------------------------------------------------------------
# 8. The words, and the tree rebuilt
# ---------------------------------------------------------------------------

lang_path = os.path.join(RES, "assets", NS, "lang/en_us.json")
lang = json.loads(read(lang_path)) if os.path.isfile(lang_path) else {}
if beasts:
    for beast in beasts:
        for key in (f"entity.{NS}.{beast['id']}",
                    f"item.{NS}.{beast['id']}_spawn_egg"):
            if key not in lang:
                fail(f"{key} has no name, so it reads as its own translation key in "
                     f"the world")
        why = lang.get(f"bestiary.{NS}.why.{beast['id']}", "")
        if len(why) < 40:
            fail(f"{beast['id']} is described in {len(why)} characters; a line that "
                 f"short tells a player nothing they could not see, and this is the "
                 f"only place the pack says what meeting one is like")

if os.path.isfile(GENERATOR):
    with tempfile.TemporaryDirectory() as tmp:
        env = dict(os.environ, ELYSIUM_BESTIARY_OUT=tmp)
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
                print("the words          : every sprite, model, loot table and spawn "
                      "rule rebuilt and identical")

# ---------------------------------------------------------------------------
# 9. The beasts in the world, and the seam
# ---------------------------------------------------------------------------

entities = strip_comments(read(SRC, "registry/BeastEntities.java"))
items = strip_comments(read(SRC, "registry/BeastItems.java"))
client = strip_comments(read(SRC, "client/BestiaryClient.java"))
entity = strip_comments(read(SRC, "entity/BeastEntity.java"))

registering = squashed(body_of(entities, "for (BeastRules.Beast beast : BeastRules.BEASTS)"))
if "for (BeastRules.Beast beast : BeastRules.BEASTS) {" not in squashed(entities):
    fail("the beasts are not registered by walking the bestiary, so a tenth row is a "
         "row nothing in the game has ever heard of")
if ".sized(beast.width(), beast.height())" not in registering:
    fail("an entity type is not built to the size in its own row, so the hitbox and "
         "the table disagree and the renderer squashes onto the wrong box")

# A loop that walks the bestiary and then decides is not a loop that registers
# the bestiary. Eight of nine registered is a pack that works until somebody
# meets the ninth, a month later, in somebody else's crash report.
egg_loop = squashed(body_of(items, "for (BeastRules.Beast beast : BeastRules.BEASTS)"))
for what, loop in (("entity type", registering), ("spawn egg", egg_loop)):
    for escape in ("continue;", "break;", "if ("):
        if escape in loop:
            fail(f"the {what} loop decides whether to register each beast; a bestiary "
                 f"registered in part is a mod that works until a player meets the one "
                 f"that was skipped")

# And the same thing said at load time, because a check in this file only runs
# for whoever runs this file.
for where, source, what in (("BeastEntities", entities, "entity type(s)"),
                            ("BeastItems", items, "spawn egg(s)")):
    counted = 'if (made.size() != BeastRules.BEASTS.size()) {'
    if counted not in squashed(source):
        fail(f"{where} does not count what it registered against the bestiary, so the "
             f"game starts with {what} missing and says nothing")

attributes = squashed(body_of(entities, "public static void onAttributes("))
if "for (BeastRules.Beast beast : BeastRules.BEASTS) {" not in attributes:
    fail("attributes are not given out by walking the bestiary, so a beast added later "
         "spawns with a player's health and no attack at all")

placements = squashed(body_of(entities, "public static void onSpawnPlacement("))
if "for (BeastRules.Beast beast : BeastRules.BEASTS) {" not in placements:
    fail("spawn placement is not registered by walking the bestiary, so a beast added "
         "later spawns in daylight inside somebody's base")

renderers = squashed(body_of(client, "public static void onRenderers("))
if "for (BeastRules.Beast beast : BeastRules.BEASTS) {" not in renderers:
    fail("renderers are registered one at a time, which is how a pack ends up with one "
         "mob drawn as a black and magenta cube and nobody noticing for a month")

layers = squashed(body_of(client, "public static void onLayers("))
if "for (String family : BeastRules.FAMILIES) {" not in layers:
    fail("model layers are registered one at a time, so a fourth family is a crash on "
         "the first one a player meets")

eggs = squashed(body_of(items, "register()"))
if "for (BeastRules.Beast beast : BeastRules.BEASTS) {" not in eggs:
    fail("spawn eggs are not made by walking the bestiary, so a beast added later "
         "cannot be put in the world to look at")

lookup = squashed(body_of(entity, "public BeastRules.Beast beast()"))
if "String name = EntityType.getKey(getType()).getPath();" not in lookup:
    fail("a beast no longer finds its own row by the name it was registered under, so "
         "whatever it is storing instead can be stored wrong")

reward = squashed(body_of(entity, "protected int getBaseExperienceReward()"))
if "return BeastRules.earned(beast()).experience();" not in reward:
    fail("what a kill gives is not what the bestiary derived, so the ladder the harness "
         "proved rises is not the ladder a player climbs")

burn = squashed(body_of(entity, "public void aiStep()"))
if "setRemainingFireTicks(8 * 20);" not in burn:
    fail("wights no longer burn at dawn, which is the one thing that makes a family "
         "more than a set of numbers")

dimensions = squashed(body_of(entity, "public net.minecraft.world.entity.EntityDimensions "
                                      "getDefaultDimensions("))
if "return net.minecraft.world.entity.EntityDimensions.scalable(beast.width(), " \
        "beast.height());" not in dimensions:
    fail("a beast's dimensions are not its row's, so it is the size of whichever beast "
         "the game happened to build the type for")

# Every register there is, put on the bus. Naming the class is not enough:
# a module that registers two of a class's three registers has a third that
# silently contains nothing.
main = squashed(strip_comments(read(SRC, "ElysiumBestiary.java")))
for where, source in (("BeastEntities", entities), ("BeastItems", items)):
    found = re.findall(
        r"public static final DeferredRegister[\w.<>?,\s]*?\s(\w+)\s*=", source)
    if not found:
        fail(f"{where} has no registers in it at all, so nothing it declares reaches "
             f"the game")
    for field in found:
        wanted = f"com.elysium.bestiary.registry.{where}.{field}.register(modEventBus);"
        if wanted not in main and f"{where}.{field}.register(modEventBus);" not in main:
            fail(f"{where}.{field} is declared and never put on the bus, so everything "
                 f"in it is written, compiled, and absent from the game")

print("in the world       : every beast, attribute, spawn rule, renderer and egg "
      "walked out of the one table")

# ---------------------------------------------------------------------------

print("-" * 70)
# ---------------------------------------------------------------------------
# Where each beast is, and whether the world really has such a place
# ---------------------------------------------------------------------------

HAUNT_RULES = strip_comments(read(SRC, "rules/HauntRules.java"), keep_strings=True)
BEAST_ENTITY = strip_comments(read(SRC, "entity/BeastEntity.java"), keep_strings=True)
if not HAUNT_RULES:
    fail("nothing says where any beast is, so a mob whose spawn cannot happen "
         "is registered, rendered, looted, harnessed and never met — which is "
         "the quietest bug a pack can have")
else:
    _one_line = re.sub(r"\s+", " ", HAUNT_RULES)
    _beast_table = re.search(r"List<Haunt> HAUNTS = List\.of\((.*?)\); ", _one_line)
    _fauna_table = re.search(r"List<Haunt> THE_FAUNA = List\.of\((.*?)\); ", _one_line)
    if not _beast_table or not _fauna_table:
        fail("one of the two tables of haunts is gone, so half the things this "
             "module registers are somewhere nothing says")
    _HAUNT_ROW = (r'new Haunt\("([a-z_]+)", "(#?[a-z_:]+)", (-?\w+), (-?\w+), '
                  r'(-?\w+), (-?\w+), (\d+), (\d+)\)')
    haunts = re.findall(_HAUNT_ROW, _beast_table.group(1) if _beast_table else "")
    fauna_haunts = re.findall(_HAUNT_ROW,
                              _fauna_table.group(1) if _fauna_table else "")
    if len(haunts) != len(beasts):
        fail(f"there are {len(haunts)} haunt(s) for {len(beasts)} beast(s), so "
             f"one of them is somewhere twice or nowhere at all")
    for needed, why in (
            ("public static List<String> faultsInBands(",
             "nothing asks whether a beast's light and height bands are bands "
             "a world really has"),
            ("public static List<String> faultsInRoom(",
             "nothing asks whether a group fits on the floor it is asked to "
             "stand on, so a weight could promise a rate the game cannot "
             "place"),
            ("public static List<String> faultsInDominance(",
             "nothing asks whether a beast is a strictly worse version of "
             "another, so a player could meet a weaker copy of a fight they "
             "have already had with a different model on it")):
        if needed not in HAUNT_RULES:
            fail(why)

    # And every one of them is actually added up. A rule that is written,
    # compiled, controlled by the harness and left out of the sum is the same
    # failure as a validator section appended below its own summary: it runs
    # in the controls, it never runs over the roster, and everything passes.
    _sum = squashed(body_of(HAUNT_RULES, "public static List<String> faults()"))
    _rules = set(re.findall(r"public static List<String> (faultsIn\w+)\(",
                            HAUNT_RULES))
    if len(_rules) < 4:
        fail(f"only {len(_rules)} haunt rule(s) are declared at all, so the "
             f"check below is checking nothing")
    # The whole statement, not the opening of one: a rule whose result is
    # worked out and then dropped on the floor is exactly the bug this is
    # looking for, and `bad.addAll(faultsInRoom(` would still be there.
    _added = {}
    for _name, _args in re.findall(r"bad\.addAll\((faultsIn\w+)\(([^;]*)\)\);",
                                   _sum):
        _added.setdefault(_name, []).append(_args)
    for _rule in sorted(_rules - set(_added)):
        fail(f"{_rule} is written, compiled and never added up by faults(), "
             f"so it answers the harness's controls and is never once asked "
             f"about the roster this pack ships")
    # And asked about every table it governs, not just the first one. There
    # are two tables of haunts now — the things that hunt you and the things
    # that do not — and a rule added up once covers one of them while looking
    # from here exactly like a rule that covers both.
    _tables = re.findall(r"List<Haunt> (\w+) = List\.of\(", HAUNT_RULES)
    _over_tables = set(re.findall(
        r"public static List<String> (faultsIn\w+)\([^)]*List<Haunt>", HAUNT_RULES))
    if len(_tables) < 2:
        fail(f"there are {len(_tables)} table(s) of haunts, so the check below "
             f"is checking nothing")
    for _rule in sorted(_over_tables & set(_added)):
        _calls = _added[_rule]
        if any("everyHaunt()" in _one for _one in _calls):
            continue
        for _table in _tables:
            if not any(_table in _one for _one in _calls):
                fail(f"{_rule} is written, added up once, and never asked "
                     f"about {_table} — so half of what this module puts in "
                     f"the world is judged by a rule the other half is judged "
                     f"by and this half is not")

    # The bands have to reach the game. A band in a table that nothing reads
    # is the same as no band at all, and looks the same from outside.
    _spawn = squashed(body_of(BEAST_ENTITY, "public static boolean canSpawn("))
    if "HauntRules.haunt(id);" not in _spawn:
        fail("a beast spawns wherever a zombie would, so the light and height "
             "bands are a table nothing reads and the whole bestiary is nine "
             "things in one place")
    if "if (pos.getY() < haunt.lowest() || pos.getY() > haunt.highest()) {" \
            not in _spawn:
        fail("the height band never reaches the game, so a beast written as "
             "living deep is a beast that spawns on the surface")
    if "return light >= haunt.darkest() && light <= haunt.brightest();" \
            not in _spawn:
        fail("the light band never reaches the game, so a beast written as "
             "living in the pitch dark spawns in daylight")

    # And what the world is written out as has to agree with the haunt.
    for beast, where, darkest, brightest, lowest, highest, least, most in \
            haunts + fauna_haunts:
        path = os.path.join(RES, "data", NS, "neoforge/biome_modifier",
                            f"add_{beast}_spawns.json")
        if not os.path.isfile(path):
            fail(f"{beast} has a haunt and no spawn rule in the world")
            continue
        rule = json.loads(read(path))
        if rule.get("biomes") != where:
            fail(f"{beast} haunts {where} and its spawn rule says "
                 f"{rule.get('biomes')}, so where the harness proved it lives "
                 f"is not where a player meets it")
        for spawner in rule.get("spawners", []):
            if spawner.get("minCount") != int(least) \
                    or spawner.get("maxCount") != int(most):
                fail(f"{beast} comes in groups of {least} to {most} and its "
                     f"spawn rule says {spawner.get('minCount')} to "
                     f"{spawner.get('maxCount')}")

    print(f"where they are     : {len(haunts) + len(fauna_haunts)} haunt(s), "
          f"every biome tag one "
          f"the game really ships, every light and height band one a world has "
          f"and one the game is actually told about, and every group small "
          f"enough to fit where it is asked to appear")


# ---------------------------------------------------------------------------
# The two entities the ability system cannot happen without
# ---------------------------------------------------------------------------
#
# Three of the library's thirty abilities throw or summon something, and until
# this module existed nothing in the pack was that something: the ability was
# priced, the relic carried it, every check in every module passed, and
# pressing the button did nothing. That is the state this section makes
# impossible to return to.

SUMMONS = read(SRC, "registry/SummonEntities.java")
SHAPES_SRC = read(os.path.join(ROOT, "elysium-lib/src/main/java/com/elysium/lib",
                               "ability/ElysiumAbilityShapes.java"))
if not SUMMONS:
    fail("nothing here fills in what a thrown or summoned ability throws or "
         "summons, so three of the library's abilities are silent")
elif not SHAPES_SRC:
    fail("the library no longer says which ability shapes need something in "
         "the world, so this module cannot know what it owes")
else:
    needed = re.findall(r'List<String> NEED_AN_ENTITY = List\.of\(([^;]*)\);',
                        re.sub(r"\s+", " ", SHAPES_SRC))
    needed = re.findall(r"[A-Z]+", needed[0]) if needed else []
    if len(needed) < 2:
        fail("the library names fewer than two shapes as needing an entity, "
             "so this check is checking nothing")
    for shape in needed:
        if f"ElysiumAbilityEntities.{shape.lower()}(" not in re.sub(
                r"\s+", " ", strip_comments(SUMMONS)):
            fail(f"nothing hands the library something for a {shape.lower()} "
                 f"ability, so every ability of that shape does nothing and "
                 f"says nothing")
    _fill = squashed(body_of(strip_comments(SUMMONS),
                             "public static void fillIn("))
    if "event.enqueueWork(() -> { ElysiumAbilityEntities.bolt(BOLT::get);" \
            not in _fill:
        fail("the entity types are handed over outside enqueueWork, so they "
             "are asked for before their registry is frozen — which is a "
             "crash on somebody else's machine and not on this one")
    if "ElysiumAbilityEntities.missing().isEmpty()" not in _fill:
        fail("nothing checks, after this module has registered, that every "
             "shape needing an entity has one; a shape left empty would ship "
             "as a relic that does nothing")
    for name, why in (("RelicBoltEntity", "there is nothing to throw"),
                      ("ShadeEntity", "there is nothing to summon")):
        if not os.path.isfile(os.path.join(SRC, "entity", name + ".java")):
            fail(f"{name} is not here, so {why}")
    # Both of them go out on their own. A summon that stayed is a mob a player
    # farms rather than an ability with a cooldown, and a bolt that lies on
    # the ground is a pile of arrows.
    for name in ("RelicBoltEntity", "ShadeEntity"):
        body = strip_comments(read(SRC, "entity", name + ".java"))
        if "discard();" not in body:
            fail(f"{name} never goes out, so an ability with a cooldown is a "
                 f"way of filling a world with entities")
        if "LASTS" not in body:
            fail(f"{name} does not say how long it lasts")

    print(f"the summons        : {len(needed)} shape(s) of ability that cannot "
          f"happen without something in the world, each given one here, each "
          f"going out on its own, and a check after registration that none of "
          f"them was left empty")


# ---------------------------------------------------------------------------
# The other half of the world
# ---------------------------------------------------------------------------
#
# Twelve animals that do not attack you. Everything below is the bestiary's
# own checks aimed at the second roster: that it is read out of the rules
# rather than typed here, that every one of them is exactly as big as it
# looks, that what it leaves is what the arithmetic says and is a thing that
# exists, and that not one of the twelve was registered by hand.

print("-" * 70)

FAUNA_RULES = strip_comments(read(SRC, "rules/FaunaRules.java"), keep_strings=True)
FAUNA_MODEL = strip_comments(read(SRC, "client/FaunaModel.java"), keep_strings=True)
FAUNA_ENTITY = strip_comments(read(SRC, "entity/FaunaEntity.java"), keep_strings=True)
if not FAUNA_RULES:
    fail("nothing says what an animal is, so this module is nine things that "
         "hunt you and nothing that does not — which is not a harder world, it "
         "is a thinner one")
else:
    KIND_NAMES = dict(re.findall(
        r'public static final String (GRAZER|FORAGER|FOWL|SWIMMER) = "(\w+)";',
        FAUNA_RULES))
    _fauna_one_line = re.sub(r"\s+", " ", FAUNA_RULES)
    _roster = re.search(r"List<Animal> ANIMALS = List\.of\((.*?)\); ",
                        _fauna_one_line)
    animals = []
    for row in re.finditer(
            r'new Animal\("([a-z_]+)", (GRAZER|FORAGER|FOWL|SWIMMER), '
            r'([\d.]+)D, ([\d.]+)D, ([\d.]+)F, ([\d.]+)F, '
            r'"([a-z_:]+)", "([a-z_:]+)", (\d+), (\d+)\)',
            _roster.group(1) if _roster else ""):
        animals.append({
            "id": row.group(1),
            "kind": KIND_NAMES.get(row.group(2), row.group(2)),
            "width": float(row.group(5)),
            "height": float(row.group(6)),
            "eats": row.group(7),
            "gives": row.group(8),
            "yield": int(row.group(9)),
            "weight": int(row.group(10)),
        })
    if not animals:
        fail("this file can no longer read the fauna out of the rules, so "
             "everything below is checking an empty list and passing")

    # Every rule the fauna declares is added up, for the same reason the
    # haunts' are: a rule that answers its controls and is never asked about
    # the roster is a rule that is not running.
    _fauna_sum = squashed(body_of(FAUNA_RULES, "public static List<String> faults()"))
    _fauna_rules = set(re.findall(
        r"public static List<String> (faultsIn\w+)\(", FAUNA_RULES))
    _fauna_added = set(re.findall(
        r"bad\.addAll\((faultsIn\w+)\([^;]*\)\);", _fauna_sum))
    if len(_fauna_rules) < 6:
        fail(f"only {len(_fauna_rules)} fauna rule(s) are declared at all, so "
             f"the check below is checking nothing")
    for _rule in sorted(_fauna_rules - _fauna_added):
        fail(f"{_rule} is written, compiled and never added up by the fauna's "
             f"faults(), so it answers the harness's controls and is never "
             f"once asked about the roster this pack ships")

    # Four shapes, six parts each, every box inside the corner of the sheet it
    # is drawn in. The same arithmetic the bestiary's sheet section does,
    # against the same slots, because both rosters draw on one sheet layout.
    fauna_shapes = {}
    for block in re.finditer(
            r"made\.put\(FaunaRules\.(GRAZER|FORAGER|FOWL|SWIMMER), "
            r"List\.of\((.*?)\)\);", strip_comments(FAUNA_MODEL), re.S):
        parts = []
        for row in re.finditer(
                r"new Part\((HEAD|BODY|ARM_LEFT|ARM_RIGHT|LEG_LEFT|LEG_RIGHT), "
                r"([-\d.]+)F, ([-\d.]+)F, ([-\d.]+)F, ([-\d.]+)F, ([-\d.]+)F, "
                r"([-\d.]+)F, (\d+), (\d+), (\d+)\)", block.group(2)):
            parts.append({
                "name": row.group(1),
                "pivot": (float(row.group(2)), float(row.group(3)),
                          float(row.group(4))),
                "box": (float(row.group(5)), float(row.group(6)),
                        float(row.group(7))),
                "size": (int(row.group(8)), int(row.group(9)), int(row.group(10))),
            })
        fauna_shapes[KIND_NAMES.get(block.group(1), block.group(1))] = parts
    for kind in sorted({animal["kind"] for animal in animals}):
        if kind not in fauna_shapes:
            fail(f"nothing is shaped like a {kind}, so every animal of that kind "
                 f"is a crash the first time one is drawn")
    for kind, parts in sorted(fauna_shapes.items()):
        if len(parts) != 6:
            fail(f"a {kind} is made of {len(parts)} part(s) rather than six; the "
                 f"walk cycle reaches for all six by name and will not find them")
        for part in parts:
            wide, tall, deep = part["size"]
            slot = slots.get(part["name"])
            if slot is None:
                fail(f"a {kind}'s {part['name']} has no corner of the sheet at "
                     f"all, so it is drawn out of whatever happens to be at the "
                     f"origin")
                continue
            if 2 * deep + 2 * wide > slot[2] or deep + tall > slot[3]:
                fail(f"a {kind}'s {part['name']} unwraps to "
                     f"{2 * deep + 2 * wide}x{deep + tall} and its corner of the "
                     f"sheet is {slot[2]}x{slot[3]}, so one of its faces is drawn "
                     f"in another part's corner")

    # And exactly as big as it looks, worked out the same way.
    fauna_renderer = strip_comments(read(SRC, "client/FaunaRenderer.java"))
    fauna_scaling = squashed(body_of(fauna_renderer,
                                     "protected void scale(FaunaEntity beast,"))
    for wanted, why in (
            ("float across = row.width() * PIXELS / Math.max(shape.wide(), "
             "shape.deep());",
             "how wide an animal is drawn is no longer worked out from how wide "
             "it is, so an animal whose row is edited keeps the size of whatever "
             "it was before"),
            ("float up = row.height() * PIXELS / shape.tall();",
             "how tall an animal is drawn is no longer worked out from how tall "
             "it is, which is a head above its own hitbox"),
            ("pose.scale(across, up, across);",
             "the shape is not squashed onto the hitbox at all, so every animal "
             "is the size of the shape it shares with two others")):
        if wanted not in fauna_scaling:
            fail(why)
    if fauna_shapes and animals:
        measured = {}
        for kind, parts in sorted(fauna_shapes.items()):
            low = [float("inf")] * 3
            high = [float("-inf")] * 3
            for part in parts:
                for axis in range(3):
                    start = part["pivot"][axis] + part["box"][axis]
                    low[axis] = min(low[axis], start)
                    high[axis] = max(high[axis], start + part["size"][axis])
            measured[kind] = (high[0] - low[0], high[1] - low[1], high[2] - low[2])
            if min(measured[kind]) <= 0.0:
                fail(f"a {kind} has no extent in one direction, so it is drawn as "
                     f"a sheet of paper")
        for animal in animals:
            if animal["kind"] not in measured:
                continue
            wide, tall, deep = measured[animal["kind"]]
            across = animal["width"] * 16.0 / max(wide, deep)
            up = animal["height"] * 16.0 / tall
            drawn = (wide * across, tall * up, deep * across)
            box = (animal["width"] * 16.0, animal["height"] * 16.0)
            if abs(drawn[1] - box[1]) > 1e-6:
                fail(f"{animal['id']} is drawn {drawn[1] / 16.0:.2f} blocks tall "
                     f"in a hitbox {box[1] / 16.0:.2f} blocks tall")
            if abs(max(drawn[0], drawn[2]) - box[0]) > 1e-6:
                fail(f"{animal['id']} does not touch the sides of its own hitbox, "
                     f"so there is a band around it that can be hit and cannot "
                     f"be seen")

    # What it leaves, read back out of the file rather than believed.
    rate_of = {}
    for kind, tilt in re.findall(r"(GRAZER|FORAGER|FOWL|SWIMMER), ([\d.]+)D",
                                 _fauna_one_line):
        rate_of[KIND_NAMES.get(kind, kind)] = float(tilt)
    dropped_by_animals = set()
    for animal in animals:
        path = os.path.join(RES, "data", NS, "loot_table/entities",
                            animal["id"] + ".json")
        if not os.path.isfile(path):
            fail(f"{animal['id']} has no loot table, so killing one leaves "
                 f"nothing and the whole point of keeping one is gone")
            continue
        table = json.loads(read(path))
        expected = 0.0
        for poolset in table.get("pools", []):
            rolls = poolset.get("rolls", 1)
            for entry in poolset.get("entries", []):
                if entry.get("type") == "minecraft:item" and entry.get("name"):
                    dropped_by_animals.add(entry["name"])
                for function in entry.get("functions", []):
                    if function.get("function") != "minecraft:set_count":
                        continue
                    count = function.get("count", {})
                    if count.get("type") != "minecraft:uniform":
                        fail(f"{animal['id']}'s loot uses a {count.get('type')} "
                             f"count, which this file cannot work an expectation "
                             f"out of, so what it leaves has stopped being checked")
                        continue
                    expected += rolls * (count.get("min", 0.0)
                                         + count.get("max", 0.0)) / 2.0
        if abs(expected - animal["yield"]) > 1e-9:
            fail(f"{animal['id']}'s loot table leaves {expected} items on average "
                 f"and its row says {animal['yield']}, so the wait the arithmetic "
                 f"worked out is a wait for something else")
        if dropped_by_animals and animal["gives"] not in dropped_by_animals:
            fail(f"{animal['id']} leaves {animal['gives']} in its row and its "
                 f"loot table hands out something else")
    if animals and not dropped_by_animals:
        fail("no animal's loot table names a single item, so the twelve of them "
             "leave nothing at all")

    # Everything they leave and everything they eat exists.
    if THE_GAME_MAKES:
        for animal in animals:
            for what, why in ((animal["eats"], "is bred with"),
                              (animal["gives"], "leaves")):
                if what not in THE_GAME_MAKES and what not in THE_PACK_HAS:
                    fail(f"{animal['id']} {why} {what} and neither the game "
                         f"nor this pack makes it, so it is an animal that "
                         f"cannot be bred or one that leaves nothing")

    # The share of the world each one is. The bestiary's own section checks
    # this for the nine; it walks the beasts and so has never once looked at
    # an animal, which is how a generator could hand every one of the twelve
    # the same weight and be told nothing.
    _modifiers = os.path.join(RES, "data", NS, "neoforge/biome_modifier")
    for animal in animals:
        rule_path = os.path.join(_modifiers, f"add_{animal['id']}_spawns.json")
        if not os.path.isfile(rule_path):
            fail(f"{animal['id']} has no spawn rule in the world, so the only "
                 f"way to meet one is a spawn egg")
            continue
        for spawner in json.loads(read(rule_path)).get("spawners", []):
            if spawner.get("type") != f"{NS}:{animal['id']}":
                continue
            if spawner.get("weight") != animal["weight"]:
                fail(f"{animal['id']} spawns at weight {spawner.get('weight')} "
                     f"and its row says {animal['weight']}, so how often a "
                     f"player meets one is not what the roster declares")

    # The share of the world each one is. The bestiary's own section checks
    # this for the nine; it walks the beasts and so has never once looked at
    # an animal, which is how a generator could hand every one of the twelve
    # the same weight and be told nothing.
    _modifiers = os.path.join(RES, "data", NS, "neoforge/biome_modifier")
    for animal in animals:
        rule_path = os.path.join(_modifiers, f"add_{animal['id']}_spawns.json")
        if not os.path.isfile(rule_path):
            fail(f"{animal['id']} has no spawn rule in the world, so the only "
                 f"way to meet one is a spawn egg")
            continue
        for spawner in json.loads(read(rule_path)).get("spawners", []):
            if spawner.get("type") != f"{NS}:{animal['id']}":
                continue
            if spawner.get("weight") != animal["weight"]:
                fail(f"{animal['id']} spawns at weight {spawner.get('weight')} "
                     f"and its row says {animal['weight']}, so how often a "
                     f"player meets one is not what the roster declares")

    # Not one of the twelve registered by hand.
    fauna_entities = read(SRC, "registry/FaunaEntities.java")
    fauna_items = read(SRC, "registry/FaunaItems.java")
    for where, source, why in (
            ("FaunaEntities", fauna_entities, "no animal exists at all"),
            ("FaunaItems", fauna_items, "no animal can be put in front of you "
                                        "to look at")):
        if not source:
            fail(f"{where} is not here, so {why}")
            continue
        walked = squashed(strip_comments(source)).count(
            "for (FaunaRules.Animal animal : FaunaRules.ANIMALS) {")
        if walked < (3 if where == "FaunaEntities" else 2):
            fail(f"{where} walks the roster {walked} time(s), and one of the "
                 f"things it does for each animal is being done for a list "
                 f"somebody typed instead — eleven of twelve is a pack that "
                 f"works until somebody meets the twelfth")
        if f"!= FaunaRules.ANIMALS.size()" not in strip_comments(source):
            fail(f"{where} does not count what it registered, so a roster that "
                 f"half registers is a roster that half exists")
    for field, where in (("TYPES", "FaunaEntities"), ("ITEMS", "FaunaItems"),
                         ("TABS", "FaunaItems")):
        wanted = f"com.elysium.bestiary.registry.{where}.{field}.register(modEventBus);"
        if wanted not in main:
            fail(f"{where}.{field} is declared and never put on the bus, so "
                 f"everything in it is written, compiled, and absent from the game")

    # A swimmer is a water creature and is placed in water. The game counts
    # the two buckets separately, and a fish in the land bucket crowds out
    # every animal in the world — which reads as "this pack has too few cows"
    # and is diagnosed nowhere.
    _category = squashed(body_of(strip_comments(fauna_entities),
                                 "public static MobCategory categoryOf("))
    if "MobCategory.WATER_CREATURE" not in _category \
            or "MobCategory.CREATURE" not in _category:
        fail("the fauna no longer sorts itself into the game's two spawn "
             "buckets, so either the fish crowd out the animals or the animals "
             "crowd out the fish")
    _placement = squashed(body_of(strip_comments(fauna_entities),
                                  "public static void onSpawnPlacement("))
    if "SpawnPlacementTypes.IN_WATER" not in _placement:
        fail("nothing is placed in the water, so the swimmers are put on the "
             "ground and the ocean is empty")

    # And the animal itself reads its row rather than storing it.
    _lookup = squashed(body_of(FAUNA_ENTITY, "public FaunaRules.Animal animal()"))
    if "String name = EntityType.getKey(getType()).getPath();" not in _lookup:
        fail("an animal no longer finds its own row by the name it was "
             "registered under, so whatever it is storing instead can be "
             "stored wrong")
    _dimensions = squashed(body_of(FAUNA_ENTITY,
                                   "public net.minecraft.world.entity."
                                   "EntityDimensions getDefaultDimensions("))
    if "return net.minecraft.world.entity.EntityDimensions.scalable(animal.width(), " \
            "animal.height());" not in _dimensions:
        fail("an animal's dimensions are not its row's, so it is the size of "
             "whichever animal the game happened to build the type for")
    _love = squashed(body_of(FAUNA_ENTITY, "public void resetLove()"))
    if "setAge(FaunaRules.patienceOf(animal()));" not in _love:
        fail("how long an animal waits before it will breed again is not the "
             "number the arithmetic worked out, so the one thing that stops a "
             "single row of the roster being the answer to every pen is a "
             "number nothing checks")
    _food = squashed(body_of(FAUNA_ENTITY, "public Ingredient food()"))
    if "animal().eats()" not in _food:
        fail("what an animal is bred with is no longer its own row's, so the "
             "rule that says no pen makes what a pen consumes is a rule about "
             "a table the game never reads")
    _spawn = squashed(body_of(FAUNA_ENTITY, "public static boolean canSpawn("))
    if "HauntRules.haunt(id);" not in _spawn:
        fail("an animal spawns wherever the game feels like, so its light and "
             "height bands are a table nothing reads")

    # Every animal drawn, every kind given a layer, and neither loop written out.
    client = squashed(strip_comments(read(SRC, "client/BestiaryClient.java")))
    if "for (FaunaRules.Animal animal : FaunaRules.ANIMALS) {" not in client:
        fail("the renderers are not walked from the roster, so an animal added "
             "tomorrow is a black-and-magenta cube and only the player who "
             "meets it will ever know")
    if "for (String kind : FaunaRules.KINDS) {" not in client:
        fail("the model layers are not walked from the kinds, so a kind with no "
             "layer is a crash the first time one is drawn")

    # The words.
    for animal in animals:
        for key in (f"entity.{NS}.{animal['id']}",
                    f"item.{NS}.{animal['id']}_spawn_egg",
                    f"bestiary.{NS}.why.{animal['id']}"):
            if key not in lang:
                fail(f"{key} has no name, so it reads as its own translation key")
        if not os.path.isfile(os.path.join(RES, "assets", NS, "textures/entity",
                                           animal["id"] + ".png")):
            fail(f"{animal['id']} has no sheet, so it is drawn as whatever the "
                 f"renderer finds at that path, which is nothing")

    print(f"the other half     : {len(animals)} animal(s) over "
          f"{len(fauna_shapes)} shape(s), every one exactly as big as it looks, "
          f"leaving what the arithmetic says it leaves, bred on something the "
          f"game really makes, and not one of them registered by hand")


# ---------------------------------------------------------------------------
# A mechanic apiece
# ---------------------------------------------------------------------------
#
# Nine beasts on three sets of family goals is nine rows of numbers and three
# fights. This section is the proof that it is nine fights: one verb each,
# none spare, one branch apiece to happen in, and every magnitude worked out
# from what the beast already is rather than typed beside it.

print("-" * 70)

BEHAVIOURS = strip_comments(read(SRC, "rules/BehaviourRules.java"), keep_strings=True)
ACTS = strip_comments(read(SRC, "entity/act/BeastActs.java"), keep_strings=True)
if not BEHAVIOURS:
    fail("nothing says what any beast does, so meeting a stalker and meeting a "
         "ripper are the same fight with a different model on it — which is "
         "what four of the jars this module is meant to replace exist to fix")
else:
    VERB_NAMES = dict(re.findall(
        r'public static final String (SPLIT|LEAP|STALK|ANCHOR|SUNDER|CHARGE'
        r'|DRIFT|CHILL|CALL) = "(\w+)";', BEHAVIOURS))
    _one_line = re.sub(r"\s+", " ", BEHAVIOURS)
    _verbs = re.search(r"List<String> VERBS = List\.of\((.*?)\); ", _one_line)
    verbs = [VERB_NAMES.get(one, one)
             for one in re.findall(r"[A-Z_]+", _verbs.group(1) if _verbs else "")]
    does = {beast: VERB_NAMES.get(verb, verb) for beast, verb in
            re.findall(r'made\.put\("([a-z_]+)", (\w+)\);', _one_line)}

    if len(verbs) < 3:
        fail(f"only {len(verbs)} verb(s) could be read out of the rules, so "
             f"everything below is checking an empty list and passing")
    # One each, and none spare. Both directions, because a verb given to two
    # beasts and a verb given to none are two different mistakes and this is
    # the only place that can tell them apart from outside.
    if len(does) != len(beasts):
        fail(f"{len(does)} beast(s) are given something to do and there are "
             f"{len(beasts)}, so one of them fights like whichever family goal "
             f"set it happens to have")
    if len(set(does.values())) != len(does):
        fail("two beasts do the same thing, so two of them are one fight "
             "wearing two skins")
    for verb in verbs:
        if verb not in does.values():
            fail(f"{verb} is a thing nothing does, so it is a mechanic that "
                 f"was written, given a branch to happen in, and will never "
                 f"once be performed")

    # Every rule the file declares is added up.
    _sum = squashed(body_of(BEHAVIOURS, "public static List<String> faults()"))
    _rules = set(re.findall(r"public static List<String> (faultsIn\w+)\(",
                            BEHAVIOURS))
    _added = set(re.findall(r"bad\.addAll\((faultsIn\w+)\([^;]*\)\);", _sum))
    if len(_rules) < 5:
        fail(f"only {len(_rules)} behaviour rule(s) are declared at all, so the "
             f"check below is checking nothing")
    for _rule in sorted(_rules - _added):
        fail(f"{_rule} is written, compiled and never added up by the "
             f"behaviours' faults(), so it answers the harness's controls and "
             f"is never once asked about the roster this pack ships")

    # The one place it happens.
    if not ACTS:
        fail("nothing performs any of it, so every verb in the rules is a word "
             "in a table and no beast does anything at all")
    else:
        _perform = squashed(body_of(ACTS, "public static boolean perform("))
        CONSTANT_OF = {word: name for name, word in VERB_NAMES.items()}
        for verb in verbs:
            wanted = f"case BehaviourRules.{CONSTANT_OF.get(verb, verb.upper())} ->"
            if wanted not in _perform:
                fail(f"nothing performs {verb}, so every beast that does it is "
                     f"priced, placed, drawn and inert")
            elif _perform.count(wanted) != 1:
                fail(f"{verb} has {_perform.count(wanted)} branches to happen "
                     f"in, so which one happens is whichever the compiler put "
                     f"first")
        # And no beast named in it, ever. The day one is, the module has begun
        # growing a branch per creature and the rest of the roster follows.
        for beast in beasts:
            if f'"{beast["id"]}"' in ACTS:
                fail(f"{beast['id']} is named in the one place a mechanic "
                     f"happens, so it has started growing a branch per beast "
                     f"rather than per verb — which is nine files by the time "
                     f"anybody notices")
        # The magnitudes are asked for rather than typed.
        for named, why in (("BehaviourRules.strengthOf(row, all)",
                            "how hard a mechanic lands is typed here rather "
                            "than worked out from the beast"),
                           ("BehaviourRules.reachOf(row)",
                            "how far a mechanic reaches is typed here rather "
                            "than worked out from how big the beast is")):
            if named not in _perform:
                fail(why)

    # The clock is the rules' clock, and a thing that happens on death cannot
    # happen on a clock at all.
    _step = squashed(body_of(BEAST_ENTITY, "public void aiStep()"))
    if "com.elysium.bestiary.rules.BehaviourRules.waitsOf(beast(), " \
            "BeastRules.BEASTS);" not in _step:
        fail("how often a beast does what it does is not the number the rules "
             "worked out, so the mechanic is a second set of dials beside the "
             "first and the two will disagree the day anybody edits one")
    if "com.elysium.bestiary.entity.act.BeastActs.perform(this, getTarget())" \
            not in _step:
        fail("nothing asks a beast to do what it does while it is alive, so "
             "every mechanic in the roster is a branch nothing reaches")
    _die = squashed(body_of(BEAST_ENTITY, "public void die("))
    if "BeastActs.perform(this, getTarget());" not in _die:
        fail("nothing asks a beast to do what it does when it dies, so the one "
             "verb that can only happen then never happens")

    # A piece is half a beast and cannot itself come apart. Either half of
    # that missing is a bug you meet as a server that stops.
    _piece = squashed(body_of(BEAST_ENTITY, "public void becomeAPiece()"))
    if "beast().health() / 2.0D" not in _piece:
        fail("a piece of a beast has as much health as the beast it came off, "
             "so one mob is worth two and the ladder is out by a factor")
    if "this.xpReward = 0;" not in _piece:
        fail("a piece of a beast is worth experience, so the thing that comes "
             "apart is the best thing in the pack to farm")
    if "beast.piece()" not in squashed(ACTS):
        fail("nothing stops a piece coming apart into more pieces, which is a "
             "room that fills until the server stops answering")
    if "beast.called() >= MOST_CALLED" not in squashed(ACTS):
        fail("nothing caps how many of its family a beast may call up, which "
             "is the same room and the same server")

    # And what a player is told about it.
    for beast in beasts:
        if f"bestiary.{NS}.mechanic.{beast['id']}" not in lang:
            fail(f"{beast['id']}'s mechanic has no name, so the almanac prints "
                 f"a translation key at a player")
    for verb in verbs:
        if f"bestiary.{NS}.does.{verb}" not in lang:
            fail(f"{verb} has no description, so a player meets it and is told "
                 f"nothing about what happened")

    print(f"a mechanic apiece  : {len(verbs)} verb(s) over {len(does)} beast(s), "
          f"one each and none spare, each with exactly one branch to happen in, "
          f"no beast named in it, and every magnitude asked of the rules rather "
          f"than typed")


# ---------------------------------------------------------------------------
# The two things an ability puts in the world
# ---------------------------------------------------------------------------
#
# Three of the thirty abilities cannot happen without an entity existing: two
# throw a bolt and one calls up a shade. Both were registered, wired into the
# library and spawned by those three abilities for a while with **nothing
# drawing either of them** — which is not a quiet failure. An entity type with
# no renderer takes the client down the first time one appears, and the appeal
# of that bug is that nobody on the team ever presses the button.
#
# So this section is mostly about the far end: the entity exists, the renderer
# is registered, the layer is registered, the texture is on disk, and the
# numbers the entity carries are the rules' numbers rather than its own.

print("-" * 70)

SUMMON = strip_comments(read(SRC, "rules/SummonRules.java"), keep_strings=True)
BOLT_ENTITY = strip_comments(read(SRC, "entity/RelicBoltEntity.java"),
                             keep_strings=True)
SHADE_ENTITY = strip_comments(read(SRC, "entity/ShadeEntity.java"),
                              keep_strings=True)
SUMMON_REG = strip_comments(read(SRC, "registry/SummonEntities.java"),
                            keep_strings=True)
CLIENT = strip_comments(read(SRC, "client/BestiaryClient.java"), keep_strings=True)
if not SUMMON:
    fail("nothing says what an ability puts in the world or what it is worth, "
         "so three of the thirty abilities could call up anything at all")
else:
    _summon = squashed(SUMMON)
    summons = re.findall(
        r'new Summon\((\w+), \w+\.(\w+), ([\d_]+), ([\d.]+)D, ([\w.]+),'
        r' ([\d.]+)F, ([\d.]+)F, (true|false)\)', _summon)
    if len(summons) < 2:
        fail(f"only {len(summons)} summon(s) could be read out of the rules, so "
             f"every check below is walking a short list and passing")

    ids = []
    for row in summons:
        ids.append("bolt" if row[0] == "BOLT" else row[0].lower())

    # Each one exists all the way out to something a player can see.
    for name in ids:
        where = f"assets/{NS}/textures/entity/{name}.png"
        if not os.path.isfile(os.path.join(RES, where)):
            fail(f"{name} has no texture, so the first one a player summons is "
                 f"a black-and-magenta cube")
        if f"entity.{NS}.{name}" not in lang:
            fail(f"{name} has no name, so it reads as its own translation key "
                 f"wherever the game shows it")

    # And the renderer. This is the check the section was written for.
    if not CLIENT:
        fail("there is no client class, so nothing in this module is drawn")
    else:
        _client = squashed(CLIENT)
        for named, why in (
                ("SummonEntities.SHADE.get(), ShadeRenderer::new",
                 "the shade has no renderer registered, so the first one a "
                 "player summons takes the client down with it"),
                ("SummonEntities.BOLT.get(), RelicBoltRenderer::new",
                 "the bolt has no renderer registered, so the first thrown "
                 "ability takes the client down with it"),
                ("event.registerLayerDefinition(ShadeRenderer.LAYER",
                 "the shade's model layer is never registered, so baking it "
                 "throws and the renderer that was registered cannot be built")):
            if named not in _client:
                fail(why)
        if not os.path.isfile(os.path.join(SRC, "client/ShadeRenderer.java")):
            fail("there is no shade renderer at all")
        if not os.path.isfile(os.path.join(SRC, "client/RelicBoltRenderer.java")):
            fail("there is no bolt renderer at all")
        if not os.path.isfile(os.path.join(SRC, "client/ShadeModel.java")):
            fail("there is no shade model, so there is nothing for the renderer "
                 "to draw")

    # The library gets what it asked for, and says so loudly if it does not.
    if not SUMMON_REG:
        fail("nothing registers the two entity types the library needs")
    else:
        _reg = squashed(SUMMON_REG)
        if "ElysiumAbilityEntities.bolt(BOLT::get);" not in _reg:
            fail("the library is never handed a bolt, so both abilities of that "
                 "shape pay their cost, play their sound and do nothing")
        if "ElysiumAbilityEntities.summon(SHADE::get);" not in _reg:
            fail("the library is never handed a summon, so the one ability of "
                 "that shape pays its cost and does nothing")
        if "ElysiumAbilityEntities.dress(" not in _reg:
            fail("nothing tells a freshly thrown bolt which ability threw it, so "
                 "a hunter's mark and a stormcall hit for the same and what the "
                 "stormcall was priced for buys nothing")
        if "thrown.thrownBy(ability)" not in _reg:
            fail("the way a bolt is dressed never reaches the bolt, so the "
                 "library hands over the ability and nothing reads it")
        if "ElysiumAbilityEntities.missing().isEmpty()" not in _reg:
            fail("nothing checks that the library ended up with everything it "
                 "asked for, so a shape left unanswered is a set of abilities "
                 "that quietly do nothing")
        for typed in re.findall(r"\.sized\((\d+\.\d+)F", _reg):
            fail(f"an entity is registered at a size of {typed} typed into the "
                 f"register, which is a second set of dials beside the rules'")
        if "SummonRules.summon(SummonRules.BOLT).wide()" not in _reg:
            fail("the bolt's size is not the rules' own, so the hitbox the "
                 "harness checked is not the hitbox the game builds")

    # Neither entity carries a number of its own.
    if not BOLT_ENTITY:
        fail("there is no bolt, so two abilities throw nothing")
    else:
        _bolt = squashed(BOLT_ENTITY)
        if "SummonRules.summon(SummonRules.BOLT).lasts()" not in _bolt:
            fail("how long a bolt lives is a number typed into the entity, and "
                 "the rules hold that number under the cooldown of every "
                 "ability that throws one — so the two can drift apart and the "
                 "cooldown stops being a limit")
        if "SummonRules.hurtOfBolt(ability)" not in _bolt:
            fail("a bolt does not ask what the ability that threw it was priced "
                 "at, so a hunter's mark and a stormcall hit for the same, and "
                 "what the stormcall was priced for bought nothing")
        for typed in re.findall(r"setBaseDamage\((\d+\.\d+)D\)", _bolt):
            fail(f"a bolt hits for {typed} by a number typed into the entity, "
                 f"which is a second set of dials beside the library's own "
                 f"pricing")
        for typed in re.findall(r"int LASTS = (\d+);", _bolt):
            fail(f"a bolt lives {typed} ticks by a number typed into the entity")

    if not SHADE_ENTITY:
        fail("there is no shade, so the one summoning ability calls up nothing")
    else:
        _shade = squashed(SHADE_ENTITY)
        if "SummonRules.summon(SummonRules.SHADE).lasts()" not in _shade:
            fail("how long a shade stands is a number typed into the entity, "
                 "and the rules hold that under the cooldown of the ability "
                 "that calls one up — so a player could have two at once")
        for asked in ("shade.health()", "shade.damage()", "SummonRules.SHADE_SPEED",
                      "SummonRules.SHADE_NOTICES"):
            if asked not in _shade:
                fail(f"a shade does not ask the rules for {asked}, so the band "
                     f"it was checked against is not the band the game gives it")
        for typed in re.findall(r"Attributes\.\w+, (\d+\.\d+)D\)", _shade):
            fail(f"a shade is given {typed} by a number typed into the entity, "
                 f"which is how the first draft ended up out-hitting every "
                 f"beast in the game with nothing saying so")
        for typed in re.findall(r"int LASTS = (\d+);", _shade):
            fail(f"a shade stands {typed} ticks by a number typed into the "
                 f"entity")
        # And the one thing nobody forgives.
        if "Monster.class" not in _shade:
            fail("a shade does not restrict what it hunts to things that hunt "
                 "you, so it can turn on its summoner — the one bug in this "
                 "kind of ability nobody forgives")

    # Every rule the file declares is added up.
    _sum = squashed(body_of(SUMMON, "public static List<String> faults()"))
    _rules = set(re.findall(r"public static List<String> (faultsIn\w+)\(", SUMMON))
    _added = set(re.findall(r"bad\.addAll\((faultsIn\w+)\([^;]*\)\);", _sum))
    if len(_rules) < 6:
        fail(f"only {len(_rules)} summon rule(s) are declared at all, so the "
             f"check below is checking nothing")
    for _rule in sorted(_rules - _added):
        fail(f"{_rule} is written, compiled and never added up by the summons' "
             f"faults(), so it answers the harness's controls and is never once "
             f"asked about what this module ships")

    print(f"the summons         : {len(ids)} thing(s) the ability system cannot "
          f"do without, each drawn by a renderer that is registered and a "
          f"texture that is on disk, each carrying the rules' numbers rather "
          f"than its own, and neither outlasting the cooldown meant to limit it")


if problems:
    print(f"{len(problems)} problem(s):")
    for problem in problems:
        print(f"  - {problem}")
    sys.exit(1)
print("all bestiary checks passed")
