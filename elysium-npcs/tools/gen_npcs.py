#!/usr/bin/env python3
"""
Generate the envoy model, renderer, skins and lang from one table.

Run from the repo root:   python3 tools/gen_npcs.py

Same argument as elysium-mobs' generator, and the same shared machinery in
art/boxmodel.py: a model says where a box's faces are on the sheet and the sheet
has to have them there, and hand-written those two drift the first time a box
moves. The box table below is the single source and both come out of it.

<h2>One model, five people</h2>

Every member of the court is a humanoid in player proportions. What differs is
the skin and the regalia - a crown, a cape, pauldrons, a collar, a visor, a
halo. So the model carries every accessory and hides the ones a kind does not
wear, which is one model to keep in step instead of five.

<h2>Why the palette is per-box and not per-sheet</h2>

The mobs' generator paints a whole creature from one base and one accent, which
is right for something armoured head to foot. These are people: a face is not
the colour of a cloak, and hair is not the colour of either. Each box picks its
colours by name from the kind's palette, so Lillith's hair is red on a black
coat without either of them being a special case in the drawing code.
"""
import json
import os
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
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

import numpy as np
from PIL import Image, ImageDraw

from boxmodel import (Skin, draw_box, hex_to_rgb, is_pivot, key_of, pack,
                      parent_of, part_lines, smallest_sheet)

JAVA = ROOT / "src/main/java/com/elysium/npcs"
RES = ROOT / "src/main/resources/assets/elysiumnpcs"
# 96, and measured rather than picked.
#
# Thirteen boxes with seven distinct accessory footprints do not fit in 64x64 -
# the packer says so and raises rather than overlapping, which is the whole
# reason it exists. 96 fits with room to spare and 128 would be a quarter more
# texture memory for empty sheet, so 96 it is. If a fourteenth box ever pushes
# past this, the packer will say so again.
SHEET = 96

# ---------------------------------------------------------------------------
# The box table
# ---------------------------------------------------------------------------
#
# (name, originX, originY, originZ, sizeX, sizeY, sizeZ, pivotX, pivotY, pivotZ)
# and optionally an eleventh element: the part this one hangs from.
#
# Player proportions, because these are people and a player already knows what
# size a person is. The six body parts are vanilla's own numbers; the seven
# accessories are sized to sit on them.

BOXES = [
    ("head", -4, -8, -4, 8, 8, 8, 0, 0, 0),
    ("body", -4, 0, -2, 8, 12, 4, 0, 0, 0),
    ("arm_left", -1, -2, -2, 4, 12, 4, 5, 2, 0),
    ("arm_right", -3, -2, -2, 4, 12, 4, -5, 2, 0),
    ("leg_left", -2, 0, -2, 4, 12, 4, 2, 12, 0),
    ("leg_right", -2, 0, -2, 4, 12, 4, -2, 12, 0),

    # Regalia. Each hangs from the part it is worn on, so a crown turns with
    # the head and a pauldron swings with the arm.
    ("crown", -4, -11, -4, 8, 3, 8, 0, 0, 0, "head"),
    ("halo", -5, -12, -5, 10, 4, 10, 0, 0, 0, "head"),
    ("visor", -4, -6, -5, 8, 3, 1, 0, 0, 0, "head"),
    ("collar", -5, 0, -3, 10, 3, 6, 0, 0, 0, "body"),
    ("cape", -5, 0, 2, 10, 16, 1, 0, 0, 0, "body"),
    ("pauldron_left", -1, -3, -3, 6, 4, 6, 0, 0, 0, "arm_left"),
    ("pauldron_right", -5, -3, -3, 6, 4, 6, 0, 0, 0, "arm_right"),
]

REGALIA = ["crown", "halo", "visor", "collar", "cape", "pauldron_left", "pauldron_right"]

# ---------------------------------------------------------------------------
# The five, and what they are made of
# ---------------------------------------------------------------------------
#
# Black and emerald with gold, throughout - that is the Empire's livery and the
# thing all five have in common. What separates them is which of those three is
# loudest and what a face is doing on top of it.

PALETTES = {
    "emperor": {
        "skin": "#c8a486",
        "hair": "#8d8b86",
        "base": "#5d584a",
        "accent": "#c9a227",
        "cape": "#315e4a",
        "regalia": "#d8b13a",
    },
    "architect": {
        "skin": "#695b58",
        "hair": "#3f3947",
        "base": "#5a5469",
        "accent": "#c9a227",
        "cape": "#3a584e",
        "regalia": "#59b889",
    },
    "sentinel": {
        "skin": "#0d0f0e",
        "hair": "#0d0f0e",
        "base": "#545a57",
        "accent": "#2ee06a",
        "cape": "#4a504d",
        "regalia": "#2ee06a",
    },
    "commander": {
        "skin": "#d8b49a",
        "hair": "#a81a20",
        "base": "#57575d",
        "accent": "#c9a227",
        "cape": "#305e4a",
        "regalia": "#c9a227",
    },
    "queen": {
        "skin": "#e2c9ae",
        "hair": "#ffe89a",
        "base": "#466157",
        "accent": "#e0c14a",
        "cape": "#325c4e",
        "regalia": "#ffe89a",
    },
}



# ---------------------------------------------------------------------------
# The court, built from the character sheets
# ---------------------------------------------------------------------------
#
# The old thirteen-box humanoid was defended on the grounds that these are
# people and a player already knows what size a person is. That held while the
# mobs were nine boxes too. Against a hundred-and-twenty-part bestiary the
# court read as the oldest art in the project, and four of the five differed
# only by the colour of a face.
#
# So: the same machinery the mobs use, aimed at a different brief. The mobs are
# carapace; these are *tailoring*. What the reference sheets have in common is
# a high collar, a long garment, and gold ornament on black and emerald - and
# what separates them is entirely costume, which is why the body below is
# shared and everything above `costume` is per-kind.

import organic as org



