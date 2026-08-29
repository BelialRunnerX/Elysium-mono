package com.elysium.lib.stats;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.lib.character.ElysiumClass;
import com.elysium.lib.character.ElysiumRace;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The twelve canonical stats, and what a character's totals come to.
 *
 * <h2>The sum</h2>
 *
 * <pre>
 *   total = race base
 *         + (race growth + class growth) x (level - 1)
 *         + points spent by hand
 *         + every equipped piece the character is high enough level to use
 * </pre>
 *
 * <h2>Where the numbers come from</h2>
 *
 * Five of the twelve are the Sleeping Empire character sheet's own — Strength,
 * Reflexes, Intelligence (here Intellect), Willpower and Presence. The other
 * seven exist because the mod's systems needed them.
 *
 * Each proportional stat's halfway point and ceiling live on the stat itself,
 * so an add-on reading {@code ACCURACY.getHalfway()} can tune against the same
 * curve rather than guessing, and a stat added by an add-on behaves the same
 * way for free.
 */
public final class ElysiumStats {

    private ElysiumStats() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, path);
    }

    // ------------------------------------------------------------------
    // The twelve
    // ------------------------------------------------------------------

    /** Passive health regeneration, and a little maximum health with it. */
    public static final ElysiumStat VITALITY = ElysiumStat.flat(id("vitality"), ChatFormatting.RED);

    /** Base armour with nothing equipped. Being tough before being armoured. */
    public static final ElysiumStat FORTITUDE = ElysiumStat.flat(id("fortitude"), ChatFormatting.GRAY);

    /** Flat proportional damage reduction, applied after armour. */
    public static final ElysiumStat RESILIENCE =
            ElysiumStat.curve(id("resilience"), ChatFormatting.DARK_AQUA, 120.0F, 1.0F);

    /** Base attack damage. Weapons multiply this. */
    public static final ElysiumStat STRENGTH = ElysiumStat.flat(id("strength"), ChatFormatting.DARK_RED);

    /** Movement speed. */
    public static final ElysiumStat AGILITY =
            ElysiumStat.curve(id("agility"), ChatFormatting.GREEN, 200.0F, 0.60F);

    /** Critical hit chance. */
    public static final ElysiumStat ACCURACY =
            ElysiumStat.curve(id("accuracy"), ChatFormatting.YELLOW, 220.0F, 0.75F);

    /** Chance to avoid a blow outright. The archive calls this Reflexes. */
    public static final ElysiumStat REFLEXES =
            ElysiumStat.curve(id("reflexes"), ChatFormatting.AQUA, 380.0F, 0.50F);

    /** The share of incoming damage sent back to whoever dealt it. */
    public static final ElysiumStat RETRIBUTION =
            ElysiumStat.curve(id("retribution"), ChatFormatting.LIGHT_PURPLE, 260.0F, 0.80F);

    /** Psionic potency: elemental advantage size and rune strength. */
    public static final ElysiumStat INTELLECT = ElysiumStat.flat(id("intellect"), ChatFormatting.BLUE);

    /** Shield capacity and how fast it rebuilds. */
    public static final ElysiumStat WILLPOWER = ElysiumStat.flat(id("willpower"), ChatFormatting.DARK_PURPLE);

    /** Loot chance modifier. */
    public static final ElysiumStat LUCK =
            ElysiumStat.curve(id("luck"), ChatFormatting.GOLD, 160.0F, 0.90F);

    /** How fast standing moves, and how well a reforge rolls. */
    public static final ElysiumStat PRESENCE = ElysiumStat.flat(id("presence"), ChatFormatting.WHITE);

    /** Touching this class registers all twelve. */
    public static void bootstrap() {
    }

    // ------------------------------------------------------------------
    // The sum
    // ------------------------------------------------------------------

    /** Everything a character has, gear included. */
    public static ElysiumStatBlock total(Player player) {
        return innate(player).plus(fromGear(player));
    }

    /** Race, class, level and spent points — everything but the gear. */
    public static ElysiumStatBlock innate(Player player) {
        ElysiumRace race = ElysiumCharacter.getRace(player);
        ElysiumClass job = ElysiumCharacter.getElysiumClass(player);
        int levels = Math.max(0, ElysiumCharacter.getLevel(player) - 1);

        ElysiumStatBlock block = race == null ? ElysiumStatBlock.EMPTY : race.getBaseStats();
        ElysiumStatBlock growth = race == null ? ElysiumStatBlock.EMPTY : race.getGrowth();
        if (job != null) {
            growth = growth.plus(job.getGrowth());
        }

        return block.plus(growth.times(levels)).plus(ElysiumCharacter.getSpent(player));
    }

    /**
     * What the equipped set is granting.
     *
     * A piece the character is too low a level for contributes nothing. It is
     * still worn, still visible, still takes damage — it simply does not pay
     * out until they have earned it.
     */
    public static ElysiumStatBlock fromGear(Player player) {
        int level = ElysiumCharacter.getLevel(player);
        ElysiumStatBlock block = ElysiumStatBlock.EMPTY;

        for (ItemStack stack : player.getArmorSlots()) {
            if (ElysiumGearStats.meetsRequirement(level, stack)) {
                block = block.plus(ElysiumGearStats.of(stack));
            }
        }

        ItemStack held = player.getMainHandItem();
        if (ElysiumGearStats.meetsRequirement(level, held)) {
            block = block.plus(ElysiumGearStats.of(held));
        }

        return block;
    }

    public static int get(Player player, ElysiumStat stat) {
        return total(player).get(stat);
    }

    /** A stat read through its own curve, for the player's current total. */
    public static float proportion(Player player, ElysiumStat stat) {
        return stat.proportionOf(get(player, stat));
    }

    // ------------------------------------------------------------------
    // Combining proportions
    // ------------------------------------------------------------------

    /**
     * Stacks two proportional effects the way overlapping shields stack.
     *
     * <pre>{@code   1 - (1 - a)(1 - b)   }</pre>
     *
     * Adding shares and clamping is the obvious thing and the wrong one: two
     * 60% sources become 100% and the clamp swallows everything beyond, so past
     * a point more of a stat buys nothing. Combining what each one <em>lets
     * through</em> means 60% and 60% is 84%, and the total climbs toward 1.0
     * without arriving.
     *
     * Both inputs are clamped below 1.0, so the result is strictly less than
     * 1.0 however many times this is applied. Nothing downstream needs a
     * ceiling of its own.
     */
    public static float combine(float a, float b) {
        return 1.0F - (1.0F - clampShare(a)) * (1.0F - clampShare(b));
    }

    /** A proportional share, forced into [0, 1). */
    public static float clampShare(float value) {
        return Math.max(0.0F, Math.min(0.999F, value));
    }

    /** The raw curve, for callers with a value that is not a stat total. */
    public static float curve(int value, float halfway) {
        if (value <= 0) {
            return 0.0F;
        }
        return value / (value + halfway);
    }

    // ------------------------------------------------------------------
    // What the canonical twelve do
    //
    // These are this library's own effects for its own stats. An add-on's stat
    // gets its effect from that add-on, in exactly this shape.
    // ------------------------------------------------------------------

    /** Half-hearts restored per regeneration tick. */
    public static float regenPerTick(Player player) {
        return 0.25F + get(player, VITALITY) * 0.05F;
    }

    /** Extra maximum health, so Vitality is felt as well as seen. */
    public static double bonusHealth(Player player) {
        return get(player, VITALITY) * 0.2D;
    }

    /** Armour points with nothing equipped. */
    public static double baseArmour(Player player) {
        return get(player, FORTITUDE) * 0.5D;
    }

    public static float damageReduction(Player player) {
        return proportion(player, RESILIENCE);
    }

    /**
     * The player's own base damage, before any weapon multiplies it.
     *
     * A character with no Strength gets nothing from a high-multiplier weapon,
     * and a character with a great deal of it hits hard with anything.
     */
    public static float baseDamage(Player player) {
        return get(player, STRENGTH) * 0.25F;
    }

    public static double speedBonus(Player player) {
        return proportion(player, AGILITY);
    }

    public static float critChance(Player player) {
        return proportion(player, ACCURACY);
    }

    public static float dodgeChance(Player player) {
        return proportion(player, REFLEXES);
    }

    /** The share of a blow the Retribution stat alone sends back. */
    public static float reflectShare(Player player) {
        return proportion(player, RETRIBUTION);
    }

    /** Multiplies elemental advantage and rune affix strength. */
    public static float psionicScale(Player player) {
        float scale = 1.0F + get(player, INTELLECT) * 0.02F;
        return scale * ElysiumCharacter.passiveProduct(player, passive -> passive.psionicScale(player));
    }

    /** Absorption capacity granted by Willpower. */
    public static float shieldCapacity(Player player) {
        return get(player, WILLPOWER) * 0.4F;
    }

    /** Chance of an extra roll on an Elysium drop, from Luck alone. */
    public static float luckChance(Player player) {
        return proportion(player, LUCK);
    }

    /** Multiplies Favor and Suspicion gains, and reforge quality. */
    public static float presenceScale(Player player) {
        return 1.0F + get(player, PRESENCE) * 0.02F;
    }
}
