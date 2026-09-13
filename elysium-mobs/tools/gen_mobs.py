#!/usr/bin/env python3
"""
Generate the mob models, renderers and texture sheets from one table.

Run from the repo root:   python3 tools/gen_mobs.py

<h2>Why generate instead of hand-writing</h2>

An entity model and its texture are two halves of the same fact. The model says
"this box's faces are at (16, 8) on the sheet, and it is 6x8x4"; the texture has
to have that box's unwrapped faces at exactly that spot. Hand-written, those two
drift the first time a box moves, and the symptom is a mob wearing the wrong
part of its own skin - which compiles, loads, and looks like nonsense.

So the box table below is the single source, and both the Java and the PNG come
out of it. A box cannot be in the model and missing from the sheet.

<h2>What is generated</h2>

  * One EntityModel per family, with a real ModelPart hierarchy.
  * One renderer per family.
  * One 64x64 texture sheet per sub-variant, palette-shifted per variant.
  * The lang entries for families, variants, abilities and bosses.

Everything it writes is overwritten on each run, because all of it is derived.
Nothing here should be edited by hand; edit the table and re-run.
"""
import json
import os
import pathlib
import sys

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

from PIL import Image

# The UV packer, the texture unwrapper and the MeshDefinition emitter live in
# art/boxmodel.py, shared with elysium-npcs. They were here until there was a
# second caller; copying them would have meant two packers that had to agree
# about Minecraft's cross layout forever.
from boxmodel import hex_to_rgb, part_lines, smallest_sheet, write_texture

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/elysium/mobs"
RES = ROOT / "src/main/resources/assets/elysiummobs"

# The sheet size is MEASURED, not chosen — smallest_sheet does it per family.
#
# It used to be a constant 64 with a comment explaining that the detail parts
# fit inside it "with room to spare". That was true when it was written and
# stopped being true the moment a second pass of detail was added: the packer
# refused, correctly, rather than overlapping two boxes and producing mobs
# textured with each other's parts and no error anywhere.
#
# Replacing 64 with a constant 128 would have worked and been wrong in the other
# direction, for the reason the old comment gave against it: thirty variant
# sheets at 128 where 96 would do is wasted texture memory forever. So each
# family now asks the same helper the bosses already used, and the model is told
# the same figure so the two cannot drift.

# ---------------------------------------------------------------------------
# The table
# ---------------------------------------------------------------------------
#
# A box is (name, originX, originY, originZ, sizeX, sizeY, sizeZ,
# pivotX, pivotY, pivotZ) and optionally an eleventh element: the name of the
# part it hangs from. Minecraft's entity coordinate space has Y growing
# downward from the pivot, which is why "head" origins are negative.
#
# A parented box is a child in the ModelPart tree, so it inherits its parent's
# rotation for free - a crest on a head turns with the head, a pauldron on an
# arm swings with the arm. Bolting detail to the root instead would leave a
# helmet crest hanging in the air while the helmet looked around, which is the
# single most obvious way for added detail to look worse than none.
#
# UVs are NOT in the table. They are assigned by the packer below, because
# hand-assigned UVs is exactly how the first draft of this file put a reaver's
# legs on top of its body: two boxes at different offsets whose unwrapped
# footprints overlapped, which no amount of care catches by eye. A packer
# cannot make that mistake.
#
# The families are shaped to be told apart in silhouette at a distance, which
# is the only thing that matters at 16 blocks:
#   scavenger - small, hunched, long arms
#   reaver    - wide, top-heavy, tiny head
#   whisper   - tall, thin, no bulk at all
#   drone     - floating core with fins, no legs
#   lictor    - broad shouldered slab, helmeted
#   adept     - narrow, robed, oversized head

