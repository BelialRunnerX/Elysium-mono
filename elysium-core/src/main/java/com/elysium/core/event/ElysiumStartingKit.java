package com.elysium.core.event;

import com.elysium.core.Elysium;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * What a player starts with (Silent Gear's SG-105).
 *
 * <h2>Why it is a loot table</h2>
 *
 * Because the game already has a format for "a list of items, possibly with
 * counts, possibly with conditions", and a modpack already knows how to edit
 * one. A kit declared in a config would be a second format that only this mod
 * reads, a second parser to get wrong, and a thing a pack author has to look
 * up. The table is {@code elysium:gameplay/starting_kit}; overriding that one
 * file is the whole of changing it, and emptying it is the whole of turning it
 * off.
 *
 * <h2>Once, and remembered</h2>
 *
 * An attachment that survives death and is copied to the respawned player, so
 * a kit is given on the first join and never again. Keyed on the player rather
 * than on the world, because a player joining a server for the first time
 * should get one whatever anybody else has done.
 */
@EventBusSubscriber(modid = Elysium.MODID)
public final class ElysiumStartingKit {

    private ElysiumStartingKit() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Elysium.MODID);

    /** Whether this player has already been handed one. */
    public static final Supplier<AttachmentType<Boolean>> GIVEN =
            ATTACHMENTS.register("starting_kit", () ->
                    AttachmentType.<Boolean>builder(() -> false)
                            .serialize(Codec.BOOL)
                            .copyOnDeath()
                            .build());

    /** The table. A pack overrides this file and nothing else. */
    public static final ResourceKey<LootTable> KIT = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(Elysium.MODID, "gameplay/starting_kit"));

    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.getData(GIVEN.get())) {
            return;
        }
        player.setData(GIVEN.get(), true);
        LootTable table = player.server.reloadableRegistries().getLootTable(KIT);
        LootParams params = new LootParams.Builder(player.serverLevel())
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.GIFT);
        for (ItemStack stack : table.getRandomItems(params)) {
            if (stack.isEmpty()) {
                continue;
            }
            // Dropped rather than lost when the inventory is full, which it can
            // be: a pack may hand a kit to a player who joined with a full
            // inventory from a previous world.
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }
}
