# Astral Repository â€” Fabric 26.3

A magical storage and automation network for Minecraft 26.3. Crystals connect nearby storage, relay resources, and coordinate crafting through ordinary workstations. The `CappleApple/fabric-26.3` branch contains Astral Repository 1.11.8 for Fabric.

## Installation

Install `astral_repository-fabric-26.3-1.11.8.jar` on both the client and server. Requires Minecraft **26.3**, **Fabric Loader 0.19.5 or newer**, **Fabric API 0.161.0+26.3**, and **Java 25**. Required configuration, energy, and applicable model libraries are bundled in the mod JAR.

JEI and Jade APIs are supported by optional adapters. No public Patchouli, EMI, or Trinkets release was available for this Minecraft version during validation. Their hooks stay inactive without those mods; goggles work in the normal helmet slot. Full optional-mod packs were not part of the runtime checks.

## Start a workshop

1. Collect Astral Gems from an Astral geode and craft a Storage Nexus.
2. Place the Nexus near chests or barrels and open it after automatic discovery.
3. Extend the network with same-channel Relay Crystals. Storage Crystals provide their own capacity-based storage.
4. Put an inscribed Recipe Tome in a nearby Chiseled Bookshelf to teach its selected recipe to the network.

Right-click the Attunement Wand in the air to choose or edit rune presets. Place a Push or Pull rune on a container, then Shift-right-click the glyph with the wand to assign target containers.

## Building

Run Gradle with **JDK 25**.

```powershell
.\gradlew.bat test build
```

On Linux or macOS, use `./gradlew test build`. The output is `build/libs/astral_repository-fabric-26.3-1.11.8.jar`. Do not package a release with `-PclientSmoke`.

The 26.2 base Java sources and test fixtures live in `support/fabric-common/src/`. `tools/modern-26.3.gradle` generates the 26.3 sources under `build/generated/sources/`. Target replacements in `src/overrides/java/` take precedence over `support/minecraft263-overrides/src/main/java/`, which takes precedence over the base sources. Edit these inputs rather than generated files. Fabric test-bootstrap conversion is separate in `tools/fabric-validation.gradle`.

## Validation

The port passed 146 JUnit tests and native server checks for item, fluid, and energy storage; transaction rollback; persistence; all 13 unconditional recipes; and ordinary and Silk Touch harvesting. A background client check covered world rendering, rune packets and geometry, Nexus storage, ordinary and foil items, armor trims, goggles, and wand-editor artwork and text. The independent branch layout is checked again with `test build`; the gameplay checks apply to the same port implementation.

```powershell
.\gradlew.bat runServerSmoke
.\gradlew.bat runClientSmoke -PclientSmoke
```

The dedicated fixture uses `run-server-smoke/`. Accept the Minecraft EULA in that directory before running it. Client smoke runs are hidden, muted, and do not grab the mouse. Runtime checks do not establish complete optional-pack compatibility or cross-version world conversion.

The [main branch](https://github.com/CappleApple/astral-repository/tree/main) retains the original NeoForge 1.21.1 implementation and its documentation. See the [port validation record](https://github.com/CappleApple/astral-repository/blob/main/ports/VALIDATION.md) for the original runtime evidence. Its loader APIs and compatibility descriptions do not automatically apply to this Fabric branch.

## License

[CC BY-NC-SA 4.0 with additional permission for Minecraft modpacks and servers](LICENSE).
