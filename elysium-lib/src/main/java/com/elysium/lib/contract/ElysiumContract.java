package com.elysium.lib.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One piece of issued work.
 *
 * <h2>Why the progress is on the contract and not beside it</h2>
 *
 * A contract and its progress have exactly the same lifetime: they are created
 * together, they are read together, and they are thrown away together. Keeping
 * them in two places would mean a completed contract whose progress had been
 * dropped, or progress against a contract that no longer exists, and both look
 * to a player like the office having lost their paperwork.
 *
 * <h2>Why "issued" is a timestamp and not a countdown</h2>
 *
 * A countdown has to be ticked, which means something has to run for every
 * contract every tick, including for players who are offline. A timestamp plus
 * a deadline is the same fact and is read when somebody asks — so a contract
 * expires on schedule in an unloaded world with nothing running, which is both
 * cheaper and harder to get wrong.
 *
 * @param kind     what sort of work
 * @param target   the item or faction id the work is about; empty for a kind
 *                 that has no subject, like a catalogue survey
 * @param quantity how many
 * @param done     how many so far
 * @param issued   the game time it was taken
 * @param deadline how long the office will wait, in ticks
 * @param pay      emeralds on delivery
 * @param standing what it moves on the issuer's meter, either way
 * @param empire   whether the Empire issued it; false for the Unsworn
 */
public record ElysiumContract(String kind, String target, int quantity, int done,
                              long issued, int deadline, int pay, int standing,
                              boolean empire) {

    public static final Codec<ElysiumContract> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("kind").forGetter(ElysiumContract::kind),
                    Codec.STRING.fieldOf("target").forGetter(ElysiumContract::target),
                    Codec.INT.fieldOf("quantity").forGetter(ElysiumContract::quantity),
                    Codec.INT.fieldOf("done").forGetter(ElysiumContract::done),
                    Codec.LONG.fieldOf("issued").forGetter(ElysiumContract::issued),
                    Codec.INT.fieldOf("deadline").forGetter(ElysiumContract::deadline),
                    Codec.INT.fieldOf("pay").forGetter(ElysiumContract::pay),
                    Codec.INT.fieldOf("standing").forGetter(ElysiumContract::standing),
                    Codec.BOOL.fieldOf("empire").forGetter(ElysiumContract::empire)
            ).apply(instance, ElysiumContract::new));

    public static final Codec<java.util.List<ElysiumContract>> LIST_CODEC =
            CODEC.listOf();

    /** The kind, or null for one this build no longer has. */
    public ElysiumContractKind resolve() {
        return ElysiumContractKind.byId(kind);
    }

    /** Whether the work is finished. */
    public boolean complete() {
        return done >= quantity;
    }

    /** Ticks left before the office gives up, or 0 once it has. */
    public long remaining(long now) {
        return Math.max(0L, issued + deadline - now);
    }

    /** Whether the office has given up. */
    public boolean expired(long now) {
        return remaining(now) <= 0L;
    }

    /**
     * The same contract with more done.
     *
     * Capped at the quantity asked for. Counting past it would make the
     * readout say "34 of 32", which reads as the office not knowing what it
     * asked for.
     */
    public ElysiumContract advancedBy(int more) {
        if (more <= 0) {
            return this;
        }
        return new ElysiumContract(kind, target, quantity,
                Math.min(quantity, done + more), issued, deadline, pay,
                standing, empire);
    }

    /** What failing this costs on the issuer's meter. */
    public int penalty() {
        ElysiumContractKind resolved = resolve();
        return resolved == null ? 1 : ElysiumTerms.penaltyFor(resolved, quantity);
    }
}
