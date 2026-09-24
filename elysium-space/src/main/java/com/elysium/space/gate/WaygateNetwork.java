package com.elysium.space.gate;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.world.GateRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every waygate anybody has finished, in one place.
 *
 * <h2>Why one file and not one per world</h2>
 *
 * Because the question a gate asks is "where can I go", and the answer spans
 * dimensions by definition. Level data is per-dimension, so this lives on the
 * overworld and every gate in the game reads and writes that one copy —
 * whichever world it is standing in.
 *
 * <p>That also means the network survives a dimension being unloaded, which it
 * must: the whole point of a gate on the Pale Moon is that it is reachable from
 * the overworld while nobody is on the Pale Moon.
 */
public final class WaygateNetwork extends SavedData {

    /** The file this lives in, beside the world's own. */
    public static final String FILE = "elysium_waygates";

    /**
     * One gate.
     *
     * @param dimension the world it stands in
     * @param anchor    the anchor block's position, which is also its address
     * @param name      what its builder called it
     * @param facing    whether the ring lies across X or across Z
     */
    public record Gate(ResourceLocation dimension, BlockPos anchor, String name,
                       boolean acrossX) {

        public static final Codec<Gate> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        ResourceLocation.CODEC.fieldOf("dimension")
                                .forGetter(Gate::dimension),
                        BlockPos.CODEC.fieldOf("anchor").forGetter(Gate::anchor),
                        Codec.STRING.optionalFieldOf("name", "").forGetter(Gate::name),
                        Codec.BOOL.optionalFieldOf("across_x", true)
                                .forGetter(Gate::acrossX)
                ).apply(instance, Gate::new));

        /** The address a gate is known by: its world and its anchor. */
        public String key() {
            return dimension + "@" + anchor.getX() + "," + anchor.getY() + ","
                    + anchor.getZ();
        }

        /** What to show a traveller, falling back to where it is. */
        public String shown() {
            return GateRules.nameOr(name,
                    anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ());
        }

        public Gate renamed(String to) {
            return new Gate(dimension, anchor, GateRules.nameOr(to, ""), acrossX);
        }
    }

    private static final Codec<List<Gate>> LIST = Gate.CODEC.listOf();

    private final Map<String, Gate> gates = new LinkedHashMap<>();

    public static SavedData.Factory<WaygateNetwork> factory() {
        return new SavedData.Factory<>(WaygateNetwork::new, WaygateNetwork::load,
                DataFixTypes.LEVEL);
    }

    /**
     * The one network, wherever you ask from.
     *
     * Always the overworld's copy. A gate on Cinderveil asking Cinderveil's
     * level data would build a second, private network that the overworld's
     * gates could not see, and the first thing a player would notice is that
     * their gates only work one way.
     */
    public static WaygateNetwork of(Level anywhere) {
        MinecraftServer server = anywhere.getServer();
        if (server == null) {
            return new WaygateNetwork();
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return new WaygateNetwork();
        }
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE);
    }

    /** Records a finished gate, or updates one that has been rebuilt. */
    public void record(Gate gate) {
        gates.put(gate.key(), gate);
        setDirty();
    }

    /** Forgets a gate whose anchor is gone. */
    public void forget(ResourceLocation dimension, BlockPos anchor) {
        Gate gone = new Gate(dimension, anchor, "", true);
        if (gates.remove(gone.key()) != null) {
            setDirty();
        }
    }

    /** The gate at this spot, or null. */
    public Gate at(ResourceLocation dimension, BlockPos anchor) {
        return gates.get(new Gate(dimension, anchor, "", true).key());
    }

    /** Every gate, in the order they were built. */
    public List<Gate> all() {
        return List.copyOf(gates.values());
    }

    /** Every gate except the one asking. */
    public List<Gate> others(ResourceLocation dimension, BlockPos anchor) {
        String mine = new Gate(dimension, anchor, "", true).key();
        List<Gate> out = new ArrayList<>();
        for (Map.Entry<String, Gate> entry : gates.entrySet()) {
            if (!entry.getKey().equals(mine)) {
                out.add(entry.getValue());
            }
        }
        return out;
    }

    /** How many gates exist, for a report or a validator. */
    public int size() {
        return gates.size();
    }

    // ------------------------------------------------------------------

    private static WaygateNetwork load(CompoundTag tag, HolderLookup.Provider lookup) {
        WaygateNetwork network = new WaygateNetwork();
        // A network that will not parse must not take the world with it: losing
        // the gates costs a player their shortcuts, which is bad; refusing to
        // load the overworld is worse.
        LIST.parse(NbtOps.INSTANCE, tag.get("gates"))
                .resultOrPartial(problem -> ElysiumSpace.LOGGER.error(
                        "the waygate network is unreadable: {}", problem))
                .ifPresent(read -> {
                    for (Gate gate : read) {
                        network.gates.put(gate.key(), gate);
                    }
                });
        return network;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        LIST.encodeStart(NbtOps.INSTANCE, new ArrayList<>(gates.values()))
                .result().ifPresent(written -> tag.put("gates", (Tag) written));
        return tag;
    }

    /** The key of the dimension a gate names, for the teleport. */
    public static ResourceKey<Level> worldOf(ResourceLocation dimension) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                dimension);
    }
}
