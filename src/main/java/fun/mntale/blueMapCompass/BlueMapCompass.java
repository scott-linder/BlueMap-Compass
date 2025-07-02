package fun.mntale.blueMapCompass;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;

import com.tcoded.folialib.FoliaLib;

public final class BlueMapCompass extends JavaPlugin implements Listener {

    public static BlueMapCompass instance;
    public static FoliaLib foliaLib;
    public BannerMarkerStorage bannerMarkerStorage;
    public BannerMarkerListener bannerMarkerListener;
    public MarkerGUI markerGUI;
    public static boolean debug = false;

    @Override
    public void onEnable() {
        instance = this;
        foliaLib = new FoliaLib(this);
        BlueMapIntegration.initialize(instance);
        bannerMarkerStorage = new BannerMarkerStorage(this);
        bannerMarkerListener = new BannerMarkerListener();
        bannerMarkerListener.restoreAllMarkers();
        markerGUI = new MarkerGUI();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(markerGUI, this);
        getServer().getPluginManager().registerEvents(bannerMarkerListener, this);
        WaypointManager.registerCleanupTask(this);
    }

    @Override
    public void onDisable() {
    }

    @EventHandler
    public void onCompassRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (!player.hasPermission("bluemapcompass.use")) {
            return;
        }
        
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (item != null && item.getType() == Material.COMPASS) {
                event.setCancelled(true);
                MarkerGUI.openFor(player);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        java.util.Set<String> tracked = fun.mntale.blueMapCompass.WaypointManager.getTrackedMarkers(player);
        java.util.List<MarkerData> allMarkers = fun.mntale.blueMapCompass.BlueMapIntegration.getMarkers();
        java.util.Set<String> validMarkerIds = new java.util.HashSet<>();
        for (MarkerData marker : allMarkers) validMarkerIds.add(marker.id());
        for (String markerId : new java.util.HashSet<>(tracked)) {
            if (!validMarkerIds.contains(markerId)) {
                tracked.remove(markerId);
                fun.mntale.blueMapCompass.WaypointManager.setTrackedMarkers(player, tracked);
                fun.mntale.blueMapCompass.WaypointManager.removeWaypointDisplayOnly(player, markerId);
            }
        }
        // Restore tracked waypoints for valid markers
        for (String markerId : tracked) {
            MarkerData marker = allMarkers.stream()
                .filter(m -> m.id().equals(markerId))
                .findFirst()
                .orElse(null);
            if (marker != null) {
                fun.mntale.blueMapCompass.WaypointManager.setWaypoint(player, marker, this);
            }
        }
    }
}
