# Configuration



Rune assignments are saved per world. Right-click a glyph with any held item to edit it. Shift-click inventory items or drop EMI/JEI ingredients onto its filter grid; filled containers select their fluid. Valid changes save automatically. The stock limit uses -1 for unlimited. Cadence shows only capabilities exposed by the attached face. Zero amount disables that medium; positive amounts and intervals follow the synchronized server limits below. Transfers also respect available stock and destination acceptance.



Patchouli 1.21.1-93 or newer for NeoForge is optional. Installing it on both sides enables the `astral_repository:field_guide` item and recipe; there is no separate config switch. The [player guide](player-guide.md) covers the book, Nexus controls, filters, and rune pickup.



Common and compatibility files hold pack settings; the client file holds local appearance settings. They do not replace per-rune assignments, filters, or explicit crystal links.



- `config/astral_repository-common.toml`: player and automatic timing, network budgets, capacities, ranges, visual density, and optional power.

- `config/astral_repository-compat.toml`: independent optional integration switches.

- `config/astral_repository-client.toml`: overlay opacity, animation speeds, and shooting stars; created only on clients.

- Datapacks: geode placement and single-input machine boundary rules.



Use matching gameplay settings on clients and servers. Relay range and line-of-sight changes rebuild connections after config reload. Restart after changing discovery coverage or storage capacity.



## Rune presets and placement



The server config is `<world>/serverconfig/astral_repository-server.toml`, synchronized to connected clients. Pack authors can put a template in `defaultconfigs/astral_repository-server.toml` for new worlds.



| Server key | Default | Allowed values / behavior |

| --- | ---: | --- |

| `runeResolution` | 32 | 8–128 pixels per side. Crops the top-left of larger artwork; hidden pixels remain saved. |

| `maxRunesPerFace` | 8 | 1–64 runes per container face. New placements also need enough free space; lowering the limit preserves existing runes. |

| `runeSize` | 0.20 | 0.0625–0.75 blocks per side. New placement fits to the usable container face and avoids existing squares. |

| `wandBindingRange` | 8 | Direct rune transfer range and maximum endpoint-to-relay distance, from 2 to 30,000,000 blocks. Longer routes require loaded same-channel relays. |



### Transfer limits



These server settings cap each automatic rune batch, including direct targets and whole-network targets. They also apply to saved presets and with `instantAutomaticLogistics` enabled. They do not throttle direct player Nexus transactions or crafting ingredient reservations.



| Medium | Maximum amount key | Default | Minimum interval key | Default |

| --- | --- | ---: | --- | ---: |

| Items | `maxItemTransfer` | 64 items | `minItemTransferTicks` | 1 tick |

| Fluids | `maxFluidTransfer` | 1,000 mB | `minFluidTransferTicks` | 1 tick |

| Energy | `maxEnergyTransfer` | 10,000 FE/RF | `minEnergyTransferTicks` | 1 tick |

| Source | `maxSourceTransfer` | 1,000 Source | `minSourceTransferTicks` | 1 tick |



Each config value accepts 1 through 2,147,483,647. Raising the item cap allows bulk transfers above one stack where providers support them. Existing common defaults (`transferRate`, `fluidTransferRate`, `energyTransferRate`, `sourceTransferRate`, and `runeTransferInterval`) choose the starting cadence; these limits bound it. Saved preferences remain intact when limits change, while actual transfers and the editor use the current bounds. Changing settings does not reset an active cooldown.



A container face accepts up to `maxRunesPerFace` runes if they fit (eight by default). Lowering the limit preserves existing runes and blocks additions until space is available. Placement keeps existing positions. Increasing size later refits existing runes to their face and reduces their displayed size when necessary to avoid overlap.



Pack presets are JSON files in `config/astral_repository/rune_presets/`, on the client or server. A newly created folder receives editable `push.json`, `pull.json`, and `filter.json` examples. Copy a player-made JSON file into this folder to distribute it. Use a distinct UUID for each preset. Up to 64 files are read in filename order; invalid files are skipped and logged. Server presets are sent when the wand editor opens.



Personal files live at `<instance>/astral_repository/runes/<player-UUID>/<preset-UUID>.json`. They are shared by that player's wands across worlds in the instance. The optional `cadence` field stores an SNBT compound with `ITEMS`, `FLUID`, `ENERGY`, or `SOURCE` entries containing integer `Amount` and `Ticks` values. Omitted entries follow server defaults. Each preset is imported once; personal edits take precedence. `pack-presets.txt` records imported IDs, so deleting a preset does not make it reappear on the next open. Give a pack preset a new UUID to distribute it as a new entry.



