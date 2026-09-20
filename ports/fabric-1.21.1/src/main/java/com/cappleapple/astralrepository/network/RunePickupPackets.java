package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.content.RuneProgramming;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.cappleapple.astralrepository.platform.IEventBus;
import com.cappleapple.astralrepository.platform.network.event.RegisterPayloadHandlersEvent;

/** An exact layer request cannot turn into a pickup of a neighboring or newly reflowed rune. */
public final class RunePickupPackets {
    private RunePickupPackets() {}
    public record Pickup(BlockPos pos, Direction face, UUID layer) implements CustomPacketPayload {
        public static final Type<Pickup> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("astral_repository", "rune_pickup"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pickup> CODEC = StreamCodec.of(
                (buffer, packet) -> { buffer.writeBlockPos(packet.pos); buffer.writeEnum(packet.face); buffer.writeUUID(packet.layer); },
                buffer -> new Pickup(buffer.readBlockPos(), buffer.readEnum(Direction.class), buffer.readUUID()));
        @Override public Type<Pickup> type() { return TYPE; }
    }
    public static void setup(IEventBus bus) { register(new RegisterPayloadHandlersEvent()); }
    private static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(Pickup.TYPE, Pickup.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) RuneProgramming.pickup(player, packet.pos(), packet.face(), packet.layer());
        });
    }
}