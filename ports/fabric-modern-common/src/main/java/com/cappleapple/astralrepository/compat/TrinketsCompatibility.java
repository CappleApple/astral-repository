package com.cappleapple.astralrepository.compat;
import com.cappleapple.astralrepository.content.AstralContent;import net.minecraft.world.entity.LivingEntity;
/** Optional Fabric equipment integration, loaded only when Trinkets is installed. */
public final class TrinketsCompatibility {
 public static boolean isWearingGoggles(LivingEntity entity){if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("trinkets"))return false;var component=(java.util.Optional<?>)OptionalApi.call(null,"dev.emi.trinkets.api.TrinketsApi","getTrinketComponent",new Class<?>[]{LivingEntity.class},entity);return component.isPresent()&&(Boolean)OptionalApi.call(component.get(),"dev.emi.trinkets.api.TrinketComponent","isEquipped",new Class<?>[]{java.util.function.Predicate.class},(java.util.function.Predicate<net.minecraft.world.item.ItemStack>)stack->stack.is(AstralContent.RESONANCE_GOGGLES.get()));}
 private TrinketsCompatibility(){}
}
