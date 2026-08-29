package com.elysium.lib.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Every shape the Elysium interface is made of.
 *
 * <h2>Why there are no textures in here</h2>
 *
 * A GUI texture is drawn at a fixed pixel size and then scaled by the player's
 * GUI scale setting. At scale 1 it is right; at scale 3 — which is what anyone
 * on a 4K monitor is using — every edge in it has been tripled by a nearest
 * filter, and a 1-pixel border is a 3-pixel stripe. That is what makes most
 * modded interfaces look soft next to the game's own.
 *
 * Everything below is a {@code fill} on integer boundaries, so a hairline is
 * one <em>screen</em> pixel at every scale and nothing is ever resampled. It is
 * also what makes the panels resizable: nothing has a fixed nine-slice, so a
 * panel is whatever size it needs to be.
 *
 * <h2>The vocabulary</h2>
 *
 * Deliberately small. Panel, rule, bar, chip, slot, and text at three weights.
 * A toolkit with forty primitives is one where two screens solve the same
 * problem differently, so anything that could be composed from these is not
 * added here.
 *
 * <h2>Everything lands on the grid</h2>
 *
 * Positions and sizes come from {@link ElysiumPalette}'s metrics, all multiples
 * of {@code UNIT}. Ad-hoc spacing is what makes an interface feel untidy even
 * when each screen looked fine on its own.
 */
public final class ElysiumUI {

    private ElysiumUI() {
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    // ==================================================================
    // Surfaces
    // ==================================================================

    /**
     * The dim behind a modal screen.
     *
     * Drawn rather than using vanilla's {@code renderBackground} gradient,
     * which is a vertical fade tuned for the inventory and reads as a smear
     * behind a full-bleed panel.
     */
    public static void scrim(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, ElysiumPalette.SCRIM);
    }

