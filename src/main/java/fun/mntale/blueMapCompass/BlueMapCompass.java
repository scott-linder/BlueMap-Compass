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
        for (String markerId : WaypointManager.getTrackedMarkers(player)) {
            MarkerData marker = BlueMapIntegration.getMarkers().stream()
                .filter(m -> m.id().equals(markerId))
                .findFirst()
                .orElse(null);
            if (marker != null) {
                WaypointManager.setWaypoint(player, marker, this);
            }
        }
    }
}
