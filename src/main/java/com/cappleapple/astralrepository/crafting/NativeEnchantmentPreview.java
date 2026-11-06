package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ItemKey;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import static com.cappleapple.astralrepository.crafting.NativeBackendReflection.*;

/** Symbolic native enchanting output plus the external-feed isolation required for job-owned essence. */
final class NativeEnchantmentPreview {
    private static final ClassValue<Boolean> USES_ENCHANTING=new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            return NativeBytecode.hierarchy(type).stream().flatMap(shape->shape.references().stream()).anyMatch(reference->
                    reference.owner().equals(net.minecraft.world.item.enchantment.EnchantmentHelper.class.getName()) && NativeMinecraftCalls.name(reference).equals("enchantItem"));
        }
    };
    static boolean usesNativeEnchanting(Class<?> type) { return USES_ENCHANTING.get(type); }
    private static final ClassValue<Boolean> USES_EXTERNAL_FEED=new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            var references=NativeBytecode.hierarchy(type).stream().flatMap(shape->shape.references().stream()).toList();
            boolean above=references.stream().anyMatch(reference->reference.owner().equals(BlockPos.class.getName()) && NativeMinecraftCalls.name(reference).equals("above"));
            boolean fluid=references.stream().anyMatch(reference->reference.staticRead() &&
                    (reference.owner().equals("com.cappleapple.astralrepository.platform.capabilities.Capabilities$FluidHandler") && reference.name().equals("BLOCK")
                    || reference.owner().equals("net.minecraftforge.common.capabilities.ForgeCapabilities") && reference.name().equals("FLUID_HANDLER")));
            return above && fluid;
        }
    };
    static boolean usesExternalFeed(Class<?> type) { return USES_EXTERNAL_FEED.get(type); }
    static boolean usesEnchantMutation(Class<?> type) {
        for(Class<?> current=type;current!=null && !current.getName().equals("com.buuz135.industrial.block.tile.IndustrialMachineTile");current=current.getSuperclass())
            for(var reference:NativeBytecode.inspect(current).references()) if(reference.owner().equals(net.minecraft.world.item.enchantment.EnchantmentHelper.class.getName())
                    && NativeMinecraftCalls.name(reference).equals("setEnchantments")) return true;
        return false;
    }
    static final class FeedGuard {
        final ServerLevel level; final BlockPos position; final BlockEntity owner; final BlockState state;
        FeedGuard(ServerLevel level,BlockPos machine) {
            this.level=level; position=machine.above(); boolean loaded=level.hasChunkAt(position); owner=loaded?level.getBlockEntity(position):null; state=loaded?level.getBlockState(position):null;
        }
        boolean valid() {
            if(!level.hasChunkAt(position) || level.getBlockEntity(position)!=owner || level.getBlockState(position)!=state) return false;
            try {
                Object handler=fluidHandler(level,position);
                if(handler==null) return true;
                for(int tank=0;tank<number(call(handler,"getTanks"),0);tank++) if(MekanismResourceSupport.amount(call(handler,"getFluidInTank",tank))>0) return false;
                return true;
            } catch(RuntimeException | LinkageError unsupported) { return false; }
        }
        Object catalogState() { return List.of(position,state==null?"unloaded":state,owner!=null,valid()); }
    }
    private final FeedGuard guard;
    private final ItemKey target;
    private final ServerLevel level;
    NativeEnchantmentPreview(ServerLevel level,FeedGuard guard,ItemKey target) { this.level=level; this.guard=guard; this.target=target; }
    ItemKey target() { return target; }
    ServerLevel level() { return level; }
    /** Model only a proven-empty native external feed; never inspect arbitrary world mechanics. */
    Object world(NativeBytecode.Reference reference,List<Object> arguments) {
        String name=NativeMinecraftCalls.name(reference);
        if(guard==null || !guard.valid()) throw new UnsupportedOperationException("Native enchanting external feed is unclassified, nonempty or changed");
        if(name.equals("getBlockEntity") && arguments.size()==1 && arguments.get(0).equals(guard.position)) return null;
        Class<?> capabilities=optionalClass("com.cappleapple.astralrepository.platform.capabilities.Capabilities$FluidHandler");
        if(name.equals("getCapability") && capabilities!=null && arguments.size()==3 && arguments.get(0)==field(capabilities,"BLOCK")
                && arguments.get(1).equals(guard.position) && arguments.get(2)==Direction.DOWN) return null;
        throw new UnsupportedOperationException("Unclassified native enchanting world query: " + reference.owner() + "." + name);
    }
    ItemStack enchant(List<Object> arguments) {
        if(target==null) throw new UnsupportedOperationException("Native random enchanting requires an exact requested/taught target");
        if(arguments.size()<4 || !(arguments.get(1) instanceof ItemStack input) || !(arguments.get(2) instanceof Number power))
            throw new UnsupportedOperationException("Unknown native enchantment invocation shape");
        Object options=arguments.get(arguments.size() - 1);
        if(arguments.size()==4 && options instanceof Number number && (number.intValue()==0 || number.intValue()==1)) options=number.intValue()==1;
        var match=NativeEnchantmentTargets.test(level,input,power.intValue(),target.sample(),options);
        if(!match.supported() || !match.possible()) throw new UnsupportedOperationException("Exact native enchanting target: " + match.reason());
        return target.sample();
    }
    private static Object fluidHandler(ServerLevel level,BlockPos position) {
        Class<?> neo=optionalClass("com.cappleapple.astralrepository.platform.capabilities.Capabilities$FluidHandler");
        if(neo!=null) return call(level,"getCapability",field(neo,"BLOCK"),position,Direction.DOWN);
        Class<?> forge=optionalClass("net.minecraftforge.common.capabilities.ForgeCapabilities");
        BlockEntity owner=level.getBlockEntity(position);
        if(forge==null || owner==null) return null;
        Object optional=call(owner,"getCapability",field(forge,"FLUID_HANDLER"),Direction.DOWN);
        return optional==null?null:call(optional,"orElse",(Object)null);
    }
}
