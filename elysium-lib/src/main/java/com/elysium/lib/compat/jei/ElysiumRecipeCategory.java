package com.elysium.lib.compat.jei;

import com.elysium.lib.recipe.ElysiumRecipeKinds;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * One machine's recipes, drawn the same way for every machine: its inputs in
 * a row, an arrow, the result, and any recovered companion beside it. The
 * category is titled with the machine's own name, so a player who knows the
 * block finds the recipes under it and no language key is needed.
 */
public class ElysiumRecipeCategory implements IRecipeCategory<RecipeHolder<Recipe<?>>> {

    private static final int SLOT = 18;
    private static final int ARROW_W = 24;
    private static final int MOST_INPUTS = 3;
    private static final int MOST_OUTPUTS = 2;

    private final ElysiumRecipeKinds.Kind kind;
    private final RecipeType<RecipeHolder<Recipe<?>>> type;
    private final IDrawable icon;
    private final IDrawableStatic arrow;

    public ElysiumRecipeCategory(ElysiumRecipeKinds.Kind kind, IGuiHelper helper) {
        this.kind = kind;
        this.type = ElysiumJeiPlugin.jeiType(kind);
        this.icon = helper.createDrawableItemLike(kind.catalyst().get());
        this.arrow = helper.getRecipeArrow();
    }

    @Override
    public RecipeType<RecipeHolder<Recipe<?>>> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return kind.catalyst().get().asItem().getDescription();
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return MOST_INPUTS * SLOT + 4 + ARROW_W + 4 + MOST_OUTPUTS * SLOT;
    }

    @Override
    public int getHeight() {
        return SLOT + 14;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<Recipe<?>> holder, IFocusGroup focuses) {
        Recipe<?> recipe = holder.value();
        List<Ingredient> inputs = kind.inputs().apply(recipe);
        int x = 0;
        for (int i = 0; i < Math.min(inputs.size(), MOST_INPUTS); i++) {
            builder.addInputSlot(x + 1, 1).setStandardSlotBackground().addIngredients(inputs.get(i));
            x += SLOT;
        }
        int outX = MOST_INPUTS * SLOT + 4 + ARROW_W + 4;
        ItemStack result = recipe.getResultItem(Minecraft.getInstance().level == null ? null
                : Minecraft.getInstance().level.registryAccess());
        builder.addOutputSlot(outX + 1, 1).setStandardSlotBackground().addItemStack(result);
        List<ItemStack> extras = kind.extras().apply(recipe);
        for (int i = 0; i < Math.min(extras.size(), MOST_OUTPUTS - 1); i++) {
            builder.addOutputSlot(outX + SLOT * (i + 1) + 1, 1).setStandardSlotBackground().addItemStack(extras.get(i));
        }
    }

    @Override
    public void draw(RecipeHolder<Recipe<?>> holder, IRecipeSlotsView view, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        arrow.draw(graphics, MOST_INPUTS * SLOT + 4, 1);
        // The machine's name under the row, since a category is one machine.
        graphics.drawString(Minecraft.getInstance().font, getTitle(), 0, SLOT + 4, 0x808080, false);
    }
}
