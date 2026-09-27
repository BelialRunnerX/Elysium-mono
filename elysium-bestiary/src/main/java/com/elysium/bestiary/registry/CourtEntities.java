package com.elysium.bestiary.registry;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.CourtEntity;
import com.elysium.bestiary.rules.BeastRules;
import com.elysium.bestiary.rules.FaunaRules;
import com.elysium.bestiary.rules.CourtRules;
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
 * Fifty-eight creatures, registered from the rules rather than typed.
 *
 * <p>A fifty-ninth is a line in {@link CourtRules#MEMBERS} and nothing here
 * changes at all — including its attributes, its size and its spawn rule, all
 * three of which are walked from the same list. Registered on the bestiary's
 * own registry rather than one of their own, because they are creatures in the
 * same world and a second registry is a second place to forget one.
 *
 * <p>Fifty-eight is the point at which typing a register by hand stops being
 * tedious and starts being unreliable: the failure is not that somebody gets
 * bored, it is that nineteen of them do the same job under different names and
 * a pasted line reads correct while pointing at the wrong row.
 */
@EventBusSubscriber(modid = ElysiumBestiary.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class CourtEntities {

    private CourtEntities() {
    }

    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<CourtEntity>>>
            ALL = register();

    private static Map<String, DeferredHolder<EntityType<?>, EntityType<CourtEntity>>>
            register() {
        Map<String, DeferredHolder<EntityType<?>, EntityType<CourtEntity>>> made =
                new LinkedHashMap<>();
        for (CourtRules.Member one : CourtRules.MEMBERS) {
            made.put(one.id(), BeastEntities.TYPES.register(one.id(),
                    () -> EntityType.Builder.<CourtEntity>of(CourtEntity::new,
                                    MobCategory.MONSTER)
                            .sized(sizeOf(one)[0], sizeOf(one)[1])
                            .clientTrackingRange(8)
                            .build(one.id())));
        }
        if (made.size() != CourtRules.MEMBERS.size()) {
            throw new IllegalStateException("the rules name "
                    + CourtRules.MEMBERS.size() + " creature(s) and " + made.size()
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
    public static float[] sizeOf(CourtRules.Member one) {
        float[] found = shapeSize(one);
        float bigger = one.head() ? HEADS_ARE : 1.0F;
        return new float[]{found[0] * bigger, found[1] * bigger};
    }

    /**
     * How much bigger a head is than the things around it.
     *
     * <p>A court whose head is the same size as its swarm is a court where the
     * player cannot tell which one the fight is, and the whole arrangement of
     * this module -- a head worth three beasts with everything else held under
     * it -- is invisible from inside the room.
     */
    public static final float HEADS_ARE = 1.4F;

    /** The size the court's own shape comes out at. */
    private static float[] shapeSize(CourtRules.Member one) {
        String shape = CourtRules.shapeOf(one);
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
        for (CourtRules.Member one : CourtRules.MEMBERS) {
            event.put(ALL.get(one.id()).get(), CourtEntity.attributes(one).build());
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
        for (CourtRules.Member one : CourtRules.MEMBERS) {
            event.register(ALL.get(one.id()).get(), SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Monster::checkMonsterSpawnRules,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }
}
