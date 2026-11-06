# Astral Repository — Minecraft 26.3 NeoForge

A magical storage and automation network: place crystals around a workshop, teach a bookshelf the products you want, and let the network find storage, route resources, and coordinate ordinary workstations.

This branch builds Astral Repository **1.12.2** for **Minecraft 26.3**, **NeoForge 26.3.0.7-beta or newer within Minecraft 26.3**, and **Java 25**. Install the matching JAR on both the client and server. No additional mod is required.

This edition was validated with NeoForge 26.3.0.7-beta.

Version 1.12.2 discovers machine recipes through shared native APIs for Industrial Foregoing, Immersive Engineering, Oritech, Create, Thermal, Ender IO, and Powah. Recipe Tomes show nearby machine conversions and byproducts. Powah requests use one real orb cycle at a time. See the [changelog](CHANGELOG.md).

The Nexus grid is a saved personal workspace shared by your Nexus menus. Keep a physical crafting table within coverage for autocrafting. Inscribe the exact final output in a Recipe Tome and place it in a nearby Chiseled Bookshelf. Each recipe page names its process; hovering it shows the exact recipe and type IDs. Click an empty starred Nexus entry, enter the amount, then press Enter or the craft button. Use **Block recipe** or **Allow recipe** to control an alternative without replacing the taught output. The default **Blocking Rune** excludes a container without moving its contents.

Ordinary item, fluid, and chemical deposits skip processing machines. Energy may enter an unclaimed native energy container; active crafting claims exclude every resource through all aliases. Machine discovery uses shared native recipe, machine, and conversion contracts. It includes compatible addon recipes when they expose those contracts. It preserves item, fluid, chemical, and energy requirements. Native power, security, mode, structure, and processing conditions remain required. World mechanics without a recipe or conversion contract are not inferred.

## Building

Use Java 25 to run the included Gradle wrapper:

```powershell
.\gradlew.bat test build
```

On Linux or macOS, use `./gradlew test build`.

The installable JAR is `build/libs/astral_repository-26.3-neoforge-1.12.2.jar`.

## Source and validation

Version 1.12.2 passes 230 unit tests and its normal release build. Its actual packaged client ran hidden and muted without capturing the mouse, exercising placement, Nexus packets, Recipe Tome inscription and physical autocrafting. The tested JAR's class and resource entries match the release artifact. Native machine integrations on this edition received build/source validation; installed native-mod suites were run separately on NeoForge 1.21.1 and Forge 1.20.1. Discovery audits do not establish physical execution of every recipe or support for every addon. See the [current validation record](https://github.com/CappleApple/astral-repository/blob/main/docs/validation.md). Runtime descriptions retained below document earlier release checks.

The branch contains all of its source inputs. [The 26.3 source generator](tools/modern-26.3.gradle) adapts the Java in `src/main/java` and tests in `src/test/java` into `build/generated/sources/minecraft263/`. Replacement classes are applied in this order: `src/overrides/java`, then `src/shared-overrides/java`, then the base sources. Edit these inputs rather than the generated files. Resources in `src/main/resources` already use the 26.3 formats.

The project contains 230 unit tests. Development runtime checks are available as `runServer -PsmokeTest` and `runClientSmoke -PclientSmoke`; the client fixture is compiled only when requested. Test fixtures are excluded from the release JAR. Server runs require accepting Minecraft's EULA in `run-server/eula.txt`.

The [packaged validation launcher](tools/neoforge-packaged-validation.gradle) runs the built release JAR with a separate fixture directory and removes the production source output directories from the runtime classpath. For a background client run, first disable NeoForge's early startup window:

```powershell
New-Item -ItemType Directory -Force build/smoke-client/config
Set-Content build/smoke-client/config/fml.toml 'earlyWindowControl = false'
.\gradlew.bat runClientSmoke -PclientSmoke -I tools/neoforge-packaged-validation.gradle
.\gradlew.bat runServer -PsmokeTest -I tools/neoforge-packaged-validation.gradle
.\gradlew.bat runServer -PsmokeTest -I tools/neoforge-packaged-validation.gradle
```

The client fixture hides the game window, mutes audio, and releases the mouse. The two server commands run separate JVMs; the second verifies saved inventory components, fluids, energy, rune settings, targets, and links before resetting the fixture. The fixture classes are excluded from the release JAR.

The dedicated-server gate also checks 12 automatic-rune scenarios covering all four resource types, instant and delayed transfers, one-tick pipelines, stock limits, failed deliveries, saved flights, and network-bound storage paths. The client gate checks actual resource sprites, fallback item icons, and binding beams in classic and improved transparency.

See the [port validation record on main](https://github.com/CappleApple/astral-repository/blob/main/ports/VALIDATION.md) for the completed checks and their limits. Both the development runtime and packaged JAR passed block placement and localized-name checks, Nexus packet transactions, manual crafting and exact ingredient refill, Recipe Tome/table autocrafting, filtered rune transfers, client rendering, and dedicated-server save/restart checks. The [main branch](https://github.com/CappleApple/astral-repository) retains the original Minecraft 1.21.1 NeoForge implementation and usage documentation.

## Optional integrations

The packaged JAR passed the gameplay and rendering checks with JEI 31.1.0.12, its required MezzConfig 0.5.12, and Jade 26.3.1 installed. Jade is stable; JEI and MezzConfig are beta releases. No stable JEI release or matching Curios release was available for Minecraft 26.3 during validation. The Curios hook compiles against the 26.2 API and is not runtime-verified on 26.3.

Other integration hooks remain optional and were not exercised in a complete third-party modpack. No matching public Patchouli or EMI release was available during validation, so those hooks remain inactive without a matching release. The Field Guide requires Patchouli.

## License

[CC BY-NC-SA 4.0 with additional permission for Minecraft modpacks and servers](LICENSE). The additional permission allows use in commercial or monetized modpacks and servers; standalone paid distribution is excluded. See `LICENSE` for the complete terms supplied with the project.
