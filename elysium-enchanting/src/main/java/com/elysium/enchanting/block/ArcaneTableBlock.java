package com.elysium.enchanting.block;

import com.elysium.enchanting.menu.ArcaneTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import com.elysium.lib.sound.ElysiumVoices;

/**
 * The Arcane Table: an enchanting table that reads the room.
 *
 * <h2>Why a new block rather than a replacement</h2>
 *
 * Vanilla's enchanting table is a block thousands of recipes, advancements,
 * structures and other mods already name. Replacing its behaviour would change
 * all of them at once and give a player no way back; standing beside it gives
 * them a choice and gives a pack a way to have both.
 */
public class ArcaneTableBlock extends Block {

    public ArcaneTableBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(5.0F, 1200.0F)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> 7)
                .sound(SoundType.STONE));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer server) {
            ElysiumVoices.click(level, pos, "station");
            server.openMenu(new Provider(pos), buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Carries the table's position, which is what the menu needs to read the room. */
    private record Provider(BlockPos pos) implements MenuProvider {

        @Override
        public Component getDisplayName() {
            return Component.translatable("block.elysiumenchanting.arcane_table");
        }

        @Nullable
        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            return new ArcaneTableMenu(id, inventory,
                    ContainerLevelAccess.create(player.level(), pos), pos);
        }
    }
}