Each JSON file stores `id`, `name`, `mode` (`PUSH`, `PULL`, or `FILTER`), `resolution`, `pixelFormat`, base64 `pixels`, `filter` (SNBT), `priority`, `enabled`, and `icons`. New files use `pixelFormat: "argb32"`: each pixel is a big-endian 32-bit ARGB integer, row by row. Zero alpha is transparent; painted pixels are opaque. Files without `pixelFormat` retain the earlier one-byte RGB332 decoding. An icon stores `item` (registry ID), `x`, `y`, `scale` (pixels), and `rotation` (degrees). Up to eight item layers remain separate from paint. Missing items render blank.



The library holds at most 64 presets, with saved artwork up to 128 by 128 pixels. Names are limited to 32 characters, JSON files to 192 KiB, and editor network messages to 96 KiB. Oversized or invalid designs are rejected. Use the editor-generated files as templates rather than hand-encoding the pixel buffer.



The server keeps a validated player mirror in world saved data for placement. Each placed rune saves an independent copy of its design and behavior alongside its target and position. Editing or deleting the personal preset leaves placed runes unchanged.



## Client appearance



`config/astral_repository-client.toml` contains local appearance settings:



```toml

astralOverlayOpacity = 0.4

astralInterfaceOverlayOpacity = 0.24

astralShootingStars = true

astralLayerDriftSpeed = 1.0

astralLayerWobbleSpeed = 1.0

astralParticleSpeed = 1.0

astralTwinkleSpeed = 1.0

astralShootingStarSpeed = 1.0

```



`astralOverlayOpacity` accepts values from `0.0` to `1.0`, with a default of `0.4`. On natural blocks, buds, clusters, and Astral Gems, it places a purple field and stars over the blue amethyst surface. At `0.0`, only the blue base is visible. At `1.0`, the full purple field and stars replace the base color. In the world, surfaces look into shared depth planes anchored to world coordinates, including across different blocks and independently textured faces. Camera angle and position change which part of this volume is visible. Inventory and interface effects retain their own presentation coordinates. Square and rectangular particles drift and twinkle at varied rates while the larger layers move behind them.



The same setting controls the overlay on the Attunement Wand's large head crystal. That head retains the custom texture's colors beneath the overlay; `0.0` shows its original artwork. The frame, grip, and small pommel gem use the ordinary item material and are unaffected. The entire handheld Astral Nexus orb also uses this setting and preserves its original texture colors beneath the overlay.



`astralInterfaceOverlayOpacity` accepts values from `0.0` to `1.0`, with a default of `0.24`. The Nexus and rune editor use dark blue and violet panels with pale text. Their backgrounds use the same animated astral field as the crystals, behind opaque slots and controls. At `0.0`, the original panel artwork is displayed without the field; at `1.0`, the full field covers the panel background. Interface animation is independent of the camera, world lighting, and fog. With Bundled Not Siloed installed, the same setting controls its side-tab background while a Storage Nexus is open. Its buttons use Bundled Not Siloed's normal rendering.



`astralShootingStars` defaults to `true`. One square-headed shooting star with a connected, tapering pixel trail appears at a randomized point in each 24-second interval, crossing in about 0.7 seconds at the default speed. Placed surfaces share a single trajectory, fixed in world space while it travels. Item and interface effects show the same event timing. Set it to `false` to keep the existing drifting stars without shooting stars. Overlay opacity controls both kinds of stars.



All five speed settings accept `0.0` through `10.0` and default to `1.0`. They are multipliers: `0.5` is half speed, `2.0` is double speed, and `0.0` stops that animation. They apply to world, item, and interface effects.



| Setting | Animation |

| --- | --- |

| `astralLayerDriftSpeed` | Broad movement of the nebula and star layers. |

| `astralLayerWobbleSpeed` | Small oscillations of those layers. |

| `astralParticleSpeed` | Movement of individual square and rectangular stars within each layer. |

| `astralTwinkleSpeed` | Star brightness pulsing. |

| `astralShootingStarSpeed` | Shooting-star travel, fading, and event cadence together. Zero suppresses shooting stars. |



