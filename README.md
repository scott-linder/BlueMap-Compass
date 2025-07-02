# BlueMap Compass Plugin

A Minecraft plugin that allows players to view and teleport to BlueMap markers through a custom GUI when right-clicking with a compass.

## Features

- **Compass Integration**: Right-click with any compass to open the BlueMap markers GUI
- **Custom GUI**: Beautiful inventory-based interface showing all available markers
- **Marker Categories**: Different marker types are displayed with appropriate icons
- **Teleportation**: Click on any marker to teleport to its location
- **Permission System**: Configurable permissions for using the compass feature

## Installation

1. **Prerequisites**:
   - Paper/Spigot server (1.21+)
   - BlueMap plugin installed and configured

2. **Installation Steps**:
   - Download the plugin JAR file
   - Place it in your server's `plugins` folder
   - Restart your server
   - The plugin will automatically detect BlueMap and integrate with it

## Usage

### For Players

1. **Get a Compass**: Obtain any compass item (vanilla compass works)
2. **Right-Click**: Right-click with the compass in hand
3. **Browse Markers**: The GUI will show all available BlueMap markers
4. **Teleport**: Click on any marker to teleport to its location

### For Server Administrators

#### Permissions

- `bluemapcompass.use` - Allows players to use the compass GUI (default: true)

#### Commands

Currently, there are no commands available. The plugin works entirely through the compass interaction.

## Configuration

The plugin uses sample data when BlueMap is not available. To use real BlueMap markers:

1. Ensure BlueMap plugin is installed and running
2. Create markers in BlueMap
3. The plugin will automatically detect and display them

## Marker Types

The plugin supports different marker types with appropriate icons:

- **Spawn** - Beacon icon
- **Shop** - Emerald icon  
- **Warps** - Ender Pearl icon
- **Buildings** - Bricks icon
- **Points of Interest** - Map icon
- **Other** - Name Tag icon

## Development

### Building from Source

1. Clone the repository
2. Run `./gradlew build`
3. Find the JAR file in `build/libs/`

### Dependencies

- Paper API 1.21.6+
- BlueMap API (optional, for real marker integration)

## Troubleshooting

### Plugin Not Working

1. Check if BlueMap is installed and running
2. Verify server version compatibility (1.21+)
3. Check console for error messages
4. Ensure players have the `bluemapcompass.use` permission

### No Markers Showing

1. Verify BlueMap has markers configured
2. Check BlueMap configuration
3. Plugin will show sample markers if BlueMap is not available

## License

This plugin is open source and available under the MIT License.

## Support

For issues and feature requests, please create an issue on the project repository. 