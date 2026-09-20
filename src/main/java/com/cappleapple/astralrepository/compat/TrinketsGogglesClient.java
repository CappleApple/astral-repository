package com.cappleapple.astralrepository.compat;
import com.cappleapple.astralrepository.content.AstralContent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
/** Optional Trinkets renderer shares the goggles' normal head-slot model. */
public final class TrinketsGogglesClient {
 public static void register(){
  if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("trinkets"))return;
  var contract=OptionalApi.type("dev.emi.trinkets.api.client.TrinketRenderer");
  var renderer=java.lang.reflect.Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{
   if(method.getDeclaringClass()==Object.class)return switch(method.getName()){case "toString"->"Astral goggles renderer";case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->null;};
   var wearer=(LivingEntity)args[6];
   if(wearer.getItemBySlot(EquipmentSlot.HEAD).is(AstralContent.RESONANCE_GOGGLES.get())||!(args[2] instanceof HeadedModel model))return null;
   var pose=(PoseStack)args[3];pose.pushPose();
   try{
    if(wearer.isBaby()&&!(wearer instanceof Villager)){pose.translate(0,.03125F,0);pose.scale(.7F,.7F,.7F);pose.translate(0,1,0);}
    model.getHead().translateAndRotate(pose);CustomHeadLayer.translateToHead(pose,wearer instanceof Villager||wearer instanceof ZombieVillager);
    if(wearer.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof ArmorItem)pose.scale(1.16F,1.16F,1.16F);
    Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(wearer,(ItemStack)args[0],ItemDisplayContext.HEAD,false,pose,(MultiBufferSource)args[4],(int)args[5]);
   }finally{pose.popPose();}return null;
  });
  OptionalApi.call(null,"dev.emi.trinkets.api.client.TrinketRendererRegistry","registerRenderer",new Class<?>[]{Item.class,contract},AstralContent.RESONANCE_GOGGLES.get(),renderer);
 }
 private TrinketsGogglesClient(){}
}
