package com.elysium.space.world;

/**
 * Space stations (Stellaris's small, medium and big stations), as numbers: what
 * each costs, which template it is, and where each player's station stands in
 * High Orbit. Pure Java, checked by the harness.
 *
 * <p>Every player may own one station. Stations stand on a grid of slots far
 * enough apart that the largest never touches a neighbour; a player's slot is
 * fixed when they first build, and a bigger blueprint rebuilds the station in
 * place (each template contains the smaller ones' footprint, from the same
 * corner, so the rebuild covers the old station completely).
 *
 * <p>Credit: Stellaris, (c) Exodus Studio, CC BY-NC-SA 4.0. See CREDITS.md.
 */
public final class StationRules {

    private StationRules() {
    }

    /** Steel and lunite ingots (Stellaris asked for steel and heavy metal; lunite is our desh). */
    public enum Size {
        SMALL("small", 100, 100, 38, 58, 38),
        MEDIUM("medium", 150, 150, 49, 55, 49),
        BIG("big", 200, 200, 55, 62, 54);

        public final String id;
        public final int steel;
        public final int lunite;
        public final int sizeX;
        public final int sizeY;
        public final int sizeZ;

        Size(String id, int steel, int lunite, int sizeX, int sizeY, int sizeZ) {
            this.id = id;
            this.steel = steel;
            this.lunite = lunite;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
        }

        /** The structure template, elysiumspace:space_station_&lt;id&gt;. */
        public String template() {
            return "space_station_" + id;
        }

        public static Size named(String id) {
            for (Size s : values()) {
                if (s.id.equals(id)) {
                    return s;
                }
            }
            return null;
        }
    }

    /** The world stations stand in. */
    public static final String WORLD = "high_orbit";
    /** Blocks between slot corners, and slots per row. */
    public static final int SPACING = 1024;
    public static final int ROW = 32;
    /** Height of a station's lowest block. */
    public static final int FLOOR_Y = 64;

    /** The corner (x, z) of slot {@code index}; the rows start one spacing away from the origin. */
    public static int[] slot(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("slot " + index);
        }
        int x = (index % ROW - ROW / 2) * SPACING;
        int z = (index / ROW + 1) * SPACING;
        return new int[] {x, z};
    }

    /** What building {@code want} does to a player who owns {@code owned} (null: none). */
    public enum Build { NEW, UPGRADE, ALREADY }

    public static Build build(Size owned, Size want) {
        if (owned == null) {
            return Build.NEW;
        }
        return want.ordinal() > owned.ordinal() ? Build.UPGRADE : Build.ALREADY;
    }
}
