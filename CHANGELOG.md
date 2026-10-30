# Changelog

## 1.11.15 - 2026-10-30

### Fixed

- Fixed remaining Nexus opening, EMI recipe-screen and recipe-fill pauses with large networks and supported Moonlight block-type swap ingredients (Moonlight 1.20-2.16.38B on this branch).
- Custom ingredients with complete item-only matching now use indexed stock. Other custom predicates keep their live checks while reusing detached item samples.
- Recipe availability and EMI's craftable list refresh when client item tags reload, including in single player.

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

## 1.11.12 (Fabric 1.20.1) - 2026-10-13

### Fixed

- Fixed invisible geodes and crystal surfaces with Iris shader packs enabled. Astral materials use their ordinary textures while a shader pack is active and restore the procedural effect when shaders are disabled.

## 1.11.11 — 2026-09-20

### Fixed

- Automatic runes now wait for resources to reach their destination when instant logistics is disabled.
- Resources in transit survive world restarts and respect stock limits. Failed deliveries return safely or stay in recovery storage.
- Fixed clouds appearing in front of nearby resource animations and rune binding beams.
