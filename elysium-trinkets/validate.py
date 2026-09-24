#!/usr/bin/env python3
"""
Cross-check the forty trinkets declared in Java against the shipped resources.

Run from the repo root:   python3 validate.py

The failures this exists to catch are all of one kind: a trinket that registers
perfectly, appears in the creative tab, and cannot be used. A missing slot file
makes it unwearable; a missing tag entry makes it unwearable in a different way;
a missing model makes it a purple-and-black cube; a missing recipe and no loot
table make it unobtainable. None of those is a crash and none of them appears in
a log.
"""
import json
import os
import re
import sys

WORK = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(WORK, "src/main/resources")
JAVA = os.path.join(WORK, "src/main/java/com/elysium/trinkets")

MOD = "elysiumtrinkets"

problems = []


def fail(msg):
    problems.append(msg)


def exists(rel):
    return os.path.exists(os.path.join(RES, rel))


def load(rel):
    with open(os.path.join(RES, rel), encoding="utf-8") as handle:
        return json.load(handle)


def load_maybe(rel):
    """The file, or None. For checks that want to say why it is missing."""
    return load(rel) if exists(rel) else None


def read_java(rel, keep_comments=False):
    """
    One source file, or an empty string.

    Comments are stripped by default. A rule that asks whether a file still
    calls something passes for ever once the javadoc above it mentions the
    name — which is exactly the failure this project keeps finding.
    """
    path = os.path.join(JAVA, rel)
    if not os.path.isfile(path):
        return ""
    text = open(path, encoding="utf-8").read()
    if keep_comments:
        return text
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", " ", text)


# ---------------------------------------------------------------------------
# The table, read from the Java that registers it
# ---------------------------------------------------------------------------
#
# Same rule as elysium-core's material gear: there is one list of trinkets and
# it is the one the game reads. A validator with its own copy validates its own
# copy.

def read_trinkets():
    found = []
    for filename, crafted in (("trinket/UniqueTrinkets.java", False),
                              ("trinket/CraftedTrinkets.java", True)):
        path = os.path.join(JAVA, filename)
        if not os.path.exists(path):
            fail(f"{filename} is missing - no trinkets could be read")
            continue
        source = open(path, encoding="utf-8").read()
        call = "crafted" if crafted else "unique"
        for match in re.finditer(
                r'=\s*' + call + r'\(\s*\n?\s*"(\w+)",\s*'
                r'(?:ElysiumElements?\.)(\w+),\s*"(\w+)",\s*(\d+)',
                source):
            found.append((match.group(1), match.group(2), match.group(3),
                          int(match.group(4)), crafted))

        # Everything declared must also be in that file's ALL array, because
        # ALL is what the mod registers items from. A trinket missing from it
        # exists in the library's registry, does nothing, and has no item.
        declared = set(re.findall(
            r'public static final ElysiumTrinket (\w+) = ' + call, source))
        listed = set(re.findall(r'\b([A-Z][A-Z0-9_]+),',
                                source[source.index("ALL = {"):]
                                if "ALL = {" in source else ""))
        for name in sorted(declared - listed):
            fail(f"{name} is declared in {filename} but missing from its ALL array, "
                 f"so it is registered as a trinket with no item to carry it")
    return found


trinkets = read_trinkets()
if not trinkets:
    print("read no trinkets out of the Java - the registration shape has changed")
    sys.exit(1)

unique = [t for t in trinkets if not t[4]]
crafted = [t for t in trinkets if t[4]]

names = [t[0] for t in trinkets]
if len(set(names)) != len(names):
    duplicates = sorted({n for n in names if names.count(n) > 1})
    fail(f"trinket id declared twice: {duplicates} - the library's registry throws "
         f"on the second registration")

# ---------------------------------------------------------------------------
# Every trinket is a complete item
# ---------------------------------------------------------------------------

lang = load(f"assets/{MOD}/lang/en_us.json") if exists(f"assets/{MOD}/lang/en_us.json") else {}

