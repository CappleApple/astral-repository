package com.cappleapple.astralrepository.content;

import com.cappleapple.astralrepository.compat.TrinketsCompatibility;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/** Shared equipment check for client presentation and server-side visibility packets. */
public final class GogglesEquipment {
    public static boolean isWearing(LivingEntity entity) {
        return entity != null && (entity.getItemBySlot(EquipmentSlot.HEAD).is(AstralContent.RESONANCE_GOGGLES.get())
                || TrinketsCompatibility.isWearingGoggles(entity));
    }

    private GogglesEquipment() {}
}
