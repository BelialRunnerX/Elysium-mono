package com.elysium.colonies.citizen;

import com.elysium.colonies.ElysiumColonies;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * What this module remembers about a town and the people in it.
 *
 * <h2>Why saved data and not attachments</h2>
 *
 * A citizen is an entity, and an entity can carry an attachment — but a colony
 * citizen is not a normal entity. MineColonies keeps the person in its own
 * data object and rebuilds the body from it whenever the chunk loads, whenever
 * they die and are resurrected, and whenever the colony restarts them. An
 * attachment on the body would be a lifetime of practice thrown away by a
 * chunk unload, which is exactly the kind of quiet loss that makes a long
 * save feel unsafe.
 *
 * <p>So this is level data, keyed by the colony's number and the citizen's,
 * both of which MineColonies keeps stable for the life of the person. It sits
 * beside the colony rather than inside it, which also means removing this
 * module leaves the colony untouched.
 */
public final class ColonyMemory extends SavedData {

    /** The file this lives in, beside the colony's own. */
    public static final String FILE = "elysium_colonies";

    /**
     * One person, as this module knows them.
     *
     * @param stress   what their life came to at the last daily reckoning
     * @param wounds   how many untreated injuries they carry
     * @param practice how many pieces of work they have finished, ever —
     *                 counted here rather than read from the job, because a
     *                 job's own count resets when a citizen changes trade and
     *                 a lifetime of practice should not
     */
    public record Life(int stress, int wounds, int practice) {

        public static final Life NEW = new Life(0, 0, 0);

