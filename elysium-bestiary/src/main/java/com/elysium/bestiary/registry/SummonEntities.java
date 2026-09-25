package com.elysium.bestiary.registry;

import com.elysium.bestiary.ElysiumBestiary;
import com.elysium.bestiary.entity.RelicBoltEntity;
import com.elysium.bestiary.entity.ShadeEntity;
import com.elysium.lib.ability.act.ElysiumAbilityEntities;
import com.elysium.lib.ability.ElysiumAbilityShapes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The two entities the ability system cannot happen without.
 *
 * <h2>Why they are here and not there</h2>
 *
 * Thirty abilities were priced, carried by thirty relics and registered, and
 * pressing the button did nothing, because three of them throw or summon
 * something and nothing in the pack was that something. The abilities are in
 * {@code elysium-lib} and the relics are in {@code elysium-arcana}, and both
 * are built before this module — so the library says what it needs through
 * {@link ElysiumAbilityEntities} and this fills it in.
 *
 * <p>One bolt for both abilities that throw, and one shade for the one that
 * summons. What differs between a hunter's mark and a stormcall is how many
 * and how hard, and both of those are arithmetic in the library rather than a
 * second class here.
 */
@EventBusSubscriber(modid = ElysiumBestiary.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class SummonEntities {

    private SummonEntities() {
    }

    public static final DeferredHolder<EntityType<?>, EntityType<RelicBoltEntity>>
            BOLT = BeastEntities.TYPES.register(ElysiumAbilityShapes.BOLT,
                    () -> EntityType.Builder.<RelicBoltEntity>of(
                                    RelicBoltEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build(ElysiumAbilityShapes.BOLT));

    public static final DeferredHolder<EntityType<?>, EntityType<ShadeEntity>>
            SHADE = BeastEntities.TYPES.register("shade",
                    () -> EntityType.Builder.<ShadeEntity>of(ShadeEntity::new,
                                    MobCategory.MISC)
                            .sized(0.6F, 1.9F)
                            .clientTrackingRange(8)
                            .build("shade"));

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(SHADE.get(), ShadeEntity.attributes().build());
    }

    /**
     * Hand the library what it asked for.
     *
     * <p>On common setup rather than at class load, because a registry object
     * asked for before its registry has been frozen is the one crash in this
     * pattern, and it happens on somebody else's machine.
     */
    @SubscribeEvent
    public static void fillIn(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ElysiumAbilityEntities.bolt(BOLT::get);
            ElysiumAbilityEntities.summon(SHADE::get);
            if (!ElysiumAbilityEntities.missing().isEmpty()) {
                throw new IllegalStateException("after this module has "
                        + "registered, these ability shapes still have nothing "
                        + "behind them and would quietly do nothing: "
                        + ElysiumAbilityEntities.missing());
            }
        });
    }
}
