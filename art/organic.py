"""
Organic forms, built out of the one primitive Minecraft entities have.

ARCHIVED — NOTHING IMPORTS THIS, AND THAT IS DELIBERATE
=======================================================

The primitives below are finished and correct. What was built *on* them — a
whole bestiary generated from proportions instead of from hand-authored boxes —
was measured against the models it would have replaced and found worse, so it
was never wired in. At 106-151 parts per family:

  * the torso read as a stack of slabs rather than as a ribcage. The chest
    ovoid, the spine chain and the rib pairs each looked plausible alone and
    did not add up to a chest;
  * the limbs read as spider legs. Six tapering segments plus articulated
    digits is anatomically right and visually wrong at the sixteen pixels of
    screen height these are actually seen at;
  * the whole figure leaned, and only some of that was ever accounted for.

Three real bugs were found by the renders that showed this — digit fan angle
conflated with digit spacing, rib spread measured from the wrong axis, and limb
roll accumulating along the chain — and all three are fixed here. The file is
kept for that, and because the next attempt should not have to rediscover any
of it.

The next attempt starts from the silhouette and works in, rather than from
anatomy in the hope that a silhouette falls out. That is a session with the
previewer open, not a parameter tweak.

There is no curved geometry in a Minecraft entity model. The renderer walks a
tree of ModelParts and draws Cubes; that is the whole vocabulary. So a spine, a
rib, a horn and a knuckle are all the same thing — a run of small boxes, each
hanging off the last, each turned a few degrees further than its parent, so the
rotations compound into an arc.

That compounding is the entire trick, and it is why these builders return
*chains* rather than flat lists of independently-posed boxes. Twelve boxes each
rotated 5 degrees relative to the previous one is a smooth 60-degree curve.
Twelve boxes each rotated 5, 10, 15 ... degrees relative to the *root* is a fan.
The first reads as a tail; the second reads as a mistake.

Why this is not just "more boxes"
---------------------------------
Adding detail in the rectilinear vocabulary — more panels, more plates, more
right angles — makes a model read as *more* machined, not less. Everything here
is aimed the other way:

  * curves rather than steps, via compounding rotation
  * radius profiles that swell and taper rather than repeat
  * deliberate asymmetry, because bilateral symmetry is what reads as
    manufactured
  * no 90-degree corner left in the silhouette

Every builder returns rows in the box format boxmodel.py already understands:

    (name, ox, oy, oz, sx, sy, sz, px, py, pz, tail)

so nothing downstream — packing, unwrapping, the Java emitter — had to learn
anything new.
"""
import math

# Box sizes are quantised to this grid.
#
# Not for tidiness: pack() gives every *distinct* size its own patch on the
# texture sheet, and a taper that multiplies by 0.86 produces a new irrational
# size at every link. Two hundred unique sizes is two hundred patches and a
# sheet nothing will fit on. Rounded to a half pixel, a chain's links collapse
# onto a handful of shared patches and the sheet stays small — while the model
# keeps sub-unit sizes, which is what the fractional addBox overload was for.
GRID = 0.5


def q(value):
    return max(GRID, round(value / GRID) * GRID)


def _tail(parent, rot):
    if rot is None or all(abs(v) < 1e-9 for v in rot):
        return parent
    return {"parent": parent, "rot": tuple(rot)}


def chain(prefix, parent, count, size, step, pivot=(0.0, 0.0, 0.0),
          taper=0.88, lead=None, twist=0.0, centred=True):
    """
    A run of boxes each hanging off the last, tracing an arc.

    `step` is the rotation *per link*, in radians, applied to every link after
    the first — so the curve is the sum of them. `taper` shrinks each link
    against the one before it, which is what turns a tube into a horn.

    `lead` is the first link's own rotation, for aiming the whole chain
    somewhere without bending it.

    Used for: spines, tails, horns, fingers, tentacles, hair, rib tips,
    anything that should look grown.
    """
    rows = []
    sx, sy, sz = size
    previous = parent
    for i in range(count):
        name = "%s_%d" % (prefix, i)
        rot = list(step)
        if i == 0 and lead is not None:
            rot = [rot[j] + lead[j] for j in range(3)]
        if twist:
            rot[1] += twist
        # Each link sits at the far end of the one before it. The first sits at
        # the pivot it was given.
        if i == 0:
            px, py, pz = pivot
        else:
            px, py, pz = 0.0, previous_sy, 0.0
        qx, qy, qz = q(sx), q(sy), q(sz)
        rows.append((name,
                     -qx / 2.0 if centred else 0.0, 0.0, -qz / 2.0 if centred else 0.0,
                     qx, qy, qz, px, py, pz,
                     _tail(previous, rot)))
        previous, previous_sy = name, qy
        sx, sy, sz = sx * taper, sy * taper, sz * taper
    return rows


