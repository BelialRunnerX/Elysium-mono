#!/usr/bin/env python3
"""Derive Elysium's own names for the bestiary, and check they are ours.

    python3 tools/rename_bestiary.py            # derive, check, write the map
    python3 tools/rename_bestiary.py --apply    # and rewrite the tree

Why this is a generator and not a list of 167 names somebody typed.

134 of the 176 creature ids in elysium-bestiary were taken verbatim from the
jars they were converted from. That is not a naming preference, it is the
project's own rule in docs/CONVERSION.md section 1 -- "our code, our art, our
names" -- being kept for the code and the art and dropped for the names. A
typed list of replacements would fix the 134 and do nothing to stop the 135th,
because nothing about a typed list knows where a name came from.

So the names are derived from the columns the rosters already have, and the
result is checked against every entity name in every jar in the pack
(tools/data/borrowed_entity_names.json). A court is a set, so its members are
named for their court and their role. A kindred family shares a form and its
members vary by material or place. A companion is named for the one job it
does. The structure a player can already feel is the structure the names now
carry, and a name nobody could have derived is a name somebody borrowed.
"""
import json
import os
import pathlib
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
BES = os.path.join(ROOT, "elysium-bestiary/src/main/java/com/elysium/bestiary")
RULES = os.path.join(BES, "rules")
BORROWED = os.path.join(HERE, "data/borrowed_entity_names.json")
MAP_OUT = os.path.join(HERE, "data/rethemed_names.json")


def read(name):
    with open(os.path.join(RULES, name), encoding="utf-8") as handle:
        return handle.read()


def rows(text, record, least):
    out = []
    for call in re.findall(r"new %s\(([^()]*(?:\([^()]*\)[^()]*)*)\)" % record, text):
        parts = [one.strip().strip('"') for one in call.split(",")]
        if len(parts) >= least:
            out.append(parts)
    return out


# ---------------------------------------------------------------------------
# The vocabulary
# ---------------------------------------------------------------------------
#
# Two halves to every name: what it is made of or came out of, and what shape
# it takes. Both halves are drawn from the settled system this pack is set in
# -- foundries, reactors, regolith, signal -- rather than from folklore, which
# is where every one of the 134 came from.

#: What a thing is made of, or the place that made it.
MATTER = {
    "base": "ferric", "desert": "dune", "frost": "rime", "nether": "cinder",
    "cave": "deep", "hell": "cinder", "dart": "dart", "imp": "cinder",
    "wild": "feral", "were": "moon", "werewolf": "moon",
    "werevillager": "mask",
    "nightmare": "cinder", "skeleton": "bone", "zombie": "rot",
    "fire": "cinder", "ice": "rime", "evil": "null", "horse": "haul",
}

#: What shape it takes. One per kindred family, so kinship reads off the name.
FORM = {
    "scorpion": "barb", "ogre": "hulk", "goblin": "runt", "wolf": "fang",
    "horse": "charger", "ghost": "echo", "golem": "frame", "bat": "wing",
    "mimic": "crate", "owlbear": "talon", "purple_worm": "bore",
    "shrike": "caster", "silver_skeleton": "caller",
}

#: Ids that are also the name of one of the game's own mobs, and are ours
#: anyway. Each of these is a row in one of our rosters that happened to be
#: spelled like a vanilla mob; renaming them is right.
#:
#: Anything else that collides with a vanilla name is refused, because the one
#: that was not on this list was `zombie` -- and `zombie` in BeastRules is not
#: a beast of ours at all, it is `BeastRules.ZOMBIE`, the game's own mob, kept
#: as the yardstick every beast in the module is priced against. Renaming it
#: renamed the ruler. Fifteen of negtest_behaviours.sh's cases stopped being
#: caught and that is the only reason it was found.
VANILLA_OK = {"illusioner", "skeleton_horse", "zombie_horse"}

#: A family of one. There is nothing to tell apart, so these are named for
#: what they are rather than assembled out of a matter stem and a form.
ALONE = {
    "mimic": "falsecrate", "owlbear": "talonhulk",
    "purple_worm": "deepbore", "shrike": "spinecaster",
    "silver_skeleton": "chromecaller",
}