FAMILIES = {
    "scavenger": {
        "height": 1.5,
        "boxes": [
            ("head", -3, -6, -3, 6, 6, 6, 0, 12, 0),
            ("body", -4, 0, -2, 8, 8, 4, 0, 12, 0),
            ("arm_left", -1, -1, -2, 3, 10, 4, 5, 13, 0),
            ("arm_right", -2, -1, -2, 3, 10, 4, -5, 13, 0),
            ("leg_left", -2, 0, -2, 4, 6, 4, 2, 18, 0),
            ("leg_right", -2, 0, -2, 4, 6, 4, -2, 18, 0),
            # Hunched and scavenging: a hood over the skull, a pauldron of
            # salvage on the working arm, a satchel of it on the back.
            ("hood", -4, -8, -4, 8, 3, 8, 0, 0, 0, "head"),
            ("pauldron_left", -1, -2, -3, 5, 3, 6, 0, 0, 0, "arm_left"),
            ("satchel", -3, 3, 2, 6, 5, 3, 0, 0, 0, "body"),
            # A second pass of detail. The first gave each family a silhouette;
            # these give it a surface, which is what a player sees once the
            # thing is close enough to be hitting them.
            ("scrap_horn", 2, -9, -1, 2, 3, 2, 0, 0, 0, "head"),
            ("bracer_right", -3, 4, -3, 5, 3, 6, 0, 0, 0, "arm_right"),
            ("knee_left", -3, 3, -3, 6, 2, 6, 0, 0, 0, "leg_left"),
            ("knee_right", -3, 3, -3, 6, 2, 6, 0, 0, 0, "leg_right"),
            ("belt", -5, 7, -3, 10, 2, 6, 0, 0, 0, "body"),
        ],
    },
    "reaver": {
        "height": 2.3,
        "boxes": [
            ("head", -2, -4, -2, 4, 4, 4, 0, 6, 0),
            ("body", -7, 0, -4, 14, 12, 8, 0, 6, 0),
            ("arm_left", 0, -2, -3, 6, 16, 6, 7, 8, 0),
            ("arm_right", -6, -2, -3, 6, 16, 6, -7, 8, 0),
            ("leg_left", -3, 0, -3, 6, 6, 6, 4, 18, 0),
            ("leg_right", -3, 0, -3, 6, 6, 6, -4, 18, 0),
            # Top-heavy and blunt: tusks under the tiny head, and a ridge of
            # slag down the back that reads from behind.
            ("tusk_left", 1, 1, -3, 2, 3, 2, 0, 0, 0, "head"),
            ("tusk_right", -3, 1, -3, 2, 3, 2, 0, 0, 0, "head"),
            ("spine", -2, -2, 3, 4, 10, 3, 0, 0, 0, "body"),
            ("jaw", -2, 3, -3, 4, 2, 4, 0, 0, 0, "head"),
            ("fist_left", -1, 13, -4, 8, 5, 8, 0, 0, 0, "arm_left"),
            ("fist_right", -7, 13, -4, 8, 5, 8, 0, 0, 0, "arm_right"),
            ("slag_left", -4, 1, -5, 3, 6, 3, 0, 0, 0, "body"),
            ("slag_right", 1, 1, -5, 3, 6, 3, 0, 0, 0, "body"),
            ("boot_left", -4, 4, -4, 8, 3, 8, 0, 0, 0, "leg_left"),
            ("boot_right", -4, 4, -4, 8, 3, 8, 0, 0, 0, "leg_right"),
        ],
    },
    "whisper": {
        "height": 1.9,
        "boxes": [
            ("head", -3, -7, -3, 6, 7, 6, 0, 5, 0),
            ("body", -3, 0, -2, 6, 12, 3, 0, 5, 0),
            ("arm_left", 0, -1, -1, 2, 14, 2, 3, 6, 0),
            ("arm_right", -2, -1, -1, 2, 14, 2, -3, 6, 0),
            ("leg_left", -1, 0, -1, 3, 7, 3, 2, 17, 0),
            ("leg_right", -2, 0, -1, 3, 7, 3, -2, 17, 0),
            # Nothing but height: a cowl that makes the head a shape rather
            # than a box, and a shroud that trails from the shoulders.
            ("cowl", -4, -8, -4, 8, 4, 8, 0, 0, 0, "head"),
            ("shroud", -4, 2, 1, 8, 12, 2, 0, 0, 0, "body"),
            ("mask", -2, -5, -5, 4, 3, 2, 0, 0, 0, "head"),
            ("horn_left", 3, -9, -2, 2, 3, 2, 0, 0, 0, "head"),
            ("horn_right", -5, -9, -2, 2, 3, 2, 0, 0, 0, "head"),
            ("tatter_left", -5, 6, 0, 2, 9, 2, 0, 0, 0, "body"),
            ("tatter_right", 3, 6, 0, 2, 9, 2, 0, 0, 0, "body"),
            ("claw_left", -1, 12, -2, 3, 4, 3, 0, 0, 0, "arm_left"),
            ("claw_right", -2, 12, -2, 3, 4, 3, 0, 0, 0, "arm_right"),
        ],
    },
    "drone": {
        "height": 1.2,
        "boxes": [
            ("head", -4, -4, -4, 8, 8, 8, 0, 14, 0),
            ("body", -3, 0, -3, 6, 5, 6, 0, 14, 0),
            ("arm_left", 0, -1, -1, 8, 2, 2, 3, 15, 0),
            ("arm_right", -8, -1, -1, 8, 2, 2, -3, 15, 0),
            ("leg_left", -1, 0, -1, 2, 5, 2, 2, 19, 0),
            ("leg_right", -1, 0, -1, 2, 5, 2, -2, 19, 0),
            # A machine: an antenna off the core, and two fins that say which
            # way it is facing when nothing else about it does.
            ("antenna", -1, -7, -1, 2, 4, 2, 0, 0, 0, "head"),
            ("fin_left", 2, 0, -1, 3, 4, 5, 0, 0, 0, "body"),
            ("fin_right", -5, 0, -1, 3, 4, 5, 0, 0, 0, "body"),
            ("lens", -2, -2, -5, 4, 4, 2, 0, 0, 0, "head"),
            ("vane_left", 1, -5, -1, 4, 2, 2, 0, 0, 0, "head"),
            ("vane_right", -5, -5, -1, 4, 2, 2, 0, 0, 0, "head"),
            ("thruster", -2, 4, 2, 4, 3, 3, 0, 0, 0, "body"),
            ("muzzle_left", 7, -2, -2, 3, 4, 4, 0, 0, 0, "arm_left"),
            ("muzzle_right", -10, -2, -2, 3, 4, 4, 0, 0, 0, "arm_right"),
        ],
    },
    # Fast and fragile: nothing on it is wide. Two blades instead of hands,
    # a low crouch, and a crest that trails so a player can tell at a glance
    # that the small fast thing is not a Scavenger.
    "harrier": {
        "height": 1.4,
        "boxes": [
            ("head", -2, -5, -3, 5, 5, 6, 0, 14, 0),
            ("body", -3, 0, -2, 6, 8, 3, 0, 14, 0),
            ("arm_left", 0, -1, -1, 2, 11, 2, 3, 15, 0),
            ("arm_right", -2, -1, -1, 2, 11, 2, -3, 15, 0),
            ("leg_left", -1, 0, -1, 3, 6, 3, 2, 18, 0),
            ("leg_right", -2, 0, -1, 3, 6, 3, -2, 18, 0),
            ("crest", -1, -8, 0, 2, 3, 7, 0, 0, 0, "head"),
            ("snout", -2, -2, -6, 4, 3, 3, 0, 0, 0, "head"),
            ("blade_left", -1, 9, -1, 2, 8, 2, 0, 0, 0, "arm_left"),
            ("blade_right", -1, 9, -1, 2, 8, 2, 0, 0, 0, "arm_right"),
            ("harness", -4, 2, -3, 8, 3, 5, 0, 0, 0, "body"),
        ],
    },
    "lictor": {
        "height": 2.4,
        "boxes": [
            ("head", -4, -8, -4, 8, 8, 8, 0, 4, 0),
            ("body", -6, 0, -3, 12, 12, 6, 0, 4, 0),
            ("arm_left", 0, -2, -2, 5, 15, 5, 6, 6, 0),
            ("arm_right", -5, -2, -2, 5, 15, 5, -6, 6, 0),
            ("leg_left", -2, 0, -2, 5, 8, 5, 3, 16, 0),
            ("leg_right", -3, 0, -2, 5, 8, 5, -3, 16, 0),
            # State armour: a crest along the helmet and a pauldron on each
            # shoulder, which is most of what makes a slab read as a soldier.
            ("crest", -1, -11, -4, 2, 4, 9, 0, 0, 0, "head"),
            ("pauldron_left", -1, -3, -3, 7, 4, 7, 0, 0, 0, "arm_left"),
            ("pauldron_right", -6, -3, -3, 7, 4, 7, 0, 0, 0, "arm_right"),
            ("visor", -3, -5, -5, 6, 2, 2, 0, 0, 0, "head"),
            ("gorget", -4, 0, -4, 8, 3, 8, 0, 0, 0, "head"),
            ("cuirass", -7, 2, -4, 14, 7, 8, 0, 0, 0, "body"),
            ("greave_left", -3, 3, -3, 7, 4, 7, 0, 0, 0, "leg_left"),
            ("greave_right", -4, 3, -3, 7, 4, 7, 0, 0, 0, "leg_right"),
            ("banner", -1, 2, 4, 2, 12, 1, 0, 0, 0, "body"),
        ],
    },
    "adept": {
        "height": 2.0,
        "boxes": [
            ("head", -5, -9, -5, 10, 9, 10, 0, 6, 0),
            ("body", -4, 0, -2, 8, 14, 5, 0, 6, 0),
            ("arm_left", 0, -1, -1, 3, 13, 3, 4, 7, 0),
            ("arm_right", -3, -1, -1, 3, 13, 3, -4, 7, 0),
            ("leg_left", -2, 0, -2, 4, 5, 4, 2, 19, 0),
            ("leg_right", -2, 0, -2, 4, 5, 4, -2, 19, 0),
            # Robed and ceremonial: a mitre above the oversized head and a
            # heavy collar under it, so the head reads as worn rather than big.
            ("mitre", -4, -13, -4, 8, 5, 8, 0, 0, 0, "head"),
            ("collar", -6, 0, -3, 12, 3, 7, 0, 0, 0, "body"),
            ("halo", -6, -15, -1, 12, 2, 2, 0, 0, 0, "head"),
            ("cheek_left", 4, -6, -4, 2, 5, 8, 0, 0, 0, "head"),
            ("cheek_right", -6, -6, -4, 2, 5, 8, 0, 0, 0, "head"),
            ("stole_left", -5, 3, -3, 2, 11, 1, 0, 0, 0, "body"),
            ("stole_right", 3, 3, -3, 2, 11, 1, 0, 0, 0, "body"),
            ("censer", -2, 12, -3, 3, 4, 3, 0, 0, 0, "arm_right"),
        ],
    },
    # Wide and low, and nothing about it is quick. A slab of a shield on one
    # arm, a mace on the other, and a hull that is broader than it is tall.
    "bulwark": {
        "height": 2.1,
        "boxes": [
            ("head", -3, -5, -3, 6, 5, 6, 0, 7, 0),
            ("body", -8, 0, -5, 16, 12, 10, 0, 7, 0),
            ("arm_left", 0, -2, -3, 6, 14, 6, 8, 9, 0),
            ("arm_right", -6, -2, -3, 6, 14, 6, -8, 9, 0),
            ("leg_left", -3, 0, -3, 7, 5, 7, 4, 19, 0),
            ("leg_right", -4, 0, -3, 7, 5, 7, -4, 19, 0),
            ("helm", -4, -7, -4, 8, 3, 8, 0, 0, 0, "head"),
            ("visor", -3, -4, -5, 6, 2, 2, 0, 0, 0, "head"),
            ("shield", -2, 2, -7, 3, 14, 14, 0, 0, 0, "arm_left"),
            ("mace", -2, 12, -2, 5, 5, 5, 0, 0, 0, "arm_right"),
            ("plate", -9, 1, -6, 18, 8, 2, 0, 0, 0, "body"),
            ("skirt", -8, 10, -5, 16, 4, 10, 0, 0, 0, "body"),
        ],
    },
}

