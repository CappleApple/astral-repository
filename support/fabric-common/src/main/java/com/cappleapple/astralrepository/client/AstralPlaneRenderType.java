package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.AstralClientConfig;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.nio.ByteBuffer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import com.cappleapple.astralrepository.platform.client.event.RegisterRenderPipelinesEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/** Immutable per-draw uniform snapshots for the procedural astral material. */
public final class AstralPlaneRenderType {
    private record Mode(boolean item, boolean panel) {}
    private static final Map<RenderPipeline,Mode> MODES = new IdentityHashMap<>();
    private static final BindGroupLayout ASTRAL = BindGroupLayout.builder().withUniform("AstralParameters",UniformType.UNIFORM_BUFFER).build();
    private static final Map<Identifier,RenderType> INTERFACES = new HashMap<>();
    private record AtlasMaterial(RenderType original, Identifier atlas) {}
    private static final Map<AtlasMaterial,RenderType> ATLAS_MATERIALS = new HashMap<>();
    public static RenderType withAtlas(RenderType original,Identifier atlas){
        return ATLAS_MATERIALS.computeIfAbsent(new AtlasMaterial(original,atlas),key->{
            var builder=RenderSetup.builder(original.pipeline()).withTexture("Sampler0",atlas).useOverlay().useLightmap();
            if(original==ITEM_TRIM||original==ARMOR_TRIM||original==ARMOR_TRIM_DECAL)builder.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING);
            if(original.hasBlending())builder.sortOnUpload();
            return RenderType.create("astral_repository:atlas/"+original+"/"+atlas,builder.createRenderSetup());
        });
    }
    private static boolean registered, worldSpace;
    private static Vec3 worldEye=Vec3.ZERO, meteorEye=Vec3.ZERO;
    private static final Matrix4f viewToWorld=new Matrix4f(), meteorWorldToView=new Matrix4f();
    private static final Vector3f cameraPosition=new Vector3f(), meteorEyeOffset=new Vector3f();
    private static Object meteorLevel;
    private static int meteorEvent=-1;
    private static float previousMeteorTime=-1, meteorStart;
    private static float appliedOverlayOpacity=Float.NaN, appliedInterfaceOverlayOpacity=Float.NaN;
    private static boolean appliedInterfaceMode;
    private static DynamicUniformStorage<Snapshot> uniforms;
    private static Snapshot activeSnapshot;
    private static final Map<PreparedRenderType,Snapshot> PREPARED = new IdentityHashMap<>();
    public static boolean owns(RenderType type){return MODES.containsKey(type.pipeline());}
    public static void prepared(RenderType type,PreparedRenderType prepared){var data=snapshot(type);if(data!=null)PREPARED.put(prepared,data);}
    public static void bindPrepared(RenderPass pass,PreparedRenderType prepared){var previous=activeSnapshot;try{if(previous==null)activeSnapshot=PREPARED.get(prepared);bind(pass,prepared.pipeline());}finally{activeSnapshot=previous;}}

    public static final RenderType ASTRAL_PLANE = material("astral_plane",false,false,true,false,false);
    public static final RenderType ASTRAL_OVERLAY = material("astral_overlay",false,false,true,false,false);
    public static final RenderType GOGGLES_LENS = material("goggles_lens",false,true,true,false,false);
    public static final RenderType GEM_ITEM = material("gem_item",true,false,true,false,false);
    public static final RenderType ITEM_TRIM = material("item_trim",false,false,true,true,false);
    private static final RenderType ARMOR_TRIM = material("armor_trim",false,false,false,true,false);
    private static final RenderType ARMOR_TRIM_DECAL = material("armor_trim_decal",false,false,false,true,true);
    public static final RenderPipeline INTERFACE_PIPELINE = pipeline("interface",false,true,true,false,false);

    private static RenderPipeline pipeline(String name,boolean item,boolean panel,boolean translucent,boolean cull,boolean decal) {
        var builder=RenderPipeline.builder(panel?RenderPipelines.GUI_TEXTURED_SNIPPET:RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("astral_repository","pipeline/"+name))
            .withVertexShader(Identifier.fromNamespaceAndPath("astral_repository","core/astral_plane"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("astral_repository","core/astral_plane"))
            .withBindGroupLayout(ASTRAL).withCull(cull);
        if(panel)builder.withShaderDefine("ASTRAL_INTERFACE");
        else builder.withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2).withVertexBinding(0,DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS).withDepthStencilState(new DepthStencilState(decal?CompareOp.EQUAL:CompareOp.GREATER_THAN_OR_EQUAL,!translucent));
        if(translucent)builder.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT));
        var pipeline=builder.build();MODES.put(pipeline,new Mode(item,panel));return pipeline;
    }
    private static RenderType material(String name,boolean item,boolean translucent,boolean cull,boolean layered,boolean decal) {
        var pipeline=pipeline(name,item,false,translucent,cull,decal);
        var builder=RenderSetup.builder(pipeline).withTexture("Sampler0",name.startsWith("armor_trim")?Sheets.ARMOR_TRIMS_SHEET:TextureAtlas.LOCATION_BLOCKS).useOverlay().useLightmap();
        if(layered)builder.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING);
        if(translucent)builder.sortOnUpload();else builder.affectsCrumbling();
        return RenderType.create("astral_repository:"+name,builder.createRenderSetup());
    }
    public static RenderType armorTrim(boolean decal){return decal?ARMOR_TRIM_DECAL:ARMOR_TRIM;}
    static RenderType interfaceMaterial(Identifier texture){return INTERFACES.computeIfAbsent(texture,id->RenderType.create("astral_repository:interface/"+id,RenderSetup.builder(INTERFACE_PIPELINE).withTexture("Sampler0",id).createRenderSetup()));}
    public static void register(RegisterRenderPipelinesEvent event){MODES.keySet().forEach(event::registerPipeline);event.registerPipeline(ResourceTransferRenderer.PIPELINE);event.registerPipeline(ResourceTransferRenderer.ITEM_PIPELINE);event.registerPipeline(BindingBeamRenderType.PIPELINE);registered=true;}
    public static boolean ready(){return registered;}
    public static Snapshot snapshot(RenderType type){var mode=MODES.get(type.pipeline());return mode==null?null:snapshot(mode);}
    private static Snapshot snapshot(Mode mode){
        var mc=Minecraft.getInstance();var level=mc.level;long ticks=level==null?0:level.getGameTime();
        float partial=mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float day=(Math.floorMod(ticks,24000L)+partial)/24000F;
        float drift=AstralAnimationClock.phase(ticks,day,AstralClientConfig.astralLayerDriftSpeed.get());
        float wobble=AstralAnimationClock.phase(ticks,day,AstralClientConfig.astralLayerWobbleSpeed.get());
        float particle=AstralAnimationClock.phase(ticks,day,AstralClientConfig.astralParticleSpeed.get());
        float twinkle=AstralAnimationClock.phase(ticks,day,AstralClientConfig.astralTwinkleSpeed.get());
        float shooting=AstralAnimationClock.phase(ticks,day,AstralClientConfig.astralShootingStarSpeed.get());
        boolean world=worldSpace&&!mode.panel&&!mode.item;updateMeteor(shooting,level,world);
        float opacity=(mode.panel?AstralClientConfig.astralInterfaceOverlayOpacity:AstralClientConfig.astralOverlayOpacity).get().floatValue();
        Vector4f sprite=new Vector4f(0,0,1,1);
        if(mode.item){var s=AstralModels.get(AstralMineralClient.GEM_MODEL).getParticleIcon();sprite.set(s.getU0(),s.getV0(),s.getU1()-s.getU0(),s.getV1()-s.getV0());}
        return new Snapshot(new Matrix4f(viewToWorld),new Matrix4f(AstralViewBobbing.correction()),new Matrix4f(AstralViewBobbing.worldProjectionBob()),new Matrix4f(meteorWorldToView),
            new Vector4f(cameraPosition,0),new Vector4f(meteorEyeOffset,0),new Vector4f(drift,wobble,particle,twinkle),new Vector4f(shooting,meteorStart,opacity,0),
            new Vector4f(mode.item?1:0,mode.panel?1:0,world?1:0,AstralClientConfig.astralShootingStars.get()?1:0),sprite);
    }
    public static Snapshot useSnapshot(Snapshot snapshot){var previous=activeSnapshot;activeSnapshot=snapshot;return previous;}
    public static void bind(RenderPass pass,RenderPipeline pipeline){
        var mode=MODES.get(pipeline);if(mode==null)return;
        var data=activeSnapshot!=null&&!mode.panel?activeSnapshot:snapshot(mode);
        if(uniforms==null)uniforms=new DynamicUniformStorage<>("Astral material UBO",352,32);
        pass.setUniform("AstralParameters",uniforms.writeUniform(data));
        appliedInterfaceMode=mode.panel;if(mode.panel)appliedInterfaceOverlayOpacity=data.times2.z();else appliedOverlayOpacity=data.times2.z();
    }
    public static void endFrame(){PREPARED.clear();if(uniforms!=null)uniforms.endFrame();}
    public static void close(){if(uniforms!=null){uniforms.close();uniforms=null;}}
    private static void updateMeteor(float time,Object level,boolean worldDraw){
        float clock=time*50;int event=(int)Math.floor(clock),hash=(event+1)*0x45d9f3b;hash^=hash>>>16;
        meteorStart=4+(hash&65535)/65535F*15;float age=(clock-event)*24-meteorStart;
        if(worldDraw){if(event!=meteorEvent||meteorLevel!=level||time<previousMeteorTime||age<0){meteorEye=worldEye;meteorWorldToView.set(viewToWorld).invert();meteorEvent=event;meteorLevel=level;}previousMeteorTime=time;
            meteorEyeOffset.set((float)(worldEye.x-meteorEye.x),(float)(worldEye.y-meteorEye.y),(float)(worldEye.z-meteorEye.z));meteorWorldToView.transformDirection(meteorEyeOffset);}
    }
    public static void beginWorld(Matrix4f worldView,Vec3 camera){worldSpace=true;worldEye=camera;viewToWorld.set(worldView).invert();cameraPosition.set(wrap(camera.x),wrap(camera.y),wrap(camera.z));}
    static boolean setWorldSpace(boolean enabled){boolean previous=worldSpace;worldSpace=enabled;return previous;}
    public static void endWorld(){worldSpace=false;viewToWorld.identity();cameraPosition.zero();}
    private static float wrap(double n){return(float)(n-Math.floor(n/4096)*4096);}
    public static float appliedOverlayOpacity(){return appliedOverlayOpacity;}
    public static float appliedInterfaceOverlayOpacity(){return appliedInterfaceOverlayOpacity;}
    public static boolean appliedInterfaceMode(){return appliedInterfaceMode;}
    public record Snapshot(Matrix4fc viewToWorld,Matrix4fc correction,Matrix4fc projectionBob,Matrix4fc meteorWorldToView,
            Vector4fc camera,Vector4fc meteorOffset,Vector4fc times1,Vector4fc times2,Vector4fc modes,Vector4fc sprite) implements DynamicUniformStorage.DynamicUniform {
        public Snapshot withBobMatrices(){
            if(modes.z()<0.5F)return this;
            return new Snapshot(viewToWorld,new Matrix4f(AstralViewBobbing.correction()),new Matrix4f(AstralViewBobbing.worldProjectionBob()),meteorWorldToView,camera,meteorOffset,times1,times2,modes,sprite);
        }
        @Override public void write(ByteBuffer buffer){Std140Builder.intoBuffer(buffer).putMat4f(viewToWorld).putMat4f(correction).putMat4f(projectionBob).putMat4f(meteorWorldToView)
            .putVec4(camera).putVec4(meteorOffset).putVec4(times1).putVec4(times2).putVec4(modes).putVec4(sprite);}
    }
    private AstralPlaneRenderType(){}
}
