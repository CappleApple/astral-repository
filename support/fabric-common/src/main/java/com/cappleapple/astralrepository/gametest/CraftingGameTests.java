package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.crafting.CraftingService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class CraftingGameTests {
    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty", timeoutTicks=800)
    public static void realFurnacesFeedVanillaCraftingTable(GameTestHelper h) {
        BlockPos chestPos = new BlockPos(1, 2, 1), table = new BlockPos(1, 2, 2), furnace1 = new BlockPos(2, 2, 1), furnace2 = new BlockPos(2, 2, 2);
        h.setBlock(chestPos, Blocks.CHEST); h.setBlock(table, Blocks.CRAFTING_TABLE);
        h.setBlock(furnace1, Blocks.FURNACE); h.setBlock(furnace2, Blocks.FURNACE);
        Container chest = (Container) h.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(Items.RAW_IRON, 4));
        chest.setItem(1, new ItemStack(Items.COAL, 2));
        ItemStack target = new ItemStack(Items.IRON_TRAPDOOR);
        Fixture access = new Fixture(h, chestPos, chest, List.of(table, furnace1, furnace2), target);
        CraftingService service = new CraftingService(access);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "craft-test"), ClientInformation.createDefault());
        var request = service.request(player, target, 1);
        h.assertTrue(request.accepted(), "Plan accepted: " + request.message());
        awaitPlanning(service);service.tick();
        h.assertTrue(service.activeProcessors() == 2, "Two real furnaces start independent smelting operations");
        h.assertTrue(((Container)h.getBlockEntity(furnace1)).getItem(0).isEmpty()&&((Container)h.getBlockEntity(furnace2)).getItem(0).isEmpty(), "Furnaces remain empty while inputs are in flight");
        for(int tick=0;tick<20;tick++)service.tick();
        h.assertTrue(((Container)h.getBlockEntity(furnace1)).getItem(0).is(Items.RAW_IRON), "First furnace receives actual raw iron after arrival");
        h.assertTrue(((Container)h.getBlockEntity(furnace2)).getItem(0).is(Items.RAW_IRON), "Second furnace receives actual raw iron after arrival");
        boolean[] observedLit = {false};
        h.onEachTick(() -> {
            service.tick();
            observedLit[0] |= h.getBlockState(furnace1).getValue(AbstractFurnaceBlock.LIT);
        });
        h.succeedWhen(() -> {
            h.assertTrue(service.activeJobs() == 0, "Physical pipeline completed");
            h.assertTrue(access.snapshot().getOrDefault(new ItemKey(target), 0L) >= 1, "Vanilla crafting result returned to chest");
            h.assertTrue(!access.snapshot().containsKey(new ItemKey(new ItemStack(Items.RAW_IRON))), "Raw iron was consumed exactly once");
            h.assertTrue(observedLit[0], "Vanilla furnace actually ignited");
            h.assertTrue(access.tableAnimations > 0 && access.correctGrid, "Table visual used the shaped 2x2 iron ingredient positions");
            h.assertTrue(!CraftingService.isProcessorReserved(access.at(furnace1)), "Processor ownership released");
        });
    }
    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty", timeoutTicks=80)
    public static void cancelReturnsRealUnprocessedFurnaceInputs(GameTestHelper h) {
        BlockPos chestPos = new BlockPos(1, 2, 1), furnace = new BlockPos(2, 2, 1);
        h.setBlock(chestPos, Blocks.CHEST); h.setBlock(furnace, Blocks.FURNACE);
        Container chest = (Container)h.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(Items.RAW_IRON, 2)); chest.setItem(1, new ItemStack(Items.COAL));
        ItemStack target = new ItemStack(Items.IRON_INGOT);
        Fixture access = new Fixture(h, chestPos, chest, List.of(furnace), target);
        CraftingService service = new CraftingService(access);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "cancel-test"), ClientInformation.createDefault());
        var request = service.request(player, target, 2);
        h.assertTrue(request.accepted(), "Smelting plan accepted"); awaitPlanning(service);service.tick();
        h.runAfterDelay(3, () -> {
            service.cancel(request.jobId());
            h.assertTrue(access.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.RAW_IRON)), 0L) == 2L, "Queued and actual furnace input both returned");
            h.assertTrue(((Container)h.getBlockEntity(furnace)).getItem(0).isEmpty(), "Cancelled input removed from furnace");
            h.assertTrue(!CraftingService.isProcessorReserved(access.at(furnace)), "Cancelled machine released");
            h.assertTrue(!service.cancel(request.jobId()), "Second cancellation cannot duplicate refunds");
            h.succeed();
        });
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void autocraftingTravelsFromStorageToProcessorAndBackWithoutNexusDetour(GameTestHelper h){
        var chestPos=new BlockPos(3,2,3);var table=chestPos.east();var nexus=chestPos.north(4);h.setBlock(chestPos,Blocks.CHEST);h.setBlock(table,Blocks.CRAFTING_TABLE);
        Container chest=(Container)h.getBlockEntity(chestPos);chest.setItem(0,new ItemStack(Items.IRON_INGOT,4));List<GlobalPos> from=new ArrayList<>(),to=new ArrayList<>();
        Fixture access=new Fixture(h,chestPos,chest,List.of(table),new ItemStack(Items.IRON_TRAPDOOR)){
            @Override public GlobalPos origin(){return at(nexus);}
            @Override public ItemStack reserve(ItemKey key,int count,java.util.function.BiConsumer<GlobalPos,ItemStack> receipt){var part=extract(key,count);if(!part.isEmpty())receipt.accept(at(chestPos),part);return part;}
            @Override public void visual(GlobalPos a,GlobalPos b,ItemStack stack,int ticks){from.add(a);to.add(b);}
            @Override public ItemStack insertFrom(ItemStack stack,GlobalPos source){from.add(source);to.add(at(chestPos));return insert(stack);}
        };
        var player=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"craft-route"));var service=new CraftingService(access);
        h.assertTrue(service.request(player,new ItemStack(Items.IRON_TRAPDOOR),1).accepted(),"Recipe accepted");h.assertTrue(from.isEmpty(),"Reservation has no pretend flight into the Nexus");
        awaitPlanning(service);for(int i=0;i<90;i++)service.tick();
        h.assertTrue(service.activeJobs()==0&&access.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_TRAPDOOR)),0L)==1,"Actual table produces exactly one output");
        h.assertTrue(from.equals(List.of(access.at(chestPos),access.at(table)))&&to.equals(List.of(access.at(table),access.at(chestPos))),"Only chest-to-table ingredients and table-to-chest output are animated: "+from+" -> "+to);h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=500)
    public static void queuedCalculationShowsTaggedShortagesAndRetriesWithNewStock(GameTestHelper h){
        var chestPos=new BlockPos(3,2,3);var table=chestPos.east(2);h.setBlock(chestPos,Blocks.CHEST);h.setBlock(table,Blocks.CRAFTING_TABLE);
        Container chest=(Container)h.getBlockEntity(chestPos);var target=new ItemStack(Items.CHEST);
        var access=new Fixture(h,chestPos,chest,List.of(table),target){
            @Override public Map<ItemKey,Long> snapshot(){h.assertTrue(h.getLevel().getServer().isSameThread(),"Worker never polls a live inventory");return super.snapshot();}
        };
        var player=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"async-craft"));var service=new CraftingService(access);
        var request=service.request(player,target,1);h.assertTrue(request.accepted()&&service.visibleStatuses(player.getUUID()).getFirst().state().equals("CALCULATING"),"Accepted job is immediately visible as calculating");
        h.onEachTick(service::tick);
        h.startSequence().thenWaitUntil(()->h.assertTrue(service.statuses().stream().anyMatch(j->j.state().equals("MISSING")),"Worker returned a shortage"))
            .thenExecute(()->{
                var job=service.visibleStatuses(player.getUUID()).getFirst();h.assertTrue(job.id().equals(request.jobId())&&job.missing().size()==1&&job.missing().getFirst().tag().equals("minecraft:planks")&&job.missing().getFirst().count()==8,"Shortage keeps the recipe's any-plank tag and amount: "+job.missing());
                chest.setItem(0,new ItemStack(Items.BIRCH_PLANKS,8));
            }).thenWaitUntil(()->h.assertTrue(service.activeJobs()==0,"New stock resumes the same job"))
            .thenExecute(()->h.assertTrue(access.snapshot().getOrDefault(new ItemKey(target),0L)==1&&!access.snapshot().containsKey(new ItemKey(new ItemStack(Items.BIRCH_PLANKS))),"Actual table accepts the alternate tag member exactly once")).thenSucceed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void furnaceInitialAndReplacementFuelUseTheProcessorDestination(GameTestHelper h){
        var chestPos=new BlockPos(2,2,2);var furnace=chestPos.east(3);h.setBlock(chestPos,Blocks.CHEST);h.setBlock(furnace,Blocks.FURNACE);
        Container chest=(Container)h.getBlockEntity(chestPos);chest.setItem(0,new ItemStack(Items.COAL,2));
        List<GlobalPos> destinations=new ArrayList<>();
        var access=new Fixture(h,chestPos,chest,List.of(furnace),new ItemStack(Items.IRON_INGOT)){
            @Override public ItemStack extractForDelivery(ItemKey key,int count,GlobalPos destination,java.util.function.BiConsumer<GlobalPos,ItemStack> receipt){destinations.add(destination);ItemStack stack=extract(key,count);if(!stack.isEmpty())receipt.accept(origin(),stack);return stack;}
        };
        var adapter=new com.cappleapple.astralrepository.crafting.VanillaProcessingAdapter();
        var recipe=adapter.recipes(access).stream().filter(r->r.id().equals("minecraft:iron_ingot_from_smelting_raw_iron")).findFirst().orElseThrow();
        var node=new com.cappleapple.astralrepository.crafting.CraftPlan.Node<>(0,recipe,List.of(new com.cappleapple.astralrepository.crafting.CraftPlan.Selection<>(new ItemKey(new ItemStack(Items.RAW_IRON)),1)),Set.of());
        var operation=adapter.start(access,access.at(furnace),node);h.assertTrue(operation!=null,"Vanilla furnace accepts the recipe");
        h.assertTrue(((Container)h.getBlockEntity(furnace)).getItem(1).isEmpty()&&operation.recoverable().getOrDefault(new ItemKey(new ItemStack(Items.COAL)),0L)==1,"Fuel remains recoverable in transit and cannot burn early");
        for(int i=0;i<access.travelTicks(access.origin(),access.at(furnace));i++)operation.poll();
        h.assertTrue(((Container)h.getBlockEntity(furnace)).getItem(1).is(Items.COAL),"Fuel enters its slot at arrival");
        ((Container)h.getBlockEntity(furnace)).removeItem(1,1);operation.poll();
        h.assertTrue(((Container)h.getBlockEntity(furnace)).getItem(1).isEmpty(),"Replacement fuel also waits in transit");
        for(int i=0;i<access.travelTicks(access.origin(),access.at(furnace));i++)operation.poll();
        h.assertTrue(destinations.equals(List.of(access.at(furnace),access.at(furnace))),"Both startup and replacement fuel go directly to the active furnace");
        h.assertTrue(chest.getItem(0).isEmpty()&&((Container)h.getBlockEntity(furnace)).getItem(1).is(Items.COAL),"Fuel is physically owned by the furnace");operation.cancel();
        chest.setItem(0,new ItemStack(Items.COAL));operation=adapter.start(access,access.at(furnace),node);
        var refund=operation.cancel();h.assertTrue(refund.getOrDefault(new ItemKey(new ItemStack(Items.COAL)),0L)==1&&refund.getOrDefault(new ItemKey(new ItemStack(Items.RAW_IRON)),0L)==1&&operation.cancel().isEmpty(),"Cancellation refunds pending fuel and arrived input exactly once");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void workstationPopulatesArrivalsBeforeCraftingAndTransitCancellationConservesItems(GameTestHelper h){
        var near=new BlockPos(2,2,2);var far=new BlockPos(7,2,2);var table=new BlockPos(4,2,5);
        h.setBlock(near,Blocks.BARREL);h.setBlock(far,Blocks.BARREL);h.setBlock(table,Blocks.CRAFTING_TABLE);
        Container a=(Container)h.getBlockEntity(near),b=(Container)h.getBlockEntity(far);a.setItem(0,new ItemStack(Items.IRON_INGOT,2));b.setItem(0,new ItemStack(Items.IRON_INGOT,2));
        List<Integer> populations=new ArrayList<>();int[] crafting={0};
        var access=new Fixture(h,near,a,List.of(table),new ItemStack(Items.IRON_TRAPDOOR)){
            @Override public Map<ItemKey,Long> snapshot(){var stock=new HashMap<>(super.snapshot());if(!b.getItem(0).isEmpty())stock.merge(new ItemKey(b.getItem(0)),(long)b.getItem(0).getCount(),Long::sum);return stock;}
            @Override public ItemStack reserve(ItemKey key,int count,java.util.function.BiConsumer<GlobalPos,ItemStack> receipt){var first=extract(key,count);if(!first.isEmpty())receipt.accept(at(near),first);var second=b.removeItem(0,count-first.getCount());if(!second.isEmpty())receipt.accept(at(far),second);return key.sample().copyWithCount(first.getCount()+second.getCount());}
            @Override public int travelTicks(GlobalPos from,GlobalPos to){return from.equals(at(near))?10:30;}
            @Override public void stagingVisual(GlobalPos at,List<ItemStack> grid,int duration){populations.add((int)grid.stream().filter(stack->!stack.isEmpty()).count());}
            @Override public void craftingVisual(GlobalPos at,List<ItemStack> grid,ItemStack output,int duration){crafting[0]++;}
        };
        var service=new CraftingService(access);var player=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"craft-arrivals"));
        var request=service.request(player,new ItemStack(Items.IRON_TRAPDOOR),1);h.assertTrue(request.accepted(),"Arrival job accepted");awaitPlanning(service);service.tick();
        for(int i=0;i<9;i++)service.tick();h.assertTrue(populations.isEmpty()&&crafting[0]==0,"Nothing populates or starts before the first arrival");
        service.tick();h.assertTrue(populations.equals(List.of(2))&&crafting[0]==0,"Two near ingredients populate while farther ingredients are still travelling");
        for(int i=0;i<19;i++)service.tick();h.assertTrue(crafting[0]==0,"Crafting waits for the final delivery");
        service.tick();h.assertTrue(crafting[0]==1&&service.activeJobs()==1,"Crafting begins at the last arrival, not at dispatch");
        for(int i=0;i<40;i++)service.tick();h.assertTrue(access.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_TRAPDOOR)),0L)==1,"Delayed craft produces one actual result");
        a.clearContent();b.clearContent();a.setItem(0,new ItemStack(Items.IRON_INGOT,2));b.setItem(0,new ItemStack(Items.IRON_INGOT,2));
        var cancelled=service.request(player,new ItemStack(Items.IRON_TRAPDOOR),1);awaitPlanning(service);service.tick();for(int i=0;i<12;i++)service.tick();
        h.assertTrue(service.cancel(cancelled.jobId())&&access.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)),0L)==4,"Cancellation returns both arrived and in-flight ingredients exactly once");
        h.assertTrue(!service.cancel(cancelled.jobId()),"Repeated cancellation cannot duplicate delivery escrow");h.succeed();
    }
    /** Keep existing processor-timing assertions separate from asynchronous planning latency. */
    static void awaitPlanning(CraftingService service){
        try{
            var tick=CraftingService.class.getDeclaredMethod("tickPlanning");tick.setAccessible(true);
            long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            do{tick.invoke(service);if(service.statuses().stream().noneMatch(j->j.state().equals("CALCULATING")))return;Thread.sleep(1);}while(System.nanoTime()<until);
            throw new AssertionError("Craft calculation timed out");
        }catch(ReflectiveOperationException|InterruptedException failure){throw new AssertionError(failure);}
    }
    static class Fixture implements CraftingService.NetworkAccess {
        final GameTestHelper helper; final BlockPos origin; final Container chest; final List<BlockPos> processors; final ItemStack target;
        int tableAnimations; boolean correctGrid;
        Fixture(GameTestHelper helper, BlockPos origin, Container chest, List<BlockPos> processors, ItemStack target) {
            this.helper = helper; this.origin = origin; this.chest = chest; this.processors = processors; this.target = target;
        }
        GlobalPos at(BlockPos relative) { return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relative)); }
        public net.minecraft.server.level.ServerLevel level() { return helper.getLevel(); }
        public Map<ItemKey, Long> snapshot() {
            Map<ItemKey, Long> result = new HashMap<>();
            for (int i = 0; i < chest.getContainerSize(); i++) { ItemStack stack = chest.getItem(i); if (!stack.isEmpty()) result.merge(new ItemKey(stack), (long)stack.getCount(), Long::sum); }
            return result;
        }
        public ItemStack extract(ItemKey key, int count) {
            ItemStack result = key.sample().copyWithCount(0);
            for (int i = 0; i < chest.getContainerSize() && result.getCount() < count; i++)
                if (!chest.getItem(i).isEmpty() && key.equals(new ItemKey(chest.getItem(i)))) {
                    ItemStack extracted = chest.removeItem(i, count - result.getCount());
                    if (result.isEmpty()) result = extracted; else result.grow(extracted.getCount());
                }
            return result;
        }
        public ItemStack insert(ItemStack input) {
            ItemStack remainder = input.copy();
            for (int i = 0; i < chest.getContainerSize() && !remainder.isEmpty(); i++) {
                ItemStack present = chest.getItem(i);
                if (!present.isEmpty() && !ItemStack.isSameItemSameComponents(present, remainder)) continue;
                int amount = Math.min(remainder.getCount(), remainder.getMaxStackSize() - present.getCount());
                if (present.isEmpty()) chest.setItem(i, remainder.copyWithCount(amount)); else present.grow(amount);
                remainder.shrink(amount);
            }
            chest.setChanged(); return remainder;
        }
        public List<GlobalPos> workstations() { return processors.stream().map(this::at).toList(); }
        public List<ItemStack> exposedProducts() { return List.of(target.copy()); }
        public GlobalPos origin() { return at(origin); }
        public void visual(GlobalPos from, GlobalPos to, ItemStack stack, int ticks) {}
        public void craftingVisual(GlobalPos table, List<ItemStack> grid, ItemStack output, int ticks) {
            tableAnimations++;
            correctGrid = grid.size() == 9 && grid.get(0).is(Items.IRON_INGOT) && grid.get(1).is(Items.IRON_INGOT)
                    && grid.get(3).is(Items.IRON_INGOT) && grid.get(4).is(Items.IRON_INGOT) && grid.get(2).isEmpty();
        }
    }
}
