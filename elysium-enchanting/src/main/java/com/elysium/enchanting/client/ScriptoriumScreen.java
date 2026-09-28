package com.elysium.enchanting.client;

import com.elysium.enchanting.block.ScriptoriumBlockEntity;
import com.elysium.enchanting.menu.ScriptoriumMenu;
import com.elysium.lib.client.ElysiumPalette;
import com.elysium.lib.client.ElysiumUI;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.List;
import java.util.Optional;

/**
 * The Scriptorium's screen: what is on the shelf, and what this line buys.
 *
 * <h2>Where the names come from</h2>
 *
 * The block entity, not the menu. A container's data is integers and an
 * enchantment is a name; the shelf is synced to the client like a chest's
 * contents, and this reads the copy in front of the player. Nothing here is
 * trusted to spend anything — the menu works the price out again on the server
 * before a page moves.
 */
public class ScriptoriumScreen extends AbstractContainerScreen<ScriptoriumMenu> {

    private static final int LEFT_X = 44;
    private static final int RIGHT_X = 122;
    private static final int ARROW_Y = 30;
    private static final int ARROW_W = 12;
    private static final int ARROW_H = 12;
    private static final int BUY_X = 44;
    private static final int BUY_Y = 66;
    private static final int BUY_W = 90;
    private static final int BUY_H = 26;   // two lines of a wrapped offer

