package com.cappleapple.astralrepository.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;

/** Keeps armor's normal render passes and shades only its trim-mask quads. */
public final class AstralTrimItemModel extends ForwardingBakedModel {
    private static final Map<BakedModel, BakedModel> CACHE = new IdentityHashMap<>();
    private final Map<BakedModel, List<BakedModel>> passes = new IdentityHashMap<>();

    private AstralTrimItemModel(BakedModel model) { this.wrapped=model; }
    public static BakedModel wrap(BakedModel model) {
        if (model instanceof AstralTrimItemModel || model.isCustomRenderer()) return model;
        return CACHE.computeIfAbsent(model, AstralTrimItemModel::new);
    }
    public static void clearCache() { CACHE.clear(); }

    public void render(net.minecraft.client.renderer.entity.ItemRenderer renderer,ItemStack stack,ItemDisplayContext context,boolean left,PoseStack poses,net.minecraft.client.renderer.MultiBufferSource buffers,int light,int overlay){
        var passesForModel=passes.computeIfAbsent(wrapped,model->List.of(new Pass(model,false),new Pass(model,true)));
        renderer.render(stack,context,left,poses,buffers,light,overlay,passesForModel.get(0));
        var material=AstralPlaneRenderType.ready()?AstralPlaneRenderType.ITEM_TRIM:RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
        renderer.render(stack,context,left,poses,ignored->buffers.getBuffer(material),light,overlay,passesForModel.get(1));
    }
    public static boolean isTrim(BakedQuad quad) {
        var name = quad.getSprite().contents().name();
        return name.getNamespace().equals("minecraft") && name.getPath().startsWith("trims/items/")
                && name.getPath().contains("_trim_");
    }
    private static final class Pass extends ForwardingBakedModel {
        private final boolean trim;
        private final Map<BakedQuad, BakedQuad> remapped = new IdentityHashMap<>();
        Pass(BakedModel model, boolean trim) { this.wrapped=model; this.trim = trim; }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            var result = new ArrayList<BakedQuad>();
            for (var quad : wrapped.getQuads(state, side, random)) {
                if (isTrim(quad) != trim) continue;
                result.add(trim ? remapped.computeIfAbsent(quad, Pass::retexture) : quad);
            }
            return result;
        }
        private static BakedQuad retexture(BakedQuad quad) {
            var old = quad.getSprite();
            String path = old.contents().name().getPath();
            var id = ResourceLocation.withDefaultNamespace(path.substring(0, path.indexOf("_trim_") + 6) + "astral_repository_astral_gem");
            var sprite = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(id);
            int[] vertices = quad.getVertices().clone();
            int stride = vertices.length / 4;
            for (int i = 0; i < 4; i++) {
                int at = i * stride;
                float u = (Float.intBitsToFloat(vertices[at + 4]) - old.getU0()) / (old.getU1() - old.getU0());
                float v = (Float.intBitsToFloat(vertices[at + 5]) - old.getV0()) / (old.getV1() - old.getV0());
                vertices[at + 4] = Float.floatToRawIntBits(sprite.getU0() + u * (sprite.getU1() - sprite.getU0()));
                vertices[at + 5] = Float.floatToRawIntBits(sprite.getV0() + v * (sprite.getV1() - sprite.getV0()));
            }
            return new BakedQuad(vertices, -1, quad.getDirection(), sprite, quad.isShade());
        }
    }
}
