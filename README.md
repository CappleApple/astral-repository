# Astral Repository — Fabric 1.20.1

A magical storage and automation network for Minecraft 1.20.1. Crystals connect nearby storage, relay resources, and coordinate crafting through ordinary workstations. This is Astral Repository **1.11.9**, on branch `CappleApple/fabric-1.20.1`.

## Installation

Install `astral_repository-fabric-1.20.1-1.11.9.jar` on the client and server. Requires **Minecraft 1.20.1**, **Fabric Loader 0.19.5 or newer**, **Fabric API 0.92.12+1.20.1**, and **Java 17**. Configuration, energy, and applicable model libraries are bundled in the mod JAR.

Trinkets enables the goggles face slot; goggles also work in the normal helmet slot. Patchouli enables the Astral Field Guide. EMI adds recipe and menu integration, and Jade adds block information. These optional mods are not bundled. The explicit AE2 Fabric 1.20.1 adapter was not exercised with a complete optional storage network during these checks.

## Start a workshop

1. Collect Astral Gems from an Astral geode and craft a Storage Nexus.
2. Place the Nexus near chests or barrels and open it after automatic discovery.
3. Extend the network with same-channel Relay Crystals. Storage Crystals provide their own capacity-based storage.
4. Inscribe a Recipe Tome and put it in a nearby Chiseled Bookshelf to teach the network its recipe. Place the required workstation nearby and supply ingredients through connected storage.

Right-click the Attunement Wand in the air to choose or edit rune presets. Place a Push or Pull rune on a container, then Shift-right-click the glyph with the wand to assign target containers.

## Building

Run Gradle with **JDK 21**. An installed JDK 17 is also required for compilation. Set `org.gradle.java.installations.paths` in your user Gradle properties if Gradle cannot discover it.

```powershell
.\gradlew.bat test build
```

On Linux or macOS, use `./gradlew test build`. The output is `build/libs/astral_repository-fabric-1.20.1-1.11.9.jar`. All source sets and resources are inside this branch; no sibling checkout is required.

## Validation

The release checks include 146 JUnit tests and dedicated-server storage, transaction, recipe, and loot assertions. The packaged client fixture uses the remapped release JAR with a separate test mod. It covers all ten blocks on all six placement faces, survival item consumption, stateful storage break/re-place, stock-limited rune transfer, translated item names and tooltips, Nexus withdrawal and deposit packets, ordinary crafting, Recipe Tome inscription, bookshelf discovery, and a completed autocrafting job.

```powershell
.\gradlew.bat productionGameplaySmoke
.\gradlew.bat productionPersistenceRead
.\gradlew.bat productionIntegrationGameplaySmoke
```

`productionPersistenceRead` first runs a separate write process, then reopens its saved world in a second server JVM. It checks large named item stacks, fluid, energy, upgrades, channel settings, rune settings, and explicit links. The tasks create isolated worlds under `build/` and fail if their fixture does not report success. The persistence fixture accepts the Minecraft EULA for those local test servers. Client fixtures are hidden, muted, and release the mouse.

The optional profile uses Jade **11.13.3+fabric**, Patchouli **1.20.1-85-fabric**, EMI **1.1.24+1.20.1+fabric**, and Trinkets **3.7.2**. It repeats the gameplay workflow, opens the real Field Guide, checks EMI registration, and equips goggles in the Trinkets face slot with client/server synchronization. This does not establish compatibility with every optional-mod combination or cross-version world conversion.

The [main branch](https://github.com/CappleApple/astral-repository/tree/main) retains the original NeoForge 1.21.1 version and its documentation. Its loader APIs and compatibility descriptions do not automatically apply to this Fabric branch. See [port validation](https://github.com/CappleApple/astral-repository/blob/main/ports/VALIDATION.md) for the broader version matrix.

## License

[CC BY-NC-SA 4.0 with additional permission for Minecraft modpacks and servers](LICENSE).
