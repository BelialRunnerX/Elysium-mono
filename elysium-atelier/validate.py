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

join_out = ""
for where in (HARNESS, JOIN_HARNESS):
    if not os.path.isfile(where):
        fail(f"{os.path.basename(where)} is gone, so what it proved is now checked by "
             f"reading, which nobody has ever done")

if os.path.isfile(HARNESS) and os.path.isfile(JOIN_HARNESS):
    with tempfile.TemporaryDirectory() as tmp:
        staged = os.path.join(tmp, "src")
        os.makedirs(staged)
        sources = [os.path.join(PURE, n) for n in os.listdir(PURE) if n.endswith(".java")]
        for source in sources + [HARNESS, JOIN_HARNESS]:
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
                                (JOIN_MAIN, "the joining        ")):
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
                said = [line for line in ran.stdout.strip().splitlines()
                        if not line.startswith("TAG ")
                        and not line.startswith("OPENING ")]
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

materials = re.findall(r'wood\("([a-z_]+)"\)|stone\("([a-z_]+)", "[a-z_]+"\)|'
                       r'new Material\("([a-z_]+)"', strip_comments(rules, True))
declared = [next(part for part in group if part) for group in materials]
declared = [name for name in declared if name != "name"]
woods = re.search(r'for \(String name : List\.of\(([^)]*)\)\)', strip_comments(rules, True))
if woods:
    declared = re.findall(r'"([a-z_]+)"', woods.group(1)) + [
        name for name in declared if name not in re.findall(r'"([a-z_]+)"', woods.group(1))]

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
                    elif open(made_path, "rb").read() != open(kept, "rb").read():
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
VANILLA_JAR = "/tmp/mc/mapped-at.jar"
SAME_AS = {"door": "oak_door", "trapdoor": "oak_trapdoor", "gate": "oak_fence_gate",
           "fence": "oak_fence", "pane": "glass_pane"}
if not os.path.isfile(VANILLA_JAR):
    print("the joins          : the game's jar is not here, so the blockstates were "
          "not compared against it")
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

print("what it declares   : the game and the library, and not one thing beyond them")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all atelier checks passed")
