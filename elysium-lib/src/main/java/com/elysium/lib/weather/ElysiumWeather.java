package com.elysium.lib.weather;

import com.elysium.lib.hazard.ElysiumHazard;
import com.elysium.lib.hazard.ElysiumHazardField;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Weather that does something.
 *
 * <h2>Why this is in the library</h2>
 *
 * Because a storm is a hazard that comes and goes, and the hazard pipeline is
 * already here: intensity, resistance, armour sets, wards, rooms, claims. A
 * weather system of its own would be a second pipeline that had to agree with
 * the first about what being indoors means. So an ash storm is registered as a
 * hazard source like a planet's atmosphere is, and everything that already
 * answers a hazard answers it — most importantly the shelter rules, which is
 * why <em>storm shutters</em> needs no code: a sealed room is the answer,
 * because a sealed room is already the answer.
 *
 * <h2>What a content module declares</h2>
 *
 * Which kinds of weather a world has, how stormy it is, and what each kind
 * does. Nothing here knows what Cinderveil is.
 *
 * <h2>What it costs</h2>
 *
 * One hash per query and no storage. The schedule is arithmetic on the day
 * ({@link ElysiumWeatherRules}), so this answers "what is the weather" without
 * a file, without a tick, and identically on the client and the server — which
 * is what lets a client draw a storm it was never told about.
 */
public final class ElysiumWeather {

    private ElysiumWeather() {
    }

    /**
     * One kind of weather.
     *
     * @param id      what it is called, for the message and the translation key
     * @param hazard  the hazard it raises, or null for weather that is only weather
     * @param peak    how much of that hazard it adds at full strength, 0 to 1
     * @param machines how much of a machine's output it takes at full strength
     */
    public record Kind(String id, ElysiumHazard hazard, float peak, float machines) {

        public Kind {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("weather needs a name");
            }
            peak = Math.max(0.0F, Math.min(1.0F, peak));
            machines = Math.max(0.0F, Math.min(1.0F, machines));
        }

        /** The key a message about this weather is written under. */
        public String messageKey() {
            return "elysium.weather." + id;
        }
    }

    /**
     * What a world's sky does.
     *
     * @param kinds      the kinds it gets, in a stable order
     * @param storminess how many watches in eight it storms in
     */
    public record Climate(List<Kind> kinds, int storminess) {

        public Climate {
            kinds = List.copyOf(kinds);
            storminess = Math.max(0, Math.min(ElysiumWeatherRules.MOST_STORMY, storminess));
        }
    }

    /** One spell of weather, as it stands right now. */
    public record Spell(Kind kind, float strength, int secondsLeft) {

        public static final Spell CLEAR = new Spell(null, 0.0F, 0);

        public boolean blowing() {
            return kind != null && strength > 0.0F;
        }
    }

    private static final Map<ResourceLocation, Climate> CLIMATES = new LinkedHashMap<>();

    /**
     * Declares what a world's weather is.
     *
     * Called during setup by whichever module owns the world. Declaring a
     * world twice replaces the first declaration rather than adding to it:
     * two modules disagreeing about a sky is a pack problem, and picking one
     * is better than storming twice as often as either intended.
     */
    public static void declare(ResourceLocation dimension, Climate climate) {
        if (dimension == null || climate == null || climate.kinds().isEmpty()) {
            return;
        }
        CLIMATES.put(dimension, climate);
        install();
    }

    /** What a world's sky can do, or null for a world nobody declared. */
    public static Climate climateOf(ResourceLocation dimension) {
        return CLIMATES.get(dimension);
    }

    /** How many worlds have weather, for a report or a validator. */
    public static int size() {
        return CLIMATES.size();
    }

    /**
     * The weather in this world right now.
     *
     * Derived from the world's own day and time, so the answer is the same
     * wherever it is asked and whoever asks: the server ticking hazards, a
     * machine deciding its output, and a client drawing particles all agree
     * without a packet passing between them.
     */
    public static Spell now(Level level) {
        if (level == null) {
            return Spell.CLEAR;
        }
        Climate climate = CLIMATES.get(level.dimension().location());
        if (climate == null) {
            return Spell.CLEAR;
        }
        long time = level.getDayTime();
        long day = time / 24000L;
        int watch = ElysiumWeatherRules.watchOf(time);
        int index = ElysiumWeatherRules.kindAt(
                level.dimension().location().toString().hashCode(), day, watch,
                climate.kinds().size(), climate.storminess());
        if (index < 0) {
            return Spell.CLEAR;
        }
        float strength = ElysiumWeatherRules.strength(
                ElysiumWeatherRules.intoWatch(time));
        if (strength <= 0.0F) {
            return Spell.CLEAR;
        }
        return new Spell(climate.kinds().get(index), strength,
                ElysiumWeatherRules.remaining(time));
    }

    /**
     * What a machine gets out of the sky right now, as a share of its best.
     *
     * Read by anything whose output depends on the weather — a solar panel
     * under an ash cloud, a wind turbine in a whiteout. Never zero; see
     * {@link ElysiumWeatherRules#LEAST_OUTPUT}.
     */
    public static float machineShare(Level level) {
        Spell spell = now(level);
        if (!spell.blowing()) {
            return 1.0F;
        }
        return ElysiumWeatherRules.machineShare(spell.kind().machines(),
                spell.strength());
    }

    /** Every world with weather, for a command or a report. */
    public static List<ResourceLocation> worlds() {
        return new ArrayList<>(CLIMATES.keySet());
    }

    // ------------------------------------------------------------------

    private static volatile boolean installed;

    /**
     * Registers weather as a hazard source, once.
     *
     * From the first declaration rather than from a mod constructor, so a pack
     * with no weather never adds a source at all — and so the library does not
     * need a setup hook of its own for something no module may use.
     */
    private static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        ElysiumHazardField.registerSource("elysiumlib:weather",
                ElysiumWeather::intensity);
    }

    /**
     * How much of a hazard the weather is adding here.
     *
     * The position is ignored on purpose: weather is a property of a sky, and
     * a storm that was worse in one chunk than the next would be a storm made
     * of chunks. What makes it not apply is being indoors, and that is the
     * hazard pipeline's own answer rather than this source's.
     */
    private static float intensity(Level level, BlockPos pos, ElysiumHazard hazard) {
        Spell spell = now(level);
        if (!spell.blowing() || spell.kind().hazard() != hazard) {
            return 0.0F;
        }
        return ElysiumWeatherRules.hazardFrom(spell.kind().peak(), spell.strength());
    }
}
