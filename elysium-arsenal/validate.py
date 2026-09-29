#!/usr/bin/env python3
"""
Validate Elysium Arsenal against its sources.

Run from anywhere:   python3 elysium-arsenal/validate.py

A tool that clears a three-by-three is a good idea and is almost impossible to
add without making the pickaxe pointless — and once the pickaxe is pointless a
whole tier of the game's crafting is a step nobody takes for its own sake. So
every tool here is *worse* at everything except breadth, which leaves exactly
one band of speed where it is worth carrying, and a band is the sort of thing a
designer is confident about and wrong about.

  1. INDEPENDENCE. The library, and nothing else at all.
  2. PURITY, and the harness run rather than read.
  3. THE BAND. Slower on one block, faster over nine.
  4. WHAT A SWING COSTS, and the three refusals.
  5. THE CATALOGUE, generated rather than typed.
  6. THE SEAM.
"""
import itertools
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
SRC = os.path.join(WORK, "src/main/java/com/elysium/arsenal")
PURE = os.path.join(SRC, "rules")
BELOW = []
HARNESS = os.path.join(WORK, "tools/ArsenalHarness.java")
HARNESS_MAIN = "com.elysium.arsenal.rules.ArsenalHarness"
GENERATOR = os.path.join(WORK, "tools/gen_arsenal.py")

#: How long the arithmetic is allowed to take. Two million checks run in a few
#: seconds; anything past this is a loop.
HARNESS_SECONDS = 120
NS = "elysiumarsenal"

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
    print("This is not the elysium-arsenal tree.")
    sys.exit(1)

print("=" * 70)
print("elysium-arsenal — the tools that take a wall at a time, and the proof that not "
      "one of them is simply better")
print("=" * 70)

# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------

FOREIGN = re.compile(
    r"(?:net\.sweenus|com\.simplest|mythsandlegends|betterarcheology)"
    r"|\b(?:simplyswords|simplest_hammers|simplest_excavators|simplest_paxels|"
    r"simplest_scythes|reforged)\s*:", re.I)
UPWARD = re.compile(r"com\.elysium\.(bestiary|wilds|colonies)")

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
        if used not in ("arsenal", "lib"):
            fail(f"{where} names com.elysium.{used}; every tool here sits on a vanilla "
                 f"tier and is built out of a vanilla tool, so it works in a pack with "
                 f"nothing else installed")

toml = read(RES, "META-INF/neoforge.mods.toml")
declared = set(re.findall(r'modId="([^"]+)"', toml))
allowed = {"${mod_id}", "neoforge", "minecraft", "elysiumlib"}
for mod in declared - allowed:
    fail(f"the module declares a dependency on {mod}; every tool it adds is built out "
         f"of a vanilla one and must not need anything else")
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
                 f"so a harness can sweep a thousand tool speeds without a game")
    for used in re.findall(r"import\s+(com\.elysium\.[\w.]+);", without_package):
        # The library's ability roster is the exception, and only it. The
        # uniques are priced by the same arithmetic the relics are, and a
        # second copy of that arithmetic here is a second copy that drifts.
        if used.startswith("com.elysium.lib."):
            continue
        fail(f"rules/{name} imports {used}; the rules are the JDK alone, except for "
             f"the library's own pure arithmetic")

if not os.path.isfile(HARNESS):
    fail("there is no harness, so whether a tool sits inside the band is an opinion")
else:
    with tempfile.TemporaryDirectory() as tmp:
        staged = os.path.join(tmp, "src")
        os.makedirs(staged)
        sources = [os.path.join(PURE, n) for n in os.listdir(PURE) if n.endswith(".java")]
        for source in sources + [HARNESS,
                                 os.path.join(WORK, "tools/WeaponHarness.java"),
                                 os.path.join(WORK, "tools/RangedHarness.java"),
                                 os.path.join(WORK, "tools/ForgeHarness.java"),
                                 os.path.join(WORK, "tools/DigHarness.java"),
                                 os.path.join(ROOT, "elysium-lib/src/main/java/"
                                              "com/elysium/lib/ability/"
                                              "ElysiumAbilityRules.java")]:
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
                                ("com.elysium.arsenal.rules.WeaponHarness",
                                 "the weapons        "),
                                ("com.elysium.arsenal.rules.RangedHarness",
                                 "the bows           "),
                                ("com.elysium.arsenal.rules.ForgeHarness",
                                 "reforging          "),
                                ("com.elysium.arsenal.rules.DigHarness",
                                 "the digging        ")):
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
                print(label + ": " + ran.stdout.strip().splitlines()[-1])

rules = read(PURE, "ArsenalRules.java")
plain = strip_comments(rules)
squished = squashed(plain)


def constant(name, kind=r"(?:float|int|double)"):
    m = re.search(r"public static final " + kind + r"\s+" + name + r"\s*=\s*([^;]+);",
                  plain, re.S)
    return " ".join(m.group(1).split()) if m else None










# ---------------------------------------------------------------------------
# 3. The band
# ---------------------------------------------------------------------------

reach = constant("REACH", "int")
if reach != "1":
    fail(f"a broad tool reaches {reach}; two is twenty-five blocks a swing and a "
         f"ceiling a player drops on themselves before they have noticed")

blocks = squashed(body_of(plain, "public static int blocksIn("))
if "int across = 2 * Math.max(0, reach) + 1;" not in blocks:
    fail("how many blocks a swing takes is not worked out from the reach, so the two "
         "can disagree and the durability is charged for a different number of blocks "
         "than were taken")

guard = squashed(body_of(plain, "public Broad {"))
if "if (share <= 0.0F || share >= 1.0F) {" not in guard:
    fail("a broad tool may be as fast as the tool it replaces, which makes that tool a "
         "crafting ingredient rather than a tool")

alive = squashed(body_of(plain, "public boolean leavesTheSpecialistAlive()"))
if "return swing() > 1.0F;" not in alive:
    fail("whether a specialist survives is not whether the broad tool is slower on one "
         "block, so the check the whole module rests on is checking something else")

worth = squashed(body_of(plain, "public boolean worthCarrying()"))
if "return clearing() < specialistClearing();" not in worth:
    fail("whether a broad tool is worth carrying is not whether it beats the specialist "
         "over a full swing, so a tool nobody would ever make passes")

for name, wide, share, replaces in re.findall(
        r'new Broad\("([a-z_]+)", (true|false), (\w+), "([^"]+)"\)',
        strip_comments(rules, keep_strings=True)):
    value = constant(share)
    try:
        value = float((value or share).rstrip("Ff"))
    except ValueError:
        fail(f"{name}'s share is {share}, which is not a number this file can read")
        continue
    if value >= 1.0:
        fail(f"{name} swings as fast as a {replaces} or faster, so the {replaces} is "
             f"now a crafting ingredient")
    if wide == "true" and value <= 1.0 / 9.0:
        fail(f"{name} clears nine blocks more slowly than a {replaces} clears nine, so "
             f"nobody would ever make one")

broad = constant("BROAD_SHARE")
paxel = constant("PAXEL_SHARE")
try:
    if float((paxel or "0").rstrip("Ff")) <= float((broad or "1").rstrip("Ff")):
        fail("the tool that does every job is slower than the ones that do one *and* "
             "narrower, which is two penalties for one convenience and nobody makes one")
except ValueError:
    fail("the shares are not numbers this file can read")

across = 2 * int(reach or 1) + 1
print(f"the band           : every tool slower than its specialist on one block and "
      f"faster over a {across} by {across}, and the one that does every job worse than "
      f"all of them")

# ---------------------------------------------------------------------------
# 4. What a swing costs, and the three refusals
# ---------------------------------------------------------------------------

per_block = constant("COST_PER_BLOCK", "int")
if per_block != "1":
    fail(f"a block costs {per_block} durability; anything under one makes a broad tool "
         f"nine times the tool for the same price and every other tool in the game a "
         f"mistake")

costing = squashed(body_of(plain, "public static int costOf("))
if "return Math.max(0, blocks) * COST_PER_BLOCK;" not in costing:
    fail("what a swing costs is not what its blocks cost, so nine blocks can be cheaper "
         "than nine")

tool = strip_comments(read(SRC, "item/BroadToolItem.java"))
mining = squashed(body_of(tool, "public boolean mineBlock("))
for statement, why in (
        ("stack.hurtAndBreak(ArsenalRules.costOf(taken), player, EquipmentSlot.MAINHAND);",
         "the extra blocks are free, so the tool is nine times the tool for one price"),
        ("if (state.getDestroySpeed(level, pos) <= 0.0F) { return broke; }",
         "a tool clears nine blocks every time somebody brushes a leaf, which is "
         "unusable rather than powerful"),
        ("if (other.isAir() || !stack.isCorrectToolForDrops(other)) { continue; }",
         "a hammer takes blocks a pickaxe could not, so it is not the pickaxe's "
         "replacement, it is every tool at once")):
    if statement not in mining:
        fail("a swing: " + why)
if "hardness > state.getDestroySpeed(level, pos) + HARDER_BY" not in mining:
    fail("a swing takes blocks much harder than the one it was aimed at, so hitting a "
         "stone block beside an ore vein takes the vein")
if "SWINGING.set(true);" not in mining or "SWINGING.set(false);" not in mining:
    fail("breaking a block calls back into the swing that broke it, so one swing clears "
         "nine blocks around each of nine blocks around each of nine blocks")

around = squashed(body_of(tool, "private List<BlockPos> around("))
if "switch (face.getAxis())" not in around:
    fail("the nine blocks are not the plane the player is looking at, so a swing at a "
         "floor takes the block under the player and they fall into a hole they did not "
         "dig")

scythe = strip_comments(read(SRC, "item/ScytheItem.java"))
reaping = squashed(body_of(scythe, "private boolean reap("))
if "int held = ArsenalRules.replanted(1);" not in reaping:
    fail("how much seed is put back is not the rules' answer, so the accounting the "
         "harness proves is about a different scythe")
