package com.elysium.mobs.register;

import com.elysium.lib.entity.ElysiumSquads;
import com.elysium.lib.standing.ElysiumRewards;
import com.elysium.lib.standing.ElysiumStanding;
import com.elysium.mobs.ElysiumMobs;
import com.elysium.mobs.entity.ElysiumMob;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * When the Empire comes, and what it sends.
 *
 * <h2>Shape borrowed deliberately</h2>
 *
 * This is {@code EnvoyScheduler}'s shape — a per-player check on a fixed
 * cadence, offset by the player's own id so two players are never on the same
 * clock — and it is copied on purpose rather than generalised into a shared
 * base. The two schedulers agree today about almost nothing except the cadence
 * trick: one sends a single friendly entity that leaves on its own, the other
 * runs a multi-phase event with waves and a reward. A shared abstraction would
 * have to grow a flag for every one of those differences.
 *
 * <h2>The three phases</h2>
 *
 * <b>Idle</b> — a player far enough up Suspicion is considered on the cadence.
 * At Noted they get a patrol, which is two mobs and a warning: §14.3 calls it
 * "interruption, warning, low-cost defense" and that is exactly what it should
 * feel like. At Marked or Hunted an action is <em>announced</em>.
 *
 * <b>Announced</b> — ten minutes of nothing happening, which is the point. The
 * player is told, and what they do with the warning is the interesting part.
 *
 * <b>Running</b> — waves arrive on a timer. A wave is cleared when what it sent
 * is dead; the next does not arrive until it is, so an action is paced by the
 * player's ability to fight rather than by a stopwatch.
 *
 * <h2>Why clearance is counted by proximity rather than by tracking entities</h2>
 *
 * The obvious implementation keeps the UUIDs of what it spawned. It breaks on
 * the first chunk unload: the entities are gone from memory but not dead, the
 * list never empties, and the action stalls forever. Counting hostile Empire
 * mobs near the player instead is cheap, has no bookkeeping to get out of step,
 * and survives a reload — and it is <em>correct for the wrong-looking reason</em>,
 * because what the player cares about is whether anything is currently trying
 * to kill them, not whether a specific mob is still alive somewhere.
 *
 * The cost is that unrelated Empire mobs in the area count. That is acceptable,
 * and arguably right: if the Empire is standing on you, the action is not over.
 */
public final class RegisterScheduler {

    private RegisterScheduler() {
    }

    /**
     * Tells the world to keep roaming bands away from a player mid-action.
     *
     * Called once from the mod constructor. The suppression is not about
     * difficulty — a siege can afford some extra company — it is about the
     * <em>announcement</em>, which §14.3 makes the point of the whole Marked
     * row. Ten minutes of warning is ten minutes the player is supposed to
     * spend preparing, and preparing is impossible if bands keep walking in
     * during it. A warning you cannot act on is just a notification.
     *
     * Registered as a predicate rather than checked inside the library because
     * the library has no idea register actions exist, and should not learn.
     */
    public static void suppressWhileActive() {
        ElysiumSquads.addSuppressor(player -> !RegisterAction.get(player).isIdle());
    }

    /** How often an idle player is considered. Four minutes. */
    private static final int INTERVAL = 20 * 60 * 4;

    /** The chance that a considered, eligible player is acted against. */
    private static final float CHANCE = 0.30F;

    /** How often a player mid-action is ticked. Once a second is plenty. */
    private static final int ACTION_INTERVAL = 20;

    /** Where waves are placed, and how far out "still fighting" is counted. */
    private static final int RANGE = 28;

