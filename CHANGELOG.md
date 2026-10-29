# Changelog

## 1.11.14 - 2026-10-29

### Fixed

- Fixed severe Nexus screen lag with large Repository inventories and EMI or JEI installed. Recipe availability now reuses indexed stock and unchanged recipe results instead of repeatedly scanning and copying every stored item.
- Recipe availability still updates when network contents, crafting ingredients, player inventory, or the cursor change. Custom ingredient predicates continue to check their current state.

## 1.11.13 - 2026-10-28

### Fixed

- EMI and JEI can fill Nexus crafting recipes using stored ingredients, including items outside the current search or visible page.
- On Minecraft 1.20.1 and 1.21.1, Polymorph can select conflicting recipes in the Nexus grid and supported furnaces. Unsupported furnace conflicts now fail with an explanation instead of leaving the job locked.
- Registered the Fabric JEI plugin so the Nexus recipe-transfer handler loads.
- Workstations placed or replaced after a network's initial scan are discovered without restarting the network, including block changes made by commands or other mods.

## 1.11.11 — 2026-09-20

### Fixed

- Automatic runes now wait for resources to reach their destination when instant logistics is disabled.
- Resources in transit survive world restarts and respect stock limits. Failed deliveries return safely or stay in recovery storage.
- Fixed clouds appearing in front of nearby resource animations and rune binding beams.
