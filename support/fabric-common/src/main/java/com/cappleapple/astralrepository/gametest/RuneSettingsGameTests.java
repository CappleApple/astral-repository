package com.cappleapple.astralrepository.gametest;

import net.minecraft.world.item.Items;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.RuneSettingsPackets;
import com.cappleapple.astralrepository.network.RuneSettingsPackets.Operation;
import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class RuneSettingsGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void cadenceRejectsUnsupportedResourcesAndInvalidNumbers(GameTestHelper h)throws Exception{
        BlockPos host=new BlockPos(3,2,3);h.setBlock(host,Blocks.CHEST);var surface=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(host),Direction.SOUTH);var rune=surface.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"cadence-editor"));player.setPos(h.absolutePos(host).getCenter());RuneSettingsPackets.open(player,surface,rune);var session=token(player);
        h.assertTrue(RuneSettingsPackets.supportedResources(surface)==1,"A chest exposes only the item row");
        for(String invalid:java.util.List.of("ITEMS:3:0","ITEMS:-1:10","ENERGY:100:10","ITEMS:65:10","ITEMS:2147483648:10","ITEMS:3:2147483648"))RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(session,Operation.SET_CADENCE,false,false,true,0,Long.MAX_VALUE,0,invalid,-1));
        h.assertTrue(rune.cadence().equals(RuneCadence.DEFAULT),"Invalid and unsupported requests cannot alter the rune");
        int maximum=com.cappleapple.astralrepository.AstralServerConfig.maxItemTransfer.get(),minimum=com.cappleapple.astralrepository.AstralServerConfig.minItemTransferTicks.get();
        try{
            com.cappleapple.astralrepository.AstralServerConfig.maxItemTransfer.set(2);com.cappleapple.astralrepository.AstralServerConfig.minItemTransferTicks.set(8);
            for(String invalid:java.util.List.of("ITEMS:3:8","ITEMS:2:7"))RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(session,Operation.SET_CADENCE,false,false,true,0,Long.MAX_VALUE,0,invalid,-1));
            h.assertTrue(rune.cadence().equals(RuneCadence.DEFAULT),"Edited packets cannot exceed a custom amount ceiling or bypass a custom interval floor");
        }finally{com.cappleapple.astralrepository.AstralServerConfig.maxItemTransfer.set(maximum);com.cappleapple.astralrepository.AstralServerConfig.minItemTransferTicks.set(minimum);}

        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(session,Operation.SET_CADENCE,false,false,true,0,Long.MAX_VALUE,0,"ITEMS:3:7",-1));
        h.assertTrue(rune.cadence().rate(RuneCadence.Kind.ITEMS).equals(new RuneCadence.Rate(3,7)),"Valid supported rate saves immediately");
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(session,Operation.RESET_CADENCE,false,false,true,0,Long.MAX_VALUE,0,"",-1));h.assertTrue(rune.cadence().equals(RuneCadence.DEFAULT),"Reset returns to live server defaults");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void inventoryFiltersCopyWithoutMovingAndRejectInvalidSlots(GameTestHelper h)throws ReflectiveOperationException {
        BlockPos host=new BlockPos(3,2,3);h.setBlock(host,Blocks.CHEST);
        var player=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"ghost-items"),net.minecraft.server.level.ClientInformation.createDefault());
        player.connection=new FakePlayer(h.getLevel(),player.getGameProfile()).connection;player.setPos(h.absolutePos(host).getCenter());
        player.getInventory().setItem(9,new net.minecraft.world.item.ItemStack(Items.IRON_INGOT,32));
        player.getInventory().setItem(10,new net.minecraft.world.item.ItemStack(Items.WATER_BUCKET));
        var surface=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(host),Direction.SOUTH);var rune=surface.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
        RuneSettingsPackets.open(player,surface,rune);var token=token(player);
        h.assertTrue(player.containerMenu instanceof com.cappleapple.astralrepository.menu.RuneSettingsMenu,"Server opens an actual inventory menu");
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.ADD_INVENTORY,false,false,true,0,Long.MAX_VALUE,0,"minecraft:diamond",0));
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.ADD_INVENTORY,false,false,true,0,Long.MAX_VALUE,0,"",-1));
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.ADD_INVENTORY,false,false,true,0,Long.MAX_VALUE,0,"",1));
        h.assertTrue(rune.filter().entries().size()==2&&rune.filter().entries().getFirst().id().equals("minecraft:iron_ingot")&&rune.filter().entries().get(1).kind()==FilterRules.Kind.FLUID,"Server resolves actual slot samples and ignores supplied identities");
        var menu=player.containerMenu;
        menu.clicked(0,0,net.minecraft.world.inventory.ClickType.PICKUP,player);
        h.assertTrue(menu.getCarried().getCount()==32&&player.getInventory().getItem(9).isEmpty(),"Normal click picks up the actual stack");
        menu.clicked(2,1,net.minecraft.world.inventory.ClickType.PICKUP,player);
        h.assertTrue(menu.getCarried().getCount()==31&&player.getInventory().getItem(11).getCount()==1,"Right click places one item normally");
        menu.clicked(0,0,net.minecraft.world.inventory.ClickType.PICKUP,player);
        menu.clicked(0,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player);
        h.assertTrue(menu.getCarried().isEmpty()&&player.getInventory().getItem(9).getCount()==31,"Shift shortcut cannot accidentally transfer inventory");
        menu.clicked(0,0,net.minecraft.world.inventory.ClickType.PICKUP,player);menu.removed(player);
        h.assertTrue(menu.getCarried().isEmpty()&&count(player,Items.IRON_INGOT)==32,"Closing with a carried stack returns it without loss");
        h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void settingsSessionsValidateIdentityReachExpiryAndIndependentRemoval(GameTestHelper h) throws ReflectiveOperationException {
        BlockPos host=new BlockPos(3,2,3),target=host.east(3);
        h.setBlock(host,Blocks.CHEST);h.setBlock(target,Blocks.CHEST);
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"rune-settings"));
        var near=h.absolutePos(host).getCenter().add(0,0,2);player.setPos(near);
        RuneSurface surface=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(host),Direction.SOUTH);
        RuneLayer push=surface.addLayer(Identifier.parse("astral_repository:push_rune"),RuneLayer.Mode.PUSH);
        RuneLayer pull=surface.addLayer(Identifier.parse("astral_repository:pull_rune"),RuneLayer.Mode.PULL);
        RuneSettingsPackets.open(player,surface,push);
        UUID token=token(player);
        h.assertTrue(token!=null,"Nearby player receives a session for the exact rune");

        push.setEnabled(false);push.report("Transfer outcome uncertain; paused");
        RuneSettingsPackets.handle(player,action(token,Operation.SAVE,98,""));
        h.assertTrue(!push.enabled()&&push.priority()==0&&token(player)==null,"A stale running editor cannot undo an automatic failure pause");
        RuneSettingsPackets.handle(player,action(token,Operation.SAVE,98,""));
        h.assertTrue(!push.enabled(),"A queued duplicate Save cannot reactivate a closed stale session");
        push.setEnabled(true);RuneSettingsPackets.open(player,surface,push);token=token(player);

        RuneSettingsPackets.handle(player,action(UUID.randomUUID(),Operation.SAVE,99,""));
        h.assertTrue(push.priority()==0&&pull.priority()==0,"An unknown session cannot edit either rune");
        RuneSettingsPackets.handle(player,action(token,Operation.ADD_RULE,8,"minecraft:does_not_exist"));
        h.assertTrue(push.priority()==0&&push.filter().entries().isEmpty(),"Invalid rules reject the entire settings action");
        RuneSettingsPackets.handle(player,action(token,Operation.SAVE,2000,""));
        h.assertTrue(push.priority()==0,"Out-of-range priority is rejected");
        RuneSettingsPackets.handle(player,action(token,Operation.ADD_RULE,0,"minecraft:iron_ingot"));
        h.assertTrue(push.filter().entries().size()==1&&push.filter().entries().getFirst().sample().isEmpty(),"Typed item ID starts without a captured component sample");
        var named=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Recorded sample"));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,named);
        var hit=new net.minecraft.world.phys.BlockHitResult(RuneLayout.cells(h.getLevel(),h.absolutePos(host),Direction.SOUTH,2).get(0).center(),Direction.SOUTH,h.absolutePos(host),false);
        h.assertTrue(RuneProgramming.use(named,h.getLevel(),h.absolutePos(host),player,net.minecraft.world.InteractionHand.MAIN_HAND,hit),"Held named item opens the editor");
        h.assertTrue(push.filter().entries().getFirst().sample().isEmpty(),"Opening the editor does not change the filter");
        token=token(player);
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.TOGGLE_ITEM_DATA,false,false,true,0,Long.MAX_VALUE,0,"",0));
        h.assertTrue(push.filter().matches(named)&&!push.filter().matches(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT)),"Exact-data mode distinguishes the captured name from an ordinary item");
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.TOGGLE_ITEM_DATA,false,false,true,0,Long.MAX_VALUE,0,"",0));
        h.assertTrue(push.filter().matches(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT)),"Toggling back restores item-only matching");
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.SAVE,true,false,false,2,32,7,"",-1));
        h.assertTrue(push.priority()==7&&!push.enabled()&&!push.filter().all()&&push.filter().minimum()==2&&push.filter().target()==32,"Valid settings save updates the selected layer");
        h.assertTrue(pull.priority()==0&&pull.enabled()&&!pull.filter().all()&&pull.filter().minimum()==0,"Saving one layer leaves its neighbor unchanged");
        RuneSettingsPackets.handle(player,action(token,Operation.REMOVE_RUNE,0,""));
        h.assertTrue(surface.get(push.id())==push,"A consumed Save session cannot be replayed to remove the rune");

        RuneSettingsPackets.open(player,surface,push);token=token(player);
        player.setPos(near.add(100,0,0));
        RuneSettingsPackets.handle(player,action(token,Operation.SAVE,12,""));
        h.assertTrue(push.priority()==7,"Moving out of reach invalidates an open settings session");
        player.setPos(near);RuneSettingsPackets.open(player,surface,push);token=token(player);
        expire(player,h.getLevel().getGameTime()-1);
        RuneSettingsPackets.handle(player,action(token,Operation.SAVE,13,""));
        h.assertTrue(push.priority()==7,"Expired editor sessions cannot update a layer");

        RuneSettingsPackets.open(player,surface,push);token=token(player);
        surface.removeLayer(push.id());
        RuneSettingsPackets.handle(player,action(token,Operation.SAVE,14,""));
        h.assertTrue(surface.get(push.id())==null&&pull.priority()==0,"A removed layer cannot redirect a stale session to the next visible cell");
        RuneLayer retained=surface.addLayer(Identifier.parse("astral_repository:push_rune"),RuneLayer.Mode.PUSH);
        h.assertTrue(surface.toggleTarget(retained.id(),GlobalPos.of(h.getLevel().dimension(),h.absolutePos(target)),Direction.UP).success(),"Remaining layer has a plain-container target");
        int before=count(player,Items.STICK);
        RuneSettingsPackets.open(player,surface,pull);token=token(player);
        RuneSettingsPackets.handle(player,action(token,Operation.REMOVE_RUNE,0,""));
        h.assertTrue(surface.layers().size()==1&&surface.get(retained.id())==retained&&retained.target()!=null,"Remove rune affects only its own layer and preserves the neighbor's target");
        h.assertTrue(count(player,Items.STICK)==before,"Survival removal does not create a rune item");
        h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void concurrentFilterEditsInvalidateIndexedActionsButCountersDoNot(GameTestHelper h) throws ReflectiveOperationException {
        BlockPos pos=new BlockPos(3,2,3);h.setBlock(pos,Blocks.CHEST);
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"rune-concurrent"));player.setPos(h.absolutePos(pos).getCenter().add(0,0,2));
        RuneSurface surface=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(pos),Direction.SOUTH);RuneLayer layer=surface.addLayer(Identifier.parse("astral_repository:push_rune"),RuneLayer.Mode.PUSH);
        layer.filter().add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,net.minecraft.world.item.ItemStack.EMPTY);layer.filter().add(FilterRules.Kind.ITEM,"minecraft:gold_ingot",false,net.minecraft.world.item.ItemStack.EMPTY);layer.changed();
        RuneSettingsPackets.open(player,surface,layer);UUID token=token(player);
        layer.filter().remove(0);layer.changed();
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.REMOVE_RULE,false,false,true,0,Long.MAX_VALUE,12,"",0));
        h.assertTrue(layer.filter().entries().size()==1&&layer.filter().entries().getFirst().id().equals("minecraft:gold_ingot")&&layer.priority()==0,"An indexed action cannot remove a different rule after another editor changes the list");
        h.assertTrue(token(player)==null,"Concurrent configuration changes close the stale session");
        RuneSettingsPackets.open(player,surface,layer);token=token(player);layer.transferred(1,0);layer.report("Waiting for matching contents");
        RuneSettingsPackets.handle(player,action(token,Operation.SAVE,7,""));
        h.assertTrue(layer.priority()==7&&layer.transferredItems()==1,"Runtime counters and status do not invalidate a configuration editor");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void autosaveKeepsSessionAndOrderedEditsWithoutLosingRules(GameTestHelper h) throws ReflectiveOperationException {
        BlockPos pos=new BlockPos(3,2,3);h.setBlock(pos,Blocks.CHEST);
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"rune-autosave"));player.setPos(h.absolutePos(pos).getCenter().add(0,0,2));
        RuneSurface surface=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(pos),Direction.SOUTH);
        RuneLayer layer=surface.addLayer(Identifier.parse("astral_repository:push_rune"),RuneLayer.Mode.PUSH);
        RuneLayer neighbor=surface.addLayer(Identifier.parse("astral_repository:pull_rune"),RuneLayer.Mode.PULL);
        RuneSettingsPackets.open(player,surface,layer);UUID token=token(player);
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.AUTOSAVE,false,false,false,1,32,2,"",-1));
        h.assertTrue(layer.priority()==2&&!layer.enabled()&&layer.filter().minimum()==1&&token.equals(token(player)),"Autosave persists fields without consuming the editing session");
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.ADD_RULE,false,false,false,1,32,3,"minecraft:iron_ingot",-1));
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.AUTOSAVE,true,true,true,12,64,4,"",-1));
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.ADD_RULE,true,true,true,12,64,4,"!fluid:minecraft:water",-1));
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.AUTOSAVE,false,false,true,21,80,13,"",-1));
        h.assertTrue(token.equals(token(player))&&layer.priority()==13&&layer.enabled()&&layer.filter().minimum()==21&&layer.filter().target()==80&&!layer.filter().all()&&!layer.filter().blacklist(),"Successive acknowledged snapshots preserve the latest values without invalidating their own session");
        h.assertTrue(layer.filter().entries().size()==2&&layer.filter().entries().getFirst().id().equals("minecraft:iron_ingot")&&layer.filter().entries().get(1).kind()==FilterRules.Kind.FLUID&&layer.filter().entries().get(1).exclude(),"Rapid autosaves do not erase intervening item and excluded-fluid rules");
        h.assertTrue(neighbor.priority()==0&&neighbor.filter().entries().isEmpty(),"Autosave remains isolated to the selected layer");
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.SAVE,false,false,true,22,81,14,"",-1));
        h.assertTrue(token(player)==null&&layer.priority()==14&&layer.filter().minimum()==22&&layer.filter().target()==81&&layer.filter().entries().size()==2,"Done commits the final pending values and closes the session without losing rules");
        RuneSettingsPackets.handle(player,action(token,Operation.AUTOSAVE,99,""));
        h.assertTrue(layer.priority()==14,"Queued autosaves cannot reopen or replay a closed editor");

        var other=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"rune-other-editor"));other.setPos(player.position());
        RuneSettingsPackets.open(other,surface,layer);UUID otherToken=token(other);
        RuneSettingsPackets.open(player,surface,layer);token=token(player);
        RuneSettingsPackets.handle(player,action(token,Operation.AUTOSAVE,16,""));
        RuneSettingsPackets.handle(other,action(otherToken,Operation.AUTOSAVE,17,""));
        h.assertTrue(layer.priority()==16&&token(other)==null&&token.equals(token(player)),"Autosave from a stale second editor cannot overwrite the current editor's changes");
        layer.setEnabled(false);layer.report("Transfer outcome uncertain; paused");
        RuneSettingsPackets.handle(player,action(token,Operation.AUTOSAVE,18,""));
        h.assertTrue(!layer.enabled()&&layer.priority()==16&&token(player)==null,"A delayed autosave cannot reverse an automatic safety pause");
        h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void rejectedSettingsOperationsSendFailureWithoutClosingOrLosingPendingValues(GameTestHelper h) throws ReflectiveOperationException {
        BlockPos pos=new BlockPos(3,2,3);h.setBlock(pos,Blocks.CHEST);
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"rune-rejections"));player.setPos(h.absolutePos(pos).getCenter().add(0,0,2));
        var pages=capturePages(player);
        RuneSurface surface=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(pos),Direction.SOUTH);
        RuneLayer layer=surface.addLayer(Identifier.parse("astral_repository:push_rune"),RuneLayer.Mode.PUSH);
        layer.filter().add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,net.minecraft.world.item.ItemStack.EMPTY);layer.changed();
        RuneSettingsPackets.open(player,surface,layer);UUID token=token(player);
        h.assertTrue(pages.getLast().accepted()&&!pages.getLast().closed(),"Initial editor page is a successful nonclosing response");
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.TOGGLE_ITEM_DATA,false,false,true,3,32,11,"",0));
        var failure=pages.getLast();
        h.assertTrue(!failure.accepted()&&!failure.closed()&&failure.status().contains("capture")&&layer.priority()==0&&token.equals(token(player)),"An unavailable exact-data action explicitly rejects its pending scalar snapshot and leaves the editor open");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try { RuneSettingsPackets.Page.CODEC.encode(buffer,failure);h.assertTrue(RuneSettingsPackets.Page.CODEC.decode(buffer).equals(failure),"Failure acknowledgement survives the actual wire codec"); }
        finally { buffer.release(); }
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.AUTOSAVE,false,false,true,3,32,11,"",-1));
        h.assertTrue(pages.getLast().accepted()&&!pages.getLast().closed()&&layer.priority()==11&&layer.filter().minimum()==3,"A following automatic scalar save is separately acknowledged without retrying the failed operation");

        var ids=net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().stream().filter(id->!id.equals(Identifier.withDefaultNamespace("air"))).sorted().limit(65).toList();
        layer.filter().clearPredicates();for(int index=0;index<64;index++)layer.filter().add(FilterRules.Kind.ITEM,ids.get(index).toString(),false,net.minecraft.world.item.ItemStack.EMPTY);layer.changed();
        RuneSettingsPackets.open(player,surface,layer);token=token(player);
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.ADD_RULE,false,false,true,4,40,22,ids.get(64).toString(),-1));
        h.assertTrue(!pages.getLast().accepted()&&!pages.getLast().closed()&&pages.getLast().status().contains("64")&&layer.filter().entries().size()==64&&layer.priority()==11,"The sixty-fifth rule returns an explicit failure without applying its scalar snapshot");
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.AUTOSAVE,false,false,true,4,40,22,"",-1));
        h.assertTrue(pages.getLast().accepted()&&layer.priority()==22&&layer.filter().entries().size()==64,"Pending scalar changes still save after a full-filter rejection");

        for(int slot=0;slot<36;slot++)player.getInventory().setItem(slot,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE,64));
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token,Operation.REMOVE_RUNE,false,false,true,8,44,33,"",-1));
        h.assertTrue(pages.getLast().accepted()&&pages.getLast().closed()&&surface.get(layer.id())==null&&token(player)==null&&count(player,Items.PAPER)==0,"Item-free removal succeeds with a full inventory and closes its session");
        h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void savingPlacedRunePreservesPortableArtworkAndIndependentBehavior(GameTestHelper h)throws Exception{
        BlockPos pos=new BlockPos(3,2,3);h.setBlock(pos,Blocks.CHEST);
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"painted-preset-save"));player.setPos(h.absolutePos(pos).getCenter().add(0,0,2));
        var pages=new java.util.ArrayList<com.cappleapple.astralrepository.network.WandPackets.Page>();
        var connection=player.connection.getConnection();
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(player.getServer(),connection,player,net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom&&custom.payload() instanceof com.cappleapple.astralrepository.network.WandPackets.Page page)pages.add(page);}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){send(packet);}
        };
        int[] pixels=new int[128*128];for(int i=0;i<pixels.length;i++)pixels[i]=i%7==0?0:0xff000000|(i*7919)&0xffffff;
        var icons=java.util.List.of(new RuneDesign.Icon(Identifier.withDefaultNamespace("diamond"),12.25f,7.5f,19.75f,-137.5f),new RuneDesign.Icon(Identifier.withDefaultNamespace("water_bucket"),123.5f,111.25f,64.5f,225.25f));
        var design=new RuneDesign(128,pixels,icons);var filter=new FilterRules();
        var named=new net.minecraft.world.item.ItemStack(Items.IRON_INGOT);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Stored sample"));
        filter.add(FilterRules.Kind.COMPONENTS,"minecraft:iron_ingot",false,named);filter.add(FilterRules.Kind.FLUID_TAG,"minecraft:water",true,net.minecraft.world.item.ItemStack.EMPTY);filter.setMinimum(12);filter.setTarget(80);
        var cadence=RuneCadence.DEFAULT.with(RuneCadence.Kind.ITEMS,new RuneCadence.Rate(32,7)).with(RuneCadence.Kind.FLUID,new RuneCadence.Rate(600,11)).with(RuneCadence.Kind.ENERGY,new RuneCadence.Rate(5000,13)).with(RuneCadence.Kind.SOURCE,new RuneCadence.Rate(750,17));
        var original=new RunePreset(UUID.randomUUID(),"Painted source",RuneLayer.Mode.PULL,design,filter.save(player.registryAccess()),17,false,cadence);
        var surface=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(pos),Direction.SOUTH);var layer=surface.addLayer(RuneGlyph.id(RuneLayer.Mode.PULL),RuneLayer.Mode.PULL);layer.preset(original,player.registryAccess());
        var originalFilter=layer.filter().save(player.registryAccess());
        RuneSettingsPackets.open(player,surface,layer);
        RuneSettingsPackets.handle(player,new RuneSettingsPackets.Action(token(player),Operation.SAVE_PRESET,false,false,false,12,80,17,"",-1));
        var opening=pages.stream().filter(p->p.open()&&p.data().contains("EditPreset")).findFirst().orElseThrow();
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),player.registryAccess());
        com.cappleapple.astralrepository.network.WandPackets.Page received;
        try{com.cappleapple.astralrepository.network.WandPackets.Page.CODEC.encode(buffer,opening);received=com.cappleapple.astralrepository.network.WandPackets.Page.CODEC.decode(buffer);}finally{buffer.release();}
        var copied=RunePreset.load(received.data().getCompound("EditPreset"),player.registryAccess());
        h.assertTrue(!copied.id().equals(original.id())&&!copied.id().equals(layer.id()),"Save Preset creates a new independent identity");
        h.assertTrue(copied.design().size()==128&&java.util.Arrays.equals(copied.design().argbPixels(),design.argbPixels())&&copied.design().icons().equals(icons),"Actual clientbound editor packet preserves the whole canvas including cropped pixels and separate item transforms");
        h.assertTrue(copied.mode()==RuneLayer.Mode.PULL&&copied.filter().equals(originalFilter)&&copied.priority()==17&&!copied.enabled()&&copied.cadence().equals(cadence),"Artwork export retains all placed behavior");
        String json=RunePresetFiles.encode(copied);var shared=RunePresetFiles.decode(json,player.registryAccess());
        h.assertTrue(shared.save().equals(copied.save()),"One portable JSON preserves pixels, resolution, item overlays and behavior without external image files");
        var handler=com.cappleapple.astralrepository.network.WandPackets.class.getDeclaredMethod("handle",ServerPlayer.class,com.cappleapple.astralrepository.network.WandPackets.Action.class);handler.setAccessible(true);
        handler.invoke(null,player,new com.cappleapple.astralrepository.network.WandPackets.Action(received.session(),com.cappleapple.astralrepository.network.WandPackets.Op.IMPORT,shared.id(),shared.save()));
        var library=com.cappleapple.astralrepository.network.RuneLibraryData.get(player.getServer()).library(player.getUUID());
        h.assertTrue(library.get(shared.id()).save().equals(shared.save()),"Portable instance import retains the artwork in the server mirror without a held wand");
        int[] editedPixels=shared.design().argbPixels();editedPixels[0]=0xff12abcd;var editedFilter=shared.filter();for(String key:java.util.List.copyOf(editedFilter.getAllKeys()))editedFilter.remove(key);
        var edited=new RunePreset(shared.id(),"Edited copy",RuneLayer.Mode.FILTER,new RuneDesign(128,editedPixels,java.util.List.of()),editedFilter,-5,true,RuneCadence.DEFAULT);
        handler.invoke(null,player,new com.cappleapple.astralrepository.network.WandPackets.Action(received.session(),com.cappleapple.astralrepository.network.WandPackets.Op.SAVE,edited.id(),edited.save()));
        h.assertTrue(library.get(shared.id()).design().argb(0,0)==0xff12abcd&&library.get(shared.id()).design().icons().isEmpty(),"The saved copy can be edited independently");
        h.assertTrue(java.util.Arrays.equals(layer.design().argbPixels(),design.argbPixels())&&layer.design().icons().equals(icons)&&layer.filter().save(player.registryAccess()).equals(originalFilter)&&layer.mode()==RuneLayer.Mode.PULL&&layer.priority()==17&&!layer.enabled()&&layer.cadence().equals(cadence),"Editing the imported preset cannot mutate the placed rune");
        h.assertTrue(java.util.Arrays.equals(shared.design().argbPixels(),design.argbPixels())&&shared.filter().equals(originalFilter),"Draft pixel and filter arrays cannot mutate the exported snapshot");
        h.succeed();
    }
    private static java.util.List<RuneSettingsPackets.Page> capturePages(ServerPlayer player) {
        var pages=new java.util.ArrayList<RuneSettingsPackets.Page>();
        var connection=player.connection.getConnection();
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(player.getServer(),connection,player,net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom&&custom.payload() instanceof RuneSettingsPackets.Page page)pages.add(page);
            }
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener) { send(packet); }
        };
        return pages;
    }
    private static RuneSettingsPackets.Action action(UUID token,Operation operation,int priority,String rule) {
        return new RuneSettingsPackets.Action(token,operation,false,false,true,0,Long.MAX_VALUE,priority,rule,-1);
    }
    private static int count(ServerPlayer player,Item item) {int count=0;for(var stack:player.getInventory().items)if(stack.is(item))count+=stack.getCount();return count;}

    // Test-only access controls the session clock without advancing every parallel GameTest's world.
    @SuppressWarnings("unchecked")
    private static Map<ServerPlayer,Object> sessions() throws ReflectiveOperationException {
        var field=RuneSettingsPackets.class.getDeclaredField("SESSIONS");field.setAccessible(true);return (Map<ServerPlayer,Object>)field.get(null);
    }
    static UUID token(ServerPlayer player) throws ReflectiveOperationException {
        Object session=sessions().get(player);if(session==null)return null;
        var method=session.getClass().getDeclaredMethod("token");method.setAccessible(true);return (UUID)method.invoke(session);
    }
    private static void expire(ServerPlayer player,long time) throws ReflectiveOperationException {
        Object old=sessions().get(player);Class<?> type=old.getClass();
        var surface=type.getDeclaredMethod("surface");surface.setAccessible(true);
        var layer=type.getDeclaredMethod("layer");layer.setAccessible(true);
        var configuration=type.getDeclaredMethod("configuration");configuration.setAccessible(true);
        var constructor=type.getDeclaredConstructor(UUID.class,RuneSurface.class,UUID.class,long.class,net.minecraft.nbt.CompoundTag.class);constructor.setAccessible(true);
        sessions().put(player,constructor.newInstance(token(player),surface.invoke(old),layer.invoke(old),time,configuration.invoke(old)));
    }
}
