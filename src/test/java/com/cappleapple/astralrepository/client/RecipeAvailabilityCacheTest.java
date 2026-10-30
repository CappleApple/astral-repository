package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.menu.RecipeTransferPlan;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RecipeAvailabilityCacheTest {
    private record Key(String item, String components) {}

    @Test void largeCatalogIndexesOnceAndRepeatedRecipesDoNoIngredientWork() {
        Map<Integer,Long> stock = new LinkedHashMap<>();
        for (int key = 0; key < 10_000; key++) stock.put(key, 64L);
        AtomicInteger indexed = new AtomicInteger(), predicates = new AtomicInteger(), computed = new AtomicInteger();
        var cache = new RecipeAvailabilityCache<Integer,Integer,Object>(stock, key -> {
            indexed.incrementAndGet();
            return key / 10;
        });
        List<Object> recipes = new ArrayList<>();
        for (int item = 0; item < 1_000; item++) {
            Object recipe = new Object();
            recipes.add(recipe);
            int bucket = item;
            assertTrue(cache.available(recipe, () -> {
                computed.incrementAndGet();
                return !cache.candidates(List.of(bucket), key -> {
                    predicates.incrementAndGet();
                    return true;
                }).isEmpty();
            }));
        }
        assertEquals(10_000, indexed.get());
        assertEquals(10_000, predicates.get(), "Only the ten identities in each matching item bucket may be tested");
        assertEquals(1_000, computed.get());
        for (int frame = 0; frame < 100; frame++) {
            for (Object recipe : recipes) assertTrue(cache.available(recipe, () -> {
                fail("An unchanged recipe/inventory must reuse its availability result");
                return false;
            }));
        }
        assertEquals(10_000, indexed.get());
        assertEquals(10_000, predicates.get());
        assertEquals(1_000, computed.get());
    }

    @Test void duplicateBucketsDoNotRepeatKeysAndComponentsRemainDistinct() {
        Key plain = new Key("iron", ""), named = new Key("iron", "named"), gold = new Key("gold", "");
        var cache = new RecipeAvailabilityCache<Key,String,Object>(
                Map.of(plain, 5L, named, 2L, gold, 9L), Key::item);
        AtomicInteger tested = new AtomicInteger();
        List<Key> result = cache.candidates(List.of("iron", "iron"), key -> {
            tested.incrementAndGet();
            return true;
        });
        assertEquals(Set.of(plain, named), new HashSet<>(result));
        assertEquals(2, result.size());
        assertEquals(2, tested.get());
        assertEquals(List.of(named), cache.candidates(List.of("iron"), key -> key.components().equals("named")));
        assertEquals(5L, cache.stock().get(plain));
        assertEquals(2L, cache.stock().get(named));
    }

    @Test void nullBucketsUseTheExactGenericPredicateAndEmptyBucketsDoNoWork() {
        var cache = new RecipeAvailabilityCache<String,String,Object>(
                Map.of("oak", 4L, "birch", 3L, "iron", 9L), key -> key);
        AtomicInteger tested = new AtomicInteger();
        assertEquals(Set.of("oak", "birch"), new HashSet<>(cache.candidates(null, key -> {
            tested.incrementAndGet();
            return !key.equals("iron");
        })));
        assertEquals(3, tested.get());
        assertEquals(List.of(), cache.candidates(List.of(), key -> {
            fail("An empty bucket selection has no candidates");
            return true;
        }));
        assertEquals(List.of(), cache.candidates(List.of("missing"), key -> {
            fail("A missing bucket has no candidates");
            return true;
        }));
    }

    @Test void cachedResultsUseRecipeIdentityAndIncludeUnavailableRecipes() {
        var cache = new RecipeAvailabilityCache<String,String,String>(Map.of("iron", 1L), key -> key);
        String first = new String("same recipe id"), replacement = new String("same recipe id");
        AtomicInteger computed = new AtomicInteger();
        assertFalse(cache.available(first, () -> { computed.incrementAndGet(); return false; }));
        assertFalse(cache.available(first, () -> { fail("False results must also be cached"); return true; }));
        assertTrue(cache.available(replacement, () -> { computed.incrementAndGet(); return true; }));
        assertEquals(2, computed.get(), "Replacing a recipe object must not reuse another object's result");
    }

    @Test void aNewInventoryEpochRecomputesAvailabilityAndKeepsItsOwnCounts() {
        Map<String,Long> source = new LinkedHashMap<>(Map.of("iron", 1L));
        Object recipe = new Object();
        var before = new RecipeAvailabilityCache<String,String,Object>(source, key -> key);
        assertFalse(before.available(recipe, () -> before.stock().get("iron") >= 2));
        source.put("iron", 2L);
        source.put("gold", 8L);
        var after = new RecipeAvailabilityCache<String,String,Object>(source, key -> key);
        assertTrue(after.available(recipe, () -> after.stock().get("iron") >= 2));
        assertEquals(Map.of("iron", 1L), before.stock());
        assertEquals(Map.of("iron", 2L, "gold", 8L), after.stock());
        assertThrows(UnsupportedOperationException.class, () -> before.stock().put("iron", 99L));
        assertEquals(List.of(), before.candidates(List.of("gold"), key -> true));
    }

    @Test void indexedCandidatesStillShareExactCapacityAcrossIngredients() {
        Key oak = new Key("wood", "oak"), birch = new Key("wood", "birch");
        var cache = new RecipeAvailabilityCache<Key,String,Object>(Map.of(oak, 1L, birch, 1L), Key::item);
        var alternatives = cache.candidates(List.of("wood"), key -> true);
        var exact = cache.candidates(List.of("wood"), key -> key.equals(oak));
        assertEquals(List.of(birch, oak), RecipeTransferPlan.assign(
                List.of(alternatives, exact), cache.stock(), 1, key -> 64));
        assertNull(RecipeTransferPlan.assign(
                List.of(exact, exact), cache.stock(), 1, key -> 64));
    }

    @Test void coldDeclaredDomainAvoidsUnrelatedItemsAcrossManyComponentVariants() {
        Map<Key,Long> stock = new LinkedHashMap<>();
        for (int item = 0; item < 5_000; item++) {
            stock.put(new Key("item_" + item, "plain"), 1L);
            stock.put(new Key("item_" + item, "named"), 1L);
        }
        AtomicInteger indexed = new AtomicInteger(), tested = new AtomicInteger();
        var cache = new RecipeAvailabilityCache<Key,String,Object>(stock, key -> {
            indexed.incrementAndGet();
            return key.item();
        });
        for (int item = 0; item < 1_000; item++) {
            String wanted = "item_" + item;
            var candidates = cache.candidates(List.of(wanted), key -> {
                tested.incrementAndGet();
                return true;
            });
            assertEquals(Set.of(new Key(wanted, "plain"), new Key(wanted, "named")), new HashSet<>(candidates));
        }
        assertEquals(10_000, indexed.get());
        assertEquals(2_000, tested.get(), "Cold complete-domain queries must skip unrelated item buckets");
    }

    @Test void declaredDomainRetestsDynamicComponentPredicatesWithoutCachingAnswers() {
        Key plain = new Key("iron", ""), first = new Key("iron", "first"),
                second = new Key("iron", "second"), gold = new Key("gold", "");
        var cache = new RecipeAvailabilityCache<Key,String,Object>(
                Map.of(plain, 5L, first, 2L, second, 3L, gold, 9L), Key::item);
        AtomicInteger tested = new AtomicInteger();
        boolean[] enabled = { false };
        String[] selected = { "first" };
        java.util.function.Predicate<Key> predicate = key -> {
            tested.incrementAndGet();
            return enabled[0] && key.components().equals(selected[0]);
        };
        assertEquals(List.of(), cache.candidates(List.of("iron"), predicate));
        enabled[0] = true;
        assertEquals(List.of(first), cache.candidates(List.of("iron"), predicate));
        selected[0] = "second";
        assertEquals(List.of(second), cache.candidates(List.of("iron"), predicate));
        enabled[0] = false;
        assertEquals(List.of(), cache.candidates(List.of("iron"), predicate));
        assertEquals(12, tested.get(), "A fixed item domain must still test current component and external state");
        assertEquals(2L, cache.stock().get(first));
        assertEquals(3L, cache.stock().get(second));
    }

    @Test void incompleteDisplaySamplesDoNotRestrictUnknownCustomPredicates() {
        Key displayed = new Key("stone", ""), hidden = new Key("iron", "accepted"), other = new Key("gold", "");
        var cache = new RecipeAvailabilityCache<Key,String,Object>(
                Map.of(displayed, 1L, hidden, 4L, other, 1L), Key::item);
        List<String> displayItems = List.of(displayed.item());
        assertEquals(List.of(), cache.candidates(displayItems, key -> key.equals(hidden)));
        AtomicInteger tested = new AtomicInteger();
        assertEquals(List.of(hidden), cache.candidates(null, key -> {
            tested.incrementAndGet();
            return key.equals(hidden);
        }));
        assertEquals(3, tested.get(), "Display samples are not a complete domain contract");
    }

    @Test void denseItemDomainPreservesAllExactIdentitiesAndSharedCapacity() {
        Map<Key,Long> stock = new LinkedHashMap<>();
        for (int variant = 0; variant < 10_000; variant++)
            stock.put(new Key("same_item", "variant_" + variant), 1L);
        var cache = new RecipeAvailabilityCache<Key,String,Object>(stock, Key::item);
        AtomicInteger tested = new AtomicInteger();
        var candidates = cache.candidates(List.of("same_item"), key -> {
            tested.incrementAndGet();
            return true;
        });
        assertEquals(10_000, tested.get(), "A dense matching bucket still contains every component identity");
        assertEquals(10_000, new HashSet<>(candidates).size());
        var assignment = RecipeTransferPlan.assign(Collections.nCopies(9, candidates), cache.stock(), 1, key -> 64);
        assertNotNull(assignment);
        assertEquals(9, new HashSet<>(assignment).size(), "Distinct one-count variants cannot be spent twice");
    }

}
