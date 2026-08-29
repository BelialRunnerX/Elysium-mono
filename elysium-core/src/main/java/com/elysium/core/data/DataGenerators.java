package com.elysium.core.data;

import com.elysium.core.Elysium;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Data generation entry point. Run with {@code gradlew runData}; output lands
 * in {@code src/generated/resources}.
 *
 * Every asset the mod needs is also committed by hand under
 * {@code src/main/resources}, so a plain {@code gradlew build} produces a
 * complete jar without anyone having to run datagen first. When datagen is run,
 * the hand-written copies win (see the {@code duplicatesStrategy} in
 * build.gradle).
 */
@EventBusSubscriber(modid = Elysium.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class DataGenerators {

    private DataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        generator.addProvider(event.includeServer(),
                new ElysiumRecipeProvider(output, lookupProvider));
        generator.addProvider(event.includeClient(),
                new ElysiumBlockStateProvider(output, existingFileHelper));
    }
}
