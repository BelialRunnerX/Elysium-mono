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
        return addFavor(player, delta, CAUSE_UNKNOWN);
    }

    public static boolean addFavor(Player player, int delta, String cause) {
        if (delta > 0) {
            delta = scale(delta, ElysiumPassives.favorScale(player));
        }
        int before = getFavor(player);
        int after = clamp(before + delta);
        if (after == before) {
            return false;
        }
        player.setData(FAVOR.get(), after);
        file(player, after - before, cause, false);
        announce(player, before, after, true);
        return true;
    }

    public static boolean addSuspicion(Player player, int delta) {
        return addSuspicion(player, delta, CAUSE_UNKNOWN);
    }

    public static boolean addSuspicion(Player player, int delta, String cause) {
        if (delta > 0) {
            delta = scale(delta, ElysiumPassives.suspicionScale(player));
        }
        int before = getSuspicion(player);
        int after = clamp(before + delta);
        if (after == before) {
            return false;
        }
        player.setData(SUSPICION.get(), after);
        file(player, after - before, cause, true);
        announce(player, before, after, false);
        return true;
    }

    // ------------------------------------------------------------------
    // The file
    // ------------------------------------------------------------------
    //
    // §14.5 asks for an archive that reveals "how much the Empire currently
    // knows", and §21 lists "recent causes" among what it shows. Recording the
    // cause here rather than at each call site is the same argument the Presence
    // scale above makes: a handler that grants points cannot forget to file
    // them, because filing is not something a handler does.
    //
    // The causes are declared as constants rather than passed as loose strings
    // so that a typo is a compile error instead of an archive line reading
    // "elysium.cause.mine_ritch_ore".

    /** Killing one of the Empire's own. §14.2. */
    public static final String CAUSE_EMPIRE_KILL = "empire_kill";
    /** Killing a sanctioned Unsworn target. §14.2. */
    public static final String CAUSE_UNSWORN_KILL = "unsworn_kill";
    /** An ordinary hostile that happened to count. §14.2, "incidental". */
    public static final String CAUSE_INCIDENTAL = "incidental";
    /** Extraction. §14.2 taxes rich ore at double. */
    public static final String CAUSE_ORE = "ore";
    public static final String CAUSE_RICH_ORE = "rich_ore";
    /** Socketing or reforging. §14.2. */
    public static final String CAUSE_CRAFT = "craft";
    /** Ascending gear — §14.2 calls it "high-visibility progression". */
    public static final String CAUSE_ASCEND = "ascend";
    /** Planting a Registry Beacon, or building inside a claim. §14.2. */
    public static final String CAUSE_CLAIM = "claim";
    /** Time passing. The only cause that is always negative. */
    /**
     * An unregistered works, found. {@code ElysiumWorksHandler}.
     *
     * Its own cause rather than {@link #CAUSE_CLAIM}: a claim is a file you
     * opened, and this is one the Empire opened for you. The archive should
     * say which.
     */
    public static final String CAUSE_WORKS = "works";
    public static final String CAUSE_DECAY = "decay";
    /** Clearing a register action. §14.3's success row. */
    public static final String CAUSE_CLEARED = "cleared";
    /** Trading with the court. */
    public static final String CAUSE_COURT = "court";
    /**
     * §27.1's amnesty row: pressure discharged by arrangement.
     *
     * Its own cause rather than folded into {@link #CAUSE_COURT}, because the
     * archive is read to answer "why am I Marked" and "you bought your way
     * down 25" is a different answer from "you traded with an envoy". They are
     * also the only two entries that can appear in the same session with
     * opposite signs.
     */
    public static final String CAUSE_AMNESTY = "amnesty";

    /**
     * §14.5: extraction that an inspection post was reporting.
     *
     * Its own cause because the archive's whole job is answering "why is this
     * number moving faster than it was". Filing a watched vein as ordinary ore
     * would show the player a +8 next to a +2 for the same block and explain
     * nothing.
     */
    public static final String CAUSE_INSPECTED = "inspected";

    /**
     * §27.1's "resist": destroying Imperial equipment.
     *
     * The Empire's own row calls resistance one of the legitimate responses to
     * an inspection, and it charges for it. Separate from a kill because it is
     * property rather than people, and a player reading their file should be
     * able to tell which they did.
     */
    public static final String CAUSE_RESISTED = "resisted";

    /**
     * What an unattributed change is filed as.
     *
     * The two-argument overloads above route here rather than skipping the
     * ledger, so a caller that has not been taught to name its cause still
     * shows up in the archive as something that happened. An unexplained entry
     * is worth more than a missing one: a player who sees "+4, unattributed"
     * knows to look, and a player who sees nothing concludes the archive is
     * broken.
     */
    public static final String CAUSE_UNKNOWN = "unknown";

    private static void file(Player player, int delta, String cause, boolean suspicion) {
        if (player.level().isClientSide()) {
            return;
        }
        ElysiumFile.record(player, player.level().getGameTime(), delta, cause,
                suspicion);
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

    /**
     * The same band, named from the Favor side.
     *
     * One value, two names, because the two meters are one number wearing two
     * fictions and the specification's Court table gates Aurelia at "Exalted"
     * while gating Elysomnion at "Hunted". A Favor envoy declaring its gate as
     * {@code BAND_HUNTED} is not wrong, it just reads as though the Queen were
     * waiting for the Code to take an interest in you, which is the opposite of
     * what she reads.
     *
     * Deliberately an alias rather than a second constant with its own value:
     * if the bands are ever renumbered, one of two independent constants would
     * be updated and the other would not.
     */
    public static final int BAND_EXALTED = BAND_HUNTED;

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

    /**
     * The thresholds {@link #bandOf} switches on, lowest first.
     *
     * Exposed so that a readout can say "nineteen from Marked" rather than
     * leaving a player to work out where the edges are by crossing them. The
     * array is the same set of numbers bandOf uses and there is a check that
     * says so — two copies of a threshold is two chances for a readout to
     * promise something the meter does not do.
     */
    public static final int[] BAND_THRESHOLDS = {NOTICE, 50, 75};

    /** How far to the next band, or 0 at the top. */
    public static int toNextBand(int value) {
        for (int threshold : BAND_THRESHOLDS) {
            if (value < threshold) {
                return threshold - value;
            }
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

    /**
     * The Suspicion this player cannot fall below.
     *
     * §14.2 lists claiming as the one entry that grants no points and instead
     * puts a permanent floor under the meter: you cannot be quietly forgotten
     * while standing on something with your name on it. Everything else on that
     * table decays.
     *
     * Returned as a number rather than folded into {@link #decays} because two
     * callers want it for different reasons — decay stops at it, and clearing a
     * register action returns you <em>to</em> it — and a boolean can only serve
     * the first.
     *
     * Zero for a player holding no claims, which is every player until they
     * plant a beacon and every world where no mod supplies one.
     */
    public static int floorFor(Player player) {
        int floor = com.elysium.lib.claim.ElysiumClaims.floorFor(player);
        for (FloorSource source : FLOOR_SOURCES) {
            floor = Math.max(floor, Math.max(0, source.floorFor(player)));
        }
        return floor;
    }

    /**
     * Something other than a claim that the Empire has noticed.
     *
     * A claim is this library's own answer to "you cannot be quietly forgotten
     * while standing on something with your name on it", and it is the only
     * one the library ships. But a beacon is not the only way to put your name
     * on a place: a mod that adds settlements, towns or territory has exactly
     * the same fact to state, and no way to state it without this.
     *
     * <p>The highest floor wins rather than the sum. Two names on one place is
     * still one place, and a player with a beacon inside their own town should
     * not be noticed twice for it.
     */
    @FunctionalInterface
    public interface FloorSource {
        int floorFor(Player player);
    }

    private static final java.util.List<FloorSource> FLOOR_SOURCES =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Adds a source of Suspicion floor.
     *
     * Called during startup by whatever mod owns the thing being noticed.
     * Copy-on-write because registration happens on the mod thread and the
     * floor is read on the decay tick.
     */
    public static void addFloorSource(FloorSource source) {
        if (source != null) {
            FLOOR_SOURCES.add(source);
        }
    }

    /** How many sources are installed, for a validator or a debug command. */
    public static int floorSourceCount() {
        return FLOOR_SOURCES.size();
    }

    /**
     * Whether Suspicion decays for this player at this value.
     *
     * The claim floor, then the notice threshold. A claim below the notice
     * threshold — a beacon with almost nothing built around it, floor eight —
     * changes nothing, which is right: eight points is not a file, it is a
     * form.
     */
    public static boolean decays(Player player, int value) {
        return decays(value) && value > floorFor(player);
    }
}
