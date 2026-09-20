package com.cappleapple.astralrepository.platform.client.extensions.common;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.resources.Identifier;
import com.cappleapple.astralrepository.platform.fluids.FluidStack;

/** Native fluid model textures and Fabric variant tinting. */
public record IClientFluidTypeExtensions(Fluid fluid) {
    public static IClientFluidTypeExtensions of(Fluid fluid){return new IClientFluidTypeExtensions(fluid);}
    public Identifier getStillTexture(){return getStillTexture(new FluidStack(fluid,1000));}
    public Identifier getFlowingTexture(){return Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState()).flowingMaterial().sprite().contents().name();}
    public int getTintColor(){return getTintColor(new FluidStack(fluid,1000));}
    public Identifier getStillTexture(FluidStack stack){return Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(stack.getFluid().defaultFluidState()).stillMaterial().sprite().contents().name();}
    public int getTintColor(FluidStack stack){return net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering.getColor(stack.variant());}
}
