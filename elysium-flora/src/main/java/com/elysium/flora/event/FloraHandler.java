package com.elysium.flora.event;

import com.elysium.flora.ElysiumFlora;
import com.elysium.flora.registry.FloraItems;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;

/**
 * How long emberwood burns.
 *
 * <h2>Why an event and not a tag</h2>
 *
 * Because a tag can say that something is fuel and cannot say for how long. The
 * numbers are {@link com.elysium.flora.rules.FloraRules}', and the harness pins
 * the relationships between them, so this file is only the wiring.
 */
@EventBusSubscriber(modid = ElysiumFlora.MODID)
public final class FloraHandler {

    private FloraHandler() {
    }

    /**
     * An axe on an emberwood log or wood strips it. {@link FloraBlocks#stripping}
     * said what becomes what, and nothing read it until the mechanics survey of
     * 27 September 2026; the axis is kept, as the game keeps it.
     */
    @SubscribeEvent
    public static void onToolModification(net.neoforged.neoforge.event.level.BlockEvent.BlockToolModificationEvent event) {
        if (event.getItemAbility() != net.neoforged.neoforge.common.ItemAbilities.AXE_STRIP || event.isSimulated()) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState state = event.getState();
        net.minecraft.world.level.block.Block stripped = com.elysium.flora.registry.FloraBlocks.stripping().get(state.getBlock());
        if (stripped == null) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState after = stripped.defaultBlockState();
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)
                && after.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)) {
            after = after.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
                    state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS));
        }
        event.setFinalState(after);
    }

    @SubscribeEvent
    public static void onBurnTime(FurnaceFuelBurnTimeEvent event) {
        int ticks = FloraItems.burnTicks(event.getItemStack().getItem());
        if (ticks > 0) {
            event.setBurnTime(ticks);
        }
    }
}
