package com.elysium.lib.loot;

import com.elysium.lib.affix.ElysiumAffixRoll;
import com.elysium.lib.tier.ElysiumLootRarity;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

import java.util.List;
import java.util.Optional;

/**
 * A loot function that rolls affixes onto whatever came out of the table.
 *
 * <pre>
 * { "function": "elysiumlib:roll_affixes" }
 * { "function": "elysiumlib:roll_affixes", "count": 2 }
 * </pre>
 *
 * <p>With no {@code count} the rarity is rolled from the world tier of whoever
 * the drop is for and their luck, and the rarity <em>is</em> the number of
 * affixes — which is the ordinary case, and the one a pack should use. A
 * stated count overrides it, for a table that means a specific thing.
 *
 * <h2>Why this exists now rather than with the tiers</h2>
 *
 * How many affixes an item gets is rarity, and rarity is gated by world tier,
 * which is P5. But a roller nothing can call is a page of code that compiles,
 * validates and does nothing in game — the same trap three coded traits fell
 * into and were caught in. A loot function makes the roller reachable from
 * data the day it is written: a pack can put affixes in a chest now, and when
 * the tiers arrive they decide the count rather than the mechanism.
 *
 * <p>Items that are not gear come out untouched, so a table that rolls over
 * everything it drops does no harm to the bread.
 */
public class ElysiumRollAffixesFunction extends LootItemConditionalFunction {

    public static final MapCodec<ElysiumRollAffixesFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
            commonFields(instance).and(
                    NumberProviders.CODEC.optionalFieldOf("count")
                            .forGetter(function -> function.count)
            ).apply(instance, ElysiumRollAffixesFunction::new));

    private final Optional<NumberProvider> count;

    protected ElysiumRollAffixesFunction(List<LootItemCondition> conditions,
                                         Optional<NumberProvider> count) {
        super(conditions);
        this.count = count;
    }

    @Override
    public LootItemFunctionType<? extends LootItemConditionalFunction> getType() {
        return ElysiumLoot.ROLL_AFFIXES.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        int tier = ElysiumLootTier.of(context);
        int rarity = count.isPresent()
                ? count.get().getInt(context)
                : ElysiumLootRarity.roll(tier, ElysiumLootTier.luck(context),
                        context.getRandom()::nextDouble);
        if (rarity <= 0) {
            return stack;                  // a Common drop is a plain one
        }
        ElysiumAffixRoll.roll(stack, ElysiumLootRarity.affixes(rarity), tier, context.getRandom());
        // The rarity is the gear tier, so the name takes the colour that tier
        // already means everywhere else in the mod rather than a second one.
        stack.set(net.minecraft.core.component.DataComponents.RARITY,
                com.elysium.lib.item.ElysiumRarities.getRarityFromTier(rarity));
        return stack;
    }
}
