package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.compat.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class DirectRuneGameTests {
    private static final Identifier PUSH=Identifier.parse("astral_repository:push_rune"),PULL=Identifier.parse("astral_repository:pull_rune");
    private static RuneLayer layer(RuneSurface rune,RuneLayer.Mode mode){return Objects.requireNonNull(rune.addLayer(mode==RuneLayer.Mode.PUSH?PUSH:PULL,mode));}
    private static GlobalPos at(GameTestHelper h,BlockPos relative){return GlobalPos.of(h.getLevel().dimension(),h.absolutePos(relative));}
    private static ChestBlockEntity chest(GameTestHelper h,BlockPos pos){h.setBlock(pos,Blocks.CHEST);return (ChestBlockEntity)h.getBlockEntity(pos);}
    private static long count(ChestBlockEntity chest,ItemKey key){long count=0;for(int i=0;i<chest.getContainerSize();i++){ItemStack stack=chest.getItem(i);if(!stack.isEmpty()&&new ItemKey(stack).equals(key))count+=stack.getCount();}return count;}

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",batch="astral_candidate")
    public static void candidateHintsKeepFallbackStockLimitsAndLiveTransactions(GameTestHelper h){
        for(int scenario=0;scenario<5;scenario++){
            final int mode=scenario;BlockPos home=new BlockPos(2+scenario*3,2,2),away=new BlockPos(2+scenario*3,2,6);
            h.setBlock(home,Blocks.ENCHANTING_TABLE);h.setBlock(away,Blocks.ENCHANTING_TABLE);
            BlockPos homeAbs=h.absolutePos(home),awayAbs=h.absolutePos(away);int[] reads={0,0};
            ItemStackHandler source=new ItemStackHandler(2);source.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,10));source.setStackInSlot(1,new ItemStack(Items.GOLD_INGOT,10));
            ItemStackHandler target=new ItemStackHandler(2){@Override public boolean isItemValid(int slot,ItemStack stack){return mode!=2||stack.is(Items.GOLD_INGOT);}};
            String id="candidate_"+UUID.randomUUID();
            StorageProvider delegate=new ItemHandlerStorageProvider(id,source,source,()->h.getLevel().getBlockState(homeAbs).is(Blocks.ENCHANTING_TABLE));
            StorageProvider probing=new StorageProvider(){
                public String id(){return id;}public Object identity(){return source;}public boolean valid(){return delegate.valid();}public long capacity(){return delegate.capacity();}
                public Map<ItemKey,Long> snapshot(){reads[0]++;return delegate.snapshot();}
                public Optional<Map<ItemKey,Long>> poll(int budget){reads[0]++;return delegate.poll(budget);}
                public ItemKey candidate(java.util.function.Predicate<ItemStack> matches){reads[1]++;return mode==1?null:delegate.candidate(matches);}
                public ItemStack insert(ItemStack stack,boolean simulate){return delegate.insert(stack,simulate);}
                public ItemStack extract(ItemKey key,int amount,boolean simulate){return delegate.extract(key,amount,simulate);}
            };
            StorageProvider receiving=new ItemHandlerStorageProvider(id+"_target",target,target,()->h.getLevel().getBlockState(awayAbs).is(Blocks.ENCHANTING_TABLE));
            CompatibilityRegistry.registerStorage(id,(level,pos,side)->level!=h.getLevel()?List.of():pos.equals(homeAbs)?List.of(probing):pos.equals(awayAbs)?List.of(receiving):List.of());
            RuneSurface surface=RuneSurfaces.getOrCreate(h.getLevel(),homeAbs,Direction.UP);RuneLayer push=layer(surface,RuneLayer.Mode.PUSH);
            push.setCadence(RuneCadence.DEFAULT.with(RuneCadence.Kind.ITEMS,new RuneCadence.Rate(4,1)));
            if(mode==3)push.filter().setMinimum(8);if(mode==4)push.filter().setTarget(3);
            h.assertTrue(surface.toggleTarget(push.id(),at(h,away),Direction.SOUTH).assigned(),"Candidate fixture assigned");
            reads[0]=0;reads[1]=0;var transfers=new DirectRuneTransfers(h.getLevel().getServer());transfers.track(surface);transfers.tick();
            int expected=mode==3?2:mode==4?3:4,total=0;
            for(int slot=0;slot<2;slot++)total+=source.getStackInSlot(slot).getCount()+target.getStackInSlot(slot).getCount();
            h.assertTrue(total==20&&push.transferredItems()==expected,"Hint/fallback commits conserve quantities in scenario "+mode);
            h.assertTrue(mode==0?reads[0]==0:reads[0]>0,"Only a usable unlimited hint avoids a full inventory poll in scenario "+mode);
            h.assertTrue(mode<3?reads[1]>0:reads[1]==0,"Reserve and stock targets use counted snapshots in scenario "+mode);
            if(mode==2)h.assertTrue(target.getStackInSlot(0).is(Items.GOLD_INGOT),"Rejected hint falls back to another accepted identity");
            RuneSurfaces.remove(h.getLevel(),homeAbs,Direction.UP);
        }
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=80)
    public static void partialWorkBatchesNeverSkipTheThirtyThirdRune(GameTestHelper h){
        var transfers=new DirectRuneTransfers(h.getLevel().getServer());var layers=new java.util.ArrayList<RuneLayer>();
        for(int i=0;i<9;i++){var pos=new BlockPos(2+i%3*4,2,2+i/3*4);chest(h,pos);var surface=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(pos),Direction.UP);
            for(int j=0;j<4&&layers.size()<33;j++){var rune=layer(surface,RuneLayer.Mode.PUSH);rune.setEnabled(true);layers.add(rune);}transfers.track(surface);}
        h.startSequence().thenWaitUntil(()->h.assertTrue(LogisticsTiming.automaticDue(h.getLevel().getServer().getTickCount()),"Await transfer tick"))
            .thenExecute(()->{layers.forEach(r->r.setEnabled(true));transfers.tick();transfers.tick();h.assertTrue(layers.stream().allMatch(r->r.status().equals("Unlinked")),"Both bounded batches visit every rune, including #33");for(var r:layers)RuneSurfaces.remove(h.getLevel(),r.surface().getBlockPos(),r.surface().facing());}).thenSucceed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=400)
    public static void stackedPushPullLayersUseIndependentBareTargetsAndComponentFilters(GameTestHelper h){
        BlockPos hostPos=new BlockPos(7,2,7),aPos=new BlockPos(3,2,7),bPos=new BlockPos(11,2,7),outPos=new BlockPos(7,2,11),untouchedPos=new BlockPos(7,2,3);
        var host=chest(h,hostPos);var a=chest(h,aPos);var b=chest(h,bPos);var out=chest(h,outPos);var untouched=chest(h,untouchedPos);
        ItemStack named=new ItemStack(Items.IRON_INGOT,11);named.set(DataComponents.CUSTOM_NAME,Component.literal("Layer-specific iron"));
        ItemKey iron=new ItemKey(new ItemStack(Items.IRON_INGOT)),gold=new ItemKey(new ItemStack(Items.GOLD_INGOT)),special=new ItemKey(named);
        a.setItem(0,new ItemStack(Items.IRON_INGOT,20));a.setItem(1,new ItemStack(Items.GOLD_INGOT,5));b.setItem(0,named);b.setItem(1,new ItemStack(Items.IRON_INGOT,7));untouched.setItem(0,new ItemStack(Items.GOLD_INGOT,9));
        var rune=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(hostPos),Direction.NORTH);
        RuneLayer pullA=layer(rune,RuneLayer.Mode.PULL),pullB=layer(rune,RuneLayer.Mode.PULL),push=layer(rune,RuneLayer.Mode.PUSH);
        pullA.filter().add(FilterRules.Kind.ITEM,"minecraft:gold_ingot",false,ItemStack.EMPTY);pullA.changed();
        pullB.filter().add(FilterRules.Kind.COMPONENTS,"minecraft:iron_ingot",false,named);pullB.changed();
        push.filter().add(FilterRules.Kind.ITEM,"minecraft:gold_ingot",false,ItemStack.EMPTY);push.changed();
        h.assertTrue(rune.toggleTarget(pullA.id(),at(h,aPos),Direction.EAST).assigned(),"First bare source assigned");
        h.assertTrue(rune.toggleTarget(pullB.id(),at(h,bPos),Direction.WEST).assigned(),"Second independent bare source assigned");
        h.assertTrue(rune.toggleTarget(push.id(),at(h,outPos),Direction.NORTH).assigned(),"Independent bare destination assigned");
        h.onEachTick(()->{
            long totalIron=0,totalGold=0,totalSpecial=0;for(var container:List.of(host,a,b,out,untouched)){totalIron+=count(container,iron);totalGold+=count(container,gold);totalSpecial+=count(container,special);}
            h.assertTrue(totalIron==27&&totalGold==14&&totalSpecial==11,"Every tick conserves both identities and components");
        });
        h.succeedWhen(()->{
            h.assertTrue(count(host,special)==11&&count(out,gold)==5,"Pull gets named items into host while Push exports only gold: "+count(host,special)+" / "+count(out,gold)+" states="+pullA.status()+"; "+pullB.status()+"; "+push.status());
            h.assertTrue(count(a,iron)==20&&count(b,iron)==7&&count(untouched,gold)==9,"Unmatched and unlinked storage is untouched");
            h.assertTrue(pullA.transferredItems()==5&&pullB.transferredItems()==11&&push.transferredItems()==5,"Each visible layer records its own successful transfers");
            h.assertTrue(NetworkManager.get(h.getLevel().getServer()).links(rune.address()).isEmpty(),"Standalone transfer requires no crystal graph links");
        });
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=400)
    public static void runeTransferFiltersDoNotHideNexusContents(GameTestHelper h){
        BlockPos nexusPos=new BlockPos(4,2,4),hostPos=new BlockPos(10,2,4);h.setBlock(nexusPos,AstralContent.STORAGE_NEXUS.get());var host=chest(h,hostPos);
        var nexus=(CrystalNodeBlockEntity)h.getBlockEntity(nexusPos);nexus.setChannel(4);host.setItem(0,new ItemStack(Items.IRON_INGOT,7));host.setItem(1,new ItemStack(Items.GOLD_INGOT,9));
        var rune=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(hostPos),Direction.NORTH);rune.setChannel(4);var pull=layer(rune,RuneLayer.Mode.PULL);
        pull.filter().add(FilterRules.Kind.ITEM,"minecraft:gold_ingot",false,ItemStack.EMPTY);pull.changed();
        // Even a legacy shared filter must not affect logical inventory access after migration.
        rune.extractionFilter().add(FilterRules.Kind.ITEM,"minecraft:gold_ingot",false,ItemStack.EMPTY);
        var manager=NetworkManager.get(h.getLevel().getServer());h.assertTrue(manager.toggleLink(nexus.address(),rune.address()).linked(),"Rune host explicitly joins Nexus");
        ItemKey iron=new ItemKey(new ItemStack(Items.IRON_INGOT)),gold=new ItemKey(new ItemStack(Items.GOLD_INGOT));
        h.succeedWhen(()->{var network=manager.networkAt(nexus.address());h.assertTrue(network!=null&&network.snapshot().getOrDefault(iron,0L)==7&&network.snapshot().getOrDefault(gold,0L)==9,"Both filtered and unfiltered items remain visible");h.assertTrue(network.extract(iron,1).getCount()==1,"Nexus can withdraw an item excluded from rune transfer");});
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=400)
    public static void sidedPartialDestinationRefundsEveryUnacceptedItem(GameTestHelper h){
        BlockPos hostPos=new BlockPos(4,2,4),targetPos=new BlockPos(10,2,4);var host=chest(h,hostPos);host.setItem(0,new ItemStack(Items.IRON_INGOT,64));h.setBlock(targetPos,Blocks.ENCHANTING_TABLE);
        ItemStackHandler target=new ItemStackHandler(1){@Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){if(simulate)return ItemStack.EMPTY;int accepted=Math.min(Math.max(0,5-getStackInSlot(slot).getCount()),stack.getCount());super.insertItem(slot,stack.copyWithCount(accepted),false);return stack.copyWithCount(stack.getCount()-accepted);}};
        String id="direct_partial_"+UUID.randomUUID();BlockPos absolute=h.absolutePos(targetPos);
        CompatibilityRegistry.registerStorage(id,(level,pos,side)->level==h.getLevel()&&pos.equals(absolute)&&side==Direction.WEST?List.of(new ItemHandlerStorageProvider(id,target,target,()->level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE))):List.of());
        var rune=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(hostPos),Direction.NORTH);var push=layer(rune,RuneLayer.Mode.PUSH);
        h.assertTrue(!rune.toggleTarget(push.id(),at(h,targetPos),Direction.EAST).success(),"Wrong target face is rejected");
        h.assertTrue(rune.toggleTarget(push.id(),at(h,targetPos),Direction.WEST).assigned(),"Actual sided target capability is assigned");
        ItemKey iron=new ItemKey(new ItemStack(Items.IRON_INGOT));
        h.onEachTick(()->h.assertTrue(count(host,iron)+target.getStackInSlot(0).getCount()==64,"Partial commits conserve all items through immediate source refund"));
        h.succeedWhen(()->{int accepted=target.getStackInSlot(0).getCount();h.assertTrue(accepted>0&&accepted<16,"Destination intentionally accepted only part of the simulated offer");push.setEnabled(false);h.assertTrue(push.transferredItems()==accepted,"Counter records committed acceptance, not attempted quantity");});
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=400,batch="astral_direct_failure")
    public static void uncertainDirectCommitPausesOnlyItsLayerAndNeverReplays(GameTestHelper h){
        BlockPos hostPos=new BlockPos(4,2,4),targetPos=new BlockPos(10,2,4);var host=chest(h,hostPos);host.setItem(0,new ItemStack(Items.IRON_INGOT,64));h.setBlock(targetPos,Blocks.ENCHANTING_TABLE);
        int[] accepted={0};ItemKey iron=new ItemKey(new ItemStack(Items.IRON_INGOT));String id="direct_failure_"+UUID.randomUUID();BlockPos absolute=h.absolutePos(targetPos);
        StorageProvider broken=new StorageProvider(){public String id(){return id;}public Object identity(){return this;}public boolean valid(){return h.getLevel().getBlockState(absolute).is(Blocks.ENCHANTING_TABLE);}public long capacity(){return 64;}public Map<ItemKey,Long> snapshot(){return Map.of(iron,(long)accepted[0]);}public ItemStack extract(ItemKey key,int amount,boolean simulate){return ItemStack.EMPTY;}public ItemStack insert(ItemStack stack,boolean simulate){if(simulate)return ItemStack.EMPTY;accepted[0]+=stack.getCount();throw new IllegalStateException("Expected direct fixture failure after commit");}};
        CompatibilityRegistry.registerStorage(id,(level,pos,side)->level==h.getLevel()&&pos.equals(absolute)?List.of(broken):List.of());
        var rune=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(hostPos),Direction.NORTH);var push=layer(rune,RuneLayer.Mode.PUSH);var other=layer(rune,RuneLayer.Mode.PULL);
        h.assertTrue(rune.toggleTarget(push.id(),at(h,targetPos),Direction.WEST).assigned(),"Failure fixture assigned");
        h.runAfterDelay(80,()->{
            h.assertTrue(accepted[0]==16&&count(host,iron)==48,"Indeterminate commit is not refunded or repeated");h.assertTrue(!push.enabled()&&other.enabled(),"Only the failed layer is paused");
            ListTag saved=TransferRecoveryData.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess()).getList("Transfers",Tag.TAG_COMPOUND);boolean found=false;
            for(int i=0;i<saved.size();i++){CompoundTag entry=saved.getCompound(i);if(entry.getString("Provider").contains(id)&&entry.getBoolean("Uncertain")&&entry.getLong("Amount")==16&&entry.getString("Side").equals("north"))found=true;}
            h.assertTrue(found,"Non-replaying audit journal retains amount and the actual source face");h.succeed();
        });
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=800)
    public static void pullMovesRealSidedFluidAndStopsAtItsOwnStockTarget(GameTestHelper h){
        BlockPos hostPos=new BlockPos(4,2,4),sourcePos=new BlockPos(10,2,4);h.setBlock(hostPos,Blocks.ENCHANTING_TABLE);h.setBlock(sourcePos,Blocks.ENCHANTING_TABLE);
        FluidTank host=new FluidTank(4000),source=new FluidTank(4000);source.fill(new FluidStack(Fluids.WATER,1000),IFluidHandler.FluidAction.EXECUTE);
        String id="direct_fluid_"+UUID.randomUUID();BlockPos hostAbs=h.absolutePos(hostPos),sourceAbs=h.absolutePos(sourcePos);
        CompatibilityRegistry.registerResources(id,(level,pos,side)->level!=h.getLevel()?List.of():pos.equals(hostAbs)&&side==Direction.SOUTH?List.of(new FluidResourceProvider(id+"h",host,host,()->level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE))):pos.equals(sourceAbs)&&side==Direction.NORTH?List.of(new FluidResourceProvider(id+"s",source,source,()->level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE))):List.of());
        var rune=RuneSurfaces.getOrCreate(h.getLevel(),hostAbs,Direction.SOUTH);var pull=layer(rune,RuneLayer.Mode.PULL);pull.filter().add(FilterRules.Kind.FLUID,"minecraft:water",false,ItemStack.EMPTY);pull.filter().setTarget(300);pull.changed();
        h.assertTrue(rune.toggleTarget(pull.id(),at(h,sourcePos),Direction.NORTH).assigned(),"Bare sided fluid source assigned");
        long started=h.getLevel().getServer().getTickCount();
        h.onEachTick(()->{
            h.assertTrue(source.getFluidAmount()+host.getFluidAmount()==1000,"Actual tanks conserve fluid every tick");
            h.assertTrue(host.getFluidAmount()<=300&&pull.transferredFluid()<=300,"No scheduled pass may exceed the per-layer stock target");
        });
        // The shared scheduler now runs every 20 ticks by default and visits a bounded batch.
        // Wait for this layer's budget turns, then observe a later visit that refuses more fluid.
        h.startSequence().thenWaitUntil(()->h.assertTrue(host.getFluidAmount()==300&&source.getFluidAmount()==700,
                "Await stock target: host="+host.getFluidAmount()+", source="+source.getFluidAmount()+", moved="+pull.transferredFluid()+", status="+pull.status()))
                .thenExecute(()->{
                    h.assertTrue(pull.transferredFluid()==300&&pull.transferredItems()==0,"Fluid and item counters remain separate");
                    pull.clearFilter();
                    h.assertTrue(pull.filter().entries().isEmpty()&&pull.filter().target()==300,"Clearing sample predicates preserves the explicit stock cap");
                }).thenWaitUntil(()->h.assertTrue(pull.status().equals("Stock target reached"),
                        "Await a subsequent budget visit after reaching 300 mB; status="+pull.status()))
                .thenExecute(()->{
                    h.assertTrue(host.getFluidAmount()==300&&source.getFluidAmount()==700&&pull.transferredFluid()==300,"A later transfer attempt keeps the exact stock cap after clearing the predicate");
                    com.cappleapple.astralrepository.AstralRepository.LOGGER.info("Direct fluid stock-cap fixture passed after {} ticks: host={}, source={}, moved={}, status={}",
                            h.getLevel().getServer().getTickCount()-started,host.getFluidAmount(),source.getFluidAmount(),pull.transferredFluid(),pull.status());
                }).thenSucceed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=100)
    public static void independentLayerAssignmentsAndLegacyMigrationPersistWithoutTransfers(GameTestHelper h){
        BlockPos hostPos=new BlockPos(4,2,4),targetPos=new BlockPos(10,2,4);var host=chest(h,hostPos);chest(h,targetPos);host.setItem(0,new ItemStack(Items.IRON_INGOT,12));
        var rune=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(hostPos),Direction.NORTH);var push=layer(rune,RuneLayer.Mode.PUSH);var pull=layer(rune,RuneLayer.Mode.PULL);
        push.filter().add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,ItemStack.EMPTY);push.filter().setMinimum(3);push.setPriority(7);push.setEnabled(false);
        h.assertTrue(rune.toggleTarget(push.id(),at(h,targetPos),Direction.EAST).assigned(),"Clicked face retained in target assignment");
        RuneSurface copy=RuneSurface.load(h.getLevel().getServer(),rune.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(copy.get(push.id())!=null&&copy.get(push.id()).target().face()==Direction.EAST&&copy.get(push.id()).priority()==7&&!copy.get(push.id()).enabled(),"UUID, side, priority and enabled state survive serialization");
        h.assertTrue(copy.get(pull.id()).target()==null&&copy.get(pull.id()).filter().entries().isEmpty(),"Sibling layer remains independently unassigned and unfiltered");
        h.assertTrue(!rune.toggleTarget(pull.id(),at(h,hostPos),Direction.SOUTH).success(),"Another face on the same host is rejected");
        h.assertTrue(!rune.toggleTarget(pull.id(),at(h,targetPos.offset(1000,0,0)),Direction.NORTH).success(),"Out-of-range bare targets are rejected before discovery");
        h.assertTrue(rune.toggleTarget(push.id(),at(h,targetPos),Direction.EAST).success()&&push.target()==null,"Assigning the exact same container face again clears only that layer target");
        CompoundTag old=rune.save(h.getLevel().registryAccess());old.remove("Layers");old.remove("LayerVersion");ListTag glyphs=new ListTag();for(String role:List.of("COLLECTION","DISTRIBUTION","ROUTING")){CompoundTag glyph=new CompoundTag();glyph.putString("Item","astral_repository:filter_sigil");glyph.putString("Role",role);glyphs.add(glyph);}old.put("Glyphs",glyphs);
        RuneSurface migrated=RuneSurface.load(h.getLevel().getServer(),old,h.getLevel().registryAccess());
        h.assertTrue(migrated.layers().size()==3&&migrated.layers().get(0).mode()==RuneLayer.Mode.PULL&&migrated.layers().get(1).mode()==RuneLayer.Mode.PUSH,"Every legacy glyph becomes a usable visible transfer layer without losing count");
        h.assertTrue(migrated.layers().stream().allMatch(l->l.target()==null)&&!migrated.collects()&&!migrated.stocks(),"Migration never creates hidden automatic transfer routes");
        migrated.layers().get(0).filter().add(FilterRules.Kind.ITEM,"minecraft:gold_ingot",false,ItemStack.EMPTY);
        h.assertTrue(migrated.layers().get(2).filter().entries().isEmpty()&&host.getItem(0).getCount()==12,"Migrated filters are independent and stored items are untouched");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void configuredLayerLimitPreservesArchivedRunesAndRestoresThemDisabled(GameTestHelper h){
        int previous=com.cappleapple.astralrepository.AstralServerConfig.maxRunesPerFace.get();
        try{com.cappleapple.astralrepository.AstralServerConfig.maxRunesPerFace.set(4);
        BlockPos hostPos=new BlockPos(4,2,4),targetPos=new BlockPos(10,2,4);chest(h,hostPos);chest(h,targetPos);
        RuneSurface rune=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(hostPos),Direction.NORTH);
        for(int i=0;i<4;i++)layer(rune,i%2==0?RuneLayer.Mode.PUSH:RuneLayer.Mode.PULL);
        h.assertTrue(rune.addLayer(PUSH,RuneLayer.Mode.PUSH)==null&&rune.layers().size()==4,"A fifth new layer is refused without modifying the existing four");
        CompoundTag old=rune.save(h.getLevel().registryAccess());old.putInt("LayerVersion",1);ListTag values=old.getList("Layers",Tag.TAG_COMPOUND);ListTag archive=new ListTag();old.put("ArchivedLayers",archive);
        UUID fifth=UUID.randomUUID(),sixth=UUID.randomUUID();
        FilterRules preserved=new FilterRules();preserved.add(FilterRules.Kind.ITEM,"minecraft:gold_ingot",false,ItemStack.EMPTY);preserved.setMinimum(5);preserved.setTarget(23);
        for(UUID id:List.of(fifth,sixth)){
            CompoundTag extra=values.getCompound(0).copy();extra.putUUID("Id",id);extra.putBoolean("Enabled",true);extra.putInt("Priority",19);extra.put("Filter",preserved.save(h.getLevel().registryAccess()));extra.putLong("TransferredItems",99);
            extra.remove("Targets");extra.put("Target",new AnchorAddress(at(h,targetPos),Direction.EAST).save());archive.add(extra);
        }
        RuneSurface migrated=RuneSurface.load(h.getLevel().getServer(),old,h.getLevel().registryAccess());
        h.assertTrue(migrated.layers().size()==4&&migrated.archivedLayerCount()==2&&migrated.get(fifth)==null,"Legacy excess is retained outside active and visible layers");
        RuneSurface restored=RuneSurface.load(h.getLevel().getServer(),migrated.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(restored.archivedLayerCount()==2,"Dormant excess survives a subsequent save and load");
        h.assertTrue(restored.removeLayer(restored.layers().getFirst().id()),"Removing a visible layer frees one slot");
        RuneLayer promoted=restored.get(fifth);
        h.assertTrue(restored.layers().size()==4&&restored.archivedLayerCount()==1&&promoted!=null&&!promoted.enabled(),"The next preserved identity is restored paused without exceeding four visible layers");
        h.assertTrue(promoted.target().equals(new RuneLayer.Target(at(h,targetPos),Direction.EAST))&&promoted.priority()==19&&promoted.filter().minimum()==5&&promoted.filter().target()==23&&promoted.transferredItems()==99&&promoted.filter().matches(new ItemStack(Items.GOLD_INGOT)),"Restored layer keeps target face, priority, filter, stock settings and counters");
        restored.removeLayer(restored.layers().getFirst().id());
        h.assertTrue(restored.get(sixth)!=null&&!restored.get(sixth).enabled()&&restored.archivedLayerCount()==0&&restored.layers().size()==4,"Every remaining legacy layer can be recovered in order, always paused");h.succeed();
        }finally{com.cappleapple.astralrepository.AstralServerConfig.maxRunesPerFace.set(previous);}

    }
}
