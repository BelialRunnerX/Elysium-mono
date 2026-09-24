package com.elysium.lib.affix;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * What an affixed item is called, and what its tooltip says.
 *
 * <p>The name is built rather than stored: an item whose name was written into
 * it at the moment it dropped would keep saying "Keen" after a pack renamed
 * the affix, or after the affix stopped existing. Built each time, the name is
 * always what the item currently is.
 */
public final class ElysiumAffixNames {

    private ElysiumAffixNames() {
    }

    /**
     * The item's name with its prefix and suffix, or the plain name when it
     * has neither.
     */
    public static Component name(ItemStack stack, Component base) {
        ElysiumItemAffixes affixes = ElysiumAffixEffects.of(stack);
        if (affixes.isEmpty()) {
            return base;
        }
        Component prefix = placed(affixes, ElysiumAffixRules.PREFIX);
        Component suffix = placed(affixes, ElysiumAffixRules.SUFFIX);
        MutableComponent out = Component.empty();
        if (prefix != null) {
            out.append(prefix).append(" ");
        }
        out.append(base);
        if (suffix != null) {
            out.append(" ").append(suffix);
        }
        return out;
    }

    /** The first affix of a placement, which is the one the name has room for. */
    private static Component placed(ElysiumItemAffixes affixes, String type) {
        for (Map.Entry<String, Integer> entry : affixes.levels().entrySet()) {
            ElysiumAffixDefinition affix = ElysiumAffixCatalogue.get().byId(entry.getKey());
            if (affix != null && affix.type().equals(type)) {
                return Component.translatable(affix.nameKey());
            }
        }
        return null;
    }

    /**
     * One tooltip line per affix, so an item carrying more than a name's worth
     * of them still says what it does.
     */
    public static void tooltip(ItemStack stack, java.util.List<Component> lines) {
        ElysiumItemAffixes affixes = ElysiumAffixEffects.of(stack);
        for (Map.Entry<String, Integer> entry : affixes.levels().entrySet()) {
            ElysiumAffixDefinition affix = ElysiumAffixCatalogue.get().byId(entry.getKey());
            if (affix == null) {
                continue;
            }
            Component named = Component.translatable(affix.nameKey());
            // A level is only worth showing on an affix that has more than
            // one; "Keen I" on an affix that only ever rolls at I is noise.
            lines.add((affix.maxLevel() > 1
                    ? Component.translatable("elysium.affix.line.level", named,
                            Component.literal(String.valueOf(entry.getValue())))
                    : Component.translatable("elysium.affix.line", named))
                    .withStyle(ChatFormatting.BLUE));
        }
    }
}
