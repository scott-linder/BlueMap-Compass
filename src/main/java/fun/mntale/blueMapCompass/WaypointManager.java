package fun.mntale.blueMapCompass;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bukkit.util.Transformation;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.DyeColor;
import net.kyori.adventure.text.format.TextColor;

import com.tcoded.folialib.wrapper.task.WrappedTask;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class WaypointManager {
    private static final Map<UUID, Map<String, BlockDisplay>> waypoints = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, WrappedTask>> tasks = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, TextDisplay>> textDisplays = new ConcurrentHashMap<>();
    public static final NamespacedKey TRACKED_MARKERS_KEY = new NamespacedKey("bluemapcompass", "tracked_markers");
    public static final NamespacedKey DISPLAY_PLAYER_KEY = new NamespacedKey("bluemapcompass", "display_player");
    public static final NamespacedKey DISPLAY_MARKER_KEY = new NamespacedKey("bluemapcompass", "display_marker");

    // Helper to map DyeColor to NamedTextColor
    private static final Map<String, NamedTextColor> DYE_TO_NAMED = Map.ofEntries(
        Map.entry("WHITE", NamedTextColor.WHITE),
        Map.entry("ORANGE", NamedTextColor.GOLD),
        Map.entry("MAGENTA", NamedTextColor.LIGHT_PURPLE),
        Map.entry("LIGHT_BLUE", NamedTextColor.AQUA),
        Map.entry("YELLOW", NamedTextColor.YELLOW),
        Map.entry("LIME", NamedTextColor.GREEN),
        Map.entry("PINK", NamedTextColor.LIGHT_PURPLE),
        Map.entry("GRAY", NamedTextColor.GRAY),
        Map.entry("LIGHT_GRAY", NamedTextColor.WHITE),
        Map.entry("CYAN", NamedTextColor.DARK_AQUA),
        Map.entry("PURPLE", NamedTextColor.DARK_PURPLE),
        Map.entry("BLUE", NamedTextColor.BLUE),
        Map.entry("BROWN", NamedTextColor.GOLD),
        Map.entry("GREEN", NamedTextColor.DARK_GREEN),
        Map.entry("RED", NamedTextColor.RED),
        Map.entry("BLACK", NamedTextColor.BLACK)
    );

    public static Set<String> getTrackedMarkers(Player player) {
        String data = player.getPersistentDataContainer().get(TRACKED_MARKERS_KEY, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return new HashSet<>();
        return new HashSet<>(Arrays.asList(data.split(";")));
    }

    public static void setTrackedMarkers(Player player, Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            player.getPersistentDataContainer().remove(TRACKED_MARKERS_KEY);
        } else {
            player.getPersistentDataContainer().set(TRACKED_MARKERS_KEY, PersistentDataType.STRING, String.join(";", ids));
        }
    }

    public static void setWaypoint(Player player, MarkerData marker, JavaPlugin plugin) {
        // Add markerId to tracked set
        Set<String> tracked = getTrackedMarkers(player);
        if (!tracked.contains(marker.id())) {
            tracked.add(marker.id());
            setTrackedMarkers(player, tracked);
        }
        // Always extract world name before '#'
        String worldName = marker.world();
        if (worldName.contains("#")) {
            worldName = worldName.substring(0, worldName.indexOf('#'));
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            if (fun.mntale.blueMapCompass.BlueMapCompass.debug) {
                Bukkit.getLogger().warning("[BlueMapCompass] World not found for marker: " + marker.world() + " (Player: " + player.getName() + ")");
            }
            return;
        }
        World playerWorld = player.getWorld();
        boolean sameWorld = playerWorld.getName().equals(world.getName());
        Location target = new Location(world, marker.x() + 0.5, marker.y(), marker.z() + 0.5);
        double minY = -64;
        double maxY = 500;
        double height = Math.max(1, Math.min(maxY - minY, maxY - minY)); // Clamp height to world range
        Location displayLoc = getBillboardLocation(player, target, 48);
        if (!sameWorld) {
            if (fun.mntale.blueMapCompass.BlueMapCompass.debug) {
                Bukkit.getLogger().warning("[BlueMapCompass] Player and marker are in different worlds. Waypoint tracked, but not displaying for: " + player.getName());
            }
            return;
        }
        BlueMapCompass.foliaLib.getScheduler().runAtLocation(displayLoc, regionTask -> {
            BlockDisplay display = (BlockDisplay) player.getWorld().spawnEntity(displayLoc, EntityType.BLOCK_DISPLAY);
            // Set BlockDisplay material based on banner color
            Material blockMaterial = Material.GREEN_STAINED_GLASS;
            if (marker.groupId().equalsIgnoreCase("banner-markers")) {
                String[] parts = marker.id().split("-");
                String colorName = parts.length > 0 ? parts[parts.length - 1].toUpperCase() : "";
                try {
                    blockMaterial = Material.valueOf(colorName + "_STAINED_GLASS");
                } catch (Exception ignored) {
                    blockMaterial = Material.GREEN_STAINED_GLASS;
                }
            }
            display.setBlock(Bukkit.createBlockData(blockMaterial));
            display.setBrightness(new Display.Brightness(15, 15));
            Transformation t = display.getTransformation();
            t.getScale().set(0.15f, (float) height, 0.15f);
            display.setTransformation(t);
            display.getPersistentDataContainer().set(DISPLAY_PLAYER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
            display.getPersistentDataContainer().set(DISPLAY_MARKER_KEY, PersistentDataType.STRING, marker.id());
            BlueMapCompass.foliaLib.getScheduler().runAtEntity(display, showTask -> player.showEntity(plugin, display));
            waypoints.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(marker.id(), display);
            // Spawn TextDisplay
            Location textLoc = getBillboardLocation(player, target, 48);
            // Set Y to player's eye level + offset
            textLoc.setY(player.getEyeLocation().getY() + getDeterministicYOffset(marker.id()));
            TextDisplay textDisplay = (TextDisplay) player.getWorld().spawnEntity(textLoc, EntityType.TEXT_DISPLAY);
            textDisplay.setBillboard(Display.Billboard.CENTER);
            textDisplay.setSeeThrough(true);
            textDisplay.setPersistent(false);
            textDisplay.setViewRange(128);
            textDisplay.setBrightness(new Display.Brightness(15, 15));
            double initialDist = player.getLocation().distance(target);
            float initialScale = (float)Math.min(2.5, Math.max(1.0, initialDist/24.0));
            textDisplay.setTransformation(new Transformation(textDisplay.getTransformation().getTranslation(), new org.joml.Quaternionf(), new org.joml.Vector3f(initialScale, initialScale, initialScale), new org.joml.Quaternionf()));
            textDisplay.getPersistentDataContainer().set(DISPLAY_PLAYER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
            textDisplay.getPersistentDataContainer().set(DISPLAY_MARKER_KEY, PersistentDataType.STRING, marker.id());
            // Color for banner marker
            final String markerName = marker.name();
            final String markerGroupId = marker.groupId();
            final String markerIdFinal = marker.id();
            // Start repeating task to update position and text
            WrappedTask task = BlueMapCompass.foliaLib.getScheduler().runTimer(() -> {
                if (!player.isOnline()) {
                    if (fun.mntale.blueMapCompass.BlueMapCompass.debug) {
                        Bukkit.getLogger().warning("[BlueMapCompass] Player offline, removing displays for: " + player.getName());
                    }
                    removeWaypointDisplayOnly(player, markerIdFinal);
                    return;
                }
                boolean inSameWorld = player.getWorld().getName().equals(target.getWorld().getName());
                Map<String, BlockDisplay> playerDisplays = waypoints.get(player.getUniqueId());
                Map<String, TextDisplay> playerTextDisplays = textDisplays.get(player.getUniqueId());
                BlockDisplay activeDisplay = playerDisplays != null ? playerDisplays.get(markerIdFinal) : null;
                TextDisplay activeTextDisplay = playerTextDisplays != null ? playerTextDisplays.get(markerIdFinal) : null;
                if (inSameWorld) {
                    BlockDisplay updateDisplay = activeDisplay;
                    TextDisplay updateTextDisplay = activeTextDisplay;
                    // Spawn displays if missing
                    if (activeDisplay == null || activeDisplay.isDead() || activeTextDisplay == null || activeTextDisplay.isDead()) {
                        removeWaypointDisplayOnly(player, markerIdFinal);
                        // SAFETY: Check player and world before spawning
                        if (!player.isOnline() || player.getWorld() == null) {
                            if (fun.mntale.blueMapCompass.BlueMapCompass.debug) {
                                Bukkit.getLogger().warning("[BlueMapCompass] Player offline or world null, skipping display spawn for: " + player.getName());
                            }
                            return;
                        }
                        // Spawn new BlockDisplay and TextDisplay using FoliaLib scheduler
                        BlueMapCompass.foliaLib.getScheduler().runAtLocation(target, (blockDisplayAndTextDisplay) -> {
                            Material spawnedBlockMaterial = Material.GREEN_STAINED_GLASS;
                            if (markerGroupId.equalsIgnoreCase("banner-markers")) {
                                String[] parts = markerIdFinal.split("-");
                                String colorName = parts.length > 0 ? parts[parts.length - 1].toUpperCase() : "";
                                try {
                                    spawnedBlockMaterial = Material.valueOf(colorName + "_STAINED_GLASS");
                                } catch (Exception ignored) {
                                    spawnedBlockMaterial = Material.GREEN_STAINED_GLASS;
                                }
                            }
                            BlockDisplay spawnedDisplay = (BlockDisplay) player.getWorld().spawnEntity(getBillboardLocation(player, target, 48), EntityType.BLOCK_DISPLAY);
                            spawnedDisplay.setBlock(Bukkit.createBlockData(spawnedBlockMaterial));
                            spawnedDisplay.setBrightness(new Display.Brightness(15, 15));
                            Transformation spawnedT = spawnedDisplay.getTransformation();
                            spawnedT.getScale().set(0.15f, (float) (500 - (-64)), 0.15f);
                            spawnedDisplay.setTransformation(spawnedT);
                            spawnedDisplay.getPersistentDataContainer().set(DISPLAY_PLAYER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
                            spawnedDisplay.getPersistentDataContainer().set(DISPLAY_MARKER_KEY, PersistentDataType.STRING, markerIdFinal);
                            BlueMapCompass.foliaLib.getScheduler().runAtEntity(spawnedDisplay, showTask -> player.showEntity(plugin, spawnedDisplay));
                            waypoints.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(markerIdFinal, spawnedDisplay);
                            // Spawn new TextDisplay
                            Location spawnedTextLoc = getBillboardLocation(player, target, 48);
                            spawnedTextLoc.setY(player.getEyeLocation().getY() + getDeterministicYOffset(markerIdFinal));
                            TextDisplay spawnedTextDisplay = (TextDisplay) player.getWorld().spawnEntity(spawnedTextLoc, EntityType.TEXT_DISPLAY);
                            spawnedTextDisplay.setBillboard(Display.Billboard.CENTER);
                            spawnedTextDisplay.setSeeThrough(true);
                            spawnedTextDisplay.setPersistent(false);
                            spawnedTextDisplay.setViewRange(128);
                            spawnedTextDisplay.setBrightness(new Display.Brightness(15, 15));
                            double spawnedInitialDist = player.getLocation().distance(target);
                            float spawnedInitialScale = (float)Math.min(8.0, Math.max(1.0, spawnedInitialDist/24.0));
                            spawnedTextDisplay.setTransformation(new Transformation(spawnedTextDisplay.getTransformation().getTranslation(), new org.joml.Quaternionf(), new org.joml.Vector3f(spawnedInitialScale, spawnedInitialScale, spawnedInitialScale), new org.joml.Quaternionf()));
                            spawnedTextDisplay.getPersistentDataContainer().set(DISPLAY_PLAYER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
                            spawnedTextDisplay.getPersistentDataContainer().set(DISPLAY_MARKER_KEY, PersistentDataType.STRING, markerIdFinal);
                            TextColor spawnedInitialColor = NamedTextColor.GREEN;
                            if (markerGroupId.equalsIgnoreCase("banner-markers")) {
                                String[] parts = markerIdFinal.split("-");
                                String colorName = parts.length > 0 ? parts[parts.length - 1] : "";
                                if (colorName.startsWith("#") && colorName.length() == 7) {
                                    try {
                                        spawnedInitialColor = TextColor.fromHexString(colorName);
                                    } catch (Exception ignored) {}
                                } else {
                                    spawnedInitialColor = DYE_TO_NAMED.getOrDefault(colorName.toUpperCase(), NamedTextColor.GREEN);
                                }
                            }
                            double spawnedDist = player.getLocation().distance(target);
                            Component spawnedText = Component.text(markerName + " (" + Math.round(spawnedDist) + "m)", spawnedInitialColor);
                            spawnedTextDisplay.text(spawnedText);
                            BlueMapCompass.foliaLib.getScheduler().runAtEntity(spawnedTextDisplay, td -> player.showEntity(plugin, spawnedTextDisplay));
                            textDisplays.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(markerIdFinal, spawnedTextDisplay);
                            updateWaypointDisplays(player, target, markerName, markerGroupId, markerIdFinal, spawnedDisplay, spawnedTextDisplay);
                        });
                        // End FoliaLib region context
                    }
                    // Add null check before updating displays
                    if (updateDisplay == null || updateTextDisplay == null) return;
                    updateWaypointDisplays(player, target, markerName, markerGroupId, markerIdFinal, updateDisplay, updateTextDisplay);
                } else {
                    // Remove displays if they exist
                    if ((activeDisplay != null && !activeDisplay.isDead()) || (activeTextDisplay != null && !activeTextDisplay.isDead())) {
                        removeWaypointDisplayOnly(player, markerIdFinal);
                    }
                    // Do not cancel the timer; displays will respawn if player returns to the world
                }
            }, 1, 2);
            tasks.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(marker.id(), task);
            // Set initial text color as well
            TextColor initialColor = NamedTextColor.GREEN;
            if (marker.groupId().equalsIgnoreCase("banner-markers")) {
                String[] parts = marker.id().split("-");
                String colorName = parts.length > 0 ? parts[parts.length - 1] : "";
                if (colorName.startsWith("#") && colorName.length() == 7) {
                    try {
                        initialColor = TextColor.fromHexString(colorName);
                    } catch (Exception ignored) {}
                } else {
                    initialColor = DYE_TO_NAMED.getOrDefault(colorName.toUpperCase(), NamedTextColor.GREEN);
                }
            }
            double dist = player.getLocation().distance(target);
            Component text = Component.text(marker.name() + " (" + Math.round(dist) + "m)", initialColor);
            textDisplay.text(text);
            BlueMapCompass.foliaLib.getScheduler().runAtEntity(textDisplay, td -> player.showEntity(plugin, textDisplay));
            textDisplays.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(marker.id(), textDisplay);
        });
    }

    // Remove only display/task, not from tracked set
    public static void removeWaypointDisplayOnly(Player player, String markerId) {
        Map<String, BlockDisplay> playerDisplays = waypoints.get(player.getUniqueId());
        if (playerDisplays != null) {
            BlockDisplay display = playerDisplays.remove(markerId);
            if (display != null && !display.isDead()) {
                BlueMapCompass.foliaLib.getScheduler().runAtEntity(display, removeTask -> display.remove());
                if (fun.mntale.blueMapCompass.BlueMapCompass.debug) {
                    Bukkit.getLogger().warning("[BlueMapCompass] BlockDisplay removed for player: " + player.getName());
                }
            }
            if (playerDisplays.isEmpty()) waypoints.remove(player.getUniqueId());
        }
        Map<String, TextDisplay> playerTextDisplays = textDisplays.get(player.getUniqueId());
        if (playerTextDisplays != null) {
            TextDisplay textDisplay = playerTextDisplays.remove(markerId);
            if (textDisplay != null && !textDisplay.isDead()) {
                BlueMapCompass.foliaLib.getScheduler().runAtEntity(textDisplay, removeTask -> textDisplay.remove());
                if (fun.mntale.blueMapCompass.BlueMapCompass.debug) {
                    Bukkit.getLogger().warning("[BlueMapCompass] TextDisplay removed for player: " + player.getName());
                }
            }
            if (playerTextDisplays.isEmpty()) textDisplays.remove(player.getUniqueId());
        }
        Map<String, WrappedTask> playerTasks = tasks.get(player.getUniqueId());
        if (playerTasks != null) {
            WrappedTask task = playerTasks.remove(markerId);
            if (task != null) {
                task.cancel();
                if (fun.mntale.blueMapCompass.BlueMapCompass.debug) {
                    Bukkit.getLogger().warning("[BlueMapCompass] Update task cancelled for player: " + player.getName());
                }
            }
            if (playerTasks.isEmpty()) tasks.remove(player.getUniqueId());
        }
    }

    public static void removeWaypoint(Player player, String markerId) {
        if (fun.mntale.blueMapCompass.BlueMapCompass.debug) {
            Bukkit.getLogger().warning("[BlueMapCompass] removeWaypoint called for player: " + player.getName() + ", markerId: " + markerId);
        }
        Set<String> tracked = getTrackedMarkers(player);
        tracked.remove(markerId);
        setTrackedMarkers(player, tracked);
        // Remove display entity for this markerId
        removeWaypointDisplayOnly(player, markerId);
    }

    private static Location getBillboardLocation(Player player, Location target, double distance) {
        Location playerLoc = player.getEyeLocation();
        Vector toTarget = target.toVector().subtract(playerLoc.toVector()).normalize();
        Vector pos = playerLoc.toVector().add(toTarget.multiply(distance));
        // Always use the target's Y coordinate for BlockDisplay
        return new Location(player.getWorld(), pos.getX(), -64, pos.getZ());
    }

    public static BlockDisplay getWaypoint(Player player, String markerId) {
        Map<String, BlockDisplay> playerDisplays = waypoints.get(player.getUniqueId());
        if (playerDisplays != null) return playerDisplays.get(markerId);
        return null;
    }

    public static void cleanupDisplays(JavaPlugin plugin) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(BlockDisplay.class)) {
                BlockDisplay display = (BlockDisplay) entity;
                var pdc = display.getPersistentDataContainer();
                String playerId = pdc.get(DISPLAY_PLAYER_KEY, PersistentDataType.STRING);
                String markerId = pdc.get(DISPLAY_MARKER_KEY, PersistentDataType.STRING);
                if (playerId == null || markerId == null) continue;
                Player player = Bukkit.getPlayer(UUID.fromString(playerId));
                if (player == null || !player.isOnline() || !getTrackedMarkers(player).contains(markerId)) {
                    BlueMapCompass.foliaLib.getScheduler().runAtEntity(display, removeTask -> display.remove());
                    continue;
                }
                if (display.getLocation().distanceSquared(player.getLocation()) > 128*128) {
                    BlueMapCompass.foliaLib.getScheduler().runAtEntity(display, removeTask -> display.remove());
                }
            }
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                TextDisplay textDisplay = (TextDisplay) entity;
                var pdc = textDisplay.getPersistentDataContainer();
                String playerId = pdc.get(DISPLAY_PLAYER_KEY, PersistentDataType.STRING);
                String markerId = pdc.get(DISPLAY_MARKER_KEY, PersistentDataType.STRING);
                if (playerId == null || markerId == null) continue;
                Player player = Bukkit.getPlayer(UUID.fromString(playerId));
                if (player == null || !player.isOnline() || !getTrackedMarkers(player).contains(markerId)) {
                    BlueMapCompass.foliaLib.getScheduler().runAtEntity(textDisplay, removeTask -> textDisplay.remove());
                    continue;
                }
                if (textDisplay.getLocation().distanceSquared(player.getLocation()) > 128*128) {
                    BlueMapCompass.foliaLib.getScheduler().runAtEntity(textDisplay, removeTask -> textDisplay.remove());
                }
            }
        }
    }

    public static void registerCleanupTask(JavaPlugin plugin) {
        BlueMapCompass.foliaLib.getImpl().runTimer(() -> cleanupDisplays(plugin), 5 * 60 * 20L, 5 * 60 * 20L);
    }

    private static void updateWaypointDisplays(Player player, Location target, String markerName, String markerGroupId, String markerIdFinal, BlockDisplay display, TextDisplay textDisplay) {
        if (display == null || display.isDead() || textDisplay == null || textDisplay.isDead()) {
            return;
        }
        double distance = player.getLocation().distance(target);
        float scale = (float)Math.min(8.0, Math.max(1.0, distance/24.0));
        if (distance > 48) {
            Location newLoc = getBillboardLocation(player, target, 48);
            newLoc.setY(player.getEyeLocation().getY() + getDeterministicYOffset(markerIdFinal));
            BlueMapCompass.foliaLib.getScheduler().runAtEntity(display, tpTask -> display.teleportAsync(newLoc));
            BlueMapCompass.foliaLib.getScheduler().runAtEntity(textDisplay, td -> {
                textDisplay.teleportAsync(newLoc);
                Transformation t = textDisplay.getTransformation();
                t.getScale().set(scale, scale, scale);
                textDisplay.setTransformation(t);
                // Update text and color
                TextColor color = NamedTextColor.GREEN;
                if (markerGroupId.equalsIgnoreCase("banner-markers")) {
                    String[] parts = markerIdFinal.split("-");
                    String colorName = parts.length > 0 ? parts[parts.length - 1] : "";
                    if (colorName.startsWith("#") && colorName.length() == 7) {
                        try {
                            color = TextColor.fromHexString(colorName);
                        } catch (Exception ignored) {}
                    } else {
                        color = DYE_TO_NAMED.getOrDefault(colorName.toUpperCase(), NamedTextColor.GREEN);
                    }
                }
                double distNow = player.getLocation().distance(target);
                Component newText = Component.text(markerName + " (" + Math.round(distNow) + "m)", color);
                textDisplay.text(newText);
            });
        } else {
            BlueMapCompass.foliaLib.getScheduler().runAtEntity(display, tpTask -> display.teleportAsync(target));
            Location textTarget = target.clone();
            textTarget.setY(player.getEyeLocation().getY() + getDeterministicYOffset(markerIdFinal));
            BlueMapCompass.foliaLib.getScheduler().runAtEntity(textDisplay, td -> {
                textDisplay.teleportAsync(textTarget);
                Transformation t = textDisplay.getTransformation();
                t.getScale().set(scale, scale, scale);
                textDisplay.setTransformation(t);
                // Update text and color
                TextColor color = NamedTextColor.GREEN;
                if (markerGroupId.equalsIgnoreCase("banner-markers")) {
                    String[] parts = markerIdFinal.split("-");
                    String colorName = parts.length > 0 ? parts[parts.length - 1] : "";
                    if (colorName.startsWith("#") && colorName.length() == 7) {
                        try {
                            color = TextColor.fromHexString(colorName);
                        } catch (Exception ignored) {}
                    } else {
                        color = DYE_TO_NAMED.getOrDefault(colorName.toUpperCase(), NamedTextColor.GREEN);
                    }
                }
                double distNow = player.getLocation().distance(target);
                Component newText = Component.text(markerName + " (" + Math.round(distNow) + "m)", color);
                textDisplay.text(newText);
            });
        }
    }

    public static void removeAllWaypoints(Player player) {
        Set<String> tracked = getTrackedMarkers(player);
        for (String markerId : tracked) {
            removeWaypointDisplayOnly(player, markerId);
        }
        setTrackedMarkers(player, new HashSet<>());
        // Remove all BlockDisplay/TextDisplay for this player
        UUID uuid = player.getUniqueId();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(BlockDisplay.class)) {
                String playerId = entity.getPersistentDataContainer().get(DISPLAY_PLAYER_KEY, PersistentDataType.STRING);
                if (playerId != null && playerId.equals(uuid.toString())) {
                    BlueMapCompass.foliaLib.getScheduler().runAtEntity(entity, t -> entity.remove());
                }
            }
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                String playerId = entity.getPersistentDataContainer().get(DISPLAY_PLAYER_KEY, PersistentDataType.STRING);
                if (playerId != null && playerId.equals(uuid.toString())) {
                    BlueMapCompass.foliaLib.getScheduler().runAtEntity(entity, t -> entity.remove());
                }
            }
        }
    }

    private static double getDeterministicYOffset(String markerId) {
        // Deterministic offset in range [-0.5, +0.5] based on markerId hash
        int hash = markerId.hashCode();
        return ((hash % 1000) / 1000.0 - 0.5);
    }

    /**
     * Atomically add a marker to the tracked set and spawn its display.
     */
    public static void addTrackedWaypoint(Player player, MarkerData marker, JavaPlugin plugin) {
        Set<String> tracked = getTrackedMarkers(player);
        if (!tracked.contains(marker.id())) {
            tracked.add(marker.id());
            setTrackedMarkers(player, tracked);
        }
        setWaypoint(player, marker, plugin);
    }
} 