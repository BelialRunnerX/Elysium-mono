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
    ("elysium-npcs", "the court: five envoys, their regalia and their standing gates"),
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
