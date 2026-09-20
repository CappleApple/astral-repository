# Validation record

## 1.11.11 Automatic rune travel (NeoForge 1.21.1)

`test build` passes all 146 unit tests. All 148 required dedicated-server GameTests pass both without Lithium and with Lithium `0.15.4+mc1.21.1`. Evidence: `build/validation-1.11.11-plain.log` and `build/validation-1.11.11-lithium-final.log`.

New tests verify delayed and instant Push/Pull transfers for items, fluids, energy and Source; consecutive one-tick departures with separate arrival deadlines; config changes during flight; shared destination stock reservations; full/removed destination refunds; and non-replaying recovery after a provider throws following insertion. Saved-data round trips preserve every medium, physical endpoint, policy anchor and remaining travel time across a reset server clock. Network-bound tests use different physical storage distances, replace the network component during flight, and verify restored endpoints still enforce live filters. A rate-limited energy receiver accepts consecutive in-flight batches without serializing dispatches.

These checks exercise authoritative server inventories and the same route durations used by visual packets. No new client visual capture or 10,000-rune timing run was performed for this change. The same fixes were subsequently ported to all seven other targets for 1.11.11; see the [port validation record](../ports/VALIDATION.md).

## 1.11.10 Cloud depth (NeoForge 1.21.1)

Fluid, energy, source and flat item transfers now render after clouds in Fast/Fancy. Fabulous uses the particle target and records sprite depth for the vanilla transparency compositor. Binding beams preserve the color-only glow/core pass, then replay depth for Fabulous after both layers finish.

`runClientSmoke -PcloudDepthOnly` checks real effect shaders on the GPU against deterministic cloud-depth geometry and an opaque foreground obstruction. It checks water, lava, energy, source, rune-beam materials and fading trails in Fast, Fancy and Fabulous. Fabulous uses Minecraft's actual transparency targets and compositor. This is a controlled geometry test, not a screenshot comparison of naturally generated cloud shapes. All 24 cases pass; the earlier 15-case pass also succeeded with Sodium, Lithium and AsyncParticles installed. Screenshots and per-case measurements are under `build/client-smoke/cloud-depth-vanilla-final/` and `build/client-smoke/cloud-depth-performance/`.

The hidden, muted, mouse-free transfer stress client also passes with those three performance mods: 20,000 offers retain the configured 4,096 flights, drawing 128 item models, 3,968 resources and up to 444 trails. On the tested RTX 5070 Ti, the selected render-event scope measured 1.37 ms CPU median / 1.93 ms p95 and 0.094 ms GPU median / 0.744 ms p95. These are event-scope timings, not whole-frame FPS guarantees. See `build/validation-cloud-transfer-stress.log`. The CPU/GPU measurement hooks follow the selected transfer stage, which is now `AFTER_WEATHER` outside Fabulous.

The in-world binding feedback client passes with the same performance mods, including visible assigned/preview beams, hotbar gating and binding cancellation (`build/validation-cloud-binding-feedback.log`).

`test build` passes. See `build/validation-cloud-final.log`. These original-renderer checks preceded the 1.11.11 port work. Each port uses its Minecraft version's rendering pipeline; its checks are recorded in [port validation](../ports/VALIDATION.md).

## 1.11.8 Transfer bookkeeping and live validation

The build passes 146 unit tests and all 137 required dedicated-server GameTests, with and without Lithium `0.15.4+mc1.21.1`. New coverage checks nested item-handler calls, same-tick filter additions/removal, shared-provider invalidation, container replacement, capability invalidation, chunk demotion before unload, route snapshots, and observer coverage during distant traffic. Inventory/world operations remain on the server thread; the existing recipe and relay workers consume snapshots. Evidence: `build/validation-1.11.8-regressions-final.log` and `build/validation-1.11.8-plain-regressions.log`.

The same 10,000-rune fixture described below was rerun after reducing slot-index allocations, repeated chunk lookups, dirty-notification allocations, and distant visual-audience work. Each direct/relayed phase warms for 600 ticks and measures 600 ticks with two nearby codec observers. Every rune still transfers 64 real items every measured tick; each phase moves 384,000,000 items with quantities and components conserved. Cadence, route length, visual budgets, discovery budgets, and inventory probe limits were unchanged. Final measurements ran without JFR instrumentation.

| Mods / route | Achieved TPS | Work median | Work p95 | Work p99 | Maximum | Ticks over 50 ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Astral only, direct | 19.994 | 38.96 ms | 47.24 ms | 59.80 ms | 68.64 ms | 17 / 600 |
| Astral only, relayed | 20.173 | 47.79 ms | 57.06 ms | 67.68 ms | 82.88 ms | 197 / 600 |
| With Lithium, direct | 20.019 | 42.39 ms | 51.41 ms | 66.81 ms | 70.89 ms | 38 / 600 |
| With Lithium, relayed | 19.895 | 48.25 ms | 57.47 ms | 71.17 ms | 79.35 ms | 205 / 600 |

Average relay throughput improved from the previous 18.6 to 18.8 TPS to roughly 20 TPS on this machine, but about one-third of relay ticks still exceed 50 ms. **This does not establish a consistently stable 20-TPS result at 10,000 one-tick runes.** Short sampling windows can include Minecraft's catch-up ticks, producing averages slightly above 20. The six native container types, hardware, item-only workload, and observer/socket limits remain those documented below; the results are not a guarantee for arbitrary provider mods, larger player counts, or lower memory budgets.

Evidence is archived in `build/ten-thousand-rune-stress-1.11.8-final-plain/` and `build/ten-thousand-rune-stress-1.11.8-final-lithium/`, each containing `metrics.txt`, `ticks.csv`, `result.txt`, and `run.log`. The distributable `astral_repository-1.11.8.jar` excludes GameTest classes, fixture recipes, and optional-mod JARs; all 125 packaged JSON resources parsed successfully.

The deep crafting fixture was repeated on a fresh world with this server code and Lithium. Both concurrent 128-item orders planned in three ticks and completed all 3,072 operations across 12 stages and five machine types. Each stage processed 256 inputs, returning 256 final outputs. Material conservation, dependency order, arrival-before-processing, fuel delivery, and direct/relayed paths passed throughout. Across 12,854 ticks and 642.698 seconds, the server maintained 20.000 TPS: work median 2.30 ms, p95 4.73 ms, p99 5.89 ms, maximum 38.01 ms, with no ticks over 50 ms. The 16.54 GB maximum heap, four-tick fixture cooking recipes, normal processing/travel delays, and codec-observer limits remain as described in the earlier fixture section. Evidence: `build/deep-craft-stress-1.11.8-lithium/` and `build/validation-1.11.8-deep-craft-lithium.log`.

The updated wand PNG was copied byte-for-byte from the supplied 34x36 image. Its 473 visible pixels are fully opaque; there are no partial-alpha pixels to clean. The extruded geometry was regenerated and checked against every source pixel. All 144 large-crystal mask pixels are unchanged, while the changed grip pixels remain untinted. These are source/geometry checks; this texture-only update did not receive a new live client capture.

## 1.11.7 Worker routing, deep crafting, and performance mods

The build passes 139 unit tests and all 127 required dedicated-server GameTests with Lithium `0.15.4+mc1.21.1`. Regression coverage includes worker cancellation and topology replacement, prepared route reuse, live capability replacement, dense spatial hashes, per-medium cooldowns after config edits, bounded item hints and snapshot fallback, partial refunds, stock limits, and crafting output returning to storage instead of an idle processor. The distributable JAR excludes GameTest classes and stress-fixture recipes. Evidence: `build/validation-1.11.7-complete-regressions.log`.

### Ten thousand one-tick runes

The isolated dedicated-server fixture uses 10,000 rune layers on 10,000 distinct native containers across 50 separate networks, with 1,000 Relays/Nexuses and 400 loaded fixture chunks. Chests, trapped chests, barrels, droppers, disabled hoppers, and shulker boxes hold eight item types and 350 named component variants, plus filtered-out contents. Every rune has a distinct destination. Direct assignments use neighboring containers; relayed assignments cross 40 blocks. Each stage warms for 600 ticks and measures 600 more.

Every measured tick asserts that **each of the 10,000 runes moved 64 actual items**. Each stage transfers 384,000,000 items, with quantities and components conserved. No hidden rune-turn limit, replenishment loop, or raised discovery/transfer budget is used. Two fake nearby observers exercise actual visual packet encoding and decoding near the first network. Each stage emitted 1,200 batches containing 153,600 sampled visual offers; socket traffic and client rendering are separate tests.

The following final-build measurements use the same Ryzen 7 9800X3D, Java 21.0.12, and NeoForge 21.1.244 as the crafting test:

| Mods / route | Achieved TPS | Work median | Work p95 | Work p99 | Maximum | Ticks over 50 ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Astral only, direct | 19.999 | 45.26 ms | 64.36 ms | 74.16 ms | 81.45 ms | 108 / 600 |
| Astral only, relayed | 18.779 | 51.75 ms | 58.61 ms | 66.37 ms | 76.36 ms | 558 / 600 |
| With Lithium, direct | 20.002 | 45.97 ms | 53.17 ms | 58.40 ms | 67.25 ms | 82 / 600 |
| With Lithium, relayed | 18.639 | 52.12 ms | 59.88 ms | 67.77 ms | 77.94 ms | 569 / 600 |

The 10,000-rune relay case still exceeds the 50 ms tick budget with and without Lithium; this extreme load does not maintain 20 TPS. A throughput/conservation PASS is not a 20-TPS guarantee: this fixture records timing without treating a slow but correct transfer as data loss. The run covers item transfers, not 10,000 simultaneous fluid/FE/Source transfers or a combined crafting workload. Chunk generation, autosaves, arbitrary provider implementations, different CPUs, and tighter memory limits can change results.