A particle follows its layer as well as its own motion; stop both layer speeds and particle speed to hold positions still. Setting all five to zero freezes timed animation while camera-driven world parallax still works. Fractional rates remain continuous across the shader clock's daily wrap. Changing a multiplier can change its current animation phase.



`resourceArrivalVariance` defaults to `0.18` blocks and accepts `0` through `0.45`. Each fluid, energy, or Source batch samples one landing point inside the receiving block. Zero uses its center. These sprites remain opaque outside the block and fade to zero inside it. Trail releases use independent fractional times and randomized intervals of one to three ticks; smaller matching sprites fall and fade after release. This does not change resource delivery or transfer rates.



Save the file to apply changes after NeoForge reloads the client config. The next material or interface draw uses the new value, without restarting the game or reloading shaders. These settings are not synchronized by servers.



`transferPathVariation` is a client setting from 0.0 to 2.0 blocks, default 0.2. Each resource batch gets a different sideways curve offset. The offset fades to zero only at the source and destination. Relay crossings retain the same variation range as mid-flight points, with continuous direction through each hop; zero disables it.



## Transfer animation budgets



Animation budgets change presentation only. Real item, fluid, energy, and Source transactions keep their normal cadence and amounts.



| File | Key | Default | Behavior |

| --- | --- | ---: | --- |

| Server | `maxVisualsPerPlayerTick` | 128 | Sample up to this many moving batches per nearby player per tick (1–1,024). Excess offers are not queued for later. |

| Server | `maxVisualBytesPerPlayerTick` | 65,536 | Maximum bytes per player per tick, including workstation displays (4,096–262,144). Large cosmetic icons can be omitted. |

| Client | `maxActiveTransfers` | 4,096 | Maximum simultaneous moving animations (64–65,536). New offers above this limit are skipped; existing flights finish. |

| Client | `maxItemTransferModels` | 128 | Full 3D moving item models (0–4,096). Additional item flights use batched flat icons. |

| Client | `maxResourceTrails` | 512 | Detached fluid, energy, and Source trail sprites (0–8,192). |

| Client | `maxTransferTrailEmissions` | 32 | Shared trail emissions per client tick before `particleDensity` is applied (0–512). |

| Client | `transferRenderDistance` | 96 | Draw distance for transfer models, icons, and trails (16–256 blocks). Server delivery covers observers within approximately 128 blocks of a route. |



Ordinary overflow item icons reuse flattened baked GUI faces. Custom item renderers or complex models use their particle artwork. Preparation is spread over frames (128 new identities, 16 model resolutions, and four flattened meshes per frame); a new icon can wait briefly for its model. Full 3D flights keep their display mode until they finish.

The server sends at most one visual batch per observer per tick. Workstation updates are coalesced separately and take priority over moving effects; overload resets stale workstation displays. Encoding work and temporary buffers are bounded as well as outgoing bytes. No animation packets or cosmetic route calculations are needed in dimensions with no players.



Local flight time is independent of server time corrections and freezes with the game. Integer tick differences are calculated before adding frame interpolation, avoiding the precision loss caused by converting a long-running world clock to float. Path variation is prepared once per flight; changes to `transferPathVariation` apply to new flights.



Higher limits allow more visible detail but increase rendering and bandwidth costs. Lowering a client population limit takes effect as existing flights finish.



## Interaction and logistics timing



These common settings control server behavior independently:



```toml

instantPlayerInteractions = true

instantAutomaticLogistics = false

```



`instantPlayerInteractions = true` completes player-triggered Nexus transfers, grid refill/clear actions, and craft submissions immediately. When false, a transaction waits 20 server ticks before the menu revalidates access and its captured inventory state. A changed inventory or closed menu cancels the pending action before items or power are consumed. Only one deferred transaction can be pending in a menu.



`instantAutomaticLogistics = false` schedules crystal-network routing every 20 server ticks. Push/Pull runes use `runeTransferInterval` unless overridden per resource in Cadence. With this setting false, a batch is extracted at departure and inserted only after its actual route travel time. Batches can overlap in flight, so a one-tick Cadence does not skip travel time. Astral-managed crafting-table work takes 36 processing ticks; stonecutter work takes 20. When true, network routing and default rune intervals use one tick, and automatic rune batches enter their destination immediately; explicit rune cadence overrides remain in effect. Delivery waits are skipped and those managed operations can finish on their first scheduler poll after starting. Discovery, recipe planning, processor reservations, quantity budgets, and power costs remain in effect. Upkeep is still evaluated every 20 ticks.



