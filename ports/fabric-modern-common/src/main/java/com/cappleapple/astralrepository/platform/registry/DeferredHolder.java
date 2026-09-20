package com.cappleapple.astralrepository.platform.registry;

import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

/** A lazy handle initialized by the loader's common entry point. */
public class DeferredHolder<R,T extends R> implements Supplier<T> {
    private final Identifier id;
    private final Supplier<T> factory;
    private T value;
    public DeferredHolder(Identifier id,Supplier<T> factory){this.id=id;this.factory=factory;}
    public Identifier getId(){return id;}
    @Override public T get(){if(value==null)value=factory.get();return value;}
}