# The bosses have their own geometry now.
#
# They used to reuse a family's boxes at a larger scale, with a note that a
# bespoke model was the obvious later improvement. The reasoning for reusing was
# sound - a boss should be unmistakably the same kind of thing as its escort -
# and it survives here: each boss is built *from* its family's silhouette and
# then given the things that make it the one the others answer to. A Choir is
# still a Scavenger shape; it is a Scavenger shape with six arms.
#
# Scaling alone could not do that. At 2.2x a Scavenger is a large Scavenger, and
# "large" is the one way a boss should not be distinguished, because it is also
# what the game does to every mob it wants you to take seriously.
BOSS_SCALE = {"choir": 2.2, "praetor": 1.6}

BOSSES = {
    "choir": {
        # A Scavenger that never stopped scavenging: hunched, long-armed, and
        # wearing four more arms than it was issued. The extra pair hangs from
        # the body rather than the shoulders, which is what makes them read as
        # taken rather than grown.
        "family": "scavenger",
        "boxes": [
            ("head", -4, -7, -4, 8, 7, 8, 0, 11, 0),
            ("body", -5, 0, -3, 10, 10, 6, 0, 11, 0),
            ("arm_left", -1, -1, -2, 4, 12, 4, 6, 12, 0),
            ("arm_right", -3, -1, -2, 4, 12, 4, -6, 12, 0),
            ("leg_left", -2, 0, -2, 4, 6, 4, 2, 18, 0),
            ("leg_right", -2, 0, -2, 4, 6, 4, -2, 18, 0),
            # The choir itself: two more pairs, smaller, off the ribs.
            ("arm_second_left", 0, 0, -1, 3, 9, 3, 5, 3, 0, "body"),
            ("arm_second_right", -3, 0, -1, 3, 9, 3, -5, 3, 0, "body"),
            ("arm_third_left", 0, 0, -1, 2, 7, 2, 5, 6, 0, "body"),
            ("arm_third_right", -2, 0, -1, 2, 7, 2, -5, 6, 0, "body"),
            ("hood", -5, -9, -5, 10, 3, 10, 0, 0, 0, "head"),
        ],
    },
    "praetor": {
        # A Lictor the Empire spent more on: the same slab, crowned, mantled,
        # and carrying the standard the rest of them march under.
        "family": "lictor",
        "boxes": [
            ("head", -4, -8, -4, 8, 8, 8, 0, 4, 0),
            ("body", -7, 0, -4, 14, 13, 8, 0, 4, 0),
            ("arm_left", 0, -2, -2, 6, 16, 6, 7, 6, 0),
            ("arm_right", -6, -2, -2, 6, 16, 6, -7, 6, 0),
            ("leg_left", -2, 0, -2, 6, 8, 6, 4, 16, 0),
            ("leg_right", -4, 0, -2, 6, 8, 6, -4, 16, 0),
            ("crest", -1, -13, -5, 2, 6, 11, 0, 0, 0, "head"),
            ("pauldron_left", -1, -3, -4, 8, 5, 8, 0, 0, 0, "arm_left"),
            ("pauldron_right", -7, -3, -4, 8, 5, 8, 0, 0, 0, "arm_right"),
            ("mantle", -8, 1, 4, 16, 14, 2, 0, 0, 0, "body"),
            # The standard, on the back. Static - it does not need to move for
            # a silhouette to read at thirty blocks, and a pole that swung with
            # the body would look like it was falling over.
            ("standard", -1, -14, 5, 2, 20, 2, 0, 0, 0, "body"),
        ],
    },
}

