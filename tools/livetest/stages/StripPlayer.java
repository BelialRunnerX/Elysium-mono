import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.nio.file.Path;

/** Copies a level.dat, removes the singleplayer host's Player tag so the next join spawns a fresh player,
 *  renames the world and pre-confirms the experimental-settings warning so quick play is not blocked by a GUI. */
public class StripPlayer {
    public static void main(String[] args) throws Exception {
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        String name = args[2];
        CompoundTag root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        CompoundTag data = root.getCompound("Data");
        boolean hadPlayer = data.contains("Player");
        data.remove("Player");
        data.putString("LevelName", name);
        data.putBoolean("confirmedExperimentalSettings", true);
        data.putBoolean("WasModded", true);
        data.putInt("GameType", 0);
        data.putBoolean("hardcore", false);
        CompoundTag wgs = data.getCompound("WorldGenSettings");
        if (args.length >= 6) {
            // The reference world's spawn is an ocean floor under the current biome
            // list, and a new player drowns reading the declaration screen there:
            // the launcher names a spot on land for this seed instead.
            data.putInt("SpawnX", Integer.parseInt(args[3]));
            data.putInt("SpawnY", Integer.parseInt(args[4]));
            data.putInt("SpawnZ", Integer.parseInt(args[5]));
        }
        NbtIo.writeCompressed(root, out);
        System.out.println("player removed=" + hadPlayer
                + " seed=" + wgs.getLong("seed")
                + " version=" + data.getCompound("Version").getString("Name")
                + " dims=" + wgs.getCompound("dimensions").getAllKeys()
                + " spawn=" + data.getInt("SpawnX") + "," + data.getInt("SpawnY") + "," + data.getInt("SpawnZ")
                + " datapacks=" + data.getCompound("DataPacks").getList("Enabled", 8).size()
                + " keys=" + data.getAllKeys());
    }
}
