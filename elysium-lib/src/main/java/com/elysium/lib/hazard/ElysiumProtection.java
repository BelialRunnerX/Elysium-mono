package com.elysium.lib.hazard;

import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.claim.ElysiumClaims;
import com.elysium.lib.item.ElysiumGear;
import com.elysium.lib.item.ElysiumSocketable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * What a player is wearing, read as protection against one hazard.
 *
 * <h2>Two ways to be protected, and why there are two</h2>
 *
 * Armour of the hazard's own element gives a full share. Armour of an element
 * that <em>beats</em> that element gives half.
 *
 * The first is the obvious one and the one a player will find without being
 * told: Plasma gear against the Plasma hazard, the same alignment rule runes
 * already use. If it were the only one, every hazard would have exactly one
 * right answer and a wardrobe would be a lookup table.
 *
 * The second is the ring doing work outside combat. A player who knows that
 * Neural and Dimensional beat Plasma has a second, worse, already-owned answer
 * to a burning world — which is the difference between "go and craft the fire
 * suit" and "what have I got that will do". Half rather than full, so knowing
 * the ring is a shortcut and not a replacement for the right gear.
 *
 * <h2>Why tier scales the share and the piece count does not</h2>
 *
 * Each matching piece contributes, so four pieces beat one — but through
 * {@link ElysiumResistance}, where the fourth piece is worth less than the
 * first. Tier raises the share itself, so a single late-game piece can be worth
 * more than a full set of early ones. That is the intended shape: progression
 * should let a player wear what they like rather than forcing four pieces of
 * one element forever.
 */
public final class ElysiumProtection {

    private ElysiumProtection() {
    }

    /**
     * What one matching piece removes at tier 0.
     *
     * Set against the whole budget rather than on its own. At 0.14 with a
     * 0.035 tier step, a fully equipped player at tier 10 — four pieces, a
     * sealed room, a meal and a trinket — shed 97.7% of the worst hazard and
     * could stand in it for six minutes, which is immunity wearing a
     * percentage. HazardHarness computes that total and fails below a residue
     * of 3%, so these two numbers are not free to drift upward.
     */
    public static final float BASE_SHARE = 0.13F;

    /**
     * How much stat each tier adds, in odds units.
     *
     * Re-pitched with BASE_SHARE when shares stopped being capped and started
     * adding as stats (the owner's ruling). Adding stats is gentler than the
     * old product of what each piece let through, so the same numbers bought
     * less: a full tier-0 set fell from 37% to 33%, and eight tiers no longer
     * doubled a piece. At 0.13 and 0.03 both design targets hold again — a
     * full set 37%, eight tiers more than doubling — and HazardHarness checks
     * them, not these numbers.
     */
    public static final float TIER_STEP = 0.03F;

    /** What a piece gets for beating the hazard's element instead of matching it. */
    public static final float RING_FRACTION = 0.5F;

    /**
     * The share one piece of gear contributes against one hazard.
     *
     * Zero for gear of an unrelated element, which is most of it.
     */
    public static float shareOf(ItemStack stack, ElysiumHazard hazard) {
        ElysiumSocketable gear = ElysiumGear.of(stack);
        if (gear == null || hazard == null) {
            return 0.0F;
        }
        ElysiumElement worn = gear.getElement();
        if (worn == null || !worn.isElemental()) {
            return 0.0F;
        }
        // The stat behind the share, turned into a share once: see
        // ElysiumResistance.pieceShare. No ceiling; this used to be a straight
        // percentage clamped at 95%, which an ascended piece reached.
        int tier = gear.getEffectiveTier(stack);
        if (worn.equals(hazard.getElement())) {
            return ElysiumResistance.pieceShare(BASE_SHARE, TIER_STEP, tier, 1.0F);
        }
        if (worn.isStrongAgainst(hazard.getElement())) {
            return ElysiumResistance.pieceShare(BASE_SHARE, TIER_STEP, tier, RING_FRACTION);
        }
        return 0.0F;
    }

