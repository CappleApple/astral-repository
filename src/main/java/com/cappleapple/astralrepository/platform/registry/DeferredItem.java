package com.cappleapple.astralrepository.platform.registry;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
public final class DeferredItem<T extends Item> extends DeferredHolder<Item,T> {
    public DeferredItem(Identifier id,Supplier<T> factory){super(id,factory);}
}
