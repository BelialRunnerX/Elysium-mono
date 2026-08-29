package com.elysium.core.character;

import com.elysium.core.Elysium;
import com.elysium.lib.character.ElysiumClass;
import com.elysium.lib.entity.ElysiumFaction;
import com.elysium.lib.stats.ElysiumStatBlock;
import com.elysium.lib.stats.ElysiumStats;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import static com.elysium.lib.stats.ElysiumStats.ACCURACY;
import static com.elysium.lib.stats.ElysiumStats.AGILITY;
import static com.elysium.lib.stats.ElysiumStats.FORTITUDE;
import static com.elysium.lib.stats.ElysiumStats.INTELLECT;
import static com.elysium.lib.stats.ElysiumStats.LUCK;
import static com.elysium.lib.stats.ElysiumStats.PRESENCE;
import static com.elysium.lib.stats.ElysiumStats.REFLEXES;
import static com.elysium.lib.stats.ElysiumStats.RESILIENCE;
import static com.elysium.lib.stats.ElysiumStats.RETRIBUTION;
import static com.elysium.lib.stats.ElysiumStats.STRENGTH;
import static com.elysium.lib.stats.ElysiumStats.VITALITY;
import static com.elysium.lib.stats.ElysiumStats.WILLPOWER;

/**
 * The nine classes, and what each one does.
 *
 * Race is biology; class is a job. Six of the nine are lifted from what the
 * Sleeping Empire archive says the Empire actually employs people to do —
 * Medical Regeneration, Fleet and Infrastructure Engineering, Cybernetic
 * Enhancement, the fleets, the trade apparatus — rather than from a fantasy
 * party sheet.
 *
 * <b>Every class contributes exactly 2 points of growth per level</b>, against
 * a race's 3: what you were born as should outweigh the job you took. An
 * earlier draft gave 3 as well, which quietly made the two equal and doubled
 * the documented growth rate, so {@code validate.py} now checks the total after
 * every edit.
 */
public final class ElysiumClasses {

