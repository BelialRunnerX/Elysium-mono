package com.elysium.trinkets.trinket;

import com.elysium.lib.character.ElysiumPassive;
import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.element.ElysiumElements;
import com.elysium.lib.item.ElysiumAscension;
import com.elysium.lib.trinket.ElysiumTrinket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.function.IntFunction;

/**
 * The sixteen crafted trinkets.
 *
 * <h2>These are the numbers, and the numbers have no ceiling</h2>
 *
 * Where the twenty-four uniques change a rule, these change an amount — and an
 * amount is exactly the thing an ascension tier can multiply. So each of these
 * is built once per tier and grows on {@link ElysiumAscension}, the same curve
 * armour and weapons grow on. A trinket ascended to tier 8 is worth what a
 * chestplate ascended to tier 8 is worth, because it is the same function.
 *
 * <h2>Two shapes, and why some effects cannot use the first</h2>
 *
 * A multiplier ({@link #multiplier}) starts at 1.0 and is multiplied by the
 * ascension scale — damage, regeneration, experience. A share
 * ({@link #share}) starts at a fraction and approaches a ceiling it never
 * reaches, because a dodge chance or a lifesteal share that grew geometrically
 * would pass 1.0 at about tier 12 and mean "every blow" forever after. The
 * library combines shares proportionally for the same reason; this keeps a
 * single trinket from doing on its own what the combinator exists to prevent.
 *
 * That is not a cap bolted on. {@code 1 - (1-base)^scale} is the same
 * arithmetic as wearing the trinket {@code scale} times over, which is what
 * ascending it should mean.
 */
public final class CraftedTrinkets {

