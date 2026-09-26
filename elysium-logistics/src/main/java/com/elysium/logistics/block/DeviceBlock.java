package com.elysium.logistics.block;

import com.elysium.logistics.registry.LogisticBlockEntities;
import com.elysium.logistics.rules.NetRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A device: one of the seven faces a network reaches the world through.
 *
 * <h2>One block, seven roles</h2>
 *
 * The blocks are registered from {@link NetRules#DEVICES} and this class is
 * parameterised by the device, so an eighth face is a line in the rules and
 * nothing here changes. What differs between them is one {@code switch} on
 * the role, in the block entity, and every one of them shares the same three
 * facts: it finds its controller along the bus, it costs that controller a
 * stated draw, and it does nothing at all without one.
 *
 * <h2>The face it points at</h2>
 *
 * A device is placed against the thing it works on. The clicked face is the
 * chest, the machine or the furnace, and {@link #FACING} points at it; bus
 * joins on any other side. That is the hopper's rule and it needs no
 * explanation, which is why it is the rule.
 *
 * <h2>No screen</h2>
 *
 * Right-click with an item to tell a device what to care about; empty-handed
 * for a sentence saying what it is doing; crouch for the one setting that is
 * a number. The terminal has already taught a player all three gestures.
 */
public class DeviceBlock extends BaseEntityBlock {

    public static final MapCodec<DeviceBlock> CODEC =
            simpleCodec(properties -> new DeviceBlock(properties, NetRules.DEVICES.get(0)));

    /** The block it works on. Any of six, so a device can hang under a machine. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /** Whether a detector is saying yes. Meaningless on the other six. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private final NetRules.Device device;

    public DeviceBlock(Properties properties, NetRules.Device device) {
        super(properties);
        this.device = device;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }

    public NetRules.Device device() {
        return device;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeviceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != LogisticBlockEntities.DEVICE.get()) {
            return null;
        }
        return (world, pos, blockState, entity) ->
                ((DeviceBlockEntity) entity).serverTick(world, pos, blockState);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        LogisticNetwork.disturb(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            if (level.getBlockEntity(pos) instanceof DeviceBlockEntity gone) {
                gone.leaveChannel();
            }
            LogisticNetwork.disturb(level, pos);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (held.isEmpty() || !(level.getBlockEntity(pos) instanceof DeviceBlockEntity face)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            player.displayClientMessage(face.shown(held), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DeviceBlockEntity face)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(face.crouched(), true);
            } else {
                player.displayClientMessage(face.emptyHanded(), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return NetRules.SIGNAL.equals(device.role());
    }

    @Override
    protected int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level,
                            BlockPos pos, Direction direction) {
        return isSignalSource(state) && state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    // ------------------------------------------------------------------

    /**
     * The working half.
     *
     * <p>Implements {@link NetRules.Readings} itself, because the readings an
     * expression can take — how many of a thing, how much power, how many
     * devices, whether anything is being made — are exactly the questions a
     * device asks its controller, and there is no reason for them to exist
     * twice.
     */
    public static class DeviceBlockEntity extends BlockEntity implements NetRules.Readings {

        /** What this device cares about: what to move, count or show. */
        private ItemStack filter = ItemStack.EMPTY;

        /** Where on the ladder a detector is. */
        private int threshold = NetRules.firstThreshold();

        /** Which channel an ender link is on. Empty for the other six. */
        private String channel = "";

        @Nullable
        private BlockPos controllerAt;

        private int since;

        public DeviceBlockEntity(BlockPos pos, BlockState state) {
            super(LogisticBlockEntities.DEVICE.get(), pos, state);
        }

        public NetRules.Device device() {
            return getBlockState().getBlock() instanceof DeviceBlock block
                    ? block.device() : NetRules.DEVICES.get(0);
        }

        public String role() {
            return device().role();
        }

        public ItemStack filter() {
            return filter;
        }

        public int threshold() {
            return threshold;
        }

        public String channel() {
            return channel;
        }

        private Direction facing() {
            return getBlockState().hasProperty(FACING)
                    ? getBlockState().getValue(FACING) : Direction.NORTH;
        }

        /** The controller on this device's bus, or null. */
        @Nullable
        public ControllerBlock.ControllerBlockEntity controller() {
            if (level == null || controllerAt == null || !level.isLoaded(controllerAt)) {
                return null;
            }
            return level.getBlockEntity(controllerAt)
                    instanceof ControllerBlock.ControllerBlockEntity found ? found : null;
        }

        /** Look for the controller along the bus again. */
        private void findController(Level level, BlockPos pos) {
            List<BlockPos> found = LogisticNetwork.along(level, pos,
                    ControllerBlock.ControllerBlockEntity.class);
            controllerAt = found.isEmpty() ? null : found.get(0);
        }

        // --------------------------------------------------------------

        void serverTick(Level level, BlockPos pos, BlockState state) {
            if (++since < NetRules.TICK_EVERY) {
                return;
            }
            since = 0;
            findController(level, pos);
            ControllerBlock.ControllerBlockEntity controller = controller();
            if (controller == null) {
                power(level, pos, state, false);
                return;
            }
            if (controller.share() <= 0.0F) {
                // A dark grid: the network is a load nobody is feeding, and a
                // device on it is as still as the controller is.
                return;
            }
            switch (role()) {
                case NetRules.IMPORT -> importRound(controller);
                case NetRules.EXPORT -> exportRound(controller);
                case NetRules.SIGNAL -> power(level, pos, state, detects());
                default -> {
                }
            }
        }

        private void power(Level level, BlockPos pos, BlockState state, boolean now) {
            if (state.hasProperty(POWERED) && state.getValue(POWERED) != now) {
                level.setBlock(pos, state.setValue(POWERED, now), Block.UPDATE_ALL);
            }
        }

        /**
         * The inventory this device points at, or null.
         *
         * <p>Never a controller and never another device. A storage bus
         * pointed at its own controller would make the network contain
         * itself, and one pointed at an interface would reach the network
         * through a face that reaches the network — both loops, both
         * refused here rather than detected later.
         */
        @Nullable
        private IItemHandler facingHandler() {
            if (level == null) {
                return null;
            }
            BlockPos target = getBlockPos().relative(facing());
            if (!level.isLoaded(target)) {
                return null;
            }
            BlockEntity there = level.getBlockEntity(target);
            if (there instanceof ControllerBlock.ControllerBlockEntity
                    || there instanceof DeviceBlockEntity) {
                return null;
            }
            return level.getCapability(Capabilities.ItemHandler.BLOCK, target,
                    facing().getOpposite());
        }

        private boolean passes(ItemStack stack) {
            return filter.isEmpty() || ItemStack.isSameItemSameComponents(filter, stack);
        }

        /** One stack, from the block it faces into the network. */
        private void importRound(ControllerBlock.ControllerBlockEntity controller) {
            IItemHandler from = facingHandler();
            if (from == null) {
                return;
            }
            IItemHandler into = controller.asOneInventory();
            for (int slot = 0; slot < from.getSlots(); slot++) {
                ItemStack offered = from.extractItem(slot, NetRules.MOVES_PER_ROUND, true);
                if (offered.isEmpty() || !passes(offered)) {
                    continue;
                }
                ItemStack left = ItemHandlerHelper.insertItemStacked(into, offered, true);
                int moved = offered.getCount() - left.getCount();
                if (moved <= 0) {
                    continue;
                }
                move(from, slot, into, moved);
                return;
            }
        }

        /** One stack of the filtered thing, from the network into the block it faces. */
        private void exportRound(ControllerBlock.ControllerBlockEntity controller) {
            IItemHandler into = facingHandler();
            if (into == null || filter.isEmpty()) {
                return;
            }
            IItemHandler from = controller.asOneInventory();
            for (int slot = 0; slot < from.getSlots(); slot++) {
                ItemStack offered = from.extractItem(slot, NetRules.MOVES_PER_ROUND, true);
                if (offered.isEmpty() || !passes(offered)) {
                    continue;
                }
                ItemStack left = ItemHandlerHelper.insertItemStacked(into, offered, true);
                int moved = offered.getCount() - left.getCount();
                if (moved <= 0) {
                    continue;
                }
                move(from, slot, into, moved);
                return;
            }
        }

        /**
         * Take exactly this many out of one and put them in the other.
         *
         * <p>Simulated first, on both sides, by the callers; what cannot be
         * placed after all goes straight back where it came from, and what
         * cannot go back either — a slot that changed under us — is dropped
         * into the world rather than lost. Nothing is created and nothing is
         * destroyed on this path, which is the whole discipline of this module.
         */
        private void move(IItemHandler from, int slot, IItemHandler into, int count) {
            ItemStack taken = from.extractItem(slot, count, false);
            if (taken.isEmpty()) {
                return;
            }
            ItemStack left = ItemHandlerHelper.insertItemStacked(into, taken, false);
            if (!left.isEmpty()) {
                left = from.insertItem(slot, left, false);
            }
            if (!left.isEmpty() && level != null) {
                net.minecraft.world.Containers.dropItemStack(level, getBlockPos().getX(),
                        getBlockPos().getY(), getBlockPos().getZ(), left);
            }
        }

        /** What a detector's redstone says: the expression, run. */
        private boolean detects() {
            if (filter.isEmpty()) {
                return false;
            }
            NetRules.Value said = NetRules.evaluate(
                    NetRules.detectorExpression(idOf(filter), threshold), this);
            return said != null && said.truth();
        }

        // --------------------------------------------------------------
        // What the network sees of this device

        /**
         * An inventory this device brings onto the network, or null.
         *
         * <p>A storage bus brings the chest it faces; an ender link brings the
         * network at the other end. The other five bring nothing — they use
         * the network rather than adding to it.
         */
        @Nullable
        public IItemHandler store() {
            return switch (role()) {
                case NetRules.EXPOSE -> facingHandler();
                case NetRules.LINK -> linked();
                default -> null;
            };
        }

        /**
         * What a hopper against this device reaches, or null.
         *
         * <p>An interface is its own network; an ender link is the other
         * network. Everything else has no inventory to offer.
         */
        @Nullable
        public IItemHandler exposed() {
            switch (role()) {
                case NetRules.SERVE -> {
                    ControllerBlock.ControllerBlockEntity controller = controller();
                    return controller == null ? null : controller.asOneInventory();
                }
                case NetRules.LINK -> {
                    return linked();
                }
                default -> {
                    return null;
                }
            }
        }

        /**
         * How deep the current call is inside an ender link.
         *
         * <p>The guard against a network containing itself: network A's
         * inventory includes the link, which is network B's inventory, which
         * includes the link back, which is network A's inventory. Inside a
         * link every further link is nothing, so the walk is at most one
         * link deep and comes back.
         */
        private static int linking;

        /** The network at the other end of this channel, or null. */
        @Nullable
        private IItemHandler linked() {
            if (linking > 0) {
                return null;
            }
            if (!(level instanceof ServerLevel server) || channel.isEmpty()) {
                return null;
            }
            MinecraftServer host = server.getServer();
            EnderChannels.End self = new EnderChannels.End(server.dimension().location(),
                    getBlockPos());
            for (EnderChannels.End other : EnderChannels.of(host).others(channel, self)) {
                ServerLevel there = host.getLevel(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, other.dimension()));
                if (there == null || !there.isLoaded(other.pos())) {
                    continue;
                }
                if (!(there.getBlockEntity(other.pos()) instanceof DeviceBlockEntity end)
                        || !NetRules.LINK.equals(end.role())) {
                    continue;
                }
                ControllerBlock.ControllerBlockEntity far = end.controller();
                if (far != null) {
                    return new Through(far.asOneInventory());
                }
            }
            return null;
        }

        /** The other network's inventory, with the depth guard held while inside it. */
        private static final class Through implements IItemHandler {

            private final IItemHandler far;

            private Through(IItemHandler far) {
                this.far = far;
            }

            @Override
            public int getSlots() {
                linking++;
                try {
                    return far.getSlots();
                } finally {
                    linking--;
                }
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                linking++;
                try {
                    return far.getStackInSlot(slot);
                } finally {
                    linking--;
                }
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                linking++;
                try {
                    return far.insertItem(slot, stack, simulate);
                } finally {
                    linking--;
                }
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                linking++;
                try {
                    return far.extractItem(slot, amount, simulate);
                } finally {
                    linking--;
                }
            }

            @Override
            public int getSlotLimit(int slot) {
                linking++;
                try {
                    return far.getSlotLimit(slot);
                } finally {
                    linking--;
                }
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                linking++;
                try {
                    return far.isItemValid(slot, stack);
                } finally {
                    linking--;
                }
            }
        }

        // --------------------------------------------------------------
        // Readings: what an expression can read off this device's network

        @Override
        public long countOf(String item) {
            ControllerBlock.ControllerBlockEntity controller = controller();
            if (controller == null) {
                return 0;
            }
            IItemHandler all = controller.asOneInventory();
            long count = 0;
            for (int slot = 0; slot < all.getSlots(); slot++) {
                ItemStack in = all.getStackInSlot(slot);
                if (!in.isEmpty() && idOf(in).equals(item)) {
                    count += in.getCount();
                }
            }
            return count;
        }

        @Override
        public long power() {
            ControllerBlock.ControllerBlockEntity controller = controller();
            return controller == null ? 0 : Math.round(controller.demand() * controller.share());
        }

        @Override
        public long devices() {
            ControllerBlock.ControllerBlockEntity controller = controller();
            return controller == null ? 0 : controller.network().devices();
        }

        @Override
        public boolean busy() {
            AssemblyCoreBlock.AssemblyCoreBlockEntity core = core();
            return core != null && core.busy();
        }

        @Override
        public String nameOf(String item) {
            ResourceLocation id = ResourceLocation.tryParse(item);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                return item;
            }
            return new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString();
        }

        @Nullable
        private AssemblyCoreBlock.AssemblyCoreBlockEntity core() {
            if (level == null) {
                return null;
            }
            for (BlockPos where : LogisticNetwork.along(level, getBlockPos(),
                    AssemblyCoreBlock.AssemblyCoreBlockEntity.class)) {
                if (level.getBlockEntity(where)
                        instanceof AssemblyCoreBlock.AssemblyCoreBlockEntity core) {
                    return core;
                }
            }
            return null;
        }

        static String idOf(ItemStack stack) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }

        // --------------------------------------------------------------
        // The three gestures

        /** Right-clicked with something in hand. */
        public Component shown(ItemStack held) {
            if (NetRules.LINK.equals(role())) {
                joinChannel(idOf(held));
                return Component.translatable("elysiumlogistics.device.channel",
                        held.getHoverName());
            }
            if (NetRules.EXPOSE.equals(role()) || NetRules.SERVE.equals(role())) {
                return says();
            }
            filter = held.copyWithCount(1);
            setChanged();
            return Component.translatable("elysiumlogistics.device.filter",
                    filter.getHoverName());
        }

        /**
         * Right-clicked with nothing in hand: an importer or exporter forgets
         * what it was shown, and says so; everything else says what it is doing.
         */
        public Component emptyHanded() {
            if ((NetRules.IMPORT.equals(role()) || NetRules.EXPORT.equals(role()))
                    && !filter.isEmpty()) {
                filter = ItemStack.EMPTY;
                setChanged();
                return Component.translatable("elysiumlogistics.device.cleared");
            }
            return says();
        }

        /** Crouch-right-clicked, empty handed. */
        public Component crouched() {
            if (NetRules.SIGNAL.equals(role())) {
                threshold = NetRules.nextThreshold(threshold);
                setChanged();
                return Component.translatable("elysiumlogistics.detector.stepped",
                        String.format("%,d", threshold));
            }
            if (!filter.isEmpty() && !NetRules.LINK.equals(role())) {
                filter = ItemStack.EMPTY;
                setChanged();
                return Component.translatable("elysiumlogistics.device.cleared");
            }
            return says();
        }

        /** What it would say if it could talk. */
        public Component says() {
            ControllerBlock.ControllerBlockEntity controller = controller();
            if (controller == null) {
                return Component.translatable("elysiumlogistics.device.loose");
            }
            switch (role()) {
                case NetRules.IMPORT, NetRules.EXPORT -> {
                    if (filter.isEmpty() && NetRules.EXPORT.equals(role())) {
                        return Component.translatable("elysiumlogistics.exporter.idle");
                    }
                    return filter.isEmpty()
                            ? Component.translatable("elysiumlogistics.device.unfiltered")
                            : Component.translatable("elysiumlogistics.device.filtered",
                                    filter.getHoverName());
                }
                case NetRules.EXPOSE -> {
                    IItemHandler brought = facingHandler();
                    return brought == null
                            ? Component.translatable("elysiumlogistics.storage_bus.nothing")
                            : Component.translatable("elysiumlogistics.storage_bus.exposing",
                                    String.valueOf(brought.getSlots()));
                }
                case NetRules.WATCH -> {
                    return watched(controller);
                }
                case NetRules.SIGNAL -> {
                    if (filter.isEmpty()) {
                        return Component.translatable("elysiumlogistics.detector.unset",
                                String.format("%,d", threshold));
                    }
                    return Component.translatable(detects()
                                    ? "elysiumlogistics.detector.on"
                                    : "elysiumlogistics.detector.off",
                            String.format("%,d", countOf(idOf(filter))),
                            filter.getHoverName(), String.format("%,d", threshold));
                }
                case NetRules.SERVE -> {
                    return Component.translatable("elysiumlogistics.interface.serving",
                            String.valueOf(controller.asOneInventory().getSlots()));
                }
                case NetRules.LINK -> {
                    if (channel.isEmpty()) {
                        return Component.translatable("elysiumlogistics.ender_link.unnamed");
                    }
                    IItemHandler far = linked();
                    return far == null
                            ? Component.translatable("elysiumlogistics.ender_link.alone",
                                    nameOf(channel))
                            : Component.translatable("elysiumlogistics.ender_link.joined",
                                    nameOf(channel), String.valueOf(far.getSlots()));
                }
                default -> {
                    return Component.translatable("elysiumlogistics.device.loose");
                }
            }
        }

        /**
         * A monitor's sentence: what is being made, and — if it was shown a
         * thing — how many of that thing the network holds, said by the
         * logic layer rather than by this method.
         */
        private Component watched(ControllerBlock.ControllerBlockEntity controller) {
            AssemblyCoreBlock.AssemblyCoreBlockEntity core = core();
            Component making = core == null
                    ? Component.translatable("elysiumlogistics.monitor.coreless")
                    : core.says();
            if (filter.isEmpty()) {
                return making;
            }
            NetRules.Value line = NetRules.evaluate(
                    NetRules.monitorExpression(idOf(filter)), this);
            return Component.translatable("elysiumlogistics.monitor.holding",
                    line == null ? "" : line.text(), making);
        }

        // --------------------------------------------------------------
        // Channels

        private void joinChannel(String now) {
            leaveChannel();
            channel = now;
            if (level instanceof ServerLevel server) {
                EnderChannels.of(server.getServer()).join(channel, new EnderChannels.End(
                        server.dimension().location(), getBlockPos()));
            }
            setChanged();
        }

        void leaveChannel() {
            if (channel.isEmpty() || !(level instanceof ServerLevel server)) {
                return;
            }
            EnderChannels.of(server.getServer()).leave(channel, new EnderChannels.End(
                    server.dimension().location(), getBlockPos()));
        }

        @Override
        public void onLoad() {
            super.onLoad();
            // Written down again on every load, so a channel a save lost
            // the registry for finds its ends the moment they are in memory.
            if (!channel.isEmpty() && level instanceof ServerLevel server) {
                EnderChannels.of(server.getServer()).join(channel, new EnderChannels.End(
                        server.dimension().location(), getBlockPos()));
            }
        }

        // --------------------------------------------------------------

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            filter = tag.contains("filter")
                    ? ItemStack.parse(registries, tag.getCompound("filter")).orElse(ItemStack.EMPTY)
                    : ItemStack.EMPTY;
            threshold = tag.contains("threshold") ? tag.getInt("threshold")
                    : NetRules.firstThreshold();
            if (!NetRules.THRESHOLDS.contains(threshold)) {
                threshold = NetRules.firstThreshold();
            }
            channel = tag.getString("channel");
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (!filter.isEmpty()) {
                tag.put("filter", filter.save(registries));
            }
            tag.putInt("threshold", threshold);
            tag.putString("channel", channel);
        }
    }
}
