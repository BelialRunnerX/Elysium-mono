#!/usr/bin/env python3
"""
Every file elysium-ores ships that is a table rather than a decision.

Two halves, from one vocabulary:

  * the unification half reads tools/pack_scan.json - the c: tags as they
    actually are in the pack this was written against - and emits the tag
    files that complete every family, so a recipe written against
    c:ingots/steel accepts all five steels.

  * the ore half works out, per material, which of the pack's stones nobody
    has put that material in, and emits a block, a model, a loot table, a
    feature and a placement for each gap.

Nothing here is a decision; the decisions live in rules/OreRules.java and
rules/UnifyRules.java, and this file reads their constants rather than
restating them.
"""
import json
import os
import re
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumores"
RES = os.path.join(MOD, "src/main/resources")
DATA = os.path.join(RES, "data", NS)
ASSETS = os.path.join(RES, "assets", NS)
C = os.path.join(RES, "data", "c")
JAVA = os.path.join(MOD, "src/main/java/com/elysium/ores")

SCAN = json.load(open(os.path.join(HERE, "pack_scan.json"), encoding="utf-8"))

# ---------------------------------------------------------------------------
# The bands, read from the rules so there is one place they are defined.
# ---------------------------------------------------------------------------
RULES = open(os.path.join(JAVA, "rules/OreRules.java"), encoding="utf-8").read()


def constant(name):
    m = re.search(r"public static final \w+ " + name + r" = ([0-9.]+)F?;", RULES)
    if not m:
        raise SystemExit("OreRules has no constant " + name)
    return float(m.group(1)) if "." in m.group(1) else int(m.group(1))


COMMON, INDUSTRIAL, PRECIOUS, EXOTIC = (constant(n) for n in
                                        ("COMMON", "INDUSTRIAL", "PRECIOUS", "EXOTIC"))
BASE_SIZE = constant("BASE_SIZE")
SIZE_PER_BAND = constant("SIZE_PER_BAND")
MINIMUM_SIZE = constant("MINIMUM_SIZE")
BASE_TRIES = constant("BASE_TRIES")


def vein_size(band):
    return max(MINIMUM_SIZE, BASE_SIZE - SIZE_PER_BAND * band)


def tries(band):
    return max(1, BASE_TRIES >> band)


# ---------------------------------------------------------------------------
# The families this module fills in, and how hard they are to find.
#
# Left out on purpose: the families that belong to one mod's own progression
# and are already placed by it in every world it cares about - Mystical
# Agriculture's essences, Extreme Reactors' yellorite and its two crystal
# ores, Powah's three grades of uraninite (the plain grade is here; the dense
# and poor grades are that mod's own idea of a ladder and not ours to extend).
# ---------------------------------------------------------------------------
FAMILIES = [
    # (family, band, the vanilla item to fall back on, or None)
    ("coal",           COMMON,     "minecraft:coal"),
    ("copper",         COMMON,     None),
    ("iron",           COMMON,     None),
    ("redstone",       COMMON,     "minecraft:redstone"),
    ("lapis",          COMMON,     "minecraft:lapis_lazuli"),
    ("quartz",         COMMON,     "minecraft:quartz"),
    ("tin",            INDUSTRIAL, None),
    ("zinc",           INDUSTRIAL, None),
    ("lead",           INDUSTRIAL, None),
    ("nickel",         INDUSTRIAL, None),
    ("aluminum",       INDUSTRIAL, None),
    ("lithium",        INDUSTRIAL, None),
    ("silver",         INDUSTRIAL, None),
    ("steel",          INDUSTRIAL, None),
    ("gold",           PRECIOUS,   "minecraft:raw_gold"),
    ("diamond",        PRECIOUS,   "minecraft:diamond"),
    ("emerald",        PRECIOUS,   "minecraft:emerald"),
    ("platinum",       PRECIOUS,   None),
    ("fluorite",       PRECIOUS,   None),
    ("certus_quartz",  PRECIOUS,   None),
    ("osmium",         EXOTIC,     None),
    ("uranium",        EXOTIC,     None),
    ("uraninite",      EXOTIC,     None),
    ("crimson_iron",   EXOTIC,     None),
    ("azure_silver",   EXOTIC,     None),
    ("bort",           EXOTIC,     None),
    ("cindrium",       EXOTIC,     None),
    ("cryonite",       EXOTIC,     None),
    ("frostite",       EXOTIC,     None),
    ("lunite",         EXOTIC,     None),
    ("rime_shard",     EXOTIC,     None),
]

