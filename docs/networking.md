# Nexus synchronization



The server owns item transfers, recipe execution, and cancellation. The client sends an item identity and an action; it cannot supply a trusted stored count, output stack limit, or another player's job ownership.



The Nexus uses a nine-column, six-row window. Scrolling advances rows; there are no page controls. The server caches sorted and filtered identities and takes at most 54 entries directly from that list. Count changes preserve ordering. Storage-index or recipe-library changes invalidate the relevant cache; elapsed time alone does not.



A full update establishes the visible item identities. Subsequent updates send only changed counts, crafting flags, job progress, job order, or error state. Known job targets are reused by UUID. An idle menu sends no catalog packet. Multiple search/scroll requests before the next broadcast are coalesced.



Two counters serve different purposes:



- A menu revision identifies the exact full/delta baseline. A delta for another revision is rejected.

- A client request number acknowledges search/scroll input. An older response can update the protocol baseline without moving the scrollbar back over newer input.



Current jobs are limited to 48 active requests per player per network plus 16 brief terminal records. Cancellation checks both the active menu and the job owner. Spectators cannot mutate storage through custom packets.



## Upstream review



[AE2's incremental update helper](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/fd8b717a405672ce4f65ba540f1db8c91317daa4/src/main/java/appeng/menu/me/common/IncrementalUpdateHelper.java) tracks changed resource keys and stable serial identities. [Refined Storage's pending grid updates](https://github.com/refinedmods/refinedstorage2/blob/0dec63affe8147702b020843c22748483ef18c01/refinedstorage-common/src/main/java/com/refinedmods/refinedstorage/common/grid/PendingGridUpdates.java) merges changes before sending them. Those implementations informed change tracking, coalescing, and identity reuse here. Astral Repository uses bounded visible windows rather than copying either mod's protocol.



## Transfer flights



`NetworkPackets.Visual` describes one ordered route of 2-130 block positions. The codec validates the path endpoints and a total duration of 1-13,000 ticks. `TransferVisualDispatcher` samples nearby observers along the complete route, including between waypoints, and sends one bounded `TransferVisualBatch` per recipient at the end of the tick. Moving effects have a per-player count budget; workstation updates coalesce by slot. A separate byte budget bounds encoded output and oversized item-component work. Excess cosmetic offers are discarded without delaying real transfers.

The client prepares each admitted path once and follows a time-weighted cubic Hermite spline influenced by its nodes. Adjacent segments share a velocity, avoiding per-hop stops. Optional endpoint data carries a bounded local glyph offset and face direction for outward launch and inward arrival. Items, fluids, energy, and Source share this interpolation. A local tick clock and integer age subtraction preserve frame interpolation during long sessions and server time corrections. Server-side arrival timers remain authoritative; workstation processing never depends on receipt or display of an animation. See [Transfer animation budgets](configuration.md#transfer-animation-budgets).



Rune snapshots include at most six target icons plus the total target count. Artwork remains separately synchronized.