        public static final Codec<Life> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.INT.optionalFieldOf("stress", 0).forGetter(Life::stress),
                        Codec.INT.optionalFieldOf("wounds", 0).forGetter(Life::wounds),
                        Codec.INT.optionalFieldOf("practice", 0)
                                .forGetter(Life::practice)
                ).apply(instance, Life::new));

        public Life withStress(int now) {
            return new Life(Math.max(0, now), wounds, practice);
        }

        public Life withWounds(int now) {
            return new Life(stress, Math.max(0, now), practice);
        }

        /** Another day's work remembered. Never falls. */
        public Life practised(int more) {
            return new Life(stress, wounds, practice + Math.max(0, more));
        }
    }

    /**
     * One town's books.
     *
     * @param development what it has saved towards raising a building
     * @param lastDay     the colony day the last reckoning was made on
     * @param lastUpgrade the colony day it last ordered an upgrade
     * @param bestStress  the worst any of its people has been, ever, so a
     *                    report can say what the place has been through
     */
    public record Ledger(long development, int lastDay, int lastUpgrade,
                         int bestStress, int lastSeen, int lastSuggested,
                         String steward, int foundedOn, int lastProgress,
                         int lastReported, int builtThen, int citizensThen,
                         long treasury) {

        /**
         * A town nobody has reckoned yet.
         *
         * {@code lastSeen} starts negative rather than at zero, and that is
         * load-bearing: it means "nobody has ever been here", which is a
         * different thing from "somebody was here on day zero" and leads to a
         * different answer about who decides where the next building goes.
         */
        public static final Ledger NEW = new Ledger(0L, -1, -1000, 0, -1, -1000,
                com.elysium.colonies.rules.StewardRules.Steward.ATTENDANCE.id(),
                -1, -1, -1000, -1, -1, 0L);

        public static final Codec<Ledger> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.LONG.optionalFieldOf("development", 0L)
                                .forGetter(Ledger::development),
                        Codec.INT.optionalFieldOf("last_day", -1)
                                .forGetter(Ledger::lastDay),
                        Codec.INT.optionalFieldOf("last_upgrade", -1000)
                                .forGetter(Ledger::lastUpgrade),
                        Codec.INT.optionalFieldOf("worst_stress", 0)
                                .forGetter(Ledger::bestStress),
                        Codec.INT.optionalFieldOf("last_seen", -1)
                                .forGetter(Ledger::lastSeen),
                        Codec.INT.optionalFieldOf("last_suggested", -1000)
                                .forGetter(Ledger::lastSuggested),
                        Codec.STRING.optionalFieldOf("steward",
                                        com.elysium.colonies.rules.StewardRules
                                                .Steward.ATTENDANCE.id())
                                .forGetter(Ledger::steward),
                        Codec.INT.optionalFieldOf("founded_on", -1)
                                .forGetter(Ledger::foundedOn),
                        Codec.INT.optionalFieldOf("last_progress", -1)
                                .forGetter(Ledger::lastProgress),
                        Codec.INT.optionalFieldOf("last_reported", -1000)
                                .forGetter(Ledger::lastReported),
                        Codec.INT.optionalFieldOf("built_then", -1)
                                .forGetter(Ledger::builtThen),
                        Codec.INT.optionalFieldOf("citizens_then", -1)
                                .forGetter(Ledger::citizensThen),
                        Codec.LONG.optionalFieldOf("treasury", 0L)
                                .forGetter(Ledger::treasury)
                ).apply(instance, Ledger::new));

        /** Who the owner has said decides where buildings go. */
        public com.elysium.colonies.rules.StewardRules.Steward stewardship() {
            return com.elysium.colonies.rules.StewardRules.Steward.named(steward);
        }

        public Ledger withSteward(com.elysium.colonies.rules.StewardRules.Steward who) {
            return new Ledger(development, lastDay, lastUpgrade, bestStress, lastSeen,
                    lastSuggested, who.id(), foundedOn, lastProgress, lastReported,
                    builtThen, citizensThen, treasury);
        }

        public Ledger seenOn(int day) {
            return new Ledger(development, lastDay, lastUpgrade, bestStress, day,
                    lastSuggested, steward, foundedOn, lastProgress, lastReported,
                    builtThen, citizensThen, treasury);
        }

        public Ledger suggestedOn(int day) {
            return new Ledger(development, lastDay, lastUpgrade, bestStress, lastSeen,
                    day, steward, foundedOn, lastProgress, lastReported, builtThen,
                    citizensThen, treasury);
        }

        /**
         * The first day this module ever looked at the settlement.
         *
         * Stands in for the day it was founded, which the host does not record
         * anywhere this module can read. For a colony that existed before this
         * module did, that is the day the module was installed — which is the
         * right answer anyway: a town that has been running for a year does not
         * need a founding grant, and one that gets four days of help because
         * the mod is new is not a problem worth solving.
         */
        public Ledger foundedOn(int day) {
            return foundedOn >= 0 ? this
                    : new Ledger(development, lastDay, lastUpgrade, bestStress, lastSeen,
                            lastSuggested, steward, day, lastProgress, lastReported,
                            builtThen, citizensThen, treasury);
        }

        public Ledger movedOn(int day, int built, int citizens) {
            return new Ledger(development, lastDay, lastUpgrade, bestStress, lastSeen,
                    lastSuggested, steward, foundedOn, day, lastReported, built,
                    citizens, treasury);
        }

        public Ledger reportedOn(int day) {
            return new Ledger(development, lastDay, lastUpgrade, bestStress, lastSeen,
                    lastSuggested, steward, foundedOn, lastProgress, day, builtThen,
                    citizensThen, treasury);
        }

        /**
         * What the port banked, kept apart from the day's own takings.
         *
         * A treasury is money and development is labour, and folding one into
         * the other would mean a colony could buy its way to a taller
         * building without anybody working — which is the one thing the
         * development bank exists to stop. They are two numbers because they
         * are two things.
         */
        public Ledger withTreasury(long held) {
            return new Ledger(development, lastDay, lastUpgrade, bestStress, lastSeen,
                    lastSuggested, steward, foundedOn, lastProgress, lastReported,
                    builtThen, citizensThen, Math.max(0L, held));
        }

        /** The day's books, keeping everything the day does not decide. */
        public Ledger after(long banked, int day, int upgraded, int worst) {
            return new Ledger(banked, day, upgraded, worst, lastSeen, lastSuggested,
                    steward, foundedOn, lastProgress, lastReported, builtThen,
                    citizensThen, treasury);
        }
    }

    private static final Codec<Map<String, Life>> LIVES =
            Codec.unboundedMap(Codec.STRING, Life.CODEC);
    private static final Codec<Map<String, Ledger>> LEDGERS =
            Codec.unboundedMap(Codec.STRING, Ledger.CODEC);

    private final Map<String, Life> lives = new HashMap<>();
    private final Map<String, Ledger> ledgers = new HashMap<>();

    public static SavedData.Factory<ColonyMemory> factory() {
        return new SavedData.Factory<>(ColonyMemory::new, ColonyMemory::load,
                net.minecraft.util.datafix.DataFixTypes.LEVEL);
    }

    public static ColonyMemory of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), FILE);
    }

    private static String key(int colony, int citizen) {
        return colony + ":" + citizen;
    }

    /** What is remembered about this person. Never null. */
    public Life lifeOf(int colony, int citizen) {
        return lives.getOrDefault(key(colony, citizen), Life.NEW);
    }

    public void remember(int colony, int citizen, Life life) {
        lives.put(key(colony, citizen), life);
        setDirty();
    }

    /** A town's books. Never null. */
    public Ledger ledgerOf(int colony) {
        return ledgers.getOrDefault(String.valueOf(colony), Ledger.NEW);
    }

    public void record(int colony, Ledger ledger) {
        ledgers.put(String.valueOf(colony), ledger);
        setDirty();
    }

    /** Forgets a person who no longer exists, so the file does not grow forever. */
    public void forget(int colony, int citizen) {
        if (lives.remove(key(colony, citizen)) != null) {
            setDirty();
        }
    }

    /** How many people are remembered, for a report or a validator. */
    public int size() {
        return lives.size();
    }

    // ------------------------------------------------------------------

    private static ColonyMemory load(CompoundTag tag, HolderLookup.Provider lookup) {
        ColonyMemory memory = new ColonyMemory();
        LIVES.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("lives"))
                .resultOrPartial(problem -> ElysiumColonies.LOGGER.error(
                        "colony lives are unreadable: {}", problem))
                .ifPresent(memory.lives::putAll);
        LEDGERS.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("ledgers"))
                .resultOrPartial(problem -> ElysiumColonies.LOGGER.error(
                        "colony ledgers are unreadable: {}", problem))
                .ifPresent(memory.ledgers::putAll);
        return memory;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        LIVES.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, lives)
                .result().ifPresent(written ->
                        tag.put("lives", (net.minecraft.nbt.Tag) written));
        LEDGERS.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, ledgers)
                .result().ifPresent(written ->
                        tag.put("ledgers", (net.minecraft.nbt.Tag) written));
        return tag;
    }
}
