package com.elysium.spawners.event;

import com.elysium.spawners.ElysiumSpawners;
import com.elysium.spawners.block.ElysiumSpawnerBlockEntity;
import com.elysium.spawners.data.SpawnerTuning;
import com.elysium.spawners.registry.SpawnerBlocks;
import com.elysium.spawners.registry.SpawnerComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Picking a spawner up (Apothic Spawners' AS-1).
 *
 * <h2>Why this is code and not a loot table</h2>
 *
 * A spawner that can be carried has to carry two things: what is in it, and
 * what has been done to it. The second is a data component and a loot table
 * can copy that. The first is the block entity's own tag — the mob, its NBT,
 * the spawn potentials — and there is no loot function that puts a block
 * entity's tag into the one component that restores it.
 *
 * <p>So the drop is assembled here, in one place, for both blocks. The loot
 * tables of this module's spawner are deliberately empty and say so; anything
 * else would be two mechanisms that have to agree about the same item.
 *
 * <h2>Vanilla's spawner becomes this one</h2>
 *
 * Mined with silk touch, a vanilla spawner drops an Elysium spawner holding
 * exactly what it held. That is the only moment a player was ever going to
 * care which block it was, and it means no structure, advancement or other
 * mod has to be told anything: the block in the world is still vanilla's until
 * somebody decides otherwise.
 */
@EventBusSubscriber(modid = ElysiumSpawners.MODID)
public final class SpawnerPickup {

    private SpawnerPickup() {
    }

    /** What a smashed spawner is worth. A table, so a pack decides. */
    public static final net.minecraft.resources.ResourceKey<LootTable> UNSTABLE =
            net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE,
                    ResourceLocation.fromNamespaceAndPath(ElysiumSpawners.MODID,
                            "unstable_spawner"));

    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        BlockEntity entity = event.getBlockEntity();
        boolean ours = entity instanceof ElysiumSpawnerBlockEntity;
        boolean vanilla = isVanillaSpawner(entity);
        if (!ours && !vanilla) {
            return;
        }
        if (ours && !carriable(((ElysiumSpawnerBlockEntity) entity).tuning())) {
            // An unstable spawner is not picked up. It is the one that goes off
            // when it is touched, and what it leaves is its own loot table's
            // business rather than a block in a pocket.
            return;
        }
        if (!hasSilkTouch(event.getLevel(), event.getTool())) {
            smashed(event);
            return;
        }
        event.getDrops().clear();
        event.setDroppedExperience(0);
        ItemStack stack = new ItemStack(SpawnerBlocks.SPAWNER_ITEM.get());
        CompoundTag contents = contentsOf(entity, event.getLevel());
        if (contents != null) {
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(contents));
        }
        if (ours) {
            stack.set(SpawnerComponents.TUNING.get(),
                    ((ElysiumSpawnerBlockEntity) entity).tuning());
        }
        BlockPos pos = event.getPos();
        event.getDrops().add(new ItemEntity(event.getLevel(), pos.getX() + 0.5D,
                pos.getY() + 0.5D, pos.getZ() + 0.5D, stack));
    }

    /**
     * A spawner broken by somebody without silk touch (Apothic Spawners'
     * AS-5).
     *
     * Vanilla gives you rubble and some experience, which makes smashing one
     * strictly worse than leaving it — and since a lit room is worth more than
     * four zombies a minute, every player smashes every spawner anyway and
     * gets nothing for it. Here it pays: the cage comes apart and what was
     * holding it together is worth something.
     *
     * <p>So the decision is real in both directions. Silk-touch it and own it,
     * or take it apart and take the parts.
     */
    private static void smashed(BlockDropsEvent event) {
        ServerLevel level = event.getLevel();
        BlockPos pos = event.getPos();
        LootTable table = level.getServer().reloadableRegistries()
                .getLootTable(UNSTABLE);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, pos.getCenter())
                .withParameter(LootContextParams.TOOL, event.getTool())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, event.getBreaker())
                .create(LootContextParamSets.CHEST);
        for (ItemStack stack : table.getRandomItems(params)) {
            event.getDrops().add(new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D, stack));
        }
    }

    /**
     * What the spawner is holding, written under this module's block entity id.
     *
     * The id matters: placing a block item applies its block-entity data only
     * when the tag names the block entity that is actually there, which is how
     * vanilla stops a chest's contents being loaded into a furnace.
     */
    @Nullable
    private static CompoundTag contentsOf(BlockEntity entity, ServerLevel level) {
        CompoundTag tag = entity.saveWithoutMetadata(level.registryAccess());
        if (tag.isEmpty()) {
            return null;
        }
        tag.putString("id", ResourceLocation.fromNamespaceAndPath(
                ElysiumSpawners.MODID, "spawner").toString());
        // The tuning travels as a component rather than inside the tag: two
        // copies of one fact is one copy too many, and the component is the
        // one a player can read.
        tag.remove("tuning");
        return tag;
    }

    private static boolean hasSilkTouch(ServerLevel level, ItemStack tool) {
        if (tool.isEmpty()) {
            return false;
        }
        Optional<Holder.Reference<Enchantment>> silk = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.SILK_TOUCH);
        return silk.isPresent()
                && EnchantmentHelper.getItemEnchantmentLevel(silk.get(), tool) > 0;
    }

    /** Whether a spawner in this state would come up at all. */
    public static boolean carriable(SpawnerTuning tuning) {
        return !tuning.unstable();
    }

    /** Vanilla's block, named once so the reason above is checkable. */
    public static boolean isVanillaSpawner(BlockEntity entity) {
        return entity instanceof SpawnerBlockEntity
                && entity.getBlockState().is(Blocks.SPAWNER);
    }
}
