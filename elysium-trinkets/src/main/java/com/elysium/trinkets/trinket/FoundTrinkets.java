package com.elysium.trinkets.trinket;

import com.elysium.lib.standing.ElysiumRewards;
import com.elysium.lib.trinket.ElysiumTrinket;
import com.elysium.trinkets.ElysiumTrinkets;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a found trinket is found.
 *
 * <h2>The gap this closes</h2>
 *
 * {@link UniqueTrinkets} and {@link CompanionTrinkets} are "found rather than
 * made", and each had a loot table of its own for a dungeon to point at — and
 * for a while nothing pointed at any of them. Twenty-eight tables, referenced
 * nowhere, is twenty-eight items that exist in the creative tab and nowhere
 * else, which is the exact failure the validator's "all obtainable" line was
 * written to catch and, until it read this class, could not.
 *
 * <h2>Two ways in</h2>
 *
 * <ul>
 *   <li><b>Vanilla's own chests.</b> The generator writes one aggregate table,
 *       {@code trinkets/found}, rolling once over every found trinket, and one
 *       global loot modifier per entry in {@link #CHESTS} that adds it at
 *       {@link #CHEST_CHANCE}. The chests are the ones a player has already
 *       fought for — an ancient city, an end city, a bastion's treasure room,
 *       a stronghold's library, a mansion — because a rule that changes how
 *       you play is a thing to find at the end of something, not in a
 *       mineshaft's minecart.</li>
 *   <li><b>Elysium's dungeons.</b> {@link #roll} is a reward provider on the
 *       library's shelf, answering only the top tier and only sometimes, so
 *       the deepest loot room of an elysium-dungeons run can hand one out
 *       while elysium-core's ingots and runes stay what a chest usually
 *       holds. The provider declines every other tier; the library then asks
 *       the next one, which is the contract.</li>
 * </ul>
 *
 * <p>Which trinket comes out is uniform over the roster, on purpose: a found
 * trinket is a question — is this worth a slot? — and weighting the roster is
 * answering it for the player.
 */
public final class FoundTrinkets {

    private FoundTrinkets() {
    }

    /** One vanilla chest in twenty holds a found trinket. */
    public static final float CHEST_CHANCE = 0.05F;

    /** How often the top-tier reward shelf answers with a trinket rather than declining. */
    public static final float REWARD_CHANCE = 0.15F;

    /**
     * The vanilla chests that can hold one. Read by the generator, which
     * writes one loot modifier per entry; a chest added here is a chest in the
     * game and nothing else needs to change.
     */
    public static final List<String> CHESTS = List.of(
            "minecraft:chests/ancient_city",
            "minecraft:chests/end_city_treasure",
            "minecraft:chests/bastion_treasure",
            "minecraft:chests/stronghold_library",
            "minecraft:chests/woodland_mansion");

    /** Every found trinket: the uniques, then the companions, in roster order. */
    public static List<ElysiumTrinket> all() {
        List<ElysiumTrinket> out = new ArrayList<>();
        for (ElysiumTrinket trinket : UniqueTrinkets.ALL) {
            out.add(trinket);
        }
        for (ElysiumTrinket trinket : CompanionTrinkets.ALL) {
            out.add(trinket);
        }
        return List.copyOf(out);
    }

    /**
     * The reward provider: a found trinket off the top shelf, sometimes.
     *
     * <p>Declines below {@link ElysiumRewards#MAX_TIER} and declines most of
     * the time at it, so the shelf stays elysium-core's and a trinket is what
     * a top-tier chest occasionally is. The item is looked up when the roll
     * happens, in a loaded world, never at registration.
     */
    static ItemStack roll(int tier, RandomSource random) {
        if (tier < ElysiumRewards.MAX_TIER) {
            return ItemStack.EMPTY;
        }
        if (random.nextFloat() >= REWARD_CHANCE) {
            return ItemStack.EMPTY;
        }
        List<ElysiumTrinket> found = all();
        DeferredHolder<net.minecraft.world.item.Item, net.minecraft.world.item.Item> item =
                ElysiumTrinkets.itemFor(found.get(random.nextInt(found.size())));
        return item == null ? ItemStack.EMPTY : new ItemStack(item.get());
    }

    /** Puts the provider on the library's shelf. Called once, from the mod constructor. */
    public static void registerRewards() {
        ElysiumRewards.register(FoundTrinkets::roll);
    }
}
