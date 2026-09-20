package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.network.RuneSettingsPackets;
import com.cappleapple.astralrepository.network.RuneSettingsPackets.Operation;
import java.util.*;
import com.cappleapple.astralrepository.content.RuneCadence;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.cappleapple.astralrepository.platform.PacketDistributor;

/** One ordered editor session; acknowledgements update the model, never the active text widgets. */
public final class RuneSettingsScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<com.cappleapple.astralrepository.menu.RuneSettingsMenu> implements GhostIngredientScreen {
    private static final ResourceLocation PANEL=texture("rune_settings.png");
    private static final ResourceLocation BUTTON=sprite("rune_button"), HOVER=sprite("rune_button_hovered"), DISABLED=sprite("rune_button_disabled"), THUMB=sprite("rune_scroll_thumb");
    private static final int TEXT=0xd9defb, MUTED=0xa5b2dd, ERROR=0xf18caf;
    private static ResourceLocation texture(String name){return new ResourceLocation("astral_repository","textures/gui/"+name);}
    private static ResourceLocation sprite(String name){return new ResourceLocation("astral_repository",name);}
    private record Change(Operation operation, String rule, int index) {}
    private record Values(boolean all, boolean blacklist, boolean enabled, long minimum, long target, int priority) {
        boolean matches(RuneSettingsPackets.Page page) { return all == page.all() && blacklist == page.blacklist() && enabled == page.enabled() && minimum == page.minimum() && target == page.targetAmount() && priority == page.priority(); }
    }
    private RuneSettingsPackets.Page page;
    private final RuneFilterSearch catalogue = new RuneFilterSearch();
    private final ArrayDeque<Change> changes = new ArrayDeque<>();
    private final List<AbstractWidget> settings = new ArrayList<>();
    private EditBox minimum, stock, priority;
    private Button running, blocking, modeButton, unlink, cadenceButton;
    private final List<AbstractWidget> cadenceWidgets=new ArrayList<>();
    private final Map<RuneCadence.Kind,EditBox[]> cadenceFields=new EnumMap<>(RuneCadence.Kind.class);
    private final Set<RuneCadence.Kind> dirtyCadences=EnumSet.noneOf(RuneCadence.Kind.class);
    private boolean cadenceOpen;private long cadenceDebounce;private int cadenceTop,cadenceHeight;
    private boolean all, blacklist, enabled, exclude, inFlight, closeRequested, closed;
    private int left, top, offset;
    private long revision, acknowledged, sentRevision, debounceUntil;
    private Values sentValues;
    private String error = "";