# One row per member of the court, read off their character sheet.
#
#   collar / collar_fan   every one of them has a standing collar; the height
#                         and how far it flares is most of their silhouette
#   pauldron              banded rings, a flat epaulette, a swept spur, none
#   hair                  short / long / ponytail / flowing / helm
#   crown                 number of laurel leaves, 0 for no crown
CHARACTERS = {
    # Gold laurel, eagle on the chest, banded pauldrons, green cape.
    "emperor": dict(collar=3, collar_fan=0.28, belt=True, emblem=True,
                    cape=5, pauldron="banded", hair="short", crown=8),
    # A star that stopped being one. The hair is larger than she is and is the
    # only thing on any of the five meant to read from across a room.
    "queen": dict(collar=5, collar_fan=0.62, skirt=6, cape=5,
                  pauldron="none", hair="flowing", crown=0),
    # Architecture, worn: a high pointed collar and shoulder spurs swept up
    # and out, over a long cloak.
    "architect": dict(collar=5, collar_fan=0.52, skirt=4, cape=6,
                      pauldron="angular", hair="long", crown=0),
    # A greatcoat: standing collar, epaulettes, belt and chain, open tails.
    "commander": dict(collar=3, collar_fan=0.18, tails=True, belt=True,
                      emblem=True, pauldron="epaulette", hair="ponytail",
                      crown=0),
    # No ornament at all. Smooth helm, two lit eyes, and nothing else - which
    # is the whole character.
    "sentinel": dict(collar=1, collar_fan=0.05, pauldron="none", hair="helm",
                     crown=0),
}


def head(parent="head"):
    """
    A skull, rather than a cube with a face painted on it.

    <h2>Why this is the part that mattered</h2>

    The court kept vanilla's 8x8x8 head all the way through the costume rebuild
    and the material and grain work, and by the end it was the single most
    obviously wrong thing on the model: a hundred shaped parts of tailoring
    below, and above them one perfect cube with a stripe across it for eyes.
    Every other fix made it worse, because the better the surface got the more
    the cube read as a cube.

    A head is the hardest thing on a humanoid to fake, and the reason is that
    people read faces at a resolution they read nothing else at. So it is built
    the way a head is built: a cranium that is widest above the ears and falls
    away at the back, a brow that overhangs, sockets the eyes sit *inside*, a
    nose with a bridge and a tip that are not the same width, cheekbones that
    catch the light, a jaw that tapers to a chin, and ears - which nobody looks
    at and everybody notices the absence of.

    All of it inside the same 8-wide envelope vanilla uses, so the crowns,
    hair and helms hung off `head` by `costume` still fit.
    """
    rows = []

    # The cranium. bulge=0.34 puts the widest ring high, just above where the
    # ears go, which is where a human skull is actually widest - an egg with
    # its widest point in the middle reads as a bean.
    rows += org.ovoid("skull", parent, 6, 6.0, 3.7, (0, -7.4, -0.2),
                      1.05, 0.40)
    # The back of the head. A skull is not front-back symmetrical; the occiput
    # projects, and leaving it out is most of why a boxy head reads as flat.
    rows.append(("occiput", -2.6, -6.4, 2.4, 5.2, 3.4, 1.4, 0, 0, 0,
                 {"parent": parent, "rot": (0.16, 0, 0)}))

    # The brow, overhanging - it is what puts the eyes in shadow, and the
    # shadow is what makes them read as set into a face rather than drawn on
    # the front of a box.
    rows.append(("brow", -3.4, -5.0, -4.1, 6.8, 1.1, 1.0, 0, 0, 0,
                 {"parent": parent, "rot": (-0.20, 0, 0)}))
    for side, sign in (("left", 1), ("right", -1)):
        # The eye, under the brow and proud of the cheek.
        #
        # Clearances here are worked out rather than eyeballed, because an eye
        # is the one part of this model painted in a *different* colour from
        # what surrounds it, and that makes it the one place where two boxes
        # sharing a volume actually shows. The first version overlapped the
        # cheekbone by half a pixel in all three axes and rendered as a spray
        # of hair-coloured speckle across the face that crawled when the head
        # turned. Everywhere else on the head the neighbours are all "skin" and
        # z-fighting between two identical colours is invisible - which is
        # exactly why this was the one that got noticed.
        #
        # Brow's lower edge lands at y = -4.4 once its -0.20 rotation is
        # applied; the cheek's upper edge at -2.49 after its own. The eye sits
        # in the 1.5 pixels between them, and 0.1 in front of the skull ring
        # behind it.
        rows.append(("head_iris_%s" % side, -0.9, -3.75, -4.15, 1.8, 0.85, 0.55,
                     sign * 1.9, 0, 0, parent))
        # Cheekbone, angled out and down, catching light under the socket.
        rows.append(("cheek_%s" % side, -1.5, -2.85, -3.5, 3.0, 1.6, 1.2,
                     sign * 2.0, 0, 0,
                     {"parent": parent, "rot": (0.10, sign * 0.20, 0)}))
        # An ear: two small boxes so it has a lobe.
        rows += org.chain("ear_%s" % side, parent, 2, (0.8, 1.6, 1.8),
                          (0.10, 0, 0), (sign * 4.0, -4.4, 0.2), taper=0.80)

    # The nose: a bridge and a tip, and they are not the same width, which is
    # the whole of what makes it a nose and not a peg.
    rows.append(("nose_bridge", -0.7, -4.2, -4.2, 1.4, 1.8, 1.0, 0, 0, 0,
                 {"parent": parent, "rot": (-0.12, 0, 0)}))
    rows.append(("nose_tip", -1.0, -2.35, -4.4, 2.0, 1.0, 1.2, 0, 0, 0,
                 {"parent": parent, "rot": (0.10, 0, 0)}))

    # The jaw, narrowing to a chin. Three plates rather than one box, because
    # a jaw is a curve seen from the front and a taper seen from the side.
    rows += org.plates("jaw", parent, 3, (6.2, 1.5, 4.6), (0, -2.6, -0.6),
                       spacing=1.4, lean=-0.10, shrink=0.84)
    rows.append(("chin", -1.2, -0.5, -3.9, 2.4, 1.2, 1.4, 0, 0, 0,
                 {"parent": parent, "rot": (0.22, 0, 0)}))

    # The neck. Tapered, and tucked slightly back, because a neck that leaves
    # the skull at the front of the jaw is a chin resting on a chest.
    rows += org.chain("neck", parent, 2, (3.4, 1.0, 3.2), (0.02, 0, 0),
                      (0, -1.6, 0.6), taper=0.94)
    return rows