# ---------------------------------------------------------------------------
# The Cordon's materials (rules/CordonMaterials.java): read, never retyped.
# ---------------------------------------------------------------------------
ROSTER_SRC = open(os.path.join(JAVA, "rules/CordonMaterials.java"), encoding="utf-8").read()
ROSTER_SRC = re.sub(r"//[^\n]*", " ", re.sub(r"/\*.*?\*/", " ", ROSTER_SRC, flags=re.S))


def _call_bodies(name):
    """The argument text of every top-level call `name(...)`, parentheses balanced."""
    out = []
    for match in re.finditer(r"\b" + name + r"\(", ROSTER_SRC):
        depth, at = 0, match.end() - 1
        while at < len(ROSTER_SRC):
            if ROSTER_SRC[at] == "(":
                depth += 1
            elif ROSTER_SRC[at] == ")":
                depth -= 1
                if depth == 0:
                    break
            at += 1
        body = ROSTER_SRC[match.end():at]
        if body.strip().startswith('"'):
            out.append(body)
    return out


def roster():
    """Every material as a dict: family, kind, band, element, colour, made_of, yield."""
    rows = []
    for call in ("dug", "alloy", "found"):
        for body in _call_bodies(call):
            head = re.match(r'"([a-z_]+)",\s*(?:Kind\.(\w+),\s*)?(?:OreRules\.(\w+),\s*)?Element\.(\w+),\s*"(#[0-9a-f]{6})"', body)
            if not head:
                raise SystemExit("cannot read a %s row of CordonMaterials: %s" % (call, body[:60]))
            family, kind, band, element, colour = head.groups()
            row = {"family": family, "kind": kind or "ALLOY", "element": element, "colour": colour,
                   "band": constant(band) if band else EXOTIC, "made_of": {}, "yield": 0}
            if call == "alloy":
                tail = body[head.end():]
                # ... tool, armour, yield, then ("part", count) pairs
                pairs = re.findall(r'"([a-z_:]+)",\s*(\d+)', tail)
                row["made_of"] = {part: int(count) for part, count in pairs}
                yield_match = re.search(r'\),\s*(\d+),\s*"' if "new Plate" in tail or "new Tier" in tail
                                        else r'null,\s*(\d+),\s*"', tail)
                row["yield"] = int(yield_match.group(1)) if yield_match else 1
            rows.append(row)
    return rows


ROSTER = roster()
DROPS = [{"name": n, "element": e, "colour": c} for n, e, c in
         re.findall(r'new Drop\("([a-z_]+)",\s*Element\.(\w+),\s*"(#[0-9a-f]{6})"', ROSTER_SRC)]
PARTS = [{"name": n, "family": f, "colour": c, "takes": int(t)} for n, f, c, t in
         re.findall(r'new Part\("([a-z_]+)",\s*"([a-z_]+)",\s*"(#[0-9a-f]{6})",\s*(\d+)\)', ROSTER_SRC)]
_cloth = re.search(r"CLOTH_MADE_OF = Map\.of\((.*?)\);", ROSTER_SRC, re.S)
CLOTH_MADE_OF = {p: int(n) for p, n in re.findall(r'"([a-z_:]+)",\s*(\d+)', _cloth.group(1))} if _cloth else {}
CLOTH_YIELD = int(re.search(r"CLOTH_YIELD = (\d+);", ROSTER_SRC).group(1))
if not ROSTER or not DROPS or not PARTS or not CLOTH_MADE_OF:
    raise SystemExit("CordonMaterials.java did not parse; the roster's shape changed")

# The dug ones join the families this module fills every stone with. Nothing
# falls back on vanilla: the family's item is this module's own.
for _row in ROSTER:
    if _row["kind"] in ("METAL", "GEM"):
        FAMILIES.append((_row["family"], _row["band"], None))

