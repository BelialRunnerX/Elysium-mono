package com.elysium.lib.tree;

import com.elysium.lib.tree.ElysiumTreeRules.Keystone;
import com.elysium.lib.tree.ElysiumTreeRules.Reward;
import com.elysium.lib.tree.ElysiumTreeRules.Start;
import com.elysium.lib.tree.ElysiumTreeRules.Subject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Ledger Web laid out: every node with a position, its rewards and its
 * neighbours, built from {@link ElysiumTreeRules} by one deterministic layout.
 *
 * <h2>The shape</h2>
 * A hub in the middle ("Enrolled"). Ring one: the twelve stat clusters, a
 * notable at each cluster's heart with its small nodes on a circle round it,
 * roads between neighbouring clusters and six spokes in to the hub. Ring two:
 * the fourteen system and element clusters, larger, with roads between
 * neighbours and a road in to the nearest ring-one cluster. Keystones hang
 * off their cluster on a short road outward. The rim: fifteen starts, one per
 * origin and calling, each on a road in to the cluster its rules name. The
 * frontier: sixteen larger clusters outside the rim, one for each part of the
 * pack the first two rings do not name, each reached by a road out from the
 * nearest road on the rim.
 *
 * <p>A cluster is entered through its small nodes: a road meets the small
 * nearest to it, and the notable is linked to every third small, so the heart
 * of a cluster is never one step from the road. Adding a subject row to the
 * rules adds its cluster, its roads and its gates without a point placed by
 * hand, which is the whole reason the layout is arithmetic.
 *
 * <p>Pure Java: the harness builds this on the JDK and prints it for the
 * headless preview; the server and the screen build the same object.
 */
public final class ElysiumTree {

    private ElysiumTree() {
    }

    public static final String KIND_HUB = "hub";
    public static final String KIND_SMALL = "small";
    public static final String KIND_NOTABLE = "notable";
    public static final String KIND_ROAD = "road";
    public static final String KIND_KEYSTONE = "keystone";
    public static final String KIND_START = "start";

    /** The hub's id: the one node every player has for free. */
    public static final String HUB = "enrolled";

    // Radii, in web units (the screen scales them).
    public static final double R_SPOKE = 70;
    public static final double R_RING1 = 150;
    public static final double R_CLUSTER1 = 27;
    public static final double R_LINK = 190;
    public static final double R_RING2 = 252;
    public static final double R_CLUSTER2 = 38;
    public static final double R_KEY_ROAD = 304;
    public static final double R_KEYSTONE = 332;
    public static final double R_START_ROAD = 330;
    public static final double R_START = 362;
    /** The frontier: a road out from the rim, then the third ring of clusters. */
    public static final double R_OUT = 404;
    public static final double R_RING3 = 478;
    public static final double R_CLUSTER3 = 44;
    /** The outermost node's distance from the hub, which the screen fits to. */
    public static final double R_EDGE = R_RING3 + R_CLUSTER3;

    /** One node of the web. */
    public record Node(String id, String kind, String cluster, double x, double y, List<Reward> rewards,
                       List<String> links) {
        public boolean linkedTo(String other) {
            return links.contains(other);
        }
    }

    private static final Map<String, Node> NODES;
    private static final List<Node> ALL;

    static {
        Map<String, MutableNode> built = build();
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (MutableNode node : built.values()) {
            nodes.put(node.id, new Node(node.id, node.kind, node.cluster, round(node.x), round(node.y),
                    List.copyOf(node.rewards), List.copyOf(node.links)));
        }
        NODES = Collections.unmodifiableMap(nodes);
        ALL = List.copyOf(nodes.values());
    }

    public static List<Node> all() {
        return ALL;
    }

    public static Node node(String id) {
        return NODES.get(id);
    }

    public static boolean exists(String id) {
        return NODES.containsKey(id);
    }

    /** The start node of an origin or calling, or null. */
    public static Node startOf(String originOrCalling) {
        return NODES.get("start_" + originOrCalling);
    }

    public static Node keystoneOf(String doctrine) {
        return NODES.get("key_" + doctrine);
    }

    /** Every node reachable from a set of held nodes along links, held nodes included. */
    public static Set<String> reachable(Set<String> from) {
        Set<String> seen = new HashSet<>(from);
        Deque<String> queue = new ArrayDeque<>(from);
        while (!queue.isEmpty()) {
            Node at = NODES.get(queue.poll());
            if (at == null) {
                continue;
            }
            for (String link : at.links()) {
                if (seen.add(link)) {
                    queue.add(link);
                }
            }
        }
        return seen;
    }

