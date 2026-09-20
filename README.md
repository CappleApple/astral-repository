# Astral Repository

A magical storage and automation network for Minecraft 1.21.1. Place crystals around a workshop, teach a bookshelf the products you want, and let the network find storage, route resources, and coordinate ordinary workstations.

Transfers follow the shortest loaded route with clear line of sight, travelling directly when in range or hopping through relays. Transparent blocks pass the connection; opaque shapes block it. Crafting uses actual storage and processor endpoints. Nexus inventory access completes immediately by default; automatic transfers use a 20-tick cadence. `instantPlayerInteractions=true` and `instantAutomaticLogistics=false` control those timings independently.

## Features

- A textured Storage Nexus interface with a continuously scrolling 9-column, 6-row storage view, normal cursor transfers, search, and autocrafting jobs with individual cancellation.
- Manual crafting refills matching ingredients from storage, clears the grid back into storage, and Shift-crafts up to one normal output stack at a time.
- An optional Patchouli Field Guide with screenshots for workshop setup and controls, plus a reference for the four network block roles. Its item and recipe appear only with Patchouli installed.
- Automatic nearby inventory discovery and connections between same-channel Relays, Nexuses, and Storage Crystals. Multiple Nexuses share one network.
- A wand preset library with a pixel editor, separate item overlays, and autosaving Push/Pull/Filter behavior. Place up to eight runes by default at chosen points on each container face; each can retain multiple container or whole-network targets.
- Rune inspection uses icons and amounts shown only while holding Shift. Look at a face to see its glyphs, hold a programming tool while building, or wear Astral Goggles in the helmet or optional Curios head slot for continuous visibility. Jade integration is optional.
- Capacity-based Astral Storage Crystals with individual storage views that retain their contents when moved.
- Four block roles: Storage Nexus, Relay Crystal, Storage Crystal, and optional Power Node. Storage and Relay upgrades reuse the higher-stage crystal models.
- A handheld Astral Nexus and distant network bridges, with separate upgrades for dimensional access.
- Recipe Tomes select actual recipes from inventory samples or EMI/JEI drops in a book editor and teach final outputs through vanilla Chiseled Bookshelves. Recipe searches are cached and planned on worker threads. Jobs appear immediately as Calculating, show tagged ingredient shortages, and resume when stock becomes available.
- Cadence sets per-rune batch amounts and intervals for supported item, fluid, energy, and Source storage.
- Real furnace processing and crafting-table assembly, including Visual Workbench tables without changing their saved player grids. Optional Create adapters provide pressing and milling.
- Item and fluid rune transfers, native item/fluid/FE storage, and optional Ars Nouveau Source, AE2, Refined Storage, and Stacks Not Slots adapters.
- Vanilla-amethyst geode shells, growth, and harvesting behavior. Astral Gems and the natural blocks use blue amethyst surfaces with a purple interior, depth-dependent parallax, drifting square and rectangular particles, and configurable opacity.
- Astral Gems work with all vanilla smithing trim patterns, adding the astral shader only to the trim on worn armor and item icons.
- Separate timing controls for player interactions and automatic logistics.
- Optional crystal-network upkeep, disabled by default.

## Versions

The default `main` branch builds the original Minecraft 1.21.1 NeoForge edition. Each other edition has a separate branch with its own build at the repository root.

