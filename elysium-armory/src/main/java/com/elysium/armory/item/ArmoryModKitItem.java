package com.elysium.armory.item;

import com.elysium.armory.material.ArmoryPart;
import com.elysium.armory.material.ArmoryPartType;
import com.elysium.armory.material.ArmoryParts;
import com.elysium.armory.registry.ArmoryComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The mod kit (Silent Gear's SG-52): takes a removable part back off a piece of
 * gear, whole.
 *
 * <p>Hold the gear in one hand and the kit in the other. The last removable
 * part put on comes off and is handed back — a tip, a grip, a binding, a
 * coating, a lining, a setting or an upgrade. What the piece <em>is</em> — its
 * main part and the rod it is held by — cannot be taken off, because that is
 * not modifying a tool, it is unmaking one.
 */
public class ArmoryModKitItem extends Item {

    public ArmoryModKitItem(Properties properties) {
        super(properties.stacksTo(1).durability(64));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack kit = player.getItemInHand(hand);
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        ItemStack gear = player.getItemInHand(other);
        if (!(gear.getItem() instanceof ArmoryGear holder)) {
            return InteractionResultHolder.pass(kit);
        }
        ArmoryGearParts parts = ArmoryGearLogic.partsOf(gear);
        ArmoryGearParts.Entry removable = null;
        for (ArmoryGearParts.Entry entry : parts.entries()) {
            ArmoryPart part = ArmoryParts.get().byId(entry.part());
            ArmoryPartType type = part == null ? null : ArmoryPartType.named(part.partType());
            if (type != null && type.removable()) {
                removable = entry;                  // the last one on is the first one off
            }
        }
        if (removable == null) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("elysiumarmory.mod_kit.nothing"), true);
            }
            return InteractionResultHolder.fail(kit);
        }
        if (!level.isClientSide()) {
            ItemStack taken = ArmoryBuilder.part(removable.part(), List.of(removable.materials().toArray(String[]::new)));
            gear.set(ArmoryComponents.PARTS.get(), parts.without(removable.part()));
            holder.recompute(gear);
            if (!taken.isEmpty() && !player.addItem(taken)) {
                player.drop(taken, false);
            }
            kit.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        return InteractionResultHolder.sidedSuccess(kit, level.isClientSide());
    }
}