for path, element, slot, level, is_crafted in trinkets:
    if not exists(f"assets/{MOD}/models/item/{path}.json"):
        fail(f"{path} has no item model - it renders as a purple-and-black cube")
    elif not exists(f"assets/{MOD}/textures/item/{path}.png"):
        fail(f"{path} has a model but no texture at textures/item/{path}.png")

    for key in (f"item.{MOD}.{path}", f"trinket.{MOD}.{path}",
                f"trinket.{MOD}.{path}.desc"):
        if key not in lang:
            fail(f"{path}: no lang entry for {key} - the raw key shows in the tooltip")

print(f"trinkets declared  : {len(trinkets)}  ({len(unique)} found, {len(crafted)} crafted)")

# ---------------------------------------------------------------------------
# Curios: the slot exists, is given to players, and accepts the item
# ---------------------------------------------------------------------------
#
# Three separate files have to agree for a trinket to be wearable, and two of
# them living in another mod's namespace is exactly the arrangement where one
# gets forgotten. Checked together because they are one fact.

_before = len(problems)
slots = sorted({t[2] for t in trinkets})

for slot in slots:
    if not exists(f"data/{MOD}/curios/slots/{slot}.json"):
        fail(f"slot '{slot}' is used by a trinket but never registered at "
             f"data/{MOD}/curios/slots/{slot}.json - nothing can be worn in it")

entities_path = f"data/{MOD}/curios/entities/player.json"
if not exists(entities_path):
    fail(f"{entities_path} is missing - the slots exist and no player has any of them")
else:
    given = set(load(entities_path).get("slots", []))
    for slot in slots:
        if slot not in given:
            fail(f"slot '{slot}' is registered but not given to the player in "
                 f"{entities_path}")

# A consumer mod asks a preset slot for a size. It does not redefine one.
#
# Curios ships all seven of these slots itself, each carrying an icon and
# `"validators": ["curios:tag"]` - and that validator is the entire reason the
# item tag below decides what fits. A slot file here with "operation": "SET"
# replaces the preset, taking the validator with it, and the slot then accepts
# anything or nothing depending on which mod loaded last. Nothing logs it.
#
# This was written that way once, from memory rather than from the jar.
PRESET_SLOTS = {"back", "belt", "body", "bracelet", "charm",
                "curio", "hands", "head", "necklace", "ring"}

for slot in slots:
    slot_path = f"data/{MOD}/curios/slots/{slot}.json"
    if not exists(slot_path):
        continue
    declared = load(slot_path)
    if slot in PRESET_SLOTS and declared.get("operation") == "SET":
        fail(f"{slot_path} uses \"operation\": \"SET\" on '{slot}', which is one of "
             f"Curios' own preset slots. SET replaces the preset and discards its "
             f"\"curios:tag\" validator, so the slot stops honouring the item tag - "
             f"silently. Ask for a size and nothing else.")
    unexpected = set(declared) - {"size", "operation", "order", "icon", "drop_rule"}
    if unexpected:
        fail(f"{slot_path} carries key(s) Curios does not read: {sorted(unexpected)}")

for slot in slots:
    tag_path = f"data/curios/tags/item/{slot}.json"
    if not exists(tag_path):
        fail(f"{tag_path} is missing - the '{slot}' slot exists and accepts nothing")
        continue
    tag = load(tag_path)
    if tag.get("replace"):
        fail(f"{tag_path} sets replace:true, which drops every other mod's items "
             f"out of the '{slot}' slot")
    values = set(tag.get("values", []))
    for path, _element, item_slot, _level, _crafted in trinkets:
        if item_slot != slot:
            continue
        if f"{MOD}:{path}" not in values:
            fail(f"{path} declares slot '{slot}' but is not in {tag_path}, so it "
                 f"cannot be equipped anywhere")

if len(problems) == _before:
    print(f"curios slots       : {len(slots)} ({', '.join(slots)}), all registered and tagged")

# ---------------------------------------------------------------------------
# Obtainable, and obtainable exactly one way
# ---------------------------------------------------------------------------

