package com.elysium.space.client.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A mob's geometry, read from assets/elysiumspace/geometry/&lt;mob&gt;.json (written
 * by tools/port_mobs.py from Stellaris's models): the layer definition vanilla
 * bakes, and the rig RiggedModel moves it with.
 *
 * <p>Read from our own jar rather than through resource packs: layer
 * definitions are built when entity models are baked, and a resource pack
 * that changes a model's shape would also have to change its texture layout.
 * A missing or broken file fails loudly, naming the file.
 */
public final class Geometry {

    public final LayerDefinition layer;
    public final Rig rig;

    private Geometry(LayerDefinition layer, Rig rig) {
        this.layer = layer;
        this.rig = rig;
    }

    private static final Map<String, Geometry> CACHE = new HashMap<>();

    public static synchronized Geometry of(String mob) {
        return CACHE.computeIfAbsent(mob, Geometry::read);
    }

    private static Geometry read(String mob) {
        String path = "/assets/elysiumspace/geometry/" + mob + ".json";
        try (InputStream in = Geometry.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("no geometry at " + path);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return new Geometry(layer(root), root.has("rig") ? Rig.parse(root.getAsJsonObject("rig")) : Rig.NONE);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("bad geometry " + path + ": " + e.getMessage(), e);
        }
    }

    static LayerDefinition layer(JsonObject root) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> made = new HashMap<>();
        made.put("", mesh.getRoot());
        JsonObject parts = root.getAsJsonObject("parts");
        List<String> order = new ArrayList<>(parts.keySet());
        order.sort(Comparator.comparingInt(p -> p.split("/").length));     // parents before children
        for (String path : order) {
            JsonObject part = parts.getAsJsonObject(path);
            PartDefinition parent = made.get(part.get("parent").getAsString());
            if (parent == null) {
                throw new IllegalStateException(path + " names a parent that is not a part");
            }
            CubeListBuilder cubes = CubeListBuilder.create();
            for (JsonElement el : part.getAsJsonArray("cubes")) {
                JsonObject c = el.getAsJsonObject();
                float[] uv = floats(c.getAsJsonArray("uv"));
                float[] o = floats(c.getAsJsonArray("origin"));
                float[] size = floats(c.getAsJsonArray("size"));
                CubeDeformation grow = new CubeDeformation(c.get("inflate").getAsFloat());
                cubes.texOffs((int) uv[0], (int) uv[1]).mirror(c.get("mirror").getAsBoolean());
                if (c.has("texScale")) {
                    float[] ts = floats(c.getAsJsonArray("texScale"));
                    cubes.addBox(o[0], o[1], o[2], size[0], size[1], size[2], grow, ts[0], ts[1]);
                } else {
                    cubes.addBox(o[0], o[1], o[2], size[0], size[1], size[2], grow);
                }
            }
            float[] pose = floats(part.getAsJsonArray("pose"));
            String name = path.substring(path.lastIndexOf('/') + 1);
            made.put(path, parent.addOrReplaceChild(name, cubes,
                    PartPose.offsetAndRotation(pose[0], pose[1], pose[2], pose[3], pose[4], pose[5])));
        }
        float[] tex = floats(root.getAsJsonArray("texture"));
        return LayerDefinition.create(mesh, (int) tex[0], (int) tex[1]);
    }

    static float[] floats(JsonArray array) {
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsFloat();
        }
        return out;
    }
}
