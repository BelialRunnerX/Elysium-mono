#!/usr/bin/env python3
"""
Every id our modules register, listed for the load test to stub.

    python3 tools/loadtest/ids.py > tools/loadtest/ids.txt

The load test boots the game's registries outside the launcher and then loads
our data the way the game will. Our *data* can be loaded that way; our *code*
cannot, because it needs FML. So the blocks, items and entities our data refers
to have to be put into the registries some other way, and this is where the
list of them comes from.

Derived from what we ship rather than from the Java: a block with a
blockstate file is a block the game will have, and reading the resources
rather than the registration code means this cannot disagree with the jar.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))


def modules():
    for name in sorted(os.listdir(ROOT)):
        res = os.path.join(ROOT, name, "src", "main", "resources")
        if name.startswith("elysium-") and os.path.isdir(res):
            yield name, res


def listing(root, *parts):
    path = os.path.join(root, *parts)
    if not os.path.isdir(path):
        return []
    return sorted(name[:-5] for name in os.listdir(path) if name.endswith(".json"))


def namespaces(res):
    assets = os.path.join(res, "assets")
    if not os.path.isdir(assets):
        return []
    return [n for n in sorted(os.listdir(assets)) if n.startswith("elysium")]


out = []
seen = set()


def add(registry, namespace, name, extra=""):
    key = (registry, f"{namespace}:{name}")
    if key not in seen:
        seen.add(key)
        out.append((registry, f"{namespace}:{name}", extra))


def properties(path):
    """
    A block's properties and their values, read out of its blockstate.

    "type=bottom", "type=double" and "type=top" say that this block has a
    property called type whose values include bottom, double and top — which
    is all a stand-in needs to answer the same questions the real block would.
    Written as "type=bottom,double,top|waterlogged=false,true".
    """
    try:
        content = json.load(open(path, encoding="utf-8"))
    except Exception:
        return ""
    found = {}
    keys = list(content.get("variants", {}).keys())
    for entry in content.get("multipart", []):
        when = entry.get("when", {})
        for branch in (when.get("OR") or when.get("AND") or [when]):
            for prop, value in branch.items():
                if prop in ("OR", "AND"):
                    continue
                found.setdefault(prop, set()).update(str(value).split("|"))
    for key in keys:
        if not key:
            continue
        for pair in key.split(","):
            if "=" not in pair:
                continue
            prop, value = pair.split("=", 1)
            found.setdefault(prop.strip(), set()).add(value.strip())
    return "|".join(f"{prop}={','.join(sorted(values))}"
                    for prop, values in sorted(found.items()) if values)


LANG_ENTITY = re.compile(r"^entity\.([a-z0-9_]+)\.([a-z0-9_/]+)$")
LANG_EFFECT = re.compile(r"^effect\.([a-z0-9_]+)\.([a-z0-9_]+)$")

for module, res in modules():
    for namespace in namespaces(res):
        assets = os.path.join(res, "assets", namespace)
        # A block is a thing with a blockstate — and the variant keys in that
        # file are exactly the block's properties and their values, which is
        # what a stand-in needs to have or every loot table that checks one
        # fails against it.
        states = os.path.join(assets, "blockstates")
        for name in listing(assets, "blockstates"):
            add("block", namespace, name, properties(os.path.join(states, name + ".json")))
        # An item is a thing with an item model.
        for name in listing(assets, "models", "item"):
            add("item", namespace, name)
        # Entities and effects only appear in the language file, which is the
        # one place every one of them is named.
        lang = os.path.join(assets, "lang", "en_us.json")
        if os.path.isfile(lang):
            for key in json.load(open(lang, encoding="utf-8")):
                entity = LANG_ENTITY.match(key)
                if entity and entity.group(1).startswith("elysium"):
                    add("entity_type", entity.group(1), entity.group(2))
                effect = LANG_EFFECT.match(key)
                if effect and effect.group(1).startswith("elysium"):
                    add("mob_effect", effect.group(1), effect.group(2))
        sounds = os.path.join(assets, "sounds.json")
        if os.path.isfile(sounds):
            for name in json.load(open(sounds, encoding="utf-8")):
                add("sound_event", namespace, name)

# ---------------------------------------------------------------------------
# The types our Java registers
# ---------------------------------------------------------------------------
#
# A placement modifier, a structure processor, a feature: things our code adds
# to a registry, which our data then names. The harness never runs that code,
# so without these every file using one fails to parse and everything
# referring to it comes back unbound — which looks exactly like a real bug and
# is not one.

#: Which registry a "type" in our own namespace belongs to, by the folder the
#: file sits in.
BY_FOLDER = {
    "worldgen/placed_feature": "placement_modifier_type",
    "worldgen/processor_list": "structure_processor_type",
    "worldgen/configured_feature": "feature",
    "worldgen/structure": "structure_type",
    "worldgen/template_pool": "structure_pool_element_type",
}

OURS = re.compile(r'"type"\s*:\s*"(elysium[a-z]*:[a-z0-9_/]+)"')

for module, res in modules():
    data = os.path.join(res, "data")
    if not os.path.isdir(data):
        continue
    for dirpath, _, filenames in os.walk(data):
        for name in filenames:
            if not name.endswith(".json"):
                continue
            path = os.path.join(dirpath, name)
            rel = os.path.relpath(path, data).replace(os.sep, "/").split("/")
            folder = "/".join(rel[1:-1])
            registry = BY_FOLDER.get(folder)
            if registry is None:
                continue
            for found in OURS.findall(open(path, encoding="utf-8").read()):
                namespace, rest = found.split(":", 1)
                add(registry, namespace, rest)

# Data component types: ours appear in loot tables that copy a block entity's
# components onto the item it drops, and are registered by code this harness
# never runs.
COMPONENT = re.compile(r'"(?:include|exclude)"\s*:\s*\[([^\]]*)\]', re.S)

for module, res in modules():
    data = os.path.join(res, "data")
    if not os.path.isdir(data):
        continue
    for dirpath, _, filenames in os.walk(data):
        for name in filenames:
            if not name.endswith(".json"):
                continue
            text = open(os.path.join(dirpath, name), encoding="utf-8").read()
            if "copy_components" not in text:
                continue
            for block in COMPONENT.findall(text):
                for found in re.findall(r'"(elysium[a-z]*):([a-z0-9_/]+)"', block):
                    add("data_component_type", found[0], found[1])

for registry, identifier, extra in out:
    print(f"{registry}\t{identifier}\t{extra}")
print(f"# {len(out)} ids", file=sys.stderr)
