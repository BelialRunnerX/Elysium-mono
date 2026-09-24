package com.elysium.core.item;

import com.elysium.core.Elysium;
import com.elysium.lib.element.ElysiumElement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import com.elysium.lib.item.ElysiumAreaBreak;
import com.elysium.lib.item.ElysiumRarities;
import com.elysium.lib.item.ElysiumGearMaterial;
import com.elysium.lib.item.ElysiumSocketable;

/**
 * The four Elysium area tools, one variant per material.
 *
 * Each one is a real weapon as well as a tool — the attack damage is set in the
 * same range as the elemental blades, traded against a slower swing. A player
 * carrying a Neutronium Hammer is not carrying a pickaxe and a sword.
 *
 * Attack attributes are built by hand rather than through the vanilla
 * {@code createAttributes} helpers. Those helpers replace the player's base
 * damage using vanilla's own modifier ids; doing it additively means the numbers
 * land in the same place while relying only on the attribute API this project
 * has already verified against 1.21.1.
 */
public final class ElysiumTools {

    private ElysiumTools() {
    }

    private static final ResourceLocation DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(Elysium.MODID, "tool_attack_damage");
    private static final ResourceLocation SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(Elysium.MODID, "tool_attack_speed");

    /** Attack damage before the material bonus, and the swing speed penalty. */
    private static final float HAMMER_DAMAGE = 7.0F;
    private static final float HAMMER_SPEED = -3.2F;
    private static final float BROADAXE_DAMAGE = 6.0F;
    private static final float BROADAXE_SPEED = -3.0F;
    private static final float SCYTHE_DAMAGE = 5.0F;
    private static final float SCYTHE_SPEED = -2.6F;
    private static final float SPEAR_DAMAGE = 4.0F;
    private static final float SPEAR_SPEED = -2.2F;

    // The five plain shapes, at vanilla's own numbers.
    //
    // Deliberately not rebalanced. A player who knows what an iron pickaxe
    // swings like should find that an Elysium iron pickaxe swings like an iron
    // pickaxe — the material bonus is added on top exactly as vanilla adds a
    // tier's bonus, so a Neutronium sword is a better sword for the same reason
    // a diamond one is. What Elysium adds to these is element, tier and
    // sockets, not a different feel in the hand.
    private static final float SWORD_DAMAGE = 3.0F;
    private static final float SWORD_SPEED = -2.4F;
    private static final float PICKAXE_DAMAGE = 1.0F;
    private static final float PICKAXE_SPEED = -2.8F;
    private static final float AXE_DAMAGE = 5.0F;
    private static final float AXE_SPEED = -3.1F;
    private static final float SHOVEL_DAMAGE = 1.5F;
    private static final float SHOVEL_SPEED = -3.0F;
    private static final float HOE_DAMAGE = 0.0F;
    private static final float HOE_SPEED = -3.0F;

    private static Item.Properties properties(ElysiumGearMaterial material, float damage, float speed) {
        return new Item.Properties()
                .rarity(ElysiumRarities.getRarityFromTier(material.getTier()))
                .attributes(attributes(damage + material.getDamageBonus(), speed));
    }