for path, _element, _slot, _level, is_crafted in trinkets:
    has_recipe = exists(f"data/{MOD}/recipe/{path}.json")
    has_loot = exists(f"data/{MOD}/loot_table/trinkets/{path}.json")

    if is_crafted and not has_recipe:
        fail(f"{path} is a crafted trinket with no recipe - it can only be spawned in")
    if not is_crafted and not has_loot:
        fail(f"{path} is a found trinket with no loot table - nothing can ever "
             f"give it to a player")
    if not is_crafted and has_recipe:
        fail(f"{path} is a found trinket with a recipe. The whole distinction "
             f"between the two kinds is that a rule is found and a number is "
             f"made; a craftable unique is neither")

# ---------------------------------------------------------------------------
# The potion charm (SPEC_CLONE P12.3)
# ---------------------------------------------------------------------------
#
# One item holding any potion there is, which is a different shape from every
# other trinket in this module: what it does is on the stack rather than in a
# passive, so none of the checks above see it at all.


def charm_file(path):
    return os.path.join(RES, path)


charm_item = read_java("item/PotionCharmItem.java")
charm_handler = read_java("event/PotionCharmHandler.java")
charm_recipe = read_java("recipe/PotionCharmRecipe.java")
charm_client = read_java("client/TrinketsClient.java")
curios_slots = read_java("CuriosSlots.java")

if not charm_item or not charm_handler or not charm_recipe:
    fail("the potion charm is missing one of its three parts: the item, the handler "
         "that keeps its effect going, or the recipe that fills it")
else:
    if "isInstantenous()" not in charm_item or "isBeneficial()" not in charm_item:
        fail("a charm will take any potion at all. An instant potion in one fires every "
             "few seconds forever, and a harmful one is a charm whose only use is on "
             "somebody else — which is not a thing a worn item can be")
    if "durability(" not in charm_item:
        fail("a charm never wears out, so one potion is every potion forever")
    if "working(" not in charm_handler:
        fail("the handler does not ask whether a charm is spent or empty before using it")
    if "setDamageValue(" not in charm_handler:
        fail("wearing a charm costs nothing")
    if "hurtAndBreak" in charm_handler or "shrink(" in charm_handler:
        fail("a spent charm is destroyed rather than going inert. A charm is an "
             "investment, and an investment that vanishes is one nobody makes twice")
    if "tickCount % PotionCharmItem.INTERVAL" not in charm_handler:
        fail("the charm is looked at every tick rather than on an interval, which puts "
             "an inventory scan into every tick of every player")
    if "PotionCharmItem.allowed(" not in charm_handler:
        fail("the handler applies whatever is in the charm rather than what a charm is "
             "allowed to hold")
    if "getEffect(" not in charm_handler or "REFRESH_AT" not in charm_handler:
        fail("the charm re-applies its effect whether or not the wearer already has it, "
             "so it costs durability for doing nothing")
    if "PotionCharmItem.allowed(contents)" not in charm_recipe:
        fail("the bench will fill a charm with something the charm cannot use, so a "
             "player finds out by wearing an inert one")
    if "GLASS_BOTTLE" not in charm_recipe:
        fail("filling a charm eats the bottle, which no vanilla recipe that drinks one "
             "does")
    if "already.equals(contents)" not in charm_recipe:
        fail("a charm holding one potion is refilled by a different one, which is a free "
             "swap that throws away what was in it")
    if "setDamageValue(0)" not in charm_recipe:
        fail("refilling a charm does not restore it, so a spent charm stays spent")
    if "CuriosApi.registerCurio(" not in curios_slots:
        fail("nothing tells Curios the charm is wearable, so it cannot be put in a slot")
    if "wornCharms(" not in curios_slots:
        fail("nothing asks Curios where the charm is worn")
    charm_model = load_maybe(f"assets/{MOD}/models/item/potion_charm.json")
    if charm_model is None:
        fail("the charm has no model")
    else:
        layers = charm_model.get("textures", {})
        if "layer0" not in layers or "layer1" not in layers:
            fail("the charm's model has one layer. A charm holds any potion there is, so "
                 "its colour has to come from the potion — which needs a layer to paint")
    for path in (f"assets/{MOD}/textures/item/potion_charm.png",
                 f"assets/{MOD}/textures/item/potion_charm_fill.png"):
        if not os.path.isfile(charm_file(path)):
            fail(f"the charm is missing {path}")
    if "TINTED_LAYER" not in charm_client or "getColor()" not in charm_client:
        fail("nothing paints the charm with the colour of what is in it, so every charm "
             "in a hotbar looks like every other")
    charm_tag = load_maybe("data/curios/tags/item/charm.json") or {}
    if f"{MOD}:potion_charm" not in charm_tag.get("values", []):
        fail("the charm is not in the charm slot's tag, so it cannot be worn")
    for key in (f"item.{MOD}.potion_charm", f"item.{MOD}.potion_charm.of",
                f"item.{MOD}.potion_charm.worn", f"item.{MOD}.potion_charm.empty",
                f"item.{MOD}.potion_charm.spent"):
        if key not in lang:
            fail(f"the charm shows {key} and nothing translates it")
    print("potion charm       : fills, refills, wears out and goes inert rather than "
          "breaking")

