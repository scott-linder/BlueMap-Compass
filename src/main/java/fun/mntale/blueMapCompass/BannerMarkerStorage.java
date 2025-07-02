package fun.mntale.blueMapCompass;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BannerMarkerStorage {
    private final File file;
    private final FileConfiguration config;

    public BannerMarkerStorage(Plugin plugin) {
        this.file = new File(plugin.getDataFolder(), "banner-markers.yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void addMarker(String world, int x, int y, int z, String name, String color, String markerId, String placerUuid, String placerName) {
        List<String> list = config.getStringList("banner-markers");
        String entry = world + "," + x + "," + y + "," + z + "," + name.replace(",", "<comma>") + "," + color + "," + markerId + "," + placerUuid + "," + placerName.replace(",", "<comma>");
        list.add(entry);
        config.set("banner-markers", list);
        save();
    }

    public void removeMarker(String markerId) {
        List<String> list = config.getStringList("banner-markers");
        list.removeIf(s -> s.endsWith("," + markerId));
        config.set("banner-markers", list);
        save();
    }

    public List<BannerMarkerData> getAllMarkers() {
        List<BannerMarkerData> result = new ArrayList<>();
        List<String> list = config.getStringList("banner-markers");
        for (String s : list) {
            String[] parts = s.split(",", 9);
            if (parts.length >= 7) {
                String world = parts[0];
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                String name = parts[4].replace("<comma>", ",");
                String color = parts[5];
                String markerId = parts[6];
                String placerUuid = parts.length > 7 ? parts[7] : "";
                String placerName = parts.length > 8 ? parts[8].replace("<comma>", ",") : "";
                result.add(new BannerMarkerData(world, x, y, z, name, color, markerId, placerUuid, placerName));
            }
        }
        return result;
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class BannerMarkerData {
        public final String world;
        public final int x, y, z;
        public final String name;
        public final String color;
        public final String markerId;
        public final String placerUuid;
        public final String placerName;
        public BannerMarkerData(String world, int x, int y, int z, String name, String color, String markerId, String placerUuid, String placerName) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name;
            this.color = color;
            this.markerId = markerId;
            this.placerUuid = placerUuid;
            this.placerName = placerName;
        }
        public Location getLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }
    }
} 