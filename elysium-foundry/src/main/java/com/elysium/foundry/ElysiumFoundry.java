package com.elysium.foundry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.elysium.lib.recipe.ElysiumRecipeKinds;
import com.elysium.foundry.recipe.AlloyRecipe;
import com.elysium.foundry.recipe.RefineRecipe;
import com.elysium.foundry.registry.FoundryBlocks;
import com.elysium.foundry.registry.FoundryRecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.List;

/**
 * Elysium Foundry: the fourth age.
 *
 * <h2>It adds no new physics, and that is the point</h2>
 *
 * Age I is rotation, Age II a divided grid, Age III a buffered one. There is
 * no fourth mechanism. What this age adds is the thing the other three were
 * built to pay for: a material chain deep enough that a factory becomes a
 * <em>supply</em> problem rather than a crafting problem.
 *
 * <h2>And it pays a debt</h2>
 *
 * {@code elysium-ores} generates ore for thirty-one materials, and most of
 * them were only ever supplied by the mods this conversion is replacing — so
 * without this module, a great many of the ores in the world drop nothing at
 * all once those jars go. The metals are here, and the module's validator
 * fails the build if an ore family the pack generates has no supplier
 * anywhere in it.
 *
 * <h2>What is checked</h2>
 *
 * Two things, over the whole declared chain, by execution rather than by
 * reading: no material is made of itself however far down, and no step gives
 * back more material than went into it. A pack that gets either wrong does not
 * crash — it leaks, and by the time anybody notices, every save has a chest of
 * free metal in it.
 */
@Mod(ElysiumFoundry.MODID)
public class ElysiumFoundry {

    public static final String MODID = "elysiumfoundry";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElysiumFoundry");

    public ElysiumFoundry(IEventBus modEventBus) {
        // The recipe kinds, for a recipe browser to show (POLISH.md P6): nothing here names JEI.
        ElysiumRecipeKinds.register(ResourceLocation.fromNamespaceAndPath(MODID, "alloying"),
                FoundryRecipes.ALLOYING::get, FoundryBlocks.ALLOY_SMELTER,
                recipe -> recipe instanceof AlloyRecipe alloy
                        ? List.of(counted(alloy.first(), alloy.firstCount()), counted(alloy.second(), alloy.secondCount()))
                        : List.of(),
                ElysiumRecipeKinds.NOTHING_MORE);
        ElysiumRecipeKinds.register(ResourceLocation.fromNamespaceAndPath(MODID, "refining"),
                FoundryRecipes.REFINING::get, FoundryBlocks.REFINERY,
                recipe -> recipe instanceof RefineRecipe refine ? List.of(refine.input()) : List.of(),
                recipe -> recipe instanceof RefineRecipe refine ? List.of(refine.recovered()) : List.of());
        com.elysium.foundry.registry.FoundryBlocks.BLOCKS.register(modEventBus);
        com.elysium.foundry.registry.FoundryItems.ITEMS.register(modEventBus);
        com.elysium.foundry.registry.FoundryItems.TABS.register(modEventBus);
        com.elysium.foundry.registry.FoundryBlockEntities.TYPES.register(modEventBus);
        com.elysium.foundry.registry.FoundryRecipes.TYPES.register(modEventBus);
        com.elysium.foundry.registry.FoundryRecipes.SERIALIZERS.register(modEventBus);
        LOGGER.info("Elysium Foundry ready");
    }

    /** An ingredient shown with the count the smelter takes of it. */
    private static Ingredient counted(Ingredient ingredient, int count) {
        if (count <= 1) {
            return ingredient;
        }
        ItemStack[] stacks = ingredient.getItems();
        ItemStack[] sized = new ItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            sized[i] = stacks[i].copyWithCount(count);
        }
        return Ingredient.of(sized);
    }
}
