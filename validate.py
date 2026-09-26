#!/usr/bin/env python3
"""
Run every mod's validator, from the root of the monorepo.

    python3 validate.py

Each of the six mods owns its own checks and keeps its own validate.py; this
only runs them in order and reports. It deliberately does not merge them into
one file — each validator knows about exactly one mod's resources, and the
whole reason the library is a separate build is that it must not know what is
built on top of it.

The mods are run library-first. A library problem usually shows up again as a
symptom in all three content mods, and reading it once at the top is better than
finding it four times.

Exits non-zero if any validator does, so CI can gate on this before spending
twenty minutes on Gradle.
"""
import pathlib
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent

MODS = [
    ("elysium-lib", "the engine — and that it knows nothing of what is built on it"),
    ("elysium-core", "gear, materials, races, classes, recipes, obtainability"),
    ("elysium-dungeons", "rooms, blocks, items and the dimension's JSON"),
    ("elysium-mobs", "entity types, models, renderers, attributes, both factions"),
    ("elysium-trinkets", "forty accessories, their slots, and how each is obtained"),
    ("elysium-solarpunk", "the solarpunk set, and which of it still has to be made to work"),
    ("elysium-npcs", "the court: five envoys, their regalia and their standing gates"),
    ("elysium-armory", "the part-built gear engine, and that it owes Silent Gear nothing"),
    ("elysium-ascendancy", "the loot and affix engine, and that it owes Apotheosis nothing"),
    ("elysium-enchanting", "the enchanting table you build power around, and that it owes\n     Apothic Enchanting nothing"),
    ("elysium-spawners", "the spawner you can pick up and tune, and that it owes\n     Apothic Spawners nothing"),
    ("elysium-space", "worlds, their air and hazards, and that it needs no Stellaris library"),
    ("elysium-ores", "one item per material, and one ore per material in every stone"),
    ("elysium-flora", "string and charcoal you can farm, on the slower road"),
    ("elysium-kinetics", "the first age, and the spine the other three hang off"),
    ("elysium-industry", "the second age, and the one that makes electricity worth it"),
    ("elysium-atomics", "the third age, and the first one that can keep what it makes"),
    ("elysium-logistics", "where everything goes, and the proof that none of it goes missing"),
    ("elysium-agronomy", "things that grow, and the proof that a farm loses to a mine"),
    ("elysium-foundry", "the material chain, and the proof that none of it leaks"),
    ("elysium-almanac", "the book, and the proof a player can get to the end of it"),
    ("elysium-transit", "getting there, and the proof the network cannot be arbitraged"),
    ("elysium-arcana", "turning things into other things, and the proof it never makes\n     something out of nothing"),
    ("elysium-comforts", "the small mercies, and the proof that nothing you owned is\n     ever lost"),
    ("elysium-atelier", "the shapes a house is made of, and the proof that none of them\n     makes wood out of nothing"),
    ("elysium-arsenal", "the tools that take a wall at a time, and the proof that not one\n     of them is simply better"),
    ("elysium-bestiary", "nine things that hunt you, and the proof that none of them is\n     the only one worth killing"),
    ("elysium-wilds", "the game's own biome placement, so a biome that never generates\n     is a build failure rather than an afternoon"),
    ("elysium-colonies", "one seam onto MineColonies, and three readings of a town"),
]


