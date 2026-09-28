package com.elysium.lib.sound;

import com.elysium.lib.ElysiumLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One sound event per clip in {@link ElysiumSoundRules}, registered from the
 * table rather than typed: a voice added to the rules is registered and
 * rendered and listed in {@code sounds.json} without anyone touching this.
 *
 * <p>Names: {@code elysiumlib:voice.<id>.<call>}, {@code elysiumlib:hum.<id>},
 * {@code elysiumlib:click.<id>}; the files sit at the same paths under
 * {@code sounds/}.
 */
public final class ElysiumSounds {

    private ElysiumSounds() {
    }

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, ElysiumLib.MODID);

    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> ALL = new LinkedHashMap<>();

    static {
        for (ElysiumSoundRules.Voice voice : ElysiumSoundRules.VOICES) {
            for (String call : ElysiumSoundRules.CALLS) {
                add("voice." + voice.id() + "." + call);
            }
        }
        for (ElysiumSoundRules.Hum hum : ElysiumSoundRules.HUMS) {
            add("hum." + hum.id());
        }
        for (ElysiumSoundRules.Click click : ElysiumSoundRules.CLICKS) {
            add("click." + click.id());
        }
    }

    private static void add(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, name);
        ALL.put(name, SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }

    /** How many events the table registers; the validator holds sounds.json to it. */
    public static int count() {
        return ALL.size();
    }

    /** The event for one name, or null for a name the table does not have. */
    public static SoundEvent get(String name) {
        DeferredHolder<SoundEvent, SoundEvent> holder = ALL.get(name);
        return holder == null ? null : holder.get();
    }

    /** A voice's idle call, or null when no voice is called that. */
    public static SoundEvent ambient(String voice) {
        return get("voice." + voice + ".ambient");
    }

    public static SoundEvent hurt(String voice) {
        return get("voice." + voice + ".hurt");
    }

    public static SoundEvent death(String voice) {
        return get("voice." + voice + ".death");
    }

    public static SoundEvent hum(String hum) {
        return get("hum." + hum);
    }

    public static SoundEvent click(String click) {
        return get("click." + click);
    }
}
