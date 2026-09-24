#!/usr/bin/env python3
"""
Validate Elysium Logistics against its sources.

Run from anywhere:   python3 elysium-logistics/validate.py

This module replaces seventeen jars, and a player will spend more hours in it
than in all four ages put together. Two things here would ruin a save and
neither would be visible until it had: items appearing or vanishing, and a
crafting plan that never comes back. Everything else is downstream of those.

  1. INDEPENDENCE. Vanilla, elysium-lib, elysium-kinetics, elysium-industry.
  2. PURITY, and the harness run rather than read.
  3. CONSERVATION, and a planner that terminates.
  4. THE LADDER, and the grid it hangs off.
  5. THE SEAM. What it declares.
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
SRC = os.path.join(WORK, "src/main/java/com/elysium/logistics")
PURE = os.path.join(SRC, "rules")
BELOW = [os.path.join(ROOT, "elysium-kinetics/src/main/java/com/elysium/kinetics/rules"),
         os.path.join(ROOT, "elysium-industry/src/main/java/com/elysium/industry/rules")]
HARNESS = os.path.join(WORK, "tools/LogisticHarness.java")
HARNESS_MAIN = "com.elysium.logistics.rules.LogisticHarness"

#: How long the arithmetic is allowed to take. The honest sweeps in
#: the harness run in a few seconds; anything past this is a loop.
HARNESS_SECONDS = 60
NS = "elysiumlogistics"
IND_NS = "elysiumindustry"

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
    """Every run of whitespace as one space.

    So that a rule can name a whole statement — "drawers.add(...); continue;" —
    without also having to guess how it was wrapped. Naming the whole statement
    is the point: this project has now been bitten ten times by a rule that
    looked for a token which survives the very edit it was meant to catch,
    because the token appears somewhere else in the same method.
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
    print("This is not the elysium-logistics tree.")
    sys.exit(1)

print("=" * 70)
print("elysium-logistics — where everything goes, and the proof that none of it "
      "goes missing")
print("=" * 70)

# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------

FOREIGN = re.compile(
    r"(?:appeng\.|refinedstorage|com\.jaquadro|org\.cyclops|sophisticatedcore"
    r"|com\.buuz135\.functionalstorage)"
    r"|\b(?:ae2|appliedenergistics2|refinedstorage|storagedrawers|functionalstorage"
    r"|sophisticatedstorage|sophisticatedbackpacks|integrateddynamics|dankstorage)\s*:"
    r"|\"(?:appliedenergistics2|refinedstorage|storagedrawers|functionalstorage"
    r"|sophisticatedstorage|integrateddynamics)\"", re.I)
UPWARD = re.compile(r"com\.elysium\.(foundry|transit|agronomy)")

java_files = []
for dirpath, _, filenames in os.walk(SRC):
    for name in filenames:
        if name.endswith(".java"):
            java_files.append(os.path.join(dirpath, name))

for path in java_files:
    src = strip_comments(read(path))
    where = os.path.relpath(path, SRC)
    for hit in FOREIGN.findall(src):
        fail(f"{where} names {hit}; this module is a reimplementation of seventeen jars "
             f"and must not reach for any of them")
    if UPWARD.search(src):
        fail(f"{where} reaches up into a later module; the dependencies run one way")

toml = read(RES, "META-INF/neoforge.mods.toml")
declared = set(re.findall(r'modId="([^"]+)"', toml))
allowed = {"${mod_id}", "neoforge", "minecraft", "elysiumlib", "elysiumkinetics",
           "elysiumindustry"}
for mod in declared - allowed:
    fail(f"the module declares a dependency on {mod}; it needs the game, the library, "
         f"and the grid it hangs off")
for needed in ("neoforge", "minecraft", "elysiumlib", "elysiumindustry"):
    if needed not in declared:
        fail(f"the module does not declare {needed}")

print(f"independence       : {len(java_files)} source file(s), none of the seventeen "
      f"named, and the grid it hangs off declared")

# ---------------------------------------------------------------------------
# 2. Purity, and the harness executed
# ---------------------------------------------------------------------------

