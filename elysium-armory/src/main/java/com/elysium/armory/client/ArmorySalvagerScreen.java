package com.elysium.armory.client;

import com.elysium.armory.menu.ArmorySalvagerMenu;
import com.elysium.lib.client.ElysiumPalette;
import com.elysium.lib.client.ElysiumUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Salvager's screen: a piece on the left, a lever, and what came out.
 *
 * <p>The lever is only live when there is something to take apart and nowhere
 * for the results to go is not the case — so a player never presses it and
 * watches nothing happen.
 */
public class ArmorySalvagerScreen extends AbstractContainerScreen<ArmorySalvagerMenu> {

    private static final int BUTTON_X = 20;
    private static final int BUTTON_Y = 58;
    private static final int BUTTON_W = 44;
    private static final int BUTTON_H = 14;

    public ArmorySalvagerScreen(ArmorySalvagerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = ArmorySalvagerMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ElysiumUI.scrim(g, width, height);
        ElysiumUI.panel(g, leftPos, topPos, imageWidth, imageHeight);
        int x = leftPos + ElysiumPalette.GAP;
        ElysiumUI.accentRule(g, x, topPos + 18, 24, ElysiumPalette.ACCENT);
        ElysiumUI.ruleSoft(g, x + 24, topPos + 18, imageWidth - ElysiumPalette.GAP * 2 - 24);
        ElysiumUI.rule(g, x, topPos + ArmorySalvagerMenu.INVENTORY_Y - 14,
                imageWidth - ElysiumPalette.GAP * 2);

        slot(g, ArmorySalvagerMenu.INPUT_X, ArmorySalvagerMenu.INPUT_Y, mouseX, mouseY);
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 4; column++) {
                slot(g, ArmorySalvagerMenu.OUTPUT_X + column * 18,
                        ArmorySalvagerMenu.OUTPUT_Y + row * 18, mouseX, mouseY);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(g, 8 + column * 18, ArmorySalvagerMenu.INVENTORY_Y + row * 18, mouseX, mouseY);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(g, 8 + column * 18, ArmorySalvagerMenu.INVENTORY_Y + 58, mouseX, mouseY);
        }

        boolean hovered = ElysiumUI.within(mouseX, mouseY, leftPos + BUTTON_X, topPos + BUTTON_Y,
                BUTTON_W, BUTTON_H);
        if (menu.ready()) {
            ElysiumUI.selection(g, leftPos + BUTTON_X, topPos + BUTTON_Y, BUTTON_W, BUTTON_H,
                    ElysiumPalette.ACCENT);
            if (hovered) {
                ElysiumUI.focusRing(g, leftPos + BUTTON_X, topPos + BUTTON_Y, BUTTON_W, BUTTON_H);
            }
        } else {
            ElysiumUI.sunken(g, leftPos + BUTTON_X, topPos + BUTTON_Y, BUTTON_W, BUTTON_H);
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
        Component label = Component.translatable("elysiumarmory.salvager.button");
        ElysiumUI.centred(g, label, BUTTON_X + BUTTON_W / 2, BUTTON_Y + 3,
                menu.ready() ? ElysiumPalette.TEXT : ElysiumPalette.TEXT_FAINT);
        if (!menu.ready()) {
            ElysiumUI.faint(g, ElysiumUI.truncate(
                    Component.translatable("elysiumarmory.salvager.nothing"), imageWidth - 88),
                    ArmorySalvagerMenu.OUTPUT_X, ArmorySalvagerMenu.OUTPUT_Y + 42);
        }
        ElysiumUI.faint(g, Component.translatable("container.inventory"),
                ElysiumPalette.GAP, inventoryLabelY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu.ready() && ElysiumUI.within(mouseX, mouseY, leftPos + BUTTON_X, topPos + BUTTON_Y,
                BUTTON_W, BUTTON_H)) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        ArmorySalvagerMenu.SALVAGE);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
