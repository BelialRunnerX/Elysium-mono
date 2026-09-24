package com.elysium.enchanting.event;

import com.elysium.enchanting.ElysiumEnchanting;
import com.elysium.enchanting.registry.EnchantingItems;
import com.elysium.enchanting.rules.AnvilRules;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * What this module does to the anvil.
 *
 * <h2>Two things, and only two</h2>
 *
 * <ol>
 *   <li><b>The tomes.</b> Four single-use books, each answering something a
 *       long world runs into and vanilla has no move for.</li>
 *   <li><b>The wall.</b> An operation vanilla refuses because it costs forty
 *       levels or more is computed here instead and offered at its real price.
 *       Nothing else about the anvil changes: an operation vanilla is willing
 *       to do is left to vanilla, so a player's anvil behaves exactly as they
 *       expect right up to the point where it used to stop working.</li>
 * </ol>
 *
 * The second one is deliberately narrow. Reimplementing the whole anvil would
 * mean this module quietly owning every repair, rename and combination in the
 * game, and every difference from vanilla's arithmetic would be a bug nobody
 * could find.
 */
@EventBusSubscriber(modid = ElysiumEnchanting.MODID)
public final class EnchantingAnvil {

    private EnchantingAnvil() {
    }

    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) {
            return;
        }
        if (tome(event, left, right)) {
            return;
        }
        wall(event, left, right);
    }

    // ------------------------------------------------------------------
    // The tomes
    // ------------------------------------------------------------------

    private static boolean tome(AnvilUpdateEvent event, ItemStack left, ItemStack right) {
        if (right.is(EnchantingItems.SCRAPPING_TOME.get())) {
            return scrap(event, left);
        }
        if (right.is(EnchantingItems.TRANSCRIPTION_TOME.get())) {
            return transcribe(event, left);
        }
        if (right.is(EnchantingItems.CONCENTRATION_TOME.get())) {
            return concentrate(event, left);
        }
        if (right.is(EnchantingItems.PATIENCE_TOME.get())) {
            return forgive(event, left);
        }
        return false;
    }

    /** Everything off, so a table will look at the item again. */
    private static boolean scrap(AnvilUpdateEvent event, ItemStack left) {
        ItemEnchantments on = EnchantmentHelper.getEnchantmentsForCrafting(left);
        if (on.isEmpty()) {
            return false;
        }
        ItemStack output = left.copy();
        output.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        output.remove(DataComponents.STORED_ENCHANTMENTS);
        finish(event, output, AnvilRules.cost(levels(on), priorWork(left), 0));
        return true;
    }

    /** Everything off the item and onto a book, at the cost of the item. */
    private static boolean transcribe(AnvilUpdateEvent event, ItemStack left) {
        ItemEnchantments on = EnchantmentHelper.getEnchantmentsForCrafting(left);
        if (on.isEmpty() || left.is(Items.ENCHANTED_BOOK)) {
            return false;
        }
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, on);
        finish(event, book, AnvilRules.cost(levels(on) * 2, priorWork(left), 0));
        return true;
    }

    /** A mixed book narrowed to its best line. */
    private static boolean concentrate(AnvilUpdateEvent event, ItemStack left) {
        ItemEnchantments on = EnchantmentHelper.getEnchantmentsForCrafting(left);
        if (!left.is(Items.ENCHANTED_BOOK) || on.size() < 2) {
            return false;
        }
        Holder<Enchantment> best = null;
        int bestLevel = 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : on.entrySet()) {
            if (entry.getIntValue() > bestLevel) {
                best = entry.getKey();
                bestLevel = entry.getIntValue();
            }
        }
        if (best == null) {
            return false;
        }
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable only = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        only.set(best, bestLevel);
        book.set(DataComponents.STORED_ENCHANTMENTS, only.toImmutable());
        finish(event, book, AnvilRules.cost(levels(on), priorWork(left), 0));
        return true;
    }

    /** The history cleared, for what that history was costing. */
    private static boolean forgive(AnvilUpdateEvent event, ItemStack left) {
        int history = priorWork(left);
        if (history <= 0) {
            return false;
        }
        ItemStack output = left.copy();
        output.set(DataComponents.REPAIR_COST, AnvilRules.forgiven());
        // Priced at exactly what the history was costing, so the tome is worth
        // using at the moment the anvil has become unaffordable and not before.
        finish(event, output, Math.max(1, AnvilRules.priorWorkCost(history)));
        return true;
    }

    private static void finish(AnvilUpdateEvent event, ItemStack output, int cost) {
        event.setOutput(output);
        event.setCost(cost);
        event.setMaterialCost(1);
    }

    // ------------------------------------------------------------------
    // The wall
    // ------------------------------------------------------------------

    /**
     * The operation vanilla would refuse, computed and offered.
     *
     * Only when it would refuse: below the cap this does nothing at all and
     * the anvil a player knows is the anvil they get.
     */
    private static void wall(AnvilUpdateEvent event, ItemStack left, ItemStack right) {
        boolean book = right.is(Items.ENCHANTED_BOOK);
        boolean alike = left.getItem() == right.getItem() && left.isDamageableItem();
        if (!book && !alike) {
            return;
        }
        ItemEnchantments incoming = EnchantmentHelper.getEnchantmentsForCrafting(right);
        if (incoming.isEmpty() && !alike) {
            return;
        }
        ItemStack output = left.copy();
        ItemEnchantments.Mutable merged =
                new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(left));
        int worth = 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : incoming.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            if (!enchantment.value().canEnchant(output)) {
                continue;
            }
            boolean clashes = false;
            for (Holder<Enchantment> worn : merged.keySet()) {
                if (!worn.equals(enchantment) && !Enchantment.areCompatible(worn, enchantment)) {
                    clashes = true;
                    break;
                }
            }
            if (clashes) {
                continue;
            }
            int landed = AnvilRules.merged(merged.getLevel(enchantment), entry.getIntValue(),
                    enchantment.value().getMaxLevel());
            merged.set(enchantment, landed);
            // The enchantment's own anvil cost, halved out of a book, which is
            // vanilla's rule and the one players have priced things by for a
            // decade.
            int per = Math.max(1, enchantment.value().definition().anvilCost() / (book ? 2 : 1));
            worth += landed * per;
        }
        if (alike && left.getDamageValue() > 0) {
            output.setDamageValue(AnvilRules.repair(left.getMaxDamage(), left.getDamageValue()));
            worth += 2;
        }
        ItemEnchantments result = merged.toImmutable();
        if (worth <= 0 || (result.equals(EnchantmentHelper.getEnchantmentsForCrafting(left))
                && output.getDamageValue() == left.getDamageValue())) {
            return;
        }
        int cost = AnvilRules.cost(worth, priorWork(left), priorWork(right));
        if (!AnvilRules.refusedByVanilla(cost)) {
            // Vanilla is willing to do this one. Leave it alone: an anvil that
            // behaves differently in the ordinary case is a bug report nobody
            // can reproduce.
            return;
        }
        EnchantmentHelper.setEnchantments(output, result);
        output.set(DataComponents.REPAIR_COST,
                AnvilRules.nextPriorWork(priorWork(left), priorWork(right)));
        event.setOutput(output);
        event.setCost(cost);
        event.setMaterialCost(1);
    }

    private static int levels(ItemEnchantments on) {
        int total = 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : on.entrySet()) {
            total += entry.getIntValue();
        }
        return total;
    }

    private static int priorWork(ItemStack stack) {
        return stack.getOrDefault(DataComponents.REPAIR_COST, 0);
    }
}
