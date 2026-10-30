# Port validation

## Custom ingredient and tag invalidation checks (1.11.15)

All eight normal builds pass: 161 unit tests for each legacy/original edition, 162 each for modern Fabric, and 166 each for modern NeoForge, totalling 1,300. The four new shared cases cover cold item-domain lookup, dense same-item component variants, dynamic predicates, and incomplete custom display domains. A complete current item domain does not imply an immutable result: unknown custom-simple ingredients use the index but recompute answers. Non-simple tests reuse detached samples. Client tag callbacks clear cached answers and advance the epoch used by EMI inventory equality.

Moonlight's exact tested item-only implementations are `1.21.1-3.7.2` on NeoForge/Fabric and `1.20-2.16.38B` on Forge/Fabric. Other versions/classes keep generic matching. Forge's domain can be invalidated without replacing the recipe, so its adapter recomputes answers; its live fixture changes the domain with unchanged stock and verifies the updated result. The official Modrinth API returned no matching Moonlight release for Fabric/NeoForge 26.2/26.3 during this check. Modern tests exercise the generic APIs, not an installed Moonlight release.

The original [cold-transition fixture](../docs/validation.md#nexus-and-recipe-screen-transitions-11115) uses three development clients with the reported optional-mod versions. The seven port editions use packaged JARs. Legacy viewer fixtures compare actual Moonlight matching against exhaustive matching, preserve named-component/cursor behavior, and test tag-update invalidation. Forge also verifies a changing custom-simple domain with the same recipe and inventory, an incomplete non-simple display list, prototype reuse, and server-cause tag events leaving the client epoch unchanged.

The legacy custom differential cases use programmatically constructed recipes. Their separate registered-viewer fill checks use the existing vanilla recipe fixture. The original NeoForge fixture additionally verifies registered Moonlight recipes through actual client-to-server fill and crafting packets. Both legacy Fabric editions and all four modern editions also pass separate optional-mod absence profiles.

The modern Fabric fixture tests 1,024 cold custom-simple recipes through Fabric's public API, a live non-simple predicate accepting an item absent from its display list, reused prototype identities, and natural client tag callbacks. Existing registered JEI availability, off-search packet fill, conservation, physical cooking and late-workstation fixtures remain in use. Exact binaries, source contracts, logs and preserved preliminary attempts are under ignored `build/nexus-transitions/`.

## Nexus recipe availability performance (1.11.14)

All eight normal builds and unit suites pass: 157 tests each for NeoForge 1.21.1, Forge 1.20.1 and both legacy Fabric editions; 158 each for Fabric 26.2/26.3; 162 each for NeoForge 26.2/26.3. The six maintained source families share the same detached availability index and regression tests. `python ports/tools/check_recipe_availability_sources.py` verifies those shared files remain identical. Modern 26.3 builds use their normal generated-source paths.

The new tests cover 10,000 identities, 1,000 recipe objects and 100 repeated passes with exact operation counters, as well as overlapping ingredients, exact component identities, negative answers, recipe replacement and inventory replacement. This establishes cache/index work bounds; the real-client fixtures establish menu invalidation and viewer integration. The original [large-network client comparison](../docs/validation.md#nexus-recipe-availability-performance-11114) runs separately with EMI and JEI.

Packaged Fabric 26.2/26.3 clients pass with their matching JEI versions. The registered handler retains the same availability stock across 1,000 unchanged queries and updates after local count/component/cursor changes. A nine-ingot recipe with two named local ingots and seven cursor ingots succeeds; six cursor ingots fail, checking that cursor stock is counted once. Existing off-search fills, physical cooking, conflicting-recipe failure/refunds and workstation replacement checks also pass.

NeoForge 26.2/26.3 packaged clients pass the same 1,000-query cache checks with JEI, alongside their existing processing, cancellation, conflict and late-workstation checks. All four modern editions also pass separate profiles without optional mods. Modern NeoForge runs the release JAR through ModDev without production source directories; Fabric uses Loom production launches.

Both legacy Fabric editions pass separate EMI, JEI and optional-mod absence profiles. The installed Forge 1.20.1 loader passes with standalone EMI and standalone JEI. These clients check at least 200 unchanged queries, in-place local count/components, cursor changes, real network ingredient removal/restoration, off-search recipe fill and exact conservation. Forge also checks a custom ingredient whose external state changes without an inventory update. Matching Polymorph widgets retain their selected outputs. Exact versions and final JAR provenance are in `build/nexus-performance/legacy-fabric/validation-matrix.json`, `build/nexus-performance/forge/successful-manifest.json`, `build/nexus-performance/NFmodern/matrix.json` and `build/recipe-performance/fabric-modern-runtime.json`.

Fabric 1.21.1's two empty GameTest structure templates are excluded from the release JAR and copied into the separate test mod's resources.

Evidence is under ignored `build/recipe-performance/` and `build/nexus-performance/`. Availability caches are client-only; server-authoritative recipe fill, power, access and extraction rules are unchanged. No matching 26.2/26.3 EMI release was available in the earlier integration check, so modern runtime claims remain limited to JEI.

## Recipe compatibility fixes (1.11.13)

All eight editions pass their normal builds and unit suites. The reported recipe-transfer and processing paths were also exercised in Minecraft on every edition, using matching published optional mods and separate profiles without those mods.

| Target | Unit tests | Recipe viewers run separately | Polymorph / Iron Furnaces |
| --- | ---: | --- | --- |
| NeoForge 1.21.1 | 151 | EMI 1.1.24; JEI 19.52.0.421 | Polymorph 1.2.0; Iron Furnaces 4.3.2 |
| Forge 1.20.1 | 151 | EMI 1.1.24; JEI 15.74.0.230 | Polymorph 0.49.11; Iron Furnaces 4.1.8 |
| Fabric 1.20.1 | 151 | EMI 1.1.24; JEI 15.74.0.230 | Polymorph 0.49.11; no matching upstream Iron Furnaces release |
| Fabric 1.21.1 | 151 | EMI 1.1.24; JEI 19.70.0.462 | Polymorph 1.2.0; no matching upstream Iron Furnaces release |
| Fabric 26.2 | 152 | JEI 30.48.0.242 | No matching upstream Polymorph or Iron Furnaces releases |
| Fabric 26.3 | 152 | JEI 31.17.0.95 | No matching upstream Polymorph or Iron Furnaces releases |
| NeoForge 26.2 | 156 | JEI 30.48.0.242 | No matching upstream Polymorph or Iron Furnaces releases |
| NeoForge 26.3 | 156 | JEI 31.17.0.95 | No matching upstream Polymorph or Iron Furnaces releases |

Upstream availability was checked on 2026-10-28. EMI also has no matching 26.2/26.3 release. Required MezzConfig dependencies and exact downloaded files/hashes are recorded in the evidence manifests. No test substitutes a mod for a different Minecraft version.

Packaged client checks receive 81 exact stored identities, hide the required ingredient with a search, retrieve the viewer's registered Nexus transfer handler, fill a real shaped recipe, and check ingredient conservation. Matching legacy profiles use Polymorph's actual widget and selection packets. Native result clicks and real furnace processing are checked with optional mods present and absent. Conflicting furnace recipes either use the installed Polymorph API or fail explicitly and return unconsumed inputs.

Late-workstation tests wait for the initial coverage scan to finish, then place a furnace through ordinary world edits. Actual Nexus requests must discover it and consume real inputs and fuel. Modern Fabric retains a crafting table in the workshop and asserts that smelting, blasting, and smoking use the correct physical processor. This catches a missing refresh that previously let a table conversion be selected while the new furnace remained undiscovered. Furnace lit-state changes retain the running network and service.

The expanded checks fixed missing `jei_mod_plugin` metadata on all Fabric editions, added the 1.20.1 Polymorph API bridge, corrected Forge's vanilla fuel fallback, and added guarded block-identity discovery notifications. The artifact collector now rejects missing Fabric recipe-viewer entrypoints. Test classes, conflict recipes, and optional test binaries are excluded from release JARs.

Fabric checks use Loom production launches. Forge uses an installed Forge distribution with the reobfuscated release and a separate test mod. Modern NeoForge loads the release JAR through ModDev with production source folders removed; these are not retail-installer launches. Loaded class locations are checked against the release JAR. Original NeoForge uses development clients and 166 server GameTests; see its [validation record](../docs/validation.md#recipe-compatibility-fixes-11113). All clients run hidden, muted, and without capturing the mouse. These checks do not establish arbitrary-modpack compatibility or support Iron Furnaces factory/generator modes.

Ignored local evidence:

- `ports/forge-1.20.1/build/recipe-runtime/successful-manifest.json`
- `build/recipe-runtime-legacy-fabric/matrix.json`
- `build/modern-fabric-compat/matrix.json`
- `build/recipe-cross-loader/neoforge-matrix.json`
- `build/recipe-cross-loader/original-late-discovery.log` and `original-plain-final.log`

The manifests include successful receipts, exact optional-mod versions, loaded-JAR provenance, hashes, normal build results, and exclusion audits. Failed attempts remain alongside the successful evidence. Forge's offline build used its existing cached mapped Curios artifact through an ignored local init script after the normal offline resolver failed.

## Fabric 1.20.1 shader compatibility - 1.11.12

Validated on 2026-10-13 with Fabric Loader 0.19.5, Fabric API 0.92.12+1.20.1, and Java 17. The packaged client loaded Iris 1.7.6+mc1.20.1, Sodium 0.5.12-beta.2+mc1.20.1, Indium 1.0.36+mc1.20.1, and Complementary Unbound r5.9.3. The test launcher pins LWJGL 3.3.1 for this Sodium release.

The pre-fix client reproduced [issue #1](https://github.com/CappleApple/astral-repository/issues/1): placing a geode changed zero of 8,100 stable center pixels. The fixed client changed 8,096 of those pixels with the same shader pack enabled. Captures were also inspected separately.

All 146 JUnit tests and 12 packaged dedicated-server transit regressions passed. The server checks used a fresh test world; saved recovery data in a reused world can contaminate their fixed fixture coordinates.

The packaged client passed eight placed-world visibility cases with shaders enabled, disabled, re-enabled, and without Iris installed: geode, budding block, three bud sizes, two cluster orientations, and a real baked Storage Crystal model. Each mode also passed opaque-wall occlusion. The fixture checks live material readiness across shader toggles and asserts a hidden window, muted audio, and an ungrabbed mouse. The node pixel check measures the complete model, including its ordinary stone surfaces. Inventory items, armor trims, and interface visuals were not separately pixel-tested in this fixture.

From the repository root, with Java 21 for Gradle and the Java 17 toolchain installed:

```powershell
.\ports\fabric-1.20.1\gradlew.bat -p ports/fabric-1.20.1 productionGeodeShaderSmoke
.\ports\fabric-1.20.1\gradlew.bat -p ports/fabric-1.20.1 productionGeodeShaderSmoke -PirisTest
```

The Iris profile expects these downloaded files in `build/issue-1/dependencies/`: `iris-1.7.6+mc1.20.1.jar`, `sodium-fabric-0.5.12-beta.2+mc1.20.1.jar`, `indium-1.0.36+mc1.20.1.jar`, and `ComplementaryUnbound_r5.9.3.zip`. Test mods and rendering dependencies are excluded from the release JAR.

Ignored local evidence: `build/issue-1/before/`, `build/issue-1/artifact-audit.json`, and `ports/fabric-1.20.1/build/production-geode-shader-{iris,plain}/geode-shader-captures/`. The JAR audit checked loader/version metadata, Java 17 bytecode, license, resources, test exclusion, and absence of bundled or required Iris/Sodium/Indium dependencies.

## 1.11.11 — 2026-09-20

All eight release builds passed 1,180 JUnit tests with no failures or skipped tests. The standalone branch projects were built independently. Release metadata, Java bytecode, license contents, and exclusion of test fixtures were checked before collecting the JARs.

| Target | JUnit | Server checks | Client checks |
| --- | ---: | --- | --- |
| NeoForge 1.21.1 | 146 | 148 GameTests, both plain and with Lithium | 24 controlled cloud-depth cases in Fast, Fancy, and Fabulous modes |
| Forge 1.20.1 | 146 | 22 GameTests; installed release JAR and second-process restart | Installed release JAR, gameplay, and 24 cloud-depth cases |
| Fabric 1.20.1 | 146 | 12 transit regressions; production-server restart with four media in flight | Packaged gameplay and 24 cloud-depth cases |
| Fabric 1.21.1 | 146 | 12 transit regressions; production-server restart with four media in flight | Packaged gameplay and 24 cloud-depth cases |
| Fabric 26.2 | 147 | 12 transit regressions; server restart | Packaged gameplay with JEI/Jade; classic and improved transparency |
| Fabric 26.3 | 147 | 12 transit regressions; server restart | Packaged gameplay with JEI/Jade; classic and improved transparency |
| NeoForge 26.2 | 151 | 12 transit regressions; packaged server restart | Packaged gameplay; classic and improved transparency |
| NeoForge 26.3 | 151 | 12 transit regressions; packaged server restart | Packaged gameplay; classic and improved transparency |

Transit coverage includes delayed arrival, immediate mode, one-tick pipelining, pending stock limits, endpoint routing, save/load, failed-delivery recovery, and conservation. Item, fluid, and energy checks use each loader's native storage interfaces. Source checks use a registered test adapter; Ars Nouveau was not installed.

Legacy cloud checks compare controlled GPU output for nearby and occluded resource sprites and binding beams. Modern checks exercise actual resource, fallback item-icon, and beam draws in both transparency modes; they do not perform the legacy pixel-depth comparison. The 26.3 checks also cover custom transparency pipelines and enchanted-item rendering. Clients ran hidden, muted, and without mouse capture. Vulkan was not tested.

The standalone legacy and NeoForge artifacts are byte-identical to the runtime-tested JARs. Modern Fabric artifacts have identical ZIP-entry contents; archive timestamps differ. Modern NeoForge packaged checks use ModDev with production source directories removed and the release JAR installed, rather than a retail installer.

No new 10,000-rune TPS benchmark or ten-layer crafting stress run was performed for this patch. Optional-mod coverage from 1.11.9 below is historical unless explicitly included above.

Local evidence, relative to the repository root:

- `build/validation-1.11.11-plain.log` and `build/validation-1.11.11-lithium-final.log`
- `build/publication/v1.11.11/forge-validation.json`
- `build/publication/v1.11.11/legacy-fabric-validation.json`
- `build/publication/v1.11.11/modern-fabric-report.json`
- `build/publication/v1.11.11/modern-neoforge-report.json`
- `build/publication/v1.11.11/artifact-audit.json`

These are ignored build outputs. Reproduction commands and loader-specific fixture details are retained below.

## 1.11.9 validation record

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