    /**
     * Every node of a held set that a walk from the roots reaches without
     * leaving the held set. A player holds two starts, which are two roots,
     * and what is held hangs off either: a refund is allowed when what is
     * left still all hangs off some root, which is this set being the whole
     * held set. (The first rule asked the held set to be one connected piece,
     * which no player with two starts could ever satisfy — found by hand,
     * 28 Sep 2026: every refund refused as a "bridge".)
     */
    public static Set<String> rootedWithin(Set<String> held, Set<String> roots) {
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        for (String root : roots) {
            if (held.contains(root) && seen.add(root)) {
                queue.add(root);
            }
        }
        while (!queue.isEmpty()) {
            Node at = NODES.get(queue.poll());
            if (at == null) {
                continue;
            }
            for (String link : at.links()) {
                if (held.contains(link) && seen.add(link)) {
                    queue.add(link);
                }
            }
        }
        return seen;
    }

    /** Whether a set of node ids is connected through its own links (empty and single sets are). */
    public static boolean connected(Set<String> held) {
        if (held.size() < 2) {
            return true;
        }
        String first = held.iterator().next();
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        seen.add(first);
        queue.add(first);
        while (!queue.isEmpty()) {
            Node at = NODES.get(queue.poll());
            if (at == null) {
                continue;
            }
            for (String link : at.links()) {
                if (held.contains(link) && seen.add(link)) {
                    queue.add(link);
                }
            }
        }
        return seen.size() == held.size();
    }

    // ------------------------------------------------------------------
    // The layout
    // ------------------------------------------------------------------

    private static final class MutableNode {
        final String id;
        final String kind;
        final String cluster;
        final double x;
        final double y;
        final List<Reward> rewards = new ArrayList<>();
        final List<String> links = new ArrayList<>();

