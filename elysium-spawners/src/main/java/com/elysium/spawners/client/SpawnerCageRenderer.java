package com.elysium.spawners.client;

import com.elysium.spawners.block.ElysiumSpawnerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.SpawnerRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * The creature turning in the cage.
 *
 * <h2>Vanilla's own drawing</h2>
 *
 * {@link SpawnerRenderer#renderEntityInSpawner} is public and does exactly the
 * right thing — the scale, the spin, the bob. Calling it means this spawner
 * looks like a spawner in every resource pack, in every shader, at every
 * distance, without this module knowing how any of that works.
 */
public class SpawnerCageRenderer implements BlockEntityRenderer<ElysiumSpawnerBlockEntity> {

    private final EntityRenderDispatcher entities;

    public SpawnerCageRenderer(BlockEntityRendererProvider.Context context) {
        this.entities = context.getEntityRenderer();
    }

    @Override
    public void render(ElysiumSpawnerBlockEntity spawner, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        Level level = spawner.getLevel();
        if (level == null) {
            return;
        }
        Entity shown = spawner.spawner().getOrCreateDisplayEntity(level, spawner.getBlockPos());
        if (shown == null) {
            return;
        }
        SpawnerRenderer.renderEntityInSpawner(partialTick, pose, buffers, light, shown,
                entities, spawner.spawner().getoSpin(), spawner.spawner().getSpin());
    }
}
