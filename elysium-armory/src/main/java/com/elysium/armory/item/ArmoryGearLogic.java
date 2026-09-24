package com.elysium.armory.item;

import com.elysium.armory.material.ArmoryGearBuild;
import com.elysium.armory.material.ArmoryMaterial;
import com.elysium.armory.material.ArmoryMaterials;
import com.elysium.armory.material.ArmoryPart;
import com.elysium.armory.material.ArmoryParts;
import com.elysium.armory.property.ArmoryProperty;
import com.elysium.armory.property.ArmoryPropertyKey;
import com.elysium.armory.property.ArmoryTier;
import com.elysium.armory.property.ArmoryVanilla;
import com.elysium.armory.registry.ArmoryComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * How a piece of modular gear works out what it is. What it is made of
 * lives on the stack ({@link ArmoryGearParts}); what that makes it is computed
 * from the loaded materials and parts and cached ({@link ArmoryGearStats}).
 *
 * <p>Nothing is overridden to make the game read those numbers. The computed
 * properties are written into vanilla's own components — max damage, attribute
 * modifiers, the tool rules — so every part of the game that already knows how
 * to read a sword reads this one, including other mods.
 *
 * <p>Elysium's fixed-shape gear stays exactly as it is: this is the modular
 * path alongside it, not a replacement for it.
 */
public final class ArmoryGearLogic {

    private ArmoryGearLogic() {
    }

    /** The slot a piece of this type is worn in, or null for anything that is not armour. */
    public static String armourSlot(String gearType) {
        return switch (gearType) {
            case "helmet", "chestplate", "leggings", "boots", "elytra" -> gearType;
            default -> null;
        };
    }

    /**
     * The accessory slot a piece of this type is worn in, or null.
     *
     * Kept apart from {@link #armourSlot} because the two answer different
     * questions: a helmet goes in a slot the game itself has, and a ring goes
     * in one some other mod holds. Everything downstream of armourSlot —
     * durability multiples, armour attribute groups, the worn texture — is
     * about vanilla armour and would be wrong for jewellery.
     */
    public static String accessorySlot(String gearType) {
        return switch (gearType) {
            case "ring", "bracelet", "necklace" -> gearType;
            default -> null;
        };
    }

    public static ArmoryGearParts partsOf(ItemStack stack) {
        return stack.getOrDefault(ArmoryComponents.PARTS.get(), ArmoryGearParts.EMPTY);
    }

    /** What has been done to this piece beyond making it. */
    public static ArmoryMarks marksOf(ItemStack stack) {
        return stack.getOrDefault(ArmoryComponents.MARKS.get(), ArmoryMarks.PLAIN);
    }

    public static ArmoryGearStats statsOf(ItemStack stack) {
        return stack.getOrDefault(ArmoryComponents.STATS.get(), ArmoryGearStats.EMPTY);
    }

    /** The build this stack describes, or null if the data it names is gone. */
    public static ArmoryGearBuild build(String gearType, ItemStack stack) {
        ArmoryGearParts parts = partsOf(stack);
        if (parts.isEmpty()) {
            return null;
        }
        List<ArmoryGearBuild.Piece> pieces = new ArrayList<>();
        for (ArmoryGearParts.Entry entry : parts.entries()) {
            ArmoryPart part = ArmoryParts.get().byId(entry.part());
            if (part == null) {
                continue;               // its definition is gone: the rest of the piece still works
            }
            List<ArmoryMaterial> materials = new ArrayList<>();
            for (String id : entry.materials()) {
                ArmoryMaterial material = ArmoryMaterials.get().byId(id);
                if (material != null) {
                    materials.add(material);
                }
            }
            pieces.add(new ArmoryGearBuild.Piece(part, materials));
        }
        return pieces.isEmpty() ? null : new ArmoryGearBuild(gearType, pieces);
    }

