package com.elysium.space.client;

import com.elysium.lib.client.ElysiumPalette;
import com.elysium.lib.client.ElysiumUI;
import com.elysium.space.machine.MachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * A machine's screen, drawn in the interface's own chrome (ElysiumUI, as the
 * workstation and character screens are): an energy bar, one bar per tank in
 * the fluid's own colour, the fuel slot where the machine has one, and the
 * player's inventory.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {

    private static final int BAR_X = 8;
    private static final int FIRST_BAR_Y = 26;
    private static final int BAR_STEP = 16;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = MachineMenu.INVENTORY_Y - 11;
    }

    private int barWidth() {
        return menu.hasSlot() ? MachineMenu.SLOT_X - BAR_X - 8 : imageWidth - BAR_X * 2;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ElysiumUI.scrim(g, width, height);
        ElysiumUI.panel(g, leftPos, topPos, imageWidth, imageHeight);
        int x = leftPos + ElysiumPalette.GAP;
        int inner = imageWidth - ElysiumPalette.GAP * 2;
        ElysiumUI.accentRule(g, x, topPos + 18, 24, ElysiumPalette.ACCENT);
        ElysiumUI.ruleSoft(g, x + 24, topPos + 18, inner - 24);
        ElysiumUI.rule(g, x, topPos + MachineMenu.INVENTORY_Y - 14, inner);

        int y = topPos + FIRST_BAR_Y;
        if (menu.energyCapacity() > 0) {
            ElysiumUI.bar(g, leftPos + BAR_X, y + 9, barWidth(), fraction(menu.energy(), menu.energyCapacity()),
                    ElysiumPalette.KINETIC);
            y += BAR_STEP;
        }
        for (int i = 0; i < menu.tanks; i++) {
            ElysiumUI.bar(g, leftPos + BAR_X, y + 9, barWidth(), fraction(menu.amount(i), menu.capacity(i)),
                    colour(menu.fluid(i)));
            y += BAR_STEP;
        }
        if (menu.hasSlot()) {
            int sx = leftPos + MachineMenu.SLOT_X - 1;
            int sy = topPos + MachineMenu.SLOT_Y - 1;
            ElysiumUI.slotHinted(g, sx, sy, ElysiumUI.within(mouseX, mouseY, sx, sy, ElysiumPalette.SLOT_SIZE,
                    ElysiumPalette.SLOT_SIZE), Component.literal("F"), ElysiumPalette.PLASMA);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, 8 + col * 18, MachineMenu.INVENTORY_Y + row * 18, mouseX, mouseY);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, 8 + col * 18, MachineMenu.INVENTORY_Y + 58, mouseX, mouseY);
        }
    }

    private void slot(GuiGraphics g, int sx, int sy, int mouseX, int mouseY) {
        int x = leftPos + sx - 1;
        int y = topPos + sy - 1;
        ElysiumUI.slot(g, x, y, ElysiumUI.within(mouseX, mouseY, x, y, ElysiumPalette.SLOT_SIZE, ElysiumPalette.SLOT_SIZE));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        ElysiumUI.heading(g, ElysiumUI.truncate(title, imageWidth - 16), ElysiumPalette.GAP, ElysiumPalette.GAP);
        int y = FIRST_BAR_Y;
        if (menu.energyCapacity() > 0) {
            ElysiumUI.faint(g, Component.translatable("elysiumspace.gui.energy", menu.energy(), menu.energyCapacity()),
                    BAR_X, y);
            y += BAR_STEP;
        }
        for (int i = 0; i < menu.tanks; i++) {
            Fluid fluid = menu.fluid(i);
            Component name = fluid == Fluids.EMPTY ? Component.translatable("elysiumspace.gui.empty")
                    : fluid.getFluidType().getDescription();
            ElysiumUI.faint(g, Component.translatable("elysiumspace.gui.tank", name, menu.amount(i), menu.capacity(i)),
                    BAR_X, y);
            y += BAR_STEP;
        }
        ElysiumUI.faint(g, Component.translatable("container.inventory"), ElysiumPalette.GAP, inventoryLabelY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    private static float fraction(int amount, int capacity) {
        return capacity <= 0 ? 0.0F : (float) amount / capacity;
    }

    private static int colour(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return ElysiumPalette.TEXT_FAINT;
        }
        int tint = IClientFluidTypeExtensions.of(fluid).getTintColor();
        return 0xFF000000 | (tint & 0xFFFFFF);
    }
}
