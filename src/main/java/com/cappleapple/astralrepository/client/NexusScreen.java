package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.menu.NexusViewRequests;
import com.cappleapple.astralrepository.network.NetworkPackets;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** A scrollable view of network entries; real inventory slots retain vanilla click/drag handling. */
public final class NexusScreen extends AbstractContainerScreen<NexusMenu> {
    private static final Identifier BACKGROUND = texture("nexus.png"), CONTROLS = texture("nexus_controls.png");
    private static final Identifier WIDGETS = texture("nexus_widgets.png"), DYNAMIC = texture("nexus_dynamic.png");
    private static final int COLUMNS = 9, ROWS = 6, GRID_X = 10, GRID_Y = 49, JOB_Y = 181;
    private EditBox search; private FilteredEditBox quantity;
    private CraftMissingScreen missingDetails;
    public void closeMissingDetails(){missingDetails=null;}
    private Button make, clearGrid;
    private ItemStack selected = ItemStack.EMPTY;
    private int row, jobOffset, searchCooldown, storageClickButton = -1;
    private boolean draggingStorage, draggingJobs;
    private double storageDragOffset, jobDragOffset;
    private List<NetworkPackets.Job> lastJobs = List.of(), visibleJobs = List.of();
    private String lastSearch = "";
    private final NexusViewRequests viewRequests = new NexusViewRequests();