# ---------------------------------------------------------------------------
# Recipe shape
# ---------------------------------------------------------------------------
#
# An Ingredient is an object in 1.21, never a bare id. This is the third mod in
# this project to need the check and the first to have it before shipping: 229
# recipes in elysium-core failed to load for exactly this, silently, and the
# only trace was one line per recipe during a resource reload.

# Everything this module registers that is not a trinket. Read out of the Java
# rather than listed here, so an item added there cannot be missing from here.
_mod_source = open(os.path.join(JAVA, "ElysiumTrinkets.java"), encoding="utf-8").read()
OTHER_ITEMS = set(re.findall(r'ITEMS\.register\(\s*"(\w+)"', _mod_source))

# Recipe types whose result is computed. The type is the serializer's id, and
# the serializer has to be one this module registers.
DYNAMIC_RECIPES = {f"{MOD}:{name}" for name
                   in re.findall(r'RECIPES\.register\(\s*"(\w+)"', _mod_source)}
if not DYNAMIC_RECIPES:
    fail("no recipe serializer is registered, so the charm cannot be filled")

_before = len(problems)
recipe_dir = os.path.join(RES, f"data/{MOD}/recipe")
if os.path.isdir(recipe_dir):
    for filename in sorted(os.listdir(recipe_dir)):
        if not filename.endswith(".json"):
            continue
        where = f"data/{MOD}/recipe/{filename}"
        recipe = load(where)

        for symbol, value in (recipe.get("key") or {}).items():
            if isinstance(value, str):
                fail(f"{where}: key '{symbol}' is the bare id {value!r}. In 1.21 an "
                     f"Ingredient is an object, and a string drops the whole recipe "
                     f"without a crash")
            elif not (isinstance(value, dict) and ({"item", "tag"} & set(value))):
                fail(f"{where}: key '{symbol}' is not an Ingredient object")

        if recipe.get("type") == "minecraft:crafting_shaped":
            widths = {len(row) for row in recipe["pattern"]}
            if len(widths) != 1:
                fail(f"{where}: pattern rows have differing widths {widths}")
            used = {c for row in recipe["pattern"] for c in row if c != " "}
            declared = set(recipe["key"])
            if used - declared:
                fail(f"{where}: pattern uses undeclared key(s) {used - declared}")
            if declared - used:
                fail(f"{where}: declares unused key(s) {declared - used}")

        if recipe.get("type") in DYNAMIC_RECIPES:
            # A recipe whose result is worked out in Java rather than written
            # down. There is exactly one, it has hundreds of results — a charm
            # of every potion there is — and its serializer is checked below
            # instead: a file naming a serializer nobody registered is a recipe
            # that silently does not exist.
            continue

        result = recipe.get("result", {})
        if "item" in result and "id" not in result:
            fail(f"{where}: result uses the pre-1.21 \"item\" key; it is \"id\" now")
        made = result.get("id", "").split(":")[-1]
        if made not in names and made not in OTHER_ITEMS:
            fail(f"{where}: result {result.get('id')} is neither a registered trinket nor "
                 f"one of this module's other items")

if len(problems) == _before:
    print(f"recipes            : {len(crafted)} crafted trinkets, all with a valid recipe")
    print(f"loot tables        : {len(unique)} found trinkets, all obtainable")

