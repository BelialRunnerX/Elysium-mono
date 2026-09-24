package com.elysium.space.block;

import com.elysium.lib.room.ElysiumRoomScan;
import com.elysium.space.machine.MachineBlockEntity;
import com.elysium.space.machine.MachineEnergy;
import com.elysium.space.oxygen.OxygenField;
import com.elysium.space.registry.SpaceBlockEntities;
import com.elysium.space.registry.SpaceFluids;
import com.elysium.space.world.MachineRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every {@link #INTERVAL} ticks: flood the air from each face with
 * {@link ElysiumRoomScan#interior} (the same fill that decides whether a volume
 * is an Elysium room) and fill it, paying Stellaris's way. Each position newly
 * filled costs air and power, and keeping the room full leaks a little
 * ({@link MachineRules#distributorOxygen}). When it cannot pay, the room empties.
 *
 * <p>The room rule differs from Stellaris on purpose. There, only true air
 * counted and a cube capped the fill. Here a torch, sign or grass does not
 * seal a room but a door or glass does, and the cap is {@link #MAX_VOLUME}
 * positions (16 cubed).
 */
public class OxygenDistributorBlockEntity extends MachineBlockEntity {

    public static final int INTERVAL = 40;
    public static final int MAX_VOLUME = 4096;

    private final FluidTank oxygen;
    private Set<BlockPos> filled = Set.of();
    private int countdown;
    private String state = "elysiumspace.machine.idle";

    public OxygenDistributorBlockEntity(BlockPos pos, BlockState blockState) {
        this(pos, blockState, tank(MachineRules.DISTRIBUTOR_OXYGEN_TANK, SpaceFluids.OXYGEN.source));
    }

    private OxygenDistributorBlockEntity(BlockPos pos, BlockState blockState, FluidTank oxygen) {
        super(SpaceBlockEntities.OXYGEN_DISTRIBUTOR.get(), pos, blockState,
                new MachineEnergy(MachineRules.DISTRIBUTOR_BUFFER, 1000, 0), List.of(oxygen), List.of());
        this.oxygen = oxygen;
    }

    public FluidTank oxygenTank() {
        return oxygen;
    }

    @Override
    public void serverTick() {
        if (level == null || --countdown > 0) {
            return;
        }
        countdown = INTERVAL;
        Set<BlockPos> room = new HashSet<>();
        for (Direction side : Direction.values()) {
            BlockPos start = worldPosition.relative(side);
            if (room.contains(start)) {
                continue;
            }
            room.addAll(ElysiumRoomScan.interior(level, start, MAX_VOLUME));
            if (room.size() > MAX_VOLUME) {
                room.clear();
                break;
            }
        }
        if (room.isEmpty()) {
            empty("elysiumspace.distributor.open");
            return;
        }
        int fresh = 0;
        for (BlockPos p : room) {
            if (!filled.contains(p)) {
                fresh++;
            }
        }
        int air = MachineRules.distributorOxygen(fresh, room.size() - fresh);
        int power = MachineRules.distributorEnergy(fresh);
        if (oxygen.getFluidAmount() < air) {
            empty("elysiumspace.distributor.no_air");
            return;
        }
        if (energy.getEnergyStored() < power) {
            empty("elysiumspace.distributor.no_power");
            return;
        }
        oxygen.drain(air, IFluidHandler.FluidAction.EXECUTE);
        energy.consume(power);
        filled = Set.copyOf(room);
        OxygenField.set(level, worldPosition, filled);
        state = "elysiumspace.machine.running";
        setChanged();
    }

    private void empty(String why) {
        filled = Set.of();
        OxygenField.clear(level, worldPosition);
        state = why;
    }

    @Override
    public Component status() {
        return Component.translatable("elysiumspace.machine.distributor", filled.size(), mb(oxygen),
                energy.getEnergyStored(), energy.getMaxEnergyStored(), Component.translatable(state));
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            OxygenField.clear(level, worldPosition);
        }
        super.setRemoved();
    }
}
