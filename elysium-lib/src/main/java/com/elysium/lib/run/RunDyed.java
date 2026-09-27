package com.elysium.lib.run;

/**
 * A run's block entity remembers its dye. The name is the game's dye name
 * ({@code "red"}) or {@link RunRules#UNDYED}; the block entity saves it,
 * syncs it, and asks its level to redraw when it changes.
 */
public interface RunDyed {

    String runDye();

    void setRunDye(String dye);
}
