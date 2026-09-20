package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralClientConfig;
import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.client.WorldVisuals;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.network.NetworkPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

/** Isolated cosmetic packets travel into transparent test targets; server contents remain untouched. */
public final class ResourceArrivalClientSmoke {
    private static final Path OUT=Path.of("../build/client-smoke");
    private static final BlockPos SOURCE=new BlockPos(34,-56,0);
    private static final List<BlockPos> TARGETS=List.of(new BlockPos(40,-58,0),new BlockPos(40,-56,0),new BlockPos(40,-54,0));
    private static int phase,ticks;
    private static long started;
    private static com.cappleapple.astralrepository.network.TransferVisuals.Endpoint departure;
    private static boolean departureChecked;
    private static boolean pending,initialized,mid,entry,late,arrived,oldGui;
    private static double originalVariance,originalDensity;
    private static volatile String failure;
    private static final StringBuilder measurements=new StringBuilder();

    public static boolean tick()throws Exception{
        try{return run();}
        catch(Throwable error){restore();throw error;}
    }
    private static boolean run()throws Exception{
        var mc=Minecraft.getInstance();
        if(failure!=null)throw new AssertionError(failure);
        if(++ticks>800)throw new AssertionError("Resource arrival timeout in phase "+phase);
        if(phase==0){
            Files.createDirectories(OUT);
            originalVariance=AstralClientConfig.resourceArrivalVariance.get();
            originalDensity=AstralConfig.particleDensity.get();oldGui=mc.options.hideGui;initialized=true;
            AstralClientConfig.resourceArrivalVariance.set(.28);AstralConfig.particleDensity.set(1.0);mc.options.hideGui=true;
            server(1,p->{
                var level=p.serverLevel();p.closeContainer();p.getInventory().clearContent();p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
                p.getAbilities().flying=true;p.onUpdateAbilities();
                for(int x=33;x<=42;x++)for(int y=-60;y<=-51;y++)level.setBlockAndUpdate(new BlockPos(x,y,-2),Blocks.GRAY_CONCRETE.defaultBlockState());
                level.setBlockAndUpdate(SOURCE,Blocks.BARREL.defaultBlockState());
                ((Container)level.getBlockEntity(SOURCE)).setItem(0,new ItemStack(Items.DIAMOND,17));
                var surface=com.cappleapple.astralrepository.content.RuneSurfaces.getOrCreate(level,SOURCE,net.minecraft.core.Direction.EAST);
                var rune=surface.addLayer(com.cappleapple.astralrepository.content.RuneGlyph.id(com.cappleapple.astralrepository.content.RuneLayer.Mode.PUSH),com.cappleapple.astralrepository.content.RuneLayer.Mode.PUSH);
                departure=com.cappleapple.astralrepository.network.TransferVisuals.rune(rune);
                for(var target:TARGETS)level.setBlockAndUpdate(target,Blocks.GLASS.defaultBlockState());
                p.connection.teleport(37.5,-57,6,180,0);p.inventoryMenu.broadcastChanges();
            });
        }else if(phase==1&&!pending&&ticks>35){
            flights().clear();trails().clear();
            for(int i=0;i<TARGETS.size();i++){
                var target=TARGETS.get(i);
                WorldVisuals.add(new NetworkPackets.Visual(SOURCE,target,ItemStack.EMPTY,0x9470ee,100,-2-i,
                        List.of(SOURCE,target),departure,null,i==0?ResourceLocation.withDefaultNamespace("water"):null));
            }
            started=mc.level.getGameTime();checkFlightEndpoints();checkDeparture();next(2);
        }else if(phase==2){
            long age=mc.level.getGameTime()-started;
            if(age>=6&&!departureChecked){
                check(trails().stream().anyMatch(trail->{try{return ((Number)access(trail,"initialOpacity")).floatValue()<.99f;}catch(Exception e){throw new RuntimeException(e);}}),"Early trails did not inherit the rune departure fade");
                shot("resource_departure.png");departureChecked=true;
            }
            if(age>=50&&!mid){checkTrails(false);measure("mid",.5);shot("resource_arrival_mid.png");mid=true;}
            if(age>=92&&!entry){measure("entry",.92);shot("resource_arrival_entry.png");entry=true;}
            if(age>=98&&!late){checkTrails(true);measure("late",.98);shot("resource_arrival_late.png");late=true;}
            if(age>=100&&!arrived){measure("arrived",1);shot("resource_arrival_complete.png");arrived=true;}
            if(age>=125&&!pending){
                check(flights().isEmpty()&&trails().isEmpty(),"Expired cosmetic resources remain active");
                server(3,p->{
                    var level=p.serverLevel();
                    check(level.getBlockState(SOURCE).is(Blocks.BARREL),"Cosmetic packet changed its source block");
                    var stack=((Container)level.getBlockEntity(SOURCE)).getItem(0);
                    check(stack.is(Items.DIAMOND)&&stack.getCount()==17,"Cosmetic packets changed authoritative source contents");
                    for(var target:TARGETS)check(level.getBlockState(target).is(Blocks.GLASS)&&level.getBlockEntity(target)==null,
                            "Cosmetic resource arrival changed a transparent target");
                });
            }
        }else if(phase==3&&!pending){
            mc.options.hideGui=false;mc.setScreen(new TextureSheet());next(4);
        }else if(phase==4&&ticks>15){
            shot("custom_item_textures.png");
            check(!mc.mouseHandler.isMouseGrabbed(),"Resource presentation client captured mouse");
            check(mc.options.getSoundSourceVolume(SoundSource.MASTER)==0,"Resource presentation client enabled audio");
            Files.writeString(OUT.resolve("resource_arrival_metrics.txt"),measurements);
            Files.writeString(OUT.resolve("result.txt"),"PASS: rune resource sprites start invisible at half size, smoothly reach full opacity and size after three ticks, and pass the fade into emitted trails; fluid, energy and Source visual endpoints remain inside transparent target blocks; full mid-flight opacity fades at arrival; three detached resource trails fall and expire; server fixture blocks and contents unchanged; actual ItemRenderer contact sheet captured for "+textureItems().size()+" custom item textures.\n");
            restore();mc.setScreen(null);return true;
        }
        return false;
    }

