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
     * Cache first, then open.
     *
     * In that order, so the screen is built from a state the HUD behind it
     * already agrees with. Open-then-cache leaves one frame where the two
     * disagree, which is a frame long enough to notice on a meter that has
     * just moved.
     */
    public static void openCharacterScreen(ElysiumPayloads.OpenCharacter payload) {
        CharacterSheet.Parsed parsed = CharacterSheet.parse(payload.sheet());
        ElysiumClientState.accept(parsed, payload.unspent());
        Minecraft.getInstance().setScreen(new ElysiumCharacterScreen(parsed, payload.unspent()));
    }

    /** The same thing without taking over the screen. See the payload's javadoc. */
    public static void syncCharacter(ElysiumPayloads.SyncCharacter payload) {
        ElysiumClientState.accept(CharacterSheet.parse(payload.sheet()), payload.unspent());
    }
}
