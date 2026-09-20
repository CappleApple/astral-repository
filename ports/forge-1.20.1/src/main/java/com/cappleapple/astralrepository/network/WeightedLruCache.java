package com.cappleapple.astralrepository.network;

import java.util.LinkedHashMap;
import java.util.function.ToIntFunction;

/** Server-thread cache with independent entry and retained-state limits. Values must remain immutable. */
final class WeightedLruCache<K,V> {
    private final int maxEntries,maxWeight;
    private final ToIntFunction<V> weigh;
    private final LinkedHashMap<K,V> entries=new LinkedHashMap<>(128,.75f,true);
    private int weight;
    WeightedLruCache(int maxEntries,int maxWeight,ToIntFunction<V> weigh){
        if(maxEntries<1||maxWeight<1)throw new IllegalArgumentException("Positive cache limits required");
        this.maxEntries=maxEntries;this.maxWeight=maxWeight;this.weigh=weigh;
    }
    V get(K key){return entries.get(key);}
    void put(K key,V value){
        remove(key);int added=weight(value);if(added>maxWeight)return;
        entries.put(key,value);weight+=added;
        while(entries.size()>maxEntries||weight>maxWeight)weight-=weight(com.cappleapple.astralrepository.platform.Backport.pollFirst(entries).getValue());
    }
    void remove(K key){var old=entries.remove(key);if(old!=null)weight-=weight(old);}
    void clear(){entries.clear();weight=0;}
    int size(){return entries.size();}
    int weight(){return weight;}
    private int weight(V value){return Math.max(1,weigh.applyAsInt(value));}
}