# ---------------------------------------------------------------------------
# Palettes: base metal, accent, and the glow each variant carries
# ---------------------------------------------------------------------------

FAMILY_BASE = {
    "scavenger": ("#3a352c", "#5c5344"),
    "reaver":    ("#2e2a26", "#4a4038"),
    "whisper":   ("#1e2028", "#333a48"),
    "drone":     ("#2a3040", "#455066"),
    "lictor":    ("#181430", "#2b2450"),
    "adept":     ("#241d38", "#3a2f56"),
    "harrier":   ("#2b2118", "#4a3728"),
    "bulwark":   ("#1c1f2a", "#333a4e"),
}

# id -> accent, taken from the ChatFormatting each variant declares in Java.
VARIANT_ACCENT = {
    "ragpicker": "#9a9a9a", "feral": "#d4402a", "carrion": "#3f8c3f",
    "scuttler": "#e0c040", "blightfed": "#5cd45c",
    "chainbound": "#9a9a9a", "slagfist": "#6a6a6a", "hollowed": "#a02020",
    "yokebreaker": "#e0a020", "grindmaw": "#8a3fd4",
    "ashling": "#9a9a9a", "nightcut": "#a02020", "veilwalk": "#e07fe0",
    "gutterghost": "#3fb0b0", "mourner": "#4060d4",
    "pattern_one": "#9a9a9a", "interdictor": "#40d0e0", "lancer": "#e0a020",
    "relay": "#e0c040", "kill_switch": "#a02020",
    "sanctioned": "#9a9a9a", "aegis": "#40d0e0", "censor": "#8a3fd4",
    "custodian": "#4060d4", "inquisitor": "#a02020",
    "acolyte": "#9a9a9a", "vivifier": "#3f8c3f", "resonant": "#e07fe0",
    "marshal": "#e0a020", "null_speaker": "#3fb0b0",
    "cutthroat": "#9a9a9a", "swiftblade": "#e0c040", "bloodletter": "#a02020",
    "windshear": "#3fb0b0", "flenser": "#8a3fd4",
    "shieldwall": "#9a9a9a", "immovable": "#4060d4", "anvil": "#e0a020",
    "gatekeeper": "#40d0e0", "obstinate": "#3f8c3f",
    # bosses
    "choir": "#d4402a", "praetor": "#e6c34d",
}

