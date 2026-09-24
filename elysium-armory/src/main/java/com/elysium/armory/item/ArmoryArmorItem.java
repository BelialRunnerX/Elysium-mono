package com.elysium.armory.item;

import com.elysium.armory.registry.ArmoryArmorMaterial;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Modular armour. An {@code ArmorItem} so the game equips it and renders it
 * worn; everything it is worth comes from its parts.
 */
public class ArmoryArmorItem extends ArmorItem implements ArmoryGear {

    private final String gearType;

    public ArmoryArmorItem(String gearType, Type type, Properties properties) {
        super(ArmoryArmorMaterial.holder(), type, properties.stacksTo(1));
        this.gearType = gearType;
    }

    @Override
    public String gearType() {
        return gearType;
    }

    /** The piece's own computed armour, not the material's — the material grants nothing. */
    @Override
    public int getDefense() {
        return 0;
    }

    /**
     * The material's enchantment value is nothing: the piece's own is read off
     * its parts, by the stack-aware overload below.
     */
    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return ArmoryGearLogic.enchantmentValue(stack);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return !ArmoryGearLogic.isBroken(stack) && ArmoryGearLogic.enchantmentValue(stack) > 0;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        ArmoryGearLogic.tick(this, stack, level);
    }

    @Override
    public <T extends net.minecraft.world.entity.LivingEntity> int damageItem(ItemStack stack, int amount,
                                                                              T entity,
                                                                              java.util.function.Consumer<net.minecraft.world.item.Item> onBroken) {
        return ArmoryGearLogic.damage(stack, amount, entity);
    }

    /** Snow walker: powder snow holds up anyone wearing a piece with the trait. */
    @Override
    public boolean canWalkOnPowderedSnow(ItemStack stack, net.minecraft.world.entity.LivingEntity wearer) {
        return com.elysium.armory.trait.ArmoryCodedTraits.snowWalker(wearer);
    }

    /** Brilliant: worn, it reads as gold to a piglin. */
    @Override
    public boolean makesPiglinsNeutral(ItemStack stack, net.minecraft.world.entity.LivingEntity wearer) {
        return com.elysium.armory.trait.ArmoryCodedTraits.brilliant(stack);
    }
}
