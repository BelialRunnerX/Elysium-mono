#!/usr/bin/env python3
"""Is the companion content actually inside the jars?

Why this exists
---------------
Everything else in this repo checks the *source*. This checks the *product*,
and it is the only check that does: a file can be correct in
src/main/resources, pass every validator, and still not reach a player, because
packaging is a separate step with its own rules about what it copies and its
own opportunities to exclude something.

The Extraction Ledger is the case that made it worth writing. It is a datapack
that lives inside the mod so that nobody has to install it separately, which
means "it shipped" is a claim about a zip file and not about the tree. A
packaging change that quietly dropped `data/` would leave forty-five skills
that exist in the repository, validate perfectly, and are not in the game.

    python3 tools/checkship.py dist/elysium-all-1.0.0.jar [more.jar ...]

Exits non-zero if anything that is supposed to be in a jar is not.
"""

import json
import sys
import zipfile

# What must be inside a jar for the mod to work out of the box, and the reason
# it matters — printed on failure, because "missing file" is not a diagnosis.
FORBIDDEN = []

REQUIRED = [
    ("data/elysium/puffish_skills/config.json",
     "the file that names the category; without it the mod never loads the "
     "tree, whatever else is in the jar"),
    ("data/elysium/puffish_skills/categories/extraction_ledger/category.json",
     "the skill tree's category; without it Pufferfish's Skills shows nothing"),
    ("data/elysium/puffish_skills/categories/extraction_ledger/definitions.json",
     "the forty-five skills themselves"),
    ("data/elysium/puffish_skills/categories/extraction_ledger/skills.json",
     "where those skills sit on the tree"),
    ("data/elysium/puffish_skills/categories/extraction_ledger/connections.json",
     "what connects to what, including the exclusive commitments"),
    ("data/elysium/puffish_skills/categories/extraction_ledger/experience.json",
     "how points are earned; without it the tree opens with one point and "
     "nothing a player does ever gives another"),
    ("data/elysium/tags/block/empire_ores.json",
     "the tag the experience file tests Empire ores against"),
]

# A shipped tree whose text is missing renders as raw keys, which looks like a
# broken mod rather than a missing translation.
REQUIRED_LANG = ("assets/elysium/lang/en_us.json", "elysium.skilltree.")


def check(path):
    problems = []
    with zipfile.ZipFile(path) as jar:
        names = set(jar.namelist())
        for wanted, why in REQUIRED:
            if wanted not in names:
                problems.append(f"{path}: missing {wanted} — {why}")

        lang_path, prefix = REQUIRED_LANG
        if lang_path not in names:
            problems.append(f"{path}: missing {lang_path}, so every line of "
                            f"the skill tree renders as a raw key")
        else:
            lang = json.loads(jar.read(lang_path).decode("utf-8"))
            keys = [key for key in lang if key.startswith(prefix)]
            if not keys:
                problems.append(
                    f"{path}: the language file carries no {prefix}* keys, so "
                    f"the tree ships with no words on it")

        for unwanted, why in FORBIDDEN:
            if unwanted in names:
                problems.append(f"{path}: ships {unwanted} — {why}")

        # And the tree has to be readable once it is in there. A jar can carry
        # a file that is not valid JSON, and the game's only complaint is a log
        # line during startup that nobody reads.
        for wanted, _ in REQUIRED:
            if wanted in names:
                try:
                    json.loads(jar.read(wanted).decode("utf-8"))
                except Exception as exc:
                    problems.append(f"{path}: {wanted} is in the jar and is "
                                    f"not valid JSON ({exc})")
    return problems


def main():
    jars = sys.argv[1:]
    if not jars:
        print("usage: checkship.py <jar> [jar ...]")
        return 1
    problems = []
    for jar in jars:
        problems.extend(check(jar))
    if problems:
        print(f"{len(problems)} PROBLEM(S):")
        for problem in problems:
            print("  -", problem)
        return 1
    print(f"shipped content    : the skill tree and its {len(REQUIRED)} files "
          f"are inside {len(jars)} jar(s), with their language entries")
    return 0


if __name__ == "__main__":
    sys.exit(main())
