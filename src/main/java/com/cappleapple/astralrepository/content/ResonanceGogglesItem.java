package com.cappleapple.astralrepository.content;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class ResonanceGogglesItem extends AstralToolItem implements Equipable {
    public ResonanceGogglesItem(Properties properties) { super(properties, ""); }
    @Override public EquipmentSlot getEquipmentSlot() { return EquipmentSlot.HEAD; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) { return swapWithEquipmentSlot(this, level, player, hand); }
}
