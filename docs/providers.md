# Provider API

The extension API is under `com.cappleapple.astralrepository.api`. Registration is in `com.cappleapple.astralrepository.compat.CompatibilityRegistry`. Providers run on the server thread. They must not load chunks or access the world from worker threads.

## Storage

`StorageProvider` represents a live item store. `ItemKey` includes the item and all data components, ignores count, and returns defensive copies from `sample()`.

| Method | Contract |
| --- | --- |
| `id()` | Diagnostic identifier for this endpoint. |
| `identity()` | Shared backing-store identity used for deduplication. Return the same value for multiple views of one digital network. |
| `snapshot()` | Immutable map from item identity to nonnegative stored count. Counts are advisory until extraction succeeds. |
| `poll(int workBudget)` | Incremental reconciliation. Return `Optional.empty()` while scanning; publish a complete replacement snapshot when finished. The default calls `snapshot()`. |
| `insert(ItemStack, boolean)` | Return the remainder. Do not mutate the input stack. Simulation must not change storage. |
| `extract(ItemKey, int, boolean)` | Return actual extracted items, at most the request. Simulation must not change storage. |
| `capacity()` | Nominal capacity, or `-1` when no meaningful aggregate capacity is available. |
| `valid()` | False when removed, unloaded, disconnected, or quarantined. |
| `version()` | Monotonic dirty version, or `-1` to use reconciliation polling. |

Provider extraction amounts may span multiple ordinary stacks. The returned `ItemStack` is an internal quantity carrier and may exceed its item's maximum stack size. Consumers must split it into legal stack sizes before placing it in a real inventory or sending it through a stack-limited packet codec. The built-in item-handler provider examines at most 512 slots per extraction call, so it may return fewer items than requested even when more exist elsewhere in the inventory.

Call `CompatibilityRegistry.registerStorage(id, factory)` during common setup. A factory receives a `ServerLevel`, `BlockPos`, and nullable `Direction`; return an empty list for unsupported blocks. The direction is the machine face selected by an attached endpoint. Keep sided insertion and extraction restrictions intact.

The built-in `IItemHandler` adapter scans at most the requested number of slots per `poll` call. A completed pass is a best-effort view over that interval, not an atomic inventory snapshot: another machine may change slots during the pass. Every real transfer therefore checks the live handler again. Dense Astral storage restarts an incomplete pass when its inventory revision changes. Capability invalidation makes item, fluid, and FE providers unavailable until rediscovery. Double-chest views use a canonical pair of positions, preventing both halves from being counted twice.

`candidate(Predicate<ItemStack>)` is an optional live identity hint for a single-provider rune transfer with no source reserve or destination stock target. The default returns `null`. The built-in item handler checks at most 32 live slots per call. It scans eight rotating slots for new contents, then rotates through known occupied slots; if no useful hint exists it spends the remaining budget on discovery. Newly inserted contents remain discoverable during continuous transfers from known stacks. A hint carries no count or extraction permission: the caller still simulates and validates both sides before committing. A missing or rejected hint falls back to bounded polling. The predicate must not mutate the stack, and the returned `ItemKey` must retain its components independently of later inventory changes. Like every provider operation, this method runs on the server thread.

Capability backends with external change notifications can maintain their own cached index. The current network reconciler calls `poll`; it does not schedule reads from `version()` automatically. A digital provider's default `poll` can still enumerate every distinct resource in one call. Integrations with very large digital stores should override it or use their native indexed change stream.

## Crystal and surface endpoints

Storage factories receive the capability face chosen by the network owner. An ordinary nearby crystal uses an unsided view. A physically attached crystal uses the adjacent block and the face pointing back toward the crystal. A rune uses its host block and the outward face where it was placed. Preserve this distinction when querying capabilities; do not replace a requested face with an unsided handler.

The internal [`NetworkAnchor`](../src/main/java/com/cappleapple/astralrepository/network/NetworkAnchor.java) contract separates an endpoint's graph address, provider position and side, coverage behavior, and rules. [`CrystalNodeBlockEntity`](../src/main/java/com/cappleapple/astralrepository/content/CrystalNodeBlockEntity.java) and [`RuneSurface`](../src/main/java/com/cappleapple/astralrepository/content/RuneSurface.java) implement it. [`AnchorAddress`](../src/main/java/com/cappleapple/astralrepository/network/AnchorAddress.java) includes a face for rune endpoints, so opposite sides of one machine can belong to different explicit components.

