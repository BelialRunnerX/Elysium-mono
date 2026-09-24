package com.elysium.wilds.item;

import com.elysium.wilds.network.WildNetwork;
import com.elysium.wilds.registry.WildComponents;
import com.elysium.wilds.rules.WildRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A compass that finds a kind of place rather than a direction.
 *
 * <h2>Why the cooldown is not a balance decision</h2>
 *
 * Finding the nearest instance of a biome is a synchronous scan on the server
 * thread whose cost grows with the square of how far it looks. A compass
 * without a bound on that is a compass that, held down, is a server that
 * stutters — and what gets reported is "the server lags", so nobody ever
 * connects it to the compass.
 *
 * <p>So the search here is a fixed size: it looks as far as it is ever allowed
 * to, and coarsely enough that doing so costs one stated number of samples.
 * Every search costs the same, for every biome, however rare. The cooldown
 * then turns that into a bound per player per second, which
 * {@link WildRules#tooExpensive()} checks and the harness sweeps.
 *
 * <p>The blurriness that buys this is real and is safe for a reason rather
 * than by luck: the grid is about a tenth of the width of a patch of biome, so
 * a scan cannot step over one.
 */
public class BiomeCompassItem extends Item {

    public BiomeCompassItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sent)) {
            return InteractionResultHolder.success(held);
        }
        List<String> places = new ArrayList<>();
        for (var biome : level.registryAccess().registryOrThrow(Registries.BIOME).keySet()) {
            places.add(biome.toString());
        }
        places.sort(String::compareTo);
        WildNetwork.offer(sent, places);
        player.getCooldowns().addCooldown(this, WildRules.COOLDOWN_TICKS);
        return InteractionResultHolder.success(held);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        WildComponents.Bearing bearing = stack.get(WildComponents.BEARING.get());
        if (bearing == null) {
            lines.add(Component.translatable("elysiumwilds.compass.idle"));
            return;
        }
        lines.add(Component.translatable("elysiumwilds.compass.holding",
                bearing.biome(),
                String.valueOf(bearing.where().pos().getX()),
                String.valueOf(bearing.where().pos().getZ())));
    }
}
