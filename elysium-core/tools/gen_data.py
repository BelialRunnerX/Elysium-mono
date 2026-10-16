#!/usr/bin/env python3
"""Rebuild Elysium's data pack + asset JSON for Minecraft 1.21.1."""
import json
import os
import pathlib
import shutil

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


# The live tree.
#
# This pointed at work/ - a scratch checkout that stopped being the shipped one
# some time ago - so every run wrote files nobody would ever load while the real
# ones drifted. art/build.py had the same footgun and got the same fix. Derived
# from this file's own location so moving the repo does not silently re-break
# it.
ROOT = _locate("data/elysium", "core")
NS = "elysium"

BLOCKS = [
    "neutronium_ore",
    "neutronium_block",
    "aetherium_ore",
    "voidglass_ore",
    "reforge_table",
    "rune_socket_table",
    "ascension_forge",
    "registry_beacon",
    "imperial_archive",
    "inspection_beacon",
    # SPEC_CLONE P7: the two benches that act on runes rather than on gear.
    "gem_cutting_table",
    "augmenting_table",
    # SPEC_CLONE P9.5 and P9.6: things underground that a player did not put
    # there.
    "brutal_spawner",
    "swarm_spawner",
    "caged_invader",
]

# Blocks with no block item, and why.
#
# A block item needs an item model; a block with no item must not get one, or
# the creative tab search turns up an entry that cannot be placed.
ITEMLESS_BLOCKS = {
    "inspection_beacon": "planted by the Empire, never held by a player",
}

# Blocks that drop nothing when broken. Everything else self-drops.
NO_DROP_BLOCKS = {
    "inspection_beacon": "breaking it is the point; keeping it would be a free beacon",
}

ELEMENTAL_RUNES = [
    "voidward_rune",
    "plasmaforge_rune",
    "neuralspike_rune",
    "dimensionalshift_rune",
    "kineticsurge_rune",
]

UTILITY_RUNES = [
    "stabilizer_rune",
    "reflex_rune",
    "barrier_rune",
    "plasma_core_rune",
]

WEAPONS = [
    "voidcut_blade",
    "plasma_brand",
    "neural_lash",
    "rift_edge",
    "kinetic_maul",
    "singularity_lance",
    "neural_cascade_rifle",
]

TOOL_MATERIALS = ["voidglass", "aetherium", "neutronium"]
TOOL_SHAPES = ["hammer", "broadaxe", "scythe", "spear"]
TOOLS = [f"{m}_{s}" for s in TOOL_SHAPES for m in TOOL_MATERIALS]

# Rendered from a vanilla template rather than a flat sprite.
TEMPLATE_ITEMS = {
    "imperial_enforcer_spawn_egg": "minecraft:item/template_spawn_egg",
    "unsworn_raider_spawn_egg": "minecraft:item/template_spawn_egg",
}

# §15.5's food that answers a hazard: one ration per hazard, each granting that
# hazard's ward. Listed here so the model, the sprite reference and the language
# entry are generated with everything else — a ration that shipped with no model
# would be a purple cube in the one situation where a player is reading their
# hotbar in a hurry.
RATIONS = {
    # ration -> (display name, the two vanilla ingredients that make it)
    "thermal_ration": ("Coolant Ration", "minecraft:snowball"),
    "cryogenic_ration": ("Emberbread", "minecraft:charcoal"),
    "corrosive_ration": ("Filtered Mash", "minecraft:honeycomb"),
    "radiological_ration": ("Chelating Paste", "minecraft:dried_kelp"),
    "pressure_ration": ("Ballast Ration", "minecraft:clay_ball"),
}

SIMPLE_ITEMS = [
    "neutronium_ingot",
    "aetherium_ingot",
    "voidglass_ingot",
    "elysium_reforge",
    "imperial_codex",
    "field_scanner",
    "silent_convergence_template",
    "affix_convergence_template",
    # SPEC_CLONE P7. Dust is what a rune is worth once it is not one; a sigil
    # is a single use spent on a piece already worth keeping; the case is
    # where all of it is kept.
    "gem_dust",
    "gem_case",
    "sigil_of_socketing",
    "sigil_of_enhancement",
    "sigil_of_withdrawal",
    "sigil_of_rebirth",
    # The Ledger Web's refund: one node back per erasure (docs/SKILLTREE.md).
    "ledger_erasure",
] + list(RATIONS) + ELEMENTAL_RUNES + UTILITY_RUNES + WEAPONS + TOOLS + [
    "elysium_helmet",
    "plasma_chestplate",
    "voidweave_aegis",
    "neural_leggings",
    "dimensional_boots",
    "emperor_crown",
    "neutronium_helmet",
    "neutronium_chestplate",
    "neutronium_leggings",
    "neutronium_boots",
    "imperial_enforcer_spawn_egg",
    "unsworn_raider_spawn_egg",
]

ORE_DROPS = {
    "neutronium_ore": "neutronium_ingot",
    "aetherium_ore": "aetherium_ingot",
    "voidglass_ore": "voidglass_ingot",
}

# Derived rather than listed. A block added to BLOCKS and forgotten here is a
# block that drops nothing when mined, which is the same class of silent loss
# this file's "clean slate" note is about — and it is exactly what happened to
# the three Imperial blocks, which were registered in Java, shipped resources
# written by hand, and then had them deleted by the next run of this script.
SELF_DROPS = [block for block in BLOCKS
              if block not in ORE_DROPS and block not in NO_DROP_BLOCKS]


def write(path, obj):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(obj, handle, indent=2)
        handle.write("\n")


# ---------------------------------------------------------------------------
# Start from a clean slate for the directories we own.
#
# "Own" is doing work in that sentence, and it is why this script could not
# simply be repointed at the live tree when the drift was first noticed.
# gen_material_gear.py also writes into data/elysium/recipe and
# assets/elysium/models/item, so wiping them here deletes every file it wrote.
#
# The answer is ordering rather than cleverness: this runs first and clears,
# then gen_material_gear.py runs and refills, and regen.sh is the one place that
# knows the order so nobody has to remember it. A cleverer clean - deleting only
# files this script is about to rewrite - would leave a renamed file behind
# forever, which is a worse failure than an ordering rule that is written down
# and enforced by a script.
# ---------------------------------------------------------------------------
for stale in ["data", "assets/elysium/models", "assets/elysium/blockstates", "assets/elysium/lang"]:
    shutil.rmtree(os.path.join(ROOT, stale), ignore_errors=True)

# ---------------------------------------------------------------------------
# Blockstates + block models + item models
# ---------------------------------------------------------------------------
for block in BLOCKS:
    write(f"assets/{NS}/blockstates/{block}.json",
          {"variants": {"": {"model": f"{NS}:block/{block}"}}})
    write(f"assets/{NS}/models/block/{block}.json",
          {"parent": "minecraft:block/cube_all",
           "textures": {"all": f"{NS}:block/{block}"}})
    if block not in ITEMLESS_BLOCKS:
        write(f"assets/{NS}/models/item/{block}.json",
              {"parent": f"{NS}:block/{block}"})

for item in SIMPLE_ITEMS:
    if item in TEMPLATE_ITEMS:
        # A spawn egg takes its colours from the item, not from a texture.
        write(f"assets/{NS}/models/item/{item}.json", {"parent": TEMPLATE_ITEMS[item]})
        continue
    # Weapons use handheld, which is what tilts an item into the player's grip
    # instead of leaving it lying flat against the hand.
    parent = ("minecraft:item/handheld" if item in WEAPONS or item in TOOLS
              else "minecraft:item/generated")
    write(f"assets/{NS}/models/item/{item}.json",
          {"parent": parent,
           "textures": {"layer0": f"{NS}:item/{item}"}})

# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------
def title(name):
    return " ".join(part.capitalize() for part in name.split("_"))


lang = {"itemGroup.elysium": "Elysium"}
for block in BLOCKS:
    lang[f"block.{NS}.{block}"] = title(block)
