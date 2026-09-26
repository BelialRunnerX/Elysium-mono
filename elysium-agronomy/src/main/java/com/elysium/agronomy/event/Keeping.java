package com.elysium.agronomy.event;

import com.elysium.agronomy.ElysiumAgronomy;
import com.elysium.agronomy.entity.BeeEntity;
import com.elysium.agronomy.registry.AgronomyComponents;
import com.elysium.agronomy.registry.BeeItems;
import com.elysium.agronomy.rules.KeepingRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * The seam between a player working bees and the rules about their temper.
 *
 * <p>Taking a comb off a bee, or a bee out of the air or a nest, upsets the
 * bees within {@link KeepingRules#UPSET_RANGE}; the smoker calms every bee
 * within {@link KeepingRules#SMOKE_RANGE}. The harness holds the smoke to
 * reaching further and lasting longer than the anger, and these two methods
 * are where those numbers meet the world.
 */
@EventBusSubscriber(modid = ElysiumAgronomy.MODID)
public final class Keeping {

    private Keeping() {
    }

    /** Somebody took something from the bees here: the ones around notice. */
    public static void robbed(Level level, BlockPos at, Player by) {
        for (BeeEntity bee : level.getEntitiesOfClass(BeeEntity.class,
                new AABB(at).inflate(KeepingRules.UPSET_RANGE))) {
            bee.upset(by);
        }
    }

    /** Smoke here: every bee in reach is calm for a while. Returns how many. */
    public static int smoke(Level level, BlockPos at) {
        int calmed = 0;
        for (BeeEntity bee : level.getEntitiesOfClass(BeeEntity.class,
                new AABB(at).inflate(KeepingRules.SMOKE_RANGE))) {
            bee.smoke();
            calmed++;
        }
        return calmed;
    }

    /** The name of a species, as a player reads it. */
    public static Component named(String species) {
        return Component.translatable("entity." + ElysiumAgronomy.MODID + "." + species + "_bee");
    }

    /**
     * Which bee a stack is: a jar, or a bee out of a hive. Anything carrying
     * a species says so, whatever item it is.
     */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        String species = AgronomyComponents.speciesIn(stack);
        if (species != null) {
            event.getToolTip().add(Component.translatable("elysiumagronomy.jar.holds",
                    named(species)).withStyle(ChatFormatting.GOLD));
        } else if (stack.is(BeeItems.JAR.get())) {
            event.getToolTip().add(Component.translatable("elysiumagronomy.jar.empty")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
