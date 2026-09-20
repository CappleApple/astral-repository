# Player guide

## Your first workshop

Patchouli 1.21.1-93 or newer for NeoForge is optional. With it installed on the client and server, craft one book with two Astral Gems in any arrangement to make the **Astral Field Guide** (`astral_repository:field_guide`), then right-click it for setup and controls. The guide item and recipe are absent without Patchouli.

Find an Astral geode in newly generated Overworld terrain from six blocks above the world bottom through Y=30. Its smooth-basalt shell surrounds calcite, blue Astral Gem blocks, and budding blocks. Geode size, frequency, cracks, and crystal placement follow vanilla amethyst geodes. The natural blocks, buds, clusters, and gems retain amethyst-style shapes with custom blue crystal textures beneath a subtle purple astral overlay. The surface acts as a window into seven depth layers: moving the camera shifts distant details farther than nearby details. Square and rectangular particles drift and twinkle slowly within those layers. The Attunement Wand uses custom artwork. Its large head crystal receives the moving astral overlay, while the frame, grip, and small pommel gem keep their original appearance. Set `astralOverlayOpacity` in `config/astral_repository-client.toml` to adjust the overlay from `0.0` (off, including stars) to `1.0` (full purple field); the default is `0.4`. Saving the file updates the appearance after the client config reloads, without restarting the game.

Budding Astral Gem blocks grow small, medium, and large buds into mature Astral Clusters on adjacent air or source-water blocks. Leave them in place: budding blocks drop nothing, including with Silk Touch, and pistons destroy them. Silk Touch preserves buds and clusters. A mature cluster yields four Astral Gems with a pickaxe, with Fortune bonuses, or two with other tools.

Craft a Storage Nexus with four Astral Gems, a crafting table, and three iron ingots. Place it near chests or barrels, then open it. Compatible storage within five blocks is discovered automatically. Discovery runs over several ticks, so a newly placed Nexus can briefly show a discovery status.

To extend the workshop, place Relay Crystals within range and line of sight of another same-channel Relay, Storage Crystal, or Nexus. They connect automatically. Each connected crystal adds its own five-block discovery radius. Ordinary discovered chests do not need individual links. Multiple Storage Nexuses in the same connected graph share storage and crafting requests.

## Storage Nexus controls

The Storage Nexus combines storage, manual crafting, and autocrafting. Storage appears in nine columns and six visible rows. The scrollbar appears when more than six rows match the search; its thumb scales with the visible portion of the list, with a 16-pixel minimum. The mouse wheel and scrollbar move through one continuous list.

| Interaction | Result |
| --- | --- |
| Left-click a stored item with an empty cursor | Pick up one normal stack on the cursor |
| Right-click a stored item with an empty cursor | Pick up half of the available normal stack, rounded up |
| Shift-left-click a stored item | Move up to one normal stack into available player inventory space |
| Right-click a craftable entry with nothing stored and an empty cursor | Request one final item |
| Shift-right-click a craftable entry with an empty cursor | Request one normal stack, even if the network already stores some |
| Middle-click a craftable entry | Select its output and focus the quantity field without withdrawing |
| Left-click anywhere in the storage grid while carrying a stack | Deposit the cursor stack |
| Right-click anywhere in the storage grid while carrying a stack | Deposit one item |
| Shift-click a player inventory stack | Deposit that stack directly |
| Mouse wheel over storage or drag its scrollbar | Scroll the storage list by rows |
| Place ingredients in the 3×3 manual crafting grid | Craft normally; matching ingredients refill from network storage after taking the result |
| Shift-click the manual crafting result | Craft up to one normal output stack into player inventory |
| **Clear crafting grid** | Return ingredients to network storage; rejected items remain in the grid |
| Select a taught product, enter 1–4,096, press the hammer button | Request that many final items |
| Click the cancel icon beside a running job | Cancel that individual request |

Deposits and withdrawals complete immediately by default. Pack settings can defer player transactions by 20 server ticks; the menu rechecks access and inventory state before committing. Closing the menu or changing the affected inventory cancels a pending transaction. Cursor pickups can be placed in player inventory slots or the manual crafting grid with normal left-click, right-click, and drag controls. Partial deposits leave rejected items on the cursor. Shift-click withdrawal leaves the rest in network storage. Items in the manual crafting grid return when the interface closes.

