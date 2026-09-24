package com.elysium.atomics.block;

import com.elysium.atomics.registry.AtomicBlockEntities;
import com.elysium.atomics.registry.AtomicItems;
import com.elysium.atomics.rules.AtomicRules;
import com.elysium.industry.block.GridBlock;
import com.elysium.industry.block.GridBlockEntity;
import com.elysium.industry.rules.IndustryRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A reactor core. The only block in the pack that can take the roof off.
 *
 * <h2>The whole of the danger is one number</h2>
 *
 * Heat in is what the throttle makes; heat out is a fraction of how far above
 * the room the core already is; and the fraction is how much plumbing touches
 * it. That means a core <em>settles</em>: a given throttle with a given number
 * of channels reaches a temperature and stays there. It does not creep. It
 * cannot run away while a player is in the Nether.
 *
 * <p>So a meltdown is always something a player did — they turned it up, or
 * they mined a coolant channel — and always something they could have worked
 * out first, because prodding the core says where the current setting will
 * land and what the next notch would cost. The arithmetic is available before
 * the decision, which is the difference between a machine that is dangerous
 * and a machine that is unfair.
 *
 * <h2>Four notches, not a slider</h2>
 *
 * Four numbers a player can learn. A continuous control would be a number
 * nobody reads, and this whole age is built on the claim that a player who
 * does the arithmetic is safe.
 *
 * <h2>Wear</h2>
 *
 * Between {@code SAFE} and {@code MELTDOWN} a core is not fine and is not
 * dead: it is wearing out, faster the hotter it is, squared rather than
 * straight so that overshooting slightly gives time to notice and ignoring it
 * entirely does not. A worn-out core stops and waits for steel plate. That is
 * the failure a careless player should meet, with meltdown reserved for the
 * one who kept going.
 */
public class ReactorBlock extends GridBlock {

    public static final MapCodec<ReactorBlock> CODEC = simpleCodec(ReactorBlock::new);

    /** Whether the core is making power, which is the one thing you can see. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ReactorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, AtomicBlockEntities.REACTOR.get());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (held.isEmpty() || !(level.getBlockEntity(pos) instanceof ReactorBlockEntity core)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && core.offer(held, player)) {
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * An empty hand turns it up one notch, and past full turns it off.
     *
     * A dial you can only turn one way and then round, because the important
     * thing is that every step is deliberate and reversible without a tool.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ReactorBlockEntity core)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            core.turn(player.isShiftKeyDown() ? -1 : 1);
            player.displayClientMessage(core.says(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof ReactorBlockEntity core) {
            Containers.dropContents(level, pos, core.spill());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** The working half: a dial, a temperature, and how worn out it is. */
    public static class ReactorBlockEntity extends GridBlockEntity {

        public static final int FUEL = 0;

