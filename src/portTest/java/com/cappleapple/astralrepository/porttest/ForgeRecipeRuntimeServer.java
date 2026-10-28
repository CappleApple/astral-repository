package com.cappleapple.astralrepository.porttest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.crafting.CraftingService;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;

/** Installed-server tests: real furnace tickers produce every requested result. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="astral_port_test")
public final class ForgeRecipeRuntimeServer {
    private static final List<Fixture> fixtures=new ArrayList<>();
    private static MinecraftServer server;
    private static int ticks;
    private static boolean done,started;
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void start(net.minecraftforge.event.server.ServerStartedEvent event){
        if(!Boolean.getBoolean("astral_repository.recipeCompatServer"))return;
        server=event.getServer();
        try{
            var level=server.overworld();level.setDefaultSpawnPos(new BlockPos(0,100,0),0);net.minecraftforge.common.world.ForgeChunkManager.forceChunk(level,"astral_port_test",new UUID(0,1),0,0,true,true);level.getChunkAt(new BlockPos(0,100,0));
            if(Boolean.getBoolean("astral_repository.recipeCompatPlain")){
                create(level,"native-smelting",0,"",Items.RAW_IRON,Items.IRON_INGOT,true);
                create(level,"native-crafting",1,"vanilla_crafting",Items.IRON_INGOT,Items.IRON_TRAPDOOR,true);
            }else{
            create(level,"normal",0,"",Items.RAW_IRON,Items.IRON_INGOT,false);
            create(level,"blasting",1,"augment_blasting",Items.RAW_IRON,Items.IRON_INGOT,false);
            create(level,"smoking",2,"augment_smoking",Items.BEEF,Items.COOKED_BEEF,false);
            create(level,"conflicting-gold",3,"",Items.DIRT,Items.GOLD_NUGGET,true);
            create(level,"conflicting-iron",4,"",Items.DIRT,Items.IRON_NUGGET,true);
            }
            ForgeFullNetworkRecipeFixture.setup(server,Boolean.getBoolean("astral_repository.recipeCompatPlain"));
            LogUtils.getLogger().info("FORGE_RECIPE_RUNTIME_SERVER_SOURCE {}",CraftingService.class.getProtectionDomain().getCodeSource().getLocation());
        }catch(Throwable failure){finish(failure);}
    }
    private static void create(ServerLevel level,String name,int index,String augment,Item input,Item output,boolean vanilla){
        var chest=new BlockPos(1,100,1+index*2);var furnace=chest.east(3);
        level.setBlockAndUpdate(chest.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(furnace.below(),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(chest,Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(furnace,vanilla?(augment.equals("vanilla_crafting")?Blocks.CRAFTING_TABLE:Blocks.FURNACE).defaultBlockState():BuiltInRegistries.BLOCK.get(new ResourceLocation("ironfurnaces:iron_furnace")).defaultBlockState());
        if(!vanilla&&!augment.isEmpty())((Container)level.getBlockEntity(furnace)).setItem(3,new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("ironfurnaces",augment))));
        var inventory=(Container)level.getBlockEntity(chest);inventory.setItem(0,new ItemStack(input,augment.equals("vanilla_crafting")?4:1));if(!augment.equals("vanilla_crafting"))inventory.setItem(1,new ItemStack(Items.COAL));
        fixtures.add(new Fixture(name,level,chest,furnace,inventory,input,output));
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event){
        if(server==null||done||event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        try{
            if(++ticks>1000)throw new AssertionError("Furnace runtime timeout: "+fixtures.stream().map(f->f.name+":"+f.service.statuses()).toList());
            if(ticks<20)return;
            if(!started){
                started=true;
                for(var f:fixtures){var player=new FakePlayer(f.level,new GameProfile(UUID.randomUUID(),"forge-recipe-test"));check(f.service.request(player,new ItemStack(f.output),1).accepted(),"Request rejected: "+f.name);}
            }
            for(var f:fixtures){
                f.service.tick();
                if(ticks%100==0&&f.level.getBlockEntity(f.processor) instanceof Container machine){LogUtils.getLogger().info("FORGE_RECIPE_RUNTIME_PROGRESS {} jobs={} input={} fuel={} output={} chunkLoaded={}",f.name,f.service.statuses(),machine.getItem(0),machine.getItem(1),machine.getItem(2),f.level.hasChunkAt(f.processor));}
                check(f.service.statuses().stream().noneMatch(job->job.state().equals("FAILED")),"Processing failed: "+f.name+" "+f.service.statuses());
            }
            boolean fullNetworkDone=ForgeFullNetworkRecipeFixture.tick();
            if(fixtures.stream().anyMatch(f->f.service.activeJobs()!=0)||!fullNetworkDone)return;
            for(var f:fixtures){
                var stock=f.snapshot();check(stock.getOrDefault(new ItemKey(new ItemStack(f.output)),0L)==1,"Wrong real output: "+f.name+" "+stock);
                check(!stock.containsKey(new ItemKey(new ItemStack(f.input)))&&!stock.containsKey(new ItemKey(new ItemStack(Items.COAL))),"Input or coal not consumed: "+f.name);
                if(f.level.getBlockEntity(f.processor) instanceof Container machine)check(machine.getItem(0).isEmpty()&&machine.getItem(2).isEmpty(),"Machine input/output not collected: "+f.name);
                check(!CraftingService.isProcessorReserved(f.at(f.processor)),"Claim retained: "+f.name);
                LogUtils.getLogger().info("FORGE_RECIPE_RUNTIME_CASE_OK {}",f.name);
            }
            finish(null);
        }catch(Throwable failure){finish(failure);}
    }
    private static void finish(Throwable failure){
        if(done)return;done=true;
        String text=failure==null?(Boolean.getBoolean("astral_repository.recipeCompatPlain")?"PASS: packaged Forge 1.20.1 with no optional mods; actual native crafting and furnace tickers consume exactly four iron and one raw iron/fuel, return trapdoor/ingot, and release workstation claims.":"PASS: packaged Forge 1.20.1; actual Iron Furnaces normal/blasting/smoking tickers consume input and fuel; Polymorph selects both conflicting vanilla smelting recipes; exact outputs return to storage; claims release."):"FAIL: "+failure;
        if(failure==null)text+=" Full Nexus/library/storage network completed its empty-machine scan, then discovered a late ordinary block placement and completed taught iron through the real processor with exact conservation.";
        if(failure!=null)LogUtils.getLogger().error("FORGE_RECIPE_RUNTIME_SERVER_FAILED",failure);else LogUtils.getLogger().info("FORGE_RECIPE_RUNTIME_SERVER_OK");
        try{Files.writeString(Path.of("recipe-server-result.txt"),text+"\n");}catch(Exception ignored){}
        fixtures.forEach(f->f.service.cancelAll());if(server!=null)server.halt(false);
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static final class Fixture implements CraftingService.NetworkAccess {
        final String name;final ServerLevel level;final BlockPos origin,processor;final Container inventory;final Item input,output;final CraftingService service;
        Fixture(String name,ServerLevel level,BlockPos origin,BlockPos processor,Container inventory,Item input,Item output){this.name=name;this.level=level;this.origin=origin;this.processor=processor;this.inventory=inventory;this.input=input;this.output=output;service=new CraftingService(this);}
        GlobalPos at(BlockPos pos){return GlobalPos.of(level.dimension(),pos);}
        public ServerLevel level(){return level;}
        public Map<ItemKey,Long> snapshot(){var stock=new HashMap<ItemKey,Long>();for(int i=0;i<inventory.getContainerSize();i++){var item=inventory.getItem(i);if(!item.isEmpty())stock.merge(new ItemKey(item),(long)item.getCount(),Long::sum);}return stock;}
        public ItemStack extract(ItemKey key,int count){for(int i=0;i<inventory.getContainerSize();i++)if(!inventory.getItem(i).isEmpty()&&key.equals(new ItemKey(inventory.getItem(i))))return inventory.removeItem(i,count);return ItemStack.EMPTY;}
        public ItemStack insert(ItemStack item){return net.minecraftforge.items.ItemHandlerHelper.insertItem(new net.minecraftforge.items.wrapper.InvWrapper(inventory),item,false);}
        public List<GlobalPos> workstations(){return List.of(at(processor));}
        public List<ItemStack> exposedProducts(){return List.of(new ItemStack(output));}
        public GlobalPos origin(){return at(origin);}
        public void visual(GlobalPos from,GlobalPos to,ItemStack item,int ticks){}
    }
}
