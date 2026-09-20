package com.cappleapple.astralrepository;

import com.cappleapple.astralrepository.compat.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.network.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import com.cappleapple.astralrepository.platform.IEventBus;

import com.cappleapple.astralrepository.platform.registry.*;
import org.slf4j.Logger;

public final class AstralRepository implements net.fabricmc.api.ModInitializer {
    public static final String MOD_ID="astral_repository";
    public static final Logger LOGGER=LogUtils.getLogger();
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,MOD_ID);
    public static final DeferredHolder<MenuType<?>,MenuType<com.cappleapple.astralrepository.menu.RuneSettingsMenu>> RUNE_SETTINGS_MENU=MENUS.register("rune_settings",()->new MenuType<>(com.cappleapple.astralrepository.menu.RuneSettingsMenu::new,FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>,MenuType<NexusMenu>> NEXUS_MENU=MENUS.register("storage_nexus",()->new MenuType<>(NexusMenu::new,FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>,MenuType<com.cappleapple.astralrepository.menu.RecipeTomeMenu>> RECIPE_TOME_MENU=MENUS.register("recipe_tome",()->new MenuType<>(com.cappleapple.astralrepository.menu.RecipeTomeMenu::new,FeatureFlags.DEFAULT_FLAGS));
    @Override public void onInitialize(){
        com.cappleapple.astralrepository.platform.ConfigRegistration.register(MOD_ID,net.neoforged.fml.config.ModConfig.Type.COMMON,AstralConfig.SPEC);
        com.cappleapple.astralrepository.platform.ConfigRegistration.register(MOD_ID,net.neoforged.fml.config.ModConfig.Type.SERVER,AstralServerConfig.SPEC,"astral_repository-server.toml");
        com.cappleapple.astralrepository.platform.ConfigRegistration.register(MOD_ID,net.neoforged.fml.config.ModConfig.Type.COMMON,CompatConfig.SPEC,"astral_repository-compat.toml");
        AstralContent.setup(null);MENUS.register();com.cappleapple.astralrepository.platform.capabilities.NodeStorages.register();
        NetworkPackets.setup(null);RecipeTomePackets.setup(null);RunePackets.setup(null);RuneSettingsPackets.setup(null);RunePickupPackets.setup(null);WandPackets.setup(null);
        PowerNodeVisibilityPackets.Registration.register(new com.cappleapple.astralrepository.platform.network.event.RegisterPayloadHandlersEvent());
        RuneProgramming.openSettings=RuneSettingsPackets::open;
        ContentHooks.openNexus=NetworkManager::open;ContentHooks.remoteUse=NetworkManager::remote;ContentHooks.topologyChanged=NetworkManager::changed;ContentHooks.capacityCost=CapacityCosts::unitCost;ContentHooks.capacityCostExact=CapacityCosts::exactCost;
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity,level)->{if(entity instanceof CrystalNodeBlockEntity node)node.onLoad();});
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{var event=new com.cappleapple.astralrepository.platform.event.tick.ServerTickEvent.Post(server);RunePackets.tick(event);NetworkManager.tick(event);PowerNodeVisibilityPackets.tick(event);TransferVisualDispatcher.flush(event);});
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server->com.cappleapple.astralrepository.platform.network.PacketDistributor.server=server);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server->NetworkManager.stop(new com.cappleapple.astralrepository.platform.event.server.ServerStoppingEvent(server)));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->{var event=new com.cappleapple.astralrepository.platform.event.server.ServerStoppedEvent(server);NetworkManager.stopped(event);com.cappleapple.astralrepository.platform.capabilities.NodeStorages.clear();com.cappleapple.astralrepository.platform.PlayerSessionData.clear();PowerNodeVisibilityPackets.stop(event);TransferVisualDispatcher.stop(event);com.cappleapple.astralrepository.platform.network.PacketDistributor.server=null;});
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register((level,chunk,generated)->{var event=new com.cappleapple.astralrepository.platform.event.level.ChunkEvent.Load(level,chunk);NetworkManager.chunkLoad(event);AstralMineralMigration.loaded(event);});
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_UNLOAD.register((level,chunk)->NetworkManager.chunkUnload(new com.cappleapple.astralrepository.platform.event.level.ChunkEvent.Unload(level,chunk)));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->PowerNodeVisibilityPackets.login(new com.cappleapple.astralrepository.platform.event.entity.player.PlayerEvent.PlayerLoggedInEvent(handler.player)));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server,manager,success)->{if(success)NetworkManager.datapacks(new com.cappleapple.astralrepository.platform.event.OnDatapackSyncEvent(server.getPlayerList()));});
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher,context,selection)->com.cappleapple.astralrepository.command.AstralCommands.register(new com.cappleapple.astralrepository.platform.event.RegisterCommandsEvent(dispatcher,context)));
        com.cappleapple.astralrepository.crafting.ProcessingRules.registerReload(new com.cappleapple.astralrepository.platform.event.AddReloadListenerEvent());
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,level,hand,hit)->{var event=new com.cappleapple.astralrepository.platform.event.entity.player.PlayerInteractEvent.RightClickBlock(player,level,hand,hit);RuneProgramming.interact(event);return event.isCanceled()?event.result():net.minecraft.world.InteractionResult.PASS;});
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player,level,hand,pos,face)->{var event=new com.cappleapple.astralrepository.platform.event.entity.player.PlayerInteractEvent.LeftClickBlock(player,level,hand,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),face,pos,false));RuneProgramming.leftClick(event);return event.isCanceled()?net.minecraft.world.InteractionResult.FAIL:net.minecraft.world.InteractionResult.PASS;});
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_OVERWORLD),net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_DECORATION,net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE,net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID,"astral_geode")));
    }
}
