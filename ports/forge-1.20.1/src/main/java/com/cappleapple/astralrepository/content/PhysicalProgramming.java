package com.cappleapple.astralrepository.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Crystal attunement; routing inscriptions and explicit links are handled on their actual endpoints. */
public final class PhysicalProgramming {
    private PhysicalProgramming() {}
    public static boolean use(ItemStack stack,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(stack.is(AstralContent.ATTUNEMENT_WAND.get())||RuneGlyph.isRune(stack)||RuneGlyph.isLegacyRune(stack))return RuneProgramming.use(stack,level,pos,player,hand,hit);
        if(!(level.getBlockEntity(pos) instanceof CrystalNodeBlockEntity node)||stack.isEmpty())return false;
        boolean special=stack.getItem() instanceof DyeItem||stack.is(Items.WATER_BUCKET)||stack.is(AstralContent.ASTRAL_NEXUS.get())||stack.is(AstralContent.DIMENSIONAL_ATTUNEMENT.get())||stack.is(AstralContent.RANGE_ATTUNEMENT.get())||stack.is(AstralContent.MOON_ATTUNEMENT.get())||stack.is(AstralContent.STAR_ATTUNEMENT.get());
        if(!special)return false;
        if(level.isClientSide)return true;
        if(stack.is(AstralContent.DIMENSIONAL_ATTUNEMENT.get())){
            if(node.kind()==NodeKind.RELAY&&node.longRange()&&!node.dimensional()){node.setDimensional(true);if(!player.isCreative())stack.shrink(1);}
            return true;
        }
        if(stack.is(AstralContent.RANGE_ATTUNEMENT.get())){if(node.kind()==NodeKind.RELAY&&!node.longRange()){node.upgradeRange();if(!player.isCreative())stack.shrink(1);}return true;}
        if(stack.is(AstralContent.MOON_ATTUNEMENT.get())||stack.is(AstralContent.STAR_ATTUNEMENT.get())){if(node.upgradeStorage(stack.is(AstralContent.MOON_ATTUNEMENT.get())?2:3)&&!player.isCreative())stack.shrink(1);return true;}
        if(stack.getItem() instanceof DyeItem dye)node.setChannel(dye.getDyeColor().getId());
        else if(stack.is(Items.WATER_BUCKET))node.setChannel(-1);
        else if(stack.is(AstralContent.ASTRAL_NEXUS.get())){
            if(node.kind()!=NodeKind.NEXUS){message(player,"Bind an Astral Nexus to a Storage Nexus.");return true;}
            bind(stack,"BoundNexus",GlobalPos.of(level.dimension(),pos));message(player,"Astral Nexus bound.");return true;
        }
        describe(player,node);return true;
    }
    public static void bind(ItemStack stack,String key,GlobalPos pos) {
        CompoundTag data=com.cappleapple.astralrepository.platform.Backport.customData(stack);
        GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE,pos).result().ifPresent(value->data.put(key,value));stack.setTag(data);
    }
    public static GlobalPos readBinding(ItemStack stack,String key) {
        CompoundTag data=com.cappleapple.astralrepository.platform.Backport.customData(stack);
        return data.contains(key)?GlobalPos.CODEC.parse(NbtOps.INSTANCE,data.get(key)).result().orElse(null):null;
    }
    public static boolean hasRemoteAttunement(ItemStack stack){return com.cappleapple.astralrepository.platform.Backport.customData(stack).getBoolean("DimensionalRemote");}
    public static void describe(Player player,CrystalNodeBlockEntity node){String color=node.channel()<0?"neutral":DyeColor.byId(node.channel()).getName();message(player,node.kind()+" | "+color+" | Use the wand to link endpoints. Apply routing runes to container faces.");}
    private static void message(Player player,String text){}
}