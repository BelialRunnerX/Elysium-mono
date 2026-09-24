package com.elysium.space;

import com.elysium.lib.hazard.ElysiumHazardField;
import com.elysium.space.hazard.SpaceAtmosphere;
import com.elysium.space.hazard.SpaceHazards;
import com.elysium.space.registry.SpaceBlockEntities;
import com.elysium.space.registry.SpaceBlocks;
import com.elysium.space.registry.SpaceItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Elysium Space: other worlds, the air you cannot breathe on them, and the
 * rooms that let you.
 *
 * Ported from Stellaris (Exodus Studio, CC BY-NC-SA 4.0; see CREDITS.md) and
 * rebuilt on Elysium Library: its planets' hazards run through the library's
 * hazard pipeline, and "enclosed" is the library's room rule. The plan is
 * docs/SPEC_SPACE.md.
 */
@Mod(ElysiumSpace.MODID)
public final class ElysiumSpace {

    public static final String MODID = "elysiumspace";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ElysiumSpace(IEventBus modEventBus) {
        // What standing each of these worlds takes to be standing on. Told to
        // the library, because the library is what gates loot by tier and it
        // cannot know what dimensions a pack has.
        com.elysium.space.world.SpaceTiers.declare();
        com.elysium.space.registry.SpaceFluids.TYPES.register(modEventBus);
        com.elysium.space.registry.SpaceFluids.FLUIDS.register(modEventBus);
        com.elysium.space.registry.SpaceFluids.BLOCKS.register(modEventBus);
        com.elysium.space.registry.SpaceFluids.ITEMS.register(modEventBus);
        SpaceBlocks.BLOCKS.register(modEventBus);
        com.elysium.space.worldgen.SpaceProcessors.TYPES.register(modEventBus);
        com.elysium.space.registry.SpaceData.ATTACHMENTS.register(modEventBus);
        com.elysium.space.registry.SpaceData.COMPONENTS.register(modEventBus);
        com.elysium.space.registry.SpaceArmor.MATERIALS.register(modEventBus);
        SpaceItems.ITEMS.register(modEventBus);
        SpaceItems.TABS.register(modEventBus);
        SpaceBlockEntities.TYPES.register(modEventBus);
        com.elysium.space.registry.SpaceEntities.TYPES.register(modEventBus);
        com.elysium.space.registry.SpaceMenus.TYPES.register(modEventBus);
        // Hazards and hazard sources are plain registries in the library, not
        // game registries, so they are filled here at construction.
        SpaceHazards.bootstrap();
        ElysiumHazardField.registerSource(MODID + ":atmosphere", new SpaceAtmosphere());
        // And what each of those skies does when it turns. Weather registers
        // itself as another hazard source, so a storm is answered by exactly
        // what answers a planet's air: armour, a ward, a room, a claim.
        com.elysium.space.world.SpaceWeather.declare();
        LOGGER.info("Elysium Space ready");
    }
}
