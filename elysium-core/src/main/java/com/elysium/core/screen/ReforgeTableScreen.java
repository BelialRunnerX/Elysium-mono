package com.elysium.core.screen;

import com.elysium.core.menu.ReforgeTableMenu;
import com.elysium.lib.client.ElysiumPalette;
import com.elysium.lib.client.ElysiumUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The workstation screen, drawn rather than blitted.
 *
 * <h2>What changed and what deliberately did not</h2>
 *
 * The chrome is now {@link ElysiumUI} — a panel, hairlines and drawn slots
 * instead of {@code textures/gui/reforge_table.png} — so this screen belongs to
 * the same interface as the character sheet. A GUI texture is authored at one
 * size and then scaled by the player's GUI scale setting, so at scale 3 its
 * one-pixel border is a three-pixel stripe; that softness is most of what made
 * this screen look unlike the game's own.
 *
 * <b>Every slot is exactly where it was.</b> The positions below are mirrored
 * from {@link ReforgeTableMenu} rather than chosen, because a slot's
 * coordinates live on the menu: the menu decides where a click lands, and the
 * screen only decides where the box is painted. Moving the boxes without moving
 * the menu's slots would give a screen whose every click missed by exactly the
 * distance it had been prettied up. Rearranging them is a real change to the
 * menu and a separate piece of work.
 *
 * The action button is drawn rather than a vanilla widget, for the same reason
 * as the border, and sends the same container button click it always did.
 */
public class ReforgeTableScreen extends AbstractContainerScreen<ReforgeTableMenu> {

    /**
     * Where the three working slots are, mirrored from ReforgeTableMenu.
     *
     * Named here rather than left as pairs of numbers inside a draw call, so
     * that if the menu ever moves one the two places that must agree are easy
     * to find.
     */
    private static final int SLOT_GEAR_X = 30;
    private static final int SLOT_MATERIAL_X = 80;
    private static final int SLOT_RUNE_X = 130;
    private static final int SLOT_ROW_Y = 35;

    /** The player's inventory grid, likewise from the menu. */
    private static final int INVENTORY_X = 8;
    private static final int INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;

    private static final int BUTTON_X = 58;
    private static final int BUTTON_Y = 56;
    private static final int BUTTON_W = 60;

    public ReforgeTableScreen(ReforgeTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    // ------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ElysiumUI.scrim(g, this.width, this.height);
        ElysiumUI.panel(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);

        int x = this.leftPos + ElysiumPalette.GAP;
        int inner = this.imageWidth - ElysiumPalette.GAP * 2;

        // A rule under the title and another above the player's inventory —
        // the two divisions this screen actually has.
        ElysiumUI.accentRule(g, x, this.topPos + 18, 24, ElysiumPalette.ACCENT);
        ElysiumUI.ruleSoft(g, x + 24, this.topPos + 18, inner - 24);
        ElysiumUI.rule(g, x, this.topPos + INVENTORY_Y - 10, inner);

        // The three working slots, hinted so a workstation is not a guessing
        // game. The hint is drawn at 30% and disappears under a real item.
        slotAt(g, SLOT_GEAR_X, SLOT_ROW_Y, mouseX, mouseY,
                Component.literal("G"), ElysiumPalette.ACCENT);
        slotAt(g, SLOT_MATERIAL_X, SLOT_ROW_Y, mouseX, mouseY,
                Component.literal("C"), ElysiumPalette.KINETIC);
        slotAt(g, SLOT_RUNE_X, SLOT_ROW_Y, mouseX, mouseY,
                Component.literal("R"), ElysiumPalette.VOID);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotAt(g, INVENTORY_X + col * 18, INVENTORY_Y + row * 18,
                        mouseX, mouseY, null, 0);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotAt(g, INVENTORY_X + col * 18, HOTBAR_Y, mouseX, mouseY, null, 0);
        }
    }

    /**
     * One slot, given the coordinate the menu holds.
     *
     * A menu slot's x and y are where its <em>item</em> is drawn, and an item is
     * sixteen pixels inside an eighteen-pixel box — so the chrome starts one
     * pixel up and left of the menu's number. On a texture that offset is baked
     * into the image and invisible; drawn, getting it wrong is glaring.
     */
    private void slotAt(GuiGraphics g, int slotX, int slotY, int mouseX, int mouseY,
                        Component hint, int hintColour) {
        int x = this.leftPos + slotX - 1;
        int y = this.topPos + slotY - 1;
        boolean hovered = ElysiumUI.within(mouseX, mouseY, x, y,
                ElysiumPalette.SLOT_SIZE, ElysiumPalette.SLOT_SIZE);
        if (hint == null) {
            ElysiumUI.slot(g, x, y, hovered);
        } else {
            ElysiumUI.slotHinted(g, x, y, hovered, hint, hintColour);
        }
    }

    /**
     * The two labels, in the interface's own weights.
     *
     * Coordinates here are relative to the panel's top-left, which is what
     * {@code renderLabels} is handed; everything else in this class works in
     * screen space, so the two are kept in separate methods rather than mixed.
     */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        ElysiumUI.heading(g, ElysiumUI.truncate(this.title, this.imageWidth - 16),
                ElysiumPalette.GAP, ElysiumPalette.GAP);
        ElysiumUI.faint(g, Component.translatable("container.inventory"),
                ElysiumPalette.GAP, this.inventoryLabelY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        boolean hovered = ElysiumUI.within(mouseX, mouseY,
                this.leftPos + BUTTON_X, this.topPos + BUTTON_Y,
                BUTTON_W, ElysiumPalette.BUTTON_HEIGHT);
        ElysiumUI.button(g, Component.translatable("elysium.gui.reforge"),
                this.leftPos + BUTTON_X, this.topPos + BUTTON_Y, BUTTON_W, true, hovered);

        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && ElysiumUI.within(mouseX, mouseY,
                this.leftPos + BUTTON_X, this.topPos + BUTTON_Y,
                BUTTON_W, ElysiumPalette.BUTTON_HEIGHT)) {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(
                        this.menu.containerId, ReforgeTableMenu.BUTTON_PERFORM);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
