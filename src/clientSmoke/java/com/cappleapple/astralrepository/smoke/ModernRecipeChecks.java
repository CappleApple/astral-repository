package com.cappleapple.astralrepository.smoke;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.client.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.crafting.CraftingService;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.network.*;
import com.cappleapple.astralrepository.port.NbtCodecs;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Separate packaged-JAR fixture; never part of an installable mod. */
final class ModernRecipeChecks {
    private static final BlockPos NEXUS=new BlockPos(0,-60,0),STORE=NEXUS.east(2),MACHINE=NEXUS.north(),SHELF=NEXUS.west(),TABLE=NEXUS.south();
    private static final List<String> checks=new ArrayList<>();
    private static int phase,ticks,cooking;
    private static boolean done,planRecorded;
    private static CompletableFuture<Void> pending;
    private static UUID job;
    private static long expectedOutput;
    private static volatile boolean operationDone;
    private static Item conflictTarget;

    static void tick(Minecraft mc)throws Exception{
        if(done)return;
        if(++ticks>3000)throw new AssertionError("Packaged recipe compatibility timeout phase "+phase);
        if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
        if(phase==0&&mc.gui.screen()!=null&&mc.gui.overlay()==null){
            check(!FabricLoader.getInstance().isDevelopmentEnvironment(),"Expected a production launcher");
            check(com.cappleapple.astralrepository.AstralRepository.class.getProtectionDomain().getCodeSource().getLocation().toString().contains(".jar"),"Core classes did not load from a packaged JAR");
            for(String absent:List.of("emi","polymorph","ironfurnaces"))check(!FabricLoader.getInstance().isModLoaded(absent),"Unsupported modern dependency was unexpectedly loaded: "+absent);
            check(FabricLoader.getInstance().isModLoaded("jei")==Boolean.getBoolean("astral_repository.recipeCompatJei"),"JEI presence does not match selected profile");
            mc.options.renderDistance().set(3);mc.options.guiScale().set(1);mc.resizeGui();mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            next(1);
            mc.createWorldOpenFlows().createFreshLevel("astral-recipes-"+System.currentTimeMillis(),
                    new LevelSettings("Astral recipe compatibility",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42,false,false),r->r.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.gui.screen());
        }else if(phase==1&&mc.player!=null&&mc.level!=null&&mc.getSingleplayerServer()!=null&&mc.gui.overlay()==null){
            mc.gui.setScreen(null);server(ModernRecipeChecks::setup);next(2);
        }else if(phase==2&&ticks>100){server(p->NetworkManager.open(p,GlobalPos.of(p.level().dimension(),NEXUS)));next(3);
        }else if(phase==3&&mc.gui.screen() instanceof NexusScreen screen&&ticks>30){
            if(FabricLoader.getInstance().isModLoaded("jei")&&!Jei.ready())return;
            RecipeTransfers.inventory(screen.getMenu());
            var search=(EditBox)screen.children().stream().filter(w->w instanceof EditBox e&&e.getMessage().getString().equals("Search storage")).findFirst().orElseThrow();search.setValue("no matching storage");next(4);
        }else if(phase==4&&mc.gui.screen() instanceof NexusScreen screen&&ticks>30){
            if(screen.getMenu().recipeInventory().size()<81)return;
            check(screen.getMenu().entries.isEmpty(),"Search did not hide ingredients");
            check(screen.getMenu().recipeInventory().getOrDefault(key(Items.IRON_INGOT),0L)==8,"Off-page iron missing from viewer inventory");
            customAvailability(screen.getMenu());
            if(FabricLoader.getInstance().isModLoaded("jei"))Jei.fill(screen);
            else RecipeTransfers.fill(screen.getMenu(),Identifier.withDefaultNamespace("iron_trapdoor"),1);
            next(5);
        }else if(phase==5&&mc.gui.screen() instanceof NexusScreen screen&&ticks>30){
            if(!screen.getMenu().slots.getFirst().getItem().is(Items.IRON_TRAPDOOR))return;
            server(p->{
                var menu=(NexusMenu)p.containerMenu;check(count(menu,Items.IRON_INGOT)==4&&gridCount(menu,Items.IRON_INGOT)==4,"Actual network fill did not conserve eight iron");
                check(p.getInventory().isEmpty()&&menu.getCarried().isEmpty(),"Fill changed player inventory or cursor");
                menu.action(new NetworkPackets.Action(menu.containerId,NetworkPackets.CLEAR_GRID,ItemStack.EMPTY,0,0,""));
                check(menu.network().insertAt(new ItemStack(Items.OAK_PLANKS,64),menu.origin).isEmpty(),"Network rejected capacity fixture planks");
                for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
                menu.grid.setItem(0,new ItemStack(Items.BIRCH_PLANKS,64));menu.grid.setItem(3,new ItemStack(Items.BIRCH_PLANKS,64));
                menu.fillRecipe(new NetworkPackets.FillRecipe(menu.containerId,Identifier.withDefaultNamespace("stick"),1));
                check(menu.error.isEmpty()&&gridCount(menu,Items.BIRCH_PLANKS)==128,"Sufficient grid stacks changed with a full inventory");
                for(int i=0;i<9;i++)menu.grid.setItem(i,ItemStack.EMPTY);
                menu.grid.setItem(7,new ItemStack(Items.BIRCH_PLANKS,5));menu.grid.setItem(8,new ItemStack(Items.BIRCH_PLANKS,5));
                menu.fillRecipe(new NetworkPackets.FillRecipe(menu.containerId,Identifier.withDefaultNamespace("stick"),64));
                check(menu.error.isEmpty()&&gridCount(menu,Items.BIRCH_PLANKS)==10&&gridCount(menu,Items.OAK_PLANKS)==10&&count(menu,Items.OAK_PLANKS)==54,"Maximum fill did not choose a capacity-feasible ten batches: error="+menu.error+" birch="+gridCount(menu,Items.BIRCH_PLANKS)+" oak="+gridCount(menu,Items.OAK_PLANKS)+" storedOak="+count(menu,Items.OAK_PLANKS));
                p.getInventory().clearContent();menu.action(new NetworkPackets.Action(menu.containerId,NetworkPackets.CLEAR_GRID,ItemStack.EMPTY,0,0,""));
                pass("81 exact identities outside search; actual "+(FabricLoader.getInstance().isModLoaded("jei")?"registered JEI handler":"viewer-absent packet")+" fills shaped grid; conserved inventory; sufficient-stack and maximum-fill capacity edges");
                configureCooking(p,0);
            });next(6);
        }else if(phase==6&&ticks>80){server(p->request(p,cookingOutput(cooking)));next(7);
        }else if(phase==7){
            if(operationDone){operationDone=false;pass("physical "+List.of("furnace smelting","blast furnace blasting","smoker smoking").get(cooking)+" consumed fuel and returned exact output");
                if(++cooking<3){server(p->configureCooking(p,cooking));next(6);}else{server(ModernRecipeChecks::configureConflict);next(8);}return;}
            if(ticks%15!=0)return;
            server(p->{
                var menu=(NexusMenu)p.containerMenu;var service=menu.network().crafting();tracePlan(service);var status=service.statuses().stream().filter(s->s.id().equals(job)).findFirst().orElseThrow();
                if(!status.state().equals("COMPLETE")){check(!status.state().equals("FAILED"),"Physical cooking failed: "+status);return;}
                check(count(menu,cookingOutput(cooking))==expectedOutput,"Physical cooking output count incorrect: target="+cookingOutput(cooking)+" count="+count(menu,cookingOutput(cooking))+" expected="+expectedOutput+" raw="+count(menu,cookingInput(cooking))+" coal="+count(menu,Items.COAL)+" job="+status);
                check(count(menu,cookingInput(cooking))==0&&count(menu,Items.COAL)==0,"Physical cooking did not consume exact input and fuel");
                var machine=(Container)p.level().getBlockEntity(MACHINE);check(machine.getItem(0).isEmpty()&&machine.getItem(2).isEmpty(),"Physical cooking left owned input/output in machine");
                check(!CraftingService.isProcessorReserved(GlobalPos.of(p.level().dimension(),MACHINE)),"Cooking processor claim leaked");operationDone=true;
            });
        }else if(phase==8&&ticks>80){server(p->request(p,conflictTarget));next(9);
        }else if(phase==9){
            if(operationDone){operationDone=false;next(10);return;}
            if(ticks%15!=0)return;
            server(p->{
                var menu=(NexusMenu)p.containerMenu;var service=menu.network().crafting();tracePlan(service);var status=service.statuses().stream().filter(s->s.id().equals(job)).findFirst().orElseThrow();
                if(!status.state().equals("FAILED")){check(!status.state().equals("COMPLETE"),"Unsupported conflicting furnace recipe unexpectedly manufactured output");return;}
                check(status.message().contains("conflicting recipe"),"Furnace conflict did not report explicit failure: "+status);
                check(count(menu,Items.DIRT)==1&&count(menu,conflictTarget)==0,"Conflicting furnace input was not refunded exactly");
                check(!CraftingService.isProcessorReserved(GlobalPos.of(p.level().dimension(),MACHINE)),"Failed furnace claim leaked");
                var furnace=(Container)p.level().getBlockEntity(MACHINE);check(furnace.getItem(0).isEmpty()&&furnace.getItem(2).isEmpty(),"Rejected conflict touched furnace input/output");
                pass("unsupported furnace conflict fails explicitly, refunds its exact ingredient, and releases processor ownership");
                menu.network().extractAt(key(Items.DIRT),1,menu.origin);
                manualConflict(p,menu,"conflict_crafting_a",Items.GOLD_NUGGET);manualConflict(p,menu,"conflict_crafting_z",Items.IRON_NUGGET);
                pass("manual conflicting recipes select and consume the exact requested result without Polymorph");
                p.level().setBlockAndUpdate(TABLE,Blocks.CRAFTING_TABLE.defaultBlockState());put(p,95,new ItemStack(Items.DANDELION));teach(p,new ItemStack(Items.IRON_NUGGET));operationDone=true;
            });
        }else if(phase==10&&ticks>80){server(p->request(p,Items.IRON_NUGGET));next(11);
        }else if(phase==11){
            if(operationDone){pass("conflicting table autocrafting completes the exact planned output with no stuck job");next(12);return;}
            if(ticks%15!=0)return;
            server(p->{var menu=(NexusMenu)p.containerMenu;var status=menu.network().crafting().statuses().stream().filter(s->s.id().equals(job)).findFirst().orElseThrow();
                if(!status.state().equals("COMPLETE")){check(!status.state().equals("FAILED"),"Conflicting table recipe failed: "+status);return;}
                check(count(menu,Items.IRON_NUGGET)==1&&count(menu,Items.DANDELION)==0,"Table conflict did not conserve exact selected output");
                check(menu.network().crafting().activeJobs()==0&&!CraftingService.isProcessorReserved(GlobalPos.of(p.level().dimension(),TABLE)),"Completed conflicting table job remained locked");operationDone=true;});
        }else if(phase==12){
            check(!mc.mouseHandler.isMouseGrabbed(),"Background client captured the mouse");check(mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).get()==0D,"Background client was not muted");
            var details=new LinkedHashMap<String,Object>();details.put("minecraft",FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString());
            details.put("loader","fabric");details.put("jei",FabricLoader.getInstance().getModContainer("jei").map(m->m.getMetadata().getVersion().getFriendlyString()).orElse("absent"));details.put("checks",checks);details.put("status","PASS");
            Files.createDirectories(Path.of("captures"));Files.writeString(Path.of("captures/recipe-compat.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(details));Files.writeString(Path.of("captures/result.txt"),"PASS: packaged recipe compatibility; "+String.join("; ",checks)+"\n");done=true;mc.stop();
        }
    }
    private static void setup(ServerPlayer p){
        var level=p.level();for(int x=-4;x<6;x++)for(int z=-4;z<5;z++)level.setBlockAndUpdate(new BlockPos(x,-61,z),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(NEXUS,AstralContent.STORAGE_NEXUS.get().defaultBlockState());level.setBlockAndUpdate(STORE,AstralContent.SEED_STORAGE_CRYSTAL.get().defaultBlockState());
        ((CrystalNodeBlockEntity)level.getBlockEntity(NEXUS)).setChannel(12);storage(p).setChannel(12);
        check(NetworkManager.get(level.getServer()).toggleLink(AnchorAddress.crystal(level,NEXUS),AnchorAddress.crystal(level,STORE)).success(),"Could not link fixture storage");
        for(int i=0;i<80;i++){var stack=new ItemStack(Items.STONE);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Recipe availability "+i));put(p,i,stack);}
        put(p,80,new ItemStack(Items.IRON_INGOT,8));
        level.setBlockAndUpdate(SHELF,Blocks.CHISELED_BOOKSHELF.defaultBlockState());level.setBlockAndUpdate(TABLE,Blocks.CRAFTING_TABLE.defaultBlockState());
        p.getInventory().clearContent();p.teleportTo(level,.5,-59,4.5,Set.of(),180,20,false);
        check(level.getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(Registries.RECIPE,id("conflict_smelting_a"))).isPresent(),"Dedicated conflict fixture resources were not loaded");
        pass("packaged core loaded with EMI, Polymorph and Iron Furnaces absent");
    }
    private static void customAvailability(NexusMenu menu){
        check(RecipeTransfers.recipeEpoch()>0,"Actual client tag synchronization did not advance the recipe epoch");
        int[] simpleCalls={0};
        var simple=new net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient(){
            @Override public boolean test(ItemStack stack){simpleCalls[0]++;return stack.is(Items.IRON_INGOT);}
            @Override public java.util.stream.Stream<Holder<Item>> items(){return java.util.stream.Stream.of(Items.IRON_INGOT.builtInRegistryHolder());}
            @Override public boolean requiresTesting(){return false;}
            @Override public net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer<?> getSerializer(){throw new AssertionError("Client-only fixture must not be serialized");}
        }.toVanilla();
        int before=simpleCalls[0];
        for(int i=0;i<1024;i++)check(RecipeTransfers.canFill(menu,customRecipe(simple)),"Cold custom-simple catalogue lost stored iron");
        check(simpleCalls[0]==before,"Simple custom ingredient ignored its public item-only matching contract");

        boolean[] enabled={false};int[] dynamicCalls={0};var prototypes=new IdentityHashMap<ItemStack,Boolean>();
        var dynamic=new net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient(){
            @Override public boolean test(ItemStack stack){dynamicCalls[0]++;prototypes.put(stack,true);check(stack.getCount()==1,"Predicate sample count was not normalized");return enabled[0]&&stack.is(Items.IRON_INGOT);}
            // Display entries are intentionally incomplete for this non-simple ingredient.
            @Override public java.util.stream.Stream<Holder<Item>> items(){return java.util.stream.Stream.of(Items.STONE.builtInRegistryHolder());}
            @Override public boolean requiresTesting(){return true;}
            @Override public net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer<?> getSerializer(){throw new AssertionError("Client-only fixture must not be serialized");}
        }.toVanilla();
        var recipe=customRecipe(dynamic);
        check(!RecipeTransfers.canFill(menu,recipe),"Disabled dynamic ingredient unexpectedly matched");
        int samples=prototypes.size(),firstCalls=dynamicCalls[0];check(samples>0,"Dynamic predicate was never evaluated");
        enabled[0]=true;check(RecipeTransfers.canFill(menu,recipe),"Incomplete display items hid an accepted stored ingredient");
        enabled[0]=false;check(!RecipeTransfers.canFill(menu,recipe),"Dynamic predicate answer was cached");
        enabled[0]=true;check(RecipeTransfers.canFill(menu,recipe),"Repeated dynamic state change was ignored");
        check(dynamicCalls[0]>firstCalls&&prototypes.size()==samples,"Dynamic matching allocated fresh stack copies instead of reusing detached prototypes");
        var stock=RecipeTransfers.inventory(menu);long epoch=RecipeTransfers.recipeEpoch();
        RecipeTransfers.recipesReloaded();
        check(RecipeTransfers.recipeEpoch()==epoch+1&&RecipeTransfers.inventory(menu)!=stock,"Tag-only recipe reload retained cached stock");
        check(RecipeTransfers.canFill(menu,recipe),"Dynamic matching failed after recipe reload");
        pass("1024 cold custom-simple recipes use indexed stock; incomplete-display dynamic predicates observe state changes and reuse detached sample stacks; client tag synchronization and recipe epoch invalidate cached stock");
    }
    private static CraftingRecipe customRecipe(Ingredient ingredient){
        return new ShapelessRecipe(new Recipe.CommonInfo(false),new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC,"client cache fixture"),new ItemStackTemplate(Items.STICK),List.of(ingredient));
    }
    private static void configureCooking(ServerPlayer p,int index){
        p.level().setBlockAndUpdate(MACHINE,List.of(Blocks.FURNACE,Blocks.BLAST_FURNACE,Blocks.SMOKER).get(index).defaultBlockState());
        put(p,90,new ItemStack(cookingInput(index)));put(p,91,new ItemStack(Items.COAL));teach(p,new ItemStack(cookingOutput(index)));
    }
    private static void configureConflict(ServerPlayer p){
        p.level().setBlockAndUpdate(MACHINE,Blocks.FURNACE.defaultBlockState());
        var actual=p.level().getServer().getRecipeManager().getRecipeFor(RecipeType.SMELTING,new SingleRecipeInput(new ItemStack(Items.DIRT)),p.level()).orElseThrow();
        conflictTarget=actual.value().assemble(new SingleRecipeInput(new ItemStack(Items.DIRT))).is(Items.GOLD_NUGGET)?Items.IRON_NUGGET:Items.GOLD_NUGGET;
        put(p,92,new ItemStack(Items.DIRT));teach(p,new ItemStack(conflictTarget));
    }
    private static void request(ServerPlayer p,Item target){var menu=(NexusMenu)p.containerMenu;expectedOutput=count(menu,target)+1;var request=menu.network().crafting().request(p,new ItemStack(target),1);check(request.accepted(),"Recipe request rejected: "+request.message());job=request.jobId();planRecorded=false;LogUtils.getLogger().info("FABRIC_RECIPE_REQUEST target={} expected={} raw={} coal={} workstations={} message={}",target,expectedOutput,count(menu,cookingInput(Math.min(cooking,2))),count(menu,Items.COAL),menu.network().workstations().stream().map(at->at+"="+p.level().getBlockState(at.pos())).toList(),request.message());}
    private static void tracePlan(CraftingService service){
        if(planRecorded)return;
        try{
            var jobsField=CraftingService.class.getDeclaredField("jobs");jobsField.setAccessible(true);Object current=((Map<?,?>)jobsField.get(service)).get(job);if(current==null)return;
            var schedulerMethod=current.getClass().getDeclaredMethod("scheduler");schedulerMethod.setAccessible(true);Object scheduler=schedulerMethod.invoke(current);
            var planField=scheduler.getClass().getDeclaredField("plan");planField.setAccessible(true);Object plan=planField.get(scheduler);
            LogUtils.getLogger().info("FABRIC_RECIPE_ACTUAL_PLAN {}",plan);
            if(phase==7){
                var actual=(com.cappleapple.astralrepository.crafting.CraftPlan<?>)plan;
                String expected=List.of("smelting","blasting","smoking").get(cooking);
                check(actual.nodes().size()==1&&actual.nodes().getFirst().recipe().process().equals(expected),"Newly placed cooking block was not selected with the existing table: "+actual);
            }
            planRecorded=true;
        }catch(ReflectiveOperationException failure){throw new AssertionError("Could not record actual fixture plan",failure);}
    }
    private static void manualConflict(ServerPlayer p,NexusMenu menu,String recipe,Item output){
        menu.grid.setItem(0,new ItemStack(Items.DANDELION));check(menu.selectCraftingRecipe(id(recipe)),"Matching manual conflict could not be selected");check(menu.slots.getFirst().getItem().is(output),"Manual conflict preview chose wrong output");
        menu.clicked(0,0,ContainerInput.PICKUP,p);check(menu.getCarried().is(output)&&menu.getCarried().getCount()==1&&menu.grid.isEmpty(),"Manual conflict consumed/produced wrong items");menu.setCarried(ItemStack.EMPTY);
    }
    private static void teach(ServerPlayer p,ItemStack output){var tome=new ItemStack(AstralContent.RECIPE_TOME.get());var tag=new CompoundTag();tag.put("Output",NbtCodecs.save(output,p.registryAccess()));tag.putString("OutputId",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output.getItem()).toString());tome.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));((Container)p.level().getBlockEntity(SHELF)).setItem(0,tome);}
    private static void put(ServerPlayer p,int slot,ItemStack stack){var inventory=storage(p).inventory();for(int i=0;i<inventory.getSlots()&&!stack.isEmpty();i++)stack=inventory.insertItem(i,stack,false);check(stack.isEmpty(),"Fixture storage insertion rejected for input "+slot);}
    private static CrystalNodeBlockEntity storage(ServerPlayer p){return (CrystalNodeBlockEntity)p.level().getBlockEntity(STORE);}
    private static Item cookingInput(int index){return List.of(Items.RAW_IRON,Items.RAW_GOLD,Items.BEEF).get(index);}
    private static Item cookingOutput(int index){return List.of(Items.IRON_INGOT,Items.GOLD_INGOT,Items.COOKED_BEEF).get(index);}
    private static ItemKey key(Item item){return new ItemKey(new ItemStack(item));}
    private static long count(NexusMenu menu,Item item){return menu.network().snapshot().getOrDefault(key(item),0L);}
    private static int gridCount(NexusMenu menu,Item item){int count=0;for(int i=0;i<9;i++)if(menu.grid.getItem(i).is(item))count+=menu.grid.getItem(i).getCount();return count;}
    private static Identifier id(String name){return Identifier.fromNamespaceAndPath("astral_repository_recipe_test",name);}
    private static void server(Consumer<ServerPlayer> action){var mc=Minecraft.getInstance();var player=mc.player.getUUID();pending=new CompletableFuture<>();var future=pending;mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(player));future.complete(null);}catch(Throwable error){future.completeExceptionally(error);}});}
    private static void next(int value){phase=value;ticks=0;LogUtils.getLogger().info("FABRIC_RECIPE_COMPAT_PHASE {}",phase);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void pass(String value){checks.add(value);LogUtils.getLogger().info("FABRIC_RECIPE_COMPAT_CHECK {}",value);}
    private static final class Jei {
        static mezz.jei.api.runtime.IJeiRuntime runtime()throws Exception{var field=com.cappleapple.astralrepository.compat.AstralJeiPlugin.class.getDeclaredField("runtime");field.setAccessible(true);return (mezz.jei.api.runtime.IJeiRuntime)field.get(null);}
        static boolean ready()throws Exception{return runtime()!=null;}
        static void fill(NexusScreen screen)throws Exception{
            var runtime=runtime();var category=runtime.getRecipeManager().getRecipeCategory(mezz.jei.api.constants.RecipeTypes.CRAFTING);
            var handler=runtime.getRecipeTransferManager().getRecipeTransferHandler(screen.getMenu(),category).orElseThrow();check(handler instanceof com.cappleapple.astralrepository.compat.AstralJeiPlugin.RecipeHandler,"Actual JEI runtime did not register the Nexus handler");
            var recipe=runtime.getRecipeManager().createRecipeLookup(mezz.jei.api.constants.RecipeTypes.CRAFTING).get().filter(r->r.id().identifier().equals(Identifier.withDefaultNamespace("iron_trapdoor"))).findFirst().orElseThrow();
            var menu=screen.getMenu();var stock=RecipeTransfers.inventory(menu);
            for(int i=0;i<1000;i++){
                check(handler.transferRecipe(menu,recipe,null,Minecraft.getInstance().player,false,false)==null,"Repeated JEI availability changed");
                check(RecipeTransfers.inventory(menu)==stock,"Unchanged JEI availability rebuilt client stock");
            }
            var previous=menu.slots.get(10).getItem().copy();var carried=menu.getCarried().copy();
            try{
                menu.slots.get(10).set(new ItemStack(Items.GOLD_INGOT,3));
                var local=RecipeTransfers.inventory(menu);check(local!=stock&&local.getOrDefault(key(Items.GOLD_INGOT),0L)==3,"Local slot did not invalidate cached stock");
                menu.slots.get(10).getItem().setCount(2);
                var counted=RecipeTransfers.inventory(menu);check(counted!=local&&counted.getOrDefault(key(Items.GOLD_INGOT),0L)==2,"In-place local count did not invalidate cached stock");
                menu.slots.get(10).getItem().set(DataComponents.CUSTOM_NAME,Component.literal("Availability identity"));
                var named=new ItemKey(menu.slots.get(10).getItem());var components=RecipeTransfers.inventory(menu);
                check(components!=counted&&components.getOrDefault(named,0L)==2&&components.getOrDefault(key(Items.GOLD_INGOT),0L)==0,"Local components were merged into a different exact identity");
                var blocks=runtime.getRecipeManager().createRecipeLookup(mezz.jei.api.constants.RecipeTypes.CRAFTING).get().filter(r->r.id().identifier().equals(Identifier.withDefaultNamespace("gold_block"))).findFirst().orElseThrow();
                check(handler.transferRecipe(menu,blocks,null,Minecraft.getInstance().player,false,false)!=null,"Two local ingots unexpectedly satisfied nine slots");
                menu.setCarried(new ItemStack(Items.GOLD_INGOT,6));
                var cursor=RecipeTransfers.inventory(menu);check(cursor!=components&&cursor.getOrDefault(key(Items.GOLD_INGOT),0L)==0,"Cursor was counted inside EMI constructor input stock");
                check(handler.transferRecipe(menu,blocks,null,Minecraft.getInstance().player,false,false)!=null,"Cursor counted twice in JEI availability");
                menu.getCarried().setCount(7);
                check(handler.transferRecipe(menu,blocks,null,Minecraft.getInstance().player,false,false)==null,"Cursor count change did not invalidate a cached negative recipe answer");
                check(RecipeTransfers.inventory(menu)!=cursor,"Cursor count change reused stale stock");
            }finally{menu.slots.get(10).set(previous);menu.setCarried(carried);}
            pass("registered JEI handler reuses stock for 1000 checks; local counts, exact components, and cursor changes invalidate availability without double counting");
            check(handler.transferRecipe(screen.getMenu(),recipe,null,Minecraft.getInstance().player,false,false)==null,"JEI reported stored ingredients missing");check(handler.transferRecipe(screen.getMenu(),recipe,null,Minecraft.getInstance().player,false,true)==null,"JEI rejected actual recipe transfer");
        }
    }
}
