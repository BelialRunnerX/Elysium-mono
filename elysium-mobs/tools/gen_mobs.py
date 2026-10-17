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
from boxmodel import Skin, hex_to_rgb, part_lines, smallest_sheet, write_texture
import organic as org

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
# The organic anatomy
# ---------------------------------------------------------------------------
#
# Every family is built from one body plan and its own row of proportions —
# long tapering limbs with the length in the forearm and shin, broad shoulders
# over a pinched waist, a swept cranium, layered plate shells over a darker
# under-body, curved claws, digitigrade legs. The primitives are art/organic.py;
# the plan is organic_body() below; the numbers are PROPORTIONS.
#
# This was built, measured, judged worse than the hand-authored boxes and left
# unwired, with its reasons written down in art/organic.py. The project owner
# looked at the renders and asked for it anyway — the body shape is the thing
# they wanted changed — and an owner's decision on how their mobs look is not
# one this file gets to overrule. So it is wired in, the reasons stay in the
# archive header where they were, and the hand-authored tables below it remain
# the fallback for any family that has no row here.

PROPORTIONS = {
    # skull_sweep  how far back each cranium slab tips (the domed helmet)
    # waist        waist radius as a fraction of chest width - the taper
    # elbow/knee   the joint angles that make a limb read as jointed
    # claw_len     length of one claw segment; four of them make a blade
    "scavenger": dict(seed=1, head_y=11, skull_w=6, skull_h=6, skull_d=1.15,
        skull_sweep=0.20, chest_w=8, chest_h=13, chest_d=0.62, waist=0.30,
        plates=6, plate_lean=0.22, back_name="scrap",
        shoulder_x=4.6, shoulder_y=12, arm_w=3.0, arm_len=17, elbow=0.30,
        fingers=4, claw_len=2.2, claw_curl=0.22,
        hip_x=2.2, hip_y=19, leg_w=3.2, leg_len=11, hip_pitch=0.26,
        knee=-0.52, ankle=0.42, wobble=0.05),
    "reaver": dict(seed=2, head_y=6, skull_w=5.5, skull_h=5, skull_d=1.25,
        skull_sweep=0.24, chest_w=13, chest_h=15, chest_d=0.60, waist=0.30,
        plates=7, plate_lean=0.24, back_name="spine_plate",
        shoulder_x=7, shoulder_y=7, arm_w=4.6, arm_len=22, elbow=0.22,
        fingers=4, claw_len=3.0, claw_curl=0.20,
        hip_x=3.6, hip_y=18, leg_w=5.0, leg_len=12, hip_pitch=0.22,
        knee=-0.46, ankle=0.36, wobble=0.04),
    "whisper": dict(seed=3, head_y=5, skull_w=5, skull_h=6, skull_d=1.30,
        skull_sweep=0.26, chest_w=6.5, chest_h=15, chest_d=0.52, waist=0.24,
        plates=6, plate_lean=0.16, back_name="shroud",
        shoulder_x=3.2, shoulder_y=6, arm_w=2.2, arm_len=24, elbow=0.18,
        fingers=4, claw_len=3.4, claw_curl=0.16,
        hip_x=1.9, hip_y=18, leg_w=2.6, leg_len=15, hip_pitch=0.20,
        knee=-0.44, ankle=0.32, wobble=0.03),
    "drone": dict(seed=4, head_y=13, skull_w=7, skull_h=5.5, skull_d=1.20,
        skull_sweep=0.16, chest_w=6, chest_h=8, chest_d=0.85, waist=0.34,
        plates=4, plate_lean=0.26, back_name="vane",
        shoulder_x=3.0, shoulder_y=14, arm_w=2.2, arm_len=12, elbow=0.14,
        fingers=3, claw_len=1.6, claw_curl=0.12,
        hip_x=1.8, hip_y=19, leg_w=2.0, leg_len=7, hip_pitch=0.30,
        knee=-0.60, ankle=0.48, wobble=0.02, fins=2),
    "lictor": dict(seed=5, head_y=5, skull_w=7, skull_h=6.5, skull_d=1.20,
        skull_sweep=0.22, chest_w=11.5, chest_h=15, chest_d=0.60, waist=0.30,
        plates=7, plate_lean=0.20, back_name="carapace",
        shoulder_x=6.2, shoulder_y=6, arm_w=4.2, arm_len=20, elbow=0.24,
        fingers=4, claw_len=2.6, claw_curl=0.18,
        hip_x=3.2, hip_y=18, leg_w=4.6, leg_len=13, hip_pitch=0.22,
        knee=-0.46, ankle=0.36, wobble=0.04),
    "adept": dict(seed=6, head_y=6, skull_w=7.5, skull_h=7, skull_d=1.25,
        skull_sweep=0.20, chest_w=8.5, chest_h=15, chest_d=0.62, waist=0.32,
        plates=8, plate_lean=0.18, back_name="mantle",
        shoulder_x=4.4, shoulder_y=7, arm_w=3.0, arm_len=19, elbow=0.26,
        fingers=4, claw_len=2.4, claw_curl=0.24,
        hip_x=2.2, hip_y=19, leg_w=3.4, leg_len=10, hip_pitch=0.24,
        knee=-0.48, ankle=0.38, wobble=0.04),
    "choir": dict(seed=7, head_y=10, skull_w=7.5, skull_h=6.5, skull_d=1.15,
        skull_sweep=0.24, chest_w=10, chest_h=15, chest_d=0.62, waist=0.28,
        plates=7, plate_lean=0.26, back_name="scrap",
        shoulder_x=5.6, shoulder_y=11, arm_w=3.6, arm_len=21, elbow=0.32,
        fingers=4, claw_len=2.8, claw_curl=0.26,
        hip_x=2.4, hip_y=19, leg_w=3.6, leg_len=11, hip_pitch=0.28,
        knee=-0.54, ankle=0.44, wobble=0.06, extra_arms=2),
    "praetor": dict(seed=8, head_y=5, skull_w=8, skull_h=7, skull_d=1.20,
        skull_sweep=0.22, chest_w=13, chest_h=17, chest_d=0.60, waist=0.30,
        plates=9, plate_lean=0.20, back_name="mantle",
        shoulder_x=7, shoulder_y=6, arm_w=4.8, arm_len=22, elbow=0.22,
        fingers=4, claw_len=3.0, claw_curl=0.18,
        hip_x=3.6, hip_y=18, leg_w=5.2, leg_len=14, hip_pitch=0.22,
        knee=-0.46, ankle=0.36, wobble=0.04),
    # Fast and lean: the Whisper's reach on a lower, forward-pitched frame,
    # with the longest claws in the set — this family fights on the wing.
    "harrier": dict(seed=9, head_y=8, skull_w=5.5, skull_h=5.5, skull_d=1.35,
        skull_sweep=0.30, chest_w=7, chest_h=13, chest_d=0.55, waist=0.26,
        plates=5, plate_lean=0.30, back_name="fin",
        shoulder_x=3.8, shoulder_y=9, arm_w=2.6, arm_len=22, elbow=0.34,
        fingers=3, claw_len=3.8, claw_curl=0.30,
        hip_x=2.0, hip_y=18, leg_w=2.8, leg_len=14, hip_pitch=0.34,
        knee=-0.62, ankle=0.50, wobble=0.05),
    # Squat and wide: the Lictor's plate on a frame a head shorter and a
    # shoulder wider, arms short because they carry a shield, not a blade.
    "bulwark": dict(seed=10, head_y=8, skull_w=7, skull_h=5.5, skull_d=1.10,
        skull_sweep=0.14, chest_w=15, chest_h=14, chest_d=0.66, waist=0.42,
        plates=9, plate_lean=0.16, back_name="bastion",
        shoulder_x=8, shoulder_y=9, arm_w=5.0, arm_len=16, elbow=0.16,
        fingers=3, claw_len=1.8, claw_curl=0.10,
        hip_x=4.2, hip_y=18, leg_w=5.6, leg_len=11, hip_pitch=0.16,
        knee=-0.36, ankle=0.28, wobble=0.03),
}


