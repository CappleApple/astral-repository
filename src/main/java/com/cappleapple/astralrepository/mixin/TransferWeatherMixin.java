package com.cappleapple.astralrepository.mixin;
import com.cappleapple.astralrepository.client.TransferRenderPass;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Classic transparency transfers draw after cloud depth, while their staged vertices remain valid. */
@Mixin(WeatherEffectRenderer.class)
public abstract class TransferWeatherMixin {
    @Inject(method="render",at=@At("RETURN"))
    private void astral$afterWeather(CallbackInfo ci){TransferRenderPass.afterWeather();}
}
