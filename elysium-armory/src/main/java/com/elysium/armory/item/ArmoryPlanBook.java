package com.elysium.armory.item;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Which plans a book has been taught, as it rides on the stack.
 *
 * <p>A list of part ids rather than a container of items, and that is the
 * whole design: Silent Gear's blueprint book is a bag you keep forty pieces of
 * paper in, which solves the inventory problem and leaves you still hunting
 * through forty pieces of paper. A book that has <em>learned</em> a plan is one
 * item that answers for all of them, and a plan learned twice is learned once.
 */
public record ArmoryPlanBook(List<String> plans) {

    public static final ArmoryPlanBook BLANK = new ArmoryPlanBook(List.of());

    public static final Codec<ArmoryPlanBook> CODEC =
            Codec.STRING.listOf().xmap(ArmoryPlanBook::new, ArmoryPlanBook::plans);

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmoryPlanBook> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.<RegistryFriendlyByteBuf>cast()
                    .apply(ByteBufCodecs.list())
                    .map(ArmoryPlanBook::new, ArmoryPlanBook::plans);

    public ArmoryPlanBook {
        plans = List.copyOf(plans);
    }

    public boolean knows(String part) {
        return part != null && plans.contains(part);
    }

    /** The same book, having learned one more plan. Learning twice is once. */
    public ArmoryPlanBook learn(String part) {
        if (part == null || part.isBlank() || knows(part)) {
            return this;
        }
        List<String> more = new ArrayList<>(plans);
        more.add(part);
        more.sort(String::compareTo);
        return new ArmoryPlanBook(more);
    }

    public int size() {
        return plans.size();
    }

    public boolean isEmpty() {
        return plans.isEmpty();
    }
}