GAITS = {
    # Hunched over its own salvage: short quick steps, arms low and busy,
    # already leaning towards whatever it is about to pick up.
    "scavenger": dict(stride=0.85, rate=1.35, arm=0.45, arm_phase=0.5,
                      arm_out=0.10, lean=0.22, bob=0.6, roll=0.04, hover=0.0,
                      doc="A quick, stooped shuffle."),
    # Top-heavy and committed to the direction it is already going. Long steps,
    # heavy roll, arms swinging wide because there is a lot of shoulder behind
    # them.
    "reaver": dict(stride=1.15, rate=0.9, arm=0.58, arm_phase=0.0,
                   arm_out=0.18, lean=0.09, bob=1.1, roll=0.10, hover=0.0,
                   doc="A long, rolling lope."),
    # Tall, thin and not quite committed to the floor. Slow stride, almost no
    # arm movement, and a drift that continues when it stops.
    "whisper": dict(stride=0.55, rate=0.55, arm=0.12, arm_phase=1.2,
                    arm_out=0.0, lean=-0.05, bob=0.15, roll=0.03, hover=0.8,
                    doc="A slow drift that never quite becomes a walk."),
    # Does not walk. The legs are landing gear, the arms are thrusters, and the
    # whole thing floats whether or not it is going anywhere.
    "drone": dict(stride=0.0, rate=1.0, arm=0.25, arm_phase=0.0,
                  arm_out=0.55, lean=0.0, bob=0.0, roll=0.06, hover=1.4,
                  legs="tuck", tuck=-0.45,
                  doc="A hover. This one has no gait at all."),
    # Two and a half metres of plate that has somewhere to be. Short steps for
    # its size, which is what makes something read as heavy, and a hard bounce
    # on each one.
    "lictor": dict(stride=0.62, rate=0.8, arm=0.28, arm_phase=0.0,
                   arm_out=0.22, lean=-0.06, bob=1.3, roll=0.05, hover=0.0,
                   doc="A short, heavy march."),
    # Robed, so the legs barely show and the walk has to happen above the hem.
    # Hands kept together in front.
    "adept": dict(stride=0.35, rate=0.8, arm=0.18, arm_phase=2.6,
                  arm_out=-0.12, lean=0.08, bob=0.3, roll=0.02, hover=0.25,
                  doc="A glide, with the robe doing most of the work."),
    # Bosses. Their family's movement, slowed and made deliberate — a boss that
    # scurries is not frightening, and one that moves like its escort is not a
    # boss.
    "choir": dict(stride=0.75, rate=0.95, arm=0.40, arm_phase=0.5,
                  arm_out=0.14, lean=0.14, bob=0.7, roll=0.06, hover=0.0,
                  doc="The Scavenger's stoop, slowed to something deliberate."),
    "praetor": dict(stride=0.55, rate=0.65, arm=0.22, arm_phase=0.0,
                    arm_out=0.26, lean=-0.08, bob=1.5, roll=0.04, hover=0.0,
                    doc="The Lictor's march, at half speed and twice the weight."),
    # Fast, low, and never quite upright: long stride at a high rate, arms
    # swept back out of the way, the whole frame pitched into the run.
    "harrier": dict(stride=1.05, rate=1.5, arm=0.30, arm_phase=0.4,
                    arm_out=-0.20, lean=0.30, bob=0.5, roll=0.08, hover=0.0,
                    doc="A low, driving sprint."),
    # Everything the Lictor does, shorter and wider. Almost no roll — a wall
    # does not sway — and the arms held out where the shield is.
    "bulwark": dict(stride=0.50, rate=0.7, arm=0.12, arm_phase=0.0,
                    arm_out=0.34, lean=-0.02, bob=1.0, roll=0.02, hover=0.0,
                    doc="A squat, planted advance."),
}


