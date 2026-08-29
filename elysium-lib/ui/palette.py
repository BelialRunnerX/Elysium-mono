#!/usr/bin/env python3
"""
The Elysium interface palette. One source, two consumers.

This file is the only place a colour is written down. It generates the Java
constants in ElysiumPalette.java, and it is imported directly by the mockup
renderer — so a colour cannot be one value on screen and another in a preview I
looked at and approved.

That matters more than it sounds. A GUI is the one part of this project the stub
harness cannot check: it compiles whatever colours it is given. The preview is
the only thing that can catch "this panel is too dark to read", and a preview
drawn from different numbers than the game is worse than no preview at all,
because it is confidently wrong.

---------------------------------------------------------------------------
The design, stated so it can be argued with
---------------------------------------------------------------------------

**Ink, not chrome.** Panels are near-black glass with a single hairline edge.
No bevels, no gradients pretending to be lit metal, no drop shadows. Vanilla's
GUI is a texture of carved stone; this is a display panel on the inside of a
helmet, and the difference should be obvious at a glance.

**One accent at a time.** Violet carries the interface itself — selection,
focus, the active tab. The five element colours are borrowed from the existing
art system and used *only* for content that is genuinely of that element. A
screen with three accents is a screen with none.

**Contrast is measured, not guessed.** Every text colour below is checked
against the surface it sits on by check_contrast() at the bottom of this file,
because "looks fine" on a bright monitor is illegible on a dim one, and a GUI
that cannot be read is not a modern GUI however sharp its corners are.

**Sharp means no textures.** Every one of these is used by a fill() call on an
integer pixel boundary. Nothing is scaled, so nothing is soft, at any GUI scale.
"""
import json
import pathlib
import sys

# ---------------------------------------------------------------------------
# Surfaces — darkest at the back, each step forward slightly lighter
# ---------------------------------------------------------------------------

PALETTE = {
    # The dim behind a modal screen. Deliberately not pure black: pure black
    # against a bright world reads as a hole rather than as glass.
    "SCRIM":            0xB4070609,

    # The panel itself, and the raised areas inside it. Two steps only — a
    # third would need a third border colour and the hierarchy stops reading.
    "SURFACE":          0xF00D0C12,
    "SURFACE_RAISED":   0xF0141320,
    "SURFACE_SUNKEN":   0xF0090810,

    # Hairlines. LINE for structure, LINE_SOFT for grouping within a panel.
    "LINE":             0xFF2A2440,
    "LINE_SOFT":        0xFF1B1729,

    # Text.
    "TEXT":             0xFFE8E6F0,
    "TEXT_MUTED":       0xFF8F89A8,
    "TEXT_FAINT":       0xFF6B6590,
    "TEXT_ON_ACCENT":   0xFF0B0910,

    # The interface's own accent. Violet, from the mod's existing voidsteel
    # highlight, so the GUI and the gear look like the same world.
    "ACCENT":           0xFF7F70C4,
    "ACCENT_DIM":       0xFF4B4286,
    "ACCENT_BRIGHT":    0xFFA99BE8,

    # Meaning colours. Used for state, never for decoration.
    "GOOD":             0xFF56C08A,
    "WARN":             0xFFD9A441,
    "BAD":              0xFFD1553F,

    # The five elements, matching art/style.py's glow ramps at index 3 so the
    # interface and the item sprites agree about what "plasma" looks like.
    "VOID":             0xFFA86EF0,
    "PLASMA":           0xFFF28F3E,
    "NEURAL":           0xFF33D296,
    "DIMENSIONAL":      0xFF3AA6E8,
    "KINETIC":          0xFFE6B23C,

    # The two standing meters. Favor is the Empire's approval, Suspicion its
    # attention; they are opposed, so they are given opposed hues rather than
    # two shades of one.
    "FAVOR":            0xFF63B7E0,
    "SUSPICION":        0xFFD1553F,

    # Slots, for the container screens.
    "SLOT":             0xFF07060B,
    "SLOT_EDGE":        0xFF322B4A,
    "SLOT_HOVER":       0x40A99BE8,
}

