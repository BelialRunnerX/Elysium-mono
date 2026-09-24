package com.elysium.space.entity.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.level.Level;

/**
 * Cinderveil's natives (Stellaris's pygro): piglins at home on their world.
 * They barter, hunt and ally as piglins do, and never zombify outside the
 * Nether. Kept when far away, since they live in their settlements.
 */
public class Cinderling extends Piglin {

    public Cinderling(EntityType<? extends AbstractPiglin> type, Level level) {
        super(type, level);
        setImmuneToZombification(true);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