def accent_for(family, boxes):
    """
    Which generated parts carry the accent band.

    Matched by role, because the names are produced by the builders and a hand
    list cannot track them - which it proved by silently going empty when the
    v2 anatomy renamed every part, so the whole bestiary rendered as
    featureless black.

    The marks are chosen the way the reference sheet does it: a lit visor
    across the face, the leading chest shell, the shoulder caps, the tips of
    the claws. Everything else stays dark, which is what makes those few read.
    """
    marks = set()
    for row in boxes:
        name = row[0]
        if (name.startswith("visor_")
                or name.startswith("crest_")
                or name in ("cuirass_0", "cuirass_1")
                or (name.startswith("pauldron_") and name.endswith("_0"))
                or (name.startswith("claw_") and name.endswith("_3"))
                or (name.startswith("cranium_") and name.endswith(("_0", "_1")))):
            marks.add(name)
    return marks


def organic_body(p):
    """
    One body, built to the reference sheet rather than to an anatomy textbook.

    The four references agree on more than they disagree, and the agreements
    are what this builds:

      * long tapering limbs, with the *forearm and shin* carrying the length
      * broad shoulders over a narrow waist - the taper is the silhouette
      * a smooth cranium swept backwards, no face
      * layered plate shells over a darker under-body
      * long curving claws
      * digitigrade legs
      * few big masses; detail lives inside the outline instead of breaking it

    They also agree on something absent: **none of them has an exposed
    ribcage.** The first attempt at this (§32) built a skeleton - ribs, spine,
    pelvis - and got a stack of slabs, because a ribcage is anatomy and these
    creatures are armour. The cage is gone; `plates()` over a solid core does
    the work instead.

    Built outside-in, which is the other lesson from §32: the masses that make
    the outline are placed first and everything else is hung inside them.
    """
    rows = []
    seed = p["seed"]

    # -- cranium: flat slabs swept back, so the head reads as a helmet -------
    hw, hh = p["skull_w"], p["skull_h"]
    rows.append(("head", -hw * 0.30, -hh * 0.45, -hw * 0.30, hw * 0.60,
                 hh * 0.45, hw * 0.60, 0, p["head_y"], 0))
    # A dome, then a short crest sweeping off the back of it. The first
    # version made the whole cranium a chain with a -1.35 lead, which tips it
    # 77 degrees - the head came out as a stack of horizontal shelves. The
    # mass wants to be a mass; only the crest wants to be a chain.
    rows += org.ovoid("cranium", "head", 6, hh * 0.90, hw * 0.44,
                      (0, -hh * 0.88, 0), p["skull_d"], 0.44)
    # The crest runs *backwards from the base of the skull*, not upwards from
    # the top of it. Sitting it on top gave every mob a mortarboard.
    rows += org.chain("crest", "head", 4,
                      (hw * 0.34, hh * 0.24, hw * 0.30),
                      (p["skull_sweep"], 0, 0),
                      (0, -hh * 0.30, hw * 0.34), taper=0.84,
                      lead=(-1.25, 0, 0))
    rows += org.plates("visor", "head", 3,
                       (hw * 0.72, hh * 0.22, hw * 0.30),
                       (0, -hh * 0.34, -hw * p["skull_d"] * 0.45),
                       spacing=hh * 0.16, lean=-0.10, shrink=0.86)

    # -- torso: broad thorax, pinched waist, and shells over both -----------
    bw, bh = p["chest_w"], p["chest_h"]
    rows.append(("body", -bw * 0.22, 0, -bw * 0.18, bw * 0.44, bh * 0.30,
                 bw * 0.36, 0, p["head_y"], 0))
    rows += org.ovoid("thorax", "body", 5, bh * 0.46, bw * 0.60,
                      (0, -bh * 0.04, 0), p["chest_d"], 0.28)
    # Narrow across, but not shallow front-to-back: pinching both axes reads
    # as the model coming apart at the waist rather than as a taper.
    rows += org.ovoid("waist", "body", 3, bh * 0.26, bw * p["waist"],
                      (0, bh * 0.42, 0), p["chest_d"] * 1.9, 0.55)
    rows += org.ovoid("hips", "body", 2, bh * 0.22, bw * 0.42,
                      (0, bh * 0.66, 0), p["chest_d"] * 1.1, 0.4)
    # Four or five thick shells, not eight thin ones. Thin stacked plates read
    # as a rib ladder - which is the shape the references pointedly do not have.
    rows += org.plates("cuirass", "body", max(4, p["plates"] - 3),
                       (bw * 0.72, bh * 0.15, bw * 0.16),
                       (0, bh * 0.00, -bw * p["chest_d"] * 0.40),
                       spacing=bh * 0.12, lean=p["plate_lean"], shrink=0.90)
    rows += org.plates(p["back_name"], "body", max(3, p["plates"] - 2),
                       (bw * 0.44, bh * 0.12, bw * 0.10),
                       (0, bh * 0.04, bw * p["chest_d"] * 0.42),
                       spacing=bh * 0.10, lean=-p["plate_lean"] * 0.7,
                       shrink=0.88)

    for side, sign in (("left", 1), ("right", -1)):
        # -- arm: shoulder mass, short upper, LONG forearm, long claws ------
        aw = p["arm_w"]
        rows.append(("arm_%s" % side, -aw * 0.5, 0, -aw * 0.5, aw, aw, aw,
                     sign * p["shoulder_x"], p["shoulder_y"], 0))
        rows += org.ovoid("pauldron_%s" % side, "arm_%s" % side, 3,
                          aw * 1.25, aw * 0.78, (0, -aw * 0.45, 0), 0.95, 0.35)
        rows += org.chain("upper_%s" % side, "arm_%s" % side, 3,
                          (aw * 0.80, p["arm_len"] * 0.16, aw * 0.80),
                          (0.03, 0, 0), (0, aw * 0.55, 0), taper=0.93)
        # The forearm is where the length lives in every one of the four
        # references - a long thin run below a short thick upper arm.
        rows += org.chain("fore_%s" % side, "upper_%s_2" % side, 4,
                          (aw * 0.60, p["arm_len"] * 0.15, aw * 0.60),
                          (0.04, 0, 0), (0, p["arm_len"] * 0.16, 0),
                          taper=0.94, lead=(p["elbow"], 0, 0))
        rows += org.digits("claw_%s" % side, "fore_%s_3" % side,
                           p["fingers"], 4,
                           (aw * 0.22, p["claw_len"], aw * 0.22), aw * 0.34,
                           (0, p["arm_len"] * 0.14, 0),
                           curl=p["claw_curl"], sign=sign, fan=0.10)

        # -- leg: digitigrade. thigh forward, shin back, foot forward -------
        lw = p["leg_w"]
        rows.append(("leg_%s" % side, -lw * 0.5, 0, -lw * 0.5, lw, lw, lw,
                     sign * p["hip_x"], p["hip_y"], 0))
        rows += org.chain("thigh_%s" % side, "leg_%s" % side, 3,
                          (lw * 0.92, p["leg_len"] * 0.17, lw * 0.92),
                          (0.02, 0, 0), (0, lw * 0.4, 0), taper=0.94,
                          lead=(p["hip_pitch"], 0, 0))
        rows += org.chain("shin_%s" % side, "thigh_%s_2" % side, 3,
                          (lw * 0.76, p["leg_len"] * 0.17, lw * 0.76),
                          (0.02, 0, 0), (0, p["leg_len"] * 0.17, 0),
                          taper=0.93, lead=(p["knee"], 0, 0))
        rows += org.chain("meta_%s" % side, "shin_%s_2" % side, 2,
                          (lw * 0.52, p["leg_len"] * 0.10, lw * 0.52),
                          (0.0, 0, 0), (0, p["leg_len"] * 0.17, 0),
                          taper=0.92, lead=(p["ankle"], 0, 0))
        rows += org.digits("toe_%s" % side, "meta_%s_1" % side, 3, 2,
                           (lw * 0.26, lw * 0.60, lw * 0.26), lw * 0.40,
                           (0, p["leg_len"] * 0.10, 0), curl=0.14, sign=sign,
                           fan=0.12)

    # -- what the shared anatomy would otherwise flatten out ----------------
    #
    # One body plan for eight families is what makes them a set; it is also
    # what makes them the same. These two put back the things that were a
    # family's whole identity before the rewrite.
    for i in range(p.get("extra_arms", 0)):
        # The Choir wears more arms than it was issued. They hang off the ribs
        # rather than the shoulders, which is what reads as taken rather than
        # grown, and each pair is smaller and set at its own angle.
        scale = 0.72 - i * 0.16
        for side, sign in (("left", 1), ("right", -1)):
            base = "extra%d_%s" % (i, side)
            rows += org.chain(base, "body", 3,
                              (p["arm_w"] * scale * 0.7,
                               p["arm_len"] * 0.14 * scale,
                               p["arm_w"] * scale * 0.7),
                              (0.10, 0, 0),
                              (sign * p["chest_w"] * 0.30,
                               p["chest_h"] * (0.16 + i * 0.14), 0),
                              taper=0.88,
                              lead=(0.18 + i * 0.12, 0,
                                    -sign * (0.55 + i * 0.18)))
            rows += org.digits("xclaw%d_%s" % (i, side), "%s_2" % base, 3, 3,
                               (p["arm_w"] * 0.16, p["claw_len"] * 0.7,
                                p["arm_w"] * 0.16),
                               p["arm_w"] * 0.26,
                               (0, p["arm_len"] * 0.12 * scale, 0),
                               curl=p["claw_curl"], sign=sign, fan=0.10)

    for i in range(p.get("fins", 0)):
        # The Drone is a machine and stopped looking like one the moment it got
        # the same skeleton as everything else. Fins off the core, angled out.
        for side, sign in (("left", 1), ("right", -1)):
            rows += org.chain("fin%d_%s" % (i, side), "body", 3,
                              (p["chest_w"] * 0.18, p["chest_h"] * 0.16,
                               p["chest_w"] * 0.30),
                              (0.0, 0, -sign * 0.16),
                              (sign * p["chest_w"] * 0.34,
                               p["chest_h"] * (0.10 + i * 0.26), 0),
                              taper=0.80,
                              lead=(0.20, sign * 0.30, -sign * 1.15))

    return org.asymmetry(rows, seed, p.get("wobble", 0.03))


