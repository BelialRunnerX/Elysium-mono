package com.elysium.lib.affix;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.attribute.ElysiumStackModifiers;
import com.elysium.lib.effect.ElysiumItemEffects;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Locale;

/**
 * Where an affix meets the game.
 *
 * <p>Two things an affix needs that the shared effect engine cannot do for it,
 * because they are about the item rather than about what it does:
 *
 * <ul>
 *   <li><b>Attributes.</b> An affix's attribute effect has to reach an item
 *       that knows nothing about affixes — a vanilla iron sword — so it is
 *       contributed per stack rather than written into the item. That is what
 *       {@link ElysiumStackModifiers} is for, and it means changing an affix's
 *       numbers changes every sword that already rolled it.</li>
 *   <li><b>Saying so.</b> A line per affix in the tooltip. The name itself is
 *       built by {@link ElysiumAffixNames} wherever a name is asked for.</li>
 * </ul>
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumAffixHandler {

    private ElysiumAffixHandler() {
    }

    /** Contributes every affix's attribute effects. Called once, at mod construction. */
    public static void register() {
        ElysiumStackModifiers.register("elysiumlib:affixes", (stack, sink) -> {
            for (ElysiumItemEffects.Active active : ElysiumItemEffects.of(stack, "attribute")) {
                if (!active.effect().has("modifiers")) {
                    continue;
                }
                for (JsonElement element : active.effect().getAsJsonArray("modifiers")) {
                    contribute(sink, active, element.getAsJsonObject());
                }
            }
        });
    }

    private static void contribute(ElysiumStackModifiers.Sink sink,
                                   ElysiumItemEffects.Active active, JsonObject spec) {
        Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE
                .getHolder(ResourceKey.create(Registries.ATTRIBUTE,
                        ResourceLocation.parse(spec.get("attribute").getAsString())))
                .orElse(null);
        if (attribute == null) {
            return;                        // an attribute from a mod that is not here
        }
        double value = value(spec, active.level());
        if (value == 0.0D) {
            return;
        }
        AttributeModifier.Operation operation = AttributeModifier.Operation.valueOf(
                spec.get("operation").getAsString().toUpperCase(Locale.ROOT));
        EquipmentSlotGroup group = group(spec.has("slot") ? spec.get("slot").getAsString() : "any");
        // The id carries the source, so two affixes on one attribute are two
        // modifiers rather than one overwriting the other — and it stays in
        // this mod's namespace, which the contributor table insists on.
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID,
                "affix/" + active.source().replace(':', '/') + "/"
                        + group.name().toLowerCase(Locale.ROOT));
        sink.add(attribute, new AttributeModifier(id, value, operation), group);
    }

    /**
     * The value at this level.
     *
     * Two shapes, because affixes and traits state strength differently and
     * both had to keep working: a list with one entry per level, which is how
     * a trait says it, or a min and a max the level walks between, which is how
     * an affix rolls.
     */
    private static double value(JsonObject spec, int level) {
        if (spec.has("values")) {
            var values = spec.getAsJsonArray("values");
            if (values.isEmpty() || level <= 0) {
                return 0.0D;
            }
            return values.get(Math.min(level, values.size()) - 1).getAsDouble();
        }
        if (spec.has("min") && spec.has("max")) {
            int max = spec.has("levels") ? spec.get("levels").getAsInt() : 1;
            double at = max <= 1 ? 1.0D : (double) (level - 1) / (max - 1);
            return ElysiumAffixRules.between(spec.get("min").getAsDouble(),
                    spec.get("max").getAsDouble(), at);
        }
        return spec.has("value") ? spec.get("value").getAsDouble() * Math.max(1, level) : 0.0D;
    }

    private static EquipmentSlotGroup group(String name) {
        try {
            return EquipmentSlotGroup.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return EquipmentSlotGroup.ANY;
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (ElysiumAffixEffects.of(stack).isEmpty()) {
            return;
        }
        java.util.List<Component> lines = new java.util.ArrayList<>();
        ElysiumAffixNames.tooltip(stack, lines);
        event.getToolTip().addAll(lines);
    }
}
