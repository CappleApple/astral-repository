package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.crafting.NativeEnchantmentTargets;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class NativeEnchantmentTargetGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void nativeExactEnchantTargetsCheckPowerCompatibilityAndDataWithoutRandomRolls(GameTestHelper h) {
        var registry=h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var unbreaking=registry.getHolderOrThrow(Enchantments.UNBREAKING);
        ItemStack book=new ItemStack(Items.BOOK),target=new ItemStack(Items.ENCHANTED_BOOK); target.enchant(unbreaking,3);
        long seed=0x1ee257acL; h.getLevel().random.setSeed(seed); long expected=net.minecraft.util.RandomSource.create(seed).nextLong();
        var valid=NativeEnchantmentTargets.test(h.getLevel(),book,30,target);
        h.assertTrue(valid.supported() && valid.possible(),"A native weighted selection can produce the exact UnbreakingIII book");
        ItemStack impossible=new ItemStack(Items.ENCHANTED_BOOK); impossible.enchant(unbreaking,99);
        var level=NativeEnchantmentTargets.test(h.getLevel(),book,30,impossible);
        h.assertTrue(level.supported() && !level.possible(),"Native maximum-level/power bounds reject an impossible exact target");
        ItemStack sword=new ItemStack(Items.DIAMOND_SWORD),incompatible=sword.copy();
        incompatible.enchant(registry.getHolderOrThrow(Enchantments.SHARPNESS),3); incompatible.enchant(registry.getHolderOrThrow(Enchantments.SMITE),3);
        var pair=NativeEnchantmentTargets.test(h.getLevel(),sword,30,incompatible);
        h.assertTrue(pair.supported() && !pair.possible(),"Native incompatibility rules reject mutually exclusive enchantments");
        ItemStack changed=target.copy(); changed.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("not created by enchanting"));
        h.assertTrue(!NativeEnchantmentTargets.test(h.getLevel(),book,30,changed).possible(),"Native book conversion cannot invent unrelated target components");
        h.assertTrue(h.getLevel().random.nextLong()==expected,"Checking exact enchant targets never rolls world randomness");
        h.assertTrue(book.getCount()==1 && !book.isEnchanted(),"Checking exact targets never changes the real input sample");
        h.succeed();
    }
}
