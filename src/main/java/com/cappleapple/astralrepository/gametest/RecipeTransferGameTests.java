package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.network.NetworkPackets;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class RecipeTransferGameTests {
    private record Fixture(NexusMenu menu,FakePlayer player,Container chest,List<NetworkPackets.RecipeInventory> updates) {}
    private static Fixture fixture(GameTestHelper h,int id){
        BlockPos nexus=new BlockPos(5,2,5),chest=nexus.west();h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());h.setBlock(chest,Blocks.CHEST);
        FakePlayer player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"recipe-transfer"));BlockPos absolute=h.absolutePos(nexus);player.setPos(absolute.getX()+.5,absolute.getY(),absolute.getZ()+.5);
        List<NetworkPackets.RecipeInventory> updates=new ArrayList<>();
        player.connection=new ServerGamePacketListenerImpl(player.getServer(),player.connection.getConnection(),player,CommonListenerCookie.createInitial(player.getGameProfile(),false)){
            @Override public void send(Packet<?> packet){if(packet instanceof ClientboundCustomPayloadPacket custom&&custom.payload() instanceof NetworkPackets.RecipeInventory inventory)updates.add(inventory);}
            @Override public void send(Packet<?> packet,PacketSendListener listener){send(packet);}
        };
        NexusMenu menu=new NexusMenu(id,player.getInventory(),GlobalPos.of(h.getLevel().dimension(),absolute),false);player.containerMenu=menu;
        return new Fixture(menu,player,(Container)h.getBlockEntity(chest),updates);
    }
    private static long stored(Fixture f,Item item){return f.menu().network().snapshot().getOrDefault(new ItemKey(new ItemStack(item)),0L);}
    private static void fill(Fixture f,String recipe,int amount){
        boolean before=AstralConfig.instantPlayerInteractions.get();AstralConfig.instantPlayerInteractions.set(true);
        try{f.menu().fillRecipe(new NetworkPackets.FillRecipe(f.menu().containerId,ResourceLocation.parse(recipe),amount));}
        finally{AstralConfig.instantPlayerInteractions.set(before);}
    }
    private static GameTestSequence await(GameTestHelper h,Fixture f,Item item,long count){return h.startSequence().thenWaitUntil(()->h.assertTrue(f.menu().network()!=null&&stored(f,item)==count,"Recipe transfer stock indexed"));}

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void fillCombinesGridPlayerAndOffSearchNetworkStockWithoutCreatingItems(GameTestHelper h){
        var f=fixture(h,131);f.chest().setItem(0,new ItemStack(Items.IRON_INGOT,10));
        await(h,f,Items.IRON_INGOT,10).thenExecute(()->{
            f.menu().action(new NetworkPackets.Action(131,NetworkPackets.SEARCH,ItemStack.EMPTY,0,0,"no matching stock"));f.menu().collectUpdate();
            h.assertTrue(f.menu().entries.isEmpty(),"Current search excludes every network ingredient");
            f.player().getInventory().setItem(9,new ItemStack(Items.IRON_INGOT,2));f.menu().grid.setItem(4,new ItemStack(Items.IRON_INGOT));
            fill(f,"minecraft:iron_trapdoor",64);
            int grid=0;for(int i=0;i<9;i++)grid+=f.menu().grid.getItem(i).getCount();
            h.assertTrue(grid==12&&stored(f,Items.IRON_INGOT)==1&&f.player().getInventory().isEmpty(),"Maximum fill conserves thirteen combined ingredients across three batches");
            h.assertTrue(f.menu().slots.getFirst().getItem().is(Items.IRON_TRAPDOOR),"The real shaped recipe matches the filled grid");h.succeed();
        });
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void fullInventoryLeavesIncompatibleGridAndNetworkUntouched(GameTestHelper h){
        var f=fixture(h,132);f.chest().setItem(0,new ItemStack(Items.OAK_LOG,4));
        await(h,f,Items.OAK_LOG,4).thenExecute(()->{
            for(int i=0;i<36;i++)f.player().getInventory().setItem(i,new ItemStack(Items.STONE,64));
            f.menu().grid.setItem(7,new ItemStack(Items.DIAMOND,3));fill(f,"minecraft:oak_planks",1);
            h.assertTrue(f.menu().grid.getItem(7).getCount()==3&&stored(f,Items.OAK_LOG)==4&&!f.menu().error.isEmpty(),"Rejected grid replacement preserves all owned and stored items");h.succeed();
        });
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void fullInventoryReusesExistingTagAlternativeBeforeNetworkStock(GameTestHelper h){
        var f=fixture(h,136);f.chest().setItem(0,new ItemStack(Items.OAK_PLANKS,16));
        await(h,f,Items.OAK_PLANKS,16).thenExecute(()->{
            for(int i=0;i<36;i++)f.player().getInventory().setItem(i,new ItemStack(Items.STONE,64));
            f.menu().grid.setItem(7,new ItemStack(Items.BIRCH_PLANKS));f.menu().grid.setItem(8,new ItemStack(Items.BIRCH_PLANKS));
            fill(f,"minecraft:stick",1);
            h.assertTrue(f.menu().error.isEmpty()&&f.menu().grid.getItem(0).is(Items.BIRCH_PLANKS)&&f.menu().grid.getItem(3).is(Items.BIRCH_PLANKS),"A full inventory can keep the valid birch ingredients already in its grid");
            h.assertTrue(stored(f,Items.OAK_PLANKS)==16&&f.menu().slots.getFirst().getItem().is(Items.STICK),"Alternative network planks remain untouched and the selected recipe is ready");h.succeed();
        });
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void sufficientGridStacksStayInPlaceWhenPlayerInventoryIsFull(GameTestHelper h){
        var f=fixture(h,137);f.chest().setItem(0,new ItemStack(Items.OAK_PLANKS,16));
        await(h,f,Items.OAK_PLANKS,16).thenExecute(()->{
            for(int i=0;i<36;i++)f.player().getInventory().setItem(i,new ItemStack(Items.STONE,64));
            f.menu().grid.setItem(0,new ItemStack(Items.BIRCH_PLANKS,64));f.menu().grid.setItem(3,new ItemStack(Items.BIRCH_PLANKS,64));
            fill(f,"minecraft:stick",1);
            h.assertTrue(f.menu().error.isEmpty()&&f.menu().grid.getItem(0).getCount()==64&&f.menu().grid.getItem(3).getCount()==64,"An already sufficient grid retains every ingredient without inventory space");
            h.assertTrue(stored(f,Items.OAK_PLANKS)==16&&f.menu().slots.getFirst().getItem().is(Items.STICK),"Selecting an already filled recipe consumes no network stock");h.succeed();
        });
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void maximumFillChoosesLargestBatchThatAlsoFitsGridLeftovers(GameTestHelper h){
        var f=fixture(h,138);f.chest().setItem(0,new ItemStack(Items.OAK_PLANKS,64));
        await(h,f,Items.OAK_PLANKS,64).thenExecute(()->{
            for(int i=0;i<36;i++)f.player().getInventory().setItem(i,new ItemStack(Items.STONE,64));
            f.menu().grid.setItem(7,new ItemStack(Items.BIRCH_PLANKS,5));f.menu().grid.setItem(8,new ItemStack(Items.BIRCH_PLANKS,5));
            fill(f,"minecraft:stick",64);
            int birch=0,oak=0;for(int i=0;i<9;i++){ItemStack stack=f.menu().grid.getItem(i);if(stack.is(Items.BIRCH_PLANKS))birch+=stack.getCount();if(stack.is(Items.OAK_PLANKS))oak+=stack.getCount();}
            h.assertTrue(f.menu().error.isEmpty()&&birch==10&&oak==10&&stored(f,Items.OAK_PLANKS)==54,"Maximum fill backs off to ten batches, which reuse all grid birch and need no leftover space");
            h.assertTrue(f.menu().slots.getFirst().getItem().is(Items.STICK),"The largest complete transaction leaves the actual crafting result available");h.succeed();
        });
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void invalidRecipePacketsCannotExtractStock(GameTestHelper h){
        var f=fixture(h,133);f.chest().setItem(0,new ItemStack(Items.OAK_LOG,4));
        await(h,f,Items.OAK_LOG,4).thenExecute(()->{
            for(int amount:new int[]{-1,0,65,Integer.MAX_VALUE})fill(f,"minecraft:oak_planks",amount);
            fill(f,"minecraft:missing_recipe",1);fill(f,"minecraft:iron_ingot_from_smelting_raw_iron",1);
            f.menu().fillRecipe(new NetworkPackets.FillRecipe(999,ResourceLocation.parse("minecraft:oak_planks"),1));
            f.player().setGameMode(GameType.SPECTATOR);fill(f,"minecraft:oak_planks",1);
            h.assertTrue(stored(f,Items.OAK_LOG)==4&&f.menu().grid.isEmpty()&&f.player().getInventory().isEmpty(),"Invalid IDs, categories, amounts, menus and spectators never mutate stock");h.succeed();
        });
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void viewerInventoryStreamsBoundedExactIdentitiesOutsideTheSearch(GameTestHelper h){
        var f=fixture(h,134);for(int i=0;i<27;i++){ItemStack stack=new ItemStack(Items.STONE,i+1);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Viewer item "+i));f.chest().setItem(i,stack);}
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.menu().network()!=null&&f.menu().network().snapshot().size()==27,"Viewer stock indexed")).thenExecute(()->{
            f.menu().action(new NetworkPackets.Action(134,NetworkPackets.SEARCH,ItemStack.EMPTY,0,0,"absent"));f.menu().broadcastChanges();
            h.assertTrue(f.updates().isEmpty(),"No inventory stream is sent without a viewer subscription");
            f.menu().subscribeRecipeInventory();f.menu().broadcastChanges();
            h.assertTrue(f.menu().entries.isEmpty()&&!f.updates().isEmpty(),"Availability sync is independent of the active search");
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
            try{for(var update:f.updates()){NetworkPackets.RecipeInventory.CODEC.encode(buffer,update);f.menu().acceptRecipeInventory(NetworkPackets.RecipeInventory.CODEC.decode(buffer));}}finally{buffer.release();}
            var stock=f.menu().recipeInventory();h.assertTrue(stock.size()==27&&stock.values().stream().mapToLong(Long::longValue).sum()==378,"Exact named identities and counts survive the wire");
            ItemStack removed=f.chest().getItem(0).copy();f.chest().setItem(0,ItemStack.EMPTY);f.menu().network().providerChanged(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(new BlockPos(4,2,5))));
            h.runAfterDelay(5,()->{f.updates().clear();f.menu().sendPage();f.menu().broadcastChanges();f.updates().forEach(f.menu()::acceptRecipeInventory);h.assertTrue(!f.menu().recipeInventory().containsKey(new ItemKey(removed)),"Removed ingredients disappear from the viewer delta");h.succeed();});
        });
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=40)
    public static void viewerInventoryPublishesOnlyCompleteChunksAndRejectsOversizedPackets(GameTestHelper h){
        var f=fixture(h,135);ItemStack iron=new ItemStack(Items.IRON_INGOT);var entry=new NexusMenu.Entry(iron,4,false);
        f.menu().acceptRecipeInventory(new NetworkPackets.RecipeInventory(135,1,true,true,false,List.of(entry)));
        h.assertTrue(f.menu().recipeInventory().isEmpty(),"Partial revisions never advertise an incomplete inventory");
        f.menu().acceptRecipeInventory(new NetworkPackets.RecipeInventory(135,1,false,false,true,List.of()));
        h.assertTrue(f.menu().recipeInventory().get(new ItemKey(iron))==4,"Final chunk commits the complete revision");
        f.menu().acceptRecipeInventory(new NetworkPackets.RecipeInventory(135,0,true,true,true,List.of()));
        h.assertTrue(f.menu().recipeInventory().size()==1,"Stale snapshots cannot overwrite newer availability");
        boolean rejected=false;try{new NetworkPackets.RecipeInventory(135,2,true,true,true,Collections.nCopies(55,entry));}catch(IllegalArgumentException expected){rejected=true;}
        h.assertTrue(rejected,"Each inventory packet is bounded to fifty-four identities");h.succeed();
    }
    private RecipeTransferGameTests() {}
}