def gait_java(name):
    """The setupAnim substitutions for one family, from its GAITS row."""
    gait = dict(GAITS[name])
    if gait.get("legs") == "tuck":
        tuck = gait.get("tuck", 0.0)
        legs = ("        // Landing gear, not legs: held, never swung.\n"
                "        this.legLeft.xRot = %.2fF;\n"
                "        this.legRight.xRot = %.2fF;" % (tuck, tuck))
    else:
        legs = ("        this.legLeft.xRot = stride;\n"
                "        this.legRight.xRot = -stride;")
    return {
        "gait_doc": gait["doc"],
        "stride": "%.2f" % gait["stride"],
        "rate": "%.2f" % gait["rate"],
        "arm": "%.2f" % gait["arm"],
        "arm_phase": "%.2f" % gait["arm_phase"],
        "arm_out": "%.2f" % gait["arm_out"],
        # Emitted rather than negated in the template: a negative value there
        # produced "--0.12F", which javac reads as a pre-decrement of a literal.
        "arm_out_neg": "%.2f" % -gait["arm_out"],
        "lean": "%.2f" % gait["lean"],
        "bob": "%.2f" % gait["bob"],
        "roll": "%.2f" % gait["roll"],
        "hover": "%.2f" % gait["hover"],
        "legs": legs,
    }


