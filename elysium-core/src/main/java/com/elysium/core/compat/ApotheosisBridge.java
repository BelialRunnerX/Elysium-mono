package com.elysium.core.compat;

import com.elysium.lib.item.ElysiumGear;
import com.elysium.lib.item.ElysiumGearData;
import com.elysium.lib.item.ElysiumSocketable;
import com.elysium.lib.item.ElysiumSockets;
import dev.shadowsoffire.apotheosis.affix.Affix;
import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.affix.AffixRegistry;
import dev.shadowsoffire.apotheosis.affix.AffixType;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.socket.SocketHelper;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Elysium gear and Apotheosis gear, each granted the other's identity.
 *
 * <h2>Why nothing is taken away</h2>
 *
 * Apotheosis affixes and Elysium's runes and reforge rolls were already able to
 * sit on the same item — they are separate data components adding separate
 * attribute modifiers, and neither mod knows the other exists. So a conversion
 * that <em>stripped</em> one to grant the other would be destroying something
 * the game was perfectly happy to keep. Both directions here add.
 *
 * Which means the two directions are not opposites. "To Apotheosis" grants
 * affixes to a piece that has none; "from Apotheosis" grants Elysium data to a
 * piece that has none. A piece carrying both is finished, and
 * {@link GearConvergence#directionFor} says so rather than charging a player
 * for an item that comes back identical.
 *
 * <h2>Why every number here is derived and none is rolled</h2>
 *
 * The affixes a piece receives are a function of what is already on it: its
 * tier, its reforge rolls, its rune slots, and its item id. Nothing consults a
 * random source.
 *
 * That is the difference between a feature and an exploit. Apotheosis rolls
 * affixes randomly when it generates loot, and copying that here would make the
 * smithing table a slot machine — convert, read the tooltip, convert back,
 * repeat until the affixes are good. Deriving instead means the second
 * conversion produces exactly the first one's answer, so there is nothing to
 * farm. A player who wants better affixes has to make the piece better first,
 * which is the loop the rest of Elysium already has.
 *
 * <h2>What the derivation is</h2>
 *
 * <ul>
 *   <li><b>Rarity</b> from the effective tier, as a position in Apotheosis's own
 *       sorted rarity list rather than a hard-coded name — a pack that adds or
 *       removes a rarity gets a sensible answer instead of a crash.</li>
 *   <li><b>How many affixes</b> from how much work is on the piece: its reforge
 *       rolls and its ascension.</li>
 *   <li><b>Which affixes</b> by walking Apotheosis's own candidates for this
 *       item, in id order, and stepping through them with a stride taken from
 *       the piece's own data. Id order is what makes it stable across
 *       launches; the stride is what stops every piece of the same tier
 *       getting the same affix.</li>
 *   <li><b>Sockets</b> from the rune slots the piece's tier grants it.</li>
 * </ul>
 */
final class ApotheosisBridge {

    private ApotheosisBridge() {
    }

    /** At most this many affixes, however invested the piece is. */
    private static final int MAX_AFFIXES = 4;

    /** Whether Apotheosis considers this item to carry its data already. */
    static boolean hasApotheosisData(ItemStack stack) {
        return AffixHelper.hasAffixes(stack)
                || AffixHelper.getRarity(stack).isBound()
                || SocketHelper.getSockets(stack) > 0;
    }

    /**
     * A stable number for this exact piece.
     *
     * The item id and every field of the gear data, and nothing else — no
     * position, no time, no random source. Two identical pieces convert
     * identically, and the same piece converted twice gets the same answer,
     * which is the whole point.
     */
    private static int fingerprint(ItemStack stack, ElysiumGearData data) {
        int hash = BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode();
        hash = hash * 31 + data.armorBonus();
        hash = hash * 31 + data.healthBonus();
        hash = hash * 31 + data.speedBonus();
        hash = hash * 31 + data.ascendedTier();
        hash = hash * 31 + data.reforgeCount();
        for (String rune : data.runes()) {
            hash = hash * 31 + rune.hashCode();
        }
        return hash & 0x7FFFFFFF;
    }

    /** Where this tier sits in Apotheosis's rarity ladder. */
    private static LootRarity rarityForTier(int tier) {
        List<LootRarity> ladder = RarityRegistry.getSortedRarities();
        if (ladder.isEmpty()) {
            return null;
        }
        // Elysium tiers run from 0 upward with no fixed ceiling, so clamp
        // rather than scale: a tier past the top of the ladder is the top of
        // the ladder, not an index out of bounds.
        int index = Math.max(0, Math.min(ladder.size() - 1, tier));
        return ladder.get(index);
    }

    /** How much work a player has put into this piece, as a small integer. */
    private static int investment(ElysiumGearData data) {
        int rolls = data.armorBonus() + data.healthBonus() + data.speedBonus();
        int ascension = Math.max(0, data.ascendedTier());
        return rolls + ascension + data.runes().size();
    }

    static ItemStack grantApotheosis(ItemStack stack) {
        LootCategory category = LootCategory.forItem(stack);
        if (category == null || category.isNone()) {
            // Apotheosis has no idea what this item is, so it can hold no
            // affixes. Refusing is the honest answer; setting the components
            // anyway would produce a piece whose tooltip promises what nothing
            // will ever read.
            return ItemStack.EMPTY;
        }
        ElysiumSocketable gear = ElysiumGear.of(stack);
        if (gear == null) {
            return ItemStack.EMPTY;
        }
        ElysiumGearData data = ElysiumSockets.gearData(stack);
        LootRarity rarity = rarityForTier(gear.getEffectiveTier(stack));
        if (rarity == null) {
            return ItemStack.EMPTY;      // a pack with no rarities at all
        }
        DynamicHolder<LootRarity> rarityHolder =
                RarityRegistry.INSTANCE.holder(rarity);

        ItemStack result = stack.copy();
        AffixHelper.setRarity(result, rarity);

        // Sockets from rune slots: the piece has as many places to put an
        // Apotheosis gem as Elysium would give it places to put a rune.
        SocketHelper.setSockets(result, gear.getMaxRuneSlots(stack));

        int wanted = Math.min(MAX_AFFIXES, 1 + investment(data) / 4);
        for (AffixInstance affix : chooseAffixes(result, category, rarity,
                rarityHolder, fingerprint(stack, data), wanted)) {
            AffixHelper.applyAffix(result, affix);
        }
        return result;
    }

    /**
     * Pick affixes without rolling any.
     *
     * Candidates come from Apotheosis's own type map and are filtered by its own
     * {@code canApplyTo}, so this never invents a combination the mod would
     * refuse. They are then sorted by id — the one ordering that is the same on
     * every launch, unlike registry iteration order — and walked with a stride
     * taken from the piece's fingerprint, so two swords of the same tier do not
     * come out identical.
     */
    private static List<AffixInstance> chooseAffixes(
            ItemStack stack, LootCategory category, LootRarity rarity,
            DynamicHolder<LootRarity> rarityHolder, int fingerprint, int wanted) {

        List<DynamicHolder<Affix>> candidates = new ArrayList<>();
        for (AffixType type : new AffixType[] {AffixType.STAT, AffixType.BASIC_EFFECT}) {
            for (DynamicHolder<Affix> holder
                    : AffixRegistry.INSTANCE.getTypeMap().get(type)) {
                if (holder.isBound()
                        && holder.get().canApplyTo(stack, category, rarity)) {
                    candidates.add(holder);
                }
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        candidates.sort(Comparator.comparing(holder -> holder.getId().toString()));

        List<AffixInstance> chosen = new ArrayList<>();
        int size = candidates.size();
        // A stride coprime with the list length visits distinct entries; the
        // +1 and the modulus keep it in range for a one-element list too.
        int stride = 1 + (fingerprint % Math.max(1, size - 1));
        int index = fingerprint % size;
        for (int taken = 0; taken < Math.min(wanted, size); taken++) {
            DynamicHolder<Affix> holder = candidates.get(index % size);
            // The level is derived the same way, scaled into the range
            // Apotheosis itself uses for a rolled affix.
            float level = ((fingerprint >> (taken * 3)) % 1000) / 1000.0F;
            chosen.add(new AffixInstance(holder, level, rarityHolder, stack));
            index += stride;
        }
        return chosen;
    }

    /**
     * Give an affixed piece the Elysium data it never had.
     *
     * The inverse derivation: the rarity's position in the ladder becomes an
     * ascended tier, the affixes become reforge rolls, and the sockets stay
     * where they are. Everything Apotheosis wrote is left exactly as it was.
     */
    static ItemStack grantElysium(ItemStack stack) {
        ElysiumSocketable gear = ElysiumGear.of(stack);
        if (gear == null) {
            return ItemStack.EMPTY;
        }
        DynamicHolder<LootRarity> rarity = AffixHelper.getRarity(stack);
        int ladderIndex = -1;
        if (rarity.isBound()) {
            ladderIndex = RarityRegistry.getSortedRarities().indexOf(rarity.get());
        }
        int affixes = AffixHelper.getAffixes(stack).size();

        // The rolls are split across the three stats rather than piled onto
        // one, because a piece that arrives with everything in Fortitude and
        // nothing else reads as broken rather than as lucky.
        int total = affixes + Math.max(0, ladderIndex);
        ElysiumGearData data = new ElysiumGearData(
                List.of(),
                (total + 2) / 3,
                (total + 1) / 3,
                total / 3,
                ladderIndex,
                0);

        ItemStack result = stack.copy();
        ElysiumSockets.setGearData(result, data);
        return result;
    }
}
