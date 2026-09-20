package com.cappleapple.astralrepository.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.rendertype.*;
import com.mojang.renderpearl.api.commands.RenderPass;

/** OIT shares cloud depth; classic transparency defers transfers until the weather pass. */
public final class TransferRenderPass {
    public static volatile long deferredDraws,compositorDraws;
    private record Pending(PreparedRenderType material,StagedVertexBuffer.ExecuteInfo vertices){}
    private static final List<Pending> pending=new ArrayList<>();
    static boolean isTransfer(RenderType type){return type==BindingBeamRenderType.BEAM||ResourceTransferRenderer.transferMaterial(type);}
    static void draw(PreparedRenderType material,PreparedRenderType depth,StagedVertexBuffer.ExecuteInfo vertices,OitStage stage,RenderPass pass){
        if(stage==null){pending.add(new Pending(material,vertices));return;}
        material.drawFromBufferOit(vertices,stage,pass);compositorDraws++;
    }
    public static void afterWeather(RenderPass pass){
        try{for(var draw:pending){draw.material.drawFromBuffer(draw.vertices,pass);deferredDraws++;}}finally{pending.clear();}
    }
    static void clear(){pending.clear();}
    private TransferRenderPass(){}
}
