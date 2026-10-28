package com.cappleapple.astralrepository.porttest;

import com.cappleapple.astralrepository.client.AstralPlaneRenderType;
import com.cappleapple.astralrepository.client.CrystalModelRenderer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.model.data.ModelData;

/** Checks actual Forge-baked OBJ quads used by both placed nodes and their items. */
final class NodeMaterialClientCheck {
    static void verify() {
        var manager = Minecraft.getInstance().getModelManager();
        for (String name : java.util.List.of("storage_nexus", "seed_storage_crystal", "relay_crystal", "power_node",
                "moon_storage_crystal", "star_storage_crystal", "remote_crystal", "gateway_crystal")) {
            var model = manager.getModel(CrystalModelRenderer.modelLocation(name));
            if (model == manager.getMissingModel()) throw new AssertionError("Missing node model: " + name);
            verifyModel(name, "item", model);
        }
        for (var node : com.cappleapple.astralrepository.content.AstralContent.NODES) {
            var state = node.get().defaultBlockState();
            verifyModel(node.getId().getPath(), "placed", Minecraft.getInstance().getBlockRenderer().getBlockModel(state));
        }
    }
    private static void verifyModel(String mesh, String usage, net.minecraft.client.resources.model.BakedModel model) {
        String name = mesh + " " + usage;
        int crystals = 0, ordinary = 0, accents = 0;
        var random = RandomSource.create(42);
        for (int face = 0; face <= Direction.values().length; face++) {
            var direction = face == Direction.values().length ? null : Direction.values()[face];
            random.setSeed(42);
            for (var quad : model.getQuads(null, direction, random, ModelData.EMPTY, null)) {
                String texture = quad.getSprite().contents().name().getPath();
                int expected = switch (texture) {
                    case "block/node_crystal" -> 0;
                    case "block/node_channel" -> 1;
                    case "block/node_stone", "block/node_trim" -> -1;
                    default -> throw new AssertionError("Unexpected node texture: " + texture);
                };
                if (quad.getTintIndex() != expected)
                    throw new AssertionError(name + " " + texture + ": expected tint " + expected + ", got " + quad.getTintIndex());
                var material = expected == 0 ? AstralPlaneRenderType.ASTRAL_OVERLAY
                        : RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
                if (CrystalModelRenderer.materialFor(quad) != material)
                    throw new AssertionError(name + " routes " + texture + " to the wrong shader");
                if (CrystalModelRenderer.isChannelAccent(quad) != (expected == 1))
                    throw new AssertionError(name + " has incorrect channel tint routing");
                if (expected == 0) crystals++; else if (expected == 1) accents++; else ordinary++;
            }
        }
        int[] expectedFaces = sourceFaces(mesh);
        if (crystals != expectedFaces[0] || ordinary != expectedFaces[1] || accents != expectedFaces[2])
            throw new AssertionError("Lost OBJ faces for " + name + ": crystal=" + crystals + ", ordinary=" + ordinary
                    + ", channel=" + accents + "; expected " + java.util.Arrays.toString(expectedFaces));
        LogUtils.getLogger().info("FORGE_NODE_MATERIAL_PASS {} crystal={} ordinary={} channel={}", name, crystals, ordinary, accents);
    }
    private static int[] sourceFaces(String mesh) {
        int[] counts = new int[3];
        var location = new net.minecraft.resources.ResourceLocation("astral_repository", "models/block/node_meshes/" + mesh + ".obj");
        try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(location)) {
            String material = "";
            for (String line; (line = reader.readLine()) != null;) {
                if (line.startsWith("usemtl ")) material = line.substring(7);
                else if (line.startsWith("f ")) {
                    int bucket = switch (material) {
                        case "node_crystal" -> 0;
                        case "node_stone", "node_trim" -> 1;
                        case "node_channel" -> 2;
                        default -> throw new AssertionError("Unknown OBJ material " + material);
                    };
                    counts[bucket]++;
                }
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read " + location, failure);
        }
        return counts;
    }
    private NodeMaterialClientCheck() {}
}
