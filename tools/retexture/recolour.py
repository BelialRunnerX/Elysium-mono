"""
Turning one texture into the Elysium palette, and the rules that bound it.

<h2>Recolour, never reshape</h2>

Three things about a texture are the author's and stay untouched: its size, its
alpha, and the order of its brightnesses. A pixel that was lighter than its
neighbour is lighter than its neighbour afterwards, because that ordering *is*
the drawing — it is where the bevels, the rivets and the shading live. What
changes is only which colour each level of brightness is painted in.

That is why the mapping is a single global curve rather than anything adaptive.
A per-texture contrast stretch would make each file look better alone and make
the top and the side of the same block disagree, and a block whose faces
disagree is a block that reads as broken.

<h2>Colour carries energy, not material</h2>

The one rule from `art/style.py` that decides how this looks: a surface is
painted on its class's desaturated ramp, and anything that was saturated *and*
bright in the source — the lit core of a machine, the beam, the glowing rune —
is painted on the mod's energy ramp instead. So a Create machine still tells
you it is running, in kinetic amber, and stops telling you it is made of brass.
"""

#: How saturated a pixel has to be before it is read as energy rather than as
#: paint. Below this a mod's warm brass and cold steel are just two greys and
#: are treated as such.
#:
#: This started at 0.42 and it was wrong: oak grain sits at about 0.5, so the
#: first run painted amber lightning across every wooden panel in Create.
ENERGY_SATURATION = 0.58

#: And how bright, measured as the strongest channel rather than as luminance.
#: A dark saturated pixel is a shadow with a colour cast, not a light source,
#: and mapping it onto a glow ramp lights up every shaded corner of every
#: machine in the pack.
#:
#: Luminance is the wrong ruler for this one question, and using it was a bug:
#: Rec. 601 puts pure red at 0.30 and pure blue at 0.11, so a red warning lamp
#: and a blue laser both failed a brightness test that a dull yellow passed.
#: What is being asked here is "is this colour vivid", and the strongest
#: channel is what answers that.
ENERGY_VALUE = 0.62

#: And how *rare*. The rule in art/style.py is that a glow is "small and
#: sparse — one focal glow per sprite, at most two", and that is the half of
#: the test that no per-pixel threshold can express: a texture where a third of
#: the pixels are bright orange is not a texture with a light on it, it is a
#: texture that is orange. Above this share, the file is all paint.
ENERGY_SHARE = 0.12

#: How far a pixel's own hue is allowed to survive on a surface. Zero: the
#: ramps carry all of it. Stated as a constant rather than left implicit so
#: that a future decision to let some through is a decision somebody made.
HUE_KEPT = 0.0


def luminance(pixel):
    """Rec. 601, which is what the eye does and what the ramps were built on."""
    return (pixel[0] * 299 + pixel[1] * 587 + pixel[2] * 114) / 255000.0


def saturation(pixel):
    high = max(pixel[0], pixel[1], pixel[2])
    low = min(pixel[0], pixel[1], pixel[2])
    return 0.0 if high == 0 else (high - low) / high


def value(pixel):
    """How vivid a colour is: its strongest channel, in [0, 1]."""
    return max(pixel[0], pixel[1], pixel[2]) / 255.0


def sample(ramp, position):
    """
    A colour from a ramp, interpolated between its stops.

    Linear, so the output of this function is always a point on the ramp's own
    polyline — which is what lets the validator state, and check, that every
    colour in the pack came from the palette.
    """
    if not ramp:
        raise ValueError("an empty ramp")
    if len(ramp) == 1:
        return ramp[0]
    position = max(0.0, min(1.0, position))
    scaled = position * (len(ramp) - 1)
    low = int(scaled)
    if low >= len(ramp) - 1:
        return ramp[-1]
    share = scaled - low
    first, second = ramp[low], ramp[low + 1]
    return tuple(int(round(first[channel]
                           + (second[channel] - first[channel]) * share))
                 for channel in range(3))


def pixels_of(image):
    """
    Every pixel of an image, as a list.

    Pillow renamed this between versions and left the old name deprecated, and
    this tool runs on whichever Pillow a player's machine happens to have. Two
    lines here beat a warning per texture and a break at some future upgrade.
    """
    reader = getattr(image, "get_flattened_data", None) or image.getdata
    return list(reader())


