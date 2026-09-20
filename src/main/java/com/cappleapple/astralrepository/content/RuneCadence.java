package com.cappleapple.astralrepository.content;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.AstralServerConfig;
import com.cappleapple.astralrepository.api.ResourceKinds;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/** Saved preferences survive changing server limits; every effective rate applies the current bounds. */
public record RuneCadence(Map<Kind,Rate> overrides) {
    public static final RuneCadence DEFAULT=new RuneCadence(Map.of());
    public enum Kind {
        ITEMS(ResourceKinds.ITEM,"Items"), FLUID(ResourceKinds.FLUID,"Fluid (mB)"),
        ENERGY(ResourceKinds.ENERGY,"Energy (FE)"), SOURCE(ResourceKinds.SOURCE,"Source");
        public final Identifier resource; public final String title;
        Kind(Identifier resource,String title){this.resource=resource;this.title=title;}
        public int bit(){return 1<<ordinal();}
        public int defaultAmount(){return switch(this){case ITEMS->AstralConfig.transferRate.get();case FLUID->AstralConfig.fluidTransferRate.get();case ENERGY->AstralConfig.energyTransferRate.get();case SOURCE->AstralConfig.sourceTransferRate.get();};}
        public int maximum(){return switch(this){case ITEMS->AstralServerConfig.maxItemTransfer.get();case FLUID->AstralServerConfig.maxFluidTransfer.get();case ENERGY->AstralServerConfig.maxEnergyTransfer.get();case SOURCE->AstralServerConfig.maxSourceTransfer.get();};}
        public int minimumTicks(){return switch(this){case ITEMS->AstralServerConfig.minItemTransferTicks.get();case FLUID->AstralServerConfig.minFluidTransferTicks.get();case ENERGY->AstralServerConfig.minEnergyTransferTicks.get();case SOURCE->AstralServerConfig.minSourceTransferTicks.get();};}
        public Rate constrain(Rate rate){return new Rate(Math.min(rate.amount(),maximum()),Math.max(rate.ticks(),minimumTicks()));}
        public boolean permits(Rate rate){return rate.amount()<=maximum()&&rate.ticks()>=minimumTicks();}
        public static Kind of(Identifier resource){for(var kind:values())if(kind.resource.equals(resource))return kind;throw new IllegalArgumentException("Unsupported resource kind: "+resource);}
    }
    public record Rate(int amount,int ticks){public Rate{if(amount<0||ticks<1)throw new IllegalArgumentException("Invalid cadence");}}
    public RuneCadence{overrides=Map.copyOf(overrides);}
    public Rate rate(Kind kind){return kind.constrain(overrides.getOrDefault(kind,new Rate(kind.defaultAmount(),AstralConfig.instantAutomaticLogistics.get()?1:AstralConfig.runeTransferInterval.get())));}
    public RuneCadence with(Kind kind,Rate rate){var copy=new EnumMap<Kind,Rate>(Kind.class);copy.putAll(overrides);if(rate==null)copy.remove(kind);else copy.put(kind,rate);return new RuneCadence(copy);}
    public RuneCadence resolved(){var rates=new EnumMap<Kind,Rate>(Kind.class);for(var kind:Kind.values())rates.put(kind,rate(kind));return new RuneCadence(rates);}
    public CompoundTag save(){var tag=new CompoundTag();overrides.forEach((kind,rate)->{var value=new CompoundTag();value.putInt("Amount",rate.amount());value.putInt("Ticks",rate.ticks());tag.put(kind.name(),value);});return tag;}
    public static RuneCadence load(CompoundTag tag){var rates=new EnumMap<Kind,Rate>(Kind.class);for(var kind:Kind.values())if(tag.contains(kind.name())){var v=tag.getCompoundOrEmpty(kind.name());rates.put(kind,new Rate(v.getIntOr("Amount",0),v.getIntOr("Ticks",0)));}return new RuneCadence(rates);}
}
