package com.elysium.space.client.sky;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * A space world's dimension effects (in place of Sky Aesthetics): our sky
 * renderer, and clouds and weather only where the world has them. Fog and
 * lighting follow the overworld's rules unless the sky file says otherwise.
 */
public final class SpaceSkyEffects extends DimensionSpecialEffects {

    private final Sky sky;
    private final SkyRenderer renderer;

    public SpaceSkyEffects(Sky sky) {
        super(sky.cloudHeight(), true, SkyType.NORMAL, sky.brightLightmap(), sky.ambientLight());
        this.sky = sky;
        this.renderer = new SkyRenderer(sky);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 colour, float brightness) {
        return colour.multiply(brightness * 0.94F + 0.06F, brightness * 0.94F + 0.06F, brightness * 0.91F + 0.09F);
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelView, Camera camera,
                             Matrix4f projection, boolean isFoggy, Runnable setupFog) {
        renderer.render(level, partialTick, modelView, camera, projection, setupFog);
        return true;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, com.mojang.blaze3d.vertex.PoseStack pose,
                                double camX, double camY, double camZ, Matrix4f modelView, Matrix4f projection) {
        return !sky.clouds();           // true: nothing drawn
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture light,
                                     double camX, double camY, double camZ) {
        return !sky.weather();
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return !sky.weather();
    }
}
