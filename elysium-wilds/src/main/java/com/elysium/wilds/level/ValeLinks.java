package com.elysium.wilds.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the ways into the Vale remember, across restarts.
 *
 * <p>Two things. Where each player came in, so the way back puts them there
 * rather than at the world's spawn. And which Vale side each ring leads to,
 * filed under the ring's anchor, so a ring searches for the verdant vale and
 * builds its far side once: every trip after goes where the first one went,
 * and a party through one ring arrives together.
 *
 * <p>And the world's record of its trips, for the live stages
 * (tools/livetest/stages; 16 October 2026, round 5, OP1 and RZ1): how many
 * first trips the world has had and the line the latest of them logged, word
 * for word (ValeTravel's first-trip line), and for each player how many ways
 * out of the Vale put them at the world's spawn for want of a way in on
 * record. Saved data, because no command writes it: the record was command
 * storage before, which {@code /data modify} writes. Only ValeTravel adds to
 * it, and {@code /elysium vale trips} only reads it.
 *
 * <p>Kept on the overworld, for the rift's reason: it always exists and is
 * always loaded, and a dimension nobody has visited has no save to attach to.
 */
public final class ValeLinks extends SavedData {

    private static final String NAME = "elysiumwilds_vale_links";

    /**
     * Where a player came in.
     *
     * @param dimension the world they left
     * @param where     where to put them back: in front of the ring they used
     * @param facing    which way to face them: away from it
     */
    public record Return(ResourceKey<Level> dimension, Vec3 where, float facing) {
    }

    /**
     * The Vale side of a ring.
     *
     * @param anchor the low corner of the far ring's opening
     * @param axis   which way that ring runs
     */
    public record Arrival(BlockPos anchor, Direction.Axis axis) {
    }

    private final Map<UUID, Return> returns = new HashMap<>();

    private final Map<String, Arrival> arrivals = new HashMap<>();

    /** How many first trips the world has had (see the class comment). */
    private int firstTrips;

    /** The line the latest first trip logged, word for word; empty before the first. */
    private String lastFirstTrip = "";

    /** Each player's ways out of the Vale to the world's spawn (see the class comment). */
    private final Map<UUID, Integer> outToSpawn = new HashMap<>();

    public ValeLinks() {
    }

    public static ValeLinks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ValeLinks::new, ValeLinks::load), NAME);
    }

    private static String ring(ResourceKey<Level> dimension, BlockPos anchor) {
        return dimension.location() + "@" + anchor.asLong();
    }

    /** Records where a player came in, over wherever they came in last time. */
    public void remember(UUID player, Return back) {
        returns.put(player, back);
        setDirty();
    }

    /** Where a player came in, or null if they never came through a ring. */
    public Return returnFor(UUID player) {
        return returns.get(player);
    }

    /** The Vale side a ring leads to, or null before its first trip. */
    public Arrival arrival(ResourceKey<Level> dimension, BlockPos anchor) {
        return arrivals.get(ring(dimension, anchor));
    }

    /** Files a ring's Vale side under it. */
    public void link(ResourceKey<Level> dimension, BlockPos anchor, Arrival arrival) {
        arrivals.put(ring(dimension, anchor), arrival);
        setDirty();
    }

    /** A ring's first trip, kept: one more first trip, and the line it logged. */
    public void firstTrip(String said) {
        firstTrips += 1;
        lastFirstTrip = said;
        setDirty();
    }

    /** How many first trips the world has had. */
    public int firstTrips() {
        return firstTrips;
    }

    /** The line the latest first trip logged, or empty before the first. */
    public String lastFirstTrip() {
        return lastFirstTrip;
    }

    /** A way out of the Vale that put a player at the world's spawn, counted for them. */
    public void outToSpawn(UUID player) {
        outToSpawn.merge(player, 1, Integer::sum);
        setDirty();
    }

    /** How many ways out of the Vale have put a player at the world's spawn. */
    public int outToSpawnFor(UUID player) {
        return outToSpawn.getOrDefault(player, 0);
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag backs = new ListTag();
        for (Map.Entry<UUID, Return> entry : returns.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putUUID("Player", entry.getKey());
            one.putString("Dimension", entry.getValue().dimension().location().toString());
            one.putDouble("X", entry.getValue().where().x);
            one.putDouble("Y", entry.getValue().where().y);
            one.putDouble("Z", entry.getValue().where().z);
            one.putFloat("Facing", entry.getValue().facing());
            backs.add(one);
        }
        tag.put("Returns", backs);

        ListTag links = new ListTag();
        for (Map.Entry<String, Arrival> entry : arrivals.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putString("Ring", entry.getKey());
            one.putLong("Anchor", entry.getValue().anchor().asLong());
            one.putBoolean("AlongX", entry.getValue().axis() == Direction.Axis.X);
            links.add(one);
        }
        tag.put("Arrivals", links);

        tag.putInt("FirstTrips", firstTrips);
        tag.putString("LastFirstTrip", lastFirstTrip);
        ListTag spawned = new ListTag();
        for (Map.Entry<UUID, Integer> entry : outToSpawn.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putUUID("Player", entry.getKey());
            one.putInt("Count", entry.getValue());
            spawned.add(one);
        }
        tag.put("OutToSpawn", spawned);
        return tag;
    }

    public static ValeLinks load(CompoundTag tag,
                                 net.minecraft.core.HolderLookup.Provider registries) {
        ValeLinks data = new ValeLinks();
        ListTag backs = tag.getList("Returns", Tag.TAG_COMPOUND);
        for (int i = 0; i < backs.size(); i++) {
            CompoundTag one = backs.getCompound(i);
            ResourceLocation world = ResourceLocation.tryParse(one.getString("Dimension"));
            if (world == null || !one.hasUUID("Player")) {
                continue;
            }
            data.returns.put(one.getUUID("Player"), new Return(
                    ResourceKey.create(Registries.DIMENSION, world),
                    new Vec3(one.getDouble("X"), one.getDouble("Y"), one.getDouble("Z")),
                    one.getFloat("Facing")));
        }
        ListTag links = tag.getList("Arrivals", Tag.TAG_COMPOUND);
        for (int i = 0; i < links.size(); i++) {
            CompoundTag one = links.getCompound(i);
            data.arrivals.put(one.getString("Ring"), new Arrival(
                    BlockPos.of(one.getLong("Anchor")),
                    one.getBoolean("AlongX") ? Direction.Axis.X : Direction.Axis.Z));
        }
        data.firstTrips = tag.getInt("FirstTrips");
        data.lastFirstTrip = tag.getString("LastFirstTrip");
        ListTag spawned = tag.getList("OutToSpawn", Tag.TAG_COMPOUND);
        for (int i = 0; i < spawned.size(); i++) {
            CompoundTag one = spawned.getCompound(i);
            if (one.hasUUID("Player")) {
                data.outToSpawn.put(one.getUUID("Player"), one.getInt("Count"));
            }
        }
        return data;
    }
}
