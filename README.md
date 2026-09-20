# Astral Repository â€” Fabric 1.21.1

A magical storage and automation network for Minecraft 1.21.1. Crystals connect nearby storage, relay resources, and coordinate crafting through ordinary workstations. The `CappleApple/fabric-1.21.1` branch contains Astral Repository 1.11.8 for Fabric.

## Installation

Install `astral_repository-fabric-1.21.1-1.11.8.jar` on both the client and server. Requires Minecraft **1.21.1**, **Fabric Loader 0.19.5 or newer**, **Fabric API 0.116.16+1.21.1**, and **Java 21**. Required configuration, energy, and applicable model libraries are bundled in the mod JAR.

Optional Trinkets provides the goggles accessory slot; the normal helmet slot works without it. Patchouli enables the Field Guide. Refined Storage Fabric 1.21.1 has an explicit storage adapter; full optional-mod networks were not part of the runtime checks.

## Start a workshop

1. Collect Astral Gems from an Astral geode and craft a Storage Nexus.
2. Place the Nexus near chests or barrels and open it after automatic discovery.
3. Extend the network with same-channel Relay Crystals. Storage Crystals provide their own capacity-based storage.
4. Put an inscribed Recipe Tome in a nearby Chiseled Bookshelf to teach its selected recipe to the network.

Right-click the Attunement Wand in the air to choose or edit rune presets. Place a Push or Pull rune on a container, then Shift-right-click the glyph with the wand to assign target containers.

## Building

Run Gradle with **JDK 21**.

```powershell
.\gradlew.bat test build
```

On Linux or macOS, use `./gradlew test build`. The output is `build/libs/astral_repository-fabric-1.21.1-1.11.8.jar`. Do not package a release with `-PclientSmoke`.

All source sets and resources live under `src/`.

## Validation

The port passed 146 JUnit tests and native server checks for item, fluid, and energy storage; transaction rollback; persistence; all 13 unconditional recipes; and ordinary and Silk Touch harvesting. A background client check covered nine standalone models, both custom shaders, and four rendered item models. The independent branch layout is checked again with `test build`; the gameplay checks apply to the same port implementation.

```powershell
.\gradlew.bat runServerSmoke
.\gradlew.bat runClientSmoke
```

The dedicated fixture uses `run-server-smoke/`. Accept the Minecraft EULA in that directory before running it. Client smoke runs are hidden, muted, and do not grab the mouse. Runtime checks do not establish complete optional-pack compatibility or cross-version world conversion.

The [main branch](https://github.com/CappleApple/astral-repository/tree/main) retains the original NeoForge 1.21.1 implementation and its documentation. See the [port validation record](https://github.com/CappleApple/astral-repository/blob/main/ports/VALIDATION.md) for the original runtime evidence. Its loader APIs and compatibility descriptions do not automatically apply to this Fabric branch.

## License

[CC BY-NC-SA 4.0 with additional permission for Minecraft modpacks and servers](LICENSE).
