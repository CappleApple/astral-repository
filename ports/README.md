# Astral Repository ports

Astral Repository 1.11.11 is available for the eight Minecraft and loader combinations below. The original Minecraft 1.21.1 NeoForge edition remains at the repository root.

## Installation

Install exactly one Astral Repository JAR matching the Minecraft version and loader on both the client and server. Fabric builds also require Fabric API. Required configuration, transfer, and legacy model libraries are bundled inside the Fabric JARs.

| Minecraft | Loader used for validation | Java runtime | Project |
| --- | --- | --- | --- |
| 1.21.1 | NeoForge 21.1.244 | 21 | [Original](../README.md) |
| 1.20.1 | Forge 47.4.10 | 17 | [forge-1.20.1](forge-1.20.1) |
| 1.20.1 | Fabric Loader 0.19.5, Fabric API 0.92.12+1.20.1 | 17 | [fabric-1.20.1](fabric-1.20.1) |
| 1.21.1 | Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1 | 21 | [fabric-1.21.1](fabric-1.21.1) |
| 26.2 | Fabric Loader 0.19.5, Fabric API 0.161.0+26.2 | 25 | [fabric-26.2](fabric-26.2) |
| 26.2 | NeoForge 26.2.0.88 | 25 | [neoforge-26.2](neoforge-26.2) |
| 26.3 | Fabric Loader 0.19.5, Fabric API 0.161.0+26.3 | 25 | [fabric-26.3](fabric-26.3) |
| 26.3 | NeoForge 26.3.0.7-beta | 25 | [neoforge-26.3](neoforge-26.3) |

The Forge build targets the recommended 47.4.10 release. The 26.3 NeoForge build uses a beta loader. These are separate game-version builds; this port does not provide a Minecraft world downgrade or cross-loader world conversion tool.

## Building

Run these commands from the repository root. Set `JAVA_HOME` to the JDK used to run Gradle. The 1.20.1 Fabric build also needs an installed JDK 17 for its compilation toolchain.

| Target | JDK for Gradle | Windows command |
| --- | --- | --- |
| Original 1.21.1 NeoForge | 21 | `.\gradlew.bat test build` |
| 1.20.1 Forge | 17 | `.\ports\forge-1.20.1\gradlew.bat -p ports/forge-1.20.1 test build` |
| 1.20.1 Fabric | 21 | `.\ports\fabric-1.20.1\gradlew.bat -p ports/fabric-1.20.1 test build` |
| 1.21.1 Fabric | 21 | `.\ports\fabric-1.21.1\gradlew.bat -p ports/fabric-1.21.1 test build` |
| 26.2 Fabric | 25 | `.\ports\fabric-26.2\gradlew.bat -p ports/fabric-26.2 test build` |
| 26.3 Fabric | 25 | `.\ports\fabric-26.3\gradlew.bat -p ports/fabric-26.3 test build` |
| 26.2 NeoForge | 25 | `.\gradlew.bat -p ports/neoforge-26.2 test build` |
| 26.3 NeoForge | 25 | `.\gradlew.bat -p ports/neoforge-26.3 test build` |

Each JAR is written to its project's `build/libs/`. Fabric outputs are remapped by Loom, and Forge outputs are reobfuscated by ForgeGradle. Distribute only the normal release JAR, excluding sources, development JARs, and audit fixtures. Rebuild without `-PclientSmoke` after a development client check.

After building all targets, collect the eight installable JARs and SHA-256 hashes:

```powershell
python ports/tools/collect_artifacts.py
```

Use `--branch-root <directory>` to collect the seven port JARs from standalone checkouts named `<loader>-<minecraft>` within that directory. The original NeoForge 1.21.1 JAR still comes from the repository root.

The collector writes `build/ports/`, including a manifest and an archive containing all targets. It checks the loader metadata, Minecraft dependency, main-class bytecode version, and absence of test entrypoints before copying a JAR.

## Integrations

The core item, fluid, and energy adapters use each loader's native storage APIs. Fabric fluid amounts are converted between droplets and millibuckets through transactional adapters, including rollback when a transfer cannot represent a complete millibucket.

Optional mods are not required for the core storage and crafting features. The audit ran installed recipe viewers, tooltips, books, and accessory mods; exact versions and coverage are listed in [Optional mod checks](VALIDATION.md#optional-mod-checks).

- Forge 1.20.1 was checked with Curios, JEI, EMI, Jade, and Patchouli together. Legacy Fabric was checked with Trinkets, EMI, Jade, and Patchouli, including equipped goggles and opening the Field Guide.
- Modern Fabric was checked with JEI and Jade. Modern NeoForge was checked with JEI and Jade, plus actual Curios head-slot goggles on 26.2. The 26.3 Curios hook has not been runtime-tested.
- The normal helmet slot works without an accessory mod. The Patchouli Field Guide requires Patchouli; Patchouli and EMI were not installed in the modern-target audit.
- AE2 and Refined Storage adapters are version-specific. Published API paths were inspected where available, but complete external networks and crafting jobs were not part of the runtime checks.

These checks cover the interactions described in the validation record, not every optional plugin feature or complete modpacks.

## Source layout

The older targets keep their version-specific sources in their own projects. `fabric-modern-common/` and `neoforge-modern-common/` contain the respective 26.2 implementations. The 26.3 projects generate version-adjusted sources under `build/generated/sources/` using [modern-26.3.gradle](tools/modern-26.3.gradle). Small replacement classes live in `modern-26.3-overrides/` and each target's `src/overrides/java/`; target overrides take precedence. Edit those inputs rather than generated Java files.

[migrate_resources.py](tools/migrate_resources.py) converts the original resource tree into a modern target. It handles recipe ingredients, loader conditions, item definitions, trim resources, OBJ material slots, and the 26.3 world-generation and shader formats. Loader metadata and mixin configurations are maintained separately.

```powershell
python ports/tools/migrate_resources.py --minecraft 26.3 --loader fabric --output ports/fabric-26.3/src/main/resources
```

See [Validation](VALIDATION.md) for the checks performed and their limits. The project uses [CC BY-NC-SA 4.0 with additional permission for Minecraft modpacks and servers](../LICENSE).
