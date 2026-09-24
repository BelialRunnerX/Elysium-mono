package com.elysium.comforts.block;

import com.elysium.comforts.registry.ComfortBlockEntities;
import com.elysium.comforts.rules.ComfortRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A grave: everything you were carrying, where you fell.
 *
 * <h2>Three ways of getting your things back, and all of them work</h2>
 *
 * Open it and everything goes back into your inventory. Break it and
 * everything falls on the floor. Let a piston shove it and it refuses to
 * move — because the one thing a grave may never do is be somewhere your
 * things are not.
 */
public class GraveBlock extends BaseEntityBlock {

    public static final MapCodec<GraveBlock> CODEC = simpleCodec(GraveBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 4.0D, 14.0D, 14.0D, 12.0D);

    public GraveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Nothing moves a grave.
     *
     * <p>A piston that could push one could push it into a wall, and a block
     * pushed into a wall is destroyed along with whatever was inside it.
     */
    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GraveBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        if (!(level.getBlockEntity(pos) instanceof GraveBlockEntity grave)) {
            return InteractionResult.PASS;
        }
        boolean owner = grave.belongsTo(player);
        boolean operator = player.hasPermissions(2);
        long age = level.getGameTime() - grave.madeAt();
        if (!ComfortRules.mayOpen(owner, operator, age)) {
            player.displayClientMessage(Component.translatable("elysiumcomforts.grave.theirs",
                    grave.ownerName(),
                    String.valueOf(Math.max(1L, ComfortRules.publicIn(age) / 20L / 60L))),
                    true);
            return InteractionResult.CONSUME;
        }
        grave.giveBackTo(player);
        level.removeBlock(pos, false);
        return InteractionResult.CONSUME;
    }

    /**
     * Broken, rather than opened.
     *
     * <p>Everything falls on the floor. A grave that could be destroyed with
     * its contents inside would be the one thing in this module that loses a
     * player's belongings, which is the thing it exists to prevent.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof GraveBlockEntity grave) {
            Containers.dropContents(level, pos, grave.spill());
            grave.scatterExperience(level, pos);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** The working half: whose it is, what is in it, and when it was made. */
    public static class GraveBlockEntity extends BlockEntity {

        private final List<ItemStack> held = new ArrayList<>();
        private UUID owner;
        private String ownerName = "";
        private int experience;
        private long madeAt;

        public GraveBlockEntity(BlockPos pos, BlockState state) {
            super(ComfortBlockEntities.GRAVE.get(), pos, state);
        }

        /**
         * Fill it, once, from what a player was carrying.
         *
         * <p>Sized to what it is handed rather than to a number decided in
         * advance — which is the whole of "nothing is ever lost", and the one
         * thing every gravestone mod that has eaten a backpack got wrong.
         */
        public void fill(Player player, List<ItemStack> carried, int experienceHad,
                         long now) {
            owner = player.getUUID();
            ownerName = player.getGameProfile().getName();
            held.clear();
            int room = ComfortRules.slotsFor(carried.size());
            for (int at = 0; at < carried.size() && at < room; at++) {
                held.add(carried.get(at).copy());
            }
            experience = ComfortRules.experienceKept(experienceHad);
            madeAt = now;
            setChanged();
        }

        public boolean belongsTo(Player player) {
            return owner != null && owner.equals(player.getUUID());
        }

        public String ownerName() {
            return ownerName.isBlank() ? "?" : ownerName;
        }

        public long madeAt() {
            return madeAt;
        }

        public int held() {
            return held.size();
        }

        /** Everything back, and whatever will not fit on the floor. */
        public void giveBackTo(Player player) {
            for (ItemStack stack : held) {
                if (stack.isEmpty()) {
                    continue;
                }
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
            held.clear();
            if (experience > 0) {
                player.giveExperiencePoints(experience);
                experience = 0;
            }
            setChanged();
        }

        /** Everything in it, for when it is broken rather than opened. */
        public SimpleContainer spill() {
            SimpleContainer out = new SimpleContainer(Math.max(1, held.size()));
            for (int at = 0; at < held.size(); at++) {
                out.setItem(at, held.get(at));
            }
            held.clear();
            return out;
        }

        /** And the levels with it. */
        public void scatterExperience(Level level, BlockPos pos) {
            if (experience > 0 && !level.isClientSide) {
                net.minecraft.world.entity.ExperienceOrb.award(
                        (net.minecraft.server.level.ServerLevel) level,
                        net.minecraft.world.phys.Vec3.atCenterOf(pos), experience);
                experience = 0;
            }
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            held.clear();
            ListTag list = tag.getList("Held", 10);
            for (int at = 0; at < list.size(); at++) {
                ItemStack.parse(registries, list.getCompound(at)).ifPresent(held::add);
            }
            owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
            ownerName = tag.getString("OwnerName");
            experience = tag.getInt("Experience");
            madeAt = tag.getLong("MadeAt");
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            ListTag list = new ListTag();
            for (ItemStack stack : held) {
                if (!stack.isEmpty()) {
                    list.add(stack.save(registries, new CompoundTag()));
                }
            }
            tag.put("Held", list);
            if (owner != null) {
                tag.putUUID("Owner", owner);
            }
            tag.putString("OwnerName", ownerName);
            tag.putInt("Experience", experience);
            tag.putLong("MadeAt", madeAt);
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putString("OwnerName", ownerName);
            return tag;
        }

        @Nullable
        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }
    }
}