    private static void checkDeparture()throws Exception{
        for(var flight:flights()){
            var method=WorldVisuals.class.getDeclaredMethod("resourceOpacity",flight.getClass(),Vec3.class,double.class);method.setAccessible(true);
            var sizeMethod=WorldVisuals.class.getDeclaredMethod("resourceSize",flight.getClass(),double.class);sizeMethod.setAccessible(true);
            var packet=(NetworkPackets.Visual)access(flight,"packet");
            for(double age:new double[]{0,1.5,3}){
                Vec3 point=position(flight,age/packet.duration());
                float alpha=((Number)method.invoke(null,flight,point,age)).floatValue();
                check(Math.abs(alpha-(age==0?0:age==1.5?.5:1))<1e-6,"Unexpected departure opacity "+alpha+" at tick "+age);
                float radius=((Number)sizeMethod.invoke(null,flight,age)).floatValue();
                check(Math.abs(radius-(age==0?.08:age==1.5?.12:.16))<1e-6,"Unexpected departure size "+radius+" at tick "+age);
                measurements.append("departure size=").append(radius).append(' ');
                measurements.append("departure slot=").append(packet.slot()).append(" age=").append(age).append(" alpha=").append(alpha).append('\n');
            }
        }
    }

    private static void checkFlightEndpoints()throws Exception{
        check(flights().size()==3,"Three resource flights were not recorded");
        var endpoints=new HashSet<Vec3>();
        for(var flight:flights()){
            var packet=(NetworkPackets.Visual)access(flight,"packet");
            var arrival=access(flight,"arrival");
            check(arrival!=null,"Resource flight has no arrival presentation");
            Vec3 point=position(flight,1),center=packet.to().getCenter();
            check(Math.abs(point.x-center.x)<.5&&Math.abs(point.y-center.y)<.5&&Math.abs(point.z-center.z)<.5,"Resource endpoint escaped the target");
            check(point.distanceTo((Vec3)access(arrival,"endpoint"))<1.0e-6,"Flight failed to finish at its sampled endpoint");
            check(opacity(arrival,point)==0,"Resource remains visible at its endpoint");
            endpoints.add(point.subtract(center));
        }
        check(endpoints.size()==3,"Resource batches reused a single arrival offset");
    }

    private static void measure(String label,double progress)throws Exception{
        for(var flight:flights()){
            var packet=(NetworkPackets.Visual)access(flight,"packet");
            var arrival=access(flight,"arrival");
            Vec3 point=position(flight,progress);float alpha=opacity(arrival,point);
            if(progress==.5)check(alpha>.99f,"A resource began fading before approaching its target");
            if(progress==.98)check(alpha<.5f,"A late resource arrival remains too opaque: "+alpha);
            if(progress==1)check(alpha==0,"Completed resource arrival is still visible");
            measurements.append(label).append(" slot=").append(packet.slot()).append(" progress=").append(progress)
                    .append(" alpha=").append(alpha).append(" position=").append(point).append('\n');
        }
    }

