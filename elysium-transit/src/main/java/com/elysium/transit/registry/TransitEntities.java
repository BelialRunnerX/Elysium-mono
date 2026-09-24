package com.elysium.transit.registry;

import com.elysium.transit.ElysiumTransit;
import com.elysium.transit.entity.VehicleEntity;
import com.elysium.transit.rules.TransitRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

/**
 * Three rungs of one vehicle.
 *
 * <p>One class and three types rather than three classes, because a cart, a
 * truck and a hauler differ in four numbers and a texture. Which rung a given
 * entity is comes from {@link #rungOf}, so adding a fourth is four numbers and
 * a sprite rather than a file.
 */
public final class TransitEntities {

    private TransitEntities() {
    }

    public static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ElysiumTransit.MODID);

    private static DeferredHolder<EntityType<?>, EntityType<VehicleEntity>> vehicle(
            TransitRules.Vehicle rung, float width, float height) {
        return TYPES.register(rung.name(),
                () -> EntityType.Builder.<VehicleEntity>of(VehicleEntity::new,
                                MobCategory.MISC)
                        .sized(width, height).clientTrackingRange(10).updateInterval(1)
                        .build(rung.name()));
    }

    public static final DeferredHolder<EntityType<?>, EntityType<VehicleEntity>> CART =
            vehicle(TransitRules.CART, 1.1F, 0.9F);
    public static final DeferredHolder<EntityType<?>, EntityType<VehicleEntity>> TRUCK =
            vehicle(TransitRules.TRUCK, 1.5F, 1.2F);
    public static final DeferredHolder<EntityType<?>, EntityType<VehicleEntity>> HAULER =
            vehicle(TransitRules.HAULER, 1.9F, 1.5F);

    /** Every rung, in order, so the tab and the client walk the same list. */
    public static final List<TransitRules.Vehicle> RUNGS =
            List.of(TransitRules.CART, TransitRules.TRUCK, TransitRules.HAULER);

    /**
     * Which rung an entity type is.
     *
     * <p>By the type's own registered name, which is the rung's name, so the
     * two cannot drift: a type registered under a name no rung has would be a
     * vehicle with no numbers, and that is caught here rather than three ticks
     * later in the middle of somebody's journey.
     */
    public static TransitRules.Vehicle rungOf(EntityType<?> type) {
        String name = EntityType.getKey(type).getPath();
        for (TransitRules.Vehicle rung : RUNGS) {
            if (rung.name().equals(name)) {
                return rung;
            }
        }
        return TransitRules.CART;
    }

    /** The other way round, for the items that place them. */
    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<VehicleEntity>>>
            BY_NAME = Map.of(TransitRules.CART.name(), CART,
                    TransitRules.TRUCK.name(), TRUCK,
                    TransitRules.HAULER.name(), HAULER);
}
