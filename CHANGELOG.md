# Changelog

## 1.11.13 - 2026-10-28

### Fixed

- EMI and JEI can fill Nexus crafting recipes using stored ingredients, including items outside the current search or visible page.
- Iron Furnaces in normal furnace mode can run network smelting jobs, including blasting and smoking augments. Factory and generator modes remain unsupported.
- On Minecraft 1.20.1 and 1.21.1, Polymorph can select conflicting recipes in the Nexus grid and supported furnaces. Unsupported furnace conflicts now fail with an explanation instead of leaving the job locked.
- Fixed Forge 1.20.1 furnace jobs rejecting vanilla fuels such as coal.
- Workstations placed or replaced after a network's initial scan are discovered without restarting the network, including block changes made by commands or other mods.

## 1.11.12 - 2026-09-26

### Fixed

- Forge 1.20.1 now applies the astral shader only to crystal surfaces on network blocks and their items, preserving stone, trim, and channel accents.
- Restored missing Forge 1.20.1 node mesh parts caused by OBJ object names overwriting one another.

## 1.11.11 — 2026-09-20

### Fixed

- Automatic runes now wait for resources to reach their destination when instant logistics is disabled.
- Resources in transit survive world restarts and respect stock limits. Failed deliveries return safely or stay in recovery storage.
- Fixed clouds appearing in front of nearby resource animations and rune binding beams.
