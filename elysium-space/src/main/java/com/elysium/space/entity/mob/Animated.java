package com.elysium.space.entity.mob;

import net.minecraft.world.entity.AnimationState;

/**
 * A mob whose one-shot keyframe animations (an attack, a head shake) are
 * started by the game and played by the client's rigged model. The rig names
 * each animation's state ("attack", "shake"); a mob returns null for a state
 * it does not have, and the model then skips that animation.
 */
public interface Animated {

    AnimationState animation(String state);
}