for name in sorted(os.listdir(PURE)) if os.path.isdir(PURE) else []:
    if not name.endswith(".java"):
        continue
    src = read(PURE, name)
    without_package = re.sub(r"^\s*package [^;]+;", "", src, count=1, flags=re.M)
    for forbidden in ("net.minecraft", "net.neoforged", "com.mojang"):
        if forbidden in without_package:
            fail(f"rules/{name} reaches for {forbidden}; the rules are the JDK and the "
                 f"modules below, so they can be executed by a harness")
    for used in re.findall(r"import\s+(com\.elysium\.[\w.]+);", without_package):
        if not (used.startswith("com.elysium.kinetics.rules.")
                or used.startswith("com.elysium.industry.rules.")):
            fail(f"rules/{name} imports {used}; a rules class may reach only into other "
                 f"rules, which are pure like itself")

if not os.path.isfile(HARNESS):
    fail("there is no harness, so every number below is checked by reading")
else:
    with tempfile.TemporaryDirectory() as tmp:
        staged = os.path.join(tmp, "src")
        os.makedirs(staged)
        sources = [os.path.join(PURE, n) for n in os.listdir(PURE) if n.endswith(".java")]
        for folder in BELOW:
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
            fail("the rules do not compile against the JDK and the modules below alone:\n"
                 + compiled.stderr.strip()[:1200])
        else:
            # With a clock on it. A rules file is meant to be arithmetic, and
            # arithmetic finishes; a harness that does not come back means some
            # rule in there can loop forever on input a player could produce,
            # which is a server that stops responding rather than a wrong
            # number. Waiting for it is not a way of finding that out.
            try:
                ran = subprocess.run(
                    ["java", "-cp", os.path.join(tmp, "out"), HARNESS_MAIN],
                    capture_output=True, text=True, timeout=HARNESS_SECONDS)
            except subprocess.TimeoutExpired:
                ran = None
                fail(f"the harness did not finish within {HARNESS_SECONDS} seconds, so "
                     f"something in the rules can loop forever — which is a server that "
                     f"stops responding, not a wrong answer")
            if ran is None:
                pass
            elif ran.returncode != 0:
                fail("the logistics harness failed:\n" + ran.stdout.strip()[:1600])
            else:
                print("the arithmetic     : " + ran.stdout.strip().splitlines()[-1])

rules = read(PURE, "LogisticRules.java")
plain = strip_comments(rules)


def constant(name, kind=r"(?:float|int)"):
    m = re.search(r"public static final " + kind + r"\s+" + name + r"\s*=\s*([^;]+);",
                  plain, re.S)
    return " ".join(m.group(1).split()) if m else None


# ---------------------------------------------------------------------------
# 3. Conservation, and a planner that terminates
# ---------------------------------------------------------------------------

accepted = body_of(plain, "public static int accepted(")
if "Math.min(" not in accepted:
    fail("a store takes what it is offered without asking whether it has room, so the "
         "remainder is destroyed")
leftover = body_of(plain, "public static int leftOver(")
if "accepted(offered, room)" not in leftover:
    fail("what is left in a player's hand is worked out separately from what the store "
         "took, so the two can disagree and the difference is items appearing or "
         "vanishing")

if "public static boolean voids(" not in plain:
    fail("there is no single named place where destroying items is allowed, so every "
         "path that does it is its own decision and none of them can be checked")

walk = body_of(plain, "private static String walk(")
if "path.contains(want)" not in walk:
    fail("the planner does not check whether it is already inside what it is planning, "
         "so a circular recipe set hangs the server")
if "depth > MOST_DEPTH" not in walk:
    fail("the planner has no depth limit, so a shape nobody thought of hangs the server "
         "even though cycles are caught")
if "(count + step.makes() - 1) / step.makes()" not in walk:
    fail("the planner does not round runs up, so a plan that needs three of something "
         "made four at a time comes up short — the bug that makes an autocrafter look "
         "haunted")
if "runs.size() >= MOST_STEPS" not in walk:
    fail("a plan may contain any number of steps, so one deep enough exhausts memory "
         "rather than being refused")

if "public static boolean reaches(" not in plain:
    fail("there is no way to ask whether one recipe needs another, so the cycle check "
         "cannot tell a broken recipe from one merely downstream of a broken one")
reaches = body_of(plain, "public static boolean reaches(")
if "seen.add(here)" not in reaches:
    fail("the reachability walk has no memory, so asking whether a thing needs itself "
         "on a recipe set that contains a cycle is itself an endless loop")

