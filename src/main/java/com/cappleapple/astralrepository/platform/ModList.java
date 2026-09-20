package com.cappleapple.astralrepository.platform;
public final class ModList { private static final ModList INSTANCE=new ModList(); public static ModList get(){return INSTANCE;} public boolean isLoaded(String id){return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id);} }
