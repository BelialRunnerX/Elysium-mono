package com.elysium.space.machine;

import com.elysium.space.item.SpaceSuitItem;
import com.elysium.space.oxygen.OxygenField;
import com.elysium.space.registry.SpaceFluids;
import com.elysium.space.registry.SpaceItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * One block class for every space machine. Right-click with a bucket to pour
 * in or take out; with fuel on a generator to load it; with a glass bottle to
 * bottle fuel (refinery) or oxygen (separator, distributor); with an oxygen
 * canister to top up a distributor; with an empty hand to open its screen (sneak for a one-line status).
 * Machine screens come with the S8 reskin and Elysium's GUI kit.
 */
public class MachineBlock extends BaseEntityBlock {

    private final Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type;
    private final BiFunction<BlockPos, BlockState, ? extends MachineBlockEntity> factory;

    public MachineBlock(Properties properties, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type,
                        BiFunction<BlockPos, BlockState, ? extends MachineBlockEntity> factory) {
        super(properties);
        this.type = type;
        this.factory = factory;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new MachineBlock(p, type, factory));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.apply(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != type.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((MachineBlockEntity) be).serverTick();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (FluidUtil.getFluidHandler(held).isPresent()) {
            boolean moved = FluidUtil.interactWithFluidHandler(player, hand, machine.fluids());
            if (moved) {
                machine.setChanged();
            }
            return moved ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.FAIL;
        }
        if (machine instanceof CoalGeneratorBlockEntity gen && held.getBurnTime(RecipeType.SMELTING) > 0) {
            if (!level.isClientSide) {
                ItemStack left = gen.items().insertItem(0, held.copy(), false);
                held.setCount(left.getCount());
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.is(Items.GLASS_BOTTLE) && player.isSecondaryUseActive()
                && machine instanceof WaterSeparatorBlockEntity separator) {
            FluidTank h2 = separator.hydrogenTank();
            int need = com.elysium.space.entity.RocketEntity.CANISTER_MB;
            if (h2.getFluidAmount() < need) {
                return ItemInteractionResult.FAIL;
            }
            if (!level.isClientSide) {
                h2.drain(need, IFluidHandler.FluidAction.EXECUTE);
                held.consume(1, player);
                ItemStack out = new ItemStack(SpaceItems.HYDROGEN_CANISTER.get());
                if (!player.addItem(out)) {
                    player.drop(out, false);
                }
                machine.setChanged();
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.is(Items.GLASS_BOTTLE)) {
            FluidTank from = machine instanceof FuelRefineryBlockEntity r ? r.fuelTank()
                    : machine instanceof WaterSeparatorBlockEntity s ? s.oxygenTank()
                    : machine instanceof com.elysium.space.block.OxygenDistributorBlockEntity d ? d.oxygenTank() : null;
            boolean fuel = machine instanceof FuelRefineryBlockEntity;
            int need = fuel ? com.elysium.space.entity.RocketEntity.CANISTER_MB : com.elysium.space.world.SuitAir.CANISTER;
            if (from == null || from.getFluidAmount() < need) {
                return ItemInteractionResult.FAIL;
            }
            if (!level.isClientSide) {
                from.drain(need, IFluidHandler.FluidAction.EXECUTE);
                held.consume(1, player);
                ItemStack out = new ItemStack(fuel ? SpaceItems.FUEL_CANISTER.get() : SpaceItems.OXYGEN_CANISTER.get());
                if (!player.addItem(out)) {
                    player.drop(out, false);
                }
                machine.setChanged();
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.is(SpaceItems.OXYGEN_CANISTER.get())
                && machine instanceof com.elysium.space.block.OxygenDistributorBlockEntity d) {
            FluidStack air = new FluidStack(SpaceFluids.OXYGEN.source.get(), com.elysium.space.world.SuitAir.CANISTER);
            if (d.oxygenTank().fill(air, IFluidHandler.FluidAction.SIMULATE) < air.getAmount()) {
                return ItemInteractionResult.FAIL;
            }
            if (!level.isClientSide) {
                d.oxygenTank().fill(air, IFluidHandler.FluidAction.EXECUTE);
                held.consume(1, player);
                d.setChanged();
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            if (!level.isClientSide) {
                if (player.isShiftKeyDown() || !(player instanceof net.minecraft.server.level.ServerPlayer server)) {
                    player.displayClientMessage(machine.status(), true);        // a glance, without the screen
                } else {
                    server.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (id, inventory, p) -> new MachineMenu(id, inventory, machine), state.getBlock().getName()),
                            buf -> {
                                buf.writeBlockPos(pos);
                                buf.writeVarInt(machine.tankList().size());
                                buf.writeBoolean(machine.slots() != null);
                            });
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof CoalGeneratorBlockEntity gen) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                        gen.items().getStackInSlot(0));
            }
            if (!level.isClientSide) {
                OxygenField.clear(level, pos);
            }
        }
        super.onRemove(state, level, pos, next, moved);
    }
}
