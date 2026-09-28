#!/usr/bin/env python3
"""
Every gear class in every module is one the kind-tag pack can place.

    python3 check_kind_tags.py

The library writes the vanilla kind tags of the pack's gear as a data pack
from the registry (elysium-lib/validate.py, "Kind tags"): every item in one
of the pack's namespaces is asked what kind it is by ElysiumLootCategory,
and the kind says which of vanilla's lists it is on. That resolver reads a
class — a SwordItem is a sword — or an answer the item gives itself through
ElysiumKinded, for the gear whose class says nothing: a DiggerItem is any
digging tool, a ProjectileWeaponItem is any launcher, a TieredItem is
anything with a tier. A module that builds gear on one of those and does not
say what it is has gear the resolver reads as "some weapon" or as nothing,
and that gear is on no list and enchants at no table — silently, since the
class compiles and the item works (found by hand, 28 Sep 2026: the arsenal's
broad tools and bows).

What no module validator can see is the seam: which bases the library's
resolver knows, and which classes across twenty modules extend the ones it
does not. So this walks every module for classes extending a vanilla item
base, follows a module's own class chain, and holds each blind base to an
ElysiumKinded answer whose literal kinds the gear tree has. And the other
half of the seam: the pack tags only namespaces with the library's prefix,
so every module's id has to carry it, or that module's gear is skipped
without a word.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
LIB = os.path.join(ROOT, "elysium-lib/src/main/java/com/elysium/lib")
CATEGORY = os.path.join(LIB, "item/ElysiumLootCategory.java")
TABLE = os.path.join(LIB, "item/ElysiumKindTags.java")
TREE = os.path.join(LIB, "item/ElysiumGearTree.java")
COMMENT = re.compile(r"//[^\n]*|/\*.*?\*/", re.S)
CLASS = re.compile(r"^\s*public\s+(?:static\s+)?(?:final\s+)?(?:abstract\s+)?class\s+(\w+)\s+extends\s+([\w.]+)([^{]*)\{", re.M)

# The vanilla bases whose class alone says nothing about the kind of gear.
BLIND = {"DiggerItem", "TieredItem", "ProjectileWeaponItem", "Item"}
# The vanilla bases the resolver reads by class; kept in step with the
# resolver's own instanceof chain, which this reads rather than repeats.
GEAR_WORDS = ("Sword", "Mace", "Axe", "Pickaxe", "Shovel", "Hoe", "Shears", "Trident", "Bow", "Crossbow",
              "Shield", "FishingRod", "Elytra", "Armor", "Digger", "Tiered", "ProjectileWeapon")


def read(path):
    return COMMENT.sub(" ", open(path, encoding="utf-8").read())


def modules():
    for name in sorted(os.listdir(ROOT)):
        if name.startswith("elysium-") and os.path.isdir(os.path.join(ROOT, name, "src/main/java")):
            yield name


def mod_id(module):
    props = os.path.join(ROOT, module, "gradle.properties")
    if os.path.isfile(props):
        m = re.search(r"^mod_id\s*=\s*(\S+)", open(props, encoding="utf-8").read(), re.M)
        if m:
            return m.group(1)
    toml = os.path.join(ROOT, module, "src/main/resources/META-INF/neoforge.mods.toml")
    if os.path.isfile(toml):
        m = re.search(r'^modId\s*=\s*"([^"$]+)"', open(toml, encoding="utf-8").read(), re.M)
        if m:
            return m.group(1)
    return None


def main():
    problems = []
    if not os.path.isfile(CATEGORY) or not os.path.isfile(TABLE) or not os.path.isfile(TREE):
        print("1 PROBLEM(S):\n  - the library has no resolver, kind table or gear tree, so no gear is tagged")
        return 1
    known = set(re.findall(r"instanceof (\w+Item)\b", read(CATEGORY)))
    prefix_m = re.search(r'NAMESPACE_PREFIX = "(\w+)"', read(TABLE))
    prefix = prefix_m.group(1) if prefix_m else ""
    tree = set(re.findall(r'\{"(\w+)", (?:"\w+"|null)\}', read(TREE)))
    if not prefix:
        problems.append("ElysiumKindTags has no NAMESPACE_PREFIX, so the pack cannot tell its own items from others'")
    if not known:
        problems.append("ElysiumLootCategory reads no class at all, so nothing is gear by its class")

    modules_seen = 0
    for module in modules():
        modules_seen += 1
        this = mod_id(module)
        if this is None:
            problems.append(f"{module}: no mod id in gradle.properties or the manifest, so whether the pack tags it is unknown")
        elif prefix and not this.startswith(prefix):
            problems.append(f"{module}: its mod id \"{this}\" does not start with \"{prefix}\", so the kind-tag pack "
                            f"skips every item it registers and none of its gear can be enchanted")

    classes = {}          # module -> {name: (base, implements text, file)}
    for module in modules():
        classes[module] = {}
        src = os.path.join(ROOT, module, "src/main/java")
        for dirpath, _, filenames in os.walk(src):
            for filename in filenames:
                if not filename.endswith(".java"):
                    continue
                path = os.path.join(dirpath, filename)
                text = read(path)
                for name, base, rest in CLASS.findall(text):
                    classes[module][name] = (base.split(".")[-1], rest, os.path.relpath(path, ROOT).replace(os.sep, "/"))

    def placed(module, name, seen):
        """How the resolver places a class: 'class', 'kinded', or None for blind."""
        if name in seen:
            return None
        seen.add(name)
        base, rest, _ = classes[module][name]
        if "ElysiumKinded" in rest or "ArmoryGear" in rest:
            return "kinded"
        if base in classes[module]:
            return placed(module, base, seen)
        if base in known:
            return "class"
        if base in BLIND:
            return None
        return "class"     # not a vanilla gear base at all

    gear_classes = 0
    for module, named in classes.items():
        for name, (base, rest, rel) in named.items():
            chain_base = base
            seen = set()
            while chain_base in named and chain_base not in seen:
                seen.add(chain_base)
                chain_base = named[chain_base][0]
            if not any(word in chain_base for word in GEAR_WORDS) or chain_base == "Item":
                continue
            gear_classes += 1
            if placed(module, name, set()) is None:
                problems.append(f"{rel}: {name} extends {base}, which the resolver reads as no particular gear, and "
                                f"neither it nor a parent implements ElysiumKinded — so every item of it is on no "
                                f"vanilla list and enchants at no table")

    # A kinded class answers with names the tree has: a literal it returns
    # is checked; a name built from a table is the module validator's to hold.
    kinded_literals = 0
    for module, named in classes.items():
        for name, (base, rest, rel) in named.items():
            if "ElysiumKinded" not in rest:
                continue
            text = read(os.path.join(ROOT, rel))
            body = re.search(r"public String gearKind\(\)\s*\{(.*?)\n\s*\}", text, re.S)
            if not body:
                problems.append(f"{rel}: {name} implements ElysiumKinded and has no gearKind(), which is a class that says it "
                                f"will answer and does not")
                continue
            for literal in re.findall(r'"(\w+)"', body.group(1)):
                if literal in tree:
                    kinded_literals += 1
                elif literal not in ("scythe",):      # a shape name the tool maps, not a kind it claims
                    problems.append(f"{rel}: {name} answers \"{literal}\" for its kind, which the gear tree does not have, "
                                    f"so the resolver ignores the answer and the item is placed by class instead")

    if problems:
        print(f"{len(problems)} PROBLEM(S):")
        for problem in problems:
            print("  - " + problem)
        return 1
    print(f"kind tags          : {gear_classes} gear class(es) across {modules_seen} modules, every one placed by "
          f"a class the resolver reads or by its own ElysiumKinded answer ({kinded_literals} literal kind(s), all "
          f"in the tree); every module id under the pack's \"{prefix}\" prefix")
    return 0


if __name__ == "__main__":
    sys.exit(main())
