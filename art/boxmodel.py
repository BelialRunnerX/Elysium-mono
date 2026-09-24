"""
Box models: UV packing, texture unwrapping and the Java a MeshDefinition needs.

Shared by every mod in this project that builds an entity out of boxes -
elysium-mobs' six families and elysium-npcs' court. It was gen_mobs.py's alone
until there was a second caller, at which point copying it would have meant two
UV packers that had to agree about Minecraft's cross layout forever.

The layout is fixed by the game, not by us. Getting it wrong puts a mob's legs
on top of its body, which compiles, loads, and looks like nonsense - which is
the entire class of bug this code exists to prevent, and the reason it is
written once.
"""


def hex_to_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def shade(colour, factor):
    r, g, b = hex_to_rgb(colour)
    return tuple(max(0, min(255, int(c * factor))) for c in (r, g, b))


import math

import numpy as np
from PIL import Image


class Skin:
    """
    What each part is made of, and which parts carry the accent.

    <h2>Why materials exist at all</h2>

    The first answer to "every mob renders black" was to raise the palette, and
    it was only half the problem. Measured through the previewer, against
    vanilla run through the same code:

        cow      mean  64.9   p5  20.6   p95 172.0   std 48.6
        golem    mean 145.4   p5  88.2   p95 180.9   std 32.0
        ours     mean  64-76  p5  55-65  p95  73-105 std  7.6-13.5

    The *mean* was already vanilla's. The **range** was not. A cow is not a
    mid-brown animal: it is near-black patches against near-white ones, and the
    eye reads the contrast, not the average. Ours was one flat value over the
    whole body - six face multipliers within 1.08..0.86 of a single colour -
    so at eight metres there was nothing to see but an outline, which is
    exactly what "they are all black" describes. Raising a flat value only
    makes a lighter flat blob.

    So a part is now made of something. A `Skin` carries a palette keyed by
    material - the dark under-body, the mid shell, the light armour plate, the
    pale claw - and a map from part name to which one it is. The silhouette
    keeps its dark masses and the plates that catch the light are genuinely
    light, which is how the reference images read and how vanilla reads.

    A plain set of names is still accepted everywhere a Skin is, and means what
    it always meant: these parts are accented, everything is one colour. That
    is what elysium-npcs still passes.
    """

    def __init__(self, palette, materials=None, accent_parts=None, grains=None):
        self.palette = palette
        self.materials = materials or {}
        self.accent_parts = accent_parts
        # material key -> a profile name in art/grain.py. Absent means flat,
        # which is what a caller that has not thought about surface gets.
        self.grains = grains or {}

    def material(self, name):
        return self.materials.get(name, "base")

    def colour(self, name):
        return self.palette[self.material(name)]

    def accented(self, name):
        return self.accent_parts is None or name in self.accent_parts

    def grain(self, name):
        return self.grains.get(self.material(name))

    def fields(self, sheet, seed=0):
        """
        One grain field per profile this skin uses, at sheet resolution.

        Built once per texture rather than once per box: a field is indexed by
        its position on the sheet, so two parts that share a patch share their
        grain - which is the same thing that makes a left and a right arm match,
        and is what vanilla does with a mirrored limb.
        """
        import grain as grain_module
        return {profile: grain_module.field(profile, sheet, seed)
                for profile in set(self.grains.values()) if profile}


def key_of(box, skin=None):
    """
    The identity of a box's texture patch.

    Size, whether the box is accented, *and* what it is made of. Size alone was
    correct while every box carried the accent band and every box was the same
    colour, and became silently wrong the moment either stopped being true: two
    boxes of the same size shared one patch, draw_box painted each in turn, and
    whichever was drawn last decided what the patch looked like. With a
    hundred-plus parts sharing forty sizes that meant the accents vanished from
    the entire bestiary - every mob rendered featureless black, with nothing in
    any log to say why. Materials would have done the same thing again, one
    layer down, so the material is in the key from the start.
    """
    if is_pivot(box):
        return None
    if isinstance(skin, Skin):
        return (box[4], box[5], box[6], skin.accented(box[0]),
                skin.material(box[0]))
    accented = skin is None or box[0] in skin
    return (box[4], box[5], box[6], accented)


