package com.elysium.lib.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A button drawn in the Elysium interface, as a widget.
 *
 * <h2>Why this exists at all</h2>
 *
 * {@link ElysiumUI#button} already draws one, and every container screen in
 * the mod uses it. But a container screen draws its own chrome in
 * {@code renderBg} and handles its own clicks, and a plain {@code Screen} does
 * not: it adds {@link net.minecraft.client.gui.components.Button} widgets,
 * which bring vanilla's carved-stone texture with them and undo the entire
 * palette in one line.
 *
 * <p>That is not a hypothetical. Three of this project's screens were written
 * that way and sat in the pack looking like a different mod from the ones
 * beside them, each with a note in its javadoc promising to fix it later.
 * There is no way to use the kit from a {@code Screen} without a widget, so
 * here is the widget.
 *
 * <h2>Two lines, not one</h2>
 *
 * A chooser row says what a thing is and what it costs, and cramming both into
 * one centred label gives you a sentence nobody reads. With a {@code detail}
 * the button grows to two lines, the label goes left at full contrast, and the
 * detail sits under it faint. Without one it is exactly
 * {@link ElysiumUI#button}, so a button is a button wherever it appears.
 *
 * <h2>The accent</h2>
 *
 * A row may carry one — the element of the thing it leads to, the colour of
 * the world on the other side of the gate. It shows as a rule down the left
 * edge and nothing else, because a row tinted end to end is a row that has
 * stopped being readable in order to be decorative.
 */
public class ElysiumButton extends AbstractButton {

    /** A button with no detail line is this tall; vanilla's is 20. */
    public static final int HEIGHT = ElysiumPalette.BUTTON_HEIGHT;

    /** A button with a detail line. Two lines of text plus the same padding. */
    public static final int HEIGHT_TALL = ElysiumPalette.BUTTON_HEIGHT + 10;

    private final OnPress onPress;
    @Nullable
    private final Component detail;
    private final int accent;

    /** What a press does. Separate from vanilla's so nothing here depends on it. */
    @FunctionalInterface
    public interface OnPress {
        void pressed(ElysiumButton button);
    }

    private ElysiumButton(int x, int y, int w, int h, Component label,
                          @Nullable Component detail, int accent, OnPress onPress) {
        super(x, y, w, h, label);
        this.detail = detail;
        this.accent = accent;
        this.onPress = onPress;
    }

    /** One line, centred: a confirm, a cancel, a pager. */
    public static ElysiumButton plain(int x, int y, int w, Component label, OnPress onPress) {
        return new ElysiumButton(x, y, w, HEIGHT, label, null, 0, onPress);
    }

    /** A chooser row: a name on the left, what it costs underneath. */
    public static ElysiumButton row(int x, int y, int w, Component label,
                                    @Nullable Component detail, int accent, OnPress onPress) {
        return new ElysiumButton(x, y, w, detail == null ? HEIGHT : HEIGHT_TALL,
                label, detail, accent, onPress);
    }

    @Override
    public void onPress() {
        onPress.pressed(this);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean lit = isHovered() || isFocused();
        if (detail == null) {
            ElysiumUI.button(g, getMessage(), getX(), getY(), getWidth(), active, lit);
            return;
        }
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        if (!active) {
            ElysiumUI.panel(g, x, y, w, h, ElysiumPalette.SURFACE_SUNKEN, ElysiumPalette.LINE_SOFT);
        } else {
            ElysiumUI.panel(g, x, y, w, h, ElysiumPalette.SURFACE_RAISED,
                    lit ? ElysiumPalette.ACCENT : ElysiumPalette.LINE);
            if (lit) {
                ElysiumUI.hover(g, x, y, w, h);
            }
        }
        if (accent != 0) {
            g.fill(x + 1, y + 2, x + 2, y + h - 2,
                    active ? accent : ElysiumPalette.alpha(accent, 0.35F));
        }
        int textX = x + ElysiumPalette.GAP;
        int room = w - ElysiumPalette.GAP * 2;
        ElysiumUI.value(g, ElysiumUI.truncate(getMessage(), room), textX, y + 5,
                active ? (lit ? ElysiumPalette.TEXT : ElysiumPalette.TEXT_MUTED)
                       : ElysiumPalette.TEXT_FAINT);
        ElysiumUI.faint(g, ElysiumUI.truncate(detail, room), textX,
                y + 5 + ElysiumUI.lineHeight() + ElysiumUI.LINE_GAP);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
