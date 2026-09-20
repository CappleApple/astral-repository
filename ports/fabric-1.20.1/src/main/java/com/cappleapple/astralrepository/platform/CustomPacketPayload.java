package com.cappleapple.astralrepository.platform;
public interface CustomPacketPayload { Type<? extends CustomPacketPayload> type(); record Type<T extends CustomPacketPayload>(net.minecraft.resources.ResourceLocation id) {} }
