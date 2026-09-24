package com.elysium.enchanting.block;

import com.elysium.enchanting.registry.EnchantingBlockEntities;
import com.elysium.enchanting.rules.LibraryRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a Scriptorium is holding.
 *
 * <h2>Pages by enchantment, and nothing crosses</h2>
 *
 * One number per enchantment, keyed by its registry id rather than by a
 * holder: a shelf has to survive a mod being removed, and an id that no longer
 * resolves is a line the shelf keeps and cannot spend rather than a crash on
 * load.
 *
 * <h2>Why the whole shelf is sent to the client</h2>
 *
 * The screen lists what is filed here and what each line would buy, and a
 * menu's container data is a handful of integers — it cannot carry names. So
 * the block entity syncs its shelf the way a chest syncs its contents, and the
 * screen reads the copy in front of the player. The menu still works
 * everything out again on the server before it spends anything.
 */
public class ScriptoriumBlockEntity extends BlockEntity {

    /** Pages filed, by enchantment id, in the order a screen should list them. */
    private final Map<String, Integer> shelf = new TreeMap<>();

    public ScriptoriumBlockEntity(BlockPos pos, BlockState state) {
        super(EnchantingBlockEntities.SCRIPTORIUM.get(), pos, state);
    }

    /** How many pages this shelf holds for one enchantment. */
    public int capacity() {
        return getBlockState().getBlock() instanceof ScriptoriumBlock scriptorium
                ? scriptorium.size().capacity
                : LibraryRules.SHELF_CAPACITY;
    }

    public int pagesOf(String enchantment) {
        return shelf.getOrDefault(enchantment, 0);
    }

    /** Everything filed here, in a fixed order, so a client and a server agree on it. */
    public List<String> filed() {
        return new ArrayList<>(shelf.keySet());
    }

    public static String idOf(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey().map(key -> key.location().toString()).orElse("");
    }

    /**
     * File a book.
     *
     * @return false when the shelf had no room, in which case nothing is taken
     */
    public boolean file(String enchantment, int level) {
        int stored = pagesOf(enchantment);
        if (!LibraryRules.room(stored, level, capacity())) {
            return false;
        }
        shelf.put(enchantment, LibraryRules.deposit(stored, level, capacity()));
        changed();
        return true;
    }

    /**
     * Take a copy.
     *
     * @return false when the shelf could not pay for it
     */
    public boolean take(String enchantment, int level) {
        int stored = pagesOf(enchantment);
        if (LibraryRules.cost(level) > stored) {
            return false;
        }
        int left = LibraryRules.withdraw(stored, level);
        if (left <= 0) {
            shelf.remove(enchantment);
        } else {
            shelf.put(enchantment, left);
        }
        changed();
        return true;
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    // ------------------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        shelf.clear();
        ListTag filed = tag.getList("shelf", Tag.TAG_COMPOUND);
        for (int index = 0; index < filed.size(); index++) {
            CompoundTag line = filed.getCompound(index);
            String id = line.getString("enchantment");
            int pages = line.getInt("pages");
            if (!id.isEmpty() && pages > 0) {
                shelf.put(id, pages);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag filed = new ListTag();
        for (Map.Entry<String, Integer> line : shelf.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putString("enchantment", line.getKey());
            one.putInt("pages", line.getValue());
            filed.add(one);
        }
        tag.put("shelf", filed);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The registry key a filed line names, for a screen that wants to show its name. */
    @Nullable
    public static net.minecraft.resources.ResourceKey<Enchantment> keyOf(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? null
                : net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, location);
    }

    /** Only used by the tests that check a shelf round-trips; kept deliberately small. */
    public Map<String, Integer> contents() {
        return new LinkedHashMap<>(shelf);
    }
}
