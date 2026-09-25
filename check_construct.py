#!/usr/bin/env python3
"""
Build every block and item we register, the way the game will.

    python3 check_construct.py

Why this exists
---------------

Every other check in this repository reads a file or runs arithmetic, and
`check_loadtest.py` runs the game's *data* loaders. None of them has ever run
our **Java**. A block's constructor is where a surprising amount of a mod
actually lives: the properties it is built from, the attribute modifiers on an
item, the block set behind a door, the switch over a kind that grew a case
nobody added, the guard that throws when two tables disagree.

Every one of those throws at the moment FML fires the registry event — four
seconds into loading, before any screen appears — so the only report is a crash
log, and getting one means asking Dylan to launch the game. That has happened
several times, and it is the slowest loop in this project by a wide margin.

How
---

A ``DeferredRegister`` keeps the suppliers it was handed in a private map until
FML asks for them. This asks for them. There is no second list of things to
build — the same suppliers, called early — so it cannot drift from what the
game does.

What it cannot do
-----------------

Anything that needs the mod constructor to have run, or another mod to exist.
A supplier reaching for a registry FML has not populated throws here and not in
the game; those are listed in ``tools/construct/skip.txt``, which is short and
visible on purpose.

And **anything behind a lazy supplier**. A spawn egg holds a
``Supplier<EntityType>`` rather than an entity type, so an egg pointing at a
mob nothing registers builds perfectly here and fails the first time a player
holds one. That is a real gap and it is worth knowing where it is rather than
assuming this catches everything: what it catches is the constructor, and a
constructor that only stores a lambda has not done the interesting part yet.
"""
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.join(HERE, "tools", "construct")
import sys as _sys
_sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "tools"))
import mcpath  # noqa: E402
CLASSPATH_FILE = os.path.join(mcpath.libs(), "cp.txt")
LIBS = os.path.join(mcpath.libs(), "libs")

LOG4J = ["log4j-api-2.19.0.jar", "log4j-core-2.19.0.jar", "log4j-slf4j2-impl-2.19.0.jar"]
EXTRA = ["asm-9.10.1.jar", "asm-analysis-9.10.1.jar", "asm-commons-9.10.1.jar",
         "asm-tree-9.10.1.jar", "asm-util-9.10.1.jar", "commons-io-2.15.1.jar",
         # The game's own assets and data (its default language file above
         # all): Bootstrap reads them off the classpath, and a classpath built
         # from a dev artifact has the classes without them. An assets-only
         # copy of the client jar, so nothing obfuscated shadows a class.
         "vanilla-assets.jar"]

#: Modules whose classes need another mod on the classpath to load at all.
#: elysium-colonies is the only one, and it is deliberately not in the merged
#: jar either.
APART = {"elysium-colonies"}


problems = []


def fail(msg):
    problems.append(msg)


def skip(why):
    print(f"construction       : skipped, {why}")
    sys.exit(0)





if not os.path.isfile(CLASSPATH_FILE):
    skip(f"no {CLASSPATH_FILE}")
missing = [n for n in LOG4J if not os.path.isfile(os.path.join(LIBS, n))]
if missing:
    skip(f"log4j is not in {LIBS}, and the game cannot start without it")

classpath = mcpath.join(mcpath.classpath(CLASSPATH_FILE))

# The same swap check_loadtest.py makes, and for the same reason: one jar was
# remapped after it was signed, so a plain JVM throws a digest error on any
# class it actually verifies. The launcher never notices.
UNSIGNED = os.path.join(LIBS, "mapped-at-unsigned.jar")
if os.path.isfile(UNSIGNED):
    classpath = os.pathsep.join(UNSIGNED if part.endswith("mapped-at.jar") else part
                                for part in classpath.split(os.pathsep))
for name in LOG4J + EXTRA:
    jar = os.path.join(LIBS, name)
    if os.path.isfile(jar):
        classpath += os.pathsep + jar

# The same two additions compile.sh makes: the annotations jar every module
# imports @Nullable from, and the compile-only jars the gear bridge is written
# against. Without them this check reports "the modules do not compile", which
# is true of nothing but this file's classpath.
ANNOTATIONS = os.environ.get("ELYSIUM_ANNOTATIONS",
                             "/opt/gradle-8.14.3/lib/annotations-24.0.1.jar")
if os.path.isfile(ANNOTATIONS):
    classpath += os.pathsep + mcpath.native(ANNOTATIONS)

