package com.elysium.lib.hazard;

import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.registry.ElysiumRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A way a world can be hostile, and the element that answers it.
 *
 * <h2>Why a hazard is an element</h2>
 *
 * Each of the five is bound to one of the five psionic elements — Thermal to
 * Plasma, Cryogenic to Kinetic, Corrosive to Neural, Radiological to Void,
 * Pressure to Dimensional. That pairing is what makes a hazard a decision
 * rather than a tax: a player already owns gear with an element on it, already
 * knows the ring, and the answer to a burning world turns out to be something
 * they have been sorting their armour by since their first blade.
 *
 * Without the pairing, hazards would need their own resistance stat, their own
 * gear line and their own tooltips — a second progression bolted beside the one
 * the mod already has.
 *
 * <h2>Two counters, always</h2>
 *
 * Every hazard names something free a player can do about it today and
 * something built they can do about it later. The specification asks for both
 * columns and it is right to: a hazard with only an engineered answer is a wall
 * in front of anybody who has not built the answer yet, and one with only a
 * cheap answer is never a reason to build anything.
 *
 * These are not flavour text. They are the tooltip a player reads when the
 * damage starts, which is the moment they need to be told.
 */
public final class ElysiumHazard {

    public static final ElysiumRegistry<ElysiumHazard> REGISTRY =
            new ElysiumRegistry<>("hazard");

    private final String translationKey;
    private final ElysiumElement element;
    private final float damagePerSecond;

    private ElysiumHazard(String translationKey, ElysiumElement element,
                          float damagePerSecond) {
        this.translationKey = translationKey;
        this.element = element;
        this.damagePerSecond = damagePerSecond;
    }

    /**
     * @param element         the element whose gear resists this
     * @param damagePerSecond at full intensity, with no protection at all
     */
    public static ElysiumHazard register(ResourceLocation id, ElysiumElement element,
                                         float damagePerSecond) {
        String bare = id.getNamespace().equals("elysium")
                || id.getNamespace().equals("elysiumlib")
                ? id.getPath()
                : id.getNamespace() + "." + id.getPath();
        return REGISTRY.register(id,
                new ElysiumHazard("elysium.hazard." + bare, element, damagePerSecond));
    }

    public ElysiumElement getElement() {
        return element;
    }

    public float getDamagePerSecond() {
        return damagePerSecond;
    }

    public ResourceLocation getId() {
        return REGISTRY.idOf(this);
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey)
                .withStyle(element.getColour());
    }

    /** "Travel at night, or eat something that holds the heat off." */
    public Component getCheapCounter() {
        return Component.translatable(translationKey + ".cheap");
    }

    /** "Insulate a sealed room, or run a thermal collector." */
    public Component getEngineeredCounter() {
        return Component.translatable(translationKey + ".engineered");
    }

    @Override
    public String toString() {
        return "ElysiumHazard[" + translationKey + "]";
    }
}
