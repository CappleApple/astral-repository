package com.cappleapple.astralrepository.porttest;

import com.cappleapple.astralrepository.client.BindingBeamRenderType;
import com.cappleapple.astralrepository.client.TransferRenderPass;
import com.cappleapple.astralrepository.network.NetworkPackets;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.nio.file.*;
import java.lang.reflect.Method;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.cappleapple.astralrepository.platform.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Real GPU materials and vanilla Fabulous compositor, with deterministic cloud-depth geometry. */
public final class CloudDepthClientSmoke {
    private static final Path OUT=Path.of("gameplay-captures/cloud-depth");
    private static Method resource,flush;
    public static void verify() throws Exception {
        var mc=Minecraft.getInstance();Files.createDirectories(OUT);
        var previousGraphics=mc.options.graphicsMode().get();
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        var type=Class.forName("com.cappleapple.astralrepository.client.ResourceTransferRenderer");
        resource=type.getDeclaredMethod("render",NetworkPackets.Visual.class,Vec3.class,PoseStack.class,MultiBufferSource.class,Quaternionf.class,float.class,float.class);resource.setAccessible(true);
        flush=type.getDeclaredMethod("flush",MultiBufferSource.BufferSource.class,boolean.class);flush.setAccessible(true);
        StringBuilder report=new StringBuilder("GPU cloud-depth checks: foreground / behind cloud / behind opaque block.\n");
        RenderSystem.getModelViewStack().pushMatrix();
        try {
            for(var mode:GraphicsStatus.values()) {
                mc.options.graphicsMode().set(mode);mc.levelRenderer.allChanged();
                boolean fabulous=Minecraft.useShaderTransparency();
                check(fabulous==(mode==GraphicsStatus.FABULOUS),"Requested graphics mode was not activated: "+mode);
                check(TransferRenderPass.stage()==(fabulous?RenderLevelStageEvent.Stage.AFTER_PARTICLES:RenderLevelStageEvent.Stage.AFTER_WEATHER),"Incorrect render stage");
                for(int effect=0;effect<8;effect++) {
                    render(effect,fabulous);
                    try(var pixels=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                        pixels.writeToFile(OUT.resolve(mode.name().toLowerCase()+"-"+effect+".png"));
                        double near=chroma(pixels,-.65F),far=chroma(pixels,0),blocked=chroma(pixels,.65F);
                        report.append(mode).append(" effect=").append(effect).append(" near=").append(near).append(" far=").append(far).append(" blocked=").append(blocked).append('\n');
                        Files.writeString(OUT.resolve("measurements.txt"),report);
                        check(near>4,"Foreground effect is missing: "+mode+" / "+effect);
                        check(near>far*2.5+2,"Cloud incorrectly covers foreground effect: "+mode+" / "+effect);
                        check(blocked<1,"Effect draws through opaque geometry: "+mode+" / "+effect);
                    }
                }
            }
            check(!mc.mouseHandler.isMouseGrabbed(),"Test captured the mouse");
            check(mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)==0,"Test is not muted");

            Files.writeString(OUT.resolve("result.txt"),"PASS: water, lava, energy, source and rune beams plus fading trails respect foreground/cloud/background depth in Fast, Fancy and Fabulous; real Fabulous compositor; opaque occlusion.\n");
        } finally {
            mc.options.graphicsMode().set(previousGraphics);mc.levelRenderer.allChanged();
            RenderSystem.getModelViewStack().popMatrix();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection,sorting);RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.depthMask(true);RenderSystem.colorMask(true,true,true,true);RenderSystem.defaultBlendFunc();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }
    private static void render(int effect,boolean fabulous)throws Exception {
        var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();var level=mc.levelRenderer;
        mc.renderBuffers().bufferSource().endBatch();
        RenderSystem.disableScissor();RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);
        main.setClearColor(0,0,0,0);main.clear(Minecraft.ON_OSX);main.bindWrite(true);
        RenderSystem.getModelViewStack().identity();RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-1,1,-1,1,.1F,100),VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.setShaderFogStart(1000);RenderSystem.setShaderFogEnd(2000);RenderSystem.setShaderColor(1,1,1,1);
        solid(.4F,.9F,-1,255); // opaque obstruction in front of the rightmost effect
        if(fabulous) {
            for(var target:new RenderTarget[]{level.getTranslucentTarget(),level.getItemEntityTarget(),level.getParticlesTarget(),level.getWeatherTarget(),level.getCloudsTarget()}) {
                target.setClearColor(0,0,0,0);target.clear(Minecraft.ON_OSX);
                if(target!=level.getCloudsTarget())target.copyDepthFrom(main);
            }
            // Like AFTER_PARTICLES, start on particles. Every material must restore this target itself.
            level.getParticlesTarget().bindWrite(true);
            effects(effect);
            level.getCloudsTarget().bindWrite(true);solid(-1,1,-5,204);
            PostChain chain=null;
            for(var field:LevelRenderer.class.getDeclaredFields())if(field.getType()==PostChain.class){field.setAccessible(true);var candidate=(PostChain)field.get(level);if(candidate!=null&&candidate.getTempTarget("clouds")!=null){chain=candidate;break;}}
            check(chain!=null,"Fabulous transparency chain exists");chain.process(0);main.bindWrite(true);
        } else {
            solid(-1,1,-5,204); // vanilla clouds write depth before AFTER_WEATHER
            effects(effect);
        }
    }
    private static void effects(int kind)throws Exception {
        var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();
        for(int i=0;i<3;i++) {
            float x=(i-1)*.65F,z=i==1?-8:-2;
            if(kind==4) {
                for(int pass=0;pass<(Minecraft.useShaderTransparency()?2:1);pass++) {
                    var type=pass==0?BindingBeamRenderType.BEAM:BindingBeamRenderType.DEPTH;
                    var out=buffers.getBuffer(type);
                    for(float[] xy:new float[][]{{x-.2F,-.2F},{x+.2F,-.2F},{x+.2F,.2F},{x-.2F,.2F}})
                        out.addVertex(xy[0],xy[1],z).setColor(255,190,40,210);
                    buffers.endBatch(type);
                }
            } else {
                int medium=kind>=5?kind-5:kind;
                int slot=medium<2?-2:medium==2?-3:-4;
                var packet=new NetworkPackets.Visual(BlockPos.ZERO,new BlockPos(1,0,0),ItemStack.EMPTY,0xffffff,20,slot,
                    java.util.List.of(BlockPos.ZERO,new BlockPos(1,0,0)),null,null,ResourceLocation.withDefaultNamespace(medium==1?"lava":"water"));
                resource.invoke(null,packet,new Vec3(x,0,z),new PoseStack(),buffers,new Quaternionf(),kind>=5?.08F:.23F,kind>=5?.35F:1F);
                flush.invoke(null,buffers,medium==2);
            }
            // A different RenderType batch can bind main between effects in Fabulous.
            if(Minecraft.useShaderTransparency())mc.getMainRenderTarget().bindWrite(true);
        }
    }
    private static void solid(float left,float right,float z,int alpha) {
        RenderSystem.enableDepthTest();RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);RenderSystem.depthMask(true);
        RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var out=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        for(float[] xy:new float[][]{{left,-1},{right,-1},{right,1},{left,1}})out.addVertex(xy[0],xy[1],z).setColor(160,160,160,alpha);
        BufferUploader.drawWithShader(out.buildOrThrow());
    }
    private static double chroma(NativeImage image,float x) {
        int cx=(int)((x+1)*.5*image.getWidth()),cy=image.getHeight()/2;double total=0;int count=0;
        for(int dx=-5;dx<=5;dx++)for(int dy=-5;dy<=5;dy++) {
            int color=image.getPixelRGBA(cx+dx,cy+dy),r=color&255,g=color>>8&255,b=color>>16&255;
            total+=Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));count++;
        }
        return total/count;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private CloudDepthClientSmoke(){}
}
