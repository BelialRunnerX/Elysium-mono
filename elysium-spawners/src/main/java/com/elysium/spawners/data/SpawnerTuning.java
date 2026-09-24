package com.elysium.spawners.data;

import com.elysium.spawners.rules.SpawnerRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Everything that has been done to one spawner.
 *
 * <h2>Steps rather than values</h2>
 *
 * A tuning holds how many times each axis has been raised, not what the
 * resulting numbers are. That way the arithmetic lives in exactly one place —
 * {@link SpawnerRules} — and a spawner tuned in an old version gets the new
 * curve rather than keeping the old one baked into it. It is also what makes
 * the tooltip honest: "four steps of haste" is a thing a player did, and
 * "waits 66 ticks" is a consequence.
 *
 * <h2>Why it is a component</h2>
 *
 * A spawner that can be picked up has to carry what it was in the item, and a
 * data component is how 1.21 says that: the block entity offers it, the loot
 * table copies it, and placing the block puts it back. No custom NBT handling
 * anywhere, and a command can read and write it like any other component.
 */
public record SpawnerTuning(int delaySteps, int countSteps, int nearbySteps,
                            int rangeSteps, int spreadSteps,
                            boolean ignoresPlayers, boolean ignoresLight,
                            boolean persistent, boolean noAi, boolean silent,
                            boolean redstone, boolean unstable) {

    /** An untouched spawner: vanilla's own numbers and none of the behaviours. */
    public static final SpawnerTuning NONE =
            new SpawnerTuning(0, 0, 0, 0, 0, false, false, false, false, false, false, false);

    /** The five axes that take steps, in the order a screen or a tooltip lists them. */
    public static final List<String> STATS =
            List.of("delay", "count", "nearby", "range", "spread");

    /** The six behaviours, which are on or off and never partly on. */
    public static final List<String> FLAGS =
            List.of("ignores_players", "ignores_light", "persistent", "no_ai",
                    "silent", "redstone");

    public static final Codec<SpawnerTuning> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("delay", 0).forGetter(SpawnerTuning::delaySteps),
                    Codec.INT.optionalFieldOf("count", 0).forGetter(SpawnerTuning::countSteps),
                    Codec.INT.optionalFieldOf("nearby", 0).forGetter(SpawnerTuning::nearbySteps),
                    Codec.INT.optionalFieldOf("range", 0).forGetter(SpawnerTuning::rangeSteps),
                    Codec.INT.optionalFieldOf("spread", 0).forGetter(SpawnerTuning::spreadSteps),
                    Codec.BOOL.optionalFieldOf("ignores_players", false)
                            .forGetter(SpawnerTuning::ignoresPlayers),
                    Codec.BOOL.optionalFieldOf("ignores_light", false)
                            .forGetter(SpawnerTuning::ignoresLight),
                    Codec.BOOL.optionalFieldOf("persistent", false)
                            .forGetter(SpawnerTuning::persistent),
                    Codec.BOOL.optionalFieldOf("no_ai", false).forGetter(SpawnerTuning::noAi),
                    Codec.BOOL.optionalFieldOf("silent", false).forGetter(SpawnerTuning::silent),
                    Codec.BOOL.optionalFieldOf("redstone", false)
                            .forGetter(SpawnerTuning::redstone),
                    Codec.BOOL.optionalFieldOf("unstable", false)
                            .forGetter(SpawnerTuning::unstable)
            ).apply(instance, SpawnerTuning::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnerTuning> STREAM_CODEC =
            StreamCodec.of(SpawnerTuning::write, SpawnerTuning::read);

    private static void write(RegistryFriendlyByteBuf buf, SpawnerTuning tuning) {
        for (String stat : STATS) {
            ByteBufCodecs.VAR_INT.encode(buf, tuning.steps(stat));
        }
        for (String flag : FLAGS) {
            ByteBufCodecs.BOOL.encode(buf, tuning.flag(flag));
        }
        ByteBufCodecs.BOOL.encode(buf, tuning.unstable());
    }

    private static SpawnerTuning read(RegistryFriendlyByteBuf buf) {
        SpawnerTuning tuning = NONE;
        for (String stat : STATS) {
            tuning = tuning.withSteps(stat, ByteBufCodecs.VAR_INT.decode(buf));
        }
        for (String flag : FLAGS) {
            tuning = tuning.withFlag(flag, ByteBufCodecs.BOOL.decode(buf));
        }
        return tuning.withUnstable(ByteBufCodecs.BOOL.decode(buf));
    }

    // ------------------------------------------------------------------

    /** How many steps one axis has taken, or 0 for a name that is not an axis. */
    public int steps(String stat) {
        return switch (stat) {
            case "delay" -> delaySteps;
            case "count" -> countSteps;
            case "nearby" -> nearbySteps;
            case "range" -> rangeSteps;
            case "spread" -> spreadSteps;
            default -> 0;
        };
    }

    public SpawnerTuning withSteps(String stat, int steps) {
        int held = Math.max(0, steps);
        return switch (stat) {
            case "delay" -> new SpawnerTuning(held, countSteps, nearbySteps, rangeSteps,
                    spreadSteps, ignoresPlayers, ignoresLight, persistent, noAi,
                    silent, redstone, unstable);
            case "count" -> new SpawnerTuning(delaySteps, held, nearbySteps, rangeSteps,
                    spreadSteps, ignoresPlayers, ignoresLight, persistent, noAi,
                    silent, redstone, unstable);
            case "nearby" -> new SpawnerTuning(delaySteps, countSteps, held, rangeSteps,
                    spreadSteps, ignoresPlayers, ignoresLight, persistent, noAi,
                    silent, redstone, unstable);
            case "range" -> new SpawnerTuning(delaySteps, countSteps, nearbySteps, held,
                    spreadSteps, ignoresPlayers, ignoresLight, persistent, noAi,
                    silent, redstone, unstable);
            case "spread" -> new SpawnerTuning(delaySteps, countSteps, nearbySteps, rangeSteps,
                    held, ignoresPlayers, ignoresLight, persistent, noAi,
                    silent, redstone, unstable);
            default -> this;
        };
    }

    /** One more step on an axis. */
    public SpawnerTuning stepped(String stat) {
        return withSteps(stat, steps(stat) + 1);
    }

    public boolean flag(String name) {
        return switch (name) {
            case "ignores_players" -> ignoresPlayers;
            case "ignores_light" -> ignoresLight;
            case "persistent" -> persistent;
            case "no_ai" -> noAi;
            case "silent" -> silent;
            case "redstone" -> redstone;
            default -> false;
        };
    }

    public SpawnerTuning withFlag(String name, boolean value) {
        return switch (name) {
            case "ignores_players" -> new SpawnerTuning(delaySteps, countSteps, nearbySteps,
                    rangeSteps, spreadSteps, value, ignoresLight, persistent, noAi,
                    silent, redstone, unstable);
            case "ignores_light" -> new SpawnerTuning(delaySteps, countSteps, nearbySteps,
                    rangeSteps, spreadSteps, ignoresPlayers, value, persistent, noAi,
                    silent, redstone, unstable);
            case "persistent" -> new SpawnerTuning(delaySteps, countSteps, nearbySteps,
                    rangeSteps, spreadSteps, ignoresPlayers, ignoresLight, value, noAi,
                    silent, redstone, unstable);
            case "no_ai" -> new SpawnerTuning(delaySteps, countSteps, nearbySteps, rangeSteps,
                    spreadSteps, ignoresPlayers, ignoresLight, persistent, value,
                    silent, redstone, unstable);
            case "silent" -> new SpawnerTuning(delaySteps, countSteps, nearbySteps, rangeSteps,
                    spreadSteps, ignoresPlayers, ignoresLight, persistent, noAi,
                    value, redstone, unstable);
            case "redstone" -> new SpawnerTuning(delaySteps, countSteps, nearbySteps, rangeSteps,
                    spreadSteps, ignoresPlayers, ignoresLight, persistent, noAi,
                    silent, value, unstable);
            default -> this;
        };
    }

    public SpawnerTuning withUnstable(boolean value) {
        return new SpawnerTuning(delaySteps, countSteps, nearbySteps, rangeSteps, spreadSteps,
                ignoresPlayers, ignoresLight, persistent, noAi, silent, redstone, value);
    }

    /** Whether anything has been done to this spawner at all. */
    public boolean untouched() {
        return equals(NONE);
    }

    /** Everything spent on it so far, which is what a tooltip should say. */
    public int spent() {
        int total = 0;
        for (String stat : STATS) {
            total += SpawnerRules.spentOn(steps(stat));
        }
        return total;
    }
}
