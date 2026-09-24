"""The S8 reskin: Stellaris's art, redrawn into Elysium's Voidforged palette.

Shapes are kept pixel for pixel; only colour changes. Each pixel keeps its lightness
exactly and takes the colour its world's metal ramp has at that lightness; pixels that are bright and either far more saturated than
the texture's material or of another hue altogether, which in Stellaris's art are the lit parts (glowing vines, eyes), go to the world's glow
ramp instead, following the art rule that colour only ever carries energy. `strength` blends toward the
original: blocks take the palette fully; mobs keep some of their own colour so
a moonfolk's profession still reads from its clothes.

Credit for the source art: Stellaris, (c) Exodus Studio, CC BY-NC-SA 4.0.
"""
import colorsys
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
from art import style  # noqa: E402

# Cinderveil has no Voidforged metal of its own: a scorched basalt, warm but desaturated.
CINDER = ["#0a0605", "#241410", "#3f231a", "#5e3a29", "#86583f", "#c49a7c"]
WORLDS = {  # world: (base ramp, glow ramp)
    "pale_moon": (style.METAL["neutronium"], style.GLOW["aetherium"]),
    "frostfall": (style.METAL["aetherium"], style.GLOW["dimensional"]),
    "cinderveil": (CINDER, style.GLOW["plasma"]),
}


def _rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def _lerp(ramp, t):
    """t in [0, 1] along a ramp of colours."""
    t = min(1.0, max(0.0, t)) * (len(ramp) - 1)
    i = min(int(t), len(ramp) - 2)
    f = t - i
    a, b = ramp[i], ramp[i + 1]
    return tuple(round(a[k] + (b[k] - a[k]) * f) for k in range(3))


def _lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def _at_lum(ramp, lum):
    """The colour on a ramp whose lightness is lum (ramps run dark to light), clamped at the ends."""
    ls = [_lum(c) for c in ramp]
    if lum <= ls[0]:
        return ramp[0]
    for i in range(len(ramp) - 1):
        if lum <= ls[i + 1]:
            f = (lum - ls[i]) / max(1e-6, ls[i + 1] - ls[i])
            return tuple(round(ramp[i][k] + (ramp[i + 1][k] - ramp[i][k]) * f) for k in range(3))
    return ramp[-1]


def reskin(img, world, strength=1.0):
    base, glow = WORLDS[world]
    base = [_rgb(h) for h in base]
    glow = [_rgb(h) for h in glow]
    img = img.convert("RGBA")
    px = img.load()
    w, h = img.size
    sats = sorted(colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)[1] for x in range(w) for y in range(h)
                  for r, g, b, a in [px[x, y]] if a > 0)
    if not sats:
        return img
    usual = sats[len(sats) // 2]            # a texture's usual saturation is its material, not a glow
    hues = sorted(colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)[0] for x in range(w) for y in range(h)
                  for r, g, b, a in [px[x, y]] if a > 0)
    main_hue = hues[len(hues) // 2]
    out = Image.new("RGBA", img.size)
    po = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                po[x, y] = (0, 0, 0, 0)
                continue
            hue, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            lum = _lum((r, g, b))
            apart = min(abs(hue - main_hue), 1 - abs(hue - main_hue)) > 1 / 6   # a colour the material is not
            if v > 0.55 and (s > usual + 0.3 or (s > 0.5 and apart)):
                c = _at_lum(glow, lum)
            else:
                c = _at_lum(base, lum)       # same lightness, the world's colour: value structure is untouched
            po[x, y] = tuple(round(c[k] * strength + (r, g, b)[k] * (1 - strength)) for k in range(3)) + (a,)
    return out


def reskin_file(src, dst, world, strength=1.0):
    reskin(Image.open(src), world, strength).save(dst)