FAMILY_VARIANTS = {
    "scavenger": ["ragpicker", "feral", "carrion", "scuttler", "blightfed"],
    "reaver": ["chainbound", "slagfist", "hollowed", "yokebreaker", "grindmaw"],
    "whisper": ["ashling", "nightcut", "veilwalk", "gutterghost", "mourner"],
    "drone": ["pattern_one", "interdictor", "lancer", "relay", "kill_switch"],
    "harrier": ["cutthroat", "swiftblade", "bloodletter", "windshear", "flenser"],
    "bulwark": ["shieldwall", "immovable", "anvil", "gatekeeper", "obstinate"],
    "lictor": ["sanctioned", "aegis", "censor", "custodian", "inquisitor"],
    "adept": ["acolyte", "vivifier", "resonant", "marshal", "null_speaker"],
}


# ---------------------------------------------------------------------------
# Texture: unwrap each box the way Minecraft expects
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Java: the model class
# ---------------------------------------------------------------------------

MODEL_TEMPLATE = '''package com.elysium.mobs.client.model;

import com.elysium.mobs.entity.ElysiumMob;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * GENERATED by tools/gen_mobs.py. Do not edit.
 *
 * The box table lives in that script, and the texture sheet is generated from
 * the same table - so the UVs here and the pixels there cannot disagree. Edit
 * the table and re-run.
 */
public class {Cls}Model<T extends ElysiumMob> extends EntityModel<T> {{

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart armLeft;
    private final ModelPart armRight;
    private final ModelPart legLeft;
    private final ModelPart legRight;

    public {Cls}Model(ModelPart root) {{
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.armLeft = root.getChild("arm_left");
        this.armRight = root.getChild("arm_right");
        this.legLeft = root.getChild("leg_left");
        this.legRight = root.getChild("leg_right");
    }}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{parts}
        return LayerDefinition.create(mesh, {sheet}, {sheet});
    }}

    /**
     * Walk and look, and nothing else.
     *
     * Deliberately the same animation for every family. A distinctive walk is
     * worth having and is not worth guessing at without being able to watch it
     * - the silhouettes carry the difference, and an animation written blind is
     * more likely to look broken than characterful.
     */
    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {{
        this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
        this.head.xRot = headPitch * ((float) Math.PI / 180F);

        float swing = (float) Math.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        this.legLeft.xRot = swing;
        this.legRight.xRot = -swing;
        this.armLeft.xRot = -swing * 0.8F;
        this.armRight.xRot = swing * 0.8F;
    }}

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
                               int overlay, int colour) {{
        this.root.render(pose, buffer, light, overlay, colour);
    }}
}}
'''