def torso(parent="body"):
    """
    Shoulders, chest, waist, hips - four masses, not one slab.

    Vanilla's body is a rectangle 8 wide and 4 deep from the collarbone to the
    hips, which is a person shaped like a filing cabinet. What separates a
    human silhouette from that is one thing: the **taper**. Shoulders are the
    widest point, the waist is roughly two-thirds of them, and the hips come
    back out. Everything else here is detail on top of that one proportion.
    """
    rows = []
    # Clavicles, swept back and out. They are what give the top of the torso a
    # width the chest itself does not have.
    for side, sign in (("left", 1), ("right", -1)):
        rows.append(("clavicle_%s" % side, -2.6, -0.6, -1.9, 5.2, 1.6, 3.6,
                     sign * 2.0, 0.4, 0,
                     {"parent": parent, "rot": (0.06, 0, sign * 0.16)}))
        rows.append(("scapula_%s" % side, -2.4, 0.6, 0.4, 4.8, 3.6, 1.6,
                     sign * 1.8, 0, 0,
                     {"parent": parent, "rot": (-0.08, 0, sign * 0.10)}))

    rows += org.ovoid("chest", parent, 4, 5.6, 4.1, (0, 0.6, 0), 0.62, 0.26)
    rows += org.ovoid("waist", parent, 3, 3.6, 3.0, (0, 6.0, 0), 0.70, 0.40)
    rows += org.ovoid("hips", parent, 3, 3.2, 3.9, (0, 9.2, 0), 0.68, 0.62)
    # The spine, down the back, so the torso has a centre line from behind.
    rows += org.chain("spine", parent, 4, (1.8, 2.6, 1.2), (0.03, 0, 0),
                      (0, 1.0, 2.0), taper=0.96)
    return rows


def arm(side, sign):
    """
    Deltoid, upper arm, elbow, forearm, wrist, hand - and actual fingers.

    The hand is where the part count goes and it is worth it. A mitten reads
    as a mitten at any distance; four fingers and a thumb read as a hand even
    when each is one pixel across, because the silhouette has the right number
    of gaps in it.
    """
    root = "arm_%s" % side
    rows = [(root, 0, 0, 0, 0, 0, 0, sign * 5, 1.8, 0)]

    # <h2>The height budget</h2>
    #
    # Vanilla's arm runs from y=2 to y=14 and its legs from 12 to 24, and those
    # numbers are not style - they are what the entity's hitbox, its held item
    # and every armour model in the game are built against. The first version
    # of this ignored them and set each chain's link length by eye: three links
    # of 2.6 is 7.5 pixels of upper arm alone, and the envoy came out 44 pixels
    # tall against vanilla's 32, with fingertips at the ankle. It read exactly
    # as what it was, a person stretched by a third.
    #
    # So every run below is costed. The arm is 12 from shoulder to wrist -
    # deltoid 2, upper 4.5, elbow 1, forearm 4.5 - and the hand carries on
    # another 5 to put the fingertips at mid-thigh, which is where a human's
    # are.
    rows += org.ovoid("deltoid_%s" % side, root, 2, 2.0, 2.1, (0, -0.8, 0),
                      1.0, 0.35)
    rows += org.chain("upper_%s" % side, root, 3, (3.0, 1.5, 3.0),
                      (0.02, 0, 0), (0, 1.2, 0), taper=0.96)
    rows.append(("elbow_%s" % side, -1.4, 0, -1.4, 2.8, 1.0, 2.8,
                 0, 1.5, 0, "upper_%s_2" % side))
    rows += org.chain("fore_%s" % side, "elbow_%s" % side, 3, (2.6, 1.5, 2.6),
                      (0.02, 0, 0), (0, 1.0, 0), taper=0.94,
                      lead=(0.06, 0, 0))
    rows.append(("wrist_%s" % side, -1.1, 0, -1.1, 2.2, 0.8, 2.2,
                 0, 1.5, 0, "fore_%s_2" % side))
    rows.append(("palm_%s" % side, -1.3, 0, -1.0, 2.6, 1.8, 2.0,
                 0, 0.8, 0, "wrist_%s" % side))
    # Four fingers off the palm, and a thumb off the side of it at an angle -
    # a thumb in line with the fingers is a fifth finger, which is the tell.
    rows += org.digits("hand_%s" % side, "palm_%s" % side, 4, 3,
                       (0.7, 0.8, 0.8), 0.72, (0, 1.8, 0), curl=0.34,
                       sign=sign, fan=0.07)
    rows += org.chain("thumb_%s" % side, "palm_%s" % side, 3,
                      (0.8, 0.8, 0.9), (0.16, 0, 0),
                      (sign * 1.3, 0.7, -0.5), taper=0.88,
                      lead=(0.30, 0, sign * 0.55))
    return rows


def leg(side, sign):
    """
    Thigh, knee, calf, ankle, and a foot with a heel and toes.

    The old leg ended in a 4x1.8x6 slab, which is a shoe box. A foot has a
    heel behind the ankle and toes in front of it, and the reason that matters
    at this scale is the walk: the part of a stride a viewer actually reads is
    the foot rolling, and a slab cannot roll.
    """
    root = "leg_%s" % side
    # Costed the same way as the arm: thigh 6, knee 1, calf 4.5, ankle 0.8,
    # sole 1.2, off a root at 10.8 - which puts the bottom of the foot on
    # y=24.3 against vanilla's 24. The 0.3 is a rounding artefact of the
    # half-pixel size grid and is under a third of a texel.
    rows = [(root, 0, 0, 0, 0, 0, 0, sign * 2, 10.8, 0)]
    rows += org.chain("thigh_%s" % side, root, 3, (3.8, 2.0, 3.6),
                      (0.02, 0, 0), (0, 0.2, 0), taper=0.96)
    rows.append(("knee_%s" % side, -1.8, 0, -1.9, 3.6, 1.0, 3.6,
                 0, 2.0, 0, "thigh_%s_2" % side))
    rows += org.chain("calf_%s" % side, "knee_%s" % side, 3, (3.2, 1.5, 3.2),
                      (0.02, 0, 0), (0, 1.0, 0), taper=0.94,
                      lead=(-0.05, 0, 0))
    rows.append(("ankle_%s" % side, -1.3, 0, -1.3, 2.6, 0.8, 2.6,
                 0, 1.5, 0, "calf_%s_2" % side))
    rows.append(("heel_%s" % side, -1.5, 0, 0.2, 3.0, 1.2, 2.0,
                 0, 0.8, 0, "ankle_%s" % side))
    rows.append(("foot_%s" % side, -1.7, 0, -2.8, 3.4, 1.2, 3.0,
                 0, 0.8, 0, "ankle_%s" % side))
    rows += org.digits("toe_%s" % side, "foot_%s" % side, 3, 1,
                       (0.9, 0.8, 1.0), 1.0, (0, 0.2, -2.8), curl=0.05,
                       sign=sign, fan=0.05)
    return rows