This setting does not accelerate physical furnace cooking, Create machines, or external crafting backends. They must consume their real inputs and complete their own processing. Workstations wait for their ingredient flights before processing; furnace fuel also waits before entering its slot. Instant automatic logistics removes those delivery waits. Cosmetic flights remain visible. Each hop takes 20–100 ticks based on distance, and one flight follows a continuous spline through its waypoints without stopping at each relay. Rune effects start at the placed glyph and launch outward along its face; incoming effects approach that face from outside.



## Coverage, storage, and work budgets



| Common key | Default | Meaning |

| --- | ---: | --- |

| `coverageRange` | 5 | Spherical discovery radius around each crystal, in blocks. |

| `requireLineOfSight` | true | Require loaded, unobstructed sight for each same-dimensional relay link and transfer leg. |

| `relayRange` | 16 | Maximum distance for ordinary crystal links, direct Nexus/crafting transfers, and their endpoint-to-relay legs. |

| `remoteRange` | 4,096 | Maximum same-dimensional distance for handheld Nexus access and links between two range-upgraded Relays; upgraded Relays connect automatically. |

| `maxNodes` | 16,384 | Maximum loaded crystals and explicitly linked rune-face endpoints admitted to topology construction. Standalone runes do not create graph components. |

| `discoveryBudget` | 512 | Block positions examined per server tick across networks. |

| `reconciliationBudget` | 8 | Provider polling budget per server tick across networks. |

| `reconciliationTicks` | 100 | Fallback interval after a provider publishes a completed snapshot. |

| `transferRate` | 16 | Item budget per crystal-network routing pass; default item batch amount for each rune. |

| `fluidTransferRate` | 250 | Fluid mB per source resource in network routing; default fluid batch amount for each rune. |

| `energyTransferRate` | 1,000 | FE per source resource in network routing; default energy batch amount for each rune. |

| `sourceTransferRate` | 1,000 | Source units per source resource in network routing; default Source batch amount for each rune. |

| `runeTransferInterval` | 20 | Default ticks between rune resource batches, from 1 through 2,147,483,647. Effective intervals also obey the per-medium server minimums. |

| `parallelism` | 32 | Maximum active local processing operations per network. |

| `seedStorageCapacity` | 16,384 | Seed Storage Crystal item-capacity units. |

| `moonStorageCapacity` | 262,144 | Moon Storage Crystal item-capacity units. |

| `starStorageCapacity` | 4,194,304 | Star Storage Crystal item-capacity units. |

| `bufferCapacity` | 4,096 | Power Node internal item-capacity units. |

| `particleDensity` | 1.0 | Cosmetic particle budget multiplier; 0 disables transfer trails while travelling resource sprites remain visible. |



Sight uses the line between block centers and ignores the two endpoint blocks. Glass, panes, leaves, water, and other non-occluding blocks pass it. An opaque partial block blocks only where its occlusion shape intersects the line. An intervening unloaded chunk also blocks sight. Opaque block changes invalidate cached routes; relays can take a visible detour even when the endpoints are within direct range. Explicit dimensional bridges have no physical intervening sight line. Handheld remote-access distance remains controlled separately by `remoteRange`.



Rune transfers schedule each resource independently using its effective Cadence amount and interval. Every loaded, enabled Push/Pull rune is checked each server tick; `reconciliationBudget` limits inventory indexing, not the number of rune turns. Equal-priority runes rotate their starting order. A one-tick cadence can attempt a transfer every tick; unavailable stock, rejected items, unloaded routes, or provider scan work can still prevent a transfer. A rune can retain up to 32 targets, visited in rotation. Assignments require the same dimension; `wandBindingRange` limits direct travel and endpoint-to-relay reach. Longer transfers require a loaded route. Container-to-container transfers do not use network power; a bound network endpoint applies its normal access and power rules.



Discovery and reconciliation run across ticks, so these budgets do not make all storage refresh simultaneously. The built-in item handler examines at most 512 slots per provider poll; very large inventories can require several polls to produce a snapshot.



Native capacity cost is `64 / maximum stack size`, retaining fractions. Optional Stacks Not Slots integration replaces that calculation with its public exact capacity API. See [Player guide](player-guide.md) for storage retention and nesting behavior.



