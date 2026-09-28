"""
Body plans for everything that is not one of the eight.

The eight Elysium mobs keep their models exactly as they are (docs/POLISH.md
P10, Dylan, 27 September 2026). Every other creature is patterned after their
complexity — a hundred-plus boxes, a real part hierarchy, layered shells over a
darker under-body, articulated digits — but with a body plan of its own: a
quadruped, a knuckle-walking hulk, a stilt-legged insectile thing, and later a
serpent, a winged thing, a floater, a fish. None of them is a hunched clawed
humanoid recoloured.

Each builder takes one row of proportions (a dict, like gen_mobs.PROPORTIONS)
and returns a Plan: the box rows in the format art/boxmodel.py packs and
emits — (name, ox, oy, oz, sx, sy, sz, px, py, pz[, parent-and-rotation]) — plus
the gait the emitter turns into setupAnim, and which root parts are which.

Coordinates are Minecraft's: y grows downward from the model origin, the model
faces negative z, and the ground is at y = 24 for a mob drawn at scale 1 (the
renderer scales the whole thing onto the entity's hitbox afterwards, so a plan
is drawn at any convenient size and measured by art/creature.py).

A chain or an ovoid grows along its own +y, which is downward. To lay one along
a back, hang it from a pivot box turned a quarter turn about x: +pi/2 sends its
+y backwards along +z, -pi/2 forwards along -z. That is how every torso here is
laid down rather than stood up.
"""
import math

import organic as org

GROUND = 24.0
BACK = math.pi / 2          # a child's +y runs backwards along +z
FORWARD = -math.pi / 2      # a child's +y runs forwards along -z


class Plan:
    """What a builder returns: rows, the gait, and which roots move."""

    def __init__(self, name, rows, gait, roots):
        self.name = name
        self.rows = rows
        self.gait = gait
        self.roots = roots

    def __len__(self):
        return len(self.rows)


def _root(name, size, pivot, rot=None):
    """A pivot box: a small cube the rest of a limb hangs from."""
    sx, sy, sz = size
    row = (name, -sx / 2.0, 0.0, -sz / 2.0, sx, sy, sz, pivot[0], pivot[1], pivot[2])
    if rot is not None:
        row = row + (org._tail(None, rot),)
    return row


def _axis(name, parent, pivot, rot):
    """An invisible-sized pivot that turns its children: a spine to lay a torso along."""
    return (name, -0.25, 0.0, -0.25, 0.5, 0.5, 0.5, pivot[0], pivot[1], pivot[2],
            org._tail(parent, rot))


def _head(rows, p, pivot, mandibles=False):
    """
    A swept cranium with a muzzle, a jaw, a lit visor and a crest, hung off the
    "head" root; the face points forward along -z.
    """
    hw, hh, hd = p["skull_w"], p["skull_h"], p["skull_d"]
    rows.append(("head", -hw * 0.25, -hh * 0.40, -hw * 0.25, hw * 0.5, hh * 0.40, hw * 0.5,
                 pivot[0], pivot[1], pivot[2]))
    rows += org.ovoid("cranium", "head", 5, hh * 0.85, hw * 0.46,
                      (0, -hh * 0.85, hw * 0.05), hd, 0.42)
    # The muzzle runs forward off the face, the jaw under it, slightly open.
    rows += org.chain("muzzle", "head", 3,
                      (hw * 0.56, hh * 0.28, hw * 0.34), (0.06, 0, 0),
                      (0, -hh * 0.42, -hw * 0.40), taper=0.86, lead=(FORWARD + 0.10, 0, 0))
    rows += org.chain("jaw", "head", 2,
                      (hw * 0.44, hh * 0.24, hw * 0.28), (0.10, 0, 0),
                      (0, -hh * 0.12, -hw * 0.38), taper=0.88, lead=(FORWARD + 0.42, 0, 0))
    # The lit band across the brow: plates leaning back over the eyes.
    rows += org.plates("visor", "head", 3,
                       (hw * 0.72, hh * 0.16, hw * 0.22),
                       (0, -hh * 0.72, -hw * 0.48),
                       spacing=hh * 0.14, lean=-0.14, shrink=0.86)
    # A crest sweeping up and back off the skull.
    rows += org.chain("crest", "head", 4,
                      (hw * 0.30, hh * 0.24, hw * 0.24), (p["skull_sweep"], 0, 0),
                      (0, -hh * 0.80, hw * 0.10), taper=0.82, lead=(2.05, 0, 0))
    for side, sign in (("left", 1), ("right", -1)):
        if mandibles:
            rows += org.chain("mandible_%s" % side, "head", 3,
                              (hw * 0.16, hh * 0.32, hw * 0.16), (0.24, 0, -sign * 0.10),
                              (sign * hw * 0.24, -hh * 0.10, -hw * 0.42), taper=0.82,
                              lead=(FORWARD + 0.35, 0, sign * 0.30))
        else:
            # Horns: a short curling pair off the temples, up and out.
            rows += org.chain("horn_%s" % side, "head", 3,
                              (hw * 0.16, hh * 0.28, hw * 0.16), (0.20, 0, sign * 0.08),
                              (sign * hw * 0.36, -hh * 0.72, 0), taper=0.80,
                              lead=(2.6, 0, sign * 0.7))


