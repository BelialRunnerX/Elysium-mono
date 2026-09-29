#!/usr/bin/env python3
"""
Everything the pack adds can be had in survival.

    python3 check_obtainable.py [--list] [--why <id>]

Dylan, 29 September 2026: *"All content should be available and accessible
via survival."* The questbook had just been drafted over every block, item
and creature in the catalogue, and the drafting found what no check had: the
first arcana shard came from nowhere (every shard came from a cluster, every
cluster from a budding block, every budding block from shards), the
arsenal's dig sites were never placed in any world, the Vale had no way in
but a command, and pigeons never spawned. Each looked fine on its own - a
recipe here, a loot table there - and was unreachable as a whole.

So this walks the pack forward from nothing, the way a player does. It
starts from the base game's world (vanilla's own items and creatures, the
overworld, the nether and the end) and repeats until nothing new is reached:

* a **recipe** makes its result once every ingredient is reached (a tag is
  reached when any member is);
* a **block** that is reached (placed from its item, or put in the world by
  a feature or a structure in a reached dimension) opens its loot table;
* a **creature** that is reached (spawned by a biome modifier, a biome, a
  structure or a declared source, in a reached dimension) opens its loot
  table;
* a **chest or gameplay table** opens when something reached refers to it,
  and a **loot modifier** adds to a table once that table is reached;
* a **feature** places its blocks, and a **structure** its templates' blocks,
  chests and spawners, in the biomes it names - in the dimensions a player
  can reach;
* a **dimension** is reached through a declared way in, once what that way
  needs is reached.

What only code hands out - a join gift, a machine with no recipe file, a
creature that summons another - is declared in SOURCES below, each with the
file and the words in it that do the handing out, so the declaration fails
the day the code stops doing it. Anything the catalogue lists that the walk
never reaches, and that is not excused in NOT_ITEMS with a reason (spawn
eggs, sprite-only models, projectiles), is a failure: content a survival
player cannot have.
"""
import gzip
import io
import json
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import nbtlite  # noqa: E402

MC_JAR = os.environ.get("MC_JAR", "C:/tmp/mc/libs/vanilla-assets.jar")
NEOFORGE_JAR = os.environ.get(
    "NEOFORGE_JAR", "C:/ClaudeValoria/Valoria/build/moddev/artifacts/neoforge-21.1.251.jar")

# The mods beside the pack whose ids our recipes and tags may name, and whose
# content the pack does not answer for.
OTHER_MODS = {"minecraft", "neoforge", "c", "curios", "minecolonies", "structurize", "domum_ornamentum",
              "blockui", "jei"}

# Vanilla things a survival player cannot have. Everything else from the base
# game is taken as reached: this checks the pack, not the game.
VANILLA_UNREACHABLE = re.compile(
    r"^minecraft:(.*_spawn_egg|bedrock|barrier|command_block.*|chain_command_block|repeating_command_block|"
    r"structure_block|structure_void|jigsaw|light|debug_stick|knowledge_book|petrified_oak_slab|"
    r"budding_amethyst|reinforced_deepslate|spawner|trial_spawner|vault|end_portal_frame|farmland|"
    r"dirt_path|frogspawn|player_head|infested_.*|chorus_plant|bundle|test_block|test_instance_block)$")

# Catalogued ids that are not things a player holds or meets, and why.
NOT_ITEMS = [
    (r"_pulling_\d$", "a bow's drawing-back sprite, a model and not an item"),
    (r"^elysiumwilds:biome_compass_\d\d$", "the compass needle's sixteen frames, models and not items"),
    (r"_spawn_egg$", "a spawn egg: creative in the base game too (capturing drops them in survival)"),
]

problems = []


def fail(message):
    problems.append(message)


# ---------------------------------------------------------------------------
# Reading the tree and the jars
# ---------------------------------------------------------------------------

MODULES = sorted(d for d in os.listdir(ROOT)
                 if d.startswith("elysium-") and os.path.isdir(os.path.join(ROOT, d, "src/main/resources")))


def data_files():
    """Every data file in every module: (namespace, relative path under the namespace, absolute path)."""
    for module in MODULES:
        base = os.path.join(ROOT, module, "src/main/resources/data")
        if not os.path.isdir(base):
            continue
        for namespace in os.listdir(base):
            ns_dir = os.path.join(base, namespace)
            for dirpath, _, files in os.walk(ns_dir):
                for name in files:
                    full = os.path.join(dirpath, name)
                    rel = os.path.relpath(full, ns_dir).replace(os.sep, "/")
                    yield module, namespace, rel, full


def jar_files(path, prefix):
    if not os.path.isfile(path):
        return
    with zipfile.ZipFile(path) as jar:
        for name in jar.namelist():
            if name.startswith(prefix) and not name.endswith("/"):
                yield name, jar.read(name)


def load_json(raw):
    try:
        return json.loads(raw)
    except ValueError:
        return None


def ident(value):
    """A resource id with its namespace; a bare path is the base game's."""
    return value if ":" in value else "minecraft:" + value


TAGS = {"item": {}, "block": {}, "entity_type": {}, "worldgen/biome": {}}


