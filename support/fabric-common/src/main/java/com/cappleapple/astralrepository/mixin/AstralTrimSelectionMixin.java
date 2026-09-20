package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.content.AstralTrims;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.renderer.item.SelectItemModel;
import net.minecraft.client.renderer.item.properties.select.TrimMaterialProperty;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Reuses an existing trim-mask model when a pack has no explicit astral material case. */
@Mixin(SelectItemModel.UnbakedSwitch.class)
public abstract class AstralTrimSelectionMixin {
    @ModifyVariable(method="createModelGetter",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private Object2ObjectMap<?,?> astral$trimFallback(Object2ObjectMap<?,?> input) {
        if(!(((SelectItemModel.UnbakedSwitch<?,?>)(Object)this).property() instanceof TrimMaterialProperty))return input;
        @SuppressWarnings("unchecked") var models=(Object2ObjectMap<Object,Object>)(Object)input;
        var quartz=ResourceKey.create(Registries.TRIM_MATERIAL,Identifier.withDefaultNamespace("quartz"));
        if(!models.containsKey(AstralTrims.MATERIAL)&&models.containsKey(quartz))models.put(AstralTrims.MATERIAL,models.get(quartz));
        return input;
    }
}
