package com.elysium.enchanting.block;

import com.elysium.enchanting.menu.InfusionMenu;
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
 * The Infusion Basin: the bench where nothing is left to chance.
 *
 * <h2>Why it is a second block</h2>
 *
 * It could have been a tab on the Arcane Table. It is not, because the two
 * benches want opposite rooms and a player should be able to build both. A
 * table wants quanta and a basin does not care about it; a basin wants
 * rectification to bring the price down and a table wants it to keep the
 * upside. Two blocks means two rooms, or one room that is a compromise, and
 * the compromise is a decision rather than a default.
 *
 * <p>It reads the same room, with {@code EnchantingSurvey}, so nothing a
 * player learned about building for one bench is wasted on the other.
 */
public class InfusionBasinBlock extends Block {

    public InfusionBasinBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_CYAN)
                .strength(5.0F, 1200.0F)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> 5)
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

    private record Provider(BlockPos pos) implements MenuProvider {

        @Override
        public Component getDisplayName() {
            return Component.translatable("block.elysiumenchanting.infusion_basin");
        }

        @Nullable
        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            return new InfusionMenu(id, inventory,
                    ContainerLevelAccess.create(player.level(), pos), pos);
        }
    }
}
