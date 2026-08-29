#!/usr/bin/env python3
"""
Check the library's own invariants.

Run from the repo root:   python3 validate.py

The library ships no models, no recipes and no loot tables, so none of core's
resource cross-checks apply. What it does have is one property worth more than
all of them:

    the library must not know about any mod built on it.

That is the whole reason this repo is separate. A single import of
com.elysium.core would compile fine here — core is not on the classpath, so it
would not even compile — but the equivalent mistakes that *do* compile are easy
to make: naming a content mod in the manifest, hard-coding an item id, checking
for a specific mod being loaded. Each of those is checked below.

The rest is arithmetic the code documents about itself and cannot verify at
runtime: the element ring is closed, the stats are the twelve they claim to be,
and every translation key the engine emits has an entry to emit.
"""
import json
import os
import pathlib
import re
import sys

WORK = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(WORK, "src/main/java/com/elysium/lib")
RES = os.path.join(WORK, "src/main/resources")

problems = []


def fail(msg):
    problems.append(msg)


def read(rel):
    return open(os.path.join(SRC, rel), encoding="utf-8").read()


# Comments and string-literal *documentation* are not code. A javadoc example
# showing an add-on how a race id is stored ("elysium:imperial") is the library
# doing its job, not the library depending on a content mod — so the checks
# below read code with the comments stripped.
COMMENT = re.compile(r"//[^\n]*|/\*.*?\*/", re.S)


def sources(strip_comments=True):
    for dirpath, _, filenames in os.walk(SRC):
        for filename in sorted(filenames):
            if filename.endswith(".java"):
                path = os.path.join(dirpath, filename)
                text = open(path, encoding="utf-8").read()
                if strip_comments:
                    text = COMMENT.sub(" ", text)
                yield os.path.relpath(path, SRC), text


if not os.path.isdir(SRC):
    print("This is not the elysium-lib tree: src/main/java/com/elysium/lib is missing.")
    sys.exit(1)

# ---------------------------------------------------------------------------
# 1. The library knows about no mod built on it
# ---------------------------------------------------------------------------
#
# Checked first because everything else is worthless if this is false. A
# library that reaches into a content mod is not a library; it is one mod in
# two jars, and the second Elysium mod will discover that the hard way.

for path, text in sources():
    for hit in re.findall(r"com\.elysium\.(?!lib\b)\w+", text):
        fail(f"{path}: library source refers to {hit} - the library must not "
             f"know about any mod built on it")

    # An id in the elysium namespace is a content mod's id. The two exceptions
    # are the translation-key prefixes, which are deliberately shared so that a
    # player sees "Vitality" and not "elysiumlib.stat.vitality" — those are
    # string prefixes, not resource ids.
    for hit in re.findall(r'"elysium:([a-z_/]+)"', text):
        fail(f"{path}: hard-coded content id \"elysium:{hit}\" - the library "
             f"ships no items and must not name any")

    for hit in re.findall(r'isLoaded\("(\w+)"\)', text):
        fail(f"{path}: library checks for mod \"{hit}\" being loaded - "
             f"integrations belong to the mod that wants them")

print(f"library sources    : {sum(1 for _ in sources())} files, no content references")

# ---------------------------------------------------------------------------
# 2. The manifest depends on nothing but the platform
# ---------------------------------------------------------------------------
manifest = open(os.path.join(RES, "META-INF/neoforge.mods.toml"), encoding="utf-8").read()
declared = re.findall(r'modId="([\w$\{\}]+)"', manifest)
allowed = {"${mod_id}", "neoforge", "minecraft"}
for mod in declared:
    if mod not in allowed:
        fail(f"manifest declares a dependency on \"{mod}\" - the library "
             f"depends on the platform and nothing else")

# ---------------------------------------------------------------------------
# 3. The element ring is closed
# ---------------------------------------------------------------------------
#
# Five elements, each beating exactly two and beaten by exactly two. This was
# ordinal arithmetic on an enum and is now a declared graph, which is the whole
# point — and also exactly the change that could silently produce a lopsided
# ring nobody notices until a fight feels wrong.

elements_src = read("element/ElysiumElements.java")
# Each element is registered against a shared *_ID constant, so read the
# constants first and then the ring in terms of them. Reading the constants
# rather than assuming the names match is what catches the copy-paste where
# KINETIC is registered against DIMENSIONAL_ID.
ids = dict(re.findall(
    r"ResourceLocation (\w+)_ID = id\(\"(\w+)\"\)", elements_src))

beats = {}
for constant, targets in re.findall(
        r"ElysiumElement\.register\(\s*(\w+)_ID,.*?Set\.of\((.*?)\)\s*,",
        elements_src, re.S):
    name = ids.get(constant, constant.lower())
    beats[name] = {ids.get(t, t.lower())
                   for t in re.findall(r"(\w+)_ID", targets)}

if len(beats) != 5:
    fail(f"expected 5 canonical elements, read {len(beats)}: {sorted(beats)} - "
         f"the ring check cannot read ElysiumElements")
