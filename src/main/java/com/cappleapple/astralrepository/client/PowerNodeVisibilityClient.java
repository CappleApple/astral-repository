package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.compat.AstralEmiPlugin;
import com.cappleapple.astralrepository.compat.AstralJeiPlugin;
import com.cappleapple.astralrepository.compat.PatchouliIntegration;
import com.cappleapple.astralrepository.content.PowerNodeVisibility;
import com.cappleapple.astralrepository.mixin.CreativeTabsAccessor;
import net.minecraft.client.Minecraft;
import com.cappleapple.astralrepository.platform.ModList;
import com.cappleapple.astralrepository.platform.client.event.ClientPlayerNetworkEvent;
import com.cappleapple.astralrepository.platform.client.event.ClientTickEvent;

public final class PowerNodeVisibilityClient {
    private static Boolean lastEnabled;

    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        PowerNodeVisibility.clearServerState();
        lastEnabled = null;
    }

    public static void tick(com.cappleapple.astralrepository.platform.client.event.ClientTickEvent.Post event) {
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