# ---------------------------------------------------------------------------
# The stones the pack is made of.
#
# target   - the block or tag a feature replaces to place an ore here
# dimension- which world's biomes get the placed feature
# ---------------------------------------------------------------------------
HOSTS = [
    # (host, pretty, replace target, y-range, dimension tag, optional?)
    ("stone",     "Stone",      "#minecraft:stone_ore_replaceables",
     (-16, 112), "overworld", False),
    ("deepslate", "Deepslate",  "#minecraft:deepslate_ore_replaceables",
     (-63, 16), "overworld", False),
    ("netherrack", "Netherrack", "minecraft:netherrack",
     (10, 118), "nether", False),
    ("end_stone", "End Stone",  "minecraft:end_stone",
     (0, 96), "end", False),
    ("cinder",    "Cinder",     "elysiumspace:cinder_stone",
     (-48, 96), "cinderveil", True),
    ("frost",     "Frost",      "elysiumspace:frost_stone",
     (-48, 96), "frostfall", True),
    ("pale",      "Pale",       "elysiumspace:pale_stone",
     (-48, 96), "pale_moon", True),
]

# The biomes each dimension is made of, so a placed feature reaches all of them.
BIOMES = {
    "overworld": ["#minecraft:is_overworld"],
    "nether":    ["#minecraft:is_nether"],
    "end":       ["#minecraft:is_end"],
    "cinderveil": ["elysiumspace:cinderveil_desert",
                   "elysiumspace:cinderveil_infernal_barrens"],
    "frostfall": ["elysiumspace:frostfall_desert",
                  "elysiumspace:frostfall_ice_spikes",
                  "elysiumspace:frostfall_rocky_plains"],
    "pale_moon": ["elysiumspace:pale_moon_caves",
                  "elysiumspace:pale_moon_desert"],
}

# ---------------------------------------------------------------------------
# What the pack already has, so nothing here duplicates it.
# ---------------------------------------------------------------------------
VANILLA_STONE_AND_DEEPSLATE = {"coal", "copper", "iron", "gold", "redstone",
                               "lapis", "emerald", "diamond"}


def existing_hosts(family):
    """Which stones somebody has already put this material in."""
    have = set()
    for block in SCAN["ores"].get(family, []):
        path = block.split(":", 1)[1]
        for prefix, host in (("deepslate_", "deepslate"),
                             ("nether_", "netherrack"),
                             ("netherrack", "netherrack"),
                             ("blackstone_", "netherrack"),
                             ("end_", "end_stone"),
                             ("endstone_", "end_stone"),
                             ("cinder_", "cinder"),
                             ("frost_", "frost"),
                             ("pale_", "pale")):
            if path.startswith(prefix):
                have.add(host)
                break
        else:
            have.add("deepslate" if "deepslate" in path else "stone")
    if family in VANILLA_STONE_AND_DEEPSLATE:
        have |= {"stone", "deepslate"}
    if family == "quartz":
        have |= {"netherrack"}
    return have


GAPS = []
for family, band, fallback in FAMILIES:
    have = existing_hosts(family)
    for host, pretty, target, yrange, dimension, optional in HOSTS:
        if host in have:
            continue
        GAPS.append((family, host, band, fallback, target, yrange, dimension, optional))


def name_of(family, host):
    return f"{host}_{family}_ore"


def title(word):
    return " ".join(p.capitalize() for p in word.split("_"))


# ---------------------------------------------------------------------------
def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if isinstance(content, (dict, list)):
        content = json.dumps(content, indent=2) + "\n"
    open(path, "w", encoding="utf-8", newline="\n").write(content)


def clean():
    for sub in ("blockstates", "models", "lang"):
        shutil.rmtree(os.path.join(ASSETS, sub), ignore_errors=True)
    for sub in ("worldgen", "loot_table", "tags", "neoforge", "recipe"):
        shutil.rmtree(os.path.join(DATA, sub), ignore_errors=True)
    shutil.rmtree(C, ignore_errors=True)
    shutil.rmtree(os.path.join(RES, "data", "neoforge"), ignore_errors=True)


