package com.cappleapple.astralrepository.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client recipe answers depend on tag contents even when stored items are unchanged. */
@Mod.EventBusSubscriber(modid="astral_repository", value=Dist.CLIENT)
public final class RecipeTagReloadEvents {
    private RecipeTagReloadEvents() {}

    @SubscribeEvent
    public static void tagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED)
            RecipeTransfers.recipesReloaded();
    }
}
