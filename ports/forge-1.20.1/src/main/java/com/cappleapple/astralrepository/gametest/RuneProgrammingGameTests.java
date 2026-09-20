package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.CommonHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class RuneProgrammingGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void rightClickOpensExactRuneWithAnyItemAndShiftWandNeverPlaces(GameTestHelper h) {
        BlockPos host = new BlockPos(3,2,3), target = host.east(4);
        h.setBlock(host, Blocks.CHEST); h.setBlock(target, Blocks.CHEST);
        FakePlayer player = player(h);
        place(h, player, host,RuneLayer.Mode.PUSH);
        place(h, player, host,RuneLayer.Mode.PULL);
        RuneSurface surface = surface(h, host);
        RuneLayer push = surface.layers().get(0), pull = surface.layers().get(1);
        h.assertTrue(push.mode() == RuneLayer.Mode.PUSH && pull.mode() == RuneLayer.Mode.PULL && !push.id().equals(pull.id()), "Stacked layers have independent modes and identities");
        h.assertTrue(player.getOffhandItem().isEmpty() && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "Fixture has no offhand sample or goggles");
        push.filter().add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,ItemStack.EMPTY);
        pull.filter().add(FilterRules.Kind.ITEM,"minecraft:gold_ingot",false,ItemStack.EMPTY);
        var originalPush=push.filter().save(h.getLevel().registryAccess());
        var originalPull=pull.filter().save(h.getLevel().registryAccess());
        AtomicReference<UUID> opened = new AtomicReference<>();
        RuneProgramming.SettingsOpener previous = RuneProgramming.openSettings;
        try {
            RuneProgramming.openSettings = (who, face, layer) -> opened.set(layer.id());
            for (ItemStack held : new ItemStack[]{ItemStack.EMPTY,new ItemStack(Items.COBBLESTONE,12),new ItemStack(Items.WATER_BUCKET),new ItemStack(Items.IRON_INGOT)}) {
                for (int index=0;index<2;index++) {
                    opened.set(null);
                    sample(h,player,host,index,held);
                    h.assertTrue(surface.layers().get(index).id().equals(opened.get()),"Normal right-click opens the exact rune with any held item");
                }
            }
            var preset=RunePreset.initial(RuneLayer.Mode.PUSH);
            RuneLibraryData.get(h.getLevel().getServer()).library(player.getUUID()).put(preset.id(),preset);
            var wand=new ItemStack(AstralContent.ATTUNEMENT_WAND.get());
            WandPackets.selection(wand,preset.id(),true);
            sample(h,player,host,0,wand);
            h.assertTrue(push.id().equals(opened.get())&&surface.layers().size()==2,"A placement-ready wand opens an existing rune without placing another");
            WandPackets.selection(wand,preset.id(),true);opened.set(null);player.setShiftKeyDown(true);
            sample(h,player,host,1,wand);
            h.assertTrue(surface.layers().size()==2&&RuneProgramming.selection(wand).layer().equals(pull.id())&&opened.get()==null,"Shift-wand selects targets without placing or opening another rune");
            player.setShiftKeyDown(false);
            sample(h,player,host,1,wand);
            h.assertTrue(pull.id().equals(opened.get())&&RuneProgramming.selection(wand)==null,"Normal wand use opens settings and finishes target selection");
            h.assertTrue(originalPush.equals(push.filter().save(h.getLevel().registryAccess()))&&originalPull.equals(pull.filter().save(h.getLevel().registryAccess())),"Opening settings never changes either filter");
            h.assertTrue(h.getBlockState(host.south()).isAir(),"Opening settings with a block or bucket never uses it on the host");
            pull.setMode(RuneLayer.Mode.FILTER);opened.set(null);player.setShiftKeyDown(true);
            WandPackets.selection(wand,preset.id(),true);sample(h,player,host,1,wand);
            h.assertTrue(pull.id().equals(opened.get())&&surface.layers().size()==2,"Shift-wand on a Filter opens settings without placing another rune");

        } finally { RuneProgramming.openSettings=previous;player.setShiftKeyDown(false); }
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void wandAssignsEitherOrderTogglesTargetsAndKeepsCrystalLinks(GameTestHelper h) {
        BlockPos host = new BlockPos(2,2,2), a = host.east(4), b = host.south(4), nexus = host.east(7), relay = nexus.south(4);
        h.setBlock(host, Blocks.CHEST); h.setBlock(a, Blocks.CHEST); h.setBlock(b, Blocks.CHEST);
        h.setBlock(nexus, AstralContent.STORAGE_NEXUS.get()); h.setBlock(relay, AstralContent.RELAY_CRYSTAL.get());
        FakePlayer player = player(h);
        place(h, player, host,RuneLayer.Mode.PULL);
        place(h, player, host,RuneLayer.Mode.PUSH);
        RuneSurface surface = surface(h, host);
        RuneLayer pull = surface.layers().get(0), push = surface.layers().get(1);
        ItemStack wand = new ItemStack(AstralContent.ATTUNEMENT_WAND.get());
        var cell=RuneLayout.placed(surface).get(0);
        var missedGlyph=new BlockHitResult(cell.center().add(cell.right().scale(cell.size()*0.55)),Direction.SOUTH,h.absolutePos(host),false);
        wand(h,player,wand,missedGlyph);
        h.assertTrue(RuneProgramming.selection(wand)!=null&&RuneProgramming.selection(wand).layer()==null,"Clicking beside a glyph selects only the plain container, never a rune layer");
        wand(h,player,wand,missedGlyph);
        h.assertTrue(RuneProgramming.selection(wand)==null&&pull.target()==null&&push.target()==null,"Canceling a bare-container selection leaves every rune unbound");
        player.setShiftKeyDown(true);wand(h,player,wand,runeHit(h,host,0));player.setShiftKeyDown(false);
        h.assertTrue(pull.id().equals(RuneProgramming.selection(wand).layer()),"Shift-wand selects the exact rune");
        wand(h,player,wand,bareHit(h,a,Direction.UP));wand(h,player,wand,bareHit(h,b,Direction.NORTH));
        h.assertTrue(pull.targets().size()==2&&RuneProgramming.selection(wand)!=null,"Selection persists for multiple exact sided targets");
        wand(h,player,wand,bareHit(h,a,Direction.UP));
        h.assertTrue(pull.targets().size()==1&&pull.target().position().pos().equals(h.absolutePos(b)),"Clicking an assigned target removes only that target");
        player.setShiftKeyDown(true);wand(h,player,wand,runeHit(h,host,1));player.setShiftKeyDown(false);
        wand(h,player,wand,bareHit(h,a,Direction.UP));
        h.assertTrue(push.target()!=null&&pull.targets().size()==1,"Selecting another rune preserves the previous rune's assignments");
        wand(h,player,wand,runeHit(h,host,1));
        h.assertTrue(RuneProgramming.selection(wand)==null&&push.target()!=null,"Clicking the selected glyph ends target selection without unlinking");
        for (int pass=0; pass<2; pass++) {
            wand(h, player, wand, bareHit(h, nexus, Direction.UP));
            wand(h, player, wand, bareHit(h, relay, Direction.SOUTH));
            var first = AnchorAddress.crystal(h.getLevel(), h.absolutePos(nexus));
            var second = AnchorAddress.crystal(h.getLevel(), h.absolutePos(relay));
            h.assertTrue(NetworkManager.get(h.getLevel().getServer()).links(first).contains(second) == (pass == 0), "Crystal pairs still toggle their explicit network link");
        }
        h.assertTrue(RuneSurfaces.at(h.getLevel(),h.absolutePos(a)).isEmpty() && RuneSurfaces.at(h.getLevel(),h.absolutePos(b)).isEmpty(), "Targets require no inscriptions");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void shiftContainerFirstBindsMultipleRunesWithoutChangingSelection(GameTestHelper h) {
        BlockPos host=new BlockPos(2,2,2),target=host.east(4),relay=host.south(4);
        h.setBlock(host,Blocks.CHEST);h.setBlock(target,Blocks.CHEST);h.setBlock(relay,AstralContent.RELAY_CRYSTAL.get());
        var player=player(h);place(h,player,host,RuneLayer.Mode.PUSH);place(h,player,host,RuneLayer.Mode.PULL);
        var surface=surface(h,host);var push=surface.layers().get(0);var pull=surface.layers().get(1);
        var wand=new ItemStack(AstralContent.ATTUNEMENT_WAND.get());
        var preset=RunePreset.initial(RuneLayer.Mode.PUSH);WandPackets.selection(wand,preset.id(),true);
        AtomicReference<UUID> opened=new AtomicReference<>();var previous=RuneProgramming.openSettings;
        try {
            RuneProgramming.openSettings=(who,face,layer)->opened.set(layer.id());player.setShiftKeyDown(true);
            wand(h,player,wand,bareHit(h,target,Direction.UP));var original=RuneProgramming.selection(wand);
            h.assertTrue(original!=null&&original.layer()==null&&wand.hasFoil()&&!WandPackets.placing(wand),"Shift container starts binding and disables placement");
            for(int index=0;index<2;index++){
                wand(h,player,wand,runeHit(h,host,index));
                var rune=surface.layers().get(index);
                h.assertTrue(rune.target()!=null&&rune.target().position().pos().equals(h.absolutePos(target))&&rune.target().face()==Direction.UP,"Rune binds selected container face");
                h.assertTrue(original.equals(RuneProgramming.selection(wand))&&wand.hasFoil(),"Container stays selected after assigning each rune");
            }
            wand(h,player,wand,runeHit(h,host,0));
            h.assertTrue(push.targets().isEmpty()&&pull.targets().size()==1&&original.equals(RuneProgramming.selection(wand)),"Repeat click removes just that rune's assignment");
            h.assertTrue(opened.get()==null&&surface.layers().size()==2,"Reverse binding opens no UI and places no extra rune");
            RuneProgramming.cancel(wand);wand(h,player,wand,bareHit(h,relay,Direction.SOUTH));var network=RuneProgramming.selection(wand);
            wand(h,player,wand,runeHit(h,host,0));
            h.assertTrue(push.target()!=null&&push.target().face()==null&&push.target().position().pos().equals(h.absolutePos(relay)),"Relay-first binding addresses the whole network");
            h.assertTrue(network.equals(RuneProgramming.selection(wand)),"Relay remains selected as the shared target");
            RuneProgramming.cancel(wand);wand(h,player,wand,runeHit(h,host,0));wand(h,player,wand,runeHit(h,host,1));
            h.assertTrue(pull.id().equals(RuneProgramming.selection(wand).layer())&&pull.targets().size()==1&&push.targets().size()==1,"Rune-first Shift click changes rune without editing existing bindings");
            RuneProgramming.cancel(wand);h.assertTrue(!wand.hasFoil(),"Cancel clears both selection modes");
        }finally{RuneProgramming.openSettings=previous;player.setShiftKeyDown(false);}
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=500)
    public static void actualPushAndPullRouteBetweenPlainContainersUsingSeparateLayerFilters(GameTestHelper h) {
        BlockPos host = new BlockPos(3,2,3), destination = host.east(4), source = host.south(4);
        h.setBlock(host, Blocks.CHEST); h.setBlock(destination, Blocks.CHEST); h.setBlock(source, Blocks.CHEST);
        FakePlayer player = player(h);
        place(h,player,host,RuneLayer.Mode.PUSH); place(h,player,host,RuneLayer.Mode.PULL);
        surface(h,host).layers().get(0).filter().add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,ItemStack.EMPTY); surface(h,host).layers().get(1).filter().add(FilterRules.Kind.ITEM,"minecraft:diamond",false,ItemStack.EMPTY);
        var hostChest=(ChestBlockEntity)h.getBlockEntity(host);var sourceChest=(ChestBlockEntity)h.getBlockEntity(source);var destinationChest=(ChestBlockEntity)h.getBlockEntity(destination);
        hostChest.setItem(0,new ItemStack(Items.IRON_INGOT,40));hostChest.setItem(1,new ItemStack(Items.GOLD_INGOT,7));sourceChest.setItem(0,new ItemStack(Items.DIAMOND,32));
        ItemStack wand=new ItemStack(AstralContent.ATTUNEMENT_WAND.get());
        player.setShiftKeyDown(true);wand(h,player,wand,runeHit(h,host,0));player.setShiftKeyDown(false);wand(h,player,wand,bareHit(h,destination,Direction.UP));
        player.setShiftKeyDown(true);wand(h,player,wand,runeHit(h,host,1));player.setShiftKeyDown(false);wand(h,player,wand,bareHit(h,source,Direction.UP));
        h.assertTrue(surface(h,host).layers().get(1).target()!=null,"Pull linked before ticking");
        h.succeedWhen(() -> {
            h.assertTrue(count(destinationChest,Items.IRON_INGOT)==40,"Push sends the host's iron into its plain target");
            h.assertTrue(count(hostChest,Items.DIAMOND)==32,"Pull draws diamonds from its plain target into the rune host: "+surface(h,host).layers().get(1).status());
            h.assertTrue(count(hostChest,Items.IRON_INGOT)==0 && count(sourceChest,Items.DIAMOND)==0,"Transfers preserve totals without duplication");
            h.assertTrue(count(hostChest,Items.GOLD_INGOT)==7 && count(destinationChest,Items.DIAMOND)==0,"Stacked filters do not bleed into one another");
        });
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void runeItemsAreHiddenAndUncraftable(GameTestHelper h) {
        var tab=AstralContent.CREATIVE_TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS,true,h.getLevel().registryAccess()));
        for (String name : new String[]{"push_rune","pull_rune"}) {
            var id=new ResourceLocation("astral_repository",name);
            h.assertTrue(h.getLevel().getRecipeManager().byKey(id).isEmpty(),"Rune item recipe removed: "+name);
            h.assertTrue(tab.getDisplayItems().stream().noneMatch(stack->BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id)),"Rune item hidden from creative: "+name);
            h.assertTrue(!BuiltInRegistries.ITEM.containsKey(id),"Rune items are no longer registered");
        }
        for (String name : AstralContent.REMOVED_IDS) {
            var id=new ResourceLocation("astral_repository",name);
            h.assertTrue(!BuiltInRegistries.ITEM.containsKey(id),"Retired item ID removed: "+name);
            h.assertTrue(tab.getDisplayItems().stream().noneMatch(stack->BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id)),"Legacy item is hidden from creative: "+name);
            h.assertTrue(h.getLevel().getRecipeManager().byKey(id).isEmpty(),"Legacy rune recipe is removed: "+name);
        }
        h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void shiftPickupReturnsExactlyTheHitRuneAndPreservesItsContainer(GameTestHelper h) {
        BlockPos host = new BlockPos(4,2,4);
        h.setBlock(host, Blocks.CHEST);
        var chest = (ChestBlockEntity)h.getBlockEntity(host);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 17));
        FakePlayer player = player(h);
        place(h, player, host,RuneLayer.Mode.PUSH);
        place(h, player, host,RuneLayer.Mode.PULL);
        RuneSurface surface = surface(h, host);
        RuneLayer push = surface.layers().get(0), pull = surface.layers().get(1);
        push.filter().add(FilterRules.Kind.ITEM, "minecraft:iron_ingot", false, ItemStack.EMPTY);
        RuneSurface opposite = RuneSurfaces.getOrCreate(h.getLevel(), h.absolutePos(host), Direction.NORTH);
        RuneLayer north = opposite.addLayer(RuneGlyph.id(RuneLayer.Mode.PULL), RuneLayer.Mode.PULL);
        player.getInventory().clearContent();
        player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        player.setShiftKeyDown(true);
        aim(player, runeHit(h, host, 1).getLocation(), 2);
        assertAimedAt(h, player, host, 1);
        var mining = CommonHooks.onLeftClickBlock(player, h.absolutePos(host), Direction.SOUTH, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
        h.assertTrue(mining.isCanceled(), "The actual NeoForge mining hook consumes Shift-click on a glyph");
        player.gameMode.handleBlockBreakAction(h.absolutePos(host), ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.SOUTH, h.getLevel().getMaxBuildHeight(), 1);
        h.assertTrue(h.getBlockState(host).is(Blocks.CHEST) && count(chest, Items.DIAMOND) == 17, "Creative mining cannot destroy the rune's host or its contents");
        player.setYHeadRot(0); // A fresh movement packet can precede the server tick that updates head pose.
        h.assertTrue(RuneProgramming.pickup(player, h.absolutePos(host), Direction.SOUTH, pull.id()), "Pickup uses current packet rotation even while animated head yaw is stale");
        h.assertTrue(inventoryCount(player, Items.STICK) == 0 && inventoryCount(player, Items.PAPER) == 0, "Creative removal produces no rune items");
        h.assertTrue(surface.layers().size() == 1 && surface.get(push.id()) == push && push.filter().entries().size() == 1, "Neighbor UUID and filter survive layout reflow");
        aim(player, runeHit(h, host, 0).getLocation(), 2);
        for (int i = 0; i < 20; i++) h.assertTrue(!RuneProgramming.pickup(player, h.absolutePos(host), Direction.SOUTH, pull.id()), "Replayed identity cannot pick up a reflowed neighbor");
        h.assertTrue(inventoryCount(player, Items.STICK) == 0 && surface.layers().size() == 1, "Repeated requests never refund twice");
        h.assertTrue(RuneProgramming.pickup(player, h.absolutePos(host), Direction.SOUTH, push.id()), "A fresh exact request picks up the remaining rune");
        h.assertTrue(RuneSurfaces.get(h.getLevel(), h.absolutePos(host), Direction.SOUTH) == null && opposite.get(north.id()) == north, "Removing the last rune clears only its own face");
        h.assertTrue(h.getBlockState(host).is(Blocks.CHEST) && count(chest, Items.DIAMOND) == 17, "Every pickup leaves the original chest and inventory intact");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void pickupRejectsMissesNeighborIdsBlockedRaysAndOutOfReachRequests(GameTestHelper h) {
        BlockPos host = new BlockPos(4,2,4), neighbor = host.east(3);
        h.setBlock(host, Blocks.CHEST); h.setBlock(neighbor, Blocks.CHEST);
        FakePlayer player = player(h);
        place(h, player, host,RuneLayer.Mode.PUSH);
        place(h, player, host,RuneLayer.Mode.PULL);
        RuneSurface surface = surface(h, host);
        RuneLayer first = surface.layers().get(0), second = surface.layers().get(1);
        player.getInventory().clearContent(); player.setShiftKeyDown(true);
        aim(player, runeHit(h, host, 0).getLocation(), 2);
        assertAimedAt(h, player, host, 0);
        h.assertTrue(CommonHooks.onLeftClickBlock(player, h.absolutePos(host), Direction.SOUTH, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK).isCanceled(), "The fixture first confirms a valid glyph hit reaches the real pickup guard");
        var cell = RuneLayout.placed(surface).get(0);
        aim(player, cell.center().add(cell.right().scale(cell.size() * .55)), 2);
        h.assertTrue(!RuneProgramming.pickup(player, h.absolutePos(host), Direction.SOUTH, first.id()), "A hit beside the actual glyph cannot remove a rune");
        aim(player, runeHit(h, host, 1).getLocation(), 2);
        h.assertTrue(!RuneProgramming.pickup(player, h.absolutePos(host), Direction.SOUTH, first.id()), "A visible neighboring cell cannot be substituted for the requested UUID");
        h.assertTrue(!RuneProgramming.pickup(player, h.absolutePos(host), Direction.NORTH, second.id()), "Another host face cannot be substituted");
        h.assertTrue(!RuneProgramming.pickup(player, h.absolutePos(neighbor), Direction.SOUTH, second.id()), "Another container cannot be substituted");
        aim(player, runeHit(h, host, 0).getLocation(), 8);
        h.assertTrue(!RuneProgramming.pickup(player, h.absolutePos(host), Direction.SOUTH, first.id()), "Requests beyond actual block reach are rejected");
        aim(player, runeHit(h, host, 0).getLocation(), 2);
        h.setBlock(host.south(), Blocks.STONE);
        h.assertTrue(!RuneProgramming.pickup(player, h.absolutePos(host), Direction.SOUTH, first.id()), "A wall in front of the rune blocks pickup");
        h.setBlock(host.south(), Blocks.AIR);
        player.setShiftKeyDown(false);
        h.assertTrue(!RuneProgramming.pickup(player, h.absolutePos(host), Direction.SOUTH, first.id()), "The server requires Shift, not just a client assertion");
        player.setShiftKeyDown(true); player.gameMode.changeGameModeForPlayer(GameType.SPECTATOR);
        h.assertTrue(!RuneProgramming.pickup(player, h.absolutePos(host), Direction.SOUTH, first.id()), "Spectators cannot modify rune state");
        h.assertTrue(surface.layers().size() == 2 && surface.get(first.id()) == first && surface.get(second.id()) == second, "Rejected requests preserve both original layers");
        h.assertTrue(inventoryCount(player, Items.PAPER) == 0 && inventoryCount(player, Items.STICK) == 0 && h.getBlockState(host).is(Blocks.CHEST), "Rejected requests create no refunds and never damage the container");
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL); aim(player, runeHit(h, host, 0).getLocation(), 2);
        assertAimedAt(h, player, host, 0);
        h.assertTrue(RuneProgramming.pickup(player, h.absolutePos(host), Direction.SOUTH, first.id()) && surface.layers().size() == 1 && inventoryCount(player, Items.PAPER) == 0, "Restoring valid access succeeds, so rejections cannot pass because every ray missed");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void fullInventoryDoesNotBlockItemFreeRemoval(GameTestHelper h) {
        BlockPos host = new BlockPos(4,2,4);
        h.setBlock(host, Blocks.CHEST);
        AtomicReference<String> feedback = new AtomicReference<>("");
        FakePlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "rune-capacity")) {
            @Override public void displayClientMessage(net.minecraft.network.chat.Component message, boolean actionBar) { feedback.set(message.getString()); }
        };
        for (GameType mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
            player.gameMode.changeGameModeForPlayer(mode);
            RuneSurface surface = RuneSurfaces.getOrCreate(h.getLevel(), h.absolutePos(host), Direction.SOUTH);
            RuneLayer layer = surface.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH), RuneLayer.Mode.PUSH);
            layer.filter().add(FilterRules.Kind.ITEM, "minecraft:diamond", false, ItemStack.EMPTY);
            for (int slot = 0; slot < player.getInventory().items.size(); slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            player.setShiftKeyDown(true); aim(player, runeHit(h, host, 0).getLocation(), 2);
            assertAimedAt(h, player, host, 0);
            h.assertTrue(RuneProgramming.pickup(player,h.absolutePos(host),Direction.SOUTH,layer.id()),"Full inventory does not block item-free removal in "+mode);
            h.assertTrue(inventoryCount(player,Items.STONE)==36*64&&inventoryCount(player,Items.PAPER)==0,"Removal leaves every inventory stack unchanged");
            h.assertTrue(RuneSurfaces.get(h.getLevel(), h.absolutePos(host), Direction.SOUTH) == null && h.getBlockState(host).is(Blocks.CHEST), "The face is removed but its chest survives in " + mode);
        }
        h.succeed();
    }

    private static void aim(FakePlayer player, Vec3 location, double distance) {
        player.setPos(location.x, location.y - player.getEyeHeight(), location.z + distance);
        player.setYRot(180); player.setYHeadRot(180); player.setXRot(0);
    }
    private static void assertAimedAt(GameTestHelper h, FakePlayer player, BlockPos host, int expectedIndex) {
        var ray = player.pick(player.blockInteractionRange(), 1, false);
        h.assertTrue(ray instanceof BlockHitResult && ray.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK,
                "Positive fixture ray must hit a block; eye=" + player.getEyePosition() + ", view=" + player.getViewVector(1) + ", result=" + ray.getType() + " at " + ray.getLocation());
        var hit = (BlockHitResult)ray;
        h.assertTrue(hit.getBlockPos().equals(h.absolutePos(host)) && hit.getDirection() == Direction.SOUTH,
                "Positive fixture must hit the chest's south face; got " + hit.getBlockPos() + " / " + hit.getDirection());
        h.assertTrue(RuneLayout.selectedIndex(RuneLayout.placed(surface(h,host)),hit.getLocation()) == expectedIndex,
                "Positive fixture must hit visible glyph " + expectedIndex + " at " + hit.getLocation());
        h.assertTrue(player.isAlive() && player.isShiftKeyDown() && !player.isSpectator()
                && player.canInteractWithBlock(hit.getBlockPos(), 0) && h.getLevel().mayInteract(player, hit.getBlockPos())
                && !player.blockActionRestricted(h.getLevel(), hit.getBlockPos(), player.gameMode.getGameModeForPlayer()),
                "Positive fixture must have live, sneaking, in-range build access");
    }    private static int inventoryCount(FakePlayer player, Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static FakePlayer player(GameTestHelper h) {
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"rune-user"));
        player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);return player;
    }
    private static RuneSurface surface(GameTestHelper h,BlockPos host) { return RuneSurfaces.get(h.getLevel(),h.absolutePos(host),Direction.SOUTH); }
    private static BlockHitResult bareHit(GameTestHelper h,BlockPos pos,Direction face) { return new BlockHitResult(h.absolutePos(pos).getCenter().add(net.minecraft.world.phys.Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),face,h.absolutePos(pos),false); }
    private static BlockHitResult runeHit(GameTestHelper h,BlockPos host,int layer) {
        var cell=RuneLayout.placed(surface(h,host)).get(layer);
        return new BlockHitResult(cell.center(),Direction.SOUTH,h.absolutePos(host),false);
    }
    private static void place(GameTestHelper h,FakePlayer player,BlockPos host,RuneLayer.Mode mode) {
        var preset=RunePreset.initial(mode);RuneLibraryData.get(h.getLevel().getServer()).library(player.getUUID()).put(preset.id(),preset);
        var wand=new ItemStack(AstralContent.ATTUNEMENT_WAND.get());WandPackets.selection(wand,preset.id(),true);player.setItemInHand(InteractionHand.MAIN_HAND,wand);
        int before=surface(h,host)==null?0:surface(h,host).layers().size();
        var result=wand.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(h.absolutePos(host).getCenter().add(before%2==0?-.22:.22,before<2?.22:-.22,.4375),Direction.SOUTH,h.absolutePos(host),false)));
        h.assertTrue(result.consumesAction()&&surface(h,host).layers().size()==before+1&&wand.getCount()==1,"Wand places one independent preset without consuming an item");
    }
    private static void sample(GameTestHelper h,FakePlayer player,BlockPos host,int layer,ItemStack item) {
        player.setItemInHand(InteractionHand.MAIN_HAND,item);
        int count=item.getCount();var hit=runeHit(h,host,layer);
        var event=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,hit.getBlockPos(),hit);
        RuneProgramming.interact(event);
        h.assertTrue(event.isCanceled() && event.getCancellationResult().consumesAction(),"Hit-rune interaction prevents ordinary container/item use");
        h.assertTrue(player.getMainHandItem().getCount()==count,"Samples are never consumed");
    }
    private static void wand(GameTestHelper h,FakePlayer player,ItemStack wand,BlockHitResult hit) {
        player.setItemInHand(InteractionHand.MAIN_HAND,wand);
        h.assertTrue(wand.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Actual wand use handles the endpoint");
    }
    private static int count(ChestBlockEntity chest,Item item) {int amount=0;for(int slot=0;slot<chest.getContainerSize();slot++){ItemStack stack=chest.getItem(slot);if(stack.is(item))amount+=stack.getCount();}return amount;}
}
