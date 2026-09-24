import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;

import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Capture what Minecraft would have drawn, one animation frame at a time.
 *
 * <h2>Why this exists</h2>
 *
 * All eight mob families share one {@code setupAnim}, and giving each its own
 * walk was deferred with the note that "a distinctive walk is worth having and
 * is not worth guessing at without being able to watch it". That note is right.
 * This is the machinery for watching it.
 *
 * <h2>Why it captures rather than reimplements</h2>
 *
 * The obvious approach is to read the box tables out of gen_mobs.py, transcribe
 * setupAnim into Python and draw the result. That produces a second copy of the
 * animation which can silently disagree with the one in the game — which is the
 * exact failure this project has now hit three times (a stub that flattened
 * TieredItem, a stub that invented Component.withStyle, and a classpath that
 * was reading stubs while reporting that it read jars). A previewer that can
 * disagree with the shipped code is worth less than nothing, because it is
 * believed.
 *
 * So nothing here is reimplemented. Minecraft's own classes do all of it:
 *
 * <ol>
 *   <li>{@code createBodyLayer()} — the real generated mesh, called reflectively
 *       so this harness never needs updating when a model is added.</li>
 *   <li>{@code LayerDefinition.bakeRoot()} — the real baked part tree, with the
 *       real cubes and the real UVs.</li>
 *   <li>{@code setupAnim(...)} — the real animation, on the real ModelPart
 *       fields.</li>
 *   <li>{@code renderToBuffer(...)} — the real traversal, the real PoseStack
 *       maths, the real per-vertex transform.</li>
 * </ol>
 *
 * None of that touches OpenGL. {@code PoseStack} is JOML arithmetic and
 * {@code VertexConsumer} is an interface, so the whole pipeline runs headless
 * in a plain JVM. The only thing written here is a VertexConsumer that writes
 * the vertices down instead of drawing them.
 *
 * <h2>Output</h2>
 *
 * One JSON object per frame: a flat list of quads, each with four vertices in
 * model space after transformation, their texture coordinates, and the face
 * normal. Everything downstream is projection and rasterising, which is the
 * only part a renderer is allowed to get wrong on its own.
 *
 * <h2>Usage</h2>
 *
 * <pre>
 * java -cp ... PoseCapture &lt;modelClass&gt; &lt;frames&gt; &lt;out.json&gt;
 * </pre>
 */
public final class PoseCapture {

    /** One captured vertex, already transformed into model space. */
    private record V(float x, float y, float z, float u, float v) {
    }