else:
    for name, targets in beats.items():
        if len(targets) != 2:
            fail(f"{name} beats {len(targets)} elements, not 2: {sorted(targets)}")
        if name in targets:
            fail(f"{name} beats itself")
        for target in targets:
            if target not in beats:
                fail(f"{name} beats \"{target}\", which is not a canonical element")
            elif name in beats.get(target, ()):
                fail(f"{name} and {target} both beat each other - "
                     f"the advantage would cancel and neither would matter")
    beaten = {name: sum(1 for t in beats.values() if name in t) for name in beats}
    lopsided = {n: c for n, c in beaten.items() if c != 2}
    if lopsided:
        fail(f"elements are not beaten by exactly two others: {lopsided} - "
             f"the ring is lopsided and some elements are strictly better")
    if not problems:
        print(f"element ring       : {len(beats)} elements, each beats 2 and is beaten by 2")

# ---------------------------------------------------------------------------
# 4. The stats are the twelve they claim to be
# ---------------------------------------------------------------------------
stats_src = read("stats/ElysiumStats.java")
stat_ids = re.findall(r"ElysiumStat\.(?:flat|curve)\(id\(\"(\w+)\"\)", stats_src)
if len(stat_ids) != 12:
    fail(f"expected 12 canonical stats, read {len(stat_ids)}: {sorted(stat_ids)}")
if len(set(stat_ids)) != len(stat_ids):
    duplicates = sorted({s for s in stat_ids if stat_ids.count(s) > 1})
    fail(f"duplicate stat ids: {duplicates}")

# A curved stat's halfway point must be positive, or the curve divides by zero
# at the origin and every value comes out as 1.0 — full effect for free.
for name, halfway in re.findall(
        r"ElysiumStat\.curve\(id\(\"(\w+)\"\),\s*[\w.]+,\s*([\d.]+)F", stats_src):
    if float(halfway) <= 0.0:
        fail(f"stat {name} has a halfway point of {halfway} - a curve needs a "
             f"positive one or it returns full effect at zero points")

if len(stat_ids) == 12 and len(set(stat_ids)) == 12:
    print(f"stats registered   : {len(stat_ids)}")

# ---------------------------------------------------------------------------
# 5. Every key the engine emits has something to emit
# ---------------------------------------------------------------------------
lang_path = os.path.join(RES, "assets/elysiumlib/lang/en_us.json")
lang = json.load(open(lang_path, encoding="utf-8"))

for name in stat_ids:
    for suffix in ("", ".desc"):
        key = f"elysium.stat.{name}{suffix}"
        if key not in lang:
            fail(f"missing lang key: {key}")

for name in beats:
    key = f"elysium.element.{name}"
    if key not in lang:
        fail(f"missing lang key: {key}")

# Literal keys, for everything not built by concatenation.
literal = set()
for _path, text in sources():
    literal |= set(re.findall(r'"(elysium\.[a-z_.]+)"', text))
for key in sorted(literal):
    if key not in lang and not key.endswith("."):
        fail(f"missing lang key: {key} (referenced literally in the sources)")

print(f"lang keys          : {len(lang)}")

# ---------------------------------------------------------------------------
# 6. All JSON parses
# ---------------------------------------------------------------------------
count = 0
for dirpath, _, filenames in os.walk(RES):
    for filename in filenames:
        if filename.endswith(".json"):
            count += 1
            try:
                json.load(open(os.path.join(dirpath, filename), encoding="utf-8"))
            except Exception as exc:
                fail(f"invalid JSON {os.path.join(dirpath, filename)}: {exc}")
print(f"json files parsed  : {count}")

# ---------------------------------------------------------------------------
# 7. The generated palette is the palette that was designed against
# ---------------------------------------------------------------------------
#
# ElysiumPalette.java is generated by ui/palette.py, and the mockups that the
# interface was reviewed and approved from import that same file. If someone
# edits the Java by hand, the game and the previews part company silently —
# every screenshot that was signed off is then a picture of a build that no
# longer exists. So the file is regenerated in memory and compared.
palette_tool = os.path.join(WORK, "ui", "palette.py")
palette_java = os.path.join(
    WORK, "src/main/java/com/elysium/lib/client/ElysiumPalette.java")
if os.path.exists(palette_tool) and os.path.exists(palette_java):
    import importlib.util
    import tempfile

    spec = importlib.util.spec_from_file_location("elysium_palette", palette_tool)
    palette_module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(palette_module)

    for name, surface, ratio, floor in palette_module.check_contrast():
        fail(f"contrast: {name} on {surface} is {ratio}:1, below {floor}")

    with tempfile.TemporaryDirectory() as tmp:
        regenerated = pathlib.Path(tmp) / "ElysiumPalette.java"
        palette_module.write_java(regenerated)
        current = regenerated.read_text() == open(palette_java, encoding="utf-8").read()
    if not current:
        fail("ElysiumPalette.java does not match ui/palette.py — "
             "run `python3 ui/palette.py` (never edit the Java by hand)")
    else:
        print(f"palette            : {len(palette_module.PALETTE)} colours, "
              f"{len(palette_module.METRICS)} metrics, generated file is current")
else:
    fail("ui/palette.py or the generated ElysiumPalette.java is missing")

print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all library checks passed")
