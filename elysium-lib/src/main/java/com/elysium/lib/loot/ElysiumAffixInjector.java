package com.elysium.lib.loot;

import com.elysium.lib.affix.ElysiumAffixEffects;
import com.elysium.lib.affix.ElysiumAffixRoll;
import com.elysium.lib.item.ElysiumLootCategory;
import com.elysium.lib.item.ElysiumRarities;
import com.elysium.lib.tier.ElysiumLootRarity;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Affixes on whatever the world was already going to drop.
 *
 * <h2>The choice this makes</h2>
 *
 * Apotheosis injects <em>new</em> items into loot tables: a chest that held
 * bread now also holds an affixed sword. That works, and it means the loot a
 * player finds stops being the loot the pack author wrote.
 *
 * <p>This does the other thing. It adds nothing: it looks at what the table
 * already produced, and if a piece of that is gear, it rolls affixes onto it.
 * A dungeon chest keeps holding exactly what the dungeon's author said it
 * holds, and a stone pickaxe out of a village chest is still a stone pickaxe —
 * only, at a high enough tier, a Tempered stone pickaxe of Delving.
 *
 * <p>Two things follow from that and both are wanted. A pack that adds its own
 * gear gets affixes on it for free, with no patching. And the loot economy
 * stays whoever wrote the table's to balance, because the count of items never
 * changes.
 *
 * <h2>What decides how much</h2>
 *
 * The rarity roll, which is the world tier of whoever the drop is for and
 * their luck. A Frontier player finds plain gear; nothing here is a chance
 * that a pack has to tune.
 *
 * <p>Gear that already carries affixes is left alone — a table that rolled
 * them itself has said what it wants.
 */
public class ElysiumAffixInjector extends LootModifier {

    public static final MapCodec<ElysiumAffixInjector> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).apply(instance, ElysiumAffixInjector::new));

    public ElysiumAffixInjector(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generated,
                                                 LootContext context) {
        int tier = ElysiumLootTier.of(context);
        if (tier <= 0) {
            return generated;              // the Frontier drops what it always did
        }
        double luck = ElysiumLootTier.luck(context);
        for (ItemStack stack : generated) {
            if (ElysiumLootCategory.of(stack) == null
                    || !ElysiumAffixEffects.of(stack).isEmpty()) {
                continue;
            }
            int rarity = ElysiumLootRarity.roll(tier, luck, context.getRandom()::nextDouble);
            if (rarity <= 0) {
                continue;
            }
            ElysiumAffixRoll.roll(stack, ElysiumLootRarity.affixes(rarity), tier,
                    context.getRandom());
            if (!ElysiumAffixEffects.of(stack).isEmpty()) {
                stack.set(DataComponents.RARITY, ElysiumRarities.getRarityFromTier(rarity));
            }
        }
        return generated;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
