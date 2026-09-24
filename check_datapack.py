#!/usr/bin/env python3
"""
Our data files, checked against the game's own codecs.

    python3 check_datapack.py

<h2>The bugs this exists for</h2>

Four files in one shipped jar, none of which any check we had could see,
because every one of them is valid JSON and every one of them named files that
exist. They only fail when the game builds its registries — which is when a
player presses **Create World**, long after the title screen, so the mod loads
and looks fine and then the pack is unusable.

  * a biome modifier with ``"biomes": ["#minecraft:is_nether", "a:b", "a:c"]``.
    A HolderSet is a tag *or* a list of ids; a tag inside a list is not a
    thing, and the error is ``Not a JSON object``.
  * an enchantment giving ``minecraft:trident_spin_attack_strength`` a list of
    conditional effects. In the jar that component is a bare
    ``EnchantmentValueEffect`` — one object, no list, no ``effect`` wrapper.
  * a configured feature whose ``type`` was its own id. ``type`` names a
    *feature*, of which there are about sixty and all of them vanilla's.
  * a ``random_selector`` pointing at two configured features. Its entries are
    **placed** features, and the two it named had no placed form at all.

<h2>Where the rules come from</h2>

The jars, not memory. The enchantment table is read out of
``EnchantmentEffectComponents`` with javap and the feature list out of
``Feature``, so this file cannot drift from the version we build against —
which matters, because every one of the four bugs above was somebody being
confident about a codec.
"""
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CLASSPATH_FILE = "/tmp/mc/cp.txt"

problems = []


def fail(msg):
    problems.append(msg)


if not os.path.isfile(CLASSPATH_FILE):
    print(f"datapack           : skipped, no {CLASSPATH_FILE} to ask")
    sys.exit(0)

CLASSPATH = open(CLASSPATH_FILE, encoding="utf-8").read().strip()


def javap(fqcn):
    result = subprocess.run(["javap", "-cp", CLASSPATH, fqcn],
                            capture_output=True, text=True)
    return result.stdout


# ---------------------------------------------------------------------------
# What shape each enchantment effect component takes, read from the jar
# ---------------------------------------------------------------------------

COMPONENT = re.compile(
    r"public static final net\.minecraft\.core\.component\.DataComponentType<"
    r"(?P<type>.+?)>\s+(?P<name>[A-Z0-9_]+);")

SHAPES = {}
for match in COMPONENT.finditer(javap("net.minecraft.world.item.enchantment.EnchantmentEffectComponents")):
    kind = match.group("type")
    key = "minecraft:" + match.group("name").lower()
    if kind.startswith("java.util.List<"):
        inner = kind[len("java.util.List<"):-1]
        conditional = inner.startswith("net.minecraft.world.item.enchantment.ConditionalEffect<") \
            or inner.startswith("net.minecraft.world.item.enchantment.TargetedConditionalEffect<")
        SHAPES[key] = "conditional list" if conditional else "plain list"
    elif kind.endswith("Unit"):
        SHAPES[key] = "unit"
    else:
        SHAPES[key] = "object"

if not SHAPES:
    print("datapack           : could not read EnchantmentEffectComponents from the jars")
    sys.exit(1)

# ---------------------------------------------------------------------------
# Which features exist, read from the jar
# ---------------------------------------------------------------------------
#
# Vanilla assigns every Feature to a constant of the same name in upper case,
# so the constants are the registry keys. Nothing we ship registers a feature
# of its own; if that ever changes this list grows from our registry the same
# way.

FEATURES = set()
for match in re.finditer(
        r"public static final net\.minecraft\.world\.level\.levelgen\.feature\.Feature<[^>]*>\s+([A-Z0-9_]+);",
        javap("net.minecraft.world.level.levelgen.feature.Feature")):
    FEATURES.add("minecraft:" + match.group(1).lower())

# ---------------------------------------------------------------------------
# Every data file we ship
# ---------------------------------------------------------------------------

def modules():
    for name in sorted(os.listdir(HERE)):
        data = os.path.join(HERE, name, "src", "main", "resources", "data")
        if name.startswith("elysium-") and os.path.isdir(data):
            yield name, data


#: (namespace, registry folder, path) -> file, for reference checking.
shipped = {}
files = []
for module, data in modules():
    for dirpath, _, filenames in os.walk(data):
        for name in filenames:
            if not name.endswith(".json"):
                continue
            path = os.path.join(dirpath, name)
            files.append((module, path))
            rel = os.path.relpath(path, data).replace(os.sep, "/")
            parts = rel.split("/")
            if len(parts) >= 3:
                shipped[(parts[0], "/".join(parts[1:-1]), parts[-1][:-5])] = path

if not files:
    print("no data files found — is this the Elysium tree?")
    sys.exit(1)

#: Only namespaces that are ours. We also write files under data/minecraft/
#: (tag additions), and treating that as ours made every reference to a vanilla
#: tag look like a missing file of ours.
OURS = {namespace for namespace, _, _ in shipped if namespace.startswith("elysium")}


def where(module, path):
    return f"{module}/{os.path.relpath(path, os.path.join(HERE, module))}"


def registry_of(path):
    """The registry folder a data file sits in, e.g. worldgen/configured_feature."""
    rel = path.replace(os.sep, "/")
    if "/resources/data/" not in rel:
        return None
    after = rel.split("/resources/data/", 1)[1].split("/")
    return "/".join(after[1:-1]) if len(after) >= 3 else None


def must_exist(module, path, reference, registry, field):
    """A reference into one of our own namespaces has to resolve to a file."""
    if not isinstance(reference, str) or ":" not in reference:
        return
    namespace, rest = reference.split(":", 1)
    if namespace not in OURS:
        return                              # vanilla or another mod: not ours to check
    if (namespace, registry, rest) not in shipped:
        fail(f"{where(module, path)}: {field} points at {reference}, and there is no "
             f"{registry} by that name in this repository")


