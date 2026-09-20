package com.cappleapple.astralrepository.client;

import java.math.BigDecimal;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/** One wheel event is one step, independent of platform scroll delta magnitude. */
public final class NumericScroll {
    public static int step(boolean shift,boolean control){return control?(shift?1000:100):(shift?10:1);}
    public static int step(){return step(Screen.hasShiftDown(),Screen.hasControlDown());}
    public static boolean adjust(EditBox field,double x,double y,double wheel,long min,long max,boolean unlimited){
        if(field==null||!field.visible||!field.active||!field.isMouseOver(x,y)||wheel==0)return false;
        try{BigDecimal value=field.getValue().isBlank()?BigDecimal.valueOf(unlimited?-1:0):new BigDecimal(field.getValue());
            if(unlimited&&value.compareTo(BigDecimal.valueOf(-1))<=0){field.setValue(wheel>0?"0":"-1");return true;}
            value=value.add(BigDecimal.valueOf(wheel>0?step():-step())).max(BigDecimal.valueOf(unlimited?-1:min)).min(BigDecimal.valueOf(max));
            field.setValue(value.stripTrailingZeros().toPlainString());
        }catch(NumberFormatException ignored){field.setValue(Long.toString(min));}return true;
    }
    private NumericScroll(){}
}
