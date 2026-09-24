package com.elysium.lib.social;

import com.elysium.lib.registry.ElysiumRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.gossip.GossipType;

/**
 * A thing worth remembering, and what remembering it is worth.
 *
 * <h2>Why the weight lives here and not on the memory</h2>
 *
 * A recorded memory stores what happened, to whom, where and when — and
 * nothing about how bad it was. The judgement is looked up from the kind every
 * time it is needed.
 *
 * That is the difference between a system you can tune and one you cannot. If
 * each memory carried its own weight, deciding that witnessing a killing should
 * hurt more would apply only to killings witnessed after the change: every
 * villager in every existing world would go on holding the old opinion, and
 * there would be no way to tell the two populations apart. Looking the weight
 * up means the table is the only place the number exists, and changing it
 * changes what everybody already remembers.
 *
 * <h2>Half-life, not an expiry</h2>
 *
 * Memories fade rather than vanish. A villager you hit once and then traded
 * with for an hour should end up thinking well of you, and a villager who
 * watched you kill their neighbour should still be wary tomorrow — those are
 * different rates, not a single "forget after N ticks". So each kind carries a
 * half-life and {@link ElysiumMemories} decays its weight against it.
 *
 * <h2>The gossip type is how a price changes</h2>
 *
 * Elysium keeps the episode: the narrative, the one a player can be shown.
 * Vanilla already has the machinery for turning a villager's opinion of you
 * into what it charges, and duplicating that would mean two systems disagreeing
 * about the same relationship. So each kind names a vanilla {@link GossipType},
 * and recording a memory pushes one delta into it. The gossip is an
 * <em>output</em> of the memory, never a second copy of it.
 */
public final class ElysiumMemoryKind {

    /** Every kind of thing anyone can remember. Content mods add to this. */
    public static final ElysiumRegistry<ElysiumMemoryKind> REGISTRY =
            new ElysiumRegistry<>("memory kind");

    private final ResourceLocation id;
    private final int weight;
    private final int halfLife;
    private final GossipType gossip;
    private final int gossipAmount;

    private ElysiumMemoryKind(ResourceLocation id, int weight, int halfLife,
                              GossipType gossip, int gossipAmount) {
        this.id = id;
        this.weight = weight;
        this.halfLife = halfLife;
        this.gossip = gossip;
        this.gossipAmount = gossipAmount;
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private int weight;
        private int halfLife = 20 * 60 * 20;
        private GossipType gossip;
        private int gossipAmount;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        /** How much this moves an opinion, positive or negative. */
        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }

        /** Ticks until the memory is worth half what it was. */
        public Builder halfLife(int ticks) {
            this.halfLife = ticks;
            return this;
        }

        /** The vanilla gossip this pushes, so prices follow the relationship. */
        public Builder gossip(GossipType type, int amount) {
            this.gossip = type;
            this.gossipAmount = amount;
            return this;
        }

        public ElysiumMemoryKind register() {
            if (weight == 0) {
                throw new IllegalStateException(
                        "memory kind '" + id + "' has no weight. A memory that "
                        + "moves no opinion is a memory nothing can act on, "
                        + "which is the failure this whole system exists to "
                        + "avoid");
            }
            if (halfLife <= 0) {
                throw new IllegalStateException(
                        "memory kind '" + id + "' has a half-life of " + halfLife
                        + ". Memories fade; one that does not would make a "
                        + "single bad first meeting permanent");
            }
            return REGISTRY.register(id, new ElysiumMemoryKind(
                    id, weight, halfLife, gossip, gossipAmount));
        }
    }

    // ------------------------------------------------------------------

    public ResourceLocation getId() {
        return id;
    }

    public int getWeight() {
        return weight;
    }

    public int getHalfLife() {
        return halfLife;
    }

    public GossipType getGossip() {
        return gossip;
    }

    public int getGossipAmount() {
        return gossipAmount;
    }

    public boolean isGood() {
        return weight > 0;
    }

    /**
     * What this reads as to a player, with the other party's name in it.
     *
     * A key rather than a sentence, for the reason the standing ledger gives:
     * storing rendered English would put one language in the save file.
     */
    public Component describe(String who) {
        return Component.translatable("elysium.memory." + id.getNamespace()
                + "." + id.getPath(), who);
    }
}
