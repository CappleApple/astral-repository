package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.AstralServerConfig;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.cappleapple.astralrepository.platform.PacketDistributor;

/** Personal instance library, pixel editor, independent item layers, and preset behavior. */
public final class WandScreen extends Screen implements GhostIngredientScreen {
    private static final int W=420,H=290,CANVAS=144;
    private static final ResourceLocation PANEL=new ResourceLocation("astral_repository","textures/gui/rune_settings.png");
    private static final ResourceLocation BUTTON=new ResourceLocation("astral_repository","rune_button"),HOVER=new ResourceLocation("astral_repository","rune_button_hovered");
    private final UUID session;
    private final LinkedHashMap<UUID,RunePreset> library=new LinkedHashMap<>();
    private final ArrayDeque<RuneDesign> undo=new ArrayDeque<>();
    private final Set<UUID> seenPack=new HashSet<>();
    private final RuneFilterSearch catalogue=new RuneFilterSearch();
    private final Path folder;
    private UUID current;
    private RunePreset draft;
    private int[] pixels;
    private RuneDesign preview;
    private final List<RuneDesign.Icon> icons=new ArrayList<>();
    private final FilterRules rules=new FilterRules();
    private EditBox name,search,minimum,target,priority;
    private int left,top,listOffset,resultOffset,selectedIcon=-1,brush=0xffaaccff,lastX=-1,lastY=-1;
    private boolean behavior,erase,dirty,dragging,exclude,editing;
    private Button modeButton;
    private RuneColorPicker colorPicker;
    public RuneColorPicker colorPicker(){return colorPicker;}
    private final List<EditBox> transforms=new ArrayList<>();
    private int filterOffset;
    private long saveAt;
    private String error="";
    private List<RuneFilterSearch.Option> results=List.of();
    public WandScreen(UUID session){this(session,null);}
    public WandScreen(UUID session,RunePreset imported){super(Component.literal("Attunement Wand"));this.session=session;var mc=Minecraft.getInstance();folder=mc.gameDirectory.toPath().resolve("astral_repository/runes/"+mc.getUser().getProfileId());readLibrary();for(var preset:RunePresetFiles.pack(mc.level.registryAccess()))acceptPack(preset);for(var preset:library.values())send(WandPackets.Op.IMPORT,preset.id(),preset.save());if(!library.isEmpty())load(library.values().iterator().next());if(imported!=null){if(library.size()<64){library.put(imported.id(),imported);writePreset(imported);send(WandPackets.Op.IMPORT,imported.id(),imported.save());editing=true;load(imported);}else error="Preset library is full";}}
    public static void receive(WandPackets.Page page){var mc=Minecraft.getInstance();if(page.open()){mc.setScreen(new WandScreen(page.session(),page.data().contains("EditPreset")?RunePreset.load(page.data().getCompound("EditPreset"),mc.level.registryAccess()):null));return;}if(!(mc.screen instanceof WandScreen screen)||!screen.session.equals(page.session()))return;
        if(page.data().contains("PackPreset")){try{var p=RunePreset.load(page.data().getCompound("PackPreset"),mc.level.registryAccess());screen.acceptPack(p);}catch(RuntimeException failure){screen.error="Invalid pack preset";}return;}
        if(!page.data().getString("Error").isEmpty())screen.error=page.data().getString("Error");
    }
    private void acceptPack(RunePreset p){if(!seenPack.add(p.id()))return;if(!library.containsKey(p.id())&&library.size()<RuneLibraryData.MAX_PRESETS){library.put(p.id(),p);writePreset(p);send(WandPackets.Op.IMPORT,p.id(),p.save());if(draft==null)load(p);}writeSeen();rebuild();}
    private void readLibrary(){try{Files.createDirectories(folder);Path seen=folder.resolve("pack-presets.txt");if(Files.exists(seen))for(String line:Files.readAllLines(seen))try{seenPack.add(UUID.fromString(line));}catch(IllegalArgumentException ignored){}try(var files=Files.list(folder)){for(var file:files.filter(f->f.toString().endsWith(".json")).sorted().limit(64).toList())try{if(Files.size(file)>196608)continue;var p=RunePresetFiles.decode(Files.readString(file),Minecraft.getInstance().level.registryAccess());library.put(p.id(),p);}catch(Exception invalid){error="Could not load a saved preset";}}}catch(Exception failure){error="Cannot read personal rune library";}}
    private void writeSeen(){try{Files.write(folder.resolve("pack-presets.txt"),seenPack.stream().map(UUID::toString).sorted().toList());}catch(Exception failure){error="Cannot save preset history";}}
    private void writePreset(RunePreset p){try{Path file=folder.resolve(p.id()+".json"),temp=folder.resolve(p.id()+".tmp");Files.writeString(temp,RunePresetFiles.encode(p));try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ignored){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}catch(Exception failure){error="Cannot save personal rune";}}
    private void send(WandPackets.Op op,UUID id,CompoundTag data){PacketDistributor.sendToServer(new WandPackets.Action(session,op,id==null?new UUID(0,0):id,data));}
    private int resolution(){return AstralServerConfig.runeResolution.get();}
    private void load(RunePreset p){flush();draft=p;current=p.id();int size=Math.max(resolution(),p.design().size());pixels=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=p.design().argb(x,y);icons.clear();icons.addAll(p.design().icons());rules.load(p.filter(),Minecraft.getInstance().level.registryAccess());selectedIcon=-1;preview=null;undo.clear();dirty=false;name=null;search=null;rebuild();}
    private int storedSize(){return (int)Math.sqrt(pixels.length);}
    public RunePreset snapshot(){return draft==null?null:new RunePreset(current,name==null?draft.name():name.getValue(),draft.mode(),currentDesign(),rules.save(Minecraft.getInstance().level.registryAccess()),draft.priority(),draft.enabled(),draft.cadence());}
    private RuneDesign currentDesign(){if(preview==null)preview=new RuneDesign(storedSize(),pixels,icons);return preview;}
    private void changed(){preview=null;dirty=true;saveAt=System.currentTimeMillis()+250;error="";}
    public void flush(){if(!dirty||draft==null)return;RunePreset p=snapshot();library.put(current,p);draft=p;writePreset(p);send(WandPackets.Op.SAVE,current,p.save());dirty=false;}
    private void rebuild(){if(minecraft!=null)rebuildWidgets();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){if(dirty&&!dragging&&System.currentTimeMillis()>=saveAt)flush();}
    @Override public void onClose(){flush();if(editing){editing=false;rebuild();return;}super.onClose();}
    public boolean editing(){return editing;}
    public int presetCount(){return library.size();}
    public void newPreset(){editing=true;create(false);}
    private void use(){if(draft!=null){flush();send(WandPackets.Op.SELECT,current,new CompoundTag());minecraft.setScreen(null);}}
    @Override public void removed(){if(colorPicker!=null)colorPicker.onClose();flush();super.removed();}
    @Override protected void init(){left=(width-W)/2;top=(height-H)/2;
        transforms.clear();minimum=target=priority=null;
        if(!editing){
            button(314,260,98,20,"Done",this::onClose);return;
        }
        button(8,34,98,20,"Library",()->{flush();editing=false;rebuild();});
        button(8,60,98,20,"Duplicate",()->create(true));button(8,86,98,20,"Delete preset",this::delete);
        button(116,260,150,20,"Use selected rune",this::use);button(277,260,135,20,"Done",this::onClose);
        if(draft==null)return;
        String nameText=name==null?draft.name():name.getValue();name=edit(120,32,144,18,nameText,v->changed());name.setMaxLength(32);
        modeButton=button(120,54,69,18,draft.mode().title(),()->cycleMode(1));
        button(194,54,70,18,erase?"Eraser":selectedIcon>=0?"Move item":"Paint",()->{if(selectedIcon>=0){selectedIcon=-1;erase=false;}else erase=!erase;rebuild();});
        button(278,32,64,18,behavior?"Artwork":"[Artwork]",()->{flush();behavior=false;search=null;rebuild();});button(346,32,66,18,behavior?"[Rules]":"Rules",()->{flush();behavior=true;search=null;rebuild();});
        String query=search==null?"":search.getValue();search=edit(278,55,134,18,query,v->{resultOffset=0;refresh();});search.setHint(Component.literal("Search items"));search.visible=!behavior;refresh();
        if(behavior){
            button(278,119,65,18,"Clear filter",()->{rules.clearPredicates();changed();rebuild();});button(347,119,65,18,rules.blacklist()?"Blacklist":"Whitelist",()->{rules.toggleBlacklist();changed();rebuild();});
            minimum=edit(278,158,64,18,Long.toString(rules.minimum()),v->{try{rules.setMinimum(Long.parseLong(v));changed();}catch(NumberFormatException ignored){}});
            target=edit(348,158,64,18,rules.target()==Long.MAX_VALUE?"-1":Long.toString(rules.target()),v->{try{rules.setTarget(v.isBlank()?-1:Long.parseLong(v));changed();}catch(NumberFormatException ignored){}});
            minimum.visible=target.visible=draft.mode()!=RuneLayer.Mode.FILTER;
            priority=edit(278,196,64,18,Integer.toString(draft.priority()),v->{try{draft=new RunePreset(current,draft.name(),draft.mode(),draft.design(),draft.filter(),Integer.parseInt(v),draft.enabled(),draft.cadence());changed();}catch(NumberFormatException ignored){}});
            button(348,196,64,18,draft.enabled()?"Enabled":"Disabled",()->{draft=new RunePreset(current,draft.name(),draft.mode(),draft.design(),draft.filter(),draft.priority(),!draft.enabled(),draft.cadence());changed();rebuild();});
            
        }else{
            button(278,9,134,18,"Color",()->{colorPicker=new RuneColorPicker(brush,c->{brush=c;erase=false;selectedIcon=-1;},()->colorPicker=null);colorPicker.init(minecraft,width,height);});
            button(278,119,28,18,"<",()->{if(!icons.isEmpty())selectedIcon=Math.floorMod(selectedIcon-1,icons.size());rebuild();});button(310,119,28,18,">",()->{if(!icons.isEmpty())selectedIcon=(selectedIcon+1)%icons.size();rebuild();});button(342,119,70,18,"Remove item",()->{if(selectedIcon>=0){icons.remove(selectedIcon);selectedIcon=-1;changed();rebuild();}});
            if(selectedIcon>=0&&selectedIcon<icons.size()){var icon=icons.get(selectedIcon);transforms.add(edit(278,158,64,18,Float.toString(icon.x()),v->transform(0,v)));transforms.add(edit(348,158,64,18,Float.toString(icon.y()),v->transform(1,v)));transforms.add(edit(278,196,64,18,Float.toString(icon.scale()),v->transform(2,v)));transforms.add(edit(348,196,64,18,Float.toString(icon.rotation()),v->transform(3,v)));}
            button(278,231,134,18,"Clear visible paint",()->{for(int y=0;y<resolution();y++)for(int x=0;x<resolution();x++)pixels[y*storedSize()+x]=0;changed();});
        }
    }
    private void create(boolean copy){if(library.size()>=64)return;editing=true;flush();RunePreset base=copy&&draft!=null?snapshot():RunePreset.initial(RuneLayer.Mode.PUSH);RunePreset p=new RunePreset(UUID.randomUUID(),copy?base.name()+" copy":"New rune",base.mode(),base.design(),base.filter(),base.priority(),base.enabled(),base.cadence());library.put(p.id(),p);writePreset(p);send(WandPackets.Op.IMPORT,p.id(),p.save());load(p);}
    private void delete(){if(draft==null)return;flush();UUID id=current;library.remove(id);try{Files.deleteIfExists(folder.resolve(id+".json"));}catch(Exception failure){error="Cannot delete saved preset";return;}send(WandPackets.Op.DELETE,id,new CompoundTag());draft=null;current=null;name=null;editing=false;if(!library.isEmpty())load(library.values().iterator().next());else rebuild();}
    private void cycleMode(int step){
        var before=draft.mode();var next=before.cycle(step);boolean defaultArt=currentDesign().key().equals(RuneDesign.initial(before).key());
        draft=new RunePreset(current,draft.name(),next,draft.design(),draft.filter(),draft.priority(),draft.enabled(),draft.cadence());
        if(defaultArt){var art=RuneDesign.initial(next);pixels=art.argbPixels();icons.clear();icons.addAll(art.icons());}
        changed();rebuild();
    }
    private int libraryCell(double x,double y){if(!in(x,y,20,40,384,192))return -1;return listOffset*8+(int)(x-left-20)/48+8*((int)(y-top-40)/48);}
    private void transform(int field,String value){try{float n=Float.parseFloat(value);var i=icons.get(selectedIcon);icons.set(selectedIcon,new RuneDesign.Icon(i.item(),field==0?n:i.x(),field==1?n:i.y(),field==2?n:i.scale(),field==3?n:i.rotation()));changed();}catch(RuntimeException ignored){}}
    @Override public net.minecraft.client.renderer.Rect2i ingredientArea(){return editing&&draft!=null?new net.minecraft.client.renderer.Rect2i(left+278,top+77,134,178):new net.minecraft.client.renderer.Rect2i(0,0,0,0);}
    @Override public boolean acceptItem(ItemStack item){
        if(!editing||draft==null||item.isEmpty())return false;
        if(behavior){var rule=RuneSettingsPackets.parseRule(RuneSettingsPackets.sampleRule(item));rules.add(rule.kind(),rule.id(),false,rule.kind()==FilterRules.Kind.ITEM?item:ItemStack.EMPTY);}
        else {if(icons.size()>=8)return false;icons.add(new RuneDesign.Icon(BuiltInRegistries.ITEM.getKey(item.getItem()),resolution()/2f,resolution()/2f,resolution()/2f,0));selectedIcon=icons.size()-1;}
        changed();rebuild();return true;
    }
    @Override public boolean acceptFluid(net.minecraftforge.fluids.FluidStack fluid){
        if(!editing||draft==null||!behavior||fluid.isEmpty())return false;
        rules.add(FilterRules.Kind.FLUID,BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString(),false,ItemStack.EMPTY);changed();rebuild();return true;
    }
    private void refresh(){
        if(behavior){
            var samples=new ArrayList<RuneFilterSearch.Option>();
            var handler=minecraft.player.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,null).orElse(null);
            if(handler==null)handler=new net.minecraftforge.items.wrapper.PlayerInvWrapper(minecraft.player.getInventory());
            for(int i=0;i<Math.min(handler.getSlots(),8192);i++){var item=handler.getStackInSlot(i);if(!item.isEmpty()){var option=catalogue.option(RuneSettingsPackets.sampleRule(item));if(option!=null&&samples.stream().noneMatch(o->o.rule().equals(option.rule())))samples.add(option);}}
            results=List.copyOf(samples);
        }else results=catalogue.search(search.getValue(),RuneFilterSearch.Category.ITEMS);resultOffset=Math.min(resultOffset,Math.max(0,results.size()-12));}
    private Button button(int x,int y,int w,int h,String text,Runnable action){return addRenderableWidget(new Button(left+x,top+y,w,h,Component.literal(text),b->action.run(),supplier -> supplier.get()){@Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){com.cappleapple.astralrepository.platform.ClientBackport.blitSprite(g,isHovered()?HOVER:BUTTON,getX(),getY(),getWidth(),getHeight());g.drawCenteredString(font,getMessage(),getX()+getWidth()/2,getY()+(getHeight()-8)/2,0xd9defb);}});}
    private EditBox edit(int x,int y,int w,int h,String value,java.util.function.Consumer<String> responder){EditBox e=new AstralEditBox(font,left+x,top+y,w,h,Component.empty());e.setBordered(false);e.setMaxLength(64);e.setValue(value);e.setTextColor(0xd9defb);e.setResponder(responder);return addRenderableWidget(e);}
    private boolean in(double x,double y,int rx,int ry,int w,int h){return x>=left+rx&&y>=top+ry&&x<left+rx+w&&y<top+ry+h;}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(colorPicker!=null){colorPicker.mouseClicked(x,y,button);return true;}
        if(!editing){int i=libraryCell(x,y);if(i>=0){var entries=new ArrayList<>(library.values());if(i==entries.size()&&entries.size()<64)newPreset();else if(i<entries.size()){if(button==1){editing=true;load(entries.get(i));}else if(button==0){load(entries.get(i));use();}}}return i>=0||super.mouseClicked(x,y,button);}
        if(button==1&&modeButton!=null&&modeButton.isMouseOver(x,y)){cycleMode(-1);return true;}
        if(behavior&&in(x,y,278,218,132,38)){int i=filterOffset+(int)(x-left-278)/22+6*((int)(y-top-218)/19);if(i<rules.entries().size()){if(button==1)rules.remove(i);else rules.toggleItemData(i);changed();}return true;}

        if(draft!=null&&in(x,y,120,80,CANVAS,CANVAS)&&(button==0||button==1)){undo.addLast(snapshot().design());while(undo.size()>16)undo.removeFirst();dragging=true;lastX=lastY=-1;paint(x,y,button);return true;}
        if(draft!=null&&in(x,y,120,230,144,18)){int[] colors=colors();brush=colors[Math.min(8,(int)(x-left-120)/16)];erase=false;selectedIcon=-1;rebuild();return true;}
        if(draft!=null&&in(x,y,278,77,132,38)){int i=resultOffset+(int)(x-left-278)/22+6*((int)(y-top-77)/19);if(i<results.size()){var option=results.get(i);if(behavior){var rule=RuneSettingsPackets.parseRule((exclude?"!":"")+option.rule());rules.add(rule.kind(),rule.id(),rule.exclude(),rule.sample());}else if(icons.size()<8){icons.add(new RuneDesign.Icon(BuiltInRegistries.ITEM.getKey(option.icon().getItem()),resolution()/2f,resolution()/2f,resolution()/2f,0));selectedIcon=icons.size()-1;}changed();rebuild();}return true;}
        return super.mouseClicked(x,y,button);
    }
    private void paint(double x,double y,int button){int n=resolution(),px=com.cappleapple.astralrepository.platform.Backport.clamp((int)((x-left-120)*n/CANVAS),0,n-1),py=com.cappleapple.astralrepository.platform.Backport.clamp((int)((y-top-80)*n/CANVAS),0,n-1);if(selectedIcon>=0&&!erase&&button==0){var icon=icons.get(selectedIcon);icons.set(selectedIcon,new RuneDesign.Icon(icon.item(),px+.5f,py+.5f,icon.scale(),icon.rotation()));}else{if(lastX<0){lastX=px;lastY=py;}int steps=Math.max(Math.abs(px-lastX),Math.abs(py-lastY));for(int i=0;i<=steps;i++){int a=steps==0?px:Math.round(lastX+(px-lastX)*i/(float)steps),b=steps==0?py:Math.round(lastY+(py-lastY)*i/(float)steps);pixels[b*storedSize()+a]=(button==1||erase?0:brush);}lastX=px;lastY=py;}changed();}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(colorPicker!=null){if(key==256)colorPicker.onClose();else colorPicker.keyPressed(key,scan,modifiers);return true;}if(editing&&hasControlDown()&&key==org.lwjgl.glfw.GLFW.GLFW_KEY_Z&&!undo.isEmpty()){var art=undo.removeLast();pixels=art.argbPixels();icons.clear();icons.addAll(art.icons());selectedIcon=-1;changed();rebuild();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean charTyped(char c,int modifiers){return colorPicker!=null?colorPicker.charTyped(c,modifiers):super.charTyped(c,modifiers);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(colorPicker!=null){colorPicker.mouseDragged(x,y,button,dx,dy);return true;}if(dragging){paint(x,y,button);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(colorPicker!=null){colorPicker.mouseReleased(x,y,button);return true;}if(dragging){dragging=false;flush();return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double dy){
        if(colorPicker!=null)return true;
        if(!editing){if(in(x,y,20,40,384,192)){listOffset=com.cappleapple.astralrepository.platform.Backport.clamp(listOffset-(int)Math.signum(dy),0,Math.max(0,(library.size()+1+7)/8-4));return true;}return super.mouseScrolled(x,y,dy);}
        if(dy!=0&&modeButton!=null&&modeButton.isMouseOver(x,y)){cycleMode(dy>0?1:-1);return true;}
        if(NumericScroll.adjust(minimum,x,y,dy,0,Long.MAX_VALUE,false)||NumericScroll.adjust(target,x,y,dy,0,Long.MAX_VALUE,true)||NumericScroll.adjust(priority,x,y,dy,-999,999,false))return true;
        for(int i=0;i<transforms.size();i++)if(NumericScroll.adjust(transforms.get(i),x,y,dy,i==3?-360:i==2?1:0,i==3?360:128,false))return true;
        if(behavior&&in(x,y,278,218,132,38)){filterOffset=com.cappleapple.astralrepository.platform.Backport.clamp(filterOffset-(int)Math.signum(dy)*6,0,Math.max(0,(rules.entries().size()+5)/6-2)*6);return true;}
if(in(x,y,8,34,98,192)){listOffset=com.cappleapple.astralrepository.platform.Backport.clamp(listOffset-(int)Math.signum(dy),0,Math.max(0,library.size()-8));return true;}if(in(x,y,278,77,134,38)){resultOffset=com.cappleapple.astralrepository.platform.Backport.clamp(resultOffset-(int)Math.signum(dy)*6,0,Math.max(0,results.size()-12));return true;}if(draft!=null&&selectedIcon>=0&&in(x,y,120,80,144,144)){var icon=icons.get(selectedIcon);transform(hasShiftDown()?2:3,Float.toString((hasShiftDown()?icon.scale():icon.rotation())+(float)Math.signum(dy)*NumericScroll.step()));rebuild();return true;}return super.mouseScrolled(x,y,dy);}
    private static int[] colors(){return new int[]{0xffffffff,0xffaaddff,0xff4488ff,0xff9944ff,0xffff88cc,0xffffbb44,0xff44dd88,0xffff4444,0xff000000};}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);AstralInterfaceRenderer.blit(g,PANEL,left,top,W,H);g.drawString(font,"Rune library",left+9,top+13,0xe1d4ff,false);for(var child:children())if(child instanceof AbstractWidget widget)widget.render(g,mx,my,partial);
        if(!editing){
            var entries=new ArrayList<>(library.values());int count=entries.size()+(entries.size()<64?1:0),hover=libraryCell(mx,my);
            for(int i=listOffset*8;i<Math.min(count,listOffset*8+32);i++){int k=i-listOffset*8,x=left+20+k%8*48,y=top+40+k/8*48;RuneUi.slot(g,x,y,44,i==hover);
                if(i==entries.size())g.drawCenteredString(font,"+",x+22,y+18,0xd9defb);else RuneDesignRenderer.draw(g,entries.get(i).design(),resolution(),x+5,y+5,34);
            }
            if(hover>=0&&hover<entries.size())g.renderTooltip(font,Component.literal(entries.get(hover).name()),mx,my);
            if(!error.isEmpty())g.drawString(font,error,left+10,top+242,0xff8899,false);return;
        }
        if(draft!=null)RuneDesignRenderer.draw(g,currentDesign(),resolution(),left+13,top+128,88);
        if(draft!=null){g.fill(left+119,top+79,left+265,top+225,0xff7777aa);for(int y=0;y<18;y++)for(int x=0;x<18;x++)g.fill(left+120+x*8,top+80+y*8,left+128+x*8,top+88+y*8,(x+y)%2==0?0xff14182e:0xff202440);
            RuneDesignRenderer.draw(g,currentDesign(),resolution(),left+120,top+80,CANVAS);int[] colors=colors();for(int i=0;i<colors.length;i++){g.fill(left+120+i*16,top+231,left+135+i*16,top+247,colors[i]);if(colors[i]==brush&&!erase)g.renderOutline(left+120+i*16,top+231,15,16,0xffffffff);}
            for(int k=0;k<12&&resultOffset+k<results.size();k++){var option=results.get(resultOffset+k);int x=left+279+(k%6)*22,y=top+78+(k/6)*19;if(mx>=x&&my>=y&&mx<x+20&&my<y+18)g.fill(x-1,y-1,x+18,y+18,0x447e9eff);RuneUi.slot(g,x-1,y-1,20,mx>=x&&my>=y&&mx<x+20&&my<y+18);RuneUi.icon(g,option,x+1,y+1);}
            if(behavior)g.drawString(font,"Inventory",left+278,top+60,0xa5b2dd,false);
            if(!behavior||draft.mode()!=RuneLayer.Mode.FILTER)g.drawString(font,behavior?"Reserve":"X / Y (pixels)",left+278,top+145,0xa5b2dd,false);if(behavior&&draft.mode()!=RuneLayer.Mode.FILTER)g.drawString(font,"Stock limit",left+347,top+145,0xa5b2dd,false);
            g.drawString(font,behavior?"Priority":"Size / Rotation",left+278,top+183,0xa5b2dd,false);
            if(!behavior)g.drawString(font,icons.size()+" item layers",left+278,top+219,0xa5b2dd,false);
            if(!behavior)g.fill(left+383,top+10,left+411,top+26,brush);
            if(behavior)RuneUi.dropArea(g,left+278,top+218,132,38);
            if(behavior)for(int k=0;k<12&&filterOffset+k<rules.entries().size();k++){var rule=rules.entries().get(filterOffset+k);var option=catalogue.option(RuneSettingsPackets.ruleCode(rule));int x=left+278+k%6*22,y=top+218+k/6*19;RuneUi.slot(g,x,y,19,mx>=x&&mx<x+19&&my>=y&&my<y+19);if(option!=null)RuneUi.icon(g,option,x+1,y+1);}

        }
        if(!error.isEmpty())g.drawString(font,font.plainSubstrByWidth(error,W-20),left+10,top+H+3,0xff8899,false);
        for(int k=0;k<12&&resultOffset+k<results.size();k++){int x=left+279+(k%6)*22,y=top+78+(k/6)*19;if(mx>=x&&my>=y&&mx<x+20&&my<y+18)g.renderTooltip(font,Component.literal(results.get(resultOffset+k).name()),mx,my);}
        if(colorPicker!=null){g.pose().pushPose();g.pose().translate(0,0,500);colorPicker.render(g,mx,my,partial);g.pose().popPose();}
    }
}
