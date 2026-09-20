package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.mixin.ItemRenderStateAccessor;
import com.cappleapple.astralrepository.mixin.ItemLayerStateAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Bounded cached GUI silhouettes; item models resolve only within the per-frame budget. */
final class TransferItemSprites {
    private static final int MAX_MESHES=256, MAX_IDENTITIES=8192, MAX_QUADS=8;
    private record Quad(float[] coordinates, int[] colors, Identifier atlas) {}
    private record Mesh(List<Quad> quads) {}
    private record Base(ItemStackRenderState model, Mesh fallback) {}
    private static final class Lookup {
        final ItemKey key; Base base;
        Lookup(ItemStack stack) { key=new ItemKey(stack); }
    }
    private static final Map<ItemStack,Lookup> IDENTITIES=new IdentityHashMap<>();
    private static final ArrayDeque<ItemStack> IDENTITY_ORDER=new ArrayDeque<>();
    private static final Map<ItemKey,Mesh> MESHES=new LinkedHashMap<>(256,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<ItemKey,Mesh> entry) { return size()>MAX_MESHES; }
    };
    private static final Map<ItemKey,Base> BASES=new LinkedHashMap<>(256,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<ItemKey,Base> entry) { return size()>MAX_MESHES; }
    };
    private static final Set<RenderType> usedMaterials=new LinkedHashSet<>();
    private static int buildsRemaining,resolutionsRemaining,modelResolutions,renderedVertices,identitiesRemaining;
    static void beginFrame() { identitiesRemaining=128; buildsRemaining=4; resolutionsRemaining=16; modelResolutions=renderedVertices=0; usedMaterials.clear(); }
    static int modelResolutions() { return modelResolutions; }
    static int renderedVertices() { return renderedVertices; }
    static void clearCache() { MESHES.clear(); BASES.clear(); IDENTITIES.clear(); IDENTITY_ORDER.clear(); usedMaterials.clear(); }
    static void flush(AstralBufferSource buffers) { for(var type:usedMaterials)buffers.endBatch(type); usedMaterials.clear(); }
    static boolean render(ItemStack stack,Vec3 position,PoseStack poses,AstralBufferSource buffers,Quaternionf camera,float size) {
        if(stack.isEmpty())return false; var mesh=mesh(stack); if(mesh==null)return false;
        poses.pushPose(); poses.translate(position.x,position.y,position.z); poses.mulPose(camera);
        float scale=size*2;
        for(var quad:mesh.quads) {
            var type=ResourceTransferRenderer.itemMaterial(quad.atlas); usedMaterials.add(type);
            var vertices=buffers.getBuffer(type);
            for(int i=0;i<4;i++) { int at=i*5; vertices.addVertex(poses.last().pose(),quad.coordinates[at]*scale,quad.coordinates[at+1]*scale,quad.coordinates[at+2]*scale)
                .setColor(quad.colors[i]).setUv(quad.coordinates[at+3],quad.coordinates[at+4]).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(poses.last(),0,0,1); }
            renderedVertices+=4;
        }
        poses.popPose(); return true;
    }
    private static Mesh mesh(ItemStack stack) {
        var lookup=IDENTITIES.get(stack);
        if(lookup==null) {
            if(identitiesRemaining--<=0)return null;
            lookup=new Lookup(stack); IDENTITIES.put(stack,lookup); IDENTITY_ORDER.addLast(stack);
            if(IDENTITY_ORDER.size()>MAX_IDENTITIES)IDENTITIES.remove(IDENTITY_ORDER.removeFirst());
        }
        var cached=MESHES.get(lookup.key); if(cached!=null)return cached;
        if(lookup.base==null) {
            var base=BASES.get(lookup.key);
            if(base==null) {
                if(resolutionsRemaining<=0)return null; resolutionsRemaining--; modelResolutions++;
                var mc=Minecraft.getInstance(); var state=new ItemStackRenderState();
                mc.getItemModelResolver().updateForTopItem(state,stack,ItemDisplayContext.GUI,mc.level,mc.player,0);
                base=new Base(state,fallback(stack,state)); BASES.put(lookup.key,base);
            }
            lookup.base=base;
        }
        if(buildsRemaining>0) {
            buildsRemaining--;
            try { cached=flatten(lookup.base.model); } catch(RuntimeException ignored) { /* Custom model fallback retains its particle icon. */ }
            if(cached==null)cached=lookup.base.fallback;
            if(cached!=null)MESHES.put(lookup.key,cached);
            return cached;
        }
        return lookup.base.fallback;
    }
    private static Mesh flatten(ItemStackRenderState model) {
        var state=(ItemRenderStateAccessor)model; var output=new ArrayList<Quad>(); int examined=0;
        for(int layerIndex=0;layerIndex<state.astral$activeLayerCount();layerIndex++) {
            var layer=state.astral$layers()[layerIndex]; var access=(ItemLayerStateAccessor)layer;
            if(access.astral$specialRenderer()!=null)return null;
            var pose=new PoseStack.Pose(); access.astral$applyTransform(pose);
            for(var quad:layer.prepareQuadList()) {
                if(++examined>512)return null;
                var positions=new Vector3f[4];
                for(int i=0;i<4;i++)positions[i]=pose.pose().transformPosition(new Vector3f(quad.position(i)));
                var normal=new Vector3f(positions[1]).sub(positions[0]).cross(new Vector3f(positions[2]).sub(positions[0]));
                if(normal.z<=1e-7f)continue;
                if(output.size()>=MAX_QUADS)return null;
                var info=quad.materialInfo(); int tint=info.tintIndex()>=0&&info.tintIndex()<layer.tintLayers().size()?layer.tintLayers().getInt(info.tintIndex()):-1;
                float shade=model.usesBlockLight()&&info.shade()?faceShade(quad):1;
                float[] vertices=new float[20]; int[] colors=new int[4];
                for(int i=0;i<4;i++) { int at=i*5; vertices[at]=positions[i].x; vertices[at+1]=positions[i].y; vertices[at+2]=positions[i].z*.001f;
                    vertices[at+3]=UVPair.unpackU(quad.packedUV(i)); vertices[at+4]=UVPair.unpackV(quad.packedUV(i)); colors[i]=tint(quad.bakedColors().color(i),tint,shade); }
                output.add(new Quad(vertices,colors,info.sprite().atlasLocation()));
            }
        }
        if(output.isEmpty())return null;
        output.sort(Comparator.comparingDouble(q->q.coordinates[2]+q.coordinates[7]+q.coordinates[12]+q.coordinates[17]));
        return new Mesh(List.copyOf(output));
    }
    private static float faceShade(BakedQuad quad) { return switch(quad.direction()) { case UP->1; case DOWN->.5f; case NORTH,SOUTH->.8f; case EAST,WEST->.6f; }; }
    private static int tint(int base,int tint,float shade) {
        int a=(base>>>24)*(tint>>>24)/255, r=(int)((base>>16&255)*(tint>>16&255)/255f*shade),g=(int)((base>>8&255)*(tint>>8&255)/255f*shade),b=(int)((base&255)*(tint&255)/255f*shade);
        return a<<24|r<<16|g<<8|b;
    }
    private static Mesh fallback(ItemStack stack,ItemStackRenderState state) {
        var material=state.pickParticleMaterial(RandomSource.create(42)); TextureAtlasSprite sprite=material==null?null:material.sprite();
        AstralModels.Key key=null;
        if(stack.is(AstralContent.ASTRAL_GEM.get()))key=AstralMineralClient.GEM_MODEL;
        else if(stack.is(AstralContent.ATTUNEMENT_WAND.get()))key=AstralMineralClient.WAND_MODEL;
        else if(stack.is(AstralContent.ASTRAL_NEXUS.get()))key=AstralMineralClient.REMOTE_MODEL;
        else if(stack.is(AstralContent.RESONANCE_GOGGLES.get()))key=AstralMineralClient.GOGGLES_ICON_MODEL;
        else if(stack.getItem() instanceof BlockItem block&&net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("astral_repository"))key=CrystalModelRenderer.modelLocation(block.getBlock());
        if(key!=null)sprite=AstralModels.get(key).getParticleIcon(); if(sprite==null)return null;
        int color=new AstralClient.ItemTint().calculate(stack,Minecraft.getInstance().level,null);
        return new Mesh(List.of(new Quad(new float[]{-.5f,-.5f,0,sprite.getU0(),sprite.getV1(), .5f,-.5f,0,sprite.getU1(),sprite.getV1(), .5f,.5f,0,sprite.getU1(),sprite.getV0(), -.5f,.5f,0,sprite.getU0(),sprite.getV0()},new int[]{color,color,color,color},sprite.atlasLocation())));
    }
    private TransferItemSprites() {}
}