def envoy_body(kind, p):
    """Shared anatomy plus one kind's costume. Names are prefixed per kind so
    setupAnim can hide a whole wardrobe with one loop.

    `head` and `body` carry no geometry any more. They are pivots - the joints
    the animation turns and the costume hangs off - and the person is built
    around them. They used to be vanilla's 8x8x8 and 8x12x4 cubes, which meant
    every shaped part added was an improvement laid *over* two cubes that were
    still in the silhouette underneath.
    """
    rows = [("head", 0, 0, 0, 0, 0, 0, 0, 0, 0),
            ("body", 0, 0, 0, 0, 0, 0, 0, 0, 0)]
    rows += head()
    rows += torso()
    for side, sign in (("left", 1), ("right", -1)):
        rows += arm(side, sign)
        rows += leg(side, sign)
    rows += costume(kind, p)
    return rows


def costume(kind, p):
    """
    Everything above the skin, per character, straight off the sheets.

    The livery is shared - black, emerald, gold - so what tells one from
    another has to be shape: a laurel, a ponytail, a fan of collar, a coat with
    tails. Colour alone was what the old models relied on and it is precisely
    why four of the five looked the same.
    """
    rows = []
    k = kind

    # A standing collar. Every one of the five has one; only the height and
    # how far it fans out differ.
    rows += org.plates("%s_collar" % k, "body", p["collar"],
                       (7.6, 1.8, 5.6), (0, -0.6, 1.0), spacing=-1.9,
                       lean=-p["collar_fan"], shrink=1.06)

    # The garment: overlapping panels down the front, then a skirt or tails.
    rows += org.plates("%s_front" % k, "body", 4, (6.4, 2.6, 1.4),
                       (0, 1.4, -2.4), spacing=2.4, lean=0.05, shrink=0.96)
    if p.get("skirt"):
        rows += org.plates("%s_skirt" % k, "body", p["skirt"],
                           (8.4, 3.0, 5.6), (0, 7.6, 0), spacing=2.7,
                           lean=0.06, shrink=1.12)
    if p.get("tails"):
        # A greatcoat opens at the front and hangs behind - two panels, not a
        # skirt, which is the difference between a uniform and a gown.
        for side, sign in (("left", 1), ("right", -1)):
            rows += org.chain("%s_tail_%s" % (k, side), "body", 4,
                              (3.6, 3.4, 1.6), (0.03, 0, 0),
                              (sign * 2.2, 7.6, 1.6), taper=0.97,
                              lead=(-0.05, 0, 0))

    if p.get("belt"):
        rows.append(("%s_belt" % k, -4.4, 6.4, -2.4, 8.8, 1.8, 4.8,
                     0, 0, 0, "body"))
        rows.append(("%s_buckle" % k, -1.4, 6.0, -3.0, 2.8, 2.6, 0.8,
                     0, 0, 0, "body"))

    if p.get("emblem"):
        # The eagle on the Emperor's chest, the winged mark on Lillith's coat.
        rows.append(("%s_emblem" % k, -3.0, 1.6, -3.0, 6.0, 3.0, 1.0,
                     0, 0, 0, "body"))
        for side, sign in (("left", 1), ("right", -1)):
            rows += org.chain("%s_wing_%s" % (k, side), "body", 3,
                              (2.2, 1.2, 0.9), (0, 0, -sign * 0.10),
                              (sign * 2.6, 2.4, -2.8), taper=0.84,
                              lead=(0, 0, -sign * 1.30))

    if p.get("cape"):
        rows += org.plates("%s_cape" % k, "body", p["cape"],
                           (10.0, 3.2, 1.2), (0, -0.2, 2.2), spacing=2.9,
                           lean=-0.04, shrink=0.99)

    style = p["pauldron"]
    for side, sign in (("left", 1), ("right", -1)):
        arm = "arm_%s" % side
        if style == "banded":
            # The Emperor's segmented rings, each smaller than the last.
            rows += org.plates("%s_band_%s" % (k, side), arm, 4,
                               (5.6, 1.8, 5.6), (0, -2.4, 0), spacing=1.9,
                               lean=0.10, shrink=0.90)
        elif style == "epaulette":
            # A flat board on the shoulder with a fringe under it.
            rows.append(("%s_board_%s" % (k, side), -2.6, -2.6, -2.6,
                         5.6, 1.4, 5.2, 0, 0, 0, arm))
            rows += org.chain("%s_fringe_%s" % (k, side), arm, 3,
                              (1.0, 1.6, 1.0), (0.04, 0, 0),
                              (sign * 1.8, -1.0, 0), taper=0.88,
                              lead=(0, 0, -sign * 0.12))
        elif style == "angular":
            # Sylphara's architecture: a plate swept up and outward.
            rows += org.chain("%s_spur_%s" % (k, side), arm, 3,
                              (3.0, 2.4, 3.4), (0, 0, -sign * 0.14),
                              (0, -2.0, 0), taper=0.80,
                              lead=(-0.20, 0, -sign * 1.05))

    # -- head: hair, helm, crown -------------------------------------------
    hair = p["hair"]
    if hair == "helm":
        # The Sentinel is not wearing anything; the head *is* the costume.
        rows += org.ovoid("%s_helm" % k, "head", 4, 8.4, 4.6,
                          (0, -8.2, 0), 1.05, 0.40)
        rows.append(("%s_eye_left" % k, 0.8, -5.6, -4.9, 2.2, 1.2, 0.6,
                     0, 0, 0, "head"))
        rows.append(("%s_eye_right" % k, -3.0, -5.6, -4.9, 2.2, 1.2, 0.6,
                     0, 0, 0, "head"))
    else:
        # A cap over the crown, not a second head.
        #
        # This used to be an ovoid 8.0 tall and 8.8 wide set 1.6 back, which
        # was sized against the old 8x8x8 cube and comfortably swallowed it.
        # Against a built skull - 6.0 tall, 7.4 wide - it was a slab hanging
        # off the back of the head, and it read as one. Hair covers the top and
        # the back of a cranium and stops at the brow, so this now follows the
        # skull's own profile a half-pixel proud of it and only over its top
        # two-thirds. The face is what is left.
        # Built from the *skull's own* ring geometry, one size up, and only
        # the top three rings of it.
        #
        # Sizing it independently is what produced the first version's
        # forehead: a cap of four rings at radius 3.9 with a different bulge
        # crosses a skull of six rings at 3.7, so at two of them the skull was
        # the wider of the two and poked through hair that was supposed to
        # cover it. Coplanar quads at the same depth is z-fighting, and it
        # showed as a spray of hair-coloured speckle across the brow that moved
        # when the head turned. The same rings, the same bulge, a bigger
        # radius: then every ring is strictly outside its counterpart and there
        # is nothing to fight over.
        rows += org.ovoid("%s_hair" % k, "head", 6, 6.0, 4.3,
                          (0, -7.5, -0.2), 1.05, 0.40)[:3]
        rows.append(("%s_nape" % k, -3.0, -4.4, 2.2, 6.0, 3.0, 1.4,
                     0, 0, 0, {"parent": "head", "rot": (0.12, 0, 0)}))
        if hair == "long":
            for side, sign in (("left", 1), ("right", -1)):
                rows += org.chain("%s_fall_%s" % (k, side), "head", 5,
                                  (2.4, 3.0, 2.2), (0.03, 0, 0),
                                  (sign * 3.4, -5.0, 0.6), taper=0.95)
        elif hair == "ponytail":
            # Clear of the skull and falling behind it. Started at the crown
            # it just sat beside the head as a coloured block.
            rows += org.chain("%s_tail" % k, "head", 7, (2.4, 3.2, 2.4),
                              (0.20, 0, 0), (0, -7.8, 4.4), taper=0.91,
                              lead=(-0.95, 0, 0))
        elif hair == "flowing":
            # Aurelia is a star that stopped being one. The hair is the
            # silhouette - it is larger than she is and it is the only thing
            # on any of these five that is meant to read from across a room.
            for i in range(5):
                sign = 1 if i % 2 == 0 else -1
                rows += org.chain("%s_ray%d" % (k, i), "head", 5,
                                  (2.6, 3.4, 2.4), (0.10 + 0.03 * i, 0, 0),
                                  (sign * (2.0 + i * 0.9), -6.4 - i * 0.5, 1.0),
                                  taper=0.92,
                                  lead=(-0.30, sign * 0.20, -sign * (0.45 + i * 0.14)))

    if p.get("crown"):
        # A laurel: leaves fanned around the head, not a band on top of it.
        for i in range(p["crown"]):
            angle = (i / p["crown"]) * 6.2832
            import math as _m
            rows.append(("%s_leaf%d" % (k, i),
                         -0.8, -1.9, -0.5, 1.6, 3.8, 1.0,
                         _m.sin(angle) * 4.0, -8.4, _m.cos(angle) * 4.0,
                         {"parent": "head",
                          "rot": (_m.cos(angle) * 0.45, -angle,
                                  -_m.sin(angle) * 0.45)}))

    return rows


