package com.elysium.agronomy.item;

import com.elysium.agronomy.rules.KitchenRules;
import net.minecraft.world.item.Item;

/**
 * A knife: the one tool a cutting board answers to.
 *
 * <p>Nothing here but the class, and the class is the point. A board asked
 * "is that a knife" used to have no way to answer except by name, and five
 * items called {@code *_knife} that were plain {@link Item}s were five items
 * that cut nothing. Now the board asks {@code instanceof}, and a sixth knife
 * is a knife the day it is registered.
 *
 * <p>What a knife does to what it cuts is {@link KitchenRules#CUTS}, read
 * off disk; how much a cut wears it is {@link KitchenRules#KNIFE_WEAR}.
 */
public class KnifeItem extends Item {

    public KnifeItem(Properties properties) {
        super(properties);
    }

    /** What a cutting recipe calls this kind of tool. */
    public String tool() {
        return KitchenRules.KNIFE;
    }
}
