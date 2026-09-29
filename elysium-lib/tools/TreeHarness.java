import com.elysium.lib.tree.ElysiumTree;
import com.elysium.lib.tree.ElysiumTreeRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the Ledger Web on the JDK alone and fails on anything a player would
 * meet as a web that does not work.
 *
 *   javac -cp "/tmp/elysium-classes/elysium-lib" -d /tmp/th tools/TreeHarness.java
 *   java  -cp "/tmp/elysium-classes/elysium-lib:/tmp/th" TreeHarness [dump-file]
 *
 * What it proves: the rules table has no faults; the web is four hundred nodes
 * and more; every node is reachable from every start; no two nodes share a
 * position and none sit closer than a frame can draw; every cluster has one
 * notable; every keystone is on the web with its opposite elsewhere; a start's
 * first ten nodes are about its subject; and no build of forty points from any
 * start buys more than the stat scaling allows. With a file argument it writes
 * every node as one line ("id kind cluster x y rewards | links"), which
 * tools/tree_preview.py draws.
 */
public final class TreeHarness {

    private TreeHarness() {
    }

    private static int checks;
    private static int failures;

    private static void check(boolean ok, String complaint) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + complaint);
        }
    }

    /** The most one stat may reach from forty points on the web, whatever the path. */
    private static final int FORTY_POINT_CEILING = 40 * ElysiumTreeRules.NOTABLE + ElysiumTreeRules.KEYSTONE_GAIN;

    public static void main(String[] args) throws Exception {
        for (String fault : ElysiumTreeRules.faults()) {
            check(false, "rules: " + fault);
        }
        List<ElysiumTree.Node> all = ElysiumTree.all();
        check(all.size() >= 400, "the web is " + all.size() + " node(s); four hundred and more is the size asked for");
        // Fifty levels' points, twice over: a web a mid-game character could hold
        // whole is a picture with nothing left to choose.
        int fiftyLevels = 50 * ElysiumTreeRules.POINTS_PER_LEVEL;
        check(all.size() >= 2 * fiftyLevels, "fifty levels buy " + fiftyLevels + " nodes and the web is "
                + all.size() + "; the web must be at least twice what fifty levels buy, or a build is the whole web");

        // Positions: none shared, none too close to draw.
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                ElysiumTree.Node a = all.get(i);
                ElysiumTree.Node b = all.get(j);
                double d = Math.hypot(a.x() - b.x(), a.y() - b.y());
                if (d < 9.0) {
                    check(false, a.id() + " and " + b.id() + " sit " + Math.round(d) + " apart, which is one frame over another");
                }
            }
        }

        // Every node reachable from every start, and every link two-way.
        for (ElysiumTreeRules.Start start : ElysiumTreeRules.STARTS) {
            ElysiumTree.Node node = ElysiumTree.startOf(start.id());
            check(node != null, "the start " + start.id() + " is not on the web");
            if (node == null) {
                continue;
            }
            Set<String> reach = ElysiumTree.reachable(Set.of(node.id()));
            check(reach.size() == all.size(), "from " + start.id() + " only " + reach.size() + " of " + all.size()
                    + " node(s) can be reached");
            // The first ten nodes out from a start are about its subject.
            List<String> near = nearest(node, 10);
            int own = 0;
            for (String id : near) {
                ElysiumTree.Node n = ElysiumTree.node(id);
                if (n.cluster().equals(start.subject()) || n.kind().equals(ElysiumTree.KIND_ROAD)) {
                    own++;
                }
            }
            check(own >= 8, "the start " + start.id() + " has " + own + " of its first ten nodes about " + start.subject()
                    + "; a start is a career, not a crossroads");
        }
        for (ElysiumTree.Node node : all) {
            for (String link : node.links()) {
                ElysiumTree.Node other = ElysiumTree.node(link);
                check(other != null && other.linkedTo(node.id()), node.id() + " links to " + link + " and " + link
                        + " does not link back");
            }
            check(!node.links().isEmpty(), node.id() + " links to nothing");
        }

        // One notable per cluster; every keystone on the web, its opposite on another cluster.
        Map<String, Integer> notables = new HashMap<>();
        for (ElysiumTree.Node node : all) {
            if (node.kind().equals(ElysiumTree.KIND_NOTABLE)) {
                notables.merge(node.cluster(), 1, Integer::sum);
            }
        }
        for (ElysiumTreeRules.Subject subject : ElysiumTreeRules.SUBJECTS) {
            check(notables.getOrDefault(subject.id(), 0) == 1, "the cluster " + subject.id() + " has "
                    + notables.getOrDefault(subject.id(), 0) + " notable(s)");
        }
        for (ElysiumTreeRules.Keystone keystone : ElysiumTreeRules.KEYSTONES) {
            ElysiumTree.Node node = ElysiumTree.keystoneOf(keystone.doctrine());
            check(node != null, "the keystone " + keystone.doctrine() + " is not on the web");
            String opposite = ElysiumTreeRules.opposedTo(keystone.doctrine());
            ElysiumTree.Node other = opposite == null ? null : ElysiumTree.keystoneOf(opposite);
            check(node != null && other != null && !node.cluster().equals(other.cluster()),
                    "the keystone " + keystone.doctrine() + " and its opposite hang off the same cluster");
            if (node != null && other != null) {
                double d = Math.hypot(node.x() - other.x(), node.y() - other.y());
                check(d > ElysiumTree.R_RING2, "the keystone " + keystone.doctrine() + " and its opposite are "
                        + Math.round(d) + " apart; opposites belong on opposite sides");
            }
        }

        // A build of forty points from any start cannot buy more of one stat
        // than the ceiling: greedy along the best-paying reachable node.
        for (ElysiumTreeRules.Start start : ElysiumTreeRules.STARTS) {
            for (String stat : ElysiumTreeRules.STATS) {
                int best = greedy(start, stat, 40);
                check(best <= FORTY_POINT_CEILING, "forty points from " + start.id() + " buy " + best + " " + stat
                        + ", over the ceiling of " + FORTY_POINT_CEILING);
            }
        }

        // Two starts are two roots. A leaf off one comes back; the road it
        // hung from does not while it is held; a start with nothing on it
        // is still rooted by itself.
        {
            ElysiumTree.Node a = ElysiumTree.startOf("imperial");
            ElysiumTree.Node b = ElysiumTree.startOf("marksman");
            String road = b.links().get(0);
            String small = null;
            for (String link : ElysiumTree.node(road).links()) {
                if (!link.equals(b.id())) {
                    small = link;
                }
            }
            Set<String> roots = Set.of(a.id(), b.id());
            Set<String> held = new HashSet<>(Set.of(a.id(), b.id(), road, small));
            check(ElysiumTree.rootedWithin(held, roots).size() == 4, "a held set of two starts, a road and a small is not all rooted");
            Set<String> withoutSmall = new HashSet<>(held);
            withoutSmall.remove(small);
            check(ElysiumTree.rootedWithin(withoutSmall, roots).size() == 3, "giving back a leaf small leaves something unrooted");
            Set<String> withoutRoad = new HashSet<>(held);
            withoutRoad.remove(road);
            check(ElysiumTree.rootedWithin(withoutRoad, roots).size() == 2, "giving back a road with a small on it should leave the small unrooted, and did not");
            check(!ElysiumTree.connected(held), "two starts read as one connected piece, so the old rule would have allowed what the new one refuses");
        }

        int smalls = 0;
        int roads = 0;
        for (ElysiumTree.Node node : all) {
            if (node.kind().equals(ElysiumTree.KIND_SMALL)) {
                smalls++;
            }
            if (node.kind().equals(ElysiumTree.KIND_ROAD)) {
                roads++;
            }
        }
        System.out.println("ledger web: " + all.size() + " node(s): " + smalls + " small, " + notables.size()
                + " notable, " + ElysiumTreeRules.KEYSTONES.size() + " keystone, " + ElysiumTreeRules.STARTS.size()
                + " start, " + roads + " road; " + checks + " check(s), " + failures + " failure(s)");
        if (args.length > 0) {
            StringBuilder out = new StringBuilder();
            for (ElysiumTree.Node node : all) {
                out.append(node.id()).append(' ').append(node.kind()).append(' ').append(node.cluster()).append(' ')
                        .append(node.x()).append(' ').append(node.y()).append(' ');
                List<String> rewards = new ArrayList<>();
                for (ElysiumTreeRules.Reward reward : node.rewards()) {
                    rewards.add(reward.kind() + ":" + reward.id() + ":" + reward.amount());
                }
                out.append(String.join(",", rewards)).append(" | ").append(String.join(",", node.links())).append('\n');
            }
            java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]), out.toString());
        }
        if (failures > 0) {
            System.exit(1);
        }
    }

    /** The n nodes nearest a start along links, breadth first. */
    private static List<String> nearest(ElysiumTree.Node from, int n) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(from.id());
        List<String> frontier = new ArrayList<>(from.links());
        while (!frontier.isEmpty() && out.size() < n) {
            List<String> next = new ArrayList<>();
            for (String id : frontier) {
                if (seen.add(id) && out.size() < n) {
                    out.add(id);
                    next.addAll(ElysiumTree.node(id).links());
                }
            }
            frontier = next;
        }
        return out;
    }

    /** Points spent greedily on whatever reachable node pays most of a stat. */
    private static int greedy(ElysiumTreeRules.Start start, String stat, int points) {
        Set<String> held = new HashSet<>();
        held.add(ElysiumTree.startOf(start.id()).id());
        int total = value(ElysiumTree.startOf(start.id()), stat);
        for (int p = 0; p < points; p++) {
            String best = null;
            int bestValue = Integer.MIN_VALUE;
            for (String id : ElysiumTree.reachable(held)) {
                if (held.contains(id)) {
                    continue;
                }
                ElysiumTree.Node node = ElysiumTree.node(id);
                boolean adjacent = false;
                for (String link : node.links()) {
                    if (held.contains(link)) {
                        adjacent = true;
                        break;
                    }
                }
                if (!adjacent) {
                    continue;
                }
                int v = value(node, stat);
                if (v > bestValue) {
                    bestValue = v;
                    best = id;
                }
            }
            if (best == null) {
                break;
            }
            held.add(best);
            total += bestValue;
        }
        return total;
    }

    private static int value(ElysiumTree.Node node, String stat) {
        int v = 0;
        for (ElysiumTreeRules.Reward reward : node.rewards()) {
            if (reward.kind().equals("stat") && reward.id().equals(stat)) {
                v += reward.amount();
            }
        }
        return v;
    }
}