for item in SIMPLE_ITEMS:
    lang[f"item.{NS}.{item}"] = title(item)

for ration, (display, _ingredient) in RATIONS.items():
    lang[f"item.{NS}.{ration}"] = display

lang.update({
    # --- the tier command and the claim's tier line: written by hand into the
    # generated file once (Sep 2026) and lost on the next regen, so they live
    # here now, where every other line of the file comes from.
    "elysium.command.tier.header": "World tier: %s (%s)",
    "elysium.command.tier.from": "Character level %s, world floor %s",
    "elysium.command.tier.share": "Everything percentile this tier buys: %s%% of the way",
    "elysium.claim.world_tier": "Your holdings are known: world tier %s, %s. The worlds answer in kind.",
    # --- items, named as the Sleeping Empire archive names them ------------
    "item.elysium.elysium_helmet": "Elysium Helm",
    "item.elysium.plasma_chestplate": "Plasma Carapace",
    "item.elysium.voidweave_aegis": "Voidweave Aegis",
    "item.elysium.neural_leggings": "Neural Leggings",
    "item.elysium.dimensional_boots": "Dimensional Boots",
    "item.elysium.emperor_crown": "Emperor's Crown",
    "item.elysium.elysium_reforge": "Elysium Reforge Catalyst",

    # --- field rations -----------------------------------------------------
    #
    # Named for what they do to a body rather than for the hazard, because the
    # hazard is already on the tooltip and a shelf of "Thermal Ration,
    # Cryogenic Ration, Corrosive Ration" is a menu nobody reads twice.
    "elysium.tooltip.ration": "Immune to %s \u00b7 %s",
    "elysium.tooltip.ration.whole": "Nothing of it lands while the meal lasts. "
                                    "Eat again to reset the clock.",

    "item.elysium.voidward_rune": "Voidward Rune",
    "item.elysium.plasmaforge_rune": "Plasmaforge Rune",
    "item.elysium.neuralspike_rune": "Neuralspike Rune",
    "item.elysium.dimensionalshift_rune": "Dimensionalshift Rune",
    "item.elysium.kineticsurge_rune": "Kineticsurge Rune",
    "item.elysium.stabilizer_rune": "Stabilizer Rune",
    "item.elysium.reflex_rune": "Reflex Rune",
    "item.elysium.barrier_rune": "Barrier Rune",
    "item.elysium.plasma_core_rune": "Plasma Core",

    "item.elysium.voidcut_blade": "Voidcut Blade",
    "item.elysium.plasma_brand": "Plasma Brand",
    "item.elysium.neural_lash": "Neural Lash",
    "item.elysium.rift_edge": "Rift Edge",
    "item.elysium.kinetic_maul": "Kinetic Maul",
    "item.elysium.singularity_lance": "Singularity Lance",
    "item.elysium.neural_cascade_rifle": "Neural Cascade Rifle",

    # --- the /elysium materials report -------------------------------------
    #
    # These five were hand-added to the shipped lang file and were not in this
    # table, so they survived only because this generator had been pointed at a
    # tree nobody shipped. The moment it was pointed at the live one they would
    # have been deleted and the command would have printed raw keys - which is
    # exactly the class of loss a "clean slate" step causes when the table it
    # rebuilds from is not actually complete.
    "elysium.command.materials.header": "Gear materials: %s of %s available",
    "elysium.command.materials.complete": "Every ingot type in this pack has Elysium gear.",
    "elysium.command.materials.uncovered": "%s ingot type(s) in this pack have no Elysium gear:",
    "elysium.command.materials.missing": "%s registered with no ingredient in this pack:",
    "elysium.command.materials.hint": "Add them under [materials] extra_materials in "
                                      "elysium-common.toml, then restart.",

    # --- elements ----------------------------------------------------------
    "elysium.element.void": "Void",
    "elysium.element.plasma": "Plasma",
    "elysium.element.neural": "Neural",
    "elysium.element.dimensional": "Dimensional",
    "elysium.element.kinetic": "Kinetic",
    "elysium.element.none": "Inert",

    # --- Imperial clearance, per the Code of Satisfaction -------------------
    "elysium.clearance.unranked": "Unranked",
    "elysium.clearance.petitioner": "Clearance: Petitioner",
    "elysium.clearance.sanctioned": "Clearance: Sanctioned",
    "elysium.clearance.codified": "Clearance: Codified",
    "elysium.clearance.imperial": "Clearance: Imperial",
    "elysium.clearance.sovereign": "Clearance: Elysomnion's Own",

    # --- tooltips ----------------------------------------------------------
    "elysium.gui.reforge": "Forge",
    "elysium.tooltip.rune_hint": "Socket at a Rune Socket Table",
    "elysium.tooltip.rune_tier_item": "Tier %s: %sx the base rune",
    "elysium.tooltip.rune_merge_hint": "Two of tier %s merge into tier %s at the Ascension Forge",
    "elysium.rune.tiered_name": "%s %s",
    "elysium.tooltip.reforge_hint": "Consumed at a Reforge Table to reroll stats",
    "elysium.tooltip.advantage": "+%s%% against %s and %s",
    "elysium.tooltip.resists": "Answers %s and %s",
    "elysium.tooltip.runes": "Runes: %s/%s",
    "elysium.tooltip.aligned": "(aligned)",
    "elysium.tooltip.ability.hammer": "Breaks a 3x3 face",
    "elysium.tooltip.ability.broadaxe": "Fells the whole tree",
    "elysium.tooltip.ability.scythe": "Harvests a 3x3",
    "elysium.tooltip.ability.spear": "Digs a 3x3",
    "elysium.tooltip.reforged": "Reforged \u00b7 %s of %s charges left",

    # --- rune effects ------------------------------------------------------
    "elysium.rune.voidward.effect": "Resistance when badly hurt \u00b7 +2 toughness",
    "elysium.rune.plasmaforge.effect": "Strength while healthy \u00b7 +1.5 attack damage",
    "elysium.rune.neuralspike.effect": "Haste \u00b7 +15% attack speed",
    "elysium.rune.dimensionalshift.effect": "Slow Falling \u00b7 +8% movement speed",
    "elysium.rune.kineticsurge.effect": "Jump Boost \u00b7 +0.1 knockback resistance",
    "elysium.rune.stabilizer.effect": "Health regeneration",
    "elysium.rune.reflex.effect": "+5% chance to avoid a blow",
    "elysium.rune.barrier.effect": "Recharging shield",
    "elysium.rune.plasma_core.effect": "-12% damage from fire and blasts",

    # --- affixes -----------------------------------------------------------
    "elysium.affix.void": "Void Warding %s knockback resistance",
    "elysium.affix.plasma": "Plasma Surge %s attack damage",
    "elysium.affix.neural": "Neural Overclock %s attack speed",
    "elysium.affix.dimensional": "Dimensional Drift %s movement speed",
    "elysium.affix.kinetic": "Kinetic Force %s attack damage",
    "elysium.affix.rune_voidward": "Voidward %s toughness",
    "elysium.affix.rune_plasmaforge": "Plasmaforge %s attack damage",
    "elysium.affix.rune_neuralspike": "Neuralspike %s attack speed",
    "elysium.affix.rune_dimensionalshift": "Dimensionalshift %s movement speed",
    "elysium.affix.rune_kineticsurge": "Kineticsurge %s knockback resistance",

    "material.elysium.neutronium": "Neutronium",
    "material.elysium.aetherium": "Aetherium",

    # --- the Empire's regard ------------------------------------------------
    "entity.elysium.imperial_enforcer": "Imperial Enforcer",
    "item.elysium.imperial_enforcer_spawn_egg": "Imperial Enforcer Spawn Egg",
    "entity.elysium.unsworn_raider": "Unsworn Raider",
    "item.elysium.unsworn_raider_spawn_egg": "Unsworn Raider Spawn Egg",

    "elysium.favor.unknown": "The Empire does not know your name",
    "elysium.favor.recognised": "Favor: Recognised \u2014 the Unsworn are drawn to you",
    "elysium.favor.favoured": "Favor: Favoured",
    "elysium.favor.exalted": "Favor: Exalted",

    "elysium.suspicion.clear": "Your record is clean",
    "elysium.suspicion.noted": "Suspicion: Noted",
    "elysium.suspicion.marked": "Suspicion: Marked \u2014 enforcers dispatched",
    "elysium.suspicion.hunted": "Suspicion: Hunted",

    "elysium.standing.report": "Favor %s \u00b7 %s    Suspicion %s \u00b7 %s",

    # --- claims, the archive and inspection posts --------------------------
    #
    # These nineteen were hand-added to the shipped language file and were not
    # in this table, so the first run of this script that cleared assets/lang
    # deleted every one of them — the same loss the note above the materials
    # report is about, repeated because the table was not brought along when
    # the feature was. Anything added to BLOCKS has to be added here too.
    "elysium.claim.staked": "You claim this ground. The Empire opens a file: "
                            "%s blocks in every direction, Suspicion held at "
                            "%s. You will respawn here.",
    "elysium.claim.report": "Filed: %s structures, Suspicion floor %s, "
                            "radius %s.",
    "elysium.claim.struck": "Your beacon is destroyed. The claim is struck \u2014 "
                            "what you built remains, and is nobody's.",
    "elysium.claim.not_yours": "This ground is filed to %s.",

    "elysium.archive.header": "\u2014 Imperial Archive \u2014",
    "elysium.archive.empty": "The file is empty. Nothing recent is recorded.",
    "elysium.archive.floor": "Structural floor: Suspicion will not fall below "
                             "%s. %s",
    "elysium.archive.holdings": "(%s claim(s) filed here, largest %s "
                                "structures)",
    "elysium.archive.no_claim": "No claim filed. Nothing holds your Suspicion "
                                "up.",

    "elysium.inspection.reporting": "Imperial inspection post. Reporting "
                                    "extraction within %s blocks at %sx for "
                                    "another %s minute(s).",
    "elysium.inspection.lapsed": "This post has lapsed. It is reporting "
                                 "nothing.",
    "elysium.inspection.resisted": "The post is destroyed. +%s Suspicion "
                                   "\u2014 and the Empire has sent someone to "
                                   "ask about it.",
    "elysium.inspection.resisted_quiet": "The post is destroyed. +%s "
                                         "Suspicion. Nobody was free to "
                                         "answer it.",

    "elysium.tooltip.registry_beacon": "Place and use it to claim the ground "
                                       "around it. You respawn here, and the "
                                       "claim is what an Imperial siege comes "
                                       "for.",
    "elysium.tooltip.registry_beacon.cost": "Sets a permanent floor under your "
                                            "Suspicion, rising with what you "
                                            "build inside the claim. Breaking "
                                            "the beacon strikes the claim; "
                                            "nothing you built is lost.",
    "elysium.tooltip.imperial_archive": "Use it to read your file: both "
                                        "meters, your bands, your structural "
                                        "floor, and what recently moved them.",
})


