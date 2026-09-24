package com.elysium.agronomy.registry;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.rules.KitchenRules;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything the kitchen makes, and the effects that keep its meals apart.
 *
 * <p>The effects are the load-bearing part. Twenty-nine meals whose only
 * differences are numbers collapse into six worth cooking; twenty-nine with
 * eleven different effects do not, and the harness proves it by refusing any
 * meal another meal makes pointless.
 */
public final class KitchenItems {

    private KitchenItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumAgronomy.MODID);

    /** How long a meal's effect lasts, in ticks. */
    public static final int EFFECT_TICKS = 3_600;

    /**
     * The game's effect for each name in the rules.
     *
     * <p>A meal naming an effect that is not here would silently be a meal
     * with no effect, which is the one thing that could make two meals
     * identical after the harness has proved they are not. validate.py checks
     * every name in the rules appears here.
     */
    public static final Map<String, Holder<MobEffect>> EFFECTS = Map.ofEntries(
            Map.entry("comfort", MobEffects.REGENERATION),
            Map.entry("nourishment", MobEffects.SATURATION),
            Map.entry("regeneration", MobEffects.REGENERATION),
            Map.entry("absorption", MobEffects.ABSORPTION),
            Map.entry("resistance", MobEffects.DAMAGE_RESISTANCE),
            Map.entry("strength", MobEffects.DAMAGE_BOOST),
            Map.entry("haste", MobEffects.DIG_SPEED),
            Map.entry("speed", MobEffects.MOVEMENT_SPEED),
            Map.entry("fire_resistance", MobEffects.FIRE_RESISTANCE),
            Map.entry("water_breathing", MobEffects.WATER_BREATHING),
            Map.entry("night_vision", MobEffects.NIGHT_VISION));

    public static final Map<String, DeferredItem<Item>> INGREDIENTS = registerParts();
    public static final Map<String, DeferredItem<Item>> MEALS = registerMeals();

    private static Map<String, DeferredItem<Item>> registerParts() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        for (KitchenRules.Ingredient one : KitchenRules.INGREDIENTS) {
            made.put(one.id(), ITEMS.register(one.id(), () -> new Item(
                    new Item.Properties().food(new FoodProperties.Builder()
                            .nutrition(one.nutrition())
                            .saturationModifier(one.saturation())
                            .build()))));
        }
        if (made.size() != KitchenRules.INGREDIENTS.size()) {
            throw new IllegalStateException("the kitchen has "
                    + KitchenRules.INGREDIENTS.size() + " ingredients and "
                    + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }

    private static Map<String, DeferredItem<Item>> registerMeals() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        for (KitchenRules.Meal meal : KitchenRules.MEALS) {
            made.put(meal.id(), ITEMS.register(meal.id(), () -> {
                FoodProperties.Builder food = new FoodProperties.Builder()
                        .nutrition(meal.nutrition())
                        .saturationModifier(meal.saturation());
                Holder<MobEffect> effect = EFFECTS.get(meal.effect());
                if (effect != null) {
                    food = food.effect(new MobEffectInstance(effect, EFFECT_TICKS),
                            1.0F);
                }
                return new Item(new Item.Properties().food(food.build()));
            }));
        }
        if (made.size() != KitchenRules.MEALS.size()) {
            throw new IllegalStateException("the kitchen has "
                    + KitchenRules.MEALS.size() + " meals and " + made.size()
                    + " were registered");
        }
        return Map.copyOf(made);
    }

    /** The five knives, which is what a cutting board wants. */
    public static final Map<String, DeferredItem<Item>> KNIVES = registerKnives();

    private static Map<String, DeferredItem<Item>> registerKnives() {
        Map<String, DeferredItem<Item>> made = new LinkedHashMap<>();
        String[] kinds = {"flint", "iron", "golden", "diamond", "netherite"};
        int[] uses = {48, 180, 96, 780, 1400};
        for (int index = 0; index < kinds.length; index++) {
            final int durability = uses[index];
            made.put(kinds[index], ITEMS.register(kinds[index] + "_knife",
                    () -> new Item(new Item.Properties().durability(durability))));
        }
        if (made.size() != kinds.length) {
            throw new IllegalStateException("there are " + kinds.length
                    + " kinds of knife and " + made.size() + " were registered");
        }
        return Map.copyOf(made);
    }
}
