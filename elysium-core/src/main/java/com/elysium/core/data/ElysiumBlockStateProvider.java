package com.elysium.core.data;

import com.elysium.core.Elysium;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Generates blockstates and block models.
 *
 * The 1.21.1 constructor takes a {@link PackOutput}, not a
 * {@code DataGenerator}.
 */
public class ElysiumBlockStateProvider extends BlockStateProvider {

    public ElysiumBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Elysium.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        simpleBlockWithItem(Elysium.NEUTRONIUM_BLOCK.get(), cubeAll(Elysium.NEUTRONIUM_BLOCK.get()));
        simpleBlockWithItem(Elysium.NEUTRONIUM_ORE.get(), cubeAll(Elysium.NEUTRONIUM_ORE.get()));
        simpleBlockWithItem(Elysium.AETHERIUM_ORE.get(), cubeAll(Elysium.AETHERIUM_ORE.get()));
        simpleBlockWithItem(Elysium.VOIDGLASS_ORE.get(), cubeAll(Elysium.VOIDGLASS_ORE.get()));
        simpleBlockWithItem(Elysium.REFORGE_TABLE.get(), cubeAll(Elysium.REFORGE_TABLE.get()));
        simpleBlockWithItem(Elysium.RUNE_SOCKET_TABLE.get(), cubeAll(Elysium.RUNE_SOCKET_TABLE.get()));
        simpleBlockWithItem(Elysium.ASCENSION_FORGE.get(), cubeAll(Elysium.ASCENSION_FORGE.get()));
    }
}
