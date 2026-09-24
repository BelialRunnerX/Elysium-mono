#!/usr/bin/env python3
"""
The vanilla GUI textures Elysium re-skins, drawn from the interface palette.

Run from the repo root:   python3 elysium-lib/tools/gen_gui.py

<h2>Why these are textures and the bars are code</h2>

Health, food and armour each have a layer id of their own, so the mod cancels
vanilla's and draws its own — see ElysiumHealthBar and ElysiumStatusBars. Two
parts of the interface cannot be reached that way and are re-skinned here
instead.

  * **The attack indicator.** Vanilla draws it inside the hotbar layer and
    inside the crosshair layer, depending on a setting. Cancelling either to
    reach it would take the hotbar or the crosshair with it.
  * **The inventory.** Its background is one texture with every slot recess
    already in the right place, and the screen that draws it also draws the
    player model, the recipe-book button and the slot contents on top. Drawing
    a panel over it by event would mean re-deriving all of those positions and
    keeping them in step with the game's, forever.

Overriding the texture is not a workaround for either. It is how the game
expects to be re-skinned, it cannot drift out of alignment with the code that
positions things on top of it, and it stops the moment somebody puts a resource
pack over it — which is the correct answer to "I liked the old one".

<h2>What it costs</h2>

These are written under `assets/minecraft/`, so they replace the textures for
every screen that uses them, not only Elysium's. That is the whole point — a
themed inventory that only applied to Elysium's own containers would be the
same inconsistency this file exists to remove — but it is worth stating plainly
in the one place it happens.

<h2>The slot coordinates</h2>

Vanilla's, and not negotiable: `InventoryMenu` puts the armour column at x=8
from y=8, the offhand at (77, 62), the 2x2 crafting grid at (98, 18), its
result at (154, 28), the three inventory rows at (8, 84) and the hotbar at
(8, 142), all on an 18-pixel pitch. The texture draws each slot's recess one
pixel up and left of the slot itself, which is where the game expects the
border. Getting any of these wrong produces a screen whose items float beside
their holes.
"""
import os
import pathlib
import sys

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "ui"))

from palette import PALETTE, rgba          # noqa: E402

RES = HERE.parent / "src/main/resources/assets/minecraft/textures/gui"


def colour(name, alpha=None):
    r, g, b, a = rgba(PALETTE[name])
    return (r, g, b, a if alpha is None else alpha)


SURFACE = colour("SURFACE", 255)
SURFACE_RAISED = colour("SURFACE_RAISED", 255)
SURFACE_SUNKEN = colour("SURFACE_SUNKEN", 255)
LINE = colour("LINE")
LINE_SOFT = colour("LINE_SOFT")
SLOT = colour("SLOT")
SLOT_EDGE = colour("SLOT_EDGE")
ACCENT = colour("ACCENT")
ACCENT_DIM = colour("ACCENT_DIM")
TEXT_FAINT = colour("TEXT_FAINT")

CLEAR = (0, 0, 0, 0)


def rect(img, x0, y0, x1, y1, fill):
    """Inclusive-exclusive, like every other rectangle in this project."""
    for y in range(max(0, y0), min(img.size[1], y1)):
        for x in range(max(0, x0), min(img.size[0], x1)):
            img.putpixel((x, y), fill)


def outline(img, x0, y0, x1, y1, fill):
    rect(img, x0, y0, x1, y0 + 1, fill)
    rect(img, x0, y1 - 1, x1, y1, fill)
    rect(img, x0, y0, x0 + 1, y1, fill)
    rect(img, x1 - 1, y0, x1, y1, fill)


def panel(img, x0, y0, x1, y1):
    """
    The same plate every Elysium screen is drawn on: a flat surface, a hairline
    border, and a brighter rule along the top edge.

    No gradient and no bevel. Both are authored at one size and then scaled by
    the player's GUI scale, so at scale 3 a one-pixel highlight is a three-pixel
    stripe — which is most of what made the old screens look unlike the game.
    """
    rect(img, x0, y0, x1, y1, SURFACE)
    outline(img, x0, y0, x1, y1, LINE)
    rect(img, x0 + 1, y0 + 1, x1 - 1, y0 + 2, ACCENT_DIM)
    # The corner notches the panel style cuts everywhere else.
    for corner in ((x0, y0), (x1 - 2, y0), (x0, y1 - 2), (x1 - 2, y1 - 2)):
        rect(img, corner[0], corner[1], corner[0] + 2, corner[1] + 2, CLEAR)


def slot(img, x, y, size=18):
    """
    One recess, drawn where the game puts a slot's border.

    Sunken rather than raised: an item sits *in* a slot. The top and left edges
    are the darker pair, which is what reads as depth under light from the top
    left — the same direction every sprite in the mod is lit from.
    """
    rect(img, x, y, x + size, y + size, SLOT)
    outline(img, x, y, x + size, y + size, SLOT_EDGE)
    rect(img, x + 1, y + 1, x + size - 1, y + 2, LINE_SOFT)
    rect(img, x + 1, y + 1, x + 2, y + size - 1, LINE_SOFT)


