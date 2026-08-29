package com.elysium.lib.item;

import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.registry.ElysiumRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;

/**
 * What a piece of gear is made of.
 *
 * <h2>Why this is a registry and not an enum</h2>
 *
 * It was an enum of three: Voidglass, Aetherium, Neutronium. That is fine for a
 * mod that ships three materials and hopeless for anything else — vanilla's own
 * iron and diamond could not be expressed, and a mod adding tin had no way to
 * say so. A material is now a registered description that anything can add.
 *
 * <h2>The ingredient tag, and why availability is a runtime question</h2>
 *
 * A material names its ingredient as a <b>tag</b> — {@code c:ingots/tin} — and
 * never as an item. That is not a stylistic preference; it is the only thing
 * that works.
 *
 * Item registration happens during mod loading, before any mod can read another
 * mod's entries, so nothing can decide at registration time whether tin exists.
 * And a registry that changed shape depending on which mods were installed
 * would be worse than useless: ids would shift, saved stacks would fail to
 * resolve, and a player who removed one mod would lose gear belonging to
 * another.
 *
 * So the gear for a material is <b>always registered</b>, and
 * {@link #isAvailable()} answers the separate question of whether anything in
 * the world can actually make it. Tags load with datapacks, long after
 * registration, so by the time anyone asks the answer is real. Recipes written
 * against an empty tag simply never resolve, which is vanilla behaviour and
 * needs no special handling; the creative tab asks this method and hides what
 * cannot be built.
 *
 * <h2>Registering one</h2>
 *
 * <pre>{@code
 * public static final ElysiumGearMaterial TIN = ElysiumGearMaterial.builder(id("tin"))
 *         .ingredient(ResourceLocation.parse("c:ingots/tin"))
 *         .element(MyElements.TIDE)
 *         .tier(1)
 *         .toolTier(Tiers.IRON)
 *         .damageBonus(0.5F)
 *         .armour(new ElysiumGearMaterial.ArmourProfile(2, 5, 6, 2, 12, 1.0F, 0.0F, 18))
 *         .register();
 * }</pre>
 *
 * The <b>Elysium tier</b> is the important number: it sets rarity, the size of
 * the elemental advantage, and the character level the gear requires. The
 * <b>vanilla tier</b> only sets mining level, speed and base durability.
 */
public final class ElysiumGearMaterial {

    /** Every material in the game. Add-ons register here during construction. */
    public static final ElysiumRegistry<ElysiumGearMaterial> REGISTRY =
            new ElysiumRegistry<>("gear material");

    /**
     * The armour numbers for a material, in the shape 1.21.1's
     * {@code ArmorMaterial} record wants them.
     *
     * Kept as plain data rather than a built {@code ArmorMaterial} because
     * armour materials are a vanilla registry, and registering into a vanilla
     * registry means a namespace — which belongs to the mod adding the
     * material, not to the library describing it.
     *
     * @param durabilityMultiplier armour durability is no longer part of the
     *                             vanilla material in 1.21.1; it is set on the
     *                             item properties, so it is carried here
     */
    public record ArmourProfile(int boots, int leggings, int chestplate, int helmet,
                                int enchantmentValue, float toughness,
                                float knockbackResistance, int durabilityMultiplier) {
    }

    private final ResourceLocation ingredientTagId;
    private final TagKey<Item> ingredientTag;
    private final ElysiumElement element;
    private final int tier;
    private final Tier toolTier;
    private final float damageBonus;
    private final ArmourProfile armour;
    private final boolean vanilla;

