package com.elysium.npcs;

import com.elysium.npcs.entity.EnvoyKind;
import com.elysium.npcs.entity.ImperialEnvoy;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Elysium Court — the five named figures of the Black and Emerald Empire.
 *
 * <h2>What this mod is</h2>
 *
 * The half of the Empire you can talk to. Elysium Mobs is what the Empire sends
 * after you; this is who it sends to deal with you. An envoy arrives near a
 * player who has climbed far enough up one of the standing meters, stays about
 * twenty minutes, accepts a tribute and answers with something from their own
 * office, and leaves.
 *
 * <h2>One entity, five people</h2>
 *
 * The same argument Elysium Mobs makes for thirty creatures on eight types, and
 * it is stronger here: all five are humanoid, so five entity types would be
 * five models and five renderers for people who differ in a skin and a piece of
 * regalia. One type, a synced {@link EnvoyKind}, one model that carries every
 * accessory and hides what a kind does not wear.
 *
 * <h2>What it does not contain</h2>
 *
 * No item table. Every envoy pays out of {@code ElysiumRewards}, so the court
 * hands over whatever any installed mod has registered — elysium-core's runes
 * and catalysts when it is present, and whatever else when it is not. A trader
 * carrying its own loot list would be a fourth place to keep item ids in step.
 */
@Mod(ElysiumNpcs.MODID)
public class ElysiumNpcs {

    public static final String MODID = "elysiumnpcs";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, MODID);

    /**
     * The one entity type.
     *
     * {@code MobCategory.MISC} rather than CREATURE or MONSTER: an envoy is
     * never spawned by the world's own spawner. It is placed, by the scheduler
     * or by a player with an egg, and putting it in a spawning category would
     * invite the natural spawner to fill a plains biome with emperors.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ImperialEnvoy>> ENVOY =
            ENTITIES.register("envoy", () -> EntityType.Builder
                    .of(ImperialEnvoy::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(12)
                    .build("envoy"));

    /**
     * One egg per member of the court.
     *
     * They all spawn the same entity type and then set the kind, which is why
     * the egg has to do a little work rather than being a plain
     * DeferredSpawnEggItem for each: the type alone does not say who arrives.
     */
    private static final Map<EnvoyKind, DeferredHolder<Item, Item>> EGGS =
            new LinkedHashMap<>();

    static {
        for (EnvoyKind kind : EnvoyKind.values()) {
            EGGS.put(kind, ITEMS.register(kind.id() + "_summons",
                    () -> new EnvoySummonsItem(kind, new Item.Properties())));
        }
    }

    public ElysiumNpcs(IEventBus modEventBus) {
        modEventBus.addListener(ElysiumNpcEvents::onAttributes);

        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        // The scheduler listens on the game bus, not the mod bus: it runs on
        // server ticks rather than during loading.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(EnvoyScheduler.class);

        LOGGER.info("Elysium Court ready: {} envoys", EnvoyKind.values().length);
    }

    public static DeferredHolder<Item, Item> summonsFor(EnvoyKind kind) {
        return EGGS.get(kind);
    }

    public static List<DeferredHolder<Item, Item>> allSummons() {
        return List.copyOf(EGGS.values());
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> COURT_TAB =
            CREATIVE_TABS.register("court", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elysiumnpcs"))
                    // A lambda: it runs when the tab is built, long after the
                    // registries are full. Resolving a holder here directly is
                    // the unbound-value crash.
                    .icon(() -> new ItemStack(summonsFor(EnvoyKind.EMPEROR).get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredHolder<Item, Item> egg : allSummons()) {
                            output.accept(egg.get());
                        }
                    })
                    .build());
}
