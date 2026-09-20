package com.cappleapple.astralrepository.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;


/** Shared world/item draw of the baked node mesh, partitioned by its OBJ material tint indices. */
public final class CrystalModelRenderer {
    private static final float[] BRIGHTNESS = {1, 1, 1, 1};
    private static final Direction[] DIRECTIONS = Direction.values();

    public static ModelResourceLocation modelLocation(Block block) {
        var id = BuiltInRegistries.BLOCK.getKey(block);
        return com.cappleapple.astralrepository.platform.client.FabricModels.standalone(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "item/" + id.getPath() + "_geometry"));
    }

    public static ModelResourceLocation modelLocation(String name){return com.cappleapple.astralrepository.platform.client.FabricModels.standalone(ResourceLocation.fromNamespaceAndPath("astral_repository","item/"+name+"_geometry"));}
    public static String upgradedModel(com.cappleapple.astralrepository.content.NodeKind kind,int tier,boolean range,boolean dimensional){
        if(kind==com.cappleapple.astralrepository.content.NodeKind.STORAGE&&tier>=2)return tier==3?"star_storage_crystal":"moon_storage_crystal";
        if(kind==com.cappleapple.astralrepository.content.NodeKind.RELAY&&range)return dimensional?"gateway_crystal":"remote_crystal";return null;
    }
    public static boolean isCrystal(BakedQuad quad) { return quad.isTinted() && quad.getTintIndex() == 0; }
    public static boolean isChannelAccent(BakedQuad quad) { return quad.isTinted() && quad.getTintIndex() == 1; }

    public static RenderType materialFor(BakedQuad quad) {
        return isCrystal(quad) && AstralPlaneRenderType.ready()
                ? AstralPlaneRenderType.ASTRAL_OVERLAY : RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
    }

    public static int channel(ItemStack stack) {
        var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) return -1;
        var tag = data.copyTag();
        return tag.contains("Channel", Tag.TAG_INT) ? Math.clamp(tag.getInt("Channel"), -1, 15) : -1;
    }

    public static void render(PoseStack poses, MultiBufferSource buffers, @Nullable BlockState state,
            BakedModel model, int channel, int light, int overlay) {
        int color = channel < 0 ? 0x65D6CF : DyeColor.byId(channel).getTextureDiffuseColor();
        float red = ((color >> 16) & 255) / 255f;
        float green = ((color >> 8) & 255) / 255f;
        float blue = (color & 255) / 255f;
        int[] lightmap = {light, light, light, light};
        var random = RandomSource.create(42);
        for (int face = 0; face <= DIRECTIONS.length; face++) {
            var direction = face == DIRECTIONS.length ? null : DIRECTIONS[face];
            random.setSeed(42);
            for (var quad : model.getQuads(state, direction, random)) {
                boolean accent = isChannelAccent(quad);
                // Preserve the OBJ's per-vertex colors and non-axis-aligned facet normals.
                buffers.getBuffer(materialFor(quad)).putBulkData(poses.last(), quad, BRIGHTNESS,
                        accent ? red : 1, accent ? green : 1, accent ? blue : 1, 1, lightmap, overlay, true);
            }
        }
    }

    private CrystalModelRenderer() {}
}
