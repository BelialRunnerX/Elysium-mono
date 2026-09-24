package com.elysium.lib.discovery;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Who a discovery is reported to, and what that costs.
 *
 * <h2>The whole feature is this table</h2>
 *
 * §17.3, almost word for word: <blockquote>Filing with the Empire pays more and
 * raises Suspicion; filing with Unsworn networks pays less but increases Favor
 * and propagates charts; keeping the discovery private pays nothing. Naming
 * therefore remains a repeated low-stakes lesson in visibility versus
 * reward.</blockquote>
 *
 * "Repeated low-stakes" is the part that matters and the part that decides the
 * numbers below. This is not a choice a player makes once with a large
 * consequence; it is a choice they make every time they scan a rabbit, and the
 * amounts have to be small enough that getting it wrong is a shrug. What
 * accumulates is the pattern, not any one decision.
 *
 * <h2>Why private is on the list at all</h2>
 *
 * Because it has to be possible to find something and tell nobody. A player who
 * is already Marked and does not want the attention needs somewhere to put a
 * discovery that is not a choice between two meters both moving the wrong way —
 * and the catalogue still counts it, so the completionist is not punished for
 * being quiet.
 */
public enum ElysiumFiling {

    /**
     * Report it to the Empire.
     *
     * The most money and the attention that comes with it. Suspicion is
     * deliberately smaller than the emerald count is large: a player should be
     * able to file a hundred things and still not be Hunted, because §14.2's
     * bands are meant to be reached by what you do, not by what you catalogue.
     */
    EMPIRE("empire", 1.0F, 1, 0, ChatFormatting.GOLD),

    /**
     * Hand it to the Unsworn networks.
     *
     * Less money, and Favor. The charts propagate, which is flavour here and
     * the reason the payment is lower: the Unsworn are not buying exclusivity.
     */
    UNSWORN("unsworn", 0.5F, 0, 1, ChatFormatting.GREEN),

    /** Tell nobody. Costs nothing, pays nothing, still catalogued. */
    PRIVATE("private", 0.0F, 0, 0, ChatFormatting.GRAY);

    private final String id;
    private final float payShare;
    private final int suspicion;
    private final int favorMultiple;
    private final ChatFormatting colour;

    ElysiumFiling(String id, float payShare, int suspicion, int favorMultiple,
                  ChatFormatting colour) {
        this.id = id;
        this.payShare = payShare;
        this.suspicion = suspicion;
        this.favorMultiple = favorMultiple;
        this.colour = colour;
    }

    public String getId() {
        return id;
    }

    /** Emeralds for filing this kind of discovery here. */
    public int payFor(ElysiumDiscoveryKind kind) {
        return Math.round(kind.getEmeraldsWhenFiled() * payShare);
    }

    /** Suspicion added by filing here. */
    public int suspicionFor(ElysiumDiscoveryKind kind) {
        return suspicion;
    }

    /** Favor added by filing here. */
    public int favorFor(ElysiumDiscoveryKind kind) {
        return favorMultiple * kind.getFavorWhenUnsworn();
    }

    public ChatFormatting getColour() {
        return colour;
    }

    public Component getDisplayName() {
        return Component.translatable("elysium.discovery.filing." + id)
                .withStyle(colour);
    }

    /** The next one round the ring, for an item that cycles through them. */
    public ElysiumFiling next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
