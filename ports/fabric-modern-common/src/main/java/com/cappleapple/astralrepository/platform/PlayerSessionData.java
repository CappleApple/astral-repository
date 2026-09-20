package com.cappleapple.astralrepository.platform;
/** Server-only editor authorization, scoped to the exact connected player instance. */
public final class PlayerSessionData {private static final java.util.Map<net.minecraft.server.level.ServerPlayer,net.minecraft.nbt.CompoundTag> SESSIONS=new java.util.WeakHashMap<>();public static net.minecraft.nbt.CompoundTag get(net.minecraft.server.level.ServerPlayer player){return SESSIONS.computeIfAbsent(player,ignored->new net.minecraft.nbt.CompoundTag());}public static void clear(){SESSIONS.clear();}}
