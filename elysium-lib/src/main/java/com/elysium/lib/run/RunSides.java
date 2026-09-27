package com.elysium.lib.run;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** The six link properties every run block carries, one per face. */
public final class RunSides {

    private RunSides() {
    }

    public static final EnumProperty<RunLink> NORTH = EnumProperty.create("north", RunLink.class);
    public static final EnumProperty<RunLink> EAST = EnumProperty.create("east", RunLink.class);
    public static final EnumProperty<RunLink> SOUTH = EnumProperty.create("south", RunLink.class);
    public static final EnumProperty<RunLink> WEST = EnumProperty.create("west", RunLink.class);
    public static final EnumProperty<RunLink> UP = EnumProperty.create("up", RunLink.class);
    public static final EnumProperty<RunLink> DOWN = EnumProperty.create("down", RunLink.class);

    public static final Map<Direction, EnumProperty<RunLink>> BY_SIDE = new EnumMap<>(Direction.class);

    static {
        BY_SIDE.put(Direction.NORTH, NORTH);
        BY_SIDE.put(Direction.EAST, EAST);
        BY_SIDE.put(Direction.SOUTH, SOUTH);
        BY_SIDE.put(Direction.WEST, WEST);
        BY_SIDE.put(Direction.UP, UP);
        BY_SIDE.put(Direction.DOWN, DOWN);
    }
}
