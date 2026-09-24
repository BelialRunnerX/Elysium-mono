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
import json
import os
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
    """
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
        fail(f"rules/{name} imports {used}; the rules are the JDK alone")

if not os.path.isfile(HARNESS):
    fail("there is no harness, so whether a tool sits inside the band is an opinion")
else:
    with tempfile.TemporaryDirectory() as tmp:
        staged = os.path.join(tmp, "src")
        os.makedirs(staged)
        sources = [os.path.join(PURE, n) for n in os.listdir(PURE) if n.endswith(".java")]
        for source in sources + [HARNESS, os.path.join(WORK,
                                                       "tools/WeaponHarness.java")]:
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
                                 "the weapons        ")):
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
                    fail(f"{main} failed:\n" + ran.stdout.strip()[:1600])
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
models = os.path.join(RES, "assets", NS, "models/item")
flat = []
for name in sorted(os.listdir(models)) if os.path.isdir(models) else []:
    if not name.endswith(".json"):
        continue
    if '"parent": "item/handheld"' not in read(models, name):
        flat.append(name[:-5])
for name in flat[:8]:
    fail(f"{name} is not held like a tool, so it lies flat in a player's hand")
if len(flat) > 8:
    fail(f"...and {len(flat) - 8} more that lie flat")

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
                    elif open(made_path, "rb").read() != open(kept, "rb").read():
                        drifted.append(os.path.relpath(made_path, fresh))
            if drifted:
                fail(f"{len(drifted)} generated file(s) differ from what the generator "
                     f"writes: " + ", ".join(sorted(drifted)[:6]))

print("generated          : every sprite, model, recipe and name written by the "
      "generator")

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

print("what it declares   : the game and the library, and not one thing beyond them")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all arsenal checks passed")