cycles = body_of(plain, "public static Set<String> cyclesIn(")
if "reaches(steps, name, name)" not in cycles:
    fail("the cycle check reports everything a cycle blocks rather than the cycle, so a "
         "player is told to fix recipes that are fine")

# ---------------------------------------------------------------------------
# Autocrafting: the order, the budget, and the hands
# ---------------------------------------------------------------------------
#
# The planner says what has to be made and says nothing at all about when. The
# difference between those two is the whole of this section: an autocrafter
# that assembles the gear before the plates exist does not crash, it stalls
# holding somebody's iron, and that is the single most complained-about
# behaviour in every storage mod this module replaces.

if "public static List<Job> order(" not in plain:
    fail("there is no ordering, so a plan is a map with no order in it and a factory "
         "reads it in whatever order a hash table produced")

ordering = squashed(body_of(plain, "public static List<Job> order("))
if "for (Map.Entry<String, Long> entry : new TreeMap<>(runs).entrySet()) {" not in ordering:
    fail("the order is settled over a hash map, so the same request comes out in a "
         "different order on a different day and a factory never looks the same twice")
if "return placed.size() == runs.size() ? List.copyOf(out) : List.of();" not in ordering:
    fail("the ordering returns a partial order rather than nothing when it cannot "
         "settle, so a cycle comes back as a plan that silently leaves steps out")
if "if (runs.containsKey(need) && !placed.contains(need)) {" not in ordering:
    fail("a job is not held behind the jobs that make its ingredients, so the order is "
         "not an order")

planning = squashed(body_of(plain, "public static Plan plan("))
if "List<Job> jobs = refused == null ? order(steps, runs) : List.of();" not in planning:
    fail("planning does not order what it planned, so every caller has to do it and "
         "every caller can do it differently")
if "if (refused == null && jobs.isEmpty() && !runs.isEmpty()) {" not in planning:
    fail("a plan the walk accepted and the ordering could not settle is returned as a "
         "good plan; the two mechanisms are independent on purpose, and when they "
         "disagree the answer is to refuse rather than to pick a winner")

if "if (--budget[0] < 0) {" not in squashed(walk):
    fail("the planner has no budget, so a recipe set with no cycle, no great depth and "
         "a great many ways through it — which is the shape of every real Minecraft "
         "recipe tree — is a server that stops answering rather than a refusal")

batch = squashed(body_of(plain, "public static int batchSize("))
if "int fits = Math.max(1, STACK / perRun);" not in batch:
    fail("a batch is not bounded by what fits in a slot, so one dispatch empties a "
         "warehouse into a machine")
if "return (int) Math.min(remaining, fits);" not in batch:
    fail("a batch may exceed what is left to do, so the last dispatch of a job makes "
         "more than was asked for")

if constant("ASSEMBLE_TICKS", "int") != "40":
    fail("an assembler is not slower than a player at a bench, so the bench is "
         "pointless and the thing being bought is no longer not having to be there")

core_draw = re.search(r"ASSEMBLY_CORE\s*=\s*new IndustryRules\.Machine\([^;]*;", plain)
if not core_draw or "Voltage.MEDIUM" not in core_draw.group(0):
    fail("the core is not a medium-voltage machine, so autocrafting is available the "
         "moment a player has any grid at all and the age it belongs to is decided by "
         "nothing")
assembler_draw = re.search(r"ASSEMBLER\s*=\s*new IndustryRules\.Machine\([^;]*;", plain)
if not assembler_draw or "Voltage.LOW" not in assembler_draw.group(0):
    fail("an assembler is not a low-voltage machine, so a player cannot add hands "
         "without rebuilding the wire that feeds them")

# The blocks that do it.
assembler = strip_comments(read(SRC, "block/AssemblerBlock.java"))
assembler_squashed = squashed(assembler)
core = strip_comments(read(SRC, "block/AssemblyCoreBlock.java"))
core_squashed = squashed(core)
terminal = strip_comments(read(SRC, "block/TerminalBlock.java"))
catalogue = strip_comments(read(SRC, "block/CraftingCatalogue.java"))

