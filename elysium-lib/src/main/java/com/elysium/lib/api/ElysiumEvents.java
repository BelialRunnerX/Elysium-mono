package com.elysium.lib.api;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The three decisions this library makes that somebody else might want a say
 * in (Silent Gear's SG-107).
 *
 * <h2>Why three, and why these three</h2>
 *
 * An event for everything is a public surface nobody can change afterwards and
 * a pile of dispatch in hot paths. So the rule used here is: an event exists
 * where an add-on would otherwise have to copy a whole system to change one
 * answer.
 *
 * <ul>
 *   <li><b>Rolling affixes onto a piece of gear.</b> The alternative is
 *       reimplementing the roller.</li>
 *   <li><b>How far along a player is.</b> World tier decides what mobs,
 *       loot and now enchanting are worth; a pack that keeps progression
 *       somewhere else has no other way to say so.</li>
 *   <li><b>Promoting a creature to an elite.</b> The alternative is
 *       un-promoting it afterwards, which is visible to the player as a
 *       flicker.</li>
 * </ul>
 *
 * Everything else an add-on might want is already data: affixes, traits, gear
 * sets, shelves, spawner runes and enchantments are all files, and a file
 * needs no event.
 *
 * <h2>Where they are fired</h2>
 *
 * On {@link NeoForge#EVENT_BUS}, on the server, at the moment the decision is
 * made and before anything has been written down. A listener that changes a
 * value changes what actually happens rather than correcting it afterwards.
 */
public final class ElysiumEvents {

    private ElysiumEvents() {
    }

    /**
     * Affixes are about to be rolled onto a stack.
     *
     * Cancel it and the piece stays plain. Change the count and the piece gets
     * that many instead; change the tier and it rolls from a different pool.
     */
    public static class AffixRoll extends Event implements ICancellableEvent {

        private final ItemStack stack;
        private int count;
        private int worldTier;

        public AffixRoll(ItemStack stack, int count, int worldTier) {
            this.stack = stack;
            this.count = count;
            this.worldTier = worldTier;
        }

        /** The piece about to be affixed. Not a copy: a listener may read its components. */
        public ItemStack getStack() {
            return stack;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = Math.max(0, count);
        }

        public int getWorldTier() {
            return worldTier;
        }

        public void setWorldTier(int worldTier) {
            this.worldTier = Math.max(0, worldTier);
        }
    }

    /**
     * A player's world tier has been worked out and is about to be used.
     *
     * Not cancellable: there is no such thing as "no tier", and a listener
     * that wanted one would be asking for a crash somewhere downstream. It can
     * be changed to any tier at all, including a much higher one — nothing in
     * this mod caps a tier, and a pack that wants its own progression curve
     * says so here.
     */
    public static class WorldTier extends Event {

        private final Player player;
        private int tier;

        public WorldTier(Player player, int tier) {
            this.player = player;
            this.tier = tier;
        }

        public Player getPlayer() {
            return player;
        }

        public int getTier() {
            return tier;
        }

        public void setTier(int tier) {
            this.tier = Math.max(0, tier);
        }
    }

    /**
     * A creature is about to be promoted.
     *
     * Cancel it and the creature is ordinary. Fired once, when the creature is
     * built, which is the only moment at which promoting it is invisible to
     * the player.
     */
    public static class Promote extends Event implements ICancellableEvent {

        private final Mob mob;
        private final int worldTier;
        private final String rank;

        public Promote(Mob mob, int worldTier, String rank) {
            this.mob = mob;
            this.worldTier = worldTier;
            this.rank = rank;
        }

        public Mob getMob() {
            return mob;
        }

        public int getWorldTier() {
            return worldTier;
        }

        /** {@code ELITE} or {@code INVADER}, as the rules named it. */
        public String getRank() {
            return rank;
        }
    }

    // ------------------------------------------------------------------
    // Firing
    // ------------------------------------------------------------------
    //
    // One method each, so that every fire site is a single call and the bus is
    // named in exactly one file. A system that fired its own events would be a
    // system that can forget to.

    /** @return the event, so the caller can read what listeners changed */
    public static AffixRoll fireAffixRoll(ItemStack stack, int count, int worldTier) {
        AffixRoll event = new AffixRoll(stack, count, worldTier);
        NeoForge.EVENT_BUS.post(event);
        return event;
    }

    /** @return the tier after anybody listening has had a say */
    public static int fireWorldTier(Player player, int tier) {
        WorldTier event = new WorldTier(player, tier);
        NeoForge.EVENT_BUS.post(event);
        return event.getTier();
    }

    /** @return true when the promotion should go ahead */
    public static boolean firePromote(Mob mob, int worldTier, String rank) {
        Promote event = new Promote(mob, worldTier, rank);
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }
}
