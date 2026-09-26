#!/usr/bin/env python3
"""
Generate every resource elysium-trinkets needs: sprites, models, lang, the
Curios slot data, recipes and loot.

    python3 gen_trinkets.py

The trinket table is read out of the Java rather than duplicated here, for the
same reason gen_material_gear.py reads ElysiumMaterials.java: a trinket declared
in one place and not the other is an item with no model, which is a
purple-and-black cube in a player's hand. There is exactly one list, and it is
the one the game registers from.

Nothing here overwrites a file that already exists, so a hand-drawn sprite or a
hand-written recipe always wins and re-running after adding a trinket is safe.
"""
import json
import os
import pathlib
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
def _art_path():
    """
    The shared art package, wherever this script sits in the tree.

    Walks up looking for it rather than assuming a depth, because the same file
    ships at the repo root in one layout and two directories down in the other.
    """
    here = pathlib.Path(__file__).resolve()
    for base in [here.parent] + list(here.parents):
        if (base / "art" / "style.py").is_file():
            return str(base / "art")
    raise SystemExit("cannot find the art package from " + str(here.parent))


sys.path.insert(0, _art_path())


def _locate(marker, mod_dir):
    """
    Find a mod's resources under either repo layout.

    The multi-repo work tree has this script at the root with the mod in
    `core/`; the monorepo has it in `elysium-core/tools/` with the mod one
    level up. check_lifecycle.py already handles both for the same reason: a
    script that only knows one layout silently generates nothing in the other,
    and a generator that quietly writes nowhere is the exact failure this file
    spent two sections of FIXES.md on.
    """
    here = pathlib.Path(__file__).resolve()
    for base in [here.parent] + list(here.parents):
        for candidate in (base / mod_dir / "src/main/resources", base / "src/main/resources"):
            if (candidate / marker).is_dir():
                return str(candidate)
    raise SystemExit("cannot find %s under either repo layout - looked from %s"
                     % (mod_dir, here.parent))


import materials as material_art
import style
import trinkets as trinket_art

MOD = "elysiumtrinkets"
RES = _locate("assets/elysiumtrinkets", "trinkets")
TRINKETS = os.path.dirname(os.path.dirname(os.path.dirname(RES)))
JAVA = os.path.join(TRINKETS, "src/main/java/com/elysium/trinkets/trinket")

TEXTURES = os.path.join(RES, f"assets/{MOD}/textures/item")
MODELS = os.path.join(RES, f"assets/{MOD}/models/item")
LANG = os.path.join(RES, f"assets/{MOD}/lang/en_us.json")
RECIPES = os.path.join(RES, f"data/{MOD}/recipe")
LOOT = os.path.join(RES, f"data/{MOD}/loot_table/trinkets")
MODIFIERS = os.path.join(RES, f"data/{MOD}/loot_modifiers")
GLOBAL = os.path.join(RES, "data/neoforge/loot_modifiers/global_loot_modifiers.json")

# The metal each element is cast in. Element decides the glow; this decides the
# body, so two Void trinkets in different slots still look like a matched set.
ELEMENT_METAL = {
    "VOID": ("voidsteel", "void"),
    "PLASMA": ("bronze", "plasma"),
    "NEURAL": ("electrum", "neural"),
    "DIMENSIONAL": ("cobalt", "dimensional"),
    "KINETIC": ("steel", "kinetic"),
    "NONE": ("iron", "inert"),
}


def read_trinkets():
    """
    [(path, element, slot, level, crafted)] for all forty, in declaration order.

    Parsed out of the two Java files that declare them. The registration call
    is a fixed shape - a string, an element, a slot, a level - so this is a
    read of the real table rather than a second copy of it.
    """
    found = []
    for filename, crafted, call in (("UniqueTrinkets.java", False, "unique"),
                                    ("CraftedTrinkets.java", True, "crafted"),
                                    ("CompanionTrinkets.java", False, "companion")):
        source = open(os.path.join(JAVA, filename), encoding="utf-8").read()
        for match in re.finditer(
                r'=\s*' + call + r'\(\s*\n?\s*"(\w+)",\s*'
                r'(?:ElysiumElements?\.)(\w+),\s*"(\w+)",\s*(\d+)',
                source):
            found.append((match.group(1), match.group(2), match.group(3),
                          int(match.group(4)), crafted))
    return found