for where, name, body in (("an assembler", "AssemblerBlock", assembler),
                          ("the core", "AssemblyCoreBlock", core)):
    drawn = squashed(body_of(body, "public IndustryRules.Machine machine()"))
    if "share()" in drawn:
        fail(f"what {where} draws is a function of what the grid can give it, so the "
             f"grid's answer depends on the machine's answer and the machine's answer "
             f"depends on the grid's; that never settles, and this pack has made the "
             f"mistake impossible in five modules")

if "return working() ? LogisticRules.ASSEMBLER : null;" not in assembler_squashed:
    fail("an assembler with nothing to make still bills the grid, so a hall of idle "
         "machines browns out the factory that was going to use them")

if "Containers.dropContents(level, pos, assembler.spill());" not in assembler_squashed:
    fail("an assembler that is mined destroys the batch of ingredients the core took "
         "out of the warehouse for it, which is the one place in this module where "
         "items would vanish without anybody asking")

ticking = squashed(body_of(assembler, "protected void serverTick("))
if "if (!consume()) {" not in ticking:
    fail("an assembler produces without checking that it had the ingredients, so "
         "anything that reached into it mid-job is a duplication bug")
if "done += IndustryRules.workPerTick(share());" not in ticking:
    fail("an assembler does not run on Age II's work model, so a browned-out assembler "
         "behaves differently from a browned-out crusher and a player has to learn the "
         "same lesson twice")

handing = squashed(body_of(core, "private boolean hand("))
if "if (available(warehouse, need.getKey()) < need.getValue()) { return false; }" \
        not in handing:
    fail("the core takes ingredients out of the warehouse without first checking that "
         "all of them are there, so a job that is one iron short strands everything "
         "else it already took inside a machine that cannot use it")
if handing.count("give(warehouse, taken);") < 2:
    fail("the core does not put back what it took when a batch could not be handed "
         "over, so a failed dispatch is a warehouse that is quietly emptier")

dispatching = squashed(body_of(core, "private void dispatch("))
if "if (!hand(level, warehouse, assembler, step, batch)) { return; }" not in dispatching:
    fail("the core moves past a job whose ingredients are not ready yet, which defeats "
         "the whole point of the order: what this job needs is being made by an "
         "earlier one, and skipping it means it never is")

requesting = squashed(body_of(core, "public Component request("))
if "if (!LogisticRules.worthStarting(worked, assemblers.size())) {" not in requesting:
    fail("the core starts any plan however large, so a request nobody could finish "
         "becomes a warehouse full of intermediate parts rather than a sentence")
# Named as the whole statement and not as the token: a rule that looked for
# "worthStarting" would go on passing the day somebody left the guard where it
# was and added a second, redundant one after the clear — which is not the bug.
# The bug is the guard being *only* after it.
GUARD = "if (!LogisticRules.worthStarting(worked, assemblers.size())) {"
if GUARD in requesting and requesting.find(GUARD) > requesting.find("queue.clear();"):
    fail("the core empties its queue before deciding whether the new plan is worth "
         "starting, so a refused request also cancels the one that was running")

if "private static String representative(Ingredient ingredient)" not in catalogue:
    fail("there is no single place where an ingredient that accepts many items is "
         "reduced to one, so the planner's vocabulary depends on the call site")
picking = squashed(body_of(catalogue, "private static String representative("))
if "TreeMap<String, String> sorted = new TreeMap<>();" not in picking:
    fail("which item an ingredient stands for is whichever the game listed first, so a "
         "factory makes a torch out of coal one day and charcoal the next with nothing "
         "changed")
choosing = squashed(body_of(catalogue, "private static boolean better("))
if "return againstName == null || name.compareTo(againstName) < 0;" not in choosing:
    fail("two recipes for the same thing are not chosen between by name, so which one "
         "a factory uses is whatever order the recipe manager happened to produce")
if "needs.containsKey(output)" not in squashed(catalogue):
    fail("a recipe that consumes what it makes is offered to the planner, which would "
         "refuse the whole plan for a cycle when there was another recipe to use")

if "LogisticNetwork.along(level, pos," not in squashed(terminal):
    fail("the terminal does not find its core along the bus, so it has to be next to "
         "it and there is no reason for it to be a separate block")