def _digitigrade_leg(rows, name, pivot, sign, length, width, hip_pitch, knee, ankle, toes=3):
    """A leg in three chains and toes, hung from a pivot box called `name`."""
    rows.append(_root(name, (width, width, width), pivot))
    rows += org.chain("%s_thigh" % name, name, 3,
                      (width * 0.92, length * 0.17, width * 0.92),
                      (0.02, 0, 0), (0, width * 0.4, 0), taper=0.94, lead=(hip_pitch, 0, 0))
    rows += org.chain("%s_shin" % name, "%s_thigh_2" % name, 3,
                      (width * 0.74, length * 0.17, width * 0.74),
                      (0.02, 0, 0), (0, length * 0.17, 0), taper=0.93, lead=(knee, 0, 0))
    rows += org.chain("%s_meta" % name, "%s_shin_2" % name, 2,
                      (width * 0.52, length * 0.10, width * 0.52),
                      (0, 0, 0), (0, length * 0.17, 0), taper=0.92, lead=(ankle, 0, 0))
    rows += org.digits("%s_toe" % name, "%s_meta_1" % name, toes, 2,
                       (width * 0.26, width * 0.62, width * 0.26), width * 0.40,
                       (0, length * 0.10, 0), curl=0.16, sign=sign, fan=0.12)


def quadruped(p):
    """
    Low and long, leading with the head: the prowler, the grazer, the hound.

    A torso laid along a spine of two masses — a deep chest and a narrower
    haunch — on four digitigrade legs, a ridge of plates from the shoulders to
    the tail, a neck of rings carrying the head forward and a tail that curls.
    """
    rows = []
    seed = p["seed"]
    bl, bh, bw = p["body_len"], p["body_h"], p["body_w"]
    shoulder_y = GROUND - p["leg_len"] - bh * 0.45
    rows.append(("body", -bw * 0.25, -bh * 0.25, -bw * 0.25, bw * 0.5, bh * 0.5, bw * 0.5,
                 0, shoulder_y, 0))
    # The spine: everything of the torso hangs from it and runs backwards.
    rows.append(_axis("spine", "body", (0, 0, -bl * 0.18), (BACK, 0, 0)))
    rows += org.ovoid("chest", "spine", 5, bl * 0.42, bw * 0.55, (0, 0, 0), bh / bw * 0.95, 0.35)
    rows += org.ovoid("belly", "spine", 3, bl * 0.30, bw * 0.46, (0, bl * 0.40, bh * 0.08),
                      bh / bw * 0.80, 0.30)
    rows += org.ovoid("haunch", "spine", 4, bl * 0.34, bw * 0.50, (0, bl * 0.66, 0), bh / bw * 0.92, 0.40)
    # Shells down the back: thin plates lying on the spine, one after another.
    rows += org.plates("scute", "spine", p["plates"],
                       (bw * 0.60, bl * 0.11, bh * 0.16),
                       (0, bl * 0.02, -bh * 0.48), spacing=bl * 0.14, lean=0.06, shrink=0.93)
    # The neck: rings from the front of the chest up and forward to the head.
    rows += org.chain("neck", "body", 3,
                      (bw * 0.42, bh * 0.20, bw * 0.42), (0.10, 0, 0),
                      (0, -bh * 0.20, -bl * 0.20), taper=0.92, lead=(-2.15, 0, 0))
    head_pivot = (0, shoulder_y - bh * 0.62, -bl * 0.20 - bh * 0.45)
    _head(rows, p, head_pivot)
    # The tail: off the haunch, raised, then curling down and back.
    rows += org.chain("tail", "body", p["tail_links"],
                      (bw * 0.28, bl * 0.11, bw * 0.28), (0.14, 0, 0),
                      (0, -bh * 0.18, bl * 0.64), taper=0.86, lead=(2.35, 0, 0))
    for side, sign in (("left", 1), ("right", -1)):
        hip_x = sign * bw * 0.34
        _digitigrade_leg(rows, "leg_front_%s" % side, (hip_x, shoulder_y + bh * 0.20, -bl * 0.10),
                         sign, p["leg_len"], p["leg_w"], 0.16, -0.28, 0.20)
        _digitigrade_leg(rows, "leg_back_%s" % side, (hip_x, shoulder_y + bh * 0.20, bl * 0.42),
                         sign, p["leg_len"] * 1.05, p["leg_w"] * 1.1, -0.24, 0.55, -0.42)
    rows = org.asymmetry(rows, seed, p.get("wobble", 0.03))
    gait = dict(kind="quadruped", rate=p.get("rate", 1.0), stride=p.get("stride", 0.9),
                bob=p.get("bob", 0.6), roll=0.04, tail=0.25,
                legs=(("leg_front_left", 0.0), ("leg_back_right", 0.0),
                      ("leg_front_right", math.pi), ("leg_back_left", math.pi)),
                head_pitch=p.get("head_pitch", 0.0))
    return Plan("quadruped", rows, gait,
                ["head", "body", "leg_front_left", "leg_front_right",
                 "leg_back_left", "leg_back_right"])