if "stack.shrink(taken);" not in reaping:
    fail("the seed that goes back in the ground is not taken out of the drops, so a "
         "field grows every time it is harvested and nobody can work out where the "
         "wheat is coming from")
if "crop.isMaxAge(state)" not in reaping:
    fail("an ungrown crop is harvested, which is a way of turning a seed into a seed "
         "and a crop into nothing")

seeds = squashed(body_of(plain, "public static int seedsFrom("))
if "return Math.max(0, harvested);" not in seeds:
    fail("a harvest gives back more seed than there were crops, which is a seed farm "
         "that needs no field")

print("a swing            : every block paid for singly, nothing instant, nothing the "
      "specialist could not take, nothing much harder, and no swing inside a swing")

# ---------------------------------------------------------------------------
# 5. The catalogue
# ---------------------------------------------------------------------------

items_src = squashed(strip_comments(read(SRC, "registry/ArsenalItems.java"), True))
if "for (String name : ArsenalRules.TIERS) {" not in items_src:
    fail("the tools are not registered from the catalogue, so a fifth shape is six more "
         "declarations and six more chances to give one of them the wrong tier")
slowing = squashed(body_of(strip_comments(read(SRC, "registry/ArsenalItems.java")),
                           "private static float slower("))
if "return attacks * share - 4.0F;" not in slowing:
    fail("the swing speed is not the vanilla tool's, shared by the rules' fraction — "
         "and the game's speed attribute is 'attacks a second minus four', so getting "
         "this wrong is wrong by a reciprocal and looks fine")

shapes = re.findall(r'new Broad\("([a-z_]+)"', strip_comments(rules, keep_strings=True))
tiers = re.findall(r'"([a-z]+)"', re.search(
    r"TIERS =\s*List\.of\(([^)]*)\)", strip_comments(rules, keep_strings=True)).group(1))

lang = {}
lang_path = os.path.join(RES, "assets", NS, "lang/en_us.json")
if os.path.isfile(lang_path):
    lang = json.load(open(lang_path, encoding="utf-8"))
else:
    fail("there is no language file, so every tool is called by its id")

missing = []
for tier in tiers:
    for shape in shapes:
        id = f"{tier}_{shape}"
        for kind, where in (("a model", f"assets/{NS}/models/item/{id}.json"),
                            ("a sprite", f"assets/{NS}/textures/item/{id}.png"),
                            ("a recipe", f"data/{NS}/recipe/{id}.json")):
            if not os.path.isfile(os.path.join(RES, where)):
                missing.append(f"{id} has no {kind}")
        if f"item.{NS}.{id}" not in lang:
            missing.append(f"{id} has no name in the language file")
if missing:
    fail(f"{len(missing)} of {len(tiers) * len(shapes) * 4} things a tool needs are "
         f"missing: " + ", ".join(missing[:5]))

# Every tool has to be built out of the tool it replaces, or a player can skip
# a tier of the game by making the broad one first.
for tier in tiers:
    for shape in shapes:
        recipe = read(RES, "data", NS, f"recipe/{tier}_{shape}.json")
        if not recipe:
            continue
        # The *tool*, not the prefix. "minecraft:iron_" also matches
        # "minecraft:iron_ingot", which is the material — so a rule written
        # that way passes for every recipe in the module and has never once
        # been about what it says it is about.
        tools = [f"minecraft:{tier}_{kind}"
                 for kind in ("pickaxe", "shovel", "hoe", "axe")]
        if not any(tool in recipe for tool in tools):
            fail(f"{tier}_{shape} is not built out of a vanilla {tier} tool, so a "
                 f"player can make the broad one without ever making the one it "
                 f"replaces")
        if "elysium" in json.dumps(json.loads(recipe).get("key", {})):
            fail(f"{tier}_{shape} is made out of another Elysium module")

# Every model, not the first one. Checking a single file passed happily while
# thirty-six weapon models were held flat, because nothing had ever looked at
# them — which is the same shape as every other bug in this repository: the
# check was right about what it looked at and looked at one thing.
print(f"the catalogue      : {len(tiers) * len(shapes)} tool(s), every one drawn, "
      f"modelled, named and built out of the vanilla tool it replaces")

# ---------------------------------------------------------------------------
# 5b. Generated, and still what the generator writes
# ---------------------------------------------------------------------------

if not os.path.isfile(GENERATOR):
    fail("there is no generator, so every sprite in the module is kept by hand")
else:
    with tempfile.TemporaryDirectory() as tmp:
        environment = dict(os.environ, ELYSIUM_ARSENAL_OUT=tmp)
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
            # And the other way: a file shipped that the generator no longer
            # writes. The generator never deletes, so whatever it stops making
            # stays in the jar - the retired disc's sprite and model did,
            # until this looked. Only the mods file is written by hand.
            left = []
            for dirpath, _, filenames in os.walk(RES):
                for name in filenames:
                    kept = os.path.join(dirpath, name)
                    rel = os.path.relpath(kept, RES)
                    if rel.replace(os.sep, "/").startswith("META-INF/"):
                        continue
                    if not os.path.isfile(os.path.join(fresh, rel)):
                        left.append(rel.replace(os.sep, "/"))
            if left:
                fail(f"{len(left)} shipped file(s) the generator no longer writes, left "
                     f"behind by something retired or kept by hand: "
                     + ", ".join(sorted(left)[:6]))

print("generated          : every sprite, model, recipe and name written by the "
      "generator, and nothing shipped that it no longer writes")

# ---------------------------------------------------------------------------
# 5c. The Cordon's rungs, and the roster they are read from
# ---------------------------------------------------------------------------
#
# WeaponHarness proves the ladder still climbs with the new rungs on it. What
# it cannot see is whether the rungs are the materials elysium-ores actually
# ships: "the gear and the materials agree" is the check that matters, and it
# is a check between two modules, so it lives here and reads the other's file.

cordon_rungs = re.findall(r'new Cordon\("([a-z_]+)", (\d+), (\d+), ([\d.]+)F, ([\d.]+)F, (\d+), '
                          r'"(c:[a-z_/]+)", "(#[0-9a-f]{6})"\)',
                          strip_comments(read(PURE, "WeaponRules.java"), True))
if not cordon_rungs:
    fail("WeaponRules names no Cordon rungs, so Valoria's gear has no tiers here")
roster_path = os.path.join(os.path.dirname(WORK),
                           "elysium-ores/src/main/java/com/elysium/ores/rules/CordonMaterials.java")
roster_src = strip_comments(read(roster_path), keep_strings=True)
if not roster_src:
    fail("elysium-ores' CordonMaterials.java is missing, so the Cordon's rungs stand on nothing")
else:
    tool_sets = {}
    for valoria, family in re.findall(r'new Set\("([a-z_]+)", "([a-z_]+)", true,', roster_src):
        tool_sets[family] = valoria
    tiers_in_roster = {}
    for family, level, uses, speed, damage, enchant in re.findall(
            r'"([a-z_]+)", (?:Kind\.\w+, )?(?:OreRules\.\w+, )?Element\.\w+, "#[0-9a-f]{6}",\s*'
            r'(?:null|new Tier\((\d+), (\d+), ([\d.]+)F, ([\d.]+)F, (\d+)\))', roster_src):
        if level:
            tiers_in_roster[family] = (int(level), int(uses), float(speed), float(damage), int(enchant))
    colours_in_roster = dict(re.findall(r'\b(?:dug|alloy|found)\("([a-z_]+)",(?:[^"]*"[^"]*")*?[^"]*?"(#[0-9a-f]{6})"',
                                        roster_src))
    ours = {row[0]: row for row in cordon_rungs}
    for family, valoria in sorted(tool_sets.items()):
        if family not in ours:
            fail(f"Valoria's {valoria} set had tools and stands on {family}, and the arsenal has no "
                 f"rung for it; the gear and the materials disagree")
            continue
        _, level, uses, speed, damage, enchant, material, colour = ours[family]
        wanted_tier = tiers_in_roster.get(family)
        if wanted_tier is None:
            fail(f"{family} is a rung here and has no tool tier in the roster")
        elif wanted_tier != (int(level), int(uses), float(speed), float(damage), int(enchant)):
            fail(f"{family}'s tier is {(int(level), int(uses), float(speed), float(damage), int(enchant))} "
                 f"here and {wanted_tier} in elysium-ores; two lists typed twice have stopped agreeing")
        if colours_in_roster.get(family) != colour:
            fail(f"{family} is drawn in {colour} here and {colours_in_roster.get(family)} in elysium-ores, "
                 f"so a Cordon sword is not the colour of the ingot it is made of")
        stem = material.split("/")[-1]
        if stem != family:
            fail(f"{family}'s weapons are made of {material}, which is another material's tag")
    for family in ours:
        if family not in tool_sets:
            fail(f"{family} is a rung here and no Valoria set with tools stands on it in the roster")
    # Every rung has every weapon and every bow in the game, and the durability the bows need.
    ranged_items_src = strip_comments(read(SRC, "registry/RangedItems.java"), True)
    if "if (rung.cordon()) { made.put(rung.name(), rung.uses()); }" not in squashed(ranged_items_src):
        fail("the Cordon's bows have no durability, so every one of them would be unbreakable — or "
             "the registry would refuse to start")
    arsenal_items_src = squashed(strip_comments(read(SRC, "registry/ArsenalItems.java"), True))
    if "new SimpleTier(incorrectFor(rung.level()), rung.uses(), rung.speed(), rung.damage(), rung.enchant()," \
            not in arsenal_items_src:
        fail("a Cordon rung's Tier is not built from the rung's own numbers")
    _plain_items = strip_comments(read(SRC, "registry/ArsenalItems.java"), True)
    if not (0 <= _plain_items.find("GAME = Map.of(") < _plain_items.find("TIERS = tiers();")):
        fail("the game's tier map is declared after the TIERS field that reads it; static fields "
             "initialise in declaration order, so TIERS reads a null map and the mod crashes at the "
             "title screen — which is how the first jar of this did")
    if "if (made.size() != WeaponRules.RUNGS.size()) {" not in arsenal_items_src:
        fail("nothing counts the tiers built against the rungs, so a rung can go without a Tier and "
             "every weapon at it would throw on registration")
    print(f"the cordon's rungs : {len(ours)} tiers from elysium-ores' roster, each with Valoria's own "
          f"numbers, its material's tag and its ingot's colour")

