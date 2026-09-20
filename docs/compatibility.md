# Optional integrations

Astral Repository runs without the optional mods below. `astral_repository-compat.toml` controls the five storage, crafting, capacity, and power adapters listed under Configuration. Visual Workbench recognition, the Patchouli Field Guide, and Jade inspection activate when installed and have no Astral compatibility switch. Generic NeoForge capabilities remain available without any of these mods.

| Integration | Implemented behavior | Integration point |
| --- | --- | --- |
| NeoForge item handlers | Item indexing, insertion, extraction, bounded reconciliation, sided access | `Capabilities.ItemHandler.BLOCK` |
| NeoForge fluid handlers | Fluid identity and components, tank indexing, simulated and actual transfer | `Capabilities.FluidHandler.BLOCK` |
| NeoForge energy | FE indexing and transfer | `Capabilities.EnergyStorage.BLOCK` |
| Ars Nouveau | Source indexing and transfer, preserving Source as a separate resource | `ISourceTile` block entities |
| Stacks Not Slots | Exact rational capacity cost lookup, including custom cost providers | `StacksNotSlotsApi.exactCapacityCost` |
| Applied Energistics 2 | Digital item storage and real CPU crafting requests | Explicitly exposed `AECapabilities.ME_STORAGE`; an active grid host is also required for crafting |
| Refined Storage 2 | Root item storage and real autocrafting tasks | Public network-node container and storage/crafting components |
| Create | Sided item/fluid capabilities; pressing and milling processing adapters; optional spare-stress operating requirement | Existing capabilities and a running kinetic network adjacent to a Power Node |
| Visual Workbench | Network autocrafting at persistent tables without using their saved player grid or preview as storage | Block entity type `visualworkbench:crafting_table` |
| Patchouli | Optional mod-owned Field Guide item, recipe, creative entry, and book screens | `custom_book_item` and `PatchouliAPI.openBookGUI` |
| Jade | Shift-only rune icons and amounts in Jade's tooltip | Client block-component and Element APIs |

The storage and processing adapter API names and contracts were checked against local Stacks Not Slots 1.x source, the installed Create 6.0.10 JAR, and the upstream sources listed below. Create 6.0.10 pressing, milling, and stress leases, plus Stacks Not Slots 1.0 exact capacity, passed the [installed-mod runtime gate](optional-runtime-validation.md). AE2, Refined Storage, and Ars Nouveau runtime integration has not yet been exercised in a loaded modded game. Version drift disables the affected provider and logs a diagnostic.

## Setup and boundaries

AE2 storage access uses the ME storage capability that AE2 deliberately exposes to external storage systems. Merely placing an arbitrary ME cable near a crystal does not expose the whole grid. The returned provider accesses AE2's actual storage; Astral does not copy its contents into local containers. Unconfigured interfaces expose the shared grid inventory and deduplicate by that actual storage object. Configured stocking interfaces expose their own local inventory; those views remain distinct. An active AE grid is required, including for storage operations. Crafting is exposed only through an interface that exposes the whole grid inventory.

AE2 crafting delegates calculation and ingredient reservation to AE2, then submits a real CPU job. Completed output is inserted back into that ME network. The Astral ticket observes and can cancel the crafting link. An unloaded or disconnected integration point cancels its session-local ticket.

Refined Storage discovery reads the public network-node containers of its block entities and deduplicates their shared root network. Transfers use the native storage component and item resource factory. Item-to-stack conversion is isolated around RS2's current `ItemResource` implementation, which upstream marks internal. This is a version-sensitive part of the adapter. RS task tickets observe completion events and support cancellation; disappearing tasks without a completion event fail conservatively.

Neither integration advertises Astral crafting patterns back into AE2 or Refined Storage. Addon bridges must preserve `CraftingContext` to provide cycle detection. Normal digital-network power, CPU, ingredient, and storage constraints continue to apply.