# ---------------------------------------------------------------------------
# Character system: stats, races, classes, passives
# ---------------------------------------------------------------------------
STATS = {
    "vitality": ("Vitality", "Passive health regeneration, and a little more of it to lose"),
    "fortitude": ("Fortitude", "Armour you have without wearing any"),
    "resilience": ("Resilience", "Cuts a share off everything that reaches you"),
    "strength": ("Strength", "Your base damage, before a weapon multiplies it"),
    "agility": ("Agility", "Movement speed"),
    "accuracy": ("Accuracy", "Chance to land a critical hit"),
    "reflexes": ("Reflexes", "Chance to avoid a blow outright"),
    "retribution": ("Retribution", "Sends a share of every blow back at its owner"),
    "intellect": ("Intellect", "Psionic potency: elemental advantage and rune strength"),
    "willpower": ("Willpower", "A shield that rebuilds itself"),
    "luck": ("Luck", "Chance of more out of everything you kill"),
    "presence": ("Presence", "How fast the Empire notices, and how well a reforge rolls"),
}

RACES = {
    "imperial": ("Imperial",
                 "Humanoid. The most common form across the Empire, and the only "
                 "people with no weakness worth naming."),
    "druun": ("Druun",
              "Reptilian, of the Druun Ascendancy. Militaristic and steeply "
              "hierarchical; enormously strong and slow to think."),
    "veylari": ("Veylari",
                "Avian, of the Veylari Concord. Isolationist and technologically "
                "advanced. Precise, clever, and barely armoured."),
    "korrath": ("Korrath",
                "Insectoid, of the Korrath Dominion. Hive-structured and rapidly "
                "expanding. Fast, evasive, and impossible to demoralise."),
    "lumari": ("Lumari",
               "Energy-based, of the Lumari Collective. Advanced, peaceful, and "
               "existing in forms that transcend traditional biology."),
    "unsworn": ("Unsworn",
                "Outside the Code entirely. Lucky, quick, and worth nothing at all "
                "to the Empire."),
}

CLASSES = {
    "medicae": ("Medicae", "Medical Regeneration, practised in the field. The only role that keeps anyone else standing."),
    "factor": ("Factor", "Imperial trade. Does not fight better - simply comes away with more."),
    "artificer": ("Artificer", "Fleet and infrastructure engineering. Keeps gear alive and reforges it better."),
    "enforcer": ("Enforcer", "The fleets' line soldier, and the Code's blunt instrument."),
    "psion": ("Psion", "The psionic elemental system, practised rather than merely carried."),
    "voidrunner": ("Voidrunner", "Cybernetic enhancement, spent entirely on arriving first."),
    "reclaimer": ("Reclaimer", "Singularity-forged neutronium comes from somewhere. This is who is down there getting it."),
    "warden": ("Warden", "Modelled on Sentinel: what stands in the way when nobody is left to give orders."),
    "marksman": ("Marksman", "The long shot, taken from cover, once."),
}

PASSIVES = {
    "sanctioned_answer": ("Sanctioned Answer",
                          "Harm done to an Imperial is answered. Reflects a share of every "
                          "blow, climbing with level toward 100% and never quite arriving: "
                          "9% at level 10, 33% at 50, 50% at 100, 80% at 400."),
    "cold_blood": ("Cold Blood", "Deals up to 60% more damage as your health falls."),
    "lightfeather": ("Lightfeather", "Ignores the first ten blocks of any fall, and two thirds of the rest."),
    "molt": ("Molt", "Regeneration triples after five seconds without being hurt."),
    "photonic": ("Photonic", "Double shield capacity. Takes a third less fire and blast damage, and 15% more of everything else."),
    "uncounted": ("Uncounted", "Suspicion sheds twice as fast. Favor comes half as easily."),
    "triage_field": ("Triage Field", "Heals every player within eight blocks, and yourself half again faster."),
    "profiteer": ("Profiteer", "A further 25% chance of a second drop from anything that pays out."),
    "field_repair": ("Field Repair", "Elysium gear wears a third slower. Reforges roll 50% higher."),
    "sanctioned_force": ("Sanctioned Force", "25% more damage to the Unsworn. Earns 40% less Suspicion."),
    "resonance": ("Resonance", "Psionic potency raised by half: wider elemental advantage, stronger runes."),
    "slipstream": ("Slipstream", "Halves fall damage."),
    "prospector": ("Prospector", "A one in four chance of a second ingot from any Elysium vein."),
    "bulwark": ("Bulwark", "Below half health your whole reflected share is laid over itself."),
    "called_shot": ("Called Shot", "A critical hit deals 125% extra damage instead of 50%."),
}

for _id, (_name, _desc) in STATS.items():
    lang[f"elysium.stat.{_id}"] = _name
    lang[f"elysium.stat.{_id}.desc"] = _desc

for _id, (_name, _desc) in RACES.items():
    lang[f"elysium.race.{_id}"] = _name
    lang[f"elysium.race.{_id}.desc"] = _desc