# ---------------------------------------------------------------------------
# 6. The seam
# ---------------------------------------------------------------------------

main_class = read(SRC, "ElysiumArsenal.java")
if f'MODID = "{NS}"' not in main_class:
    fail("the module id is not what everything else in the tree spells it")
for registered in ("ArsenalItems.ITEMS.register", "ArsenalItems.TABS.register"):
    if registered not in main_class:
        fail(f"{registered} is never called, so part of the module is written and none "
             f"of it exists")

# ---------------------------------------------------------------------------
# The weapons, and the only thing that makes a hundred of them worth having
# ---------------------------------------------------------------------------
#
# Five weapon mods in this pack add about a hundred swords between them. What a
# player ends up with is three. The other ninety-seven are not badly made; they
# are simply never compared to each other, and a weapon that is beaten on every
# axis by another is dead content that nobody will ever find out about, because
# finding out means comparing it against everything else.

weapon_rules = read(PURE, "WeaponRules.java")
weapon_plain = strip_comments(weapon_rules)

beating = squashed(body_of(weapon_plain, "public boolean dominates(Weapon other)"))
if "return atLeastAsGoodAs(other) && betterSomewhereThan(other);" not in beating:
    fail("what it means for one weapon to make another pointless is no longer 'at "
         "least as good everywhere and better somewhere', so a weapon that is worse at "
         "nothing passes")

good = squashed(body_of(weapon_plain, "public boolean atLeastAsGoodAs(Weapon other)"))
for axis in ("damage >= other.damage()", "speed >= other.speed()",
             "reach >= other.reach()", "sweep >= other.sweep()",
             "pierce >= other.pierce()"):
    if axis not in good:
        fail(f"the comparison no longer looks at {axis.split()[0]}, so a weapon that is "
             f"worse only in that can be declared dead content, or one that is better "
             f"only in that can be declared alive")

crowns = squashed(body_of(weapon_plain, "public static List<String> bestAtNothing()"))
if "if (!(fastest || hardest || longest || widest || crowds || sharpest || deadliest)) {" \
        not in crowns:
    fail("a weapon no longer has to be the best at something. Not being dominated is a "
         "weaker claim: a weapon can be middling on every axis, beaten outright by "
         "nobody and chosen by nobody either")

climbing = squashed(body_of(weapon_plain, "public static List<String> flatLadders()"))
if "for (int rung = 1; rung < LADDER.size(); rung++) {" not in climbing:
    fail("the ladder is walked in some order other than the order it climbs. Gold sits "
         "between iron and diamond in the registry and below both in damage, so a "
         "check that walks the registry reports every weapon in the module as broken")

if "\"golden\"" in squashed(strip_comments(weapon_rules, keep_strings=True)).split(
        "LADDER =")[-1][:200]:
    fail("gold is on the ladder. It is a side-grade — quick and weak — and putting it "
         "on the ladder is making the list tidy at the cost of being wrong about the "
         "game")

game = squashed(body_of(weapon_plain, "public static List<String> outclassesTheGame()"))
if "if (ours.dominates(sword)) {" not in game:
    fail("nothing checks our weapons against the game's own sword. A weapon set whose "
         "entries beat a diamond sword on every axis has not added six weapons, it has "
         "deleted one")

item = strip_comments(read(SRC, "item/WeaponItem.java"))
made = squashed(body_of(item, "private static ItemAttributeModifiers attributes("))
if "float stoppable = made.damage() * (1.0F - made.pierce());" not in made:
    fail("a piercing weapon's damage is no longer split at the source, so its armour-"
         "bypassing half is added on top and it is simply a stronger weapon than the "
         "rules say — which is the one thing the domination check cannot see")
if "made.speed() - 4.0D" not in made:
    fail("attack speed is no longer converted out of the game's own convention, so "
         "every weapon here swings at a speed nothing proved anything about")

piercing = squashed(body_of(item, "private void pierce(ServerLevel level,"))
if "if (weapon.pierce() <= 0.0F) { return; }" not in piercing:
    fail("every weapon deals armour-bypassing damage, including the five that do not "
         "pierce, so the estoc has no reason to exist")

sweeping = squashed(body_of(item, "private void sweep(ServerLevel level,"))
if "if (taken >= weapon.sweep() - 1) { break; }" not in sweeping:
    fail("a sweep hits whatever happens to be nearby rather than the number the rules "
         "state, so a greatsword's three is a suggestion")

registering = squashed(read(SRC, "registry/ArsenalItems.java"))
if "if (made.size() != WeaponRules.catalogue().size()) {" not in registering:
    fail("nothing counts the weapons registered against the weapons the rules list, so "
         "the game can start with one missing and say nothing")

print("the weapons        : none of them beaten on every axis by another, each the best "
      "at something, and the piercing half split at the source rather than added on top")

# Three kinds of model, and each has to be the right one. A tool or a melee
# weapon is held; a bow has its own held pose and its own draw frames; an
# arrow and a template lie flat, which is correct for them and wrong for
# everything else. Checking one file passed happily while thirty-six weapon
# models were held flat, because nothing had ever looked at them.
models = os.path.join(RES, "assets", NS, "models/item")
bow_shapes = set(re.findall(r'new Bow\("([a-z_]+)"',
                            strip_comments(read(PURE, "RangedRules.java"), True)))
# The rungs a weapon or a bow comes in: the game's six and the Cordon's,
# read off WeaponRules the way the generator reads them. The broad tools stay
# on ArsenalRules' six.
weapon_tiers = re.findall(r'game\("([a-z]+)",', strip_comments(read(PURE, "WeaponRules.java"), True))
cordon_rungs = re.findall(r'new Cordon\("([a-z_]+)", (\d+), (\d+), ([\d.]+)F, ([\d.]+)F, (\d+), '
                          r'"(c:[a-z_/]+)", "(#[0-9a-f]{6})"\)',
                          strip_comments(read(PURE, "WeaponRules.java"), True))
weapon_tiers += [row[0] for row in cordon_rungs]
if len(weapon_tiers) < 7:
    fail("WeaponRules names fewer rungs than the game's six and one of the Cordon's, so the "
         "roster was not read at all")
bow_ids = {f"{tier}_{shape}" for tier in weapon_tiers for shape in bow_shapes}
unique_rows = re.findall(r'unique\("([a-z_]+)", "([a-z_]+)", "([a-z]+)"',
                         strip_comments(read(PURE, "UniqueRules.java"), True))
for id, shape, _ in unique_rows:
    if shape in bow_shapes:
        bow_ids.add(id)
flat_on_purpose = {f"{name}" for name, _, bolt in re.findall(
    r'new Shot\("([a-z_]+)", ([\d.]+)F, "[a-z]*", (true|false)\)',
    strip_comments(read(PURE, "RangedRules.java"), True))}
flat_on_purpose.add("legend_sigil")


# ---------------------------------------------------------------------------
# The bows, their ammunition, and the twenty-four that have names
# ---------------------------------------------------------------------------
#
# The arithmetic above is about whether the set is worth having. This is about
# whether it is in the game at all: every one of them drawn, modelled, named,
# craftable, registered, counted, and — for the bows — able to animate a draw.

ranged_src = strip_comments(read(PURE, "RangedRules.java"), True)
shot_rows = re.findall(
    r'new Shot\("([a-z_]+)", ([\d.]+)F, "([a-z]*)", (true|false)\)', ranged_src)

wanted = []
for tier in weapon_tiers:
    for shape in sorted(bow_shapes):
        wanted.append((f"{tier}_{shape}", True))
melee_kinds = [k for k in re.findall(r'new Weapon\("([a-z_]+)"',
                                     strip_comments(read(PURE, "WeaponRules.java"), True)) if k != "sword"]
for tier in weapon_tiers:
    for kind in melee_kinds:
        wanted.append((f"{tier}_{kind}", False))
for name, _, _, _ in shot_rows:
    wanted.append((name, False))
for id, shape, _ in unique_rows:
    wanted.append((id, shape in bow_shapes))
wanted.append(("legend_sigil", False))

absent = []
for id, is_bow in wanted:
    for kind, where in (("a sprite", f"assets/{NS}/textures/item/{id}.png"),
                        ("a model", f"assets/{NS}/models/item/{id}.json"),
                        ("a recipe", f"data/{NS}/recipe/{id}.json")):
        if not os.path.isfile(os.path.join(RES, where)):
            absent.append(f"{id} has no {kind}")
    if f"item.{NS}.{id}" not in lang:
        absent.append(f"{id} has no name in the language file")
    if is_bow:
        for frame in range(3):
            drawn = f"{id}_pulling_{frame}"
            for where in (f"assets/{NS}/textures/item/{drawn}.png",
                          f"assets/{NS}/models/item/{drawn}.json"):
                if not os.path.isfile(os.path.join(RES, where)):
                    absent.append(f"{id} has no frame {frame} of its draw")
if absent:
    fail(f"{len(absent)} of {len(wanted) * 4} things a ranged or named weapon needs "
         f"are missing: " + ", ".join(absent[:5]))

# Our arrows have to be in the game's own arrow tag. Without it no bow in the
# game will accept one of them, ours included, and the failure is silent: the
# bow simply refuses to draw and nothing is logged.
arrow_tag = read(RES, "data/minecraft/tags/item/arrows.json")
for name, _, _, bolt in shot_rows:
    if bolt == "true":
        continue
    if f"{NS}:{name}" not in arrow_tag:
        fail(f"{name} is not in the game's arrow tag, so no bow will accept it and "
             f"the only symptom is a bow that will not draw")

