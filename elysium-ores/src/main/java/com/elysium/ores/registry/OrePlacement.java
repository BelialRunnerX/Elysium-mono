package com.elysium.ores.registry;

import com.elysium.ores.ElysiumOres;
import com.elysium.ores.place.FamilyPresent;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The one placement modifier this module adds. */
public final class OrePlacement {

    private OrePlacement() {
    }

    public static final DeferredRegister<PlacementModifierType<?>> TYPES =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, ElysiumOres.MODID);

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<FamilyPresent>>
            FAMILY_PRESENT = TYPES.register("family_present",
                    () -> () -> FamilyPresent.CODEC);

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
