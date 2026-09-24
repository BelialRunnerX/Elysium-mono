package com.elysium.space.registry;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.entity.RocketEntity;
import com.elysium.space.entity.mob.CinderBoar;
import com.elysium.space.entity.mob.Cinderling;
import com.elysium.space.entity.mob.CinderlingBrute;
import com.elysium.space.entity.mob.FrostRaptor;
import com.elysium.space.entity.mob.HollowMoonfolk;
import com.elysium.space.entity.mob.Moonfolk;
import com.elysium.space.entity.mob.RimeSpit;
import com.elysium.space.entity.mob.VoidCrawler;
import com.elysium.space.world.MobRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SpaceEntities {

    private SpaceEntities() {
    }

    public static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ElysiumSpace.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<RocketEntity>> ROCKET = TYPES.register("rocket",
            () -> EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC)
                    .sized(1.2F, 3.4F).clientTrackingRange(10).updateInterval(1).fireImmune()
                    .build("rocket"));

    public static final DeferredHolder<EntityType<?>, EntityType<RimeSpit>> RIME_SPIT = TYPES.register("rime_spit",
            () -> EntityType.Builder.<RimeSpit>of(RimeSpit::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(20).build("rime_spit"));

    // The mobs: sizes from MobRules (Stellaris's), categories from whether they hunt.
    public static final DeferredHolder<EntityType<?>, EntityType<Moonfolk>> MOONFOLK =
            mob(MobRules.MOONFOLK, Moonfolk::new);
    public static final DeferredHolder<EntityType<?>, EntityType<HollowMoonfolk>> HOLLOW_MOONFOLK =
            mob(MobRules.HOLLOW_MOONFOLK, HollowMoonfolk::new);
    public static final DeferredHolder<EntityType<?>, EntityType<VoidCrawler>> VOID_CRAWLER =
            mob(MobRules.VOID_CRAWLER, VoidCrawler::new);
    public static final DeferredHolder<EntityType<?>, EntityType<FrostRaptor>> FROST_RAPTOR =
            mob(MobRules.FROST_RAPTOR, FrostRaptor::new);
    public static final DeferredHolder<EntityType<?>, EntityType<CinderBoar>> CINDER_BOAR =
            mob(MobRules.CINDER_BOAR, CinderBoar::new);
    public static final DeferredHolder<EntityType<?>, EntityType<Cinderling>> CINDERLING =
            mob(MobRules.CINDERLING, Cinderling::new);
    public static final DeferredHolder<EntityType<?>, EntityType<CinderlingBrute>> CINDERLING_BRUTE =
            mob(MobRules.CINDERLING_BRUTE, CinderlingBrute::new);

    private static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> mob(
            MobRules.Mob rules, EntityType.EntityFactory<T> factory) {
        return TYPES.register(rules.id(), () -> EntityType.Builder.of(factory,
                        rules.hostile() ? MobCategory.MONSTER : MobCategory.CREATURE)
                .sized(rules.width(), rules.height()).clientTrackingRange(8).build(rules.id()));
    }
}