capabilities = squashed(strip_comments(read(SRC, "registry/LogisticBlockEntities.java")))
if "ASSEMBLER.get()" in capabilities and "registerBlockEntity" in capabilities:
    hands = capabilities[capabilities.find("registerBlockEntity"):]
    if "ASSEMBLER.get()" in hands:
        fail("an assembler is exposed as an inventory, so a hopper can take half a "
             "recipe out of a machine that is halfway through it")

print(f"autocrafting       : a plan that is ordered as well as costed, a walk with a "
      f"budget, batches that fit in a slot, and "
      f"{constant('MOST_JOBS', 'int')} jobs at a time across hands that are machines "
      f"on the grid")

print("conservation       : one place where a transfer is decided, one place where "
      "destroying is allowed, and a planner that refuses cycles, depth and size")

# ---------------------------------------------------------------------------
# 4. The ladder, and the grid it hangs off
# ---------------------------------------------------------------------------

if "IndustryRules.Machine" not in plain:
    fail("a network is not a machine on Age II's grid, so this module has invented a "
         "second idea of what power is")
machine = body_of(plain, "public IndustryRules.Machine machine()")
if "devices * DEVICE_DRAW" not in machine:
    fail("a network's draw is not its devices times what one costs, so the number a "
         "player is shown is not the number the grid uses")
for reading in ("share", "supply", "demand"):
    if reading in machine:
        fail(f"a network's draw reads {reading}, so what the grid can afford depends on "
             f"what the network asks for and the other way round — that oscillates and "
             f"never settles, and this pack has made the mistake impossible three times")

moves = body_of(plain, "public float movesAt(")
if "Math.min(1.0F, share)" not in moves:
    fail("a network on a grid with more than it needs moves items faster than full, so "
         "over-building is a speed upgrade nobody documented")
if "overloaded()" not in moves:
    fail("an overloaded controller still works, so the device limit is not one")

if constant("COMPRESSION", kind="int") != "9":
    fail("compression is not nine, which is what a crafting grid does and therefore the "
         "only number a player will expect")

if "DRAWER_STEP" not in plain:
    fail("the drawer ladder is not written as a step, so each tier is a number of its "
         "own and the rungs can stop being rungs")

print("the ladder         : drawers that step by " + str(constant("DRAWER_STEP", "int"))
      + ", a network of at most " + str(constant("MOST_DEVICES", "int"))
      + " devices at " + str(constant("DEVICE_DRAW")) + " each, on Age II's grid")

# ---------------------------------------------------------------------------
# 5. Storage in the world
# ---------------------------------------------------------------------------

GENERATOR = os.path.join(WORK, "tools/gen_logistics.py")
if not os.path.isfile(GENERATOR):
    fail("there is no generator, so every asset in the module is kept by hand")
else:
    with tempfile.TemporaryDirectory() as tmp:
        environment = dict(os.environ, ELYSIUM_LOGISTICS_OUT=tmp)
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
                     f"writes, so they were edited by hand and the next run will undo "
                     f"them: " + ", ".join(sorted(drifted)[:6])
                     + (" ..." if len(drifted) > 6 else ""))

blocks_src = read(SRC, "registry/LogisticBlocks.java")
items_src = read(SRC, "registry/LogisticItems.java")
registered = re.findall(r'BLOCKS\.register\("([a-z_]+)"', strip_comments(blocks_src, True))
block_items = set(re.findall(r'registerSimpleBlockItem\(LogisticBlocks\.([A-Z_]+)\)',
                             strip_comments(items_src, True)))
order = re.search(r"ORDER = List\.of\(([^;]+)\);", strip_comments(items_src, True))
in_tab = set(re.findall(r"[A-Z_]{3,}", order.group(1))) if order else set()

lang = {}
lang_path = os.path.join(RES, "assets", NS, "lang/en_us.json")
if os.path.isfile(lang_path):
    lang = json.load(open(lang_path, encoding="utf-8"))
else:
    fail("there is no language file, so every block is called by its id")

mineable = []
for tool in ("axe", "pickaxe"):
    where = os.path.join(RES, f"data/minecraft/tags/block/mineable/{tool}.json")
    if os.path.isfile(where):
        mineable.extend(json.load(open(where, encoding="utf-8")).get("values", []))

