# Astral Repository — Minecraft 26.2 NeoForge

A magical storage and automation network: place crystals around a workshop, teach a bookshelf the products you want, and let the network find storage, route resources, and coordinate ordinary workstations.

This branch builds Astral Repository **1.12.0** for **Minecraft 26.2**, **NeoForge 26.2.0.88 or newer within Minecraft 26.2**, and **Java 25**. Install the matching JAR on both the client and server. No additional mod is required.

Version 1.12.0 keeps Nexus crafting ingredients and the selected recipe between visits, prevents conversion loops, and combines stored-block conversions with other recipe paths. Recipe Tomes can block individual final or intermediate recipes. The default red X Blocking Rune excludes its container without moving the contents; the wand's linking glint follows the selected rune's painted color. See the [changelog](CHANGELOG.md).


The Nexus grid is a saved personal workspace shared by your Nexus menus. Ordinary deposits skip processing machines. Inscribe a final output in a Recipe Tome, browse an alternative or intermediate output, then select **Block recipe** without inscribing that intermediate as the new target. **Allow recipe** restores the selected route. Place the default **Blocking Rune** on a container to exclude it; remove or disable the rune to reconnect it.

## Building

Use Java 25 to run the included Gradle wrapper:

```powershell
.\gradlew.bat test build
```

On Linux or macOS, use `./gradlew test build`.

The installable JAR is `build/libs/astral_repository-26.2-neoforge-1.12.0.jar`.

## Source and validation

Version 1.12.0 passes 173 unit tests and the normal release build. The shared tests cover conversion-loop rejection, mixed recipe paths, net additional output, maximum requests, and recipe exclusion matching. This edition did not receive a new runtime-client audit for 1.12.0. The original NeoForge edition additionally passed 178 server GameTests in both ordinary and actual Polymorph/Iron Furnaces profiles, plus focused wand/tome UI client checks. See the [current validation record](https://github.com/CappleApple/astral-repository/blob/main/docs/validation.md). Runtime descriptions retained below document earlier release checks.

The Java implementation and tests are in `src/main/java` and `src/test/java`. Resources are in `src/main/resources`.

The project contains 173 unit tests. Development runtime checks are available as `runServer -PsmokeTest` and `runClientSmoke -PclientSmoke`; the client fixture is compiled only when requested. Test fixtures are excluded from the release JAR. Server runs require accepting Minecraft's EULA in `run-server/eula.txt`.

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

The packaged JAR passed the gameplay and rendering checks with JEI 30.29.0.201, Jade 26.2.10, and Curios 16.0.0+26.2 installed. These are publisher-designated stable releases. The Curios check equipped Astral Goggles in the actual head slot and verified server detection, client synchronization, and rendering.

Other integration hooks remain optional and were not exercised in a complete third-party modpack. No matching public Patchouli or EMI release was available during validation, so those hooks remain inactive without a matching release. The Field Guide requires Patchouli.

## License

[CC BY-NC-SA 4.0 with additional permission for Minecraft modpacks and servers](LICENSE). The additional permission allows use in commercial or monetized modpacks and servers; standalone paid distribution is excluded. See `LICENSE` for the complete terms supplied with the project.
