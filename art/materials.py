"""
Metal ramps for every gear material, derived rather than hand-painted.

The style rules in style.py say colour never carries a material — only energy.
That held while there were three materials and a player could tell Voidglass
from Neutronium by silhouette and glow alone. It does not survive twenty-three:
an inventory row of identically grey hammers is unreadable, and "which of these
is the steel one" is a question the sprite has to answer.

So this is a deliberate, bounded relaxation of that rule:

  * A material's ramp is built from one base hue, muted but genuinely coloured.
    Copper reads warm, gold reads pale, cobalt reads cold, and you can name
    which is which at sixteen pixels. This started out *heavily* desaturated,
    on the theory that a tint was enough — it was not, and the numbers behind
    that are under _SATURATION below.
  * The glow still carries the element, and is still the only thing on a sprite
    that is fully saturated and the only thing that reaches the top of its
    ramp. A copper hammer and an iron hammer differ in a way you notice on
    inspection; a Plasma hammer and a Void hammer differ in a way you notice
    across the screen.

That ordering is the point. Element is the mechanically important fact — it
decides matchups and rune alignment — so it stays loudest. Material only decides
how good the tool is, which the tooltip already says.

Ramps are generated from the base so that all twenty-three are internally
consistent: same five steps, the same lightness curve tilted by the base's own
lightness, and an outline derived from the material rather than shared.
Hand-painting each one would drift, and drift across two dozen sprites reads as
sloppiness rather than variety.

Two properties of this set are checked rather than trusted, in
elysium-core/validate.py: no two materials may land within 8 units of CIELAB of
each other, and no material's metal may flatten below 120 units of luminance.
Both of those had already gone wrong once.
"""
import colorsys


def _hex_to_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def _rgb_to_hex(rgb):
    return "#%02x%02x%02x" % tuple(max(0, min(255, int(round(c)))) for c in rgb)


# Lightness for steps 1..5.
#
# Was [0.10, 0.19, 0.29, 0.41, 0.56], which topped out at luminance 142. That
# was measured against vanilla's own metal and found to be the root of the
# "blobby" reading: vanilla's iron ingot runs from luminance 53 to 255 and its
# sword from 24 to 255, so *our brightest pixel was darker than vanilla's
# midtone*. A sprite with no highlight has no value structure, and a shape with
# no value structure reads as a silhouette however well it is drawn - which is
# why reshaping the tools alone did not fix it.
#
# This is a deliberate revision of the rule above it. The glow keeps its
# monopoly on *saturation*, which is what actually carries across a screen; it
# no longer keeps a monopoly on brightness, which was costing every sprite its
# form.
_STEPS = [0.12, 0.24, 0.38, 0.55, 0.78]

# How much of the base hue survives.
#
# Was 0.22, and that was the second thing the "colour never carries a material"
# rule was costing us. At 0.22 a copper hammer and an iron hammer are two greys
# a shade apart: the material is technically encoded and not actually visible,
# which is the worst of both outcomes — the sprite spends its colour budget and
# the player still cannot read it.
#
# Measured rather than eyeballed, in CIELAB on each material's midtone:
#
#              chroma, brightest material    widest separation of two materials
#   sat 0.22             27                              40
#   sat 0.46             54                              82
#
# Twenty-odd units of chroma is not a colour at sixteen pixels, it is a tint on
# a grey. Fifty is copper looking like copper.
#
# The rule that survives is narrower, and is the half that was always doing the
# work: the element glow is the only *fully* saturated thing on a sprite and the
# only thing that reaches the top of its ramp. A material is a tint. An element
# is a light. Those stay distinguishable across a room; two greys never did.
_SATURATION = 0.46


# A base at or above this saturation gets the full allowance; below it, a
# proportional share. Without this an achromatic base like iron's #c8c8c8 has
# hue 0 by convention — which is *red* — and forcing saturation onto it turns
# iron into copper. A grey material must stay grey.
_REFERENCE_SATURATION = 0.35

# ...and a base well *above* the reference is allowed more than the full
# allowance, up to this multiple.
#
# The cap used to be 1.0, and together with the fixed lightness curve below it
# meant a ramp kept nothing from its base except hue. That is a bug with a
# visible consequence: brass (#c4a748) and gold (#e6c34d) are the same hue, so
# every brass item in the mod was *byte-identical* to its gold counterpart.
# Twenty-six materials, twenty-five distinguishable. Measured, not noticed —
# see the separation check in elysium-core/validate.py.
_SATURATION_CAP = 1.8

