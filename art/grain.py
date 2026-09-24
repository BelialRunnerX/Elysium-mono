"""
Material grain: the thing that stops a face being one flat colour.

<h2>Why this exists</h2>

FIXES §35 fixed two reasons the whole cast rendered black - a palette three to
four times too dark, and then a palette at vanilla's mean with a quarter of its
range. The second fix gave a part a *material*, so a pauldron is lighter than
the arm under it. What it did not do is give the pauldron a *surface*: every
face was still one flat rectangle of one colour, which is why the cast still
looked like painted card next to a vanilla mob.

Vanilla is not flat anywhere. Its materials are the reference here, and they
were measured rather than eyeballed - every tile below is one of Mojang's own
textures, put through the same arithmetic:

    tile                    std      h1      v1    skew
    deepslate             21.20   0.648   0.226    0.28
    tuff                  20.31   0.507   0.226    0.44
    blackstone            15.76   0.589   0.477    0.57
    iron_block            17.97   0.672   0.543   -1.01
    netherite_block       15.36   0.526   0.494   -0.50
    copper_block          17.99   0.431   0.424   -0.40
    white_wool            12.46   0.427  -0.390   -0.11
    bone_block_side        4.54   0.801   0.967   -0.72
    quartz_block_side      5.63   0.842   0.610   -0.31
    iron_golem (entity)     ---   0.749   0.634     ---
    cow (entity)            ---   0.666   0.662     ---

`std` is how far the pixels stray from the tile's mean; `h1` and `v1` are how
much a pixel resembles the one beside it and the one below it, which is the
difference between a surface and static; `skew` is which tail is longer, which
is the difference between bright flecks and dark pits.

Four characters fall out of that table and they are not subtle - and a fifth,
**strand**, is cloth with its axes swapped, for hair, which falls:

  * **stone** - strata. Nearly three times as correlated along a row as down a
    column, with bright flecks. Carapace, hide, the bulk of a body.
  * **metal** - long streaks, only mildly directional, with dark pits in them.
    Armour plate and regalia.
  * **cloth** - a weave: positive across, **negative** down. A thread has
    nothing to do with the thread beside it and everything to do with the one
    it runs into. Capes and tailoring.
  * **smooth** - barely a texture at all, and running the other way, along the
    shaft. Bone, quartz, skin.

<h2>Why the grain is generated and not copied</h2>

The obvious implementation is to load deepslate.png, scale it down and multiply
it into our colour. It would look right immediately and it would put Mojang's
pixels in our jar, which is not ours to ship however small the crop or however
much it has been recoloured. Deriving a *statistic* from a texture and
generating our own field to match is a different thing entirely, and it is also
the better engineering: a copied tile is 16x16 and repeats, while a generated
field is any size, is seeded per material, and can be asked for the exact
amplitude a dark part can carry without crushing to black.

So the numbers above are the specification, `field()` is the implementation,
and `verify()` measures the implementation against the specification - which is
the only reason to believe any of this.
"""
import math
import zlib

import numpy as np


class Profile:
    """A material's surface character, in the four numbers that describe it."""

    def __init__(self, std, across, down, skew):
        self.std = std
        self.across = across
        self.down = down
        self.skew = skew


