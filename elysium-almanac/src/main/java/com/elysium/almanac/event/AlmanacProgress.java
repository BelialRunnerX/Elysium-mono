package com.elysium.almanac.event;

import com.elysium.almanac.ElysiumAlmanac;
import com.elysium.almanac.registry.AlmanacAttachments;
import com.elysium.almanac.registry.AlmanacItems;
import com.elysium.almanac.rules.AlmanacRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;

/**
 * Noticing that a player has done something.
 *
 * <h2>Quietly, and never in the way</h2>
 *
 * Nothing in this pack is gated behind the almanac. A player who ignores it
 * entirely plays exactly the same game, and one who reads it is told what to
 * do next. So progress is <em>noticed</em> rather than claimed: there is no
 * button to press, no turning things in, and no step that can be missed by
 * having done it before opening the book.
 *
 * <h2>And cheaply</h2>
 *
 * The expensive check — looking through an inventory — runs once a second and
 * only against the quests a player could currently finish, which is a handful
 * out of forty. Everything else is an event that was going to fire anyway.
 */
@EventBusSubscriber(modid = ElysiumAlmanac.MODID)
public final class AlmanacProgress {

    private AlmanacProgress() {
    }

    /** How often an inventory is looked through. Not every tick. */
    public static final int LOOKS_EVERY = 20;

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || player.tickCount % LOOKS_EVERY != 0) {
            return;
        }
        Set<String> done = AlmanacAttachments.done(player);
        for (AlmanacRules.Quest quest : AlmanacRules.openTo(done)) {
            if (satisfied(player, quest)) {
                finish(player, quest);
            }
        }
    }

    /**
     * Whether everything a quest asks for is true right now.
     *
     * Only the kinds that can be answered by looking: the ones that are
     * answered by an event are marked when that event happens and are already
     * in the finished list by the time this runs.
     */
    private static boolean satisfied(Player player, AlmanacRules.Quest quest) {
        for (AlmanacRules.Task task : quest.tasks()) {
            switch (task.kind()) {
                case READ -> {
                    // A page of instructions is finished by being reachable.
                }
                case HAVE -> {
                    if (!holds(player, task.what(), task.count())) {
                        return false;
                    }
                }
                default -> {
                    return false;              // an event's business, not ours
                }
            }
        }
        return true;
    }

    private static boolean holds(Player player, String id, int wanted) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return false;                      // nothing installed makes it
        }
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(BuiltInRegistries.ITEM.get(key))) {
                found += stack.getCount();
                if (found >= wanted) {
                    return true;
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        BlockState placed = event.getPlacedBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(placed.getBlock());
        Set<String> done = AlmanacAttachments.done(player);
        for (AlmanacRules.Quest quest : AlmanacRules.openTo(done)) {
            boolean all = true;
            for (AlmanacRules.Task task : quest.tasks()) {
                if (task.kind() != AlmanacRules.Kind.PLACE
                        || !task.what().equals(id.toString())) {
                    all = false;
                    break;
                }
            }
            if (all) {
                finish(player, quest);
            }
        }
    }

    /**
     * Something died, and somebody killed it.
     *
     * <p>Counted per player rather than watched for, because a quest asking
     * for three of a thing a player has already killed two of would otherwise
     * never finish: they would have to go and kill three more without knowing
     * why. The tally is kept, so the third one finishes it.
     */
    @SubscribeEvent
    public static void onSlain(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(
                event.getEntity().getType());
        String named = id.toString();
        AlmanacAttachments.slew(player, named);
        Set<String> done = AlmanacAttachments.done(player);
        for (AlmanacRules.Quest quest : AlmanacRules.openTo(done)) {
            boolean all = true;
            for (AlmanacRules.Task task : quest.tasks()) {
                if (task.kind() != AlmanacRules.Kind.SLAY
                        || AlmanacAttachments.slain(player, task.what())
                        < task.count()) {
                    all = false;
                    break;
                }
            }
            if (all) {
                finish(player, quest);
            }
        }
    }

    @SubscribeEvent
    public static void onTravelled(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        String arrived = event.getTo().location().toString();
        Set<String> done = AlmanacAttachments.done(player);
        for (AlmanacRules.Quest quest : AlmanacRules.openTo(done)) {
            boolean all = true;
            for (AlmanacRules.Task task : quest.tasks()) {
                if (task.kind() != AlmanacRules.Kind.VISIT
                        || !task.what().equals(arrived)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                finish(player, quest);
            }
        }
    }

    /**
     * Hand a player the book the first time they arrive.
     *
     * A guide nobody is given is a guide nobody reads. It is craftable too, and
     * holds no state, so losing it costs nothing.
     */
    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!AlmanacAttachments.finish(player, GIVEN)) {
            return;
        }
        ItemStack book = new ItemStack(AlmanacItems.ALMANAC.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
    }

    /**
     * The marker for "this player has been handed a book".
     *
     * Kept in the same list as the quests, under a name no quest can have,
     * because a second list would be a second thing to sync and a second thing
     * to get wrong — and this one already survives death for the same reason
     * the quests do.
     */
    public static final String GIVEN = "#given";

    private static void finish(Player player, AlmanacRules.Quest quest) {
        if (!AlmanacAttachments.finish(player, quest.id())) {
            return;
        }
        player.displayClientMessage(Component.translatable("elysiumalmanac.finished",
                Component.translatable("elysiumalmanac.quest." + quest.id())), true);
    }
}
