package com.elysium.agronomy.block;

import com.elysium.agronomy.data.AgronomyData;
import com.elysium.agronomy.item.EssenceSeed;
import com.elysium.agronomy.registry.AgronomyBlockEntities;
import com.elysium.agronomy.rules.AgronomyRules;
import com.elysium.agronomy.rules.PotRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A pot: farming without a field.
 *
 * <h2>Why a pot rather than farmland</h2>
 *
 * Because a resource farm on farmland is a flat green rectangle a hundred
 * blocks across, and because farmland needs water, light, a fence and a player
 * who does not sprint. A pot is one block that does the whole job, can be
 * stacked in a room, and can be pointed at by a hopper — which is what anybody
 * building a resource farm actually wants, and is the reason Botany Pots was
 * in the pack at all.
 *
 * <h2>Light, and the one condition it puts on a player</h2>
 *
 * Below {@link AgronomyRules#LEAST_LIGHT} nothing grows. That is the only
 * requirement, and it exists so that a farm has to be lit — so a room full of
 * pots is a room somebody built rather than a hole they dug. Its blockstate
 * carries four steps of growth so the room reads at a glance and from the
 * door, which is this pack's rule everywhere: the block says what it is doing.
 *
 * <h2>The seed stays</h2>
 *
 * A harvested pot keeps its seed and starts again. Replanting is not a
 * decision, it is a chore, and a farm that has to be replanted is one a player
 * automates by never visiting — which is the same outcome with more hoppers.
 */
public class PotBlock extends BaseEntityBlock {

    public static final MapCodec<PotBlock> CODEC = simpleCodec(PotBlock::new);

    /** How far through the growth it is, in four steps. */
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);

    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 12, 14);

    public PotBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PotBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != AgronomyBlockEntities.POT.get()) {
            return null;
        }
        return (world, pos, blockState, entity) ->
                ((PotBlockEntity) entity).grow(world, pos, blockState);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (held.isEmpty() || !(level.getBlockEntity(pos) instanceof PotBlockEntity pot)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            Component said = pot.use(held);
            if (said != null) {
                player.displayClientMessage(said, true);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PotBlockEntity pot)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack out = pot.takeAll();
            if (!out.isEmpty()) {
                player.getInventory().placeItemBackInInventory(out);
            } else {
                player.displayClientMessage(pot.says(level, pos), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof PotBlockEntity pot) {
            Containers.dropContents(level, pos, pot.spill());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /**
     * The working half: a seed, the soil it is in, a growth, and what it made.
     *
     * <p>Two kinds of thing grow in a pot. An essence seed grows on its own,
     * as it always has. A sprout (anything in the {@code botany_sprout}
     * folder) wants soil first: the soil slot takes anything the
     * {@code botany_soil} folder names, and a sprout goes in only over a soil
     * that takes what it needs. Both folders are read by {@link AgronomyData};
     * whether a soil takes a sprout is {@link PotRules#growsIn}, and how long
     * it takes is {@link PotRules.Sprout#ticksIn}.
     */
    public static class PotBlockEntity extends BlockEntity {

        public static final int SEED = 0;
        public static final int OUT = 1;
        public static final int SOIL = 2;

        private final ItemStackHandler items = new ItemStackHandler(3) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (slot == SEED || slot == SOIL) {
                    grown = 0;
                }
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                if (slot == SOIL) {
                    return AgronomyData.bedFor(blockOf(stack)) != null;
                }
                return slot == SEED && (stack.getItem() instanceof EssenceSeed
                        || AgronomyData.sownFor(nameOf(stack)) != null);
            }
        };

        private int grown;

        public PotBlockEntity(BlockPos pos, BlockState state) {
            super(AgronomyBlockEntities.POT.get(), pos, state);
        }

        public ItemStackHandler items() {
            return items;
        }

        @Nullable
        private EssenceSeed seed() {
            return items.getStackInSlot(SEED).getItem() instanceof EssenceSeed sown
                    ? sown : null;
        }

        /** The full name of an item, which is what the data files use. */
        public static String nameOf(ItemStack stack) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }

        /** The full name of the block an item places, or empty when it places none. */
        public static String blockOf(ItemStack stack) {
            return stack.getItem() instanceof BlockItem placed
                    ? BuiltInRegistries.BLOCK.getKey(placed.getBlock()).toString() : "";
        }

        /** The soil it is filled with, or null. */
        @Nullable
        public AgronomyData.Bed bed() {
            ItemStack soil = items.getStackInSlot(SOIL);
            return soil.isEmpty() ? null : AgronomyData.bedFor(blockOf(soil));
        }

        /** The sprout planted in it, or null when it is empty or an essence seed. */
        @Nullable
        public AgronomyData.Sown sown() {
            ItemStack seed = items.getStackInSlot(SEED);
            return seed.isEmpty() || seed.getItem() instanceof EssenceSeed
                    ? null : AgronomyData.sownFor(nameOf(seed));
        }

        /**
         * Something used on it: a soil fills it, a seed plants it.
         *
         * <p>A soil goes in only when there is none; a sprout only over a soil
         * that takes it, which is the one moment a player can be told why.
         */
        @Nullable
        public Component use(ItemStack held) {
            AgronomyData.Bed filling = AgronomyData.bedFor(blockOf(held));
            if (filling != null) {
                ItemStack there = items.getStackInSlot(SOIL);
                if (there.isEmpty()) {
                    items.setStackInSlot(SOIL, held.copyWithCount(1));
                    held.shrink(1);
                    there = items.getStackInSlot(SOIL);
                }
                return Component.translatable("elysiumagronomy.pot.bedded",
                        there.getHoverName());
            }
            AgronomyData.Sown planting = AgronomyData.sownFor(nameOf(held));
            if (planting != null && !(held.getItem() instanceof EssenceSeed)) {
                AgronomyData.Bed under = bed();
                if (under == null) {
                    return Component.translatable("elysiumagronomy.pot.soilless");
                }
                if (!PotRules.growsIn(under.soil(), planting.sprout())) {
                    return Component.translatable("elysiumagronomy.pot.wrong_soil",
                            held.getHoverName(), items.getStackInSlot(SOIL).getHoverName(),
                            planting.sprout().needs());
                }
                if (items.getStackInSlot(SEED).isEmpty()) {
                    items.setStackInSlot(SEED, held.copyWithCount(1));
                    held.shrink(1);
                }
                return null;
            }
            plant(held);
            return null;
        }

        /** Put an essence seed in it. True when it took one. */
        public boolean plant(ItemStack held) {
            if (!(held.getItem() instanceof EssenceSeed)) {
                return false;
            }
            if (!items.insertItem(SEED, held.copyWithCount(1), false).isEmpty()) {
                return false;
            }
            held.shrink(1);
            return true;
        }

        /** Take what it has grown. */
        public ItemStack takeAll() {
            return items.extractItem(OUT, items.getStackInSlot(OUT).getCount(), false);
        }

        public SimpleContainer spill() {
            return new SimpleContainer(items.getStackInSlot(SEED),
                    items.getStackInSlot(OUT), items.getStackInSlot(SOIL));
        }

        /**
         * How fast it is growing, asking the world for the two things the
         * rules need and nothing else.
         */
        public float speed(Level level, BlockPos pos) {
            int light = level.getMaxLocalRawBrightness(pos.above());
            AcceleratorBlock.AcceleratorBlockEntity driver = accelerator(level, pos);
            return AgronomyRules.potSpeed(light, driver != null,
                    driver == null ? 0.0F : driver.share());
        }

        /** The accelerator beside it, if there is one. */
        @Nullable
        public static AcceleratorBlock.AcceleratorBlockEntity accelerator(Level level,
                                                                         BlockPos pos) {
            for (Direction face : Direction.values()) {
                if (level.getBlockEntity(pos.relative(face))
                        instanceof AcceleratorBlock.AcceleratorBlockEntity driver) {
                    return driver;
                }
            }
            return null;
        }

        /** What it would say if it could talk. */
        public Component says(Level level, BlockPos pos) {
            EssenceSeed sown = seed();
            if (sown == null) {
                return sproutSays(level, pos);
            }
            int light = level.getMaxLocalRawBrightness(pos.above());
            if (light < AgronomyRules.LEAST_LIGHT) {
                return Component.translatable("elysiumagronomy.pot.dark",
                        String.valueOf(light), String.valueOf(AgronomyRules.LEAST_LIGHT));
            }
            float speed = speed(level, pos);
            int left = AgronomyRules.ticksAtSpeed(sown.ticks(), speed)
                    - grown / AgronomyRules.WORK_FULL;
            return Component.translatable("elysiumagronomy.pot.growing",
                    items.getStackInSlot(SEED).getHoverName().getString(),
                    String.format("%.1f", speed),
                    String.valueOf(Math.max(0, left) / 20));
        }

        /** What it says when what is in it is a sprout, or nothing. */
        private Component sproutSays(Level level, BlockPos pos) {
            AgronomyData.Sown sprout = sown();
            if (sprout == null) {
                return Component.translatable("elysiumagronomy.pot.bare");
            }
            AgronomyData.Bed under = bed();
            if (under == null) {
                return Component.translatable("elysiumagronomy.pot.soilless");
            }
            if (!PotRules.growsIn(under.soil(), sprout.sprout())) {
                return Component.translatable("elysiumagronomy.pot.wrong_soil",
                        items.getStackInSlot(SEED).getHoverName(),
                        items.getStackInSlot(SOIL).getHoverName(),
                        sprout.sprout().needs());
            }
            int light = level.getMaxLocalRawBrightness(pos.above());
            if (light < AgronomyRules.LEAST_LIGHT) {
                return Component.translatable("elysiumagronomy.pot.dark",
                        String.valueOf(light), String.valueOf(AgronomyRules.LEAST_LIGHT));
            }
            float speed = speed(level, pos);
            int left = sprout.sprout().ticksIn(under.soil(), speed) - grown;
            return Component.translatable("elysiumagronomy.pot.growing",
                    items.getStackInSlot(SEED).getHoverName().getString(),
                    String.format("%.1f", speed * under.soil().speed()),
                    String.valueOf(Math.max(0, left) / 20));
        }

        /**
         * Whether a harvest fits in the output.
         *
         * <p>Asked of the stacks rather than of {@code insertItem}: the output
         * slot refuses anything a hopper offers it, so asking it whether it
         * would take a harvest was asking a door locked from outside whether
         * it was open, and the answer was no every time: a pot that grew right
         * up to the end and never once gave anything.
         */
        private boolean roomFor(ItemStack harvest) {
            ItemStack there = items.getStackInSlot(OUT);
            if (there.isEmpty()) {
                return true;
            }
            return ItemStack.isSameItemSameComponents(there, harvest)
                    && there.getCount() + harvest.getCount() <= there.getMaxStackSize();
        }

        /** One tick of growing a sprout, in the soil it is in, at the speed the pot is. */
        private void growSprout(Level level, BlockPos pos, BlockState state) {
            AgronomyData.Sown sprout = sown();
            AgronomyData.Bed under = bed();
            if (sprout == null || under == null
                    || !PotRules.growsIn(under.soil(), sprout.sprout())) {
                settle(level, pos, state, 0);
                return;
            }
            float speed = speed(level, pos);
            if (speed <= 0.0F) {
                return;                        // too dark: it waits, it does not wilt
            }
            ItemStack harvest = new ItemStack(BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse(sprout.drop())), sprout.sprout().amount());
            if (harvest.isEmpty() || !roomFor(harvest)) {
                return;
            }
            grown++;
            int whole = sprout.sprout().ticksIn(under.soil(), speed);
            if (grown < whole) {
                settle(level, pos, state, Math.min(3, grown * 4 / whole));
                return;
            }
            grown = 0;
            items.setStackInSlot(OUT, grow(items.getStackInSlot(OUT), harvest));
            settle(level, pos, state, 0);
            setChanged();
        }

        /** One tick of growing. */
        public void grow(Level level, BlockPos pos, BlockState state) {
            EssenceSeed sown = seed();
            if (sown == null) {
                growSprout(level, pos, state);
                return;
            }
            int done = AgronomyRules.workPerTick(speed(level, pos));
            if (done <= 0) {
                return;
            }
            ItemStack harvest = new ItemStack(sown.essence(), sown.yield());
            if (!roomFor(harvest)) {
                return;                        // no room; stop rather than lose it
            }
            grown += done;
            int whole = AgronomyRules.workFor(sown.ticks());
            if (grown < whole) {
                settle(level, pos, state, Math.min(3, grown * 4 / whole));
                return;
            }
            grown = 0;
            // Set rather than inserted: isItemValid guards what a hopper may
            // push in, and a pot is allowed to fill its own output.
            items.setStackInSlot(OUT, grow(items.getStackInSlot(OUT), harvest));
            settle(level, pos, state, 0);
            setChanged();
        }

        private static ItemStack grow(ItemStack there, ItemStack added) {
            if (there.isEmpty()) {
                return added;
            }
            ItemStack more = there.copy();
            more.grow(added.getCount());
            return more;
        }

        /**
         * Put the growth on the blockstate, and only when the step moved.
         *
         * A room of forty pots would otherwise send forty block updates a
         * second to every player who can see the room.
         */
        private void settle(Level level, BlockPos pos, BlockState state, int step) {
            if (state.hasProperty(AGE) && state.getValue(AGE) != step) {
                level.setBlock(pos, state.setValue(AGE, step), Block.UPDATE_ALL);
            }
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            items.deserializeNBT(registries, tag.getCompound("items"));
            grown = Math.max(0, tag.getInt("grown"));
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("items", items.serializeNBT(registries));
            tag.putInt("grown", grown);
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            CompoundTag tag = super.getUpdateTag(registries);
            saveAdditional(tag, registries);
            return tag;
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }
    }
}