# How much of the base's own lightness shifts the curve.
#
# The curve in _STEPS is what keeps twenty-six materials consistent, and it is
# worth keeping — but applied rigidly it flattens a pale metal and a dark one
# into the same values. This tilts the whole ramp by the base's lightness while
# leaving its shape alone, which is the difference between gold and brass, and
# between aluminium and lead. 0.35 was chosen by measurement: it lifts the
# closest pair in the set from 0.0 to 4.7 while keeping every material's
# highlight inside luminance 177..234, which is the band vanilla's own metals
# sit in.
_LIGHTNESS_INFLUENCE = 0.35

# The base lightness that gets no tilt at all. Roughly the middle of the range
# the bases occupy, so the tilt pushes as many materials up as down and the set
# keeps its overall value.
_PIVOT_LIGHTNESS = 0.55

# The outline's lightness, as a share of step 1's.
_OUTLINE_LIGHTNESS = 0.45


def ramp(base_hex, saturation=_SATURATION, outline=None):
    """
    Six entries: index 0 is the outline, 1 darkest .. 5 brightest highlight.

    The base's hue is kept and its saturation scaled down, so a material reads
    as "grey, but warm" rather than as its real-world colour — and a base that
    was already grey stays grey rather than acquiring a hue it never had.

    The outline is now derived from the material instead of being one shared
    near-black, and that single line is most of why every sprite in this mod
    read as violet whatever it was made of. `#05060a` is a blue-black; an
    outline traces the whole silhouette, so at sixteen pixels it is a large
    share of what the eye averages, and it was averaging every material toward
    the same cold dark. Vanilla does not share one outline either — its iron is
    outlined in a dark grey and its gold in a dark brown.
    """
    hue, base_lightness, base_saturation = colorsys.rgb_to_hls(
        *[c / 255.0 for c in _hex_to_rgb(base_hex)])
    saturation *= min(_SATURATION_CAP, base_saturation / _REFERENCE_SATURATION)
    tilt = (base_lightness - _PIVOT_LIGHTNESS) * _LIGHTNESS_INFLUENCE

    def at(step):
        return min(0.95, max(0.04, step + tilt))

    shades = []
    for step in _STEPS:
        rgb = colorsys.hls_to_rgb(hue, at(step), saturation)
        shades.append(_rgb_to_hex([c * 255 for c in rgb]))
    if outline is None:
        outline = _rgb_to_hex([c * 255 for c in colorsys.hls_to_rgb(
            hue, at(_STEPS[0]) * _OUTLINE_LIGHTNESS, saturation)])
    return [outline] + shades


# --------------------------------------------------------------------------
# Base hues. One line per material; everything else is derived.
# --------------------------------------------------------------------------
#
# The names are the material ids from ElysiumMaterials.java. They must match
# exactly, because build.py looks them up by id — a mismatch means a material
# with no texture, which validate.py then reports.

BASES = {
    # vanilla
    "copper":     "#c87a45",
    "iron":       "#c8c8c8",
    "gold":       "#e6c34d",
    "diamond":    "#4fd8d0",
    "netherite":  "#5c4f4a",

    # modded — the common metals, in the colours those mods usually pick
    # tin, aluminium, constantan and osmium were moved apart from their
    # neighbours after the separation check found them within 5 units of
    # CIELAB of bronze, each other and silver. The mods that add them do not
    # agree on a colour anyway; being distinguishable matters more than
    # matching any one of them.
    "tin":        "#8fa9b6",
    "zinc":       "#b8bfa8",
    "lead":       "#6b6b86",
    "silver":     "#c4d0dc",
    "nickel":     "#c0bda0",
    "aluminum":   "#dfe4e6",
    "platinum":   "#a8d8e0",
    "bronze":     "#c08a4a",
    "brass":      "#c4a748",
    "steel":      "#8f9499",
    "invar":      "#a8b0a6",
    "constantan": "#b8806f",
    "electrum":   "#ddd07a",
    "osmium":     "#7d96b8",
    "uranium":    "#7fbf5a",
    "titanium":   "#b0a8b8",
    "tungsten":   "#5f6470",
    "cobalt":     "#4a7fc4",
}


def all_ramps():
    """{material id: six-entry ramp}, ready to merge into style.METAL."""
    return {name: ramp(base) for name, base in BASES.items()}
