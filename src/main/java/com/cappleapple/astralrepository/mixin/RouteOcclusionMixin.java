package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.network.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers player edits, pistons, commands, explosions, and other mods' ordinary block changes. */
@Mixin(LevelChunk.class)
public abstract class RouteOcclusionMixin {
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void astral$visibility(BlockPos pos,BlockState state,boolean moving,CallbackInfoReturnable<BlockState> ci){
        BlockState old=ci.getReturnValue();
        if(old==null||old==state||!(old.canOcclude()||state.canOcclude()))return;
        if(((LevelChunk)(Object)this).getLevel() instanceof ServerLevel level){
            // Lighting a furnace or changing another full cube's visual state does not change sight.
            // Identity equality is deliberately conservative for modded, context-dependent shapes.
            if(old.getBlock()==state.getBlock()&&!state.getBlock().hasDynamicShape()&&old.canOcclude()&&state.canOcclude()
                    &&old.getOcclusionShape(level,pos)==state.getOcclusionShape(level,pos))return;
            NetworkManager.visibilityChanged(level,pos);
        }
    }
}
