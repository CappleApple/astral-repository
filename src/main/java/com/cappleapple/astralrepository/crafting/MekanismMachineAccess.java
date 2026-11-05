package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import static com.cappleapple.astralrepository.crafting.MekanismRecipeDiscovery.*;

/** Native machine/slot/tank discovery shared by current and future Mekanism recipe handlers. */
public final class MekanismMachineAccess {
    private record Role(int flags, Set<String> names) {}
    private record Surface(boolean lookup, boolean inventory, boolean multiblock, boolean security) {
        boolean candidate() { return lookup || inventory || multiblock; }
    }
    private static final ClassValue<Surface> SURFACES = new ClassValue<>() {
        @Override protected Surface computeValue(Class<?> type) {
            boolean lookup = implementsContract(type, "mekanism.common.recipe.lookup.IRecipeLookupHandler");
            boolean inventory = implementsContract(type, "mekanism.api.inventory.IMekanismInventory");
            boolean multiblock = methodExists(type, "getMultiblock");
            return new Surface(lookup, inventory, multiblock, (lookup || inventory || multiblock) && methodExists(type, "validateSecurityIsPublic"));
        }
    };
    private static boolean methodExists(Class<?> type, String name) {
        try { type.getMethod(name); return true; }
        catch (NoSuchMethodException absent) { return false; }
    }
    private static final ClassValue<Map<String, VarHandle>> USAGE_FIELDS = new ClassValue<>() {
        @Override protected Map<String, VarHandle> computeValue(Class<?> owner) {
            Map<String, VarHandle> result = new LinkedHashMap<>();
            for (String name : List.of("baseTotalUsage", "injectUsage", "gasPerTickMeanMultiplier", "chemicalPerTickMeanMultiplier")) {
                Class<?> valueType = name.equals("baseTotalUsage") ? long.class : double.class;
                for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
                    try { result.put(name, MethodHandles.privateLookupIn(type, MethodHandles.lookup()).findVarHandle(type, name, valueType)); break; }
                    catch (ReflectiveOperationException absent) { }
                }
            }
            return Map.copyOf(result);
        }
    };
    private MekanismMachineAccess() {}
    public static Object handler(Object tile) {
        if (tile == null) return null;
        Surface surface = SURFACES.get(tile.getClass());
        if (!surface.candidate()) return null;
        if (surface.security()) invoke(tile, "validateSecurityIsPublic");
        Object candidate = tile;
        if (surface.multiblock()) {
            candidate = invoke(candidate, "getMultiblock");
            if (candidate == null || hasMethod(candidate, "isFormed") && !Boolean.TRUE.equals(invoke(candidate, "isFormed"))) return null;
        }
        Surface resolved = SURFACES.get(candidate.getClass());
        if (resolved.lookup()) return candidate;
        if (resolved.inventory()) {
            Object slots = invoke(candidate, "getInventorySlots", new Class<?>[]{Direction.class}, new Object[]{null});
            if (slots instanceof List<?> nativeSlots && nativeSlots.stream().anyMatch(MekanismMachineAccess::isConverter)) return candidate;
        }
        return null;
    }
    public static String process(Object handler) { return hasMethod(handler, "getRecipeType") ? invoke(invoke(handler, "getRecipeType"), "getRegistryName").toString() : ""; }
    private static boolean isConverter(Object slot) {
        if (!hasMethod(slot, "fillTankOrConvert") && !hasMethod(slot, "fillContainerOrConvert")) return false;
        // Fill/drain and conversion slots share a class. Only conversion constructors bind
        // the live world supplier used for recipe lookup; ordinary storage slots return null.
        for (Class<?> type = slot.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("worldSupplier"); field.setAccessible(true);
                Object supplier = field.get(slot);
                return supplier instanceof java.util.function.Supplier<?> source && source.get() != null;
            } catch (NoSuchFieldException absent) { }
            catch (ReflectiveOperationException failure) { return false; }
        }
        return false;
    }
    public static Object identity(Object handler) {
        try { Object id = MethodHandles.publicLookup().findVarHandle(handler.getClass(), "inventoryID", java.util.UUID.class).get(handler); if (id != null) return id; }
        catch (ReflectiveOperationException ignored) { }
        return handler;
    }
    public static long usage(Object handler) {
        var state = usageState(handler);
        if (state.containsKey("baseTotalUsage")) return Math.max(1, state.get("baseTotalUsage").longValue());
        long ticks = state.getOrDefault("ticks", 200).longValue();
        return Math.max(1, state.containsKey("injectUsage") ? (long) Math.ceil(ticks * state.get("injectUsage").doubleValue()) : ticks);
    }
    public static Map<String, Number> usageState(Object handler) {
        Map<String, Number> state = new LinkedHashMap<>();
        USAGE_FIELDS.get(handler.getClass()).forEach((name, field) -> state.put(name, (Number) field.get(handler)));
        if (hasMethod(handler, "getTicksRequired")) state.put("ticks", (Number) invoke(handler, "getTicksRequired"));
        return Map.copyOf(state);
    }

    public static List<Port> ports(Object handler) {
        var roles = roles(handler);
        List<Port> ports = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (String getter : List.of("getInventorySlots", "getFluidTanks", "getChemicalTanks", "getGasTanks", "getInfusionTanks", "getPigmentTanks", "getSlurryTanks", "getEnergyContainers")) {
            Method method;
            try { method = handler.getClass().getMethod(getter, Direction.class); }
            catch (NoSuchMethodException absent) { continue; }
            Object result = invoke(handler, getter, new Class<?>[]{Direction.class}, new Object[]{null});
            if (!(result instanceof List<?> containers)) continue;
            for (Object container : containers) if (seen.add(container)) {
                Role role = roles.getOrDefault(container, new Role(3, Set.of()));
                String kind = getter.equals("getInventorySlots") ? "item" : getter.equals("getFluidTanks") ? "fluid" : getter.equals("getEnergyContainers") ? "energy" : "chemical";
                if (role.flags() == 0 && !isConverter(container)) continue;
                ports.add(new Port(container, kind, (role.flags() & 1) != 0, (role.flags() & 2) != 0, role.names()));
            }
        }
        return List.copyOf(ports);
    }

    private static Map<Object, Role> roles(Object handler) {
        Map<Object, Role> result = new IdentityHashMap<>();
        if (!hasMethod(handler, "getConfig")) return result;
        Object component = invoke(handler, "getConfig");
        if (component == null) return result;
        for (Object transmission : (List<?>) invoke(component, "getTransmissions")) {
            Object config = callCompatible(component, "getConfig", transmission);
            if (config == null) continue;
            for (Object type : (Set<?>) invoke(config, "getSupportedDataTypes")) {
                Object info = callCompatible(config, "getSlotInfo", type);
                if (info == null) continue;
                String getter = hasMethod(info, "getSlots") ? "getSlots" : hasMethod(info, "getTanks") ? "getTanks" : null;
                if (getter == null) continue;
                int role = type instanceof Enum<?> mode && mode.name().equals("ENERGY") ? 0 : (Boolean.TRUE.equals(invoke(info, "canInput")) ? 1 : 0) | (Boolean.TRUE.equals(invoke(info, "canOutput")) ? 2 : 0);
                String name = type instanceof Enum<?> mode ? mode.name() : type.toString();
                for (Object nativePort : (List<?>) invoke(info, getter)) result.merge(nativePort, new Role(role, Set.of(name)), (a, b) -> {
                    Set<String> names = new HashSet<>(a.names()); names.addAll(b.names()); return new Role(a.flags() | b.flags(), Set.copyOf(names));
                });
            }
        }
        return result;
    }

    public static final class Port {
        public final Object nativePort;
        public final String kind;
        public final boolean input, output;
        public final boolean converter;
        public final Set<String> semanticRoles;
        private final Object execute, simulate, manual;
        Port(Object nativePort, String kind, boolean input, boolean output, Set<String> roles) {
            this.nativePort = nativePort; this.kind = kind; this.input = input; this.output = output;
            semanticRoles = roles;
            converter = isConverter(nativePort);
            try {
                Class<?> action = Class.forName("mekanism.api.Action"), automation = Class.forName("mekanism.api.AutomationType");
                execute = action.getField("EXECUTE").get(null); simulate = action.getField("SIMULATE").get(null); manual = automation.getField("MANUAL").get(null);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Missing native Mekanism resource actions", failure); }
        }
        public Object stack() { return invoke(nativePort, kind.equals("fluid") ? "getFluid" : kind.equals("energy") ? "getEnergy" : "getStack"); }
        public long amount() { long amount = MekanismResourceSupport.amount(stack()); return kind.equals("energy") ? MekanismResourceSupport.energyToNetwork(amount) : amount; }
        public ResourceKey key() { Object value = stack(); return MekanismResourceSupport.amount(value) == 0 ? null : MekanismResourceSupport.key(value); }
        public long insert(ResourceKey key, long amount, boolean simulated) {
            if ((!input && !converter) || !kind.equals(MekanismResourceSupport.kind(key))) return 0;
            if (kind.equals("energy")) {
                long nativeAmount = MekanismResourceSupport.energyFromNetwork(amount);
                Object remainder = invokeTransfer("insert", nativeAmount, simulated);
                return MekanismResourceSupport.energyToNetwork(nativeAmount - MekanismResourceSupport.amount(remainder));
            }
            Object sample = MekanismResourceSupport.sample(key, amount);
            if (kind.equals("chemical")) {
                Object nativeStack = stack();
                // Older Mekanism has separate gas/infusion/pigment/slurry APIs. A mismatched
                // family is an ordinary rejected candidate, not an insertion exception.
                if (!nativeStack.getClass().isInstance(sample) && !sample.getClass().isInstance(nativeStack)) return 0;
            }
            Object remainder = invokeTransfer(kind.equals("item") ? "insertItem" : "insert", sample, simulated);
            return amount - MekanismResourceSupport.amount(remainder);
        }
        public long extract(long amount, boolean simulated) {
            if (amount <= 0) return 0;
            Object extracted = invokeTransfer(kind.equals("item") ? "extractItem" : "extract", kind.equals("energy") ? MekanismResourceSupport.energyFromNetwork(amount) : amount, simulated);
            long result = MekanismResourceSupport.amount(extracted);
            return kind.equals("energy") ? MekanismResourceSupport.energyToNetwork(result) : result;
        }
        private Object invokeTransfer(String name, Object amount, boolean simulated) {
            for (Method method : nativePort.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 3 || method.isBridge()) continue;
                Class<?>[] types = method.getParameterTypes();
                Object first = amount;
                if (amount instanceof Long value) {
                    if (types[0] == int.class) first = Integer.valueOf(Math.toIntExact(value));
                    else if (types[0] == long.class) first = value;
                }
                if (types[0].isPrimitive() || types[0].isInstance(first)) {
                    try { method.setAccessible(true); return method.invoke(nativePort, first, simulated ? simulate : execute, manual); }
                    catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native Mekanism transfer failed", failure); }
                }
            }
            throw new IllegalStateException("Unsupported native transfer " + name + " for " + nativePort.getClass().getName());
        }
        public boolean linksTo(Port other) {
            for (Class<?> type = nativePort.getClass(); type != null; type = type.getSuperclass()) for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try { field.setAccessible(true); if (field.get(nativePort) == other.nativePort) return true; }
                catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            return false;
        }
    }
}