def ovoid(prefix, parent, rings, height, radius, pivot=(0.0, 0.0, 0.0),
          depth_ratio=1.0, bulge=0.5):
    """
    A rounded mass: stacked slabs whose width follows a sine profile.

    `bulge` is where the widest ring sits, 0 at the top and 1 at the bottom.
    0.5 is an egg on its side; 0.35 is a skull; 0.7 is a gut.

    This is how you get something that reads as a head or a ribcage rather than
    as a cube with the corners knocked off. The rings are siblings, not a
    chain — a mass does not bend.
    """
    rows = []
    slab = height / rings
    for i in range(rings):
        # Distance from the widest point, normalised, fed through a cosine so
        # the profile is round rather than triangular.
        t = (i + 0.5) / rings
        falloff = math.cos((t - bulge) * math.pi / max(bulge, 1 - bulge) * 0.5)
        r = q(max(0.6, radius * max(0.15, falloff)))
        d = q(r * depth_ratio)
        rows.append(("%s_%d" % (prefix, i),
                     -r, 0.0, -d, r * 2, q(slab * 1.06), d * 2,
                     pivot[0], pivot[1] + i * slab, pivot[2],
                     parent))
    return rows


def ribs(prefix, parent, pairs, spacing, length, thickness,
         pivot=(0.0, 0.0, 0.0), spread=0.45, sweep=0.30, curl=0.18,
         offset=0.0):
    """
    Paired curved bars down a torso.

    Each rib is a two-link chain so it can bend backwards as it wraps, which is
    the difference between a ribcage and a ladder. `spread` sets how far out
    they sit, `sweep` how far back they lean, `curl` how much the outer half
    turns in.
    """
    rows = []
    for i in range(pairs):
        # Ribs are longest in the middle of the cage, like a real one.
        t = (i + 0.5) / pairs
        scale = 0.55 + 0.45 * math.sin(t * math.pi)
        for side, sign in (("left", 1.0), ("right", -1.0)):
            base = "%s_%s_%d" % (prefix, side, i)
            qt, ql = q(thickness), q(length * scale)
            # A rib leaves the spine going *down and out*, not straight out.
            # The first version used (pi/2 - spread), which is 65 degrees from
            # vertical - near horizontal - so the cage came out as a fin
            # sticking off one side rather than as something wrapping a chest.
            # `spread` is now the angle itself, and it is the angle from
            # vertical rather than from horizontal.
            rows.append((base,
                         -qt / 2.0, 0.0, -qt / 2.0,
                         qt, ql, qt,
                         pivot[0] + sign * offset, pivot[1] + i * spacing,
                         pivot[2],
                         _tail(parent, (sweep * (1 - t), 0.0, -sign * spread))))
            qt2, ql2 = q(thickness * 0.85), q(length * scale * 0.7)
            # The second link turns further out and forward, which is the wrap.
            rows.append(("%s_tip" % base,
                         -qt2 / 2.0, 0.0, -qt2 / 2.0,
                         qt2, ql2, qt2,
                         0.0, ql, 0.0,
                         _tail(base, (-curl * 0.8, 0.0, -sign * curl))))
    return rows


