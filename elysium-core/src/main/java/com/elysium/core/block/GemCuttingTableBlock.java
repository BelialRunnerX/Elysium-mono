package com.elysium.core.block;

import com.elysium.core.menu.GemCuttingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/** The Gem Cutting Table (Apotheosis' AP-13): dust spent to climb a purity. */
public class GemCuttingTableBlock extends Block {

    public GemCuttingTableBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(4.0F, 1200.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer server) {
            server.openMenu(new SimpleMenuProvider((id, inventory, p) ->
                    new GemCuttingMenu(id, inventory), getName()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
