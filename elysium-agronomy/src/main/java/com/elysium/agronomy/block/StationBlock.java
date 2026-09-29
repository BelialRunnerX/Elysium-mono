package com.elysium.agronomy.block;

import com.elysium.agronomy.data.AgronomyData;
import com.elysium.agronomy.item.KnifeItem;
import com.elysium.agronomy.registry.AgronomyBlockEntities;
import com.elysium.agronomy.rules.KitchenRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One of the kitchen's four stations: a pot, a pan, a board or an oven.
 *
 * <h2>How it is used</h2>
 *
 * There is no screen. A player puts things in by using them on the block, one
 * at a time; uses it with an empty hand to start it once what is in it is
 * exactly a recipe, and again to take what it made; and sneaks with an empty
 * hand to have the ingredients back. The board takes a knife in place of the
 * empty hand: a knife used on a board cuts the first thing on it that that
 * knife cuts, and wears by {@link KitchenRules#KNIFE_WEAR}. A kitchen knife and
 * the fillet knife cut different things ({@link KnifeItem#tool}).
 *
 * <p>Why it waits to be told rather than starting the moment it can: dough
 * alone on a board is pasta and dough with wheat is a pie crust. A board that
 * began on the pasta as the dough went down would make a crust only for a
 * player quick enough to add the wheat inside ten seconds. So a station starts
 * on the empty hand, with what it has, and {@link KitchenRules#match} says
 * exactly what that is.
 *
 * <h2>What it decides for itself</h2>
 *
 * Nothing. Which station this is comes from the block; what it can make comes
 * from {@link AgronomyData}, read off disk; whether these contents are a recipe
 * is {@link KitchenRules#match}; whether it needs a fire under it is
 * {@link KitchenRules#needsHeat}, and what counts as a fire is
 * {@link KitchenRules#isHeat}. A pot with a hand-typed list of fuels is a pot
 * whose fuel list the harness has never seen.
 */
public class StationBlock extends BaseEntityBlock {

    public static final MapCodec<StationBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    propertiesCodec(),
                    Codec.STRING.fieldOf("station").forGetter(StationBlock::station))
            .apply(instance, StationBlock::new));

    private static final VoxelShape POT_SHAPE = box(2, 0, 2, 14, 10, 14);
    private static final VoxelShape PAN_SHAPE = box(1, 0, 1, 15, 3, 15);
    private static final VoxelShape BOARD_SHAPE = box(1, 0, 1, 15, 2, 15);

    private final String station;

    public StationBlock(Properties properties, String station) {
        super(properties);
        if (!KitchenRules.STATIONS.contains(station)) {
            throw new IllegalArgumentException("no such station: " + station);
        }
        this.station = station;
    }

    /** Which of the four this is. */
    public String station() {
        return station;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        if (KitchenRules.POT.equals(station)) {
            return POT_SHAPE;
        }
        if (KitchenRules.SKILLET.equals(station)) {
            return PAN_SHAPE;
        }
        if (KitchenRules.BOARD.equals(station)) {
            return BOARD_SHAPE;
        }
        return super.getShape(state, level, pos, context);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != AgronomyBlockEntities.STATION.get()) {
            return null;
        }
        return (world, pos, blockState, entity) ->
                ((StationBlockEntity) entity).cook(world, pos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (held.isEmpty()
                || !(level.getBlockEntity(pos) instanceof StationBlockEntity entity)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            Component said = held.getItem() instanceof KnifeItem
                    ? entity.slice(held, player, hand)
                    : entity.offer(held, level, pos);
            if (said != null) {
                player.displayClientMessage(said, true);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof StationBlockEntity entity)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            Component said = player.isShiftKeyDown()
                    ? entity.giveBack(player)
                    : entity.serve(player, level, pos);
            if (said != null) {
                player.displayClientMessage(said, true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof StationBlockEntity entity) {
            Containers.dropContents(level, pos, entity.spill());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** The working half: what is in it, what it is making, and what it made. */
    public static class StationBlockEntity extends BlockEntity {

        private final List<ItemStack> contents = new ArrayList<>();
        private final List<ItemStack> made = new ArrayList<>();
        private boolean cooking;
        private String making = "";
        private int progress;

        public StationBlockEntity(BlockPos pos, BlockState state) {
            super(AgronomyBlockEntities.STATION.get(), pos, state);
        }

        /** Which station this is, asked of the block rather than remembered. */
        public String station() {
            return getBlockState().getBlock() instanceof StationBlock block
                    ? block.station() : KitchenRules.POT;
        }

        /** The full name of an item, which is what the data files use. */
        public static String nameOf(ItemStack stack) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }

        /** The item of that full name, or air. */
        public static Item itemOf(String name) {
            return BuiltInRegistries.ITEM.get(ResourceLocation.parse(name));
        }

        /** What is in it, by name. */
        public List<String> have() {
            List<String> names = new ArrayList<>();
            for (ItemStack stack : contents) {
                names.add(nameOf(stack));
            }
            return names;
        }

        /** Whether there is a fire under this block. */
        public static boolean heated(Level level, BlockPos pos) {
            return KitchenRules.isHeat(BuiltInRegistries.BLOCK.getKey(
                    level.getBlockState(pos.below()).getBlock()).toString());
        }

        /** Something used on it that is not a knife. */
        @Nullable
        public Component offer(ItemStack held, Level level, BlockPos pos) {
            if (!made.isEmpty()) {
                return Component.translatable("elysiumagronomy.station.take_first");
            }
            if (cooking) {
                return says(level, pos);
            }
            if (contents.size() >= KitchenRules.mostIngredients()) {
                return Component.translatable("elysiumagronomy.station.full",
                        String.valueOf(KitchenRules.mostIngredients()));
            }
            contents.add(held.copyWithCount(1));
            held.shrink(1);
            progress = 0;
            settle();
            return says(level, pos);
        }

        /** A knife used on it. Only a board cuts. */
        @Nullable
        public Component slice(ItemStack knife, Player player, InteractionHand hand) {
            if (!KitchenRules.BOARD.equals(station())) {
                return Component.translatable("elysiumagronomy.station.not_a_board");
            }
            if (cooking) {
                return Component.translatable("elysiumagronomy.station.busy");
            }
            for (int at = 0; at < contents.size(); at++) {
                KitchenRules.Cut cut = AgronomyData.cutFor(nameOf(contents.get(at)),
                        toolOf(knife));
                if (cut == null) {
                    continue;
                }
                ItemStack whole = contents.remove(at);
                for (var yield : cut.yields().entrySet()) {
                    made.add(new ItemStack(itemOf(yield.getKey()), yield.getValue()));
                }
                knife.hurtAndBreak(KitchenRules.KNIFE_WEAR, player,
                        LivingEntity.getSlotForHand(hand));
                settle();
                return Component.translatable("elysiumagronomy.station.cut",
                        whole.getHoverName());
            }
            return Component.translatable("elysiumagronomy.station.nothing_to_cut");
        }

        /** What a cutting recipe calls the knife in hand. */
        public static String toolOf(ItemStack knife) {
            return knife.getItem() instanceof KnifeItem one ? one.tool() : KitchenRules.KNIFE;
        }

        /** An empty hand: take what it made, or start it. */
        @Nullable
        public Component serve(Player player, Level level, BlockPos pos) {
            if (!made.isEmpty()) {
                for (ItemStack stack : made) {
                    player.getInventory().placeItemBackInInventory(stack);
                }
                made.clear();
                settle();
                return null;
            }
            if (cooking) {
                return says(level, pos);
            }
            KitchenRules.Order order = AgronomyData.orderFor(station(), have());
            if (order == null) {
                return says(level, pos);
            }
            if (KitchenRules.needsHeat(station()) && !heated(level, pos)) {
                return Component.translatable("elysiumagronomy.station.cold");
            }
            cooking = true;
            making = order.id();
            progress = 0;
            settle();
            return says(level, pos);
        }

        /** A sneaking empty hand: the ingredients back, and nothing made. */
        @Nullable
        public Component giveBack(Player player) {
            if (contents.isEmpty()) {
                return Component.translatable("elysiumagronomy.station.bare");
            }
            for (ItemStack stack : contents) {
                player.getInventory().placeItemBackInInventory(stack);
            }
            contents.clear();
            cooking = false;
            making = "";
            progress = 0;
            settle();
            return null;
        }

        /** One tick of making whatever it was told to make. */
        public void cook(Level level, BlockPos pos) {
            if (!cooking) {
                return;
            }
            KitchenRules.Order order = AgronomyData.orderFor(station(), have());
            if (order == null || !order.id().equals(making)) {
                // The data changed under it, or something is wrong with what
                // it holds: stop rather than make something nobody asked for.
                cooking = false;
                making = "";
                progress = 0;
                settle();
                return;
            }
            if (KitchenRules.needsHeat(station()) && !heated(level, pos)) {
                return;                      // the fire went out; wait for it
            }
            progress++;
            if (progress < order.ticks()) {
                return;
            }
            made.add(new ItemStack(itemOf(order.id()), order.count()));
            contents.clear();
            cooking = false;
            making = "";
            progress = 0;
            settle();
        }

        /** What it would say if it could talk. */
        public Component says(Level level, BlockPos pos) {
            String station = station();
            if (!made.isEmpty()) {
                return Component.translatable("elysiumagronomy.station.made",
                        made.get(0).getHoverName());
            }
            if (contents.isEmpty()) {
                return Component.translatable("elysiumagronomy.station.bare");
            }
            KitchenRules.Order order = AgronomyData.orderFor(station, have());
            if (cooking && order != null) {
                int left = Math.max(0, order.ticks() - progress) / 20;
                if (KitchenRules.needsHeat(station) && !heated(level, pos)) {
                    return Component.translatable("elysiumagronomy.station.cold");
                }
                return Component.translatable("elysiumagronomy.station.cooking",
                        itemOf(order.id()).getDescription(), String.valueOf(left));
            }
            if (order != null) {
                return Component.translatable("elysiumagronomy.station.ready",
                        itemOf(order.id()).getDescription(),
                        String.valueOf(order.ticks() / 20));
            }
            if (AgronomyData.couldBecome(station, have())) {
                return Component.translatable("elysiumagronomy.station.more",
                        String.valueOf(contents.size()));
            }
            return Component.translatable("elysiumagronomy.station.nothing",
                    String.valueOf(contents.size()));
        }

        /** Everything in it, for dropping when it is broken. */
        public SimpleContainer spill() {
            SimpleContainer all = new SimpleContainer(contents.size() + made.size());
            int slot = 0;
            for (ItemStack stack : contents) {
                all.setItem(slot++, stack);
            }
            for (ItemStack stack : made) {
                all.setItem(slot++, stack);
            }
            return all;
        }

        private void settle() {
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        private static ListTag saved(List<ItemStack> stacks, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (ItemStack stack : stacks) {
                if (!stack.isEmpty()) {
                    list.add(stack.save(registries));
                }
            }
            return list;
        }

        private static void loaded(ListTag list, List<ItemStack> into,
                                   HolderLookup.Provider registries) {
            into.clear();
            for (Tag one : list) {
                ItemStack.parse(registries, one).ifPresent(into::add);
            }
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            loaded(tag.getList("contents", Tag.TAG_COMPOUND), contents, registries);
            loaded(tag.getList("made", Tag.TAG_COMPOUND), made, registries);
            cooking = tag.getBoolean("cooking");
            making = tag.getString("making");
            progress = Math.max(0, tag.getInt("progress"));
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("contents", saved(contents, registries));
            tag.put("made", saved(made, registries));
            tag.putBoolean("cooking", cooking);
            tag.putString("making", making);
            tag.putInt("progress", progress);
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
