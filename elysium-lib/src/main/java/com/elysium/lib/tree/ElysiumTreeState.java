package com.elysium.lib.tree;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.lib.character.ElysiumClass;
import com.elysium.lib.character.ElysiumRace;
import com.elysium.lib.doctrine.ElysiumDoctrine;
import com.elysium.lib.stats.ElysiumStat;
import com.elysium.lib.stats.ElysiumStatBlock;
import com.elysium.lib.tree.ElysiumTreeRules.Reward;
import com.mojang.serialization.Codec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What a player holds on the Ledger Web, and the two things they can do to it.
 *
 * <p>One attachment: the ids of the nodes allocated by hand. A player's starts
 * (their origin's and their calling's) are held without being stored, so a
 * character who chose before the web existed has them the moment they open
 * it, and a respec that empties the list leaves the starts where they were.
 *
 * <p>Points: {@link ElysiumTreeRules#POINTS_PER_LEVEL} per level from
 * {@link ElysiumTreeRules#FIRST_POINT_LEVEL}, less the nodes stored. An
 * allocation has to touch a held node and have a point to pay with; a keystone
 * also has to have its opposite unheld. A refund has to leave the held set
 * connected (a leaf comes off, a bridge does not), and costs a Ledger Erasure
 * from the player's inventory unless they are in creative. Keystones set and
 * clear the doctrine tags {@code ElysiumDoctrines} reads, so every reader in
 * the pack sees a keystone the way it always saw a doctrine.
 *
 * <p>Every method that changes something returns null on success, or the
 * translation key of the reason it did not — the screen and the command print
 * the same words.
 */
public final class ElysiumTreeState {

    private ElysiumTreeState() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ElysiumLib.MODID);

    /** The nodes allocated by hand, in the order they were taken. */
    public static final Supplier<AttachmentType<List<String>>> HELD =
            ATTACHMENTS.register("tree_held", () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf()).copyOnDeath().build());

    /**
     * The item a refund costs. The library ships no items and names none:
     * the core hands its Ledger Erasure in when it loads, and until something
     * does, a refund costs nothing (a library on its own has no economy).
     */
    public static Supplier<Item> erasure = () -> Items.AIR;

    public static final String WHY_NO_NODE = "elysium.tree.why.no_node";
    public static final String WHY_HELD = "elysium.tree.why.held";
    public static final String WHY_NOT_HELD = "elysium.tree.why.not_held";
    public static final String WHY_NO_POINT = "elysium.tree.why.no_point";
    public static final String WHY_NOT_ADJACENT = "elysium.tree.why.not_adjacent";
    public static final String WHY_OPPOSED = "elysium.tree.why.opposed";
    public static final String WHY_START = "elysium.tree.why.start";
    public static final String WHY_BRIDGE = "elysium.tree.why.bridge";
    public static final String WHY_NO_ERASURE = "elysium.tree.why.no_erasure";

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    // ------------------------------------------------------------------
    // What is held
    // ------------------------------------------------------------------

    /** The bare id of a race or class: the path of its registry id. */
    private static String bare(String serialised) {
        if (serialised == null) {
            return "";
        }
        int colon = serialised.indexOf(':');
        return colon < 0 ? serialised : serialised.substring(colon + 1);
    }

    /** The start nodes a player has for free: their origin's and their calling's. */
    public static Set<String> starts(Player player) {
        Set<String> out = new LinkedHashSet<>();
        ElysiumRace race = ElysiumCharacter.getRace(player);
        ElysiumClass job = ElysiumCharacter.getElysiumClass(player);
        if (race != null && ElysiumTree.startOf(bare(race.getSerialisedName())) != null) {
            out.add(ElysiumTree.startOf(bare(race.getSerialisedName())).id());
        }
        if (job != null && ElysiumTree.startOf(bare(job.getSerialisedName())) != null) {
            out.add(ElysiumTree.startOf(bare(job.getSerialisedName())).id());
        }
        return out;
    }

    /** The nodes stored by hand, those that still exist on the web. */
    public static List<String> stored(Player player) {
        List<String> out = new ArrayList<>();
        for (String id : player.getData(HELD.get())) {
            if (ElysiumTree.exists(id) && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** Everything held: the starts, then what was stored. */
    public static Set<String> held(Player player) {
        Set<String> out = new LinkedHashSet<>(starts(player));
        out.addAll(stored(player));
        return out;
    }

    public static boolean holds(Player player, String node) {
        return held(player).contains(node);
    }

    public static int spent(Player player) {
        return stored(player).size();
    }

    public static int earned(Player player) {
        int level = ElysiumCharacter.getLevel(player);
        return Math.max(0, level - ElysiumTreeRules.FIRST_POINT_LEVEL + 1) * ElysiumTreeRules.POINTS_PER_LEVEL;
    }

    public static int free(Player player) {
        return Math.max(0, earned(player) - spent(player));
    }

    // ------------------------------------------------------------------
    // Allocate and refund
    // ------------------------------------------------------------------

    /** Whether a node could be taken next: held neighbour, a point free, no opposed keystone held. */
    public static String canAllocate(Player player, String id) {
        ElysiumTree.Node node = ElysiumTree.node(id);
        if (node == null) {
            return WHY_NO_NODE;
        }
        Set<String> held = held(player);
        if (held.contains(id)) {
            return WHY_HELD;
        }
        if (free(player) <= 0) {
            return WHY_NO_POINT;
        }
        boolean adjacent = false;
        for (String link : node.links()) {
            if (held.contains(link)) {
                adjacent = true;
                break;
            }
        }
        if (!adjacent) {
            return WHY_NOT_ADJACENT;
        }
        String doctrine = doctrineOf(node);
        if (doctrine != null) {
            String opposite = ElysiumTreeRules.opposedTo(doctrine);
            if (opposite != null && held.contains("key_" + opposite)) {
                return WHY_OPPOSED;
            }
        }
        return null;
    }

    /** Takes a node; null on success, otherwise the reason. */
    public static String allocate(Player player, String id) {
        String why = canAllocate(player, id);
        if (why != null) {
            return why;
        }
        List<String> stored = stored(player);
        stored.add(id);
        player.setData(HELD.get(), List.copyOf(stored));
        String doctrine = doctrineOf(ElysiumTree.node(id));
        if (doctrine != null && ElysiumDoctrine.byId(doctrine) != null) {
            player.addTag(ElysiumDoctrine.byId(doctrine).tag());
        }
        return null;
    }

    /** Whether a node could be given back: stored, a leaf of what is held, and an erasure to pay with. */
    public static String canRefund(Player player, String id, boolean pay) {
        if (!ElysiumTree.exists(id)) {
            return WHY_NO_NODE;
        }
        if (starts(player).contains(id)) {
            return WHY_START;
        }
        if (!stored(player).contains(id)) {
            return WHY_NOT_HELD;
        }
        Set<String> after = held(player);
        after.remove(id);
        if (ElysiumTree.rootedWithin(after, starts(player)).size() != after.size()) {
            return WHY_BRIDGE;
        }
        if (pay && !player.getAbilities().instabuild && erasure.get() != Items.AIR && erasures(player) <= 0) {
            return WHY_NO_ERASURE;
        }
        return null;
    }

    /** Gives a node back; null on success, otherwise the reason. */
    public static String refund(Player player, String id, boolean pay) {
        String why = canRefund(player, id, pay);
        if (why != null) {
            return why;
        }
        if (pay && !player.getAbilities().instabuild) {
            takeErasure(player);
        }
        List<String> stored = stored(player);
        stored.remove(id);
        player.setData(HELD.get(), List.copyOf(stored));
        String doctrine = doctrineOf(ElysiumTree.node(id));
        if (doctrine != null && ElysiumDoctrine.byId(doctrine) != null) {
            player.removeTag(ElysiumDoctrine.byId(doctrine).tag());
        }
        return null;
    }

    /** Everything stored given back at once, no erasure asked: the command's reset. */
    public static int reset(Player player) {
        List<String> stored = stored(player);
        for (String id : stored) {
            String doctrine = doctrineOf(ElysiumTree.node(id));
            if (doctrine != null && ElysiumDoctrine.byId(doctrine) != null) {
                player.removeTag(ElysiumDoctrine.byId(doctrine).tag());
            }
        }
        player.setData(HELD.get(), List.of());
        return stored.size();
    }

    // ------------------------------------------------------------------
    // What it is worth
    // ------------------------------------------------------------------

    /** The stat block every held node adds up to. */
    public static ElysiumStatBlock block(Player player) {
        ElysiumStatBlock block = ElysiumStatBlock.EMPTY;
        for (String id : held(player)) {
            ElysiumTree.Node node = ElysiumTree.node(id);
            if (node == null) {
                continue;
            }
            for (Reward reward : node.rewards()) {
                if (!"stat".equals(reward.kind())) {
                    continue;
                }
                ElysiumStat stat = stat(reward.id());
                if (stat != null) {
                    block = block.with(stat, reward.amount());
                }
            }
        }
        return block;
    }

    /** A stat by the bare name the rules use, whichever namespace the registry filed it under. */
    public static ElysiumStat stat(String name) {
        for (String candidate : new String[] {name, "elysiumlib:" + name, "elysium:" + name}) {
            ElysiumStat stat = ElysiumStat.REGISTRY.get(candidate);
            if (stat != null) {
                return stat;
            }
        }
        return null;
    }

    public static String doctrineOf(ElysiumTree.Node node) {
        if (node == null) {
            return null;
        }
        for (Reward reward : node.rewards()) {
            if ("doctrine".equals(reward.kind())) {
                return reward.id();
            }
        }
        return null;
    }

    public static int erasures(Player player) {
        Item item = erasure.get();
        if (item == Items.AIR) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void takeErasure(Player player) {
        Item item = erasure.get();
        if (item == Items.AIR) {
            return;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                stack.shrink(1);
                return;
            }
        }
    }
}
