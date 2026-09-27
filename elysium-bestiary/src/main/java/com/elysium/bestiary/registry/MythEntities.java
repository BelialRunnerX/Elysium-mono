package com.elysium.bestiary.registry;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.MythEntity;
import com.elysium.bestiary.rules.MythRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Eight myths, registered from the rules rather than typed.
 *
 * <p>A ninth is a line in {@link MythRules#MYTHS} and nothing here changes at
 * all — including its attributes and its spawn rule, both of which are walked
 * from the same list. Registered on the bestiary's own registry rather than one
 * of their own, because they are creatures in the same world and a second
 * registry is a second place to forget one.
 */
@EventBusSubscriber(modid = ElysiumBestiary.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class MythEntities {

    private MythEntities() {
    }

    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<MythEntity>>>
            ALL = register();

    private static Map<String, DeferredHolder<EntityType<?>, EntityType<MythEntity>>>
            register() {
        Map<String, DeferredHolder<EntityType<?>, EntityType<MythEntity>>> made =
                new LinkedHashMap<>();
        for (MythRules.Myth myth : MythRules.MYTHS) {
            made.put(myth.id(), BeastEntities.TYPES.register(myth.id(),
                    () -> EntityType.Builder.<MythEntity>of(MythEntity::new,
                                    MobCategory.MONSTER)
                            .sized(sizeOf(myth)[0], sizeOf(myth)[1])
                            .clientTrackingRange(8)
                            .build(myth.id())));
        }
        if (made.size() != MythRules.MYTHS.size()) {
            throw new IllegalStateException("the rules name "
                    + MythRules.MYTHS.size() + " myth(s) and " + made.size()
                    + " entity type(s) were registered");
        }
        return Map.copyOf(made);
    }

    /**
     * How big one is, from the shape it is drawn with.
     *
     * <p>Not a number of its own: a hitbox that disagrees with the model is a
     * creature a player swings through, and the shape is already the thing that
     * decides what it looks like.
     */
    public static float[] sizeOf(MythRules.Myth myth) {
        for (com.elysium.bestiary.rules.BeastRules.Beast beast
                : com.elysium.bestiary.rules.BeastRules.BEASTS) {
            if (beast.family().equals(myth.form()) && beast.tier() == myth.tier()) {
                return new float[]{beast.width(), beast.height()};
            }
        }
        for (com.elysium.bestiary.rules.FaunaRules.Animal animal
                : com.elysium.bestiary.rules.FaunaRules.ANIMALS) {
            if (animal.kind().equals(myth.form())) {
                return new float[]{animal.width(), animal.height()};
            }
        }
        return new float[]{0.9F, 1.8F};
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (MythRules.Myth myth : MythRules.MYTHS) {
            event.put(ALL.get(myth.id()).get(), MythEntity.attributes(myth).build());
        }
    }

    /**
     * Where they may appear.
     *
     * <p>The game's own monster rule, which is a light level and a solid block
     * — the same one every hostile thing in the world answers to. A myth that
     * spawned by its own rule would be a myth that appeared where the player
     * had already made somewhere safe, and safety a player has built is the one
     * thing a pack must not quietly take back.
     */
    @SubscribeEvent
    public static void onSpawnPlacement(RegisterSpawnPlacementsEvent event) {
        for (MythRules.Myth myth : MythRules.MYTHS) {
            event.register(ALL.get(myth.id()).get(), SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Monster::checkMonsterSpawnRules,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }
}
