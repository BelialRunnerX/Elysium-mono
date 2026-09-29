package com.elysium.armory.block;

import com.elysium.armory.item.ArmoryBlueprintItem;
import com.elysium.armory.menu.ArmorySmithingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import com.elysium.lib.sound.ElysiumVoices;

/**
 * The Gear Smithing Table (Silent Gear's SG-86): where a plan is drawn.
 *
 * <p>Blueprint paper goes in, a blueprint for whichever part you pick comes
 * out. The parts are datapack data the client does not have, so the list is
 * sent with the screen when it opens — which also means a pack that adds a part
 * needs no client of its own. The list is {@link ArmoryBlueprintItem#plannable},
 * the same one a found template draws from.
 */
public class ArmorySmithingTableBlock extends Block {

    public ArmorySmithingTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer server) {
            List<String> parts = ArmoryBlueprintItem.plannable();
            ElysiumVoices.click(level, pos, "station");
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
