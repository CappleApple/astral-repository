# Astral Repository for Forge 1.20.1

Astral Repository connects workshop inventories through crystal networks, moves items, fluids, and energy, and crafts products from recipes taught to the network.

This directory builds the Forge edition, version 1.11.9. The original Minecraft 1.21.1 NeoForge edition remains at the repository root. See the [project README](../../README.md) for the full gameplay guide.

## Installation

Requires Minecraft 1.20.1, Java 17, and Forge 47.4.10 or later. The build targets Forge's recommended 47.4.10 release. Install the mod on both the client and server. No additional mod is required.

Curios, JEI, EMI, Jade, and Patchouli are optional. The Astral Field Guide item and recipe become available when Patchouli is installed. These release versions are used by the optional runtime validation profile:

| Mod | Version |
| --- | --- |
| Curios | 5.14.1+1.20.1 |
| JEI | 15.56.0.205 |
| EMI | 1.1.24+1.20.1 |
| Jade | 11.13.3+forge |
| Patchouli | 1.20.1-85-FORGE |

## Getting started

Find Astral Gems in Overworld geodes and craft a Storage Nexus. Place the Nexus near chests or barrels, then open it to browse storage. Use Relay Crystals to extend coverage and Storage Crystals to hold items, fluids, and energy directly.

Teach recipes with a Recipe Tome and request their products from the Nexus. Use the Attunement Wand to edit rune programs and their destinations. Astral Goggles reveal placed runes.

Item identities and saved settings use Minecraft 1.20.1 item NBT. Storage interoperability uses native Forge capabilities with invalidation listeners.

## Build and validation

Set `JAVA_HOME` to a JDK 17 installation and run:

```powershell
.\gradlew.bat test build
.\gradlew.bat runGameTestServer
.\gradlew.bat -PgameplaySmoke runClient
.\gradlew.bat -PgameplaySmoke -PoptionalRuntime runClient
```

The Gradle 8.8 wrapper downloads public dependencies. The release JAR is `build/libs/astral_repository-1.20.1-forge-1.11.9.jar`. ForgeGradle reobfuscates it and includes the Mixin refmap. Both refmaps and the Mixin member-mapping file are retained when compilation is restored from Gradle cache.

The port audit passed 146 JUnit tests and 10 native Forge GameTests. Runtime checks cover all ten blocks placed through their items on all six faces, storage recovery after breaking and replacement, Nexus withdrawal/deposit and vanilla crafting/refill, Recipe Tome inscription and bookshelf discovery, completed automatic crafting with ingredient conservation, item/fluid/energy rune transfers with stock limits, capability invalidation, all thirteen base recipes, and worldgen registration. This is a focused port suite; it does not claim the original NeoForge edition's complete GameTest coverage.

The gameplay client runs hidden and muted without capturing the mouse. It verifies placed blocks, translated item names and tooltips, synchronized Nexus contents, real client/server transfer packets, and vanilla crafting clicks. It saves images and a result file under `run-gameplay/gameplay-captures/`. The installed retail client also passed the Recipe Tome catalogue, inscription, and an eight-plank autocraft consuming exactly two logs. The earlier development-client checks passed with all five optional mods together. The older `-PclientSmoke` gate separately checks models and shaders.

`auditHarnessJar` builds a separate test mod for an installed Forge client or server. Its client subscriber uses the fixture mod ID; it is excluded from the release JAR. The installed-server audit verified the reobfuscated release, normal placement, Nexus transactions, rune transfers, automatic crafting, and saved storage/rune data, taught recipes, and crafted output after a second JVM start. The audit harness is a development artifact; do not distribute its `-audit-harness.jar` alongside the release. See the [validation record](../VALIDATION.md) for evidence and limits.

## Optional storage integrations

The Applied Energistics 2 adapter targets Forge storage and grid-node capabilities. The Refined Storage 1 adapter uses its item storage and crafting task APIs. Signatures were checked against AE2 15.4.10 and Refined Storage 1.12.4; complete external networks and crafting jobs with these mods have not been runtime-tested.

EMI and JEI together emit duplicate tag-recipe diagnostics in their development integration. The optional runtime audit passed despite these diagnostics; it does not establish complete compatibility for every optional plugin feature.

## License

Copyright 2026 CappleApple. [CC BY-NC-SA 4.0 with additional permission for Minecraft modpacks and servers](LICENSE). The additional permission permits inclusion in commercial or monetized modpacks and servers; it does not permit standalone paid distribution of the mod.