# ---------------------------------------------------------------------------
# 1. A tag inside a list
# ---------------------------------------------------------------------------
#
# A HolderSet is written either as "#some:tag" or as a list of ids. A list with
# a tag in it parses as JSON and then fails in the codec with a message that
# names neither the file nor the field.

#: What a tag reference actually looks like. A crafting pattern row is "###",
#: which begins with a hash and is not a tag, and flagging those made the first
#: run of this file two hundred lines of noise.
TAG = re.compile(r"^#[a-z0-9_.-]+(?::[a-z0-9_./-]+)?$")


def tag_in_list(module, path, node, trail="root"):
    if isinstance(node, list):
        for index, item in enumerate(node):
            if isinstance(item, str) and TAG.match(item):
                fail(f"{where(module, path)}: {trail}[{index}] is the tag {item} inside a "
                     f"list. A set is a tag or a list of ids, never a list with a tag in "
                     f"it — put the tag in a tag of its own and name that")
            tag_in_list(module, path, item, f"{trail}[{index}]")
    elif isinstance(node, dict):
        for key, value in node.items():
            tag_in_list(module, path, value, f"{trail}.{key}")


for module, path in files:
    tidy = path.replace(os.sep, "/")
    if "/tags/" in tidy or "/recipe/" in tidy or "/advancement" in tidy:
        continue          # tag files may name tags; recipes hold no sets at all
    try:
        content = json.load(open(path, encoding="utf-8"))
    except json.JSONDecodeError as broken:
        fail(f"{where(module, path)}: is not valid JSON — {broken}")
        continue
    tag_in_list(module, path, content)

# ---------------------------------------------------------------------------
# 2. Enchantment effects take the shape the jar says
# ---------------------------------------------------------------------------

enchantments = 0
for module, path in files:
    if registry_of(path) != "enchantment":
        continue
    enchantments += 1
    content = json.load(open(path, encoding="utf-8"))
    for key, value in (content.get("effects") or {}).items():
        shape = SHAPES.get(key)
        if shape is None:
            fail(f"{where(module, path)}: {key} is not an enchantment effect component "
                 f"this version has, so it is silently ignored")
            continue
        if shape == "object" and isinstance(value, list):
            fail(f"{where(module, path)}: {key} is a single effect in the jar and is "
                 f"written here as a list, which fails when the world is created")
        elif shape in ("conditional list", "plain list") and not isinstance(value, list):
            fail(f"{where(module, path)}: {key} is a list in the jar and is written here "
                 f"as one value")
        elif shape == "conditional list":
            for index, item in enumerate(value):
                if isinstance(item, dict) and "effect" not in item:
                    fail(f"{where(module, path)}: {key}[{index}] has no 'effect' — a "
                         f"conditional effect is an effect and an optional requirement")

# ---------------------------------------------------------------------------
# 3. Worldgen: real feature types, and references that resolve
# ---------------------------------------------------------------------------

configured = placed = 0
for module, path in files:
    registry = registry_of(path)
    if registry not in ("worldgen/configured_feature", "worldgen/placed_feature"):
        continue
    content = json.load(open(path, encoding="utf-8"))

    if registry == "worldgen/configured_feature":
        configured += 1
        kind = content.get("type")
        if kind not in FEATURES:
            fail(f"{where(module, path)}: type is {kind}, which is not a feature. "
                 f"'type' names one of the {len(FEATURES)} features the game has, not "
                 f"the file it is written in")
        config = content.get("config") or {}
        if kind == "minecraft:random_selector":
            # Its entries are PLACED features. Naming configured ones leaves
            # them unbound with an error that mentions neither this file nor
            # the word 'placed'.
            for index, entry in enumerate(config.get("features") or []):
                must_exist(module, path, entry.get("feature"), "worldgen/placed_feature",
                           f"config.features[{index}].feature")
            must_exist(module, path, config.get("default"), "worldgen/placed_feature",
                       "config.default")
        if kind == "minecraft:simple_random_selector":
            for index, entry in enumerate(config.get("features") or []):
                must_exist(module, path, entry, "worldgen/placed_feature",
                           f"config.features[{index}]")
    else:
        placed += 1
        must_exist(module, path, content.get("feature"), "worldgen/configured_feature",
                   "feature")

# ---------------------------------------------------------------------------
# 4. Biome modifiers place placed features
# ---------------------------------------------------------------------------

modifiers = 0
for module, path in files:
    if registry_of(path) != "neoforge/biome_modifier":
        continue
    modifiers += 1
    content = json.load(open(path, encoding="utf-8"))
    features = content.get("features")
    for index, entry in enumerate(features if isinstance(features, list) else []):
        must_exist(module, path, entry, "worldgen/placed_feature", f"features[{index}]")
    biomes = content.get("biomes")
    if isinstance(biomes, str) and biomes.startswith("#"):
        namespace, rest = biomes[1:].split(":", 1) if ":" in biomes[1:] else ("minecraft", biomes[1:])
        if namespace in OURS and (namespace, "tags/worldgen/biome", rest) not in shipped:
            fail(f"{where(module, path)}: biomes names the tag {biomes}, which this "
                 f"repository does not ship")

if problems:
    print("datapack           : FAILED")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print(f"datapack           : {len(files)} data file(s) — {enchantments} enchantment(s) "
      f"against {len(SHAPES)} effect components read from the jar, {configured} configured "
      f"and {placed} placed feature(s) against {len(FEATURES)} real features, "
      f"{modifiers} biome modifier(s), and no tag written inside a list")
