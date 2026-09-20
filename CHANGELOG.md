# Changelog

## 1.11.8 - 2026-09-19

### Added

- Minecraft 1.20.1 Forge and Fabric editions, Minecraft 1.21.1 Fabric, and Minecraft 26.2 and 26.3 Fabric and NeoForge editions.

### Changed

- Licensed all editions under CC BY-NC-SA 4.0 with additional permission for Minecraft modpacks and servers.

- Updated the Attunement Wand texture and its extruded outline.

### Fixed

- Busy one-tick rune networks reuse bounded slot lookup buffers, provider identities, and relay references, reducing work and allocations for each transfer.
- Transfers validate retained chunks and container instances instead of repeatedly looking them up, while still rejecting replacement, unloading, state changes, and capability invalidation immediately.
- Distant transfer effects avoid unused path arrays, audience-cache entries, and exact distance checks before nearby viewers are selected.

## 1.11.7 - 2026-09-19

### Changed

- Shared relay route searches prepare on bounded worker threads from immutable snapshots. Live world checks and inventory changes remain on the server thread.

### Fixed

- Large rune installations retain their active container views instead of repeatedly rediscovering providers after exceeding a fixed cache limit.
- Busy inventories reuse unchanged item identities and update only changed item counts in the network index, reducing allocation and work per transfer. Transfers without stock limits can use a bounded live item probe instead of rebuilding an inventory snapshot.
- Finished crafting output returns to storage instead of occupying unrelated processing machines.
- Dense networks reuse nearby ownership lookups and avoid position-index collisions. Player rune updates inspect nearby chunks instead of every world rune.
- Cancelled crafting calculations release their queued worker slots immediately.

## 1.11.6 - 2026-09-18

### Fixed

- One-tick rune Cadence no longer slows down as more runes share the inventory reconciliation budget. Each loaded rune gets its due turn, with rotating order among equal priorities.
- Busy rune transfers update only the affected container views and network indexes instead of scanning unrelated containers and networks.
- Standalone runes no longer create unused storage indexes and crafting coordinators.
- Large shared networks reuse shortest-path searches across destinations, preventing stalls when many runes search many stores. Furnace lighting changes no longer rebuild routes when their blocking shape is unchanged.
- Filter checks reuse indexed rune surfaces while preserving immediate edits and policies on both halves of a double chest.

## 1.11.5 - 2026-09-18

### Changed

- Rune binding range defaults to eight blocks in new configs.
- Transfer animations batch network updates, reuse prepared paths, cull off-screen effects, and use configurable client and server budgets. Heavy item streams use flat icons beyond the full-model allowance.
- Fluid and energy circles use one quad with soft shader edges instead of tessellated rings.

### Fixed

- Long-running world clocks and server time corrections no longer make transfer animations skip. Pause and tick freeze stop cosmetic flight time.
- Excess visual traffic no longer evicts animations in progress or builds a delayed packet backlog. Large item components cannot consume an unbounded cosmetic encoding budget.
- World shader interiors remain steady with view bobbing enabled while normal camera parallax and block movement remain visible.
- Restored the original crystal-face artwork and UV layout while retaining proportional textures on network block bases and trim.

## 1.11.4 - 2026-09-18

### Changed

- Updated the Field Guide and Recipe Tome item artwork and removed faint translucent pixels.

## 1.11.3 - 2026-09-18

### Fixed

- View bobbing no longer shifts the astral interior on held items. Normal hand movement and camera parallax are preserved.
- Network block textures keep a consistent pixel scale on crystal facets, narrow bases, and trim instead of stretching a whole texture across each face.

## 1.11.2 - 2026-09-18

### Changed

- Fluid, energy, and Source sprites leaving runes start at half size and grow to full size during their three-tick fade-in.

## 1.11.1 - 2026-09-18

### Fixed

- Fluid, energy, and Source sprites fade in over their first three ticks when leaving runes. Their trails inherit the fade instead of appearing at full brightness inside the source face.

## 1.11.0 - 2026-09-18

### Added

- Astral Gems can be used as smithing trim material. Astral trims use blue shading and the astral shader on worn armor and item icons.

### Fixed

- Removed faint pixels from the supplied base textures and made near-opaque artwork fully opaque.
- Corrected stretched goggles textures on lenses, rims, and straps.
- Randomized transfer paths retain their spread through relays instead of converging at each hop.
- Container-first wand binding stays selected while assigning multiple runes. Rune-first Shift-clicks switch to the newly selected rune.
- Goggles use a flat, one-pixel-thick icon in inventories, hands, and dropped items. Equipped goggles retain their armor model and animated gem lenses.
- Gem lenses are translucent, with open frames so the wearer shows through.