    private ElysiumClasses() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Elysium.MODID, path);
    }

    // ------------------------------------------------------------------
    // Medicae — Medical Regeneration, in the field
    // ------------------------------------------------------------------

    /** How far a Triage Field reaches, in blocks. */
    private static final double TRIAGE_RANGE = 8.0D;

    /**
     * Triage Field: the only passive in the mod that touches another player,
     * which is the entire point of the class — every other role is a way of
     * being better at something yourself.
     *
     * Scales with the Medicae's own Vitality, so a healer who never invests is
     * a healer who barely heals.
     */
    private static final class TriageField extends CorePassive {
        private TriageField() {
            super("triage_field", ChatFormatting.RED);
        }

        @Override
        public float regenScale(Player player) {
            return 1.5F;
        }

        @Override
        public void onServerTick(Player medic) {
            float amount = ElysiumStats.regenPerTick(medic) * 0.75F;
            if (amount <= 0.0F) {
                return;
            }
            for (Player nearby : medic.level().getEntitiesOfClass(Player.class,
                    medic.getBoundingBox().inflate(TRIAGE_RANGE))) {
                if (nearby != medic && nearby.getHealth() < nearby.getMaxHealth()) {
                    nearby.heal(amount);
                }
            }
        }
    }

    public static final ElysiumClass MEDICAE = ElysiumClass.register(id("medicae"),
            ChatFormatting.RED,
            ElysiumStatBlock.of(VITALITY, 1, PRESENCE, 1),
            new TriageField());

    // ------------------------------------------------------------------
    // Factor — the trade apparatus
    // ------------------------------------------------------------------

    /**
     * Profiteer: a Factor does not fight better, they simply come away with
     * more.
     *
     * The engine combines this with the Luck stat rather than adding it, so a
     * Factor at high Luck is not pinned against a clamp — which is what made
     * the last stretch of the Luck curve worthless to exactly the class built
     * around it.
     */
    private static final class Profiteer extends CorePassive {
        private Profiteer() {
            super("profiteer", ChatFormatting.GOLD);
        }

        @Override
        public float extraDropChance(Player player) {
            return 0.25F;
        }
    }

    public static final ElysiumClass FACTOR = ElysiumClass.register(id("factor"),
            ChatFormatting.GOLD,
            ElysiumStatBlock.of(LUCK, 1, PRESENCE, 1),
            new Profiteer());

    // ------------------------------------------------------------------
    // Artificer — Fleet and Infrastructure Engineering
    // ------------------------------------------------------------------

    /** Field Repair: gear wears at two thirds the rate, and reforges better. */
    private static final class FieldRepair extends CorePassive {
        private FieldRepair() {
            super("field_repair", ChatFormatting.AQUA);
        }

        @Override
        public boolean savesDurability(Player player) {
            return player.getRandom().nextFloat() < 0.34F;
        }

        @Override
        public float reforgeScale(Player player) {
            return 1.5F;
        }
    }

    public static final ElysiumClass ARTIFICER = ElysiumClass.register(id("artificer"),
            ChatFormatting.AQUA,
            ElysiumStatBlock.of(INTELLECT, 1, PRESENCE, 1),
            new FieldRepair());

    // ------------------------------------------------------------------
    // Enforcer — the fleets' line soldier, and the Code's blunt instrument
    // ------------------------------------------------------------------

    /**
     * Sanctioned Force: +25% against the Unsworn, and less Suspicion earned.
     *
     * The Code is specific about who may be struck, and an Enforcer striking
     * them is given the benefit of the doubt.
     */
    private static final class SanctionedForce extends CorePassive {
        private SanctionedForce() {
            super("sanctioned_force", ChatFormatting.DARK_RED);
        }

        @Override
        public float attackScale(Player attacker, LivingEntity victim) {
            return ElysiumFaction.of(victim) == ElysiumFaction.UNSWORN ? 1.25F : 1.0F;
        }

        @Override
        public float suspicionScale(Player player) {
            return 0.6F;
        }
    }

    public static final ElysiumClass ENFORCER = ElysiumClass.register(id("enforcer"),
            ChatFormatting.DARK_RED,
            ElysiumStatBlock.of(STRENGTH, 1, FORTITUDE, 1),
            new SanctionedForce());

    // ------------------------------------------------------------------
    // Psion — the elemental system, practised rather than merely carried
    // ------------------------------------------------------------------

    /**
     * Resonance: elemental advantage bites harder, and aligned runes count for
     * more.
     *
     * Originally written as "aligned runes count twice", which nothing ever
     * asked about — it is now a multiplier on psionic potency, which the combat
     * handler and the rune tick both read.
     */
    private static final class Resonance extends CorePassive {
        private Resonance() {
            super("resonance", ChatFormatting.DARK_PURPLE);
        }

        @Override
        public float psionicScale(Player player) {
            return 1.5F;
        }
    }

    public static final ElysiumClass PSION = ElysiumClass.register(id("psion"),
            ChatFormatting.DARK_PURPLE,
            ElysiumStatBlock.of(INTELLECT, 1, WILLPOWER, 1),
            new Resonance());

    // ------------------------------------------------------------------
    // Voidrunner — Cybernetic Enhancement, spent on getting somewhere first
    // ------------------------------------------------------------------

    /** Slipstream: a Voidrunner is merely good at landing, and halves it. */
    private static final class Slipstream extends CorePassive {
        private Slipstream() {
            super("slipstream", ChatFormatting.GREEN);
        }

        @Override
        public float fallDamageScale(Player player) {
            return 0.5F;
        }
    }

    public static final ElysiumClass VOIDRUNNER = ElysiumClass.register(id("voidrunner"),
            ChatFormatting.GREEN,
            ElysiumStatBlock.of(AGILITY, 1, REFLEXES, 1),
            new Slipstream());

    // ------------------------------------------------------------------
    // Reclaimer — Singularity-Forged Neutronium has to come from somewhere
    // ------------------------------------------------------------------

    /** Prospector: a second ingot out of a vein, one time in four. */
    private static final class Prospector extends CorePassive {
        private Prospector() {
            super("prospector", ChatFormatting.YELLOW);
        }

        @Override
        public boolean doublesOre(Player player) {
            return player.getRandom().nextFloat() < 0.25F;
        }
    }

    public static final ElysiumClass RECLAIMER = ElysiumClass.register(id("reclaimer"),
            ChatFormatting.YELLOW,
            ElysiumStatBlock.of(FORTITUDE, 1, LUCK, 1),
            new Prospector());

    // ------------------------------------------------------------------
    // Warden — the thing that stands in the way
    // ------------------------------------------------------------------

    /**
     * Bulwark: below half health, everything this character reflects is
     * doubled.
     *
     * A multiplier rather than a share of its own, because "double whatever
     * reflection you already have" is a different idea from "add some", and the
     * engine bounds the product below 1.0 — so a Warden who is already close to
     * reflecting everything gains very little from being nearly dead, which is
     * the correct answer.
     */
    private static final class Bulwark extends CorePassive {
        private Bulwark() {
            super("bulwark", ChatFormatting.BLUE);
        }

        @Override
        public float reflectMultiplier(Player defender) {
            return defender.getHealth() < defender.getMaxHealth() * 0.5F ? 2.0F : 1.0F;
        }
    }

    public static final ElysiumClass WARDEN = ElysiumClass.register(id("warden"),
            ChatFormatting.BLUE,
            ElysiumStatBlock.of(RESILIENCE, 1, RETRIBUTION, 1),
            new Bulwark());

    // ------------------------------------------------------------------
    // Marksman — the long shot, taken from cover
    // ------------------------------------------------------------------

    /**
     * Called Shot: a Marksman does not crit more often than Accuracy says —
     * they make it count.
     */
    private static final class CalledShot extends CorePassive {
        private CalledShot() {
            super("called_shot", ChatFormatting.WHITE);
        }

        @Override
        public float critMultiplier(Player attacker) {
            return 2.25F;
        }
    }

    public static final ElysiumClass MARKSMAN = ElysiumClass.register(id("marksman"),
            ChatFormatting.WHITE,
            ElysiumStatBlock.of(ACCURACY, 1, AGILITY, 1),
            new CalledShot());

    /** Touching this class registers all nine. */
    public static void bootstrap() {
    }
}
