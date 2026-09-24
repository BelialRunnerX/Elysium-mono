package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.item.ElysiumRarities;
import com.elysium.lib.item.ElysiumRune;
import com.elysium.lib.item.ElysiumGear;
import com.elysium.lib.item.ElysiumSocketable;
import com.elysium.lib.stats.ElysiumStats;
import com.elysium.lib.telegraph.ElysiumTelegraph;
import com.elysium.lib.telegraph.ElysiumWarnings;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.elysium.lib.combat.ElysiumCombatRules;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * The elemental counter matrix, plus the two utility runes that act on
 * incoming damage.
 *
 * Hooked to {@link LivingIncomingDamageEvent} rather than
 * {@code LivingDamageEvent.Pre} on purpose: this one fires before armour and
 * enchantment reduction, so an elemental multiplier here behaves like a change
 * to the blow itself rather than something bolted on after the defence
 * calculation.
 *
 * Order matters and is deliberate:
 *
 * <ol>
 *   <li><b>Dodge</b> — Reflexes, the Reflex rune, and any passive that adds to
 *       the same single chance. A dodge either happens or it does not, so it
 *       resolves first and short-circuits everything else.</li>
 *   <li><b>Plasma Core</b> — flat reduction against heat.</li>
 *   <li><b>The elemental matrix</b> — the attacker's advantage, then the
 *       defender's, both widened by the attacker's Intellect.</li>
 *   <li><b>Strength and the weapon multiplier</b> — the attacker's own base
 *       damage, multiplied by whatever they are holding.</li>
 *   <li><b>Critical hits</b> — Accuracy.</li>
 *   <li><b>Resilience</b> — proportional reduction, last of the modifiers, so
 *       it applies to the final figure rather than to an intermediate one.</li>
 *   <li><b>Retribution</b> — a share of the <em>arriving</em> blow is
 *       returned.</li>
 *   <li><b>Lifesteal</b> — a share of the blow that <em>landed</em> heals the
 *       attacker. The asymmetry with Retribution is deliberate: reflection
 *       answers for what was tried at you, lifesteal pays out on what you
 *       actually did.</li>
 *   <li><b>The moment</b> — passives are told what landed, after everything
 *       above has decided what that was.</li>
 * </ol>
 *
 * Resilience deliberately lands after the elemental matrix. A defender's
 * percentage reduction should answer the blow that is actually arriving,
 * including its elemental bonus, not the number it started as.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumCombatHandler {

    private ElysiumCombatHandler() {
    }

    /**
     * Re-entry guard for reflection.
     *
     * {@code hurt} fires this same event for the entity being reflected at, and
     * two players who both carry Retribution — or two Imperials, who reflect by
     * default — would otherwise volley one blow back and forth. Each round trip
     * shrinks by the square of the share so it does terminate, but "terminates
     * eventually" is not a property to leave a combat loop resting on: it means
     * dozens of nested event dispatches per hit, on the server thread, entirely
     * at the mercy of how large the shares are.
     *
     * A blow struck while a reflection is in flight skips this handler
     * entirely.
     *
     * Guarding only the reflection step was not enough, and the way it failed
     * is worth recording. {@code DamageSources.thorns(defender)} sets the
     * defender as both the causing <em>and</em> the direct entity, so the
     * reflected packet re-entered here and satisfied
     * {@link #isMelee}: the mod then treated it as a sword swing by the
     * defender and added their full Strength, their weapon multiplier, their
     * elemental advantage and a critical roll on top. A level-appropriate
     * Imperial holding a blade would have killed anything that touched them,
     * from a 0.3-damage reflection.
     */

    private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> false);

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || REFLECTING.get()) {
            return;
        }

        // --- 1. Dodge: avoid the blow outright ----------------------------
        // Runes contribute a flat chance each; the engine no longer knows or
        // cares which rune that is. A rune added by another mod that declares a
        // dodge bonus is counted here for free.
        float dodge = ElysiumRune.sumAcross(victim, ElysiumRune::getDodgeBonus);
        // The dodge_chance attribute (AA-6) is one more stat behind the same
        // single chance, and it is read off anything that carries it, not only
        // players: gear and affixes can give it to a mob.
        if (com.elysium.lib.attribute.ElysiumAttributeValues.has(victim, "dodge_chance")) {
            dodge = com.elysium.lib.stats.ElysiumShares.add(dodge,
                    (float) com.elysium.lib.attribute.ElysiumAttributeValues.chance(victim, "dodge_chance"));
        }
        if (victim instanceof Player defender) {
            // Runes, Reflexes and passives are three stats behind one chance:
            // added as stats, then stat / (stat + b) once — ElysiumShares.
            // One roll, not three, so a trinket raises the same chance a
            // tooltip can describe rather than granting a second dodge.
            dodge = com.elysium.lib.stats.ElysiumShares.add(dodge,
                    ElysiumStats.dodgeChance(defender),
                    ElysiumPassives.dodgeChance(defender, event.getSource()));
        }
        if (dodge > 0.0F && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // No cap: the chance comes off stat / (stat + b), which climbs
            // toward every blow missed without arriving.
            if (victim.getRandom().nextFloat() < dodge) {
                event.setCanceled(true);
                // "Something swung at you and missed" is a moment some effects
                // want, so it is reported — with a zero, because nothing landed.
                if (victim instanceof Player defender) {
                    ElysiumPassives.damaged(defender, event.getSource(), 0.0F);
                }
                return;
            }
        }

        float amount = event.getAmount();

        // --- 1b. The telegraph rule ---------------------------------------
        //
        // Nothing hits a player hard without warning them first. Enforced here
        // rather than in each mob's attack, and enforced for *every* attacker
        // rather than this mod's own, because a promise that only holds for
        // content this mod shipped is not a promise a player can rely on — the
        // whole value of "you will always see it coming" is that it is true of
        // the mob you have never met.
        //
        // Before the elemental matrix and before armour, so that what is capped
        // is the blow as it was swung. Capping afterwards would mean a player
        // in good armour could be hit unannounced for far more than one in
        // none, which is the wrong way round: armour should make a fair hit
        // survivable, not make an unfair one legal.
        if (victim instanceof Player && event.getSource().getEntity()
                instanceof LivingEntity swinging) {
            int announced = ElysiumWarnings.showingFor(swinging);
            float permitted = ElysiumTelegraph.allowed(amount, announced);
            if (permitted < amount) {
                amount = permitted;
            }
            // Spent either way. A mob that lands a blow has finished the swing
            // it announced, and a second hit has to be announced again —
            // otherwise one wind-up would licence every blow that followed it.
            ElysiumWarnings.spend(swinging);
        }

        // --- 2. Rune resistance to heat -----------------------------------
        if (isHeat(event.getSource())) {
            float reduction = ElysiumRune.shareAcross(victim, ElysiumRune::getHeatReduction);
            if (reduction > 0.0F) {
                // A share off stat / (stat + b), so below one by construction.
                // The floor stays anyway: it is not a cap on the stat but on
                // the arithmetic, and a negative factor here would be a hit
                // that heals.
                amount *= Math.max(0.0F, 1.0F - reduction);
            }
        }

        // --- 3. The elemental matrix --------------------------------------
        Player attacker = attackerOf(event.getSource());
        ItemStack held = heldWeapon(event.getSource());
        ElysiumSocketable weapon = ElysiumGear.weapon(held);
        ElysiumElement attacking = weapon != null ? weapon.getElement() : ElysiumElement.NONE;
        // Effective tier, not registered tier: an ascended weapon has to be
        // read the same way an ascended chestplate is, or the defensive side
        // of the matrix scales and the offensive side silently does not.
        int weaponTier = weapon != null ? weapon.getEffectiveTier(held) : 0;

        ArmourProfile defence = armourProfileOf(victim);

        // Intellect is psionic training: it widens whichever side of the
        // matchup you are on, attacking or defending.
        float attackPsi = attacker != null ? ElysiumStats.psionicScale(attacker) : 1.0F;
        float defendPsi = victim instanceof Player p ? ElysiumStats.psionicScale(p) : 1.0F;

        if (weapon != null && attacking.isStrongAgainst(defence.element())) {
            amount *= (1.0F + ElysiumRarities.getAdvantage(weaponTier) * attackPsi);
        } else if (defence.element().isStrongAgainst(attacking)) {
            // Wearing the answer to what is hitting you blunts it. Worth half
            // of what the attacker's advantage would have been, so offence
            // still beats defence on an even matchup.
            //
            // The advantage is a stat, uncapped; what it removes is
            // stat / (stat + 1). This was the stat itself, clamped at 90%.
            float blunt = ElysiumRarities.getAdvantage(defence.tier()) * 0.5F * defendPsi;
            amount *= (1.0F - com.elysium.lib.stats.ElysiumShares.fromOdds(
                    blunt * com.elysium.lib.stats.ElysiumShares.PACE));
        }

        // --- 4. Strength, multiplied by whatever is in hand ----------------
        if (attacker != null && isMelee(event.getSource(), attacker)) {
            amount += ElysiumStats.baseDamage(attacker) * multiplierOf(attacker);
            amount *= ElysiumPassives.attackScale(attacker, victim);
        }

        // --- 4b. Whose side you are on -------------------------------------
        //
        // A warrant sharpens a player against the Unsworn; a writ from the
        // other side against the Empire's own. Applied against the victim's
        // faction rather than flatly, so neither is a damage upgrade wearing a
        // faction's name — a warranted player fighting a creeper is an
        // ordinary player fighting a creeper.
        if (attacker != null) {
            amount *= com.elysium.lib.doctrine.ElysiumDoctrines
                    .damageMultiplier(attacker, victim);
        }

        // --- 5. Accuracy and the crit attributes ---------------------------
        //
        // One roll set, not two: Accuracy's chance, the passives' chance and
        // the crit_chance attribute are added as odds (ElysiumShares.add) and
        // rolled once through ElysiumCombatRules, which keeps rolling while the
        // stat buys more crits — each rarer and 15% softer than the last. The
        // crit multiplier is the larger of the passives' and the attribute's,
        // so a build that has both is not paid twice for the same hit.
        LivingEntity living = event.getSource().getEntity() instanceof LivingEntity hitter ? hitter : null;
        if (living != null && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            double odds = 0.0D;
            double critDamage = 1.0D;
            if (com.elysium.lib.attribute.ElysiumAttributeValues.has(living, "crit_chance")) {
                odds += com.elysium.lib.attribute.ElysiumAttributeValues.raw(living, "crit_chance");
                critDamage = com.elysium.lib.attribute.ElysiumAttributeValues.multiplier(living, "crit_damage");
            }
            if (attacker != null && isMelee(event.getSource(), attacker)) {
                odds += com.elysium.lib.stats.ElysiumShares.odds(ElysiumStats.critChance(attacker));
                critDamage = Math.max(critDamage, ElysiumPassives.critMultiplier(attacker));
            }
            amount *= ElysiumCombatRules.critMultiplier(odds, critDamage,
                    () -> living.getRandom().nextDouble());
        }

        // --- 6. Resilience -------------------------------------------------
        if (victim instanceof Player defender) {
            amount *= (1.0F - ElysiumStats.damageReduction(defender));
            amount *= ElysiumPassives.defenceScale(defender, event.getSource());
        }

        if (amount != event.getAmount()) {
            event.setAmount(Math.max(0.0F, amount));
        }

        float landed = Math.max(0.0F, amount);

        // --- 6b. What the blow did to the scenery --------------------------
        //
        // Measured on the swing rather than on what landed, and that is the
        // whole distinction: a blow that a player's armour turned aside was
        // still a blow that size, and the wall behind them does not have their
        // armour. Using the landed figure would mean the better equipped a
        // player was, the more solid the world became around them.
        //
        // Everything that stops this happening lives in ElysiumCover, which is
        // written as a list of reasons to do nothing.
        com.elysium.lib.scar.ElysiumCover.scar(
                victim.level(), victim, event.getAmount());

        // --- 7. Retribution ------------------------------------------------
        if (victim instanceof Player defender) {
            reflect(defender, event.getSource(), landed);
        }

        // --- 8. Lifesteal --------------------------------------------------
        //
        // On what landed, not on what was attempted — the opposite of
        // Retribution one step above, and the asymmetry is deliberate.
        // Reflection answers for what was tried at you; lifesteal pays out on
        // what you actually did, so the victim's armour reduces the healing
        // along with the damage.
        //
        // A reflected blow cannot feed a heal: REFLECTING is checked at the top
        // of this method and the reflection is dealt inside that guard, so this
        // line is never reached for one.
        LivingEntity thief = event.getSource().getEntity() instanceof LivingEntity hitter ? hitter : null;
        if (landed > 0.0F && thief != null) {
            float share = attacker != null ? ElysiumPassives.lifestealShare(attacker, victim) : 0.0F;
            if (com.elysium.lib.attribute.ElysiumAttributeValues.has(thief, "life_steal")) {
                share = com.elysium.lib.stats.ElysiumShares.add(share,
                        (float) com.elysium.lib.attribute.ElysiumAttributeValues.chance(thief, "life_steal"));
            }
            float healed = ElysiumCombatRules.lifeSteal(landed, share);
            if (healed > 0.0F) {
                thief.heal(healed);
            }
            // Overheal (AA-5): what the heal could not use becomes absorption,
            // filling toward the overheal share of max health and no further.
            double overheal = com.elysium.lib.attribute.ElysiumAttributeValues.has(thief, "overheal")
                    ? com.elysium.lib.attribute.ElysiumAttributeValues.chance(thief, "overheal") : 0.0D;
            if (overheal > 0.0D) {
                thief.setAbsorptionAmount(ElysiumCombatRules.overhealAbsorption(thief.getAbsorptionAmount(),
                        landed, thief.getMaxHealth(), overheal));
            }
        }

        // --- 9. The moment -------------------------------------------------
        if (victim instanceof Player defender) {
            ElysiumPassives.damaged(defender, event.getSource(), landed);
        }
    }

    /**
     * Sends a share of the blow back to whoever landed it.
     *
     * <b>The share is of the incoming figure, not the figure that survives
     * armour.</b> This event fires at the top of the damage pipeline, which is
     * what the elemental matrix wants but means a heavily armoured defender
     * reflects more than they took, and a defender who blocks with a shield
     * reflects a blow that never landed. Both are deliberate reads of "you are
     * answered for what was attempted", and both are the reason the share
     * curve tops out well below 1.0.
     *
     * Guarded three ways, because a reflection loop is the classic way to
     * freeze a server: it only fires for a living attacker, never for the
     * defender hitting themselves, and the returned damage is dealt with a
     * generic source so it cannot be reflected again by the other party.
     */
    private static void reflect(Player defender, DamageSource source, float amount) {
        float share = ElysiumPassives.totalReflectShare(defender);
        if (share <= 0.0F || amount <= 0.0F) {
            return;
        }
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == defender) {
            return;
        }
        // No ceiling here: totalReflectShare is bounded below 1.0 by the way
        // its sources combine, so a clamp would only ever be dead code hiding
        // the fact that the guarantee lives somewhere else.
        float returned = amount * share;
        if (returned < 0.1F) {
            return;
        }

        REFLECTING.set(true);
        try {
            attacker.hurt(defender.damageSources().thorns(defender), returned);
        } finally {
            // In a finally block because hurt() runs the whole damage pipeline,
            // every other mod's listeners included. One of them throwing must
            // not leave this flag set — that would silently disable reflection
            // for every player on the server until restart.
            REFLECTING.set(false);
        }
    }

    /**
     * True for a blow the attacker landed with their own body — which is what
     * Strength and Accuracy are allowed to modify. An arrow or a thrown item
     * carries its own damage and should not inherit the shooter's melee stats.
     */
    private static boolean isMelee(DamageSource source, Player attacker) {
        return source.getDirectEntity() == attacker;
    }

    private static Player attackerOf(DamageSource source) {
        return source.getEntity() instanceof Player player ? player : null;
    }

    /**
     * The weapon multiplier applied to the attacker's base damage.
     *
     * This is what makes Strength worth investing in: a sword multiplies it,
     * a hammer multiplies it more, and an empty hand does not multiply it at
     * all.
     */
    private static float multiplierOf(Player attacker) {
        ItemStack held = attacker.getMainHandItem();
        ElysiumSocketable gear = ElysiumGear.of(held);
        if (gear != null) {
            return gear.getDamageMultiplier();
        }
        return held.isEmpty() ? 0.5F : 1.0F;
    }

    // ------------------------------------------------------------------

    private static boolean isHeat(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION);
    }

    /**
     * The Elysium weapon behind a blow, if there is one. Reads the direct
     * entity's main hand, so an arrow or a thrown item does not inherit its
     * shooter's sword.
     *
     * Any held Elysium gear with an element counts, which is what makes the
     * hammer and the broadaxe real weapons rather than pickaxes that hurt —
     * they swing into the counter matrix like a blade does. Armour is excluded:
     * holding a chestplate is not wielding it.
     */
    private static ItemStack heldWeapon(DamageSource source) {
        Entity attacker = source.getDirectEntity();
        if (attacker instanceof LivingEntity living) {
            return living.getMainHandItem();
        }
        return ItemStack.EMPTY;
    }

    /** The element a defender is wearing, and the tier backing it. */
    private record ArmourProfile(ElysiumElement element, int tier) {
        static final ArmourProfile NONE = new ArmourProfile(ElysiumElement.NONE, 0);
    }

    /**
     * A mixed set has no single affinity, so the element worn on the most
     * pieces wins and its highest tier sets the strength. A two-two split falls
     * back to whichever the iteration reaches first — deliberately unhelpful,
     * because a player who wants the defensive matchup should commit to it.
     */
    private static ArmourProfile armourProfileOf(LivingEntity entity) {
        Map<ElysiumElement, Integer> counts = new HashMap<>();
        Map<ElysiumElement, Integer> tiers = new HashMap<>();

        for (ItemStack stack : entity.getArmorSlots()) {
            ElysiumSocketable armor = ElysiumGear.armour(stack);
            if (armor == null) {
                continue;
            }
            ElysiumElement element = armor.getElement();
            if (!element.isElemental()) {
                continue;
            }
            counts.merge(element, 1, Integer::sum);
            tiers.merge(element, armor.getEffectiveTier(stack), Math::max);
        }

        ElysiumElement best = ElysiumElement.NONE;
        int bestCount = 0;
        for (Map.Entry<ElysiumElement, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }

        if (best == ElysiumElement.NONE) {
            return ArmourProfile.NONE;
        }
        return new ArmourProfile(best, tiers.getOrDefault(best, 0));
    }

}