Ars Source transfer respects `getTransferRate`, current quantity, available capacity, and `canAcceptSource`. Ars mutation methods return the new total, so the adapter computes actual transferred quantity from the before/after difference. It does not interpret that return value as the amount moved.

Create inventories and tanks participate through NeoForge capabilities. The processing adapter discovers a millstone and a depot with a Mechanical Press two blocks above it, supplies actual inputs, and waits for machine output. Arbitrary Create processing assemblies require an explicit adapter; no output is synthesized. The stress adapter checks live spare capacity and maintains shared Astral leases. It does not add virtual stress consumers to Create's network, turn rotation into FE, or produce items without a machine process.

Stacks Not Slots can override the capacity cost of individual items through its public API. Without it, one normal stack costs 64 capacity units: an item with a maximum stack size of one costs 64 units, while a 64-stackable item costs one. Fractional costs are available for unusual maximum stack sizes through the exact capacity API.

## Persistent tables and the Field Guide

Visual Workbench tables are recognized by the `visualworkbench:crafting_table` block entity type, without loading Visual Workbench classes. Astral's managed crafting adapter uses its own reserved ingredients at that table. The persistent player grid and preview are neither indexed as storage nor read or changed by network autocrafting.

Patchouli 1.21.1-93 or newer for NeoForge enables the `astral_repository:field_guide` item. Install it on both client and server to use the guide. Without Patchouli, that item, its creative entry, and its recipe are absent. The book declaration at `data/astral_repository/patchouli_books/field_guide/book.json` sets `custom_book_item` to the mod-owned item and `dont_generate_book` to prevent a duplicate generic Patchouli book. The recipe has a matching `neoforge:mod_loaded` condition; using the item opens the registered book through Patchouli's API.

These integrations have no `astral_repository-compat.toml` switches. See the [validation record](validation.md) for recorded runtime checks.

## Rune inspection with Jade

Hold Shift while aiming at a rune to show its icons and amounts. With Jade installed, Astral adds one icon element to Jade's tooltip; otherwise the built-in overlay draws the same presentation. Releasing Shift hides the rune contribution. No rune description paragraphs or control hints are added. Neither path requires Astral Goggles for the directly targeted face.

This optional plugin uses Jade's client block-component and Element APIs and has no `astral_repository-compat.toml` switch. It is compiled against Jade 15.10.6 for NeoForge 1.21.1. See the [validation record](validation.md) for runtime evidence; the earlier Create and Stacks Not Slots results do not validate Jade.

## Bundled Not Siloed side tab

While a local or remote Storage Nexus, placed-rune editor, or Recipe Tome is open, Bundled Not Siloed's attached tab background uses an Astral texture and the interface shader. `astralInterfaceOverlayOpacity` controls its strength. The four native buttons, hover effects, layout, and input remain owned by Bundled Not Siloed; other screens keep their usual tab background.

The tab hook targets `InventorySideRail.Rail.renderBackground`. A second client hook prevents Bundled Not Siloed from adding its white inventory highlight over the Nexus's translucent slot tint. Both apply to the Nexus and ingredient editors and add no dependency or separate compatibility switch. See the [validation record](validation.md) for the tested version.

## Performance mods

Runtime checks used Minecraft 1.21.1, NeoForge 21.1.244, and Java 21 with these versions:

| Mod | Tested version | Runtime coverage |
| --- | --- | --- |
| Lithium | `0.15.4+mc1.21.1` | All 137 required dedicated-server GameTests passed; deep crafting and 10,000-rune fixtures completed without transfer errors; also loaded in both client checks below |
| Sodium | `0.8.13+mc1.21.1` | Loaded together with Lithium and AsyncParticles in both client checks |
| AsyncParticles | `21.1.4.4` | Loaded together with Lithium and Sodium in both client checks |