def limb(prefix, parent, segments, size, pivot=(0.0, 0.0, 0.0),
         taper=0.86, bend=0.10, sign=1.0):
    """
    A tapering limb that is not a stack of identical boxes.

    Alternating bend, so the segments articulate instead of telescoping — a
    knee that goes one way and an ankle that goes the other. `sign` mirrors it
    for the other side of the body.
    """
    rows = []
    sx, sy, sz = size
    previous = parent
    previous_sy = 0.0
    for i in range(segments):
        name = "%s_%d" % (prefix, i)
        # Alternating, and *not* accumulating. The first version added
        # 0.02 * i of roll to every segment, which is nothing at two segments
        # and twelve degrees of sideways drift at six - so the long limbs
        # splayed and the mob stood like a tripod.
        turn = bend * (1 if i % 2 == 0 else -1.4)
        qx, qy, qz = q(sx), q(sy), q(sz)
        rows.append((name, -qx / 2.0, 0.0, -qz / 2.0, qx, qy, qz,
                     pivot[0] if i == 0 else 0.0,
                     pivot[1] if i == 0 else previous_sy,
                     pivot[2] if i == 0 else 0.0,
                     _tail(previous, (turn, 0.0, 0.0))))
        previous, previous_sy = name, qy
        sx, sy, sz = sx * taper, sy * taper, sz * taper
    return rows


def digits(prefix, parent, count, joints, size, spread, pivot=(0.0, 0.0, 0.0),
           curl=0.32, sign=1.0, fan=0.16):
    """
    Fingers, claws, toes. Fanned out, each one a curling chain.

    Nothing says "grown" faster than a hand with articulated digits, and
    nothing says "machined" faster than a mitten.
    """
    rows = []
    for d in range(count):
        offset = (d - (count - 1) / 2.0) * spread
        # `offset` is a distance, in model units. Feeding it straight into a
        # rotation - which the first version did - turns a hand into a starfish:
        # four fingers at a unit and a half became four fingers at ninety
        # degrees. `fan` is the radians-per-unit that converts one to the other,
        # and it is deliberately small.
        rows += chain("%s_%d" % (prefix, d), parent, joints, size,
                      step=(curl, 0.0, 0.0),
                      lead=(-curl * 0.6, 0.0, offset * fan),
                      pivot=(pivot[0] + offset * sign, pivot[1], pivot[2]),
                      taper=0.82)
    return rows


def plates(prefix, parent, count, size, pivot=(0.0, 0.0, 0.0),
           spacing=1.6, lean=0.22, shrink=0.9):
    """
    Overlapping scales down a back or a shoulder.

    Each one leans further than the last, so the run curves over the form it
    sits on instead of lying flat along it.
    """
    rows = []
    sx, sy, sz = size
    for i in range(count):
        qx, qy, qz = q(sx), q(sy), q(sz)
        rows.append(("%s_%d" % (prefix, i),
                     -qx / 2.0, 0.0, -qz / 2.0, qx, qy, qz,
                     pivot[0], pivot[1] + i * spacing, pivot[2],
                     _tail(parent, (lean * (i + 1) * 0.35, 0.0, 0.0))))
        sx, sy, sz = sx * shrink, sy * shrink, sz * shrink
    return rows


def asymmetry(rows, seed, amount=0.06):
    """
    Nudge every rotation by a small, deterministic, per-part amount.

    A model whose left and right are exact mirrors reads as manufactured no
    matter how many boxes it has. This is the cheapest possible fix: a fixed
    pseudo-random wobble, seeded so it is identical on every run and therefore
    still a generated artifact rather than a hand edit.
    """
    out = []
    for i, row in enumerate(rows):
        tail = row[10] if len(row) > 10 else None
        # Never on a chain link. A wobble applied to every link of a nine-link
        # spine compounds exactly the way the curve does - that is the whole
        # point of the chain - so a "small" 0.05 becomes half a radian of lean
        # by the tail. Asymmetry belongs on things that hang off the body, not
        # on the body's own axis.
        if row[0].startswith(("spine_", "chest_", "pelvis_", "skull_")):
            out.append(row)
            continue
        if isinstance(tail, dict) and tail.get("rot"):
            h = (seed * 7919 + i * 104729) % 1000 / 1000.0 - 0.5
            k = (seed * 6271 + i * 92831) % 1000 / 1000.0 - 0.5
            rot = tail["rot"]
            tail = dict(tail)
            tail["rot"] = (rot[0] + h * amount, rot[1] + k * amount,
                           rot[2] + h * amount * 0.5)
            row = row[:10] + (tail,)
        out.append(row)
    return out