for _id, (_name, _desc) in CLASSES.items():
    lang[f"elysium.class.{_id}"] = _name
    lang[f"elysium.class.{_id}.desc"] = _desc

for _id, (_name, _desc) in PASSIVES.items():
    lang[f"elysium.passive.{_id}"] = _name
    lang[f"elysium.passive.{_id}.desc"] = _desc

# --- SPEC_CLONE P7: what the sigils and the two benches say -----------------
#
# A sigil's description is the whole of its interface: the item does one thing
# once, and a player deciding whether to spend it is reading this line.
for _augment, _says in (
        ("socketing", "Adds one socket to a piece of gear"),
        ("enhancement", "Raises one of a piece's affixes by a level"),
        ("withdrawal", "Takes the last socketed rune back out, intact"),
        ("rebirth", "Rolls a piece's affixes again, keeping their levels")):
    lang[f"elysium.sigil.{_augment}.desc"] = _says
lang["elysium.gem_case.desc"] = "Holds runes, dust and sigils"
lang["elysium.cutting.cost"] = "Costs %s dust"
lang["elysium.cutting.no_rune"] = "Put a rune in to cut it"
lang["elysium.augmenting.no_sigil"] = "Put a sigil in to use it"

# --- gear sets (SPEC_CLONE P9.1, P9.2) --------------------------------------
#
# Two sets, and they are the two the mod already has four pieces of. A set
# bonus is stated in the shared effect vocabulary, so it is read by the same
# handlers as a trait or an affix; the only thing new about it is the counting.
#
# Cumulative on purpose: four pieces gives the two-piece bonus and the
# four-piece one, because the alternative is a fourth piece that takes
# something away.
GEAR_SETS = {
    "elysium_regalia": {
        "name": "Elysium Regalia",
        "items": [f"{NS}:elysium_helmet", f"{NS}:plasma_chestplate",
                  f"{NS}:neural_leggings", f"{NS}:dimensional_boots"],
        "bonuses": [
            {"pieces": 2, "effects": [
                {"type": "attribute", "modifiers": [
                    {"attribute": "minecraft:generic.max_health", "operation": "add_value",
                     "slot": "any", "value": 2.0}]}]},
            {"pieces": 4, "effects": [
                {"type": "attribute", "modifiers": [
                    {"attribute": "elysiumlib:dodge_chance", "operation": "add_value",
                     "slot": "any", "value": 0.06}]},
                # Level 1 is Speed I: the engine gives amplifier level - 1 and
                # skips anything below 0, so a 0 here was a bonus that never
                # applied (found by Quill, 15 Oct 2026).
                {"type": "wielder_effect", "targets": [
                    {"effect": "minecraft:speed", "levels": [1], "duration": 60}]}]},
        ],
    },
    "neutronium_plate": {
        "name": "Neutronium Plate",
        "items": [f"{NS}:neutronium_helmet", f"{NS}:neutronium_chestplate",
                  f"{NS}:neutronium_leggings", f"{NS}:neutronium_boots"],
        "bonuses": [
            {"pieces": 2, "effects": [
                {"type": "attribute", "modifiers": [
                    {"attribute": "minecraft:generic.knockback_resistance",
                     "operation": "add_value", "slot": "any", "value": 0.1}]}]},
            {"pieces": 4, "effects": [
                {"type": "negate_damage", "tag": "minecraft:is_explosion",
                 "scale_per_level": 0.05},
                {"type": "attribute", "modifiers": [
                    {"attribute": "minecraft:generic.armor_toughness",
                     "operation": "add_value", "slot": "any", "value": 2.0}]}]},
        ],
    },
}
for _set, _body in GEAR_SETS.items():
    write(f"data/{NS}/elysium_gear_sets/{_set}.json",
          {"items": _body["items"], "bonuses": _body["bonuses"]})
    lang[f"elysium.gear_set.{NS}.{_set}"] = _body["name"]

# --- the starting kit (SPEC_CLONE P12.18) -----------------------------------
#
# What a new player is handed once, by ElysiumStartingKit; the codex is left
# to the library's join handler. Written here because this script clears data/
# before anything else runs: the kit was kept by hand under data/, and every
# regen.sh deleted it (found 15 Oct 2026, when the regen order was run).
def _kit_pool(item, count):
    return {"rolls": 1, "bonus_rolls": 0, "entries": [
        {"type": "minecraft:item", "name": item,
         "functions": [{"function": "minecraft:set_count", "count": count}]}]}


write(f"data/{NS}/loot_table/gameplay/starting_kit.json", {
    "type": "minecraft:gift",
    "pools": [_kit_pool("minecraft:bread", 8), _kit_pool("minecraft:torch", 16)],
    "random_sequence": f"{NS}:gameplay/starting_kit",
})

# --- lines the code has always sent and nobody had written ------------------
#
# Found by the check added with P7: a Component.translatable with no entry
# behind it shows the player a raw key, and thirteen of these had been shipping
# that way. The words are written from what each call site passes, in the order
# it passes them.
lang["elysium.archive.detail"] = (
    "Favor %s, Suspicion %s. %s to the next band; the file forgets %s every %s seconds.")
lang["elysium.claim.ascend.price"] = "That costs %s beacons to raise to ascension %s."
lang["elysium.claim.ascend.unclaimed"] = "Claim this ground before raising it."
lang["elysium.claim.ascended"] = "Ascension %s. The claim runs to %s blocks; floor %s."
lang["elysium.scanner.nothing"] = "Nothing here worth filing."
lang["elysium.scanner.known"] = "%s is already on the record."
lang["elysium.scanner.found"] = "%s recorded as %s."
lang["elysium.scanner.filed"] = "Filed to %s: %s paid, Suspicion %s, Favor %s."
lang["elysium.scanner.filing_set"] = "Reporting to %s."
lang["elysium.scanner.survey"] = "Depth: y %s."
lang["elysium.scanner.taxonomy"] = "%s, %s health."
lang["elysium.scanner.reports_to"] = "Reports to: %s"
lang["elysium.scanner.cycle"] = "Use in the air to change office"

lang.update({
    "item.elysium.imperial_codex": "Imperial Codex",
    "elysium.tooltip.codex": "Right-click to open your character sheet",
    "elysium.tooltip.requires_level": "Requires character level %s",
    "elysium.clearance.beyond": "Clearance: Beyond the Code",

    "elysium.screen.character": "Imperial Codex",
    "elysium.screen.choose": "Declare yourself. This is recorded once.",
    "elysium.screen.confirm": "Enter it in the record",
    "elysium.screen.level": "Level %s",
    "elysium.screen.points": "%s points to assign",

    "elysium.level.up": "Level %s \u00b7 %s points to assign",
    "elysium.audit.harvest.clean": "Harvest audit: %s blocks, and every one of the %s that needs a tool has one tagged to mine it; none a player places is unbreakable.",
    "elysium.audit.harvest.untagged": "Harvest audit: %s of the %s blocks that need a tool have no tool tagged to mine them, so they drop nothing: %s (the whole list is in logs/harvest-audit.txt)",
    "elysium.audit.harvest.unwritten": "Harvest audit: the list could not be written to %s (%s)",
    "elysium.audit.harvest.unbreakable": "Harvest audit: %s blocks a player can place can never be broken: %s",
    "elysium.character.chosen": "Recorded: %s, %s",
    "elysium.character.unchosen": "Unrecorded",
    "elysium.command.header": "%s \u00b7 %s \u00b7 Level %s",
    "elysium.command.respec": "Every assigned point returned \u2014 %s to spend",
})

os.makedirs(os.path.join(ROOT, f"assets/{NS}/lang"), exist_ok=True)
with open(os.path.join(ROOT, f"assets/{NS}/lang/en_us.json"), "w", encoding="utf-8", newline="\n") as handle:
    json.dump(lang, handle, indent=2, sort_keys=True)
    handle.write("\n")

