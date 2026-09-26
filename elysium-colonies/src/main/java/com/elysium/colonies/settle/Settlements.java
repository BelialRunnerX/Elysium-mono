package com.elysium.colonies.settle;

import com.elysium.colonies.ElysiumColonies;
import com.elysium.colonies.charter.Charter;
import com.elysium.colonies.charter.Charters;
import com.elysium.colonies.citizen.ColonyMemory;
import com.elysium.colonies.colony.Colonies;
import com.elysium.colonies.event.ColonyLife;
import com.elysium.colonies.rules.SettleRules;
import com.elysium.colonies.rules.SiteRules;
import com.elysium.colonies.rules.StewardRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Towns that found themselves.
 *
 * The world places a {@link SettlementSeedBlock survey stake} at every site
 * the structure set chooses; the first time somebody is near enough for the
 * stake to tick, {@link #found} turns it into a colony owned by a named
 * steward who is nobody, run by the town itself. From then on the daily pass
 * treats it like any other settlement, and {@link #direct} adds what a town
 * with no player needs: its orders get completed, whole, on a schedule, and it
 * founds its next building on a cadence rather than on savings.
 *
 * <p>A player who wants one can have it: {@link #claim} hands the town over
 * and the steward steps aside.
 */
public final class Settlements {

    private Settlements() {
    }

    /** How far, in blocks, the ground is sampled for flatness around a stake. */
    public static final int FLAT_REACH = 8;

    /**
     * Founds a town at a stake, if the site is fit for one.
     *
     * Refused, in order: a world whose charter does not settle; too near the
     * world spawn; on water or leaves; ground too uneven to walk between
     * buildings; the host's own distance from other colonies. Each refusal is
     * silent in chat and one line in the log, because a refused stake is not
     * a fault, it is the world declining a site.
     */
    public static boolean found(ServerLevel level, BlockPos stake) {
        Charter charter = Charters.of(level.dimension().location());
        if (!charter.settles()) {
            return declined(level, stake, "this world's charter does not settle");
        }
        BlockPos spawn = level.getSharedSpawnPos();
        if (!SettleRules.farEnoughFromSpawn((stake.getX() - spawn.getX()) >> 4,
                (stake.getZ() - spawn.getZ()) >> 4)) {
            return declined(level, stake, "too near the world spawn");
        }
        // The ground under the stake's column, beneath any canopy: the stake
        // was placed on the generation surface, which is the tops of trees in
        // a forest, and has been removed before this is asked.
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, stake);
        if (!level.getFluidState(ground.below()).isEmpty()
                || !level.getBlockState(ground.below()).isSolid()) {
            return declined(level, stake, "the ground is water or nothing to stand on");
        }
        if (!levelEnough(level, ground)) {
            return declined(level, stake, "the ground is too uneven");
        }
        if (!Colonies.mayFoundAt(level, ground)) {
            return declined(level, stake, "the host wants more distance from other colonies");
        }

        String style = charter.hasStyle() ? charter.style() : null;
        String pack = style != null ? style : SettleRules.DEFAULT_PACK;
        long seed = SettleRules.mix(level.getSeed() ^ ground.asLong());
        String name = SettleRules.name(seed, style);
        String stewardName = stewardName(name);
        UUID stewardId = UUID.nameUUIDFromBytes(("elysium-settlement:"
                + level.dimension().location() + ":" + ground.asLong())
                .getBytes(StandardCharsets.UTF_8));

        int id = Colonies.settle(level, ground, name, pack, stewardId, stewardName);
        if (id < 0) {
            ElysiumColonies.LOGGER.info("no settlement at {} in {}: the host declined",
                    ground, level.dimension().location());
            return false;
        }
        ColonyMemory memory = ColonyMemory.of(level);
        memory.record(id, ColonyMemory.Origin.settled(stewardName, 0));
        memory.record(id, memory.ledgerOf(id).withSteward(StewardRules.Steward.TOWN));
        ElysiumColonies.LOGGER.info("{} founded itself at {} in {}, building in {}",
                name, ground, level.dimension().location(), pack);

        // The first buildings straight away: a town hall alone is a stake
        // with a roof. What the plan wants from nobody is what a town starts
        // with, and the director will complete them in their time.
        Colonies.Settlement town = Colonies.byId(level, id);
        if (town != null) {
            for (String kind : Charters.plan().firstWants()) {
                ColonyLife.foundNear(level, town, kind);
            }
        }
        return true;
    }

    /** A refused site: one line in the log, nothing in chat, and the stake is spent. */
    private static boolean declined(ServerLevel level, BlockPos stake, String why) {
        ElysiumColonies.LOGGER.info("no settlement at {} in {}: {}", stake,
                level.dimension().location(), why);
        return false;
    }

    /** Whether the ground within reach of a stake is level enough for a town. */
    static boolean levelEnough(ServerLevel level, BlockPos ground) {
        for (int dx = -FLAT_REACH; dx <= FLAT_REACH; dx += FLAT_REACH) {
            for (int dz = -FLAT_REACH; dz <= FLAT_REACH; dz += FLAT_REACH) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos there = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        ground.offset(dx, 0, dz));
                if (!SiteRules.levelEnough(ground.getY(), there.getY())) {
                    return false;
                }
                if (!level.getFluidState(there.below()).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** A steward's name: the town's, run together, within a profile name's length. */
    static String stewardName(String town) {
        String plain = town.replace(" ", "");
        return plain.length() <= 16 ? plain : plain.substring(0, 16);
    }

    /**
     * The director's round, for a town that founded itself.
     *
     * Completes the oldest order once it has waited its build time, and files
     * the next building the plan wants once the cadence allows and nothing is
     * waiting. Silent for any town a player made.
     */
    public static void direct(ServerLevel level, Colonies.Settlement town) {
        ColonyMemory memory = ColonyMemory.of(level);
        ColonyMemory.Origin origin = memory.originOf(town.id());
        if (!origin.natural()) {
            return;
        }
        List<Colonies.Order> waiting = Colonies.orders(level, town.id());
        for (Colonies.Order order : waiting) {
            if (!SettleRules.finished(town.day(), origin.lastFinished(), order.targetLevel())) {
                continue;
            }
            if (Colonies.finish(level, town.id(), order.id())) {
                origin = origin.finishedOn(town.day());
                memory.record(town.id(), origin);
                ElysiumColonies.LOGGER.info("{} completed {} to level {} by itself",
                        town.name(), order.blueprint(), order.targetLevel());
                waiting = Colonies.orders(level, town.id());
                break;
            }
        }
        if (waiting.isEmpty() && SettleRules.mayFound(town.day(), origin.lastFinished())) {
            String wanted = Charters.plan().next(town.citizens(),
                    kind -> Colonies.has(level, town.id(), kind));
            if (wanted != null && ColonyLife.foundNear(level, town, wanted)) {
                ElysiumColonies.LOGGER.info("{} laid out a {} by itself", town.name(), wanted);
            }
        }
    }

    /**
     * A player takes a self-founded town.
     *
     * @return false when the town was not the world's to give
     */
    public static boolean claim(ServerLevel level, ServerPlayer player, Colonies.Settlement town) {
        ColonyMemory memory = ColonyMemory.of(level);
        ColonyMemory.Origin origin = memory.originOf(town.id());
        if (!origin.natural()) {
            return false;
        }
        if (!Colonies.handOver(level, town.id(), player)) {
            return false;
        }
        memory.record(town.id(), origin.claimed());
        memory.record(town.id(), memory.ledgerOf(town.id())
                .withSteward(StewardRules.Steward.ATTENDANCE));
        ElysiumColonies.LOGGER.info("{} claimed {}", player.getGameProfile().getName(),
                town.name());
        return true;
    }

    /** Every town in this world that founded itself. */
    public static List<Colonies.Settlement> natural(ServerLevel level) {
        ColonyMemory memory = ColonyMemory.of(level);
        List<Colonies.Settlement> out = new ArrayList<>();
        for (Colonies.Settlement town : Colonies.in(level)) {
            if (memory.originOf(town.id()).natural()) {
                out.add(town);
            }
        }
        return out;
    }
}