for name in registered:
    if name.upper() not in block_items:
        fail(f"{name} has no item, so it exists only to a command")
    if name.upper() not in in_tab:
        fail(f"{name} is in no creative tab, so nobody in a creative world can find it")
    for kind, where in (("blockstates", f"blockstates/{name}.json"),
                        ("an item model", f"models/item/{name}.json")):
        if not os.path.isfile(os.path.join(RES, "assets", NS, where)):
            fail(f"{name} has no {kind}, so it is a purple cube")
    if not os.path.isfile(os.path.join(RES, "data", NS, f"loot_table/blocks/{name}.json")):
        fail(f"{name} has no drop, so mining one destroys everything in it")
    if f"block.{NS}.{name}" not in lang:
        fail(f"{name} has no name in the language file")
    if f"{NS}:{name}" not in mineable:
        fail(f"{name} is in no mineable tag, so it takes a fist and drops nothing quickly")

# A drawer's blockstate is four ways round times five steps of fullness, and
# every one of them has to exist or the game refuses the model at load.
for tier in ("timber_drawer", "brass_drawer", "steel_drawer"):
    where = os.path.join(RES, "assets", NS, f"blockstates/{tier}.json")
    if not os.path.isfile(where):
        continue
    variants = json.load(open(where, encoding="utf-8")).get("variants", {})
    wanted = {f"facing={facing},full={step}"
              for facing in ("north", "east", "south", "west") for step in range(5)}
    if set(variants) != wanted:
        fail(f"{tier}'s blockstate does not name every way round times every step of "
             f"fullness, so some combination a player can reach has no model")
    if len({entry.get("model") for entry in variants.values()}) < 5:
        fail(f"{tier} looks the same however full it is, so a wall of them cannot be "
             f"read at a glance — which is the one thing a drawer is better at than a "
             f"chest")

bus_box = re.search(r"SHAPE = box\((\d+), \d+, \d+, (\d+), \d+, \d+\)",
                    strip_comments(read(SRC, "block/BusBlock.java")))
bus_model = os.path.join(RES, "assets", NS, "models/block/bus.json")
if bus_box and os.path.isfile(bus_model):
    element = json.load(open(bus_model, encoding="utf-8"))["elements"][0]
    if float(element["from"][0]) != float(bus_box.group(1)) \
            or float(element["to"][0]) != float(bus_box.group(2)):
        fail("the bus's model and its collision box are different sizes, so it can be "
             "hit where it cannot be seen or the other way round")

# --- the blocks, and the ways they can be made to lose things -------------

store = strip_comments(read(SRC, "block/DrawerStore.java"))
inserting = body_of(store, "public ItemStack insertItem(")
if "LogisticRules.accepted(" not in inserting or "LogisticRules.leftOver(" not in inserting:
    fail("a drawer works out for itself how much of a stack it took, so the one piece of "
         "arithmetic the harness proves conserves items is not the one the game runs")
if "LogisticRules.voids(voiding)" not in inserting:
    fail("a drawer decides on its own whether to destroy what will not fit, so the "
         "single named place where that is allowed is not the place it happens")
if "isSameItemSameComponents" not in inserting:
    fail("a drawer will take a different item into a slot that already holds something, "
         "so two kinds of thing end up counted as one and the difference is destroyed")

extracting = body_of(store, "public ItemStack extractItem(")
if "Math.min(Math.min(wanted, counts[slot])" not in extracting:
    fail("a drawer hands out more than it holds, or more than a stack, either of which "
         "is items appearing out of nothing")

picking = body_of(store, "public int slotFor(")
if "isSameItemSameComponents" not in picking:
    fail("a drawer starts a second pile of something it already holds, so its other "
         "slots fill with duplicates and its second kind can never be stored")

network = strip_comments(read(SRC, "block/LogisticNetwork.java"))
walking = squashed(body_of(network, "public static Found walk("))
# The whole statement, not "continue" on its own: there is a second continue
# four lines further down, so looking for the word alone passes on a walk that
# chains straight through the drawers it finds.
if "drawers.add(next.immutable()); continue;" not in walking:
    fail("the network walks through drawers as well as bus, so a base with drawers "
         "packed together turns out to be one enormous accidental network")
if "MOST_STEPS" not in walking:
    fail("a network walk has no limit, so a long enough run of bus hangs the server")
