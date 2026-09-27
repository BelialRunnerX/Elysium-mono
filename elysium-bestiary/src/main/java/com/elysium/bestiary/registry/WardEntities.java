package com.elysium.bestiary.registry;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.WardEntity;
import com.elysium.bestiary.rules.BeastRules;
import com.elysium.bestiary.rules.FaunaRules;
import com.elysium.bestiary.rules.WardRules;
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
 * Thirteen creatures, registered from the rules rather than typed.
 *
 * <p>A fourteenth is a line in {@link WardRules#WARDED} and nothing here
 * changes at all — including its attributes, its size and its spawn rule, all
 * three of which are walked from the same list. Registered on the bestiary's
 * own registry rather than one of their own, because they are creatures in the
 * same world and a second registry is a second place to forget one.
 *
 */
@EventBusSubscriber(modid = ElysiumBestiary.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class WardEntities {

    private WardEntities() {
    }

    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<WardEntity>>>
            ALL = register();

    private static Map<String, DeferredHolder<EntityType<?>, EntityType<WardEntity>>>
            register() {
        Map<String, DeferredHolder<EntityType<?>, EntityType<WardEntity>>> made =
                new LinkedHashMap<>();
        for (WardRules.Warded one : WardRules.WARDED) {
            made.put(one.id(), BeastEntities.TYPES.register(one.id(),
                    () -> EntityType.Builder.<WardEntity>of(WardEntity::new,
                                    MobCategory.MONSTER)
                            .sized(sizeOf(one)[0], sizeOf(one)[1])
                            .clientTrackingRange(8)
                            .build(one.id())));
        }
        if (made.size() != WardRules.WARDED.size()) {
            throw new IllegalStateException("the rules name "
                    + WardRules.WARDED.size() + " creature(s) and " + made.size()
                    + " entity type(s) were registered");
        }
        return Map.copyOf(made);
    }

    /**
     * How big one is, from the shape its ward is drawn with.
     *
     * <p>Not a number of its own: a hitbox that disagrees with the model is a
     * creature a player swings through, and the ward's shape already decides
     * what it looks like. The tier picks which rung of that shape, so that a
     * tier-three knight is not drawn at the size of a tier-one rat.
     */
    public static float[] sizeOf(WardRules.Warded one) {
        String shape = WardRules.shapeOf(one);
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            if (beast.family().equals(shape) && beast.tier() == one.tier()) {
                return new float[]{beast.width(), beast.height()};
            }
        }
        for (FaunaRules.Animal animal : FaunaRules.ANIMALS) {
            if (animal.kind().equals(shape)) {
                return new float[]{animal.width(), animal.height()};
            }
        }
        return new float[]{0.9F, 1.8F};
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (WardRules.Warded one : WardRules.WARDED) {
            event.put(ALL.get(one.id()).get(), WardEntity.attributes(one).build());
        }
    }

    /**
     * Where they may appear.
     *
     * <p>The game's own monster rule, which is a light level and a solid block —
     * the same one every hostile thing in the world answers to. Something that
     * spawned by its own rule would appear where the player had already made
     * somewhere safe, and safety a player has built is the one thing a pack must
     * not quietly take back.
     */
    @SubscribeEvent
    public static void onSpawnPlacement(RegisterSpawnPlacementsEvent event) {
        for (WardRules.Warded one : WardRules.WARDED) {
            event.register(ALL.get(one.id()).get(), SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Monster::checkMonsterSpawnRules,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }
}
