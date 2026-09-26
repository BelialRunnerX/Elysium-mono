package com.elysium.agronomy.block;

import com.elysium.agronomy.registry.AgronomyBlockEntities;
import com.elysium.agronomy.event.Keeping;
import com.elysium.agronomy.registry.AgronomyComponents;
import com.elysium.agronomy.registry.AgronomyItems;
import com.elysium.agronomy.registry.BeeItems;
import com.elysium.agronomy.rules.KeepingRules;
import com.elysium.agronomy.rules.ApiaryRules;
import com.elysium.agronomy.rules.AgronomyRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A hive: farming without a field or a grid.
 *
 * <h2>Crowding, not capacity</h2>
 *
 * Bees want flowers. Too few and the hive gets <em>slower</em>, in proportion,
 * rather than switching off — because a player who adds one bee too many
 * should see the rate drop, notice, and plant something. A hive that stopped
 * dead would be a hive they concluded was broken.
 *
 * <p>It counts the flowers around it rather than being told, so the answer to
 * "why is my hive slow" is a thing you can walk outside and look at.
 */
public class HiveBlock extends BaseEntityBlock {

    /** The rung a plain hive is: the one you make before you make anything. */
    public static final String HOUSING = "hive";

    public static final MapCodec<HiveBlock> CODEC =
            simpleCodec(properties -> new HiveBlock(properties, HOUSING));

    private final String housing;

    public HiveBlock(Properties properties) {
        this(properties, HOUSING);
    }

    /**
     * @param housing which rung of {@link ApiaryRules#HOUSINGS} this block is.
     *                One class for all four of them, and the block says which:
     *                four classes differing in two numbers is four places for
     *                the two numbers to drift apart.
     */
    public HiveBlock(Properties properties, String housing) {
        super(properties);
        this.housing = housing;
    }

