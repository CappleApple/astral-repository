package com.cappleapple.astralrepository.porttest;

import com.cappleapple.astralrepository.api.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Packaged-JAR regressions. No test entrypoint is included in the public mod. */
public final class FabricRuneTransitSmoke implements net.fabricmc.api.ModInitializer {
    @Override public void onInitialize(){
        if(!Boolean.getBoolean("astral_repository.runeTransitSmoke"))return;
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            String result;int count=0;
            try {
                for(var suite:List.of(RuneTransitGameTests.class,RuneAggregateTransitGameTests.class))
                    for(var method:Arrays.stream(suite.getDeclaredMethods()).filter(m->java.lang.reflect.Modifier.isPublic(m.getModifiers())&&java.lang.reflect.Modifier.isStatic(m.getModifiers())&&m.getParameterCount()==1&&m.getParameterTypes()[0]==PortGameTestHelper.class).sorted(Comparator.comparing(java.lang.reflect.Method::getName)).toList()){
                        try(var helper=new PortGameTestHelper(server.overworld(),count)){
                            method.invoke(null,helper);count++;
                            com.mojang.logging.LogUtils.getLogger().info("RUNE_TRANSIT_PASS {}",method.getName());
                        }
                    }
                result="PASS: "+count+" packaged rune transit regressions: delayed/instant push/pull of items, fluids, energy and source; one-tick pipelines; stock reservations; refunds; persistence; uncertain-handler quarantine; physical aggregate endpoints and network rebuilds.";
            } catch(Throwable failure){Throwable cause=failure instanceof java.lang.reflect.InvocationTargetException?failure.getCause():failure;result="FAIL after "+count+" regressions: "+cause;com.mojang.logging.LogUtils.getLogger().error("Packaged rune transit regression",cause);}
            try{Files.writeString(Path.of("rune-transit-result.txt"),result);}catch(Exception error){throw new RuntimeException(error);}finally{server.halt(false);}
        });
    }
    public static ResourceProvider scalar(String id,long[] amounts,int index){return new ResourceProvider(){
        public String id(){return id;}public Object identity(){return id;}public boolean valid(){return true;}public long capacity(){return 10000;}public net.minecraft.resources.ResourceLocation resourceType(){return ResourceKinds.SOURCE;}
        public Map<ResourceKey,Long> snapshot(){return Map.of(ResourceKinds.ARS_SOURCE,amounts[index]);}
        public long insert(ResourceKey key,long n,boolean simulate){long accepted=key.equals(ResourceKinds.ARS_SOURCE)?Math.min(n,10000-amounts[index]):0;if(!simulate)amounts[index]+=accepted;return accepted;}
        public long extract(ResourceKey key,long n,boolean simulate){long taken=key.equals(ResourceKinds.ARS_SOURCE)?Math.min(n,amounts[index]):0;if(!simulate)amounts[index]-=taken;return taken;}
    };}
}
