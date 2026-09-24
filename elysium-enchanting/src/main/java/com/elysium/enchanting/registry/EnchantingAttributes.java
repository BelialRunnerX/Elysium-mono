package com.elysium.enchanting.registry;

import com.elysium.enchanting.ElysiumEnchanting;
import com.elysium.enchanting.rules.EnchantingRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The one attribute enchanting adds (Apothic Enchanting's AE-12).
 *
 * <h2>Why it is an attribute and not a config value</h2>
 *
 * The cap on a table's power is the thing a player should be able to raise,
 * and everything else in this mod that a player raises is an attribute: gear
 * can grant it, a trait can grant it, an affix can grant it, a world tier can
 * grant it. A number in a config file can be raised by nobody.
 *
 * <p>Uncapped upward, like every other attribute in this mod, and floored at
 * nothing: a player with negative maximum eterna would have a table that
 * refuses to work for reasons they cannot see.
 */
public final class EnchantingAttributes {

    private EnchantingAttributes() {
    }

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, ElysiumEnchanting.MODID);

    public static final DeferredHolder<Attribute, Attribute> MAX_ETERNA =
            ATTRIBUTES.register("max_eterna", () -> new RangedAttribute(
                    "attribute.name.elysiumenchanting.max_eterna",
                    EnchantingRules.BASE_MAX_ETERNA, 0.0D, Double.MAX_VALUE).setSyncable(true));

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
    }
}
