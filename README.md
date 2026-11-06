# Astral Repository — Fabric 26.2

A magical storage and automation network for Minecraft 26.2. Crystals connect nearby storage, relay resources, and coordinate crafting through ordinary workstations. This branch contains Astral Repository 1.12.2 for Fabric.

Version 1.12.2 discovers machine recipes through shared native APIs for Industrial Foregoing, Immersive Engineering, Oritech, Create, Thermal, Ender IO, and Powah. Recipe Tomes show nearby machine conversions and byproducts. Powah requests use one real orb cycle at a time. See the [changelog](CHANGELOG.md).

## Installation

Install `astral_repository-fabric-26.2-1.12.2.jar` on both the client and server. Requires Minecraft **26.2**, **Fabric Loader 0.19.5 or newer**, **Fabric API 0.161.0+26.2**, and **Java 25**. Configuration and energy libraries are bundled in the mod JAR.

JEI 30.29.0.201 and Jade 26.2.11 were installed during gameplay checks. These integrations are optional. No public Patchouli, EMI or Trinkets build was available for this target during validation; goggles work in the helmet slot. Tests with these integrations do not establish compatibility with every modpack.

## Start a workshop

1. Collect Astral Gems from an Astral geode and craft a Storage Nexus.
2. Place it near chests or barrels and open it after automatic discovery.
3. Extend the network with same-channel Relay Crystals. Storage Crystals provide their own storage.
4. Inscribe a Recipe Tome and place it in a nearby Chiseled Bookshelf to expose its recipe to autocrafting.

Right-click the Attunement Wand in the air to choose or edit rune presets. Place a Push or Pull rune on a container, then Shift-right-click the glyph with the wand to assign targets.

The Nexus grid is a saved personal workspace shared by your Nexus menus. Keep a physical crafting table within coverage for autocrafting. Inscribe the exact final output in a Recipe Tome and place it in a nearby Chiseled Bookshelf. Each recipe page names its process; hovering it shows the exact recipe and type IDs. Click an empty starred Nexus entry, enter the amount, then press Enter or the craft button. Use **Block recipe** or **Allow recipe** to control an alternative without replacing the taught output. The default **Blocking Rune** excludes a container without moving its contents.

Ordinary item, fluid, and chemical deposits skip processing machines. Energy may enter an unclaimed native energy container; active crafting claims exclude every resource through all aliases. Machine discovery uses shared native recipe, machine, and conversion contracts. It includes compatible addon recipes when they expose those contracts. It preserves item, fluid, chemical, and energy requirements. Native power, security, mode, structure, and processing conditions remain required. World mechanics without a recipe or conversion contract are not inferred.

## Building

Run Gradle with JDK 25:

```powershell
.\gradlew.bat test build
```

On Linux or macOS, use `./gradlew test build`. The output is `build/libs/astral_repository-fabric-26.2-1.12.2.jar`. Release builds must omit `-PclientSmoke`.

All source sets and resources live under `src/`.

## Validation

Version 1.12.2 passes 225 unit tests and its normal release build. Its actual packaged client ran hidden and muted without capturing the mouse, exercising placement, Nexus packets, Recipe Tome inscription and physical autocrafting. The tested JAR's class and resource entries match the release artifact. Native machine integrations on this edition received build/source validation; installed native-mod suites were run separately on NeoForge 1.21.1 and Forge 1.20.1. Discovery audits do not establish physical execution of every recipe or support for every addon. See the [current validation record](https://github.com/CappleApple/astral-repository/blob/main/docs/validation.md). Runtime descriptions retained below document earlier release checks.

The earlier port checks passed 162 JUnit tests and twelve dedicated-server rune transit cases. These cover delayed and instant Push/Pull for native item, fluid, and energy providers plus a Source fixture; one-tick pipelining; shared stock limits; refunds and indeterminate-provider recovery; saved travel time; and actual storage routes through network targets. Both classic and improved-transparency client paths draw fluid, power, Source, and binding beam geometry. These render checks do not replace pixel comparisons against real cloud layers. Actual packaged-client checks cover all ten block-item placements, every registered block and item name, storage upgrades and preserved drops, Nexus packets and inventory transfers, crafting and refill, Recipe Tome inscription, bookshelf discovery and completed autocrafting, rune filter/stock limits, remote access, local storage, rendering and the wand editor. Dedicated-server checks cover native item/fluid/energy transactions, recipes and harvesting, and a second process verifies saved resources after restart.

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
