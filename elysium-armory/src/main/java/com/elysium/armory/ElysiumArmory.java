package com.elysium.armory;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Elysium Armory — modular gear: the property engine, and everything built on it.
 *
 * One of Elysium's separate module jars, merged into elysium-all with the
 * rest. It depends on the library and nothing else: not on elysium-core, and
 * not on the mod whose mechanics it replaces. See docs/SPEC_CLONE.md for the
 * build order it is working down.
 */
@Mod(ElysiumArmory.MODID)
public final class ElysiumArmory {

    public static final String MODID = "elysiumarmory";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ElysiumArmory(IEventBus modEventBus) {
        com.elysium.armory.registry.ArmoryComponents.register(modEventBus);
        com.elysium.armory.registry.ArmoryItems.register(modEventBus);
        com.elysium.armory.registry.ArmoryRecipes.register(modEventBus);
        com.elysium.armory.registry.ArmoryBlocks.register(modEventBus);
        com.elysium.armory.registry.ArmoryBlockEntities.register(modEventBus);
        com.elysium.armory.registry.ArmoryMenus.register(modEventBus);
        com.elysium.armory.registry.ArmoryArmorMaterial.register(modEventBus);
        com.elysium.armory.loot.ArmoryLoot.register(modEventBus);
        // The traits a piece was built with, offered to the library as one of
        // the things that can be attached to an item. Everything that reads an
        // effect reads it from there, so a trait and an affix are acted on by
        // the same code.
        com.elysium.armory.trait.ArmoryTraitEffects.register();
        // Jewellery is worn in a slot this module is not allowed to know
        // about: the library holds the list of what wants wearing and the
        // accessory bridge reads it. At setup rather than here, because an
        // item resolved during construction is a crash.
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event)
                -> event.enqueueWork(
                        com.elysium.armory.registry.ArmoryItems::declareWearables));
        LOGGER.info("Elysium Armory ready");
    }
}
