package com.elysium.lib.element;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.registry.ElysiumRegistry;
import com.elysium.lib.stats.ElysiumStat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A psionic element, and what it answers.
 *
 * <h2>From a closed cycle to a declared graph</h2>
 *
 * The five canonical elements form a ring: each is strong against the two that
 * precede it. That was expressed as arithmetic on an enum's ordinal — elegant,
 * and impossible to extend, because a sixth element has nowhere to sit in a
 * five-cycle without changing what every existing matchup means.
 *
 * So the relationship is declared rather than computed. Each element names the
 * elements it beats. The canonical five declare exactly the ring they always
 * had — {@link ElysiumElements} is the proof, and its numbers are unchanged —
 * and an add-on's element declares whatever it likes, including nothing.
 *
 * <h2>Why the targets are ids, not elements</h2>
 *
 * Registration order between mods is not knowable. An element that referred
 * directly to another would force whoever registers second to exist first.
 * Ids are resolved on the first query instead, by which time everything has
 * registered, and an id naming an element nobody installed simply never
 * matches.
 */
public final class ElysiumElement {

    /** Every element in the game. Add-ons register here during construction. */
    public static final ElysiumRegistry<ElysiumElement> REGISTRY = new ElysiumRegistry<>("element");

    /**
     * The absence of an affinity. Never strong against anything, never weak to
     * anything, and never registered — inert gear has no element rather than a
     * special one, and the difference matters when iterating the registry.
     */
    public static final ElysiumElement NONE =
            new ElysiumElement("none", ChatFormatting.GRAY, Set.of(), List.of());

    private final String translationKey;
    private final ChatFormatting colour;
    private final Set<ResourceLocation> beatsIds;
    private final List<ElysiumStat> grants;

    private Set<ElysiumElement> beats;

    private ElysiumElement(String bareKey, ChatFormatting colour,
                           Set<ResourceLocation> beats, List<ElysiumStat> grants) {
        this.translationKey = "elysium.element." + bareKey;
        this.colour = colour;
        this.beatsIds = Set.copyOf(beats);
        this.grants = List.copyOf(grants);
    }

    /**
     * Registers an element.
     *
     * @param id     the element's id; the namespace should be your mod's
     * @param colour how it is drawn in tooltips
     * @param beats  the ids of the elements this one has the advantage over.
     *               They need not be registered yet, and need not exist at all.
     * @param grants the stats gear of this affinity strengthens
     */
    public static ElysiumElement register(ResourceLocation id, ChatFormatting colour,
                                          Set<ResourceLocation> beats,
                                          List<ElysiumStat> grants) {
        ElysiumElement element = new ElysiumElement(
                id.getNamespace().equals(ElysiumLib.MODID) || id.getNamespace().equals("elysium")
                        ? id.getPath()
                        : id.getNamespace() + "." + id.getPath(),
                colour, beats, grants);
        return REGISTRY.register(id, element);
    }

    /**
     * The stats a piece of gear with this affinity grants, one helping of each
     * per tier.
     *
     * Gear used to map element to stats with a switch, which meant a new
     * element granted nothing and there was no error to notice — the switch
     * simply fell through to its default. An element declares its own now.
     */
    public List<ElysiumStat> getGrantedStats() {
        return grants;
    }

    // ------------------------------------------------------------------

    /** True when this element has the advantage over {@code other}. */
    public boolean isStrongAgainst(ElysiumElement other) {
        if (other == null || other == NONE || this == NONE) {
            return false;
        }
        return resolved().contains(other);
    }

    /** The elements this one beats — what a tooltip lists as "answers". */
    public Set<ElysiumElement> counters() {
        return resolved();
    }

    /**
     * Resolved lazily and cached. The first call happens well after every mod
     * has registered, because nothing asks about a matchup until something is
     * swung at something else.
     */
    private Set<ElysiumElement> resolved() {
        Set<ElysiumElement> cached = beats;
        if (cached == null) {
            Set<ElysiumElement> found = new LinkedHashSet<>();
            for (ResourceLocation id : beatsIds) {
                ElysiumElement target = REGISTRY.get(id);
                if (target != null) {
                    found.add(target);
                }
            }
            cached = Set.copyOf(found);
            beats = cached;
        }
        return cached;
    }

    public boolean isElemental() {
        return this != NONE;
    }

    public ChatFormatting getColour() {
        return colour;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey).withStyle(colour);
    }

    /** The registered id, or null for {@link #NONE}. */
    public ResourceLocation getId() {
        return REGISTRY.idOf(this);
    }

    /**
     * For logs and debugging only — nothing persists an element this way.
     *
     * NONE is deliberately never registered (it is the absence of an element,
     * not one of them), so it has no id of its own and names itself in the
     * library's own namespace rather than a content mod's.
     */
    @Override
    public String toString() {
        ResourceLocation id = getId();
        return id == null ? ElysiumLib.MODID + ":none" : id.toString();
    }
}
