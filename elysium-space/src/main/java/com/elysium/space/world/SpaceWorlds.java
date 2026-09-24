package com.elysium.space.world;

import com.elysium.space.ElysiumSpace;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** {@link SpaceWorld} rows matched to the dimensions the game loads. */
public final class SpaceWorlds {

    private SpaceWorlds() {
    }

    public static ResourceKey<Level> key(SpaceWorld world) {
        return ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, world.path()));
    }

    /** The space world a level is, or null for the Overworld and every other dimension. */
    public static SpaceWorld of(Level level) {
        if (level == null) {
            return null;
        }
        ResourceLocation id = level.dimension().location();
        return ElysiumSpace.MODID.equals(id.getNamespace()) ? SpaceWorld.named(id.getPath()) : null;
    }
}