# ---------------------------------------------------------------------------
# Loot tables  (1.21 folder is singular: loot_table, not loot_tables)
# ---------------------------------------------------------------------------
def ore_loot(block, drop):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{
                "type": "minecraft:alternatives",
                "children": [
                    {
                        "type": "minecraft:item",
                        "name": f"{NS}:{block}",
                        "conditions": [{
                            "condition": "minecraft:match_tool",
                            "predicate": {
                                "predicates": {
                                    "minecraft:enchantments": [{
                                        "enchantments": "minecraft:silk_touch",
                                        "levels": {"min": 1}
                                    }]
                                }
                            }
                        }]
                    },
                    {
                        "type": "minecraft:item",
                        "name": f"{NS}:{drop}",
                        "functions": [
                            {
                                "function": "minecraft:apply_bonus",
                                "enchantment": "minecraft:fortune",
                                "formula": "minecraft:ore_drops"
                            },
                            {"function": "minecraft:explosion_decay"}
                        ]
                    }
                ]
            }]
        }]
    }


def self_loot(block):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{block}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]
        }]
    }


# The enforcer's base table. Without a loot table at the path derived from the
# entity id, Minecraft logs a missing-table warning every time one dies; the
# Favor-scaled extras are added on top of this by ElysiumLootHandler.
write(f"data/{NS}/loot_table/entities/imperial_enforcer.json", {
    "type": "minecraft:entity",
    "pools": [
        {
            "rolls": 1,
            "entries": [{
                "type": "minecraft:item",
                "name": "minecraft:rotten_flesh",
                "functions": [
                    {"function": "minecraft:set_count",
                     "count": {"type": "minecraft:uniform", "min": 0.0, "max": 2.0}},
                    # Renamed in 1.21, and the new function will not assume
                    # which enchantment it scales with - naming it is required.
                    {"function": "minecraft:enchanted_count_increase",
                     "count": {"type": "minecraft:uniform", "min": 0.0, "max": 1.0},
                     "enchantment": "minecraft:looting"}
                ]
            }]
        },
        {
            "rolls": 1,
            "conditions": [{"condition": "minecraft:random_chance", "chance": 0.35}],
            "entries": [{"type": "minecraft:item", "name": f"{NS}:neutronium_ingot"}]
        }
    ]
})

write(f"data/{NS}/loot_table/entities/unsworn_raider.json", {
    "type": "minecraft:entity",
    "pools": [
        {
            "rolls": 1,
            "entries": [{
                "type": "minecraft:item",
                "name": "minecraft:rotten_flesh",
                "functions": [
                    {"function": "minecraft:set_count",
                     "count": {"type": "minecraft:uniform", "min": 0.0, "max": 2.0}},
                    # Renamed in 1.21, and the new function will not assume
                    # which enchantment it scales with - naming it is required.
                    {"function": "minecraft:enchanted_count_increase",
                     "count": {"type": "minecraft:uniform", "min": 0.0, "max": 1.0},
                     "enchantment": "minecraft:looting"}
                ]
            }]
        },
        {
            "rolls": 1,
            "conditions": [{"condition": "minecraft:random_chance", "chance": 0.35}],
            "entries": [{"type": "minecraft:item", "name": f"{NS}:voidglass_ingot"}]
        }
    ]
})

for block, drop in ORE_DROPS.items():
    write(f"data/{NS}/loot_table/blocks/{block}.json", ore_loot(block, drop))
for block in SELF_DROPS:
    write(f"data/{NS}/loot_table/blocks/{block}.json", self_loot(block))
# An explicit empty table rather than no file. A block with no loot table at
# all logs a missing-table warning every time one is broken; an empty one says
# "drops nothing" on purpose and is silent.
for block in NO_DROP_BLOCKS:
    write(f"data/{NS}/loot_table/blocks/{block}.json",
          {"type": "minecraft:block", "pools": []})

# ---------------------------------------------------------------------------
# Recipes  (1.21 folder is singular: recipe. Result key is "id", not "item".)
#
# An Ingredient is an *object* — {"item": ...} or {"tag": ...} — or an array of
# them. It is never a bare id string. That was legal through 1.20 and this file
# assumed it still was, so every one of the 229 recipes written here failed to
# load with
#
#     Map entry '#' : Failed to parse either.
#     First: Not a json array: "elysium:neutronium_ingot";
#     Second: Not a JSON object: "elysium:neutronium_ingot"
#
# and the whole mod's crafting quietly did not exist. Callers below still pass
# plain strings, which read far better in a recipe table; ing() is the single
# place that turns one into the shape the codec wants, so the mistake cannot be
# made once per recipe again.
# ---------------------------------------------------------------------------
def ing(value):
    """A bare id (or a list of them) as an Ingredient object."""
    if isinstance(value, (list, tuple)):
        return [ing(each) for each in value]
    if not isinstance(value, str):
        return value          # already an object; leave it alone
    if value.startswith("#"):
        return {"tag": value[1:]}
    # The common-tag namespace: c:ingots/tin is a tag, and writing it as an
    # item would mean the recipe only works when nothing provides the tag.
    if value.split(":", 1)[0] == "c":
        return {"tag": value}
    return {"item": value}


def shaped(pattern, key, result, count=1, category="misc"):
    return {
        "type": "minecraft:crafting_shaped",
        "category": category,
        "pattern": pattern,
        "key": {symbol: ing(value) for symbol, value in key.items()},
        "result": {"id": result, "count": count},
    }


def shapeless(ingredients, result, count=1, category="misc"):
    return {
        "type": "minecraft:crafting_shapeless",
        "category": category,
        "ingredients": [ing(each) for each in ingredients],
        "result": {"id": result, "count": count},
    }


ING = f"{NS}:neutronium_ingot"
AET = f"{NS}:aetherium_ingot"
VOID = f"{NS}:voidglass_ingot"

write(f"data/{NS}/recipe/neutronium_block.json",
      shaped(["###", "###", "###"], {"#": ING}, f"{NS}:neutronium_block", category="building"))

write(f"data/{NS}/recipe/neutronium_ingot_from_block.json",
      shapeless([f"{NS}:neutronium_block"], ING, 9))

write(f"data/{NS}/recipe/neutronium_helmet.json",
      shaped(["###", "# #"], {"#": ING}, f"{NS}:neutronium_helmet", category="equipment"))
write(f"data/{NS}/recipe/neutronium_chestplate.json",
      shaped(["# #", "###", "###"], {"#": ING}, f"{NS}:neutronium_chestplate", category="equipment"))
write(f"data/{NS}/recipe/neutronium_leggings.json",
      shaped(["###", "# #", "# #"], {"#": ING}, f"{NS}:neutronium_leggings", category="equipment"))
write(f"data/{NS}/recipe/neutronium_boots.json",
      shaped(["# #", "# #"], {"#": ING}, f"{NS}:neutronium_boots", category="equipment"))

write(f"data/{NS}/recipe/elysium_reforge.json",
      shaped([" a ", "ava", " a "], {"a": AET, "v": VOID}, f"{NS}:elysium_reforge"))

# --- field rations ----------------------------------------------------------
#
# The category is "misc" and not "food": CraftingBookCategory has exactly four
# values — building, redstone, equipment, misc — and the recipe book has no food
# tab. An unknown one is not an error anybody sees, it is a DataResult failure
# during recipe loading that drops the whole file, so the five rations shipped,
# had art and language, and simply could not be crafted.
#
# Bread plus two of one ordinary thing, shapeless, for two rations. Cheap on
# purpose: this is §10.2's *cheap* counter, and a counter that costs an Elysium
# ingot is not cheap — it is the engineered answer wearing a smaller price.
#
# Each pairing is the one a player can reason about backwards. Snow against
# heat, charcoal against cold, honeycomb sealing against corrosion, kelp's
# iodine against fallout, clay as ballast against pressure. None of it needs a
# wiki, which matters more here than anywhere else in the mod: this is the
# recipe somebody looks for while they are on fire.
for ration, (_display, ingredient) in RATIONS.items():
    write(f"data/{NS}/recipe/{ration}.json",
          shapeless(["minecraft:bread", ingredient, ingredient],
                    f"{NS}:{ration}", count=2, category="misc"))