#: A court is a set. Its stem says which set, and the role says what the
#: member does in it -- which is the thing a player has to read in the two
#: seconds before it reaches them.
COURT_STEM = {
    "bonescaller": "forge", "pumpkin": "graft", "krampus": "hoar",
    "zombie": "rot", "spirit": "static", "hound": "pack",
    "vermin": "brood", "chaos": "breach",
}
ROLE_STEM = {
    "HEAD": "prime", "HERALD": "caller", "SWARM": "let", "SHIELD": "guard",
    "ARTILLERY": "mortar", "HEALER": "mender", "MOUNT": "charger",
}
#: Where a court has more than one of a role, they are told apart by what
#: they are rather than by a number: a "forgelet_two" is a name nobody
#: remembers and nobody can shout across a room.
SWARM_MARK = ["", "mite", "spall", "sprue", "dross", "flake", "grit"]
GUARD_MARK = ["", "plate", "bulk", "rivet", "shell"]
HERALD_MARK = ["", "horn"]

#: One job apiece. A companion is a tool that walks, so it is named for the
#: job, not for an animal it resembles.
COMPANION = {
    "SHOWS_DARK": "lumen", "REACH": "tether", "HOLDS": "holdfast",
    "PRESSES": "tapper", "BLOOMS": "seeder", "DIGS": "borer",
    "HIDES": "stasher", "CHILLS": "chiller", "DOUBLES": "doubler",
    "BITES": "snapper", "BURNS": "igniter",
}

#: The warded are named for what they are made of. Deliberately not for the
#: ward they carry: a name that announces the answer turns "work out what
#: this needs" into reading a label, and the whole feature is the working out.
WARDED = {
    "ratman": "scurf", "gremlin": "slinger", "bad_patch": "canker",
    "ghosty": "residue", "lizzy": "cinderling", "swampman": "sludge",
    "ghastling": "pyrelet", "eldbomb": "charge", "samhain": "masker",
    "momma_lizzy": "cinderdam", "fallen_knight": "hullguard",
    "eldknight": "rimeguard", "netherman": "detonant",
}

#: The myths are what the colony tells itself is out there. Named for the one
#: thing each is supposed to do, because that is the part of a rumour that
#: survives being retold.
MYTH = {
    "PETRIFY": "glasseye", "DRAIN": "siphon", "BREATH": "blastjaw",
    "PLATING": "plateframe", "NUMBERS": "swarmhand", "THIEVERY": "pilferhand",
    "HEIGHT": "skimwing", "SPORES": "bloomlet",
}

#: The nine and the twelve were already ours. They move anyway, because a
#: bestiary that reads half industrial and half pastoral reads as two packs.
BEASTS = {
    "shardling": "spallet", "ripper": "shearjaw", "stalker": "prowlframe",
    "husk_ox": "drayhulk", "breaker": "pileframe", "siege_beast": "siegehulk",
    "mourner": "carrier", "keener": "sidetone", "hollow_lord": "nullprime",
    "zombie": "rotframe",
}
FAUNA = {
    "moorstag": "fenstag", "dunemare": "dunecharger", "fellox": "ferrox",
    "stonemole": "cragmole", "quillrat": "quillmite", "brackhog": "brackhide",
    "cragjay": "cragwing", "mirefowl": "mirewing", "ashquail": "ashwing",
    "glasseel": "glassfin", "silverfin": "chromefin", "shalecrab": "shaleshell",
}

#: The Vale is a place of its own, so its denizens are named for the crossing
#: that made them. The crossing is already on every row.
VALE_FORM = {
    0: ["let", "mote"],
    1: ["kin", "crawler"],
    2: ["hulk", "maw"],
    3: ["prime", "warden"],
}


def join(stem, form):
    """Stick two halves together without leaving a doubled letter at the seam.

    chalk + kin is chalkkin, which nobody reads correctly the first time and
    nobody spells correctly ever. One letter goes.
    """
    if stem and form and stem[-1] == form[0]:
        return stem + form[1:]
    return stem + form


