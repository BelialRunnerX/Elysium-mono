package com.elysium.agronomy.registry;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.entity.FishEntity;
import com.elysium.agronomy.event.Casts;
import com.elysium.agronomy.rules.FishRules;
import com.elysium.agronomy.rules.WaterRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/** One entity type per fish, walked out of the rules. */
@EventBusSubscriber(modid = ElysiumAgronomy.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class FishEntities {

    private FishEntities() {
    }

    public static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ElysiumAgronomy.MODID);

    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<FishEntity>>>
            ALL = register();

    private static Map<String, DeferredHolder<EntityType<?>, EntityType<FishEntity>>>
            register() {
        Map<String, DeferredHolder<EntityType<?>, EntityType<FishEntity>>> made =
                new LinkedHashMap<>();
        for (FishRules.Fish one : FishRules.FISH) {
            // Size follows the length in the rules, so a three-metre oarfish is
            // a three-metre oarfish and nobody has to keep a second table of
            // hitboxes in step with the first.
            float across = Math.max(0.3F, Math.min(2.0F, one.length() / 100.0F));
            float tall = Math.max(0.2F, across * 0.55F);
            boolean molten = FishRules.LAVA.equals(one.family().water());
            made.put(one.id(), TYPES.register(one.id(), () -> {
                EntityType.Builder<FishEntity> builder = EntityType.Builder
                        .<FishEntity>of(FishEntity::new, MobCategory.WATER_AMBIENT)
                        .sized(across, tall)
                        .clientTrackingRange(4);
                // A lava fish that burns in lava is a fish that is only ever
                // seen dying.
                return (molten ? builder.fireImmune() : builder).build(one.id());
            }));
        }
        if (made.size() != FishRules.FISH.size()) {
            throw new IllegalStateException("the roster has " + FishRules.FISH.size()
                    + " fish and " + made.size() + " entity type(s) were registered");
        }
        return Map.copyOf(made);
    }

    /** The type for a species. Throws rather than returning null. */
    public static EntityType<FishEntity> of(String species) {
        DeferredHolder<EntityType<?>, EntityType<FishEntity>> holder = ALL.get(species);
        if (holder == null) {
            throw new IllegalArgumentException("no fish called " + species);
        }
        return holder.get();
    }

    /**
     * Where a fish may appear on its own: in the liquid its family lives in,
     * at the depth and the hour it lives at, by {@link
     * com.elysium.agronomy.event.Casts#suits} &mdash; the same {@link
     * FishRules#lives} a cast is judged by, so a fish swims in exactly the
     * water it comes up from. The biomes it is offered to are the add-spawns
     * modifiers gen_fish.py writes from {@link
     * com.elysium.agronomy.rules.WaterRules#hauntOf}.
     */
    @SubscribeEvent
    public static void onSpawnPlacement(RegisterSpawnPlacementsEvent event) {
        int placed = 0;
        for (FishRules.Fish one : FishRules.FISH) {
            if (!WaterRules.spawnsWild(one.family())) {
                continue;
            }
            boolean molten = FishRules.LAVA.equals(one.family().water());
            event.register(ALL.get(one.id()).get(),
                    molten ? SpawnPlacementTypes.IN_LAVA : SpawnPlacementTypes.IN_WATER,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, level, reason, pos, random) -> Casts.suits(one, level, pos),
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
            placed++;
        }
        int wild = 0;
        for (FishRules.Family family : WaterRules.wildFamilies()) {
            wild += FishRules.ofFamily(family).size();
        }
        if (placed != wild) {
            throw new IllegalStateException(wild + " fish swim on their own and "
                    + placed + " were given somewhere to appear");
        }
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (FishRules.Fish one : FishRules.FISH) {
            event.put(ALL.get(one.id()).get(),
                    net.minecraft.world.entity.animal.AbstractFish
                            .createAttributes().build());
        }
    }
}
