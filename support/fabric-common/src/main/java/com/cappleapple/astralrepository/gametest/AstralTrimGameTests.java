package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.AstralTrims;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository") @PrefixGameTestTemplate(false)
public final class AstralTrimGameTests {
    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void astralGemUsesVanillaSmithingPatterns(GameTestHelper h) {
        var level=h.getLevel();
        var gem=new ItemStack(AstralContent.ASTRAL_GEM.get());
        h.assertTrue(gem.is(ItemTags.TRIM_MATERIALS), "Gem is absent from smithing additions");
        var material=level.registryAccess().registryOrThrow(Registries.TRIM_MATERIAL).getHolderOrThrow(AstralTrims.MATERIAL);
        h.assertTrue(material.value().ingredient().value()==gem.getItem(), "Trim material has wrong ingredient");
        int patterns=0;
        var registry=level.registryAccess().registryOrThrow(Registries.TRIM_PATTERN);
        for(var key:registry.registryKeySet()) {
            if(!key.location().getNamespace().equals("minecraft"))continue;
            patterns++;
            var pattern=registry.getHolderOrThrow(key);
            var template=new ItemStack(BuiltInRegistries.ITEM.get(key.location().withSuffix("_armor_trim_smithing_template")));
            for(var item:java.util.List.of(Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS,
                    Items.NETHERITE_CHESTPLATE,Items.LEATHER_CHESTPLATE)) {
                var armor=new ItemStack(item);armor.setDamageValue(7);
                var input=new SmithingRecipeInput(template,armor,gem);
                var recipe=level.getRecipeManager().getRecipeFor(RecipeType.SMITHING,input,level).orElseThrow();
                var result=recipe.value().assemble(input,level.registryAccess());
                var trim=result.get(DataComponents.TRIM);
                h.assertTrue(!result.isEmpty()&&result.is(item)&&result.getDamageValue()==7,"Smithing lost armor or damage");
                h.assertTrue(AstralTrims.isAstral(trim)&&trim.pattern().equals(pattern),"Smithing chose wrong trim");
                h.assertTrue(armor.get(DataComponents.TRIM)==null&&gem.getCount()==1,"Preview mutated inputs");
                var duplicate=new SmithingRecipeInput(template,result,gem);
                h.assertTrue(recipe.value().assemble(duplicate,level.registryAccess()).isEmpty(),"Duplicate trim should produce no result");
            }
        }
        h.assertTrue(patterns==18,"Expected all eighteen vanilla trim patterns");
        h.succeed();
    }
}
