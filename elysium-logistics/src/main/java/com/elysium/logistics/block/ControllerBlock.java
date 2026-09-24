package com.elysium.logistics.block;

import com.elysium.industry.block.GridBlock;
import com.elysium.industry.block.GridBlockEntity;
import com.elysium.industry.rules.IndustryRules;
import com.elysium.logistics.registry.LogisticBlockEntities;
import com.elysium.logistics.rules.LogisticRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A controller: every drawer on its bus, behaving as one inventory.
 *
 * <h2>What it is for, before there is any screen</h2>
 *
 * A hopper into a controller reaches every drawer on the network and finds the
 * right one; a hopper out of it pulls from all of them. That is the whole of
 * what a storage network is <em>for</em> in a factory, and none of it needs a
 * terminal, a search box or a screen. A player who has built a wall of drawers
 * and a smelter can now point one at the other.
 *
 * <h2>It is a machine on the grid like any other</h2>
 *
 * It draws {@link LogisticRules#DEVICE_DRAW} per drawer it indexes, it runs at
 * whatever share Age II's grid can afford, and it slows rather than stopping —
 * which is the same decision this pack has now made four times. What it draws
 * is a constant given the drawers on it and never a function of what the grid
 * can give, because that would be a loop with no fixed point.
 *
 * <p>The rate matters: a starved network moves a trickle of items a tick
 * rather than none, so a brown-out makes a factory sluggish rather than
 * broken, and the fix a player reaches for is the fix that is true.
 */
public class ControllerBlock extends GridBlock {

    public static final MapCodec<ControllerBlock> CODEC = simpleCodec(ControllerBlock::new);

    public ControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GridBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, LogisticBlockEntities.CONTROLLER.get());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof ControllerBlockEntity controller) {
            player.displayClientMessage(controller.says(), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ------------------------------------------------------------------

    /** The working half: a walk it remembers, and a budget it spends. */
    public static class ControllerBlockEntity extends GridBlockEntity {

        /**
         * How often it looks for drawers it was not told about.
         *
         * A block placed three hundred blocks down a bus cannot tell this
         * controller it exists, and a network that only noticed changes next
         * to itself would be one a player had to break and replace to fix.
         * Two seconds is far below the threshold of anybody noticing and far
         * above the cost of caring.
         */
        public static final int LOOKS_AGAIN_EVERY = 40;

        private LogisticNetwork.Found found =
                new LogisticNetwork.Found(List.of(), false);
        private int since = LOOKS_AGAIN_EVERY;
        private boolean unsettledNetwork = true;

        /** How many items it has already moved this tick, against its budget. */
        private float spent;

        public ControllerBlockEntity(BlockPos pos, BlockState state) {
            super(LogisticBlockEntities.CONTROLLER.get(), pos, state);
        }

        /** Ask for the network to be walked again, because something changed. */
        public void unsettleNetwork() {
            unsettledNetwork = true;
        }

        public LogisticNetwork.Found found() {
            return found;
        }

        public LogisticRules.Network network() {
            return found.network();
        }

        @Override
        public IndustryRules.Voltage voltage() {
            return network().machine().rated();
        }

        /**
         * What it asks the grid for.
         *
         * A constant, given the drawers it has found — never a function of
         * what the grid can afford. A network that asked for less when the
         * grid was short would change what the grid could afford, which would
         * change what the network asked for.
         */
        @Nullable
        @Override
        public IndustryRules.Machine machine() {
            LogisticRules.Network network = network();
            return network.devices() <= 0 ? null : network.machine();
        }

        /** How many items it may still move this tick. */
        public float budget() {
            return Math.max(0.0F, network().movesAt(share()) - spent);
        }

        /** What it would say if it could talk. */
        public Component says() {
            LogisticRules.Network network = network();
            if (network.overloaded()) {
                return Component.translatable("elysiumlogistics.controller.overloaded",
                        String.valueOf(network.devices()),
                        String.valueOf(LogisticRules.MOST_DEVICES));
            }
            if (network.devices() == 0) {
                return Component.translatable("elysiumlogistics.controller.alone");
            }
            if (burned()) {
                return Component.translatable("elysiumindustry.grid.burned",
                        String.format("%.0f", Math.min(supply(), demand())),
                        String.valueOf(voltage().capacity()));
            }
            if (share() <= 0.0F) {
                return Component.translatable("elysiumlogistics.controller.dark",
                        String.valueOf(network.devices()));
            }
            return Component.translatable("elysiumlogistics.controller.running",
                    String.valueOf(network.devices()),
                    String.format("%.0f", network.machine().draw()),
                    String.format("%.1f", network.movesAt(share())));
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            spent = 0.0F;
            if (++since >= LOOKS_AGAIN_EVERY || unsettledNetwork) {
                since = 0;
                unsettledNetwork = false;
                int was = found.drawers().size();
                found = LogisticNetwork.walk(level, pos);
                if (found.drawers().size() != was) {
                    // What the network costs just changed, so the grid has to
                    // be worked out again.
                    unsettle();
                    setChanged();
                }
            }
        }

        /**
         * Every drawer on the network, as one inventory.
         *
         * <p>Rate limited: each item that crosses this spends a unit of the
         * tick's budget, and when the budget is gone the handler is simply
         * full or empty until the next tick. A hopper sees a network that is
         * slow, which is what a starved grid should look like — not one that
         * has stopped, and not one that ignores the grid entirely.
         */
        public IItemHandler asOneInventory() {
            return new Across(this);
        }

        void spend(int items) {
            spent += items;
        }
    }

    /**
     * The whole network seen as slots, in the order the walk found them.
     *
     * <p>Not a copy and not a cache: it asks the drawers every call, because a
     * cached view of somebody else's inventory is how a storage mod ends up
     * duplicating items when two hoppers pull at once.
     */
    private static final class Across implements IItemHandler {

        private final ControllerBlockEntity controller;

        private Across(ControllerBlockEntity controller) {
            this.controller = controller;
        }

        private List<DrawerStore> stores() {
            Level level = controller.getLevel();
            List<DrawerStore> found = new java.util.ArrayList<>();
            if (level == null) {
                return found;
            }
            for (BlockPos pos : controller.found().drawers()) {
                DrawerBlock.DrawerBlockEntity drawer = LogisticNetwork.drawerAt(level, pos);
                if (drawer != null) {
                    found.add(drawer.store());
                }
            }
            return found;
        }

        private DrawerStore storeOf(int slot, int[] within) {
            for (DrawerStore store : stores()) {
                if (slot < store.getSlots()) {
                    within[0] = slot;
                    return store;
                }
                slot -= store.getSlots();
            }
            return null;
        }

        @Override
        public int getSlots() {
            int slots = 0;
            for (DrawerStore store : stores()) {
                slots += store.getSlots();
            }
            return slots;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            int[] within = new int[1];
            DrawerStore store = storeOf(slot, within);
            return store == null ? ItemStack.EMPTY : store.getStackInSlot(within[0]);
        }

        @Override
        public int getSlotLimit(int slot) {
            int[] within = new int[1];
            DrawerStore store = storeOf(slot, within);
            return store == null ? 0 : store.getSlotLimit(within[0]);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            int[] within = new int[1];
            DrawerStore store = storeOf(slot, within);
            return store != null && store.isItemValid(within[0], stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int[] within = new int[1];
            DrawerStore store = storeOf(slot, within);
            if (store == null || stack.isEmpty()) {
                return stack;
            }
            int allowed = (int) Math.floor(controller.budget());
            if (allowed <= 0) {
                return stack;
            }
            ItemStack offered = stack.copyWithCount(Math.min(stack.getCount(), allowed));
            ItemStack left = store.insertItem(within[0], offered, simulate);
            int moved = offered.getCount() - left.getCount();
            if (!simulate) {
                controller.spend(moved);
            }
            return stack.copyWithCount(stack.getCount() - moved);
        }

        @Override
        public ItemStack extractItem(int slot, int wanted, boolean simulate) {
            int[] within = new int[1];
            DrawerStore store = storeOf(slot, within);
            if (store == null) {
                return ItemStack.EMPTY;
            }
            int allowed = (int) Math.floor(controller.budget());
            if (allowed <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack out = store.extractItem(within[0], Math.min(wanted, allowed),
                    simulate);
            if (!simulate) {
                controller.spend(out.getCount());
            }
            return out;
        }
    }
}
