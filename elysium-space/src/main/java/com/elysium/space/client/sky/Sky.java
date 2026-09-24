package com.elysium.space.client.sky;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A world's sky, from assets/elysiumspace/skies/&lt;world&gt;.json (ported from
 * Stellaris's Sky Aesthetics files by tools/port_skies.py). Read from our jar
 * when the dimension effects are registered, before resource packs load.
 * Sizes and heights are Sky Aesthetics's: a body is a square of half-width
 * {@code size} at distance {@code height}, so vanilla's sun is 30 at 100.
 */
public record Sky(Body sun, Body moon, boolean moonPhases, Stars stars, List<SkyObject> objects,
                  float sunset, boolean clouds, float cloudHeight, boolean weather,
                  boolean brightLightmap, boolean ambientLight) {

    public record Body(ResourceLocation texture, float size, float height) {
    }

    public record Stars(int count, float scale, float r, float g, float b, boolean always, boolean moving) {
    }

    public record SkyObject(Body body, float rx, float ry, float rz, boolean fixed) {
    }

    public static Sky of(String world) {
        String path = "/assets/elysiumspace/skies/" + world + ".json";
        try (InputStream in = Sky.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("no sky at " + path);
            }
            return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("bad sky " + path + ": " + e.getMessage(), e);
        }
    }

    static Body body(JsonObject o) {
        return new Body(ResourceLocation.parse(o.get("texture").getAsString()),
                o.get("size").getAsFloat(), o.get("height").getAsFloat());
    }

    static Sky parse(JsonObject o) {
        JsonObject s = o.getAsJsonObject("stars");
        var color = s.getAsJsonArray("color");
        Stars stars = new Stars(s.get("count").getAsInt(), s.get("scale").getAsFloat(),
                color.get(0).getAsFloat() / 255F, color.get(1).getAsFloat() / 255F, color.get(2).getAsFloat() / 255F,
                s.get("always").getAsBoolean(), s.get("moving").getAsBoolean());
        List<SkyObject> objects = new ArrayList<>();
        for (JsonElement el : o.getAsJsonArray("objects")) {
            JsonObject ob = el.getAsJsonObject();
            var rot = ob.getAsJsonArray("rotation");
            objects.add(new SkyObject(body(ob), rot.get(0).getAsFloat(), rot.get(1).getAsFloat(), rot.get(2).getAsFloat(),
                    ob.get("static").getAsBoolean()));
        }
        JsonObject moon = o.has("moon") ? o.getAsJsonObject("moon") : null;
        return new Sky(body(o.getAsJsonObject("sun")), moon == null ? null : body(moon),
                moon != null && moon.get("phases").getAsBoolean(), stars, List.copyOf(objects),
                o.get("sunset").getAsFloat(), o.get("clouds").getAsBoolean(), o.get("cloudHeight").getAsFloat(),
                o.get("weather").getAsBoolean(), o.get("brightLightmap").getAsBoolean(),
                o.get("ambientLight").getAsBoolean());
    }
}
