package com.elysium.armory.item;

import com.elysium.armory.material.ArmoryPart;
import com.elysium.armory.material.ArmoryParts;
import com.elysium.armory.registry.ArmoryComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The plan book: one item that remembers every blueprint you have shown it.
 *
 * <h2>Why it learns rather than holds</h2>
 *
 * Silent Gear's blueprint book is a container — it solves "my inventory is
 * forty pieces of paper" by making it "my book is forty pieces of paper". The
 * problem it does not solve is the one that actually bites, which is finding
 * the right one at the bench.
 *
 * <p>So this eats a blueprint and keeps the knowledge. At the Gear Smithing
 * Table it stands in for any plan it has learned, and a plan it has learned is
 * learned for good. A template — the single-use kind — is deliberately *not*
 * learnable: a plan you were only ever given once is not a plan you know.
 */
public class ArmoryPlanBookItem extends Item {

    public ArmoryPlanBookItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ArmoryPlanBook plansOf(ItemStack stack) {
        return stack.getOrDefault(ArmoryComponents.PLANS.get(), ArmoryPlanBook.BLANK);
    }

    /** Whether this stack is a book that knows this part's plan. */
    public static boolean knows(ItemStack stack, String part) {
        return stack.getItem() instanceof ArmoryPlanBookItem
                && plansOf(stack).knows(part);
    }

    /**
     * The book, having learned what a blueprint plans.
     *
     * @return the book with the plan in it, or the same book when there was
     *         nothing to learn — so a caller can tell whether the blueprint
     *         was worth consuming
     */
    public static ItemStack taught(ItemStack book, String part) {
        ArmoryPlanBook plans = plansOf(book);
        ArmoryPlanBook after = plans.learn(part);
        if (after == plans) {
            return book;
        }
        ItemStack out = book.copy();
        out.set(ArmoryComponents.PLANS.get(), after);
        return out;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        ArmoryPlanBook plans = plansOf(stack);
        if (plans.isEmpty()) {
            tooltip.add(Component.translatable("elysiumarmory.planbook.blank")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("elysiumarmory.planbook.count",
                plans.size()).withStyle(ChatFormatting.GRAY));
        // The plans themselves, but only a handful: a book that has learned
        // forty of them would otherwise be a tooltip taller than the screen.
        int shown = 0;
        for (String part : plans.plans()) {
            if (shown++ >= SHOWN) {
                tooltip.add(Component.translatable("elysiumarmory.planbook.more",
                        plans.size() - SHOWN).withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            ArmoryPart known = ArmoryParts.get().byId(part);
            tooltip.add(Component.literal("  ")
                    .append(known == null ? Component.literal(part)
                            : Component.translatable("item.elysiumarmory."
                                    + part.substring(part.indexOf(':') + 1)))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** How many plans a tooltip names before it stops. */
    public static final int SHOWN = 6;
}