    private static void checkTrails(boolean nearArrival)throws Exception{
        var samples=trails();check(samples.size()>=6&&samples.size()<=512,"Resource trails missing or unbounded");
        var kinds=new HashSet<Integer>();boolean falling=false,fading=false;
        for(var trail:samples){
            var packet=(NetworkPackets.Visual)access(trail,"packet");kinds.add(packet.slot());
            Vec3 origin=(Vec3)access(trail,"origin");
            var method=trail.getClass().getDeclaredMethod("position",double.class);method.setAccessible(true);
            Vec3 point=(Vec3)method.invoke(trail,(double)Minecraft.getInstance().level.getGameTime());
            falling|=point.y<origin.y-.04;
            fading|=((Number)access(trail,"initialOpacity")).floatValue()<.9f;
        }
        check(kinds.containsAll(List.of(-2,-3,-4)),"Not all resource kinds leave a textured trail");
        check(falling,"Resource trail sprites did not fall under gravity");
        if(nearArrival)check(fading,"Arrival trail emission failed to inherit the resource fade");
    }

    private static float opacity(Object arrival,Vec3 point)throws Exception{var method=arrival.getClass().getDeclaredMethod("opacity",Vec3.class);method.setAccessible(true);return (float)method.invoke(arrival,point);}
    private static Object access(Object value,String name)throws Exception{var method=value.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(value);}
    private static Vec3 position(Object flight,double progress)throws Exception{var method=WorldVisuals.class.getDeclaredMethod("position",flight.getClass(),double.class);method.setAccessible(true);return (Vec3)method.invoke(null,flight,progress);}
    private static List<?> flights()throws Exception{return list("flights");}
    private static List<?> trails()throws Exception{return list("resourceTrails");}
    private static List<?> list(String field)throws Exception{var value=WorldVisuals.class.getDeclaredField(field);value.setAccessible(true);return (List<?>)value.get(null);}

    private static List<ItemStack> textureItems(){
        var values=new ArrayList<ItemStack>();
        for(var item:List.of(AstralContent.RANGE_ATTUNEMENT.get(),AstralContent.MOON_ATTUNEMENT.get(),AstralContent.STAR_ATTUNEMENT.get(),
                AstralContent.DIMENSIONAL_ATTUNEMENT.get(),AstralContent.RECIPE_TOME.get(),AstralContent.RESONANCE_GOGGLES.get(),
                AstralContent.ASTRAL_GEM.get(),AstralContent.ASTRAL_GEODE.get().asItem(),AstralContent.BUDDING_ASTRAL.get().asItem(),
                AstralContent.SMALL_ASTRAL_BUD.get().asItem(),AstralContent.MEDIUM_ASTRAL_BUD.get().asItem(),
                AstralContent.LARGE_ASTRAL_BUD.get().asItem(),AstralContent.ASTRAL_CLUSTER.get().asItem()))values.add(new ItemStack(item));
        AstralContent.FIELD_GUIDE.ifPresent(item->values.add(new ItemStack(item.get())));return values;
    }
    private static final class TextureSheet extends Screen {
        TextureSheet(){super(Component.literal("Custom Astral item artwork"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
            g.fill(0,0,width,height,0xff15142b);
            g.drawCenteredString(font,"Custom Astral item textures",width/2,12,0xffe1ddfa);
            var items=textureItems();int columns=4,cellWidth=(width-24)/columns,rowHeight=(height-38)/4;
            for(int i=0;i<items.size();i++){
                int x=12+(i%columns)*cellWidth,y=31+(i/columns)*rowHeight;
                g.fill(x+2,y,x+cellWidth-2,y+rowHeight-4,0xff22213d);
            }
            g.flush();
            for(int i=0;i<items.size();i++){
                int x=12+(i%columns)*cellWidth,y=31+(i/columns)*rowHeight;
                var stack=items.get(i);
                MaterialGeometrySmoke.renderItem(g,stack,x+cellWidth/2f,y+31,53,0,0);
                var lines=font.split(stack.getHoverName(),cellWidth-10);
                for(int line=0;line<Math.min(lines.size(),2);line++)g.drawCenteredString(font,lines.get(line),x+cellWidth/2,y+59+line*9,0xffc6c9e8);
            }
        }
    }

    private static void restore(){if(initialized){AstralClientConfig.resourceArrivalVariance.set(originalVariance);AstralConfig.particleDensity.set(originalDensity);Minecraft.getInstance().options.hideGui=oldGui;initialized=false;}}
    private static void server(int after,Consumer<ServerPlayer> action){
        pending=true;var mc=Minecraft.getInstance();var id=mc.player.getUUID();
        mc.getSingleplayerServer().execute(()->{
            try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));mc.execute(()->{pending=false;next(after);});}
            catch(Throwable error){failure=error.toString();}
        });
    }
    private static void shot(String name)throws Exception{try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(OUT.resolve(name));}}
    private static void next(int value){phase=value;ticks=0;}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private ResourceArrivalClientSmoke(){}
}
