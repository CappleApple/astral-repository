package com.cappleapple.astralrepository.platform.fluids;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;import net.minecraft.world.level.material.Fluid;import net.minecraft.world.level.material.Fluids;import net.minecraft.core.HolderLookup;import net.minecraft.nbt.*;import net.minecraft.network.chat.Component;
/** Local millibucket value. Fabric storage conversion happens only at transfer boundaries. */
public final class FluidStack {
 public static final FluidStack EMPTY=new FluidStack(Fluids.EMPTY,0);private final FluidVariant variant;private int amount;
 public FluidStack(Fluid fluid,int amount){this(FluidVariant.of(fluid),amount);}public FluidStack(FluidVariant variant,int amount){this.variant=variant;this.amount=Math.max(amount,0);}
 public FluidStack(FluidStack stack,int amount){this(stack.variant,amount);}public Component getDisplayName(){return getHoverName();}
 public FluidVariant variant(){return variant;} public Fluid getFluid(){return variant.getFluid();}public int getAmount(){return amount;}public void setAmount(int n){amount=Math.max(n,0);}public void grow(int n){setAmount(amount+n);}public void shrink(int n){setAmount(amount-n);}public boolean isEmpty(){return amount<=0||variant.isBlank();}public FluidStack copy(){return new FluidStack(variant,amount);}public FluidStack copyWithAmount(int n){return new FluidStack(variant,n);}
 public static int hashFluidAndComponents(FluidStack stack){return stack.variant.hashCode();}public static boolean isSameFluidSameComponents(FluidStack a,FluidStack b){return a.variant.equals(b.variant);}
 public boolean is(net.minecraft.tags.TagKey<Fluid> tag){return getFluid().is(tag);}
 public CompoundTag getTag(){return variant.getNbt();}public static boolean isSameFluidSameTags(FluidStack a,FluidStack b){return a.variant.equals(b.variant);}public boolean isFluidEqual(FluidStack other){return variant.equals(other.variant);}
 public Component getHoverName(){return net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.getName(variant);}
 public CompoundTag writeToNBT(CompoundTag tag){tag.merge((CompoundTag)save(null));return tag;}public static FluidStack loadFluidStackFromNBT(CompoundTag tag){return parseOptional(null,tag);}
 public Tag save(HolderLookup.Provider registries){var tag=new CompoundTag();tag.put("Variant",variant.toNbt());tag.putInt("Amount",amount);return tag;}
 public static FluidStack parseOptional(HolderLookup.Provider registries,CompoundTag tag){if(!tag.contains("Variant"))return EMPTY;return new FluidStack(FluidVariant.fromNbt(tag.getCompound("Variant")),tag.getInt("Amount"));}
}
