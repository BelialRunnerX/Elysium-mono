package com.elysium.armory.item;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The plain case: a piece of modular gear that needs no vanilla class of its
 * own — a sword, a pickaxe, a ring. Everything it knows is in
 * {@link ArmoryGearLogic}; what makes it a sword rather than a pickaxe is its
 * gear type and the parts on the stack.
 */
public class ArmoryGearItem extends Item implements ArmoryGear {

    private final String gearType;

    public ArmoryGearItem(String gearType, Properties properties) {
        super(properties.stacksTo(1));
        this.gearType = gearType;
    }

    @Override
    public String gearType() {
        return gearType;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        ArmoryGearLogic.tick(this, stack, level);
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
    public <T extends net.minecraft.world.entity.LivingEntity> int damageItem(ItemStack stack, int amount,
                                                                              T entity,
                                                                              java.util.function.Consumer<net.minecraft.world.item.Item> onBroken) {
        return ArmoryGearLogic.damage(stack, amount, entity);
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        return ArmoryGearLogic.useOn(context);
    }

    /**
     * Spoon: a pickaxe with the trait also takes what a shovel would, so the
     * block drops rather than being knocked out for nothing.
     */
    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, net.minecraft.world.level.block.state.BlockState state) {
        return super.isCorrectToolForDrops(stack, state)
                || com.elysium.armory.trait.ArmoryCodedTraits.spoonMines(stack, state);
    }
}
