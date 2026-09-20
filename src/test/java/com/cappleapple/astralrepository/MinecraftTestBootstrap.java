package com.cappleapple.astralrepository;
public final class MinecraftTestBootstrap implements org.junit.platform.launcher.LauncherSessionListener {
 @Override public void launcherSessionOpened(org.junit.platform.launcher.LauncherSession session){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
}
