package com.elysium.industry.block;

import com.elysium.industry.registry.IndustryBlockEntities;
import com.elysium.industry.rules.IndustryRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Four turbines in one block, and it drinks like four turbines.
 *
 * <h2>Why it is not simply better</h2>
 *
 * {@link IndustryRules#DIESEL_HASTE} is used twice — once as the multiple of
 * the turbine's output, once as the rate it gets through the same fuel — so an
 * engine and four turbines cost the same coal per unit of power. What a player
 * buys with it is <em>density</em>: one block instead of four, one fuel hopper
 * instead of four, and a single {@code MEDIUM} connection instead of four
 * {@code LOW} ones that would have burned the wire between them.
 *
 * <p>That is deliberately a smaller step than it looks like on the tooltip. A
 * generator that was four times the power for the same fuel would make the
 * turbine a mistake rather than a rung, and the ladder of this mod is built
 * out of rungs that stay worth having.
 */
public class DieselEngineBlock extends FueledBlock {

    public static final MapCodec<DieselEngineBlock> CODEC = simpleCodec(DieselEngineBlock::new);

    public DieselEngineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Override
    public IndustryRules.Generator output() {
        return IndustryRules.DIESEL_ENGINE;
    }

    /**
     * The same fuel, gone in a quarter of the time.
     *
     * Rounded up rather than down, so that the cheapest fuel in the game still
     * lasts at least one tick in it. A fuel that an engine accepted and then
     * consumed for nothing would be a trap with no message attached.
     */
    @Override
    public int burns(ItemStack fuel) {
        int furnace = Math.max(0, fuel.getBurnTime(null));
        if (furnace <= 0) {
            return 0;
        }
        return Math.max(1, furnace / IndustryRules.DIESEL_HASTE);
    }

    @Override
    public String saying() {
        return "elysiumindustry.diesel_engine";
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FueledBlockEntity(IndustryBlockEntities.DIESEL_ENGINE.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, IndustryBlockEntities.DIESEL_ENGINE.get());
    }
}
