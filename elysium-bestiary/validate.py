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
  4. THE SHAPES: a body plan per family and kind, dense, measured, listed.
  5. AS BIG AS IT LOOKS. The model measured against the hitbox.
  6. WHAT IT LEAVES, counted out of the written loot tables.
  7. WHAT YOU MEET, counted out of the written spawn rules.
  8. THE WORDS, and the generated tree rebuilt and compared.
  9. THE BEASTS IN THE WORLD, and the seam.
"""
import importlib.util
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

# ---------------------------------------------------------------------------
# 4b. The shapes (docs/POLISH.md P10)
# ---------------------------------------------------------------------------
#
# Every beast wears a body plan from art/bodyplans.py: a hundred-plus boxes in
# a real part tree, generated with its sheet from the same rows. The beasts are
# drawn by the classes under client/shape, which Shapes.java lists by family and
# BeastModel delegates to; the six-box table is gone (28 Sep). What is checked: a class per family, dense enough, measured,
# listed, delegated to, and every beast's sheet the size its shape was packed to.
shape_dir = os.path.join(SRC, "client/shape")
shapes_registry = strip_comments(read(SRC, "client/shape/Shapes.java"), keep_strings=True) \
    if os.path.isfile(os.path.join(shape_dir, "Shapes.java")) else ""
if not shapes_registry:
    fail("there is no client/shape/Shapes.java, so no family has a body plan and every beast "
         "is back to six boxes")
shape_boxes = {}
shape_sheets = {}
shape_dims = {}
_animal_kinds = sorted(set(re.findall(r'new Animal\("\w+", (GRAZER|FORAGER|FOWL|SWIMMER),',
                                       strip_comments(read(SRC, "rules/FaunaRules.java"), keep_strings=True))))
_animal_kinds = [k.lower() for k in _animal_kinds]
for family in sorted({beast["family"] for beast in beasts} | set(_animal_kinds)):
    cls = family.capitalize() + "Shape"
    path = os.path.join(shape_dir, cls + ".java")
    if not os.path.isfile(path):
        fail(f"a {family} has no shape class (client/shape/{cls}.java), so every beast in that "
             f"family has no body plan")
        continue
    src = strip_comments(read(SRC, "client/shape/" + cls + ".java"))
    boxes = src.count(".addBox(")
    shape_boxes[family] = boxes
    # A beast is patterned on the eight; an animal is simpler where the animal is
    # simple (a bird, a fish), but never six boxes again.
    least = 100 if family in {beast["family"] for beast in beasts} else 28
    if boxes < least:
        fail(f"a {family}'s shape is {boxes} box(es); it needs {least} and more, or it is a "
             f"recoloured six-box mob with extra steps")
    # A tree is parts hung from parts: a tenth of the boxes at least are parents.
    if "addOrReplaceChild(" not in src or src.count("PartDefinition ") < max(4, boxes // 10):
        fail(f"a {family}'s shape is a flat list of boxes rather than a part tree, so nothing on "
             f"it turns with the part it sits on")
    for constant in ("WIDE", "TALL", "DEEP", "SHEET"):
        if not re.search(r"public static final (?:float|int) %s = " % constant, src):
            fail(f"a {family}'s shape does not publish its {constant}, so the renderer has nothing "
                 f"to squash onto the hitbox")
    sheet_match = re.search(r"public static final int SHEET = (\d+);", src)
    shape_sheets[family] = int(sheet_match.group(1)) if sheet_match else 0
    _dims = re.findall(r"public static final float (WIDE|TALL|DEEP) = ([\d.]+)F;", src)
    if len(_dims) == 3:
        _d = dict(_dims)
        shape_dims[family] = (float(_d["WIDE"]), float(_d["TALL"]), float(_d["DEEP"]))
    # Three switches in the registry: the layer, the model and the bounds. A
    # family listed in two of them is drawn and then squashed onto nothing.
    if shapes_registry and shapes_registry.count(f'case "{family}"') < 3:
        fail(f"Shapes.java does not list the {family} in every one of its three switches, "
             f"so its class is written and never drawn, or drawn and never measured")
_shade_src = strip_comments(read(SRC, "client/ShadeModel.java"))
if "GENERATED by tools/gen_bestiary.py" not in read(SRC, "client/ShadeModel.java"):
    fail("ShadeModel is written by hand again, so its sheet and its boxes can disagree")
if _shade_src.count(".addBox(") < 60:
    fail(f"ShadeModel is {_shade_src.count('.addBox(')} box(es); a shade patterned on the eight is sixty and more")
if "boolean hunting = entity != null && entity.getTarget() != null;" not in _shade_src:
    fail("the shade no longer reaches when it hunts, which was the one tell that it was")
for _who, _key in (("BeastModel", "family"), ("FaunaModel", "kind"), ("KinModel", "known(form)"),
                   ("CourtModel", "known(form)"), ("DenizenModel", "known(form)"), ("WardModel", "known(form)"),
                   ("MythModel", "known(form)"), ("CompanionModel", "FORM")):
    _model_src = squashed(strip_comments(read(SRC, "client/%s.java" % _who)))
    for needed, why in (("Shapes.model(%s, root)" % _key, "the model of its %s's shape" % _key),
                        ("this.inner.setupAnim(", "its shape's walk"),
                        ("Shapes.bounds(%s)" % _key, "its shape's measured bounds")):
        if needed not in _model_src:
            fail(f"{_who} no longer delegates to {why}, so the shapes are generated and the "
                 f"creatures are drawn as something else")
_entity_textures = os.path.join(ROOT, "elysium-bestiary/src/main/resources/assets/elysiumbestiary/textures/entity")
_animals = [{"id": _id, "family": _kind.lower()} for _id, _kind in re.findall(
    r'new Animal\("(\w+)", (GRAZER|FORAGER|FOWL|SWIMMER),',
    strip_comments(read(SRC, "rules/FaunaRules.java"), keep_strings=True))]
for beast in beasts + _animals:
    sheet = shape_sheets.get(beast["family"])
    png = os.path.join(_entity_textures, beast["id"] + ".png")
    if not sheet or not os.path.isfile(png):
        continue
    try:
        from PIL import Image as _Image
        with _Image.open(png) as _img:
            if _img.size != (sheet, sheet):
                fail(f"{beast['id']}'s sheet is {_img.size[0]}x{_img.size[1]} and its shape was packed "
                     f"to {sheet}: the model reads pixels that are not there")
    except ImportError:
        break
_sheet_sizes = set(shape_sheets.values()) - {0}
if _sheet_sizes and os.path.isdir(_entity_textures):
    try:
        from PIL import Image as _Image
        # The bolt is a projectile sprite, not a creature; everything else wears a plan.
        for _name in sorted(os.listdir(_entity_textures)):
            if not _name.endswith(".png") or _name == "bolt.png":
                continue
            with _Image.open(os.path.join(_entity_textures, _name)) as _img:
                if _img.size[0] != _img.size[1] or _img.size[0] not in _sheet_sizes:
                    fail(f"{_name} is {_img.size[0]}x{_img.size[1]}, which no shape was packed to, so it is a "
                         f"sheet nothing here wears")
    except ImportError:
        pass
# As big as it looks, worked out on the plans' own measured bounds: the
# renderer squashes each shape onto its row's hitbox, so every beast and
# animal must come out exactly as tall and as wide as the box you swing at.
_rows = [dict(beast, what="beast") for beast in beasts]
for _id, _kind, _w, _h in re.findall(
        r'new Animal\("(\w+)", (GRAZER|FORAGER|FOWL|SWIMMER), [\d.]+D, [\d.]+D, ([\d.]+)F, ([\d.]+)F,',
        strip_comments(read(SRC, "rules/FaunaRules.java"), keep_strings=True)):
    _rows.append({"id": _id, "family": _kind.lower(), "width": float(_w), "height": float(_h), "what": "animal"})
if shape_dims and _rows:
    PIXELS = 16.0
    for row in _rows:
        if row["family"] not in shape_dims:
            continue
        wide, tall, deep = shape_dims[row["family"]]
        if min(wide, tall, deep) <= 0.0:
            fail(f"a {row['family']} has no extent in one direction, so it is drawn as a sheet of paper")
            continue
        across = row["width"] * PIXELS / max(wide, deep)
        up = row["height"] * PIXELS / tall
        drawn = (wide * across, tall * up, deep * across)
        box = (row["width"] * PIXELS, row["height"] * PIXELS, row["width"] * PIXELS)
        for axis, side in enumerate(("wide", "tall", "deep")):
            if drawn[axis] > box[axis] + 1e-6:
                fail(f"{row['id']} is drawn {drawn[axis] / PIXELS:.2f} blocks {side} inside a hitbox "
                     f"{box[axis] / PIXELS:.2f} blocks {side}, so part of it is outside the thing you are swinging at")
        if abs(drawn[1] - box[1]) > 1e-6:
            fail(f"{row['id']} is drawn {drawn[1] / PIXELS:.2f} blocks tall in a hitbox {box[1] / PIXELS:.2f} "
                 f"blocks tall, so its head is either above the box or floating inside it")
        if abs(max(drawn[0], drawn[2]) - box[0]) > 1e-6:
            fail(f"{row['id']} does not touch the sides of its own hitbox, so there is a band around it "
                 f"that can be hit and cannot be seen")
    print(f"as big as it looks : {len(_rows)} beasts and animals, every one of them exactly as tall and "
          f"as wide on screen as the box you swing at, on its plan's measured bounds")

if shape_boxes:
    print(f"the shapes         : {len(shape_boxes)} body plan(s) of "
          f"{', '.join(str(shape_boxes[f]) for f in sorted(shape_boxes))} boxes, each a part tree "
          f"with its bounds published, listed by family or kind and worn by every beast and animal")

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
            # The generated Java too: the shape classes and the shade's model
            # are written by the generator, and a WIDE typed in by hand is a
            # size nothing measured.
            compared = [(os.path.join(RES, kind, NS), os.path.join(tmp, "src/main/resources", kind, NS))
                        for kind in ("assets", "data")]
            compared.append((os.path.join(SRC, "client"), os.path.join(tmp, "src/main/java/com/elysium/bestiary/client")))
            # And the game's own tags this module writes into: the mineable
            # tags come from HarvestRules, and one edited by hand is a machine
            # that drops nothing again after the next regen.
            compared.append((os.path.join(RES, "data", "minecraft"),
                             os.path.join(tmp, "src/main/resources/data/minecraft")))
            for here, there in compared:
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
                print("the words          : every sprite, model, shape class, loot table and "
                      "spawn rule rebuilt and identical")

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

    # The kinds' plans: a shape class per kind, listed and measured in 4b, and
    # every animal sized onto its plan's measured bounds there as well.
    fauna_shapes = {kind: shape_dims[kind] for kind in sorted({animal["kind"] for animal in animals})
                    if kind in shape_dims}
    for kind in sorted({animal["kind"] for animal in animals}):
        if kind not in fauna_shapes:
            fail(f"nothing is shaped like a {kind}, so every animal of that kind "
                 f"is a crash the first time one is drawn")

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
    fail("nothing says what any beast does, so meeting a prowlframe and meeting a "
         "shearjaw are the same fight with a different model on it — which is "
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


# ---------------------------------------------------------------------------
# Eight creatures armour does not answer
# ---------------------------------------------------------------------------
#
# The nine beasts are a ladder and the answer to every one of them is the same
# answer, more of it. A myth is the other kind: a basilisk's stare is not
# survived by wearing more, a chupacabra taking the top off your health bar is
# not healed back by eating more. Each has exactly one of these, and the two
# things this section is for are that the thing which beats it is something the
# pack really makes, and that nothing about it is a number typed twice.

print("-" * 70)

MYTH = strip_comments(read(SRC, "rules/MythRules.java"), keep_strings=True)
MYTH_ENTITY = strip_comments(read(SRC, "entity/MythEntity.java"), keep_strings=True)
BANES = strip_comments(read(SRC, "entity/act/MythBanes.java"), keep_strings=True)
MYTH_REG = strip_comments(read(SRC, "registry/MythEntities.java"), keep_strings=True)
if not MYTH:
    fail("nothing says what a myth is or what beats it, so eight creatures "
         "could be dangerous in ways nothing in the pack answers")
else:
    _myth = squashed(MYTH)
    myths = re.findall(
        r'new Myth\("([a-z_]+)", (\w+), "([\w:]+)", (\d+), ([\d.]+)D, ([\d.]+)D,'
        r' ([\d.]+)D, (\d+), (true|false), ([\w.]+)\)', _myth)
    banes = re.findall(r'public static final String ([A-Z]+) = "([a-z_]+)";', MYTH)
    if len(myths) < 2:
        fail(f"only {len(myths)} myth(s) could be read out of the rules, so "
             f"every check below is walking a short list and passing")

    # Everything the rules name exists, all the way out to the world.
    for row in myths:
        name = row[0]
        for kind, where in (
                ("texture", f"assets/{NS}/textures/entity/{name}.png"),
                ("spawn egg model",
                 f"assets/{NS}/models/item/{name}_spawn_egg.json"),
                ("loot table", f"data/{NS}/loot_table/entities/{name}.json"),
                ("spawn rule",
                 f"data/{NS}/neoforge/biome_modifier/add_{name}_spawns.json")):
            if not os.path.isfile(os.path.join(RES, where)):
                fail(f"{name} has no {kind}, so it is a creature the rules "
                     f"describe and the world never shows")
        for key, why in (
                (f"entity.{NS}.{name}",
                 "reads as its own translation key wherever the game names it"),
                (f"item.{NS}.{name}_spawn_egg",
                 "has an egg with no name on it"),
                (f"bestiary.{NS}.bane.{name}",
                 "has no sentence saying what is dangerous about it, which is a "
                 "creature a player learns by dying to it twice")):
            if key not in lang:
                fail(f"{name} {why}")

    # The thing that beats it has to be a thing. This is the check the section
    # exists for: a bane whose answer the pack does not make is a wall, and a
    # wall and a hard fight feel the same from inside one.
    if THE_PACK_HAS or THE_GAME_MAKES:
        known = set(THE_PACK_HAS) | set(THE_GAME_MAKES)
        for row in myths:
            name, answer = row[0], row[2]
            if answer.startswith("minecraft:") and answer not in known:
                fail(f"{name} is answered by {answer} and the game does not "
                     f"make that, so the one thing that beats it does not "
                     f"exist — which is a wall rather than a fight")
            if answer.startswith("elysium:") and answer not in known:
                fail(f"{name} is answered by {answer} and nothing in this pack "
                     f"makes it, so the one thing that beats it does not exist")

    # Nothing registered by hand, and everything counted.
    if not MYTH_REG:
        fail("nothing registers the myths, so all eight are rows in a table")
    else:
        _reg = squashed(MYTH_REG)
        # The register() body in particular. This file walks the myths three
        # times — to register, to give them attributes and to place their
        # spawns — so a check that only asks whether the file mentions the walk
        # anywhere is a check two of the three can answer for the third.
        _register = squashed(body_of(MYTH_REG, "register() {"))
        if "for (MythRules.Myth myth : MythRules.MYTHS) {" not in _register:
            fail("the myths are named by hand rather than walked from the "
                 "rules, so a ninth is a row nothing registers")
        if "made.size() != MythRules.MYTHS.size()" not in _reg:
            fail("nothing counts the myths that were registered, so a roster "
                 "that half registers is a roster that half exists")
        for typed in re.findall(r"\.sized\((\d+\.\d+)F, (\d+\.\d+)F\)", _reg):
            fail(f"a myth is registered at {typed[0]} by {typed[1]} typed into "
                 f"the register, and a hitbox that disagrees with the model is "
                 f"a creature a player swings through")
    MOD = squashed(strip_comments(read(SRC, "ElysiumBestiary.java")))
    if "MythEntities.ALL.size()" not in MOD:
        fail("the myths' register is never touched while the mod is being "
             "built, so whether eight creatures exist depends on when FML "
             "happens to load the class — and the symptom is that they simply "
             "are not in the game, with nothing in the log saying so")

    # Drawn, which is the thing this module got wrong once already.
    _client = squashed(strip_comments(read(SRC, "client/BestiaryClient.java")))
    if "new MythRenderer(context, myth.form())" not in _client:
        fail("the myths have no renderer registered, so the first one a player "
             "meets takes the client down with it")
    if "MythModel.LAYERS.get(form)" not in _client:
        fail("the myths' model layers are never registered, so baking one "
             "throws and the renderer that was registered cannot be built")
    for named in ("client/MythModel.java", "client/MythRenderer.java"):
        if not os.path.isfile(os.path.join(SRC, named)):
            fail(f"there is no {named}, so nothing draws the myths")

    # One branch per bane, and no creature named in any of them.
    if not BANES:
        fail("nothing makes a myth's way past armour happen, so all eight are "
             "beasts with a worse stat line")
    else:
        _banes = squashed(BANES)
        for shorthand, bane in banes:
            if f"MythRules.{shorthand}" not in _banes:
                fail(f"nothing happens for the {bane} bane, so whatever has it "
                     f"is a creature whose danger is a sentence in a language "
                     f"file")
        for row in myths:
            if re.search(r'"%s"' % row[0], _banes):
                fail(f"{row[0]} is named in the banes, so the module has "
                     f"started growing a branch per creature and the whole "
                     f"roster will follow it there")
        _happen = squashed(body_of(BANES, "public static void happen("))
        _contact = squashed(body_of(BANES, "public static void onContact("))
        for where, body in (("on its own clock", _happen),
                            ("on contact", _contact)):
            if "switch (myth.bane())" not in body:
                fail(f"what happens {where} is not chosen by which bane it is, "
                     f"so whatever chooses it is something a validator cannot "
                     f"read — and the file holds two switches, so asking "
                     f"whether the file mentions one anywhere is a question "
                     f"the other answers")

    # And the entity carries no number of its own.
    if not MYTH_ENTITY:
        fail("there is no myth entity, so nothing the rules describe is alive")
    else:
        _entity = squashed(MYTH_ENTITY)
        for asked in ("myth.health()", "myth.damage()", "myth.speed()"):
            if asked not in _entity:
                fail(f"a myth does not ask the rules for {asked}, so the band "
                     f"the harness held it to is not the band the game gives it")
        if "MythRules.myth(name)" not in _entity:
            fail("a myth does not find its own row by the name it was "
                 "registered under, so whatever it is storing instead can be "
                 "stored wrong")
        # Both arms, because the entity asks twice — once for what a runner
        # does and once for what a stander does — and one of the two answers
        # for the other if only the bare name is looked for.
        for arm, why in (("if (myth().flees()) {",
                          "what something that runs does is not decided by the "
                          "row's own flees, so the rule that holds a runner "
                          "under a stander is about a field nothing reads"),
                         ("if (!myth().flees()) {",
                          "what something that stands does is not decided by "
                          "the row's own flees, so every myth would either "
                          "fight or run and the field would decide nothing")):
            if arm not in _entity:
                fail(why)
        for typed in re.findall(r"Attributes\.\w+, (\d+\.\d+)D\)", _entity):
            if typed not in ("24.0",):
                fail(f"a myth is given {typed} by a number typed into the "
                     f"entity, which is a second set of dials beside the rules'")

    # Every rule the file declares is added up.
    _sum = squashed(body_of(MYTH, "public static List<String> faults()"))
    _rules = set(re.findall(r"public static List<String> (faultsIn\w+)\(", MYTH))
    _added = set(re.findall(r"bad\.addAll\((faultsIn\w+)\([^;]*\)\);", _sum))
    if len(_rules) < 8:
        fail(f"only {len(_rules)} myth rule(s) are declared at all, so the "
             f"check below is checking nothing")
    for _rule in sorted(_rules - _added):
        fail(f"{_rule} is written, compiled and never added up by the myths' "
             f"faults(), so it answers the harness's controls and is never once "
             f"asked about what this module ships")

    print(f"the myths           : {len(myths)} creature(s) over {len(banes)} "
          f"way(s) past armour, one each and none spare, every one of them "
          f"answered by something the pack really makes, drawn on a shape this "
          f"module already had, and not one carrying a number of its own")


# ---------------------------------------------------------------------------
# Eleven creatures that do a block's job
# ---------------------------------------------------------------------------
#
# A glare tells you where the dark is, a crab lets you reach further, a tuff
# golem holds a thing. None of them is a fight; every one of them is a block's
# job done by something alive — which is exactly the danger. A companion that
# does a block's job better than the block ends the block, quietly, one category
# at a time, and by the time anybody notices nobody can say when.
#
# So the two things this section is for are that the block each one competes
# with is a block the pack really makes, and that nothing about a companion is a
# number typed twice.

print("-" * 70)

COMPANION = strip_comments(read(SRC, "rules/CompanionRules.java"), keep_strings=True)
COMPANION_ENTITY = strip_comments(read(SRC, "entity/CompanionEntity.java"),
                                  keep_strings=True)
GIFTS = strip_comments(read(SRC, "entity/act/CompanionGifts.java"), keep_strings=True)
COMPANION_REG = strip_comments(read(SRC, "registry/CompanionEntities.java"),
                               keep_strings=True)
if not COMPANION:
    fail("nothing says what a companion does or what keeping one costs, so "
         "eleven creatures could quietly do every block's job for free")
else:
    _companion = squashed(COMPANION)
    companions = re.findall(
        r'new Companion\("([a-z_]+)", (\w+), "([\w:]+)", (true|false), (\d+),'
        r' ([\d.]+)D, (\d+), (\d+)\)', _companion)
    gifts = re.findall(r'public static final String ([A-Z_]+) = "([a-z_]+)";',
                       COMPANION)
    if len(companions) < 2:
        fail(f"only {len(companions)} companion(s) could be read out of the "
             f"rules, so every check below is walking a short list and passing")

    for row in companions:
        name = row[0]
        for kind, where in (
                ("texture", f"assets/{NS}/textures/entity/{name}.png"),
                ("spawn egg model",
                 f"assets/{NS}/models/item/{name}_spawn_egg.json"),
                ("loot table", f"data/{NS}/loot_table/entities/{name}.json"),
                ("spawn rule",
                 f"data/{NS}/neoforge/biome_modifier/add_{name}_spawns.json")):
            if not os.path.isfile(os.path.join(RES, where)):
                fail(f"{name} has no {kind}, so it is a creature the rules "
                     f"describe and the world never shows")
        for key, why in (
                (f"entity.{NS}.{name}",
                 "reads as its own translation key wherever the game names it"),
                (f"bestiary.{NS}.gift.{name}",
                 "has no sentence saying what it does, which is a creature a "
                 "player keeps by accident and never uses")):
            if key not in lang:
                fail(f"{name} {why}")

    # The block it competes with has to be a block. This is the check the
    # section exists for: a companion measured against something that does not
    # exist is a companion measured against nothing.
    if THE_PACK_HAS or THE_GAME_MAKES:
        known = set(THE_PACK_HAS) | set(THE_GAME_MAKES)
        for row in companions:
            name, rivals = row[0], row[2]
            if rivals not in known:
                fail(f"{name} is held under {rivals} and nothing makes one of "
                     f"those, so what it is measured against does not exist and "
                     f"it is free to replace whatever it likes")

    if not COMPANION_REG:
        fail("nothing registers the companions, so all eleven are rows")
    else:
        _reg = squashed(COMPANION_REG)
        _register = squashed(body_of(COMPANION_REG, "register() {"))
        if "for (CompanionRules.Companion one : CompanionRules.COMPANIONS) {" \
                not in _register:
            fail("the companions are named by hand rather than walked from the "
                 "rules, so a twelfth is a row nothing registers")
        if "made.size() != CompanionRules.COMPANIONS.size()" not in _reg:
            fail("nothing counts the companions that were registered, so a "
                 "roster that half registers is a roster that half exists")
        if "MobCategory.CREATURE" not in _reg:
            fail("the companions are registered in the monster bucket, and the "
                 "game counts the two against separate caps — so eleven "
                 "harmless things would crowd out everything a player is meant "
                 "to be afraid of")
    MODCLASS = squashed(strip_comments(read(SRC, "ElysiumBestiary.java")))
    if "CompanionEntities.ALL.size()" not in MODCLASS:
        fail("the companions' register is never touched while the mod is being "
             "built, so whether eleven creatures exist depends on when FML "
             "happens to load the class")

    _client = squashed(strip_comments(read(SRC, "client/BestiaryClient.java")))
    if "CompanionRenderer::new" not in _client:
        fail("the companions have no renderer registered, so the first one a "
             "player meets takes the client down with it")
    if "event.registerLayerDefinition(CompanionModel.LAYER," not in _client:
        fail("the companions' model layer is never registered, so baking it "
             "throws and the renderer that was registered cannot be built")

    # One branch per gift, and no creature named in any of them.
    if not GIFTS:
        fail("nothing makes a companion's one thing happen, so all eleven are "
             "animals that wander")
    else:
        _gifts = squashed(GIFTS)
        for shorthand, gift in gifts:
            if f"CompanionRules.{shorthand}" not in _gifts:
                fail(f"nothing happens for {gift}, so whatever does it is a "
                     f"creature whose point is a sentence in a language file")
        for row in companions:
            if re.search(r'"%s"' % row[0], _gifts):
                fail(f"{row[0]} is named in the gifts, so the module has "
                     f"started growing a branch per creature and the whole "
                     f"roster will follow it there")
        if "switch (row.gift())" not in _gifts:
            fail("what a companion does is not chosen by which gift it is, so "
                 "whatever chooses it is something a validator cannot read")
        # And the one number no branch is allowed to set for itself.
        _lasting = squashed(body_of(GIFTS, "private static void lasting("))
        if "LINGERS" not in _lasting:
            fail("how long a companion's gift lasts is not decided in one "
                 "place, so a branch could hand out something permanent and "
                 "the rule that forbids it would be about a field nothing "
                 "reads")
        for typed in re.findall(r"new MobEffectInstance\(\s*\w[\w.]*,\s*(\d+),",
                                _gifts):
            fail(f"a gift lasts {typed} ticks by a number typed into the "
                 f"branch, which is a second set of dials beside the one place "
                 f"that is supposed to decide it")

    if not COMPANION_ENTITY:
        fail("there is no companion entity, so nothing the rules describe is "
             "alive")
    else:
        _entity = squashed(COMPANION_ENTITY)
        if "CompanionRules.companion(name)" not in _entity:
            fail("a companion does not find its own row by the name it was "
                 "registered under, so whatever it is storing instead can be "
                 "stored wrong")
        # Two places read whether a companion has to be earned, and each gets
        # its own guard: one check covering both is one check a control can
        # answer with the occurrence it did not break.
        _willing = squashed(body_of(COMPANION_ENTITY, "public boolean willing()"))
        if "companion().tamed()" not in _willing:
            fail("whether a companion will do its one thing at all is not the "
                 "row's own, so the rule that stops one working before it is "
                 "earned is about a field nothing reads")
        _feed = squashed(body_of(COMPANION_ENTITY, "public net.minecraft.world.InteractionResult mobInteract("))
        if "companion().tamed()" not in _feed:
            fail("what it takes to earn a companion is not the row's own, so a "
                 "creature the rules say must be tamed can be owned by handing "
                 "it anything, or nothing")
        # And the same again for reach. Four calls in give() are handed a
        # distance; every one of them has to be the row's, because a single
        # `row.reach() in the file` check is answered by the three a control
        # left alone.
        _give = squashed(body_of(GIFTS, "public static void give("))
        if not _give:
            fail("nothing in the gifts is handed a companion's row, so how far "
                 "one reaches is decided somewhere a validator cannot read")
        for _call, _arg in re.findall(
                r"\b(nearby|monsters)\(self, ((?:[^()]|\([^()]*\))*)\)", _give):
            if _arg != "row.reach()":
                fail(f"a companion's gift gathers who it is for with "
                     f"{_call}(self, {_arg}), which is a distance that is not "
                     f"the row's own — so the band the harness held it to is "
                     f"not the band the game keeps")

    # Every rule the file declares is added up.
    _sum = squashed(body_of(COMPANION, "public static List<String> faults()"))
    _rules = set(re.findall(r"public static List<String> (faultsIn\w+)\(",
                            COMPANION))
    _added = set(re.findall(r"bad\.addAll\((faultsIn\w+)\([^;]*\)\);", _sum))
    if len(_rules) < 7:
        fail(f"only {len(_rules)} companion rule(s) are declared at all, so the "
             f"check below is checking nothing")
    for _rule in sorted(_rules - _added):
        fail(f"{_rule} is written, compiled and never added up by the "
             f"companions' faults(), so it answers the harness's controls and "
             f"is never once asked about what this module ships")

    print(f"the companions      : {len(companions)} creature(s) over "
          f"{len(gifts)} job(s) a block would otherwise do, one each and none "
          f"spare, every one of them held under a block the pack really makes, "
          f"and not one doing its job for free")


# ---------------------------------------------------------------------------
# The kindred
# ---------------------------------------------------------------------------
#
# The roster that grows by variant, and the one section here whose whole job is
# to catch padding. Every check below is about one of the two halves of the
# claim -- a variant does something its kin do not, and lives somewhere they do
# not -- or about the place those halves get quietly dropped: a generator that
# writes every spawn rule into the overworld at large, a branch that names a
# creature, a kindred drawn with two shapes.

print("-" * 70)

KINDRED_SRC = strip_comments(read(SRC, "rules/KindredRules.java"), keep_strings=True)
KIN_ENTITY = strip_comments(read(SRC, "entity/KinEntity.java"), keep_strings=True)
TRAITS_SRC = strip_comments(read(SRC, "entity/act/KinTraits.java"), keep_strings=True)
KIN_REG = strip_comments(read(SRC, "registry/KinEntities.java"), keep_strings=True)

if not KINDRED_SRC:
    fail("nothing says what a kindred is, so twenty-seven creatures could be "
         "four creatures in twenty-seven costumes and nothing would report it")
else:
    _kindred = squashed(KINDRED_SRC)
    kindred = re.findall(
        r'new Kin\("([a-z_]+)", "([a-z_]+)", (true|false), (\w+), (\w+), (\d+),'
        r' ([\d.]+)D, ([\d.]+)D, ([\d.]+)D, (\d+), (true|false)\)', _kindred)
    traits = re.findall(r'public static final String ([A-Z_]+) = "([a-z]+)";',
                        KINDRED_SRC)
    homes = dict(re.findall(
        r'public static final String (\w+) = "(#[\w:]+)";', _kindred))
    if len(kindred) < 4:
        fail(f"only {len(kindred)} creature(s) could be read out of the kindred "
             f"rules, so every check below is walking a short list and passing")

    by_kin = {}
    for row in kindred:
        by_kin.setdefault(row[1], []).append(row)

    for row in kindred:
        name = row[0]
        for kind, where in (
                ("texture", f"assets/{NS}/textures/entity/{name}.png"),
                ("spawn egg model",
                 f"assets/{NS}/models/item/{name}_spawn_egg.json"),
                ("loot table", f"data/{NS}/loot_table/entities/{name}.json"),
                ("spawn rule",
                 f"data/{NS}/neoforge/biome_modifier/add_{name}_spawns.json")):
            if not os.path.isfile(os.path.join(RES, where)):
                fail(f"{name} has no {kind}, so it is a creature the rules "
                     f"describe and the world never shows")
        for key, why in (
                (f"entity.{NS}.{name}",
                 "reads as its own translation key wherever the game names it"),
                (f"bestiary.{NS}.trait.{name}",
                 "has no sentence saying what it does that its kin do not, which "
                 "is the one thing a player needs told about a roster this shape")):
            if key not in lang:
                fail(f"{name} {why}")

    # The check this section exists for, and the one no other file can make:
    # the spawn rule on disk has to be the row's own place. Two of a kindred in
    # one biome tag is the padding the rules forbid, arriving through the
    # generator instead.
    for kin_name, family in by_kin.items():
        seen_homes = {}
        for row in family:
            where = os.path.join(RES, f"data/{NS}/neoforge/biome_modifier",
                                 f"add_{row[0]}_spawns.json")
            if not os.path.isfile(where):
                continue
            try:
                written = json.loads(read(os.path.dirname(where),
                                          os.path.basename(where)))
            except ValueError:
                fail(f"{row[0]}'s spawn rule is not readable as JSON, so the game "
                     f"will refuse the whole data pack over one creature")
                continue
            biomes = written.get("biomes")
            wanted = homes.get(row[4])
            if wanted is not None and biomes != wanted:
                fail(f"{row[0]} is written into {biomes} and its row says "
                     f"{wanted}, so the rules hold a variant to a place of its "
                     f"own and the world puts it somewhere else")
            if biomes in seen_homes:
                fail(f"{row[0]} and {seen_homes[biomes]} are both written into "
                     f"{biomes} and both are {kin_name}, so the roster is padding "
                     f"itself where the player is standing")
            seen_homes[biomes] = row[0]

    # And the same for the trait, which is the other half.
    for kin_name, family in by_kin.items():
        seen_traits = {}
        for row in family:
            if row[3] in seen_traits:
                fail(f"{row[0]} and {seen_traits[row[3]]} are both {kin_name} and "
                     f"both of them {row[3]}, so whichever the player meets first "
                     f"teaches them the answer and the second is scenery")
            seen_traits[row[3]] = row[0]

    # Exactly one of each kindred is the one the others are versions of.
    for kin_name, family in by_kin.items():
        bases = [row[0] for row in family if row[2] == "true"]
        if not bases:
            fail(f"the {kin_name} kindred has no base on disk, so every one of "
                 f"its members is a variant of nothing")
        if len(bases) > 1:
            fail(f"the {kin_name} kindred has {len(bases)} bases — "
                 f"{', '.join(bases)} — so it is two families sharing a name")

    # One branch per trait, and no creature named in any of them.
    if not TRAITS_SRC:
        fail("nothing makes a creature's one difference from its kin happen, so "
             "all twenty-seven are mobs that walk at you")
    else:
        _traits = squashed(TRAITS_SRC)
        # Both switches, separately. One check across the whole file is a check
        # a control answers with the occurrence it did not break: every trait
        # appears in each of the two branches, either as its own case or in the
        # group that says the other one handles it.
        _on_clock = squashed(body_of(TRAITS_SRC, "public static void happen("))
        _on_contact = squashed(body_of(TRAITS_SRC, "public static void onContact("))
        for shorthand, trait in traits:
            if f"KindredRules.{shorthand}" not in _on_clock:
                fail(f"nothing {trait} on the clock — not even to say that "
                     f"contact handles it — so whatever carries it is a creature "
                     f"whose difference from its kin is a sentence in a language "
                     f"file")
        for shorthand, trait in traits:
            if f"KindredRules.{shorthand}" not in _on_contact:
                fail(f"nothing decides what {trait} does when the creature "
                     f"reaches you, so whether it lands at all is whatever the "
                     f"default branch happens to be")
        for row in kindred:
            if re.search(r'"%s"' % row[0], _traits):
                fail(f"{row[0]} is named in the traits, so the module has started "
                     f"growing a branch per creature — which on a roster that "
                     f"grows by variant is how eleven fights become twenty-seven "
                     f"near-copies of four")
        for named, _where in (("happen", _on_clock), ("onContact", _on_contact)):
            if "switch (row.trait())" not in _where:
                fail(f"what a creature does in {named} is not chosen by which "
                     f"trait it is, so whatever chooses it is something a "
                     f"validator cannot read — and on a roster that grows by "
                     f"variant, the thing it usually is instead is the creature's "
                     f"own name")
        # The clock and the contact are separate on purpose; a module with only
        # one of them is a roster whose danger the player learns to walk away
        # from, or one that hits them from across the room for no visible reason.
        for named, why in (
                ("public static void happen(",
                 "nothing happens on its own clock, so every difference between "
                 "two kin only lands if the player lets one reach them"),
                ("public static void onContact(",
                 "nothing happens on contact, so a creature's danger is a timer "
                 "the player learns to walk away from")):
            if named not in _traits:
                fail(why)

    if not KIN_ENTITY:
        fail("there is no entity for the kindred, so nothing the rules describe "
             "is alive")
    else:
        _kin_entity = squashed(KIN_ENTITY)
        if "KindredRules.creature(name)" not in _kin_entity:
            fail("a creature does not find its own row by the name it was "
                 "registered under, so whatever it is storing instead can be "
                 "stored wrong")
        # The entity must not decide what a trait does. The day it does, there
        # are two switches on trait() and they will disagree.
        if "switch" in squashed(body_of(KIN_ENTITY, "public void aiStep()")):
            fail("the entity decides for itself what a trait does, so there are "
                 "two places switching on the same field and they will disagree")
        _hurt = squashed(body_of(KIN_ENTITY, "public boolean doHurtTarget("))
        if "KinTraits.onContact(this, hurt, creature());" not in _hurt:
            fail("nothing hands a creature to the traits when it reaches you, so "
                 "the four that land on contact never land")
        _calls = squashed(body_of(KIN_ENTITY, "public void callOne()"))
        if "called >= CALLS_UP_TO" not in _calls:
            fail("nothing caps how many a caller may have out at once, so "
                 "'kill it first' is not advice, it is the only move — and a "
                 "fight with one move is not a fight")

    if not KIN_REG:
        fail("nothing registers the kindred, so all twenty-seven are rows")
    else:
        _kin_reg = squashed(KIN_REG)
        _kin_register = squashed(body_of(KIN_REG, "register() {"))
        if "for (KindredRules.Kin one : KindredRules.KINDRED) {" not in _kin_register:
            fail("the kindred are named by hand rather than walked from the "
                 "rules, so a twenty-eighth is a row nothing registers — and on "
                 "a roster of near-identical names, a hand-typed list is four "
                 "chances to paste the wrong id")
        if "made.size() != KindredRules.KINDRED.size()" not in _kin_reg:
            fail("nothing counts the creatures that were registered, so a roster "
                 "that half registers is a roster that half exists")
        if "MobCategory.MONSTER" not in _kin_reg:
            fail("the kindred are not registered as monsters, so the game counts "
                 "twenty-seven hostile things against the cap meant for the "
                 "harmless ones")

    _mod = squashed(strip_comments(read(SRC, "ElysiumBestiary.java")))
    if "KinEntities.ALL.size()" not in _mod:
        fail("the kindred's register is never touched while the mod is being "
             "built, so whether twenty-seven creatures exist depends on when FML "
             "happens to load the class")

    _kin_client = squashed(strip_comments(read(SRC, "client/BestiaryClient.java")))
    if "new KinRenderer(context," not in _kin_client:
        fail("the kindred have no renderer registered, so the first one a player "
             "meets takes the client down with it")
    if "event.registerLayerDefinition(KinModel.LAYERS.get(form)," not in _kin_client:
        fail("the kindred's model layers are never registered, so baking one "
             "throws and the renderer that was registered cannot be built")

    # Every rule the file declares is added up.
    _kin_sum = squashed(body_of(KINDRED_SRC, "public static List<String> faults()"))
    _kin_rules = set(re.findall(r"public static List<String> (faultsIn\w+)\(",
                                KINDRED_SRC))
    _kin_added = set(re.findall(r"bad\.addAll\((faultsIn\w+)\([^;]*\)\);", _kin_sum))
    if len(_kin_rules) < 10:
        fail(f"only {len(_kin_rules)} kindred rule(s) are declared at all, so the "
             f"check below is checking nothing")
    for _kin_rule in sorted(_kin_rules - _kin_added):
        fail(f"{_kin_rule} is written, compiled and never added up by the "
             f"kindred's faults(), so it answers the harness's controls and is "
             f"never once asked about what this module ships")

    print(f"the kindred         : {len(kindred)} creature(s) in {len(by_kin)} "
          f"kindred(s) over {len(traits)} thing(s) a player has to do differently "
          f"and {len(homes)} place(s) the game really has, no two of one kindred "
          f"doing one thing or standing in one place on disk, and not one of them "
          f"named in the branch that makes its difference happen")

# ---------------------------------------------------------------------------
# The farm
# ---------------------------------------------------------------------------
#
# The section that checks a proof rather than a roster. BeastRules spends most
# of its length proving that no mob is worth farming more than half again as
# much as any other; a farm is a multiplier on exactly that ratio, and a
# multiplier can break it without touching a line of the bestiary. So what is
# checked here is that the numbers the blocks actually use are the row's own,
# and that nothing in the world can apply a multiplier the rules have not seen.

print("-" * 70)

FARM_SRC = strip_comments(read(SRC, "rules/FarmRules.java"), keep_strings=True)
FARM_ACTS = strip_comments(read(SRC, "block/act/FarmActs.java"), keep_strings=True)
FARM_BLOCK = strip_comments(read(SRC, "block/FarmBlock.java"), keep_strings=True)
FARM_BE = strip_comments(read(SRC, "block/FarmBlockEntity.java"), keep_strings=True)
FARM_REG = strip_comments(read(SRC, "registry/FarmBlocks.java"), keep_strings=True)

if not FARM_SRC:
    fail("nothing says what a mob farm multiplies, so a grinder could reorder "
         "the whole bestiary and the bestiary's own proof would still pass")
else:
    _farm = squashed(FARM_SRC)
    machines = re.findall(
        r'new Machine\("([a-z_]+)", (\w+), ([\d.]+)D, ([\d.]+)D, "([\w:]+)",'
        r' (true|false)\)', _farm)
    kinds = re.findall(r'public static final String ([A-Z_]+) = "([a-z]+)";', FARM_SRC)
    if len(machines) < 3:
        fail(f"only {len(machines)} machine(s) could be read out of the farm rules, "
             f"so every check below is walking a short list and passing")

    for row in machines:
        name = row[0]
        for kind, where in (
                ("texture", f"assets/{NS}/textures/block/{name}.png"),
                ("blockstate", f"assets/{NS}/blockstates/{name}.json"),
                ("block model", f"assets/{NS}/models/block/{name}.json"),
                ("item model", f"assets/{NS}/models/item/{name}.json"),
                ("loot table", f"data/{NS}/loot_table/blocks/{name}.json"),
                ("recipe", f"data/{NS}/recipe/{name}.json")):
            if not os.path.isfile(os.path.join(RES, where)):
                fail(f"{name} has no {kind}, so it is a machine the rules describe "
                     f"and a player cannot place")
        for key, why in (
                (f"block.{NS}.{name}",
                 "reads as its own translation key wherever the game names it"),
                (f"bestiary.{NS}.machine.{name}",
                 "has no sentence saying what it does, which is a block a player "
                 "places once and never works out the point of")):
            if key not in lang:
                fail(f"{name} {why}")

    # What mines them (rules/HarvestRules; the harvest audit of 29 Sep 2026).
    # Every machine copies the game's iron block, which asks for the right
    # tool, and nothing put one in a tool's tag: all nine broke in seven
    # seconds and dropped nothing. So every registered block is in exactly one
    # of this module's mineable tags, or named BY_HAND in the rules with its
    # reason here; an exemption is read where the block is registered, not
    # taken on trust; the tags are what the rules say, written by the
    # generator; and a tag names only blocks this module registers, because
    # one unknown id makes the game refuse the whole tag.
    HARVEST_SRC = strip_comments(read(SRC, "rules/HarvestRules.java"), keep_strings=True)
    if not HARVEST_SRC:
        fail("rules/HarvestRules.java is missing, so nothing says what mines a machine "
             "and the tags are written from nothing")
    HARVEST_TOOLS = ("pickaxe", "axe", "shovel", "hoe")
    _machine_tool = re.search(r'String MACHINE_TOOL = "([a-z]+)";', HARVEST_SRC)
    _machine_tool = _machine_tool.group(1) if _machine_tool else ""
    if _machine_tool not in HARVEST_TOOLS:
        fail(f"HarvestRules.MACHINE_TOOL is {_machine_tool!r}, which is no tool the game "
             f"keeps a list for, so the machines are written into a tag nothing reads")
    _by_hand_found = re.search(r"List<String> BY_HAND = List\.of\(([^;]*?)\);", HARVEST_SRC, re.S)
    _by_hand = re.findall(r'"([a-z_]+)"', _by_hand_found.group(1)) if _by_hand_found else []
    MINEABLE = {}
    for _tool in HARVEST_TOOLS:
        _tag = os.path.join(RES, "data/minecraft/tags/block/mineable", _tool + ".json")
        if not os.path.isfile(_tag):
            continue
        _doc = json.load(open(_tag, encoding="utf-8"))
        if _doc.get("replace"):
            fail(f"mineable/{_tool} sets replace: true, which throws away every other "
                 f"mod's entries in the game's own tag")
        for _one in _doc.get("values", []):
            _named = _one.get("id") if isinstance(_one, dict) else _one
            if not isinstance(_named, str):
                continue
            MINEABLE.setdefault(_named, set()).add(_tool)
            if _named.startswith(f"{NS}:") and not os.path.isfile(os.path.join(
                    RES, f"assets/{NS}/blockstates", _named.split(":", 1)[1] + ".json")):
                fail(f"mineable/{_tool} names {_named}, which is not a block this module "
                     f"registers, so the game refuses the whole tag and every block in "
                     f"it is back to dropping nothing")

    #: The machines no tool is for, and why. HarvestRules.BY_HAND says which; a
    #: machine it names with no reason here is refused, and so is a reason for
    #: a machine it no longer names. Nothing, today: every one is an iron block.
    MINE_EXEMPT = {}

    #: What the game's blocks this module copies ask of a tool, read out of the
    #: game's own jar: the iron block wants the right one.
    ASKS_FOR_A_TOOL = {"IRON_BLOCK": True}

    def asks_for_a_tool(made, what):
        """Whether a block built like this asks for the right tool, and how."""
        if not made:
            fail(f"nothing here finds how {what} is built, so nothing shows whether it "
                 f"asks for the right tool")
            return None, ""
        if ".requiresCorrectToolForDrops()" in made:
            return True, "it says so where it is registered"
        for copied in re.findall(r"\bBlocks\.([A-Z_]+)", made):
            if copied not in ASKS_FOR_A_TOOL:
                fail(f"{what} copies Blocks.{copied}, and nothing here knows whether that "
                     f"asks for the right tool: look it up in the game's jar and add it")
                return None, ""
            if ASKS_FOR_A_TOOL[copied]:
                return True, f"it copies Blocks.{copied}, which does"
        return False, ""

    _machine_ids = [row[0] for row in machines]
    _blockstate_dir = os.path.join(RES, f"assets/{NS}/blockstates")
    for _name in sorted(os.path.splitext(f)[0] for f in os.listdir(_blockstate_dir)
                        if f.endswith(".json")) if os.path.isdir(_blockstate_dir) else []:
        if _name not in _machine_ids:
            fail(f"{_name} has a blockstate and is no machine of the rules, so nothing "
                 f"here can say what mines it")
    for _name in _by_hand:
        if _name not in MINE_EXEMPT:
            fail(f"HarvestRules breaks {_name} by hand and this gives no reason, so it is "
                 f"left out of every tool's tag on nobody's say-so")
        if _name not in _machine_ids:
            fail(f"HarvestRules breaks {_name} by hand and no such machine is registered")
    for _name in MINE_EXEMPT:
        if _name not in _by_hand:
            fail(f"a reason is given for breaking {_name} by hand and HarvestRules no "
                 f"longer does, so the exemption covers a block the rules put in a tag")
    # One statement registers all nine, so one reading answers for all nine.
    _flat_reg = squashed(FARM_REG)
    _start = _flat_reg.find("BLOCKS.register(one.id()")
    _made = _flat_reg[_start:_flat_reg.find(";", _start)] if _start >= 0 else ""
    _asks, _how = asks_for_a_tool(_made, "a machine")
    _tool_blocks = []
    _asking_untagged = []
    _untagged = []
    for _name in _machine_ids:
        _tags = MINEABLE.get(f"{NS}:{_name}", set())
        if _asks:
            _tool_blocks.append(_name)
        if len(_tags) > 1:
            fail(f"{_name} is in {len(_tags)} mineable tags ({', '.join(sorted(_tags))}), "
                 f"so two tools are right and the rules can only have named one")
        if _name in MINE_EXEMPT:
            if _asks:
                fail(f"{_name} is exempt from the mineable tags ({MINE_EXEMPT[_name]}), but "
                     f"it asks for the right tool: {_how}. With no tag no tool is right, so "
                     f"it would drop nothing")
            if _tags:
                fail(f"{_name} is exempt from the mineable tags and in one all the same; "
                     f"take it off MINE_EXEMPT or out of the tag")
            continue
        if not _tags:
            (_asking_untagged if _asks else _untagged).append(_name)
        if sorted(_tags) != [_machine_tool]:
            fail(f"the rules put {_name} under {_machine_tool} and the tags under "
                 f"{' and '.join(sorted(_tags)) or 'none'}; the tags are written from the "
                 f"rules by tools/gen_bestiary.py, so one of them was edited by hand")
    if _asking_untagged:
        fail(f"{len(_asking_untagged)} block(s) ask for the right tool and are in no tool's "
             f"tag, so no tool is ever right and they drop nothing however they are mined: "
             + ", ".join(_asking_untagged))
    if _untagged:
        fail(f"{len(_untagged)} block(s) are in no mineable tag and not exempt, so every "
             f"tool breaks them at a fist's pace: " + ", ".join(_untagged))
    print(f"what mines them     : {len(_machine_ids)} block(s), {len(_tool_blocks)} asking "
          f"for the right tool, every one in exactly one tool's tag or broken by hand "
          f"with a reason: {len(_machine_ids) - len(MINE_EXEMPT)} by {_machine_tool}, "
          f"{len(MINE_EXEMPT)} by hand")

    # What a machine is built from has to be a thing that exists. This check
    # exists because it did not: `minecraft:wool` reads like an item, is a tag,
    # and the recipe naming it was dropped at load with nothing said — one
    # machine in nine that simply could not be crafted.
    if THE_PACK_HAS or THE_GAME_MAKES:
        known = set(THE_PACK_HAS) | set(THE_GAME_MAKES)
        for row in machines:
            if row[4] not in known:
                fail(f"{row[0]} is built out of {row[4]} and nothing makes one of "
                     f"those, so its recipe is dropped at load and the machine is "
                     f"a block nobody can craft — with nothing anywhere saying so")

    # And the recipe on disk has to name the row's own ingredient, because a
    # cost written twice is a cost that disagrees with itself.
    for row in machines:
        where = os.path.join(RES, f"data/{NS}/recipe", f"{row[0]}.json")
        if not os.path.isfile(where):
            continue
        try:
            written = json.loads(read(os.path.dirname(where), os.path.basename(where)))
        except ValueError:
            fail(f"{row[0]}'s recipe is not readable as JSON, so the game refuses "
                 f"the whole data pack over one machine")
            continue
        named = {value.get("item") for value in written.get("key", {}).values()
                 if isinstance(value, dict)}
        if row[4] not in named:
            fail(f"{row[0]} is built out of {row[4]} in the rules and out of "
                 f"{sorted(named)} on disk, so what a machine costs is written in "
                 f"two places and they have already stopped agreeing")

    # One branch per kind, and no machine named in any of them.
    if not FARM_ACTS:
        fail("nothing makes a machine do its one job, so all nine are blocks that "
             "sit there")
    else:
        _acts = squashed(FARM_ACTS)
        for shorthand, kind in kinds:
            if f"FarmRules.{shorthand}" not in _acts:
                fail(f"nothing in the farm {kind}, so whatever is of that kind is a "
                     f"block whose point is a sentence in a language file")
        for row in machines:
            if re.search(r'"%s"' % row[0], _acts):
                fail(f"{row[0]} is named in the acts, so the module has started "
                     f"growing a branch per machine and the whole roster will "
                     f"follow it there")
        _happen = squashed(body_of(FARM_ACTS, "public static void happen("))
        if "switch (row.kind())" not in _happen:
            fail("what a machine does is not chosen by which kind it is, so "
                 "whatever chooses it is something a validator cannot read")
        # The line the whole feature is about: the killer's two multipliers are
        # the row's own. A number typed here would leave the proof passing and
        # the game doing something else, which is the one failure in this
        # module that is invisible from inside the game.
        _strike = squashed(body_of(FARM_ACTS, "private static void strike("))
        if "strike(self, mob, row.orbs(), row.drops());" not in _happen:
            fail("the killer is not handed the row's own multipliers, so what a "
                 "farm actually pays is a number typed into the branch — and the "
                 "proof that it stays inside the bestiary's band would still pass "
                 "while the game did something else")
        if "mob.getExperienceReward(server, mob)" not in _strike:
            fail("what a kill is worth is not asked of the game, so the share the "
                 "rules hold a killer to is a share of a number this module made "
                 "up")
        for typed in re.findall(r"Math\.round\(mob\.getExperienceReward\("
                                r"server, mob\) \* ([\d.]+)\)", _strike):
            fail(f"a killer keeps {typed} of the experience by a number typed into "
                 f"the branch, which is a second set of dials beside the rules "
                 f"that are supposed to decide it")

    if not FARM_BLOCK:
        fail("there is no block for the machines, so nothing the rules describe "
             "can be placed")
    else:
        _block = squashed(FARM_BLOCK)
        if "FarmRules.machine(name)" not in _block:
            fail("a machine does not find its own row by the name it was "
                 "registered under, so whatever it is storing instead can be "
                 "stored wrong")
        _ticker = squashed(body_of(FARM_BLOCK, "public <T extends BlockEntity> BlockEntityTicker<T> getTicker("))
        if "machine().powered()" not in _ticker:
            fail("every machine ticks whether it has anything to do or not, which "
                 "is what makes a large farm unplayable — three of the nine have "
                 "nothing to do on a clock at all")

    if not FARM_BE:
        fail("machines remember nothing, so what a gatherer has collected is lost "
             "on the first chunk unload")
    else:
        _be = squashed(FARM_BE)
        for named, why in (
                ("FarmActs.happen(this, machine());",
                 "nothing hands a machine to the acts, so no machine ever does "
                 "anything"),
                ('tag.putInt("ElysiumHeldOrbs", heldOrbs);',
                 "what a gatherer has collected is never written down, so it is "
                 "lost the first time the chunk unloads")):
            if named not in _be:
                fail(why)

    if not FARM_REG:
        fail("nothing registers the machines, so all nine are rows")
    else:
        _freg = squashed(FARM_REG)
        _fregister = squashed(body_of(FARM_REG, "register() {"))
        if "for (FarmRules.Machine one : FarmRules.MACHINES) {" not in _fregister:
            fail("the machines are named by hand rather than walked from the "
                 "rules, so a tenth is a row nothing registers")
        for named, what in (("register() {", "blocks"), ("items() {", "items")):
            _body = squashed(body_of(FARM_REG, named))
            if "made.size() != FarmRules.MACHINES.size()" not in _body:
                fail(f"nothing counts the machine {what} that were registered, so a "
                     f"roster that half registers is a roster that half exists — "
                     f"and half of nine is the kind of number nobody notices")
        # The block entity type has to cover every block, or placing the one it
        # missed throws the first time somebody does it.
        if "ALL.values().stream().map(DeferredBlock::get)" not in _freg:
            fail("the machines' block entity type is built from a list of names "
                 "rather than from the register, so a tenth machine throws the "
                 "first time anybody places one")

    _fmod = squashed(strip_comments(read(SRC, "ElysiumBestiary.java")))
    for named, why in (
            ("FarmBlocks.BLOCKS.register(modEventBus);",
             "the machines' blocks are never handed to the bus, so none of them "
             "exists"),
            ("FarmBlocks.ITEMS.register(modEventBus);",
             "the machines have blocks and no items, so nine blocks exist and "
             "nothing can place any of them"),
            ("FarmBlocks.BLOCK_ENTITIES.register(modEventBus);",
             "the machines' block entity type is never registered, so placing "
             "one throws")):
        if named not in _fmod:
            fail(why)

    # Every rule the file declares is added up.
    _fsum = squashed(body_of(FARM_SRC, "public static List<String> faults()"))
    _frules = set(re.findall(r"public static List<String> (faultsIn\w+)\(", FARM_SRC))
    _fadded = set(re.findall(r"bad\.addAll\((faultsIn\w+)\([^;]*\)\);", _fsum))
    if len(_frules) < 5:
        fail(f"only {len(_frules)} farm rule(s) are declared at all, so the check "
             f"below is checking nothing")
    # And faults() is a list, not a program. A rule added up inside a condition
    # is a rule the check above still sees the call to, and the condition is
    # where "we turned that one off for a minute" goes to live for a year.
    if re.search(r"\bif\s*\(", _fsum):
        fail("the farm's faults() has a condition in it, so some rule is added up "
             "only sometimes — and the check that every rule is added up cannot "
             "tell the difference between that and always")
    for _frule in sorted(_frules - _fadded):
        fail(f"{_frule} is written, compiled and never added up by the farm's "
             f"faults(), so it answers the harness's controls and is never once "
             f"asked about what this module ships")

    print(f"the farm            : {len(machines)} machine(s) over {len(kinds)} "
          f"kind(s), every one of them built out of something the game really "
          f"makes and costing on disk what its row says, not one of them named in "
          f"the branch that makes its kind happen, and the only numbers that can "
          f"reorder the bestiary asked of the rules rather than typed")



# ---------------------------------------------------------------------------
# The courts
# ---------------------------------------------------------------------------
#
# The largest roster this module has, and the one where a validator earns most:
# fifty-eight creatures of which nineteen do the same job under different names.
# What is checked here is the half of the claim no pure rule can see — that the
# spawn rule on disk allows as many of a thing as the budget was computed
# against, and that nothing in the world can put more of a court on the floor
# than the arithmetic was told about.

print("-" * 70)

COURT_SRC = strip_comments(read(SRC, "rules/CourtRules.java"), keep_strings=True)
COURT_ENTITY = strip_comments(read(SRC, "entity/CourtEntity.java"), keep_strings=True)
ROLES_SRC = strip_comments(read(SRC, "entity/act/CourtRoles.java"), keep_strings=True)
COURT_REG = strip_comments(read(SRC, "registry/CourtEntities.java"), keep_strings=True)

if not COURT_SRC:
    fail("nothing says what a court is, so a boss could be a wall and its "
         "minions could be the whole fight with nothing reporting either")
else:
    _court = squashed(COURT_SRC)
    members = re.findall(
        r'new Member\("([a-z_]+)", "([a-z_]+)", (\w+), (\d+), ([\d.]+)D,'
        r' ([\d.]+)D, ([\d.]+)D, (\d+), (\d+), "([\w:]*)", "([\w:]*)"\)', _court)
    roles = re.findall(r'public static final String ([A-Z_]+) = "([a-z]+)";', COURT_SRC)
    if len(members) < 6:
        fail(f"only {len(members)} creature(s) could be read out of the court "
             f"rules, so every check below is walking a short list and passing")

    in_court = {}
    for row in members:
        in_court.setdefault(row[1], []).append(row)

    for row in members:
        name = row[0]
        for kind, where in (
                ("texture", f"assets/{NS}/textures/entity/{name}.png"),
                ("spawn egg model",
                 f"assets/{NS}/models/item/{name}_spawn_egg.json"),
                ("loot table", f"data/{NS}/loot_table/entities/{name}.json"),
                ("spawn rule",
                 f"data/{NS}/neoforge/biome_modifier/add_{name}_spawns.json")):
            if not os.path.isfile(os.path.join(RES, where)):
                fail(f"{name} has no {kind}, so it is a creature the rules "
                     f"describe and the world never shows")
        for key, why in (
                (f"entity.{NS}.{name}",
                 "reads as its own translation key wherever the game names it"),
                (f"bestiary.{NS}.role.{name}",
                 "has no sentence saying what it is in its court, which on a "
                 "roster of fifty-eight is the difference between a fight and "
                 "another skeleton")):
            if key not in lang:
                fail(f"{name} {why}")

    # The check no pure rule can make. The budget that holds a court under its
    # head counts what can be standing at once; a spawn rule that puts more of
    # something on the floor than its row allows makes that number a fact about
    # a table rather than about the game.
    for row in members:
        where = os.path.join(RES, f"data/{NS}/neoforge/biome_modifier",
                             f"add_{row[0]}_spawns.json")
        if not os.path.isfile(where):
            continue
        try:
            written = json.loads(read(os.path.dirname(where),
                                      os.path.basename(where)))
        except ValueError:
            fail(f"{row[0]}'s spawn rule is not readable as JSON, so the game "
                 f"refuses the whole data pack over one creature")
            continue
        for spawner in written.get("spawners", []):
            if spawner.get("maxCount") != int(row[7]):
                fail(f"{row[0]} may have {row[7]} standing at once in the rules "
                     f"and {spawner.get('maxCount')} in its spawn rule, so the "
                     f"budget that holds its court under its head was computed "
                     f"against a number the world does not use")
            if spawner.get("weight") != int(row[8]):
                fail(f"{row[0]} is chosen {row[8]} times in the rules and "
                     f"{spawner.get('weight')} times on disk")

    # Exactly one head per court, checked against what is on disk rather than
    # only in the table.
    for court_name, court_members in in_court.items():
        heads = [row[0] for row in court_members if row[2] == "HEAD"]
        if not heads:
            fail(f"the {court_name} court has no head on disk, so everything in "
                 f"it is held under nothing")
        if len(heads) > 1:
            fail(f"the {court_name} court has {len(heads)} heads — "
                 f"{', '.join(heads)} — so its budget is spent twice")

    # A second phase has to be answered by something that exists, the way a
    # myth's bane is. An answer nothing makes is a wall wearing an answer.
    if THE_PACK_HAS or THE_GAME_MAKES:
        known = set(THE_PACK_HAS) | set(THE_GAME_MAKES)
        for row in members:
            for answer, which in ((row[9], "first phase"), (row[10], "second phase")):
                if answer and answer not in known:
                    fail(f"{row[0]}'s {which} is answered by {answer} and nothing "
                         f"makes one of those, so what the player is told to bring "
                         f"is a name in a table")

    # One branch per role, and no creature named in any of them.
    if not ROLES_SRC:
        fail("nothing makes a creature's place in its court happen, so all "
             "fifty-eight are mobs that walk at you")
    else:
        _roles = squashed(ROLES_SRC)
        _happen = squashed(body_of(ROLES_SRC, "public static void happen("))
        _contact = squashed(body_of(ROLES_SRC, "public static void onContact("))
        for shorthand, role in roles:
            if f"CourtRules.{shorthand}" not in _happen:
                fail(f"nothing decides what a {role} does on the clock, so "
                     f"whatever fills that place in a court is a creature whose "
                     f"point is a sentence in a language file")
        for shorthand, role in roles:
            if f"CourtRules.{shorthand}" not in _contact:
                fail(f"nothing decides what a {role} does when it reaches you, so "
                     f"whether it lands at all is whatever the default branch "
                     f"happens to be")
        for row in members:
            if re.search(r'"%s"' % row[0], _roles):
                fail(f"{row[0]} is named in the roles, so the module has started "
                     f"growing a branch per creature — on a roster where nineteen "
                     f"of them do the same job, which is fifty-eight places for "
                     f"one bug")
        for named, _where in (("happen", _happen), ("onContact", _contact)):
            if "switch (row.role())" not in _where:
                fail(f"what a creature does in {named} is not chosen by which "
                     f"role it is, so whatever chooses it is something a "
                     f"validator cannot read")
        # The cap the whole budget depends on.
        if "self.roomFor() > 0" not in _happen:
            fail("a herald calls without asking whether there is room, so what a "
                 "court can have standing is unbounded — and the rule that holds "
                 "it under its head becomes a fact about a table")
        # And the moment, which is not a role.
        if "public static void turns(" not in ROLES_SRC:
            fail("nothing happens when a two-phased head turns, so the second "
                 "phase is a flag nothing in the world reflects")

    if not COURT_ENTITY:
        fail("there is no entity for the courts, so nothing the rules describe "
             "is alive")
    else:
        _centity = squashed(COURT_ENTITY)
        if "CourtRules.member(name)" not in _centity:
            fail("a creature does not find its own row by the name it was "
                 "registered under, so whatever it is storing instead can be "
                 "stored wrong")
        _room = squashed(body_of(COURT_ENTITY, "public int roomFor()"))
        if "member().most()" not in _room:
            fail("how many of a thing may be standing is not the row's own, so "
                 "the number the court's budget was computed against and the "
                 "number the game keeps are two different numbers")
        _answered = squashed(body_of(COURT_ENTITY, "public String answeredBy()"))
        for named, why in (
                ("row.then()", "a head that has turned is still answered by what "
                               "answered its first phase, so the second phase is "
                               "the same fight with more health"),
                ("row.first()", "a head that has not turned is answered by its "
                                "second phase's answer, so the fight starts where "
                                "it was meant to end")):
            if named not in _answered:
                fail(why)
        _step = squashed(body_of(COURT_ENTITY, "public void aiStep()"))
        if "CourtRoles.happen(this, row);" not in _step:
            fail("nothing hands a creature to the roles on the clock, so no "
                 "court ever does anything")
        if "getHealth() <= getMaxHealth() * TURNS_AT" not in _step:
            fail("nothing turns a two-phased head, so its second phase is a "
                 "field nothing ever sets")

    if not COURT_REG:
        fail("nothing registers the courts, so all fifty-eight are rows")
    else:
        _creg = squashed(COURT_REG)
        _cregister = squashed(body_of(COURT_REG, "register() {"))
        if "for (CourtRules.Member one : CourtRules.MEMBERS) {" not in _cregister:
            fail("the courts are named by hand rather than walked from the "
                 "rules, so a fifty-ninth is a row nothing registers — and on a "
                 "roster where nineteen creatures do one job, a pasted line "
                 "reads correct while pointing at the wrong row")
        if "made.size() != CourtRules.MEMBERS.size()" not in _creg:
            fail("nothing counts the creatures that were registered, so a roster "
                 "that half registers is a roster that half exists")
        if "MobCategory.MONSTER" not in _creg:
            fail("the courts are registered in the harmless bucket, and the game "
                 "counts the two against separate caps")
        if "one.head() ? HEADS_ARE" not in _creg:
            fail("a head is the same size as the things around it, so which one "
                 "the fight is cannot be told from inside the room — and the "
                 "whole arrangement of this module is invisible there")

    _cmod = squashed(strip_comments(read(SRC, "ElysiumBestiary.java")))
    if "CourtEntities.ALL.size()" not in _cmod:
        fail("the courts' register is never touched while the mod is being "
             "built, so whether fifty-eight creatures exist depends on when FML "
             "happens to load the class")

    _cclient = squashed(strip_comments(read(SRC, "client/BestiaryClient.java")))
    if "new CourtRenderer(context," not in _cclient:
        fail("the courts have no renderer registered, so the first one a player "
             "meets takes the client down with it")
    if "event.registerLayerDefinition(CourtModel.LAYERS.get(form)," not in _cclient:
        fail("the courts' model layers are never registered, so baking one "
             "throws and the renderer that was registered cannot be built")

    # Every rule the file declares is added up, and faults() is a list.
    _csum = squashed(body_of(COURT_SRC, "public static List<String> faults()"))
    _crules = set(re.findall(r"public static List<String> (faultsIn\w+)\(", COURT_SRC))
    _cadded = set(re.findall(r"bad\.addAll\((faultsIn\w+)\([^;]*\)\);", _csum))
    if len(_crules) < 9:
        fail(f"only {len(_crules)} court rule(s) are declared at all, so the "
             f"check below is checking nothing")
    if re.search(r"\bif\s*\(", _csum):
        fail("the courts' faults() has a condition in it, so some rule is added "
             "up only sometimes")
    for _crule in sorted(_crules - _cadded):
        fail(f"{_crule} is written, compiled and never added up by the courts' "
             f"faults(), so it answers the harness's controls and is never once "
             f"asked about what this module ships")

    print(f"the courts          : {len(members)} creature(s) in {len(in_court)} "
          f"court(s) over {len(roles)} role(s), one head each, every one of them "
          f"allowed on the floor in exactly the numbers its court's budget was "
          f"computed against, every phase answered by something the pack really "
          f"makes, and not one of them named in the branch that makes its role "
          f"happen")



# ---------------------------------------------------------------------------
# The warded
# ---------------------------------------------------------------------------
#
# The section about gear rather than about creatures. What a thing does to
# somebody carrying the right item, and to somebody carrying nothing, is a pair
# of numbers the rules prove a band about — and the one way that proof goes
# stale without anything reporting it is for the game to use a third number.
# So most of what is checked here is that nothing in the world decides how hard
# one of these hits except the row it came from.

print("-" * 70)

WARD_SRC = strip_comments(read(SRC, "rules/WardRules.java"), keep_strings=True)
WARD_ENTITY = strip_comments(read(SRC, "entity/WardEntity.java"), keep_strings=True)
WARD_ACTS = strip_comments(read(SRC, "entity/act/WardActs.java"), keep_strings=True)
WARD_REG = strip_comments(read(SRC, "registry/WardEntities.java"), keep_strings=True)

if not WARD_SRC:
    fail("nothing says what gear any of this is about, so a creature could be a "
         "door for the player who brought the set and a wall for the one who "
         "did not, with nothing reporting either")
else:
    _ward = squashed(WARD_SRC)
    warded = re.findall(
        r'new Warded\("([a-z_]+)", (\w+), "([\w:]+)", (\d+), ([\d.]+)D,'
        r' ([\d.]+)D, ([\d.]+)D, ([\d.]+)D, (\d+)\)', _ward)
    wards = re.findall(r'public static final String ([A-Z_]+) = "([a-z]+)";', WARD_SRC)
    if len(warded) < 4:
        fail(f"only {len(warded)} creature(s) could be read out of the ward rules, "
             f"so every check below is walking a short list and passing")

    for row in warded:
        name = row[0]
        for kind, where in (
                ("texture", f"assets/{NS}/textures/entity/{name}.png"),
                ("spawn egg model",
                 f"assets/{NS}/models/item/{name}_spawn_egg.json"),
                ("loot table", f"data/{NS}/loot_table/entities/{name}.json"),
                ("spawn rule",
                 f"data/{NS}/neoforge/biome_modifier/add_{name}_spawns.json")):
            if not os.path.isfile(os.path.join(RES, where)):
                fail(f"{name} has no {kind}, so it is a creature the rules "
                     f"describe and the world never shows")
        for key, why in (
                (f"entity.{NS}.{name}",
                 "reads as its own translation key wherever the game names it"),
                (f"bestiary.{NS}.ward.{name}",
                 "has no sentence saying what to bring, and 'bring the right "
                 "gear' is only advice if the player is told which gear")):
            if key not in lang:
                fail(f"{name} {why}")

    # What the player is told to bring has to be a thing the pack or the game
    # really makes, the way a myth's answer does.
    if THE_PACK_HAS or THE_GAME_MAKES:
        known = set(THE_PACK_HAS) | set(THE_GAME_MAKES)
        for row in warded:
            if row[2] not in known:
                fail(f"{row[0]} is answered by {row[2]} and nothing makes one of "
                     f"those, so what the player is told to bring is a word in a "
                     f"table")

    # And the tooltip has to name the row's own answer, because a tooltip and an
    # arithmetic that came apart is a player brought the wrong thing on purpose.
    for row in warded:
        said = lang.get(f"bestiary.{NS}.bring.{row[0]}")
        if said is not None and said != row[2]:
            fail(f"{row[0]} is answered by {row[2]} in the rules and the game "
                 f"tells the player to bring {said}, so the advice and the "
                 f"arithmetic have already come apart")

    # One branch per ward, and no creature named.
    if not WARD_ACTS:
        fail("nothing looks for a ward, so every one of these hits for its "
             "unwarded number whatever the player brought — which makes the band "
             "the rules prove a fact about a table")
    else:
        _acts = squashed(WARD_ACTS)
        _looks = squashed(body_of(WARD_ACTS, "public static boolean warded("))
        for shorthand, ward in wards:
            if f"WardRules.{shorthand}" not in _looks:
                fail(f"nothing decides whether a player is protected from {ward}, "
                     f"so whichever creatures are about it hit for their unwarded "
                     f"number always")
        for row in warded:
            if re.search(r'"%s"' % row[0], _acts):
                fail(f"{row[0]} is named where the ward is looked for, so the "
                     f"module has started asking the same question about the "
                     f"player thirteen slightly different ways")
        if "switch (row.ward())" not in _looks:
            fail("which ward to look for is not chosen by the row's own ward, so "
                 "whatever chooses it is something a validator cannot read")
        # The line the whole feature is about: the amount is the row's, either
        # way. A number typed here leaves the band passing and the game hitting
        # for something else.
        _bites = squashed(body_of(WARD_ACTS, "public static float bites("))
        if "warded(who, row) ? row.blunted() : row.bite()" not in _bites:
            fail("what one of these takes off is not the row's own pair of "
                 "numbers, so the proof that every creature leaves between a "
                 "third and four fifths of itself is about a table and the game "
                 "hits for something else")
        for typed in re.findall(r"return \(float\) ([\d.]+)", _bites):
            fail(f"a creature takes off {typed} by a number typed into the branch, "
                 f"which is a third number beside the two the rules prove a band "
                 f"about")

    if not WARD_ENTITY:
        fail("there is no entity for the warded, so nothing the rules describe "
             "is alive")
    else:
        _wentity = squashed(WARD_ENTITY)
        if "WardRules.warded(name)" not in _wentity:
            fail("a creature does not find its own row by the name it was "
                 "registered under, so whatever it is storing instead can be "
                 "stored wrong")
        _hurt = squashed(body_of(WARD_ENTITY, "public boolean doHurtTarget("))
        if "WardActs.bites(this, hurt, row);" not in _hurt:
            fail("nothing asks what a hit should take off, so the attack "
                 "attribute lands whatever the player is carrying and the ward "
                 "is a note in a table")
        # The attribute is the worst case on purpose. Set to the warded number,
        # a bug in the warding makes every fight free rather than hard.
        _attrs = squashed(body_of(WARD_ENTITY, "public static AttributeSupplier.Builder attributes("))
        if "Attributes.ATTACK_DAMAGE, row.bite()" not in _attrs:
            fail("the attack attribute is not the unwarded number, so a bug in "
                 "the warding makes every one of these fights free rather than "
                 "hard — and free is the one that nobody reports")

    if not WARD_REG:
        fail("nothing registers the warded, so all thirteen are rows")
    else:
        _wreg = squashed(WARD_REG)
        _wregister = squashed(body_of(WARD_REG, "register() {"))
        if "for (WardRules.Warded one : WardRules.WARDED) {" not in _wregister:
            fail("the warded are named by hand rather than walked from the "
                 "rules, so a fourteenth is a row nothing registers")
        if "made.size() != WardRules.WARDED.size()" not in _wreg:
            fail("nothing counts the creatures that were registered, so a roster "
                 "that half registers is a roster that half exists")

    _wmod = squashed(strip_comments(read(SRC, "ElysiumBestiary.java")))
    if "WardEntities.ALL.size()" not in _wmod:
        fail("the warded's register is never touched while the mod is being "
             "built, so whether thirteen creatures exist depends on when FML "
             "happens to load the class")

    _wclient = squashed(strip_comments(read(SRC, "client/BestiaryClient.java")))
    if "new WardRenderer(context," not in _wclient:
        fail("the warded have no renderer registered, so the first one a player "
             "meets takes the client down with it")
    if "event.registerLayerDefinition(WardModel.LAYERS.get(form)," not in _wclient:
        fail("the warded's model layers are never registered, so baking one "
             "throws and the renderer that was registered cannot be built")

    # Every rule the file declares is added up, and faults() is a list.
    _wsum = squashed(body_of(WARD_SRC, "public static List<String> faults()"))
    _wrules = set(re.findall(r"public static List<String> (faultsIn\w+)\(", WARD_SRC))
    _wadded = set(re.findall(r"bad\.addAll\((faultsIn\w+)\([^;]*\)\);", _wsum))
    if len(_wrules) < 8:
        fail(f"only {len(_wrules)} ward rule(s) are declared at all, so the check "
             f"below is checking nothing")
    if re.search(r"\bif\s*\(", _wsum):
        fail("the warded's faults() has a condition in it, so some rule is added "
             "up only sometimes")
    for _wrule in sorted(_wrules - _wadded):
        fail(f"{_wrule} is written, compiled and never added up by the warded's "
             f"faults(), so it answers the harness's controls and is never once "
             f"asked about what this module ships")

    print(f"the warded          : {len(warded)} creature(s) over {len(wards)} "
          f"kind(s) of protection, every one of them answered by something the "
          f"pack really makes, told to the player as the row's own answer rather "
          f"than a second copy of it, and what each takes off asked of its row "
          f"both with the gear and without")



# ---------------------------------------------------------------------------
# The Vale's own
# ---------------------------------------------------------------------------
#
# The roster that fills a dimension somebody else's module already built, and
# the section with the most to check that no rule can see. The gradient the
# rules prove is a fact about where things are; where things are is a spawn
# rule on disk naming a biome in another module's data. A name with a letter
# wrong there is a creature that never appears, and it looks exactly like a
# creature that is rare — which is the failure this whole module keeps writing
# checks against, and which this section caught on its first run.

print("-" * 70)

VALE_SRC = strip_comments(read(SRC, "rules/DenizenRules.java"), keep_strings=True)
VALE_ENTITY = strip_comments(read(SRC, "entity/DenizenEntity.java"), keep_strings=True)
VALE_REG = strip_comments(read(SRC, "registry/DenizenEntities.java"), keep_strings=True)


def the_pack_grows():
    """Every biome any module in this pack actually writes."""
    found = set()
    for where, _, names in os.walk(ROOT):
        tidy = where.replace(os.sep, "/")
        if not tidy.endswith("/worldgen/biome"):
            continue
        parts = tidy.split("/")
        if len(parts) < 4:
            continue
        namespace = parts[-3]
        for name in names:
            if name.endswith(".json"):
                found.add(f"{namespace}:{name[:-5]}")
    return found


THE_PACK_GROWS = the_pack_grows()

if not VALE_SRC:
    fail("nothing says what lives in the Vale, so fifteen biomes that were "
         "proved to partition a climate plane have nothing in any of them")
else:
    _vale = squashed(VALE_SRC)
    denizens = re.findall(
        r'new Denizen\("([a-z_]+)", "([a-z_]+)", (\d+), ([\d.]+)D, ([\d.]+)D,'
        r' ([\d.]+)D, (\d+)\)', _vale)
    places = re.findall(r'"([a-z_]+)"',
                        re.search(r"List<String> PLACES = List\.of\((.*?)\);",
                                  _vale).group(1)) if "PLACES = List.of(" in _vale else []
    if len(denizens) < 8:
        fail(f"only {len(denizens)} creature(s) could be read out of the Vale's "
             f"rules, so every check below is walking a short list and passing")

    for row in denizens:
        name = row[0]
        for kind, where in (
                ("texture", f"assets/{NS}/textures/entity/{name}.png"),
                ("spawn egg model",
                 f"assets/{NS}/models/item/{name}_spawn_egg.json"),
                ("loot table", f"data/{NS}/loot_table/entities/{name}.json"),
                ("spawn rule",
                 f"data/{NS}/neoforge/biome_modifier/add_{name}_spawns.json")):
            if not os.path.isfile(os.path.join(RES, where)):
                fail(f"{name} has no {kind}, so it is a creature the rules "
                     f"describe and the world never shows")
        for key, why in (
                (f"entity.{NS}.{name}",
                 "reads as its own translation key wherever the game names it"),
                (f"bestiary.{NS}.ring.{name}",
                 "has no sentence saying how far out it lives, and 'further out "
                 "is harder' is only useful to a player who has been told that "
                 "further out is the thing that matters here")):
            if key not in lang:
                fail(f"{name} {why}")

    # The check this section exists for. The rules prove a gradient over
    # crossings; the crossings are biomes in another module, and a spawn rule
    # naming one that does not exist is a creature that never appears.
    for row in denizens:
        where = os.path.join(RES, f"data/{NS}/neoforge/biome_modifier",
                             f"add_{row[0]}_spawns.json")
        if not os.path.isfile(where):
            continue
        try:
            written = json.loads(read(os.path.dirname(where),
                                      os.path.basename(where)))
        except ValueError:
            fail(f"{row[0]}'s spawn rule is not readable as JSON, so the game "
                 f"refuses the whole data pack over one creature")
            continue
        biomes = written.get("biomes")
        if not isinstance(biomes, str) or not biomes.startswith("#"):
            continue
        # The modifier names a tag, so the biome is one step further on. A
        # check that stopped at the tag would pass for a tag that is empty or
        # that holds a biome nothing grows -- which is the same creature that
        # never appears, reached through one more file.
        plain = biomes[1:].split(":", 1)[-1]
        tag_at = os.path.join(RES, f"data/{NS}/tags/worldgen/biome",
                              f"{plain}.json")
        if not os.path.isfile(tag_at):
            fail(f"{row[0]} is put in {biomes} and this module writes no such "
                 f"tag, so it is a creature that never appears")
            continue
        try:
            held = json.loads(read(os.path.dirname(tag_at),
                                   os.path.basename(tag_at))).get("values", [])
        except ValueError:
            fail(f"{plain} is not readable as JSON, so the game refuses the "
                 f"whole data pack over one tag")
            continue
        if not held:
            fail(f"{row[0]} is put in {biomes} and that tag holds nothing, so "
                 f"it is a creature that never appears — which looks exactly "
                 f"like one that is rare")
        for grown in held:
            if THE_PACK_GROWS and grown not in THE_PACK_GROWS:
                fail(f"{row[0]} is put in {biomes}, which holds {grown}, and no "
                     f"module in this pack grows a biome of that name")
            if not str(grown).endswith(row[1]):
                fail(f"{row[0]} lives in {row[1]} in the rules and its tag holds "
                     f"{grown}, so the gradient the rules prove is about "
                     f"somewhere the world does not put it")

    # Every crossing the rules name has to be a biome the pack really grows,
    # even the ones nothing happens to live in yet.
    if THE_PACK_GROWS:
        for place in places:
            if not any(grown.endswith(place) for grown in THE_PACK_GROWS):
                fail(f"the Vale's roster names {place} and no module in this "
                     f"pack grows a biome of that name, so the map this "
                     f"gradient is measured over is not the map the world has")

    if not VALE_ENTITY:
        fail("there is no entity for the Vale's own, so nothing the rules "
             "describe is alive")
    else:
        _ventity = squashed(VALE_ENTITY)
        if "DenizenRules.denizen(name)" not in _ventity:
            fail("a creature does not find its own row by the name it was "
                 "registered under, so whatever it is storing instead can be "
                 "stored wrong")
        _rings = squashed(body_of(VALE_ENTITY, "public int ringsOut()"))
        if "denizen().ring()" not in _rings:
            fail("how far out one of these lives is not the row's own, so what "
                 "the renderer draws and what the rules prove are two different "
                 "numbers")

    if not VALE_REG:
        fail("nothing registers the Vale's own, so all twenty-eight are rows")
    else:
        _vreg = squashed(VALE_REG)
        _vregister = squashed(body_of(VALE_REG, "register() {"))
        if "for (DenizenRules.Denizen one : DenizenRules.DENIZENS) {" not in _vregister:
            fail("the Vale's own are named by hand rather than walked from the "
                 "rules, so a twenty-ninth is a row nothing registers")
        if "made.size() != DenizenRules.DENIZENS.size()" not in _vreg:
            fail("nothing counts the creatures that were registered, so a roster "
                 "that half registers is a roster that half exists")

    _vmod = squashed(strip_comments(read(SRC, "ElysiumBestiary.java")))
    if "DenizenEntities.ALL.size()" not in _vmod:
        fail("the Vale's register is never touched while the mod is being built, "
             "so whether twenty-eight creatures exist depends on when FML "
             "happens to load the class")

    _vclient = squashed(strip_comments(read(SRC, "client/BestiaryClient.java")))
    if "new DenizenRenderer(context," not in _vclient:
        fail("the Vale's own have no renderer registered, so the first one a "
             "player meets takes the client down with it")
    if "event.registerLayerDefinition(DenizenModel.LAYERS.get(form)," not in _vclient:
        fail("the Vale's model layers are never registered, so baking one throws "
             "and the renderer that was registered cannot be built")
    _vrender = strip_comments(read(SRC, "client/DenizenRenderer.java"))
    if "one.ringsOut()" not in squashed(_vrender):
        fail("nothing on the screen says how far out a creature lives, so a "
             "player has to work it out from how badly the fight went — which is "
             "the coin flip this roster exists to remove")

    # Every rule the file declares is added up, and faults() is a list.
    _vsum = squashed(body_of(VALE_SRC, "public static List<String> faults()"))
    _vrules = set(re.findall(r"public static List<String> (faultsIn\w+)\(", VALE_SRC))
    _vadded = set(re.findall(r"bad\.addAll\((faultsIn\w+)\([^;]*\)\);", _vsum))
    if len(_vrules) < 8:
        fail(f"only {len(_vrules)} Vale rule(s) are declared at all, so the check "
             f"below is checking nothing")
    if re.search(r"\bif\s*\(", _vsum):
        fail("the Vale's faults() has a condition in it, so some rule is added "
             "up only sometimes")
    for _vrule in sorted(_vrules - _vadded):
        fail(f"{_vrule} is written, compiled and never added up by the Vale's "
             f"faults(), so it answers the harness's controls and is never once "
             f"asked about what this module ships")

    print(f"the Vale            : {len(denizens)} creature(s) across "
          f"{len(places)} crossing(s), every one of them put on disk in the "
          f"biome its row names and in a biome this pack really grows, and how "
          f"far out each lives drawn on the screen rather than left for the "
          f"player to work out from how badly the fight went")



# ---------------------------------------------------------------------------
# All of them at once
# ---------------------------------------------------------------------------
#
# Eight rosters, one registry. Every section above checks its own roster for
# creatures written twice; none of them can see a name used once in two
# different tables, and that is the one that hurts — the second register throws
# at load with a message about entity types, or the generator writes one
# creature's spawn rule over another's and the loser simply never appears.
#
# This section exists because it happened: `goblin` was a kindred and a denizen
# of the Vale, `maggot` was in a court and in the Vale, and what reported it
# was a spawn rule pointing at the wrong tag two sections further down.

ROSTERS = (
    ("the beasts", "BeastRules.java", r'new Beast\("([a-z_]+)"'),
    ("the fauna", "FaunaRules.java", r'new Animal\("([a-z_]+)"'),
    ("the myths", "MythRules.java", r'new Myth\("([a-z_]+)"'),
    ("the companions", "CompanionRules.java", r'new Companion\("([a-z_]+)"'),
    ("the kindred", "KindredRules.java", r'new Kin\("([a-z_]+)"'),
    ("the courts", "CourtRules.java", r'new Member\("([a-z_]+)"'),
    ("the warded", "WardRules.java", r'new Warded\("([a-z_]+)"'),
    ("the Vale", "DenizenRules.java", r'new Denizen\("([a-z_]+)"'),
)

_named = {}
_creatures = 0
_rosters_read = 0
for _label, _file, _pattern in ROSTERS:
    _text = read(PURE, _file)
    if not _text:
        fail(f"{_file} is missing, so {_label} is a roster nothing in this "
             f"module can check against any other")
        continue
    _rosters_read += 1
    for _name in re.findall(_pattern, squashed(strip_comments(_text, keep_strings=True))):
        _creatures += 1
        _named.setdefault(_name, []).append(_label)

if _rosters_read < len(ROSTERS):
    fail(f"only {_rosters_read} of {len(ROSTERS)} roster(s) could be read, so "
         f"the check below is comparing a subset against itself")
else:
    for _name, _in in sorted(_named.items()):
        if len(_in) > 1:
            fail(f"{_name} is a creature in {' and in '.join(_in)}, and they all "
                 f"register onto one registry — so the second one throws at load, "
                 f"or the generator writes one's spawn rule over the other's and "
                 f"the loser never appears")
    print(f"every roster        : {_creatures} creature(s) across "
          f"{_rosters_read} roster(s), every one of them under a name no other "
          f"roster in this module uses")

    # The generator writes a creature's files and never removes them, so a
    # rename leaves the old name's spawn rule, egg and loot behind (a sheet too,
    # but a sheet nobody draws is only disk). The
    # game refuses to load a spawn rule for an entity type it does not have -
    # the whole registry layer fails, and no world opens - so every file named
    # for a creature has to be named for one the rosters still have.
    _leftovers = []
    for _folder, _pattern in (
            (f"data/{NS}/neoforge/biome_modifier", r"^add_([a-z_]+)_spawns\.json$"),
            (f"assets/{NS}/models/item", r"^([a-z_]+)_spawn_egg\.json$"),
            (f"data/{NS}/loot_table/entities", r"^([a-z_]+)\.json$")):
        _dir = os.path.join(RES, _folder)
        for _entry in sorted(os.listdir(_dir)) if os.path.isdir(_dir) else []:
            _m = re.match(_pattern, _entry)
            if _m and _m.group(1) not in _named:
                _leftovers.append(f"{_folder}/{_entry}")
    if _leftovers:
        fail(f"{len(_leftovers)} file(s) are named for a creature no roster has - "
             f"a rename's leftovers, and a spawn rule among them stops every world "
             f"from loading: {', '.join(_leftovers[:6])}")
    else:
        print(f"no leftovers        : every spawn rule, egg and loot table on "
              f"disk is named for a creature a roster still has")




# Every roster with entities has its spawn eggs. Four rosters shipped with an
# egg's model and name and no egg, so 127 creatures could not be put in front
# of a player to look at (the catalogue test, 27 September 2026).
_eggs_src = read(SRC, "registry/BeastItems.java")
for _roster in ("BeastRules.BEASTS", "CompanionRules.COMPANIONS", "MythRules.MYTHS",
                "DenizenRules.DENIZENS", "CourtRules.MEMBERS", "KindredRules.KINDRED", "WardRules.WARDED"):
    if _roster not in _eggs_src:
        fail(f"BeastItems registers no spawn eggs for {_roster}, so those creatures have an egg's "
             f"model and name and no egg")

if problems:
    print(f"{len(problems)} problem(s):")
    for problem in problems:
        print(f"  - {problem}")
    sys.exit(1)
print("all bestiary checks passed")
