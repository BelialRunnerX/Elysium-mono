package com.elysium.logistics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
 * Which ender links share a channel, in one file for the whole server.
 *
 * <h2>Why saved data and not a search</h2>
 *
 * Two ender links are meant to join networks that are nowhere near each
 * other — another base, another world. Nothing can walk to the other end,
 * so the other end has to be written down somewhere both can read, and the
 * overworld's saved data is the one place every server has exactly one of
 * and loads before any other world is asked for. The spaceport registry in
 * the colonies module lives there for the same reason.
 *
 * <h2>What a channel is</h2>
 *
 * The id of whatever item a player showed the link: a diamond, a named
 * sign, a specific compressed block. There is no screen to type a name into
 * and no need for one — two players who each hold up a lapis block have
 * agreed on a channel in the only way this pack asks anything of anybody.
 */
public final class EnderChannels extends SavedData {

    /** The file this lives in. */
    public static final String FILE = "elysium_ender_channels";

    /** One end of a link, wherever it is. */
    public record End(ResourceLocation dimension, BlockPos pos) {

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dim", dimension.toString());
            tag.putInt("x", pos.getX());
            tag.putInt("y", pos.getY());
            tag.putInt("z", pos.getZ());
            return tag;
        }

        static End load(CompoundTag tag) {
            return new End(ResourceLocation.parse(tag.getString("dim")),
                    new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")));
        }
    }

    private final Map<String, List<End>> channels = new LinkedHashMap<>();

    public static SavedData.Factory<EnderChannels> factory() {
        return new SavedData.Factory<>(EnderChannels::new, EnderChannels::load,
                DataFixTypes.LEVEL);
    }

    /** The one registry, always the overworld's copy. */
    public static EnderChannels of(MinecraftServer server) {
        ServerLevel home = server.getLevel(Level.OVERWORLD);
        if (home == null) {
            home = server.overworld();
        }
        return home.getDataStorage().computeIfAbsent(factory(), FILE);
    }

    /** Put this end on a channel, once. */
    public void join(String channel, End end) {
        if (channel == null || channel.isEmpty()) {
            return;
        }
        List<End> ends = channels.computeIfAbsent(channel, key -> new ArrayList<>());
        if (!ends.contains(end)) {
            ends.add(end);
            setDirty();
        }
    }

    /** Take this end off a channel. */
    public void leave(String channel, End end) {
        List<End> ends = channels.get(channel);
        if (ends != null && ends.remove(end)) {
            if (ends.isEmpty()) {
                channels.remove(channel);
            }
            setDirty();
        }
    }

    /** Every other end on this channel, in the order they joined. */
    public List<End> others(String channel, End self) {
        List<End> out = new ArrayList<>();
        for (End one : channels.getOrDefault(channel, List.of())) {
            if (!one.equals(self)) {
                out.add(one);
            }
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<String, List<End>> entry : channels.entrySet()) {
            ListTag list = new ListTag();
            for (End end : entry.getValue()) {
                list.add(end.save());
            }
            all.put(entry.getKey(), list);
        }
        tag.put("channels", all);
        return tag;
    }

    public static EnderChannels load(CompoundTag tag, HolderLookup.Provider registries) {
        EnderChannels made = new EnderChannels();
        CompoundTag all = tag.getCompound("channels");
        for (String channel : all.getAllKeys()) {
            ListTag list = all.getList(channel, Tag.TAG_COMPOUND);
            List<End> ends = new ArrayList<>();
            for (int at = 0; at < list.size(); at++) {
                ends.add(End.load(list.getCompound(at)));
            }
            if (!ends.isEmpty()) {
                made.channels.put(channel, ends);
            }
        }
        return made;
    }
}
