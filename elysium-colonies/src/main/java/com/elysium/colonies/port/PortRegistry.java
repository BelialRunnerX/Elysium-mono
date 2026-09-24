package com.elysium.colonies.port;

import com.elysium.colonies.ElysiumColonies;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every spaceport in the game, in one file.
 *
 * <h2>Why one file and not one per world</h2>
 *
 * Because the whole claim of a space elevator is that what leaves it is above
 * the world it left. A port on Cinderveil that could only deal with other
 * ports on Cinderveil would be a warehouse with a good view. Freight between
 * two colonies is the reason to build a second port at all, and a registry
 * split per dimension could not answer the only question freight asks:
 * <em>who, anywhere, has this?</em>
 *
 * <p>So it lives on the overworld's saved data, which every server has exactly
 * one of and which loads before any other world is asked for. The same shape
 * the waygate network uses, for the same reason.
 *
 * <h2>One port per colony</h2>
 *
 * Keyed by world and colony, so a settlement has one spaceport however many
 * anchors somebody lays down; placing another moves the port rather than
 * adding one. Two ports in one town would be two answers to "what did this
 * colony ship today", and the second one would exist only to double a number.
 */
public final class PortRegistry extends SavedData {

    /** The file this lives in, beside the colony memory rather than inside it. */
    public static final String FILE = "elysium_spaceports";

    /**
     * One colony's port, as it was last seen standing.
     *
     * @param dimension the world the anchor is in
     * @param colony    whose port it is
     * @param anchor    the block a player talks to
     * @param tether    how tall the column was when it was last read
     * @param working   whether it could ship anything when it was last read
     * @param readOn    the colony day that reading was taken
     * @param legs      how many of the flare's legs were standing then
     */
    public record Berth(ResourceLocation dimension, int colony, BlockPos anchor,
                        int tether, boolean working, int readOn, int legs) {

        public static final Codec<Berth> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        ResourceLocation.CODEC.fieldOf("dimension")
                                .forGetter(Berth::dimension),
                        Codec.INT.fieldOf("colony").forGetter(Berth::colony),
                        BlockPos.CODEC.fieldOf("anchor").forGetter(Berth::anchor),
                        Codec.INT.optionalFieldOf("tether", 0).forGetter(Berth::tether),
                        Codec.BOOL.optionalFieldOf("working", false)
                                .forGetter(Berth::working),
                        Codec.INT.optionalFieldOf("read_on", -1)
                                .forGetter(Berth::readOn),
                        Codec.INT.optionalFieldOf("legs", 0).forGetter(Berth::legs)
                ).apply(instance, Berth::new));

        /** The same berth, as the world says it stands today. */
        public Berth read(SpaceElevator.Reading reading, int day) {
            return new Berth(dimension, colony, anchor, reading.tether(),
                    reading.working(), day, reading.legs());
        }

        public ResourceLocation world() {
            return dimension;
        }
    }

    private static final Codec<Map<String, Berth>> BERTHS =
            Codec.unboundedMap(Codec.STRING, Berth.CODEC);

    private final Map<String, Berth> berths = new HashMap<>();

    public static SavedData.Factory<PortRegistry> factory() {
        return new SavedData.Factory<>(PortRegistry::new, PortRegistry::load,
                net.minecraft.util.datafix.DataFixTypes.LEVEL);
    }

    /**
     * The one registry, wherever it is asked for.
     *
     * Always the overworld's copy. Asking the level a port happens to be in
     * would give a Cinderveil port its own private registry, and the first
     * freight run between two worlds would find nothing on the other side.
     */
    public static PortRegistry of(MinecraftServer server) {
        ServerLevel home = server.getLevel(Level.OVERWORLD);
        if (home == null) {
            home = server.overworld();
        }
        return home.getDataStorage().computeIfAbsent(factory(), FILE);
    }

    private static String key(ResourceLocation dimension, int colony) {
        return dimension + "/" + colony;
    }

    /** This colony's port, or null. */
    public Berth berth(ResourceLocation dimension, int colony) {
        return berths.get(key(dimension, colony));
    }

    /** Every port anywhere, in a stable order. */
    public List<Berth> all() {
        List<Berth> out = new ArrayList<>(berths.values());
        out.sort((one, two) -> key(one.dimension(), one.colony())
                .compareTo(key(two.dimension(), two.colony())));
        return out;
    }

    /** Records a port, replacing whatever this colony had before. */
    public void record(Berth berth) {
        berths.put(key(berth.dimension(), berth.colony()), berth);
        setDirty();
    }

    /** Forgets this colony's port. */
    public void forget(ResourceLocation dimension, int colony) {
        if (berths.remove(key(dimension, colony)) != null) {
            setDirty();
        }
    }

    /**
     * Forgets whatever port stood on this block, whoever owned it.
     *
     * The question a broken anchor has to ask: the block that was removed
     * knows where it was and not whose colony it was in, and by the time it is
     * asking, the colony may not be there either.
     */
    public boolean forgetAt(ResourceLocation dimension, BlockPos anchor) {
        String found = null;
        for (Map.Entry<String, Berth> entry : berths.entrySet()) {
            Berth berth = entry.getValue();
            if (berth.dimension().equals(dimension) && berth.anchor().equals(anchor)) {
                found = entry.getKey();
                break;
            }
        }
        if (found == null) {
            return false;
        }
        berths.remove(found);
        setDirty();
        return true;
    }

    /** How many ports there are, for a report or a validator. */
    public int size() {
        return berths.size();
    }

    // ------------------------------------------------------------------

    private static PortRegistry load(CompoundTag tag, HolderLookup.Provider lookup) {
        PortRegistry registry = new PortRegistry();
        BERTHS.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("berths"))
                .resultOrPartial(problem -> ElysiumColonies.LOGGER.error(
                        "spaceports are unreadable: {}", problem))
                .ifPresent(registry.berths::putAll);
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        BERTHS.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, berths)
                .result().ifPresent(written ->
                        tag.put("berths", (net.minecraft.nbt.Tag) written));
        return tag;
    }
}