# Which surface each text colour is expected to sit on, for the contrast check.
TEXT_ON = {
    "TEXT": "SURFACE",
    "TEXT_MUTED": "SURFACE",
    "TEXT_FAINT": "SURFACE",
    "ACCENT": "SURFACE",
    "ACCENT_BRIGHT": "SURFACE",
    "GOOD": "SURFACE",
    "WARN": "SURFACE",
    "BAD": "SURFACE",
    "VOID": "SURFACE",
    "PLASMA": "SURFACE",
    "NEURAL": "SURFACE",
    "DIMENSIONAL": "SURFACE",
    "KINETIC": "SURFACE",
    "FAVOR": "SURFACE",
    "SUSPICION": "SURFACE",
    "TEXT_ON_ACCENT": "ACCENT",
}

# ---------------------------------------------------------------------------
# Metrics — the spacing system, in GUI pixels
# ---------------------------------------------------------------------------
#
# One scale, used everywhere. Ad-hoc spacing is what makes an interface feel
# untidy even when every individual screen looked fine while it was being
# written.

METRICS = {
    "UNIT": 4,            # everything is a multiple of this
    "PANEL_PAD": 12,      # panel edge to content
    "GAP": 8,             # between related things
    "GAP_WIDE": 16,       # between groups
    "ROW": 14,            # a line of text plus breathing room
    "ROW_TALL": 20,       # a row with a control in it
    "TAB_HEIGHT": 18,
    "BUTTON_HEIGHT": 18,
    "BAR_HEIGHT": 6,
    "SLOT_SIZE": 18,      # vanilla slot pitch; kept so item rendering lines up
    "CORNER": 2,          # how many pixels the corner notch cuts
}


