package com.elysium.lib.network;

import com.elysium.lib.client.ElysiumClientState;
import com.elysium.lib.screen.ElysiumCharacterScreen;
import net.minecraft.client.Minecraft;

/**
 * The client half of the character packets, kept in its own class on purpose.
 *
 * Nothing here is referenced from a method reference or a field — only from
 * inside a lambda body in {@link ElysiumNetwork}. That is what keeps a
 * dedicated server from ever loading {@link Minecraft}: the lambda is created
 * during registration on both sides, but its body, and therefore this class,
 * is only resolved when a client-bound packet actually arrives.
 */
public final class ElysiumClientHooks {

    private ElysiumClientHooks() {
    }

    /**
     * Cache first, then show.
     *
     * In that order, so the screen is built from a state the HUD behind it
     * already agrees with. Open-then-cache leaves one frame where the two
     * disagree, which is a frame long enough to notice on a meter that has
     * just moved.
     *
     * <h3>Open, or refresh</h3>
     *
     * If this screen is already the one in front of the player, the new sheet
     * is handed to it rather than used to build a replacement.
     *
     * The server sends this same packet for four different reasons — first
     * join, the Codex, {@code /elysium sheet}, and every point spent — and only
     * the first three are a request to <em>open</em> anything. The fourth is a
     * player who is already looking at the screen and has just clicked a button
     * on it. Building a new screen for them resets every field that screen
     * holds, including which tab they were on, which is why spending a point on
     * the Stats page used to throw them back to the first page.
     *
     * Deciding that here rather than adding a fifth packet keeps the server's
     * side of this exactly as it was: one packet, one meaning — "here is the
     * current sheet, show it". What "show it" costs is the client's business,
     * and the client is the only side that knows whether the screen is already
     * up.
     */
    public static void openCharacterScreen(ElysiumPayloads.OpenCharacter payload) {
        CharacterSheet.Parsed parsed = CharacterSheet.parse(payload.sheet());
        ElysiumClientState.accept(parsed, payload.unspent());

        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof ElysiumCharacterScreen open) {
            open.refresh(parsed, payload.unspent());
            return;
        }
        client.setScreen(new ElysiumCharacterScreen(parsed, payload.unspent()));
    }

    /** The same thing without taking over the screen. See the payload's javadoc. */
    public static void syncCharacter(ElysiumPayloads.SyncCharacter payload) {
        ElysiumClientState.accept(CharacterSheet.parse(payload.sheet()), payload.unspent());
    }
}