def table(ramp, steps=256):
    """
    A ramp as two hundred and fifty-six colours, one per level of brightness.

    <h3>Why this is not simply sample(ramp, level)</h3>

    Because a ramp's stops are not evenly spaced in brightness. Every Elysium
    ramp starts with an outline that is far darker than its first shade, so
    walking the ramp at an even rate spends the bottom fifth of every texture
    between two near-blacks — which is why the first run of this tool turned
    Create's press head into a silhouette.

    So the walk is by *brightness* rather than by position: a pixel at a third
    of the way up the source's range comes out a third of the way up the ramp's
    range, whatever the stops are doing in between. The output is still a point
    on the ramp, so the palette rule is untouched, and the drawing's tonal
    structure survives, which is the whole of "recolour, never reshape".

    Built once per ramp and looked up per pixel: seven thousand textures is
    tens of millions of pixels, and this search is the difference between a
    pack that builds in a minute and one that builds in an hour.
    """
    fine = [sample(ramp, index / 1023.0) for index in range(1024)]
    levels = [luminance(colour) for colour in fine]
    low, high = levels[0], levels[-1]
    out = []
    cursor = 0
    for step in range(steps):
        target = low + (high - low) * (step / (steps - 1.0))
        while cursor + 1 < len(levels) and levels[cursor + 1] < target:
            cursor += 1
        # The nearer of the two stops either side, so the walk never overshoots
        # at the ends and never drifts in the middle.
        nearer = cursor
        if cursor + 1 < len(levels) and \
                abs(levels[cursor + 1] - target) < abs(levels[cursor] - target):
            nearer = cursor + 1
        out.append(fine[nearer])
    return out


_CHANNELS = {}


def channels(ramp):
    """
    A ramp's lookup table split into one list per channel, for Image.point.

    Kept, because there are a dozen ramps and seven thousand textures, and
    building the same table seven thousand times is the only remaining place
    this tool does work it has already done.
    """
    key = tuple(tuple(step) for step in ramp)
    found = _CHANNELS.get(key)
    if found is None:
        built = table(ramp)
        found = ([colour[0] for colour in built],
                 [colour[1] for colour in built],
                 [colour[2] for colour in built])
        _CHANNELS[key] = found
    return found


def painted(grey, ramp):
    """
    The whole image mapped onto a ramp in three lookups.

    Pillow does the per-pixel work in C. The first version of this walked the
    pixels in Python and took about a second per large texture, which is fine
    for one and is an hour for seven thousand.
    """
    from PIL import Image
    red, green, blue = channels(ramp)
    return Image.merge("RGB", (grey.point(red), grey.point(green),
                               grey.point(blue)))


def energy_mask(colour):
    """
    Which pixels are vivid enough to be energy, as a mask.

    Saturation is (high - low) / high and vividness is high, both of which are
    channel arithmetic — so both are done with image operations rather than a
    Python loop, and the comparison that turns them into a mask is done in
    integers to avoid a division per pixel.
    """
    from PIL import Image, ImageChops
    red, green, blue = colour.split()
    high = ImageChops.lighter(ImageChops.lighter(red, green), blue)
    low = ImageChops.darker(ImageChops.darker(red, green), blue)
    spread = ImageChops.subtract(high, low)
    # saturation >= S  is  (high - low) >= S * high, with no division.
    wanted = high.point(lambda level: int(level * ENERGY_SATURATION))
    bright = high.point(
        lambda level: 255 if level >= ENERGY_VALUE * 255.0 else 0)
    vivid = ImageChops.subtract(spread, wanted).point(
        lambda level: 255 if level > 0 else 0)
    return ImageChops.multiply(vivid, bright)


def recolour(image, surface, glow, always_energy=False):
    """
    The same picture on the Elysium palette.

    @param image        an RGBA image
    @param surface      the class's ramp, darkest first, as RGB triples
    @param glow         the mod's energy ramp, dimmest first
    @param always_energy whether this file is the glowing half of a pair, in
                         which case everything on it is energy however desaturated
                         the author drew it
    @return a new image of exactly the same size, with exactly the same alpha
    """
    from PIL import Image, ImageChops
    colour = image.convert("RGB")
    alpha = image.getchannel("A")
    # Pillow's own L conversion is Rec. 601, which is the curve the ramps were
    # built on, so this is the same number the tables are indexed by.
    grey = colour.convert("L")

    if always_energy:
        out = painted(grey, glow)
    else:
        mask = energy_mask(colour)
        seen = alpha.point(lambda level: 255 if level >= 32 else 0)
        counted = ImageChops.multiply(mask, seen)
        lit = counted.histogram()[255]
        opaque = seen.histogram()[255]
        if opaque and lit > opaque * ENERGY_SHARE:
            # Not a picture with a light in it: a picture that is orange.
            out = painted(grey, surface)
        else:
            out = Image.composite(painted(grey, glow), painted(grey, surface),
                                  mask)
    out = out.convert("RGBA")
    out.putalpha(alpha)
    return out
