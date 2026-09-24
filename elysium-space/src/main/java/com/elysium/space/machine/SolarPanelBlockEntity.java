package com.elysium.space.machine;

import com.elysium.space.registry.SpaceBlockEntities;
import com.elysium.space.world.MachineRules;
import com.elysium.space.world.SpaceWorld;
import com.elysium.space.world.SpaceWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Sunlight to FE; twice as much on a world with no air to filter it. */
public class SolarPanelBlockEntity extends MachineBlockEntity {

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(SpaceBlockEntities.SOLAR_PANEL.get(), pos, state,
                new MachineEnergy(MachineRules.GENERATOR_BUFFER, 0, MachineRules.PUSH_PER_TICK), List.of(), List.of());
    }

    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }
        SpaceWorld world = SpaceWorlds.of(level);
        boolean airless = world != null && world.air() == SpaceWorld.Air.NONE;
        boolean sun = level.isDay() && level.canSeeSky(worldPosition.above()) && (airless || !level.isRaining());
        // And what the sky is doing beyond day and night: an ash cloud halves
        // what a panel makes of a sun that is still up there. Never zero — a
        // panel under a storm is a bad panel, not a broken one — so a base
        // built on Cinderveil browns out rather than going dark.
        int made = Math.round(MachineRules.solarPerTick(sun, airless)
                * com.elysium.lib.weather.ElysiumWeather.machineShare(level));
        if (energy.produce(made) > 0) {
            setChanged();
        }
        pushEnergy(MachineRules.PUSH_PER_TICK);
    }

    @Override
    public Component status() {
        return Component.translatable("elysiumspace.machine.generator", energy.getEnergyStored(),
                energy.getMaxEnergyStored(), Component.translatable(level != null && level.isDay()
                        ? "elysiumspace.machine.running" : "elysiumspace.machine.idle"));
    }
}
