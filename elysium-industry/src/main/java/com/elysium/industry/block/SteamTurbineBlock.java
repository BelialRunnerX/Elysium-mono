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
 * A firebox and a boiler. The first generator anybody builds.
 *
 * <p>It takes whatever a furnace takes, which is the point: a player reaching
 * Age II already has coal, and the first grid should light on the day they
 * build the alternator rather than after a fuel chain they have not met yet.
 * It eats forever, it is not efficient, and both of those are the reason the
 * diesel engine and then the reactor are worth reaching.
 */
public class SteamTurbineBlock extends FueledBlock {

    public static final MapCodec<SteamTurbineBlock> CODEC = simpleCodec(SteamTurbineBlock::new);

    public SteamTurbineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Override
    public IndustryRules.Generator output() {
        return IndustryRules.STEAM_TURBINE;
    }

    @Override
    public int burns(ItemStack fuel) {
        return Math.max(0, fuel.getBurnTime(null));
    }

    @Override
    public String saying() {
        return "elysiumindustry.steam_turbine";
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FueledBlockEntity(IndustryBlockEntities.STEAM_TURBINE.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, IndustryBlockEntities.STEAM_TURBINE.get());
    }
}
