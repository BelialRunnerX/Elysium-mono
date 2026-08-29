package com.elysium.lib.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Rarity;

/**
 * Elysium's six gear tiers.
 *
 * {@link Rarity} is a plain four-value enum and an item's rarity is a data
 * component, so the six tiers map onto the closest vanilla rarity for the name
 * colour and the tier itself is spelled out in the tooltip — which is what
 * keeps Legendary and Unique visually distinct.
 *
 * Each tier also carries an Imperial clearance, the rank the Code of
 * Satisfaction would file the piece under. Flavour only; nothing reads it but
 * the tooltip.
 */
public final class ElysiumRarities {

    private ElysiumRarities() {
    }

    public static final int COMMON = 0;
    public static final int UNCOMMON = 1;
    public static final int RARE = 2;
    public static final int EPIC = 3;
    public static final int LEGENDARY = 4;
    public static final int UNIQUE = 5;

    /**
     * The highest tier with a name of its own. It is <b>not</b> a ceiling —
     * ascension climbs past it forever, and everything above reads as
     * "Ascendant N". Nothing in this class caps a tier any more; the named
     * range is just the part with hand-written flavour.
     */
    public static final int MAX_NAMED_TIER = UNIQUE;

    public static Rarity getRarityFromTier(int tier) {
        return switch (tier) {
            case UNCOMMON -> Rarity.UNCOMMON;
            case RARE -> Rarity.RARE;
            case EPIC, LEGENDARY, UNIQUE -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }

    public static String getTierName(int tier) {
        if (tier > MAX_NAMED_TIER) {
            return "Ascendant " + (tier - MAX_NAMED_TIER);
        }
        return switch (tier) {
            case UNCOMMON -> "Uncommon";
            case RARE -> "Rare";
            case EPIC -> "Epic";
            case LEGENDARY -> "Legendary";
            case UNIQUE -> "Unique";
            default -> "Common";
        };
    }

    public static ChatFormatting getTierColour(int tier) {
        if (tier > MAX_NAMED_TIER) {
            return ChatFormatting.DARK_PURPLE;
        }
        return switch (tier) {
            case UNCOMMON -> ChatFormatting.GREEN;
            case RARE -> ChatFormatting.BLUE;
            case EPIC -> ChatFormatting.LIGHT_PURPLE;
            case LEGENDARY -> ChatFormatting.GOLD;
            case UNIQUE -> ChatFormatting.RED;
            default -> ChatFormatting.WHITE;
        };
    }

    public static Component getTierComponent(int tier) {
        return Component.literal(getTierName(tier)).withStyle(getTierColour(tier));
    }

    /**
     * The rank the Imperial Code of Satisfaction would file this tier under.
     */
    public static Component getClearance(int tier) {
        if (tier > MAX_NAMED_TIER) {
            // Past Sovereign the Code has no filing for you.
            return Component.translatable("elysium.clearance.beyond")
                    .withStyle(ChatFormatting.DARK_PURPLE);
        }
        String key = switch (tier) {
            case UNCOMMON -> "petitioner";
            case RARE -> "sanctioned";
            case EPIC -> "codified";
            case LEGENDARY -> "imperial";
            case UNIQUE -> "sovereign";
            default -> "unranked";
        };
        return Component.translatable("elysium.clearance." + key)
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    /**
     * How large an elemental advantage a piece of this tier confers.
     *
     * The archive's own numbers anchor this: the Legendary Singularity Lance
     * reads +30%, the Epic Neural Cascade Rifle +20%.
     */
    public static float getAdvantage(int tier) {
        if (tier > MAX_NAMED_TIER) {
            // Above Sovereign the advantage keeps climbing but along a curve
            // that approaches +100% without reaching it, so an arbitrarily
            // ascended weapon is devastating and never divides by nothing.
            int beyond = tier - MAX_NAMED_TIER;
            return 0.40F + 0.60F * beyond / (beyond + 12.0F);
        }
        return switch (tier) {
            case UNCOMMON -> 0.10F;
            case RARE -> 0.15F;
            case EPIC -> 0.20F;
            case LEGENDARY -> 0.30F;
            case UNIQUE -> 0.40F;
            default -> 0.05F;
        };
    }

    /**
     * Reforge quality scales with tier. Previously this read
     * {@code Rarity#ordinal()}, which silently broke once the custom rarities
     * were gone — the tier is the real source of truth.
     */
    public static int getTierMultiplier(int tier) {
        return Math.max(1, tier + 1);
    }

    /**
     * The character level needed to wear or wield a piece of this tier.
     *
     * Five levels per tier, forever. An ascended piece is always worth more
     * than the level it costs you to use it, but you cannot ascend your way
     * out of having to play the character.
     */
    public static int getRequiredLevel(int tier) {
        return Math.max(0, tier) * 5;
    }

    /**
     * How much the psionic affix on a piece is multiplied by, once tier has
     * pushed past the named range.
     *
     * The affix roll itself is a 0..1 interpolation and cannot express a tier
     * of 40. This is the term that lets it.
     *
     * It used to be its own curve — a flat +15% a tier, linear, unrelated to
     * anything else that grew with tier. It now defers to
     * {@link ElysiumAscension}, so the affix keeps pace with the armour, damage
     * and stats on the same piece instead of falling behind them by a widening
     * margin. Measured from the top of the named range, because the
     * interpolation already covers everything below it and multiplying twice
     * over that stretch would pay for those tiers a second time.
     */
    public static float getAscendantScale(int tier) {
        return ElysiumAscension.scale(Math.max(0, tier - MAX_NAMED_TIER));
    }
}
