package com.cappleapple.astralrepository;

import com.cappleapple.astralrepository.compat.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.network.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.*;
import org.slf4j.Logger;

@Mod(AstralRepository.MOD_ID)
public final class AstralRepository {
    public static final String MOD_ID="astral_repository";
    public static final Logger LOGGER=LogUtils.getLogger();
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,MOD_ID);
    public static final RegistryObject<MenuType<com.cappleapple.astralrepository.menu.RuneSettingsMenu>> RUNE_SETTINGS_MENU=MENUS.register("rune_settings",()->new MenuType<>(com.cappleapple.astralrepository.menu.RuneSettingsMenu::new,FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<MenuType<NexusMenu>> NEXUS_MENU=MENUS.register("storage_nexus",()->new MenuType<>(NexusMenu::new,FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<MenuType<com.cappleapple.astralrepository.menu.RecipeTomeMenu>> RECIPE_TOME_MENU=MENUS.register("recipe_tome",()->new MenuType<>(com.cappleapple.astralrepository.menu.RecipeTomeMenu::new,FeatureFlags.DEFAULT_FLAGS));
    public AstralRepository(){
        IEventBus bus=net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        var container=net.minecraftforge.fml.ModLoadingContext.get();
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event)->bus.post(new com.cappleapple.astralrepository.platform.RegisterPayloadHandlersEvent()));
        container.registerConfig(ModConfig.Type.COMMON,AstralConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER,AstralServerConfig.SPEC,"astral_repository-server.toml");
        container.registerConfig(ModConfig.Type.COMMON,CompatConfig.SPEC,"astral_repository-compat.toml");
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT)
            container.registerConfig(ModConfig.Type.CLIENT,AstralClientConfig.SPEC,"astral_repository-client.toml");
        AstralContent.setup(bus);MENUS.register(bus);NetworkPackets.setup(bus);RecipeTomePackets.setup(bus);RunePackets.setup(bus);RuneSettingsPackets.setup(bus);RunePickupPackets.setup(bus);WandPackets.setup(bus);RuneProgramming.openSettings=RuneSettingsPackets::open;
        MinecraftForge.EVENT_BUS.addListener(RunePackets::tick);
        MinecraftForge.EVENT_BUS.addListener(RuneProgramming::leftClick);
        ContentHooks.openNexus=NetworkManager::open;ContentHooks.remoteUse=NetworkManager::remote;ContentHooks.topologyChanged=NetworkManager::changed;ContentHooks.capacityCost=CapacityCosts::unitCost;ContentHooks.capacityCostExact=CapacityCosts::exactCost;
        MinecraftForge.EVENT_BUS.addListener(NetworkManager::tick);
        MinecraftForge.EVENT_BUS.addListener(com.cappleapple.astralrepository.command.AstralCommands::register);
        MinecraftForge.EVENT_BUS.addListener(com.cappleapple.astralrepository.crafting.ProcessingRules::registerReload);
        MinecraftForge.EVENT_BUS.addListener(NetworkManager::chunkLoad);
        MinecraftForge.EVENT_BUS.addListener(NetworkManager::chunkUnload);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) -> NetworkManager.blockChanged(event));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.level.BlockEvent.BreakEvent event) -> NetworkManager.blockChanged(event));
        MinecraftForge.EVENT_BUS.addListener(NetworkManager::stop);
        MinecraftForge.EVENT_BUS.addListener(NetworkManager::stopped);
        MinecraftForge.EVENT_BUS.addListener(NetworkManager::datapacks);
    }
}







