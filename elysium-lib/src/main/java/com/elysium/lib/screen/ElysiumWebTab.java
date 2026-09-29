package com.elysium.lib.screen;

import com.elysium.lib.client.ElysiumPalette;
import com.elysium.lib.client.ElysiumUI;
import com.elysium.lib.network.CharacterSheet;
import com.elysium.lib.network.ElysiumPayloads;
import com.elysium.lib.tree.ElysiumTree;
import com.elysium.lib.tree.ElysiumTreeNames;
import com.elysium.lib.tree.ElysiumTreeRules;
import com.elysium.lib.tree.ElysiumTreeState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Ledger Web, drawn: a pannable, zoomable canvas inside the character
 * sheet's body.
 *
 * <p>Everything is fills in the kit's palette — no textures, no line
 * primitive: a link is a run of beads between two nodes, a small node a dot,
 * a notable a framed square, a keystone a diamond, a start a square on the
 * rim, the hub a larger disc. Held nodes are lit, nodes a free point could
 * take are in the accent, the rest are dim. Hover explains a node in a
 * tooltip; a click asks the server for it; a right-click asks for it back.
 * The server answers with a fresh sheet, which this reads on the next frame,
 * so the screen and the server agree by construction.
 *
 * <p>The tab does its own input: the screen hands it clicks, drags, releases
 * and scrolls that land in the body while this tab is up. A press that moves
 * is a pan; a press that does not is a click. Nothing here decides whether a
 * node may be taken — {@link ElysiumTreeState} does, on the server — but the
 * lit colour is read from the same rules so a player sees what a click will do.
 */
public final class ElysiumWebTab {

    private static final float MIN_ZOOM = 0.16F;
    private static final float MAX_ZOOM = 1.6F;
    private static final double CLICK_SLOP = 3.0;

    private float zoom = 0.0F;          // 0 until the first frame sizes it to fit
    private float panX;
    private float panY;
    private double pressX;
    private double pressY;
    private boolean pressed;
    private boolean dragged;
    private int pressedButton;

    private int x;
    private int y;
    private int w;
    private int h;
    private CharacterSheet.Parsed sheet = CharacterSheet.empty();

    private String hovered;

    /** The search: what was typed, and the node it found (centred and outlined). */
    private String typed = "";
    private String found;

