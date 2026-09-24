package com.elysium.lib.craft;

import com.elysium.lib.ElysiumLib;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * How many of each thing a player has made.
 *
 * <h2>Why this is a count and not a skill level</h2>
 *
 * Because "how well can you make this" and "how well can you make things" are
 * different questions, and only the first one is interesting. A single crafting
 * level means a player who has made five hundred shovels forges a masterwork
 * blade on their first try; per-item counts mean somebody who specialised reads
 * as a specialist and somebody who dabbled reads as a dabbler.
 *
 * <h2>Why it is capped</h2>
 *
 * The top grade is reached at a hundred and twenty-eight of one thing, and
 * counting past that buys nothing. Stopping there means the map holds small
 * numbers forever rather than growing without limit in a world somebody plays
 * for a year — and it means an automated farm cannot turn into a number nobody
 * else can reach.
 */
public final class ElysiumCraftLog {

    private ElysiumCraftLog() {
    }

    /**
     * Where counting stops.
     *
     * One past the top grade's threshold, so the highest grade is reachable and
     * nothing beyond it is recorded.
     */
    public static final int CEILING = ElysiumQuality.COUNT_CEILING;

    private static final Codec<Map<String, Integer>> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT);

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ElysiumLib.MODID);

    public static final Supplier<AttachmentType<Map<String, Integer>>> MADE =
            ATTACHMENTS.register("craft_log", () ->
                    AttachmentType.builder(
                                    (Supplier<Map<String, Integer>>) LinkedHashMap::new)
                            .serialize(CODEC)
                            .copyOnDeath()
                            .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    /** The registry id of an item, as a log key. */
    public static String keyFor(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    /** How many of this a player has made before now. */
    public static int made(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return 0;
        }
        Integer held = player.getData(MADE.get()).get(keyFor(stack));
        return held == null ? 0 : held;
    }

    /** What grade this player would produce for this item right now. */
    public static ElysiumQuality gradeFor(Player player, ItemStack stack) {
        return ElysiumQuality.forPractice(made(player, stack));
    }

    /**
     * Record that one more has been made.
     *
     * Called <em>after</em> the grade is read, never before: the mark states
     * the skill at the moment of making, and incrementing first would give
     * every maker a free grade on their first attempt.
     */
    public static void record(Player player, ItemStack stack, int howMany) {
        if (player == null || stack == null || stack.isEmpty()
                || howMany <= 0 || player.level().isClientSide()) {
            return;
        }
        String key = keyFor(stack);
        if (key.isEmpty()) {
            return;
        }
        Map<String, Integer> log = new LinkedHashMap<>(player.getData(MADE.get()));
        int was = log.getOrDefault(key, 0);
        if (was >= CEILING) {
            return;
        }
        log.put(key, Math.min(CEILING, was + howMany));
        player.setData(MADE.get(), log);
    }
}
