package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.menu.NexusMenu;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.cappleapple.astralrepository.platform.StreamCodec;
import com.cappleapple.astralrepository.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.cappleapple.astralrepository.platform.IEventBus;
import com.cappleapple.astralrepository.platform.network.PacketDistributor;
import com.cappleapple.astralrepository.platform.network.event.RegisterPayloadHandlersEvent;

public final class NetworkPackets {
    public static final int SEARCH=0, QUICK_WITHDRAW=1, DEPOSIT=2, CRAFT=3, CANCEL=4, PICKUP=5, CRAFT_STACK=6, CLEAR_GRID=7;
    public static final int WINDOW_SIZE=54, MAX_JOBS=64;
    public static Consumer<Page> pageReceiver = p -> {};
    public static Consumer<Visual> visualReceiver = p -> {};
    public static Runnable visualResetReceiver = () -> {};
    public static Consumer<Diagnostics> diagnosticsReceiver = p -> {};
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) { return new CustomPacketPayload.Type<>(new ResourceLocation("astral_repository",path)); }
    public record Action(int menu,int kind,ItemStack stack,int amount,int row,String query,long request) implements CustomPacketPayload {
        public Action(int menu,int kind,ItemStack stack,int amount,int row,String query){this(menu,kind,stack,amount,row,query,0);}
        public static final Type<Action> TYPE=NetworkPackets.type("action");
        public static final StreamCodec<FriendlyByteBuf,Action> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.menu);b.writeVarLong(p.request);b.writeVarInt(p.kind);com.cappleapple.astralrepository.platform.Backport.ITEM_CODEC.encode(b,p.stack);b.writeVarInt(p.amount);b.writeVarInt(p.row);b.writeUtf(p.query,128);},b->{int menu=b.readVarInt();long request=b.readVarLong();return new Action(menu,b.readVarInt(),com.cappleapple.astralrepository.platform.Backport.ITEM_CODEC.decode(b),b.readVarInt(),b.readVarInt(),b.readUtf(128),request);});
        @Override public Type<Action> type(){return TYPE;}
    }
    public enum JobState { WAITING, RUNNING, EXTERNAL, COMPLETE, FAILED, CANCELLED, CALCULATING, MISSING;
        public boolean terminal(){return this==COMPLETE||this==FAILED||this==CANCELLED;}
    }
    public record Missing(ItemStack icon,String tag,long count) {}
    public record Job(UUID id,ItemStack target,long count,JobState state,int completed,int total,int active,String detail,List<Missing> missing) {
        public Job{missing=List.copyOf(missing);}
        public Job(UUID id,ItemStack target,long count,JobState state,int completed,int total,int active,String detail){this(id,target,count,state,completed,total,active,detail,List.of());}
    }
    public record CancelJob(int menu,UUID job) implements CustomPacketPayload {
        public static final Type<CancelJob> TYPE=NetworkPackets.type("cancel_job");
        public static final StreamCodec<FriendlyByteBuf,CancelJob> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.menu);b.writeUUID(p.job);},b->new CancelJob(b.readVarInt(),b.readUUID()));
        @Override public Type<CancelJob> type(){return TYPE;}
    }
    /** A complete bounded window. A revision belongs to this menu instance, including its search. */
    public record Page(int menu,long revision,int row,int total,String error,List<NexusMenu.Entry> entries,List<Job> jobs,long request) implements CustomPacketPayload {
        public Page(int menu,long revision,int row,int total,String error,List<NexusMenu.Entry> entries,List<Job> jobs){this(menu,revision,row,total,error,entries,jobs,0);}
        public Page { entries=List.copyOf(entries);jobs=List.copyOf(jobs); }
        public int totalRows(){return (int)(((long)total+8)/9);}
        public static final Type<Page> TYPE=NetworkPackets.type("page");
        public static final StreamCodec<FriendlyByteBuf,Page> CODEC=StreamCodec.of((b,p)->{
            b.writeVarInt(p.menu);b.writeVarLong(p.revision);b.writeVarLong(p.request);b.writeVarInt(p.row);b.writeVarInt(p.total);b.writeUtf(p.error,512);
            b.writeVarInt(p.entries.size());for(var e:p.entries){com.cappleapple.astralrepository.platform.Backport.ITEM_CODEC.encode(b,e.stack());b.writeVarLong(e.count());b.writeBoolean(e.craftable());}
            b.writeVarInt(p.jobs.size());p.jobs.forEach(j->writeJob(b,j,false));
        },b->{int menu=b.readVarInt();long revision=b.readVarLong(),request=b.readVarLong();int row=b.readVarInt(),total=b.readVarInt();String error=b.readUtf(512);
            int n=size(b,WINDOW_SIZE);List<NexusMenu.Entry> entries=new ArrayList<>();for(int i=0;i<n;i++)entries.add(new NexusMenu.Entry(com.cappleapple.astralrepository.platform.Backport.ITEM_CODEC.decode(b),b.readVarLong(),b.readBoolean()));
            n=size(b,MAX_JOBS);List<Job> jobs=new ArrayList<>();for(int i=0;i<n;i++)jobs.add(readJob(b,false));return new Page(menu,revision,row,total,error,entries,jobs,request);});
        @Override public Type<Page> type(){return TYPE;}
    }
    public record CountChange(int slot,long count,boolean craftable) {}
    /** Empty targets in jobChanges reuse the target already known for that UUID. */
    public record Delta(int menu,long baselineRevision,long revision,int total,List<CountChange> counts,
                        List<Job> jobChanges,List<UUID> jobOrder,String error,long request) implements CustomPacketPayload {
        public Delta(int menu,long baselineRevision,long revision,int total,List<CountChange> counts,List<Job> jobChanges,List<UUID> jobOrder,String error){this(menu,baselineRevision,revision,total,counts,jobChanges,jobOrder,error,0);}
        public static final Type<Delta> TYPE=NetworkPackets.type("delta");
        public static final StreamCodec<FriendlyByteBuf,Delta> CODEC=StreamCodec.of((b,p)->{
            b.writeVarInt(p.menu);b.writeVarLong(p.baselineRevision);b.writeVarLong(p.revision);b.writeVarLong(p.request);b.writeVarInt(p.total);
            b.writeVarInt(p.counts.size());for(var c:p.counts){b.writeVarInt(c.slot);b.writeVarLong(c.count);b.writeBoolean(c.craftable);}
            b.writeVarInt(p.jobChanges.size());p.jobChanges.forEach(j->writeJob(b,j,true));
            b.writeBoolean(p.jobOrder!=null);if(p.jobOrder!=null){b.writeVarInt(p.jobOrder.size());p.jobOrder.forEach(b::writeUUID);}
            b.writeBoolean(p.error!=null);if(p.error!=null)b.writeUtf(p.error,512);
        },b->{int menu=b.readVarInt();long baseline=b.readVarLong(),revision=b.readVarLong(),request=b.readVarLong();int total=b.readVarInt();
            int n=size(b,WINDOW_SIZE);List<CountChange> counts=new ArrayList<>();for(int i=0;i<n;i++)counts.add(new CountChange(b.readVarInt(),b.readVarLong(),b.readBoolean()));
            n=size(b,MAX_JOBS);List<Job> jobs=new ArrayList<>();for(int i=0;i<n;i++)jobs.add(readJob(b,true));
            List<UUID> order=null;if(b.readBoolean()){n=size(b,MAX_JOBS);order=new ArrayList<>();for(int i=0;i<n;i++)order.add(b.readUUID());}
            String error=b.readBoolean()?b.readUtf(512):null;return new Delta(menu,baseline,revision,total,List.copyOf(counts),List.copyOf(jobs),order==null?null:List.copyOf(order),error,request);});
        @Override public Type<Delta> type(){return TYPE;}
    }
    private static int size(FriendlyByteBuf b,int maximum){int n=b.readVarInt();if(n<0||n>maximum)throw new IllegalArgumentException("Invalid Nexus update size");return n;}
    private static void writeJob(FriendlyByteBuf b,Job j,boolean patch){
        b.writeUUID(j.id);if(patch)com.cappleapple.astralrepository.platform.Backport.ITEM_CODEC.encode(b,j.target);else com.cappleapple.astralrepository.platform.Backport.ITEM_CODEC.encode(b,j.target);
        b.writeVarLong(j.count);b.writeEnum(j.state);b.writeVarInt(j.completed);b.writeVarInt(j.total);b.writeVarInt(j.active);b.writeUtf(j.detail,512);
        b.writeVarInt(j.missing.size());for(var m:j.missing){com.cappleapple.astralrepository.platform.Backport.ITEM_CODEC.encode(b,m.icon());b.writeUtf(m.tag(),256);b.writeVarLong(m.count());}
    }
    private static Job readJob(FriendlyByteBuf b,boolean patch){return new Job(b.readUUID(),patch?com.cappleapple.astralrepository.platform.Backport.ITEM_CODEC.decode(b):com.cappleapple.astralrepository.platform.Backport.ITEM_CODEC.decode(b),b.readVarLong(),b.readEnum(JobState.class),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUtf(512),readMissing(b));}
    private static List<Missing> readMissing(FriendlyByteBuf b){int n=size(b,512);var result=new ArrayList<Missing>();for(int i=0;i<n;i++){var icon=com.cappleapple.astralrepository.platform.Backport.ITEM_CODEC.decode(b);var tag=b.readUtf(256);long count=b.readVarLong();if(count<1)throw new IllegalArgumentException("Invalid shortage");result.add(new Missing(icon,tag,count));}return List.copyOf(result);}
    private static boolean sameJob(Job a,Job b){return a.id.equals(b.id)&&ItemStack.isSameItemSameTags(a.target,b.target)&&a.count==b.count&&a.state==b.state&&a.completed==b.completed&&a.total==b.total&&a.active==b.active&&a.detail.equals(b.detail)&&sameMissing(a.missing,b.missing);}
    private static boolean sameMissing(List<Missing> a,List<Missing> b){if(a.size()!=b.size())return false;for(int i=0;i<a.size();i++){var x=a.get(i);var y=b.get(i);if(x.count()!=y.count()||!x.tag().equals(y.tag())||!ItemStack.isSameItemSameTags(x.icon(),y.icon()))return false;}return true;}
    /** Returns null when nothing observable changed; inventory identities are only sent when needed. */
    public static CustomPacketPayload difference(Page before,Page after,boolean resetWindow){
        if(before==null||resetWindow||before.menu!=after.menu||before.row!=after.row||before.entries.size()!=after.entries.size())return after;
        List<CountChange> counts=new ArrayList<>();
        for(int i=0;i<after.entries.size();i++){
            var a=before.entries.get(i);var b=after.entries.get(i);
            if(!ItemStack.isSameItemSameTags(a.stack(),b.stack()))return after;
            if(a.count()!=b.count()||a.craftable()!=b.craftable())counts.add(new CountChange(i,b.count(),b.craftable()));
        }
        Map<UUID,Job> known=new HashMap<>();before.jobs.forEach(j->known.put(j.id,j));List<Job> changes=new ArrayList<>();
        for(Job job:after.jobs){Job previous=known.get(job.id);if(previous==null||!sameJob(previous,job))changes.add(new Job(job.id,previous!=null&&ItemStack.isSameItemSameTags(previous.target,job.target)?ItemStack.EMPTY:job.target,job.count,job.state,job.completed,job.total,job.active,job.detail,job.missing));}
        List<UUID> oldOrder=before.jobs.stream().map(Job::id).toList(),newOrder=after.jobs.stream().map(Job::id).toList();
        List<UUID> order=oldOrder.equals(newOrder)?null:newOrder;String error=before.error.equals(after.error)?null:after.error;
        if(counts.isEmpty()&&changes.isEmpty()&&order==null&&error==null&&before.total==after.total&&before.request==after.request)return null;
        return new Delta(after.menu,before.revision,after.revision,after.total,List.copyOf(counts),List.copyOf(changes),order,error,after.request);
    }
    /** Reject stale baselines rather than applying a slot delta to another search's row zero. */
    public static Page reconstruct(Page baseline,Delta delta){
        if(baseline==null||baseline.menu!=delta.menu||baseline.revision!=delta.baselineRevision||delta.revision<=baseline.revision)return null;
        List<NexusMenu.Entry> entries=new ArrayList<>(baseline.entries);Set<Integer> changed=new HashSet<>();
        for(var count:delta.counts){if(count.slot<0||count.slot>=entries.size()||count.count<0||!changed.add(count.slot))return null;var old=entries.get(count.slot);entries.set(count.slot,new NexusMenu.Entry(old.stack(),count.count,count.craftable));}
        Map<UUID,Job> jobs=new LinkedHashMap<>();baseline.jobs.forEach(j->jobs.put(j.id,j));
        for(Job update:delta.jobChanges){Job old=jobs.get(update.id);if(update.target.isEmpty()&&old==null)return null; jobs.put(update.id,new Job(update.id,update.target.isEmpty()?old.target:update.target,update.count,update.state,update.completed,update.total,update.active,update.detail,update.missing));}
        List<Job> ordered=new ArrayList<>();if(delta.jobOrder==null)ordered.addAll(jobs.values());else for(UUID id:delta.jobOrder){Job job=jobs.remove(id);if(job==null)return null;ordered.add(job);}
        return new Page(delta.menu,delta.revision,baseline.row,delta.total,delta.error==null?baseline.error:delta.error,entries,ordered,delta.request);
    }
    public record Visual(BlockPos from,BlockPos to,ItemStack stack,int color,int duration,int slot,List<BlockPos> path,TransferVisuals.Endpoint departure,TransferVisuals.Endpoint arrival,ResourceLocation fluid) implements CustomPacketPayload {
        public Visual(BlockPos from,BlockPos to,ItemStack stack,int color,int duration,int slot,List<BlockPos> path,TransferVisuals.Endpoint departure,TransferVisuals.Endpoint arrival){this(from,to,stack,color,duration,slot,path,departure,arrival,null);}
        public Visual(BlockPos from,BlockPos to,ItemStack stack,int color,int duration,int slot,List<BlockPos> path){this(from,to,stack,color,duration,slot,path,null,null);}
        public Visual(BlockPos from,BlockPos to,ItemStack stack,int color,int duration,int slot){this(from,to,stack,color,duration,slot,List.of(from,to));}
        public Visual {path=List.copyOf(path);if(path.size()<2||path.size()>130||!path.get(0).equals(from)||!path.get(path.size()-1).equals(to)||duration<1||duration>13000)throw new IllegalArgumentException("Invalid visual route");}
        public static final Type<Visual> TYPE=NetworkPackets.type("visual");
        public static final StreamCodec<FriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,p)->{
            b.writeBlockPos(p.from);b.writeBlockPos(p.to);com.cappleapple.astralrepository.platform.Backport.ITEM_CODEC.encode(b,p.stack);b.writeInt(p.color);b.writeVarInt(p.duration);b.writeInt(p.slot);
            b.writeVarInt(p.path.size());p.path.forEach(b::writeBlockPos);writeEndpoint(b,p.departure);writeEndpoint(b,p.arrival);b.writeBoolean(p.fluid!=null);if(p.fluid!=null)b.writeResourceLocation(p.fluid);
        },b->{var from=b.readBlockPos();var to=b.readBlockPos();var stack=com.cappleapple.astralrepository.platform.Backport.ITEM_CODEC.decode(b);int color=b.readInt(),duration=b.readVarInt(),slot=b.readInt(),n=size(b,130);List<BlockPos> path=new ArrayList<>();for(int i=0;i<n;i++)path.add(b.readBlockPos());return new Visual(from,to,stack,color,duration,slot,path,readEndpoint(b),readEndpoint(b),b.readBoolean()?b.readResourceLocation():null);});
        private static void writeEndpoint(FriendlyByteBuf b,TransferVisuals.Endpoint e){b.writeBoolean(e!=null);if(e!=null){b.writeEnum(e.face());b.writeFloat((float)e.offset().x);b.writeFloat((float)e.offset().y);b.writeFloat((float)e.offset().z);}}
        private static TransferVisuals.Endpoint readEndpoint(FriendlyByteBuf b){if(!b.readBoolean())return null;var face=b.readEnum(net.minecraft.core.Direction.class);return new TransferVisuals.Endpoint(new net.minecraft.world.phys.Vec3(b.readFloat(),b.readFloat(),b.readFloat()),face);}
        @Override public Type<Visual> type(){return TYPE;}
    }
    public record DiagnosticNode(BlockPos pos,int color,String text) {}
    public record DiagnosticEdge(BlockPos from,BlockPos to,int color) {}
    public record Diagnostics(List<DiagnosticNode> nodes,List<DiagnosticEdge> edges) implements CustomPacketPayload {
        public static final Type<Diagnostics> TYPE=NetworkPackets.type("diagnostics");
        public static final StreamCodec<FriendlyByteBuf,Diagnostics> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.nodes.size());for(var n:p.nodes){b.writeBlockPos(n.pos);b.writeInt(n.color);b.writeUtf(n.text,256);}b.writeVarInt(p.edges.size());for(var e:p.edges){b.writeBlockPos(e.from);b.writeBlockPos(e.to);b.writeInt(e.color);}},b->{int n=b.readVarInt();if(n<0||n>256)throw new IllegalArgumentException();List<DiagnosticNode> nodes=new ArrayList<>();for(int i=0;i<n;i++)nodes.add(new DiagnosticNode(b.readBlockPos(),b.readInt(),b.readUtf(256)));int m=b.readVarInt();if(m<0||m>512)throw new IllegalArgumentException();List<DiagnosticEdge> edges=new ArrayList<>();for(int i=0;i<m;i++)edges.add(new DiagnosticEdge(b.readBlockPos(),b.readBlockPos(),b.readInt()));return new Diagnostics(nodes,edges);});
        @Override public Type<Diagnostics> type(){return TYPE;}
    }
    public static void setup(IEventBus bus) { register(new RegisterPayloadHandlersEvent()); }
    private static void register(RegisterPayloadHandlersEvent event) {
        var r=event.registrar("8");
        r.playToServer(Action.TYPE,Action.CODEC,(payload,context)-> { if(context.player() instanceof ServerPlayer p && p.containerMenu instanceof NexusMenu menu && menu.containerId==payload.menu) menu.action(payload); });
        r.playToServer(CancelJob.TYPE,CancelJob.CODEC,(payload,context)-> { if(context.player() instanceof ServerPlayer p && p.containerMenu instanceof NexusMenu menu && menu.containerId==payload.menu) menu.cancelJob(payload); });
        r.playToClient(Page.TYPE,Page.CODEC,(payload,context)->{if(context.player().containerMenu instanceof NexusMenu menu&&menu.acceptPage(payload))pageReceiver.accept(payload);});
        r.playToClient(Delta.TYPE,Delta.CODEC,(payload,context)->{if(context.player().containerMenu instanceof NexusMenu menu){Page page=reconstruct(menu.currentPage(),payload);if(page!=null&&menu.acceptPage(page))pageReceiver.accept(page);}});
        r.playToClient(Visual.TYPE,Visual.CODEC,(payload,context)->visualReceiver.accept(payload));
        r.playToClient(TransferVisualBatch.TYPE,TransferVisualBatch.CODEC,(payload,context)->{if(payload.resetStations())visualResetReceiver.run();payload.visuals().forEach(visualReceiver);});
        r.playToClient(Diagnostics.TYPE,Diagnostics.CODEC,(payload,context)->diagnosticsReceiver.accept(payload));
    }
    public static void sendUpdate(ServerPlayer player,CustomPacketPayload update) { PacketDistributor.sendToPlayer(player,update); }
    private NetworkPackets() {}
}