    private ElysiumGearMaterial(ResourceLocation ingredientTagId, ElysiumElement element,
                                int tier, Tier toolTier, float damageBonus,
                                ArmourProfile armour, boolean vanilla) {
        this.ingredientTagId = ingredientTagId;
        this.ingredientTag = TagKey.create(BuiltInRegistries.ITEM.key(), ingredientTagId);
        this.element = element;
        this.tier = tier;
        this.toolTier = toolTier;
        this.damageBonus = damageBonus;
        this.armour = armour;
        this.vanilla = vanilla;
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private ResourceLocation ingredientTagId;
        private ElysiumElement element = ElysiumElement.NONE;
        private int tier;
        private Tier toolTier;
        private float damageBonus;
        private ArmourProfile armour;
        private boolean vanilla;

        private Builder(ResourceLocation id) {
            this.id = id;
            // A sensible default that is right far more often than not:
            // c:ingots/<name>. Override for gems and anything unusual.
            this.ingredientTagId = ResourceLocation.fromNamespaceAndPath(
                    "c", "ingots/" + id.getPath());
        }

        /** The tag a recipe draws this material's ingredient from. */
        public Builder ingredient(ResourceLocation tagId) {
            this.ingredientTagId = tagId;
            return this;
        }

        public Builder element(ElysiumElement element) {
            this.element = element == null ? ElysiumElement.NONE : element;
            return this;
        }

        /** Rarity, elemental advantage size and the required character level. */
        public Builder tier(int tier) {
            this.tier = tier;
            return this;
        }

        /** Mining level, speed and base durability. */
        public Builder toolTier(Tier toolTier) {
            this.toolTier = toolTier;
            return this;
        }

        /** Added on top of each tool shape's own attack damage. */
        public Builder damageBonus(float bonus) {
            this.damageBonus = bonus;
            return this;
        }

        public Builder armour(ArmourProfile armour) {
            this.armour = armour;
            return this;
        }

        /**
         * Marks a material whose ingredient ships with the game.
         *
         * The only thing this changes is that {@link #isAvailable()} does not
         * bother consulting the tag — iron is always available, and asking
         * during a window when tags are not loaded would wrongly say it is not.
         */
        public Builder vanilla() {
            this.vanilla = true;
            return this;
        }

        public ElysiumGearMaterial register() {
            if (toolTier == null) {
                throw new IllegalArgumentException(
                        "Material '" + id + "' has no tool tier. Every material needs one: it is "
                        + "what decides what the tools can mine, and there is no sane default.");
            }
            if (tier < 0) {
                throw new IllegalArgumentException(
                        "Material '" + id + "' has a negative Elysium tier. Tier drives rarity, "
                        + "advantage and the required character level, none of which mean "
                        + "anything below zero.");
            }
            return REGISTRY.register(id, new ElysiumGearMaterial(
                    ingredientTagId, element, tier, toolTier, damageBonus, armour, vanilla));
        }
    }

    // ------------------------------------------------------------------

    public ElysiumElement getElement() {
        return element;
    }

    /** The Elysium gear tier: rarity, elemental advantage, required level. */
    public int getTier() {
        return tier;
    }

    /** The vanilla tier: mining level, speed and durability. */
    public Tier getToolTier() {
        return toolTier;
    }

    public float getDamageBonus() {
        return damageBonus;
    }

    /** @return the armour numbers, or null for a material with no armour */
    public ArmourProfile getArmour() {
        return armour;
    }

    public boolean hasArmour() {
        return armour != null;
    }

    public TagKey<Item> getIngredientTag() {
        return ingredientTag;
    }

    public ResourceLocation getIngredientTagId() {
        return ingredientTagId;
    }

    /**
     * Whether anything installed can actually supply this material.
     *
     * True for vanilla materials without asking, and for everything else
     * exactly when the ingredient tag has at least one entry — which is the
     * definition of "some mod provides this".
     *
     * <b>Do not call this during registration.</b> Tags are not loaded then and
     * the answer would be a confident no for everything. It is meant for the
     * creative tab, tooltips, and the reload-time report.
     */
    public boolean isAvailable() {
        if (vanilla) {
            return true;
        }
        return BuiltInRegistries.ITEM.getTag(ingredientTag)
                .map(entries -> entries.size() > 0)
                .orElse(false);
    }

    /** True for a material whose ingredient ships with the game. */
    public boolean isVanilla() {
        return vanilla;
    }

    /** True for one of the elemental materials, as against a plain metal. */
    public boolean isElemental() {
        return element.isElemental();
    }

    public ResourceLocation getId() {
        return REGISTRY.idOf(this);
    }

    public String getSerialisedName() {
        ResourceLocation id = getId();
        return id == null ? "" : id.toString();
    }

    /** The name used to build item registry names: {@code <material>_hammer}. */
    public String getPath() {
        ResourceLocation id = getId();
        return id == null ? "" : id.getPath();
    }

    /** Every registered material, in registration order. */
    public static Iterable<ElysiumGearMaterial> all() {
        return REGISTRY.all();
    }

    @Override
    public String toString() {
        return getSerialisedName();
    }
}
