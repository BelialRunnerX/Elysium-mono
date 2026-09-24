#!/usr/bin/env python3
"""Writes the Extraction Ledger, a Pufferfish's Skills category, as a datapack.

Why this is generated
---------------------
Forty-five skills is forty-five definitions, forty-five placements and sixty-odd
connections across four JSON files that all have to agree about the same ids. By
hand that is four places for a typo to hide, and the symptom of a typo is a node
that silently does not appear.

Stated as a table it is one place. The layout is computed, the connections are
derived from the shape rather than listed, and the ids exist once.

Why it ships inside the mod
---------------------------
A mod's resources are a datapack. Putting the category in elysium-core means a
player who installs Elysium and Pufferfish's Skills gets the tree, with no third
download and no version of it drifting from the mod it is about.

What the tree is
----------------
"The Extraction Ledger": the Empire's record of what is taken out of the world
and by whom. The design follows the mod's own central tension — extraction is
watched, and what you take is weighed against what the Empire thinks of you — so
the tree is not a list of upgrades but a series of commitments.

Three of those commitments are exclusive, at increasing depth:

  Method     (shallow)  Sanctioned  | Clandestine   — how you extract
  Posture    (middle)   Ordered     | Irregular     — how you fight
  Allegiance (deep)     Warranted   | Unsworn Writ  — whose enemies are yours

Each exclusive pair sets a scoreboard tag that Elysium reads; see
ElysiumDoctrine, which is the single list both halves are checked against.

Versions
--------
ATTRIBUTE_STYLE and CONNECTION_STYLE are the two things that differ between
Minecraft and Pufferfish versions. Both are one constant here rather than
scattered through the output.
"""

import json
import pathlib

HERE = pathlib.Path(__file__).resolve().parent
RES = HERE.parent / "src/main/resources"
NS = "elysium"
CATEGORY = "extraction_ledger"
OUT = RES / f"data/{NS}/puffish_skills/categories/{CATEGORY}"
CONFIG = RES / f"data/{NS}/puffish_skills/config.json"

# The mod's config format version. The documentation is explicit: set it to 3.
CONFIG_VERSION = 3

# --- the two version-sensitive decisions, in one place ----------------------
#
# Attributes were flattened -- generic./player. prefixes dropped, operations
# renamed -- in 1.21.2, NOT in 1.21. Elysium targets 1.21.1, which is one
# version below the change, so the prefixed spelling is the correct one here.
#
# This was got wrong once, and the way it failed is worth keeping written down:
# every node loaded, every node appeared, and every bonus was inert. The mod
# reported it, but only as a warning, and only with "show_warnings": true. A
# tree that looks right and does nothing is the expensive kind of wrong.
#
# The fix was not reasoning harder. It was reading a pack that already worked
# -- Stronger Skill Tree, in the same instance -- which spells it
# "generic.max_health", and then confirming against the game itself:
#   javap -c net.minecraft.world.entity.ai.attributes.Attributes
# Set this to "modern" only for a build on 1.21.2 or later.
ATTRIBUTE_STYLE = "legacy"

# Every vanilla attribute, spelled the way the registry spells it in 1.21.1.
#
# A table rather than a rule, because the prefix is not derivable from the
# name: block_break_speed is player., burning_time is generic., and nothing
# about either word says which. The previous version guessed with a set of ten
# "generic" names and defaulted the rest to player., which would have turned
# oxygen_bonus into player.oxygen_bonus -- the same class of silent failure,
# fixed at nine of the twenty attributes this tree uses.
#
# Source: the strings passed to register() in the class named above.
VANILLA_ATTRIBUTES = {
    name.split(".", 1)[1]: name
    for name in (
        "generic.armor",
        "generic.armor_toughness",
        "generic.attack_damage",
        "generic.attack_knockback",
        "generic.attack_speed",
        "generic.burning_time",
        "generic.explosion_knockback_resistance",
        "generic.fall_damage_multiplier",
        "generic.flying_speed",
        "generic.follow_range",
        "generic.gravity",
        "generic.jump_strength",
        "generic.knockback_resistance",
        "generic.luck",
        "generic.max_absorption",
        "generic.max_health",
        "generic.movement_efficiency",
        "generic.movement_speed",
        "generic.oxygen_bonus",
        "generic.safe_fall_distance",
        "generic.scale",
        "generic.step_height",
        "generic.water_movement_efficiency",
        "player.block_break_speed",
        "player.block_interaction_range",
        "player.entity_interaction_range",
        "player.mining_efficiency",
        "player.sneaking_speed",
        "player.submerged_mining_speed",
        "player.sweeping_damage_ratio",
    )
}

