package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ItemKey;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;

/** Exact target admissibility for the native random enchanting algorithm, without invoking a random source. */
public final class NativeEnchantmentTargets {
    public record Match(boolean supported,boolean possible,String reason) {}
    private record ProfileKey(ItemKey input,int selectedLevel,Object registry,Object options) {}
    private record Choice(Enchantment enchantment,int level) {}
    private record Power(int amount,List<Choice> choices) {}
    private static final Map<ProfileKey,List<Power>> PROFILES=new LinkedHashMap<>(64,0.75F,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<ProfileKey,List<Power>> eldest) { return size()>1024; }
    };
    private NativeEnchantmentTargets() {}
    public static boolean isTarget(ItemStack stack) { return stack.is(Items.ENCHANTED_BOOK) || !EnchantmentHelper.getEnchantments(stack).isEmpty(); }
    public static ItemStack inputForTarget(ItemStack target) {
        if(target.is(Items.ENCHANTED_BOOK)) return new ItemStack(Items.BOOK);
        ItemStack input=target.copyWithCount(1); EnchantmentHelper.setEnchantments(java.util.Map.of(),input); return input;
    }
    public static boolean inputMayProduce(ItemStack input,ItemStack target) {
        ItemStack required=inputForTarget(target);
        return required.is(Items.BOOK)?input.is(Items.BOOK):ItemStack.isSameItemSameTags(input,required);
    }
    public static ItemStack carrierForTarget(ItemStack target) {
        ItemStack book=new ItemStack(Items.ENCHANTED_BOOK); EnchantmentHelper.setEnchantments(EnchantmentHelper.getEnchantments(target),book); return book;
    }
    static String mutableType() { return ""; }
    static Object mutableCopy(Object source) { throw new UnsupportedOperationException("Legacy native enchantments use maps"); }
    public static Match test(ServerLevel level,ItemStack input,int selectedLevel,ItemStack target) {
        return test(level,input,selectedLevel,target,Boolean.FALSE);
    }
    public static Match test(ServerLevel level,ItemStack input,int selectedLevel,ItemStack target,boolean treasure) {
        return test(level,input,selectedLevel,target,(Object)treasure);
    }
    /** Carries the real native call's candidate-set/legacy treasure option without inventing broader eligibility. */
    public static Match test(ServerLevel level,ItemStack input,int selectedLevel,ItemStack target,Object nativeOptions) {
        if(input.isEmpty() || target.isEmpty() || selectedLevel<1) return new Match(true,false,"Native enchanting needs a real item and positive configured level");
        Map<Enchantment,Integer> wanted=EnchantmentHelper.getEnchantments(target);
        if(wanted.keySet().size()>64) return new Match(false,false,"Native exact target exceeds64 enchantments");
        if(!input.is(Items.BOOK) && !EnchantmentHelper.getEnchantments(input).isEmpty())
            return new Match(false,false,"Exact target validation requires the native unenchanted input contract");
        ItemStack constructed=input.is(Items.BOOK)?new ItemStack(Items.ENCHANTED_BOOK):input.copyWithCount(1);
        net.minecraft.nbt.ListTag ordered=target.is(Items.ENCHANTED_BOOK)?EnchantedBookItem.getEnchantments(target):target.getEnchantmentTags();
        if(ordered.size()!=wanted.size()) return new Match(true,false,"Target enchantment NBT is not a distinct native list");
        Set<Enchantment> seen=new HashSet<>();
        for(int index=0;index<ordered.size();index++) {
            var data=ordered.getCompound(index); var id=net.minecraft.resources.ResourceLocation.tryParse(data.getString("id"));
            var enchantment=id==null?null:net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.get(id);
            Integer amount=wanted.get(enchantment);
            if(enchantment==null || amount==null || amount!=data.getInt("lvl") || !seen.add(enchantment)) return new Match(true,false,"Target enchantment NBT is not a distinct native list");
            if(constructed.is(Items.ENCHANTED_BOOK)) EnchantedBookItem.addEnchantment(constructed,new EnchantmentInstance(enchantment,amount)); else constructed.enchant(enchantment,amount);
        }
        if(!ItemStack.isSameItemSameTags(constructed,target)) return new Match(true,false,"The target changes data beyond native enchantment application");
        int value=input.getItem().getEnchantmentValue();
        if(value<1) return new Match(true,wanted.isEmpty(),"The native item has no enchanting value");
        if(value>4096 || selectedLevel>32768) return new Match(false,false,"Native enchantment power exceeds the finite metadata analysis bound");
        var registry=level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Object options=nativeOptions==null?Optional.empty():nativeOptions;
        ProfileKey key=new ProfileKey(new ItemKey(input),selectedLevel,registry,options);
        List<Power> powers=PROFILES.computeIfAbsent(key,ignored->powers(level,input,selectedLevel,value,options));
        for(var power:powers) {
            if(wanted.isEmpty()) { if(power.choices().isEmpty()) return new Match(true,true,"Native selection has an empty eligible set"); continue; }
            List<Choice> desired=new ArrayList<>();
            for(var entry:wanted.entrySet()) {
                var match=power.choices().stream().filter(choice->choice.enchantment().equals(entry.getKey()) && choice.level()==entry.getValue()).findFirst();
                if(match.isEmpty()) { desired.clear(); break; }
                desired.add(match.get());
            }
            if(desired.size()!=wanted.keySet().size()) continue;
            boolean compatible=true;
            for(int first=0;first<desired.size();first++) for(int second=first+1;second<desired.size();second++)
                compatible &= desired.get(first).enchantment().isCompatibleWith(desired.get(second).enchantment());
            if(!compatible) continue;
            // Native selection always picks one. Later successful picks halve the continuation level.
            int remainingPower=power.amount(); for(int index=1;index<desired.size();index++) remainingPower/=2;
            boolean candidatesRemain=power.choices().stream().anyMatch(candidate->desired.stream().allMatch(chosen->candidate.enchantment().isCompatibleWith(chosen.enchantment())));
            if(remainingPower<49 || !candidatesRemain) return new Match(true,true,"Exact target has a positive native selection path at power" + power.amount());
        }
        return new Match(true,false,"No native power/compatibility/termination path produces this exact enchantment set");
    }
    private static List<Power> powers(ServerLevel level,ItemStack input,int selectedLevel,int value,Object nativeOptions) {
        BitSet reachable=new BitSet(); int quarter=value/4;
        // Both integer draws have the same finite range, so their sums cover every integer in0..2q.
        float greatestDraw=1.0F-0x1.0p-24F;
        float greatestVariance=(greatestDraw+greatestDraw-1.0F)*0.15F;
        for(int sum=0;sum<=quarter*2;sum++) {
            int base=selectedLevel+1+sum;
            int low=Math.max(1,Math.round((float)base+(float)base*-0.15F));
            int high=Math.max(1,Math.round((float)base+(float)base*greatestVariance));
            reachable.set(low,high+1);
        }
        if (!(nativeOptions instanceof Boolean)) throw new IllegalArgumentException("Native legacy enchanting requires its actual treasure flag");
        List<Power> result=new ArrayList<>();
        for(int power=reachable.nextSetBit(0);power>=0;power=reachable.nextSetBit(power+1)) {
            List<Choice> choices=EnchantmentHelper.getAvailableEnchantmentResults(power,input,(Boolean)nativeOptions).stream()
                    .filter(entry->entry.getWeight().asInt()>0).map(entry->new Choice(entry.enchantment,entry.level)).toList();
            result.add(new Power(power,choices));
        }
        return List.copyOf(result);
    }
}
