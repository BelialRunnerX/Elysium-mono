package com.elysium.space.station;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.registry.SpaceBlocks;
import com.elysium.space.world.SpaceWorld;
import com.elysium.space.world.SpaceWorlds;
import com.elysium.space.world.StationRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who owns which station in High Orbit (Stellaris's space stations, one per
 * player). Kept on the overworld, which always exists, so a station survives
 * even before High Orbit has ever been loaded. Slots only ever increase, so no
 * station is built over another; a player who upgrades keeps their slot.
 */
public final class SpaceStations extends SavedData {

    private static final String NAME = "elysiumspace_stations";

    public record Station(int slot, StationRules.Size size, BlockPos pad) {
    }

    private int nextSlot;
    private final Map<UUID, Station> stations = new HashMap<>();

    public static SpaceStations get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SpaceStations::new, SpaceStations::load), NAME);
    }

    public Station of(UUID player) {
        return stations.get(player);
    }

    /** Where a player arriving in High Orbit lands: above their station's launch pad, if they have one. */
    public static BlockPos arrival(ServerPlayer player, ServerLevel target) {
        SpaceWorld world = SpaceWorlds.of(target);
        if (world == null || !world.path().equals(StationRules.WORLD)) {
            return null;
        }
        Station station = get(player.server).of(player.getUUID());
        return station == null ? null : station.pad().above();
    }

    public enum Result { BUILT, UPGRADED, NOT_IN_ORBIT, ALREADY, MISSING_MATERIALS, NO_TEMPLATE }

    static final TagKey<Item> STEEL = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ingots/steel"));
    static final TagKey<Item> LUNITE = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ingots/lunite"));

    /**
     * Builds (or upgrades to) a station of {@code size} for {@code player},
     * who must be in High Orbit and carry the materials (creative players
     * build free). On success the player stands on the new station's pad.
     */
    public Result build(ServerPlayer player, StationRules.Size size) {
        ServerLevel level = player.serverLevel();
        SpaceWorld world = SpaceWorlds.of(level);
        if (world == null || !world.path().equals(StationRules.WORLD)) {
            return Result.NOT_IN_ORBIT;
        }
        Station owned = stations.get(player.getUUID());
        StationRules.Build kind = StationRules.build(owned == null ? null : owned.size(), size);
        if (kind == StationRules.Build.ALREADY) {
            return Result.ALREADY;
        }
        boolean free = player.getAbilities().instabuild;
        if (!free && (count(player, STEEL) < size.steel || count(player, LUNITE) < size.lunite)) {
            return Result.MISSING_MATERIALS;
        }
        StructureTemplate template = level.getStructureManager()
                .get(ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, size.template())).orElse(null);
        if (template == null) {
            return Result.NO_TEMPLATE;
        }
        int slot = owned != null ? owned.slot() : nextSlot++;
        int[] corner = StationRules.slot(slot);
        BlockPos origin = new BlockPos(corner[0], StationRules.FLOOR_Y, corner[1]);
        StructurePlaceSettings settings = new StructurePlaceSettings();
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        BlockPos pad = padCentre(template.filterBlocks(origin, settings, SpaceBlocks.LAUNCH_PAD.get()), origin, template);
        if (!free) {
            take(player, STEEL, size.steel);
            take(player, LUNITE, size.lunite);
        }
        stations.put(player.getUUID(), new Station(slot, size, pad));
        setDirty();
        player.teleportTo(pad.getX() + 0.5D, pad.getY() + 1.0D, pad.getZ() + 0.5D);
        return kind == StationRules.Build.NEW ? Result.BUILT : Result.UPGRADED;
    }

    /** The launch pad block nearest the pads' middle; the template's centre if it has none. */
    static BlockPos padCentre(List<StructureTemplate.StructureBlockInfo> pads, BlockPos origin, StructureTemplate template) {
        if (pads.isEmpty()) {
            return origin.offset(template.getSize().getX() / 2, template.getSize().getY() / 2, template.getSize().getZ() / 2);
        }
        double x = 0, y = 0, z = 0;
        for (StructureTemplate.StructureBlockInfo info : pads) {
            x += info.pos().getX();
            y += info.pos().getY();
            z += info.pos().getZ();
        }
        BlockPos mid = BlockPos.containing(x / pads.size(), y / pads.size(), z / pads.size());
        BlockPos best = pads.get(0).pos();
        for (StructureTemplate.StructureBlockInfo info : pads) {
            if (info.pos().distSqr(mid) < best.distSqr(mid)) {
                best = info.pos();
            }
        }
        return best;
    }

    static int count(ServerPlayer player, TagKey<Item> tag) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(tag)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    static void take(ServerPlayer player, TagKey<Item> tag, int amount) {
        for (ItemStack stack : player.getInventory().items) {
            if (amount <= 0) {
                return;
            }
            if (stack.is(tag)) {
                int used = Math.min(amount, stack.getCount());
                stack.shrink(used);
                amount -= used;
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("NextSlot", nextSlot);
        ListTag list = new ListTag();
        stations.forEach((owner, s) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Owner", owner);
            e.putInt("Slot", s.slot());
            e.putString("Size", s.size().id);
            e.putLong("Pad", s.pad().asLong());
            list.add(e);
        });
        tag.put("Stations", list);
        return tag;
    }

    public static SpaceStations load(CompoundTag tag, HolderLookup.Provider registries) {
        SpaceStations data = new SpaceStations();
        data.nextSlot = tag.getInt("NextSlot");
        ListTag list = tag.getList("Stations", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            StationRules.Size size = StationRules.Size.named(e.getString("Size"));
            if (size != null) {
                data.stations.put(e.getUUID("Owner"),
                        new Station(e.getInt("Slot"), size, BlockPos.of(e.getLong("Pad"))));
            }
        }
        return data;
    }
}
