package com.cappleapple.astralrepository.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.ChunkEvent;

/** Old mineral states had no block-entity NBT. Repair only already loaded matching sections. */
@EventBusSubscriber(modid = AstralContent.MOD_ID)
public final class AstralMineralMigration {
    @SubscribeEvent public static void loaded(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        if (chunk.getLevel() instanceof ServerLevel level) {
            level.getServer().execute(() -> {
                if (level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) == chunk) repair(chunk);
            });
        } else if (chunk.getLevel().isClientSide) {
            // ClientChunkCache posts Load after installing packet sections, before render snapshots are built.
            repair(chunk);
        }
    }

    /** Server/client owner thread only. Never obtains a neighboring chunk. */
    public static int repair(LevelChunk chunk) {
        int created = 0;
        var sections = chunk.getSections();
        var pos = new BlockPos.MutableBlockPos();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            var section = sections[sectionIndex];
            if (!section.maybeHas(AstralMineralMigration::natural)) continue;
            int baseY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                BlockState state = section.getBlockState(x, y, z);
                if (!natural(state)) continue;
                pos.set(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                if (chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) != null) continue;
                if (chunk.getBlockEntity(pos.immutable(), LevelChunk.EntityCreationType.IMMEDIATE) instanceof AstralMineralBlockEntity) {
                    created++;
                    if (chunk.getLevel().isClientSide) chunk.getLevel().sendBlockUpdated(pos.immutable(), state, state, 2);
                }
            }
        }
        if (created > 0 && !chunk.getLevel().isClientSide) chunk.setUnsaved(true);
        return created;
    }

    private static boolean natural(BlockState state) { return state.getBlock() instanceof AstralMineralBlock || state.getBlock() instanceof AstralClusterBlock; }
    private AstralMineralMigration() {}
}
