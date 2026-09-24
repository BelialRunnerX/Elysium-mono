package com.elysium.space.entity.mob;

import com.elysium.space.registry.SpaceEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.level.Level;

/**
 * Cinderveil's boar (Stellaris's mogler): a hoglin that is at home on its
 * world, so it never zombifies outside the Nether, breeds true, and does not
 * despawn. Plays Stellaris's charge animation when it strikes (the hoglin's
 * own attack event, 4).
 */
public class CinderBoar extends Hoglin implements Animated {

    private final AnimationState attack = new AnimationState();

    public CinderBoar(EntityType<? extends Hoglin> type, Level level) {
        super(type, level);
        setImmuneToZombification(true);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        CinderBoar child = SpaceEntities.CINDER_BOAR.get().create(level);
        if (child != null) {
            child.setPersistenceRequired();
        }
        return child;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) {
            attack.start(tickCount);
        }
        super.handleEntityEvent(id);
    }

    @Override
    public AnimationState animation(String state) {
        return "attack".equals(state) ? attack : null;
    }
}