# ---------------------------------------------------------------------------
# Palettes: base metal, accent, and the glow each variant carries
# ---------------------------------------------------------------------------

# These were three to four times too dark. Vanilla entity textures average
# 73-97 luminance (measured off cow.png and iron_golem.png); ours averaged
# 11-37, so every mob in the game rendered as a silhouette with a coloured dot
# on it. Raised to land in vanilla's range while keeping each family's hue —
# the Empire is black and emerald, but "black" in a texture still has to have
# a value or there is nothing to see.
#
# The six that were measured are verbatim; harrier and bulwark arrived later
# and are their own dark hues carried to the same luminance band by the same
# arithmetic, so the eight sit together.
FAMILY_BASE = {
    "scavenger": ("#888175", "#c5bbab"),
    "reaver":    ("#7f7973", "#bcaea3"),
    "whisper":   ("#6c6f7f", "#99a3b7"),
    "drone":     ("#757e95", "#abb8d1"),
    "lictor":    ("#7167af", "#a89ad6"),
    "adept":     ("#796c9f", "#b1a0d6"),
    "harrier":   ("#8a7461", "#c6ac92"),
    "bulwark":   ("#707791", "#a3abc9"),
}

# ---------------------------------------------------------------------------
# What each part is made of
# ---------------------------------------------------------------------------
#
# Brightening the base was half the fix, and the half that was not visible.
# Measured through the previewer against vanilla run through the same code, the
# mobs came out at a cow's *mean* and a fifth of its *range*: std 7.6-13.5
# against the cow's 48.6, p95 of 73-105 against its 172. A cow is not a
# mid-brown animal — it is near-black patches against near-white ones — and the
# eye reads that contrast, not the average. One flat colour over a hundred
# parts is a silhouette however bright it is, which is what "they are all
# black" was describing.
#
# So a part is made of something. The under-body stays dark, which is what
# keeps the outline; the armour plate is genuinely light, which is what gives
# the eye somewhere to land; the claws are paler still, because bone is not
# painted metal; cloth sits just under the base, because a hanging rag does not
# catch the light a shoulder does. The second number pulls a material towards
# neutral as it brightens — scaling a saturated hue up turns it into a poster
# colour, which is a mistake the lictor's purple has already made once.
MATERIALS = {
    "deep":  (0.62, 0.85),   # under-body, joints, the masses inside the outline
    "cloth": (0.82, 0.95),   # shrouds, tatters, stoles, banners
    "base":  (1.00, 1.00),   # the default: hide, the bulk of a limb
    "plate": (1.52, 0.55),   # helm, cuirass, pauldrons, greaves — the shell
    "bone":  (1.86, 0.28),   # claw, tusk, horn, jaw
}


