package com.elysium.core.character;

import com.elysium.core.Elysium;
import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.lib.character.ElysiumRace;
import com.elysium.lib.stats.ElysiumStatBlock;
import com.elysium.lib.stats.ElysiumStats;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
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
 * The six races, and what each one is.
 *
 * Five come straight out of the Sleeping Empire's Known Species document — the
 * humanoid majority, the reptilian Druun Ascendancy, the avian Veylari Concord,
 * the insectoid Korrath Dominion and the energy-based Lumari Collective — and
 * each stat shape is read off what that entry says about them. The Druun are
 * described as militaristic and hierarchical, so they hit hard and think
 * slowly. The Lumari "exist in forms that transcend traditional biology", so
 * they are barely armoured and enormously willed.
 *
 * The sixth, the Unsworn, is this mod's own: the people outside the Code.
 *
 * <b>Every race begins with exactly 44 points</b>, spread differently, and
 * grows 3 a level. Nobody starts strictly ahead. The library does not enforce
 * that — an add-on may have reasons — but it is what keeps these six a choice
 * rather than a ranking, and {@code validate.py} checks it.
 */
public final class ElysiumRaces {

    private ElysiumRaces() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Elysium.MODID, path);
    }

    // ------------------------------------------------------------------
    // Imperial
    // ------------------------------------------------------------------

    /**
     * Sanctioned Answer: the Empire's own doctrine turned inward. An Imperial
     * standing inside the Code is answered for.
     *
     * Climbs toward 100% and never arrives — 9% at level 10, 50% at 100, 80% at
     * 400. At the far end an attacker takes very nearly what they dealt, which
     * is the Code's position on the matter stated as arithmetic. It cannot
     * reach 100 because the engine combines shares proportionally; past that
     * boundary, touching an Imperial would kill you outright whatever you hit
     * them with, and every fight would collapse into the same one.
     */
    private static final class SanctionedAnswer extends CorePassive {
        private SanctionedAnswer() {
            super("sanctioned_answer", ChatFormatting.GOLD);
        }

        @Override
        public float reflectShare(Player defender) {
            return ElysiumStats.curve(ElysiumCharacter.getLevel(defender), 100.0F);
        }
    }

    public static final ElysiumRace IMPERIAL = ElysiumRace.register(id("imperial"),
            ChatFormatting.GOLD,
            ElysiumStatBlock.of(VITALITY, 4, FORTITUDE, 4, RESILIENCE, 3, STRENGTH, 4,
                    AGILITY, 3, ACCURACY, 3, REFLEXES, 3, RETRIBUTION, 5,
                    INTELLECT, 3, WILLPOWER, 3, LUCK, 3, PRESENCE, 6),
            ElysiumStatBlock.of(RETRIBUTION, 1, PRESENCE, 1, VITALITY, 1),
            new SanctionedAnswer());

    // ------------------------------------------------------------------
    // Druun — "militaristic tendencies and strong hierarchical societies"
    // ------------------------------------------------------------------

    /** Cold Blood: the Ascendancy fights hardest when it is losing. */
    private static final class ColdBlood extends CorePassive {
        private ColdBlood() {
            super("cold_blood", ChatFormatting.DARK_GREEN);
        }

        @Override
        public float attackScale(Player attacker, LivingEntity victim) {
            float missing = 1.0F - attacker.getHealth() / Math.max(1.0F, attacker.getMaxHealth());
            return 1.0F + 0.60F * missing;
        }
    }

    public static final ElysiumRace DRUUN = ElysiumRace.register(id("druun"),
            ChatFormatting.DARK_GREEN,
            ElysiumStatBlock.of(VITALITY, 6, FORTITUDE, 8, RESILIENCE, 5, STRENGTH, 8,
                    AGILITY, 2, ACCURACY, 3, REFLEXES, 2, RETRIBUTION, 2,
                    INTELLECT, 1, WILLPOWER, 3, LUCK, 2, PRESENCE, 2),
            ElysiumStatBlock.of(STRENGTH, 2, FORTITUDE, 1),
            new ColdBlood());

    // ------------------------------------------------------------------
    // Veylari — "isolationist and technologically advanced"
    // ------------------------------------------------------------------

    /** Lightfeather: avian, so gravity is a formality. */
    private static final class Lightfeather extends CorePassive {
        private Lightfeather() {
            super("lightfeather", ChatFormatting.AQUA);
        }

        @Override
        public float fallDistanceIgnored(Player player) {
            return 10.0F;
        }

        @Override
        public float fallDamageScale(Player player) {
            return 0.33F;
        }
    }

    public static final ElysiumRace VEYLARI = ElysiumRace.register(id("veylari"),
            ChatFormatting.AQUA,
            ElysiumStatBlock.of(VITALITY, 3, FORTITUDE, 2, RESILIENCE, 2, STRENGTH, 3,
                    AGILITY, 6, ACCURACY, 8, REFLEXES, 5, RETRIBUTION, 1,
                    INTELLECT, 7, WILLPOWER, 2, LUCK, 3, PRESENCE, 2),
            ElysiumStatBlock.of(INTELLECT, 2, ACCURACY, 1),
            new Lightfeather());

    // ------------------------------------------------------------------
    // Korrath — "hive-like social structures and rapid expansion"
    // ------------------------------------------------------------------

    /**
     * Molt: the race that sheds its shell heals in bursts rather than a
     * trickle. Nothing for five seconds, then triple.
     */
    private static final class Molt extends CorePassive {
        private Molt() {
            super("molt", ChatFormatting.YELLOW);
        }

        @Override
        public float regenScale(Player player) {
            return ElysiumCharacter.untouchedFor(player, 100) ? 3.0F : 1.0F;
        }
    }

    public static final ElysiumRace KORRATH = ElysiumRace.register(id("korrath"),
            ChatFormatting.YELLOW,
            ElysiumStatBlock.of(VITALITY, 5, FORTITUDE, 3, RESILIENCE, 3, STRENGTH, 4,
                    AGILITY, 8, ACCURACY, 4, REFLEXES, 7, RETRIBUTION, 2,
                    INTELLECT, 2, WILLPOWER, 1, LUCK, 4, PRESENCE, 1),
            ElysiumStatBlock.of(AGILITY, 2, REFLEXES, 1),
            new Molt());

    // ------------------------------------------------------------------
    // Lumari — "exist in forms that transcend traditional biology"
    // ------------------------------------------------------------------

    /**
     * Photonic: a body held together by will has more of it to spend, and is
     * hard to burn but easy to hit. A trade, not a bonus.
     */
    private static final class Photonic extends CorePassive {
        private Photonic() {
            super("photonic", ChatFormatting.LIGHT_PURPLE);
        }

        @Override
        public float shieldScale(Player player) {
            return 2.0F;
        }

        @Override
        public float defenceScale(Player defender, DamageSource source) {
            boolean elemental = source.is(DamageTypeTags.IS_FIRE)
                    || source.is(DamageTypeTags.IS_EXPLOSION);
            return elemental ? 0.66F : 1.15F;
        }
    }

    public static final ElysiumRace LUMARI = ElysiumRace.register(id("lumari"),
            ChatFormatting.LIGHT_PURPLE,
            ElysiumStatBlock.of(VITALITY, 2, FORTITUDE, 1, RESILIENCE, 5, STRENGTH, 2,
                    AGILITY, 3, ACCURACY, 3, REFLEXES, 3, RETRIBUTION, 3,
                    INTELLECT, 8, WILLPOWER, 9, LUCK, 3, PRESENCE, 2),
            ElysiumStatBlock.of(WILLPOWER, 2, INTELLECT, 1),
            new Photonic());

    // ------------------------------------------------------------------
    // Unsworn — this mod's own
    // ------------------------------------------------------------------

    /**
     * Uncounted: the Empire does not reward people it does not recognise, and
     * does not watch them closely enough to build a file either.
     */
    private static final class Uncounted extends CorePassive {
        private Uncounted() {
            super("uncounted", ChatFormatting.DARK_RED);
        }

        @Override
        public float favorScale(Player player) {
            return 0.5F;
        }

        @Override
        public float suspicionScale(Player player) {
            return 0.5F;
        }

        @Override
        public int decayRate(Player player) {
            return 2;
        }
    }

    public static final ElysiumRace UNSWORN = ElysiumRace.register(id("unsworn"),
            ChatFormatting.DARK_RED,
            ElysiumStatBlock.of(VITALITY, 4, FORTITUDE, 3, RESILIENCE, 3, STRENGTH, 5,
                    AGILITY, 6, ACCURACY, 4, REFLEXES, 4, RETRIBUTION, 2,
                    INTELLECT, 2, WILLPOWER, 2, LUCK, 9, PRESENCE, 0),
            ElysiumStatBlock.of(LUCK, 2, STRENGTH, 1),
            new Uncounted());

    /** Touching this class registers all six. */
    public static void bootstrap() {
    }
}
