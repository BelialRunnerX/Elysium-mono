package com.elysium.lib.trinket;

import com.elysium.lib.character.ElysiumPassive;
import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.registry.ElysiumRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A thing worn in an accessory slot, and what wearing it does.
 *
 * <h2>Why the library has no idea what Curios is</h2>
 *
 * Slot APIs are containers. Curios, Accessories and a hand-rolled inventory all
 * answer exactly one question — <em>what is this player wearing?</em> — and
 * none of them has an opinion about elements, ascension tiers, standing or
 * anything else Elysium cares about. So the container stays outside: a content
 * mod installs a {@link ElysiumTrinkets#setProvider provider} that answers that
 * one question, and everything interesting happens here, in terms the rest of
 * the engine already speaks.
 *
 * That is not only tidiness. It means the choice of slot API is one class wide
 * and can be changed later without touching a single trinket, and it means the
 * library still builds and runs with no accessory mod installed at all — the
 * provider is simply absent and nobody is wearing anything.
 *
 * <h2>Trinkets are passives</h2>
 *
 * A trinket's behaviour is an {@link ElysiumPassive}, the same interface a race
 * or a class answers. There is no separate trinket-effect system, no second set
 * of hooks and no ordering question between the two, because there is only one
 * mechanism: {@code ElysiumCharacter.passives(player)} returns the race's, the
 * class's, and every equipped trinket's, and the existing combinators multiply
 * or combine them exactly as they always did.
 *
 * The practical consequence is that a hook added for a trinket is immediately
 * available to races, classes and anything else — and that a trinket can do
 * anything a class passive can, which is the whole point.
 *
 * <h2>Ascension, and why the passive is a function of tier</h2>
 *
 * A craftable trinket ascends without a ceiling, so its effect has to know how
 * far it has been taken. An {@link ElysiumPassive} only ever sees the player,
 * never the stack it came from, so the tier cannot be discovered from inside
 * one. Instead the trinket <em>produces</em> a passive for a given tier, and the
 * provider reports the tier alongside the trinket.
 *
 * Unique trinkets ignore the argument and return the same object every time,
 * which is why {@link #unique} exists — it is the common case and it should not
 * require writing a lambda that discards its parameter.
 *
 * <h2>Registering one</h2>
 *
 * <pre>{@code
 * public static final ElysiumTrinket SIGNET = ElysiumTrinket.register(
 *         ResourceLocation.fromNamespaceAndPath("mymod", "iron_signet"),
 *         ElysiumElement.NONE,
 *         "ring",
 *         0,
 *         tier -> new SignetPassive(tier));
 * }</pre>
 *
 * From your mod's constructor, like everything else. The id is the persistence
 * key, so it is permanent once anyone has worn one.
 */
public final class ElysiumTrinket {

    /** Every trinket any Elysium mod can put on a player. */
    public static final ElysiumRegistry<ElysiumTrinket> REGISTRY = new ElysiumRegistry<>("trinket");

    /**
     * Produces the behaviour of a trinket at a given ascension tier.
     *
     * Called on every hook, so it should be cheap — return a cached object per
     * tier rather than building one. {@link #unique} does this for the trinkets
     * that do not ascend at all.
     */
    @FunctionalInterface
    public interface Behaviour {
        ElysiumPassive at(int ascension);
    }

    private final ResourceLocation id;
    private final ElysiumElement element;
    private final String slot;
    private final int levelRequirement;
    private final Behaviour behaviour;

    private ElysiumTrinket(ResourceLocation id, ElysiumElement element, String slot,
                           int levelRequirement, Behaviour behaviour) {
        this.id = id;
        this.element = element;
        this.slot = slot;
        this.levelRequirement = levelRequirement;
        this.behaviour = behaviour;
    }

    public static ElysiumTrinket register(ResourceLocation id, ElysiumElement element,
                                          String slot, int levelRequirement,
                                          Behaviour behaviour) {
        if (behaviour == null) {
            throw new IllegalArgumentException("trinket " + id + " has no behaviour; "
                    + "a trinket that does nothing is an item, not a trinket");
        }
        ElysiumTrinket trinket = new ElysiumTrinket(
                id, element == null ? ElysiumElement.NONE : element,
                slot, Math.max(0, levelRequirement), behaviour);
        REGISTRY.register(id, trinket);
        return trinket;
    }

    /**
     * A behaviour that does not change with ascension.
     *
     * The unique trinkets are all of this shape: they are found, not built, and
     * what they do is a rule rather than a number, so there is nothing for a
     * tier to scale.
     */
    public static Behaviour unique(ElysiumPassive passive) {
        return ascension -> passive;
    }

    // ------------------------------------------------------------------

    public ResourceLocation getId() {
        return id;
    }

    public ElysiumElement getElement() {
        return element;
    }

    /**
     * The slot this belongs in, as a bare string.
     *
     * A string rather than an enum because the set of slots is decided by
     * whichever accessory mod is installed, and the library has no business
     * having an opinion about it. The adapter matches this against its own slot
     * ids; anything it does not recognise simply never gets equipped.
     */
    public String getSlot() {
        return slot;
    }

    /** The character level below which this does nothing. Zero for no gate. */
    public int getLevelRequirement() {
        return levelRequirement;
    }

    public ElysiumPassive passiveAt(int ascension) {
        return behaviour.at(Math.max(0, ascension));
    }

    public Component getDisplayName() {
        return Component.translatable("trinket." + id.getNamespace() + "." + id.getPath());
    }

    public Component getDescription() {
        return Component.translatable("trinket." + id.getNamespace() + "." + id.getPath() + ".desc");
    }

    @Override
    public String toString() {
        return "ElysiumTrinket[" + id + "]";
    }
}
