package com.elysium.trinkets.trinket;

import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.element.ElysiumElements;
import com.elysium.lib.standing.ElysiumStanding;
import com.elysium.lib.trinket.ElysiumTrinket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The twenty-four found trinkets.
 *
 * <h2>What makes one of these different from the crafted sixteen</h2>
 *
 * These change a <em>rule</em>. Not "+12% damage" — "your first blow of a fight
 * costs nothing", "falling never hurts you and always gets noticed", "ore pays
 * twice and experience pays less". There is nothing here for an ascension tier
 * to multiply, which is exactly why none of them ascends: a rule that is 30%
 * more true is not a thing.
 *
 * That is also why they are found rather than made. A rule is either interesting
 * or it is not, and a player who can craft one to order will craft the two that
 * suit their build and ignore the rest. Finding one is being handed a question —
 * is this worth a slot? — which is the whole point of the slot being scarce.
 *
 * <h2>Every one of them is a trade or a condition</h2>
 *
 * A trinket that is strictly better than an empty slot is not a decision, and
 * six slots of strictly-better is just a stat increase with extra steps. So each
 * of these either costs something (Prospector's Lens doubles ore and cuts
 * experience) or only applies sometimes (Widow's Thimble is free until the first
 * blow lands). The interesting ones do both.
 */
public final class UniqueTrinkets {

