package com.cappleapple.astralrepository.smoke;

import com.cappleapple.astralrepository.client.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Development-only background client gate. Never included in release artifacts. */
@EventBusSubscriber(modid="astral_repository",value=Dist.CLIENT)
public final class ModernClientSmoke {
    private static final Path OUT=Path.of("captures");
    private static final BlockPos NEXUS=new BlockPos(0,-60,0), STORAGE=new BlockPos(2,-60,0), CHEST=new BlockPos(4,-60,0);
    private static final BlockPos RUNE_TARGET=new BlockPos(4,-60,4), TABLE=new BlockPos(-2,-60,0), SHELF=new BlockPos(0,-60,-2);
    private static RuneLayer transferRune;
    private static int phase,ticks;
    private static CompletableFuture<Void> pending;
    private static boolean done;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("astral_repository.modernClientSmoke")||done)return;
        var mc=Minecraft.getInstance();
        mc.options.pauseOnLostFocus=false;
        mc.mouseHandler.releaseMouse();mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0D);
        try {
            if(ticks%200==0)LogUtils.getLogger().info("Modern smoke phase {} tick {} screen {} overlay {}",phase,ticks,mc.gui.screen(),mc.gui.overlay());
            if(++ticks>3600)throw new AssertionError("Client gate timeout in phase "+phase);
            if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
            if(phase==0&&mc.gui.screen() instanceof net.neoforged.neoforge.client.gui.LoadingErrorScreen warning&&mc.gui.overlay()==null){
                String proceed=net.neoforged.fml.i18n.FMLTranslations.parseMessage("fml.button.continue.launch");
                var button=warning.children().stream().filter(child->child instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(proceed)).map(child->(net.minecraft.client.gui.components.Button)child).findFirst();
                check(button.isPresent(),"NeoForge reported a fatal loading error");button.get().onPress(null);
            }
            if(phase==0&&mc.gui.screen() instanceof AccessibilityOnboardingScreen onboarding&&mc.gui.overlay()==null)onboarding.onClose();
            if(phase==0&&mc.gui.screen() instanceof TitleScreen&&mc.gui.overlay()==null){
                Files.createDirectories(OUT);Files.deleteIfExists(OUT.resolve("result.txt"));
                if(Boolean.getBoolean("astral_repository.packagedAudit")){
                    var location=AstralContent.class.getProtectionDomain().getCodeSource().getLocation().toString();
                    Files.writeString(OUT.resolve("packaged-source.txt"),location+"\n");
                    check(!location.contains("classes/java/main")&&!location.contains("classes\\java\\main"),"Packaged audit loaded development class output: "+location);
                }
                verifyModels();
                mc.options.guiScale().set(1);mc.options.renderDistance().set(3);mc.resizeGui();
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                next(1);
                mc.createWorldOpenFlows().createFreshLevel("astral-port-smoke-"+System.currentTimeMillis(),
                        new LevelSettings("Astral port smoke",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42,false,false),r->r.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.gui.screen());
            } else if(phase==1&&mc.player!=null&&mc.level!=null&&mc.getSingleplayerServer()!=null&&mc.gui.overlay()==null){
                mc.gui.setScreen(null);server(ModernClientSmoke::workshop);next(2);
            } else if(phase==2&&ticks>120){
                verifyNames();
                verifyPlacedBlocks();
                check(AstralPlaneRenderType.ready(),"Astral render pipelines did not register");
                check(RuneRenderer.visibleFaces()>0,"Rune packets did not reach the client");
                check(RuneRenderer.renderedGlyphs()>0,"Rune world renderer produced no glyphs");
                capture("world.png");next(3);
            } else if(phase==3){
                server(p->{p.teleportTo(p.level(),.5,-58,5.5,Set.of(),180,0,false);NetworkManager.open(p,GlobalPos.of(p.level().dimension(),NEXUS));});next(4);
            } else if(phase==4&&mc.gui.screen() instanceof NexusScreen screen&&ticks>30){
                check(!screen.getMenu().entries.isEmpty(),"Nexus did not receive real linked inventory contents");
                capture("nexus.png");next(20);
            } else if(phase==20){
                sendAction(NetworkPackets.PICKUP,new ItemStack(Items.IRON_INGOT),1);next(21);
            } else if(phase==21&&ticks>30){
                server(p->{var menu=menu(p);check(menu.getCarried().is(Items.IRON_INGOT)&&menu.getCarried().getCount()==32,"Nexus half pickup packet");check(stored(menu,Items.IRON_INGOT)==32,"Nexus pickup conservation");});
                sendAction(NetworkPackets.DEPOSIT,ItemStack.EMPTY,1);next(22);
            } else if(phase==22&&ticks>30){
                server(p->{var menu=menu(p);check(menu.getCarried().getCount()==31&&stored(menu,Items.IRON_INGOT)==33,"Nexus one-item deposit packet");});
                sendAction(NetworkPackets.DEPOSIT,ItemStack.EMPTY,0);next(23);
            } else if(phase==23&&ticks>30){
                server(p->{check(menu(p).getCarried().isEmpty()&&stored(menu(p),Items.IRON_INGOT)==64,"Nexus full deposit packet");manualCraft(p);verifyRunes(p);});next(24);
            } else if(phase==24&&ticks>30){
                sendAction(NetworkPackets.CRAFT,new ItemStack(Items.IRON_TRAPDOOR),1);next(25);
            } else if(phase==25&&ticks>200){
                server(p->{var menu=menu(p);check(stored(menu,Items.IRON_TRAPDOOR)==1&&stored(menu,Items.IRON_INGOT)==60,"Real Recipe Tome/table autocraft output and input conservation: "+menu.network().crafting().statuses());check(menu.network().crafting().activeJobs()==0,"Autocraft completed");check(!com.cappleapple.astralrepository.crafting.CraftingService.isProcessorReserved(GlobalPos.of(p.level().dimension(),TABLE)),"Table reservation released");LogUtils.getLogger().info("MODERN_PORT_GAMEPLAY_OK: all10 block placements, client-localized block/item names, actual Nexus packets, manual crafting/refill/clear, tome/table autocrafting, filtered rune transfer");});next(5);
            } else if(phase==5){mc.gui.setScreen(new ItemGallery());next(6);
            } else if(phase==6&&ticks>30){capture("items.png");next(7);
            } else if(phase==7){mc.gui.setScreen(null);mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                if(net.neoforged.fml.ModList.get().isLoaded("curios"))server(CuriosProbe::equip);next(8);
            } else if(phase==8&&ticks>30){
                if(net.neoforged.fml.ModList.get().isLoaded("curios"))check(com.cappleapple.astralrepository.compat.CuriosCompatibility.isWearingGoggles(mc.player),"Curios goggles synchronize to client");
                capture("armor.png");next(9);
            } else if(phase==9){
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                server(p->{
                    var base=RunePreset.initial(RuneLayer.Mode.PUSH);int n=base.design().size();
                    var art=new RuneDesign(n,base.design().argbPixels(),List.of(
                        new RuneDesign.Icon(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.STONE),n*.3F,n*.5F,n*.4F,30),
                        new RuneDesign.Icon(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(AstralContent.ASTRAL_GEM.get()),n*.7F,n*.5F,n*.3F,0)));
                    WandPackets.openPreset(p,new RunePreset(base.id(),"Port runtime check",base.mode(),art,base.filter(),base.priority(),base.enabled(),base.cadence()));
                });next(10);
            } else if(phase==10&&mc.gui.screen() instanceof WandScreen screen&&ticks>30){
                check(screen.editing()&&screen.snapshot().design().icons().size()==2,"Wand artwork did not synchronize");
                capture("wand.png");next(11);
            } else if(phase==11){
                check(!mc.mouseHandler.isMouseGrabbed(),"Client grabbed the mouse");
                Files.createDirectories(OUT);Files.writeString(OUT.resolve("result.txt"),"PASS: background client; registered models; all10 real block-item placements and localized block/item names; Nexus pickup/deposit packets; manual crafting and exact refill; Recipe Tome/table autocrafting; filtered rune transfer conservation; world crystals and minerals; live rune packets and glyph drawing; linked Nexus menu; item gallery; astral trim and glint; worn armor; wand editor with embedded 3D item artwork; captures saved.\n");
                LogUtils.getLogger().info("MODERN_PORT_CLIENT_SMOKE_OK");done=true;mc.stop();
            }
        }catch(Throwable failure){fail(failure);}
    }
    private static void verifyModels(){
        var models=new ArrayList<>(List.of(AstralMineralClient.GEM_MODEL,AstralMineralClient.WAND_MODEL,AstralMineralClient.REMOTE_MODEL,AstralMineralClient.GOGGLES_MODEL,AstralMineralClient.GOGGLES_ICON_MODEL));
        for(var node:AstralContent.NODES)models.add(CrystalModelRenderer.modelLocation(node.get()));
        for(var key:models){
            var model=AstralModels.get(key);int quads=model.getQuads(null,null,net.minecraft.util.RandomSource.create(42)).size();
            for(var side:Direction.values())quads+=model.getQuads(null,side,net.minecraft.util.RandomSource.create(42)).size();
            check(quads>0,"Model has no geometry: "+key.id());
            check(!model.getParticleIcon().contents().name().getPath().equals("missingno"),"Model has missing texture: "+key.id());
        }
    }
    private static void verifyNames() throws Exception {
        var language=net.minecraft.locale.Language.getInstance();
        var report=new StringBuilder();
        var failures=new ArrayList<String>();
        for(var entry:AstralContent.BLOCKS.getEntries()){
            var block=entry.get();var key="block.astral_repository."+entry.getId().getPath();
            var expected=language.getOrDefault(key);var blockName=block.getName().getString();
            var itemName=new ItemStack(block).getHoverName().getString();
            String line=entry.getId()+" | block="+blockName+" | item="+itemName;
            report.append(line).append('\n');
            if(expected.equals(key)||!expected.equals(blockName)||!expected.equals(itemName))failures.add(line);
        }
        Files.createDirectories(OUT);Files.writeString(OUT.resolve("localization.txt"),report);
        for(var entry:AstralContent.ITEMS.getEntries()){
            var stack=new ItemStack(entry.get());var key=entry.get().getDescriptionId();
            check(language.has(key)&&!stack.getHoverName().getString().equals(key),"Item has no client translation: "+entry.getId());
        }
        check(failures.isEmpty(),"Block localization: "+failures);
    }
    private static void workshop(ServerPlayer p){
        var level=p.level();
        for(int x=-4;x<15;x++)for(int z=-2;z<12;z++)level.setBlockAndUpdate(new BlockPos(x,-61,z),Blocks.SMOOTH_STONE.defaultBlockState());
        level.setBlockAndUpdate(NEXUS,AstralContent.STORAGE_NEXUS.get().defaultBlockState());
        level.setBlockAndUpdate(STORAGE,AstralContent.SEED_STORAGE_CRYSTAL.get().defaultBlockState());
        var storage=((CrystalNodeBlockEntity)level.getBlockEntity(STORAGE)).inventory();
        storage.insertItem(0,new ItemStack(Items.IRON_INGOT,64),false);
        storage.insertItem(1,namedLog().copyWithCount(24),false);
        level.setBlockAndUpdate(TABLE,Blocks.CRAFTING_TABLE.defaultBlockState());
        level.setBlockAndUpdate(SHELF,Blocks.CHISELED_BOOKSHELF.defaultBlockState());
        var tome=new ItemStack(AstralContent.RECIPE_TOME.get());var data=new net.minecraft.nbt.CompoundTag();
        data.put("Output",com.cappleapple.astralrepository.port.NbtCodecs.save(new ItemStack(Items.IRON_TRAPDOOR),level.registryAccess()));data.putString("OutputId","minecraft:iron_trapdoor");
        tome.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(data));
        ((net.minecraft.world.Container)level.getBlockEntity(SHELF)).setItem(0,tome);
        check(NetworkManager.get(level.getServer()).toggleLink(AnchorAddress.crystal(level,NEXUS),AnchorAddress.crystal(level,STORAGE)).success(),"Could not link test storage");
        level.setBlockAndUpdate(CHEST,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.SOUTH));
        level.setBlockAndUpdate(RUNE_TARGET,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.SOUTH));
        var source=(net.minecraft.world.Container)level.getBlockEntity(CHEST);source.setItem(0,new ItemStack(Items.GOLD_INGOT,9));source.setItem(1,new ItemStack(Items.DIAMOND,5));
        var runes=RuneSurfaces.getOrCreate(level,CHEST,Direction.SOUTH);transferRune=runes.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
        transferRune.filter().add(FilterRules.Kind.ITEM,"minecraft:gold_ingot",false,ItemStack.EMPTY);transferRune.setCadence(RuneCadence.DEFAULT.with(RuneCadence.Kind.ITEMS,new RuneCadence.Rate(4,1)));transferRune.changed();
        check(runes.toggleTarget(transferRune.id(),GlobalPos.of(level.dimension(),RUNE_TARGET),Direction.SOUTH).assigned(),"Rune target assigned");
        placeAllBlocks(p);
        int x=6;for(var block:List.of(AstralContent.ASTRAL_GEODE.get(),AstralContent.BUDDING_ASTRAL.get(),AstralContent.SMALL_ASTRAL_BUD.get(),AstralContent.MEDIUM_ASTRAL_BUD.get(),AstralContent.LARGE_ASTRAL_BUD.get(),AstralContent.ASTRAL_CLUSTER.get()))level.setBlockAndUpdate(new BlockPos(x++,-60,0),block.defaultBlockState());
        check(new ItemStack(AstralContent.ASTRAL_GEM.get()).get(net.minecraft.core.component.DataComponents.PROVIDES_TRIM_MATERIAL).is(AstralTrims.MATERIAL),"Gem does not provide its trim material");
        p.setItemSlot(EquipmentSlot.CHEST,trimmedChestplate(level.registryAccess()));
        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AstralContent.RESONANCE_GOGGLES.get()));
        p.getInventory().setItem(0,new ItemStack(AstralContent.ATTUNEMENT_WAND.get()));p.getInventory().setSelectedSlot(0);
        p.getAbilities().flying=true;p.onUpdateAbilities();
        Vec3 target=new Vec3(5.5,-59.3,.5),eye=new Vec3(5.5,-58,8.5),delta=target.subtract(eye);
        float yaw=(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90,pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        p.teleportTo(level,eye.x,eye.y-p.getEyeHeight(),eye.z,Set.of(),yaw,pitch,false);
    }
    private static ItemStack namedLog(){var stack=new ItemStack(Items.OAK_LOG);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal("Exact runtime crafting input"));return stack;}
    private static com.cappleapple.astralrepository.menu.NexusMenu menu(ServerPlayer p){check(p.containerMenu instanceof com.cappleapple.astralrepository.menu.NexusMenu,"Real Nexus is still open");return (com.cappleapple.astralrepository.menu.NexusMenu)p.containerMenu;}
    private static long stored(com.cappleapple.astralrepository.menu.NexusMenu menu,Item item){return menu.network().snapshot().getOrDefault(new com.cappleapple.astralrepository.api.ItemKey(new ItemStack(item)),0L);}
    private static void sendAction(int kind,ItemStack stack,int amount){
        var mc=Minecraft.getInstance();check(mc.player.containerMenu instanceof com.cappleapple.astralrepository.menu.NexusMenu,"Client Nexus menu present");
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new NetworkPackets.Action(mc.player.containerMenu.containerId,kind,stack,amount,0,""));
    }
    private static void placeAllBlocks(ServerPlayer p){
        var level=p.level();var previous=p.gameMode.getGameModeForPlayer();p.setGameMode(GameType.SURVIVAL);
        try{int index=0;for(var entry:AstralContent.BLOCKS.getEntries()){
            var pos=new BlockPos(-8+(index%5)*2,-60,6+(index/5)*2);index++;
            level.setBlockAndUpdate(pos.below(),Blocks.SMOOTH_STONE.defaultBlockState());level.removeBlock(pos,false);
            var stack=new ItemStack(entry.get());
            var hit=new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false);
            var context=new net.minecraft.world.item.context.UseOnContext(level,p,net.minecraft.world.InteractionHand.MAIN_HAND,stack,hit);
            check(stack.getItem().useOn(context).consumesAction(),"BlockItem placement failed: "+entry.getId());
            check(level.getBlockState(pos).is(entry.get())&&stack.isEmpty(),"Block placement/consumption mismatch: "+entry.getId());
            check(level.getBlockEntity(pos)!=null,"Placed block entity missing: "+entry.getId());
        }}finally{p.setGameMode(previous);}
    }
    private static void verifyPlacedBlocks(){
        int index=0;for(var entry:AstralContent.BLOCKS.getEntries()){
            var pos=new BlockPos(-8+(index%5)*2,-60,6+(index/5)*2);index++;
            check(Minecraft.getInstance().level.getBlockState(pos).is(entry.get()),"Placed block did not synchronize to client: "+entry.getId());
        }
    }
    private static void verifyRunes(ServerPlayer p){
        var source=(net.minecraft.world.Container)p.level().getBlockEntity(CHEST);var target=(net.minecraft.world.Container)p.level().getBlockEntity(RUNE_TARGET);
        check(count(source,Items.GOLD_INGOT)==0&&count(target,Items.GOLD_INGOT)==9,"Rune physically transferred nine gold ingots");
        check(count(source,Items.DIAMOND)==5&&count(target,Items.DIAMOND)==0,"Rune item filter left five diamonds untouched");
        check(transferRune.transferredItems()==9,"Rune successful-transfer counter");
    }
    private static long count(net.minecraft.world.Container container,Item item){long amount=0;for(int i=0;i<container.getContainerSize();i++)if(container.getItem(i).is(item))amount+=container.getItem(i).getCount();return amount;}
    private static void manualCraft(ServerPlayer p){
        var menu=menu(p);var key=new com.cappleapple.astralrepository.api.ItemKey(namedLog());
        var first=menu.network().extractAt(key,1,menu.origin);check(first.getCount()==1,"Exact named ingredient withdrawn");menu.grid.setItem(0,first);
        check(menu.slots.getFirst().getItem().is(Items.OAK_PLANKS),"Real vanilla recipe matches");
        menu.clicked(0,0,net.minecraft.world.inventory.ContainerInput.PICKUP,p);
        check(menu.getCarried().is(Items.OAK_PLANKS)&&menu.getCarried().getCount()==4,"Manual craft produces four planks");
        check(ItemStack.isSameItemSameComponents(menu.grid.getItem(0),namedLog())&&menu.network().snapshot().getOrDefault(key,0L)==22,"Craft refills exact components");
        menu.clicked(10,0,net.minecraft.world.inventory.ContainerInput.PICKUP,p);
        menu.clicked(0,0,net.minecraft.world.inventory.ContainerInput.QUICK_MOVE,p);
        check(count(p.getInventory(),Items.OAK_PLANKS)==68,"Shift craft limits output to one additional stack");
        check(menu.network().snapshot().getOrDefault(key,0L)==6&&menu.grid.getItem(0).getCount()==1,"Shift craft conservation");
        menu.action(new NetworkPackets.Action(menu.containerId,NetworkPackets.CLEAR_GRID,ItemStack.EMPTY,0,0,""));
        check(menu.grid.isEmpty()&&menu.network().snapshot().getOrDefault(key,0L)==7,"Clear grid returns remaining ingredient");
    }
    private static ItemStack trimmedChestplate(net.minecraft.core.RegistryAccess registries){
        var stack=new ItemStack(Items.DIAMOND_CHESTPLATE);
        stack.set(net.minecraft.core.component.DataComponents.TRIM,new net.minecraft.world.item.equipment.trim.ArmorTrim(
            registries.lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(AstralTrims.MATERIAL),
            registries.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(net.minecraft.world.item.equipment.trim.TrimPatterns.SENTRY)));
        return stack;
    }
    private static void server(Consumer<ServerPlayer> action){
        var mc=Minecraft.getInstance();var id=mc.player.getUUID();pending=new CompletableFuture<>();var job=pending;
        mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));job.complete(null);}catch(Throwable failure){job.completeExceptionally(failure);}});
    }
    private static void capture(String filename)throws Exception{
        Files.createDirectories(OUT);pending=new CompletableFuture<>();var job=pending;
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(),image->{try(image){image.writeToFile(OUT.resolve(filename));job.complete(null);}catch(Throwable failure){job.completeExceptionally(failure);}});
    }
    private static void next(int value){phase=value;ticks=0;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void fail(Throwable failure){done=true;LogUtils.getLogger().error("Modern client smoke failed",failure);try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("result.txt"),"FAIL: "+failure+"\n");}catch(Exception ignored){}Minecraft.getInstance().stop();}
    private static final class CuriosProbe {
        static void equip(ServerPlayer player){
            var inventory=top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).orElseThrow();
            check(inventory.getStacksHandler("head").isPresent(),"Astral Curios head slot datapack loaded");
            inventory.setEquippedCurio("head",0,new ItemStack(AstralContent.RESONANCE_GOGGLES.get()));
            player.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
            check(com.cappleapple.astralrepository.compat.CuriosCompatibility.isWearingGoggles(player),"Curios equipped goggles recognized");
            LogUtils.getLogger().info("MODERN_PORT_CURIOS_OK: actual head slot goggles equipped and detected");
        }
    }
    private static final class ItemGallery extends Screen {
        ItemGallery(){super(Component.literal("Astral Repository port item check"));}
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick){
            graphics.fill(0,0,width,height,0xff182132);graphics.centeredText(font,title,width/2,20,0xffffffff);
            var items=List.of(AstralContent.ASTRAL_GEM.get(),AstralContent.ATTUNEMENT_WAND.get(),AstralContent.ASTRAL_NEXUS.get(),AstralContent.RESONANCE_GOGGLES.get(),AstralContent.RECIPE_TOME.get(),AstralContent.STORAGE_NEXUS.get().asItem(),AstralContent.SEED_STORAGE_CRYSTAL.get().asItem(),AstralContent.RELAY_CRYSTAL.get().asItem());
            var stacks=new ArrayList<ItemStack>();for(var item:items)stacks.add(new ItemStack(item));
            var foil=new ItemStack(AstralContent.ATTUNEMENT_WAND.get());foil.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);stacks.add(foil);
            stacks.add(trimmedChestplate(Minecraft.getInstance().level.registryAccess()));
            int x=width/2-stacks.size()*12;for(var stack:stacks){graphics.item(stack,x,height/2);x+=24;}
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