# ---------------------------------------------------------------------------
# The one Curios import
# ---------------------------------------------------------------------------
#
# The library's whole design for accessories rests on the claim that swapping
# Curios out is one file. That claim is only true while it is true, and it is
# the sort of thing a second import quietly ends.

CURIOS_FILES = {"CuriosSlots.java", "ElysiumTrinketItem.java"}
for dirpath, _, filenames in os.walk(JAVA):
    for filename in filenames:
        if not filename.endswith(".java"):
            continue
        text = open(os.path.join(dirpath, filename), encoding="utf-8").read()
        if "top.theillusivec4" in text and filename not in CURIOS_FILES:
            fail(f"{filename} imports Curios directly. The dependency is supposed to "
                 f"be {', '.join(sorted(CURIOS_FILES))} and nothing else - see "
                 f"CuriosSlots for why that is worth keeping true")

print(f"curios dependency  : confined to {len(CURIOS_FILES)} file(s)")

# ---------------------------------------------------------------------------
# All JSON parses
# ---------------------------------------------------------------------------

count = 0
for dirpath, _, filenames in os.walk(RES):
    for filename in filenames:
        if filename.endswith(".json"):
            count += 1
            try:
                json.load(open(os.path.join(dirpath, filename), encoding="utf-8"))
            except Exception as exc:
                fail(f"invalid JSON {os.path.join(dirpath, filename)}: {exc}")

# ---------------------------------------------------------------------------
# The accessory bridge, and the second question it now answers
# ---------------------------------------------------------------------------
#
# This module owns the only file in the project that knows Curios exists, and
# it now answers two questions rather than one: what trinkets is this player
# wearing, and what *stacks* are in their accessory slots. The second is what
# lets a modular ring, an affixed amulet or another mod's charm be asked what
# effects it carries, exactly as a helmet is.

_SRC = os.path.join(WORK, "src/main/java/com/elysium/trinkets")
_slots = open(os.path.join(_SRC, "CuriosSlots.java"), encoding="utf-8").read()
_trinkets_main = open(os.path.join(_SRC, "ElysiumTrinkets.java"), encoding="utf-8").read()

if "setStackProvider(" not in _slots:
    fail("the engine is never told what is in an accessory slot, so a worn ring's "
         "effects do nothing and jewellery is an item you can make and not use")
if "registerWearables" not in _slots:
    fail("nothing registers the items other modules declared wearable, so a modular "
         "ring cannot go in a slot at all")
if "registerWearables" not in _trinkets_main:
    fail("the wearables are registered by a method nobody calls")
if "FMLCommonSetupEvent" not in _trinkets_main:
    fail("the curio registration runs from the mod constructor, where the items do "
         "not exist yet")
if "ElysiumWearables.all()" not in _slots:
    fail("the bridge keeps its own list of what is wearable instead of reading the "
         "library's, so a module that adds jewellery has to edit this one")
# The comparison itself, not the name: the javadoc above it says
# "EquipmentSlotGroup.ANY" too, and a rule a comment can satisfy is no rule.
if "entry.slot() == net.minecraft.world.entity.EquipmentSlotGroup.ANY" not in _slots:
    fail("everything a stack grants is published when it is worn in an accessory "
         "slot, so a sword in a charm slot hands over its attack damage")
_granted = _slots[_slots.find("granted(ItemStack stack)"):]
if "ATTRIBUTE_MODIFIERS" not in _granted[:2000]:
    fail("a worn accessory's modifiers are invented here rather than read off the "
         "stack, so the bridge has to know what every wearable item is made of")
# The cache is not optional: this is read on the damage path.
if "STACKS.remove(" not in _slots:
    fail("the accessory cache is never emptied for a player who logs out, so a long "
         "server grows a map forever")
if "player.tickCount" not in _slots:
    fail("the accessory scan is not cached against the tick, so an inventory walk "
         "lands in the damage path several times a swing")

print("accessory bridge   : two questions, one file, cached against the tick")

print(f"json files parsed  : {count}")
print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)

print("all trinket checks passed")
