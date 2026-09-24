package com.elysium.space.freight;

import com.elysium.space.ElysiumSpace;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every freight terminal in the game, and everything currently in the air.
 *
 * <h2>Why the cargo lives here and not in a rocket</h2>
 *
 * Because a rocket is an entity and an entity is in a world, and cargo in
 * flight is by definition between two of them. An entity carrying a chest of
 * goods across a dimension boundary is a chunk-unload away from being gone,
 * and "your shipment vanished because nobody was standing there" is the single
 * worst thing a logistics system can do.
 *
 * <p>So a shipment is a record in the overworld's saved data with a landing
 * time on it, and the rocket you watch leave is scenery. The cargo is as safe
 * as the save file.
 *
 * <h2>And why one file for every world</h2>
 *
 * Because freight between two worlds is the whole point, and a per-world
 * registry could not answer the only question a terminal asks: where can I
 * send this? The same shape the waygate network uses, for the same reason.
 */
public final class FreightNetwork extends SavedData {

    /** The file this lives in. */
    public static final String FILE = "elysium_freight";

    /**
     * One terminal, as somewhere a rocket can be sent.
     *
     * @param dimension the world it stands in
     * @param at        the block
     */
    public record Terminal(ResourceLocation dimension, BlockPos at) {

        public static final Codec<Terminal> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        ResourceLocation.CODEC.fieldOf("dimension")
                                .forGetter(Terminal::dimension),
                        BlockPos.CODEC.fieldOf("at").forGetter(Terminal::at)
                ).apply(instance, Terminal::new));

        /** The address this terminal answers to, which is what a packet carries. */
        public String key() {
            return dimension + "@" + at.getX() + "," + at.getY() + "," + at.getZ();
        }

        /** What a player reads in the list. */
        public String shown() {
            String world = dimension.getPath().replace('_', ' ');
            return world + " " + at.getX() + ", " + at.getZ();
        }

        /** The world's own name, as {@code SpaceRoute} spells it. */
        public String world() {
            return dimension.getPath();
        }
    }

    /**
     * One load, between leaving and landing.
     *
     * @param from       where it left
     * @param to         where it is going
     * @param cargo      what is aboard
     * @param launchedAt the overworld's game time when it left
     * @param flight     how many ticks it takes
     */
    public record Shipment(Terminal from, Terminal to, List<ItemStack> cargo,
                           long launchedAt, int flight) {

        public static final Codec<Shipment> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Terminal.CODEC.fieldOf("from").forGetter(Shipment::from),
                        Terminal.CODEC.fieldOf("to").forGetter(Shipment::to),
                        ItemStack.CODEC.listOf().fieldOf("cargo").forGetter(Shipment::cargo),
                        Codec.LONG.fieldOf("launched").forGetter(Shipment::launchedAt),
                        Codec.INT.fieldOf("flight").forGetter(Shipment::flight)
                ).apply(instance, Shipment::new));

        /** The same load, sent back where it came from, because nothing is lost. */
        public Shipment returned(long now) {
            return new Shipment(to, from, cargo, now, flight);
        }

        /** The same load, still in the air, tried again later. */
        public Shipment held(long now) {
            return new Shipment(from, to, cargo, now, flight);
        }
    }

    private static final Codec<List<Shipment>> SHIPMENTS = Shipment.CODEC.listOf();
    private static final Codec<List<Terminal>> TERMINALS = Terminal.CODEC.listOf();

    private final Map<String, Terminal> terminals = new HashMap<>();
    private final List<Shipment> flying = new ArrayList<>();

    public static SavedData.Factory<FreightNetwork> factory() {
        return new SavedData.Factory<>(FreightNetwork::new, FreightNetwork::load,
                net.minecraft.util.datafix.DataFixTypes.LEVEL);
    }

    /** The one network, wherever it is asked for. */
    public static FreightNetwork of(MinecraftServer server) {
        ServerLevel home = server.getLevel(Level.OVERWORLD);
        if (home == null) {
            home = server.overworld();
        }
        return home.getDataStorage().computeIfAbsent(factory(), FILE);
    }

    // ------------------------------------------------------------------
    // Terminals
    // ------------------------------------------------------------------

    public void record(Terminal terminal) {
        terminals.put(terminal.key(), terminal);
        setDirty();
    }

    public void forget(ResourceLocation dimension, BlockPos at) {
        if (terminals.remove(new Terminal(dimension, at).key()) != null) {
            setDirty();
        }
    }

    public Terminal terminal(String key) {
        return terminals.get(key);
    }

    /** Every terminal, in a stable order. */
    public List<Terminal> all() {
        List<Terminal> out = new ArrayList<>(terminals.values());
        out.sort((one, two) -> one.key().compareTo(two.key()));
        return out;
    }

    // ------------------------------------------------------------------
    // What is in the air
    // ------------------------------------------------------------------

    public void launch(Shipment shipment) {
        flying.add(shipment);
        setDirty();
    }

    /** How many loads this terminal has out, which is what limits it. */
    public int inFlightFrom(Terminal terminal) {
        int out = 0;
        for (Shipment shipment : flying) {
            if (shipment.from().key().equals(terminal.key())) {
                out++;
            }
        }
        return out;
    }

    /** Everything on its way here, for a terminal that wants to say so. */
    public List<Shipment> inbound(Terminal terminal) {
        List<Shipment> out = new ArrayList<>();
        for (Shipment shipment : flying) {
            if (shipment.to().key().equals(terminal.key())) {
                out.add(shipment);
            }
        }
        return out;
    }

    /** Everything in the air, as a copy: the caller is about to change the list. */
    public List<Shipment> flying() {
        return new ArrayList<>(flying);
    }

    public void settle(Shipment shipment) {
        if (flying.remove(shipment)) {
            setDirty();
        }
    }

    /** Puts a shipment back in the air in a new state, in one step. */
    public void replace(Shipment was, Shipment now) {
        flying.remove(was);
        flying.add(now);
        setDirty();
    }

    public int size() {
        return flying.size();
    }

    // ------------------------------------------------------------------

    private static FreightNetwork load(CompoundTag tag, HolderLookup.Provider lookup) {
        FreightNetwork network = new FreightNetwork();
        // Item stacks carry components, and a component is a registry entry, so
        // reading one needs the registries rather than plain NBT ops. This is
        // the difference between a shipment of enchanted tools surviving a
        // restart and arriving as sticks.
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, lookup);
        TERMINALS.parse(ops, tag.get("terminals"))
                .resultOrPartial(problem -> ElysiumSpace.LOGGER.error(
                        "freight terminals are unreadable: {}", problem))
                .ifPresent(found -> {
                    for (Terminal terminal : found) {
                        network.terminals.put(terminal.key(), terminal);
                    }
                });
        SHIPMENTS.parse(ops, tag.get("flying"))
                .resultOrPartial(problem -> ElysiumSpace.LOGGER.error(
                        "freight in flight is unreadable: {}", problem))
                .ifPresent(network.flying::addAll);
        return network;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, lookup);
        TERMINALS.encodeStart(ops, all()).result()
                .ifPresent(written -> tag.put("terminals", written));
        SHIPMENTS.encodeStart(ops, flying).result()
                .ifPresent(written -> tag.put("flying", written));
        return tag;
    }
}
