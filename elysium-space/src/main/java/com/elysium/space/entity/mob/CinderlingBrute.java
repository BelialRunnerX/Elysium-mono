package com.elysium.space.entity.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.level.Level;

/** The cinderlings' guards (Stellaris's pygro brute): piglin brutes that never zombify on Cinderveil. */
public class CinderlingBrute extends PiglinBrute {

    public CinderlingBrute(EntityType<? extends PiglinBrute> type, Level level) {
        super(type, level);
        setImmuneToZombification(true);
    }
}
