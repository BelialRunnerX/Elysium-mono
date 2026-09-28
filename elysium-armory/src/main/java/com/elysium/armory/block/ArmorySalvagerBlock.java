package com.elysium.armory.block;

import com.elysium.armory.menu.ArmorySalvagerMenu;
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
 * The Salvager (Silent Gear's SG-83, Apotheosis' AP-15): where gear is taken
 * back apart.
 *
 * <p>Two rows that the one block answers for, because they are the same idea
 * told twice: parts come back as parts, and affixes come back as essence.
 */
public class ArmorySalvagerBlock extends Block {

    public ArmorySalvagerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer server) {
            ElysiumVoices.click(level, pos, "station");
            server.openMenu(new SimpleMenuProvider((id, inventory, p) ->
                    new ArmorySalvagerMenu(id, inventory), getName()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
