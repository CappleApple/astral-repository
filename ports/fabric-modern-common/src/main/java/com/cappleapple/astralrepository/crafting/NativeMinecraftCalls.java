package com.cappleapple.astralrepository.crafting;

import java.util.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.HolderSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

/** Derives semantic names from compiled Minecraft references, including production reobfuscation. */
public final class NativeMinecraftCalls {
    private NativeMinecraftCalls() {}
    private record Signature(String owner, String name, String descriptor) {}
    private static final Map<Signature, String> NAMES = discover();
    private static final Map<Signature, Optional<String>> INHERITED = new java.util.concurrent.ConcurrentHashMap<>();
    public static String name(NativeBytecode.Reference reference) {
        Signature signature = new Signature(reference.owner(), reference.name(), reference.descriptor());
        String exact = NAMES.get(signature);
        return exact != null ? exact : INHERITED.computeIfAbsent(signature, NativeMinecraftCalls::inherited).orElse(reference.name());
    }
    public static boolean known(NativeBytecode.Reference reference) {
        Signature signature = new Signature(reference.owner(), reference.name(), reference.descriptor());
        return NAMES.containsKey(signature) || INHERITED.computeIfAbsent(signature, NativeMinecraftCalls::inherited).isPresent();
    }
    private static Optional<String> inherited(Signature signature) {
        var candidates=NAMES.entrySet().stream().filter(entry->entry.getKey().name().equals(signature.name()) && entry.getKey().descriptor().equals(signature.descriptor())).toList();
        if(candidates.isEmpty()) return Optional.empty();
        try {
            ClassLoader loader = NativeMinecraftCalls.class.getClassLoader(); Class<?> owner = Class.forName(signature.owner(), false, loader);
            for (var entry : candidates) {
                var declared = entry.getKey();
                if (declared.name().equals(signature.name()) && declared.descriptor().equals(signature.descriptor())
                        && Class.forName(declared.owner(), false, loader).isAssignableFrom(owner)) return Optional.of(entry.getValue());
            }
        } catch (ClassNotFoundException | LinkageError absent) { /* Unknown native owners keep their literal method name. */ }
        return Optional.empty();
    }
    private static final ClassValue<Map<String, Set<String>>> RUNTIME_NAMES = new ClassValue<>() {
        @Override protected Map<String, Set<String>> computeValue(Class<?> receiver) {
            Map<String, Set<String>> result = new HashMap<>();
            NAMES.forEach((signature, semantic) -> {
                try {
                    Class<?> contract = Class.forName(signature.owner(), false, receiver.getClassLoader());
                    if (contract.isAssignableFrom(receiver)) result.computeIfAbsent(semantic, ignored -> new LinkedHashSet<>()).add(signature.name());
                } catch (ClassNotFoundException | LinkageError unavailable) { /* An absent contract cannot match this receiver. */ }
            });
            Map<String, Set<String>> immutable = new HashMap<>(); result.forEach((name, values) -> immutable.put(name, Set.copyOf(values))); return Map.copyOf(immutable);
        }
    };
    public static Set<String> runtimeNames(Class<?> receiver, String semantic) {
        Set<String> names = new LinkedHashSet<>(RUNTIME_NAMES.get(receiver).getOrDefault(semantic, Set.of())); names.add(semantic); return Set.copyOf(names);
    }
    private static Map<Signature, String> discover() {
        Map<Signature, String> result = new HashMap<>();
        for (var method : NativeBytecode.inspect(NativeMinecraftCalls.class).methods()) if (method.name().startsWith("minecraft$")) {
            String semantic = method.name().substring("minecraft$".length());
            for (var reference : method.references()) {
                if (reference.name().equals("<init>")) continue;
                Signature signature = new Signature(reference.owner(), reference.name(), reference.descriptor());
                String old = result.putIfAbsent(signature, semantic);
                if (old != null && !old.equals(semantic)) throw new IllegalStateException("Ambiguous compiled Minecraft semantic " + signature);
            }
        }
        return Map.copyOf(result);
    }
    // Never invoked by discovery: their compiled references form the loader's actual mapping.
    private static boolean minecraft$isEmpty(ItemStack stack) { return stack.isEmpty(); }
    private static Item minecraft$getItem(ItemStack stack) { return stack.getItem(); }
    private static int minecraft$getCount(ItemStack stack) { return stack.getCount(); }
    private static int minecraft$getMaxStackSize(ItemStack stack) { return stack.getMaxStackSize(); }
    private static ItemStack minecraft$copy(ItemStack stack) { return stack.copy(); }
    private static ItemStack minecraft$copyWithCount(ItemStack stack, int count) { return stack.copyWithCount(count); }
    private static boolean minecraft$is(ItemStack stack, TagKey<Item> tag) { return stack.is(tag); }
    private static boolean minecraft$is(ItemStack stack, Item item) { return stack.is(item); }
    private static void minecraft$shrink(ItemStack stack, int count) { stack.shrink(count); }
    private static void minecraft$grow(ItemStack stack, int count) { stack.grow(count); }
    private static void minecraft$setCount(ItemStack stack, int count) { stack.setCount(count); }
    private static Object minecraft$getCraftingRemainingItem(ItemStack stack) { return stack.getCraftingRemainder(); }
    private static boolean minecraft$isEnchanted(ItemStack stack) { return stack.isEnchanted(); }
    private static boolean minecraft$has(ItemStack stack, DataComponentType<?> type) { return stack.has(type); }
    private static <T> T minecraft$get(ItemStack stack, DataComponentType<T> type) { return stack.get(type); }
    private static <T> T minecraft$set(ItemStack stack, DataComponentType<T> type, T value) { return stack.set(type, value); }
    private static <T> T minecraft$remove(ItemStack stack,DataComponentType<T> type) { return stack.remove(type); }
    private static <T> T minecraft$getOrDefault(ItemStack stack,DataComponentType<T> type,T fallback) { return stack.getOrDefault(type,fallback); }
    private static int minecraft$getMaxDamage(ItemStack stack) { return stack.getMaxDamage(); }
    private static boolean minecraft$isDamageableItem(ItemStack stack) { return stack.isDamageableItem(); }
    private static void minecraft$setDamageValue(ItemStack stack,int value) { stack.setDamageValue(value); }
    private static int minecraft$flightDuration(net.minecraft.world.item.component.Fireworks fireworks) { return fireworks.flightDuration(); }
    private static List<?> minecraft$explosions(net.minecraft.world.item.component.Fireworks fireworks) { return fireworks.explosions(); }
    private static net.minecraft.world.item.component.FireworkExplosion.Shape minecraft$shape(net.minecraft.world.item.component.FireworkExplosion explosion) { return explosion.shape(); }
    private static it.unimi.dsi.fastutil.ints.IntList minecraft$colors(net.minecraft.world.item.component.FireworkExplosion explosion) { return explosion.colors(); }
    private static it.unimi.dsi.fastutil.ints.IntList minecraft$fadeColors(net.minecraft.world.item.component.FireworkExplosion explosion) { return explosion.fadeColors(); }
    private static boolean minecraft$hasTrail(net.minecraft.world.item.component.FireworkExplosion explosion) { return explosion.hasTrail(); }
    private static boolean minecraft$hasTwinkle(net.minecraft.world.item.component.FireworkExplosion explosion) { return explosion.hasTwinkle(); }
    private static ItemStack minecraft$enchantItem(RandomSource random, ItemStack input, int level, net.minecraft.core.RegistryAccess registries, Optional<? extends HolderSet<net.minecraft.world.item.enchantment.Enchantment>> candidates) { return net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(random,input,level,registries,candidates); }
    private static boolean minecraft$isEnchantable(ItemStack stack) { return stack.isEnchantable(); }
    private static BlockPos minecraft$above(BlockPos position) { return position.above(); }
    private static BlockEntity minecraft$getBlockEntity(Level level, BlockPos position) { return level.getBlockEntity(position); }
    private static net.minecraft.world.item.crafting.RecipeManager minecraft$getRecipeManager(net.minecraft.server.MinecraftServer server) { return server.getRecipeManager(); }
    private static Object minecraft$id(RecipeHolder<?> holder) { return holder.id(); }
    private static net.minecraft.resources.Identifier minecraft$parse(String value) { return net.minecraft.resources.Identifier.parse(value); }
    private static int minecraft$nutrition(net.minecraft.world.food.FoodProperties food) { return food.nutrition(); }
    private static float minecraft$saturation(net.minecraft.world.food.FoodProperties food) { return food.saturation(); }
    private static boolean minecraft$isSame(Fluid fluid, Fluid other) { return fluid.isSame(other); }
    private static boolean minecraft$is(Fluid fluid, TagKey<Fluid> tag) { return fluid.is(tag); }
    private static DyeColor minecraft$byId(int id) { return DyeColor.byId(id); }
    private static RandomSource minecraft$worldRandom(Level level) { return level.getRandom(); }
    private static Iterable<MobEffectInstance> minecraft$getAllEffects(PotionContents contents) { return contents.getAllEffects(); }
    private static int minecraft$getDuration(MobEffectInstance effect) { return effect.getDuration(); }
    private static int minecraft$getAmplifier(MobEffectInstance effect) { return effect.getAmplifier(); }
    private static RecipeType<?> minecraft$getType(Recipe<?> recipe) { return recipe.getType(); }
    private static int minecraft$getCookingTime(AbstractCookingRecipe recipe) { return recipe.cookingTime(); }
    private static float minecraft$getExperience(AbstractCookingRecipe recipe) { return recipe.experience(); }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static boolean minecraft$matches(Recipe recipe, RecipeInput input, Level level) { return recipe.matches(input, level); }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static ItemStack minecraft$assemble(Recipe recipe, RecipeInput input, HolderLookup.Provider registries) { return recipe.assemble(input); }
    private static Recipe<?> minecraft$value(RecipeHolder<?> holder) { return holder.value(); }
    private static Object minecraft$value(net.minecraft.core.Holder<?> holder) { return holder.value(); }
    private static net.minecraft.core.RegistryAccess minecraft$registryAccess(Level level) { return level.registryAccess(); }
    private static net.minecraft.world.item.enchantment.ItemEnchantments minecraft$getEnchantmentsForCrafting(ItemStack stack) { return net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(stack); }
    private static void minecraft$setEnchantments(ItemStack stack,net.minecraft.world.item.enchantment.ItemEnchantments enchantments) { net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(stack,enchantments); }
    private static void minecraft$set(net.minecraft.world.item.enchantment.ItemEnchantments.Mutable enchantments,net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment,int level) { enchantments.set(enchantment,level); }
    private static void minecraft$upgrade(net.minecraft.world.item.enchantment.ItemEnchantments.Mutable enchantments,net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment,int level) { enchantments.upgrade(enchantment,level); }
    private static int minecraft$getLevel(net.minecraft.world.item.enchantment.ItemEnchantments.Mutable enchantments,net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment) { return enchantments.getLevel(enchantment); }
    private static net.minecraft.world.item.enchantment.ItemEnchantments minecraft$toImmutable(net.minecraft.world.item.enchantment.ItemEnchantments.Mutable enchantments) { return enchantments.toImmutable(); }
    private static Set<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> minecraft$keySet(net.minecraft.world.item.enchantment.ItemEnchantments.Mutable enchantments) { return enchantments.keySet(); }
    private static void minecraft$removeIf(net.minecraft.world.item.enchantment.ItemEnchantments.Mutable enchantments,java.util.function.Predicate<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> predicate) { enchantments.removeIf(predicate); }
    private static Set<?> minecraft$entrySet(net.minecraft.world.item.enchantment.ItemEnchantments enchantments) { return enchantments.entrySet(); }
    private static <T> boolean minecraft$is(net.minecraft.core.Holder<T> holder,TagKey<T> tag) { return holder.is(tag); }
    private static int minecraft$calculateIncreasedRepairCost(int cost) { return net.minecraft.world.inventory.AnvilMenu.calculateIncreasedRepairCost(cost); }
    private static Set<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> minecraft$keySet(net.minecraft.world.item.enchantment.ItemEnchantments enchantments) { return enchantments.keySet(); }
    private static int minecraft$getLevel(net.minecraft.world.item.enchantment.ItemEnchantments enchantments, net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment) { return enchantments.getLevel(enchantment); }
    private static boolean minecraft$isValidRepairItem(ItemStack input, ItemStack material) { return input.isValidRepairItem(material); }
    private static boolean minecraft$canEnchant(net.minecraft.world.item.enchantment.Enchantment enchantment,ItemStack stack) { return enchantment.canEnchant(stack); }
    private static int minecraft$getAnvilCost(net.minecraft.world.item.enchantment.Enchantment enchantment) { return enchantment.getAnvilCost(); }
    private static boolean minecraft$areCompatible(net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> first,net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> second) { return net.minecraft.world.item.enchantment.Enchantment.areCompatible(first,second); }
    private static net.minecraft.world.item.enchantment.Enchantment.EnchantmentDefinition minecraft$definition(net.minecraft.world.item.enchantment.Enchantment enchantment) { return enchantment.definition(); }
    private static int minecraft$weight(net.minecraft.world.item.enchantment.Enchantment.EnchantmentDefinition definition) { return definition.weight(); }
    private static int minecraft$getMaxLevel(net.minecraft.world.item.enchantment.Enchantment enchantment) { return enchantment.getMaxLevel(); }
    private static boolean minecraft$test(Ingredient ingredient, ItemStack stack) { return ingredient.test(stack); }
    private static boolean minecraft$isEmpty(Ingredient ingredient) { return ingredient.isEmpty(); }
    private static ItemStack[] minecraft$getItems(Ingredient ingredient) { return ingredient.items().map(net.minecraft.world.item.ItemStack::new).toArray(net.minecraft.world.item.ItemStack[]::new); }
    private static int minecraft$getDamageValue(ItemStack stack) { return stack.getDamageValue(); }
    private static BlockPos minecraft$getBlockPos(BlockEntity entity) { return entity.getBlockPos(); }
    private static Level minecraft$getLevel(BlockEntity entity) { return entity.getLevel(); }
    private static boolean minecraft$isRemoved(BlockEntity entity) { return entity.isRemoved(); }
    private static void minecraft$setChanged(BlockEntity entity) { entity.setChanged(); }
    private static DataComponentPatch minecraft$getComponentsPatch(ItemStack stack) { return stack.getComponentsPatch(); }
    private static Optional<?> minecraft$getTag(Registry<Item> registry, TagKey<Item> tag) { return registry.get(tag); }
    private static Optional<?> minecraft$getTag(DefaultedRegistry<Item> registry, TagKey<Item> tag) { return registry.get(tag); }
    private static Optional<?> minecraft$getRandomElement(HolderSet<Item> values, RandomSource random) { return values.getRandomElement(random); }
    private static Optional<?> minecraft$getRandomElement(HolderSet.Named<Item> values, RandomSource random) { return values.getRandomElement(random); }
    private static Object minecraft$getCraftingRemainingItem(Item item) { return item.getCraftingRemainder(); }
}
