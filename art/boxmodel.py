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


from PIL import Image, ImageDraw


def pack(boxes, sheet=64):
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


def draw_box(draw, box, uv, base, accent, sheet=64):
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


def write_texture(path, boxes, base, accent, sheet=64):
    img = Image.new("RGBA", (sheet, sheet), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    placed = pack(boxes, sheet)
    for box in boxes:
        draw_box(draw, box, placed[(box[4], box[5], box[6])], base, accent, sheet)
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def parent_of(box):
    """The part a box hangs from, or None for one attached to the root."""
    return box[10] if len(box) > 10 else None


def java_name(name):
    """part_name -> partName, for the local PartDefinition variable."""
    head, *rest = name.split("_")
    return head + "".join(word.capitalize() for word in rest)


def part_lines(boxes, sheet=64):
    """
    The body of createBodyLayer, parents before their children.

    A box that something hangs from is emitted into a local variable so the
    child can be added to it. A box that nothing hangs from is emitted inline,
    which keeps the common case reading exactly as it did before parents
    existed.
    """
    placed = pack(boxes, sheet)
    parents = {parent_of(box) for box in boxes if parent_of(box)}
    lines = []

    def emit(box, on):
        name, ox, oy, oz, sx, sy, sz, px, py, pz = box[:10]
        u, v = placed[(sx, sy, sz)]
        target = "%s.addOrReplaceChild" % on
        assign = ""
        if name in parents:
            assign = "PartDefinition %s = " % java_name(name)
        lines.append(
            '        %s%s("%s",\n'
            '                CubeListBuilder.create().texOffs(%d, %d)\n'
            '                        .addBox(%.1fF, %.1fF, %.1fF, %d, %d, %d),\n'
            '                PartPose.offset(%.1fF, %.1fF, %.1fF));'
            % (assign, target, name, u, v, ox, oy, oz, sx, sy, sz, px, py, pz))

    for box in boxes:
        if parent_of(box) is None:
            emit(box, "root")
    for box in boxes:
        parent = parent_of(box)
        if parent is not None:
            emit(box, java_name(parent))
    return "\n".join(lines)


def smallest_sheet(boxes, options=(64, 96, 128, 256)):
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
            placed = pack(boxes, sheet)
        except SystemExit:
            continue
        fits = True
        for box in boxes:
            _n, _ox, _oy, _oz, sx, sy, sz, _px, _py, _pz = box[:10]
            u, v = placed[(sx, sy, sz)]
            if u + 2 * sz + 2 * sx > sheet or v + sz + sy > sheet:
                fits = False
                break
        if fits:
            return sheet
    raise SystemExit("these boxes do not fit on any sheet up to %d" % options[-1])
