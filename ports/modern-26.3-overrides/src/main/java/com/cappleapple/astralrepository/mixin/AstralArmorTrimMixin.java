package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.client.AstralPlaneRenderType;
import com.cappleapple.astralrepository.content.AstralTrims;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EquipmentLayerRenderer.class)
public abstract class AstralArmorTrimMixin {
    @Redirect(method="renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorTrim(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType astral$trimMaterial(Identifier trimTexture, boolean decal, EquipmentClientInfo.LayerType layerType,
            ResourceKey<EquipmentAsset> equipment, Model<?> model, Object state, ItemStack stack,
            PoseStack pose, SubmitNodeCollector collector, int light, Identifier texture, int outline, int order) {
        return AstralTrims.isAstral(stack.get(DataComponents.TRIM)) ? AstralPlaneRenderType.withAtlas(AstralPlaneRenderType.armorTrim(decal),trimTexture) : RenderTypes.armorTrim(trimTexture,decal);
    }
}
