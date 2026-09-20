package com.cappleapple.astralrepository.mixin;
import com.cappleapple.astralrepository.client.TransferRenderPass;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import com.mojang.renderpearl.api.commands.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Uses the active classic-transparency pass; OIT already shares cloud depth directly. */
@Mixin(WeatherEffectRenderer.class)
public abstract class TransferWeatherMixin {
    @Inject(method="render",at=@At("RETURN"))
    private void astral$afterWeather(WeatherRenderState state,RenderPass pass,CallbackInfo ci){TransferRenderPass.afterWeather(pass);}
}
