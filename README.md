# AGGamemode

<img width="1254" height="1254" alt="logo" src="https://github.com/user-attachments/assets/37439bc4-b8e2-4074-8c53-e36de204636f" />


[![Java 21](https://img.shields.io/badge/Java-21-2ea043)](https://adoptium.net/)
[![Paper 1.20.6+](https://img.shields.io/badge/Paper-1.20.6%2B-0094ff)](https://papermc.io/)
[![Folia Supported](https://img.shields.io/badge/Folia-Supported-7c3aed)](https://papermc.io/software/folia)
[![License](https://img.shields.io/badge/License-View%20LICENSE-f59e0b)](LICENSE)

AGGamemode is a polished Paper and Folia Minecraft plugin that adds a structured gamemode progression system with AG-styled menus, commands, and persistent player data. Players can choose between Traveler, Ironman, and Hardcore while the plugin enforces restricted command access, trade rules, and group-based progression.

## Highlights

- Traveler, Ironman, and Hardcore mode selection
- In-game GUI for mode selection
- Restricted command handling for challenge modes
- Group-based trade flow with request, accept, decline, and cancel support
- Persistent player data stored in H2
- Admin tools for setting other players' modes
- AG-branded messaging and UI styling
- Folia-compatible runtime support

## Quick Start

### Requirements

- Java 21
- Paper 1.20.6+ or Folia
- A Minecraft server with plugin support enabled

### Build

```powershell
.\build.ps1
```

The compiled jar is output to `build/out/AGGamemode-1.0.0.jar`.

### Install

1. Build the jar using the included PowerShell script.
2. Place the jar in your server's `plugins` folder.
3. Start the server once to generate plugin data files.
4. Configure mode settings, GUI layout, and restrictions as needed.
5. Restart the server after making changes.

## Commands

### Gamemode Control

| Command | Description |
| --- | --- |
| `/agg` | Show your current mode or open the main flow. |
| `/agg help` | Show command help. |
| `/agg set <player> <traveler|ironman|hardcore>` | Set another player's mode. |
| `/agg group` | Open the trade group menu. |
| `/agg group create <name>` | Create a personal trade group. |
| `/agg group rename <name>` | Rename your trade group. |
| `/agg group join <player>` | Request to join another player's group. |
| `/agg group accept <player>` | Accept a join request. |
| `/agg group decline <player>` | Decline a join request. |
| `/agg group leave` | Leave the current shared group. |
| `/agg group info [player]` | View group details for yourself or another player. |

### Trade Flow

| Command | Description |
| --- | --- |
| `/trade <player>` | Send a trade request. |
| `/trade request <player>` | Send a trade request with the explicit flow. |
| `/trade accept [player]` | Accept a pending trade request. |
| `/trade decline [player]` | Decline a pending trade request. |
| `/trade cancel` | Cancel your outgoing trade request. |

## Permissions

| Permission | Description | Default |
| --- | --- | --- |
| `aggamemode.self` | Allows setting your own gamemode. | `true` |
| `aggamemode.admin` | Allows setting other players' gamemodes. | `op` |

## Configuration

AGGamemode stores its settings in the plugin folder and uses H2 for player mode and trade group persistence. On first startup it creates the required data files automatically.

The plugin is designed to stay consistent with the rest of the AG suite:

- AG-styled text and prefix handling
- GUI menus for player-facing actions
- Restricted-mode rules that keep the progression loop intact
- Persistent player state for reliable restarts

## Release

- Version: 1.0.0
- Artifact: `AGGamemode-1.0.0.jar`

## Support

If something breaks, include:

- Server software and version
- Java version
- Plugin version
- Reproduction steps
- Relevant log output

## License

See [LICENSE](LICENSE).