# Likewise both ways out of the walk have to be sorted, and the overloaded one
# already is — so "sorted(" appearing somewhere proves nothing about the other.
if "List<BlockPos> found = sorted(drawers);" not in walking:
    fail("a network hands back its drawers in whatever order a hash table produced, so "
         "the same stack lands in a different drawer on every world load")

controller = strip_comments(read(SRC, "block/ControllerBlock.java"))
drawing = body_of(controller, "public IndustryRules.Machine machine()")
for reading in ("share()", "supply()", "demand()"):
    if reading in drawing:
        fail(f"a controller's draw on the grid reads {reading}, so what the grid can "
             f"afford depends on what the controller asks for and the other way round")
if "network.machine()" not in drawing:
    fail("a controller's draw is not the one the rules state, so the number a player is "
         "shown is not the number the grid uses")
budget = body_of(controller, "public float budget()")
if "network().movesAt(share())" not in budget:
    fail("a controller moves items at a rate that ignores the grid, so a network on a "
         "dead grid works as well as one on a full one")
# Named in full, because "controller.spend(" survives being passed a zero.
moving = squashed(controller)
for charged in ("controller.spend(moved);", "controller.spend(out.getCount());"):
    if charged not in moving:
        fail(f"{charged} is not what the controller charges against its budget, so the "
             f"rate limit is arithmetic nobody applies and a starved network moves "
             f"items as fast as a fed one")

# --- the recipes ----------------------------------------------------------

OURS = {f"{NS}:{name}" for name in registered}
BELOW_TAGS = {"c:ingots/steel"} | {f"c:plates/{m}" for m in
                                   ("iron", "copper", "gold", "steel")}
below = set()
for module, namespace in (("elysium-industry", "elysiumindustry"),
                          ("elysium-kinetics", "elysiumkinetics"),
                          ("elysium-atomics", "elysiumatomics")):
    folder = os.path.join(ROOT, module, "src/main/resources/data", namespace, "recipe")
    if os.path.isdir(folder):
        for name in os.listdir(folder):
            if name.endswith(".json"):
                below.add(f"{namespace}:{name[:-5]}")

recipe_root = os.path.join(RES, "data", NS, "recipe")
crafted = {}
if os.path.isdir(recipe_root):
    for name in sorted(os.listdir(recipe_root)):
        if name.endswith(".json"):
            crafted[name[:-5]] = json.load(
                open(os.path.join(recipe_root, name), encoding="utf-8"))


def ingredients(recipe):
    named = list(recipe.get("key", {}).values())
    named.extend(recipe.get("ingredients", []))
    out = []
    for entry in named:
        for one in (entry if isinstance(entry, list) else [entry]):
            if "item" in one:
                out.append(one["item"])
            elif "tag" in one:
                out.append("#" + one["tag"])
    return out


for name in registered:
    if name not in crafted:
        fail(f"{name} has no crafting recipe, so it can only be given")

for name, recipe in crafted.items():
    for asked in ingredients(recipe):
        if asked.startswith("#"):
            if asked[1:] not in BELOW_TAGS and not asked.startswith("#minecraft:"):
                fail(f"recipe/{name} asks for the tag {asked}, which nothing in this "
                     f"install is guaranteed to fill")
        elif not (asked.startswith("minecraft:") or asked in OURS or asked in below):
            fail(f"recipe/{name} asks for {asked}; storage is buildable out of the base "
                 f"game and the modules below it, and nothing else")

# The first rung has to be reachable before there is a factory, or there is no
# first rung and the whole module starts at the second age.
first = ingredients(crafted.get("timber_drawer", {}))
if any(a.startswith("#c:plates/") or a.startswith("#c:ingots/steel")
       or a.startswith(f"{IND_NS}:") for a in first):
    fail("the first drawer needs something only a factory makes, so a player meets "
         "storage after electricity rather than long before it")

print(f"storage in the world: {len(registered)} block(s), each modelled through every "
      f"step of fullness, dropped, named and craftable; the first of them out of planks")

# ---------------------------------------------------------------------------
# 6. The seam
# ---------------------------------------------------------------------------

main_class = read(SRC, "ElysiumLogistics.java")
if f'MODID = "{NS}"' not in main_class:
    fail("the module id is not what everything else in the tree spells it")

print("what it declares   : the game, the library, and the grid a network hangs off")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all logistics checks passed")