## 1.10.4 - 2026-09-18

### Changed

- Installed the supplied reduced base textures, preserving their pixels and adding transparent canvas padding where needed.
- Updated the goggles material UVs and editable model to match the reduced texture atlas.

## 1.10.3 - 2026-09-18

### Added

- Server-configurable transfer amount caps and minimum intervals for items, fluids, energy, and Source.
- Optional Curios head-slot support for Astral Goggles.
- A custom goggles model and pixel texture with animated astral-gem lenses.

### Changed

- Renamed Resonance Goggles to Astral Goggles. Instructional item tooltips are removed.
- Dimensional Attunement replaces Remote and Bridge Attunement and is consumed when successfully applied in survival. Existing items resolve to the new item.
- The Power Node is hidden from creative lists, EMI/JEI, and the Patchouli guide when server power costs are disabled.
- Attunements, books, and natural crystals now have custom base textures.
- The default world/item astral shader opacity is now 0.4.
- Fluid and energy sprites, including their falling trails, fade around their circular edges.
- Fluid, energy, and Source batches have configurable landing variation and fade inside their destination. Textured trails use varied emission timing.
- Saving a placed rune as a preset preserves its painted artwork and item overlays for sharing.

## 1.10.2 - 2026-09-18

### Changed

- Fluid and energy transfers use circular texture crops and shed smaller matching sprites that fall and fade behind them.

## 1.10.1 - 2026-09-18

### Changed

- Renamed Arcane Cadence to Cadence in the rune interface and guides.

## 1.10.0 - 2026-09-18

### Added

- Arcane Cadence lets each Push/Pull rune set separate batch amounts and tick intervals for the item, fluid, energy, and Source capabilities exposed by its host. Changes save automatically and can be reset to server defaults.
- Push/Pull runes transfer energy and Source to compatible containers or connected networks. Presets retain per-resource cadence settings.
- Resource batches use configurable curve variation while preserving endpoints and relay approach points.

### Changed

- Workstations wait for ingredient deliveries before starting. Crafting tables display arriving ingredients separately; furnace input and fuel enter their slots only after their flights finish. Instant automatic logistics skips delivery waits.
- Fluids travel as flat flowing-fluid sprites. Energy and Source use flat textured sprites instead of particle trails.
- Priority replaces Clear Filter below the rune filter grid. Arcane Cadence occupies its former position.
- Binding particles emit from the selected rune only while it has no assignments. Shift-right-clicking air also exits binding mode.

### Fixed

- Binding beam glow and core no longer fight for depth, and adjacent ribbon segments share their edges.
- The loose Astral Gem uses sprite-local shader coordinates in held and inventory rendering.

### Removed

- The wand library's Bind links button.

## 1.9.4 - 2026-09-17

### Added

- A held binding wand displays the selected rune's assigned routes and a preview to the aimed container, following relay paths in the average color of its visible painted pixels.
- A small matching particle effect plays while the binding wand is selected in the hotbar.

### Changed

- Shift-right-clicking a non-container block ends wand binding while preserving assigned targets.

### Fixed

- Mining cracks now render on astral gem blocks, budding blocks, buds, and clusters.

## 1.9.3 - 2026-09-17

### Fixed

- Recipe Tome page arrows and page numbers now fit inside the book, using Minecraft book arrows.
- Recipe outputs appear directly on the paper without an astral slot background.

## 1.9.2 - 2026-09-17

### Fixed

- The wand now visibly renders its enchantment glint while a binding endpoint is selected.
- Keeping Shift held when clicking a target completes the pending binding instead of replacing its source. Target clicks work with Shift held or released, including Relay Crystal targets that address the whole connected network.
- Furnace fuel travels to the furnace instead of the requesting Nexus. Fuel and ingredient flights use separate approaches so both remain visible.
- Finished furnace products return to storage instead of being reinserted into their own furnace input.
- Opening a Storage Crystal shows only its own items. Deposits, withdrawals, grid clearing, and crafting refill stay within that crystal.


## 1.9.1 - 2026-09-16

### Changed

- Rune and tome inventories use normal pickup, placement, and drag controls. Shift-click copies a filter or recipe output.
- Empty filter areas use a translucent themed frame without a plus button.
- Rune state controls now read Enabled and Disabled.
- Container faces allow eight runes by default, controlled by the server's `maxRunesPerFace` setting.
- Inscribed tome tooltips show the output icon and localized item name.

