package com.cappleapple.astralrepository.platform.common;
import com.cappleapple.astralrepository.platform.event.entity.player.PlayerInteractEvent;import net.minecraft.server.level.ServerPlayer;import net.minecraft.core.*;import net.minecraft.world.*;import net.minecraft.world.phys.*;
public final class CommonHooks {
 public static PlayerInteractEvent.LeftClickBlock onLeftClickBlock(ServerPlayer player,BlockPos pos,Direction face,net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action action){var event=new PlayerInteractEvent.LeftClickBlock(player,player.level(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),face,pos,false));var result=net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.invoker().interact(player,player.level(),InteractionHand.MAIN_HAND,pos,face);event.setCanceled(result==InteractionResult.FAIL);return event;}
 public static void setCraftingPlayer(net.minecraft.world.entity.player.Player player){/* Vanilla crafting invokes recipe events through the active player. */}
}