        MutableNode(String id, String kind, String cluster, double x, double y) {
            this.id = id;
            this.kind = kind;
            this.cluster = cluster;
            this.x = x;
            this.y = y;
        }
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static Map<String, MutableNode> build() {
        Map<String, MutableNode> nodes = new LinkedHashMap<>();
        MutableNode hub = put(nodes, HUB, KIND_HUB, "hub", 0, 0);

        // Ring one: the stat clusters.
        List<Subject> ring1 = ElysiumTreeRules.onRing(ElysiumTreeRules.RING_STATS);
        List<Subject> ring2 = ElysiumTreeRules.onRing(ElysiumTreeRules.RING_SYSTEMS);
        List<String> gates1 = new ArrayList<>();
        for (int i = 0; i < ring1.size(); i++) {
            double angle = 2 * Math.PI * i / ring1.size();
            cluster(nodes, ring1.get(i), angle, R_RING1, R_CLUSTER1, ElysiumTreeRules.SMALLS_STAT);
            gates1.add(ring1.get(i).id());
        }
        // Roads between ring-one neighbours, at the midpoint angle on the ring.
        for (int i = 0; i < ring1.size(); i++) {
            Subject a = ring1.get(i);
            Subject b = ring1.get((i + 1) % ring1.size());
            double angle = 2 * Math.PI * (i + 0.5) / ring1.size();
            MutableNode road = put(nodes, "road_r1_" + i, KIND_ROAD, "road", R_RING1 * Math.cos(angle), R_RING1 * Math.sin(angle));
            road.rewards.add(Reward.stat(a.stats().get(0), ElysiumTreeRules.ROAD));
            link(nodes, road.id, nearestSmall(nodes, a.id(), road));
            link(nodes, road.id, nearestSmall(nodes, b.id(), road));
        }
        // Six spokes from the hub to every other stat cluster.
        for (int i = 0; i < ring1.size(); i += 2) {
            Subject a = ring1.get(i);
            double angle = 2 * Math.PI * i / ring1.size();
            MutableNode spoke = put(nodes, "road_spoke_" + i, KIND_ROAD, "road", R_SPOKE * Math.cos(angle), R_SPOKE * Math.sin(angle));
            spoke.rewards.add(Reward.stat(a.stats().get(0), ElysiumTreeRules.ROAD));
            link(nodes, hub.id, spoke.id);
            link(nodes, spoke.id, nearestSmall(nodes, a.id(), spoke));
        }

        // Ring two: the system and element clusters.
        for (int j = 0; j < ring2.size(); j++) {
            double angle = 2 * Math.PI * j / ring2.size();
            cluster(nodes, ring2.get(j), angle, R_RING2, R_CLUSTER2, ElysiumTreeRules.SMALLS_SYSTEM);
        }
        for (int j = 0; j < ring2.size(); j++) {
            Subject a = ring2.get(j);
            Subject b = ring2.get((j + 1) % ring2.size());
            double angle = 2 * Math.PI * (j + 0.5) / ring2.size();
            MutableNode road = put(nodes, "road_r2_" + j, KIND_ROAD, "road", R_RING2 * Math.cos(angle), R_RING2 * Math.sin(angle));
            road.rewards.add(Reward.stat(a.stats().get(1), ElysiumTreeRules.ROAD));
            link(nodes, road.id, nearestSmall(nodes, a.id(), road));
            link(nodes, road.id, nearestSmall(nodes, b.id(), road));
            // The road in: from this cluster to the ring-one cluster nearest its angle.
            double inAngle = 2 * Math.PI * j / ring2.size();
            MutableNode in = put(nodes, "road_in_" + j, KIND_ROAD, "road", R_LINK * Math.cos(inAngle), R_LINK * Math.sin(inAngle));
            in.rewards.add(Reward.stat(a.stats().get(2 % a.stats().size()), ElysiumTreeRules.ROAD));
            link(nodes, in.id, nearestSmall(nodes, a.id(), in));
            Subject inner = ring1.get(nearestIndex(inAngle, ring1.size()));
            link(nodes, in.id, nearestSmall(nodes, inner.id(), in));
        }

        // Keystones, hung off their cluster on a short road outward.
        for (int j = 0; j < ring2.size(); j++) {
            Subject subject = ring2.get(j);
            if (subject.keystone() == null) {
                continue;
            }
            Keystone keystone = ElysiumTreeRules.keystone(subject.keystone());
            double angle = 2 * Math.PI * j / ring2.size();
            MutableNode road = put(nodes, "road_key_" + keystone.doctrine(), KIND_ROAD, "road",
                    R_KEY_ROAD * Math.cos(angle), R_KEY_ROAD * Math.sin(angle));
            road.rewards.add(Reward.stat(keystone.gain(), ElysiumTreeRules.ROAD));
            link(nodes, road.id, nearestSmall(nodes, subject.id(), road));
            MutableNode key = put(nodes, "key_" + keystone.doctrine(), KIND_KEYSTONE, subject.id(),
                    R_KEYSTONE * Math.cos(angle), R_KEYSTONE * Math.sin(angle));
            key.rewards.add(Reward.doctrine(keystone.doctrine()));
            key.rewards.add(Reward.stat(keystone.gain(), ElysiumTreeRules.KEYSTONE_GAIN));
            key.rewards.add(Reward.stat(keystone.loss(), -ElysiumTreeRules.KEYSTONE_LOSS));
            link(nodes, road.id, key.id);
        }

        // The rim: a start per origin and calling, on a road in to its cluster.
        // Starts share a cluster's neighbourhood, so each is offset round the
        // rim by its position among the starts that enter beside the same
        // cluster, and never on top of the keystone's angle.
        Map<String, Integer> perSubject = new LinkedHashMap<>();
        for (Start start : ElysiumTreeRules.STARTS) {
            int j = indexOf(ring2, start.subject());
            int nth = perSubject.merge(start.subject(), 1, Integer::sum) - 1;
            double step = 2 * Math.PI / ring2.size();
            double angle = step * j + step * (0.22 + 0.28 * nth);
            MutableNode road = put(nodes, "road_start_" + start.id(), KIND_ROAD, "road",
                    R_START_ROAD * Math.cos(angle), R_START_ROAD * Math.sin(angle));
            road.rewards.add(Reward.stat(start.stat(), ElysiumTreeRules.ROAD));
            link(nodes, road.id, nearestSmall(nodes, start.subject(), road));
            MutableNode node = put(nodes, "start_" + start.id(), KIND_START, start.subject(),
                    R_START * Math.cos(angle), R_START * Math.sin(angle));
            node.rewards.add(Reward.stat(start.stat(), ElysiumTreeRules.START));
            link(nodes, road.id, node.id);
        }
        // The frontier: the third ring, outside the starts. Each cluster has a
        // road out to it from the nearest road on the rim (a start's road or a
        // keystone's), never from a start itself: a start is only its owner's,
        // and the frontier is everyone's.
        List<Subject> ring3 = ElysiumTreeRules.onRing(ElysiumTreeRules.RING_FRONTIER);
        List<MutableNode> rim = new ArrayList<>();
        for (MutableNode node : nodes.values()) {
            if (node.id.startsWith("road_start_") || node.id.startsWith("road_key_")) {
                rim.add(node);
            }
        }
        for (int k = 0; k < ring3.size(); k++) {
            double angle = 2 * Math.PI * k / ring3.size();
            cluster(nodes, ring3.get(k), angle, R_RING3, R_CLUSTER3, ElysiumTreeRules.SMALLS_FRONTIER);
        }
        for (int k = 0; k < ring3.size(); k++) {
            Subject a = ring3.get(k);
            Subject b = ring3.get((k + 1) % ring3.size());
            double angle = 2 * Math.PI * (k + 0.5) / ring3.size();
            MutableNode road = put(nodes, "road_r3_" + k, KIND_ROAD, "road", R_RING3 * Math.cos(angle), R_RING3 * Math.sin(angle));
            road.rewards.add(Reward.stat(a.stats().get(1), ElysiumTreeRules.ROAD));
            link(nodes, road.id, nearestSmall(nodes, a.id(), road));
            link(nodes, road.id, nearestSmall(nodes, b.id(), road));
            double outAngle = 2 * Math.PI * k / ring3.size();
            MutableNode out = put(nodes, "road_out_" + k, KIND_ROAD, "road", R_OUT * Math.cos(outAngle), R_OUT * Math.sin(outAngle));
            out.rewards.add(Reward.stat(a.stats().get(2 % a.stats().size()), ElysiumTreeRules.ROAD));
            link(nodes, out.id, nearestSmall(nodes, a.id(), out));
            MutableNode nearest = null;
            double best = Double.MAX_VALUE;
            for (MutableNode candidate : rim) {
                double d = (candidate.x - out.x) * (candidate.x - out.x) + (candidate.y - out.y) * (candidate.y - out.y);
                if (d < best) {
                    best = d;
                    nearest = candidate;
                }
            }
            link(nodes, out.id, nearest.id);
        }
        return nodes;
    }

