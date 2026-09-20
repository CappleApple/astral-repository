package com.cappleapple.astralrepository.port;

public final class PortTestBootstrap {
    private static boolean initialized;
    public static synchronized void initialize(){
        if(initialized)return;
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var vanilla=net.minecraft.data.registries.VanillaRegistries.createLookup();
        var trimKey=net.minecraft.core.registries.Registries.TRIM_MATERIAL;
        var trims=new net.minecraft.core.RegistrySetBuilder().add(trimKey,context->{
            net.minecraft.world.item.equipment.trim.TrimMaterials.bootstrap(context);
            try(var stream=PortTestBootstrap.class.getResourceAsStream("/data/astral_repository/trim_material/astral_gem.json")) {
                if(stream==null)throw new IllegalStateException("Missing actual Astral trim datapack");
                var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8));
                var trim=net.minecraft.world.item.equipment.trim.TrimMaterial.DIRECT_CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow();
                context.register(com.cappleapple.astralrepository.content.AstralTrims.MATERIAL,trim);
            }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        }).build(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        var registries=net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.concat(
                vanilla.listRegistries().filter(registry->!registry.key().equals(trimKey)),
                java.util.stream.Stream.of(trims.lookupOrThrow(trimKey))));
        // Datagen returns vanilla deferred tag holders; they have the same identity semantics as HolderSet.Named.
        var deferredTag=registries.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getOrThrow(net.minecraft.tags.BiomeTags.IS_OVERWORLD);
        net.neoforged.neoforge.common.CommonHooks.markComponentClassAsValid(deferredTag.getClass());
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries)
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        initialized=true;
    }
    private PortTestBootstrap(){}
}
