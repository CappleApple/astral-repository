package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.PowerNodeVisibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Server presentation synchronization, real creative tabs, and installed viewer/guide integrations. */
public final class PowerVisibilityClientSmoke {
    private static final Path OUT=Path.of("../build/client-smoke");
    private static final boolean[] STATES={false,true,false};
    private static int phase,ticks,transition;
    private static boolean originalPower,initialized,pending;
    private static volatile String failure;
    private static String waiting="initial server state";

    public static boolean tick()throws Exception{
        try{return run();}
        catch(Throwable error){restore();throw error;}
    }

    private static boolean run()throws Exception{
        var mc=Minecraft.getInstance();
        if(failure!=null)throw new AssertionError(failure);
        if(++ticks>1200)throw new AssertionError("Power visibility timeout during transition "+transition+": "+waiting);
        if(phase==0){
            Files.createDirectories(OUT);
            originalPower=AstralConfig.powerEnabled.get();initialized=true;
            for(String mod:List.of("emi","jei","patchouli"))
                check(!Boolean.getBoolean("astral_repository."+mod+"Test")||installed(mod),mod+" test requested but not installed");
            applyState(false);phase=1;ticks=0;
        }else if(phase==1&&!pending&&ticks>20){
            openCreative();phase=2;ticks=0;
        }else if(phase==2&&!pending&&ticks>30){
            boolean expected=STATES[transition];
            var mismatches=visibilityMismatches(expected);
            if(!mismatches.isEmpty()){waiting=String.join("; ",mismatches);return false;}
            verifyLocalConfigCannotOverrideServer(expected);
            shot("power_visibility_"+transition+"_"+(expected?"enabled":"disabled")+".png");
            if(++transition==STATES.length){
                check(!mc.mouseHandler.isMouseGrabbed(),"Power visibility client captured mouse");
                check(mc.options.getSoundSourceVolume(SoundSource.MASTER)==0,"Power visibility client enabled audio");
                restore();
                Files.writeString(OUT.resolve("result.txt"),"PASS: server COMMON powerEnabled false -> true -> false synchronized; conflicting local fallback could not override the server; open creative tab and search membership refreshed; "+
                        (installed("emi")?"EMI item index and output recipes refreshed; ":"")+
                        (installed("jei")?"JEI ingredient visibility and crafting recipes refreshed; ":"")+
                        (installed("patchouli")?"loaded Patchouli Power Node entry followed the server flag; ":"")+
                        "muted, mouse-free client.\n");
                mc.setScreen(null);return true;
            }
            applyState(STATES[transition]);ticks=0;
        }
        return false;
    }

    private static void applyState(boolean value){
        pending=true;var mc=Minecraft.getInstance();
        mc.getSingleplayerServer().execute(()->{
            try{
                AstralConfig.powerEnabled.set(value);
                // The normal server tick broadcasts the changed setting; never inject the test state on the client.
                mc.execute(()->pending=false);
            }catch(Throwable error){failure=error.toString();}
        });
    }

    private static void openCreative()throws Exception{
        var mc=Minecraft.getInstance();
        var screen=new CreativeModeInventoryScreen(mc.player,mc.level.enabledFeatures(),true);
        mc.setScreen(screen);
        var select=CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab",CreativeModeTab.class);
        select.setAccessible(true);select.invoke(screen,AstralContent.CREATIVE_TAB.get());
    }

    private static List<String> visibilityMismatches(boolean expected)throws Exception{
        var result=new ArrayList<String>();
        var mc=Minecraft.getInstance();
        if(!PowerNodeVisibility.hasServerState()||PowerNodeVisibility.powerEnabled()!=expected)result.add("server presentation packet");
        boolean tab=hasPower(AstralContent.CREATIVE_TAB.get().getDisplayItems());
        boolean search=hasPower(AstralContent.CREATIVE_TAB.get().getSearchTabDisplayItems());
        if(tab!=expected||search!=expected)result.add("creative tab/search membership "+tab+"/"+search);
        if(!(mc.screen instanceof CreativeModeInventoryScreen screen)||hasPower(screen.getMenu().items)!=expected)result.add("open creative menu contents");
        if(installed("emi"))EmiCheck.check(expected,result);
        if(installed("jei"))JeiCheck.check(expected,result);
        if(installed("patchouli"))PatchouliCheck.check(expected,result);
        return result;
    }

