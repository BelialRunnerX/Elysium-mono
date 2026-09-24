package com.elysium.enchanting.block;

import com.elysium.enchanting.menu.ScriptoriumMenu;
import com.elysium.enchanting.rules.LibraryRules;
import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Scriptorium: where the books nobody wants become the book somebody does.
 *
 * <h2>Two sizes, and the difference is only how much it holds</h2>
 *
 * The small one is a mid-game block: enough to turn a chest of junk books into
 * something worth carrying, not enough to bank for the highest levels. The
 * great one holds sixty-four times as much, which is what makes it worth the
 * walk — and holding is the only thing that changes, because two blocks that
 * work differently are two things to learn and this is one thing at two sizes.
 */
public class ScriptoriumBlock extends BaseEntityBlock {

    /** How much one of these holds, per enchantment. */
    public enum Size {
        SHELF(LibraryRules.SHELF_CAPACITY),
        GRAND(LibraryRules.GRAND_CAPACITY);

        public final int capacity;

        Size(int capacity) {
            this.capacity = capacity;
        }
    }

    public static final MapCodec<ScriptoriumBlock> CODEC =
            simpleCodec(properties -> new ScriptoriumBlock(Size.SHELF));

    private final Size size;

    public ScriptoriumBlock(Size size) {
        super(BlockBehaviour.Properties.of()
                .mapColor(size == Size.GRAND ? MapColor.COLOR_YELLOW : MapColor.WOOD)
                .strength(4.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.WOOD));
        this.size = size;
    }

    public Size size() {
        return size;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScriptoriumBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer server) {
            server.openMenu(new Provider(pos, this), buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private record Provider(BlockPos pos, ScriptoriumBlock block) implements MenuProvider {

        @Override
        public Component getDisplayName() {
            return Component.translatable(block.size() == Size.GRAND
                    ? "block.elysiumenchanting.grand_scriptorium"
                    : "block.elysiumenchanting.scriptorium");
        }

        @Nullable
        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            return new ScriptoriumMenu(id, inventory,
                    ContainerLevelAccess.create(player.level(), pos), pos);
        }
    }
}
