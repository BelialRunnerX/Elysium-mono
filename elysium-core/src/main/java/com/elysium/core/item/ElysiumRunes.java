package com.elysium.core.item;

import com.elysium.core.Elysium;
import com.elysium.lib.affix.ElysiumAffix;
import com.elysium.lib.element.ElysiumElements;
import com.elysium.lib.item.ElysiumRune;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * The nine runes, and what each one does.
 *
 * Two families, both taken from the Sleeping Empire equipment archive:
 * elemental runes tied to the five affinities, and utility runes that do
 * something a raw attribute cannot.
 *
 * <h2>Where a rune's behaviour lives</h2>
 *
 * On the rune. This used to be split three ways — an enum constant here, an
 * affix entry in a table over there, and a {@code switch} in the tick handler
 * for the rest — which meant a rune added by another mod could be registered
 * and then do nothing, because the code that decided what runes did had never
 * heard of it. Each registration below now carries its own affix, its own
 * effect, or both.
 *
 * The elemental runes are mostly affixes, because "+1.5 attack damage" is an
 * attribute and attributes are what the game already knows how to apply; their
 * effects are the conditional potion bursts. The utility runes are mostly
 * effects, because a dodge chance and a recharging shield are not attributes
 * and never will be.
 */
public final class ElysiumRunes {

    private ElysiumRunes() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Elysium.MODID, path);
    }

    /** Effects are short and refreshed on a cadence rather than every tick. */
    private static final int DURATION = 60;

    /** Health restored per Stabilizer rune, per refresh. */
    private static final float STABILIZER_HEAL = 0.5F;

    /** Absorption granted per Barrier rune, and how fast it rebuilds. */
    private static final float BARRIER_CAPACITY = 4.0F;
    private static final float BARRIER_RECHARGE = 1.0F;

    /** Dodge chance per Reflex rune — the archive's "+5% Dodge Chance". */
    public static final float REFLEX_DODGE = 0.05F;

    /** Damage reduction per Plasma Core rune — the archive's "+12%". */
    public static final float PLASMA_CORE_REDUCTION = 0.12F;

    private static ElysiumAffix affix(String name, Holder<Attribute> attribute,
                                      AttributeModifier.Operation operation, float value) {
        return new ElysiumAffix(name, attribute, operation, value, value);
    }

    // ------------------------------------------------------------------
    // Elemental
    // ------------------------------------------------------------------

    /** Voidward: toughness, and resistance when badly hurt. */
    public static final ElysiumRune VOIDWARD = ElysiumRune.builder(id("voidward"))
            .element(ElysiumElements.VOID)
            .affix(affix("rune_voidward", Attributes.ARMOR_TOUGHNESS,
                    AttributeModifier.Operation.ADD_VALUE, 2.0F))
            .effect((player, gear, aligned) -> {
                if (player.getHealth() < player.getMaxHealth() * 0.4F) {
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                            DURATION, aligned ? 1 : 0, false, false));
                }
            })
            .register();

    /** Plasmaforge: raw damage, and strength while healthy. */
    public static final ElysiumRune PLASMAFORGE = ElysiumRune.builder(id("plasmaforge"))
            .element(ElysiumElements.PLASMA)
            .affix(affix("rune_plasmaforge", Attributes.ATTACK_DAMAGE,
                    AttributeModifier.Operation.ADD_VALUE, 1.5F))
            .effect((player, gear, aligned) -> {
                if (player.getHealth() > player.getMaxHealth() * 0.7F) {
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,
                            DURATION, aligned ? 1 : 0, false, false));
                }
            })
            .register();

    /** Neuralspike: attack speed, and haste. */
    public static final ElysiumRune NEURALSPIKE = ElysiumRune.builder(id("neuralspike"))
            .element(ElysiumElements.NEURAL)
            .affix(affix("rune_neuralspike", Attributes.ATTACK_SPEED,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.15F))
            .effect((player, gear, aligned) -> player.addEffect(new MobEffectInstance(
                    MobEffects.DIG_SPEED, DURATION, aligned ? 1 : 0, false, false)))
            .register();

    /** Dimensionalshift: movement, and a soft landing once a fall gets dangerous. */
    public static final ElysiumRune DIMENSIONALSHIFT = ElysiumRune.builder(id("dimensionalshift"))
            .element(ElysiumElements.DIMENSIONAL)
            .affix(affix("rune_dimensionalshift", Attributes.MOVEMENT_SPEED,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.08F))
            .effect((player, gear, aligned) -> {
                if (player.fallDistance > 2.5F) {
                    player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,
                            DURATION, aligned ? 1 : 0, false, false));
                }
            })
            .register();

    /** Kineticsurge: immovability, and a jump. */
    public static final ElysiumRune KINETICSURGE = ElysiumRune.builder(id("kineticsurge"))
            .element(ElysiumElements.KINETIC)
            .affix(affix("rune_kineticsurge", Attributes.KNOCKBACK_RESISTANCE,
                    AttributeModifier.Operation.ADD_VALUE, 0.10F))
            .effect((player, gear, aligned) -> player.addEffect(new MobEffectInstance(
                    MobEffects.JUMP, DURATION, aligned ? 1 : 0, false, false)))
            .register();

    // ------------------------------------------------------------------
    // Utility — straight from the equipment archive
    // ------------------------------------------------------------------

    /**
     * Stabilizer: "+10 Health Regeneration".
     *
     * Steady regeneration that does not consume hunger the way vanilla's does.
     * Never aligned and never penalised, like every utility rune — it is the
     * flat option you take when you cannot get a match.
     */
    public static final ElysiumRune STABILIZER = ElysiumRune.builder(id("stabilizer"))
            .effect((player, gear, aligned) -> {
                if (player.getHealth() < player.getMaxHealth()) {
                    player.heal(STABILIZER_HEAL);
                }
            })
            .register();

    /**
     * Reflex: "+5% Dodge Chance".
     *
     * Data rather than behaviour, because dodging happens inside the damage
     * pipeline where a callback would need to be able to cancel the event.
     */
    public static final ElysiumRune REFLEX = ElysiumRune.builder(id("reflex"))
            .dodgeBonus(REFLEX_DODGE)
            .register();

    /**
     * Barrier: "+25 Shield Capacity" — a shield that rebuilds while it is not
     * being spent.
     *
     * Only ever raises absorption, and only up to its own cap, so it cannot
     * quietly delete a bigger shield from a golden apple or a totem.
     */
    public static final ElysiumRune BARRIER = ElysiumRune.builder(id("barrier"))
            .effect(ElysiumRunes::rechargeBarrier)
            .register();

    /** Plasma Core: "+12% Plasma Damage Reduction". */
    public static final ElysiumRune PLASMA_CORE = ElysiumRune.builder(id("plasma_core"))
            .heatReduction(PLASMA_CORE_REDUCTION)
            .register();

    /**
     * One socketed Barrier's worth of shield.
     *
     * Each copy runs this in turn, so the cap climbs with the number socketed
     * while the recharge stays honest: the first copy fills toward 4, the
     * second toward 8, and a player wearing four of them rebuilds four points a
     * tick up to sixteen.
     */
    private static void rechargeBarrier(Player player, net.minecraft.world.item.ItemStack gear,
                                        boolean aligned) {
        int copies = ElysiumRune.countSocketed(player, BARRIER);
        float cap = BARRIER_CAPACITY * copies;
        float current = player.getAbsorptionAmount();
        if (current < cap) {
            player.setAbsorptionAmount(Math.min(cap, current + BARRIER_RECHARGE));
        }
    }

    /** Touching this class registers all nine. */
    public static void bootstrap() {
    }
}
