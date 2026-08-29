package com.elysium.mobs.variant;

import com.elysium.lib.registry.ElysiumRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * One of the five sub-variants a family comes in.
 *
 * <h2>Why a variant is data on an entity rather than its own entity type</h2>
 *
 * Thirty registered entity types would mean thirty models, thirty renderers,
 * thirty spawn eggs and thirty of everything else — for creatures that differ
 * in a texture, three numbers and one ability. Vanilla makes the same choice
 * for cats, villagers and axolotls, and for the same reason.
 *
 * So there are six entity types, and the variant is synced entity data. It
 * decides:
 *
 * <ul>
 *   <li>the <b>texture</b>, which is what a player actually reads;</li>
 *   <li>three <b>multipliers</b> applied on top of the family's base stats and
 *       the level scaling;</li>
 *   <li>one <b>ability</b>, which is the part that makes it a different fight
 *       rather than a different colour.</li>
 * </ul>
 *
 * <h2>The multipliers are a budget, not a bonus</h2>
 *
 * Every variant's three multipliers should come to roughly 3.0 in total. A
 * variant that is tougher is slower; one that hits harder has less health. That
 * is what keeps five sub-variants a choice of how to fight rather than a
 * ranking of which one is dangerous — and {@code validate.py} checks it, because
 * it is exactly the invariant that decays quietly as variants are tuned one at
 * a time.
 */
public final class MobVariant {

    /** Every sub-variant in the game. Add-ons register here during construction. */
    public static final ElysiumRegistry<MobVariant> REGISTRY = new ElysiumRegistry<>("mob variant");

    private final ResourceLocation family;
    private final float healthScale;
    private final float damageScale;
    private final float speedScale;
    private final ChatFormatting colour;
    private final MobAbility ability;
    private final ResourceLocation texture;

    private MobVariant(ResourceLocation id, ResourceLocation family,
                       float healthScale, float damageScale, float speedScale,
                       ChatFormatting colour, MobAbility ability) {
        this.family = family;
        this.healthScale = healthScale;
        this.damageScale = damageScale;
        this.speedScale = speedScale;
        this.colour = colour;
        this.ability = ability;
        this.texture = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                "textures/entity/" + family.getPath() + "/" + id.getPath() + ".png");
    }

    public static Builder builder(ResourceLocation id, ResourceLocation family) {
        return new Builder(id, family);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private final ResourceLocation family;
        private float healthScale = 1.0F;
        private float damageScale = 1.0F;
        private float speedScale = 1.0F;
        private ChatFormatting colour = ChatFormatting.GRAY;
        private MobAbility ability = new MobAbility() {
        };

        private Builder(ResourceLocation id, ResourceLocation family) {
            this.id = id;
            this.family = family;
        }

        /** Health, damage and speed, as multipliers on the family's base. */
        public Builder stats(float health, float damage, float speed) {
            this.healthScale = health;
            this.damageScale = damage;
            this.speedScale = speed;
            return this;
        }

        public Builder colour(ChatFormatting colour) {
            this.colour = colour;
            return this;
        }

        public Builder ability(MobAbility ability) {
            this.ability = ability;
            return this;
        }

        public MobVariant register() {
            for (float scale : new float[]{healthScale, damageScale, speedScale}) {
                if (scale <= 0.0F) {
                    throw new IllegalArgumentException(
                            "Variant '" + id + "' has a multiplier of " + scale + ". Zero health "
                            + "is a creature that dies on spawn and zero speed is one that never "
                            + "reaches anybody; neither is a variant.");
                }
            }
            return REGISTRY.register(id, new MobVariant(
                    id, family, healthScale, damageScale, speedScale, colour, ability));
        }
    }

    // ------------------------------------------------------------------

    /** Which family this is a sub-variant of. */
    public ResourceLocation getFamily() {
        return family;
    }

    public float getHealthScale() {
        return healthScale;
    }

    public float getDamageScale() {
        return damageScale;
    }

    public float getSpeedScale() {
        return speedScale;
    }

    public MobAbility getAbility() {
        return ability;
    }

    /** The skin this variant wears, derived from its id so it cannot mismatch. */
    public ResourceLocation getTexture() {
        return texture;
    }

    public ResourceLocation getId() {
        return REGISTRY.idOf(this);
    }

    public Component getDisplayName() {
        ResourceLocation id = getId();
        return Component.translatable("elysiummobs.variant."
                + (id == null ? "unknown" : id.getPath())).withStyle(colour);
    }

    /** The id as a string, which is how a variant is stored on an entity. */
    public String getSerialisedName() {
        ResourceLocation id = getId();
        return id == null ? "" : id.toString();
    }

    // ------------------------------------------------------------------

    /** Every sub-variant of a family, in registration order. */
    public static List<MobVariant> forFamily(ResourceLocation family) {
        List<MobVariant> found = new ArrayList<>();
        for (MobVariant variant : REGISTRY.all()) {
            if (variant.family.equals(family)) {
                found.add(variant);
            }
        }
        return found;
    }

    /**
     * One sub-variant of a family, at random.
     *
     * @return null when a family has no variants registered, which the mob
     *         treats as "stay unvariant" — a plain creature with its family's
     *         base stats, rather than a crash on spawn
     */
    public static MobVariant random(ResourceLocation family, RandomSource random) {
        List<MobVariant> options = forFamily(family);
        return options.isEmpty() ? null : options.get(random.nextInt(options.size()));
    }

    public static MobVariant byName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(name);
        return id == null ? null : REGISTRY.get(id);
    }

    @Override
    public String toString() {
        return getSerialisedName();
    }
}
