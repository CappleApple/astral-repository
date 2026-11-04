# Astral Repository — Fabric 1.21.1

A magical storage and automation network for Minecraft 1.21.1. Crystals connect nearby storage, relay resources, and coordinate crafting through ordinary workstations. This is Astral Repository **1.12.0**, on branch `CappleApple/fabric-1.21.1`.

Version 1.12.0 keeps Nexus crafting ingredients and the selected recipe between visits, prevents conversion loops, and combines stored-block conversions with other recipe paths. Recipe Tomes can block individual final or intermediate recipes. The default red X Blocking Rune excludes its container without moving the contents; the wand's linking glint follows the selected rune's painted color. See the [changelog](CHANGELOG.md).

## Installation

Install `astral_repository-fabric-1.21.1-1.12.0.jar` on the client and server. Requires **Minecraft 1.21.1**, **Fabric Loader 0.19.5 or newer**, **Fabric API 0.116.17+1.21.1**, and **Java 21**. Configuration, energy, and applicable model libraries are bundled in the mod JAR.

Trinkets enables the goggles face slot; goggles also work in the normal helmet slot. Patchouli enables the Astral Field Guide. EMI adds recipe and menu integration, and Jade adds block information. These optional mods are not bundled. The explicit Refined Storage Fabric 1.21.1 adapter was not exercised with a complete optional storage network during these checks.

## Start a workshop

1. Collect Astral Gems from an Astral geode and craft a Storage Nexus.
2. Place the Nexus near chests or barrels and open it after automatic discovery.
3. Extend the network with same-channel Relay Crystals. Storage Crystals provide their own capacity-based storage.
4. Inscribe a Recipe Tome and put it in a nearby Chiseled Bookshelf to teach the network its recipe. Place the required workstation nearby and supply ingredients through connected storage.

Right-click the Attunement Wand in the air to choose or edit rune presets. Place a Push or Pull rune on a container, then Shift-right-click the glyph with the wand to assign target containers.


The Nexus grid is a saved personal workspace shared by your Nexus menus. Ordinary deposits skip processing machines. Inscribe a final output in a Recipe Tome, browse an alternative or intermediate output, then select **Block recipe** without inscribing that intermediate as the new target. **Allow recipe** restores the selected route. Place the default **Blocking Rune** on a container to exclude it; remove or disable the rune to reconnect it.

## Building

Run Gradle with **JDK 21**.

```powershell
.\gradlew.bat test build
```

On Linux or macOS, use `./gradlew test build`. The output is `build/libs/astral_repository-fabric-1.21.1-1.12.0.jar`. All source sets and resources are inside this branch; no sibling checkout is required.

## Validation

Version 1.12.0 passes 168 unit tests and the normal release build. The shared tests cover conversion-loop rejection, mixed recipe paths, net additional output, maximum requests, and recipe exclusion matching. This edition did not receive a new runtime-client audit for 1.12.0. The original NeoForge edition additionally passed 178 server GameTests in both ordinary and actual Polymorph/Iron Furnaces profiles, plus focused wand/tome UI client checks. See the [current validation record](https://github.com/CappleApple/astral-repository/blob/main/docs/validation.md). Runtime descriptions retained below document earlier release checks.

The earlier release checks included 161 JUnit tests and 12 packaged-server rune transfer regressions. These cover delayed and instant transfers, one-tick pipelines, stock limits, rejected deliveries, saved shipments, and actual storage endpoints for network-bound runes. The packaged client fixture uses the remapped release JAR with a separate test mod. It covers all ten blocks on all six placement faces, survival item consumption, stateful storage break/re-place, stock-limited rune transfer, translated item names and tooltips, Nexus withdrawal and deposit packets, ordinary crafting, Recipe Tome inscription, bookshelf discovery, and a completed autocrafting job.

```powershell
.\gradlew.bat productionRuneTransitSmoke
.\gradlew.bat productionGameplaySmoke
.\gradlew.bat productionPersistenceRead
.\gradlew.bat productionIntegrationGameplaySmoke
```

`productionPersistenceRead` first runs a separate write process, then reopens its saved world in a second server JVM. It checks large named item stacks, fluid, energy, upgrades, channel settings, rune settings, explicit links, and the resources and remaining travel time of saved shipments. The tasks create isolated worlds under `build/` and fail if their fixture does not report success. The persistence fixture accepts the Minecraft EULA for those local test servers. Client fixtures are hidden, muted, and release the mouse. The gameplay fixture also checks foreground, cloud, and opaque-block depth for resource sprites, trails, and rune beams in Fast, Fancy, and Fabulous graphics.

The optional profile, last checked for 1.11.9, uses Jade **15.10.6+fabric**, Patchouli **1.21.1-93-fabric**, EMI **1.1.24+1.21.1+fabric**, and Trinkets **3.10.0**. It repeats the gameplay workflow, opens the real Field Guide, checks EMI registration, and equips goggles in the Trinkets face slot with client/server synchronization. This does not establish compatibility with every optional-mod combination or cross-version world conversion.

The [main branch](https://github.com/CappleApple/astral-repository/tree/main) retains the original NeoForge 1.21.1 version and its documentation. Its loader APIs and compatibility descriptions do not automatically apply to this Fabric branch. See [port validation](https://github.com/CappleApple/astral-repository/blob/main/ports/VALIDATION.md) for the broader version matrix.

## License

[CC BY-NC-SA 4.0 with additional permission for Minecraft modpacks and servers](LICENSE).