## Optional power



`powerEnabled` defaults to `false`. Networks work without FE, Source, fuel, or stress requirements until this setting is enabled. The Power Node is hidden from creative lists, EMI/JEI, and the Patchouli guide while costs are disabled. Connected clients follow the server setting, including changes during play; existing blocks remain registered.



Power Nodes draw from compatible storage or providers on their attached side. All costs are evaluated as nonnegative amounts and rounded upward for payment. Item and fluid fuel can provide more value than one operation needs; the excess becomes persistent credit on the Power Node.



| Common key | Default | Meaning |

| --- | --- | --- |

| `powerEnabled` | `false` | Enable upkeep and operation costs. |

| `powerMode` | `"any"` | `any` accepts the first allowed resource type able to pay the full cost; `all` requires the full cost in every allowed type. |

| `powerProviders` | `["energy"]` | Ordered allowed kinds: `energy`, `source`, `item`, `fluid`, and `stress`. |

| `itemFuel` | `"minecraft:amethyst_shard"` | Item consumed when the `item` kind is selected. |

| `itemFuelValue` | 10,000 | Cost units supplied by one fuel item. |

| `fluidFuel` | `"minecraft:lava"` | Fluid consumed when the `fluid` kind is selected. |

| `fluidFuelValue` | 10 | Cost units supplied by one millibucket of fuel. |



For FE **or** Ars Source:



```toml

powerEnabled = true

powerMode = "any"

powerProviders = ["energy", "source"]

```



For FE **and** spare Create stress capacity:



```toml

powerEnabled = true

powerMode = "all"

powerProviders = ["energy", "stress"]

```



A listed optional resource kind needs its corresponding loaded, enabled integration. `any` does not combine half a payment from FE with half from Source; each selected kind must satisfy its own full requirement. Create stress is a capacity lease checked against the running kinetic network, not a consumable fuel.



### Cost scaling



Periodic upkeep is calculated every 20 ticks from:



```text

baseCost

+ nodeCost × connected crystals

+ storageCost × discovered storage providers

+ capacityCost × indexed provider capacity

+ jobCost × active craft requests

+ processorCost × active local processors

+ dimensionalCost × max(0, connected dimensions - 1)

```



`capacityCost` defaults to zero, avoiding capacity enumeration when that charge is unused.



| Cost key | Default | Charged behavior |

| --- | ---: | --- |

| `baseCost` | 1.0 | Base upkeep. |

| `nodeCost` | 0.1 | Upkeep per connected crystal. |

| `storageCost` | 0.1 | Upkeep per discovered storage provider. |

| `capacityCost` | 0.0 | Upkeep per reported capacity unit. |

| `transferCost` | 1.0 | Base charge for a routing operation or charged Nexus interaction. |

| `itemCost` | 0.1 | Per item moved by automatic routing. |

| `fluidCost` | 0.001 | Per millibucket moved by automatic routing. |

| `energyCost` | 0.0 | Per FE moved by automatic routing. |

| `sourceCost` | 0.1 | Per Source unit moved by automatic routing. |

| `jobCost` | 1.0 | Per active request in upkeep, and base charge when submitting a craft. |

| `processorCost` | 1.0 | Per active local processor in upkeep. |

| `remoteCost` | 5.0 | Additional charge for a handheld Nexus operation. |

| `distanceCost` | 0.001 | Per block of same-dimensional handheld distance. |

| `dimensionalCost` | 20.0 | Per additional connected dimension in upkeep, and dimensional handheld-operation surcharge. |

| `complexityCost` | 1.0 | Per planned operation when submitting a local craft. |



A craft submission costs `jobCost + complexityCost × planned operations`. An external backend request uses one complexity unit. Automatic resource transfers use `transferCost` plus the appropriate quantity charge. Remote access adds its configured remote and distance/dimension charges to the Nexus operation cost.



Insufficient power rejects new charged interactions and pauses network routing/scheduling. Interaction delays are controlled separately by `instantPlayerInteractions`. A physical furnace or modded machine can continue processing input already supplied to it through its own normal mechanics.



## Compatibility switches



The following keys in `astral_repository-compat.toml` all default to `true`:



| Key | Optional integration |

| --- | --- |

| `arsNouveau` | Ars Nouveau Source storage, transfer, and power supply. |

| `create` | Create processing rules and spare-stress provider. |

