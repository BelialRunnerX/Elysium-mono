import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Build every block and item this repository registers, the way the game will.
 *
 * <h2>What this is for</h2>
 *
 * Every other check here reads a file or runs arithmetic. The load test runs
 * the game's <em>data</em> loaders. Nothing has ever run our <em>Java</em> —
 * and a block's constructor is where a surprising amount of a mod lives: the
 * properties it is built from, the attribute modifiers on an item, the block
 * set behind a door, the switch over a kind that grew a case nobody added.
 *
 * <p>Every one of those throws at exactly the moment FML fires the registry
 * event, which is four seconds into loading and before any screen appears, so
 * the only report is a crash log. This builds them all here instead.
 *
 * <h2>How</h2>
 *
 * A {@code DeferredRegister} keeps the suppliers it was handed in a private
 * map until FML asks for them. We ask for them. That is the whole trick, and
 * it means this cannot drift from what the game does: there is no second list
 * of things to build, only the same suppliers, called early.
 *
 * <p>What it cannot do is anything that needs the mod to have been
 * constructed, or another mod to exist. A supplier that reaches for a registry
 * FML has not populated will throw here and not in the game; those are named
 * on the command line and skipped, which is a short list and a visible one.
 */
public final class Construct {

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        thaw();

        List<String> classes = Files.readAllLines(Path.of(args[0]));
        List<String> skip = args.length > 1
                ? Files.readAllLines(Path.of(args[1])) : List.of();

        int registers = 0;
        int built = 0;
        List<String> broken = new ArrayList<>();
        List<Map.Entry<DeferredHolder<?, ?>, Supplier<?>>> waiting = new ArrayList<>();

