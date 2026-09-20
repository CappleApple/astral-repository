package com.cappleapple.astralrepository.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Recognizes the persistent table by registry identity without loading optional mod classes. */
public final class VisualWorkbenchCompatibility {
    private static final ResourceLocation TABLE_ENTITY=new ResourceLocation("visualworkbench","crafting_table");
    private VisualWorkbenchCompatibility() {}

    public static boolean isPersistentTable(ServerLevel level,BlockPos pos) {
        if(!level.hasChunkAt(pos)||!level.getBlockState(pos).hasBlockEntity())return false;
        var entity=level.getBlockEntity(pos);
        return entity!=null&&TABLE_ENTITY.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()));
    }

    public static boolean isCraftingTable(ServerLevel level,BlockPos pos) {
        return level.hasChunkAt(pos)&&(level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)||isPersistentTable(level,pos));
    }
}
