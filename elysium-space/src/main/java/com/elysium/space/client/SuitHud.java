package com.elysium.space.client;

import com.elysium.lib.client.ElysiumPalette;
import com.elysium.lib.client.ElysiumUI;
import com.elysium.space.ElysiumSpace;
import com.elysium.space.item.SpaceSuitItem;
import com.elysium.space.world.SpaceWorld;
import com.elysium.space.world.SpaceWorlds;
import com.elysium.space.world.SuitAir;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The suit's air gauge, above the hotbar's right end, on worlds that have
 * vacuum. It turns red under a fifth.
 *
 * <p>Drawn through {@link ElysiumUI} rather than with fills of its own, so it
 * is the same bar as the health bar, the machine screen's energy bar and every
 * other bar in the mod. A HUD element is the easiest place in a codebase for a
 * one-off colour to survive, because nothing beside it is there to look wrong.
 */
@EventBusSubscriber(modid = ElysiumSpace.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SuitHud {

    private SuitHud() {
    }

    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, "suit_air");

    @SubscribeEvent
    public static void onLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, ID, SuitHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || !SpaceSuitItem.fullSuit(player)) {
            return;
        }
        SpaceWorld world = SpaceWorlds.of(player.level());
        if (world == null || world.intensity(SpaceWorld.VACUUM) <= 0.0F) {
            return;
        }
        int air = SpaceSuitItem.air(player.getItemBySlot(EquipmentSlot.CHEST));
        int width = 81;
        int x = graphics.guiWidth() / 2 + 10;
        int y = graphics.guiHeight() - 59;
        float left = (float) air / SuitAir.CAPACITY;
        boolean low = air * 5 < SuitAir.CAPACITY;
        int colour = low ? ElysiumPalette.BAD : ElysiumPalette.GOOD;
        ElysiumUI.plate(graphics, x - 1, y - 1, width + 2, ElysiumPalette.BAR_HEIGHT + 2);
        ElysiumUI.bar(graphics, x, y, width, left, colour);
        ElysiumUI.value(graphics,
                Component.translatable("elysiumspace.suit.gauge", air / SuitAir.DRAIN_PER_SECOND),
                x, y - 10, low ? ElysiumPalette.BAD : ElysiumPalette.TEXT);
    }
}