Relay preparation completed 950 worker jobs and retained 19,000 tree states. The fixture verifies that the worker thread differs from the server thread; live world access and inventory transactions remain on the server thread. Initial routes can fall back to immediate server-thread searches until preparation is ready. Evidence: `build/ten-thousand-rune-stress-final-plain/{metrics.txt,ticks.csv,result.txt,run.log}` and the matching `build/ten-thousand-rune-stress-final-lithium/` archive.

```powershell
.\gradlew.bat runTenThousandRuneStress -PtenThousandRuneObservers=2
.\gradlew.bat runTenThousandRuneStress -PtenThousandRuneObservers=2 -PlithiumTest
```

This profile uses `build/ten-thousand-rune-stress/server/` and the previously accepted `run-server/eula.txt`. Run performance profiles separately, not concurrently.

### Twelve-stage crafting orders

`runDeepCraftStress -PlithiumTest` submitted two concurrent orders of 128 outputs through the actual Nexus menu action and packet codec. Each order required 12 dependent recipe stages across crafting tables, furnaces, blast furnaces, smokers, and stonecutters, with eight machines of each type. The fixture datapack lives in [`src/testFixtures/deep-crafting-datapack`](../src/testFixtures/deep-crafting-datapack). Its cooking recipes take four ticks; table/stonecutter delays, actual route-flight waits, production budgets, and the normal 32-processor limit remain in effect. Both jobs planned in three ticks. The second job waited for processing capacity and then completed normally.

All 3,072 operations completed, returning 256 final items to storage. Every stage processed exactly 256 inputs. Assertions checked dependency order, machine type, material conservation, input arrival before processing, fuel delivery, and direct/relayed paths. The observer decoded 3,072 flights, including 2,254 relayed paths, 562 direct machine paths, and 207 fuel flights. The first trial exposed finished output entering an unrelated idle blast furnace; the corrected full run completed both orders.

On an AMD Ryzen 7 9800X3D with Java 21.0.12 and NeoForge 21.1.244, 12,854 measured ticks over 642.7 seconds held **20.000 TPS**. Server work measured **2.46 ms median, 4.88 ms p95, 6.37 ms p99, and 53.42 ms maximum**; one tick exceeded 50 ms. Timing includes the final network/visual Post callback. Per-tick inventory and dependency verification is outside work samples but included in elapsed TPS; visual-arrival assertions inside the observer callback are included in server work. The observer uses actual packet encoding/decoding without socket traffic. The JVM maximum heap was 16.54 GB. Evidence: `build/validation-1.11.7-deep-craft-lithium.log` and `build/deep-craft-stress-lithium/{result.txt,metrics.txt}`. This exercises the five listed vanilla machine adapters, not arbitrary third-party recipes or processors.

```powershell
.\gradlew.bat runDeepCraftStress -PlithiumTest
```

The isolated world is under `build/deep-craft-stress/server/world`. Start from a fresh fixture world when repeating a failed or interrupted trial, since crafting recovery data intentionally persists. The fixture uses the previously accepted `run-server/eula.txt` and never modifies player saves.

### Client performance-mod checks

Hidden, muted clients loaded Lithium `0.15.4+mc1.21.1`, Sodium `0.8.13+mc1.21.1`, and AsyncParticles `21.1.4.4` together. The transfer profile offered 20,000 flights, retained the configured 4,096 active flights, and rendered up to 128 item models, 3,968 resource sprites, and 444 trails. On the **AMD Radeon(TM) Graphics** device selected for this run, render CPU time was **1.12 ms median / 1.23 ms p95**; the measured GPU scope was **0.116 / 0.128 ms**. The artificial admission burst took 11.30 ms separately. These measure Astral rendering in this fixture, not total-frame FPS or every third-party renderer.