# The five characters, with the vanilla tiles each was read from.
#
# The first attempt at this table averaged the horizontal and vertical
# correlations into one number and produced a grain that read as dithering
# rather than as a surface. Splitting them says why:
#
#     tile                     h1      v1
#     deepslate              0.648   0.226
#     tuff                   0.507   0.226
#     white_wool             0.427  -0.390
#     iron_block             0.672   0.543
#     bone_block_side        0.801   0.967
#     iron_golem (entity)    0.749   0.634
#     cow (entity)           0.666   0.662
#
# Almost nothing vanilla paints is isotropic. Deepslate is nearly three times
# as correlated along a row as down a column - it is *strata* - and wool is
# positive across and **negative** down, which is a weave: a thread has nothing
# to do with the thread beside it and everything to do with the one it runs
# into. Bone runs the other way, along the shaft.
#
# The second correction is the overall scale. Entity textures sit at 0.63-0.75
# where the block tiles sit at 0.4-0.6, because a block tile is 16 pixels of
# one material and an entity texture is painted in broader strokes over a body.
# Since these are entity textures, the block tiles set each material's
# *character* - the ratio between the two axes, and which tail the skew is on -
# and the two vanilla entity textures set the scale.
PROFILES = {
    # deepslate / tuff: strata, with bright flecks
    "stone":  Profile(std=13.5, across=0.62, down=0.30, skew=0.35),
    # iron_block / netherite_block, against the golem: streaks with dark pits
    "metal":  Profile(std=12.0, across=0.72, down=0.56, skew=-0.55),
    # white_wool: a weave, negative down the column
    "cloth":  Profile(std=9.0, across=0.45, down=-0.35, skew=-0.10),
    # bone_block_side: smooth, and running the other way
    "smooth": Profile(std=5.0, across=0.80, down=0.92, skew=-0.45),
    # cloth, transposed: hair falls, so its strands run down the column and
    # disagree across it. There is no vanilla block that does this - a block is
    # a cube and has no "down" - so it is wool's own numbers with the two axes
    # swapped, which is what a fibre looked at from the side actually is.
    "strand": Profile(std=10.0, across=-0.30, down=0.55, skew=-0.10),
}


def _gaussian(sigma):
    """A discrete Gaussian wide enough that its own tails do not matter."""
    radius = max(2, int(math.ceil(3.0 * sigma)))
    taps = np.arange(-radius, radius + 1, dtype=np.float64)
    weights = np.exp(-(taps ** 2) / (2.0 * sigma * sigma))
    return weights / weights.sum()


def _lag_one(weights):
    """A kernel's own lag-1 autocorrelation, which is what it imposes on noise."""
    return float((weights[:-1] * weights[1:]).sum() / (weights ** 2).sum())


def _kernel(target):
    """
    The one-axis kernel that gives white noise this much correlation.

    White noise convolved with h has a lag-1 autocorrelation of exactly
    sum(h_i h_{i+1}) / sum(h_i^2). That is a closed form, but inverting it is
    not, and the continuous approximation exp(-1 / (4 sigma^2)) is wrong by a
    fifth at the widths used here - it was tried first and undershot every
    profile, 0.305 where 0.550 was wanted. So the width is found by bisection
    on the exact discrete expression, which is monotone and trivial to solve.

    A *negative* target cannot come from a smoothing kernel at all: every
    Gaussian correlates a pixel with its neighbour positively. Wool's weave
    needs the opposite, so below zero the kernel becomes [k, 1, k] with k
    negative - a sharpening kernel, which is what makes one row disagree with
    the next.
    """
    target = min(max(target, -0.49), 0.95)
    if target < 0:
        def achieved(k):
            weights = np.array([k, 1.0, k])
            return _lag_one(weights)
        # 2k / (2k^2 + 1) rises monotonically with k across this bracket, from
        # -0.667 at k = -0.5 to 0 at k = 0, so the test reads the same way
        # round as the Gaussian branch below. Writing it the other way round
        # pinned every negative profile to the end of the bracket - wool came
        # out at -0.666 where -0.35 was asked for, which is a bisection that
        # never turns around.
        low, high = -0.5, 0.0
        for _ in range(60):
            mid = (low + high) / 2.0
            if achieved(mid) < target:
                low = mid
            else:
                high = mid
        mid = (low + high) / 2.0
        return np.array([mid, 1.0, mid])

    low, high = 0.05, 12.0
    for _ in range(60):
        mid = (low + high) / 2.0
        if _lag_one(_gaussian(mid)) < target:
            low = mid
        else:
            high = mid
    return _gaussian((low + high) / 2.0)


def _smooth(noise, across, down):
    """
    Separable convolution that wraps, so the field tiles seamlessly.

    A different kernel per axis, which is the whole point: the two axes are
    what separate strata from a weave from a polished shaft.
    """
    for axis, weights in ((1, across), (0, down)):
        radius = len(weights) // 2
        noise = sum(w * np.roll(noise, shift, axis=axis)
                    for shift, w in zip(range(-radius, radius + 1), weights))
    return noise


