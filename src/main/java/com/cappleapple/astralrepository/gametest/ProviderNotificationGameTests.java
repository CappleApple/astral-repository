package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.compat.CompatibilityRegistry;
import com.cappleapple.astralrepository.compat.ItemHandlerStorageProvider;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder("astral_repository") @PrefixGameTestTemplate(false)
public final class ProviderNotificationGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=500)
    public static void sharedIdentityInvalidatesAllWatchingNetworksAndSurvivesRemoval(GameTestHelper h){
        var a=new BlockPos(3,2,3);var b=new BlockPos(12,2,3);
        var first=a.west();var second=b.east();
        h.setBlock(a,AstralContent.STORAGE_NEXUS.get());h.setBlock(b,AstralContent.STORAGE_NEXUS.get());
        ((CrystalNodeBlockEntity)h.getBlockEntity(a)).setChannel(13);((CrystalNodeBlockEntity)h.getBlockEntity(b)).setChannel(14);
        h.setBlock(first,Blocks.ENCHANTING_TABLE);h.setBlock(second,Blocks.ENCHANTING_TABLE);
        var handler=new ItemStackHandler(2);handler.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,61));
        var level=h.getLevel();var aa=GlobalPos.of(level.dimension(),h.absolutePos(a));var bb=GlobalPos.of(level.dimension(),h.absolutePos(b));
        var pa=h.absolutePos(first);var pb=h.absolutePos(second);String id="notification_alias_"+UUID.randomUUID();
        CompatibilityRegistry.registerStorage(id,(world,pos,side)->world==level&&(pos.equals(pa)||pos.equals(pb))
                &&world.getBlockState(pos).is(Blocks.ENCHANTING_TABLE)
                ?List.of(new ItemHandlerStorageProvider(id+pos,handler,handler,()->world.getBlockState(pos).is(Blocks.ENCHANTING_TABLE))):List.of());
        var manager=NetworkManager.get(level.getServer());var key=new ItemKey(new ItemStack(Items.IRON_INGOT));
        h.startSequence().thenWaitUntil(()->{
            var one=manager.networkAt(aa);var two=manager.networkAt(bb);
            h.assertTrue(one!=null&&two!=null&&one!=two&&one.snapshot().getOrDefault(key,0L)==61&&two.snapshot().getOrDefault(key,0L)==61,"Independent networks discover the same physical provider identity");
        }).thenExecute(()->{
            handler.extractItem(0,7,false);
            var one=manager.networkAt(aa);var two=manager.networkAt(bb);long beforeOne=one.version(),beforeTwo=two.version();
            manager.providerIdentitiesChanged(Set.of(handler),Set.of(handler));
            h.assertTrue(one.version()==beforeOne+1&&two.version()==beforeTwo+1,"Overlapping endpoint identities notify each watcher once");
            one.tick(0,64);two.tick(0,64);
            h.assertTrue(one.snapshot().getOrDefault(key,0L)==54&&two.snapshot().getOrDefault(key,0L)==54,"Every watching network is invalidated immediately");
            int sameTick=level.getServer().getTickCount();
            handler.extractItem(0,3,false);NetworkManager.providerChanged(level,pa);NetworkManager.providerChanged(level,pb);one.tick(0,64);two.tick(0,64);
            h.assertTrue(one.snapshot().getOrDefault(key,0L)==51&&two.snapshot().getOrDefault(key,0L)==51,"Position notifications remain immediate after identity invalidation and polling");
            handler.insertItem(0,new ItemStack(Items.IRON_INGOT,3),false);NetworkManager.providerChanged(level,pa);NetworkManager.providerChanged(level,pb);one.tick(0,64);two.tick(0,64);
            h.assertTrue(level.getServer().getTickCount()==sameTick&&one.snapshot().getOrDefault(key,0L)==54&&two.snapshot().getOrDefault(key,0L)==54,"A second mutation after polling in the same tick invalidates both indexes again");
            h.setBlock(first,Blocks.AIR);NetworkManager.changed(level,pa);
        }).thenWaitUntil(()->h.assertTrue(!manager.networkAt(aa).snapshot().containsKey(key),"Removed provider leaves its first network index"))
        .thenExecute(()->{
            handler.extractItem(0,5,false);manager.providerIdentitiesChanged(Set.of(handler));manager.networkAt(bb).tick(0,64);
            h.assertTrue(manager.networkAt(bb).snapshot().getOrDefault(key,0L)==49,"Removing one watcher keeps the other watcher live");
            h.assertTrue(handler.getStackInSlot(0).getCount()==49,"Notifications do not mutate physical contents");
        }).thenExecute(h::succeed);
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=100)
    public static void runeViewIndexesKeepEqualAliasesAndRemoveReplacedEndpoints(GameTestHelper h)throws Exception{
        var first=new BlockPos(3,2,3);var second=new BlockPos(12,2,3);
        h.setBlock(first,Blocks.ENCHANTING_TABLE);h.setBlock(second,Blocks.ENCHANTING_TABLE);
        var level=h.getLevel();var pa=h.absolutePos(first);var pb=h.absolutePos(second);
        var firstPos=GlobalPos.of(level.dimension(),pa);var secondPos=GlobalPos.of(level.dimension(),pb);
        var handler=new ItemStackHandler(2);handler.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,9));
        var generation=new java.util.concurrent.atomic.AtomicInteger();UUID identity=UUID.randomUUID();String id="rune_view_alias_"+identity;
        record BackingIdentity(UUID value){}
        CompatibilityRegistry.registerStorage(id,(world,pos,side)->{
            if(world!=level||!pos.equals(pa)&&!pos.equals(pb)||!world.getBlockState(pos).is(Blocks.ENCHANTING_TABLE))return List.of();
            int discovered=generation.get();
            return List.of(new ItemHandlerStorageProvider(id+pos+side,new BackingIdentity(identity),handler,()->generation.get()==discovered&&world.getBlockState(pos).is(Blocks.ENCHANTING_TABLE)));
        });
        var worker=new DirectRuneTransfers(level.getServer());
        var endpoint=DirectRuneTransfers.class.getDeclaredMethod("endpoint",RuneLayer.Target.class);endpoint.setAccessible(true);
        var targets=List.of(new RuneLayer.Target(firstPos,Direction.UP),new RuneLayer.Target(firstPos,Direction.NORTH),new RuneLayer.Target(secondPos,Direction.UP));
        Object[] endpoints=new Object[3],views=new Object[3];
        for(int i=0;i<targets.size();i++)endpoints[i]=endpoint.invoke(worker,targets.get(i));
        var items=endpoints[0].getClass().getDeclaredField("items");items.setAccessible(true);
        for(int i=0;i<views.length;i++)views[i]=((List<?>)items.get(endpoints[i])).getFirst();
        var poll=views[0].getClass().getDeclaredMethod("poll",long.class);poll.setAccessible(true);
        var contents=views[0].getClass().getDeclaredField("contents");contents.setAccessible(true);
        var ready=views[0].getClass().getDeclaredField("ready");ready.setAccessible(true);
        long sameTick=level.getServer().getTickCount();var key=new ItemKey(new ItemStack(Items.IRON_INGOT));
        for(Object view:views){poll.invoke(view,sameTick);h.assertTrue(Objects.equals(((Map<?,?>)contents.get(view)).get(key),9L),"All sided aliases see initial physical contents");}
        handler.extractItem(0,2,false);worker.invalidateIdentities(Set.of(new BackingIdentity(identity)));
        for(Object view:views){poll.invoke(view,sameTick);h.assertTrue(Objects.equals(((Map<?,?>)contents.get(view)).get(key),7L),"Equal backing identities invalidate all independently cached sided views");}
        handler.extractItem(0,2,false);worker.invalidate(firstPos);
        for(int i=0;i<views.length;i++){poll.invoke(views[i],sameTick);h.assertTrue(Objects.equals(((Map<?,?>)contents.get(views[i])).get(key),i<2?5L:7L),"Position invalidation refreshes every face of that host without discarding unrelated cached views");}
        generation.incrementAndGet();Object[] replacements=new Object[3];
        for(int i=0;i<targets.size();i++){
            endpoints[i]=endpoint.invoke(worker,targets.get(i));replacements[i]=((List<?>)items.get(endpoints[i])).getFirst();poll.invoke(replacements[i],sameTick);
            h.assertTrue(replacements[i]!=views[i]&&Objects.equals(((Map<?,?>)contents.get(replacements[i])).get(key),5L),"Invalid providers are replaced with current live views");
        }
        var index=DirectRuneTransfers.class.getDeclaredMethod("index",endpoints[0].getClass());index.setAccessible(true);index.invoke(worker,endpoints[0]);
        var identitiesField=DirectRuneTransfers.class.getDeclaredField("itemsByIdentity");identitiesField.setAccessible(true);var identities=(Map<?,?>)identitiesField.get(worker);
        var positionsField=DirectRuneTransfers.class.getDeclaredField("endpointsByPosition");positionsField.setAccessible(true);var positions=(Map<?,?>)positionsField.get(worker);
        h.assertTrue(((List<?>)identities.get(new BackingIdentity(identity))).size()==3&&((List<?>)positions.get(firstPos)).size()==2,"Indexes retain each view instance once across replacement and duplicate indexing");
        worker.invalidateIdentities(Set.of(new BackingIdentity(identity)));
        for(int i=0;i<views.length;i++)h.assertTrue(ready.getBoolean(views[i])&&!ready.getBoolean(replacements[i]),"Replaced views leave the identity index while replacement views receive invalidation");
        var clock=DirectRuneTransfers.class.getDeclaredField("clock");clock.setAccessible(true);clock.setLong(worker,clock.getLong(worker)+201);
        var prune=DirectRuneTransfers.class.getDeclaredMethod("pruneEndpoints");prune.setAccessible(true);prune.invoke(worker);
        h.assertTrue(identities.isEmpty()&&positions.isEmpty(),"Pruned endpoints release empty identity and position index buckets");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",batch="coverage_cache",timeoutTicks=100)
    public static void positionalNotificationsDeduplicateLiveFacesWithoutDebouncing(GameTestHelper h)throws Exception{
        var first=new BlockPos(3,2,3);var second=new BlockPos(11,2,3);var host=new BlockPos(7,2,3);
        h.setBlock(first,AstralContent.STORAGE_NEXUS.get());h.setBlock(second,AstralContent.STORAGE_NEXUS.get());h.setBlock(host,Blocks.CHEST);
        var a=(CrystalNodeBlockEntity)h.getBlockEntity(first);var b=(CrystalNodeBlockEntity)h.getBlockEntity(second);
        a.setChannel(12);b.setChannel(13);a.setPriority(100);
        var level=h.getLevel();var position=GlobalPos.of(level.dimension(),h.absolutePos(host));var manager=NetworkManager.get(level.getServer());
        var upper=RuneSurfaces.getOrCreate(level,position.pos(),Direction.UP);upper.setChannel(13);upper.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
        var north=RuneSurfaces.getOrCreate(level,position.pos(),Direction.NORTH);north.setChannel(13);north.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
        var south=RuneSurfaces.getOrCreate(level,position.pos(),Direction.SOUTH);south.setChannel(12);south.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
        h.assertTrue(manager.toggleLink(upper.address(),b.address()).success()&&manager.toggleLink(north.address(),b.address()).success()&&manager.toggleLink(south.address(),a.address()).success(),"Three faces join their intended owner components");
        var tick=NetworkManager.class.getDeclaredMethod("tick");tick.setAccessible(true);tick.invoke(manager);
        var one=manager.networkAt(a.address());var two=manager.networkAt(b.address());
        h.assertTrue(one!=null&&two!=null&&one!=two,"Notification fixture has two independent components");
        assertNotifications(h,position,one,two,1,1);
        assertNotifications(h,position,one,two,1,1);
        upper.toggleEnabled();assertNotifications(h,position,one,two,1,1);
        north.toggleEnabled();assertNotifications(h,position,one,two,1,0);
        upper.toggleEnabled();assertNotifications(h,position,one,two,1,1);
        h.setBlock(host,Blocks.AIR);assertNotifications(h,position,one,two,1,0);
        h.succeed();
    }
    private static void assertNotifications(GameTestHelper h,GlobalPos position,AstralNetwork first,AstralNetwork second,int firstChanges,int secondChanges){
        long beforeFirst=first.version(),beforeSecond=second.version();
        NetworkManager.providerChanged(h.getLevel(),position.pos());
        h.assertTrue(first.version()==beforeFirst+firstChanges&&second.version()==beforeSecond+secondChanges,"Each current owner receives one notification per mutation, including repeated mutations in the same tick");
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",batch="coverage_cache",timeoutTicks=100)
    public static void cachedCoverageKeepsLiveFaceStateAndOwnershipEdits(GameTestHelper h)throws Exception{
        var first=new BlockPos(3,2,3);var second=new BlockPos(11,2,3);var host=new BlockPos(7,2,3);
        h.setBlock(first,AstralContent.STORAGE_NEXUS.get());h.setBlock(second,AstralContent.STORAGE_NEXUS.get());h.setBlock(host,Blocks.CHEST);
        var a=(CrystalNodeBlockEntity)h.getBlockEntity(first);var b=(CrystalNodeBlockEntity)h.getBlockEntity(second);
        a.setChannel(12);b.setChannel(13);a.setPriority(100);
        var level=h.getLevel();var position=GlobalPos.of(level.dimension(),h.absolutePos(host));
        var face=RuneSurfaces.getOrCreate(level,position.pos(),Direction.UP);face.setChannel(13);
        face.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
        var manager=NetworkManager.get(level.getServer());
        h.assertTrue(manager.toggleLink(face.address(),b.address()).success(),"Explicit face joins its separate component");
        var tick=NetworkManager.class.getDeclaredMethod("tick");tick.setAccessible(true);tick.invoke(manager);
        var covering=NetworkManager.class.getDeclaredMethod("coveringNetworks",GlobalPos.class);covering.setAccessible(true);
        var cacheField=NetworkManager.class.getDeclaredField("coverage");cacheField.setAccessible(true);var cache=(Map<?,?>)cacheField.get(manager);
        var one=manager.networkAt(a.address());var two=manager.networkAt(b.address());
        h.assertTrue(one!=null&&two!=null&&one!=two&&covering.invoke(manager,position).equals(Set.of(one,two)),"Nearest crystal and linked face both receive notifications");
        Object cached=cache.get(position);
        for(int i=0;i<20;i++)NetworkManager.providerChanged(level,position.pos());
        h.assertTrue(cache.get(position)==cached,"Repeated inventory changes reuse the same ownership lookup");
        face.toggleEnabled();
        h.assertTrue(covering.invoke(manager,position).equals(Set.of(one)),"Disabled linked face stops receiving notifications immediately");
        face.toggleEnabled();covering.invoke(manager,position);h.setBlock(host,Blocks.AIR);
        h.assertTrue(covering.invoke(manager,position).equals(Set.of(one)),"Removed host is excluded before saved surface reconciliation");
        b.setPriority(200);
        h.assertTrue(covering.invoke(manager,position).equals(Set.of(two)),"Priority edit immediately invalidates nearest-owner cache");
        b.toggleEnabled();
        h.assertTrue(covering.invoke(manager,position).equals(Set.of(one)),"Disabled crystal cannot retain cached ownership");
        tick.invoke(manager);
        h.assertTrue(covering.invoke(manager,position).equals(Set.of(manager.networkAt(a.address()))),"Rebuild replaces cached component references");
        h.succeed();
    }

}
