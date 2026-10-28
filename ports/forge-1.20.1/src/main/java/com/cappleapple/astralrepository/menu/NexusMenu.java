package com.cappleapple.astralrepository.menu;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import net.minecraft.core.GlobalPos;
import com.cappleapple.astralrepository.platform.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import java.util.*;

/** Shared local/handheld menu. Network entries are bounded scrolling records, never phantom inventory slots. */
public final class NexusMenu extends AbstractContainerMenu {
    public record Entry(ItemStack stack, long count, boolean craftable) {}
    public final CraftingContainer grid = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer result = new ResultContainer();
    private net.minecraft.world.item.crafting.CraftingRecipe selectedCraftingRecipe;
    private net.minecraft.resources.ResourceLocation preferredCraftingRecipe;
    private final Player player;
    public final GlobalPos origin;
    private final boolean remote;
    private final CapacityInventory localStorage;
    private String query = "", cachedQuery = "", sentQuery = "";
    private long cachedVersion = -1, nextUpdate, feedbackUntil;
    private AstralNetwork cachedNetwork;
    private Map<ItemKey,Long> cachedItems = Map.of();
    private Set<ItemKey> cachedKeys = Set.of(), cachedCraftable = Set.of();
    private List<ItemKey> sortedKeys = List.of(), filteredKeys = List.of();
    private boolean searchPending;
    private NetworkPackets.Page previousPage;
    public List<Entry> entries = List.of();
    public List<NetworkPackets.Job> jobs = List.of();
    public String error = "";
    public int totalEntries, scrollRow;
    public long syncRevision;
    private long acknowledgedRequest;
    private boolean committingInteraction, insideClick, handledShiftCraft;
    private List<ItemStack> deferredRefill;
    private PendingInteraction pendingInteraction;
    private boolean recipeInventorySubscribed;
    private long recipeInventoryRevision, clientRecipeInventoryRevision;
    private Map<ItemKey,Long> sentRecipeInventory, pendingRecipeInventory, clientRecipeInventory = Map.of();
    private final Deque<NetworkPackets.RecipeInventory> recipeInventoryUpdates = new ArrayDeque<>();
    private record PendingInteraction(long due,List<ItemStack> slots,ItemStack carried,Runnable action) {}
    public NexusMenu(int id, Inventory inventory) { this(id, inventory, null, false); }
    public NexusMenu(int id, Inventory inventory, GlobalPos origin, boolean remote) {
        super(AstralRepository.NEXUS_MENU.get(), id);
        this.player = inventory.player; this.origin = origin; this.remote = remote;
        localStorage=!remote && origin!=null && player instanceof ServerPlayer sp
                && sp.server.getLevel(origin.dimension())!=null
                && sp.server.getLevel(origin.dimension()).getBlockEntity(origin.pos()) instanceof CrystalNodeBlockEntity node
                && (node.kind()==NodeKind.STORAGE||node.kind()==NodeKind.BUFFER)?node.inventory():null;
        addSlot(new ResultSlot(player, grid, result, 0, 284, 72) {
            @Override public void onTake(Player player,ItemStack stack) {
                List<ItemStack> pattern=copyGrid();
                takeCraftingResult(player,stack,this::checkTakeAchievements);
                if(committingInteraction||LogisticsTiming.playerDelayTicks()==0)LogisticsTiming.playerInteraction(()->refillGrid(pattern));
                else if(deferredRefill==null)deferredRefill=pattern;
            }
        });
        for (int y=0;y<3;y++) for(int x=0;x<3;x++) addSlot(new Slot(grid,x+y*3,208+x*18,54+y*18));
        for (int y=0;y<3;y++) for(int x=0;x<9;x++) addSlot(new Slot(inventory,x+y*9+9,8+x*18,180+y*18));
        for (int x=0;x<9;x++) addSlot(new Slot(inventory,x,8+x*18,238));
    }
    public AstralNetwork network() { return player instanceof ServerPlayer p && origin != null ? NetworkManager.get(p.server).networkAt(origin) : null; }
    private Map<ItemKey,Long> storedItems(AstralNetwork network){
        if(localStorage==null)return network.snapshot();
        Map<ItemKey,Long> items=new HashMap<>();
        for(int slot=0;slot<localStorage.getSlots();slot++){ItemStack stack=localStorage.getStackInSlot(slot);if(!stack.isEmpty())items.merge(new ItemKey(stack),(long)stack.getCount(),Long::sum);}
        return items;
    }
    private ItemStack withdraw(AstralNetwork network,ItemKey key,int amount){
        if(localStorage==null)return network.extractAt(key,amount,origin);
        for(int slot=0;slot<localStorage.getSlots();slot++)if(ItemStack.isSameItemSameTags(localStorage.getStackInSlot(slot),key.sample())){ItemStack extracted=localStorage.extractItem(slot,amount,false);if(!extracted.isEmpty())network.providerChanged(origin);return extracted;}
        return ItemStack.EMPTY;
    }
    private ItemStack deposit(AstralNetwork network,ItemStack stack){
        if(localStorage==null)return network.insertAt(stack,origin);
        ItemStack rest=net.minecraftforge.items.ItemHandlerHelper.insertItem(localStorage,stack,false);
        if(rest.getCount()!=stack.getCount())network.providerChanged(origin);return rest;
    }
    private boolean payForAccess(AstralNetwork network,ServerPlayer player){return localStorage!=null||network.payForAccess(player,origin,remote);}
    @Override public boolean stillValid(Player p) {
        if (!(p instanceof ServerPlayer sp) || origin == null) return true;
        return NetworkManager.get(sp.server).canAccess(sp, origin, remote)
                && (localStorage==null || sp.server.getLevel(origin.dimension()).getBlockEntity(origin.pos()) instanceof CrystalNodeBlockEntity node && node.inventory()==localStorage);
    }
    @Override public void slotsChanged(Container container) {
        if (!(player instanceof ServerPlayer sp)) return;
        ItemStack output = ItemStack.EMPTY;
        var recipe=com.cappleapple.astralrepository.compat.PolymorphCompatibility.playerRecipe(this,grid,player);
        if(recipe==null){
        recipe=sp.server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,sp.level());
        if(preferredCraftingRecipe!=null){
            var preferred=sp.server.getRecipeManager().byKey(preferredCraftingRecipe);
            if(preferred.isPresent()&&preferred.get() instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting&&crafting.matches(grid,sp.level()))recipe=Optional.of(crafting);
        }
        }
        selectedCraftingRecipe=recipe.orElse(null);
        if (recipe.isPresent() && result.setRecipeUsed(sp.level(),sp,recipe.get())) {
            ItemStack assembled = recipe.get().assemble(grid,sp.level().registryAccess());
            if (assembled.isItemEnabled(sp.level().enabledFeatures())) output = assembled;
        }
        result.setItem(0,output); setRemoteSlot(0,output);
        sp.connection.send(new ClientboundContainerSetSlotPacket(containerId,incrementStateId(),0,output));
    }
    /** Selects only an existing server recipe that matches the grid after a recipe-viewer fill. */
    public boolean selectCraftingRecipe(net.minecraft.resources.ResourceLocation id){
        if(!(player instanceof ServerPlayer sp))return false;
        var recipe=sp.server.getRecipeManager().byKey(id);
        if(recipe.isEmpty()||!(recipe.get() instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting)||!crafting.matches(grid,sp.level()))return false;
        preferredCraftingRecipe=id;com.cappleapple.astralrepository.compat.PolymorphCompatibility.selectPlayerRecipe(player,recipe.get());slotsChanged(grid);return true;
    }
    private net.minecraft.core.NonNullList<ItemStack> craftingRemainders(CraftingContainer input){
        if(selectedCraftingRecipe!=null&&selectedCraftingRecipe.matches(input,player.level()))return selectedCraftingRecipe.getRemainingItems(input);
        return player.level().getRecipeManager().getRemainingItemsFor(RecipeType.CRAFTING,input,player.level());
    }
    private void takeCraftingResult(Player player,ItemStack output,java.util.function.Consumer<ItemStack> achievements){
        net.minecraft.core.NonNullList<ItemStack> remainders;
        net.minecraftforge.common.ForgeHooks.setCraftingPlayer(player);
        try{remainders=craftingRemainders(grid);}finally{net.minecraftforge.common.ForgeHooks.setCraftingPlayer(null);}
        achievements.accept(output);
        for(int slot=0;slot<grid.getContainerSize();slot++){
            ItemStack present=grid.getItem(slot),remainder=remainders.get(slot);
            if(!present.isEmpty()){grid.removeItem(slot,1);present=grid.getItem(slot);}
            if(!remainder.isEmpty()){
                if(present.isEmpty())grid.setItem(slot,remainder);
                else if(ItemStack.isSameItemSameTags(present,remainder)){remainder.grow(present.getCount());grid.setItem(slot,remainder);}
                else if(!player.getInventory().add(remainder))player.drop(remainder,false);
            }
        }
    }
    public void action(NetworkPackets.Action action) {
        if (!(player instanceof ServerPlayer sp) || action.menu()!=containerId || !stillValid(player)) return;
        if(action.kind()<NetworkPackets.SEARCH || action.kind()>NetworkPackets.CLEAR_GRID || action.kind()==NetworkPackets.CANCEL) return;
        AstralNetwork network = network(); if (network == null) return;
        if (action.kind() == NetworkPackets.SEARCH) {
            // Multiple keystrokes/wheel messages before the next broadcast collapse into one view.
            if(action.request()<acknowledgedRequest)return;acknowledgedRequest=action.request();
            query=action.query().toLowerCase(Locale.ROOT).strip();scrollRow=Math.max(0,action.row());searchPending=true;return;
        }
        if(sp.isSpectator())return;
        if(action.kind()==NetworkPackets.CRAFT&&(action.amount()<1||action.amount()>4096))return;
        if((action.kind()==NetworkPackets.PICKUP||action.kind()==NetworkPackets.DEPOSIT)&&(action.amount()<0||action.amount()>1))return;
        if(!committingInteraction){interact(()->action(action));return;}
        if(action.kind()==NetworkPackets.CLEAR_GRID){clearGrid();return;}
        if (action.kind() == NetworkPackets.CRAFT || action.kind() == NetworkPackets.CRAFT_STACK) {
            if(localStorage!=null)return;
            ItemStack target=network.exposedProducts().stream().filter(stack->ItemStack.isSameItemSameTags(stack,action.stack())).findFirst().orElse(ItemStack.EMPTY);
            if(target.isEmpty()){showError("Place a Recipe Tome for this product in a connected Chiseled Bookshelf");return;}
            int count=action.kind()==NetworkPackets.CRAFT_STACK?target.getMaxStackSize():action.amount();
            var result=network.crafting().request(sp,target,count);
            if(result.accepted())clearError();else showError(result.message());sendPage();return;
        }
        // Custom clicks identify a resource; only the server owns the carried stack and transfer size.
        if (!payForAccess(network,sp)) { showError("The network needs power for this action."); return; }
        if(action.kind()==NetworkPackets.PICKUP && getCarried().isEmpty() && !action.stack().isEmpty()) {
            ItemKey key=new ItemKey(action.stack());
            int available=(int)Math.min(storedItems(network).getOrDefault(key,0L),key.sample().getMaxStackSize());
            int requested=action.amount()==1?(available+1)/2:available;
            ItemStack withdrawn=withdraw(network,key,requested);
            if(!withdrawn.isEmpty()) { setCarried(withdrawn);clearError(); }
            else showError("No items could be withdrawn. Check source filters, reserved stock, and availability.");
        } else if (action.kind() == NetworkPackets.QUICK_WITHDRAW && !action.stack().isEmpty() && getCarried().isEmpty()) {
            ItemKey key=new ItemKey(action.stack());
            int requested=Math.min(key.sample().getMaxStackSize(),inventoryRoom(key.sample()));
            ItemStack withdrawn=withdraw(network,key,requested);
            if (!withdrawn.isEmpty()) {
                for(int i=10;i<slots.size()&&!withdrawn.isEmpty();i++) {
                    Slot slot=slots.get(i);
                    if(slot.hasItem()&&slot.getItem().getCount()<slot.getMaxStackSize(withdrawn)
                            &&ItemStack.isSameItemSameTags(slot.getItem(),withdrawn)) withdrawn=slot.safeInsert(withdrawn);
                }
                for(int i=10;i<slots.size()&&!withdrawn.isEmpty();i++)if(!slots.get(i).hasItem()) withdrawn=slots.get(i).safeInsert(withdrawn);
                if(!withdrawn.isEmpty())setCarried(withdrawn);clearError();
            } else showError(requested==0?"Your inventory is full.":"No items could be withdrawn. Check source filters, reserved stock, and availability.");
        } else if (action.kind() == NetworkPackets.DEPOSIT && !getCarried().isEmpty()) {
            ItemStack carried=getCarried();int offered=action.amount()==1?1:carried.getCount();
            ItemStack remainder=deposit(network,carried.copyWithCount(offered));int inserted=offered-remainder.getCount();
            setCarried(carried.copyWithCount(carried.getCount()-inserted));
            if(inserted<offered)showError("Deposited "+inserted+" of "+offered+". Check destination capacity and filters.");else clearError();
        }
        broadcastChanges();sendPage();
    }
    public void fillRecipe(NetworkPackets.FillRecipe packet) {
        if (!(player instanceof ServerPlayer sp) || sp.isSpectator() || packet.menu()!=containerId
                || packet.amount()<1 || packet.amount()>64 || !stillValid(player)) return;
        if (!committingInteraction) { interact(()->fillRecipe(packet)); return; }
        var holder=sp.server.getRecipeManager().byKey(packet.recipe()).orElse(null);
        if (holder==null || !(holder instanceof net.minecraft.world.item.crafting.CraftingRecipe recipe)
                || recipe.isSpecial() || recipe.getIngredients().isEmpty() || !recipe.canCraftInDimensions(3,3)) return;
        AstralNetwork network=network(); if(network==null)return;
        boolean alreadyFilled=recipe.matches(grid,sp.level());
        for(int i=0;alreadyFilled&&i<9;i++)if(!grid.getItem(i).isEmpty()&&grid.getItem(i).getCount()<packet.amount())alreadyFilled=false;
        if(alreadyFilled){
            if(!payForAccess(network,sp)){showError("The network needs power for this action.");return;}
            clearError();selectCraftingRecipe(recipe.getId());broadcastChanges();sendPage();return;
        }
        Map<ItemKey,Long> stock=new LinkedHashMap<>();
        List<ItemStack> ownedInventory=new ArrayList<>();for(int i=10;i<slots.size();i++)ownedInventory.add(slots.get(i).getItem().copy());
        List<ItemStack> previous=copyGrid();ItemStack ownedCarried=getCarried().copy();
        for(ItemStack stack:previous)if(!stack.isEmpty())stock.merge(new ItemKey(stack),(long)stack.getCount(),Long::sum);
        for(ItemStack stack:ownedInventory)if(!stack.isEmpty())stock.merge(new ItemKey(stack),(long)stack.getCount(),Long::sum);
        if(!ownedCarried.isEmpty())stock.merge(new ItemKey(ownedCarried),(long)ownedCarried.getCount(),Long::sum);
        // Prefer inputs already owned by the player before introducing another tag alternative.
        storedItems(network).forEach((key,count)->stock.merge(key,count,Long::sum));
        List<List<ItemKey>> ingredients=recipeIngredients(recipe,stock);
        if(ingredients==null){showError("Not enough ingredients in your inventory or Repository.");return;}
        List<ItemKey> plan=null;Map<ItemKey,Integer> needed=null;List<ItemStack> inventory=null;ItemStack carried=ItemStack.EMPTY;
        int amount=packet.amount();boolean hasMaterials=false;
        for(;amount>0;amount--){
            List<ItemKey> candidate=RecipeTransferPlan.assign(ingredients,stock,amount,key->key.sample().getMaxStackSize());
            if(candidate==null)continue;hasMaterials=true;
            List<ItemStack> plannedGrid=new ArrayList<>();for(ItemKey key:candidate)plannedGrid.add(key==null?ItemStack.EMPTY:key.sample().copyWithCount(amount));
            CraftingContainer plannedInput=new TransientCraftingContainer(this,3,3);for(int i=0;i<9;i++)plannedInput.setItem(i,plannedGrid.get(i));
            if(!recipe.matches(plannedInput,sp.level()))return;
            Map<ItemKey,Integer> requirements=new LinkedHashMap<>();for(ItemKey key:candidate)if(key!=null)requirements.merge(key,amount,Integer::sum);
            List<ItemStack> leftovers=new ArrayList<>();for(ItemStack stack:previous)leftovers.add(consumeRecipeInput(stack,requirements));
            List<ItemStack> remainingInventory=new ArrayList<>();for(ItemStack stack:ownedInventory)remainingInventory.add(consumeRecipeInput(stack,requirements));
            ItemStack remainingCarried=consumeRecipeInput(ownedCarried,requirements);
            boolean fits=true;for(ItemStack stack:leftovers)if(!stack.isEmpty()&&!fitInventory(remainingInventory,stack)){fits=false;break;}
            if(!fits)continue;
            plan=candidate;needed=requirements;inventory=remainingInventory;carried=remainingCarried;break;
        }
        if(plan==null){showError(hasMaterials?"Make room in your inventory for the current crafting grid.":"Not enough ingredients in your inventory or Repository.");return;}
        if(!payForAccess(network,sp)){showError("The network needs power for this action.");return;}
        List<ItemStack> extracted=new ArrayList<>();
        for(var requirement:needed.entrySet()){
            int remaining=requirement.getValue();
            while(remaining>0){
                ItemStack stack=withdraw(network,requirement.getKey(),remaining);if(stack.isEmpty())break;
                extracted.add(stack);remaining-=stack.getCount();
            }
            if(remaining>0){
                for(ItemStack stack:extracted){ItemStack rest=deposit(network,stack);if(!rest.isEmpty())sp.getInventory().placeItemBackInInventory(rest);}
                showError("Ingredients are unavailable. Check source filters and reserved stock.");broadcastChanges();return;
            }
        }
        for(int i=0;i<inventory.size();i++)slots.get(i+10).setByPlayer(inventory.get(i));
        setCarried(carried);
        for(int i=0;i<9;i++){ItemKey key=i<plan.size()?plan.get(i):null;grid.setItem(i,key==null?ItemStack.EMPTY:key.sample().copyWithCount(amount));}
        clearError();selectCraftingRecipe(recipe.getId());broadcastChanges();sendPage();
    }
    private static ItemStack consumeRecipeInput(ItemStack stack,Map<ItemKey,Integer> needed){
        if(stack.isEmpty())return ItemStack.EMPTY;
        ItemKey key=new ItemKey(stack);int used=Math.min(stack.getCount(),needed.getOrDefault(key,0));
        if(used>0)needed.put(key,needed.get(key)-used);
        return stack.copyWithCount(stack.getCount()-used);
    }
    public static List<List<ItemKey>> recipeIngredients(net.minecraft.world.item.crafting.CraftingRecipe recipe,Map<ItemKey,Long> stock){
        List<List<ItemKey>> result=new ArrayList<>(Collections.nCopies(9,List.of()));
        var ingredients=recipe.getIngredients();
        int width=recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped?shaped.getWidth():3;
        for(int i=0;i<ingredients.size();i++){
            var ingredient=ingredients.get(i);int target=recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe?i%width+(i/width)*3:i;
            if(target>=9)return null;
            if(!ingredient.isEmpty())result.set(target,stock.keySet().stream().filter(key->stock.get(key)>0&&ingredient.test(key.sample())).toList());
            if(!ingredient.isEmpty()&&result.get(target).isEmpty())return null;
        }
        return result;
    }
    public void subscribeRecipeInventory(){
        if(player instanceof ServerPlayer sp&&!sp.isSpectator()&&stillValid(player))recipeInventorySubscribed=true;
    }
    /** Exact stored identities for recipe viewers, independent of the current search and scroll. */
    public Map<ItemKey,Long> recipeInventory(){
        Map<ItemKey,Long> stock=new LinkedHashMap<>(clientRecipeInventory);
        for(int i=1;i<slots.size();i++){ItemStack stack=slots.get(i).getItem();if(!stack.isEmpty())stock.merge(new ItemKey(stack),(long)stack.getCount(),Long::sum);}
        return stock;
    }
    public void acceptRecipeInventory(NetworkPackets.RecipeInventory update){
        if(update.menu()!=containerId||update.revision()<clientRecipeInventoryRevision)return;
        if(update.reset()){clientRecipeInventoryRevision=update.revision();pendingRecipeInventory=new LinkedHashMap<>(update.clear()?Map.of():clientRecipeInventory);}
        if(update.revision()!=clientRecipeInventoryRevision||pendingRecipeInventory==null)return;
        for(Entry entry:update.entries()){ItemKey key=new ItemKey(entry.stack());if(entry.count()==0)pendingRecipeInventory.remove(key);else pendingRecipeInventory.put(key,entry.count());}
        if(update.complete()){clientRecipeInventory=Map.copyOf(pendingRecipeInventory);pendingRecipeInventory=null;}
    }
    private void sendRecipeInventory(){
        if(!recipeInventorySubscribed||!(player instanceof ServerPlayer sp)||!stillValid(player))return;
        if(recipeInventoryUpdates.isEmpty()&&!Objects.equals(sentRecipeInventory,cachedItems)){
            List<Entry> changes=new ArrayList<>();
            cachedItems.forEach((key,count)->{if(count>0&&(sentRecipeInventory==null||!Objects.equals(sentRecipeInventory.get(key),count)))changes.add(new Entry(key.sample(),count,false));});
            if(sentRecipeInventory!=null)sentRecipeInventory.keySet().stream().filter(key->cachedItems.getOrDefault(key,0L)<=0).forEach(key->changes.add(new Entry(key.sample(),0,false)));
            long revision=++recipeInventoryRevision;
            for(int start=0;start<Math.max(1,changes.size());start+=NetworkPackets.WINDOW_SIZE){int end=Math.min(changes.size(),start+NetworkPackets.WINDOW_SIZE);recipeInventoryUpdates.add(new NetworkPackets.RecipeInventory(containerId,revision,start==0,sentRecipeInventory==null,end==changes.size(),List.copyOf(changes.subList(start,end))));}
            // cachedItems is a detached snapshot; retaining it makes idle viewer checks constant time.
            sentRecipeInventory=cachedItems;
        }
        for(int i=0;i<4&&!recipeInventoryUpdates.isEmpty();i++)NetworkPackets.sendUpdate(sp,recipeInventoryUpdates.removeFirst());
    }
    public void cancelJob(NetworkPackets.CancelJob packet) {
        if(!(player instanceof ServerPlayer sp)||sp.isSpectator()||packet.menu()!=containerId||!stillValid(player))return;
        AstralNetwork network=network();if(network==null)return;
        // Unknown, terminal, replayed and other players' UUIDs have no effect.
        if(localStorage==null&&network.crafting().cancel(packet.job(),sp.getUUID())){clearError();sendPage();}
    }
    private List<ItemStack> copyGrid(){List<ItemStack> result=new ArrayList<>();for(int i=0;i<grid.getContainerSize();i++)result.add(grid.getItem(i).copy());return List.copyOf(result);}
    private static boolean sameStack(ItemStack a,ItemStack b){return a.getCount()==b.getCount()&&(a.isEmpty()&&b.isEmpty()||ItemStack.isSameItemSameTags(a,b));}
    private List<ItemStack> copySlots(){return slots.stream().map(slot->slot.getItem().copy()).toList();}
    private void commitInteraction(Runnable action){
        LogisticsTiming.playerInteraction(()->{boolean prior=committingInteraction;committingInteraction=true;try{action.run();}finally{committingInteraction=prior;}});
    }
    private void interact(Runnable action){
        if(pendingInteraction!=null)return;
        if(LogisticsTiming.playerDelayTicks()==0){commitInteraction(action);return;}
        if(player instanceof ServerPlayer sp)pendingInteraction=new PendingInteraction(sp.server.getTickCount()+LogisticsTiming.playerDelayTicks(),copySlots(),getCarried().copy(),action);
    }
    private void tickInteraction(){
        if(pendingInteraction==null||!(player instanceof ServerPlayer sp))return;
        PendingInteraction pending=pendingInteraction;
        boolean unchanged=!sp.isSpectator()&&sp.containerMenu==this&&stillValid(player)&&sameStack(getCarried(),pending.carried())&&slots.size()==pending.slots().size();
        for(int i=0;unchanged&&i<slots.size();i++)unchanged=sameStack(slots.get(i).getItem(),pending.slots().get(i));
        if(!unchanged){pendingInteraction=null;return;}
        if(sp.server.getTickCount()<pending.due())return;
        pendingInteraction=null;commitInteraction(pending.action());
    }
    private void flushDeferredRefill(){
        if(deferredRefill==null)return;List<ItemStack> pattern=deferredRefill;deferredRefill=null;interact(()->refillGrid(pattern));
    }
    private void clearGrid(){
        if(!(player instanceof ServerPlayer sp))return;
        if(grid.isEmpty()){clearError();sendPage();return;}
        AstralNetwork network=network();if(network==null)return;
        if(!payForAccess(network,sp)){showError("The network needs power for this action.");return;}
        boolean remaining=false;
        for(int i=0;i<grid.getContainerSize();i++){
            ItemStack input=grid.getItem(i);if(input.isEmpty())continue;
            ItemStack rest=deposit(network,input.copy());grid.setItem(i,rest);remaining|=!rest.isEmpty();
        }
        if(remaining)showError("Some ingredients remain in the grid. Check storage capacity and filters.");else clearError();sendPage();
    }
    private void refillGrid(List<ItemStack> pattern){
        if(!(player instanceof ServerPlayer sp)||sp.isSpectator()||!stillValid(player))return;
        AstralNetwork network=network();if(network==null)return;
        boolean needed=false;for(int i=0;i<pattern.size();i++)if(!pattern.get(i).isEmpty()&&!sameStack(pattern.get(i),grid.getItem(i))){needed=true;break;}
        if(!needed)return;
        if(!payForAccess(network,sp)){showError("The network needs power to refill the crafting grid.");return;}
        for(int i=0;i<pattern.size();i++){
            ItemStack wanted=pattern.get(i);if(wanted.isEmpty())continue;
            ItemStack present=grid.getItem(i);
            if(!present.isEmpty()&&!ItemStack.isSameItemSameTags(present,wanted)){
                // Vanilla's container remainder remains owned until storage actually accepts it.
                ItemStack rest=deposit(network,present.copy());grid.setItem(i,rest);if(!rest.isEmpty())continue;present=ItemStack.EMPTY;
            }
            int missing=Math.max(0,Math.min(wanted.getCount(),wanted.getMaxStackSize())-present.getCount());
            if(missing==0)continue;
            ItemStack extracted=withdraw(network,new ItemKey(wanted),missing);
            if(extracted.isEmpty())continue;
            if(present.isEmpty())grid.setItem(i,extracted);else{ItemStack filled=present.copy();filled.grow(extracted.getCount());grid.setItem(i,filled);}
        }
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        boolean outer=insideClick;if(!outer){insideClick=true;handledShiftCraft=false;}
        try{super.clicked(slot,button,type,player);}finally{if(!outer){insideClick=false;flushDeferredRefill();sendPage();}}
    }
    private int inventoryRoom(ItemStack stack) {
        int room=0;
        for(int i=10;i<slots.size();i++) {
            Slot slot=slots.get(i);ItemStack existing=slot.getItem();
            if(slot.mayPlace(stack)&&(existing.isEmpty()||ItemStack.isSameItemSameTags(existing,stack)))
                room+=Math.max(0,slot.getMaxStackSize(stack)-existing.getCount());
        }
        return room;
    }
    private static String clipped(String value){return value.length()>512?value.substring(0,509)+"...":value;}
    private void showError(String message) {
        error=clipped(message);feedbackUntil=player instanceof ServerPlayer sp?sp.server.getTickCount()+200L:0;sendPage();
    }
    private void clearError(){error="";feedbackUntil=0;}
    @Override public void broadcastChanges() {
        tickInteraction();super.broadcastChanges();
        if(player instanceof ServerPlayer sp&&(searchPending||sp.server.getTickCount()>=nextUpdate)){
            nextUpdate=sp.server.getTickCount()+2;sendPage();
        }
        sendRecipeInventory();
    }
    public void sendPage(){
        CustomPacketPayload update=collectUpdate();
        if(update!=null&&player instanceof ServerPlayer sp)NetworkPackets.sendUpdate(sp,update);
    }
    /** Builds and commits the next server view; an idle view produces no packet. */
    public CustomPacketPayload collectUpdate() {
        if(!(player instanceof ServerPlayer sp))return null;
        AstralNetwork network=network();if(network==null)return null;
        if(!error.isEmpty()&&sp.server.getTickCount()>=feedbackUntil)clearError();
        boolean networkChanged=network!=cachedNetwork;
        long version=localStorage==null?network.menuStorageVersion():localStorage.revision();
        boolean identitiesChanged=networkChanged;
        if(networkChanged||version!=cachedVersion){
            cachedItems=storedItems(network);Set<ItemKey> craftable=new HashSet<>();if(localStorage==null)network.exposedProducts().forEach(s->{if(!s.isEmpty())craftable.add(new ItemKey(s));});
            Set<ItemKey> keys=new HashSet<>(cachedItems.keySet());keys.addAll(craftable);
            identitiesChanged|=!keys.equals(cachedKeys);
            if(identitiesChanged){
                sortedKeys=keys.stream().sorted(Comparator.comparing((ItemKey k)->k.sample().getHoverName().getString(),String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(k->k.id().toString()).thenComparingInt(ItemKey::hashCode).thenComparing(k->k.sample().getOrCreateTag().toString())).toList();
                cachedKeys=Set.copyOf(keys);
            }
            cachedCraftable=Set.copyOf(craftable);cachedVersion=version;cachedNetwork=network;
        }
        if(identitiesChanged||!cachedQuery.equals(query)){
            filteredKeys=sortedKeys.stream().filter(key->matches(key,query)).toList();cachedQuery=query;
        }
        totalEntries=filteredKeys.size();scrollRow=com.cappleapple.astralrepository.platform.Backport.clamp(scrollRow,0,Math.max(0,(int)(((long)totalEntries+8)/9)-6));
        int start=scrollRow*9,end=Math.min(filteredKeys.size(),start+NetworkPackets.WINDOW_SIZE);
        entries=filteredKeys.subList(start,end).stream()
                .map(k->new Entry(k.sample(),cachedItems.getOrDefault(k,0L),cachedCraftable.contains(k))).toList();
        jobs=localStorage!=null?List.of():network.crafting().visibleStatuses(player.getUUID()).stream().map(job->new NetworkPackets.Job(job.id(),job.target(),job.count(),
                NetworkPackets.JobState.valueOf(job.state()),job.completed(),job.total(),job.active(),clipped(job.message()),job.missing().stream().limit(512).map(m->new NetworkPackets.Missing(m.alternatives().get(0).sample(),m.tag(),m.count())).toList())).toList();
        NetworkPackets.Page next=new NetworkPackets.Page(containerId,syncRevision+1,scrollRow,totalEntries,error,entries,jobs,acknowledgedRequest);
        CustomPacketPayload update=NetworkPackets.difference(previousPage,next,networkChanged||!sentQuery.equals(query));
        searchPending=false;
        if(update!=null){previousPage=next;syncRevision=next.revision();sentQuery=query;}
        return update;
    }
    public NetworkPackets.Page currentPage(){return previousPage;}
    public boolean acceptPage(NetworkPackets.Page page){
        if(page.menu()!=containerId||page.revision()<=syncRevision)return false;
        previousPage=page;syncRevision=page.revision();entries=page.entries();jobs=page.jobs();scrollRow=page.row();totalEntries=page.total();error=page.error();return true;
    }
    private static boolean matches(ItemKey key,String query) {
        if (query.isBlank()) return true;
        if (query.startsWith("@")) return key.id().getNamespace().contains(query.substring(1));
        if (query.startsWith("#")) return key.sample().getTags().anyMatch(tag -> tag.location().toString().contains(query.substring(1)));
        return key.id().toString().contains(query) || key.sample().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query);
    }
    @Override public ItemStack quickMoveStack(Player player,int slotIndex) {
        if(slotIndex<0||slotIndex>=slots.size())return ItemStack.EMPTY;
        if(slotIndex>=10&&player.level().isClientSide)return ItemStack.EMPTY;
        if(slotIndex>=10&&!committingInteraction){
            ItemStack[] moved={ItemStack.EMPTY};interact(()->moved[0]=quickMoveNow(player,slotIndex));return moved[0];
        }
        if(slotIndex==0){
            if(insideClick&&handledShiftCraft)return ItemStack.EMPTY;
            handledShiftCraft=true;
            if(!committingInteraction&&LogisticsTiming.playerDelayTicks()>0){
                interact(()->{shiftCraft(player);sendPage();});return ItemStack.EMPTY;
            }
            ItemStack crafted=LogisticsTiming.playerInteraction(()->shiftCraft(player));
            if(!insideClick){flushDeferredRefill();sendPage();}return crafted;
        }
        return quickMoveNow(player,slotIndex);
    }
    private ItemStack shiftCraft(Player player){
        ItemStack first=slots.get(0).getItem().copy();if(first.isEmpty())return ItemStack.EMPTY;
        int limit=first.getMaxStackSize(),made=0;boolean any=false;
        while(made<limit){
            ItemStack output=slots.get(0).getItem();
            if(output.isEmpty()||!ItemStack.isSameItemSameTags(output,first)||output.getCount()>limit-made)break;
            if(!fitsCraftBatch(output))break;
            int batch=output.getCount();if(quickMoveNow(player,0).isEmpty())break;made+=batch;any=true;
        }
        return any?first:ItemStack.EMPTY;
    }
    /** Include remainders that vanilla must put in inventory because their grid slot stays occupied. */
    private boolean fitsCraftBatch(ItemStack output){
        List<ItemStack> inventory=new ArrayList<>();for(int i=10;i<slots.size();i++)inventory.add(slots.get(i).getItem().copy());
        if(!fitInventory(inventory,output))return false;
        var input=grid;
        net.minecraft.core.NonNullList<ItemStack> remainders;
        net.minecraftforge.common.ForgeHooks.setCraftingPlayer(player);
        try{remainders=craftingRemainders(input);}
        finally{net.minecraftforge.common.ForgeHooks.setCraftingPlayer(null);}
        for(int y=0;y<input.getHeight();y++)for(int x=0;x<input.getWidth();x++){
            ItemStack original=grid.getItem(x+y*grid.getWidth());
            ItemStack remainder=remainders.get(x+y*input.getWidth());
            if(original.getCount()>1&&!remainder.isEmpty()&&!ItemStack.isSameItemSameTags(original,remainder)&&!fitInventory(inventory,remainder))return false;
        }
        return true;
    }
    private boolean fitInventory(List<ItemStack> inventory,ItemStack incoming){
        int remaining=incoming.getCount();
        for(int i=inventory.size()-1;i>=0&&remaining>0;i--){
            ItemStack present=inventory.get(i);if(present.isEmpty()||!ItemStack.isSameItemSameTags(present,incoming))continue;
            int accepted=Math.min(remaining,Math.max(0,slots.get(i+10).getMaxStackSize(incoming)-present.getCount()));present.grow(accepted);remaining-=accepted;
        }
        for(int i=inventory.size()-1;i>=0&&remaining>0;i--)if(inventory.get(i).isEmpty()){
            int accepted=Math.min(remaining,slots.get(i+10).getMaxStackSize(incoming));inventory.set(i,incoming.copyWithCount(accepted));remaining-=accepted;
        }
        return remaining==0;
    }    private ItemStack quickMoveNow(Player player,int slotIndex) {
        if(slotIndex<0||slotIndex>=slots.size())return ItemStack.EMPTY;
        Slot slot=slots.get(slotIndex);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack live=slot.getItem(),original=live.copy();
        if(slotIndex>=10&&player instanceof ServerPlayer sp){
            if(sp.isSpectator()||!stillValid(player))return ItemStack.EMPTY;
            AstralNetwork network=network();if(network==null)return ItemStack.EMPTY;
            if(!payForAccess(network,sp)){showError("The network needs power for this action.");return ItemStack.EMPTY;}
            ItemStack rest=deposit(network,live);slot.setByPlayer(rest);
            if(rest.isEmpty())clearError();else showError("Deposited "+(original.getCount()-rest.getCount())+" of "+original.getCount()+". Check destination capacity and filters.");
            return rest.getCount()==original.getCount()?ItemStack.EMPTY:original;
        }
        if(slotIndex==0)live.getItem().onCraftedBy(live,player.level(),player);
        if(!moveItemStackTo(live,10,46,true))return ItemStack.EMPTY;
        if(slotIndex==0)slot.onQuickCraft(live,original);
        if(live.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        if(live.getCount()==original.getCount())return ItemStack.EMPTY;
        slot.onTake(player,live);if(slotIndex==0)player.drop(live,false);return original;
    }
    @Override public void removed(Player player) { pendingInteraction=null;deferredRefill=null;super.removed(player); if (!player.level().isClientSide) clearContainer(player,grid); }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.container!=result && super.canTakeItemForPickAll(stack,slot); }
}