# "grouped" is the object form with normal and exclusive lists, which is what
# the mod's own editor writes and what exclusive commitments need. "pairs" is
# the older flat array, kept because the documentation page for the file still
# shows it and a pack on an older build will want it.
CONNECTION_STYLE = "grouped"

# The operation names are the mod's own, not Minecraft's, and they do not move
# with the Minecraft version -- so unlike the attribute spelling above, this is
# not keyed on ATTRIBUTE_STYLE. The five it accepts, read out of the strings in
# net.puffish.skillsmod.api.json.BuiltinJson in the 0.18.3 jar:
#
#   addition  multiply_base  add_multiplied_base  multiply_total
#   add_multiplied_total
#
# Worth noting what is not there: "add_value", Minecraft's own 1.21 name for
# addition, which the previous version of this file emitted for every additive
# bonus. Had the attribute ids been right, those rewards would still have been
# refused -- a second fault hiding behind the first.
OPERATIONS = {"add": "addition", "scale": "multiply_base"}


def attribute(name):
    """A vanilla attribute id in the spelling this build's Minecraft uses.

    An unknown name stops generation here rather than shipping. The game only
    warns about one, and only if the player has turned warnings on, so this is
    the last place a typo is loud.
    """
    if name not in VANILLA_ATTRIBUTES:
        raise SystemExit(
            f"gen_skilltree: no vanilla attribute named {name!r}.\n"
            "  Names are the unprefixed form, e.g. max_health, "
            "block_break_speed.\n"
            f"  Known: {', '.join(sorted(VANILLA_ATTRIBUTES))}"
        )
    if ATTRIBUTE_STYLE == "modern":
        return f"minecraft:{name}"
    return f"minecraft:{VANILLA_ATTRIBUTES[name]}"


# Every reward is written with "required": false.
#
# The mod treats a reward it cannot parse as an error by default, and an error
# in one definition fails the whole category — which takes the pack down with
# it. That is how forty-five skills and somebody else's two working trees
# disappeared at once behind a single wrong item id.
#
# With this, a reward that will not load is a warning: the node still exists,
# the tree still opens, and the worst case is one bonus that does nothing
# instead of an empty skills screen. It is the difference between a mistake
# that is visible and one that is silent and total.
NON_FATAL = False


def reward(kind, data):
    return {"type": kind, "data": data, "required": NON_FATAL}


def add(name, value):
    return reward("puffish_skills:attribute",
                  {"attribute": attribute(name), "value": value,
                   "operation": OPERATIONS["add"]})


def scale(name, value):
    """A proportional bonus. 0.06 is six per cent."""
    return reward("puffish_skills:attribute",
                  {"attribute": attribute(name), "value": value,
                   "operation": OPERATIONS["scale"]})


def doctrine(tag):
    """Commit to one of Elysium's doctrines.

    An unlock/lock pair rather than an immediate command, so that refunding the
    node takes the commitment back. The mod's own documentation is emphatic
    about this and it is the difference between a respec that works and a
    player who is permanently Sanctioned because they once were.
    """
    full = "elysium.doctrine." + tag
    return reward("puffish_skills:command",
                  {"unlock_command": f"tag @s add {full}",
                   "lock_command": f"tag @s remove {full}"})


# ===========================================================================
# The tree
# ===========================================================================
#
# Each entry: id, title, blurb, icon item, frame, grid position, rewards, and
# the ids it hangs from. Positions are in grid cells; SPACING turns them into
# the pixel coordinates the mod wants, so the shape can be adjusted without
# touching forty-five numbers.
SPACING = 28