# --- Imperial infrastructure ------------------------------------------------
#
# Both are deliberately buildable without any Elysium material except the one
# Aetherium ingot in the beacon: filing a claim is paperwork, and the Empire
# does not make paperwork expensive. What it costs is the permanent Suspicion
# floor the tooltip warns about.
# ---------------------------------------------------------------------------
# Gear convergence: the templates, and the smithing recipe that reads them
# ---------------------------------------------------------------------------
#
# One recipe file, not four. The direction is read off what is in the table's
# slots — see ConvergenceSmithingRecipe — so a second file would be a second
# place for the same question to be answered differently.
#
# The recipe ships unconditionally and does nothing without Silent Gear or
# Apotheosis installed: GearConvergence.directionFor asks ModList before it
# answers anything, so on a game with neither mod the output slot simply stays
# empty. A datapack condition would have been the other way to do this and
# would have meant the mod's own behaviour depending on load order.
write(f"data/{NS}/recipe/convergence.json", {"type": f"{NS}:convergence"})

# Both templates cost the same, because neither crossing is meant to be the
# advanced one. Voidglass is the lens, the ingot is what the piece is keyed to,
# and an amethyst shard is spent per conversion at the table.
# The Field Scanner: a lens, a brain and the metal to hold them.
write(f"data/{NS}/recipe/field_scanner.json",
      shaped([" v ", "vav", " n "],
             {"v": VOID, "a": f"{NS}:neuralspike_rune", "n": ING},
             f"{NS}:field_scanner", count=1))

write(f"data/{NS}/recipe/silent_convergence_template.json",
      shaped([" v ", "vav", " n "],
             {"v": VOID, "a": AET, "n": ING},
             f"{NS}:silent_convergence_template", count=2))

write(f"data/{NS}/recipe/affix_convergence_template.json",
      shaped([" v ", "vgv", " n "],
             {"v": VOID, "g": "minecraft:amethyst_shard", "n": ING},
             f"{NS}:affix_convergence_template", count=2))

write(f"data/{NS}/recipe/registry_beacon.json", {
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "pattern": ["iai", "i#i", "ooo"],
    "key": {
        "a": {"item": f"{NS}:aetherium_ingot"},
        "#": {"item": "minecraft:redstone_block"},
        "i": {"item": "minecraft:iron_ingot"},
        "o": {"item": "minecraft:obsidian"},
    },
    "result": {"id": f"{NS}:registry_beacon", "count": 1},
})
write(f"data/{NS}/recipe/imperial_archive.json", {
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "pattern": ["ooo", "b#b", "ooo"],
    "key": {
        "#": {"item": "minecraft:amethyst_shard"},
        "b": {"item": "minecraft:book"},
        "o": {"item": "minecraft:obsidian"},
    },
    "result": {"id": f"{NS}:imperial_archive", "count": 1},
})

# --- the three workstations -------------------------------------------------
#
# Their bases are wood, stone and iron, not obsidian and iron blocks. Obsidian
# is a diamond pickaxe and a trip to lava, and five iron blocks is forty-five
# ingots: either one put the bench a player needs *first* behind the resources
# they would need it to make sense of. The mod's own ingots stay on the top
# row — that is the gate that is actually meant to be there — and the body of
# each bench is what a player has on hand the day they find their first ore.
#
# Each of the two gear benches carries exactly one vanilla station in its
# middle, and never more than one: the Reforge Table an anvil, because
# reforging is rehammering a piece that already exists, and the Ascension
# Forge a smithing table, because ascending is the same verb vanilla spends
# that block on. One station apiece is the ceiling the owner set, so the
# validator counts them rather than trusting the pattern to stay honest.
write(f"data/{NS}/recipe/reforge_table.json",
      shaped(["###", "pap", "ppp"],
             {"#": ING, "p": "#minecraft:planks", "a": "minecraft:anvil"},
             f"{NS}:reforge_table", category="building"))

write(f"data/{NS}/recipe/rune_socket_table.json",
      shaped(["vvv", "s#s", "sss"],
             {"v": VOID, "#": ING, "s": "minecraft:stone_bricks"},
             f"{NS}:rune_socket_table", category="building"))

# --- the P7 benches and what they spend ------------------------------------
write(f"data/{NS}/recipe/gem_cutting_table.json",
      shaped(["vvv", "v#v", "sss"],
             {"v": VOID, "#": ING, "s": "minecraft:stone_bricks"},
             f"{NS}:gem_cutting_table", category="building"))

write(f"data/{NS}/recipe/augmenting_table.json",
      shaped(["aaa", "v#v", "sss"],
             {"a": AET, "v": VOID, "#": ING, "s": "minecraft:stone_bricks"},
             f"{NS}:augmenting_table", category="building"))

# The case: voidglass around a chest, which is what it is.
write(f"data/{NS}/recipe/gem_case.json",
      shaped(["vvv", "vcv", "vvv"],
             {"v": VOID, "c": "minecraft:chest"},
             f"{NS}:gem_case", category="equipment"))

# The four sigils. Each is a plate of the metal that suits what it does, with
# the catalyst that names it. Dust is in all four: a sigil is made of ground
# runes, which is what ties them to the rest of P7.
for _sigil, _catalyst in (("socketing", "minecraft:ender_pearl"),
                          ("enhancement", "minecraft:blaze_powder"),
                          ("withdrawal", "minecraft:ghast_tear"),
                          ("rebirth", "minecraft:echo_shard")):
    write(f"data/{NS}/recipe/sigil_of_{_sigil}.json",
          shaped([" d ", "dcd", " d "],
                 {"d": f"{NS}:gem_dust", "c": _catalyst},
                 f"{NS}:sigil_of_{_sigil}", category="equipment"))

# A Ledger Erasure: a page struck out of the ledger. Paper round a book,
# with the dust that ties every ledger item to the runes.
write(f"data/{NS}/recipe/ledger_erasure.json",
      shaped([" d ", "pbp", " d "],
             {"d": f"{NS}:gem_dust", "p": "minecraft:paper", "b": "minecraft:book"},
             f"{NS}:ledger_erasure", category="misc"))

# The Empire's ores, tagged: elysium:empire_ores for anything that counts
# them, and the common #c:ores so vein miners and ore finders see the three.
# These were the retired skill-tree generator's to write (28 Sep 2026); the
# tags outlive the tree.
for _tag_path in (f"data/{NS}/tags/block/empire_ores.json", "data/c/tags/block/ores.json"):
    write(_tag_path, {"replace": False,
                      "values": [f"{NS}:aetherium_ore", f"{NS}:neutronium_ore", f"{NS}:voidglass_ore"]})

# Dust has no shaped recipe of its own: it is what grinding a rune produces.
# The grinding is a recipe with no fixed result — what comes out depends on the
# purity of what went in — so it is a serializer rather than a shape.
write(f"data/{NS}/recipe/gem_grinding.json", {"type": f"{NS}:gem_grinding", "category": "misc"})

write(f"data/{NS}/recipe/ascension_forge.json",
      shaped(["aaa", "i#i", "iti"],
             {"a": AET, "#": ING, "i": "minecraft:iron_ingot",
              "t": "minecraft:smithing_table"},
             f"{NS}:ascension_forge", category="building"))

# --- runes -----------------------------------------------------------------
# Runes had no recipes at all, so the whole socket system was creative-only.
# Four voidglass around a catalyst that suits the effect; every catalyst is
# distinct, so no two rune recipes collide.
RUNE_CATALYSTS = {
    "voidward_rune": "minecraft:ender_pearl",
    "plasmaforge_rune": "minecraft:blaze_rod",
    "neuralspike_rune": "minecraft:amethyst_shard",
    "dimensionalshift_rune": "minecraft:chorus_fruit",
    "kineticsurge_rune": "minecraft:slime_ball",
    "stabilizer_rune": "minecraft:glistering_melon_slice",
    "reflex_rune": "minecraft:rabbit_foot",
    "barrier_rune": "minecraft:shield",
    "plasma_core_rune": "minecraft:magma_cream",
}

