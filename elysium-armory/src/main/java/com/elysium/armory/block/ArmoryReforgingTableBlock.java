package com.elysium.armory.block;

import com.elysium.armory.menu.ArmoryReforgingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.elysium.lib.sound.ElysiumVoices;

/**
 * The Reforging Table (Apotheosis' AP-16): where a bad roll is rolled again.
 */
public class ArmoryReforgingTableBlock extends Block {

    public ArmoryReforgingTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer server) {
            ElysiumVoices.click(level, pos, "station");
            server.openMenu(new SimpleMenuProvider((id, inventory, p) ->
                    new ArmoryReforgingMenu(id, inventory), getName()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