def hulk(p):
    """
    A door with arms: the brute.

    Most of the mass is a shoulder girdle and a barrel chest leaning forward;
    the head is small and set low and forward between the shoulders; the
    forelimbs are enormous and reach the ground, so it knuckle-walks on them;
    the hind legs are short and thick. Shells ridge the shoulders and the back.
    """
    rows = []
    seed = p["seed"]
    bw, bh = p["chest_w"], p["chest_h"]
    lean = p.get("lean", 0.40)
    top_y = GROUND - p["leg_len"] - bh * 0.95
    rows.append(_root("body", (bw * 0.4, bh * 0.3, bw * 0.3), (0, top_y, 0), (lean, 0, 0)))
    rows += org.ovoid("girdle", "body", 4, bh * 0.40, bw * 0.64, (0, -bh * 0.06, 0), 0.72, 0.30)
    rows += org.ovoid("barrel", "body", 5, bh * 0.55, bw * 0.52, (0, bh * 0.30, 0), 0.82, 0.42)
    rows += org.ovoid("rump", "body", 3, bh * 0.28, bw * 0.40, (0, bh * 0.76, bw * 0.04), 0.85, 0.45)
    rows += org.plates("carapace", "body", p["plates"],
                       (bw * 0.78, bh * 0.13, bw * 0.16),
                       (0, -bh * 0.02, bw * 0.30), spacing=bh * 0.12, lean=0.16, shrink=0.90)
    rows += org.plates("gorget", "body", 3,
                       (bw * 0.56, bh * 0.10, bw * 0.12),
                       (0, bh * 0.08, -bw * 0.32), spacing=bh * 0.10, lean=-0.12, shrink=0.88)
    # The head sits low and forward of the girdle, on a short thick neck.
    rows += org.chain("neck", "body", 2, (bw * 0.30, bh * 0.14, bw * 0.30), (0.05, 0, 0),
                      (0, bh * 0.02, -bw * 0.34), taper=0.9, lead=(FORWARD - 0.30, 0, 0))
    head_pivot = (0, top_y + bh * 0.02 - math.sin(lean) * bw * 0.34 * 0 + bh * 0.10, -bw * 0.34 - bh * 0.30)
    _head(rows, p, head_pivot)
    for side, sign in (("left", 1), ("right", -1)):
        aw = p["arm_w"]
        shoulder = (sign * bw * 0.48, top_y + bh * 0.06, -bw * 0.02)
        rows.append(_root("arm_%s" % side, (aw, aw, aw), shoulder))
        rows += org.ovoid("pauldron_%s" % side, "arm_%s" % side, 3,
                          aw * 1.3, aw * 0.85, (0, -aw * 0.5, 0), 0.95, 0.35)
        rows += org.chain("upper_%s" % side, "arm_%s" % side, 3,
                          (aw * 0.85, p["arm_len"] * 0.17, aw * 0.85), (0.02, 0, 0),
                          (0, aw * 0.5, 0), taper=0.94, lead=(-0.30, 0, sign * 0.12))
        rows += org.chain("fore_%s" % side, "upper_%s_2" % side, 4,
                          (aw * 0.72, p["arm_len"] * 0.15, aw * 0.72), (0.02, 0, 0),
                          (0, p["arm_len"] * 0.17, 0), taper=0.95, lead=(0.42, 0, -sign * 0.12))
        rows += org.digits("knuckle_%s" % side, "fore_%s_3" % side, p["fingers"], 3,
                           (aw * 0.24, aw * 0.55, aw * 0.24), aw * 0.34,
                           (0, p["arm_len"] * 0.14, 0), curl=0.60, sign=sign, fan=0.10)
        _digitigrade_leg(rows, "leg_%s" % side, (sign * bw * 0.24, top_y + bh * 0.95, bw * 0.10),
                         sign, p["leg_len"], p["leg_w"], 0.12, -0.42, 0.36)
    rows = org.asymmetry(rows, seed, p.get("wobble", 0.03))
    gait = dict(kind="hulk", rate=p.get("rate", 0.8), stride=p.get("stride", 0.6),
                bob=p.get("bob", 1.2), roll=0.06, tail=0.0,
                legs=(("leg_left", 0.0), ("arm_right", 0.0),
                      ("leg_right", math.pi), ("arm_left", math.pi)),
                head_pitch=0.25)
    return Plan("hulk", rows, gait,
                ["head", "body", "arm_left", "arm_right", "leg_left", "leg_right"])


