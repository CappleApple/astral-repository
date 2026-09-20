package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.network.NetworkPackets;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class NexusRequestGameTests {
    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void identicalWindowsStillAcknowledgeTheLatestRequestThroughTheWire(GameTestHelper helper) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var request = new NetworkPackets.Action(91, NetworkPackets.SEARCH, ItemStack.EMPTY, 0, 3, "iron", 3);
            NetworkPackets.Action.CODEC.encode(buffer, request);
            var decodedRequest = NetworkPackets.Action.CODEC.decode(buffer);
            helper.assertTrue(decodedRequest.request() == 3 && decodedRequest.row() == 3 && decodedRequest.query().equals("iron"), "The query and its request identity survive the action codec");
            buffer.clear();
            var rows = List.of(new NexusMenu.Entry(new ItemStack(Items.IRON_INGOT), 12, false));
            var first = new NetworkPackets.Page(91, 1, 0, 1, "", rows, List.of(), 1);
            NetworkPackets.Page.CODEC.encode(buffer, first);
            var baseline = NetworkPackets.Page.CODEC.decode(buffer);
            helper.assertTrue(baseline.request() == 1, "A full page echoes its acknowledged query");
            buffer.clear();
            var next = new NetworkPackets.Page(91, 2, 0, 1, "", rows, List.of(), 3);
            var update = NetworkPackets.difference(baseline, next, false);
            helper.assertTrue(update instanceof NetworkPackets.Delta, "Coalesced requests receive an acknowledgement even when the clamped window is unchanged");
            var delta = (NetworkPackets.Delta) update;
            helper.assertTrue(delta.counts().isEmpty() && delta.jobChanges().isEmpty() && delta.jobOrder() == null && delta.error() == null, "An acknowledgement does not resend inventory or job data");
            NetworkPackets.Delta.CODEC.encode(buffer, delta);
            var decodedDelta = NetworkPackets.Delta.CODEC.decode(buffer);
            var acknowledged = NetworkPackets.reconstruct(baseline, decodedDelta);
            helper.assertTrue(acknowledged != null && acknowledged.request() == 3 && acknowledged.revision() == 2 && acknowledged.entries().get(0).count() == 12, "Delta reconstruction advances request identity without changing the resource");
            helper.assertTrue(NetworkPackets.difference(acknowledged, new NetworkPackets.Page(91, 3, 0, 1, "", rows, List.of(), 3), false) == null, "After the acknowledgement, idle broadcasts send nothing");
            helper.succeed();
        } finally { buffer.release(); }
    }
    private NexusRequestGameTests() {}
}
