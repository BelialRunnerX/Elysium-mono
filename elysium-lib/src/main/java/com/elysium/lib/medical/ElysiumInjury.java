package com.elysium.lib.medical;

import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Everything a player is carrying. Immutable: every change is a new value, so
 * the attachment is only ever written whole and a half-applied change cannot
 * be saved.
 */
public record ElysiumInjury(List<ElysiumWound> wounds) {

    public static final ElysiumInjury NONE = new ElysiumInjury(List.of());

    /**
     * Lenient on the way in. A list with one wound this version cannot read —
     * a kind since removed, a save from a newer build — keeps every wound it
     * can read and drops the one it cannot. Strict decoding would fail the
     * whole list, and a failed attachment is a player who logs in with every
     * wound gone; this way they lose only the one nobody can name. The
     * harness decodes exactly that case.
     */
    public static final Codec<ElysiumInjury> CODEC = ElysiumWound.CODEC.listOf()
            .promotePartial(problem -> {})
            .xmap(ElysiumInjury::new, ElysiumInjury::wounds);

    public ElysiumInjury {
        wounds = List.copyOf(wounds);
    }

    public boolean isEmpty() {
        return wounds.isEmpty();
    }

    public int count(ElysiumWoundKind kind) {
        int count = 0;
        for (ElysiumWound wound : wounds) {
            if (wound.kind() == kind) {
                count++;
            }
        }
        return count;
    }

    /**
     * This injury with one more wound of a kind.
     *
     * At the cap a new wound does not stack — it reopens the most healed one
     * of that kind back to full. So being hit again while hurt keeps you hurt
     * for longer, which is right, but never makes you worse than the cap,
     * which is the line between wounded and crippled.
     */
    public ElysiumInjury wounded(ElysiumWoundKind kind) {
        List<ElysiumWound> next = new ArrayList<>(wounds);
        if (count(kind) < kind.stack()) {
            next.add(ElysiumWound.fresh(kind));
            return new ElysiumInjury(next);
        }
        int mostHealed = -1;
        for (int i = 0; i < next.size(); i++) {
            ElysiumWound wound = next.get(i);
            if (wound.kind() == kind && (mostHealed < 0
                    || wound.remaining() < next.get(mostHealed).remaining())) {
                mostHealed = i;
            }
        }
        next.set(mostHealed, ElysiumWound.fresh(kind));
        return new ElysiumInjury(next);
    }

    /** This injury after {@code elapsed} ticks at {@code rate}. */
    public ElysiumInjury recovered(long elapsed, int rate) {
        if (wounds.isEmpty()) {
            return this;
        }
        List<ElysiumWound> next = new ArrayList<>(wounds.size());
        for (ElysiumWound wound : wounds) {
            int left = ElysiumMedicine.healed(wound.remaining(), elapsed, rate);
            if (left > 0) {
                next.add(new ElysiumWound(wound.kind(), left));
            }
        }
        return new ElysiumInjury(next);
    }

    /** The kinds present in this injury and gone from {@code after}. What to tell the player has healed. */
    public Set<ElysiumWoundKind> healedBy(ElysiumInjury after) {
        Set<ElysiumWoundKind> healed = EnumSet.noneOf(ElysiumWoundKind.class);
        for (ElysiumWound wound : wounds) {
            if (after.count(wound.kind()) == 0) {
                healed.add(wound.kind());
            }
        }
        return healed;
    }

    /** The longest any wound here still has, in ticks of natural recovery. */
    public int longest() {
        int longest = 0;
        for (ElysiumWound wound : wounds) {
            longest = Math.max(longest, wound.remaining());
        }
        return longest;
    }
}
