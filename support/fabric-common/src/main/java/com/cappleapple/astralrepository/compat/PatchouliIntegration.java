package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.content.FieldGuideItem;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import com.cappleapple.astralrepository.platform.ModList;
import com.cappleapple.astralrepository.platform.registry.DeferredItem;
import com.cappleapple.astralrepository.platform.registry.DeferredRegister;

/** Optional book registration; the API bridge is loaded only when Patchouli is installed. */
public final class PatchouliIntegration {
    public static final Identifier BOOK = Identifier.fromNamespaceAndPath("astral_repository", "field_guide");

    public static final String POWER_FLAG = "astral_repository:power_enabled";

    public static boolean isLoaded() { return ModList.get().isLoaded("patchouli"); }

    public static Optional<DeferredItem<FieldGuideItem>> register(DeferredRegister.Items items) {
        return isLoaded() ? Optional.of(items.registerItem("field_guide", props -> new FieldGuideItem(props.stacksTo(1)))) : Optional.empty();
    }

    public static void open(ServerPlayer player) {
        if (isLoaded()) ApiBridge.open(player);
    }

    public static void refreshPowerVisibility(boolean enabled) {
        if (isLoaded()) ClientBridge.refreshPowerVisibility(enabled);
    }

    private static final class ClientBridge {
        private static void refreshPowerVisibility(boolean enabled) {
            OptionalApi.call(OptionalApi.call(null,"vazkii.patchouli.api.PatchouliAPI","get"),"vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI","setConfigFlag",new Class<?>[]{String.class,boolean.class},POWER_FLAG,enabled);
            if (net.minecraft.client.Minecraft.getInstance().level != null)
                OptionalApi.call(OptionalApi.field("vazkii.patchouli.client.book.ClientBookRegistry","INSTANCE"),"vazkii.patchouli.client.book.ClientBookRegistry","reload");
        }
    }

    private static final class ApiBridge {
        private static void open(ServerPlayer player) {
            OptionalApi.call(OptionalApi.call(null,"vazkii.patchouli.api.PatchouliAPI","get"),"vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI","openBookGUI",new Class<?>[]{ServerPlayer.class,Identifier.class},player,BOOK);
        }
    }
    private PatchouliIntegration() {}
}
