package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.hazard.ElysiumHazards;
import com.elysium.lib.weather.ElysiumWeather;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Telling people about the weather, and showing it to them.
 *
 * <h2>Why anything has to be said at all</h2>
 *
 * Because a hazard that rises without warning is a hazard that reads as a bug.
 * The player is standing where they were standing a minute ago, taking damage
 * they were not taking, and nothing on the screen has changed. One line when
 * it starts and one when it clears is the difference between "the weather
 * turned" and "something is wrong with this mod".
 *
 * <h2>Why the particles are chosen from the hazard</h2>
 *
 * So that a module declaring a new kind of storm gets something to look at
 * without deciding how it looks, and so that two worlds' ash storms look like
 * each other. A storm is drawn in the colour of what it is doing to you, which
 * is also the most useful thing it could tell you.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumWeatherHandler {

    private ElysiumWeatherHandler() {
    }

    /** How often the sky is asked. Once a second; the answer changes over minutes. */
    public static final int INTERVAL = 20;

    /** How often a player outdoors sees the weather. */
    public static final int PARTICLE_INTERVAL = 10;

    /** How many particles, at full strength. */
    public static final int PARTICLES = 12;

    /** How far around a player the weather is drawn. */
    public static final double SPREAD = 10.0D;

    /** What the sky was doing last time anybody looked, per world. */
    private static final Map<ResourceLocation, String> LAST = new HashMap<>();

    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        long time = level.getGameTime();
        ElysiumWeather.Spell spell = ElysiumWeather.now(level);
        if (time % INTERVAL == 0L) {
            announce(level, spell);
        }
        if (spell.blowing() && time % PARTICLE_INTERVAL == 0L) {
            draw(level, spell);
        }
    }

    /** One line when it turns, and only when it turns. */
    private static void announce(ServerLevel level, ElysiumWeather.Spell spell) {
        ResourceLocation where = level.dimension().location();
        String now = spell.blowing() ? spell.kind().id() : "";
        String was = LAST.getOrDefault(where, "");
        if (now.equals(was)) {
            return;
        }
        LAST.put(where, now);
        if (level.players().isEmpty()) {
            return;             // nobody to tell; the sky changed anyway
        }
        Component message = spell.blowing()
                ? Component.translatable("elysium.weather.begins",
                        Component.translatable(spell.kind().messageKey()))
                        .withStyle(ChatFormatting.GOLD)
                : Component.translatable("elysium.weather.clears")
                        .withStyle(ChatFormatting.GRAY);
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, true);
        }
    }

    /**
     * What it looks like, for anybody who can see the sky.
     *
     * Only for players with sky above them: a storm drawn in a mine is a storm
     * drawn where the hazard pipeline has already decided it does not reach,
     * which would tell the player exactly the wrong thing.
     */
    private static void draw(ServerLevel level, ElysiumWeather.Spell spell) {
        ParticleOptions particle = particleFor(spell);
        int many = Math.max(1, Math.round(PARTICLES * spell.strength()));
        for (ServerPlayer player : level.players()) {
            BlockPos at = player.blockPosition();
            if (!level.canSeeSky(at)) {
                continue;
            }
            level.sendParticles(player, particle, false,
                    at.getX() + 0.5D, at.getY() + 3.0D, at.getZ() + 0.5D,
                    many, SPREAD, 2.0D, SPREAD, 0.02D);
        }
    }

    /** The look of a storm, taken from what it is doing to you. */
    private static ParticleOptions particleFor(ElysiumWeather.Spell spell) {
        if (spell.kind().hazard() == ElysiumHazards.THERMAL) {
            return ParticleTypes.FLAME;
        }
        if (spell.kind().hazard() == ElysiumHazards.CRYOGENIC) {
            return ParticleTypes.SNOWFLAKE;
        }
        if (spell.kind().hazard() == ElysiumHazards.CORROSIVE) {
            return ParticleTypes.ASH;
        }
        if (spell.kind().hazard() == ElysiumHazards.RADIOLOGICAL) {
            return ParticleTypes.GLOW;
        }
        if (spell.kind().hazard() == ElysiumHazards.PRESSURE) {
            return ParticleTypes.WHITE_ASH;
        }
        return ParticleTypes.SMOKE;
    }

    /** Forgets a world. Called when one unloads, purely to bound the map. */
    public static void forget(ResourceLocation dimension) {
        LAST.remove(dimension);
    }
}
