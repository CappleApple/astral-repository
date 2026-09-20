package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.content.AstralContent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid=AstralRepository.MOD_ID, bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class CuriosGogglesClient {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("curios")) event.enqueueWork(ApiBridge::register);
    }

    private static final class ApiBridge {
        private static void register() {
            top.theillusivec4.curios.api.client.CuriosRendererRegistry.register(
                    AstralContent.RESONANCE_GOGGLES.get(), GogglesRenderer::new);
        }
    }

    /** Shares the item's HEAD model and lens material with vanilla head-slot equipment. */
    private static final class GogglesRenderer implements top.theillusivec4.curios.api.client.ICurioRenderer {
        @Override
        public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack,
                top.theillusivec4.curios.api.SlotContext context, PoseStack pose,
                RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
                float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                float netHeadYaw, float headPitch) {
            LivingEntity wearer = context.entity();
            if (!context.visible() || wearer.getItemBySlot(EquipmentSlot.HEAD).is(AstralContent.RESONANCE_GOGGLES.get())
                    || !(parent.getModel() instanceof HeadedModel model)) return;
            pose.pushPose();
            if (wearer.isBaby() && !(wearer instanceof Villager)) {
                pose.translate(0, .03125F, 0);
                pose.scale(.7F, .7F, .7F);
                pose.translate(0, 1, 0);
            }
            model.getHead().translateAndRotate(pose);
            CustomHeadLayer.translateToHead(pose, wearer instanceof Villager || wearer instanceof ZombieVillager);
            // Clear vanilla helmet thickness while keeping the lenses centered on the eyes.
            if(wearer.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof net.minecraft.world.item.ArmorItem)
                pose.scale(1.16F,1.16F,1.16F);
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer()
                    .renderItem(wearer, stack, ItemDisplayContext.HEAD, false, pose, buffers, light);
            pose.popPose();
        }
    }

    private CuriosGogglesClient() {}
}