# Two more materials than the character sheets name, derived from `base`.
#
# The mobs had the same problem and it is the same fix (FIXES §35): the court's
# *mean* luminance was already vanilla's and its *range* was a quarter of it -
# the architect measured std 16 against a cow's 48.6, which is a figure with no
# light on it anywhere. Six named colours all sitting between 58 and 93 is one
# grey suit in six shades, and at eight metres that is a silhouette.
#
# The character sheets do not name these because a person describing a costume
# does not: they say "black coat with a high collar", and the collar being the
# part that catches the light is left to the painter. So `trim` is the lit edge
# of the tailoring - collar, epaulette, belt, boot - and `deep` is what falls
# into shadow inside the outline. Derived rather than written out, so retuning
# a kind's `base` carries its whole wardrobe with it.
DERIVED = {"trim": (1.62, 0.50), "deep": (0.60, 0.88)}


def derive(palette):
    """A palette plus the two materials that give it a tonal range."""
    full = dict(palette)
    red, green, blue = hex_to_rgb(palette["base"])
    lum = 0.2126 * red + 0.7152 * green + 0.0722 * blue
    for name, (scale, keep) in DERIVED.items():
        target = lum * scale
        full[name] = "#%02x%02x%02x" % tuple(
            max(0, min(255, int(channel * scale * keep + target * (1 - keep))))
            for channel in (red, green, blue))
    return full


# Which of art/grain.py's surfaces each material wears. These are people in
# tailoring rather than carapace, so almost all of it is cloth; what is not
# cloth is the metal they are decorated with, the skin above the collar, and
# hair, which is the one thing on either mod that falls rather than wraps.
ROLE_GRAIN = {
    "base": "cloth",
    "trim": "cloth",
    "deep": "cloth",
    "cape": "cloth",
    "regalia": "metal",
    "accent": "metal",
    "skin": "smooth",
    "hair": "strand",
}

TRIM = ("_collar", "_board_", "_belt", "_spur_", "_helm", "chest")
DEEP = ("body", "hips", "arm_", "fore_", "calf_", "thigh_", "leg_",
        "_fringe_", "_fall_")


# The second colour a role is marked in. Everything gold-marked takes the
# accent; a face takes its own hair colour, because a gold line across a cheek
# is a scar rather than a livery.
ROLE_MARK = {"skin": "hair"}


# Bare skin. Everything the anatomy builds above the collar and past the cuff -
# a face, an ear, a hand - plus nothing else, because these are people in
# clothes and the clothes are the rest of the model.
#
# Written out rather than pattern-matched, and checked. When the head was one
# cube the rule was `n.startswith("skull") or n == "head"` and it covered
# everything there was; the moment a head became a brow, a nose, a jaw and a
# chin, every new part fell through to the clothing colour and the first render
# was a pale slab floating over a face made of coat. A list that is verified
# against the actual part names cannot fail that way quietly.
SKIN = ("skull_", "occiput", "brow", "cheek_", "nose_", "jaw_", "chin",
        "ear_", "neck_", "palm_", "wrist_", "hand_", "thumb_")

# Boots. The foot is the one place the anatomy and the costume meet, and it
# belongs to the costume.
SHOE = ("boot_", "heel_", "foot_", "toe_", "ankle_")


