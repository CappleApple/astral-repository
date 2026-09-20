package com.cappleapple.astralrepository.content;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** The same consumable upgrades long-range relays and the handheld Nexus orb. */
public final class DimensionalAttunementItem extends AstralToolItem {
    public DimensionalAttunementItem(Properties properties){super(properties,"");}

    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack upgrade=player.getItemInHand(hand);
        ItemStack other=player.getItemInHand(hand==InteractionHand.MAIN_HAND?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND);
        if(!other.is(AstralContent.ASTRAL_NEXUS.get()))return InteractionResultHolder.pass(upgrade);
        attuneNexus(level,player,other,upgrade);
        return InteractionResultHolder.sidedSuccess(upgrade,level.isClientSide);
    }

    public static boolean attuneNexus(Level level,Player player,ItemStack nexus,ItemStack upgrade){
        if(!nexus.is(AstralContent.ASTRAL_NEXUS.get())||!upgrade.is(AstralContent.DIMENSIONAL_ATTUNEMENT.get())||AstralNexusItem.attuned(nexus))return false;
        if(!level.isClientSide){
            var data=nexus.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            data.putBoolean("DimensionalRemote",true);
            nexus.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
            if(!player.isCreative())upgrade.shrink(1);
        }
        return true;
    }
}
