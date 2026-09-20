package com.cappleapple.astralrepository.platform.capabilities;
import java.util.function.BooleanSupplier;import net.minecraft.server.level.ServerLevel;import net.minecraft.core.BlockPos;
/** Revalidates native lookup identity before any retained provider performs work. */
public final class BlockCapabilityCache<T,C> {
 private final BlockCapability<T,C> type;private final ServerLevel level;private final BlockPos pos;private final C context;private final BooleanSupplier ownerValid;private final Runnable invalidate;private Object initial;private boolean captured,invalid;
 private BlockCapabilityCache(BlockCapability<T,C> type,ServerLevel level,BlockPos pos,C context,BooleanSupplier ownerValid,Runnable invalidate){this.type=type;this.level=level;this.pos=pos.immutable();this.context=context;this.ownerValid=ownerValid;this.invalidate=invalidate;}
 public static <T,C> BlockCapabilityCache<T,C> create(BlockCapability<T,C> type,ServerLevel level,BlockPos pos,C context,BooleanSupplier valid,Runnable invalidate){return new BlockCapabilityCache<>(type,level,pos,context,valid,invalidate);}
 public T getCapability(){T result=type==null?null:type.find(level,pos,context);if(!captured){initial=FabricTransfer.identity(result);captured=true;}return result;}
 public boolean isValid(){if(invalid||!ownerValid.getAsBoolean())return false;Object current=FabricTransfer.identity(type==null?null:type.find(level,pos,context));if(captured&&current!=initial){invalid=true;invalidate.run();return false;}return true;}
}
