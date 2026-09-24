package com.elysium.lib.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * "Pick one of these": a titled panel, a list of rows, and a pager.
 *
 * <h2>The shape this is</h2>
 *
 * Three screens in this project were already this screen — where a rocket
 * goes, where a waygate goes, where a crate goes — written three times, each
 * with vanilla buttons and a centred title in a colour picked by hand, and
 * each carrying a javadoc note promising to be rethemed later. Written a
 * fourth time for the tech ages it would have been four.
 *
 * <p>So it is written once here. A module that needs a chooser answers three
 * questions — what are the rows, what happens when one is pressed, and what is
 * the line under the title — and gets the chrome, the paging, the keyboard,
 * the narration and the empty state for nothing.
 *
 * <h2>What it does not do</h2>
 *
 * It does not scroll. A wheel-scrolled list needs a scrollbar, a thumb, a
 * momentum feel and a hit region that survives resizing, and every one of
 * those is a thing to get subtly wrong. Paging is a button, and a button is a
 * thing that already works. The pager rolls round rather than offering a
 * back and a forward, because with two buttons one of them is disabled most
 * of the time and a control that is usually dead reads as broken.
 *
 * <h2>Sizing</h2>
 *
 * The panel is as tall as its content and no taller, and if the content would
 * not fit the screen the number of rows shrinks until it does. A chooser that
 * runs off the bottom of a 720p window is the single most common way a modded
 * list screen fails, and it fails invisibly for everyone whose monitor is
 * bigger than the author's.
 */
public abstract class ElysiumChooser extends Screen {

    /** One row of the list. */
    public record Choice(Component label, @Nullable Component detail, int accent, boolean enabled) {

        public static Choice of(Component label) {
            return new Choice(label, null, 0, true);
        }

        public static Choice of(Component label, @Nullable Component detail) {
            return new Choice(label, detail, 0, true);
        }

        public static Choice of(Component label, @Nullable Component detail, int accent) {
            return new Choice(label, detail, accent, true);
        }
    }

    /** How many rows a chooser shows before it pages, given the room. */
    public static final int ROWS = 7;

    /** The index the visible page starts at. */
    private int first;

    /** The page's rows, resolved once per {@link #init()} so render agrees with it. */
    private List<Choice> shown = List.of();

    /** What has been typed, when {@link #filterable()}. */
    private String typed = "";

    /**
     * Which of {@link #choices()} survived the filter, by their own index.
     *
     * <p>Indices rather than rows, and this is the important part: whatever a
     * subclass is handed by {@link #chose} is an index into its own list, the
     * one it built, filtered or not. A chooser that handed back a position in
     * a filtered page would make every screen using it responsible for
     * undoing a filter it never asked for, and one of them would get it wrong
     * and act on the wrong row — which is the kind of bug that only happens
     * to somebody who typed something first.
     */
    private List<Integer> surviving = List.of();
    private int panelX;
    private int panelY;
    private int panelH;
    private int listY;

    protected ElysiumChooser(Component title) {
        super(title);
    }

    // ------------------------------------------------------------------
    // What a subclass answers
    // ------------------------------------------------------------------

    /** Every row, in order. Read once per rebuild, so it may be computed. */
    protected abstract List<Choice> choices();

    /** A row was pressed. The index is into {@link #choices()}, not the page. */
    protected abstract void chose(int index);

    /** The line under the title — a fuel gauge, a count. Null for none. */
    @Nullable
    protected Component status() {
        return null;
    }

    /** What to say when there is nothing to choose. Null to say nothing. */
    @Nullable
    protected Component nothing() {
        return null;
    }

    /** A last row that is not one of the choices — "Land here", "Cancel". */
    @Nullable
    protected Component footer() {
        return null;
    }

    /** The footer was pressed. */
    protected void footerPressed() {
    }

    /** How wide the panel is. Widen it for rows with long detail lines. */
    protected int panelWidth() {
        return 240;
    }

