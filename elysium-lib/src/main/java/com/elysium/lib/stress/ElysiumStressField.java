package com.elysium.lib.stress;

import com.elysium.lib.hazard.ElysiumHazard;
import com.elysium.lib.hazard.ElysiumHazardField;
import com.elysium.lib.room.ElysiumRoom;
import com.elysium.lib.room.ElysiumRoomCache;
import com.elysium.lib.social.ElysiumMemories;
import com.elysium.lib.social.ElysiumMemoryKind;
import com.elysium.lib.social.ElysiumMemoryKinds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Which reasons are true about one villager, right now.
 *
 * <h2>Read from the world, not from a tally</h2>
 *
 * Nothing here is remembered. Every stressor is a question asked of the villager
 * and the block it is standing on at the moment somebody wants to know, which is
 * what makes the explanation in {@link ElysiumStress.Reading} true rather than
 * merely plausible — see {@link ElysiumStressor} for why that was worth giving
 * up an accumulated score for.
 *
 * <h2>Read off the brain where the brain already knows</h2>
 *
 * Homelessness, unemployment and a nearby hostile are all things vanilla's
 * villager AI has already worked out and stored: it has a HOME memory, a
 * JOB_SITE memory, a NEAREST_HOSTILE memory, and it maintains them properly
 * across chunk loads, deaths and bed thefts. Asking the brain is both cheaper
 * than searching and — more importantly — guaranteed to agree with what the
 * villager will actually <em>do</em>. A private homelessness check would
 * eventually say a villager had a bed at the same moment its pathfinder gave up
 * looking for one.
 *
 * <h2>Cost</h2>
 *
 * Called when a player asks, and once a second per villager that is being
 * tracked — not every tick. The two expensive questions, the hazard field and
 * the room scan, both go through their own caches, so a village of forty
 * villagers standing in one room performs one room scan between them.
 */
public final class ElysiumStressField {

    private ElysiumStressField() {
    }

    /** How near a bed still counts as being at home. */
    public static final int HOME_RADIUS = 16;

    /** Light level at or below which a villager outdoors is in the dark. */
    public static final int DARK = 7;

    /** Villagers within this many blocks before it feels crowded. */
    public static final int CROWD_RADIUS = 8;

    /** How many of them is too many. */
    public static final int CROWD = 8;

    /** How near a player has to be to settle somebody who likes them. */
    public static final double FRIEND_RADIUS = 12.0D;

    /** Below this fraction of full health, somebody is visibly hurt. */
    public static final float INJURED = 0.5F;

    /** Everything true about this villager at this moment. */
    public static Set<ElysiumStressor> reasonsFor(Villager villager) {
        EnumSet<ElysiumStressor> reasons = EnumSet.noneOf(ElysiumStressor.class);
        Level level = villager.level();
        if (level.isClientSide()) {
            return reasons;
        }
        BlockPos pos = villager.blockPosition();

        // --- what the brain already knows ---------------------------------
        Optional<GlobalPos> home = villager.getBrain()
                .getMemory(MemoryModuleType.HOME);
        if (home.isEmpty()) {
            reasons.add(ElysiumStressor.HOMELESS);
        } else if (home.get().dimension().equals(level.dimension())
                && home.get().pos().closerThan(pos, HOME_RADIUS)) {
            reasons.add(ElysiumStressor.AT_HOME);
        }
        if (villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty()
                && !villager.isBaby()) {
            // Babies are not unemployed. They are children, and a village of
            // toddlers reading as a workforce crisis is the sort of thing that
            // makes a whole system look like it is not paying attention.
            reasons.add(ElysiumStressor.UNEMPLOYED);
        }
        if (villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE)
                .isPresent()) {
            reasons.add(ElysiumStressor.THREATENED);
        }

        // --- what their body says -----------------------------------------
        if (villager.getHealth() < villager.getMaxHealth() * INJURED) {
            reasons.add(ElysiumStressor.INJURED);
        }

        // --- where they are standing ---------------------------------------
        if (level.isNight() && level.getBrightness(LightLayer.BLOCK, pos) <= DARK
                && level.canSeeSky(pos)) {
            reasons.add(ElysiumStressor.IN_THE_DARK);
        }
        for (ElysiumHazard hazard : ElysiumHazard.REGISTRY.all()) {
            if (ElysiumHazardField.intensity(level, pos, hazard) > 0.0F) {
                reasons.add(ElysiumStressor.IN_A_HAZARD);
                break;
            }
        }
        ElysiumRoom room = ElysiumRoomCache.at(level, pos);
        if (room != null) {
            reasons.add(ElysiumStressor.SHELTERED);
        }

        // --- who is around ---------------------------------------------------
        int neighbours = level.getEntitiesOfClass(Villager.class,
                villager.getBoundingBox().inflate(CROWD_RADIUS)).size();
        if (neighbours > CROWD) {
            reasons.add(ElysiumStressor.CROWDED);
        }
        for (Player nearby : level.players()) {
            if (nearby.distanceToSqr(villager) <= FRIEND_RADIUS * FRIEND_RADIUS
                    && ElysiumMemories.opinionOf(villager, nearby)
                            >= ElysiumMemories.WARMTH) {
                reasons.add(ElysiumStressor.AMONG_FRIENDS);
                break;
            }
        }

        // --- what they have not got over -------------------------------------
        addRemembered(villager, reasons);
        return reasons;
    }

    /**
     * Two stressors that come from the villager's own memories.
     *
     * Reusing the memory system rather than keeping a second history is the
     * point: those episodes already fade at their own rate, already survive a
     * save, and are already the thing a player is shown when they ask what a
     * villager remembers. A separate "recently traumatised" timer would fade on
     * a different schedule and the two readouts would contradict each other in
     * front of the player.
     */
    private static void addRemembered(Villager villager,
                                      EnumSet<ElysiumStressor> reasons) {
        long now = villager.level().getGameTime();
        for (ElysiumMemories.Episode episode : ElysiumMemories.all(villager)) {
            ElysiumMemoryKind kind = episode.resolve();
            if (kind == null || episode.weightAt(now) >= 0) {
                continue;               // faded away, or was never a bad one
            }
            if (kind == ElysiumMemoryKinds.SAW_KILLING) {
                reasons.add(ElysiumStressor.SAW_A_KILLING);
            } else if (kind == ElysiumMemoryKinds.HURT_BY) {
                reasons.add(ElysiumStressor.HURT_RECENTLY);
            }
        }
    }

    /** Whether an entity is one this system has anything to say about. */
    public static boolean applies(LivingEntity subject) {
        return subject instanceof Villager;
    }
}