TASK, GOAL, CHALLENGE = "task", "goal", "challenge"


def node(nid, title, blurb, icon, col, row, rewards,
         after=(), frame=TASK, cost=1, spent=0, size=None):
    return {"id": nid, "title": title, "blurb": blurb, "icon": icon,
            "col": col, "row": row, "rewards": list(rewards),
            "after": list(after), "frame": frame, "cost": cost,
            "spent": spent, "size": size}


NODES = [
    # --- the spine: what everyone learns -------------------------------
    node("enrolled", "Enrolled", "Your name is in the ledger. Everything after "
         "this is an entry against it.", "elysium:imperial_codex", 0, 0,
         [add("oxygen_bonus", 1)], frame=GOAL, size=2.0),

    node("ledger_kept", "A Ledger Kept", "You write down what you take. It "
         "turns out to be worth knowing.", "minecraft:writable_book", 0, 2,
         [add("luck", 0.5)], after=["enrolled"]),

    node("field_kit", "Field Kit", "Longer arms and better tools than the "
         "requisition form admits to.", "elysium:field_scanner", 0, 4,
         [add("block_interaction_range", 0.75)], after=["ledger_kept"]),

    node("long_shift", "The Long Shift", "You have learned to keep going.",
         "minecraft:clock", 0, 10, [add("max_health", 2)],
         after=["field_kit"], spent=8),

    node("veterans_footing", "Veteran's Footing", "Whatever the floor does, "
         "you have felt it do that before.", "minecraft:iron_boots", 0, 12,
         [add("safe_fall_distance", 2), add("knockback_resistance", 0.1)],
         after=["long_shift"]),

    # --- the Seam: taking things out of the ground -----------------------
    node("seam_sense", "Seam Sense", "You can tell where the vein is going "
         "before you have followed it.", "minecraft:iron_pickaxe", -2, 4,
         [add("mining_efficiency", 0.5)], after=["ledger_kept"]),

    node("steady_hand", "Steady Hand", "Fewer swings, in the same place.",
         "minecraft:golden_pickaxe", -4, 4, [scale("block_break_speed", 0.06)],
         after=["seam_sense"]),

    node("pit_lungs", "Pit Lungs", "Bad air and deep water stopped being an "
         "argument some time ago.", "minecraft:turtle_helmet", -2, 6,
         [add("oxygen_bonus", 2), scale("submerged_mining_speed", 0.5)],
         after=["seam_sense"]),

    node("sure_footing", "Sure Footing", "Quiet, and quick about it.",
         "minecraft:leather_boots", -4, 6, [scale("sneaking_speed", 0.15)],
         after=["steady_hand", "pit_lungs"]),

    # --- Method: the first commitment ------------------------------------
    node("sanctioned", "Sanctioned Extraction", "You file the paperwork. The "
         "Empire knows exactly what you are taking, minds a good deal more "
         "about it, and pays you for the honesty.", "minecraft:paper", -2, 8,
         [doctrine("sanctioned"), add("luck", 1)], after=["sure_footing"],
         frame=GOAL, size=1.5),

    node("clandestine", "Clandestine Extraction", "You do not file anything. "
         "Less is noticed. Nothing is paid.", "minecraft:barrel", -5, 8,
         [doctrine("clandestine"), scale("sneaking_speed", 0.2)],
         after=["sure_footing"], frame=GOAL, size=1.5),

    # --- Sanctioned branch -----------------------------------------------
    node("licensed_assay", "Licensed Assay", "The office tells you what is "
         "worth digging for.", "minecraft:amethyst_shard", -1, 10,
         [add("luck", 1)], after=["sanctioned"]),

    node("crown_tools", "Crown Tools", "Issued, stamped, and better than "
         "yours.", "minecraft:diamond_pickaxe", -3, 10,
         [add("mining_efficiency", 1)], after=["sanctioned"]),

    node("levied_haul", "Levied Haul", "They take a share. What is left still "
         "comes up faster.", "minecraft:minecart", -1, 12,
         [scale("block_break_speed", 0.08)], after=["licensed_assay"]),

    node("bonded_depth", "Bonded Depth", "A licence that reaches further down "
         "than anyone reads.", "minecraft:spyglass", -3, 12,
         [add("block_interaction_range", 1)], after=["crown_tools"]),

    # --- Clandestine branch -----------------------------------------------
    node("muffled_stroke", "Muffled Stroke", "A pick wrapped in rag still "
         "breaks stone.", "minecraft:white_wool", -6, 10,
         [scale("block_break_speed", 0.08)], after=["clandestine"]),

    node("spoil_cache", "Spoil Cache", "You have somewhere to put it that is "
         "not on any map.", "minecraft:chest", -4, 10, [add("luck", 1)],
         after=["clandestine"]),

    node("night_shift", "Night Shift", "The hours nobody audits.",
         "minecraft:black_dye", -6, 12, [scale("sneaking_speed", 0.2)],
         after=["muffled_stroke"]),

    node("unlit_ways", "Unlit Ways", "You know the tunnels that are not lit "
         "because nobody official uses them.", "minecraft:soul_lantern",
         -4, 12, [scale("movement_speed", 0.05)], after=["spoil_cache"]),
]

