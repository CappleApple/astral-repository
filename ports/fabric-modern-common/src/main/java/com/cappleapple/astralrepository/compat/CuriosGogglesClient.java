package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.content.AstralContent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid=AstralRepository.MOD_ID,value=Dist.CLIENT)
public final class CuriosGogglesClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){
        if(ModList.get().isLoaded("curios"))event.enqueueWork(ApiBridge::register);
    }
    private static final class ApiBridge {
        private static void register(){top.theillusivec4.curios.api.client.ICurioRenderer.register(AstralContent.RESONANCE_GOGGLES.get(),GogglesRenderer::new);}
    }
    /** Uses the same extracted HEAD item geometry as vanilla equipment. */
    private static final class GogglesRenderer implements top.theillusivec4.curios.api.client.ICurioRenderer {
        @Override public <S extends LivingEntityRenderState,M extends EntityModel<? super S>> void render(
                ItemStack stack,top.theillusivec4.curios.api.SlotContext slot,PoseStack pose,SubmitNodeCollector collector,
                int light,S state,RenderLayerParent<S,M> parent,EntityRendererProvider.Context context,float yaw,float pitch){
            var wearer=slot.entity();var helmet=wearer.getItemBySlot(EquipmentSlot.HEAD);
            if(!slot.visible()||helmet.is(AstralContent.RESONANCE_GOGGLES.get())||!(parent.getModel() instanceof HeadedModel head))return;
            pose.pushPose();
            parent.getModel().root().translateAndRotate(pose);
            head.translateToHead(pose);
            CustomHeadLayer.translateToHead(pose,CustomHeadLayer.Transforms.DEFAULT);
            var equippable=helmet.get(DataComponents.EQUIPPABLE);
            if(equippable!=null&&equippable.slot()==EquipmentSlot.HEAD&&equippable.assetId().isPresent())pose.scale(1.16F,1.16F,1.16F);
            var item=new ItemStackRenderState();
            Minecraft.getInstance().getItemModelResolver().updateForLiving(item,stack,ItemDisplayContext.HEAD,wearer);
            item.submit(pose,collector,light,OverlayTexture.NO_OVERLAY,state.outlineColor);
            pose.popPose();
        }
    }
    private CuriosGogglesClient(){}
}
