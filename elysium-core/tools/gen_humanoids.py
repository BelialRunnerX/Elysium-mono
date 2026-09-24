#!/usr/bin/env python3
"""
Skins for the two humanoid combatants the Empire and the Unsworn send.

Run from the repo root:   python3 elysium-core/tools/gen_humanoids.py

<h2>Why these are painted and not packed</h2>

Every other entity in this project gets its geometry *and* its texture from one
table — see elysium-mobs/tools/gen_mobs.py, where a UV packer places each box
and the same placement is written into the model's Java. These two cannot work
that way, because their model is not ours: an Imperial Enforcer is a Zombie, so
it renders through vanilla's ZombieModel and its texture has to sit in vanilla's
humanoid layout, at vanilla's offsets, or the mob wears its own legs on its
face.

So the layout below is transcribed from HumanoidModel and is not negotiable.
What *is* ours is the painting, and that goes through the same
`draw_box_flat` the court uses, which unwraps a cube into Minecraft's cross
exactly once for the whole project.

<h2>Why they had no skins at all</h2>

They rendered as a plain zombie and a plain husk. The reasoning at the time is
still in ElysiumClient: a Zombie renderer already draws worn armour, so an
enforcer in a full Elysium set was visually distinct without the mod shipping a
model, a texture or a renderer — none of which could be checked without
launching the game.

That was true and it was not enough. A band-0 enforcer wears no armour at all,
so the thing the Empire sends after you was a zombie in a green shirt; and the
mod now has ten families of purpose-built mobs to be inconsistent with.
"""
import pathlib
import sys

from PIL import Image, ImageDraw

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(ROOT / "art"))

from boxmodel import draw_box_flat            # noqa: E402

RES = ROOT / "elysium-core/src/main/resources/assets/elysium/textures/entity"

SHEET = 64

# --- vanilla's humanoid layout, from HumanoidModel --------------------------
#
# (name, u, v, width, height, depth). The second entry of each pair is the
# outer "hat"/jacket layer, which vanilla draws one pixel proud of the first.
PARTS = [
    ("head",      0,  0, 8, 8, 8),
    ("body",     16, 16, 8, 12, 4),
    ("arm_right", 40, 16, 4, 12, 4),
    ("arm_left",  32, 48, 4, 12, 4),
    ("leg_right",  0, 16, 4, 12, 4),
    ("leg_left",  16, 48, 4, 12, 4),
]
OVERLAYS = [
    ("hat",        32,  0, 8, 8, 8),
    ("jacket",     16, 32, 8, 12, 4),
    ("sleeve_r",   40, 32, 4, 12, 4),
    ("sleeve_l",   48, 48, 4, 12, 4),
    ("trouser_r",   0, 32, 4, 12, 4),
    ("trouser_l",   0, 48, 4, 12, 4),
]

# Which parts are the shell and which are the body under it. The shell is the
# outer layer: a plate over a chest, a pauldron over an arm, a helm over a head.
SKINS = {
    # The Empire: black iron and a violet core. Its soldiers are the same
    # material as its architecture, which is the point — the Empire does not
    # raise new soldiers, it wakes the ones it already has.
    "imperial_enforcer": {
        "under": "#2a2438",
        "shell": "#4b4286",
        "accent": "#a86ef0",
        "skin": "#6b6480",
    },
    # The Unsworn: scavenged plate over dirty cloth, and a green signal light.
    # Deliberately warmer and cheaper-looking than the Empire's.
    "unsworn_raider": {
        "under": "#332c26",
        "shell": "#5c5344",
        "accent": "#33d296",
        "skin": "#8a7b66",
    },
}


def paint(palette):
    img = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    for name, u, v, w, h, d in PARTS:
        # A synthetic box row: draw_box_flat reads only the three sizes, and
        # takes the placement as an argument, which is exactly what is needed
        # when the placement is somebody else's.
        box = (name, 0, 0, 0, w, h, d, 0, 0, 0)
        base = palette["skin"] if name == "head" else palette["under"]
        draw_box_flat(draw, box, (u, v), base, palette["accent"], SHEET)

    # The outer layer carries the armour: a helm over the head, a cuirass over
    # the chest, pauldrons over the shoulders. Vanilla draws it proud of the
    # body, so this is the silhouette a player actually reads at distance.
    for name, u, v, w, h, d in OVERLAYS:
        box = (name, 0, 0, 0, w, h, d, 0, 0, 0)
        draw_box_flat(draw, box, (u, v), palette["shell"], palette["accent"], SHEET)

    # The face. Vanilla's humanoid front face is at (u + depth, v + depth) on
    # the head's cross, which is (8, 8) for a head at (0, 0) — a lit band where
    # the eyes would be, because neither of these has a face to show.
    visor = _rgb(palette["accent"])
    draw.rectangle([9, 12, 14, 13], fill=visor)
    # ...and the same mark on the helm layer above it, so it survives the outer
    # layer being drawn over the top.
    draw.rectangle([41, 12, 46, 13], fill=visor)
    return img


def _rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def main():
    RES.mkdir(parents=True, exist_ok=True)
    for name, palette in SKINS.items():
        paint(palette).save(RES / ("%s.png" % name))
    print("humanoid skins    : %d, in vanilla's humanoid layout" % len(SKINS))


if __name__ == "__main__":
    main()