NODES += [
    # --- the Line: what happens when something objects -------------------
    node("close_order", "Close Order", "You have been taught where to stand.",
         "minecraft:iron_sword", 2, 4, [add("attack_damage", 1)],
         after=["ledger_kept"]),

    node("standing_guard", "Standing Guard", "And how long to stand there.",
         "minecraft:shield", 4, 4, [add("armor", 2)], after=["close_order"]),

    node("quick_hands", "Quick Hands", "Recovery between blows, which is most "
         "of a fight.", "minecraft:feather", 2, 6,
         [add("attack_speed", 0.2)], after=["close_order"]),

    node("braced", "Braced", "Harder to move than you look.",
         "minecraft:anvil", 4, 6, [add("knockback_resistance", 0.15)],
         after=["standing_guard", "quick_hands"]),

    # --- Posture: the second commitment ----------------------------------
    node("ordered", "Ordered Line", "Hold, and let it come to you. Nothing "
         "gets past and nothing goes quickly.", "minecraft:iron_chestplate",
         2, 8, [add("armor", 2), add("armor_toughness", 2),
                add("knockback_resistance", 0.1)],
         after=["braced"], frame=GOAL, size=1.5),

    node("irregular", "Irregular Order", "Go and find it. Faster, sharper, "
         "and considerably easier to kill.", "minecraft:iron_axe", 5, 8,
         [add("attack_damage", 2), scale("movement_speed", 0.06)],
         after=["braced"], frame=GOAL, size=1.5),

    # --- Ordered branch ---------------------------------------------------
    node("shield_wall", "Shield Wall", "Two of you is four times the wall.",
         "minecraft:netherite_chestplate", 1, 10, [add("armor", 3)],
         after=["ordered"]),

    node("dug_in", "Dug In", "Explosions are somebody else's problem.",
         "minecraft:obsidian", 3, 10,
         [add("explosion_knockback_resistance", 0.5)], after=["ordered"]),

    node("endurance_drill", "Endurance Drill", "The fight lasts as long as "
         "you do.", "minecraft:golden_apple", 1, 12, [add("max_health", 4)],
         after=["shield_wall"]),

    node("fire_discipline", "Fire Discipline", "You burn for a shorter time "
         "than the situation deserves.", "minecraft:magma_cream", 3, 12,
         [scale("burning_time", -0.4)], after=["dug_in"]),

    # --- Irregular branch -------------------------------------------------
    node("opening_cut", "Opening Cut", "Whatever it was about to do, it is "
         "not doing it now.", "minecraft:diamond_sword", 6, 10,
         [add("attack_damage", 2)], after=["irregular"]),

    node("long_arm", "Long Arm", "You are further away than it thinks.",
         "minecraft:trident", 4, 10, [add("entity_interaction_range", 1)],
         after=["irregular"]),

    node("through_and_through", "Through And Through", "One swing, several "
         "answers.", "minecraft:netherite_sword", 6, 12,
         [add("sweeping_damage_ratio", 0.4)], after=["opening_cut"]),

    node("loose_footing", "Loose Footing", "Never quite where the blow lands.",
         "minecraft:rabbit_foot", 4, 12, [scale("movement_speed", 0.06)],
         after=["long_arm"]),

    # --- Acclimation: what the world does to you -------------------------
    node("acclimatised", "Acclimatised", "The weather still happens. It has "
         "stopped being an event.", "elysium:thermal_ration", -1, 14,
         [doctrine("acclimatised")], after=["levied_haul", "night_shift"],
         frame=GOAL, size=1.5, spent=14),

    node("deep_lunged", "Deep-Lunged", "You have spent so long in it that it "
         "has had to come to terms with you.", "elysium:pressure_ration",
         -5, 14, [doctrine("deep_lunged")],
         after=["bonded_depth", "unlit_ways"], frame=GOAL, size=1.5, spent=14),

    # --- Allegiance: the last commitment ---------------------------------
    node("warranted", "Imperial Warrant", "A piece of paper that makes the "
         "Empire's enemies your business, and says so out loud.",
         "minecraft:golden_helmet", 2, 14, [doctrine("warranted")],
         after=["endurance_drill", "through_and_through"],
         frame=CHALLENGE, size=1.5, spent=18),

    node("unsworn_writ", "Unsworn Writ", "A different piece of paper, which "
         "it would be better nobody found on you.", "minecraft:wither_rose",
         5, 14, [doctrine("unsworn_writ")],
         after=["fire_discipline", "loose_footing"],
         frame=CHALLENGE, size=1.5, spent=18),

    # --- deeper in each branch --------------------------------------------
    node("assay_office", "The Assay Office", "Somebody whose whole job is "
         "knowing what a rock is worth, and who now knows you.",
         "minecraft:emerald", -1, 16, [add("luck", 1)],
         after=["acclimatised"], spent=16),

    node("dead_drop", "Dead Drop", "A hole in a wall that has never once been "
         "searched.", "minecraft:ender_chest", -5, 16,
         [add("block_interaction_range", 0.75), scale("sneaking_speed", 0.1)],
         after=["deep_lunged"], spent=16),

    node("field_surgeon", "Field Surgeon", "Somebody in the line who knows "
         "which wounds are the ones that matter.", "minecraft:glistering_melon_slice",
         2, 16, [add("max_health", 4)], after=["warranted"], spent=16),

    node("hunters_pace", "Hunter's Pace", "Fast enough that the first blow is "
         "usually yours.", "minecraft:sugar", 5, 16,
         [add("attack_speed", 0.3)], after=["unsworn_writ"], spent=16),

    node("second_wind", "Second Wind", "The part of the shift after you "
         "stopped counting.", "minecraft:bread", 0, 14,
         [add("max_health", 2), scale("movement_efficiency", 0.2)],
         after=["veterans_footing"], spent=12),

    # --- capstones ---------------------------------------------------------
    node("master_of_the_seam", "Master Of The Seam", "There is nothing under "
         "this world you have not already had out of it once.",
         "elysium:neutronium_ingot", -3, 16,
         [add("mining_efficiency", 2), add("luck", 1),
          scale("block_break_speed", 0.1)],
         after=["assay_office", "dead_drop"], frame=CHALLENGE, cost=3,
         spent=24, size=2.0),

    node("master_of_the_line", "Master Of The Line", "Whatever the Empire "
         "writes next to your name, it writes carefully.",
         "elysium:aetherium_ingot", 3, 16,
         [add("attack_damage", 3), add("armor", 2), add("max_health", 4)],
         after=["field_surgeon", "hunters_pace"], frame=CHALLENGE, cost=3,
         spent=24, size=2.0),

    node("the_whole_ledger", "The Whole Ledger", "Both columns, in your own "
         "hand.", "elysium:imperial_codex", 0, 18,
         [add("luck", 1), add("max_health", 4),
          add("block_interaction_range", 1)],
         after=["master_of_the_seam", "master_of_the_line", "second_wind"],
         frame=CHALLENGE, cost=5, spent=36, size=2.5),
]

