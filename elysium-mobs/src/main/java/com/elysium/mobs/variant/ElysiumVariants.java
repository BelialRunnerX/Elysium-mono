package com.elysium.mobs.variant;

import com.elysium.mobs.ElysiumMobs;
import com.elysium.mobs.entity.ElysiumFamilies;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

/**
 * The thirty sub-variants: five for each of the six families.
 *
 * <h2>The budget</h2>
 *
 * Every variant's three multipliers — health, damage, speed — add up to exactly
 * <b>3.00</b>. One of them is 1.0/1.0/1.0 in each family; the other four spend
 * the same total differently.
 *
 * That is the whole balance model, and it is deliberately crude, because a
 * crude rule that holds is worth more than a subtle one that decays. Five
 * sub-variants only stay a choice of <em>how</em> a fight goes if none of them
 * is simply better, and the way that promise breaks is one variant getting
 * nudged up during tuning and nobody noticing. {@code validate.py} adds up all
 * thirty after every edit.
 *
 * The abilities are not in the budget, and cannot be: there is no exchange rate
 * between "+0.2 health" and "poisons on hit". They are balanced by being
 * different rather than by being equal, which is a judgement rather than an
 * invariant — so the numbers carry the part that can be checked, and the
 * abilities carry the part that cannot.
 *
 * <h2>Names</h2>
 *
 * Unsworn variants are named for what happened to them; Imperial ones for the
 * office they hold. That is the difference between the two sides stated in the
 * only place a player reads before the fight starts.
 */
public final class ElysiumVariants {

