package com.elysium.core.item;

import com.elysium.lib.item.ElysiumRune;
import com.elysium.lib.item.ElysiumRuneStacks;
import com.elysium.lib.item.ElysiumRuneTiers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The item a player holds. Runes are consumed when socketed into Elysium gear
 * at a workstation, granting the piece a permanent bonus.
 *
 * <h2>Why the item and the rune are two things</h2>
 *
 * The {@link ElysiumRune} is the definition — its element, its affix, its
 * effect — and lives in the library, where the socket system, the tick handler
 * and the combat pipeline can all read it without knowing which mod added it.
 * This item is just the physical object that carries one, registered by
 * whichever mod wants players to be able to pick it up.
 *
 * That split is what lets an add-on ship a rune with no item at all (a reward
 * applied straight to gear) or an item for somebody else's rune.
 */
public class ElysiumRuneItem extends Item {

    private final ElysiumRune rune;

    public ElysiumRuneItem(ElysiumRune rune) {
        super(new Item.Properties()
                .stacksTo(16)
                .rarity(rune.isUtility() ? Rarity.RARE : Rarity.UNCOMMON));
        this.rune = rune;
    }

    public ElysiumRune getRune() {
        return rune;
    }

    /** "Voidward Rune III". The base tier keeps its plain name. */
    @Override
    public Component getName(ItemStack stack) {
        int tier = ElysiumRuneStacks.tierOf(stack);
        Component base = super.getName(stack);
        return tier <= ElysiumRuneTiers.BASE ? base
                : Component.translatable("elysium.rune.tiered_name", base,
                        ElysiumRuneTiers.numeral(tier));
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                Item.TooltipContext context,
                                List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(rune.getEffectLine());
        // The tier and what it is worth, stated as the rule: tier N, N times.
        int tier = ElysiumRuneStacks.tierOf(stack);
        tooltip.add(Component.translatable("elysium.tooltip.rune_tier_item",
                        ElysiumRuneTiers.numeral(tier), tier)
                .withStyle(tier > ElysiumRuneTiers.BASE
                        ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY));
        if (tier < ElysiumRuneTiers.MAX_TIER) {
            tooltip.add(Component.translatable("elysium.tooltip.rune_merge_hint",
                            ElysiumRuneTiers.numeral(tier),
                            ElysiumRuneTiers.numeral(ElysiumRuneTiers.merged(tier)))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("elysium.tooltip.rune_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
