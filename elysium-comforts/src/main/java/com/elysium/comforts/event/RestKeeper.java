package com.elysium.comforts.event;

import com.elysium.comforts.ElysiumComforts;
import com.elysium.comforts.block.RestBlock;
import com.elysium.comforts.rules.RestRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

/**
 * The three things the game does about a bed that a rest must not do.
 *
 * <p>The game moves your spawn, refuses you by day, and throws you out of bed
 * at dawn whatever you lay down in. All three of those are what makes a bed a
 * bed, and all three are wrong for something you unroll on the floor three
 * hundred blocks from home:
 *
 * <ul>
 *   <li><b>Your spawn does not move.</b> This is the whole feature. A rest
 *       that quietly set your spawn would be indistinguishable from a bed
 *       until the day you died and woke up somewhere you had camped a week
 *       earlier.</li>
 *   <li><b>A hammock works by day.</b> The game's own refusal is "you can only
 *       sleep at night", which is true of a bed and not of this.</li>
 *   <li><b>You wake where the rules say.</b> {@link RestRules#wakesAt} puts
 *       you on the far side of the line, so the thing you just used cannot be
 *       used again until the world has moved on.</li>
 * </ul>
 */
@EventBusSubscriber(modid = ElysiumComforts.MODID)
public final class RestKeeper {

    private RestKeeper() {
    }

    /** Whether the block at this position is one of ours to rest in. */
    public static RestBlock restAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof RestBlock rest ? rest : null;
    }

    /**
     * Your spawn does not move, and nothing else about spawns changes.
     *
     * <p>Only when the position the game is about to remember is one of ours.
     * Cancelling every set-spawn would break every bed in the pack, which is
     * a far worse bug than the one this exists to prevent.
     */
    @SubscribeEvent
    public static void onSetSpawn(PlayerSetSpawnEvent event) {
        Player player = event.getEntity();
        BlockPos where = event.getNewSpawn();
        if (where == null || event.isForced() || player.level() == null) {
            return;
        }
        if (restAt(player.level(), where) != null) {
            event.setCanceled(true);
        }
    }

    /**
     * A hammock by day, a sleeping bag by night, and the rules decide which.
     *
     * <p>The game's own answer is checked first and kept whenever it is about
     * something other than the hour — somebody else in it, monsters nearby,
     * too far away. Overriding all of them would make a rest a way to sleep
     * inside a siege.
     */
    @SubscribeEvent
    public static void onCanSleep(CanPlayerSleepEvent event) {
        RestBlock rest = restAt(event.getLevel(), event.getPos());
        if (rest == null || event.getProblem() == null) {
            return;
        }
        if (event.getProblem() != Player.BedSleepingProblem.NOT_POSSIBLE_NOW) {
            return;
        }
        int tick = (int) (event.getLevel().getDayTime() % RestRules.DAY);
        if (RestRules.usableAt(tick).equals(rest.kind())) {
            event.setProblem(null);
        }
    }

    /**
     * And it does not throw you out at the hour a bed would.
     *
     * <p>A hammock is used by day and the game would end that sleep the
     * moment it decided it was day, which is immediately.
     */
    @SubscribeEvent
    public static void onKeepSleeping(CanContinueSleepingEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getProblem() != Player.BedSleepingProblem.NOT_POSSIBLE_NOW) {
            return;
        }
        BlockPos where = player.getSleepingPos().orElse(null);
        if (where == null) {
            return;
        }
        RestBlock rest = restAt(player.level(), where);
        if (rest == null) {
            return;
        }
        int tick = (int) (player.level().getDayTime() % RestRules.DAY);
        if (RestRules.usableAt(tick).equals(rest.kind())) {
            event.setContinueSleeping(true);
        }
    }

    /**
     * You wake where the rules say, not where a bed would put you.
     *
     * <p>Worked out rather than typed: {@link RestRules#skipsFrom} says how
     * far the world moves, and that number is the same one the harness sweeps
     * over every tick of the day.
     */
    @SubscribeEvent
    public static void onSlept(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        String kind = whatTheySleptIn(level);
        if (kind.isEmpty()) {
            return;
        }
        int tick = (int) (level.getDayTime() % RestRules.DAY);
        event.setTimeAddition(level.getDayTime() + RestRules.skipsFrom(tick, kind));
    }

    /** The kind of rest the sleepers in this world are lying in, if they agree. */
    public static String whatTheySleptIn(ServerLevel level) {
        String found = "";
        for (Player player : level.players()) {
            BlockPos where = player.getSleepingPos().orElse(null);
            if (where == null) {
                continue;
            }
            RestBlock rest = restAt(level, where);
            if (rest == null) {
                return "";
            }
            if (!found.isEmpty() && !found.equals(rest.kind())) {
                return "";
            }
            found = rest.kind();
        }
        return found;
    }
}
