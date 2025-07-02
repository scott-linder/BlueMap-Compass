package fun.mntale.blueMapCompass;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.BlueMapWorld;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import com.flowpowered.math.vector.Vector3d;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class BannerMarkerListener implements Listener {

    @EventHandler
    public void onBannerPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!(block.getState() instanceof Banner banner)) return;
        ItemStack item = event.getItemInHand();
        if (!(item.hasItemMeta() && item.getItemMeta().hasDisplayName())) return; // Ignore banners with no custom name
        Component nameComponent = item.getItemMeta().displayName();
        String name = PlainTextComponentSerializer.plainText().serialize(nameComponent);
        DyeColor color = banner.getBaseColor();
        Location loc = block.getLocation();
        String worldName = loc.getWorld().getName();
        Vector3d pos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
        String markerId = "banner-" + worldName + "-" + loc.getBlockX() + "-" + loc.getBlockY() + "-" + loc.getBlockZ() + "-" + color;
        String placerUuid = event.getPlayer().getUniqueId().toString();
        String placerName = event.getPlayer().getName();
        // Save to storage
        BlueMapCompass.instance.bannerMarkerStorage.addMarker(worldName, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), name, color.name(), markerId, placerUuid, placerName);
        // Create marker in BlueMap
        BlueMapAPI.getInstance().ifPresent(api -> {
            Optional<BlueMapWorld> blueMapWorld = api.getWorld(loc.getWorld().getUID());
            blueMapWorld.ifPresent(world -> {
                world.getMaps().forEach(map -> {
                    MarkerSet markerSet = map.getMarkerSets().computeIfAbsent("banner-markers", k -> MarkerSet.builder().label("Banner Markers").build());
                    POIMarker marker = POIMarker.builder()
                            .label(name)
                            .position(pos)
                            .maxDistance(100000)
                            .build();
                    markerSet.getMarkers().put(markerId, marker);
                });
            });
        });
    }

    @EventHandler
    public void onBannerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Banner banner)) return;
        Location loc = block.getLocation();
        String worldName = loc.getWorld().getName();
        DyeColor color = banner.getBaseColor();
        String markerId = "banner-" + worldName + "-" + loc.getBlockX() + "-" + loc.getBlockY() + "-" + loc.getBlockZ() + "-" + color;
        // Remove from storage
        BlueMapCompass.instance.bannerMarkerStorage.removeMarker(markerId);
        // Remove marker from BlueMap
        BlueMapAPI.getInstance().ifPresent(api -> {
            Optional<BlueMapWorld> blueMapWorld = api.getWorld(loc.getWorld().getUID());
            blueMapWorld.ifPresent(world -> {
                world.getMaps().forEach(map -> {
                    MarkerSet markerSet = map.getMarkerSets().get("banner-markers");
                    if (markerSet != null) {
                        markerSet.getMarkers().remove(markerId);
                    }
                });
            });
        });
    }

    // Call this on plugin enable to restore all markers
    public void restoreAllMarkers() {
        BlueMapAPI.onEnable(api -> {
            for (BannerMarkerStorage.BannerMarkerData data : BlueMapCompass.instance.bannerMarkerStorage.getAllMarkers()) {
                Location loc = data.getLocation();
                if (loc == null) continue;
                Vector3d pos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
                Optional<BlueMapWorld> blueMapWorld = api.getWorld(loc.getWorld().getUID());
                blueMapWorld.ifPresent(world -> {
                    world.getMaps().forEach(map -> {
                        MarkerSet markerSet = map.getMarkerSets().computeIfAbsent("banner-markers", k -> MarkerSet.builder().label("Banner Markers").build());
                        POIMarker marker = POIMarker.builder()
                                .label(data.name)
                                .position(pos)
                                .maxDistance(100000)
                                .build();
                        markerSet.getMarkers().put(data.markerId, marker);
                    });
                });
            }
        });
    }
} 