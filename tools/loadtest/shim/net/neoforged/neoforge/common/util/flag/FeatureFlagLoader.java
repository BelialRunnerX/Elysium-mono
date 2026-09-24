package net.neoforged.neoforge.common.util.flag;

import net.minecraft.world.flag.FeatureFlagRegistry;

/**
 * A stand-in for the real one, which asks FML which mods are loaded.
 *
 * Nothing is loaded here: this is a harness that boots the game's registries
 * outside the launcher so our data can be loaded the way the game will load
 * it. No mod declares a feature flag, so doing nothing is the right answer as
 * well as the only available one.
 */
public final class FeatureFlagLoader {

    private FeatureFlagLoader() {
    }

    public static void loadModdedFlags(FeatureFlagRegistry.Builder builder) {
    }
}
