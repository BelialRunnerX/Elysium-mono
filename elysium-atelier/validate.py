#!/usr/bin/env python3
"""
Validate Elysium Atelier against its sources.

Run from anywhere:   python3 elysium-atelier/validate.py

Thirty jars in this pack exist to add chairs, shelves and lattices. Between
them they add several thousand blocks and one recurring bug: a way of turning
a material into a shape and back again that comes out ahead. It is never
deliberate — somebody writes "four planks make four chairs" because four is a
nice number, somebody else writes "a chair gives a plank back" because it would
be annoying not to, and nobody reads the four hundred recipes that result.

  1. INDEPENDENCE. The library, and nothing else at all.
  2. PURITY, and the harness run rather than read.
  3. THE PRICES. No shape, and no pair of recipes, that pays.
  4. THE CATALOGUE. Two hundred blocks, generated rather than typed.
  5. THE GEOMETRY. What you see and what you walk into, from one place.
  6. THE SEAM.
  7. THE JOINS. The tags they ship, and the states they are drawn with.

The five joining kinds are a different question from the twelve shapes. A
chair is a shape; a fence is a *relation*, and everything that goes wrong with
one goes wrong between blocks rather than inside one — in tags, which are only
ever wrong about what is missing from them, and in blockstates, which are only
ever wrong about one rotation out of thirty-two. So both are compared against
something outside this module: the tags against what the rules derive, and the
states and models against the game's own, read out of the jar. That comparison
found three bugs the first time it ran.
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
SRC = os.path.join(WORK, "src/main/java/com/elysium/atelier")
PURE = os.path.join(SRC, "rules")
BELOW = []
HARNESS = os.path.join(WORK, "tools/AtelierHarness.java")
HARNESS_MAIN = "com.elysium.atelier.rules.AtelierHarness"
GENERATOR = os.path.join(WORK, "tools/gen_atelier.py")

#: How long the arithmetic is allowed to take. Two million checks run in a few
#: seconds; anything past this is a loop.
HARNESS_SECONDS = 120
NS = "elysiumatelier"

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
    failed, and this project has been bitten by that a dozen times.
    
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
    newest feature is the broken one — the failures fell off the end and the
    validator reported a wall of successful headings instead.
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
    print("This is not the elysium-atelier tree.")
    sys.exit(1)

print("=" * 70)
print("elysium-atelier — the shapes a house is made of, and the proof that none of "
      "them makes wood out of nothing")
print("=" * 70)

# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------

FOREIGN = re.compile(
    r"(?:com\.mcwfurnitures|sfw\.|mcwdoors|earthcomputer|com\.ordana)"
    r"|\b(?:mcwfurnitures|mcwdoors|chipped|handcrafted|supplementaries|amendments)\s*:",
    re.I)
UPWARD = re.compile(r"com\.elysium\.(arsenal|bestiary|wilds|colonies)")

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
        if used not in ("atelier", "lib"):
            fail(f"{where} names com.elysium.{used}; every shape here is cut from a "
                 f"vanilla material and wears a vanilla face, so this module adds two "
                 f"hundred blocks and not one dependency")

toml = read(RES, "META-INF/neoforge.mods.toml")
declared = set(re.findall(r'modId="([^"]+)"', toml))
allowed = {"${mod_id}", "neoforge", "minecraft", "elysiumlib"}
for mod in declared - allowed:
    fail(f"the module declares a dependency on {mod}; every block it adds is cut from "
         f"a vanilla material and must not need anything else")
for needed in ("neoforge", "minecraft", "elysiumlib"):
    if needed not in declared:
        fail(f"the module does not declare {needed}")

print(f"independence       : {len(java_files)} source file(s), nothing it replaces "
      f"named, and not one dependency beyond the game")

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
                 f"so a harness can price two hundred blocks without a game")
    for used in re.findall(r"import\s+(com\.elysium\.[\w.]+);", without_package):
        fail(f"rules/{name} imports {used}; the rules are the JDK alone")

JOIN_HARNESS = os.path.join(WORK, "tools/JoinHarness.java")
JOIN_MAIN = "com.elysium.atelier.rules.JoinHarness"
ARCH_HARNESS = os.path.join(WORK, "tools/ArchHarness.java")
ARCH_MAIN = "com.elysium.atelier.rules.ArchHarness"
COURSE_HARNESS = os.path.join(WORK, "tools/CourseHarness.java")
COURSE_MAIN = "com.elysium.atelier.rules.CourseHarness"
TRIM_HARNESS = os.path.join(WORK, "tools/TrimHarness.java")
TRIM_MAIN = "com.elysium.atelier.rules.TrimHarness"
CLOTH_HARNESS = os.path.join(WORK, "tools/ClothHarness.java")
CLOTH_MAIN = "com.elysium.atelier.rules.ClothHarness"
DIAG_HARNESS = os.path.join(WORK, "tools/DiagonalHarness.java")
DIAG_MAIN = "com.elysium.atelier.rules.DiagonalHarness"
HARVEST_HARNESS = os.path.join(WORK, "tools/HarvestHarness.java")
HARVEST_MAIN = "com.elysium.atelier.rules.HarvestHarness"

join_out = ""
arch_out = ""
course_out = ""
trim_out = ""
cloth_out = ""
diag_out = ""
harvest_out = ""
for where in (HARNESS, JOIN_HARNESS, ARCH_HARNESS, COURSE_HARNESS, TRIM_HARNESS,
              CLOTH_HARNESS, DIAG_HARNESS, HARVEST_HARNESS):
    if not os.path.isfile(where):
        fail(f"{os.path.basename(where)} is gone, so what it proved is now checked by "
             f"reading, which nobody has ever done")

if os.path.isfile(HARNESS) and os.path.isfile(JOIN_HARNESS) \
        and os.path.isfile(ARCH_HARNESS) and os.path.isfile(COURSE_HARNESS) \
        and os.path.isfile(TRIM_HARNESS) and os.path.isfile(CLOTH_HARNESS) \
        and os.path.isfile(DIAG_HARNESS) and os.path.isfile(HARVEST_HARNESS):
    with tempfile.TemporaryDirectory() as tmp:
        staged = os.path.join(tmp, "src")
        os.makedirs(staged)
        sources = [os.path.join(PURE, n) for n in os.listdir(PURE) if n.endswith(".java")]
        for source in sources + [HARNESS, JOIN_HARNESS, ARCH_HARNESS,
                                 COURSE_HARNESS, TRIM_HARNESS, CLOTH_HARNESS,
                                 DIAG_HARNESS, HARVEST_HARNESS]:
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
            for main, label in ((HARNESS_MAIN, "the arithmetic     "),
                                (JOIN_MAIN, "the joining        "),
                                (ARCH_MAIN, "the building       "),
                                (COURSE_MAIN, "the courses        "),
                                (TRIM_MAIN, "the fittings       "),
                                (CLOTH_MAIN, "the colours        "),
                                (DIAG_MAIN, "the diagonals      "),
                                (HARVEST_MAIN, "what mines it      ")):
                try:
                    ran = subprocess.run(
                        ["java", "-cp", os.path.join(tmp, "out"), main],
                        capture_output=True, text=True, timeout=HARNESS_SECONDS)
                except subprocess.TimeoutExpired:
                    fail(f"{main} did not finish within {HARNESS_SECONDS} seconds, so "
                         f"something in the rules can loop forever — which is a server "
                         f"that stops responding, not a wrong number")
                    continue
                if ran.returncode != 0:
                    fail(f"{main} failed:\n" + harness_trouble(ran.stdout))
                    continue
                if main == JOIN_MAIN:
                    join_out = ran.stdout
                if main == ARCH_MAIN:
                    arch_out = ran.stdout
                if main == COURSE_MAIN:
                    course_out = ran.stdout
                if main == TRIM_MAIN:
                    trim_out = ran.stdout
                if main == CLOTH_MAIN:
                    cloth_out = ran.stdout
                if main == DIAG_MAIN:
                    diag_out = ran.stdout
                if main == HARVEST_MAIN:
                    harvest_out = ran.stdout
                said = [line for line in ran.stdout.strip().splitlines()
                        if not line.startswith("TAG ")
                        and not line.startswith("OPENING ")
                        and not line.startswith("GEOM ")
                        and not line.startswith("COURSE ")
                        and not line.startswith("CTAG ")
                        and not line.startswith("TRIM ")
                        and not line.startswith("TTAG ")
                        and not line.startswith("PAD ")
                        and not line.startswith("CLOTH ")
                        and not line.startswith("DIAG ")
                        and not line.startswith("DTAG ")
                        and not line.startswith("HARVEST ")]
                print(label + ": " + said[-1])

rules = read(PURE, "AtelierRules.java")
plain = strip_comments(rules)
squished = squashed(plain)


def constant(name, kind=r"(?:float|int|double)"):
    m = re.search(r"public static final " + kind + r"\s+" + name + r"\s*=\s*([^;]+);",
                  plain, re.S)
    return " ".join(m.group(1).split()) if m else None








# ---------------------------------------------------------------------------
# 3. The prices
# ---------------------------------------------------------------------------
#
# Two ways this pays, and the second is the one that is actually missed: a
# recipe can lose units and the *pair* still pay, if breaking gives back more
# than the share of a block the shape cost.

whole = constant("WHOLE", "int")
if whole != "8":
    fail("a block is not eight units, so every price in the module is in a unit nobody "
         "can halve in their head")

shape_guard = squashed(body_of(plain, "public Shape {"))
if "if (units < 1 || units > WHOLE * 2) {" not in shape_guard:
    fail("a shape may cost nothing, or more than two blocks; the first is free "
         "furniture and the second is a recipe nobody will ever make")

profit = squashed(body_of(plain, "public boolean profitable()"))
if "return yields() > spends();" not in profit:
    fail("what counts as a profitable shape is not what it makes against what it takes, "
         "so the check the whole module rests on is checking something else")

recovering = squashed(body_of(plain, "public int recovers()"))
if "return units / WHOLE;" not in recovering:
    fail("what breaking a shape gives back is not rounded down to whole blocks, so a "
         "shape worth six eighths hands back a whole plank and the pair is free wood")

recoverable = squashed(body_of(plain, "public static List<String> recoverable()"))
if "if (shape.recovers() * WHOLE * shape.made() > shape.spends()) {" not in recoverable:
    fail("nobody checks whether crafting a batch and breaking all of it comes back with "
         "more than it cost, which is the way this actually goes wrong: the recipe can "
         "lose units and the pair still pay")

shapes = re.findall(r'new Shape\("([a-z_]+)", (\d+), (\d+), (\d+)\)',
                    strip_comments(rules, keep_strings=True))
if len(shapes) < 10:
    fail(f"there are {len(shapes)} shapes, which is not a decoration module")
for name, units, made, of in shapes:
    units, made, of = int(units), int(made), int(of)
    if made * units > of * 8:
        fail(f"{name} makes {made * units} units out of {of * 8}, which is a loop a "
             f"player can ride for ever in a module nobody would think to look in")
    if (units // 8) * 8 * made > of * 8:
        fail(f"crafting {made} {name}(s) costs {of * 8} units and breaking them all "
             f"gives back {(units // 8) * 8 * made}; the pair pays")
    if of * 8 - made * units > 8:
        fail(f"{name} wastes {of * 8 - made * units} units a craft, which is more than "
             f"a whole block, and nobody will ever make one")

print(f"the prices         : {len(shapes)} shapes, none of them profitable to craft, "
      f"none of them profitable to craft and break, and none wasting a whole block")

# ---------------------------------------------------------------------------
# 4. The catalogue
# ---------------------------------------------------------------------------

# Every material, from every place the rules build one: the loops that call a
# helper, and the three built by hand. Reading only the *first* loop was fine
# while there was one and silently checked thirteen of thirty-three the day
# there were two — and a catalogue check that looks at a subset passes.
plain_rules = strip_comments(rules, True)
declared = []
for listed, _helper in re.findall(
        r'for \(String name : List\.of\(([^)]*)\)\) \{\s*found\.add\((\w+)\(',
        plain_rules, re.S):
    declared.extend(re.findall(r'"([a-z_]+)"', listed))
for group in re.findall(r'stone\("([a-z_]+)", "[a-z_]+"\)|new Material\("([a-z_]+)"',
                        plain_rules):
    name = next((part for part in group if part), "")
    if name and name != "name" and name not in declared:
        declared.append(name)
if len(declared) < 20:
    fail(f"this validator could only find {len(declared)} materials in the rules, "
         f"and a catalogue check that looks at a subset of the catalogue passes "
         f"whatever is wrong with the rest of it")

blocks_src = squashed(strip_comments(read(SRC, "registry/AtelierBlocks.java"), True))
if "for (AtelierRules.Material material : AtelierRules.MATERIALS) { for (AtelierRules.Shape shape : AtelierRules.SHAPES) {" \
        not in blocks_src:
    fail("the blocks are not registered from the catalogue, so a thirteenth shape is "
         "sixteen more declarations and sixteen more chances to give one of them the "
         "wrong name")
if 'BLOCKS.register(id, () -> new ShapedBlock(properties, shape.name()))' not in blocks_src:
    fail("the shape a block is is not the shape it was registered under, so a chair "
         "can have a shelf's collision box")

items_src = squashed(strip_comments(read(SRC, "registry/AtelierItems.java"), True))
if "ITEMS.registerSimpleBlockItem(entry.getValue())" not in items_src:
    fail("the items are not registered from the blocks, so a block can exist with no "
         "item and only a command can place it")

lang = {}
lang_path = os.path.join(RES, "assets", NS, "lang/en_us.json")
if os.path.isfile(lang_path):
    lang = json.load(open(lang_path, encoding="utf-8"))
else:
    fail("there is no language file, so two hundred blocks are called by their ids")

expected = [f"{material}_{name}" for material in declared for name, _, _, _ in shapes]
missing_model = []
for id in expected:
    for kind, where in (("a model", f"assets/{NS}/models/block/{id}.json"),
                        ("an item model", f"assets/{NS}/models/item/{id}.json"),
                        ("a blockstate", f"assets/{NS}/blockstates/{id}.json"),
                        ("a drop", f"data/{NS}/loot_table/blocks/{id}.json"),
                        ("a recipe", f"data/{NS}/recipe/{id}.json")):
        if not os.path.isfile(os.path.join(RES, where)):
            missing_model.append(f"{id} has no {kind}")
    if f"block.{NS}.{id}" not in lang:
        missing_model.append(f"{id} has no name in the language file")
if missing_model:
    fail(f"{len(missing_model)} of {len(expected) * 6} things a block needs are missing: "
         + ", ".join(missing_model[:5]) + (" ..." if len(missing_model) > 5 else ""))

mineable = read(RES, "data/minecraft/tags/block/mineable/axe.json") \
    + read(RES, "data/minecraft/tags/block/mineable/pickaxe.json")
unmineable = [id for id in expected if f"{NS}:{id}" not in mineable]
if unmineable:
    fail(f"{len(unmineable)} block(s) are in no mineable tag, so they take a fist and "
         f"drop nothing quickly: " + ", ".join(unmineable[:4]))

print(f"the catalogue      : {len(expected)} block(s), every one modelled, stated, "
      f"dropped, named, craftable and mineable, and every one of them registered from "
      f"a loop")

# ---------------------------------------------------------------------------
# 5. The geometry
# ---------------------------------------------------------------------------
#
# The model a player sees and the box they walk into are two descriptions of
# one block. Two files that must agree are one file too many, so they are
# compared here rather than trusted.

generator = read(GENERATOR)
geometry = {}
table = re.search(r"GEOMETRY = \{(.*?)\n\}", generator, re.S)
if not table:
    fail("the generator has no geometry table, so nothing here can compare it with "
         "anything")
else:
    for name, boxes in re.findall(r'"([a-z_]+)": \[(.*?)\],\n', table.group(1) + "\n",
                                  re.S):
        found = [tuple(int(part) for part in box.split(","))
                 for box in re.findall(r"\(([^)]*)\)", boxes)]
        if found:
            geometry[name] = found

java_boxes = {}
for name, numbers in re.findall(r'case "([a-z_]+)" -> (?:Shapes\.or\()?(.*?);\n',
                                strip_comments(read(SRC, "block/ShapedBlock.java"), True),
                                re.S):
    found = [tuple(int(float(part)) for part in box.replace("D", "").split(","))
             for box in re.findall(r"Block\.box\(([^)]*)\)", numbers)]
    if found:
        java_boxes[name] = found

if not java_boxes:
    fail("no collision boxes could be read out of the block, so nothing below compares "
         "anything")
for name in sorted(java_boxes):
    if name not in geometry:
        fail(f"{name} has a collision box and no model geometry")
        continue

    def hull(boxes):
        return (min(b[0] for b in boxes), min(b[1] for b in boxes),
                min(b[2] for b in boxes), max(b[3] for b in boxes),
                max(b[4] for b in boxes), max(b[5] for b in boxes))

    if hull(java_boxes[name]) != hull(geometry[name]):
        fail(f"{name}'s model fills {hull(geometry[name])} and its collision box fills "
             f"{hull(java_boxes[name])}; a block you can see through and cannot walk "
             f"past, or the other way about")

full = [name for name, _, _, _ in shapes if name not in java_boxes]
for name in full:
    if name in geometry and geometry[name] != [(0, 0, 0, 16, 16, 16)]:
        fail(f"{name} has no collision box of its own — so it is a whole block — and a "
             f"model that is {geometry[name]}, which is a block you can see through and "
             f"cannot walk past")

upright = re.search(r"UPRIGHT = List\.of\(([^)]*)\)",
                    strip_comments(read(SRC, "registry/AtelierBlocks.java"), True))
upright_java = set(re.findall(r'"([a-z_]+)"', upright.group(1) if upright else ""))
upright_gen = set(re.findall(r'"([a-z_]+)"',
                             re.search(r"UPRIGHT = \[([^\]]*)\]", generator).group(1)))
if upright_java != upright_gen:
    fail(f"the blocks that stand along an axis are {sorted(upright_java)} in the Java "
         f"and {sorted(upright_gen)} in the generator, so one of them gets a blockstate "
         f"for properties it does not have and will not load")

print(f"the geometry       : {len(java_boxes)} shape(s) whose model and collision box "
      f"are the same size, and {len(full)} that are whole blocks in both")

# ---------------------------------------------------------------------------
# 5b. Generated, and still what the generator writes
# ---------------------------------------------------------------------------

if not os.path.isfile(GENERATOR):
    fail("there is no generator, so two hundred blocks are kept by hand")
else:
    with tempfile.TemporaryDirectory() as tmp:
        environment = dict(os.environ, ELYSIUM_ATELIER_OUT=tmp)
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
                     f"writes: " + ", ".join(sorted(drifted)[:6]))

# Every recipe's grid has to use exactly the number of blocks the price is
# worked out from, or the price is about a recipe nobody ships.
patterns = re.search(r"PATTERNS = \{(.*?)\n\}", generator, re.S)
if not patterns:
    fail("the generator has no recipe patterns")
else:
    laid = {}
    for name, rows in re.findall(r'"([a-z_]+)": \[([^\]]*)\]', patterns.group(1)):
        grid = re.findall(r'"([^"]*)"', rows)
        used = sum(row.count("M") for row in grid)
        shape = next((s for s in shapes if s[0] == name), None)
        if shape and used != int(shape[3]):
            fail(f"{name}'s grid uses {used} blocks and it is priced from "
                 f"{shape[3]}; the price is about a recipe nobody ships")
        key = min("|".join(grid), "|".join(row[::-1] for row in grid))
        if key in laid:
            fail(f"{name} and {laid[key]} are laid out the same way, so they are one "
                 f"recipe and which you get is whichever was listed first")
        laid[key] = name

print("generated          : every model, state, drop, recipe and name written by the "
      "generator, and every grid using exactly what its price is worked out from")

# ---------------------------------------------------------------------------
# 6. The seam
# ---------------------------------------------------------------------------

main_class = read(SRC, "ElysiumAtelier.java")
if f'MODID = "{NS}"' not in main_class:
    fail("the module id is not what everything else in the tree spells it")
for registered in ("AtelierBlocks.BLOCKS.register", "AtelierItems.ITEMS.register",
                   "AtelierItems.TABS.register"):
    if registered not in main_class:
        fail(f"{registered} is never called, so part of the module is written and none "
             f"of it exists")

# ---------------------------------------------------------------------------
# 7. The joins: the tags they ship, and the states they are drawn with
# ---------------------------------------------------------------------------
#
# A fence, a gate, a pane, a door and a trapdoor are not decorations that sit
# near things — joining is what they are for. And none of that is written in
# the block; it is written in tags, which are only ever wrong about what is
# missing from them, and in blockstates, which are only ever wrong about one
# rotation out of thirty-two.

join_rules = read(PURE, "JoinRules.java")
join_plain = strip_comments(join_rules)

already = squashed(body_of(join_plain, "public static List<Opening> catalogue()"))
if "if (alreadyInTheGame(kind, material)) { continue; }" not in already:
    fail("the catalogue no longer leaves out what the game already ships, so this "
         "module adds eleven materials' worth of wooden doors that have been in "
         "Minecraft since 2011 — which its own shape list says in so many words is "
         "adding nothing")

guard = squashed(read(SRC, "registry/AtelierBlocks.java"))
if "if (set.canOpenByHand() != JoinRules.openedByHand(opening)" not in guard:
    fail("nothing makes the game's own block set and our rules agree about whether a "
         "hand opens a door, and those two are read by different parts of the game "
         "with neither checking the other")

# What the rules say each block's tags are, against the tag files that shipped.
# Against the rules rather than against the generator: comparing generated
# files to the generator only proves it agrees with itself.
wanted = {}
joining = set()
for line in join_out.splitlines():
    parts = line.split()
    if len(parts) == 2 and parts[0] == "OPENING":
        joining.add(f"{NS}:{parts[1]}")
    if len(parts) == 3 and parts[0] == "TAG":
        # The rules name blocks, not files, so they say "stone_fence" where a
        # tag file says "elysiumatelier:stone_fence". The namespace is added
        # here rather than there because it is a fact about the file format
        # and the rules do not know what a file is.
        wanted.setdefault(parts[1], set()).add(f"{NS}:{parts[2]}")

if join_out and not wanted:
    fail("the join harness no longer prints the tags it derived, so the tag files that "
         "ship are believed rather than checked")

for tag, blocks in sorted(wanted.items()):
    namespace, path = tag.split(":", 1)
    where = os.path.join(RES, "data", namespace, "tags/block", path + ".json")
    if not os.path.isfile(where):
        fail(f"{tag} is a tag {len(blocks)} of our blocks need and no such file ships, "
             f"so every one of them is a block the game does not know the kind of")
        continue
    shipped = set(json.loads(read(where)).get("values", []))
    for block in sorted(blocks - shipped):
        fail(f"{block} needs to be in {tag} and is not; the block will work, it will "
             f"simply not join, or a villager will not walk through it, and nobody "
             f"ever finds that by reading a tag file")
    # Only the joining blocks: the two mineable tags also carry the two
    # hundred shapes, which are none of this section's business.
    strays = {b for b in shipped - blocks if b in joining}
    for block in sorted(strays):
        fail(f"{block} is in {tag} and the rules do not put it there")

# And the blockstates, against the game's own. A rotation derived by hand came
# out wrong for two of these five: the trapdoor turned its top half the wrong
# way, and the pane put its four blank sides on the wrong sides, which is a
# pane with holes in it.
# The jar is wherever this machine keeps it: MC_JAR, or the first of the known
# places (the container's mapped jar, the Windows assets jar); until 29 Sep 2026
# only the container's path was tried and on Windows these comparisons never ran.
VANILLA_JAR = next((_j for _j in [os.environ.get("MC_JAR", ""), "/tmp/mc/mapped-at.jar",
                                  "/tmp/mc/libs/vanilla-assets.jar", "C:/tmp/mc/libs/vanilla-assets.jar"]
                    if _j and os.path.isfile(_j)), "/tmp/mc/mapped-at.jar")
SAME_AS = {"door": "oak_door", "trapdoor": "oak_trapdoor", "gate": "oak_fence_gate",
           "fence": "oak_fence", "pane": "glass_pane"}
if not os.path.isfile(VANILLA_JAR):
    fail(f"the joins' blockstates cannot be compared with the game's: no game jar here "
         f"(not MC_JAR, not {VANILLA_JAR}, not vanilla-assets.jar under /tmp/mc/libs); "
         f"set MC_JAR to vanilla-assets.jar or the 1.21.1 client jar, or run "
         f"python3 tools/setup_env.py and use the MC_JAR it exports")
else:
    import zipfile
    compared = 0
    with zipfile.ZipFile(VANILLA_JAR) as jar:
        for kind, theirs in sorted(SAME_AS.items()):
            ours = "stone_" + kind
            mine = os.path.join(RES, "assets", NS, "blockstates", ours + ".json")
            if not os.path.isfile(mine):
                fail(f"{ours} has no blockstate, so it is drawn as nothing")
                continue
            try:
                vanilla = jar.read(f"assets/minecraft/blockstates/{theirs}.json")
            except KeyError:
                fail(f"the game has no {theirs} to compare {ours} against")
                continue
            got = json.dumps(json.loads(read(mine)), sort_keys=True) \
                .replace(f"{NS}:block/{ours}", f"minecraft:block/{theirs}")
            want = json.dumps(json.loads(vanilla), sort_keys=True)
            if got != want:
                fail(f"{ours} is not drawn the way the game draws a {theirs}. One "
                     f"rotation out of thirty-two is a door that opens through its own "
                     f"frame, and it is not something anybody sees by reading the file")
            compared += 1
    # And the models under those states. A blockstate that names the right
    # model is no use when the model is built on the wrong template: a pane
    # whose blank sides parent the alternate piece is a pane with a seam down
    # it, and the blockstate is perfect.
    SAME_MODELS = {}
    for half in ("bottom", "top"):
        for hinge in ("left", "right"):
            for shut in ("", "_open"):
                SAME_MODELS[f"stone_door_{half}_{hinge}{shut}"] = \
                    f"oak_door_{half}_{hinge}{shut}"
    for part in ("bottom", "top", "open"):
        SAME_MODELS[f"stone_trapdoor_{part}"] = f"oak_trapdoor_{part}"
    for part in ("", "_open", "_wall", "_wall_open"):
        SAME_MODELS[f"stone_gate{part}"] = f"oak_fence_gate{part}"
    for part in ("post", "side", "inventory"):
        SAME_MODELS[f"stone_fence_{part}"] = f"oak_fence_{part}"
    for part in ("post", "side", "side_alt", "noside", "noside_alt"):
        SAME_MODELS[f"stone_pane_{part}"] = f"glass_pane_{part}"

    checked = 0
    with zipfile.ZipFile(VANILLA_JAR) as jar:
        for ours, theirs in sorted(SAME_MODELS.items()):
            mine = os.path.join(RES, "assets", NS, "models/block", ours + ".json")
            if not os.path.isfile(mine):
                fail(f"{ours} has no model, so the state that names it draws nothing")
                continue
            try:
                vanilla = json.loads(jar.read(
                    f"assets/minecraft/models/block/{theirs}.json"))
            except KeyError:
                fail(f"the game has no model {theirs} to compare {ours} against")
                continue
            got = json.loads(read(mine)).get("parent")
            want = vanilla.get("parent")
            if got != want:
                fail(f"{ours} is built on {got} and the game builds a {theirs} on "
                     f"{want}; the state naming it is perfect and the piece is the "
                     f"wrong shape")
            checked += 1

    if compared:
        print(f"the joins          : {len(wanted)} tag file(s) matching the rules "
              f"block for block, {compared} blockstate(s) identical to the game's own, "
              f"and {checked} model(s) built on the templates the game builds them on")

# ---------------------------------------------------------------------------
# The architectural pieces
# ---------------------------------------------------------------------------
#
# Six hundred and ninety-three blocks whose models are drawn by a table in the
# generator and whose collision comes from the rules. Two descriptions of one
# geometry, and a block whose model and collision differ is one you fall
# through or cannot step onto, looking completely correct from every angle.

arch = strip_comments(read(PURE, "ArchRules.java"), True)
arch_pieces = re.findall(r'new Piece\(\s*"([a-z_]+)"', arch)
pitches = re.findall(r'"([a-z]+)"',
                     re.search(r"PITCHES =\s*List\.of\(([^)]*)\)", arch).group(1))
roof_roles = re.findall(r"found\.add\(roof\(pitch, (\w+),", arch)
for pitch in pitches:
    for role in roof_roles:
        arch_pieces.append(f"{pitch}_roof_{role.lower()}")
joining = {name for name in re.findall(
    r'new Piece\(\s*"([a-z_]+)"[^)]*?,\s*true\s*\)', arch, re.S)}

if len(arch_pieces) < 10:
    fail(f"this validator found {len(arch_pieces)} architectural pieces in the "
         f"rules, and a catalogue check that looks at a handful of the catalogue "
         f"passes whatever is wrong with the rest of it")

absent = []
for material in declared:
    for piece in arch_pieces:
        id = f"{material}_{piece}"
        wanted = [f"assets/{NS}/blockstates/{id}.json",
                  f"assets/{NS}/models/item/{id}.json",
                  f"data/{NS}/loot_table/blocks/{id}.json",
                  f"data/{NS}/recipe/{id}.json"]
        if piece not in joining:
            wanted.append(f"assets/{NS}/models/block/{id}.json")
        for where in wanted:
            if not os.path.isfile(os.path.join(RES, where)):
                absent.append(f"{id} has no {where.rsplit('/', 2)[-2]}")
        if f"block.{NS}.{id}" not in lang:
            absent.append(f"{id} has no name, so it is called by its id")
if absent:
    fail(f"{len(absent)} of {len(declared) * len(arch_pieces) * 5} things an "
         f"architectural block needs are missing: " + ", ".join(absent[:6]))

# The geometry, compared against what the harness printed out of the rules the
# block itself reads. This is the only thing standing between the two
# descriptions drifting apart.
printed = {}
for line in arch_out.splitlines():
    if not line.startswith("GEOM "):
        continue
    parts = line.split()
    printed[parts[1]] = [tuple(int(n) for n in box.split(",")) for box in parts[2:]]
if not printed:
    fail("the harness printed no geometry, so the models are compared against "
         "nothing and the check passes by having no opinion")
else:
    for piece in arch_pieces:
        if piece not in printed:
            fail(f"{piece} has no geometry from the rules, so its model is checked "
                 f"against nothing")
            continue
        if piece in joining:
            continue
        body = read(RES, "assets", NS, f"models/block/{declared[0]}_{piece}.json")
        if not body:
            continue
        drawn = [tuple(json.loads(body)["elements"][n]["from"]
                       + json.loads(body)["elements"][n]["to"])
                 for n in range(len(json.loads(body)["elements"]))]
        if drawn != printed[piece]:
            fail(f"{piece} is drawn as {drawn} and collides as {printed[piece]}, so "
                 f"it is a block you can fall through or cannot step onto and it "
                 f"looks correct from every angle")

pieces_src = squashed(strip_comments(read(SRC, "registry/AtelierBlocks.java"), True))
if "for (AtelierRules.Material material : AtelierRules.MATERIALS) { for " \
        "(ArchRules.Piece piece : ArchRules.PIECES) {" not in pieces_src:
    fail("the architectural blocks are not registered from the two rosters, so a "
         "twenty-second piece is a block that does not exist and nothing says so")
if "if (made.size() != ArchRules.howMany()) { throw new IllegalStateException(" \
        not in pieces_src:
    fail("nothing counts the architectural blocks registered against the roster "
         "they were walked from")
if "properties = properties.lightLevel(state -> piece.light());" not in pieces_src:
    fail("a light's brightness is not read off the piece, so the ladder the rules "
         "prove climbs is not the ladder a player sees in a dark room")

items_src = squashed(strip_comments(read(SRC, "registry/AtelierItems.java"), True))
if "java.util.List.of(AtelierBlocks.ALL, AtelierBlocks.OPENINGS, " \
        "AtelierBlocks.PIECES, AtelierBlocks.COURSES, AtelierBlocks.FITTINGS, " \
        "AtelierBlocks.CLOTH, AtelierBlocks.DIAGONALS)) {" not in items_src:
    fail("the seven rosters of blocks are not all walked for items, so one of them "
         "is blocks a player can only place with a command — which in a decoration "
         "module is a block that does not exist")
if "int blocks = AtelierBlocks.ALL.size() + AtelierBlocks.OPENINGS.size() + " \
        "AtelierBlocks.PIECES.size() + AtelierBlocks.COURSES.size() + " \
        "AtelierBlocks.FITTINGS.size() + AtelierBlocks.CLOTH.size() + " \
        "AtelierBlocks.DIAGONALS.size();" not in items_src:
    fail("the count the items are checked against does not add up all seven "
         "rosters, so a roster left out of the loop above would not be noticed")

print(f"the building       : {len(arch_pieces)} piece(s) in {len(declared)} "
      f"material(s) = {len(arch_pieces) * len(declared)} block(s), every one drawn "
      f"exactly as it collides, stated, dropped, named, craftable and registered "
      f"from a loop")

# ---------------------------------------------------------------------------
# The courses
# ---------------------------------------------------------------------------
#
# The cuts of a block and the ways up one: three hundred and sixty-three
# blocks whose prices are not written down anywhere but counted off their own
# models. So the three things that can be wrong here are all about agreement
# rather than about a number — the shape the rules price against the shape the
# generator draws, the list of what the game already has against the game, and
# the four cuts the game knows against the way the game draws them.

# Every material, with the vanilla block it is cut from and whether a hand
# alone works it. Read the same three ways the catalogue above reads them: the
# loops that call a helper, and the ones written out by hand.
course_materials = {}
#: What each helper actually builds, read out of the helper rather than
#: assumed: wood() makes planks out of a name and stone() makes the block of
#: that name, and a validator that assumes the first would call an oak
#: vertical slab a cut of minecraft:oak, which is not a block.
helpers = {}
for helper, suffix, soft in re.findall(
        r'private static Material (\w+)\(String name[^)]*\) \{\s*'
        r'return new Material\(name,\s*"minecraft:" \+ name'
        r'(?:\s*\+\s*"([a-z_]+)")?,.*?(true|false)\);',
        plain_rules, re.S):
    helpers[helper] = (suffix, soft == "true")
for listed, helper in re.findall(
        r'for \(String name : List\.of\(([^)]*)\)\) \{\s*found\.add\((\w+)\(',
        plain_rules, re.S):
    if helper not in helpers:
        fail(f"this validator cannot read what {helper}() builds a material out "
             f"of, so every stonecutting recipe it checks is checked against a "
             f"guess at the ingredient")
        continue
    suffix, soft = helpers[helper]
    for name in re.findall(r'"([a-z_]+)"', listed):
        course_materials[name] = ("minecraft:" + name + suffix, soft)
for name, block, _face, soft in re.findall(
        r'new Material\("([a-z_]+)",\s*"([a-z:_]+)",\s*"([a-z:/_]+)",\s*(true|false)\)',
        plain_rules, re.S):
    course_materials[name] = (block, soft == "true")
for name in declared:
    if name not in course_materials:
        fail(f"this validator cannot say what {name} is cut from or whether a hand "
             f"works it, so every tag it checks below is checked against a guess")
        course_materials[name] = ("minecraft:" + name, False)

course = strip_comments(read(PURE, "CourseRules.java"), True)
course_kinds = []
course_named = dict(re.findall(
    r'public static final String (\w+)\s*=\s*"([a-z_]+)";', course))
for name, family, role, made, of, joins in re.findall(
        r'new Kind\(\s*(\w+),\s*(\w+),\s*("[a-z]*"|\w+),\s*(\d+),\s*(\d+),'
        r'\s*(true|false)\s*\)', course, re.S):
    course_kinds.append((course_named[name], course_named[family],
                         int(made), int(of), joins == "true"))
if len(course_kinds) != len(re.findall(r"new Kind\(", course)):
    fail(f"this validator read {len(course_kinds)} of "
         f"{len(re.findall(r'new Kind', course))} courses out of the rules, so "
         f"whatever it says below is about a smaller catalogue than the one that "
         f"ships")

course_already = {}
_block = re.search(r"VANILLA_ALREADY = Map\.of\((.*?)\);", course, re.S)
if not _block:
    fail("the course rules no longer say what the game already has, so this module "
         "would add a second oak stair to a game that has had one since 2011")
else:
    for name, listed in re.findall(r'(\w+),\s*Set\.of\(([^)]*)\)', _block.group(1)):
        course_already[course_named[name]] = set(re.findall(r'"([a-z_]+)"', listed))
course_spelt = dict(re.findall(r'"([a-z_]+)",\s*"([a-z_]+)"',
                               re.search(r"AS_THE_GAME_SPELLS_IT = Map\.of\((.*?)\);",
                                         course, re.S).group(1)))
course_stonecut = {course_named[name] for name in re.findall(
    r"\b([A-Z_]+)\b",
    re.search(r"STONECUT =\s*List\.of\(([^)]*)\);", course, re.S).group(1))}

# What the game already has, against the game. JoinRules has the same list for
# doors and only ever checked that it was *used*, which would not catch a
# material the game gained later and would not catch one typed wrong here.
if not os.path.isfile(VANILLA_JAR):
    fail(f"what the game already cuts cannot be checked against the game: no game jar "
         f"here (not MC_JAR, not {VANILLA_JAR}, not vanilla-assets.jar under /tmp/mc/libs); "
         f"set MC_JAR to vanilla-assets.jar or the 1.21.1 client jar, or run "
         f"python3 tools/setup_env.py and use the MC_JAR it exports")
else:
    import zipfile as _zip
    with _zip.ZipFile(VANILLA_JAR) as jar:
        theirs = {n.split("/")[-1][:-5] for n in jar.namelist()
                  if n.startswith("assets/minecraft/blockstates/")
                  and n.endswith(".json")}
    suffix = {"stair": "_stairs", "slab": "_slab", "wall": "_wall"}
    wrong = []
    for kind, tail in suffix.items():
        claimed = course_already.get(kind, set())
        for material in declared:
            spelt = course_spelt.get(material, material)
            in_game = (spelt + tail) in theirs
            if in_game and material not in claimed:
                wrong.append(f"the game has a {spelt}{tail} and this module adds "
                             f"a second one")
            if not in_game and material in claimed:
                wrong.append(f"the game is said to have a {spelt}{tail} and has "
                             f"no such block, so that cut is simply missing")
    for kind in course_already:
        if kind not in suffix and course_already[kind]:
            wrong.append(f"the game is said to have {kind}s and the game has "
                         f"never had one of any material")
    if wrong:
        fail(f"{len(wrong)} disagreement(s) between what the game has and what "
             f"this module thinks it has: " + "; ".join(sorted(set(wrong))[:4]))
    else:
        print(f"the cuts           : {sum(len(v) for v in course_already.values())} "
              f"cut(s) the game already ships, every one of them checked against "
              f"the game's own blockstates and left out")

# Every course, with everything a block needs.
course_ids = []
for material in declared:
    for kind, _family, _made, _of, _joins in course_kinds:
        if material in course_already.get(kind, set()):
            continue
        course_ids.append((material, kind, f"{material}_{kind}"))
if len(course_ids) < 100:
    fail(f"this validator found {len(course_ids)} courses, and a catalogue check "
         f"that looks at a handful of the catalogue passes whatever is wrong with "
         f"the rest of it")

missing = []
for material, kind, id in course_ids:
    wanted = [f"assets/{NS}/blockstates/{id}.json",
              f"assets/{NS}/models/item/{id}.json",
              f"data/{NS}/loot_table/blocks/{id}.json",
              f"data/{NS}/recipe/{id}.json"]
    for where in wanted:
        if not os.path.isfile(os.path.join(RES, where)):
            missing.append(f"{id} has no {where.rsplit('/', 2)[-2]}")
    if f"block.{NS}.{id}" not in lang:
        missing.append(f"{id} has no name, so it is called by its id")
if missing:
    fail(f"{len(missing)} of {len(course_ids) * 5} things a course needs are "
         f"missing: " + ", ".join(missing[:6]))

# A stonecutter takes one of its ingredient. A recipe claiming to take two is
# one the game loads, ignores the second of, and hands out the difference.
cutting = 0
for material, kind, id in course_ids:
    body = read(RES, "data", NS, f"recipe/{id}.json")
    if not body:
        continue
    recipe = json.loads(body)
    if kind in course_stonecut:
        cutting += 1
        if recipe.get("type") != "minecraft:stonecutting":
            fail(f"{id} is said to be cut on a stonecutter and its recipe is a "
                 f"{recipe.get('type')}, so it is on a grid this module has "
                 f"forty-five other recipes competing for")
            break
        cut_from = (recipe.get("ingredient") or {}).get("item")
        if cut_from != course_materials[material][0]:
            fail(f"{id} is cut from {cut_from} and it is made of "
                 f"{course_materials[material][0]}; a stonecutting recipe with "
                 f"the wrong ingredient, or none, is a recipe the player never "
                 f"sees and a block they can never get")
            break
        if recipe.get("result", {}).get("count", 0) < 1:
            fail(f"{id} is cut out of a block and gives nothing back")
            break
    elif recipe.get("type") != "minecraft:crafting_shaped":
        fail(f"{id} is crafted and its recipe is a {recipe.get('type')}")
        break
if not cutting:
    fail("not one course is cut on a stonecutter, so the check above is about "
         "a recipe type nothing in this module uses")

# The geometry, against what the harness printed out of the rules the block
# itself reads. The only thing standing between the two descriptions drifting.
course_printed = {}
for line in course_out.splitlines():
    if not line.startswith("COURSE "):
        continue
    parts = line.split()
    course_printed[parts[1]] = [tuple(int(n) for n in box.split(","))
                                for box in parts[2:]]
if not course_printed:
    fail("the course harness printed no geometry, so the models are compared "
         "against nothing and the check passes by having no opinion")
else:
    drawn_here = {kind for kind, _f, _m, _o, joins in course_kinds
                  if kind not in course_stonecut and not joins}
    for kind, _family, _made, _of, _joins in course_kinds:
        if kind not in course_printed:
            fail(f"{kind} has no geometry from the rules, so its model is checked "
                 f"against nothing")
            continue
        if kind not in drawn_here:
            continue
        first = next((id for material, k, id in course_ids if k == kind), None)
        if first is None:
            continue
        body = read(RES, "assets", NS, f"models/block/{first}.json")
        if not body:
            continue
        model = json.loads(body)
        drawn = [tuple(one["from"] + one["to"]) for one in model["elements"]]
        if drawn != course_printed[kind]:
            fail(f"{kind} is drawn as {drawn} and collides as "
                 f"{course_printed[kind]}, so it is a block you can fall through "
                 f"or cannot step onto and it looks correct from every angle")

# And the four cuts the game already knows, drawn the way the game draws them.
COURSE_SAME_AS = {"stair": ("stone_stairs", "stone"), "slab": ("stone_slab", "stone"),
                  "wall": ("cobblestone_wall", "cobblestone"),
                  "ladder": ("ladder", None)}
if os.path.isfile(VANILLA_JAR):
    import zipfile as _zip2
    copied = 0
    with _zip2.ZipFile(VANILLA_JAR) as jar:
        for kind, (theirs, their_block) in sorted(COURSE_SAME_AS.items()):
            ours = next((id for material, k, id in course_ids if k == kind), None)
            if ours is None:
                fail(f"there is no {kind} in the whole catalogue to compare with "
                     f"the game's own {theirs}")
                continue
            if kind == "ladder":
                # Ours is drawn out of its own boxes rather than the game's
                # one-texture plane, so only the state is the game's.
                pass
            mine = os.path.join(RES, "assets", NS, "blockstates", ours + ".json")
            if not os.path.isfile(mine):
                fail(f"{ours} has no blockstate, so it is drawn as nothing")
                continue
            try:
                vanilla = jar.read(f"assets/minecraft/blockstates/{theirs}.json")
            except KeyError:
                fail(f"the game has no {theirs} to compare {ours} against")
                continue
            got = json.dumps(json.loads(read(mine)), sort_keys=True) \
                .replace(f"{NS}:block/{ours}", f"minecraft:block/{theirs}")
            if their_block:
                material = ours[:-(len(kind) + 1)]
                whole = course_materials.get(material, ("", False))[0]
                if whole:
                    got = got.replace("minecraft:block/" + whole.split(":", 1)[1],
                                      f"minecraft:block/{their_block}")
            want = json.dumps(json.loads(vanilla), sort_keys=True)
            if got != want:
                fail(f"{ours} is not drawn the way the game draws a {theirs}. A "
                     f"stair has forty variants and one of them wrong is a corner "
                     f"you fall through from exactly one direction, and it is not "
                     f"something anybody sees by reading the file")
            copied += 1

# The models under those states. A blockstate naming the right model is no use
# when the model is built on the wrong template: a slab whose bottom half
# parents the top half is a slab that floats, and the blockstate is perfect.
COURSE_SAME_MODELS = {
    "stair": [("", "stone_stairs"), ("_inner", "stone_stairs_inner"),
              ("_outer", "stone_stairs_outer")],
    "slab": [("", "stone_slab"), ("_top", "stone_slab_top")],
    "wall": [("_post", "cobblestone_wall_post"), ("_side", "cobblestone_wall_side"),
             ("_side_tall", "cobblestone_wall_side_tall"),
             ("_inventory", "cobblestone_wall_inventory")],
}
if os.path.isfile(VANILLA_JAR):
    import zipfile as _zip3
    parented = 0
    with _zip3.ZipFile(VANILLA_JAR) as jar:
        for kind, parts in sorted(COURSE_SAME_MODELS.items()):
            ours = next((id for material, k, id in course_ids if k == kind), None)
            if ours is None:
                continue
            for suffix, theirs in parts:
                mine = os.path.join(RES, "assets", NS, "models/block",
                                    ours + suffix + ".json")
                if not os.path.isfile(mine):
                    fail(f"{ours}{suffix} has no model, so the state that names it "
                         f"draws nothing")
                    continue
                try:
                    vanilla = json.loads(jar.read(
                        f"assets/minecraft/models/block/{theirs}.json"))
                except KeyError:
                    fail(f"the game has no model {theirs} to compare "
                         f"{ours}{suffix} against")
                    continue
                got = json.loads(read(mine)).get("parent")
                want = vanilla.get("parent")
                if got != want:
                    fail(f"{ours}{suffix} is built on {got} and the game builds a "
                         f"{theirs} on {want}; the state naming it is perfect and "
                         f"the piece is the wrong shape")
                parented += 1
    if not parented:
        fail("not one course model was compared with the game's own, so the "
             "check above has no opinion about anything")

# A double slab is the material's own block and nobody else's. Getting it
# wrong is a slab that turns into somebody else's stone when you stack two.
for material, kind, id in course_ids:
    if kind != "slab":
        continue
    body = read(RES, "assets", NS, f"blockstates/{id}.json")
    if not body:
        continue
    whole = json.loads(body).get("variants", {}).get("type=double", {}).get("model")
    want = "minecraft:block/" + course_materials[material][0].split(":", 1)[1]
    if whole != want:
        fail(f"two {id}s stack into {whole} and the material is {want}; a slab "
             f"that turns into somebody else's block when you stack it is not "
             f"something anybody sees by reading the file")
        break

# The tags, against what the rules derive.
# Read out of the harness, which read them out of the rules, rather than
# derived a third time here. A third copy is a third thing to keep in step, and
# the copy that drifts is always the one nothing is compared against.
course_tags_want = {}
for line in course_out.splitlines():
    if not line.startswith("CTAG "):
        continue
    parts = line.split()
    for tag in parts[2:]:
        course_tags_want.setdefault(tag, set()).add(f"{NS}:{parts[1]}")
if len(course_tags_want) < 3:
    fail("the course harness printed tags for nothing, so the tag files below "
         "are compared against an empty list and the check has no opinion")
tagged = {id for values in course_tags_want.values() for id in values}
if len(tagged) != len(course_ids):
    fail(f"the rules put {len(tagged)} of {len(course_ids)} courses in a tag; a "
         f"course in no tag at all is mined with a fist and stacks with nothing")
# The four cuts the game already knows have tags the game itself relies on.
# Said here rather than only against what the rules derive, because a rule
# that says less is a rule nothing contradicts: dropping minecraft:slabs from
# the rules would drop it from the files and from the list they are compared
# against, all three in step and all three wrong.
MUST_BE_IN = {"stair": "minecraft:stairs", "slab": "minecraft:slabs",
              "wall": "minecraft:walls", "ladder": "minecraft:climbable"}
for material, kind, id in course_ids:
    tag = MUST_BE_IN.get(kind)
    if tag and f"{NS}:{id}" not in course_tags_want.get(tag, set()):
        fail(f"{id} is a {kind} and the rules do not put it in {tag}; a slab "
             f"outside the game's slab tag is a slab no other slab stacks with, "
             f"a ladder outside its climbable tag is one a player slides down, "
             f"and both read as a rendering bug")
        break

for tag, values in sorted(course_tags_want.items()):
    namespace, path = tag.split(":", 1)
    where = os.path.join(RES, "data", namespace, "tags/block", path + ".json")
    if not os.path.isfile(where):
        fail(f"{tag} has no file, so every course that needs it is mined with a "
             f"fist and stacks with nothing")
        continue
    have = set(json.loads(read(where)).get("values", []))
    absent = values - have
    if absent:
        fail(f"{len(absent)} course(s) are missing from {tag}: "
             f"{sorted(absent)[:3]} — a slab outside the game's slab tag is a slab "
             f"no other slab stacks with, and it reads as a rendering bug")

courses_src = squashed(strip_comments(read(SRC, "registry/AtelierBlocks.java"), True))
if "for (CourseRules.Course course : CourseRules.catalogue()) {" not in courses_src:
    fail("the courses are not registered by walking the catalogue, so a "
         "fourteenth kind is a block that does not exist and nothing says so")
if "if (made.size() != CourseRules.howMany()) { throw new IllegalStateException(" \
        not in courses_src:
    fail("nothing counts the courses registered against the catalogue they were "
         "walked from")
if "case CourseRules.STAIR -> BLOCKS.register(id, () -> new StairBlock(cut(from)," \
        " settled));" not in courses_src:
    fail("a stair is not registered as the game's own stair, so it does not turn "
         "its own corners and every corner in a staircase is a hole")
if "case CourseRules.LADDER -> BLOCKS.register(id, () -> new LadderBlock(settled));" \
        not in courses_src:
    fail("a ladder is not registered as the game's own ladder, so it is a block "
         "shaped like a ladder that a player slides down")

print(f"the courses        : {len(course_kinds)} kind(s) over {len(declared)} "
      f"material(s) = {len(course_ids)} block(s), {cutting} of them cut on a "
      f"stonecutter one block at a time, every one drawn exactly as it collides, "
      f"tagged, dropped, named and registered from the catalogue")

# ---------------------------------------------------------------------------
# The fittings
# ---------------------------------------------------------------------------
#
# Twelve shapes, three of which exist only in the eleven materials a hand can
# work. The interesting check is that last one: a crossing that is *not* every
# kind times every material is a crossing with a condition in it, and a
# condition a generator gets wrong writes three hundred and thirty blocks or
# three hundred and ninety-six and both look completely reasonable.

trim = strip_comments(read(PURE, "TrimRules.java"), True)
trim_named = dict(re.findall(
    r'public static final String (\w+)\s*=\s*"([a-z_]+)";', trim))
trim_kinds = []
for name, family, made, of, joins, climbs in re.findall(
        r'new Kind\(\s*(\w+),\s*(\w+),\s*(\d+),\s*(\d+),\s*(true|false),'
        r'\s*(true|false)\s*\)', trim, re.S):
    trim_kinds.append((trim_named[name], trim_named[family], int(made), int(of),
                       joins == "true", climbs == "true"))
if len(trim_kinds) != len(re.findall(r"new Kind\(", trim)):
    fail(f"this validator read {len(trim_kinds)} of "
         f"{len(re.findall(r'new Kind', trim))} fittings out of the rules, so "
         f"whatever it says below is about a smaller catalogue than the one that "
         f"ships")
trim_soft = trim_named.get("SOFT")
if not trim_soft:
    fail("the trim rules no longer name the family that is only made out of "
         "something soft, so a cushion of deepslate would be checked for and "
         "found")

trim_ids = []
trim_refused = []
for material in declared:
    soft = course_materials[material][1]
    for kind, family, _made, _of, joins, climbs in trim_kinds:
        if family == trim_soft and not soft:
            trim_refused.append(f"{material}_{kind}")
            continue
        trim_ids.append((material, kind, f"{material}_{kind}", joins, climbs))
if len(trim_ids) < 100:
    fail(f"this validator found {len(trim_ids)} fittings, and a catalogue check "
         f"that looks at a handful of the catalogue passes whatever is wrong with "
         f"the rest of it")
if not trim_refused:
    fail("every fitting is made in every material, so the rule that a cushion is "
         "cut out of something soft refuses nothing and is not a rule")

missing = []
for material, kind, id, joins, climbs in trim_ids:
    wanted = [f"assets/{NS}/blockstates/{id}.json",
              f"assets/{NS}/models/item/{id}.json",
              f"data/{NS}/loot_table/blocks/{id}.json",
              f"data/{NS}/recipe/{id}.json"]
    if not joins:
        wanted.append(f"assets/{NS}/models/block/{id}.json")
    for where in wanted:
        if not os.path.isfile(os.path.join(RES, where)):
            missing.append(f"{id} has no {where.rsplit('/', 2)[-2]}")
    if f"block.{NS}.{id}" not in lang:
        missing.append(f"{id} has no name, so it is called by its id")
if missing:
    fail(f"{len(missing)} of {len(trim_ids) * 5} things a fitting needs are "
         f"missing: " + ", ".join(missing[:6]))

# And the other direction: a cushion of deepslate would be a file nobody asked
# for, which is exactly what a crossing with a condition gets wrong.
strayed = [id for id in trim_refused
           if os.path.isfile(os.path.join(RES, "assets", NS, "blockstates",
                                          id + ".json"))]
if strayed:
    fail(f"{len(strayed)} fitting(s) exist that the rules refuse to make: "
         f"{strayed[:4]} — a cushion of deepslate is a joke the generator makes "
         f"on its own, in every material, and nobody sees it until they open the "
         f"creative menu")

# The geometry, against what the harness printed out of the rules.
trim_printed = {}
for line in trim_out.splitlines():
    if not line.startswith("TRIM "):
        continue
    parts = line.split()
    trim_printed[parts[1]] = [tuple(int(n) for n in box.split(","))
                              for box in parts[2:]]
if not trim_printed:
    fail("the trim harness printed no geometry, so the models are compared "
         "against nothing and the check passes by having no opinion")
else:
    for kind, _family, _made, _of, joins, _climbs in trim_kinds:
        if kind not in trim_printed:
            fail(f"{kind} has no geometry from the rules, so its model is checked "
                 f"against nothing")
            continue
        if joins:
            continue
        first = next((id for material, k, id, _j, _c in trim_ids if k == kind), None)
        if first is None:
            continue
        body = read(RES, "assets", NS, f"models/block/{first}.json")
        if not body:
            continue
        drawn = [tuple(one["from"] + one["to"])
                 for one in json.loads(body)["elements"]]
        if drawn != trim_printed[kind]:
            fail(f"{kind} is drawn as {drawn} and collides as {trim_printed[kind]}, "
                 f"so it is a block you can fall through or cannot step onto and it "
                 f"looks correct from every angle")

# The tags, read out of the harness rather than derived a third time here.
trim_tags_want = {}
for line in trim_out.splitlines():
    if not line.startswith("TTAG "):
        continue
    parts = line.split()
    for tag in parts[2:]:
        trim_tags_want.setdefault(tag, set()).add(f"{NS}:{parts[1]}")
if len(trim_tags_want) < 2:
    fail("the trim harness printed tags for nothing, so the tag files below are "
         "compared against an empty list and the check has no opinion")
climbing = trim_tags_want.get("minecraft:climbable", set())
for material, kind, id, _joins, climbs in trim_ids:
    if climbs and f"{NS}:{id}" not in climbing:
        fail(f"{id} is climbed and the rules do not put it in "
             f"minecraft:climbable, so a player slides down it and the model is "
             f"perfect")
        break
for tag, values in sorted(trim_tags_want.items()):
    namespace, path = tag.split(":", 1)
    where = os.path.join(RES, "data", namespace, "tags/block", path + ".json")
    if not os.path.isfile(where):
        fail(f"{tag} has no file, so every fitting that needs it is mined with a "
             f"fist")
        continue
    absent = values - set(json.loads(read(where)).get("values", []))
    if absent:
        fail(f"{len(absent)} fitting(s) are missing from {tag}: {sorted(absent)[:3]}")

fittings_src = squashed(strip_comments(read(SRC, "registry/AtelierBlocks.java"), True))
if "for (TrimRules.Fitting fitting : TrimRules.catalogue()) {" not in fittings_src:
    fail("the fittings are not registered by walking the catalogue, so a "
         "thirteenth is a block that does not exist and nothing says so")
if "if (made.size() != TrimRules.howMany()) { throw new IllegalStateException(" \
        not in fittings_src:
    fail("nothing counts the fittings registered against the catalogue they were "
         "walked from")

print(f"the fittings       : {len(trim_kinds)} kind(s) over {len(declared)} "
      f"material(s) = {len(trim_ids)} block(s) and {len(trim_refused)} refused for "
      f"being cut out of something a hand cannot work, every one drawn exactly as "
      f"it collides, tagged, dropped, named and registered from the catalogue")

# ---------------------------------------------------------------------------
# The colour axis
# ---------------------------------------------------------------------------
#
# Five hundred and ninety-two blocks and not one new texture: a red oak chair
# is this module's oak chair with two of its six boxes drawn in the game's own
# red wool. Which two is written down in the rules and the boxes are in the
# generator, so the one thing that can go wrong silently is the two disagreeing
# — a chair that gains a box and goes on being padded on the boxes it used to
# have, which is a chair with a cushion on one leg and it renders perfectly.

cloth = strip_comments(read(PURE, "ClothRules.java"), True)
colours = re.findall(r'"([a-z_]+)"',
                     re.search(r"COLOURS = List\.of\((.*?)\);", cloth, re.S).group(1))
cloth_seats = [(shape, int(boxes), [int(n) for n in re.findall(r"\d+", padded)])
               for shape, boxes, padded in re.findall(
                   r'new Seat\("([a-z_]+)",\s*(\d+),\s*List\.of\(([^)]*)\)\)', cloth)]
cloth_kinds = []
for name, made, of in re.findall(r'new Cloth\((\w+),\s*(\d+),\s*(\d+)\)', cloth):
    spelt = re.search(r'public static final String ' + name + r'\s*=\s*"([a-z_]+)";',
                      cloth)
    if spelt:
        cloth_kinds.append((spelt.group(1), int(made), int(of)))
if len(colours) != 16 or not cloth_seats or not cloth_kinds:
    fail(f"this validator read {len(colours)} colour(s), {len(cloth_seats)} seat(s) "
         f"and {len(cloth_kinds)} kind(s) of cloth out of the rules, and a crossing "
         f"check built on a short list passes whatever is wrong with the rest")

cloth_woods = [m for m in declared if course_materials[m][1]]
cloth_ids = []
for colour in colours:
    for wood in cloth_woods:
        for shape, _boxes, _padded in cloth_seats:
            cloth_ids.append((colour, f"{colour}_{wood}_{shape}", shape))
    for kind, _made, _of in cloth_kinds:
        cloth_ids.append((colour, f"{colour}_{kind}", kind))
if len(cloth_ids) < 300:
    fail(f"this validator found {len(cloth_ids)} coloured blocks, and a catalogue "
         f"check that looks at a handful of the catalogue passes whatever is wrong "
         f"with the rest of it")

missing = []
for colour, id, _shape in cloth_ids:
    for where in (f"assets/{NS}/blockstates/{id}.json",
                  f"assets/{NS}/models/block/{id}.json",
                  f"assets/{NS}/models/item/{id}.json",
                  f"data/{NS}/loot_table/blocks/{id}.json",
                  f"data/{NS}/recipe/{id}.json"):
        if not os.path.isfile(os.path.join(RES, where)):
            missing.append(f"{id} has no {where.rsplit('/', 2)[-2]}")
    if f"block.{NS}.{id}" not in lang:
        missing.append(f"{id} has no name, so it is called by its id")
if missing:
    fail(f"{len(missing)} of {len(cloth_ids) * 6} things a coloured block needs are "
         f"missing: " + ", ".join(missing[:6]))

# The padding, against what the harness printed out of the rules. Every seat is
# drawn as its own shape's boxes with some of them in wool, and both numbers —
# how many boxes and which of them are cloth — have to be what the rules say.
padding = {}
for line in cloth_out.splitlines():
    if not line.startswith("PAD "):
        continue
    parts = line.split()
    padding[parts[1]] = (int(parts[2]), [int(n) for n in parts[3:]])
if not padding:
    fail("the cloth harness printed no padding, so which boxes are cloth is "
         "checked against nothing and the check passes by having no opinion")
else:
    checked = 0
    for shape, boxes, padded in cloth_seats:
        if shape not in padding:
            fail(f"{shape} is upholstered and the rules printed no padding for it")
            continue
        if padding[shape] != (boxes, padded):
            fail(f"{shape} is padded on {padded} of {boxes} boxes here and "
                 f"{padding[shape]} in the rules")
            continue
        first = next((id for colour, id, kind in cloth_ids if kind == shape), None)
        if first is None:
            continue
        body = read(RES, "assets", NS, f"models/block/{first}.json")
        if not body:
            continue
        model = json.loads(body)
        drawn = model.get("elements", [])
        if len(drawn) != boxes:
            fail(f"{first} is drawn as {len(drawn)} boxes and the rules upholster "
                 f"{boxes}; the cushion is on whichever box that number lands on")
            continue
        wore = [n for n, one in enumerate(drawn)
                if one["faces"]["up"]["texture"] == "#cloth"]
        if wore != sorted(padded):
            fail(f"{first} wears cloth on boxes {wore} and the rules pad {padded}, "
                 f"so it is a chair with a cushion on one of its legs and it "
                 f"renders perfectly")
            continue
        textures = model.get("textures", {})
        want_cloth = f"minecraft:block/{first.split('_' + shape)[0].rsplit('_', 1)[0]}_wool"
        if not textures.get("cloth", "").endswith("_wool"):
            fail(f"{first} is upholstered in {textures.get('cloth')}, which is not "
                 f"wool, so the colour axis is not a colour axis")
        if textures.get("wood") == textures.get("cloth"):
            fail(f"{first} wears one texture on its frame and its cushion, so it "
                 f"is a block of wool shaped like a chair")
        checked += 1
    if not checked:
        fail("not one upholstered seat was compared with the padding the rules "
             "print, so that comparison has no opinion about anything")

# Every colour's seat has to be upholstered in that colour's own wool, which is
# the whole of the difference between five hundred and twenty-eight blocks and
# thirty-three of them.
wrong = []
for colour, id, kind in cloth_ids:
    body = read(RES, "assets", NS, f"models/block/{id}.json")
    if not body:
        continue
    textures = json.loads(body).get("textures", {})
    wore = textures.get("cloth") or textures.get("all")
    if wore != f"minecraft:block/{colour}_wool":
        wrong.append(f"{id} is {wore}")
if wrong:
    fail(f"{len(wrong)} coloured block(s) wear the wrong colour: {wrong[:4]} — "
         f"which is the entire difference between this and one uncoloured block")

# The recipes: a seat and a block of wool, shapeless, and nothing else.
seat_recipes = 0
for colour, id, kind in cloth_ids:
    body = read(RES, "data", NS, f"recipe/{id}.json")
    if not body:
        continue
    recipe = json.loads(body)
    if kind in {shape for shape, _b, _p in cloth_seats}:
        seat_recipes += 1
        if recipe.get("type") != "minecraft:crafting_shapeless":
            fail(f"{id} is made on a grid rather than out of a seat and a block of "
                 f"wool, and this module has no grids left")
            break
        items = [one.get("item") for one in recipe.get("ingredients", [])]
        if f"minecraft:{colour}_wool" not in items:
            fail(f"{id} is not made out of {colour} wool at all, so it is a "
                 f"{colour} seat only by name")
            break
        if not any(str(one).startswith(f"{NS}:") for one in items):
            fail(f"{id} is not made out of one of this module's own seats, so it "
                 f"is a seat that costs nothing to sit on")
            break
if not seat_recipes:
    fail("not one upholstered seat has a recipe, so the checks above are about "
         "recipes nothing ships")

cloth_src = squashed(strip_comments(read(SRC, "registry/AtelierBlocks.java"), True))
if "for (ClothRules.Upholstered seat : ClothRules.seats()) {" not in cloth_src:
    fail("the upholstered seats are not registered by walking the crossing, so a "
         "seventeenth colour is five hundred blocks that do not exist")
if "if (made.size() != ClothRules.howMany()) { throw new IllegalStateException(" \
        not in cloth_src:
    fail("nothing counts the coloured blocks registered against the crossing they "
         "were walked from")

print(f"the colours        : {len(colours)} dye(s) x {len(cloth_woods)} wood(s) x "
      f"{len(cloth_seats)} seat(s) plus {len(cloth_kinds)} thing(s) of cloth alone "
      f"= {len(cloth_ids)} block(s), every one wearing its own colour's wool on "
      f"the boxes the rules pad and its wood everywhere else")

# ---------------------------------------------------------------------------
# The forty-five degrees
# ---------------------------------------------------------------------------
#
# A diagonal in a game of cubes is a staircase of little boxes, and a
# staircase of little boxes has one way of being wrong that renders perfectly:
# two boxes meeting at a corner do not meet, the game walks a mob straight
# through the join, and it looks like a continuous rail from every angle. The
# rules flood-fill it; this compares what they drew with what the generator
# drew, which is the only thing standing between the two descriptions.

diagonal = strip_comments(read(PURE, "DiagonalRules.java"), True)
diag_named = dict(re.findall(
    r'public static final String (\w+)\s*=\s*"([a-z_]+)";', diagonal))
diag_kinds = []
for name, wide, made, of in re.findall(
        r'new Kind\((\w+),\s*(\d+),\s*(\d+),\s*(\d+)\)', diagonal):
    if name in diag_named:
        diag_kinds.append((diag_named[name], int(wide), int(made), int(of)))
if len(diag_kinds) != len(re.findall(r"new Kind\(", diagonal)):
    fail(f"this validator read {len(diag_kinds)} of "
         f"{len(re.findall(r'new Kind', diagonal))} diagonals out of the rules")

diag_ids = [(material, kind, f"{material}_{kind}")
            for material in declared for kind, _w, _m, _o in diag_kinds]
if len(diag_ids) < 50:
    fail(f"this validator found {len(diag_ids)} diagonals, and a catalogue check "
         f"that looks at a handful of the catalogue passes whatever is wrong with "
         f"the rest of it")

missing = []
for material, kind, id in diag_ids:
    for where in (f"assets/{NS}/blockstates/{id}.json",
                  f"assets/{NS}/models/block/{id}.json",
                  f"assets/{NS}/models/item/{id}.json",
                  f"data/{NS}/loot_table/blocks/{id}.json",
                  f"data/{NS}/recipe/{id}.json"):
        if not os.path.isfile(os.path.join(RES, where)):
            missing.append(f"{id} has no {where.rsplit('/', 2)[-2]}")
    if f"block.{NS}.{id}" not in lang:
        missing.append(f"{id} has no name, so it is called by its id")
if missing:
    fail(f"{len(missing)} of {len(diag_ids) * 6} things a diagonal needs are "
         f"missing: " + ", ".join(missing[:6]))

diag_printed = {}
for line in diag_out.splitlines():
    if not line.startswith("DIAG "):
        continue
    parts = line.split()
    diag_printed[parts[1]] = [tuple(int(n) for n in box.split(","))
                              for box in parts[2:]]
if not diag_printed:
    fail("the diagonal harness printed no geometry, so the models are compared "
         "against nothing and the check passes by having no opinion")
else:
    for kind, _wide, _made, _of in diag_kinds:
        if kind not in diag_printed:
            fail(f"{kind} has no geometry from the rules")
            continue
        first = next((id for material, k, id in diag_ids if k == kind), None)
        body = read(RES, "assets", NS, f"models/block/{first}.json") if first else ""
        if not body:
            continue
        drawn = [tuple(one["from"] + one["to"])
                 for one in json.loads(body)["elements"]]
        if drawn != diag_printed[kind]:
            fail(f"{kind} is drawn as {len(drawn)} boxes and collides as "
                 f"{len(diag_printed[kind])}, so it is a rail with a gap in it "
                 f"that looks continuous from every angle")

# And the drawn run itself, checked here rather than taken on trust: every box
# has to overlap the one before it and the run has to reach both corners.
for kind, _wide, _made, _of in diag_kinds:
    run = diag_printed.get(kind)
    if not run:
        continue
    for before, now in zip(run, run[1:]):
        if not (now[0] < before[3] and now[2] < before[5]):
            fail(f"{kind} has a box starting at {now[0]} and the one before it "
                 f"ending at {before[3]}: two boxes meeting at a corner do not "
                 f"meet, and a mob walks straight through the join")
            break
    if run[0][0] != 0 or run[-1][3] != 16:
        fail(f"{kind} runs from {run[0][0]} to {run[-1][3]} rather than corner to "
             f"corner, so a line of them is a dotted line")

# The tags, read out of the harness rather than derived a third time here.
diag_tags_want = {}
for line in diag_out.splitlines():
    if not line.startswith("DTAG "):
        continue
    parts = line.split()
    for tag in parts[2:]:
        diag_tags_want.setdefault(tag, set()).add(f"{NS}:{parts[1]}")
if len(diag_tags_want) < 2:
    fail("the diagonal harness printed tags for nothing, so the tag files are "
         "compared against an empty list")
for material, kind, id in diag_ids:
    if kind == "diagonal_fence" \
            and f"{NS}:{id}" not in diag_tags_want.get("minecraft:fences", set()):
        fail(f"{id} is not in the game's own fence tag, so no ordinary fence meets "
             f"it and the whole point of a diagonal is that a run of them can be "
             f"mixed with a straight one")
        break
for tag, values in sorted(diag_tags_want.items()):
    namespace, path = tag.split(":", 1)
    where = os.path.join(RES, "data", namespace, "tags/block", path + ".json")
    if not os.path.isfile(where):
        fail(f"{tag} has no file, so every diagonal that needs it is mined with a "
             f"fist and meets nothing")
        continue
    absent = values - set(json.loads(read(where)).get("values", []))
    if absent:
        fail(f"{len(absent)} diagonal(s) are missing from {tag}: {sorted(absent)[:3]}")

diag_src = squashed(strip_comments(read(SRC, "registry/AtelierBlocks.java"), True))
if "for (DiagonalRules.Run run : DiagonalRules.catalogue()) {" not in diag_src:
    fail("the diagonals are not registered by walking the catalogue")
if "if (made.size() != DiagonalRules.howMany()) { throw new IllegalStateException(" \
        not in diag_src:
    fail("nothing counts the diagonals registered against the catalogue they were "
         "walked from")

print(f"the diagonals      : {len(diag_kinds)} kind(s) over {len(declared)} "
      f"material(s) = {len(diag_ids)} block(s), every one an unbroken run of "
      f"overlapping boxes from one corner of its block to the other and through "
      f"the middle where an ordinary fence keeps its post")

# ---------------------------------------------------------------------------
# What mines it (29 Sep 2026): every block on one tool's list, or excused
# ---------------------------------------------------------------------------
#
# A stone block asks for the right tool before it drops anything, and "the
# right tool" is whichever tool's tag names the block. On 29 September 2026
# the running game's harvest audit found 693 blocks of this module in no tag:
# the architectural pieces, in every material. Six families wrote their own
# lists and the seventh wrote none, so a survival player broke a stone roof,
# saw it vanish and held nothing.
#
# So this is held both ways, for every block the module registers (every
# blockstate it ships): each is in exactly one tool's list, the one
# HarvestRules names, or is a kind HarvestRules breaks by hand with a reason
# written here; a kind broken by hand must not ask for the right tool where
# it is registered, nor copy a game block that does, read out of the registry
# rather than taken on trust; and a list may name only blocks this module
# registers, because one unknown id makes the game refuse the whole tag.

MINEABLE = {}
mineable_files = 0
for tool in ("pickaxe", "axe", "shovel", "hoe"):
    tag_path = os.path.join(RES, "data/minecraft/tags/block/mineable", tool + ".json")
    if not os.path.isfile(tag_path):
        continue
    mineable_files += 1
    listed = json.load(open(tag_path, encoding="utf-8"))
    if listed.get("replace", False):
        fail(f"mineable/{tool} replaces the game's own list rather than adding to it, "
             f"so every block the game mines with a {tool} would drop nothing")
    for one in listed.get("values", []):
        named = one.get("id") if isinstance(one, dict) else one
        if not isinstance(named, str):
            continue
        MINEABLE.setdefault(named, set()).add(tool)
        if not named.startswith(NS + ":") or not os.path.isfile(os.path.join(
                RES, "assets", NS, "blockstates", named.split(":", 1)[1] + ".json")):
            fail(f"mineable/{tool} names {named}, which is not a block this module "
                 f"registers, so the game refuses the whole tag and every block in "
                 f"it is back to dropping nothing")

#: The kinds no tool is for, and why. HarvestRules.BY_HAND says which; a kind
#: it names with no reason here is refused, and so is a reason for a kind it
#: no longer names. Nothing at present: everything here is wood, stone or
#: wool, and the game's own are on a tool's list whatever the shape.
MINE_EXEMPT = {}

#: What the game's blocks this module copies ask of a tool, read out of the
#: game's own jar: stone wants the right one, planks and wool want none. A
#: block copying something not here is a failure, not a pass.
ASKS_FOR_A_TOOL = {"STONE": True, "OAK_PLANKS": False, "WHITE_WOOL": False}


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


harvest_rules = strip_comments(read(PURE, "HarvestRules.java"), True)
if not harvest_rules:
    fail("there is no HarvestRules.java, so nothing says what mines two thousand nine "
         "hundred blocks and nothing can be held to it")
_harvest_names = dict(re.findall(r'public static final String ([A-Z_]+) = "([a-z_]+)";',
                                 harvest_rules))
_by_hand_listed = re.search(r"List<String> BY_HAND =\s*List\.of\(([^;]*?)\);",
                            harvest_rules)
if harvest_rules and not _by_hand_listed:
    fail("HarvestRules no longer has a BY_HAND list, so nothing says which kinds are "
         "left off every tool's list on purpose")
by_hand = ([_harvest_names.get(token, token.strip('"'))
            for token in re.findall(r'[A-Z_]{2,}|"[a-z_]+"', _by_hand_listed.group(1))]
           if _by_hand_listed else [])
for kind in by_hand:
    if kind not in MINE_EXEMPT:
        fail(f"HarvestRules breaks {kind} by hand and this gives no reason, so it is "
             f"left out of every tool's list on nobody's say-so")
for kind in MINE_EXEMPT:
    if kind not in by_hand:
        fail(f"a reason is given for breaking {kind} by hand and HarvestRules no longer "
             f"does, so the exemption covers blocks the rules put in a tool's list")

# What each roster's blocks are built on, read out of the registry. Every
# roster but the cloth takes its properties from like(material), which copies
# the game's planks for wood and the game's stone for stone; the cloth copies
# the game's wool, and an upholstered seat the planks.
registry_src = squashed(strip_comments(read(SRC, "registry/AtelierBlocks.java"), True))
for copied in sorted(set(re.findall(r"\bBlocks\.([A-Z_]+)", registry_src))):
    if copied not in ASKS_FOR_A_TOOL:
        fail(f"AtelierBlocks copies Blocks.{copied}, and nothing here knows whether that "
             f"asks for the right tool: look it up in the game's jar and add it to "
             f"ASKS_FOR_A_TOOL")
like_body = body_of(registry_src,
                    "private static BlockBehaviour.Properties like(AtelierRules.Material material)")
_branches = re.search(r"return material\.soft\(\) \? (.*?) : (.*?); \}", like_body)
if not _branches:
    fail("like() no longer chooses by material.soft(), so this validator cannot tell "
         "a block of wood from a block of stone where they are registered")
soft_asks = asks_for_a_tool(_branches.group(1) if _branches else "",
                            "a wooden block (the soft branch of like())")
hard_asks = asks_for_a_tool(_branches.group(2) if _branches else "",
                            "a stone block (the hard branch of like())")
ROSTER_METHODS = {
    "shapes": "private static Map<String, DeferredBlock<Block>> register()",
    "openings": "private static Map<String, DeferredBlock<Block>> openings()",
    "pieces": "private static Map<String, DeferredBlock<Block>> pieces()",
    "courses": "private static Map<String, DeferredBlock<Block>> courses()",
    "fittings": "private static Map<String, DeferredBlock<Block>> fittings()",
    "cloth": "private static Map<String, DeferredBlock<Block>> cloth()",
    "diagonals": "private static Map<String, DeferredBlock<Block>> diagonals()",
}
roster_bodies = {}
for roster, signature in ROSTER_METHODS.items():
    body = body_of(registry_src, signature)
    if not body:
        fail(f"the registry no longer has {signature.rsplit(' ', 1)[-1]}, so this "
             f"validator cannot read what the {roster} are built on")
    roster_bodies[roster] = body
BUILT_ON_LIKE = {
    "shapes": "BlockBehaviour.Properties properties = like(material);",
    "openings": "BlockBehaviour.Properties properties = like(opening.material()).noOcclusion();",
    "pieces": "BlockBehaviour.Properties properties = like(material);",
    "courses": "BlockBehaviour.Properties settled = like(course.material());",
    "fittings": "BlockBehaviour.Properties settled = like(fitting.material());",
    "diagonals": "BlockBehaviour.Properties settled = like(run.material());",
}
for roster, statement in BUILT_ON_LIKE.items():
    if statement not in roster_bodies.get(roster, ""):
        fail(f"the {roster} are no longer built on like(material), so what they ask "
             f"of a tool is decided somewhere this validator does not read")
_wool = re.search(r"BlockBehaviour\.Properties soft = (.*?);", roster_bodies.get("cloth", ""))
_seat = re.search(r"new ShapedBlock\((.*?), shape\)", roster_bodies.get("cloth", ""))
cloth_asks = asks_for_a_tool(_wool.group(1) if _wool else "", "a piece of cloth alone")
seat_asks = asks_for_a_tool(_seat.group(1) if _seat else "", "an upholstered seat")


def asks_of(roster, material):
    """Whether a block of this roster and material asks for the right tool."""
    body = roster_bodies.get(roster, "")
    if ".requiresCorrectToolForDrops()" in body:
        return True, f"the {roster} say so where they are registered"
    for copied in re.findall(r"\bBlocks\.([A-Z_]+)", body):
        if ASKS_FOR_A_TOOL.get(copied):
            return True, f"the {roster} copy Blocks.{copied}, which does"
    if roster == "cloth":
        return seat_asks if material in course_materials else cloth_asks
    return soft_asks if course_materials.get(material, ("", False))[1] else hard_asks


# What the rules say mines each block, read out of the harness rather than
# derived a third time here, against the lists that shipped.
harvest = {}
for line in harvest_out.splitlines():
    parts = line.split()
    if len(parts) == 6 and parts[0] == "HARVEST":
        harvest[parts[1]] = (parts[2], parts[3], parts[4], parts[5])
if harvest_out and not harvest:
    fail("the harvest harness printed no blocks, so the tool lists are compared "
         "against nothing and the check passes by having no opinion")

blockstates = sorted(name[:-5] for name in os.listdir(
    os.path.join(RES, "assets", NS, "blockstates")) if name.endswith(".json"))
unlisted = [id for id in blockstates if id not in harvest]
phantom = [id for id in harvest if id not in set(blockstates)]
if harvest and unlisted:
    fail(f"{len(unlisted)} block(s) ship a blockstate and HarvestRules names no tool "
         f"for them, so nothing says what mines them: " + ", ".join(unlisted[:6]))
if phantom:
    fail(f"HarvestRules names {len(phantom)} block(s) nothing registers: "
         + ", ".join(phantom[:6]))

asking_untagged = []
untagged = []
twice = []
wrong_list = []
exempt_asking = {}
exempt_tagged = []
tool_blocks = 0
for id, (roster, kind, material, tool) in sorted(harvest.items()):
    full = f"{NS}:{id}"
    asks, how = asks_of(roster, material)
    lists = MINEABLE.get(full, set())
    if asks:
        tool_blocks += 1
    if tool == "hand":
        if asks:
            count, _how = exempt_asking.get(kind, (0, how))
            exempt_asking[kind] = (count + 1, how)
        if lists:
            exempt_tagged.append(id)
        continue
    if not lists:
        (asking_untagged if asks else untagged).append(full)
    elif len(lists) > 1:
        twice.append(f"{id} ({', '.join(sorted(lists))})")
    elif tool not in lists:
        wrong_list.append(f"{id} is on the {next(iter(lists))}'s list and the rules "
                          f"put it on the {tool}'s")
if asking_untagged:
    fail(f"{len(asking_untagged)} block(s) ask for the right tool and are in no tool's "
         f"tag, so no tool is ever right and they drop nothing however they are "
         f"mined: " + ", ".join(asking_untagged[:6]))
if untagged:
    fail(f"{len(untagged)} block(s) are in no mineable tag and not exempt, so every "
         f"tool breaks them at a fist's pace: " + ", ".join(untagged[:6]))
if twice:
    fail(f"{len(twice)} block(s) are on two tools' lists, so the rules say one thing "
         f"and the files another: " + ", ".join(twice[:4]))
if wrong_list:
    fail(f"{len(wrong_list)} block(s) are on the wrong tool's list: "
         + "; ".join(wrong_list[:4]))
for kind, (count, how) in sorted(exempt_asking.items()):
    fail(f"{kind} is exempt from the mineable tags "
         f"({MINE_EXEMPT.get(kind, 'no reason given')}), but {count} block(s) of it "
         f"ask for the right tool: {how}. With no tag no tool is right, so they "
         f"would drop nothing")
if exempt_tagged:
    fail(f"{len(exempt_tagged)} block(s) are exempt from the mineable tags and in one "
         f"all the same; take the kind off BY_HAND or out of the tag: "
         + ", ".join(exempt_tagged[:4]))

print(f"what mines it      : {len(harvest)} block(s) on {mineable_files} tool list(s), "
      f"{tool_blocks} of them asking for the right tool and every one of those in "
      f"its tool's tag; wood by axe and stone by pickaxe as the game's own, and "
      f"{sum(1 for _r, _k, _m, t in harvest.values() if t == 'hand')} broken by hand "
      f"with a reason")

print("what it declares   : the game and the library, and not one thing beyond them")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all atelier checks passed")
