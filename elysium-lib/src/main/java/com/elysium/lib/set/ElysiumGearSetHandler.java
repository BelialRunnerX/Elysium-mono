package com.elysium.lib.set;

import com.elysium.lib.ElysiumLib;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Where a set bonus meets the game.
 *
 * <h2>Attributes are the exception, and why</h2>
 *
 * Every other kind of effect a set grants is read through the engine, with the
 * wearer in scope, at the moment it matters. Attributes cannot be: the event
 * that collects a stack's modifiers is handed the stack and nothing else, so a
 * bonus that depends on what else is worn has nowhere to come from.
 *
 * <p>So the set's attributes are put on the wearer rather than on the piece —
 * re-asserted with fixed ids, the same way the world tier's augments are, so
 * taking a piece off takes the bonus with it and putting it back does not
 * stack a second copy.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumGearSetHandler {

    private ElysiumGearSetHandler() {
    }

    /** How often the set bonuses are re-checked, in ticks. */
    public static final int INTERVAL = 20;

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || player.tickCount % INTERVAL != 0) {
            return;
        }
        apply(player);
    }

    /** The attribute half of whatever sets this player is wearing. */
    public static void apply(LivingEntity wearer) {
        Set<ResourceLocation> wanted = new LinkedHashSet<>();
        for (ElysiumGearSet set : ElysiumGearSets.get().all().values()) {
            int worn = ElysiumGearSets.get().wornCount(wearer, set);
            if (worn <= 0) {
                continue;
            }
            for (JsonObject effect : set.effects(worn, "attribute")) {
                if (!effect.has("modifiers")) {
                    continue;
                }
                for (JsonElement element : effect.getAsJsonArray("modifiers")) {
                    ResourceLocation id = assertOne(wearer, set, element.getAsJsonObject());
                    if (id != null) {
                        wanted.add(id);
                    }
                }
            }
        }
        // Anything this mod put on and no longer wants comes back off: a set
        // bonus that survived taking the armour off would be a bonus for
        // having once owned it.
        strip(wearer, wanted);
    }

    private static ResourceLocation assertOne(LivingEntity wearer, ElysiumGearSet set,
                                              JsonObject spec) {
        Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE
                .getHolder(ResourceKey.create(Registries.ATTRIBUTE,
                        ResourceLocation.parse(spec.get("attribute").getAsString())))
                .orElse(null);
        if (attribute == null) {
            return null;                    // an attribute from a mod that is not here
        }
        AttributeInstance instance = wearer.getAttribute(attribute);
        if (instance == null) {
            return null;
        }
        double amount = spec.has("value") ? spec.get("value").getAsDouble() : 0.0D;
        if (amount == 0.0D) {
            return null;
        }
        AttributeModifier.Operation operation = AttributeModifier.Operation.valueOf(
                spec.get("operation").getAsString().toUpperCase(Locale.ROOT));
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID,
                "gear_set/" + set.id().replace(':', '/') + "/"
                        + spec.get("attribute").getAsString().replace(':', '/'));
        AttributeModifier present = instance.getModifier(id);
        if (present != null && present.amount() == amount) {
            return id;                      // already exactly right
        }
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        return id;
    }

    /** Takes off every set modifier this mod applied that is no longer earned. */
    private static void strip(LivingEntity wearer, Set<ResourceLocation> keep) {
        for (Holder<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.asHolderIdMap()) {
            AttributeInstance instance = wearer.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            List<ResourceLocation> stale = new ArrayList<>();
            for (AttributeModifier modifier : instance.getModifiers()) {
                ResourceLocation id = modifier.id();
                if (id.getNamespace().equals(ElysiumLib.MODID)
                        && id.getPath().startsWith("gear_set/") && !keep.contains(id)) {
                    stale.add(id);
                }
            }
            stale.forEach(instance::removeModifier);
        }
    }

    /** A line on every piece saying which set it belongs to and how far it goes. */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ElysiumGearSet set = ElysiumGearSets.get().setOf(stack);
        if (set == null) {
            return;
        }
        event.getToolTip().add(Component.translatable("elysium.gear_set.line",
                Component.translatable(set.nameKey()),
                Component.literal(String.valueOf(set.items().size()))));
    }
}
