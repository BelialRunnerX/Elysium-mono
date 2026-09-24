package com.elysium.lib.attribute;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.TooltipFlag;

/**
 * A chance attribute. It stores odds (see {@link ElysiumAttributeSpec}) but
 * displays a percentage, so an item reads "+5% Crit Chance" rather than
 * "+0.05263". Only the number is changed. This builds a Component and never
 * calls tooltip code, so it is safe to run on a dedicated server.
 */
public class ElysiumChanceAttribute extends RangedAttribute {

    public ElysiumChanceAttribute(String descriptionId, double defaultOdds,
                                  double minOdds, double max) {
        super(descriptionId, defaultOdds, minOdds, max);
    }

    @Override
    public MutableComponent toValueComponent(AttributeModifier.Operation operation,
                                             double value, TooltipFlag flag) {
        if (operation != AttributeModifier.Operation.ADD_VALUE) {
            return super.toValueComponent(operation, value, flag);
        }
        return Component.literal(ElysiumAttributeSpec.percentText(value));
    }
}
