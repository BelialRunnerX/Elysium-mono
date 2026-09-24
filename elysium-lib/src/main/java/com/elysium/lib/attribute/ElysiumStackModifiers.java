package com.elysium.lib.attribute;

import com.elysium.lib.ElysiumLib;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-stack attribute modifiers (SPEC_CLONE P0.3). This is the way affixes,
 * gems and armory parts put attributes on an item without writing them into
 * the item's stored component. The modifiers are worked out again each time
 * vanilla asks, so changing an affix's numbers updates every existing item.
 *
 * <pre>
 * ElysiumStackModifiers.register("elysiumascendancy:affixes", (stack, sink) -&gt; {
 *     sink.add(ElysiumAttributes.get("crit_chance"), modifier, EquipmentSlotGroup.MAINHAND);
 * });
 * </pre>
 *
 * A contributor that throws or strays is logged the first time and skipped
 * for that stack. It does
 * not take down every other contributor, the tooltip, or the server tick. See
 * {@link ElysiumContributorTable} for the naming and ordering rules.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumStackModifiers {

    private ElysiumStackModifiers() {
    }

    @FunctionalInterface
    public interface Sink {
        void add(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot);
    }

    @FunctionalInterface
    public interface Contributor {
        void contribute(ItemStack stack, Sink sink);
    }

    private static final ElysiumContributorTable<Contributor> TABLE = new ElysiumContributorTable<>();
    private static final Set<String> COMPLAINED = java.util.Collections.synchronizedSet(new HashSet<>());

    public static void register(String name, Contributor contributor) {
        TABLE.register(name, contributor);
    }

    // LOW, so the item's own modifiers and anything that rewrites them run first.
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || TABLE.size() == 0) {
            return;
        }
        for (Map.Entry<String, Contributor> entry : TABLE.ordered()) {
            String name = entry.getKey();
            Sink sink = (attribute, modifier, slot) -> {
                if (!ElysiumContributorTable.owns(name, modifier.id().toString())) {
                    complain(name, "added modifier " + modifier.id() + " outside its namespace");
                    return;
                }
                event.addModifier(attribute, modifier, slot);
            };
            try {
                entry.getValue().contribute(stack, sink);
            } catch (RuntimeException | LinkageError e) {
                complain(name, e.toString());
            }
        }
    }

    private static void complain(String name, String what) {
        if (COMPLAINED.add(name)) {   // once per contributor, so the set stays small
            ElysiumLib.LOGGER.error("stack modifier contributor {} skipped: {}", name, what);
        }
    }
}