import glob as _glob
COMPAT = os.environ.get("ELYSIUM_COMPAT_LIBS", "/tmp/mods")
for pattern in ("silent-gear-*.jar", "silent-lib-*.jar", "Apotheosis-*.jar",
                "Placebo-*.jar", "minecolonies-*.jar", "structurize-*.jar",
                "blockui-*.jar"):
    found = sorted(_glob.glob(os.path.join(COMPAT, pattern)))
    if found:
        classpath += os.pathsep + found[0]

# Every registry class in the tree, found rather than listed: a module that
# adds a registry class and is not named here would be a module nothing builds.
REGISTRY = re.compile(r"DeferredRegister")
classes = []
modules = []
for module in sorted(os.listdir(HERE)):
    if not module.startswith("elysium-") or module in APART:
        continue
    root = os.path.join(module, "src/main/java")
    if not os.path.isdir(os.path.join(HERE, root)):
        continue
    modules.append(module)
    for base, _, names in os.walk(os.path.join(HERE, root)):
        for name in names:
            if not name.endswith(".java"):
                continue
            path = os.path.join(base, name)
            with open(path, encoding="utf-8") as handle:
                body = handle.read()
            if not REGISTRY.search(body):
                continue
            rel = os.path.relpath(path, os.path.join(HERE, root))
            classes.append(rel[:-5].replace(os.sep, "."))

if not classes:
    skip("no registry classes found, which cannot be right")

sources = []
for module in modules:
    root = os.path.join(HERE, module, "src/main/java")
    for base, _, names in os.walk(root):
        for name in names:
            if name.endswith(".java"):
                sources.append(os.path.join(base, name))

with tempfile.TemporaryDirectory() as tmp:
    out = os.path.join(tmp, "out")
    os.makedirs(out)
    listing = os.path.join(tmp, "sources.txt")
    with open(listing, "w", encoding="utf-8") as handle:
        handle.write("\n".join(sources))
    # The same stand-in check_loadtest.py uses: NeoForge's feature-flag loader
    # asks FML which mods are loaded, and nothing here is loaded. It is shared
    # rather than copied, so there is one stand-in and not two.
    shim = [os.path.join(base, name)
            for base, _, names in os.walk(os.path.join(HERE, "tools/loadtest/shim"))
            for name in names if name.endswith(".java")]
    built = subprocess.run(
        ["javac", "-nowarn", "-proc:none", "-cp", classpath, "-d", out,
         os.path.join(TOOLS, "Construct.java")] + shim + ["@" + listing],
        capture_output=True, text=True)
    ran = None
    if built.returncode != 0:
        fail("the modules do not compile:\n" + built.stderr.strip()[:1500])

    names = os.path.join(tmp, "classes.txt")
    if not problems:
        with open(names, "w", encoding="utf-8") as handle:
            handle.write("\n".join(sorted(set(classes))))
        skips = os.path.join(TOOLS, "skip.txt")
        command = ["java",
                   "-Dlog4j.configurationFile=" + os.path.join(TOOLS, "log4j2.xml"),
                   "-cp", out + os.pathsep + classpath, "Construct", names]
        # `out` is first, so the stand-in wins over the real loader.
        if os.path.isfile(skips):
            command.append(skips)
        try:
            ran = subprocess.run(command, capture_output=True, text=True, timeout=600)
        except subprocess.TimeoutExpired:
            fail("building our blocks and items did not finish in ten minutes")

counts = {}
if ran is not None:
    broken = [line[len("BROKEN "):] for line in ran.stdout.splitlines()
              if line.startswith("BROKEN ")]
    counts = {line.split()[0]: line.split()[1] for line in ran.stdout.splitlines()
              if line.startswith(("REGISTERS ", "BUILT "))}
    if ran.returncode != 0 and not broken:
        fail("the harness itself would not run:\n"
             + (ran.stdout + ran.stderr).strip()[-1500:])
    for one in broken:
        fail(one)

if problems:
    print("construction       : FAILED")
    for problem in problems[:20]:
        print("  - " + problem)
    if len(problems) > 20:
        print(f"  - ...and {len(problems) - 20} more")
    sys.exit(1)

print(f"construction       : {counts.get('BUILT', '?')} block(s), item(s) and type(s) "
      f"built from {counts.get('REGISTERS', '?')} register(s) across {len(modules)} "
      f"module(s), the way FML will build them")