# Which pairs the player must choose between. Derived from the three
# commitments rather than listed twice: the same pairs are declared in
# ElysiumDoctrine.OPPOSED, and validate.py checks the two agree.
EXCLUSIVE_PAIRS = [
    ("sanctioned", "clandestine"),
    ("ordered", "irregular"),
    ("warranted", "unsworn_writ"),
    ("acclimatised", "deep_lunged"),
]


# ===========================================================================
# Writers
# ===========================================================================

def write(rel, obj):
    path = OUT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(obj, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def text(key):
    """A translatable text object.

    Translatable rather than a bare string, so the tree can be localised and so
    validate.py can check every line of it exists in the language file — a
    skill whose title is a raw key is a skill nobody can read, and it looks
    exactly like a skill.
    """
    return {"translate": key}


def title_key(nid):
    return f"elysium.skilltree.{nid}"


def blurb_key(nid):
    return f"elysium.skilltree.{nid}.desc"


def build():
    by_id = {entry["id"]: entry for entry in NODES}
    if len(by_id) != len(NODES):
        raise SystemExit("two skills share an id")

    definitions = {}
    skills = {}
    for entry in NODES:
        nid = entry["id"]
        definition = {
            "title": text(title_key(nid)),
            "description": text(blurb_key(nid)),
            "icon": {"type": "item", "data": {"item": entry["icon"]}},
            "frame": {"type": "advancement", "data": {"frame": entry["frame"]}},
            "rewards": entry["rewards"],
        }
        if entry["cost"] != 1:
            definition["cost"] = entry["cost"]
        if entry["spent"]:
            definition["required_spent_points"] = entry["spent"]
        if entry["size"]:
            definition["size"] = entry["size"]
        definitions[nid] = definition

        placed = {"x": entry["col"] * SPACING,
                  "y": entry["row"] * SPACING,
                  "definition": nid}
        if not entry["after"]:
            placed["root"] = True
        skills[nid] = placed

    # Connections are derived from "after", so the shape of the tree is stated
    # once. A node naming a parent that does not exist is caught here rather
    # than by the game deciding the connection simply is not there.
    normal = []
    for entry in NODES:
        for parent in entry["after"]:
            if parent not in by_id:
                raise SystemExit(f"{entry['id']} hangs from unknown {parent}")
            normal.append([parent, entry["id"]])

    exclusive = []
    for first, second in EXCLUSIVE_PAIRS:
        for nid in (first, second):
            if nid not in by_id:
                raise SystemExit(f"exclusive pair names unknown {nid}")
        exclusive.append([first, second])

    if CONNECTION_STYLE == "grouped":
        # Each group is an OBJECT, not a list. The mod said so, in as many
        # words: "Expected `normal` to be an object". A group holds
        # `unidirectional` and `bidirectional` arrays of pairs.
        #
        # Normal connections are unidirectional because they unlock forward:
        # taking the parent makes the child available, and not the reverse.
        # Exclusive connections are bidirectional because exclusion is mutual —
        # if taking Sanctioned rules out Clandestine, then taking Clandestine
        # has to rule out Sanctioned, and a one-way exclusion would let a
        # player have both by picking them in the right order.
        connections = {
            "normal": {"unidirectional": normal},
            "exclusive": {"bidirectional": exclusive},
        }
    else:
        # The flat form cannot express exclusivity. Emitting it silently would
        # turn three commitments into six free nodes, so say so.
        print("  WARNING: the flat connection form has no exclusive groups; "
              "the three commitments become ordinary nodes")
        connections = normal

    category = {
        "title": text("elysium.skilltree.category"),
        "description": text("elysium.skilltree.category.desc"),
        "icon": {"type": "item", "data": {"item": "elysium:imperial_codex"}},
        # Required, not optional. It was dropped once on the theory that its
        # format was unverified, and the mod's answer was "Expected
        # `background` to exist" — which failed the category, which failed the
        # pack, which emptied the player's whole skills screen.
        #
        # The plain-string form is the documented shorthand for an
        # advancement-style tiled background, and points at a texture vanilla
        # has shipped since advancements existed.
        "background": "minecraft:textures/gui/advancements/backgrounds/stone.png",
        "unlocked_by_default": True,
        "exclusive_root": False,
        "starting_points": 1,
    }

    # The config that names the category, which is what makes the tree appear
    # with nothing for the player to do.
    #
    # It was removed once, on the theory that it had overwritten the player's
    # other trees. That theory was wrong. The mod's loader reads every
    # config.json it can find under data/<any namespace>/puffish_skills/ and
    # merges them into one cumulative map — but a pack whose category fails to
    # parse contributes nothing, and the skills screen came up empty.
    #
    # The actual fault was an icon naming an item that does not exist. Fixing
    # the guess was not the lesson; the lesson is that one bad field took down
    # forty-five skills and two unrelated trees without a word, which is why
    # every reward above is now non-fatal and why validate.py checks that every
    # icon is a real item.
    # gen_data.py wipes data/ at the start of every regen, by design, so this
    # directory does not exist when this runs as part of regen.sh. It only
    # ever worked by hand because the directory happened to be there.
    CONFIG.parent.mkdir(parents=True, exist_ok=True)
    with open(CONFIG, "w", encoding="utf-8") as handle:
        json.dump({"version": CONFIG_VERSION, "categories": [CATEGORY]},
                  handle, indent=2)
        handle.write("\n")

    write("category.json", category)
    write("definitions.json", definitions)
    write("skills.json", skills)
    write("connections.json", connections)
    write("experience.json", experience())
    write_ore_tags()
    return by_id, normal, exclusive


# ---------------------------------------------------------------------------
# Earning points
# ---------------------------------------------------------------------------
#
# A category with no experience.json has no way to earn a point at all: the
# tree opens, shows forty-five skills and the one starting point, and after
# that nothing a player does moves it. That shipped. This is the fix.
#
# The tree is the Extraction Ledger, so it pays for exactly the two things it
# is about: taking things out of the ground, and fighting.
#
# Every key and operation here was confirmed against the 0.18.3 jar rather
# than copied from another pack — the attribute spelling taught that lesson.
# In particular silk touch is detected with a vanilla item predicate
# ("predicates" / "minecraft:enchantments"), the form Minecraft's own
# diamond_ore loot table uses. The NBT form some older packs use,
# {Enchantments:[...]}, cannot match on 1.21, where enchantments are an item
# component and not NBT — so a pack written that way pays for silk-touched ore
# without knowing it.

def _test_block(block):
    return {"operations": [{"type": "get_mined_block_state"},
                           {"type": "puffish_skills:test",
                            "data": {"block": block}}]}


SILK_TOUCH = {"operations": [
    {"type": "get_tool_item_stack"},
    {"type": "puffish_skills:test", "data": {"predicates": {
        "minecraft:enchantments": [
            {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}},
]}

# What a vein is worth. The Empire's own three ores are the rare find the
# whole mod is built around, so they are worth three ordinary ores.
EXPERIENCE_EMPIRE_ORE = 25
EXPERIENCE_ORE = 8
EXPERIENCE_DEEPSLATE = 2
EXPERIENCE_STONE = 1

# What a level costs: 55 for the first point, rising by 15, capped at 400.
# About 13,000 to fill every node — a tree that fills over twenty-odd evenings
# of mining and fighting rather than one, with the first few points arriving
# in the first session so the screen is not empty for long.
LEVEL_BASE = 40
LEVEL_STEP = 15
LEVEL_CAP = 400


def experience():
    """experience.json: mining and combat, and nothing that can be farmed."""
    mine = {
        "type": "puffish_skills:mine_block",
        "data": {
            "variables": {
                "silk_touch": SILK_TOUCH,
                # A block tag of our own rather than three variables, so a
                # fourth Empire ore joins by editing a tag, not this file.
                "empire_ore": _test_block(f"#{NS}:empire_ores"),
                "ore": _test_block("#c:ores"),
                "deepslate": _test_block("minecraft:deepslate"),
                "stone": _test_block("#minecraft:base_stone_overworld"),
            },
            # Written so that no two conditions can hold at once. That keeps
            # the reward right whether the mod takes the first match or adds
            # them all up, which is a thing the documentation does not say.
            #
            # Silk touch pays nothing, for stone as well as ore: a silk-touched
            # block can be put down and mined again, which is a loop, and a
            # loop in an experience source is the one bug a player will find
            # and never report.
            "experience": [
                {"condition": "!silk_touch & empire_ore",
                 "expression": str(EXPERIENCE_EMPIRE_ORE)},
                {"condition": "!silk_touch & ore & !empire_ore",
                 "expression": str(EXPERIENCE_ORE)},
                {"condition": "!silk_touch & deepslate & !ore",
                 "expression": str(EXPERIENCE_DEEPSLATE)},
                {"condition": "!silk_touch & stone & !deepslate & !ore",
                 "expression": str(EXPERIENCE_STONE)},
            ],
        },
    }
    fight = {
        "type": "puffish_skills:kill_entity",
        "data": {
            "variables": {
                "dropped_xp": {"operations": [
                    {"type": "get_dropped_experience"}]},
                "max_health": {"operations": [
                    {"type": "get_killed_living_entity"},
                    {"type": "get_max_health"}]},
            },
            # What it dropped, plus a tenth of how tough it was: a zombie is
            # worth seven, an Enforcer more, a boss a great deal more.
            "experience": "dropped_xp + max_health / 10",
            # A mob farm pays for the first fifteen kills in a chunk every
            # five minutes and then nothing.
            "anti_farming": {"limit_per_chunk": 15,
                             "reset_after_seconds": 300},
        },
    }
    return {
        "experience_per_level": {
            "type": "expression",
            "data": {"expression":
                     f"min({LEVEL_BASE} + level * {LEVEL_STEP}, {LEVEL_CAP})"},
        },
        "sources": [mine, fight],
    }


EMPIRE_ORES = ["aetherium_ore", "neutronium_ore", "voidglass_ore"]
EMPIRE_ORE_TAG = RES / f"data/{NS}/tags/block/empire_ores.json"
# The common ore tag, so vein miners, ore finders and this tree's #c:ores all
# see the three. Nothing in Elysium reads it — ElysiumHooks keeps its own set —
# so adding them changes no behaviour of ours.
COMMON_ORE_TAG = RES / "data/c/tags/block/ores.json"


def write_ore_tags():
    ids = [f"{NS}:{name}" for name in EMPIRE_ORES]
    for path in (EMPIRE_ORE_TAG, COMMON_ORE_TAG):
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump({"replace": False, "values": ids}, handle, indent=2)
            handle.write("\n")


def lang():
    """Every line of text the tree shows, for the mod's language file."""
    out = {
        "elysium.skilltree.category": "The Extraction Ledger",
        "elysium.skilltree.category.desc":
            "The Empire's record of what is taken out of the world, and by "
            "whom. Every entry is a commitment.",
    }
    for entry in NODES:
        out[title_key(entry["id"])] = entry["title"]
        out[blurb_key(entry["id"])] = entry["blurb"]
    return out


if __name__ == "__main__":
    by_id, normal, exclusive = build()

    # The lang entries go into the mod's own file, beside everything else.
    lang_path = RES / f"assets/{NS}/lang/en_us.json"
    existing = json.load(open(lang_path, encoding="utf-8"))
    existing.update(lang())
    with open(lang_path, "w", encoding="utf-8") as handle:
        json.dump(existing, handle, indent=2, ensure_ascii=False)
        handle.write("\n")

    roots = [entry["id"] for entry in NODES if not entry["after"]]
    doctrines = sorted({
        reward["data"]["unlock_command"].rsplit(" ", 1)[1]
        for entry in NODES for reward in entry["rewards"]
        if reward["type"] == "puffish_skills:command"})
    print(f"skill tree         : {len(NODES)} skills, {len(normal)} connections, "
          f"{len(exclusive)} exclusive pair(s), {len(roots)} root")
    print(f"                     {len(doctrines)} doctrine tag(s): "
          f"{', '.join(d.rsplit('.', 1)[1] for d in doctrines)}")
    print(f"                     attributes in {ATTRIBUTE_STYLE} style, "
          f"connections {CONNECTION_STYLE}")
