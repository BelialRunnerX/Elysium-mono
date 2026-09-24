package com.elysium.lib.research;

/**
 * The six things a player can come to understand.
 *
 * <h2>Why these are not one experience bar</h2>
 *
 * The mod already has a level, and levelling already gives points that buy
 * stats. A second bar filled the same way would be the same bar twice. What
 * makes a research track worth having is that it is filled by <em>doing the
 * particular thing</em>: a player who has spent two hundred hours underground
 * is a geologist and is not thereby a xenobiologist, and the game should be
 * able to tell the difference and say so.
 *
 * That is also what makes the tracks a description of a playthrough rather than
 * a shopping list. Nobody grinds Rift Science; they go through rifts, and one
 * day the scanner starts telling them things about rifts.
 *
 * <h2>Why they are declared here and not derived</h2>
 *
 * A field's identity is a string, fixed forever, because it is a key in a save
 * file. The ordinal is not used for anything — unlike {@code PowerClass}, where
 * the order is the meaning, these six are peers and reordering them must be
 * harmless. {@link ElysiumResearchRank} is where the ordering lives.
 *
 * <h2>No Minecraft here</h2>
 *
 * Six constants and a string each, so the harness can execute the whole
 * progression — thresholds, deed values, unlock gates — without a world.
 */
public enum ElysiumResearchField {

    /** Stone, ore, veins, what is under things. Advanced by mining and by scanning minerals. */
    GEOLOGY("geology"),

    /** Living things and what they are made of. Advanced by scanning flora and fauna. */
    XENOBIOLOGY("xenobiology"),

    /** Hazards, weather, what a place does to a person. Advanced by surviving it. */
    ENVIRONMENTAL("environmental"),

    /** The Empire's machinery, paperwork and habits. Advanced by reading its structures. */
    IMPERIAL_SYSTEMS("imperial_systems"),

    /** Rifts, anomalies and whatever is on the other side. Advanced by going there. */
    RIFT_SCIENCE("rift_science"),

    /** Building things that work. Advanced by running machinery and working benches. */
    ENGINEERING("engineering");

    private final String id;

    ElysiumResearchField(String id) {
        this.id = id;
    }

    /** Stable forever: this is a key in a save file. */
    public String id() {
        return id;
    }

    public String translationKey() {
        return "elysium.research.field." + id;
    }

    public String blurbKey() {
        return "elysium.research.field." + id + ".blurb";
    }

    /** By id, or null. Null rather than throwing: an unknown id in a save is
     *  a mod that was removed, and losing one field is better than losing the
     *  world. */
    public static ElysiumResearchField byId(String id) {
        for (ElysiumResearchField field : values()) {
            if (field.id.equals(id)) {
                return field;
            }
        }
        return null;
    }
}