# A unique is smithed from its own shape at its own tier. Anything else lets a
# player skip the weapon it is a version of, which is the whole ladder; and a
# recipe whose base is another module's item would make this module need that
# module to be installed.
for id, shape, tier in unique_rows:
    recipe = read(RES, "data", NS, f"recipe/{id}.json")
    if not recipe:
        continue
    body = json.loads(recipe)
    if body.get("type") != "minecraft:smithing_transform":
        fail(f"{id} is crafted rather than smithed, so it can be made without ever "
             f"making the weapon it is a version of")
        continue
    base = body.get("base", {}).get("item", "")
    if base != f"{NS}:{tier}_{shape}":
        fail(f"{id} is smithed from {base} rather than from a {tier} {shape}, so "
             f"either it skips a tier or it is a different weapon underneath")
    addition = body.get("addition", {}).get("item", "")
    if not addition.startswith("minecraft:"):
        fail(f"{id} costs {addition}, which is not the game's own — so this module "
             f"would need another one installed to make its uniques")

# Registration, per registry, counted against the roster it walked. A registry
# that walks the roster and stops halfway registers a prefix of it and says
# nothing, and the count is the only thing that notices.
ranged_items = squashed(strip_comments(read(SRC, "registry/RangedItems.java"), True))
if "for (RangedRules.Bow kind : RangedRules.KINDS) {" not in ranged_items:
    fail("the bows are not registered from the catalogue, so a seventh kind is six "
         "more declarations and six more chances to give one of them the wrong tier")
for statement in (
        "if (made.size() != RangedRules.catalogue().size()) { throw new "
        "IllegalStateException(",
        "if (made.size() != RangedRules.SHOTS.size()) { throw new "
        "IllegalStateException("):
    if statement not in ranged_items:
        fail("nothing counts what the ranged registry registered against the roster "
             "it walked, so the game can start with one missing and say nothing")

unique_items = squashed(strip_comments(read(SRC, "registry/UniqueItems.java"), True))
if "for (UniqueRules.Unique unique : UniqueRules.UNIQUES) {" not in unique_items:
    fail("the uniques are not registered from the roster")
if "if (made.size() != UniqueRules.UNIQUES.size()) { throw new IllegalStateException(" \
        not in unique_items:
    fail("nothing counts the uniques registered against the roster, so the game can "
         "start with one missing and say nothing")
if "ElysiumAbilityRules.cannotShareAnItem(unique.abilities());" not in unique_items:
    fail("a unique added by hand later would never be asked whether the abilities it "
         "carries are a set any item may carry")

# A bow that does not describe its draw holds one picture for the whole draw
# and then fires, which reads as a bow that has stopped working rather than as
# a missing model — so nobody would ever report it.
client = squashed(strip_comments(read(SRC, "client/ArsenalClient.java"), True))
for statement, why in (
        ("for (DeferredItem<? extends Item> held : RangedItems.BOWS.values()) {",
         "the draw is registered for named bows only, or for none"),
        ("if (described != expected) { throw new IllegalStateException(",
         "nothing counts the bows that know how to draw against the bows there are"),
        ("event.enqueueWork(ArsenalClient::describeEveryBow);",
         "registration happens on the setup thread, where it is not safe and "
         "silently loses entries")):
    if statement not in client:
        fail("the draw: " + why)

tab = squashed(strip_comments(read(SRC, "registry/ArsenalItems.java"), True))
for statement in ("for (String id : RangedRules.catalogue()) {",
                  "for (RangedRules.Shot shot : RangedRules.SHOTS) {",
                  "for (UniqueRules.Unique unique : UniqueRules.UNIQUES) {"):
    if statement not in tab:
        fail("the creative tab does not walk one of the rosters, so some of what the "
             "module adds cannot be found in it")

for registered in ("RangedItems.ITEMS.register", "UniqueItems.ITEMS.register"):
    if registered not in main_class:
        fail(f"{registered} is never called, so part of the module is written and "
             f"none of it exists")

# What an arrow does beyond arriving. Every one of these was a way of turning
# twelve kinds of ammunition back into one kind with twelve names.
effects = strip_comments(read(SRC, "event/ArrowEffects.java"), True)
behind = squashed(body_of(effects, "public static RangedRules.Shot shotBehind("))
if "ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());" not in behind:
    fail("which shot an arrow was is not read off the item it carries, so an arrow "
         "fired by a skeleton, a dispenser or the game's own bow does nothing at all "
         "and only the arrows we fire ourselves work")
if "if (id == null || !ElysiumArsenal.MODID.equals(id.getNamespace())) {" not in behind:
    fail("every arrow in the game is looked up in our roster by path alone, so "
         "another mod's arrow of the same name would fire our effect")

applying = squashed(body_of(effects, "private static void apply(ServerLevel level,"))
for effect in ("bleed", "sundering", "slowing", "marking", "burning", "piercing",
               "chaining"):
    if f'case "{effect}"' not in applying:
        fail(f"an arrow that says it does {effect} does nothing, so it is a flint "
             f"arrow with a different name and a different recipe")

piercing = squashed(body_of(effects, "private static void pierce(ServerLevel level,"))
if "hit.hurt(new DamageSource(type, arrow, shooter), dealt * PIERCE_SHARE);" not in \
        piercing:
    fail("a piercing shot's armour-bypassing half is not a share of what the hit "
         "actually took, so it is a share of a number armour, resistance and every "
         "other mod in the pack never saw")

chaining = squashed(body_of(effects, "private static void chain(ServerLevel level,"))
if "other -> other != hit && other != shooter && other.isAlive()" not in chaining:
    fail("a chain can come back to what it just left or reach the shooter, which is "
         "an arrow that kills the person who fired it")
if ("nearest.hurt(level.damageSources().indirectMagic(arrow, shooter), "
        "dealt * CHAIN_SHARE);") not in chaining:
    fail("the second target of a chain is hit by something other than the chain, so "
         "it can chain again and a herd is one arrow")

print(f"the bows and names : {len(weapon_tiers) * len(bow_shapes)} bow(s) in "
      f"{len(bow_shapes)} kinds, {len(shot_rows)} kind(s) of ammunition and "
      f"{len(unique_rows)} named weapon(s), every one drawn, modelled, named, "
      f"smithed or crafted, registered and counted")

# ---------------------------------------------------------------------------
# Reforging
# ---------------------------------------------------------------------------
#
# The arithmetic above proves the gamble is a gamble. This is about whether the
# gamble is in the game: three hammers a player can make, forty-five grades
# with names, a component that survives a save, and a grade that is read back
# rather than baked in.

forge_src = strip_comments(read(PURE, "ForgeRules.java"), True)
grade_rows = re.findall(r'grade\("([a-z_]+)", "([a-z]+)", \d+', forge_src)
hammer_rows = re.findall(r'"([a-z_]+)", List\.of\(([^)]*)\)',
                         re.search(r"HAMMERS = Map\.of\((.*?)\);", forge_src,
                                   re.S).group(1))
gear_rows = re.findall(r'"([a-z]+)"', re.search(
    r"KINDS =\s*List\.of\(([^)]*)\)", forge_src).group(1))

absent = []
for hammer, _ in hammer_rows:
    for kind, where in (("sprite", f"assets/{NS}/textures/item/{hammer}.png"),
                        ("model", f"assets/{NS}/models/item/{hammer}.json"),
                        ("recipe", f"data/{NS}/recipe/{hammer}.json")):
        if not os.path.isfile(os.path.join(RES, where)):
            absent.append(f"{hammer} has no {kind}")
    if f"item.{NS}.{hammer}" not in lang:
        absent.append(f"{hammer} has no name in the language file")
for grade, _ in grade_rows:
    if f"{NS}.grade.{grade}" not in lang:
        absent.append(f"{grade} has no name, so its tooltip reads as its id")
for kind in gear_rows:
    if f"{NS}.gear.{kind}" not in lang:
        absent.append(f"{kind} has no name, so a hammer's tooltip reads as an id")
for key in ("forge.rolls", "forge.how", "forge.wrong_hammer", "forge.too_poor"):
    if f"{NS}.{key}" not in lang:
        absent.append(f"{key} has no wording, so the hammer says nothing when it "
                      f"refuses")
if absent:
    fail(f"{len(absent)} thing(s) reforging needs are missing: "
         + ", ".join(absent[:5]))

# The grade has to survive a save and a network packet, or every sword loses
# its grade the moment a player logs out and back in.
components = squashed(strip_comments(read(SRC, "forge/ForgeComponents.java"), True))
if ".persistent(Codec.STRING)" not in components:
    fail("a grade is not saved, so every piece of gear in the world is ungraded "
         "again the moment the server restarts")
if ".networkSynchronized(ByteBufCodecs.STRING_UTF8)" not in components:
    fail("a grade never reaches the client, so the tooltip is blank on every "
         "machine that is not the server")

main_class_squashed = squashed(strip_comments(main_class, True))
if "com.elysium.arsenal.forge.ForgeComponents.TYPES.register(modEventBus);" not in \
        main_class_squashed:
    fail("the grade component is written and never registered, which is a crash the "
         "first time anybody picks up a sword")
if "com.elysium.arsenal.registry.ForgeItems.ITEMS.register(modEventBus);" not in \
        main_class_squashed:
    fail("the hammers are written and never registered")

events = strip_comments(read(SRC, "forge/ForgeEvents.java"), True)
assigning = squashed(body_of(events, "public static void assignTo("))
if "if (ForgeComponents.gradeOn(stack) != null) { return; }" not in assigning:
    fail("gear is regraded every time it is picked up, so dropping a sword and "
         "collecting it again is a free hammer that costs no experience and wears "
         "nothing out — and every number the harness proves is about a mechanic the "
         "game does not have")

