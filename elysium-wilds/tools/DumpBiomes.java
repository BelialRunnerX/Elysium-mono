import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public final class DumpBiomes {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        HolderLookup.Provider lookup = VanillaRegistries.createLookup();
        HolderLookup.RegistryLookup<Biome> biomes = lookup.lookupOrThrow(Registries.BIOME);
        biomes.listElements().forEach(holder -> {
            String name = holder.key().location().toString();
            List<net.minecraft.core.HolderSet<PlacedFeature>> steps =
                    holder.value().getGenerationSettings().features();
            for (int step = 0; step < steps.size(); step++) {
                for (var one : steps.get(step)) {
                    System.out.println(name + "\t" + step + "\t"
                            + one.unwrapKey().map(k -> k.location().toString())
                                 .orElse("?"));
                }
            }
        });
    }
}