    private static ItemAttributeModifiers attributes(float damage, float speed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    private static void tooltip(ElysiumSocketable item, ItemStack stack,
                                List<Component> tooltip, String ability) {
        item.appendSocketTooltip(stack, tooltip);
        tooltip.add(Component.translatable("elysium.tooltip.ability." + ability)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    // ==================================================================

    /** Breaks a 3x3 in the plane of the face you struck. */
    public static class Hammer extends PickaxeItem implements ElysiumSocketable {

        private final ElysiumGearMaterial material;

        public Hammer(ElysiumGearMaterial material) {
            super(material.getToolTier(), properties(material, HAMMER_DAMAGE, HAMMER_SPEED));
            this.material = material;
        }

        @Override
        public ElysiumElement getElement() {
            return material.getElement();
        }

        @Override
        public int getElysiumTier() {
            return material.getTier();
        }

        /** A hammer is most of its own weight; it carries Strength well. */
        @Override
        public float getDamageMultiplier() {
            return 1.6F;
        }

        /**
         * The same figure handed to {@code properties(...)} above - the tool's
         * own damage plus the material's bonus, which together are what the
         * attribute component was built from.
         */
        @Override
        public float getBaseAttackDamage() {
            return HAMMER_DAMAGE + material.getDamageBonus();
        }

        @Override
        public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
            return elysiumModifiers(stack, super.getDefaultAttributeModifiers(stack),
                    EquipmentSlotGroup.MAINHAND);
        }

        @Override
        public boolean mineBlock(ItemStack stack, Level level, BlockState state,
                                 BlockPos pos, LivingEntity miner) {
            if (miner instanceof Player player) {
                ElysiumAreaBreak.area(level, pos, player, stack,
                        target -> target.is(BlockTags.MINEABLE_WITH_PICKAXE));
            }
            return super.mineBlock(stack, level, state, pos, miner);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                    List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            ElysiumTools.tooltip(this, stack, tooltip, "hammer");
        }
    }

    /** Takes the whole tree. */
    public static class Broadaxe extends AxeItem implements ElysiumSocketable {

        private final ElysiumGearMaterial material;

        public Broadaxe(ElysiumGearMaterial material) {
            super(material.getToolTier(), properties(material, BROADAXE_DAMAGE, BROADAXE_SPEED));
            this.material = material;
        }

        @Override
        public ElysiumElement getElement() {
            return material.getElement();
        }

        @Override
        public int getElysiumTier() {
            return material.getTier();
        }

        /** A broadaxe is most of its own weight; it carries Strength well. */
        @Override
        public float getDamageMultiplier() {
            return 1.5F;
        }

        /**
         * The same figure handed to {@code properties(...)} above - the tool's
         * own damage plus the material's bonus, which together are what the
         * attribute component was built from.
         */
        @Override
        public float getBaseAttackDamage() {
            return BROADAXE_DAMAGE + material.getDamageBonus();
        }

        @Override
        public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
            return elysiumModifiers(stack, super.getDefaultAttributeModifiers(stack),
                    EquipmentSlotGroup.MAINHAND);
        }

        @Override
        public boolean mineBlock(ItemStack stack, Level level, BlockState state,
                                 BlockPos pos, LivingEntity miner) {
            if (miner instanceof Player player && state.is(BlockTags.LOGS)) {
                ElysiumAreaBreak.fell(level, pos, player, stack,
                        target -> target.is(BlockTags.LOGS));
            }
            return super.mineBlock(stack, level, state, pos, miner);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                    List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            ElysiumTools.tooltip(this, stack, tooltip, "broadaxe");
        }
    }

    /** Digs a 3x3. */
    public static class Spear extends ShovelItem implements ElysiumSocketable {

        private final ElysiumGearMaterial material;

        public Spear(ElysiumGearMaterial material) {
            super(material.getToolTier(), properties(material, SPEAR_DAMAGE, SPEAR_SPEED));
            this.material = material;
        }

        @Override
        public ElysiumElement getElement() {
            return material.getElement();
        }

        @Override
        public int getElysiumTier() {
            return material.getTier();
        }

        /** A spear is most of its own weight; it carries Strength well. */
        @Override
        public float getDamageMultiplier() {
            return 1.25F;
        }

        /**
         * The same figure handed to {@code properties(...)} above - the tool's
         * own damage plus the material's bonus, which together are what the
         * attribute component was built from.
         */
        @Override
        public float getBaseAttackDamage() {
            return SPEAR_DAMAGE + material.getDamageBonus();
        }

        @Override
        public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
            return elysiumModifiers(stack, super.getDefaultAttributeModifiers(stack),
                    EquipmentSlotGroup.MAINHAND);
        }

        @Override
        public boolean mineBlock(ItemStack stack, Level level, BlockState state,
                                 BlockPos pos, LivingEntity miner) {
            if (miner instanceof Player player) {
                ElysiumAreaBreak.area(level, pos, player, stack,
                        target -> target.is(BlockTags.MINEABLE_WITH_SHOVEL));
            }
            return super.mineBlock(stack, level, state, pos, miner);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                    List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            ElysiumTools.tooltip(this, stack, tooltip, "spear");
        }
    }