# The block that runs each machine recipe type: a recipe is only a way to its
# result once the machine can be had as well as the ingredients.
RECIPE_MACHINES = {
    "elysiumindustry:crushing": ["elysiumindustry:crusher"],
    "elysiumindustry:rolling": ["elysiumindustry:rolling_mill"],
    "elysiumindustry:smelting": ["elysiumindustry:arc_furnace"],
    "elysiumindustry:separating": ["elysiumindustry:separator"],
    "elysiumkinetics:milling": ["elysiumkinetics:millstone"],
    "elysiumkinetics:sawing": ["elysiumkinetics:saw"],
    "elysiumkinetics:mixing": ["elysiumkinetics:mixer"],
    "elysiumkinetics:pressing": ["elysiumkinetics:press"],
    "elysiumkinetics:refining": ["elysiumkinetics:refinery"],
    "elysiumfoundry:alloying": ["elysiumfoundry:alloy_smelter"],
    "elysiumfoundry:refining": ["elysiumfoundry:refinery"],
    "elysiumatomics:enriching": ["elysiumatomics:enricher"],
    "elysiumatomics:reacting": ["elysiumatomics:reaction_chamber"],
    "elysiumatomics:centrifuging": ["elysiumatomics:centrifuge"],
    "elysiumsolarpunk:farming": ["elysiumsolarpunk:aquaponics_tank"],
    "elysiumsolarpunk:pressing": ["elysiumsolarpunk:mycelium_press"],
    "elysiumsolarpunk:weaving": ["elysiumsolarpunk:fibre_loom"],
}
STATIONS = {"board": "cutting_board", "oven": "stone_oven", "pot": "cooking_pot", "skillet": "skillet"}


def machine_recipes(namespace, kind, doc):
    """A data-driven machine's recipe, in the shape recipe_parts reads, with its machine."""
    if not isinstance(doc, dict):
        return []
    if kind == "cutting":
        return [{"input": {"item": ident(doc.get("input", ""))}, "__machine__": [f"{namespace}:cutting_board"],
                 "result": [{"id": y.get("item")} for y in doc.get("yields", []) if isinstance(y, dict)]}]
    if kind in ("cooking", "larder"):
        station = STATIONS.get(doc.get("station", ""), doc.get("station", ""))
        listed = doc.get("ingredients", []) if kind == "cooking" else doc.get("of", [])
        return [{"ingredients": [{"item": ident(i)} for i in listed if isinstance(i, str)],
                 "__machine__": [f"{namespace}:{station}"], "result": {"id": ident(doc.get("result", ""))}}]
    if kind == "botany_sprout":
        return [{"input": {"item": ident(doc.get("seed", ""))}, "__machine__": [f"{namespace}:pot"],
                 "result": {"id": ident(doc.get("drop", ""))}}]
    if kind == "awakening":
        out = []
        for row in doc.get("awakenings", []):
            takes = [row.get("takes"), row.get("essence")]
            out.append({"ingredients": [{"item": f"{namespace}:{t}"} for t in takes if t],
                        "__machine__": [f"{namespace}:awakening_altar"],
                        "result": {"id": f"{namespace}:{row.get('makes')}"}})
        return out
    return []
RECIPES = []            # (id, module, inputs as list of alternatives, outputs, type)
LOOT = {}               # table id -> json
LOOT_MODIFIERS = []     # (id, file, json)
LIVE_MODIFIERS = set()  # the modifiers a global_loot_modifiers.json lists: the only ones the game applies
FEATURES = {}           # configured feature id -> json
PLACED = {}             # placed feature id -> json
BIOME_MODS = []         # json of every biome modifier
BIOMES = {}             # our biome id -> json
DIMENSIONS = {}         # our dimension id -> json
PRESETS = {}            # world preset id -> json (the wilds override the base game's)
NOISE = {}              # noise settings id -> json
STRUCTURES = {}         # structure id -> json
POOLS = {}              # template pool id -> json
PROCESSORS = {}         # processor list id -> json
TEMPLATES = {}          # template id -> absolute path of its .nbt
LANG = {}               # key -> text, every module
CATALOGUE = {}


def tag_kind(rel):
    for kind in ("worldgen/biome", "entity_type", "item", "block"):
        for folder in (kind, kind + "s"):
            if rel.startswith("tags/" + folder + "/"):
                return kind, rel[len("tags/" + folder + "/"):-len(".json")]
    return None, None


def read_tag(kind, tag_id, doc):
    if not isinstance(doc, dict):
        return
    members = TAGS[kind].setdefault(tag_id, set())
    if doc.get("replace"):
        members.clear()
    for value in doc.get("values", []):
        if isinstance(value, dict):
            value = value.get("id", "")
        if value:
            members.add(value if value.startswith("#") else ident(value))


