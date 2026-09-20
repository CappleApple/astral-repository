package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.api.StorageProvider;
import com.cappleapple.astralrepository.content.CapacityInventory;
import java.util.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.function.BooleanSupplier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** Sided capability adapter with incremental snapshots and verified, bounded slot lookups. */
public final class ItemHandlerStorageProvider implements StorageProvider {
    private static final int LOOKUP_BUDGET=512;
    private final String id;
    private final Object identity;
    private final IItemHandler handler;
    private final BooleanSupplier valid;
    private int scanCursor,scanSlots=-1,insertionCursor,extractionCursor,candidateCursor,knownCandidateCursor;
    private int preferredInsertionSlot=-1;
    private ItemKey lastInsertionKey;
    private long scanVersion=-1,partialCapacity,knownCapacity=-1;
    private Map<ItemKey,Long> partial=new LinkedHashMap<>();
    private Map<ItemKey,IntLinkedOpenHashSet> partialLocations=new HashMap<>(),locations=new HashMap<>();
    private Int2ObjectOpenHashMap<ItemKey> partialKeys=new Int2ObjectOpenHashMap<>(2),slotKeys=new Int2ObjectOpenHashMap<>(2);
    private BitSet partialEmpty=new BitSet(),emptySlots=new BitSet(),partialOccupied=new BitSet(),occupiedSlots=new BitSet();
    public ItemHandlerStorageProvider(String id,Object identity,IItemHandler handler,BooleanSupplier valid) {
        this.id=id; this.identity=identity; this.handler=handler; this.valid=valid;
    }
    public String id() { return id; }
    public Object identity() { return identity; }
    public boolean valid() { return valid.getAsBoolean(); }
    @Override public long version() { return handler instanceof CapacityInventory dense?dense.revision():-1; }
    public Map<ItemKey,Long> snapshot() {
        resetScan();
        if(!valid()) return Map.of();
        // Explicit full snapshot remains available; the network itself uses bounded poll().
        return poll(Math.max(1,handler.getSlots())).orElseThrow();
    }
    public Optional<Map<ItemKey,Long>> poll(int workBudget) {
        if(!valid()) { resetScan(); return Optional.of(Map.of()); }
        int slots=handler.getSlots(); long currentVersion=version();
        if(scanSlots!=slots || (currentVersion>=0&&scanVersion!=currentVersion)) resetScan();
        if(scanSlots==-1) { scanSlots=slots; scanVersion=currentVersion; }
        int end=(int)Math.min(slots,(long)scanCursor+Math.max(1,workBudget));
        while(scanCursor<end) {
            int slot=scanCursor++;
            partialCapacity+=handler.getSlotLimit(slot);
            ItemStack stack=handler.getStackInSlot(slot);
            if(stack.isEmpty()) partialEmpty.set(slot);
            else {
                ItemKey cached=slotKeys.get(slot);
                ItemKey key=cached!=null&&cached.matches(stack)?cached:new ItemKey(stack);
                partial.merge(key,(long)stack.getCount(),Math::addExact);
                partialLocations.computeIfAbsent(key,ignored -> new IntLinkedOpenHashSet(2)).add(slot);
                partialKeys.put(slot,key);partialOccupied.set(slot);
            }
        }
        if(scanCursor<slots) return Optional.empty();
        Map<ItemKey,Long> complete=Map.copyOf(partial);
        knownCapacity=partialCapacity;
        // Swap live indexes with cleared scan scratch rather than allocate several maps per transfer.
        var oldLocations=locations;locations=partialLocations;partialLocations=oldLocations;
        var oldKeys=slotKeys;slotKeys=partialKeys;partialKeys=oldKeys;
        var oldEmpty=emptySlots;emptySlots=partialEmpty;partialEmpty=oldEmpty;
        var oldOccupied=occupiedSlots;occupiedSlots=partialOccupied;partialOccupied=oldOccupied;
        resetScan();
        return Optional.of(complete);
    }
    private void resetScan() {
        if(scanSlots==-1)return;
        scanCursor=0; scanSlots=-1; scanVersion=-1; partialCapacity=0;
        partial.clear(); partialLocations.clear(); partialKeys.clear(); partialEmpty.clear(); partialOccupied.clear();
    }
    private ItemStack observe(int slot) {
        ItemStack stack=handler.getStackInSlot(slot);
        // Discovery still reads the live handler, but unchanged empty slots need no index repair.
        if(stack.isEmpty()&&emptySlots.get(slot))return stack;
        occupiedSlots.set(slot,!stack.isEmpty());
        ItemKey before=slotKeys.get(slot);
        if(before!=null&&before.matches(stack)){
            // Keep rejected candidates rotating; successful simulation moves its slot to the front.
            var matching=locations.get(before);if(matching!=null)matching.addAndMoveToLast(slot);
            return stack;
        }
        if(before!=null) {
            slotKeys.remove(slot);
            IntLinkedOpenHashSet old=locations.get(before);
            if(old!=null) { old.remove(slot); if(old.isEmpty()) locations.remove(before); }
        }
        if(stack.isEmpty()) emptySlots.set(slot);
        else {
            ItemKey key=new ItemKey(stack);
            slotKeys.put(slot,key); emptySlots.clear(slot);
            locations.computeIfAbsent(key,ignored -> new IntLinkedOpenHashSet(2)).add(slot);
        }
        return stack;
    }
    @Override public ItemKey candidate(java.util.function.Predicate<ItemStack> matches) {
        Objects.requireNonNull(matches);
        if(!valid())return null;
        int slots=handler.getSlots();if(slots<=0)return null;
        if(occupiedSlots.length()>slots)occupiedSlots.clear(slots,occupiedSlots.length());
        int budget=32,reads=0,discovered=0;
        // Independent discovery continues even while a known stack transfers forever.
        // Inspect eight physical slots per call; sparse inventories then reuse their occupied index.
        for(;discovered<Math.min(8,slots);discovered++){
            observe(Math.floorMod(candidateCursor++,slots));reads++;
        }
        int start=Math.floorMod(knownCandidateCursor,slots),cursor=start;boolean wrapped=false;
        while(reads<budget){
            int slot=occupiedSlots.nextSetBit(cursor);
            if(slot<0||slot>=slots){if(wrapped)break;wrapped=true;slot=occupiedSlots.nextSetBit(0);}
            if(slot<0||slot>=slots||(wrapped&&slot>=start))break;
            cursor=slot+1;knownCandidateCursor=cursor;
            ItemStack stack=observe(slot);reads++;
            if(!stack.isEmpty()&&matches.test(stack))return slotKeys.get(slot);
        }
        // No useful occupied hint: spend the rest of the same budget advancing discovery.
        while(reads<budget&&discovered++<slots){
            int slot=Math.floorMod(candidateCursor++,slots);ItemStack stack=observe(slot);reads++;
            if(!stack.isEmpty()&&matches.test(stack)){knownCandidateCursor=slot+1;return slotKeys.get(slot);}
        }
        return null;
    }
    private static final class LookupScratch {
        final IntArrayList candidates=new IntArrayList(8);
        final IntOpenHashSet visited=new IntOpenHashSet(8);
        void clear(){candidates.clear();visited.clear();}
    }
    private LookupScratch lookupScratch;
    private boolean lookupBorrowed;
    private LookupScratch borrowLookup(){
        // Handler callbacks can reenter this provider; nested calls must not overwrite the outer lookup.
        if(lookupBorrowed)return new LookupScratch();
        if(lookupScratch==null)lookupScratch=new LookupScratch();
        lookupBorrowed=true;return lookupScratch;
    }
    private void releaseLookup(LookupScratch scratch){
        scratch.clear();if(scratch==lookupScratch)lookupBorrowed=false;
    }
    private void candidates(ItemKey key,int limit,IntArrayList result) {
        var known=locations.get(key);if(known==null||known.isEmpty())return;
        int count=Math.min(limit,known.size());
        if(count==1){result.add(known.firstInt());return;}
        var iterator=known.iterator();while(result.size()<count)result.add(iterator.nextInt());
    }
    public ItemStack insert(ItemStack stack,boolean simulate) {
        ItemStack remainder=stack.copy();
        if(!valid()||remainder.isEmpty()) return remainder;
        if(preferredInsertionSlot>=0) { insertionCursor=preferredInsertionSlot; preferredInsertionSlot=-1; }
        if(!simulate) resetScan();
        ItemKey key=lastInsertionKey;
        if(key==null||!key.matches(stack))lastInsertionKey=key=new ItemKey(stack);
        LookupScratch scratch=borrowLookup();
        try {
            IntOpenHashSet visited=scratch.visited;
            IntArrayList candidates=scratch.candidates;
            int slots=handler.getSlots();
            // Reserve half of each lookup budget for empty/new slots, even with many full matching stacks.
            candidates(key,LOOKUP_BUDGET/2,candidates);
            for(int i=0;i<candidates.size();i++) {
                int slot=candidates.getInt(i);
                if(slot<0||slot>=slots||!visited.add(slot)) continue;
                ItemStack current=observe(slot);
                if(!current.isEmpty()&&key.matches(current)) remainder=offer(slot,remainder,simulate,key);
                if(remainder.isEmpty()) return remainder;
            }
            if(handler instanceof CapacityInventory&&slots>0&&visited.add(slots-1)) {
                observe(slots-1); remainder=offer(slots-1,remainder,simulate,key);
                if(remainder.isEmpty()) return remainder;
            }
            int emptyVisits=0,cursor=slots==0?0:Math.floorMod(insertionCursor,slots);
            while(visited.size()<LOOKUP_BUDGET&&emptyVisits<slots) {
                int slot=emptySlots.nextSetBit(cursor);
                if(slot<0||slot>=slots) slot=emptySlots.nextSetBit(0);
                if(slot<0||slot>=slots) break;
                cursor=slot+1; insertionCursor=cursor; emptyVisits++;
                if(!visited.add(slot)) break;
                ItemStack current=observe(slot);
                if(current.isEmpty()||key.matches(current)) remainder=offer(slot,remainder,simulate,key);
                if(remainder.isEmpty()) return remainder;
            }
            int examined=0;
            while(slots>0&&visited.size()<LOOKUP_BUDGET&&examined++<slots) {
                int slot=Math.floorMod(insertionCursor++,slots);
                if(!visited.add(slot)) continue;
                ItemStack current=observe(slot);
                if(current.isEmpty()||key.matches(current)) remainder=offer(slot,remainder,simulate,key);
                if(remainder.isEmpty()) return remainder;
            }
            return remainder;
        } finally { releaseLookup(scratch); }
    }
    private ItemStack offer(int slot,ItemStack stack,boolean simulate,ItemKey key) {
        if(!handler.isItemValid(slot,stack)) return stack;
        ItemStack remainder=handler.insertItem(slot,stack,simulate);
        if(!remainder.isEmpty()&&!ItemStack.isSameItemSameComponents(stack,remainder))
            throw new IllegalStateException("Item capability changed insertion remainder at "+id);
        if(remainder.getCount()>stack.getCount()) throw new IllegalStateException("Item capability grew insertion remainder at "+id);
        if(simulate&&remainder.getCount()<stack.getCount()) {
            preferredInsertionSlot=slot;
            var matching=locations.get(key);
            if(matching!=null&&matching.contains(slot)) matching.addAndMoveToFirst(slot);
        } else if(!simulate&&remainder.getCount()<stack.getCount()&&slot<handler.getSlots()&&valid()) observe(slot);
        return remainder;
    }
    public ItemStack extract(ItemKey key,int amount,boolean simulate) {
        if(!valid()||amount<=0) return ItemStack.EMPTY;
        if(!simulate) resetScan();
        int wanted=amount;
        ItemStack result=ItemStack.EMPTY;
        LookupScratch scratch=borrowLookup();
        try {
            IntArrayList candidates=scratch.candidates;
            candidates(key,LOOKUP_BUDGET,candidates);
            IntOpenHashSet visited=scratch.visited;
            int slots=handler.getSlots();
            boolean stale=false;
            for(int i=0;i<candidates.size();i++) {
                int slot=candidates.getInt(i);
                if(slot<0||slot>=slots) { stale=true; continue; }
                if(!visited.add(slot)) continue;
                ItemStack current=observe(slot);
                if(current.isEmpty()||!key.matches(current)) { stale=true; continue; }
                ItemStack extracted=take(slot,key,wanted-result.getCount(),simulate);
                if(!extracted.isEmpty()) { if(result.isEmpty()) result=extracted; else result.grow(extracted.getCount()); }
                if(result.getCount()>=wanted) return result;
            }
            // Compacting virtual inventories tend to shift nearby slots. Repair locally before later rotating scans.
            if(stale&&!candidates.isEmpty()) extractionCursor=Math.max(0,candidates.getInt(0)-LOOKUP_BUDGET/2);
            int examined=0;
            while(slots>0&&visited.size()<LOOKUP_BUDGET&&examined++<slots&&result.getCount()<wanted) {
                int slot=Math.floorMod(extractionCursor++,slots);
                if(!visited.add(slot)) continue;
                ItemStack current=observe(slot);
                if(current.isEmpty()||!key.matches(current)) continue;
                ItemStack extracted=take(slot,key,wanted-result.getCount(),simulate);
                if(!extracted.isEmpty()) { if(result.isEmpty()) result=extracted; else result.grow(extracted.getCount()); }
            }
            return result;
        } finally { releaseLookup(scratch); }
    }
    private ItemStack take(int slot,ItemKey key,int amount,boolean simulate) {
        ItemStack extracted=handler.extractItem(slot,amount,simulate);
        if(extracted.isEmpty()) return ItemStack.EMPTY;
        if(!key.matches(extracted)||extracted.getCount()>amount)
            throw new IllegalStateException("Item capability violated extraction contract at "+id);
        // The immediately following commit must revisit slots that passed simulated extraction.
        if(simulate) locations.computeIfAbsent(key,ignored -> new IntLinkedOpenHashSet(2)).addAndMoveToFirst(slot);
        else if(slot<handler.getSlots()&&valid()) observe(slot);
        return extracted.copy();
    }
    public long capacity() { return handler instanceof CapacityInventory dense?dense.capacity():knownCapacity; }
}
