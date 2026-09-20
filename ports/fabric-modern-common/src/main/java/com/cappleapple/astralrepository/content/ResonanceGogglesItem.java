package com.cappleapple.astralrepository.content;
import net.minecraft.world.entity.EquipmentSlot;
/** Equippable data enables vanilla right-click head-slot swapping. */
public final class ResonanceGogglesItem extends AstralToolItem {
    public ResonanceGogglesItem(Properties properties) { super(properties.equippable(EquipmentSlot.HEAD), ""); }
}