def write(path, obj, always=False):
    """
    Write one JSON file. A file that already exists is left alone unless
    `always`: the sprites and the recipes are a hand's to improve, but a file
    that is a function of the roster - the aggregate table, the modifiers -
    has to track the roster, and a stale one is a trinket nothing rolls.
    """
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if os.path.exists(path) and not always:
        return False
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(obj, handle, indent=2)
        handle.write("\n")
    return True


def title(name):
    return " ".join(part.capitalize() for part in name.split("_"))


# ---------------------------------------------------------------------------
# Sprites and models
# ---------------------------------------------------------------------------

def write_sprites(entries):
    os.makedirs(TEXTURES, exist_ok=True)
    style.METAL.update(material_art.all_ramps())
    written = 0
    for path, element, slot, _level, _crafted in entries:
        target = os.path.join(TEXTURES, path + ".png")
        if os.path.exists(target):
            continue          # hand-drawn; leave it alone
        metal, glow = ELEMENT_METAL.get(element, ELEMENT_METAL["NONE"])
        if metal not in style.METAL:
            metal = "iron"
        if slot == "companion":
            companion_sprite(path, metal, glow).save(target)
        else:
            trinket_art.SHAPES[slot](metal, glow).save(target)
        written += 1
    return written


# ---------------------------------------------------------------------------
# The companion slot (docs/SPLITS.md "Valoria: charms")
# ---------------------------------------------------------------------------
#
# Valoria's pet slot under its own name. Not one of Curios' presets, so the
# slot file has to say what it is - an icon and an order - where the seven
# preset slots only ask for a size. The four companions are each a small
# machine or anomaly, and each is drawn as one: a hovering body with the
# element's light in it, and a silhouette that says what it does.

COMPANION_SLOT = "companion"
COMPANION_ICON = f"{MOD}:slot/companion_slot"
SLOT_ICONS = os.path.join(RES, f"assets/{MOD}/textures/slot")

from canvas import Canvas, rect, union  # noqa: E402


def companion_sprite(path, metal, glow):
    """One companion: what hovers at the shoulder."""
    c = Canvas()
    ramp = style.METAL[metal]
    light = style.GLOW[glow]
    if path == "mote":
        # A round little light with two wisps trailing.
        body = {(x, y) for y in range(16) for x in range(16) if (x - 8) ** 2 + (y - 7) ** 2 <= 9}
        c.plate(body, ramp)
        c.glow([(7, 6), (8, 6), (7, 7), (8, 7)], light, within=body)
        c.plate(union(rect(3, 10, 5, 10), rect(2, 11, 3, 12), rect(10, 10, 12, 10), rect(12, 11, 13, 12)), ramp)
    elif path == "carrion_drone":
        # A squat hull with a grabbing arm below.
        body = union(rect(4, 4, 11, 8), rect(5, 3, 10, 3), rect(5, 9, 10, 9))
        c.plate(body, ramp)
        c.glow([(6, 5), (9, 5)], light, within=body)
        c.plate(union(rect(7, 10, 8, 12), rect(5, 13, 7, 13), rect(8, 13, 10, 13)), ramp)
    elif path == "lattice_shard":
        # A tall crystal, point down, that reads as a shield when it settles.
        body = {(x, y) for y in range(16) for x in range(16)
                if abs(x - 7.5) <= (12 - y) * 0.45 and 2 <= y <= 12}
        c.plate(body, ramp)
        c.glow([(7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8)], light, within=body)
    else:
        # The purge drone: a ring with a core, and a spout beneath.
        ring = {(x, y) for y in range(16) for x in range(16)
                if 6 <= (x - 8) ** 2 + (y - 7) ** 2 <= 16}
        c.plate(ring, ramp)
        c.glow([(7, 6), (8, 6), (7, 7), (8, 7)], light, within={(7, 6), (8, 6), (7, 7), (8, 7)})
        c.plate(union(rect(7, 11, 8, 13), rect(6, 14, 9, 14)), ramp)
    c.outline(ramp[0])
    return c


def companion_slot_icon():
    """The slot's own icon: a grey silhouette of something hovering."""
    c = Canvas()
    grey = style.METAL["stone"]
    body = {(x, y) for y in range(16) for x in range(16) if (x - 8) ** 2 + (y - 7) ** 2 <= 9}
    c.plate(body, grey)
    c.plate(union(rect(3, 10, 5, 10), rect(2, 11, 3, 12), rect(10, 10, 12, 10), rect(12, 11, 13, 12)), grey)
    c.outline(grey[0])
    return c


