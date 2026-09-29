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
SRC = os.path.join(WORK, "src/main/java/com/elysium/logistics")
PURE = os.path.join(SRC, "rules")
BELOW = [os.path.join(ROOT, "elysium-kinetics/src/main/java/com/elysium/kinetics/rules"),
         os.path.join(ROOT, "elysium-industry/src/main/java/com/elysium/industry/rules")]
HARNESS = os.path.join(WORK, "tools/LogisticHarness.java")
HARNESS_MAIN = "com.elysium.logistics.rules.LogisticHarness"
COMPRESS_HARNESS = os.path.join(WORK, "tools/CompressHarness.java")
COMPRESS_MAIN = "com.elysium.logistics.rules.CompressHarness"
PACK_HARNESS = os.path.join(WORK, "tools/PackHarness.java")
PACK_MAIN = "com.elysium.logistics.rules.PackHarness"
NET_HARNESS = os.path.join(WORK, "tools/NetHarness.java")
NET_MAIN = "com.elysium.logistics.rules.NetHarness"

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

if not os.path.isfile(NET_HARNESS):
    fail("there is no network harness, so a logic layer that can loop or add a "
         "number to a chest is checked by reading")
if not os.path.isfile(PACK_HARNESS):
    fail("there is no pack harness, so the one thing every mod of this kind gets "
         "wrong — taking a space upgrade out of a full bag — is checked by reading")
if not os.path.isfile(COMPRESS_HARNESS):
    fail("there is no compression harness, so the round trip — the one property a "
         "save depends on — is checked by reading, which nobody has ever done")
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
        for source in sources + [HARNESS, COMPRESS_HARNESS, PACK_HARNESS,
                                 NET_HARNESS]:
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
                fail("the logistics harness failed:\n" + harness_trouble(ran.stdout))
            else:
                print("the arithmetic     : " + ran.stdout.strip().splitlines()[-1])

            try:
                ran_compress = subprocess.run(
                    ["java", "-cp", os.path.join(tmp, "out"), COMPRESS_MAIN],
                    capture_output=True, text=True, timeout=HARNESS_SECONDS)
            except subprocess.TimeoutExpired:
                ran_compress = None
                fail(f"the compression harness did not finish within "
                     f"{HARNESS_SECONDS} seconds")
            if ran_compress is None:
                pass
            elif ran_compress.returncode != 0:
                fail("the compression harness failed:\n"
                     + harness_trouble(ran_compress.stdout))
            else:
                print("the round trip     : "
                      + ran_compress.stdout.strip().splitlines()[-1])

            try:
                ran_pack = subprocess.run(
                    ["java", "-cp", os.path.join(tmp, "out"), PACK_MAIN],
                    capture_output=True, text=True, timeout=HARNESS_SECONDS)
            except subprocess.TimeoutExpired:
                ran_pack = None
                fail(f"the pack harness did not finish within {HARNESS_SECONDS} "
                     f"seconds")
            if ran_pack is None:
                pass
            elif ran_pack.returncode != 0:
                fail("the pack harness failed:\n" + harness_trouble(ran_pack.stdout))
            else:
                print("what a pack holds : "
                      + ran_pack.stdout.strip().splitlines()[-1])

            try:
                ran_net = subprocess.run(
                    ["java", "-cp", os.path.join(tmp, "out"), NET_MAIN],
                    capture_output=True, text=True, timeout=HARNESS_SECONDS)
            except subprocess.TimeoutExpired:
                ran_net = None
                fail(f"the network harness did not finish within "
                     f"{HARNESS_SECONDS} seconds, which for a file whose whole "
                     f"claim is that expressions terminate is the answer")
            if ran_net is None:
                pass
            elif ran_net.returncode != 0:
                fail("the network harness failed:\n"
                     + harness_trouble(ran_net.stdout))
            else:
                print("the logic layer    : "
                      + ran_net.stdout.strip().splitlines()[-1])

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
                    elif not samefile.same(made_path, kept):
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

# the bus run(s): arms, plates, dye and pick as every run in the pack, checked by
# the shared art/runs.py so six modules agree.
import importlib.util as _ilu
_runs_spec = _ilu.spec_from_file_location("art_runs", os.path.join(os.path.dirname(str(WORK)), "art", "runs.py"))
_art_runs = _ilu.module_from_spec(_runs_spec)
_runs_spec.loader.exec_module(_art_runs)
_run_java = open(os.path.join(str(SRC), "block/BusBlock.java"), encoding="utf-8").read()
for _run in ("bus",):
    for _problem in _art_runs.check("elysiumlogistics", _run, str(RES), _run_java):
        fail(_problem)
_mod_java = open(os.path.join(str(SRC), "ElysiumLogistics.java"), encoding="utf-8").read()
for _run in ("bus",):
    if "com.elysium.lib.run.Runs.declare(" not in _mod_java:
        fail(f"{_run} is not declared a run, so its plates have no colour and no tint handler")
        break
print("runs               : the bus draw arms and plates, take a dye, and keep it")

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

# ---------------------------------------------------------------------------
# The compression catalogue, read once and used twice
# ---------------------------------------------------------------------------
#
# Five hundred and thirty-one blocks registered from a loop rather than by
# name, so nothing that reads `BLOCKS.register("...")` above can see them.
# They are ours all the same: a recipe that asks for the tier below itself is
# asking for one of ours, and a check that does not know that reports five
# hundred problems that are not problems.

compress = strip_comments(read(PURE, "CompressRules.java"), True)


def compress_list(name):
    found = re.search(name + r" = List\.of\((.*?)\);", compress, re.S)
    return re.findall(r'"([a-z_:]+)"', found.group(1)) if found else []


compress_materials = compress_list("MATERIALS")
compress_depths = compress_list("DEPTHS")
compress_markers = compress_list("MARKERS")
_tiers = re.search(r"TIERS = (\d+);", compress)
compress_tiers = int(_tiers.group(1)) if _tiers else 0

