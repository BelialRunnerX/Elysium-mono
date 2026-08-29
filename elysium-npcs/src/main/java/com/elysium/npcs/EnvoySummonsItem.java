package com.elysium.npcs;

import com.elysium.npcs.entity.EnvoyKind;
import com.elysium.npcs.entity.ImperialEnvoy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/**
 * Summons one named member of the court.
 *
 * <h2>Why not a spawn egg</h2>
 *
 * {@code DeferredSpawnEggItem} spawns an entity <em>type</em>, and all five of
 * these are the same type. An egg would put a Sentinel on the ground five
 * different ways, because the type alone does not say who arrives — the kind
 * does, and it has to be set after the entity exists.
 *
 * It also reads better. These are not eggs; they are writs.
 */
public class EnvoySummonsItem extends Item {

    private final EnvoyKind kind;

    public EnvoySummonsItem(EnvoyKind kind, Item.Properties properties) {
        super(properties.stacksTo(16));
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos where = context.getClickedPos().above();
        ImperialEnvoy envoy = ElysiumNpcs.ENVOY.get().create(level);
        if (envoy == null) {
            return InteractionResult.FAIL;
        }
        envoy.setKind(kind);
        // Not transient: one placed by hand stays until it is dealt with or
        // killed. The twenty-minute clock is for the ones that arrive
        // uninvited, and applying it here would make a summoned envoy vanish
        // while its summoner was still walking back with a tribute.
        envoy.setTransient(false);
        envoy.moveTo(where.getX() + 0.5D, where.getY(), where.getZ() + 0.5D,
                context.getHorizontalDirection().toYRot(), 0.0F);
        envoy.setPersistenceRequired();
        level.addFreshEntity(envoy);
        context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(kind.displayName().copy()
                .withStyle(net.minecraft.ChatFormatting.GOLD));
    }
}
