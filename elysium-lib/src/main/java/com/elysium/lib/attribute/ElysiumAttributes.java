package com.elysium.lib.attribute;

import com.elysium.lib.ElysiumLib;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers every row of {@link ElysiumAttributeSpec} as a synced attribute
 * under {@code elysiumlib:<name>} and adds them to players.
 *
 * <p>Registering an attribute does nothing by itself. Each attribute's effect
 * is its own P1 feature (crit, dodge, lifesteal, pierce and so on), and until
 * that lands the attribute is only a number gear can carry. The attributes are
 * in the library so that both the armory (gear properties) and the ascendancy
 * (affixes and gems) can refer to the same holder without depending on each
 * other.
 */
public final class ElysiumAttributes {

    private ElysiumAttributes() {
    }

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(BuiltInRegistries.ATTRIBUTE, ElysiumLib.MODID);

    private static final Map<String, DeferredHolder<Attribute, Attribute>> BY_NAME =
            new LinkedHashMap<>();

    static {
        for (ElysiumAttributeSpec spec : ElysiumAttributeSpec.ALL) {
            BY_NAME.put(spec.name(), ATTRIBUTES.register(spec.name(), () -> create(spec)));
        }
    }

    private static Attribute create(ElysiumAttributeSpec spec) {
        String key = "attribute." + ElysiumLib.MODID + "." + spec.name();
        Attribute attribute = spec.kind() == ElysiumAttributeSpec.Kind.CHANCE
                ? new ElysiumChanceAttribute(key, spec.storedDefault(), spec.storedMin(),
                        ElysiumAttributeSpec.MAX)
                : new RangedAttribute(key, spec.storedDefault(), spec.storedMin(),
                        ElysiumAttributeSpec.MAX);
        attribute.setSyncable(true);
        return attribute;
    }

    /** The holder for a spec name. Throws for a name that is not in the table. */
    public static Holder<Attribute> get(String name) {
        DeferredHolder<Attribute, Attribute> holder = BY_NAME.get(name);
        if (holder == null) {
            throw new IllegalArgumentException("no Elysium attribute " + name);
        }
        return holder;
    }

    public static Map<String, DeferredHolder<Attribute, Attribute>> all() {
        return java.util.Collections.unmodifiableMap(BY_NAME);
    }

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        modBus.addListener(ElysiumAttributes::addToPlayers);
    }

    private static void addToPlayers(EntityAttributeModificationEvent event) {
        for (DeferredHolder<Attribute, Attribute> holder : BY_NAME.values()) {
            if (!event.has(EntityType.PLAYER, holder)) {
                event.add(EntityType.PLAYER, holder);
            }
        }
    }
}
