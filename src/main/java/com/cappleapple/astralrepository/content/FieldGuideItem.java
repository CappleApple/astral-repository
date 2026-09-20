package com.cappleapple.astralrepository.content;

import com.cappleapple.astralrepository.compat.PatchouliIntegration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Registered only with Patchouli; this item owns the Field Guide's identity. */
public final class FieldGuideItem extends Item {
    public FieldGuideItem(Properties properties) { super(properties); }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) PatchouliIntegration.open(serverPlayer);
        return com.cappleapple.astralrepository.port.Interactions.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
