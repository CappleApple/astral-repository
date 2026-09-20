package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.network.*;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import com.cappleapple.astralrepository.platform.CustomPacketPayload;
import net.minecraft.server.network.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class NexusProtocolGameTests {
    private record Fixture(NexusMenu menu,FakePlayer player,Container chest,GlobalPos shelf,List<CustomPacketPayload> packets) {}
    private static Fixture fixture(GameTestHelper h,int id) {
        BlockPos nexus=new BlockPos(5,2,5),chest=nexus.west(),shelf=nexus.north();
        h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());h.setBlock(chest,Blocks.CHEST);h.setBlock(shelf,Blocks.CHISELED_BOOKSHELF);h.setBlock(nexus.east(),Blocks.CRAFTING_TABLE);
        ItemStack tome=new ItemStack(AstralContent.RECIPE_TOME.get());CompoundTag data=new CompoundTag();
        data.put("Output",new ItemStack(Items.OAK_PLANKS).save(h.getLevel().registryAccess()));data.putString("OutputId","minecraft:oak_planks");tome.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
        ((Container)h.getBlockEntity(shelf)).setItem(0,tome);
        FakePlayer player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"nexus-protocol"));BlockPos absolute=h.absolutePos(nexus);
        player.setPos(absolute.getX()+.5,absolute.getY(),absolute.getZ()+.5);
        List<CustomPacketPayload> packets=new ArrayList<>();
        player.connection=new ServerGamePacketListenerImpl(player.getServer(),player.connection.getConnection(),player,CommonListenerCookie.createInitial(player.getGameProfile(),false)){
            @Override public void send(Packet<?> packet){if(packet instanceof ClientboundCustomPayloadPacket custom&&(custom.payload() instanceof NetworkPackets.Page||custom.payload() instanceof NetworkPackets.Delta))packets.add(custom.payload());}
            @Override public void send(Packet<?> packet,PacketSendListener listener){send(packet);}
        };
        var menu=new NexusMenu(id,player.getInventory(),GlobalPos.of(h.getLevel().dimension(),absolute),false);player.containerMenu=menu;
        return new Fixture(menu,player,(Container)h.getBlockEntity(chest),GlobalPos.of(h.getLevel().dimension(),h.absolutePos(shelf)),packets);
    }
    private static ItemStack named(int i){ItemStack item=new ItemStack(Items.STONE,10);item.set(DataComponents.CUSTOM_NAME,Component.literal(String.format(Locale.ROOT,"Entry %03d",i)));return item;}
    private static void search(NexusMenu menu,int row,String query){menu.action(new NetworkPackets.Action(menu.containerId,NetworkPackets.SEARCH,ItemStack.EMPTY,0,row,query));}
    private static List<NexusMenu.Entry> rows(){return java.util.stream.IntStream.range(0,54).mapToObj(i->new NexusMenu.Entry(named(i).copyWithCount(1),10,false)).toList();}
    private static FriendlyByteBuf buffer(GameTestHelper h){return new FriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());}

    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void countAndJobDeltasPreserveComponentsAndRejectStaleBaselines(GameTestHelper h) {
        UUID id=UUID.randomUUID();ItemStack output=new ItemStack(Items.OAK_PLANKS);var originalRows=rows();
        var waiting=new NetworkPackets.Job(id,output,64,NetworkPackets.JobState.WAITING,0,16,0,"");
        var first=new NetworkPackets.Page(84,1,0,90,"",originalRows,List.of(waiting));
        List<NexusMenu.Entry> changed=new ArrayList<>(originalRows);changed.set(17,new NexusMenu.Entry(originalRows.get(17).stack(),9,false));
        var next=new NetworkPackets.Page(84,2,0,90,"",changed,List.of(waiting));
        var payload=NetworkPackets.difference(first,next,false);
        h.assertTrue(payload instanceof NetworkPackets.Delta,"One changed count produces a delta");var delta=(NetworkPackets.Delta)payload;
        h.assertTrue(delta.counts().size()==1&&delta.counts().get(0).slot()==17&&delta.jobChanges().isEmpty()&&delta.jobOrder()==null&&delta.error()==null,"Count delta carries only one slot/count/flag update");
        var encoded=buffer(h);NetworkPackets.Delta.CODEC.encode(encoded,delta);int bytes=encoded.readableBytes();
        var decoded=NetworkPackets.Delta.CODEC.decode(encoded);encoded.release();h.assertTrue(bytes<40,"Count update uses fewer than 40 bytes and contains no item-component payload: "+bytes);
        var reconstructed=NetworkPackets.reconstruct(first,decoded);h.assertTrue(reconstructed!=null&&reconstructed.entries().get(17).count()==9&&ItemStack.isSameItemSameTags(reconstructed.entries().get(17).stack(),originalRows.get(17).stack()),"Wire round-trip retains original exact named-item identity");
        h.assertTrue(NetworkPackets.reconstruct(reconstructed,decoded)==null,"A repeated delta cannot apply twice");
        var anotherSearch=new NetworkPackets.Page(84,3,0,90,"",rows(),List.of(waiting));
        h.assertTrue(NetworkPackets.reconstruct(anotherSearch,decoded)==null,"An older delta cannot mutate a newer search with the same row and size");
        var running=new NetworkPackets.Job(id,output,64,NetworkPackets.JobState.RUNNING,3,16,2,"");
        var progress=new NetworkPackets.Page(84,3,0,90,"",changed,List.of(running));
        var jobDelta=(NetworkPackets.Delta)NetworkPackets.difference(next,progress,false);
        h.assertTrue(jobDelta.counts().isEmpty()&&jobDelta.jobChanges().size()==1&&jobDelta.jobChanges().get(0).target().isEmpty(),"Progress reuses its known UUID target and sends no storage or item identity");
        var jobBuffer=buffer(h);NetworkPackets.Delta.CODEC.encode(jobBuffer,jobDelta);var jobCopy=NetworkPackets.Delta.CODEC.decode(jobBuffer);jobBuffer.release();
        var completed=NetworkPackets.reconstruct(next,jobCopy);h.assertTrue(completed!=null&&completed.jobs().get(0).completed()==3&&completed.jobs().get(0).target().is(Items.OAK_PLANKS),"Progress codec restores the known target");
        h.assertTrue(NetworkPackets.difference(next,next,false)==null,"Identical observable state produces no packet");
        h.assertTrue(NetworkPackets.difference(next,new NetworkPackets.Page(84,3,0,90,"",changed,List.of(waiting)),true) instanceof NetworkPackets.Page,"A search transition establishes a new complete baseline even when the identities happen to match");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=700)
    public static void actualMenuCoalescesScrollingAndSendsNothingForIdleShelfPolling(GameTestHelper h) {
        var f=fixture(h,85);List<Container> containers=new ArrayList<>();containers.add(f.chest());
        for(BlockPos pos:List.of(new BlockPos(5,2,7),new BlockPos(7,2,5))){h.setBlock(pos,Blocks.CHEST);containers.add((Container)h.getBlockEntity(pos));}
        for(int i=0;i<80;i++)containers.get(i/27).setItem(i%27,named(i));
        h.startSequence().thenWaitUntil(()->{
            var network=f.menu().network();h.assertTrue(network!=null&&network.snapshot().size()==80&&network.exposedProducts().size()==1,"All named stacks and the actual bookshelf are discovered");
        }).thenExecute(()->{
            f.menu().sendPage();h.assertTrue(f.packets().size()==1&&f.packets().get(0) instanceof NetworkPackets.Page,"Opening sends one complete bounded window");
            f.packets().clear();long revision=f.menu().network().menuStorageVersion();
            for(int i=0;i<20;i++)f.menu().sendPage();
            h.assertTrue(f.packets().isEmpty(),"Repeated idle broadcasts send zero inventory packets");
            h.runAfterDelay(45,()->{
                h.assertTrue(f.menu().network().menuStorageVersion()==revision,"Unchanged provider and bookshelf polling do not advance inventory/library version");
                f.menu().sendPage();h.assertTrue(f.packets().isEmpty(),"Crossing the old 40-tick clock boundary sends no idle packet");
                var taken=f.menu().network().extractAt(new ItemKey(named(0)),1,f.menu().origin);h.assertTrue(taken.getCount()==1,"A real provider extraction changed one count");
                f.menu().sendPage();h.assertTrue(f.packets().size()==1&&f.packets().get(0) instanceof NetworkPackets.Delta delta&&delta.counts().size()==1,"Real indexed count mutation sends exactly one count delta");
                f.packets().clear();search(f.menu(),1,"");search(f.menu(),2,"");search(f.menu(),Integer.MAX_VALUE,"");
                h.assertTrue(f.packets().isEmpty(),"A burst of scroll messages is coalesced before broadcast");f.menu().sendPage();
                h.assertTrue(f.packets().size()==1&&f.menu().scrollRow==3&&f.menu().entries.size()==54,"Final scroll request is clamped to row three with a six-row window");
                f.packets().clear();search(f.menu(),0,"");f.menu().sendPage();var top=f.menu().currentPage();
                f.packets().clear();search(f.menu(),1,"");f.menu().sendPage();
                h.assertTrue(ItemStack.isSameItemSameTags(f.menu().entries.get(0).stack(),top.entries().get(9).stack()),"Scrolling one row advances nine entries, not a page");
                f.packets().clear();search(f.menu(),999,"Entry 079");f.menu().sendPage();
                h.assertTrue(f.menu().scrollRow==0&&f.menu().totalEntries==1&&f.menu().entries.get(0).stack().getHoverName().getString().equals("Entry 079"),"Search clamps the old scroll position and returns the actual named resource");
                f.menu().action(new NetworkPackets.Action(85,NetworkPackets.SEARCH,ItemStack.EMPTY,0,0,"Entry 078",10));
                f.menu().action(new NetworkPackets.Action(85,NetworkPackets.SEARCH,ItemStack.EMPTY,0,0,"Entry 079",9));
                f.menu().sendPage();h.assertTrue(f.menu().currentPage().request()==10&&f.menu().entries.get(0).stack().getHoverName().getString().equals("Entry 078"),"A stale request cannot replace the latest query even at the same row");h.succeed();
            });
        });
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void stackRequestsCraftAdditionalOutputsAndCancelOnlyTheOwnedJob(GameTestHelper h) {
        var f=fixture(h,86);f.chest().setItem(0,new ItemStack(Items.OAK_LOG,64));f.chest().setItem(1,new ItemStack(Items.OAK_PLANKS,7));
        ItemKey logs=new ItemKey(new ItemStack(Items.OAK_LOG)),planks=new ItemKey(new ItemStack(Items.OAK_PLANKS));
        h.startSequence().thenWaitUntil(()->{
            var n=f.menu().network();h.assertTrue(n!=null&&n.snapshot().getOrDefault(logs,0L)==64&&n.exposedProducts().size()==1&&n.workstations().stream().anyMatch(p->h.getLevel().getBlockState(p.pos()).is(Blocks.CRAFTING_TABLE)),"Actual crafting table, inputs and bookshelf are discovered");
        }).thenExecute(()->{
            for(int invalid:new int[]{0,-1,4097})f.menu().action(new NetworkPackets.Action(86,NetworkPackets.CRAFT,new ItemStack(Items.OAK_PLANKS),invalid,0,""));
            h.assertTrue(f.menu().network().crafting().activeJobs()==0&&f.menu().network().snapshot().getOrDefault(logs,0L)==64,"Invalid explicit quantities cannot create jobs or reserve ingredients");
            f.menu().action(new NetworkPackets.Action(86,NetworkPackets.CRAFT_STACK,new ItemStack(Items.OAK_PLANKS,99),Integer.MAX_VALUE,0,""));
            CraftingGameTests.awaitPlanning(f.menu().network().crafting());
            var service=f.menu().network().crafting();var own=service.visibleStatuses(f.player().getUUID());
            h.assertTrue(own.size()==1&&own.get(0).count()==64&&own.get(0).total()==16,"Shift-right uses the server's 64-item maximum and plans sixteen real recipes");
            UUID first=own.get(0).id();h.assertTrue(f.menu().network().snapshot().getOrDefault(planks,0L)==7&&f.menu().network().snapshot().getOrDefault(logs,0L)==48,"Seven existing outputs stay in storage while sixteen logs enter physical escrow");
            f.menu().action(new NetworkPackets.Action(86,NetworkPackets.CRAFT,new ItemStack(Items.OAK_PLANKS),4,0,""));CraftingGameTests.awaitPlanning(service);
            h.assertTrue(service.activeJobs()==2,"A second independently cancellable job exists");
            h.assertTrue(!service.cancel(first,UUID.randomUUID())&&service.activeJobs()==2,"Another owner cannot cancel the request");
            f.menu().cancelJob(new NetworkPackets.CancelJob(87,first));h.assertTrue(service.activeJobs()==2,"A stale menu ID cannot cancel a request");
            f.menu().cancelJob(new NetworkPackets.CancelJob(86,first));
            h.assertTrue(service.activeJobs()==1&&f.menu().network().snapshot().getOrDefault(logs,0L)==63,"The selected request alone is cancelled and its exact escrow is returned");
            f.menu().cancelJob(new NetworkPackets.CancelJob(86,first));h.assertTrue(service.activeJobs()==1&&f.menu().network().snapshot().getOrDefault(logs,0L)==63,"Replaying cancellation does not duplicate the refund");
            h.assertTrue(service.visibleStatuses(UUID.randomUUID()).isEmpty()&&f.menu().error.isEmpty(),"Other players see no jobs and successful requests add no prose error");
            service.cancelAll();h.succeed();
        });
    }
    private NexusProtocolGameTests() {}
}