    /** Below this many survivors the wave counts as cleared. */
    private static final int CLEARED_AT = 0;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer server)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        RegisterAction action = RegisterAction.get(player);
        int offset = Math.floorMod(player.getUUID().hashCode(), INTERVAL);

        if (!action.isIdle()) {
            if ((player.tickCount + offset) % ACTION_INTERVAL == 0) {
                advance(level, server, action);
            }
            return;
        }

        if ((player.tickCount + offset) % INTERVAL != 0) {
            return;
        }
        consider(level, server);
    }

    // ------------------------------------------------------------------
    // Idle: does anything happen to this player
    // ------------------------------------------------------------------

    private static void consider(ServerLevel level, ServerPlayer player) {
        int band = ElysiumStanding.bandOf(ElysiumStanding.getSuspicion(player));
        RandomSource random = level.getRandom();

        if (RegisterAction.patrolsAt(band)) {
            // §14.3, Noted: a patrol. Two mobs and a warning — an interruption,
            // not an event. It costs the player a minute and tells them the
            // Empire has started looking, which is the whole job of this row.
            if (random.nextFloat() < CHANCE) {
                spawnWave(level, player, band, 2, false, random);
                say(player, "elysiummobs.register.patrol", ChatFormatting.YELLOW);
            }
            return;
        }

        if (RegisterAction.wavesFor(band) <= 0 || random.nextFloat() >= CHANCE) {
            return;
        }

        long now = level.getGameTime();
        RegisterAction.set(player, new RegisterAction(
                RegisterAction.ANNOUNCED, band, 0,
                now + RegisterAction.WARNING_TICKS,
                now + RegisterAction.WARNING_TICKS + RegisterAction.ABANDON_TICKS));
        player.displayClientMessage(Component.translatable(
                        "elysiummobs.register.announced",
                        RegisterAction.wavesFor(band),
                        RegisterAction.WARNING_TICKS / (20 * 60))
                .withStyle(ChatFormatting.RED), false);
    }

    // ------------------------------------------------------------------
    // Announced and running
    // ------------------------------------------------------------------

    private static void advance(ServerLevel level, ServerPlayer player,
                                RegisterAction action) {
        long now = level.getGameTime();
        RandomSource random = level.getRandom();

        // The Empire gives up on an action that has gone nowhere. Without this
        // a player who waits somewhere unreachable holds one open forever, and
        // every later check sees one running and schedules nothing — one
        // stalled action would disable the system for that player permanently.
        if (now >= action.deadline()) {
            RegisterAction.clear(player);
            say(player, "elysiummobs.register.abandoned", ChatFormatting.GRAY);
            return;
        }

        if (action.phase() == RegisterAction.ANNOUNCED) {
            if (now < action.nextTick()) {
                return;
            }
            RegisterAction.set(player, new RegisterAction(
                    RegisterAction.RUNNING, action.band(), 0, now,
                    action.deadline()));
            say(player, "elysiummobs.register.begins", ChatFormatting.RED);
            return;
        }

        if (now < action.nextTick()) {
            return;
        }

        // A wave is not sent until the last one is dead. The timer is a floor
        // on the pace, not the pace itself.
        if (action.wave() > 0 && survivors(level, player) > CLEARED_AT) {
            return;
        }

        if (action.isFinalWave()) {
            complete(level, player, action);
            return;
        }

        int wave = action.wave() + 1;
        int total = RegisterAction.wavesFor(action.band());
        boolean last = wave >= total;

        // §14.3: Hunted ends with a Praetor. The elite pressure builds before
        // it — a wave count alone would make five waves of the same thing.
        if (last && action.band() >= 3) {
            spawnPraetor(level, player, random);
            say(player, "elysiummobs.register.praetor", ChatFormatting.DARK_RED);
        } else {
            int count = 2 + wave + (action.band() >= 3 ? 1 : 0);
            spawnWave(level, player, action.band(), count, wave >= 2, random);
        }

        player.displayClientMessage(Component.translatable(
                "elysiummobs.register.wave", wave, total)
                .withStyle(ChatFormatting.RED), true);

        RegisterAction.set(player, new RegisterAction(
                RegisterAction.RUNNING, action.band(), wave,
                now + RegisterAction.WAVE_TICKS, action.deadline()));
    }

    /**
     * The action is cleared.
     *
     * §14.3: "large reward; Suspicion returns toward structural floor". Both
     * halves matter. The reward is several top-shelf rolls, which is the
     * largest single payout in the mod — a five-wave siege ending in a Praetor
     * should be worth more than anything the court sells. The floor is the
     * bottom of the band that sent the action, so clearing it satisfies the
     * Empire without erasing your record: you are still Marked, they simply
     * have nothing outstanding on you today.
     */
    private static void complete(ServerLevel level, ServerPlayer player,
                                 RegisterAction action) {
        RegisterAction.clear(player);
        RandomSource random = level.getRandom();

        int rolls = action.band() >= 3 ? 5 : 3;
        int paid = 0;
        for (int i = 0; i < rolls; i++) {
            ItemStack reward = ElysiumRewards.roll(ElysiumRewards.MAX_TIER, random);
            if (reward.isEmpty()) {
                continue;
            }
            paid++;
            if (!player.addItem(reward)) {
                player.drop(reward, false);
            }
        }

        int floor = RegisterAction.floorAfter(player);
        int current = ElysiumStanding.getSuspicion(player);
        if (current > floor) {
            ElysiumStanding.addSuspicion(player, floor - current,
                    ElysiumStanding.CAUSE_CLEARED);
        }

        player.displayClientMessage(Component.translatable(
                "elysiummobs.register.cleared", paid).withStyle(ChatFormatting.GOLD),
                false);
    }

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------

    /**
     * Empire mobs still standing near the player — the action's own, not the
     * traffic.
     *
     * The class documentation argues that counting by proximity is right even
     * though unrelated Empire mobs count: if the Empire is standing on you, the
     * action is not over. That argument held while the only Empire mobs nearby
     * were ones something had deliberately sent.
     *
     * Roaming bands break it. They arrive on their own clock, they are often
     * Empire, and they do not stop arriving because a siege is running — so
     * every wave after the first would wait on a band that has nothing to do
     * with the action, and a player who cleared the wave would stand in an
     * empty field wondering why the next one never came. One stalled action
     * disables the system for that player permanently, which is exactly the
     * failure {@code ABANDON_TICKS} exists to bound and should not have to.
     *
     * So clearance counts only mobs that are not roaming — and the scheduler
     * additionally asks the world to hold bands off entirely while an action is
     * pending, below. Both are needed: the suppressor stops new bands, and this
     * handles the ones that were already standing there when the action was
     * announced ten minutes ago.
     */
    private static int survivors(ServerLevel level, ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(RANGE);
        List<ElysiumMob> nearby = level.getEntitiesOfClass(ElysiumMob.class, box,
                mob -> mob.isAlive() && isEmpire(mob) && !ElysiumSquads.isAmbient(mob));
        return nearby.size();
    }

    /**
     * Whether this is one of the Empire's, by entity type rather than by a flag.
     *
     * The faction lives on the family, and reading it from the type keeps this
     * correct for any future Empire family without a second list to maintain —
     * as long as new ones are added here, which a validator checks.
     */
    private static boolean isEmpire(Entity entity) {
        EntityType<?> type = entity.getType();
        return type == ElysiumMobs.DRONE.get()
                || type == ElysiumMobs.LICTOR.get()
                || type == ElysiumMobs.ADEPT.get();
    }

    private static void spawnWave(ServerLevel level, ServerPlayer player, int band,
                                  int count, boolean withElite, RandomSource random) {
        int mobLevel = 1 + band * 6;
        for (int i = 0; i < count; i++) {
            // Lictors are the elite of the three: they stand in the way and do
            // not move, which is what makes a later wave different in kind and
            // not just in number.
            EntityType<? extends ElysiumMob> type = (withElite && i == 0)
                    ? ElysiumMobs.LICTOR.get()
                    : (random.nextBoolean() ? ElysiumMobs.DRONE.get()
                                            : ElysiumMobs.ADEPT.get());
            ElysiumMob mob = type.create(level);
            if (mob == null) {
                continue;
            }
            BlockPos at = placement(level, player, random);
            if (at == null) {
                continue;
            }
            mob.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D,
                    random.nextFloat() * 360.0F, 0.0F);
            mob.initialise(mobLevel, random);
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
            // setTarget is Mob's, not LivingEntity's. ElysiumMob extends
            // Monster extends Mob, so it is available directly and the cast
            // this used to do was both unnecessary and to the wrong type.
            mob.setTarget(player);
        }
    }

    private static void spawnPraetor(ServerLevel level, ServerPlayer player,
                                     RandomSource random) {
        // ElysiumBoss extends ElysiumMob, so a Praetor is initialised and
        // targeted exactly like anything else the Empire sends. Typing it as
        // the base rather than as Entity is what makes that true at compile
        // time instead of behind an instanceof.
        ElysiumMob praetor = ElysiumMobs.PRAETOR.get().create(level);
        if (praetor == null) {
            return;
        }
        BlockPos at = placement(level, player, random);
        if (at == null) {
            return;
        }
        praetor.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D,
                random.nextFloat() * 360.0F, 0.0F);
        praetor.initialise(kPraetorLevel, random);
        praetor.setPersistenceRequired();
        level.addFreshEntity(praetor);
        praetor.setTarget(player);
    }

    /** What a Praetor is scaled to when a Hunted action ends with one. */
    private static final int kPraetorLevel = 24;

    /**
     * Somewhere to stand, or null.
     *
     * Gives up rather than forcing a placement, for the reason EnvoyScheduler
     * gives: a mob dropped into a wall suffocates, and a siege whose attackers
     * die on arrival is worse than no siege — the player never learns what
     * happened, only that a warning came to nothing.
     */
    private static BlockPos placement(ServerLevel level, ServerPlayer player,
                                      RandomSource random) {
        for (int attempt = 0; attempt < 12; attempt++) {
            int dx = random.nextInt(RANGE) - RANGE / 2;
            int dz = random.nextInt(RANGE) - RANGE / 2;
            if (Math.abs(dx) < 8 && Math.abs(dz) < 8) {
                continue;                       // not on top of the player
            }
            BlockPos at = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    player.blockPosition().offset(dx, 0, dz));
            if (level.getBlockState(at).isAir()
                    && level.getBlockState(at.above()).isAir()) {
                return at;
            }
        }
        return null;
    }

    private static void say(ServerPlayer player, String key, ChatFormatting style) {
        player.displayClientMessage(Component.translatable(key).withStyle(style),
                false);
    }
}
