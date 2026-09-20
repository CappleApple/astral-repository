package com.cappleapple.astralrepository.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.state.WindowRenderState;

/** Scoped only to the private GUI renderer used for a cached rune composition. */
public final class RuneCompositeTarget {
    private static final ThreadLocal<RenderTarget> TARGET = new ThreadLocal<>();
    static void begin(RenderTarget target) { TARGET.set(target); }
    static void end() { TARGET.remove(); }
    public static RenderTarget target(RenderTarget original) { var target=TARGET.get(); return target==null?original:target; }
    public static WindowRenderState window(WindowRenderState original) {
        var target=TARGET.get(); if(target==null)return original;
        var state=new WindowRenderState(); state.width=target.width; state.height=target.height; state.guiScale=1; state.appropriateLineWidth=1; return state;
    }
    private RuneCompositeTarget() {}
}
