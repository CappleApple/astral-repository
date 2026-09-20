package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.api.ResourceKey;
import com.cappleapple.astralrepository.api.ResourceProvider;
import com.cappleapple.astralrepository.api.StorageProvider;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;

/** Physical members of an aggregate rune endpoint; operations remain on the server thread. */
public final class RuneRouting {
    /** Network policy retained with saved flights, separately from physical storage identity. */
    public interface Policy { AnchorAddress policyAnchor(); }
    public interface ItemPolicy extends StorageProvider,Policy { long insertionLimit(ItemKey key); }
    public interface ResourcePolicy extends ResourceProvider,Policy { long insertionLimit(ResourceKey key); }
    public record ItemEndpoint(GlobalPos position,Direction side,StorageProvider provider) {}
    public record ResourceEndpoint(GlobalPos position,Direction side,ResourceProvider provider) {}
    public interface Items extends StorageProvider {
        List<ItemEndpoint> sources(ItemKey key);
        List<ItemEndpoint> destinations(ItemKey key);
    }
    public interface Resources extends ResourceProvider {
        List<ResourceEndpoint> sources(ResourceKey key);
        List<ResourceEndpoint> destinations(ResourceKey key);
    }
    private RuneRouting() {}
}