    public RuneSettingsScreen(com.cappleapple.astralrepository.menu.RuneSettingsMenu menu,net.minecraft.world.entity.player.Inventory inventory,Component title) {
        super(menu,inventory,title);imageWidth=230;imageHeight=268;
        page=new RuneSettingsPackets.Page(new UUID(0,0),"Rune","","",List.of(),false,false,true,0,Long.MAX_VALUE,0,false);
        enabled=true;
    }
    public RuneSettingsPackets.Page currentPage() { return page; }
    public String visibleError() { return error; }
    public int ruleScrollOffset() { return offset; }
    public boolean pendingChanges() { return !dirtyCadences.isEmpty() || inFlight || revision > acknowledged || !changes.isEmpty() || closeRequested; }
    public boolean chooseFilter(String rule) {
        var option = catalogue.option(rule);
        if (option == null || closeRequested) return false;
        enqueue(Operation.ADD_RULE, (exclude ? "!" : "") + option.rule(), -1); return true;
    }
    public void update(RuneSettingsPackets.Page value) {
        if(closed)return;
        if(page.session().equals(new UUID(0,0))){
            page=value;blacklist=value.blacklist();enabled=value.enabled();
            minimum.setValue(Long.toString(value.minimum()));stock.setValue(value.targetAmount()==Long.MAX_VALUE?"-1":Long.toString(value.targetAmount()));priority.setValue(Integer.toString(value.priority()));
            acknowledged=revision;refreshControls();return;
        }
        if(!page.session().equals(value.session()))return;
        // A later automatic scalar save must not erase the reason an explicit rule/removal action failed.
        // Only a new user edit/action clears this field; successful acknowledgements leave it intact.
        if (!value.accepted()) error = value.status();
        if (value.closed()) { closed = true; changes.clear(); super.onClose(); return; }
        page = value; inFlight = false;
        if (sentValues != null && sentValues.matches(value)) acknowledged = Math.max(acknowledged, sentRevision);
        // Do not replace field values, selection, cursor, focus, or newer toggle drafts on an ACK.
        offset = Math.min(offset, Math.max(0, (page.entries().size()+1+8)/9-2)*9);
        refreshControls(); pump(false);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected void containerTick() { super.containerTick(); flushCadences(false);pump(false); }
    @Override protected void init() {
        if(cadenceOpen)closeCadence();super.init();left=leftPos;top=topPos;
        settings.clear();
        modeButton=button(settings,8,7,108,20,page.mode().title(),b->enqueue(Operation.SET_MODE,"",1));
        running=button(settings,122,7,100,20,"",b->{enabled=!enabled;changed(true);});
        priority=edit(56,84,56,"Priority",4,priority==null?Integer.toString(page.priority()):priority.getValue());
        blocking=button(settings,118,84,104,20,"",b->{blacklist=!blacklist;changed(true);});
        minimum=edit(8,120,58,"Keep at source",19,minimum==null?Long.toString(page.minimum()):minimum.getValue());
        stock=edit(74,120,58,"Stop at destination",19,stock==null?(page.targetAmount()==Long.MAX_VALUE?"-1":Long.toString(page.targetAmount())):stock.getValue());
        cadenceButton=button(settings,140,120,82,20,"Cadence",b->openCadence());
        minimum.setResponder(v->changed(false));stock.setResponder(v->changed(false));priority.setResponder(v->changed(false));
        button(settings,8,146,44,20,"Done",b->apply());
        unlink=button(settings,56,146,66,20,"Unlink",b->enqueue(Operation.UNLINK,"",-1));
        button(settings,126,146,96,20,"Save Preset",b->{revision++;changes.addLast(new Change(Operation.SAVE_PRESET,"",-1));closeRequested=true;refreshControls();pump(true);});
        refreshControls();
    }
    private EditBox edit(int x,int y,int width,String name,int length,String value){
        EditBox field=new AstralEditBox(font,left+x,top+y,width,20,Component.literal(name));
        field.setBordered(false);field.setTextColor(TEXT);field.setMaxLength(length);field.setValue(value);
        settings.add(field);return addRenderableWidget(field);
    }
    private Button button(List<AbstractWidget> group, int x, int y, int width, int height, String text, Button.OnPress action) {
        Button widget = new Button(left+x,top+y,width,height,Component.literal(text),action,supplier -> supplier.get()) {
            @Override protected void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
                com.cappleapple.astralrepository.platform.ClientBackport.blitSprite(graphics,!active?DISABLED:isHovered()?HOVER:BUTTON,getX(),getY(),getWidth(),getHeight());
                graphics.drawString(font,getMessage(),getX()+(getWidth()-font.width(getMessage()))/2,getY()+(getHeight()-8)/2,active?TEXT:MUTED,false);
            }
        };
        group.add(widget); return addRenderableWidget(widget);
    }
    private void refreshControls(){
        if(running==null)return;
        settings.forEach(w->w.active=!closeRequested&&!cadenceOpen);
        running.setMessage(Component.literal(enabled?"Enabled":"Disabled"));modeButton.setMessage(Component.literal(page.mode().title()));
        blocking.setMessage(Component.literal(blacklist?"Blacklist":"Whitelist"));
        boolean transfer=page.mode()!=com.cappleapple.astralrepository.content.RuneLayer.Mode.FILTER;
        minimum.visible=stock.visible=unlink.visible=cadenceButton.visible=transfer;
    }
    public void openCadence(){
        if(cadenceOpen)return;cadenceOpen=true;setFocused(null);cadenceFields.clear();
        int rows=Integer.bitCount(page.resources()&15);cadenceHeight=rows*42+78;cadenceTop=(height-cadenceHeight)/2;
        int row=0;for(var kind:RuneCadence.Kind.values())if((page.resources()&kind.bit())!=0){
            var rate=page.cadence().overrides().get(kind);if(rate==null)continue;
            int y=cadenceTop+43+row++*42;
            EditBox amount=cadenceField(left+10,y,70,rate.amount()),ticks=cadenceField(left+123,y,58,rate.ticks());
            cadenceFields.put(kind,new EditBox[]{amount,ticks});
            amount.setResponder(v->cadenceChanged(kind));ticks.setResponder(v->cadenceChanged(kind));
        }
        button(cadenceWidgets,10,cadenceTop-top+cadenceHeight-30,134,20,"Server defaults",b->{
            for(var e:cadenceFields.entrySet()){var rate=page.defaults().overrides().get(e.getKey());e.getValue()[0].setValue(Integer.toString(rate.amount()));e.getValue()[1].setValue(Integer.toString(rate.ticks()));}
            dirtyCadences.clear();enqueue(Operation.RESET_CADENCE,"",-1);
        });
        button(cadenceWidgets,152,cadenceTop-top+cadenceHeight-30,68,20,"Done",b->closeCadence());refreshControls();
    }
    private EditBox cadenceField(int x,int y,int width,int value){var field=new AstralEditBox(font,x,y,width,20,Component.literal("Transfer cadence"));field.setMaxLength(10);field.setValue(Integer.toString(value));cadenceWidgets.add(field);return addRenderableWidget(field);}
    private void cadenceChanged(RuneCadence.Kind kind){dirtyCadences.add(kind);cadenceDebounce=Util.getMillis()+200;}
    private void flushCadences(boolean force){
        if(!force&&Util.getMillis()<cadenceDebounce)return;
        for(var kind:List.copyOf(dirtyCadences))try{var fields=cadenceFields.get(kind);int amount=Integer.parseInt(fields[0].getValue()),ticks=Integer.parseInt(fields[1].getValue());fields[0].setTextColor(amount<0||amount>kind.maximum()?ERROR:TEXT);fields[1].setTextColor(ticks<kind.minimumTicks()?ERROR:TEXT);if(amount<0||amount>kind.maximum()||ticks<kind.minimumTicks())continue;dirtyCadences.remove(kind);enqueue(Operation.SET_CADENCE,kind.name()+":"+amount+":"+ticks,-1);}catch(NumberFormatException ignored){}
    }
    private void closeCadence(){flushCadences(true);dirtyCadences.clear();cadenceOpen=false;for(var widget:cadenceWidgets)removeWidget(widget);cadenceWidgets.clear();cadenceFields.clear();setFocused(null);refreshControls();}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(cadenceOpen){if(key==256){closeCadence();return true;}return getFocused()!=null&&getFocused().keyPressed(key,scan,modifiers);}return super.keyPressed(key,scan,modifiers);}
    private void changed(boolean immediate) {
        if (closed || closeRequested) return;
        revision++; debounceUntil = Util.getMillis() + 200; error = ""; refreshControls(); if (immediate) pump(true);
    }
    private void enqueue(Operation operation, String rule, int index) {
        if (closed) return;
        revision++; changes.addLast(new Change(operation, rule, index)); error = ""; refreshControls(); pump(true);
    }
    /** Done and Escape both flush valid pending values before closing the server session. */
    public void apply() { if (closed) return;flushCadences(true); error = ""; closeRequested = true; refreshControls(); pump(true); }
    @Override public void onClose() { if(cadenceOpen)closeCadence();else apply(); }
    private void pump(boolean immediate) {
        if (closed || inFlight || minimum == null || page.session().equals(new UUID(0,0))) return;
        if (changes.isEmpty() && !closeRequested && (revision <= acknowledged || (!immediate && Util.getMillis() < debounceUntil))) return;
        Values values = values(immediate || closeRequested || !changes.isEmpty()); if (values == null) return;
        Change change = changes.isEmpty() ? new Change(closeRequested ? Operation.SAVE : Operation.AUTOSAVE, "", -1) : changes.removeFirst();
        inFlight = true; sentRevision = revision; sentValues = values; refreshControls();
        PacketDistributor.sendToServer(new RuneSettingsPackets.Action(page.session(), change.operation(), values.all(), values.blacklist(), values.enabled(), values.minimum(), values.target(), values.priority(), change.rule(), change.index()));
    }
    private Values values(boolean keepValidFields) {
        long keep = page.minimum(), target = page.targetAmount(); int order = page.priority(); boolean invalid = false;
        try { keep = minimum.getValue().isBlank() ? 0 : Long.parseLong(minimum.getValue()); if (keep < 0) throw new NumberFormatException(); } catch (NumberFormatException failure) { keep = page.minimum(); invalid = true; }
        try { target = stock.getValue().isBlank() ? Long.MAX_VALUE : Long.parseLong(stock.getValue()); if (target == -1) target=Long.MAX_VALUE; else if (target < -1) throw new NumberFormatException(); } catch (NumberFormatException failure) { target = page.targetAmount(); invalid = true; }
        try { order = priority.getValue().isBlank() ? 0 : Integer.parseInt(priority.getValue()); if (order < -999 || order > 999) throw new NumberFormatException(); } catch (NumberFormatException failure) { order = page.priority(); invalid = true; }
        if (invalid) { if (error.isEmpty()) error = "Use -1 for unlimited; other counts start at 0."; if (!keepValidFields) return null; }
        return new Values(false, blacklist, enabled, keep, target, order);
    }
    private int count(){return page.entries().size();}
    private int scrollLimit(){return Math.max(0,(count()+8)/9-2);}
    private int cell(double x,double y){return x>=left+7&&x<left+223&&y>=top+32&&y<top+80?(int)(x-left-7)/24+(int)(y-top-32)/24*9:-1;}
    @Override public net.minecraft.client.renderer.Rect2i ingredientArea(){return new net.minecraft.client.renderer.Rect2i(left+7,top+32,216,48);}
    @Override public boolean acceptItem(net.minecraft.world.item.ItemStack stack){return !stack.isEmpty()&&chooseFilter(RuneSettingsPackets.sampleRule(stack));}
    @Override public boolean acceptFluid(net.minecraftforge.fluids.FluidStack fluid){return !fluid.isEmpty()&&chooseFilter("fluid:"+net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid()));}
    @Override protected void slotClicked(net.minecraft.world.inventory.Slot slot,int id,int button,net.minecraft.world.inventory.ClickType type){
        if(type==net.minecraft.world.inventory.ClickType.QUICK_MOVE){if(slot!=null&&!slot.getItem().isEmpty()&&!closeRequested)enqueue(Operation.ADD_INVENTORY,"",id);return;}
        super.slotClicked(slot,id,button,type);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(cadenceOpen){for(var widget:cadenceWidgets)if(widget.mouseClicked(x,y,button)){setFocused(widget);setDragging(true);break;}return true;}
        if(!closeRequested&&button==1&&modeButton.isMouseOver(x,y)){enqueue(Operation.SET_MODE,"",-1);return true;}
        int cell=cell(x,y);
        if(cell>=0){
            if(!closeRequested&&button==0&&!menu.getCarried().isEmpty()){acceptItem(menu.getCarried());return true;}
            int index=offset+cell;
            if(!closeRequested&&index<page.entries().size()&&!inFlight&&changes.isEmpty()&&(button==0||button==1))enqueue(button==1?Operation.REMOVE_RULE:Operation.TOGGLE_ITEM_DATA,"",index);
            else if(index==page.entries().size()){setFocused(null);if(!menu.getCarried().isEmpty())acceptItem(menu.getCarried());}
            return true;
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double dy){
        if(cadenceOpen){for(var e:cadenceFields.entrySet())if(NumericScroll.adjust(e.getValue()[0],x,y,dy,0,e.getKey().maximum(),false)||NumericScroll.adjust(e.getValue()[1],x,y,dy,e.getKey().minimumTicks(),Integer.MAX_VALUE,false))return true;return true;}
        if(!closeRequested){
            if(NumericScroll.adjust(minimum,x,y,dy,0,Long.MAX_VALUE,false)||NumericScroll.adjust(stock,x,y,dy,0,Long.MAX_VALUE,true)||NumericScroll.adjust(priority,x,y,dy,-999,999,false))return true;
            if(dy!=0&&modeButton.isMouseOver(x,y)){enqueue(Operation.SET_MODE,"",dy>0?1:-1);return true;}
            if(dy!=0&&cell(x,y)>=0){offset=com.cappleapple.astralrepository.platform.Backport.clamp(offset/9+(dy>0?-1:1),0,scrollLimit())*9;return true;}
        }
        return super.mouseScrolled(x,y,dy);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        AstralInterfaceRenderer.blit(g,PANEL,left,top,imageWidth,imageHeight);
        RuneUi.dropArea(g,left+7,top+32,216,48);
        int hover=cell(mx,my);
        for(int k=0;k<18&&offset+k<count();k++){
            int x=left+7+k%9*24,y=top+32+k/9*24;RuneUi.slot(g,x,y,22,k==hover);
            int index=offset+k;
            {
                var option=index<page.rules().size()?catalogue.option(page.rules().get(index)):null;
                if(option!=null)RuneUi.icon(g,option,x+3,y+3);
                if(page.entries().get(index).contains("(exact data)"))g.drawString(font,"=",x+14,y+13,TEXT,false);
            }
        }
        if(scrollLimit()>0)com.cappleapple.astralrepository.platform.ClientBackport.blitSprite(g,THUMB,left+224,top+32+offset/9*32/scrollLimit(),3,16);
        if(minimum.visible){g.drawString(font,"Keep",left+8,top+110,MUTED,false);g.drawString(font,"Limit",left+74,top+110,MUTED,false);}
        g.drawString(font,"Priority",left+8,top+90,MUTED,false);
        g.drawString(font,playerInventoryTitle,left+34,top+173,MUTED,false);
        for(var slot:menu.slots)RuneUi.slot(g,left+slot.x-1,top+slot.y-1,18,isHovering(slot.x,slot.y,16,16,mx,my));
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    @Override public int getSlotColor(int slot){return 0;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        super.render(g,cadenceOpen?-1:mx,cadenceOpen?-1:my,partial);
        if(cadenceOpen){
            g.pose().pushPose();g.pose().translate(0,0,500);g.fill(0,0,width,height,0x99000000);
            AstralInterfaceRenderer.blit(g,PANEL,left,cadenceTop,230,cadenceHeight);
            g.drawCenteredString(font,"Cadence",left+115,cadenceTop+10,TEXT);
            int row=0;for(var kind:cadenceFields.keySet()){int y=cadenceTop+31+row++*42;g.drawString(font,kind.title,left+10,y,MUTED,false);g.drawString(font,"every",left+88,y+16,MUTED,false);g.drawString(font,"ticks",left+187,y+16,MUTED,false);}
            for(var w:cadenceWidgets)w.render(g,mx,my,partial);g.pose().popPose();return;
        }
        renderTooltip(g,mx,my);
        if(!error.isEmpty())g.drawString(font,font.plainSubstrByWidth(error,230),left,top+imageHeight+3,ERROR,false);
        int hovered=cell(mx,my);
        if(hovered>=0&&offset+hovered<page.entries().size())g.renderTooltip(font,Component.literal(page.entries().get(offset+hovered)),mx,my);
    }
}