applying = squashed(body_of(events, "public static void onAttributes("))
if "ForgeRules.Grade grade = ForgeRules.grade(id);" not in applying:
    fail("what a grade does is not read out of the rules at the moment it is asked, "
         "so a grade retuned in an update leaves every sword already in a world "
         "carrying the old numbers with nothing to say which is which")
if "if (grade == null) { return; }" not in applying:
    fail("a grade whose name no longer exists is not handled, so a world saved under "
         "an older roster crashes rather than treating the gear as ungraded")

# Every attribute a grade names must be one the game of 1.21.1 has. The roster
# names them plainly ("attack_damage"); the game files them under "generic." and
# the player's own under "player.", and a name under neither is a grade that
# silently does nothing -- which every grade was until 27 September 2026.
naming = squashed(body_of(events, "public static Holder<Attribute> attributeNamed("))
if "for (String prefix : PREFIXES)" not in naming or '"generic.", "player."' not in events:
    fail("a grade's attribute is looked up under its plain name alone, which the game "
         "of 1.21.1 does not have (it files attack_damage under generic. and "
         "block_break_speed under player.), so every grade resolves to no attribute "
         "and a heavy sword hits as hard as a plain one")
_game_attributes = {
    "generic.armor", "generic.armor_toughness", "generic.attack_damage", "generic.attack_knockback",
    "generic.attack_speed", "generic.block_interaction_range", "generic.burning_time",
    "generic.entity_interaction_range", "generic.explosion_knockback_resistance",
    "generic.fall_damage_multiplier", "generic.flying_speed", "generic.follow_range", "generic.gravity",
    "generic.jump_strength", "generic.knockback_resistance", "generic.luck", "generic.max_absorption",
    "generic.max_health", "generic.movement_efficiency", "generic.movement_speed", "generic.oxygen_bonus",
    "generic.safe_fall_distance", "generic.scale", "generic.step_height", "generic.water_movement_efficiency",
    "player.block_break_speed", "player.block_interaction_range", "player.entity_interaction_range",
    "player.mining_efficiency", "player.sneaking_speed", "player.submerged_mining_speed",
    "player.sweeping_damage_ratio"}
_mc_jar_for_attributes = os.environ.get("MC_JAR")
if _mc_jar_for_attributes and os.path.exists(_mc_jar_for_attributes):
    import zipfile as _zipfile
    with _zipfile.ZipFile(_mc_jar_for_attributes) as _z:
        _lang = json.loads(_z.read("assets/minecraft/lang/en_us.json").decode("utf-8"))
    _game_attributes = {k[len("attribute.name."):] for k in _lang if k.startswith("attribute.name.")}
_rules_src = strip_comments(read(SRC, "rules/ForgeRules.java"), True)
_named = set(re.findall(r'(?:share|flat|add)\("([a-z_.:]+)"', _rules_src))
for _name in sorted(_named):
    _candidates = [_name] if ":" in _name else [_name, "generic." + _name, "player." + _name]
    if not any(c.removeprefix("minecraft:") in _game_attributes for c in _candidates):
        fail(f"the grade roster names the attribute '{_name}', which the game of 1.21.1 does not "
             f"have under that name, generic. or player., so every grade that changes it does nothing")
_attribute_note = f"{len(_named)} attribute(s) named by the grades, every one the game has"

colouring = squashed(body_of(events, "public static ChatFormatting colourOf("))
if "float worth = grade.worth();" not in colouring:
    fail("a grade's colour is not read from what it is worth, so a grade retuned "
         "from a loss into a win goes on reading grey while making the gear better")

kinds_src = strip_comments(read(SRC, "forge/ForgeKinds.java"), True)
of_stack = squashed(body_of(kinds_src, "public static String of(ItemStack stack)"))
for statement, why in (
        ("if (stack.getItem() instanceof ShieldItem) { return \"shield\"; }",
         "a shield is not told from a weapon"),
        ("if (stack.getItem() instanceof DiggerItem) { return \"tool\"; }",
         "a paxel is a weapon on one machine and a tool on another, and the two "
         "disagree about what it is worth")):
    if statement not in of_stack:
        fail("what kind of gear a thing is: " + why)
if "instanceof ProjectileWeaponItem" not in of_stack:
    fail("only our own bows can be graded, so the mechanic is invisible to a player "
         "carrying anybody else's")

hammer_src = strip_comments(read(SRC, "item/HammerItem.java"), True)
using = squashed(body_of(hammer_src, "public InteractionResultHolder<ItemStack> use("))
for statement, why in (
        ("int experience = current != null ? current.experience() : cheapest(kind);",
         "the price is not read off the grade the gear already has, so rolling away "
         "from a Fabled sword costs what rolling away from a Dull one costs and the "
         "right play is to hold the hammer down"),
        ("player.giveExperienceLevels(-experience);",
         "reforging is free, so the odds the harness proves describe a gamble with "
         "nothing at stake"),
        ("held.hurtAndBreak(wear, player, LivingEntity.getSlotForHand(hand));",
         "the hammer never wears out")):
    if statement not in using:
        fail("a swing of the hammer: " + why)
if "if (kind == null || !rolls(kind)) {" not in using:
    fail("any hammer reforges any gear, so which hammer a player made does not "
         "matter and two of the three are decoration")

forge_items = squashed(strip_comments(read(SRC, "registry/ForgeItems.java"), True))
if "for (String hammer : new TreeMap<>(ForgeRules.HAMMERS).keySet()) {" not in \
        forge_items:
    fail("the hammers are not registered from the rules' own table, so a fourth one "
         "added there is a kind of gear nothing can reforge and the only symptom is "
         "a player wondering why their shield never changes")
if "if (made.size() != ForgeRules.HAMMERS.size()) { throw new IllegalStateException(" \
        not in forge_items:
    fail("nothing counts the hammers registered against the table")

print(f"grade attributes   : {_attribute_note}")
print(f"reforging          : {len(grade_rows)} grade(s) across {len(gear_rows)} kinds "
      f"of gear, {len(hammer_rows)} hammer(s) that roll them, every grade named and "
      f"every hammer craftable")

# ---------------------------------------------------------------------------
# The digging
# ---------------------------------------------------------------------------

dig_src = strip_comments(read(PURE, "DigRules.java"), True)
brush_rows = re.findall(r'new Brush\("([a-z_]+)", (\d+), [\d.]+F\)', dig_src)
site_rows = re.findall(r'new Site\("([a-z_]+)", "([a-z:_]+)", "([a-z_]+)"\)', dig_src)
fossil_rows = re.findall(
    r'new Fossil\("([a-z_]+)", "([a-z_]+)", "([a-z_]+)", [\d.]+F, \d+\)',
    strip_comments(read(PURE, "FossilRules.java"), True))
artifact_rows = re.findall(
    r'new Artifact\("([a-z_]+)", "([a-z_]+)", [\d.]+F, \d+, "([a-z]+)"\)',
    strip_comments(read(PURE, "ArtifactRules.java"), True))
totem_rows = re.findall(r'new Totem\("([a-z_]+)", (PLACED|HELD), [\d.]+F, \d+, \d+\)',
                        strip_comments(read(PURE, "TotemRules.java"), True))
how_many_stages = int(re.search(r"STAGES = (\d+);", dig_src).group(1))

absent = []
for name, _ in brush_rows:
    for kind, where in (("sprite", f"assets/{NS}/textures/item/{name}.png"),
                        ("model", f"assets/{NS}/models/item/{name}.json"),
                        ("recipe", f"data/{NS}/recipe/{name}.json")):
        if not os.path.isfile(os.path.join(RES, where)):
            absent.append(f"{name} has no {kind}")
    if f"item.{NS}.{name}" not in lang:
        absent.append(f"{name} has no name in the language file")

# Every block the digging adds needs four things, and a missing one of each
# fails differently: no blockstate is a purple-and-black cube, no model is the
# same, no loot table is a block that vanishes when broken, and no name is a
# block called by its id.
dug_blocks = [block for block, _, _ in site_rows]
for creature, _, _ in fossil_rows:
    dug_blocks += [f"{creature}_fossil", f"{creature}_fossil_head",
                   f"{creature}_fossil_body"]
dug_blocks += [name for name, kind in totem_rows if kind == "PLACED"]
dug_blocks += ["chiseled_bone_block", "archeology_table", "rotten_log",
               "rotten_planks", "rotten_slab", "rotten_stairs", "rotten_fence",
               "rotten_fence_gate", "rotten_door", "rotten_trapdoor",
               "rotten_pressure_plate", "cracked_mud_bricks",
               "cracked_mud_brick_slab", "cracked_mud_brick_stairs",
               "infested_mud_bricks"]
for block in dug_blocks:
    for kind, where in (("blockstate", f"assets/{NS}/blockstates/{block}.json"),
                        ("item model", f"assets/{NS}/models/item/{block}.json"),
                        ("loot table",
                         f"data/{NS}/loot_table/blocks/{block}.json")):
        if not os.path.isfile(os.path.join(RES, where)):
            absent.append(f"{block} has no {kind}")
    if f"block.{NS}.{block}" not in lang:
        absent.append(f"{block} has no name in the language file")

# A site has a model per stage of its brushing. One fewer than there are
# stages is a blockstate pointing at a model that is not there, which is a
# purple-and-black cube at the moment a player is halfway through a dig.
for block, _, _ in site_rows:
    states = read(RES, "assets", NS, f"blockstates/{block}.json")
    if states:
        variants = json.loads(states).get("variants", {})
        if len(variants) != how_many_stages:
            absent.append(f"{block} has {len(variants)} of {how_many_stages} "
                          f"stages drawn")
    for stage in range(how_many_stages):
        if not os.path.isfile(os.path.join(
                RES, f"assets/{NS}/models/block/{block}_{stage}.json")):
            absent.append(f"{block} has no model for stage {stage}")

for name, _, _ in artifact_rows:
    for kind, where in (("sprite",
                         f"assets/{NS}/textures/item/identified_{name}.png"),
                        ("model",
                         f"assets/{NS}/models/item/identified_{name}.json")):
        if not os.path.isfile(os.path.join(RES, where)):
            absent.append(f"identified_{name} has no {kind}")
    if f"item.{NS}.identified_{name}" not in lang:
        absent.append(f"identified_{name} has no name")