compress_ids = []
for _material in compress_materials:
    for _tier in range(1, compress_tiers + 1):
        compress_ids.append((_material, _tier,
                             compress_depths[_tier - 1] + "_" + _material))

OURS = {f"{NS}:{name}" for name in registered}
OURS |= {f"{NS}:{id}" for _m, _t, id in compress_ids}

pack = strip_comments(read(PURE, "PackRules.java"), True)
pack_bodies = [(name, int(slots), int(fittings), carried == "true")
               for name, slots, fittings, carried in re.findall(
                   r'new Body\("([a-z_]+)",\s*(\d+),\s*(\d+),\s*(true|false)\)',
                   pack)]
pack_kinds = dict(re.findall(
    r'public static final String (\w+)\s*=\s*"([a-z_]+)";', pack))
pack_parts = [(name, pack_kinds.get(kind, kind), int(slots))
              for name, kind, slots in re.findall(
                  r'new Part\("([a-z_]+)",\s*(\w+),\s*(\d+)\)', pack)]
pack_ids = [one[0] for one in pack_bodies] + [one[0] for one in pack_parts]
OURS |= {f"{NS}:{name}" for name in pack_ids}

net = strip_comments(read(PURE, "NetRules.java"), True)
net_roles = dict(re.findall(
    r'public static final String (\w+)\s*=\s*"([a-z_]+)";', net))
net_devices = [(name, net_roles.get(role, role), int(draws))
               for name, role, draws in re.findall(
                   r'new Device\("([a-z_]+)",\s*(\w+),\s*(\d+)\)', net)]
net_ops = [(name, gives) for name, gives in re.findall(
    r'new Op\("([a-z_]+)",\s*List\.of\([^)]*\),\s*(\w+)\)', net)]
OURS |= {f"{NS}:{name}" for name, _r, _d in net_devices}
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

# ---------------------------------------------------------------------------
# Compression
# ---------------------------------------------------------------------------
#
# Five hundred and thirty-one blocks from a loop over two lists. The harness
# proves the arithmetic; what is left for here is that the data the game will
# actually run says the same thing — because the property that matters is a
# pair of recipes, and a pair of recipes that disagree with the arithmetic by
# one is a machine for making matter and nothing in this pack survives it.

if compress_tiers < 3 or len(compress_materials) < 20:
    fail(f"this validator read {len(compress_materials)} material(s) and "
         f"{compress_tiers} tier(s) out of the compression rules, and a check "
         f"against a short list passes whatever is wrong with the rest")
if len(compress_depths) != compress_tiers or len(compress_markers) != compress_tiers:
    fail(f"the rules go {compress_tiers} tiers deep with {len(compress_depths)} "
         f"name(s) and {len(compress_markers)} frame(s); a tier would be unnamed "
         f"or two tiers would look the same across a room")

missing = []
for material, tier, id in compress_ids:
    for where in (f"assets/{NS}/blockstates/{id}.json",
                  f"assets/{NS}/models/block/{id}.json",
                  f"assets/{NS}/models/item/{id}.json",
                  f"data/{NS}/loot_table/blocks/{id}.json",
                  f"data/{NS}/recipe/{id}.json",
                  f"data/{NS}/recipe/{id}_undone.json"):
        if not os.path.isfile(os.path.join(RES, where)):
            missing.append(f"{id} has no {where.rsplit('/', 2)[-2]}")
if missing:
    fail(f"{len(missing)} of {len(compress_ids) * 6} things a compression needs "
         f"are missing: " + ", ".join(missing[:6]))

# The pair of recipes, which is the round trip written as data. Nine in, one
# out, one in, nine out, and the thing it is made of is the tier below it.
wrong = []
for material, tier, id in compress_ids:
    under = ("minecraft:" + material if tier == 1
             else f"{NS}:" + compress_depths[tier - 2] + "_" + material)
    body = read(RES, "data", NS, f"recipe/{id}.json")
    if body:
        made = json.loads(body)
        grid = made.get("pattern", [])
        cells = sum(row.count("M") for row in grid)
        if cells != 9:
            wrong.append(f"{id} is compressed out of {cells} rather than nine")
        if made.get("key", {}).get("M", {}).get("item") != under:
            wrong.append(f"{id} is compressed out of "
                         f"{made.get('key', {}).get('M')} and not {under}")
        if made.get("result", {}).get("count", 0) != 1:
            wrong.append(f"{id} makes {made.get('result', {}).get('count')} at a "
                         f"time, and nine into more than one is a machine for "
                         f"making matter")
    back = read(RES, "data", NS, f"recipe/{id}_undone.json")
    if back:
        undone = json.loads(back)
        if undone.get("type") != "minecraft:crafting_shapeless":
            wrong.append(f"{id} comes apart on a grid rather than in the hand")
        items = [one.get("item") for one in undone.get("ingredients", [])]
        if items != [f"{NS}:{id}"]:
            wrong.append(f"{id} comes apart out of {items}")
        if undone.get("result", {}).get("id") != under:
            wrong.append(f"{id} comes apart into "
                         f"{undone.get('result', {}).get('id')} and not {under}")
        if undone.get("result", {}).get("count", 0) != 9:
            wrong.append(f"{id} comes apart into "
                         f"{undone.get('result', {}).get('count')} rather than "
                         f"nine — which is a loop that "
                         f"{'makes' if undone.get('result', {}).get('count', 0) > 9 else 'destroys'}"
                         f" matter every time it is run")
    if wrong:
        break
if wrong:
    fail(f"the compression recipes and the arithmetic disagree: {wrong[:3]}")