    /**
     * A VertexConsumer that records instead of drawing.
     *
     * ModelPart.Cube.compile calls the eleven-argument default addVertex, which
     * delegates to these abstract methods in a fixed order: position, colour,
     * uv, overlay, light, normal. So a vertex is complete when the normal
     * arrives, and four complete vertices are a quad — the order Minecraft
     * emits cube faces in.
     */
    private static final class Capture implements VertexConsumer {
        final List<V> vertices = new ArrayList<>();
        private float x, y, z, u, v;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            // Last of the six, so the vertex is finished here.
            vertices.add(new V(x, y, z, u, v));
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int a, int b) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int a, int b) {
            return this;
        }
    }

    /** Depth-first search for a named part, so callers need not know the tree. */
    private static boolean hide(ModelPart part, String name, boolean visible) {
        try {
            ModelPart child = part.getChild(name);
            child.visible = visible;
            return true;
        } catch (Exception ignored) {
            // getChild throws when the name is not a direct child; recurse.
        }
        // ModelPart.children is private and NeoForge's access transformer does
        // not widen it, so the walk goes through reflection. Acceptable here
        // and nowhere else: this file is a harness that never ships.
        try {
            java.lang.reflect.Field field = ModelPart.class.getDeclaredField("children");
            field.setAccessible(true);
            java.util.Map<?, ?> children = (java.util.Map<?, ?>) field.get(part);
            for (Object value : children.values()) {
                if (hide((ModelPart) value, name, visible)) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException exc) {
            throw new IllegalStateException("cannot walk the part tree", exc);
        }
        return false;
    }

    /** One named command-line argument, or a default. */
    private static String argument(String[] args, String name, String fallback) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: PoseCapture <modelClass> <frames> <out.json>");
            System.exit(2);
        }
        Class<?> modelClass = Class.forName(args[0]);
        int frames = Integer.parseInt(args[1]);
        boolean bindPose = args.length > 3 && args[3].equals("--bind");

        // How hard the mob is moving, 0..1. This is NOT a constant to leave at
        // 1: vanilla feeds it the entity's actual ground speed, and a walking
        // humanoid sits near 0.65 while 1.0 is a sprint. Previewing at 1.0
        // makes every gait look like a caricature of itself, and "the arms are
        // too wide" is then a fact about the harness rather than the model.
        float amount = 0.65F;
        for (int i = 3; i < args.length - 1; i++) {
            if (args[i].equals("--amount")) {
                amount = Float.parseFloat(args[i + 1]);
            }
        }

        // --visible a,b,c : show only these optional parts, hiding every other
        // part named in --optional. Needed for elysium-npcs, where one model
        // carries every piece of regalia any of the five envoys wears and
        // setupAnim picks per entity - and there is no entity here.
        String show = "", optional = "";
        for (int i = 3; i < args.length - 1; i++) {
            if (args[i].equals("--visible")) {
                show = args[i + 1];
            } else if (args[i].equals("--optional")) {
                optional = args[i + 1];
            }
        }

        LayerDefinition layer =
                (LayerDefinition) modelClass.getMethod("createBodyLayer").invoke(null);
        ModelPart root = layer.bakeRoot();

        if (!optional.isEmpty()) {
            java.util.Set<String> wanted =
                    new java.util.HashSet<>(java.util.Arrays.asList(show.split(",")));
            for (String name : optional.split(",")) {
                hide(root, name, wanted.contains(name));
            }
        }

        Constructor<?> ctor = modelClass.getConstructor(ModelPart.class);
        Object model = ctor.newInstance(root);

        // A model may have costume to choose before it is posed. The court's
        // five envoys share one model and one texture sheet and differ only in
        // which wardrobe is visible, and that choice is deliberately not in
        // setupAnim — it is a constant for the life of an entity, and keeping
        // it out of the per-frame path is also what lets this previewer drive
        // the model with no entity at all.
        //
        // So: if the model offers a one-argument enum setter, call it with the
        // constant named by --kind. A model without one, which is every mob,
        // is unaffected.
        String kindName = argument(args, "--kind", null);
        for (Method m : modelClass.getMethods()) {
            if (m.getParameterCount() != 1 || !m.getParameterTypes()[0].isEnum()) {
                continue;
            }
            Class<?> enumType = m.getParameterTypes()[0];
            Object[] constants = enumType.getEnumConstants();
            if (constants == null || constants.length == 0) {
                continue;
            }
            Object chosen = constants[0];
            if (kindName != null) {
                for (Object constant : constants) {
                    if (constant.toString().equalsIgnoreCase(kindName)) {
                        chosen = constant;
                        break;
                    }
                }
            }
            m.invoke(model, chosen);
            break;
        }

        Method setupAnim = null;
        for (Method m : modelClass.getMethods()) {
            if (m.getName().equals("setupAnim") && m.getParameterCount() == 6) {
                setupAnim = m;
                break;
            }
        }
        if (setupAnim == null) {
            throw new IllegalStateException(modelClass + " has no setupAnim(entity, 5 floats)");
        }
        Method renderToBuffer = null;
        for (Method m : modelClass.getMethods()) {
            if (m.getName().equals("renderToBuffer") && m.getParameterCount() == 5) {
                renderToBuffer = m;
                break;
            }
        }
        if (renderToBuffer == null) {
            throw new IllegalStateException(modelClass + " has no renderToBuffer/5");
        }

        try (PrintWriter out = new PrintWriter(args[2], "UTF-8")) {
            out.println("{\"model\": \"" + modelClass.getSimpleName()
                    + "\", \"frames\": [");
            for (int frame = 0; frame < frames; frame++) {
                // One full stride over the frame range. 0.6662 is the constant
                // vanilla walks at, so a period is 2*PI/0.6662 ~= 9.43 units of
                // limbSwing; sweeping exactly that means frame 0 and frame N
                // are the same pose and the loop reads as a loop.
                float limbSwing = (float) (frame * (2.0 * Math.PI / 0.6662) / frames);
                float ageInTicks = frame;

                // entity is null: every generated setupAnim ignores it. If one
                // ever stops ignoring it this throws, loudly, which is the
                // right outcome — a previewer that quietly poses a mob using
                // default entity state is showing a pose the game never has.
                // --bind skips the animation entirely and captures the rest
                // pose. It exists for calibration: a vanilla model's bind pose
                // is something whose correct appearance is already known, and
                // vanilla's setupAnim reads live entity state that does not
                // exist here. The animation is Minecraft's own code and needs
                // no checking; the projection and UV mapping downstream are
                // mine, and do.
                if (!bindPose) {
                    setupAnim.invoke(model, null, limbSwing, amount, ageInTicks,
                            0.0F, 0.0F);
                }

                Capture capture = new Capture();
                renderToBuffer.invoke(model, new PoseStack(), capture,
                        0xF000F0, 0, 0xFFFFFFFF);

                out.print(frame > 0 ? ",\n" : "");
                out.print("  {\"frame\": " + frame + ", \"quads\": [");
                List<V> vs = capture.vertices;
                for (int i = 0; i + 3 < vs.size(); i += 4) {
                    out.print(i > 0 ? "," : "");
                    out.print("[");
                    for (int k = 0; k < 4; k++) {
                        V vertex = vs.get(i + k);
                        out.print(k > 0 ? "," : "");
                        out.printf("[%.5f,%.5f,%.5f,%.5f,%.5f]",
                                vertex.x(), vertex.y(), vertex.z(),
                                vertex.u(), vertex.v());
                    }
                    out.print("]");
                }
                out.print("]}");
            }
            out.println("\n]}");
        }
        System.out.println(modelClass.getSimpleName() + ": " + frames + " frames captured");
    }
}
