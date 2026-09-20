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
import com.cappleapple.astralrepository.platform.client.event.ModelEvent;
import net.fabricmc.fabric.api.client.model.loading.v1.*;

/** Baked geometry shared by block-entity and special-item renderers. */
public final class AstralModels {
    private static final Map<Identifier, Key> KEYS = new LinkedHashMap<>();
    private AstralModels() {}
    public record Key(Identifier id, ExtraModelKey<Model> baked) {
        public static Key standalone(Identifier id) { return KEYS.computeIfAbsent(id,k->new Key(k,ExtraModelKey.create(k::toString))); }
    }
    public static void register(ModelEvent.RegisterStandalone event, Key key) {
        event.context().addModel(key.baked(),new SimpleUnbakedExtraModel<>(key.id(),(resolved,baker)->{
            var textures=resolved.getTopTextureSlots();
            var quads=resolved.bakeTopGeometry(textures,baker,new ModelState(){});
            var particle=resolved.resolveParticleMaterial(textures,baker);
            return new Model(List.of(new BlockStateModelPart(){
                @Override public List<BakedQuad> getQuads(Direction side){return quads.getQuads(side);}
                @Override public boolean useAmbientOcclusion(){return resolved.getTopAmbientOcclusion();}
                @Override public net.minecraft.client.resources.model.sprite.Material.Baked particleMaterial(){return particle;}
                @Override public int materialFlags(){return quads.materialFlags();}
            }));
        }));
    }
    public static Model get(Key key) {
        return ((FabricModelManager)Minecraft.getInstance().getModelManager()).getModel(key.baked());
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
