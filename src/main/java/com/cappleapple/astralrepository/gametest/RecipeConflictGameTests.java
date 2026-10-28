package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.crafting.CraftingService;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.mojang.authlib.GameProfile;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Opt-in recipes and actual Polymorph are required; the normal recipe set is unchanged. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class RecipeConflictGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void polymorphSelectionChangesTheNexusOutputAndConsumesOneRealIngredient(GameTestHelper h) {
        if(!enabled(h))return;
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"recipe-conflict-grid"));
        var menu=new NexusMenu(141,player.getInventory());player.containerMenu=menu;
        menu.grid.setItem(4,new ItemStack(Items.DANDELION));
        h.assertTrue(menu.slots.getFirst().hasItem(),"The conflicting grid has an actual result");
        try {
            Class<?> apiClass=Class.forName("com.illusivesoulworks.polymorph.api.PolymorphApi");
            Object api=apiClass.getMethod("getInstance").invoke(null);
            Object data=apiClass.getMethod("getPlayerRecipeData",net.minecraft.world.entity.player.Player.class).invoke(api,player);
            Class<?> dataClass=Class.forName("com.illusivesoulworks.polymorph.api.common.capability.IRecipeData");
            h.assertTrue(((Collection<?>)dataClass.getMethod("getRecipesList").invoke(data)).size()>=2,"Both real conflicting recipes are sent to Polymorph's widget data");
            Class<?> packet=Class.forName("com.illusivesoulworks.polymorph.common.network.client.CPacketPlayerRecipeSelection");
            Object selection=packet.getConstructor(ResourceLocation.class).newInstance(id("conflict_crafting_z"));
            packet.getMethod("handle",packet,ServerPlayer.class).invoke(null,selection,player);
        } catch(ReflectiveOperationException failure) { throw new AssertionError("Actual Polymorph selection packet failed",failure); }
        h.assertTrue(menu.slots.getFirst().getItem().is(Items.IRON_NUGGET),"Polymorph's real server selection changes the Nexus preview");
        h.assertTrue(!menu.selectCraftingRecipe(ResourceLocation.withDefaultNamespace("oak_planks")),"A recipe that does not match cannot be selected");
        menu.clicked(0,0,ClickType.PICKUP,player);
        h.assertTrue(menu.getCarried().is(Items.IRON_NUGGET)&&menu.getCarried().getCount()==1,"The selected real result reaches the cursor");
        h.assertTrue(menu.grid.isEmpty()&&menu.slots.getFirst().getItem().isEmpty(),"Exactly one ingredient is consumed and no phantom result remains");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=800)
    public static void conflictingSmeltingRecipeCompletesThroughTheRealFurnace(GameTestHelper h) {
        if(!enabled(h))return;
        runJob(h,Blocks.FURNACE,new ItemStack(Items.DIRT));
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=500)
    public static void conflictingTableRecipeKeepsThePlannedOutput(GameTestHelper h) {
        if(!enabled(h))return;
        runJob(h,Blocks.CRAFTING_TABLE,new ItemStack(Items.DANDELION));
    }

    private static void runJob(GameTestHelper h,net.minecraft.world.level.block.Block machine,ItemStack input) {
        BlockPos chestPos=new BlockPos(1,2,1),processor=new BlockPos(3,2,1);
        h.setBlock(chestPos,Blocks.CHEST);h.setBlock(processor,machine);
        Container chest=(Container)h.getBlockEntity(chestPos);chest.setItem(0,input.copy());chest.setItem(1,new ItemStack(Items.COAL,2));
        var target=new ItemStack(Items.IRON_NUGGET);
        var access=new CraftingGameTests.Fixture(h,chestPos,chest,List.of(processor),target);
        var service=new CraftingService(access);
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"recipe-conflict-job"));
        var request=service.request(player,target,1);
        h.assertTrue(request.accepted(),"The conflicting recipe plan is accepted: "+request.message());
        h.onEachTick(service::tick);
        h.succeedWhen(()->{
            h.assertTrue(service.activeJobs()==0,"The conflicting recipe finishes without remaining locked");
            h.assertTrue(access.snapshot().getOrDefault(new ItemKey(target),0L)==1,"The exact selected output returns to actual storage: "+service.statuses());
            h.assertTrue(access.snapshot().getOrDefault(new ItemKey(input),0L)==0,"The physical ingredient was consumed");
            h.assertTrue(access.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.GOLD_NUGGET)),0L)==0,"The conflicting alternative was not manufactured");
            h.assertTrue(!CraftingService.isProcessorReserved(access.at(processor)),"Processor ownership is released on completion");
        });
    }

    private static boolean enabled(GameTestHelper h) {
        if(!Boolean.getBoolean("astral_repository.recipeCompatTest")){h.succeed();return false;}
        h.assertTrue(ModList.get().isLoaded("polymorph"),"The integration gate requires actual Polymorph");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(id("conflict_crafting_z")).isPresent(),"Conflict fixtures are loaded only for the integration gate");
        return true;
    }
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("astral_repository_recipe_test",name);}
    private RecipeConflictGameTests(){}
}
