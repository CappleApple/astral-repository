package com.cappleapple.astralrepository.network;
import java.util.*;
import java.util.function.*;
/** Dijkstra search with weighted entry and exit legs; nonnegative finite costs only. */
public final class ShortestPath {
    private record Visit<T>(T node,double cost,long order){}
    public static <T> List<T> find(Map<T,Double> starts,ToDoubleFunction<T> exit,Function<T,? extends Collection<T>> neighbors,ToDoubleBiFunction<T,T> cost){
        Map<T,Double> distance=new HashMap<>();Map<T,T> previous=new HashMap<>();long order=0;
        PriorityQueue<Visit<T>> queue=new PriorityQueue<>(Comparator.<Visit<T>>comparingDouble(Visit::cost).thenComparingLong(Visit::order));
        for(var e:starts.entrySet())if(Double.isFinite(e.getValue())&&e.getValue()>=0){distance.put(e.getKey(),e.getValue());previous.put(e.getKey(),null);queue.add(new Visit<>(e.getKey(),e.getValue(),order++));}
        T end=null;double best=Double.POSITIVE_INFINITY;
        while(!queue.isEmpty()){
            var v=queue.remove();if(v.cost()!=distance.get(v.node()))continue;if(v.cost()>=best)break;
            double finish=exit.applyAsDouble(v.node());if(finish>=0&&v.cost()+finish<best){best=v.cost()+finish;end=v.node();}
            for(T n:neighbors.apply(v.node())){double edge=cost.applyAsDouble(v.node(),n);if(!Double.isFinite(edge)||edge<0)continue;double next=v.cost()+edge;
                if(next<distance.getOrDefault(n,Double.POSITIVE_INFINITY)){distance.put(n,next);previous.put(n,v.node());queue.add(new Visit<>(n,next,order++));}
            }
        }
        if(end==null)return List.of();LinkedList<T> path=new LinkedList<>();for(T n=end;n!=null;n=previous.get(n))path.addFirst(n);return List.copyOf(path);
    }
    private record Step<T>(T previous,double distance,long order){}
    private record Branch<T>(Tree<T> tree,double entry){}
    private record Selection<T>(Tree<T> tree,T end,double distance,long order){}
    /** Immutable source tree, optionally sharing prepared relay trees for several weighted entry legs. */
    public static final class Tree<T> {
        private final Map<T,Step<T>> steps;
        private final List<Branch<T>> branches;
        private final int size;
        private Tree(Map<T,Step<T>> steps){this.steps=Map.copyOf(steps);this.branches=List.of();this.size=steps.size();}
        private Tree(List<Branch<T>> branches){
            this.steps=Map.of();this.branches=List.copyOf(branches);
            // Count shared state conservatively so the origin cache cannot retain unbounded forests.
            long states=0;for(var branch:branches)states+=branch.tree.size();this.size=(int)Math.min(Integer.MAX_VALUE,states);
        }
        public int size(){return size;}
        public Set<T> nodes(){
            if(branches.isEmpty())return steps.keySet();
            Set<T> nodes=new HashSet<>();for(var branch:branches)nodes.addAll(branch.tree.nodes());return Set.copyOf(nodes);
        }
        private Selection<T> select(Collection<T> candidates,ToDoubleFunction<T> exit){
            Selection<T> selected=null;
            if(!branches.isEmpty()){
                for(var branch:branches){
                    var result=branch.tree.select(candidates,exit);if(result==null)continue;
                    double distance=result.distance+branch.entry;if(!Double.isFinite(distance))continue;
                    if(selected==null||distance<selected.distance)selected=new Selection<>(result.tree,result.end,distance,result.order);
                }
                return selected;
            }
            for(T candidate:candidates){
                Step<T> step=steps.get(candidate);if(step==null)continue;
                double finish=exit.applyAsDouble(candidate);if(!Double.isFinite(finish)||finish<0)continue;
                double total=step.distance()+finish;
                if(Double.isFinite(total)&&(selected==null||total<selected.distance||total==selected.distance&&step.order()<selected.order))
                    selected=new Selection<>(this,candidate,total,step.order());
            }
            return selected;
        }
        public List<T> path(Collection<T> candidates,ToDoubleFunction<T> exit){
            var selected=select(candidates,exit);if(selected==null)return List.of();
            LinkedList<T> path=new LinkedList<>();
            for(T node=selected.end;node!=null;node=selected.tree.steps.get(node).previous())path.addFirst(node);
            return List.copyOf(path);
        }
    }
    /** Shares immutable single-relay trees without walking their graphs or copying their predecessor maps. */
    public static <T> Tree<T> combine(Map<T,Double> starts,Map<T,Tree<T>> trees){
        List<Branch<T>> branches=new ArrayList<>();
        for(var entry:starts.entrySet())if(Double.isFinite(entry.getValue())&&entry.getValue()>=0){
            var tree=trees.get(entry.getKey());if(tree==null)throw new IllegalArgumentException("Missing prepared start tree");
            branches.add(new Branch<>(tree,entry.getValue()));
        }
        return new Tree<>(branches);
    }
    public static <T> Tree<T> tree(Map<T,Double> starts,Function<T,? extends Collection<T>> neighbors,ToDoubleBiFunction<T,T> cost){
        Map<T,Double> distance=new HashMap<>();Map<T,T> previous=new HashMap<>();Map<T,Step<T>> settled=new HashMap<>();long order=0;
        PriorityQueue<Visit<T>> queue=new PriorityQueue<>(Comparator.<Visit<T>>comparingDouble(Visit::cost).thenComparingLong(Visit::order));
        for(var entry:starts.entrySet())if(Double.isFinite(entry.getValue())&&entry.getValue()>=0){
            distance.put(entry.getKey(),entry.getValue());previous.put(entry.getKey(),null);queue.add(new Visit<>(entry.getKey(),entry.getValue(),order++));
        }
        while(!queue.isEmpty()){
            var visit=queue.remove();if(visit.cost()!=distance.get(visit.node())||settled.containsKey(visit.node()))continue;
            settled.put(visit.node(),new Step<>(previous.get(visit.node()),visit.cost(),settled.size()));
            for(T neighbor:neighbors.apply(visit.node())){
                double edge=cost.applyAsDouble(visit.node(),neighbor);if(!Double.isFinite(edge)||edge<0)continue;
                double next=visit.cost()+edge;
                if(next<distance.getOrDefault(neighbor,Double.POSITIVE_INFINITY)){
                    distance.put(neighbor,next);previous.put(neighbor,visit.node());queue.add(new Visit<>(neighbor,next,order++));
                }
            }
        }
        return new Tree<>(settled);
    }
    private ShortestPath(){}
}