def material_palette(base):
    """One family base colour, as the five materials it is built from."""
    red, green, blue = hex_to_rgb(base)
    lum = 0.2126 * red + 0.7152 * green + 0.0722 * blue
    out = {}
    for name, (scale, keep) in MATERIALS.items():
        target = lum * scale
        out[name] = "#%02x%02x%02x" % tuple(
            max(0, min(255, int(channel * scale * keep + target * (1 - keep))))
            for channel in (red, green, blue))
    return out


# Which of art/grain.py's surfaces each material wears. Read off the reference
# images rather than off the material table: the shells are hard and streaked
# and pitted, which is metal; the body under them is a fine even mottle, which
# is stone; claws are smooth, which is bone; and a rag is a weave.
MATERIAL_GRAIN = {
    "deep": "stone",
    "base": "stone",
    "cloth": "cloth",
    "plate": "metal",
    "bone": "smooth",
}

# Named rather than matched by prefix, because this table's part names are
# written by hand — "visor", "gorget", "scrap_horn" — and a prefix rule over
# hand-written names is a rule that silently stops matching the first time
# somebody renames one. A part not listed here is "base", so a part added later
# looks like the rest of the body rather than like a hole.
PLATE_PARTS = {
    "helm", "visor", "lens", "cuirass", "plate", "gorget", "collar", "harness",
    "pauldron_left", "pauldron_right", "bracer_right", "greave_left",
    "greave_right", "boot_left", "boot_right", "knee_left", "knee_right",
    "shield", "mask", "mitre", "halo", "crest", "blade_left", "blade_right",
    "mace", "censer", "thruster", "antenna", "vane_left", "vane_right",
    "fin_left", "fin_right", "belt",
}
CLOTH_PARTS = {
    "shroud", "hood", "cowl", "mantle", "skirt", "banner", "standard",
    "satchel", "tatter_left", "tatter_right", "stole_left", "stole_right",
}
BONE_PARTS = {
    "claw_left", "claw_right", "tusk_left", "tusk_right", "horn_left",
    "horn_right", "scrap_horn", "jaw", "spine", "fist_left", "fist_right",
}
DEEP_PARTS = {
    "body", "head", "arm_left", "arm_right", "arm_second_left",
    "arm_second_right", "arm_third_left", "arm_third_right", "leg_left",
    "leg_right", "muzzle_left", "muzzle_right", "cheek_left", "cheek_right",
    "snout", "slag_left", "slag_right",
}


