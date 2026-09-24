package com.elysium.lib.affix;

import com.elysium.lib.item.ElysiumComponents;
import com.elysium.lib.item.ElysiumLootCategory;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Putting affixes on an item.
 *
 * <h2>What a roll is</h2>
 *
 * A number of affixes, drawn from the pools that accept this kind of item, at
 * levels drawn the same way. Nothing here decides <em>how many</em> beyond
 * what it is asked for: that is rarity, and rarity is gated by world tier,
 * which is P5's business. This is the act itself, so that when the tiers
 * arrive they have something to call.
 *
 * <h2>One prefix, one suffix, then more of either</h2>
 *
 * An item's name has room for one of each, so the first two affixes are drawn
 * one from each pool. Past that an item keeps rolling from whichever pool has
 * anything left, and the extra affixes are real but unnamed — they show in the
 * tooltip rather than in the name, which is the honest way round: the name can
 * only say so much, and the effects are what the player is holding.
 */
public final class ElysiumAffixRoll {

    private ElysiumAffixRoll() {
    }

    /**
     * Rolls affixes onto a stack, replacing whatever it had.
     *
     * @param count     how many to try for; fewer land if the pools run dry
     * @param worldTier the tier of whoever the loot is for, which decides
     *                  which affixes exist to be drawn at all
     * @return the stack, for chaining
     */
    public static ItemStack roll(ItemStack stack, int count, int worldTier, RandomSource random) {
        String kind = ElysiumLootCategory.of(stack);
        if (kind == null || count <= 0) {
            return stack;
        }
        com.elysium.lib.api.ElysiumEvents.AffixRoll asked =
                com.elysium.lib.api.ElysiumEvents.fireAffixRoll(stack, count, worldTier);
        if (asked.isCanceled() || asked.getCount() <= 0) {
            return stack;
        }
        count = asked.getCount();
        worldTier = asked.getWorldTier();
        ElysiumItemAffixes affixes = ElysiumItemAffixes.NONE;
        String[] order = {ElysiumAffixRules.PREFIX, ElysiumAffixRules.SUFFIX};
        for (int i = 0; i < count; i++) {
            String type = order[i % order.length];
            affixes = draw(affixes, kind, type, worldTier, random);
        }
        if (affixes.isEmpty()) {
            return stack;
        }
        stack.set(ElysiumComponents.AFFIXES.get(), affixes);
        return stack;
    }

    /**
     * One more affix of a placement, if the pool has one this item does not
     * already carry.
     */
    public static ElysiumItemAffixes draw(ElysiumItemAffixes affixes, String kind, String type,
                                          int worldTier, RandomSource random) {
        List<ElysiumAffixRules.Candidate> pool = ElysiumAffixCatalogue.get().pool(kind, type, worldTier);
        if (pool.isEmpty()) {
            return affixes;
        }
        List<ElysiumAffixRules.Candidate> left = new java.util.ArrayList<>();
        for (ElysiumAffixRules.Candidate candidate : pool) {
            if (affixes.level(candidate.id()) == 0) {
                left.add(candidate);
            }
        }
        String chosen = ElysiumAffixRules.pick(left, random.nextDouble());
        if (chosen == null) {
            return affixes;
        }
        ElysiumAffixDefinition affix = ElysiumAffixCatalogue.get().byId(chosen);
        int level = ElysiumAffixRules.level(affix == null ? 1 : affix.maxLevel(), random.nextDouble());
        return affixes.with(chosen, level);
    }

    /** Takes every affix off a stack, which is what a reforge starts with. */
    public static ItemStack clear(ItemStack stack) {
        stack.remove(ElysiumComponents.AFFIXES.get());
        return stack;
    }
}
