# Changelog

## 1.12.2 - 2026-11-05

### Added

- Expanded autocrafting for Industrial Foregoing, Immersive Engineering, Oritech, Create, Thermal, and Ender IO. Machines using each mod's shared recipe APIs are detected automatically, with their real item/fluid inputs, byproducts, fuel, tools, and catalysts.
- Added Powah energizing recipes. Large requests run as separate orb cycles, with at most one ingredient in each input slot.
- Recipe Tomes can teach machine conversions and byproducts that have no ordinary recipe entry, and identify the nearby machine that performs them.

### Fixed

- Queued crafts retain their planned recipes when machine discovery or stored ingredient variants refresh.
- Custom machine ingredients recognize newly stocked item types even when those items are missing from the recipe's displayed alternatives.
- Replication recipes can reuse a real seed while counting only the newly produced items toward the requested amount.
- Recipe Tome exclusions also block the native recipes used inside machine conversions.
- Active machines, multiblock ports, and output containers share one reservation. Dropped results stay separate from preexisting item stacks until collected.

## 1.12.1 - 2026-11-05

### Added

- Added autocrafting for Mekanism recipe machines and factories, including fluid and chemical recipe chains, byproducts, and chance-based outputs. Compatible machines and addons are detected automatically.
- Added autocrafting support for Industrial Foregoing's Resourceful Furnace, Immersive Engineering's Alloy Kiln, and Oritech's Foundry.

### Changed

- Recipe Tome pages now identify the processing method and required workstation, including crafting tables, with more recipe details on hover.

### Fixed

- Recipe Tomes preserve the correct potion type, and autocrafting can brew potions using brewing stands.
- Clicking an out-of-stock starred Nexus item lets you choose the amount before submitting, instead of immediately queuing a one-item craft.

## 1.12.0 - 2026-11-04

### Added

- Added a red X Blocking Rune that excludes a container from storage transfers, processing, and recipe discovery while preserving its contents.
- Recipe Tomes can block individual final or intermediate recipes for their taught output.

### Changed

- Nexus crafting ingredients and the selected recipe now remain in a saved personal grid when the interface closes.
- The wand's linking glint uses the average color of the selected rune's visible painted pixels.

### Fixed

- Ordinary storage deposits no longer fill processing machines such as idle furnaces.
- Recipe planning rejects conversion loops and plans for the requested net increase. Stored blocks can supply part of an unpacking request before the remaining amount moves to smelting.

## 1.11.15 - 2026-10-30

### Fixed

- Custom ingredients with complete item-only matching now use indexed stock. Other custom predicates keep their live checks while reusing detached item samples.
- Recipe availability refreshes when client item tags reload, including in single player.

## 1.11.14 - 2026-10-29

### Fixed

- Fixed severe Nexus screen lag with large Repository inventories and JEI installed. Recipe availability now reuses indexed stock and unchanged recipe results instead of repeatedly scanning and copying every stored item.
- Recipe availability still updates when network contents, crafting ingredients, player inventory, or the cursor change. Custom ingredient predicates continue to check their current state.

## 1.11.13 - 2026-10-28

### Fixed

- JEI can fill Nexus crafting recipes using stored ingredients, including items outside the current search or visible page.
- Unsupported furnace conflicts now fail with an explanation instead of leaving the job locked.
- Registered the Fabric JEI plugin so the Nexus recipe-transfer handler loads.
- Workstations placed or replaced after a network's initial scan are discovered without restarting the network, including block changes made by commands or other mods.

EMI, Polymorph, and Iron Furnaces had no matching official 26.2/26.3 releases during the 2026-10-28 checks. Their runtime behavior is not claimed for this branch.

## 1.11.11 — 2026-09-20

### Fixed

- Automatic runes now wait for resources to reach their destination when instant logistics is disabled.
- Resources in transit survive world restarts and respect stock limits. Failed deliveries return safely or stay in recovery storage.
- Fixed clouds appearing in front of nearby resource animations and rune binding beams.
