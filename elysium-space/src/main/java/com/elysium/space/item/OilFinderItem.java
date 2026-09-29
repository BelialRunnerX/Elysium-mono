package com.elysium.space.item;

import com.elysium.space.registry.SpaceData;
import com.elysium.space.world.MachineRules;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Stellaris's Oil Finder: used on a block, it says what the chunk under it
 * holds. The deposit is the pumpjack's own rule ({@link MachineRules#oilDeposit})
 * from the seed, or the chunk's count once a pumpjack has drawn on it, so the
 * finder and the pumpjack can never disagree about a chunk.
 */
public class OilFinderItem extends Item {

    public OilFinderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel server) || context.getPlayer() == null) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        LevelChunk chunk = server.getChunkAt(context.getClickedPos());
        int stored = chunk.getData(SpaceData.CHUNK_OIL);
        int deposit = stored < 0 ? MachineRules.oilDeposit(server.getSeed(), chunk.getPos().x, chunk.getPos().z) : stored;
        Component said;
        if (deposit <= 0) {
            said = Component.translatable("elysiumspace.oil_finder.dry");
        } else if (stored < 0) {
            said = Component.translatable("elysiumspace.oil_finder.found", deposit);
        } else {
            said = Component.translatable("elysiumspace.oil_finder.tapped", deposit);
        }
        context.getPlayer().displayClientMessage(said, true);
        return InteractionResult.CONSUME;
    }
}