# Every tier framed in its own metal. Two tiers framed the same are two tiers
# a player miscrafts, and the mistake costs them the difference between them.
frames = {}
for material, tier, id in compress_ids[:compress_tiers]:
    body = read(RES, "assets", NS, f"models/block/{id}.json")
    if not body:
        continue
    textures = json.loads(body).get("textures", {})
    frame = textures.get("frame")
    material_face = textures.get("material")
    if not frame or not material_face:
        fail(f"{id} is drawn without a frame or without a face, so the tier "
             f"cannot be read off it")
        break
    if frame == material_face:
        fail(f"{id} is framed in the same thing it is made of, so it looks "
             f"exactly like the block it compresses")
        break
    if frame in frames:
        fail(f"tier {tier} and tier {frames[frame]} are both framed in {frame}, "
             f"so a player cannot tell them apart across a room")
        break
    frames[frame] = tier
if len(frames) and len(frames) != compress_tiers:
    fail(f"{len(frames)} of {compress_tiers} tiers were checked for a frame")

compress_src = squashed(strip_comments(
    read(SRC, "registry/LogisticBlocks.java"), True))
if "for (String material : CompressRules.MATERIALS) { for (int tier = 1; " \
        "tier <= CompressRules.TIERS; tier++) {" not in compress_src:
    fail("the compressions are not registered from the two lists, so a "
         "sixtieth material is nine blocks that do not exist and nothing says so")
if "if (made.size() != CompressRules.howMany()) { throw new IllegalStateException(" \
        not in compress_src:
    fail("nothing counts the compressions registered against the rules")
if ".strength(hardness, resistance)" not in compress_src:
    fail("a compression's hardness is not read off its tier, so the ladder the "
         "harness proved climbs is not the ladder a player swings a pickaxe at")

compress_items = squashed(strip_comments(
    read(SRC, "registry/LogisticItems.java"), True))
if "ITEMS.registerSimpleBlockItem(entry.getValue())" not in compress_items:
    fail("the compressions have no items, so they are five hundred blocks only a "
         "command can place")

print(f"compression        : {len(compress_materials)} material(s) x "
      f"{compress_tiers} tier(s) = {len(compress_ids)} block(s), every one "
      f"compressed out of nine of the tier below and coming apart into nine "
      f"again, and every tier framed in a different one of the game's own metals")

# ---------------------------------------------------------------------------
# Packs
# ---------------------------------------------------------------------------
#
# Four jars' worth of one idea. The harness proves the arithmetic; what is
# left for here is that every body and every part is an item a player can
# actually get, that the ladder in the rules is the ladder in the recipes, and
# that nothing was registered by hand beside the loop.

if len(pack_bodies) != len(re.findall(r"new Body\(", pack)) \
        or len(pack_parts) != len(re.findall(r"new Part\(", pack)):
    fail(f"this validator read {len(pack_bodies)} body(s) and {len(pack_parts)} "
         f"part(s) out of the pack rules and they declare more, so everything "
         f"below is about a smaller roster than the one that ships")
if len(pack_ids) < 8:
    fail(f"this validator found {len(pack_ids)} packs and parts, and a check "
         f"against a handful of the roster passes whatever is wrong with the rest")

missing = []
for name in pack_ids:
    for where in (f"assets/{NS}/textures/item/{name}.png",
                  f"assets/{NS}/models/item/{name}.json",
                  f"data/{NS}/recipe/{name}.json"):
        if not os.path.isfile(os.path.join(RES, where)):
            missing.append(f"{name} has no {where.rsplit('/', 2)[-2]}")
    if f"item.{NS}.{name}" not in lang:
        missing.append(f"{name} has no name, so it is called by its id")
if missing:
    fail(f"{len(missing)} of {len(pack_ids) * 4} things a pack needs are missing: "
         + ", ".join(missing[:6]))

# The ladder in the recipes: every bag above the first is built out of the bag
# below it, or the ladder is five unrelated recipes and a player with a
# rucksack has three bags they can never use again.
carried = [one for one in pack_bodies if one[3]]
ladder = 0
for at, (name, slots, fittings, _carried) in enumerate(carried):
    body = read(RES, "data", NS, f"recipe/{name}.json")
    if not body:
        continue
    key = json.loads(body).get("key", {})
    asked = {one.get("item") for one in key.values() if isinstance(one, dict)}
    if at == 0:
        if any(str(one).startswith(f"{NS}:") for one in asked):
            fail(f"the first bag is built out of {asked}, and the first rung of a "
                 f"ladder cannot be built out of a rung")
    else:
        below = f"{NS}:{carried[at - 1][0]}"
        if below not in asked:
            fail(f"{name} is not built out of {carried[at - 1][0]}, so the bag "
                 f"below it is a dead end and a player who made one has wasted it")
        else:
            ladder += 1
if carried and ladder != len(carried) - 1:
    fail(f"{ladder} of {len(carried) - 1} bags are built out of the bag below them")

# And the same for the pockets, which are the part of the roster that has a
# ladder at all.
pockets = [one for one in pack_parts if one[1] == "space"]
pockets.sort(key=lambda one: one[2])
for at, (name, _kind, _slots) in enumerate(pockets[1:], start=1):
    body = read(RES, "data", NS, f"recipe/{name}.json")
    if not body:
        continue
    asked = {one.get("item") for one in json.loads(body).get("key", {}).values()
             if isinstance(one, dict)}
    if f"{NS}:{pockets[at - 1][0]}" not in asked:
        fail(f"{name} is not built out of {pockets[at - 1][0]}, so the smaller "
             f"pocket is a dead end")

# Every kind of part drawn as its own thing. A row of identical plates down
# the side of a bag tells a player nothing at all, and it is the one thing
# about this roster that no amount of arithmetic can notice.
drawn = {}
for name, kind, slots in pack_parts:
    where = os.path.join(RES, "assets", NS, "textures/item", name + ".png")
    if not os.path.isfile(where):
        continue
    with open(where, "rb") as handle:
        sprite = handle.read()
    if sprite in drawn and drawn[sprite][1] != kind:
        fail(f"{name} and {drawn[sprite][0]} are drawn exactly the same and are "
             f"different kinds of part, so a row of them down the side of a bag "
             f"says nothing about what is fitted")
        break
    drawn[sprite] = (name, kind)

