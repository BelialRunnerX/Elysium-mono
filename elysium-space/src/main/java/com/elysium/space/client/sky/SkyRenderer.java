package com.elysium.space.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a {@link Sky}, in vanilla's order (LevelRenderer.renderSky): the sky
 * disc in the biome's sky colour, the sunset fan, then the sun, moon, stars and
 * sky objects turning with the day. Buffers are built once per sky, on the
 * render thread, the first time it is drawn.
 */
final class SkyRenderer {

    private final Sky sky;
    private VertexBuffer disc;
    private VertexBuffer stars;

    SkyRenderer(Sky sky) {
        this.sky = sky;
    }

    void render(ClientLevel level, float partial, Matrix4f modelView, Camera camera, Matrix4f projection,
                Runnable setupFog) {
        if (disc == null) {
            build();
        }
        setupFog.run();
        PoseStack pose = new PoseStack();
        pose.mulPose(modelView);

        Vec3 colour = level.getSkyColor(camera.getPosition(), partial);
        FogRenderer.levelFogColor();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor((float) colour.x, (float) colour.y, (float) colour.z, 1.0F);
        disc.bind();
        disc.drawWithShader(pose.last().pose(), projection, GameRenderer.getPositionShader());
        VertexBuffer.unbind();
        RenderSystem.enableBlend();

        float time = level.getTimeOfDay(partial);
        float[] sunrise = level.effects().getSunriseColor(time, partial);
        if (sunrise != null && sky.sunset() > 0) {
            sunset(pose, sunrise, time);
        }

        RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        float clear = 1.0F - level.getRainLevel(partial);

        // Fixed objects (a planet hanging over its orbit) do not turn with the day.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, clear);
        for (Sky.SkyObject object : sky.objects()) {
            if (object.fixed()) {
                pose.pushPose();
                rotate(pose, object);
                quad(pose, object.body(), false, 0, 0, 1, 1);
                pose.popPose();
            }
        }

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        float brightness = sky.stars().always() ? clear : level.getStarBrightness(partial) * clear;
        if (brightness > 0.0F && !sky.stars().moving()) {
            drawStars(pose, projection, brightness, setupFog);
        }
        pose.mulPose(Axis.XP.rotationDegrees(time * 360.0F));
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, clear);
        quad(pose, sky.sun(), false, 0, 0, 1, 1);
        if (sky.moon() != null) {
            if (sky.moonPhases()) {
                int phase = level.getMoonPhase();
                float u = (phase % 4) / 4.0F;
                float v = (phase / 4 % 2) / 2.0F;
                quad(pose, sky.moon(), true, u, v, u + 0.25F, v + 0.5F);
            } else {
                quad(pose, sky.moon(), true, 0, 0, 1, 1);
            }
        }
        for (Sky.SkyObject object : sky.objects()) {
            if (!object.fixed()) {
                pose.pushPose();
                rotate(pose, object);
                quad(pose, object.body(), false, 0, 0, 1, 1);
                pose.popPose();
            }
        }
        if (brightness > 0.0F && sky.stars().moving()) {
            drawStars(pose, projection, brightness, setupFog);
        }
        pose.popPose();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
    }

    private void drawStars(PoseStack pose, Matrix4f projection, float brightness, Runnable setupFog) {
        Sky.Stars s = sky.stars();
        RenderSystem.setShaderColor(brightness * s.r(), brightness * s.g(), brightness * s.b(), brightness);
        FogRenderer.setupNoFog();
        stars.bind();
        stars.drawWithShader(pose.last().pose(), projection, GameRenderer.getPositionShader());
        VertexBuffer.unbind();
        setupFog.run();
    }

    private static void rotate(PoseStack pose, Sky.SkyObject object) {
        pose.mulPose(Axis.XP.rotationDegrees(object.rx()));
        pose.mulPose(Axis.YP.rotationDegrees(object.ry()));
        pose.mulPose(Axis.ZP.rotationDegrees(object.rz()));
    }

    /** A textured square facing the viewer, above (or, for a moon, below) the sky's centre. */
    private static void quad(PoseStack pose, Sky.Body body, boolean below, float u0, float v0, float u1, float v1) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, body.texture());
        Matrix4f m = pose.last().pose();
        float s = body.size();
        float h = below ? -body.height() : body.height();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        if (below) {
            b.addVertex(m, -s, h, s).setUv(u1, v1);
            b.addVertex(m, s, h, s).setUv(u0, v1);
            b.addVertex(m, s, h, -s).setUv(u0, v0);
            b.addVertex(m, -s, h, -s).setUv(u1, v0);
        } else {
            b.addVertex(m, -s, h, -s).setUv(u0, v0);
            b.addVertex(m, s, h, -s).setUv(u1, v0);
            b.addVertex(m, s, h, s).setUv(u1, v1);
            b.addVertex(m, -s, h, s).setUv(u0, v1);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private void sunset(PoseStack pose, float[] c, float time) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        float side = Mth.sin(sunAngle(time)) < 0.0F ? 180.0F : 0.0F;
        pose.mulPose(Axis.ZP.rotationDegrees(side));
        pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
        Matrix4f m = pose.last().pose();
        float alpha = c[3] * Math.min(1.0F, sky.sunset());
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        b.addVertex(m, 0.0F, 100.0F, 0.0F).setColor(c[0], c[1], c[2], alpha);
        for (int i = 0; i <= 16; i++) {
            float a = i * (float) (Math.PI * 2) / 16.0F;
            float x = Mth.sin(a);
            float y = Mth.cos(a);
            b.addVertex(m, x * 120.0F, y * 120.0F, -y * 40.0F * c[3]).setColor(c[0], c[1], c[2], 0.0F);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        pose.popPose();
    }

    private static float sunAngle(float time) {
        return time * (float) (Math.PI * 2);
    }

    private void build() {
        Tesselator t = Tesselator.getInstance();
        BufferBuilder d = t.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        float y = 16.0F;
        d.addVertex(0.0F, y, 0.0F);
        for (int i = -180; i <= 180; i += 45) {
            d.addVertex(512.0F * Mth.cos(i * Mth.DEG_TO_RAD), y, 512.0F * Mth.sin(i * Mth.DEG_TO_RAD));
        }
        disc = new VertexBuffer(VertexBuffer.Usage.STATIC);
        disc.bind();
        disc.upload(d.buildOrThrow());

        RandomSource random = RandomSource.create(10842L);
        BufferBuilder b = t.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        float scale = sky.stars().scale() / 0.1F;
        for (int i = 0; i < sky.stars().count(); i++) {
            float x = random.nextFloat() * 2.0F - 1.0F;
            float yy = random.nextFloat() * 2.0F - 1.0F;
            float z = random.nextFloat() * 2.0F - 1.0F;
            float size = (0.15F + random.nextFloat() * 0.1F) * scale;
            float len = Mth.lengthSquared(x, yy, z);
            if (len <= 0.010000001F || len >= 1.0F) {
                continue;
            }
            Vector3f at = new Vector3f(x, yy, z).normalize(100.0F);
            float spin = (float) (random.nextDouble() * Math.PI * 2);
            Quaternionf q = new Quaternionf().rotateTo(new Vector3f(0.0F, 0.0F, -1.0F), at).rotateZ(spin);
            b.addVertex(new Vector3f(at).add(new Vector3f(size, -size, 0.0F).rotate(q)));
            b.addVertex(new Vector3f(at).add(new Vector3f(size, size, 0.0F).rotate(q)));
            b.addVertex(new Vector3f(at).add(new Vector3f(-size, size, 0.0F).rotate(q)));
            b.addVertex(new Vector3f(at).add(new Vector3f(-size, -size, 0.0F).rotate(q)));
        }
        stars = new VertexBuffer(VertexBuffer.Usage.STATIC);
        stars.bind();
        stars.upload(b.buildOrThrow());
        VertexBuffer.unbind();
    }
}
