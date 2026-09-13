package com.elysium.lib.event;

import com.elysium.lib.ElysiumHooks;
import com.elysium.lib.ElysiumLib;
import com.elysium.lib.claim.ElysiumClaim;
import com.elysium.lib.claim.ElysiumClaims;
import com.elysium.lib.imperial.ElysiumInspection;
import com.elysium.lib.imperial.ElysiumInspections;
import com.elysium.lib.standing.ElysiumDispatch;
import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.lib.entity.ElysiumFaction;
import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The two loops, and the clock that runs them down.
 *
 * <pre>
 *   kill Unsworn  →  Favor      →  more Unsworn spawn
 *   kill Empire   →  Suspicion  →  more Empire spawn
 * </pre>
 *
 * Both sides are dispatched the same way and on the same schedule, so neither
 * loop is mechanically privileged — the difference is only in what each one
 * pays out, which {@code ElysiumLootHandler} decides.
 *
 * There is deliberately no spawn suppression here. An earlier cut had Favor
 * calling off natural spawns, which reads well as "the Empire keeps the roads
 * clear" but pulls against the loop: a meter that both summons enemies and
 * removes them nets out to nothing.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumStandingHandler {

    private ElysiumStandingHandler() {
    }

    /** How often the world considers sending someone from either side. */
    private static final int DISPATCH_INTERVAL = 200;

    /**
     * The chance an ordinary hostile counts toward Favor at all.
     *
     * This is the only rung on the ladder available to a player who has not
     * touched Elysium content yet — no workstation, no raider to kill, no
     * Empire mob to anger. At one point in three it takes about seventy-five
     * kills to reach Recognised, which is a long cave trip rather than a mob
     * farm. Any higher and a single night tops the meter out on its own.
     */
    private static final float INCIDENTAL_FAVOR_CHANCE = 0.34F;

    private static final double DISPATCH_MIN_RANGE = 20.0D;
    private static final double DISPATCH_MAX_RANGE = 36.0D;
    private static final double CROWD_RADIUS = 64.0D;

    // ------------------------------------------------------------------
    // Decay and dispatch
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        // Both meters bleed off at the same rate. Standing is something you
        // hold by continuing to act, not something you bank once — but only
        // above the notice threshold, or the first climb runs uphill against
        // the clock and nothing is reachable. See ElysiumStanding.decays.
        if (player.tickCount % ElysiumStanding.DECAY_INTERVAL == 0) {
            if (ElysiumStanding.decays(ElysiumStanding.getFavor(player))) {
                ElysiumStanding.addFavor(player, -1, ElysiumStanding.CAUSE_DECAY);
            }
            // Suspicion alone takes the player-aware overload: §14.2's claim
            // floor holds it up, and nothing holds Favor up — the Unsworn have
            // no register of who owns what.
            if (ElysiumStanding.decays(player, ElysiumStanding.getSuspicion(player))) {
                ElysiumStanding.addSuspicion(player, -ElysiumPassives.decayRate(player),
                        ElysiumStanding.CAUSE_DECAY);
            }

            // Lapsed inspection posts are forgotten on this tick rather than on
            // a clock of their own. They lapse after twenty minutes and nothing
            // reads a lapsed one anyway — reporting() skips them — so all a
            // sweep buys is the map not growing, and two minutes of slack on
            // that cannot hurt.
            ElysiumInspections.of(level).sweep(level.getGameTime());
        }

        if (player.tickCount % DISPATCH_INTERVAL == 0) {
            considerDispatch(level, player, ElysiumFaction.EMPIRE,
                    ElysiumStanding.getSuspicion(player));
            considerDispatch(level, player, ElysiumFaction.UNSWORN,
                    ElysiumStanding.getFavor(player));
        }
    }

    /**
     * Considers sending someone from one faction after this player.
     *
     * Both loops run through here. The engine owns the timing, the dice, the
     * placement and the crowd cap; a dispatcher registered by a content mod
     * owns what a member of that faction actually is. With no dispatchers
     * registered — a library installed on its own — nothing is ever sent and
     * standing is simply a number that rises.
     */
    private static void considerDispatch(ServerLevel level, Player player,
                                         ElysiumFaction faction, int meter) {
        List<ElysiumDispatch.Dispatcher> options = ElysiumDispatch.forFaction(faction);
        if (options.isEmpty()) {
            return;
        }

        float chance = ElysiumStanding.spawnChance(meter);
        int cap = ElysiumStanding.spawnCap(meter);
        if (chance <= 0.0F || cap <= 0 || level.getRandom().nextFloat() >= chance) {
            return;
        }

        // Picked at random rather than in order, so two mods contributing mobs
        // to the same faction both get a turn.
        ElysiumDispatch.Dispatcher dispatcher =
                options.get(level.getRandom().nextInt(options.size()));

        List<? extends Mob> nearby = level.getEntitiesOfClass(dispatcher.type(),
                player.getBoundingBox().inflate(CROWD_RADIUS));
        if (nearby.size() >= cap) {
            return;
        }

        BlockPos pos = findSpawnPos(level, player);
        if (pos == null) {
            return;
        }

        int band = ElysiumStanding.bandOf(meter);
        Mob mob = dispatcher.create(level, band);
        if (mob == null) {
            return;
        }
        place(level, player, mob, pos);
        dispatcher.afterPlaced(mob, level, player, pos, band);
    }

    /**
     * finalizeSpawn runs the vanilla kit-out for the base mob, so any Elysium
     * gear has to go on after this or it would be overwritten.
     */
    private static void place(ServerLevel level, Player player, Mob mob, BlockPos pos) {
        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        mob.setTarget(player);
        // Deliberate, so it persists. Since Elysium mobs now consent to the
        // ordinary despawn rules — natural spawning would otherwise fill the
        // world with immortal ones — anything sent ON PURPOSE has to say so, or
        // a dispatch the player walked away from evaporates and the meter that
        // summoned it looks broken.
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
    }

    /**
     * Somewhere out of sight but within earshot: far enough that they are not
     * simply dropped on the player's head, close enough that they arrive.
     */
    @Nullable
    private static BlockPos findSpawnPos(ServerLevel level, Player player) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = DISPATCH_MIN_RANGE
                    + random.nextDouble() * (DISPATCH_MAX_RANGE - DISPATCH_MIN_RANGE);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            int startY = Mth.floor(player.getY()) + 6;

            for (int y = startY; y > startY - 16; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (level.isEmptyBlock(pos)
                        && level.isEmptyBlock(pos.above())
                        && !level.isEmptyBlock(pos.below())) {
                    return pos;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Feeding the meters
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof Player player)) {
            return;
        }

        Entity victim = event.getEntity();
        boolean named = ElysiumFaction.isNamedCombatant(victim);

        // Character experience. A named faction combatant is worth several
        // ordinary hostiles, because the loop that spawns them is the one the
        // level track is meant to follow.
        ElysiumCharacter.addXp(player, named ? 14 : 4);

        switch (ElysiumFaction.of(victim)) {
            case EMPIRE ->
                // Killing the Empire's own is the one thing it cannot overlook.
                    ElysiumStanding.addSuspicion(player, 12,
                            ElysiumStanding.CAUSE_EMPIRE_KILL);
            case UNSWORN -> {
                // A named raider is a sanctioned target and always counts. An
                // ordinary hostile counts sometimes, or a night of mob farming
                // would top the meter out on its own.
                if (named) {
                    ElysiumStanding.addFavor(player, 4, ElysiumStanding.CAUSE_UNSWORN_KILL);
                } else if (player.getRandom().nextFloat() < INCIDENTAL_FAVOR_CHANCE) {
                    ElysiumStanding.addFavor(player, 1, ElysiumStanding.CAUSE_INCIDENTAL);
                }
            }
            default -> {
                // Neutral. The Empire does not care.
            }
        }
    }

    /**
     * Building inside a claim grows the file.
     *
     * §A.2's floor is a function of {@code totalStructureVoxels}, and this is
     * the only place that number can honestly come from: nothing in a Minecraft
     * world remembers who placed a block, so it has to be counted as it
     * happens. See {@link com.elysium.lib.claim.ElysiumClaim} for why only
     * placements count and breaks never subtract.
     *
     * <b>Anyone's placements, not only the owner's.</b> A claim is territorial,
     * and a wall a friend helped you build is still your wall — the Empire is
     * filing the structure, not auditing the labour.
     *
     * The cost of this is one map walk per block placed, over a map that holds
     * one entry per beacon in the dimension. That is the price of the feature
     * and it is paid on an action that already writes a block, updates
     * lighting, and sends a packet.
     */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ElysiumClaims claims = ElysiumClaims.of(level);
        if (claims.size() == 0) {
            return;
        }
        ElysiumClaim claim = claims.at(event.getPos());
        if (claim == null) {
            return;
        }

        int before = claim.floor();
        claims.grew(claim.anchor(), 1);
        int after = ElysiumClaim.floorFor(claim.structures() + 1);
        if (after <= before) {
            return;
        }

        // The floor moved. Suspicion is pushed up to meet it rather than left
        // below a floor it is supposed to be standing on — a floor that only
        // applies on the next decay tick would let a player build a fortress
        // and stay at zero for two minutes, which reads as the feature not
        // working.
        Player owner = level.getServer().getPlayerList().getPlayer(claim.owner());
        if (owner != null && ElysiumStanding.getSuspicion(owner) < after) {
            ElysiumStanding.addSuspicion(owner,
                    after - ElysiumStanding.getSuspicion(owner),
                    ElysiumStanding.CAUSE_CLAIM);
        }
    }

    // ------------------------------------------------------------------
    // §14.5: high-volume extraction, and who notices
    // ------------------------------------------------------------------

    /** Ores inside one window that bring an inspection post. */
    private static final int EXTRACTION_THRESHOLD = 64;

    /** How long that window is. Five minutes. */
    private static final int EXTRACTION_WINDOW = 20 * 60 * 5;

    /**
     * How much extraction it takes to be noticed, counted per player.
     *
     * <b>A rate, not a total.</b> The count resets when the window elapses, so
     * what triggers a post is sixty-four ores in five minutes — somebody
     * working a vein hard — and not sixty-four ores across an afternoon of
     * ordinary caving. §14.5 says "high-volume extraction" and the word doing
     * the work is volume: a total would eventually catch every player alive and
     * stop discriminating between them.
     *
     * Kept in a server-side map rather than on a player attachment because it
     * is genuinely disposable — a player who logs out mid-vein has stopped
     * extracting, and resuming their count an hour later would be measuring
     * nothing. The map is keyed by UUID and trimmed by the same window that
     * resets the count, so it cannot grow without bound on a busy server.
     */
    private record Extraction(int count, long windowStart) {
    }

    private static final Map<UUID, Extraction> EXTRACTING = new HashMap<>();

    private static void extracted(ServerLevel level, Player player, BlockPos where) {
        if (!ElysiumInspections.canPlant()) {
            return;                    // no content mod supplies a beacon
        }
        // Below the notice threshold the Empire is not watching anybody, which
        // is the rule the whole of §14.1 runs on. Planting a post for a player
        // with a clean record would be the one place that rule did not hold.
        if (ElysiumStanding.getSuspicion(player) < ElysiumStanding.NOTICE) {
            return;
        }

        long now = level.getGameTime();
        UUID id = player.getUUID();
        Extraction state = EXTRACTING.get(id);
        if (state == null || now - state.windowStart() > EXTRACTION_WINDOW) {
            state = new Extraction(0, now);
        }

        int count = state.count() + 1;
        if (count < EXTRACTION_THRESHOLD) {
            EXTRACTING.put(id, new Extraction(count, state.windowStart()));
            return;
        }

        // Threshold reached: the count resets whether or not a post actually
        // gets planted. Leaving it at the threshold would retry on every
        // subsequent swing, which for a player standing where nothing can be
        // placed is a placement search per ore block.
        EXTRACTING.put(id, new Extraction(0, now));

        ElysiumInspection post = ElysiumInspections.of(level)
                .plant(level, player, where);
        if (post != null) {
            player.displayClientMessage(Component.translatable(
                            "elysium.inspection.planted",
                            post.anchor().getX(), post.anchor().getY(),
                            post.anchor().getZ(),
                            ElysiumInspection.LIFETIME / (20 * 60))
                    .withStyle(ChatFormatting.RED), false);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        ServerLevel level = player.level() instanceof ServerLevel server
                ? server : null;

        Block block = event.getState().getBlock();

        // Ore a player put there is not ore they found. Without this, Silk
        // Touch plus a single vein was an unbounded experience loop — see
        // ElysiumPlacedOre.
        if (ElysiumPlacedOre.isElysiumOre(block) && ElysiumPlacedOre.wasPlaced(event.getPos())) {
            return;
        }

        // How much the Empire minds, decided by the content mod that
        // registered the ore rather than by a list of block names here.
        int suspicion = ElysiumHooks.isRichOre(block) ? 4 : ElysiumHooks.isOre(block) ? 2 : 0;

        if (suspicion > 0) {
            // §14.5: "inspection beacons that report nearby high-volume
            // extraction until disabled". A post reprices the vein rather than
            // forbidding it — see ElysiumInspection.MULTIPLIER.
            boolean watched = level != null
                    && ElysiumInspections.of(level)
                            .reporting(event.getPos(), level.getGameTime()) != null;
            if (watched) {
                suspicion *= ElysiumInspection.MULTIPLIER;
            }

            ElysiumStanding.addSuspicion(player, suspicion,
                    watched
                            ? ElysiumStanding.CAUSE_INSPECTED
                            : ElysiumHooks.isRichOre(block)
                                    ? ElysiumStanding.CAUSE_RICH_ORE
                                    : ElysiumStanding.CAUSE_ORE);
            ElysiumCharacter.addXp(player, 6);

            // Volume, not value: §14.5 says "high-volume extraction", so a
            // hundred cobblestone-adjacent ores count the same as a hundred
            // rich ones. The threshold is about how hard you are digging.
            if (level != null) {
                extracted(level, player, event.getPos());
            }

            // Prospector: a Reclaimer sometimes gets a second ingot out of a
            // vein. Dropped at the block rather than added to the loot table,
            // because the table has no idea who is holding the pickaxe.
            if (ElysiumPassives.doublesOre(player)) {
                // The engine has no idea what this ore is worth, so it asks the
                // world to drop its own loot a second time.
                net.minecraft.world.level.block.Block.dropResources(
                        event.getState(), player.level(), event.getPos());
            }
        }
    }
}