    /**
     * Every share this entity is wearing, in slot order.
     *
     * Returned as an array rather than a total so the caller can hand it
     * straight to {@link ElysiumResistance#combine} alongside shares from
     * elsewhere — shelter, food, a trinket — without this class having to know
     * those exist.
     */
    public static float[] wornShares(LivingEntity entity, ElysiumHazard hazard) {
        List<Float> shares = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;               // a hazard is weather, not something you parry
            }
            float share = shareOf(entity.getItemBySlot(slot), hazard);
            if (share > 0.0F) {
                shares.add(share);
            }
        }
        float[] out = new float[shares.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = shares.get(i);
        }
        return out;
    }

    /** The total the worn set removes, before anything else is counted. */
    public static float fromArmour(LivingEntity entity, ElysiumHazard hazard) {
        return ElysiumResistance.combine(wornShares(entity, hazard));
    }

    /**
     * Every partial source this entity has against one hazard, in one array.
     *
     * <h3>Why the gathering is here rather than at the call site</h3>
     *
     * Because there will be more of them. Armour was the first, a room is the
     * second, and the trinket line is waiting in HazardHarness's budget with a
     * number already reserved. (The ward from food was here briefly, as a
     * tenth; the project owner made it immunity instead, so it is asked with
     * the other two named states and never combines with anything.)
     * If the damage tick assembled the list itself, every new source would mean
     * editing the tick — and a source added to the game and forgotten at the
     * tick is protection a player can see on their screen and cannot feel.
     *
     * The three named immunity states are deliberately <em>not</em> here. They
     * are not shares and do not combine with anything; they are asked before
     * this is, and they short-circuit. Folding them in as a share of 1.0 would
     * make immunity an arithmetic accident again, which is the exact shape
     * {@link #isSealed} exists to avoid.
     */
    public static float[] allShares(LivingEntity entity, ElysiumHazard hazard) {
        float[] worn = wornShares(entity, hazard);
        float room = roomShare(entity, hazard);
        // A doctrine the player has committed to, if any. Gathered as one more
        // partial source rather than applied separately, so it combines with
        // armour and shelter by the same never-quite-one arithmetic as
        // everything else — see ElysiumResistance. A doctrine can make a
        // hazard survivable; only the three named states make it harmless.
        float doctrine = entity instanceof net.minecraft.world.entity.player.Player player
                ? com.elysium.lib.doctrine.ElysiumDoctrines.hazardShare(player)
                : 0.0F;
        int extra = (room > 0.0F ? 1 : 0) + (doctrine > 0.0F ? 1 : 0);
        if (extra == 0) {
            return worn;
        }
        float[] all = java.util.Arrays.copyOf(worn, worn.length + extra);
        int at = worn.length;
        if (room > 0.0F) {
            all[at++] = room;
        }
        if (doctrine > 0.0F) {
            all[at] = doctrine;
        }
        return all;
    }

    /**
     * What the room this entity is standing in removes from one hazard.
     *
     * Asked through {@link com.elysium.lib.room.ElysiumRoomCache} rather than
     * by scanning, because this runs once per hazard per second per player and
     * a scan walks up to five hundred blocks — five times a second for an
     * answer that cannot have changed between the first and the fifth.
     */
    private static float roomShare(LivingEntity entity, ElysiumHazard hazard) {
        if (entity == null) {
            return 0.0F;
        }
        com.elysium.lib.room.ElysiumRoom room =
                com.elysium.lib.room.ElysiumRoomCache.at(
                        entity.level(), entity.blockPosition());
        return com.elysium.lib.room.ElysiumRooms.shareAgainst(room, hazard);
    }

    /**
     * Whether this entity is sealed against a hazard and takes none of it.
     *
     * <h3>Why immunity is a state rather than a number</h3>
     *
     * {@link ElysiumResistance} cannot reach 1.0 by construction — it multiplies
     * what is left and never arrives at nothing. So immunity could only have
     * come from a percentage threshold, and a threshold is the wrong shape for
     * it: a player would cross an invisible line somewhere in the middle of a
     * kit they could not see the edges of, and "am I safe yet" would be a
     * question only the source code could answer.
     *
     * A complete set is legible. Four pieces, all of the hazard's own element,
     * and the world stops hurting you. A player can look at their own armour and
     * know.
     *
     * <h3>What it costs</h3>
     *
     * All four slots, committed to one element. That is the trade, and it is
     * the reason this is not simply a power-up: a player sealed against Thermal
     * is wearing four pieces of Plasma, and the frozen world on the next
     * mountain is going to be exactly as cold as it was before. Immunity to one
     * hazard is a decision to be ordinary in the other four.
     *
     * <h3>Why tier is not part of it</h3>
     *
     * A full set of the right element at tier 0 seals as well as one at tier 12.
     * Tier already governs partial protection, where it does the interesting
     * work of making an incomplete kit gradually better; making it gate
     * immunity as well would mean a player assembles the obvious answer, finds
     * it does not work, and is told the reason is a number on a tooltip.
     *
     * The ring answer never seals. Gear that merely beats the hazard's element
     * gets half shares and nothing else — otherwise the matching element would
     * have no purpose, which is the same failure the half share exists to avoid.
     */
    public static boolean isSealed(LivingEntity entity, ElysiumHazard hazard) {
        if (entity == null) {
            return false;
        }
        List<ElysiumElement> worn = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            ElysiumSocketable gear = ElysiumGear.of(entity.getItemBySlot(slot));
            worn.add(gear == null ? null : gear.getElement());
        }
        return sealedBy(worn, hazard);
    }