def role_for(box_name):
    """
    Which of the palette's materials a box is made of.

    Matched on the role in the name rather than a lookup table, because the
    names are generated now and a hand list would go stale silently - which is
    exactly what happened to elysium-mobs' accent list (see FIXES §33).

    This is a *material*, and materials are part of a patch's identity. It was
    read straight into a colour at draw time and never reached the packer,
    which meant two same-sized boxes of different materials - a jaw and a
    buckle, a cape panel and a greave - shared one patch on the sheet, and
    whichever happened to be painted last decided the colour of both. The
    court has been wearing a shuffled wardrobe ever since costume replaced the
    thirteen-box humanoid. Threading it through `Skin` is what fixes it: see
    the note on key_of in art/boxmodel.py, which is the same bug one layer up.

    Order matters. The costume tests come first, because a kind's own parts are
    prefixed with its name and a name like `commander_tail` must not be read as
    anatomy - and `head_iris_` must be tested before the skin list, or an eye
    is the same colour as the face it is in.
    """
    n = box_name
    if ("hair" in n or "_nape" in n or "_fall_" in n
            or n.endswith("_tail") or "_ray" in n):
        return "hair"
    # Eyes. The darkest material the palette derives, not the hair colour -
    # Lillith is a redhead and it gave her red eyes. Not the accent either: a
    # gold or green iris is a lit optic, which is the Sentinel and nobody else.
    if n.startswith("head_iris_"):
        return "deep"
    if "_helm" in n:
        return "base"
    if "_eye_" in n:
        return "accent"
    if "_cape" in n or "_skirt" in n or "_tail_" in n:
        return "cape"
    if "_leaf" in n or "_crown" in n:
        return "regalia"
    if "_emblem" in n or "_wing_" in n or "_buckle" in n:
        return "regalia"
    if n.startswith(SKIN):
        return "skin"
    if n.startswith(SHOE):
        return "trim"
    if any(mark in n for mark in TRIM):
        return "trim"
    if any(mark in n for mark in DEEP):
        return "deep"
    return "base"


def colours_for(box_name, palette):
    """Which two colours a box is painted in: its material, and its mark."""
    role = role_for(box_name)
    return palette[role], palette[ROLE_MARK.get(role, "accent")]


# The few parts that carry a gold mark. Everything else stays plain: a band on
# every panel is the confetti problem elysium-mobs hit at thirty parts, and
# these are heading past a hundred.
def accent_parts_for(boxes):
    marks = set()
    for row in boxes:
        n = row[0]
        if ("_buckle" in n or "_emblem" in n or "_wing_" in n or "_leaf" in n
                or "_eye_" in n or n.endswith("_collar_0")
                or n.endswith("_front_0") or n.endswith("_board_left")
                or n.endswith("_board_right")
                # The face. `head` is a plain 8x8x8 box and its front face was
                # one flat rectangle of skin - which read as a blank slab the
                # moment the costume around it got light enough to see, and
                # a bright blank face is worse than a dark one. The band
                # draw_box puts a third of the way down a front face is an eye
                # line, and ROLE_MARK paints a face in its own hair colour
                # rather than in gold, because a gold stripe across a cheek is
                # a scar and not a livery.
                or n == "head"):
            marks.add(n)
    return marks


def wardrobe(boxes, marks):
    """
    The court's Skin: which material every part is made of, and what is marked.

    The palette is left empty on purpose. All five envoys share one model and
    one UV layout and differ only in colour, so the packer must be told about
    materials - two materials cannot share a patch - while the colours
    themselves are chosen per kind by write_sheet. A Skin with no palette is
    exactly that: identity for packing, nothing for painting.
    """
    return Skin({}, {row[0]: role_for(row[0]) for row in boxes}, marks,
                ROLE_GRAIN)


def write_sheet(path, palette, boxes, sheet, skin):
    pixels = np.zeros((sheet, sheet, 4), dtype=np.float64)
    placed = pack(boxes, sheet, skin)
    # One grain field per surface, shared by all five kinds: they share a model
    # and a UV layout, so the emperor's collar and the queen's occupy the same
    # texels and must take the same weave, or the two sheets differ in a way
    # that has nothing to do with either character.
    fields = skin.fields(sheet)
    for box in boxes:
        if is_pivot(box):
            continue
        base, accent = colours_for(box[0], palette)
        draw_box(pixels, box, placed[key_of(box, skin)], base, accent, sheet,
                 skin.accented(box[0]), fields.get(skin.grain(box[0])))
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(pixels.astype(np.uint8), "RGBA").save(path)


# ---------------------------------------------------------------------------
# Java
# ---------------------------------------------------------------------------

MODEL = '''package com.elysium.npcs.client.model;

import com.elysium.npcs.entity.EnvoyKind;
import com.elysium.npcs.entity.ImperialEnvoy;
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
 * GENERATED by tools/gen_npcs.py. Do not edit.
 *
 * One body, five wardrobes.
 *
 * The body - skull, chest, hips, tapering limbs, hands - is shared, because
 * the court is human where the bestiary is not and that contrast is most of
 * what makes them read as the half of the Empire you can talk to. Everything
 * above the skin belongs to exactly one of the five and is hidden for the
 * other four.
 *
 * Five separate models would be five copies of a humanoid to keep in step, and
 * the moment one gained an elbow and the others did not the court would stop
 * reading as one set. Hiding a wardrobe costs one boolean per part and
 * Minecraft skips an invisible part before it touches its cubes.
 */
public class EnvoyModel<T extends ImperialEnvoy> extends EntityModel<T> {{

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart armLeft;
    private final ModelPart armRight;
    private final ModelPart legLeft;
    private final ModelPart legRight;

{fields}
    public EnvoyModel(ModelPart root) {{
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.armLeft = root.getChild("arm_left");
        this.armRight = root.getChild("arm_right");
        this.legLeft = root.getChild("leg_left");
        this.legRight = root.getChild("leg_right");

{wiring}    }}

    /** Walk a path of child names from the root. */
    private ModelPart at(String... path) {{
        ModelPart part = this.root;
        for (String name : path) {{
            part = part.getChild(name);
        }}
        return part;
    }}

    private static void show(ModelPart[] parts, boolean visible) {{
        for (ModelPart part : parts) {{
            part.visible = visible;
        }}
    }}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{parts}
        return LayerDefinition.create(mesh, {sheet}, {sheet});
    }}

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {{
        this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
        this.head.xRot = headPitch * ((float) Math.PI / 180F);

        float swing = (float) Math.cos(limbSwing * 0.6662F) * 0.85F * limbSwingAmount;
        this.legLeft.xRot = swing;
        this.legRight.xRot = -swing;
        this.armLeft.xRot = -swing * 0.45F;
        this.armRight.xRot = swing * 0.45F;

    }}

    /**
     * Which of the five wardrobes this model is currently wearing.
     *
     * This lived at the bottom of setupAnim, and it did not belong there for
     * two separate reasons.
     *
     * It is not animation. It does not read limbSwing, limbSwingAmount or
     * ageInTicks; it is a constant for the life of an entity, and running five
     * array loops per envoy per frame to re-establish it is work for nothing.
     *
     * And it was the one thing in setupAnim that dereferenced the entity,
     * which made the model impossible to pose outside a running game. The
     * offline previewer (mobs/tools/PoseCapture.java) drives the real
     * createBodyLayer, the real setupAnim and the real renderToBuffer with a
     * null entity - every generated mob model ignores it - and the court alone
     * threw, so the court alone could only ever be captured standing still.
     * Five characters were rebuilt from their reference sheets without anybody
     * being able to watch one walk.
     *
     * Moved here, setupAnim never touches the entity, and the previewer works
     * by construction rather than by a null check somebody has to remember.
     */
    public void wardrobe(EnvoyKind kind) {{
{toggles}    }}

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
                               int overlay, int colour) {{
        this.root.render(pose, buffer, light, overlay, colour);
    }}
}}
'''

