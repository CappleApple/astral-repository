package com.cappleapple.astralrepository.crafting;

import java.util.*;

/** Inventory-aware shortage preview. Runs on immutable snapshots alongside the dependency search. */
public final class MissingIngredients<K> {
    public record Missing<K>(List<K> alternatives,String tag,long count) {
        public Missing {alternatives=List.copyOf(alternatives);}
    }
    private final Map<K,List<CraftRecipe<K>>> recipes=new HashMap<>();
    private int budget;
    public MissingIngredients(Collection<CraftRecipe<K>> recipes){for(var r:recipes)this.recipes.computeIfAbsent(r.output(),k->new ArrayList<>()).add(r);}
    public synchronized List<Missing<K>> find(K target,long count,Map<K,Long> stock,Set<String> processes){
        budget=20000;var state=new State(stock);produce(target,count,state,new HashSet<>(),processes,false);
        return state.missing.entrySet().stream().map(e->new Missing<>(e.getKey().alternatives(),e.getKey().tag(),e.getValue())).toList();
    }
    private record Group<K>(List<K> alternatives,String tag){}
    private final class State {
        final Map<K,Long> stock;final Map<Group<K>,Long> missing=new LinkedHashMap<>();
        State(Map<K,Long> stock){this.stock=new HashMap<>(stock);}
        State(State other){this(other.stock);missing.putAll(other.missing);}
        long take(K key,long count){long n=Math.min(count,stock.getOrDefault(key,0L));stock.merge(key,-n,Long::sum);return count-n;}
        long score(){return missing.values().stream().mapToLong(Long::longValue).sum();}
    }
    private boolean produce(K key,long count,State state,Set<K> path,Set<String> processes,boolean consume){
        if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
        if(consume)count=state.take(key,count);
        if(count==0)return true;
        if(--budget<0||path.size()>128||path.contains(key))return false;
        var next=new HashSet<>(path);next.add(key);State best=null;
        for(var recipe:recipes.getOrDefault(key,List.of())){
            if(!processes.contains(recipe.process()))continue;
            long operations=(count-1)/recipe.outputCount()+1;if(operations>8192)continue;
            var branch=new State(state);
            for(var ingredient:recipe.ingredients()){
                long left=Math.multiplyExact(operations,ingredient.count());
                for(K alt:ingredient.alternatives()){left=branch.take(alt,left);if(left==0)break;}
                if(left==0)continue;
                State bestAlternative=null;
                for(K alt:ingredient.alternatives()){
                    var candidate=new State(branch);
                    if(produce(alt,left,candidate,next,processes,false)&&(bestAlternative==null||candidate.score()<bestAlternative.score()))bestAlternative=candidate;
                }
                boolean usesStock=false;
                if(bestAlternative!=null)for(var entry:branch.stock.entrySet())if(entry.getValue()>bestAlternative.stock.getOrDefault(entry.getKey(),0L)){usesStock=true;break;}
                if(bestAlternative!=null&&(ingredient.tag().isEmpty()||bestAlternative.score()==branch.score()||usesStock))branch=bestAlternative;
                else branch.missing.merge(new Group<>(ingredient.alternatives(),ingredient.tag()),left,Math::addExact);
            }
            branch.stock.merge(key,operations*recipe.outputCount()-count,Long::sum);
            if(best==null||branch.score()<best.score())best=branch;
            if(best.score()==state.score())break;
        }
        if(best==null)return false;
        state.stock.clear();state.stock.putAll(best.stock);state.missing.clear();state.missing.putAll(best.missing);return true;
    }
}