    /** Harvests a 3x3 of crops. */
    public static class Scythe extends HoeItem implements ElysiumSocketable {

        private final ElysiumGearMaterial material;

        public Scythe(ElysiumGearMaterial material) {
            super(material.getToolTier(), properties(material, SCYTHE_DAMAGE, SCYTHE_SPEED));
            this.material = material;
        }

        @Override
        public ElysiumElement getElement() {
            return material.getElement();
        }

        @Override
        public int getElysiumTier() {
            return material.getTier();
        }

        /** A scythe is most of its own weight; it carries Strength well. */
        @Override
        public float getDamageMultiplier() {
            return 1.3F;
        }

        /**
         * The same figure handed to {@code properties(...)} above - the tool's
         * own damage plus the material's bonus, which together are what the
         * attribute component was built from.
         */
        @Override
        public float getBaseAttackDamage() {
            return SCYTHE_DAMAGE + material.getDamageBonus();
        }

        @Override
        public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
            return elysiumModifiers(stack, super.getDefaultAttributeModifiers(stack),
                    EquipmentSlotGroup.MAINHAND);
        }

        @Override
        public boolean mineBlock(ItemStack stack, Level level, BlockState state,
                                 BlockPos pos, LivingEntity miner) {
            if (miner instanceof Player player) {
                // Crops are not in the hoe-mineable tag — they break instantly
                // by hand — so the harvest has to name them explicitly.
                ElysiumAreaBreak.area(level, pos, player, stack,
                        target -> target.is(BlockTags.CROPS)
                                || target.is(BlockTags.MINEABLE_WITH_HOE));
            }
            return super.mineBlock(stack, level, state, pos, miner);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                    List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            ElysiumTools.tooltip(this, stack, tooltip, "scythe");
        }
    }

    // ==================================================================
    // The plain shapes
    // ==================================================================
    //
    // Sword, pickaxe, axe, shovel and hoe, one per material, doing exactly
    // what their vanilla counterparts do.
    //
    // <h2>Why these exist beside the area tools</h2>
    //
    // The four tools above are not additions to vanilla's set — they *replace*
    // its roles. A Hammer is a PickaxeItem that breaks a 3x3, a Spear is a
    // ShovelItem, a Scythe is a HoeItem. So before these five, a player who
    // smelted a new metal could make a hammer out of it and could not make a
    // pickaxe, and there was no sword in the entire material set: the only way
    // to hold a plain blade was to leave Elysium's materials behind.
    //
    // That also made the first rung of every material an area tool, which is
    // the wrong shape for progression. Now the plain tool is what a material
    // gives you first and the area tool is what it gives you next — the same
    // metal, more work, a real ability.
    //
    // <h2>The boilerplate, and why it is not less</h2>
    //
    // Each class repeats the same two overrides. They cannot be factored into
    // {@link MaterialTool} because both override methods on the vanilla class
    // rather than on the interface, and each of the five extends a different
    // vanilla class — so Java has nowhere to put the shared version. Everything
    // that *can* be shared is on the interface.

    /**
     * The half of {@link ElysiumSocketable} that a material can answer by
     * itself.
     *
     * Nine tool classes asking the same material the same three questions is
     * nine chances for one of them to answer differently — a copper axe with
     * iron's element, which is invisible until somebody wonders why their rune
     * is not aligning.
     */
    public interface MaterialTool extends ElysiumSocketable {

        ElysiumGearMaterial material();

        /** The shape's own damage, before the material's bonus. */
        float shapeDamage();

        @Override
        default ElysiumElement getElement() {
            return material().getElement();
        }

        @Override
        default int getElysiumTier() {
            return material().getTier();
        }

        @Override
        default float getBaseAttackDamage() {
            return shapeDamage() + material().getDamageBonus();
        }
    }

    /** A blade and nothing else, which is why it carries Strength best. */
    public static class Sword extends SwordItem implements MaterialTool {

        private final ElysiumGearMaterial material;

        public Sword(ElysiumGearMaterial material) {
            super(material.getToolTier(), properties(material, SWORD_DAMAGE, SWORD_SPEED));
            this.material = material;
        }

        @Override
        public ElysiumGearMaterial material() {
            return material;
        }

        @Override
        public float shapeDamage() {
            return SWORD_DAMAGE;
        }

        /**
         * Above every plain tool and above the spear and scythe, below the
         * hammer and broadaxe. A sword digs nothing, so what it trades that for
         * is being the best thing to swing that is not a two-handed tool.
         */
        @Override
        public float getDamageMultiplier() {
            return 1.4F;
        }

        @Override
        public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
            return elysiumModifiers(stack, super.getDefaultAttributeModifiers(stack),
                    EquipmentSlotGroup.MAINHAND);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                    List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            appendSocketTooltip(stack, tooltip);
        }
    }

    public static class Pickaxe extends PickaxeItem implements MaterialTool {

        private final ElysiumGearMaterial material;

        public Pickaxe(ElysiumGearMaterial material) {
            super(material.getToolTier(), properties(material, PICKAXE_DAMAGE, PICKAXE_SPEED));
            this.material = material;
        }

        @Override
        public ElysiumGearMaterial material() {
            return material;
        }

        @Override
        public float shapeDamage() {
            return PICKAXE_DAMAGE;
        }

        @Override
        public float getDamageMultiplier() {
            return 1.0F;
        }

        @Override
        public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
            return elysiumModifiers(stack, super.getDefaultAttributeModifiers(stack),
                    EquipmentSlotGroup.MAINHAND);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                    List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            appendSocketTooltip(stack, tooltip);
        }
    }

    public static class Axe extends AxeItem implements MaterialTool {

        private final ElysiumGearMaterial material;

        public Axe(ElysiumGearMaterial material) {
            super(material.getToolTier(), properties(material, AXE_DAMAGE, AXE_SPEED));
            this.material = material;
        }

        @Override
        public ElysiumGearMaterial material() {
            return material;
        }

        @Override
        public float shapeDamage() {
            return AXE_DAMAGE;
        }

        @Override
        public float getDamageMultiplier() {
            return 1.2F;
        }

        @Override
        public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
            return elysiumModifiers(stack, super.getDefaultAttributeModifiers(stack),
                    EquipmentSlotGroup.MAINHAND);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                    List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            appendSocketTooltip(stack, tooltip);
        }
    }

    public static class Shovel extends ShovelItem implements MaterialTool {

        private final ElysiumGearMaterial material;

        public Shovel(ElysiumGearMaterial material) {
            super(material.getToolTier(), properties(material, SHOVEL_DAMAGE, SHOVEL_SPEED));
            this.material = material;
        }

        @Override
        public ElysiumGearMaterial material() {
            return material;
        }

        @Override
        public float shapeDamage() {
            return SHOVEL_DAMAGE;
        }

        @Override
        public float getDamageMultiplier() {
            return 0.85F;
        }

        @Override
        public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
            return elysiumModifiers(stack, super.getDefaultAttributeModifiers(stack),
                    EquipmentSlotGroup.MAINHAND);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                    List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            appendSocketTooltip(stack, tooltip);
        }
    }

    public static class Hoe extends HoeItem implements MaterialTool {

        private final ElysiumGearMaterial material;

        public Hoe(ElysiumGearMaterial material) {
            super(material.getToolTier(), properties(material, HOE_DAMAGE, HOE_SPEED));
            this.material = material;
        }

        @Override
        public ElysiumGearMaterial material() {
            return material;
        }

        @Override
        public float shapeDamage() {
            return HOE_DAMAGE;
        }

        @Override
        public float getDamageMultiplier() {
            return 0.8F;
        }

        @Override
        public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
            return elysiumModifiers(stack, super.getDefaultAttributeModifiers(stack),
                    EquipmentSlotGroup.MAINHAND);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                    List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            appendSocketTooltip(stack, tooltip);
        }
    }
}
