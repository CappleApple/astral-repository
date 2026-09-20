package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.mixin.ItemRenderStateAccessor;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;

/** Replaces only trim materials after the normal item model resolves its layers. */
public final class AstralTrimItemModel {
    private static final Map<BakedQuad,BakedQuad> CACHE = new IdentityHashMap<>();
    public static void clearCache() { CACHE.clear(); }
    public static boolean isTrim(BakedQuad quad) {
        var path = quad.materialInfo().sprite().contents().name().getPath();
        return path.startsWith("trims/items/") || path.startsWith("item/trim/");
    }
    public static void apply(ItemStackRenderState state) {
        var access = (ItemRenderStateAccessor) state;
        for (int i=0; i<access.astral$activeLayerCount(); i++) {
            var quads = access.astral$layers()[i].prepareQuadList();
            quads.replaceAll(quad -> isTrim(quad) ? CACHE.computeIfAbsent(quad, AstralTrimItemModel::material) : quad);
        }
    }
    private static BakedQuad material(BakedQuad quad) {
        var info = quad.materialInfo(); var old=info.sprite();
        String path=old.contents().name().getPath();int marker=path.indexOf("_trim_");
        var sprite=old;
        if(marker>=0) {
            var id=net.minecraft.resources.Identifier.withDefaultNamespace(path.substring(0,marker+6)+"astral_repository_astral_gem");
            var replacement=net.minecraft.client.Minecraft.getInstance().getAtlasManager().get(new net.minecraft.client.resources.model.sprite.SpriteId(old.atlasLocation(),id));
            if(!replacement.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()))sprite=replacement;
        }
        var replacement = new BakedQuad.MaterialInfo(sprite, info.layer(), AstralPlaneRenderType.withAtlas(AstralPlaneRenderType.ITEM_TRIM,sprite.atlasLocation()),
            -1, info.shade(), info.lightEmission());
        long[] uv=new long[4];
        for(int i=0;i<4;i++) {
            float u=(net.minecraft.client.model.geom.builders.UVPair.unpackU(quad.packedUV(i))-old.getU0())/(old.getU1()-old.getU0());
            float v=(net.minecraft.client.model.geom.builders.UVPair.unpackV(quad.packedUV(i))-old.getV0())/(old.getV1()-old.getV0());
            uv[i]=net.minecraft.client.model.geom.builders.UVPair.pack(sprite.getU(u),sprite.getV(v));
        }
        return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
            uv[0],uv[1],uv[2],uv[3],quad.direction(),replacement);
    }
    private AstralTrimItemModel() {}
}
