# Changelog

## 1.11.12 - 2026-09-26

### Fixed

- Forge 1.20.1 now applies the astral shader only to crystal surfaces on network blocks and their items, preserving stone, trim, and channel accents.
- Restored missing Forge 1.20.1 node mesh parts caused by OBJ object names overwriting one another.

## 1.11.11 — 2026-09-20

### Fixed

- Automatic runes now wait for resources to reach their destination when instant logistics is disabled.
- Resources in transit survive world restarts and respect stock limits. Failed deliveries return safely or stay in recovery storage.
- Fixed clouds appearing in front of nearby resource animations and rune binding beams.
