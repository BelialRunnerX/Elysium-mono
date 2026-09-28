import com.elysium.lib.sound.ElysiumSoundRules;

import java.util.List;

/**
 * Runs the sound table on the JDK alone and fails on any fault it reports.
 *
 *   javac -cp "/tmp/elysium-classes/elysium-lib" -d /tmp/sh tools/SoundHarness.java
 *   java  -cp "/tmp/elysium-classes/elysium-lib:/tmp/sh" SoundHarness
 *
 * What it proves: every voice, hum and click has a name a sound event can
 * carry, a timbre or kind the synthesiser knows, a pitch a speaker can carry
 * and a player can hear as a creature, a length that is a call and not a song,
 * and its own seed. What it cannot see: whether the clips were rendered and
 * registered; validate.py holds the files, the JSON and the registry to this
 * table.
 */
public final class SoundHarness {

    private SoundHarness() {
    }

    public static void main(String[] args) {
        List<String> faults = ElysiumSoundRules.faults();
        for (String fault : faults) {
            System.out.println("  FAIL: " + fault);
        }
        // The table cannot be empty in any of its three parts: a pack with a
        // sound table and no voices is the silent pack with extra steps.
        if (ElysiumSoundRules.VOICES.size() < 20) {
            System.out.println("  FAIL: " + ElysiumSoundRules.VOICES.size() + " voice(s); the rosters need twenty and more");
            faults = List.of("too few voices");
        }
        if (ElysiumSoundRules.HUMS.size() < 6) {
            System.out.println("  FAIL: " + ElysiumSoundRules.HUMS.size() + " hum(s); the engine classes need six and more");
            faults = List.of("too few hums");
        }
        if (ElysiumSoundRules.CLICKS.isEmpty()) {
            System.out.println("  FAIL: no clicks");
            faults = List.of("no clicks");
        }
        System.out.println("sound table: " + ElysiumSoundRules.VOICES.size() + " voice(s) x "
                + ElysiumSoundRules.CALLS.size() + " call(s), " + ElysiumSoundRules.HUMS.size()
                + " hum(s), " + ElysiumSoundRules.CLICKS.size() + " click(s), "
                + (faults.isEmpty() ? "no faults" : faults.size() + " fault(s)"));
        if (!faults.isEmpty()) {
            System.exit(1);
        }
    }
}
