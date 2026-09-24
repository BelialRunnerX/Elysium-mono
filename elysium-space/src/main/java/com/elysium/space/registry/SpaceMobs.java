package com.elysium.space.registry;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.world.MobRules;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * The mobs' attributes (from MobRules) and where natural spawning may place
 * them. Which biomes spawn which mob is data: the ported biomes' spawners.
 */
@EventBusSubscriber(modid = ElysiumSpace.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class SpaceMobs {

    private SpaceMobs() {
    }

    /** A mob's attributes: the base set for its kind, with MobRules's numbers on top. */
    static AttributeSupplier.Builder attributes(AttributeSupplier.Builder base, MobRules.Mob rules) {
        base.add(Attributes.MAX_HEALTH, rules.health())
                .add(Attributes.MOVEMENT_SPEED, rules.speed())
                .add(Attributes.FOLLOW_RANGE, rules.followRange())
                .add(Attributes.KNOCKBACK_RESISTANCE, rules.knockbackResistance());
        if (rules.hostile()) {
            base.add(Attributes.ATTACK_DAMAGE, rules.damage())
                    .add(Attributes.ATTACK_KNOCKBACK, rules.attackKnockback());
        }
        return base;
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        put(event, SpaceEntities.MOONFOLK.get(), Mob.createMobAttributes(), MobRules.MOONFOLK);
        put(event, SpaceEntities.HOLLOW_MOONFOLK.get(), Monster.createMonsterAttributes(), MobRules.HOLLOW_MOONFOLK);
        put(event, SpaceEntities.VOID_CRAWLER.get(), Monster.createMonsterAttributes(), MobRules.VOID_CRAWLER);
        put(event, SpaceEntities.FROST_RAPTOR.get(), Monster.createMonsterAttributes(), MobRules.FROST_RAPTOR);
        put(event, SpaceEntities.CINDER_BOAR.get(), Monster.createMonsterAttributes(), MobRules.CINDER_BOAR);
        put(event, SpaceEntities.CINDERLING.get(), Monster.createMonsterAttributes(), MobRules.CINDERLING);
        put(event, SpaceEntities.CINDERLING_BRUTE.get(), Monster.createMonsterAttributes(), MobRules.CINDERLING_BRUTE);
    }

    private static void put(EntityAttributeCreationEvent event, EntityType<? extends LivingEntity> type,
                            AttributeSupplier.Builder base, MobRules.Mob rules) {
        event.put(type, attributes(base, rules).build());
    }

    /**
     * On the ground, as Stellaris placed them. The hunters need the dark, as
     * any monster does; the boar and the cinderlings spawn in any light, as
     * hoglins and piglins do, but not on nether wart; moonfolk as villagers.
     * The Emerald Bastion's no-hostiles rule (SpaceEvents) still applies.
     */
    @SubscribeEvent
    public static void onPlacements(RegisterSpawnPlacementsEvent event) {
        RegisterSpawnPlacementsEvent.Operation op = RegisterSpawnPlacementsEvent.Operation.REPLACE;
        Heightmap.Types ground = Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
        event.register(SpaceEntities.MOONFOLK.get(), SpawnPlacementTypes.ON_GROUND, ground, Mob::checkMobSpawnRules, op);
        event.register(SpaceEntities.HOLLOW_MOONFOLK.get(), SpawnPlacementTypes.ON_GROUND, ground,
                Monster::checkMonsterSpawnRules, op);
        event.register(SpaceEntities.VOID_CRAWLER.get(), SpawnPlacementTypes.ON_GROUND, ground,
                Monster::checkMonsterSpawnRules, op);
        event.register(SpaceEntities.FROST_RAPTOR.get(), SpawnPlacementTypes.ON_GROUND, ground,
                Monster::checkMonsterSpawnRules, op);
        event.register(SpaceEntities.CINDER_BOAR.get(), SpawnPlacementTypes.ON_GROUND, ground,
                (type, level, reason, pos, random) -> !level.getBlockState(pos.below()).is(Blocks.NETHER_WART_BLOCK), op);
        event.register(SpaceEntities.CINDERLING.get(), SpawnPlacementTypes.ON_GROUND, ground,
                (type, level, reason, pos, random) -> !level.getBlockState(pos.below()).is(Blocks.NETHER_WART_BLOCK), op);
        event.register(SpaceEntities.CINDERLING_BRUTE.get(), SpawnPlacementTypes.ON_GROUND, ground,
                (type, level, reason, pos, random) -> !level.getBlockState(pos.below()).is(Blocks.NETHER_WART_BLOCK), op);
    }
}