# --- vanilla's own coordinates ---------------------------------------------
ARMOUR_SLOTS = [(8, 8 + i * 18) for i in range(4)]
OFFHAND_SLOT = (77, 62)
CRAFTING_SLOTS = [(98 + c * 18, 18 + r * 18) for r in range(2) for c in range(2)]
RESULT_SLOT = (154, 28)
INVENTORY_SLOTS = [(8 + c * 18, 84 + r * 18) for r in range(3) for c in range(9)]
HOTBAR_SLOTS = [(8 + c * 18, 142) for c in range(9)]

# Where the player model is drawn, from InventoryScreen. Its well stops one
# pixel short of the offhand slot at x=77 rather than running under it — the
# first version overlapped by a column, which reads as a slot with a bite out
# of it.
PREVIEW = (26, 8, 75, 78)


def inventory():
    """The survival inventory, 176x166 inside a 256x256 sheet."""
    img = Image.new("RGBA", (256, 256), CLEAR)
    panel(img, 0, 0, 176, 166)

    # The preview well. The player stands in a recess, so the eye reads the
    # figure as being inside the panel rather than printed on it.
    rect(img, PREVIEW[0] - 1, PREVIEW[1] - 1, PREVIEW[2] + 1, PREVIEW[3] + 1,
         SURFACE_SUNKEN)
    outline(img, PREVIEW[0] - 1, PREVIEW[1] - 1, PREVIEW[2] + 1, PREVIEW[3] + 1,
            LINE_SOFT)

    # A rule between the crafting half and the inventory half, which is the one
    # grouping the screen actually has.
    rect(img, 7, 80, 169, 81, LINE_SOFT)
    # ...and one above the hotbar, which is the other.
    rect(img, 7, 139, 169, 140, LINE_SOFT)

    for x, y in (ARMOUR_SLOTS + [OFFHAND_SLOT] + CRAFTING_SLOTS
                 + INVENTORY_SLOTS + HOTBAR_SLOTS):
        slot(img, x - 1, y - 1)

    # The result slot is the one output on the screen, so it is bigger and it
    # is marked: a brighter edge, the way the interface marks anything it is
    # about to give you. Centred on the slot rather than hung off its top-left
    # corner — at 26 pixels the difference is five, and the first version ran
    # three pixels off the right edge of the panel.
    rx, ry = RESULT_SLOT
    big = 26
    ox, oy = rx + 8 - big // 2, ry + 8 - big // 2
    slot(img, ox, oy, big)
    outline(img, ox, oy, ox + big, oy + big, ACCENT_DIM)

    return img


def attack_indicator_background():
    """The empty track behind the swing timer."""
    img = Image.new("RGBA", (16, 4), CLEAR)
    rect(img, 0, 0, 16, 4, SURFACE_SUNKEN)
    outline(img, 0, 0, 16, 4, LINE)
    return img


def attack_indicator_progress():
    """
    The fill. Vanilla crops this from the left as the swing recharges, so it
    has to read at every width — which is why it is flat with a lit cap rather
    than a gradient that only looks right when it is full.
    """
    img = Image.new("RGBA", (16, 4), CLEAR)
    rect(img, 0, 1, 16, 3, ACCENT)
    rect(img, 0, 1, 16, 2, ACCENT_DIM)
    rect(img, 15, 1, 16, 3, colour("ACCENT_BRIGHT"))
    return img


def crosshair_background():
    img = Image.new("RGBA", (16, 4), CLEAR)
    rect(img, 0, 0, 16, 4, SURFACE_SUNKEN)
    outline(img, 0, 0, 16, 4, LINE_SOFT)
    return img


def crosshair_full():
    """Shown for one frame when the swing is charged. The only bright one."""
    img = Image.new("RGBA", (16, 4), CLEAR)
    rect(img, 0, 0, 16, 4, colour("ACCENT_BRIGHT"))
    return img


SHEETS = {
    "container/inventory.png": inventory,
    "sprites/hud/hotbar_attack_indicator_background.png": attack_indicator_background,
    "sprites/hud/hotbar_attack_indicator_progress.png": attack_indicator_progress,
    "sprites/hud/crosshair_attack_indicator_background.png": crosshair_background,
    "sprites/hud/crosshair_attack_indicator_progress.png": attack_indicator_progress,
    "sprites/hud/crosshair_attack_indicator_full.png": crosshair_full,
}


def main():
    for relative, build in SHEETS.items():
        path = RES / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        build().save(path)
    print("gui textures      : %d re-skinned (inventory, attack indicator)"
          % len(SHEETS))


if __name__ == "__main__":
    main()
