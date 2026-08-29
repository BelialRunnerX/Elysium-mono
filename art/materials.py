"""
Metal ramps for every gear material, derived rather than hand-painted.

The style rules in style.py say colour never carries a material — only energy.
That held while there were three materials and a player could tell Voidglass
from Neutronium by silhouette and glow alone. It does not survive twenty-three:
an inventory row of identically grey hammers is unreadable, and "which of these
is the steel one" is a question the sprite has to answer.

So this is a deliberate, bounded relaxation of that rule:

  * A material's ramp is built from one base hue, **heavily desaturated** — far
    closer to grey than the real metal. Copper reads warm, gold reads pale,
    cobalt reads cold. None of them reads *saturated*.
  * The glow still carries the element, at full saturation, and is still the
    only genuinely bright thing on the sprite. A copper hammer and an iron
    hammer differ in a way you notice on inspection; a Plasma hammer and a Void
    hammer differ in a way you notice across the screen.

That ordering is the point. Element is the mechanically important fact — it
decides matchups and rune alignment — so it stays loudest. Material only decides
how good the tool is, which the tooltip already says.

Ramps are generated from the base so that all twenty-three are internally
consistent: same five steps, same lightness curve, same near-black outline.
Hand-painting each one would drift, and drift across two dozen sprites reads as
sloppiness rather than variety.
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

# How much of the base hue survives. Low on purpose — see the module docstring.
_SATURATION = 0.22


# A base at or above this saturation gets the full allowance; below it, a
# proportional share. Without this an achromatic base like iron's #c8c8c8 has
# hue 0 by convention — which is *red* — and forcing saturation onto it turns
# iron into copper. A grey material must stay grey.
_REFERENCE_SATURATION = 0.35


def ramp(base_hex, saturation=_SATURATION, outline="#05060a"):
    """
    Six entries: index 0 is the outline, 1 darkest .. 5 brightest highlight.

    The base's hue is kept and its saturation scaled down, so a material reads
    as "grey, but warm" rather than as its real-world colour — and a base that
    was already grey stays grey rather than acquiring a hue it never had.
    """
    hue, _, base_saturation = colorsys.rgb_to_hls(
        *[c / 255.0 for c in _hex_to_rgb(base_hex)])
    saturation *= min(1.0, base_saturation / _REFERENCE_SATURATION)
    shades = []
    for lightness in _STEPS:
        rgb = colorsys.hls_to_rgb(hue, lightness, saturation)
        shades.append(_rgb_to_hex([c * 255 for c in rgb]))
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
    "tin":        "#9fb4bd",
    "zinc":       "#b8bfa8",
    "lead":       "#6b6b86",
    "silver":     "#c4d0dc",
    "nickel":     "#c0bda0",
    "aluminum":   "#c9d2d6",
    "platinum":   "#a8d8e0",
    "bronze":     "#c08a4a",
    "brass":      "#c4a748",
    "steel":      "#8f9499",
    "invar":      "#a8b0a6",
    "constantan": "#c48f5e",
    "electrum":   "#ddd07a",
    "osmium":     "#8fa8c4",
    "uranium":    "#7fbf5a",
    "titanium":   "#b0a8b8",
    "tungsten":   "#5f6470",
    "cobalt":     "#4a7fc4",
}


def all_ramps():
    """{material id: six-entry ramp}, ready to merge into style.METAL."""
    return {name: ramp(base) for name, base in BASES.items()}
