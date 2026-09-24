#!/usr/bin/env python3
"""
Validate Elysium Foundry against its sources.

Run from anywhere:   python3 elysium-foundry/validate.py

Two claims, and neither can be checked by reading four hundred recipes: the
material chain has no cycles in it, and no step of it gives back more material
than went in. A pack that gets either wrong does not crash — it leaks.

There is a third thing this module owes the pack, and it is checked here too:
elysium-ores generates ore for thirty-one materials, most of which were only
ever supplied by the mods being replaced.

  1. INDEPENDENCE. Vanilla, the library, and the three ages below.
  2. PURITY, and the harness run rather than read.
  3. THE CHAIN. No cycles, no matter out of nothing, no tier skipped.
  4. THE DEBT. Every ore the pack generates drops something.
  5. THE SEAM. What it declares.
"""
import json
import json
import os
import re
import subprocess
import sys
import tempfile

WORK = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(WORK)
RES = os.path.join(WORK, "src/main/resources")
SRC = os.path.join(WORK, "src/main/java/com/elysium/foundry")
PURE = os.path.join(SRC, "rules")
BELOW = [os.path.join(ROOT, "elysium-kinetics/src/main/java/com/elysium/kinetics/rules"),
         os.path.join(ROOT, "elysium-industry/src/main/java/com/elysium/industry/rules"),
         os.path.join(ROOT, "elysium-atomics/src/main/java/com/elysium/atomics/rules")]
HARNESS = os.path.join(WORK, "tools/FoundryHarness.java")
HARNESS_MAIN = "com.elysium.foundry.rules.FoundryHarness"

#: How long the arithmetic is allowed to take. The honest sweeps in
#: the harness run in a few seconds; anything past this is a loop.
HARNESS_SECONDS = 60
NS = "elysiumfoundry"
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


