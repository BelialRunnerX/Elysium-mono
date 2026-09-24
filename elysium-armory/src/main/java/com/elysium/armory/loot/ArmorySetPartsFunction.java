package com.elysium.armory.loot;

import com.elysium.armory.item.ArmoryBuilder;
import com.elysium.armory.item.ArmoryGear;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;

import java.util.ArrayList;
import java.util.List;

/**
 * A loot function that builds a piece of modular gear out of named materials
 * (SG-64).
 *
 * <pre>
 * { "function": "elysiumarmory:set_parts",
 *   "materials": ["elysiumarmory:iron", "elysiumarmory:wood"] }
 * </pre>
 *
 * <h2>Why loot needs this at all</h2>
 *
 * A modular sword straight out of a loot table is an item with no parts: no
 * durability, no damage, no name, and nothing a player can repair. It is not a
 * weak sword, it is a broken one. This makes the item a real piece of gear
 * before it ever reaches a chest.
 *
 * <p>The materials fill the piece's parts in order, the last one repeating —
 * so one material makes a sword of that metal throughout, and two make a blade
 * of the first on a handle of the second.
 */
public class ArmorySetPartsFunction extends LootItemConditionalFunction {

    public static final MapCodec<ArmorySetPartsFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
            commonFields(instance).and(
                    com.mojang.serialization.Codec.STRING.listOf().fieldOf("materials")
                            .forGetter(function -> function.materials)
            ).apply(instance, ArmorySetPartsFunction::new));

    private final List<String> materials;

    protected ArmorySetPartsFunction(
            List<net.minecraft.world.level.storage.loot.predicates.LootItemCondition> conditions,
            List<String> materials) {
        super(conditions);
        this.materials = List.copyOf(materials);
    }

    @Override
    public LootItemFunctionType<? extends LootItemConditionalFunction> getType() {
        return ArmoryLoot.SET_PARTS.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        if (!(stack.getItem() instanceof ArmoryGear gear) || materials.isEmpty()) {
            return stack;
        }
        List<com.elysium.armory.material.ArmoryPart> parts =
                com.elysium.armory.material.ArmoryParts.get().forGear(gear.gearType());
        if (parts.isEmpty()) {
            return stack;
        }
        List<ItemStack> made = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            String material = materials.get(Math.min(i, materials.size() - 1));
            ItemStack part = ArmoryBuilder.part(parts.get(i).id(), List.of(material));
            if (!part.isEmpty()) {
                made.add(part);
            }
        }
        ItemStack built = ArmoryBuilder.gear(gear.gearType(), made);
        return built.isEmpty() ? stack : built;
    }
}
