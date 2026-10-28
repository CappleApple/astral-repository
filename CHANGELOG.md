# Changelog

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
- Fixed custom effects failing to render with improved transparency on Minecraft 26.3.
