package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.content.*;
import java.util.*;
import java.util.function.Consumer;
import java.io.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Small editor messages carry one preset at a time, never the entire artwork library. */
public final class WandPackets {
    public enum Op { LOAD, IMPORT, SAVE, CREATE, DELETE, SELECT, BIND }
    private static Identifier id(String path){return Identifier.fromNamespaceAndPath("astral_repository",path);}
    private static void write(RegistryFriendlyByteBuf b,CompoundTag tag){try{var bytes=new ByteArrayOutputStream();NbtIo.write(tag,new DataOutputStream(bytes));if(bytes.size()>98304)throw new IllegalArgumentException("Preset exceeds packet budget");b.writeByteArray(bytes.toByteArray());}catch(IOException e){throw new IllegalArgumentException(e);}}
    private static CompoundTag read(RegistryFriendlyByteBuf b){try{return NbtIo.read(new DataInputStream(new ByteArrayInputStream(b.readByteArray(98304))),NbtAccounter.create(524288));}catch(IOException e){throw new IllegalArgumentException(e);}}
    public record Action(UUID session,Op op,UUID preset,CompoundTag data) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(id("wand_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.session);b.writeEnum(p.op);b.writeUUID(p.preset);write(b,p.data);},b->new Action(b.readUUID(),b.readEnum(Op.class),b.readUUID(),read(b)));
        public Type<Action> type(){return TYPE;}
    }
    public record Page(UUID session,CompoundTag data,boolean open) implements CustomPacketPayload {
        public static final Type<Page> TYPE=new Type<>(id("wand_page"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Page> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.session);write(b,p.data);b.writeBoolean(p.open);},b->new Page(b.readUUID(),read(b),b.readBoolean()));
        public Type<Page> type(){return TYPE;}
    }
    public static Consumer<Page> receiver=p->{};
    public static void setup(IEventBus bus){bus.addListener((RegisterPayloadHandlersEvent e)->{var r=e.registrar("3");r.playToClient(Page.TYPE,Page.CODEC,(p,c)->receiver.accept(p));r.playToServer(Action.TYPE,Action.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer player)handle(player,p);});});}
    private static ItemStack wand(ServerPlayer p){if(p.getMainHandItem().is(AstralContent.ATTUNEMENT_WAND.get()))return p.getMainHandItem();if(p.getOffhandItem().is(AstralContent.ATTUNEMENT_WAND.get()))return p.getOffhandItem();return ItemStack.EMPTY;}
    public static boolean placing(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getBooleanOr("PlaceRune",false);}
    public static UUID selected(ItemStack stack){var t=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();return t.read("RunePreset",net.minecraft.core.UUIDUtil.CODEC).isPresent()?t.read("RunePreset",net.minecraft.core.UUIDUtil.CODEC).orElseThrow():null;}
    public static void selection(ItemStack stack,UUID id,boolean place){var t=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();if(id!=null)t.store("RunePreset",net.minecraft.core.UUIDUtil.CODEC,id);else t.remove("RunePreset");t.putBoolean("PlaceRune",place);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(t));RuneProgramming.cancel(stack);}
    public static RunePreset preset(ServerPlayer player,ItemStack wand){return RuneLibraryData.get(player.level().getServer()).library(player.getUUID()).get(selected(wand));}
    public static void open(ServerPlayer player){if(wand(player).isEmpty())return;openEditor(player,null);}
    public static void openPreset(ServerPlayer player,RunePreset preset){openEditor(player,preset);}
    private static void openEditor(ServerPlayer player,RunePreset preset){if(!player.isAlive()||player.isSpectator())return;UUID session=UUID.randomUUID();player.getPersistentData().store("AstralWandSession",net.minecraft.core.UUIDUtil.CODEC,session);
        var data=RuneLibraryData.get(player.level().getServer());data.library(player.getUUID()).clear();data.setDirty();
        var opening=new CompoundTag();if(preset!=null)opening.put("EditPreset",preset.save());player.getPersistentData().putBoolean("AstralPresetEditor",preset!=null);
        PacketDistributor.sendToPlayer(player,new Page(session,opening,true));
        for(var packPreset:RunePresetFiles.pack(player.registryAccess())){CompoundTag value=new CompoundTag();value.put("PackPreset",packPreset.save());PacketDistributor.sendToPlayer(player,new Page(session,value,false));}
    }
    private static void handle(ServerPlayer player,Action action){
        ItemStack wand=wand(player);var state=player.getPersistentData();if((wand.isEmpty()&&!state.getBooleanOr("AstralPresetEditor",false))||!player.isAlive()||player.isSpectator()||!state.read("AstralWandSession",net.minecraft.core.UUIDUtil.CODEC).isPresent()||!state.read("AstralWandSession",net.minecraft.core.UUIDUtil.CODEC).orElseThrow().equals(action.session))return;
        long tick=player.level().getGameTime();if(state.getLongOr("AstralWandWindow",0L)!=tick/20){state.putLong("AstralWandWindow",tick/20);state.putInt("AstralWandActions",0);}int actions=state.getIntOr("AstralWandActions",0);if(actions>=192)return;state.putInt("AstralWandActions",actions+1);
        var data=RuneLibraryData.get(player.level().getServer());var library=data.library(player.getUUID());UUID selected=action.preset;String error="";
        try{switch(action.op){
            case LOAD->{if(!library.containsKey(selected))return;}
            case IMPORT->{if(!library.containsKey(selected)&&library.size()>=RuneLibraryData.MAX_PRESETS)return;var preset=RunePreset.load(action.data,player.registryAccess());if(!preset.id().equals(selected))return;library.put(selected,preset);data.setDirty();}
            case SAVE->{if(!library.containsKey(selected))return;var preset=RunePreset.load(action.data,player.registryAccess());if(!preset.id().equals(selected))return;
                var previous=library.get(selected);int limit=com.cappleapple.astralrepository.AstralServerConfig.runeResolution.get();
                // Cropped pixels are owned by the saved design, not by a smaller editor viewport.
                int size=Math.max(previous.design().size(),preset.design().size());int[] pixels=new int[size*size];
                for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=(x<limit&&y<limit?preset.design().argb(x,y):previous.design().argb(x,y));
                library.put(selected,new RunePreset(selected,preset.name(),preset.mode(),new RuneDesign(size,pixels,preset.design().icons()),preset.filter(),preset.priority(),preset.enabled(),preset.cadence()));data.setDirty();}
            case CREATE->{if(library.size()>=RuneLibraryData.MAX_PRESETS){error="Preset limit reached";break;}RunePreset previous=library.get(selected);RunePreset next=previous==null?RunePreset.initial(RuneLayer.Mode.PUSH):new RunePreset(UUID.randomUUID(),previous.name()+" copy",previous.mode(),previous.design(),previous.filter(),previous.priority(),previous.enabled(),previous.cadence());selected=next.id();library.put(selected,next);data.setDirty();}
            case DELETE->{library.remove(selected);if(Objects.equals(selected,selected(wand)))selection(wand,null,false);selected=library.isEmpty()?null:library.keySet().iterator().next();data.setDirty();}
            case SELECT->{if(wand.isEmpty()||!library.containsKey(selected))return;selection(wand,selected,true);}
            case BIND->{if(!wand.isEmpty())selection(wand,selected(wand),false);}
        }}catch(IllegalArgumentException invalid){error="Invalid preset";}
        send(player,action.session,selected,false,error);
    }
    private static void send(ServerPlayer p,UUID session,UUID selected,boolean open,String error){if(error.isEmpty()&&!open)return;CompoundTag tag=new CompoundTag();tag.putString("Error",error);PacketDistributor.sendToPlayer(p,new Page(session,tag,open));}
    private WandPackets(){}
}
