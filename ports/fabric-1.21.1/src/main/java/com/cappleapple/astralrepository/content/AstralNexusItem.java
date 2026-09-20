package com.cappleapple.astralrepository.content;

import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class AstralNexusItem extends AstralToolItem {
    public AstralNexusItem(Properties properties) { super(properties, ""); }
    public static GlobalPos bound(ItemStack stack) { return PhysicalProgramming.readBinding(stack, "BoundNexus"); }
    public static boolean attuned(ItemStack stack) { return PhysicalProgramming.hasRemoteAttunement(stack); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if(other.is(AstralContent.DIMENSIONAL_ATTUNEMENT.get()))DimensionalAttunementItem.attuneNexus(level,player,stack,other);
        else if(player instanceof ServerPlayer server)ContentHooks.remoteUse.accept(server, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var bound = bound(stack);
        if (bound != null) lines.add(Component.literal(bound.dimension().location() + " " + bound.pos().toShortString()));
        if (attuned(stack)) lines.add(Component.translatable("hint.astral_repository.dimensional"));
    }
    @Override public boolean isFoil(ItemStack stack) { return attuned(stack); }
}
