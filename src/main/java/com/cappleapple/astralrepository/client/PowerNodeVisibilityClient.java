package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.compat.AstralEmiPlugin;
import com.cappleapple.astralrepository.compat.AstralJeiPlugin;
import com.cappleapple.astralrepository.compat.PatchouliIntegration;
import com.cappleapple.astralrepository.content.PowerNodeVisibility;
import com.cappleapple.astralrepository.mixin.CreativeTabsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

@EventBusSubscriber(modid=AstralRepository.MOD_ID, value=Dist.CLIENT)
public final class PowerNodeVisibilityClient {
    private static Boolean lastEnabled;

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        PowerNodeVisibility.clearServerState();
        lastEnabled = null;
    }

    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        boolean enabled = PowerNodeVisibility.powerEnabled();
        if (lastEnabled != null && lastEnabled == enabled) return;
        lastEnabled = enabled;
        CreativeTabsAccessor.astral$invalidate(null);
        if (ModList.get().isLoaded("jei")) AstralJeiPlugin.refreshPowerVisibility();
        if (ModList.get().isLoaded("emi") && Minecraft.getInstance().level != null) AstralEmiPlugin.refreshPowerVisibility();
        PatchouliIntegration.refreshPowerVisibility(enabled);
    }

    private PowerNodeVisibilityClient() {}
}