RENDERER = '''package com.elysium.npcs.client;

import com.elysium.npcs.client.model.EnvoyModel;
import com.elysium.npcs.entity.EnvoyKind;
import com.elysium.npcs.entity.ImperialEnvoy;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * GENERATED by tools/gen_npcs.py. Do not edit.
 *
 * The texture comes from the envoy's kind, which is what lets one entity type
 * be five people.
 */
public class EnvoyRenderer extends MobRenderer<ImperialEnvoy, EnvoyModel<ImperialEnvoy>> {

    public EnvoyRenderer(EntityRendererProvider.Context context) {
        super(context, new EnvoyModel<>(context.bakeLayer(
                ElysiumNpcsClient.ENVOY_LAYER)), 0.5F);
    }

    /**
     * The wardrobe is set here, once per frame per envoy, rather than inside
     * setupAnim - see the note on EnvoyModel.wardrobe. The renderer is the
     * thing that knows both the model and the entity, which makes it the place
     * where "this model is now that person" belongs.
     */
    @Override
    public void render(ImperialEnvoy entity, float yaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffers, int light) {
        this.getModel().wardrobe(entity.getKind());
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ImperialEnvoy entity) {
        return entity.getKind().texture();
    }
}
'''

CLIENT = '''package com.elysium.npcs.client;

import com.elysium.npcs.ElysiumNpcs;
import com.elysium.npcs.client.model.EnvoyModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * GENERATED by tools/gen_npcs.py. Do not edit.
 *
 * Client-only, and annotated so: a renderer class touched on a dedicated server
 * is a NoClassDefFoundError at load, and the annotation is what keeps this file
 * from ever being loaded there.
 */
@EventBusSubscriber(modid = ElysiumNpcs.MODID, bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ElysiumNpcsClient {

    private ElysiumNpcsClient() {
    }

    public static final ModelLayerLocation ENVOY_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ElysiumNpcs.MODID, "envoy"), "main");

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ENVOY_LAYER, EnvoyModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ElysiumNpcs.ENVOY.get(), EnvoyRenderer::new);
    }
}
'''

# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

NAMES = {
    "emperor": ("Elysomnion", "The Code has no filing for you yet."),
    "architect": ("Sylphara Voss", "You have not been drawn on any plan of mine."),
    "sentinel": ("Sentinel", "Observation is not yet warranted."),
    "commander": ("Lillith", "The fleet takes recommendations. You have none."),
    "queen": ("Aurelia", "I have not yet had cause to look at you."),
}

TITLES = {
    "emperor": "Emperor of the Black and Emerald",
    "architect": "Chief Imperial Architect",
    "sentinel": "Stealth Envoy",
    "commander": "Fleet Commander of the Empire",
    "queen": "Queen of the Empire, Former Sentient Star",
}


def write_lang():
    lang = {
        "itemGroup.elysiumnpcs": "Elysium Court",
        "entity.elysiumnpcs.envoy": "Imperial Envoy",
        "elysiumnpcs.nothing_to_give":
            "They search their office and find nothing to give you.",
        "elysiumnpcs.arrival": "%s has come to find you.",

        # §27.1's amnesty row. The tooltip carries the floor caveat as well as
        # the failure message does, because a player reads the tooltip in the
        # shop and the failure message only after carrying the writ home.
        "item.elysiumnpcs.writ_of_amnesty": "Writ of Amnesty",
        "elysiumnpcs.amnesty.tooltip":
            "Discharges %s Suspicion when used.",
        "elysiumnpcs.amnesty.tooltip_floor":
            "Never below the structural floor of what you have claimed.",
        "elysiumnpcs.amnesty.granted":
            "The file is amended. %s discharged; %s outstanding.",
        "elysiumnpcs.amnesty.at_floor":
            "Nothing here can be discharged. Suspicion is already at the "
            "structural floor of %s — the Empire will not forget a fortress.",
        "elysiumnpcs.amnesty.nothing_filed":
            "There is nothing on file to amend.",

        # Court commissions, §14.5/§19.2. These belong here rather than in the
        # JSON because write_lang() REWRITES that file wholesale — a key added
        # by hand survives exactly until the next time anybody runs this script,
        # which is how all seven of these were lost once already. The validator
        # caught it; the fix is to put them where the generator can see them.
        "elysiumnpcs.commission.expired":
            "The commission lapsed. The Court has noted it.",
        "elysiumnpcs.commission.issued":
            "Commission: deliver %s %s within %s seconds.",
        "elysiumnpcs.commission.not_my_office":
            "This office does not commission work. Take it to the throne.",
        "elysiumnpcs.commission.nothing_wanted":
            "The Court wants for nothing it can name.",
        "elysiumnpcs.commission.outstanding":
            "Outstanding: %s more %s. %s seconds remain.",
        "elysiumnpcs.commission.settled":
            "The commission is discharged. %s rewards are entered against "
            "your name.",
        "elysiumnpcs.commission.unknown_item":
            "something no longer catalogued",
    }
    for kind, (name, refusal) in NAMES.items():
        lang[f"entity.elysiumnpcs.envoy.{kind}"] = name
        lang[f"elysiumnpcs.refusal.{kind}"] = refusal
        lang[f"item.elysiumnpcs.{kind}_summons"] = f"Writ of Audience: {name}"
        lang[f"elysiumnpcs.title.{kind}"] = TITLES[kind]
    path = RES / "lang/en_us.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(lang, handle, indent=2, ensure_ascii=False, sort_keys=True)
    return len(lang)


