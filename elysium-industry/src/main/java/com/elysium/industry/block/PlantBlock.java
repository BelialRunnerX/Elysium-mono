package com.elysium.industry.block;

import com.elysium.lib.item.ElysiumSlots;
import com.elysium.industry.recipe.PlantRecipe;
import com.elysium.industry.rules.IndustryRules;
import com.elysium.kinetics.block.KineticLine;
import com.elysium.kinetics.rules.KineticRules;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A machine of the second age: fed by a wire, or by a shaft if you must.
 *
 * <h2>Two ways to drive it, and the spine's rule</h2>
 *
 * The grid is the proper way, and gives full speed when the grid can afford
 * it. A shaft is the other way, and gives {@link IndustryRules#ON_A_SHAFT} of
 * full speed above {@link IndustryRules#SHAFT_LEAST_RPM} — a penalty and not a
 * refusal, so a player whose generator has broken is slowed down rather than
 * stopped, and one who has not built a generator yet can see what the machine
 * does before committing to the age.
 *
 * <p>Whichever is better wins. A machine on both takes the grid when the grid
 * is healthy and falls back to the shaft when it is not, which is exactly what
 * a player would expect from wiring a backup to something.
 *
 * <h2>Why it is also a KineticNode</h2>
 *
 * Because that is the only honest way to be driven by a shaft: the machine has
 * to appear on Age I's line as a load, drawing torque, slowing it down like
 * anything else. A machine that took rotation without costing any would be a
 * free machine, and the shaft route would be the better one.
 */
public abstract class PlantBlock extends GridBlock {

    protected PlantBlock(Properties properties) {
        super(properties);
    }

    /** What this machine draws from a grid. */
    public abstract IndustryRules.Machine machine();

    /** What it draws from a shaft, when it is driven by one. */
    public abstract KineticRules.Load load();

    /**
     * Which recipes it reads.
     *
     * A recipe type rather than a name out of an enum, so that an age above
     * can put its own catalogue behind the same machine rather than copying
     * the machine.
     */
    public abstract RecipeType<? extends PlantRecipe> recipes();

    /**
     * What a shaft is worth to this machine, as a fraction of full speed.
     *
     * Age II's answer, and each age above overrides it with a worse one: the
     * further up the spine a machine is, the less a rope and a water wheel
     * should be able to do for it.
     */
    public float shaftShare(float rpm) {
        return IndustryRules.shaftShare(rpm);
    }

    /**
     * The share at which this machine lets go of a shaft altogether.
     *
     * The best a shaft could ever do for it. Above that the grid is already
     * doing at least as well, and continuing to draw torque would be dragging
     * a line for nothing.
     */
    public float bestOnAShaft() {
        return IndustryRules.ON_A_SHAFT;
    }

    /** The prefix of its messages. */
    public abstract String saying();

    /**
     * How many of what the recipe says this machine actually gets out.
     *
     * One for everything but the separator, whose doubling is the material
     * ladder and therefore belongs to {@link IndustryRules#SEPARATOR_MULTIPLIER}
     * rather than to a data file that states "two ingots" and cannot be
     * checked against anything.
     */
    public int multiplier() {
        return 1;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (held.isEmpty() || !(level.getBlockEntity(pos) instanceof PlantBlockEntity plant)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && plant.offer(held)) {
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PlantBlockEntity plant)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack out = plant.takeAll();
            if (!out.isEmpty()) {
                player.getInventory().placeItemBackInInventory(out);
            } else {
                player.displayClientMessage(plant.says(), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof PlantBlockEntity plant) {
            Containers.dropContents(level, pos, plant.spill());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    // ------------------------------------------------------------------

    /** The working half: on a grid, on a line, or on neither. */
    public static class PlantBlockEntity extends GridBlockEntity implements SteelNode {

        public static final int IN = 0;
        public static final int OUT = 1;

        private final ItemStackHandler items = new ItemStackHandler(2) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (slot == IN) {
                    done = 0;
                }
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot == IN;
            }
        };

        private int done;

        /** What Age I's line last told this machine it was turning at. */
        private float rpm;
        private boolean lineRunning;
        private boolean lineUnsettled = true;

        public PlantBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        public ItemStackHandler items() {
            return items;
        }

        private PlantBlock block() {
            return getBlockState().getBlock() instanceof PlantBlock plant ? plant : null;
        }

        @Override
        public IndustryRules.Voltage voltage() {
            PlantBlock plant = block();
            return plant == null ? IndustryRules.Voltage.LOW : plant.machine().rated();
        }

        @Nullable
        @Override
        public IndustryRules.Machine machine() {
            PlantBlock plant = block();
            return plant == null ? null : plant.machine();
        }

        // --- the Age I side ------------------------------------------

        @Override
        public Direction.Axis axis() {
            return Direction.Axis.Y;
        }

        /**
         * What it takes out of an Age I line — and nothing, when the grid is
         * already doing at least as well as a shaft could.
         *
         * <p>Without that condition a machine wired to a healthy grid would go
         * on dragging any shaft it happened to touch, for no gain; with the
         * condition reversed — dropping the load whenever the grid supplies
         * <em>anything</em> — it would take the shaft's speed without paying
         * the shaft's torque, which is worse. The line is drawn exactly where
         * the shaft stops being worth having.
         *
         * <p>It cannot oscillate: what the grid gives depends on
         * {@link #machine()}, which is a constant, and never on this.
         */
        @Nullable
        @Override
        public KineticRules.Load load() {
            PlantBlock plant = block();
            if (plant == null || share() >= plant.bestOnAShaft()) {
                return null;
            }
            return plant.load();
        }

        @Override
        protected String hum() {
            return "whine";
        }

        @Override
        protected boolean humming() {
            return lineRunning && !items.getStackInSlot(IN).isEmpty() && speed() > 0.0F;
        }

        @Override
        public void driven(float speed, boolean running) {
            rpm = speed;
            lineRunning = running;
            lineUnsettled = false;
        }

        @Override
        public void unsettle() {
            super.unsettle();
            lineUnsettled = true;
        }

        /**
         * How fast this machine is running, as a fraction of full.
         *
         * Whichever way of driving it is better, because a player who wires a
         * backup to a machine expects the backup to be a backup.
         */
        public float speed() {
            PlantBlock plant = block();
            float onGrid = share();
            float onShaft = lineRunning && plant != null ? plant.shaftShare(rpm) : 0.0F;
            return Math.max(onGrid, onShaft);
        }

        /** What it would say if it could talk. */
        public Component says() {
            PlantBlock plant = block();
            String prefix = plant == null ? "elysiumindustry.plant" : plant.saying();
            if (burned()) {
                return Component.translatable("elysiumindustry.grid.burned",
                        String.format("%.0f", Math.min(supply(), demand())),
                        String.valueOf(voltage().capacity()));
            }
            float speed = speed();
            if (speed <= 0.0F) {
                return Component.translatable(prefix + ".dark");
            }
            if (items.getStackInSlot(IN).isEmpty()) {
                return Component.translatable(prefix + ".empty",
                        String.format("%.0f%%", speed * 100.0F));
            }
            return Component.translatable(prefix + ".working",
                    String.format("%.0f%%", speed * 100.0F));
        }

        public boolean offer(ItemStack held) {
            if (!items.insertItem(IN, held.copyWithCount(1), false).isEmpty()) {
                return false;
            }
            held.shrink(1);
            return true;
        }

        public ItemStack takeAll() {
            return items.extractItem(OUT, items.getStackInSlot(OUT).getCount(), false);
        }

        public SimpleContainer spill() {
            return new SimpleContainer(items.getStackInSlot(IN), items.getStackInSlot(OUT));
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            if (lineUnsettled) {
                // It sits on Age I's line as well, and that line has to be
                // worked out by Age I's own code: this module knows what a
                // load is, and nothing about how a line is found.
                KineticLine.solve(level, pos);
            }
            PlantBlock plant = block();
            ItemStack input = items.getStackInSlot(IN);
            float speed = speed();
            if (plant == null || input.isEmpty() || speed <= 0.0F) {
                done = 0;
                return;
            }
            SingleRecipeInput asked = new SingleRecipeInput(input);
            Optional<? extends RecipeHolder<? extends PlantRecipe>> found =
                    lookUp(plant.recipes(), asked, level);
            if (found.isEmpty()) {
                done = 0;
                return;
            }
            PlantRecipe recipe = found.get().value();
            ItemStack result = recipe.assemble(asked, level.registryAccess());
            result.setCount(result.getCount() * plant.multiplier());
            if (!ElysiumSlots.roomFor(items, OUT, result)) {
                return;                            // no room; stop rather than lose it
            }
            // Progress is made at the speed the machine is running at, so a
            // half-fed grid is half as quick rather than stopped.
            done += IndustryRules.workPerTick(speed);
            if (done < recipe.work()) {
                return;
            }
            done = 0;
            items.extractItem(IN, 1, false);
            // Set rather than inserted: isItemValid guards what a hopper may
            // push in, and the machine is allowed to fill its own output.
            items.setStackInSlot(OUT, grow(items.getStackInSlot(OUT), result));
            setChanged();
        }

        /**
         * The recipe manager, asked about a type this class only knows the
         * bound of.
         *
         * A method of its own because the wildcard has to be captured by a
         * type variable before the manager will take it; written out rather
         * than cast, so a recipe type that does not match its recipes is a
         * compile error and not a class cast at midnight.
         */
        private static <T extends PlantRecipe> Optional<RecipeHolder<T>> lookUp(
                RecipeType<T> type, SingleRecipeInput asked, Level level) {
            return level.getRecipeManager().getRecipeFor(type, asked, level);
        }

        private static ItemStack grow(ItemStack there, ItemStack added) {
            if (there.isEmpty()) {
                return added;
            }
            ItemStack more = there.copy();
            more.grow(added.getCount());
            return more;
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            items.deserializeNBT(registries, tag.getCompound("items"));
            done = tag.getInt("done");
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("items", items.serializeNBT(registries));
            tag.putInt("done", done);
        }
    }
}
