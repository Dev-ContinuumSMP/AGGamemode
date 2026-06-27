# AGGamemode
<img width="1254" height="1254" alt="logo" src="https://github.com/user-attachments/assets/55fb6e9e-4c56-47cd-829c-1fe4186f73ee" />

AGGamemode is a Paper/Folia Minecraft plugin that adds a structured gamemode progression system with AG-styled menus, commands, and persistent player state. Players can choose between Traveler, Ironman, and Hardcore, while the plugin enforces restricted command access, trade rules, and group-based progression.

## Features

- Traveler, Ironman, and Hardcore mode selection
- In-game GUI for mode selection
- Command restrictions for restricted modes
- Group-based trade flow with request, accept, decline, and cancel support
- Persistent player data stored in H2
- Admin tools for setting other players' modes
- AG-branded messaging and UI styling

## Commands

- `/agg`
- `/agg help`
- `/agg set <player> <traveler|ironman|hardcore>`
- `/agg group`
- `/agg group create <name>`
- `/agg group rename <name>`
- `/agg group join <player>`
- `/agg group accept <player>`
- `/agg group decline <player>`
- `/agg group leave`
- `/agg group info [player]`
- `/trade <player>`
- `/trade request <player>`
- `/trade accept [player]`
- `/trade decline [player]`
- `/trade cancel`

## Permissions

- `aggamemode.self` - Allows setting your own gamemode
- `aggamemode.admin` - Allows setting other players' gamemodes

## Requirements

- Minecraft Paper 1.20.6 or compatible Folia/Paper server setup
- Java 21 recommended for runtime
- H2 library provided through the plugin metadata

## Build

This project is built with the included PowerShell script:

```powershell
.\build.ps1
```

The compiled jar is output to `build/out/AGGamemode-1.0.0.jar`.

## Configuration

On first startup, the plugin creates its data files in the plugin folder and initializes the H2 store used for player mode and trade group data. The GUI and mode text are driven by the plugin resources bundled with the jar.

## Notes

- The plugin is designed to feel consistent with the rest of the AG plugin suite.
- Trade restrictions only apply in Ironman and Hardcore.
- If the database cannot initialize, the plugin will disable itself rather than run in a partially broken state.