# ---------------------------------------------------------------------------
# Half one: the tags that complete every family.
# ---------------------------------------------------------------------------
def unify_tags():
    """
    Every variant added to its family's c: tag, as an optional entry.

    Optional because this module has no dependency on any of the mods these
    ids belong to: a tag entry for a mod that is not installed would be a
    datapack error on a vanilla-plus-Elysium world, and an optional one is
    simply not there.
    """
    written = 0
    for kind, families in SCAN["tags"].items():
        for family, members in families.items():
            namespaces = {m.split(":", 1)[0] for m in members}
            if len(namespaces) < 2:
                continue                      # not a duplication problem
            realm = "item"
            write(os.path.join(C, f"tags/{realm}/{kind}/{family}.json"), {
                "replace": False,
                "values": [{"id": m, "required": False} for m in sorted(members)]
            })
            written += 1
    # The ore blocks this module adds answer to their family too, so a recipe
    # or a mining tool written against c:ores/<family> finds them.
    by_family = {}
    for family, host, *_ in GAPS:
        by_family.setdefault(family, []).append(f"{NS}:{name_of(family, host)}")
    for family, blocks in sorted(by_family.items()):
        existing = SCAN["ores"].get(family, [])
        write(os.path.join(C, f"tags/block/ores/{family}.json"), {
            "replace": False,
            "values": [{"id": b, "required": False} for b in sorted(existing)]
                      + sorted(blocks)
        })
        written += 1
    return written


# ---------------------------------------------------------------------------
# Half two: an ore for every gap.
# ---------------------------------------------------------------------------
def ore_blocks():
    mineable = []
    needs_iron, needs_stone, needs_diamond = [], [], []
    for family, host, band, fallback, target, yrange, dimension, optional in GAPS:
        name = name_of(family, host)
        block = f"{NS}:{name}"
        mineable.append(block)
        if band >= EXOTIC:
            needs_diamond.append(block)
        elif band >= PRECIOUS:
            needs_iron.append(block)
        else:
            needs_stone.append(block)

        write(os.path.join(ASSETS, f"blockstates/{name}.json"),
              {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        write(os.path.join(ASSETS, f"models/block/{name}.json"),
              {"parent": "minecraft:block/cube_all",
               "textures": {"all": f"{NS}:block/{name}"}})
        write(os.path.join(ASSETS, f"models/item/{name}.json"),
              {"parent": f"{NS}:block/{name}"})

        # The loot table hands the block to Java, which resolves the family's
        # canonical raw material at runtime. A table naming an item outright
        # would name another mod's item, which this module never does.
        write(os.path.join(DATA, f"loot_table/blocks/{name}.json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": block}],
                "conditions": [{"condition": "minecraft:survives_explosion"}]
            }]
        })

    TOOL_TAGS["mineable"].extend(mineable)
    TOOL_TAGS["stone"].extend(needs_stone)
    TOOL_TAGS["iron"].extend(needs_iron)
    TOOL_TAGS["diamond"].extend(needs_diamond)
    return len(GAPS)


TOOL_TAGS = {"mineable": [], "stone": [], "iron": [], "diamond": []}


def loot_modifiers():
    """The one global loot modifier, listed so it runs. clean() removes the directory; this puts it back."""
    write(os.path.join(RES, "data/neoforge/loot_modifiers/global_loot_modifiers.json"),
          {"replace": False, "entries": [f"{NS}:unifying"]})


def tool_tags():
    for path, values in (("minecraft/tags/block/mineable/pickaxe.json", TOOL_TAGS["mineable"]),
                         ("minecraft/tags/block/needs_stone_tool.json", TOOL_TAGS["stone"]),
                         ("minecraft/tags/block/needs_iron_tool.json", TOOL_TAGS["iron"]),
                         ("minecraft/tags/block/needs_diamond_tool.json", TOOL_TAGS["diamond"])):
        write(os.path.join(RES, "data", path),
              {"replace": False, "values": sorted(values)})


