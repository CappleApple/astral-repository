package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.content.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.registries.*;
import net.minecraft.network.FriendlyByteBuf;
import com.cappleapple.astralrepository.platform.StreamCodec;
import com.cappleapple.astralrepository.platform.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import com.cappleapple.astralrepository.platform.IEventBus;
import com.cappleapple.astralrepository.platform.network.PacketDistributor;
import com.cappleapple.astralrepository.platform.network.event.RegisterPayloadHandlersEvent;

/** The server issues a short-lived editor session for exactly one nearby rune layer. */
public final class RuneSettingsPackets {
    public enum Operation { SAVE, ADD_RULE, REMOVE_RULE, CLEAR_FILTER, UNLINK, REMOVE_RUNE, TOGGLE_ITEM_DATA, AUTOSAVE, SET_MODE, SAVE_PRESET, ADD_INVENTORY, SET_CADENCE, RESET_CADENCE }
    public record Page(UUID session,String title,String target,String status,List<String> entries,boolean all,boolean blacklist,boolean enabled,long minimum,long targetAmount,int priority,boolean closed,boolean accepted,RuneLayer.Mode mode,List<String> rules,int resources,RuneCadence cadence,RuneCadence defaults) implements CustomPacketPayload {
        public Page(UUID session,String title,String target,String status,List<String> entries,boolean all,boolean blacklist,boolean enabled,long minimum,long targetAmount,int priority,boolean closed,boolean accepted,RuneLayer.Mode mode,List<String> rules){this(session,title,target,status,entries,all,blacklist,enabled,minimum,targetAmount,priority,closed,accepted,mode,rules,0,RuneCadence.DEFAULT,RuneCadence.DEFAULT);}
        public Page(UUID session,String title,String target,String status,List<String> entries,boolean all,boolean blacklist,boolean enabled,long minimum,long targetAmount,int priority,boolean closed,boolean accepted){this(session,title,target,status,entries,false,blacklist,enabled,minimum,targetAmount,priority,closed,accepted,RuneLayer.Mode.PUSH,List.of());}
        public Page(UUID session,String title,String target,String status,List<String> entries,boolean all,boolean blacklist,boolean enabled,long minimum,long targetAmount,int priority,boolean closed){
            this(session,title,target,status,entries,all,blacklist,enabled,minimum,targetAmount,priority,closed,true);
        }
        public static final Type<Page> TYPE=new Type<>(new ResourceLocation("astral_repository","rune_settings"));
        public static final StreamCodec<FriendlyByteBuf,Page> CODEC=StreamCodec.of((b,p)->{
            b.writeUUID(p.session);b.writeUtf(p.title,32);b.writeUtf(p.target,256);b.writeUtf(p.status,256);b.writeVarInt(p.entries.size());for(String e:p.entries)b.writeUtf(e,256);
            b.writeBoolean(p.all);b.writeBoolean(p.blacklist);b.writeBoolean(p.enabled);b.writeLong(p.minimum);b.writeLong(p.targetAmount);b.writeInt(p.priority);b.writeBoolean(p.closed);b.writeBoolean(p.accepted);b.writeEnum(p.mode);b.writeVarInt(p.rules.size());for(String rule:p.rules)b.writeUtf(rule,256);b.writeVarInt(p.resources);b.writeNbt(p.cadence.save());b.writeNbt(p.defaults.save());
        },b->{UUID id=b.readUUID();String title=b.readUtf(32),target=b.readUtf(256),status=b.readUtf(256);int n=b.readVarInt();if(n<0||n>64)throw new IllegalArgumentException("Invalid filter size");List<String> entries=new ArrayList<>();for(int i=0;i<n;i++)entries.add(b.readUtf(256));return new Page(id,title,target,status,List.copyOf(entries),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readLong(),b.readLong(),b.readInt(),b.readBoolean(),b.readBoolean(),b.readEnum(RuneLayer.Mode.class),readRules(b),b.readVarInt(),RuneCadence.load(java.util.Objects.requireNonNull(b.readNbt())),RuneCadence.load(java.util.Objects.requireNonNull(b.readNbt())));});
        private static List<String> readRules(FriendlyByteBuf b){int n=b.readVarInt();if(n<0||n>64)throw new IllegalArgumentException("Invalid filter count");List<String> result=new ArrayList<>();for(int i=0;i<n;i++)result.add(b.readUtf(256));return List.copyOf(result);}
        @Override public Type<Page> type(){return TYPE;}
    }
    public record Action(UUID session,Operation operation,boolean all,boolean blacklist,boolean enabled,long minimum,long targetAmount,int priority,String rule,int index) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(new ResourceLocation("astral_repository","rune_settings_action"));
        public static final StreamCodec<FriendlyByteBuf,Action> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.session);b.writeEnum(p.operation);b.writeBoolean(p.all);b.writeBoolean(p.blacklist);b.writeBoolean(p.enabled);b.writeLong(p.minimum);b.writeLong(p.targetAmount);b.writeInt(p.priority);b.writeUtf(p.rule,256);b.writeInt(p.index);},b->new Action(b.readUUID(),b.readEnum(Operation.class),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readLong(),b.readLong(),b.readInt(),b.readUtf(256),b.readInt()));
        @Override public Type<Action> type(){return TYPE;}
    }
    private record Session(UUID token,RuneSurface surface,UUID layer,long expires,net.minecraft.nbt.CompoundTag configuration){}
    private static final Map<ServerPlayer,Session> SESSIONS=new WeakHashMap<>();
    public static Consumer<Page> receiver=page->{};
    public static void setup(IEventBus bus){register(new RegisterPayloadHandlersEvent());}
    private static void register(RegisterPayloadHandlersEvent event){var registrar=event.registrar("5");registrar.playToClient(Page.TYPE,Page.CODEC,(p,c)->receiver.accept(p));registrar.playToServer(Action.TYPE,Action.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer player)handle(player,p);});}
    public static void open(ServerPlayer player,RuneSurface surface,RuneLayer layer){
        if(!nearby(player,surface)||surface.get(layer.id())!=layer)return;
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,who)->new com.cappleapple.astralrepository.menu.RuneSettingsMenu(id,inventory),net.minecraft.network.chat.Component.literal("Rune")));
        Session session=new Session(UUID.randomUUID(),surface,layer.id(),player.serverLevel().getGameTime()+6000,configuration(surface,layer));SESSIONS.put(player,session);send(player,session,layer,"",false);
    }
    private static net.minecraft.nbt.CompoundTag configuration(RuneSurface surface,RuneLayer layer){
        var tag=new net.minecraft.nbt.CompoundTag();tag.put("Filter",layer.filter().save(surface.getLevel().registryAccess()));tag.put("Cadence",layer.cadence().save());tag.putInt("Priority",layer.priority());tag.putBoolean("Enabled",layer.enabled());tag.putString("Mode",layer.mode().name());tag.putString("Item",layer.item().toString());
        var targets=new net.minecraft.nbt.ListTag();for(var target:layer.targets())targets.add(new AnchorAddress(target.position(),target.face()).save());tag.put("Targets",targets);return tag;
    }
    private static boolean nearby(ServerPlayer player,RuneSurface surface){return player.isAlive()&&player.serverLevel()==surface.getLevel()&&!surface.isRemoved()&&player.distanceToSqr(surface.getBlockPos().getCenter())<=Math.pow(com.cappleapple.astralrepository.platform.FabricReach.range(player)+1,2);}
    public static void handle(ServerPlayer player,Action action){
        Session session=SESSIONS.get(player);if(session==null||!session.token.equals(action.session))return;
        RuneLayer layer=session.surface.get(session.layer);
        if(layer==null||!nearby(player,session.surface)||player.serverLevel().getGameTime()>session.expires){SESSIONS.remove(player);send(player,session,layer,"This rune is no longer within reach. Open it again.",true,false);return;}
        if(!session.configuration.equals(configuration(session.surface,layer))){
            SESSIONS.remove(player);String reason="This rune changed while its settings were open. Reopen it before applying changes.";
            send(player,session,layer,reason,true,false);return;
        }
        if(action.minimum<0||action.targetAmount<0||action.priority < -999||action.priority>999){send(player,session,layer,"Use nonnegative counts and priority from -999 to 999.",false,false);return;}
        RuneCadence cadence=layer.cadence();
        try{
            if(action.operation==Operation.SET_CADENCE){String[] parts=action.rule.split(":");if(parts.length!=3)throw new IllegalArgumentException();var kind=RuneCadence.Kind.valueOf(parts[0]);if(layer.mode()==RuneLayer.Mode.FILTER||(supportedResources(session.surface)&kind.bit())==0)throw new IllegalArgumentException();var rate=new RuneCadence.Rate(Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));if(!kind.permits(rate))throw new IllegalArgumentException();cadence=cadence.with(kind,rate);}
            else if(action.operation==Operation.RESET_CADENCE)cadence=RuneCadence.DEFAULT;
        }catch(IllegalArgumentException invalid){send(player,session,layer,"Invalid transfer amount or interval.",false,false);return;}
        ItemStack inventorySample=ItemStack.EMPTY;
        if(action.operation==Operation.ADD_INVENTORY){
            if(!(player.containerMenu instanceof com.cappleapple.astralrepository.menu.RuneSettingsMenu menu)||action.index<0||action.index>=menu.slots.size()){send(player,session,layer,"",false,false);return;}
            inventorySample=menu.getSlot(action.index).getItem().copy();
            if(inventorySample.isEmpty()){send(player,session,layer,"",false,false);return;}
        }
        FilterRules.Entry added=null;
        try{if(action.operation==Operation.ADD_RULE||action.operation==Operation.ADD_INVENTORY){if(layer.filter().entries().size()>=64)throw new IllegalArgumentException("This filter already has 64 rules.");added=parseRule(action.operation==Operation.ADD_INVENTORY?sampleRule(inventorySample):action.rule);}}
        catch(IllegalArgumentException error){send(player,session,layer,error.getMessage(),false,false);return;}
        if((action.operation==Operation.REMOVE_RULE||action.operation==Operation.TOGGLE_ITEM_DATA)&&(action.index<0||action.index>=layer.filter().entries().size())){send(player,session,layer,"That rule no longer exists.",false,false);return;}
        if(action.operation==Operation.TOGGLE_ITEM_DATA){var e=layer.filter().entries().get(action.index);if((e.kind()!=FilterRules.Kind.ITEM&&e.kind()!=FilterRules.Kind.COMPONENTS)||(e.sample().isEmpty()&&heldSample(player,e.id()).isEmpty())){send(player,session,layer,"Hold a matching item to capture its exact data.",false,false);return;}}
        if(action.operation==Operation.REMOVE_RUNE){
            if(RuneProgramming.removeAndReturnRune(player,session.surface,layer)){SESSIONS.remove(player);send(player,session,layer,"Rune removed.",true);}
            else send(player,session,layer,"The rune could not be removed.",false,false);
            return;
        }
        layer.setCadence(cadence);var filter=layer.filter();if(filter.blacklist()!=action.blacklist)filter.toggleBlacklist();filter.setMinimum(action.minimum);filter.setTarget(action.targetAmount);layer.setPriority(action.priority);layer.setEnabled(action.enabled);
        String status=switch(action.operation){
            case ADD_RULE,ADD_INVENTORY->{filter.add(added.kind(),added.id(),added.exclude(),added.kind()==FilterRules.Kind.ITEM?(inventorySample.isEmpty()?heldSample(player,added.id()):inventorySample):ItemStack.EMPTY);yield "Filter rule added.";}
            case TOGGLE_ITEM_DATA->{var entry=filter.entries().get(action.index);if(entry.sample().isEmpty())filter.add(entry.kind(),entry.id(),entry.exclude(),heldSample(player,entry.id()));filter.toggleItemData(action.index);yield "Item-data matching changed.";}
            case REMOVE_RULE->{filter.remove(action.index);yield "Filter rule removed.";}
            case CLEAR_FILTER->{layer.clearFilter();yield "Filter cleared.";}
            case SET_MODE->{layer.setMode(layer.mode().cycle(action.index<0?-1:1));yield "";}
            case UNLINK->{session.surface.clearTarget(layer.id());yield "Target cleared.";}
            default->"Settings saved.";
        };
        layer.changed();
        if(action.operation==Operation.SAVE_PRESET){
            var preset=new RunePreset(UUID.randomUUID(),layer.mode().title(),layer.mode(),layer.design(),filter.save(player.level().registryAccess()),layer.priority(),layer.enabled(),layer.cadence());
            SESSIONS.remove(player);send(player,session,layer,"",true);WandPackets.openPreset(player,preset);return;
        }
        if(action.operation!=Operation.SAVE)SESSIONS.put(player,new Session(session.token,session.surface,session.layer,session.expires,configuration(session.surface,layer)));send(player,session,layer,status,action.operation==Operation.SAVE);if(action.operation==Operation.SAVE)SESSIONS.remove(player);
    }
    public static String sampleRule(ItemStack sample){
        var fluid=com.cappleapple.astralrepository.platform.fluids.FluidUtil.getFluidContained(sample);
        return fluid.isPresent()?"fluid:"+BuiltInRegistries.FLUID.getKey(fluid.get().getFluid()):BuiltInRegistries.ITEM.getKey(sample.getItem()).toString();
    }
    private static ItemStack heldSample(ServerPlayer player, String id) {
        ItemStack carried=player.containerMenu.getCarried();
        if(!carried.isEmpty()&&BuiltInRegistries.ITEM.getKey(carried.getItem()).toString().equals(id))return carried.copyWithCount(1);
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (!held.isEmpty() && BuiltInRegistries.ITEM.getKey(held.getItem()).toString().equals(id)) return held.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }
    public static FilterRules.Entry parseRule(String value){
        String rule=value.strip();boolean exclude=rule.startsWith("!");if(exclude)rule=rule.substring(1).strip();boolean fluid=rule.startsWith("fluid:");if(fluid)rule=rule.substring(6);
        if(rule.startsWith("@")){
            String namespace=rule.substring(1);if(!namespace.matches("[a-z0-9_.-]+")||java.util.stream.Stream.concat(BuiltInRegistries.ITEM.keySet().stream(),BuiltInRegistries.FLUID.keySet().stream()).noneMatch(id->id.getNamespace().equals(namespace)))throw new IllegalArgumentException("No loaded items or fluids use that mod ID.");
            return new FilterRules.Entry(FilterRules.Kind.NAMESPACE,namespace,exclude,ItemStack.EMPTY);
        }
        boolean tag=rule.startsWith("#");if(tag)rule=rule.substring(1);ResourceLocation id=ResourceLocation.tryParse(rule);if(id==null)throw new IllegalArgumentException("Use an item ID, #item_tag, @mod_id, or fluid:minecraft:water.");
        if(tag){boolean exists=fluid?BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID,id)).isPresent():BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM,id)).isPresent();if(!exists)throw new IllegalArgumentException("That tag is not loaded.");}
        else if(fluid?!BuiltInRegistries.FLUID.containsKey(id):!BuiltInRegistries.ITEM.containsKey(id))throw new IllegalArgumentException("That "+(fluid?"fluid":"item")+" ID does not exist.");
        return new FilterRules.Entry(fluid?(tag?FilterRules.Kind.FLUID_TAG:FilterRules.Kind.FLUID):(tag?FilterRules.Kind.ITEM_TAG:FilterRules.Kind.ITEM),id.toString(),exclude,ItemStack.EMPTY);
    }
    private static void send(ServerPlayer player,Session session,RuneLayer layer,String status,boolean closed){send(player,session,layer,status,closed,true);}
    private static void send(ServerPlayer player,Session session,RuneLayer layer,String status,boolean closed,boolean accepted){
        if(layer==null){PacketDistributor.sendToPlayer(player,new Page(session.token,"Rune","",limit(status),List.of(),false,false,false,0,Long.MAX_VALUE,0,true,accepted));return;}
        var snapshot=RunePackets.snapshot(session.surface,layer);String target=layer.target()==null?"Unlinked — use the wand to choose a container":(layer.mode()==RuneLayer.Mode.PUSH?"To ":"From ")+snapshot.targetName()+" at "+layer.target().position().pos().toShortString()+" ("+(layer.target().face()==null?"network":layer.target().face().getName())+")";
        List<String> entries=layer.filter().entries().stream().map(e->limit((e.exclude()?"Except ":"")+switch(e.kind()){case ITEM->BuiltInRegistries.ITEM.get(new ResourceLocation(e.id())).getDescription().getString();case COMPONENTS->e.sample().getHoverName().getString()+" (exact data)";case ITEM_TAG->"#"+e.id();case FLUID_TAG->"Fluid #"+e.id();case NAMESPACE->"@"+e.id();case FLUID->"Fluid: "+e.id();})).toList();
        PacketDistributor.sendToPlayer(player,new Page(session.token,layer.mode().title()+" Rune",limit(target),limit(status),entries,layer.filter().all(),layer.filter().blacklist(),layer.enabled(),layer.filter().minimum(),layer.filter().target(),layer.priority(),closed,accepted,layer.mode(),layer.filter().entries().stream().map(RuneSettingsPackets::ruleCode).toList(),supportedResources(session.surface),layer.cadence().resolved(),RuneCadence.DEFAULT.resolved()));
    }
    public static int supportedResources(RuneSurface surface){
        var level=surface.getLevel();var pos=surface.getBlockPos();var face=surface.facing();int mask=0;
        if(!com.cappleapple.astralrepository.compat.CompatibilityRegistry.discoverStorage(level,pos,face).isEmpty())mask|=RuneCadence.Kind.ITEMS.bit();
        for(var provider:com.cappleapple.astralrepository.compat.CompatibilityRegistry.discoverResources(level,pos,face))for(var kind:RuneCadence.Kind.values())if(provider.resourceType().equals(kind.resource))mask|=kind.bit();
        return mask;
    }
    public static String ruleCode(FilterRules.Entry e){return (e.exclude()?"!":"")+switch(e.kind()){case ITEM,COMPONENTS->e.id();case ITEM_TAG->"#"+e.id();case FLUID->"fluid:"+e.id();case FLUID_TAG->"fluid:#"+e.id();case NAMESPACE->"@"+e.id();};}
    private static String limit(String value){return value==null?"Invalid rule.":value.length()>256?value.substring(0,255)+"…":value;}
    private RuneSettingsPackets(){}
}

