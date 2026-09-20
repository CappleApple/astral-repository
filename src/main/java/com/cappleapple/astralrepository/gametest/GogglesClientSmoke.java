package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralClientConfig;
import com.cappleapple.astralrepository.client.AstralPlaneRenderType;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.GogglesEquipment;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Actual player head layers and optional Curios packets, with a hidden, muted client. */
public final class GogglesClientSmoke {
    private static final Path OUT=Path.of("../build/client-smoke");
    private static int phase,ticks,changedHeadPixels;
    private static boolean pending;
    private static volatile String failure;
    private static double opacity;
    private static NativeImage baseline;

    public static boolean tick() throws Exception {
        var mc=Minecraft.getInstance();
        if(failure!=null)throw new AssertionError(failure);
        if(++ticks>600)throw new AssertionError("Goggles client timeout in phase "+phase);
        if(phase==0){
            Files.createDirectories(OUT);
            check(!Boolean.getBoolean("astral_repository.curiosTest")||curios(),"Curios test requested but the mod is absent");
            var stack=goggles();
            check(stack.getHoverName().getString().equals("Astral Goggles"),"Goggles have the wrong localized name");
            var lines=new ArrayList<Component>();
            stack.getItem().appendHoverText(stack,Item.TooltipContext.of(mc.level),lines,TooltipFlag.NORMAL);
            check(lines.isEmpty(),"Goggles still append an instructional tooltip: "+lines);
            check(mc.getItemRenderer().getModel(stack,mc.level,mc.player,0).isCustomRenderer(),"Goggles do not dispatch the custom model renderer");
            check(AstralPlaneRenderType.ready(),"Astral lens shader failed to load");
            checkGeometry();
            opacity=AstralClientConfig.astralOverlayOpacity.get();
            server(1,p->{
                p.closeContainer();p.getInventory().clearContent();
                p.setItemSlot(EquipmentSlot.HEAD,goggles());
                p.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
                p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
                p.inventoryMenu.broadcastChanges();
                check(GogglesEquipment.isWearing(p),"Helmet-slot goggles do not enable inspection on the server");
            });
        }else if(phase==1&&!pending&&ticks>30){
            check(GogglesEquipment.isWearing(mc.player),"Helmet-slot goggles did not synchronize to the client");
            mc.setScreen(new Preview("Head armor slot"));
            AstralClientConfig.astralOverlayOpacity.set(0.0);next(2);
        }else if(phase==2&&ticks>10){
            shot("goggles_helmet_base.png");
            baseline=Screenshot.takeScreenshot(mc.getMainRenderTarget());
            AstralClientConfig.astralOverlayOpacity.set(1.0);next(3);
        }else if(phase==3&&ticks>10){
            shot("goggles_helmet_full_overlay.png");
            try(var current=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                changedHeadPixels=changedPixels(baseline,current,mc.screen);
                check(changedHeadPixels>=80,"Changing shader opacity did not affect worn goggles: "+changedHeadPixels);
            }finally{baseline.close();baseline=null;}
            AstralClientConfig.astralOverlayOpacity.set(opacity);next(4);
        }else if(phase==4&&ticks>10){
            shot("goggles_helmet_front_oblique.png");
            if(!curios())return finish();
            server(5,p->{
                p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
                CuriosFixture.equip(p,false,goggles());
                check(GogglesEquipment.isWearing(p),"Curios head goggles do not enable server inspection");
                p.inventoryMenu.broadcastChanges();
            });
        }else if(phase==5&&!pending&&ticks>30){
            check(mc.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),"Helmet was not cleared for Curios-only verification");
            check(GogglesEquipment.isWearing(mc.player),"Curios head goggles did not enable client inspection");
            check(CuriosFixture.equipped(mc.player,false),"Curios head stack did not synchronize");
            mc.setScreen(new Preview("Curios head slot"));next(6);
        }else if(phase==6&&!pending&&ticks>10){
            shot("goggles_curios_front_oblique.png");
            server(9,p->{
                p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
                p.inventoryMenu.broadcastChanges();
                check(CuriosFixture.equipped(p,false)&&GogglesEquipment.isWearing(p),"Adding armor disabled functional Curios goggles on the server");
            });
        }else if(phase==9&&!pending&&ticks>30){
            check(mc.player.getItemBySlot(EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.DIAMOND_HELMET),"Diamond helmet did not synchronize");
            check(CuriosFixture.equipped(mc.player,false)&&GogglesEquipment.isWearing(mc.player),"Curios goggles stopped functioning alongside head armor");
            mc.setScreen(new Preview("Curios with diamond helmet"));next(10);
        }else if(phase==10&&!pending&&ticks>10){
            shot("goggles_curios_with_helmet.png");
            server(7,p->{
                p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
                p.inventoryMenu.broadcastChanges();
                CuriosFixture.equip(p,false,ItemStack.EMPTY);
                CuriosFixture.equip(p,true,goggles());
                check(!GogglesEquipment.isWearing(p),"Cosmetic Curios goggles incorrectly enable server inspection");
            });
        }else if(phase==7&&!pending&&ticks>30){
            check(CuriosFixture.equipped(mc.player,true),"Cosmetic Curios stack did not synchronize");
            check(!GogglesEquipment.isWearing(mc.player),"Cosmetic Curios goggles incorrectly enable client inspection");
            mc.setScreen(new Preview("Curios cosmetic slot"));next(8);
        }else if(phase==8&&ticks>10){
            shot("goggles_curios_cosmetic.png");return finish();
        }
        return false;
    }

    private static void checkGeometry(){
        var model=Minecraft.getInstance().getModelManager().getModel(com.cappleapple.astralrepository.client.AstralMineralClient.GOGGLES_MODEL);
        var quads=new ArrayList<net.minecraft.client.renderer.block.model.BakedQuad>();
        var random=net.minecraft.util.RandomSource.create(42);
        quads.addAll(model.getQuads(null,null,random));
        for(var face:net.minecraft.core.Direction.values())quads.addAll(model.getQuads(null,face,random));
        check(!model.isCustomRenderer()&&quads.size()==126,"Low-poly goggles geometry is missing or changed: "+quads.size());
        check(quads.stream().filter(q->q.getTintIndex()==0).count()==36,"Only the six lens cuboids should use the astral overlay");
        check(quads.stream().filter(q->!q.isTinted()).count()==90,"Frame and strap must retain their own unshaded material");
        var texture=net.minecraft.resources.Identifier.fromNamespaceAndPath("astral_repository","item/astral_goggles");
        check(quads.stream().allMatch(q->q.getSprite().contents().name().equals(texture)),"Goggles geometry lost its custom texture");
        for (var context : net.minecraft.world.item.ItemDisplayContext.values()) {
            var selected = com.cappleapple.astralrepository.client.AstralMineralClient.gogglesModel(context);
            check(selected.equals(context == net.minecraft.world.item.ItemDisplayContext.HEAD
                    ? com.cappleapple.astralrepository.client.AstralMineralClient.GOGGLES_MODEL
                    : com.cappleapple.astralrepository.client.AstralMineralClient.GOGGLES_ICON_MODEL),
                    "Goggles selected the wrong model for " + context);
        }
        var icon = Minecraft.getInstance().getModelManager().getModel(com.cappleapple.astralrepository.client.AstralMineralClient.GOGGLES_ICON_MODEL);
        var iconQuads = new ArrayList<net.minecraft.client.renderer.block.model.BakedQuad>(icon.getQuads(null,null,random));
        for(var face:net.minecraft.core.Direction.values())iconQuads.addAll(icon.getQuads(null,face,random));
        check(!iconQuads.isEmpty(), "Goggles icon geometry is missing");
        float near=Float.POSITIVE_INFINITY, far=Float.NEGATIVE_INFINITY;
        boolean sides=false, lens=false, frame=false;
        for(var quad:iconQuads){
            check(quad.getSprite().contents().name().getPath().equals("item/astral_goggles_icon"), "Goggles icon sampled the armor atlas");
            lens |= quad.isTinted(); frame |= !quad.isTinted();
            sides |= quad.getDirection().getAxis()!=net.minecraft.core.Direction.Axis.Z;
            int[] vertices=quad.getVertices();int stride=vertices.length/4;
            for(int i=0;i<4;i++){
                float z=Float.intBitsToFloat(vertices[i*stride+2]);near=Math.min(near,z);far=Math.max(far,z);
            }
        }
        check(sides&&lens&&frame, "Goggles icon lacks filled sides or separate lens/frame materials");
        check(Math.abs(near-7.5f/16)<1e-6&&Math.abs(far-8.5f/16)<1e-6, "Goggles item is not one pixel thick");
    }

    private static int changedPixels(NativeImage first,NativeImage second,Screen screen){
        check(first.getWidth()==second.getWidth()&&first.getHeight()==second.getHeight(),"Goggles preview changed framebuffer size");
        double scaleX=first.getWidth()/(double)screen.width,scaleY=first.getHeight()/(double)screen.height;
        int count=0;
        // Only the two rendered heads; the item thumbnail and labels are excluded.
        for(int y=(int)(56*scaleY);y<(int)(240*scaleY);y++)
            for(int x=(int)(18*scaleX);x<(int)((screen.width-18)*scaleX);x++){
                int a=first.getPixelRGBA(x,y),b=second.getPixelRGBA(x,y);
                int difference=Math.abs((a&255)-(b&255))+Math.abs(((a>>>8)&255)-((b>>>8)&255))+Math.abs(((a>>>16)&255)-((b>>>16)&255));
                if(difference>=18)count++;
            }
        return count;
    }

    private static boolean finish()throws Exception{
        var mc=Minecraft.getInstance();
        AstralClientConfig.astralOverlayOpacity.set(opacity);
        check(!mc.mouseHandler.isMouseGrabbed(),"Goggles test captured the mouse");
        check(mc.options.getSoundSourceVolume(SoundSource.MASTER)==0,"Goggles test enabled audio");
        Files.writeString(OUT.resolve("result.txt"),"PASS: Astral Goggles localized name, absent instructional tooltip, helmet-slot inspection, front/oblique actual player head layers; inventory/held/dropped icons and one-pixel geometry, "+changedHeadPixels+" worn-head pixels changed with lens shader opacity; "+(curios()?"Curios head and cosmetic equip packets synchronized, functional head slot enabled inspection with and without a diamond helmet, cosmetic slot did not.":"Curios absent; base client loaded and goggles remained functional.")+"\n");
        mc.setScreen(null);return true;
    }

    private static final class Preview extends Screen {
        private final String slot;
        Preview(String slot){super(Component.literal("Astral Goggles model inspection"));this.slot=slot;}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
            g.fill(0,0,width,height,0xff121125);
            g.drawCenteredString(font,"Astral Goggles / "+slot,width/2,14,0xe3ddff);
            int gap=18,mid=width/2;
            g.fill(gap,40,mid-8,270,0xff202039);
            g.fill(mid+8,40,width-gap,270,0xff202039);
            g.drawCenteredString(font,"Front",mid/2,45,0xaeb7e8);
            g.drawCenteredString(font,"Oblique",mid+mid/2,45,0xaeb7e8);
            g.flush();
            var player=Minecraft.getInstance().player;
            InventoryScreen.renderEntityInInventoryFollowsAngle(g,gap,58,mid-8,267,240,.72f,0,0,player);
            InventoryScreen.renderEntityInInventoryFollowsAngle(g,mid+8,58,width-gap,267,240,.72f,.9f,.12f,player);
            MaterialGeometrySmoke.renderItem(g,goggles(),width/6f,320,100,0,0);
            MaterialGeometrySmoke.renderItem(g,goggles(),width/2f,320,150,45,0,net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
            MaterialGeometrySmoke.renderItem(g,goggles(),width*5/6f,320,170,35,0,net.minecraft.world.item.ItemDisplayContext.GROUND);
            g.drawCenteredString(font,"Inventory",width/6,360,0xaeb7e8);
            g.drawCenteredString(font,"Held",width/2,360,0xaeb7e8);
            g.drawCenteredString(font,"Dropped",width*5/6,360,0xaeb7e8);
        }
    }

    /** Separate class keeps optional API linkage out of the no-Curios execution path. */
    private static final class CuriosFixture {
        static void equip(net.minecraft.world.entity.LivingEntity player,boolean cosmetic,ItemStack stack){
            var head=top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler("head").orElseThrow();
            (cosmetic?head.getCosmeticStacks():head.getStacks()).setStackInSlot(0,stack);
        }
        static boolean equipped(net.minecraft.world.entity.LivingEntity player,boolean cosmetic){
            var head=top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler("head").orElseThrow();
            return (cosmetic?head.getCosmeticStacks():head.getStacks()).getStackInSlot(0).is(AstralContent.RESONANCE_GOGGLES.get());
        }
    }
    private static ItemStack goggles(){return new ItemStack(AstralContent.RESONANCE_GOGGLES.get());}
    private static boolean curios(){return ModList.get().isLoaded("curios");}
    private static void server(int after,Consumer<ServerPlayer> work){
        pending=true;var mc=Minecraft.getInstance();var id=mc.player.getUUID();
        mc.getSingleplayerServer().execute(()->{
            try{work.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));mc.execute(()->{pending=false;next(after);});}
            catch(Throwable error){failure=error.toString();}
        });
    }
    private static void shot(String name)throws Exception{try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(OUT.resolve(name));}}
    private static void next(int value){phase=value;ticks=0;}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private GogglesClientSmoke(){}
}
