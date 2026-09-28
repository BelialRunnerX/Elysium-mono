package com.elysium.industry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.elysium.lib.recipe.ElysiumRecipeKinds;
import com.elysium.industry.recipe.IndustryRecipe;
import com.elysium.industry.registry.IndustryBlocks;
import com.elysium.industry.registry.IndustryRecipes;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/**
 * Elysium Industry: the second age.
 *
 * <h2>The fiction</h2>
 *
 * The Reconstruction. Somebody has worked out what the salvaged gearing in
 * Age I was <em>for</em>, and started building new. This is the age of steel,
 * of poles carrying wire across a valley, of a diesel engine you can hear from
 * the next hill. It is ugly and it is enormous and it works.
 *
 * <h2>The mechanism, and how it differs from Age I</h2>
 *
 * Age I is rigidly coupled: hang another machine on a line and the whole hall
 * slows down together. Age II is divided: ask a grid for more than it has and
 * every machine gets the same share of full speed. Nothing stops in either
 * case — that is the same decision made twice — but a shaft and a wire fail
 * differently, and a player can feel which one they are standing next to.
 *
 * <p>A wire has a capacity and burns if you exceed it, which is why voltage
 * tiers exist rather than being a number on a tooltip; and it loses power per
 * block, which is why where a generator sits is a decision.
 *
 * <h2>What it names</h2>
 *
 * Vanilla, elysium-lib, and elysium-kinetics. The ages depend downwards only:
 * this knows about rotation, and Age I knows nothing about electricity.
 */
@Mod(ElysiumIndustry.MODID)
public class ElysiumIndustry {

    public static final String MODID = "elysiumindustry";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumIndustry");

    public ElysiumIndustry(IEventBus modEventBus) {
        // The recipe kinds, for a recipe browser to show (POLISH.md P6): nothing here names JEI.
        for (IndustryRecipe.Kind kind : IndustryRecipe.Kind.values()) {
            ElysiumRecipeKinds.register(ResourceLocation.fromNamespaceAndPath(MODID, kind.id()),
                    () -> IndustryRecipes.type(kind), switch (kind) {
                        case CRUSHING -> IndustryBlocks.CRUSHER;
                        case ROLLING -> IndustryBlocks.ROLLING_MILL;
                        case SMELTING -> IndustryBlocks.ARC_FURNACE;
                        case SEPARATING -> IndustryBlocks.SEPARATOR;
                    },
                    recipe -> recipe instanceof IndustryRecipe made ? List.of(made.input()) : List.of(),
                    ElysiumRecipeKinds.NOTHING_MORE);
        }
        com.elysium.industry.registry.IndustryBlocks.BLOCKS.register(modEventBus);
        // The wires are runs; each one's plates are its own metal until a dye says otherwise.
        com.elysium.lib.run.Runs.declare(com.elysium.industry.registry.IndustryBlocks.COPPER_WIRE, 0xB87333);
        com.elysium.lib.run.Runs.declare(com.elysium.industry.registry.IndustryBlocks.GOLD_WIRE, 0xE0B040);
        com.elysium.lib.run.Runs.declare(com.elysium.industry.registry.IndustryBlocks.STEEL_WIRE, 0x8A9AA8);
        com.elysium.industry.registry.IndustryItems.ITEMS.register(modEventBus);
        com.elysium.industry.registry.IndustryItems.TABS.register(modEventBus);
        com.elysium.industry.registry.IndustryBlockEntities.TYPES.register(modEventBus);
        com.elysium.industry.registry.IndustryRecipes.TYPES.register(modEventBus);
        com.elysium.industry.registry.IndustryRecipes.SERIALIZERS.register(modEventBus);
        LOGGER.info("Elysium Industry ready");
    }
}
