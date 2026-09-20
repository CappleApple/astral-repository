package com.cappleapple.astralrepository.mixin;
import com.cappleapple.astralrepository.platform.registry.RegistryAliases;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(MappedRegistry.class)
abstract class FabricRegistryAliasMixin {
    @ModifyVariable(method={"getValue(Lnet/minecraft/resources/Identifier;)Ljava/lang/Object;","get(Lnet/minecraft/resources/Identifier;)Ljava/util/Optional;","containsKey(Lnet/minecraft/resources/Identifier;)Z"},at=@At("HEAD"),argsOnly=true)
    private Identifier astral$legacyId(Identifier id){return RegistryAliases.resolve(((Registry<?>)(Object)this).key().identifier(),id);}
    @ModifyVariable(method={"get(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;","getValue(Lnet/minecraft/resources/ResourceKey;)Ljava/lang/Object;"},at=@At("HEAD"),argsOnly=true)
    private ResourceKey<?> astral$legacyKey(ResourceKey<?> key){var registry=((Registry<?>)(Object)this).key();var id=RegistryAliases.resolve(registry.identifier(),key.identifier());return id.equals(key.identifier())?key:ResourceKey.create((ResourceKey)registry,id);}
}
