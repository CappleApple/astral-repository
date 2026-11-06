package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ResourceKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Optional per-mod native contracts; all callbacks run on the owning server thread. */
public interface NativeMachineBackend {
    String id();

    /** Return null for storage, unformed structures, denied access, or an unrelated block. */
    Machine machine(CraftingService.NetworkAccess access, GlobalPos position);

    /** Read-only identity lookup for direct rune paths outside the discovering network. */
    default Object identityAt(ServerLevel level, BlockPos position) { return null; }

    /** Read actual registered/native production data, without running a machine or rolling outputs. */
    List<Recipe> recipes(CraftingService.NetworkAccess access, List<Machine> loadedMachines);
    default List<Recipe> recipes(CraftingService.NetworkAccess access, List<Machine> loadedMachines, List<ResourceKey> requestedOutputs) {
        return recipes(access, loadedMachines);
    }
    /** Dynamic native output domains depend on requested samples and taught product identities. */
    default boolean usesRequestedOutputs() { return false; }

    /** Custom native predicates may accept identities absent from their displayed alternatives. */
    default boolean usesUnlistedStock() { return true; }

    record Recipe(String id, String process, List<CraftRecipe.Ingredient<ResourceKey>> inputs,
                  Map<ResourceKey, Long> guaranteed, Map<ResourceKey, Long> possible,
                  int durationTicks, int timeoutTicks, Object nativeRecipe, boolean chanceOutput,Set<String> sourceRecipes) {
        public Recipe(String id,String process,List<CraftRecipe.Ingredient<ResourceKey>> inputs,Map<ResourceKey,Long> guaranteed,Map<ResourceKey,Long> possible,
                      int durationTicks,int timeoutTicks,Object nativeRecipe,boolean chanceOutput) {
            this(id,process,inputs,guaranteed,possible,durationTicks,timeoutTicks,nativeRecipe,chanceOutput,id==null?Set.of():Set.of(id));
        }
        public Recipe {
            inputs = List.copyOf(inputs);
            guaranteed = Map.copyOf(guaranteed);
            possible = Map.copyOf(possible);
            sourceRecipes=Set.copyOf(sourceRecipes);
            if (id == null || process == null || inputs.isEmpty() || possible.isEmpty()
                    || durationTicks < 1 || timeoutTicks < 1)
                throw new IllegalArgumentException("A native recipe needs its real inputs, outputs and timing");
            for (long amount : possible.values()) if (amount <= 0) throw new IllegalArgumentException("Invalid native output quantities");
            for (var entry : guaranteed.entrySet()) if (entry.getValue() <= 0 || possible.getOrDefault(entry.getKey(), 0L) < entry.getValue())
                throw new IllegalArgumentException("Invalid native output quantities");
        }
    }

    /** Supplemental native fuel is owned and delivered separately from recipe reagents. */
    record Supplemental(Port port, ResourceKey key, long count) {
        public Supplemental { if (port == null || key == null || count < 1) throw new IllegalArgumentException("Invalid native supplemental input"); }
    }

    interface Machine {
        /** Stable physical ownership identity shared by every side and multiblock port. */
        Object identity();
        /** Exact live native owner; replacement invalidates an in-flight operation. */
        Object instance();
        boolean valid();
        boolean accepts(Recipe recipe);
        /** Read-only native mode/setup/catalyst checks before any input is committed. */
        default boolean ready(Recipe recipe, List<CraftPlan.Selection<ResourceKey>> selected) { return true; }
        /** Native roles for this recipe; setup/catalyst containers must not be input/output ports. */
        List<Port> ports(Recipe recipe);
        /** Immutable configuration that changes discovered recipes or their quantities. */
        default Object catalogState() { return List.of(); }
        /** Loaded aliases and native output destinations requiring the same exclusive claim. */
        default List<GlobalPos> claimedPositions() { return List.of(); }
        /** Requests for real native fuel only; called again while running to handle depleted burn time. */
        default List<Supplemental> supplemental(CraftingService.NetworkAccess access, Recipe recipe,
                                                List<CraftPlan.Selection<ResourceKey>> selected) { return List.of(); }
        /** Required for zero-yield attempts; consumption alone does not prove a cycle finished. */
        default boolean completed(Recipe recipe, int polls) { return false; }
        default boolean completed(Recipe recipe, int polls, Map<ResourceKey, Long> produced) { return completed(recipe, polls); }
        default boolean requiresNativeCompletion(Recipe recipe) { return false; }
        /** A proved native batch end may leave conservatively reserved inputs physically unused. */
        default boolean allowsUnusedInputs(Recipe recipe) { return false; }
        default boolean emitsWorldItems() { return false; }
        /** Shapeless bulk containers may require repeated equal inputs in one physical stack. */
        default boolean coalesceInputs() { return false; }
        /** Native runtimes may expose a finite shorter bound, never change processing time here. */
        default int timeoutTicks(Recipe recipe) { return recipe.timeoutTicks(); }
    }

    interface Port {
        Object identity();
        boolean input();
        boolean output();
        /** Exact preloaded native setup that must remain in this input port; never owned by the job. */
        default Map<ResourceKey, Long> retainedInputBaseline() { return Map.of(); }
        /** Collect continuously produced resources so native buffer capacity cannot truncate a cycle. */
        default boolean streamingOutput() { return false; }
        /** Accepted operations may maintain their own physical staging; never called by discovery. */
        default void tickOwned() {}
        Map<ResourceKey, Long> snapshot();
        /** Return the exact accepted amount; simulation must have no side effects. */
        long insert(ResourceKey key, long amount, boolean simulate);
        /** Return the exact extracted amount; validate identity before executing. */
        long extract(ResourceKey key, long amount, boolean simulate);
    }
}
