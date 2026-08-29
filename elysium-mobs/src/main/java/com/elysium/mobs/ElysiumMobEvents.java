package com.elysium.mobs;

import com.elysium.mobs.boss.ElysiumBosses;
import com.elysium.mobs.entity.ElysiumFamilies;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The two mod-bus events every custom mob has to answer.
 *
 * <h2>Attributes</h2>
 *
 * A mob whose type has no attribute supplier crashes the moment it is created,
 * with a message that names the entity type and not the missing registration.
 * Every one of the eight is listed here, and {@code validate.py} checks the
 * count against the number of registered types — because the failure mode of
 * forgetting one is a crash that only happens when that particular family
 * spawns, which may be a long way into a playthrough.
 *
 * <h2>Spawn placement</h2>
 *
 * These are placed deliberately — by a dungeon room or a standing dispatch —
 * and never by natural spawning. The placement rule registered here is
 * therefore the strictest sensible one: on ground, in the dark, on a solid
 * block. It matters for spawn eggs and for anything a datapack later decides to
 * do with them, and it stops a Lictor appearing inside a wall.
 */
public final class ElysiumMobEvents {

    private ElysiumMobEvents() {
    }

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ElysiumMobs.SCAVENGER.get(),
                ElysiumFamilies.Scavenger.createAttributes().build());
        event.put(ElysiumMobs.REAVER.get(),
                ElysiumFamilies.Reaver.createAttributes().build());
        event.put(ElysiumMobs.WHISPER.get(),
                ElysiumFamilies.Whisper.createAttributes().build());
        event.put(ElysiumMobs.DRONE.get(),
                ElysiumFamilies.Drone.createAttributes().build());
        event.put(ElysiumMobs.LICTOR.get(),
                ElysiumFamilies.Lictor.createAttributes().build());
        event.put(ElysiumMobs.ADEPT.get(),
                ElysiumFamilies.Adept.createAttributes().build());
        event.put(ElysiumMobs.CHOIR.get(),
                ElysiumBosses.Choir.createAttributes().build());
        event.put(ElysiumMobs.PRAETOR.get(),
                ElysiumBosses.Praetor.createAttributes().build());
    }

    public static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        placement(event, ElysiumMobs.SCAVENGER.get());
        placement(event, ElysiumMobs.REAVER.get());
        placement(event, ElysiumMobs.WHISPER.get());
        placement(event, ElysiumMobs.DRONE.get());
        placement(event, ElysiumMobs.LICTOR.get());
        placement(event, ElysiumMobs.ADEPT.get());
        placement(event, ElysiumMobs.CHOIR.get());
        placement(event, ElysiumMobs.PRAETOR.get());
    }

    /**
     * One placement rule, applied to one type.
     *
     * Listed one per line rather than looped over an array because the array
     * form has to be raw to satisfy the generics, and a raw array is exactly
     * the place a type error stops being a compile error. Eight lines is a
     * small price for the compiler checking all eight.
     */
    private static <T extends Monster> void placement(
            RegisterSpawnPlacementsEvent event, net.minecraft.world.entity.EntityType<T> type) {
        event.register(type,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (SpawnPlacements.SpawnPredicate<T>) Monster::checkMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
