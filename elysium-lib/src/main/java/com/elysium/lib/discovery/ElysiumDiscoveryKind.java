package com.elysium.lib.discovery;

import net.minecraft.network.chat.Component;

/**
 * What kind of thing was found, and what finding it is worth.
 *
 * <h2>Why the reward differs by kind</h2>
 *
 * §17.1 pays these differently on purpose. A plant is proximity and a moment;
 * an anomaly is a special scanner and a journey. Paying the same for both would
 * make the catalogue a checklist to be filled in the nearest meadow, and the
 * dangerous half of the world would never be scanned by anybody.
 *
 * The order here is the order of difficulty, and the rewards rise with it.
 */
public enum ElysiumDiscoveryKind {

    /** A plant. The cheapest and most plentiful thing to catalogue. */
    FLORA("flora", 1, 2),

    /** A creature. Worth more: it moves, and some of it fights back. */
    FAUNA("fauna", 2, 4),

    /** An ore or vein. Worth what walking into a cave is worth. */
    MINERAL("mineral", 2, 5),

    /** Somebody's building. Filing one is reporting on its owner. */
    STRUCTURE("structure", 3, 7),

    /** A rift, a derelict, something that should not be there. */
    ANOMALY("anomaly", 5, 12);

    private final String id;
    private final int favorWhenUnsworn;
    private final int emeraldsWhenFiled;

    ElysiumDiscoveryKind(String id, int favorWhenUnsworn, int emeraldsWhenFiled) {
        this.id = id;
        this.favorWhenUnsworn = favorWhenUnsworn;
        this.emeraldsWhenFiled = emeraldsWhenFiled;
    }

    public String getId() {
        return id;
    }

    /** Favor for handing this to the Unsworn networks. */
    public int getFavorWhenUnsworn() {
        return favorWhenUnsworn;
    }

    /** What the Empire pays for it, in emeralds. */
    public int getEmeraldsWhenFiled() {
        return emeraldsWhenFiled;
    }

    public Component getDisplayName() {
        return Component.translatable("elysium.discovery.kind." + id);
    }
}
