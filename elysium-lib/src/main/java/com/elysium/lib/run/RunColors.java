package com.elysium.lib.run;

import com.elysium.lib.ElysiumLib;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Tints every declared run: the body white or the dye, the plate the run's
 * own colour or the dye. One handler for all of them, registered once the
 * modules have declared their blocks.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RunColors {

    private RunColors() {
    }

    @SubscribeEvent
    public static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        for (Block block : Runs.declared()) {
            event.register(Runs::tint, block);
        }
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        for (Block block : Runs.declared()) {
            event.register(Runs::itemTint, block.asItem());
        }
    }
}
