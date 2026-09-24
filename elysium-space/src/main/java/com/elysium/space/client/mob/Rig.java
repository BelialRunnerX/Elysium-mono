package com.elysium.space.client.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a rigged model moves, from its geometry file's "rig": the bone that
 * looks where the mob looks, the limbs that swing (with walking, or idly with
 * age), an optional keyframe walk, and the one-shot keyframe animations the
 * entity starts by state name. Every part of a rig is optional.
 */
public record Rig(String head, boolean pitch, List<Swing> swings, Walk walk, Map<String, ResourceLocation> states) {

    public static final Rig NONE = new Rig(null, false, List.of(), null, Map.of());

    public enum Axis { X, Y, Z }

    /** angle = cos(t * rate + phase) * amp, t being limb swing (times its amount) or age in ticks. */
    public record Swing(String bone, Axis axis, boolean byWalk, float rate, float amp, float phase) {
    }

    public record Walk(ResourceLocation animation, float speed, float scale) {
    }

    static Rig parse(JsonObject o) {
        List<Swing> swings = new ArrayList<>();
        if (o.has("swing")) {
            for (JsonElement el : o.getAsJsonArray("swing")) {
                JsonObject s = el.getAsJsonObject();
                swings.add(new Swing(s.get("bone").getAsString(),
                        Axis.valueOf(s.get("axis").getAsString().toUpperCase(java.util.Locale.ROOT)),
                        "walk".equals(s.get("by").getAsString()), s.get("rate").getAsFloat(),
                        s.get("amp").getAsFloat(), s.get("phase").getAsFloat()));
            }
        }
        Walk walk = null;
        if (o.has("walk")) {
            JsonObject w = o.getAsJsonObject("walk");
            walk = new Walk(ResourceLocation.parse(w.get("animation").getAsString()),
                    w.get("speed").getAsFloat(), w.get("scale").getAsFloat());
        }
        Map<String, ResourceLocation> states = new LinkedHashMap<>();
        if (o.has("states")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("states").entrySet()) {
                states.put(e.getKey(), ResourceLocation.parse(e.getValue().getAsString()));
            }
        }
        return new Rig(o.has("head") ? o.get("head").getAsString() : null,
                o.has("pitch") && o.get("pitch").getAsBoolean(), List.copyOf(swings), walk, Map.copyOf(states));
    }
}