        for (String name : classes) {
            name = name.trim();
            if (name.isEmpty() || name.startsWith("#")) {
                continue;
            }
            Class<?> owner;
            try {
                owner = Class.forName(name, true, Construct.class.getClassLoader());
            } catch (Throwable problem) {
                broken.add(name + " will not even load: " + describe(problem));
                continue;
            }
            for (Field field : owner.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())
                        || !DeferredRegister.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                DeferredRegister<?> register;
                try {
                    register = (DeferredRegister<?>) field.get(null);
                } catch (Throwable problem) {
                    broken.add(name + "." + field.getName() + " cannot be read: "
                            + describe(problem));
                    continue;
                }
                if (register == null) {
                    continue;
                }
                registers++;
                Field held = DeferredRegister.class.getDeclaredField("entries");
                held.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<DeferredHolder<?, ?>, Supplier<?>> suppliers =
                        (Map<DeferredHolder<?, ?>, Supplier<?>>) held.get(register);
                for (Map.Entry<DeferredHolder<?, ?>, Supplier<?>> entry
                        : suppliers.entrySet()) {
                    String id = String.valueOf(entry.getKey().getId());
                    if (!skip.contains(id)) {
                        waiting.add(entry);
                    }
                }
            }
        }

        // Round and round until a pass builds nothing new. Whatever is left
        // is genuinely broken rather than merely early.
        Map<String, String> lastComplaint = new LinkedHashMap<>();
        int passes = 0;
        while (!waiting.isEmpty()) {
            passes++;
            int before = waiting.size();
            List<Map.Entry<DeferredHolder<?, ?>, Supplier<?>>> again = new ArrayList<>();
            for (Map.Entry<DeferredHolder<?, ?>, Supplier<?>> entry : waiting) {
                String id = String.valueOf(entry.getKey().getId());
                try {
                    Object made = entry.getValue().get();
                    if (made == null) {
                        lastComplaint.put(id, "was built as nothing at all");
                        again.add(entry);
                        continue;
                    }
                    put(entry.getKey().getKey(), made);
                    built++;
                } catch (Throwable problem) {
                    lastComplaint.put(id, "will not build: " + describe(problem));
                    again.add(entry);
                }
            }
            // Bind what was registered this pass, so the next one can see it.
            for (Map.Entry<DeferredHolder<?, ?>, Supplier<?>> entry : waiting) {
                try {
                    Method bind = DeferredHolder.class
                            .getDeclaredMethod("bind", boolean.class);
                    bind.setAccessible(true);
                    bind.invoke(entry.getKey(), false);
                } catch (Throwable ignored) {
                    // Not yet bindable; the next pass will try again.
                }
            }
            waiting = again;
            if (waiting.size() == before) {
                break;
            }
        }
        for (Map.Entry<DeferredHolder<?, ?>, Supplier<?>> entry : waiting) {
            String id = String.valueOf(entry.getKey().getId());
            broken.add(id + " " + lastComplaint.getOrDefault(id, "will not build"));
        }

        for (String one : broken) {
            System.out.println("BROKEN " + one);
        }
        System.out.println("PASSES " + passes);
        System.out.println("REGISTERS " + registers);
        System.out.println("BUILT " + built);
        if (!broken.isEmpty()) {
            System.exit(1);
        }
    }

    /**
     * Open the game's registries back up.
     *
     * <p>{@code Bootstrap} freezes every built-in registry when it finishes,
     * and a vanilla {@code Block} constructor asks its registry for an
     * intrusive holder — so after bootstrap, building a block throws "Registry
     * is already frozen" before it has done anything interesting. FML does
     * its registering before the freeze; this undoes the freeze instead,
     * which reaches the same place.
     */
    /**
     * The four registries whose values hold a reference to their own holder.
     *
     * <p>A block, an item, a fluid and an entity type each ask their registry
     * for a holder in their own constructor. The rest do not, and handing one
     * of them the map that machinery uses makes it demand a holder for
     * everything registered afterwards — which reads as fifty broken items
     * and is this harness being wrong rather than the mod.
     */
    private static final Set<String> INTRUSIVE = Set.of(
            "minecraft:block", "minecraft:item", "minecraft:fluid",
            "minecraft:entity_type");

    private static void thaw() throws Exception {
        for (Object one : BuiltInRegistries.REGISTRY) {
            if (!(one instanceof MappedRegistry<?> registry)) {
                continue;
            }
            registry.unfreeze();
            if (!INTRUSIVE.contains(registry.key().location().toString())) {
                continue;
            }
            // unfreeze() clears the frozen flag; the map an intrusive holder
            // is put in is separate and is nulled by freeze(). Without it a
            // Block constructor throws on the next line instead of this one.
            Field intrusive = MappedRegistry.class
                    .getDeclaredField("unregisteredIntrusiveHolders");
            intrusive.setAccessible(true);
            if (intrusive.get(registry) == null) {
                intrusive.set(registry, new IdentityHashMap<>());
            }
        }
    }

    /**
     * Put a built thing into the registry it was declared for.
     *
     * <p>Not for tidiness: a block entity type is built from the blocks it is
     * valid for, and a creative tab from the items in it, so a supplier that
     * runs before the things it names are registered fails with "trying to
     * access an unbound value". Registering as we go, and going round again,
     * is how FML gets the same result without anybody writing the order down.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void put(ResourceKey<?> key, Object made) {
        Registry<?> target = (Registry<?>) BuiltInRegistries.REGISTRY
                .get(key.registry());
        if (target instanceof WritableRegistry writable
                && !writable.containsKey(key.location())) {
            writable.register((ResourceKey) key, made, RegistrationInfo.BUILT_IN);
        }
    }

    /** The first line of a throwable that says something, with its cause. */
    private static String describe(Throwable problem) {
        StringBuilder said = new StringBuilder();
        Throwable at = problem;
        int depth = 0;
        while (at != null && depth < 4) {
            if (depth > 0) {
                said.append(" <- ");
            }
            said.append(at.getClass().getSimpleName());
            if (at.getMessage() != null) {
                said.append(": ").append(at.getMessage().replace('\n', ' '));
            }
            at = at.getCause();
            depth++;
        }
        StackTraceElement[] where = problem.getStackTrace();
        for (StackTraceElement frame : where) {
            if (frame.getClassName().startsWith("com.elysium.")) {
                said.append("  at ").append(frame);
                break;
            }
        }
        return said.toString();
    }
}
