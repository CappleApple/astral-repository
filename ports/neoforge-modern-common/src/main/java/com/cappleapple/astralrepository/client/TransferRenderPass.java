package com.cappleapple.astralrepository.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Keeps prepared transfer geometry alive until clouds have written their depth. */
@EventBusSubscriber(modid="astral_repository",value=Dist.CLIENT)
public final class TransferRenderPass {
    static final OutputTarget TARGET=new OutputTarget("astral_repository:transfers",()->Minecraft.getInstance().levelRenderer.particlesTarget());
    private record Pending(PreparedRenderType material,StagedVertexBuffer.ExecuteInfo vertices){}
    private static final List<Pending> pending=new ArrayList<>();
    public static long deferredDraws,compositorDraws;
    static boolean isTransfer(RenderType type){return type==BindingBeamRenderType.BEAM||ResourceTransferRenderer.transferMaterial(type);}
    static void draw(PreparedRenderType material,PreparedRenderType depth,StagedVertexBuffer.ExecuteInfo vertices){
        if(Minecraft.getInstance().levelRenderer.particlesTarget()==null){pending.add(new Pending(material,vertices));return;}
        material.drawFromBuffer(vertices);compositorDraws++;
        // Fabulous sorts the particle layer using depth; write beam depth only after its glow and core.
        if(depth!=null)depth.drawFromBuffer(vertices);
    }
    @SubscribeEvent public static void afterWeather(RenderLevelStageEvent.AfterWeather event){
        try{for(var draw:pending){draw.material.drawFromBuffer(draw.vertices);deferredDraws++;}}finally{pending.clear();}
    }
    static void clear(){pending.clear();}
    private TransferRenderPass(){}
}
