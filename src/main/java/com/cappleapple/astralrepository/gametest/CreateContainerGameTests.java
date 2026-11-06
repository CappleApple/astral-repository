package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.crafting.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class CreateContainerGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void nativeContainerCatalogConservesBucketsAndExactPotionFluidWithoutRollingWorldRandom(GameTestHelper h) {
        if(!Boolean.getBoolean("astral_repository.genericTechTest")) { h.succeed(); return; }
        h.assertTrue(ModList.get().isLoaded("create"),"The actual native Create runtime is installed");
        var potion=PotionContents.createItemStack(Items.SPLASH_POTION,Potions.STRONG_HEALING);
        var potionKey=new ItemKey(potion); var bucket=new ItemKey(new ItemStack(Items.BUCKET)); var waterBucket=new ItemKey(new ItemStack(Items.WATER_BUCKET));
        FluidKey water=new FluidKey(new FluidStack(Fluids.WATER,1));
        var stock=Map.<ResourceKey,Long>of(potionKey,1L,bucket,1L,waterBucket,1L,water,1000L);
        var helper=new CreateContainerRecipes();
        long seed=0x3f117aebL; h.getLevel().random.setSeed(seed); long expected=net.minecraft.util.RandomSource.create(seed).nextLong();
        var catalog=helper.discover(h.getLevel(),stock,List.of());
        h.assertTrue(h.getLevel().random.nextLong()==expected,"Native metadata previews never advance the world's random generator");
        h.assertTrue(catalog.complete(),"The current finite candidate domain was completely checked");
        var fill=catalog.cycles().stream().filter(value->value.kind()==CreateContainerRecipes.Kind.FILL && value.itemInput().equals(bucket)
                && water.equals(value.fluidInput()) && value.outputs().containsKey(waterBucket)).findFirst().orElseThrow();
        h.assertTrue(fill.fluidCount()==1000 && fill.outputs().get(waterBucket)==1,"The native spout fills one bucket with exactly1000 mB");
        var empty=catalog.cycles().stream().filter(value->value.kind()==CreateContainerRecipes.Kind.EMPTY && value.itemInput().equals(waterBucket)).findFirst().orElseThrow();
        h.assertTrue(empty.outputs().getOrDefault(water,0L)==1000 && empty.outputs().getOrDefault(bucket,0L)==1,"The native drain returns both actual bucket contents and its empty container");
        var emptyPotion=catalog.cycles().stream().filter(value->value.kind()==CreateContainerRecipes.Kind.EMPTY && value.itemInput().equals(potionKey)).findFirst().orElseThrow();
        FluidKey potionFluid=emptyPotion.outputs().keySet().stream().filter(FluidKey.class::isInstance).map(FluidKey.class::cast).findFirst().orElseThrow();
        var refill=catalog.cycles().stream().filter(value->value.kind()==CreateContainerRecipes.Kind.FILL && potionFluid.equals(value.fluidInput())
                && value.outputs().getOrDefault(potionKey,0L)==1).findFirst().orElseThrow();
        h.assertTrue(refill.fluidCount()==emptyPotion.outputs().get(potionFluid),"The native refill preserves potion strength, bottle type and exact fluid amount");
        h.assertTrue(new ItemKey(potion).equals(potionKey) && potion.getCount()==1,"Preview never mutates the supplied physical stock sample");
        var again=helper.discover(h.getLevel(),stock,List.of());
        h.assertTrue(again.cycles().equals(catalog.cycles()),"Cached accepted and rejected exact pairs preserve native output identities");
        h.succeed();
    }
}