def stilt(p):
    """
    Tall, thin, and not a person: the wight.

    A narrow segmented thorax held high on four stilt legs whose knees stand
    above the body; an abdomen of rings hanging down and back; two raptorial
    arms folded up under the head; a small head with mandibles and antennae.
    It stands taller than a player and touches the ground in four places.
    """
    rows = []
    seed = p["seed"]
    tw, th = p["thorax_w"], p["thorax_h"]
    # The legs stand the body up: a short thigh out and a little down, a long
    # shin nearly vertical, so the toes meet the ground at 0.92 of a leg.
    top_y = GROUND - p["leg_len"] * 0.92 - th * 0.10
    rows.append(("body", -tw * 0.3, 0, -tw * 0.3, tw * 0.6, th * 0.3, tw * 0.6, 0, top_y, 0))
    rows.append(_axis("spine", "body", (0, th * 0.12, -tw * 0.30), (BACK, 0, 0)))
    rows += org.ovoid("thorax", "spine", 4, tw * 1.3, tw * 0.55, (0, 0, 0), th / tw * 0.55, 0.35)
    rows += org.plates("scute", "spine", p["plates"],
                       (tw * 0.70, tw * 0.22, th * 0.12),
                       (0, tw * 0.05, -th * 0.30), spacing=tw * 0.26, lean=0.06, shrink=0.90)
    rows += org.chain("abdomen", "body", p["abdomen_links"],
                      (tw * 0.62, th * 0.16, tw * 0.62), (0.16, 0, 0),
                      (0, th * 0.30, tw * 0.90), taper=0.90, lead=(2.35, 0, 0))
    rows += org.chain("neck", "body", 2,
                      (tw * 0.30, th * 0.14, tw * 0.30), (0.10, 0, 0),
                      (0, th * 0.04, -tw * 0.50), taper=0.9, lead=(-2.0, 0, 0))
    head_pivot = (0, top_y - th * 0.20, -tw * 0.50 - th * 0.26)
    _head(rows, p, head_pivot, mandibles=True)
    for side, sign in (("left", 1), ("right", -1)):
        rows += org.chain("antenna_%s" % side, "head", 4,
                          (p["skull_w"] * 0.10, p["skull_h"] * 0.36, p["skull_w"] * 0.10),
                          (-0.20, 0, sign * 0.06),
                          (sign * p["skull_w"] * 0.22, -p["skull_h"] * 0.80, 0), taper=0.86,
                          lead=(2.5, 0, sign * 0.5))
        # Raptorial arms: folded up under the head, claws hanging.
        aw = p["arm_w"]
        rows.append(_root("arm_%s" % side, (aw, aw, aw), (sign * tw * 0.40, top_y + th * 0.10, -tw * 0.40)))
        rows += org.chain("upper_%s" % side, "arm_%s" % side, 3,
                          (aw * 0.8, p["arm_len"] * 0.16, aw * 0.8), (0.03, 0, 0),
                          (0, aw * 0.4, 0), taper=0.92, lead=(-1.5, 0, sign * 0.25))
        rows += org.chain("fore_%s" % side, "upper_%s_2" % side, 4,
                          (aw * 0.62, p["arm_len"] * 0.15, aw * 0.62), (0.04, 0, 0),
                          (0, p["arm_len"] * 0.16, 0), taper=0.93, lead=(2.5, 0, 0))
        rows += org.digits("claw_%s" % side, "fore_%s_3" % side, 3, 4,
                           (aw * 0.22, p["claw_len"], aw * 0.22), aw * 0.34,
                           (0, p["arm_len"] * 0.14, 0), curl=0.24, sign=sign, fan=0.10)
        # Four stilt legs: thighs up and out past the body, shins down and in.
        for end, z, pitch in (("front", -tw * 0.30, -0.40), ("back", tw * 0.55, 0.40)):
            name = "leg_%s_%s" % (end, side)
            lw = p["leg_w"]
            rows.append(_root(name, (lw, lw, lw), (sign * tw * 0.40, top_y + th * 0.20, z)))
            # A chain grows along +y (down); about z, a negative turn on the left
            # sends it outwards. Just past a right angle is up-and-out; the shin
            # turns back most of the way, to a third off vertical, down-and-in.
            rows += org.chain("%s_thigh" % name, name, 3,
                              (lw * 0.9, p["leg_len"] * 0.18, lw * 0.9), (0.0, 0, 0),
                              (0, 0, 0), taper=0.95, lead=(pitch, 0, -sign * 1.35))
            rows += org.chain("%s_shin" % name, "%s_thigh_2" % name, 4,
                              (lw * 0.70, p["leg_len"] * 0.21, lw * 0.70), (0.0, 0, 0),
                              (0, p["leg_len"] * 0.18, 0), taper=0.94,
                              lead=(-pitch * 1.6, 0, sign * 1.50))
            rows += org.digits("%s_toe" % name, "%s_shin_3" % name, 2, 2,
                               (lw * 0.24, lw * 0.6, lw * 0.24), lw * 0.4,
                               (0, p["leg_len"] * 0.21, 0), curl=0.30, sign=sign, fan=0.10)
    rows = org.asymmetry(rows, seed, p.get("wobble", 0.03))
    gait = dict(kind="stilt", rate=p.get("rate", 1.1), stride=p.get("stride", 0.35),
                bob=p.get("bob", 0.9), roll=0.03, tail=0.0,
                legs=(("leg_front_left", 0.0), ("leg_back_right", 0.0),
                      ("leg_front_right", math.pi), ("leg_back_left", math.pi)),
                arms=(("arm_left", 1.0), ("arm_right", -1.0)), head_pitch=0.15,
                abdomen="abdomen_0")
    return Plan("stilt", rows, gait,
                ["head", "body", "arm_left", "arm_right",
                 "leg_front_left", "leg_front_right", "leg_back_left", "leg_back_right"])