pack_src = squashed(strip_comments(read(SRC, "registry/LogisticItems.java"), True))
if "for (String name : PackRules.catalogue()) {" not in pack_src:
    fail("the packs are not registered by walking the roster, so a ninth body is "
         "an item that does not exist and nothing says so")
if "if (made.size() != PackRules.howMany()) { throw new IllegalStateException(" \
        not in pack_src:
    fail("nothing counts the packs registered against the roster they were "
         "walked from")
if "new Item.Properties().stacksTo(1)" not in pack_src:
    fail("a pack stacks, so a player can put sixty-four bags in one slot and "
         "every one of them holds what the one on top holds")

print(f"what a pack holds  : {len(pack_bodies)} body(s) and {len(pack_parts)} "
      f"part(s), every one drawn, named and craftable, every bag above the first "
      f"built out of the bag below it, and none of them stacking")

# ---------------------------------------------------------------------------
# The devices and the logic layer
# ---------------------------------------------------------------------------
#
# The harness proves the logic terminates and is typed. What is left for here
# is that the seven devices exist as blocks a player can build, that each is
# drawn as its own thing — a wall of identical faces tells a player nothing
# about what their network does — and that every one of them hangs off the bus
# it needs, so none of them is a device you can put down on its own and wonder
# about.

if len(net_devices) != len(re.findall(r"new Device\(", net)):
    fail(f"this validator read {len(net_devices)} of "
         f"{len(re.findall(r'new Device', net))} devices out of the rules")
if len(net_devices) < 4 or len(net_ops) < 8:
    fail(f"this validator found {len(net_devices)} device(s) and {len(net_ops)} "
         f"operator(s), and a check against a handful passes whatever is wrong "
         f"with the rest")

missing = []
for name, role, draws in net_devices:
    for where in (f"assets/{NS}/textures/block/{name}.png",
                  f"assets/{NS}/models/block/{name}.json",
                  f"assets/{NS}/blockstates/{name}.json",
                  f"assets/{NS}/models/item/{name}.json",
                  f"data/{NS}/loot_table/blocks/{name}.json",
                  f"data/{NS}/recipe/{name}.json"):
        if not os.path.isfile(os.path.join(RES, where)):
            missing.append(f"{name} has no {where.rsplit('/', 2)[-2]}")
    if f"block.{NS}.{name}" not in lang:
        missing.append(f"{name} has no name, so it is called by its id")
if missing:
    fail(f"{len(missing)} of {len(net_devices) * 7} things a device needs are "
         f"missing: " + ", ".join(missing[:6]))

# Every device drawn as its own thing, and every one of them built on a bus.
faces = {}
for name, role, draws in net_devices:
    where = os.path.join(RES, "assets", NS, "textures/block", name + ".png")
    if os.path.isfile(where):
        with open(where, "rb") as handle:
            sprite = handle.read()
        if sprite in faces:
            fail(f"{name} and {faces[sprite]} are drawn exactly the same, so a "
                 f"wall of devices says nothing about what a network does")
            break
        faces[sprite] = name
    body = read(RES, "data", NS, f"recipe/{name}.json")
    if body:
        asked = {one.get("item") for one in json.loads(body).get("key", {}).values()
                 if isinstance(one, dict)}
        if f"{NS}:bus" not in asked:
            fail(f"{name} is not built out of a bus, so it is a device a player "
                 f"can put down with nothing to hang it off and no way of "
                 f"knowing why it does nothing")
            break

net_src = squashed(strip_comments(read(SRC, "registry/LogisticBlocks.java"), True))
if "for (NetRules.Device device : NetRules.DEVICES) {" not in net_src:
    fail("the devices are not registered by walking the roster, so an eighth is "
         "a block that does not exist and nothing says so")
if "if (made.size() != NetRules.howMany()) { throw new IllegalStateException(" \
        not in net_src:
    fail("nothing counts the devices registered against the roster")

print(f"the network's face : {len(net_devices)} device(s) filling "
      f"{len({one[1] for one in net_devices})} role(s) and {len(net_ops)} "
      f"operator(s) in the logic layer, every device drawn as its own thing and "
      f"every one of them built on the bus it hangs off")

# ---------------------------------------------------------------------------
# The devices at work
# ---------------------------------------------------------------------------
#
# Seven blocks that were plain cubes until they had a block entity. What is
# checked here is that each does what NetRules says it does, in the statement
# that does it: a rule that names half a statement survives the edit it was
# written to catch, so every rule below names a whole one.


def need(text, statement, complaint):
    if statement not in text:
        fail(complaint)


device_src = squashed(read(SRC, "block/DeviceBlock.java"))
need(device_src, "public class DeviceBlock extends BaseEntityBlock {",
     "a device is not a block with a block entity, so it is a cube that does nothing")
ticking = squashed(body_of(strip_comments(read(SRC, "block/DeviceBlock.java"), True),
                           "void serverTick("))
need(ticking, "if (++since < NetRules.TICK_EVERY) { return; }",
     "a device does not wait NetRules.TICK_EVERY between rounds, so a wall of "
     "importers asks every chest it faces what it holds twenty times a second")
need(ticking, "findController(level, pos);",
     "a device never looks for its controller along the bus, so it works with "
     "none or keeps working after the bus is cut")
need(ticking, "if (controller.share() <= 0.0F) { return; }",
     "a device on a dark grid goes on working, so a network nobody feeds moves "
     "items for nothing")
for role, statement in (("importer", "case NetRules.IMPORT -> importRound(controller);"),
                        ("exporter", "case NetRules.EXPORT -> exportRound(controller);"),
                        ("detector", "case NetRules.SIGNAL -> power(level, pos, state, detects());")):
    need(ticking, statement, f"the {role} is never run on its tick, so it is a "
                             f"block that does nothing")
importing = squashed(body_of(strip_comments(read(SRC, "block/DeviceBlock.java"), True),
                             "private void importRound("))
