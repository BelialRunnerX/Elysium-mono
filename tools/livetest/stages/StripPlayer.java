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
        // The reference is the dedicated server's own level.dat, which never had cheats on;
        // every stage drives the world with commands.
        data.putBoolean("allowCommands", true);
        // The server world was saved at whatever hour it was running at; a new
        // player joining at night under a modal screen was slain where they stood
        // (28 Sep). A fresh world starts at morning, in clear weather.
        data.putLong("Time", 0L);
        data.putLong("DayTime", 1000L);
        data.putBoolean("raining", false);
        data.putBoolean("thundering", false);
        data.putInt("rainTime", 24000);
        data.putInt("thunderTime", 24000);
        data.putInt("clearWeatherTime", 24000);
        CompoundTag wgs = data.getCompound("WorldGenSettings");
        if (args.length >= 6) {
            // The reference world's spawn is the one the game chose for its seed; a
            // launcher may still name another spot for a stage that needs one.
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