for name, kind in totem_rows:
    if kind == "HELD" and f"item.{NS}.{name}" not in lang:
        absent.append(f"{name} has no name")
if absent:
    fail(f"{len(absent)} thing(s) the digging needs are missing: "
         + ", ".join(absent[:6]))

# What is buried has to be worked out from the position, in the game as well
# as in the arithmetic. A block entity that stored it would let a player break
# the block, put it back and roll again for something better.
site_entity = strip_comments(read(SRC, "block/entity/SiteBlockEntity.java"), True)
buried = squashed(body_of(site_entity, "public ItemStack whatIsBuried("))
if ("DigRules.Find find = DigRules.whatIsBuried(pool, level.getSeed(), "
        "getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ());") \
        not in buried:
    fail("what a site holds is not worked out from where the site is, so a player "
         "who dislikes what they found can break the block, put it back and roll "
         "again — and two blocks that ought to hold the same thing do not")
saving = squashed(body_of(site_entity, "protected void saveAdditional("))
if 'tag.putInt("stage", stage);' not in saving:
    fail("how far through a dig a site is is not saved, so a player who walks away "
         "and comes back starts again")
if 'tag.putString("item"' in squashed(site_entity):
    fail("what is buried is written into the block entity, which is the thing "
         "working it out from the position exists to avoid")

brush_item = strip_comments(read(SRC, "item/BrushItem.java"), True)
brushing = squashed(body_of(brush_item, "public void onUseTick("))
for statement, why in (
        ("if (!entity.brushFor(brush.ticks())) { return; }",
         "a brush works at a speed that is not its own, so the ladder is three "
         "items that behave identically"),
        ("level.setBlock(pos, site.leavesBehind(), Block.UPDATE_ALL);",
         "a finished site stays a site, so it can be brushed for ever"),
        ("stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand( "
         "player.getUsedItemHand()));",
         "brushing costs the brush nothing, so the cheapest one is the only one "
         "anybody makes")):
    if statement not in brushing:
        fail("brushing: " + why)
if "BlockPos pos = aimedAt(player);" not in brushing:
    fail("the block being brushed is remembered from the click rather than looked "
         "at each tick, so a player can start on a site and keep brushing it from "
         "across the room")

# A fossil's effect is looked for from the player outward. The other way round
# is twenty-one blocks each searching a room for players, which costs what
# somebody built rather than what they are doing.
fossil_events = strip_comments(read(SRC, "event/FossilEffects.java"), True)
ticking = squashed(body_of(fossil_events, "public static void onTick("))
if "if (event.getServer().getTickCount() % EVERY != 0) { return; }" not in ticking:
    fail("every player looks through a cube of four and a half thousand blocks "
         "twenty times a second, which is the same mistake as having the blocks "
         "search for players and costs more")
looking = squashed(body_of(fossil_events, "public static Map<String, Float> around("))
if "float worth = FossilRules.given(one, skulls.containsKey(one.creature()), " \
        "bodies.containsKey(one.creature()));" not in looking:
    fail("what a fossil gives is not the rules' own answer about its halves, so "
         "the arithmetic proving a pair is worth more than two halves is about a "
         "different fossil")

totem_block = strip_comments(read(SRC, "block/TotemBlock.java"), True)
ticking = squashed(body_of(totem_block, "public void tick(BlockState state,"))
if "level.scheduleTick(pos, this, totem.every());" not in ticking:
    fail("a placed totem acts once and stops, or acts on the world's own tick "
         "rather than on its own period — and the arithmetic capping what it does "
         "in a minute is about a period it does not use")

artifact_events = strip_comments(read(SRC, "event/ArtifactEffects.java"), True)
strength = squashed(body_of(artifact_events, "public static float strengthOn("))
if "return found.at(ForgeComponents.artifactLevel(stack));" not in strength:
    fail("what an artefact does is not read out of its own rules at its own level, "
         "so deepening one buys whatever this file thinks rather than what the "
         "roster says")
tunnelling = squashed(body_of(artifact_events, "public static void onBreak("))
if "if (DIGGING.get()) { return; }" not in tunnelling:
    fail("breaking a block calls back into the break that broke it, so one swing "
         "digs to bedrock")
if "if (!tool.isCorrectToolForDrops(under)) { return; }" not in tunnelling:
    fail("a tunnelling pickaxe takes the dirt under the stone, so it is not a "
         "pickaxe that digs deeper, it is every tool at once")

dig_blocks_src = squashed(strip_comments(read(SRC, "registry/DigBlocks.java"), True))
for statement, why in (
        ("for (DigRules.Site site : DigRules.SITES) {", "the sites"),
        ("for (FossilRules.Fossil one : FossilRules.FOSSILS) {", "the fossils"),
        ("for (TotemRules.Totem one : TotemRules.of(TotemRules.PLACED)) {",
         "the placed totems")):
    if statement not in dig_blocks_src:
        fail(f"{why} are not registered from their own roster, so one added there "
             f"is a block that does not exist and nothing says so")
for counted in ("if (made.size() != DigRules.SITES.size()) { throw new "
                "IllegalStateException(",
                "if (made.size() != FossilRules.catalogue().size()) { throw new "
                "IllegalStateException("):
    if counted not in dig_blocks_src:
        fail("nothing counts what the block registry registered against the roster "
             "it walked")

dig_items_src = squashed(strip_comments(read(SRC, "registry/DigItems.java"), True))
if "for (Map.Entry<String, DeferredBlock<net.minecraft.world.level.block.Block>> " \
        "row : blocks.entrySet()) {" not in dig_items_src:
    fail("the block items are not made by walking the blocks, so a block "
         "registered without one is a block that exists, generates and cannot be "
         "picked up — which looks exactly like a block that does not exist")
if "if (made.size() != ArtifactRules.ARTIFACTS.size()) { throw new " \
        "IllegalStateException(" not in dig_items_src:
    fail("nothing counts the artefacts registered against the roster, so "
         "identifying can land on one that was never registered")

for registered in ("DigBlocks.BLOCKS.register", "DigItems.ITEMS.register",
                   "DigBlockEntities.TYPES.register"):
    if registered not in main_class:
        fail(f"{registered} is never called, so part of the digging is written and "
             f"none of it exists")

entities = squashed(strip_comments(read(SRC, "registry/DigBlockEntities.java"), True))
if ("return DigBlocks.SITES.values().stream() .map(DeferredHolder::get)"
        ".toArray(Block[]::new);") not in entities:
    fail("the site block entity is bound to a list of blocks typed by hand, so a "
         "fourth site is a block that drops its block entity the moment anybody "
         "looks at it")

# Block items inherit their block's model; a block item that did not would be
# a floating flat square in the hand. Everything the digging adds that is a
# block is one, and the list is the same one the checks above walk - except a
# door, which is held flat with a sprite of its own, as the game's doors are:
# its block model is half a door, a sliver in the hand.
held_flat = {block for block in dug_blocks if block.endswith("_door")}
block_items = set(dug_blocks) - held_flat
flat_on_purpose |= held_flat
flat_on_purpose |= {f"identified_{name}" for name, _, _ in artifact_rows}
flat_on_purpose |= {name for name, kind in totem_rows if kind == "HELD"}
flat_on_purpose |= {"artifact_shard", "unidentified_artifact"}
handheld_on_purpose = {name for name, _ in brush_rows}

wrong = []
for name in sorted(os.listdir(models)) if os.path.isdir(models) else []:
    if not name.endswith(".json"):
        continue
    id = name[:-5]
    body = read(models, name)
    if id.endswith(("_pulling_0", "_pulling_1", "_pulling_2")):
        # A draw frame inherits its bow's pose. One that inherited
        # item/generated instead would snap flat the instant a draw began,
        # which reads as the bow disappearing.
        parent = id.rsplit("_pulling_", 1)[0]
        if f'"parent": "{NS}:item/{parent}"' not in body:
            wrong.append(f"{id} does not inherit {parent}'s pose")
        continue
    if id in block_items:
        if f'"parent": "{NS}:block/' not in body:
            wrong.append(f"{id} is a block item that does not inherit its block's "
                         f"model, so it is a flat square in the hand")
        continue
    if id in bow_ids:
        if '"overrides"' not in body or '"pulling"' not in body:
            wrong.append(f"{id} is a bow with no draw frames, so it holds one "
                         f"picture through the whole draw")
        if '"thirdperson_righthand"' not in body:
            wrong.append(f"{id} is a bow with no held pose")
    elif id in flat_on_purpose:
        if '"parent": "item/generated"' not in body:
            wrong.append(f"{id} is held like a tool and is not one")
    elif '"parent": "item/handheld"' not in body:
        wrong.append(f"{id} is not held like a tool, so it lies flat in a "
                     f"player's hand")
for complaint in wrong[:8]:
    fail(complaint)
if len(wrong) > 8:
    fail(f"...and {len(wrong) - 8} more models of the wrong kind")


print(f"the digging        : {len(brush_rows)} brush(es), {len(site_rows)} site(s), "
      f"{len(fossil_rows)} fossil(s) in {how_many_stages}-stage digs, "
      f"{len(artifact_rows)} artefact(s) and {len(totem_rows)} totem(s), every "
      f"block drawn, modelled, named, dropping itself and registered")

# ---------------------------------------------------------------------------
# The dig, in a survival world
# ---------------------------------------------------------------------------
#
# Dylan, 29 September 2026: "All content should be available and accessible
# via survival." Until that day the dig was registered, drawn, named and
# brushable, and no world held any of it: nothing laid a site down, brushing
# gave up the game's own things and a shard, and every fossil, totem, rotten
# plank and cracked brick came from a command and from nothing else. Each rule
# below is one link of the chain a survival player walks instead: a site in a
# world, a pool that holds the find, a recipe for what the finds make.