    /**
     * Whether typing narrows the list.
     *
     * <p>Off by default, because on a list of six places a filter is a thing
     * that can go wrong for no gain. On a list of a hundred and forty biomes
     * it is the only reason anybody opens the screen: twenty pages at seven
     * rows is not a list, it is a filing cabinet.
     *
     * <h2>Why there is no text box</h2>
     *
     * A text box means a caret, a selection, a clipboard, a click-to-position
     * and a focus ring, and every one of those is a thing to get subtly wrong
     * — the same argument this class already makes about scrollbars. Typing
     * straight into the screen is the whole interaction with none of the
     * surface: letters narrow, backspace widens, escape clears before it
     * closes. What was typed is shown where the status line goes, so nothing
     * is hidden state.
     */
    protected boolean filterable() {
        return false;
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    /**
     * Whether a row survives what has been typed.
     *
     * <p>Both lines, because a biome's row shows a pretty name and its id, and
     * a player who types "minecraft" means the ones from the base game.
     */
    private boolean survives(Choice choice) {
        if (typed.isEmpty()) {
            return true;
        }
        String wanted = typed.toLowerCase(java.util.Locale.ROOT);
        if (choice.label().getString().toLowerCase(java.util.Locale.ROOT)
                .contains(wanted)) {
            return true;
        }
        return choice.detail() != null && choice.detail().getString()
                .toLowerCase(java.util.Locale.ROOT).contains(wanted);
    }

    @Override
    protected void init() {
        List<Choice> everything = choices();
        List<Choice> all = new java.util.ArrayList<>(everything.size());
        List<Integer> kept = new java.util.ArrayList<>(everything.size());
        for (int at = 0; at < everything.size(); at++) {
            if (survives(everything.get(at))) {
                all.add(everything.get(at));
                kept.add(at);
            }
        }
        surviving = List.copyOf(kept);
        int pad = ElysiumPalette.PANEL_PAD;
        int w = Math.min(panelWidth(), width - ElysiumPalette.GAP_WIDE * 2);
        boolean tall = all.stream().anyMatch(choice -> choice.detail() != null);
        int rowH = (tall ? ElysiumButton.HEIGHT_TALL : ElysiumButton.HEIGHT) + ElysiumPalette.UNIT;

        int head = ElysiumPalette.ROW + (statusShown() == null ? 0 : ElysiumPalette.ROW)
                + ElysiumPalette.GAP;
        int foot = (footer() == null ? 0 : rowOf(ElysiumButton.HEIGHT))
                + (all.size() > ROWS ? rowOf(ElysiumButton.HEIGHT) : 0);
        int room = height - ElysiumPalette.GAP_WIDE * 2 - pad * 2 - head - foot;
        int fits = Math.max(1, room / rowH);
        int count = Math.min(Math.min(ROWS, fits), Math.max(0, all.size() - first));

        // A page that starts past the end is a page the last rebuild left
        // behind — the list can shrink under us while the screen is open.
        if (count == 0 && !all.isEmpty()) {
            first = 0;
            count = Math.min(Math.min(ROWS, fits), all.size());
        }
        shown = all.subList(first, first + count);

        // An empty list still needs somewhere to say so. Without this the
        // panel is exactly its chrome and the sentence is drawn in the bottom
        // padding, half over the border — which the preview showed and no
        // amount of reading the arithmetic would have.
        int saying = count == 0 && nothing() != null ? ElysiumPalette.ROW_TALL : 0;

        panelH = pad * 2 + head + count * rowH + saying + foot;
        panelX = (width - w) / 2;
        panelY = (height - panelH) / 2;
        listY = panelY + pad + head;

        int x = panelX + pad;
        int inner = w - pad * 2;
        int y = listY;
        for (int row = 0; row < count; row++) {
            int index = first + row;
            Choice choice = shown.get(row);
            int theirs = surviving.get(index);
            ElysiumButton button = ElysiumButton.row(x, y, inner, choice.label(),
                    choice.detail(), choice.accent(), b -> chose(theirs));
            button.active = choice.enabled();
            addRenderableWidget(button);
            y += rowH;
        }
        if (all.size() > ROWS) {
            addRenderableWidget(ElysiumButton.plain(x, y, inner, pageLabel(all.size(), count),
                    b -> page(all.size())));
            y += rowOf(ElysiumButton.HEIGHT);
        }
        Component footer = footer();
        if (footer != null) {
            addRenderableWidget(ElysiumButton.plain(x, y, inner, footer, b -> footerPressed()));
        }
    }

    private static int rowOf(int height) {
        return height + ElysiumPalette.UNIT;
    }

    private Component pageLabel(int total, int count) {
        return Component.translatable("elysium.ui.page",
                first + 1, first + count, total);
    }

    private void page(int total) {
        first += shown.size();
        if (first >= total) {
            first = 0;
        }
        rebuildWidgets();
    }

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ElysiumUI.scrim(g, width, height);
        int w = Math.min(panelWidth(), width - ElysiumPalette.GAP_WIDE * 2);
        ElysiumUI.panel(g, panelX, panelY, w, panelH);

        int x = panelX + ElysiumPalette.PANEL_PAD;
        int inner = w - ElysiumPalette.PANEL_PAD * 2;
        ElysiumUI.heading(g, ElysiumUI.truncate(title, inner), x, panelY + ElysiumPalette.PANEL_PAD);
        int y = panelY + ElysiumPalette.PANEL_PAD + ElysiumPalette.ROW;
        Component status = statusShown();
        if (status != null) {
            ElysiumUI.label(g, ElysiumUI.truncate(status, inner), x, y);
            y += ElysiumPalette.ROW;
        }
        ElysiumUI.accentRule(g, x, y, ElysiumPalette.GAP_WIDE, ElysiumPalette.ACCENT);
        ElysiumUI.ruleSoft(g, x + ElysiumPalette.GAP_WIDE, y, inner - ElysiumPalette.GAP_WIDE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Component nothing = nothing();
        if (shown.isEmpty() && nothing != null) {
            ElysiumUI.centred(g, nothing, width / 2, listY, ElysiumPalette.TEXT_MUTED);
        }
    }

    // ------------------------------------------------------------------
    // Typing
    // ------------------------------------------------------------------

    @Override
    public boolean charTyped(char letter, int modifiers) {
        if (filterable() && letter >= ' ' && letter != 127) {
            narrow(typed + letter);
            return true;
        }
        return super.charTyped(letter, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (filterable() && key == 259 && !typed.isEmpty()) {
            narrow(typed.substring(0, typed.length() - 1));
            return true;
        }
        // Escape clears what was typed before it closes the screen, because a
        // player who has narrowed to nothing and presses escape means "undo
        // that", and losing the whole screen instead is the most annoying
        // possible reading of it.
        if (filterable() && key == 256 && !typed.isEmpty()) {
            narrow("");
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    /**
     * Take what was typed and start again at the first page.
     *
     * <p>Not doing this is the bug: filter down to two rows while looking at
     * page nine and the page index is past the end of the list, so the screen
     * shows nothing while the count underneath it says two.
     */
    private void narrow(String now) {
        typed = now;
        first = 0;
        rebuildWidgets();
    }

    /** What has been typed so far, for a subclass that wants to say so. */
    protected String typed() {
        return typed;
    }

    /**
     * The one line under the title, whatever it currently is.
     *
     * <p>One accessor rather than two because {@link #init()} budgets a row
     * for it and {@link #renderBackground} draws it, and those two deciding
     * separately is how a panel ends up a row shorter than what is drawn in
     * it. A chooser with no status of its own that is being typed into has a
     * line where it did not before, and nothing else in the layout knows that
     * unless it comes from here.
     */
    @Nullable
    private Component statusShown() {
        if (filterable() && !typed.isEmpty()) {
            return Component.translatable("elysium.ui.typed", typed, surviving.size());
        }
        return status();
    }

    /** The world keeps running behind a chooser: it is a console, not a menu. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
