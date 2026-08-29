#!/usr/bin/env python3
"""
Every piece of Elysium gear takes the whole Elysium treatment.

    python3 check_gear.py

---------------------------------------------------------------------------
The rule
---------------------------------------------------------------------------

An item that implements ElysiumSocketable and overrides
getDefaultAttributeModifiers must build them with elysiumModifiers(...), not
with applyRunes(...).

The two differ by exactly one thing: elysiumModifiers also adds the ascension
bonuses — the tier's share of armour, toughness and attack damage. Both compile.
Both run. An item that calls the wrong one takes runes correctly, socketable
correctly, ascends its tier correctly, shows the higher tier on its tooltip, and
does not gain a single point of anything for it.

That is the failure this checks for, and it is worth a checker because of how it
reads in game: ascension appears to work. The rarity changes, the tier line
changes, the socket count goes up. Only the numbers the player ascended *for*
stay where they were, and nothing in any log says why.

---------------------------------------------------------------------------
Why it cannot be a compiler's job
---------------------------------------------------------------------------

applyRunes is a legitimate public method on the interface — elysiumModifiers is
written in terms of it. Deleting it to force the issue would leave the library
unable to express its own composition. So the distinction is a convention, and a
convention that a new item silently half-satisfies is exactly the kind worth
spending twenty lines of checker on.

Six gear classes called applyRunes directly before ascension existed. All six
were correct at the time and all six would have been wrong the moment ascension
landed, with no error anywhere.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent

# The library defines the composition, so it is the one place allowed to call
# the inner method. Everything else is content.
LIBRARY_FILE = "ElysiumSocketable.java"


def mod_sources():
    """Every content mod's java, under either the multi-repo or mono layout."""
    found = []
    for short in ("core", "dungeons", "mobs", "trinkets", "npcs"):
        for directory in (ROOT / short, ROOT / f"elysium-{short}"):
            source = directory / "src/main/java"
            if source.is_dir():
                found.append((directory.name, source))
                break
    return found


def classes_with_bodies(text):
    """(class name, body) for every class declaration in a file."""
    out = []
    for match in re.finditer(r"\bclass\s+(\w+)[^{]*?\{", text):
        start = match.end() - 1
        depth, i = 0, start
        while i < len(text):
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        out.append((match.group(1), match.group(0), text[start:i]))
    return out


def check(mod_name, source_dir):
    problems = []
    for path in sorted(source_dir.rglob("*.java")):
        if path.name == LIBRARY_FILE:
            continue
        text = path.read_text(encoding="utf-8")
        if "ElysiumSocketable" not in text:
            continue

        for name, declaration, body in classes_with_bodies(text):
            if "ElysiumSocketable" not in declaration:
                continue
            if "getDefaultAttributeModifiers" not in body:
                # Inherits the default, which is already the whole treatment.
                continue
            if "elysiumModifiers(" in body:
                continue
            if "applyRunes(" in body:
                problems.append(
                    f"{mod_name}: {path.name}: {name} builds its attribute modifiers with "
                    f"applyRunes(...). Use elysiumModifiers(...) — otherwise the item takes "
                    f"runes but gains nothing at all from being ascended")
            else:
                problems.append(
                    f"{mod_name}: {path.name}: {name} overrides getDefaultAttributeModifiers "
                    f"without calling elysiumModifiers(...), so it gets neither its socketed "
                    f"runes nor its ascension bonuses")
    return sorted(set(problems))


def main():
    mods = mod_sources()
    if not mods:
        print("gear: no content mod source directories found — check has no coverage")
        return 1

    problems = []
    for mod_name, source_dir in mods:
        problems += check(mod_name, source_dir)

    if problems:
        print(f"{len(problems)} PROBLEM(S):")
        for problem in problems:
            print("  - " + problem)
        print("\nelysiumModifiers(stack, modifiers, group) is runes AND ascension. "
              "applyRunes\nis the inner half and is the library's to call.")
        return 1

    print(f"gear treatment      : {len(mods)} mod(s), every socketable item takes "
          f"runes and ascension")
    return 0


if __name__ == "__main__":
    sys.exit(main())
