package com.elysium.lib.combat;

import com.elysium.lib.ElysiumLib;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The potions Apothic Attributes brews (AA-9) and the mixes that make them
 * (PL-4), in Elysium's names.
 *
 * <p>The mixes are data: {@code data/elysiumlib/brewing_mixes/*.json}, each one
 * {@code {"from": potion, "ingredient": item, "to": potion}}, listed in
 * {@link #MIXES}. They are read from the jar rather than through a datapack
 * because brewing is built once at startup, before any datapack is loaded —
 * vanilla's own mixes work the same way. A mix naming something that is not
 * registered is skipped with a line in the log, so one bad file cannot stop a
 * server booting.
 */
public final class ElysiumPotions {

    private ElysiumPotions() {
    }

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(BuiltInRegistries.POTION, ElysiumLib.MODID);

    private static DeferredHolder<Potion, Potion> potion(String name, Holder<MobEffect> effect,
                                                         int ticks, int amplifier) {
        return POTIONS.register(name, () -> new Potion(name,
                new MobEffectInstance(effect, ticks, amplifier)));
    }

    // Vanilla effects Apotheosis made brewable.
    public static final DeferredHolder<Potion, Potion> RESISTANCE = potion("resistance", MobEffects.DAMAGE_RESISTANCE, 3600, 0);
    public static final DeferredHolder<Potion, Potion> LONG_RESISTANCE = potion("long_resistance", MobEffects.DAMAGE_RESISTANCE, 9600, 0);
    public static final DeferredHolder<Potion, Potion> STRONG_RESISTANCE = potion("strong_resistance", MobEffects.DAMAGE_RESISTANCE, 1800, 1);
    public static final DeferredHolder<Potion, Potion> ABSORPTION = potion("absorption", MobEffects.ABSORPTION, 3600, 0);
    public static final DeferredHolder<Potion, Potion> LONG_ABSORPTION = potion("long_absorption", MobEffects.ABSORPTION, 9600, 0);
    public static final DeferredHolder<Potion, Potion> STRONG_ABSORPTION = potion("strong_absorption", MobEffects.ABSORPTION, 1800, 1);
    public static final DeferredHolder<Potion, Potion> HASTE = potion("haste", MobEffects.DIG_SPEED, 3600, 0);
    public static final DeferredHolder<Potion, Potion> LONG_HASTE = potion("long_haste", MobEffects.DIG_SPEED, 9600, 0);
    public static final DeferredHolder<Potion, Potion> STRONG_HASTE = potion("strong_haste", MobEffects.DIG_SPEED, 1800, 1);
    public static final DeferredHolder<Potion, Potion> FATIGUE = potion("fatigue", MobEffects.DIG_SLOWDOWN, 3600, 0);
    public static final DeferredHolder<Potion, Potion> LONG_FATIGUE = potion("long_fatigue", MobEffects.DIG_SLOWDOWN, 9600, 0);
    public static final DeferredHolder<Potion, Potion> WITHER = potion("wither", MobEffects.WITHER, 900, 0);
    public static final DeferredHolder<Potion, Potion> LONG_WITHER = potion("long_wither", MobEffects.WITHER, 1800, 0);
    public static final DeferredHolder<Potion, Potion> STRONG_WITHER = potion("strong_wither", MobEffects.WITHER, 432, 1);
    public static final DeferredHolder<Potion, Potion> LEVITATION = potion("levitation", MobEffects.LEVITATION, 900, 0);
    public static final DeferredHolder<Potion, Potion> LONG_LEVITATION = potion("long_levitation", MobEffects.LEVITATION, 1800, 0);

    // Elysium's own.
    public static final DeferredHolder<Potion, Potion> SUNDERING = potion("sundering", ElysiumEffects.SUNDERING, 3600, 0);
    public static final DeferredHolder<Potion, Potion> LONG_SUNDERING = potion("long_sundering", ElysiumEffects.SUNDERING, 9600, 0);
    public static final DeferredHolder<Potion, Potion> STRONG_SUNDERING = potion("strong_sundering", ElysiumEffects.SUNDERING, 1800, 1);
    public static final DeferredHolder<Potion, Potion> KNOWLEDGE = potion("knowledge", ElysiumEffects.KNOWLEDGE, 3600, 0);
    public static final DeferredHolder<Potion, Potion> LONG_KNOWLEDGE = potion("long_knowledge", ElysiumEffects.KNOWLEDGE, 9600, 0);
    public static final DeferredHolder<Potion, Potion> STRONG_KNOWLEDGE = potion("strong_knowledge", ElysiumEffects.KNOWLEDGE, 1800, 1);
    public static final DeferredHolder<Potion, Potion> VITALITY = potion("vitality", ElysiumEffects.VITALITY, 3600, 0);
    public static final DeferredHolder<Potion, Potion> LONG_VITALITY = potion("long_vitality", ElysiumEffects.VITALITY, 9600, 0);
    public static final DeferredHolder<Potion, Potion> STRONG_VITALITY = potion("strong_vitality", ElysiumEffects.VITALITY, 1800, 1);
    public static final DeferredHolder<Potion, Potion> GRIEVOUS = potion("grievous", ElysiumEffects.GRIEVOUS, 1800, 0);
    public static final DeferredHolder<Potion, Potion> LONG_GRIEVOUS = potion("long_grievous", ElysiumEffects.GRIEVOUS, 3600, 0);
    public static final DeferredHolder<Potion, Potion> STRONG_GRIEVOUS = potion("strong_grievous", ElysiumEffects.GRIEVOUS, 900, 1);
    public static final DeferredHolder<Potion, Potion> FLYING = potion("flying", ElysiumEffects.FLYING, 1800, 0);
    public static final DeferredHolder<Potion, Potion> LONG_FLYING = potion("long_flying", ElysiumEffects.FLYING, 3600, 0);

    /** Every mix file shipped in the jar. Kept as a list so the loader needs no directory walk. */
    public static final List<String> MIXES = List.of(
            "absorption", "absorption_long", "absorption_strong",
            "resistance", "resistance_long", "resistance_strong",
            "haste", "haste_long", "haste_strong",
            "fatigue", "fatigue_long",
            "wither", "wither_long", "wither_strong",
            "levitation", "levitation_long",
            "sundering", "sundering_long", "sundering_strong",
            "knowledge", "knowledge_long", "knowledge_strong",
            "vitality", "vitality_long", "vitality_strong",
            "grievous", "grievous_long", "grievous_strong",
            "flying", "flying_long");

    /**
     * The potions on the mod bus, the brewing recipes on the game bus.
     *
     * Two buses, and the difference is not a style choice: NeoForge refuses a
     * listener for an event that does not implement {@code IModBusEvent} on
     * the mod bus, at construction, with a crash that takes the whole game
     * down before the title screen. {@code RegisterBrewingRecipesEvent} is a
     * game-bus event — it is fired when a world's recipes are built, not while
     * mods are being constructed — and this line spent a build on the wrong
     * one. Nothing in the type system says so, because both buses take a
     * {@code Consumer} and the check happens at runtime; check_buses.py now
     * asks the jars the same question before the build.
     */
    public static void register(IEventBus modBus) {
        POTIONS.register(modBus);
        NeoForge.EVENT_BUS.addListener(ElysiumPotions::onBrewing);
    }

    /** One mix as it is written in its file. */
    public record Mix(ResourceLocation from, ResourceLocation ingredient, ResourceLocation to) {
    }

    /** Reads a mix file from the jar; null if it is missing or malformed. */
    public static Mix read(String name) {
        String path = "/data/" + ElysiumLib.MODID + "/brewing_mixes/" + name + ".json";
        try (InputStream in = ElysiumPotions.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return new Mix(ResourceLocation.parse(json.get("from").getAsString()),
                    ResourceLocation.parse(json.get("ingredient").getAsString()),
                    ResourceLocation.parse(json.get("to").getAsString()));
        } catch (IOException | RuntimeException e) {
            ElysiumLib.LOGGER.warn("brewing mix {} is unreadable: {}", name, e.toString());
            return null;
        }
    }

    private static void onBrewing(RegisterBrewingRecipesEvent event) {
        PotionBrewing.Builder builder = event.getBuilder();
        List<String> skipped = new ArrayList<>();
        for (String name : MIXES) {
            Mix mix = read(name);
            Holder<Potion> from = mix == null ? null : lookup(mix.from());
            Holder<Potion> to = mix == null ? null : lookup(mix.to());
            Item ingredient = mix == null ? null : BuiltInRegistries.ITEM.get(mix.ingredient());
            if (from == null || to == null || ingredient == null || ingredient == net.minecraft.world.item.Items.AIR) {
                skipped.add(name);
                continue;
            }
            builder.addMix(from, ingredient, to);
        }
        if (!skipped.isEmpty()) {
            ElysiumLib.LOGGER.warn("skipped {} brewing mix(es) naming something unregistered: {}",
                    skipped.size(), skipped);
        }
    }

    private static Holder<Potion> lookup(ResourceLocation id) {
        ResourceKey<Potion> key = ResourceKey.create(Registries.POTION, id);
        return BuiltInRegistries.POTION.getHolder(key).orElse(null);
    }
}
