package com.elysium.space.tools;

import com.elysium.space.client.mob.Geometry;
import com.elysium.space.client.mob.Rig;
import net.minecraft.client.model.PiglinModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Bakes every mob's geometry with Minecraft's own classes (no game running):
 * each layer builds and bakes, every rig bone is a real part path, and the
 * cinderling's geometry satisfies vanilla's PiglinModel, which looks up its
 * parts by name. Run by validate.py when the Minecraft classpath is present.
 * Also reads each "sky:" world's sky file, as the client does when it
 * registers the dimension effects. Arguments: rigged mob names, "piglin:"
 * names, "sky:" worlds.
 */
public final class GeometryCheck {

    private GeometryCheck() {
    }

    public static void main(String[] args) {
        int failures = 0;
        for (String arg : args) {
            if (arg.startsWith("sky:")) {
                try {
                    com.elysium.space.client.sky.Sky sky = com.elysium.space.client.sky.Sky.of(arg.substring(4));
                    System.out.println(arg + ": " + sky.stars().count() + " stars, sun " + sky.sun().texture());
                } catch (RuntimeException e) {
                    failures++;
                    System.out.println("FAIL " + arg + ": " + e);
                }
                continue;
            }
            boolean piglin = arg.startsWith("piglin:");
            String mob = piglin ? arg.substring(7) : arg;
            try {
                Geometry geometry = Geometry.of(mob);
                ModelPart root = geometry.layer.bakeRoot();
                Rig rig = geometry.rig;
                if (rig.head() != null) {
                    part(root, rig.head());
                }
                for (Rig.Swing swing : rig.swings()) {
                    part(root, swing.bone());
                }
                if (piglin) {
                    new PiglinModel<>(root);
                }
                System.out.println(mob + ": " + root.getAllParts().count() + " parts bake"
                        + (piglin ? ", a valid piglin model" : ", rig resolves"));
            } catch (RuntimeException e) {
                failures++;
                System.out.println("FAIL " + mob + ": " + e);
            }
        }
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void part(ModelPart root, String path) {
        ModelPart part = root;
        for (String segment : path.split("/")) {
            part = part.getChild(segment);
        }
    }
}
