package com.elysium.arsenal.client;

import com.elysium.arsenal.ElysiumArsenal;
import com.elysium.arsenal.item.BowItem;
import com.elysium.arsenal.registry.RangedItems;
import com.elysium.arsenal.registry.UniqueItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The two numbers a bow's model needs, on every bow in the module.
 *
 * <h2>Why this file exists at all</h2>
 *
 * The game registers {@code pulling} and {@code pull} for its own bow and its
 * own crossbow, by hand, in a client class nobody else can reach. An item that
 * animates a draw and does not do this is not subtly wrong — it holds the
 * unpulled model through the whole draw and then fires, which reads as a bow
 * that does not work.
 *
 * <p>The loop walks the registry rather than naming bows. Thirty-six bows
 * registered by hand and thirty-five given their properties is a bug found
 * only by the player who made the thirty-sixth.
 */
@EventBusSubscriber(modid = ElysiumArsenal.MODID, bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ArsenalClient {

    private ArsenalClient() {
    }

    /** Whether the bow is being drawn right now: zero or one. */
    public static final ResourceLocation PULLING =
            ResourceLocation.withDefaultNamespace("pulling");

    /** How far through the draw it is: zero to one. */
    public static final ResourceLocation PULL =
            ResourceLocation.withDefaultNamespace("pull");

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Registration is not thread-safe, and client setup runs off-thread.
        event.enqueueWork(ArsenalClient::describeEveryBow);
    }

    private static void describeEveryBow() {
        int described = 0;
        for (DeferredItem<? extends Item> held : RangedItems.BOWS.values()) {
            describe(held.get());
            described++;
        }
        for (DeferredItem<Item> held : UniqueItems.ALL.values()) {
            if (held.get() instanceof BowItem) {
                describe(held.get());
                described++;
            }
        }
        // A bow whose draw is not described holds one frame for the whole
        // draw, which looks like a bow that has stopped working rather than
        // like a missing model, so nothing would ever report it.
        int expected = RangedItems.BOWS.size() + UniqueItems.rangedCount();
        if (described != expected) {
            throw new IllegalStateException("there are " + expected + " bows and "
                    + described + " of them know how to draw");
        }
    }

    /**
     * The draw, as the model sees it.
     *
     * <p>Read off the item rather than assumed: a sling draws in three ticks
     * and a longbow in twenty-five, and a model told the same fraction for
     * both would animate one of them at four times the speed it fires.
     */
    private static void describe(Item item) {
        if (!(item instanceof BowItem bow)) {
            return;
        }
        ItemProperties.register(item, PULLING, (stack, level, holder, seed) ->
                holder != null && holder.isUsingItem() && holder.getUseItem() == stack
                        ? 1.0F : 0.0F);
        ItemProperties.register(item, PULL, (stack, level, holder, seed) -> {
            if (holder == null || holder.getUseItem() != stack) {
                return 0.0F;
            }
            return bow.drawn(stack.getUseDuration(holder) - holder.getUseItemRemainingTicks());
        });
    }
}
