package com.elysium.lib.character;

import com.elysium.lib.registry.ElysiumRegistry;
import com.elysium.lib.stats.ElysiumStatBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * What a character is. Chosen once, and permanent.
 *
 * <h2>What a race decides</h2>
 *
 * <ul>
 *   <li><b>A starting block.</b> Convention among the canonical six is that
 *       every race begins with the same total, spread differently, so nobody
 *       starts strictly ahead. The library does not enforce it — an add-on may
 *       have reasons — but a race that starts with twice everyone else's points
 *       is the only race anyone will pick.</li>
 *   <li><b>A growth curve.</b> Applied every level, so a race's shape sharpens
 *       rather than washing out.</li>
 *   <li><b>One passive.</b> Something no amount of points can buy.</li>
 * </ul>
 *
 * <h2>Registering one</h2>
 *
 * <pre>{@code
 * public static final ElysiumRace TIDEBORN = ElysiumRace.register(
 *         ResourceLocation.fromNamespaceAndPath("mymod", "tideborn"),
 *         ChatFormatting.BLUE,
 *         ElysiumStatBlock.of(ElysiumStats.AGILITY, 8, ElysiumStats.VITALITY, 6),
 *         ElysiumStatBlock.of(ElysiumStats.AGILITY, 2, ElysiumStats.REFLEXES, 1),
 *         new TidebornPassive());
 * }</pre>
 *
 * Do it from your mod's constructor. The id is the persistence key — a player's
 * race is stored as that string — so it is permanent once anyone has played it.
 *
 * <h2>What happens when the mod that added a race is removed</h2>
 *
 * The stored id stops resolving and {@code getRace} returns null, which every
 * consumer already handles: the character keeps its level, its spent points and
 * its gear, and loses the race's base stats, growth and passive until the mod
 * comes back. Nothing is deleted, and nothing crashes.
 */
public final class ElysiumRace {

    /** Every race in the game. Add-ons register here during construction. */
    public static final ElysiumRegistry<ElysiumRace> REGISTRY = new ElysiumRegistry<>("race");

    private final String translationKey;
    private final ChatFormatting colour;
    private final ElysiumStatBlock base;
    private final ElysiumStatBlock growth;
    private final ElysiumPassive passive;

    private ElysiumRace(String translationKey, ChatFormatting colour, ElysiumStatBlock base,
                        ElysiumStatBlock growth, ElysiumPassive passive) {
        this.translationKey = translationKey;
        this.colour = colour;
        this.base = base;
        this.growth = growth;
        this.passive = passive;
    }

    public static ElysiumRace register(ResourceLocation id, ChatFormatting colour,
                                       ElysiumStatBlock base, ElysiumStatBlock growth,
                                       ElysiumPassive passive) {
        String bare = id.getNamespace().equals("elysium") || id.getNamespace().equals("elysiumlib")
                ? id.getPath()
                : id.getNamespace() + "." + id.getPath();
        return REGISTRY.register(id,
                new ElysiumRace("elysium.race." + bare, colour, base, growth, passive));
    }

    /** The stats a character of this race starts with at level 1. */
    public ElysiumStatBlock getBaseStats() {
        return base;
    }

    /** What every level adds, before class growth and free points. */
    public ElysiumStatBlock getGrowth() {
        return growth;
    }

    public ElysiumPassive getPassive() {
        return passive;
    }

    public ChatFormatting getColour() {
        return colour;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey).withStyle(colour);
    }

    public Component getDescription() {
        return Component.translatable(translationKey + ".desc").withStyle(ChatFormatting.GRAY);
    }

    public ResourceLocation getId() {
        return REGISTRY.idOf(this);
    }

    /** The id as a string, which is how a race is stored on a player. */
    public String getSerialisedName() {
        ResourceLocation id = getId();
        return id == null ? "" : id.toString();
    }

    @Override
    public String toString() {
        return getSerialisedName();
    }
}
