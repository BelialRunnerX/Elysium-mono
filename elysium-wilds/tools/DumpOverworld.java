package net.minecraft.world.level.biome;

/** Ask the game itself what the overworld's parameter list actually is. */
public final class DumpOverworld {
    public static void main(String[] args) {
        OverworldBiomeBuilder builder = new OverworldBiomeBuilder();
        builder.addBiomes(pair -> {
            Climate.ParameterPoint p = pair.getFirst();
            StringBuilder line = new StringBuilder();
            line.append(pair.getSecond().location());
            for (Climate.Parameter one : new Climate.Parameter[] {
                    p.temperature(), p.humidity(), p.continentalness(),
                    p.erosion(), p.depth(), p.weirdness() }) {
                line.append('\t').append(one.min()).append('\t').append(one.max());
            }
            line.append('\t').append(p.offset());
            System.out.println(line);
        });
    }
}