    private static void verifyLocalConfigCannotOverrideServer(boolean expected){
        var mc=Minecraft.getInstance();
        boolean previous=AstralConfig.powerEnabled.get();
        try{
            // Integrated client/server share one config object. Probe the opposing client fallback synchronously,
            // then restore it before the server's next tick rather than leaving the shared COMMON value changed.
            AstralConfig.powerEnabled.set(!expected);
            check(PowerNodeVisibility.hasServerState()&&PowerNodeVisibility.powerEnabled()==expected,"Local config overrode synchronized power visibility");
            AstralContent.CREATIVE_TAB.get().buildContents(new CreativeModeTab.ItemDisplayParameters(mc.level.enabledFeatures(),true,mc.level.registryAccess()));
            check(hasPower(AstralContent.CREATIVE_TAB.get().getDisplayItems())==expected,"Creative content used the local config rather than the server flag");
        }finally{AstralConfig.powerEnabled.set(previous);}
    }

    private static final class EmiCheck {
        static void check(boolean expected,List<String> result)throws Exception{
            if(!(boolean)Class.forName("dev.emi.emi.runtime.EmiReloadManager").getMethod("isLoaded").invoke(null)){result.add("EMI reload");return;}
            boolean item=dev.emi.emi.api.EmiApi.getIndexStacks().stream().anyMatch(stack->isPower(stack.getItemStack()));
            boolean recipe=dev.emi.emi.api.EmiApi.getRecipeManager().getRecipes().stream().anyMatch(r->r.getOutputs().stream().anyMatch(stack->isPower(stack.getItemStack())));
            if(item!=expected||recipe!=expected)result.add("EMI item/recipe "+item+"/"+recipe);
        }
    }

    private static final class JeiCheck {
        static void check(boolean expected,List<String> result)throws Exception{
            mezz.jei.api.runtime.IJeiRuntime runtime=null;
            for(var field:com.cappleapple.astralrepository.compat.AstralJeiPlugin.class.getDeclaredFields()){
                if(mezz.jei.api.runtime.IJeiRuntime.class.isAssignableFrom(field.getType())){
                    field.setAccessible(true);runtime=(mezz.jei.api.runtime.IJeiRuntime)field.get(null);break;
                }
            }
            if(runtime==null){result.add("JEI runtime");return;}
            boolean item=runtime.getIngredientVisibility().isIngredientVisible(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,new ItemStack(AstralContent.POWER_NODE.get()));
            boolean recipe=runtime.getRecipeManager().createRecipeLookup(mezz.jei.api.constants.RecipeTypes.CRAFTING).get()
                    .anyMatch(r->isPower(r.value().getResultItem(Minecraft.getInstance().level.registryAccess())));
            if(item!=expected||recipe!=expected)result.add("JEI item/recipe "+item+"/"+recipe);
        }
    }

    private static final class PatchouliCheck {
        static void check(boolean expected,List<String> result){
            var book=vazkii.patchouli.common.book.BookRegistry.INSTANCE.books.get(id("field_guide"));
            if(book==null||book.getContents()==null||book.getContents().isErrored()){result.add("Patchouli loaded contents");return;}
            var entry=book.getContents().entries.get(id("crystal_power_node"));
            if((entry!=null)!=expected)result.add("Patchouli Power Node entry "+(entry!=null));
            if(entry!=null&&(entry.isLocked()||entry.getPages().isEmpty()))result.add("Patchouli Power Node entry locked/empty");
        }
    }

    private static void restore(){
        if(initialized){AstralConfig.powerEnabled.set(originalPower);PowerNodeVisibility.acceptServerState(originalPower);initialized=false;}
    }
    private static boolean installed(String id){return ModList.get().isLoaded(id);}
    private static boolean hasPower(Iterable<ItemStack> items){for(var stack:items)if(isPower(stack))return true;return false;}
    private static boolean isPower(ItemStack stack){return stack.is(AstralContent.POWER_NODE.get().asItem());}
    private static ResourceLocation id(String path){return new ResourceLocation("astral_repository",path);}
    private static void shot(String name)throws Exception{try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(OUT.resolve(name));}}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private PowerVisibilityClientSmoke(){}
}