# The generated anatomy names its parts by role — "cuirass_3", "fore_left_2",
# "claw_right_1_3" — so those are matched by prefix. The hand-authored names
# above stay for any family still built from its table.
PLATE_PREFIXES = ("carapace_", "cuirass_", "pauldron_", "cranium_", "crest_",
                  "meta_", "visor_", "scrap_", "spine_plate_", "shroud_",
                  "vane_", "mantle_", "fin", "bastion_")
DEEP_PREFIXES = ("thorax_", "waist_", "hips", "upper_", "fore_", "thigh_",
                 "shin_", "extra")


def material_for(boxes):
    """part name -> material key."""
    out = {}
    for row in boxes:
        name = row[0]
        if ((name.startswith("claw_") and name.endswith(("_2", "_3")))
                or (name.startswith("toe_") and name.endswith(("_1", "_2")))
                or (name.startswith("xclaw") and name.endswith(("_1", "_2")))):
            out[name] = "bone"
        elif name.startswith(PLATE_PREFIXES):
            out[name] = "plate"
        elif name.startswith(DEEP_PREFIXES):
            out[name] = "deep"
        elif name in BONE_PARTS:
            out[name] = "bone"
        elif name in PLATE_PARTS:
            out[name] = "plate"
        elif name in CLOTH_PARTS:
            out[name] = "cloth"
        elif name in DEEP_PARTS:
            out[name] = "deep"
    return out


# Which parts carry the emissive band.
#
# A few, and not every box. Every box was what it used to be — write_texture
# with no skin accents all of them — and a mob with sixty lit stripes on it has
# no lit parts at all, only a pattern. The reference sheet lights a visor, the
# leading chest shell, the shoulder caps and the tips: everything else stays
# dark, which is what makes those few read across a room.
ACCENT_PARTS = {
    "visor", "lens", "crest", "cuirass", "gorget", "halo", "censer",
    "thruster", "pauldron_left", "pauldron_right", "mask", "antenna",
}


# One entity at a time, where the general rule gives the wrong answer.
#
# Empty now. The Choir needed a pale face while it was six arms, a body and a
# hood from the hand table — not one hard surface, and it measured exactly like
# the flat sheets this system replaced. The generated anatomy gives it a
# cranium and a cuirass like everything else, so the override is gone; the
# validator's value-range check is what would notice if it were needed again.
MATERIAL_OVERRIDES = {}


def skin_for(name, boxes, family=None):
    """
    The whole of what an entity is made of, for one texture sheet.

    @param name   the entity, for its overrides
    @param family whose base colour it wears — a boss shares its escort's
    """
    base, _highlight = FAMILY_BASE[family or name]
    materials = material_for(boxes)
    materials.update({part: material
                      for part, material in MATERIAL_OVERRIDES.get(name, {}).items()
                      if part in {row[0] for row in boxes}})
    # Marks by role for the generated anatomy, by name for a hand table.
    marks = accent_for(name, boxes) if name in PROPORTIONS else \
        {row[0] for row in boxes if row[0] in ACCENT_PARTS}
    return Skin(material_palette(base), materials, marks, MATERIAL_GRAIN)


