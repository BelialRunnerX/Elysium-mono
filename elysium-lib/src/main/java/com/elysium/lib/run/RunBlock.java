package com.elysium.lib.run;

/**
 * A block that is a run. The library asks it what a device is when it has
 * to re-read the run without the block's own methods in hand - after a dye
 * arrives in a block entity's data, or when something other than a player
 * placed the block.
 */
public interface RunBlock {

    /** What this run counts as a device: an energy handler, a fluid handler, a grid node. */
    Runs.DeviceTest runDevice();
}
