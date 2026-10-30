package com.cappleapple.astralrepository.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/** Client recipe answers depend on tag contents even when stored items are unchanged. */
@EventBusSubscriber(modid="astral_repository", value=Dist.CLIENT)
public final class RecipeTagReloadEvents {
    private RecipeTagReloadEvents() {}

    @SubscribeEvent
    public static void tagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED)
            RecipeTransfers.recipesReloaded();
    }
}