# ---------------------------------------------------------------------------
# Half three: the Cordon's materials, from the roster.
# ---------------------------------------------------------------------------
def _item_model(name):
    write(os.path.join(ASSETS, f"models/item/{name}.json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})


def _block(name):
    write(os.path.join(ASSETS, f"blockstates/{name}.json"),
          {"variants": {"": {"model": f"{NS}:block/{name}"}}})
    write(os.path.join(ASSETS, f"models/block/{name}.json"),
          {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{name}"}})
    write(os.path.join(ASSETS, f"models/item/{name}.json"), {"parent": f"{NS}:block/{name}"})
    write(os.path.join(DATA, f"loot_table/blocks/{name}.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1,
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def _ingredient(part):
    return {"item": part if ":" in part else f"{NS}:{part}"}


def _compaction(small, big, count=9):
    """Nine small make a big, and a big is nine small again."""
    write(os.path.join(DATA, f"recipe/{big}_from_{small}.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["###", "###", "###"], "key": {"#": {"item": f"{NS}:{small}"}},
        "result": {"count": 1, "id": f"{NS}:{big}"}})
    write(os.path.join(DATA, f"recipe/{small}_from_{big}.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": f"{NS}:{big}"}],
        "result": {"count": count, "id": f"{NS}:{small}"}})


def _c_tag(kind, family, values, realm="item"):
    """A c: tag of our own members, merged with whatever unification already wrote there."""
    path = os.path.join(C, f"tags/{realm}/{kind}/{family}.json")
    existing = json.load(open(path, encoding="utf-8"))["values"] if os.path.isfile(path) else []
    for value in values:
        if value not in existing:
            existing.append(value)
    write(path, {"replace": False, "values": existing})


def cordon():
    """Items, blocks, recipes and tags for every material the roster ships."""
    items = blocks = recipes = 0
    for row in ROSTER:
        family, kind, band = row["family"], row["kind"], row["band"]
        if kind == "EXTERNAL":
            continue
        if kind == "METAL":
            names = [f"raw_{family}", f"{family}_ingot", f"{family}_nugget"]
            block_names = [f"{family}_block", f"raw_{family}_block"]
        elif kind == "ALLOY":
            names = [f"{family}_ingot", f"{family}_nugget"]
            block_names = [f"{family}_block"]
        else:
            names = [family]
            block_names = [f"{family}_block"]
        for name in names:
            _item_model(name)
            items += 1
        for name in block_names:
            _block(name)
            blocks += 1
            if kind != "CLOTH":
                TOOL_TAGS["mineable"].append(f"{NS}:{name}")
                tier = "diamond" if band >= EXOTIC else "iron" if band >= PRECIOUS else "stone"
                if kind == "ALLOY":
                    tier = "iron"
                TOOL_TAGS[tier].append(f"{NS}:{name}")
        # recipes
        if kind == "METAL":
            for cook, time in (("smelting", 200), ("blasting", 100)):
                write(os.path.join(DATA, f"recipe/{family}_ingot_from_{cook}.json"), {
                    "type": f"minecraft:{cook}", "category": "misc",
                    "ingredient": {"item": f"{NS}:raw_{family}"},
                    "result": {"id": f"{NS}:{family}_ingot"}, "experience": 0.7, "cookingtime": time})
                recipes += 1
            _compaction(f"raw_{family}", f"raw_{family}_block")
            _compaction(f"{family}_ingot", f"{family}_block")
            _compaction(f"{family}_nugget", f"{family}_ingot")
            recipes += 6
        elif kind == "ALLOY":
            ingredients = []
            for part, count in row["made_of"].items():
                ingredients.extend([_ingredient(part)] * count)
            write(os.path.join(DATA, f"recipe/{family}_ingot.json"), {
                "type": "minecraft:crafting_shapeless", "category": "misc",
                "ingredients": ingredients,
                "result": {"count": row["yield"], "id": f"{NS}:{family}_ingot"}})
            _compaction(f"{family}_ingot", f"{family}_block")
            _compaction(f"{family}_nugget", f"{family}_ingot")
            recipes += 5
        elif kind == "CLOTH":
            ingredients = []
            for part, count in CLOTH_MADE_OF.items():
                ingredients.extend([_ingredient(part)] * count)
            write(os.path.join(DATA, f"recipe/{family}.json"), {
                "type": "minecraft:crafting_shapeless", "category": "misc",
                "ingredients": ingredients,
                "result": {"count": CLOTH_YIELD, "id": f"{NS}:{family}"}})
            _compaction(family, f"{family}_block")
            recipes += 3
        else:
            _compaction(family, f"{family}_block")
            recipes += 2
        # tags: the family's own, so the ore drops it and every recipe against the tag takes it
        if kind in ("METAL", "ALLOY"):
            _c_tag("ingots", family, [f"{NS}:{family}_ingot"])
            _c_tag("nuggets", family, [f"{NS}:{family}_nugget"])
        if kind == "METAL":
            _c_tag("raw_materials", family, [f"{NS}:raw_{family}"])
            _c_tag("storage_blocks", f"raw_{family}", [f"{NS}:raw_{family}_block"])
            _c_tag("storage_blocks", f"raw_{family}", [f"{NS}:raw_{family}_block"], realm="block")
        if kind in ("GEM", "SHARD"):
            _c_tag("gems", family, [f"{NS}:{family}"])
        _c_tag("storage_blocks", family, [f"{NS}:{family}_block"])
        _c_tag("storage_blocks", family, [f"{NS}:{family}_block"], realm="block")
    for drop in DROPS:
        _item_model(drop["name"])
        items += 1
    for part in PARTS:
        _item_model(part["name"])
        items += 1
        side = 2 if part["takes"] == 4 else 3
        write(os.path.join(DATA, f"recipe/{part['name']}.json"), {
            "type": "minecraft:crafting_shaped", "category": "misc",
            "pattern": ["##", "##"] if side == 2 else ["###", "###", "###"],
            "key": {"#": {"item": f"{NS}:{part['family']}"}},
            "result": {"count": 1, "id": f"{NS}:{part['name']}"}})
        _c_tag("plates", part["family"], [f"{NS}:{part['name']}"])
        recipes += 1
    return items, blocks, recipes


def cordon_language():
    lang = {}
    for row in ROSTER:
        family, kind = row["family"], row["kind"]
        if kind == "EXTERNAL":
            continue
        pretty = title(family)
        if kind == "METAL":
            lang[f"item.{NS}.raw_{family}"] = f"Raw {pretty}"
            lang[f"item.{NS}.{family}_ingot"] = f"{pretty} Ingot"
            lang[f"item.{NS}.{family}_nugget"] = f"{pretty} Nugget"
            lang[f"block.{NS}.raw_{family}_block"] = f"Block of Raw {pretty}"
        elif kind == "ALLOY":
            lang[f"item.{NS}.{family}_ingot"] = f"{pretty} Ingot"
            lang[f"item.{NS}.{family}_nugget"] = f"{pretty} Nugget"
        else:
            lang[f"item.{NS}.{family}"] = pretty
        lang[f"block.{NS}.{family}_block"] = f"Block of {pretty}"
    for drop in DROPS:
        lang[f"item.{NS}.{drop['name']}"] = title(drop["name"])
    for part in PARTS:
        lang[f"item.{NS}.{part['name']}"] = title(part["name"])
    return lang


def features():
    """A configured feature, a placed feature and a biome modifier per gap."""
    by_dimension = {}
    for family, host, band, fallback, target, yrange, dimension, optional in GAPS:
        name = name_of(family, host)
        block = f"{NS}:{name}"
        if target.startswith("#"):
            rule_target = {"predicate_type": "minecraft:tag_match",
                           "tag": target[1:]}
        else:
            rule_target = {"predicate_type": "minecraft:block_match",
                           "block": target}
        write(os.path.join(DATA, f"worldgen/configured_feature/{name}.json"), {
            "type": "minecraft:ore",
            "config": {
                "size": vein_size(band),
                "discard_chance_on_air_exposure": 0.5,
                "targets": [{"target": rule_target,
                             "state": {"Name": block}}]
            }
        })
        write(os.path.join(DATA, f"worldgen/placed_feature/{name}.json"), {
            "feature": f"{NS}:{name}",
            "placement": [
                {"type": f"{NS}:family_present", "family": family},
                {"type": "minecraft:count", "count": tries(band)},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:height_range",
                 "height": {"type": "minecraft:uniform",
                            "min_inclusive": {"absolute": yrange[0]},
                            "max_inclusive": {"absolute": yrange[1]}}},
                {"type": "minecraft:biome"}
            ]
        })
        by_dimension.setdefault(dimension, []).append(f"{NS}:{name}")

    for dimension, placed in sorted(by_dimension.items()):
        biomes = BIOMES[dimension]
        write(os.path.join(DATA, f"neoforge/biome_modifier/ores_{dimension}.json"), {
            "type": "neoforge:add_features",
            "biomes": biomes[0] if len(biomes) == 1 else biomes,
            "features": sorted(placed),
            "step": "underground_ores"
        })
    return len(by_dimension)


def language():
    lang = {}
    for family, host, *_ in GAPS:
        name = name_of(family, host)
        lang[f"block.{NS}.{name}"] = f"{title(host)} {title(family)} Ore"
    lang.update(cordon_language())
    lang.update({
        "commands.elysiumores.unified": "%s unified into %s",
        "commands.elysiumores.none": "Nothing in this pack needed unifying.",
        "commands.elysiumores.header": "%s families unified, %s ores filled in",
        "commands.elysiumores.ambiguous": "%s answers to more than one material and was left alone",
        "itemGroup.elysiumores.ores": "Elysium Ores",
        "elysiumores.unify.converted": "Unified to %s",
    })
    write(os.path.join(ASSETS, "lang/en_us.json"), lang)
    return len(lang)


def catalogue():
    """The generated table the Java registry walks."""
    rows = []
    for family, host, band, fallback, target, yrange, dimension, optional in GAPS:
        name = name_of(family, host)
        rows.append('            new Entry("%s", "%s", "%s", %d, %s),'
                    % (name, family, host, band,
                       'null' if not fallback else '"%s"' % fallback))
    body = "\n".join(rows)[:-1]
    families = sorted({f for f, *_ in GAPS})
    family_rows = ",\n".join('            "%s"' % f for f in families)
    template = """package com.elysium.ores.data;

import java.util.List;

/**
 * Every ore this module adds, as a table.
 *
 * GENERATED by tools/gen_ores.py from tools/pack_scan.json. Do not edit by
 * hand; edit the generator and run it.
 *
 * <p>A row exists for each material-and-stone pair that nobody in the scanned
 * pack had filled in: netherrack_lead_ore exists because six mods put lead in
 * stone and deepslate and none put it in the Nether. A row is a name, the
 * material family it belongs to, the stone it is hosted in, how hard it is to
 * find, and - for the handful of materials vanilla itself supplies - the item
 * to fall back on when no mod has claimed the family's tag.
 *
 * <p>Nothing here names another mod's item. The family is a tag; what the tag
 * resolves to is a runtime question, asked by OreDrops.
 */
public final class OreCatalogue {

    private OreCatalogue() {
    }

    /**
     * One ore: the block's own path, the c: material family it belongs to, the
     * stone it is found in, how rare it is on OreRules' scale, and the item to
     * drop when no mod supplies the family - null for the families no mod-free
     * install has.
     */
    public record Entry(String name, String family, String host, int band,
                        String vanilla) {
    }

    public static final List<Entry> ENTRIES = List.of(
__BODY__);

    /** Every material family this module has an ore for. */
    public static final List<String> FAMILIES = List.of(
__FAMILIES__);
}
"""
    write(os.path.join(JAVA, "data/OreCatalogue.java"),
          template.replace("__BODY__", body).replace("__FAMILIES__", family_rows))
    return len(rows)


def main():
    clean()
    tags = unify_tags()
    ores = ore_blocks()
    items, blocks, recipes = cordon()
    tool_tags()
    loot_modifiers()
    dims = features()
    keys = language()
    rows = catalogue()
    print(f"unification tags  : {tags}")
    print(f"ore blocks        : {ores} across {len(HOSTS)} stones")
    print(f"cordon materials  : {items} items, {blocks} blocks, {recipes} recipes")
    print(f"biome modifiers   : {dims} worlds")
    print(f"catalogue rows    : {rows}")
    print(f"language keys     : {keys}")


if __name__ == "__main__":
    main()
