#!/usr/bin/env python3
"""Rebuild tools/data/common_words.txt from a system dictionary.

    python3 tools/gen_common_words.py

check_names.py has to tell a coinage from a noun: we may share "swordfish"
with anybody who also ships a swordfish, and we may not share "bonescaller"
with the mod that invented it. The line between the two is whether the word
already existed, so the check needs a dictionary -- and a dictionary is not
something every machine that clones this repository will have.

So the words are cached here, and only the ones that matter: every
underscore-separated part of every entity name in the pack, ours and theirs,
that an English dictionary carries. About nine hundred of them. A part that is
not in this file is treated as coined, which is the safe direction to be wrong
in: a new word nobody has seen gets looked at, rather than waved through.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
THEIRS = os.path.join(HERE, "data/borrowed_entity_names.json")
OUT = os.path.join(HERE, "data/common_words.txt")
DICTIONARIES = ["/usr/share/hunspell/en_US.dic", "/usr/share/dict/words",
                "/usr/share/myspell/en_US.dic"]
GAME_JAR = os.path.join(os.environ.get("ELYSIUM_MC_LIBS", "/tmp/mc"),
                        "vanilla.jar")

#: Real things an English dictionary happens not to carry. Every one of these
#: is a creature that exists in the world, so two mods shipping one is two
#: mods shipping the same animal -- there is no other word for a blobfish.
#: Anything added here needs that sentence to be true of it.
REAL = ["blobfish", "mahi", "oarfish", "lionfish", "sunfish", "tilapia",
        "arapaima", "piranha", "coelacanth", "sturgeon", "wahoo", "opah"]


def the_game_says():
    """Mojang's own vocabulary. A word the game ships is nobody's coinage.

    productivebees calls a bee that makes netherite a netherite_bee, and so
    would anybody, because "netherite" is the game's word for the material and
    "bee" is the game's word for the insect. Without this the check reports
    five bees and reads as noise.
    """
    if not os.path.isfile(GAME_JAR):
        return set()
    import zipfile
    with zipfile.ZipFile(GAME_JAR) as jar:
        named = json.loads(jar.read("assets/minecraft/lang/en_us.json"))
    found = set()
    for key in named:
        hit = re.match(r"^(?:item|block|entity)\.minecraft\.([a-z0-9_]+)$", key)
        if hit:
            found |= set(hit.group(1).split("_"))
    return found


def dictionary():
    for where in DICTIONARIES:
        if not os.path.isfile(where):
            continue
        words = set()
        for line in open(where, encoding="latin-1"):
            word = line.split("/")[0].strip().lower()
            if word.isalpha():
                words.add(word)
        if len(words) > 10_000:
            return where, words
    print("no English dictionary on this machine, so the cached list cannot "
          "be rebuilt here. " + ", ".join(DICTIONARIES))
    sys.exit(2)


def parts():
    found = set()
    for names in json.load(open(THEIRS, encoding="utf-8")).values():
        for one in names:
            found |= set(one.split("_"))
    for module in sorted(os.listdir(ROOT)):
        assets = os.path.join(ROOT, module, "src/main/resources/assets")
        if not module.startswith("elysium-") or not os.path.isdir(assets):
            continue
        for namespace in sorted(os.listdir(assets)):
            where = os.path.join(assets, namespace, "lang/en_us.json")
            if not os.path.isfile(where):
                continue
            for key in json.load(open(where, encoding="utf-8")):
                hit = re.fullmatch(r"entity\.[a-z0-9_]+\.([a-z0-9_]+)", key)
                if hit:
                    found |= set(hit.group(1).split("_"))
    return found


if __name__ == "__main__":
    where, words = dictionary()
    game = the_game_says()
    if not game:
        print(f"note: {GAME_JAR} is not here, so the game's own vocabulary is "
              f"not in this list and words like 'netherite' will read as "
              f"coinages")
    words |= game
    words |= set(REAL)
    every = parts()
    common = sorted(one for one in every if one in words)
    with open(OUT, "w", encoding="utf-8") as out:
        out.write("# Every part of an entity name, ours or the pack's, that is "
                  "an ordinary\n# English word, one of Mojang's own, or a real "
                  "animal the dictionary\n# happens not to carry. Built from %s "
                  "and the game's jar by\n"
                  "# tools/gen_common_words.py. check_names.py uses it to tell "
                  "a coinage\n# from a noun: we may share 'swordfish' with "
                  "anybody, and we may not\n# share 'bonescaller'.\n"
                  % os.path.basename(where))
        out.write("\n".join(common) + "\n")
    print(f"{len(every)} part(s) across every entity name in the pack; "
          f"{len(common)} are ordinary English -> "
          f"{os.path.relpath(OUT, ROOT)}")
