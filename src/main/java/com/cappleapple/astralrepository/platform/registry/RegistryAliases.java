package com.cappleapple.astralrepository.platform.registry;
import java.util.*;
import net.minecraft.resources.Identifier;
public final class RegistryAliases {
    private static final Map<Identifier,Map<Identifier,Identifier>> ALIASES=new HashMap<>();
    public static void add(Identifier registry,Identifier oldId,Identifier current){ALIASES.computeIfAbsent(registry,ignored->new HashMap<>()).put(oldId,current);}
    public static Identifier resolve(Identifier registry,Identifier id){return ALIASES.getOrDefault(registry,Map.of()).getOrDefault(id,id);}
    private RegistryAliases(){}
}
