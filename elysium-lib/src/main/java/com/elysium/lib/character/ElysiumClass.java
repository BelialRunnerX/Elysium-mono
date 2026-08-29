package com.elysium.lib.character;

import com.elysium.lib.registry.ElysiumRegistry;
import com.elysium.lib.stats.ElysiumStatBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * What a character does. A job, rather than a biology.
 *
 * <h2>Class against race</h2>
 *
 * A class contributes growth and one passive, and no starting block — the
 * canonical nine give two points a level against a race's three, on the
 * principle that what you were born as should outweigh the job you took. That
 * is a convention, not a rule the library enforces, but it is the reason the
 * two are separate concepts at all: if a class gave as much as a race, there
 * would be no reason to have both.
 *
 * <h2>Registering one</h2>
 *
 * <pre>{@code
 * public static final ElysiumClass NAVIGATOR = ElysiumClass.register(
 *         ResourceLocation.fromNamespaceAndPath("mymod", "navigator"),
 *         ChatFormatting.AQUA,
 *         ElysiumStatBlock.of(ElysiumStats.INTELLECT, 1, ElysiumStats.LUCK, 1),
 *         new NavigatorPassive());
 * }</pre>
 *
 * As with races, the id is the persistence key and is permanent once played,
 * and a class whose mod is removed leaves the character intact and unclassed
 * rather than broken.
 */
public final class ElysiumClass {

    /** Every race in the game. Add-ons register here during construction. */
    public static final ElysiumRegistry<ElysiumClass> REGISTRY = new ElysiumRegistry<>("class");

    private final String translationKey;
    private final ChatFormatting colour;
    private final ElysiumStatBlock growth;
    private final ElysiumPassive passive;

    private ElysiumClass(String translationKey, ChatFormatting colour,
                         ElysiumStatBlock growth, ElysiumPassive passive) {
        this.translationKey = translationKey;
        this.colour = colour;
        this.growth = growth;
        this.passive = passive;
    }

    public static ElysiumClass register(ResourceLocation id, ChatFormatting colour,
                                        ElysiumStatBlock growth, ElysiumPassive passive) {
        String bare = id.getNamespace().equals("elysium") || id.getNamespace().equals("elysiumlib")
                ? id.getPath()
                : id.getNamespace() + "." + id.getPath();
        return REGISTRY.register(id,
                new ElysiumClass("elysium.class." + bare, colour, growth, passive));
    }

    /** What every level adds, on top of the race's own growth. */
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
