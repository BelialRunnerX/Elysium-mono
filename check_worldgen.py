#!/usr/bin/env python3
"""
Every world this repository ships, and what each biome in it actually gets.

    python3 check_worldgen.py

The bug this exists for
-----------------------

The game does not pick a biome from a weighted list. Every biome claims a box
in a six-dimensional climate space, and each column of the world goes to
whichever box is *nearest*. Which means a biome can be declared, registered,
translated, listed in every compass and wiki in the pack, and never generate
anywhere at all — because some other box is nearer to every climate the noise
ever produces.

Nothing reports that. It is not a crash and not a warning: the file is valid,
the id resolves, the biome is genuinely there. It simply has no territory. The
only way anybody finds out is a player spending an afternoon flying and
concluding they were unlucky.

**This repository shipped one.** `elysiumspace:emerald_bastion_river` claimed a
weirdness band inside `emerald_bastion_plains`' claim, on a world where plains
was declared first — so every climate the river could have won was a tie, and
the game breaks ties by declaration order. It never generated. It was found by
this arithmetic and by nothing else.

How much is too much
--------------------

The other half of the question has no universal answer. A frozen moon ought to
be mostly frozen desert; an overworld ought not to be mostly anything. So each
world below states the largest share any one of its biomes may hold, and this
proves the world that generates is the world it claimed to be. A world asking
for a high ceiling is making a claim somebody can argue with in review, which
is the most that can honestly be demanded of it.

A world with no row here is an error rather than a pass, for the same reason
the conversion manifest works that way: the whole point is that nothing is
quietly left out.
"""
import glob
import json
import os
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.abspath(__file__))
RULES = os.path.join(ROOT, "elysium-wilds/src/main/java/com/elysium/wilds/rules/"
                           "WildRules.java")
AXES = ["temperature", "humidity", "continentalness", "erosion", "depth", "weirdness"]

#: What each world is meant to look like: the largest share one of its biomes
#: may hold. These are design claims, not measurements — argue with them here.
WORLDS = {
    # Three biomes, and it is a frozen world: being mostly frozen desert is
    # the point of it rather than a flaw in it.
    "frostfall": (0.80, "a frozen world, and mostly the frozen part of it"),
    # A dead moon: two biomes, surface and cave, roughly even.
    "pale_moon": (0.65, "surface and caves, and neither is the whole moon"),
    # Ash and fire, two biomes, roughly even.
    "cinderveil": (0.65, "barrens and desert, neither of them the whole world"),
    # The one that had the dead river in it. Plains dominate on purpose - it
    # is the habitable world - but the highlands and the river have to be
    # somewhere a player actually walks into.
    "emerald_bastion": (0.70, "a habitable world: plains, with highlands and rivers "
                              "through them"),
    # Fifteen biomes on a partition of the climate plane: five bands of warmth
    # crossed with three of rain, every band running edge to edge from where
    # the last one stopped. Fifteen equal rectangles is one fifteenth apiece,
    # so a ceiling anywhere near a third means the bands are not what they say
    # they are -- which is exactly the kind of claim this file exists to make
    # somebody argue with in review.
    "vale": (0.30, "fifteen biomes on an even grid of warmth and rain, none of "
                   "them more than a third of it"),
}

problems = []


def fail(msg):
    problems.append(msg)


def read_worlds():
    found = {}
    for path in sorted(glob.glob(os.path.join(
            ROOT, "*/src/main/resources/data/*/dimension/*.json"))):
        try:
            body = json.load(open(path, encoding="utf-8"))
        except (OSError, ValueError) as broken:
            fail(f"{os.path.relpath(path, ROOT)} is not readable: {broken}")
            continue
        source = body.get("generator", {}).get("biome_source", {})
        if source.get("type") != "minecraft:multi_noise":
            continue
        name = os.path.basename(path)[:-5]
        rows = []
        for index, entry in enumerate(source.get("biomes", [])):
            given = entry.get("parameters", {})
            spans = []
            for axis in AXES:
                value = given.get(axis, 0)
                if isinstance(value, list):
                    if len(value) != 2:
                        fail(f"{name}: biome {index} gives {axis} as a list of "
                             f"{len(value)}, and a span is two numbers")
                        value = [0, 0]
                    spans.append((float(value[0]), float(value[1])))
                else:
                    spans.append((float(value), float(value)))
            rows.append((entry.get("biome", f"?{index}"), spans,
                         float(given.get("offset", 0))))
        found[name] = (os.path.relpath(path, ROOT), rows)
    return found


worlds = read_worlds()

for name in sorted(worlds):
    if name not in WORLDS:
        fail(f"{name} is a multi-noise world with no row in check_worldgen.py; add one "
             f"saying how much of itself one of its biomes may be, or it is a world "
             f"nothing has ever looked at")
for name in sorted(WORLDS):
    if name not in worlds:
        fail(f"check_worldgen.py has a row for {name} and there is no such world, so "
             f"the row is describing something that no longer exists")

checked = {name: rows for name, rows in worlds.items() if name in WORLDS}

if not checked:
    print("worldgen           : no multi-noise worlds to check")
    sys.exit(1 if problems else 0)

# ---------------------------------------------------------------------------
# Asked of the rules rather than worked out again here
# ---------------------------------------------------------------------------
#
# The nearest-box arithmetic lives in WildRules and is swept by its harness.
# Reimplementing it here in Python would be a second copy of the one thing in
# this file that has to be exactly the game's.

