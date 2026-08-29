#!/usr/bin/env python3
"""
A pixel-accurate mirror of ElysiumUI, for looking at screens before they ship.

Why this exists: a GUI is the one part of this project the compile harness
cannot check. javac will happily compile a screen whose text is unreadable,
whose panels overlap, or whose columns collide at the width a player actually
uses. Nothing else in the tree has that gap.

So every primitive in ElysiumUI.java has a twin here, drawing the same
rectangles from the same palette — palette.py generates the Java constants and
is imported directly by this file, so the colours cannot differ. What is
transcribed rather than shared is the *layout* of each screen; that is stated
plainly rather than claimed to be verified, and the mockups are checked against
the Java by eye.

The font is the thing that cannot be mirrored exactly. Minecraft's is a 6x9
bitmap with per-glyph widths; this uses a 5x7 grid approximation with the same
advance rules, which is close enough to catch "this label collides with that
number" and not close enough to judge kerning.
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

from PIL import Image, ImageDraw

import palette as P

UNIT = P.METRICS["UNIT"]
PANEL_PAD = P.METRICS["PANEL_PAD"]
GAP = P.METRICS["GAP"]
GAP_WIDE = P.METRICS["GAP_WIDE"]
ROW = P.METRICS["ROW"]
ROW_TALL = P.METRICS["ROW_TALL"]
TAB_HEIGHT = P.METRICS["TAB_HEIGHT"]
BUTTON_HEIGHT = P.METRICS["BUTTON_HEIGHT"]
BAR_HEIGHT = P.METRICS["BAR_HEIGHT"]
SLOT = P.METRICS["SLOT_SIZE"]
CORNER = P.METRICS["CORNER"]

LINE_HEIGHT = 9
CHAR_W = 6          # Minecraft's default advance for most glyphs


class Canvas:
    """A GuiGraphics work-alike: fill() and text, nothing else."""

    def __init__(self, width, height, world=(0x5A, 0x60, 0x6E)):
        self.w = width
        self.h = height
        # A flat mid-tone stands in for the world behind the screen. Not a
        # screenshot, because the question a mockup answers is "is this legible
        # over a typical background", and a real screenshot would answer it for
        # exactly one background.
        self.img = Image.new("RGBA", (width, height), world + (255,))
        self.draw = ImageDraw.Draw(self.img, "RGBA")

    def fill(self, x0, y0, x1, y1, colour):
        """
        Composite a colour over what is already there, as the game does.

        PIL's rectangle() *replaces* pixels including their alpha, which is not
        what GuiGraphics.fill does — and the difference is not subtle. Every
        translucent thing in this interface is translucent on purpose: the
        panel is glass over the world, selection is a 14% wash, the active tab
        is a 10% tint. Drawn with replacement, a 10% violet tint renders as
        near-white, which is exactly what the first mockup showed and what sent
        me looking for a design problem that did not exist.
        """
        x0, y0 = max(0, x0), max(0, y0)
        x1, y1 = min(self.w, x1), min(self.h, y1)
        if x1 <= x0 or y1 <= y0:
            return
        r, g, b, a = P.rgba(colour)
        if a >= 255:
            self.draw.rectangle([x0, y0, x1 - 1, y1 - 1], fill=(r, g, b, 255))
            return
        box = (x0, y0, x1, y1)
        base = self.img.crop(box)
        layer = Image.new("RGBA", (x1 - x0, y1 - y0), (r, g, b, a))
        self.img.paste(Image.alpha_composite(base, layer), box)
        self.draw = ImageDraw.Draw(self.img, "RGBA")

    # --- text ------------------------------------------------------------
    def text(self, s, x, y, colour):
        r, g, b, a = P.rgba(colour)
        self._blit_text(str(s), x, y, (r, g, b, a))

    def text_right(self, s, right, y, colour):
        self.text(s, right - self.width_of(s), y, colour)

    def text_centre(self, s, cx, y, colour):
        self.text(s, cx - self.width_of(s) // 2, y, colour)

    @staticmethod
    def width_of(s):
        return len(str(s)) * CHAR_W

    def _blit_text(self, s, x, y, rgba_colour):
        # A 5x7 block approximation: each glyph is drawn as a small pattern of
        # pixels dense enough to read as text at 1x and to occupy the right
        # space. It is a stand-in for legibility testing, not a font.
        for i, ch in enumerate(s):
            if ch == " ":
                continue
            gx = x + i * CHAR_W
            self._glyph(ch, gx, y, rgba_colour)

    def _glyph(self, ch, x, y, colour):
        rows = _GLYPHS.get(ch.upper(), _GLYPHS["?"])
        r, g, b, a = colour
        for ry, bits in enumerate(rows):
            for rx in range(5):
                if bits & (1 << (4 - rx)):
                    if a >= 255:
                        self.draw.point((x + rx, y + ry), fill=(r, g, b, 255))
                    else:
                        # Translucent text - the slot hint glyphs - has to
                        # composite for the same reason fills do.
                        self.fill(x + rx, y + ry, x + rx + 1, y + ry + 1,
                                  (a << 24) | (r << 16) | (g << 8) | b)

    def save(self, path, zoom=1):
        img = self.img
        if zoom != 1:
            img = img.resize((self.w * zoom, self.h * zoom), Image.NEAREST)
        img.save(path)


# A crude 5x7 font. Enough to tell a heading from a number and to occupy the
# correct width; not enough to judge typography.
_GLYPHS = {
    "A": [0x0E, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11],
    "B": [0x1E, 0x11, 0x1E, 0x11, 0x11, 0x11, 0x1E],
    "C": [0x0E, 0x11, 0x10, 0x10, 0x10, 0x11, 0x0E],
    "D": [0x1E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x1E],
    "E": [0x1F, 0x10, 0x1E, 0x10, 0x10, 0x10, 0x1F],
    "F": [0x1F, 0x10, 0x1E, 0x10, 0x10, 0x10, 0x10],
    "G": [0x0E, 0x11, 0x10, 0x17, 0x11, 0x11, 0x0F],
    "H": [0x11, 0x11, 0x1F, 0x11, 0x11, 0x11, 0x11],
    "I": [0x0E, 0x04, 0x04, 0x04, 0x04, 0x04, 0x0E],
    "J": [0x07, 0x02, 0x02, 0x02, 0x12, 0x12, 0x0C],
    "K": [0x11, 0x12, 0x14, 0x18, 0x14, 0x12, 0x11],
    "L": [0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x1F],
    "M": [0x11, 0x1B, 0x15, 0x15, 0x11, 0x11, 0x11],
    "N": [0x11, 0x19, 0x15, 0x13, 0x11, 0x11, 0x11],
    "O": [0x0E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E],
    "P": [0x1E, 0x11, 0x11, 0x1E, 0x10, 0x10, 0x10],
    "Q": [0x0E, 0x11, 0x11, 0x11, 0x15, 0x12, 0x0D],
    "R": [0x1E, 0x11, 0x11, 0x1E, 0x14, 0x12, 0x11],
    "S": [0x0F, 0x10, 0x10, 0x0E, 0x01, 0x01, 0x1E],
    "T": [0x1F, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04],
    "U": [0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E],
    "V": [0x11, 0x11, 0x11, 0x11, 0x11, 0x0A, 0x04],
    "W": [0x11, 0x11, 0x11, 0x15, 0x15, 0x1B, 0x11],
    "X": [0x11, 0x11, 0x0A, 0x04, 0x0A, 0x11, 0x11],
    "Y": [0x11, 0x11, 0x0A, 0x04, 0x04, 0x04, 0x04],
    "Z": [0x1F, 0x01, 0x02, 0x04, 0x08, 0x10, 0x1F],
    "0": [0x0E, 0x11, 0x13, 0x15, 0x19, 0x11, 0x0E],
    "1": [0x04, 0x0C, 0x04, 0x04, 0x04, 0x04, 0x0E],
    "2": [0x0E, 0x11, 0x01, 0x02, 0x04, 0x08, 0x1F],
    "3": [0x1F, 0x02, 0x04, 0x02, 0x01, 0x11, 0x0E],
    "4": [0x02, 0x06, 0x0A, 0x12, 0x1F, 0x02, 0x02],
    "5": [0x1F, 0x10, 0x1E, 0x01, 0x01, 0x11, 0x0E],
    "6": [0x06, 0x08, 0x10, 0x1E, 0x11, 0x11, 0x0E],
    "7": [0x1F, 0x01, 0x02, 0x04, 0x08, 0x08, 0x08],
    "8": [0x0E, 0x11, 0x11, 0x0E, 0x11, 0x11, 0x0E],
    "9": [0x0E, 0x11, 0x11, 0x0F, 0x01, 0x02, 0x0C],
    "%": [0x18, 0x19, 0x02, 0x04, 0x08, 0x13, 0x03],
    "+": [0x00, 0x04, 0x04, 0x1F, 0x04, 0x04, 0x00],
    "-": [0x00, 0x00, 0x00, 0x1F, 0x00, 0x00, 0x00],
    ".": [0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0x0C],
    ",": [0x00, 0x00, 0x00, 0x00, 0x0C, 0x04, 0x08],
    ":": [0x00, 0x0C, 0x0C, 0x00, 0x0C, 0x0C, 0x00],
    "/": [0x01, 0x02, 0x02, 0x04, 0x08, 0x08, 0x10],
    "(": [0x02, 0x04, 0x08, 0x08, 0x08, 0x04, 0x02],
    ")": [0x08, 0x04, 0x02, 0x02, 0x02, 0x04, 0x08],
    "[": [0x0E, 0x08, 0x08, 0x08, 0x08, 0x08, 0x0E],
    "]": [0x0E, 0x02, 0x02, 0x02, 0x02, 0x02, 0x0E],
    "'": [0x04, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00],
    "?": [0x0E, 0x11, 0x01, 0x02, 0x04, 0x00, 0x04],
    "!": [0x04, 0x04, 0x04, 0x04, 0x04, 0x00, 0x04],
    "↑": [0x04, 0x0E, 0x15, 0x04, 0x04, 0x04, 0x04],
    "→": [0x00, 0x04, 0x02, 0x1F, 0x02, 0x04, 0x00],
}


# ===========================================================================
# The primitives — one per ElysiumUI method, same arithmetic
# ===========================================================================

def scrim(c):
    c.fill(0, 0, c.w, c.h, P.PALETTE["SCRIM"])


# Every panel drawn since the last reset, for the fits-on-screen check.
PANELS = []


def reset_panels():
    PANELS.clear()


def panel(c, x, y, w, h, fill=None, edge=None):
    PANELS.append((x, y, w, h, c.w, c.h))
    fill = P.PALETTE["SURFACE"] if fill is None else fill
    edge = P.PALETTE["LINE"] if edge is None else edge
    k = CORNER
    c.fill(x + k, y, x + w - k, y + h, fill)
    c.fill(x, y + k, x + k, y + h - k, fill)
    c.fill(x + w - k, y + k, x + w, y + h - k, fill)

    c.fill(x + k, y, x + w - k, y + 1, edge)
    c.fill(x + k, y + h - 1, x + w - k, y + h, edge)
    c.fill(x, y + k, x + 1, y + h - k, edge)
    c.fill(x + w - 1, y + k, x + w, y + h - k, edge)
    for i in range(k):
        c.fill(x + k - 1 - i, y + i, x + k - i, y + i + 1, edge)
        c.fill(x + w - k + i, y + i, x + w - k + i + 1, y + i + 1, edge)
        c.fill(x + k - 1 - i, y + h - i - 1, x + k - i, y + h - i, edge)
        c.fill(x + w - k + i, y + h - i - 1, x + w - k + i + 1, y + h - i, edge)


def raised(c, x, y, w, h):
    c.fill(x, y, x + w, y + h, P.PALETTE["SURFACE_RAISED"])


def sunken(c, x, y, w, h):
    c.fill(x, y, x + w, y + h, P.PALETTE["SURFACE_SUNKEN"])
    c.fill(x, y, x + w, y + 1, P.PALETTE["LINE_SOFT"])


def rule(c, x, y, w):
    c.fill(x, y, x + w, y + 1, P.PALETTE["LINE"])


def rule_soft(c, x, y, w):
    c.fill(x, y, x + w, y + 1, P.PALETTE["LINE_SOFT"])


def rule_vertical(c, x, y, h):
    c.fill(x, y, x + 1, y + h, P.PALETTE["LINE"])


def accent_rule(c, x, y, w, colour):
    c.fill(x, y, x + w, y + 1, colour)


def alpha(colour, opacity):
    a = max(0, min(255, round(255 * opacity)))
    return (a << 24) | (colour & 0x00FFFFFF)


def bar(c, x, y, w, fraction, colour, h=BAR_HEIGHT):
    f = max(0.0, min(1.0, fraction))
    c.fill(x, y, x + w, y + h, P.PALETTE["SURFACE_SUNKEN"])
    c.fill(x, y, x + w, y + 1, P.PALETTE["LINE_SOFT"])
    filled = round(w * f)
    if filled > 0:
        c.fill(x, y, x + filled, y + h, colour)
    if 0 < filled < w:
        c.fill(x + filled - 1, y, x + filled, y + h, alpha(0xFFFFFFFF, 0.35))


def bar_with_mark(c, x, y, w, fraction, mark, colour):
    bar(c, x, y, w, fraction, colour)
    at = x + round(w * max(0.0, min(1.0, mark)))
    c.fill(at, y - 1, at + 1, y + BAR_HEIGHT + 1, P.PALETTE["TEXT_FAINT"])


def chip(c, text, x, y, colour):
    w = c.width_of(text) + 8
    h = LINE_HEIGHT + 3
    c.fill(x, y, x + w, y + h, alpha(colour, 0.12))
    c.fill(x, y, x + 1, y + h, colour)
    c.text(text, x + 4, y + 2, colour)
    return w


def wrapped(c, text, x, y, w, colour, line_gap=1, max_y=None):
    """
    Prose, broken to fit a column, stopping at max_y and saying so.

    A primitive rather than something each screen does, because every screen
    with a sentence on it had the same bug in the first mockup: text laid out
    at a fixed x with no idea how wide its container was, running out past the
    panel edge. Wrapping is not a nicety here — a description pane is a column
    of arbitrary text, and its width is decided by the layout, not the writer.

    Every line is measured before any is drawn, so a paragraph that does not
    fit can end in an ellipsis rather than simply stopping. The picker at 320
    pixels cut a passive's description off after two words and the result read
    as a complete if terse sentence. Text that has been cut must look cut, or
    the reader is misinformed rather than underinformed.

    Returns the y below the last line, so callers can stack.
    """
    lines, line = [], ""
    for word in str(text).split():
        candidate = word if not line else line + " " + word
        if c.width_of(candidate) > w and line:
            lines.append(line)
            line = word
        else:
            line = candidate
    if line:
        lines.append(line)

    room = max(0, (max_y - y + line_gap) // (LINE_HEIGHT + line_gap)) \
        if max_y is not None else len(lines)
    shown = min(room, len(lines))
    for i in range(shown):
        drawn = lines[i]
        if i == shown - 1 and shown < len(lines):
            drawn = truncate(c, drawn + " ...", w)
        c.text(drawn, x, y, colour)
        y += LINE_HEIGHT + line_gap
    return y


def truncate(c, text, w):
    """
    Text cut to fit, with an ellipsis.

    Needed because a heading's width is decided by the layout and its content
    by a translator: "SANCTIONED ANSWER" fits a third of a 380-pixel panel and
    not a third of a 320-pixel one, and a localised string could be longer
    again. Silently overflowing is the wrong answer for the same reason a
    fixed-width panel was.
    """
    text = str(text)
    if c.width_of(text) <= w:
        return text
    while text and c.width_of(text + "..") > w:
        text = text[:-1]
    return text + ".."


def chip_right(c, text, right, y, colour):
    """A chip whose right edge is fixed, for a header corner."""
    w = c.width_of(text) + 8
    chip(c, text, right - w, y, colour)
    return w


def plate(c, x, y, w, h):
    """
    A backing plate for HUD text.

    The one place the no-shadow rule needs help. Everywhere else this interface
    draws on its own dark panel, where a shadow only softens the glyphs. The HUD
    sits directly on the world, which can be snow or lava, and unbacked text
    there is legible by luck. A plate is a better answer than a shadow: it holds
    the contrast the palette was checked at instead of hoping.
    """
    c.fill(x, y, x + w, y + h, alpha(P.PALETTE["SURFACE"], 0.72))


def slot(c, x, y, hovered=False):
    c.fill(x, y, x + SLOT, y + SLOT, P.PALETTE["SLOT"])
    c.fill(x, y, x + SLOT, y + 1, P.PALETTE["SLOT_EDGE"])
    c.fill(x, y + SLOT - 1, x + SLOT, y + SLOT, P.PALETTE["SLOT_EDGE"])
    c.fill(x, y, x + 1, y + SLOT, P.PALETTE["SLOT_EDGE"])
    c.fill(x + SLOT - 1, y, x + SLOT, y + SLOT, P.PALETTE["SLOT_EDGE"])
    if hovered:
        c.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, P.PALETTE["SLOT_HOVER"])


def slot_hinted(c, x, y, glyph, colour, hovered=False):
    slot(c, x, y, hovered)
    w = c.width_of(glyph)
    c.text(glyph, x + (SLOT - w) // 2, y + 5, alpha(colour, 0.30))


def selection(c, x, y, w, h, colour):
    c.fill(x, y, x + w, y + h, alpha(colour, 0.14))
    c.fill(x, y, x + 2, y + h, colour)


def hover(c, x, y, w, h):
    c.fill(x, y, x + w, y + h, alpha(P.PALETTE["ACCENT_BRIGHT"], 0.08))


def focus_ring(c, x, y, w, h):
    colour = P.PALETTE["ACCENT_BRIGHT"]
    c.fill(x, y, x + w, y + 1, colour)
    c.fill(x, y + h - 1, x + w, y + h, colour)
    c.fill(x, y, x + 1, y + h, colour)
    c.fill(x + w - 1, y, x + w, y + h, colour)


# ===========================================================================
# Composites the screens share
# ===========================================================================

def button(c, text, x, y, w, state="normal"):
    """
    A flat button: a hairline box, an accent left bar when it can be pressed.

    Three states and no more. A button that is disabled, hovered, focused and
    pressed at once is four booleans and sixteen appearances, and nobody has
    ever needed the sixteenth.
    """
    h = BUTTON_HEIGHT
    if state == "disabled":
        panel(c, x, y, w, h, P.PALETTE["SURFACE_SUNKEN"], P.PALETTE["LINE_SOFT"])
        c.text_centre(text, x + w // 2, y + (h - 7) // 2, P.PALETTE["TEXT_FAINT"])
        return
    edge = P.PALETTE["ACCENT"] if state == "hover" else P.PALETTE["LINE"]
    fill = P.PALETTE["SURFACE_RAISED"]
    panel(c, x, y, w, h, fill, edge)
    if state == "hover":
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, alpha(P.PALETTE["ACCENT"], 0.10))
    colour = P.PALETTE["TEXT"] if state == "hover" else P.PALETTE["TEXT_MUTED"]
    c.text_centre(text, x + w // 2, y + (h - 7) // 2, colour)


def stepper(c, x, y, enabled=True):
    """
    A 10x10 "+" beside a stat row.

    Its own primitive rather than a small button, because a button is 18 pixels
    tall by definition and a stat row is 14 - reusing one here is what made six
    of them overlap each other and spill out of the panel in the first mockup.
    """
    size = 10
    colour = P.PALETTE["ACCENT"] if enabled else P.PALETTE["LINE_SOFT"]
    c.fill(x, y, x + size, y + size, alpha(colour, 0.16 if enabled else 0.08))
    c.fill(x, y, x + size, y + 1, colour)
    c.fill(x, y + size - 1, x + size, y + size, colour)
    c.fill(x, y, x + 1, y + size, colour)
    c.fill(x + size - 1, y, x + size, y + size, colour)
    text_colour = P.PALETTE["TEXT"] if enabled else P.PALETTE["TEXT_FAINT"]
    c.fill(x + 4, y + 2, x + 6, y + 8, text_colour)
    c.fill(x + 2, y + 4, x + 8, y + 6, text_colour)
    return size


def tabs(c, labels, x, y, w, active):
    """
    A tab strip: labels on a rule, the active one carrying an accent underline.

    The underline rather than a raised tab shape, because a raised tab needs a
    join to the panel below it and that join is the fiddliest thing in any
    interface to keep right at every width.
    """
    step = w // len(labels)
    for i, text in enumerate(labels):
        tx = x + i * step
        if i == active:
            c.fill(tx, y, tx + step, y + TAB_HEIGHT, alpha(P.PALETTE["ACCENT"], 0.10))
        c.text_centre(text, tx + step // 2, y + 5,
                      P.PALETTE["TEXT"] if i == active else P.PALETTE["TEXT_MUTED"])
    rule(c, x, y + TAB_HEIGHT, w)
    active_x = x + active * step
    accent_rule(c, active_x, y + TAB_HEIGHT, step, P.PALETTE["ACCENT"])


def section(c, title, x, y, w, colour=None):
    """A heading with an accent rule under it. Returns the y below it."""
    colour = P.PALETTE["ACCENT"] if colour is None else colour
    c.text(truncate(c, title, w), x, y, P.PALETTE["TEXT"])
    accent_rule(c, x, y + LINE_HEIGHT + 2, min(w, 24), colour)
    rule_soft(c, x + min(w, 24), y + LINE_HEIGHT + 2, w - min(w, 24))
    return y + LINE_HEIGHT + 2 + GAP


# A stat row: a line of text, a 2px bar under it, and air. Named because the
# screen has to know it to size the panel, and guessing is how the last row
# ended up underneath the footer.
STAT_ROW = LINE_HEIGHT + 1 + 2 + 4


def stat_row(c, name, value, x, y, w, fraction=None, colour=None):
    """A stat: name left, value right, a thin bar beneath."""
    colour = P.PALETTE["TEXT"] if colour is None else colour
    c.text(name, x, y, P.PALETTE["TEXT_MUTED"])
    c.text_right(value, x + w, y, colour)
    if fraction is not None:
        bar(c, x, y + LINE_HEIGHT + 1, w, fraction, colour, h=2)
    return y + STAT_ROW
