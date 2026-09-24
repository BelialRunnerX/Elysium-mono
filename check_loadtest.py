#!/usr/bin/env python3
"""
Load our data the way the game will, here, without anybody pressing a button.

    python3 check_loadtest.py

<h2>Why this exists</h2>

Every other check in this repository reads. This one runs the game.

Four data files shipped in one jar that were valid JSON and named files that
existed, and failed at the exact moment a player pressed **Create World** —
because that is when {@code RegistryDataLoader} first puts our files through
the real codecs. Finding them meant asking Dylan to launch Minecraft and send
back a log, three times in one evening.

So this boots the game's registries outside the launcher, puts stand-ins in
for the blocks and items our own code would have registered, builds a resource
manager over vanilla's data pack and every module's resources, and calls the
same loader with the same registry list. Every error it reports is an error
the game would have reported, named by file, before the jar is built.

<h2>What it cannot do</h2>

It does not run our Java — that needs FML — so NeoForge's own registries are
not loaded here and neither is anything that only exists once the mod's
constructor has run. `check_datapack.py` covers what this cannot see, and this
covers what no amount of reading can.

<h2>What it needs</h2>

The Minecraft classpath at /tmp/mc/cp.txt and log4j beside it. Without either
it says so and passes, because a machine that cannot run the game should not
fail a build over it.
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.join(HERE, "tools", "loadtest")
CLASSPATH_FILE = "/tmp/mc/cp.txt"
LIBS = "/tmp/mc/libs"
OUT = "/tmp/elysium-loadtest"

LOG4J = ["log4j-api-2.19.0.jar", "log4j-core-2.19.0.jar", "log4j-slf4j2-impl-2.19.0.jar"]

#: Loading recipes reaches NeoForge code that reads bytecode. Optional: if ASM
#: is not here the worldgen and loot halves still run, and only recipes and
#: tags are skipped.
EXTRA = ["asm-9.10.1.jar", "asm-analysis-9.10.1.jar", "asm-commons-9.10.1.jar",
         "asm-tree-9.10.1.jar", "asm-util-9.10.1.jar", "commons-io-2.15.1.jar"]


problems = []


def fail(msg):
    problems.append(msg)


def skip(why):
    print(f"load test          : skipped, {why}")
    sys.exit(0)


if not os.path.isfile(CLASSPATH_FILE):
    skip(f"no {CLASSPATH_FILE}")
missing = [name for name in LOG4J if not os.path.isfile(os.path.join(LIBS, name))]
if missing:
    skip(f"log4j is not in {LIBS} ({', '.join(missing)}) — the game cannot start without it")

classpath = open(CLASSPATH_FILE, encoding="utf-8").read().strip()

# One jar on the classpath carries a stale signature manifest — it was
# remapped after signing — and any class the verifier actually checks throws
# "SHA-384 digest error". The game never notices because the launcher does not
# verify; a plain JVM does. An unsigned copy placed first is the whole fix.
UNSIGNED = os.path.join(LIBS, "mapped-at-unsigned.jar")
if os.path.isfile(UNSIGNED):
    # Swapped in where the signed one sat, not put in front of it: the order
    # of this classpath decides which copy of a shared class wins, and moving
    # a jar to the front quietly changes the game we are compiling against.
    classpath = ":".join(UNSIGNED if part.endswith("mapped-at.jar") else part
                         for part in classpath.split(":"))
for name in LOG4J + EXTRA:
    jar = os.path.join(LIBS, name)
    if os.path.isfile(jar):
        classpath += ":" + jar

modules = sorted(name for name in os.listdir(HERE)
                 if name.startswith("elysium-")
                 and os.path.isdir(os.path.join(HERE, name, "src", "main", "resources")))
resources = [os.path.join(HERE, name, "src", "main", "resources") for name in modules]

os.makedirs(OUT, exist_ok=True)

# The ids our own code would have registered, read out of what we ship.
ids = os.path.join(OUT, "ids.txt")
listing = subprocess.run([sys.executable, os.path.join(TOOLS, "ids.py")],
                         capture_output=True, text=True)
if listing.returncode != 0:
    fail("could not list our ids: " + listing.stderr.strip()[:800])
open(ids, "w", encoding="utf-8").write(listing.stdout)

# Every recipe of ours that the harness can be expected to load: all of them
# except the ones whose serializer is our own Java, which this cannot run.
plain = custom = 0
for folder in resources:
    for dirpath, _, names in os.walk(folder):
        if os.sep + "recipe" not in dirpath:
            continue
        for name in names:
            if not name.endswith(".json"):
                continue
            text = open(os.path.join(dirpath, name), encoding="utf-8").read()
            if '"type": "elysium' in text or '"type":"elysium' in text:
                custom += 1
            else:
                plain += 1

shim = [os.path.join(dirpath, name)
        for dirpath, _, names in os.walk(os.path.join(TOOLS, "shim"))
        for name in names if name.endswith(".java")]

built = subprocess.run(
    ["javac", "-nowarn", "-cp", classpath, "-d", os.path.join(OUT, "classes")]
    + shim + [os.path.join(TOOLS, "LoadTest.java")],
    capture_output=True, text=True)
if built.returncode != 0:
    fail("the harness does not compile:\n" + built.stderr.strip()[:1600])

# NeoForge's own data pack, which the game always has. Its jar is on the
# classpath already; here it is handed over as a pack as well.
modded = [part for part in classpath.split(":") if "neoforge-" in os.path.basename(part)]

ran = None if problems else subprocess.run(
    ["java", "-Dlog4j.configurationFile=" + os.path.join(TOOLS, "log4j2.xml"),
     "-Delysium.moddedPack=" + ":".join(modded),
     "-cp", os.path.join(OUT, "classes") + ":" + classpath,
     "LoadTest", ids, str(plain)] + resources,
    capture_output=True, text=True)

output = ""
if ran is not None:
    output = "\n".join(line for line in (ran.stdout + ran.stderr).splitlines()
                       if line.strip() and not line.startswith(("\tat ", "\t... "))
                       and "JAVA_TOOL_OPTIONS" not in line and not line.startswith("SLF4J"))
    if ran.returncode != 0:
        fail("the game would not load this data:\n"
             + "\n".join("    " + line for line in output.splitlines()))

    # Several of the game's loaders report a broken file and carry on. A tag
    # that cannot load is simply empty in game, and whatever depended on it
    # quietly never matches — which is exactly the kind of bug that does not
    # crash and is never found. So every logged error is a failure here,
    # except the two the harness is known to cause.
    def explained(line):
        if "recipe_serializer]: elysium" in line:
            return True                     # our own serializer, registered by FML
        if "ingredient_serializer]: neoforge" in line or "neoforge:difference" in line:
            return True                     # NeoForge's own, likewise
        if line.startswith("ERROR RecipeManager Parsing error loading recipe"):
            return True                     # the header above one of those
        return False

    complaints = [line for line in output.splitlines()
                  if line.startswith("ERROR ") and not explained(line)]
    if complaints:
        fail("the game logged errors it would then have carried on past, which is how "
             "a tag ends up empty and nothing says so:\n"
             + "\n".join("    " + line for line in complaints[:40]))

if problems:
    print("load test          : FAILED")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

summary = next((line for line in output.splitlines() if line.startswith("loaded ")), output)
print(f"load test          : {summary}, from {len(modules)} module(s), with the game's own "
      f"codecs ({custom} recipe(s) of ours use a serializer only FML can register and are "
      f"not loaded here)")