    /**
     * Computes this stack's properties and writes them into the components the
     * game reads. Called when a piece is crafted, when its parts change, and
     * when it wears past a step ({@link ArmoryVanilla#damageFactor}).
     */
    public static void recompute(ArmoryGear gear, ItemStack stack) {
        String gearType = gear.gearType();
        ArmoryGearBuild build = build(gearType, stack);
        if (build == null) {
            return;
        }
        java.util.Map<String, Double> values = new java.util.LinkedHashMap<>();
        for (ArmoryProperty property : ArmoryProperty.ALL) {
            values.put(property.id(), build.property(property));
        }
        // What has been done to the parts beyond making them: grade, charge and
        // the synergy the materials earned. One multiplier on everything the
        // piece is worth, applied before the traits have their say, so a trait
        // that reads a number reads the number the player actually has.
        ArmoryMarks marks = marksOf(stack);
        if (!marks.isPlain()) {
            double factor = marks.factor();
            for (java.util.Map.Entry<String, Double> entry : values.entrySet()) {
                entry.setValue(entry.getValue() * factor);
            }
        }
        String slot = armourSlot(gearType);
        if (slot != null) {
            values.put("armor_piece", ArmoryPropertyKey.pieceArmour(
                    pieceValues(gearType, build), com.elysium.armory.property.ArmoryGearType.named(slot)));
        }
        // The traits the parts grant, and everything they change about the
        // numbers before those numbers are written anywhere.
        java.util.Map<String, Integer> traits = com.elysium.armory.material.ArmoryTraits.get().resolve(build);
        stack.set(ArmoryComponents.TRAITS.get(), new ArmoryGearTraits(traits));
        double ratio = stack.getMaxDamage() <= 0 ? 0.0D
                : (double) stack.getDamageValue() / Math.max(1, stack.getMaxDamage());
        com.elysium.armory.trait.ArmoryTraitProperties.apply(stack, values, ratio);
        ArmoryTier tier = build.tier();
        int max = Math.max(1, maxDamage(values, slot));
        // Broken gear is not gear: it hits for nothing, mines at a crawl and
        // gives no armour, until it is repaired.
        boolean broken = stack.getDamageValue() >= max - 1;
        int step = ArmoryVanilla.damageFactor(stack.getDamageValue(), max);
        stack.set(ArmoryComponents.STATS.get(),
                new ArmoryGearStats(values, tier == null ? "" : tier.name(), step));
        stack.set(DataComponents.MAX_DAMAGE, max);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        ItemAttributeModifiers modifiers = attributes(values, slot, broken,
                accessorySlot(gearType));
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS,
                com.elysium.armory.trait.ArmoryTraitProperties.attributes(stack, modifiers));
        com.elysium.armory.trait.ArmoryTraitProperties.components(stack);
        if (slot == null) {
            stack.set(DataComponents.TOOL, tool(values, tier, broken));
        }
    }

    private static java.util.Map<ArmoryPropertyKey, Double> pieceValues(String gearType, ArmoryGearBuild build) {
        java.util.Map<ArmoryPropertyKey, Double> out = new java.util.LinkedHashMap<>();
        out.put(new ArmoryPropertyKey(ArmoryProperty.ARMOR.id(), null), build.property(ArmoryProperty.ARMOR));
        out.put(new ArmoryPropertyKey(ArmoryProperty.ARMOR.id(), gearType), build.property(ArmoryProperty.ARMOR));
        return out;
    }

    /** Durability: a tool's own, or an armour piece's per-slot multiple. */
    public static int maxDamage(java.util.Map<String, Double> values, String slot) {
        if (slot != null) {
            return ArmoryVanilla.armourDurability(values.getOrDefault("armor_durability", 0.0D), slot);
        }
        return (int) Math.max(1.0D, values.getOrDefault("durability", 1.0D));
    }

    private static ItemAttributeModifiers attributes(java.util.Map<String, Double> values, String slot,
                                                     boolean broken) {
        return attributes(values, slot, broken, null);
    }

    /**
     * What a built piece grants, as vanilla attribute modifiers.
     *
     * <h3>Jewellery</h3>
     *
     * A ring is worn in a slot vanilla does not have, so its modifiers are
     * written for {@link EquipmentSlotGroup#ANY} — the group that means
     * "wherever this is" — and the accessory bridge in elysium-trinkets
     * publishes exactly those when the piece is equipped. Nothing here knows
     * that Curios exists, and nothing in that bridge knows what a ring is made
     * of.
     *
     * <p>What it grants is the defensive three and nothing else: a ring is a
     * small, permanent advantage, not a second weapon. Everything else
     * jewellery does is its material's traits, which are effects rather than
     * numbers and reach the wearer through the library's own engine.
     */
    private static ItemAttributeModifiers attributes(java.util.Map<String, Double> values, String slot,
                                                     boolean broken, String accessory) {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        if (accessory != null) {
            if (broken) {
                return builder.build();
            }
            jewel(builder, Attributes.ARMOR, "armor",
                    values.getOrDefault("magic_armor", 0.0D)
                            + values.getOrDefault("armor", 0.0D));
            jewel(builder, Attributes.ARMOR_TOUGHNESS, "armor_toughness",
                    ArmoryVanilla.toughnessAttribute(
                            values.getOrDefault("armor_toughness", 0.0D)));
            jewel(builder, Attributes.KNOCKBACK_RESISTANCE, "knockback_resistance",
                    ArmoryVanilla.knockbackResistanceAttribute(
                            values.getOrDefault("knockback_resistance", 0.0D)));
            return builder.build();
        }
        if (slot == null) {
            double damage = ArmoryVanilla.attackDamage(values.getOrDefault("attack_damage", 0.0D), broken);
            double speed = ArmoryVanilla.attackSpeed(values.getOrDefault("attack_speed", 4.0D), broken);
            builder.add(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(id("attack_damage"), damage, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
            builder.add(Attributes.ATTACK_SPEED,
                    new AttributeModifier(id("attack_speed"), speed, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
            reach(builder, Attributes.BLOCK_INTERACTION_RANGE, "block_reach", values);
            reach(builder, Attributes.ENTITY_INTERACTION_RANGE, "attack_reach", values);
            // Ranged gear speaks through the library's own attributes, so that
            // P1's bow code reads a modular bow exactly as it reads any other,
            // and an enchantment on top of it still stacks.
            double draw = values.getOrDefault("draw_speed", 0.0D);
            if (draw != 0.0D) {
                builder.add(com.elysium.lib.attribute.ElysiumAttributes.get("draw_speed"),
                        new AttributeModifier(id("draw_speed"), draw, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
            }
            double ranged = values.getOrDefault("ranged_damage", 0.0D);
            if (ranged > 1.0D) {
                builder.add(com.elysium.lib.attribute.ElysiumAttributes.get("arrow_damage"),
                        new AttributeModifier(id("arrow_damage"), ranged - 1.0D,
                                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
            }
            return builder.build();
        }
        EquipmentSlotGroup group = switch (slot) {
            case "helmet" -> EquipmentSlotGroup.HEAD;
            case "chestplate", "elytra" -> EquipmentSlotGroup.CHEST;
            case "leggings" -> EquipmentSlotGroup.LEGS;
            default -> EquipmentSlotGroup.FEET;
        };
        builder.add(Attributes.ARMOR, new AttributeModifier(id("armor"),
                broken ? 0.0D : values.getOrDefault("armor_piece", values.getOrDefault("armor", 0.0D)),
                AttributeModifier.Operation.ADD_VALUE), group);
        double toughness = ArmoryVanilla.toughnessAttribute(values.getOrDefault("armor_toughness", 0.0D));
        if (toughness > 0.0D) {
            builder.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id("armor_toughness"), toughness,
                    AttributeModifier.Operation.ADD_VALUE), group);
        }
        double knockback = ArmoryVanilla.knockbackResistanceAttribute(
                values.getOrDefault("knockback_resistance", 0.0D));
        if (knockback > 0.0D) {
            builder.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id("knockback_resistance"),
                    knockback, AttributeModifier.Operation.ADD_VALUE), group);
        }
        return builder.build();
    }

    /** One of jewellery's modifiers, written for wherever the piece ends up. */
    private static void jewel(ItemAttributeModifiers.Builder builder,
                              net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                              String property, double value) {
        if (value > 0.0D) {
            builder.add(attribute, new AttributeModifier(id(property), value,
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY);
        }
    }

    private static void reach(ItemAttributeModifiers.Builder builder,
                              net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                              String property, java.util.Map<String, Double> values) {
        double value = values.getOrDefault(property, 0.0D);
        if (value != 0.0D) {
            builder.add(attribute, new AttributeModifier(id(property), value,
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
    }

    /** What the tool mines and how fast: vanilla's own Tool component, built from the properties. */
    private static Tool tool(java.util.Map<String, Double> values, ArmoryTier tier, boolean broken) {
        float speed = (float) ArmoryVanilla.harvestSpeed(
                Math.max(1.0D, values.getOrDefault("harvest_speed", 1.0D)), true, broken);
        List<Tool.Rule> rules = new ArrayList<>();
        if (tier != null) {
            rules.add(Tool.Rule.deniesDrops(incorrectFor(tier)));
        }
        rules.add(Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_PICKAXE, speed));
        rules.add(Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_AXE, speed));
        rules.add(Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_SHOVEL, speed));
        rules.add(Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_HOE, speed));
        return new Tool(rules, 1.0F, 1);
    }

    /**
     * Whether a piece has reached the point where it stops working.
     *
     * One short of its maximum, which is where {@link #damage} leaves
     * everything without the red card trait: broken, useless, and still the
     * player's own materials.
     */
    public static boolean isBroken(ItemStack stack) {
        int max = stack.getMaxDamage();
        return max > 0 && stack.getDamageValue() >= max - 1;
    }

    /**
     * How well a piece takes an enchantment (Silent Gear's SG-73).
     *
     * From its parts, like everything else it is worth: a gold-handled tool
     * enchants like gold because gold is what it is made of. Zero for a broken
     * piece — an enchanting table that would take a player's levels for
     * something that does not work is a table that has robbed them.
     */
    public static int enchantmentValue(ItemStack stack) {
        if (isBroken(stack)) {
            return 0;
        }
        return (int) Math.max(0.0D, Math.round(statsOf(stack).get(
                com.elysium.armory.property.ArmoryProperty.ENCHANTMENT_VALUE.id(), 0.0D)));
    }

    /** The vanilla tag of blocks a tier cannot mine. */
    public static TagKey<Block> incorrectFor(ArmoryTier tier) {
        return TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                ResourceLocation.parse(tier.incorrectTag()));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(com.elysium.armory.ElysiumArmory.MODID, "gear/" + path);
    }

    /**
     * A durability event, after the traits have had their say: some gear takes
     * an extra point now and then, some saves one (SG-61's durability effect).
     */
    public static <T extends net.minecraft.world.entity.LivingEntity> int damage(ItemStack stack, int amount,
                                                                                 T entity) {
        if (com.elysium.armory.trait.ArmoryCodedTraits.indestructible(stack)) {
            return 0;                       // indestructible gear wears not at all
        }
        int rolled = com.elysium.lib.effect.ElysiumEffectHandlers.durability(stack, amount, entity.getRandom());
        if (com.elysium.armory.trait.ArmoryCodedTraits.breaksForGood(stack)) {
            return rolled;                  // red card: destroyed like a vanilla tool
        }
        // Everything else stops one point short and stays there: broken, useless
        // until it is repaired, but still the player's own materials.
        int left = stack.getMaxDamage() - 1 - stack.getDamageValue();
        return Math.max(0, Math.min(rolled, left));
    }

    /**
     * What a trait does when the gear is used on a block: place what it makes,
     * or turn a patch of one block into another (SG-61's block_placer and
     * block_filler).
     */
    public static net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        net.minecraft.world.entity.player.Player player = context.getPlayer();
        net.minecraft.world.level.Level level = context.getLevel();
        if (player == null) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        for (com.elysium.lib.effect.ElysiumItemEffects.Active active
                : com.elysium.lib.effect.ElysiumItemEffects.of(stack, "block_placer")) {
            net.minecraft.core.BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
            if (!level.getBlockState(pos).canBeReplaced()) {
                continue;
            }
            if (!level.isClientSide()) {
                level.setBlockAndUpdate(pos,
                        com.elysium.lib.effect.ElysiumEffectHandlers.placedBlock(active).defaultBlockState());
                stack.hurtAndBreak((int) active.number("cost", 1.0D), player,
                        net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                int cooldown = (int) active.number("cooldown", 0.0D);
                if (cooldown > 0) {
                    player.getCooldowns().addCooldown(stack.getItem(), cooldown);
                }
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
        }
        for (com.elysium.lib.effect.ElysiumItemEffects.Active active
                : com.elysium.lib.effect.ElysiumItemEffects.of(stack, "block_filler")) {
            net.minecraft.world.level.block.Block from = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .get(net.minecraft.resources.ResourceLocation.parse(active.text("from", "minecraft:air")));
            net.minecraft.world.level.block.Block to = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .get(net.minecraft.resources.ResourceLocation.parse(active.text("to", "minecraft:air")));
            int range = (int) active.number("range", 1.0D);
            boolean any = false;
            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    net.minecraft.core.BlockPos pos = context.getClickedPos().offset(dx, 0, dz);
                    if (!level.getBlockState(pos).is(from)) {
                        continue;
                    }
                    any = true;
                    if (!level.isClientSide()) {
                        level.setBlockAndUpdate(pos, to.defaultBlockState());
                        stack.hurtAndBreak(Math.max(1, (int) Math.ceil(active.number("cost", 1.0D))), player,
                                net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                    }
                }
            }
            if (any) {
                return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    /**
     * Keeps a piece's numbers in step with its wear, and fills them in for a
     * piece that has none yet. Every gear item calls this from its own tick.
     */
    public static void tick(ArmoryGear gear, ItemStack stack, net.minecraft.world.level.Level level) {
        if (level.isClientSide() || partsOf(stack).isEmpty()) {
            return;
        }
        ArmoryGearStats stats = statsOf(stack);
        int step = ArmoryVanilla.damageFactor(stack.getDamageValue(), Math.max(1, stack.getMaxDamage()));
        if (stats.isEmpty() || stats.step() != step) {
            recompute(gear, stack);
        }
        // Enchantments a trait grants need the datapack registry, which only a
        // level has; the first tick in a player's hands is where they land.
        com.elysium.armory.trait.ArmoryTraitProperties.enchantments(stack, level.registryAccess());
    }
}