need(importing, "IItemHandler into = controller.asOneInventory();",
     "an importer pulls into something other than its network")
need(importing, "if (offered.isEmpty() || !passes(offered)) { continue; }",
     "an importer ignores its filter, so it empties a chest it was told to take "
     "one thing out of")
need(importing, "move(from, slot, into, moved);",
     "an importer works out what it could move and then moves nothing, or moves it "
     "some way other than the one path that puts back what did not fit")
exporting = squashed(body_of(strip_comments(read(SRC, "block/DeviceBlock.java"), True),
                             "private void exportRound("))
need(exporting, "if (into == null || filter.isEmpty()) { return; }",
     "an exporter with no filter pushes anything, so one placed against a chest "
     "empties the whole network into it")
need(exporting, "move(from, slot, into, moved);",
     "an exporter does not move through the one path that puts back what did not "
     "fit")
moving = squashed(body_of(strip_comments(read(SRC, "block/DeviceBlock.java"), True),
                          "private void move("))
need(moving, "left = from.insertItem(slot, left, false);",
     "what a device took and could not place is not put back where it came from, "
     "so a slot that changed under it loses the difference")
need(device_src, "NetRules.Value said = NetRules.evaluate( NetRules.detectorExpression(idOf(filter), threshold), this);",
     "a detector compares a count in Java rather than running the expression the "
     "harness proved, so the logic layer is a file nothing in the world reads")
need(device_src, "NetRules.Value line = NetRules.evaluate( NetRules.monitorExpression(idOf(filter)), this);",
     "a monitor does not say what it was shown through the logic layer")
need(device_src, "threshold = NetRules.nextThreshold(threshold);",
     "a detector's threshold is not stepped along the ladder in the rules")
need(device_src, "case NetRules.EXPOSE -> facingHandler();",
     "a storage bus does not bring the inventory it faces onto the network")
need(device_src, "case NetRules.LINK -> linked();",
     "an ender link does not bring the other network onto this one")
need(device_src, "return controller == null ? null : controller.asOneInventory();",
     "an interface offers something other than its network to what takes from it")
need(device_src, "if (linking > 0) { return null; }",
     "an ender link reaches through another ender link, so two linked networks "
     "each contain the other and asking either how many slots it has never returns")
need(device_src, "if (there instanceof ControllerBlock.ControllerBlockEntity "
                 "|| there instanceof DeviceBlockEntity) { return null; }",
     "a device may face a controller or another device, so a storage bus pointed "
     "at its own network makes the network contain itself")
need(device_src, "gone.leaveChannel();",
     "a broken ender link stays on its channel, so the other end goes on sharing "
     "with a block that is not there")

network_src = squashed(read(SRC, "block/LogisticNetwork.java"))
need(network_src, "devices.add(next.immutable()); continue;",
     "the network walk finds no devices, or walks through them as if they were bus")
need(network_src, "return new Found(found, faces, surchargeOf(level, devices), "
                  "found.size() + faces.size() > LogisticRules.MOST_DEVICES);",
     "a device does not take a slot on its controller, or pays nothing for it")
need(squashed(net), "extra += one.draws() - 1;",
     "a dear device's surcharge is not what it draws beyond a slot")
need(squashed(rules), "return new IndustryRules.Machine(devices * DEVICE_DRAW "
                      "+ extra * DEVICE_DRAW, IndustryRules.Voltage.LOW);",
     "what the devices cost is not billed to the grid, so a network of storage "
     "buses costs what a network of drawers does")
need(squashed(read(SRC, "block/ControllerBlock.java")),
     "IItemHandler brought = device == null ? null : device.store();",
     "a controller's inventory leaves out what its devices bring, so a storage bus "
     "and an ender link are faces onto nothing")
entities_src = squashed(strip_comments(read(SRC, "registry/LogisticBlockEntities.java"), True))
need(entities_src, "event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, "
                   "DEVICE.get(), (entity, side) -> entity.exposed());",
     "an interface or ender link has no item capability, so nothing can take "
     "from it")
need(entities_src, "for (NetRules.Device device : NetRules.DEVICES) { blocks[at++] = "
                   "LogisticBlocks.DEVICES.get(device.name()).get(); }",
     "the device block entity is not bound to every device on the roster, so an "
     "eighth device has no block entity and crashes the moment it is placed")
need(net_src, "() -> new DeviceBlock(steel().noOcclusion(), device)));",
     "the devices are registered as plain blocks rather than as the device that "
     "does their job")

wanted_states = {f"facing={f},powered={p}"
                 for f in ("north", "south", "east", "west", "up", "down")
                 for p in ("false", "true")}
for name, role, draws in net_devices:
    where = os.path.join(RES, "assets", NS, f"blockstates/{name}.json")
    if os.path.isfile(where):
        variants = set(json.load(open(where, encoding="utf-8")).get("variants", {}))
        if variants != wanted_states:
            fail(f"{name}'s blockstate does not name every facing times both states "
                 f"of its redstone, so a device hung under a machine is a purple cube")
            break

# Every sentence a device or a pack says is in the language file.
spoken = set()
for path in java_files:
    spoken |= set(re.findall(r'"(elysiumlogistics\.[a-z_]+\.[a-z_.]+)"',
                             strip_comments(read(path), True)))
unsaid = sorted(key for key in spoken if key not in lang)
if unsaid:
    fail(f"{len(unsaid)} sentence(s) the blocks say are not in the language file, so "
         f"a player reads the key: " + ", ".join(unsaid[:6]))

print(f"the devices at work: {len(net_devices)} device(s) each ticking every "
      f"NetRules.TICK_EVERY on its controller's draw, the detector and monitor "
      f"running the logic layer's own expressions, and {len(spoken)} sentence(s) "
      f"all in the language file")

