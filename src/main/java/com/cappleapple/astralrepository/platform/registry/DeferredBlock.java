package com.cappleapple.astralrepository.platform.registry;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
public final class DeferredBlock<T extends Block> extends DeferredHolder<Block,T> {
    public DeferredBlock(Identifier id,Supplier<T> factory){super(id,factory);}
}
