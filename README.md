# Astral Repository for Forge 1.20.1

Astral Repository connects workshop inventories through crystal networks, moves items, fluids, and energy, and crafts products from recipes taught to the network.

The `CappleApple/forge-1.20.1` branch contains the standalone Forge 1.20.1 edition, version 1.11.8. It builds independently of the other Minecraft and loader branches.

## Installation

Requires Minecraft 1.20.1, Forge 47.4.23 or later for that Minecraft version, and Java 17. Install the mod on both the client and server.

Curios, JEI, EMI, Jade, and Patchouli are optional. The Astral Field Guide item and recipe become available when Patchouli is installed. This port uses Patchouli 1.20.1-84.1-FORGE and Curios 5.14.1+1.20.1 APIs.

## Getting started

Find Astral Gems in Overworld geodes and craft a Storage Nexus. Place the Nexus near chests or barrels, then open it to browse discovered storage. Discovery extends five blocks from connected crystals by default and runs over several ticks.

Use Relay Crystals to extend coverage. Nearby crystals on the same dye channel connect automatically when they have line of sight. Storage Crystals hold items, fluids, and energy directly. Teach recipes with a Recipe Tome, then request their products from the Nexus. Use the Attunement Wand to edit rune programs and configure supported links.

The port retains geodes, rune programming, resource transfers, recipe tomes, crafting requests, Resonance Goggles, and astral rendering. Item identities and saved settings use Minecraft 1.20.1 item NBT. Storage interoperability uses native Forge capabilities with invalidation listeners.

## Build and validation

Set `JAVA_HOME` to a JDK 17 installation and run:

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
.\gradlew.bat runClient -PclientSmoke
```

The Gradle 8.8 wrapper downloads the required public dependencies. The distributable is `build/libs/astral_repository-1.20.1-forge-1.11.8.jar`; ForgeGradle reobfuscates it and generates the Mixin refmap.

The port passed 146 JUnit tests and five native Forge GameTests covering capability discovery and invalidation, storage NBT, recipes and registry data, and packet serialization. The client smoke test runs hidden and muted without grabbing the mouse, checks nine standalone models and two shaders, renders four item icons, writes `run-client/captures/items.png`, and exits. Its screenshot was inspected during port validation. The original NeoForge GameTest suite is not included in this port's GameTest count.

## Optional storage integrations

The Applied Energistics 2 adapter targets Forge storage and grid-node capabilities. The Refined Storage 1 adapter uses its item storage and crafting task APIs. Their signatures were checked against AE2 15.4.10 and Refined Storage 1.12.4. Complete external networks and crafting jobs with those mods have not been runtime-tested; compilation is not a claim of full optional-mod compatibility.

## License

Copyright 2026 CappleApple. [CC BY-NC-SA 4.0 with additional permission for Minecraft modpacks and servers](LICENSE). The additional permission permits inclusion in commercial or monetized modpacks and servers; it does not permit standalone paid distribution of the mod.
