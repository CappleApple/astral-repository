package com.cappleapple.astralrepository.content;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;

public class AstralToolItem extends Item {
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){consumer.accept(com.cappleapple.astralrepository.client.AstralMineralClient.itemExtension());}

    public AstralToolItem(Properties properties, String hint) { super(properties); }
    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(stack.is(AstralContent.ATTUNEMENT_WAND.get())) {
            if(player.isShiftKeyDown()){
                var picked=player.pick(player.getBlockReach(),1,false);
                if(picked instanceof BlockHitResult hit&&picked.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK
                        &&RuneProgramming.use(stack,level,hit.getBlockPos(),player,hand,hit))return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
                if(RuneProgramming.selection(stack)!=null){
                    if(!level.isClientSide)RuneProgramming.cancel(stack);
                    return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
                }
            }
            if(player instanceof net.minecraft.server.level.ServerPlayer server)com.cappleapple.astralrepository.network.WandPackets.open(server);
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
        }
        return super.use(level,player,hand);
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null) return InteractionResult.PASS;
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
        return PhysicalProgramming.use(context.getItemInHand(), context.getLevel(), context.getClickedPos(), context.getPlayer(), context.getHand(), hit)
                ? InteractionResult.sidedSuccess(context.getLevel().isClientSide) : InteractionResult.PASS;
    }
    @Override public boolean isFoil(ItemStack stack) {
        var selection=RuneProgramming.selection(stack);
        return super.isFoil(stack)||(stack.is(AstralContent.ATTUNEMENT_WAND.get())&&selection!=null);
    }
}
