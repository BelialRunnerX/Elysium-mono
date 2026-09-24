package com.elysium.armory.loot;

import com.elysium.lib.effect.ElysiumItemEffects;
import com.elysium.lib.item.ElysiumGearTree;
import com.elysium.lib.item.ElysiumLootCategory;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

import java.util.Optional;

/**
 * A loot condition about the tool in hand (SG-64).
 *
 * <pre>
 * { "condition": "elysiumarmory:gear", "kind": "harvest_tool", "trait": "lustrous", "level": 2 }
 * </pre>
 *
 * <p>Every field is optional and they narrow together: with none of them the
 * condition asks only that the tool is gear at all.
 *
 * <h2>Why a condition rather than a tag</h2>
 *
 * A pack author writing a loot table wants to say "if this was mined with a
 * hammer" or "if the tool carries Lustrous". Neither is a property of the item
 * that a tag could hold: the first is a kind in the shared tree, which a
 * modular hammer and a vanilla pickaxe both answer to, and the second is a
 * trait rolled onto the stack, which two items of the same type may differ on.
 *
 * <p>It reads the trait through the shared engine, so an <em>affix</em> named
 * here works exactly as a trait does. That is the point of there being one
 * engine.
 */
public record ArmoryGearCondition(Optional<String> kind, Optional<String> trait, int level)
        implements LootItemCondition {

    public static final MapCodec<ArmoryGearCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    com.mojang.serialization.Codec.STRING.optionalFieldOf("kind")
                            .forGetter(ArmoryGearCondition::kind),
                    com.mojang.serialization.Codec.STRING.optionalFieldOf("trait")
                            .forGetter(ArmoryGearCondition::trait),
                    com.mojang.serialization.Codec.INT.optionalFieldOf("level", 1)
                            .forGetter(ArmoryGearCondition::level)
            ).apply(instance, ArmoryGearCondition::new));

    @Override
    public LootItemConditionType getType() {
        return ArmoryLoot.GEAR.get();
    }

    @Override
    public boolean test(LootContext context) {
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        if (tool == null || tool.isEmpty()) {
            return false;
        }
        String resolved = ElysiumLootCategory.of(tool);
        if (resolved == null) {
            return false;
        }
        if (kind.isPresent() && !ElysiumGearTree.matches(resolved, kind.get())) {
            return false;
        }
        return trait.isEmpty() || ElysiumItemEffects.level(tool, trait.get()) >= level;
    }
}
