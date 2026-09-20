package com.cappleapple.astralrepository.content;

import com.cappleapple.astralrepository.network.AnchorAddress;
import com.cappleapple.astralrepository.network.NetworkManager;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.cappleapple.astralrepository.platform.capabilities.Capabilities;
import com.cappleapple.astralrepository.platform.common.CommonHooks;
import com.cappleapple.astralrepository.platform.items.ItemHandlerHelper;
import com.cappleapple.astralrepository.platform.items.wrapper.PlayerMainInvWrapper;
import com.cappleapple.astralrepository.platform.event.entity.player.PlayerInteractEvent;

/** Each visible Push/Pull rune has its own target and filter. All mutations are server-owned. */
public final class RuneProgramming {
    private static final String SELECTION = "RuneLinkStart";
    private static final ThreadLocal<Boolean> PICKUP_PERMISSION_CHECK = ThreadLocal.withInitial(() -> false);
    public record Selection(AnchorAddress address, UUID layer) {}
    @FunctionalInterface public interface SettingsOpener { void open(ServerPlayer player, RuneSurface surface, RuneLayer layer); }
    public static SettingsOpener openSettings = (player, surface, layer) -> {};
    public static Function<BlockHitResult, Integer> clientSelection = hit -> -1;

    private RuneProgramming() {}
    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (use(event.getItemStack(), event.getLevel(), event.getPos(), event.getEntity(), event.getHand(), event.getHitVec())) {
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
        }
    }
    /** Vanilla mining packets must never break the container underneath a Shift-clicked glyph. */
    public static void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (PICKUP_PERMISSION_CHECK.get() || !event.getEntity().isShiftKeyDown()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            BlockHitResult hit = pickupHit(player, event.getPos(), event.getFace());
            if (hit != null && selectedLayer(player.serverLevel(), hit) != null) event.setCanceled(true);
        } else {
            var hit = event.getEntity().pick(event.getEntity().blockInteractionRange(), 1, false);
            if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                    && blockHit.getBlockPos().equals(event.getPos()) && blockHit.getDirection() == event.getFace()
                    && clientSelection.apply(blockHit) >= 0) event.setCanceled(true);
        }
    }

    /** The client supplies identity only; reach, line of sight, face, and visible cell are checked here. */
    public static boolean pickup(ServerPlayer player, BlockPos pos, Direction face, UUID expectedLayer) {
        BlockHitResult hit = pickupHit(player, pos, face);
        if (hit == null) return false;
        RuneLayer selected = selectedLayer(player.serverLevel(), hit);
        if (selected == null || !selected.id().equals(expectedLayer)) return false;
        // Preserve other mods' normal mining protection hooks without canceling our own permission check.
        PICKUP_PERMISSION_CHECK.set(true);
        try {
            if (CommonHooks.onLeftClickBlock(player, pos, face, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK).isCanceled()) return false;
        } finally { PICKUP_PERMISSION_CHECK.remove(); }
        RuneSurface surface = RuneSurfaces.get(player.serverLevel(), pos, face);
        return surface != null && removeAndReturnRune(player, surface, selected);
    }

    private static BlockHitResult pickupHit(ServerPlayer player, BlockPos pos, Direction face) {
        ServerLevel level = player.serverLevel();
        if (face == null || !player.isAlive() || player.isSpectator() || !player.isShiftKeyDown()
                || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !player.canInteractWithBlock(pos, 0) || !level.mayInteract(player, pos)
                || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) return null;
        double reach = player.blockInteractionRange();
        // Check the short ray's chunks before calling vanilla outline clipping; pickup never loads chunks.
        var eye = player.getEyePosition();
        // Movement packets update body yaw before the next server tick updates animated head yaw.
        var view = player.calculateViewVector(player.getXRot(), player.getYRot());
        for (double distance = 0; distance <= reach; distance += 1) {
            if (!level.hasChunkAt(BlockPos.containing(eye.add(view.scale(distance))))) return null;
        }
        if (!level.hasChunkAt(BlockPos.containing(eye.add(view.scale(reach))))) return null;
        HitResult ray = level.clip(new ClipContext(eye, eye.add(view.scale(reach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return ray instanceof BlockHitResult hit && ray.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(pos) && hit.getDirection() == face ? hit : null;
    }

    private static RuneLayer selectedLayer(ServerLevel level, BlockHitResult hit) {
        RuneSurface surface = RuneSurfaces.get(level, hit.getBlockPos(), hit.getDirection());
        if (surface == null) return null;
        int index = RuneLayout.selectedIndex(RuneLayout.placed(surface), hit.getLocation());
        return index < 0 ? null : surface.layers().get(index);
    }

    /** Caller must validate access first. Removing an inscription does not create an item. */
    public static boolean removeAndReturnRune(ServerPlayer player, RuneSurface surface, RuneLayer layer) {
        if (surface.get(layer.id()) != layer) return false;
        if (!surface.removeLayer(layer.id())) return false;
        if (surface.layers().isEmpty()) RuneSurfaces.remove(player.serverLevel(), surface.getBlockPos(), surface.facing());
        player.containerMenu.broadcastChanges();
        message(player, "Rune picked up.");
        return true;
    }
    public static boolean use(ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        boolean wand = stack.is(AstralContent.ATTUNEMENT_WAND.get());
        
        if (level.getBlockEntity(pos) == null) {
            if(wand&&player.isShiftKeyDown()){if(!level.isClientSide)cancel(stack);return true;}
            return false;
        }
        if (!(level instanceof ServerLevel server)) {
            // Predict consumption for the same visible cell, so blocks/buckets never use the host instead.
            return wand || clientSelection.apply(hit) >= 0;
        }
        RuneSurface surface = RuneSurfaces.get(server, pos, hit.getDirection());
        int index = surface == null ? -1 : RuneLayout.selectedIndex(RuneLayout.placed(surface), hit.getLocation());
        RuneLayer selected = index < 0 ? null : surface.layers().get(index);
        if(wand&&player.isShiftKeyDown()){
            if(selected!=null&&selected.mode()==RuneLayer.Mode.FILTER){if(player instanceof ServerPlayer sp)openSettings.open(sp,surface,selected);return true;}
            if(selected!=null||level.getBlockEntity(pos) instanceof CrystalNodeBlockEntity||isContainer(server,pos,hit.getDirection())){
                Selection first=selection(stack),current=new Selection(AnchorAddress.rune(server,pos,hit.getDirection()),selected==null?null:selected.id());
                // A container-first selection stays the target while assigning several runes.
                // A rune-first selection instead switches to the newly Shift-clicked glyph.
                if(first!=null&&first.layer()==null&&selected!=null){
                    link(stack,server,current,player);return true;
                }
                // Keeping Shift held while clicking a target still completes the pending link.
                if(first!=null&&selected==null&&!sameEndpoint(server,first,current)){
                    link(stack,server,current,player);return true;
                }
                com.cappleapple.astralrepository.network.WandPackets.selection(stack,com.cappleapple.astralrepository.network.WandPackets.selected(stack),false);
                select(stack,new Selection(AnchorAddress.rune(server,pos,hit.getDirection()),selected==null?null:selected.id()));
                var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
                data.putBoolean(selected!=null?"RuneTargeting":"ContainerTargeting",true);
                stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
            }else cancel(stack);
            return true;
        }
        // Existing glyphs take priority over placing a preset or using the held item.
        if (selected != null) {
            Selection first=wand?selection(stack):null;
            if(first!=null&&!sameEndpoint(server,first,new Selection(surface.address(),selected.id()))){link(stack,server,new Selection(surface.address(),selected.id()),player);return true;}
            if (wand) cancel(stack);
            if (player instanceof ServerPlayer sp) openSettings.open(sp, surface, selected);
            return true;
        }
        if(wand&&com.cappleapple.astralrepository.network.WandPackets.placing(stack)&&!(level.getBlockEntity(pos) instanceof CrystalNodeBlockEntity)) {
            if(!(player instanceof ServerPlayer sp)||!isContainer(server,pos,hit.getDirection()))return true;
            if(!level.mayInteract(player,pos)||player.blockActionRestricted(level,pos,sp.gameMode.getGameModeForPlayer()))return true;
            var preset=com.cappleapple.astralrepository.network.WandPackets.preset(sp,stack);if(preset==null)return true;
            if(surface==null)surface=RuneSurfaces.getOrCreate(server,pos,hit.getDirection());
            var placement=RuneLayout.placement(surface,hit.getLocation());if(placement==null)return true;
            // Freeze legacy auto-layout centers before appending a freely placed rune.
            var cells=RuneLayout.placed(surface);var frame=RuneLayout.frame(level,pos,hit.getDirection());
            for(int i=0;i<surface.layers().size();i++){var old=surface.layers().get(i);if(!Double.isFinite(old.u())){var delta=cells.get(i).center().subtract(frame.center().center());old.position(delta.dot(frame.center().right()),delta.dot(frame.center().up()));}}
            var added=surface.addLayer(RuneGlyph.id(preset.mode()),preset.mode());
            if(added!=null){added.preset(preset,server.registryAccess());added.position(placement.u(),placement.v());}
            return true;
        }
        if (wand) {
            if (!(level.getBlockEntity(pos) instanceof CrystalNodeBlockEntity) && !isContainer(server, pos, hit.getDirection())) {
                message(player, "Select a rune, crystal, or container face with the wand.");
                return true;
            }
            link(stack, server, new Selection(AnchorAddress.rune(server, pos, hit.getDirection()), null), player);
            return true;
        }
        return false;
    }
    public static boolean isContainer(ServerLevel level, BlockPos pos, Direction face) {
        return level.hasChunkAt(pos) && (com.cappleapple.astralrepository.platform.capabilities.Capabilities.find(level,Capabilities.ItemHandler.BLOCK, pos, face) != null
                || com.cappleapple.astralrepository.platform.capabilities.Capabilities.find(level,Capabilities.FluidHandler.BLOCK, pos, face) != null
                || com.cappleapple.astralrepository.platform.capabilities.Capabilities.find(level,Capabilities.EnergyStorage.BLOCK,pos,face)!=null
                || !com.cappleapple.astralrepository.compat.CompatibilityRegistry.discoverStorage(level,pos,face).isEmpty()
                || !com.cappleapple.astralrepository.compat.CompatibilityRegistry.discoverResources(level,pos,face).isEmpty());
    }
    public static void link(ItemStack wand, ServerLevel level, Selection current, Player player) {
        Selection first = selection(wand);
        if (first == null) {
            select(wand, current);
            message(player, current.layer() == null ? "Container or crystal selected. Select a rune, or another crystal to link networks." : mode(resolveLayer(level, current)) + " Rune selected. Select its target container.");
            return;
        }
        if(first.layer()==null&&current.layer()==null&&first.address().position().dimension().equals(current.address().position().dimension()) && first.address().position().pos().distSqr(current.address().position().pos()) > Math.pow(com.cappleapple.astralrepository.AstralServerConfig.wandBindingRange.get(),2))return;
        if (sameEndpoint(level, first, current)) {
            cancel(wand);
            message(player, "Link selection cleared.");
            return;
        }
        Selection rune = first.layer() != null ? first : current.layer() != null ? current : null;
        if (rune != null) {
            Selection target = rune == first ? current : first;
            ServerLevel runeLevel = level.getServer().getLevel(rune.address().position().dimension());
            if (runeLevel == null || !runeLevel.hasChunkAt(rune.address().position().pos())) {
                cancel(wand); message(player, "The selected rune is no longer loaded. Select it again."); return;
            }
            RuneSurface surface = RuneSurfaces.get(runeLevel, rune.address().position().pos(), face(rune));
            if (surface == null || surface.get(rune.layer()) == null) {
                cancel(wand); message(player, "The selected rune no longer exists. Select it again."); return;
            }
            var result = surface.toggleTarget(rune.layer(), target.address().position(), crystal(level,target)?null:face(target));
            message(player, result.message());
            var data=wand.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            if(result.success()&&!data.getBoolean("RuneTargeting")&&!data.getBoolean("ContainerTargeting"))cancel(wand);
            return;
        }
        if (crystal(level, first) && crystal(level, current)) {
            var a = new AnchorAddress(first.address().position(), null);
            var b = new AnchorAddress(current.address().position(), null);
            var result = NetworkManager.get(level.getServer()).toggleLink(a, b);
            message(player, result.message());
            if (result.success()) cancel(wand);
        } else message(player, "Select a Push or Pull Rune for one end of a container transfer.");
    }
    private static boolean crystal(ServerLevel level, Selection selection) {
        ServerLevel target = level.getServer().getLevel(selection.address().position().dimension());
        return target != null && target.hasChunkAt(selection.address().position().pos()) && target.getBlockEntity(selection.address().position().pos()) instanceof CrystalNodeBlockEntity;
    }
    private static boolean sameEndpoint(ServerLevel level, Selection a, Selection b) {
        if (!Objects.equals(a.layer(), b.layer())) return false;
        if (a.address().equals(b.address())) return true;
        return a.layer() == null && a.address().position().equals(b.address().position()) && crystal(level, a) && crystal(level, b);
    }
    private static RuneLayer resolveLayer(ServerLevel level, Selection selected) {
        if (selected.layer() == null) return null;
        RuneSurface surface = RuneSurfaces.get(level, selected.address().position().pos(), face(selected));
        return surface == null ? null : surface.get(selected.layer());
    }
    private static Direction face(Selection selection) { return selection.address().face() == null ? Direction.UP : selection.address().face(); }
    private static String mode(RuneLayer layer) { return layer == null ? "" : layer.mode() == RuneLayer.Mode.PUSH ? "Push" : "Pull"; }
    private static void select(ItemStack wand, Selection selected) {
        CompoundTag tag = wand.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag endpoint = selected.address().save();
        if (selected.layer() != null) endpoint.putUUID("Layer", selected.layer());
        tag.put(SELECTION, endpoint);
        wand.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    public static Selection selection(ItemStack wand) {
        CompoundTag tag = wand.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(SELECTION)) return null;
        try {
            CompoundTag endpoint = tag.getCompound(SELECTION);
            return new Selection(AnchorAddress.load(endpoint), endpoint.hasUUID("Layer") ? endpoint.getUUID("Layer") : null);
        } catch (IllegalArgumentException invalid) { return null; }
    }
    public static void cancel(ItemStack wand) {
        CompoundTag tag = wand.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(SELECTION);tag.remove("RuneTargeting");tag.remove("ContainerTargeting");
        wand.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    private static void message(Player player, String text) {}
}

