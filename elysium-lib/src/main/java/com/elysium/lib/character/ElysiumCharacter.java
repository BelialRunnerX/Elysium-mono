package com.elysium.lib.character;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.stats.ElysiumStat;
import com.elysium.lib.stats.ElysiumStatBlock;
import com.elysium.lib.stats.ElysiumStats;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Who a player is: race, class, level, and the points they have spent.
 *
 * All of it lives in data attachments and all of it survives death. A character
 * sheet you lose on a bad fall is not a character sheet.
 *
 * <h2>Levelling has no ceiling</h2>
 *
 * The requirement per level grows linearly, so total experience grows as the
 * square of level, and there is no maximum. That is not an oversight: armour
 * requires a level to wear and ascension raises armour tiers forever, so the
 * level track has to be able to follow it forever too.
 *
 * Vanilla experience is untouched. Spending green levels on an anvil should
 * never take your chestplate off.
 *
 * <h2>Passives</h2>
 *
 * The bottom half of this class is how the engine asks a character's race and
 * class what they do. Everything goes through {@link #passiveProduct},
 * {@link #passiveShare} or {@link #passiveMax}, so adding a hook to
 * {@link ElysiumPassive} needs no change here and no change in any add-on that
 * does not care about it.
 */
public final class ElysiumCharacter {

    private ElysiumCharacter() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ElysiumLib.MODID);

    /** Free points granted per level, on top of race and class growth. */
    public static final int POINTS_PER_LEVEL = 2;

    // ------------------------------------------------------------------
    // Attachments
    // ------------------------------------------------------------------

    /** Empty until chosen. An empty race is what triggers the first-join picker. */
    public static final Supplier<AttachmentType<String>> RACE =
            ATTACHMENTS.register("race", () -> AttachmentType.<String>builder(() -> "")
                    .serialize(Codec.STRING).copyOnDeath().build());

    public static final Supplier<AttachmentType<String>> CLAZZ =
            ATTACHMENTS.register("class", () -> AttachmentType.<String>builder(() -> "")
                    .serialize(Codec.STRING).copyOnDeath().build());

    public static final Supplier<AttachmentType<Integer>> LEVEL =
            ATTACHMENTS.register("level", () -> AttachmentType.<Integer>builder(() -> 1)
                    .serialize(Codec.INT).copyOnDeath().build());

    public static final Supplier<AttachmentType<Integer>> XP =
            ATTACHMENTS.register("xp", () -> AttachmentType.<Integer>builder(() -> 0)
                    .serialize(Codec.INT).copyOnDeath().build());

    public static final Supplier<AttachmentType<Integer>> UNSPENT =
            ATTACHMENTS.register("unspent", () -> AttachmentType.<Integer>builder(() -> 0)
                    .serialize(Codec.INT).copyOnDeath().build());

    /**
     * Whether this player has been handed a character codex.
     *
     * Kept separate from the race because the picker can be escaped by
     * quitting: keying the grant on "has no race yet" handed out a fresh one on
     * every relog to anyone who had not answered it.
     */
    public static final Supplier<AttachmentType<Boolean>> CODEX_GIVEN =
            ATTACHMENTS.register("codex_given", () -> AttachmentType.<Boolean>builder(() -> false)
                    .serialize(Codec.BOOL).copyOnDeath().build());

    /** Points assigned by hand, keyed by namespaced stat id. */
    public static final Supplier<AttachmentType<Map<String, Integer>>> SPENT =
            ATTACHMENTS.register("spent", () -> AttachmentType.<Map<String, Integer>>builder(Map::of)
                    .serialize(ElysiumStatBlock.MAP_CODEC).copyOnDeath().build());

    // ------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------

    /**
     * @return the chosen race, or null when the player has not chosen — or when
     *         the mod that defined their race is no longer installed
     */
    public static ElysiumRace getRace(Player player) {
        return ElysiumRace.REGISTRY.get(player.getData(RACE.get()));
    }

    /** @return the chosen class, or null, on the same terms as {@link #getRace} */
    public static ElysiumClass getElysiumClass(Player player) {
        return ElysiumClass.REGISTRY.get(player.getData(CLAZZ.get()));
    }

    public static boolean hasChosen(Player player) {
        return getRace(player) != null && getElysiumClass(player) != null;
    }

    public static void setRace(Player player, ElysiumRace race) {
        player.setData(RACE.get(), race.getSerialisedName());
    }

    public static void setElysiumClass(Player player, ElysiumClass value) {
        player.setData(CLAZZ.get(), value.getSerialisedName());
    }

    public static boolean hasCodex(Player player) {
        return player.getData(CODEX_GIVEN.get());
    }

    public static void markCodexGiven(Player player) {
        player.setData(CODEX_GIVEN.get(), true);
    }

    // ------------------------------------------------------------------
    // Level and experience
    // ------------------------------------------------------------------

    public static int getLevel(Player player) {
        return Math.max(1, player.getData(LEVEL.get()));
    }

    public static int getXp(Player player) {
        return player.getData(XP.get());
    }

    public static int getUnspentPoints(Player player) {
        return player.getData(UNSPENT.get());
    }

    /**
     * Experience needed to leave the given level. Linear in level, so the
     * cumulative cost is quadratic.
     */
    public static int xpToNext(int level) {
        return 60 + 40 * level;
    }

    /**
     * Awards experience and levels up as many times as it earns.
     *
     * @return how many levels were gained, so the caller can announce it
     */
    public static int addXp(Player player, int amount) {
        if (amount <= 0) {
            return 0;
        }

        // Scaled here, at the one place experience is ever granted, rather than
        // at each of the callers. A trinket that says "gain more experience"
        // should mean all experience, including from a source added by a mod
        // that has never heard of the trinket.
        //
        // Rounded up, so a small award times a small bonus is never rounded
        // down to the award itself — the bonus a player was promised should be
        // visible on the first kill, not only in aggregate.
        float scale = com.elysium.lib.event.ElysiumPassives.xpScale(player);
        if (scale != 1.0F) {
            amount = Math.max(1, (int) Math.ceil(amount * scale));
        }

        int xp = getXp(player) + amount;
        int level = getLevel(player);
        int gained = 0;

        while (xp >= xpToNext(level)) {
            xp -= xpToNext(level);
            level++;
            gained++;
            // A runaway award should not lock the server in this loop.
            if (gained > 1000) {
                break;
            }
        }

        player.setData(XP.get(), xp);
        if (gained > 0) {
            player.setData(LEVEL.get(), level);
            player.setData(UNSPENT.get(), getUnspentPoints(player) + gained * POINTS_PER_LEVEL);
            player.displayClientMessage(Component.translatable(
                            "elysium.level.up", level, gained * POINTS_PER_LEVEL)
                    .withStyle(ChatFormatting.GOLD), false);
        }
        return gained;
    }

    // ------------------------------------------------------------------
    // Spent points
    // ------------------------------------------------------------------

    public static ElysiumStatBlock getSpent(Player player) {
        return ElysiumStatBlock.fromMap(player.getData(SPENT.get()));
    }

    /** @return false when there is nothing left to spend */
    public static boolean spendPoint(Player player, ElysiumStat stat) {
        int unspent = getUnspentPoints(player);
        if (unspent <= 0) {
            return false;
        }
        player.setData(UNSPENT.get(), unspent - 1);
        player.setData(SPENT.get(), getSpent(player).with(stat, 1).toMap());
        return true;
    }

    /**
     * Hands every spent point back.
     *
     * Offered because a stat sheet a player cannot correct is a stat sheet they
     * will reroll a character to escape.
     */
    public static void respec(Player player) {
        int returned = getSpent(player).sum();
        player.setData(SPENT.get(), Map.of());
        player.setData(UNSPENT.get(), getUnspentPoints(player) + returned);
    }

    // ------------------------------------------------------------------
    // Conditions a passive is likely to want
    // ------------------------------------------------------------------

    /**
     * True when the player has not been hurt recently.
     *
     * Both halves are needed. {@code getLastHurtByMobTimestamp} is only written
     * when the damage had a living attacker, so on its own a Korrath standing
     * in lava counted as untouched and molted at triple rate the whole time —
     * and because the field defaults to 0, a brand-new player satisfied it from
     * tick 101 without ever having been left alone. {@code hurtTime} is set by
     * every damage type including fire, fall and drowning, and runs down over
     * about half a second, which closes both.
     */
    public static boolean untouchedFor(Player player, int ticks) {
        return player.hurtTime == 0
                && player.tickCount - player.getLastHurtByMobTimestamp() > ticks;
    }

    // ------------------------------------------------------------------
    // Passives
    // ------------------------------------------------------------------

    /**
     * This character's passives: race, then class, then everything worn.
     *
     * <h2>Why trinkets arrive here rather than anywhere else</h2>
     *
     * Every combinator below — {@link #passiveProduct}, {@link #passiveShare},
     * {@link #passiveMax} — and every hook in {@code ElysiumPassives} reads its
     * subjects from this one method. Appending worn trinkets here is therefore
     * the entire integration: all fifteen hooks gained trinket support without
     * one of them being edited, and a hook added tomorrow gets it for free.
     *
     * The alternative — a parallel trinket-effect system — would have needed
     * its own copy of each hook, its own combining rules, and an answer to
     * which of the two applies first. There is nothing to decide here because
     * there is only one mechanism.
     *
     * <h2>Order</h2>
     *
     * Race, class, then trinkets in whatever order the accessory mod reports
     * them. Order is not load-bearing: products and proportional shares are
     * both commutative, and {@code passiveMax} takes the largest. If a hook is
     * ever added where order matters, that hook is the bug.
     *
     * <h2>Cost</h2>
     *
     * This runs in the damage path. Race and class are two attachment reads;
     * the trinket list comes from an adapter that is required to cache — see
     * {@link com.elysium.lib.trinket.ElysiumTrinkets}. With no accessory mod
     * installed the trinket call returns an immutable empty list and allocates
     * nothing.
     */
    public static List<ElysiumPassive> passives(Player player) {
        List<ElysiumPassive> found = new ArrayList<>(4);
        ElysiumRace race = getRace(player);
        if (race != null && race.getPassive() != null) {
            found.add(race.getPassive());
        }
        ElysiumClass job = getElysiumClass(player);
        if (job != null && job.getPassive() != null) {
            found.add(job.getPassive());
        }
        found.addAll(com.elysium.lib.trinket.ElysiumTrinkets.passives(player));
        return found;
    }

    /**
     * Multiplies every passive's answer together.
     *
     * For multipliers on damage, regeneration, standing gain and the like. Two
     * sources of +25% come to +56%, not +50%, and a passive that returns 1.0
     * costs nothing.
     */
    public static float passiveProduct(Player player, Function<ElysiumPassive, Float> question) {
        float scale = 1.0F;
        for (ElysiumPassive passive : passives(player)) {
            scale *= question.apply(passive);
        }
        return scale;
    }

    /**
     * Combines every passive's answer as proportional shares.
     *
     * For anything that is a fraction of something — reflection, dodge, drop
     * chance. Approaches 1.0 and cannot pass it, so no clamp is needed at the
     * call site and no amount of stacked passives makes a share exceed the
     * whole.
     */
    public static float passiveShare(Player player, Function<ElysiumPassive, Float> question) {
        float share = 0.0F;
        for (ElysiumPassive passive : passives(player)) {
            share = ElysiumStats.combine(share, question.apply(passive));
        }
        return share;
    }

    /**
     * The largest answer any passive gives.
     *
     * For quantities where stacking makes no sense — two passives that each
     * "make critical hits hurt more" should give the better of the two, not
     * their product.
     */
    public static float passiveMax(Player player, Function<ElysiumPassive, Float> question,
                                   float fallback) {
        float best = fallback;
        for (ElysiumPassive passive : passives(player)) {
            best = Math.max(best, question.apply(passive));
        }
        return best;
    }

    /** True when any passive says so. */
    public static boolean passiveAny(Player player,
                                     Function<ElysiumPassive, Boolean> question) {
        for (ElysiumPassive passive : passives(player)) {
            if (question.apply(passive)) {
                return true;
            }
        }
        return false;
    }

    /** The sum of every passive's answer, for whole-number quantities. */
    public static int passiveSum(Player player, Function<ElysiumPassive, Integer> question,
                                 int fallback) {
        List<ElysiumPassive> found = passives(player);
        if (found.isEmpty()) {
            return fallback;
        }
        int total = 0;
        for (ElysiumPassive passive : found) {
            total += question.apply(passive);
        }
        return total;
    }
}