    private CraftedTrinkets() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("elysiumtrinkets", path);
    }

    /** The tier a crafted trinket is made at. Everything above is ascension. */
    public static final int CRAFTED_TIER = 2;

    private static ElysiumTrinket crafted(String path, ElysiumElement element, String slot,
                                          int levelRequirement,
                                          IntFunction<ElysiumPassive> perTier) {
        return ElysiumTrinket.register(id(path), element, slot, levelRequirement,
                TrinketPassive.perTier(perTier));
    }

    /**
     * A quantity that starts at {@code base} and grows geometrically.
     *
     * For things where more is simply more: damage dealt, damage taken,
     * experience earned. Tier 0 gives exactly {@code base}, so a freshly
     * crafted trinket is worth what it says on the recipe.
     */
    private static float multiplier(float base, int tier) {
        return 1.0F + (base - 1.0F) * ElysiumAscension.scale(tier);
    }

    /**
     * A share that grows towards 1.0 without arriving.
     *
     * Exactly equivalent to owning {@code scale(tier)} copies of the same
     * share, combined the way the library combines shares from different
     * sources. A geometric share would cross 1.0 and stay there.
     */
    private static float share(float base, int tier) {
        double stacked = 1.0D - Math.pow(1.0D - base, ElysiumAscension.scale(tier));
        return (float) Math.min(0.95D, stacked);
    }

    // ==================================================================
    // Offence
    // ==================================================================

    public static final ElysiumTrinket AETHERIUM_BAND = crafted(
            "aetherium_band", ElysiumElements.KINETIC, "ring", 10,
            tier -> new TrinketPassive("aetherium_band") {
                private final float scale = multiplier(1.15F, tier);

                @Override
                public float attackScale(Player attacker, LivingEntity victim) {
                    return scale;
                }

                @Override
                public Component getDescription() {
                    return scaledDescription(id(), percent(scale - 1.0F));
                }
            });

    public static final ElysiumTrinket EXECUTIONERS_GRIP = crafted(
            "executioners_grip", ElysiumElements.VOID, "hands", 20,
            tier -> new TrinketPassive("executioners_grip") {
                private final float crit = 1.5F + 0.35F * ElysiumAscension.scale(tier);

                @Override
                public float critMultiplier(Player attacker) {
                    return crit;
                }

                @Override
                public Component getDescription() {
                    return scaledDescription(id(), String.format("%.2f", crit));
                }
            });

    public static final ElysiumTrinket BLOODLET_RING = crafted(
            "bloodlet_ring", ElysiumElements.VOID, "ring", 20,
            tier -> new TrinketPassive("bloodlet_ring") {
                private final float amount = share(0.06F, tier);

                @Override
                public float lifestealShare(Player attacker, LivingEntity victim) {
                    return amount;
                }

                @Override
                public Component getDescription() {
                    return scaledDescription(id(), percent(amount));
                }
            });

    public static final ElysiumTrinket VOIDGLASS_PENDANT = crafted(
            "voidglass_pendant", ElysiumElements.VOID, "necklace", 15,
            tier -> new TrinketPassive("voidglass_pendant") {
                private final float scale = multiplier(1.20F, tier);

                @Override
                public float psionicScale(Player player) {
                    return scale;
                }

                @Override
                public Component getDescription() {
                    return scaledDescription(id(), percent(scale - 1.0F));
                }
            });

    // ==================================================================
    // Defence
    // ==================================================================

    public static final ElysiumTrinket NEUTRONIUM_BAND = crafted(
            "neutronium_band", ElysiumElement.NONE, "ring", 10,
            tier -> new TrinketPassive("neutronium_band") {
                // Damage *taken*, so the useful direction is downward and the
                // multiplier helper - which grows upward - is the wrong tool.
                // A share of the blow removed is the same idea pointing the
                // right way, and it inherits the ceiling for free.
                private final float removed = share(0.08F, tier);

                @Override
                public float defenceScale(Player defender, DamageSource source) {
                    return 1.0F - removed;
                }

                @Override
                public Component getDescription() {
                    return scaledDescription(id(), percent(removed));
                }
            });

    public static final ElysiumTrinket KINETIC_SPUR = crafted(
            "kinetic_spur", ElysiumElements.KINETIC, "belt", 15,
            tier -> new TrinketPassive("kinetic_spur") {
                private final float chance = share(0.05F, tier);

                @Override
                public float dodgeChance(Player defender, DamageSource source) {
                    return chance;
                }

                @Override
                public Component getDescription() {
                    return scaledDescription(id(), percent(chance));
                }
            });

    public static final ElysiumTrinket THORNPLATE = crafted(
            "thornplate", ElysiumElements.KINETIC, "back", 15,
            tier -> new TrinketPassive("thornplate") {
                private final float amount = share(0.10F, tier);

                @Override
                public float reflectShare(Player defender) {
                    return amount;
                }

                @Override
                public Component getDescription() {
                    return scaledDescription(id(), percent(amount));
                }
            });

    public static final ElysiumTrinket WARDENS_GORGET = crafted(
            "wardens_gorget", ElysiumElements.NEURAL, "necklace", 20,
            tier -> new TrinketPassive("wardens_gorget") {
                private final float scale = multiplier(1.25F, tier);

                @Override
                public float shieldScale(Player player) {
                    return scale;
                }

                @Override
                public Component getDescription() {
                    return scaledDescription(id(), percent(scale - 1.0F));
                }
            });

    public static final ElysiumTrinket DIMENSIONAL_ANCHOR = crafted(
            "dimensional_anchor", ElysiumElements.DIMENSIONAL, "belt", 5,
            tier -> new TrinketPassive("dimensional_anchor") {
                private final float ignored = 3.0F * ElysiumAscension.scale(tier);

                @Override
                public float fallDistanceIgnored(Player player) {
                    return ignored;
                }

                @Override
                public Component getDescription() {
                    return scaledDescription(id(), String.valueOf(Math.round(ignored)));
                }
            });

    // ==================================================================
    // Body
    // ==================================================================

    public static final ElysiumTrinket PLASMA_CORD = crafted(
            "plasma_cord", ElysiumElements.PLASMA, "belt", 10,
            tier -> new TrinketPassive("plasma_cord") {
                private final float scale = multiplier(1.30F, tier);

                @Override
                public float regenScale(Player player) {
                    return scale;
                }

                @Override
                public Component getDescription() {
                    return scaledDescription(id(), percent(scale - 1.0F));
                }
            });

    public static final ElysiumTrinket NEURAL_FILAMENT = crafted(
            "neural_filament", ElysiumElements.NEURAL, "head", 10,
            tier -> new TrinketPassive("neural_filament") {
                private final float scale = multiplier(1.20F, tier);

                @Override
                public float xpScale(Player player) {
                    return scale;
                }

                @Override
                public Component getDescription() {
                    return scaledDescription(id(), percent(scale - 1.0F));
                }
            });

    // ==================================================================
    // Standing
    // ==================================================================

    public static final ElysiumTrinket FAVORED_SIGIL = crafted(
            "favored_sigil", ElysiumElements.NEURAL, "charm", 5,
            tier -> new TrinketPassive("favored_sigil") {
                private final float scale = multiplier(1.25F, tier);

                @Override
                public float favorScale(Player player) {
                    return scale;
                }

                @Override
                public Component getDescription() {
                    return scaledDescription(id(), percent(scale - 1.0F));
                }
            });

    public static final ElysiumTrinket SHROUDED_SIGIL = crafted(
            "shrouded_sigil", ElysiumElements.DIMENSIONAL, "charm", 5,
            tier -> new TrinketPassive("shrouded_sigil") {
                private final float removed = share(0.15F, tier);

                @Override
                public float suspicionScale(Player player) {
                    return 1.0F - removed;
                }

                @Override
                public Component getDescription() {
                    return scaledDescription(id(), percent(removed));
                }
            });

    // ==================================================================
    // Work
    // ==================================================================

    public static final ElysiumTrinket PROSPECT_CHARM = crafted(
            "prospect_charm", ElysiumElements.KINETIC, "charm", 10,
            tier -> new TrinketPassive("prospect_charm") {
                private final float chance = share(0.08F, tier);

                @Override
                public float extraDropChance(Player player) {
                    return chance;
                }

                @Override
                public Component getDescription() {
                    return scaledDescription(id(), percent(chance));
                }
            });

    public static final ElysiumTrinket ARTIFICERS_LOUPE = crafted(
            "artificers_loupe", ElysiumElements.PLASMA, "head", 15,
            tier -> new TrinketPassive("artificers_loupe") {
                private final float scale = multiplier(1.20F, tier);

                @Override
                public float reforgeScale(Player player) {
                    return scale;
                }

                @Override
                public Component getDescription() {
                    return scaledDescription(id(), percent(scale - 1.0F));
                }
            });

    public static final ElysiumTrinket MINERS_RIG = crafted(
            "miners_rig", ElysiumElement.NONE, "hands", 10,
            tier -> new TrinketPassive("miners_rig") {
                // A boolean hook cannot be scaled, so what ascension buys here
                // is the *chance* of the answer being yes - rolled per call,
                // which is what "one break in five costs no durability" means.
                private final float chance = share(0.20F, tier);

                @Override
                public boolean savesDurability(Player player) {
                    return player.getRandom().nextFloat() < chance;
                }

                @Override
                public boolean doublesOre(Player player) {
                    // Rolled separately from the durability save: one rig, two
                    // independent chances, rather than one roll deciding both
                    // and correlating them for no reason a player could name.
                    return player.getRandom().nextFloat() < chance * 0.6F;
                }

                @Override
                public Component getDescription() {
                    return scaledDescription(id(), percent(chance));
                }
            });

    /** Every one of them, in registration order, for the item registry. */
    public static final ElysiumTrinket[] ALL = {
            AETHERIUM_BAND, EXECUTIONERS_GRIP, BLOODLET_RING, VOIDGLASS_PENDANT,
            NEUTRONIUM_BAND, KINETIC_SPUR, THORNPLATE, WARDENS_GORGET, DIMENSIONAL_ANCHOR,
            PLASMA_CORD, NEURAL_FILAMENT,
            FAVORED_SIGIL, SHROUDED_SIGIL,
            PROSPECT_CHARM, ARTIFICERS_LOUPE, MINERS_RIG,
    };
}