def is_pivot(box):
    """
    A part with no cube in it: a joint to hang other parts from.

    Minecraft is perfectly happy with `addOrReplaceChild(name,
    CubeListBuilder.create(), pose)` - a ModelPart that draws nothing and
    exists only so its children inherit its position and rotation. That is
    worth having, because the alternative is what the court used to do: keep
    the vanilla 8x8x8 head and 8x12x4 body as the anchors everything else
    hangs off, and then build the real anatomy *on top of two enormous
    cubes* that are still there underneath, still square, still in the
    silhouette. A pivot is how the cube leaves and the anchor stays.
    """
    return box[4] == 0 and box[5] == 0 and box[6] == 0


def pack(boxes, sheet=64, skin=None):
    """
    Assign every distinct box footprint a place on the sheet.

    A box unwraps into a cross that is (2*depth + 2*width) across and
    (depth + height) tall. Mirrored parts - two arms, two legs - have identical
    footprints and deliberately share one patch, which is what vanilla does and
    what keeps a humanoid inside 64x64.

    Shelf packing: fill a row left to right, drop to a new row when it will not
    fit. Not optimal, and optimal is not needed - what is needed is that two
    boxes never land on the same pixels, which any correct packer guarantees
    and hand-assignment does not.
    """
    placed = {}
    shelves = []          # (y, height, cursor x)
    for box in boxes:
        _name, _ox, _oy, _oz, sx, sy, sz, _px, _py, _pz = box[:10]
        key = key_of(box, skin)
        if key is None or key in placed:
            continue
        # Ceil'd, because a box may now be a fractional size and a texture
        # patch cannot be. The model still uses the true size; the patch is
        # simply large enough. Faces here are flat colour, so a fraction of a
        # pixel of slack at the edge is invisible.
        w = 2 * math.ceil(sz) + 2 * math.ceil(sx)
        h = math.ceil(sz) + math.ceil(sy)
        for shelf in shelves:
            if shelf[2] + w <= sheet and h <= shelf[1]:
                placed[key] = (shelf[2], shelf[0])
                shelf[2] += w
                break
        else:
            y = sum(s[1] for s in shelves)
            if y + h > sheet or w > sheet:
                raise SystemExit(
                    "cannot fit a %dx%dx%d box on a %dx%d sheet - the family is too big"
                    % (sx, sy, sz, sheet, sheet))
            shelves.append([y, h, w])
            placed[key] = (0, y)
    return placed


def paint_face(pixels, rect, colour, light, grain):
    """
    One face: the material's colour, its grain, and a darker rim.

    <h2>Why the grain is multiplied and not added</h2>

    The grain arrives as luminance offsets, because that is how it was measured
    off vanilla's tiles. Adding those offsets to each channel would drag every
    colour towards grey as it varies - a dark green plate would come out
    green, grey, green, grey - so the offset is turned into a *ratio* against
    the colour's own luminance and all three channels are scaled by it. Hue and
    saturation survive; only value moves, which is what a surface does.

    <h2>Why the amplitude is capped</h2>

    Vanilla's own tiles carry a relative amplitude that falls as they get
    brighter: blackstone is std 15.8 on a mean of 37 (43%), deepslate 21.2 on
    80 (26%), iron_block 18.0 on 220 (8%). A fixed absolute std would therefore
    crush a dark part to black and leave a bright one flat, so it is capped at
    a share of the colour's own luminance - which is the same relationship,
    read off the same tiles.
    """
    x, y, width, height = rect
    if width <= 0 or height <= 0:
        return
    red, green, blue = shade(colour, light)
    lum = 0.2126 * red + 0.7152 * green + 0.0722 * blue

    patch = np.zeros((height, width, 3), dtype=np.float64)
    patch[:, :, 0], patch[:, :, 1], patch[:, :, 2] = red, green, blue
    if grain is not None and lum > 1.0:
        offsets = grain[y:y + height, x:x + width]
        amplitude = min(1.0, (0.34 * lum) / (grain.std() + 1e-9))
        patch *= (1.0 + offsets[:, :, None] * amplitude / lum)

    # A darker rim, and only on a face big enough to have an inside.
    #
    # This used to be unconditional, and it was a crutch for flat colour: with
    # nothing on a face to look at, an outline was the only thing telling one
    # box from the next. It does not survive contact with a grain. The organic
    # anatomy is built out of parts three to six pixels across, where a rim on
    # all four sides is nine of sixteen pixels - so every part came out as a
    # dark quilted square with a few lit texels trapped in the middle, and the
    # surface this whole change exists to add was invisible underneath it.
    #
    # Six pixels is where the inside becomes the majority. Below that the edge
    # reads anyway, from the face shading and from the engine's own lighting,
    # which is how vanilla does it - vanilla outlines nothing.
    #
    # Through a mask, not by darkening each of the four edges in turn: that
    # squares the multiplier at the corners, which PIL's `outline` never did.
    # It cost nine luminance off every sheet, most of a round of brightening.
    if min(width, height) >= 6:
        rim = np.ones((height, width), dtype=np.float64)
        rim[0, :] = rim[-1, :] = rim[:, 0] = rim[:, -1] = 0.82
        patch *= rim[:, :, None]

    pixels[y:y + height, x:x + width, :3] = np.clip(patch, 0, 255)
    pixels[y:y + height, x:x + width, 3] = 255


