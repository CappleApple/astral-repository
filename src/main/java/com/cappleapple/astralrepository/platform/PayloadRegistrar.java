package com.cappleapple.astralrepository.platform;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.BiConsumer;
public final class PayloadRegistrar {
 public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new net.minecraft.resources.ResourceLocation("astral_repository","main"),()->"1.11.8-forge-1.20.1","1.11.8-forge-1.20.1"::equals,"1.11.8-forge-1.20.1"::equals);
 private static int next;
 public record Context(Player player) {}
 public <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type,StreamCodec<FriendlyByteBuf,T> codec,BiConsumer<T,Context> handler){register(type,codec,handler,NetworkDirection.PLAY_TO_CLIENT);}
 public <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type,StreamCodec<FriendlyByteBuf,T> codec,BiConsumer<T,Context> handler){register(type,codec,handler,NetworkDirection.PLAY_TO_SERVER);}
 @SuppressWarnings("unchecked") private <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type,StreamCodec<FriendlyByteBuf,T> codec,BiConsumer<T,Context> handler,NetworkDirection direction){
  // The payload class is resolved from its enclosing record's TYPE field at registration.
  Class<T> clazz=(Class<T>)findPayload(type);
  CHANNEL.messageBuilder(clazz,next++,direction).encoder((p,b)->codec.encode(b,p)).decoder(codec::decode).consumerMainThread((p,c)->{Player player=direction==NetworkDirection.PLAY_TO_SERVER?c.get().getSender():ClientPlayer.get();if(player!=null)handler.accept(p,new Context(player));}).add();
 }
 private static Class<?> findPayload(CustomPacketPayload.Type<?> type){
  String[] classes={"NetworkPackets","RecipeTomePackets","RunePackets","RunePickupPackets","RuneSettingsPackets","WandPackets","BindingPreviewPackets","PowerNodeVisibilityPackets","TransferVisualBatch"};
  for(String name:classes)try{Class<?> owner=Class.forName("com.cappleapple.astralrepository.network."+name);for(Class<?> nested:owner.getDeclaredClasses())if(CustomPacketPayload.class.isAssignableFrom(nested)&&nested.getField("TYPE").get(null)==type)return nested;if(CustomPacketPayload.class.isAssignableFrom(owner)&&owner.getField("TYPE").get(null)==type)return owner;}catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
  throw new IllegalArgumentException("Unknown payload "+type.id());
 }
 private static final class ClientPlayer {static Player get(){return net.minecraft.client.Minecraft.getInstance().player;}}
}
