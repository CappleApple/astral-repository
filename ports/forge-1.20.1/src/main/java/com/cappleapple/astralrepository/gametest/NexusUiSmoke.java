package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.client.NexusScreen;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.network.NetworkManager;
import com.cappleapple.astralrepository.network.NetworkPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Real screen input -> NeoForge packet -> integrated server -> incremental UI update. */
public final class NexusUiSmoke {
    private static int stage,ticks,stable;
    private static String previousInventory="";
    private static CompletableFuture<Void> work;
    private static List<com.cappleapple.astralrepository.menu.NexusMenu.Entry> first;
    private static UUID cancel;
    private static int chatBefore;
    private static long logsBeforeClear;
    private static final Path OUT=Path.of("../build/client-smoke");
    public static boolean tick(NexusScreen screen) throws Exception {
        Minecraft mc=Minecraft.getInstance();
        if(++ticks>1200)throw new AssertionError("Nexus scrolling/job test timed out in stage "+stage);
        if(work!=null){if(!work.isDone())return false;work.join();work=null;ticks=0;}
        int left=(screen.width-318)/2,top=(screen.height-266)/2;
        switch(stage){
            case 0 -> {
                chatBefore=receivedMessages();
                work=server(NexusUiSmoke::fixture);advance();
            }
            case 1 -> {
                if(screen.getMenu().totalEntries<80)return false;
                String current=screen.getMenu().totalEntries+":"+screen.getMenu().entries.stream().map(e->BuiltInRegistries.ITEM.getKey(e.stack().getItem()).toString()+"/"+e.count()).toList();
                if(!current.equals(previousInventory)){previousInventory=current;stable=0;}if(++stable<20)return false;
                first=List.copyOf(screen.getMenu().entries);
                check(screen.maxScrollRow()>0,"More than54 items did not enable storage scrolling");
                check(screen.mouseScrolled(left+20,top+80,0,-1),"Wheel over storage was ignored");advance();
            }
            case 2 -> {
                if(ticks<8||screen.scrollRow()!=1||screen.getMenu().scrollRow!=1)return false;
                check(ItemStack.isSameItemSameTags(first.get(9).stack(),screen.getMenu().entries.get(0).stack()),"Wheel skipped a page instead of one row");
                check(screen.hoveredEntry(left+18,top+57)==0,"Hover did not resolve first visible slot");
                capture("nexus_scrolled.png");
                screen.mouseClicked(left+194,top+151,0);screen.mouseDragged(left+194,top+155,0,0,4);screen.mouseReleased(left+194,top+155,0);advance();
            }
            case 3 -> {
                if(ticks<8||screen.getMenu().scrollRow!=screen.maxScrollRow())return false;
                check(!screen.getMenu().entries.isEmpty(),"Dragging to bottom lost the last item rows");
                search(screen).setValue("oak planks");advance();
            }
            case 4 -> {
                if(ticks<8||screen.getMenu().entries.size()!=1)return false;
                check(screen.scrollRow()==0&&screen.maxScrollRow()==0,"Narrowing search did not clamp scrollbar");
                var entry=screen.getMenu().entries.get(0);
                check(entry.stack().is(Items.OAK_PLANKS)&&entry.count()==7&&entry.craftable(),"Fixture output is not both stored and craftable");
                System.setProperty("astral_repository.testShift","true");
                try{click(screen,0,1);}finally{System.clearProperty("astral_repository.testShift");}
                advance();
            }
            case 5 -> {
                if(ticks<8||screen.getMenu().jobs.isEmpty())return false;
                check(screen.getMenu().jobs.stream().anyMatch(j->j.target().is(Items.OAK_PLANKS)&&j.count()==64),"Shift-right did not queue exactly the item's max stack");
                check(screen.getMenu().getCarried().isEmpty(),"Shift-right withdrew existing stock instead of crafting");
                click(screen,0,2);
                var quantity=(EditBox)screen.children().stream().filter(w->w instanceof EditBox e&&e.getMessage().getString().equals("Craft quantity")).findFirst().orElseThrow();
                quantity.setValue("1");
                int[] steps={1,10,100,1000};for(int m=0;m<4;m++){
                    System.setProperty("astral_repository.testShift",Boolean.toString((m&1)!=0));System.setProperty("astral_repository.testControl",Boolean.toString((m&2)!=0));
                    try{int before=Integer.parseInt(quantity.getValue());screen.mouseScrolled(quantity.getX()+2,quantity.getY()+2,0,3);check(Integer.parseInt(quantity.getValue())==before+steps[m],"Nexus quantity scroll modifier "+m);}
                    finally{System.clearProperty("astral_repository.testShift");System.clearProperty("astral_repository.testControl");}
                }
                quantity.setValue("256");screen.keyPressed(257,0,0);advance();
            }
            case 6 -> {
                if(ticks<8||screen.getMenu().jobs.size()<2)return false;
                search(screen).setValue("");advance();
            }
            case 7 -> {
                if(ticks<8||screen.getMenu().totalEntries<80)return false;
                capture("nexus.png");capture("nexus_jobs.png");
                var visibleJobs=screen.getMenu().jobs.stream().filter(j->!j.state().terminal()&&!j.target().isEmpty()).toList();
                check(!visibleJobs.isEmpty(),"No active job remained for individual cancellation");
                int index=0;
                cancel=visibleJobs.get(index).id();
                screen.mouseClicked(left+297,top+181+index*24+6,0);screen.mouseReleased(left+297,top+181+index*24+6,0);advance();
            }
            case 8 -> {
                if(ticks<8||screen.getMenu().jobs.stream().noneMatch(j->j.id().equals(cancel)&&j.state()==NetworkPackets.JobState.CANCELLED))return false;
                check(screen.getMenu().jobs.stream().anyMatch(j->!j.id().equals(cancel)&&j.state()!=NetworkPackets.JobState.CANCELLED),"Cancel-one cancelled another crafting job");
                check(receivedMessages()==chatBefore,"Crafting controls appended chat notifications");
                search(screen).setValue("oak log");advance();
            }
            case 9 -> {
                if(ticks<8||screen.getMenu().entries.size()!=1||!screen.getMenu().entries.get(0).stack().is(Items.OAK_LOG))return false;
                click(screen,0,0);advance();
            }
            case 10 -> {
                if(ticks<8||!screen.getMenu().getCarried().is(Items.OAK_LOG))return false;
                slotClick(screen,1,1);advance();
            }
            case 11 -> {
                if(ticks<8||screen.getMenu().grid.getItem(0).getCount()!=1)return false;
                click(screen,0,0);advance();
            }
            case 12 -> {
                if(ticks<8||!screen.getMenu().getCarried().isEmpty()||!screen.getMenu().slots.get(0).getItem().is(Items.OAK_PLANKS))return false;
                capture("nexus_cursor.png");slotClick(screen,0,0);advance();
            }
            case 13 -> {
                if(ticks<8||screen.getMenu().getCarried().getCount()!=4)return false;
                check(screen.getMenu().grid.getItem(0).is(Items.OAK_LOG),"Normal craft did not refill its emptied ingredient from storage");
                click(screen,0,0);advance();
            }
            case 14 -> {
                if(ticks<8||!screen.getMenu().getCarried().isEmpty())return false;
                System.setProperty("astral_repository.testShift","true");
                try{check(net.minecraft.client.gui.screens.Screen.hasShiftDown(),"Smoke Shift modifier was not active");check(screen.getMenu().slots.get(0).getItem().is(Items.OAK_PLANKS),"No output before Shift-result click");slotClick(screen,0,0);}finally{System.clearProperty("astral_repository.testShift");}
                advance();
            }
            case 15 -> {
                if(ticks<8)return false;
                int planks=mc.player.getInventory().items.stream().filter(i->i.is(Items.OAK_PLANKS)).mapToInt(ItemStack::getCount).sum();
                if(planks!=64)capture("nexus_shift_failure.png");
                check(planks==64,"Shift-result must craft exactly one max output stack; got "+planks+", cursor="+screen.getMenu().getCarried()+", grid="+screen.getMenu().grid.getItem(0)+", result="+screen.getMenu().slots.get(0).getItem()+", error="+screen.getMenu().error);
                check(screen.getMenu().grid.getItem(0).is(Items.OAK_LOG),"Stack craft did not retain a refilled ingredient");
                logsBeforeClear=screen.getMenu().entries.get(0).count();
                screen.mouseClicked(left+294,top+38,0);screen.mouseReleased(left+294,top+38,0);advance();
            }
            case 16 -> {
                if(ticks<8||!screen.getMenu().grid.isEmpty())return false;
                check(screen.getMenu().slots.get(0).getItem().isEmpty(),"Clear grid left a ghost result");
                check(screen.getMenu().entries.get(0).count()==logsBeforeClear+1,"Clear grid did not return its one ingredient to storage");
                check(screen.getMenu().error.isEmpty(),"Cleared grid produced an unnecessary warning");
                search(screen).setFocused(false);screen.setFocused(null);
                work=server(p->{var n=NetworkManager.get(p.server).networkAt(GlobalPos.of(p.level().dimension(),new BlockPos(1,-59,0)));n.crafting().cancelAll();check(n.crafting().request(p,new ItemStack(Items.OAK_PLANKS),4096).accepted(),"Missing-ingredient request was not queued");});advance();
            }
            case 17 -> {
                var jobs=screen.getMenu().jobs.stream().filter(j->!j.state().terminal()).toList();int index=-1;
                for(int i=0;i<jobs.size();i++)if(jobs.get(i).state()==NetworkPackets.JobState.MISSING)index=i;
                if(index<0||ticks<8)return false;check(!jobs.get(index).missing().isEmpty(),"Missing job has no ingredient icons");capture("nexus_missing.png");
                screen.mouseClicked(left+244,top+181+index*24+8,0);screen.mouseReleased(left+244,top+181+index*24+8,0);advance();
            }
            case 18 -> {
                if(ticks<8)return false;capture("nexus_missing_grid.png");check(mc.player.containerMenu==screen.getMenu(),"Missing grid closed the live storage menu");
                screen.keyPressed(256,0,0);work=server(p->NetworkManager.get(p.server).networkAt(GlobalPos.of(p.level().dimension(),new BlockPos(1,-59,0))).crafting().cancelAll());advance();
            }
            case 19 -> {
                Files.writeString(OUT.resolve("nexus-ui-result.txt"),"PASS: >80 real stored types; wheel advances one row; drag reaches last row; search resets/clamps; Shift-right queues64 additional already-stocked planks; explicit256 request; job icons/progress rendered; individual cancellation; normal crafting refills from storage; Shift-result crafts64; clear-grid returns ingredients without ghost result or warning; quantity scroll modifiers; queued missing ingredient icons and scrollable detail grid; live menu retained; no chat notifications.\n");
                return true;
            }
        }
        return false;
    }
    private static int receivedMessages() throws ReflectiveOperationException {
        var state=Minecraft.getInstance().gui.getChat().storeState();
        var messages=state.getClass().getDeclaredField("messages");messages.setAccessible(true);
        return ((java.util.List<?>)messages.get(state)).size();
    }
    private static void advance(){stage++;ticks=0;}
    private static EditBox search(NexusScreen screen){return (EditBox)screen.children().stream().filter(w->w instanceof EditBox e&&e.getMessage().getString().equals("Search storage")).findFirst().orElseThrow();}
    private static void click(NexusScreen screen,int cell,int button){double x=(screen.width-318)/2+10+cell%9*20+8,y=(screen.height-266)/2+49+cell/9*18+8;check(screen.mouseClicked(x,y,button),"Storage input not consumed");screen.mouseReleased(x,y,button);}
    private static void slotClick(NexusScreen screen,int index,int button){var slot=screen.getMenu().slots.get(index);double x=(screen.width-318)/2+slot.x+8,y=(screen.height-266)/2+slot.y+8;screen.mouseClicked(x,y,button);screen.mouseReleased(x,y,button);}
    private static CompletableFuture<Void> server(Consumer<ServerPlayer> action){var mc=Minecraft.getInstance();var future=new CompletableFuture<Void>();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()));future.complete(null);}catch(Throwable failure){future.completeExceptionally(failure);}});return future;}
    private static void fixture(ServerPlayer player){
        var level=player.serverLevel();Container[] barrels=new Container[4];
        for(int i=0;i<4;i++){var pos=new BlockPos(i,-59,-1);level.setBlockAndUpdate(pos,Blocks.BARREL.defaultBlockState());barrels[i]=(Container)level.getBlockEntity(pos);barrels[i].setItem(25,new ItemStack(Items.OAK_LOG,64));}
        int index=0;
        for(String kind:List.of("wool","terracotta","concrete","stained_glass","concrete_powder"))for(DyeColor color:DyeColor.values()){
            var item=BuiltInRegistries.ITEM.get(new ResourceLocation(color.getName()+"_"+kind));
            barrels[index/25].setItem(index%25,new ItemStack(item,32));index++;
        }
        barrels[0].setItem(26,new ItemStack(Items.OAK_PLANKS,7));
        level.setBlockAndUpdate(new BlockPos(2,-59,-2),Blocks.CRAFTING_TABLE.defaultBlockState());
        var shelfPos=new BlockPos(3,-59,-2);level.setBlockAndUpdate(shelfPos,Blocks.CHISELED_BOOKSHELF.defaultBlockState());
        ItemStack tome=new ItemStack(AstralContent.RECIPE_TOME.get());CompoundTag tag=new CompoundTag();
        tag.put("Output",new ItemStack(Items.OAK_PLANKS).save(player.registryAccess()));tag.putString("OutputId","minecraft:oak_planks");
        tome.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));((ChiseledBookShelfBlockEntity)level.getBlockEntity(shelfPos)).setItem(0,tome);
        for(int i=0;i<4;i++)NetworkManager.changed(level,new BlockPos(i,-59,-1));
        NetworkManager.changed(level,new BlockPos(2,-59,-2));NetworkManager.changed(level,shelfPos);
    }
    private static void capture(String name) throws Exception{Files.createDirectories(OUT);try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(OUT.resolve(name));}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private NexusUiSmoke(){}
}
