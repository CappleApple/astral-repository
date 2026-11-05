package com.cappleapple.astralrepository.api;

import java.lang.reflect.Method;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Optional Mekanism chemical identity; amounts use the native long unit. */
public final class ChemicalKey implements ResourceKey {
    private final ResourceLocation kind, id;
    private final Object sample;
    private ChemicalKey(ResourceLocation kind, ResourceLocation id, Object sample) {
        this.kind=kind;this.id=id;this.sample=copyWithAmount(sample,1);
    }
    public static ChemicalKey fromNative(Object stack) {
        Objects.requireNonNull(stack);
        try {
            if ((Boolean)stack.getClass().getMethod("isEmpty").invoke(stack)) throw new IllegalArgumentException("Empty chemical identity");
            String name=stack.getClass().getName();
            if (!name.startsWith("mekanism.api.chemical.")) throw new IllegalArgumentException("Not a Mekanism chemical stack");
            ResourceLocation kind=name.contains(".gas.")?ResourceKinds.GAS:name.contains(".infuse.")?ResourceKinds.INFUSION:
                    name.contains(".pigment.")?ResourceKinds.PIGMENT:name.contains(".slurry.")?ResourceKinds.SLURRY:ResourceKinds.CHEMICAL;
            Object id=stack.getClass().getMethod("getTypeRegistryName").invoke(stack);
            return new ChemicalKey(kind, ResourceLocation.parse(id.toString()), stack);
        } catch (ReflectiveOperationException failure) { throw new IllegalArgumentException("Unsupported Mekanism chemical stack",failure); }
    }
    public Object sample(){return sampleWithAmount(1);}
    public Object sampleWithAmount(long amount){if(amount<0)throw new IllegalArgumentException("Negative chemical amount");return copyWithAmount(sample,amount);}
    public ResourceLocation type(){return kind;}
    public ResourceLocation kind(){return kind;}
    public ResourceLocation id(){return id;}
    @Override public boolean equals(Object other){return other instanceof ChemicalKey key&&kind.equals(key.kind)&&id.equals(key.id);}
    @Override public int hashCode(){return Objects.hash(kind,id);}
    @Override public String toString(){return kind+"/"+id;}
    private static Object copyWithAmount(Object stack,long amount){
        try { return stack.getClass().getMethod("copyWithAmount",long.class).invoke(stack,amount); }
        catch(NoSuchMethodException oldApi){try{Object copy=stack.getClass().getMethod("copy").invoke(stack);copy.getClass().getMethod("setAmount",long.class).invoke(copy,amount);return copy;}
            catch(ReflectiveOperationException failure){throw new IllegalArgumentException("Cannot copy chemical stack",failure);}}
        catch(ReflectiveOperationException failure){throw new IllegalArgumentException("Cannot copy chemical stack",failure);}
    }
    /** Resolve only through the installed optional registry; absent chemicals never become substitute resources. */
    public static ChemicalKey resolve(ResourceLocation kind,ResourceLocation id){
        try {
            Class<?> api=Class.forName("mekanism.api.MekanismAPI");Object registry=null;
            if(kind.equals(ResourceKinds.CHEMICAL))registry=api.getField("CHEMICAL_REGISTRY").get(null);
            else{
                String method=kind.equals(ResourceKinds.GAS)?"gasRegistry":kind.equals(ResourceKinds.INFUSION)?"infuseTypeRegistry":
                        kind.equals(ResourceKinds.PIGMENT)?"pigmentRegistry":kind.equals(ResourceKinds.SLURRY)?"slurryRegistry":"";
                if(method.isEmpty())throw new IllegalArgumentException("Unknown chemical kind");
                registry=api.getMethod(method).invoke(null);
            }
            Object chemical=null;
            for(String methodName:new String[]{"getValue","get"})for(Method method:registry.getClass().getMethods()){
                if(method.getName().equals(methodName)&&method.getParameterCount()==1&&method.getParameterTypes()[0].isInstance(id)){
                    chemical=method.invoke(registry,id);break;
                }
                if(chemical!=null)break;
            }
            if(chemical==null)throw new IllegalArgumentException("Chemical no longer registered");
            Object stack;
            try{stack=chemical.getClass().getMethod("getStack",long.class).invoke(chemical,1L);}
            catch(NoSuchMethodException legacy){
                String name=kind.equals(ResourceKinds.GAS)?"gas.GasStack":kind.equals(ResourceKinds.INFUSION)?"infuse.InfusionStack":
                        kind.equals(ResourceKinds.PIGMENT)?"pigment.PigmentStack":kind.equals(ResourceKinds.SLURRY)?"slurry.SlurryStack":"ChemicalStack";
                stack=null;for(var constructor:Class.forName("mekanism.api.chemical."+name).getConstructors()){
                    var parameters=constructor.getParameterTypes();if(parameters.length==2&&parameters[0].isInstance(chemical)&&parameters[1]==long.class){stack=constructor.newInstance(chemical,1L);break;}
                }
                if(stack==null)throw new IllegalArgumentException("Cannot resolve native chemical stack");
            }
            ChemicalKey result=fromNative(stack);
            if(!result.type().equals(kind)||!result.id().equals(id))throw new IllegalArgumentException("Chemical no longer registered");
            return result;
        } catch(ReflectiveOperationException|LinkageError failure){throw new IllegalArgumentException("Mekanism chemical unavailable: "+id,failure);}
    }
}
