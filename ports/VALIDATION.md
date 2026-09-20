# Port validation

Validation date: 2026-09-19. Mod version: 1.11.8. Runtime and loader versions are listed in [Installation](README.md#installation).

| Target | JUnit tests | Dedicated server | Client GPU check |
| --- | ---: | --- | --- |
| Original 1.21.1 NeoForge | 146 passed | 137 GameTests passed | Original client harness retained; not rerun as part of this port |
| 1.20.1 Forge | 146 passed | 5 native Forge GameTests passed | 9 standalone models, 2 shaders, 4 item icons; screenshot inspected |
| 1.20.1 Fabric | 146 passed | Native storage, transactions, persistence, recipes passed | 9 standalone models, 2 shaders, 4 item icons passed |
| 1.21.1 Fabric | 146 passed | Native storage, transactions, persistence, recipes passed | 9 standalone models, 2 shaders, 4 item icons passed |
| 26.2 Fabric | 146 passed | Native storage, transactions, persistence, recipes passed | Expanded world, menu, item, trim, armor, and wand-artwork check passed |
| 26.3 Fabric | 146 passed | Native storage, transactions, persistence, recipes passed | Expanded world, menu, item, trim, armor, and wand-artwork check passed |
| 26.2 NeoForge | 149 passed | Native capabilities, rollback, persistence, recipes passed | Expanded world, menu, item, trim, armor, and wand-artwork check passed |
| 26.3 NeoForge | 149 passed | Native capabilities, rollback, persistence, recipes passed | Expanded world, menu, item, trim, armor, and wand-artwork check passed |

The original 137 GameTests have not been ported wholesale. The new targets run the shared regression suite plus their own loader-specific runtime fixtures.

## Runtime coverage

The Fabric server fixture resolves real sided item, fluid, and energy storage APIs. It checks stable lookup identity, invalid-input guards, zero-amount transfers, committed and aborted transactions, fluid-unit conversion, fractional-fluid rollback, block-entity save/load, all 13 unconditional recipes, and ordinary and Silk Touch cluster/bud loot. The Forge fixture checks capability discovery and invalidation, storage NBT, registry data, and packet serialization. Modern NeoForge checks the native resource-handler APIs, rollback, persistence, all 13 unconditional recipes, and ordinary and Silk Touch cluster/bud loot.

The expanded modern client fixture creates a world containing storage crystals, a linked Nexus, mineral blocks, and a rune-bearing chest. It verifies that rune packets arrive and glyph geometry is submitted, opens the Nexus with real linked inventory contents, renders ordinary and foil items plus astral-trimmed armor, captures worn armor and goggles, and opens the wand editor with two embedded item icons, exercising its offscreen item rendering. These clients run hidden, muted, and without grabbing the mouse. Screenshots are inspected separately from the automated assertions.

The 26.3 resources use the current singular loot `condition` and `modifier` fields; the native drop assertions cover this migration.

## Reproduction and evidence

Use the Java versions and build commands in [Building](README.md#building). Runtime task names, run from each target project, are:

| Target | Dedicated check | Client check |
| --- | --- | --- |
| Original NeoForge | `runGameTestServer` | Existing original client tasks |
| Forge 1.20.1 | `runGameTestServer` | `runClient -PclientSmoke` |
| Fabric 1.20.1 / 1.21.1 | `runServerSmoke` | `runClientSmoke` |
| Fabric 26.2 / 26.3 | `runServerSmoke` | `runClientSmoke -PclientSmoke` |
| NeoForge 26.2 / 26.3 | `runServer -PsmokeTest` | `runClientSmoke -PclientSmoke` |

All targets write JUnit XML to `build/test-results/test/`. Fabric server fixtures write `run-server-smoke/port-smoke-result.txt`. Modern client fixtures write `build/smoke-client/captures/result.txt` and PNG captures. Forge writes its item capture to `run-client/captures/items.png`; legacy Fabric writes `run-client-smoke/client-smoke-result.txt`.

Local validation logs are ignored build evidence, not repository assets. The completed runs use:

- Original: `../build/porting/original-regression.log` and the current JUnit XML.
- Forge: `forge-1.20.1/build/verification-fixed.log` and `forge-1.20.1/build/client-capture.log`.
- Legacy Fabric: each target's `final-validation.log` and `final-client.log`.
- Modern Fabric: each target's `final-validation.log`, `client-final.log`, and `build-release-final.log`.
- Modern NeoForge: `neoforge-26.2/release-validation.log`, `neoforge-26.2/client-validated.log`, `neoforge-26.3/final-loot-validation.log`, `neoforge-26.3/release-build.log`, and `neoforge-26.3/client-final.log`. Client capture results are under `build/smoke-client/captures/`.

Release builds run without `-PclientSmoke`. The artifact collector rejects test entrypoints and test classes. The final normal Fabric builds also replace the temporary client-smoke metadata before packaging.

## Limits

These checks do not cover complete optional-mod packs, external AE2 or Refined Storage networks, cross-version world conversion, or every interactive editor workflow. Optional API compilation and signature inspection are recorded separately from runtime compatibility in the [integration notes](README.md#integrations).
