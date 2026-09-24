package com.elysium.lib.item;

import com.elysium.lib.affix.ElysiumAffix;
import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.registry.ElysiumRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A rune: something socketed into gear that changes what the gear does.
 *
 * <h2>Two halves, either optional</h2>
 *
 * A rune can grant an <b>affix</b> — a flat attribute bonus, applied by the
 * item's own attribute component — and it can carry an <b>effect</b>, which is
 * behaviour running on a tick. Most runes have one or the other:
 *
 * <ul>
 *   <li>Elemental runes are mostly affixes. "+1.5 attack damage" is an
 *       attribute, and attributes are what the game already knows how to
 *       apply.</li>
 *   <li>Utility runes are mostly effects. A dodge chance, a recharging shield
 *       and steady regeneration are not attributes and never will be; there is
 *       no vanilla number to add to.</li>
 * </ul>
 *
 * A rune with neither is legal and does nothing, which is occasionally what you
 * want while building one.
 *
 * <h2>Alignment</h2>
 *
 * A rune's element is what alignment is measured against. Socketed into gear of
 * its own element, its affix is multiplied by
 * {@link ElysiumSockets#ALIGNED_MULTIPLIER} and its effect runs one amplifier
 * higher. A rune with {@link ElysiumElement#NONE} is never aligned and never
 * penalised — the flat option you take when you cannot get a match.
 *
 * <h2>Registering one</h2>
 *
 * <pre>{@code
 * public static final ElysiumRune TIDECALL = ElysiumRune.builder(
 *             ResourceLocation.fromNamespaceAndPath("mymod", "tidecall"))
 *         .element(MyElements.TIDE)
 *         .affix(new ElysiumAffix("tidecall", Attributes.MOVEMENT_SPEED,
 *                 AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.05F, 0.05F))
 *         .effect((player, stack, aligned) -> player.heal(aligned ? 1.0F : 0.5F))
 *         .register();
 * }</pre>
 *
 * The rune is the definition; the <em>item</em> a player holds is registered
 * separately by whichever mod wants one, and points at this.
 */
public final class ElysiumRune {

    /** Every rune in the game. Add-ons register here during construction. */
    public static final ElysiumRegistry<ElysiumRune> REGISTRY = new ElysiumRegistry<>("rune");

    /**
     * Behaviour a rune runs while its gear is equipped.
     *
     * Called on the server, on the regeneration cadence, once per socketed
     * copy — so a rune in four pieces runs four times, which is what makes
     * stacking them meaningful.
     *
     * @param aligned whether this copy is in gear of the rune's own element
     */
    @FunctionalInterface
    public interface RuneEffect {
        void apply(Player player, ItemStack gear, boolean aligned);
    }

    /**
     * An effect that knows its rune's tier.
     *
     * {@code scale} is N for a tier-N rune ({@link ElysiumRuneTiers}), and an
     * effect is expected to do N times what it does at tier 1. Elysium's own
     * runes are written this way.
     *
     * The three-argument {@link RuneEffect} still works, so an add-on written
     * before tiers keeps compiling and keeps working — its effect runs as it
     * always did, and only its effect: the rune's attribute bonus, dodge and
     * heat reduction still scale with tier, because those are numbers this
     * library holds rather than code the add-on wrote.
     */
    @FunctionalInterface
    public interface TieredRuneEffect {
        void apply(Player player, ItemStack gear, boolean aligned, float scale);
    }

    private final String translationKey;
    private final ElysiumElement element;
    private final ElysiumAffix affix;
    private final TieredRuneEffect effect;
    private final float dodgeBonus;
    private final float heatReduction;
    private final boolean utility;

    private ElysiumRune(String translationKey, ElysiumElement element, ElysiumAffix affix,
                        TieredRuneEffect effect, float dodgeBonus, float heatReduction,
                        boolean utility) {
        this.translationKey = translationKey;
        this.element = element;
        this.affix = affix;
        this.effect = effect;
        this.dodgeBonus = dodgeBonus;
        this.heatReduction = heatReduction;
        this.utility = utility;
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private ElysiumElement element = ElysiumElement.NONE;
        private ElysiumAffix affix;
        private TieredRuneEffect effect;
        private float dodgeBonus;
        private float heatReduction;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        /** The element this rune aligns with. Omit for a utility rune. */
        public Builder element(ElysiumElement element) {
            this.element = element == null ? ElysiumElement.NONE : element;
            return this;
        }

        /** A flat attribute bonus applied through the item. */
        public Builder affix(ElysiumAffix affix) {
            this.affix = affix;
            return this;
        }

        /** Behaviour run per equipped copy, on the server tick. */
        public Builder effect(RuneEffect effect) {
            this.effect = effect == null ? null
                    : (player, gear, aligned, scale) -> effect.apply(player, gear, aligned);
            return this;
        }

        /** An effect that scales with the rune's tier. */
        public Builder effect(TieredRuneEffect effect) {
            this.effect = effect;
            return this;
        }

        /**
         * A flat chance, per socketed copy, of avoiding a blow outright.
         *
         * Data rather than behaviour, because dodging happens inside the
         * engine's damage pipeline where a callback would have to be able to
         * cancel the event. The engine sums this across every socketed rune.
         */
        public Builder dodgeBonus(float chance) {
            this.dodgeBonus = chance;
            return this;
        }

        /** A flat reduction, per copy, against fire and explosions. */
        public Builder heatReduction(float fraction) {
            this.heatReduction = fraction;
            return this;
        }

        public ElysiumRune register() {
            String bare = id.getNamespace().equals("elysium")
                    || id.getNamespace().equals("elysiumlib")
                    ? id.getPath()
                    : id.getNamespace() + "." + id.getPath();
            return REGISTRY.register(id, new ElysiumRune(
                    "elysium.rune." + bare, element, affix, effect,
                    dodgeBonus, heatReduction, !element.isElemental()));
        }
    }

    // ------------------------------------------------------------------

    public ElysiumElement getElement() {
        return element;
    }

    /** @return the attribute bonus, or null for a purely behavioural rune */
    @Nullable
    public ElysiumAffix getAffix() {
        return affix;
    }

    @Nullable
    public TieredRuneEffect getEffect() {
        return effect;
    }

    public float getDodgeBonus() {
        return dodgeBonus;
    }

    public float getHeatReduction() {
        return heatReduction;
    }

    /**
     * How many copies of this rune an entity is carrying, across armour and
     * the main hand.
     *
     * Runes are unique per piece, so four armour slots plus a held weapon caps
     * a single rune at five.
     */
    public static int countSocketed(LivingEntity entity, ElysiumRune rune) {
        int count = 0;
        for (ItemStack stack : entity.getArmorSlots()) {
            if (carries(stack, rune)) {
                count++;
            }
        }
        if (carries(entity.getMainHandItem(), rune)) {
            count++;
        }
        return count;
    }

    /** The old name for {@link #shareAcross}, which every percentage now goes through. */
    public static float sumAcross(LivingEntity entity,
                                  java.util.function.Function<ElysiumRune, Float> value) {
        return shareAcross(entity, value);
    }

    /**
     * A percentage every rune worn contributes to — dodge, heat reduction —
     * as one share: each rune's stat times its tier, added, then
     * stat / (stat + b). See {@link com.elysium.lib.stats.ElysiumShares}.
     *
     * One tier-1 rune is exactly its base; a tier-N rune is N times its stat;
     * and no number of them reaches 100%. This used to be a straight sum of
     * percentages, clamped at 60% where it was used.
     */
    public static float shareAcross(LivingEntity entity,
                                    java.util.function.Function<ElysiumRune, Float> value) {
        double odds = 0.0D;
        for (ItemStack stack : entity.getArmorSlots()) {
            odds += sumOn(stack, value);
        }
        odds += sumOn(entity.getMainHandItem(), value);
        return com.elysium.lib.stats.ElysiumShares.fromOdds(odds);
    }

    /** The stat, in odds units, one piece's runes contribute. Tier N counts N times. */
    private static double sumOn(ItemStack stack,
                                java.util.function.Function<ElysiumRune, Float> value) {
        ElysiumSocketable gear = ElysiumGear.of(stack);
        if (gear == null) {
            return 0.0F;
        }
        // Tier N counts N times: a tier-3 Reflex is three Reflexes' dodge
        // stat. Nothing on the far side caps it.
        double odds = 0.0D;
        for (ElysiumSockets.Socketed socketed : ElysiumSockets.socketed(stack)) {
            odds += com.elysium.lib.stats.ElysiumShares.odds(value.apply(socketed.rune()))
                    * socketed.scale() * com.elysium.lib.stats.ElysiumShares.PACE;
        }
        return odds;
    }

    /**
     * How much of one rune an entity is carrying, tier-weighted: a tier-1 and
     * a tier-3 copy are four. What a rune that stacks across pieces — the
     * Barrier — scales its capacity by.
     */
    public static float tierSum(LivingEntity entity, ElysiumRune rune) {
        float total = 0.0F;
        for (ItemStack stack : entity.getArmorSlots()) {
            total += tierSumOn(stack, rune);
        }
        return total + tierSumOn(entity.getMainHandItem(), rune);
    }

    private static float tierSumOn(ItemStack stack, ElysiumRune rune) {
        if (ElysiumGear.of(stack) == null) {
            return 0.0F;
        }
        float total = 0.0F;
        for (ElysiumSockets.Socketed socketed : ElysiumSockets.socketed(stack)) {
            if (socketed.rune() == rune) {
                total += socketed.scale();
            }
        }
        return total;
    }

    private static boolean carries(ItemStack stack, ElysiumRune rune) {
        ElysiumSocketable gear = ElysiumGear.of(stack);
        return gear != null && gear.getSocketedRunes(stack).contains(rune);
    }

    /** A rune with no element: never aligned, never penalised. */
    public boolean isUtility() {
        return utility;
    }

    public Component getEffectLine() {
        return Component.translatable(translationKey + ".effect")
                .withStyle(utility ? ChatFormatting.AQUA : ChatFormatting.DARK_AQUA);
    }

    public ResourceLocation getId() {
        return REGISTRY.idOf(this);
    }

    /** How a socketed rune is written into an item's gear data. */
    public String getSerialisedName() {
        ResourceLocation id = getId();
        return id == null ? "" : id.toString();
    }

    @Override
    public String toString() {
        return getSerialisedName();
    }
}
