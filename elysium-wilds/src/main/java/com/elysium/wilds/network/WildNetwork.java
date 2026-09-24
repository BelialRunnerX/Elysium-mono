package com.elysium.wilds.network;

import com.elysium.wilds.ElysiumWilds;
import com.elysium.wilds.registry.WildComponents;
import com.elysium.wilds.registry.WildItems;
import com.elysium.wilds.rules.WildRules;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/**
 * The compass's one round trip, and the one place the search is priced.
 *
 * <p>The search runs here and nowhere else, at a size decided by
 * {@link WildRules} rather than by whoever is holding the compass. Letting the
 * client ask for a radius would be letting the client ask the server to do
 * arbitrary work, which is the same bug as an unbounded search with an extra
 * step in it.
 */
@EventBusSubscriber(modid = ElysiumWilds.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class WildNetwork {

    private WildNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(WildPayloads.OfferPlaces.TYPE,
                WildPayloads.OfferPlaces.STREAM,
                (payload, context) -> context.enqueueWork(() ->
                        com.elysium.wilds.client.WildsClientHooks.offer(payload)));
        registrar.playToClient(WildPayloads.Found.TYPE, WildPayloads.Found.STREAM,
                (payload, context) -> context.enqueueWork(() ->
                        com.elysium.wilds.client.WildsClientHooks.found(payload)));
        registrar.playToServer(WildPayloads.LookFor.TYPE, WildPayloads.LookFor.STREAM,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer asked) {
                        look(asked, payload.place());
                    }
                }));
    }

    /** Hand the client the list of places, without it having asked for a search. */
    public static void offer(ServerPlayer player, List<String> places) {
        PacketDistributor.sendToPlayer(player, new WildPayloads.OfferPlaces(places));
    }

    /**
     * One search, the same size every time.
     *
     * <p>{@link WildRules#MOST_SEARCH_BLOCKS} out, sampled every
     * {@link WildRules#stepFor} blocks, which multiply to
     * {@link WildRules#SAMPLES_PER_SEARCH} whatever is being looked for. A
     * compass that searched further for rarer things would be a compass whose
     * worst case is a biome nobody thought about.
     */
    private static void look(ServerPlayer player, String place) {
        ServerLevel level = player.serverLevel();
        ResourceLocation named = ResourceLocation.tryParse(place);
        if (named == null) {
            return;
        }
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        if (!biomes.containsKey(named)) {
            return;
        }
        int radius = (int) WildRules.MOST_SEARCH_BLOCKS;
        int step = (int) Math.round(WildRules.stepFor(WildRules.MOST_SEARCH_BLOCKS));
        Pair<BlockPos, ?> hit = level.findClosestBiome3d(
                holder -> holder.is(net.minecraft.resources.ResourceKey.create(
                        Registries.BIOME, named)),
                player.blockPosition(), radius, step, step);
        if (hit == null) {
            PacketDistributor.sendToPlayer(player,
                    new WildPayloads.Found(place, false, BlockPos.ZERO, 0));
            return;
        }
        BlockPos where = hit.getFirst();
        int blocks = (int) Math.round(Math.sqrt(
                where.distSqr(player.blockPosition())));
        remember(player, level, where, place);
        PacketDistributor.sendToPlayer(player,
                new WildPayloads.Found(place, true, where, blocks));
    }

    /** Point whichever compass the player is holding at what was found. */
    private static void remember(ServerPlayer player, ServerLevel level, BlockPos where,
                                 String place) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(WildItems.COMPASS.get())) {
                held.set(WildComponents.BEARING.get(), new WildComponents.Bearing(
                        GlobalPos.of(level.dimension(), where), place));
                return;
            }
        }
    }
}
