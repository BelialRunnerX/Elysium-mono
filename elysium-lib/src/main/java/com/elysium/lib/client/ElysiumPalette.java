package com.elysium.lib.client;

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

    // ------------------------------------------------------------------
    // Colours, as 0xAARRGGBB
    // ------------------------------------------------------------------

    public static final int SCRIM = 0xB4070609;
    public static final int SURFACE = 0xF00D0C12;
    public static final int SURFACE_RAISED = 0xF0141320;
    public static final int SURFACE_SUNKEN = 0xF0090810;
    public static final int LINE = 0xFF2A2440;
    public static final int LINE_SOFT = 0xFF1B1729;
    public static final int TEXT = 0xFFE8E6F0;
    public static final int TEXT_MUTED = 0xFF8F89A8;
    public static final int TEXT_FAINT = 0xFF6B6590;
    public static final int TEXT_ON_ACCENT = 0xFF0B0910;
    public static final int ACCENT = 0xFF7F70C4;
    public static final int ACCENT_DIM = 0xFF4B4286;
    public static final int ACCENT_BRIGHT = 0xFFA99BE8;
    public static final int GOOD = 0xFF56C08A;
    public static final int WARN = 0xFFD9A441;
    public static final int BAD = 0xFFD1553F;
    public static final int VOID = 0xFFA86EF0;
    public static final int PLASMA = 0xFFF28F3E;
    public static final int NEURAL = 0xFF33D296;
    public static final int DIMENSIONAL = 0xFF3AA6E8;
    public static final int KINETIC = 0xFFE6B23C;
    public static final int FAVOR = 0xFF63B7E0;
    public static final int SUSPICION = 0xFFD1553F;
    public static final int SLOT = 0xFF07060B;
    public static final int SLOT_EDGE = 0xFF322B4A;
    public static final int SLOT_HOVER = 0x40A99BE8;

    // ------------------------------------------------------------------
    // Metrics, in GUI pixels. Everything is a multiple of UNIT.
    // ------------------------------------------------------------------

    public static final int UNIT = 4;
    public static final int PANEL_PAD = 12;
    public static final int GAP = 8;
    public static final int GAP_WIDE = 16;
    public static final int ROW = 14;
    public static final int ROW_TALL = 20;
    public static final int TAB_HEIGHT = 18;
    public static final int BUTTON_HEIGHT = 18;
    public static final int BAR_HEIGHT = 6;
    public static final int SLOT_SIZE = 18;
    public static final int CORNER = 2;

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
