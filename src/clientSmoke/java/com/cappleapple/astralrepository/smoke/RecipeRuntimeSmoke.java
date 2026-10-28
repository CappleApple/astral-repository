package com.cappleapple.astralrepository.smoke;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.client.NexusScreen;
import com.cappleapple.astralrepository.client.RecipeTransfers;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.crafting.CraftingService;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.neoforged.fml.ModList;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Loaded release-JAR recipe checks, including the registered JEI handler when installed. */
public final class RecipeRuntimeSmoke {
    private static final Path OUT = Path.of("captures");
    private static final BlockPos NEXUS = new BlockPos(26,-59,14), STORE = NEXUS.east(2);
    private static final Identifier TRAPDOOR = Identifier.withDefaultNamespace("iron_trapdoor");
    private static final List<MachineFixture> machines = new ArrayList<>();
    private static CompletableFuture<Void> pending;
    private static int phase, ticks;
    private static boolean observedLit, cancelled, networkReady;
    private static long networkIronBefore;

    public static boolean tick() throws Exception {
        var mc = Minecraft.getInstance();
        if (++ticks > 1800) throw new AssertionError("Recipe runtime timeout in phase " + phase);
        if (pending != null) { if (!pending.isDone()) return false; pending.join(); pending = null; }
        if (phase == 0) {
            check(Boolean.getBoolean("astral_repository.packagedAudit"),"Use the packaged validation init script for this fixture");
            check(ModList.get().isLoaded("jei") == Boolean.getBoolean("astral_repository.expectJei"), "JEI profile does not match loaded mods");
            server(p -> {
                p.closeContainer(); var level = p.level(); level.getChunkAt(NEXUS);
                level.setBlockAndUpdate(NEXUS,AstralContent.STORAGE_NEXUS.get().defaultBlockState());
                level.setBlockAndUpdate(STORE,AstralContent.SEED_STORAGE_CRYSTAL.get().defaultBlockState());
                ((CrystalNodeBlockEntity)level.getBlockEntity(NEXUS)).setChannel(12);
                var storage = (CrystalNodeBlockEntity)level.getBlockEntity(STORE); storage.setChannel(12);
                int slot=0;
                for (var item : BuiltInRegistries.ITEM) {
                    if (item==Items.AIR || item==Items.IRON_INGOT) continue;
                    check(storage.inventory().insertItem(slot++,new ItemStack(item),false).isEmpty(),"Fixture storage rejected item");
                    if (slot==80) break;
                }
                check(storage.inventory().insertItem(slot,new ItemStack(Items.IRON_INGOT,8),false).isEmpty(),"Fixture storage rejected iron");
                p.getInventory().clearContent(); p.teleportTo(level,27.5,-59,17,Set.of(),180,20,false);
            }); next(1);
        } else if (phase==1 && ticks>100) {
            server(p -> NetworkManager.open(p,GlobalPos.of(p.level().dimension(),NEXUS))); next(2);
        } else if (phase==2 && ticks>20 && mc.gui.screen() instanceof NexusScreen screen) {
            if (ModList.get().isLoaded("jei") && Jei.runtime()==null) return false;
            RecipeTransfers.inventory(screen.getMenu());
            var search=(net.minecraft.client.gui.components.EditBox)screen.children().stream()
                    .filter(w -> w instanceof net.minecraft.client.gui.components.EditBox e && e.getMessage().getString().equals("Search storage")).findFirst().orElseThrow();
            search.setValue("diamond"); next(3);
        } else if (phase==3 && ticks>20 && mc.gui.screen() instanceof NexusScreen screen) {
            if (screen.getMenu().recipeInventory().size()<81) return false;
            check(screen.getMenu().recipeInventory().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)),0L)==8,"Off-page stock missing");
            check(screen.getMenu().entries.stream().noneMatch(e -> e.stack().is(Items.IRON_INGOT)),"Search did not hide iron");
            if (ModList.get().isLoaded("jei")) Jei.fill(screen); else RecipeTransfers.fill(screen.getMenu(),TRAPDOOR,1);
            next(4);
        } else if (phase==4 && ticks>20 && mc.gui.screen() instanceof NexusScreen screen) {
            if (!screen.getMenu().slots.getFirst().getItem().is(Items.IRON_TRAPDOOR)) return false;
            check(screen.getMenu().grid.getItem(0).is(Items.IRON_INGOT)&&screen.getMenu().grid.getItem(1).is(Items.IRON_INGOT)
                    &&screen.getMenu().grid.getItem(3).is(Items.IRON_INGOT)&&screen.getMenu().grid.getItem(4).is(Items.IRON_INGOT)
                    &&screen.getMenu().grid.getItem(2).isEmpty(),"Wrong shaped transfer positions");
            if (screen.getMenu().recipeInventory().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)),0L)!=8) return false;
            capture("recipe-transfer.png");
            server(p -> {
                var menu=(NexusMenu)p.containerMenu;
                check(menu.network().snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)),0L)==4,"Transfer conservation");
                check(p.getInventory().isEmpty()&&menu.getCarried().isEmpty(),"Transfer changed player inventory/cursor");
                menu.action(new NetworkPackets.Action(menu.containerId,NetworkPackets.CLEAR_GRID,ItemStack.EMPTY,0,0,""));
                p.getInventory().setItem(9,new ItemStack(Items.DANDELION));
                menu.fillRecipe(new NetworkPackets.FillRecipe(menu.containerId,id("conflict_crafting_z"),1));
                check(menu.slots.getFirst().getItem().is(Items.IRON_NUGGET),"Transfer lost the selected conflicting recipe");
                menu.clicked(0,0,ContainerInput.PICKUP,p);
                check(menu.getCarried().is(Items.IRON_NUGGET)&&menu.grid.isEmpty(),"Selected result did not consume exactly one real ingredient");
                menu.setCarried(ItemStack.EMPTY);
                startMachines(p);
            }); next(5);
        } else if (phase==5) {
            server(p -> {
                for (var fixture:machines) fixture.service.tick();
                var first=machines.getFirst();
                observedLit |= p.level().getBlockState(first.processor).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
                var cancellation=machines.get(4);
                if (!cancelled && ((Container)p.level().getBlockEntity(cancellation.processor)).getItem(0).is(Items.RAW_IRON)) {
                    check(cancellation.service.cancel(cancellation.request.jobId()),"Active furnace cancellation rejected");
                    check(!cancellation.service.cancel(cancellation.request.jobId()),"Cancellation replay accepted"); cancelled=true;
                }
            });
            if (ticks>300) next(6);
        } else if (phase==6) {
            server(p -> {
                for (int i=0;i<3;i++) {
                    var fixture=machines.get(i);
                    check(fixture.service.activeJobs()==0&&fixture.count(fixture.target)==1&&fixture.count(fixture.input)==0,
                            "Real cooking job failed: "+i+" "+fixture.service.statuses());
                    check(!CraftingService.isProcessorReserved(fixture.at(fixture.processor)),"Cooking processor reservation leaked");
                }
                check(observedLit,"The actual furnace never ignited");
                var conflict=machines.get(3);
                check(conflict.service.activeJobs()==0,"Unsupported furnace conflict stayed locked");
                check(conflict.count(Items.DIRT)==1&&conflict.count(conflict.target)==0,"Conflicting furnace did not return the owned ingredient");
                check(conflict.service.statuses().stream().anyMatch(s -> s.message().contains("conflicting recipe")),"Unsupported conflict has no clear failure");
                var cancellation=machines.get(4);
                check(cancelled&&cancellation.count(Items.RAW_IRON)==1,"Cancellation did not return exactly one physical input");
                var table=machines.get(5);
                check(table.service.activeJobs()==0&&table.count(Items.IRON_NUGGET)==1&&table.count(Items.DANDELION)==0,"Exact conflicting table recipe failed");
            }); next(7);
        } else if (phase==7) {
            server(p -> {
                var network=((NexusMenu)p.containerMenu).network();
                try{var scans=network.getClass().getDeclaredField("scans");scans.setAccessible(true);networkReady=((Collection<?>)scans.get(network)).isEmpty();}
                catch(ReflectiveOperationException error){throw new AssertionError(error);}
            });next(8);
        } else if(phase==8){
            if(!networkReady){next(7);return false;}
            server(p -> {
                var menu=(NexusMenu)p.containerMenu;var level=p.level();networkIronBefore=menu.network().snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)),0L);
                level.setBlockAndUpdate(NEXUS.north(2),Blocks.FURNACE.defaultBlockState());
                level.setBlockAndUpdate(NEXUS.west(2),Blocks.CRAFTING_TABLE.defaultBlockState());
                level.setBlockAndUpdate(NEXUS.south(2),Blocks.CHISELED_BOOKSHELF.defaultBlockState());
                var tome=new ItemStack(AstralContent.RECIPE_TOME.get());var data=new net.minecraft.nbt.CompoundTag();
                data.put("Output",com.cappleapple.astralrepository.port.NbtCodecs.save(new ItemStack(Items.IRON_INGOT),level.registryAccess()));data.putString("OutputId","minecraft:iron_ingot");
                tome.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(data));
                ((Container)level.getBlockEntity(NEXUS.south(2))).setItem(0,tome);
                check(menu.network().insertAt(new ItemStack(Items.RAW_IRON),menu.origin).isEmpty()&&menu.network().insertAt(new ItemStack(Items.COAL),menu.origin).isEmpty(),"Network rejected real cooking inputs");networkReady=false;
            });next(9);
        } else if(phase==9){
            if(networkReady){server(p -> {var menu=(NexusMenu)p.containerMenu;var request=menu.network().crafting().request(p,new ItemStack(Items.IRON_INGOT),1);check(request.accepted(),"Actual network rejected late-furnace recipe");});next(10);return false;}
            server(p -> {var network=((NexusMenu)p.containerMenu).network();networkReady=network.workstations().contains(GlobalPos.of(p.level().dimension(),NEXUS.north(2)))&&network.workstations().contains(GlobalPos.of(p.level().dimension(),NEXUS.west(2)))&&network.exposedProducts().stream().anyMatch(s -> s.is(Items.IRON_INGOT))&&network.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.RAW_IRON)),0L)==1;});
        } else if(phase==10){
            if(ticks<300)return false;
            server(p -> {var network=((NexusMenu)p.containerMenu).network();check(network.crafting().activeJobs()==0&&network.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)),0L)==networkIronBefore+1,"Actual Nexus network did not produce an additional smelted ingot: "+network.crafting().statuses());check(network.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.RAW_IRON)),0L)==0,"Actual network used a table conversion instead of the late furnace");});next(11);
        } else if(phase==11){
            check(!mc.mouseHandler.isMouseGrabbed(),"Test grabbed the mouse");
            check(mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)==0,"Test audio was not muted");
            Files.createDirectories(OUT);
            Files.writeString(OUT.resolve("recipe-runtime-result.txt"),"PASS: packaged release; "+(ModList.get().isLoaded("jei")?"registered JEI handler":"optional-viewer absence")
                    +"; 81 stored identities, off-search shaped transfer and exact conservation; conflicting grid/table selection; actual smelting/blasting/smoking and fuel; physical cancellation; unsupported furnace conflict fails and refunds without locking; late furnace discovered after completed scan and smelts through actual Nexus with a table also available.\n");
            return true;
        }
        return false;
    }

    private static void startMachines(ServerPlayer player) {
        var level=player.level();
        machines.add(new MachineFixture(level,0,Blocks.FURNACE,Items.RAW_IRON,Items.IRON_INGOT));
        machines.add(new MachineFixture(level,1,Blocks.BLAST_FURNACE,Items.RAW_GOLD,Items.GOLD_INGOT));
        machines.add(new MachineFixture(level,2,Blocks.SMOKER,Items.PORKCHOP,Items.COOKED_PORKCHOP));
        var defaultRecipe=level.getServer().getRecipeManager().getRecipeFor(RecipeType.SMELTING,new SingleRecipeInput(new ItemStack(Items.DIRT)),level).orElseThrow();
        var defaultResult=defaultRecipe.value().assemble(new SingleRecipeInput(new ItemStack(Items.DIRT)));
        machines.add(new MachineFixture(level,3,Blocks.FURNACE,Items.DIRT,defaultResult.is(Items.GOLD_NUGGET)?Items.IRON_NUGGET:Items.GOLD_NUGGET));
        machines.add(new MachineFixture(level,4,Blocks.FURNACE,Items.RAW_IRON,Items.IRON_INGOT));
        machines.add(new MachineFixture(level,5,Blocks.CRAFTING_TABLE,Items.DANDELION,Items.IRON_NUGGET));
        for(var fixture:machines) {
            fixture.request=fixture.service.request(player,new ItemStack(fixture.target),1);
            check(fixture.request.accepted(),"Fixture job was rejected: "+fixture.request.message());
        }
    }

    private static final class MachineFixture implements CraftingService.NetworkAccess {
        final ServerLevel level; final BlockPos chestPos,processor; final Container chest;
        final Item input,target; final CraftingService service; CraftingService.RequestResult request;
        MachineFixture(ServerLevel level,int index,Block machine,Item input,Item target) {
            this.level=level;this.input=input;this.target=target;chestPos=new BlockPos(50+index*4,-59,20);processor=chestPos.east();
            level.getChunkAt(chestPos);level.setBlockAndUpdate(chestPos,Blocks.CHEST.defaultBlockState());level.setBlockAndUpdate(processor,machine.defaultBlockState());
            chest=(Container)level.getBlockEntity(chestPos);chest.setItem(0,new ItemStack(input));chest.setItem(1,new ItemStack(Items.COAL,2));service=new CraftingService(this);
        }
        long count(Item item){return snapshot().entrySet().stream().filter(e -> e.getKey().sample().is(item)).mapToLong(Map.Entry::getValue).sum();}
        GlobalPos at(BlockPos position){return GlobalPos.of(level.dimension(),position);}
        public ServerLevel level(){return level;}
        public Map<ItemKey,Long> snapshot(){var result=new LinkedHashMap<ItemKey,Long>();for(int i=0;i<chest.getContainerSize();i++){var stack=chest.getItem(i);if(!stack.isEmpty())result.merge(new ItemKey(stack),(long)stack.getCount(),Long::sum);}return result;}
        public ItemStack extract(ItemKey key,int count){var result=ItemStack.EMPTY;for(int i=0;i<chest.getContainerSize()&&result.getCount()<count;i++)if(!chest.getItem(i).isEmpty()&&key.equals(new ItemKey(chest.getItem(i)))){var part=chest.removeItem(i,count-result.getCount());if(result.isEmpty())result=part;else result.grow(part.getCount());}return result;}
        public ItemStack insert(ItemStack stack){var rest=stack.copy();for(int i=0;i<chest.getContainerSize()&&!rest.isEmpty();i++){var current=chest.getItem(i);if(!current.isEmpty()&&!ItemStack.isSameItemSameComponents(current,rest))continue;int count=Math.min(rest.getCount(),rest.getMaxStackSize()-current.getCount());if(current.isEmpty())chest.setItem(i,rest.copyWithCount(count));else current.grow(count);rest.shrink(count);}chest.setChanged();return rest;}
        public List<GlobalPos> workstations(){return List.of(at(processor));}
        public List<ItemStack> exposedProducts(){return List.of(new ItemStack(target));}
        public GlobalPos origin(){return at(chestPos);}
        public void visual(GlobalPos from,GlobalPos to,ItemStack stack,int ticks){}
    }

    private static final class Jei {
        static mezz.jei.api.runtime.IJeiRuntime runtime() throws Exception {
            var field=com.cappleapple.astralrepository.compat.AstralJeiPlugin.class.getDeclaredField("runtime");field.setAccessible(true);
            return (mezz.jei.api.runtime.IJeiRuntime)field.get(null);
        }
        static void fill(NexusScreen screen) throws Exception {
            var runtime=runtime();
            var category=runtime.getRecipeManager().getRecipeCategory(mezz.jei.api.constants.RecipeTypes.CRAFTING);
            var handler=runtime.getRecipeTransferManager().getRecipeTransferHandler(screen.getMenu(),category).orElseThrow();
            check(handler instanceof com.cappleapple.astralrepository.compat.AstralJeiPlugin.RecipeHandler,"Wrong registered JEI handler");
            var recipe=runtime.getRecipeManager().createRecipeLookup(mezz.jei.api.constants.RecipeTypes.CRAFTING).get().filter(r -> r.id().identifier().equals(TRAPDOOR)).findFirst().orElseThrow();
            check(handler.transferRecipe(screen.getMenu(),recipe,null,Minecraft.getInstance().player,false,false)==null,"JEI disabled available stored ingredients");
            check(handler.transferRecipe(screen.getMenu(),recipe,null,Minecraft.getInstance().player,false,true)==null,"JEI actual transfer failed");
        }
    }
    private static Identifier id(String path){return Identifier.fromNamespaceAndPath("astral_repository_recipe_test",path);}
    private static void next(int value){phase=value;ticks=0;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void server(Consumer<ServerPlayer> action){var mc=Minecraft.getInstance();var id=mc.player.getUUID();pending=new CompletableFuture<>();var job=pending;mc.getSingleplayerServer().execute(() -> {try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));job.complete(null);}catch(Throwable error){job.completeExceptionally(error);}});}
    private static void capture(String name){try{Files.createDirectories(OUT);}catch(Exception error){throw new IllegalStateException(error);}net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(),image -> {try(image){image.writeToFile(OUT.resolve(name));}catch(Exception error){throw new IllegalStateException(error);}});}
}
