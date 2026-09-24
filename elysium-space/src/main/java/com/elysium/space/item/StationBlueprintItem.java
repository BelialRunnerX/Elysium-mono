package com.elysium.space.item;

import com.elysium.space.station.SpaceStations;
import com.elysium.space.world.StationRules;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A station blueprint: used in High Orbit, it builds your space station (or
 * rebuilds it bigger) from the steel and lunite ingots you carry, and stands
 * you on its launch pad. Rockets flying to High Orbit then land there. The
 * blueprint is kept; the ingots are spent.
 */
public class StationBlueprintItem extends Item {

    private final StationRules.Size size;

    public StationBlueprintItem(StationRules.Size size, Properties properties) {
        super(properties);
        this.size = size;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer server)) {
            return InteractionResultHolder.success(held);
        }
        SpaceStations.Result result = SpaceStations.get(server.server).build(server, size);
        String key = "elysiumspace.station." + result.name().toLowerCase(java.util.Locale.ROOT);
        player.displayClientMessage(Component.translatable(key, size.steel, size.lunite), true);
        return result == SpaceStations.Result.BUILT || result == SpaceStations.Result.UPGRADED
                ? InteractionResultHolder.consume(held) : InteractionResultHolder.fail(held);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("elysiumspace.station.cost", size.steel, size.lunite));
    }
}
