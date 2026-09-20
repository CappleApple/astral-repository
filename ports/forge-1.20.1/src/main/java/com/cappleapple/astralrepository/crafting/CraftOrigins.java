package com.cappleapple.astralrepository.crafting;
import java.util.*;
/** Cosmetic locations follow escrow quantities without owning or changing those quantities. */
public final class CraftOrigins<K,P> {
    private final Map<K,LinkedHashMap<P,Long>> origins=new HashMap<>();
    public void add(K key,long count,P at){if(count>0)origins.computeIfAbsent(key,k->new LinkedHashMap<>()).merge(at,count,Math::addExact);}
    public Map<P,Long> take(K key,long count,P fallback){
        Map<P,Long> result=new LinkedHashMap<>();var positions=origins.get(key);
        if(positions!=null){var it=positions.entrySet().iterator();while(it.hasNext()&&count>0){var e=it.next();long n=Math.min(count,e.getValue());result.put(e.getKey(),n);count-=n;if(n==e.getValue())it.remove();else e.setValue(e.getValue()-n);}if(positions.isEmpty())origins.remove(key);}
        if(count>0)result.merge(fallback,count,Math::addExact);return result;
    }
    public Set<P> positions(K key){var map=origins.get(key);return map==null?Set.of():Set.copyOf(map.keySet());}
}
