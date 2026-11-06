package com.cappleapple.astralrepository.crafting;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.HolderSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
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
    private static boolean minecraft$hasCraftingRemainingItem(ItemStack stack) { return stack.hasCraftingRemainingItem(); }
    private static ItemStack minecraft$getCraftingRemainingItem(ItemStack stack) { return stack.getCraftingRemainingItem(); }
    private static boolean minecraft$isEnchanted(ItemStack stack) { return stack.isEnchanted(); }
    private static int minecraft$getBurnTime(ItemStack stack, RecipeType<?> type) { return stack.getBurnTime(type); }
    private static int minecraft$getMaxDamage(ItemStack stack) { return stack.getMaxDamage(); }
    private static boolean minecraft$isDamageableItem(ItemStack stack) { return stack.isDamageableItem(); }
    private static void minecraft$setDamageValue(ItemStack stack,int value) { stack.setDamageValue(value); }
    private static boolean minecraft$isEnchantable(ItemStack stack) { return stack.isEnchantable(); }
    private static BlockPos minecraft$above(BlockPos position) { return position.above(); }
    private static BlockEntity minecraft$getBlockEntity(Level level, BlockPos position) { return level.getBlockEntity(position); }
    private static net.minecraft.world.item.crafting.RecipeManager minecraft$getRecipeManager(Level level) { return level.getRecipeManager(); }
    @SuppressWarnings({"rawtypes","unchecked"}) private static List<?> minecraft$getAllRecipesFor(net.minecraft.world.item.crafting.RecipeManager manager,RecipeType<?> type) { return manager.getAllRecipesFor((RecipeType)type); }
    private static net.minecraft.world.food.FoodProperties minecraft$getFoodProperties(Item item, ItemStack stack, net.minecraft.world.entity.LivingEntity entity) { return item.getFoodProperties(stack, entity); }
    private static net.minecraft.world.food.FoodProperties minecraft$getFoodProperties(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) { return stack.getFoodProperties(entity); }
    private static int minecraft$nutrition(net.minecraft.world.food.FoodProperties food) { return food.getNutrition(); }
    private static float minecraft$saturation(net.minecraft.world.food.FoodProperties food) { return food.getSaturationModifier(); }
    private static boolean minecraft$isSame(Fluid fluid, Fluid other) { return fluid.isSame(other); }
    private static boolean minecraft$is(Fluid fluid, TagKey<Fluid> tag) { return fluid.is(tag); }
    private static DyeColor minecraft$byId(int id) { return DyeColor.byId(id); }
    private static RandomSource minecraft$worldRandom(Level level) { return level.random; }
    private static int minecraft$getDuration(MobEffectInstance effect) { return effect.getDuration(); }
    private static int minecraft$getAmplifier(MobEffectInstance effect) { return effect.getAmplifier(); }
    private static net.minecraft.core.NonNullList<Ingredient> minecraft$getIngredients(Recipe<?> recipe) { return recipe.getIngredients(); }
    private static ItemStack minecraft$getResultItem(Recipe<?> recipe, net.minecraft.core.RegistryAccess registries) { return recipe.getResultItem(registries); }
    private static RecipeType<?> minecraft$getType(Recipe<?> recipe) { return recipe.getType(); }
    private static int minecraft$getCookingTime(AbstractCookingRecipe recipe) { return recipe.getCookingTime(); }
    private static float minecraft$getExperience(AbstractCookingRecipe recipe) { return recipe.getExperience(); }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static boolean minecraft$matches(Recipe recipe, net.minecraft.world.Container input, Level level) { return recipe.matches(input, level); }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static ItemStack minecraft$assemble(Recipe recipe, net.minecraft.world.Container input, net.minecraft.core.RegistryAccess registries) { return recipe.assemble(input, registries); }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static net.minecraft.core.NonNullList<ItemStack> minecraft$getRemainingItems(Recipe recipe, net.minecraft.world.Container input) { return recipe.getRemainingItems(input); }
    private static Object minecraft$value(net.minecraft.core.Holder<?> holder) { return holder.value(); }
    private static net.minecraft.core.RegistryAccess minecraft$registryAccess(Level level) { return level.registryAccess(); }
    private static <T> boolean minecraft$is(net.minecraft.core.Holder<T> holder,TagKey<T> tag) { return holder.is(tag); }
    private static int minecraft$calculateIncreasedRepairCost(int cost) { return net.minecraft.world.inventory.AnvilMenu.calculateIncreasedRepairCost(cost); }
    private static boolean minecraft$isValidRepairItem(Item item, ItemStack input, ItemStack material) { return item.isValidRepairItem(input,material); }
    private static boolean minecraft$canEnchant(net.minecraft.world.item.enchantment.Enchantment enchantment,ItemStack stack) { return enchantment.canEnchant(stack); }
    private static int minecraft$getMaxLevel(net.minecraft.world.item.enchantment.Enchantment enchantment) { return enchantment.getMaxLevel(); }
    private static boolean minecraft$test(Ingredient ingredient, ItemStack stack) { return ingredient.test(stack); }
    private static boolean minecraft$isEmpty(Ingredient ingredient) { return ingredient.isEmpty(); }
    private static ItemStack[] minecraft$getItems(Ingredient ingredient) { return ingredient.getItems(); }
    private static java.util.Collection<ItemStack> minecraft$getItems(Ingredient.Value value) { return value.getItems(); }
    private static net.minecraft.world.item.DyeItem minecraft$byColor(net.minecraft.world.item.DyeColor color) { return net.minecraft.world.item.DyeItem.byColor(color); }
    private static int minecraft$getDamageValue(ItemStack stack) { return stack.getDamageValue(); }
    private static BlockPos minecraft$getBlockPos(BlockEntity entity) { return entity.getBlockPos(); }
    private static Level minecraft$getLevel(BlockEntity entity) { return entity.getLevel(); }
    private static boolean minecraft$isRemoved(BlockEntity entity) { return entity.isRemoved(); }
    private static void minecraft$setChanged(BlockEntity entity) { entity.setChanged(); }
    private static net.minecraft.nbt.CompoundTag minecraft$getComponentsPatch(ItemStack stack) { return stack.getTag(); }
    private static Optional<?> minecraft$getTag(Registry<Item> registry, TagKey<Item> tag) { return registry.getTag(tag); }
    private static Optional<?> minecraft$getTag(DefaultedRegistry<Item> registry, TagKey<Item> tag) { return registry.getTag(tag); }
    private static Optional<?> minecraft$getRandomElement(HolderSet<Item> values, RandomSource random) { return values.getRandomElement(random); }
    private static Optional<?> minecraft$getRandomElement(HolderSet.Named<Item> values, RandomSource random) { return values.getRandomElement(random); }
    private static boolean minecraft$hasCraftingRemainingItem(Item item) { return item.hasCraftingRemainingItem(); }
    private static Item minecraft$getCraftingRemainingItem(Item item) { return item.getCraftingRemainingItem(); }
    private static java.util.List<MobEffectInstance> minecraft$getAllEffects(ItemStack stack) { return net.minecraft.world.item.alchemy.PotionUtils.getMobEffects(stack); }
    private static java.util.Map<net.minecraft.world.item.enchantment.Enchantment,Integer> minecraft$getEnchantments(ItemStack stack) { return net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(stack); }
    private static net.minecraft.world.item.alchemy.Potion minecraft$getPotion(ItemStack stack) { return net.minecraft.world.item.alchemy.PotionUtils.getPotion(stack); }
    private static java.util.List<MobEffectInstance> minecraft$getEffects(net.minecraft.world.item.alchemy.Potion potion) { return potion.getEffects(); }
    private static ItemStack minecraft$enchantItem(RandomSource random, ItemStack input, int level, boolean treasure) { return net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(random,input,level,treasure); }
    private static boolean minecraft$hasTag(ItemStack stack) { return stack.hasTag(); }
    private static net.minecraft.nbt.CompoundTag minecraft$getTagElement(ItemStack stack, String key) { return stack.getTagElement(key); }
    private static net.minecraft.nbt.CompoundTag minecraft$getCompound(net.minecraft.nbt.CompoundTag tag, String key) { return tag.getCompound(key); }
    private static net.minecraft.nbt.ListTag minecraft$getList(net.minecraft.nbt.CompoundTag tag, String key, int type) { return tag.getList(key,type); }
    private static int minecraft$getInt(net.minecraft.nbt.CompoundTag tag, String key) { return tag.getInt(key); }
    private static byte minecraft$getByte(net.minecraft.nbt.CompoundTag tag, String key) { return tag.getByte(key); }
    private static boolean minecraft$getBoolean(net.minecraft.nbt.CompoundTag tag, String key) { return tag.getBoolean(key); }
    private static int[] minecraft$getIntArray(net.minecraft.nbt.CompoundTag tag, String key) { return tag.getIntArray(key); }
    private static net.minecraft.nbt.CompoundTag minecraft$getCompound(net.minecraft.nbt.ListTag tag, int index) { return tag.getCompound(index); }
    private static net.minecraft.world.item.enchantment.Enchantment.Rarity minecraft$getRarity(net.minecraft.world.item.enchantment.Enchantment enchantment) { return enchantment.getRarity(); }
    private static int minecraft$getWeight(net.minecraft.world.item.enchantment.Enchantment.Rarity rarity) { return rarity.getWeight(); }
    private static int minecraft$getMinCost(net.minecraft.world.item.enchantment.Enchantment enchantment, int level) { return enchantment.getMinCost(level); }
    private static boolean minecraft$isCurse(net.minecraft.world.item.enchantment.Enchantment enchantment) { return enchantment.isCurse(); }
    private static int minecraft$getBaseRepairCost(ItemStack stack) { return stack.getBaseRepairCost(); }
    private static void minecraft$removeTagKey(ItemStack stack, String key) { stack.removeTagKey(key); }
    private static void minecraft$setRepairCost(ItemStack stack, int cost) { stack.setRepairCost(cost); }
    private static net.minecraft.world.item.FireworkRocketItem.Shape[] minecraft$values(net.minecraft.world.item.FireworkRocketItem.Shape ignored) { return net.minecraft.world.item.FireworkRocketItem.Shape.values(); }
    private static net.minecraft.nbt.ListTag minecraft$getEnchantments(net.minecraft.world.item.EnchantedBookItem ignored, ItemStack stack) { return net.minecraft.world.item.EnchantedBookItem.getEnchantments(stack); }
    private static net.minecraft.resources.ResourceLocation minecraft$getId(Recipe<?> recipe) { return recipe.getId(); }
    private static boolean minecraft$isCompatibleWith(net.minecraft.world.item.enchantment.Enchantment first, net.minecraft.world.item.enchantment.Enchantment second) { return first.isCompatibleWith(second); }
    private static void minecraft$setEnchantments(java.util.Map<net.minecraft.world.item.enchantment.Enchantment,Integer> enchantments, ItemStack stack) { net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(enchantments,stack); }
}
