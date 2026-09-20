package com.cappleapple.astralrepository.client;

import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.*;

/** Baked geometry shared by block-entity and special-item renderers. */
public final class AstralModels {
    private static final Map<Identifier, Key> KEYS = new LinkedHashMap<>();
    private AstralModels() {}
    public record Key(Identifier id, StandaloneModelKey<BlockStateModelPart> baked) {
        public static Key standalone(Identifier id) { return KEYS.computeIfAbsent(id,k->new Key(k,new StandaloneModelKey<>(k::toString))); }
    }
    public static void register(ModelEvent.RegisterStandalone event, Key key) {
        event.register(key.baked(), new SimpleUnbakedStandaloneModel<>(key.id(), (model, baker, name) -> {
            var textures = model.getTopTextureSlots();
            // These parts are drawn by our special renderer, which binds each quad's atlas.
            // The vanilla block-only factory rejects otherwise valid item-atlas geometry.
            return new net.minecraft.client.resources.model.SimpleModelWrapper(
                    model.bakeTopGeometry(textures, baker, BlockModelRotation.IDENTITY),
                    model.getTopAmbientOcclusion(), model.resolveParticleMaterial(textures, baker));
        }));
    }
    public static Model get(Key key) {
        return new Model(List.of(Minecraft.getInstance().getModelManager().getStandaloneModel(key.baked())));
    }
    public static Model block(BlockState state) {
        var parts=new ArrayList<BlockStateModelPart>();
        Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(42),parts);
        return new Model(List.copyOf(parts));
    }
    public record Model(List<BlockStateModelPart> parts) {
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            var result=new ArrayList<BakedQuad>();
            for(var part:parts)result.addAll(part.getQuads(side));
            return result;
        }
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, Object data,Object renderType) { return getQuads(state,side,random); }
        public TextureAtlasSprite getParticleIcon() { return parts.getFirst().particleMaterial().sprite(); }
    }
}
