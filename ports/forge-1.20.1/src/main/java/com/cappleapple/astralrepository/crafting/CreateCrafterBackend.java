package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.compat.CompatConfig;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.cappleapple.astralrepository.platform.Capabilities;
import static com.cappleapple.astralrepository.crafting.NativeBackendReflection.*;

/** The native crafter graph supplies layout, grouping, inventory and execution; no output is assembled here. */
public final class CreateCrafterBackend implements NativeMachineBackend {
    private static final String CRAFTER = "com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity";
    private static final String GRID = "com.simibubi.create.content.kinetics.crafter.RecipeGridHandler$GroupedItems";
    private static final String INPUT = "com.simibubi.create.content.kinetics.crafter.MechanicalCraftingInput";
    private record Cell(BlockEntity tile, int x, int y, boolean covered) {}
    private record Slot(int x, int y) {}
    private record Bound(NativeRecipeSource.Entry recipe, List<Object> layout, Map<Slot, CraftRecipe.Ingredient<ResourceKey>> inputs) {}
    private final Map<String, String> diagnostics = new LinkedHashMap<>();
    public Map<String, String> diagnostics() { return Map.copyOf(diagnostics); }
    public String id() { return "create"; }
    public Object identityAt(ServerLevel level, BlockPos pos) { var host = at(level, pos); return host == null ? null : host.identity(); }
    public Machine machine(CraftingService.NetworkAccess access, GlobalPos pos) { return at(access.level().getServer().getLevel(pos.dimension()), pos.pos()); }
    private static Host at(ServerLevel level, BlockPos pos) {
        if (!CompatConfig.create.get() || level == null || !level.hasChunkAt(pos)) return null;
        var first = level.getBlockEntity(pos); if (!contract(first, CRAFTER)) return null;
        Map<BlockPos, BlockEntity> tiles = new LinkedHashMap<>(); ArrayDeque<BlockEntity> queue = new ArrayDeque<>(); queue.add(first);
        while (!queue.isEmpty()) {
            BlockEntity tile = queue.removeFirst(); if (tiles.putIfAbsent(tile.getBlockPos(), tile) != null) continue;
            if (tiles.size() > 64) return null;
            for (Direction side : Direction.values()) {
                BlockPos adjacent = tile.getBlockPos().relative(side); if (!level.hasChunkAt(adjacent)) return null;
                BlockEntity other = level.getBlockEntity(adjacent);
                if (!contract(other, CRAFTER) || !Objects.equals(facing(tile), facing(other))) continue;
                if (target(tile).equals(adjacent) || target(other).equals(tile.getBlockPos())) queue.addLast(other);
            }
        }
        List<BlockEntity> terminals = tiles.values().stream().filter(tile -> !tiles.containsKey(target(tile))).toList();
        if (terminals.size() != 1) return null;
        return new Host(level, terminals.get(0), List.copyOf(tiles.values()));
    }
    private static Object facing(BlockEntity tile) { for (var p : tile.getBlockState().getProperties()) if (p.getName().equals("facing")) return tile.getBlockState().getValue(p); return null; }
    private static Object pointing(BlockEntity tile) { for (var p : tile.getBlockState().getProperties()) if (p.getName().equals("pointing")) return tile.getBlockState().getValue(p); return null; }
    private static BlockPos target(BlockEntity tile) { return tile.getBlockPos().relative((Direction)call(tile, "getTargetDirection")); }
    private static Object group(ItemStack stack) {
        try { return optionalClass(GRID).getConstructor(ItemStack.class).newInstance(stack); }
        catch (ReflectiveOperationException unavailable) { throw new IllegalArgumentException("Native crafter grouped-item contract unavailable", unavailable); }
    }
    private static Object nativeInput(Object group) {
        Class<?> modern = optionalClass(INPUT); if (modern != null) return call(modern, "of", group);
        Class<?> api = optionalClass("com.simibubi.create.content.kinetics.crafter.RecipeGridHandler");
        for (var method : NativeBytecode.inspect(api).methods()) if (method.name().equals("tryToApplyRecipe"))
            for (var reference : method.references()) if (reference.name().equals("<init>") && reference.owner().startsWith("com.simibubi.create.")) try {
                Class<?> owner = optionalClass(reference.owner());
                var constructor = owner.getDeclaredConstructor(group.getClass()); constructor.setAccessible(true);
                return constructor.newInstance(group);
            } catch (ReflectiveOperationException unrelated) { /* Only the actual native grouped-grid input constructor applies. */ }
        throw new IllegalArgumentException("Native grouped crafting-input contract unavailable");
    }
    @SuppressWarnings("unchecked") private static Map<Object, ItemStack> grid(Object group) { return (Map<Object, ItemStack>)field(group, "grid"); }
    /** Native grouping is run only on detached item maps; no machine lifecycle or crafting assembly is invoked. */
    private static Object grouped(Host host, Map<BlockPos, ItemStack> values) {
        Map<BlockPos, Object> groups = new HashMap<>(); Set<BlockPos> done = new HashSet<>();
        host.tiles.forEach(tile -> groups.put(tile.getBlockPos(), group(values.getOrDefault(tile.getBlockPos(), ItemStack.EMPTY))));
        for (int round = 0; round < host.tiles.size(); round++) for (BlockEntity tile : host.tiles) {
            BlockPos pos = tile.getBlockPos(); if (done.contains(pos)) continue;
            if (host.tiles.stream().anyMatch(other -> target(other).equals(pos) && !done.contains(other.getBlockPos()))) continue;
            BlockPos target = target(tile);
            if (groups.containsKey(target)) call(groups.get(pos), "mergeOnto", groups.get(target), pointing(tile));
            done.add(pos);
        }
        if (done.size() != host.tiles.size()) throw new IllegalArgumentException("Native crafter graph contains a cycle");
        Object result = groups.get(host.terminal.getBlockPos()); call(result, "calcStats"); return result;
    }
    private static List<Cell> cells(Host host) {
        Map<BlockPos, ItemStack> markers = new HashMap<>(); Map<ItemStack, BlockEntity> owners = new IdentityHashMap<>();
        for (var tile : host.tiles) { ItemStack marker = new ItemStack(Items.STICK); markers.put(tile.getBlockPos(), marker); owners.put(marker, tile); }
        Object result = grouped(host, markers); List<Cell> cells = new ArrayList<>();
        int minX = (int)number(field(result, "minX"), 0), maxY = (int)number(field(result, "maxY"), 0);
        for (var entry : grid(result).entrySet()) {
            BlockEntity tile = owners.get(entry.getValue()); if (tile == null) throw new IllegalArgumentException("Unknown native crafter grid identity");
            cells.add(new Cell(tile, (int)number(call(entry.getKey(), "getKey"), 0)-minX, maxY-(int)number(call(entry.getKey(), "getValue"), 0), Boolean.TRUE.equals(field(tile, "covered"))));
        }
        if (cells.size() != host.tiles.size()) throw new IllegalArgumentException("Overlapping native crafter positions");
        return List.copyOf(cells);
    }
    public List<Recipe> recipes(CraftingService.NetworkAccess access, List<Machine> machines) {
        diagnostics.clear(); List<Recipe> result = new ArrayList<>(); Set<List<Object>> seen = new HashSet<>(); ItemStock stock = itemStock(access.resourceSnapshot());
        for (Machine candidate : machines) if (candidate instanceof Host host) try {
            List<Object> layout = host.layout(); if (!seen.add(layout)) continue; var cells = cells(host);
            int width = cells.stream().mapToInt(Cell::x).max().orElse(0)+1, height = cells.stream().mapToInt(Cell::y).max().orElse(0)+1;
            var providers = CreateBackend.nativeRecipeTypes(host.terminal.getClass());
            for (var entry : NativeRecipeSource.entries(access.level())) {
                if (!providers.contains(entry.type())) continue;
                String type = String.valueOf(NativeRecipeSource.typeKey(entry.value()));
                ItemStack output = NativeRecipeSource.output(entry.value(), access.level().registryAccess()); if (output.isEmpty()) continue;
                var declared = NativeRecipeSource.ingredients(entry.value()); int[] shape = NativeRecipeSource.shape(entry.value());
                Map<BlockPos, CraftRecipe.Ingredient<ResourceKey>> assigned = null;
                for (int dy = 0; assigned == null && dy <= height - Math.max(1, shape[1]); dy++) for (int dx = 0; assigned == null && dx <= width - Math.max(1, shape[0]); dx++) for (int mirror = 0; assigned == null && mirror < 2; mirror++) {
                    Map<BlockPos, CraftRecipe.Ingredient<ResourceKey>> attempt = new LinkedHashMap<>(); int sequential = 0; boolean valid = true;
                    for (var cell : cells.stream().sorted(Comparator.comparingInt(Cell::y).thenComparingInt(Cell::x)).toList()) {
                        int x = cell.x()-dx, y = cell.y()-dy; net.minecraft.world.item.crafting.Ingredient ingredient = null;
                        if (shape[0] > 0) { if (x >= 0 && y >= 0 && x < shape[0] && y < shape[1]) ingredient = declared.get(y*shape[0]+(mirror == 0 ? x : shape[0]-x-1)); }
                        else if (!cell.covered() && sequential < declared.size()) ingredient = declared.get(sequential++);
                        boolean empty = ingredient == null || ingredient.isEmpty();
                        if (cell.covered() != empty) { valid = false; break; }
                        if (!empty) attempt.put(cell.tile().getBlockPos(), ingredient(ingredient, 1, stock));
                    }
                    if (!valid || attempt.isEmpty() || attempt.size() != declared.stream().filter(value -> !value.isEmpty()).count()) continue;
                    Map<BlockPos, ItemStack> samples = new HashMap<>(); attempt.forEach((pos, input) -> samples.put(pos, ((ItemKey)input.alternatives().get(0)).sample()));
                    Object nativeInput = nativeInput(grouped(host, samples));
                    if (!NativeRecipeSource.matches(entry.value(), nativeInput, access.level())) continue;
                    if (type.equals("minecraft:crafting")) {
                        Object config = call(optionalClass("com.simibubi.create.infrastructure.config.AllConfigs"), "server");
                        if (!Boolean.TRUE.equals(call(field(field(config, "recipes"), "allowRegularCraftingInCrafter"), "get"))) continue;
                        if (!Boolean.TRUE.equals(call(optionalClass("com.simibubi.create.content.kinetics.crafter.RecipeGridHandler"), "isRecipeAllowed", NativeRecipeSource.nativeHolder(entry), nativeInput))) continue;
                    }
                    assigned = attempt;
                }
                if (assigned == null) continue;
                Map<ResourceKey, Long> outputs = Map.of(new ItemKey(output), (long)output.getCount());
                String profile = NativeMachineAdapter.profile(access.level(), layout);
                Map<Slot, CraftRecipe.Ingredient<ResourceKey>> local = new HashMap<>();
                for (var cell : cells) { var input = assigned.get(cell.tile().getBlockPos()); if (input != null) local.put(new Slot(cell.x(),cell.y()), input); }
                result.add(new Recipe("create/crafter/"+profile+"/"+entry.id(), type+"/crafter/"+profile, List.copyOf(assigned.values()), outputs, outputs, 100, 7200, new Bound(entry, layout, Map.copyOf(local)), false));
            }
        } catch (RuntimeException | LinkageError unavailable) { diagnostics.put(host.terminal.getBlockPos().toShortString(), String.valueOf(unavailable.getMessage())); }
        return List.copyOf(result);
    }
    private static final class Host implements Machine {
        final ServerLevel level; final BlockEntity terminal; final List<BlockEntity> tiles;
        final List<net.minecraft.world.level.block.state.BlockState> states; final List<Boolean> covers;
        private List<Cell> cachedCells; private List<Object> cachedLayout;
        Host(ServerLevel level, BlockEntity terminal, List<BlockEntity> tiles) { this.level=level; this.terminal=terminal; this.tiles=tiles; states=tiles.stream().map(BlockEntity::getBlockState).toList(); covers=tiles.stream().map(tile->Boolean.TRUE.equals(field(tile,"covered"))).toList(); }
        public Object identity() { return terminal; }
        public Object instance() { return terminal; }
        public boolean valid() { for(int i=0;i<tiles.size();i++) { var tile=tiles.get(i); if(tile.isRemoved() || level.getBlockEntity(tile.getBlockPos())!=tile || !states.get(i).equals(tile.getBlockState()) || covers.get(i)!=Boolean.TRUE.equals(field(tile,"covered"))) return false; } return true; }
        List<Cell> cells() { if(cachedCells==null) cachedCells=CreateCrafterBackend.cells(this); return cachedCells; }
        List<Object> layout() { if(cachedLayout==null) cachedLayout=cells().stream().sorted(Comparator.comparingInt(Cell::y).thenComparingInt(Cell::x)).map(cell -> (Object)List.of(cell.x(),cell.y(),cell.covered(),String.valueOf(pointing(cell.tile())))).toList(); return cachedLayout; }
        public Object catalogState() { return layout(); }
        public boolean accepts(Recipe recipe) { return recipe.nativeRecipe() instanceof Bound bound && bound.layout().equals(layout()); }
        public boolean ready(Recipe recipe, List<CraftPlan.Selection<ResourceKey>> selected) { return tiles.stream().allMatch(tile -> String.valueOf(field(tile,"phase")).equals("IDLE") && number(call(tile,"getSpeed"),0)!=0); }
        public boolean emitsWorldItems() { return true; }
        public List<GlobalPos> claimedPositions() { List<GlobalPos> result=new ArrayList<>(); tiles.forEach(tile -> result.add(GlobalPos.of(level.dimension(),tile.getBlockPos()))); BlockPos output=target(terminal); if (level.hasChunkAt(output) && level.getBlockEntity(output)!=null) result.add(GlobalPos.of(level.dimension(),output)); return List.copyOf(result); }
        public List<Port> ports(Recipe recipe) {
            Bound bound=(Bound)recipe.nativeRecipe(); List<Port> result=new ArrayList<>();
            for (var cell:cells()) { var tile=cell.tile(); var expected=bound.inputs().get(new Slot(cell.x(),cell.y())); Object inventory=field(tile,"inventory"); if (expected==null) continue; var nativePort=NativeMachinePorts.itemSlot(inventory,0,true,false);
                result.add(NativeMachinePorts.callbacks(nativePort.identity(),true,false,nativePort::snapshot,(key,count,simulate)->expected.alternatives().contains(key)?nativePort.insert(key,count,simulate):0,nativePort::extract)); }
            BlockPos output=target(terminal); if (level.hasChunkAt(output)) { Object handler=optional(field(terminal,"inserting"),"getInventory"); if(handler instanceof Optional<?> value) handler=value.orElse(null); if(handler==null || !has(handler,"getSlots")) handler=Capabilities.get(level, Capabilities.ItemHandler.BLOCK,output,null); if(handler!=null) result.addAll(NativeMachinePorts.items(handler,java.util.stream.IntStream.range(0,(int)number(call(handler,"getSlots"),0)).boxed().toList(),false,true)); }
            return List.copyOf(result);
        }
    }
}