for rune_name, catalyst in RUNE_CATALYSTS.items():
    write(f"data/{NS}/recipe/{rune_name}.json",
          shaped([" v ", "vcv", " v "], {"v": VOID, "c": catalyst}, f"{NS}:{rune_name}"))

# --- area tools -------------------------------------------------------------
# Deliberately plain: the material across the top, sticks down the middle.
# Every shape is a distinct pattern, and none of them collide with a vanilla
# recipe, so the three material variants are just the same shape in a
# different metal.
TOOL_INGOTS = {
    "voidglass": VOID,
    "aetherium": AET,
    "neutronium": ING,
}

TOOL_PATTERNS = {
    "hammer":   ["mmm", "msm", " s "],
    "broadaxe": ["mmm", "ms ", " s "],
    "scythe":   ["mmm", "  s", "  s"],
    "spear":    ["  m", " s ", " s "],
}

for shape, pattern in TOOL_PATTERNS.items():
    for material, ingot in TOOL_INGOTS.items():
        write(f"data/{NS}/recipe/{material}_{shape}.json",
              shaped(pattern,
                     {"m": ingot, "s": "minecraft:stick"},
                     f"{NS}:{material}_{shape}",
                     category="equipment"))

# --- weapons ---------------------------------------------------------------
# The matching rune is part of the recipe: an element is a commitment, not a
# free label, and it competes with socketing that rune into armour.
WEAPON_RECIPES = {
    "voidcut_blade": ([" v ", " r ", " n "], {"v": VOID, "r": f"{NS}:voidward_rune", "n": ING}),
    "plasma_brand": ([" v ", " r ", " n "], {"v": VOID, "r": f"{NS}:plasmaforge_rune", "n": ING}),
    "neural_lash": ([" v ", " r ", " n "], {"v": VOID, "r": f"{NS}:neuralspike_rune", "n": ING}),
    "rift_edge": ([" v ", " r ", " n "], {"v": VOID, "r": f"{NS}:dimensionalshift_rune", "n": ING}),
    "kinetic_maul": (["vv ", "vr ", " n "], {"v": VOID, "r": f"{NS}:kineticsurge_rune", "n": ING}),
    "singularity_lance": (["  a", " a ", "nr "],
                          {"a": AET, "n": ING, "r": f"{NS}:dimensionalshift_rune"}),
    "neural_cascade_rifle": (["aa ", "nr ", "n  "],
                             {"a": AET, "n": ING, "r": f"{NS}:neuralspike_rune"}),
}

for weapon_name, (pattern, keys) in WEAPON_RECIPES.items():
    write(f"data/{NS}/recipe/{weapon_name}.json",
          shaped(pattern, keys, f"{NS}:{weapon_name}", category="equipment"))

# --- elemental armour -------------------------------------------------------
# These five pieces were creative-only: no recipe, no drop. Which quietly made
# the whole defensive half of the counter matrix, reforging and ascension
# unreachable in survival, since all three operate on Elysium armour.
#
# Same bargain as the weapons: the matching rune is part of the recipe, so an
# affinity costs you the rune you could have socketed instead. Plasma and
# Neural have no material of their own, so their pieces are voidglass — the
# same alloy the elemental blades are cut from.
ARMOUR_RECIPES = {
    "elysium_helmet": (["vvv", "vrv"],
                       {"v": VOID, "r": f"{NS}:voidward_rune"}),
    "plasma_chestplate": (["v v", "vrv", "vvv"],
                          {"v": VOID, "r": f"{NS}:plasmaforge_rune"}),
    "neural_leggings": (["vvv", "vrv", "v v"],
                        {"v": VOID, "r": f"{NS}:neuralspike_rune"}),
    "dimensional_boots": (["a a", "ara"],
                          {"a": AET, "r": f"{NS}:dimensionalshift_rune"}),
}

for armour_name, (pattern, keys) in ARMOUR_RECIPES.items():
    write(f"data/{NS}/recipe/{armour_name}.json",
          shaped(pattern, keys, f"{NS}:{armour_name}", category="equipment"))

# The Emperor's Crown is deliberately not here. It is Elysomnion's own, and it
# comes off an Imperial Enforcer's body once Suspicion reaches Hunted — see
# ElysiumLootHandler.

# --- the archive's flagship chestplate --------------------------------------
write(f"data/{NS}/recipe/voidweave_aegis.json",
      shaped(["v v", "vnv", "vvv"],
             {"v": VOID, "n": f"{NS}:neutronium_block"},
             f"{NS}:voidweave_aegis", category="equipment"))

# ---------------------------------------------------------------------------
# Block tags  (1.21 folder is singular: tags/block)
# ---------------------------------------------------------------------------
write("data/minecraft/tags/block/mineable/pickaxe.json",
      {"replace": False, "values": [f"{NS}:{b}" for b in BLOCKS]})

write("data/minecraft/tags/block/needs_iron_tool.json",
      {"replace": False, "values": [f"{NS}:aetherium_ore", f"{NS}:voidglass_ore"]})

write("data/minecraft/tags/block/needs_diamond_tool.json",
      {"replace": False,
       "values": [f"{NS}:reforge_table", f"{NS}:rune_socket_table", f"{NS}:ascension_forge",
                  f"{NS}:gem_cutting_table", f"{NS}:augmenting_table"]})

write("data/minecraft/tags/block/needs_netherite_tool.json",
      {"replace": False, "values": [f"{NS}:neutronium_ore", f"{NS}:neutronium_block"]})

# NeoForge reads this tag to decide what a netherite pickaxe can harvest.
write("data/neoforge/tags/block/needs_netherite_tool.json",
      {"replace": False, "values": [f"{NS}:neutronium_ore", f"{NS}:neutronium_block"]})

# ---------------------------------------------------------------------------
# Item tags: the Empire's ingots, by their common names
# ---------------------------------------------------------------------------
#
# Every generated recipe for an Elysium material names its ingot by the common
# tag — c:ingots/voidglass — so that another mod's voidglass, if one ever
# exists, crafts the same gear. That only works if the tag exists, and for the
# Empire's three it did not: NeoForge defines the common tags for vanilla
# metals and nobody defines them for ours. So every voidglass, aetherium and
# neutronium tool recipe pointed at an empty tag and none of them could be
# crafted, with nothing in the game or the log to say so. Neutronium armour
# was the exception only because its recipe names the item directly.
EMPIRE_INGOTS = {
    "voidglass": f"{NS}:voidglass_ingot",
    "aetherium": f"{NS}:aetherium_ingot",
    "neutronium": f"{NS}:neutronium_ingot",
}
for material, ingot in EMPIRE_INGOTS.items():
    write(f"data/c/tags/item/ingots/{material}.json",
          {"replace": False, "values": [ingot]})
# And the umbrella tag, which is what "any ingot" means to every other mod.
write("data/c/tags/item/ingots.json",
      {"replace": False, "values": [f"#c:ingots/{m}" for m in EMPIRE_INGOTS]})

# ---------------------------------------------------------------------------
# Worldgen
# ---------------------------------------------------------------------------
ORE_SETTINGS = {
    # name: (size, count, min_y, max_y, replaceable tag)
    "neutronium_ore": (5, 3, -64, 16),
    "aetherium_ore": (8, 8, -32, 64),
    "voidglass_ore": (6, 5, -48, 32),
}

