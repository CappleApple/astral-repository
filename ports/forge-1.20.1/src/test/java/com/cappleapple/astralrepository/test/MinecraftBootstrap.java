package com.cappleapple.astralrepository.test;

/** Bootstraps vanilla registries before ItemStack codecs initialize in a plain JUnit JVM. */
public final class MinecraftBootstrap implements org.junit.jupiter.api.extension.BeforeAllCallback {
    private static boolean initialized;
    @Override public synchronized void beforeAll(org.junit.jupiter.api.extension.ExtensionContext context) throws Exception {
        if(initialized)return;
        // ModLauncher normally adds event constructors. Seed Forge's supported internal
        // listener-list path because plain JUnit does not run its bytecode transformer.
        var listeners=net.minecraftforge.eventbus.api.EventListenerHelper.class
                .getDeclaredMethod("getListenerListInternal",Class.class,boolean.class);
        listeners.setAccessible(true);
        seed(listeners,net.minecraftforge.network.NetworkEvent.class);
        for(Class<?> event:net.minecraftforge.network.NetworkEvent.class.getDeclaredClasses())
            if(net.minecraftforge.eventbus.api.Event.class.isAssignableFrom(event))seed(listeners,event);
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        initialized=true;
    }
    private static void seed(java.lang.reflect.Method listeners,Class<?> type) throws Exception {
        if(type==net.minecraftforge.eventbus.api.Event.class)return;
        seed(listeners,type.getSuperclass());listeners.invoke(null,type,true);
    }
}