| Minecraft | Loader | Branch |
| --- | --- | --- |
| 1.21.1 | NeoForge | [`main`](https://github.com/CappleApple/astral-repository/tree/main) |
| 1.20.1 | Forge | [`CappleApple/forge-1.20.1`](https://github.com/CappleApple/astral-repository/tree/CappleApple/forge-1.20.1) |
| 1.20.1 | Fabric | [`CappleApple/fabric-1.20.1`](https://github.com/CappleApple/astral-repository/tree/CappleApple/fabric-1.20.1) |
| 1.21.1 | Fabric | [`CappleApple/fabric-1.21.1`](https://github.com/CappleApple/astral-repository/tree/CappleApple/fabric-1.21.1) |
| 26.2 | Fabric | [`CappleApple/fabric-26.2`](https://github.com/CappleApple/astral-repository/tree/CappleApple/fabric-26.2) |
| 26.2 | NeoForge | [`CappleApple/neoforge-26.2`](https://github.com/CappleApple/astral-repository/tree/CappleApple/neoforge-26.2) |
| 26.3 | Fabric | [`CappleApple/fabric-26.3`](https://github.com/CappleApple/astral-repository/tree/CappleApple/fabric-26.3) |
| 26.3 | NeoForge | [`CappleApple/neoforge-26.3`](https://github.com/CappleApple/astral-repository/tree/CappleApple/neoforge-26.3) |

Download matching JARs from [Releases](https://github.com/CappleApple/astral-repository/releases). The [port guide](ports/README.md) lists runtime requirements and the [validation record](ports/VALIDATION.md) distinguishes automated checks from observed client behavior. The `ports/` projects on `main` retain the shared source layout used to maintain all editions together.

## Installation

Requires Minecraft **1.21.1**, **NeoForge 21.1.244 or newer**, and **Java 21**. Install Astral Repository on both the client and the server.

**Patchouli 1.21.1-93 or newer for NeoForge is optional.** Install it on both sides to add the `astral_repository:field_guide` item and its crafting recipe.

Put `astral_repository-1.11.8.jar` in the instance's `mods` directory. Optional integrations have specific access points and limits; see [Compatibility](docs/compatibility.md) and the [validation record](docs/validation.md).

## Start a workshop

1. Find an Astral geode and collect Astral Gems. With Patchouli installed, craft a book with two gems in any arrangement for the **Astral Field Guide**.
2. Craft a Storage Nexus and place it near chests or barrels. Open it once automatic discovery has populated the index.
3. Extend discovery with Relay Crystals within 16 blocks of other same-channel network crystals; they connect automatically.
4. Right-click a Recipe Tome, Shift-click an output in inventory or drag it from EMI/JEI, and choose **Inscribe**. Put the tome in a nearby Chiseled Bookshelf, then request that product through the Nexus.

For a chest-to-chest transfer, right-click the wand in air and left-click a **Push** or **Pull** preset. Right-click a preset to edit it; the **+** tile creates one. Click the source container for Push or the destination for Pull. Shift-right-click the placed glyph with the wand, then click its target containers. Click an assigned target again to remove it. A Relay or Storage Nexus target addresses its whole network. Nearby transfers need no relay; longer transfers follow loaded relay paths.

Right-click a glyph with any item or an empty hand to open its settings. Shift-click inventory items or drop items/fluids from EMI/JEI into the filter grid. Right-click a filter to remove it. Shift-right-click with the wand selects its targets without placing another rune. Shift-left-click removes the glyph. Personal presets live in the client instance; pack authors can distribute defaults through `config/astral_repository/rune_presets/`. Editing a preset leaves already placed runes unchanged.

A **Filter** rune restricts Astral deposits into its container and sets its insertion priority. Rune editors use icon grids, a color wheel with exact hex input, and emissive artwork. Numeric fields scroll by 1, or 10 with Shift, 100 with Ctrl, and 1,000 with both. Use -1 for unlimited stock; scrolling up starts at zero. The rune editor includes player inventory and an astral BNS sidebar when installed. Network blocks attach to floors, walls, or ceilings.

The [player guide](docs/player-guide.md) explains controls, per-rune assignments, filters, storage tiers, and remote attunements. The [configuration reference](docs/configuration.md) covers pack settings and datapack paths.

## Building and development

With Java 21 available, run on Windows:

```powershell
.\gradlew.bat test build
```

The mod JAR is written to `build/libs/astral_repository-1.11.8.jar`.

```powershell
.\gradlew.bat runGameTestServer
.\gradlew.bat runClientSmoke
```

GameTests exercise real block and network behavior. The client smoke run creates a temporary flat workshop for storage, crafting, rune, and material checks. Screenshots and its result are written under `build/client-smoke/`.

Add `-PpatchouliTest` to either test command to load Patchouli and validate the guide item, recipe, and book screens. The base profile runs without Patchouli; `-PcompatTest` includes it with the local integration-test mods.

Development `runClient` and `runClientSmoke` sessions set `astral_repository.testClient=true`: audio is muted and operating-system mouse capture is disabled. Normal installed clients do not enable that setting.

The Java namespace is `com.cappleapple.astralrepository`; the mod and resource namespace is `astral_repository`. Extension documentation is split by responsibility:

- [Architecture and indexing](docs/architecture.md)
- [Incremental Nexus synchronization](docs/networking.md)
- [Resource and storage provider API](docs/providers.md)
- [Craft planning and processing adapters](docs/crafting.md)
- [Optional integration boundaries](docs/compatibility.md)
- [Editable network models and export contract](art/blockbench/README.md)

Licensed under [CC BY-NC-SA 4.0 with additional permission for Minecraft modpacks and servers](LICENSE).

