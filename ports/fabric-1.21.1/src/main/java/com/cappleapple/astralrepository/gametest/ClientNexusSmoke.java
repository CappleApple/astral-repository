package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.client.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.function.Consumer;

@EventBusSubscriber(modid=AstralRepository.MOD_ID,value=Dist.CLIENT)
public final class ClientNexusSmoke {
    private static int phase,ticks;
    private static boolean pending,done;
    private static double originalOpacity=Double.NaN,baseGreen;
    private static java.util.UUID pushId,pullId,pickupId;
    private static long pickupBefore;
    private static int runeItemsBefore;
    private static EditBox editedPriority;
    private static RuneSettingsScreen previousEditor;
    private static long jadeShownBefore,builtInShownBefore;
    private static final Path OUT=Path.of("../build/client-smoke");
    private static final BlockPos NEXUS=new BlockPos(1,-59,0),CHEST=new BlockPos(3,-59,0),PUSH_TARGET=new BlockPos(-2,-59,0),PULL_TARGET=new BlockPos(0,-59,2);
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("astral_repository.clientSmoke")||done)return;
        Minecraft mc=Minecraft.getInstance();
        mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);mc.mouseHandler.releaseMouse();
        try {
            if(phase>=13&&phase<=24&&mc.player!=null)mc.player.getInventory().selected=phase>=19?0:6;
            // Set the actual LocalPlayer inspection input after movement, before HUD/Jade callbacks.
            if(phase>=25&&mc.player!=null&&mc.player.input!=null)mc.player.input.shiftKeyDown=phase==39||phase==40||phase==30||phase==32||phase==34||phase==58||phase==59;
            if(++ticks>3600)throw new AssertionError("Client gate timed out in phase "+phase);
            if(phase==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                next(1);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.options.guiScale().set(2);mc.options.renderDistance().set(3);mc.resizeDisplay();
                mc.createWorldOpenFlows().createFreshLevel("astral-smoke-"+System.currentTimeMillis(),new LevelSettings("Astral smoke",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
            } else if(phase==1&&mc.level!=null&&mc.player!=null&&mc.getSingleplayerServer()!=null&&!pending&&mc.getOverlay()==null){
                server(2,ClientNexusSmoke::workshop);
            } else if(phase==2&&ticks>180&&!pending){
                if(Boolean.getBoolean("astral_repository.transferStress")){if(TransferStressClientSmoke.tick()){done=true;mc.stop();}return;}
                if(Boolean.getBoolean("astral_repository.resourceArrivalOnly")){if(ResourceArrivalClientSmoke.tick()){done=true;mc.stop();}return;}
                if(Boolean.getBoolean("astral_repository.powerVisibilityOnly")){if(PowerVisibilityClientSmoke.tick()){done=true;mc.stop();}return;}
                if(Boolean.getBoolean("astral_repository.trimOnly")){if(AstralTrimClientSmoke.tick()){done=true;mc.stop();}return;}
                if(Boolean.getBoolean("astral_repository.gogglesOnly")){if(GogglesClientSmoke.tick()){done=true;mc.stop();}return;}
                if(Boolean.getBoolean("astral_repository.presentationOnly")){if(PresentationClientSmoke.tick()){done=true;mc.stop();}return;}
                if(Boolean.getBoolean("astral_repository.feedbackOnly")){if(BindingFeedbackClientSmoke.tick()){done=true;mc.stop();}return;}
                if(Boolean.getBoolean("astral_repository.crystalStorageOnly")){if(StorageCrystalClientSmoke.tick()){done=true;mc.stop();}return;}
                if(Boolean.getBoolean("astral_repository.furnaceOnly")){if(FurnaceRoutingSmoke.tick()){done=true;mc.stop();}return;}
                if(Boolean.getBoolean("astral_repository.bindingOnly")){if(BindingClientSmoke.tick()){done=true;mc.stop();}return;}
                if(Boolean.getBoolean("astral_repository.editorOnly")){
                    if(InventoryEditorSmoke.tick()){Files.writeString(OUT.resolve("result.txt"),"PASS: inventory rune and tome editors, unlimited stepping, binding glint, optional recipe-viewer drops.\n");done=true;mc.stop();}return;
                }
                if(Boolean.getBoolean("astral_repository.routingOnly")){
                    if(RelayRoutingSmoke.tick()){done=true;mc.stop();}return;
                }
                if(Boolean.getBoolean("astral_repository.wandOnly")){
                    if(WandEditorSmoke.tick()){Files.createDirectories(OUT);Files.writeString(OUT.resolve("result.txt"),"PASS: silent mouse-free wand editor; instance presets, drawing, transformed item overlay, cropping, preset placement and world render.\n");done=true;mc.stop();}return;
                }
                if(Boolean.getBoolean("astral_repository.nodeModels")){
                    if(NodeModelSmoke.tick()){Files.createDirectories(OUT);Files.writeString(OUT.resolve("result.txt"),"PASS: dedicated node model client; see node-models/result.txt and per-model GPU measurements.\n");done=true;mc.stop();}
                    return;
                }
                if(Boolean.getBoolean("astral_repository.guideOnly")){MaterialGeometrySmoke.verify();next(60);}else server(3,p->NetworkManager.open(p,GlobalPos.of(p.level().dimension(),NEXUS)));
            } else if(phase==3&&mc.screen instanceof NexusScreen screen&&ticks>25){
                NexusPresentationSmoke.verify(screen);
                if(Boolean.getBoolean("astral_repository.bundledTest")){
                    BundledTabSmoke.verify(screen);screenshot("nexus_bundled.png");
                    Files.createDirectories(OUT);Files.writeString(OUT.resolve("result.txt"),"PASS: silent mouse-free client with real Bundled Not Siloed; astral tab background, independent opacity, unchanged native buttons and non-Nexus scope verified.\n");
                    done=true;mc.stop();return;
                }
                MaterialGeometrySmoke.verify();ParallaxMaterialSmoke.verify();
                for(int slot=0;slot<8;slot++){ItemStack icon=mc.player.getInventory().getItem(slot);if(RuneGlyph.isRune(icon)||icon.is(AstralContent.RECIPE_TOME.get())||icon.is(AstralContent.ATTUNEMENT_WAND.get()))check((mc.getItemColors().getColor(icon,0)>>>24)==255,"Tinted item icon is transparent");}
                screenshot("nexus.png");
                check(!screen.getMenu().entries.isEmpty(),"Linked Nexus did not receive storage entries");
                EditBox search=(EditBox)screen.children().stream().filter(w->w instanceof EditBox e&&e.getMessage().getString().equals("Search storage")).findFirst().orElseThrow();
                screen.mouseClicked(search.getX()+4,search.getY()+4,0);check(search.isFocused(),"Search click did not focus");screen.charTyped('i',0);screen.charTyped('r',0);check(search.getValue().equals("ir"),"Search typing failed");
                screen.mouseClicked(search.getX()+4,search.getY()+4,1);check(search.getValue().isEmpty(),"Right-click failed to clear entire search");check(search.isFocused(),"Clear lost focus");
                screen.mouseClicked(search.getX()-5,search.getY()-10,1);check(!search.isFocused(),"Right-click outside retained focus");
                screen.mouseClicked(search.getX()+4,search.getY()+4,0);screen.mouseClicked(search.getX()-5,search.getY()-10,0);check(!search.isFocused(),"Left-click outside retained focus");
                next(46);
            } else if(phase==4&&!pending){
                server(5,p->{
                    check(count((ChestBlockEntity)p.serverLevel().getBlockEntity(PUSH_TARGET),Items.IRON_INGOT)==64,"Push failed to send the host iron to its plain target");
                    check(count((ChestBlockEntity)p.serverLevel().getBlockEntity(CHEST),Items.DIAMOND)==16,"Pull failed to bring the plain target diamonds into its rune host");
                    p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AstralContent.RESONANCE_GOGGLES.get()));p.getInventory().selected=0;lookAtRune(p,0);
                });
            } else if(phase==5&&ticks>30&&RuneRenderer.visibleFaces()>0){
                check(RuneRenderer.renderedGlyphs()>=2,"Goggles did not render both independent Push/Pull runes");screenshot("runes_with_goggles.png");next(6);
            } else if(phase==6&&!pending){server(7,p->{p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);p.getInventory().selected=6;look(p,3.5,-60,3.25,new Vec3(1.5,-58.5,.5));});
            } else if(phase==7&&ticks>20){
                check(RuneRenderer.visibleFaces()==0&&RuneRenderer.renderedGlyphs()==0,"Unaimed runes remained visible without goggles or an editing tool");screenshot("runes_without_goggles.png");next(8);
            } else if(phase==8&&!pending){
                server(9,p->{p.getInventory().selected=7;AstralContent.RECIPE_TOME.get().use(p.level(),p,InteractionHand.MAIN_HAND);});
            } else if(phase==9&&mc.screen instanceof RecipeTomeScreen screen&&ticks>10){screen.acceptItem(new ItemStack(Items.IRON_TRAPDOOR));next(10);
            } else if(phase==10&&mc.screen instanceof RecipeTomeScreen screen&&ticks>12){
                if(screen.visibleRecipes().stream().noneMatch(e->e.recipe().equals(ResourceLocation.withDefaultNamespace("iron_trapdoor"))))return;
                screenshot("tome_catalogue.png");check(screen.selectRecipe(ResourceLocation.withDefaultNamespace("iron_trapdoor")),"Recipe selection failed");next(11);
            } else if(phase==11&&mc.screen instanceof RecipeTomeScreen screen&&ticks>10){screenshot("tome_recipe.png");screen.inscribeSelection();next(12);
            } else if(phase==12&&ticks>20&&!pending){
                screenshot("tome_inscribed.png");
                server(13,p->{check(RecipeTomeItem.product(p.getMainHandItem(),p.registryAccess()).is(Items.IRON_TRAPDOOR),"Book inscription packet did not save the selected output");p.closeContainer();p.getInventory().selected=6;look(p,8.5,-59.7,4.5,new Vec3(8.5,-58.65,.5));});
            } else if(phase==13&&ticks>35){
                check(AstralPlaneRenderType.ready(),"Custom astral parallax shader was not loaded");InterfaceMaterialSmoke.verify();screenshot("astral_plane_front.png");next(14);
            } else if(phase==14&&!pending){server(15,p->look(p,5.5,-59.7,3.5,new Vec3(8.5,-58.65,.5)));
            } else if(phase==15&&ticks>25){
                screenshot("astral_plane_angle.png");originalOpacity=com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.get();
                com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.set(0.0);next(16);
            } else if(phase==16&&ticks>10){
                check(Math.abs(AstralPlaneRenderType.appliedOverlayOpacity())<0.001,"Zero opacity did not reach the shader during rendering");
                screenshot("astral_plane_opacity_zero.png");baseGreen=surfaceGreen();
                com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.set(1.0);next(17);
            } else if(phase==17&&ticks>10){
                check(Math.abs(AstralPlaneRenderType.appliedOverlayOpacity()-1)<0.001,"Full opacity did not reach the shader during rendering");
                screenshot("astral_plane_opacity_full.png");check(baseGreen-surfaceGreen()>0.035,"Opacity changed the setting but not the rendered crystal surface");
                com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.set(originalOpacity);next(18);
            } else if(phase==18&&ticks>10){
                check(Math.abs(AstralPlaneRenderType.appliedOverlayOpacity()-originalOpacity)<0.001,"Configured opacity was not restored");
                screenshot("astral_plane_angle.png");next(19);
            } else if(phase==19&&!pending){
                com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.set(0.55);
                server(20,p->{p.getInventory().selected=0;look(p,10,-60,3,new Vec3(10,-58.65,.5));});
            } else if(phase==20&&ticks>=20){
                if(ticks==20)screenshot("buds_front.png");
                if(ticks%5==0&&ticks<=80)screenshot(String.format("surface_motion_%02d.png",(ticks-20)/5));
                if(ticks>=80){screenshot("buds_front_later.png");next(21);}
            } else if(phase==21&&!pending){server(22,p->look(p,10,-60,-2,new Vec3(10,-58.65,.5)));
            } else if(phase==22&&ticks>25){screenshot("buds_back.png");next(23);
            } else if(phase==23){
                com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.set(originalOpacity);
                mc.setScreen(MaterialGeometrySmoke.preview());next(24);
            } else if(phase==24&&ticks>25){
                screenshot("wand_geometry.png");
                check(!mc.mouseHandler.isMouseGrabbed(),"Test client grabbed mouse");check(mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)==0,"Test client volume was not muted");
                com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.set(0.0);
                mc.setScreen(MaterialGeometrySmoke.wandOpacityPreview());next(43);
            } else if(phase==43&&ticks>15){
                MaterialGeometrySmoke.captureWandOpacityBaseline();screenshot("wand_opacity_zero.png");screenshot("item_overlays_zero.png");
                com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.set(1.0);next(44);
            } else if(phase==44&&ticks>15){
                MaterialGeometrySmoke.verifyWandOpacityIsolation();screenshot("wand_opacity_full.png");screenshot("item_overlays_full.png");
                com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.set(originalOpacity);next(45);
            } else if(phase==45&&ticks>10){
                check(Math.abs(AstralPlaneRenderType.appliedOverlayOpacity()-originalOpacity)<0.001,"Wand comparison did not restore configured opacity");
                screenshot("wand_opacity_default.png");screenshot("item_overlays_default.png");next(25);
            } else if(phase==25&&!pending){
                mc.setScreen(null);jadeShownBefore=jadeShown();builtInShownBefore=RuneRenderer.builtInHoverFrames;
                server(26,p->{p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);p.getInventory().selected=6;var layer=runes(p).get(pushId);layer.filter().setMinimum(2);layer.filter().setTarget(64);layer.changed();lookAtRune(p,0);});
            } else if(phase==26&&ticks>30){
                var hover=RuneRenderer.hover();check(hover!=null&&hover.layer().id().equals(pushId),"Aiming without equipment did not select the visible Push Rune");
                check(!RunePackets.editingTool(mc.player.getMainHandItem())&&mc.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),"Equipment-free fixture accidentally has an editing tool or goggles");
                check(hover.layer().target()!=null&&hover.layer().target().position().pos().equals(PUSH_TARGET)&&hover.layer().filter().contains("Iron"),"Hover does not describe the rune's actual target and filter");
                check(RuneRenderer.hoverIcons((BlockHitResult)mc.hitResult).isEmpty(),"Rune details appeared without holding Shift");
                if(jadeShownBefore>=0)check(jadeShown()==jadeShownBefore,"Jade appended rune details while Shift was not held");
                else check(RuneRenderer.builtInHoverFrames==builtInShownBefore,"Fallback rune popup rendered while Shift was not held");
                screenshot("rune_hover_unshifted.png");next(39);
            } else if(phase==39&&!pending){
                server(40,p->lookAtRune(p,0));
            } else if(phase==40&&ticks>20){
                check(mc.player.isShiftKeyDown(),"Inspection fixture is not holding Shift");
                var icons=RuneRenderer.hoverIcons((BlockHitResult)mc.hitResult).stream().flatMap(java.util.Collection::stream).toList();
                check(icons.stream().anyMatch(icon->icon.texture()!=null),"Shift hover lacks the painted rune icon");
                check(icons.stream().anyMatch(icon->icon.item().is(Items.CHEST)),"Shift hover lacks its target-container icon");
                check(icons.stream().anyMatch(icon->icon.item().is(Items.IRON_INGOT)),"Shift hover lacks the matching-resource icon");
                check(icons.stream().anyMatch(icon->icon.amount().equals("2"))&&icons.stream().anyMatch(icon->icon.amount().equals("64")),"Shift hover lacks configured source/destination amounts");
                if(jadeShownBefore>=0)check(jadeShown()>jadeShownBefore,"Jade registered but never appended the Shift rune icons");
                else check(RuneRenderer.builtInHoverFrames>builtInShownBefore,"Fallback Shift rune icons were never drawn");
                screenshot(jadeShownBefore>=0?"rune_hover_jade.png":"rune_hover_builtin.png");next(27);
            } else if(phase==27&&!pending){
                server(28,p->{p.getInventory().selected=0;interact(p,runeMiss(p,0));check(RuneProgramming.selection(p.getMainHandItem())!=null&&RuneProgramming.selection(p.getMainHandItem()).layer()==null,"A wand click beside the glyph incorrectly selected a rune");interact(p,runeMiss(p,0));check(RuneProgramming.selection(p.getMainHandItem())==null,"Repeating the missed-glyph click did not cancel its container selection");selectRune(p,0);check(RuneProgramming.selection(p.getMainHandItem()).layer().equals(pushId),"Wand did not select the aimed layer");look(p,.5,-60,3.25,PUSH_TARGET.getCenter());});
            } else if(phase==28&&ticks>20){
                check(RuneProgramming.selection(mc.player.getMainHandItem())!=null,"Wand selection was not synchronized for target selection");screenshot("rune_target_selection.png");next(29);
            } else if(phase==29&&!pending){
                server(30,p->{interact(p,bareHit(PUSH_TARGET,Direction.SOUTH));check(runes(p).get(pushId).target()==null&&runes(p).get(pullId).target()!=null,"Repeated target pair failed to unlink only the selected rune");p.getInventory().selected=6;lookAtRune(p,0);});
            } else if(phase==30&&ticks>20){
                check(RuneRenderer.hover()!=null&&RuneRenderer.hover().layer().target()==null,"Unlinked state did not reach the hover");screenshot("rune_unlinked.png");next(31);
            } else if(phase==31&&!pending){
                server(70,p->{
                    p.getInventory().selected=0;selectRune(p,0);interact(p,bareHit(PUSH_TARGET,Direction.SOUTH));RuneProgramming.cancel(p.getMainHandItem());
                    check(runes(p).get(pushId).target()!=null,"Shift-wand assignment failed");
                    p.getInventory().selected=5;p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.COBBLESTONE,32));interact(p,runeHit(p,0));
                    check(p.getMainHandItem().getCount()==32&&!runes(p).get(pushId).filter().matches(new ItemStack(Items.COBBLESTONE)),"Opening settings consumed the held block or changed the filter");
                    check(!runes(p).get(pullId).filter().matches(new ItemStack(Items.COBBLESTONE)),"Sample changed another stacked rune");lookAtRune(p,0);
                });
            } else if(phase==70&&mc.screen instanceof RuneSettingsScreen screen&&ticks>10){
                check(screen.chooseFilter("minecraft:cobblestone"),"Could not add the held block through the filter interface");screen.apply();next(32);
            } else if(phase==32&&mc.screen==null&&ticks>20){
                check(RuneRenderer.hover()!=null&&RuneRenderer.hover().layer().filter().contains("Cobblestone"),"Held sample did not update visible filter information");screenshot("rune_filter_sample.png");next(33);
            } else if(phase==33&&!pending){
                server(71,p->{p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);interact(p,runeHit(p,0));check(!runes(p).get(pushId).filter().entries().isEmpty(),"Opening with an empty hand cleared the filter");});
            } else if(phase==71&&mc.screen instanceof RuneSettingsScreen screen&&ticks>10){
                for(var widget:screen.children())if(widget instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().equals("Clear filter"))button.onPress();
                screen.apply();next(34);
            } else if(phase==34&&mc.screen==null&&ticks>20){
                check(RuneRenderer.hover()!=null&&RuneRenderer.hover().layer().filter().equals("All items and fluids"),"Cleared filter did not reach the hover");screenshot("rune_filter_cleared.png");next(35);
            } else if(phase==35&&!pending){
                server(72,p->{p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_INGOT));p.setShiftKeyDown(false);interact(p,runeHit(p,0));});
            } else if(phase==72&&mc.screen instanceof RuneSettingsScreen screen&&ticks>10){
                check(screen.chooseFilter("minecraft:iron_ingot"),"Could not configure iron through the filter interface");next(36);
            } else if(phase==36&&mc.screen instanceof RuneSettingsScreen screen&&ticks>15){
                check(screen.currentPage().title().contains("Push")&&screen.currentPage().target().contains(PUSH_TARGET.toShortString()),"Advanced editor opened the wrong rune or target");screenshot("rune_advanced_settings.png");
                for(var widget:screen.children())if(widget instanceof EditBox box){switch(box.getMessage().getString()){case "Priority"->box.setValue("13");case "Keep at source"->box.setValue("2");case "Stop at destination"->box.setValue("64");default->{}}}
                for(var widget:screen.children())if(widget instanceof net.minecraft.client.gui.components.Button button&&java.util.Set.of("Running","Whitelist").contains(button.getMessage().getString()))button.onPress();
                editedPriority=(EditBox)screen.children().stream().filter(w->w instanceof EditBox e&&e.getMessage().getString().equals("Priority")).findFirst().orElseThrow();
                screen.setFocused(editedPriority);editedPriority.setFocused(true);editedPriority.setCursorPosition(1);editedPriority.setHighlightPos(1);next(52);
            } else if(phase==46&&mc.screen instanceof NexusScreen screen&&ticks>12){
                int index=-1;for(int i=0;i<screen.getMenu().entries.size();i++)if(screen.getMenu().entries.get(i).stack().is(Items.COPPER_INGOT))index=i;
                check(index>=0,"No copper in the storage-click fixture");storageClick(screen,index,0);next(47);
            } else if(phase==47&&mc.screen instanceof NexusScreen screen&&ticks>10){
                check(screen.getMenu().getCarried().is(Items.COPPER_INGOT)&&screen.getMenu().getCarried().getCount()==48,"Left storage click did not pick up its stack onto the cursor");
                storageClick(screen,53,1);next(48);
            } else if(phase==48&&mc.screen instanceof NexusScreen screen&&ticks>10){
                check(screen.getMenu().getCarried().getCount()==47,"Right-click on an empty storage cell did not deposit exactly one");
                craftingClick(screen,0);next(49);
            } else if(phase==49&&mc.screen instanceof NexusScreen screen&&ticks>10){
                check(screen.getMenu().getCarried().isEmpty()&&screen.getMenu().grid.getItem(0).getCount()==47,"Cursor stack did not enter the vanilla crafting slot");
                screenshot("nexus_cursor.png");craftingClick(screen,0);next(50);
            } else if(phase==50&&mc.screen instanceof NexusScreen screen&&ticks>10){
                check(screen.getMenu().getCarried().getCount()==47&&screen.getMenu().grid.getItem(0).isEmpty(),"Vanilla crafting slot did not return its stack to the cursor");storageClick(screen,53,0);next(51);
            } else if(phase==51&&mc.screen instanceof NexusScreen screen&&ticks>10){
                check(screen.getMenu().getCarried().isEmpty(),"Left-click on empty storage did not deposit the cursor remainder");
                check(screen.getMenu().entries.stream().anyMatch(e->e.stack().is(Items.COPPER_INGOT)&&e.count()==48),"Storage cursor round trip lost copper");
                next(66);
            } else if(phase==66&&mc.screen instanceof NexusScreen screen){
                if(NexusUiSmoke.tick(screen)){screen.keyPressed(mc.options.keyInventory.getKey().getValue(),0,0);check(!(mc.screen instanceof NexusScreen),"Inventory key remained captured after blur");next(4);}
            } else if(phase==52&&mc.screen instanceof RuneSettingsScreen screen&&ticks>15&&!screen.pendingChanges()&&!pending){
                check(screen.children().contains(editedPriority)&&editedPriority.getValue().equals("13")&&editedPriority.getCursorPosition()==1&&editedPriority.isFocused(),"Autosave replaced the active widget, draft, focus, or caret");
                previousEditor=screen;server(53,p->{var layer=runes(p).get(pushId);check(layer.priority()==13&&!layer.enabled()&&!layer.filter().all()&&layer.filter().blacklist()&&layer.filter().minimum()==2&&layer.filter().target()==64,"Settings were not saved while the editor remained open");RuneSettingsPackets.open(p,runes(p),layer);RuneSettingsPackets.open(p,runes(p),layer);});
            } else if(phase==53&&mc.screen instanceof RuneSettingsScreen screen&&ticks>10){check(screen!=previousEditor&&!screen.currentPage().session().equals(previousEditor.currentPage().session()),"The latest rune editor session did not replace its superseded token");next(54);
            } else if(phase==54&&mc.screen instanceof RuneSettingsScreen screen&&ticks>12){
                check(screen.getMenu().slots.size()==36,"Rune editor lacks its player inventory");
                screenshot("rune_filter_search.png");check(screen.chooseFilter("minecraft:copper_ingot"),"Clicking the visible filter result failed");next(55);
            } else if(phase==55&&mc.screen instanceof RuneSettingsScreen screen&&ticks>15&&!screen.pendingChanges()&&!pending){
                server(56,p->check(runes(p).get(pushId).filter().entries().stream().anyMatch(e->e.id().equals("minecraft:copper_ingot")),"The selected filter was not saved while the editor remained open"));
            } else if(phase==56&&mc.screen instanceof RuneSettingsScreen screen&&ticks>5){
                for(var widget:screen.children())if(widget instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().equals("Back"))button.onPress();
                screenshot("rune_advanced_settings_edited.png");
                editedPriority=(EditBox)screen.children().stream().filter(w->w instanceof EditBox e&&e.getMessage().getString().equals("Priority")).findFirst().orElseThrow();editedPriority.setValue("14");
                screen.mouseClicked(screen.getGuiLeft()+7+24+10,screen.getGuiTop()+42,0);next(64);
            } else if(phase==64&&mc.screen instanceof RuneSettingsScreen screen&&ticks>15&&!screen.pendingChanges()){
                check(screen.currentPage().priority()==14&&screen.visibleError().contains("Hold a matching item"),"Rejected filter action lost its feedback behind the following scalar autosave");editedPriority.setValue("13");next(65);
            } else if(phase==65&&mc.screen instanceof RuneSettingsScreen screen&&ticks>15&&!screen.pendingChanges()){
                check(screen.currentPage().priority()==13&&screen.visibleError().isEmpty(),"A new valid edit did not clear the old rejection feedback");screen.apply();next(37);
            } else if(phase==57&&!pending){
                mc.options.keyShift.setDown(true);server(58,p->{BlockPos host=new BlockPos(9,-59,6);var surface=RuneSurfaces.get(p.serverLevel(),host,Direction.SOUTH);pickupId=surface.layers().get(0).id();runeItemsBefore=p.getInventory().items.stream().filter(s->s.is(Items.PAPER)).mapToInt(ItemStack::getCount).sum();p.getInventory().selected=0;p.getAbilities().flying=false;p.onUpdateAbilities();p.setShiftKeyDown(true);p.setPose(net.minecraft.world.entity.Pose.CROUCHING);look(p,9.5,-60,9.0,RuneLayout.placed(surface).get(0).center());});
            } else if(phase==58&&ticks>25){
                check(mc.player.isShiftKeyDown(),"Rune pickup fixture did not hold Shift");
                check(mc.hitResult instanceof BlockHitResult&&RuneRenderer.hover((BlockHitResult)mc.hitResult)!=null&&RuneRenderer.hover((BlockHitResult)mc.hitResult).layer().id().equals(pickupId),"Rune pickup client ray missed its glyph: "+mc.hitResult+" eye="+mc.player.getEyePosition()+" pose="+mc.player.getPose());
                screenshot("rune_four_closeup.png");pickupBefore=RunePickupClient.pickupRequests;mc.options.keyAttack.setDown(true);
                var input=new net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered(0,mc.options.keyAttack,InteractionHand.MAIN_HAND);RunePickupClient.attack(input);check(input.isCanceled(),"Rune attack was not consumed before host mining");next(59);
            } else if(phase==59&&ticks>25&&!pending){
                for(int i=0;i<5;i++){var input=new net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered(0,mc.options.keyAttack,InteractionHand.MAIN_HAND);RunePickupClient.attack(input);check(input.isCanceled(),"Held attack reached the host after pickup");}
                check(RunePickupClient.pickupRequests==pickupBefore+1,"Held attack sent more than one pickup request");screenshot("rune_picked_up.png");
                server(61,p->{var host=new BlockPos(9,-59,6);var surface=RuneSurfaces.get(p.serverLevel(),host,Direction.SOUTH);check(surface!=null&&surface.layers().size()==3&&surface.get(pickupId)==null,"Actual client pickup did not remove only its exact rune");check(p.getInventory().items.stream().filter(s->s.is(Items.PAPER)).mapToInt(ItemStack::getCount).sum()==runeItemsBefore,"Removing a painted rune must not create a legacy item");check(p.level().getBlockState(host).is(Blocks.CHEST),"Rune pickup damaged the host container");p.setShiftKeyDown(false);});
            } else if(phase==61){mc.options.keyAttack.setDown(false);mc.options.keyShift.setDown(false);next(62);
            } else if(phase==62&&ticks>3){next(60);
            } else if(phase==60){
                if(Boolean.getBoolean("astral_repository.captureGuide")||!net.neoforged.fml.ModList.get().isLoaded("patchouli")||GuidebookSmoke.tick())next(63);
            } else if(phase==37&&ticks>15&&!(mc.screen instanceof RuneSettingsScreen)&&!pending){
                server(38,p->{var push=runes(p).get(pushId);var pull=runes(p).get(pullId);check(push.priority()==13&&!push.enabled()&&!push.filter().all()&&push.filter().blacklist()&&push.filter().minimum()==2&&push.filter().target()==64,"Advanced settings did not persist the actual edited controls");check(pull.priority()==0&&pull.enabled()&&pull.filter().minimum()==0,"Advanced settings changed another rune layer");});
            } else if(phase==38){next(41);
            } else if(phase==41&&!pending){server(42,ClientNexusSmoke::runeGallery);
            } else if(phase==42&&ticks>30){
                check(!mc.player.isShiftKeyDown(),"Rune gallery unexpectedly enabled its inspection popup");
                check(RuneRenderer.renderedGlyphs()>=10,"The one-to-four gallery did not render all ten runes");
                screenshot("rune_layouts_1_to_4.png");next(57);
            } else if(phase==63){
                if(Boolean.getBoolean("astral_repository.guideOnly"))ParallaxMaterialSmoke.verify();
                check(!mc.mouseHandler.isMouseGrabbed(),"Test client grabbed mouse");check(mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)==0,"Test client volume was not muted");
                Files.writeString(OUT.resolve("result.txt"),Boolean.getBoolean("astral_repository.guideOnly")?"PASS: silent mouse-free Patchouli guide-only client; all workshop and crystal entries, spreads, and screenshot resources rendered; frozen-time GPU parallax passed.":"PASS: silent mouse-free integrated client; Nexus cursor pickup/deposit and vanilla crafting-slot transitions; tome interactions; "+(Boolean.getBoolean("astral_repository.captureGuide")?"guide screenshot capture; ":net.neoforged.fml.ModList.get().isLoaded("patchouli")?"all Patchouli guide entries and screenshot spreads rendered; ":"Patchouli absent and gameplay client loaded; ")+" independent Push/Pull rune placement and plain-container transfers; Shift-only exact-layer icon/amount hover with unshifted popup suppression, missed-glyph container selection, target selection/toggle/reassignment, held block filter without consumption, clear-filter preserving target, and autosaved independent advanced settings with preserved caret, visual filter search, exact Shift-left rune pickup with held-click suppression; "+(jadeShownBefore>=0?"Jade plugin registered and appended actual Shift icons; ":"built-in Shift rune icons displayed; ")+"one-to-four fixed-size rune layouts rendered on matching chests; closed pixel-thick wand mesh and generated 2D Astral Nexus remote orb geometry and overlay dispatch checked by MaterialGeometrySmoke; real GPU culling and all bud-facing windings checked; depth-projected astral material rendered from front/back with timed drift frames; wand rendered front/oblique/edge-on; actual opacity-0/1 framebuffer comparison changed only the large crystal while every frame/grip and sampled small pommel-gem pixel stayed stable, and all four remote-orb quadrants changed with opacity; live opacity 0/1 verified against framebuffer pixels and configured opacity restored.");done=true;mc.stop();
            }
        }catch(Throwable failure){fail(failure);}
    }
    private static void storageClick(NexusScreen screen,int cell,int button){double x=(screen.width-318)/2+10+(cell%9)*20+8,y=(screen.height-266)/2+49+(cell/9)*18+8;check(screen.mouseClicked(x,y,button),"Storage click was not consumed");screen.mouseReleased(x,y,button);}
    private static void craftingClick(NexusScreen screen,int button){var slot=screen.getMenu().slots.get(1);double x=(screen.width-318)/2+slot.x+8,y=(screen.height-266)/2+slot.y+8;screen.mouseClicked(x,y,button);screen.mouseReleased(x,y,button);}
    private static void workshop(ServerPlayer p){
        var level=p.serverLevel();p.getAbilities().flying=true;p.onUpdateAbilities();look(p,1.5,-59,4.5,new Vec3(1.5,-58.5,.5));
        level.setBlockAndUpdate(NEXUS,AstralContent.STORAGE_NEXUS.get().defaultBlockState());
        level.setBlockAndUpdate(CHEST,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.SOUTH));
        var chest=(ChestBlockEntity)level.getBlockEntity(CHEST);chest.setItem(0,new ItemStack(Items.IRON_INGOT,64));chest.setItem(1,new ItemStack(Items.COPPER_INGOT,48));chest.setItem(2,new ItemStack(AstralContent.ASTRAL_GEM.get(),24));chest.setItem(3,new ItemStack(Items.OAK_LOG,32));
        var manager=NetworkManager.get(p.server);var root=AnchorAddress.crystal(level,NEXUS);
        int x=-4;for(var block:AstralContent.NODES){BlockPos at=new BlockPos(x++,-59,-4);level.setBlockAndUpdate(at,block.get().defaultBlockState());check(manager.toggleLink(root,AnchorAddress.crystal(level,at)).success(),"Workshop crystal link failed");}
        p.getInventory().clearContent();Item[] kit={AstralContent.ATTUNEMENT_WAND.get(),Items.PAPER,Items.STICK,Items.IRON_INGOT,Items.DIAMOND,Items.COBBLESTONE,AstralContent.ASTRAL_GEM.get(),AstralContent.RECIPE_TOME.get(),AstralContent.RESONANCE_GOGGLES.get()};
        for(int i=0;i<kit.length;i++)p.getInventory().setItem(i,new ItemStack(kit[i]));
        level.setBlockAndUpdate(PUSH_TARGET,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.SOUTH));
        level.setBlockAndUpdate(PULL_TARGET,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.SOUTH));
        ((ChestBlockEntity)level.getBlockEntity(PULL_TARGET)).setItem(0,new ItemStack(Items.DIAMOND,16));
        p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        var initial=RuneSurfaces.getOrCreate(level,CHEST,Direction.SOUTH);initial.addLayer(com.cappleapple.astralrepository.content.RuneGlyph.id(com.cappleapple.astralrepository.content.RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);initial.addLayer(com.cappleapple.astralrepository.content.RuneGlyph.id(com.cappleapple.astralrepository.content.RuneLayer.Mode.PULL),RuneLayer.Mode.PULL);
        pushId=runes(p).layers().get(0).id();pullId=runes(p).layers().get(1).id();
        runes(p).get(pushId).filter().add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,new ItemStack(Items.IRON_INGOT));runes(p).get(pullId).filter().add(FilterRules.Kind.ITEM,"minecraft:diamond",false,new ItemStack(Items.DIAMOND));
        p.getInventory().selected=0;selectRune(p,0);interact(p,bareHit(PUSH_TARGET,Direction.SOUTH));
        selectRune(p,1);interact(p,bareHit(PULL_TARGET,Direction.UP));RuneProgramming.cancel(p.getMainHandItem());
        check(runes(p).get(pushId).target()!=null&&runes(p).get(pullId).target()!=null,"Independent rune targets were not assigned");
        check(RuneSurfaces.at(level,PUSH_TARGET).isEmpty()&&RuneSurfaces.at(level,PULL_TARGET).isEmpty(),"Rune targets should remain plain containers");
        level.setBlockAndUpdate(new BlockPos(6,-59,0),AstralContent.ASTRAL_GEODE.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(7,-59,0),AstralContent.BUDDING_ASTRAL.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(8,-59,0),AstralContent.SMALL_ASTRAL_BUD.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(9,-59,0),AstralContent.MEDIUM_ASTRAL_BUD.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(10,-59,0),AstralContent.LARGE_ASTRAL_BUD.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(11,-59,0),AstralContent.ASTRAL_CLUSTER.get().defaultBlockState());
    }
    private static void runeGallery(ServerPlayer p){
        var level=p.serverLevel();double glyphSize=-1;
        p.setShiftKeyDown(false);p.getInventory().selected=1;
        for(int count=1;count<=4;count++){
            BlockPos pos=new BlockPos(1+count*2,-59,6);
            level.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.SOUTH));
            look(p,pos.getX()+.5,-60,9.5,pos.getCenter());
            var placed=RuneSurfaces.getOrCreate(level,pos,Direction.SOUTH);for(int index=0;index<count;index++)placed.addLayer(com.cappleapple.astralrepository.content.RuneGlyph.id(com.cappleapple.astralrepository.content.RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
            var surface=RuneSurfaces.get(level,pos,Direction.SOUTH);
            check(surface!=null&&surface.layers().size()==count,"Gallery rune placement did not produce "+count+" independent layers");
            for(var cell:RuneLayout.placed(surface)){
                if(glyphSize<0)glyphSize=cell.size();
                check(cell.size()<=com.cappleapple.astralrepository.AstralServerConfig.runeSize.get(),"Saved rune exceeds the configured footprint");
            }
        }
        p.getInventory().selected=0;p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(AstralContent.RESONANCE_GOGGLES.get()));
        look(p,6.5,-59,13.5,new Vec3(6.5,-58.7,6.5));
    }
    private static RuneSurface runes(ServerPlayer p){return RuneSurfaces.get(p.serverLevel(),CHEST,Direction.SOUTH);}
    private static BlockHitResult runeHit(ServerPlayer p,int index){return new BlockHitResult(RuneLayout.placed(runes(p)).get(index).center(),Direction.SOUTH,CHEST,false);}
    private static BlockHitResult runeMiss(ServerPlayer p,int index){var cell=RuneLayout.placed(runes(p)).get(index);return new BlockHitResult(cell.center().add(cell.right().scale(cell.size()*0.55)),Direction.SOUTH,CHEST,false);}
    private static BlockHitResult bareHit(BlockPos pos,Direction face){return new BlockHitResult(pos.getCenter().add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),face,pos,false);}
    private static void selectRune(ServerPlayer p,int index){p.setShiftKeyDown(true);interact(p,runeHit(p,index));p.setShiftKeyDown(false);}
    private static void interact(ServerPlayer p,BlockHitResult hit){check(p.gameMode.useItemOn(p,p.level(),p.getMainHandItem(),InteractionHand.MAIN_HAND,hit).consumesAction(),"Actual rune/wand block interaction was not consumed");}
    private static void lookAtRune(ServerPlayer p,int index){look(p,3.5,-60,3.25,runeHit(p,index).getLocation());}
    private static int count(ChestBlockEntity chest,Item item){int result=0;for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(item))result+=chest.getItem(slot).getCount();return result;}
    private static long jadeShown()throws ReflectiveOperationException{
        if(!net.neoforged.fml.ModList.get().isLoaded("jade")){check(!Boolean.getBoolean("astral_repository.jadeTest"),"Jade test requested but Jade is not loaded");return -1;}
        Class<?> plugin=Class.forName("com.cappleapple.astralrepository.compat.AstralJadePlugin");check(plugin.getField("registered").getBoolean(null),"Jade did not register the rune tooltip provider");return plugin.getField("shown").getLong(null);
    }
    private static void look(ServerPlayer p,double x,double y,double z,Vec3 target){Vec3 delta=target.subtract(x,y+p.getEyeHeight(),z);float yaw=(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90;float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));p.connection.teleport(x,y,z,yaw,pitch);}
    private static void server(int after,Consumer<ServerPlayer> action){
        pending=true;var mc=Minecraft.getInstance();var id=mc.player.getUUID();
        mc.getSingleplayerServer().execute(()->{
            try{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);action.accept(player);
                int selected=player.getInventory().selected;
                mc.execute(()->{
                    try{
                        // Server-selected slots are not automatically sent to the client by vanilla.
                        // Mirror the completed fixture action and send the ordinary carried-slot packet.
                        mc.player.getInventory().selected=selected;
                        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(selected));
                        pending=false;next(after);
                    }catch(Throwable failure){fail(failure);}
                });
            }catch(Throwable failure){mc.execute(()->fail(failure));}
        });
    }
    private static double surfaceGreen(){
        try(NativeImage image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){
            long total=0;int pixels=0;
            for(int y=350;y<440;y++)for(int x=270;x<460;x++){total+=(image.getPixelRGBA(x,y)>>>8)&255;pixels++;}
            return total/(pixels*255.0);
        }
    }
    private static void next(int value){phase=value;ticks=0;}
    private static void screenshot(String name)throws Exception{Files.createDirectories(OUT);try(NativeImage image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(OUT.resolve(name));}}
    private static void fail(Throwable failure){if(done)return;done=true;try{screenshot("failure.png");}catch(Exception ignored){}Minecraft.getInstance().options.keyAttack.setDown(false);Minecraft.getInstance().options.keyShift.setDown(false);if(Minecraft.getInstance().player!=null&&Minecraft.getInstance().player.input!=null)Minecraft.getInstance().player.input.shiftKeyDown=false;if(!Double.isNaN(originalOpacity))com.cappleapple.astralrepository.AstralClientConfig.astralOverlayOpacity.set(originalOpacity);AstralRepository.LOGGER.error("Client workshop gate failed",failure);try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("result.txt"),"FAIL: "+failure);}catch(Exception ignored){}Minecraft.getInstance().stop();}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
