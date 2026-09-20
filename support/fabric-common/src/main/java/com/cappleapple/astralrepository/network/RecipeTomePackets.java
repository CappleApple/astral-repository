package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.menu.RecipeTomeMenu;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.cappleapple.astralrepository.platform.IEventBus;
import com.cappleapple.astralrepository.platform.network.PacketDistributor;
import com.cappleapple.astralrepository.platform.network.event.RegisterPayloadHandlersEvent;

public final class RecipeTomePackets {
    public static Consumer<Page> pageReceiver = page -> {};
    public record Action(int menu, int kind, String query, int page, String recipe) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Identifier.fromNamespaceAndPath("astral_repository", "tome_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.menu); b.writeVarInt(p.kind); b.writeUtf(p.query, 96); b.writeVarInt(p.page); b.writeUtf(p.recipe, 256);
        }, b -> new Action(b.readVarInt(), b.readVarInt(), b.readUtf(96), b.readVarInt(), b.readUtf(256)));
        @Override public Type<Action> type() { return TYPE; }
    }
    public record Page(int menu, int page, int total, ItemStack taught, String status, List<RecipeTomeMenu.Entry> entries) implements CustomPacketPayload {
        public static final Type<Page> TYPE = new Type<>(Identifier.fromNamespaceAndPath("astral_repository", "tome_page"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Page> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.menu); b.writeVarInt(p.page); b.writeVarInt(p.total); ItemStack.OPTIONAL_STREAM_CODEC.encode(b, p.taught); b.writeUtf(p.status, 160);
            b.writeVarInt(p.entries.size());
            for (var entry : p.entries) {
                b.writeIdentifier(entry.recipe()); b.writeIdentifier(entry.type()); ItemStack.STREAM_CODEC.encode(b, entry.output());
                b.writeVarInt(entry.ingredients().size()); for (ItemStack ingredient : entry.ingredients()) ItemStack.OPTIONAL_STREAM_CODEC.encode(b, ingredient);
            }
        }, b -> {
            int menu = b.readVarInt(), page = b.readVarInt(), total = b.readVarInt(); ItemStack taught = ItemStack.OPTIONAL_STREAM_CODEC.decode(b); String status = b.readUtf(160);
            int count = b.readVarInt(); if (count < 0 || count > RecipeTomeMenu.PAGE_SIZE) throw new IllegalArgumentException("Invalid tome page");
            List<RecipeTomeMenu.Entry> entries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Identifier recipe = b.readIdentifier(), type = b.readIdentifier(); ItemStack output = ItemStack.STREAM_CODEC.decode(b);
                int inputs = b.readVarInt(); if (inputs < 0 || inputs > 9) throw new IllegalArgumentException("Invalid tome recipe preview");
                List<ItemStack> ingredients = new ArrayList<>(); for (int slot = 0; slot < inputs; slot++) ingredients.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(b));
                entries.add(new RecipeTomeMenu.Entry(recipe, type, output, List.copyOf(ingredients)));
            }
            return new Page(menu, page, total, taught, status, List.copyOf(entries));
        });
        @Override public Type<Page> type() { return TYPE; }
    }
    public static void setup(IEventBus bus) { register(new RegisterPayloadHandlersEvent()); }
    private static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Action.TYPE, Action.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof RecipeTomeMenu menu && menu.containerId == payload.menu()) menu.action(payload);
        });
        registrar.playToClient(Page.TYPE, Page.CODEC, (payload, context) -> pageReceiver.accept(payload));
    }
    public static void send(ServerPlayer player, Page page) { PacketDistributor.sendToPlayer(player, page); }
    private RecipeTomePackets() {}
}
