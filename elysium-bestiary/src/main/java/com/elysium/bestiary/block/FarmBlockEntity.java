package com.elysium.bestiary.block;

import com.elysium.bestiary.block.act.FarmActs;
import com.elysium.bestiary.registry.FarmBlocks;
import com.elysium.bestiary.rules.FarmRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The half of a machine that remembers something.
 *
 * <p>Two numbers and a name. The name is how it finds its row; the two numbers
 * are what it has collected, which only the gatherers and keepers use. A block
 * entity per kind would be five more classes and five more places for the row
 * to stop being consulted.
 */
public class FarmBlockEntity extends BlockEntity {

    /** How often a powered machine does its one thing, in ticks. */
    public static final int BETWEEN = 20;

    private final String name;
    private int sinceAct;
    private int heldOrbs;

    public FarmBlockEntity(BlockPos where, BlockState state, String name) {
        super(FarmBlocks.MACHINE_ENTITY.get(), where, state);
        this.name = name;
    }

    /** The row this one is, by the name it was built with. */
    public FarmRules.Machine machine() {
        FarmRules.Machine found = FarmRules.machine(name);
        return found == null ? FarmRules.MACHINES.get(0) : found;
    }

    /** How much experience it is sitting on. */
    public int heldOrbs() {
        return heldOrbs;
    }

    /** Put some in, which is what a gatherer does with what it picks up. */
    public void hold(int orbs) {
        heldOrbs = Math.max(0, heldOrbs + orbs);
    }

    /** And take it back out. */
    public int takeOrbs(int wanted) {
        int given = Math.min(Math.max(0, wanted), heldOrbs);
        heldOrbs -= given;
        return given;
    }

    /** One tick of whatever this machine is. */
    public void serverTick() {
        sinceAct++;
        if (sinceAct < BETWEEN) {
            return;
        }
        sinceAct = 0;
        FarmActs.happen(this, machine());
    }

    @Override
    protected void saveAdditional(CompoundTag tag,
                                  net.minecraft.core.HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putInt("ElysiumSinceAct", sinceAct);
        tag.putInt("ElysiumHeldOrbs", heldOrbs);
    }

    @Override
    protected void loadAdditional(CompoundTag tag,
                                  net.minecraft.core.HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        sinceAct = tag.getInt("ElysiumSinceAct");
        heldOrbs = tag.getInt("ElysiumHeldOrbs");
    }
}
