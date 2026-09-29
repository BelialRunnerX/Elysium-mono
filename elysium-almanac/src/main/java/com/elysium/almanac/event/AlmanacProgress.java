package com.elysium.almanac.event;

import com.elysium.almanac.ElysiumAlmanac;
import com.elysium.almanac.registry.AlmanacAttachments;
import com.elysium.almanac.registry.AlmanacItems;
import com.elysium.almanac.rules.AlmanacRules;
import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.lib.tree.ElysiumTreeState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Noticing that a player has done something.
 *
 * <h2>Quietly, and never in the way</h2>
 *
 * Nothing in this pack is gated behind the almanac. A player who ignores it
 * entirely plays exactly the same game, and one who reads it is told what to
 * do next. So progress is <em>noticed</em> rather than claimed: there is no
 * button to press to finish a step, no turning things in, and no step that
 * can be missed by having done it before opening the book. The one button in
 * the book is the reward's, and that is a different thing: a reward is taken,
 * a step is done.
 *
 * <h2>And cheaply</h2>
 *
 * The expensive check — looking through an inventory — runs once a second and
 * only against the quests a player could currently finish, which is a handful
 * out of hundreds. Everything else is an event that was going to fire anyway,
 * and an event does two cheap things: it counts, against every task in the
 * book that names what happened, and it sweeps the open quests once.
 *
 * <h2>Counted from the first one</h2>
 *
 * A task answered by an event — placing, breaking, crafting, using, eating,
 * smelting, killing — is a running count kept on the player, and the count
 * starts the first time it happens whether or not any quest was open. A quest
 * asking for forty stone of somebody who has broken thirty is asking for ten.
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
        sweep(player);
    }

    /** Every open quest whose asks are all true right now is finished. */
    public static void sweep(Player player) {
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
     * Every kind is answered here, so a quest may mix them: a beast to kill
     * and a thing to hold. The counted kinds read the tally the events keep;
     * the rest look at the player.
     */
    private static boolean satisfied(Player player, AlmanacRules.Quest quest) {
        for (AlmanacRules.Task task : quest.tasks()) {
            boolean met = switch (task.kind()) {
                case READ -> true;             // finished by being reachable
                case HAVE -> holds(player, task.what(), task.count());
                case VISIT -> player.level().dimension().location().toString()
                        .equals(task.what());
                case LEVEL -> ElysiumCharacter.getLevel(player) >= task.count();
                case WEB -> ElysiumTreeState.held(player).size() >= task.count();
                case SLAY -> AlmanacAttachments.slain(player, task.what()) >= task.count();
                case PLACE, BREAK, CRAFT, USE, EAT, SMELT ->
                        AlmanacAttachments.count(player, task) >= task.count();
            };
            if (!met) {
                return false;
            }
        }
        return true;
    }

    private static boolean holds(Player player, String id, int wanted) {
        if (id.startsWith("#")) {
            return holdsTagged(player, id.substring(1), wanted);
        }
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

    /** Any of a tag, counted together: eight panes of any glass is eight panes. */
    private static boolean holdsTagged(Player player, String tag, int wanted) {
        ResourceLocation key = ResourceLocation.tryParse(tag);
        if (key == null) {
            return false;
        }
        TagKey<net.minecraft.world.item.Item> tagged = TagKey.create(Registries.ITEM, key);
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(tagged)) {
                found += stack.getCount();
                if (found >= wanted) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // The events, each of which counts and then sweeps
    // ------------------------------------------------------------------

    /**
     * Count what just happened against every task in the book of that kind
     * whose subject it is: the one id, or a tag it belongs to.
     */
    private static void credit(Player player, AlmanacRules.Kind kind, String id,
                               Predicate<ResourceLocation> inTag, int by) {
        for (AlmanacRules.Quest quest : AlmanacRules.QUESTS) {
            for (AlmanacRules.Task task : quest.tasks()) {
                if (task.kind() != kind) {
                    continue;
                }
                boolean matches;
                if (task.tag()) {
                    ResourceLocation tag = ResourceLocation.tryParse(task.subject());
                    matches = tag != null && inTag.test(tag);
                } else {
                    matches = task.what().equals(id);
                }
                if (matches) {
                    AlmanacAttachments.bump(player, task, by);
                }
            }
        }
        sweep(player);
    }

    private static Predicate<ResourceLocation> blockTags(BlockState state) {
        return tag -> state.is(TagKey.create(Registries.BLOCK, tag));
    }

    private static Predicate<ResourceLocation> itemTags(ItemStack stack) {
        return tag -> stack.is(TagKey.create(Registries.ITEM, tag));
    }

    @SubscribeEvent
    public static void onPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        BlockState placed = event.getPlacedBlock();
        String id = BuiltInRegistries.BLOCK.getKey(placed.getBlock()).toString();
        credit(player, AlmanacRules.Kind.PLACE, id, blockTags(placed), 1);
    }

    /** Mining and digging: one verb, whatever the tool. */
    @SubscribeEvent
    public static void onBroken(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        BlockState broken = event.getState();
        String id = BuiltInRegistries.BLOCK.getKey(broken.getBlock()).toString();
        credit(player, AlmanacRules.Kind.BREAK, id, blockTags(broken), 1);
    }

    /**
     * A block right-clicked: the main hand only, because the game asks the
     * off hand too and a machine opened once would otherwise count twice.
     */
    @SubscribeEvent
    public static void onUsed(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        BlockState used = event.getLevel().getBlockState(event.getPos());
        String id = BuiltInRegistries.BLOCK.getKey(used.getBlock()).toString();
        credit(player, AlmanacRules.Kind.USE, id, blockTags(used), 1);
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        ItemStack made = event.getCrafting();
        if (player == null || player.level().isClientSide() || made.isEmpty()) {
            return;
        }
        String id = BuiltInRegistries.ITEM.getKey(made.getItem()).toString();
        credit(player, AlmanacRules.Kind.CRAFT, id, itemTags(made), Math.max(1, made.getCount()));
    }

    @SubscribeEvent
    public static void onSmelted(PlayerEvent.ItemSmeltedEvent event) {
        Player player = event.getEntity();
        ItemStack out = event.getSmelting();
        if (player == null || player.level().isClientSide() || out.isEmpty()) {
            return;
        }
        String id = BuiltInRegistries.ITEM.getKey(out.getItem()).toString();
        credit(player, AlmanacRules.Kind.SMELT, id, itemTags(out), Math.max(1, out.getCount()));
    }

    /** Something eaten: the finish of a use, and only of food. */
    @SubscribeEvent
    public static void onEaten(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        ItemStack eaten = event.getItem();
        if (eaten.isEmpty() || !eaten.has(DataComponents.FOOD)) {
            return;
        }
        String id = BuiltInRegistries.ITEM.getKey(eaten.getItem()).toString();
        credit(player, AlmanacRules.Kind.EAT, id, itemTags(eaten), 1);
    }

    /**
     * Something died, and somebody killed it.
     *
     * <p>Counted under the beast's own id, and under every tag a SLAY task
     * names that the beast belongs to, so "one of any wight" is answered by
     * whichever wight it was.
     */
    @SubscribeEvent
    public static void onSlain(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        EntityType<?> type = event.getEntity().getType();
        String named = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        AlmanacAttachments.slew(player, named);
        for (AlmanacRules.Quest quest : AlmanacRules.QUESTS) {
            for (AlmanacRules.Task task : quest.tasks()) {
                if (task.kind() != AlmanacRules.Kind.SLAY || !task.tag()) {
                    continue;
                }
                ResourceLocation tag = ResourceLocation.tryParse(task.subject());
                if (tag != null && type.is(TagKey.create(Registries.ENTITY_TYPE, tag))) {
                    AlmanacAttachments.slew(player, task.what());
                }
            }
        }
        sweep(player);
    }

    /**
     * Arriving somewhere: the sweep reads where the player stands, so this is
     * only the prompt to look now rather than in up to a second.
     */
    @SubscribeEvent
    public static void onTravelled(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        sweep(player);
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