The model profile passed all eight node appearances, six orientations, custom sprite geometry, crystal-only shader masks, parallax movement, and held/world view-bobbing correction. Screenshots retained blue facets and visible bases without missing textures. Both profiles assert muted audio and no mouse capture. Evidence: `build/validation-performance-client-transfer-stress.log`, `build/validation-performance-client-node-models.log`, and the archived `build/client-smoke/performance-mods-transfer/` and `performance-mods-node-models/` results. See [performance-mod compatibility](compatibility.md#performance-mods) for reproduction commands and version limits.

## 1.11.6 One-tick rune server workload

The build passes 109 unit tests and 120 required server GameTests. Regression coverage includes immediate filter edits on both halves of double chests, provider invalidation between simulation and commit, shared-provider notifications across networks, indexed rune save/reload/removal, route-cache bounds, and line-of-sight changes. Shortest-path tree tests compare 1,600 destination queries against independent searches and verify repeated queries avoid graph traversal. Evidence: `build/validation-1.11.6-final.log`.

`runRuneStress -PruneStressObservers=2` runs an actual dedicated server on an AMD Ryzen 7 9800X3D with Java 21.0.12. The fixture contains 1,280 vanilla inventories: chests, trapped chests, barrels, droppers, disabled hoppers, and shulker boxes. It has 1,024 primary rune layers, 192 additional layers sharing hosts, and 256 empty/full/filter-rejecting layers. Sixty-four barrels also expose fluid and energy capabilities and a Source adapter. Each selected busy rune must transfer exactly 64 real items every measured tick; disabled or skipped runes fail the fixture. All four resource totals are checked for conservation.

Each stage warms for 200 ticks and measures 600 ticks. Two fake nearby observers encode, decode, and discard actual animation batches, including their normal budgets. Timing runs from the beginning of the server tick through the final Post callback, after visual dispatch; Minecraft's built-in pre-Post timing omits this work. Fixture verification is timed separately, while elapsed TPS includes it.

| Active workload | Achieved TPS | Tick median | Tick p95 | Tick p99 | Tick maximum | Ticks over 50 ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Idle fixture | 20.004 | 0.80 ms | 1.43 ms | 1.88 ms | 4.72 ms | 0 |
| 256 busy runes | 19.991 | 4.79 ms | 6.51 ms | 8.44 ms | 12.48 ms | 0 |
| 1,024 busy runes | 20.004 | 13.26 ms | 16.95 ms | 19.38 ms | 28.00 ms | 0 |
| 1,216 busy and 256 blocked runes | 20.000 | 16.92 ms | 21.89 ms | 24.35 ms | 42.01 ms | 0 |

The highest-load stage moves 77,824 items per tick, plus 256 transfers per tick of each of fluid, energy, and Source. It keeps the requested cadence without a hidden global limit on rune turns. Evidence: `build/validation-1.11.6-rune-stress.log` and `build/rune-stress/{metrics.txt,ticks.csv,result.txt}`. Observer totals in the metrics include warmup; reported item totals include measured ticks only.

The separate aggregate profile uses real Relay and Nexus bindings. With 256 busy runes across eight networks containing four stores each, it held 20.003 TPS (6.73 ms p95, 11.23 ms maximum). The larger variant puts all 256 runes into one automatically connected network with 128 native storage providers, eight Relays, and eight Nexuses. After 800 warmup ticks, its 400 measured ticks held 20.002 TPS (23.20 ms median, 26.33 ms p95, 30.54 ms p99, 36.74 ms maximum; zero ticks over 50 ms). Every active rune moved 64 items on every measured tick, with automatic relay routes and exact conservation asserted.

The first dense run exposed a route-cache workload that repeatedly searched the graph and triggered the server watchdog. Reusing bounded shortest-path trees resolved the stall without changing cadence or reducing the workload. Evidence: `build/validation-1.11.6-network-rune-dense-tree.log`, `build/network-rune-stress/{metrics.txt,ticks.csv,result.txt}`, and the smaller profile preserved in `build/network-rune-stress-separate/`. Dense timing was measured with 512 allowed origin trees; the final limit is 8,192 origins under the same 262,144-state memory budget. The fixture uses fewer than 512 origins, so this avoids an additional cache-count limit for larger deployments without changing the measured working set.

Reproduce these opt-in benchmarks separately:

```powershell
.\gradlew.bat runRuneStress -PruneStressObservers=2
.\gradlew.bat runNetworkRuneStress -PruneStressObservers=2
.\gradlew.bat runNetworkRuneStress -PnetworkRuneStressDense -PruneStressObservers=2
```

The profiles use isolated directories under `build/`, require the previously accepted `run-server/eula.txt`, and do not modify player saves. They fail if measured p95 server work reaches 50 ms or sustained TPS falls below 19.5, in addition to throughput and conservation assertions. Test classes are excluded from the distributable JAR.

These results measure the stated loaded fixtures on this machine. They do not establish performance for every server CPU, arbitrary third-party provider implementations, chunk generation, connected-client socket traffic, or autosaves. Source uses the public adapter contract; this is not an Ars Nouveau or Sophisticated Storage integration benchmark. Generic item handlers retain their bounded 512-slot scan, so very large inventories may need several initial scan passes before transfer readiness.

## 1.11.5 Transfer scaling and rendering corrections

The build passes 100 unit tests and 114 required server GameTests. New tests cover long-uptime frame precision, prepared-path equivalence across up to 130 nodes, relay spread, rune endpoints, and one-result-vector sampling allocation. Cosmetic sampler/audience tests cover 10,000 offers and 1,000 indexed observers. Server GameTests exercise actual payload encoding/decoding, mid-leg observers, per-player budgets, workstation overflow resets, and oversized item components. A 1,000-entry component-heavy station flood is limited to two encoding attempts and 8,192 bytes of encoding work at the 4,096-byte wire setting; real stack contents remain unchanged.

Measured server cosmetic dispatch in `build/validation-1.11.5-models-final.log`: 10,000 offers to two nearby observers and one distant observer produced one batch of 128 flights / 6,403 bytes per nearby observer. Offering took 9.51 ms and encoding/dispatch took 2.49 ms. Ten thousand observerless offers took 0.376 ms and produced no frames or packets. These timings measure cosmetic dispatch, not 10,000 complete inventory transactions or a whole factory's TPS.

Hidden, muted client profiles offered 10,000 flights, then another 10,000 while the originals remained active. The client retained 4,096 moving flights without evicting them, ignored a simulated server-time jump to 2^40 ticks, and froze its local clock under tick freeze. On this machine (GeForce RTX 5070 Ti):

| Scene | Visible population | Render CPU median / p95 | GPU median / p95 |
| --- | --- | --- | --- |
| Mixed resources | 128 item models, 3,968 resource sprites, up to 446 trails | 1.197 / 1.478 ms | 0.697 / 0.884 ms |
| Item stream | 128 item models, 3,968 flat icons | 1.193 / 1.341 ms | 0.290 / 0.294 ms |
| Distinct named item variants | 128 item models, 3,968 flat icons | 1.120 / 1.263 ms | 0.144 / 0.181 ms |

The distinct-variant profile starts with a cold icon cache and verifies at most 16 model resolutions per frame. Its first/worst recorded render frame took 11.30 ms; synchronous admission of the artificial 10,000-offer burst took 14.47 ms separately. Normal server delivery is capped at 128 sampled moving offers per player per tick by default. The mixed-resource and repeated-item measurements preceded the final cold-cache guard; the final distinct-variant profile exercises that guard. GPU query scope includes other AFTER_PARTICLES handlers between the fixture hooks. These are local animation measurements, not minimum-FPS guarantees for other hardware or arbitrary modded custom renderers.

Evidence: `build/client-smoke/transfer-stress/metrics.txt`, `transfer-stress-items/metrics.txt`, and `transfer-stress-unique/metrics.txt`; final unique run log: `build/validation-1.11.5-unique-final.log`. The resource-arrival client also passes fade-in/growth, gravity trails, varied endpoints, fade-out inside transparent containers, expiry, and unchanged server contents.

The final furnace client passes actual input and fuel arrival timing, vanilla smelting, and output return to storage. It receives raw-iron and coal flights to the furnace and an ingot flight directly back to the chest, without a Nexus detour. Evidence: `build/validation-1.11.5-furnace.log`.

The node-model client passes world and GUI rendering, six block orientations, shader masks, and parallax checks. World-bob tests compare the same screen pixels at four walking phases, both inside a volume cell and on its boundary: corrected shader differences stay below 0.000004 while the base texture visibly bobs. Real camera parallax still changes the interior. Final model screenshots were reviewed. All 165 crystal faces match the verified original 1.11.1 artwork/UVs; the 156 base, trim, and channel faces keep their proportional remap. Source and packaged-JAR resource audits pass. Evidence: `build/validation-1.11.5-models-complete.log` and `build/client-smoke/parallax/world_bobbing_metrics.txt`.



## 1.11.4 Book item textures



Installed the supplied 64 by 64 Field Guide and 23 by 23 Recipe Tome without resampling or padding. Pixel checks confirm every fully opaque source pixel is unchanged and final alpha values are only 0 or 255. Four faint Field Guide pixels were deleted; neither image required near-opaque promotion. The existing item models reference the replaced texture paths. Build and source/JAR resource audits pass. No client or server session was run for this texture replacement.



## 1.11.3 Shader bobbing and node UVs



The build passes 85 unit tests. New matrix tests cover bob translation and rotation with pre-existing hurt and item transforms, plus correction reset between hand and world rendering.



The hidden, muted client passes the node-model and parallax fixtures without capturing the mouse. The GPU check invokes the actual `GameRenderer.bobView` mixin at four walking phases. With screen displacement compensated by the fixture, corrected material differences are below 0.000001; the uncorrected control differs by 0.0031–0.0042. Eye translation, world-coordinate continuity, material rotation, animation speeds, and shooting-star checks also pass. This is an automated framebuffer comparison, not a manual walking session. Evidence: `build/validation-1.11.3-client.log` and `build/client-smoke/parallax/bobbing_metrics.txt`.



All 321 faces in the eight active OBJ models pass pairwise physical/UV-distance checks, atlas bounds, and agreement with their Blockbench sources. The remapping tool is idempotent. Front/rear world and inventory renders, six block orientations, and shader material masks pass; the base-texture model capture was visually reviewed. Source and packaged-JAR resource audits pass. Server GameTests were not repeated for these client rendering and asset changes.



## 1.11.2 Rune resource growth



The build passes 83 unit tests. Departure cases now also check half size at spawn, 75 percent size at 1.5 ticks, and full size after three ticks for all three resource types, all rune faces, and varied route durations. Ordinary items and sources without rune endpoints keep their original size.



The hidden, muted client verifies the size function used by the renderer returns radii 0.08, 0.12, and 0.16 alongside the existing fade-in. Trail inheritance, destination fading, expiry, and unchanged server inventory checks still pass. Source and JAR resource audits pass. Evidence: `build/validation-1.11.2-client.log` and `build/client-smoke/resource_arrival_metrics.txt`. Server GameTests were not repeated for this client-only change.



## 1.11.1 Rune resource departure fade



The build passes 83 unit tests. New cases check fluid, energy, and Source departures on all six rune faces, a three-tick smooth fade independent of route duration, and unchanged ordinary-item and non-rune departures. The hidden, muted resource client uses a real placed rune and verifies the shared rendering/trail opacity is 0 at spawn, 0.5 at 1.5 ticks, and 1 at three ticks. It confirms early trails inherit the fade, destination fade and expiry still work, and server inventory contents remain unchanged. The departure capture was reviewed. Source uses the fallback sprite; Ars was not installed for this check.



Evidence: `build/validation-1.11.1-client.log`, `build/client-smoke/resource_arrival_metrics.txt`, and `build/client-smoke/resource_departure.png`. Source and JAR resource audits pass. Server GameTests were not repeated for this client-only presentation change.



## 1.11.0 Texture cleanup, goggles, armor trims, and binding



The base-texture audit preserves all 8,957 fully opaque source pixels, makes 4,528 near-opaque pixels (alpha 240-254) fully opaque without changing RGB, and deletes 718 fainter pixels. All 20 textures retain visible artwork and binary alpha. The initial strict deletion erased three icons because their artwork was slightly translucent; the final rule follows the user's clarification. The goggles use an open 21-cuboid frame with proportionally mapped materials and 55-percent-opacity gem lenses. Non-equipped display contexts use a separate 16 by 16 icon, one pixel thick with filled sides.



The final build passes 81 unit tests and 110 required GameTests. New coverage checks all 18 vanilla trim patterns against six armor pieces, preserved damage and input stacks, duplicate-trim rejection, container-first multi-rune selection and toggling, rune-first switching, relay-network targets, randomized relay spread, bounded path offsets, continuous velocity, and rune-face departure/arrival directions. Evidence: `build/validation-1.11.0-final-server.log`. One numerical test initially normalized a displacement below Minecraft's threshold; it now compares the finite-difference velocity.



The trim client verifies all 36 worn-pattern sprites, all four armor item slots, separate base/trim render passes, and unchanged selection of ordinary trim materials. Changing shader opacity affects both worn and item trim pixels. The first capture caught item trim hidden by its base layer; the final material uses the same view-depth offset approach as armor layers. The corrected captures were visually reviewed. Evidence: `build/validation-1.11.0-trim-client.log` and `build/client-smoke/astral_trim_{base,shader,default}.png`.



The Curios goggles client passes equipment synchronization, cosmetic-slot behavior, helmet fit, all display-context selections, icon thickness, and lens shader response. Reviewed captures show the open frames, translucent lenses, and inventory/held/ground icons. Evidence: `build/validation-1.11.0-goggles-client.log`. Custom armor renderers and modded trim patterns were not runtime-tested.



The binding client sends actual use-item packets to select a container, assign and toggle multiple runes without losing that selection, cancel in air, then switch between rune-first selections. The synchronized wand glint and absence of unwanted menus/runes are checked. Evidence: `build/validation-1.11.0-binding-client.log`.



The final Patchouli texture client passes, and its contact sheet confirms all cleaned icons remain visible. All validation clients stayed hidden, muted, and mouse-free. The final JAR audit confirms trim definitions, palettes, atlas additions, goggles icon geometry, mixins, and runtime classes match the source, and contains no development fixtures. Evidence: `build/validation-1.11.0-textures-client.log` and `build/client-smoke/custom_item_textures.png`.



## 1.10.4 Reduced base textures



Installed all 20 user-supplied PNGs. The import checks every decoded RGBA pixel against the source and requires added padding to be fully transparent. Natural buds and the cluster retain 16 by 16 canvases; other padded sprites use square canvases. The unchanged wand keeps its supported 34 by 36 canvas and exact crystal mask. `art/reduced-textures.json` records dimensions, offsets, and hashes. The goggles runtime UVs and embedded Blockbench texture match the reduced atlas.



The build passes 79 unit tests. Source and JAR resource audits pass, including exact wand/orb hashes and unchanged natural block geometry. The hidden, muted texture client passes with Patchouli and captures all 14 refreshed items. The Curios goggles client passes, including worn lens shader changes, helmet fit, and cosmetic-slot behavior. The item sheet and helmet capture were visually reviewed. Both clients verify muted audio and no mouse capture. Evidence: `build/validation-1.10.4-build.log`, `build/validation-1.10.4-textures-client.log`, and `build/validation-1.10.4-goggles-client.log`. Server GameTests were not repeated for this asset-only update.



## 1.10.3 Goggles, attunements, visibility, limits, and resource effects



The final build passes 79 unit tests and 108 required GameTests with Curios installed. These cover the four server cadence caps and minimum intervals, configuration changes without cooldown bypass, maximum integer schedules, consumable Dimensional Attunement on both targets, old item-ID aliases, functional versus cosmetic goggles equipment, and painted-rune save/export/import independence. Evidence: `build/validation-1.10.3-build-server.log`.



Hidden, muted clients passed with and without Curios. Reviewed captures show custom goggles from the front and side, animated lens opacity, Curios-only equipment, cosmetics, and goggles over a diamond helmet. Both EMI and JEI clients verified Power Node visibility during server-driven false/true/false transitions, including open creative lists and the loaded Patchouli entry. Evidence: `build/validation-1.10.3-base.log`, `build/validation-1.10.3-curios-helmet.log`, `build/validation-1.10.3-power-emi.log`, and `build/validation-1.10.3-power-jei.log`.



The resource client captured fluid, energy, and Source batches approaching glass blocks, fading inside their destinations, and shedding gravity trails. It checked interior endpoints, fading, expiry, and unchanged server contents. The 14-item texture contact sheet was reviewed. The final no-optional-mod wand client verified that saving a placed rune writes its custom artwork and item overlays into the actual instance JSON. Evidence: `build/validation-1.10.3-final.log` (the client and all 107 then-current GameTests passed), `build/validation-1.10.3-preset-client.log`, and `build/client-smoke/{resource_arrival_*,custom_item_textures,goggles_*}.png`.



An earlier routing fixture placed a chest across its required line of sight; it now uses a clear route and explicitly tests chest occlusion. One new derivative test normalized a displacement below Minecraft's vector-normalization threshold; it now compares nonzero velocities. The final suites above passed after these fixture corrections. Source used the fallback texture; an installed Ars client was not tested. Resource audits confirm custom texture references, unchanged supplied wand/orb artwork, preserved natural geometry, matching packaged resources, and no development fixtures in the JAR.



## 1.10.2 Circular resource sprites and falling trails



The build passes 72 unit tests, including detached trail motion, gravity, fading, and expiry. The hidden, muted presentation client passes with no mouse capture; it asserts fluid and energy trails are emitted, fall below their release points, stay within the particle cap, and expire after their transfers finish. The captured frame was reviewed for circular fluid cropping and smaller matching sprites falling behind both resources. Evidence: `build/validation-1.10.2-client.log` and `build/client-smoke/flat_resource_transfers.png`.



## 1.10.0 Rune cadence, delivery timing, and resource presentation



The final build passes 70 unit tests. The base and Create/Visual Workbench compatibility runs pass 101 required GameTests; the BNS/EMI run passes 109. New cases cover independent item/fluid/energy/Source amounts and intervals on one host, conservation, persisted and portable preset settings, rejection of unsupported or invalid cadence edits, defaults, actual fluid identity in visual packets, and randomized splines with fixed endpoints and continuous relay tangents. Delivery tests cover staggered table arrivals, cancellation before arrival, pending furnace fuel, and the instant-automatic override. Evidence: `build/validation-cadence-server2.log`, `build/validation-1.10.0-compat.log`, `build/validation-1.10.0-final.log`, and the final base rerun in `build/validation-1.10.0-artifact.log`. The 1.10.0 JAR has valid JSON resources, the new production classes, matching metadata, and no development GameTest classes.



The hidden, muted client confirmed the four-resource popup autosaves through real session packets and restores server defaults. Screenshots were reviewed for the Priority layout, all four cadence rows, the loose held Gem at 0/18/100 percent overlay, and flat fluid/energy/Source sprites. A real two-source network recipe displayed two arrived ingredients on its table while waiting for the farther pair, then assembled and returned its result. The first visual review caught a missing energy texture; the corrected sprite was recaptured and reviewed. Source's optional Ars texture was not tested with Ars installed; the fallback was rendered. Sophisticated Storage itself was not installed for these runs; combined capability discovery was tested with real item/fluid/energy handlers and a registered Source provider. Evidence: `build/validation-presentation-client-final.log` and `build/client-smoke/{held_gem_*,rune_cadence_all_resources,rune_priority_layout,flat_resource_transfers,table_partial_arrival}.png`.



The furnace client check observed an empty input slot during the raw-iron flight, then arrived input waiting unlit while coal travelled, followed by real vanilla smelting and return of one ingot. The binding client checks verified rune-origin particles only without assignments, hotbar gating, assigned and aimed beams without depth writes, mining cracks, and block/air cancellation. The oblique beam capture was visually reviewed. The BNS/EMI inventory-editor smoke also passed. Evidence: `build/validation-1.9.5-furnace-client.log`, `build/validation-1.10.0-feedback-client.log`, and `build/validation-1.10.0-final.log`. All clients remained hidden, muted, and mouse-free.



Earlier development runs failed timing assertions that predated delivery waits, a partial-insertion fixture that could observe several batches, and synchronous routing checks that simulated several scheduler ticks inside one server callback. Those fixtures and tick accounting were updated; the final suites above passed. Early runs also reproduced the previously recorded intermittent whole-network Pull fixture failure. No claim is made that its independent cause was established. A dedicated persistence restart and a client with Ars/Sophisticated Storage were not repeated.



## 1.9.4 Mining and wand binding feedback



The build passed 69 unit tests and 98 required GameTests. New server cases cover cropped/transparent artwork averaging, assigned plus aimed routes, the preview packet codec, shortest relay routes, rune-face departure, selected-hotbar gating, and cancellation on ordinary blocks and non-container block entities without losing assignments. Evidence: `build/validation-1.9.4-final.log`.



The hidden client received vanilla damage-progress packets and rendered cracks on gem, budding, and cluster models through the actual world renderer. With overlay opacity temporarily zero, the three captures changed 585, 693, and 5,934 darkened pixels in the block region. The client restored opacity before testing binding. Initial visual review caught reversed beam winding; the final test checks visible rune-colored framebuffer pixels as well as emitted geometry. Assigned and aimed beams, matching particles, slot switching, reselection, and an actual Shift/use-item cancellation packet passed. The crack captures and final beam capture were visually reviewed. Evidence: `build/validation-1.9.4-client-final.log`, `build/client-smoke/binding-feedback.txt`, `mineral_*_breaking.png`, and `binding_beams.png`. The client remained hidden, muted, and mouse-free. Optional-mod combinations and dedicated restart were not repeated.



## 1.9.3 Recipe Tome layout



The build and existing inventory-editor client check passed. The multipage tome capture was visually reviewed: the Minecraft page arrow and page count sit within the paper, and the output icon has no astral slot frame. Inventory sampling and inscription still passed. The client remained hidden, muted, and mouse-free. Evidence: `build/validation-1.9.3-tome.log` and `build/client-smoke/tome_pagination.png`. The release JAR has version 1.9.3, valid JSON resources, and no development test classes. Server GameTests were not repeated for this client-only layout change.



## 1.9.2 Binding, furnace routing, and local crystal storage



The build passed 69 unit tests and 96 required GameTests. New coverage checks held/released Shift target clicks, whole-network relay bindings with actual Push/Pull transfers, both initial and replacement furnace fuel destinations, and Storage Crystal menu isolation. Crystal tests cover forged remote withdrawals, local cursor and Shift deposits, local crafting refill/clear, full-capacity rejection, and the Nexus retaining its combined view. Evidence: `build/validation-1.9.2-all-final.log`. One initial run failed the existing whole-network Pull test; the following two full runs passed. That intermittent failure was not reproduced.



Hidden, muted client tests sent actual use-item and Shift-state packets, verified relay target addition/removal with Shift held and released, and compared idle/binding wand pixels with the astral overlay disabled. The custom renderer now displays the synchronized glint. The earlier binding test also passed with BNS. Evidence: `build/validation-1.9.2-relay-binding-client.log`, `build/validation-1.9.2-binding-bns.log`, and `build/client-smoke/wand_binding_glint.png`.



The actual vanilla furnace client checks fuel and ingredient packets going from storage to the furnace, output returning to the chest, and no Nexus detour. Fuel uses a separate approach from recipe ingredients; furnace output emerges above the block. The client also verifies one physical ingot returned to storage. Evidence: `build/validation-1.9.2-furnace-final.log` and `build/client-smoke/furnace-routes.txt`.



A server-opened Storage Crystal screen displayed only its 23 amethyst shards with the localized crystal title. Opening the connected Nexus displayed those shards and 41 diamonds from a chest. The local screen and furnace output capture were visually reviewed. Evidence: `build/validation-1.9.2-crystal-client.log` and `build/client-smoke/storage_crystal_{local,network}.png`. Clients stayed hidden, muted, and mouse-free. Dedicated restart and the full optional-mod matrix were not repeated for this patch.



## 1.9.1 Inventory interaction and rune placement fixes



The build passed 69 unit tests and 92 required GameTests. Inventory tests cover actual pickup, single-item placement, non-transferring Shift shortcuts, and returning the carried stack on close. Placement tests exercise eight independent rune click targets on a chest at default size, the actual face packet codec, preserving existing layers after lowering the server limit, and selecting containers/crystals from a placement-ready wand without opening the library. Evidence: `build/validation-1.9.1-final-build.log`.



Hidden, muted clients passed with base NeoForge, BNS + EMI, and BNS + JEI. Native mouse pickup and cursor-copy actions are exercised; the fixture invokes the screen's Shift-click dispatch and the viewers' drop handlers directly. It does not synthesize operating-system modifier keys or drag gestures. The base client also checks native tome-tooltip registration and captures its rendered icon/name. Evidence: `build/validation-1.9.1-base-client.log`, `build/validation-1.9.1-bns-emi.log`, and `build/validation-1.9.1-bns-jei2.log`.



The preset editor passed its paint, color-wheel, field, save, and placement checks in `build/validation-1.9.1-wand.log`. Screenshots were reviewed for inset field text, the transparent filter area, and the tome tooltip. Patchouli rendered the updated book in `build/validation-1.9.1-guide.log`. The release audit matched all 174 packaged resources to source and excluded GameTest fixtures; see `build/artifact-validation-1.9.1.txt`. The full storage/crafting UI regression and dedicated restart were not repeated for this patch.





## 1.9.0 Inventory-based rune and tome editors



The base server gate passed 69 unit tests and 90 required GameTests. The new inventory fixture checks real menu opening, server-owned item/fluid samples, rejected slot indices, ignored client item identities, and unchanged inventory across all vanilla click types. Evidence: `build/validation-1.9.0-server-final.log`.



Focused hidden clients passed inventory filter selection, right-click removal, -1/0/1 stock-limit scrolling, binding glint state, and tome selection/inscription. BNS 1.4.5 was tested separately with EMI 1.1.24+1.21.1 and JEI 19.21.0.247. Both recipe-viewer plugins registered in the loaded mods; the fixtures invoke their item/fluid drop handlers and verify server acknowledgements. This tests the integration callbacks, not an operating-system drag gesture. Screenshots of the rune and tome inventories with the BNS rail were reviewed. Evidence: `build/validation-1.9.0-editor-client2.log`, `build/validation-1.9.0-bns-emi.log`, and `build/validation-1.9.0-bns-jei.log`.



The preset editor passed with BNS and EMI in `build/validation-1.9.0-preset-client.log`. The full UI regression passed with JEI in `build/validation-1.9.0-base-full-client.log`. The separate BNS background/opacity gate passed in `build/validation-1.9.0-full-client.log`. All development clients remained hidden, muted, and mouse-free after window construction.



Patchouli rendered 11 entries and 23 spreads using ten refreshed screenshots in `build/validation-1.9.0-guide.log`. The release audit checks packaged resources, optional integration classes, supplied item texture hashes, and exclusion of test fixtures. The server adapter matrix and dedicated restart fixture were not repeated for this UI change.



## 1.8.0 Rune editors and background crafting



The build passed 69 unit tests. Base and optional-mod server profiles each report 89 required passes; optional fixtures return early when their mods are absent. The optional profile exercises Visual Workbench, Create, and Stacks Not Slots. New cases cover Filter insertion policies, any-match migration, cached batch planning, immediate Calculating jobs, tagged shortages and resumption, exact-color serialization, six-face placement, and opening rune settings without changing filters or placing another rune. Evidence: `build/validation-1.8.0-interactions2.log` and `build/validation-1.8.0-final-compat.log`.



The focused wand client passed actual color-wheel/hex input, exact RGB painting, a 128x128 serverbound design, the preset grid, transformed item overlays, saving placed behavior, and numerical controls. The full client with Jade and Visual Workbench passed normal right-click settings access, filter add/clear, autosaving, missing-ingredient icons and their grid, Nexus quantity scrolling, storage clicks, crafting, and material checks. The color picker and shortage grid were visually reviewed. Evidence: `build/validation-1.8.0-interaction-client.log` and `build/validation-1.8.0-interactions-full-client2.log`.



Spline unit tests check relay offsets within half a block, continuous velocity, and all six launch faces. The routing client passed a real routed withdrawal, a direct storage/table autocraft round trip, and an actual rune item packet. Synthetic fluid and energy packets exercise the shared visual codec and interpolation. The model client rendered wall and ceiling orientations alongside floor variants and checked shader masks. Evidence: `build/validation-1.8.0-final-routing.log` and `build/validation-1.8.0-final-models.log`.



Patchouli rendered 11 entries, 23 spreads, and ten screenshot resources. Updated rune controls, filters, and crafting pages were visually reviewed. All client profiles stayed muted with mouse capture disabled. Evidence: `build/validation-1.8.0-final-guide.log`.



The final JAR matches all 168 source resources, preserves the supplied wand/orb images, includes the color picker and asynchronous planner, and excludes GameTest fixtures. Evidence: `build/validation-1.8.0-final-build.log` and `build/artifact-validation-1.8.0.txt`. A dedicated restart was not repeated; save-format checks cover current RGB artwork and legacy pixel data in GameTests.



## 1.7.0 Line of sight and spline flights



The build passed 64 unit tests. Base and optional-mod server profiles each report 84 required passes; six optional fixtures return early in the base profile. New world cases cover opaque blockage, glass/panes/tinted glass/leaves/water, immediate obstruction invalidation, relay detours, live range changes, and unchanged inventory quantities during blocked deposits and withdrawals. Existing Visual Workbench, Stacks Not Slots, and Create integration cases passed. Evidence: `build/validation-1.7.0-second.log` and `build/validation-1.7.0-compat.log`.



Spline tests check passage through intermediate nodes, equal incoming/outgoing velocity with unequal leg durations, outward launch and inward arrival on all six faces, repeated positions, and malformed endpoint rejection. The focused client received an actual rune item-transfer packet with its glyph offset and launch normal. Synthetic fluid and energy packets exercised the same codec and continuous three-node interpolation; this is rendering evidence, not a new fluid/energy ownership test. One captured effect frame was visually reviewed. Evidence: `build/validation-1.7.0-routing-client.log` and `build/client-smoke/routing/`.



The full UI/material client regression passed in `build/validation-1.7.0-full-client.log`. Patchouli rendered the updated range/sight and crafting pages without clipping; the guide run passed in `build/validation-1.7.0-guide.log`. All client profiles remained muted with mouse capture disabled.



All 168 packaged source resources match the final JAR. The artifact audit verifies the new occlusion hook, optional guide metadata, unchanged supplied wand/orb textures, and exclusion of test fixtures. Evidence: `build/artifact-validation-1.7.0.txt`. No new save format was introduced; the dedicated restart fixture was not repeated for this change.



## 1.6.0 Relay routes and content cleanup



The build passed 61 unit tests. Both the base and optional-mod server profiles report 82 required passes. Six optional fixtures return early in the base profile; the integration profile exercises them, including Visual Workbench 21.1.2 saved-grid preservation, cancellation, and an actual Nexus craft. Evidence: `build/validation-1.6.0-final-server.log` and `build/validation-1.6.0-compat.log`.



The focused client captured one 110-tick withdrawal flight through four ordered waypoints while the player inventory changed immediately. An actual network autocraft emitted exactly two direct flights, storage to the adjacent crafting table and back, without a Nexus detour. The full base client passed scrolling, cursor transfers, crafting refill/clear, job controls, rune interactions, and shader checks. Evidence: `build/validation-1.6.0-routing-craft-client.log`, `build/client-smoke/routing/result.txt`, and `build/validation-1.6.0-client-final.log`. Test clients remained muted with mouse capture disabled.



A fresh dedicated world and a separate restart retained 12,345 iron ingots, 37 gold ingots, 11 named iron ingots, rune UUIDs, multiple container/network targets, filters, a Moon storage upgrade, and shared Nexus links. Evidence: `build/persistence-smoke-v3/create-result.txt` and `build/persistence-smoke-v3/verify-result.txt`. The first combined run failed only because the Gradle verification gate still expected the old fixture marker; the corrected gate and restart passed in `build/validation-1.6.0-client-final.log`.



The model client rendered all four registered network blocks and four upgrade variants, checking geometry, facing states, and crystal-only shader masks. The item gallery was visually reviewed. Patchouli opened 11 entries and 22 spreads with ten screenshot resources; the revised rune, relay, and crafting pages were reviewed for clipping. Evidence: `build/validation-1.6.0-models-client.log`, `build/validation-1.6.0-guide.log`, and `build/client-smoke/`.



The packaged-resource audit checks source parity, removed block/item resources, version metadata, supplied wand/orb hashes, optional guide metadata, and exclusion of test fixtures. Evidence: `build/artifact-validation-1.6.0.txt`.



## 1.5.1 Particle stability and shooting-star trail



The GPU fixture renders nine independent one-block faces at eight world locations, with frozen animation and a +/-0.0001-block perturbation around a four-block depth boundary. Before the fix it measured 1,118 pixels changing by more than 0.15 mean RGB; after the fix it measured zero. Maximum mean frame difference fell from 0.00497 to 0.00000750. This reproduces depth-layer and particle-coverage instability in the shader; it is not a measurement from the user's world. Evidence: `build/validation-1.5.1-before.log`, `build/wall_stability_before-1.5.1.txt`, and `build/client-smoke/parallax/wall_stability_metrics.txt`.



At the default 0.18 overlay opacity, the captured meteor occupies a 102-by-18-pixel region and has 321 visibly changed pixels more than 12 pixels from the brightest head sample. The capture was visually reviewed. The 24-second scan still has three active samples and 237 quiet samples, preserving a single brief event. Evidence: `build/client-smoke/parallax/shooting_star_default_trail.png` and the neighboring meteor metrics.



The client profile also checks shared world parallax, all five animation speeds, and crystal-only material masks for all twelve retained block models. Clients remain muted with mouse capture disabled. Evidence: `build/validation-1.5.1-client.log`.



The patch build passed 54 unit tests and the 82-case server report (six optional fixtures return early without their mods). The artifact audit matched all 220 packaged source resources and verified the supplied wand/orb textures and optional guide metadata. Evidence: `build/validation-1.5.1-final.log` and `build/artifact-validation-1.5.1.txt`.



## 1.5.0 Wand presets and crystal consolidation



`test build` passed with 54 unit tests. The server reports 82 required passes; six optional-integration fixtures return early without their mods, leaving 76 exercised cases. New cases cover portable artwork and item transforms, independent placed copies, collision-free placement, network Push/Pull conservation, range rejection, upgrade persistence, network-bound settings, and confirmed network refund recovery without replay. Evidence: `build/validation-1.5.0-final.log`.



The actual wand client screen passed painting, item search, overlay movement/rotation, rule editing, file serialization, crop preservation, selection packets, placement, and world rendering. An alpha assertion verifies transparent artwork backgrounds. The editor and placed rune screenshots were visually reviewed. Evidence: `build/validation-1.5.0-wand-final.log` and `build/client-smoke/wand/`.



The full base client passed storage, manual crafting, rune inspection/removal, material, and interface regression checks in `build/validation-1.5.0-client.log`. All development clients remained muted with operating-system mouse capture disabled.



Patchouli 1.21.1-93 opened 11 entries and 21 spreads across two categories, resolving ten screenshots. New rune and upgrade spreads were reviewed for clipping, and the screenshot exports were corrected for Patchouli's image sampling. Evidence: `build/validation-1.5.0-guide-final.log` and `build/client-smoke/guide_*.png`.



A dedicated-server restart reopened the existing persistence fixture, retaining 12,345 iron ingots, 37 gold ingots, 11 named iron ingots, rune UUIDs, filters, targets, and shared Nexus links. Evidence: `build/validation-1.5.0-persistence.log` and `build/persistence-smoke-v2/verify-result.txt`. The new preset/upgrade save-load cases run in GameTests; the older restart fixture does not contain player-painted designs.



The artifact audit checks every packaged source resource, optional Patchouli metadata, referenced recipes, supplied wand/orb texture hashes, and exclusion of test fixtures. The optional third-party integration matrix was not repeated for this change.



## 1.4.7 Single shooting stars and crystal guide



`test build`, the network-model client profile, the Patchouli guide-only client profile, and `runGameTestServer` passed. The unit suite contains 54 tests. The server reports 77 required passes; six optional-integration fixtures return early in the base profile, leaving 71 exercised cases. The optional integration matrix was not repeated.



The GPU scan samples 240 frames across 24 seconds with shooting stars toggled on/off: three frames show the crossing and 237 are quiet. The peak frame has one square head and stepped trail. Existing shared-world parallax, independent speed controls, and twelve-model material checks pass. Client tests remained muted with mouse capture disabled. Evidence: `build/validation-1.4.7-client.log`, `build/client-smoke/parallax/shooting_star_metrics.txt`, and `build/client-smoke/parallax/shooting_star_peak.png`.



Patchouli 1.21.1-93 opened all 19 entries and 26 spreads across two categories, covering all twelve registered network blocks. All twelve new spreads were visually reviewed. Three overflowing storage headings and a crowded Power Node paragraph were corrected and rechecked. Nine existing screenshot resources resolve. Evidence: `build/validation-1.4.7-guide-final.log` and `build/client-smoke/guide_crystal_*.png`.



The guide describes current behavior rather than claiming unfinished controls: Routing and Distribution lack player controls for their saved stock/mode settings; Remote and Gateway have identical mechanics. This release documents those limits without changing crystal logistics.



The JAR contains all 231 expected source-matched resources and no development GameTest classes. Its 162 texture, model, and blockstate resources are unchanged from 1.4.6. Evidence: `build/artifact-validation-1.4.7.txt`. Server evidence: `build/validation-1.4.7-server.log`.





## 1.4.6 Client animation speeds



Checked on September 15, 2026 with Minecraft 1.21.1, NeoForge 21.1.244, and Java 21. The build and all 54 unit tests passed, including fractional rates crossing the vanilla shader clock wrap and zero-speed behavior.



The muted, mouse-released client tested each of the five speed controls with every other clock frozen. Each control changed its own animation, and all five double-speed renders exactly matched their normal-speed renders at twice the elapsed time. With all speeds zero, timed frames matched exactly while camera-driven world parallax remained visible. Existing shared-field, shooting-star, geometry, and twelve-model material checks also passed. Evidence: `build/validation-1.4.6-client.log` and `build/client-smoke/parallax/speed_metrics.txt`.



All five options appeared with their documented defaults in the generated client TOML. The packaged resources match source, and non-shader assets match 1.4.5 byte for byte. Evidence: `build/artifact-validation-1.4.6.txt`. The optional integration matrix was not repeated.



## 1.4.5 Shared world field and shooting stars



Checked on September 15, 2026 with Minecraft 1.21.1, NeoForge 21.1.244, and Java 21. The build and 49 unit tests passed. The full client regression is recorded in `build/validation-1.4.5-client.log`; the final focused shader and twelve-model checks are in `build/validation-1.4.5-world-client.log`.



The GPU fixture renders one surface and then splits it into two faces that each restart the entire texture UV rectangle. Their world-field images match within floating-point rounding (mean RGB difference `4.05e-8`). Moving the eye changes the interior (`0.03198` mean difference), and another world location reveals a different field (`0.00766`). Base artwork at zero opacity remains unchanged. Coordinates wrap periodically at 4096 blocks to retain GPU precision; the wrap comparison matches within the same rounding tolerance.



A frozen-view comparison toggles shooting stars at each of sixty sampled times across 24 seconds. Seven frames contain streaks and 53 are quiet. Captured motion frames were inspected. Existing item parallax, GUI positioning, crystal-only material masks, and all twelve placed/item models passed their checks. Screenshots and measurements are under `build/client-smoke/parallax/` and `build/client-smoke/node-models/`. Clients remained muted with the mouse released.



The JAR's 218 checked resources match source, and all 160 non-shader visual assets match 1.4.4 byte for byte. Evidence: `build/artifact-validation-1.4.5.txt`. The optional integration matrix was not repeated for this client-rendering change.



## 1.4.4 Network block models



Checked on September 15, 2026 with Minecraft 1.21.1, NeoForge 21.1.244, and Java 21.



- Build and all 49 unit tests passed. The base server gate passed 71 active checks; six optional integration checks returned because their mods were absent. The full base client passed its storage, crafting, rune, interface, and existing material checks. Evidence: `build/validation-1.4.4-final.log`.

- The dedicated model client loaded all twelve actual block and item models. Each model has 27-69 faces and uses the four custom sprites. Every block's six facing states passed geometry checks. Evidence: `build/validation-1.4.4-node-client.log` and `build/client-smoke/node-models/geometry.txt`.

- GPU comparisons exercised the registered world renderer and actual GUI item renderer at two opposing orientations. All 48 comparisons changed crystal pixels with shader opacity; pixels outside the crystal masks remained identical. Stone, trim, and channel inlays stayed unaffected. Face culling remained enabled. Evidence: `build/client-smoke/node-models/gpu-metrics.txt`.

- In-world views and item galleries were visually inspected. The rotated item gallery shows the reverse/underside. Test clients remained muted with the operating-system mouse released.

- The packaged JAR contains 160 data/asset JSON files, 27 PNGs, and twelve OBJ/MTL pairs. All 218 checked source resources match its bytes; test fixtures and optional implementations are excluded. Evidence: `build/artifact-validation-1.4.4.txt`.

- The 36 protected natural-crystal, wand, orb, and shared shader resources retain their baseline hashes, as do seven inherited vanilla textures. Editable Blockbench sources, runtime exports, and their face counts match. The optional integration matrix was not repeated for this visual update.



## 1.4.3 Translucent highlights



Checked on September 14, 2026 with Minecraft 1.21.1, NeoForge 21.1.244, and Java 21. The build, 49 unit tests, base server gate (71 active checks and six optional early returns), and full base client passed in `build/validation-1.4.3-base-client.log`.



The GPU fixture compares actual pixels against source-alpha composition: hover uses 48/255 opacity and both selection-border colors use 96/255. Hover, selection, and their combined result matched exactly, while all 98 opaque sample-item pixels stayed unchanged. Native crafting-slot hover also matched exactly and the later vanilla highlight hook added no pixels. Captures and measurements are in `build/client-smoke/nexus-presentation/`.



With Bundled Not Siloed 1.4.5 and Stacks Not Slots 1.0 installed, the same presentation checks and original tab checks passed in `build/validation-1.4.3-bundled-client.log`. An isolated GPU fixture directly calls the redirect merged into the loaded BNS class: the Nexus branch adds zero pixels over its existing tint, and the non-Nexus branch matches vanilla's highlight exactly. This tests the merged handler without changing live BNS inventory/search state. Evidence: `build/client-smoke/bundled-tab/highlight_metrics.txt`.



The highlight captures were visually inspected and guide screenshots refreshed. Test clients remained muted with the operating-system mouse released.



## 1.4.2 Nexus presentation



Checked on September 14, 2026 with Minecraft 1.21.1, NeoForge 21.1.244, and Java 21.



| Gate | Result | Evidence |

| --- | --- | --- |

| Build and unit tests | All 49 tests passed | `build/validation-1.4.2-base-client.log` |

| Base server | 71 active checks passed; six optional integration checks returned because their mods were absent | `build/validation-1.4.2-base-client.log` |

| Base client | Presentation checks and existing storage, crafting, rune, and material checks passed | `build/validation-1.4.2-base-client.log` |

| Bundled Not Siloed 1.4.5 and Stacks Not Slots 1.0 client | Presentation and native-tab checks passed | `build/validation-1.4.2-bundled-client.log` |

| Packaged artifact | Resources match source, including refreshed guide screenshots; test fixtures and optional implementations are excluded | `build/artifact-validation-1.4.2.txt` |



The GPU fixture renders the actual Nexus background and vanilla item icons. All 98 opaque pixels in the sample item remain identical under hover and selection, while surrounding slot pixels change. Empty and terminal-only job lists exactly match the base panel. One and two current jobs render only their occupied rows; a fourth job enables the job scrollbar.



The storage scrollbar is absent for zero through 54 entries. Its thumb measures 93 pixels at 55 entries, 54 at 108 entries, and the minimum 16 at 900 entries. Real input preserves the grab position, including a dense list with more rows than track pixels. The production drag mapping reaches both ends. Removing overflow during a drag still consumes the release. Evidence: `build/client-smoke/nexus-presentation/`.



The existing integrated-server fixture also checks row scrolling, search clamping, individual cancellation after filtering, cursor transfers, grid refill, stack crafting, and returning cleared ingredients to storage. The active-job screenshot and hover capture were visually inspected. Development clients remained muted with the operating-system mouse released. Guide screenshots were refreshed from the successful base client. No server adapter changes were made; the full optional adapter matrix was not repeated.



## 1.4.1 Bundled Not Siloed tab



Checked on September 14, 2026 with Minecraft 1.21.1, NeoForge 21.1.244, and Java 21.



| Gate | Result | Evidence |

| --- | --- | --- |

| Build and unit tests | All 49 tests passed | `build/validation-1.4.1-base.log` |

| Base server | 71 active checks passed; six optional integration checks returned because their mods were absent | `build/validation-1.4.1-base.log` |

| Client without Bundled Not Siloed | Existing storage, crafting, rune, and material checks passed | `build/validation-1.4.1-absent-client.log` |

| Bundled Not Siloed 1.4.5 and Stacks Not Slots 1.0 client | Real toolbar rendering and GPU comparisons passed | `build/validation-1.4.1-bundled-client.log` |

| Packaged artifact | Resources match source; test fixtures and optional-mod implementations are excluded | `build/artifact-validation-1.4.1.txt` |



The installed-mod client renders Bundled Not Siloed's actual toolbar against the active Nexus inventory grid. At zero interface opacity, all 1,336 opaque background pixels match the supplied 21 x 64 PNG. Default and full opacity change the background, while every pixel in the four 13 x 13 native buttons remains unchanged. A non-Nexus screen keeps the original background at both opacity settings. The narrow 8 x 64 tab also stays within its bounds. Captures and measurements are in `build/client-smoke/bundled-tab/`; the complete Nexus screen is `build/client-smoke/nexus_bundled.png`.



The Nexus and attached tab were visually inspected. Both client runs remained muted and released the operating-system mouse. This check covers the tab appearance; it does not validate Bundled Not Siloed's inventory behavior. The server adapter matrix was not repeated for this client-only change.



## 1.4.0 storage controls, dark interfaces, and optional integrations



Checked on September 14, 2026 with Minecraft 1.21.1, NeoForge 21.1.244, and Java 21. Older sections below describe earlier releases, including their dependency requirements.



| Gate | Result | Evidence |

| --- | --- | --- |

| Build and unit tests | All 49 tests passed | `build/validation-1.4.0-dark-base-client.log` |

| Base server | 71 active checks passed; six optional integration checks returned because their mods were absent | `build/validation-1.4.0-release-base.log` |

| Installed optional mods | All 77 GameTests executed and passed | `build/validation-1.4.0-release-compat.log` |

| Base integrated client | Dark interfaces, storage/crafting controls, built-in rune inspection, and GPU checks passed without Patchouli | `build/validation-1.4.0-dark-base-client.log` |

| Patchouli and Jade client | The same controls and GPU checks passed; Jade added actual Shift-only icons; all 14 guide spreads rendered | `build/validation-1.4.0-dark-guide-client.log` |

| Packaged artifact | 148 JSON resources and 21 PNGs checked; source resources and supplied artwork match | `build/artifact-validation-1.4.0.txt` |



The installed-mod server gate loaded Visual Workbench 21.1.2, Puzzles Lib 21.1.60, Patchouli 1.21.1-93-NEOFORGE, Create 6.0.10, Stacks Not Slots 1.0, and Jade 15.10.6. Visual Workbench checks use its actual replacement block, persistent container, recipe preview, menu, and NBT loader. Autocrafting consumes four chest ingots and returns one trapdoor while every saved manual ingredient and preview remains unchanged. Cancellation refunds only the scheduler's ingredients. Full-storage tests reject deposits and withdrawals involving the saved manual grid.



The base guide tests actively verify that the mod-owned guide item, creative entry, and recipe are absent without Patchouli. With Patchouli installed, they check exactly one `astral_repository:field_guide` creative entry, its conditional recipe, persistence, the loaded book's custom item, and the real item-use packet. The guide screenshots were exported from the dark base client before the final Patchouli run. The storage and crafting spreads were visually inspected for readable text and page overflow.



Server crafting checks cover component-preserving refill, one-output-stack Shift crafting, cake and honey-bottle remainders, full inventories, rejected clear-grid deposits, and deferred transactions cancelled by inventory changes, menu closure, or lost access. Timing checks exercise both settings, actual table work, native furnace processing, item/fluid routing, power costs, and cancellation conservation. Topology tests preserve an active craft across distant Nexus placement/removal, update nearby storage ownership, and cancel/refund claimed table work when an internal link changes despite unchanged component membership.



Protocol checks exercise full/delta reconstruction, stale and replayed revisions, coalesced requests, scroll acknowledgements, ownership-checked individual cancellation, and bounded quantities. An unchanged view sends no catalog packets across polling intervals. The count-only delta fixture encodes fewer than 40 bytes; job-progress updates reuse known item identities. These are correctness and payload-size checks; large live factories were not benchmarked. See [Nexus synchronization](networking.md) for the AE2 and Refined Storage source review.



The real client exercises more than 80 stored item types, row scrolling, scrollbar dragging, search clamping, cursor transfers, a 64-item craft request with existing stock, an explicit 256-item request, individual cancellation, manual-grid refill, a 64-output Shift craft, and clearing ingredients back into storage. Craft submission/cancellation did not add received chat messages. Every development client remained muted and released the operating-system mouse.



The dark Nexus and rune editor were visually reviewed. Their shared astral shader sits behind opaque controls and slots. The interface GPU fixture reproduced all 84,584 opaque panel pixels exactly at zero opacity, measured no difference when world opacity changed, and measured no difference after compensated view translation. At the default interface opacity, 2,910 sampled pixels changed by more than 0.01 normalized RGB over twelve seconds. Both opacity values and render state were restored. Evidence: `build/client-smoke/interface/`.



The world-material GPU test retained eye-dependent depth, fixed source artwork, boxed particles, and stable orthographic rendering. Twelve-second motion increased from the previous 0.002875 to 0.005446 mean normalized RGB difference in the same frozen-view fixture. Wand comparisons still isolate the 144 large-crystal pixels; the frame, grip, and small pommel stay stable. All four remote-orb quadrants respond to opacity. Evidence: `build/client-smoke/parallax/` and the client logs above.



Run `python tools/verify_guide_resources.py --jar build/libs/astral_repository-1.4.0.jar` for the guide, dependency metadata, exact artwork hashes, and packaged-resource checks. The JAR excludes GameTests, fixture structures, nested dependencies, and optional-mod implementations. Its SHA-256 sidecar is in `build/libs/`.



## 1.3.0 controls, Field Guide, and crystal depth



Checked on September 14, 2026 with Minecraft 1.21.1, NeoForge 21.1.244, Java 21, and Patchouli 1.21.1-93-NEOFORGE.



| Gate | Result | Evidence |

| --- | --- | --- |

| Build and unit tests | All 44 tests passed | `build/validation-1.3.0-controls.log` |

| Base server GameTests | 55 active checks passed; four optional integration checks returned because their dependencies were absent | `build/validation-1.3.0-controls.log` |

| Create 6.0.10, Stacks Not Slots 1.0, and Jade 15.10.6 server gate | All 59 checks executed and passed | `build/validation-1.3.0-compat.log` |

| Base integrated client | Cursor transfers, autosave, filter search, exact pickup, and material checks passed | `build/validation-1.3.0-capture-final.log` |

| Jade integrated client | The same controls and actual Shift-only Jade rows passed; all seven guide entries rendered | `build/validation-1.3.0-guide.log` |

| Deeper crystal material | Revised shader compiled and passed the complete base-client interaction and opacity checks | `build/validation-1.3.0-parallax.log` |

| Final guide and isolated GPU comparison | All fourteen guide spreads and frozen-time parallax controls passed | `build/validation-1.3.0-final.log` |



The new server tests exercise cursor stack and half-stack pickup, exact item components, partial deposits, full inventories, stale requests, normal crafting-slot clicks and dragging, and shift transfers. Rune pickup tests verify the exact glyph UUID, current player rotation, reach, blocked rays, creative and survival refunds, full-inventory retention, replay rejection, and preservation of the host and other rune layers. Guidebook tests check real Patchouli registration, book-component persistence, actual recipe matching, and separation from the Recipe Tome recipe.



The live client moves copper from storage onto the cursor, deposits one into an empty storage cell, moves the remainder through a vanilla crafting slot, and returns it to storage. It verifies server-side rune changes while the editor stays open, unchanged widget focus and caret after acknowledgements, replacement of superseded sessions, visible item-and-tag search results, and preservation of rejected-action feedback through a later autosave. Holding the pickup click removes only one glyph and returns exactly one rune to creative inventory.



Every development client remained muted and released the operating-system mouse. Both the built-in and Jade inspection paths were checked. The user-supplied wand and orb PNGs remain unchanged; opacity comparisons still isolate the large wand crystal and affect all four orb quadrants. The updated field uses seven virtual depths and square, wide-rectangle, and tall-rectangle particles.



The isolated GPU test fixes time and holds the visible quad and its UVs in place while translating the eye. The zero-opacity base remains pixel-identical; the interior changes, including 1.84% of sampled pixels by more than 0.03 normalized RGB. Rotating the material changes its interior at the same source texels. A twelve-second interval produces drift with a fixed view, while shifting an orthographic icon leaves its interior unchanged. Images and measurements are in `build/client-smoke/parallax/`.



The Field Guide contains one category, seven entries, fourteen spreads, and nine screenshot textures cropped from the actual test client. The client opens every spread through Patchouli and verifies the selected entry, page, image resources, and book model. Visual review found and corrected crowded captions and an overflowing recipe heading. Source screenshots and their crop/hash manifest are under `build/client-smoke/`; `tools/export_guide_screenshots.ps1` reproduces the texture exports.



Run `python tools/verify_guide_resources.py --jar build/libs/astral_repository-1.3.0.jar` to check resource references, exact-case paths, PNG dimensions, guide recipe components, source/JAR agreement, the supplied artwork hashes, dependency metadata, and absence of packaged test fixtures or dependency implementations. Patchouli is a separate required mod, not embedded in the release JAR.



## 1.2.2 orb overlay and Recipe Tome



The build and integrated client passed on September 14, 2026 with Jade 15.10.6 installed. Evidence: `build/final-orb-overlay-tome-1.2.2.log`. The client stayed muted and did not capture the mouse.



The packaged orb PNG matches the supplied 47 x 47 image byte for byte. Its flat generated geometry retains filled edges and normal handheld transforms. A framebuffer comparison sampled all 1,674 opaque orb pixels at overlay opacity 0 and 1; each of the four quadrants changed visibly. The wand's large crystal also changed, while its frame, grip, and small pommel gem remained stable. The configured opacity was restored afterward.



The existing Recipe Tome interaction check saved an actual recipe. Its resulting page was visually checked without the saved/bookshelf reminder or workflow instructions; output, ingredients, Back, and Done remained visible. Item identification and actual errors remain available. Images: `build/client-smoke/item_overlays_{zero,full,default}.png` and `tome_inscribed.png`.



The artifact audit parsed 137 JSON resources, verified the supplied PNG and orb renderer model, and confirmed that development fixtures and bundled dependencies are absent. Evidence: `build/artifact-validation-1.2.2.txt`. The JAR and SHA-256 sidecar are in `build/libs/`. Earlier validation records follow below.

## 1.2.1 wand and remote artwork



Checked on September 14, 2026 with Minecraft 1.21.1, NeoForge 21.1.244, and Java 21. The build and integrated client passed with Jade 15.10.6 installed; the client remained muted and never captured the mouse. Evidence: `build/final-wand-sphere-clean-1.2.1.log`.



The packaged wand PNG matches the supplied 34 x 36 image byte for byte. Its 326 baked quads cover the supplied silhouette without duplicate or internal faces, preserve its proportions, and retain one model pixel of depth. Only the 144 pixels in the large crystal's mask receive the astral material. The remote uses a separate original 32 x 32 crystal-sphere sprite with normal generated item edges.



An isolated in-game comparison sampled every opaque wand pixel at overlay opacity 0 and 1. The large-crystal samples changed; 323 frame, grip, and other plain pixels plus all four small-pommel samples stayed stable. The fixture restored the configured opacity afterward. The existing natural-crystal opacity, culling, bud orientation, rune interaction, Jade, and crafting UI checks also passed.



Visual evidence is in `build/client-smoke/wand_geometry.png` and `wand_opacity_{zero,full,default}.png`. Artifact checks parsed all 136 JSON resources and verified the custom textures, separate wand materials, version, and development-class exclusions. Evidence: `build/artifact-validation-1.2.1.txt`; the SHA-256 sidecar is beside `build/libs/astral_repository-1.2.1.jar`.



The remaining sections record the earlier 1.2.0 server and client gates. The server fixtures were not repeated for this artwork and client-renderer change.

Astral Repository 1.2.0 was checked on September 14, 2026 with Minecraft 1.21.1, NeoForge 21.1.244, and Java 21. These results cover the fixtures below, not every mod combination or the performance of a large factory.



## Build and server tests



The build and all **44 unit tests** passed. Unit coverage includes recipe planning, reservations, cancellation, provider contracts, exact capacity arithmetic, power rules, topology, remote access, and a synthetic 200,000-key inventory index. The index fixture is not a live TPS benchmark.



Both GameTest gates passed **50 registered tests**:



| Gate | Result | Evidence |

| --- | --- | --- |

| Base mod | 46 active checks passed; four conditional integration checks returned because their dependencies were absent | `build/final-four-runes-base.log` |

| Create 6.0.10, Stacks Not Slots 1.0, and Jade 15.10.6 installed | All 50 checks executed and passed | `build/final-four-runes-compat.log` |



The rune tests cover independent Push/Pull transfers against bare sided containers and fluid tanks, filters, reserves, destination limits, partial commits, and uncertain-transfer recovery. Interaction tests use real player block-use paths for placement, target assignment in both orders, samples without consumption, clearing, and precise selection of stacked glyphs. A wand click outside the glyph cannot select its rune.



Geometry tests verify fixed glyph size for one through four placements, centered/pair/triangle/quadrant layouts, the fifth-rune rejection, and real outline ray hits on chests, all hopper faces, and narrow blocks. Legacy overflow remains saved and is restored paused when a visible layer is removed.



Settings tests check session identity, expiry, reach, invalid fields, exact sample data, independent removal and survival refunds. A stale editor cannot overwrite another filter edit or resume a rune paused by a transfer failure.



Other fixtures exercise shared Nexus access, actual storage and bookshelf persistence, natural crystal growth/harvesting/geodes, renderer migration, piston behavior, manual crafting, real furnace processing, cancellation, and optional Create pressing/milling and stress leases. Deliberate provider-failure fixtures log quarantine errors; their assertions verify conservative recovery without duplicate refunds. No recipe parsing failures occurred.



AE2, Refined Storage, and Ars Nouveau adapters remain source-checked rather than validated against running backend networks. See [optional runtime validation](optional-runtime-validation.md) for the earlier detailed Create and Stacks Not Slots fixture boundaries.



## Real client



The base integrated-client run passed in `build/final-four-runes-base.log`. It verifies:



- Storage search focus, clearing, and returning control to inventory keybinds.

- Recipe Tome browsing with vanilla book artwork and a saved inscription from an actual recipe.

- Direct Push/Pull transfers, separate targets and filters, target preview/toggle/reverse assignment, and held block samples without consumption.

- No rune popup without Shift; actual rune, target, and filter icons with configured amounts while Shift is held.

- Editing and saving the selected rune's advanced controls without changing its neighbor.

- Muted audio and no operating-system mouse capture.



The same client checks actual baked tool models: the wand has 168 nonduplicated quads, six outward directions, and one pixel of depth; the remote accessor uses its original 32 x 32 Astral Nexus texture with filled edges and one pixel of depth. It verifies all 24 bud-facing states and enabled material backface culling, renders front/back and timed motion views, and checks that opacity 0 and 1 reach the shader and change framebuffer pixels.



The final Jade 15.10.6 client run passed again after the custom remote texture was installed in `build/final-custom-remote-client.log` (58 seconds). The earlier four-rune run is recorded in `build/final-four-runes-jade.log`. It verified that the plugin adds nothing without Shift, renders the shared icon and amount rows while Shift is held, and uses the normal advanced editor successfully. It also rendered the one-to-four layout gallery on four matching chests. The final client log contains no errors or missing Jade translation assertions. Jade's own container information remains controlled by Jade; Astral contributes only its icon element.



Screenshots are written to `build/client-smoke/`. The current base views include `rune_hover_builtin.png`, `rune_hover_unshifted.png`, `rune_hover_jade.png`, `rune_layouts_1_to_4.png`, `rune_advanced_settings.png`, `wand_geometry.png`, `buds_front.png`, `buds_back.png`, and timed `surface_motion_*.png` frames. These checks do not establish every transport or crafting animation under every rendering mod.



## Dedicated save and restart



Two independent headless server processes created and reopened `run-persistence-smoke-v2/world`. The second process did not recreate the fixture. It retained 12,345 iron ingots, 37 gold ingots, and 11 named iron ingots, with the named stack still distinct.



Four independent rune layers retained their UUIDs, separate bare targets and clicked faces, filters, exact sample components, priority, stock settings, and paused state. Four reciprocal graph links survived; two Nexuses still shared one network and crafting coordinator with exactly two indexed stores.



The create/restart evidence is in `build/rune-final-base.log` and `build/persistence-smoke-v2/{create,verify}-result.txt`. The final four-rune build passed another restart in `build/final-four-runes-base.log`. The older v1 fixture was retained separately. Only the test fixture explicitly loads its known chunk; production transfers do not force chunks.



## Artifact



`build/libs/astral_repository-1.2.0.jar` passed the artifact audit: **136 JSON resources** parsed, the expanded version is correct, the custom remote PNG, flat remote model, and new rune assets are present, and the legacy rune recipes are absent. GameTest classes, test structures, nested dependency JARs, and Jade API classes are not bundled.



The remote PNG is 32 x 32, contains an opaque silhouette and transparent padding, and has no partial-alpha pixels. Its model uses the mod-owned sprite instead of a vanilla item texture. The final remote was also visually checked in `build/client-smoke/wand_geometry.png`.



Evidence: `build/artifact-validation-1.2.0.txt`. A SHA-256 file is written beside the JAR.



## Reproduce



```powershell

.\gradlew.bat build runGameTestServer runClientSmoke --no-parallel

.\gradlew.bat runGameTestServer -PcompatTest

.\gradlew.bat runClientSmoke -PpatchouliTest -PjadeTest

.\gradlew.bat runPersistenceCreate runPersistenceVerify --no-parallel

```



`-PcompatTest` loads the locally supplied optional JARs in ignored `test-libs/`. `-PjadeTest` loads `test-libs/Jade-1.21.1-NeoForge-15.10.6.jar`; the client fixture fails if it is absent. `-PpatchouliTest` adds the declared Patchouli runtime dependency and exercises the guide. Without that flag or `-PcompatTest`, Patchouli remains absent from the run. Optional dependencies are not bundled in the output mod.



Persistence creation requires the already accepted `run-server/eula.txt` and refuses to overwrite an existing fixture world. Use `runPersistenceVerify` alone for later restart checks. All development clients set `astral_repository.testClient=true`, which mutes audio and disables mouse capture; installed clients do not enable that property.











