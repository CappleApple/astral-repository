package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.menu.RecipeTomeMenu;
import com.cappleapple.astralrepository.network.RecipeTomePackets;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Inventory and recipe-viewer samples select an output; the server resolves every recipe. */
public final class RecipeTomeScreen extends AbstractContainerScreen<RecipeTomeMenu> implements GhostIngredientScreen {
    private static final int INK=0x354842;
    private List<RecipeTomeMenu.Entry> entries=List.of();
    private RecipeTomeMenu.Entry selection;
    private ItemStack chosen=ItemStack.EMPTY;
    private String outputId="",status="";
    private int page,total;
    private Button previous,next,inscribe;
    public RecipeTomeScreen(RecipeTomeMenu menu,Inventory inventory,Component title){super(menu, inventory, title, 192, 306);}
    @Override protected void init(){
        super.init();
        previous=addRenderableWidget(new PageButton(leftPos+36,topPos+153,false,b->request(page-1),true));
        next=addRenderableWidget(new PageButton(leftPos+126,topPos+153,true,b->request(page+1),true));
        inscribe=addRenderableWidget(Button.builder(Component.literal("Inscribe"),b->inscribeSelection()).bounds(leftPos+14,topPos+184,94,20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(leftPos+112,topPos+184,66,20).build());
        controls();
    }
    public List<RecipeTomeMenu.Entry> visibleRecipes(){return entries;}
    public boolean selectRecipe(Identifier id){selection=entries.stream().filter(e->e.recipe().equals(id)).findFirst().orElse(null);controls();return selection!=null;}
    public void inscribeSelection(){if(selection!=null)net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new RecipeTomePackets.Action(menu.containerId,1,"",page,selection.recipe().toString()));}
    public void update(RecipeTomePackets.Page packet){
        if(packet.menu()!=menu.containerId)return;
        if(chosen.isEmpty()&&!packet.taught().isEmpty())acceptItem(packet.taught());
        entries=packet.entries();selection=entries.isEmpty()?null:entries.getFirst();page=packet.page();total=packet.total();status=packet.status();controls();
    }
    private void controls(){if(previous==null)return;previous.visible=page>0;next.visible=page+1<total;inscribe.active=selection!=null;}
    private void request(int requested){net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new RecipeTomePackets.Action(menu.containerId,0,"="+outputId,requested,""));}
    @Override public Rect2i ingredientArea(){return new Rect2i(leftPos+32,topPos+28,120,120);}
    @Override public boolean acceptItem(ItemStack stack){
        if(stack.isEmpty())return false;chosen=stack.copyWithCount(1);outputId=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();selection=null;controls();
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new RecipeTomePackets.Action(menu.containerId,2,outputId,0,""));return true;
    }
    @Override protected void slotClicked(Slot slot,int id,int button,ContainerInput type){if(type==ContainerInput.QUICK_MOVE){if(slot!=null)acceptItem(slot.getItem());return;}super.slotClicked(slot,id,button,type);}
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick){double x=event.x(), y=event.y(); int button=event.button();
        if(ingredientArea().contains((int)x,(int)y)&&!menu.getCarried().isEmpty())return acceptItem(menu.getCarried());
        boolean handled=super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x,y,new net.minecraft.client.input.MouseButtonInfo(button,AstralInput.modifiers())), false);if(handled&&getFocused() instanceof Button)setFocused(null);return handled;
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int x,int y){}
    
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float partial){
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,BookViewScreen.BOOK_LOCATION,leftPos,topPos,0,0,192,192,256,256,0xFFF0FFFC);
        GuiText.text(g,font,"Recipe Tome",leftPos+36,topPos+16,INK,false);
        ItemStack output=selection==null?chosen:selection.output();
        if(output.isEmpty())GuiText.centeredText(g,font,"+",leftPos+92,topPos+38,INK);else g.item(output,leftPos+84,topPos+34);
        if(selection!=null){
            var lines=font.split(selection.output().getHoverName(),114);for(int i=0;i<Math.min(2,lines.size());i++)GuiText.text(g,font,lines.get(i),leftPos+36,topPos+60+i*9,INK,false);
            for(int i=0;i<selection.ingredients().size();i++)g.item(selection.ingredients().get(i),leftPos+66+i%3*18,topPos+96+i/3*18);
        }else if(!chosen.isEmpty())g.textWithWordWrap(font,Component.literal("No recipe"),leftPos+36,topPos+68,114,0xff000000|INK);
        if(total>1){String number=(page+1)+" / "+total;GuiText.text(g,font,number,leftPos+92-font.width(number)/2,topPos+155,INK,false);}
        if(status.equals("Inscribed."))GuiText.centeredText(g,font,"Inscribed",leftPos+92,topPos+82,INK);
        AstralInterfaceRenderer.blit(g,Identifier.fromNamespaceAndPath("astral_repository","textures/gui/rune_settings.png"),leftPos+7,topPos+210,178,96);
        for(var slot:menu.slots)RuneUi.slot(g,leftPos+slot.x-1,topPos+slot.y-1,18,isHovering(slot.x,slot.y,16,16,mx,my));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){super.extractRenderState(g,mx,my,partial);extractTooltip(g,mx,my);if(!chosen.isEmpty()&&ingredientArea().contains(mx,my))g.setTooltipForNextFrame(font,chosen,mx,my);}
}
