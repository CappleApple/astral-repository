package com.cappleapple.astralrepository.content;

import com.cappleapple.astralrepository.network.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A face-mounted group of independent Push/Pull layers; graph links expose its host inventory. */
public final class RuneSurface implements NetworkAnchor {
    public static final int MAX_LAYERS=64;
    public static int placementLimit(){return com.cappleapple.astralrepository.AstralServerConfig.maxRunesPerFace.get();}
    private final MinecraftServer server;
    private final AnchorAddress address;
    private final List<RuneLayer> layers=new ArrayList<>();
    private final List<RuneLayer> layerView=Collections.unmodifiableList(layers);
    private final List<RuneLayer> archivedLayers=new ArrayList<>();
    // Retained only for old saved-data readers; transfer rules now belong to individual layers.
    private final FilterRules insertion=new FilterRules(),extraction=new FilterRules(),networkAccess=new FilterRules();
    private final CompoundTag persistent=new CompoundTag();
    private BlockEntity host;
    private boolean removed,dimensional,editingExtraction,excludeNext;
    private boolean enabled=true;
    private int channel=-1,priority;
    private DistributionMode distribution=DistributionMode.PRIORITY;
    public RuneSurface(MinecraftServer server,AnchorAddress address){this.server=server;this.address=address;if(address.face()==null)throw new IllegalArgumentException("Rune needs a face");}
    public AnchorAddress address(){return address;}
    public ServerLevel getLevel(){return server.getLevel(address.position().dimension());}
    public BlockPos getBlockPos(){return address.position().pos();}
    public Direction facing(){return address.face();}
    public BlockPos providerPosition(){return getBlockPos();}
    public Direction providerSide(){return facing();}
    public boolean nearbyCoverage(){return false;}
    public boolean hostPresent(){
        ServerLevel level=getLevel();if(level==null)return false;
        var chunk=level.getChunkSource().getChunkNow(getBlockPos().getX()>>4,getBlockPos().getZ()>>4);if(chunk==null)return false;
        BlockEntity current=chunk.getBlockEntity(getBlockPos());
        if(current==null||current.isRemoved()||chunk.getBlockState(getBlockPos()).isAir())return false;
        if(host==null)host=current;return host==current;
    }
    public boolean isRemoved(){return removed||!hostPresent();}
    public void markRemoved(){removed=true;}
    public void unloaded(){host=null;}
    /** Server-thread read-only view; callers needing a snapshot must copy explicitly. */
    public List<RuneLayer> layers(){return layerView;}
    public int archivedLayerCount(){return archivedLayers.size();}
    public RuneLayer get(UUID id){for(RuneLayer layer:layers)if(layer.id().equals(id))return layer;return null;}
    public RuneLayer addLayer(Identifier item,RuneLayer.Mode mode){if(layers.size()>=placementLimit()||layers.size()+archivedLayers.size()>=MAX_LAYERS)return null;RuneLayer layer=new RuneLayer(this,UUID.randomUUID(),Objects.requireNonNull(item),Objects.requireNonNull(mode));layers.add(layer);topologyChanged();return layer;}
    public boolean removeLayer(UUID id){boolean removed=layers.removeIf(layer->layer.id().equals(id));if(removed){restoreArchivedLayer();topologyChanged();}return removed;}
    private void restoreArchivedLayer(){if(layers.size()<placementLimit()&&!archivedLayers.isEmpty()){RuneLayer restored=archivedLayers.removeFirst();layers.add(restored);restored.setEnabled(false);restored.report("Preserved legacy rune restored; paused");}}
    public record TargetResult(boolean success,boolean assigned,String message){}
    public TargetResult toggleTarget(UUID id,GlobalPos position,Direction face){
        RuneLayer layer=get(id);if(layer!=null&&layer.mode()==RuneLayer.Mode.FILTER)return new TargetResult(false,false,"Filter runes do not need targets.");if(layer==null)return new TargetResult(false,false,"That rune layer no longer exists.");
        RuneLayer.Target target=new RuneLayer.Target(position,face);
        if(layer.targets().contains(target)){layer.toggleTarget(target);return new TargetResult(true,false,"Target cleared.");}
        if(layer.targets().size()>=RuneLayer.MAX_TARGETS)return new TargetResult(false,false,"Target limit reached.");
        String error=DirectRuneTransfers.targetError(this,target);
        if(error!=null)return new TargetResult(false,false,error);
        layer.toggleTarget(target);return new TargetResult(true,true,layer.mode()==RuneLayer.Mode.PULL?"Pull target assigned: linked container → rune host.":"Push target assigned: rune host → linked container.");
    }
    public boolean clearTarget(UUID id){RuneLayer layer=get(id);if(layer==null||layer.target()==null)return false;layer.target(null);return true;}
    public List<Identifier> glyphs(){return layers.stream().map(RuneLayer::item).toList();}
    /** Legacy construction remains source-compatible, but never restores automatic network routing. */
    public boolean addGlyph(Identifier glyph,NodeKind role){return addLayer(glyph,role==NodeKind.DISTRIBUTION?RuneLayer.Mode.PUSH:RuneLayer.Mode.PULL)!=null;}
    public boolean removeGlyph(Identifier glyph){for(int i=layers.size()-1;i>=0;i--)if(layers.get(i).item().equals(glyph)){return removeLayer(layers.get(i).id());}return false;}
    public boolean collects(){return false;} public boolean stocks(){return false;} public boolean distributes(){return false;}
    public NodeKind kind(){return NodeKind.ROUTING;}
    public int channel(){return channel;} public int priority(){return priority;} public boolean dimensional(){return dimensional;} public boolean enabled(){return enabled;}
    public DistributionMode distributionMode(){return distribution;} public FilterRules insertionFilter(){return insertion;} public FilterRules extractionFilter(){return extraction;}
    public FilterRules networkInsertionFilter(){return networkAccess;} public FilterRules networkExtractionFilter(){return networkAccess;}
    public FilterRules selectedFilter(){return editingExtraction?extraction:insertion;} public boolean editingExtraction(){return editingExtraction;} public boolean excludeNext(){return excludeNext;}
    public void setChannel(int value){channel=Math.clamp(value,-1,15);topologyChanged();} public void setPriority(int value){priority=Math.clamp(value,-999,999);topologyChanged();}
    public void setDimensional(boolean value){dimensional=value;topologyChanged();} public void cycleDistribution(){distribution=distribution.next();topologyChanged();}
    public void toggleDirection(){editingExtraction=!editingExtraction;changed();} public void toggleExclusion(){excludeNext=!excludeNext;changed();}
    public void toggleEnabled(){enabled=!enabled;topologyChanged();}
    public CompoundTag getPersistentData(){return persistent;}
    public void setChanged(){RuneSavedData.get(server).setDirty();}
    public void statusChanged(){ServerLevel level=getLevel();if(level!=null)RuneSurfaces.onChanged.accept(level,getBlockPos());}
    public void changed(){setChanged();statusChanged();}
    public void topologyChanged(){changed();ServerLevel level=getLevel();if(level!=null)NetworkManager.changed(level,getBlockPos());}
    public CompoundTag save(HolderLookup.Provider registries){
        CompoundTag tag=address.save();tag.putInt("Channel",channel);tag.putInt("Priority",priority);tag.putBoolean("Dimensional",dimensional);tag.putBoolean("Enabled",enabled);
        tag.putBoolean("EditingExtraction",editingExtraction);tag.putBoolean("ExcludeNext",excludeNext);tag.putString("Distribution",distribution.name());
        tag.put("Insertion",insertion.save(registries));tag.put("Extraction",extraction.save(registries));tag.put("Persistent",persistent.copy());
        ListTag values=new ListTag();for(RuneLayer layer:layers)values.add(layer.save(registries));tag.put("Layers",values);
        ListTag archive=new ListTag();for(RuneLayer layer:archivedLayers)archive.add(layer.save(registries));tag.put("ArchivedLayers",archive);tag.putInt("LayerVersion",2);return tag;
    }
    public static RuneSurface load(MinecraftServer server,CompoundTag tag,HolderLookup.Provider registries){
        RuneSurface rune=new RuneSurface(server,AnchorAddress.load(tag));rune.channel=tag.contains("Channel")?Math.clamp(tag.getIntOr("Channel",0),-1,15):-1;rune.priority=Math.clamp(tag.getIntOr("Priority",0),-999,999);
        rune.dimensional=tag.getBooleanOr("Dimensional",false);rune.enabled=!tag.contains("Enabled")||tag.getBooleanOr("Enabled",false);rune.editingExtraction=tag.getBooleanOr("EditingExtraction",false);rune.excludeNext=tag.getBooleanOr("ExcludeNext",false);
        try{rune.distribution=DistributionMode.valueOf(tag.getStringOr("Distribution",""));}catch(IllegalArgumentException ignored){}
        rune.insertion.load(tag.getCompoundOrEmpty("Insertion"),registries);rune.extraction.load(tag.getCompoundOrEmpty("Extraction"),registries);rune.persistent.merge(tag.getCompoundOrEmpty("Persistent"));
        if(tag.contains("Layers")){
            ListTag values=tag.getListOrEmpty("Layers");Set<UUID> ids=new HashSet<>();
            for(int i=0;i<Math.min(64,values.size());i++)try{RuneLayer layer=RuneLayer.load(rune,values.getCompoundOrEmpty(i),registries);if(ids.add(layer.id()))rune.acceptLoaded(layer);}catch(IllegalArgumentException ignored){}
            ListTag archive=tag.getListOrEmpty("ArchivedLayers");
            for(int i=0;i<archive.size()&&rune.layers.size()+rune.archivedLayers.size()<64;i++)try{RuneLayer layer=RuneLayer.load(rune,archive.getCompoundOrEmpty(i),registries);if(ids.add(layer.id()))rune.archivedLayers.add(layer);}catch(IllegalArgumentException ignored){}
        }else{
            ListTag values=tag.getListOrEmpty("Glyphs");
            for(int i=0;i<Math.min(64,values.size());i++){
                CompoundTag old=values.getCompoundOrEmpty(i);boolean push="DISTRIBUTION".equals(old.getStringOr("Role",""))||old.getStringOr("Item","").endsWith(":distribution_rune");
                RuneLayer layer=new RuneLayer(rune,UUID.randomUUID(),Identifier.fromNamespaceAndPath("astral_repository",push?"push_rune":"pull_rune"),push?RuneLayer.Mode.PUSH:RuneLayer.Mode.PULL);
                layer.filter().load(tag.getCompoundOrEmpty(push?"Extraction":"Insertion"),registries);rune.acceptLoaded(layer);
            }
        }
        return rune;
    }
    private void acceptLoaded(RuneLayer layer){if(layers.size()<MAX_LAYERS)layers.add(layer);else archivedLayers.add(layer);}
}