    private ElysiumVariants() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ElysiumMobs.MODID, path);
    }

    // ==================================================================
    // Scavenger — the Unsworn who kept moving
    // ==================================================================

    public static final MobVariant RAGPICKER = variant("ragpicker", ElysiumFamilies.SCAVENGER_ID,
            1.00F, 1.00F, 1.00F, ChatFormatting.GRAY, MobAbilities.none());

    /** Starving, and worst when it is nearly finished. */
    public static final MobVariant FERAL = variant("feral", ElysiumFamilies.SCAVENGER_ID,
            0.75F, 1.45F, 0.80F, ChatFormatting.RED, MobAbilities.cornered(0.80F));

    /** Lives off what it kills, and knits back together while it does. */
    public static final MobVariant CARRION = variant("carrion", ElysiumFamilies.SCAVENGER_ID,
            1.35F, 0.85F, 0.80F, ChatFormatting.DARK_GREEN, MobAbilities.knitting(0.020F));

    /** Too fast to corner. */
    public static final MobVariant SCUTTLER = variant("scuttler", ElysiumFamilies.SCAVENGER_ID,
            0.70F, 0.95F, 1.35F, ChatFormatting.YELLOW, MobAbilities.swift(1));

    /** Fed on something that stayed in it. */
    public static final MobVariant BLIGHTFED = variant("blightfed", ElysiumFamilies.SCAVENGER_ID,
            0.95F, 1.00F, 1.05F, ChatFormatting.GREEN, MobAbilities.venomous(5, 0));

    // ==================================================================
    // Reaver — the Unsworn who were put to work
    // ==================================================================

    public static final MobVariant CHAINBOUND = variant("chainbound", ElysiumFamilies.REAVER_ID,
            1.00F, 1.00F, 1.00F, ChatFormatting.GRAY, MobAbilities.unshaken());

    /** Slag-plated. Hard to cut, easy to burn. */
    public static final MobVariant SLAGFIST = variant("slagfist", ElysiumFamilies.REAVER_ID,
            1.30F, 1.00F, 0.70F, ChatFormatting.DARK_GRAY,
            MobAbilities.hardened(0.75F, 1.25F));

    /** Nothing left in it but the last swing. */
    public static final MobVariant HOLLOWED = variant("hollowed", ElysiumFamilies.REAVER_ID,
            0.85F, 1.40F, 0.75F, ChatFormatting.DARK_RED, MobAbilities.cornered(0.70F));

    /** Broke its yoke, and takes someone with it. */
    public static final MobVariant YOKEBREAKER = variant("yokebreaker", ElysiumFamilies.REAVER_ID,
            1.20F, 0.95F, 0.85F, ChatFormatting.GOLD, MobAbilities.deadfall(1.5F));

    /** Grinds armour off before it grinds through it. */
    public static final MobVariant GRINDMAW = variant("grindmaw", ElysiumFamilies.REAVER_ID,
            0.95F, 1.20F, 0.85F, ChatFormatting.DARK_PURPLE, MobAbilities.sundering(6));

    // ==================================================================
    // Whisper — the Unsworn who were never found
    // ==================================================================

    public static final MobVariant ASHLING = variant("ashling", ElysiumFamilies.WHISPER_ID,
            1.00F, 1.00F, 1.00F, ChatFormatting.GRAY, MobAbilities.swift(0));

    /** One cut, and it meant it. */
    public static final MobVariant NIGHTCUT = variant("nightcut", ElysiumFamilies.WHISPER_ID,
            0.75F, 1.45F, 0.80F, ChatFormatting.DARK_RED, MobAbilities.cornered(0.90F));

    /** Between places more than in them. */
    public static final MobVariant VEILWALK = variant("veilwalk", ElysiumFamilies.WHISPER_ID,
            0.85F, 0.95F, 1.20F, ChatFormatting.LIGHT_PURPLE, MobAbilities.swift(1));

    /** Goes out taking your eyes with it. */
    public static final MobVariant GUTTERGHOST = variant("gutterghost", ElysiumFamilies.WHISPER_ID,
            0.90F, 1.05F, 1.05F, ChatFormatting.DARK_AQUA, MobAbilities.blinding(4));

    /** What one of them feels, all of them feel. */
    public static final MobVariant MOURNER = variant("mourner", ElysiumFamilies.WHISPER_ID,
            1.15F, 0.95F, 0.90F, ChatFormatting.BLUE, MobAbilities.echo(6.0D, 0.20F));

    // ==================================================================
    // Drone — Imperial equipment
    // ==================================================================

    public static final MobVariant PATTERN_ONE = variant("pattern_one", ElysiumFamilies.DRONE_ID,
            1.00F, 1.00F, 1.00F, ChatFormatting.GRAY, MobAbilities.none());

    /** Carries a field. Get through it first. */
    public static final MobVariant INTERDICTOR = variant("interdictor", ElysiumFamilies.DRONE_ID,
            1.25F, 0.90F, 0.85F, ChatFormatting.AQUA, MobAbilities.bulwark(4.0F));

    /** One purpose, and it is not survival. */
    public static final MobVariant LANCER = variant("lancer", ElysiumFamilies.DRONE_ID,
            0.80F, 1.40F, 0.80F, ChatFormatting.GOLD, MobAbilities.sundering(5));

    /** Makes everything around it fight better. */
    public static final MobVariant RELAY = variant("relay", ElysiumFamilies.DRONE_ID,
            1.05F, 0.85F, 1.10F, ChatFormatting.YELLOW, MobAbilities.standard(8.0D, 0));

    /** The Empire does not leave equipment behind intact. */
    public static final MobVariant KILL_SWITCH = variant("kill_switch", ElysiumFamilies.DRONE_ID,
            0.90F, 1.05F, 1.05F, ChatFormatting.DARK_RED, MobAbilities.deadfall(2.0F));

    // ==================================================================
    // Lictor — Imperial office
    // ==================================================================

    public static final MobVariant SANCTIONED = variant("sanctioned", ElysiumFamilies.LICTOR_ID,
            1.00F, 1.00F, 1.00F, ChatFormatting.GRAY, MobAbilities.unshaken());

    /** Stands in a doorway and is the doorway. */
    public static final MobVariant AEGIS = variant("aegis", ElysiumFamilies.LICTOR_ID,
            1.30F, 0.85F, 0.85F, ChatFormatting.AQUA, MobAbilities.bulwark(6.0F));

    /** Removes the right to be armoured. */
    public static final MobVariant CENSOR = variant("censor", ElysiumFamilies.LICTOR_ID,
            0.90F, 1.35F, 0.75F, ChatFormatting.DARK_PURPLE, MobAbilities.sundering(8));

    /** A line, not a soldier. Hurting one hurts the line. */
    public static final MobVariant CUSTODIAN = variant("custodian", ElysiumFamilies.LICTOR_ID,
            1.15F, 0.95F, 0.90F, ChatFormatting.BLUE, MobAbilities.echo(7.0D, 0.25F));

    /** Answers a blow by getting worse. */
    public static final MobVariant INQUISITOR = variant("inquisitor", ElysiumFamilies.LICTOR_ID,
            0.95F, 1.15F, 0.90F, ChatFormatting.DARK_RED, MobAbilities.overcharged(4));

    // ==================================================================
    // Adept — Imperial doctrine
    // ==================================================================

    public static final MobVariant ACOLYTE = variant("acolyte", ElysiumFamilies.ADEPT_ID,
            1.00F, 1.00F, 1.00F, ChatFormatting.GRAY, MobAbilities.standard(10.0D, 0));

    /** Keeps the others standing. Kill it first. */
    public static final MobVariant VIVIFIER = variant("vivifier", ElysiumFamilies.ADEPT_ID,
            1.20F, 0.85F, 0.95F, ChatFormatting.GREEN, MobAbilities.knitting(0.030F));

    /** Rings louder when struck. */
    public static final MobVariant RESONANT = variant("resonant", ElysiumFamilies.ADEPT_ID,
            0.85F, 1.30F, 0.85F, ChatFormatting.LIGHT_PURPLE, MobAbilities.overcharged(5));

    /** Gives the orders, and the orders work. */
    public static final MobVariant MARSHAL = variant("marshal", ElysiumFamilies.ADEPT_ID,
            1.10F, 0.90F, 1.00F, ChatFormatting.GOLD, MobAbilities.standard(12.0D, 1));

    /** Doctrine against the psionic, at the cost of everything else. */
    public static final MobVariant NULL_SPEAKER = variant("null_speaker", ElysiumFamilies.ADEPT_ID,
            0.90F, 1.05F, 1.05F, ChatFormatting.DARK_AQUA,
            MobAbilities.hardened(1.20F, 0.60F));

    // ==================================================================

    private static MobVariant variant(String path, ResourceLocation family,
                                      float health, float damage, float speed,
                                      ChatFormatting colour, MobAbility ability) {
        return MobVariant.builder(id(path), family)
                .stats(health, damage, speed)
                .colour(colour)
                .ability(ability)
                .register();
    }

    /** Touching this class registers all thirty. */
    public static void bootstrap() {
    }
}
