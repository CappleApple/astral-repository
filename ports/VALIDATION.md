# Port validation

Validation date: 2026-09-20. Release version: 1.11.9. Loader, Fabric API, and Java versions are listed in [Installation](README.md#installation).

All eight builds passed: 1,180 JUnit tests in total, with no failures. The original edition also passed all 137 required GameTests. The port suites use focused loader-specific fixtures; they do not reproduce all 137 original GameTests.

| Target | JUnit | Server checks | Client and packaged-JAR checks |
| --- | ---: | --- | --- |
| Original 1.21.1 NeoForge | 146 | 137 GameTests | Original client harness retained; no fresh client audit |
| 1.20.1 Forge | 146 | 10 GameTests; installed Forge server and second-process restart | Retail client/server with reobfuscated JAR, Tome and autocrafting; optional development-client profile |
| 1.20.1 Fabric | 146 | Native storage fixture; two production server processes | Remapped JAR, base and optional-mod production clients |
| 1.21.1 Fabric | 146 | Native storage fixture; two production server processes | Remapped JAR, base and optional-mod production clients |
| 26.2 Fabric | 147 | Native storage fixture and development-server disk restart | Production JAR with JEI/Jade; gameplay and expanded rendering |
| 26.3 Fabric | 147 | Native storage fixture and development-server disk restart | Production JAR with JEI/Jade; gameplay and expanded rendering |
| 26.2 NeoForge | 151 | Native capabilities; packaged-JAR server and disk restart | Packaged JAR with JEI/Jade/Curios; gameplay and expanded rendering |
| 26.3 NeoForge | 151 | Native capabilities; packaged-JAR server and disk restart | Packaged JAR with JEI/Jade; gameplay and expanded rendering |

Modern NeoForge packaged checks load the release JAR through ModDev's development loader with production source directories removed and a separate test fixture. They are not retail-installer launches. Forge's packaged client and server use installed Forge distributions; Fabric production clients use Loom's production launch tasks. Loaded class locations are checked against the release JARs.

The 1.11.9 standalone projects were built independently and their artifacts inspected for version metadata, the exact license, and exclusion of test entrypoints/classes. Forge's raw and named compiler refmaps and Mixin member mappings are declared task outputs; the named refmap is present in the release JAR. A clean standalone Forge build restored compilation from cache and retained all three mapping outputs; both affected Mixin shadow members were verified in their reobfuscated form. Its 1.11.9 release also passed installed-client and server gameplay checks.

## Gameplay coverage

Every port has an actual world/client check for all ten registered blocks placed through block items, loaded English block/item names, and tooltips. Forge and legacy Fabric additionally check survival placement and item consumption on all six faces. Modern NeoForge checks survival placement; modern Fabric places the ten blocks through the creative player's normal use-item path.

| Family | Nexus and crafting | Rune transfers and storage |
| --- | --- | --- |
| Forge 1.20.1 | Actual client/server withdrawal and deposit; vanilla crafting clicks, ingredient refill, and grid conservation; real Recipe Tome catalogue/inscription, bookshelf discovery, and completed eight-plank autocraft consuming exactly two logs while preserving deposited iron | Named-item stock cap; scheduled fluid/energy transfers without overshoot; storage break/replacement; installed-server restart restores named items, upgrades, channel, fluid, energy, rune settings, links, taught recipes, and crafted output |
| Legacy Fabric | Actual client/server withdrawal and deposit; vanilla crafting clicks; Recipe Tome inscription and bookshelf discovery; completed eight-plank autocraft | Named-item stock cap; stateful storage break/replacement; two separate production server processes restore 512 named diamonds, upgrades, channel, 1,500 mB water, 2,200 energy, rune policy, and links |
| Modern Fabric | Packet-driven search, pickup, deposit, and shift transfers; manual crafting/refill; Tome inscription, bookshelf insertion, and completed autocrafting; client input opens remote and local storage menus | Filtered transfers with stock limits and conservation; upgraded storage drops/replacement and rune save/load round trips; separate development-server restart restores 23 diamonds, 1,000 mB water, and 500 energy |
| Modern NeoForge | Client/server pickup and deposit; manual crafting with named-ingredient refill and shift-output limits; Tome/bookshelf discovery and completed iron-trapdoor autocraft | Filtered transfer and counters; separate-process restart restores item components, storage settings, fluid, energy, rune layers/targets/cadence, and links |

Modern Fabric's storage replacement fixture obtains drops with `Block.getDrops`, removes the block, and places the dropped item again; it does not simulate survival digging. Its rune persistence check is a save/load round trip, separate from the narrower server restart check.

Native storage checks cover item, fluid, and energy discovery, transfer conservation, invalidation or transactional rollback as appropriate to the loader, and saved state. Fabric also checks invalid/zero transfer inputs, stable lookup identity, and fractional-fluid conversion rollback. All port server fixtures resolve the thirteen unconditional recipes. Fabric and modern NeoForge additionally assert ordinary and Silk Touch crystal-cluster/bud drops, including the 26.3 singular loot `condition` and `modifier` formats.

All audit clients run hidden, muted, and without grabbing the mouse. Modern clients render world crystals and minerals, synchronized rune glyphs, the Nexus, ordinary and foil items, astral-trimmed armor, worn goggles/armor, and the wand editor's embedded 3D item artwork. Captured screenshots were inspected separately from the automated assertions. Legacy model/shader checks remain alongside the newer gameplay checks.

## Optional mod checks

These are the versions actually installed for the audit, not a claim that every supported version or interaction was tested.

| Target | Installed optional mods |
| --- | --- |
| Forge 1.20.1 | Curios 5.14.1+1.20.1; JEI 15.56.0.205; EMI 1.1.24+1.20.1; Jade 11.13.3+forge; Patchouli 1.20.1-85-FORGE |
| Fabric 1.20.1 | Trinkets 3.7.2; EMI 1.1.24+1.20.1+fabric; Jade 11.13.3+fabric; Patchouli 1.20.1-85-fabric |
| Fabric 1.21.1 | Trinkets 3.10.0; EMI 1.1.24+1.21.1+fabric; Jade 15.10.6+fabric; Patchouli 1.21.1-93-fabric |
| Fabric 26.2 | JEI 30.29.0.201; Jade 26.2.11+fabric |
| Fabric 26.3 | JEI 31.1.0.12 beta; MezzConfig 0.5.12 beta, required by JEI; Jade 26.3.1+fabric |
| NeoForge 26.2 | JEI 30.29.0.201; Jade 26.2.10+neoforge; Curios 16.0.0+26.2 |
| NeoForge 26.3 | JEI 31.1.0.12 beta; MezzConfig 0.5.12 beta, required by JEI; Jade 26.3.1+neoforge |

The Forge optional profile passes normal gameplay with all five mods loaded. Legacy Fabric verifies equipped Trinkets goggles on the server and client, opens the actual Patchouli Field Guide, and checks EMI/Jade registration. Modern NeoForge 26.2 verifies goggles in the Curios head slot with the vanilla helmet slot empty, including synchronized detection and rendered appearance. Modern JEI/Jade profiles pass gameplay with their adapters loaded; every overlay and recipe-viewer action was not exercised.

Forge's combined EMI/JEI development profile still emits duplicate tag-recipe diagnostics and an external Curios tag-label diagnostic. Neither blocked the checked gameplay. AE2, Refined Storage, and complete third-party processing networks were not installed in this audit. Modern EMI/Patchouli and NeoForge 26.3 Curios runtime behavior remain unverified.

## Reproduction

Use the wrappers and Java versions in [Building](README.md#building). Append these tasks/arguments to the target's wrapper command:

| Target | Server | Client |
| --- | --- | --- |
| Original NeoForge | `runGameTestServer` | Existing original client tasks |
| Forge 1.20.1 | `runGameTestServer` | `-PgameplaySmoke runClient`; add `-PoptionalRuntime` for optional mods |
| Legacy Fabric | `runServerSmoke`; `productionPersistenceRead` runs both production write/read processes | `productionGameplaySmoke`; `productionIntegrationGameplaySmoke` |
| Modern Fabric | `runServerSmoke`, then repeat for disk-restart assertions | Build `-PclientSmoke gameplayFixtureJar`, then run `-PoptionalRuntime productionGameplay` without `-PclientSmoke` |
| Modern NeoForge | `-PsmokeTest runServer` | `-PclientSmoke runClientSmoke` |

Forge's `auditHarnessJar` builds the separate fixture for an installed Forge 47.4.10 client or server. The client uses `-Dastral_repository.testClient=true -Dastral_repository.portGameplay=true`; the server uses `-Dastral_repository.packagedAudit=true`. Keep the helper separate from the release JAR. These properties belong to isolated test instances, not normal installations.

For modern NeoForge's packaged checks, add the tracked [packaged validation init script](tools/neoforge-packaged-validation.gradle). From the repository root, for example:

```powershell
$packagedCheck = (Resolve-Path ports/tools/neoforge-packaged-validation.gradle).Path
.\gradlew.bat -p ports/neoforge-26.2 -PclientSmoke -I $packagedCheck runClientSmoke
.\gradlew.bat -p ports/neoforge-26.2 -PsmokeTest -I $packagedCheck runServer
.\gradlew.bat -p ports/neoforge-26.2 -PsmokeTest -I $packagedCheck runServer
```

Use `neoforge-26.3` for that target. The two server starts exercise write and restart assertions. Accept the Minecraft EULA for the isolated server instance. For a hidden NeoForge client, disable `earlyWindowControl` in that test instance's `config/fml.toml` before launching; the fixture controls the game window itself.

Run a normal `test build` without client-fixture properties before collecting release artifacts. The [collector](tools/collect_artifacts.py) rejects test classes and entrypoints.

## Evidence

JUnit XML is under each project's `build/test-results/test/`. The following local logs and reports are ignored build evidence, not published repository assets. Paths here are relative to the repository root:

| Target | Evidence |
| --- | --- |
| Original | `build/porting/original-1.11.9-regression.log` |
| Forge | `ports/forge-1.20.1/build/audit-final-server.log`, `audit-final-client-base.log`, and `audit-final-client-optional.log`; installed autocraft/restart results in `ports/forge-1.20.1/build/packaged-server/autocraft-first-start.log` and `autocraft-restart-final.log`; retail client `ports/forge-1.20.1/build/packaged-client/retail-cached-final.log` and `retail-cached-final-validation.json`; clean-cache proof in the standalone project's `autocraft-cached-clean.log` |
| Legacy Fabric | Each target's `build/production-integrations-1.11.9.log`; `build/publication/v1.11.9/branches/fabric-<version>-standalone-production.log` and `fabric-<version>-standalone-final.log` |
| Modern Fabric | Each target's `final-gameplay-validation.log`; `ports/fabric-26.3/final-client-access.log`; final 26.2 client in `build/publication/v1.11.9/branches/fabric-26.2/packaged-gameplay-validation.log` |
| Modern NeoForge | Each target's `packaged-gameplay-server.log`; `ports/neoforge-26.2/packaged-curios-client.log`; `ports/neoforge-26.3/packaged-optional-client.log`; `build/porting/audit/neoforge-audit.json` |
| Standalone builds | Reports under `build/publication/v1.11.9/`, including the legacy Fabric and NeoForge branch-validation reports and Forge's `build/publication-verification.json` inside its standalone project |

Gameplay captures/results are under Forge's `run-gameplay/gameplay-captures/` and `build/packaged-client/gameplay-captures/`, legacy Fabric's `build/production-gameplay/gameplay-captures/` and `build/production-integrations/gameplay-captures/`, and modern Fabric's `build/production-gameplay-optional/captures/`. Modern NeoForge captures are under `build/smoke-client/captures/`.

## Limits

The checks do not establish compatibility with complete modpacks, every optional integration, every editor workflow, or cross-version/cross-loader world conversion. Original-world migration and external AE2/Refined Storage networks remain untested. Loaded optional APIs and successful gameplay checks are narrower evidence than exhaustive third-party compatibility.
