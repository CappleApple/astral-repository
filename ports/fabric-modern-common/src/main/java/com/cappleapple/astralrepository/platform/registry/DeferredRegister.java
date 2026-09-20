package com.cappleapple.astralrepository.platform.registry;

import java.util.*;
import java.util.function.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Registers the same content ids through vanilla registries on Fabric. */
public class DeferredRegister<T> {
    protected final ResourceKey<? extends Registry<T>> registryKey;
    protected final String namespace;
    protected final List<DeferredHolder<T,? extends T>> entries=new ArrayList<>();
    protected DeferredRegister(ResourceKey<? extends Registry<T>> registryKey,String namespace){this.registryKey=registryKey;this.namespace=namespace;}
    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> key,String namespace){return new DeferredRegister<>(key,namespace);}
    public static Items createItems(String namespace){return new Items(namespace);}
    public static Blocks createBlocks(String namespace){return new Blocks(namespace);}
    public <V extends T> DeferredHolder<T,V> register(String name,Supplier<V> factory){var handle=new DeferredHolder<T,V>(Identifier.fromNamespaceAndPath(namespace,name),factory);entries.add(handle);return handle;}
    public Collection<DeferredHolder<T,? extends T>> getEntries(){return Collections.unmodifiableList(entries);}
    @SuppressWarnings("unchecked") public void register(){var registry=(Registry<T>)BuiltInRegistries.REGISTRY.getValue(registryKey.identifier());for(var entry:entries)Registry.register(registry,entry.getId(),entry.get());}
    public static final class Items extends DeferredRegister<Item> {
        private Items(String namespace){super(Registries.ITEM,namespace);}
        public <V extends Item> DeferredItem<V> registerItem(String name,Function<Item.Properties,V> factory){var id=Identifier.fromNamespaceAndPath(namespace,name);var handle=new DeferredItem<V>(id,()->factory.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM,id))));entries.add(handle);return handle;}
        public DeferredItem<Item> registerSimpleItem(String name){return registerItem(name,Item::new);}
        public void addAlias(Identifier oldId,Identifier current){RegistryAliases.add(Registries.ITEM.identifier(),oldId,current);}
    }
    public static final class Blocks extends DeferredRegister<Block> {
        private Blocks(String namespace){super(Registries.BLOCK,namespace);}
        public <V extends Block> DeferredBlock<V> registerBlock(String name,Function<BlockBehaviour.Properties,V> factory,Supplier<BlockBehaviour.Properties> properties){var id=Identifier.fromNamespaceAndPath(namespace,name);var handle=new DeferredBlock<V>(id,()->factory.apply(properties.get().setId(ResourceKey.create(Registries.BLOCK,id))));entries.add(handle);return handle;}
    }
}
