package com.elysium.space.machine;

import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * An FE store a machine can also fill and spend itself. NeoForge's own
 * receive/extract obey the outside limits (a consumer cannot be drained by a
 * cable, a generator cannot be charged by one); produce/consume are the
 * machine's own work and ignore them.
 */
public class MachineEnergy extends EnergyStorage {

    public MachineEnergy(int capacity, int maxReceive, int maxExtract) {
        super(capacity, maxReceive, maxExtract);
    }

    public int produce(int amount) {
        int added = Math.max(0, Math.min(capacity - energy, amount));
        energy += added;
        return added;
    }

    public boolean consume(int amount) {
        if (amount < 0 || energy < amount) {
            return false;
        }
        energy -= amount;
        return true;
    }

    public boolean isFull() {
        return energy >= capacity;
    }
}