RENDERER_TEMPLATE = '''package com.elysium.mobs.client;

import com.elysium.mobs.client.model.{Cls}Model;
import com.elysium.mobs.entity.ElysiumMob;
import com.elysium.mobs.variant.MobVariant;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * GENERATED by tools/gen_mobs.py. Do not edit.
 *
 * The texture comes from the entity's variant rather than from a constant,
 * which is the whole reason six entity types can be thirty creatures. A mob
 * with no variant - spawned by a command, or whose variant's mod was removed -
 * falls back to the family's first sub-variant rather than to a missing
 * texture, because a purple-and-black mob reads as a crash and a plain one
 * reads as a plain one.
 */
public class {Cls}Renderer extends MobRenderer<ElysiumMob, {Cls}Model<ElysiumMob>> {{

    private static final ResourceLocation FALLBACK =
            ResourceLocation.fromNamespaceAndPath("elysiummobs",
                    "textures/entity/{family}/{fallback}.png");

    public {Cls}Renderer(EntityRendererProvider.Context context) {{
        super(context, new {Cls}Model<>(context.bakeLayer(
                com.elysium.mobs.client.ElysiumMobLayers.{CONST})), {shadow}F);
    }}

    @Override
    public ResourceLocation getTextureLocation(ElysiumMob entity) {{
        MobVariant variant = entity.getVariant();
        return variant == null ? FALLBACK : variant.getTexture();
    }}
}}
'''