# ---------------------------------------------------------------------------
# Packs at work
# ---------------------------------------------------------------------------
#
# The harness proves the arithmetic of a pack. What is left for here is that
# every change a pack can go through in the world is decided by that
# arithmetic: every insertion, every fitting, every removal, and each of the
# parts' behaviours.

store_src = squashed(read(SRC, "pack/PackStore.java"))
need(store_src, "return PackRules.capacity(body, fitted());",
     "a pack's capacity is not the rules' sum of what is fitted")
need(store_src, "if (stack.isEmpty() || !mayHold(stack)) { return stack; }",
     "a pack takes a pack, so capacity is a recursion")
need(store_src, "return !(ours && !PackRules.mayHold(id.getPath())) "
                "&& !stack.has(LogisticComponents.CONTENTS.get());",
     "what a pack may hold is not decided by the rules' roster and by what the "
     "stack carries, so a bag renamed in an anvil goes inside a bag")
need(store_src, "if (!chosen && !PackRules.admits(fitted, held)) { return stack; }",
     "a sieve does not sieve, so a sieved pack takes whatever is picked up")
need(store_src, "if (PackRules.accepted(body, fitted, stored, 1) != 1) { break; }",
     "a pack starts new piles without asking the rules whether there is room")
need(store_src, "if (!left.isEmpty() && PackRules.voidsOverflow(fitted, held)) {",
     "a pack destroys what does not fit without a seal, or destroys things it "
     "does not already hold")
need(store_src, "if (!PackRules.fits(body, after)) {",
     "a part is fitted without the rules being asked, so a pocket goes in twice")
need(store_src, "if (!PackRules.mayRemove(body, fitted, going, occupied())) { return null; }",
     "a part comes out of a pack too full to lose it, and what was in the slots "
     "that went with it stops existing")
need(store_src, "if (PackRules.has(fitted, PackRules.COOK)) { cook(level); }",
     "an ember plate cooks nothing")
need(store_src, "if (PackRules.has(fitted, PackRules.PRESS)) { press(level); }",
     "a press screw presses nothing")
need(store_src, "int made = PackRules.cooked(raw.getCount(), result.getCount(), "
                "result.getMaxStackSize());",
     "a pack cooks by arithmetic of its own, so a pile can cook into more than a "
     "slot holds")
need(store_src, "int[] made = PackRules.pressed(count);",
     "a pack presses by arithmetic of its own rather than the compression step")
need(store_src, "if (!left.isEmpty()) { items = before; continue; }",
     "a press that cannot place what it made keeps going, and the nine it took "
     "are gone")

menu_src = squashed(read(SRC, "pack/PackMenu.java"))
need(menu_src, "return PackStore.mayHold(stack) && super.mayPlace(stack);",
     "a pack's screen lets a pack be put inside it")
need(menu_src, "public boolean mayPickup(Player player) { return false; }",
     "the open bag can be picked up out of its own screen, and whatever is put in "
     "afterwards is written to a stack that is not there")
need(menu_src, "if (locked >= 0 && type == ClickType.SWAP && button == locked) { return; }",
     "a number key swaps the open bag out from under its own screen")
need(menu_src, "super(typeFor(rows), id);",
     "a pack's screen is not the library's chest screen sized to its rows")
need(menu_src, "this.page = new Page(store, PackRules.firstOn(pageNumber), "
               "PackRules.shownOn(store.capacity(), pageNumber), level, still);",
     "a pack's screen is not a page the rules laid out, so a big pack is capped "
     "at one screen and the rest cannot be reached")
if re.search(r"Registries\.MENU\b|IMenuTypeExtension|new MenuType<",
             "".join(strip_comments(read(path)) for path in java_files)):
    fail("the module registers a menu type of its own, which is a new screen; a "
         "pack opens the library's chest screen")

item_src = squashed(read(SRC, "pack/PackItem.java"))
need(item_src, "bag.set(LogisticComponents.PAGE.get(), PackRules.nextPage(room, page));",
     "a big bag never turns its page, so everything past the first screen of it "
     "is unreachable")
need(item_src, "int locked = hand == InteractionHand.MAIN_HAND ? "
               "player.getInventory().selected : Inventory.SLOT_OFFHAND;",
     "the open bag's own slot is not the one locked")
need(item_src, "new PackMenu(id, inventory, store, shown, level, () -> "
               "who.getItemInHand(hand) == bag && !bag.isEmpty(), locked)",
     "a bag's screen stays open after the bag has left the hand it was opened from")
need(item_src, "player.displayClientMessage(refit(store, player, other), true);",
     "crouching with a bag does not fit or unfit a part")

events_src = squashed(read(SRC, "pack/PackEvents.java"))
need(events_src, "@EventBusSubscriber(modid = ElysiumLogistics.MODID, "
                 "bus = EventBusSubscriber.Bus.GAME)",
     "the parts' behaviours are not subscribed to the game, so none of them run")
need(events_src, "if (player.level().isClientSide || entity.hasPickUpDelay() || "
                 "(entity.getTarget() != null && !entity.getTarget().equals(player.getUUID()))) "
                 "{ return; }",
     "a gathering lens takes what was just thrown, or what was meant for somebody else")
need(events_src, "for (PackStore bag : worn(player, PackRules.GATHER, null)) {",
     "a gathering lens gathers nothing")
need(events_src, "coming.setCount(left.getCount());",
     "what a bag gathered is not taken off the stack on the ground, so it is "
     "gathered and picked up both")
need(events_src, "if (worth == null || !PackRules.worthEating(missing, worth.nutrition())) "
                 "{ continue; }",
     "a ration tin eats food whose worth is wasted")
need(events_src, "int wanted = PackRules.restockWanted(held.getCount(), held.getMaxStackSize());",
     "a restocker overfills a hand or fills an empty one")
need(events_src, "if (player.tickCount % PackRules.FEED_EVERY == 0) { feed(player, level); }",
     "a ration tin never feeds anybody")
need(events_src, "if (player.tickCount % PackRules.RESTOCK_EVERY == 0) { restock(player); }",
     "a restocker never restocks")

