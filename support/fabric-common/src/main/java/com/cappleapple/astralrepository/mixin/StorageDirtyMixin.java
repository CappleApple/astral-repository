package com.cappleapple.astralrepository.mixin;
import com.cappleapple.astralrepository.network.NetworkManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BlockEntity.class)
public abstract class StorageDirtyMixin {
    @Inject(method="setChanged()V",at=@At("TAIL"))
    private void astral$changed(CallbackInfo ci){
        BlockEntity self=(BlockEntity)(Object)this;
        if(self.getLevel() instanceof ServerLevel level)NetworkManager.providerChanged(level,self.getBlockPos());
    }
}
