package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.client.AstralPlaneRenderType;
import com.cappleapple.astralrepository.content.AstralTrims;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.armortrim.ArmorTrim;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class AstralArmorTrimMixin {
    @Shadow @Final private TextureAtlas armorTrimAtlas;

    @Inject(method="renderTrim(Lnet/minecraft/core/Holder;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/armortrim/ArmorTrim;Lnet/minecraft/client/model/Model;Z)V", at=@At("HEAD"), cancellable=true)
    private void astral$renderTrim(Holder<ArmorMaterial> armor, PoseStack poses, MultiBufferSource buffers,
            int light, ArmorTrim trim, Model model, boolean leggings, CallbackInfo ci) {
        if (!AstralTrims.isAstral(trim) || !AstralPlaneRenderType.ready()) return;
        var sprite = armorTrimAtlas.getSprite(leggings ? trim.innerTexture(armor) : trim.outerTexture(armor));
        var material = AstralPlaneRenderType.armorTrim(trim.pattern().value().decal());
        model.renderToBuffer(poses, sprite.wrap(buffers.getBuffer(material)), light, OverlayTexture.NO_OVERLAY);
        ci.cancel();
    }
}