def write_companion_slot():
    """The icon the slot file names. The slot file itself is written with the others."""
    os.makedirs(SLOT_ICONS, exist_ok=True)
    target = os.path.join(SLOT_ICONS, "companion_slot.png")
    if os.path.exists(target):
        return 0
    companion_slot_icon().save(target)
    return 1


def write_models(entries):
    written = 0
    for path, _element, _slot, _level, _crafted in entries:
        if write(os.path.join(MODELS, path + ".json"),
                 {"parent": "minecraft:item/generated",
                  "textures": {"layer0": f"{MOD}:item/{path}"}}):
            written += 1
    return written


# ---------------------------------------------------------------------------
# Curios: the slots themselves, and what may go in them
# ---------------------------------------------------------------------------

def write_curios(entries):
    """
    Two halves, and they are genuinely different questions.

    The slot files say a slot of that name should exist and how many there are.
    The item tags say which items are allowed in it. Curios needs both: a slot
    with nothing tagged for it is an empty box on the screen, and an item
    tagged for a slot that does not exist can never be worn.
    """
    slots = {}
    for _path, _element, slot, _level, _crafted in entries:
        slots.setdefault(slot, [])
    for path, _element, slot, _level, _crafted in entries:
        slots[slot].append(f"{MOD}:{path}")

    written = 0
    # Two rings, one of everything else. Rings are the slot every accessory
    # mod doubles, and the one players expect to be able to pair.
    sizes = {"ring": 2}
    for slot in sorted(slots):
        # {"size": n} and nothing else.
        #
        # Read out of curios-neoforge-9.5.1's own jar and out of Relics, which
        # ships against it. All seven slots this mod uses are Curios *presets* -
        # it defines them itself at data/curios/curios/slots/, each with an icon
        # and `"validators": ["curios:tag"]`, and that validator is what makes
        # the item tag below govern what fits.
        #
        # An earlier version of this wrote "operation": "SET" alongside an
        # "order" and an invented "add_cosmetic". SET *replaces* the preset,
        # which would have thrown away Curios' icon and - much worse - its tag
        # validator, so the slot would have accepted anything or nothing. A
        # consumer mod's only business here is asking for a size.
        # The companion slot is not a preset: it has to say what it is. No
        # validators key, because Curios' default for a slot that names none
        # is the tag validator, which is the one wanted.
        declared = ({"size": 1, "order": 1200, "icon": COMPANION_ICON}
                    if slot == COMPANION_SLOT else {"size": sizes.get(slot, 1)})
        if write(os.path.join(RES, f"data/{MOD}/curios/slots/{slot}.json"), declared):
            written += 1

        # The tag lives in the curios namespace, not ours: it is Curios' tag
        # and every mod adds to it. Ours is one more file merged into it.
        if write(os.path.join(RES, f"data/curios/tags/item/{slot}.json"),
                 {"replace": False, "values": sorted(slots[slot])}):
            written += 1

    # "player", not "minecraft:player" - which is how Curios' own consumers
    # write it. The filename is arbitrary; the contents are not.
    # The one file here that is updated rather than added: a slot that joins
    # later has to be given to the player, and the add-only write above would
    # leave it registered, tagged and worn by nobody.
    entities = os.path.join(RES, f"data/{MOD}/curios/entities/player.json")
    given = set()
    if os.path.exists(entities):
        given = set(json.load(open(entities, encoding="utf-8")).get("slots", []))
    if not set(slots) <= given:
        os.makedirs(os.path.dirname(entities), exist_ok=True)
        with open(entities, "w", encoding="utf-8", newline="\n") as handle:
            json.dump({"entities": ["player"], "slots": sorted(given | set(slots))}, handle, indent=2)
            handle.write("\n")
        written += 1
    return written


# ---------------------------------------------------------------------------
# Recipes — crafted only. The found twenty-four are found.
# ---------------------------------------------------------------------------

# What an element's trinkets are made of. The ingot is elysium-core's, which is
# a soft dependency: with core absent the recipe simply never resolves, exactly
# as a modded-metal gear recipe does, and the trinket is still obtainable from
# the loot table.
ELEMENT_INGREDIENT = {
    "VOID": "elysium:voidglass_ingot",
    "PLASMA": "elysium:aetherium_ingot",
    "NEURAL": "elysium:aetherium_ingot",
    "DIMENSIONAL": "elysium:neutronium_ingot",
    "KINETIC": "elysium:neutronium_ingot",
    "NONE": "minecraft:iron_ingot",
}

