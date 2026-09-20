# Astral Repository — Fabric 26.3

A magical storage and automation network for Minecraft 26.3. Crystals connect nearby storage, relay resources, and coordinate crafting through ordinary workstations. This branch contains Astral Repository 1.11.9 for Fabric.

## Installation

Install `astral_repository-fabric-26.3-1.11.9.jar` on both the client and server. Requires Minecraft **26.3**, **Fabric Loader 0.19.5 or newer**, **Fabric API 0.161.0+26.3**, and **Java 25**. Configuration and energy libraries are bundled in the mod JAR.

JEI 31.1.0.12 beta (with MezzConfig 0.5.12 beta) and Jade 26.3.1 were installed during gameplay checks. These integrations are optional. No public Patchouli, EMI or Trinkets build was available for this target during validation; goggles work in the helmet slot. Tests with these integrations do not establish compatibility with every modpack.

## Start a workshop

1. Collect Astral Gems from an Astral geode and craft a Storage Nexus.
2. Place it near chests or barrels and open it after automatic discovery.
3. Extend the network with same-channel Relay Crystals. Storage Crystals provide their own storage.
4. Inscribe a Recipe Tome and place it in a nearby Chiseled Bookshelf to expose its recipe to autocrafting.

Right-click the Attunement Wand in the air to choose or edit rune presets. Place a Push or Pull rune on a container, then Shift-right-click the glyph with the wand to assign targets.

## Building

Run Gradle with JDK 25:

```powershell
.\gradlew.bat test build
```

On Linux or macOS, use `./gradlew test build`. The output is `build/libs/astral_repository-fabric-26.3-1.11.9.jar`. Release builds must omit `-PclientSmoke`.

The base sources and tests live in `support/fabric-common/src/`. `tools/modern-26.3.gradle` generates the target sources. Overrides in `src/overrides/java/` take precedence over `support/minecraft263-overrides/src/main/java/`, then the base sources. Edit those inputs, not the generated Java files.

## Validation

The port passes 147 JUnit tests. Actual packaged-client checks cover all ten block-item placements, every registered block and item name, storage upgrades and preserved drops, Nexus packets and inventory transfers, crafting and refill, Recipe Tome inscription, bookshelf discovery and completed autocrafting, rune filter/stock limits, remote access, local storage, rendering and the wand editor. Dedicated-server checks cover native item/fluid/energy transactions, recipes and harvesting, and a second process verifies saved resources after restart.

```powershell
.\gradlew.bat gameplayFixtureJar -PclientSmoke
.\gradlew.bat productionGameplay
.\gradlew.bat productionGameplay -PoptionalRuntime
.\gradlew.bat runServerSmoke
.\gradlew.bat runServerSmoke
```

The separate gameplay fixture is never packaged into the release mod. Client runs are hidden, muted and do not grab the mouse. The production launcher includes Minecraft's required JVM native-access and stack settings. Accept the Minecraft EULA in `run-server-smoke/eula.txt` before using the dedicated fixture.

The original NeoForge 1.21.1 edition remains on `main`. These tests do not cover world conversion or complete AE2/Refined Storage networks.

## License

[CC BY-NC-SA 4.0 with additional permission for Minecraft modpacks and servers](LICENSE).