placed_src = squashed(read(SRC, "pack/PackBlock.java"))
need(placed_src, "Containers.dropContents(level, pos, crate.store().items());",
     "a broken crate does not drop what is in it, and a container never loses what "
     "is in it")
need(placed_src, "Containers.dropContents(level, pos, parts);",
     "a broken crate destroys the parts fitted to it")
need(placed_src, "return store().insert(stack, simulate, false, level);",
     "a hopper reaches a crate some way other than through its rules")
need(entities_src, "event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, "
                   "PACK.get(), (entity, side) -> entity.handler());",
     "a crate has no item capability, so no hopper or storage bus can reach it")
need(net_src, "if (!body.carried()) { made.put(body.name(), BLOCKS.register(body.name(), "
              "() -> new PackBlock(timber().strength(2.5F), body))); }",
     "the placed bodies are not registered as blocks off the roster, so a crate is "
     "an item that cannot be put down")
need(net_src, "if (made.size() != PackBlock.placedBodies().size()) {",
     "nothing counts the placed bodies registered against the roster")
need(pack_src, "made.put(name, ITEMS.registerItem(name, properties -> new "
               "PackItem(properties, body), new Item.Properties().stacksTo(1)));",
     "a bag is a plain item, so using it does nothing")
need(squashed(main_class), "com.elysium.logistics.registry.LogisticComponents"
                           ".COMPONENTS.register(modEventBus);",
     "the components a bag keeps its contents in are never registered, so a bag "
     "forgets everything the moment it is closed")

axe = []
axe_path = os.path.join(RES, "data/minecraft/tags/block/mineable/axe.json")
if os.path.isfile(axe_path):
    axe = json.load(open(axe_path, encoding="utf-8")).get("values", [])
placed_bodies = [one for one in pack_bodies if not one[3]]
missing = []
for name, slots, fittings, carried in placed_bodies:
    for where in (f"assets/{NS}/blockstates/{name}.json",
                  f"assets/{NS}/models/block/{name}.json",
                  f"data/{NS}/loot_table/blocks/{name}.json"):
        if not os.path.isfile(os.path.join(RES, where)):
            missing.append(f"{name} has no {where.rsplit('/', 2)[-2]}")
    if f"block.{NS}.{name}" not in lang:
        missing.append(f"{name} has no name as a block")
    if f"{NS}:{name}" not in axe:
        missing.append(f"{name} is in no mineable tag")
if missing:
    fail(f"{len(missing)} thing(s) a placed body needs are missing: "
         + ", ".join(missing[:6]))
if not placed_bodies:
    fail("this validator found no placed bodies in the rules, so everything above "
         "about crates checked nothing")

print(f"packs at work      : every insertion, fitting and removal ruled by the "
      f"rules, {len(placed_bodies)} placed bod(ies) with a block, a drop and a "
      f"capability, a page at a time in the game's own chest screen, and the "
      f"parts' behaviours on the game bus")

# ---------------------------------------------------------------------------
# What a player fits to a drawer
# ---------------------------------------------------------------------------
#
# A drawer's lock and its voiding were saved, loaded and obeyed from the day
# the drawer was written, and nothing a player could hold set either: only
# /data did. A void seal and a key are how a survival player does. Every rule
# below names the statement that does it, so undoing one fails here rather
# than leaving a flag quietly unreachable again.

drawer_src = strip_comments(read(SRC, "block/DrawerBlock.java"), True)
drawer_store_src = strip_comments(read(SRC, "block/DrawerStore.java"), True)

drawer_use = squashed(body_of(drawer_src, "protected ItemInteractionResult useItemOn("))
KEY_TURNS = ("if (held.is(ours(LogisticRules.DRAWER_KEY))) { "
             "player.displayClientMessage(into.turnKey(), true); "
             "return ItemInteractionResult.SUCCESS; }")
SEAL_FITS = ("if (held.is(ours(LogisticRules.DRAWER_SEAL))) { "
             "player.displayClientMessage(into.seal(held), true); "
             "return ItemInteractionResult.SUCCESS; }")
PUT_AWAY = "if (into.offer(held)) { return ItemInteractionResult.SUCCESS; }"
need(drawer_use, KEY_TURNS,
     "a drawer key used on a drawer does not turn in it, or is spent doing it, so the "
     "lock is back to a flag only /data can set")
need(drawer_use, SEAL_FITS,
     "a void seal used on a drawer is not fitted to it, so a drawer voids for /data "
     "and for nobody else")
if KEY_TURNS in drawer_use and SEAL_FITS in drawer_use and PUT_AWAY in drawer_use \
        and drawer_use.find(PUT_AWAY) < max(drawer_use.find(KEY_TURNS),
                                            drawer_use.find(SEAL_FITS)):
    fail("a drawer puts away what it is offered before asking whether it is the key or "
         "the seal, so the first one a player uses is filed away inside it")
need(drawer_use, "player.displayClientMessage(into.says(), true); "
                 "return ItemInteractionResult.CONSUME;",
     "a drawer that takes none of what it is offered says nothing, so a locked one "
     "refusing a new kind looks broken")

sealing = squashed(body_of(drawer_src, "public Component seal(ItemStack held)"))
SEAL_REFUSED = ('if (!LogisticRules.takesSeal(store.voiding())) { return '
                'Component.translatable("elysiumlogistics.drawer.sealed_twice", name); }')
need(sealing, SEAL_REFUSED,
     "a drawer that already voids takes a second seal and spends it on nothing")
need(sealing, "store.voiding(true);",
     "fitting a seal does not turn the drawer's voiding on, so the seal is spent and "
     "nothing is voided")
if "held.shrink(1);" not in sealing or (
        SEAL_REFUSED in sealing
        and sealing.find("held.shrink(1);") < sealing.find(SEAL_REFUSED)):
    fail("a seal is not spent when it is fitted, or is spent when it is refused, so "
         "one seal seals every drawer in the world or a refusal costs a seal")

