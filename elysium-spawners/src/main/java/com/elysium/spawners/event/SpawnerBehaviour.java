package com.elysium.spawners.event;

import com.elysium.spawners.ElysiumSpawners;
import com.elysium.spawners.block.ElysiumSpawnerBlockEntity;
import com.elysium.spawners.data.SpawnerTuning;
import com.mojang.datafixers.util.Either;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import org.jetbrains.annotations.Nullable;

/**
 * What a spawner's runes do to what it sends (Apothic Spawners' AS-4).
 *
 * <h2>Two events, because there are two different moments</h2>
 *
 * Whether a creature may appear at all is decided before it exists, and the
 * only thing a listener is handed there is the spawner itself — which is why
 * the cage is a named class. What the creature is *like* is decided once it
 * does exist, and there the spawner arrives as its block entity.
 *
 * <p>Nothing here touches a creature that came from anywhere else. A natural
 * zombie and a zombie out of somebody else's spawner behave exactly as they
 * always did.
 */
@EventBusSubscriber(modid = ElysiumSpawners.MODID)
public final class SpawnerBehaviour {

    private SpawnerBehaviour() {
    }

    /**
     * The light, and whatever else the game would have said no about.
     *
     * A spawner told to ignore it says so here, once, for the position it has
     * already chosen — it does not get to choose a better position, which is
     * what would turn a rune into a wall of mobs in a lit room.
     */
    @SubscribeEvent
    public static void onPosition(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawner() instanceof ElysiumSpawnerBlockEntity.Cage cage
                && cage.owner().tuning().ignoresLight()) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.SUCCEED);
        }
    }

    /** What the creature is like, once it exists. */
    @SubscribeEvent
    public static void onFinalize(FinalizeSpawnEvent event) {
        SpawnerTuning tuning = tuningOf(event);
        if (tuning == null || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (tuning.noAi()) {
            // Not asleep: it has no brain at all. It will not path, will not
            // attack and will not wander off, which is what makes a farm a
            // farm rather than a siege.
            mob.setNoAi(true);
        }
        if (tuning.silent()) {
            mob.setSilent(true);
        }
        if (tuning.persistent()) {
            // It stays. A spawner beside a base that quietly despawns what it
            // sent is a spawner that appears to do nothing.
            mob.setPersistenceRequired();
        }
    }

    /**
     * The tuning of the spawner that sent this creature, if one did.
     *
     * <p><b>{@code getSpawner()} is null for every mob the world spawns by
     * itself</b>, which is almost all of them — and the very first thing that
     * happens in a new world is a few hundred of them, during chunk
     * generation, which is why this threw while the spawn area was being
     * prepared and stopped the world at 51%.
     */
    @Nullable
    private static SpawnerTuning tuningOf(FinalizeSpawnEvent event) {
        Either<BlockEntity, Entity> spawner = event.getSpawner();
        if (spawner == null) {
            return null;
        }
        BlockEntity entity = spawner.left().orElse(null);
        return entity instanceof ElysiumSpawnerBlockEntity spawn ? spawn.tuning() : null;
    }
}