def draw_box(pixels, box, uv, base, accent, sheet=64, accented=True, grain=None):
    """
    Lay one box's six faces out at its UV, in Minecraft's cross layout.

    The layout is fixed by the game, not by us: at (u, v), the top row holds
    the top and bottom faces starting at u+depth, and the second row holds
    right, front, left, back in that order. Getting this wrong is the entire
    class of bug this generator exists to prevent, so it is written once.

    `pixels` is an (n, n, 4) uint8-valued array rather than a PIL draw handle.
    It was a draw handle while every face was one flat rectangle; a face with a
    grain in it is a per-pixel operation, and going through PIL to do that
    means a putpixel call per texel.

    `grain` is a full-sheet field of luminance offsets from art/grain.py, or
    None for the flat fill this used to do.
    """
    _name, _ox, _oy, _oz, sx, sy, sz, _px, _py, _pz = box[:10]
    u, v = uv
    # Ceil'd to match pack(): the patch is integer pixels even when the box is
    # not. See the note in pack() about why the slack is invisible.
    sx, sy, sz = math.ceil(sx), math.ceil(sy), math.ceil(sz)

    # Face values are deliberately close together now.
    #
    # Minecraft lights an entity from its vertex normals - that is why a
    # vanilla cow's back is brighter than its flank with a texture that barely
    # varies. Baking a full lighting model into the texture as well multiplies
    # the two, and on a palette that was already dark it produced mobs that
    # rendered as flat silhouettes.
    #
    # Measured rather than guessed: vanilla's cow.png runs 73 to 97 across the
    # faces of one box, a spread of about 1.3. This was 1.15 to 0.70, a spread
    # of 1.6, on top of whatever the engine then did. What is left is enough to
    # keep an edge readable and no more.
    faces = [
        (u + sz, v, sx, sz, 1.08),                    # top
        (u + sz + sx, v, sx, sz, 0.86),               # bottom
        (u, v + sz, sz, sy, 0.95),                    # right
        (u + sz, v + sz, sx, sy, 1.00),               # front
        (u + sz + sx, v + sz, sz, sy, 0.95),          # left
        (u + sz + sx + sz, v + sz, sx, sy, 0.92),     # back
    ]
    for fx, fy, fw, fh, light in faces:
        if fx + fw > sheet or fy + fh > sheet:
            raise SystemExit(
                "box %r at uv (%d, %d) does not fit on a %dx%d sheet - shrink it or move it"
                % (_name, u, v, sheet, sheet))
        paint_face(pixels, (fx, fy, fw, fh), base, light, grain)

    # The accent: a band across the front face, which is the face a player sees.
    #
    # Only on parts that ask for it. While a family was nine boxes, banding all
    # of them read as a livery; at thirty-three it reads as noise, and the
    # silhouette - the thing detail is supposed to serve - disappears under it.
    # A handful of deliberate marks says "Imperial" far better than a mark on
    # every plate.
    if not accented:
        return
    fx, fy, fw, fh = u + sz, v + sz, sx, sy
    # Thresholds of 4, which is what these were, silently skipped the mark on
    # anything smaller — and the organic anatomy is built almost entirely out of
    # parts smaller than that: visor slats, cranium rings, claw joints. The
    # whole bestiary came out featureless black, from a bounds check.
    #
    # The floor is now the smallest face that can carry anything at all: two
    # pixels, one of which is the mark.
    # The accent is laid on flat, without grain. It is a made thing - a lit
    # visor, a gold band, an eye - and the one part of a mob that is supposed
    # to look manufactured against everything around it.
    mark = hex_to_rgb(accent)
    if fh >= 4 and fw >= 3:
        band = fy + max(1, fh // 3)
        rows = max(1, fh // 6 + 1)
        pixels[band:band + rows, fx + 1:fx + fw - 1, :3] = mark
        pixels[band:band + rows, fx + 1:fx + fw - 1, 3] = 255
    elif fh >= 2 and fw >= 2:
        # Too small for a band across it; a lit line down the middle still
        # reads at inventory and at eight metres, which is where these are seen.
        pixels[fy:fy + fh, fx + fw // 2, :3] = mark
        pixels[fy:fy + fh, fx + fw // 2, 3] = 255


def write_texture(path, boxes, base, accent, sheet=64, skin=None):
    """
    `skin` is either a Skin - materials and accents both - or the older form: a
    set of part names that carry the accent band, or None for "all of them",
    which is what every caller wanted while the models were small.

    `base` is the colour of a part the skin has nothing to say about, which is
    every part when `skin` is a set or None. A Skin overrides it per part, so
    the same call site serves both.
    """
    pixels = np.zeros((sheet, sheet, 4), dtype=np.float64)
    placed = pack(boxes, sheet, skin)
    fields = skin.fields(sheet) if isinstance(skin, Skin) else {}
    for box in boxes:
        if is_pivot(box):
            continue
        if isinstance(skin, Skin):
            colour, marked = skin.colour(box[0]), skin.accented(box[0])
            grain = fields.get(skin.grain(box[0]))
        else:
            colour = base
            marked = skin is None or box[0] in skin
            grain = None
        draw_box(pixels, box, placed[key_of(box, skin)], colour, accent,
                 sheet, marked, grain)
    img = Image.fromarray(pixels.astype(np.uint8), "RGBA")
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


# ---------------------------------------------------------------------------
# The optional tail
# ---------------------------------------------------------------------------
#
# A box row is
#
#     (name, ox, oy, oz, sx, sy, sz, px, py, pz [, tail])
#
# and `tail` is either a parent part's name, as it always was, or a dict for
# anything richer:
#
#     {"parent": "body", "rot": (0.0, 0.4, 0.0)}
#
# A dict rather than three more positional slots because the table is written
# and read by hand: `("plate", ..., "body", 0, 0.4, 0)` is four unlabelled
# numbers at the end of a row that already has nine, and the first time one is
# transposed the part ends up somewhere plausible-looking and wrong. The bare
# string form still works, so none of the existing rows had to be touched to
# add rotation.


def _tail(box):
    return box[10] if len(box) > 10 else None


def parent_of(box):
    """The part a box hangs from, or None for one attached to the root."""
    tail = _tail(box)
    if isinstance(tail, dict):
        return tail.get("parent")
    return tail


def rotation_of(box):
    """
    (xRot, yRot, zRot) in radians, or None for an axis-aligned box.

    This is the whole of what "less blocky" means inside Minecraft's entity
    system. There is no curved geometry available - every mob in every mod,
    dragons included, is boxes - so the only way to get an edge that is not on
    the grid is to turn the box. Chamfers, tapers and angled plating are all
    this one feature.
    """
    tail = _tail(box)
    if isinstance(tail, dict) and tail.get("rot"):
        return tuple(float(v) for v in tail["rot"])
    return None


def java_name(name):
    """part_name -> partName, for the local PartDefinition variable."""
    head, *rest = name.split("_")
    return head + "".join(word.capitalize() for word in rest)


def part_lines(boxes, sheet=64, skin=None):
    """
    The body of createBodyLayer, parents before their children.

    A box that something hangs from is emitted into a local variable so the
    child can be added to it. A box that nothing hangs from is emitted inline,
    which keeps the common case reading exactly as it did before parents
    existed.
    """
    placed = pack(boxes, sheet, skin)
    parents = {parent_of(box) for box in boxes if parent_of(box)}
    lines = []

    def emit(box, on):
        name, ox, oy, oz, sx, sy, sz, px, py, pz = box[:10]
        target = "%s.addOrReplaceChild" % on
        assign = ""
        if name in parents:
            assign = "PartDefinition %s = " % java_name(name)
        rot = rotation_of(box)
        if rot is None:
            pose = 'PartPose.offset(%.2fF, %.2fF, %.2fF)' % (px, py, pz)
        else:
            pose = ('PartPose.offsetAndRotation(%.2fF, %.2fF, %.2fF, '
                    '%.4fF, %.4fF, %.4fF)' % (px, py, pz, rot[0], rot[1], rot[2]))
        if is_pivot(box):
            lines.append(
                '        %s%s("%s", CubeListBuilder.create(), %s);'
                % (assign, target, name, pose))
            return
        u, v = placed[key_of(box, skin)]
        lines.append(
            '        %s%s("%s",\n'
            '                CubeListBuilder.create().texOffs(%d, %d)\n'
            '                        .addBox(%.2fF, %.2fF, %.2fF, %.2fF, %.2fF, %.2fF),\n'
            '                %s);'
            % (assign, target, name, u, v, ox, oy, oz, sx, sy, sz, pose))

    # Emitted parents-before-children, by walking the tree rather than by
    # trusting the table's order.
    #
    # Two passes - roots, then everything else in list order - was correct for
    # a tree one level deep, which is all there was while a family was nine
    # boxes. A chain of twelve links where each hangs off the last is twelve
    # levels, and the moment a row is moved the generated Java references a
    # local variable that has not been declared yet. That is a compile error
    # rather than a silent one, but it is a compile error that appears when
    # somebody reorders a table for readability, which is a bad trade.
    by_parent = {}
    for box in boxes:
        by_parent.setdefault(parent_of(box), []).append(box)

    known = set()

    def walk(parent, target):
        for box in by_parent.get(parent, []):
            emit(box, target)
            known.add(box[0])
            walk(box[0], java_name(box[0]))

    walk(None, "root")

    orphans = [b[0] for b in boxes if b[0] not in known]
    if orphans:
        raise SystemExit(
            "these boxes name a parent that does not exist: %s" % sorted(orphans))
    return "\n".join(lines)


def smallest_sheet(boxes, options=(64, 96, 128, 192, 256, 384, 512),
                   skin=None):
    """
    The smallest sheet these boxes actually fit on.

    Measured rather than chosen. A sheet size is a number that appears in two
    places - the texture and the model's LayerDefinition - and picking one by
    eye goes wrong in both directions: too small and the packer raises, too
    large and every variant of that entity carries the empty space forever.
    Thirty variant sheets at 128 where 64 would do is four times the texture
    memory for nothing.

    Checks the faces as well as the shelves, because pack() only guarantees no
    two boxes share pixels - a box placed legally can still have its unwrapped
    cross run off the right edge.
    """
    for sheet in options:
        try:
            placed = pack(boxes, sheet, skin)
        except SystemExit:
            continue
        fits = True
        for box in boxes:
            if is_pivot(box):
                continue
            _n, _ox, _oy, _oz, sx, sy, sz, _px, _py, _pz = box[:10]
            # The key is the box's true size, because that is what pack()
            # keyed on; only the *footprint* is ceil'd, because that is what
            # occupies whole pixels. Conflating the two here looked up a box
            # that had never been placed.
            u, v = placed[key_of(box, skin)]
            fw, fh = math.ceil(sx), math.ceil(sy)
            fd = math.ceil(sz)
            if u + 2 * fd + 2 * fw > sheet or v + fd + fh > sheet:
                fits = False
                break
        if fits:
            return sheet
    raise SystemExit("these boxes do not fit on any sheet up to %d" % options[-1])


# ===========================================================================
# The flat painter, kept for the court
# ===========================================================================
#
# Everything above is the system the bestiary uses: a part is made of a
# material, the material has a value and a grain, and pack() keys a patch by
# all three. It exists because the mobs measured badly against vanilla — our
# mean was a cow's and our range was a fifth of one, which is what "they all
# render black" was describing.
#
# elysium-npcs does not use it, and the reason is worth writing down rather
# than fixing by accident. The court's five envoys paint each box from a
# *pair* of colours chosen by part — a head is skin and hair, a cape is cloth
# and its lining, a crown is regalia and its accent — and a Skin carries one
# accent for the whole entity. Porting them would not be a port, it would be a
# redesign of five faces nobody asked to change.
#
# So the old painter stays, under a name that says what it is, until the court
# is given materials of its own.


def pack_flat(boxes, sheet=64):
    """
    Assign every distinct box footprint a place on the sheet.

    A box unwraps into a cross that is (2*depth + 2*width) across and
    (depth + height) tall. Mirrored parts - two arms, two legs - have identical
    footprints and deliberately share one patch, which is what vanilla does and
    what keeps a humanoid inside 64x64.

    Shelf packing: fill a row left to right, drop to a new row when it will not
    fit. Not optimal, and optimal is not needed - what is needed is that two
    boxes never land on the same pixels, which any correct packer guarantees
    and hand-assignment does not.
    """
    placed = {}
    shelves = []          # (y, height, cursor x)
    for box in boxes:
        _name, _ox, _oy, _oz, sx, sy, sz, _px, _py, _pz = box[:10]
        key = (sx, sy, sz)
        if key in placed:
            continue
        w = 2 * sz + 2 * sx
        h = sz + sy
        for shelf in shelves:
            if shelf[2] + w <= sheet and h <= shelf[1]:
                placed[key] = (shelf[2], shelf[0])
                shelf[2] += w
                break
        else:
            y = sum(s[1] for s in shelves)
            if y + h > sheet or w > sheet:
                raise SystemExit(
                    "cannot fit a %dx%dx%d box on a %dx%d sheet - the family is too big"
                    % (sx, sy, sz, sheet, sheet))
            shelves.append([y, h, w])
            placed[key] = (0, y)
    return placed


def draw_box_flat(draw, box, uv, base, accent, sheet=64):
    """
    Lay one box's six faces out at its UV, in Minecraft's cross layout.

    The layout is fixed by the game, not by us: at (u, v), the top row holds
    the top and bottom faces starting at u+depth, and the second row holds
    right, front, left, back in that order. Getting this wrong is the entire
    class of bug this generator exists to prevent, so it is written once.
    """
    _name, _ox, _oy, _oz, sx, sy, sz, _px, _py, _pz = box[:10]
    u, v = uv

    faces = [
        (u + sz, v, sx, sz, 1.15),                    # top
        (u + sz + sx, v, sx, sz, 0.70),               # bottom
        (u, v + sz, sz, sy, 0.85),                    # right
        (u + sz, v + sz, sx, sy, 1.00),               # front
        (u + sz + sx, v + sz, sz, sy, 0.85),          # left
        (u + sz + sx + sz, v + sz, sx, sy, 0.78),     # back
    ]
    for fx, fy, fw, fh, light in faces:
        if fx + fw > sheet or fy + fh > sheet:
            raise SystemExit(
                "box %r at uv (%d, %d) does not fit on a %dx%d sheet - shrink it or move it"
                % (_name, u, v, sheet, sheet))
        draw.rectangle([fx, fy, fx + fw - 1, fy + fh - 1], fill=shade(base, light))
        # A one-pixel darker rim, so the edges of a box read at distance.
        draw.rectangle([fx, fy, fx + fw - 1, fy + fh - 1], outline=shade(base, light * 0.55))

    # The accent: a band across the front face, which is the face a player sees.
    fx, fy, fw, fh = u + sz, v + sz, sx, sy
    if fh >= 4 and fw >= 4:
        band = fy + fh // 3
        draw.rectangle([fx + 1, band, fx + fw - 2, band + max(0, fh // 6)],
                       fill=hex_to_rgb(accent))
    elif fh >= 4:
        # Too narrow for a band; a single lit pixel column still reads.
        draw.line([(fx + fw // 2, fy + 1), (fx + fw // 2, fy + fh - 2)],
                  fill=hex_to_rgb(accent))