def main():
    failures = []

    # Cross-cutting first: it reads every mod at once, and the rule it enforces
    # is one no compiler and no per-mod validator can see. Two launches of this
    # project died to lifecycle bugs before it existed.
    print("=" * 70)
    print("lifecycle — nothing resolved before the registries exist")
    print("=" * 70)
    if subprocess.run([sys.executable, "check_lifecycle.py"], cwd=HERE).returncode != 0:
        failures.append("lifecycle")
    print()

    # Also cross-cutting, and also invisible to a compiler: an item that builds
    # its attributes with the inner half of the helper takes its runes and
    # nothing from ascension, which reads in game as ascension being broken.
    print("=" * 70)
    print("gear — every socketable item takes runes and ascension")
    print("=" * 70)
    if subprocess.run([sys.executable, "check_gear.py"], cwd=HERE).returncode != 0:
        failures.append("gear")
    print()

    # The conversion programme's own bookkeeping. It is checked here because
    # the programme runs for longer than anybody's memory of it: a mod that
    # nobody decided about is a mod nobody converts, and that failure is
    # silent.
    print("=" * 70)
    print("manifest — every mod in the pack has a decision, and it has not drifted")
    print("=" * 70)
    if subprocess.run([sys.executable, "check_manifest.py"], cwd=HERE).returncode != 0:
        failures.append("manifest")
    print()

    # Cross-cutting, and about a tool rather than a mod: the tech reskin is a
    # generator that runs on somebody else's machine against somebody else's
    # art, so the only things that can be checked here are the promises it
    # makes — recolour and never reshape, every colour from the palette, and
    # none of their assets anywhere in this repository.
    print("=" * 70)
    print("retexture — recoloured, never reshaped, and nothing of theirs here")
    print("=" * 70)
    if subprocess.run([sys.executable, "check_retexture.py"], cwd=HERE).returncode != 0:
        failures.append("retexture")
    print()

    # Beside the retexture check, because the two are halves of one promise.
    # That one holds the art to "ours, not theirs" and has done from the
    # start; this one holds the names, and did not exist until 26 September,
    # by which time 134 of the bestiary's 176 creature ids were the names of
    # the mods they had been converted from. Every other check passed the
    # whole time. See docs/NAMING.md.
    print("=" * 70)
    print("names — ours, and not a coinage anybody else made up")
    print("=" * 70)
    if subprocess.run([sys.executable, "check_names.py"], cwd=HERE).returncode != 0:
        failures.append("names")
    print()

    # First, because it decides whether anything below can be believed. A
    # module validator whose report block is not the last thing in the file
    # runs its later checks into a list nobody prints, and then announces
    # success. One of them was doing exactly that.
    print("=" * 70)
    print("validators — each one can actually report what it finds")
    print("=" * 70)
    if subprocess.run([sys.executable, "check_validators.py"], cwd=HERE).returncode != 0:
        failures.append("validators")
    print()

    # Cross-cutting, and invisible to every per-module validator: they check
    # that a translation key exists, which is a different question from whether
    # it is given the arguments its string asks for. A mismatch renders as a
    # raw %s or as a value in the wrong sentence, and nothing short of
    # triggering the message in game shows it.
    print("=" * 70)
    print("messages — every translated string gets the arguments it asks for")
    print("=" * 70)
    if subprocess.run([sys.executable, "check_lang.py"], cwd=HERE).returncode != 0:
        failures.append("messages")
    print()

    # The only check here that runs the game. It boots the registries outside
    # the launcher and loads our data with the real codecs, which is what
    # happens when a player presses Create World — and was, until today, the
    # only way to find out whether our data files were right.
    print("=" * 70)
    print("load test — the game's own loader, on our data, here")
    print("=" * 70)
    if subprocess.run([sys.executable, "check_loadtest.py"], cwd=HERE).returncode != 0:
        failures.append("load test")
    print()

    # Cross-cutting, and about the checks themselves rather than the code: a
    # validator rule that names a *call* goes on passing when somebody writes
    # `if (false) theCall();` in front of it. That has happened fourteen times
    # here, every one found by a negative test rather than by reading, so the
    # fix is now a check with a ratchet under it.
    print("=" * 70)
    print("rules — no validator names half a statement and calls it a check")
    print("=" * 70)
    if subprocess.run([sys.executable, "check_rules.py"], cwd=HERE).returncode != 0:
        failures.append("validator rules")
    print()

    # Cross-cutting, and the only check here that runs our own Java. A
    # block's constructor is where a surprising amount of a mod lives, and
    # every mistake in one throws at the moment FML fires the registry event
    # — four seconds into loading, before any screen — so the only report is
    # a crash log somebody has to go and fetch. The first run of this found
    # a stone door built on a block set that opens by hand.
    print("=" * 70)
    print("construction — every block and item we register can actually be built")
    print("=" * 70)
    if subprocess.run([sys.executable, "check_construct.py"], cwd=HERE).returncode != 0:
        failures.append("construction")
    print()

    # Cross-cutting, and about the worlds rather than the code: the game picks
    # a biome by which of their claims on a six-dimensional climate space is
    # nearest, so a biome whose claim sits inside an earlier biome's ties
    # everywhere it could win, loses every tie, and never generates at all.
    # This repository shipped one - a river - and nothing reported it, because
    # nothing was wrong: the file was valid and the id resolved.
    print("=" * 70)
    print("worldgen — every biome we ship actually generates somewhere")
    print("=" * 70)
    if subprocess.run([sys.executable, "check_worldgen.py"], cwd=HERE).returncode != 0:
        failures.append("worldgen")
    print()

    # Cross-cutting, and also read out of the jars: the game says which of its
    # getters may return null, javac does not care, and a chain off one of them
    # is a world that stops where it is. One did, at 51%.
    print("=" * 70)
    print("nullable — nothing chains off a getter the jars say may be null")
    print("=" * 70)
    if subprocess.run([sys.executable, "check_nullable.py"], cwd=HERE).returncode != 0:
        failures.append("nullable")
    print()

    # Cross-cutting, and the one check that reads the game's codecs rather
    # than our own conventions. Four data files shipped that were valid JSON,
    # named files that exist, and blew up when a player pressed Create World.
    print("=" * 70)
    print("datapack — our data against the codecs the game will read it with")
    print("=" * 70)
    if subprocess.run([sys.executable, "check_datapack.py"], cwd=HERE).returncode != 0:
        failures.append("datapack")
    print()

    # Cross-cutting because it is about a shape, not about any module's
    # subject: a method whose whole body is a call to itself. It compiles, it
    # passes every validator, and it takes the game down the first time
    # anything reaches it. One shipped jar did exactly that.
    print("=" * 70)
    print("recursion — no method calls itself with nothing changed")
    print("=" * 70)
    if subprocess.run([sys.executable, "check_recursion.py"], cwd=HERE).returncode != 0:
        failures.append("recursion")
    print()

    # Cross-cutting, and the only check here that reads the game's own jars.
    # A listener on the wrong event bus compiles, validates, merges and then
    # crashes the game before the title screen. We shipped exactly that, and
    # the first machine to run the jar found it in four seconds.
    print("=" * 70)
    print("buses — every listener on the bus its event is actually fired on")
    print("=" * 70)
    if subprocess.run([sys.executable, "check_buses.py"], cwd=HERE).returncode != 0:
        failures.append("buses")
    print()

    # Cross-cutting because a theme is the one property that no module can
    # hold on its own: a palette that fifteen screens follow and one does not
    # is not a palette. The one that does not is always the newest, always a
    # placeholder, and always carries a javadoc note promising to be fixed.
    print("=" * 70)
    print("interface — one kit, every screen, every module")
    print("=" * 70)
    if subprocess.run([sys.executable, "check_ui.py"], cwd=HERE).returncode != 0:
        failures.append("interface")
    print()

    # Also cross-cutting: the six mods ship as six jars and also as one, and a
    # resource path two of them both write is a file the merged jar can quietly
    # lose. tools/mergejar.py refuses at packaging time; this asks the same
    # question at source level, before the build, where the answer is useful to
    # whoever added the file.
    print("=" * 70)
    print("packaging — the six modules can still be merged into one jar")
    print("=" * 70)
    if subprocess.run([sys.executable, "check_merge.py"], cwd=HERE).returncode != 0:
        failures.append("packaging")
    print()

    for name, blurb in MODS:
        directory = HERE / name
        script = directory / "validate.py"
        print("=" * 70)
        print(f"{name} — {blurb}")
        print("=" * 70)

        if not script.exists():
            print(f"  no validate.py in {name}\n")
            failures.append(name)
            continue

        # Run from inside the mod's own directory: each validator resolves its
        # paths relative to itself, and being run from elsewhere is the sort of
        # thing that makes a check quietly pass by finding nothing.
        result = subprocess.run([sys.executable, "validate.py"], cwd=directory)
        if result.returncode != 0:
            failures.append(name)
        print()

    print("=" * 70)
    if failures:
        print("FAILED: " + ", ".join(failures))
        return 1
    print(f"cross-cutting checks and all {len(MODS)} mod validators passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
