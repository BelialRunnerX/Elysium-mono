package com.elysium.lib.client;

import com.elysium.lib.network.CharacterSheet;

/**
 * The last sheet the server sent, held for anything on the client that has to
 * draw without asking.
 *
 * <h2>Why this exists at all</h2>
 *
 * Almost everything the interface wants to show — level, XP, Favor, Suspicion,
 * the twelve totals — lives in data attachments, and a NeoForge attachment is
 * server-side unless it is explicitly synced. A client asking the local player
 * for its own Favor gets the attachment's default, zero, with no error and no
 * hint that the number is fiction. That is the failure mode this class exists
 * to prevent: a HUD is read at a glance and believed, so it must never show a
 * value that was made up locally.
 *
 * <h2>Why it is allowed to be empty</h2>
 *
 * {@link #hasSheet()} is false until the first packet arrives, and the HUD
 * draws nothing at all in that state rather than drawing zeroes. A player who
 * has just joined and a player with no Favor look identical to a number; they
 * do not look identical to a HUD that is simply absent for a second.
 *
 * <h2>Threading</h2>
 *
 * Written from the network thread's {@code enqueueWork} — that is, on the
 * client thread — and read from the render thread, which is the same thread.
 * The field is {@code volatile} anyway, because that assumption is exactly the
 * sort of thing that is true until someone adds an off-thread caller.
 */
public final class ElysiumClientState {

    private ElysiumClientState() {
    }

    private static volatile CharacterSheet.Parsed sheet = null;
    private static volatile int unspent = 0;

    public static void accept(CharacterSheet.Parsed parsed, int free) {
        sheet = parsed;
        unspent = Math.max(0, free);
    }

    /** Whether the server has told us anything yet. */
    public static boolean hasSheet() {
        return sheet != null;
    }

    /** The last sheet, or an empty one — never null, so callers need no branch. */
    public static CharacterSheet.Parsed sheet() {
        CharacterSheet.Parsed current = sheet;
        return current == null ? CharacterSheet.empty() : current;
    }

    public static int unspent() {
        return unspent;
    }

    /**
     * Forgotten on disconnect.
     *
     * Otherwise a player who leaves one server and joins another sees the
     * first world's character on the HUD until the new server's first packet
     * lands — briefly, but wrongly, and with the wrong name on it.
     */
    public static void clear() {
        sheet = null;
        unspent = 0;
    }
}
