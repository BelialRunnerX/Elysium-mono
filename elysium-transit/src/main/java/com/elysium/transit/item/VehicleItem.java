package com.elysium.transit.item;

import com.elysium.transit.entity.VehicleEntity;
import com.elysium.transit.rules.TransitRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Supplier;

/**
 * The item that puts one down.
 *
 * <p>Its tooltip is the vehicle's own numbers rather than prose, because the
 * one question anybody has in a shop window is how this rung differs from the
 * one below it, and four numbers answer it faster than a sentence.
 */
public class VehicleItem extends Item {

    private final TransitRules.Vehicle rung;
    private final Supplier<? extends EntityType<VehicleEntity>> type;

    public VehicleItem(TransitRules.Vehicle rung,
                       Supplier<? extends EntityType<VehicleEntity>> type) {
        super(new Item.Properties().stacksTo(1));
        this.rung = rung;
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos on = context.getClickedPos().relative(
                context.getClickedFace() == Direction.UP ? Direction.UP
                        : context.getClickedFace());
        VehicleEntity vehicle = type.get().create(level);
        if (vehicle == null) {
            return InteractionResult.PASS;
        }
        vehicle.moveTo(on.getX() + 0.5, on.getY(), on.getZ() + 0.5,
                context.getHorizontalDirection().toYRot(), 0.0F);
        if (!level.noCollision(vehicle)) {
            return InteractionResult.PASS;
        }
        level.addFreshEntity(vehicle);
        context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("elysiumtransit.vehicle.tooltip",
                String.format("%.0f", rung.topSpeed()),
                String.valueOf(rung.slots()),
                String.valueOf(rung.seats()),
                String.format("%.0f", TransitRules.range(rung, TransitRules.TANK,
                        TransitRules.Surface.ROAD, 0.0F)))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
