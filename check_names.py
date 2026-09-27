#!/usr/bin/env python3
"""No creature of ours is called what somebody else's creature is called.

    python3 check_names.py

This check exists because the project failed it for months without noticing.
docs/CONVERSION.md section 1 says what conversion means here -- "our code, our
art, our names" -- and the code and the art were held to it by compile.sh and
by check_retexture.py while the names were held to nothing at all. On 26
September a count found that 134 of elysium-bestiary's 176 creature ids were
the source jars' own: 58 from born_in_chaos, 28 from Valoria, 27 from
fangs_n_claws, 13 from knightquest, 10 from friendsandfoes.

Nothing reported it. Every validator passed, the harnesses ran, the negative
tests caught what they were pointed at, and the one claim nobody had written
down as a rule was the one being broken.

What is compared is entity names against entity names, and nothing else. A
block called `steel_ingot` is a noun the language already had and half the
modded world calls it that; a creature called `bonescaller` is a name somebody
invented and we took. The first is convergence and the second is copying, and
a check that cannot tell them apart is a check that gets switched off.

That line is drawn with a dictionary rather than with an exemption list. A
shared name is reported only when some part of it is a coinage -- a word that
did not exist until a mod author made it up. We may both call a thing a
swordfish, an atlantic_cod or a copper_bee, because there is no other word for
a swordfish and "copper" and "bee" are the obvious two halves of a bee that
makes copper; convergence there is arithmetic, not influence. We may not both
call one a bonescaller, an iceologer or a chupacabra. The first version of
this check had no such rule and reported forty-five bees and fish, which is
how a check earns the reputation that gets it commented out.

Both sides are read the same way -- the `entity.<namespace>.<name>` keys of a
language file -- so neither side is a list anybody maintains by hand. Ours
come from the generated language files in the tree; theirs come from
tools/data/borrowed_entity_names.json, which is every such key in every jar in
the pack, 1,270 names across 51 namespaces.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
THEIRS = os.path.join(HERE, "tools/data/borrowed_entity_names.json")
COMMON = os.path.join(HERE, "tools/data/common_words.txt")

problems = []


def common_words():
    """The parts of a name that were words before anybody modded anything."""
    if not os.path.isfile(COMMON):
        problems.append(
            "tools/data/common_words.txt is missing, so this check cannot "
            "tell a coinage from an ordinary noun and would report every "
            "swordfish in the pack. Rebuild it with "
            "tools/gen_common_words.py")
        return set()
    found = set()
    for line in open(COMMON, encoding="utf-8"):
        line = line.strip()
        if line and not line.startswith("#"):
            found.add(line)
    return found


def coined(name, words):
    """Whether any part of this name is a word somebody invented.

    Names are checked part by part rather than whole, because that is how
    they are built: `crimson_iron_bee` is three ordinary words and
    `supreme_bonescaller` is one ordinary word and one coinage. A name is
    somebody's invention if any part of it is.
    """
    return any(part not in words for part in name.split("_") if part)


def ours():
    """Every entity this repository names, and where it names it."""
    found = {}
    for module in sorted(os.listdir(HERE)):
        assets = os.path.join(HERE, module, "src/main/resources/assets")
        if not module.startswith("elysium-") or not os.path.isdir(assets):
            continue
        for namespace in sorted(os.listdir(assets)):
            where = os.path.join(assets, namespace, "lang/en_us.json")
            if not os.path.isfile(where):
                continue
            try:
                keys = json.load(open(where, encoding="utf-8"))
            except ValueError:
                problems.append(f"{module}'s language file will not parse, so "
                                f"none of its names are checked here")
                continue
            for key in keys:
                hit = re.fullmatch(r"entity\.([a-z0-9_]+)\.([a-z0-9_]+)", key)
                if hit:
                    found.setdefault(hit.group(2), []).append(module)
    return found


def theirs():
    """Every entity the rest of the pack names, and whose it is."""
    if not os.path.isfile(THEIRS):
        problems.append(
            "tools/data/borrowed_entity_names.json is missing, so this check "
            "compares our names against nothing and passes whatever they are. "
            "Rebuild it from the jars in the pack before trusting a pass here")
        return {}
    found = {}
    for namespace, names in json.load(open(THEIRS, encoding="utf-8")).items():
        if namespace.startswith("elysium"):
            continue
        for one in names:
            found.setdefault(one, []).append(namespace)
    return found


MINE, NOT_MINE, WORDS = ours(), theirs(), common_words()
if MINE and len(MINE) < 50:
    problems.append(f"only {len(MINE)} entity name(s) could be read out of "
                    f"this repository, so this check is looking at almost "
                    f"nothing. Regenerate the language files first")
if NOT_MINE and len(NOT_MINE) < 200:
    problems.append(f"only {len(NOT_MINE)} entity name(s) could be read out "
                    f"of the pack, so a name of ours could match one of "
                    f"theirs and go unreported")

shared = sorted(set(MINE) & set(NOT_MINE))
convergent = [one for one in shared if not coined(one, WORDS)]
for name in [one for one in shared if coined(one, WORDS)]:
    problems.append(
        f"{name} is registered by {', '.join(sorted(set(MINE[name])))} and is "
        f"also what {NOT_MINE[name][0]} calls one of its creatures. Converting "
        f"a mod means keeping the idea and writing our own name for it; a "
        f"roster that keeps the original names is a re-skin wearing a "
        f"conversion's paperwork")

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for one in problems:
        print("  - " + one)
    sys.exit(1)

print(f"our own names      : {len(MINE)} creature(s) across this repository "
      f"against {len(NOT_MINE)} in the pack's other jars; {len(convergent)} "
      f"name(s) shared and every one of them a word the language already had, "
      f"not one of them anybody's coinage")