def camel(name):
    return "".join(part.capitalize() for part in name.split("_"))



# ---------------------------------------------------------------------------
# The two client files that used to be written by hand
# ---------------------------------------------------------------------------
#
# They were not generated, and the generated renderers referenced them. So
# adding a family to the table above produced a renderer that would not compile
# until somebody remembered to edit two other files — and the failure mode for
# forgetting is worse than a compile error: a layer registered without a
# renderer, or the reverse, is a mob that exists on the server and is invisible
# on the client, with nothing logged.
#
# The generator's own docstring says the box table is the single source. It is
# now actually true.

LAYERS_TEMPLATE = """package com.elysium.mobs.client;

import com.elysium.mobs.ElysiumMobs;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

/**
 * One layer location per entity type.
 *
 * GENERATED by tools/gen_mobs.py from the same table as the models. Do not edit
 * by hand: a layer here without a model there is a mob that renders as nothing.
 */
public final class ElysiumMobLayers {{

    private ElysiumMobLayers() {{
    }}

{constants}

    private static ModelLayerLocation layer(String name) {{
        return new ModelLayerLocation(
                ResourceLocation.fromNamespaceAndPath(ElysiumMobs.MODID, name), "main");
    }}
}}
"""

CLIENT_TEMPLATE = """package com.elysium.mobs.client;

import com.elysium.mobs.ElysiumMobs;
{imports}
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Everything the client needs to draw these creatures.
 *
 * GENERATED by tools/gen_mobs.py. Two registrations per family, and both are
 * required: the layer definition supplies the geometry, and the renderer says
 * which model class and texture to use. Missing either produces a mob that
 * exists on the server and is invisible on the client — which looks exactly
 * like a mod that does not work, and gives no error to search for. Generating
 * both from the one table is what makes forgetting impossible.
 *
 * Client-only. The annotation's {{@code value}} keeps this class off a dedicated
 * server entirely; without it, a server would try to load renderer classes that
 * reference client code and crash on startup.
 */
@EventBusSubscriber(modid = ElysiumMobs.MODID, bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ElysiumMobsClient {{

    private ElysiumMobsClient() {{
    }}

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {{
{layers}
    }}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {{
{renderers}
    }}
}}
"""