    /** Which rung this is. */
    public String housing() {
        return housing;
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
        return new HiveBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != AgronomyBlockEntities.HIVE.get()) {
            return null;
        }
        return (world, pos, blockState, entity) ->
                ((HiveBlockEntity) entity).work(world, pos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (held.isEmpty() || !(level.getBlockEntity(pos) instanceof HiveBlockEntity hive)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (held.is(BeeItems.JAR.get())) {
            if (!level.isClientSide) {
                player.displayClientMessage(hive.offerJar(held), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.is(BeeItems.TREAT.get())) {
            if (!level.isClientSide) {
                player.displayClientMessage(hive.treat(held, player), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && hive.offer(held)) {
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof HiveBlockEntity hive)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack out = hive.takeAll();
            if (!out.isEmpty()) {
                player.getInventory().placeItemBackInInventory(out);
            } else {
                player.displayClientMessage(hive.says(level, pos), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof HiveBlockEntity hive) {
            Containers.dropContents(level, pos, hive.spill());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** The working half: bees in one slot, combs in the other. */
    public static class HiveBlockEntity extends BlockEntity {

        public static final int BEES = 0;
        public static final int OUT = 1;

        /** How far out it looks for flowers. The bees do not go further. */
        public static final int LOOKS = 5;

        /** How often it counts them. Gardens do not change by the tick. */
        public static final int COUNTS_EVERY = 100;

        /**
         * Bees, combs, then one slot per upgrade the housing takes.
         *
         * <p>Sized for the biggest rung there is rather than for this one, so
         * that a hive broken and replaced by an apiary does not have to move
         * its contents between two differently shaped inventories. What each
         * rung actually accepts is {@link #slots()}, and the slots past it
         * refuse everything.
         */
        private final ItemStackHandler items = new ItemStackHandler(FIRST_UPGRADE
                + mostSlots()) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                if (slot == BEES) {
                    return stack.is(AgronomyItems.BEE_TAG);
                }
                if (slot < FIRST_UPGRADE) {
                    return false;
                }
                return slot - FIRST_UPGRADE < slots()
                        && ApiaryRules.upgrade(idOf(stack)) != null
                        && !alreadyFitted(idOf(stack), slot);
            }
        };

        private int flowers;
        private int since = COUNTS_EVERY;
        private int worked;

        /** The second species a jar brought in, waiting for a treat; empty for none. */
        private String mate = "";

        public HiveBlockEntity(BlockPos pos, BlockState state) {
            super(AgronomyBlockEntities.HIVE.get(), pos, state);
        }

        public ItemStackHandler items() {
            return items;
        }

        public int flowers() {
            return flowers;
        }

        /** Where the upgrade slots start. */
        public static final int FIRST_UPGRADE = 2;

        /** How many slots the deepest rung of the ladder takes. */
        public static int mostSlots() {
            int most = 0;
            for (ApiaryRules.Housing one : ApiaryRules.HOUSINGS) {
                most = Math.max(most, one.slots());
            }
            return most;
        }

        /** Which rung this block entity is sitting in. */
        public ApiaryRules.Housing housing() {
            ApiaryRules.Housing found = null;
            if (getBlockState().getBlock() instanceof HiveBlock hive) {
                found = ApiaryRules.housing(hive.housing());
            }
            return found != null ? found : ApiaryRules.HOUSINGS.get(0);
        }

        /** How many upgrades this rung takes. */
        public int slots() {
            return housing().slots();
        }

        /** What is fitted, in the order the slots hold it. */
        public java.util.List<ApiaryRules.Upgrade> fitted() {
            java.util.List<ApiaryRules.Upgrade> found = new java.util.ArrayList<>();
            for (int slot = 0; slot < slots(); slot++) {
                ApiaryRules.Upgrade one =
                        ApiaryRules.upgrade(idOf(items.getStackInSlot(
                                FIRST_UPGRADE + slot)));
                if (one != null && !found.contains(one)) {
                    found.add(one);
                }
            }
            return found;
        }

        private boolean alreadyFitted(String id, int besides) {
            for (int slot = 0; slot < slots(); slot++) {
                if (FIRST_UPGRADE + slot == besides) {
                    continue;
                }
                if (id.equals(idOf(items.getStackInSlot(FIRST_UPGRADE + slot)))) {
                    return true;
                }
            }
            return false;
        }

        private static String idOf(ItemStack stack) {
            if (stack.isEmpty()) {
                return "";
            }
            net.minecraft.resources.ResourceLocation where =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                            stack.getItem());
            return where.getNamespace().equals(
                    com.elysium.agronomy.ElysiumAgronomy.MODID)
                    ? where.getPath() : "";
        }

        /** Whether a chore has been bought back here. */
        public boolean bought(String chore) {
            for (ApiaryRules.Upgrade one : fitted()) {
                if (one.attention().equals(chore)) {
                    return true;
                }
            }
            return false;
        }

        public int bees() {
            return Math.min(ApiaryRules.beesIn(housing(), fitted()),
                    items.getStackInSlot(BEES).getCount());
        }

        public boolean offer(ItemStack held) {
            if (!items.isItemValid(BEES, held)) {
                return false;
            }
            ItemStack left = items.insertItem(BEES, held.copy(), false);
            if (left.getCount() == held.getCount()) {
                return false;
            }
            held.setCount(left.getCount());
            return true;
        }

        // --------------------------------------------------------------
        // Species, jars and treats
        // --------------------------------------------------------------

        /** Which bee lives here, or null for the plain bee or none. */
        public String species() {
            return AgronomyComponents.speciesIn(items.getStackInSlot(BEES));
        }

        /** The second species waiting for a treat, or empty. */
        public String mate() {
            return mate;
        }

        /** One bee of a species, as the hive holds it. */
        public static ItemStack beeOf(String species) {
            ItemStack bee = new ItemStack(AgronomyItems.BEE.get());
            bee.set(AgronomyComponents.SPECIES.get(), species);
            return bee;
        }

        /**
         * A jar used on the hive.
         *
         * <p>Full: its bee moves in if the hive is empty or holds that
         * species; if the hive holds another species that the rules breed
         * with it ({@link KeepingRules#childOf}), it waits as the mate.
         * Empty: it takes one of the hive's own bees out.
         */
        public Component offerJar(ItemStack jar) {
            String brought = AgronomyComponents.speciesIn(jar);
            String here = species();
            if (brought == null) {
                if (here == null || items.getStackInSlot(BEES).isEmpty()) {
                    return Component.translatable("elysiumagronomy.hive.no_species");
                }
                items.extractItem(BEES, 1, false);
                jar.set(AgronomyComponents.SPECIES.get(), here);
                return Component.translatable("elysiumagronomy.hive.jarred",
                        Keeping.named(here));
            }
            ItemStack bee = beeOf(brought);
            if (items.insertItem(BEES, bee, true).isEmpty()) {
                items.insertItem(BEES, bee, false);
                jar.remove(AgronomyComponents.SPECIES.get());
                return Component.translatable("elysiumagronomy.hive.moved_in",
                        Keeping.named(brought));
            }
            String child = KeepingRules.childOf(here, brought);
            if (child != null && mate.isEmpty()) {
                mate = brought;
                jar.remove(AgronomyComponents.SPECIES.get());
                setChanged();
                return Component.translatable("elysiumagronomy.hive.paired",
                        Keeping.named(here), Keeping.named(brought));
            }
            return Component.translatable("elysiumagronomy.hive.refused",
                    Keeping.named(brought));
        }

        /**
         * A treat used on the hive: the child of the pair it holds if the
         * rules breed one, otherwise one more of its own if it has two.
         */
        public Component treat(ItemStack treat, Player player) {
            String here = species();
            String child = KeepingRules.childOf(here, mate);
            if (child != null) {
                treat.shrink(1);
                mate = "";
                setChanged();
                player.getInventory().placeItemBackInInventory(beeOf(child));
                return Component.translatable("elysiumagronomy.hive.bred",
                        Keeping.named(child));
            }
            if (here != null && items.getStackInSlot(BEES).getCount() >= 2) {
                treat.shrink(1);
                ItemStack more = beeOf(here);
                if (items.insertItem(BEES, more, true).isEmpty()) {
                    items.insertItem(BEES, more, false);
                } else {
                    player.getInventory().placeItemBackInInventory(more);
                }
                return Component.translatable("elysiumagronomy.hive.bred",
                        Keeping.named(here));
            }
            return Component.translatable("elysiumagronomy.hive.no_pair");
        }

        /** The comb this hive makes: its species' own, or the plain comb. */
        public ItemStack combOf() {
            String here = species();
            if (here != null && BeeItems.COMBS.containsKey(here)) {
                return new ItemStack(BeeItems.COMBS.get(here).get());
            }
            return new ItemStack(AgronomyItems.COMB.get());
        }

        public ItemStack takeAll() {
            return items.extractItem(OUT, items.getStackInSlot(OUT).getCount(), false);
        }

        public SimpleContainer spill() {
            ItemStack[] held = new ItemStack[items.getSlots() + 1];
            for (int slot = 0; slot < items.getSlots(); slot++) {
                held[slot] = items.getStackInSlot(slot);
            }
            held[items.getSlots()] = mate.isEmpty() ? ItemStack.EMPTY : beeOf(mate);
            return new SimpleContainer(held);
        }

        /**
         * How many flowers this hive counts as having.
         *
         * <p>The flowering upgrade does not conjure flowers; it makes the
         * hive stop needing them, which is the same arithmetic and a very
         * different sentence. Enough for every bee in it, whatever is
         * actually planted outside.
         */
        public int flowering() {
            return bought("planting flowers")
                    ? Math.max(flowers, bees() * AgronomyRules.FLOWERS_PER_BEE)
                    : flowers;
        }

        /** Combs an hour, with the housing, the upgrades and the garden. */
        public float rate() {
            float crowded = AgronomyRules.combsPerHour(bees(), flowering());
            float bare = AgronomyRules.combsPerHour(bees(),
                    bees() * AgronomyRules.FLOWERS_PER_BEE);
            float share = bare <= 0.0F ? 0.0F : crowded / bare;
            return ApiaryRules.perHour(1, housing(), fitted()) * share
                    * ratio();
        }

        /**
         * How much of a full hive this one is.
         *
         * <p>{@link ApiaryRules#perHour} is a full house; a hive with three
         * bees in it is three bees' worth of that, and without this a single
         * bee in an industrial apiary would earn what twenty-four do.
         */
        private float ratio() {
            int full = ApiaryRules.beesIn(housing(), fitted());
            return full <= 0 ? 0.0F : (float) bees() / full;
        }

        public Component says(Level level, BlockPos pos) {
            if (bees() <= 0) {
                return Component.translatable("elysiumagronomy.hive.empty");
            }
            float rate = AgronomyRules.combsPerHour(bees(), flowering());
            float roomy = AgronomyRules.combsPerHour(bees(),
                    bees() * AgronomyRules.FLOWERS_PER_BEE);
            if (rate < roomy) {
                return Component.translatable("elysiumagronomy.hive.crowded",
                        String.valueOf(bees()), String.valueOf(flowers),
                        String.valueOf(bees() * AgronomyRules.FLOWERS_PER_BEE),
                        String.format("%.0f", rate));
            }
            return Component.translatable("elysiumagronomy.hive.working",
                    String.valueOf(bees()), String.format("%.0f", rate));
        }

        /** One tick of bees being bees. */
        public void work(Level level, BlockPos pos) {
            if (++since >= COUNTS_EVERY) {
                since = 0;
                flowers = countFlowers(level, pos);
            }
            float perHour = rate();
            if (perHour <= 0.0F) {
                return;
            }
            // Counted in the same work units the pot uses, so a rate that
            // changes when somebody mines a flower neither invents nor throws
            // away a comb halfway made.
            worked += Math.max(1, Math.round(perHour * AgronomyRules.WORK_FULL));
            int whole = AgronomyRules.TICKS_PER_HOUR * AgronomyRules.WORK_FULL;
            if (worked < whole) {
                return;
            }
            worked -= whole;
            ItemStack comb = combOf();
            if (items.insertItem(OUT, comb, true).isEmpty()) {
                items.setStackInSlot(OUT, grow(items.getStackInSlot(OUT), comb));
                setChanged();
            }
        }

        private static ItemStack grow(ItemStack there, ItemStack added) {
            if (there.isEmpty()) {
                return added;
            }
            ItemStack more = there.copy();
            more.grow(added.getCount());
            return more;
        }

        private static int countFlowers(Level level, BlockPos pos) {
            int found = 0;
            for (BlockPos around : BlockPos.betweenClosed(
                    pos.offset(-LOOKS, -2, -LOOKS), pos.offset(LOOKS, 2, LOOKS))) {
                if (level.getBlockState(around).is(net.minecraft.tags.BlockTags.FLOWERS)) {
                    found++;
                }
            }
            return found;
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            items.deserializeNBT(registries, tag.getCompound("items"));
            worked = Math.max(0, tag.getInt("worked"));
            flowers = Math.max(0, tag.getInt("flowers"));
            mate = tag.getString("mate");
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("items", items.serializeNBT(registries));
            tag.putInt("worked", worked);
            tag.putInt("flowers", flowers);
            tag.putString("mate", mate);
        }
    }
}
