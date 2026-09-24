package com.elysium.core.screen;

import com.elysium.core.menu.TwoInputBenchMenu;
import com.elysium.lib.client.ElysiumPalette;
import com.elysium.lib.client.ElysiumUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The screen both P7 benches wear.
 *
 * <p>One class for the same reason the menu is one class: they are the same
 * screen with a different line of text under the result, and two copies of it
 * would drift apart the first time either was touched.
 *
 * <p>Subclasses say what that line is. A bench with nothing to say says
 * nothing, which is better than a bench that explains itself to a player who
 * can already see the result.
 */
public abstract class TwoInputBenchScreen<T extends TwoInputBenchMenu>
        extends AbstractContainerScreen<T> {

    protected TwoInputBenchScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = TwoInputBenchMenu.INVENTORY_Y - 11;
    }

    /** The line under the result, or null for nothing. */
    protected abstract Component note();

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ElysiumUI.scrim(g, width, height);
        ElysiumUI.panel(g, leftPos, topPos, imageWidth, imageHeight);
        int x = leftPos + ElysiumPalette.GAP;
        ElysiumUI.accentRule(g, x, topPos + 18, 24, ElysiumPalette.ACCENT);
        ElysiumUI.ruleSoft(g, x + 24, topPos + 18, imageWidth - ElysiumPalette.GAP * 2 - 24);
        ElysiumUI.rule(g, x, topPos + TwoInputBenchMenu.INVENTORY_Y - 14,
                imageWidth - ElysiumPalette.GAP * 2);

        slot(g, TwoInputBenchMenu.FIRST_X, TwoInputBenchMenu.FIRST_Y, mouseX, mouseY);
        slot(g, TwoInputBenchMenu.SECOND_X, TwoInputBenchMenu.SECOND_Y, mouseX, mouseY);
        slot(g, TwoInputBenchMenu.RESULT_X, TwoInputBenchMenu.RESULT_Y, mouseX, mouseY);
        slot(g, TwoInputBenchMenu.SPARE_X, TwoInputBenchMenu.SPARE_Y, mouseX, mouseY);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(g, 8 + column * 18, TwoInputBenchMenu.INVENTORY_Y + row * 18, mouseX, mouseY);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(g, 8 + column * 18, TwoInputBenchMenu.INVENTORY_Y + 58, mouseX, mouseY);
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
        Component note = note();
        if (note != null) {
            ElysiumUI.faint(g, ElysiumUI.truncate(note, imageWidth - 24),
                    ElysiumPalette.GAP + 4, TwoInputBenchMenu.RESULT_Y + 24);
        }
        ElysiumUI.faint(g, Component.translatable("container.inventory"),
                ElysiumPalette.GAP, inventoryLabelY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