def crossing_stem(place):
    """A short stem for a crossing, taken from its own name."""
    head = place.split("_")[0]
    return {"verdant": "verd", "pinewold": "pine", "chalk": "chalk",
            "bloomfen": "bloom", "goldwood": "gold", "glacier": "glace",
            "ashen": "ash", "coldmarsh": "marsh", "amber": "amber",
            "reedbasin": "reed", "emberwood": "ember", "rime": "rime",
            "frostmere": "frost", "cinder": "cinder",
            "sulphur": "sulph"}.get(head, head[:5])


def derive():
    """Every old id to its new one."""
    out = {}

    # --- the kindred: a family shares a form, members vary by matter -------
    kin = read("KindredRules.java")
    for row in rows(kin, "Kin", 4):
        old, family = row[0], row[1]
        form = FORM.get(family)
        if not form:
            continue
        if old in ALONE:
            out[old] = ALONE[old]
            continue
        lead = old[:-len(family) - 1] if old.endswith("_" + family) else ""
        if old == family:
            matter = MATTER["base"]
        else:
            matter = MATTER.get(lead) or MATTER.get(old.split("_")[0]) \
                or old.split("_")[0]
        out[old] = join(matter, form)

    # --- the courts: the set, and the job in it ---------------------------
    court = read("CourtRules.java")
    seen = {}
    for row in rows(court, "Member", 11):
        old, which, role = row[0], row[1], row[2]
        stem = COURT_STEM.get(which, which)
        job = ROLE_STEM.get(role, role.lower())
        key = (stem, job)
        nth = seen.get(key, 0)
        seen[key] = nth + 1
        marks = {"let": SWARM_MARK, "guard": GUARD_MARK,
                 "caller": HERALD_MARK}.get(job, [""] * 9)
        mark = marks[nth] if nth < len(marks) else "mk%d" % nth
        out[old] = join(stem, job) + (("_" + mark) if mark else "")

    # --- the warded, the companions, the myths ----------------------------
    for row in rows(read("WardRules.java"), "Warded", 9):
        if row[0] in WARDED:
            out[row[0]] = WARDED[row[0]]
    for row in rows(read("CompanionRules.java"), "Companion", 8):
        if row[1] in COMPANION:
            out[row[0]] = COMPANION[row[1]]
    for row in rows(read("MythRules.java"), "Myth", 2):
        if row[1] in MYTH:
            out[row[0]] = MYTH[row[1]]

    # --- the nine and the twelve ------------------------------------------
    out.update(BEASTS)
    out.update(FAUNA)

    # --- the Vale: the crossing that made it, and how far out -------------
    vale = read("DenizenRules.java")
    tally = {}
    for row in rows(vale, "Denizen", 7):
        old, place, ring = row[0], row[1], int(row[2])
        stem = crossing_stem(place)
        forms = VALE_FORM.get(ring, ["kin", "crawler"])
        nth = tally.get(stem, 0)
        tally[stem] = nth + 1
        out[old] = join(stem, forms[nth] if nth < len(forms)
                        else forms[0] + "_%d" % nth)
    return out


def vanilla():
    """Every mob the game itself ships."""
    jar = os.path.join(os.environ.get("ELYSIUM_MC_LIBS", "/tmp/mc"),
                       "vanilla.jar")
    if not os.path.isfile(jar):
        return set()
    import zipfile
    with zipfile.ZipFile(jar) as open_jar:
        named = json.load(open_jar.open("assets/minecraft/lang/en_us.json"))
    return {key.split(".", 2)[2] for key in named
            if key.startswith("entity.minecraft.")}


def check(mapping):
    """Nothing repeated, and nothing anybody else already calls something."""
    trouble = []
    theirs_too = vanilla()
    if not theirs_too:
        trouble.append("the game's own jar is not here, so a row that is "
                       "really one of the game's mobs would be renamed and "
                       "nothing would say so")
    for old in sorted(set(mapping) & theirs_too):
        if old not in VANILLA_OK:
            trouble.append(f"{old} is what the game calls one of its own "
                           f"mobs. If that row is ours it goes in "
                           f"VANILLA_OK with a reason; if it is a reference "
                           f"to the game's mob, renaming it breaks whatever "
                           f"it was being compared against")
    borrowed = json.load(open(BORROWED, encoding="utf-8"))
    theirs = {}
    for namespace, names in borrowed.items():
        if namespace.startswith("elysium"):
            continue
        for one in names:
            theirs.setdefault(one, []).append(namespace)

    backwards = {}
    for old, new in mapping.items():
        backwards.setdefault(new, []).append(old)
    for new, olds in sorted(backwards.items()):
        if len(olds) > 1:
            trouble.append(f"{new} is the new name of {len(olds)}: {olds}")
    for old, new in sorted(mapping.items()):
        if new in theirs:
            trouble.append(f"{new} (was {old}) is {theirs[new][0]}'s name too")
        if not re.fullmatch(r"[a-z][a-z_]*[a-z]", new):
            trouble.append(f"{new} is not a legal id")
        if new == old:
            trouble.append(f"{old} did not move")
    return trouble


