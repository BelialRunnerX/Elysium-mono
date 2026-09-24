package com.elysium.space.client.sky;

import com.elysium.space.ElysiumSpace;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/** Registers one sky per ported world, under the effects id its dimension type names. */
public final class SpaceSkies {

    private SpaceSkies() {
    }

    /** The worlds with a Stellaris sky; the Emerald Bastion keeps the overworld's. */
    public static final String[] WORLDS = {"high_orbit", "pale_moon", "frostfall", "cinderveil"};

    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        for (String world : WORLDS) {
            event.register(ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, world), new SpaceSkyEffects(Sky.of(world)));
        }
    }
}
