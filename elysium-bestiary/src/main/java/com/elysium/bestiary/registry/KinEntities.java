package com.elysium.bestiary.registry;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.KinEntity;
import com.elysium.bestiary.rules.BeastRules;
import com.elysium.bestiary.rules.FaunaRules;
import com.elysium.bestiary.rules.KindredRules;
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
 * Twenty-seven creatures, registered from the rules rather than typed.
 *
 * <p>A twenty-eighth is a line in {@link KindredRules#KINDRED} and nothing here
 * changes at all — including its attributes, its size and its spawn rule, all
 * three of which are walked from the same list. Registered on the bestiary's
 * own registry rather than one of their own, because they are creatures in the
 * same world and a second registry is a second place to forget one.
 *
 * <p>This is also where a roster that grows by variant would hurt most if it
 * were typed by hand: four scorpions written out four times is four chances to
 * paste the wrong id, and the symptom of that is one of them silently being the
 * other.
 */
@EventBusSubscriber(modid = ElysiumBestiary.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class KinEntities {

    private KinEntities() {
    }

    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<KinEntity>>>
            ALL = register();

    private static Map<String, DeferredHolder<EntityType<?>, EntityType<KinEntity>>>
            register() {
        Map<String, DeferredHolder<EntityType<?>, EntityType<KinEntity>>> made =
                new LinkedHashMap<>();
        for (KindredRules.Kin one : KindredRules.KINDRED) {
            made.put(one.id(), BeastEntities.TYPES.register(one.id(),
                    () -> EntityType.Builder.<KinEntity>of(KinEntity::new,
                                    MobCategory.MONSTER)
                            .sized(sizeOf(one)[0], sizeOf(one)[1])
                            .clientTrackingRange(8)
                            .build(one.id())));
        }
        if (made.size() != KindredRules.KINDRED.size()) {
            throw new IllegalStateException("the rules name "
                    + KindredRules.KINDRED.size() + " creature(s) and " + made.size()
                    + " entity type(s) were registered");
        }
        return Map.copyOf(made);
    }

    /**
     * How big one is, from the shape its kindred is drawn with.
     *
     * <p>Not a number of its own: a hitbox that disagrees with the model is a
     * creature a player swings through, and the kindred's shape already decides
     * what it looks like. The tier picks which rung of that shape, so that the
     * tier-three golem is not drawn at the size of a tier-one scorpion.
     */
    public static float[] sizeOf(KindredRules.Kin one) {
        String shape = KindredRules.shapeOf(one);
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
        for (KindredRules.Kin one : KindredRules.KINDRED) {
            event.put(ALL.get(one.id()).get(), KinEntity.attributes(one).build());
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
        for (KindredRules.Kin one : KindredRules.KINDRED) {
            event.register(ALL.get(one.id()).get(), SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Monster::checkMonsterSpawnRules,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }
}
