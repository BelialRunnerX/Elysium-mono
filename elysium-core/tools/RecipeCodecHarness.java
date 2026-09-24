package com.elysium.core.compat;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.codec.StreamCodec;

/**
 * What {@code unit} codecs actually compare, pinned.
 *
 * <h2>The bug this exists for</h2>
 *
 * A recipe that carries no data has both of its codecs written as units, and
 * the obvious spelling of that is two lines that read correctly side by side:
 *
 * <pre>
 *   MapCodec.unit(ConvergenceSmithingRecipe::new)
 *   StreamCodec.unit(new ConvergenceSmithingRecipe())
 * </pre>
 *
 * It compiled, it loaded, it worked in every single-player world, and it
 * disconnected every client that joined a server:
 *
 * <pre>
 *   EncoderException: Failed to encode packet 'clientbound/update_recipes'
 *   Caused by: IllegalStateException: Can't encode
 *       'ConvergenceSmithingRecipe@ed0714d', expected '...@24a731ce'
 * </pre>
 *
 * The map codec's method reference builds a <em>new</em> object when the recipe
 * file loads. {@code StreamCodec.unit} refuses to encode anything that is not
 * {@code equals} to the object it captured. Two units of the same type are not
 * the same unit unless something says so.
 *
 * <h2>Why this harness tests Mojang's code and not ours</h2>
 *
 * Because it cannot test ours. Touching the recipe class loads
 * {@code net.minecraft.world.item.crafting.Recipe}, whose initialiser reads
 * {@code BuiltInRegistries}, which needs a bootstrapped game — and
 * {@code Bootstrap.bootStrap()} needs FML's mod list, which needs a real
 * launch. So the round trip belongs in a game and the <em>rule</em> belongs in
 * elysium-core/validate.py, which enforces the shape the fix took.
 *
 * What is left for here is the fact that rule rests on: that {@code unit}
 * compares by {@code equals} and not by type. If a version bump ever made it
 * lenient, this fails and the source rule can be relaxed on purpose instead of
 * quietly being pointless.
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt)" -d /tmp/rch \
 *         elysium-core/tools/RecipeCodecHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/rch" \
 *         com.elysium.core.compat.RecipeCodecHarness
 */
public final class RecipeCodecHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    /** A stateless value with the default identity equals — the shape that broke. */
    static final class ByIdentity {
    }

    /** The same thing, answering equals the way a stateless value should. */
    static final class ByValue {
        @Override
        public boolean equals(Object other) {
            return other instanceof ByValue;
        }

        @Override
        public int hashCode() {
            return ByValue.class.hashCode();
        }
    }

    /**
     * Load from JSON through one unit codec, send through another.
     *
     * @return the throwable the send produced, or null if it went through
     */
    private static <T> Throwable roundTrip(MapCodec<T> map, StreamCodec<Object, T> stream) {
        Codec<T> codec = map.codec();
        T loaded = codec.parse(JsonOps.INSTANCE, new JsonObject()).result().orElse(null);
        if (loaded == null) {
            return new IllegalStateException("the map codec could not read an "
                    + "empty object, so this harness is testing nothing");
        }
        try {
            // A unit stream codec never reads its buffer, so null is the honest
            // argument — a real one would need a RegistryAccess and would test
            // nothing extra.
            stream.encode(null, loaded);
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    /** Identity equals plus a supplier is the combination that fails. */
    private static void twoUnitsAreNotOneUnit() {
        Throwable broken = roundTrip(
                MapCodec.unit(ByIdentity::new),
                StreamCodec.unit(new ByIdentity()));
        check(broken != null,
                "StreamCodec.unit accepted a different instance of the same "
                        + "type. It used to refuse, which is the whole reason "
                        + "elysium-core/validate.py forbids that spelling — if "
                        + "this is now allowed, relax the rule deliberately");
        if (broken != null) {
            check(broken instanceof IllegalStateException,
                    "the refusal arrived as " + broken.getClass().getSimpleName()
                            + " rather than IllegalStateException, so the "
                            + "failure a server reports has changed shape");
        }
        System.out.println("  the trap: a supplier plus identity equals is "
                + "refused at encode time, in the recipe-sync packet, on join");
    }

    /** ...and either half of the fix is enough to close it. */
    private static void theFixHolds() {
        check(roundTrip(MapCodec.unit(ByValue::new),
                        StreamCodec.unit(new ByValue())) == null,
                "answering equals is not enough to make two units interchangeable");

        ByIdentity singleton = new ByIdentity();
        check(roundTrip(MapCodec.unit(singleton),
                        StreamCodec.unit(singleton)) == null,
                "sharing one instance between both codecs is not enough either, "
                        + "so there is no safe spelling at all");

        System.out.println("  the fix: one shared instance, or an equals that "
                + "knows a stateless value is a value — the recipe uses both");
    }

    public static void main(String[] args) {
        twoUnitsAreNotOneUnit();
        theFixHolds();

        System.out.println("recipe codecs: what unit compares, and what closes "
                + "it — " + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("two units of the same type are not the same unit");
    }
}
