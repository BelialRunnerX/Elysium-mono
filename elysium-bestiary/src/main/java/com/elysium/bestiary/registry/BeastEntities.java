package com.elysium.bestiary.registry;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.BeastEntity;
import com.elysium.bestiary.rules.BeastRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nine beasts, registered from the bestiary rather than typed.
 *
 * <p>A tenth is a line in {@link BeastRules#BEASTS} and nothing here changes
 * at all — including its attributes and its spawn rule, both of which are
 * walked from the same list.
 */
@EventBusSubscriber(modid = ElysiumBestiary.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class BeastEntities {

    private BeastEntities() {
    }

    public static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ElysiumBestiary.MODID);

    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<BeastEntity>>>
            ALL = register();

    private static Map<String, DeferredHolder<EntityType<?>, EntityType<BeastEntity>>>
            register() {
        Map<String, DeferredHolder<EntityType<?>, EntityType<BeastEntity>>> made =
                new LinkedHashMap<>();
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            made.put(beast.id(), TYPES.register(beast.id(),
                    () -> EntityType.Builder.<BeastEntity>of(BeastEntity::new,
                                    MobCategory.MONSTER)
                            .sized(beast.width(), beast.height())
                            .clientTrackingRange(8)
                            .build(beast.id())));
        }
        // Eight of nine registered is a mod that works until somebody meets
        // the ninth, which is a month later and a crash report nobody can
        // reproduce. Counting here turns it into a game that does not start.
        if (made.size() != BeastRules.BEASTS.size()) {
            throw new IllegalStateException("the bestiary has "
                    + BeastRules.BEASTS.size() + " beasts and " + made.size()
                    + " entity type(s) were registered");
        }
        return Map.copyOf(made);
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            event.put(ALL.get(beast.id()).get(), BeastEntity.attributes(beast).build());
        }
    }

    /**
     * Where they may appear.
     *
     * <p>The game's own monster rule and nothing else: on a solid block, in
     * the dark, not on a spawner-proof block. Inventing a spawn rule is how a
     * mod ends up in somebody's lit base, and there is no version of that
     * which is a good surprise.
     */
    @SubscribeEvent
    public static void onSpawnPlacement(RegisterSpawnPlacementsEvent event) {
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            event.register(ALL.get(beast.id()).get(), SpawnPlacementTypes.ON_GROUND,
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BeastEntity::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }
}
