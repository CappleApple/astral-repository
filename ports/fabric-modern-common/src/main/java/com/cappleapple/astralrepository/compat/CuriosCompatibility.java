package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.content.AstralContent;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Optional API references remain behind a bridge that is loaded only with Curios installed. */
@EventBusSubscriber(modid=AstralRepository.MOD_ID)
public final class CuriosCompatibility {
    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("curios")) event.enqueueWork(ApiBridge::register);
    }

    public static boolean isWearingGoggles(LivingEntity entity) {
        return ModList.get().isLoaded("curios") && ApiBridge.isWearingGoggles(entity);
    }

    private static final class ApiBridge {
        private static void register() {
            top.theillusivec4.curios.api.CuriosApi.registerCurio(AstralContent.RESONANCE_GOGGLES.get(),
                    new top.theillusivec4.curios.api.type.capability.ICurioItem() {});
        }

        private static boolean isWearingGoggles(LivingEntity entity) {
            // The predicate overload sees equipment changes immediately and excludes inactive/cosmetic slots.
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity)
                    .flatMap(inventory -> inventory.findFirstCurio(stack -> stack.is(AstralContent.RESONANCE_GOGGLES.get())))
                    .isPresent();
        }
    }

    private CuriosCompatibility() {}
}
