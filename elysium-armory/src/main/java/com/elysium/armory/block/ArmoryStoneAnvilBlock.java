package com.elysium.armory.block;

import com.elysium.armory.material.ArmoryCategory;
import com.elysium.armory.material.ArmoryPart;
import com.elysium.armory.material.ArmoryParts;
import com.elysium.armory.menu.ArmorySmithingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The Stone Anvil (Silent Gear's SG-85): the bench before there is a bench.
 *
 * <h2>The same screen, a shorter list</h2>
 *
 * A player who has not smelted anything yet still needs a way to plan a stone
 * axe. Rather than a second machine with its own rules, this is the Gear
 * Smithing Table's own screen offering only the shapes a rough material can
 * make — which is exactly what an anvil made of cobblestone should be able to
 * do, and nothing more.
 *
 * <p>Three cobblestone rows to build, no iron anywhere in it. It is the first
 * thing a player can make and the last thing they will use.
 */
public class ArmoryStoneAnvilBlock extends Block {

    public ArmoryStoneAnvilBlock(Properties properties) {
        super(properties);
    }

    /**
     * What counts as rough: the materials a player has before they have
     * smelted anything.
     */
    public static final java.util.Set<ArmoryCategory> ROUGH = java.util.Set.of(
            ArmoryCategory.ROCK, ArmoryCategory.WOOD, ArmoryCategory.ORGANIC,
            ArmoryCategory.FIBER);

    /**
     * The shapes a rough material can be beaten into on stone.
     *
     * A part that names no categories at all takes anything, so it is offered
     * too — refusing it would be the anvil being stricter than the recipe that
     * follows it, which is a way for a player to be told no twice.
     */
    public static List<String> roughParts() {
        List<String> parts = new ArrayList<>();
        for (ArmoryPart part : ArmoryParts.get().all().values()) {
            if (part.isUpgrade() || part.crafting().materials() <= 0) {
                continue;
            }
            java.util.Set<ArmoryCategory> wanted = part.crafting().categories();
            if (wanted.isEmpty() || !java.util.Collections.disjoint(wanted, ROUGH)) {
                parts.add(part.id());
            }
        }
        parts.sort(String::compareTo);
        return parts;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer server) {
            List<String> parts = roughParts();
            server.openMenu(new SimpleMenuProvider((id, inventory, p) ->
                    new ArmorySmithingMenu(id, inventory, parts), getName()),
                    buf -> {
                        buf.writeVarInt(parts.size());
                        parts.forEach(buf::writeUtf);
                    });
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
