package com.elysium.core.compat;

import com.elysium.lib.item.ElysiumGear;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Turning a piece of gear into the same piece, seen by another mod's system.
 *
 * <h2>The rule that shapes all of this: additive and idempotent</h2>
 *
 * A conversion never takes anything away. An Elysium blade that gains Apotheosis
 * affixes keeps its runes, its reforge rolls and its ascension; an Apotheosis
 * sword that gains Elysium data keeps every affix it was looted with. The two
 * systems were already unaware of each other and already stack — the bridge
 * makes that a thing a player can ask for rather than a thing that happens to
 * them.
 *
 * And everything the bridge writes is <em>derived from what is already on the
 * piece</em>, so running a conversion twice computes the same answer and writes
 * the same values. That is not a detail. The alternative — rolling something
 * fresh each time — turns a smithing table into a slot machine: convert, look,
 * convert back, repeat. Determinism is what stops the feature from quietly
 * becoming the best way to play.
 *
 * <h2>Why this class knows nothing about either mod</h2>
 *
 * Not one import from {@code net.silentchaos512} or {@code dev.shadowsoffire}
 * appears here. Every one of those lives in a bridge class that is only
 * <em>mentioned</em> inside a branch guarded by {@link ModList}, and the JVM
 * does not load a class until something uses it — so on a game with neither mod
 * installed, neither bridge is ever loaded and neither can fail.
 *
 * That sounds like a technicality and it is the entire reason this file exists
 * as a separate class. An earlier version of this repository put Apotheosis
 * imports directly on the affix classes, which were loaded unconditionally; the
 * mod then could not build without Apotheosis and could not run without it
 * either. The guard has to be at a class boundary or it is not a guard.
 */
public final class GearConvergence {

    private GearConvergence() {
    }

    public static final String SILENT_GEAR = "silentgear";
    public static final String APOTHEOSIS = "apotheosis";

    /** What a piece of gear can be turned into, from where it is now. */
    public enum Direction {
        /** Elysium gear becomes the Silent Gear item of the same material. */
        TO_SILENT_GEAR(SILENT_GEAR),
        /** A Silent Gear item becomes the Elysium piece of the same material. */
        FROM_SILENT_GEAR(SILENT_GEAR),
        /** Any gear gains Apotheosis rarity, affixes and sockets. */
        TO_APOTHEOSIS(APOTHEOSIS),
        /** An affixed item gains Elysium gear data. */
        FROM_APOTHEOSIS(APOTHEOSIS);

        private final String modId;

        Direction(String modId) {
            this.modId = modId;
        }

        public String modId() {
            return modId;
        }

        public boolean available() {
            return ModList.get().isLoaded(modId);
        }
    }

    /**
     * What this stack could be converted into with the given template, if
     * anything.
     *
     * Returns null rather than throwing, because this runs on every tick of a
     * player holding items over a smithing table and "no" is the common answer.
     */
    public static Direction directionFor(ItemStack stack, boolean silentTemplate) {
        if (stack.isEmpty()) {
            return null;
        }
        if (silentTemplate) {
            if (!ModList.get().isLoaded(SILENT_GEAR)) {
                return null;
            }
            if (SilentGearBridge.isSilentGear(stack)) {
                return Direction.FROM_SILENT_GEAR;
            }
            return ElysiumGear.isNative(stack) ? Direction.TO_SILENT_GEAR : null;
        }
        if (!ModList.get().isLoaded(APOTHEOSIS)) {
            return null;
        }
        // Either way round it is the same item coming out, so the direction is
        // decided by which system the piece is missing. A piece that has both
        // already is not convertible — saying so is better than handing the
        // player back an identical item and charging them for it.
        boolean elysium = ElysiumGear.is(stack);
        boolean affixed = ApotheosisBridge.hasApotheosisData(stack);
        if (elysium && !affixed) {
            return Direction.TO_APOTHEOSIS;
        }
        if (affixed && !elysium) {
            return Direction.FROM_APOTHEOSIS;
        }
        return null;
    }

    /**
     * Perform the conversion, or return an empty stack.
     *
     * The caller has already decided this is allowed; this is where the work
     * happens. Never mutates the input — a smithing table asks for the result
     * of a hypothetical craft many times before the player takes it.
     */
    public static ItemStack convert(ItemStack stack, Direction direction) {
        if (stack.isEmpty() || direction == null || !direction.available()) {
            return ItemStack.EMPTY;
        }
        return switch (direction) {
            case TO_SILENT_GEAR -> SilentGearBridge.toSilentGear(stack);
            case FROM_SILENT_GEAR -> SilentGearBridge.toElysium(stack);
            case TO_APOTHEOSIS -> ApotheosisBridge.grantApotheosis(stack);
            case FROM_APOTHEOSIS -> ApotheosisBridge.grantElysium(stack);
        };
    }
}