### Fixed

- Shift-right-clicking a bindable endpoint with a placement-ready wand enters binding mode instead of placing a rune or opening the library.
- Wand binding glint includes selected containers and crystals.
- Buttons no longer retain their hover highlight after clicking.
- Rune editor text, hints, and caret placement align inside their fields.


## 1.9.0 - 2026-09-16

### Added

- Inventory selection and optional EMI/JEI ingredient drops for rune filters and Recipe Tomes.
- BNS inventory browser and astral sidebar in the rune editor and tome.
- Enchantment glint on the wand while selecting rune targets.

### Changed

- Compact rune settings with a **+** filter cell, right-click removal, and recessed numeric fields.
- Unlimited stock displays as **-1**; scrolling up starts at zero and scrolling down stays unlimited.
- Removed recipe and filter search pickers. Tome recipe arrows cycle alternatives for the selected output.

## 1.8.0 - 2026-09-16

### Added

- Rune preset grid with click-to-select, right-click editing, a new-preset tile, and Save Preset for placed runes.
- Filter runes restrict Astral insertions and set container priority without requiring a target.
- Color wheel, brightness slider, exact hex colors, and emissive rune artwork.
- Cached off-thread crafting calculation, immediate Calculating jobs, tagged shortage icons, and automatic retry when stock changes.

### Changed

- Rune settings use item/fluid icon grids, Whitelist/Blacklist, and any-match rules. Push/Pull/Filter cycles in both editors.
- Numeric fields scroll by 1, Shift 10, Ctrl 100, or Ctrl+Shift 1,000, including Nexus quantity.
- Transfer curves pass near relay centers instead of passing through them.
- Network blocks and their upgraded models orient to floors, walls, and ceilings.

### Fixed

- Right-clicking an existing rune opens its settings with any held item. Shift-right-clicking it with a placement-ready wand selects targets without adding another rune.
- Repeated batch recipes no longer grow the search stack with every operation.
- Bounded transfer batches no longer repeatedly skip the same rune.

## 1.7.0 - 2026-09-16

### Added

- Line-of-sight routing through transparent blocks, with opaque obstructions and unloaded chunks blocking each transfer leg. `requireLineOfSight` defaults to true; existing range settings control reach.

### Changed

- Item, fluid, and energy flights follow continuous splines through relay positions without stopping at each hop.
- Rune flights launch from the painted glyph, initially moving outward from its container face. Incoming flights approach from outside that face.
- Placing or removing obstructions updates connections and permits visible relay detours. Blocked player deposits and withdrawals leave inventory contents unchanged.

## 1.6.0 - 2026-09-16

### Changed

- Relays, Storage Nexuses, and Storage Crystals automatically connect to nearby same-channel crystals, retaining multiple paths.
- Shift-right-click a rune with the wand to select multiple targets. Clicking an assigned target removes it; Relay and Nexus targets use their entire connected network.
- Distant rune transfers require a loaded relay route and pause when that route is broken. Routing chooses the shortest total travel distance.
- Autocrafting sends ingredients and products directly between their actual stores and processors, using relays only when needed.
- Item and resource flights follow their relay hops in sequence. Instant player inventory changes retain readable travel animations.

### Removed

- Rune binding preview lines and inspected-target arrows.
- Retired crystal block IDs, rune items, and the old Astral Shard item. Active Relay and Storage Crystal upgrades retain their models.

## 1.5.1 - 2026-09-16

### Changed

- Shooting stars have a longer, brighter, connected pixel trail that tapers behind the head. Event frequency and travel speed are unchanged.

### Fixed

- Astral squares flickering where block surfaces coincide with virtual depth planes.
- Tiny distant particles flashing as their antialiasing crossed randomized cells or depth layers.

## 1.5.0 - 2026-09-16

### Added

- A personal wand preset library with pixel painting, independent item overlays, autosaving behavior, and pack-provided JSON presets.
- Free-position rune placement with overlap prevention, configurable size and resolution, and a range-limited binding preview.
- Push/Pull bindings to whole networks, excluding their own host inventory.
- Relay range and storage-capacity attunements that upgrade placed crystals without losing contents.

### Changed

- Consolidated obtainable network blocks into Storage Nexus, Relay Crystal, Storage Crystal, and Power Node. Higher-stage forms come from upgrades.
- Rune placement uses the selected wand preset. Shift-left-click removes a placed copy without requiring inventory space.
- The Field Guide explains the preset editor, network bindings, and crystal upgrades with new screenshots.

