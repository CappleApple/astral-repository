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
            if(phase==0&&mc.gui.screen() instanceof AccessibilityOnboardingScreen onboarding&&mc.gui.overlay()==null)onboarding.onClose();
            if(phase==0&&mc.gui.screen() instanceof TitleScreen&&mc.gui.overlay()==null){
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
                check(AstralPlaneRenderType.ready(),"Astral render pipelines did not register");
                check(RuneRenderer.visibleFaces()>0,"Rune packets did not reach the client");
                check(RuneRenderer.renderedGlyphs()>0,"Rune world renderer produced no glyphs");
                capture("world.png");next(3);
            } else if(phase==3){
                server(p->{p.teleportTo(p.level(),.5,-58,5.5,Set.of(),180,0,false);NetworkManager.open(p,GlobalPos.of(p.level().dimension(),NEXUS));});next(4);
            } else if(phase==4&&mc.gui.screen() instanceof NexusScreen screen&&ticks>30){
                check(!screen.getMenu().entries.isEmpty(),"Nexus did not receive real linked inventory contents");
                capture("nexus.png");next(5);
            } else if(phase==5){mc.gui.setScreen(new ItemGallery());next(6);
            } else if(phase==6&&ticks>30){capture("items.png");next(7);
            } else if(phase==7){mc.gui.setScreen(null);mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);next(8);
            } else if(phase==8&&ticks>30){capture("armor.png");next(9);
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
                Files.createDirectories(OUT);Files.writeString(OUT.resolve("result.txt"),"PASS: background client; registered models; world crystals and minerals; live rune packets and glyph drawing; linked Nexus menu; item gallery; astral trim and glint; worn armor; wand editor with embedded 3D item artwork; captures saved.\n");
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
    private static void workshop(ServerPlayer p){
        var level=p.level();
        for(int x=-4;x<15;x++)for(int z=-2;z<12;z++)level.setBlockAndUpdate(new BlockPos(x,-61,z),Blocks.SMOOTH_STONE.defaultBlockState());
        level.setBlockAndUpdate(NEXUS,AstralContent.STORAGE_NEXUS.get().defaultBlockState());
        level.setBlockAndUpdate(STORAGE,AstralContent.SEED_STORAGE_CRYSTAL.get().defaultBlockState());
        ((CrystalNodeBlockEntity)level.getBlockEntity(STORAGE)).inventory().insertItem(0,new ItemStack(Items.IRON_INGOT,64),false);
        check(NetworkManager.get(level.getServer()).toggleLink(AnchorAddress.crystal(level,NEXUS),AnchorAddress.crystal(level,STORAGE)).success(),"Could not link test storage");
        level.setBlockAndUpdate(CHEST,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.SOUTH));
        var runes=RuneSurfaces.getOrCreate(level,CHEST,Direction.SOUTH);runes.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
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