#: Where an id can legally appear. Everything operational -- a rules row, a
#: registration, a generator's table, a negative test's anchor -- spells an id
#: inside quotes. Prose does not, which is the point: a blind word-boundary
#: replace would rewrite the word "zombie" in a sentence about zombies, and
#: several of the old ids are ordinary English.
WHERE = [
    "elysium-bestiary/src/main/java",
    "elysium-bestiary/tools",
    "elysium-bestiary/validate.py",
    "elysium-almanac/src/main/java",
    "elysium-almanac/tools",
    "elysium-almanac/validate.py",
    "tools/negtest",
]
SUFFIXES = (".java", ".py", ".sh")


def apply(mapping):
    """Rewrite every quoted id, in one pass so nothing can chain."""
    quoted = re.compile(r'"(' + "|".join(
        sorted((re.escape(one) for one in mapping), key=len, reverse=True)) + r')"')
    touched, changes = 0, 0
    for where in WHERE:
        full = os.path.join(ROOT, where)
        files = []
        if os.path.isfile(full):
            files = [full]
        else:
            for here, _, names in os.walk(full):
                files += [os.path.join(here, one) for one in names
                          if one.endswith(SUFFIXES)]
        for path in sorted(files):
            with open(path, encoding="utf-8") as handle:
                before = handle.read()
            after, count = quoted.subn(
                lambda hit: '"' + mapping[hit.group(1)] + '"', before)
            if count:
                with open(path, "w", encoding="utf-8") as handle:
                    handle.write(after)
                touched += 1
                changes += count
                print(f"  {os.path.relpath(path, ROOT)}: {count}")
    return touched, changes


#: A third place an id lives, and the one apply() could not see: inside a file
#: path. A negative test that deletes a creature's texture writes
#:
#:     remove $M/.../textures/entity/crab.png
#:
#: which is not a quoted token and not prose. After the rename those lines
#: deleted a file that no longer existed, `rm -f` said nothing, the validator
#: had nothing to complain about, and eleven cases across seven scripts
#: reported MISSED -- which is the negative tests doing exactly their job, and
#: the only reason this class was found at all.
PATH_SHAPES = ["%s.png", "%s.json", "add_%s_spawns", "%s_spawn_egg",
               "%s_spawns.json", "/%s/"]


def paths(mapping, show=True):
    """Rewrite ids where they are part of a filename rather than a token."""
    changed = 0
    for path in sorted(pathlib.Path(os.path.join(ROOT, "tools/negtest")).glob("*.sh")):
        before = path.read_text(encoding="utf-8")
        after = before
        for old, new in sorted(mapping.items(), key=lambda kv: -len(kv[0])):
            for shape in PATH_SHAPES:
                after = after.replace(shape % old, shape % new)
        if after != before:
            path.write_text(after, encoding="utf-8")
            hits = sum(1 for a, b in zip(before.split("\n"), after.split("\n"))
                       if a != b)
            changed += hits
            if show:
                print(f"  {path.name}: {hits} line(s)")
    return changed


