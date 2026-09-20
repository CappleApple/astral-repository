package com.cappleapple.astralrepository.api;

import java.util.*;

/** Pure operating-cost arithmetic and provider selection, separate from resource mutation. */
public final class PowerPolicy {
    private PowerPolicy() { }
    public enum Component {
        BASE, NODES, STORAGE_PROVIDERS, INDEXED_CAPACITY, TRANSFERS, ITEMS, FLUID, ENERGY,
        SOURCE, CRAFTING_JOBS, PROCESSORS, REMOTE_USES, DISTANCE, DIMENSIONAL_LINKS, COMPLEXITY
    }
    public record Source(Object identity,String resource,double available) {
        public Source {
            Objects.requireNonNull(identity); Objects.requireNonNull(resource);
            if(!Double.isFinite(available) || available<0) throw new IllegalArgumentException("Invalid available power");
        }
    }
    /** Each quantity is multiplied by its configured rate. Missing components have zero cost. */
    public static double cost(Map<Component,Double> quantities,Map<Component,Double> rates) {
        double result=0;
        for(var entry:quantities.entrySet()) {
            double quantity=entry.getValue(),rate=rates.getOrDefault(entry.getKey(),0D);
            if(!Double.isFinite(quantity)||!Double.isFinite(rate)||quantity<0||rate<0)
                throw new IllegalArgumentException("Power quantities and rates must be finite and nonnegative");
            double term=quantity*rate;
            result=term>Double.MAX_VALUE-result?Double.MAX_VALUE:result+term;
        }
        return result;
    }
    /** Same physical backend can contribute several resource kinds, but never the same kind twice. */
    public static Map<String,Double> available(Collection<Source> sources) {
        Map<String,Double> totals=new LinkedHashMap<>();
        Set<List<Object>> seen=new HashSet<>();
        for(Source source:sources) if(seen.add(List.of(source.identity,source.resource)))
            totals.merge(source.resource,source.available,(a,b) -> b>Double.MAX_VALUE-a?Double.MAX_VALUE:a+b);
        return Map.copyOf(totals);
    }
    /** In OR mode use the first sufficient allowed type. In AND mode require every allowed type. */
    public static Optional<List<String>> choose(List<String> allowed,boolean all,double required,Map<String,Double> available) {
        if(!Double.isFinite(required)||required<0) throw new IllegalArgumentException("Invalid required power");
        if(required==0) return Optional.of(List.of());
        List<String> chosen=new ArrayList<>();
        for(String kind:new LinkedHashSet<>(allowed)) {
            double amount=available.getOrDefault(kind,0D);
            if(!Double.isFinite(amount)||amount<0) throw new IllegalArgumentException("Invalid available power");
            if(amount>=required) { chosen.add(kind); if(!all) break; }
            else if(all) return Optional.empty();
        }
        return chosen.isEmpty()?Optional.empty():Optional.of(List.copyOf(chosen));
    }
}