    public ScriptoriumScreen(ScriptoriumMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 182;
        this.inventoryLabelY = ScriptoriumMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ElysiumUI.scrim(g, width, height);
        ElysiumUI.panel(g, leftPos, topPos, imageWidth, imageHeight);
        int x = leftPos + ElysiumPalette.GAP;
        ElysiumUI.accentRule(g, x, topPos + 18, 24, ElysiumPalette.ACCENT);
        ElysiumUI.ruleSoft(g, x + 24, topPos + 18, imageWidth - ElysiumPalette.GAP * 2 - 24);
        ElysiumUI.rule(g, x, topPos + ScriptoriumMenu.INVENTORY_Y - 14,
                imageWidth - ElysiumPalette.GAP * 2);

        slot(g, ScriptoriumMenu.INPUT_X, ScriptoriumMenu.INPUT_Y, mouseX, mouseY);
        slot(g, ScriptoriumMenu.OUTPUT_X, ScriptoriumMenu.OUTPUT_Y, mouseX, mouseY);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(g, 8 + column * 18, ScriptoriumMenu.INVENTORY_Y + row * 18, mouseX, mouseY);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(g, 8 + column * 18, ScriptoriumMenu.INVENTORY_Y + 58, mouseX, mouseY);
        }
        ElysiumUI.sunken(g, leftPos + LEFT_X, topPos + ARROW_Y, ARROW_W, ARROW_H);
        ElysiumUI.sunken(g, leftPos + RIGHT_X, topPos + ARROW_Y, ARROW_W, ARROW_H);
        boolean ready = menu.number(ScriptoriumMenu.LEVEL) > 0;
        if (ready && ElysiumUI.within(mouseX, mouseY, leftPos + BUY_X, topPos + BUY_Y,
                BUY_W, BUY_H)) {
            ElysiumUI.selection(g, leftPos + BUY_X, topPos + BUY_Y, BUY_W, BUY_H,
                    ElysiumPalette.ACCENT);
        } else {
            ElysiumUI.sunken(g, leftPos + BUY_X, topPos + BUY_Y, BUY_W, BUY_H);
        }
    }

    private void slot(GuiGraphics g, int sx, int sy, int mouseX, int mouseY) {
        int x = leftPos + sx - 1;
        int y = topPos + sy - 1;
        ElysiumUI.slot(g, x, y, ElysiumUI.within(mouseX, mouseY, x, y,
                ElysiumPalette.SLOT_SIZE, ElysiumPalette.SLOT_SIZE));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        ElysiumUI.heading(g, ElysiumUI.truncate(title, imageWidth - 16),
                ElysiumPalette.GAP, ElysiumPalette.GAP);
        Component line = chosen();
        ElysiumUI.value(g, ElysiumUI.truncate(line, RIGHT_X - LEFT_X - ARROW_W - 4),
                LEFT_X + ARROW_W + 2, ARROW_Y + 2, ElysiumPalette.TEXT);
        ElysiumUI.faint(g, Component.translatable("elysiumenchanting.library.pages",
                        Component.literal(String.valueOf(menu.number(ScriptoriumMenu.PAGES))),
                        Component.literal(String.valueOf(menu.number(ScriptoriumMenu.CAPACITY)))),
                ElysiumPalette.GAP, 44);
        int level = menu.number(ScriptoriumMenu.LEVEL);
        if (level > 0) {
            // Wrapped inside the button rather than cut: at full length the line
            // ran under the output slot (the client stage, 27 September 2026),
            // and cut to one line it read "Buy level 3 for.." (POLISH.md P14).
            ElysiumUI.wrapped(g, Component.translatable("elysiumenchanting.library.buy",
                            Component.literal(String.valueOf(level)),
                            Component.literal(String.valueOf(
                                    menu.number(ScriptoriumMenu.PRICE))),
                            Component.literal(String.valueOf(
                                    menu.number(ScriptoriumMenu.EXPERIENCE)))),
                    BUY_X + 4, BUY_Y + 4, BUY_W - 8, ElysiumPalette.TEXT, BUY_Y + BUY_H - 3);
        } else {
            ElysiumUI.wrapped(g, Component.translatable("elysiumenchanting.library.empty"),
                    BUY_X + 4, BUY_Y + 4, BUY_W - 8, ElysiumPalette.TEXT_FAINT, BUY_Y + BUY_H - 3);
        }
        ElysiumUI.faint(g, Component.translatable("container.inventory"),
                ElysiumPalette.GAP, inventoryLabelY);
    }

    /** The name of the line the player is looking at, read off the shelf itself. */
    private Component chosen() {
        ScriptoriumBlockEntity shelf = minecraft == null ? null : menu.shelf(minecraft.level);
        if (shelf == null) {
            return Component.translatable("elysiumenchanting.library.nothing");
        }
        List<String> filed = shelf.filed();
        if (filed.isEmpty()) {
            return Component.translatable("elysiumenchanting.library.nothing");
        }
        String id = filed.get(Math.floorMod(menu.selected(), filed.size()));
        ResourceKey<Enchantment> key = ScriptoriumBlockEntity.keyOf(id);
        if (key == null || minecraft.level == null) {
            return Component.literal(id);
        }
        Optional<Holder.Reference<Enchantment>> found =
                minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
        return found.<Component>map(holder -> holder.value().description())
                .orElse(Component.literal(id));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (press(mouseX, mouseY, LEFT_X, ARROW_Y, ARROW_W, ARROW_H, ScriptoriumMenu.PREVIOUS)
                || press(mouseX, mouseY, RIGHT_X, ARROW_Y, ARROW_W, ARROW_H,
                ScriptoriumMenu.NEXT)
                || press(mouseX, mouseY, BUY_X, BUY_Y, BUY_W, BUY_H, ScriptoriumMenu.BUY)) {
            return true;
        }
        // Filing is the input slot's own button: clicking the book itself is
        // what a player tries first.
        if (ElysiumUI.within(mouseX, mouseY, leftPos + ScriptoriumMenu.INPUT_X - 1,
                topPos + ScriptoriumMenu.INPUT_Y - 1, ElysiumPalette.SLOT_SIZE,
                ElysiumPalette.SLOT_SIZE) && hasShiftDown()) {
            send(ScriptoriumMenu.FILE);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean press(double mouseX, double mouseY, int x, int y, int w, int h, int button) {
        if (!ElysiumUI.within(mouseX, mouseY, leftPos + x, topPos + y, w, h)) {
            return false;
        }
        send(button);
        return true;
    }

    private void send(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
