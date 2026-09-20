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
import net.minecraftforge.client.model.BakedModelWrapper;

/** Keeps armor's normal render passes and shades only its trim-mask quads. */
public final class AstralTrimItemModel extends BakedModelWrapper<BakedModel> {
    private static final Map<BakedModel, BakedModel> CACHE = new IdentityHashMap<>();
    private final Map<BakedModel, List<BakedModel>> passes = new IdentityHashMap<>();

    private AstralTrimItemModel(BakedModel model) { super(model); }
    public static BakedModel wrap(BakedModel model) {
        if (model instanceof AstralTrimItemModel || model.isCustomRenderer()) return model;
        return CACHE.computeIfAbsent(model, AstralTrimItemModel::new);
    }
    public static void clearCache() { CACHE.clear(); }

    @Override public BakedModel applyTransform(ItemDisplayContext context, PoseStack poses, boolean left) {
        BakedModel transformed = originalModel.applyTransform(context, poses, left);
        return transformed == originalModel ? this : wrap(transformed);
    }
    @Override public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
        var result = new ArrayList<BakedModel>();
        for (var pass : originalModel.getRenderPasses(stack, fabulous))
            result.addAll(passes.computeIfAbsent(pass, model -> List.of(new Pass(model, false), new Pass(model, true))));
        return result;
    }
    public static boolean isTrim(BakedQuad quad) {
        var name = quad.getSprite().contents().name();
        return name.getNamespace().equals("minecraft") && name.getPath().startsWith("trims/items/")
                && name.getPath().contains("_trim_");
    }
    private static final class Pass extends BakedModelWrapper<BakedModel> {
        private final boolean trim;
        private final Map<BakedQuad, BakedQuad> remapped = new IdentityHashMap<>();
        Pass(BakedModel model, boolean trim) { super(model); this.trim = trim; }
        @Override public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return trim ? List.of(AstralPlaneRenderType.ready() ? AstralPlaneRenderType.ITEM_TRIM
                    : RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS)) : originalModel.getRenderTypes(stack, fabulous);
        }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            var result = new ArrayList<BakedQuad>();
            for (var quad : originalModel.getQuads(state, side, random)) {
                if (isTrim(quad) != trim) continue;
                result.add(trim ? remapped.computeIfAbsent(quad, Pass::retexture) : quad);
            }
            return result;
        }
        private static BakedQuad retexture(BakedQuad quad) {
            var old = quad.getSprite();
            String path = old.contents().name().getPath();
            var id = new ResourceLocation(path.substring(0, path.indexOf("_trim_") + 6) + "astral_repository_astral_gem");
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
            return new BakedQuad(vertices, -1, quad.getDirection(), sprite, quad.isShade(), quad.hasAmbientOcclusion());
        }
    }
}