GENERATION_STEPS = {"raw_generation", "lakes", "local_modifications",
                    "underground_structures", "surface_structures", "strongholds",
                    "underground_ores", "underground_decoration", "fluid_springs",
                    "vegetal_decoration", "top_layer_modification"}
DIG_DATA = os.path.join(RES, "data", NS)


def dig_json(*parts):
    text = read(DIG_DATA, *parts)
    try:
        return json.loads(text) if text else None
    except ValueError:
        return None


# A site is in a world when a feature lays it down, the feature is placed with
# the biome filter, and a biome modifier adds it at a step the game has, to a
# set of biomes with something in it.
added_to = {}
_modifiers = os.path.join(DIG_DATA, "neoforge/biome_modifier")
for _name in sorted(os.listdir(_modifiers)) if os.path.isdir(_modifiers) else []:
    _body = dig_json("neoforge/biome_modifier", _name)
    if not isinstance(_body, dict) or _body.get("type") != "neoforge:add_features":
        continue
    _features = _body.get("features")
    for _feature in [_features] if isinstance(_features, str) else _features or []:
        added_to.setdefault(_feature, []).append((_name[:-5], _body))

unlaid = []
for site, _, _ in site_rows:
    configured = dig_json("worldgen/configured_feature", f"{site}.json")
    if configured is None or f'"Name": "{NS}:{site}"' not in json.dumps(configured):
        unlaid.append(f"{site} has no feature that lays it down")
        continue
    placed = dig_json("worldgen/placed_feature", f"{site}.json")
    if placed is None or placed.get("feature") != f"{NS}:{site}":
        unlaid.append(f"{site}'s feature is never placed")
        continue
    if {"type": "minecraft:biome"} not in placed.get("placement", []):
        unlaid.append(f"{site} is placed with no biome filter, so its sites spill over "
                      f"the edge of every biome it is added to")
    modifiers = added_to.get(f"{NS}:{site}", [])
    if not modifiers:
        unlaid.append(f"{site} is added to no biome, so no world ever holds one")
    for modifier, body in modifiers:
        if body.get("step") not in GENERATION_STEPS:
            unlaid.append(f"{modifier} adds {site} at the step {body.get('step')!r}, "
                          f"which is not one of the game's eleven, so no world loads")
        biomes = body.get("biomes")
        if isinstance(biomes, str) and biomes.startswith(f"#{NS}:"):
            tag = dig_json("tags/worldgen/biome", biomes.split(":", 1)[1] + ".json")
            if not tag or not tag.get("values"):
                unlaid.append(f"{modifier} adds {site} to {biomes}, which names no "
                              f"biome")
        elif not biomes:
            unlaid.append(f"{modifier} adds {site} to no biome at all")
if unlaid:
    fail(f"{len(unlaid)} thing(s) keep a site out of every survival world: "
         + "; ".join(unlaid[:5]))

# Brushing is the only thing in the game that hands out the fossil halves, the
# totems, the rotten log and infested mud bricks, so each has to be in a pool.
# The fossil pool walks the roster for its halves; everything else is named.
pooling = squashed(body_of(dig_src, "private static Map<String, List<Find>> pools() {"))
walked = ("for (FossilRules.Fossil one : FossilRules.FOSSILS) { "
          "fossil.add(new Find(OURS + one.id(FossilRules.SKULL), HALF_A_FOSSIL)); "
          "fossil.add(new Find(OURS + one.id(FossilRules.BODY), HALF_A_FOSSIL)); }")
if walked not in pooling or 'made.put("fossil", List.copyOf(fossil));' not in pooling:
    fail("the fossil pool is not every skull and every body walked from the fossil "
         "roster, so a fossil added there is one no site gives up and nobody can make "
         "whole")
pooled = set(re.findall(r'new Find\("([a-z0-9_:]+)", \d+\)', pooling))
promised = [name for name, _ in totem_rows] + ["rotten_log", "cracked_mud_bricks",
                                                "infested_mud_bricks"]
unheld = [name for name in promised if f"{NS}:{name}" not in pooled]
if unheld:
    fail(f"{', '.join(unheld)}: promised by the dig and held by no pool in DigRules, "
         f"so a survival player can never have one")

# A pool entry has to name something the game has. The site's block entity looks
# the id up and, finding nothing, hands out nothing, without a word to anyone.
_vanilla_items = set()
if os.environ.get("MC_JAR") and os.path.isfile(os.environ["MC_JAR"]):
    import zipfile as _zipfile
    with _zipfile.ZipFile(os.environ["MC_JAR"]) as _jar:
        _vanilla_items = {n.rsplit("/", 1)[1][:-5] for n in _jar.namelist()
                          if n.startswith("assets/minecraft/models/item/")
                          and n.endswith(".json")}
nothing = []
for item in sorted(pooled):
    namespace, path = item.split(":", 1)
    if namespace == NS:
        if not os.path.isfile(os.path.join(RES, f"assets/{NS}/models/item/{path}.json")) \
                or not (f"item.{NS}.{path}" in lang or f"block.{NS}.{path}" in lang):
            nothing.append(item)
    elif namespace == "minecraft":
        if _vanilla_items and path not in _vanilla_items:
            nothing.append(item)
    else:
        nothing.append(item)
if nothing:
    fail(f"{', '.join(nothing)} is in a pool and is nothing the game has, so the site "
         f"that holds it gives up nothing")

# What the finds make. A pair of halves is for a whole fossil; a rotten log is
# for a rotten set; the badlands' cracked bricks start a set a furnace keeps
# going. Read from what ships, whatever made it.
makes = {}
_recipes = os.path.join(DIG_DATA, "recipe")
for _name in sorted(os.listdir(_recipes)) if os.path.isdir(_recipes) else []:
    _body = dig_json("recipe", _name)
    if not isinstance(_body, dict):
        continue
    _result = _body.get("result")
    _made = _result.get("id") if isinstance(_result, dict) else _result
    if isinstance(_made, str):
        makes.setdefault(_made, []).append(_body)


def takes(body):
    """Every item a recipe takes, however it is laid out."""
    if body.get("type") == "minecraft:crafting_shaped":
        key = body.get("key", {})
        return sorted(key.get(symbol, {}).get("item", "?")
                      for row in body.get("pattern", []) for symbol in row
                      if symbol != " ")
    if body.get("type") == "minecraft:crafting_shapeless":
        return sorted(one.get("item", "?") for one in body.get("ingredients", []))
    return [body.get("ingredient", {}).get("item", "?")]


broken_wholes = []
for creature, _, _ in fossil_rows:
    halves = sorted([f"{NS}:{creature}_fossil_body", f"{NS}:{creature}_fossil_head"])
    if not any(body.get("type") == "minecraft:crafting_shapeless" and takes(body) == halves
               for body in makes.get(f"{NS}:{creature}_fossil", [])):
        broken_wholes.append(creature)
if broken_wholes:
    fail(f"{', '.join(broken_wholes)}: the whole fossil cannot be made from its skull "
         f"and its body, so the block a pair of halves is for is one a survival "
         f"player cannot have")

_wood_shapes = re.findall(r'"(_[a-z_]+)"', re.search(
    r"WOOD_SHAPES = List\.of\(([^)]*)\)", dig_blocks_src).group(1))
_stone_shapes = re.findall(r'"(_[a-z_]+)"', re.search(
    r"STONE_SHAPES = List\.of\(([^)]*)\)", dig_blocks_src).group(1))
unmade = []
if not any(set(takes(body)) == {f"{NS}:rotten_log"}
           for body in makes.get(f"{NS}:rotten_planks", [])):
    unmade.append("rotten_planks is not made from the rotten log")
for shape in _wood_shapes:
    if shape == "_planks":
        continue
    if not any(f"{NS}:rotten_planks" in takes(body)
               and set(takes(body)) <= {f"{NS}:rotten_planks", "minecraft:stick"}
               for body in makes.get(f"{NS}:rotten{shape}", [])):
        unmade.append(f"rotten{shape} is not made from rotten planks")
if unmade:
    fail("the rotten set is not made from what a dig turns up: " + "; ".join(unmade))

cracking = []
if not any(body.get("type") == "minecraft:smelting"
           and takes(body) == ["minecraft:mud_bricks"]
           for body in makes.get(f"{NS}:cracked_mud_bricks", [])):
    cracking.append("cracked mud bricks are not smelted from the game's own mud "
                    "bricks, so the set is only as big as what the badlands give up")
for shape in _stone_shapes:
    if not any(set(takes(body)) == {f"{NS}:cracked_mud_bricks"}
               for body in makes.get(f"{NS}:cracked_mud_brick{shape}", [])):
        cracking.append(f"cracked_mud_brick{shape} is not made from cracked mud "
                        f"bricks")
if cracking:
    fail("the cracked mud bricks: " + "; ".join(cracking))

# The songless disc, retired: a plain item that no jukebox would play and
# nothing handed out. The dirt pool hands out the game's own relic disc in its
# place, and the book's "what else is buried" asks for that one.
retired = "music_disc_relic"
still = []
if retired in dig_items_src:
    still.append("DigItems registers it")
for kind, where in (("model", f"assets/{NS}/models/item/{retired}.json"),
                    ("sprite", f"assets/{NS}/textures/item/{retired}.png")):
    if os.path.isfile(os.path.join(RES, where)):
        still.append(f"its {kind} is shipped")
if f"item.{NS}.{retired}" in lang:
    still.append("it has a name")
if still:
    fail(f"{NS}:{retired} was retired on 29 September 2026, a disc with no song, and "
         f"it is back: " + "; ".join(still))
_dirt_pool = re.search(r'made\.put\("dirt", List\.of\((.*?)\)\);', pooling)
if not _dirt_pool or not re.search(r'new Find\("minecraft:music_disc_relic", \d+\)',
                                   _dirt_pool.group(1)):
    fail("the dirt pool no longer holds the game's own relic disc, which is the disc "
         "the dig hands out in place of the retired one")

