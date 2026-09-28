package com.elysium.core.block;

import com.elysium.core.menu.AugmentingMenu;
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
import com.elysium.lib.sound.ElysiumVoices;

/** The Augmenting Table (Apotheosis' AP-17): where a sigil is spent on a piece. */
public class AugmentingTableBlock extends Block {

    public AugmentingTableBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(4.0F, 1200.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer server) {
            ElysiumVoices.click(level, pos, "station");
            server.openMenu(new SimpleMenuProvider((id, inventory, p) ->
                    new AugmentingMenu(id, inventory), getName()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