The transfer-rendering client offered 20,000 animations and retained the configured maximum of 4,096 active flights. It exercised item models, fluid/energy/Source sprites, trails, clock corrections, and paused-tick behavior. The separate model client checked all four registered network blocks and four upgrade appearances in world and inventory rendering, six facing states, custom textures, crystal-only shader masks, parallax, movement, and held/world view-bobbing correction. Both clients ran hidden and muted without capturing the mouse. See the [validation record](validation.md) for measurements and fixture limits.

Lithium used its shipped defaults with zero user overrides; Astral does not disable its optimizations. These mods are optional and are not packaged with Astral Repository. The development-only Gradle flags load matching local JARs from `test-libs/performance/`: `-PlithiumTest` loads Lithium; `-PperformanceClientTest` loads all three. The checked profiles are:

```powershell
.\gradlew.bat runGameTestServer -PlithiumTest
.\gradlew.bat runClientSmoke -PperformanceClientTest -PtransferStress
.\gradlew.bat runClientSmoke -PperformanceClientTest -PnodeModels
```

Coverage is limited to these versions and fixtures; other mod combinations and rendering replacements have not been tested.

## Configuration

All five compatibility switches default to `true`: `arsNouveau`, `create`, `appliedEnergistics2`, `refinedStorage`, and `stacksNotSlots`. Disabling a switch prevents that adapter from being discovered. Generic NeoForge item, fluid, and FE access remains available.

See [Provider API](providers.md) for contracts, registration, simulation, chunk availability, and uncertain-transfer handling.

## API references

- [AE2 1.21.1 public API source](https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/1.21.1/src/main/java/appeng/api)
- [Ars Nouveau 1.21.x Source interface](https://github.com/baileyholl/Ars-Nouveau/blob/1.21.x/src/main/java/com/hollingsworth/arsnouveau/api/source/ISourceTile.java)
- [Refined Storage 2.x public network API](https://github.com/refinedmods/refinedstorage2/tree/support/2.x/refinedstorage-network-api/src/main/java/com/refinedmods/refinedstorage/api/network)


## Recipe-viewer ingredient drops

EMI and JEI are optional client integrations. Their ghost-ingredient handlers accept items in the Recipe Tome, and items or fluids in rune filter editors. Dropping an ingredient copies a selection; it never grants, removes, or transfers inventory contents. Tome outputs are resolved against server recipes before inscription.

The placed-rune editor and tome expose ordinary player slots so BNS can project its inventory browser. Normal clicks move inventory items; Shift-click copies a filter or tome output, and ingredient drops never consume the cursor stack. BNS sidebar backgrounds use the astral interface shader while these editors are open.

Client checks used EMI 1.1.24+1.21.1, JEI 19.21.0.247, and BNS 1.4.5. See the [validation record](validation.md) for the tested boundaries.

## Astral Goggles and Curios

Astral Goggles fit the normal head armor slot or the optional Curios head slot. Curios is not required. With Curios installed, the mod provides one player head slot and tags `astral_repository:resonance_goggles` for it. The existing item ID is retained for saved items and recipes. Functional equipment enables the same rune visibility and diagnostics on client and server; cosmetic and inactive slots do not. Hiding the Curios model leaves its functionality enabled.

Both slots use the custom goggles model and animated astral-gem lenses. Wearing goggles in both slots renders one model. The Curios renderer allows space around a normal helmet. The optional integration is compiled against Curios 9.5.1 for Minecraft 1.21.1.

## Armor trims

Astral Gem material uses the normal smithing recipes and `minecraft:trim_materials` tag. Its palette is supplied for all 18 vanilla trim patterns and both armor texture layers. The worn shader uses the standard `HumanoidArmorLayer` trim pass, including NeoForge's custom armor-model hook. Vanilla armor item icons split the existing base and trim quads into separate render passes, preserving the armor's colors and applying the shader only to its trim. Other trim materials use their original rendering.

Custom trim patterns need the `astral_repository_astral_gem` palette permutation in their armor atlas sources. Modded armor with a separate renderer or custom item geometry is not explicitly supported or runtime-tested. Vanilla armor and Curios goggles were checked; see the [validation record](validation.md).