#: Prose names creatures too, and prose that names a creature which no longer
#: exists is worse than no prose: the almanac hands the player a quest about a
#: shardling and there is no shardling. Only ids distinctive enough to be
#: unmistakable are rewritten here -- "a zombie" in a sentence usually means
#: the game's own zombie, and "breaker" is a piece of electrical kit.
PROSE_SAFE = {
    "shardling", "husk_ox", "hollow_lord", "siege_beast", "mourner", "keener",
    "ripper", "stalker", "moorstag", "dunemare", "fellox", "stonemole",
    "quillrat", "brackhog", "cragjay", "mirefowl", "ashquail", "glasseel",
    "silverfin", "shalecrab", "iceologer", "moobloom", "tuff_golem",
    "copper_golem", "bristler", "wildfire", "basilisk", "chupacabra",
    "sporeling", "automaton", "bonescaller", "pumpkinhead", "krampus",
    "purple_worm", "netherman", "eldknight", "samhain", "ghastling",
    "owlbear", "werewolf", "werevillager", "dryador", "draugr", "firron",
}
PROSE_IN = [
    "elysium-bestiary/src/main/java", "elysium-bestiary/validate.py",
    "elysium-bestiary/tools", "elysium-almanac/src/main/java",
    "elysium-almanac/tools", "elysium-almanac/validate.py", "tools/negtest",
]


def prose(mapping, show=True):
    """Rewrite creature names where they are written as words, not as ids."""
    pairs = []
    for old in sorted(PROSE_SAFE & set(mapping), key=len, reverse=True):
        new = mapping[old]
        pairs.append((old, new))                       # husk_ox
        if "_" in old:
            pairs.append((old.replace("_", " "), new))  # husk ox
            pairs.append((old.replace("_", " ").title(), new.capitalize()))
        pairs.append((old.capitalize(), new.capitalize()))
    changed = 0
    for where in PROSE_IN:
        full = os.path.join(ROOT, where)
        files = [full] if os.path.isfile(full) else [
            os.path.join(h, n) for h, _, ns in os.walk(full) for n in ns
            if n.endswith((".java", ".py", ".sh"))]
        for path in sorted(files):
            with open(path, encoding="utf-8") as handle:
                before = handle.read()
            after = before
            for old, new in pairs:
                # Not a letter either side, so "husk ox" in a sentence moves
                # and "husk_oxen" does not. A whole quoted token is left
                # alone, because that is an id and apply() has had it
                # already -- but "The husk ox" ends at a quote and is prose,
                # so the guard is about both sides at once rather than each.
                def swap(hit, new=new):
                    lead = after[hit.start() - 1] if hit.start() else ""
                    tail = after[hit.end()] if hit.end() < len(after) else ""
                    return hit.group(0) if lead == '"' and tail == '"' else new
                after = re.sub(r"(?<![A-Za-z_])" + re.escape(old)
                               + r"(?![A-Za-z_])", swap, after)
            if after != before:
                with open(path, "w", encoding="utf-8") as handle:
                    handle.write(after)
                hits = sum(1 for a, b in zip(before.split("\n"), after.split("\n"))
                           if a != b)
                changed += hits
                if show:
                    print(f"  {os.path.relpath(path, ROOT)}: {hits} line(s)")
    return changed


if __name__ == "__main__":
    mapping = derive()
    # This is a one-shot tool. Run again after it has been applied and every
    # row derives to the name it already has, which is not 167 problems, it
    # is the job being done.
    settled = sum(1 for old, new in mapping.items() if old == new)
    if settled > len(mapping) / 2:
        print(f"{settled} of {len(mapping)} name(s) already derive to "
              f"themselves, so this rename has been applied. "
              f"{os.path.relpath(MAP_OUT, ROOT)} is the record of what moved.")
        sys.exit(0)
    trouble = check(mapping)
    print(f"{len(mapping)} name(s) derived")
    if trouble:
        print(f"{len(trouble)} PROBLEM(S):")
        for one in trouble[:40]:
            print("  - " + one)
        sys.exit(1)
    os.makedirs(os.path.dirname(MAP_OUT), exist_ok=True)
    with open(MAP_OUT, "w", encoding="utf-8") as out:
        json.dump(mapping, out, indent=0, sort_keys=True)
        out.write("\n")
    print(f"every name is ours, and none is anybody else's -> "
          f"{os.path.relpath(MAP_OUT, ROOT)}")
    if "--apply" in sys.argv:
        print("rewriting:")
        touched, changes = apply(mapping)
        print(f"{changes} id(s) rewritten across {touched} file(s).")
        print("and where they are part of a filename:")
        print(f"{paths(mapping)} line(s) of path rewritten.")
        print("and where they are written as words:")
        print(f"{prose(mapping)} line(s) of prose rewritten. Regenerate and "
              f"run the battery.")