Manual refill preserves the original ingredient identity, including item components such as names. It returns container remainders, such as empty buckets, to storage before refilling that slot; a remainder that storage rejects stays in the grid. Shift-crafting stops when ingredients run out, the output changes, or the next complete recipe output would exceed a normal stack or available inventory space. It does not drop a partial-fit result.

The hammer button stays disabled until you select a taught output and enter a valid amount. Finished products return to network storage. Requests appear at the lower right immediately as **Calculating**. Cached recipe searches and dependency planning run off-thread against snapshots; the server rechecks reservations before taking ingredients. Missing jobs show ingredient icons and counts. Click one to open a scrollable shortage grid; a **#** marks a recipe tag, whose ID appears on hover. Supplying matching stock recalculates the same job automatically. Queued and running jobs show requested amounts and progress. Finished, cancelled, and failed jobs leave the list. Only occupied rows are shown, and the entire list disappears when no work remains. Its scrollbar appears above three jobs; scroll that list independently when it fills. Hover the error icon for any displayed failure details. Crafting updates stay in the Nexus instead of posting chat messages.

Search supports item names and IDs, `@mod` namespaces, and `#tag` identifiers. Click search to focus it. Right-click inside clears it; clicking outside removes focus. `Ctrl+A` selects its text. Once search loses focus, normal inventory keybinds work again.

Network blocks orient to the supporting floor, wall, or ceiling, including upgraded models. Cosmetic flights round relay corners within half a block of each relay center instead of passing through every center.

## Link crystals

Relays, Storage Nexuses, and Storage Crystals automatically connect to all in-range crystals of those roles on the same channel. A Nexus also acts as a relay. Multiple connections provide alternate paths. Use different dye channels to keep nearby networks separate. The wand still pairs dimensional bridges and optional Power Nodes.

Both endpoints must be loaded and use the same dye channel. Ordinary links reach 16 blocks by default. Use a dye on a crystal to change its channel; a water bucket restores neutral. These items are not consumed. Neutral connects only to neutral.

Connected crystals discover inventories within their coverage. The nearest covering crystal owns an ordinary discovered block, with priority and address breaking ties.

Changing a network's links or inventory coverage can cancel its active crafting requests and return recoverable ingredients. Separate, unaffected networks keep their active jobs.

## What each crystal does

The Field Guide's **Network Crystals** category covers four block roles. Ranges and capacities below are defaults.

| Block | Behavior |
| --- | --- |
| Storage Nexus | Opens shared storage, manual crafting, and taught crafting requests. Several linked Nexuses share one network. No internal storage. |
| Relay Crystal | Adds a link endpoint and five-block discovery coverage. A Range Attunement enables long links; a subsequent Dimensional Attunement enables dimensional pairing with another upgraded Relay. |
| Storage Crystal | Stores items, fluids, and FE. Apply Moon then Star Attunements to increase item capacity in place. Contents survive upgrades and breaking. |
| Power Node | Draws configured upkeep resources from an attached external supply when network power is enabled. Power is off by default. It is not a generator. |

Every linked crystal provides discovery coverage. Opening a Storage Crystal shows only its own items. Deposits, withdrawals, grid clearing, and manual crafting refill use that crystal alone. Use a Storage Nexus for combined network storage and autocrafting. Relays do not load chunks. The retired Collection, Routing, Distribution, Buffer, Remote, Gateway, Moon, and Star block IDs are removed. Moon/Star storage and ranged/dimensional Relays remain upgrades on the active blocks.

## Make and place runes

Right-click the wand in air to open your personal library. Shift-right-click a Push/Pull rune, container, or crystal to select a binding endpoint, even with a placement preset selected. The library shows existing presets and one **+** tile. Left-click a preset to select it, right-click to edit it, or click **+** to create one. New config folders ship **Push**, **Pull**, and **Filter** defaults. The editor has **Duplicate** and **Delete preset** controls. All wands use the same personal library.

The Artwork tab edits a pixel canvas (32 by 32 by default). Drag to paint; right-drag erases. Choose a swatch or open **Color** for a hue/saturation wheel, brightness slider, and exact six-digit hex input. Artwork retains full RGB colors; older indexed designs still load. Placed artwork is emissive. Ctrl+Z undoes a canvas stroke. Search for an item and click its icon to add a separate layer. Drag that layer to move it, scroll to rotate, or Shift-scroll to resize. The numeric fields also set position, size, and rotation. Up to eight item layers are saved separately from the paint.

