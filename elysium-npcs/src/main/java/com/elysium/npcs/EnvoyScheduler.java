package com.elysium.npcs;

import com.elysium.lib.standing.ElysiumStanding;
import com.elysium.npcs.entity.EnvoyKind;
import com.elysium.npcs.entity.ImperialEnvoy;
import com.elysium.npcs.rules.CourtRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Who arrives, and when.
 *
 * <h2>The rule</h2>
 *
 * Once every few minutes, each player is checked: whichever standing meter they
 * are further up decides which half of the court is interested in them, and one
 * of the members of that half who will actually deal with them arrives nearby.
 * When nobody in that half will, one of whoever will comes instead, from either
 * half. The Sentinel deals with a player the Code has not noticed at all, so
 * there is always somebody who can come.
 *
 * The choice itself is CourtRules.arrivals, which is pure so that a harness can
 * run it over every standing there is. It used to be written out here, and had
 * a hole for as long as it was: Favor 1 to 24 over less Suspicion picked the
 * Favor half, whose lowest gate is Favor 25, and nobody came at all.
 *
 * <h2>Why the check is throttled to a whole interval rather than rolled per tick</h2>
 *
 * A per-tick roll with a small probability is the obvious way to write this and
 * is the wrong shape: it fires in bursts, and it fires while a player is in the
 * middle of something. Checking on a fixed cadence, with the cadence offset by
 * the player's own id, means each player has their own quiet clock and two
 * players in the same world are not visited at the same instant.
 *
 * <h2>Why only one at a time</h2>
 *
 * An envoy already standing near you means no second one is sent. Without that,
 * a player who stays put accumulates a court — five named figures milling in a
 * field, which is funny once and then is a bug. The check is a box search rather
 * than bookkeeping, so it stays correct across a restart with an envoy still
 * standing.
 */
public final class EnvoyScheduler {

    private EnvoyScheduler() {
    }

    /** How often a player is considered, in ticks. Five minutes. */
    private static final int INTERVAL = 20 * 60 * 5;

    /** The chance that a considered player is actually visited. */
    private static final float CHANCE = 0.35F;

    /** How far away an arriving envoy is placed, and how far one counts as "already here". */
    private static final int RANGE = 24;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer server)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        // Offset by the player's own id, so two players are never on the same
        // clock and a busy server does not visit everybody in one tick.
        int offset = Math.floorMod(player.getUUID().hashCode(), INTERVAL);
        if ((player.tickCount + offset) % INTERVAL != 0) {
            return;
        }

        RandomSource random = level.getRandom();
        if (random.nextFloat() >= CHANCE) {
            return;
        }
        if (!level.getEntitiesOfClass(ImperialEnvoy.class,
                new AABB(player.blockPosition()).inflate(RANGE)).isEmpty()) {
            return;
        }

        EnvoyKind kind = pick(player, random);
        if (kind == null) {
            return;
        }
        place(level, server, kind, random);
    }

    /**
     * Which of the five, if any.
     *
     * CourtRules.arrivals decides, from the two meters and who would deal: the
     * half of the court the player is further up, and when nobody in that half
     * would deal, anybody who would. Only those who would deal are ever
     * candidates, so an envoy never arrives to refuse you, which is the one
     * arrival that would read as the mod being broken.
     *
     * Null only when nobody at all would deal, which the court as declared
     * cannot produce: the Sentinel deals at band 0.
     */
    private static EnvoyKind pick(Player player, RandomSource random) {
        int favor = ElysiumStanding.getFavor(player);
        int suspicion = ElysiumStanding.getSuspicion(player);
        EnvoyKind[] court = EnvoyKind.values();
        boolean[] readsFavor = new boolean[court.length];
        boolean[] willDeal = new boolean[court.length];
        for (int i = 0; i < court.length; i++) {
            readsFavor[i] = court[i].meter() == EnvoyKind.Meter.FAVOR;
            willDeal[i] = court[i].willDealWith(player);
        }

        int[] chosen = CourtRules.arrivals(favor, suspicion, readsFavor, willDeal);
        if (chosen.length == 0) {
            return null;
        }
        return court[chosen[random.nextInt(chosen.length)]];
    }

    /**
     * Puts one on the ground near the player, on the first solid spot found.
     *
     * Gives up rather than forcing a placement. An envoy dropped into a wall,
     * or into the air over a ravine, is worse than no envoy: the player sees
     * the arrival and then sees it fall or suffocate, and the mod is what
     * killed the Emperor.
     */
    private static void place(ServerLevel level, ServerPlayer player,
                              EnvoyKind kind, RandomSource random) {
        for (int attempt = 0; attempt < 12; attempt++) {
            int dx = random.nextInt(RANGE) - RANGE / 2;
            int dz = random.nextInt(RANGE) - RANGE / 2;
            if (Math.abs(dx) < 6 && Math.abs(dz) < 6) {
                continue;                      // not on top of the player
            }
            BlockPos at = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    player.blockPosition().offset(dx, 0, dz));
            if (!level.getBlockState(at).isAir()
                    || !level.getBlockState(at.above()).isAir()) {
                continue;
            }

            ImperialEnvoy envoy = ElysiumNpcs.ENVOY.get().create(level);
            if (envoy == null) {
                return;
            }
            envoy.setKind(kind);
            envoy.setTransient(true);          // this one leaves on its own clock
            envoy.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D,
                    random.nextFloat() * 360.0F, 0.0F);
            envoy.setPersistenceRequired();
            level.addFreshEntity(envoy);

            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "elysiumnpcs.arrival", kind.displayName())
                            .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
    }
}
