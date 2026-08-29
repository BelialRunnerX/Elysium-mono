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
    for filename, crafted in (("UniqueTrinkets.java", False),
                              ("CraftedTrinkets.java", True)):
        source = open(os.path.join(JAVA, filename), encoding="utf-8").read()
        call = "crafted" if crafted else "unique"
        for match in re.finditer(
                r'=\s*' + call + r'\(\s*\n?\s*"(\w+)",\s*'
                r'(?:ElysiumElements?\.)(\w+),\s*"(\w+)",\s*(\d+)',
                source):
            found.append((match.group(1), match.group(2), match.group(3),
                          int(match.group(4)), crafted))
    return found


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if os.path.exists(path):
        return False
    with open(path, "w", encoding="utf-8") as handle:
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
        trinket_art.SHAPES[slot](metal, glow).save(target)
        written += 1
    return written


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
        if write(os.path.join(RES, f"data/{MOD}/curios/slots/{slot}.json"),
                 {"size": sizes.get(slot, 1)}):
            written += 1

        # The tag lives in the curios namespace, not ours: it is Curios' tag
        # and every mod adds to it. Ours is one more file merged into it.
        if write(os.path.join(RES, f"data/curios/tags/item/{slot}.json"),
                 {"replace": False, "values": sorted(slots[slot])}):
            written += 1

    # "player", not "minecraft:player" - which is how Curios' own consumers
    # write it. The filename is arbitrary; the contents are not.
    if write(os.path.join(RES, f"data/{MOD}/curios/entities/player.json"),
             {"entities": ["player"], "slots": sorted(slots)}):
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
}


def write_lang(entries):
    lang = {}
    if os.path.exists(LANG):
        lang = json.load(open(LANG, encoding="utf-8"))

    lang.setdefault("itemGroup.elysiumtrinkets", "Elysium Trinkets")
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
    with open(LANG, "w", encoding="utf-8") as handle:
        json.dump(lang, handle, indent=2, ensure_ascii=False, sort_keys=True)
    return added


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
    print(f"lang keys added    : {write_lang(entries)}")


if __name__ == "__main__":
    main()