        private final ItemStackHandler items = new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.is(AtomicItems.ENRICHED_URANIUM.get());
            }
        };

        private int notch;
        private int fuelLeft;
        private float temperature = AtomicRules.AMBIENT;
        private float wear;

        public ReactorBlockEntity(BlockPos pos, BlockState state) {
            super(AtomicBlockEntities.REACTOR.get(), pos, state);
        }

        public ItemStackHandler items() {
            return items;
        }

        public float temperature() {
            return temperature;
        }

        public float wear() {
            return wear;
        }

        public int notch() {
            return notch;
        }

        /** Whether it has worn itself out and is waiting for somebody. */
        public boolean spent() {
            return wear >= AtomicRules.SPENT;
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return AtomicRules.reactorAt(1.0F).voltage();
        }

        /** The throttle it is actually running at: the dial, unless it cannot. */
        public float throttle() {
            return spent() || fuelLeft <= 0 ? 0.0F : AtomicRules.throttleOf(notch);
        }

        @Nullable
        @Override
        public IndustryRules.Generator generator() {
            float running = throttle();
            return running <= 0.0F ? null : AtomicRules.reactorAt(running);
        }

        /** How many coolant channels touch it, counted fresh because they can be mined. */
        public int channels() {
            if (level == null) {
                return 0;
            }
            int found = 0;
            for (Direction face : Direction.values()) {
                if (level.getBlockState(getBlockPos().relative(face))
                        .getBlock() instanceof CoolantChannelBlock) {
                    found++;
                }
            }
            return Math.min(AtomicRules.MOST_CHANNELS, found);
        }

        /** Turn the dial, and wrap round rather than stopping at either end. */
        public void turn(int by) {
            int steps = AtomicRules.THROTTLE_STEPS + 1;
            notch = ((notch + by) % steps + steps) % steps;
            unsettle();
            setChanged();
        }

        /** Fuel goes in; steel plate puts it right. */
        public boolean offer(ItemStack held, Player player) {
            if (held.is(AtomicItems.ENRICHED_URANIUM.get())) {
                if (!items.insertItem(FUEL, held.copyWithCount(1), false).isEmpty()) {
                    return false;
                }
                held.shrink(1);
                return true;
            }
            if (held.is(AtomicItems.STEEL_PLATE_TAG) && wear > 0.0F) {
                wear = Math.max(0.0F, wear - AtomicRules.REPAIR_PER_PLATE);
                held.shrink(1);
                setChanged();
                player.displayClientMessage(says(), true);
                return true;
            }
            return false;
        }

        public SimpleContainer spill() {
            return new SimpleContainer(items.getStackInSlot(FUEL));
        }

        /**
         * What it would say if it could talk.
         *
         * The temperature it is at <em>and</em> the temperature this setting
         * will reach, because the second one is the number the decision turns
         * on and it is not the one a thermometer shows.
         */
        public Component says() {
            int channels = channels();
            float running = AtomicRules.throttleOf(notch);
            float willBe = AtomicRules.settlesAt(AtomicRules.REACTOR, running, channels);
            String dial = notch + "/" + AtomicRules.THROTTLE_STEPS;
            String now = String.format("%.0f", temperature);
            String settling = Float.isInfinite(willBe) ? "∞" : String.format("%.0f", willBe);
            if (spent()) {
                return Component.translatable("elysiumatomics.reactor.spent");
            }
            if (items.getStackInSlot(FUEL).isEmpty() && fuelLeft <= 0) {
                return Component.translatable("elysiumatomics.reactor.unfuelled", dial);
            }
            if (temperature >= AtomicRules.CRITICAL) {
                return Component.translatable("elysiumatomics.reactor.critical",
                        now, settling, String.format("%.0f%%", wear * 100.0F));
            }
            if (temperature >= AtomicRules.SAFE) {
                return Component.translatable("elysiumatomics.reactor.hot",
                        now, settling, String.format("%.0f%%", wear * 100.0F));
            }
            return Component.translatable("elysiumatomics.reactor.running",
                    dial, String.format("%.0f", AtomicRules.REACTOR.outputAt(throttle())),
                    now, settling, String.valueOf(channels));
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            boolean wasLit = throttle() > 0.0F;

            // Fuel first, so that a core which has just run dry cools this tick
            // rather than making one last tick of heat out of nothing.
            if (fuelLeft <= 0 && notch > 0 && !spent()
                    && !items.getStackInSlot(FUEL).isEmpty()) {
                items.extractItem(FUEL, 1, false);
                fuelLeft = AtomicRules.FUEL_BUDGET;
                unsettle();
            }
            float running = throttle();
            if (running > 0.0F) {
                fuelLeft = Math.max(0, fuelLeft - notch);
                if (fuelLeft == 0) {
                    unsettle();
                }
            }

            temperature = AtomicRules.nextTemperature(temperature,
                    AtomicRules.heatOf(AtomicRules.REACTOR, running),
                    AtomicRules.coolingWith(channels()));
            float worn = AtomicRules.wearAt(temperature);
            if (worn > 0.0F) {
                wear = Math.min(AtomicRules.SPENT, wear + worn);
                if (spent()) {
                    unsettle();
                }
            }
            if (AtomicRules.meltingDown(temperature)) {
                meltDown(level, pos);
                return;
            }
            if (wasLit != (throttle() > 0.0F) && state.hasProperty(LIT)) {
                level.setBlock(pos, state.setValue(LIT, throttle() > 0.0F),
                        Block.UPDATE_ALL);
            }
            setChanged();
        }

        /**
         * The end of it.
         *
         * A method of its own so that "it is removed" and "it goes off" are
         * one thing rather than two lines either of which could be quietly
         * disabled without the other looking wrong.
         */
        private void meltDown(Level level, BlockPos pos) {
            level.removeBlock(pos, false);
            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    AtomicRules.MELTDOWN_RADIUS, true, ExplosionInteraction.BLOCK);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            items.deserializeNBT(registries, tag.getCompound("items"));
            notch = Math.max(0, Math.min(AtomicRules.THROTTLE_STEPS, tag.getInt("notch")));
            fuelLeft = Math.max(0, tag.getInt("fuel"));
            temperature = Math.max(AtomicRules.AMBIENT, tag.getFloat("heat"));
            wear = Math.max(0.0F, Math.min(AtomicRules.SPENT, tag.getFloat("wear")));
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("items", items.serializeNBT(registries));
            tag.putInt("notch", notch);
            tag.putInt("fuel", fuelLeft);
            tag.putFloat("heat", temperature);
            tag.putFloat("wear", wear);
        }
    }
}