# One pattern per slot: the silhouette of the thing, roughly, which is the
# oldest recipe-design trick there is and the reason nobody has to look up how
# to make a helmet.
SLOT_PATTERN = {
    "ring":     [" M ", "M M", " M "],
    "necklace": ["M M", "M M", " G "],
    "belt":     ["MMM", "G G", "MMM"],
    "charm":    [" G ", "MMM", " M "],
    "hands":    ["M M", "MGM", "MMM"],
    "back":     ["M M", "MMM", "MGM"],
    "head":     ["MMM", "MGM", "   "],
}


def write_recipes(entries):
    written = 0
    for path, element, slot, _level, crafted in entries:
        if not crafted:
            continue
        pattern = SLOT_PATTERN[slot]
        key = {"M": {"item": ELEMENT_INGREDIENT.get(element,
                                                    ELEMENT_INGREDIENT["NONE"])}}
        if any("G" in row for row in pattern):
            # The gem is what makes it a trinket rather than a lump of metal.
            key["G"] = {"item": "minecraft:amethyst_shard"}
        if write(os.path.join(RECIPES, path + ".json"),
                 {"type": "minecraft:crafting_shaped",
                  "category": "equipment",
                  "pattern": pattern,
                  "key": key,
                  "result": {"id": f"{MOD}:{path}", "count": 1}}):
            written += 1
    return written


def write_loot(entries):
    """
    One table per found trinket, for a dungeon chest to point at.

    A table rather than an entry in a shared pool: a dungeon that wants to hand
    out one specific unique can, and one that wants a random one rolls a table
    of tables. Baking them into a single pool here would take that choice away
    from the mod that owns the chest.
    """
    written = 0
    for path, _element, _slot, _level, crafted in entries:
        if crafted:
            continue
        if write(os.path.join(LOOT, path + ".json"),
                 {"type": "minecraft:chest",
                  "pools": [{"rolls": 1,
                             "entries": [{"type": "minecraft:item",
                                          "name": f"{MOD}:{path}"}]}]}):
            written += 1
    return written


# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

SLOT_NAMES = {
    "ring": "Worn on a finger",
    "necklace": "Worn at the throat",
    "belt": "Worn at the waist",
    "charm": "Carried",
    "hands": "Worn on the hands",
    "back": "Worn across the back",
    "head": "Worn on the head",
    "companion": "Hovers at your shoulder",
}

# One line each, in the trinket's own voice. Written here rather than generated
# because a description that says what a thing does is the one part of an item
# that cannot be derived from its id.
DESCRIPTIONS = {
    "widows_thimble": "The opening blow of a fight passes through you.",
    "cracked_reliquary": "Mends fast when you are nearly gone, and barely at all when you are not.",
    "nine_tenths_charm": "The nearer death, the harder to touch.",
    "iron_discipline": "Braced and still, you are half as easy to hurt.",
    "ashen_mantle": "While you burn, a third of every blow burns back.",
    "splintbone_fetish": "Doubles whatever is already turned back at your attackers.",
    "empty_reliquary": "A shield twice the size, over a body half as quick to mend.",
    "gravebound_coil": "Falling cannot kill you. The Code notices people it cannot kill.",
    "ratchet_gauntlet": "Critical blows land far harder. Everything else lands softer.",
    "duellists_cuff": "Stronger against the untouched than the wounded.",
    "hollow_chime": "Takes back from the wounded, and nothing from the whole.",
    "carrion_signet": "A kill mends you in proportion to what it cost.",
    "pale_tourniquet": "A heavy blow steadies you for a moment afterwards.",
    "deadmans_ledger": "Hunted, the Unsworn pay double for your work.",
    "quiet_hours": "Both meters move at half speed. Nothing you do is very noticeable.",
    "unsworn_bell": "Both meters move half again as fast. Everything you do is.",
    "auditors_seal": "What you have done is forgotten three times as fast.",
    "long_memory": "Nothing is forgotten. Both meters stay exactly where you left them.",
    "tithe_bracelet": "Every kill is filed, and the filing pays.",
    "debtors_knot": "Hunted, the world starts dropping things twice.",
    "prospectors_lens": "Ore pays twice. You learn a good deal less doing it.",
    "cartographers_nail": "Worked carefully, tools do not wear.",
    "longsight": "You learn faster and hit softer.",
    "reforgers_loupe": "A better eye at the forge, and a worse one for psionics.",

    "aetherium_band": "Blows land %s harder.",
    "executioners_grip": "Critical blows land at %sx.",
    "bloodlet_ring": "Takes back %s of what you deal.",
    "voidglass_pendant": "Psionic potency raised %s.",
    "neutronium_band": "Turns aside %s of every blow.",
    "kinetic_spur": "Avoids %s of blows outright.",
    "thornplate": "Returns %s of every blow to its owner.",
    "wardens_gorget": "Willpower shields %s larger.",
    "dimensional_anchor": "The first %s blocks of any fall are nothing.",
    "plasma_cord": "Mends %s faster.",
    "neural_filament": "Experience raised %s.",
    "favored_sigil": "Favor raised %s.",
    "shrouded_sigil": "Suspicion reduced %s.",
    "prospect_charm": "Elysium drops roll again %s of the time.",
    "artificers_loupe": "Reforges come out %s better.",
    "miners_rig": "Tools survive %s of their use, and ore sometimes pays twice.",

    "mote": "Lights the dark for you, and for everything looking for you.",
    "carrion_drone": "Picks over what you kill. The Empire notices the company you keep.",
    "lattice_shard": "Settles on you when you hold still, gets in the way when you do not, and you hit softer with it about you.",
    "purge_drone": "Bleeds the Null field out of you, and bleeds you a little to do it.",
}



