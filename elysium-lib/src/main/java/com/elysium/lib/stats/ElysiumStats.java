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
    /**
     * The ceiling every percentage stat is given: none. A curve stat's share
     * is value / (value + halfway) and nothing more — the owner's ruling,
     * "uncap everything, percentile effects stat / (stat + b)". The halfway
     * point is the b; it says how fast the share climbs, never where it stops.
     *
     * These used to be 0.85 to 0.98, so a maxed Reflexes character could never
     * dodge more than 85% of blows however far they pushed the stat.
     */
    public static final float UNCAPPED = 1.0F;

    public static final ElysiumStat VITALITY = ElysiumStat.flat(id("vitality"), ChatFormatting.RED);

    /** Base armour with nothing equipped. Being tough before being armoured. */
    public static final ElysiumStat FORTITUDE = ElysiumStat.flat(id("fortitude"), ChatFormatting.GRAY);

    /** Flat proportional damage reduction, applied after armour. */
    public static final ElysiumStat RESILIENCE =
            ElysiumStat.curve(id("resilience"), ChatFormatting.DARK_AQUA, (float) (1400.0F / ElysiumShares.PACE), UNCAPPED);

    /** Base attack damage. Weapons multiply this. */
    public static final ElysiumStat STRENGTH = ElysiumStat.flat(id("strength"), ChatFormatting.DARK_RED);

    /** Movement speed. */
    public static final ElysiumStat AGILITY =
            ElysiumStat.curve(id("agility"), ChatFormatting.GREEN, (float) (900.0F / ElysiumShares.PACE), UNCAPPED);

    /** Critical hit chance. */
    public static final ElysiumStat ACCURACY =
            ElysiumStat.curve(id("accuracy"), ChatFormatting.YELLOW, (float) (800.0F / ElysiumShares.PACE), UNCAPPED);

    /** Chance to avoid a blow outright. The archive calls this Reflexes. */
    public static final ElysiumStat REFLEXES =
            ElysiumStat.curve(id("reflexes"), ChatFormatting.AQUA, (float) (1200.0F / ElysiumShares.PACE), UNCAPPED);

    /** The share of incoming damage sent back to whoever dealt it. */
    public static final ElysiumStat RETRIBUTION =
            ElysiumStat.curve(id("retribution"), ChatFormatting.LIGHT_PURPLE, (float) (900.0F / ElysiumShares.PACE), UNCAPPED);

    /** Psionic potency: elemental advantage size and rune strength. */
    public static final ElysiumStat INTELLECT = ElysiumStat.flat(id("intellect"), ChatFormatting.BLUE);

    /** Shield capacity and how fast it rebuilds. */
    public static final ElysiumStat WILLPOWER = ElysiumStat.flat(id("willpower"), ChatFormatting.DARK_PURPLE);

    /** Loot chance modifier. */
    public static final ElysiumStat LUCK =
            ElysiumStat.curve(id("luck"), ChatFormatting.GOLD, (float) (700.0F / ElysiumShares.PACE), UNCAPPED);

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

        // The Ledger Web is the second thing a level buys, and it lands here
        // beside the first, so every reader of the stat sheet sees it.
        return block.plus(growth.times(levels)).plus(ElysiumCharacter.getSpent(player))
                .plus(com.elysium.lib.tree.ElysiumTreeState.block(player));
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
        // Stats added, the rule applied once: ElysiumShares. This was a product
        // of what each share let through, clamped at 99.9%.
        return ElysiumShares.add(a, b);
    }

    /** A proportional share, forced into [0, 1). */
    public static float clampShare(float value) {
        // A type check, not a ceiling: this used to stop at 99.9%.
        return ElysiumShares.sane(value);
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

    // Each of these comes in two forms on purpose. The one taking a Player is
    // what the handlers call; the one taking a plain point total is the rule
    // itself, and it is the only form that can be executed outside a game.
    //
    // Written this way after the points per level went from two to ten: the
    // multipliers below are now the difference between a character who is
    // strong and one who cannot be hurt, and a set of constants that important
    // should not be checkable only by reading them. See StatHarness.

    public static final float REGEN_BASE = 0.25F;
    public static final float REGEN_PER_POINT = 0.12F;
    public static final double HEALTH_PER_POINT = 0.5D;
    public static final float DAMAGE_PER_POINT = 0.5F;
    public static final float SHIELD_PER_POINT = 0.8F;
    public static final float PSIONIC_PER_POINT = 0.05F;
    public static final float PRESENCE_PER_POINT = 0.05F;

    /**
     * What a point total is really worth, before any rate is applied.
     *
     * Delegates to {@link ElysiumStat#effective} so that flat stats and curved
     * ones are scaled by the same function. A flat stat that kept multiplying
     * the raw total would fall further behind a curved one with every level,
     * and nothing would say why.
     */
    public static float effective(int points) {
        return ElysiumStat.effective(points);
    }

    /** Half-hearts restored per regeneration tick, for a raw Vitality total. */
    public static float regenFor(int vitality) {
        return REGEN_BASE + effective(vitality) * REGEN_PER_POINT;
    }

    /** Half-hearts restored per regeneration tick. */
    public static float regenPerTick(Player player) {
        return regenFor(get(player, VITALITY));
    }

    /** Extra maximum health, for a raw Vitality total. */
    public static double healthFor(int vitality) {
        return effective(vitality) * HEALTH_PER_POINT;
    }

    /** Extra maximum health, so Vitality is felt as well as seen. */
    public static double bonusHealth(Player player) {
        return healthFor(get(player, VITALITY));
    }

    /** What one point of Fortitude is worth as armour. */
    public static final double ARMOUR_PER_POINT = 0.6D;

    /** Where vanilla's ARMOR attribute stops paying, whatever is added to it. */
    public static final double VANILLA_ARMOUR_CEILING = 30.0D;

    /** What one point of Fortitude is worth as toughness, past that ceiling. */
    public static final double TOUGHNESS_PER_POINT = 0.25D;

    /** Vanilla's own ceiling on ARMOR_TOUGHNESS. */
    public static final double VANILLA_TOUGHNESS_CEILING = 20.0D;

    /**
     * The Fortitude at which {@link #baseArmour} has reached vanilla's ceiling.
     *
     * Searched for rather than written down, because the value a point of
     * Fortitude is worth is no longer linear and there is no closed form worth
     * reading. Changing the rate, the ceiling or the scaling divisor moves the
     * handover with it — a constant that agrees with its neighbours only
     * because somebody remembered to edit all four is one that will eventually
     * disagree.
     */
    public static final int ARMOUR_FULL = firstFullyArmouredFortitude();

    private static int firstFullyArmouredFortitude() {
        for (int points = 1; points < 100_000; points++) {
            if (effective(points) * ARMOUR_PER_POINT >= VANILLA_ARMOUR_CEILING) {
                return points;
            }
        }
        return 100_000;
    }

    /**
     * Armour points with nothing equipped.
     *
     * Vanilla's ARMOR attribute stops at 30, so past {@link #ARMOUR_FULL}
     * Fortitude this is the number the game ignores. That is why
     * {@link #baseToughness} exists: toughness has its own ceiling and changes
     * how armour behaves against large hits rather than small ones, so the back
     * half of the stat buys something the front half could not.
     */
    public static double baseArmour(Player player) {
        return armourFor(get(player, FORTITUDE));
    }

    /** The same rule, for a raw Fortitude total. */
    public static double armourFor(int fortitude) {
        return effective(fortitude) * ARMOUR_PER_POINT;
    }

    /**
     * Armour toughness, which is where Fortitude goes once armour is full.
     *
     * Nothing at all until the armour it grants has bought everything vanilla
     * will accept, so a low-Fortitude character is not quietly handed a stat
     * they have not earned, and a high-Fortitude one is not left pouring points
     * into a ceiling.
     */
    public static double baseToughness(Player player) {
        return toughnessFor(get(player, FORTITUDE));
    }

    /** The same rule, for a raw Fortitude total. */
    public static double toughnessFor(int fortitude) {
        if (fortitude <= ARMOUR_FULL) {
            return 0.0D;
        }
        double beyond = effective(fortitude) - effective(ARMOUR_FULL);
        // No ceiling: vanilla's 20 is lifted (ElysiumAttributeLimits), and the
        // damage formula reads toughness uncapped either way.
        return beyond * TOUGHNESS_PER_POINT;
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
        return damageFor(get(player, STRENGTH));
    }

    /** The same rule, for a raw Strength total. */
    public static float damageFor(int strength) {
        return effective(strength) * DAMAGE_PER_POINT;
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
        float scale = 1.0F + effective(get(player, INTELLECT)) * PSIONIC_PER_POINT;
        return scale * ElysiumCharacter.passiveProduct(player, passive -> passive.psionicScale(player));
    }

    /** Absorption capacity granted by Willpower. */
    public static float shieldCapacity(Player player) {
        return shieldFor(get(player, WILLPOWER));
    }

    /** The same rule, for a raw Willpower total. */
    public static float shieldFor(int willpower) {
        return effective(willpower) * SHIELD_PER_POINT;
    }

    /** Chance of an extra roll on an Elysium drop, from Luck alone. */
    public static float luckChance(Player player) {
        return proportion(player, LUCK);
    }

    /** Multiplies Favor and Suspicion gains, and reforge quality. */
    public static float presenceScale(Player player) {
        return 1.0F + effective(get(player, PRESENCE)) * PRESENCE_PER_POINT;
    }
}
