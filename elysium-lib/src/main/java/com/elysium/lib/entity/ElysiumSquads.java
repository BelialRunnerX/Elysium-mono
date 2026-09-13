package com.elysium.lib.entity;

import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Roaming bands: the small squads that turn up whether or not you have done
 * anything.
 *
 * <h2>What was missing</h2>
 *
 * Standing already summons opposition — {@code ElysiumStandingHandler} sends
 * one mob every ten seconds at a chance drawn from your meters. Two things are
 * wrong with that as the <em>only</em> source of company:
 *
 * <ol>
 *   <li>{@link ElysiumStanding#spawnChance} is flat zero below the notice
 *       threshold, so a player who has not yet earned either meter meets
 *       nothing at all. The loops are supposed to feed themselves, and a loop
 *       with no first rung does not start.</li>
 *   <li>It sends <b>one</b>. One mob is an encounter you walk past. A squad is
 *       a fight you have to decide about — whether to take it, where to stand,
 *       whether you brought enough.</li>
 * </ol>
 *
 * So this is the other half of the pair: dispatch is standing's answer, and a
 * roaming band is the world's. Dispatch scales with what you have done; this
 * happens because the roads are not safe.
 *
 * <h2>"Relatively consistently" is a scheduled gap, not a die roll</h2>
 *
 * The obvious implementation rolls a die on a cadence. It is wrong for what is
 * being asked here, and wrong in a way that is invisible until someone plays
 * it: a Bernoulli trial has a long tail in both directions. At a rate that
 * averages one squad every three minutes, one player in twenty goes ten minutes
 * with nothing while another meets three bands back to back. Both of those read
 * as a broken feature rather than as variance.
 *
 * A <em>bounded</em> gap — the next arrival scheduled for somewhere in a window
 * — gives the same average with none of the tail. There is always a next squad,
 * it is never immediate, and it is never far away. That is what "consistently"
 * has to mean if it means anything, and it costs one long per player to say.
 *
 * <h2>What the library is allowed to know</h2>
 *
 * Nothing about any particular creature. A squad is built out of
 * {@link ElysiumBestiary} entries by faction and role, exactly as a dungeon
 * room is, so a squad is made of whatever mobs are installed — and with no mob
 * mod installed, nothing is spawned and no error is raised.
 *
 * @see ElysiumBestiary
 */
public final class ElysiumSquads {

    private ElysiumSquads() {
    }

    // ------------------------------------------------------------------
    // The mark
    // ------------------------------------------------------------------

    /**
     * The scoreboard tag every squad member carries.
     *
     * A vanilla entity tag rather than a data attachment, and deliberately so.
     * It needs to be exactly three things: persisted across a save, readable
     * without a registry lookup from code that runs in the despawn path, and
     * <em>visible to a player debugging their own world</em> — {@code /tag @e
     * list} answers "why is this thing following me" without a special command
     * or a mod menu. An attachment would give the first two and cost a
     * registration; the third is free here and worth having.
     *
     * Two systems read it, and that they can share one mark is the sign it is
     * the right one:
     *
     * <ul>
     *   <li><b>Despawning.</b> Elysium creatures are normally permanent,
     *       because a dungeon boss that evaporated while the player walked to
     *       it would empty the room. A roaming band is the opposite case: it
     *       arrives every couple of minutes forever, and a permanent one would
     *       strew a three-hour session with a hundred and eighty abandoned
     *       mobs. Ambient members consent to the ordinary vanilla despawn
     *       rules; nothing else does.</li>
     *   <li><b>Event clearance.</b> Anything that counts nearby hostiles to
     *       decide whether an event is over — a register action's waves, say —
     *       has to be able to tell the event's own mobs from the traffic, or a
     *       band that wandered past holds the event open indefinitely.</li>
     * </ul>
     */
    public static final String TAG = "elysium_squad";

    /** Whether this entity arrived as part of a roaming band. */
    public static boolean isAmbient(Entity entity) {
        return entity.getTags().contains(TAG);
    }

    // ------------------------------------------------------------------
    // Holding them off
    // ------------------------------------------------------------------

    private static final List<Predicate<Player>> SUPPRESSORS = new ArrayList<>();

    /**
     * Registers a reason not to send a band at a particular player right now.
     *
     * The library cannot know what else is happening to somebody — it has no
     * idea that a siege is announced, that a dungeon is being run, or that a
     * cutscene is playing. Rather than grow a flag for each, it asks: anything
     * that owns a player's attention registers a predicate and gets quiet while
     * it holds.
     *
     * A suppressor should be cheap. It is consulted on a fixed cadence per
     * player, not per tick, but it is still consulted for every player on the
     * server.
     */
    public static void addSuppressor(Predicate<Player> suppressor) {
        SUPPRESSORS.add(suppressor);
    }

    /** Whether anything has asked for quiet around this player. */
    public static boolean isSuppressed(Player player) {
        for (Predicate<Player> suppressor : SUPPRESSORS) {
            if (suppressor.test(player)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // The table
    // ------------------------------------------------------------------

    /**
     * The shortest a gap can be at a clean record, in ticks. Two minutes.
     *
     * The floor matters more than the average. Whatever else the dice do, a
     * player is never jumped twice inside two minutes at band zero, which is
     * what keeps "consistent" from becoming "relentless".
     */
    public static final int GAP_BASE = 20 * 60 * 2;

    /** How much longer than the floor a gap may be. Also two minutes. */
    public static final int GAP_SPREAD = 20 * 60 * 2;

    /**
     * Ticks until the next band arrives.
     *
     * Standing shortens the gap rather than raising a probability, so the two
     * meters do here what they do everywhere else — more company, sooner —
     * without ever reintroducing the tail that a probability brings back.
     *
     * <pre>
     *   band 0   2:00 - 4:00   (mean 3:00)
     *   band 1   1:30 - 3:00   (mean 2:15)
     *   band 2   1:00 - 2:00   (mean 1:30)
     *   band 3   0:30 - 1:00   (mean 0:45)
     * </pre>
     *
     * Band three is a player the Empire is hunting or the Unsworn revere, and
     * forty-five seconds between bands is meant to be close to unlivable. That
     * is the top of a meter doing its job: it is a place you visit to collect,
     * not a place you live.
     */
    public static int gapFor(int band, RandomSource random) {
        int clamped = Math.max(0, Math.min(ElysiumStanding.BAND_HUNTED, band));
        long full = GAP_BASE + random.nextInt(GAP_SPREAD);
        return (int) (full * (4L - clamped) / 4L);
    }

    /** The smallest a band ever is. Two is a squad; one is a wanderer. */
    public static final int MIN_SIZE = 2;

    /**
     * How many turn up.
     *
     * "Small" is the whole brief, so the ceiling moves by one for every two
     * bands and stops at four. A band of four levelled mobs is already a real
     * fight; anything more stops being ambient and starts being an event, and
     * events are announced.
     */
    public static int sizeFor(int band, RandomSource random) {
        int clamped = Math.max(0, Math.min(ElysiumStanding.BAND_HUNTED, band));
        int max = MIN_SIZE + (clamped + 1) / 2;
        return MIN_SIZE + random.nextInt(max - MIN_SIZE + 1);
    }

    /** The chance a band is led by an elite rather than being all grunts. */
    public static float eliteChance(int band) {
        return Math.max(0, Math.min(ElysiumStanding.BAND_HUNTED, band)) * 0.25F;
    }

    /**
     * Whose band this is.
     *
     * Weighted, not gated, and the weights say something:
     *
     * <pre>
     *   Unsworn   4 + favor
     *   Empire    1 + suspicion
     * </pre>
     *
     * At a clean record that is four to one for the Unsworn, and the asymmetry
     * is the fiction: <b>raiders do not need a reason to rob you, and the
     * Empire does.</b> A player who has done nothing still meets the roads;
     * they just do not meet enforcement, because there is nothing to enforce.
     *
     * Both meters pull their own side up, so riding either loop brings more of
     * that faction — the same shape {@link ElysiumStanding} describes — and
     * riding both brings a lot of everyone.
     */
    public static ElysiumFaction factionFor(int favor, int suspicion,
                                            RandomSource random) {
        int unsworn = 4 + Math.max(0, favor);
        int empire = 1 + Math.max(0, suspicion);
        return random.nextInt(unsworn + empire) < unsworn
                ? ElysiumFaction.UNSWORN
                : ElysiumFaction.EMPIRE;
    }

    /**
     * How many ambient members may shadow one player before the world stops
     * sending more.
     *
     * Without a cap the feature is a leak with a clock on it: a player who runs
     * rather than fights accumulates a band every couple of minutes and never
     * sheds one. Six is about two outstanding bands — enough that fleeing has a
     * cost, bounded enough that it cannot snowball into an unwinnable train.
     *
     * <h3>The cap and the despawn rule hold each other up</h3>
     *
     * Simulated over an hour, a player who never fights back reaches the cap in
     * about twelve minutes and then receives nothing further. That is the cap
     * working — but on its own it would also be the feature <em>ending</em>,
     * permanently, for anybody who prefers to avoid combat: six immortal mobs
     * would trail them for the rest of the save and every later band would be
     * refused. It only stays a pause rather than an ending because ambient
     * members are the one Elysium creature allowed to despawn, so outrunning a
     * band actually sheds it and the flow resumes.
     *
     * Neither half is sufficient. A cap with permanent mobs switches the
     * feature off; despawning with no cap lets a fleeing player tow an
     * arbitrarily large train. Change either and check the other.
     */
    public static final int NEARBY_CAP = 6;

    /** How far out that cap is counted. */
    public static final double CROWD_RADIUS = 64.0D;

    // ------------------------------------------------------------------
    // Placement
    // ------------------------------------------------------------------

    /** Never closer than this, so a band is never simply dropped on someone. */
    public static final double MIN_RANGE = 24.0D;

    /** Never further than this, or they never arrive. */
    public static final double MAX_RANGE = 40.0D;

    /** How far a member may stand from the band's own anchor. */
    private static final int HUDDLE = 5;

    /**
     * Sends a band at this player, if anything should be sent.
     *
     * Everything that could refuse refuses quietly and returns zero: peaceful
     * difficulty, a creative or spectating player, a suppressor holding, the
     * crowd cap, no bestiary entries installed, nowhere to stand. A caller
     * treats zero as "not this time" and reschedules — none of these is an
     * error, and none of them should print.
     *
     * @return how many were placed
     */
    public static int send(ServerLevel level, Player player, RandomSource random) {
        return send(level, player, random, null);
    }

    /**
     * The same, from a faction the caller names.
     *
     * Used when a band is a <em>consequence</em> rather than traffic — the
     * Empire answering somebody who broke its equipment, say. Weighting by the
     * meters would be wrong there: it is not a question of who happens to be
     * around, it is a question of who was wronged.
     *
     * Everything else still applies, including the suppressors and the crowd
     * cap. A caller sending a band as a consequence still does not get to drop
     * one on a player who already has six, or through an announced siege — the
     * consequence is allowed to be refused, and reports it by returning zero.
     */
    public static int send(ServerLevel level, Player player, RandomSource random,
                           @Nullable ElysiumFaction from) {
        // Peaceful would discard them on their first despawn check anyway, but
        // spawning a band that vanishes a tick later still costs the chunk
        // loads and still puts three entity-join packets on the wire.
        if (level.getDifficulty() == Difficulty.PEACEFUL
                || player.isSpectator()
                || player.isCreative()) {
            return 0;
        }
        if (isSuppressed(player) || crowded(level, player)) {
            return 0;
        }
        if (ElysiumBestiary.isEmpty()) {
            return 0;
        }

        int favor = ElysiumStanding.getFavor(player);
        int suspicion = ElysiumStanding.getSuspicion(player);
        ElysiumFaction faction = from != null
                ? from
                : factionFor(favor, suspicion, random);
        int band = ElysiumStanding.bandOf(
                faction == ElysiumFaction.EMPIRE ? suspicion : favor);

        BlockPos anchor = anchor(level, player, random);
        if (anchor == null) {
            return 0;
        }

        // One level for the whole band, worked out once at the anchor. Members
        // rolled independently would come out at different levels depending on
        // where each happened to stand, and a squad whose members are visibly
        // uneven reads as a bug rather than as a mixed patrol.
        int mobLevel = ElysiumScaling.levelFor(level, anchor, faction);

        int size = sizeFor(band, random);
        boolean elite = random.nextFloat() < eliteChance(band);
        int placed = 0;

        for (int i = 0; i < size; i++) {
            ElysiumBestiary.Role role = (elite && i == 0)
                    ? ElysiumBestiary.Role.ELITE
                    : ElysiumBestiary.Role.GRUNT;

            BlockPos where = footing(level, anchor, random);
            if (where == null) {
                continue;
            }

            Mob mob = ElysiumBestiary.spawn(level, where, faction, role, random, mobLevel);
            if (mob == null && role == ElysiumBestiary.Role.ELITE) {
                // This faction has no elite registered. A band that silently
                // came up one short because of a missing role would be a
                // content gap showing through as a difficulty change.
                mob = ElysiumBestiary.spawn(level, where, faction,
                        ElysiumBestiary.Role.GRUNT, random, mobLevel);
            }
            if (mob == null) {
                continue;
            }

            mob.moveTo(where.getX() + 0.5D, where.getY(), where.getZ() + 0.5D,
                    random.nextFloat() * 360.0F, 0.0F);
            mob.addTag(TAG);
            // Deliberately NOT setPersistenceRequired: see TAG. A roaming band
            // is the one kind of Elysium creature that is allowed to be
            // forgotten, and it has to be, or the world fills with them.
            level.addFreshEntity(mob);
            mob.setTarget(player);
            placed++;
        }
        return placed;
    }

    /** Ambient members already shadowing this player. */
    private static int nearby(ServerLevel level, Player player) {
        List<Mob> found = level.getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(CROWD_RADIUS),
                mob -> mob.isAlive() && isAmbient(mob));
        return found.size();
    }

    private static boolean crowded(ServerLevel level, Player player) {
        return nearby(level, player) >= NEARBY_CAP;
    }

    /**
     * Where the band as a whole comes from: out of arm's reach, inside earshot.
     *
     * Chosen by angle and distance rather than by a box, so bands arrive evenly
     * from all sides — a box samples its corners forty percent more often than
     * its edges, which over a session reads as "they always come from the
     * north-east".
     */
    @Nullable
    private static BlockPos anchor(ServerLevel level, Player player,
                                   RandomSource random) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = MIN_RANGE + random.nextDouble() * (MAX_RANGE - MIN_RANGE);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            BlockPos found = ground(level, x, Mth.floor(player.getY()), z);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Somewhere within the huddle to actually stand, or null. */
    @Nullable
    private static BlockPos footing(ServerLevel level, BlockPos anchor,
                                    RandomSource random) {
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = anchor.getX() + random.nextInt(HUDDLE * 2 + 1) - HUDDLE;
            int z = anchor.getZ() + random.nextInt(HUDDLE * 2 + 1) - HUDDLE;
            BlockPos found = ground(level, x, anchor.getY(), z);
            if (found != null) {
                return found;
            }
        }
        // The anchor itself was checked, so falling back to it is safe and
        // means a cramped spot produces a tight band rather than none at all.
        return anchor;
    }

    /**
     * Two free blocks standing on something solid, searched near a start
     * height.
     *
     * The chunk is checked first. A position forty blocks out is normally
     * loaded, but "normally" is doing work there — near a world border, or
     * around a player who has just teleported, it is not, and a block query
     * into an absent chunk forces a synchronous load on the server thread.
     */
    @Nullable
    private static BlockPos ground(ServerLevel level, int x, int startY, int z) {
        BlockPos column = new BlockPos(x, startY, z);
        if (!level.isLoaded(column)) {
            return null;
        }
        for (int y = startY + 6; y > startY - 16; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.isEmptyBlock(pos)
                    && level.isEmptyBlock(pos.above())
                    && !level.isEmptyBlock(pos.below())) {
                return pos;
            }
        }
        return null;
    }
}
