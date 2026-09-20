package com.cappleapple.astralrepository.network;

import java.util.LinkedHashMap;
import java.util.List;

/** Server-thread route cache bounded by both route count and retained path points. */
final class BoundedPathCache<K,P> {
    private final int maxEntries,maxPoints;
    private final LinkedHashMap<K,List<P>> paths=new LinkedHashMap<>(128,.75f,true);
    private int points;
    BoundedPathCache(int maxEntries,int maxPoints){
        if(maxEntries<1||maxPoints<1)throw new IllegalArgumentException("Positive cache limits required");
        this.maxEntries=maxEntries;this.maxPoints=maxPoints;
    }
    List<P> get(K key){return paths.get(key);}
    void put(K key,List<P> path){
        var old=paths.remove(key);if(old!=null)points-=weight(old);
        int weight=weight(path);if(weight>maxPoints)return;
        paths.put(key,List.copyOf(path));points+=weight;
        while(paths.size()>maxEntries||points>maxPoints){
            var eldest=com.cappleapple.astralrepository.platform.Backport.pollFirst(paths);points-=weight(eldest.getValue());
        }
    }
    void clear(){paths.clear();points=0;}
    int size(){return paths.size();}
    int points(){return points;}
    private static int weight(List<?> path){return Math.max(1,path.size());}
}
