package com.elysium.lib.tree;

import com.elysium.lib.tree.ElysiumTreeRules.Reward;
import com.elysium.lib.tree.ElysiumTreeRules.Start;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * What a node of the Ledger Web is called, and what it says it gives.
 *
 * <p>Nothing here is a lookup table of four hundred names: a small node is
 * called by its stat, a notable by its cluster's name, a keystone by its
 * doctrine, a start by the origin or calling it belongs to, a road "Passage".
 * The words live in the language file under the keys the rest of the pack
 * already uses for stats, doctrines, races and classes, plus one per notable
 * and one per subject, so the validator can hold the file to the rules.
 */
public final class ElysiumTreeNames {

    private ElysiumTreeNames() {
    }

    public static String statKey(String stat) {
        return "elysium.stat." + stat;
    }

    public static String notableKey(String cluster) {
        return "elysium.tree.notable." + cluster;
    }

    public static String subjectKey(String cluster) {
        return "elysium.tree.subject." + cluster;
    }

    /** The node's title, styled by kind. */
    public static MutableComponent title(ElysiumTree.Node node) {
        switch (node.kind()) {
            case ElysiumTree.KIND_HUB:
                return Component.translatable("elysium.tree.hub").withStyle(ChatFormatting.WHITE);
            case ElysiumTree.KIND_ROAD:
                return Component.translatable("elysium.tree.road").withStyle(ChatFormatting.GRAY);
            case ElysiumTree.KIND_NOTABLE:
                return Component.translatable(notableKey(node.cluster())).withStyle(ChatFormatting.GOLD);
            case ElysiumTree.KIND_KEYSTONE: {
                String doctrine = ElysiumTreeState.doctrineOf(node);
                return Component.translatable("elysium.doctrine." + doctrine).withStyle(ChatFormatting.LIGHT_PURPLE);
            }
            case ElysiumTree.KIND_START: {
                String id = node.id().substring("start_".length());
                Start start = ElysiumTreeRules.start(id);
                String key = start != null && start.kind().equals("origin") ? "elysium.race." + id : "elysium.class." + id;
                return Component.translatable("elysium.tree.start", Component.translatable(key)).withStyle(ChatFormatting.AQUA);
            }
            default: {
                for (Reward reward : node.rewards()) {
                    if ("stat".equals(reward.kind())) {
                        return Component.translatable(statKey(reward.id())).withStyle(ChatFormatting.WHITE);
                    }
                }
                return Component.literal(node.id());
            }
        }
    }

    /** One line per reward: "+2 Vitality", "Doctrine: Sanctioned Extraction". */
    public static List<Component> rewards(ElysiumTree.Node node) {
        List<Component> out = new ArrayList<>();
        for (Reward reward : node.rewards()) {
            if ("stat".equals(reward.kind())) {
                String sign = reward.amount() >= 0 ? "+" : "";
                out.add(Component.translatable("elysium.tree.reward.stat", sign + reward.amount(),
                                Component.translatable(statKey(reward.id())))
                        .withStyle(reward.amount() >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
            } else if ("doctrine".equals(reward.kind())) {
                out.add(Component.translatable("elysium.tree.reward.doctrine",
                                Component.translatable("elysium.doctrine." + reward.id()))
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        return out;
    }

    /** The one-line explanation under the title. */
    public static Component blurb(ElysiumTree.Node node) {
        return switch (node.kind()) {
            case ElysiumTree.KIND_HUB -> Component.translatable("elysium.tree.hub.blurb");
            case ElysiumTree.KIND_ROAD -> Component.translatable("elysium.tree.road.blurb");
            case ElysiumTree.KIND_NOTABLE -> Component.translatable("elysium.tree.notable.blurb");
            case ElysiumTree.KIND_KEYSTONE -> Component.translatable("elysium.tree.keystone.blurb");
            case ElysiumTree.KIND_START -> Component.translatable("elysium.tree.start.blurb");
            default -> Component.translatable("elysium.tree.small.blurb");
        };
    }

    /** The cluster a node belongs to, named. */
    public static Component subject(ElysiumTree.Node node) {
        if (ElysiumTreeRules.subject(node.cluster()) == null) {
            return Component.empty();
        }
        return Component.translatable(subjectKey(node.cluster())).withStyle(ChatFormatting.DARK_GRAY);
    }
}