The mode button cycles Push/Pull/Filter in either editor tab. The Rules tab edits filters, reserve, stock limit, priority, and enabled state. Changes save automatically. Select **Use selected rune**, then click a container face to place a copy at that point. If occupied, it chooses the closest free position. A face holds up to eight runes by default, provided their configured squares fit. The server setting `maxRunesPerFace` controls the limit. Changing or deleting a preset does not alter placed copies. Runework uses no rune items.

Presets are saved in your client instance under `astral_repository/runes/<player-UUID>/`. Pack defaults come from `config/astral_repository/rune_presets/`; see [Configuration](configuration.md#rune-presets-and-placement). Reducing the canvas resolution crops larger designs without deleting hidden pixels.

## Move items with Push and Pull Runes

Push moves from its host container to its target. Pull moves from its target into its host. Neither requires a rune at the other end.

1. Place a Push rune on the source or a Pull rune on the destination.
2. Shift-right-click the placed glyph with the wand. The wand glints while selecting targets. You may keep Shift held or release it before clicking a target.
3. Click each source/destination container's intended face. Click an assigned face again to remove it. Click the selected glyph normally to finish.

Click a network crystal as the other endpoint to address that crystal's whole network. The host's own inventory is excluded from that network endpoint. Network-level filters, stock limits, and enabled power costs still apply. Nearby container-to-container transfers need no Nexus or Relay.

Each rune retains up to 32 targets and visits them in rotation. Both assigned endpoints must be in the same dimension. `wandBindingRange` defaults to 8 blocks: it limits a direct transfer and each endpoint's distance to its first or last relay. Beyond that distance, transfers need a loaded route of same-channel crystals. Routing chooses the shortest total travel distance, including endpoint legs. Missing routes pause transfers until restored; assignments remain saved. Selection outlines the glyph and gives the wand an enchantment glint. While that wand is selected in the hotbar, beams show assigned routes and preview the aimed container along the same relay paths. Their color averages the visible painted pixels of the rune; transparent and cropped pixels are excluded. Matching particles emit from the selected glyph only while it has no assigned targets. Shift-right-click air or any non-container block to stop binding without clearing assigned targets.

Each glyph has its own targets, filters, stock limits, priority, and enabled state. Opposite faces are independent. Click gaps between glyphs to use the container normally. Transfers honor sided item/fluid capabilities; runes do not transfer FE or Source.

### Rune settings

Right-click a placed glyph with any item or an empty hand to open its settings. Opening the interface does not change its filters or consume the held item. Even a wand set to place a preset opens an existing glyph. Shift-right-click with the wand selects that rune's targets instead.

Shift-click an inventory item or drop an EMI/JEI ingredient onto the translucent filter area. Ordinary clicks pick up and place items; clicking the filter area with a carried stack copies its filter without consuming the stack. Filled containers select their fluid without consuming the container. **Clear filter** removes its rules and match modes while preserving targets and stock limits. An empty filter accepts all items and fluids.

**Shift-left-click** a glyph with anything to remove just that rune. The host container remains intact, and inventory space is irrelevant. Hold-clicking removes one glyph; release before removing another.

### Advanced settings

The panel edits only the selected rune. Placement and linking remain in the world. **Enabled / Disabled** controls that rune without discarding its assignment.

| Setting | Behavior |
| --- | --- |
| **Push / Pull / Filter** | Left-click or scroll forward; right-click cycles backward |
| **Whitelist / Blacklist** | Accept or reject any matching positive rule |
| **Keep at source** | Retain this many of each resource at the source |
| **Stop at target** | Stop at this many of each resource at the destination; -1 means unlimited |
| **Priority** | Higher values run first within a scheduled batch; range −999 to 999 |

The player inventory sits below the controls. Clicking an item copies its identity; it does not pick up or consume the stack. BNS supplies its scrolling inventory and sidebar when installed, with the same astral background used by the Nexus. EMI and JEI can drop item or fluid ingredients into the grid. Their integration is optional.

Filters loaded from presets may also contain tags, namespaces, and per-rule exclusions. These remain supported; the editor has no text-search picker.

Right-click a filter icon to remove it. Left-click a captured item filter to toggle exact components, such as its custom name or enchantments. An **=** marker identifies exact matching. Adding from inventory captures that stack; an EMI/JEI item rule captures a matching held item when available. If no sample was captured, hold a matching item when enabling exact matching.

IDs and tags must exist on the server. Each filter accepts up to 64 rules. Exclusions always veto a match, including in blacklist mode. An item-only whitelist does not admit fluids, and a fluid-only whitelist does not admit items.

Valid changes save automatically; number fields save after a short typing pause. **Done** and Escape save pending valid values before closing. Invalid numbers retain their last accepted values. Priority sits beneath the icon grid. Right-click individual filters to remove them. **Unlink** clears assignments. **Save Preset** copies the placed rune's function, full painted artwork, and item overlays into a new preset and opens its cosmetic editor. Removal stays in-world with Shift-left-click.

**Cadence** opens separate amount and tick-interval fields for the resources exposed by the attached container face: items, fluid mB, energy FE, and Source. A combined container can show all four rows. Zero amount disables that resource; intervals start at one tick. Each row requests up to that amount per interval, subject to available stock and destination acceptance. **Server defaults** removes local overrides. Presets keep these settings.

Scroll any numeric field by 1 per wheel step, or hold Shift for 10, Ctrl for 100, and both for 1,000. This includes the Nexus craft quantity. Fields clamp to their supported ranges.

Counts cannot be negative. Fluid quantities use millibuckets; 1,000 mB is one bucket. For example, put a Pull Rune on a fuel chest, link it to your coal store, add a coal filter, and set **Stop at target** to 64. Set **Keep at source** to 32 to leave that much coal at the source. Both amounts apply to each matching resource identity. At -1, scrolling down keeps the stock limit unlimited; scrolling up changes it to 0, then 1.

Removing the last glyph removes the face inscription. Breaking or replacing the host container removes its runes and assignments. Chunk unload suspends transfers and preserves their saved settings. Older shared inscriptions load as independent, unlinked Push/Pull layers; assign their targets again with the wand. Rune item IDs are removed; placed rune designs remain saved independently.

Older saves may contain inactive archived layers. Removing a visible rune restores the next archived layer, disabled, when the configured face limit permits it. Its target and settings remain intact. Existing active runes remain present if the server lowers its face limit.

## Filter runes

A **Filter** rune needs no target. It controls what Astral may insert into its host container and supplies that container's destination priority. It does not restrict extraction or ordinary manual container access. Multiple whitelist runes accept any matching rule; blacklists veto matches. The highest enabled Filter priority applies. Disabling the rune removes its policy immediately.

## Dense storage

Astral Storage Crystals store component-distinct items by capacity rather than fixed slot count. They also expose a fluid tank and FE buffer. Place them within automatic connection range of the network. Their own storage is available through the connected component.

| Crystal | Default item capacity units |
| --- | ---: |
| Seed | 16,384 |
| Moon | 262,144 |
| Star | 4,194,304 |

Native item cost is `64 / maximum stack size`. A stack of 64 ordinary items costs 64 units; one unstackable tool also costs 64 units. Fractional costs are summed exactly and rounded only for the displayed total. When its optional integration is enabled, Stacks Not Slots supplies its public exact capacity semantics.

Breaking a storage crystal retains items, fluids, FE, channels, and its stored settings on the dropped block item. Automatic links reform after placing it within range of the network. Filled crystals cannot be nested inside Astral storage. Apply a Moon Attunement to a Seed-stage Storage Crystal, then a Star Attunement to reach the next stage. Successful upgrades consume the attunement in survival and preserve stored contents.

## Remote access and bridges

The handheld Astral Nexus is a simple crystal sphere rendered as a flat item with filled edges. Use it on a Storage Nexus to bind it. Use it in air to access that Nexus within the same dimension, up to the default distance of 4,096 blocks. The bound Nexus and any providers used by an operation must be loaded.

For dimensional handheld access, hold a Dimensional Attunement and an Astral Nexus in opposite hands and use either one. A successful upgrade consumes one attunement in survival and remains on that orb. Already-upgraded or ineligible targets consume none. It permits access from other dimensions; the same-dimension 4,096-block range still applies.

For longer links, apply a Range Attunement to both Relays. They automatically connect within `remoteRange` (4,096 blocks by default) when their channels match. Ordinary crystal links use `relayRange` (16 blocks). `wandBindingRange` controls rune endpoint reach separately.

Dimensional bridges use the same Dimensional Attunement item. Apply one to each range-upgraded Relay, then pair them with the wand. Each successful survival application consumes one attunement; endpoints can retain multiple explicit edges.

Remote connections do not force-load chunks. An unloaded endpoint stays unavailable until its chunks load normally.

## Teach final products

1. Right-click a Recipe Tome to open its recipe editor.
2. Shift-click an output in the inventory below the book, or drag it from EMI/JEI onto the page. Review the recipe and press **Inscribe**. Arrows cycle recipes for the same output.
3. Insert the tome into a vanilla Chiseled Bookshelf inside network coverage.
4. Open the Storage Nexus. Right-click an empty craftable entry for one item, Shift-right-click for a stack, or middle-click it to enter a quantity and press the hammer button.

Bookshelves expose their taught products as autocrafting choices. Connected external crafting providers may also expose their own products. Intermediate recipes are resolved automatically. Place ordinary crafting tables, furnaces, blast furnaces, smokers, and stonecutters within coverage to supply their corresponding processes. Workstations reserve their place while ingredients travel. Tables display each arriving batch and begin assembly after the last arrival. Furnace inputs and fuel enter their real slots only when their flights finish. Furnaces use real processing and require available fuel. Additional workstations provide parallel capacity. Missing resources or processors are reported when a request cannot be planned.

Visual Workbench tables also provide crafting capacity when that mod is installed. Autocrafting uses ingredients taken from network storage and leaves the table's saved manual grid and preview unchanged. Its player ingredients are not indexed as network stock, withdrawn by the Nexus, or filled by network deposits.

## Inspection and upkeep

Look directly at an inscribed container face to see its runes without equipment. Holding a wand reveals nearby inscriptions while building. Astral Goggles reveal nearby runes continuously and add crystal-network and processor diagnostics.

Hold Shift while aiming at a glyph to inspect its icons and amounts. Jade shows this compact rune row when installed; otherwise the built-in overlay renders the same icons. Releasing Shift hides rune inspection. The first icon row shows transfer direction and state; the next shows filter samples. Red slashes mark exclusions and a cyan equals mark means exact item data. Any final row shows reserve, stock-limit, or priority amounts. Use the advanced settings panel for full filter names, target details, and editable values.

Network upkeep is disabled by default. Pack authors can enable it in `config/astral_repository-common.toml` and attach a Power Node to a compatible supply. These charges apply to crystal networks; independent Push/Pull transfers use their own bounded scheduler.

Player and automatic timing are separate common settings. `instantPlayerInteractions` defaults to `true`. `instantAutomaticLogistics` defaults to `false`, so crystal-network transfers run every 20 server ticks and Astral-managed work keeps its delivery and processing delays. Rune transfer intervals come from Cadence, defaulting to 20 ticks. Enabling instant automatic logistics runs transfers every tick and removes those managed delays; runes without custom intervals also use one tick; quantity budgets and power charges still apply. Physical furnaces, Create machines, and external crafting backends keep their own processing time. Transfer animations remain visible regardless of transaction timing. Each flight travels through its route in order, taking 20–100 ticks per hop based on distance.

Optional storage and power adapters are controlled by `config/astral_repository-compat.toml`. See the [configuration reference](configuration.md) and [integration boundaries](compatibility.md).


Crafting ingredient flights start at the supplying storage; intermediate products start at their processor. Outputs travel from the processor back to storage. Each transfer takes a direct route when in range, otherwise the shortest loaded relay route. The Nexus is only used as a hop when that route needs it.

Transfers need clear sight along each leg by default. Glass and other transparent blocks pass the connection; opaque shapes block it. Relays can route around an obstruction when a visible path exists. Nexus and crafting legs use `relayRange` (16 blocks by default); rune legs use `wandBindingRange` (8). Upgraded Relay links use `remoteRange`.

## Share a placed rune

Open the placed rune and choose Save Preset. The new preset includes its full painted texture, item overlay transforms, and behavior settings. Share its JSON file from `<instance>/astral_repository/runes/<player-UUID>/`; pack authors can distribute it through `config/astral_repository/rune_presets/`. It needs no separate texture file. Editing the new preset does not change the placed rune.