| `appliedEnergistics2` | AE2 exposed storage and CPU crafting. |

| `refinedStorage` | Refined Storage 2 storage and crafting. |

| `stacksNotSlots` | Exact capacity cost lookup. |



Disabling an integration prevents its adapter from being used. Ordinary NeoForge item, fluid, and FE capabilities remain available. Jade inspection and the Patchouli Field Guide are separate optional integrations without switches in this file. Visual Workbench tables are recognized automatically when installed; their persistent player grids are excluded from network storage. See [Compatibility](compatibility.md) for each integration's access point and validation status.



## Field Guide resources



The shapeless recipe at `data/astral_repository/recipe/field_guide.json` combines one `minecraft:book` and two `astral_repository:astral_gem` items. Its result is the mod-owned `astral_repository:field_guide`. A `neoforge:mod_loaded` condition enables the recipe only when Patchouli is installed; item registration and the creative entry follow the same condition.



The book definition is `data/astral_repository/patchouli_books/field_guide/book.json`. Its `custom_book_item` points to `astral_repository:field_guide`, and `dont_generate_book` prevents a duplicate generic Patchouli book. English categories and entries are under `assets/astral_repository/patchouli_books/field_guide/en_us/`; guide screenshots are under `assets/astral_repository/textures/gui/guide/`. The recipe is datapack content, while the localized pages and images are resource-pack content.



## Datapacks and world generation



Geodes use vanilla amethyst generation parameters with Astral Gem blocks and budding blocks substituted in the inner layer. Their datapack resources are:



- `data/astral_repository/worldgen/configured_feature/astral_geode.json`: geode layers, filling, buds, and generation settings.

- `data/astral_repository/worldgen/placed_feature/astral_geode.json`: placement rarity and height.

- `data/astral_repository/neoforge/biome_modifier/astral_geodes.json`: biome selection and generation step.



The default placed feature uses a rarity check of 24, a uniform height from six blocks above the world's bottom through Y=30, and the biome placement filter. The biome modifier adds it to `#minecraft:is_overworld` during `underground_decoration`. Placement attempts can fail local geode constraints, so the rarity value is not a guarantee of one geode in every 24 chunks. Worldgen changes affect newly generated terrain.



Natural blocks, buds, clusters, and Astral Gems use vanilla amethyst model and texture references. The shader retints the original surface blue while preserving its brightness and projects the viewing ray through seven purple layers behind the surface, at the opacity selected in the client config. Depth-dependent intersections create parallax as the camera moves. The particles have square, wide-rectangle, and tall-rectangle silhouettes, with varied paths and twinkle timing, plus broader movement of the depth layers. Orthographic GUI items use parallel rays so moving an icon across the screen does not change its interior. The material is defined by `assets/astral_repository/shaders/core/astral_plane.json`, `astral_plane.vsh`, and `astral_plane.fsh`. These are client resource-pack assets, separate from datapack generation settings; the shader generates its own stars and cloud layers without sampling End portal textures.



The Attunement Wand uses the original 34 × 36 artwork at `assets/astral_repository/textures/item/attunement_wand.png`. Its generated `assets/astral_repository/models/item/attunement_wand_geometry.json` partitions that one sprite by the explicit pixel spans in [the crystal mask](../tools/attunement_wand_crystal_mask.json). Only the large head crystal has tint index 0 and receives the astral overlay; the frame, grip, and small pommel gem remain ordinary, untinted item quads. The overlay preserves the custom artwork's underlying colors.



The mask is a development input to [the geometry generator](../tools/generate_wand_geometry.py), not a runtime texture. Changing the silhouette or moving the head in a resource pack also requires matching generated geometry and material assignments. The item retains its source aspect ratio and closed edges.



The handheld Astral Nexus uses the supplied 47 × 47 crystal-sphere texture unchanged at `assets/astral_repository/textures/item/astral_nexus.png`. Its flat item geometry retains filled edges. The entire orb receives the same configurable astral overlay as the wand's large crystal, preserving its original texture colors beneath the effect. Setting `astralOverlayOpacity` to `0.0` displays the original orb artwork without the overlay.



Single-input machine adapters use `data/<namespace>/astral_repository/processors/<id>.json`. Their exact schema, the shipped Create rules, supported sides, slot numbering, and override behavior are documented in [Crafting](crafting.md#datapack-machine-boundaries).



