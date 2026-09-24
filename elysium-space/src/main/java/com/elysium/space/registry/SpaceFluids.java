package com.elysium.space.registry;

import com.elysium.space.ElysiumSpace;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Stellaris's four fluids, as NeoForge fluids so any mod's pipes and tanks
 * carry them. Oil and fuel are liquids with blocks and buckets. Oxygen and
 * hydrogen are gases: they live only in tanks and pipes, with no world block
 * or bucket (glass bottles take oxygen from a separator as canisters instead).
 */
public final class SpaceFluids {

    private SpaceFluids() {
    }

    public static final DeferredRegister<FluidType> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, ElysiumSpace.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, ElysiumSpace.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ElysiumSpace.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ElysiumSpace.MODID);

    /** One fluid: its type, its source and flowing forms, and (liquids only) a block and a bucket. */
    public static final class Kind {
        public final String name;
        public final DeferredHolder<FluidType, FluidType> type;
        public final DeferredHolder<Fluid, BaseFlowingFluid.Source> source;
        public final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
        public final DeferredBlock<LiquidBlock> block;
        public final DeferredItem<Item> bucket;
        public final int tint;

        Kind(String name, FluidType.Properties props, boolean liquid, int tint) {
            this.name = name;
            this.tint = tint;
            this.type = TYPES.register(name, () -> new FluidType(props));
            Kind self = this;
            BaseFlowingFluid.Properties[] fluidProps = new BaseFlowingFluid.Properties[1];
            this.source = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(fluidProps[0]));
            this.flowing = FLUIDS.register("flowing_" + name, () -> new BaseFlowingFluid.Flowing(fluidProps[0]));
            this.block = liquid ? BLOCKS.register(name, () -> new LiquidBlock((FlowingFluid) self.source.get(),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.WATER))) : null;
            this.bucket = liquid ? ITEMS.registerItem(name + "_bucket", p -> new BucketItem(self.source.get(),
                    p.craftRemainder(Items.BUCKET).stacksTo(1))) : null;
            BaseFlowingFluid.Properties fp = new BaseFlowingFluid.Properties(type, source, flowing);
            if (liquid) {
                fp.block(block).bucket(bucket).tickRate(10).slopeFindDistance(3).levelDecreasePerBlock(2);
            }
            fluidProps[0] = fp;
        }
    }

    public static final Kind OIL = new Kind("oil", FluidType.Properties.create()
            .density(1400).viscosity(4000).canSwim(true).canDrown(true), true, 0xFF1E1714);
    public static final Kind FUEL = new Kind("fuel", FluidType.Properties.create()
            .density(900).viscosity(1500), true, 0xFFE0B040);
    public static final Kind DIESEL = new Kind("diesel", FluidType.Properties.create()
            .density(850).viscosity(1200), true, 0xFF6B5A2E);
    public static final Kind OXYGEN = new Kind("oxygen", FluidType.Properties.create()
            .density(-1).viscosity(100).temperature(90), false, 0xFFB8E0FF);
    public static final Kind HYDROGEN = new Kind("hydrogen", FluidType.Properties.create()
            .density(-10).viscosity(80).temperature(20), false, 0xFFE8E4FF);

    public static final Kind[] ALL = {OIL, FUEL, DIESEL, OXYGEN, HYDROGEN};
}