def write_models():
    """A plain generated model for each writ, so none of them is a purple cube."""
    written = 0
    path = RES / "models/item/writ_of_amnesty.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump({"parent": "minecraft:item/generated",
                   "textures": {"layer0": "elysiumnpcs:item/writ_of_amnesty"}},
                  handle, indent=2)
    written += 1
    for kind in NAMES:
        path = RES / f"models/item/{kind}_summons.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump({"parent": "minecraft:item/generated",
                       "textures": {"layer0": f"elysiumnpcs:item/{kind}_summons"}},
                      handle, indent=2)
        written += 1
    return written


def write_writ_sprites():
    """
    A sealed writ, in the colours of whoever sent it.

    Deliberately the same shape five times: these are five copies of one
    document, and what differs is the seal. A player reads which is which by the
    seal's colour, which is exactly how they read the envoy it summons.
    """
    written = 0
    for kind, palette in PALETTES.items():
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        parchment = (216, 206, 180, 255)
        shadow = (168, 158, 134, 255)
        ink = (28, 26, 24, 255)
        draw.rectangle([3, 1, 12, 14], fill=parchment, outline=ink)
        draw.rectangle([3, 11, 12, 14], fill=shadow)
        for y in (4, 6, 8):
            draw.line([(5, y), (10, y)], fill=shadow)
        seal = tuple(int(palette["regalia"].lstrip("#")[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
        draw.rectangle([6, 10, 9, 13], fill=seal, outline=ink)
        path = RES / f"textures/item/{kind}_summons.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        written += 1
    return written


def write_amnesty_sprite():
    """
    The same document, with the seal broken.

    Deliberately built from the summons writ rather than given a shape of its
    own. Every writ in this mod is one piece of Imperial paperwork, and what
    tells them apart is the seal — five whole seals in five colours summon five
    offices, and the one BROKEN seal is the file being closed. A player who has
    learned to read the seals already knows what this is.

    It is also the only writ with a struck-through line, which is the second
    reading at a glance: something on the page has been cancelled.
    """
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    parchment = (216, 206, 180, 255)
    shadow = (168, 158, 134, 255)
    ink = (28, 26, 24, 255)
    gold = (216, 177, 58, 255)

    draw.rectangle([3, 1, 12, 14], fill=parchment, outline=ink)
    draw.rectangle([3, 11, 12, 14], fill=shadow)
    for y in (4, 6, 8):
        draw.line([(5, y), (10, y)], fill=shadow)
    # The cancellation, straight through the writing.
    draw.line([(4, 3), (11, 9)], fill=gold)

    # One seal with a break punched through it.
    #
    # Drawn whole and then split, rather than as two small rectangles. Two
    # 2-pixel rectangles with a 1-pixel outline each are 2 pixels of outline
    # and none of fill, so the "broken gold seal" rendered as two black blocks
    # — the difference is invisible in the source and obvious at 16x.
    draw.rectangle([4, 10, 11, 13], fill=gold, outline=ink)
    draw.rectangle([7, 10, 8, 13], fill=ink)

    path = RES / "textures/item/writ_of_amnesty.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    return 1


def java_field(kind):
    return kind + "Parts"


def build():
    """Every kind's boxes, and the paths its costume parts live at."""
    all_boxes, paths = [], {}
    seen = set()
    for kind, spec in CHARACTERS.items():
        rows = envoy_body(kind, spec)
        parent = {r[0]: parent_of(r) for r in rows}
        own = []
        for row in rows:
            if row[0] in seen:
                continue
            seen.add(row[0])
            all_boxes.append(row)
            if row[0].startswith(kind + "_"):
                chain, node = [], row[0]
                while node is not None:
                    chain.append(node)
                    node = parent.get(node)
                own.append(list(reversed(chain)))
        paths[kind] = own
    return all_boxes, paths


def main():
    (JAVA / "client/model").mkdir(parents=True, exist_ok=True)

    boxes, paths = build()
    skin = wardrobe(boxes, accent_parts_for(boxes))
    sheet = smallest_sheet(boxes, skin=skin)

    fields, wiring, toggles = [], [], []
    for kind in CHARACTERS:
        field = java_field(kind)
        fields.append("    private final ModelPart[] %s;" % field)
        entries = ",\n            ".join(
            'at(%s)' % ", ".join('"%s"' % n for n in path)
            for path in paths[kind])
        wiring.append("        this.%s = new ModelPart[] {\n            %s };\n"
                      % (field, entries))
        toggles.append('        show(this.%s, kind == EnvoyKind.%s);\n'
                       % (field, kind.upper()))

    (JAVA / "client/model/EnvoyModel.java").write_text(
        MODEL.format(parts=part_lines(boxes, sheet, skin), sheet=sheet,
                     fields="\n".join(fields) + "\n",
                     wiring="".join(wiring),
                     toggles="".join(toggles)), encoding="utf-8")
    (JAVA / "client/EnvoyRenderer.java").write_text(RENDERER, encoding="utf-8")
    (JAVA / "client/ElysiumNpcsClient.java").write_text(CLIENT, encoding="utf-8")

    for kind, palette in PALETTES.items():
        write_sheet(RES / f"textures/entity/envoy/{kind}.png",
                    derive(palette), boxes, sheet, skin)

    print(f"model + renderer  : 3 files")
    print(f"parts             : {len(boxes)} on a {sheet}px sheet")
    print(f"skins written     : {len(PALETTES)}")
    print(f"writ sprites      : {write_writ_sprites() + write_amnesty_sprite()}")
    print(f"item models       : {write_models()}")
    print(f"lang keys         : {write_lang()}")


if __name__ == "__main__":
    main()
