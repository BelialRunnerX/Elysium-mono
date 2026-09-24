package com.elysium.comforts.event;

import com.elysium.comforts.ElysiumComforts;
import com.elysium.comforts.registry.ComfortAttachments;
import com.elysium.comforts.rules.ComfortRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * Loot that belongs to whoever opened it.
 *
 * <h2>What this fixes</h2>
 *
 * A dungeon chest on a server is a race. Whoever arrives first takes the lot,
 * and everybody else finds an empty box and no reason to have come. Every
 * answer to that is some version of "give each player their own copy"; what
 * differs is how much bookkeeping it costs.
 *
 * <h2>And how it costs less than the usual answer</h2>
 *
 * The roll is a <em>function</em> of the container and the player, so nothing
 * has to be remembered about who has opened what: the same player always gets
 * the same chest, for ever, with no record of them anywhere. Only what they
 * have already taken is stored — and that is let go of the moment they have
 * taken all of it.
 *
 * <h2>The original chest is never unpacked</h2>
 *
 * Which is the whole mechanism. A vanilla container rolls its loot table once,
 * the first time anybody looks, and is an ordinary chest from then on. This one
 * is never looked at: every player is handed a copy and the chest keeps its
 * table, so the hundredth player gets the same chance as the first.
 */
@EventBusSubscriber(modid = ElysiumComforts.MODID)
public final class LootKeeper {

    private LootKeeper() {
    }

    @SubscribeEvent
    public static void onOpen(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (player.isSecondaryUseActive() && !player.getMainHandItem().isEmpty()) {
            // They are placing something against it, not opening it.
            return;
        }
        BlockPos pos = event.getPos();
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof RandomizableContainerBlockEntity container)) {
            return;
        }
        ResourceKey<LootTable> key = container.getLootTable();
        if (key == null) {
            // An ordinary chest somebody filled by hand. Nothing to share out.
            return;
        }
        SimpleContainer share = shareFor(level, player, container, pos, key);
        if (share == null) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, opener) -> rowsFor(id, inventory, share),
                Component.translatable("elysiumcomforts.loot.yours")));
    }

    /**
     * This player's copy: the one they left, or a fresh roll.
     *
     * <p>The seed is worked out rather than looked up, so a player whose copy
     * was emptied and forgotten gets the same chest back if they somehow
     * return to it — and gets it empty, because what they took is the one
     * thing that <em>is</em> remembered, right up until there is nothing left
     * to remember.
     */
    private static SimpleContainer shareFor(ServerLevel level, ServerPlayer player,
                                            RandomizableContainerBlockEntity container,
                                            BlockPos pos, ResourceKey<LootTable> key) {
        CompoundTag shares = container.getData(ComfortAttachments.SHARES.get());
        String who = player.getUUID().toString();
        SimpleContainer copy = new SimpleContainer(Math.max(9, container.getContainerSize()));
        if (shares.contains(who)) {
            ListTag saved = shares.getList(who, 10);
            for (int at = 0; at < saved.size() && at < copy.getContainerSize(); at++) {
                CompoundTag one = saved.getCompound(at);
                int slot = one.getInt("Slot");
                ItemStack.parse(level.registryAccess(), one.getCompound("Item"))
                        .ifPresent(stack -> {
                            if (slot >= 0 && slot < copy.getContainerSize()) {
                                copy.setItem(slot, stack);
                            }
                        });
            }
        } else {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
            if (table == LootTable.EMPTY) {
                return null;
            }
            UUID id = player.getUUID();
            long seed = ComfortRules.seedFor(seedOf(container, pos),
                    id.getMostSignificantBits(), id.getLeastSignificantBits());
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withLuck(player.getLuck())
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .create(LootContextParamSets.CHEST);
            table.fill(copy, params, seed);
        }
        copy.addListener(changed -> save(container, player, copy, level));
        return copy;
    }

    /**
     * The container's own number.
     *
     * <p>Its loot seed when it has one, and where it is when it does not —
     * because two chests in the same structure with no seed between them must
     * still be two different chests, and their positions are the only thing
     * that differs.
     */
    private static long seedOf(RandomizableContainerBlockEntity container, BlockPos pos) {
        long seed = container.getLootTableSeed();
        return seed != 0L ? seed : pos.asLong();
    }

    /** Write a player's copy back, or forget them if they have cleared it. */
    private static void save(RandomizableContainerBlockEntity container,
                             ServerPlayer player, SimpleContainer copy,
                             ServerLevel level) {
        CompoundTag shares = container.getData(ComfortAttachments.SHARES.get()).copy();
        String who = player.getUUID().toString();
        ListTag saved = new ListTag();
        boolean empty = true;
        for (int slot = 0; slot < copy.getContainerSize(); slot++) {
            ItemStack stack = copy.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            empty = false;
            CompoundTag one = new CompoundTag();
            one.putInt("Slot", slot);
            one.put("Item", stack.save(level.registryAccess(), new CompoundTag()));
            saved.add(one);
        }
        if (ComfortRules.forgettable(empty)) {
            shares.remove(who);
        } else {
            shares.put(who, saved);
        }
        container.setData(ComfortAttachments.SHARES.get(), shares);
        container.setChanged();
    }

    /** As many rows as the container has, so a barrel is not a double chest. */
    private static ChestMenu rowsFor(int id, net.minecraft.world.entity.player.Inventory inventory,
                                     SimpleContainer container) {
        return container.getContainerSize() > 27
                ? ChestMenu.sixRows(id, inventory, container)
                : ChestMenu.threeRows(id, inventory, container);
    }
}
