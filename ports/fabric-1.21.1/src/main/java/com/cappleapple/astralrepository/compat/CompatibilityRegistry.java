package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.api.*;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.HashCommon;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import com.cappleapple.astralrepository.platform.ModList;
import com.cappleapple.astralrepository.platform.capabilities.BlockCapability;
import com.cappleapple.astralrepository.platform.capabilities.BlockCapabilityCache;
import com.cappleapple.astralrepository.platform.capabilities.Capabilities;

/** Registration and bounded, loaded-world-only discovery. Register adapters during common setup. */
public final class CompatibilityRegistry {
    @FunctionalInterface public interface Factory<T> { List<T> discover(ServerLevel level, BlockPos pos, Direction side); }
    private record Adapter<T>(String id, Factory<T> factory) { }
    private static final List<Adapter<StorageProvider>> STORAGE = new ArrayList<>();
    private static final List<Adapter<ResourceProvider>> RESOURCES = new ArrayList<>();
    private static final List<Adapter<CraftingProvider>> CRAFTING = new ArrayList<>();
    private static final List<Adapter<NetworkPowerProvider>> POWER = new ArrayList<>();
    private static final Map<ServerLevel,Map<String,Long>> QUARANTINE = new WeakHashMap<>();
    private CompatibilityRegistry() { }
    public static void registerStorage(String id, Factory<StorageProvider> adapter) { add(STORAGE,id,adapter); }
    public static void registerResources(String id, Factory<ResourceProvider> adapter) { add(RESOURCES,id,adapter); }
    public static void registerCrafting(String id, Factory<CraftingProvider> adapter) { add(CRAFTING,id,adapter); }
    public static void registerPower(String id, Factory<NetworkPowerProvider> adapter) { add(POWER,id,adapter); }
    private static <T> void add(List<Adapter<T>> adapters,String id,Factory<T> factory) {
        if (adapters.stream().anyMatch(adapter -> adapter.id.equals(id))) throw new IllegalArgumentException("Duplicate adapter " + id);
        adapters.add(new Adapter<>(Objects.requireNonNull(id),Objects.requireNonNull(factory)));
    }
    public static List<StorageProvider> discoverStorage(ServerLevel level,BlockPos pos,Direction side) {
        // This is a player's persistent crafting grid, not general storage. Treating it as
        // a provider could consume an unfinished recipe or deposit unrelated network items.
        if (!level.hasChunkAt(pos) || VisualWorkbenchCompatibility.isPersistentTable(level,pos)) return List.of();
        List<StorageProvider> result=new ArrayList<>();
        attempt(level,pos,"items",() -> {
            var tracked=track(Capabilities.ItemHandler.BLOCK,level,pos,side);
            var handler=tracked.handler;
            if (handler != null) result.add(ProviderGuard.storage(new ItemHandlerStorageProvider(
                    location("items",level,pos,side),physicalIdentity(level,pos),handler,tracked::valid)));
            return null;
        });
        if (loaded("ae2",CompatConfig.appliedEnergistics2.get())) attempt(level,pos,"ae2",() -> {
            var tracked=trackOptional(level,pos,OptionalApi.field("appeng.api.AECapabilities","ME_STORAGE"),side);
            Object storage=tracked.handler;
            Object grid=storage==null?null:aeGrid(level,pos,side);
            if (storage != null && grid != null) {
                result.add(ProviderGuard.storage(new Ae2StorageProvider(location("ae2",level,pos,side),storage,storage,
                        OptionalApi.call(grid,"appeng.api.networking.IGrid","getEnergyService"),
                        () -> tracked.valid() && grid==aeGrid(level,pos,side))));
            }
            return null;
        });
        if (loaded("refinedstorage",CompatConfig.refinedStorage.get())) attempt(level,pos,"refinedstorage",() -> {
            for (Object node:rsNodes(level,pos)) {
                Object network=OptionalApi.call(node,"com.refinedmods.refinedstorage.api.network.node.NetworkNode","getNetwork");
                if (network==null) continue;
                Object storage=rsComponent(network,"com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent");
                BooleanSupplier physical=validity(level,pos);
                if (storage != null) result.add(ProviderGuard.storage(new RefinedStorageProvider(
                        location("refinedstorage",level,pos,side),network,storage,() -> physical.getAsBoolean()
                        && network==OptionalApi.call(node,"com.refinedmods.refinedstorage.api.network.node.NetworkNode","getNetwork"))));
            }
            return null;
        });
        for (Adapter<StorageProvider> adapter:List.copyOf(STORAGE))
            attempt(level,pos,adapter.id,() -> { adapter.factory.discover(level,pos,side).forEach(p -> result.add(ProviderGuard.storage(p))); return null; });
        return distinctStorage(result).stream().map(p->com.cappleapple.astralrepository.content.ContainerRuneRules.wrap(level,pos.immutable(),p)).toList();
    }
    public static List<ResourceProvider> discoverResources(ServerLevel level,BlockPos pos,Direction side) {
        if (!level.hasChunkAt(pos)) return List.of();
        List<ResourceProvider> result=new ArrayList<>();
        attempt(level,pos,"fluids",() -> {
            var tracked=track(Capabilities.FluidHandler.BLOCK,level,pos,side);
            var handler=tracked.handler;
            if (handler != null) result.add(ProviderGuard.resource(new FluidResourceProvider(
                    location("fluids",level,pos,side),physicalIdentity(level,pos),handler,tracked::valid)));
            return null;
        });
        attempt(level,pos,"energy",() -> {
            var tracked=track(Capabilities.EnergyStorage.BLOCK,level,pos,side);
            var handler=tracked.handler;
            if (handler != null) result.add(ProviderGuard.resource(new EnergyResourceProvider(
                    location("energy",level,pos,side),physicalIdentity(level,pos),handler,tracked::valid)));
            return null;
        });
        if (loaded("ars_nouveau",CompatConfig.arsNouveau.get())) attempt(level,pos,"ars_nouveau",() -> {
            BlockEntity tile=level.getBlockEntity(pos);
            if (ArsSourceProvider.supports(tile)) result.add(ProviderGuard.resource(new ArsSourceProvider(level,tile)));
            return null;
        });
        for (Adapter<ResourceProvider> adapter:List.copyOf(RESOURCES))
            attempt(level,pos,adapter.id,() -> { adapter.factory.discover(level,pos,side).forEach(p -> result.add(ProviderGuard.resource(p))); return null; });
        return result.stream().map(p->com.cappleapple.astralrepository.content.ContainerRuneRules.wrap(level,pos.immutable(),p)).toList();
    }
    public static List<CraftingProvider> discoverCrafting(ServerLevel level,BlockPos pos,Direction side) {
        if (!level.hasChunkAt(pos)) return List.of();
        List<CraftingProvider> result=new ArrayList<>();
        if (loaded("ae2",CompatConfig.appliedEnergistics2.get())) attempt(level,pos,"ae2_crafting",() -> {
            Object storage=capability(level,pos,OptionalApi.field("appeng.api.AECapabilities","ME_STORAGE"),side);
            Object grid=storage==null?null:aeGrid(level,pos,side);
            if(grid!=null && storage==aeInventory(grid)) {
                BooleanSupplier physical=validity(level,pos);
                result.add(ProviderGuard.crafting(new Ae2CraftingProvider(level,location("ae2_crafting",level,pos,side),grid,
                        () -> physical.getAsBoolean() && grid==aeGrid(level,pos,side))));
            }
            return null;
        });
        if (loaded("refinedstorage",CompatConfig.refinedStorage.get())) attempt(level,pos,"refinedstorage_crafting",() -> {
            for(Object node:rsNodes(level,pos)) {
                Object network=OptionalApi.call(node,"com.refinedmods.refinedstorage.api.network.node.NetworkNode","getNetwork");
                if(network==null) continue;
                BooleanSupplier physical=validity(level,pos);
                result.add(ProviderGuard.crafting(new RefinedCraftingProvider(location("refinedstorage_crafting",level,pos,side),network,
                        () -> physical.getAsBoolean() && network==OptionalApi.call(node,
                                "com.refinedmods.refinedstorage.api.network.node.NetworkNode","getNetwork"))));
            }
            return null;
        });
        for (Adapter<CraftingProvider> adapter:List.copyOf(CRAFTING))
            attempt(level,pos,adapter.id,() -> { adapter.factory.discover(level,pos,side).forEach(p -> result.add(ProviderGuard.crafting(p))); return null; });
        return List.copyOf(result);
    }
    public static List<NetworkPowerProvider> discoverPower(ServerLevel level,BlockPos pos,Direction side) {
        if (!level.hasChunkAt(pos)) return List.of();
        List<NetworkPowerProvider> result=new ArrayList<>();
        if (loaded("create",CompatConfig.create.get())) attempt(level,pos,"create_stress",() -> {
            BlockEntity tile=level.getBlockEntity(pos);
            if (CreateStressProvider.supports(tile)) result.add(guardPower(new CreateStressProvider(level,tile)));
            return null;
        });
        for (Adapter<NetworkPowerProvider> adapter:List.copyOf(POWER))
            attempt(level,pos,adapter.id,() -> { adapter.factory.discover(level,pos,side).forEach(p -> result.add(guardPower(p))); return null; });
        return List.copyOf(result);
    }
    private static NetworkPowerProvider guardPower(NetworkPowerProvider backend) {
        ProviderGuard guard=new ProviderGuard(backend.id());
        return new NetworkPowerProvider() {
            public String id() { return backend.id(); }
            public Object identity() { return backend.identity(); }
            public net.minecraft.resources.ResourceLocation resourceType() { return backend.resourceType(); }
            public Mode mode() { return backend.mode(); }
            public boolean valid() { return guard.read(backend::valid,false); }
            public double available() { return guard.read(backend::available,0D); }
            public double acquire(double amount,boolean simulate) { return guard.transfer(() -> backend.acquire(amount,simulate),0D,simulate); }
            public void release(double amount) { backend.release(amount); }
        };
    }
    private static final class TrackedCapability<T,C> {
        private final BlockCapabilityCache<T,C> cache;
        private final BooleanSupplier physical;
        private boolean invalidated;
        final T handler;
        TrackedCapability(BlockCapability<T,C> capability,ServerLevel level,BlockPos pos,C context) {
            physical=validity(level,pos);
            cache=BlockCapabilityCache.create(capability,level,pos,context,() -> !invalidated,() -> invalidated=true);
            handler=cache.getCapability();
        }
        boolean valid() { return !invalidated && physical.getAsBoolean() && cache.isValid(); }
    }
    private static <T,C> TrackedCapability<T,C> track(BlockCapability<T,C> capability,ServerLevel level,BlockPos pos,C context) {
        return new TrackedCapability<>(capability,level,pos,context);
    }
    private static boolean loaded(String mod,boolean enabled) { return enabled && ModList.get().isLoaded(mod); }
    private static <T> T attempt(ServerLevel level,BlockPos pos,String adapter,Supplier<T> work) {
        String key=adapter+":"+pos.asLong();
        Map<String,Long> failures=QUARANTINE.computeIfAbsent(level,ignored -> new HashMap<>());
        long now=level.getGameTime();
        Long retry=failures.get(key);
        if (retry != null && now < retry) return null;
        try { T result=work.get(); failures.remove(key); return result; }
        catch (RuntimeException | LinkageError failure) {
            failures.put(key,now+1200);
            LogUtils.getLogger().error("Astral Repository adapter {} failed at {} {}; retry after 1200 ticks",adapter,level.dimension().location(),pos,failure);
            return null;
        }
    }
    static String location(String kind,ServerLevel level,BlockPos pos,Direction side) {
        return kind+":"+level.dimension().location()+":"+pos.asLong()+":"+(side==null?"all":side.getName());
    }
    static BooleanSupplier validity(ServerLevel level,BlockPos pos) {
        BlockPos immutable=pos.immutable();
        var chunk=level.getChunkSource().getChunkNow(immutable.getX()>>4,immutable.getZ()>>4);
        if(chunk==null)return ()->false;
        BlockEntity tile=chunk.getBlockEntity(immutable);
        BlockState state=chunk.getBlockState(immutable);
        return () -> {
            // The retained chunk's supplier follows live ticket demotion before delayed unload.
            if(!chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)
                    ||chunk.getBlockState(immutable)!=state)return false;
            // Vanilla replacement and unload retire the old instance synchronously. Tile-less
            // capabilities still need a live lookup so adding a block entity invalidates the view.
            return tile!=null?tile.getLevel()==level&&!tile.isRemoved():chunk.getBlockEntity(immutable)==null;
        };
    }
    private record PhysicalIdentity(String dimension,long first,long second) {
        @Override public int hashCode() {
            // Single containers repeat the packed position; the record hash otherwise loses five bits.
            long mixed=HashCommon.mix(first+Long.rotateLeft(second,21));
            return (int)(mixed^(mixed>>>32))^dimension.hashCode();
        }
    }
    private static Object physicalIdentity(ServerLevel level,BlockPos pos) {
        long first=pos.asLong(), second=first;
        BlockState state=level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE) {
            BlockPos other=pos.relative(ChestBlock.getConnectedDirection(state));
            if (level.hasChunkAt(other)) second=other.asLong();
        }
        return new PhysicalIdentity(level.dimension().location().toString(),Math.min(first,second),Math.max(first,second));
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static Object capability(ServerLevel level,BlockPos pos,Object capability,Direction side) {
        return com.cappleapple.astralrepository.platform.capabilities.Capabilities.find(level,(BlockCapability)capability,pos,side);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static TrackedCapability<Object,Object> trackOptional(ServerLevel level,BlockPos pos,Object capability,Object context) {
        return track((BlockCapability)capability,level,pos,context);
    }
    private static Object aeInventory(Object grid) {
        Object service=OptionalApi.call(grid,"appeng.api.networking.IGrid","getStorageService");
        return OptionalApi.call(service,"appeng.api.networking.storage.IStorageService","getInventory");
    }
    static Object aeGrid(ServerLevel level,BlockPos pos,Direction side) {
        Object host=capability(level,pos,OptionalApi.field("appeng.api.AECapabilities","IN_WORLD_GRID_NODE_HOST"),null);
        if (host==null) return null;
        Direction[] sides=side==null?Direction.values():new Direction[]{side};
        for (Direction direction:sides) {
            Object node=OptionalApi.call(host,"appeng.api.networking.IInWorldGridNodeHost","getGridNode",new Class<?>[]{Direction.class},direction);
            if (node!=null && (Boolean)OptionalApi.call(node,"appeng.api.networking.IGridNode","isActive"))
                return OptionalApi.call(node,"appeng.api.networking.IGridNode","getGrid");
        }
        return null;
    }
    static List<Object> rsNodes(ServerLevel level,BlockPos pos) {
        BlockEntity tile=level.getBlockEntity(pos);
        String host="com.refinedmods.refinedstorage.common.api.support.network.AbstractNetworkNodeContainerBlockEntity";
        if (tile==null || !OptionalApi.type(host).isInstance(tile)) return List.of();
        Object provider=OptionalApi.call(tile,host,"getContainerProvider");
        Iterable<?> containers=(Iterable<?>)OptionalApi.call(provider,
                "com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider","getContainers");
        List<Object> nodes=new ArrayList<>();
        for(Object container:containers) nodes.add(OptionalApi.call(container,
                "com.refinedmods.refinedstorage.api.network.node.container.NetworkNodeContainer","getNode"));
        return nodes;
    }
    static Object rsComponent(Object network,String type) {
        return OptionalApi.call(network,"com.refinedmods.refinedstorage.api.core.component.ComponentAccessor",
                "getComponent",new Class<?>[]{Class.class},OptionalApi.type(type));
    }
    private static List<StorageProvider> distinctStorage(List<StorageProvider> providers) {
        Map<Object,StorageProvider> unique=new LinkedHashMap<>();
        for(StorageProvider provider:providers) unique.putIfAbsent(provider.identity(),provider);
        return List.copyOf(unique.values());
    }
}