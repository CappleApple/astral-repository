package com.cappleapple.astralrepository.platform;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
/** The actual Fabric Forge Config API Port handles file loading and server synchronization. */
public final class ConfigRegistration {
    public static void register(String id,ModConfig.Type type,ModConfigSpec spec){fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry.INSTANCE.register(id,type,spec);}
    public static void register(String id,ModConfig.Type type,ModConfigSpec spec,String path){fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry.INSTANCE.register(id,type,spec,path);}
    private ConfigRegistration(){}
}