def _skew(field, target):
    """
    Bend a symmetric field until one of its tails is longer.

    x -> x + s(x^2 - 1) is the smallest warp that introduces skew without
    moving the mean, and s is found by bisection rather than by a formula
    because the formula is only exact for a Gaussian and this field is not
    quite one after smoothing.
    """
    if abs(target) < 1e-3:
        return field

    def warped(s):
        out = field + s * (field ** 2 - 1.0)
        out = (out - out.mean()) / (out.std() + 1e-12)
        return out

    def skewness(arr):
        return float((arr ** 3).mean())

    # skewness(warped(s)) rises monotonically with s and is zero at s = 0, so
    # one bracket serves both signs and plain bisection finds the coefficient.
    # The bracket must not be split by sign: an earlier version tested the
    # direction against `target > 0` and walked the wrong way for every
    # negative profile, which came out as a uniform skew of -2.75 on three of
    # the four - the value at the end of the bracket, which is what a bisection
    # that never turns around always returns.
    low, high = -1.5, 1.5
    for _ in range(60):
        mid = (low + high) / 2.0
        if skewness(warped(mid)) < target:
            low = mid
        else:
            high = mid
    return warped((low + high) / 2.0)


def field(profile, size, seed):
    """
    One material's grain, as luminance offsets, `size` square.

    Deterministic in (profile, size, seed) and nothing else - no global RNG,
    no clock, no dict ordering - because every texture in this project is
    regenerated by regen.sh and has to come out byte-for-byte identical or the
    diff is unreadable and nobody can tell a real change from noise.
    """
    spec = PROFILES[profile]
    rng = np.random.default_rng(
        zlib.crc32(("%s|%d|%s" % (profile, size, seed)).encode()) & 0xFFFFFFFF)
    noise = rng.standard_normal((size, size))
    noise = _smooth(noise, _kernel(spec.across), _kernel(spec.down))
    noise = (noise - noise.mean()) / (noise.std() + 1e-12)
    noise = _skew(noise, spec.skew)
    return noise * spec.std


def measure(arr):
    """
    (std, across, down, skew) of a 2-D field. The check on field().

    `across` and `down` are kept apart rather than averaged. Averaging them is
    what hid the anisotropy in the first place: deepslate's 0.648 and 0.226
    average to 0.44, which is a number no vanilla tile actually has, and a
    field built to it looks like nothing in the game.
    """
    deviation = arr - arr.mean()
    variance = deviation.var()
    if variance < 1e-12:
        return 0.0, 0.0, 0.0, 0.0
    return (float(arr.std()),
            float((deviation * np.roll(deviation, 1, axis=1)).mean() / variance),
            float((deviation * np.roll(deviation, 1, axis=0)).mean() / variance),
            float((deviation ** 3).mean() / (deviation.std() ** 3 + 1e-12)))


def verify(size=192, tolerance=0.12):
    """
    Check every profile generates what it claims to.

    Returns a list of complaints, empty when all four match. Called by the mob
    and court validators: a grain generator that quietly drifts off its
    specification is a texture change nobody asked for, appearing in a diff of
    thirty-seven PNGs with no line of source to explain it.
    """
    problems = []
    for name, spec in sorted(PROFILES.items()):
        std, across, down, skew = measure(field(name, size, 0))
        if abs(std - spec.std) > spec.std * tolerance:
            problems.append("grain %r: std %.2f, wanted %.2f"
                            % (name, std, spec.std))
        for label, got, want in (("across", across, spec.across),
                                 ("down", down, spec.down)):
            if abs(got - want) > max(0.06, abs(want) * tolerance):
                problems.append("grain %r: %s correlation %.3f, wanted %.3f"
                                % (name, label, got, want))
        if abs(skew - spec.skew) > max(0.12, abs(spec.skew) * tolerance):
            problems.append("grain %r: skew %.2f, wanted %.2f"
                            % (name, skew, spec.skew))
    return problems


if __name__ == "__main__":
    print("%-8s %6s %7s %7s %6s | %6s %7s %7s %6s"
          % ("profile", "std", "across", "down", "skew",
             "want", "want", "want", "want"))
    for name, spec in sorted(PROFILES.items()):
        print("%-8s %6.2f %7.3f %7.3f %6.2f | %6.2f %7.3f %7.3f %6.2f"
              % ((name,) + measure(field(name, 192, 0))
                 + (spec.std, spec.across, spec.down, spec.skew)))
    for complaint in verify():
        print("  !", complaint)
