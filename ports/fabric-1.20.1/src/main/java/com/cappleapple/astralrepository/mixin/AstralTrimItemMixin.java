package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.client.AstralTrimItemModel;
import com.cappleapple.astralrepository.content.AstralTrims;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemRenderer.class)
public abstract class AstralTrimItemMixin {
    @Inject(method="getModel", at=@At("RETURN"), cancellable=true)
    private void astral$trimIcon(ItemStack stack, Level level, LivingEntity wearer, int seed,
            CallbackInfoReturnable<BakedModel> ci) {
        if (level!=null&&AstralTrims.isAstral(net.minecraft.world.item.armortrim.ArmorTrim.getTrim(level.registryAccess(),stack).orElse(null)))
            ci.setReturnValue(AstralTrimItemModel.wrap(ci.getReturnValue()));
    }
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void astral$renderTrim(ItemStack stack,net.minecraft.world.item.ItemDisplayContext context,boolean left,com.mojang.blaze3d.vertex.PoseStack poses,net.minecraft.client.renderer.MultiBufferSource buffers,int light,int overlay,BakedModel model,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        if(model instanceof AstralTrimItemModel trim){trim.render((ItemRenderer)(Object)this,stack,context,left,poses,buffers,light,overlay);ci.cancel();}
    }
}