### Removed

- Rune-item recipes and creative entries, plus redundant crystal recipes and entries. Their saved registry IDs remain loadable.

## 1.4.7 - 2026-09-15

### Added

- A Network Crystals section in the Field Guide covering every network block, setup steps, storage capacities, and current Routing/Distribution control limitations.

### Fixed

- Shooting stars appear as one quick, shared streak per event instead of bursts repeated across depth layers and regions.

## 1.4.6 - 2026-09-15

### Added

- Independent client speed multipliers for broad layer drift, layer wobble, particle motion, twinkling, and shooting-star animation. Defaults preserve current speeds; zero stops the selected animation. Fractional rates remain continuous across the shader clock wrap.

## 1.4.5 - 2026-09-15

### Added

- Occasional pixelated shooting stars with stepped trails inside astral effects. The client setting `astralShootingStars` enables them by default.

### Changed

- Placed crystal surfaces look into a shared field anchored to world coordinates. The interior continues across adjacent faces instead of restarting with each block texture.

## 1.4.4 - 2026-09-15

### Added

- Editable Blockbench sources for all twelve network blocks, with four shared 16 by 16 textures.

### Changed

- Network blocks have distinct faceted silhouettes, mineral bases, and channel-colored inlays. Their crystal surfaces use the configurable astral overlay in the world and as items.

## 1.4.3 - 2026-09-14

### Changed

- Nexus slot hover tints are softer and translucent, including the crafting grid and player inventory. Selection outlines are translucent too.

## 1.4.2 - 2026-09-14

### Changed

- The crafting job list shows only current work, without empty rows, and disappears when no jobs remain.
- Scrollbars appear only when their lists overflow. Their thumbs scale with the visible portion of the list and stay at least 16 pixels tall.

### Fixed

- Storage hover highlights appear behind item icons instead of covering them.
- Grabbing a scrollbar thumb preserves its position instead of snapping its center to the cursor.

## 1.4.1 - 2026-09-14

### Changed

- Bundled Not Siloed's side-tab background matches the dark astral texture and overlay while a Storage Nexus is open, including remote access. Its buttons keep their native appearance.

## 1.4.0 - 2026-09-14

### Added

- A textured Nexus interface with a continuous 9-column, 6-row storage view, mouse-wheel scrolling, and a draggable scrollbar in place of page controls.
- Crafting job icons, quantities, progress bars, and individual cancellation in the Nexus.
- Separate common settings for instant player interactions and instant automatic logistics.
- An independent client opacity setting for the animated Nexus and rune-editor backgrounds.
- A Clear crafting grid button that returns accepted ingredients to network storage.
- Automatic manual-grid refill using the same item components, with crafting remainders returned to storage when space permits.

### Changed

- Nexus and rune-editor panels use dark blue and violet artwork, pale text, and opaque controls over the astral field.
- Faster star drift and twinkling, with broader movement across the astral depth layers.
- Right-click an empty craftable entry to request one item; Shift-right-click requests a normal stack even when that item is already stored.
- Middle-click a craftable entry to enter a quantity and submit it with the hammer button.
- Crafting updates and failures appear in the Nexus instead of chat.
- Shift-clicking a manual crafting result makes up to one normal output stack, stopping before a complete recipe output would exceed inventory space or that limit.
- Nexus synchronization sends changed counts and job state between full updates, and sends no catalog update while the view is unchanged.
- Automatic routing runs every 20 ticks by default. Instant mode runs it every tick and skips Astral-managed table and stonecutter delays while retaining transfer limits, costs, and external processing times.
- Patchouli is optional. The Field Guide uses `astral_repository:field_guide`; its item, creative entry, and recipe appear only when Patchouli is installed.

### Fixed

- Visual Workbench tables support network autocrafting while keeping their saved player ingredients and preview out of network storage.
- Changing one network no longer cancels active crafting jobs in unrelated networks.

## 1.3.0 - 2026-09-14

### Added

- A Patchouli Field Guide with screenshots, crafted from a book and two Astral Gems, for workshop setup and controls.
- Visual rune-filter search for items, tags, mods, and fluids.
- Shift-left-click pickup of individual runes. A full inventory leaves the rune and its settings placed.

### Changed