    public void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, CharacterSheet.Parsed sheet) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.sheet = sheet;
        if (zoom == 0.0F) {
            // First frame: the whole web in the body, the hub in the middle.
            zoom = Math.max(MIN_ZOOM, Math.min(w, h) / (2.0F * (float) ElysiumTree.R_EDGE + 40.0F));
            panX = 0;
            panY = 0;
        }
        int line = ElysiumUI.lineHeight();
        if (typed.isEmpty()) {
            ElysiumUI.faint(g, ElysiumUI.truncate(Component.translatable("elysium.tree.hint",
                    sheet.treeFree(), sheet.erasures()), w), x, y);
        } else {
            ElysiumTree.Node match = ElysiumTree.node(found);
            ElysiumUI.label(g, ElysiumUI.truncate(Component.translatable("elysium.tree.search", typed,
                    match == null ? Component.translatable("elysium.tree.search.none") : ElysiumTreeNames.title(match)), w), x, y);
        }
        int top = y + line + 2;
        int height = h - line - 2;
        ElysiumUI.sunken(g, x, top, w, height);
        g.enableScissor(x, top, x + w, top + height);
        hovered = null;
        Set<String> held = new HashSet<>(sheet.held());
        Set<String> takeable = takeable(held);

        // Links first, as beads, so nodes sit on top.
        for (ElysiumTree.Node node : ElysiumTree.all()) {
            for (String link : node.links()) {
                if (link.compareTo(node.id()) < 0) {
                    continue;                       // each link once
                }
                ElysiumTree.Node other = ElysiumTree.node(link);
                if (other == null) {
                    continue;
                }
                boolean lit = held.contains(node.id()) && held.contains(link);
                beads(g, sx(node.x()), sy(node.y()), sx(other.x()), sy(other.y()),
                        lit ? ElysiumPalette.ACCENT : ElysiumPalette.LINE_SOFT);
            }
        }
        for (ElysiumTree.Node node : ElysiumTree.all()) {
            int px = sx(node.x());
            int py = sy(node.y());
            int r = radius(node.kind());
            if (px + r < x || px - r > x + w || py + r < top || py - r > top + height) {
                continue;
            }
            boolean isHeld = held.contains(node.id());
            boolean canTake = takeable.contains(node.id());
            int colour = isHeld ? ElysiumPalette.ACCENT_BRIGHT : canTake ? ElysiumPalette.ACCENT : ElysiumPalette.TEXT_FAINT;
            boolean over = ElysiumUI.within(mouseX, mouseY, px - r - 1, py - r - 1, 2 * r + 3, 2 * r + 3)
                    && ElysiumUI.within(mouseX, mouseY, x, top, w, height);
            if (over) {
                hovered = node.id();
            }
            frame(g, node.kind(), px, py, r, colour, isHeld, over || node.id().equals(found));
        }
        g.disableScissor();
        if (hovered != null) {
            ElysiumTree.Node node = ElysiumTree.node(hovered);
            MutableComponent body = Component.empty();
            for (Component reward : ElysiumTreeNames.rewards(node)) {
                body.append(reward).append(" ");
            }
            body.append(ElysiumTreeNames.blurb(node));
            ElysiumUI.tooltip(g, ElysiumTreeNames.title(node), body, mouseX, mouseY,
                    g.guiWidth(), g.guiHeight());
        }
    }

    /** The nodes a free point could take: unheld, touching a held node, not an opposed keystone. */
    private Set<String> takeable(Set<String> held) {
        Set<String> out = new HashSet<>();
        if (sheet.treeFree() <= 0) {
            return out;
        }
        for (String id : held) {
            ElysiumTree.Node node = ElysiumTree.node(id);
            if (node == null) {
                continue;
            }
            for (String link : node.links()) {
                if (held.contains(link)) {
                    continue;
                }
                String doctrine = ElysiumTreeState.doctrineOf(ElysiumTree.node(link));
                if (doctrine != null) {
                    String opposite = ElysiumTreeRules.opposedTo(doctrine);
                    if (opposite != null && held.contains("key_" + opposite)) {
                        continue;
                    }
                }
                out.add(link);
            }
        }
        return out;
    }

    private int sx(double wx) {
        return x + w / 2 + Math.round((float) wx * zoom + panX);
    }

    private int sy(double wy) {
        int line = ElysiumUI.lineHeight();
        int top = y + line + 2;
        int height = h - line - 2;
        return top + height / 2 + Math.round((float) wy * zoom + panY);
    }

    private static int radius(String kind) {
        return switch (kind) {
            case ElysiumTree.KIND_HUB -> 5;
            case ElysiumTree.KIND_NOTABLE -> 4;
            case ElysiumTree.KIND_KEYSTONE -> 5;
            case ElysiumTree.KIND_START -> 4;
            case ElysiumTree.KIND_ROAD -> 1;
            default -> 2;
        };
    }

    private static void beads(GuiGraphics g, int x0, int y0, int x1, int y1, int colour) {
        double length = Math.hypot(x1 - x0, y1 - y0);
        int steps = Math.max(1, (int) (length / 3.0));
        for (int i = 1; i < steps; i++) {
            int bx = x0 + (int) Math.round((x1 - x0) * (double) i / steps);
            int by = y0 + (int) Math.round((y1 - y0) * (double) i / steps);
            g.fill(bx, by, bx + 1, by + 1, colour);
        }
    }

    private static void frame(GuiGraphics g, String kind, int px, int py, int r, int colour, boolean held, boolean over) {
        int edge = over ? ElysiumPalette.TEXT : held ? ElysiumPalette.ACCENT_BRIGHT : ElysiumPalette.LINE;
        switch (kind) {
            case ElysiumTree.KIND_KEYSTONE -> {
                // A diamond: rows of widening then narrowing fills.
                for (int dy = -r; dy <= r; dy++) {
                    int half = r - Math.abs(dy);
                    g.fill(px - half, py + dy, px + half + 1, py + dy + 1, colour);
                }
                g.fill(px - 1, py - 1, px + 2, py + 2, edge);
            }
            case ElysiumTree.KIND_NOTABLE, ElysiumTree.KIND_START -> {
                g.fill(px - r - 1, py - r - 1, px + r + 2, py + r + 2, edge);
                g.fill(px - r, py - r, px + r + 1, py + r + 1, colour);
            }
            case ElysiumTree.KIND_HUB -> {
                g.fill(px - r, py - r + 1, px + r + 1, py + r, colour);
                g.fill(px - r + 1, py - r, px + r, py + r + 1, colour);
            }
            default -> {
                g.fill(px - r, py - r, px + r + 1, py + r + 1, colour);
                if (over) {
                    g.fill(px - r - 1, py - r - 1, px + r + 2, py - r, edge);
                    g.fill(px - r - 1, py + r + 1, px + r + 2, py + r + 2, edge);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Input, handed in by the screen
    // ------------------------------------------------------------------

    public boolean inside(double mouseX, double mouseY) {
        return ElysiumUI.within(mouseX, mouseY, x, y, w, h);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!inside(mouseX, mouseY)) {
            return false;
        }
        pressed = true;
        dragged = false;
        pressedButton = button;
        pressX = mouseX;
        pressY = mouseY;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (!pressed) {
            return false;
        }
        if (Math.abs(mouseX - pressX) > CLICK_SLOP || Math.abs(mouseY - pressY) > CLICK_SLOP) {
            dragged = true;
        }
        if (dragged) {
            panX += (float) dx;
            panY += (float) dy;
        }
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!pressed) {
            return false;
        }
        pressed = false;
        if (dragged || hovered == null) {
            return true;
        }
        if (pressedButton == 0) {
            PacketDistributor.sendToServer(new ElysiumPayloads.TreeAllocate(hovered));
        } else if (pressedButton == 1) {
            PacketDistributor.sendToServer(new ElysiumPayloads.TreeRefund(hovered));
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!inside(mouseX, mouseY)) {
            return false;
        }
        float before = zoom;
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * (amount > 0 ? 1.15F : 1.0F / 1.15F)));
        // Zoom about the pointer: what was under it stays under it.
        float scale = zoom / before;
        int cx = x + w / 2;
        int cy = sy(0) - Math.round(panY);
        panX = (float) ((panX + cx - mouseX) * scale - (cx - mouseX));
        panY = (float) ((panY + cy - mouseY) * scale - (cy - mouseY));
        return true;
    }

    // ------------------------------------------------------------------
    // The search box: type a node's id, Enter centres it, Enter again takes it
    // ------------------------------------------------------------------

    /** A typed character: the id alphabet only, so a hotkey is never eaten. */
    public boolean charTyped(char c) {
        if (Character.isLetterOrDigit(c) || c == '_') {
            typed += Character.toLowerCase(c);
            found = find(typed);
            return true;
        }
        return false;
    }

    /**
     * Keys: Backspace trims; Escape clears what was typed (and only then
     * closes, which is the chooser's rule too); Enter centres the node the
     * search found, or takes it when it is already centred.
     */
    public boolean keyPressed(int key) {
        if (key == 259 && !typed.isEmpty()) {          // backspace
            typed = typed.substring(0, typed.length() - 1);
            found = find(typed);
            return true;
        }
        if (key == 256 && !typed.isEmpty()) {          // escape
            typed = "";
            found = null;
            return true;
        }
        if ((key == 257 || key == 335) && found != null) {   // enter
            ElysiumTree.Node node = ElysiumTree.node(found);
            if (node == null) {
                return true;
            }
            int line = ElysiumUI.lineHeight();
            int top = y + line + 2;
            int height = h - line - 2;
            boolean centred = Math.abs(sx(node.x()) - (x + w / 2)) <= 2
                    && Math.abs(sy(node.y()) - (top + height / 2)) <= 2;
            if (!centred) {
                panX = -(float) node.x() * zoom;
                panY = -(float) node.y() * zoom;
            } else if (!sheet.holds(found)) {
                PacketDistributor.sendToServer(new ElysiumPayloads.TreeAllocate(found));
            }
            return true;
        }
        return false;
    }

    /** The node an id names, or the one node whose id starts with what was typed. */
    static String find(String text) {
        if (text.isEmpty()) {
            return null;
        }
        if (ElysiumTree.exists(text)) {
            return text;
        }
        String only = null;
        for (ElysiumTree.Node node : ElysiumTree.all()) {
            if (node.id().startsWith(text)) {
                if (only != null) {
                    return null;
                }
                only = node.id();
            }
        }
        return only;
    }

    public boolean searching() {
        return !typed.isEmpty();
    }

    /** The nodes drawn lit for a build: what the stage photographs. */
    public static List<String> heldOf(CharacterSheet.Parsed sheet) {
        return sheet.held();
    }
}
