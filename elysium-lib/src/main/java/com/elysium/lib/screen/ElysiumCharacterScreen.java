package com.elysium.lib.screen;

import com.elysium.lib.character.ElysiumClass;
import com.elysium.lib.character.ElysiumRace;
import com.elysium.lib.client.ElysiumPalette;
import com.elysium.lib.client.ElysiumUI;
import com.elysium.lib.network.CharacterSheet;
import com.elysium.lib.network.ElysiumPayloads;
import com.elysium.lib.standing.ElysiumStanding;
import com.elysium.lib.stats.ElysiumStat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The character screen: a picker on first join, a tabbed sheet thereafter.
 *
 * <h2>Two modes, one screen</h2>
 *
 * A player who has not chosen sees the races and classes and cannot leave until
 * they pick — {@link #shouldCloseOnEsc()} returns false and there is no cancel,
 * because a character with no race is a character with no stats, and every
 * other system would carry a null check forever to accommodate one.
 *
 * A player who has chosen sees three tabs over one header. Same screen, because
 * it is the same question asked twice: what is this character.
 *
 * <h2>Why it draws itself instead of using widgets</h2>
 *
 * The previous version was fifteen vanilla {@code Button}s, and it looked like
 * fifteen vanilla buttons — which is the whole thing this overhaul is about. A
 * vanilla button is a nine-sliced texture, so at GUI scale 3 its one-pixel
 * border is a three-pixel stripe and its corners are visibly resampled. Every
 * shape here is a {@code fill} on an integer boundary, so a hairline is one
 * screen pixel at every scale.
 *
 * The cost is hit-testing, which widgets would have done. It is paid by
 * {@link Hit}: the render pass appends a rectangle and an action for every live
 * thing it draws, and {@link #mouseClicked} walks that list. Rebuilding it each
 * frame rather than in {@code init} is deliberate — the list is then, by
 * construction, a description of what is currently on screen, and a control
 * cannot be clickable in a place it is not drawn or drawn in a place it is not
 * clickable. That desynchronisation is the classic bug in hand-drawn UI, and
 * this is the cheapest way to make it unrepresentable.
 *
 * <h2>Nothing is predicted locally</h2>
 *
 * Spending a point sends a packet and changes nothing on screen. The server
 * answers with a fresh sheet. A stat that briefly reads one point high because
 * the packet was refused is worse than a number that updates a tick late.
 */
public class ElysiumCharacterScreen extends Screen {

    /** A live rectangle, recorded while drawing and consulted while clicking. */
    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    private static final int TAB_CHARACTER = 0;
    private static final int TAB_STATS = 1;
    private static final int TAB_STANDING = 2;

    private static final Component[] TAB_LABELS = {
            Component.translatable("elysium.screen.tab.character"),
            Component.translatable("elysium.screen.tab.stats"),
            Component.translatable("elysium.screen.tab.standing"),
    };

    private final CharacterSheet.Parsed sheet;
    private final int unspent;

    private final List<Hit> hits = new ArrayList<>();

    private int tab = TAB_CHARACTER;

    private ElysiumRace pickedRace;
    private ElysiumClass pickedClass;

    /** What the picker's description pane is currently describing. */
    private Component focusTitle = Component.empty();
    private Component focusBody = Component.empty();
    private Component focusDetail = Component.empty();

    public ElysiumCharacterScreen(CharacterSheet.Parsed sheet, int unspent) {
        super(Component.translatable("elysium.screen.character"));
        this.sheet = sheet;
        this.unspent = unspent;
        this.pickedRace = sheet.race();
        this.pickedClass = sheet.job();
    }

    /**
     * The world keeps running behind this screen.
     *
     * A pause screen in single player would stop the tick that granted the
     * level that opened it, and in multiplayer pausing is not on offer anyway —
     * better that both behave the same.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return sheet.chosen();
    }

    // ==================================================================
    // Render
    // ==================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        ElysiumUI.scrim(g, this.width, this.height);

        if (sheet.chosen()) {
            renderSheet(g, mouseX, mouseY);
        } else {
            renderPicker(g, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------
    // The sheet
    // ------------------------------------------------------------------

    private void renderSheet(GuiGraphics g, int mouseX, int mouseY) {
        int line = ElysiumUI.lineHeight();
        // The narrower padding, as on the reforge table. Twelve rows of stats
        // are 96 pixels that cannot be negotiated, and the whole panel has to
        // fit a 720p window at GUI scale 3, which is 240 tall. With PANEL_PAD
        // this measures 228 against a 224 budget — the same four-pixel overrun
        // the reforge table had, and the same fix.
        int pad = ElysiumPalette.GAP;
        int gap = ElysiumPalette.GAP;

        // The panel is measured from its parts, never chosen. Every mockup
        // where something ran off the bottom was a panel whose height was
        // picked because it looked about right.
        int header = line + 3 + ElysiumPalette.BAR_HEIGHT + 2 + line;
        int bodyHeight = bodyHeight();
        int footer = gap + 1 + gap + line;
        int ph = pad * 2 + header + gap + ElysiumPalette.TAB_HEIGHT + gap + 2 + bodyHeight + footer;

        int pw = ElysiumUI.fit(300, this.width);
        ph = Math.min(ph, this.height - ElysiumUI.MARGIN * 2);
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        ElysiumUI.panel(g, px, py, pw, ph);

        int x = px + pad;
        int y = py + pad;
        int inner = pw - pad * 2;

        // Header: who and what level, then the bar toward the next one.
        ElysiumUI.heading(g, ElysiumUI.truncate(identity(), inner - 40), x, y);
        ElysiumUI.valueRight(g, Component.translatable("elysium.screen.level", sheet.level()),
                x + inner, y, ElysiumPalette.ACCENT);
        y += line + 3;
        ElysiumUI.bar(g, x, y, inner, sheet.levelProgress(), ElysiumPalette.ACCENT);
        y += ElysiumPalette.BAR_HEIGHT + 2;
        ElysiumUI.faint(g, Component.translatable("elysium.screen.xp", sheet.xp(), sheet.xpNext()), x, y);
        y += line + gap;

        // Tabs.
        int tabsY = y;
        ElysiumUI.tabs(g, TAB_LABELS, x, tabsY, inner, tab);
        int step = inner / TAB_LABELS.length;
        for (int i = 0; i < TAB_LABELS.length; i++) {
            int index = i;
            hits.add(new Hit(x + i * step, tabsY, step, ElysiumPalette.TAB_HEIGHT,
                    () -> tab = index));
        }
        y = tabsY + ElysiumPalette.TAB_HEIGHT + gap + 2;

        int bodyBottom = py + ph - pad - footer;
        switch (tab) {
            case TAB_STATS -> renderStatsTab(g, x, y, inner, bodyBottom, mouseX, mouseY);
            case TAB_STANDING -> renderStandingTab(g, x, y, inner, bodyBottom);
            default -> renderCharacterTab(g, x, y, inner, bodyBottom);
        }

        // Footer: a rule and one line of hint.
        int fy = py + ph - pad - line;
        ElysiumUI.rule(g, x, fy - gap, inner);
        ElysiumUI.faint(g, Component.translatable("elysium.screen.close_hint"), x, fy);
        if (unspent > 0) {
            ElysiumUI.valueRight(g, Component.translatable("elysium.screen.points", unspent),
                    x + inner, fy, ElysiumPalette.WARN);
        }
    }

    /**
     * How tall the body is — the same for every tab, deliberately.
     *
     * Sizing the panel to whichever tab is showing would make it grow and
     * shrink as the player clicks along the strip, which moves the strip itself
     * out from under the pointer: click "Stats" and the panel gets taller,
     * every tab moves up, and the next click lands somewhere else. So the panel
     * is measured once, against the tallest tab, and the other two are laid out
     * to fit inside that.
     *
     * The tallest is Stats, and it is the only one whose height is not a matter
     * of taste: twelve stats in two columns is six rows, and a row is what a
     * row is.
     */
    private int bodyHeight() {
        int rows = Math.max(1, (ElysiumStat.REGISTRY.all().size() + 1) / 2);
        return ElysiumUI.lineHeight() + ElysiumPalette.GAP + rows * ElysiumUI.statRowHeight();
    }

    /**
     * Who this character is, in words: the race and class and their passives.
     *
     * Two columns rather than one stacked pair. Stacked, the two descriptions
     * plus their passives measured 124 pixels against a body of 113 and the
     * class passive lost its last line — which the wrapper handles silently, so
     * it would have shipped. Side by side each half has the full height and the
     * layout stops depending on how wordy a class description happens to be.
     */
    private void renderCharacterTab(GuiGraphics g, int x, int y, int w, int bottom) {
        ElysiumRace race = sheet.race();
        ElysiumClass job = sheet.job();
        if (race == null || job == null) {
            return;
        }

        int col = (w - ElysiumPalette.GAP_WIDE) / 2;
        describe(g, race.getDisplayName(), race.getDescription(),
                race.getPassive().getDisplayName(), race.getPassive().getDescription(),
                x, y, col, bottom);
        describe(g, job.getDisplayName(), job.getDescription(),
                job.getPassive().getDisplayName(), job.getPassive().getDescription(),
                x + col + ElysiumPalette.GAP_WIDE, y, col, bottom);

        // A hairline between them, so two columns read as two things rather
        // than as one paragraph that happens to have a gap in it.
        ElysiumUI.ruleVertical(g, x + col + ElysiumPalette.GAP_WIDE / 2, y, bottom - y);
    }

    private void describe(GuiGraphics g, Component title, Component body,
                          Component passive, Component detail,
                          int x, int y, int w, int bottom) {
        y = ElysiumUI.section(g, title, x, y, w);
        y = ElysiumUI.wrapped(g, body, x, y, w, ElysiumPalette.TEXT_MUTED, bottom);
        y += ElysiumPalette.GAP;
        if (y + ElysiumUI.lineHeight() > bottom) {
            return;
        }
        ElysiumUI.chip(g, passive, x, y, ElysiumPalette.ACCENT);
        y += ElysiumUI.lineHeight() + 5;
        ElysiumUI.wrapped(g, detail, x, y, w, ElysiumPalette.TEXT_FAINT, bottom);
    }

    /** The twelve stats in two columns, with a stepper each while points remain. */
    private void renderStatsTab(GuiGraphics g, int x, int y, int w, int bottom,
                                int mouseX, int mouseY) {
        int line = ElysiumUI.lineHeight();
        boolean canSpend = unspent > 0;

        ElysiumUI.value(g, canSpend
                        ? Component.translatable("elysium.screen.points", unspent)
                        : Component.translatable("elysium.screen.no_points"),
                x, y, canSpend ? ElysiumPalette.WARN : ElysiumPalette.TEXT_FAINT);
        y += line + ElysiumPalette.GAP;

        List<ElysiumStat> stats = new ArrayList<>(ElysiumStat.REGISTRY.all());
        int rows = Math.max(1, (stats.size() + 1) / 2);

        // The stepper sits in a fixed gutter so the value column has a known
        // right edge and the numbers stay aligned down the page.
        int gutter = 12;
        int col = (w - ElysiumPalette.GAP_WIDE) / 2;
        int valueWidth = col - gutter;

        int hidden = 0;
        for (int i = 0; i < stats.size(); i++) {
            ElysiumStat stat = stats.get(i);
            int column = i / rows;
            int row = i % rows;
            int cx = x + column * (col + ElysiumPalette.GAP_WIDE);
            int cy = y + row * ElysiumUI.statRowHeight();
            if (cy + line > bottom) {
                // Only reachable on a window shorter than the panel wants —
                // the layout is measured to fit 240. Counted rather than
                // dropped, because a stat that is silently missing reads as a
                // stat the character does not have.
                hidden++;
                continue;
            }

            int value = sheet.get(stat);
            ElysiumUI.statRow(g, stat.getDisplayName(), Component.literal(Integer.toString(value)),
                    cx, cy, valueWidth, barFor(value), ElysiumPalette.TEXT);

            int sx = cx + col - ElysiumUI.STEPPER;
            int sy = cy - 1;
            boolean hovered = ElysiumUI.within(mouseX, mouseY, sx, sy,
                    ElysiumUI.STEPPER, ElysiumUI.STEPPER);
            ElysiumUI.stepper(g, sx, sy, canSpend, hovered);
            if (canSpend) {
                hits.add(new Hit(sx, sy, ElysiumUI.STEPPER, ElysiumUI.STEPPER, () -> spend(stat)));
            }
        }

        if (hidden > 0) {
            ElysiumUI.faint(g, Component.translatable("elysium.screen.more", hidden),
                    x, bottom - line);
        }
    }

    /**
     * A stat's bar, which is a comparison and not a measurement.
     *
     * Stats have no ceiling — reforge and ascension keep raising them — so
     * there is nothing to be a fraction of. The bar is against 50, which is
     * roughly where a heavily invested stat sits, and it is clamped: a bar that
     * is full says "very high", not "maximum", and nothing in the interface
     * claims otherwise.
     */
    private static float barFor(int value) {
        return Math.min(1.0F, value / 50.0F);
    }

    /** The two meters, with their band names and the notice threshold marked. */
    private void renderStandingTab(GuiGraphics g, int x, int y, int w, int bottom) {
        float notice = (float) ElysiumStanding.NOTICE / ElysiumStanding.MAX;
        y = meter(g, Component.translatable("elysium.screen.favor"),
                sheet.favor(), ElysiumStanding.favorBand(sheet.favor()),
                ElysiumPalette.FAVOR, notice, x, y, w, bottom);
        y += ElysiumPalette.GAP_WIDE;
        y = meter(g, Component.translatable("elysium.screen.suspicion"),
                sheet.suspicion(), ElysiumStanding.suspicionBand(sheet.suspicion()),
                ElysiumPalette.SUSPICION, notice, x, y, w, bottom);
        y += ElysiumPalette.GAP;
        ElysiumUI.wrapped(g, Component.translatable("elysium.screen.standing_hint"),
                x, y, w, ElysiumPalette.TEXT_FAINT, bottom);
    }

    private int meter(GuiGraphics g, Component name, int value, Component band, int colour,
                      float mark, int x, int y, int w, int bottom) {
        if (y + ElysiumUI.lineHeight() > bottom) {
            return y;
        }
        ElysiumUI.label(g, name, x, y);
        ElysiumUI.valueRight(g, Component.literal(value + " / " + ElysiumStanding.MAX),
                x + w, y, colour);
        y += ElysiumUI.lineHeight() + 2;
        ElysiumUI.barWithMark(g, x, y, w, (float) value / ElysiumStanding.MAX, mark, colour);
        y += ElysiumPalette.BAR_HEIGHT + 2;
        ElysiumUI.faint(g, band, x, y);
        return y + ElysiumUI.lineHeight();
    }

    private Component identity() {
        ElysiumRace race = sheet.race();
        ElysiumClass job = sheet.job();
        if (race == null || job == null) {
            return Component.translatable("elysium.screen.character");
        }
        return Component.empty()
                .append(race.getDisplayName())
                .append(Component.literal("  "))
                .append(job.getDisplayName());
    }

    // ------------------------------------------------------------------
    // The picker
    // ------------------------------------------------------------------

    private void renderPicker(GuiGraphics g, int mouseX, int mouseY) {
        int line = ElysiumUI.lineHeight();
        int pad = ElysiumPalette.PANEL_PAD;
        int gap = ElysiumPalette.GAP;

        int pw = ElysiumUI.fit(380, this.width);
        int ph = ElysiumUI.fit(210, this.height);
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        ElysiumUI.panel(g, px, py, pw, ph);

        int x = px + pad;
        int y = py + pad;
        int inner = pw - pad * 2;

        ElysiumUI.heading(g, Component.translatable("elysium.screen.choose"), x, y);
        y += line + 2;
        ElysiumUI.accentRule(g, x, y, 24, ElysiumPalette.ACCENT);
        ElysiumUI.ruleSoft(g, x + 24, y, inner - 24);
        y += gap + 2;

        int colw = (inner - ElysiumPalette.GAP_WIDE * 2) / 3;
        int listBottom = py + ph - pad - ElysiumPalette.BUTTON_HEIGHT - gap;
        int listHeight = listBottom - y;

        List<ElysiumRace> races = new ArrayList<>(ElysiumRace.REGISTRY.all());
        List<ElysiumClass> jobs = new ArrayList<>(ElysiumClass.REGISTRY.all());

        int raceX = x;
        int jobX = x + colw + ElysiumPalette.GAP_WIDE;
        int paneX = x + (colw + ElysiumPalette.GAP_WIDE) * 2;

        drawPickList(g, Component.translatable("elysium.screen.race"), raceX, y, colw, listHeight,
                races.size(), mouseX, mouseY,
                i -> races.get(i).getDisplayName(),
                i -> races.get(i) == pickedRace,
                i -> {
                    ElysiumRace race = races.get(i);
                    pickedRace = race;
                    focus(race.getDisplayName(), race.getDescription(),
                            race.getPassive().getDisplayName(), race.getPassive().getDescription());
                });

        drawPickList(g, Component.translatable("elysium.screen.job"), jobX, y, colw, listHeight,
                jobs.size(), mouseX, mouseY,
                i -> jobs.get(i).getDisplayName(),
                i -> jobs.get(i) == pickedClass,
                i -> {
                    ElysiumClass job = jobs.get(i);
                    pickedClass = job;
                    focus(job.getDisplayName(), job.getDescription(),
                            job.getPassive().getDisplayName(), job.getPassive().getDescription());
                });

        // The description pane: what was just clicked, in words.
        int paneTop = ElysiumUI.section(g, focusTitle.getString().isEmpty()
                ? Component.translatable("elysium.screen.pick_hint")
                : focusTitle, paneX, y, colw, ElysiumPalette.GOOD);
        ElysiumUI.sunken(g, paneX, paneTop, colw, listHeight - (paneTop - y));
        int paneBottom = paneTop + listHeight - (paneTop - y) - 4;
        int ty = ElysiumUI.wrapped(g, focusBody, paneX + gap, paneTop + 4, colw - gap * 2,
                ElysiumPalette.TEXT_MUTED, paneBottom);
        ElysiumUI.wrapped(g, focusDetail, paneX + gap, ty + gap, colw - gap * 2,
                ElysiumPalette.TEXT_FAINT, paneBottom);

        // Begin. Inert until both halves are answered — it draws disabled
        // rather than vanishing, so it is obvious that something is still owed.
        boolean ready = pickedRace != null && pickedClass != null;
        int bw = Math.min(70, colw);
        int bx = px + pw - pad - bw;
        int by = py + ph - pad - ElysiumPalette.BUTTON_HEIGHT;
        boolean hovered = ready && ElysiumUI.within(mouseX, mouseY, bx, by,
                bw, ElysiumPalette.BUTTON_HEIGHT);
        ElysiumUI.button(g, Component.translatable("elysium.screen.confirm"),
                bx, by, bw, ready, hovered);
        if (ready) {
            hits.add(new Hit(bx, by, bw, ElysiumPalette.BUTTON_HEIGHT, this::confirm));
        }
    }

    /**
     * One column of the picker.
     *
     * The three lambdas are there so races and classes share this code without
     * sharing a supertype. They have no useful common interface — one is a
     * species and the other a profession — and inventing one so a list could be
     * drawn twice would be the tail wagging the dog.
     */
    private void drawPickList(GuiGraphics g, Component title, int x, int y, int w, int h,
                              int count, int mouseX, int mouseY,
                              java.util.function.IntFunction<Component> name,
                              java.util.function.IntPredicate selected,
                              java.util.function.IntConsumer choose) {
        int top = ElysiumUI.section(g, title, x, y, w);
        int wellHeight = h - (top - y);
        ElysiumUI.sunken(g, x, top, w, wellHeight);

        int rowHeight = ElysiumPalette.ROW - 1;
        int ry = top + 2;
        for (int i = 0; i < count; i++) {
            if (ry + rowHeight > top + wellHeight) {
                // Out of room. Silently clipping would be a race a player
                // cannot pick and cannot see, so say how many are hidden.
                ElysiumUI.faint(g, Component.translatable("elysium.screen.more", count - i),
                        x + ElysiumPalette.GAP, top + wellHeight - ElysiumUI.lineHeight() - 2);
                return;
            }

            boolean isSelected = selected.test(i);
            boolean hovered = ElysiumUI.within(mouseX, mouseY, x, ry, w, rowHeight);
            if (isSelected) {
                ElysiumUI.selection(g, x, ry, w, rowHeight, ElysiumPalette.ACCENT);
            } else if (hovered) {
                ElysiumUI.hover(g, x, ry, w, rowHeight);
            }
            g.drawString(this.font, ElysiumUI.truncate(name.apply(i), w - ElysiumPalette.GAP * 2),
                    x + ElysiumPalette.GAP, ry + 3,
                    isSelected ? ElysiumPalette.TEXT : ElysiumPalette.TEXT_MUTED, false);

            int index = i;
            hits.add(new Hit(x, ry, w, rowHeight, () -> choose.accept(index)));
            ry += rowHeight;
        }
    }

    private void focus(Component title, Component body, Component passive, Component detail) {
        this.focusTitle = title;
        this.focusBody = body;
        this.focusDetail = Component.empty()
                .append(passive)
                .append(Component.literal(" — "))
                .append(detail);
    }

    // ==================================================================
    // Input
    // ==================================================================

    /**
     * Walks the rectangles the last frame recorded.
     *
     * In reverse, so that when two overlap the one drawn last — that is, the
     * one on top, the one the player can see — is the one that answers.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (ElysiumUI.within(mouseX, mouseY, hit.x(), hit.y(), hit.w(), hit.h())) {
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void confirm() {
        if (pickedRace == null || pickedClass == null) {
            return;
        }
        PacketDistributor.sendToServer(new ElysiumPayloads.ChooseCharacter(
                pickedRace.getSerialisedName(), pickedClass.getSerialisedName()));
        onClose();
    }

    private void spend(ElysiumStat stat) {
        PacketDistributor.sendToServer(new ElysiumPayloads.SpendPoints(stat.getSerialisedName(), 1));
        // The server answers with a fresh sheet, which replaces this screen.
    }
}