for name, (size, count, min_y, max_y) in ORE_SETTINGS.items():
    write(f"data/{NS}/worldgen/configured_feature/{name}.json", {
        "type": "minecraft:ore",
        "config": {
            "size": size,
            "discard_chance_on_air_exposure": 0.0,
            "targets": [
                {
                    "target": {
                        "predicate_type": "minecraft:tag_match",
                        "tag": "minecraft:stone_ore_replaceables"
                    },
                    "state": {"Name": f"{NS}:{name}"}
                },
                {
                    "target": {
                        "predicate_type": "minecraft:tag_match",
                        "tag": "minecraft:deepslate_ore_replaceables"
                    },
                    "state": {"Name": f"{NS}:{name}"}
                }
            ]
        }
    })

    # A placed feature POINTS AT a configured feature - it does not restate it.
    # The old files pasted the ore config in here verbatim and left out the
    # "feature" field entirely, so every one of them failed to parse on load.
    write(f"data/{NS}/worldgen/placed_feature/{name}.json", {
        "feature": f"{NS}:{name}",
        "placement": [
            {"type": "minecraft:count", "count": count},
            {"type": "minecraft:in_square"},
            {
                "type": "minecraft:height_range",
                "height": {
                    "type": "minecraft:uniform",
                    "min_inclusive": {"absolute": min_y},
                    "max_inclusive": {"absolute": max_y}
                }
            },
            {"type": "minecraft:biome"}
        ]
    })

    # Without a biome modifier nothing ever places the feature into a biome,
    # which is why none of these ores generated before.
    write(f"data/{NS}/neoforge/biome_modifier/add_{name}.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": f"{NS}:{name}",
        "step": "underground_ores"
    })

# --- rogue spawners and the cage in the world (SPEC_CLONE P9.5, P9.6) -------
#
# Rare, underground, and on the floor: a spawner in the ceiling is a spawner a
# player walks under without ever seeing. The block predicate is what puts it
# on a floor — air where it goes, something solid beneath it — and without it
# `simple_block` would happily place them inside stone where nothing can reach
# them and nothing can be reached by them.
ROGUE_SETTINGS = {
    # name: (tries per chunk, min y, max y)
    "brutal_spawner": (1, -48, 32),
    "swarm_spawner": (1, -40, 48),
    "caged_invader": (1, -56, 8),
}

for name, (tries, min_y, max_y) in ROGUE_SETTINGS.items():
    write(f"data/{NS}/worldgen/configured_feature/{name}.json", {
        "type": "minecraft:simple_block",
        "config": {
            "to_place": {"type": "minecraft:simple_state_provider",
                         "state": {"Name": f"{NS}:{name}"}}
        }
    })
    write(f"data/{NS}/worldgen/placed_feature/{name}.json", {
        "feature": f"{NS}:{name}",
        "placement": [
            # Rare: one attempt in a good few chunks, not one per chunk.
            {"type": "minecraft:rarity_filter", "chance": 26},
            {"type": "minecraft:in_square"},
            {
                "type": "minecraft:height_range",
                "height": {
                    "type": "minecraft:uniform",
                    "min_inclusive": {"absolute": min_y},
                    "max_inclusive": {"absolute": max_y}
                }
            },
            {
                "type": "minecraft:block_predicate_filter",
                "predicate": {
                    "type": "minecraft:matching_blocks",
                    "blocks": "minecraft:air"
                }
            },
            {
                "type": "minecraft:environment_scan",
                "direction_of_search": "down",
                "max_steps": 8,
                "target_condition": {
                    "type": "minecraft:solid"
                }
            },
            {"type": "minecraft:random_offset",
             "xz_spread": {"type": "minecraft:constant", "value": 0},
             "y_spread": {"type": "minecraft:constant", "value": 1}},
            {"type": "minecraft:biome"}
        ]
    })
    write(f"data/{NS}/neoforge/biome_modifier/add_{name}.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": f"{NS}:{name}",
        "step": "underground_structures"
    })

# ---------------------------------------------------------------------------
# Silent Gear material definitions (datapack route, no code dependency)
#
# The 1.21 format is not the 1.16-1.20 format, and the mod shipped the older
# one: a flat "stats" block, "colors"/"tier"/"categories" at the root, an
# ingredient written as {"item": ...}, and the whole thing filed under
# data/silentgear/materials/. Silent Gear 1.21 reads
# data/<namespace>/silentgear_materials/ instead, so those files were never
# even opened - the integration had been quietly doing nothing.
#
# Rewritten against the wiki's own 1.21 example. Only fields that appear in
# that example are used: "mining_speed" is now "harvest_speed",
# "armor_toughness" is gone, armour is split per slot, and the mining tier is
# an object rather than an integer.
#
# The wiki example was not enough, and cost two launches to find out. The
# "crafting" block has five REQUIRED keys, and the example only shows three:
#
#     can_salvage  categories  gear_type_blacklist  ingredient  part_substitutes
#
# The two extra ones have no sensible default in the codec even though they
# have an obvious empty value, so a material without them throws exactly like a
# material with a malformed ingredient. This list is not from the wiki: it is
# every key present in all 132 of Silent Gear 4.2.1.1's own material files,
# read out of the mod jar. Where a document and the shipped artifact disagree,
# the artifact is the specification.
# ---------------------------------------------------------------------------
def silentgear_material(name, colour, ingredient, durability, armour, damage,
                        speed, harvest, enchantability, rarity, tier_name,
                        tier_hint, incorrect_tag):
    """One material, in the 1.21 shape."""
    return {
        "type": "silentgear:simple",
        "parent": "silentgear:empty",
        "crafting": {
            "can_salvage": True,
            # Silent Gear reads this through the vanilla Ingredient codec, so
            # it is an object like any other ingredient. Passing the bare
            # string here did not merely fail to load the material: Silent Gear
            # throws MaterialJsonException out of its reload listener, which
            # crashed the client the moment the player clicked Create New World.
            "categories": ["metal"],
            # Required, with no default. Empty means "usable in every gear
            # type", which is what these two materials want - but leaving the
            # key out is a hard parse failure, not the empty case.
            "gear_type_blacklist": [],
            "ingredient": ing(ingredient),
            # Required, same story. A substitute lets one material be crafted
            # from a different item for a specific part - iron rods for an iron
            # rod, say. Neither Elysium material has one.
            "part_substitutes": {},
        },
        "display": {
            "color": colour,
            "main_texture_type": "HIGH_CONTRAST",
            "name": {"translate": f"material.elysium.{name}"},
        },
        "properties": {
            "silentgear:main": {
                "durability": float(durability),
                "armor_durability": round(durability / 80.0, 1),
                # Armour is a total plus a per-slot split, as vanilla iron is.
                "armor": float(armour),
                "armor/helmet": round(armour * 0.15, 1),
                "armor/chestplate": round(armour * 0.40, 1),
                "armor/leggings": round(armour * 0.32, 1),
                "armor/boots": round(armour * 0.13, 1),
                "magic_armor": round(armour * 0.30, 1),
                "attack_damage": float(damage),
                "attack_speed": float(speed),
                "harvest_speed": float(harvest),
                "enchantment_value": float(enchantability),
                "rarity": float(rarity),
                "harvest_tier": {
                    "name": tier_name,
                    "level_hint": tier_hint,
                    "incorrect_blocks_for_tool": incorrect_tag,
                },
            }
        },
    }


write("data/elysium/silentgear_materials/neutronium.json", silentgear_material(
    "neutronium", "#FF4A4A4A", f"{NS}:neutronium_ingot",
    durability=2500, armour=22.0, damage=5.0, speed=0.0, harvest=12.0,
    enchantability=25, rarity=80,
    tier_name="netherite", tier_hint="4",
    incorrect_tag="minecraft:incorrect_for_netherite_tool"))

write("data/elysium/silentgear_materials/aetherium.json", silentgear_material(
    "aetherium", "#FF7FD4E8", f"{NS}:aetherium_ingot",
    durability=1800, armour=17.0, damage=3.5, speed=0.2, harvest=10.0,
    enchantability=22, rarity=60,
    tier_name="diamond", tier_hint="3",
    incorrect_tag="minecraft:incorrect_for_diamond_tool"))

print("data + asset JSON regenerated")