def boxes_for(name, spec):
    """The generated body where there is a row for it; the table otherwise."""
    return organic_body(PROPORTIONS[name]) if name in PROPORTIONS else spec["boxes"]

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
     * {gait_doc}
     *
     * Generated from this family's row in GAITS. Every number below was set by
     * looking at it — mobs/tools/walk.sh renders the cycle through the real
     * pipeline — rather than by reasoning about what it ought to be. The
     * previous version of this method was identical for all eight families and
     * said so, on the grounds that a walk written blind is more likely to look
     * broken than characterful. That was true; the answer was to stop being
     * blind rather than to keep sharing one walk.
     */
    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {{
        this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
        this.head.xRot = headPitch * ((float) Math.PI / 180F) + {lean}F;
        this.body.xRot = {lean}F;

        // One phase for the whole body, so nothing can drift out of step.
        float cycle = limbSwing * 0.6662F * {rate}F;
        float stride = (float) Math.cos(cycle) * {stride}F * limbSwingAmount;
        float reach = (float) Math.cos(cycle + {arm_phase}F) * {arm}F * limbSwingAmount;

{legs}
        this.armLeft.xRot = -reach;
        this.armRight.xRot = reach;
        this.armLeft.zRot = {arm_out}F;
        this.armRight.zRot = {arm_out_neg}F;

        // Vertical: a bounce twice per stride (both feet land), plus an idle
        // float that does not care whether the mob is moving.
        this.root.y = (float) Math.abs(Math.sin(cycle)) * {bob}F * limbSwingAmount
                + (float) Math.sin(ageInTicks * 0.08F) * {hover}F;
        this.root.zRot = (float) Math.sin(cycle) * {roll}F * limbSwingAmount;
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

    write_source(JAVA / "client/ElysiumMobLayers.java",
        LAYERS_TEMPLATE.format(constants=constants))
    write_source(JAVA / "client/ElysiumMobsClient.java",
        CLIENT_TEMPLATE.format(imports=imports, layers=layers, renderers=renderers))
    return 2


def write_source(path, text):
    """
    One generated Java file, as UTF-8 with LF line ends.

    Path.write_text with neither said wrote the locale's code page and CRLF on
    Windows: every em dash in a template came out as a byte the validator's
    UTF-8 read stops on, and each regen marked every generated Java file
    changed (16 October 2026).
    """
    path.write_text(text, encoding="utf-8", newline="\n")


def main():
    model_dir = JAVA / "client/model"
    model_dir.mkdir(parents=True, exist_ok=True)
    (JAVA / "client").mkdir(parents=True, exist_ok=True)

    written = {"models": 0, "renderers": 0, "textures": 0}

    for family, spec in FAMILIES.items():
        cls = camel(family)
        boxes = boxes_for(family, spec)
        # The skin is built before the sheet is measured: a part's material is
        # part of its patch identity, so two same-sized boxes made of different
        # things need two patches and the sheet has to be sized knowing that.
        skin = skin_for(family, boxes)
        family_sheet = smallest_sheet(boxes, skin=skin)
        write_source(model_dir / ("%sModel.java" % cls),
            MODEL_TEMPLATE.format(Cls=cls,
                                  parts=part_lines(boxes, family_sheet, skin),
                                  sheet=family_sheet, **gait_java(family)))
        written["models"] += 1

        write_source(JAVA / "client" / ("%sRenderer.java" % cls),
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
                          boxes, base, VARIANT_ACCENT[variant],
                          family_sheet, skin)
            written["textures"] += 1

    # Bosses: their own geometry, built from their family's silhouette.
    for boss, spec in BOSSES.items():
        cls = camel(boss)
        family = spec["family"]
        scale = BOSS_SCALE[boss]
        # A boss carries more boxes and bigger ones, so it gets its own sheet
        # size - measured, and only as large as it has to be. The families are
        # untouched at 64.
        # The family's own materials, so a boss is made of what its escort is
        # made of and only its shape says which one it is.
        boxes = boxes_for(boss, spec)
        skin = skin_for(boss, boxes, family)
        sheet = smallest_sheet(boxes, skin=skin)
        write_source(model_dir / ("%sModel.java" % cls),
            MODEL_TEMPLATE.format(Cls=cls,
                                  parts=part_lines(boxes, sheet, skin),
                                  sheet=sheet, **gait_java(boss)))
        written["models"] += 1
        write_source(JAVA / "client" / ("%sRenderer.java" % cls),
            RENDERER_TEMPLATE.format(Cls=cls, family=boss, fallback=boss,
                                     CONST=boss.upper(),
                                     shadow=round(scale * 0.6, 2)))
        written["renderers"] += 1
        # The family's own base, so a boss is the same material as its escort
        # and only its shape says which one it is.
        base, _highlight = FAMILY_BASE[family]
        write_texture(RES / "textures/entity" / boss / ("%s.png" % boss),
                      boxes, base, VARIANT_ACCENT[boss], sheet, skin)
        written["textures"] += 1

    written["client"] = write_client_files(
        list(FAMILIES.keys()) + list(BOSSES.keys()))

    print("models    : %d" % written["models"])
    print("renderers : %d" % written["renderers"])
    print("textures  : %d" % written["textures"])
    print("client    : %d (layers + registrations)" % written["client"])


if __name__ == "__main__":
    main()