    /**
     * A panel: glass, a hairline edge, and notched corners.
     *
     * The notch is the one piece of ornament in the whole system. Four pixels
     * of cut corner is enough to say "this is Elysium" without a texture, a
     * logo, or anything that has to be redrawn when a panel changes size.
     */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        panel(g, x, y, w, h, ElysiumPalette.SURFACE, ElysiumPalette.LINE);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int edge) {
        int c = ElysiumPalette.CORNER;

        // Body, with the four corners bitten out.
        g.fill(x + c, y, x + w - c, y + h, fill);
        g.fill(x, y + c, x + c, y + h - c, fill);
        g.fill(x + w - c, y + c, x + w, y + h - c, fill);

        // Edge: four sides, then the four diagonals across the notches.
        g.fill(x + c, y, x + w - c, y + 1, edge);
        g.fill(x + c, y + h - 1, x + w - c, y + h, edge);
        g.fill(x, y + c, x + 1, y + h - c, edge);
        g.fill(x + w - 1, y + c, x + w, y + h - c, edge);
        for (int i = 0; i < c; i++) {
            g.fill(x + c - 1 - i, y + i, x + c - i, y + i + 1, edge);
            g.fill(x + w - c + i, y + i, x + w - c + i + 1, y + i + 1, edge);
            g.fill(x + c - 1 - i, y + h - i - 1, x + c - i, y + h - i, edge);
            g.fill(x + w - c + i, y + h - i - 1, x + w - c + i + 1, y + h - i, edge);
        }
    }

    /** A raised area inside a panel — a header strip, a selected row. */
    public static void raised(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, ElysiumPalette.SURFACE_RAISED);
    }

    /** A recessed area — a list well, a text field, the slot field. */
    public static void sunken(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, ElysiumPalette.SURFACE_SUNKEN);
        g.fill(x, y, x + w, y + 1, ElysiumPalette.LINE_SOFT);
    }

    // ==================================================================
    // Lines
    // ==================================================================

    /** A horizontal hairline. One screen pixel at every GUI scale. */
    public static void rule(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, ElysiumPalette.LINE);
    }

    public static void ruleSoft(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, ElysiumPalette.LINE_SOFT);
    }

    public static void ruleVertical(GuiGraphics g, int x, int y, int h) {
        g.fill(x, y, x + 1, y + h, ElysiumPalette.LINE);
    }

    /**
     * A short accent rule under a heading.
     *
     * The device that carries the whole hierarchy: a heading with a rule under
     * it is a section, one without is a label. Two lengths, no other signal
     * needed.
     */
    public static void accentRule(GuiGraphics g, int x, int y, int w, int colour) {
        g.fill(x, y, x + w, y + 1, colour);
    }

    // ==================================================================
    // Text
    // ==================================================================

    public static void heading(GuiGraphics g, Component text, int x, int y) {
        g.drawString(font(), text, x, y, ElysiumPalette.TEXT, false);
    }

    public static void label(GuiGraphics g, Component text, int x, int y) {
        g.drawString(font(), text, x, y, ElysiumPalette.TEXT_MUTED, false);
    }

    public static void value(GuiGraphics g, Component text, int x, int y, int colour) {
        g.drawString(font(), text, x, y, colour, false);
    }

    public static void faint(GuiGraphics g, Component text, int x, int y) {
        g.drawString(font(), text, x, y, ElysiumPalette.TEXT_FAINT, false);
    }

    /** Right-aligned, for numbers in a column. */
    public static void valueRight(GuiGraphics g, Component text, int right, int y, int colour) {
        g.drawString(font(), text, right - font().width(text), y, colour, false);
    }

    public static void centred(GuiGraphics g, Component text, int centreX, int y, int colour) {
        g.drawCenteredString(font(), text, centreX, y, colour);
    }

    /**
     * No text shadow, anywhere.
     *
     * Every {@code drawString} above passes false. Vanilla's shadow exists so
     * white text survives on an arbitrary world background; on a controlled
     * dark panel it only softens the glyph edges, which is the opposite of what
     * this overhaul is for.
     */
    public static int textWidth(Component text) {
        return font().width(text);
    }

    public static int lineHeight() {
        return font().lineHeight;
    }

    // ==================================================================
    // Bars
    // ==================================================================

    /**
     * A progress bar: a sunken track and a flat fill.
     *
     * No gloss, no rounded cap, no animation. A bar's whole job is to be read
     * at a glance and compared with the one under it, and every one of those
     * flourishes makes the comparison harder.
     */
    public static void bar(GuiGraphics g, int x, int y, int w, float fraction, int colour) {
        bar(g, x, y, w, ElysiumPalette.BAR_HEIGHT, fraction, colour);
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h,
                           float fraction, int colour) {
        float clamped = Math.max(0.0F, Math.min(1.0F, fraction));
        g.fill(x, y, x + w, y + h, ElysiumPalette.SURFACE_SUNKEN);
        g.fill(x, y, x + w, y + 1, ElysiumPalette.LINE_SOFT);

        int filled = Math.round(w * clamped);
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, colour);
        }
        // A brighter cap on the leading edge, so a nearly-empty bar is still
        // visible as a bar rather than as an empty track.
        if (filled > 0 && filled < w) {
            g.fill(x + filled - 1, y, x + filled, y + h,
                    ElysiumPalette.alpha(0xFFFFFFFF, 0.35F));
        }
    }

    /**
     * A bar with a marked threshold — a standing band boundary, a level gate.
     *
     * The notch is drawn over the fill rather than under it, so it stays
     * visible once the bar passes it. A threshold you can no longer see is a
     * threshold you cannot tell you have crossed.
     */
    public static void barWithMark(GuiGraphics g, int x, int y, int w, float fraction,
                                   float mark, int colour) {
        bar(g, x, y, w, fraction, colour);
        int at = x + Math.round(w * Math.max(0.0F, Math.min(1.0F, mark)));
        g.fill(at, y - 1, at + 1, y + ElysiumPalette.BAR_HEIGHT + 1, ElysiumPalette.TEXT_FAINT);
    }

    // ==================================================================
    // Chips and slots
    // ==================================================================

    /**
     * A chip: a small labelled tag, for an element or a state.
     *
     * Outlined rather than filled, because a filled chip competes with the
     * accent rules for attention and a screen with six of them becomes a
     * fruit salad.
     *
     * @return the width it drew, so a caller can lay several in a row
     */
    public static int chip(GuiGraphics g, Component text, int x, int y, int colour) {
        int w = textWidth(text) + 8;
        int h = lineHeight() + 3;
        g.fill(x, y, x + w, y + h, ElysiumPalette.alpha(colour, 0.12F));
        g.fill(x, y, x + 1, y + h, colour);
        g.drawString(font(), text, x + 4, y + 2, colour, false);
        return w;
    }

    /**
     * An inventory slot.
     *
     * Drawn at the same 18-pixel pitch vanilla uses even though this overhaul
     * moves the slots around, because the item, its stack count and its damage
     * bar are all rendered by vanilla at that size — a differently sized slot
     * would look correct until something was in it.
     */
    public static void slot(GuiGraphics g, int x, int y, boolean hovered) {
        g.fill(x, y, x + ElysiumPalette.SLOT_SIZE, y + ElysiumPalette.SLOT_SIZE, ElysiumPalette.SLOT);
        g.fill(x, y, x + ElysiumPalette.SLOT_SIZE, y + 1, ElysiumPalette.SLOT_EDGE);
        g.fill(x, y + ElysiumPalette.SLOT_SIZE - 1, x + ElysiumPalette.SLOT_SIZE,
                y + ElysiumPalette.SLOT_SIZE, ElysiumPalette.SLOT_EDGE);
        g.fill(x, y, x + 1, y + ElysiumPalette.SLOT_SIZE, ElysiumPalette.SLOT_EDGE);
        g.fill(x + ElysiumPalette.SLOT_SIZE - 1, y, x + ElysiumPalette.SLOT_SIZE,
                y + ElysiumPalette.SLOT_SIZE, ElysiumPalette.SLOT_EDGE);
        if (hovered) {
            g.fill(x + 1, y + 1, x + ElysiumPalette.SLOT_SIZE - 1, y + ElysiumPalette.SLOT_SIZE - 1,
                    ElysiumPalette.SLOT_HOVER);
        }
    }

    /**
     * A slot that expects a particular kind of item, with a hint glyph behind.
     *
     * The hint is what stops a five-slot workstation from being a guessing
     * game, and it is drawn at 30% so it disappears the moment something real
     * is in the slot rather than showing through it.
     */
    public static void slotHinted(GuiGraphics g, int x, int y, boolean hovered,
                                  Component glyph, int colour) {
        slot(g, x, y, hovered);
        int w = textWidth(glyph);
        g.drawString(font(), glyph, x + (ElysiumPalette.SLOT_SIZE - w) / 2, y + 5,
                ElysiumPalette.alpha(colour, 0.30F), false);
    }

    // ==================================================================
    // Selection
    // ==================================================================

    /**
     * The marker on a selected row.
     *
     * A left bar plus a wash, rather than a full highlight. A full highlight
     * has to be light enough to read text on, which makes it loud; a 2-pixel
     * bar is unmistakable and changes nothing about the row's legibility.
     */
    public static void selection(GuiGraphics g, int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + h, ElysiumPalette.alpha(colour, 0.14F));
        g.fill(x, y, x + 2, y + h, colour);
    }

    /** The wash under the pointer. Fainter than selection, on purpose. */
    public static void hover(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, ElysiumPalette.alpha(ElysiumPalette.ACCENT_BRIGHT, 0.08F));
    }

    /** A focus ring, for keyboard navigation. */
    public static void focusRing(GuiGraphics g, int x, int y, int w, int h) {
        int colour = ElysiumPalette.ACCENT_BRIGHT;
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y, x + 1, y + h, colour);
        g.fill(x + w - 1, y, x + w, y + h, colour);
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    public static boolean within(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /**
     * A value formatted for a stat column.
     *
     * Two decimal places is noise on a stat sheet; a whole number and a
     * percentage are what a player actually compares.
     */
    public static Component percent(float fraction) {
        return Component.literal(Math.round(fraction * 100.0F) + "%");
    }

    // ==================================================================
    // Composites
    // ==================================================================
    //
    // The handful of arrangements that appear on more than one screen. They
    // live here rather than in each screen for the reason the mockups kept
    // demonstrating: the same arrangement written twice is the same
    // arrangement drawn two slightly different ways, and the difference is
    // never visible in the screen you are looking at — only in the one you
    // are not.
    //
    // Each mirrors a function of the same name in ui/mock.py, so a preview
    // that was approved is a preview of what the game draws.

    /** The margin a panel always leaves around itself. */
    public static final int MARGIN = 8;

    /**
     * A panel's size: what it wants, or what there is, whichever is smaller.
     *
     * Fixed widths are what hung the picker and the codex off both sides of a
     * 320-pixel screen. GUI scale 3 on a 1280x720 window gives about 427x240;
     * on 960x540 it gives 320x180. A layout that assumes the larger is broken
     * for everyone on the smaller.
     */
    public static int fit(int preferred, int available) {
        return Math.min(preferred, available - MARGIN * 2);
    }

    /**
     * Text cut to fit, with an ellipsis.
     *
     * A heading's width is decided by the layout and its content by a
     * translator: a string that fits a third of a 427-pixel panel in English
     * need not fit a third of a 320-pixel one in German. Overflowing silently
     * is the wrong answer for the same reason a fixed width was.
     */
    public static Component truncate(Component text, int w) {
        if (textWidth(text) <= w) {
            return text;
        }
        String plain = text.getString();
        while (!plain.isEmpty() && font().width(plain + "..") > w) {
            plain = plain.substring(0, plain.length() - 1);
        }
        return Component.literal(plain + "..");
    }

    /**
     * Prose, broken to fit a column, stopping at {@code maxY} and saying so.
     *
     * Vanilla's {@code drawWordWrap} would do the wrapping, but it has no
     * notion of a bottom edge — it draws every line it produces, and a
     * description one line too long paints straight over the footer. Every
     * mockup with a sentence on it had that bug before this existed.
     *
     * The lines are all measured before any are drawn, so that a paragraph
     * which does not fit can end in an ellipsis rather than simply stopping.
     * That distinction matters more than it looks: the picker at 320 pixels cut
     * a passive's description off after two words, and the result read as a
     * complete if terse sentence rather than as truncation. Text that has been
     * cut must look cut, or the player is misinformed rather than underinformed.
     *
     * @return the y below the last line drawn, so callers can stack
     */
    public static int wrapped(GuiGraphics g, Component text, int x, int y, int w,
                              int colour, int maxY) {
        int lineGap = 1;
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.getString().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (font().width(candidate) > w && line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }

        int room = Math.max(0, (maxY - y + lineGap) / (lineHeight() + lineGap));
        int shown = Math.min(room, lines.size());
        for (int i = 0; i < shown; i++) {
            String drawn = lines.get(i);
            if (i == shown - 1 && shown < lines.size()) {
                drawn = truncate(Component.literal(drawn + " ..."), w).getString();
            }
            g.drawString(font(), drawn, x, y, colour, false);
            y += lineHeight() + lineGap;
        }
        return y;
    }

    /** A heading with an accent rule under it. Returns the y below it. */
    public static int section(GuiGraphics g, Component title, int x, int y, int w, int colour) {
        g.drawString(font(), truncate(title, w), x, y, ElysiumPalette.TEXT, false);
        int tick = Math.min(w, 24);
        accentRule(g, x, y + lineHeight() + 2, tick, colour);
        ruleSoft(g, x + tick, y + lineHeight() + 2, w - tick);
        return y + lineHeight() + 2 + ElysiumPalette.GAP;
    }

    public static int section(GuiGraphics g, Component title, int x, int y, int w) {
        return section(g, title, x, y, w, ElysiumPalette.ACCENT);
    }

    /**
     * A tab strip: labels on a rule, the active one carrying an accent
     * underline.
     *
     * An underline rather than a raised tab shape, because a raised tab needs
     * a join to the panel below it, and that join is the fiddliest thing in
     * any interface to keep right at every width.
     */
    public static void tabs(GuiGraphics g, Component[] labels, int x, int y, int w, int active) {
        int step = w / labels.length;
        for (int i = 0; i < labels.length; i++) {
            int tx = x + i * step;
            if (i == active) {
                g.fill(tx, y, tx + step, y + ElysiumPalette.TAB_HEIGHT,
                        ElysiumPalette.alpha(ElysiumPalette.ACCENT, 0.10F));
            }
            centred(g, labels[i], tx + step / 2, y + 5,
                    i == active ? ElysiumPalette.TEXT : ElysiumPalette.TEXT_MUTED);
        }
        rule(g, x, y + ElysiumPalette.TAB_HEIGHT, w);
        accentRule(g, x + active * step, y + ElysiumPalette.TAB_HEIGHT, step,
                ElysiumPalette.ACCENT);
    }

    /** The index of the tab under the pointer, or -1. */
    public static int tabAt(double mouseX, double mouseY, int x, int y, int w, int count) {
        if (count <= 0 || !within(mouseX, mouseY, x, y, w, ElysiumPalette.TAB_HEIGHT)) {
            return -1;
        }
        int step = Math.max(1, w / count);
        int index = (int) ((mouseX - x) / step);
        return Math.max(0, Math.min(count - 1, index));
    }

    /**
     * A flat button. Three states and no more.
     *
     * Disabled, hovered, focused and pressed at once is four booleans and
     * sixteen appearances, and nobody has ever needed the sixteenth.
     */
    public static void button(GuiGraphics g, Component text, int x, int y, int w,
                              boolean enabled, boolean hovered) {
        int h = ElysiumPalette.BUTTON_HEIGHT;
        // Truncated, because a button's width is decided by the layout and its
        // label by a translator. English "Confirm" fits 70 pixels; the German
        // for it need not, and a label that runs out through both walls of its
        // own box is the most visible thing on the screen.
        Component fitted = truncate(text, w - 6);
        if (!enabled) {
            panel(g, x, y, w, h, ElysiumPalette.SURFACE_SUNKEN, ElysiumPalette.LINE_SOFT);
            centred(g, fitted, x + w / 2, y + (h - 7) / 2, ElysiumPalette.TEXT_FAINT);
            return;
        }
        panel(g, x, y, w, h, ElysiumPalette.SURFACE_RAISED,
                hovered ? ElysiumPalette.ACCENT : ElysiumPalette.LINE);
        if (hovered) {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1,
                    ElysiumPalette.alpha(ElysiumPalette.ACCENT, 0.10F));
        }
        centred(g, fitted, x + w / 2, y + (h - 7) / 2,
                hovered ? ElysiumPalette.TEXT : ElysiumPalette.TEXT_MUTED);
    }

    /** The side of a stepper, in pixels. */
    public static final int STEPPER = 10;

    /**
     * A 10x10 "+" beside a stat row.
     *
     * Its own primitive rather than a small button, because a button is 18
     * pixels tall by definition and a stat row is 14 — reusing one here is
     * what made six of them overlap and spill out of the panel in the first
     * mockup.
     */
    public static void stepper(GuiGraphics g, int x, int y, boolean enabled, boolean hovered) {
        int colour = enabled ? ElysiumPalette.ACCENT : ElysiumPalette.LINE_SOFT;
        g.fill(x, y, x + STEPPER, y + STEPPER,
                ElysiumPalette.alpha(colour, enabled ? (hovered ? 0.28F : 0.16F) : 0.08F));
        g.fill(x, y, x + STEPPER, y + 1, colour);
        g.fill(x, y + STEPPER - 1, x + STEPPER, y + STEPPER, colour);
        g.fill(x, y, x + 1, y + STEPPER, colour);
        g.fill(x + STEPPER - 1, y, x + STEPPER, y + STEPPER, colour);
        int glyph = enabled ? ElysiumPalette.TEXT : ElysiumPalette.TEXT_FAINT;
        g.fill(x + 4, y + 2, x + 6, y + 8, glyph);
        g.fill(x + 2, y + 4, x + 8, y + 6, glyph);
    }

    /**
     * The height of one stat row.
     *
     * Named because a screen has to know it to size its panel, and guessing is
     * how the last row ended up underneath the footer.
     */
    public static int statRowHeight() {
        return lineHeight() + 1 + 2 + 4;
    }

    /**
     * A stat: name left, value right, a thin bar beneath.
     *
     * A negative {@code fraction} draws no bar, for the rows that have no
     * meaningful maximum to be a fraction of.
     *
     * @return the y of the next row
     */
    public static int statRow(GuiGraphics g, Component name, Component value,
                              int x, int y, int w, float fraction, int colour) {
        g.drawString(font(), name, x, y, ElysiumPalette.TEXT_MUTED, false);
        valueRight(g, value, x + w, y, colour);
        if (fraction >= 0.0F) {
            bar(g, x, y + lineHeight() + 1, w, 2, fraction, colour);
        }
        return y + statRowHeight();
    }

    /**
     * A backing plate for text drawn over the world.
     *
     * The HUD has no panel behind it, so its text sits on whatever the player
     * happens to be looking at. A plate is the smallest thing that makes it
     * legible against snow and against a cave mouth both.
     */
    public static void plate(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, ElysiumPalette.alpha(ElysiumPalette.SURFACE, 0.72F));
        g.fill(x, y, x + 1, y + h, ElysiumPalette.alpha(ElysiumPalette.ACCENT, 0.55F));
    }
}
