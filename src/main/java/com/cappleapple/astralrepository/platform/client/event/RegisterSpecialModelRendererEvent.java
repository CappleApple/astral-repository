package com.cappleapple.astralrepository.platform.client.event;
public final class RegisterSpecialModelRendererEvent {public void register(net.minecraft.resources.Identifier id,com.mojang.serialization.MapCodec<? extends net.minecraft.client.renderer.special.SpecialModelRenderer.Unbaked<?>> codec){net.minecraft.client.renderer.special.SpecialModelRenderers.ID_MAPPER.put(id,codec);}}