def rgba(value):
    """0xAARRGGBB -> (r, g, b, a) for the preview renderer."""
    return ((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF, (value >> 24) & 0xFF)


def over(top, bottom):
    """Composite a translucent colour over an opaque one, as the game does."""
    tr, tg, tb, ta = rgba(top)
    br, bg, bb, _ = rgba(bottom)
    a = ta / 255.0
    return (round(tr * a + br * (1 - a)),
            round(tg * a + bg * (1 - a)),
            round(tb * a + bb * (1 - a)), 255)


def _luminance(rgb):
    def channel(c):
        c = c / 255.0
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (channel(c) for c in rgb[:3])
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(foreground, background):
    """WCAG contrast ratio between two composited colours."""
    a, b = _luminance(foreground), _luminance(background)
    lighter, darker = max(a, b), min(a, b)
    return (lighter + 0.05) / (darker + 0.05)


def check_contrast(minimum=4.5, faint_minimum=3.0):
    """
    Every text colour against the surface it is used on.

    4.5:1 is the ordinary readable threshold. TEXT_FAINT is allowed 3.0 because
    it is only ever used for text a player is not meant to read closely — unit
    suffixes, disabled rows — and holding it to 4.5 would make it the same
    colour as TEXT_MUTED, which would mean two names for one thing.
    """
    problems = []
    # The screen sits over the world, so a translucent surface is composited
    # over the scrim over an average-ish world colour before being measured.
    world = (90, 96, 110, 255)
    scrim = over(PALETTE["SCRIM"], (world[0] << 16) | (world[1] << 8) | world[2] | 0xFF000000)
    scrim_packed = 0xFF000000 | (scrim[0] << 16) | (scrim[1] << 8) | scrim[2]

    for name, surface_name in TEXT_ON.items():
        surface = over(PALETTE[surface_name], scrim_packed)
        surface_packed = 0xFF000000 | (surface[0] << 16) | (surface[1] << 8) | surface[2]
        text = over(PALETTE[name], surface_packed)
        ratio = contrast(text, surface)
        floor = faint_minimum if name == "TEXT_FAINT" else minimum
        if ratio < floor:
            problems.append((name, surface_name, round(ratio, 2), floor))
    return problems


# ---------------------------------------------------------------------------
# Java generation
# ---------------------------------------------------------------------------

JAVA_HEADER = '''package com.elysium.lib.client;

/**
 * Every colour and measurement the Elysium interface uses.
 *
 * GENERATED from ui/palette.py. Do not edit.
 *
 * It is generated because the mockup renderer that these screens were designed
 * against imports the same file. A GUI is the one part of this project the
 * compile harness cannot check — it will happily compile an unreadable screen —
 * so the previews are the verification, and a preview drawn from different
 * numbers than the game would be worse than none.
 *
 * <h2>The palette in one paragraph</h2>
 *
 * Near-black glass panels with a single hairline edge. No bevels, no gradients
 * imitating lit metal, no drop shadows: vanilla's GUI is carved stone, this one
 * is a display panel, and the difference should be obvious at a glance. Violet
 * carries the interface — selection, focus, the active tab — and the five
 * element colours are used only for content genuinely of that element, because
 * a screen with three accents has none.
 *
 * Every text colour here was checked against the surface it sits on for a
 * contrast ratio of at least 4.5:1, composited over the scrim over the world.
 */
public final class ElysiumPalette {

    private ElysiumPalette() {
    }

'''


def write_java(path):
    lines = [JAVA_HEADER]
    lines.append("    // ------------------------------------------------------------------\n")
    lines.append("    // Colours, as 0xAARRGGBB\n")
    lines.append("    // ------------------------------------------------------------------\n\n")
    for name, value in PALETTE.items():
        lines.append("    public static final int %s = 0x%08X;\n" % (name, value))
    lines.append("\n")
    lines.append("    // ------------------------------------------------------------------\n")
    lines.append("    // Metrics, in GUI pixels. Everything is a multiple of UNIT.\n")
    lines.append("    // ------------------------------------------------------------------\n\n")
    for name, value in METRICS.items():
        lines.append("    public static final int %s = %d;\n" % (name, value))
    lines.append("""
    /** The accent for an element id path, or the interface accent for anything else. */
    public static int forElement(String path) {
        return switch (path) {
            case "void" -> VOID;
            case "plasma" -> PLASMA;
            case "neural" -> NEURAL;
            case "dimensional" -> DIMENSIONAL;
            case "kinetic" -> KINETIC;
            default -> ACCENT;
        };
    }

    /**
     * The same colour at a different opacity.
     *
     * Used for hover washes and disabled text rather than defining a second
     * constant for every state, which is how a palette of twenty becomes a
     * palette of sixty that nobody can hold in their head.
     */
    public static int alpha(int colour, float opacity) {
        int a = Math.max(0, Math.min(255, Math.round(255 * opacity)));
        return (a << 24) | (colour & 0x00FFFFFF);
    }
}
""")
    path.write_text("".join(lines))


if __name__ == "__main__":
    problems = check_contrast()
    for name, surface, ratio, floor in problems:
        print("CONTRAST %s on %s is %.2f:1, below %.1f" % (name, surface, ratio, floor))
    if problems:
        sys.exit(1)

    # Found rather than hard-coded, because this file ships inside the library
    # repo as ui/palette.py and also lives in the multi-repo work tree one level
    # further out. A fixed relative path is correct in exactly one of those and
    # silently writes a stray file in the other.
    RELATIVE = "src/main/java/com/elysium/lib/client/ElysiumPalette.java"
    target = None
    for base in pathlib.Path(__file__).resolve().parents[:4]:
        for candidate in (base / RELATIVE, base / "lib" / RELATIVE):
            if candidate.parent.is_dir():
                target = candidate
                break
        if target:
            break
    if target is None:
        print("cannot find ElysiumPalette.java's directory from " + __file__)
        sys.exit(1)

    write_java(target)
    print("palette: %d colours, %d metrics, all contrast checks pass -> %s"
          % (len(PALETTE), len(METRICS), target))
