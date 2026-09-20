package com.cappleapple.astralrepository.content;

import com.cappleapple.astralrepository.AstralConfig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Presentation follows the connected server's power setting without changing registry content. */
public final class PowerNodeVisibility {
    private static volatile Boolean serverPowerEnabled;

    public static boolean powerEnabled() {
        Boolean synced = serverPowerEnabled;
        return synced != null ? synced : AstralConfig.powerEnabled.get();
    }

    public static boolean visible(Item item) {
        return item != AstralContent.POWER_NODE.get().asItem() || powerEnabled();
    }

    public static boolean visible(ItemStack stack) { return visible(stack.getItem()); }
    public static boolean hasServerState() { return serverPowerEnabled != null; }
    public static void acceptServerState(boolean enabled) { serverPowerEnabled = enabled; }
    public static void clearServerState() { serverPowerEnabled = null; }

    private PowerNodeVisibility() {}
}