unsealing = squashed(body_of(drawer_src, "public ItemStack unseal()"))
need(unsealing, "ItemStack seal = sealsHeld(); store.voiding(false); return seal;",
     "taking a seal out does not hand back the seal that was fitted, or leaves the "
     "drawer voiding after it has gone")
seals_held = squashed(body_of(drawer_src, "public ItemStack sealsHeld()"))
need(seals_held, "return new ItemStack(ours(LogisticRules.DRAWER_SEAL), "
                 "LogisticRules.sealsIn(store.voiding()));",
     "what a drawer hands back for its seal is not what the rules say it holds, so a "
     "seal is made or lost on the way out")
without = squashed(body_of(drawer_src, "protected InteractionResult useWithoutItem("))
need(without, "if (player.isShiftKeyDown() && from.store().voiding()) { "
              "ItemStack seal = from.unseal();",
     "crouching with an empty hand at a sealed drawer never takes the seal back out, so "
     "a drawer sealed once voids for good")
need(without, "if (player.getMainHandItem().isEmpty()) { "
              "player.setItemInHand(InteractionHand.MAIN_HAND, seal); } else { "
              "player.getInventory().placeItemBackInInventory(seal); }",
     "a seal taken out of a drawer is never given to the player, so taking it out "
     "destroys it")
breaking = squashed(body_of(drawer_src, "protected void onRemove("))
need(breaking, "Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), "
               "from.sealsHeld());",
     "a broken drawer destroys the seal fitted to it, and a part is never lost with the "
     "block it was fitted to")

turning = squashed(body_of(drawer_src, "public Component turnKey()"))
need(turning, 'if (store.locked()) { store.locked(false); return '
              'Component.translatable("elysiumlogistics.drawer.unlocked"); }',
     "a key does not unlock a locked drawer, so a drawer locked once stays locked")
need(turning, 'if (!LogisticRules.mayLock(store.kindsKept())) { return '
              'Component.translatable("elysiumlogistics.drawer.nothing_to_lock"); }',
     "a key locks a drawer with nothing in it, which then refuses everything and looks "
     "broken")
need(turning, 'store.locked(true); return '
              'Component.translatable("elysiumlogistics.drawer.locked_now");',
     "a key never locks a drawer, so it forgets what it held the first time it runs dry")

KEEPS_KIND = ("if (!LogisticRules.keepsKind(locked, counts[slot])) { "
              "kinds[slot] = ItemStack.EMPTY; }")
need(squashed(body_of(drawer_store_src, "public ItemStack extractItem(")), KEEPS_KIND,
     "a drawer decides for itself whether an emptied slot keeps its kind, so the lock "
     "the rules describe is not the lock it obeys")
need(squashed(body_of(drawer_store_src, "public void locked(boolean now)")),
     "locked = now; for (int slot = 0; slot < kinds.length; slot++) { " + KEEPS_KIND
     + " } changed.run();",
     "unlocking a drawer leaves its emptied slots holding their kinds, so it stays locked "
     "to them while it says it is empty")

saying = squashed(body_of(drawer_src, "public Component says()"))
need(saying, 'if (store.locked()) { line.append(" ").append('
             'Component.translatable("elysiumlogistics.drawer.locked")); }',
     "a locked drawer does not say it is locked, and nothing on its front does")
need(saying, 'if (store.voiding()) { line.append(" ").append('
             'Component.translatable("elysiumlogistics.drawer.voids")); }',
     "a sealed drawer does not say it voids overflow, and nothing on its front does")
if saying.count("return ") != 1 or "return line; }" not in saying:
    fail("a drawer says what it holds on some path without what a key or a seal has made "
         "of it, so an empty locked drawer refuses things and never says why")

rules_kept = strip_comments(rules, True)
drawer_key = re.search(r'public static final String DRAWER_KEY\s*=\s*"([a-z_]+)";', rules_kept)
drawer_seal = re.search(r'public static final String DRAWER_SEAL\s*=\s*"([a-z_]+)";', rules_kept)
if not drawer_key or not drawer_seal:
    fail("the rules do not name a drawer's key and seal, so nothing here knows which "
         "items a drawer answers to")
else:
    if drawer_seal.group(1) not in [one[0] for one in pack_parts if one[1] == "void"]:
        fail(f"a drawer is sealed with {drawer_seal.group(1)}, which is not the pack's void "
             f"part, so the seal a bag takes does nothing to a drawer")
    key = drawer_key.group(1)
    missing = []
    for where in (f"assets/{NS}/textures/item/{key}.png",
                  f"assets/{NS}/models/item/{key}.json",
                  f"data/{NS}/recipe/{key}.json"):
        if not os.path.isfile(os.path.join(RES, where)):
            missing.append(where.rsplit("/", 2)[-2])
    if f"item.{NS}.{key}" not in lang:
        missing.append("name")
    if missing:
        fail(f"the {key} has no {', '.join(missing)}, so a survival player cannot make "
             f"one or cannot tell what it is")
    made_key = crafted.get(key, {}).get("result", {}).get("id")
    if key in crafted and made_key != f"{NS}:{key}":
        fail(f"the {key}'s recipe makes {made_key}, so the key itself is made by nothing")
    need(pack_src, "public static final DeferredItem<Item> DRAWER_KEY = "
                   "ITEMS.registerSimpleItem(LogisticRules.DRAWER_KEY);",
         "the drawer key is not registered, so its recipe makes an item that does not "
         "exist")
    need(pack_src, "output.accept(DRAWER_KEY.get());",
         "the drawer key is in no creative tab, so nobody in a creative world can find it")

print("a drawer's fittings: a void seal fitted by hand and handed back whole, taken out "
      "or broken; a key that locks it to what it holds, unlocks it, and is never spent; "
      "and a status line that says both")

print("what it declares   : the game, the library, and the grid a network hangs off")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all logistics checks passed")