`RuneSurfaces` and `RuneSavedData` manage persisted glyph layers and links. Layer filter or target changes must mark saved data dirty and notify observers through `RuneLayer.changed()`; changes to graph eligibility must also invalidate topology. World reads and all mutations remain on the server thread. `NetworkAnchor` is an internal integration boundary, not a registration API for third-party anchor types: the current manager discovers crystals and stored rune surfaces explicitly.

Direct Push/Pull assignments use the same storage and fluid factories for the layer's host face and target face, independently of crystal-network membership. A [RuneLayer](../src/main/java/com/cappleapple/astralrepository/content/RuneLayer.java) owns its directed target and rules; [DirectRuneTransfers](../src/main/java/com/cappleapple/astralrepository/network/DirectRuneTransfers.java) owns bounded live transfers. Implementations must preserve sided access and stable backing identities so aliases of the same store cannot transfer into themselves. Current direct runes support item, fluid, FE, and Source providers.

Provider identity deduplication applies within each component. Two independent networks can use different sides of one backing inventory, so their cached snapshots are advisory views of shared physical state. Every extraction and insertion must still honor the actual handler result. See [explicit topology and endpoint ownership](architecture.md#explicit-topology-and-endpoint-ownership).

## Other resources

`ResourceKey` supplies a resource-type ID and identity ID. Include any components that affect interchangeability in `equals` and `hashCode`. The interface is open to additional resource systems.

`ResourceProvider` uses native units: items, millibuckets, FE, or Source. Its `insert` and `extract` methods return the accepted or extracted quantity, rather than a remainder. Amounts are nonnegative `long` values. The fluid and FE adapters bound each capability call to the capability's integer amount range.

Built-in type IDs are `astral_repository:item`, `astral_repository:fluid`, `astral_repository:energy`, `astral_repository:source`, and `astral_repository:stress`. Source uses its own `ars_nouveau:source` key and is never converted to FE. `FluidKey` preserves fluid data components. `unit()` and `visualization()` describe presentation without controlling transaction outcomes.

Register additional resource factories through `registerResources`. Discovery checks chunk availability first and isolates adapter exceptions by adapter and location.

## Crafting

`CraftingProvider` exposes the backend's craftable output identities and submits production requests. A returned `Ticket` owns a real backend task; `state()` polls it, `message()` describes its status, and `cancel()` requests cancellation. `COMPLETE` is a backend completion signal. Consumers must still obtain the output through storage and must not create an item merely because a ticket completed.

`CraftingContext` carries an immutable request UUID, visited-provider set, and depth limit. Each bridge must call `enter` with a stable provider-network identifier and pass the resulting context to the next bridge. Revisiting an identifier or exceeding the depth limit rejects the request. A bridge that discards the context cannot provide cross-system cycle detection.

Register crafting factories through `registerCrafting`. Cancel outstanding tickets when their network shuts down, their integration point unloads, or the server stops. Built-in AE2 and Refined Storage tickets are session-local; they are not persisted proxies for those mods' crafting systems. The mod does not advertise Astral recipes back into either digital network.

Machine processes used by the dependency planner have a separate [processing adapter contract](crafting.md). Storage providers must not infer production from insertion or create recipe outputs themselves.

## Network power

`NetworkPowerProvider` distinguishes `CONSUMABLE` fuel from `CAPACITY` requirements. For consumables, `acquire` spends resource units. For capacity, it obtains a temporary lease against available capacity and must be paired with `release`. Simulation changes neither resources nor leases. Release leases on cancellation, disconnect, and every path that ends their operating interval.

Create stress uses spare live capacity minus other Astral leases for that kinetic network. It is an operating requirement; Astral leases do not modify Create's own stress network or represent stored FE. Additional power adapters register through `registerPower`.

## Failure handling

Discovery retries an adapter/location after a 1,200-tick quarantine. Provider reads that throw quarantine the discovered provider and return an unavailable result. Simulation failures cannot authorize a commit.

A provider that throws during a real mutation has an unknown outcome. `ProviderFailure` preserves this distinction. Callers must stop that transfer and avoid retrying or refunding an amount whose extraction or insertion is uncertain. Only amounts confirmed by a successful return can be safely refunded. No wrapper can make a third-party handler atomic after it has mutated storage and then thrown.

Normal partial insertion is not an exception: return the actual remainder, which the caller retains or refunds. Cosmetic transfers must be emitted only from confirmed server-side work.
