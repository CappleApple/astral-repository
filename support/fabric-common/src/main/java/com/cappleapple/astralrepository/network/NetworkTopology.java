package com.cappleapple.astralrepository.network;
import java.util.*;

/** Pure component construction from supplied edges; the manager supplies automatic and explicit links after sight checks. */
public final class NetworkTopology {
    public record Node(String id,String dimension,int x,int y,int z,int channel,boolean remote,boolean attuned){}
    public record Edge(String first,String second){}
    public static boolean allowed(Node a,Node b,int range,int remoteRange){
        if(a.channel!=b.channel)return false;
        if(!a.dimension.equals(b.dimension))return a.remote&&b.remote&&a.attuned&&b.attuned;
        long dx=(long)a.x-b.x,dy=(long)a.y-b.y,dz=(long)a.z-b.z;long distance=dx*dx+dy*dy+dz*dz;
        return distance<=(long)range*range||a.remote&&b.remote&&distance<=(long)remoteRange*remoteRange;
    }
    public static List<Set<String>> components(Collection<Node> nodes,Collection<Edge> edges,int range,int remoteRange){
        Map<String,Node> byId=new HashMap<>();Map<String,Set<String>> adjacent=new HashMap<>();for(Node node:nodes)byId.put(node.id,node);
        for(Edge edge:edges){Node a=byId.get(edge.first),b=byId.get(edge.second);if(a==null||b==null||!allowed(a,b,range,remoteRange))continue;adjacent.computeIfAbsent(a.id,k->new LinkedHashSet<>()).add(b.id);adjacent.computeIfAbsent(b.id,k->new LinkedHashSet<>()).add(a.id);}
        Set<String> visited=new HashSet<>();List<Set<String>> result=new ArrayList<>();
        for(Node start:nodes){if(!visited.add(start.id))continue;Set<String> component=new LinkedHashSet<>();ArrayDeque<String> queue=new ArrayDeque<>();queue.add(start.id);while(!queue.isEmpty()){String current=queue.remove();component.add(current);for(String next:adjacent.getOrDefault(current,Set.of()))if(visited.add(next))queue.add(next);}result.add(component);}return result;
    }
    private NetworkTopology(){}
}