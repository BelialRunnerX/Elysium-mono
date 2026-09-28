package com.elysium.lib.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * A box, in the kit: rows of slots over the player's inventory.
 *
 * <p>The game's chest screen is a 176-wide texture with a grey inventory
 * under it; this is the same layout drawn the kit's way, so a cabinet opens
 * looking like the machine beside it. The row count is read off the menu
 * rather than asked for, so one screen serves three rows, six, or whatever a
 * module's own box menu adds slots for: everything that is not the player's
 * thirty-six slots is the box.
 */
public class ElysiumChestScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    /** The player's own slots: three rows and the hotbar. */
    public static final int PLAYER_SLOTS = 36;

    private final int rows;

    public ElysiumChestScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.rows = Math.max(1, (menu.slots.size() - PLAYER_SLOTS) / 9);
        this.imageWidth = 176;
        this.imageHeight = 114 + rows * 18;
        this.inventoryLabelY = imageHeight - 94;
    }

    /** Where the player's inventory begins, as the game's chest menu lays it out. */
    private int inventoryY() {
        return rows * 18 + 31;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ElysiumUI.scrim(g, width, height);
        ElysiumUI.panel(g, leftPos, topPos, imageWidth, imageHeight);
        int x = leftPos + ElysiumPalette.GAP;
        int inner = imageWidth - ElysiumPalette.GAP * 2;
        ElysiumUI.accentRule(g, x, topPos + 16, 24, ElysiumPalette.ACCENT);
        ElysiumUI.ruleSoft(g, x + 24, topPos + 16, inner - 24);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, 8 + col * 18, 18 + row * 18, mouseX, mouseY);
            }
        }
        ElysiumUI.rule(g, x, topPos + inventoryY() - 14, inner);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, 8 + col * 18, inventoryY() + row * 18, mouseX, mouseY);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, 8 + col * 18, inventoryY() + 58, mouseX, mouseY);
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
                ElysiumPalette.GAP, ElysiumPalette.GAP - 2);
        ElysiumUI.faint(g, Component.translatable("container.inventory"),
                ElysiumPalette.GAP, inventoryLabelY);
    }
}