def write_client_files(names):
    """names: every entity type, families then bosses, in registration order."""
    constants = "\n".join(
        '    public static final ModelLayerLocation %s = layer("%s");'
        % (name.upper(), name) for name in names)
    imports = "\n".join(
        "import com.elysium.mobs.client.model.%sModel;" % camel(name)
        for name in sorted(names))
    layers = "\n".join(
        "        event.registerLayerDefinition(ElysiumMobLayers.%s, %sModel::createBodyLayer);"
        % (name.upper(), camel(name)) for name in names)
    renderers = "\n".join(
        "        event.registerEntityRenderer(ElysiumMobs.%s.get(), %sRenderer::new);"
        % (name.upper(), camel(name)) for name in names)

    (JAVA / "client/ElysiumMobLayers.java").write_text(
        LAYERS_TEMPLATE.format(constants=constants))
    (JAVA / "client/ElysiumMobsClient.java").write_text(
        CLIENT_TEMPLATE.format(imports=imports, layers=layers, renderers=renderers))
    return 2


def main():
    model_dir = JAVA / "client/model"
    model_dir.mkdir(parents=True, exist_ok=True)
    (JAVA / "client").mkdir(parents=True, exist_ok=True)

    written = {"models": 0, "renderers": 0, "textures": 0}

    for family, spec in FAMILIES.items():
        cls = camel(family)
        family_sheet = smallest_sheet(spec["boxes"])
        (model_dir / ("%sModel.java" % cls)).write_text(
            MODEL_TEMPLATE.format(Cls=cls,
                                  parts=part_lines(spec["boxes"], family_sheet),
                                  sheet=family_sheet))
        written["models"] += 1

        (JAVA / "client" / ("%sRenderer.java" % cls)).write_text(
            RENDERER_TEMPLATE.format(Cls=cls, family=family,
                                     fallback=FAMILY_VARIANTS[family][0],
                                     CONST=family.upper(),
                                     shadow=round(spec["height"] * 0.25, 2)))
        written["renderers"] += 1

        base, _highlight = FAMILY_BASE[family]
        for variant in FAMILY_VARIANTS[family]:
            # The same measured size the model was told. Defaulting here is
            # what made the texture and the model disagree the first time —
            # write_texture fell back to 64 while the model said 96.
            write_texture(RES / "textures/entity" / family / ("%s.png" % variant),
                          spec["boxes"], base, VARIANT_ACCENT[variant],
                          family_sheet)
            written["textures"] += 1

    # Bosses: their own geometry, built from their family's silhouette.
    for boss, spec in BOSSES.items():
        cls = camel(boss)
        family = spec["family"]
        scale = BOSS_SCALE[boss]
        # A boss carries more boxes and bigger ones, so it gets its own sheet
        # size - measured, and only as large as it has to be. The families are
        # untouched at 64.
        sheet = smallest_sheet(spec["boxes"])
        (model_dir / ("%sModel.java" % cls)).write_text(
            MODEL_TEMPLATE.format(Cls=cls, parts=part_lines(spec["boxes"], sheet),
                                  sheet=sheet))
        written["models"] += 1
        (JAVA / "client" / ("%sRenderer.java" % cls)).write_text(
            RENDERER_TEMPLATE.format(Cls=cls, family=boss, fallback=boss,
                                     CONST=boss.upper(),
                                     shadow=round(scale * 0.6, 2)))
        written["renderers"] += 1
        # The family's own base, so a boss is the same material as its escort
        # and only its shape says which one it is.
        base, _highlight = FAMILY_BASE[family]
        write_texture(RES / "textures/entity" / boss / ("%s.png" % boss),
                      spec["boxes"], base, VARIANT_ACCENT[boss], sheet)
        written["textures"] += 1

    written["client"] = write_client_files(
        list(FAMILIES.keys()) + list(BOSSES.keys()))

    print("models    : %d" % written["models"])
    print("renderers : %d" % written["renderers"])
    print("textures  : %d" % written["textures"])
    print("client    : %d (layers + registrations)" % written["client"])


if __name__ == "__main__":
    main()
