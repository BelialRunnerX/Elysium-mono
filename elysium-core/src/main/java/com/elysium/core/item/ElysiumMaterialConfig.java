package com.elysium.core.item;

import com.elysium.core.Elysium;
import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.element.ElysiumElements;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extra materials, for an ore the shipped table missed.
 *
 * <h2>Why this needs a restart, and cannot not</h2>
 *
 * Items are registered during mod loading. A material added after that point
 * has nowhere to put its hammer. So this config is read in the mod constructor,
 * before {@link ElysiumMaterials#bootstrap()}, and a change to it takes effect
 * the next time the game starts — there is no way to make it live, and a
 * version that pretended to would be registering items into a frozen registry.
 *
 * <h2>Why adding one changes the registry, and what that means</h2>
 *
 * Adding a line here adds items. Removing one removes them, and any of that
 * material a player was holding becomes an unknown item. That is the honest
 * trade for supporting an arbitrary ore, and the reason the shipped table in
 * {@link ElysiumMaterials} is deliberately generous: a material that ships with
 * the mod is registered in every world whether or not the ore exists, so it can
 * never go missing.
 *
 * Prefer asking for a material to be added to the shipped table over adding it
 * here, if it is one other people will want too.
 *
 * <h2>Format</h2>
 *
 * One string per material, {@code name} or {@code name:tier} or
 * {@code name:tier:element}:
 *
 * <pre>
 * extra_materials = ["mythril:1:dimensional", "adamant:1", "copperzinc"]
 * </pre>
 *
 * The ingredient is always {@code c:ingots/&lt;name&gt;}, which is the
 * convention essentially every mod follows. A material whose ingot is not in
 * that tag will register gear that nothing can craft — which
 * {@code /elysium materials} will tell you about rather than leaving you to
 * wonder.
 */
public final class ElysiumMaterialConfig {

    private ElysiumMaterialConfig() {
    }

    /** One extra material, as parsed out of a config line. */
    public record Extra(String name, int tier, ElysiumElement element) {
    }

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA_MATERIALS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(
                "Extra gear materials, beyond the ones Elysium ships with.",
                "",
                "Format: \"name\", \"name:tier\", or \"name:tier:element\".",
                "  name    - the ingot's common tag name, so \"tin\" means c:ingots/tin",
                "  tier    - 0 (iron-grade) or 1 (diamond-grade). Default 0.",
                "  element - void, plasma, neural, dimensional or kinetic. Default: none,",
                "            which means the gear takes no side in the counter matrix and",
                "            no rune ever counts as aligned in it.",
                "",
                "A change here takes effect on the NEXT GAME START, not on reload: items",
                "are registered during mod loading and cannot be added after it.",
                "",
                "THIS FILE MUST MATCH ON THE SERVER AND ON EVERY CLIENT.",
                "It decides which items exist, so a client whose list differs from the",
                "server's is missing items the server has and will be kicked at login with",
                "a registry mismatch. Ship this file with your pack rather than letting",
                "players edit it.",
                "",
                "Removing a line removes those items. Anything a player was holding of that",
                "material becomes an unknown item, so treat this list as append-only once a",
                "world has been played.")
                .push("materials");
        EXTRA_MATERIALS = builder.defineList("extra_materials", List.of(),
                entry -> entry instanceof String text && parse(text) != null);
        builder.pop();
        SPEC = builder.build();
    }

    /**
     * The configured extras, skipping anything unparseable.
     *
     * A bad line is logged and dropped rather than thrown, because a typo in a
     * config file should not stop the game from starting — and the line it
     * prints names the entry, so it is fixable.
     *
     * <h2>Why this is read during mod construction, and what that costs</h2>
     *
     * This has to be readable before any item is registered, because what it
     * says decides which items exist. Only {@code ModConfig.Type.STARTUP} is
     * loaded that early — {@code COMMON} loads before {@code FMLCommonSetupEvent},
     * which is after construction, and reading it during construction throws
     * "Cannot get config value before config is loaded". That crash is exactly
     * what the first launch of this mod did.
     *
     * NeoForge's documentation advises against using STARTUP to change what a
     * mod registers, precisely because it is not synced: a client and server
     * with different lists have different item registries and the client is
     * kicked at login. That warning is correct and it applies here in full. It
     * is accepted rather than worked around, because the alternative is not
     * having the feature at all — there is no point in the lifecycle that is
     * both after config load and before registration. The config comment says
     * so in capitals, which is the honest mitigation.
     *
     * <h2>Why a failure here is not fatal</h2>
     *
     * Wrapped, so that a lifecycle mistake degrades to "no extra materials"
     * rather than to a mod that refuses to load. This feature is optional; the
     * game booting is not.
     */
    public static List<Extra> extras() {
        List<? extends String> configured;
        try {
            configured = EXTRA_MATERIALS.get();
        } catch (IllegalStateException notLoadedYet) {
            // Only reachable if this is ever called before the spec is
            // registered. Loud, because it means extra materials silently do
            // not exist, and quiet failures are how that goes unnoticed.
            Elysium.LOGGER.error("extra_materials was read before its config was loaded, so no "
                    + "extra materials will be registered. This is a load-order bug: the STARTUP "
                    + "config must be registered before ElysiumMaterials.bootstrap() runs.",
                    notLoadedYet);
            return List.of();
        }

        List<Extra> found = new ArrayList<>();
        for (String entry : configured) {
            Extra parsed = parse(entry);
            if (parsed == null) {
                Elysium.LOGGER.warn("Ignoring unparseable extra_materials entry \"{}\" - "
                        + "expected name, name:tier or name:tier:element", entry);
                continue;
            }
            if (com.elysium.lib.item.ElysiumGearMaterial.REGISTRY.get(
                    ResourceLocation.fromNamespaceAndPath(Elysium.MODID, parsed.name())) != null) {
                Elysium.LOGGER.warn("Ignoring extra_materials entry \"{}\" - Elysium already "
                        + "ships a material called that", entry);
                continue;
            }
            found.add(parsed);
        }
        return found;
    }

    /** @return the parsed entry, or null when the line is not usable */
    static Extra parse(String entry) {
        if (entry == null || entry.isBlank()) {
            return null;
        }
        String[] parts = entry.trim().toLowerCase(Locale.ROOT).split(":");
        String name = parts[0];
        // Registry paths allow [a-z0-9_.-]; anything else would fail later, at
        // registration, with a much less helpful message than this one.
        if (!name.matches("[a-z0-9_.-]+")) {
            return null;
        }

        int tier = 0;
        if (parts.length > 1) {
            try {
                tier = Integer.parseInt(parts[1]);
            } catch (NumberFormatException exception) {
                return null;
            }
            if (tier < 0 || tier > 1) {
                return null;
            }
        }

        ElysiumElement element = ElysiumElement.NONE;
        if (parts.length > 2) {
            element = ElysiumElement.REGISTRY.get(
                    ResourceLocation.fromNamespaceAndPath(ElysiumElements.NAMESPACE, parts[2]));
            if (element == null) {
                return null;
            }
        }

        return new Extra(name, tier, element);
    }
}