/**
     * Whether this position is inside somebody's filed claim.
     *
     * <h3>Why a claim is total shelter</h3>
     *
     * The specification's cheapest answer to four of the five hazards is
     * "sealed shelter", and a claim is the mod's existing word for a place
     * somebody has built and filed. It already costs a permanent Suspicion
     * floor and makes its owner a siege target; making it also the place the
     * weather stops is what those costs buy.
     *
     * It is the second named state, alongside the complete matching set, and
     * the two answer different problems on purpose. The set is what you wear to
     * <em>go</em> into a hostile place; the claim is what you build to
     * <em>live</em> in one. A player who has done neither is exposed, and a
     * player who has done either is not.
     *
     * <h3>Why anybody's claim and not only your own</h3>
     *
     * A pressurised, shielded habitat does not check who is standing in it.
     * Restricting shelter to the owner would mean two players who built one
     * base together are only safe when the right one is logged in, and would
     * make a rescue impossible — you could not carry an unprotected friend into
     * shelter, which is the exact situation shelter is for.
     */
    public static boolean inShelteredClaim(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return false;               // the client does not hold claim data
        }
        return ElysiumClaims.of(server).at(pos) != null;
    }

        /**
     * The seal rule itself, with no world around it.
     *
     * Separated from {@link #isSealed} so it can be executed. The rule is now
     * the single most important decision in the hazard layer — it is what
     * "fully equipped" means — and in its first form it could only be checked
     * by reading it, because it needed a LivingEntity with four armour slots to
     * say anything at all.
     *
     * @param wornElements one entry per humanoid armour slot, in slot order,
     *                     null where the slot is empty or holds something that
     *                     is not Elysium gear
     */
    public static boolean sealedBy(List<ElysiumElement> wornElements,
                                   ElysiumHazard hazard) {
        if (hazard == null || wornElements == null || wornElements.isEmpty()) {
            return false;
        }
        if (!hazard.getElement().isElemental()) {
            return false;               // a hazard nothing aligns to cannot be sealed
        }
        for (ElysiumElement worn : wornElements) {
            if (worn == null || !hazard.getElement().equals(worn)) {
                return false;           // one wrong or empty slot and the seal is open
            }
        }
        return true;
    }
}
