package com.cappleapple.astralrepository.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server entries are encoded once and shared between audiences; clients decode one bounded update. */
public final class TransferVisualBatch implements CustomPacketPayload {
    public static final int MAX_BYTES = 262144;
    public static final int MAX_ENTRIES = 1536;
    public static final int HEADER_BYTES = 4;
    public static final Type<TransferVisualBatch> TYPE = new Type<>(Identifier.fromNamespaceAndPath("astral_repository", "visual_batch"));
    private final boolean resetStations;
    private final List<byte[]> encoded;
    private final List<NetworkPackets.Visual> visuals;

    private TransferVisualBatch(boolean resetStations, List<byte[]> encoded, List<NetworkPackets.Visual> visuals) {
        this.resetStations = resetStations;
        this.encoded = encoded;
        this.visuals = visuals;
    }

    static TransferVisualBatch encoded(boolean resetStations, List<byte[]> entries) {
        int bytes = HEADER_BYTES;
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many visual entries");
        for (byte[] entry : entries) {
            if (entry.length > MAX_BYTES - bytes) throw new IllegalArgumentException("Oversized visual batch");
            bytes += entry.length;
        }
        return new TransferVisualBatch(resetStations, List.copyOf(entries), List.of());
    }

    public boolean resetStations() { return resetStations; }
    public List<NetworkPackets.Visual> visuals() { return visuals; }
    @Override public Type<TransferVisualBatch> type() { return TYPE; }

    public static final StreamCodec<RegistryFriendlyByteBuf, TransferVisualBatch> CODEC = StreamCodec.of((buffer, packet) -> {
        buffer.writeBoolean(packet.resetStations);
        buffer.writeVarInt(packet.encoded != null ? packet.encoded.size() : packet.visuals.size());
        if (packet.encoded != null) for (byte[] entry : packet.encoded) buffer.writeBytes(entry);
        else for (var visual : packet.visuals) NetworkPackets.Visual.CODEC.encode(buffer, visual);
    }, buffer -> {
        if (buffer.readableBytes() > MAX_BYTES) throw new IllegalArgumentException("Oversized visual batch");
        int start = buffer.readerIndex();
        boolean reset = buffer.readBoolean(); int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Too many visual entries");
        var entries = new ArrayList<NetworkPackets.Visual>(count);
        for (int i = 0; i < count; i++) {
            entries.add(NetworkPackets.Visual.CODEC.decode(buffer));
            if (buffer.readerIndex() - start > MAX_BYTES) throw new IllegalArgumentException("Oversized visual batch");
        }
        return new TransferVisualBatch(reset, null, List.copyOf(entries));
    });
}
