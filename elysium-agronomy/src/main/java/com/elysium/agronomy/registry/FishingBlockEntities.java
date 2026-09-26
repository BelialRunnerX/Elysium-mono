package com.elysium.agronomy.registry;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.block.TackleBoxBlock;
import com.elysium.agronomy.block.WormFarmBlock;
import com.elysium.agronomy.rules.TackleRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The two stations around the water that do something: a worm farm and a
 * tackle box. Named by {@link TackleRules}, like the blocks they sit in.
 */
public final class FishingBlockEntities {

    private FishingBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ElysiumAgronomy.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WormFarmBlock.Farm>>
            WORM_FARM = TYPES.register(TackleRules.WORM_FARM, () -> BlockEntityType.Builder.of(
                    WormFarmBlock.Farm::new,
                    AgronomyBlocks.TACKLE.get(TackleRules.WORM_FARM).get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TackleBoxBlock.Box>>
            TACKLE_BOX = TYPES.register(TackleRules.TACKLE_BOX, () -> BlockEntityType.Builder.of(
                    TackleBoxBlock.Box::new,
                    AgronomyBlocks.TACKLE.get(TackleRules.TACKLE_BOX).get())
            .build(null));

    /** A hopper can feed a tackle box, and the box still refuses what is not tackle. */
    @EventBusSubscriber(modid = ElysiumAgronomy.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Capability {

        private Capability() {
        }

        @SubscribeEvent
        public static void onCapabilities(RegisterCapabilitiesEvent event) {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TACKLE_BOX.get(),
                    (entity, side) -> new InvWrapper(entity));
        }
    }
}
