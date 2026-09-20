# Astral Repository for Forge 1.20.1

This directory builds the Minecraft 1.20.1 Forge edition of Astral Repository. The original Minecraft 1.21.1 NeoForge edition remains in the repository root.

Requires Java 17 and Forge 47.4.23 or later for Minecraft 1.20.1. Install the JAR on the client and server.

The port retains crystal storage, relay networks, rune programming, resource transfers, the Nexus, recipe tomes, automatic crafting, geodes, goggles, and astral rendering. Item identities and saved settings use Minecraft 1.20.1 item NBT. Inventories, fluids, and energy use native Forge capabilities with invalidation listeners.

See the [project README](../../README.md) for gameplay. Loader and Minecraft requirements in this file apply to this edition.

## Build and validation

Run these commands in this directory:

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
.\gradlew.bat runClient -PclientSmoke
```

The distributable JAR is written to `build/libs/astral_repository-1.20.1-forge-1.11.8.jar`. The build generates a Mixin refmap and reobfuscates the JAR for Forge.

`test` runs the ported regression suite. `runGameTestServer` runs dedicated-server checks for Forge capability discovery and invalidation, storage NBT, recipes and worldgen registration, and packet serialization. The opt-in client gate starts a hidden, muted client without grabbing the mouse, checks nine standalone models and both shaders, renders four item icons, saves `run-client/captures/items.png`, and exits. The port passed 146 regression tests, five dedicated GameTests, and this GPU gate.

## Optional integrations

Curios, JEI, EMI, Jade, and Patchouli remain optional. Their APIs are compile-only dependencies. The Field Guide item and recipe are available when Patchouli is installed.

The Applied Energistics 2 adapter targets its Forge storage and grid-node capabilities. The Refined Storage 1 adapter uses its native item storage and crafting task APIs. Their signatures were checked against AE2 15.4.10 and Refined Storage 1.12.4; full network and crafting behavior with those mods is not yet runtime-tested. Other optional storage and processing adapters also need tests with their Minecraft 1.20.1 releases.

## License

[CC BY-NC-SA 4.0 with additional permission for Minecraft modpacks and servers](LICENSE).
