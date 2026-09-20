package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.content.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Bounded nearby visual state. A looked-at face remains editable without special equipment. */
public final class RunePackets {
    public record FilterIcon(ResourceLocation item,boolean excluded,boolean exact) {}
    public record Layer(UUID id,ResourceLocation item,RuneLayer.Mode mode,RuneLayer.Target target,String targetName,String status,String filter,List<FilterIcon> icons,int priority,boolean enabled,long minimum,long targetAmount,long moved,ResourceLocation targetItem,int filterCount,boolean matchAll,String design,double u,double v,List<ResourceLocation> targetIcons,int targetCount) {}
    public record Face(BlockPos pos,Direction face,int channel,List<Layer> layers) {
        public List<ResourceLocation> glyphs(){return layers.stream().map(Layer::item).toList();}
    }
    public record Faces(List<Face> faces) implements CustomPacketPayload {
        public static final Type<Faces> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("astral_repository","rune_faces"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Faces> CODEC=StreamCodec.of((b,p)->{
            b.writeVarInt(p.faces.size());
            for(Face f:p.faces){b.writeBlockPos(f.pos);b.writeEnum(f.face);b.writeInt(f.channel);b.writeVarInt(f.layers.size());for(Layer l:f.layers){
                b.writeUUID(l.id);b.writeResourceLocation(l.item);b.writeEnum(l.mode);b.writeBoolean(l.target!=null);
                if(l.target!=null){b.writeResourceLocation(l.target.position().dimension().location());b.writeBlockPos(l.target.position().pos());b.writeBoolean(l.target.face()!=null);if(l.target.face()!=null)b.writeEnum(l.target.face());}
                b.writeUtf(l.targetName,128);b.writeUtf(l.status,256);b.writeUtf(l.filter,512);b.writeVarInt(l.icons.size());for(var icon:l.icons){b.writeResourceLocation(icon.item);b.writeBoolean(icon.excluded);b.writeBoolean(icon.exact);}
                b.writeInt(l.priority);b.writeBoolean(l.enabled);b.writeLong(l.minimum);b.writeLong(l.targetAmount);b.writeLong(l.moved);b.writeResourceLocation(l.targetItem);b.writeVarInt(l.filterCount);b.writeBoolean(l.matchAll);b.writeUtf(l.design,36);b.writeDouble(l.u);b.writeDouble(l.v);b.writeVarInt(l.targetIcons.size());l.targetIcons.forEach(b::writeResourceLocation);b.writeVarInt(l.targetCount);
            }}
        },b->{int count=bounded(b.readVarInt(),128),total=0;List<Face> faces=new ArrayList<>();for(int i=0;i<count;i++){
            BlockPos pos=b.readBlockPos();Direction side=b.readEnum(Direction.class);int channel=b.readInt();int n=bounded(b.readVarInt(),RuneSurface.MAX_LAYERS);if((total+=n)>512)throw new IllegalArgumentException("Too many rune layers");List<Layer> layers=new ArrayList<>();
            for(int j=0;j<n;j++){UUID id=b.readUUID();var item=b.readResourceLocation();var mode=b.readEnum(RuneLayer.Mode.class);RuneLayer.Target target=null;
                if(b.readBoolean())target=new RuneLayer.Target(net.minecraft.core.GlobalPos.of(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,b.readResourceLocation()),b.readBlockPos()),b.readBoolean()?b.readEnum(Direction.class):null);
                String name=b.readUtf(128),status=b.readUtf(256),filter=b.readUtf(512);int k=bounded(b.readVarInt(),6);List<FilterIcon> icons=new ArrayList<>();for(int q=0;q<k;q++)icons.add(new FilterIcon(b.readResourceLocation(),b.readBoolean(),b.readBoolean()));
                layers.add(new Layer(id,item,mode,target,name,status,filter,List.copyOf(icons),b.readInt(),b.readBoolean(),b.readLong(),b.readLong(),b.readLong(),b.readResourceLocation(),bounded(b.readVarInt(),64),b.readBoolean(),b.readUtf(36),b.readDouble(),b.readDouble(),readTargetIcons(b),bounded(b.readVarInt(),RuneLayer.MAX_TARGETS)));
            }faces.add(new Face(pos,side,channel,List.copyOf(layers)));}return new Faces(List.copyOf(faces));});
        @Override public Type<Faces> type(){return TYPE;}
    }
    private static List<ResourceLocation> readTargetIcons(RegistryFriendlyByteBuf b){int n=bounded(b.readVarInt(),6);List<ResourceLocation> icons=new ArrayList<>();for(int i=0;i<n;i++)icons.add(b.readResourceLocation());return List.copyOf(icons);}
    private static int bounded(int value,int max){if(value<0||value>max)throw new IllegalArgumentException("Invalid rune packet count");return value;}
    public record Art(net.minecraft.nbt.CompoundTag design) implements CustomPacketPayload {
        public static final Type<Art> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("astral_repository","rune_art"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Art> CODEC=StreamCodec.of((b,p)->b.writeNbt(p.design),b->new Art(b.readNbt()));
        public Type<Art> type(){return TYPE;}
    }
    public static Consumer<Art> artReceiver=p->{};
    private static final Map<net.minecraft.server.level.ServerPlayer,Set<String>> sentArt=new WeakHashMap<>();
    private static final Map<net.minecraft.server.level.ServerPlayer,Faces> lastFaces=new WeakHashMap<>();
    public static Consumer<Faces> receiver=packet->{};
    public static void setup(IEventBus bus){bus.addListener(RunePackets::register);}
    private static void register(RegisterPayloadHandlersEvent event){var registrar=event.registrar("11");BindingPreviewPackets.register(registrar);registrar.playToClient(Faces.TYPE,Faces.CODEC,(packet,context)->receiver.accept(packet));registrar.playToClient(Art.TYPE,Art.CODEC,(p,c)->artReceiver.accept(p));}
    public static void tick(ServerTickEvent.Post event){
        if(event.getServer().getTickCount()%5!=0)return;
        for(var player:event.getServer().getPlayerList().getPlayers()){
            BindingPreviewPackets.tick(player);
            boolean full=GogglesEquipment.isWearing(player)||editingTool(player.getMainHandItem())||editingTool(player.getOffhandItem());
            BlockPos looked=player.pick(6,0,false) instanceof BlockHitResult hit?hit.getBlockPos():null;
            var candidates=full?RuneSurfaces.nearby(player.serverLevel(),player.position(),64):looked==null?List.<RuneSurface>of():RuneSurfaces.at(player.serverLevel(),looked);
            var surfaces=candidates.stream().filter(s->!s.layers().isEmpty()&&!s.isRemoved()&&player.distanceToSqr(s.getBlockPos().getCenter())<=4096).sorted(Comparator.comparingDouble(s->player.distanceToSqr(s.getBlockPos().getCenter()))).limit(128).toList();
            List<Face> faces=new ArrayList<>();int total=0;
            for(var surface:surfaces){if(total+surface.layers().size()>512)continue;total+=surface.layers().size();faces.add(new Face(surface.getBlockPos(),surface.facing(),surface.channel(),surface.layers().stream().map(l->snapshot(surface,l)).toList()));}
            Set<String> known=sentArt.computeIfAbsent(player,p->new LinkedHashSet<>());int artBudget=4;
            for(var surface:surfaces)for(var layer:surface.layers()){String key=layer.design().key();if(!known.contains(key)&&artBudget>0){if(known.size()>=2048)known.remove(known.iterator().next());PacketDistributor.sendToPlayer(player,new Art(layer.design().save()));known.add(key);artBudget--;}}
            Faces packet=new Faces(List.copyOf(faces));if(!packet.equals(lastFaces.get(player))||event.getServer().getTickCount()%20==0){PacketDistributor.sendToPlayer(player,packet);lastFaces.put(player,packet);}
        }
    }
    public static boolean editingTool(ItemStack stack){return stack.is(AstralContent.ATTUNEMENT_WAND.get())||RuneGlyph.isRune(stack);}
    public static Layer snapshot(RuneSurface surface,RuneLayer layer){
        String name="Unlinked";var target=layer.target();
        if(target!=null){var world=surface.getLevel().getServer().getLevel(target.position().dimension());name=world!=null&&world.hasChunkAt(target.position().pos())?world.getBlockState(target.position().pos()).getBlock().getName().getString():"Unloaded container";}
        ResourceLocation targetItem=ResourceLocation.withDefaultNamespace("barrel");
        if(target!=null){var world=surface.getLevel().getServer().getLevel(target.position().dimension());if(world!=null&&world.hasChunkAt(target.position().pos())){var item=world.getBlockState(target.position().pos()).getBlock().asItem();if(item!=net.minecraft.world.item.Items.AIR)targetItem=BuiltInRegistries.ITEM.getKey(item);}}
        List<FilterIcon> icons=layer.filter().entries().stream().limit(6).map(e->{
            var item=switch(e.kind()){
                case ITEM,COMPONENTS->BuiltInRegistries.ITEM.get(ResourceLocation.parse(e.id()));
                case ITEM_TAG,FLUID_TAG->net.minecraft.world.item.Items.NAME_TAG;
                case NAMESPACE->net.minecraft.world.item.Items.BOOK;
                case FLUID->{var bucket=BuiltInRegistries.FLUID.get(ResourceLocation.parse(e.id())).getBucket();yield bucket==net.minecraft.world.item.Items.AIR?net.minecraft.world.item.Items.BUCKET:bucket;}
            };
            return new FilterIcon(BuiltInRegistries.ITEM.getKey(item),e.exclude()||layer.filter().blacklist(),e.kind()==FilterRules.Kind.COMPONENTS);
        }).toList();
        return new Layer(layer.id(),layer.item(),layer.mode(),target,limit(name,128),limit(layer.status(),256),filterText(layer.filter()),icons,layer.priority(),layer.enabled(),layer.filter().minimum(),layer.filter().target(),layer.transferredItems(),targetItem,layer.filter().entries().size(),layer.filter().all(),layer.design().key(),layer.u(),layer.v(),layer.targets().stream().limit(6).map(t->{var world=surface.getLevel().getServer().getLevel(t.position().dimension());return world!=null&&world.hasChunkAt(t.position().pos())?BuiltInRegistries.ITEM.getKey(world.getBlockState(t.position().pos()).getBlock().asItem()):ResourceLocation.withDefaultNamespace("barrier");}).toList(),layer.targets().size());
    }
    public static String filterText(FilterRules filter){
        if(filter.entries().isEmpty())return "All items and fluids";
        List<String> names=new ArrayList<>();for(var e:filter.entries().stream().limit(6).toList()){
            String name=switch(e.kind()){case ITEM,COMPONENTS->BuiltInRegistries.ITEM.get(ResourceLocation.parse(e.id())).getDescription().getString()+(e.kind()==FilterRules.Kind.COMPONENTS?" (exact data)":"");case ITEM_TAG->"#"+e.id();case FLUID_TAG->"Fluid #"+e.id();case NAMESPACE->"@"+e.id();case FLUID->BuiltInRegistries.FLUID.get(ResourceLocation.parse(e.id())).getFluidType().getDescription().getString();};
            names.add((e.exclude()?"Except ":"")+name);
        }
        return limit((filter.blacklist()?"Blacklist: ":"Whitelist: ")+String.join(", ",names)+(filter.entries().size()>6?" …":""),512);
    }
    private static String limit(String text,int max){return text.length()<=max?text:text.substring(0,max-1)+"…";}
    private RunePackets(){}
}
