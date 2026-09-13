package com.elysium.mobs.register;

import com.elysium.mobs.ElysiumMobs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * A Register Action: the Empire coming to collect.
 *
 * <h2>What the specification asks for</h2>
 *
 * Fourth Edition §14.3 gives this as a table, and the table is the design:
 *
 * <pre>
 *   BAND     EVENT                    BASE CONSEQUENCE
 *   Noted    patrols                  interruption, warning, low-cost defense
 *   Marked   3-wave register action   grunts + elite; announced ~10 minutes early
 *   Hunted   5-wave register action   elite pressure ending with Praetor
 *   Failure  beacon destroyed         claim is struck; structures remain
 *   Success  action cleared           large reward; Suspicion returns toward
 *                                     structural floor
 * </pre>
 *
 * All five rows are implemented. The failure row was the last, and it is the
 * one this class does <em>not</em> implement, which is the point: it is written
 * against a <b>claim</b> — a territorial structure with a beacon, from §14.2 —
 * and it is the Registry Beacon block that strikes the claim when it is
 * destroyed, by any hand, including a creeper from one of these waves. Nothing
 * here needed to know about it. §E.1's invariant holds for free for the same
 * reason: the code that strikes a claim touches no blocks, so
 * <blockquote>beacon destruction releases claim but does not erase constructed
 * world.</blockquote>
 *
 * The ordinary failure — you die, or you leave — still ends the action paying
 * nothing, and is the only failure available to a player who never claimed
 * anything.
 *
 * <h2>Why Suspicion only</h2>
 *
 * A register action is enforcement. Favor is the Unsworn's opinion of you and
 * has no enforcement arm — being popular with a diaspora does not get you
 * besieged. So this reads one meter, unlike the court, which reads both.
 *
 * <h2>The announcement is the mechanic</h2>
 *
 * §14.3 says a Marked action is "announced ~10 minutes early", and that clause
 * is doing more work than it looks. A siege that arrives unannounced is a thing
 * that happens to you; a siege ten minutes out is a thing you prepare for, and
 * preparing is where the building, the gear and the stockpile all suddenly
 * matter at once. It is the one line in the table that turns three other
 * systems on.
 *
 * So the warning is not a courtesy message — it is a phase, with its own
 * duration, that the player is expected to spend.
 */
public record RegisterAction(int phase, int band, int wave, long nextTick,
                             long deadline) {

    // --- Phases ---------------------------------------------------------
    //
    // Integers rather than an enum because this is serialised into a save and
    // an enum's ordinal is a promise that the declaration order never changes.
    // The same reasoning EnvoyKind gives for storing an id string; here the
    // values are few enough that named constants are clearer than text.

    /** Nothing scheduled. */
    public static final int IDLE = 0;
    /** Announced, counting down. The player knows and can prepare. */
    public static final int ANNOUNCED = 1;
    /** Waves are arriving. */
    public static final int RUNNING = 2;

    public static final RegisterAction NONE =
            new RegisterAction(IDLE, 0, 0, 0L, 0L);

    public static final Codec<RegisterAction> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("phase").forGetter(RegisterAction::phase),
                    Codec.INT.fieldOf("band").forGetter(RegisterAction::band),
                    Codec.INT.fieldOf("wave").forGetter(RegisterAction::wave),
                    Codec.LONG.fieldOf("next").forGetter(RegisterAction::nextTick),
                    Codec.LONG.fieldOf("deadline").forGetter(RegisterAction::deadline)
            ).apply(instance, RegisterAction::new));

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,
                    ElysiumMobs.MODID);

    /**
     * The player's pending or running action.
     *
     * <b>Not {@code copyOnDeath}.</b> Dying during an action ends it — that is
     * the failure state §14.3 leaves to the ordinary one, and carrying a
     * half-finished siege through a respawn would mean the waves resume around
     * a player who has just lost everything they prepared with.
     *
     * Persisted rather than kept in a server-side map because the announcement
     * phase is ten minutes long and a player who logs out inside it should not
     * be able to erase an Imperial action by doing so.
     */
    public static final Supplier<AttachmentType<RegisterAction>> ACTIVE =
            ATTACHMENTS.register("register_action",
                    () -> AttachmentType.<RegisterAction>builder(() -> NONE)
                            .serialize(CODEC)
                            .build());

    // --- The table ------------------------------------------------------

    /**
     * How many waves a band sends. §14.3: three at Marked, five at Hunted.
     *
     * Zero for anything below, which is what makes "Noted sends patrols, not an
     * action" true rather than merely described — a band with no waves cannot
     * schedule one.
     */
    public static int wavesFor(int band) {
        return switch (band) {
            case 3 -> 5;
            case 2 -> 3;
            default -> 0;
        };
    }

    /** Whether this band sends patrols rather than an action. §14.3, Noted. */
    public static boolean patrolsAt(int band) {
        return band == 1;
    }

    /** §14.3: "announced ~10 minutes early". */
    public static final int WARNING_TICKS = 20 * 60 * 10;

    /** How long between waves once it starts. */
    public static final int WAVE_TICKS = 20 * 45;

    /**
     * How long the whole action may run before the Empire gives up.
     *
     * Not in the specification, and needed anyway: without it a player who
     * climbs a pillar and waits holds an action open forever, and every
     * subsequent check sees one already running and schedules nothing. A bound
     * is what stops a stalled action from disabling the whole system.
     */
    public static final int ABANDON_TICKS = 20 * 60 * 20;

    /**
     * The Suspicion a cleared action leaves you at — §14.3's "structural
     * floor".
     *
     * <h3>This used to be the wrong number, and the mistake is instructive</h3>
     *
     * The first version returned the bottom of the band that sent the action:
     * 75 at Hunted, 50 at Marked. It read well — "the Empire is satisfied but
     * has not forgotten" — and it was a stand-in, because §14.2's claims did
     * not exist yet and something had to stand for "structural".
     *
     * It is a stand-in that inverts the reward. A Hunted player who survives
     * five waves ending in a Praetor was returned to 75, which is still Hunted,
     * which schedules the next five-wave action on the very next check. The
     * largest fight in the mod paid out its loot and then immediately bought
     * another one. Nothing in the table says that, and no player would read
     * "large reward" and expect it.
     *
     * The word "structural" was always pointing at the thing it now points at:
     * §A.2's claim floor, computed from what you have built. So a cleared
     * action returns you to <em>your own</em> floor — high if you hold a
     * fortress, low if you hold nothing — and never below the notice
     * threshold, since an Empire that files you at zero for surviving a siege
     * is not filing you at all.
     */
    public static int floorAfter(Player player) {
        return Math.max(ElysiumStanding.NOTICE, ElysiumStanding.floorFor(player));
    }

    public boolean isIdle() {
        return phase == IDLE;
    }

    public boolean isRunning() {
        return phase == RUNNING;
    }

    /** The last wave of an action at this band. */
    public boolean isFinalWave() {
        return wave >= wavesFor(band);
    }

    public static RegisterAction get(Player player) {
        return player.getData(ACTIVE.get());
    }

    public static void set(Player player, RegisterAction action) {
        player.setData(ACTIVE.get(), action);
    }

    public static void clear(Player player) {
        player.setData(ACTIVE.get(), NONE);
    }
}
