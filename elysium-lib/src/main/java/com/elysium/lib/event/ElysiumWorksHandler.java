package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.claim.ElysiumClaims;
import com.elysium.lib.doctrine.ElysiumDoctrines;
import com.elysium.lib.imperial.ElysiumInspection;
import com.elysium.lib.imperial.ElysiumInspections;
import com.elysium.lib.standing.ElysiumStanding;
import com.elysium.lib.works.ElysiumConspicuity;
import com.elysium.lib.works.ElysiumSite;
import com.elysium.lib.works.ElysiumWorks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Unregistered building, and the Empire finding it.
 *
 * The rules are in {@link ElysiumConspicuity}; the record is in
 * {@link ElysiumWorks}. This file is only the wiring between a placement and
 * those two, and it is kept that thin on purpose: every number that matters
 * is executed by a harness, and nothing here is.
 *
 * <h2>Inside a claim, this does nothing</h2>
 *
 * A claimed placement already raises the claim's floor, in
 * {@code ElysiumStandingHandler}. Charging it here as well would tax the one
 * player who registered twice for the same wall, which is the upside-down
 * incentive this feature exists to turn over.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumWorksHandler {

    private ElysiumWorksHandler() {
    }

    /** Machines from mods that are not Elysium. Ships empty; a pack fills it. */
    public static final TagKey<Block> INDUSTRY = tag("works/industry");

    /** Vanilla blocks that are loud in quantity: furnaces, metal, redstone. */
    public static final TagKey<Block> CONSPICUOUS = tag("works/conspicuous");

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, path));
    }

    /** How loud one block is, before doctrine. */
    public static int weightOf(BlockState state) {
        if (ElysiumBuiltHandler.isElysiumMachine(state) || state.is(INDUSTRY)) {
            return ElysiumConspicuity.WEIGHT_MACHINE;
        }
        if (state.is(CONSPICUOUS)) {
            return ElysiumConspicuity.WEIGHT_CONSPICUOUS;
        }
        return ElysiumConspicuity.WEIGHT_NONE;
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof Player player)) {
            return;
        }
        // A creative builder is building, not playing against the Empire —
        // the same exemption the hazard handler makes.
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        int weight = weightOf(event.getPlacedBlock());
        if (weight <= 0) {
            return;
        }
        if (ElysiumClaims.of(level).at(event.getPos()) != null) {
            return;                        // registered: the claim files it
        }

        weight = ElysiumConspicuity.scaled(weight,
                ElysiumDoctrines.suspicionMultiplier(player));

        long now = level.getGameTime();
        ElysiumWorks works = ElysiumWorks.of(level);
        int before = works.siteAt(event.getPos(), now).weight();
        ElysiumSite.Placed outcome = works.built(event.getPos(), weight, now);

        if (!outcome.noticed()) {
            if (ElysiumConspicuity.warns(before, outcome.site().weight())) {
                player.displayClientMessage(Component.translatable(
                                "elysium.works.warning")
                        .withStyle(ChatFormatting.YELLOW), true);
            }
            return;
        }

        ElysiumStanding.addSuspicion(player, outcome.suspicion(),
                ElysiumStanding.CAUSE_WORKS);
        player.displayClientMessage(Component.translatable(
                        "elysium.works.noticed", outcome.suspicion())
                .withStyle(ChatFormatting.RED), false);

        // The same gate the extraction path keeps: below the notice threshold
        // the Empire is not watching anybody, and a post planted for a player
        // with a clean record would be the one place that rule failed.
        if (ElysiumInspections.canPlant()
                && ElysiumStanding.getSuspicion(player) >= ElysiumStanding.NOTICE) {
            ElysiumInspection post = ElysiumInspections.of(level)
                    .plant(level, player, event.getPos());
            if (post != null) {
                player.displayClientMessage(Component.translatable(
                                "elysium.inspection.planted",
                                post.anchor().getX(), post.anchor().getY(),
                                post.anchor().getZ(),
                                ElysiumInspection.LIFETIME / (20 * 60))
                        .withStyle(ChatFormatting.RED), false);
            }
        }
    }
}