def winged(p):
    """
    A bird, or something that flies like one: the fowl, the pigeon.

    A plump body laid along a short spine, a neck of rings to a head with a
    beak, two folded wings of overlapping feather plates that flap when it
    moves, a fan of tail plates, and two thin legs with clawed toes. Simpler
    than a beast, because a bird is a simpler thing, but still a part tree.
    """
    rows = []
    seed = p["seed"]
    bl, bh, bw = p["body_len"], p["body_h"], p["body_w"]
    hip_y = GROUND - p["leg_len"]
    body_y = hip_y - bh * 0.35
    rows.append(("body", -bw * 0.25, -bh * 0.25, -bw * 0.25, bw * 0.5, bh * 0.5, bw * 0.5, 0, body_y, 0))
    rows.append(_axis("spine", "body", (0, 0, -bl * 0.35), (BACK + p.get("tilt", -0.25), 0, 0)))
    rows += org.ovoid("breast", "spine", 5, bl * 0.75, bw * 0.55, (0, 0, 0), bh / bw, 0.45)
    rows += org.plates("scute", "spine", 3, (bw * 0.5, bl * 0.16, bh * 0.14),
                       (0, bl * 0.10, -bh * 0.42), spacing=bl * 0.20, lean=0.05, shrink=0.9)
    rows += org.chain("neck", "body", 3, (bw * 0.36, bh * 0.22, bw * 0.36), (0.12, 0, 0),
                      (0, -bh * 0.15, -bl * 0.32), taper=0.92, lead=(-2.5, 0, 0))
    head_pivot = (0, body_y - bh * 0.15 - bh * 0.55, -bl * 0.32 - bh * 0.25)
    hw, hh = p["skull_w"], p["skull_h"]
    rows.append(("head", -hw * 0.25, -hh * 0.4, -hw * 0.25, hw * 0.5, hh * 0.4, hw * 0.5,
                 head_pivot[0], head_pivot[1], head_pivot[2]))
    rows += org.ovoid("cranium", "head", 4, hh * 0.85, hw * 0.48, (0, -hh * 0.85, 0), 1.1, 0.45)
    rows += org.chain("beak", "head", 3, (hw * 0.34, hh * 0.30, hw * 0.28), (0.10, 0, 0),
                      (0, -hh * 0.45, -hw * 0.40), taper=0.78, lead=(FORWARD + 0.15, 0, 0))
    rows += org.plates("visor", "head", 2, (hw * 0.60, hh * 0.14, hw * 0.20),
                       (0, -hh * 0.62, -hw * 0.44), spacing=hh * 0.13, lean=-0.12, shrink=0.85)
    rows += org.chain("crest", "head", 3, (hw * 0.22, hh * 0.22, hw * 0.20), (0.25, 0, 0),
                      (0, -hh * 0.85, hw * 0.05), taper=0.8, lead=(2.2, 0, 0))
    # The tail fan: plates off the back of the spine, splayed.
    count = p.get("tail_plates", 3)
    for i in range(count):
        spread = (i - (count - 1) / 2.0) * 0.28
        rows += org.chain("tailfeather_%d" % i, "spine", 2, (bw * 0.22, bl * 0.22, bh * 0.10),
                          (0.08, 0, 0), (0, bl * 0.72, 0), taper=0.85, lead=(0.35, spread, 0))
    for side, sign in (("left", 1), ("right", -1)):
        # Wings: a shoulder pivot beside the spine, then a folded arm of feather plates.
        wing = "wing_%s" % side
        rows.append(_root(wing, (bw * 0.2, bw * 0.2, bw * 0.2), (sign * bw * 0.45, body_y - bh * 0.05, -bl * 0.10)))
        rows += org.chain("%s_arm" % wing, wing, 3, (bw * 0.18, bl * 0.22, bh * 0.12), (0.05, 0, 0),
                          (0, 0, 0), taper=0.92, lead=(BACK - 0.15, 0, sign * 0.55))
        rows += org.plates("%s_feather" % wing, "%s_arm_2" % wing, p.get("feathers", 4),
                           (bw * 0.16, bl * 0.20, bh * 0.10), (0, bl * 0.10, 0),
                           spacing=bl * 0.06, lean=0.10, shrink=0.88)
        rows += org.plates("%s_covert" % wing, "%s_arm_0" % wing, 3,
                           (bw * 0.14, bl * 0.14, bh * 0.10), (0, bl * 0.04, -bh * 0.06),
                           spacing=bl * 0.07, lean=0.08, shrink=0.9)
        # Thin legs, knees back, clawed toes.
        leg = "leg_%s" % side
        lw = p["leg_w"]
        rows.append(_root(leg, (lw, lw, lw), (sign * bw * 0.22, hip_y, bl * 0.05)))
        rows += org.chain("%s_thigh" % leg, leg, 2, (lw * 0.9, p["leg_len"] * 0.28, lw * 0.9),
                          (0.02, 0, 0), (0, 0, 0), taper=0.92, lead=(0.35, 0, 0))
        rows += org.chain("%s_shin" % leg, "%s_thigh_1" % leg, 2, (lw * 0.7, p["leg_len"] * 0.26, lw * 0.7),
                          (0.0, 0, 0), (0, p["leg_len"] * 0.28, 0), taper=0.92, lead=(-0.55, 0, 0))
        rows += org.digits("%s_toe" % leg, "%s_shin_1" % leg, 3, 2,
                           (lw * 0.28, lw * 0.7, lw * 0.28), lw * 0.45,
                           (0, p["leg_len"] * 0.26, 0), curl=0.20, sign=sign, fan=0.14)
    rows = org.asymmetry(rows, seed, p.get("wobble", 0.03))
    gait = dict(kind="winged", rate=p.get("rate", 1.4), stride=p.get("stride", 0.8),
                bob=p.get("bob", 0.5), roll=0.03, tail=0.0,
                legs=(("leg_left", 0.0), ("leg_right", math.pi)),
                wings=(("wing_left", 1.0), ("wing_right", -1.0)), head_pitch=p.get("head_pitch", 0.1))
    return Plan("winged", rows, gait, ["head", "body", "wing_left", "wing_right", "leg_left", "leg_right"])


