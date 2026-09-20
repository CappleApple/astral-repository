package com.cappleapple.astralrepository;
public final class MinecraftTestBootstrap implements org.junit.platform.launcher.LauncherSessionListener {
 @Override public void launcherSessionOpened(org.junit.platform.launcher.LauncherSession session){initialize();}
 private static boolean initialized;public static synchronized void initialize(){if(initialized)return;initialized=true;net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();var registries=net.minecraft.data.registries.VanillaRegistries.createLookup();net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);}
}
