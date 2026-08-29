package com.elysium.core.data;

import com.elysium.core.Elysium;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.util.concurrent.CompletableFuture;

/**
 * Generates the crafting recipes.
 *
 * 1.21.1 changed both ends of this: the constructor takes a {@link PackOutput}
 * plus a registry lookup future, and {@code buildRecipes} receives a
 * {@link RecipeOutput} rather than a {@code Consumer<FinishedRecipe>}.
 *
 * Note there are no rune-socketing or ascension recipes here. Doing those on a
 * crafting grid destroys the item's components - the player would feed in a
 * fully socketed, reforged piece and get a blank one back. Both operations live
 * on the workstation blocks instead, where the data is preserved.
 */
public class ElysiumRecipeProvider extends RecipeProvider {

    public ElysiumRecipeProvider(PackOutput output,
                                 CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        // Neutronium block <-> ingots
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, Elysium.NEUTRONIUM_BLOCK_ITEM.get())
                .pattern("###")
                .pattern("###")
                .pattern("###")
                .define('#', Elysium.NEUTRONIUM_INGOT.get())
                .unlockedBy("has_neutronium_ingot", has(Elysium.NEUTRONIUM_INGOT.get()))
                .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Elysium.NEUTRONIUM_INGOT.get(), 9)
                .requires(Elysium.NEUTRONIUM_BLOCK_ITEM.get())
                .unlockedBy("has_neutronium_block", has(Elysium.NEUTRONIUM_BLOCK_ITEM.get()))
                .save(output, ResourceLocation.fromNamespaceAndPath(Elysium.MODID, "neutronium_ingot_from_block"));

        // Neutronium armour set
        armour(output, Elysium.NEUTRONIUM_HELMET.get(), "###", "# #");
        armour(output, Elysium.NEUTRONIUM_CHESTPLATE.get(), "# #", "###", "###");
        armour(output, Elysium.NEUTRONIUM_LEGGINGS.get(), "###", "# #", "# #");
        armour(output, Elysium.NEUTRONIUM_BOOTS.get(), "# #", "# #");

        // Reforge catalyst
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Elysium.ELYSIUM_REFORGE.get())
                .pattern(" a ")
                .pattern("ava")
                .pattern(" a ")
                .define('a', Elysium.AETHERIUM_INGOT.get())
                .define('v', Elysium.VOIDGLASS_INGOT.get())
                .unlockedBy("has_aetherium_ingot", has(Elysium.AETHERIUM_INGOT.get()))
                .save(output);

        // Workstations
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, Elysium.REFORGE_TABLE_ITEM.get())
                .pattern("###")
                .pattern("iai")
                .pattern("iii")
                .define('#', Elysium.NEUTRONIUM_INGOT.get())
                .define('i', Items.IRON_BLOCK)
                .define('a', Items.ANVIL)
                .unlockedBy("has_neutronium_ingot", has(Elysium.NEUTRONIUM_INGOT.get()))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, Elysium.RUNE_SOCKET_TABLE_ITEM.get())
                .pattern("vvv")
                .pattern("i#i")
                .pattern("iii")
                .define('v', Elysium.VOIDGLASS_INGOT.get())
                .define('#', Elysium.NEUTRONIUM_INGOT.get())
                .define('i', Items.OBSIDIAN)
                .unlockedBy("has_voidglass_ingot", has(Elysium.VOIDGLASS_INGOT.get()))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, Elysium.ASCENSION_FORGE_ITEM.get())
                .pattern("aaa")
                .pattern("i#i")
                .pattern("iii")
                .define('a', Elysium.AETHERIUM_INGOT.get())
                .define('#', Elysium.NEUTRONIUM_INGOT.get())
                .define('i', Items.OBSIDIAN)
                .unlockedBy("has_aetherium_ingot", has(Elysium.AETHERIUM_INGOT.get()))
                .save(output);
    }

    private void armour(RecipeOutput output, ItemLike result, String... rows) {
        ShapedRecipeBuilder builder = ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, result);
        for (String row : rows) {
            builder.pattern(row);
        }
        builder.define('#', Elysium.NEUTRONIUM_INGOT.get())
                .unlockedBy("has_neutronium_ingot", has(Elysium.NEUTRONIUM_INGOT.get()))
                .save(output);
    }
}
