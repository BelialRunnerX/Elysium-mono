package com.elysium.lib.client;

import com.elysium.lib.ElysiumLib;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a piece of gear actually does, in one line each (Apotheosis' AA-11).
 *
 * <h2>What is wrong with the vanilla tooltip</h2>
 *
 * A diamond sword says "+3 Attack Damage" and "+1.6 Attack Speed", both in
 * blue, under a header — and neither number is the number. The sword's real
 * damage is seven, because the player's own base of one is added somewhere the
 * tooltip never mentions; the speed is 1.6 because 4.0 minus 2.4 happened
 * off-screen. Add a Sharpness, an affix and a gear set and there are nine
 * lines, four of which are percentages of something unstated.
 *
 * <h2>What this does instead</h2>
 *
 * One line per attribute per slot, with the additions summed, the percentages
 * folded in, and the base included where the game includes it. Nine lines
 * become three, and the numbers are the ones the player will feel.
 *
 * <p>Held {@link ElysiumKeys#DETAIL}, it steps out of the way entirely: every
 * modifier is listed separately again, because when a player is comparing two
 * affixes they want the parts rather than the total.
 *
 * <h2>Why vanilla's lines are removed rather than hidden</h2>
 *
 * Hiding them means setting {@code show_in_tooltip} to false on every stack in
 * the game, which is a change to the item rather than to the tooltip and
 * travels with the item into other mods' screens and other people's worlds.
 * Removing the lines as they are rendered changes nothing but what is on the
 * screen.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID, value = Dist.CLIENT)
public final class ElysiumAttributeTooltips {

    private ElysiumAttributeTooltips() {
    }

    /** The keys vanilla writes its attribute lines under. */
    private static final String MODIFIER_PREFIX = "attribute.modifier.";
    private static final String HEADER_PREFIX = "item.modifiers.";

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ItemAttributeModifiers modifiers = stack.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (stack.isEmpty() || modifiers.modifiers().isEmpty()) {
            return;
        }
        if (ElysiumKeys.detailWanted()) {
            // Out of the way: vanilla's own lines are what a player comparing
            // two affixes wants, and they are already there.
            return;
        }
        List<Component> lines = event.getToolTip();
        int at = removeVanillaLines(lines);
        if (at < 0) {
            return;
        }
        lines.addAll(at, summarise(modifiers));
    }

    /**
     * Take vanilla's attribute block out, and say where it was.
     *
     * Matched on the translation keys vanilla uses rather than on position:
     * another mod's line in the middle of the block would otherwise be
     * swallowed, and a mod that adds nothing would still have its tooltip
     * rearranged.
     */
    private static int removeVanillaLines(List<Component> lines) {
        int first = -1;
        for (int index = lines.size() - 1; index >= 0; index--) {
            if (!ours(lines.get(index))) {
                continue;
            }
            lines.remove(index);
            first = index;
        }
        return first;
    }

    private static boolean ours(Component line) {
        if (line.getContents() instanceof TranslatableContents contents) {
            String key = contents.getKey();
            if (key.startsWith(MODIFIER_PREFIX) || key.startsWith(HEADER_PREFIX)) {
                return true;
            }
        }
        // Vanilla wraps each modifier line in a style and a leading space, so
        // the key is on the first sibling rather than on the line itself.
        for (Component part : line.getSiblings()) {
            if (part.getContents() instanceof TranslatableContents contents
                    && contents.getKey().startsWith(MODIFIER_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    /** One line per attribute per slot, in the order the slots are worn. */
    private static List<Component> summarise(ItemAttributeModifiers modifiers) {
        Map<EquipmentSlotGroup, Map<Holder<Attribute>, double[]>> bySlot =
                new LinkedHashMap<>();
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            Map<Holder<Attribute>, double[]> totals =
                    bySlot.computeIfAbsent(entry.slot(), slot -> new LinkedHashMap<>());
            double[] sums = totals.computeIfAbsent(entry.attribute(),
                    attribute -> new double[3]);
            AttributeModifier modifier = entry.modifier();
            sums[modifier.operation().ordinal()] += modifier.amount();
        }
        List<Component> lines = new ArrayList<>();
        for (Map.Entry<EquipmentSlotGroup, Map<Holder<Attribute>, double[]>> slot
                : bySlot.entrySet()) {
            lines.add(Component.empty());
            lines.add(Component.translatable(
                    HEADER_PREFIX + slot.getKey().getSerializedName())
                    .withStyle(ChatFormatting.GRAY));
            for (Map.Entry<Holder<Attribute>, double[]> attribute : slot.getValue().entrySet()) {
                lines.add(line(attribute.getKey(), attribute.getValue()));
            }
        }
        return lines;
    }

    /**
     * One attribute's whole contribution.
     *
     * The flat part first because it is the one a player reads, then the two
     * percentage operations folded into one bracket. Green when it helps and
     * red when it does not, which is the one thing vanilla's blue never said.
     */
    private static Component line(Holder<Attribute> attribute, double[] sums) {
        double flat = sums[AttributeModifier.Operation.ADD_VALUE.ordinal()];
        double percent = sums[AttributeModifier.Operation.ADD_MULTIPLIED_BASE.ordinal()]
                + sums[AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL.ordinal()];
        MutableComponent text = Component.literal(" ");
        boolean good = flat > 0 || (flat == 0 && percent > 0);
        if (flat != 0) {
            text.append(Component.literal(format(flat) + " "));
        }
        text.append(Component.translatable(attribute.value().getDescriptionId()));
        if (percent != 0) {
            text.append(Component.literal(" (" + format(percent * 100.0D) + "%)"));
        }
        return text.withStyle(good ? ChatFormatting.DARK_GREEN : ChatFormatting.RED);
    }

    private static String format(double value) {
        String shown = Math.abs(value - Math.rint(value)) < 1.0E-4D
                ? String.valueOf((long) Math.rint(value))
                : String.format("%.2f", value);
        return value > 0 ? "+" + shown : shown;
    }
}