probe = ["package com.elysium.wilds.rules;", "import java.util.*;",
         "public final class Probe {", "  public static void main(String[] a) {"]
for name in sorted(checked):
    _, rows = checked[name]
    ceiling, _note = WORLDS[name]
    probe.append("    {")
    probe.append("      List<WildRules.Region> rs = new ArrayList<>();")
    for biome, spans, offset in rows:
        made = ", ".join(f"new WildRules.Span({low}D, {high}D)" for low, high in spans)
        probe.append(f'      rs.add(new WildRules.Region("{biome}", '
                     f'List.of({made}), {offset}D));')
    probe.append(f'      System.out.println("WORLD {name} " + rs.size() + " " '
                 f'+ WildRules.biomes(rs).size());')
    probe.append("      Map<String, Double> held = WildRules.shares(rs, WildRules.SAMPLES);")
    probe.append("      for (String b : WildRules.biomes(rs)) {")
    probe.append(f'        System.out.println("SHARE {name} " + b + " " '
                 f'+ held.getOrDefault(b, 0.0D) + " " + WildRules.walkTo('
                 f'held.getOrDefault(b, 0.0D)));')
    probe.append("      }")
    for kind, call in (("SWALLOWED", "swallowed(rs)"), ("DEAD", "unreachable(rs)"),
                       ("SKEW", f"lopsided(rs, {ceiling}D)"), ("FAR", "tooFar(rs)")):
        probe.append(f'      for (String s : WildRules.{call}) '
                     f'System.out.println("{kind} {name} " + s);')
    probe.append("    }")
probe += ["  }", "}"]

with tempfile.TemporaryDirectory() as tmp:
    source = os.path.join(tmp, "Probe.java")
    open(source, "w", encoding="utf-8").write("\n".join(probe))
    built = subprocess.run(["javac", "-nowarn", "-d", os.path.join(tmp, "out"),
                            RULES, source], capture_output=True, text=True)
    if built.returncode != 0:
        print("worldgen           : FAILED")
        print("  - the worldgen rules do not compile against the JDK alone:")
        print(built.stderr.strip()[:1500])
        sys.exit(1)
    try:
        ran = subprocess.run(["java", "-cp", os.path.join(tmp, "out"),
                              "com.elysium.wilds.rules.Probe"],
                             capture_output=True, text=True, timeout=300)
    except subprocess.TimeoutExpired:
        print("worldgen           : FAILED")
        print("  - measuring the worlds did not finish in five minutes")
        sys.exit(1)
    if ran.returncode != 0:
        print("worldgen           : FAILED")
        print("  - measuring the worlds threw:\n" + ran.stderr.strip()[:1500])
        sys.exit(1)
    said = ran.stdout

biome_count = 0
narrowest = 1.0
furthest = 0.0
for line in said.splitlines():
    parts = line.split(" ", 2)
    if len(parts) < 2:
        continue
    kind, world = parts[0], parts[1]
    rest = parts[2] if len(parts) > 2 else ""
    if kind == "WORLD":
        biome_count += int(rest.split()[1])
    elif kind == "SHARE":
        biome, share, walk = rest.rsplit(" ", 2)
        narrowest = min(narrowest, float(share))
        furthest = max(furthest, min(float(walk), 1.0e9))
    elif kind == "SWALLOWED":
        fail(f"{world}: {rest}")
    elif kind == "DEAD":
        fail(f"{world}: {rest}")
    elif kind == "SKEW":
        fail(f"{world}: {rest}")
    elif kind == "FAR":
        fail(f"{world}: {rest}")

# A surface water lake, anywhere. `minecraft:lake` is vanilla's deprecated
# LakeFeature, and for a WATER lake it ends with an ice pass that asks the
# biome under every column of the lake; a lake placed near a chunk's edge asks
# past the generation region and the chunk fails with "Requested chunk
# unavailable during world generation" — which in a singleplayer world is the
# integrated server crashing (the Emerald Bastion, 28 Sep 2026). Vanilla only
# ever fills the feature with lava, which skips that pass; any other fluid is
# fine too. Water is the one that is not.
water_lakes = 0
for module in sorted(os.listdir(ROOT)):
    root = os.path.join(ROOT, module, "src/main/resources/data")
    if not module.startswith("elysium-") or not os.path.isdir(root):
        continue
    for path in sorted(glob.glob(os.path.join(root, "*", "worldgen", "configured_feature", "**", "*.json"), recursive=True)):
        try:
            feature = json.load(open(path, encoding="utf-8"))
        except (OSError, ValueError):
            continue
        if feature.get("type") != "minecraft:lake":
            continue
        fluid = feature.get("config", {}).get("fluid", {})
        state = fluid.get("state", {}) if isinstance(fluid, dict) else {}
        name = state.get("Name", "") if isinstance(state, dict) else ""
        if name == "minecraft:water":
            water_lakes += 1
            fail(f"{os.path.relpath(path, ROOT)}: a minecraft:lake filled with water, whose ice pass reads biomes "
                 f"past the generation region and crashes the chunk near an edge; give it another fluid or "
                 f"let the rivers carry the water")

if problems:
    print("worldgen           : FAILED")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print(f"worldgen           : {len(checked)} multi-noise world(s), {biome_count} biome(s) "
      f"— every one of them generating somewhere, the rarest at "
      f"{narrowest * 100:.1f}% of its world and the furthest about "
      f"{round(furthest):,} blocks off")
