package com.cappleapple.astralrepository.content;

import com.cappleapple.astralrepository.network.AnchorAddress;
import com.cappleapple.astralrepository.network.SpatialHash;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** One visible rune, with independently persisted transfer assignments. Server-thread mutations only. */
public final class RuneLayer {
    public enum Mode { PUSH, PULL, FILTER;
        public Mode cycle(int step){return values()[Math.floorMod(ordinal()+step,values().length)];}
        public String title(){return switch(this){case PUSH->"Push";case PULL->"Pull";case FILTER->"Filter";};}
    }
    public record Target(GlobalPos position, Direction face) {
        public Target { Objects.requireNonNull(position); position=GlobalPos.of(position.dimension(),position.pos().immutable()); }
        @Override public int hashCode(){return 31*SpatialHash.POSITIONS.hashCode(position)+(face==null?0:face.ordinal()+1);}
    }
    private final RuneSurface surface;
    private final UUID id;
    private ResourceLocation item;
    private Mode mode;
    private RuneDesign design;
    private double u=Double.NaN,v=Double.NaN;
    public RuneDesign design(){return design==null?RuneDesign.initial(mode):design;}
    public double u(){return u;} public double v(){return v;}
    public void position(double x,double y){if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("Invalid placement");u=Math.clamp(x,-1,1);v=Math.clamp(y,-1,1);changed();}
    public void preset(RunePreset p,HolderLookup.Provider registries){mode=p.mode();design=p.design();filter.load(p.filter(),registries);priority=p.priority();enabled=p.enabled();cadence=p.cadence();changed();}

    public static final int MAX_TARGETS=32;
    private final List<Target> targets=new ArrayList<>();
    private int targetCursor;
    private final FilterRules filter=new FilterRules();
    private boolean enabled=true;
    private int priority;
    private RuneCadence cadence=RuneCadence.DEFAULT;
    public RuneCadence cadence(){return cadence;}
    public void setCadence(RuneCadence value){cadence=Objects.requireNonNull(value);changed();}
    private long transferredItems,transferredFluid;
    private String status="Unlinked",pendingStatus;
    RuneLayer(RuneSurface surface,UUID id,ResourceLocation item,Mode mode){this.surface=surface;this.id=id;this.item=item;this.mode=mode;this.design=RuneDesign.initial(mode);}
    public UUID id(){return id;}
    public ResourceLocation item(){return item;}
    public Mode mode(){return mode;}
    public Target target(){return targets.isEmpty()?null:targets.getFirst();}
    public List<Target> targets(){return List.copyOf(targets);}
    public Target nextTarget(){return targets.isEmpty()?null:targets.get(Math.floorMod(targetCursor++,targets.size()));}
    void toggleTarget(Target value){if(!targets.remove(value)&&targets.size()<MAX_TARGETS)targets.add(value);status=targets.isEmpty()?"Unlinked":"Waiting";changed();}
    public FilterRules filter(){return filter;}
    public int priority(){return priority;}
    public boolean enabled(){return enabled;}
    public String status(){if(mode==Mode.FILTER)return enabled?"Filtering":"Paused";return !enabled?status:targets.isEmpty()?"Unlinked":status;}
    public long transferredItems(){return transferredItems;}
    public long transferredFluid(){return transferredFluid;}
    public RuneSurface surface(){return surface;}
    public void setMode(Mode value){mode=Objects.requireNonNull(value);item=RuneGlyph.id(value);changed();}
    public void setPriority(int value){priority=Math.clamp(value,-999,999);changed();}
    public void setEnabled(boolean value){enabled=value;status=value?"Waiting":"Paused";changed();}
    public void clearFilter(){filter.clearPredicates();changed();}
    /** Call after editing the mutable filter so persistence and observers see the change. */
    public void changed(){surface.changed();}
    void target(Target value){targets.clear();if(value!=null)targets.add(value);status=value==null?"Unlinked":"Waiting";changed();}
    public void beginReport(){pendingStatus=status;}
    public void endReport(){String value=pendingStatus;pendingStatus=null;if(value!=null)report(value);}
    public void report(String value){if(pendingStatus!=null){pendingStatus=value;return;}if(!status.equals(value)){status=value;surface.statusChanged();}}
    public void transferred(long items,long fluid){transferredItems=saturatingAdd(transferredItems,items);transferredFluid=saturatingAdd(transferredFluid,fluid);if(enabled)report("Transferring");surface.changed();}
    private static long saturatingAdd(long a,long b){return b>Long.MAX_VALUE-a?Long.MAX_VALUE:a+Math.max(0,b);}
    CompoundTag save(HolderLookup.Provider registries){
        CompoundTag tag=new CompoundTag();tag.putUUID("Id",id);tag.putString("Item",item.toString());tag.putString("Mode",mode.name());tag.putBoolean("Enabled",enabled);tag.putInt("Priority",priority);
        tag.put("Cadence",cadence.save());tag.put("Design",design().save());if(Double.isFinite(u)&&Double.isFinite(v)){tag.putDouble("U",u);tag.putDouble("V",v);}tag.put("Filter",filter.save(registries));tag.putLong("TransferredItems",transferredItems);tag.putLong("TransferredFluid",transferredFluid);
        net.minecraft.nbt.ListTag saved=new net.minecraft.nbt.ListTag();for(var target:targets)saved.add(new AnchorAddress(target.position(),target.face()).save());tag.put("Targets",saved);return tag;
    }
    static RuneLayer load(RuneSurface surface,CompoundTag tag,HolderLookup.Provider registries){
        Mode mode=Mode.valueOf(tag.getString("Mode"));RuneLayer result=new RuneLayer(surface,tag.hasUUID("Id")?tag.getUUID("Id"):UUID.randomUUID(),ResourceLocation.parse(tag.getString("Item")),mode);
        if(tag.contains("Design"))result.design=RuneDesign.load(tag.getCompound("Design"));if(tag.contains("U")&&tag.contains("V")){result.u=tag.getDouble("U");result.v=tag.getDouble("V");}
        result.cadence=RuneCadence.load(tag.getCompound("Cadence"));result.enabled=!tag.contains("Enabled")||tag.getBoolean("Enabled");result.priority=Math.clamp(tag.getInt("Priority"),-999,999);result.filter.load(tag.getCompound("Filter"),registries);
        result.transferredItems=Math.max(0,tag.getLong("TransferredItems"));result.transferredFluid=Math.max(0,tag.getLong("TransferredFluid"));
        if(tag.contains("Targets")){var saved=tag.getList("Targets",10);for(int i=0;i<Math.min(MAX_TARGETS,saved.size());i++){var a=AnchorAddress.load(saved.getCompound(i));var t=new Target(a.position(),a.face());if(!result.targets.contains(t))result.targets.add(t);}}
        else if(tag.contains("Target")){AnchorAddress address=AnchorAddress.load(tag.getCompound("Target"));result.targets.add(new Target(address.position(),address.face()));}
        result.status=!result.enabled?"Paused":result.targets.isEmpty()?"Unlinked":"Waiting";return result;
    }
}