# ---------------------------------------------------------------------------
# The potion charm (SPEC_CLONE P12.3)
# ---------------------------------------------------------------------------
#
# One item for every potion there is. What is in a charm is a component, so a
# charm of Regeneration and a charm of Night Vision are the same item carrying
# different contents — which is how a bottle works, and means a mod that adds a
# potion gets a charm for it without knowing this exists.
#
# The sprite is two layers because of that: the token, and a white field the
# game multiplies by the potion's own colour. One sprite per potion is
# impossible and one grey sprite makes every charm look alike in a hotbar.
CHARM_METAL = "gold"
CHARM_FALLBACK_METAL = "voidsteel"


def write_potion_charm():
    written = 0
    style.METAL.update(material_art.all_ramps())
    metal = CHARM_METAL if CHARM_METAL in style.METAL else CHARM_FALLBACK_METAL
    body = os.path.join(TEXTURES, "potion_charm.png")
    if not os.path.exists(body):
        os.makedirs(TEXTURES, exist_ok=True)
        # Drawn inert: the colour comes from what is in it, and a charm drawn
        # in one element's light would look wrong holding any other potion.
        trinket_art.charm(metal, "inert").save(body)
        written += 1
    fill = os.path.join(TEXTURES, "potion_charm_fill.png")
    if not os.path.exists(fill):
        trinket_art.charm_fill().save(fill)
        written += 1
    if write(os.path.join(MODELS, "potion_charm.json"),
             {"parent": "minecraft:item/generated",
              "textures": {"layer0": f"{MOD}:item/potion_charm",
                           "layer1": f"{MOD}:item/potion_charm_fill"}}):
        written += 1
    # The empty charm. Gold and glass, because it is a bottle you wear.
    if write(os.path.join(RECIPES, "potion_charm.json"),
             {"type": "minecraft:crafting_shaped",
              "category": "equipment",
              "pattern": [" a ", "gbg", " a "],
              "key": {"a": {"item": "minecraft:amethyst_shard"},
                      "g": {"item": "minecraft:gold_ingot"},
                      "b": {"item": "minecraft:glass_bottle"}},
              "result": {"id": f"{MOD}:potion_charm", "count": 1}}):
        written += 1
    # Filling one, which has hundreds of results and so is a serializer rather
    # than a file per potion. The file below is only the game being told that
    # the serializer exists and may be used in a crafting grid.
    if write(os.path.join(RECIPES, "potion_charm_filling.json"),
             {"type": f"{MOD}:potion_charm", "category": "equipment"}):
        written += 1
    # It goes in the charm slot, like every other charm, so the same tag.
    charm_tag = os.path.join(RES, "data/curios/tags/item/charm.json")
    if os.path.exists(charm_tag):
        tag = json.load(open(charm_tag, encoding="utf-8"))
        if f"{MOD}:potion_charm" not in tag.get("values", []):
            tag["values"] = sorted(set(tag.get("values", [])) | {f"{MOD}:potion_charm"})
            with open(charm_tag, "w", encoding="utf-8", newline="\n") as handle:
                json.dump(tag, handle, indent=2)
                handle.write("\n")
            written += 1
    return written


