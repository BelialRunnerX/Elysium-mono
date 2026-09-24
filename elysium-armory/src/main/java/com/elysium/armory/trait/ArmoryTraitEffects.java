package com.elysium.armory.trait;

import com.elysium.armory.item.ArmoryGearTraits;
import com.elysium.armory.material.ArmoryTraitDefinition;
import com.elysium.armory.material.ArmoryTraits;
import com.elysium.armory.property.ArmoryTraitRules;
import com.elysium.armory.registry.ArmoryComponents;
import com.elysium.lib.effect.ElysiumItemEffects;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reading the effects off the traits a piece of gear has.
 *
 * <p>Every handler asks the same question — "what does this stack have of this
 * kind of effect, and at what level" — so it is asked in one place. A trait
 * whose level is past what it was written for is read at its maximum
 * ({@link ArmoryTraitRules#capped}); the level itself stays what the player
 * built, which is what the tooltip shows.
 */
public final class ArmoryTraitEffects {

    private ArmoryTraitEffects() {
    }

    /**
     * An effect arrives as the library's {@link ElysiumItemEffects.Active},
     * not a type of our own: the handlers that read these are written once for
     * every attachment an item can have, and a trait's effect has to arrive in
     * the same shape an affix's does or nothing could act on both.
     *
     * <p>The traits on a stack, as an attachment the library can gather effects
     * from. Registered at load; the handlers never ask the armory anything.
     */
    public static void register() {
        ElysiumItemEffects.register("elysiumarmory:traits", new ElysiumItemEffects.Source() {

            @Override
            public List<ElysiumItemEffects.Active> effects(ItemStack stack, String kind) {
                return ArmoryTraitEffects.of(stack, kind);
            }

            @Override
            public int level(ItemStack stack, String name) {
                return ArmoryTraitEffects.level(stack, name);
            }
        });
    }

    public static ArmoryGearTraits traitsOf(ItemStack stack) {
        return stack.getOrDefault(ArmoryComponents.TRAITS.get(), ArmoryGearTraits.NONE);
    }

    /** Every effect of one type on this stack, at the trait's capped level. */
    public static List<ElysiumItemEffects.Active> of(ItemStack stack, String type) {
        ArmoryGearTraits traits = traitsOf(stack);
        if (traits.isEmpty()) {
            return List.of();
        }
        List<ElysiumItemEffects.Active> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : traits.levels().entrySet()) {
            ArmoryTraitDefinition definition = ArmoryTraits.get().byId(entry.getKey());
            if (definition == null) {
                continue;
            }
            int level = ArmoryTraitRules.capped(entry.getValue(), definition.maxLevel());
            for (JsonObject effect : definition.effects(type)) {
                out.add(new ElysiumItemEffects.Active(entry.getKey(), level, effect));
            }
        }
        return out;
    }

    /** Whether the stack carries any effect of a type: the cheap check a tick can afford. */
    public static boolean has(ItemStack stack, String type) {
        return !of(stack, type).isEmpty();
    }

    /** The level of a named trait on a stack, capped at what it was written for. */
    public static int level(ItemStack stack, String trait) {
        ArmoryTraitDefinition definition = ArmoryTraits.get().byId(trait);
        int raw = traitsOf(stack).level(trait);
        return definition == null ? raw : ArmoryTraitRules.capped(raw, definition.maxLevel());
    }
}
