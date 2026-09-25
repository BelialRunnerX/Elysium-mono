package com.elysium.arsenal.block.entity;

import com.elysium.arsenal.registry.DigBlockEntities;
import com.elysium.arsenal.rules.DigRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One dig site, part-way through being brushed.
 *
 * <h2>What is stored and what is not</h2>
 *
 * How far through the brushing it is, is stored — a player has to be able to
 * walk away and come back. <em>What is in it</em> is not: that is worked out
 * from where the block is and the world's seed every time it is asked. Storing
 * it would make it something that could differ between two blocks that ought
 * to hold the same thing, and would also let a player who broke the block and
 * put it back roll again for something better.
 */
public class SiteBlockEntity extends BlockEntity {

    /** How many ticks of brushing have gone into the current stage. */
    private int brushed;

    /** Which stage it is on, from nothing to {@link DigRules#STAGES}. */
    private int stage;

    /** Which pool this site draws from, kept so the block can be any of ours. */
    private String pool = "dirt";

    public SiteBlockEntity(BlockPos pos, BlockState state) {
        super(DigBlockEntities.SITE.get(), pos, state);
    }

    /** Which pool this site draws from. */
    public String pool() {
        return pool;
    }

    /** Tell it which pool it draws from, when it is placed. */
    public void setPool(String pool) {
        this.pool = pool;
        setChanged();
    }

    /** How far through the whole site the brushing is, from nothing to one. */
    public float through() {
        return Math.min(1.0F, stage / (float) DigRules.STAGES);
    }

    /** Which stage it is on. */
    public int stage() {
        return stage;
    }

    /**
     * Brush it for one tick with a brush of that speed.
     *
     * @return true when a stage has just been finished
     */
    public boolean brushFor(int ticksPerStage) {
        brushed++;
        if (brushed < Math.max(DigRules.FASTEST, ticksPerStage)) {
            return false;
        }
        brushed = 0;
        stage++;
        setChanged();
        return true;
    }

    /** Whether the site has given up everything it has. */
    public boolean finished() {
        return stage >= DigRules.STAGES;
    }

    /**
     * What is buried here.
     *
     * <p>From the position and the world's seed, so the same block always
     * holds the same thing — which is what makes a site a place rather than
     * something a player can re-roll by breaking it and putting it back.
     */
    public ItemStack whatIsBuried(ServerLevel level) {
        DigRules.Find find = DigRules.whatIsBuried(pool, level.getSeed(),
                getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ());
        if (find == null) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(find.item());
        if (id == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(id)
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        brushed = tag.getInt("brushed");
        stage = tag.getInt("stage");
        if (tag.contains("pool")) {
            pool = tag.getString("pool");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("brushed", brushed);
        tag.putInt("stage", stage);
        tag.putString("pool", pool);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
