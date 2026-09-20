package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.api.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.cappleapple.astralrepository.platform.energy.EnergyStorage;
import com.cappleapple.astralrepository.platform.items.IItemHandler;
import com.cappleapple.astralrepository.platform.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityProviderTest {
    @BeforeAll static void initializeMinecraft() { com.cappleapple.astralrepository.MinecraftTestBootstrap.initialize(); }
    @Test void immutableItemKeysKeepComponentsButIgnoreCount() {
        ItemStack stack=new ItemStack(Items.IRON_INGOT,32);
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Attuned iron"));
        ItemKey key=new ItemKey(stack);
        ItemStack same=stack.copyWithCount(1);
        assertEquals(key,new ItemKey(same));
        assertTrue(key.matches(same));
        assertFalse(key.matches(ItemStack.EMPTY));
        assertEquals(key.hashCode(),new ItemKey(same).hashCode());
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Changed"));
        assertNotEquals(key,new ItemKey(stack));
        assertFalse(key.matches(stack));
        ItemStack sample=key.sample(); sample.set(DataComponents.CUSTOM_NAME,Component.literal("Altered sample"));
        assertEquals(key,new ItemKey(same));
    }
    @Test void reusedSlotKeysStillNoticeLiveComponentEditsAndCountChanges() {
        var handler=new ItemStackHandler(2);var stack=new ItemStack(Items.IRON_INGOT,32);
        handler.setStackInSlot(0,stack);
        var provider=new ItemHandlerStorageProvider("live-components",handler,handler,()->true);
        var plain=new ItemKey(stack);assertEquals(32,provider.snapshot().get(plain));
        stack.setCount(16);assertEquals(16,provider.snapshot().get(plain));
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Changed in place"));
        var renamed=new ItemKey(stack);var contents=provider.snapshot();
        assertFalse(contents.containsKey(plain));assertEquals(16,contents.get(renamed));
        assertTrue(provider.extract(plain,8,false).isEmpty());
        assertEquals(8,provider.extract(renamed,8,false).getCount());
        assertEquals(8,provider.snapshot().get(renamed));
    }
    @Test void itemSimulationLeavesBackendAndInputUntouched() {
        ItemStackHandler handler=new ItemStackHandler(2);
        handler.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,60));
        var provider=new ItemHandlerStorageProvider("test",handler,handler,() -> true);
        ItemStack input=new ItemStack(Items.IRON_INGOT,32);
        assertTrue(provider.insert(input,true).isEmpty());
        assertEquals(60,handler.getStackInSlot(0).getCount());
        assertEquals(32,input.getCount());
        assertTrue(provider.insert(input,false).isEmpty());
        assertEquals(92,provider.snapshot().get(new ItemKey(input)));
        assertEquals(32,provider.extract(new ItemKey(input),32,true).getCount());
        assertEquals(92,provider.snapshot().get(new ItemKey(input)));
        assertEquals(32,provider.extract(new ItemKey(input),32,false).getCount());
        assertEquals(60,provider.snapshot().get(new ItemKey(input)));
    }
    @Test void insertionGuardFailureReturnsOwnedRemaindersAndKeepsUncertainCommitsVisible() {
        ItemStack offered=new ItemStack(Items.IRON_INGOT,12);
        offered.set(DataComponents.CUSTOM_NAME,Component.literal("Caller-owned stack"));
        var reads=new java.util.concurrent.atomic.AtomicInteger();
        var simulationBackend=new ItemStackHandler(1){
            @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){reads.incrementAndGet();throw new IllegalArgumentException("Simulated insertion failed");}
        };
        var guarded=ProviderGuard.storage(new ItemHandlerStorageProvider("guarded-insertion",simulationBackend,simulationBackend,()->true));
        ItemStack first=guarded.insert(offered,true);
        assertNotSame(offered,first);assertTrue(ItemStack.isSameItemSameComponents(offered,first));assertEquals(12,first.getCount());
        first.shrink(5);first.remove(DataComponents.CUSTOM_NAME);
        ItemStack quarantined=guarded.insert(offered,false);
        assertEquals(1,reads.get());assertNotSame(offered,quarantined);assertNotSame(first,quarantined);
        assertEquals(12,offered.getCount());assertEquals(12,quarantined.getCount());
        assertTrue(quarantined.has(DataComponents.CUSTOM_NAME));assertFalse(guarded.valid());
        var commits=new java.util.concurrent.atomic.AtomicInteger();
        var uncertainBackend=new ItemStackHandler(1){
            @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){
                if(!simulate){commits.addAndGet(stack.getCount());throw new IllegalStateException("Failed after backend mutation");}
                return ItemStack.EMPTY;
            }
        };
        var uncertain=ProviderGuard.storage(new ItemHandlerStorageProvider("guarded-uncertain",uncertainBackend,uncertainBackend,()->true));
        assertTrue(uncertain.insert(offered,true).isEmpty());
        assertThrows(ProviderFailure.class,()->uncertain.insert(offered,false));
        assertEquals(12,commits.get());assertFalse(uncertain.valid());assertEquals(12,offered.getCount());
    }
    @Test void internalExtractionCanAggregateMoreThanOneItemStack() {
        var handler=new ItemStackHandler(2);
        handler.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,64));
        handler.setStackInSlot(1,new ItemStack(Items.IRON_INGOT,64));
        var provider=new ItemHandlerStorageProvider("multi-stack",handler,handler,() -> true);
        ItemKey key=new ItemKey(new ItemStack(Items.IRON_INGOT));
        provider.snapshot();
        assertEquals(65,provider.extract(key,65,true).getCount());
        assertEquals(64,handler.getStackInSlot(0).getCount(),"Simulation leaves the first stack untouched");
        assertEquals(64,handler.getStackInSlot(1).getCount(),"Simulation leaves the second stack untouched");
        assertEquals(65,provider.extract(key,65,false).getCount());
        assertEquals(63,handler.getStackInSlot(0).getCount()+handler.getStackInSlot(1).getCount());
        assertEquals(63,provider.snapshot().get(key));
    }
    @Test void hugeEmptyHandlerScanningNeverExceedsPollBudget() {
        class LargeHandler implements IItemHandler {
            int reads;
            public int getSlots() { return 100_000; }
            public ItemStack getStackInSlot(int slot) { reads++; return slot==99_999?new ItemStack(Items.DIAMOND,7):ItemStack.EMPTY; }
            public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) { return stack; }
            public ItemStack extractItem(int slot,int amount,boolean simulate) { return ItemStack.EMPTY; }
            public int getSlotLimit(int slot) { return 64; }
            public boolean isItemValid(int slot,ItemStack stack) { return true; }
        }
        LargeHandler handler=new LargeHandler();
        var provider=new ItemHandlerStorageProvider("huge",handler,handler,() -> true);
        for(int batch=0;batch<390;batch++) {
            int before=handler.reads;
            assertTrue(provider.poll(256).isEmpty());
            assertEquals(256,handler.reads-before);
        }
        var complete=provider.poll(256).orElseThrow();
        assertEquals(100_000,handler.reads);
        assertEquals(7,complete.get(new ItemKey(new ItemStack(Items.DIAMOND))));
    }
    @Test void denseSlotCompactionRestartsAnIncompleteSnapshot() {
        var dense=new com.cappleapple.astralrepository.content.CapacityInventory(() -> 128,() -> {});
        dense.insertItem(0,new ItemStack(Items.IRON_INGOT),false);
        dense.insertItem(1,new ItemStack(Items.GOLD_INGOT),false);
        dense.insertItem(2,new ItemStack(Items.DIAMOND),false);
        var provider=new ItemHandlerStorageProvider("dense",dense,dense,() -> true);
        assertTrue(provider.poll(1).isEmpty());
        dense.extractItem(0,1,false);
        assertTrue(provider.poll(1).isEmpty());
        assertTrue(provider.poll(1).isEmpty());
        var snapshot=provider.poll(1).orElseThrow();
        assertFalse(snapshot.containsKey(new ItemKey(new ItemStack(Items.IRON_INGOT))));
        assertEquals(1,snapshot.get(new ItemKey(new ItemStack(Items.GOLD_INGOT))));
        assertEquals(1,snapshot.get(new ItemKey(new ItemStack(Items.DIAMOND))));
        assertEquals(128,provider.capacity());
    }
    @Test void energySimulationAndResourceIdentityAreIndependentOfItems() {
        EnergyStorage handler=new EnergyStorage(10_000);
        var provider=new EnergyResourceProvider("fe",handler,handler,() -> true);
        assertEquals(2000,provider.insert(ResourceKinds.FE,2000,true));
        assertEquals(0,handler.getEnergyStored());
        assertEquals(2000,provider.insert(ResourceKinds.FE,2000,false));
        assertEquals(0,provider.extract(ResourceKinds.ARS_SOURCE,1000,false));
        assertEquals(1000,provider.extract(ResourceKinds.FE,1000,false));
        assertEquals(1000,handler.getEnergyStored());
    }
    @Test void indexedWithdrawalFromHundredThousandSlotsReadsOnlyItsReferences() {
        var handler=new SparseCountingHandler();
        handler.stacks.put(99_999,new ItemStack(Items.IRON_INGOT,20));
        var provider=new ItemHandlerStorageProvider("huge-indexed",handler,handler,() -> true);
        while(provider.poll(1024).isEmpty()) { }
        handler.reads=0;
        ItemStack extracted=provider.extract(new ItemKey(new ItemStack(Items.IRON_INGOT)),8,false);
        assertEquals(8,extracted.getCount());
        assertTrue(handler.reads<=4,"Withdrawal must not scan the 99,999 preceding slots");
        assertEquals(12,handler.stacks.get(99_999).getCount());
    }
    @Test void staleSlotReferencesNeverExtractAReplacementAndRepairIsBounded() {
        var handler=new SparseCountingHandler();
        handler.stacks.put(99_999,new ItemStack(Items.IRON_INGOT,20));
        var provider=new ItemHandlerStorageProvider("huge-stale",handler,handler,() -> true);
        while(provider.poll(1024).isEmpty()) { }
        handler.stacks.put(50_000,handler.stacks.remove(99_999));
        handler.stacks.put(99_999,new ItemStack(Items.DIAMOND,3));
        ItemStack extracted=ItemStack.EMPTY;
        for(int attempt=0;attempt<200&&extracted.isEmpty();attempt++) {
            handler.reads=0;
            extracted=provider.extract(new ItemKey(new ItemStack(Items.IRON_INGOT)),8,false);
            assertTrue(handler.reads<=514,"Stale repair must stay within a bounded slot budget");
        }
        assertEquals(8,extracted.getCount());
        assertTrue(extracted.is(Items.IRON_INGOT));
        assertEquals(3,handler.stacks.get(99_999).getCount(),"Different replacement resource must remain untouched");
    }
    @Test void simulatedExtractionKeepsAccessibleSlotReachableForTheImmediateCommit() {
        var handler=new SparseCountingHandler(); handler.extractionSlot=1024;
        for(int slot=0;slot<=1024;slot++) handler.stacks.put(slot,new ItemStack(Items.IRON_INGOT,8));
        var provider=new ItemHandlerStorageProvider("huge-extraction-access",handler,handler,() -> true);
        while(provider.poll(1024).isEmpty()) { }
        ItemKey key=new ItemKey(new ItemStack(Items.IRON_INGOT));
        ItemStack simulated=ItemStack.EMPTY;
        for(int attempt=0;attempt<4&&simulated.isEmpty();attempt++) {
            handler.reads=0;
            simulated=provider.extract(key,8,true);
            assertTrue(handler.reads<=512,"Rejected matching slots must rotate within the lookup budget");
        }
        assertEquals(8,simulated.getCount(),"Later extractable slots must remain reachable");
        assertEquals(8,handler.stacks.get(1024).getCount(),"Simulation leaves the source untouched");
        handler.reads=0;
        ItemStack actual=provider.extract(key,8,false);
        assertEquals(8,actual.getCount(),"A successful simulation must keep its slot reachable for execution");
        assertTrue(handler.reads<=514,"Execution must remain bounded");
        assertFalse(handler.stacks.containsKey(1024));
        assertEquals(8,handler.stacks.get(0).getCount(),"Denied extraction slots remain untouched");
    }
    @Test void simulatedInsertionKeepsMatchingAndEmptyCandidatesReachableForTheImmediateCommit() {
        for(boolean occupied:java.util.List.of(false,true)) {
            var handler=new SparseCountingHandler(); handler.insertionSlot=400;
            if(occupied) {
                for(int slot=0;slot<=1024;slot++) handler.stacks.put(slot,new ItemStack(Items.IRON_INGOT,64));
                handler.stacks.get(400).setCount(63);
            }
            var provider=new ItemHandlerStorageProvider("huge-insertion-access",handler,handler,() -> true);
            while(provider.poll(1024).isEmpty()) { }
            ItemStack offered=new ItemStack(Items.IRON_INGOT);
            ItemStack simulated=offered;
            for(int attempt=0;attempt<4&&!simulated.isEmpty();attempt++) {
                handler.reads=0;
                simulated=provider.insert(offered,true);
                assertTrue(handler.reads<=512,"Simulated insertion remains bounded");
            }
            assertTrue(simulated.isEmpty(),"Simulation discovers the accepting slot across bounded calls");
            assertEquals(occupied?63:0,handler.stacks.getOrDefault(400,ItemStack.EMPTY).getCount(),"Simulation leaves the slot untouched");
            handler.reads=0;
            assertTrue(provider.insert(offered,false).isEmpty(),"Execution must revisit the slot that accepted the simulation");
            assertTrue(handler.reads<=514,"Committed insertion remains bounded");
            assertEquals(occupied?64:1,handler.stacks.get(400).getCount());
        }
    }
    @Test void insertionRepairEventuallyReachesARestrictedSlotWithoutRescanningTheWholeHandler() {
        var handler=new SparseCountingHandler(); handler.insertionSlot=99_999;
        var provider=new ItemHandlerStorageProvider("huge-insert",handler,handler,() -> true);
        while(provider.poll(1024).isEmpty()) { }
        ItemStack remaining=new ItemStack(Items.IRON_INGOT);
        for(int attempt=0;attempt<200&&!remaining.isEmpty();attempt++) {
            handler.reads=0;
            remaining=provider.insert(remaining,false);
            assertTrue(handler.reads<=514,"Insertion lookup must rotate within the budget");
        }
        assertTrue(remaining.isEmpty(),"A valid late slot cannot remain permanently unreachable");
        assertEquals(1,handler.stacks.get(99_999).getCount());
    }
    @Test void nestedHandlerTransfersCannotOverwriteTheOuterBoundedLookup() {
        for(boolean nestedInsertion:java.util.List.of(false,true)){
            var providerRef=new ItemHandlerStorageProvider[1];var nestedAmount=new java.util.concurrent.atomic.AtomicInteger();
            var gold=new ItemKey(new ItemStack(Items.GOLD_INGOT));
            var handler=new ItemStackHandler(2048){
                boolean nested;
                @Override public ItemStack extractItem(int slot,int amount,boolean simulate){
                    ItemStack extracted=super.extractItem(slot,amount,simulate);
                    if(!simulate&&slot==1000&&!nested){
                        nested=true;
                        if(nestedInsertion)nestedAmount.set(3-providerRef[0].insert(new ItemStack(Items.DIAMOND,3),false).getCount());
                        else nestedAmount.set(providerRef[0].extract(gold,3,false).getCount());
                    }
                    return extracted;
                }
            };
            handler.setStackInSlot(1000,new ItemStack(Items.IRON_INGOT,4));
            handler.setStackInSlot(1001,new ItemStack(Items.IRON_INGOT,4));
            handler.setStackInSlot(2000,new ItemStack(Items.GOLD_INGOT,3));
            var provider=new ItemHandlerStorageProvider("reentrant-lookup-"+nestedInsertion,handler,handler,()->true);providerRef[0]=provider;
            provider.snapshot();
            assertEquals(8,provider.extract(new ItemKey(new ItemStack(Items.IRON_INGOT)),8,false).getCount(),"Nested callbacks preserve the outer candidate order beyond its rotating-scan window");
            assertEquals(3,nestedAmount.get());assertTrue(handler.getStackInSlot(1000).isEmpty());assertTrue(handler.getStackInSlot(1001).isEmpty());
            assertEquals(nestedInsertion?3:0,handler.getStackInSlot(2000).getCount());
            assertEquals(nestedInsertion?3:0,handler.getStackInSlot(0).getCount());
            if(nestedInsertion)assertTrue(handler.getStackInSlot(0).is(Items.DIAMOND));
            assertTrue(provider.extract(new ItemKey(new ItemStack(Items.IRON_INGOT)),1,true).isEmpty(),"Released lookup scratch contains no stale candidates");
        }
    }
    @Test void liveCandidateProbesStayBoundedAndReachMatchingLateSlotsWithoutASnapshot() {
        var handler=new SparseCountingHandler();handler.stacks.put(0,new ItemStack(Items.IRON_INGOT,4));
        handler.stacks.put(1024,new ItemStack(Items.DIAMOND,7));handler.extractionSlot=1024;
        var provider=new ItemHandlerStorageProvider("candidate-window",handler,handler,()->true);
        assertEquals(new ItemKey(new ItemStack(Items.IRON_INGOT)),provider.candidate(stack->true));
        assertTrue(provider.extract(new ItemKey(new ItemStack(Items.IRON_INGOT)),1,true).isEmpty(),"Candidate identity does not bypass sided extraction restrictions");
        ItemKey candidate=null;
        for(int probe=0;probe<33&&candidate==null;probe++){
            handler.reads=0;candidate=provider.candidate(stack->stack.is(Items.DIAMOND));
            assertTrue(handler.reads<=32,"Candidate search must read at most32 slots");
        }
        assertEquals(new ItemKey(new ItemStack(Items.DIAMOND)),candidate);
        assertEquals(7,handler.stacks.get(1024).getCount(),"Candidate lookup never removes items");
        handler.reads=0;assertEquals(3,provider.extract(candidate,3,true).getCount());
        assertTrue(handler.reads<=2,"Candidate seeds the verified extraction index");
        assertEquals(3,provider.extract(candidate,3,false).getCount());
        assertEquals(4,handler.stacks.get(0).getCount());assertEquals(4,handler.stacks.get(1024).getCount());
    }
    @Test void liveCandidateRotationPreservesComponentsAndVisitsEveryMatchingIdentity() {
        var handler=new ItemStackHandler(3);handler.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,5));
        handler.setStackInSlot(1,new ItemStack(Items.GOLD_INGOT,6));handler.setStackInSlot(2,new ItemStack(Items.DIAMOND,7));
        var provider=new ItemHandlerStorageProvider("candidate-identities",handler,handler,()->true);
        var iron=provider.candidate(stack->true);var gold=provider.candidate(stack->true);var diamond=provider.candidate(stack->true);
        assertTrue(iron.matches(handler.getStackInSlot(0))&&gold.matches(handler.getStackInSlot(1))&&diamond.matches(handler.getStackInSlot(2)));
        handler.getStackInSlot(0).set(DataComponents.CUSTOM_NAME,Component.literal("Live component change"));
        var renamed=provider.candidate(stack->true);
        assertNotEquals(iron,renamed);assertTrue(renamed.matches(handler.getStackInSlot(0)));
        assertFalse(iron.sample().has(DataComponents.CUSTOM_NAME),"Earlier candidate remains an immutable identity");
        assertEquals(5,handler.getStackInSlot(0).getCount());assertEquals(6,handler.getStackInSlot(1).getCount());assertEquals(7,handler.getStackInSlot(2).getCount());
    }
    @Test void occupiedHintsStayCheapAndDiscoverNewContentsDuringContinuousTransfers() {
        class CountingHandler extends ItemStackHandler {
            int reads;CountingHandler(){super(256);}
            @Override public ItemStack getStackInSlot(int slot){reads++;return super.getStackInSlot(slot);}
        }
        var handler=new CountingHandler();handler.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,64));
        var provider=new ItemHandlerStorageProvider("candidate-discovery",handler,handler,()->true);
        provider.snapshot();
        for(int call=0;call<16;call++){
            handler.reads=0;assertTrue(provider.candidate(stack->true).sample().is(Items.IRON_INGOT));
            assertTrue(handler.reads<=9,"A warm sparse inventory uses eight discovery reads and one live occupied check");
        }
        handler.setStackInSlot(255,new ItemStack(Items.DIAMOND,7));
        boolean found=false;
        for(int call=0;call<32&&!found;call++){
            handler.reads=0;var key=provider.candidate(stack->true);found=key!=null&&key.sample().is(Items.DIAMOND);
            assertTrue(handler.reads<=32,"Independent discovery shares the bounded probe budget");
        }
        assertTrue(found,"New external contents must be discovered while the original matching stack stays available");
        var seen=new java.util.HashSet<ItemKey>();for(int call=0;call<4;call++)seen.add(provider.candidate(stack->true));
        assertEquals(2,seen.size(),"Occupied identities rotate fairly after discovery");
    }
    @Test void occupiedHintsRevalidateRemovalComponentsAndCompletedPolls() {
        var handler=new ItemStackHandler(64);handler.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,5));
        var provider=new ItemHandlerStorageProvider("candidate-live-index",handler,handler,()->true);provider.snapshot();
        var original=provider.candidate(stack->true);handler.setStackInSlot(0,ItemStack.EMPTY);
        handler.setStackInSlot(20,new ItemStack(Items.DIAMOND,4));
        var discovered=provider.candidate(stack->true);assertTrue(discovered.sample().is(Items.DIAMOND));
        handler.getStackInSlot(20).set(DataComponents.CUSTOM_NAME,Component.literal("Updated live stack"));
        var renamed=provider.candidate(stack->true);assertNotEquals(discovered,renamed);
        assertFalse(discovered.sample().has(DataComponents.CUSTOM_NAME));assertTrue(original.sample().is(Items.IRON_INGOT));
        handler.setStackInSlot(20,ItemStack.EMPTY);handler.setStackInSlot(63,new ItemStack(Items.GOLD_INGOT,3));
        provider.snapshot();assertTrue(provider.candidate(stack->true).sample().is(Items.GOLD_INGOT),"Completed poll replaces the occupied index");
        handler.setStackInSlot(63,ItemStack.EMPTY);assertNull(provider.candidate(stack->true),"A stale occupied hint cannot return removed contents");
    }
    @Test void cachedInsertionIdentityRechecksLiveComponentsBetweenCalls() {
        var handler=new ItemStackHandler(3);var provider=new ItemHandlerStorageProvider("insertion-identity",handler,handler,()->true);
        var stack=new ItemStack(Items.IRON_INGOT,4);
        assertTrue(provider.insert(stack,true).isEmpty());assertTrue(provider.insert(stack,false).isEmpty());
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Distinct inserted iron"));
        assertTrue(provider.insert(stack,true).isEmpty());assertTrue(provider.insert(stack,false).isEmpty());
        assertEquals(4,handler.getStackInSlot(0).getCount());assertFalse(handler.getStackInSlot(0).has(DataComponents.CUSTOM_NAME));
        assertEquals(4,handler.getStackInSlot(1).getCount());assertTrue(ItemStack.isSameItemSameComponents(stack,handler.getStackInSlot(1)));
    }
    @Test void failedLiveCandidateIsQuarantinedBeforeFurtherProviderCalls() {
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var handler=new ItemStackHandler(1){@Override public ItemStack getStackInSlot(int slot){calls.incrementAndGet();throw new IllegalStateException("failed live probe");}};
        var provider=ProviderGuard.storage(new ItemHandlerStorageProvider("candidate-failure",handler,handler,()->true));
        assertNull(provider.candidate(stack->true));assertNull(provider.candidate(stack->true));
        assertEquals(1,calls.get());assertFalse(provider.valid(),"Failed candidate read quarantines the provider like other read operations");
    }
    private static final class SparseCountingHandler implements IItemHandler {
        final java.util.Map<Integer,ItemStack> stacks=new java.util.HashMap<>();
        int reads,insertionSlot=-1,extractionSlot=-1;
        public int getSlots() { return 100_000; }
        public ItemStack getStackInSlot(int slot) { reads++; return stacks.getOrDefault(slot,ItemStack.EMPTY).copy(); }
        public int getSlotLimit(int slot) { return 64; }
        public boolean isItemValid(int slot,ItemStack stack) { return insertionSlot<0||slot==insertionSlot; }
        public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) {
            if(!isItemValid(slot,stack)) return stack;
            ItemStack present=stacks.getOrDefault(slot,ItemStack.EMPTY);
            if(!present.isEmpty()&&!ItemStack.isSameItemSameComponents(present,stack)) return stack;
            int accepted=Math.min(stack.getCount(),64-present.getCount());
            if(!simulate&&accepted>0) stacks.put(slot,stack.copyWithCount(present.getCount()+accepted));
            return stack.copyWithCount(stack.getCount()-accepted);
        }
        public ItemStack extractItem(int slot,int amount,boolean simulate) {
            if(extractionSlot>=0&&slot!=extractionSlot) return ItemStack.EMPTY;
            ItemStack present=stacks.getOrDefault(slot,ItemStack.EMPTY);
            int extracted=Math.min(present.getCount(),amount);
            ItemStack result=present.copyWithCount(extracted);
            if(!simulate) { present.shrink(extracted); if(present.isEmpty()) stacks.remove(slot); }
            return result;
        }
    }
}