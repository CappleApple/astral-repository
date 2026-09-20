package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.content.RecipeTomeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Recipe output uses the same item icon and localized name as an ordinary item tooltip. */
public final class TomeOutputTooltip implements ClientTooltipComponent {
    private final ItemStack output;
    public TomeOutputTooltip(RecipeTomeItem.OutputTooltip data){
        var level=Minecraft.getInstance().level;
        ItemStack sample=level==null?ItemStack.EMPTY:RecipeTomeItem.product(data.tome(),level.registryAccess());
        if(sample.isEmpty()){
            var id=ResourceLocation.tryParse(data.tome().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("OutputId"));
            if(id!=null&&BuiltInRegistries.ITEM.containsKey(id))sample=new ItemStack(BuiltInRegistries.ITEM.get(id));
        }
        output=sample;
    }
    @Override public int getHeight(){return output.isEmpty()?0:20;}
    @Override public int getWidth(Font font){return output.isEmpty()?0:22+font.width(output.getHoverName());}
    @Override public void renderImage(Font font,int x,int y,GuiGraphics g){
        if(output.isEmpty())return;
        g.renderItem(output,x,y);g.drawString(font,output.getHoverName(),x+22,y+4,0xffffff);
    }
}
