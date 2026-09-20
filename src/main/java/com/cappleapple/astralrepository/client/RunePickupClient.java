package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.network.RunePickupPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import com.cappleapple.astralrepository.platform.client.event.ClientTickEvent;
import com.cappleapple.astralrepository.platform.client.event.InputEvent;

import com.cappleapple.astralrepository.platform.network.PacketDistributor;

/** A consumed attack stays consumed until release, including after the last glyph disappears. */
public final class RunePickupClient {
    private static boolean attackSeen;
    private static boolean consumed;
    private static Object level;
    public static long pickupRequests;
    private RunePickupClient() {}
    public static void setup() {
        // Input interception is registered in FabricClient and FabricAttackMixin.
    }
    public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
        if (level != minecraft.level) {
            attackSeen = false;
            consumed = false;
            level = minecraft.level;
        }
        boolean first = !attackSeen;
        attackSeen = true;
        if (!consumed && minecraft.player.isShiftKeyDown() && minecraft.hitResult instanceof BlockHitResult hit) {
            RuneRenderer.Hover selected = RuneRenderer.hover(hit);
            if (selected != null) {
                consumed = true;
                if (first) {
                    PacketDistributor.sendToServer(new RunePickupPackets.Pickup(selected.face().pos(), selected.face().face(), selected.layer().id()));
                    pickupRequests++;
                    minecraft.player.swing(InteractionHand.MAIN_HAND);
                }
                if (minecraft.gameMode != null) minecraft.gameMode.stopDestroyBlock();
            }
        }
        if (consumed) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (level != minecraft.level || minecraft.player == null || !minecraft.options.keyAttack.isDown()) {
            attackSeen = false;
            consumed = false;
        }
        level = minecraft.level;
    }
}