def write_lang(entries):
    lang = {}
    if os.path.exists(LANG):
        lang = json.load(open(LANG, encoding="utf-8"))

    lang.setdefault("itemGroup.elysiumtrinkets", "Elysium Trinkets")
    for key, value in (
            (f"item.{MOD}.potion_charm", "Potion Charm"),
            (f"item.{MOD}.potion_charm.of", "Charm of %s"),
            (f"item.{MOD}.potion_charm.worn",
             "Worn in a charm slot: keeps the effect going, and wears out doing it"),
            (f"item.{MOD}.potion_charm.empty",
             "Craft it with a potion to fill it, and again to refill it"),
            (f"item.{MOD}.potion_charm.spent", "Spent. Craft it with its potion again")):
        lang.setdefault(key, value)
    for slot, text in SLOT_NAMES.items():
        lang.setdefault(f"elysiumtrinkets.tooltip.slot.{slot}", text)

    added = 0
    for path, _element, _slot, _level, _crafted in entries:
        for key, value in ((f"item.{MOD}.{path}", title(path)),
                           (f"trinket.{MOD}.{path}", title(path)),
                           (f"trinket.{MOD}.{path}.desc",
                            DESCRIPTIONS.get(path, title(path)))):
            if key not in lang:
                lang[key] = value
                added += 1

    os.makedirs(os.path.dirname(LANG), exist_ok=True)
    with open(LANG, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(lang, handle, indent=2, ensure_ascii=False, sort_keys=True)
        handle.write("\n")
    return added


# ---------------------------------------------------------------------------
# Where a found trinket is found (trinket/FoundTrinkets.java)
# ---------------------------------------------------------------------------
#
# The chests and the chance are read out of the Java that also registers the
# reward provider, so the two ways in are one table. The aggregate rolls once
# over every found trinket; one modifier per chest adds it.

def read_found():
    """(chests, chance) out of FoundTrinkets.java."""
    source = open(os.path.join(JAVA, "FoundTrinkets.java"), encoding="utf-8").read()
    source = re.sub(r"/\*.*?\*/", " ", source, flags=re.S)
    source = re.sub(r"//[^\n]*", " ", source)
    block = re.search(r"CHESTS = List\.of\((.*?)\);", source, re.S)
    chests = re.findall(r'"(minecraft:chests/[a-z_]+)"', block.group(1)) if block else []
    chance = re.search(r"public static final float CHEST_CHANCE = ([0-9.]+)F;", source)
    if not chests or not chance:
        raise SystemExit("FoundTrinkets.java did not parse: no chests or no chance")
    return chests, float(chance.group(1))


def write_found(entries):
    """The aggregate, one modifier per chest, and the index that runs them."""
    chests, chance = read_found()
    found = [path for path, _element, _slot, _level, crafted in entries if not crafted]
    write(os.path.join(LOOT, "found.json"),
          {"type": "minecraft:chest",
           "pools": [{"rolls": 1,
                      "entries": [{"type": "minecraft:loot_table",
                                   "value": f"{MOD}:trinkets/{path}"} for path in found]}]},
          always=True)
    names = []
    for chest in chests:
        name = "found_in_" + chest.rsplit("/", 1)[1]
        write(os.path.join(MODIFIERS, name + ".json"),
              {"type": "neoforge:add_table",
               "conditions": [
                   {"condition": "neoforge:loot_table_id", "loot_table_id": chest},
                   {"condition": "minecraft:random_chance", "chance": chance}],
               "table": f"{MOD}:trinkets/found"},
              always=True)
        names.append(f"{MOD}:{name}")
    write(GLOBAL, {"replace": False, "entries": names}, always=True)
    return len(found), len(names)


def main():
    entries = read_trinkets()
    unique = sum(1 for e in entries if not e[4])
    crafted = sum(1 for e in entries if e[4])
    if not entries:
        raise SystemExit("read no trinkets out of the Java - check the call shape")
    print(f"trinkets read      : {len(entries)}  ({unique} found, {crafted} crafted)")
    print(f"sprites written    : {write_sprites(entries)}")
    print(f"models written     : {write_models(entries)}")
    print(f"curios files       : {write_curios(entries)}")
    print(f"recipes written    : {write_recipes(entries)}")
    print(f"loot tables written: {write_loot(entries)}")
    rolled, chests = write_found(entries)
    print(f"found trinkets     : {rolled} in one aggregate, added to {chests} vanilla chests")
    print(f"potion charm files : {write_potion_charm()}")
    print(f"companion slot icon: {write_companion_slot()}")
    print(f"lang keys added    : {write_lang(entries)}")


if __name__ == "__main__":
    main()