def fish(p):
    """
    A swimmer: a body of rings laid along a spine, a tail that wags, fins.

    Fins are plates leaning off the flanks and the back; the tail is a chain
    the gait swings side to side, the whole body following a little behind.
    """
    rows = []
    seed = p["seed"]
    bl, bh, bw = p["body_len"], p["body_h"], p["body_w"]
    body_y = GROUND - bh * 0.6
    rows.append(("body", -bw * 0.25, -bh * 0.25, -bw * 0.25, bw * 0.5, bh * 0.5, bw * 0.5, 0, body_y, 0))
    rows.append(_axis("spine", "body", (0, 0, -bl * 0.40), (BACK, 0, 0)))
    rows += org.ovoid("trunk", "spine", 6, bl * 0.70, bw * 0.55, (0, 0, 0), bh / bw, 0.40)
    rows += org.plates("dorsal", "spine", p.get("dorsal", 4), (bw * 0.16, bl * 0.14, bh * 0.36),
                       (0, bl * 0.12, -bh * 0.60), spacing=bl * 0.12, lean=0.10, shrink=0.9)
    rows.append(("head", -bw * 0.2, -bh * 0.2, -bw * 0.2, bw * 0.4, bh * 0.4, bw * 0.4, 0, body_y, -bl * 0.40))
    rows += org.ovoid("cranium", "head", 3, bh * 0.7, bw * 0.45, (0, -bh * 0.35, -bl * 0.02), 1.0, 0.45)
    rows += org.chain("jaw", "head", 2, (bw * 0.5, bh * 0.20, bw * 0.3), (0.12, 0, 0),
                      (0, bh * 0.10, -bw * 0.3), taper=0.85, lead=(FORWARD + 0.5, 0, 0))
    rows += org.plates("visor", "head", 2, (bw * 0.7, bh * 0.14, bw * 0.16),
                       (0, -bh * 0.30, -bw * 0.40), spacing=bh * 0.12, lean=-0.1, shrink=0.85)
    for side, sign in (("left", 1), ("right", -1)):
        # Pectoral fins: plates hung off the flank, turned out and down.
        rows += org.plates("pectoral_%s" % side, "spine", 2, (bw * 0.14, bl * 0.16, bh * 0.22),
                           (sign * bw * 0.50, bl * 0.20, bh * 0.10), spacing=bl * 0.08,
                           lean=0.05, shrink=0.85)
        rows[-1] = rows[-1][:10] + (org._tail("spine", (0.2, 0, -sign * 0.9)),)
        rows[-2] = rows[-2][:10] + (org._tail("spine", (0.1, 0, -sign * 0.8)),)
    rows.append(_root("tail", (bw * 0.3, bh * 0.3, bw * 0.3), (0, body_y, bl * 0.30)))
    rows += org.chain("tailseg", "tail", 3, (bw * 0.42, bl * 0.14, bh * 0.8), (0.0, 0, 0),
                      (0, 0, 0), taper=0.80, lead=(BACK, 0, 0))
    rows += org.plates("fluke", "tailseg_2", 2, (bw * 0.12, bl * 0.20, bh * 0.9), (0, bl * 0.12, 0),
                       spacing=bl * 0.04, lean=0.0, shrink=0.8)
    rows = org.asymmetry(rows, seed, p.get("wobble", 0.02))
    gait = dict(kind="fish", rate=p.get("rate", 1.6), stride=0.0, bob=0.0, roll=0.02, tail=0.0,
                wag=("tail", p.get("wag", 0.45)), head_pitch=0.0)
    return Plan("fish", rows, gait, ["head", "body", "tail"])


BUILDERS = {"quadruped": quadruped, "hulk": hulk, "stilt": stilt, "winged": winged, "fish": fish}