# ---------------------------------------------------------------------------
# The two sets, shaped
# ---------------------------------------------------------------------------
#
# Until 29 September 2026 every shape of the rotten and cracked mud brick sets
# was a cube with one model for all its states: a slab stood a full block tall
# over half a block of collision, a fence was a wall nobody saw over, and a
# pressure plate was a block nothing could stand on. Each shape is now its
# block class's own states, drawn with the game's own models and turns, and
# registered as that class; a door drops once and a double slab twice.

FACINGS = ("north", "south", "east", "west")
#: Each shape: the class DigBlocks registers it as, and the states its models
#: have to cover (a fence is a post and four sides, so it has none here).
SHAPE_STATES = {
    "_slab": ("SlabBlock", {"type": ("bottom", "top", "double")}),
    "_stairs": ("StairBlock", {"facing": FACINGS, "half": ("bottom", "top"),
                               "shape": ("straight", "inner_left", "inner_right",
                                         "outer_left", "outer_right")}),
    "_fence": ("FenceBlock", None),
    "_fence_gate": ("FenceGateBlock", {"facing": FACINGS, "in_wall": ("false", "true"),
                                       "open": ("false", "true")}),
    "_door": ("DoorBlock", {"facing": FACINGS, "half": ("lower", "upper"),
                            "hinge": ("left", "right"), "open": ("false", "true")}),
    "_trapdoor": ("TrapDoorBlock", {"facing": FACINGS, "half": ("bottom", "top"),
                                    "open": ("false", "true")}),
    "_pressure_plate": ("PressurePlateBlock", {"powered": ("false", "true")}),
}
#: Each set's stem, its full block, and the game's own set it is drawn after.
SHAPED_SETS = (("rotten", [s for s in _wood_shapes if s != "_planks"], "rotten_planks",
                "oak", "oak_planks"),
               ("cracked_mud_brick", _stone_shapes, "cracked_mud_bricks", "mud_brick",
                "mud_bricks"))


def asset_json(*parts):
    text = read(RES, "assets", NS, *parts)
    try:
        return json.loads(text) if text else None
    except ValueError:
        return None


_registered_as = {}
for _shape, _class in re.findall(r'case "(_[a-z_]+)" -> new (\w+)\(', dig_blocks_src):
    _registered_as.setdefault(_shape, set()).add(_class)

unshaped = []
for stem, shapes, _, _, _ in SHAPED_SETS:
    for shape in shapes:
        id = stem + shape
        if shape not in SHAPE_STATES:
            unshaped.append(f"{id} is a shape nothing here knows the states of")
            continue
        wanted, states = SHAPE_STATES[shape]
        if _registered_as.get(shape) != {wanted}:
            unshaped.append(f"{id} is registered as "
                            f"{' and '.join(sorted(_registered_as.get(shape, {'nothing'})))} "
                            f"and drawn as a {wanted}, so the game refuses the states its "
                            f"blockstate names")
        state = asset_json("blockstates", f"{id}.json") or {}
        if states is None:
            parts = state.get("multipart", [])
            sides = {side for part in parts
                     for side, value in (part.get("when") or {}).items() if value == "true"}
            if not any("when" not in part for part in parts) \
                    or sides != {"north", "east", "south", "west"}:
                unshaped.append(f"{id} is not a post with a side towards each of its four "
                                f"neighbours")
            continue
        keys = [dict(part.split("=", 1) for part in key.split(",") if part)
                for key in state.get("variants", {})]
        if not keys or any(not key for key in keys):
            unshaped.append(f"{id} is one model for every state, so it is drawn a full "
                            f"block whatever shape it stands in")
            continue
        strangers = sorted({name for key in keys for name in key if name not in states})
        if strangers:
            unshaped.append(f"{id} names {', '.join(strangers)}, which its block does not "
                            f"have, so the game refuses the whole blockstate")
        for values in itertools.product(*states.values()):
            wanted_state = dict(zip(states, values))
            matched = sum(1 for key in keys
                          if all(wanted_state.get(k) == v for k, v in key.items()))
            if matched != 1:
                unshaped.append(f"{id} has {matched} model(s) for "
                                + ",".join(f"{k}={v}" for k, v in wanted_state.items()))
                break
if unshaped:
    fail(f"{len(unshaped)} shape(s) of the two sets are not drawn as their shapes: "
         + "; ".join(unshaped[:5]))

# And drawn the game's way. A turn got wrong is a variant that exists and draws
# the wrong way, which no count of states can see: a stair rule that turns
# every corner alike draws most upside-down corners backwards. So each shape's
# blockstate, its model names put in the game's words, is the game's own for
# the same shape (oak for the rotten set, mud brick for the cracked one), and
# every model and item model has the game's parent and texture slots.
_game_jar = None
if os.environ.get("MC_JAR") and os.path.isfile(os.environ["MC_JAR"]):
    import zipfile as _zipfile
    _game_jar = _zipfile.ZipFile(os.environ["MC_JAR"])


def _game(kind, name):
    try:
        return json.loads(_game_jar.read(f"assets/minecraft/{kind}/{name}.json"))
    except (KeyError, ValueError):
        return None


def _canon(state):
    if "variants" in state:
        return {frozenset(key.split(",")): json.dumps(model, sort_keys=True)
                for key, model in state["variants"].items()}
    return sorted(json.dumps(part, sort_keys=True) for part in state.get("multipart", []))


def _ident(value):
    return value if ":" in value else "minecraft:" + value


not_the_games = []
for stem, shapes, full, game, game_full in SHAPED_SETS if _game_jar else ():
    for shape in [s for s in shapes if s in SHAPE_STATES]:
        id, theirs = stem + shape, game + shape
        renamed = [(f'"{NS}:block/{id}', f'"minecraft:block/{theirs}'),
                   (f'"{NS}:block/{full}"', f'"minecraft:block/{game_full}"')]
        said = json.dumps(asset_json("blockstates", f"{id}.json") or {})
        for old, new in renamed:
            said = said.replace(old, new)
        game_state = _game("blockstates", theirs)
        if game_state is None:
            not_the_games.append(f"the game has no {theirs} to draw {id} after")
            continue
        if _canon(json.loads(said)) != _canon(game_state):
            not_the_games.append(f"{id}'s states are not the game's own {theirs}'s")
        names = set(re.findall(rf'"{NS}:block/([a-z_]+)"',
                               json.dumps(asset_json("blockstates", f"{id}.json") or {})))
        for name in sorted(names):
            ours = asset_json("models/block", f"{name}.json") or {}
            other = _game("models", "block/" + name.replace(id, theirs, 1)
                          .replace(full, game_full, 1)) or {}
            if _ident(ours.get("parent", "")) != _ident(other.get("parent", "")) \
                    or set(ours.get("textures", {})) != set(other.get("textures", {})):
                not_the_games.append(f"{name} is not built like the game's "
                                     f"{name.replace(id, theirs, 1)}")
        ours = asset_json("models/item", f"{id}.json") or {}
        other = _game("models", f"item/{theirs}") or {}
        if _ident(ours.get("parent", "")).replace(f"{NS}:block/{id}",
                                                  f"minecraft:block/{theirs}") \
                != _ident(other.get("parent", "")) \
                or set(ours.get("textures", {})) != set(other.get("textures", {})):
            not_the_games.append(f"{id} is not held the way the game holds its {theirs}")
if not_the_games:
    fail(f"{len(not_the_games)} shape(s) are not drawn the game's way: "
         + "; ".join(not_the_games[:5]))

# What they drop. Breaking either half of a door takes the other half with
# it, and that half's table runs too, so a door whose table drops from both
# halves is two doors for every one broken; a double slab is two slabs.
misdropped = []
for stem, shapes, _, _, _ in SHAPED_SETS:
    for shape in shapes:
        id = stem + shape
        table = json.dumps(dig_json("loot_table/blocks", f"{id}.json") or {})
        if shape == "_door" and '"properties": {"half": "lower"}' not in table:
            misdropped.append(f"{id} drops from both halves, so every door broken is "
                              f"two doors")
        if shape == "_slab" and ('"properties": {"type": "double"}' not in table
                                 or '"function": "minecraft:set_count"' not in table):
            misdropped.append(f"a double {id} drops one slab, so every one broken loses "
                              f"one")
if misdropped:
    fail("the sets' drops: " + "; ".join(misdropped))

# Which tool mines each block. The game's lists decide how fast a tool breaks
# a block and, for one that needs the right tool, whether it gives anything up:
# until 29 September 2026 no block here was on any list, so a fossil, a
# chiselled bone block or a mud brick set down and broken again was gone.
tooled = {}
for tool in ("pickaxe", "axe", "shovel", "hoe"):
    listed = read(RES, "data/minecraft/tags/block/mineable", f"{tool}.json")
    for value in (json.loads(listed).get("values", []) if listed else []):
        tooled.setdefault(value, []).append(tool)
untooled = [block for block in dug_blocks if not tooled.get(f"{NS}:{block}")]
if untooled:
    fail(f"{len(untooled)} block(s) the digging adds are on no tool's list, so nothing "
         f"mines them faster, and those that need the right tool give nothing to any: "
         + ", ".join(untooled[:8]))

print(f"the two sets       : {sum(len(s) for _, s, _, _, _ in SHAPED_SETS)} shape(s), each "
      f"its class's own states drawn "
      f"{'the way the game draws its own' if _game_jar else 'in full (no game jar to compare)'}"
      f", doors dropping once and double slabs twice, and every dig block on a tool's list")

print(f"in survival        : {len(site_rows)} site(s) laid by a feature in their "
      f"biomes, {2 * len(fossil_rows)} fossil halves, {len(totem_rows)} totems and 3 "
      f"other finds in the pools, every whole fossil and both sets made from what a "
      f"dig turns up, and the songless disc retired")

print("what it declares   : the game and the library, and not one thing beyond them")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all arsenal checks passed")
