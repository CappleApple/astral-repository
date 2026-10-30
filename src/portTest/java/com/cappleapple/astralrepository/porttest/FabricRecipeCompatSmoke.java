package com.cappleapple.astralrepository.porttest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.client.NexusScreen;
import com.cappleapple.astralrepository.client.RecipeTransfers;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.CrystalNodeBlockEntity;
import com.cappleapple.astralrepository.network.NetworkManager;
import com.cappleapple.astralrepository.network.NetworkPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Real client/server transfer check; run separately with EMI and standalone JEI. */
public final class FabricRecipeCompatSmoke implements net.fabricmc.api.ClientModInitializer {
    private static boolean worldStarting,transferDone,completed,manualClicked;
    private static volatile boolean manualVerified;
    private static int runtimeTicks;
    private static boolean moonlightChecked,reloadChecked;
    private static int cacheNetworkStep;
    private static java.util.Map<ItemKey,Long> cacheNetworkBefore;
    private static Object cacheViewerBefore;
    private static ItemStack cacheHeldIron=ItemStack.EMPTY;
    private static volatile boolean jobsDone;
    private static com.cappleapple.astralrepository.crafting.CraftingService service;
    private static JobAccess jobAccess;
    private static int jobStep,jobTicks;
    @Override public void onInitializeClient(){
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(!Boolean.getBoolean("astral_repository.recipeRuntime")||completed)return;
            mc.options.pauseOnLostFocus=false;mc.mouseHandler.releaseMouse();mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0D);
            try{
                if(failure!=null)throw new AssertionError("Server runtime test",failure);
                if(++runtimeTicks>4200)throw new AssertionError("Runtime timeout phase="+phase+" job="+jobStep);
                if(!worldStarting&&mc.screen!=null&&mc.getOverlay()==null){
                    String expectedViewer=System.getProperty("astral_repository.recipeViewer","plain");
                    check(FabricLoader.getInstance().isModLoaded("emi")==expectedViewer.equals("emi")&&FabricLoader.getInstance().isModLoaded("jei")==expectedViewer.equals("jei"),"Unexpected recipe-viewer combination");
                    check(FabricLoader.getInstance().isModLoaded("polymorph")==Boolean.getBoolean("astral_repository.expectPolymorph"),"Unexpected Polymorph presence");
                    check(FabricLoader.getInstance().isModLoaded("moonlight")==Boolean.getBoolean("astral_repository.expectMoonlight"),"Unexpected Moonlight presence");
                    worldStarting=true;mc.getWindow().setWindowed(1100,750);mc.options.guiScale().set(2);mc.options.renderDistance().set(3);mc.resizeDisplay();mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                    mc.createWorldOpenFlows().createFreshLevel("recipe-runtime-"+System.currentTimeMillis(),new net.minecraft.world.level.LevelSettings("Recipe runtime",net.minecraft.world.level.GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.level.GameRules(),net.minecraft.world.level.WorldDataConfiguration.DEFAULT),new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),r->r.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
                }else if(mc.player!=null&&mc.level!=null&&mc.getSingleplayerServer()!=null&&mc.getOverlay()==null){
                    if(!transferDone&&tick()){
                        transferDone=true;
                    }else if(transferDone&&!manualClicked){
                        manualClicked=true;mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,0,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player);
                    }else if(manualClicked&&!manualVerified&&mc.player.containerMenu.getCarried().is(FabricLoader.getInstance().isModLoaded("polymorph")?Items.IRON_NUGGET:Items.IRON_TRAPDOOR)){
                        manualVerified=true;server(p->{
                            var menu=(com.cappleapple.astralrepository.menu.NexusMenu)p.containerMenu;
                            // The local click predicts the cursor before its packet runs on the server.
                            if(menu.getCarried().isEmpty()){manualVerified=false;return;}
                            check(menu.getCarried().getCount()==1,"Native result pickup has the wrong count");
                            if(FabricLoader.getInstance().isModLoaded("polymorph"))check(menu.grid.isEmpty(),"Selected conflicting native craft did not consume the dandelion");
                            else check(menu.network().snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)),0L)==0,"Native recipe refill did not withdraw the four remaining real iron");
                            startJob(p);
                        });
                    }else if(fullRequestReady&&!fullRequestSent){
                        fullRequestSent=true;
                        com.cappleapple.astralrepository.platform.network.PacketDistributor.sendToServer(new NetworkPackets.Action(mc.player.containerMenu.containerId,NetworkPackets.CRAFT,new ItemStack(Items.IRON_INGOT),1,0,""));
                    }else if(transferDone&&jobsDone){
                        Files.writeString(OUT.resolve("result.txt"),Files.readString(OUT.resolve("result.txt"))+"PASS: native result click consumes the actual selected recipe; actual normal furnace consumes raw iron and fuel; exact conflicting table job completes; "+(FabricLoader.getInstance().isModLoaded("polymorph")?"Polymorph selects and completes the physical conflicting furnace recipe.":"absent Polymorph fails conflicting furnace explicitly and returns its unconsumed dirt/fuel.")+" Real Nexus request discovers a furnace placed after its initial scan, consumes one raw iron, returns one new ingot, retains its network through LIT changes and removes the broken workstation.\n");
                        completed=true;mc.stop();
                    }
                }
            }catch(Throwable error){completed=true;com.mojang.logging.LogUtils.getLogger().error("Recipe runtime failed",error);try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("result.txt"),"FAIL: "+error+"\n");}catch(Exception ignored){}mc.stop();}
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{
            if(failure!=null)return;
            try{
                if(service==null){tickNetwork(server);return;}
                service.tick();if(++jobTicks>1000)throw new AssertionError("Job timeout "+jobStep+" "+service.statuses());
                if(service.activeJobs()==0&&jobTicks>2){
                    boolean conflictAbsent=jobStep==2&&!FabricLoader.getInstance().isModLoaded("polymorph");
                    var snapshot=jobAccess.snapshot();
                    if(conflictAbsent){
                        check(service.statuses().stream().anyMatch(j->j.state().equals("FAILED")),"Absent conflict support must fail explicitly");
                        check(snapshot.getOrDefault(new ItemKey(new ItemStack(Items.DIRT)),0L)==1&&snapshot.getOrDefault(new ItemKey(new ItemStack(Items.COAL)),0L)==2,"Rejected conflict did not return original dirt/fuel");
                    }else{
                        check(snapshot.getOrDefault(new ItemKey(jobAccess.target),0L)==1,"Actual job output absent: "+service.statuses());
                        check(snapshot.getOrDefault(new ItemKey(jobAccess.input),0L)==0,"Job input not consumed");
                        check(snapshot.getOrDefault(new ItemKey(new ItemStack(Items.GOLD_NUGGET)),0L)==0,"Conflicting alternative was produced");
                    }
                    check(!com.cappleapple.astralrepository.crafting.CraftingService.isProcessorReserved(jobAccess.processor),"Machine claim remained after job");
                    service=null;if(++jobStep==3)beginNetwork(server.getPlayerList().getPlayers().get(0));else startJob(server.getPlayerList().getPlayers().get(0));
                }
            }catch(Throwable error){failure=error;}
        });
    }
    private static volatile boolean fullRequestReady;
    private static volatile boolean fullRequestSent;
    private static int networkStage,networkTicks;
    private static long networkIron,networkRaw;
    private static com.cappleapple.astralrepository.network.AstralNetwork discoveredNetwork;
    private static final BlockPos LATE_FURNACE=new BlockPos(26,-59,11),LATE_SHELF=new BlockPos(26,-59,17);
    private static void beginNetwork(ServerPlayer player){
        var level=player.serverLevel();
        check(level.getBlockState(LATE_FURNACE).isAir(),"Late furnace position must begin empty");
        level.setBlockAndUpdate(LATE_SHELF,net.minecraft.world.level.block.Blocks.CHISELED_BOOKSHELF.defaultBlockState());
        var tome=new ItemStack(AstralContent.RECIPE_TOME.get());player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,tome);
        var editor=new com.cappleapple.astralrepository.menu.RecipeTomeMenu(255,player.getInventory(),net.minecraft.world.InteractionHand.OFF_HAND);
        check(editor.inscribe(ResourceLocation.parse("minecraft:iron_ingot_from_smelting_raw_iron")),"Real tome editor rejected smelting recipe");
        ((net.minecraft.world.Container)level.getBlockEntity(LATE_SHELF)).setItem(0,tome);player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,ItemStack.EMPTY);
        var storage=(CrystalNodeBlockEntity)level.getBlockEntity(STORE);
        // Isolate this scenario: the earlier viewer catalogue contains alternative iron ores.
        while(storage.inventory().getSlots()>1)storage.inventory().extractItem(0,Integer.MAX_VALUE,false);
        check(com.cappleapple.astralrepository.platform.items.ItemHandlerHelper.insertItem(storage.inventory(),new ItemStack(Items.RAW_IRON),false).isEmpty(),"Real network rejected smelting ingredient");
        check(com.cappleapple.astralrepository.platform.items.ItemHandlerHelper.insertItem(storage.inventory(),new ItemStack(Items.COAL,2),false).isEmpty(),"Real network rejected smelting fuel");
        networkStage=1;networkTicks=0;
    }
    private static void tickNetwork(net.minecraft.server.MinecraftServer server)throws Exception{
        if(networkStage==0||jobsDone)return;
        if(++networkTicks>1200)throw new AssertionError("Full network discovery timeout stage="+networkStage+" workstations="+(discoveredNetwork==null?null:discoveredNetwork.workstations()));
        var player=server.getPlayerList().getPlayers().get(0);var level=player.serverLevel();
        var network=NetworkManager.get(server).networkAt(GlobalPos.of(level.dimension(),NEXUS));if(network==null)return;
        var furnace=GlobalPos.of(level.dimension(),LATE_FURNACE);
        if(networkStage==1){
            var scans=network.getClass().getDeclaredField("scans");scans.setAccessible(true);
            if(!((java.util.Deque<?>)scans.get(network)).isEmpty()||network.exposedProducts().stream().noneMatch(s->s.is(Items.IRON_INGOT))||network.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.RAW_IRON)),0L)<1)return;
            check(!network.workstations().contains(furnace),"Air was incorrectly discovered as a cooking workstation");
            discoveredNetwork=network;networkIron=network.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)),0L);networkRaw=network.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.RAW_IRON)),0L);
            // No direct network invalidation: ordinary block identity notification must discover this machine.
            level.setBlockAndUpdate(LATE_FURNACE,net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
            networkStage=2;
        }else if(networkStage==2){
            if(!network.workstations().contains(furnace))return;
            check(network==discoveredNetwork,"Unchanged network should retain its service when a workstation is placed");
            fullRequestReady=true;networkStage=3;
        }else if(networkStage==3){
            if(!fullRequestSent||network.crafting().statuses().isEmpty()||network.crafting().activeJobs()!=0)return;
            check(network.crafting().statuses().stream().anyMatch(j->j.state().equals("COMPLETE")),"Real Nexus request failed: "+network.crafting().statuses());
            check(network.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)),0L)==networkIron+1,"Smelted output did not return to the discovered network");
            check(network.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.RAW_IRON)),0L)==networkRaw-1,"Full network job did not consume exactly one raw iron: before="+networkRaw+" after="+network.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.RAW_IRON)),0L));
            check(network.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.COAL)),0L)==1,"Full network furnace did not consume exactly one coal");
            check(!com.cappleapple.astralrepository.crafting.CraftingService.isProcessorReserved(furnace),"Real network did not release its workstation");
            check(network==discoveredNetwork,"Furnace LIT changes recreated the network during processing");
            level.setBlockAndUpdate(LATE_FURNACE,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());networkStage=4;
        }else if(networkStage==4&&!network.workstations().contains(furnace)){
            jobsDone=true;networkStage=5;
        }
    }
    private static void startJob(ServerPlayer player){
        var level=player.serverLevel();var chestPos=new BlockPos(40+jobStep*8,-59,14);var machinePos=chestPos.east(2);level.getChunkAt(chestPos);level.getChunkAt(machinePos);
        level.setBlockAndUpdate(chestPos,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(machinePos,(jobStep==1?net.minecraft.world.level.block.Blocks.CRAFTING_TABLE:net.minecraft.world.level.block.Blocks.FURNACE).defaultBlockState());
        var chest=(net.minecraft.world.Container)level.getBlockEntity(chestPos);var input=new ItemStack(jobStep==0?Items.RAW_IRON:jobStep==1?Items.DANDELION:Items.DIRT);var target=new ItemStack(jobStep==0?Items.IRON_INGOT:Items.IRON_NUGGET);
        chest.setItem(0,input.copy());chest.setItem(1,new ItemStack(Items.COAL,2));
        jobAccess=new JobAccess(level,chest,GlobalPos.of(level.dimension(),chestPos),GlobalPos.of(level.dimension(),machinePos),input,target);
        service=new com.cappleapple.astralrepository.crafting.CraftingService(jobAccess);jobTicks=0;
        var request=service.request(player,target,1);check(request.accepted(),"Actual job was rejected: "+request.message());
    }
    private static final class JobAccess implements com.cappleapple.astralrepository.crafting.CraftingService.NetworkAccess{
        final net.minecraft.server.level.ServerLevel level;final net.minecraft.world.Container chest;final GlobalPos origin,processor;final ItemStack input,target;
        JobAccess(net.minecraft.server.level.ServerLevel level,net.minecraft.world.Container chest,GlobalPos origin,GlobalPos processor,ItemStack input,ItemStack target){this.level=level;this.chest=chest;this.origin=origin;this.processor=processor;this.input=input;this.target=target;}
        public net.minecraft.server.level.ServerLevel level(){return level;}
        public java.util.Map<ItemKey,Long> snapshot(){var result=new java.util.HashMap<ItemKey,Long>();for(int i=0;i<chest.getContainerSize();i++){var s=chest.getItem(i);if(!s.isEmpty())result.merge(new ItemKey(s),(long)s.getCount(),Long::sum);}return result;}
        public ItemStack extract(ItemKey key,int count){var result=ItemStack.EMPTY;for(int i=0;i<chest.getContainerSize()&&count>0;i++)if(key.matches(chest.getItem(i))){var s=chest.removeItem(i,count);if(result.isEmpty())result=s;else result.grow(s.getCount());count-=s.getCount();}return result;}
        public ItemStack insert(ItemStack incoming){var rest=incoming.copy();for(int i=0;i<chest.getContainerSize()&&!rest.isEmpty();i++){var present=chest.getItem(i);if(!present.isEmpty()&&!ItemStack.isSameItemSameComponents(present,rest))continue;int amount=Math.min(rest.getCount(),rest.getMaxStackSize()-present.getCount());if(present.isEmpty())chest.setItem(i,rest.copyWithCount(amount));else present.grow(amount);rest.shrink(amount);}return rest;}
        public java.util.List<GlobalPos> workstations(){return java.util.List.of(processor);}
        public java.util.List<ItemStack> exposedProducts(){return java.util.List.of(target);}
        public GlobalPos origin(){return origin;}
        public void visual(GlobalPos from,GlobalPos to,ItemStack stack,int ticks){}
    }
    private static final BlockPos NEXUS = new BlockPos(26, -59, 14), STORE = NEXUS.east(2);
    private static final ResourceLocation RECIPE = ResourceLocation.withDefaultNamespace("iron_trapdoor");
    private static final Path OUT = Path.of("recipe-runtime-captures");
    private static int phase, ticks;
    private static volatile boolean ready;
    private static volatile Throwable failure;

    public static boolean tick() throws Exception {
        var mc = Minecraft.getInstance();
        if (failure != null) throw new AssertionError("Recipe transfer server check", failure);
        if (++ticks > 900) throw new AssertionError("Recipe transfer timeout in phase " + phase);
        if (phase == 0) {
            phase = 1;
            server(p -> {
                p.closeContainer();
                var level = p.serverLevel();
                level.getChunkAt(NEXUS);
                level.setBlockAndUpdate(NEXUS, AstralContent.STORAGE_NEXUS.get().defaultBlockState());
                level.setBlockAndUpdate(STORE, AstralContent.SEED_STORAGE_CRYSTAL.get().defaultBlockState());
                ((CrystalNodeBlockEntity) level.getBlockEntity(NEXUS)).setChannel(12);
                var storage = (CrystalNodeBlockEntity) level.getBlockEntity(STORE);
                storage.setChannel(12);
                int slot = 0;
                for (var item : BuiltInRegistries.ITEM) {
                    if (item == Items.AIR || item == Items.IRON_INGOT) continue;
                    check(storage.inventory().insertItem(slot++, new ItemStack(item), false).isEmpty(), "Fixture storage rejected item");
                    if (slot == 80) break;
                }
                check(storage.inventory().insertItem(slot, new ItemStack(Items.IRON_INGOT, 8), false).isEmpty(), "Fixture storage rejected iron");
                p.getInventory().clearContent();
                p.connection.teleport(27.5, -59, 17, 180, 20);
            });
        } else if (phase == 1 && ready && ticks > 100) {
            ready = false;
            server(p -> NetworkManager.open(p, GlobalPos.of(p.level().dimension(), NEXUS)));
            next(2);
        } else if (phase == 2 && mc.screen instanceof NexusScreen screen && ticks > 20) {
            if (!viewerReady()) return false;
            RecipeTransfers.inventory(screen.getMenu());
            var search = (net.minecraft.client.gui.components.EditBox) screen.children().stream()
                    .filter(w -> w instanceof net.minecraft.client.gui.components.EditBox e && e.getMessage().getString().equals("Search storage")).findFirst().orElseThrow();
            search.setValue("diamond");
            next(3);
        } else if (phase == 3 && mc.screen instanceof NexusScreen screen && ticks > 20) {
            var stock = screen.getMenu().recipeInventory();
            if (cacheNetworkStep==0) {
                if (stock.size() < 81) return false;
                check(stock.getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)), 0L) == 8, "Off-page iron is absent from recipe availability");
            }
            check(screen.getMenu().entries.stream().noneMatch(e -> e.stack().is(Items.IRON_INGOT)), "Search did not hide the ingredient");
            if (!reloadChecked){checkRecipeEpoch(screen);reloadChecked=true;}
            if (!moonlightChecked&&FabricLoader.getInstance().isModLoaded("moonlight")){checkMoonlight(screen);moonlightChecked=true;}
            if (!checkCacheNetworkChanges(screen)) return false;
            if (FabricLoader.getInstance().isModLoaded("emi")) Emi.fill(screen);
            else if (FabricLoader.getInstance().isModLoaded("jei")) Jei.fill(screen);
            else RecipeTransfers.fill(screen.getMenu(),RECIPE,1);
            next(4);
        } else if (phase == 4 && mc.screen instanceof NexusScreen screen && ticks > 20) {
            if (!screen.getMenu().slots.getFirst().getItem().is(Items.IRON_TRAPDOOR)) return false;
            check(screen.getMenu().grid.getItem(0).is(Items.IRON_INGOT)
                    && screen.getMenu().grid.getItem(1).is(Items.IRON_INGOT)
                    && screen.getMenu().grid.getItem(3).is(Items.IRON_INGOT)
                    && screen.getMenu().grid.getItem(4).is(Items.IRON_INGOT), "Transferred shaped grid has wrong positions");
            check(screen.getMenu().grid.getItem(2).isEmpty(), "Transfer filled an unused shaped position");
            if (screen.getMenu().recipeInventory().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)), 0L) != 8) return false;
            shot("recipe_transfer_" + viewer() + ".png");
            ready = false;
            server(p -> {
                var menu = (com.cappleapple.astralrepository.menu.NexusMenu) p.containerMenu;
                check(menu.network().snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)), 0L) == 4, "Transfer did not consume exactly four stored iron");
                check(p.getInventory().isEmpty() && menu.getCarried().isEmpty(), "Transfer unexpectedly used player inventory or cursor");
            });
            next(5);
        } else if (phase == 5 && ready) {
            if (FabricLoader.getInstance().isModLoaded("polymorph")) {
                ready = false;
                server(p -> {
                    var menu = (com.cappleapple.astralrepository.menu.NexusMenu) p.containerMenu;
                    menu.action(new NetworkPackets.Action(menu.containerId, NetworkPackets.CLEAR_GRID, ItemStack.EMPTY, 0, 0, ""));
                    menu.grid.setItem(0, new ItemStack(Items.DANDELION));
                    menu.slotsChanged(menu.grid);
                });
                next(6);
                return false;
            }
            return finish();
        } else if (phase == 6 && ready && ticks > 20 && mc.screen instanceof NexusScreen screen) {
            check(!screen.getMenu().slots.getFirst().getItem().isEmpty(), "Conflicting crafting recipe has no initial result");
            selectPolymorph(screen, "conflict_crafting_a");
            next(7);
        } else if (phase == 7 && ticks > 20 && mc.screen instanceof NexusScreen screen) {
            if (!screen.getMenu().slots.getFirst().getItem().is(Items.GOLD_NUGGET)) return false;
            shot("polymorph_gold_" + viewer() + ".png");
            selectPolymorph(screen, "conflict_crafting_z");
            next(8);
        } else if (phase == 8 && ticks > 20 && mc.screen instanceof NexusScreen screen) {
            if (!screen.getMenu().slots.getFirst().getItem().is(Items.IRON_NUGGET)) return false;
            check(screen.getMenu().grid.getItem(0).is(Items.DANDELION), "Polymorph selection consumed the ingredient");
            shot("polymorph_iron_" + viewer() + ".png");
            return finish();
        }
        return false;
    }

    private static void checkRecipeEpoch(NexusScreen screen)throws Exception {
        var before=RecipeTransfers.inventory(screen.getMenu());
        Object wrapper=viewerInventory(screen);long epoch=RecipeTransfers.recipeEpoch();
        net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents.TAGS_LOADED.invoker().onTagsLoaded(Minecraft.getInstance().level.registryAccess(),true);
        check(RecipeTransfers.recipeEpoch()>epoch,"Actual Fabric client tag callback did not invalidate recipes");
        var after=RecipeTransfers.inventory(screen.getMenu());
        check(after!=before&&after.equals(before),"Client tag reload did not replace the equal-content detached cache");
        if(wrapper!=null)Emi.checkRecipeEpoch(screen,wrapper);
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve("recipe-epoch.json"),new com.google.gson.Gson().toJson(java.util.Map.of("actualClientTagCallback",true,"epochBefore",epoch,"epochAfter",RecipeTransfers.recipeEpoch(),"equalStockCacheReplaced",true,"emiWrapperChecked",wrapper!=null)));
    }

    private static void checkMoonlight(NexusScreen screen)throws Exception {
        var menu=screen.getMenu();var original=menu.grid.getItem(0).copy();var cursor=menu.getCarried().copy();
        Class<?> blockType=Class.forName("net.mehvahdjukaar.moonlight.api.set.BlockType");
        Class<?> registry=Class.forName("net.mehvahdjukaar.moonlight.api.set.wood.WoodTypeRegistry");
        Object oak=registry.getMethod("getValue",String.class).invoke(null,"minecraft:oak");
        Object spruce=registry.getMethod("getValue",String.class).invoke(null,"minecraft:spruce");
        Class<?> implementation=Class.forName("net.mehvahdjukaar.moonlight.api.resources.recipe.platform.BlockTypeSwapIngredientImpl");
        var factory=implementation.getMethod("create",net.minecraft.world.item.crafting.Ingredient.class,blockType,blockType);
        var inputs=new net.minecraft.world.item.Item[]{Items.OAK_PLANKS,Items.OAK_LOG,Items.OAK_SLAB,Items.OAK_STAIRS,Items.OAK_DOOR,Items.OAK_FENCE,Items.OAK_TRAPDOOR,Items.OAK_BUTTON};
        int cases=0,queries=0;
        try{
            for(boolean nonSimple:new boolean[]{false,true})for(var input:inputs){
                var inner=net.minecraft.world.item.crafting.Ingredient.of(input);
                if(nonSimple)inner=new net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient(){
                    @Override public boolean test(ItemStack stack){return false;}
                    @Override public java.util.List<ItemStack> getMatchingStacks(){return java.util.List.of(new ItemStack(input));}
                    @Override public boolean requiresTesting(){return true;}
                    @Override public net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer<?> getSerializer(){return null;}
                }.toVanilla();
                var swap=(net.minecraft.world.item.crafting.Ingredient)factory.invoke(null,inner,oak,spruce);
                var custom=((net.fabricmc.fabric.api.recipe.v1.ingredient.FabricIngredient)(Object)swap).getCustomIngredient();
                check(custom!=null&&custom.getClass()==implementation,"Fixture did not construct the exact real Moonlight ingredient");
                check(custom.requiresTesting()==nonSimple,"Moonlight did not preserve the tested requiresTesting contract");
                if(input==Items.OAK_PLANKS)check(swap.test(new ItemStack(Items.SPRUCE_PLANKS))&&!swap.test(new ItemStack(Items.OAK_PLANKS)),"Actual Moonlight oak-to-spruce conversion differs");
                var recipe=new net.minecraft.world.item.crafting.ShapelessRecipe("",net.minecraft.world.item.crafting.CraftingBookCategory.MISC,new ItemStack(Items.STICK),net.minecraft.core.NonNullList.of(net.minecraft.world.item.crafting.Ingredient.EMPTY,swap));
                for(int state=0;state<6;state++){
                    menu.grid.setItem(0,state==0||state==4?ItemStack.EMPTY:new ItemStack(state==3?Items.OAK_PLANKS:Items.SPRUCE_PLANKS,state==5?2:1));
                    if(state==2||state==5)nameProbe(menu.grid.getItem(0));
                    menu.setCarried(state==4?new ItemStack(Items.SPRUCE_PLANKS,2):state==5?new ItemStack(Items.OAK_PLANKS,2):ItemStack.EMPTY);
                    var stock=new java.util.LinkedHashMap<ItemKey,Long>(menu.recipeInventory());
                    if(!menu.getCarried().isEmpty())stock.merge(new ItemKey(menu.getCarried()),(long)menu.getCarried().getCount(),Long::sum);
                    var ingredients=com.cappleapple.astralrepository.menu.NexusMenu.recipeIngredients(recipe,stock);
                    boolean expected=ingredients!=null&&com.cappleapple.astralrepository.menu.RecipeTransferPlan.assign(ingredients,stock,1,key->key.sample().getMaxStackSize())!=null;
                    for(int repeat=0;repeat<2;repeat++){check(RecipeTransfers.canFill(menu,recipe)==expected,"Moonlight differential mismatch input="+input+" nonSimple="+nonSimple+" state="+state);queries++;}
                    cases++;
                }
            }
        }finally{menu.grid.setItem(0,original);menu.setCarried(cursor);}
        check(cases==96&&queries==192,"Incomplete Moonlight differential matrix");
        Files.createDirectories(OUT);
        String version=FabricLoader.getInstance().getModContainer("moonlight").orElseThrow().getMetadata().getVersion().getFriendlyString();
        Files.writeString(OUT.resolve("moonlight-differential.json"),new com.google.gson.Gson().toJson(java.util.Map.of("version",version,"cases",cases,"availabilityQueries",queries,"actualExactClass",implementation.getName(),"requiresTestingInnerCovered",true,"componentAndCursorVariants",true,"matchesUnoptimizedAssignment",true)));
    }

    private static boolean checkCacheNetworkChanges(NexusScreen screen)throws Exception {
        var menu=screen.getMenu();var iron=new ItemKey(new ItemStack(Items.IRON_INGOT));
        var recipe=(CraftingRecipe)Minecraft.getInstance().level.getRecipeManager().byKey(RECIPE).orElseThrow().value();
        if(cacheNetworkStep==0){
            if(!FabricLoader.getInstance().isModLoaded("moonlight"))check(!com.cappleapple.astralrepository.compat.MoonlightRecipeCompatibility.isItemOnly(net.minecraft.world.item.crafting.Ingredient.of(Items.IRON_INGOT)),"Absent Moonlight adapter did not remain optional");
            var initial=RecipeTransfers.inventory(menu);
            for(int repeat=0;repeat<200;repeat++)check(RecipeTransfers.inventory(menu)==initial&&RecipeTransfers.canFill(menu,recipe),"Stable availability changed or rebuilt its inventory");
            try{initial.put(iron,0L);throw new AssertionError("Cached stock must be immutable");}catch(UnsupportedOperationException expected){}
            menu.grid.setItem(0,new ItemStack(Items.IRON_INGOT));
            var localOne=RecipeTransfers.inventory(menu);check(localOne!=initial&&localOne.get(iron)==9,"Local stack did not invalidate stock");
            Object first=viewerInventory(screen);
            menu.grid.getItem(0).setCount(2);
            var localTwo=RecipeTransfers.inventory(menu);check(localTwo!=localOne&&localTwo.get(iron)==10,"In-place local count change did not invalidate stock");
            changedViewer(screen,first,"local count");first=viewerInventory(screen);
            nameProbe(menu.grid.getItem(0));
            var localNamed=RecipeTransfers.inventory(menu);check(localNamed!=localTwo&&localNamed.get(iron)==8,"In-place local components did not invalidate stock");
            changedViewer(screen,first,"local components");menu.grid.setItem(0,ItemStack.EMPTY);
            menu.setCarried(new ItemStack(Items.IRON_INGOT));
            var cursorOne=RecipeTransfers.inventory(menu);check(cursorOne.get(iron)==8,"Cursor was double-counted in the viewer stock map");first=viewerInventory(screen);
            menu.getCarried().setCount(2);
            var cursorTwo=RecipeTransfers.inventory(menu);check(cursorTwo!=cursorOne&&cursorTwo.get(iron)==8,"In-place cursor count did not invalidate stock");
            changedViewer(screen,first,"cursor count");first=viewerInventory(screen);nameProbe(menu.getCarried());
            check(RecipeTransfers.inventory(menu)!=cursorTwo,"In-place cursor components did not invalidate stock");changedViewer(screen,first,"cursor components");
            menu.setCarried(ItemStack.EMPTY);
            cacheNetworkBefore=RecipeTransfers.inventory(menu);cacheViewerBefore=viewerInventory(screen);cacheNetworkStep=1;
            server(p->{
                var storage=(CrystalNodeBlockEntity)p.serverLevel().getBlockEntity(STORE);
                for(int slot=0;slot<storage.inventory().getSlots();slot++)if(storage.inventory().getStackInSlot(slot).is(Items.IRON_INGOT)){
                    cacheHeldIron=storage.inventory().extractItem(slot,8,false);break;
                }
                check(cacheHeldIron.is(Items.IRON_INGOT)&&cacheHeldIron.getCount()==8,"Cache fixture did not retain exactly eight real iron");
            });return false;
        }
        if(cacheNetworkStep==1){
            var current=RecipeTransfers.inventory(menu);if(current.getOrDefault(iron,0L)!=0)return false;
            check(current!=cacheNetworkBefore&&cacheNetworkBefore.get(iron)==8,"Committed removal failed snapshot replacement/detachment");
            check(!RecipeTransfers.canFill(menu,recipe),"Removed real ingredients stayed craftable in cache");changedViewer(screen,cacheViewerBefore,"committed network removal");
            cacheNetworkBefore=current;cacheViewerBefore=viewerInventory(screen);cacheNetworkStep=2;
            server(p->{var storage=(CrystalNodeBlockEntity)p.serverLevel().getBlockEntity(STORE);check(com.cappleapple.astralrepository.platform.items.ItemHandlerHelper.insertItem(storage.inventory(),cacheHeldIron,false).isEmpty(),"Cache fixture could not restore the original iron");cacheHeldIron=ItemStack.EMPTY;});return false;
        }
        if(cacheNetworkStep==2){
            var current=RecipeTransfers.inventory(menu);if(current.getOrDefault(iron,0L)!=8)return false;
            check(current!=cacheNetworkBefore&&RecipeTransfers.canFill(menu,recipe),"Restored real ingredients stayed uncraftable in cache");changedViewer(screen,cacheViewerBefore,"committed network restoration");cacheNetworkStep=3;
        }
        return true;
    }
    private static void nameProbe(ItemStack stack){stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("cache probe"));}
    private static Object viewerInventory(NexusScreen screen)throws Exception{return FabricLoader.getInstance().isModLoaded("emi")?Emi.handler(screen).getInventory(screen):null;}
    private static void changedViewer(NexusScreen screen,Object before,String reason)throws Exception {if(before!=null)check(viewerInventory(screen)!=before,"EMI retained stale inventory after "+reason);}

    private static boolean finish() throws Exception {
            var mc = Minecraft.getInstance();
            check(!mc.mouseHandler.isMouseGrabbed(), "Test client grabbed the mouse");
            check(mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER) == 0, "Test client audio is not muted");
            Files.createDirectories(OUT);
            Files.writeString(OUT.resolve("result.txt"), "PASS: " + viewer() + " availability includes 81 stored identities beyond the 54-entry page; hidden iron fills an actual shaped recipe through the server packet, preserving all eight iron across storage/grid. Stable cached checks and local count, component, cursor and real committed network removal/restoration invalidation pass. Actual client tag reload invalidation passes."
                    + (moonlightChecked ? " Actual Moonlight custom ingredient differential checks pass across 96 cases and 192 availability checks, including a requiresTesting inner ingredient and component/cursor variants." : "")
                    + (FabricLoader.getInstance().isModLoaded("polymorph") ? " Polymorph's actual Nexus widget selects both conflicting outputs without consuming ingredients." : "") + "\n");
            return true;
    }

    private static void selectPolymorph(NexusScreen screen, String recipe) throws Exception {
        var type = Class.forName("com.illusivesoulworks.polymorph.api.client.PolymorphWidgets");
        Object widgets = type.getMethod("getInstance").invoke(null);
        Object widget = type.getMethod("getWidgetOrDefault", net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class).invoke(widgets, screen);
        check(widget != null, "Polymorph did not discover the Nexus crafting result");
        var api = Class.forName("com.illusivesoulworks.polymorph.api.client.base.IRecipesWidget");
        check(api.getMethod("getOutputSlot").invoke(widget) == screen.getMenu().slots.getFirst(), "Polymorph targets the wrong Nexus result slot");
        api.getMethod("selectRecipe", ResourceLocation.class).invoke(widget, ResourceLocation.fromNamespaceAndPath("astral_repository_recipe_test", recipe));
    }

    private static boolean viewerReady() throws Exception {
        if (FabricLoader.getInstance().isModLoaded("emi")) return com.cappleapple.astralrepository.compat.AstralEmiPlugin.registered
                && !dev.emi.emi.api.EmiApi.getRecipeManager().getRecipes().isEmpty();
        if (!FabricLoader.getInstance().isModLoaded("jei")) return true;
        return Jei.runtime() != null;
    }

    private static final class Emi {
        static com.cappleapple.astralrepository.compat.AstralEmiPlugin.RecipeHandler handler(NexusScreen screen)throws Exception {
            var registry=Class.forName("dev.emi.emi.registry.EmiRecipeFiller");
            var handlers=(java.util.List<?>)registry.getMethod("getAllHandlers",net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class).invoke(null,screen);
            return (com.cappleapple.astralrepository.compat.AstralEmiPlugin.RecipeHandler)handlers.stream().filter(h->h instanceof com.cappleapple.astralrepository.compat.AstralEmiPlugin.RecipeHandler).findFirst().orElseThrow(()->new AssertionError("EMI did not register the Nexus handler"));
        }
        static void checkRecipeEpoch(NexusScreen screen,Object before)throws Exception {
            var after=handler(screen).getInventory(screen);
            check(after!=before&&!after.isEqual((dev.emi.emi.api.recipe.EmiPlayerInventory)before),"EMI treated changed recipe/tag epoch as equal inventory");
        }
        static void fill(NexusScreen screen) throws Exception {
            var handler=handler(screen);
            var recipe = dev.emi.emi.api.EmiApi.getRecipeManager().getRecipes().stream()
                    .filter(r -> r.getBackingRecipe() != null && r.getBackingRecipe().id().equals(RECIPE)).findFirst().orElseThrow();
            var inventory = handler.getInventory(screen);
            var context = new dev.emi.emi.api.recipe.handler.EmiCraftContext<>(screen, inventory,
                    dev.emi.emi.api.recipe.handler.EmiCraftContext.Type.FILL_BUTTON,
                    dev.emi.emi.api.recipe.handler.EmiCraftContext.Destination.NONE, 1);
            check(handler.supportsRecipe(recipe) && handler.canCraft(recipe, context), "EMI disabled quick fill despite available network ingredients");
            for(int repeat=0;repeat<200;repeat++)check(handler.getInventory(screen)==inventory&&inventory.isEqual(inventory)&&handler.canCraft(recipe,context),"Registered EMI handler did not reuse stable inventory/availability");
            check(handler.craft(recipe, context), "EMI quick-fill handler rejected recipe");
        }
    }

    private static final class Jei {
        static mezz.jei.api.runtime.IJeiRuntime runtime() throws Exception {
            var field = com.cappleapple.astralrepository.compat.AstralJeiPlugin.class.getDeclaredField("runtime");
            field.setAccessible(true);
            return (mezz.jei.api.runtime.IJeiRuntime) field.get(null);
        }

        static void fill(NexusScreen screen) throws Exception {
            var runtime = runtime();
            var category = runtime.getRecipeManager().getRecipeCategory(mezz.jei.api.constants.RecipeTypes.CRAFTING);
            var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(screen.getMenu(), category).orElseThrow();
            check(handler instanceof com.cappleapple.astralrepository.compat.AstralJeiPlugin.RecipeHandler, "JEI did not register the Nexus transfer handler");
            @SuppressWarnings("unchecked") var recipe = (RecipeHolder<CraftingRecipe>) (RecipeHolder<?>) Minecraft.getInstance().level.getRecipeManager().byKey(RECIPE).orElseThrow();
            check(handler.transferRecipe(screen.getMenu(), recipe, null, Minecraft.getInstance().player, false, false) == null,
                    "JEI disabled quick fill despite available network ingredients");
            for(int repeat=0;repeat<200;repeat++)check(handler.transferRecipe(screen.getMenu(),recipe,null,Minecraft.getInstance().player,false,false)==null,"Registered JEI stable availability changed");
            check(handler.transferRecipe(screen.getMenu(), recipe, null, Minecraft.getInstance().player, false, true) == null,
                    "JEI quick-fill handler rejected recipe");
        }
    }

    private static String viewer() { return FabricLoader.getInstance().isModLoaded("emi") ? "EMI" : FabricLoader.getInstance().isModLoaded("jei") ? "JEI" : "plain"; }
    private static void next(int value) { phase = value; ticks = 0; }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())); ready = true; }
            catch (Throwable error) { failure = error; }
        });
    }
    private static void shot(String name) throws Exception {
        Files.createDirectories(OUT);
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { image.writeToFile(OUT.resolve(name)); }
    }
}
