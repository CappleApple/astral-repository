# Astral Repository — Minecraft 26.3 NeoForge

A magical storage and automation network: place crystals around a workshop, teach a bookshelf the products you want, and let the network find storage, route resources, and coordinate ordinary workstations.

This branch builds Astral Repository **1.12.1** for **Minecraft 26.3**, **NeoForge 26.3.0.7-beta or newer within Minecraft 26.3**, and **Java 25**. Install the matching JAR on both the client and server. No additional mod is required.

This edition was validated with NeoForge 26.3.0.7-beta.

Version 1.12.1 preserves exact potion recipes, adds native Mekanism machine and resource discovery, and identifies each Recipe Tome recipe's process. Empty starred Nexus entries select a quantity before submitting a craft. Autocrafting still requires a physical crafting table within network coverage. See the [changelog](CHANGELOG.md).

The Nexus grid is a saved personal workspace shared by your Nexus menus. Keep a separate crafting table within coverage for autocrafting. Inscribe the exact final output in a Recipe Tome and place it in a nearby Chiseled Bookshelf. Each recipe page names its process; hovering it shows the exact recipe and type IDs. Click an empty starred Nexus entry, enter the amount, then press Enter or the craft button. Use **Block recipe** or **Allow recipe** to control an alternative without replacing the taught output. The default **Blocking Rune** excludes a container without moving its contents.

Ordinary item, fluid, and chemical deposits skip processing machines. Energy may enter an unclaimed native energy container; active crafting claims exclude every resource through all aliases. Mekanism discovery uses native recipe, machine, and conversion contracts, including compatible addon recipes and factories. It preserves item, fluid, chemical, and energy requirements. Native power, security, mode, structure, and processing conditions remain required. World mechanics without a recipe or conversion contract are not inferred.

## Building

Use Java 25 to run the included Gradle wrapper:

```powershell
.\gradlew.bat test build
```

On Linux or macOS, use `./gradlew test build`.

The installable JAR is `build/libs/astral_repository-26.3-neoforge-1.12.1.jar`.

## Source and validation

Version 1.12.1 passes 186 unit tests and the normal release build. Shared regressions cover recipe process labels, quantity selection, bounded machine-slot assignment, and existing planner and transfer behavior. A focused NeoForge 26.3 dedicated-server fixture also passed five physical brewing steps, exact potion Tome inscription, charged-fuel reuse, and bottle remainders. Mekanism changes on this edition received build/source validation, without an installed native Mekanism runtime audit. The original NeoForge 1.21.1 edition separately passed 223 required GameTests with actual Mekanism, Industrial Foregoing, Immersive Engineering, and Oritech, plus hidden, muted, mouse-free client checks for Tome labels and quantity requests. Those checks do not establish native execution on every other loader or game version. See the [current validation record](https://github.com/CappleApple/astral-repository/blob/main/docs/validation.md). Runtime descriptions retained below document earlier release checks.

The branch contains all of its source inputs. [The 26.3 source generator](tools/modern-26.3.gradle) adapts the Java in `src/main/java` and tests in `src/test/java` into `build/generated/sources/minecraft263/`. Replacement classes are applied in this order: `src/overrides/java`, then `src/shared-overrides/java`, then the base sources. Edit these inputs rather than the generated files. Resources in `src/main/resources` already use the 26.3 formats.

The project contains 186 unit tests. Development runtime checks are available as `runServer -PsmokeTest` and `runClientSmoke -PclientSmoke`; the client fixture is compiled only when requested. Test fixtures are excluded from the release JAR. Server runs require accepting Minecraft's EULA in `run-server/eula.txt`.

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
