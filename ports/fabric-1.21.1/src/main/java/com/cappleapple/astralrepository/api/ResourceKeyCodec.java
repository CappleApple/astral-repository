package com.cappleapple.astralrepository.api;

import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.cappleapple.astralrepository.platform.fluids.FluidStack;

/** Durable resource identities. Missing optional registries retain opaque escrow that cannot be transferred. */
public final class ResourceKeyCodec {
    private ResourceKeyCodec() {}
    public static final class DeferredKey implements ResourceKey {
        private final ResourceLocation type,id;private final CompoundTag data;
        private DeferredKey(ResourceLocation type,ResourceLocation id,CompoundTag data){this.type=type;this.id=id;this.data=data.copy();}
        public ResourceLocation type(){return type;}public ResourceLocation id(){return id;}
        @Override public boolean equals(Object other){return other instanceof DeferredKey key&&type.equals(key.type)&&id.equals(key.id)&&data.equals(key.data);}
        @Override public int hashCode(){return Objects.hash(type,id,data);}
    }
    public static CompoundTag save(ResourceKey key,HolderLookup.Provider registries){
        if(key instanceof DeferredKey deferred)return deferred.data.copy();
        CompoundTag tag=new CompoundTag();tag.putString("Type",key.type().toString());tag.putString("Resource",key.id().toString());
        if(key instanceof ResourceKinds.ScalarKey)tag.putBoolean("Scalar",true);
        if(key instanceof ItemKey item)tag.put("Item",item.sample().save(registries));
        else if(key instanceof FluidKey fluid)tag.put("Fluid",fluid.sample().save(registries));
        return tag;
    }
    public static ResourceKey load(CompoundTag tag,HolderLookup.Provider registries){
        ResourceLocation type=ResourceLocation.tryParse(tag.getString("Type"));
        ResourceLocation id=ResourceLocation.tryParse(tag.getString("Resource"));
        CompoundTag item=tag.contains("Item")?tag.getCompound("Item"):tag.getCompound("Stack");
        if(type==null)type=(tag.contains("Item")||tag.contains("Stack"))?ResourceKinds.ITEM:tag.contains("Fluid")?ResourceKinds.FLUID:ResourceKinds.CHEMICAL;
        if(id==null)id=ResourceLocation.tryParse(item.getString("id"));
        if(id==null)id=ResourceLocation.parse("astral_repository:unavailable");
        try{
            if(tag.getBoolean("Scalar"))return new ResourceKinds.ScalarKey(type,id);
            if(tag.contains("Item")||tag.contains("Stack")){
                ItemStack value=ItemStack.parseOptional(registries,item);if(!value.isEmpty())return new ItemKey(value);
            }else if(tag.contains("Fluid")){
                FluidStack value=FluidStack.parseOptional(registries,tag.getCompound("Fluid"));if(!value.isEmpty())return new FluidKey(value);
            }else if(type.equals(ResourceKinds.CHEMICAL)||type.equals(ResourceKinds.GAS)||type.equals(ResourceKinds.INFUSION)||type.equals(ResourceKinds.PIGMENT)||type.equals(ResourceKinds.SLURRY))return ChemicalKey.resolve(type,id);
            else if(type.equals(ResourceKinds.ENERGY)||type.equals(ResourceKinds.SOURCE)||type.equals(ResourceKinds.STRESS))return new ResourceKinds.ScalarKey(type,id);
        }catch(RuntimeException|LinkageError unavailable){/* Keep the original record until its registry is available again. */}
        return new DeferredKey(type,id,tag);
    }
}