    public NexusScreen(NexusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 318, 266);
        inventoryLabelX = 8; inventoryLabelY = 168; titleLabelX = 10; titleLabelY = 9;
    }
    private static Identifier texture(String name) { return Identifier.fromNamespaceAndPath("astral_repository", "textures/gui/" + name); }
    @Override protected void init() {
        super.init();
        search = new EditBox(font, leftPos + 14, topPos + 32, 166, 12, Component.literal("Search storage"));
        search.setBordered(false); search.setMaxLength(128); search.setHint(Component.literal("Search"));
        search.setTextColor(GuiText.opaque(0xFFFFFF)); search.setCanLoseFocus(true); search.setValue(lastSearch);
        search.setResponder(value -> { lastSearch = value; searchCooldown = 3; row = 0; });
        addRenderableWidget(search);
        quantity = new FilteredEditBox(font, leftPos + 236, topPos + 139, 34, 12, Component.literal("Craft quantity"));
        quantity.setBordered(false); quantity.setMaxLength(4); quantity.setFilter(s -> s.isEmpty() || s.matches("[0-9]{1,4}"));
        quantity.setValue("1"); addRenderableWidget(quantity);
        make = addRenderableWidget(new Button(leftPos + 281, topPos + 133, 24, 20, Component.literal("Craft"), b -> requestCraft(), supplier -> supplier.get()) {
            @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
                sprite(g, getX(), getY(), active ? isHovered() ? 24 : 0 : 48, 20, 24, 20);
                glyph(g, getX() + 7, getY() + 5, 0);
            }
        });
        clearGrid = addRenderableWidget(new Button(leftPos + 286, topPos + 30, 16, 16, Component.literal("Clear crafting grid"), b -> send(NetworkPackets.CLEAR_GRID, ItemStack.EMPTY, 0), supplier -> supplier.get()) {
            @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
                sprite(g, getX(), getY(), active ? isHovered() ? 92 : 76 : 108, 20, 16, 16);
                sprite(g, getX() + 2, getY() + 2, 0, 96, 12, 12);
            }
        });
        setFocused(null); search.setFocused(false); quantity.setFocused(false); updateControls(); query();
    }
    @Override protected void containerTick() {
        super.containerTick();
        if (searchCooldown > 0 && --searchCooldown == 0) query();
        updateControls();
    }
    private void query() { searchCooldown = 0; send(NetworkPackets.SEARCH, ItemStack.EMPTY, 0); }
    private void send(int action, ItemStack stack, int amount) {
        long request = action == NetworkPackets.SEARCH ? viewRequests.next() : viewRequests.latest();
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new NetworkPackets.Action(menu.containerId, action, stack, amount, row, search.getValue(), request));
    }
    private int requestedAmount() {
        try { int amount = Integer.parseInt(quantity.getValue()); return amount >= 1 && amount <= 4096 ? amount : 0; }
        catch (NumberFormatException ignored) { return 0; }
    }
    private void requestCraft() {
        int amount = requestedAmount();
        if (!selected.isEmpty() && amount > 0) send(NetworkPackets.CRAFT, selected, amount);
    }
    private void updateControls() {
        if (make == null) return;
        make.active = !selected.isEmpty() && requestedAmount() > 0;
        if (clearGrid != null) clearGrid.active = !menu.grid.isEmpty();
        quantity.setTextColor(GuiText.opaque(requestedAmount() > 0 ? 0xFFFFFF : 0xFF9999));
        if (lastJobs != menu.jobs) {
            lastJobs = menu.jobs;
            visibleJobs = menu.jobs.stream().filter(job -> !job.state().terminal() && !job.target().isEmpty()).toList();
        }
        jobOffset = Math.clamp(jobOffset, 0, Math.max(0, visibleJobs.size() - 3));
    }
    public void update(NetworkPackets.Page packet) {
        if (packet.menu() != menu.containerId) return;
        menu.entries = packet.entries(); menu.totalEntries = packet.total(); menu.scrollRow = packet.row();
        menu.error = packet.error(); menu.jobs = packet.jobs();
        // Intermediate pages still establish delta baselines, but cannot undo newer input.
        row = viewRequests.resolveRow(row, packet.row(), packet.request(), searchCooldown > 0);
        if (quantity != null) updateControls();
    }
    public int scrollRow() { return row; }
    public int maxScrollRow() { return Math.max(0, (int)((menu.totalEntries + (long)COLUMNS - 1) / COLUMNS) - ROWS); }
    public int hoveredEntry(double x, double y) { return entryAt(x, y); }
    private boolean within(double x, double y, int rx, int ry, int width, int height) {
        return x >= leftPos + rx && x < leftPos + rx + width && y >= topPos + ry && y < topPos + ry + height;
    }
    private int entryAt(double x, double y) {
        int rx = (int)Math.floor(x - leftPos - GRID_X), ry = (int)Math.floor(y - topPos - GRID_Y);
        if (rx < 0 || ry < 0 || rx >= 180 || ry >= 108 || rx % 20 >= 18) return -1;
        return ry / 18 * COLUMNS + rx / 20;
    }
    private void scrollTo(int value) {
        value = Math.clamp(value, 0, maxScrollRow());
        if (value != row) { row = value; query(); }
    }
    private static int thumbHeight(int trackHeight, int visible, int total) {
        return Math.clamp((int)Math.round(trackHeight * (double)visible / Math.max(visible, total)), 16, trackHeight);
    }
    public int storageThumbHeight() { return thumbHeight(108, ROWS, (int)((menu.totalEntries + (long)COLUMNS - 1) / COLUMNS)); }
    /** Thumb top relative to this panel, including the storage grid's vertical offset. */
    public int storageThumbTop() {
        int maximum = maxScrollRow();
        return GRID_Y + (maximum == 0 ? 0 : (int)Math.round((108 - storageThumbHeight()) * (double)Math.clamp(row, 0, maximum) / maximum));
    }
    public int storageRowAt(double mouseY, double grabOffset) {
        int travel = 108 - storageThumbHeight(), maximum = maxScrollRow();
        return travel <= 0 ? 0 : Math.clamp((int)Math.round((mouseY - topPos - GRID_Y - grabOffset) / travel * maximum), 0, maximum);
    }
    private void dragStorage(double y) { if (maxScrollRow() > 0) scrollTo(storageRowAt(y, storageDragOffset)); }
    private int jobThumbHeight() { return thumbHeight(70, 3, visibleJobs.size()); }
    private int jobThumbTop() {
        int maximum = Math.max(0, visibleJobs.size() - 3);
        return JOB_Y + (maximum == 0 ? 0 : (int)Math.round((70 - jobThumbHeight()) * (double)jobOffset / maximum));
    }
    private void dragJobs(double y) {
        int travel = 70 - jobThumbHeight(), maximum = Math.max(0, visibleJobs.size() - 3);
        jobOffset = travel <= 0 ? 0 : Math.clamp((int)Math.round((y - topPos - JOB_Y - jobDragOffset) / travel * maximum), 0, maximum);
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick){double mouseX=event.x(), mouseY=event.y(); int button=event.button();
        if(missingDetails!=null){closeMissingDetails();return true;}
        if(button==0)for(int i=0;i<3&&jobOffset+i<visibleJobs.size();i++){var job=visibleJobs.get(jobOffset+i);if(!job.missing().isEmpty()&&within(mouseX,mouseY,207,JOB_Y+i*24,82,22)){missingDetails=new CraftMissingScreen(this,menu,job.id());missingDetails.init(width,height);return true;}}

        boolean inSearch = search.isMouseOver(mouseX, mouseY);
        if (!inSearch) { search.setFocused(false); if (getFocused() == search) setFocused(null); }
        if (!quantity.isMouseOver(mouseX, mouseY)) { quantity.setFocused(false); if (getFocused() == quantity) setFocused(null); }
        if (inSearch) {
            setFocused(search); search.setFocused(true);
            if (button == 1) { search.setValue(""); return true; }
            return search.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX,mouseY,new net.minecraft.client.input.MouseButtonInfo(button,AstralInput.modifiers())), false);
        }
        if (button == 0 && maxScrollRow() > 0 && within(mouseX, mouseY, 189, GRID_Y, 11, 108)) {
            int thumbTop = topPos + storageThumbTop();
            boolean grabbedThumb = mouseY >= thumbTop && mouseY < thumbTop + storageThumbHeight();
            storageDragOffset = grabbedThumb
                    ? mouseY - topPos - GRID_Y - (108 - storageThumbHeight()) * (double)Math.clamp(row, 0, maxScrollRow()) / maxScrollRow()
                    : storageThumbHeight() / 2.0;
            draggingStorage = true;
            if (!grabbedThumb) dragStorage(mouseY);
            return true;
        }
        if (button == 0 && visibleJobs.size() > 3 && within(mouseX, mouseY, 305, JOB_Y, 10, 70)) {
            int thumbTop = topPos + jobThumbTop();
            boolean grabbedThumb = mouseY >= thumbTop && mouseY < thumbTop + jobThumbHeight();
            jobDragOffset = grabbedThumb
                    ? mouseY - topPos - JOB_Y - (70 - jobThumbHeight()) * (double)jobOffset / (visibleJobs.size() - 3)
                    : jobThumbHeight() / 2.0;
            draggingJobs = true;
            if (!grabbedThumb) dragJobs(mouseY);
            return true;
        }
        for (int i = 0; i < 3 && jobOffset + i < visibleJobs.size(); i++) {
            var job = visibleJobs.get(jobOffset + i);
            if (button == 0 && within(mouseX, mouseY, 291, JOB_Y + i * 24 + 2, 12, 12)) {
                storageClickButton = button;
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new NetworkPackets.CancelJob(menu.containerId, job.id())); return true;
            }
        }
        int index = entryAt(mouseX, mouseY);
        if (index >= 0 && button >= 0 && button <= 2) {
            storageClickButton = button;
            var entry = index < menu.entries.size() ? menu.entries.get(index) : null;
            if (entry != null && entry.craftable()) { selected = entry.stack().copyWithCount(1); updateControls(); }
            if (button == 2) {
                if (entry != null && entry.craftable()) { setFocused(quantity); quantity.setFocused(true); quantity.setHighlightPos(0); quantity.setCursorPosition(quantity.getValue().length()); }
                return true;
            }
            if (!menu.getCarried().isEmpty()) send(NetworkPackets.DEPOSIT, ItemStack.EMPTY, button == 1 ? 1 : 0);
            else if (entry != null) {
                // A stack craft takes precedence over withdrawing existing stock.
                if (button == 1 && AstralInput.hasShiftDown() && entry.craftable()) send(NetworkPackets.CRAFT_STACK, entry.stack(), 0);
                else if (entry.count() > 0) send(AstralInput.hasShiftDown() ? NetworkPackets.QUICK_WITHDRAW : NetworkPackets.PICKUP, entry.stack(), button == 1 ? 1 : 0);
                else if (entry.craftable()) send(NetworkPackets.CRAFT, entry.stack(), 1);
            }
            return true;
        }
        return super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX,mouseY,new net.minecraft.client.input.MouseButtonInfo(button,AstralInput.modifiers())), false);
    }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event){double x=event.x(), y=event.y(); int button=event.button();
        if (button == 0 && (draggingStorage || draggingJobs)) { draggingStorage = draggingJobs = false; return true; }
        if (button == storageClickButton) { storageClickButton = -1; return true; }
        return super.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(x,y,new net.minecraft.client.input.MouseButtonInfo(button,AstralInput.modifiers())));
    }
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy){double x=event.x(), y=event.y(); int button=event.button();
        if (draggingStorage) { dragStorage(y); return true; }
        if (draggingJobs) { dragJobs(y); return true; }
        return super.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(x,y,new net.minecraft.client.input.MouseButtonInfo(button,AstralInput.modifiers())), dx, dy);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if(missingDetails!=null)return missingDetails.mouseScrolled(x,y,dx,dy);
        if (dy == 0) return super.mouseScrolled(x, y, dx, dy);
        if (NumericScroll.adjust(quantity,x,y,dy,1,4096,false)) { updateControls(); return true; }
        if (within(x, y, GRID_X, GRID_Y, 190, 108)) { scrollTo(row + (dy < 0 ? 1 : -1)); return true; }
        if (visibleJobs.size() > 3 && within(x, y, 207, JOB_Y, 108, 70)) { jobOffset = Math.clamp(jobOffset + (dy < 0 ? 1 : -1), 0, visibleJobs.size() - 3); return true; }
        return super.mouseScrolled(x, y, dx, dy);
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){int key=event.key(), scan=event.scancode(), modifiers=event.modifiers();
        if(missingDetails!=null){if(key==com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE)closeMissingDetails();return true;}
        if (search.isFocused() && key != 256) return search.keyPressed(new net.minecraft.client.input.KeyEvent(key,scan,modifiers));
        if (quantity.isFocused() && key != 256) {
            if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || key == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) { requestCraft(); return true; }
            return quantity.keyPressed(new net.minecraft.client.input.KeyEvent(key,scan,modifiers));
        }
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_HOME) { scrollTo(0); return true; }
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_END) { scrollTo(maxScrollRow()); return true; }
        return super.keyPressed(new net.minecraft.client.input.KeyEvent(key,scan,modifiers));
    }
    @Override public boolean charTyped(net.minecraft.client.input.CharacterEvent event){int ch=event.codepoint(), modifiers=AstralInput.modifiers();
        if (search.isFocused()) return search.charTyped(new net.minecraft.client.input.CharacterEvent(ch));
        if (quantity.isFocused()) return quantity.charTyped(new net.minecraft.client.input.CharacterEvent(ch));
        return super.charTyped(new net.minecraft.client.input.CharacterEvent(ch));
    }
    private static void sprite(GuiGraphicsExtractor g, int x, int y, int u, int v, int w, int h) { g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,WIDGETS, x, y, u, v, w, h, 128, 128); }
    private static void translucentSprite(GuiGraphicsExtractor g, int x, int y, int u, int v, int width, int height) {
        // The atlas alpha must blend with the slot, including for the selection outline.
        
        sprite(g, x, y, u, v, width, height);
        
    }
    private static void glyph(GuiGraphicsExtractor g, int x, int y, int glyph) { sprite(g, x, y, glyph * 16, 48, 12, 10); }
    private static void dynamic(GuiGraphicsExtractor g, int x, int y, int u, int v, int w, int h) { g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,DYNAMIC, x, y, u, v, w, h, 128, 128); }
    private static void scrollbar(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        // Keep the two-pixel caps crisp; tile only the uniform middle of the textured thumb.
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,WIDGETS,x,y,38,0,width,2,7,2,128,128);
        for (int offset = 2; offset < height - 2; offset += 12) {
            int section = Math.min(12, height - 2 - offset);
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,WIDGETS,x,y+offset,38,2,width,section,7,section,128,128);
        }
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,WIDGETS,x,y+height-2,38,14,width,2,7,2,128,128);
    }
    private void renderEntries(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int hover = entryAt(mouseX, mouseY);
        for (int i = 0; i < COLUMNS * ROWS; i++) {
            int x = leftPos + GRID_X + i % COLUMNS * 20, y = topPos + GRID_Y + i / COLUMNS * 18;
            if (i == hover) {
                translucentSprite(g, x + 1, y + 1, 0, 0, 16, 16);
            }
            if (i < menu.entries.size()) {
                var entry = menu.entries.get(i);
                if (!selected.isEmpty() && ItemStack.isSameItemSameComponents(selected, entry.stack())) translucentSprite(g, x, y, 18, 0, 18, 18);
                g.item(entry.stack(), x + 1, y + 1);
                g.pose().pushMatrix(); g.nextStratum();
                if (entry.craftable()) sprite(g, x + 10, y + 1, 112, 72, 8, 8);
                if (entry.count() > 0) {
                    String count = shortCount(entry.count());
                    GuiText.text(g,font, count, x + 18 - font.width(count), y + 10, 0xFFFFFF, true);
                }
                g.pose().popMatrix();
            }
        }
        if (maxScrollRow() > 0) {
            dynamic(g, leftPos + 191, topPos + GRID_Y, 100, 0, 7, 108);
            scrollbar(g, leftPos + 191, topPos + storageThumbTop(), 7, storageThumbHeight());
        }
    }
    private void renderJobs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (visibleJobs.isEmpty()) return;
        glyph(g, leftPos + 207, topPos + 168, 0);
        for (int i = 0; i < 3 && jobOffset + i < visibleJobs.size(); i++) {
            var job = visibleJobs.get(jobOffset + i); int x = leftPos + 207, y = topPos + JOB_Y + i * 24;
            dynamic(g, x, y, 0, 0, 97, 22);
            g.item(job.target(), x + 3, y + 3);
            if(job.state()==NetworkPackets.JobState.CALCULATING){
                g.pose().pushMatrix();g.pose().translate(x+23,y+7);g.pose().scale(.8f,.8f);GuiText.text(g,font,"Calculating",0,0,0xB9B7ED,false);g.pose().popMatrix();
            }else if(!job.missing().isEmpty()){
                for(int m=0;m<3&&m<job.missing().size();m++){var missing=job.missing().get(m);g.item(missing.icon(),x+23+m*19,y+2);g.itemDecorations(font,missing.icon(),x+23+m*19,y+2,shortCount(missing.count()));}
            }else{
            int icon = switch (job.state()) { case EXTERNAL -> 6; case RUNNING -> 5; default -> 4; };
            glyph(g, x + 23, y + 3, icon);
            String amount = shortCount(job.count());
            GuiText.text(g,font, amount, x + 80 - font.width(amount), y + 4, 0xD9DEFB, false);
            sprite(g, x + 24, y + 16, 0, 64, 70, 4);
            int progress = job.total() == 0 ? 0 : Math.clamp((int)(70L * job.completed() / job.total()), 0, 70);
            if (progress > 0) sprite(g, x + 24, y + 16, 0, 70, progress, 4);
            }
            if (within(mouseX, mouseY, 291, JOB_Y + i * 24 + 2, 12, 12)) {
                translucentSprite(g, x + 84, y + 2, 0, 0, 12, 12);
            }
            glyph(g, x + 85, y + 3, 1);
        }
        if (visibleJobs.size() > 3) {
            dynamic(g, leftPos + 307, topPos + JOB_Y, 110, 0, 6, 70);
            scrollbar(g, leftPos + 307, topPos + jobThumbTop(), 6, jobThumbHeight());
        }
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partial) {
        AstralInterfaceRenderer.blit(g, BACKGROUND, leftPos, topPos, imageWidth, imageHeight);
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,CONTROLS, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        renderEntries(g, mouseX, mouseY); renderJobs(g, mouseX, mouseY);
        for (Slot slot : menu.slots) {
            if (slot.isActive() && slot.isHighlightable() && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                translucentSprite(g, leftPos + slot.x, topPos + slot.y, 0, 0, 16, 16);
            }
        }
        g.item(new ItemStack(Items.CRAFTING_TABLE), leftPos + 207, topPos + 29);
        if (!selected.isEmpty()) g.item(selected, leftPos + 208, topPos + 134);
        glyph(g, leftPos + 266, topPos + 77, 5);
        if (!menu.error.isEmpty()) glyph(g, leftPos + 290, topPos + 9, 2);
    }
    private static String shortCount(long n) {
        if (n >= 1_000_000_000) return n / 1_000_000_000 + "G";
        if (n >= 1_000_000) return n / 1_000_000 + "M";
        if (n >= 10_000) return n / 1_000 + "k";
        return Long.toString(n);
    }
    
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        GuiText.text(g,font, title, titleLabelX, titleLabelY, 0xD9DEFB, false);
        GuiText.text(g,font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xD9DEFB, false);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        updateControls(); super.extractRenderState(g, mouseX, mouseY, partial); if(missingDetails!=null){g.pose().pushMatrix();g.nextStratum();missingDetails.extractRenderState(g,mouseX,mouseY,partial);g.pose().popMatrix();return;} extractTooltip(g, mouseX, mouseY);
        if (within(mouseX, mouseY, 286, 30, 16, 16)) g.setTooltipForNextFrame(font, Component.literal("Clear crafting grid"), mouseX, mouseY);
        int index = entryAt(mouseX, mouseY);
        if (index >= 0 && index < menu.entries.size() && menu.getCarried().isEmpty()) {
            var entry = menu.entries.get(index);
            List<Component> tooltip = new ArrayList<>(getTooltipFromItem(minecraft, entry.stack()));
            if (entry.count() > 1) tooltip.add(Component.literal(Long.toString(entry.count())));
            g.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
        if (!menu.error.isEmpty() && within(mouseX, mouseY, 287, 6, 19, 15)) g.setComponentTooltipForNextFrame(font, List.of(Component.literal(menu.error)), mouseX, mouseY);
        if (!selected.isEmpty() && within(mouseX, mouseY, 207, 133, 18, 18)) g.setTooltipForNextFrame(font, selected, mouseX, mouseY);
        for (int i = 0; i < 3 && jobOffset + i < visibleJobs.size(); i++) {
            var job = visibleJobs.get(jobOffset + i);
            if (within(mouseX, mouseY, 207, JOB_Y + i * 24, 97, 22)) {
                if (within(mouseX, mouseY, 291, JOB_Y + i * 24 + 2, 12, 12)) g.setTooltipForNextFrame(font, Component.translatable("gui.cancel"), mouseX, mouseY);
                else {
                    List<Component> tooltip = new ArrayList<>(); tooltip.add(job.target().getHoverName());
                    tooltip.add(Component.literal(job.state()==NetworkPackets.JobState.CALCULATING?"Calculating":job.state()==NetworkPackets.JobState.MISSING?"Missing ingredients":job.completed() + " / " + job.total()));
                    if(!job.detail().isBlank()&&job.missing().isEmpty()&&job.state()!=NetworkPackets.JobState.CALCULATING)tooltip.add(Component.literal(job.detail()));
                    for(var missing:job.missing())tooltip.add(Component.literal(missing.count()+" x "+(missing.tag().isEmpty()?missing.icon().getHoverName().getString():"#"+missing.tag())));
                    g.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                }
            }
        }
    }
}
