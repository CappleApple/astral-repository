package com.cappleapple.astralrepository.menu;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.RecipeTomeItem;
import com.cappleapple.astralrepository.network.RecipeTomePackets;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** A held-book editing session. All recipe identities and saved outputs originate on the server. */
public final class RecipeTomeMenu extends AbstractContainerMenu {
    public static final int PAGE_SIZE = 1;
    public record Entry(ResourceLocation recipe, ResourceLocation type, ItemStack output, List<ItemStack> ingredients, boolean excluded) {
        public Entry(ResourceLocation recipe, ResourceLocation type, ItemStack output, List<ItemStack> ingredients) {
            this(recipe, type, output, ingredients, false);
        }
    }
    private final Player owner;
    private final InteractionHand hand;
    private final ItemStack tome;
    private List<Entry> catalogue;
    private String query = "";
    private ItemStack selectedOutput = ItemStack.EMPTY;
    private int page;
    private boolean initialized;
    private String status = "";

    public RecipeTomeMenu(int id, Inventory inventory) { this(id, inventory, InteractionHand.MAIN_HAND); }
    public RecipeTomeMenu(int id, Inventory inventory, InteractionHand hand) {
        super(AstralRepository.RECIPE_TOME_MENU.get(), id);
        this.owner = inventory.player;
        this.hand = hand;
        this.tome = owner.getItemInHand(hand);
        for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new net.minecraft.world.inventory.Slot(inventory,9+y*9+x,15+x*18,222+y*18));
        for(int x=0;x<9;x++)addSlot(new net.minecraft.world.inventory.Slot(inventory,x,15+x*18,280));
    }
    @Override public boolean stillValid(Player player) {
        return owner == player && owner.getItemInHand(hand) == tome && tome.is(AstralContent.RECIPE_TOME.get());
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (!initialized && owner instanceof ServerPlayer && stillValid(owner)) { initialized = true; sendPage(); }
    }
    public void action(RecipeTomePackets.Action action) {
        if (!(owner instanceof ServerPlayer) || !stillValid(owner) || action.menu() != containerId) return;
        if (action.kind() == 0) {
            query = action.query().strip().toLowerCase(Locale.ROOT);
            page = Math.max(0, action.page());
        } else if(action.kind()==2){
            ResourceLocation item=ResourceLocation.tryParse(action.query());
            if(item==null||!BuiltInRegistries.ITEM.containsKey(item))return;
            selectedOutput = action.selection().isEmpty() ? ItemStack.EMPTY : action.selection().copyWithCount(1);
            if (!selectedOutput.isEmpty() && !BuiltInRegistries.ITEM.getKey(selectedOutput.getItem()).equals(item)) return;
            query="="+item;page=0;status="";
        } else if (action.kind() == 1) {
            ResourceLocation recipe = ResourceLocation.tryParse(action.recipe());
            status = recipe != null && inscribe(recipe) ? "Inscribed." : "That recipe is no longer available.";
        } else if (action.kind() == 3 || action.kind() == 4) {
            ResourceLocation recipe = ResourceLocation.tryParse(action.recipe());
            status = recipe != null && exclude(recipe, action.kind() == 3)
                    ? (action.kind() == 3 ? "Recipe blocked for this tome." : "Recipe allowed for this tome.")
                    : "Inscribe an output and choose an available recipe first.";
        } else return;
        sendPage();
    }
    /** Resolves the current server recipe again, including after a datapack reload. Never accepts a client ItemStack. */
    public boolean inscribe(ResourceLocation recipeId) {
        if (!(owner instanceof ServerPlayer player) || !stillValid(owner)) return false;
        ItemStack output = resolveOutput(recipeId, player);
        if (output.isEmpty()) return false;
        CompoundTag tag = com.cappleapple.astralrepository.platform.Backport.customData(tome);
        if (!ItemStack.isSameItemSameTags(RecipeTomeItem.product(tome, player.level().registryAccess()), output)) tag.remove("ExcludedRecipes");
        tag.put("Output", output.copyWithCount(1).save(new CompoundTag()));
        tag.putString("OutputId", BuiltInRegistries.ITEM.getKey(output.getItem()).toString());
        tag.putString("Recipe", recipeId.toString());
        tome.setTag(tag);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return true;
    }
    /** Editing a path never changes the final product taught by this tome. */
    public boolean exclude(ResourceLocation recipeId, boolean excluded) {
        if (!(owner instanceof ServerPlayer player) || !stillValid(owner)
                || RecipeTomeItem.product(tome, player.level().registryAccess()).isEmpty()) return false;
        if (resolveOutput(recipeId, player).isEmpty()) return false;
        RecipeTomeItem.setRecipeExcluded(tome, recipeId.toString(), excluded);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return RecipeTomeItem.excludedRecipes(tome).contains(recipeId.toString()) == excluded;
    }
    public RecipeTomePackets.Page browse(String search, int requestedPage) {
        if (!(owner instanceof ServerPlayer player) || !stillValid(owner))
            return new RecipeTomePackets.Page(containerId, 0, 0, ItemStack.EMPTY, "Hold the tome to edit it.", List.of());
        if (catalogue == null) {
            List<Entry> entries = new ArrayList<>();
            for (Recipe<?> recipe : player.server.getRecipeManager().getRecipes()) {
                ItemStack output = output(recipe, player);
                if (!output.isEmpty()) entries.add(new Entry(recipe.getId(), BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()), output, ingredients(recipe)));
            }
            for (var brewing : com.cappleapple.astralrepository.crafting.BrewingRecipes.entries(player.serverLevel(), List.of()))
                entries.add(new Entry(brewing.id(), new ResourceLocation("minecraft", "brewing"), brewing.output(),
                        List.of(brewing.input(), brewing.reagent())));
            entries.sort(Comparator.comparing((Entry entry) -> entry.output().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER).thenComparing(entry -> entry.recipe().toString()));
            catalogue = List.copyOf(entries);
        }
        String normalized = search.strip().toLowerCase(Locale.ROOT);
        List<Entry> matches = catalogue.stream().filter(entry -> matches(entry, normalized) && (!normalized.startsWith("=") || selectedOutput.isEmpty() || ItemStack.isSameItemSameTags(entry.output(), selectedOutput))).toList();
        int selectedPage = com.cappleapple.astralrepository.platform.Backport.clamp(requestedPage, 0, Math.max(0, (matches.size() - 1) / PAGE_SIZE));
        return new RecipeTomePackets.Page(containerId, selectedPage, matches.size(), RecipeTomeItem.product(tome, player.level().registryAccess()), status,
                matches.stream().skip((long) selectedPage * PAGE_SIZE).limit(PAGE_SIZE)
                        .map(entry -> new Entry(entry.recipe(), entry.type(), entry.output(), entry.ingredients(),
                                RecipeTomeItem.excludedRecipes(tome).contains(entry.recipe().toString()))).toList());
    }
    private void sendPage() { if (owner instanceof ServerPlayer player) {
        if(query.isBlank())RecipeTomePackets.send(player,new RecipeTomePackets.Page(containerId,0,0,RecipeTomeItem.product(tome,player.level().registryAccess()),"",List.of()));
        else RecipeTomePackets.send(player,browse(query,page));
    } }
    private static ItemStack resolveOutput(ResourceLocation recipeId, ServerPlayer player) {
        var holder = player.server.getRecipeManager().byKey(recipeId);
        if (holder.isPresent()) return output(holder.get(), player);
        return com.cappleapple.astralrepository.crafting.BrewingRecipes.entries(player.serverLevel(), List.of()).stream()
                .filter(entry -> entry.id().equals(recipeId)).map(entry -> entry.output().copy()).findFirst().orElse(ItemStack.EMPTY);
    }
    private static ItemStack output(Recipe<?> recipe, ServerPlayer player) {
        try {
            ItemStack output = com.cappleapple.astralrepository.crafting.MachineRecipeSupport.output(recipe, player.level().registryAccess());
            return output.isEmpty() || !output.isItemEnabled(player.level().enabledFeatures()) ? ItemStack.EMPTY : output.copy();
        } catch (RuntimeException invalidRecipe) { return ItemStack.EMPTY; }
    }
    private static List<ItemStack> ingredients(Recipe<?> holder) {
        if (com.cappleapple.astralrepository.crafting.MekanismRecipeDiscovery.implementsContract(holder.getClass(), "mekanism.api.recipes.MekanismRecipe")) {
            var preview = new ArrayList<ItemStack>();
            for (var input : com.cappleapple.astralrepository.crafting.MekanismRecipeDiscovery.inputs(holder)) {
                if (input.representations().isEmpty() || preview.size() >= 9) continue;
                Object sample = input.representations().get(0);
                long amount = input.amount(sample);
                if (amount <= 0) continue;
                var key = com.cappleapple.astralrepository.crafting.MekanismResourceSupport.key(sample);
                var display = com.cappleapple.astralrepository.crafting.ResourcePresentation.icon(key);
                if (sample instanceof ItemStack) display.setCount((int)Math.min(amount, Integer.MAX_VALUE));
                else {
                display.setHoverName(net.minecraft.network.chat.Component.literal(
                        key.id() + " (" + amount + (key instanceof com.cappleapple.astralrepository.api.FluidKey ? " mB)" : " units)")));
                }
                preview.add(display);
            }
            return List.copyOf(preview);
        }
        var ingredients = holder.getIngredients();
        if (!(holder instanceof net.minecraft.world.item.crafting.CraftingRecipe)) {
            var preview = new ArrayList<ItemStack>();
            for (var input : com.cappleapple.astralrepository.crafting.MachineRecipeSupport.inputs(holder)) {
                ItemStack[] choices = input.ingredient().getItems();
                if (choices.length > 0 && preview.size() < 9) preview.add(choices[0].copyWithCount(input.count()));
            }
            return List.copyOf(preview);
        }
        List<ItemStack> preview = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        for (int i = 0; i < Math.min(9, ingredients.size()); i++) {
            int slot = holder instanceof ShapedRecipe shaped ? (i / shaped.getWidth()) * 3 + i % shaped.getWidth() : i;
            ItemStack[] choices = ingredients.get(i).getItems();
            if (slot < 9 && choices.length > 0) preview.set(slot, choices[0].copyWithCount(1));
        }
        return List.copyOf(preview);
    }
    private static boolean matches(Entry entry, String query) {
        if(query.startsWith("="))return BuiltInRegistries.ITEM.getKey(entry.output().getItem()).toString().equals(query.substring(1));
        if (query.isBlank()) return true;
        if (query.startsWith("@")) return BuiltInRegistries.ITEM.getKey(entry.output().getItem()).getNamespace().contains(query.substring(1));
        if (query.startsWith("#")) return entry.output().getTags().anyMatch(tag -> tag.location().toString().contains(query.substring(1)));
        return entry.recipe().toString().contains(query) || BuiltInRegistries.ITEM.getKey(entry.output().getItem()).toString().contains(query)
                || entry.output().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query);
    }
}

