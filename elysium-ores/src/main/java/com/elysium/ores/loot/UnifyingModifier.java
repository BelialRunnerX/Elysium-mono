package com.elysium.ores.loot;

import com.elysium.ores.unify.OreUnification;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

/**
 * Whatever a table was going to drop, of the material this pack settled on.
 *
 * <h2>Why loot and not everything</h2>
 *
 * Loot is the one place in the game where "here is a list of stacks, hand back
 * the list you want" is the actual interface. So it is the one place a
 * unifier can replace an item without guessing at somebody else's bookkeeping.
 * Every other mod's ore, every chest, every mob: whatever they were going to
 * give, the player gets the pack's version of it, and the six kinds of raw
 * lead never accumulate in the first place.
 *
 * <p>The count is kept and the components are not. A variant's components
 * belong to that variant's item; carrying them onto a different one is how a
 * unifier produces something nobody's recipe expects.
 *
 * <h2>What it will not touch</h2>
 *
 * Anything {@link OreUnification#isAmbiguous} protects — an item that answers
 * to two families at once, which is a tagging mistake somewhere and not a
 * licence to turn somebody's biosteel into steel.
 *
 * <p>And anything at all, before the map has been computed. An empty map means
 * every stack passes through untouched, which is the correct behaviour during
 * the window before tags have settled rather than a failure to handle.
 */
public class UnifyingModifier extends LootModifier {

    public static final MapCodec<UnifyingModifier> CODEC =
            RecordCodecBuilder.mapCodec(instance ->
                    codecStart(instance).apply(instance, UnifyingModifier::new));

    public UnifyingModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot,
                                                 LootContext context) {
        if (OreUnification.all().isEmpty()) {
            return loot;
        }
        for (int at = 0; at < loot.size(); at++) {
            ItemStack stack = loot.get(at);
            ItemStack unified = OreUnification.unify(stack);
            if (unified != stack) {
                loot.set(at, unified);
            }
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
