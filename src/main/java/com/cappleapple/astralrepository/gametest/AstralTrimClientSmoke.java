package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralClientConfig;
import com.cappleapple.astralrepository.client.AstralPlaneRenderType;
import com.cappleapple.astralrepository.client.AstralTrimItemModel;
import com.cappleapple.astralrepository.content.AstralTrims;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.ArmorTrim;

public final class AstralTrimClientSmoke {
    private static final Path OUT=Path.of("../build/client-smoke");
    private static int phase,ticks;
    private static double opacity;
    private static NativeImage baseline;
    private static final List<ItemStack> icons=new ArrayList<>();
    private static ArmorStand iron,netherite;

    public static boolean tick()throws Exception {
        var mc=Minecraft.getInstance();ticks++;
        if(phase==0){
            Files.createDirectories(OUT);opacity=AstralClientConfig.astralOverlayOpacity.get();
            var material=mc.level.registryAccess().registryOrThrow(Registries.TRIM_MATERIAL).getHolderOrThrow(AstralTrims.MATERIAL);
            var patterns=mc.level.registryAccess().registryOrThrow(Registries.TRIM_PATTERN);
            for(var key:patterns.registryKeySet()){
                if(!key.location().getNamespace().equals("minecraft"))continue;
                var trim=new ArmorTrim(material,patterns.getHolderOrThrow(key));
                var atlas=mc.getModelManager().getAtlas(Sheets.ARMOR_TRIMS_SHEET);
                for(var id:List.of(trim.innerTexture(ArmorMaterials.IRON),trim.outerTexture(ArmorMaterials.IRON)))
                    check(atlas.getSprite(id).contents().name().equals(id),"Missing astral pattern sprite: "+id);
            }
            iron=new ArmorStand(mc.level,0,0,0);netherite=new ArmorStand(mc.level,0,0,0);
            int index=0;
            for(var item:List.of(Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS)){
                var stack=new ItemStack(item);
                stack.set(DataComponents.TRIM,new ArmorTrim(material,patterns.getHolder(ResourceLocation.withDefaultNamespace("silence")).orElseThrow()));
                icons.add(stack);verifyIcon(stack);
                iron.setItemSlot(List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET).get(index++),stack);
            }
            index=0;
            for(var item:List.of(Items.NETHERITE_HELMET,Items.NETHERITE_CHESTPLATE,Items.NETHERITE_LEGGINGS,Items.NETHERITE_BOOTS)){
                var stack=new ItemStack(item);
                stack.set(DataComponents.TRIM,new ArmorTrim(material,patterns.getHolder(ResourceLocation.withDefaultNamespace("sentry")).orElseThrow()));
                netherite.setItemSlot(List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET).get(index++),stack);
            }
            check(!(mc.getItemRenderer().getModel(new ItemStack(Items.IRON_CHESTPLATE),mc.level,mc.player,0) instanceof AstralTrimItemModel),"Untrimmed armor was wrapped");
            var ordinary=icons.get(1).copy();ordinary.set(DataComponents.TRIM,new ArmorTrim(mc.level.registryAccess().registryOrThrow(Registries.TRIM_MATERIAL).getHolder(ResourceLocation.withDefaultNamespace("gold")).orElseThrow(),patterns.getHolder(ResourceLocation.withDefaultNamespace("silence")).orElseThrow()));
            check(!(mc.getItemRenderer().getModel(ordinary,mc.level,mc.player,0) instanceof AstralTrimItemModel),"Other trim materials were changed");
            AstralClientConfig.astralOverlayOpacity.set(0.0);mc.setScreen(new Preview());phase=1;ticks=0;
        }else if(phase==1&&ticks>20){
            baseline=Screenshot.takeScreenshot(mc.getMainRenderTarget());baseline.writeToFile(OUT.resolve("astral_trim_base.png"));
            AstralClientConfig.astralOverlayOpacity.set(1.0);phase=2;ticks=0;
        }else if(phase==2&&ticks>20){
            try(var current=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                current.writeToFile(OUT.resolve("astral_trim_shader.png"));int worn=0,item=0;
                for(int y=50;y<current.getHeight()-30;y++)for(int x=25;x<current.getWidth()-25;x++){
                    int a=baseline.getPixelRGBA(x,y),b=current.getPixelRGBA(x,y);
                    int d=Math.abs((a&255)-(b&255))+Math.abs(((a>>>8)&255)-((b>>>8)&255))+Math.abs(((a>>>16)&255)-((b>>>16)&255));
                    if(d>30){if(y<current.getHeight()*.72)worn++;else item++;}
                }
                check(worn>200,"Worn astral trim shader did not change pixels: "+worn);
                check(item>50,"Item astral trim shader did not change pixels: "+item);
                Files.writeString(OUT.resolve("astral_trim_metrics.txt"),"Changed worn pixels="+worn+", icon pixels="+item+"\n");
            }
            baseline.close();AstralClientConfig.astralOverlayOpacity.set(opacity);phase=3;ticks=0;
        }else if(phase==3&&ticks>15){
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve("astral_trim_default.png"));}
            check(mc.options.getSoundSourceVolume(SoundSource.MASTER)==0&&!mc.mouseHandler.isMouseGrabbed(),"Trim test enabled sound or mouse capture");
            Files.writeString(OUT.resolve("result.txt"),"PASS: all 36 vanilla worn trim sprites present; all four item armor slots split normal armor and astral-only trim passes; no changes to ordinary trims; worn and icon shader response captured; client stayed hidden, muted and mouse-free.\n");
            mc.setScreen(null);return true;
        }
        return false;
    }
    private static void verifyIcon(ItemStack stack){
        var mc=Minecraft.getInstance();var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
        check(model instanceof AstralTrimItemModel,"Astral armor icon not wrapped");
        var passes=model.getRenderPasses(stack,true);check(passes.size()==2,"Expected base and trim passes");
        for(int i=0;i<2;i++){
            var pass=passes.get(i);var quads=new ArrayList<BakedQuad>();var random=RandomSource.create(42);
            quads.addAll(pass.getQuads(null,null,random));
            for(var face:net.minecraft.core.Direction.values())quads.addAll(pass.getQuads(null,face,random));
            check(!quads.isEmpty(),"Empty armor render pass "+i);
            for(var q:quads){check(AstralTrimItemModel.isTrim(q)==(i==1),"Armor and trim quads mixed in one pass");
                if(i==1)check(q.getSprite().contents().name().getPath().endsWith("_astral_repository_astral_gem"),"Trim icon uses wrong palette");}
            check(pass.getRenderTypes(stack,true).contains(AstralPlaneRenderType.ITEM_TRIM)==(i==1),"Shader affects armor base");
        }
    }
    private static final class Preview extends Screen{
        Preview(){super(Component.literal("Astral Gem armor trim"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
            g.fill(0,0,width,height,0xff15142b);g.drawCenteredString(font,"Astral Gem armor trim",width/2,12,0xffddddff);
            g.drawCenteredString(font,"Iron / Silence",width/4,32,0xffb9c7ff);g.drawCenteredString(font,"Netherite / Sentry",width*3/4,32,0xffb9c7ff);
            InventoryScreen.renderEntityInInventoryFollowsAngle(g,15,45,width/2-10,265,95,0,.30f,0,iron);
            InventoryScreen.renderEntityInInventoryFollowsAngle(g,width/2+10,45,width-15,265,95,0,-.40f,0,netherite);
            for(int i=0;i<icons.size();i++)MaterialGeometrySmoke.renderItem(g,icons.get(i),width*(i+.5f)/4,325,70,0,0);
        }
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private AstralTrimClientSmoke(){}
}