    /** A cluster: the notable at its heart, the smalls on a circle, the notable linked to every third small. */
    private static void cluster(Map<String, MutableNode> nodes, Subject subject, double angle, double ring,
                                double radius, int smalls) {
        double cx = ring * Math.cos(angle);
        double cy = ring * Math.sin(angle);
        MutableNode notable = put(nodes, subject.id() + "_notable", KIND_NOTABLE, subject.id(), cx, cy);
        notable.rewards.add(Reward.stat(subject.stats().get(0), ElysiumTreeRules.NOTABLE));
        notable.rewards.add(Reward.stat(subject.stats().get(subject.stats().size() > 1 ? 1 : 0), ElysiumTreeRules.NOTABLE));
        List<String> ids = new ArrayList<>();
        for (int k = 0; k < smalls; k++) {
            double a = angle + 2 * Math.PI * k / smalls;
            MutableNode small = put(nodes, subject.id() + "_" + (k + 1), KIND_SMALL, subject.id(),
                    cx + radius * Math.cos(a), cy + radius * Math.sin(a));
            small.rewards.add(Reward.stat(subject.stats().get(k % subject.stats().size()), ElysiumTreeRules.SMALL));
            ids.add(small.id);
        }
        for (int k = 0; k < smalls; k++) {
            link(nodes, ids.get(k), ids.get((k + 1) % smalls));
            if (k % 3 == 0) {
                link(nodes, notable.id, ids.get(k));
            }
        }
    }

    private static MutableNode put(Map<String, MutableNode> nodes, String id, String kind, String cluster, double x, double y) {
        if (nodes.containsKey(id)) {
            throw new IllegalStateException("the web already has a node " + id);
        }
        MutableNode node = new MutableNode(id, kind, cluster, x, y);
        nodes.put(id, node);
        return node;
    }

    private static void link(Map<String, MutableNode> nodes, String a, String b) {
        MutableNode na = nodes.get(a);
        MutableNode nb = nodes.get(b);
        if (na == null || nb == null) {
            throw new IllegalStateException("a link to a node that is not there: " + a + " - " + b);
        }
        if (!na.links.contains(b)) {
            na.links.add(b);
        }
        if (!nb.links.contains(a)) {
            nb.links.add(a);
        }
    }

    /** The small node of a cluster nearest to a node: the gate a road meets. */
    private static String nearestSmall(Map<String, MutableNode> nodes, String cluster, MutableNode from) {
        String best = null;
        double bestDistance = Double.MAX_VALUE;
        for (MutableNode node : nodes.values()) {
            if (!node.cluster.equals(cluster) || !node.kind.equals(KIND_SMALL)) {
                continue;
            }
            double d = (node.x - from.x) * (node.x - from.x) + (node.y - from.y) * (node.y - from.y);
            if (d < bestDistance) {
                bestDistance = d;
                best = node.id;
            }
        }
        if (best == null) {
            throw new IllegalStateException("the cluster " + cluster + " has no small node to enter by");
        }
        return best;
    }

    private static int nearestIndex(double angle, int count) {
        double step = 2 * Math.PI / count;
        int index = (int) Math.round(angle / step);
        return ((index % count) + count) % count;
    }

    private static int indexOf(List<Subject> subjects, String id) {
        for (int i = 0; i < subjects.size(); i++) {
            if (subjects.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new IllegalStateException("no cluster " + id + " on the second ring");
    }
}
