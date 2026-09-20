package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.content.FieldGuideItem;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Optional book registration; the API bridge is loaded only when Patchouli is installed. */
public final class PatchouliIntegration {
    public static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath("astral_repository", "field_guide");

    public static final String POWER_FLAG = "astral_repository:power_enabled";

    public static boolean isLoaded() { return ModList.get().isLoaded("patchouli"); }

    public static Optional<DeferredItem<FieldGuideItem>> register(DeferredRegister.Items items) {
        return isLoaded() ? Optional.of(items.register("field_guide", () -> new FieldGuideItem(new Item.Properties().stacksTo(1)))) : Optional.empty();
    }

    public static void open(ServerPlayer player) {
        if (isLoaded()) ApiBridge.open(player);
    }

    public static void refreshPowerVisibility(boolean enabled) {
        if (isLoaded()) ClientBridge.refreshPowerVisibility(enabled);
    }

    private static final class ClientBridge {
        private static void refreshPowerVisibility(boolean enabled) {
            vazkii.patchouli.api.PatchouliAPI.get().setConfigFlag(POWER_FLAG, enabled);
            if (net.minecraft.client.Minecraft.getInstance().level != null)
                vazkii.patchouli.client.book.ClientBookRegistry.INSTANCE.reload();
        }
    }

    private static final class ApiBridge {
        private static void open(ServerPlayer player) {
            vazkii.patchouli.api.PatchouliAPI.get().openBookGUI(player, BOOK);
        }
    }
    private PatchouliIntegration() {}
}
