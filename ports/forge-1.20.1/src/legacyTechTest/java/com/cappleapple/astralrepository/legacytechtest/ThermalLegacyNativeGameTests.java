package com.cappleapple.astralrepository.legacytechtest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.crafting.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.*;
import net.minecraft.world.level.material.Fluids;
import static com.cappleapple.astralrepository.legacytechtest.ForgeLegacyTechGameTests.*;
import static com.cappleapple.astralrepository.crafting.NativeBackendReflection.*;

/** Installed Thermal Expansion exercises native grouped slots, tanks, modifiers and actual completion. */
@GameTestHolder("astral_legacy_tech_test")
@PrefixGameTestTemplate(false)
public final class ThermalLegacyNativeGameTests {
    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=1000)
    public static void thermalSawmillCollectsNativeGuaranteedAndChanceCoproducts(GameTestHelper h){
        if(!enabledThermal(h))return;
        BlockPos position=new BlockPos(4,2,4);h.setBlock(position,block("thermal:machine_sawmill"));
        Fixture fixture=fixture(h,List.of(position),new ItemStack(Items.OAK_PLANKS));fixture.insert(new ItemStack(Items.OAK_LOG));
        var backend=new ThermalBackend();var machine=backend.machine(fixture,fixture.at(position));var recipes=backend.recipes(fixture,List.of(machine));
        var recipe=recipes.stream().filter(value -> value.id().equals("thermal:machines/sawmill/sawmill_oak_logs")).findFirst().orElseThrow();
        var sawdust=new ItemKey(item("thermal:sawdust"));h.assertTrue(recipe.guaranteed().getOrDefault(sawdust,0L)==1&&recipe.possible().getOrDefault(sawdust,0L)==2,"Native1.25chance retains guaranteed and possible coproduct quantities");
        var service=request(h,fixture,new ItemStack(Items.OAK_PLANKS),6);tick(h,fixture,service);
        h.succeedWhen(()->{finished(h,fixture,service,new ItemStack(Items.OAK_PLANKS),6);long actual=fixture.snapshot().getOrDefault(sawdust,0L);h.assertTrue(actual>=1&&actual<=2,"One physical sawmill cycle returned every actual sawdust product");h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.OAK_LOG)),0L)==0,"One original log was consumed");});
    }

    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=1000)
    public static void thermalChillerConsumesExactRealFluidInput(GameTestHelper h){
        if(!enabledThermal(h))return;
        BlockPos position=new BlockPos(4,2,4);h.setBlock(position,block("thermal:machine_chiller"));Fixture fixture=fixture(h,List.of(position),new ItemStack(Items.ICE));
        var source=fixture.addTank(new BlockPos(1,2,2));source.tank().fill(new FluidStack(Fluids.WATER,1500),FluidAction.EXECUTE);
        var service=request(h,fixture,new ItemStack(Items.ICE),1);tick(h,fixture,service);
        h.succeedWhen(()->{finished(h,fixture,service,new ItemStack(Items.ICE),1);h.assertTrue(source.tank().getFluidAmount()==500,"Native chiller consumed exactly1000mB physical water");});
    }

    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=1400)
    public static void thermalCrucibleRoutesActualFluidIntermediateToChiller(GameTestHelper h){
        if(!enabledThermal(h))return;
        BlockPos crucible=new BlockPos(4,2,4),chiller=new BlockPos(7,2,4);h.setBlock(crucible,block("thermal:machine_crucible"));h.setBlock(chiller,block("thermal:machine_chiller"));
        Fixture fixture=fixture(h,List.of(crucible,chiller),new ItemStack(Items.ICE));fixture.insert(new ItemStack(Items.SNOW_BLOCK,2));var storage=fixture.addTank(new BlockPos(1,2,2));
        var backend=new ThermalBackend();var nativeCrucible=backend.machine(fixture,fixture.at(crucible));var recipe=backend.recipes(fixture,List.of(nativeCrucible)).stream().filter(value->value.id().equals("thermal:machines/crucible/crucible_snow_block_to_water")).findFirst().orElseThrow();
        h.assertTrue(recipe.guaranteed().getOrDefault(new FluidKey(new FluidStack(Fluids.WATER,1)),0L)==500,"Native crucible recipe produces500mB per snow block");
        var service=request(h,fixture,new ItemStack(Items.ICE),1);tick(h,fixture,service);
        h.succeedWhen(()->{finished(h,fixture,service,new ItemStack(Items.ICE),1);h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.SNOW_BLOCK)),0L)==0,"Two real500mB crucible cycles supply one1000mB chiller cycle");h.assertTrue(storage.tank().getFluidAmount()==0,"The actual intermediate was consumed, with no invented or leftover fluid");});
    }

    @GameTest(templateNamespace="astral_legacy_tech_test",template="empty_workshop",timeoutTicks=1400)
    public static void thermalSmelterUsesNativeCatalystProfileAndProtectsUnrelatedStock(GameTestHelper h){
        if(!enabledThermal(h))return;
        BlockPos position=new BlockPos(4,2,4);h.setBlock(position,block("thermal:machine_smelter"));Object entity=h.getBlockEntity(position),catalyst=nativeField(entity,"catalystSlot");
        call(catalyst,"setItemStack",new ItemStack(Items.SAND));
        Fixture fixture=fixture(h,List.of(position),new ItemStack(Items.IRON_INGOT));fixture.insert(new ItemStack(Items.SAND,2));
        var backend=new ThermalBackend();var machine=backend.machine(fixture,fixture.at(position));var recipe=backend.recipes(fixture,List.of(machine)).stream().filter(value -> value.id().equals("thermal:machines/smelter/smelter_raw_iron")).findFirst().orElseThrow();
        h.assertTrue(recipe.inputs().stream().flatMap(input -> input.alternatives().stream()).noneMatch(key -> key.equals(new ItemKey(new ItemStack(Items.SAND)))),"Native modifier slot remains protected setup rather than a fabricated recipe ingredient");
        h.assertTrue(machine.ports(recipe).stream().noneMatch(port -> port.input()&&port.snapshot().containsKey(new ItemKey(new ItemStack(Items.SAND)))),"Protected native catalyst is excluded from reagent ports");
        fund(fixture,recipe);ItemKey iron=new ItemKey(new ItemStack(Items.IRON_INGOT));int count=Math.toIntExact(recipe.guaranteed().getOrDefault(iron,1L));
        var service=request(h,fixture,new ItemStack(Items.IRON_INGOT),count);tick(h,fixture,service);
        h.succeedWhen(()->{h.assertTrue(service.activeJobs()==0&&service.statuses().stream().anyMatch(status -> status.state().equals("COMPLETE")),"Native catalyst cycle completed: "+service.statuses()+"; physical="+physical(fixture));long produced=fixture.snapshot().getOrDefault(iron,0L);h.assertTrue(produced>=count&&produced<=recipe.possible().getOrDefault(iron,0L),"Actual modified output agrees with native guaranteed/possible quantities");h.assertTrue(fixture.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.SAND)),0L)==2,"Unrelated real network sand was never reserved as setup");ItemStack remaining=(ItemStack)call(catalyst,"getItemStack");h.assertTrue(remaining.isEmpty()||remaining.is(Items.SAND)&&remaining.getCount()==1,"Only native use-chance consumes the one preloaded catalyst");assertConsumed(h,fixture,recipe);});
    }
    private static boolean enabledThermal(GameTestHelper h){if(!Boolean.getBoolean("astral_repository.legacyThermalTest")){h.succeed();return false;}return enabled(h,"thermal_expansion");}
    private static Object nativeField(Object owner,String name){for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(NoSuchFieldException missing){}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}throw new IllegalArgumentException(name);}
    private ThermalLegacyNativeGameTests(){}
}
