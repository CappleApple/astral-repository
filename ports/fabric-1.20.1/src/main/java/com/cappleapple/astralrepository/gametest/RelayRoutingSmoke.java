package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.client.WorldVisuals;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

/** Real payload delivery and sequential flight playback in a silent integrated client. */
public final class RelayRoutingSmoke {
    private static int phase,ticks;
    private static volatile String failure;
    private static volatile boolean ready;
    private static NetworkPackets.Visual received;
    private static long started;
    private static NetworkPackets.Visual runeFlight;
    private static final List<NetworkPackets.Visual> resourceFlights=new ArrayList<>();
    private static long splineStarted;
    private static final List<NetworkPackets.Visual> craftingFlights=new ArrayList<>();
    private static final Set<Integer> captured=new HashSet<>();
    private static final BlockPos NEXUS=new BlockPos(20,-59,12),STORE=new BlockPos(64,-59,12);
    private static final Path OUT=Path.of("../build/client-smoke/routing");
    public static boolean tick()throws Exception{
        var mc=Minecraft.getInstance();if(failure!=null)throw new AssertionError(failure);if(++ticks>1000)throw new AssertionError("Routing client timeout phase "+phase);
        if(phase==0){phase=1;ticks=0;NetworkPackets.visualReceiver=p->{WorldVisuals.add(p);if(p.stack().is(Items.LAPIS_LAZULI))runeFlight=p;if(p.slot()==-2||p.slot()==-3)resourceFlights.add(p);if(p.stack().is(Items.EMERALD)){received=p;started=mc.level.getGameTime();}if(p.slot()<0&&p.from().getX()>=20&&(p.stack().is(Items.IRON_INGOT)||p.stack().is(Items.IRON_TRAPDOOR)))craftingFlights.add(p);};
            mc.getSingleplayerServer().execute(()->{try{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=p.serverLevel();p.closeContainer();AstralConfig.instantPlayerInteractions.set(true);
                for(int x:new int[]{20,34,48,62}){var pos=new BlockPos(x,-59,12);level.getChunkAt(pos);level.setBlockAndUpdate(pos,(x==20?AstralContent.STORAGE_NEXUS.get():AstralContent.RELAY_CRYSTAL.get()).defaultBlockState());((CrystalNodeBlockEntity)level.getBlockEntity(pos)).setChannel(2);}
                level.setBlockAndUpdate(STORE,Blocks.CHEST.defaultBlockState());((ChestBlockEntity)level.getBlockEntity(STORE)).setItem(0,new ItemStack(Items.EMERALD,16));
                Vec3 eye=new Vec3(45.5,-48,40.5),delta=new Vec3(44.5,-58.5,12.5).subtract(eye);float yaw=(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90,pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));p.connection.teleport(eye.x,eye.y-p.getEyeHeight(),eye.z,yaw,pitch);ready=true;
            }catch(Throwable e){failure=e.toString();}});
        }else if(phase==1&&ready&&ticks>60){phase=2;ticks=0;mc.getSingleplayerServer().execute(()->{try{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var origin=GlobalPos.of(p.level().dimension(),NEXUS);var n=NetworkManager.get(p.server).networkAt(origin);check(n!=null,"Automatic client network missing");
                var result=LogisticsTiming.playerInteraction(()->n.extractAt(new ItemKey(new ItemStack(Items.EMERALD)),1,origin));check(result.getCount()==1,"Instant withdrawal did not finish immediately");check(((ChestBlockEntity)p.serverLevel().getBlockEntity(STORE)).getItem(0).getCount()==15,"Provider debit was not immediate");
            }catch(Throwable e){failure=e.toString();}});
        }else if(phase==2&&received!=null){
            check(received.from().equals(STORE)&&received.to().equals(NEXUS)&&received.path().size()>=4,"Decoded packet lost its actual relay route");
            check(received.duration()>=100&&received.duration()==TransferVisuals.duration(received.path()),"Instant player interaction collapsed the flight");
            var field=WorldVisuals.class.getDeclaredField("flights");field.setAccessible(true);var flights=(List<?>)field.get(null);
            long matching=0;for(var flight:flights){var packet=flight.getClass().getDeclaredMethod("packet");packet.setAccessible(true);if(packet.invoke(flight)==received)matching++;}
            long elapsed=mc.level.getGameTime()-started;
            if(elapsed<received.duration())check(matching==1,"Every hop must belong to one live flight");
            int stage=(int)Math.min(3,elapsed*4/received.duration());
            if(captured.add(stage)){Files.createDirectories(OUT);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve("flight_"+stage+".png"));}}
            if(elapsed>received.duration()+2){check(matching==0,"Completed flight was not retired");check(!mc.mouseHandler.isMouseGrabbed()&&mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)==0,"Test client must remain muted and mouse-free");Files.writeString(OUT.resolve("result.txt"),"PASS: immediate emerald debit; actual routed Visual packet; "+received.path().size()+" waypoints; "+received.duration()+" ticks; one sequential client flight, expiry, four captures, muted and mouse-free.\n"+received.path());phase=3;ticks=0;mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var l=p.serverLevel();((ChestBlockEntity)l.getBlockEntity(STORE)).setItem(1,new ItemStack(Items.IRON_INGOT,4));l.setBlockAndUpdate(STORE.east(),Blocks.CRAFTING_TABLE.defaultBlockState());var shelf=STORE.south();l.setBlockAndUpdate(shelf,Blocks.CHISELED_BOOKSHELF.defaultBlockState());var tome=new ItemStack(AstralContent.RECIPE_TOME.get());var tag=new net.minecraft.nbt.CompoundTag();tag.put("Output",new ItemStack(Items.IRON_TRAPDOOR).save(l.registryAccess()));tag.putString("OutputId","minecraft:iron_trapdoor");tome.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));((net.minecraft.world.Container)l.getBlockEntity(shelf)).setItem(0,tome);NetworkManager.changed(l,STORE.east());NetworkManager.changed(l,shelf);}catch(Throwable e){failure=e.toString();}});}
        }else if(phase==3&&ticks>60){phase=4;ticks=0;mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var n=NetworkManager.get(p.server).networkAt(GlobalPos.of(p.level().dimension(),NEXUS));check(n.crafting().request(p,new ItemStack(Items.IRON_TRAPDOOR),1).accepted(),"Actual network craft was not accepted");}catch(Throwable e){failure=e.toString();}});
        }else if(phase==4&&craftingFlights.stream().anyMatch(p->p.stack().is(Items.IRON_TRAPDOOR))){
            check(craftingFlights.size()==2,"Craft emitted an extra Nexus detour: "+craftingFlights);
            var ingredient=craftingFlights.get(0);var product=craftingFlights.getLast();
            check(ingredient.from().equals(STORE)&&ingredient.to().equals(STORE.east())&&ingredient.path().size()==2,"Ingredient did not travel straight from chest to adjacent table");
            check(product.from().equals(STORE.east())&&product.to().equals(STORE)&&product.path().size()==2,"Product did not travel straight from table back to storage");
            Files.writeString(OUT.resolve("result.txt"),"\nPASS: actual network autocraft produced exactly two direct flights: storage -> adjacent table -> storage; no Nexus transit.\n",StandardOpenOption.APPEND);
            phase=5;ticks=0;mc.getSingleplayerServer().execute(()->{try{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=player.serverLevel();
                var target=STORE.west(10);level.setBlockAndUpdate(target,Blocks.CHEST.defaultBlockState());
                var surface=RuneSurfaces.getOrCreate(level,STORE,Direction.SOUTH);var rune=surface.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
                rune.filter().add(FilterRules.Kind.ITEM,"minecraft:lapis_lazuli",false,ItemStack.EMPTY);rune.filter().setTarget(1);
                surface.toggleTarget(rune.id(),GlobalPos.of(level.dimension(),target),Direction.UP);
                ((ChestBlockEntity)level.getBlockEntity(STORE)).setItem(2,new ItemStack(Items.LAPIS_LAZULI,1));
                var cell=TransferVisuals.rune(rune);
                var path=List.of(GlobalPos.of(level.dimension(),STORE),GlobalPos.of(level.dimension(),STORE.offset(-4,0,6)),GlobalPos.of(level.dimension(),target));
                // Synthetic resource payloads exercise the same renderer/codec without pretending to test fluid or energy ownership.
                TransferVisuals.send(player.server,path,ItemStack.EMPTY,0x7755FF,-2,cell,null);
                TransferVisuals.send(player.server,path,ItemStack.EMPTY,0x55DDFF,-3,cell,null);
            }catch(Throwable e){failure=e.toString();}});
        }else if(phase==5&&runeFlight!=null&&resourceFlights.size()>=2){
            check(runeFlight.departure()!=null&&runeFlight.departure().face()==Direction.SOUTH,"Actual rune transfer lost its launch face in packet encoding");
            var normal=Vec3.atLowerCornerOf(Direction.SOUTH.getNormal());var start=TransferVisuals.position(runeFlight,0);
            check(TransferVisuals.position(runeFlight,.001).subtract(start).dot(normal)>0,"Rune must first move outward from its face");
            for(var p:resourceFlights){check(p.path().size()==3&&p.departure()!=null,"Resource packet lost spline nodes or launch tangent");
                double t=TransferVisuals.legTicks(p.path().get(0),p.path().get(1))/(double)p.duration(),e=.0001;
                Vec3 at=TransferVisuals.position(p,t),before=at.subtract(TransferVisuals.position(p,t-e)),after=TransferVisuals.position(p,t+e).subtract(at);
                check(before.normalize().dot(after.normalize())>.999,"Resource spline changed direction abruptly at relay");
            }
            splineStarted=mc.level.getGameTime();phase=6;ticks=0;
        }else if(phase==6){
            if(ticks==5||ticks==15||ticks==30)try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve("spline_"+ticks+".png"));}
            if(ticks>80){
                check(!mc.mouseHandler.isMouseGrabbed()&&mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)==0,"Client must remain muted and mouse-free");
                Files.writeString(OUT.resolve("result.txt"),"PASS: actual rune item packet retains glyph position and outward face tangent; decoded fluid/energy fixture packets follow one continuous three-node spline; captures at three times.\n",StandardOpenOption.APPEND);
                Files.writeString(Path.of("../build/client-smoke/result.txt"),"PASS: routed player flight, direct autocrafting and rune/resource splines; see routing/result.txt");NetworkPackets.visualReceiver=WorldVisuals::add;return true;
            }
        }
        return false;
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