for module, namespace, rel, full in data_files():
    if not rel.endswith((".json", ".nbt")):
        continue
    if rel.endswith(".nbt"):
        if rel.startswith("structure/") or rel.startswith("structures/"):
            TEMPLATES[f"{namespace}:{rel.split('/', 1)[1][:-4]}"] = full
        continue
    doc = load_json(open(full, encoding="utf-8").read())
    if doc is None:
        continue
    rid = f"{namespace}:{rel.split('/', 1)[1][:-5]}" if "/" in rel else f"{namespace}:{rel[:-5]}"
    kind, tag_path = tag_kind(rel)
    if kind:
        read_tag(kind, f"{namespace}:{tag_path}", doc)
    elif rel.startswith(("recipe/", "recipes/")):
        RECIPES.append((rid, module, doc))
    elif rel.startswith(("cutting/", "cooking/", "larder/", "botany_sprout/", "awakening/")):
        for converted in machine_recipes(namespace, rel.split("/", 1)[0], doc):
            RECIPES.append((rid, module, converted))
    elif rel.startswith("loot_table/"):
        LOOT[f"{namespace}:{rel[len('loot_table/'):-5]}"] = doc
    elif rel.startswith("loot_modifiers/"):
        if rel.endswith("global_loot_modifiers.json"):
            if namespace == "neoforge":
                for entry in doc.get("entries", []):
                    if isinstance(entry, str):
                        LIVE_MODIFIERS.add(ident(entry))
        else:
            LOOT_MODIFIERS.append((f"{namespace}:{rel[len('loot_modifiers/'):-5]}", full, doc))
    elif rel.startswith("worldgen/configured_feature/"):
        FEATURES[f"{namespace}:{rel[len('worldgen/configured_feature/'):-5]}"] = doc
    elif rel.startswith("worldgen/placed_feature/"):
        PLACED[f"{namespace}:{rel[len('worldgen/placed_feature/'):-5]}"] = doc
    elif rel.startswith("neoforge/biome_modifier/"):
        BIOME_MODS.append((f"{namespace}:{rel[len('neoforge/biome_modifier/'):-5]}", doc))
    elif rel.startswith("worldgen/biome/"):
        BIOMES[f"{namespace}:{rel[len('worldgen/biome/'):-5]}"] = doc
    elif rel.startswith("dimension/"):
        DIMENSIONS[f"{namespace}:{rel[len('dimension/'):-5]}"] = doc
    elif rel.startswith("worldgen/world_preset/"):
        PRESETS[f"{namespace}:{rel[len('worldgen/world_preset/'):-5]}"] = doc
    elif rel.startswith("worldgen/noise_settings/"):
        NOISE[f"{namespace}:{rel[len('worldgen/noise_settings/'):-5]}"] = doc
    elif rel.startswith("worldgen/structure/"):
        STRUCTURES[f"{namespace}:{rel[len('worldgen/structure/'):-5]}"] = doc
    elif rel.startswith("worldgen/template_pool/"):
        POOLS[f"{namespace}:{rel[len('worldgen/template_pool/'):-5]}"] = doc
    elif rel.startswith("worldgen/processor_list/"):
        PROCESSORS[f"{namespace}:{rel[len('worldgen/processor_list/'):-5]}"] = doc

for module in MODULES:
    assets = os.path.join(ROOT, module, "src/main/resources/assets")
    if os.path.isdir(assets):
        for namespace in os.listdir(assets):
            path = os.path.join(assets, namespace, "lang/en_us.json")
            if os.path.isfile(path):
                LANG.update(load_json(open(path, encoding="utf-8").read()) or {})

VANILLA_ITEMS = set()
VANILLA_BIOME_DIMS = {}
for jar in (MC_JAR, NEOFORGE_JAR):
    for name, raw in jar_files(jar, "data/"):
        parts = name.split("/")
        namespace, rel = parts[1], "/".join(parts[2:])
        kind, tag_path = tag_kind(rel)
        if kind and rel.endswith(".json"):
            read_tag(kind, f"{namespace}:{tag_path}", load_json(raw))
        elif rel.startswith("loot_table/") and rel.endswith(".json") and namespace == "minecraft":
            LOOT.setdefault(f"minecraft:{rel[len('loot_table/'):-5]}", load_json(raw))
for name, _ in jar_files(MC_JAR, "assets/minecraft/models/item/"):
    VANILLA_ITEMS.add("minecraft:" + name.rsplit("/", 1)[1][:-5])

catalogue_path = os.path.join(ROOT, "docs/features.json")
if not os.path.isfile(catalogue_path):
    print("docs/features.json is missing: run tools/catalogue.py")
    sys.exit(1)
for entry in json.load(open(catalogue_path, encoding="utf-8")).values():
    modid = entry["modid"]
    for kind in ("Blocks", "Items", "Creatures", "Dimensions"):
        for row in entry.get("kinds", {}).get(kind, []):
            full = row[0] if ":" in row[0] else f"{modid}:{row[0]}"
            CATALOGUE.setdefault(full, set()).add(kind)


def members(kind, tag_id, seen=None):
    """Every id a tag resolves to, through nested tags."""
    seen = seen or set()
    if tag_id in seen:
        return set()
    seen.add(tag_id)
    out = set()
    for value in TAGS[kind].get(tag_id, ()):
        if value.startswith("#"):
            out |= members(kind, value[1:], seen)
        else:
            out.add(value)
    return out


# ---------------------------------------------------------------------------
# What code hands out: declared, each pinned to the code that does it
# ---------------------------------------------------------------------------
#
# (what, needs, file, words in that file, why). "what" is an item, block,
# creature or dimension id, or a pattern in slashes; "needs" are ids (or #tags)
# that must be reached first. The file must exist and hold the words, so a
# source that is deleted from the code is deleted from here by the build.

SOURCES = []


def declared(what, needs, path, words, why):
    SOURCES.append((what, list(needs), path, words, why))


# The world itself.
declared("dimension:minecraft:overworld", [], None, None, "where a player starts")
declared("dimension:minecraft:the_nether", ["minecraft:obsidian", "minecraft:flint_and_steel"], None, None,
         "a nether portal")
declared("dimension:minecraft:the_end", ["minecraft:ender_eye"], None, None, "a stronghold's portal")

