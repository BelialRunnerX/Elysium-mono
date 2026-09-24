package com.elysium.armory.craft;

import com.elysium.armory.item.ArmoryMarks;
import com.elysium.armory.item.ArmoryPartItem;
import com.elysium.armory.property.ArmoryMaterialScience;
import com.elysium.armory.registry.ArmoryComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Grading a part (Silent Gear's SG-81), and charging one (SG-82).
 *
 * <p>Both write to the same component and neither touches the other's mark,
 * because they answer different questions: how well a part was worked, and
 * what it was left out in.
 */
public final class ArmoryGrading {

    private ArmoryGrading() {
    }

    public static boolean isPart(ItemStack stack) {
        return stack.getItem() instanceof ArmoryPartItem;
    }

    public static ArmoryMarks marks(ItemStack stack) {
        return stack.getOrDefault(ArmoryComponents.MARKS.get(), ArmoryMarks.PLAIN);
    }

    /** Whether there is a grade left to work this part up to. */
    public static boolean canGrade(ItemStack part) {
        return isPart(part) && marks(part).grade() < ArmoryMaterialScience.MAX_GRADE;
    }

    /**
     * The part, graded.
     *
     * The roll decides, and the rules decide what a failed roll costs: an
     * ungraded part can come out Crude, and a part already worked up is left
     * where it is rather than demoted — losing hours to one roll is a
     * punishment rather than a risk.
     */
    public static ItemStack grade(ItemStack part, RandomSource random) {
        if (!canGrade(part)) {
            return ItemStack.EMPTY;
        }
        ArmoryMarks marks = marks(part);
        int graded = ArmoryMaterialScience.graded(marks.grade(), random.nextDouble());
        ItemStack out = part.copy();
        out.setCount(1);
        out.set(ArmoryComponents.MARKS.get(), marks.withGrade(graded));
        return out;
    }

    /** Whether this part has yet to take the charge. */
    public static boolean canCharge(ItemStack part) {
        return isPart(part) && !marks(part).starcharged();
    }

    /** The part, charged. It happens once. */
    public static ItemStack charge(ItemStack part) {
        if (!canCharge(part)) {
            return ItemStack.EMPTY;
        }
        ItemStack out = part.copy();
        out.setCount(1);
        out.set(ArmoryComponents.MARKS.get(), marks(part).charged());
        return out;
    }
}