    private UniqueTrinkets() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("elysiumtrinkets", path);
    }

    /** Registers one unique. Level requirement, not tier: these do not ascend. */
    private static ElysiumTrinket unique(String path, ElysiumElement element, String slot,
                                         int levelRequirement, TrinketPassive passive) {
        return ElysiumTrinket.register(id(path), element, slot, levelRequirement,
                ElysiumTrinket.unique(passive));
    }

    // ==================================================================
    // Defence — when a blow lands, and when it does not
    // ==================================================================

    /** The first blow of any fight is free; nothing after it is. */
    public static final ElysiumTrinket WIDOWS_THIMBLE = unique(
            "widows_thimble", ElysiumElements.KINETIC, "ring", 5,
            new TrinketPassive("widows_thimble") {
                @Override
                public float defenceScale(Player defender, DamageSource source) {
                    // Full health is the only state a fight can start from, so
                    // this is "the opening blow" without tracking anything.
                    // It also means retreating to full heals the charge, which
                    // is a fair trade for it costing a slot.
                    return defender.getHealth() >= defender.getMaxHealth() ? 0.0F : 1.0F;
                }
            });

    /** Hurt badly, you mend fast. Comfortable, you barely mend at all. */
    public static final ElysiumTrinket CRACKED_RELIQUARY = unique(
            "cracked_reliquary", ElysiumElements.PLASMA, "necklace", 5,
            new TrinketPassive("cracked_reliquary") {
                @Override
                public float regenScale(Player player) {
                    return player.getHealth() < player.getMaxHealth() / 3.0F ? 3.0F : 0.4F;
                }
            });

    /** The closer to death, the harder to hit. */
    public static final ElysiumTrinket NINE_TENTHS_CHARM = unique(
            "nine_tenths_charm", ElysiumElements.DIMENSIONAL, "charm", 15,
            new TrinketPassive("nine_tenths_charm") {
                @Override
                public float dodgeChance(Player defender, DamageSource source) {
                    float missing = 1.0F - defender.getHealth()
                            / Math.max(1.0F, defender.getMaxHealth());
                    return missing * 0.35F;
                }
            });

    /** Crouched, you are half as easy to hurt and cannot chase anyone. */
    public static final ElysiumTrinket IRON_DISCIPLINE = unique(
            "iron_discipline", ElysiumElement.NONE, "belt", 10,
            new TrinketPassive("iron_discipline") {
                @Override
                public float defenceScale(Player defender, DamageSource source) {
                    return defender.isCrouching() ? 0.55F : 1.0F;
                }
            });

    /** A quarter of every blow goes back, but only while you are burning. */
    public static final ElysiumTrinket ASHEN_MANTLE = unique(
            "ashen_mantle", ElysiumElements.PLASMA, "back", 15,
            new TrinketPassive("ashen_mantle") {
                @Override
                public float reflectShare(Player defender) {
                    return defender.isOnFire() ? 0.30F : 0.0F;
                }
            });

    /** Doubles whatever reflection you already have, and adds none. */
    public static final ElysiumTrinket SPLINTBONE_FETISH = unique(
            "splintbone_fetish", ElysiumElements.VOID, "charm", 20,
            new TrinketPassive("splintbone_fetish") {
                @Override
                public float reflectMultiplier(Player defender) {
                    // Worthless on its own and excellent beside Retribution or
                    // a Thornplate: a trinket that is only good in a build is a
                    // trinket that makes a build.
                    return 2.0F;
                }
            });

    /** A shield twice the size, and half the mending under it. */
    public static final ElysiumTrinket EMPTY_RELIQUARY = unique(
            "empty_reliquary", ElysiumElements.NEURAL, "necklace", 20,
            new TrinketPassive("empty_reliquary") {
                @Override
                public float shieldScale(Player player) {
                    return 2.0F;
                }

                @Override
                public float regenScale(Player player) {
                    return 0.5F;
                }
            });

    // ==================================================================
    // Falling
    // ==================================================================

    /** Falling never hurts. The Empire notices every time it should have. */
    public static final ElysiumTrinket GRAVEBOUND_COIL = unique(
            "gravebound_coil", ElysiumElements.DIMENSIONAL, "belt", 10,
            new TrinketPassive("gravebound_coil") {
                @Override
                public float fallDamageScale(Player player) {
                    return 0.0F;
                }

                @Override
                public float suspicionScale(Player player) {
                    // People who cannot be killed by a drop are people the Code
                    // has questions about.
                    return 1.5F;
                }
            });

    // ==================================================================
    // Offence
    // ==================================================================

    /** Crits hit far harder; everything else hits softer. */
    public static final ElysiumTrinket RATCHET_GAUNTLET = unique(
            "ratchet_gauntlet", ElysiumElements.KINETIC, "hands", 15,
            new TrinketPassive("ratchet_gauntlet") {
                @Override
                public float critMultiplier(Player attacker) {
                    return 2.6F;
                }

                @Override
                public float attackScale(Player attacker, LivingEntity victim) {
                    return 0.85F;
                }
            });

    /** Strong against the untouched, ordinary against the wounded. */
    public static final ElysiumTrinket DUELLISTS_CUFF = unique(
            "duellists_cuff", ElysiumElements.KINETIC, "ring", 10,
            new TrinketPassive("duellists_cuff") {
                @Override
                public float attackScale(Player attacker, LivingEntity victim) {
                    return victim.getHealth() >= victim.getMaxHealth() ? 1.35F : 1.0F;
                }
            });

    /** Takes back from the wounded, and nothing from the whole. */
    public static final ElysiumTrinket HOLLOW_CHIME = unique(
            "hollow_chime", ElysiumElements.VOID, "necklace", 20,
            new TrinketPassive("hollow_chime") {
                @Override
                public float lifestealShare(Player attacker, LivingEntity victim) {
                    return victim.getHealth() < victim.getMaxHealth() * 0.5F ? 0.20F : 0.0F;
                }
            });

    /** A kill mends you, in proportion to what it took to make it. */
    public static final ElysiumTrinket CARRION_SIGNET = unique(
            "carrion_signet", ElysiumElements.VOID, "ring", 15,
            new TrinketPassive("carrion_signet") {
                @Override
                public void onKill(Player killer, LivingEntity victim) {
                    killer.heal(Math.min(6.0F, victim.getMaxHealth() * 0.10F));
                }
            });

    /** Being hurt badly enough steadies you for a moment. */
    public static final ElysiumTrinket PALE_TOURNIQUET = unique(
            "pale_tourniquet", ElysiumElements.PLASMA, "hands", 20,
            new TrinketPassive("pale_tourniquet") {
                @Override
                public void onDamaged(Player defender, DamageSource source, float amount) {
                    if (amount >= 6.0F) {
                        defender.addEffect(new MobEffectInstance(
                                MobEffects.DAMAGE_RESISTANCE, 60, 0, false, true));
                    }
                }
            });

    // ==================================================================
    // Standing — the Empire's attention, and what it is worth
    // ==================================================================

    /** The more wanted you are, the more the Unsworn will pay. */
    public static final ElysiumTrinket DEADMANS_LEDGER = unique(
            "deadmans_ledger", ElysiumElements.NEURAL, "charm", 25,
            new TrinketPassive("deadmans_ledger") {
                @Override
                public float favorScale(Player player) {
                    return ElysiumStanding.bandOf(ElysiumStanding.getSuspicion(player))
                            >= ElysiumStanding.BAND_HUNTED ? 2.0F : 1.0F;
                }
            });

    /** Both meters move at half speed. Nothing you do is very noticeable. */
    public static final ElysiumTrinket QUIET_HOURS = unique(
            "quiet_hours", ElysiumElement.NONE, "back", 1,
            new TrinketPassive("quiet_hours") {
                @Override
                public float suspicionScale(Player player) {
                    return 0.5F;
                }

                @Override
                public float favorScale(Player player) {
                    return 0.5F;
                }
            });

    /** Both meters move at half again. Everything you do is noticed. */
    public static final ElysiumTrinket UNSWORN_BELL = unique(
            "unsworn_bell", ElysiumElements.NEURAL, "necklace", 10,
            new TrinketPassive("unsworn_bell") {
                @Override
                public float suspicionScale(Player player) {
                    return 1.6F;
                }

                @Override
                public float favorScale(Player player) {
                    return 1.6F;
                }
            });

    /** What you have done is forgotten three times as fast. */
    public static final ElysiumTrinket AUDITORS_SEAL = unique(
            "auditors_seal", ElysiumElement.NONE, "charm", 15,
            new TrinketPassive("auditors_seal") {
                @Override
                public int decayRate(Player player) {
                    return 3;
                }
            });

    /** Nothing is ever forgotten. Both meters stay exactly where you put them. */
    public static final ElysiumTrinket LONG_MEMORY = unique(
            "long_memory", ElysiumElements.DIMENSIONAL, "charm", 25,
            new TrinketPassive("long_memory") {
                @Override
                public int decayRate(Player player) {
                    return 0;
                }
            });

    /** Every kill is filed, and the filing pays. */
    public static final ElysiumTrinket TITHE_BRACELET = unique(
            "tithe_bracelet", ElysiumElements.NEURAL, "ring", 20,
            new TrinketPassive("tithe_bracelet") {
                @Override
                public void onKill(Player killer, LivingEntity victim) {
                    ElysiumStanding.addFavor(killer, 1);
                }
            });

    /** Wanted enough, and the world starts dropping things twice. */
    public static final ElysiumTrinket DEBTORS_KNOT = unique(
            "debtors_knot", ElysiumElements.VOID, "charm", 25,
            new TrinketPassive("debtors_knot") {
                @Override
                public float extraDropChance(Player player) {
                    return ElysiumStanding.bandOf(ElysiumStanding.getSuspicion(player))
                            >= ElysiumStanding.BAND_HUNTED ? 0.35F : 0.0F;
                }
            });

    // ==================================================================
    // Work
    // ==================================================================

    /** Ore pays twice. You learn a good deal less doing it. */
    public static final ElysiumTrinket PROSPECTORS_LENS = unique(
            "prospectors_lens", ElysiumElements.KINETIC, "head", 10,
            new TrinketPassive("prospectors_lens") {
                @Override
                public boolean doublesOre(Player player) {
                    return true;
                }

                @Override
                public float xpScale(Player player) {
                    return 0.6F;
                }
            });

    /** Tools last, while you are being careful about it. */
    public static final ElysiumTrinket CARTOGRAPHERS_NAIL = unique(
            "cartographers_nail", ElysiumElement.NONE, "hands", 5,
            new TrinketPassive("cartographers_nail") {
                @Override
                public boolean savesDurability(Player player) {
                    return player.isCrouching();
                }
            });

    /** You learn faster and hit softer. */
    public static final ElysiumTrinket LONGSIGHT = unique(
            "longsight", ElysiumElements.NEURAL, "head", 15,
            new TrinketPassive("longsight") {
                @Override
                public float xpScale(Player player) {
                    return 1.6F;
                }

                @Override
                public float attackScale(Player attacker, LivingEntity victim) {
                    return 0.85F;
                }
            });

    /** A better eye at the forge, and a worse one everywhere else. */
    public static final ElysiumTrinket REFORGERS_LOUPE = unique(
            "reforgers_loupe", ElysiumElements.PLASMA, "head", 20,
            new TrinketPassive("reforgers_loupe") {
                @Override
                public float reforgeScale(Player player) {
                    return 1.75F;
                }

                @Override
                public float psionicScale(Player player) {
                    return 0.8F;
                }
            });

    /** Every one of them, in registration order, for the item registry. */
    public static final ElysiumTrinket[] ALL = {
            WIDOWS_THIMBLE, CRACKED_RELIQUARY, NINE_TENTHS_CHARM, IRON_DISCIPLINE,
            ASHEN_MANTLE, SPLINTBONE_FETISH, EMPTY_RELIQUARY,
            GRAVEBOUND_COIL,
            RATCHET_GAUNTLET, DUELLISTS_CUFF, HOLLOW_CHIME, CARRION_SIGNET, PALE_TOURNIQUET,
            DEADMANS_LEDGER, QUIET_HOURS, UNSWORN_BELL, AUDITORS_SEAL, LONG_MEMORY,
            TITHE_BRACELET, DEBTORS_KNOT,
            PROSPECTORS_LENS, CARTOGRAPHERS_NAIL, LONGSIGHT, REFORGERS_LOUPE,
    };
}