def load_sources(path):
    import importlib.util
    spec = importlib.util.spec_from_file_location(os.path.basename(path)[:-3], path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.declare(declared)


_sources_file = os.path.join(ROOT, "tools", "obtainable_sources.py")
if os.path.isfile(_sources_file):
    load_sources(_sources_file)
for _extra in filter(None, os.environ.get("OBTAINABLE_SOURCES_EXTRA", "").split(os.pathsep)):
    load_sources(_extra)


# ---------------------------------------------------------------------------
# Reading a recipe, a loot table, a feature, a template
# ---------------------------------------------------------------------------

OUTPUT_KEYS = ("result", "results", "output", "outputs", "byproduct", "byproducts", "product")


def item_refs(node):
    """Every item id or #tag a JSON fragment names as an ingredient."""
    found = []
    if isinstance(node, dict):
        if "tag" in node and isinstance(node["tag"], str):
            found.append("#" + node["tag"])
        elif "item" in node and isinstance(node["item"], str):
            found.append(ident(node["item"]))
        elif "id" in node and isinstance(node["id"], str) and ":" in node["id"]:
            found.append(node["id"])
        else:
            for key, value in node.items():
                if key in ("type", "neoforge:conditions", "conditions", "category", "group", "pattern"):
                    continue
                found.extend(item_refs(value))
    elif isinstance(node, list):
        for value in node:
            found.extend(item_refs(value))
    return found


def recipe_parts(doc):
    """The ingredient slots (each a list of alternatives) and the outputs of a recipe."""
    outputs = []
    slots = []
    for key, value in doc.items():
        if key in OUTPUT_KEYS:
            if isinstance(value, str):
                outputs.append(ident(value))
            else:
                outputs.extend(r for r in item_refs(value) if not r.startswith("#"))
        elif key in ("type", "__machine__", "neoforge:conditions", "conditions", "category", "group", "pattern",
                     "show_notification",
                     "experience", "cookingtime", "ticks", "time", "energy", "pace", "first_count", "second_count",
                     "input_count"):
            continue
        elif key == "key" and isinstance(value, dict):
            for slot in value.values():
                alternatives = item_refs(slot)
                if alternatives:
                    slots.append(alternatives)
        elif key in ("ingredients", "inputs") and isinstance(value, list):
            for slot in value:
                alternatives = item_refs(slot)
                if alternatives:
                    slots.append(alternatives)
        else:
            alternatives = item_refs(value)
            if alternatives:
                slots.append(alternatives)
    return slots, outputs


def loot_parts(node, items, tables, tags):
    if isinstance(node, dict):
        kind = node.get("type", "")
        if kind in ("minecraft:item", "item") and isinstance(node.get("name"), str):
            items.add(ident(node["name"]))
        elif kind in ("minecraft:loot_table", "loot_table"):
            value = node.get("value", node.get("name"))
            if isinstance(value, str):
                tables.add(ident(value))
        elif kind in ("minecraft:tag", "tag") and isinstance(node.get("name"), str):
            tags.add(ident(node["name"]))
        for key, value in node.items():
            if key in ("conditions", "functions"):
                continue
            loot_parts(value, items, tables, tags)
    elif isinstance(node, list):
        for value in node:
            loot_parts(value, items, tables, tags)


def block_names(node, out):
    """Every block a worldgen fragment names as a block state."""
    if isinstance(node, dict):
        if isinstance(node.get("Name"), str):
            out.add(ident(node["Name"]))
        for value in node.values():
            block_names(value, out)
    elif isinstance(node, list):
        for value in node:
            block_names(value, out)


def feature_refs(node, out):
    """Every configured or placed feature a fragment names."""
    if isinstance(node, str):
        out.add(node)
    elif isinstance(node, dict):
        for key, value in node.items():
            if key in ("feature", "features", "placed_feature"):
                feature_refs(value, out)
            elif isinstance(value, (dict, list)):
                feature_refs(value, out)
    elif isinstance(node, list):
        for value in node:
            feature_refs(value, out)


def biome_set(value):
    """The biomes a biome modifier or structure names, resolved through tags."""
    out = set()
    if isinstance(value, str):
        value = [value]
    for one in value or []:
        if not isinstance(one, str):
            continue
        if one.startswith("#"):
            out |= members("worldgen/biome", one[1:])
        else:
            out.add(ident(one))
    return out


TEMPLATE_CACHE = {}


def template_contents(template_id):
    """The blocks, chest tables and spawner creatures a structure template holds."""
    if template_id in TEMPLATE_CACHE:
        return TEMPLATE_CACHE[template_id]
    blocks, tables, creatures = set(), set(), set()
    path = TEMPLATES.get(template_id)
    if path:
        try:
            root = nbtlite.read(path)
        except Exception:  # noqa: BLE001 - a template that does not parse holds nothing we can vouch for
            root = {}
        palettes = root.get("palettes") or ([root.get("palette")] if root.get("palette") else [])
        for palette in palettes:
            for state in palette or []:
                if isinstance(state, dict) and isinstance(state.get("Name"), str):
                    blocks.add(ident(state["Name"]))
        for block in root.get("blocks", []) or []:
            nbt = block.get("nbt") if isinstance(block, dict) else None
            if isinstance(nbt, dict):
                if isinstance(nbt.get("LootTable"), str):
                    tables.add(ident(nbt["LootTable"]))
                spawn = nbt.get("SpawnData", {})
                entity = spawn.get("entity", {}) if isinstance(spawn, dict) else {}
                if isinstance(entity, dict) and isinstance(entity.get("id"), str):
                    creatures.add(ident(entity["id"]))
        for entity in root.get("entities", []) or []:
            nbt = entity.get("nbt") if isinstance(entity, dict) else None
            if isinstance(nbt, dict) and isinstance(nbt.get("id"), str):
                creatures.add(ident(nbt["id"]))
    TEMPLATE_CACHE[template_id] = (blocks, tables, creatures)
    return TEMPLATE_CACHE[template_id]


def pool_templates(pool_id, seen=None):
    seen = seen or set()
    if pool_id in seen or pool_id not in POOLS:
        return set()
    seen.add(pool_id)
    out = set()
    doc = POOLS[pool_id]
    for element in doc.get("elements", []):
        el = element.get("element", {})
        if isinstance(el.get("location"), str):
            out.add(ident(el["location"]))
        for sub in el.get("elements", []) or []:
            if isinstance(sub.get("location"), str):
                out.add(ident(sub["location"]))
    fallback = doc.get("fallback")
    if isinstance(fallback, str):
        out |= pool_templates(ident(fallback), seen)
    return out


def dimension_biomes(doc):
    out = set()
    source = (doc.get("generator") or {}).get("biome_source") or {}
    if isinstance(source.get("biome"), str):
        out.add(ident(source["biome"]))
    for entry in source.get("biomes", []) or []:
        if isinstance(entry, dict) and isinstance(entry.get("biome"), str):
            out.add(ident(entry["biome"]))
        elif isinstance(entry, str):
            out.add(ident(entry))
    return out


# ---------------------------------------------------------------------------
# The walk
# ---------------------------------------------------------------------------

reached = set()          # item and block ids
creatures = set()        # entity ids
dimensions = set()       # dimension ids
tables = set()           # loot table ids opened
placed_blocks = set()    # blocks the world puts down, for their tables
why = {}                 # id -> how it was first reached


def reach(thing, how, into=None):
    target = reached if into is None else into
    if thing not in target:
        target.add(thing)
        kind = "item" if into is None else ("creature" if into is creatures else
                                            "dimension" if into is dimensions else "placed")
        why.setdefault(thing, {}).setdefault(kind, how)
        return True
    return False


def have(ref):
    if ref.startswith("#"):
        tag = ref[1:]
        return any(m in reached or (m.startswith("minecraft:") and vanilla_ok(m))
                   for m in members("item", tag) | members("block", tag))
    return ref in reached or (ref.startswith("minecraft:") and vanilla_ok(ref))


def vanilla_ok(item):
    return item in VANILLA_ITEMS and not VANILLA_UNREACHABLE.match(item)


# The base game's creatures that a survival world does not simply spawn.
VANILLA_CREATURE_UNREACHABLE = {"minecraft:giant", "minecraft:illusioner", "minecraft:zombie_horse"}


def need_met(need):
    """One entry of a declared source's needs: an item or #tag, a creature, a dimension or a placed block."""
    if need.startswith("dimension:"):
        return need[len("dimension:"):] in dimensions
    if need.startswith("creature:"):
        creature = need[len("creature:"):]
        return creature in creatures or (creature.startswith("minecraft:")
                                         and creature not in VANILLA_CREATURE_UNREACHABLE)
    if need.startswith("placed:"):
        return need[len("placed:"):] in placed_blocks
    return have(need) or need in placed_blocks


def machine_ok(doc):
    """A recipe's machine, when it has one, can be had."""
    machines = doc.get("__machine__") or RECIPE_MACHINES.get(doc.get("type", ""))
    return not machines or any(have(m) or m in placed_blocks for m in machines)


# Our biomes, by the dimensions they are in; the base game's, by its tags.
BIOME_DIMS = {}
for tag, dim in (("minecraft:is_overworld", "minecraft:overworld"), ("minecraft:is_nether", "minecraft:the_nether"),
                 ("minecraft:is_end", "minecraft:the_end")):
    for biome in members("worldgen/biome", tag):
        BIOME_DIMS.setdefault(biome, set()).add(dim)
for dim, doc in DIMENSIONS.items():
    for biome in dimension_biomes(doc):
        BIOME_DIMS.setdefault(biome, set()).add(dim)
# A world preset names the biomes of each dimension a new world makes. The
# wilds override the base game's normal preset and put only their own biomes
# in the overworld, so the game's own biomes are NOT there, whatever the
# game's tags say: a feature or a spawn keyed to a tag reaches the overworld
# only if a biome of the preset carries that tag. (The wilds' biomes carried
# no tag at all until 29 September 2026: no strongholds, no way to the End.)
for preset, doc in PRESETS.items():
    for dim, entry in (doc.get("dimensions") or {}).items():
        listed = dimension_biomes(entry if isinstance(entry, dict) else {})
        if not listed:
            continue
        dim_id = ident(dim)
        if preset == "minecraft:normal":
            for biome in list(BIOME_DIMS):
                BIOME_DIMS[biome].discard(dim_id)
        for biome in listed:
            BIOME_DIMS.setdefault(biome, set()).add(dim_id)
        # The End is reached through a stronghold, and a stronghold generates in
        # any biome of minecraft:is_overworld and nowhere else. An overworld made
        # only of biomes outside that tag has no stronghold and no End.
        if preset == "minecraft:normal" and dim_id == "minecraft:overworld"                 and not any(b in members("worldgen/biome", "minecraft:is_overworld") for b in listed):
            problems.append("no biome the preset puts in the overworld is in minecraft:is_overworld, so "
                            "no stronghold generates and the End cannot be reached in survival")


def noise_blocks(dim_doc):
    """The blocks a dimension's terrain is made of: its noise settings' default block, fluid and surface rules."""
    settings = (dim_doc.get("generator") or {}).get("settings")
    doc = NOISE.get(ident(settings)) if isinstance(settings, str) else (settings if isinstance(settings, dict) else None)
    out = set()
    if doc:
        block_names(doc, out)
    return out
# Biomes that code puts in a dimension are declared as "biome:<id, #tag or
# /regex/>@<dimension>". Only declarations whose words still stand count.
KNOWN_BIOMES = set(BIOMES) | set(BIOME_DIMS)
for what, needs, path, words, reason in SOURCES:
    if what.startswith("biome:") and "@" in what and (path is None or (
            os.path.isfile(os.path.join(ROOT, path)) and words in open(os.path.join(ROOT, path), encoding="utf-8").read())):
        biome, dim = what[len("biome:"):].split("@", 1)
        if biome.startswith("#"):
            chosen = members("worldgen/biome", biome[1:])
        elif biome.startswith("/") and biome.endswith("/"):
            chosen = {b for b in KNOWN_BIOMES if re.search(biome[1:-1], b)}
        else:
            chosen = {biome}
        for one in chosen:
            BIOME_DIMS.setdefault(one, set()).add(dim)


def biome_reached(biome):
    return any(d in dimensions for d in BIOME_DIMS.get(biome, ()))


def any_biome_reached(biomes):
    return any(biome_reached(b) for b in biomes)


# Features by the biomes they are added to.
FEATURE_BIOMES = {}
for mod_id, doc in BIOME_MODS:
    if doc.get("type") == "neoforge:add_features":
        refs = set()
        feature_refs(doc.get("features"), refs)
        for placed in refs:
            FEATURE_BIOMES.setdefault(ident(placed), set()).update(biome_set(doc.get("biomes")))
for biome, doc in BIOMES.items():
    for step in doc.get("features", []) or []:
        for placed in (step if isinstance(step, list) else [step]):
            if isinstance(placed, str):
                FEATURE_BIOMES.setdefault(ident(placed), set()).add(biome)


def placed_blocks_of(placed_id, seen=None):
    """The blocks a placed feature puts down, through its configured feature and any it nests."""
    seen = seen or set()
    if placed_id in seen:
        return set()
    seen.add(placed_id)
    blocks = set()
    doc = PLACED.get(placed_id)
    configured = None
    if doc is not None:
        feature = doc.get("feature")
        if isinstance(feature, str):
            configured = FEATURES.get(ident(feature))
        elif isinstance(feature, dict):
            configured = feature
    else:
        configured = FEATURES.get(placed_id)
    if configured is not None:
        block_names(configured, blocks)
        nested = set()
        feature_refs(configured.get("config", {}), nested)
        for ref in nested:
            if ref != placed_id and (ident(ref) in PLACED or ident(ref) in FEATURES):
                blocks |= placed_blocks_of(ident(ref), seen)
    return blocks


OUR_NAMESPACES = {entry["modid"] for entry in json.load(open(catalogue_path, encoding="utf-8")).values()}
ORE_ENTRY = re.compile(r'new Entry\("([a-z0-9_]+)",\s*"([a-z0-9_]+)",\s*"[a-z_]+",\s*\d+,\s*(null|"[a-z0-9_:]+")\)')
ORE_FAMILY = {}
_ore_catalogue = os.path.join(ROOT, "elysium-ores/src/main/java/com/elysium/ores/data/OreCatalogue.java")
if os.path.isfile(_ore_catalogue):
    for _name, _family, _vanilla in ORE_ENTRY.findall(open(_ore_catalogue, encoding="utf-8").read()):
        ORE_FAMILY["elysiumores:" + _name] = (_family, None if _vanilla == "null" else _vanilla.strip('"'))
_unify = os.path.join(ROOT, "elysium-ores/src/main/java/com/elysium/ores/rules/UnifyRules.java")
_priority = re.search(r"List<String> PRIORITY = List\.of\((.*?)\);", open(_unify, encoding="utf-8").read(), re.S) \
    if os.path.isfile(_unify) else None
UNIFY_PRIORITY = re.findall(r'"([a-z0-9_]+)"', _priority.group(1)) if _priority else []
if not UNIFY_PRIORITY:
    problems.append("UnifyRules.PRIORITY could not be read, so the ores' drops cannot be modelled")
# What an ore gives, best first, read from the same rules the game reads:
# "vanilla" marks the catalogue row's own item (coal for a coal ore).
_order = re.search(r"List<String> DROP_ORDER =\s*List\.of\((.*?)\);", open(_unify, encoding="utf-8").read(), re.S) \
    if os.path.isfile(_unify) else None
DROP_ORDER = re.findall(r'"([a-z_]+)"', _order.group(1)) if _order else []
if not DROP_ORDER:
    problems.append("UnifyRules.DROP_ORDER could not be read, so the ores' drops cannot be modelled")


def canonical(ids):
    """UnifyRules.canonical: the namespace earliest in the priority, then the id first in order."""
    def rank(one):
        ns = one.split(":", 1)[0]
        return (UNIFY_PRIORITY.index(ns) if ns in UNIFY_PRIORITY else len(UNIFY_PRIORITY), one)
    return min(ids, key=rank) if ids else None


def installed(one):
    """Whether an id belongs to something the pack ships: our modules, the base game, the mods beside us."""
    namespace = one.split(":", 1)[0]
    return namespace in OUR_NAMESPACES or namespace in OTHER_MODS


def ore_drop(block):
    """UnifyRules.drop: the first kind in DROP_ORDER the family has, "vanilla" being the row's own item."""
    family, vanilla = ORE_FAMILY[block]
    for kind in DROP_ORDER:
        if kind == "vanilla":
            if vanilla:
                return vanilla
            continue
        found = {m for m in members("item", f"c:{kind}/{family}") if installed(m)}
        if found:
            return canonical(sorted(found))
    return None


def block_loot(block):
    namespace, path = block.split(":", 1)
    return f"{namespace}:blocks/{path}"


def open_table(table, how):
    if table in tables or table not in LOOT:
        return False
    tables.add(table)
    items, nested, tags = set(), set(), set()
    loot_parts(LOOT[table], items, nested, tags)
    moved = False
    for item in items:
        moved |= reach(item, f"loot {table} ({how})")
    for tag in tags:
        for member in members("item", tag):
            moved |= reach(member, f"loot {table} tag #{tag} ({how})")
    for sub in nested:
        moved |= open_table(sub, how)
    return True


def resolved(ref):
    """An id or pattern from SOURCES, against everything catalogued."""
    if ref.startswith("/") and ref.endswith("/"):
        pattern = re.compile(ref[1:-1])
        return {one for one in CATALOGUE if pattern.search(one)}
    return {ref}


def source_ok(path, words):
    if path is None:
        return True
    full = os.path.join(ROOT, path)
    if not os.path.isfile(full):
        fail(f"a declared source points at {path}, which is not there")
        return False
    if words not in open(full, encoding="utf-8").read():
        fail(f"a declared source says {path} holds {words!r}, and it does not: the code that handed "
             f"it out has changed, so the declaration is a claim nothing backs")
        return False
    return True


valid_sources = [s for s in SOURCES if source_ok(s[2], s[3])]

changed = True
rounds = 0
while changed and rounds < 200:
    changed = False
    rounds += 1
    # Declared sources.
    for what, needs, path, words, reason in valid_sources:
        if not all(need_met(n) for n in needs):
            continue
        if what.startswith("biome:"):
            continue
        if what.startswith("dimension:"):
            changed |= reach(what[len("dimension:"):], reason, dimensions)
        elif what.startswith("creature:"):
            for one in resolved(what[len("creature:"):]):
                changed |= reach(one, reason, creatures)
        elif what.startswith("table:"):
            changed |= open_table(what[len("table:"):], reason)
        elif what.startswith("placed:"):
            for one in resolved(what[len("placed:"):]):
                changed |= reach(one, reason, placed_blocks)
        else:
            for one in resolved(what):
                changed |= reach(one, reason)
    # Recipes.
    for rid, module, doc in RECIPES:
        slots, outputs = recipe_parts(doc)
        if not outputs or not machine_ok(doc):
            continue
        if all(any(have(alt) for alt in slot) for slot in slots):
            for out in outputs:
                changed |= reach(out, f"recipe {rid}")
    # The terrain of every reached dimension of ours.
    for dim, doc in DIMENSIONS.items():
        if dim in dimensions:
            for block in noise_blocks(doc):
                changed |= reach(block, f"the terrain of {dim}", placed_blocks)
    # Features and structures in reached dimensions.
    for placed_id, biomes in FEATURE_BIOMES.items():
        if any_biome_reached(biomes):
            for block in placed_blocks_of(placed_id):
                changed |= reach(block, f"worldgen {placed_id}", placed_blocks)
    for sid, doc in STRUCTURES.items():
        if not any_biome_reached(biome_set(doc.get("biomes"))):
            continue
        pool = doc.get("start_pool")
        templates = pool_templates(ident(pool)) if isinstance(pool, str) else set()
        # A jigsaw's other pools, reached from the templates' jigsaw blocks, are
        # not followed; every pool of the same namespace and prefix is taken.
        if isinstance(pool, str):
            prefix = ident(pool).rsplit("/", 1)[0]
            for other in POOLS:
                if other.startswith(prefix):
                    templates |= pool_templates(other)
        for template in templates:
            blocks, chest_tables, spawned = template_contents(template)
            for block in blocks:
                changed |= reach(block, f"structure {sid}", placed_blocks)
            for table in chest_tables:
                changed |= open_table(table, f"a chest in {sid}")
            for one in spawned:
                changed |= reach(one, f"a spawner in {sid}", creatures)
        for category in (doc.get("spawn_overrides") or {}).values():
            for spawner in (category or {}).get("spawns", []) or []:
                if isinstance(spawner, dict) and isinstance(spawner.get("type"), str):
                    changed |= reach(ident(spawner["type"]), f"spawns in {sid}", creatures)
    # Spawns.
    for mod_id, doc in BIOME_MODS:
        if doc.get("type") != "neoforge:add_spawns" or not any_biome_reached(biome_set(doc.get("biomes"))):
            continue
        spawners = doc.get("spawners")
        for spawner in (spawners if isinstance(spawners, list) else [spawners]):
            if isinstance(spawner, dict) and isinstance(spawner.get("type"), str):
                changed |= reach(ident(spawner["type"]), f"spawns by {mod_id}", creatures)
    for biome, doc in BIOMES.items():
        if not biome_reached(biome):
            continue
        for category in (doc.get("spawners") or {}).values():
            for spawner in category or []:
                if isinstance(spawner, dict) and isinstance(spawner.get("type"), str):
                    changed |= reach(ident(spawner["type"]), f"spawns in {biome}", creatures)
    # Tables: a reached block's, a reached creature's, and what refers to them.
    for block in set(reached) | set(placed_blocks):
        if ":" in block and block.split(":")[0] not in OTHER_MODS:
            changed |= open_table(block_loot(block), f"breaking {block}")
        if block in ORE_FAMILY:
            drop = ore_drop(block)
            if drop:
                changed |= reach(drop, f"mining {block} (OreDrops)")
    for creature in set(creatures):
        namespace, path = creature.split(":", 1)
        changed |= open_table(f"{namespace}:entities/{path}", f"killing {creature}")
    # The base game's own tables that a player meets anywhere: every chest,
    # fishing, archaeology, gameplay and every creature of the base game.
    for table in list(LOOT):
        if table.startswith("minecraft:") and not table.startswith("minecraft:blocks/"):
            changed |= open_table(table, "the base game")
    # Loot modifiers add to a table once it is reached.
    for mid, full, doc in LOOT_MODIFIERS:
        if mid not in LIVE_MODIFIERS:
            continue
        targets = set()
        for condition in doc.get("conditions", []) or []:
            if isinstance(condition, dict):
                for key in ("loot_table_id", "table", "id"):
                    if isinstance(condition.get(key), str):
                        targets.add(ident(condition[key]))
        if not targets or not any(t in tables for t in targets):
            continue
        adds_items, adds_tables, adds_tags = set(), set(), set()
        loot_parts(doc, adds_items, adds_tables, adds_tags)
        for key in ("item", "result"):
            if isinstance(doc.get(key), str):
                adds_items.add(ident(doc[key]))
        for key in ("table", "loot_table"):
            if isinstance(doc.get(key), str):
                adds_tables.add(ident(doc[key]))
        for item in adds_items:
            changed |= reach(item, f"loot modifier {mid}")
        for table in adds_tables:
            changed |= open_table(table, f"loot modifier {mid}")
    # A block that is reached as an item can be placed, and then broken.
    for block in list(reached):
        if block in CATALOGUE and "Blocks" in CATALOGUE[block]:
            changed |= reach(block, "placed from its item", placed_blocks)

# ---------------------------------------------------------------------------
# The verdict
# ---------------------------------------------------------------------------


def excused(thing):
    for pattern, reason in NOT_ITEMS:
        if re.search(pattern, thing):
            return reason
    namespace, path = thing.split(":", 1)
    if CATALOGUE.get(thing) == {"Items"} and not any(
            f"{kind}.{namespace}.{path}" in LANG for kind in ("item", "block")):
        return "a model with no name in any language file: an override the game switches to, not a registered item"
    return None


missing = {"Items": [], "Blocks": [], "Creatures": [], "Dimensions": []}
counted = {"Items": 0, "Blocks": 0, "Creatures": 0, "Dimensions": 0}
for thing, kinds in sorted(CATALOGUE.items()):
    namespace = thing.split(":", 1)[0]
    if namespace in OTHER_MODS:
        continue
    for kind in kinds:
        if excused(thing):
            continue
        counted[kind] += 1
        if kind == "Items":
            ok = thing in reached
        elif kind == "Blocks":
            ok = thing in reached or thing in placed_blocks
        elif kind == "Creatures":
            ok = thing in creatures
        else:
            ok = thing in dimensions
        if not ok:
            missing[kind].append(thing)

if "--blocked" in sys.argv:
    # For each id asked about (or every unreached one of a namespace), what
    # would make it and what each of those is still waiting on.
    wanted = sys.argv[sys.argv.index("--blocked") + 1]
    targets = [t for ids in missing.values() for t in ids if t == wanted or t.startswith(wanted + ":")]
    for target in targets:
        makers = []
        for rid, module, doc in RECIPES:
            slots, outputs = recipe_parts(doc)
            if target in outputs:
                waiting = [" | ".join(slot) for slot in slots if not any(have(alt) for alt in slot)]
                if not machine_ok(doc):
                    waiting.append("the machine " + " | ".join(
                        doc.get("__machine__") or RECIPE_MACHINES.get(doc.get("type", ""), [])))
                makers.append(f"recipe {rid} waits on: " + ("; ".join(waiting) or "nothing?"))
        for table, doc in LOOT.items():
            items, nested, tags = set(), set(), set()
            loot_parts(doc, items, nested, tags)
            if target in items or any(target in members("item", t) for t in tags):
                makers.append(f"loot {table} ({'opened' if table in tables else 'never opened'})")
        for what, needs, path, words, reason in valid_sources:
            if target in resolved(what.split(":", 1)[1] if what.startswith(("creature:", "placed:")) else what):
                makers.append(f"declared ({reason}) waits on: {[n for n in needs if not need_met(n)]}")
        for mod_id, doc in BIOME_MODS:
            if doc.get("type") != "neoforge:add_spawns":
                continue
            spawners = doc.get("spawners")
            listed = [sp.get("type") for sp in (spawners if isinstance(spawners, list) else [spawners])
                      if isinstance(sp, dict)]
            if target in {ident(t) for t in listed if isinstance(t, str)}:
                biomes = biome_set(doc.get("biomes"))
                dims = sorted({d for b in biomes for d in BIOME_DIMS.get(b, ())}) or ["no dimension"]
                makers.append(f"spawns by {mod_id} in {len(biomes)} biome(s) of {', '.join(dims)}"
                              + ("" if any_biome_reached(biomes) else " (none reached)"))
        print(f"{target}: " + ("; ".join(makers[:6]) if makers else "nothing in the tree makes it"))
    sys.exit(0)

if "--why" in sys.argv:
    target = sys.argv[sys.argv.index("--why") + 1]
    found = why.get(target)
    print(f"{target}: " + ("; ".join(f"as {k}: {v}" for k, v in found.items()) if found else "never reached"))
    sys.exit(0)

total_missing = sum(len(v) for v in missing.values())
if "--list" in sys.argv:
    for kind, ids in missing.items():
        by_module = {}
        for one in ids:
            by_module.setdefault(one.split(":", 1)[0], []).append(one.split(":", 1)[1])
        for module, paths in sorted(by_module.items()):
            print(f"{kind:10s} {module:20s} {len(paths):4d}: " + ", ".join(paths))

for kind, ids in missing.items():
    if ids:
        fail(f"{len(ids)} of {counted[kind]} {kind.lower()} cannot be had in survival: "
             + ", ".join(ids[:12]) + (" ..." if len(ids) > 12 else ""))

print(f"obtainable         : {sum(counted.values()) - total_missing} of {sum(counted.values())} things the pack adds "
      f"reached from a new world in {rounds} round(s) ({len(reached)} items and blocks, {len(creatures)} creatures, "
      f"{len(dimensions)} dimensions, {len(tables)} loot tables opened, {len(valid_sources)} declared sources)")
if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)
print("everything the pack adds can be had in survival")
