package com.elysium.lib.run;

import net.minecraft.util.StringRepresentable;

/**
 * What one face of a run touches. {@code RUN} draws an arm; {@code DEVICE}
 * draws an arm and the plate; {@code NONE} draws nothing.
 */
public enum RunLink implements StringRepresentable {
    NONE("none"),
    RUN("run"),
    DEVICE("device");

    private final String name;

    RunLink(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Whether an arm is drawn towards this face. */
    public boolean reaches() {
        return this != NONE;
    }
}
