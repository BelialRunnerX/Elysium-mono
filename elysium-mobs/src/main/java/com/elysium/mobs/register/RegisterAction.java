package com.elysium.mobs.register;

import com.elysium.mobs.ElysiumMobs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Optional;
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
 * All five rows are implemented, and the failure row is split between two
 * places on purpose. The <em>striking</em> of a claim belongs to the Registry
 * Beacon block, which does it whenever the beacon is destroyed by any hand —
 * §E.1's invariant then holds for free, because that code touches no blocks and
 * so <blockquote>beacon destruction releases claim but does not erase
 * constructed world.</blockquote> What belongs here is only the consequence for
 * an action already in flight: an action against a claim that no longer exists
 * ends, paying nothing.
 *
 * The ordinary failure — you die, or you leave — still ends the action paying
 * nothing too, and is the only failure available to a player who never claimed
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
                             long deadline, Optional<Target> target) {

    /**
     * The claim an action is against.
     *
     * §14.3 is titled "Register Actions and <b>base sieges</b>", and until
     * claims existed the second half of that was not implemented: waves spawned
     * around the player wherever they happened to be, so walking into the
     * wilderness took the siege with you and your base was scenery. The
     * ten-minute warning is ten minutes to prepare <em>somewhere</em>, and
     * without a target there was nowhere for it to be.
     *
     * The dimension is stored beside the position because a player can be
     * elsewhere when the action runs, and {@code ElysiumClaims} is per
     * dimension — asking the Nether whether a claim in the Overworld still
     * exists gets "no", which would read as the beacon having been destroyed
     * and end the action as a failure the player never saw happen.
     */
    public record Target(String dimension, BlockPos anchor) {

        public static final Codec<Target> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.STRING.fieldOf("dimension").forGetter(Target::dimension),
                        BlockPos.CODEC.fieldOf("anchor").forGetter(Target::anchor)
                ).apply(instance, Target::new));

        public static Target of(ServerLevel level, BlockPos anchor) {
            return new Target(level.dimension().location().toString(), anchor);
        }

        /** Whether this level is the one holding the claim. */
        public boolean isIn(ServerLevel level) {
            return level.dimension().location().toString().equals(dimension);
        }
    }

    /**
     * An action against nobody's base in particular.
     *
     * Kept as a constructor rather than pushed onto every call site, because
     * most of them are transitions that carry the existing target forward and
     * would otherwise all have to repeat it. It is also what an action loaded
     * from a save written before targets existed comes back as — see the
     * optional field in the codec.
     */
    public RegisterAction(int phase, int band, int wave, long nextTick,
                          long deadline) {
        this(phase, band, wave, nextTick, deadline, Optional.empty());
    }

    /** The same action, still against the same place. */
    public RegisterAction at(int phase, int wave, long nextTick) {
        return new RegisterAction(phase, band, wave, nextTick, deadline, target);
    }

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
                    Codec.LONG.fieldOf("deadline").forGetter(RegisterAction::deadline),
                    // Optional, so a save written before sieges had a target
                    // loads as an untargeted action rather than failing to
                    // decode — which for an attachment means the player
                    // silently loses whatever action was running, and for a
                    // player mid-siege means the waves simply stop.
                    Target.CODEC.optionalFieldOf("target")
                            .forGetter(RegisterAction::target)
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
