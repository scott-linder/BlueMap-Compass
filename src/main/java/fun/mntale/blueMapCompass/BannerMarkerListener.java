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
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;

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

    // Utility to handle banner removal logic
    private void handleBannerRemove(Block block) {
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
        // Untrack for all online players and remove displays
        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            java.util.Set<String> tracked = fun.mntale.blueMapCompass.WaypointManager.getTrackedMarkers(player);
            if (tracked.contains(markerId)) {
                tracked.remove(markerId);
                fun.mntale.blueMapCompass.WaypointManager.setTrackedMarkers(player, tracked);
                fun.mntale.blueMapCompass.WaypointManager.removeWaypointDisplayOnly(player, markerId);
            }
        }
    }

    @EventHandler
    public void onBannerBreak(BlockBreakEvent event) {
        handleBannerRemove(event.getBlock());
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            handleBannerRemove(block);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            handleBannerRemove(block);
        }
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        handleBannerRemove(event.getBlock());
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        handleBannerRemove(event.getToBlock());
    }

    @EventHandler
    public void onBlockFade(BlockFadeEvent event) {
        handleBannerRemove(event.getBlock());
    }

    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        handleBannerRemove(event.getBlock());
    }

    @EventHandler
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            handleBannerRemove(block);
        }
    }

    @EventHandler
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            handleBannerRemove(block);
        }
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