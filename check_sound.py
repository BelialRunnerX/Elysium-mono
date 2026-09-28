#!/usr/bin/env python3
"""
Every creature speaks with a voice the sound table has, every running
machine hums with a hum it has, every station clicks with a click it has.

    python3 check_sound.py

The library holds its own table, files and registry together
(elysium-lib/validate.py, "Sound"). What no module validator can see is the
seam between them: a creature in the bestiary that answers for a voice by a
family name, and a family the library never wrote a voice for, is a creature
the game hears as its class's default — a zombie's groan on a wight, or
silence — and nothing reports it. So this walks every module for the names
it asks the library for, and holds each to the table.

Creatures: every class that extends one of the game's living kinds overrides
its three sound methods through ElysiumVoices, and the name it answers with
is either a literal in the table or drawn from a rules list whose every value
is in the table (the bestiary's families and kinds, the fish's waters, the
eight mobs' ids). Machines and stations: every literal hum and click is one
the table has. Plus the seam the other way: a voice in the table nothing
asks for is a clip nobody hears, which is dead weight in every jar.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
LIB = os.path.join(ROOT, "elysium-lib/src/main/java/com/elysium/lib")

LIVING = ("Monster", "Animal", "PathfinderMob", "Mob", "AbstractFish", "TamableAnimal", "AbstractVillager",
          "Villager", "Zombie", "Hoglin", "Piglin", "PiglinBrute", "AbstractPiglin", "WaterAnimal", "AmbientCreature",
          "FlyingMob", "Raider", "AbstractIllager")
CLASS = re.compile(r"^public (?:abstract )?class (\w+) extends (\w+)", re.M)

# Voice names that reach the library through a rules list rather than a
# literal: the list's file, the pattern that reads its values, and what
# prefixes them.
LISTS = [
    ("elysium-bestiary/src/main/java/com/elysium/bestiary/rules/BeastRules.java",
     r'public static final String (?:PROWLER|BRUTE|WIGHT) = "(\w+)";', "", "a beast family"),
    ("elysium-bestiary/src/main/java/com/elysium/bestiary/rules/FaunaRules.java",
     r'public static final String (?:GRAZER|FORAGER|FOWL|SWIMMER) = "(\w+)";', "", "an animal kind"),
    ("elysium-agronomy/src/main/java/com/elysium/agronomy/rules/FishRules.java",
     r'public static final String (?:FRESH|SALT|LAVA|VOID) = "(\w+)";', "fish_", "a fish's water"),
    ("elysium-mobs/src/main/java/com/elysium/mobs/entity/ElysiumFamilies.java",
     r'_ID = id\("(\w+)"\)', "", "one of the eight"),
    ("elysium-mobs/src/main/java/com/elysium/mobs/ElysiumMobs.java",
     r'family\("(\w+)",', "", "a family registered by the mobs module"),
]


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def strip(text):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", " ", text)


def main():
    problems = []
    rules = strip(read(os.path.join(LIB, "sound/ElysiumSoundRules.java")))
    voices = set(re.findall(r'voice\("(\w+)",', rules))
    hums = set(re.findall(r'new Hum\("(\w+)",', rules))
    clicks = set(re.findall(r'new Click\("(\w+)",', rules))
    if not voices or not hums or not clicks:
        print("1 PROBLEM(S):\n  - the sound table cannot be read, so nothing below is checked")
        return 1

    asked = set()
    creatures = 0
    silent = []
    hum_sites = 0
    click_sites = 0
    for module in sorted(os.listdir(ROOT)):
        src = os.path.join(ROOT, module, "src/main/java")
        if not module.startswith("elysium-") or not os.path.isdir(src):
            continue
        for base, _, names in os.walk(src):
            for name in names:
                if not name.endswith(".java"):
                    continue
                path = os.path.join(base, name)
                text = strip(read(path))
                rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
                for cls, parent in CLASS.findall(text):
                    if parent not in LIVING or cls.endswith(("Renderer", "Model")):
                        continue
                    creatures += 1
                    if "ElysiumVoices.ambient(" not in text or "ElysiumVoices.hurt(" not in text \
                            or "ElysiumVoices.death(" not in text:
                        silent.append(f"{rel}: {cls} extends {parent} and does not answer for a voice")
                        continue
                    for literal in re.findall(r'return "(\w+)";', text[text.find("public String voice()"):] if "public String voice()" in text else ""):
                        asked.add(literal)
                        if literal not in voices:
                            problems.append(f"{rel}: speaks with the voice \"{literal}\", which the sound table does not have")
                for hum in re.findall(r'ElysiumVoices\.hum\([^;]*?"(\w+)"\)', text):
                    hum_sites += 1
                    if hum not in hums:
                        problems.append(f"{rel}: hums \"{hum}\", which the sound table does not have")
                for hum in re.findall(r'protected String hum\(\) \{\s*return "(\w+)";', text):
                    hum_sites += 1
                    if hum not in hums:
                        problems.append(f"{rel}: hums \"{hum}\", which the sound table does not have")
                for click in re.findall(r'ElysiumVoices\.click\([^;]*?"(\w+)"\)', text):
                    click_sites += 1
                    if click not in clicks:
                        problems.append(f"{rel}: clicks \"{click}\", which the sound table does not have")
    problems.extend(silent)
    if creatures < 20:
        problems.append(f"only {creatures} creature class(es) found; this check has lost the rosters")

    # The names that reach the table through a rules list.
    for rel, pattern, prefix, what in LISTS:
        path = os.path.join(ROOT, rel)
        if not os.path.isfile(path):
            problems.append(f"{rel} is missing, so {what} cannot be checked against the sound table")
            continue
        values = re.findall(pattern, strip(read(path)))
        if not values:
            problems.append(f"{rel} names no values for {what} any more, so this check reads nothing")
        for value in values:
            asked.add(prefix + value)
            if prefix + value not in voices:
                problems.append(f"{rel}: {what} \"{value}\" has no voice \"{prefix + value}\" in the sound table, "
                                f"so every creature of it speaks with its class's default")
    # The space mobs and the empire's people name their voices as literals and are
    # covered above; anything in the table nothing asks for is dead weight.
    unasked = sorted(voices - asked)
    if unasked:
        problems.append(f"{len(unasked)} voice(s) in the sound table nothing speaks with: {unasked[:6]}")
    if hum_sites < 15:
        problems.append(f"only {hum_sites} machine(s) hum; the engines across the modules are more than that")
    if click_sites < 10:
        problems.append(f"only {click_sites} station(s) click; the tables and benches are more than that")

    if problems:
        print(f"{len(problems)} PROBLEM(S):")
        for problem in problems:
            print("  - " + problem)
        return 1
    print(f"sound              : {creatures} creature classes each answering for a voice, {len(asked)} name(s) "
          f"asked for and every one in the table of {len(voices)}, {hum_sites} machines humming and "
          f"{click_sites} stations clicking with hums and clicks the table has")
    return 0


if __name__ == "__main__":
    sys.exit(main())
