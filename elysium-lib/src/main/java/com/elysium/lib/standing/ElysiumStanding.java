package com.elysium.lib.standing;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.event.ElysiumPassives;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Where the Empire stands on you.
 *
 * Two meters, 0-100 each, stored as data attachments so they persist across
 * saves and survive death. They are not a good axis and a bad axis — they are
 * two self-reinforcing loops, and a player picks which one to ride.
 *
 * <pre>
 *   kill Unsworn  →  Favor      →  more Unsworn spawn   →  better LOOT TIER
 *   kill Empire   →  Suspicion  →  more Empire spawn    →  larger LOOT AMOUNT
 * </pre>
 *
 * Each loop feeds itself: the kills that raise a meter also summon more of the
 * thing you have to kill to raise it further. Nothing stops that except time —
 * both meters decay on their own, slowly enough that a session's work holds and
 * fast enough that neither is permanent.
 *
 * Riding Favor gets you fewer, better drops. Riding Suspicion gets you more of
 * whatever you are already getting. Riding both gets you a lot of dangerous
 * company.
 */
public final class ElysiumStanding {

    private ElysiumStanding() {
    }

    public static final int MAX = 100;

    /** Below this the Empire is not paying attention either way. */
    public static final int NOTICE = 25;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ElysiumLib.MODID);

    public static final Supplier<AttachmentType<Integer>> FAVOR =
            ATTACHMENTS.register("favor", () -> AttachmentType.<Integer>builder(() -> 0)
                    .serialize(Codec.INT)
                    .copyOnDeath()
                    .build());

    /**
     * Suspicion also survives death. Dying to the enforcers the Empire sent
     * would be a strange way to clear your record.
     */
    public static final Supplier<AttachmentType<Integer>> SUSPICION =
            ATTACHMENTS.register("suspicion", () -> AttachmentType.<Integer>builder(() -> 0)
                    .serialize(Codec.INT)
                    .copyOnDeath()
                    .build());

    // ------------------------------------------------------------------
    // Access
    // ------------------------------------------------------------------

    public static int getFavor(Player player) {
        return player.getData(FAVOR.get());
    }

    public static int getSuspicion(Player player) {
        return player.getData(SUSPICION.get());
    }

    /**
     * Adds Favor, scaled by who the character is.
     *
     * The scale is applied here rather than at each call site so that Presence
     * and the racial modifiers cannot be forgotten by whichever handler grants
     * the points next. Losses are never scaled: a character good at earning
     * regard should not also be good at keeping it after they stop earning it,
     * and scaling a negative by 1.5 would make Presence a penalty on decay.
     */
    public static boolean addFavor(Player player, int delta) {
        if (delta > 0) {
            delta = scale(delta, ElysiumPassives.favorScale(player));
        }
        int before = getFavor(player);
        int after = clamp(before + delta);
        if (after == before) {
            return false;
        }
        player.setData(FAVOR.get(), after);
        announce(player, before, after, true);
        return true;
    }

    public static boolean addSuspicion(Player player, int delta) {
        if (delta > 0) {
            delta = scale(delta, ElysiumPassives.suspicionScale(player));
        }
        int before = getSuspicion(player);
        int after = clamp(before + delta);
        if (after == before) {
            return false;
        }
        player.setData(SUSPICION.get(), after);
        announce(player, before, after, false);
        return true;
    }

    /**
     * Rounds a scaled gain without ever rounding it away.
     *
     * A +1 incidental kill scaled by 0.5 would floor to zero, which would mean
     * an Unsworn character could never gain Favor from ordinary fighting at
     * all. Anything that was going to be a gain stays one.
     */
    private static int scale(int delta, float factor) {
        return Math.max(1, Math.round(delta * factor));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX, value));
    }

    // ------------------------------------------------------------------
    // Bands
    // ------------------------------------------------------------------

    /** The top band — Exalted on the Favor side, Hunted on the Suspicion side. */
    public static final int BAND_HUNTED = 3;

    /** 0 = below notice, 1, 2, 3 = the top band. */
    public static int bandOf(int value) {
        if (value >= 75) {
            return BAND_HUNTED;
        }
        if (value >= 50) {
            return 2;
        }
        if (value >= NOTICE) {
            return 1;
        }
        return 0;
    }

    public static Component favorBand(int favor) {
        String key = switch (bandOf(favor)) {
            case 3 -> "exalted";
            case 2 -> "favoured";
            case 1 -> "recognised";
            default -> "unknown";
        };
        return Component.translatable("elysium.favor." + key).withStyle(ChatFormatting.GOLD);
    }

    public static Component suspicionBand(int suspicion) {
        String key = switch (bandOf(suspicion)) {
            case 3 -> "hunted";
            case 2 -> "marked";
            case 1 -> "noted";
            default -> "clear";
        };
        return Component.translatable("elysium.suspicion." + key).withStyle(ChatFormatting.RED);
    }

    /**
     * Told only when a band changes, never on every point. A meter that talks
     * constantly stops being read.
     */
    private static void announce(Player player, int before, int after, boolean isFavor) {
        if (bandOf(before) == bandOf(after)) {
            return;
        }
        player.displayClientMessage(
                isFavor ? favorBand(after) : suspicionBand(after), true);
    }

    /** A one-line readout, shown when a player opens an Elysium workstation. */
    public static Component report(Player player) {
        return Component.translatable("elysium.standing.report",
                getFavor(player), favorBand(getFavor(player)),
                getSuspicion(player), suspicionBand(getSuspicion(player)));
    }

    // ------------------------------------------------------------------
    // Loot: Favor sets the tier, Suspicion sets the count
    // ------------------------------------------------------------------

    /**
     * Which shelf a reward comes off. 0 is raw material, 3 is a catalyst or a
     * weapon.
     */
    public static int lootTier(int favor) {
        return bandOf(favor);
    }

    /**
     * How many of it. One at a clean record, five when the Empire is actively
     * hunting you — the compensation for the company you are keeping.
     */
    public static int lootAmount(int suspicion) {
        return 1 + Math.min(4, suspicion / 25);
    }

    /**
     * The chance an ordinary hostile pays out at all. The mod's own faction
     * mobs always do; without this gate every skeleton in the world would be a
     * loot pinata.
     */
    public static float incidentalDropChance(int favor, int suspicion) {
        if (favor < NOTICE && suspicion < NOTICE) {
            return 0.0F;
        }
        return 0.10F;
    }

    // ------------------------------------------------------------------
    // Spawning: each meter summons its own side
    // ------------------------------------------------------------------

    /** The chance, per roll, that the Empire sends someone after you. */
    public static float empireSpawnChance(int suspicion) {
        return spawnChance(suspicion);
    }

    /** The chance, per roll, that an Unsworn raider turns up near you. */
    public static float unswornSpawnChance(int favor) {
        return spawnChance(favor);
    }

    /**
     * The chance, per roll, that a faction sends someone after you.
     *
     * One curve for both meters, so neither loop is mechanically privileged —
     * the difference between them is only in what each pays out.
     */
    public static float spawnChance(int value) {
        if (value < NOTICE) {
            return 0.0F;
        }
        return Math.min(0.40F, (value - NOTICE) / 200.0F);
    }

    /** How many of one faction may shadow a single player at a time. */
    public static int spawnCap(int value) {
        return switch (bandOf(value)) {
            case 3 -> 4;
            case 2 -> 3;
            case 1 -> 2;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------
    // Decay
    // ------------------------------------------------------------------

    /**
     * Ticks between a meter losing one point. Both decay at the same rate, so
     * neither loop is permanent — a full meter takes roughly two and a half
     * hours of play to fall back to the notice threshold if you stop feeding
     * it.
     */
    public static final int DECAY_INTERVAL = 2400;

    /**
     * Decay stops at the notice threshold rather than at zero.
     *
     * Below {@link #NOTICE} nothing is happening: no faction mob is dispatched,
     * no ordinary hostile pays out, and neither meter is doing anything a
     * player can see. Bleeding that range costs one point every two minutes,
     * which is faster than an ordinary night of fighting can refill it — an
     * incidental kill is worth one point one time in three. The result was a
     * meter that could not be climbed out of at any realistic pace, which put
     * the entire loot loop, and the neutronium a player might earn from it,
     * behind a mob farm.
     *
     * So the climb to Recognised is a flat one, and the pressure to keep
     * acting starts where the rewards do.
     */
    public static boolean decays(int value) {
        return value > NOTICE;
    }
}
