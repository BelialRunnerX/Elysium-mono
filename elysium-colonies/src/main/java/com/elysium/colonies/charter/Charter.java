package com.elysium.colonies.charter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/**
 * What a world offers a town, and what it charges for standing there.
 *
 * <h2>Why this is data</h2>
 *
 * Because the list of worlds is not this module's to know. Elysium ships five
 * and vanilla three, a pack may add twenty, and a module that hard-coded the
 * ones it had heard of would be a module that silently treats every new world
 * as a meadow. So a charter is a file, one per dimension, and a dimension with
 * no file gets the default one — which is a meadow, and says so.
 *
 * <h2>The four things it says</h2>
 *
 * <ul>
 *   <li><b>natives</b> — the material families the world itself produces. What
 *       a town here can work without importing.</li>
 *   <li><b>hazard</b> — the summed intensity of what is trying to kill the
 *       people. Cinderveil's heat and pressure together; Frostfall's cold and
 *       radiation.</li>
 *   <li><b>air</b> — whether it can be breathed, and if not, whether there is
 *       any at all.</li>
 *   <li><b>reach</b> — how many worlds out from home it is. Distance is what
 *       makes the other three matter, because a town next door can import its
 *       way out of a bad charter and one on the far side cannot.</li>
 * </ul>
 */
public record Charter(ResourceLocation dimension, List<String> natives,
                      float hazard, Air air, int reach, String style) {

    /** What there is to breathe. */
    public enum Air {
        /** Ordinary air. No burden. */
        BREATHABLE("breathable"),
        /** Thin or toxic: survivable, unpleasant, and a standing burden. */
        POOR("poor"),
        /** None at all. */
        NONE("none");

        private final String id;

        Air(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Air named(String id) {
            for (Air air : values()) {
                if (air.id.equalsIgnoreCase(id)) {
                    return air;
                }
            }
            return BREATHABLE;
        }

        public static final Codec<Air> CODEC =
                Codec.STRING.xmap(Air::named, Air::id);
    }

    public static final Codec<Charter> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("dimension")
                            .forGetter(Charter::dimension),
                    Codec.STRING.listOf().optionalFieldOf("natives", List.of())
                            .forGetter(Charter::natives),
                    Codec.FLOAT.optionalFieldOf("hazard", 0.0F)
                            .forGetter(Charter::hazard),
                    Air.CODEC.optionalFieldOf("air", Air.BREATHABLE)
                            .forGetter(Charter::air),
                    Codec.INT.optionalFieldOf("reach", 0)
                            .forGetter(Charter::reach),
                    Codec.STRING.optionalFieldOf("style", "")
                            .forGetter(Charter::style)
            ).apply(instance, Charter::new));

    /**
     * The charter a world nobody has written a file for gets.
     *
     * A meadow: nothing local, nothing dangerous, next door. Deliberately the
     * neutral answer rather than a generous or a harsh one, so a pack that
     * adds a world and forgets the file gets a colony that behaves exactly as
     * it did before this module existed.
     */
    public static Charter unwritten(ResourceLocation dimension) {
        return new Charter(dimension, List.of(), 0.0F, Air.BREATHABLE, 0, "");
    }

    /** Whether this world has a style of its own to build in. */
    public boolean hasStyle() {
        return !style.isEmpty();
    }

    /** How many of the families a settlement actually consumes are local here. */
    public int localCount(Set<String> needs) {
        int found = 0;
        for (String family : natives) {
            if (needs.contains(family)) {
                found++;
            }
        }
        return found;
    }

    /** Whether this is the neutral charter, for a report that wants to say so. */
    public boolean blank() {
        return natives.isEmpty() && hazard <= 0.0F && air == Air.BREATHABLE
                && reach <= 0;
    }
}
