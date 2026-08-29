package com.elysium.lib.affix;

import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.item.ElysiumSocketable;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/**
 * A psionic affix — an {@link ElysiumAffix} bound to one of the five Elysium
 * elements. Only gear of the matching element receives it.
 */
public class ElysiumPsionicAffix extends ElysiumAffix {

    private final ElysiumElement element;

    /**
     * An affix's name becomes part of its attribute modifier id, so it has to
     * be a stable plain string rather than the element's ResourceLocation.
     */
    private static String nameOf(ElysiumElement element) {
        net.minecraft.resources.ResourceLocation id = element.getId();
        return id == null ? "none" : id.getNamespace() + "_" + id.getPath();
    }

    public ElysiumPsionicAffix(ElysiumElement element,
                               Holder<Attribute> attribute,
                               AttributeModifier.Operation operation,
                               float minValue,
                               float maxValue) {
        super(nameOf(element), attribute, operation, minValue, maxValue);
        this.element = element;
    }

    public ElysiumElement getElement() {
        return element;
    }

    @Override
    public boolean canApplyTo(ItemStack stack) {
        return stack.getItem() instanceof ElysiumSocketable gear && gear.getElement() == this.element;
    }
}
