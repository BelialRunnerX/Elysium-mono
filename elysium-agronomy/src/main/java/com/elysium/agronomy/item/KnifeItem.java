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
 *
 * <p>A knife carries the name a cutting recipe calls it by, and the board
 * asks for cuts under that name. The five kitchen knives are {@link
 * KitchenRules#KNIFE}; the fillet knife is {@link KitchenRules#FILLET_KNIFE},
 * and cuts the fish the kitchen's knives leave alone.
 */
public class KnifeItem extends Item {

    private final String tool;

    /** A kitchen knife. */
    public KnifeItem(Properties properties) {
        this(properties, KitchenRules.KNIFE);
    }

    /** A knife a cutting recipe calls by another name. */
    public KnifeItem(Properties properties, String tool) {
        super(properties);
        if (!KitchenRules.TOOLS.contains(tool)) {
            throw new IllegalArgumentException(tool + " is no tool a cutting recipe asks for, "
                    + "so a knife of it would cut nothing");
        }
        this.tool = tool;
    }

    /** What a cutting recipe calls this kind of tool. */
    public String tool() {
        return tool;
    }
}