- Crystal overlays look into deeper moving layers, with stronger parallax and square or rectangular particles.
- Nexus left-click picks up a stack onto the cursor; right-click picks up half. Click the storage grid to deposit the cursor stack or one item.
- Shift-click Nexus withdrawals take only what fits in the player inventory; partial deposits keep their remainder on the cursor.
- Rune settings save automatically, including pending valid changes when closing with Done or Escape.
- Patchouli 1.21.1-93 or newer for NeoForge is now required on clients and servers.
- Moved Nexus control instructions from tooltips into the Field Guide.

## 1.2.2 - 2026-09-14

### Changed

- Updated the handheld Astral Nexus to use the supplied remote-orb artwork unchanged.
- The astral overlay covers the whole remote orb, preserves its base colors, and follows the existing opacity setting.
- Removed workflow instructions and saved/bookshelf guidance from Recipe Tome pages and item tooltips.

## 1.2.1 - 2026-09-14

### Changed

- The Attunement Wand uses custom artwork, with the astral overlay confined to its large head crystal. The frame, grip, and small pommel gem keep their original appearance.
- The handheld Astral Nexus uses a simple crystal-sphere sprite with filled item edges.

## 1.2.0 - 2026-09-14

### Added

- Independent Push and Pull Runes that move items and fluids directly between selected container faces without a Nexus.
- Per-rune targets, sample filters, stock limits, pause controls, and advanced settings for tags, exclusions, exact item data, and priorities.
- Shift-only rune inspection using icons and amounts through Jade or the built-in display, target arrows, and wand link previews.
- Slow star drift and twinkling with varied paths and timing across the astral overlay.

### Changed

- Rune faces become visible when looked at, while holding a programming tool, or while wearing Resonance Goggles.
- Container faces hold four runes with fixed-size centered, paired, triangular, or square layouts. Additional saved layers remain archived and return paused as visible runes are removed.
- The handheld Astral Nexus uses original 2D crystal artwork with normal Minecraft item thickness.
- Nexus controls explain withdrawal amounts and cursor deposits, require valid craft quantities, and show crafting feedback and job status.
- Recipe Tomes show saved-state guidance and a Done button after inscription.
- Handheld access errors identify unloaded destinations, missing bindings, missing dimensional attunement, and range limits.
- The Attunement Wand has normal Minecraft tool thickness and closed edges around its stick-and-crystal outline.
- The astral field follows the crystal surface with restrained parallax as the viewing angle changes.

### Fixed

- Fixed flickering on crystal buds and the wand by culling backfaces and removing overlapping wand surfaces.
- Fixed crafting failures disappearing from the Nexus when its storage page refreshed.

## 1.1.1 - 2026-09-14

### Fixed

- Restored the amethyst texture's brightness while shifting natural crystals, gems, and the wand tip to a deeper blue hue.

## 1.1.0 - 2026-09-14

### Added

- Flat rune items that stack on individual container faces and appear while wearing Resonance Goggles.
- Explicit wand links between crystals and rune faces, including rune-only routing and multiple Storage Nexuses on one network.
- A right-click Recipe Tome interface using Minecraft's book artwork, with recipe search, ingredient previews, and inscription.
- Darker blue amethyst surfaces with a purple astral parallax overlay across natural crystal blocks, buds, clusters, gems, and the wand tip.
- A client setting for astral overlay opacity, including fully disabling the overlay.

### Changed

- The Attunement Wand uses a flat stick-and-crystal tool model.
- Natural Astral geodes use vanilla amethyst shells, generation, growth, harvesting, sound, and piston behavior.
- Astral Shards are now Astral Gems; old saved items convert to gems in a crafting grid.

### Fixed

- Rune links and inscriptions are cleaned up when their container is replaced, while surviving chunk unloads.
- Separate rune faces can connect the same container to different networks without losing sided access.

## 1.0.0 - 2026-09-14

### Added

- Crystal networks that discover nearby storage and workstations, with dye channels and in-world routing, filters, priorities, and stock rules.
- Storage Nexus access with search, immediate inventory transactions, manual crafting, and autocrafting requests.
- Recipe Tome libraries, intermediate dependency planning, resource reservations, parallel workstation execution, and cancellation recovery.
- Capacity-based storage crystals, handheld Astral Nexus access, remote bridges, and separate dimensional attunements.
- Visible item and resource transport, crafting-table ingredient arrangements, and Resonance Goggles diagnostics.
- Item, fluid, FE, and optional Ars Nouveau Source providers, plus AE2, Refined Storage, Create, and Stacks Not Slots adapters.
- Optional configurable network upkeep, disabled by default.
- Astral geodes, renewable crystal buds and clusters, crafting recipes, and processing adapter datapack rules.