def squashed(text):
    """Every run of whitespace as one space, so a rule can name a whole statement.

    Naming the whole statement is the point: a rule that looks for a token
    which survives the very edit it was meant to catch is a rule that has never
    failed, and this project has now been bitten by that eleven times.
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
    print("This is not the elysium-foundry tree.")
    sys.exit(1)

print("=" * 70)
print("elysium-foundry — the material chain, and the proof that none of it leaks")
print("=" * 70)

# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------

FOREIGN = re.compile(
    r"(?:gregtech\.|com\.gregtechceu|blusunrize\.|mekanism\.|thermal\.)"
    r"|\b(?:gtceu|gregtech|thermal|thermal_foundation|immersiveengineering)\s*:"
    r"|\"(?:gtceu|gregtech|thermal_foundation)\"", re.I)
UPWARD = re.compile(r"com\.elysium\.(transit|arsenal|bestiary)")

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
           "elysiumindustry", "elysiumatomics"}
for mod in declared - allowed:
    fail(f"the module declares a dependency on {mod}; it needs the game, the library, "
         f"and the grid its machines hang off")
for needed in ("neoforge", "minecraft", "elysiumlib", "elysiumindustry",
               "elysiumatomics"):
    if needed not in declared:
        fail(f"the module does not declare {needed}")

print(f"independence       : {len(java_files)} source file(s), no chain it replaces "
      f"named, and all three ages below declared")

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
                or used.startswith("com.elysium.industry.rules.")
                or used.startswith("com.elysium.atomics.rules.")):
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

rules = read(PURE, "FoundryRules.java")
plain = strip_comments(rules)


def constant(name, kind=r"(?:float|int)"):
    m = re.search(r"public static final " + kind + r"\s+" + name + r"\s*=\s*([^;]+);",
                  plain, re.S)
    return " ".join(m.group(1).split()) if m else None


# ---------------------------------------------------------------------------
# 3. The chain
# ---------------------------------------------------------------------------

for needed in ("cycles", "deepCost", "rootsOf", "needs"):
    if needed not in plain:
        fail(f"there is no {needed}, so one of the two properties this module rests on "
             f"is not stated anywhere and cannot be checked")

walker = body_of(plain, "public static boolean needs(")
if "seen.add(here)" not in walker:
    fail("the walk over the chain has no memory, so asking whether a material is made "
         "of itself on a chain that contains a cycle is itself an endless loop")

cyc = body_of(plain, "public static Set<String> cycles()")
if "needs(material.name(), material.name())" not in cyc:
    fail("the cycle check does not ask each material whether it needs itself, so it is "
         "checking something else")

deep = body_of(plain, "public static int deepCost(")
if "material.mined()" not in deep:
    fail("the deep cost of a mined material is not one unit, so the whole scale is "
         "relative to a moving number")

ore = body_of(plain, "public static int fromOneOre()")
if "AtomicRules.ENRICHER_MULTIPLIER" not in ore:
    fail("the fourth age states its own ore multiplier rather than Age III's, so the "
         "ladder is 1, 2, 3 in one file and something else in another")

unit = constant("UNIT", kind="int")
if unit != "9":
    fail("the unit is not nine, so a nugget is not a whole number of them and every "
         "recipe in the chain has to be checked in floating point")

fits = body_of(plain, "public static boolean fitsIn(")
if "made.tier() <= Math.max(0, tier)" not in fits:
    fail("a casing may be made of something above its own rung, so the ladder has a step "
         "that can be skipped and therefore one fewer rung than it says")

print("the chain          : no cycles, every material rooted in something you can dig, "
      "and the ore ladder left where Age III ended it")

# ---------------------------------------------------------------------------
# 4. The debt, and the chain in the world
# ---------------------------------------------------------------------------

GENERATOR = os.path.join(WORK, "tools/gen_foundry.py")
if not os.path.isfile(GENERATOR):
    fail("there is no generator, so a hundred and more metal items are kept by hand")
else:
    with tempfile.TemporaryDirectory() as tmp:
        environment = dict(os.environ, ELYSIUM_FOUNDRY_OUT=tmp)
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

generator = read(WORK, "tools/gen_foundry.py")

# The chain is declared twice — once as arithmetic the harness executes, once
# as a table the recipes are written from — and a metal that costs one thing in
# the Java and another in the data is a metal whose price depends on which file
# you read.
java_chain = {}
for name, tier, parts in re.findall(
        r'(?:mined|alloy)\("([a-z_]+)", (\d+)([^)]*)\)',
        strip_comments(rules, keep_strings=True)):
    made_of = dict((part, int(count.replace("*", "").replace("UNIT", "").strip() or 1)
                    * (9 if "UNIT" in count else 1))
                   for part, count in re.findall(r'"([a-z_]+)", ([^,)]+)', parts))
    java_chain[name] = (int(tier), made_of)

table_chain = {}
for name, tier, parts in re.findall(
        r'"([a-z_]+)": \((\d+), \{([^}]*)\},', generator):
    made_of = dict((part, int(count.replace("*", "").replace("UNIT", "").strip() or 1)
                    * (9 if "UNIT" in count else 1))
                   for part, count in re.findall(r'"([a-z_]+)": ([^,}]+)', parts))
    table_chain[name] = (int(tier), made_of)

if not java_chain:
    fail("no materials could be read out of the rules, so the two halves cannot be "
         "compared at all")
if set(java_chain) != set(table_chain):
    only_java = sorted(set(java_chain) - set(table_chain))
    only_table = sorted(set(table_chain) - set(java_chain))
    fail(f"the chain in the rules and the chain in the generator name different "
         f"materials: {only_java} are only in the Java and {only_table} only in the "
         f"table")
else:
    for name in sorted(java_chain):
        if java_chain[name][0] != table_chain[name][0]:
            fail(f"{name} is tier {java_chain[name][0]} in the rules and "
                 f"{table_chain[name][0]} in the generator")
        if java_chain[name][1] != table_chain[name][1]:
            fail(f"{name} is made of {java_chain[name][1]} in the rules and "
                 f"{table_chain[name][1]} in the generator — its price depends on which "
                 f"file you read")

items_src = strip_comments(read(SRC, "registry/FoundryItems.java"), True)
java_skips = set(re.findall(r'"([a-z_]+)"', re.search(
    r'SOMEBODY_ELSE_SUPPLIES\s*=\s*Set\.of\(([^;]*)\);', items_src).group(1)))
table_skips = set(re.findall(r'"([a-z_]+)"',
                             re.search(r'SOMEBODY_ELSE = \{([^}]*)\}',
                                       generator).group(1)))
if java_skips != table_skips:
    fail(f"the Java skips {sorted(java_skips)} and the generator skips "
         f"{sorted(table_skips)}; whichever is wrong either registers a second copy of "
         f"somebody else's metal or leaves an item with no model")

casings_java = dict(re.findall(r'new Casing\((\d+), "([a-z_]+)"',
                                 strip_comments(rules, keep_strings=True)))
casings_table = dict((tier, material) for tier, material in
                     re.findall(r"(\d+): \"([a-z_]+)\"", generator))
if casings_java != casings_table:
    fail(f"the casings differ between the rules ({casings_java}) and the generator "
         f"({casings_table})")

lang = {}
lang_path = os.path.join(RES, "assets", NS, "lang/en_us.json")
if os.path.isfile(lang_path):
    lang = json.load(open(lang_path, encoding="utf-8"))
else:
    fail("there is no language file, so a hundred metal items are called by their ids")

made_items = sorted(re.findall(r'"item\.elysiumfoundry\.([a-z_]+)"',
                               json.dumps(lang)))
if len(made_items) < 80:
    fail(f"only {len(made_items)} item(s) are named, which is fewer than the chain has "
         f"shapes — something is registered and invisible")

for name in made_items:
    for kind, where in (("a model", f"models/item/{name}.json"),
                        ("a texture", f"textures/item/{name}.png")):
        if not os.path.isfile(os.path.join(RES, "assets", NS, where)):
            fail(f"{name} has no {kind}")

# The debt itself. Every ore family the pack generates has to have a supplier
# somewhere in the tree, or the ore drops nothing once the old jars go.
catalogue = os.path.join(ROOT, "elysium-ores/src/main/java/com/elysium/ores/data/"
                               "OreCatalogue.java")
families = sorted(set(re.findall(r'new Entry\("[a-z_]+", "([a-z_]+)"',
                                 read(catalogue))))
VANILLA = {"coal", "copper", "diamond", "emerald", "gold", "iron", "lapis",
           "quartz", "redstone"}
suppliers = {}
for dirpath, _, filenames in os.walk(ROOT):
    if "/data/c/tags/item/" not in dirpath.replace(os.sep, "/"):
        continue
    for name in filenames:
        if not name.endswith(".json"):
            continue
        kind = os.path.basename(dirpath)
        if kind not in ("ingots", "raw_materials", "gems"):
            continue
        try:
            values = json.load(open(os.path.join(dirpath, name),
                                    encoding="utf-8")).get("values", [])
        except (ValueError, OSError):
            continue
        real = [v for v in values
                if isinstance(v, str) or (isinstance(v, dict) and v.get("required", True))]
        if real:
            suppliers.setdefault(name[:-5], []).extend(real)

missing = [family for family in families
           if family not in VANILLA and family not in suppliers]
if missing:
    fail(f"{len(missing)} ore famil(ies) the pack generates have no supplier anywhere "
         f"in it, so those ores drop nothing once the mods being replaced are removed: "
         + ", ".join(missing))

print(f"the debt           : {len(families)} ore famil(ies) generated by the pack, every "
      f"one of them supplied; {len(made_items)} metal item(s) modelled and named")

# ---------------------------------------------------------------------------
# 5. The seam
# ---------------------------------------------------------------------------

main_class = read(SRC, "ElysiumFoundry.java")
if f'MODID = "{NS}"' not in main_class:
    fail("the module id is not what everything else in the tree spells it")

print("what it declares   : the game, the library, and the three ages it is built on")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all foundry checks passed")
