package com.cappleapple.astralrepository.menu;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.RecipeTomeItem;
import com.cappleapple.astralrepository.network.RecipeTomePackets;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** A held-book editing session. All recipe identities and saved outputs originate on the server. */
public final class RecipeTomeMenu extends AbstractContainerMenu {
    public static final int PAGE_SIZE = 1;
    public record Entry(ResourceLocation recipe, ResourceLocation type, ItemStack output, List<ItemStack> ingredients) {}
    private final Player owner;
    private final InteractionHand hand;
    private final ItemStack tome;
    private List<Entry> catalogue;
    private String query = "";
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
            query="="+item;page=0;status="";
        } else if (action.kind() == 1) {
            ResourceLocation recipe = ResourceLocation.tryParse(action.recipe());
            status = recipe != null && inscribe(recipe) ? "Inscribed." : "That recipe is no longer available.";
        } else return;
        sendPage();
    }
    /** Resolves the current server recipe again, including after a datapack reload. Never accepts a client ItemStack. */
    public boolean inscribe(ResourceLocation recipeId) {
        if (!(owner instanceof ServerPlayer player) || !stillValid(owner)) return false;
        var holder = player.server.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty()) return false;
        ItemStack output = output(holder.get(), player);
        if (output.isEmpty()) return false;
        CompoundTag tag = tome.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put("Output", output.copyWithCount(1).save(player.registryAccess()));
        tag.putString("OutputId", BuiltInRegistries.ITEM.getKey(output.getItem()).toString());
        tag.putString("Recipe", recipeId.toString());
        tome.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return true;
    }
    public RecipeTomePackets.Page browse(String search, int requestedPage) {
        if (!(owner instanceof ServerPlayer player) || !stillValid(owner))
            return new RecipeTomePackets.Page(containerId, 0, 0, ItemStack.EMPTY, "Hold the tome to edit it.", List.of());
        if (catalogue == null) {
            List<Entry> entries = new ArrayList<>();
            for (RecipeHolder<?> recipe : player.server.getRecipeManager().getRecipes()) {
                ItemStack output = output(recipe, player);
                if (!output.isEmpty()) entries.add(new Entry(recipe.id(), BuiltInRegistries.RECIPE_TYPE.getKey(recipe.value().getType()), output, ingredients(recipe)));
            }
            entries.sort(Comparator.comparing((Entry entry) -> entry.output().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER).thenComparing(entry -> entry.recipe().toString()));
            catalogue = List.copyOf(entries);
        }
        String normalized = search.strip().toLowerCase(Locale.ROOT);
        List<Entry> matches = catalogue.stream().filter(entry -> matches(entry, normalized)).toList();
        int selectedPage = Math.clamp(requestedPage, 0, Math.max(0, (matches.size() - 1) / PAGE_SIZE));
        return new RecipeTomePackets.Page(containerId, selectedPage, matches.size(), RecipeTomeItem.product(tome, player.registryAccess()), status,
                matches.stream().skip((long) selectedPage * PAGE_SIZE).limit(PAGE_SIZE).toList());
    }
    private void sendPage() { if (owner instanceof ServerPlayer player) {
        if(query.isBlank())RecipeTomePackets.send(player,new RecipeTomePackets.Page(containerId,0,0,RecipeTomeItem.product(tome,player.registryAccess()),"",List.of()));
        else RecipeTomePackets.send(player,browse(query,page));
    } }
    private static ItemStack output(RecipeHolder<?> recipe, ServerPlayer player) {
        try {
            ItemStack output = recipe.value().getResultItem(player.registryAccess());
            return output.isEmpty() || !output.isItemEnabled(player.level().enabledFeatures()) ? ItemStack.EMPTY : output.copy();
        } catch (RuntimeException invalidRecipe) { return ItemStack.EMPTY; }
    }
    private static List<ItemStack> ingredients(RecipeHolder<?> holder) {
        var ingredients = holder.value().getIngredients();
        List<ItemStack> preview = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        for (int i = 0; i < Math.min(9, ingredients.size()); i++) {
            int slot = holder.value() instanceof ShapedRecipe shaped ? (i / shaped.getWidth()) * 3 + i % shaped.getWidth() : i;
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

