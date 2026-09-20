package com.cappleapple.astralrepository.platform.client.event;
public final class RegisterClientTooltipComponentFactoriesEvent {public <T extends net.minecraft.world.inventory.tooltip.TooltipComponent> void register(Class<T> type,java.util.function.Function<T,net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> factory){net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback.EVENT.register(data->type.isInstance(data)?factory.apply(type.cast(data)):null);}}

