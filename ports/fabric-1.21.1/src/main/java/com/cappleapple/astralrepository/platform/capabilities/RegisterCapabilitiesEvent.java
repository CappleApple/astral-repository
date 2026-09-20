package com.cappleapple.astralrepository.platform.capabilities;
import java.util.function.BiFunction;import net.minecraft.world.level.block.entity.*;
public final class RegisterCapabilitiesEvent {public <T,C,B extends BlockEntity> void registerBlockEntity(BlockCapability<T,C> capability,BlockEntityType<B> type,BiFunction<B,C,T> factory){capability.register(type,factory);}}
